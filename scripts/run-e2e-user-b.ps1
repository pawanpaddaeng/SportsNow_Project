#!/usr/bin/env pwsh
<#
Shows the manual tester what User B has to do during the frontend E2E tests.

Run it in a second terminal before or while scripts\run-frontend-e2e-tests.ps1 runs.
Each time a test needs User B, one line starting with "USER B:" is shown. Do that step
on User B's device, then press Enter here: the test on User A's emulator carries on.
Stop it with Ctrl+C once the tests have finished.

If User B's emulator is also connected to adb, set ANDROID_SERIAL to User A's emulator
first, as for the tests (see Testing.md, "Running the tests on User A's device only").

Usage: .\scripts\run-e2e-user-b.ps1
#>

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$Root = (Resolve-Path (Join-Path (Split-Path -Parent $MyInvocation.MyCommand.Path) '..')).Path
$LocalProperties = Join-Path $Root 'frontend\local.properties'

# must match LOG_TAG, USER_B_DONE_FILE and USER_B_DONE in the E2E tests
$LogTag = 'FriendSystemTest'
$DoneFile = '/data/local/tmp/e2e-user-b-done'
$Done = 'done'

function Info($msg) { Write-Host "==> $msg" -ForegroundColor Blue }
function Die($msg)  { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }

# adb from ANDROID_HOME, sdk.dir in frontend\local.properties, or the PATH
function Find-Adb {
    $sdkDir = $env:ANDROID_HOME
    if (-not $sdkDir -and (Test-Path $LocalProperties)) {
        $line = Get-Content $LocalProperties | Where-Object { $_ -match '^\s*sdk\.dir\s*=' } | Select-Object -First 1
        if ($line) {
            $sdkDir = (($line -replace '^\s*sdk\.dir\s*=\s*', '').Trim(' ', '"') -replace '\\:', ':') -replace '\\\\', '\'
        }
    }
    if ($sdkDir) {
        $adb = Join-Path $sdkDir 'platform-tools\adb.exe'
        if (Test-Path $adb) { return $adb }
    }
    $command = Get-Command adb -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    Die 'adb not found. Set ANDROID_HOME or sdk.dir in frontend\local.properties.'
}

$Adb = Find-Adb

# only steps logged from now on, not ones from an earlier run
$since = ((& $Adb shell "date '+%m-%d %H:%M:%S.000'") | Out-String).Trim()
if (-not $since) { Die 'No device found. Start User A''s emulator first.' }

Info 'Waiting for User B steps. Keep this open while the E2E tests run, Ctrl+C to stop.'
& $Adb logcat -v raw -s "${LogTag}:I" -T $since | ForEach-Object {
    if ($_ -match '^USER B: (.+)$') {
        Write-Host ''
        Write-Host "USER B: $($Matches[1])" -ForegroundColor Yellow
        [void](Read-Host 'Press Enter when done')
        & $Adb shell "echo $Done > $DoneFile"
        Info 'Sent, the test carries on.'
    }
}
