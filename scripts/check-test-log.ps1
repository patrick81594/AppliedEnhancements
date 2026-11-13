# Reads the TEST instance's latest.log after a game start and reports problems caused by our mod.
#
# Runs on the Windows HOST in PowerShell. Read-only.
# Exit code 0 = nothing found, 1 = Mixin failures, errors naming our mod, or a new crash report.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File check-test-log.ps1
param(
    [string]$Instance = "FTB Evolution 2 - AE-TEST",
    [string]$PrismRoot = (Join-Path $env:APPDATA "PrismLauncher"),
    [int]$MaxLines = 20
)

$ErrorActionPreference = "Stop"
$modId = "appliedenhancements"
$mc = Join-Path (Join-Path $PrismRoot "instances\$Instance") "minecraft"
$log = Join-Path $mc "logs\latest.log"
$marker = Join-Path $mc "ae-port-rollback\last-deploy.txt"

if (-not (Test-Path $log)) {
    Write-Host "No latest.log yet: the TEST instance has not been started. ($log)"
    exit 0
}

$logItem = Get-Item $log
$deployTime = if (Test-Path $marker) { (Get-Item $marker).LastWriteTime } else { $null }
Write-Host ("latest.log  written {0}, {1:N0} bytes" -f $logItem.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss"), $logItem.Length)
if ($deployTime) {
    Write-Host ("last deploy {0}  ({1})" -f $deployTime.ToString("yyyy-MM-dd HH:mm:ss"), (Get-Content $marker -TotalCount 1))
    if ($logItem.LastWriteTime -lt $deployTime) {
        Write-Host "WARNING: this log is OLDER than the last deploy. Start the game again before trusting it."
    }
}

$lines = [System.IO.File]::ReadAllLines($log)
function Show-Matches($title, $found) {
    $found = @($found)
    Write-Host ""
    Write-Host ("--- {0}: {1} ---" -f $title, $found.Count)
    # Write-Host, not plain output: plain output would become part of this function's return value.
    $found | Select-Object -First $MaxLines | ForEach-Object {
        if ($_.Length -gt 260) { Write-Host ($_.Substring(0, 260) + " ...") } else { Write-Host $_ }
    }
    if ($found.Count -gt $MaxLines) { Write-Host ("... {0} more" -f ($found.Count - $MaxLines)) }
    return $found.Count
}

# 1. Is our mod there at all?
$mentions = @($lines | Where-Object { $_ -match $modId })
Write-Host ""
Write-Host ("--- lines mentioning {0}: {1} (first 3) ---" -f $modId, $mentions.Count)
$mentions | Select-Object -First 3 | ForEach-Object { if ($_.Length -gt 260) { $_.Substring(0, 260) + " ..." } else { $_ } }

# 2. Mixin failures (from any mod; ours are the lines that name appliedenhancements.mixins.json).
$mixinPattern = 'InvalidInjectionException|InvalidMixinException|MixinApplyError|MixinTransformerError|Mixin apply failed|Critical injection failure|Mixin transformation of .* failed'
$mixinAll = @($lines | Where-Object { $_ -match $mixinPattern })
$mixinOurs = Show-Matches "Mixin failures naming $modId" ($mixinAll | Where-Object { $_ -match $modId })
$null = Show-Matches "Mixin failures from other mods (for context)" ($mixinAll | Where-Object { $_ -notmatch $modId })

# 3. ERROR / FATAL lines that name our mod or its packages.
$errorLines = @($lines | Where-Object { $_ -match '/(ERROR|FATAL)\]' })
$errorsOurs = Show-Matches "ERROR/FATAL lines naming $modId" ($errorLines | Where-Object { $_ -match "$modId|com\.github\.appliedenhancements" })
Write-Host ""
Write-Host ("(all ERROR/FATAL lines in the log, any mod: {0})" -f $errorLines.Count)

# 4. Crash reports written after the last deploy.
$crashDir = Join-Path $mc "crash-reports"
$crashes = @()
if (Test-Path $crashDir) {
    $crashes = @(Get-ChildItem $crashDir -File | Where-Object { -not $deployTime -or $_.LastWriteTime -gt $deployTime } | Sort-Object LastWriteTime -Descending)
}
Write-Host ""
Write-Host ("--- crash reports since the last deploy: {0} ---" -f $crashes.Count)
$crashes | Select-Object -First 5 | ForEach-Object { "{0}  {1}" -f $_.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss"), $_.FullName }

$bad = ($mixinOurs -gt 0) -or ($errorsOurs -gt 0) -or ($crashes.Count -gt 0)
Write-Host ""
if ($bad) { Write-Host "RESULT: problems found (see above)."; exit 1 }
if ($mentions.Count -eq 0) { Write-Host "RESULT: no problems, but the log never mentions $modId - is the jar deployed and was the game started?"; exit 0 }
Write-Host "RESULT: no Mixin failures, no errors naming $modId, no new crash reports."
exit 0
