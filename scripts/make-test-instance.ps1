# Creates a TEST copy of a Prism Launcher instance for trying out development builds.
#
# Runs on the Windows HOST in PowerShell (Prism and the pack live on Windows, not in the dev container).
# The source instance is only READ: robocopy is used without /MOV or /MIR, so nothing is moved or deleted.
# World backups, saves, logs and crash reports are not copied: the TEST instance starts with no worlds.
#
# Usage (from PowerShell):
#   powershell -ExecutionPolicy Bypass -File make-test-instance.ps1
#   powershell -ExecutionPolicy Bypass -File make-test-instance.ps1 -Source "FTB Evolution 2" -Target "FTB Evolution 2 - AE-TEST"
param(
    [string]$Source = "FTB Evolution 2",
    [string]$Target = "FTB Evolution 2 - AE-TEST",
    [string]$PrismRoot = (Join-Path $env:APPDATA "PrismLauncher")
)

$ErrorActionPreference = "Stop"
$instances = Join-Path $PrismRoot "instances"
$src = Join-Path $instances $Source
$dst = Join-Path $instances $Target

if ($Source -eq $Target) { throw "Source and target must differ." }
if (-not (Test-Path (Join-Path $src "instance.cfg"))) { throw "Source instance not found: $src" }
if (Test-Path $dst) { throw "Target already exists: $dst (delete it in Prism first; this script never overwrites)." }

# Folders inside <instance>\minecraft that are NOT copied.
$skipDirs = @("ftbbackups3", "saves", "logs", "crash-reports", ".mixin.out") |
    ForEach-Object { Join-Path (Join-Path $src "minecraft") $_ }

Write-Host "Copying '$Source' -> '$Target' (skipping: ftbbackups3, saves, logs, crash-reports, .mixin.out)"
# /E   = all subfolders, including empty ones      /XD = exclude these directories
# /R:1 /W:1 = one retry, one second wait (a locked file should not stall the copy)
# /MT:8 = 8 copy threads    /NFL /NDL /NP = no per-file, per-folder or progress lines (summary only)
robocopy $src $dst /E /XD @skipDirs /R:1 /W:1 /MT:8 /NFL /NDL /NP
# robocopy exit codes: 0-7 = success (bit flags such as "files copied"), 8 and above = at least one failure.
if ($LASTEXITCODE -ge 8) { throw "robocopy reported failures (exit code $LASTEXITCODE)." }

# Give the copy its own name in Prism. Prism writes instance.cfg as UTF-8 without a BOM.
$cfgPath = Join-Path $dst "instance.cfg"
$cfg = [System.IO.File]::ReadAllLines($cfgPath)
$cfg = $cfg | ForEach-Object { if ($_ -like "name=*") { "name=$Target" } else { $_ } }
[System.IO.File]::WriteAllLines($cfgPath, $cfg, (New-Object System.Text.UTF8Encoding $false))

New-Item -ItemType Directory -Force -Path (Join-Path $dst "minecraft\saves") | Out-Null

$files = Get-ChildItem $dst -Recurse -File -Force
$mods = Get-ChildItem (Join-Path $dst "minecraft\mods") -Filter *.jar -File
Write-Host ("Done: {0} files, {1:N1} GB, {2} mod jars at {3}" -f $files.Count, (($files | Measure-Object Length -Sum).Sum / 1GB), $mods.Count, $dst)
Write-Host "Restart Prism Launcher if the new instance is not listed."
