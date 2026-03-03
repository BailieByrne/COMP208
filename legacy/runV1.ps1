Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$src = "legacy/clientV1/Client.java"
$lib = "legacy/lib/*"
$out = "legacy/out"

New-Item -ItemType Directory -Force -Path $out | Out-Null

Write-Host "Compiling $src..."
javac -cp $lib -d $out $src
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Running Java application..."
Write-Host "------------------------------------"
java -cp "$out;legacy/lib/*" Client
