$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$python = Join-Path $root '.tools\radioconda\python.exe'
if (-not (Test-Path -LiteralPath $python)) { throw 'Radioconda Python is required; run scripts\Install-HackRFTools.ps1.' }
$program = @'
import json
from pathlib import Path
from jsonschema import Draft202012Validator

root = Path(r"ROOT_TOKEN")
pairs = [
    ("schemas/capture-sidecar-1.0.0.schema.json", ["capture-valid.json", "capture-minimal-redacted.json"], "capture-invalid.json"),
    ("schemas/survey-manifest-1.0.0.schema.json", ["manifest-full.json", "manifest-redacted.json"], "manifest-invalid.json"),
]
for schema_name, valid_names, invalid_name in pairs:
    schema = json.loads((root / schema_name).read_text())
    Draft202012Validator.check_schema(schema)
    validator = Draft202012Validator(schema)
    for name in valid_names:
        validator.validate(json.loads((root / "schemas/examples" / name).read_text()))
    invalid = json.loads((root / "schemas/examples" / invalid_name).read_text())
    if not list(validator.iter_errors(invalid)):
        raise SystemExit(f"expected {invalid_name} to fail")
print("M4 schemas and committed examples validated")
'@
& $python -c $program.Replace('ROOT_TOKEN', $root.Replace('\', '\\'))
if ($LASTEXITCODE -ne 0) { throw "Schema validation failed with exit code $LASTEXITCODE" }
