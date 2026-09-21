# M3 repeated-route results

M3 uses a deterministic, redistributable repeated-route dataset so the
geographic contract can be reproduced without committing a user's precise
route or home coordinates. The two synthetic passes follow the same redacted
path near Null Island with small measurement variation. Persistent observations
fall into the same adaptive cell and reach high confidence only through support
from both surveys. Median strength remains stable in the presence of the
deliberate strong outlier.

The route includes one explicit acquisition gap, one interpolated fix, one
missing fix, and one degraded-accuracy segment. The map and equivalent list keep
all four conditions visible. A second equipment profile represents a deliberate
gain/configuration change; it is excluded from the comparable aggregate by
default and triggers the comparable-equipment warning when selected for
comparison.

Fixture sources are `test-data/M3/repeat-route.csv` and its manifest. The
connected UI dataset expands the same pattern to 20,000 observations for the
Pixel performance run. No HackRF connection was needed and no transmit-capable
operation was performed.

Physical-route variation from multipath, phone orientation, weather, and live
RF occupancy remains an M5 field-hardening concern. M3's claim is limited to
correct, uncertainty-aware rendering of the recorded observations; it makes no
claim about a transmitter position.
