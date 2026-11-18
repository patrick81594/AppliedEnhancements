# Copies the newest built Applied Enhancements jar into a Prism TEST instance, or undoes that.
#
# Runs on the Windows HOST in PowerShell. Build the jar first (in the dev container: ./gradlew build).
# Safety: the instance name must contain "TEST", so this can never write into the real pack instance.
# Any Applied Enhancements jar already in mods/ is moved to minecraft\ae-port-rollback\<timestamp>\ first,
# so the game never sees two versions of the mod.
#
# World backups: before every deploy and rollback, each world in minecraft\saves\ is zipped to
# minecraft\ae-port-world-backups\<timestamp>\<world>.zip (the zip contains the world folder: restore by
# extracting it into saves\ with the game closed). The newest -KeepWorldBackups sets are kept, older ones deleted.
# Refuses to run while the instance's game is running (worlds and the jar are in use).
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File deploy-test.ps1             # deploy the newest jar
#   powershell -ExecutionPolicy Bypass -File deploy-test.ps1 -Rollback   # undo the last deploy
#   ... -NoWorldBackup                                                   # skip the world backup
param(
    [string]$Instance = "FTB Evolution 2 - AE-TEST",
    [string]$JarDir = (Join-Path (Split-Path $PSScriptRoot -Parent) "build\libs"),
    [string]$PrismRoot = (Join-Path $env:APPDATA "PrismLauncher"),
    [switch]$Rollback,
    [switch]$NoWorldBackup,
    [int]$KeepWorldBackups = 5
)

$ErrorActionPreference = "Stop"
$jarPattern = "appliedenhancements-*.jar"

if ($Instance -notmatch "TEST") { throw "Refusing to touch '$Instance': the instance name must contain TEST." }
$mc = Join-Path (Join-Path $PrismRoot "instances\$Instance") "minecraft"
$mods = Join-Path $mc "mods"
if (-not (Test-Path $mods)) { throw "TEST instance not found (run make-test-instance.ps1 first): $mods" }

$rollbackRoot = Join-Path $mc "ae-port-rollback"
$marker = Join-Path $rollbackRoot "last-deploy.txt"
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
New-Item -ItemType Directory -Force -Path $rollbackRoot | Out-Null

function Show-Jar($path) {
    $item = Get-Item $path
    $hash = (Get-FileHash $path -Algorithm SHA256).Hash
    "{0}  {1:N0} bytes  SHA-256 {2}" -f $item.Name, $item.Length, $hash
}

# The game process of an instance is a javaw.exe whose command line contains the instance folder.
$running = Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe'" |
    Where-Object { $_.CommandLine -and $_.CommandLine.Contains("instances/$Instance/") }
if ($running) { throw "The '$Instance' game is running: close it first (its worlds and the mod jar are in use)." }

function Backup-Worlds {
    $saves = Join-Path $mc "saves"
    $worlds = @(Get-ChildItem $saves -Directory -ErrorAction SilentlyContinue)
    if ($worlds.Count -eq 0) { Write-Host "World backup: no worlds in $saves"; return }
    $root = Join-Path $mc "ae-port-world-backups"
    $dir = Join-Path $root $stamp
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    foreach ($w in $worlds) {
        $zip = Join-Path $dir ($w.Name + ".zip")
        # includeBaseDirectory = $true: the zip holds "<world>\...", so extracting into saves\ restores it.
        [System.IO.Compression.ZipFile]::CreateFromDirectory($w.FullName, $zip, [System.IO.Compression.CompressionLevel]::Optimal, $true)
        Write-Host ("World backup: {0} -> {1:N1} MB" -f $w.Name, ((Get-Item $zip).Length / 1MB))
    }
    # Keep only the newest sets; these folders are created by this script alone.
    $old = @(Get-ChildItem $root -Directory | Sort-Object Name -Descending | Select-Object -Skip $KeepWorldBackups)
    foreach ($o in $old) {
        Remove-Item -LiteralPath $o.FullName -Recurse -Confirm:$false
        Write-Host "World backup: removed old set $($o.Name)"
    }
    Write-Host "World backups in $dir"
}
if (-not $NoWorldBackup) { Backup-Worlds }

$installed = @(Get-ChildItem $mods -Filter $jarPattern -File)

if ($Rollback) {
    # Newest rollback folder that actually holds a jar; none = the instance goes back to the plain pack.
    $previous = Get-ChildItem $rollbackRoot -Directory | Sort-Object Name -Descending |
        Where-Object { @(Get-ChildItem $_.FullName -Filter $jarPattern -File).Count -gt 0 } | Select-Object -First 1
    foreach ($jar in $installed) {
        Write-Host ("Removing  " + (Show-Jar $jar.FullName))
        Remove-Item -LiteralPath $jar.FullName -Confirm:$false
    }
    if ($previous) {
        foreach ($jar in Get-ChildItem $previous.FullName -Filter $jarPattern -File) {
            Move-Item -LiteralPath $jar.FullName -Destination $mods
            Write-Host ("Restored  " + (Show-Jar (Join-Path $mods $jar.Name)))
        }
        Remove-Item -LiteralPath $previous.FullName -Recurse -Confirm:$false
    } else {
        Write-Host "No earlier build to restore: the TEST instance now has the plain pack mods only."
    }
    Set-Content -LiteralPath $marker -Value "$stamp rollback" -Encoding ascii
    return
}

if (-not (Test-Path $JarDir)) { throw "No build output folder: $JarDir (build the mod first)." }
$new = Get-ChildItem $JarDir -Filter $jarPattern -File | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (-not $new) { throw "No $jarPattern in $JarDir (build the mod first)." }

if ($installed.Count -gt 0) {
    $backup = Join-Path $rollbackRoot $stamp
    New-Item -ItemType Directory -Force -Path $backup | Out-Null
    foreach ($jar in $installed) {
        Write-Host ("Replacing " + (Show-Jar $jar.FullName))
        # Fails if the TEST game is running and holds the jar open: close the game and deploy again.
        Move-Item -LiteralPath $jar.FullName -Destination $backup
    }
}

Copy-Item -LiteralPath $new.FullName -Destination $mods
$deployed = Join-Path $mods $new.Name
$sourceHash = (Get-FileHash $new.FullName -Algorithm SHA256).Hash
$deployedHash = (Get-FileHash $deployed -Algorithm SHA256).Hash
if ($sourceHash -ne $deployedHash) { throw "Hash mismatch after copy: $deployed" }

Write-Host ("Deployed  " + (Show-Jar $deployed))
Write-Host ("Built     " + $new.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss"))
Write-Host ("Mods now  {0} jars in {1}" -f @(Get-ChildItem $mods -Filter *.jar -File).Count, $mods)
Set-Content -LiteralPath $marker -Value "$stamp deploy $($new.Name) $deployedHash" -Encoding ascii
