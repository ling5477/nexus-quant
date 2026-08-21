[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$proofPath = Join-Path $repoRoot "scripts\java-standard\get-shadow-report-proof-hash.ps1"
$currentPwsh = [Diagnostics.Process]::GetCurrentProcess().MainModule.FileName

function Assert-Equal([object]$Expected, [object]$Actual, [string]$Name) {
    if ($Expected -cne $Actual) { throw "ASSERT_EQUAL_FAILED: $Name expected=$Expected actual=$Actual" }
}

function Assert-NotEqual([object]$Left, [object]$Right, [string]$Name) {
    if ($Left -ceq $Right) { throw "ASSERT_NOT_EQUAL_FAILED: $Name value=$Left" }
}

function New-Finding([string]$RuleId, [string]$Path, [string]$Classification, [string]$Fingerprint) {
    return [pscustomobject][ordered]@{
        rule_id = $RuleId
        path = $Path
        classification = $Classification
        fingerprint = $Fingerprint
        summary = "volatile display text"
        severity = "P2"
    }
}

function New-Report([object[]]$Findings, [string]$Schema = '2.0.0', [string]$GeneratedAt = '2026-08-21T00:00:00Z', [string]$Artifact = 'artifacts/run-1.json') {
    $report = [ordered]@{
        schema_version = $Schema
        status = if ($Findings.Count) { 'VIOLATION_FOUND' } else { 'PASS' }
        generated_at_utc = $GeneratedAt
        report_artifact = $Artifact
    }
    if ($Schema -eq '2.0.0') { $report.current_violation_count = $Findings.Count }
    else { $report.current_count = $Findings.Count }
    $report.violations = $Findings
    return [pscustomobject]$report
}

function Write-Report([string]$Path, [object]$Report, [string]$Eol = "`n") {
    $json = ConvertTo-Json -InputObject $Report -Depth 10
    $json = $json.Replace("`r`n", "`n").Replace("`n", $Eol) + $Eol
    [IO.File]::WriteAllText($Path, $json, [Text.UTF8Encoding]::new($false))
}

function Invoke-Proof([string]$Path) {
    $output = @(& $currentPwsh -NoProfile -File $proofPath -ReportPath $Path 2>&1 | ForEach-Object { $_.ToString() })
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) { throw "PROOF_COMMAND_FAILED: exit=$exitCode output=$($output -join ' | ')" }
    $hashLine = @($output | Where-Object { $_.StartsWith('REPORT_PROOF_SHA256=', [StringComparison]::Ordinal) })
    Assert-Equal 1 $hashLine.Count "single proof hash output"
    return $hashLine[0].Substring('REPORT_PROOF_SHA256='.Length)
}

function Assert-ProofFails([string]$Path, [string]$Name) {
    $output = @(& $currentPwsh -NoProfile -File $proofPath -ReportPath $Path 2>&1 | ForEach-Object { $_.ToString() })
    Assert-Equal 2 $LASTEXITCODE "$Name exit code"
    if (-not (($output -join "`n").Contains('REPORT_PROOF_INVALID:'))) {
        throw "ASSERT_FAIL_CLOSED_FAILED: $Name output=$($output -join ' | ')"
    }
}

