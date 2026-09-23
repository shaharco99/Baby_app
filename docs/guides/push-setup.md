# Turning the push wake-up on — step by step

> **Status, 2026-09-21: done, verified on both phones.** Migration 0011 applied, Firebase
> project `takes-two-of-us` exists, keys in `android/local.properties`, `notify-workspace`
> deployed, Pixel and Xiaomi both registered.
>
> Verified both directions with receiving app killed: feed logged or deleted on one phone moved
> other's pending alarm to predicted time, ~10s to Pixel, ~5s to Xiaomi, receiving app never opened.
>
> **Step 7 not needed.** Xiaomi got wake-ups without Autostart or battery exemption changed. Keep
> step anyway: test ran on phone plugged in and used minutes earlier — easy case. If Xiaomi starts
> missing wake-ups after idle overnight, try Step 7 first.
>
> Rest of file = record of setup, and guide for when phone gets replaced.

**What this fixes:** now, feed logged on one phone → *other* phone keeps old reminder time until
someone opens app. This makes other phone learn within seconds, even with app closed.

**Code already written and installed.** Switched off, because needs Google (Firebase) account
only you can create. Until steps done nothing breaks — app behaves as before.

Do steps in order. Each says exactly what to click and copy.

---

## Before you start

Need on computer:

```bash
# The Supabase command-line tool (used in Step 5). Check if you already have it:
supabase --version

# If that says "command not found", install it:
brew install supabase/tap/supabase       # macOS
# or, on Linux:
curl -fsSL https://raw.githubusercontent.com/supabase/setup-cli/main/install.sh | sh
```

---

## Step 1 — Add the new table to the database

App stores one row per phone: "this phone can be woken, here its address". Table not exist yet.

1. Open <https://supabase.com/dashboard> and click your project.
2. Left sidebar → **SQL Editor**.
3. Click **New query**.
4. Open this file on computer, copy **all** of it:
   `supabase/migrations/0011_device_push_tokens.sql`
5. Paste into box, click **Run**.
6. Expect **Success. No rows returned.** That = success.

> ⚠️ Nothing applies these files automatically. Skip this step and everything below will look
> like it worked but do nothing.

**Check it worked:** left sidebar → **Table Editor**. Table `device_push_tokens` should exist,
empty. Correct — phones fill it at Step 6.

---

## Step 2 — Create the Firebase project

Firebase = Google's free service for sending "wake up" pings to phones. Only ping used. No feed
data goes near it.

1. Go to <https://console.firebase.google.com> and sign in with Google account.
2. Click **Create a project**.
3. Name anything — `takes-two-of-us` fine. Click through.
4. Google Analytics step: choose **not** to enable. Not needed.
5. Wait for finish, click **Continue**.

Now add Android app:

6. On project home page, click **Android** icon (little robot).
7. At **Android package name**, type exactly:

   ```
   com.oryareach.app
   ```

   This must match character for character or the ping will never arrive.
8. Nickname and SHA-1 can be left blank. Click **Register app**.
9. It offers a file called **`google-services.json`**. Click **Download** and save it to your
   Downloads folder.
10. Click **Next** → **Next** → **Continue to console**. Ignore everything it tells you to add
    to the code; that part is already done, differently.

---

## Step 3 — Put four values into the app

The file you just downloaded holds four things the app needs. Rather than copying the file into
the project (it would end up in git, and the build would break for anyone without it), we copy
four values out of it.

Run this, adjusting the path if you saved it somewhere else:

```bash
cd ~/repos/Baby_app

jq -r '
  "fcmProjectId=" + .project_info.project_id,
  "fcmSenderId=" + .project_info.project_number,
  "fcmApplicationId=" + .client[0].client_info.mobilesdk_app_id,
  "fcmApiKey=" + .client[0].api_key[0].current_key
' ~/Downloads/google-services.json >> android/local.properties
```

**Check it worked:**

```bash
tail -4 android/local.properties
```

You should see four lines, each with a real value after the `=`, like:

```
fcmProjectId=takes-two-of-us
fcmSenderId=123456789012
fcmApplicationId=1:123456789012:android:a1b2c3d4e5f6
fcmApiKey=AIzaSy...
```

If any value says `null`, the download did not contain it — go back to Step 2 and make sure you
registered the **Android** app, not a web one.

> `android/local.properties` is already ignored by git, so these stay on your machine. None of
> them is a secret anyway — they just name the project. The one real secret is in Step 4.

---

## Step 4 — Get the key that is allowed to send

Steps 2–3 let the phone *receive*. This step is what lets the server *send*.

