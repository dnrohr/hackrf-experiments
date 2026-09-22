# M4 UI review

The focused screen labels `RX only`, the fixed-off RF amplifier and antenna-port
power, center frequency, sample rate, duration, expected bytes, measured USB
capacity, reserve, relative dBFS, and bounded waterfall. The capture control is
disabled until measured capacity passes. Exit says it returns the radio to idle.

After completion, the review independently exposes IQ, route, note, identifier,
and full/rounded/omitted coordinate choices. A summary immediately above the
share action states the selected values. The Android share sheet appears only
after `Create reviewed bundle and open share sheet`.

Evidence: `ui/m4-export-review-pixel8a.png` shows route, note, and identifier
unchecked; summary says coordinates omitted and identifiers false; it also shows
the explicit share action and radio-idle exit.

The UI never parses or displays application payload contents.
