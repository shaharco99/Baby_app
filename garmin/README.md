# Garmin watch app (Connect IQ)

Feed and pump clocks on a Garmin watch (built for vívoactive 6). The phone app sends bare
timestamps through Garmin Connect (`android/app/.../watch/GarminTimersSink.kt`); keys and units are
`WatchTimers.toLongs()` in `android/core/watch`. The app id in `manifest.xml` must match
`GARMIN_APP_ID` there.

Not built by CI: device files need a Garmin login. Local build:

- SDK + device files in `~/.Garmin/ConnectIQ` (SDK Manager). On Ubuntu 24.04+ SDK Manager and the
  simulator need WebKitGTK 4.0, so run them from the Ubuntu 22.04 image in `~/garmin/docker`
  (rootless Docker: don't pass `--user`; mount `$XAUTHORITY` and `/tmp/.X11-unix`).
- Developer key: `~/garmin/developer_key.der` — never commit it. Keep it: a sideloaded app signed
  with a different key has to be deleted from the watch before the new one installs.

```bash
SDK=~/.Garmin/ConnectIQ/Sdks/connectiq-sdk-lin-9.2.0-*
$SDK/bin/monkeyc -d vivoactive6 -f monkey.jungle -o bin/timers.prg -y ~/garmin/developer_key.der -l 3
```

Install: copy `bin/timers.prg` to the watch's `GARMIN/APPS/` over USB.
