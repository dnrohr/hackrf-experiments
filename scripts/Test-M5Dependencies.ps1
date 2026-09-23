[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$packages = @(
    'pkg:maven/org.jetbrains.kotlin/kotlin-stdlib@2.3.21',
    'pkg:maven/org.jetbrains.kotlinx/kotlinx-coroutines-android@1.10.2',
    'pkg:maven/org.jetbrains.kotlinx/kotlinx-serialization-core-jvm@1.7.3',
    'pkg:maven/androidx.activity/activity-compose@1.12.4',
    'pkg:maven/androidx.lifecycle/lifecycle-runtime-ktx@2.9.4',
    'pkg:maven/androidx.room/room-runtime@2.8.4',
    'pkg:maven/androidx.compose.ui/ui@1.11.4',
    'pkg:maven/androidx.compose.material3/material3@1.4.0',
    'pkg:maven/org.maplibre.gl/android-sdk-opengl@13.6.1',
    'pkg:maven/org.maplibre.gl/android-sdk-geojson@6.0.1',
    'pkg:maven/org.maplibre.gl/android-sdk-turf@6.0.1',
    'pkg:maven/org.maplibre.gl/maplibre-android-gestures@0.0.4',
    'pkg:maven/com.google.code.gson/gson@2.10.1',
    'pkg:maven/com.squareup.okhttp3/okhttp@4.12.0',
    'pkg:maven/com.squareup.okio/okio-jvm@3.6.0',
    'pkg:maven/com.jakewharton.timber/timber@5.0.1',
    'pkg:github/libusb/libusb@v1.0.29',
    'pkg:github/greatscottgadgets/hackrf@v2026.01.3'
)

$queries = @($packages | ForEach-Object { @{ package = @{ purl = $_ } } })
$body = @{ queries = $queries } | ConvertTo-Json -Depth 5 -Compress
$response = Invoke-RestMethod -Method Post -Uri 'https://api.osv.dev/v1/querybatch' `
    -ContentType 'application/json' -Body $body
if ($response.results.Count -ne $packages.Count) {
    throw "OSV returned $($response.results.Count) results for $($packages.Count) packages."
}

$findings = for ($index = 0; $index -lt $packages.Count; $index++) {
    foreach ($vulnerability in @($response.results[$index].vulns)) {
        if ($null -ne $vulnerability) {
            [pscustomobject]@{ Package = $packages[$index]; Id = $vulnerability.id }
        }
    }
}
if ($findings) {
    $findings | Format-Table -AutoSize | Out-String | Write-Host
    throw 'OSV reported dependency advisories; record a disposition before release.'
}

Write-Host "M5 OSV check passed: zero advisories for $($packages.Count) reviewed release packages." -ForegroundColor Green
