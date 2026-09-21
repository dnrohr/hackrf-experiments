# M3 known limitations

- Relative dBFS is uncalibrated. Comparisons are meaningful only within
  compatible, versioned equipment settings; the UI excludes incompatible data
  by default and explains it.
- One large accuracy radius conservatively enlarges the filtered layer's cells.
  This favors honest uncertainty over local visual detail.
- OpenFreeMap's public instance has no availability SLA. Cached regions remain
  available, but a new download depends on the provider and network.
- Online basemap requests reveal requested tile areas to the provider and its
  network processor. The provider cannot read Room, RF metadata, exact stored
  fixes, notes, or exports.
- M3 repeat-route evidence is deterministic and coordinate-redacted. Live
  environmental repeatability and a manual TalkBack audit remain M5 release
  hardening work; automated semantics and 150% text coverage pass now.
- Offline storage size is reported after/during download rather than predicted
  exactly beforehand because server tile composition can change.
