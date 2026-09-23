# Export and reimport guide

Exports are the only intentional path for precise local survey data to leave
the application. Nothing is uploaded automatically.

1. Open a completed survey and choose export.
2. Review the manifest before sharing. Select whether to include IQ, route,
   exact/rounded/omitted coordinates, notes, and device serial suffix.
3. Use exact coordinates only for a trusted recipient with a stated need.
   Rounded coordinates can still reveal a neighborhood; omission is safest.
4. IQ can be large and may reveal more signal detail than aggregates. Exclude it
   unless downstream analysis requires it.
5. Complete the export, verify the manifest inventory and SHA-256 values, then
   either choose an Android document destination with **Save reviewed survey
   bundle** or explicitly invoke the share sheet. Shared-cache copies expire
   after 24 hours; the authoritative app-private survey and captures do not.

Bundle schema `1.0.0` contains versioned JSON metadata, CSV observations,
GeoJSON geographic data when selected, capture files/sidecars when selected,
units, timestamps, CRS, and hashes. Import stages an archive outside
authoritative state and rejects unknown schemas, traversal, drive/absolute
paths, duplicates, links, excessive size/compression, malformed values, and
hash mismatches before an atomic commit. Importing never executes scripts,
flowgraphs, or decoders.

For a round trip, reimport the exported ZIP, inspect the validation summary, and
compare required metadata and file hashes. Keep the original until the imported
copy is verified. GNU Radio consumes capture IQ as complex interleaved signed
8-bit (`char`) samples using the sidecar center frequency and sample rate.
