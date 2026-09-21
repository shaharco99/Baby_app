/**
 * Wakes the workspace's other devices.
 *
 * The caller says only which workspace changed. Every other device registered to that
 * workspace gets a data-only FCM message carrying that same workspace id and nothing else —
 * no record, no entity type, no count. The woken app syncs and re-derives its own reminders
 * from its own decrypted database, so the server never needs to know, and never does know,
 * what changed. That is why this is a wake-up rather than a notification: a server that could
 * write the notification text would be a server that could read the feed.
 *
 * Membership is checked against the *caller's* JWT, using a client built with their token, so
 * RLS answers the question rather than this function trusting the id it was handed. Only the
 * token lookup afterwards uses the service role, because reading another device's token is
 * exactly what RLS exists to prevent.
 */

import { createClient } from "jsr:@supabase/supabase-js@2";

interface NotifyRequest {
  /** The workspace whose devices should sync. */
  workspaceId: string;
  /** The calling device, so it is not woken by its own write. */
  deviceId?: string;
}

const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";

Deno.serve(async (request: Request): Promise<Response> => {
  if (request.method !== "POST") {
    return json({ error: "method not allowed" }, 405);
  }

  const authorization = request.headers.get("Authorization");
  if (!authorization) {
    return json({ error: "missing authorization" }, 401);
  }

  let body: NotifyRequest;
  try {
    body = await request.json();
  } catch {
    return json({ error: "malformed body" }, 400);
  }

  if (!isUuid(body.workspaceId)) {
    return json({ error: "workspaceId must be a uuid" }, 400);
  }

  const supabaseUrl = requireEnv("SUPABASE_URL");
  const anonKey = requireEnv("SUPABASE_ANON_KEY");
  const serviceKey = requireEnv("SUPABASE_SERVICE_ROLE_KEY");

  // As the caller: RLS decides whether they are in this workspace. A non-member reads zero
  // rows here and is refused below, without this function ever comparing ids itself.
  const asCaller = createClient(supabaseUrl, anonKey, {
    global: { headers: { Authorization: authorization } },
  });

  const { data: membership, error: membershipError } = await asCaller
    .from("workspace_members")
    .select("workspace_id")
    .eq("workspace_id", body.workspaceId)
    .maybeSingle();

  if (membershipError) {
    return json({ error: "membership lookup failed" }, 500);
  }
  if (!membership) {
    // Deliberately the same answer a missing workspace gets: whether a workspace exists is not
    // something a non-member should be able to probe.
    return json({ error: "not a member of that workspace" }, 403);
  }

  // As the service role, and only now: another device's token is not readable by the caller,
  // by design. The workspace id has already been proven to be theirs.
  const asService = createClient(supabaseUrl, serviceKey);

  const { data: rows, error: tokensError } = await asService
    .from("device_push_tokens")
    .select("device_id, token")
    .eq("workspace_id", body.workspaceId);

  if (tokensError) {
    return json({ error: "token lookup failed" }, 500);
  }

  const targets = (rows ?? []).filter((row) => row.device_id !== body.deviceId);
  if (targets.length === 0) {
    return json({ woke: 0 });
  }

  const accessToken = await fcmAccessToken();
  const projectId = requireEnv("FCM_PROJECT_ID");

  const results = await Promise.all(
    targets.map((row) => sendWakeUp(projectId, accessToken, row.token, body.workspaceId)),
  );

  const stale = targets
    .filter((_, index) => results[index] === "unregistered")
    .map((row) => row.device_id);

  // A token the platform has retired will never work again, and a row that keeps it would have
  // this function retrying it on every write for the life of the workspace.
  if (stale.length > 0) {
    await asService.from("device_push_tokens").delete().in("device_id", stale);
  }

  return json({
    woke: results.filter((r) => r === "sent").length,
    dropped: stale.length,
  });
});

type SendResult = "sent" | "unregistered" | "failed";

/**
 * A data-only message, high priority.
 *
 * Data-only because there is nothing to display: the device decides whether anything is worth
 * showing once it has synced and can actually read what arrived. High priority because a
 * normal-priority data message is exactly what Doze holds back, and holding it back is the bug
 * this exists to fix.
 */
async function sendWakeUp(
  projectId: string,
  accessToken: string,
  token: string,
  workspaceId: string,
): Promise<SendResult> {
  const response = await fetch(
    `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        message: {
          token,
          data: { type: "workspace-changed", workspaceId },
          android: { priority: "HIGH" },
        },
      }),
    },
  );

  if (response.ok) return "sent";

  // 404 UNREGISTERED / 400 INVALID_ARGUMENT on the token both mean this row is dead.
  if (response.status === 404) return "unregistered";
  return "failed";
}

/**
 * An OAuth access token for FCM v1, minted from the service account.
 *
 * Signed here rather than pulled from a library so the only secret this function holds is the
 * service-account JSON already in the function's environment.
 */
async function fcmAccessToken(): Promise<string> {
  const credentials = JSON.parse(requireEnv("FCM_SERVICE_ACCOUNT"));
  const now = Math.floor(Date.now() / 1000);

  const header = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = base64Url(JSON.stringify({
    iss: credentials.client_email,
    scope: FCM_SCOPE,
    aud: "https://oauth2.googleapis.com/token",
    iat: now,
    exp: now + 3600,
  }));

  const key = await importPrivateKey(credentials.private_key);
  const signature = await crypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    key,
    new TextEncoder().encode(`${header}.${claims}`),
  );

  const assertion = `${header}.${claims}.${base64UrlBytes(new Uint8Array(signature))}`;

  const response = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion,
    }),
  });

  if (!response.ok) {
    throw new Error(`token exchange failed: ${response.status}`);
  }

  const { access_token } = await response.json();
  return access_token;
}

async function importPrivateKey(pem: string): Promise<CryptoKey> {
  const body = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s/g, "");
  const der = Uint8Array.from(atob(body), (c) => c.charCodeAt(0));

  return await crypto.subtle.importKey(
    "pkcs8",
    der,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
    false,
    ["sign"],
  );
}

function base64Url(value: string): string {
  return base64UrlBytes(new TextEncoder().encode(value));
}

function base64UrlBytes(bytes: Uint8Array): string {
  return btoa(String.fromCharCode(...bytes))
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

function requireEnv(name: string): string {
  const value = Deno.env.get(name);
  if (!value) throw new Error(`${name} is not set`);
  return value;
}

function isUuid(value: unknown): value is string {
  return typeof value === "string" &&
    /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
}

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
