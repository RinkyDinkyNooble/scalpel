# Builds core (with its unit tests) and every port, then copies the jars to upload into build/release/.
# Each port is its own Gradle build: .\gradlew.bat -p ports\<loader>-<minecraft> build builds just one.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
$version = ((Get-Content gradle.properties) -match '^mod_version=' | Select-Object -First 1) -replace '^mod_version=', ''
.\gradlew.bat build --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Remove-Item -Recurse -Force build\release -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force build\release | Out-Null
foreach ($port in Get-ChildItem -Directory ports) {
    Write-Host "== $($port.Name)"
    # A port with its own wrapper (Gradle 9 for ForgeGradle 7) builds with it; the others use the root wrapper.
    $own = Join-Path $port.FullName 'gradlew.bat'
    if (Test-Path $own) { & $own -p $port.FullName build --no-daemon } else { .\gradlew.bat -p $port.FullName build --no-daemon }
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    # Only this version's jars: build\libs keeps the jars of earlier versions.
    Get-ChildItem "$($port.FullName)\build\libs\scalpel-*-$version.jar" |
        Where-Object { $_.Name -notmatch '-(slim|sources|dev)\.jar$' } |
        Copy-Item -Destination build\release
}
Get-ChildItem build\release | Select-Object -ExpandProperty Name
