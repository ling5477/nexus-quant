[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$sourceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$tempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$tempRoot = Join-Path $tempBase ("nq-archive-fixture-{0}" -f [guid]::NewGuid().ToString('N'))

function Assert-True {
    param([bool] $Condition, [string] $Message)
    if (-not $Condition) { throw $Message }
}

function Write-FixtureFile {
    param([string] $Path)
    $body = (1..10 | ForEach-Object { "Archive role line $_ records verified facts, boundaries, risks, rollback, and independent evidence." }) -join "`n"
    [System.IO.File]::WriteAllText($Path, $body, (New-Object System.Text.UTF8Encoding($false)))
}

function Invoke-ArchiveFixture {
    $hostExe = (Get-Process -Id $PID).Path
    $scriptPath = Join-Path $tempRoot 'scripts\docs\check-gate-archive.ps1'
    $output = & $hostExe -NoProfile -ExecutionPolicy Bypass -File $scriptPath -Gate gate-z -PreTag 2>&1
    return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = (($output | ForEach-Object { $_.ToString() }) -join "`n") }
}

try {
    New-Item -ItemType Directory -Path (Join-Path $tempRoot 'scripts\docs') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $tempRoot 'docs\gates\gate-z') -Force | Out-Null
    foreach ($file in @('check-gate-archive.ps1', 'governance-workflow-lib.ps1', 'governance-workflow-contract.json', 'gate-archive-manifest.json')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $file) -Destination (Join-Path $tempRoot 'scripts\docs')
    }

    $gateRoot = Join-Path $tempRoot 'docs\gates\gate-z'
    foreach ($name in @(
        'README.md', 'FREEZE_CLOSEOUT.md', 'FREEZE_READINESS.md', 'GENERIC_PLAN.md', 'EVIDENCE_MATRIX.md',
        'TESTING_EVIDENCE_SUMMARY.md', 'BOUNDARY_STATEMENT.md', 'KNOWN_LIMITATIONS.md',
        'BACKEND_EVIDENCE_SUMMARY.md', 'API_EVIDENCE_SUMMARY.md', 'FRONTEND_EVIDENCE_SUMMARY.md',
        'PYTHON_BOUNDARY_EVIDENCE.md', 'RUNTIME_EVIDENCE_SUMMARY.md'
    )) { Write-FixtureFile (Join-Path $gateRoot $name) }

    $valid = Invoke-ArchiveFixture
    Assert-True ($valid.ExitCode -eq 0 -and $valid.Output -match 'profile=default') "Generic future Gate policy failed: $($valid.Output)"
    Write-Output 'PASS fixture=generic-future-gate-default-policy'

    $mandatory = Join-Path $gateRoot 'API_EVIDENCE_SUMMARY.md'
    Remove-Item -LiteralPath $mandatory -Force
    $missingResult = Invoke-ArchiveFixture
    Assert-True ($missingResult.ExitCode -ne 0 -and $missingResult.Output -match 'ROLE_MISSING role=api-evidence') 'Missing mandatory role was accepted.'
    Write-FixtureFile $mandatory
    Write-Output 'PASS fixture=mandatory-role-fail-closed'

    [System.IO.File]::WriteAllText($mandatory, 'thin', (New-Object System.Text.UTF8Encoding($false)))
    $thinResult = Invoke-ArchiveFixture
    Assert-True ($thinResult.ExitCode -ne 0 -and $thinResult.Output -match 'THIN_ROLE role=api-evidence') 'Thin role was accepted.'
    Write-FixtureFile $mandatory
    Write-Output 'PASS fixture=thin-role-fail-closed'

    $unknown = Join-Path $gateRoot 'UNKNOWN.md'
    Write-FixtureFile $unknown
    $unknownResult = Invoke-ArchiveFixture
    Assert-True ($unknownResult.ExitCode -ne 0 -and $unknownResult.Output -match 'UNKNOWN_ARCHIVE_FILE') 'Unknown archive file was accepted.'
    Remove-Item -LiteralPath $unknown -Force
    Write-Output 'PASS fixture=unknown-file-fail-closed'

    $outside = Join-Path $tempRoot 'outside'
    New-Item -ItemType Directory -Path $outside | Out-Null
    $link = Join-Path $gateRoot 'reparse-link'
    if ($env:OS -eq 'Windows_NT') { New-Item -ItemType Junction -Path $link -Target $outside | Out-Null }
    else { New-Item -ItemType SymbolicLink -Path $link -Target $outside | Out-Null }
    $reparseResult = Invoke-ArchiveFixture
    Assert-True ($reparseResult.ExitCode -ne 0 -and $reparseResult.Output -match 'SYMLINK_ARCHIVE_ITEM_FORBIDDEN') 'Reparse archive item was accepted.'
    Remove-Item -LiteralPath $link -Force
    Write-Output 'PASS fixture=reparse-fail-closed'

    $manifest = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'gate-archive-manifest.json')) | ConvertFrom-Json
    Assert-True ($null -ne $manifest.defaultStrictPolicy) 'Default strict policy missing.'
    Assert-True ($null -ne $manifest.historicalProfiles.'gate-y') 'Frozen historical profile missing.'
    Write-Output 'PASS fixture=historical-profile-preserved'
} finally {
    $resolved = [System.IO.Path]::GetFullPath($tempRoot)
    if ($resolved.StartsWith($tempBase, [System.StringComparison]::OrdinalIgnoreCase) -and (Test-Path -LiteralPath $resolved)) {
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}

Write-Output 'SUMMARY gate-archive-manifest passed=6 failed=0'
exit 0
