[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:TestRoot = [IO.Path]::GetFullPath($PSScriptRoot)
$script:GateWRoot = [IO.Path]::GetFullPath((Join-Path $script:TestRoot '..'))
$script:RepoRoot = [IO.Path]::GetFullPath((Join-Path $script:GateWRoot '..\..'))
$script:ContractPath = Join-Path $script:GateWRoot 'gatew-soak-remediation-contract.psm1'
$script:FixturePath = Join-Path $script:TestRoot 'fixtures\attempt-09-rejected.json'
$script:ControlPath = Join-Path $script:GateWRoot 'gatew-okx-readonly-soak-control.ps1'
$script:WorkerPath = Join-Path $script:GateWRoot 'gatew-okx-readonly-soak.ps1'
$script:FailClosePath = Join-Path $script:GateWRoot 'gatew-okx-readonly-soak-failclose.ps1'
$script:BuilderPath = Join-Path $script:GateWRoot 'build-gatew-release-bundle.ps1'
$script:VerifierPath = Join-Path $script:GateWRoot 'verify-gatew-release.ps1'
$script:WorkerUnitPath = Join-Path $script:RepoRoot 'deploy\systemd\nq-gatew-soak@.service'
$script:FailCloseUnitPath = Join-Path $script:RepoRoot 'deploy\systemd\nq-gatew-soak-failclose@.service'
$script:Cases = [Collections.Generic.List[object]]::new()

Import-Module $script:ContractPath -Force

function ConvertFrom-TestJson
{
    param([Parameter(Mandatory = $true)][string]$Text)

    $command = Get-Command ConvertFrom-Json -ErrorAction Stop
    if ( $command.Parameters.ContainsKey('DateKind'))
    {
        return ($Text | ConvertFrom-Json -DateKind String)
    }
    return ($Text | ConvertFrom-Json)
}

function Copy-TestValue
{
    param([Parameter(Mandatory = $true)]$Value)

    return ConvertFrom-TestJson ($Value | ConvertTo-Json -Depth 30)
}

function Complete-Case
{
    param(
        [Parameter(Mandatory = $true)][int]$Number,
        [Parameter(Mandatory = $true)][string]$Name,
        [string]$Detail = 'PASS'
    )

    if ($Number -ne $script:Cases.Count + 1)
    {
        throw "REGRESSION_CASE_ORDER_INVALID expected=$( $script:Cases.Count + 1 ) actual=$Number"
    }
    $script:Cases.Add([pscustomobject][ordered]@{
        number = $Number
        name = $Name
        result = 'PASS'
        detail = $Detail
    })
}

function Assert-AcceptanceRejected
{
    param(
        [Parameter(Mandatory = $true)]$Snapshot,
        [Parameter(Mandatory = $true)][string]$ExpectedFailureCode,
        [Parameter(Mandatory = $true)][string]$CaseName
    )

    $result = Test-GateWAcceptanceSnapshot $Snapshot
    if ([bool]$result.accepted -or
            [string]$result.decision -cne 'FAIL / FORMAL_SOAK_ACCEPTANCE_REJECTED' -or
            @($result.failureCodes) -cnotcontains $ExpectedFailureCode)
    {
        throw "REGRESSION_ACCEPTANCE_EXPECTATION_FAILED case=$CaseName"
    }
    return $result
}

function New-GoodSnapshot
{
    return [pscustomobject][ordered]@{
        schemaVersion = 'gatew-soak-acceptance-snapshot-v1'
        runId = 'gatew-soak-20260722T111144Z-ac00f878'
        releaseCommit = '1111111111111111111111111111111111111111'
        unitActiveState = 'active'
        unitSubState = 'running'
        mainPid = 4074358L
        initialMainPid = 4074358L
        workerStartMainPid = 4074358L
        nRestarts = 0L
        execMainStartTimestampMonotonic = 731224020192L
        initialExecMainStartTimestampMonotonic = 731224020192L
        workerStartCount = 1
        earlyExitFactExists = $false
        completionMarkerValid = $true
        acceptanceStartAt = '2026-07-22T11:19:59.5201964Z'
        plannedAcceptanceAt = '2026-07-29T11:19:59.5201964Z'
        clockMainPid = 4074358L
        clockRecordSha256 = ('a' * 64)
        expectedClockRecordSha256 = ('a' * 64)
        observedDurationSeconds = 604800.0
        requiredDurationSeconds = 604800.0
        lastValidSampleAt = '2026-07-29T11:19:59.5201964Z'
        heartbeatObservedAt = '2026-07-29T11:20:04.5201964Z'
        evidenceDecision = 'PASS / FORMAL_EVIDENCE_VERIFIED'
        immutableReleaseDecision = 'PASS / IMMUTABLE_RELEASE_VERIFIED'
        sampleCount = 727L
        forbiddenEndpointCount = 0L
        fallbackCount = 0L
        rawResponseCount = 0L
        secretExposureCount = 0L
        allSamplesKillSwitchEngaged = $true
        evidenceManifestSha256 = ('b' * 64)
        evidenceFinalChainHash = ('c' * 64)
    }
}

function Assert-TextDoesNotMatch
{
    param(
        [Parameter(Mandatory = $true)][string]$Text,
        [Parameter(Mandatory = $true)][string[]]$Patterns,
        [Parameter(Mandatory = $true)][string]$Category
    )

    foreach ($pattern in $Patterns)
    {
        if ($Text -match $pattern)
        {
            throw "REGRESSION_FORBIDDEN_TEXT category=$Category pattern=$pattern"
        }
    }
}

try
{
    foreach ($required in @(
        $script:ContractPath, $script:FixturePath, $script:ControlPath, $script:WorkerPath,
        $script:FailClosePath, $script:BuilderPath, $script:VerifierPath,
        $script:WorkerUnitPath, $script:FailCloseUnitPath
    ))
    {
        if (-not (Test-Path -LiteralPath $required -PathType Leaf))
        {
            throw "REGRESSION_INPUT_MISSING path=$required"
        }
    }

    $good = New-GoodSnapshot
    $goodResult = Test-GateWAcceptanceSnapshot $good
    if (-not [bool]$goodResult.accepted -or
            [string]$goodResult.decision -cne 'PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED')
    {
        throw 'REGRESSION_GOOD_ACCEPTANCE_REJECTED'
    }
    Complete-Case 1 'complete-168h-acceptance'

    $fixture = ConvertFrom-TestJson (Get-Content -LiteralPath $script:FixturePath -Raw)
    $fixtureResult = Assert-AcceptanceRejected `
        $fixture.acceptanceSnapshot 'OBSERVED_DURATION_INSUFFICIENT' 'attempt-09'
    if ([string]$fixture.acceptanceSnapshot.evidenceDecision -cne
            [string]$fixture.expected.verifyEvidence -or
            [string]$fixture.expected.acceptanceFinalizer -cne
                    'BLOCKED / ACCEPTANCE_VERIFY_REQUIRED' -or
            [bool]$fixture.expected.attempt10Created)
    {
        throw 'REGRESSION_ATTEMPT_09_FIXTURE_INVALID'
    }
    Complete-Case 2 'attempt-09-rejected-fixture' `
        "duration=$( $fixture.acceptanceSnapshot.observedDurationSeconds )"

    $case = Copy-TestValue $good
    $case.unitActiveState = 'inactive'
    $case.unitSubState = 'dead'
    Assert-AcceptanceRejected $case 'UNIT_NOT_ACTIVE_RUNNING' 'unit-inactive' | Out-Null
    Complete-Case 3 'unit-inactive'

    $case = Copy-TestValue $good
    $case.mainPid = 0
    Assert-AcceptanceRejected $case 'MAIN_PID_ZERO' 'main-pid-zero' | Out-Null
    Complete-Case 4 'main-pid-zero'

    $case = Copy-TestValue $good
    $case.mainPid = 4074999
    Assert-AcceptanceRejected $case 'MAIN_PID_CHANGED' 'main-pid-changed' | Out-Null
    $case = Copy-TestValue $good
    $case.workerStartMainPid = 4074999
    Assert-AcceptanceRejected $case 'MAIN_PID_CHANGED' 'worker-start-main-pid-changed' | Out-Null
    Complete-Case 5 'main-pid-and-worker-start-binding-changed'

    $case = Copy-TestValue $good
    $case.nRestarts = 1
    Assert-AcceptanceRejected $case 'NRESTARTS_NONZERO' 'nrestarts-nonzero' | Out-Null
    Complete-Case 6 'nrestarts-nonzero'

    $case = Copy-TestValue $good
    $case.workerStartCount = 2
    $case.execMainStartTimestampMonotonic = 731224020299L
    Assert-AcceptanceRejected $case 'SECOND_EXEC_MAIN_START' 'second-process-start' | Out-Null
    Complete-Case 7 'second-process-start'

    $case = Copy-TestValue $good
    $case.earlyExitFactExists = $true
    Assert-AcceptanceRejected $case 'EARLY_EXIT_FACT_PRESENT' 'early-exit-fact' | Out-Null
    Complete-Case 8 'early-exit-fact'

    $case = Copy-TestValue $good
    $case.observedDurationSeconds = 604799.0
    Assert-AcceptanceRejected $case 'OBSERVED_DURATION_INSUFFICIENT' 'duration-minus-one' | Out-Null
    Complete-Case 9 'duration-minus-one-second'

    $case = Copy-TestValue $good
    $case.lastValidSampleAt = '2026-07-29T11:19:58.5201964Z'
    Assert-AcceptanceRejected $case 'LAST_VALID_SAMPLE_BEFORE_PLANNED' 'last-sample-early' | Out-Null
    Complete-Case 10 'last-valid-sample-before-planned'

    $case = Copy-TestValue $good
    $case.clockRecordSha256 = ('d' * 64)
    Assert-AcceptanceRejected $case 'ACCEPTANCE_CLOCK_DRIFT' 'clock-drift' | Out-Null
    Complete-Case 11 'acceptance-clock-drift'

    $case = Copy-TestValue $good
    $case.evidenceDecision = 'FAIL / HASH_CHAIN_INVALID'
    Assert-AcceptanceRejected $case 'EVIDENCE_INTEGRITY_NOT_VERIFIED' 'hash-chain-invalid' | Out-Null
    Complete-Case 12 'hash-chain-invalid'

    $case = Copy-TestValue $good
    $case.immutableReleaseDecision = 'FAIL / IMMUTABLE_RELEASE_DRIFT'
    Assert-AcceptanceRejected $case 'IMMUTABLE_RELEASE_NOT_VERIFIED' 'release-drift' | Out-Null
    Complete-Case 13 'immutable-release-drift'

    $case = Copy-TestValue $good
    $case.forbiddenEndpointCount = 1
    Assert-AcceptanceRejected $case 'FORBIDDEN_ENDPOINT_NONZERO' 'forbidden-endpoint' | Out-Null
    Complete-Case 14 'forbidden-endpoint-nonzero'

    $case = Copy-TestValue $good
    $case.fallbackCount = 1
    Assert-AcceptanceRejected $case 'FALLBACK_NONZERO' 'fallback' | Out-Null
    Complete-Case 15 'fallback-nonzero'

    $case = Copy-TestValue $good
    $case.rawResponseCount = 1
    Assert-AcceptanceRejected $case 'RAW_RESPONSE_NONZERO' 'raw-response' | Out-Null
    Complete-Case 16 'raw-response-nonzero'

    $case = Copy-TestValue $good
    $case.secretExposureCount = 1
    Assert-AcceptanceRejected $case 'SECRET_EXPOSURE_NONZERO' 'secret-exposure' | Out-Null
    Complete-Case 17 'secret-exposure-nonzero'

    $case = Copy-TestValue $good
    $case.allSamplesKillSwitchEngaged = $false
    Assert-AcceptanceRejected $case 'KILL_SWITCH_NOT_ENGAGED' 'kill-switch' | Out-Null
    Complete-Case 18 'kill-switch-not-engaged'

    $intent = New-GateWStopIntentRecord `
        -RunId ([string]$good.runId) `
        -RequestId 'gatew-stop-20260729T112005Z-0123abcd' `
        -RequestedAt '2026-07-29T11:20:05.0000000Z' `
        -RequestedByUid 1000 `
        -ReasonCode 'ACCEPTANCE_FINALIZATION' `
        -ReleaseCommit ([string]$good.releaseCommit)
    Assert-GateWStopIntentRecord $intent | Out-Null
    Complete-Case 19 'authorized-stop-intent-valid'

    $missingIntentRejected = $false
    try
    {
        Assert-GateWStopIntentRecord $null | Out-Null
    }
    catch
    {
        $missingIntentRejected = $true
    }
    if (-not $missingIntentRejected)
    {
        throw 'REGRESSION_MISSING_STOP_INTENT_ACCEPTED'
    }
    Complete-Case 20 'stop-intent-missing'

    $badIntent = Copy-TestValue $intent
    $badIntent.checksum = ('0' * 64)
    $checksumRejected = $false
    try
    {
        Assert-GateWStopIntentRecord $badIntent | Out-Null
    }
    catch
    {
        $checksumRejected = $_.Exception.Message -ceq 'FAIL / STOP_INTENT_CHECKSUM_INVALID'
    }
    if (-not $checksumRejected)
    {
        throw 'REGRESSION_BAD_STOP_INTENT_CHECKSUM_ACCEPTED'
    }
    $unknownReasonRejected = $false
    try
    {
        New-GateWStopIntentRecord `
            -RunId ([string]$good.runId) `
            -RequestId 'gatew-stop-20260729T112006Z-1234abcd' `
            -RequestedAt '2026-07-29T11:20:06.0000000Z' `
            -RequestedByUid 1000 `
            -ReasonCode 'UNKNOWN_REASON' `
            -ReleaseCommit ([string]$good.releaseCommit) | Out-Null
    }
    catch
    {
        $unknownReasonRejected = $_.Exception.Message -ceq
                'FAIL / STOP_INTENT_SCHEMA_INVALID'
    }
    if (-not $unknownReasonRejected)
    {
        throw 'REGRESSION_UNKNOWN_STOP_INTENT_REASON_ACCEPTED'
    }
    Complete-Case 21 'stop-intent-checksum-invalid'

    $failCloseStopwatch = [Diagnostics.Stopwatch]::StartNew()
    $failCloseOutput = @(& $script:FailClosePath -Action self-test)
    $failCloseStopwatch.Stop()
    $failCloseSelfTest = ConvertFrom-TestJson ($failCloseOutput -join "`n")
    if ([string]$failCloseSelfTest.decision -cne 'PASS / LIGHTWEIGHT_FAILCLOSE_SELF_TEST' -or
            -not [bool]$failCloseSelfTest.boundedUnder30Seconds -or
            $failCloseStopwatch.Elapsed.TotalSeconds -ge 30)
    {
        throw 'REGRESSION_FAILCLOSE_NOT_BOUNDED'
    }
    Complete-Case 22 'automatic-failclose-bounded' `
        "elapsedMs=$( $failCloseStopwatch.ElapsedMilliseconds )"

    $controlText = [IO.File]::ReadAllText($script:ControlPath)
    $workerText = [IO.File]::ReadAllText($script:WorkerPath)
    $failCloseText = [IO.File]::ReadAllText($script:FailClosePath)
    $builderText = [IO.File]::ReadAllText($script:BuilderPath)
    $verifierText = [IO.File]::ReadAllText($script:VerifierPath)
    $workerUnitText = [IO.File]::ReadAllText($script:WorkerUnitPath)
    $failCloseUnitText = [IO.File]::ReadAllText($script:FailCloseUnitPath)
    Assert-TextDoesNotMatch $failCloseText @(
        'Invoke-EvidenceVerify', 'evidence-verify', 'verify-gatew-release\.ps1',
        '(?i)\bjava\b', '(?i)\bjdbc\b'
    ) 'heavy-verifier'
    if ($failCloseUnitText -match 'ExecStartPre=')
    {
        throw 'REGRESSION_FAILCLOSE_UNIT_HEAVY_PRESTART'
    }
    Complete-Case 23 'automatic-failclose-no-heavy-verifier'

    $finalizerStart = $controlText.IndexOf(
            'function Finalize-FormalAcceptance',
            [StringComparison]::Ordinal
    )
    if ($finalizerStart -lt 0)
    {
        throw 'REGRESSION_ACCEPTANCE_FINALIZER_MISSING'
    }
    $finalizerText = $controlText.Substring($finalizerStart)
    $proofGuard = $finalizerText.IndexOf(
            "throw 'BLOCKED / ACCEPTANCE_VERIFY_REQUIRED'",
            [StringComparison]::Ordinal
    )
    $stopInvocation = $finalizerText.IndexOf(
            "Invoke-Native `$script:SystemctlPath @('stop'",
            [StringComparison]::Ordinal
    )
    if ($proofGuard -lt 0 -or $stopInvocation -lt 0 -or $proofGuard -gt $stopInvocation)
    {
        throw 'REGRESSION_ACCEPTANCE_FINALIZER_PRECONDITION_MISSING'
    }
    Complete-Case 24 'acceptance-finalizer-before-verify-blocked'

    $terminalOne = New-GateWTerminalRecord `
        -RunId ([string]$good.runId) `
        -ReleaseCommit ([string]$good.releaseCommit) `
        -AcceptanceResult 'REJECTED_RUNTIME_EXIT' `
        -TerminalReasonCode 'WORKER_EXIT_WITHOUT_EXPLICIT_ACCEPTANCE' `
        -StopClassification 'NOT_PROVEN' `
        -AcceptanceStartAt $null `
        -PlannedAcceptanceAt $null `
        -AcceptanceVerificationChecksum 'NOT_APPLICABLE' `
        -EvidenceManifestSha256 'NOT_VERIFIED' `
        -EvidenceFinalChainHash 'NOT_VERIFIED' `
        -StopIntentChecksum 'NOT_PRESENT' `
        -FinalizerKind 'AUTOMATIC_FAIL_CLOSE' `
        -FinalizedAt '2026-07-29T11:20:05.0000000Z'
    $temporary = Join-Path ([IO.Path]::GetTempPath()) (
    'nq-gatew-terminal-cas-' + [Guid]::NewGuid().ToString('N')
    )
    [IO.Directory]::CreateDirectory($temporary) | Out-Null
    try
    {
        $terminalPath = Join-Path $temporary 'terminal-status.json'
        $bytes = [Text.UTF8Encoding]::new($false).GetBytes(
                ($terminalOne | ConvertTo-Json -Depth 20)
        )
        try
        {
            $stream = [IO.FileStream]::new(
                    $terminalPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write,
                    [IO.FileShare]::None
            )
            try
            {
                $stream.Write($bytes, 0, $bytes.Length)
                $stream.Flush($true)
            }
            finally
            {
                $stream.Dispose()
            }
        }
        finally
        {
            [Array]::Clear($bytes, 0, $bytes.Length)
        }
        $secondCreateRejected = $false
        try
        {
            $second = [IO.FileStream]::new(
                    $terminalPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write,
                    [IO.FileShare]::None
            )
            $second.Dispose()
        }
        catch
        {
            $secondCreateRejected = $true
        }
        if (-not $secondCreateRejected)
        {
            throw 'REGRESSION_TERMINAL_SECOND_CREATE_ACCEPTED'
        }
    }
    finally
    {
        $resolvedTemporary = [IO.Path]::GetFullPath($temporary)
        if (Test-Path -LiteralPath $resolvedTemporary)
        {
            Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
        }
    }
    Complete-Case 25 'terminal-write-once'

    Assert-GateWTerminalRecord (Copy-TestValue $terminalOne) | Out-Null
    if (-not $controlText.Contains('NO_CHANGE / ACCEPTANCE_RESULT_ALREADY_FINALIZED'))
    {
        throw 'REGRESSION_TERMINAL_IDEMPOTENCY_CONTRACT_MISSING'
    }
    Complete-Case 26 'same-finalization-idempotent'

    $terminalConflict = Copy-TestValue $terminalOne
    $terminalConflict.acceptanceResult = 'REJECTED_FINALIZER_ERROR'
    if (($terminalConflict | ConvertTo-Json -Compress) -ceq
            ($terminalOne | ConvertTo-Json -Compress) -or
            -not $controlText.Contains('BLOCKED / TERMINAL_RESULT_CONFLICT'))
    {
        throw 'REGRESSION_TERMINAL_CONFLICT_NOT_REJECTED'
    }
    foreach ($invalidTerminalParameters in @(
        @{
            ReleaseCommit = 'UNKNOWN'
            StopClassification = 'NOT_PROVEN'
            StopIntentChecksum = 'NOT_PRESENT'
        },
        @{
            ReleaseCommit = ([string]$good.releaseCommit)
            StopClassification = 'UNAUTHORIZED_OR_UNKNOWN_STOP'
            StopIntentChecksum = ('d' * 64)
        }
    ))
    {
        $invalidAuthorityRejected = $false
        try
        {
            New-GateWTerminalRecord `
                -RunId ([string]$good.runId) `
                -ReleaseCommit ([string]$invalidTerminalParameters.ReleaseCommit) `
                -AcceptanceResult 'REJECTED_RUNTIME_EXIT' `
                -TerminalReasonCode 'WORKER_EXIT_WITHOUT_EXPLICIT_ACCEPTANCE' `
                -StopClassification ([string]$invalidTerminalParameters.StopClassification) `
                -AcceptanceStartAt $null `
                -PlannedAcceptanceAt $null `
                -AcceptanceVerificationChecksum 'NOT_APPLICABLE' `
                -EvidenceManifestSha256 'NOT_VERIFIED' `
                -EvidenceFinalChainHash 'NOT_VERIFIED' `
                -StopIntentChecksum ([string]$invalidTerminalParameters.StopIntentChecksum) `
                -FinalizerKind 'AUTOMATIC_FAIL_CLOSE' `
                -FinalizedAt '2026-07-29T11:20:05.0000000Z' | Out-Null
        }
        catch
        {
            $invalidAuthorityRejected = $_.Exception.Message -ceq
                    'FAIL / TERMINAL_AUTHORITY_BINDING_INVALID'
        }
        if (-not $invalidAuthorityRejected)
        {
            throw 'REGRESSION_TERMINAL_AUTHORITY_BINDING_NOT_REJECTED'
        }
    }
    Complete-Case 27 'conflicting-finalization-rejected'

    if (-not $controlText.Contains('PASS / FORMAL_EVIDENCE_VERIFIED') -or
            $controlText -match 'FORMAL_SOAK_VERIFIED|SOAK_ACCEPTED' -or
            -not $controlText.Contains("'verify-evidence'") -or
            -not $controlText.Contains("'verify-acceptance'") -or
            -not $controlText.Contains("'verify-terminal'"))
    {
        throw 'REGRESSION_EVIDENCE_ACCEPTANCE_SEMANTICS_AMBIGUOUS'
    }
    Complete-Case 28 'evidence-only-has-no-acceptance-semantics'

    Assert-TextDoesNotMatch $failCloseText @(
        '(?i)Invoke-WebRequest', '(?i)Invoke-RestMethod', '(?i)HttpClient',
        '(?i)WebClient', '(?i)TcpClient', '(?i)/api/'
    ) 'network'
    if (-not $failCloseUnitText.Contains('PrivateNetwork=true') -or
            -not $failCloseUnitText.Contains('RestrictAddressFamilies=AF_UNIX') -or
            -not $failCloseUnitText.Contains('IPAddressDeny=any'))
    {
        throw 'REGRESSION_FAILCLOSE_NETWORK_SANDBOX_INVALID'
    }
    Complete-Case 29 'automatic-failclose-no-network-call'

    Assert-TextDoesNotMatch $failCloseText @(
        'CREDENTIALS_DIRECTORY', 'LoadCredential', 'DB_PASSWORD',
        'credential-master-key', 'db-password\.cred'
    ) 'credential'
    if ($failCloseUnitText -match
            'LoadCredentialEncrypted|NQ_GATEW_SECRET_SOURCE' -or
            -not $failCloseUnitText.Contains(
                    'EnvironmentFile=/var/lib/nexus-quant/gatew-soak/%i/control/failclose.env'
            ))
    {
        throw 'REGRESSION_FAILCLOSE_CREDENTIAL_CONTRACT_INVALID'
    }
    $failCloseEnvironment = [regex]::Match(
            $controlText,
            '(?s)\$failCloseValues\s*=\s*@\{\s*' +
            'NQ_GATEW_RELEASE_ROOT\s*=\s*\[string\]\$release\.releaseRoot\s*' +
            'NQ_GATEW_RELEASE_ID\s*=\s*\[string\]\$release\.releaseId\s*' +
            'NQ_GATEW_RELEASE_MANIFEST_SHA256\s*=\s*\[string\]\$release\.manifestSha256\s*\}'
    )
    if (-not $failCloseEnvironment.Success -or
            $failCloseEnvironment.Value -match
                    '(?i)DB_|DATABASE|SECRET|CREDENTIAL|OKX|RUN_MODE')
    {
        throw 'REGRESSION_FAILCLOSE_RELEASE_ENV_NOT_SANITIZED'
    }
    Complete-Case 30 'automatic-failclose-no-credential-read'

    Assert-TextDoesNotMatch $failCloseText @(
        '/api/v5/', 'Invoke-SanitizedCycle', 'OkxPrivate', 'gatew-okx-readonly'
    ) 'okx'
    Complete-Case 31 'automatic-failclose-no-okx-call'

    $combinedRuntimeText = @(
        $controlText, $workerText, $failCloseText, $workerUnitText, $failCloseUnitText
    ) -join "`n"
    $acceptanceSnapshotStart = $controlText.IndexOf(
            'function New-FormalAcceptanceSnapshot',
            [StringComparison]::Ordinal
    )
    $acceptanceVerifyIndex = $controlText.IndexOf(
            '$evidence = Verify-FormalEvidence',
            $acceptanceSnapshotStart,
            [StringComparison]::Ordinal
    )
    $acceptanceUnitStateIndex = $controlText.IndexOf(
            '$state = Get-UnitState (Get-WorkerUnitName $RunId)',
            $acceptanceVerifyIndex,
            [StringComparison]::Ordinal
    )
    if ([bool]$failCloseSelfTest.attempt10Created -or [bool]$terminalOne.attempt10Created -or
            $combinedRuntimeText -match
                    '(?i)(?:create|new|prepare|start)[^\r\n]{0,48}ATTEMPT-10' -or
            -not $workerUnitText.Contains('Restart=no') -or
            -not $failCloseUnitText.Contains('Restart=no') -or
            $combinedRuntimeText -match '(?i)\.timer|OnCalendar=' -or
            $failCloseUnitText -match 'IPAddressAllow=|CAP_DAC_READ_SEARCH|AF_INET' -or
            -not $failCloseUnitText.Contains('TimeoutStartSec=30s') -or
            -not $workerText.Contains('Invoke-FormalFinalAcceptanceSample') -or
            -not $workerText.Contains("'ACCEPTANCE_READY'") -or
            $workerText.Contains('completion-marker.json') -or
            -not $workerText.Contains('Confirm-FormalCompletionBoundary') -or
            $acceptanceSnapshotStart -lt 0 -or $acceptanceVerifyIndex -lt 0 -or
            $acceptanceUnitStateIndex -lt $acceptanceVerifyIndex -or
            -not $controlText.Contains('workerStartMainPid') -or
            -not $controlText.Contains(
                    '"$( Get-ControlRoot $Value )/completion-marker.json"'
            ) -or
            -not $controlText.Contains(
                    "Assert-PosixContract `$path 'regular file' '600' 'root' 'root'"
            ) -or
            -not $controlText.Contains('Commit-AcceptanceCompletionMarker') -or
            -not $controlText.Contains('stop-intent-retired-') -or
            $controlText -match '\$terminal\.terminalStatus' -or
            -not $controlText.Contains('Complete-AcceptedLifecycle') -or
            -not $controlText.Contains('$identity = Assert-FrozenReleaseBinding $config') -or
            -not $controlText.Contains('Assert-FormalWorkerState $state $RunId -AllowInactive') -or
            -not $controlText.Contains("'terminal-status*.json'") -or
            -not $builderText.Contains("'contract-library'") -or
            -not $verifierText.Contains("'contract-library'"))
    {
        throw 'REGRESSION_ATTEMPT10_OR_SYSTEMD_BOUNDARY_INVALID'
    }
    Complete-Case 32 'attempt-10-not-created-and-runtime-boundaries-preserved'

    if (-not $controlText.Contains('$script:FormalRealCadenceSeconds = 900') -or
            -not $controlText.Contains('$script:FormalOfflineCadenceSeconds = 60') -or
            -not $controlText.Contains('cadenceSeconds = Get-FormalCadenceSeconds $RunMode') -or
            -not $controlText.Contains("(Get-FormalCadenceSeconds 'REAL_READONLY_SOAK') -ne 900"))
    {
        throw 'REGRESSION_FORMAL_CADENCE_CONTRACT_INVALID'
    }
    Complete-Case 33 'formal-real-cadence-fixed-900-offline-60'

    if ($script:Cases.Count -ne 33)
    {
        throw "REGRESSION_CASE_COUNT_INVALID actual=$( $script:Cases.Count )"
    }
    [pscustomobject][ordered]@{
        decision = 'PASS / GATEW_SOAK_REMEDIATION_REGRESSION'
        cases = $script:Cases.Count
        attempt09Evidence = [string]$fixture.expected.verifyEvidence
        attempt09Acceptance = [string]$fixtureResult.decision
        attempt09Finalizer = [string]$fixture.expected.acceptanceFinalizer
        automaticFailCloseElapsedMilliseconds = [long]$failCloseStopwatch.ElapsedMilliseconds
        ambiguousFormalSoakVerifiedRemoved = $true
        workerAcceptanceReadyContract = 'PASS'
        systemdContract = 'PASS / RESTART_NO / NO_DROPIN_OR_TIMER / FAILCLOSE_NO_NETWORK_OR_CREDENTIAL'
        immutableBundleContract = 'PASS / CONTRACT_LIBRARY_DECLARED'
        attempt10Created = $false
        results = @($script:Cases)
    } | ConvertTo-Json -Depth 8
}
catch
{
    [pscustomobject][ordered]@{
        decision = 'FAIL / GATEW_SOAK_REMEDIATION_REGRESSION'
        casesPassed = $script:Cases.Count
        detail = $_.Exception.Message
    } | ConvertTo-Json -Depth 4
    exit 2
}