$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ("shadow-report-proof-{0}" -f [guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($tempRoot) | Out-Null
try {
    $findings = @(
        (New-Finding 'JAVA-RULE-B' 'module/src/main/java/B.java' 'EXISTING_BASELINE_FINDING' 'bbbbbbbbbbbbbbbbbbbbbbbb'),
        (New-Finding 'JAVA-RULE-A' 'module/src/main/java/A.java' 'RULESET_EXPANSION_FINDING' 'aaaaaaaaaaaaaaaaaaaaaaaa')
    )
    $basePath = Join-Path $tempRoot 'base.json'
    Write-Report $basePath (New-Report $findings)
    $baseHash = Invoke-Proof $basePath
    Assert-Equal $baseHash (Invoke-Proof $basePath) 'same report twice'

    $crlfPath = Join-Path $tempRoot 'crlf.json'
    Write-Report $crlfPath (New-Report $findings) "`r`n"
    Assert-Equal $baseHash (Invoke-Proof $crlfPath) 'LF/CRLF stability'

    $volatilePath = Join-Path $tempRoot 'volatile.json'
    Write-Report $volatilePath (New-Report $findings '2.0.0' '2099-01-01T00:00:00Z' 'tmp/random/output.json')
    Assert-Equal $baseHash (Invoke-Proof $volatilePath) 'generatedAt/output path stability'

    $reorderedPath = Join-Path $tempRoot 'reordered.json'
    Write-Report $reorderedPath (New-Report @($findings[1], $findings[0]))
    Assert-Equal $baseHash (Invoke-Proof $reorderedPath) 'finding order stability'

    $schema3Path = Join-Path $tempRoot 'schema3.json'
    Write-Report $schema3Path (New-Report $findings '3.0.0')
    Assert-Equal $baseHash (Invoke-Proof $schema3Path) 'supported report schema stability'

    $mutations = @(
        @('rule_id', (New-Finding 'JAVA-RULE-C' $findings[0].path $findings[0].classification $findings[0].fingerprint)),
        @('path', (New-Finding $findings[0].rule_id 'module/src/main/java/C.java' $findings[0].classification $findings[0].fingerprint)),
        @('classification', (New-Finding $findings[0].rule_id $findings[0].path 'NEW_CODE_FINDING' $findings[0].fingerprint)),
        @('fingerprint', (New-Finding $findings[0].rule_id $findings[0].path $findings[0].classification 'cccccccccccccccccccccccc'))
    )
    foreach ($mutation in $mutations) {
        $path = Join-Path $tempRoot ("mutation-$($mutation[0]).json")
        Write-Report $path (New-Report @($mutation[1], $findings[1]))
        Assert-NotEqual $baseHash (Invoke-Proof $path) "$($mutation[0]) mutation sensitivity"
    }

    $addedPath = Join-Path $tempRoot 'added.json'
    $added = New-Finding 'JAVA-RULE-C' 'module/src/main/java/C.java' 'NEW_CODE_FINDING' 'cccccccccccccccccccccccc'
    Write-Report $addedPath (New-Report @($findings[0], $findings[1], $added))
    Assert-NotEqual $baseHash (Invoke-Proof $addedPath) 'finding addition sensitivity'

    $removedPath = Join-Path $tempRoot 'removed.json'
    Write-Report $removedPath (New-Report @($findings[0]))
    Assert-NotEqual $baseHash (Invoke-Proof $removedPath) 'finding removal sensitivity'

    $invalidSchemaPath = Join-Path $tempRoot 'invalid-schema.json'
    Write-Report $invalidSchemaPath (New-Report $findings '9.0.0')
    Assert-ProofFails $invalidSchemaPath 'invalid schema'

    $countMismatchPath = Join-Path $tempRoot 'count-mismatch.json'
    $countMismatch = New-Report $findings
    $countMismatch.current_violation_count = 999
    Write-Report $countMismatchPath $countMismatch
    Assert-ProofFails $countMismatchPath 'count mismatch'

    $backslashPath = Join-Path $tempRoot 'backslash-path.json'
    $backslashFinding = New-Finding 'JAVA-RULE-C' 'module\src\main\java\C.java' 'NEW_CODE_FINDING' 'cccccccccccccccccccccccc'
    Write-Report $backslashPath (New-Report @($findings[0], $backslashFinding))
    Assert-ProofFails $backslashPath 'non-canonical path separator'

    $duplicatePath = Join-Path $tempRoot 'duplicate.json'
    $duplicate = New-Finding $findings[0].rule_id $findings[0].path 'NEW_CODE_FINDING' $findings[0].fingerprint
    Write-Report $duplicatePath (New-Report @($findings[0], $duplicate))
    Assert-ProofFails $duplicatePath 'duplicate finding identity'

    $bomPath = Join-Path $tempRoot 'bom.json'
    $baseBytes = [IO.File]::ReadAllBytes($basePath)
    $bomBytes = [byte[]]::new($baseBytes.Length + 3)
    $bomBytes[0] = 0xef; $bomBytes[1] = 0xbb; $bomBytes[2] = 0xbf
    [Array]::Copy($baseBytes, 0, $bomBytes, 3, $baseBytes.Length)
    [IO.File]::WriteAllBytes($bomPath, $bomBytes)
    Assert-ProofFails $bomPath 'UTF-8 BOM'

    $bareCrPath = Join-Path $tempRoot 'bare-cr.json'
    $baseText = [Text.UTF8Encoding]::new($false, $true).GetString($baseBytes)
    [IO.File]::WriteAllText($bareCrPath, $baseText.Replace("`n", "`r"), [Text.UTF8Encoding]::new($false))
    Assert-ProofFails $bareCrPath 'bare CR'

    $invalidUtf8Path = Join-Path $tempRoot 'invalid-utf8.json'
    [IO.File]::WriteAllBytes($invalidUtf8Path, [byte[]]@(0xc3, 0x28))
    Assert-ProofFails $invalidUtf8Path 'invalid UTF-8'

    Write-Output 'SHADOW_REPORT_PROOF_CONTRACT_TEST=PASS'
    Write-Output 'REPORT_PROOF_ALGORITHM=shadow-report-proof-v1'
    Write-Output "FIXTURE_PROOF_SHA256=$baseHash"
}
finally {
    if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
}
