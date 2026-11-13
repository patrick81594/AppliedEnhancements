# Launches the Prism TEST instance, joins a world, waits until the world is loaded (or the game
# crashes or exits), then runs check-test-log.ps1. The game stays open for manual testing.
#
# Runs on the Windows HOST in PowerShell. Safety: the instance name must contain "TEST".
#
# The world must exist. On the very first start it does not: the script then launches to the title
# screen and you create a CREATIVE world with the name given by -World (default "AE Test").
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File run-test.ps1            # launch + join + log check
#   powershell -ExecutionPolicy Bypass -File run-test.ps1 -Deploy    # deploy the newest jar first
param(
    [string]$Instance = "FTB Evolution 2 - AE-TEST",
    [string]$World = "AE Test",
    [switch]$Deploy,
    [int]$TimeoutMinutes = 15,
    [string]$PrismRoot = (Join-Path $env:APPDATA "PrismLauncher"),
    [string]$PrismExe = (Join-Path $env:LOCALAPPDATA "Programs\PrismLauncher\prismlauncher.exe")
)

$ErrorActionPreference = "Stop"
if ($Instance -notmatch "TEST") { throw "Refusing to launch '$Instance': the instance name must contain TEST." }
$mc = Join-Path (Join-Path $PrismRoot "instances\$Instance") "minecraft"
if (-not (Test-Path $mc)) { throw "TEST instance not found (run make-test-instance.ps1 first): $mc" }
if (-not (Test-Path $PrismExe)) { throw "Prism Launcher not found: $PrismExe (pass -PrismExe)." }

if ($Deploy) {
    & (Join-Path $PSScriptRoot "deploy-test.ps1") -Instance $Instance -PrismRoot $PrismRoot
}

# The game process of an instance is a javaw.exe whose command line contains the instance folder.
function Get-GameProcess {
    Get-CimInstance Win32_Process -Filter "Name = 'javaw.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.Contains("instances/$Instance/") }
}
if (Get-GameProcess) { throw "The TEST instance is already running. Close it first." }

# Prism command line: --launch <instance folder name>, --world <world name> (joins it via quick play).
$arguments = @("--launch", "`"$Instance`"")
$worldExists = Test-Path (Join-Path $mc "saves\$World")
if ($worldExists) {
    $arguments += @("--world", "`"$World`"")
    Write-Host "Launching '$Instance' and joining world '$World' ..."
} else {
    Write-Host "World '$World' does not exist yet: launching to the title screen."
    Write-Host "Create a CREATIVE world named '$World'; this script keeps waiting until you are in it."
}

$started = Get-Date
Start-Process -FilePath $PrismExe -ArgumentList $arguments

$log = Join-Path $mc "logs\latest.log"
$crashDir = Join-Path $mc "crash-reports"
$deadline = $started.AddMinutes($TimeoutMinutes)
$seenGame = $false
$outcome = "timeout"
$offset = 0L

while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 3

    $game = Get-GameProcess
    if ($game) { $seenGame = $true }

    # Read only what was appended since the last look; share the file with the game, which is writing it.
    if ((Test-Path $log) -and (Get-Item $log).LastWriteTime -ge $started) {
        $stream = New-Object System.IO.FileStream($log, 'Open', 'Read', 'ReadWrite')
        try {
            if ($stream.Length -lt $offset) { $offset = 0 }
            $null = $stream.Seek($offset, 'Begin')
            $reader = New-Object System.IO.StreamReader($stream)
            $chunk = $reader.ReadToEnd()
            $offset = $stream.Length
        } finally { $stream.Dispose() }
        # The integrated server logs "<player> joined the game" once the player is in the world.
        if ($chunk -match "joined the game") { $outcome = "joined"; break }
    }

    if ((Test-Path $crashDir) -and (Get-ChildItem $crashDir -File | Where-Object { $_.LastWriteTime -ge $started })) {
        $outcome = "crashed"; break
    }
    if ($seenGame -and -not $game) { $outcome = "exited"; break }
    if (-not $seenGame -and ((Get-Date) - $started).TotalMinutes -ge 3) { $outcome = "not started"; break }
}

$seconds = [int]((Get-Date) - $started).TotalSeconds
switch ($outcome) {
    "joined"      { Write-Host "World joined after $seconds s. The game stays open." }
    "crashed"     { Write-Host "A crash report was written after $seconds s." }
    "exited"      { Write-Host "The game process ended after $seconds s without joining a world." }
    "not started" { Write-Host "No game process appeared within 3 minutes (check Prism: account, launch errors)." }
    default       { Write-Host "Timed out after $TimeoutMinutes minutes without joining a world." }
}

Write-Host ""
& (Join-Path $PSScriptRoot "check-test-log.ps1") -Instance $Instance -PrismRoot $PrismRoot
$checkCode = $LASTEXITCODE
if ($outcome -ne "joined") { exit 1 }
exit $checkCode
