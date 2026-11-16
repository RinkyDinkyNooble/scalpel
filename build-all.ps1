# Builds core (with its unit tests) and every port, then copies the jars to upload into build/release/.
# Each port is its own Gradle build: .\gradlew.bat -p ports\<loader>-<minecraft> build builds just one.
$ErrorActionPreference = 'Stop'
Set-Location $PSScriptRoot
.\gradlew.bat build --no-daemon
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Remove-Item -Recurse -Force build\release -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force build\release | Out-Null
foreach ($port in Get-ChildItem -Directory ports) {
    Write-Host "== $($port.Name)"
    .\gradlew.bat -p $port.FullName build --no-daemon
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
    Get-ChildItem "$($port.FullName)\build\libs\scalpel-*.jar" |
        Where-Object { $_.Name -notmatch '-(slim|sources|dev)\.jar$' } |
        Copy-Item -Destination build\release
}
Get-ChildItem build\release | Select-Object -ExpandProperty Name
