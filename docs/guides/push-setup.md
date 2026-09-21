# Turning the push wake-up on — step by step

**What this fixes:** right now, when you log a feed on one phone, the *other* phone keeps its
old reminder time until someone opens the app. This makes the other phone find out within
seconds, even with the app closed.

**The code is already written and installed.** It is switched off, because it needs an account
at Google (Firebase) that only you can create. Until you finish these steps nothing breaks —
the app just behaves the way it did before.

Do the steps in order. Each one says exactly what to click and what to copy.

---

## Before you start

You need, on the computer:

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

The app wants to store one row per phone, saying "this phone can be woken, here is its address".
That table does not exist yet.

1. Open <https://supabase.com/dashboard> and click your project.
2. In the left sidebar click **SQL Editor**.
3. Click **New query**.
4. Open this file on the computer and copy **all** of it:
   `supabase/migrations/0011_device_push_tokens.sql`
5. Paste it into the box and click **Run**.
6. You should see **Success. No rows returned.** That is what success looks like here.

> ⚠️ Nothing applies these files automatically. If you skip this step, everything else below
> will look like it worked and still do nothing.

**Check it worked:** left sidebar → **Table Editor**. There should now be a table called
`device_push_tokens`. It will be empty. That is correct — phones fill it in at Step 6.

---

## Step 2 — Create the Firebase project

Firebase is Google's free service for sending "wake up" pings to phones. We only use the ping.
No feed data ever goes near it.

1. Go to <https://console.firebase.google.com> and sign in with your Google account.
2. Click **Create a project**.
3. Name it anything — `takes-two-of-us` is fine. Click through.
4. On the Google Analytics step, choose **not** to enable it. We do not need it.
5. Wait for it to finish, then click **Continue**.

Now add the Android app to it:

6. On the project's home page, click the **Android** icon (a little robot).
7. Where it asks for **Android package name**, type exactly:

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

Nothing breaks. The app syncs, reminds, and records feeds exactly as it does today. The only
thing you lose is the other phone finding out quickly — it will keep catching up when you open
it, or within the hour on its own.