1. Back in the Firebase console, click the **gear icon** (top left, next to "Project Overview")
   → **Project settings**.
2. Click the **Service accounts** tab.
3. Click **Generate new private key**, then **Generate key** in the box that pops up.
4. A `.json` file downloads. **This one is a real secret.** Do not put it in the project folder
   and do not email it. Leave it in Downloads for now; you delete it at the end of Step 5.

---

## Step 5 — Put the function on the server

This is the little program that receives "wake the others" and sends the pings.

Find your project reference first: in the Supabase dashboard it is in the URL —
`https://supabase.com/dashboard/project/`**`abcdefghijklmnop`** — that highlighted part.

```bash
cd ~/repos/Baby_app

# Sign in (opens a browser) and connect this folder to your project.
supabase login
supabase link --project-ref PASTE_YOUR_PROJECT_REF_HERE

# Hand the function the Firebase project name and the secret key from Step 4.
supabase secrets set FCM_PROJECT_ID="$(jq -r .project_info.project_id ~/Downloads/google-services.json)"
supabase secrets set FCM_SERVICE_ACCOUNT="$(cat ~/Downloads/YOUR-SERVICE-ACCOUNT-FILE.json)"

# Upload the function.
supabase functions deploy notify-workspace
```

Replace `YOUR-SERVICE-ACCOUNT-FILE.json` with the actual filename from Step 4 — it is long, like
`takes-two-of-us-firebase-adminsdk-abc12-3d4e5f6a7b.json`. Tab-completion will fill it in.

**Check it worked:** in the Supabase dashboard, left sidebar → **Edge Functions**. You should
see `notify-workspace` listed, with a recent deploy time.

**Now delete the secret file:**

```bash
rm ~/Downloads/YOUR-SERVICE-ACCOUNT-FILE.json
```

It is already stored on the server; keeping a copy in Downloads is the only real risk in this
whole guide.

---

## Step 6 — Rebuild and install on **both** phones

Push only works between phones that have both registered. One phone on the new build and one on
the old one will not wake each other.

```bash
cd ~/repos/Baby_app/android

set -a; . ~/keystores/oryareach-release.env; set +a
export ANDROID_KEYSTORE_PATH="$HOME/keystores/oryareach-release.jks" \
       ANDROID_KEYSTORE_PASSWORD="$STORE_PASS" \
       ANDROID_KEY_ALIAS="oryareach-release" \
       ANDROID_KEY_PASSWORD="$KEY_PASS"

./gradlew :app:assembleRelease
```

Then, with **each** phone plugged in one at a time:

```bash
adb install -r -d app/build/outputs/apk/release/app-release.apk
```

> Never use `adb uninstall`. It wipes the local data, and anything not yet synced is gone.
> `install -r` keeps everything.

**Open the app on each phone once** after installing. That is when it registers itself.

**Check it worked:** Supabase dashboard → **Table Editor** → `device_push_tokens`.
There should now be **two rows**, one per phone. If there is only one, open the app on the
missing phone and wait ten seconds.

---

## Step 7 — The MIUI-only step (Xiaomi)

Xiaomi's battery manager will happily ignore a wake-up. On the **Xiaomi only**:

1. **Settings → Apps → Manage apps → Takes Two of Us**
2. **Autostart** → turn **on**
3. **Battery saver** → choose **No restrictions**

The Pixel does not need this.

---

## Step 8 — Test that it actually works

1. On the **Xiaomi**, open the app once, then swipe it fully away from recents. Closed, not
   just backgrounded.
2. On the computer, with the Xiaomi plugged in, note the current reminder time:

   ```bash
   adb shell dumpsys alarm | grep -A2 ReminderAlarmReceiver | grep origWhen
   ```

   Write down the time it prints.
3. On the **Pixel**, log a feed.
4. Wait about ten seconds, then run the same command again.

**It worked if** the time changed to roughly three hours after the feed you just logged on the
Pixel.

**It did not work if** the time is unchanged. In that case, check in this order:

| What to check | Where |
|---|---|
| Are there two rows in `device_push_tokens`? | Supabase → Table Editor |
| Did the function run, and what did it say? | Supabase → Edge Functions → notify-workspace → Logs |
| Is the Xiaomi's autostart on? | Step 7 |
| Are both phones on the new build? | `adb shell dumpsys package com.oryareach.app \| grep versionName` |

---

## What if I never do any of this?

Nothing breaks. App syncs, reminds, records feeds same as today. Only loss: other phone not
finding out quickly — catches up when opened, or within the hour on its own.