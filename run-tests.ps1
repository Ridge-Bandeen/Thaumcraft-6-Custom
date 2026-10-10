# Full test cycle for the port: the GameTest server twice (to catch flaky tests), then the unit tests.
# Run from the repo root:  powershell -ExecutionPolicy Bypass -File .\run-tests.ps1
# Results land in build\test-logs\: summary.txt (what Claude reads) plus the full logs.

$ErrorActionPreference = 'Continue'
Set-Location -Path $PSScriptRoot
$logs = Join-Path $PSScriptRoot 'build\test-logs'
New-Item -ItemType Directory -Force -Path $logs | Out-Null
$summary = Join-Path $logs 'summary.txt'

function Get-JavaProcs([string]$pattern) {
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -match $pattern }
}

# Gradle must not run while the dev client is open
if (Get-JavaProcs 'devlaunch') {
    Write-Host 'The game client is running - close it first.' -ForegroundColor Red
    exit 1
}

$start = Get-Date
$lines = @("Test run started $start", "Commit: $(git rev-parse --short HEAD)", '')

foreach ($run in 'a', 'b') {
    # leftover GameTest servers from an earlier interrupted run
    Get-JavaProcs 'gametest|GameTestServer' | Where-Object { $_.CommandLine -notmatch 'GradleDaemon|gradle-server|devlaunch' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }

    $log = Join-Path $logs "gametest_$run.log"
    Write-Host "GameTest run $run..." -ForegroundColor Cyan
    & .\gradlew.bat runGameTestServer *> $log
    $result = Select-String -Path $log -Pattern 'required tests passed|required tests failed|tests failed' | Select-Object -Last 1
    $failures = Select-String -Path $log -Pattern 'ERROR\].*LogTestReporter' | ForEach-Object { '  ' + $_.Line.Trim() }
    $lines += "== GameTest run $run"
    if ($result) { $lines += $result.Line.Trim() } else { $lines += 'NO RESULT LINE - the run did not finish (see the log)'; $lines += (Get-Content $log -Tail 30) }
    $lines += $failures
    $lines += ''
}

$log = Join-Path $logs 'unit.log'
Write-Host 'Unit tests...' -ForegroundColor Cyan
& .\gradlew.bat test *> $log
$lines += '== Unit tests'
$lines += (Select-String -Path $log -Pattern 'BUILD SUCCESSFUL|BUILD FAILED|tests completed|FAILED' | ForEach-Object { $_.Line.Trim() })
$lines += ''
$lines += "Finished $(Get-Date) ($([int]((Get-Date) - $start).TotalMinutes) min)"

$lines | Out-File -FilePath $summary -Encoding utf8
Get-Content $summary
Write-Host "`nSummary saved to $summary" -ForegroundColor Green
