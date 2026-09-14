# M1 UI review

Status: Setup/empty state reviewed; active and summary states pending radio run

The M1 setup screen was installed and rendered on the Pixel 8a running Android
17 on 2026-09-14. The first capture exposed status-bar overlap caused by
edge-to-edge layout. The root content now applies status- and navigation-bar
insets; the recaptured screen confirms that the title, disconnected state,
receive-only calibration warning, equipment fields, fixed power defaults, gain
controls, and sample-rate choices are readable without overlap.

![Pixel 8a M1 setup screen](ui/setup-pixel8a.png)

The screen uses text labels in addition to selection marks and does not encode
status in color alone. Active-survey and completion-summary visual evidence will
be added during the production HackRF gate.
