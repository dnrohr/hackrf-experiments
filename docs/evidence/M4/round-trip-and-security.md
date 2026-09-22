# M4 round-trip, privacy, and hostile-archive results

`SurveyBundleTest` creates full and redacted archives and inspects both. The full
archive retains selected IQ, route, note, coordinates, and suffix. The redacted
archive independently removes IQ, route, coordinates, note, and identifier.
Both same-version manifests validate and remain readable.

Import validation rejects before destination creation:

- absolute, drive-qualified, empty-segment, and parent-traversal paths;
- duplicate paths, directory entries, Unix symbolic links, and payloads not
  declared by the manifest inventory;
- more than 1,000 entries, per-entry oversize, total expansion above 1 GB, and
  suspicious compression ratios;
- unsupported schema versions, malformed numeric constraints, missing files,
  length mismatch, and SHA-256 mismatch.

Extraction occurs only into a fresh `.part` staging directory. A completed
validation is committed by same-parent rename; failures remove staging. Imported
scripts, decoders, flowgraphs, and executables are never run.

The physical redacted archive contained only `survey.json`, redacted
`observations.csv`, empty geographic aggregates, and the explicitly selected
IQ/sidecar/preview. Its manifest contained no serial suffix, notes, route, or
coordinates. Temporary share archives expire after 24 hours.
