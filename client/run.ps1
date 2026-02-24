Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location $repoRoot

$src = "client/clientV1/Client.java"
$lib = "client/lib/*"
$out = "client/out"

New-Item -ItemType Directory -Force -Path $out | Out-Null

Write-Host "Compiling $src..."
javac -cp $lib -d $out $src
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "Running Java application..."
Write-Host "------------------------------------"
java -cp "$out;client/lib/*" COMP208.client.clientv1.Client
