[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$harness = Join-Path $root 'run-gatey5-lock-window-drill.ps1'
$fixture = Join-Path $root 'gatey5-pre-fixture.sql'
$launcher = Join-Path $root 'GateY5FlywayLauncher.java'
foreach ($path in @($harness, $fixture, $launcher)) {
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw "missing tooling: $path" }
}

$harnessText = Get-Content -Raw -LiteralPath $harness
$fixtureText = Get-Content -Raw -LiteralPath $fixture
$launcherText = Get-Content -Raw -LiteralPath $launcher
foreach ($marker in @(
    'ConfirmDisposable', '127.0.0.1::5432', 'postgres:16-alpine',
    'MIGRATION_FAILURE_ATOMICITY_NOT_PROVEN', 'LOCK_TIMEOUT_BOUND_VIOLATED',
    "statement_timeout='60s'", 'Wait-ForApplication $sourceContainer $longRead 120',
    'pg_restore', 'cleanDisabled(true)', 'target(args[4])',
    'pg_blocking_pids', 'LOCK_CONFLICT_GRAPH_NOT_OBSERVED', 'lockGraph',
    'pg_terminate_backend', 'blockersReleased'
)) {
    if (-not ($harnessText + $launcherText).Contains($marker)) { throw "missing marker: $marker" }
}
foreach ($marker in @('generate_series', 'gatey-production-like-scale-v1', 'synthetic-disabled-hash', "'REVOKED', false")) {
    if (-not $fixtureText.Contains($marker)) { throw "fixture marker missing: $marker" }
}
foreach ($forbidden in @('Invoke-WebRequest', 'Invoke-RestMethod', 'systemctl', 'flyway repair', 'apiKey', 'passphrase')) {
    if (($harnessText + $fixtureText + $launcherText).IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -ge 0) {
        throw "forbidden behavior: $forbidden"
    }
}

Write-Output 'PASS / GATEY5_LOCK_WINDOW_TOOLING_REGRESSION'
