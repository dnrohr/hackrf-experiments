# Native third-party notices

`radio-hackrf-native` builds the following unmodified upstream revisions, apart
from the build-local Android file-descriptor adapter described below. CMake
downloads each source archive only when it is absent from its build cache and
rejects an archive whose SHA-256 does not match.

| Component | Revision | Archive SHA-256 | License |
| --- | --- | --- | --- |
| [libusb](https://github.com/libusb/libusb) | `15a7ebb4d426c5ce196684347d2b7cafad862626` (v1.0.29) | `3415F390DF8D841F275C14F4B3B5CA5A4FCA893D36BA1D845538E4CAA54C2F30` | LGPL-2.1-or-later; see upstream [`COPYING`](https://github.com/libusb/libusb/blob/15a7ebb4d426c5ce196684347d2b7cafad862626/COPYING) |
| [libhackrf](https://github.com/greatscottgadgets/hackrf) | `01f7c8b3509e308c5e17b77a8ed2dbb594a98860` (v2026.01.3) | `33B90BCC62C5798F576AF64139C23553EB74FD65AEE60A467C63152B1982B976` | Three-clause BSD terms in `host/libhackrf/src/hackrf.h` |

The build creates a temporary copy of `hackrf.c` and adds
`hackrf_android_open_by_fd`. That adapter calls `libusb_wrap_sys_device` with
libhackrf's initialized context, then delegates to libhackrf's existing private
open/setup path. Upstream source archives are never edited in place.

The application links these dependencies privately. An ELF version script
exports only the nine project-owned, receive-only JNI entry points; neither
libusb nor libhackrf (including transmit-capable libhackrf functions) is a
dynamic application-facing API. Distribution packaging must include the
applicable notices and the LGPL source/relinking materials; release packaging
is a later milestone gate.
