# Creates a MINIMAL Prism Launcher instance: NeoForge + AE2 + GuideME + this mod, nothing else.
# Proves the mod loads and works without its optional dependencies (JEI, ExtendedAE, ae2wtlib, AdvancedAE).
#
# Runs on the Windows HOST in PowerShell. The source (TEST) instance is only READ. The new instance is not a
# managed CurseForge pack (no ManagedPack* keys), so Prism never tries to update it into the full pack.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File make-minimal-instance.ps1              # AE2 + GuideME + ours
#   powershell -ExecutionPolicy Bypass -File make-minimal-instance.ps1 -WithJei     # ... + JEI
param(
    [string]$Source = "FTB Evolution 2 - AE-TEST",
    [string]$Target = "AE-MINIMAL-TEST",
    [switch]$WithJei,
    [string]$PrismRoot = (Join-Path $env:APPDATA "PrismLauncher")
)

$ErrorActionPreference = "Stop"
if ($Target -notmatch "TEST") { throw "Refusing '$Target': the instance name must contain TEST." }
$instances = Join-Path $PrismRoot "instances"
$src = Join-Path $instances $Source
$dst = Join-Path $instances $Target
if (-not (Test-Path (Join-Path $src "mmc-pack.json"))) { throw "Source instance not found: $src" }
if (Test-Path $dst) { throw "Target already exists: $dst (delete it in Prism first; this script never overwrites)." }

# Required mods only. Patterns match the jar names in the source instance's mods folder.
$patterns = @("appliedenergistics2-*.jar", "guideme-*.jar", "appliedenhancements-*.jar")
if ($WithJei) { $patterns += "jei-*-neoforge-*.jar" }
$srcMods = Join-Path $src "minecraft\mods"
$jars = foreach ($p in $patterns) {
    $hit = @(Get-ChildItem $srcMods -Filter $p -File)
    if ($hit.Count -ne 1) { throw "Expected exactly one '$p' in $srcMods, found $($hit.Count)." }
    $hit[0]
}

New-Item -ItemType Directory -Force -Path (Join-Path $dst "minecraft\mods"), (Join-Path $dst "minecraft\saves") | Out-Null
# Same Minecraft / NeoForge / LWJGL components as the source instance.
Copy-Item (Join-Path $src "mmc-pack.json") (Join-Path $dst "mmc-pack.json")

# Java: the same runtime the source instance uses (Prism's Java 25).
$javaLine = Get-Content (Join-Path $src "instance.cfg") | Where-Object { $_ -like "JavaPath=*" } | Select-Object -First 1
$cfg = @(
    "[General]",
    "ConfigVersion=1.3",
    # Prism 11 identifies instances by this uuid (32 hex digits, no dashes); without it the instance is not listed.
    "uuid=" + [guid]::NewGuid().ToString("N"),
    "InstanceType=OneSix",
    "name=$Target",
    "iconKey=default",
    $javaLine,
    "OverrideJavaLocation=true",
    "OverrideMemory=true",
    "MinMemAlloc=512",
    "MaxMemAlloc=4096"
) | Where-Object { $_ }
[System.IO.File]::WriteAllLines((Join-Path $dst "instance.cfg"), $cfg, (New-Object System.Text.UTF8Encoding $false))

foreach ($j in $jars) { Copy-Item $j.FullName (Join-Path $dst "minecraft\mods\$($j.Name)") }

Write-Host "Created '$Target' at $dst"
Get-ChildItem (Join-Path $dst "minecraft\mods") -File | ForEach-Object { Write-Host ("  {0,-48} {1,12:N0} bytes" -f $_.Name, $_.Length) }
Write-Host "Restart Prism Launcher if the new instance is not listed."
