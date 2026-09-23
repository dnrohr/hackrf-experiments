# Pixel 8a screen-off evidence

Date: 2026-09-22 (America/New_York). Source: retained Android battery history
read directly from the physical Pixel 8a with `adb shell dumpsys batterystats
--history`. App UIDs, device identifiers, network identifiers, and unrelated
application events are omitted. The percentages below are Android's battery
history level field.

Representative sanitized events:

```text
14:14:20.954  64  +screen
14:14:34.180  64  -screen display_state_changed=display=0 state=OFF reason=DEFAULT_POLICY
14:14:36.411  64  +wake_lock="*location*:GnssLocationProvider"
14:14:36.695  64  +wake_lock="NotificationManagerService:post:dev.rfnotebook"
14:29:55.276  61  +wake_lock="NotificationManagerService:post:dev.rfnotebook"
14:29:55.551  61  +wake_lock="*location*:GnssLocationProvider"
14:30:00.445  61  +wake_lock="*location*:GnssLocationProvider"
14:30:00.614  61  +wake_lock="NotificationManagerService:post:dev.rfnotebook"
14:49:55.042  57  +wake_lock="NotificationManagerService:post:dev.rfnotebook"
14:49:55.498  57  +wake_lock="*location*:GnssLocationProvider"
14:49:59.244  57  +wake_lock="NotificationManagerService:post:dev.rfnotebook"
14:50:00.168  56  charge=2166
14:50:01.765  56  +screen
```

There is no screen-on event between 14:14:34.180 and 14:50:01.765: an
uninterrupted screen-off interval of 35 minutes 27.585 seconds. The RF Notebook
foreground notification and GNSS provider activity recur inside that interval,
including just before the display wakes. A separate direct snapshot at elapsed
32 minutes 58 seconds found the acquisition service active in doze with zero
drops, overruns, and malformed frames.

This file does not claim that the entire longer survey was screen-off; Android
history shows brief operator screen use before 14:14. The interval above is the
direct evidence for the 30-minute acceptance requirement.
