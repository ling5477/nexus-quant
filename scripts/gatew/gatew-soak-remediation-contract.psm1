Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:RunIdPattern = '^gatew-soak-[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$'
$script:CommitPattern = '^[a-f0-9]{40}$'
$script:Sha256Pattern = '^[a-f0-9]{64}$'
$script:SafeCodePattern = '^[A-Z][A-Z0-9_]{1,95}$'

function ConvertTo-GateWCompactJson
{
    param([Parameter(Mandatory = $true)]$Value)
    return ($Value | ConvertTo-Json -Depth 20 -Compress)
}

function Get-GateWSha256Text
{
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text)

    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    $sha = [Security.Cryptography.SHA256]::Create()
    try
    {
        return ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-', '').ToLowerInvariant()
    }
    finally
    {
        $sha.Dispose()
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Assert-GateWExactFields
{
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string[]]$Expected,
        [Parameter(Mandatory = $true)][string]$FailureCode
    )

    if ((@($Value.PSObject.Properties.Name) -join '|') -cne ($Expected -join '|'))
    {
        throw "FAIL / $FailureCode"
    }
}

function Get-GateWRecordChecksum
{
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string[]]$Fields
    )

    $canonical = [ordered]@{ }
    foreach ($field in $Fields)
    {
        $property = $Value.PSObject.Properties[$field]
        if ($null -eq $property)
        {
            throw 'FAIL / CHECKSUM_INPUT_INVALID'
        }
        $canonical[$field] = $property.Value
    }
    return Get-GateWSha256Text (ConvertTo-GateWCompactJson $canonical)
}

function ConvertTo-GateWUtcTimestamp
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$FailureCode
    )

    $parsed = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse($Value, [ref]$parsed) -or
            $parsed.Offset -ne [TimeSpan]::Zero)
    {
        throw "FAIL / $FailureCode"
    }
    return $parsed
}

function Test-GateWIntegralNumber
{
    param([AllowNull()]$Value)

    if ($null -eq $Value -or $Value -is [string] -or $Value -is [bool])
    {
        return $false
    }
    $number = 0.0
    return [double]::TryParse(
            [string]$Value,
            [Globalization.NumberStyles]::Integer,
            [Globalization.CultureInfo]::InvariantCulture,
            [ref]$number
    )
}

function New-GateWStopIntentRecord
{
    param(
        [Parameter(Mandatory = $true)][string]$RunId,
        [Parameter(Mandatory = $true)][string]$RequestId,
        [Parameter(Mandatory = $true)][string]$RequestedAt,
        [Parameter(Mandatory = $true)][long]$RequestedByUid,
        [Parameter(Mandatory = $true)][string]$ReasonCode,
        [Parameter(Mandatory = $true)][string]$ReleaseCommit
    )

    $record = [ordered]@{
        schemaVersion = 'gatew-soak-stop-intent-v1'
        runId = $RunId
        requestId = $RequestId
        requestedAt = $RequestedAt
        requestedByUid = $RequestedByUid
        reasonCode = $ReasonCode
        releaseCommit = $ReleaseCommit
    }
    $record.checksum = Get-GateWRecordChecksum ([pscustomobject]$record) @(
        'schemaVersion', 'runId', 'requestId', 'requestedAt', 'requestedByUid',
        'reasonCode', 'releaseCommit'
    )
    $result = [pscustomobject]$record
    Assert-GateWStopIntentRecord $result | Out-Null
    return $result
}

function Assert-GateWStopIntentRecord
{
    param([Parameter(Mandatory = $true)]$Record)

    $fields = @(
        'schemaVersion', 'runId', 'requestId', 'requestedAt', 'requestedByUid',
        'reasonCode', 'releaseCommit', 'checksum'
    )
    Assert-GateWExactFields $Record $fields 'STOP_INTENT_SCHEMA_INVALID'
    $null = ConvertTo-GateWUtcTimestamp ([string]$Record.requestedAt) 'STOP_INTENT_SCHEMA_INVALID'
    if ([string]$Record.schemaVersion -cne 'gatew-soak-stop-intent-v1' -or
            [string]$Record.runId -cnotmatch $script:RunIdPattern -or
            [string]$Record.requestId -cnotmatch '^gatew-stop-[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$' -or
            -not (Test-GateWIntegralNumber $Record.requestedByUid) -or
            [long]$Record.requestedByUid -lt 0 -or
            [string]$Record.reasonCode -cnotin @(
                'OPERATOR_STOP_REQUESTED', 'ACCEPTANCE_FINALIZATION'
            ) -or
            [string]$Record.releaseCommit -cnotmatch $script:CommitPattern -or
            [string]$Record.checksum -cnotmatch $script:Sha256Pattern)
    {
        throw 'FAIL / STOP_INTENT_SCHEMA_INVALID'
    }
    $expected = Get-GateWRecordChecksum $Record @(
        'schemaVersion', 'runId', 'requestId', 'requestedAt', 'requestedByUid',
        'reasonCode', 'releaseCommit'
    )
    if ([string]$Record.checksum -cne $expected)
    {
        throw 'FAIL / STOP_INTENT_CHECKSUM_INVALID'
    }
    return $Record
}

function New-GateWCompletionMarkerRecord
{
    param([Parameter(Mandatory = $true)]$Snapshot)

    $record = [ordered]@{
        schemaVersion = 'gatew-soak-completion-marker-v2'
        runId = [string]$Snapshot.runId
        releaseCommit = [string]$Snapshot.releaseCommit
        mainPid = [long]$Snapshot.mainPid
        lastValidSampleAt = [string]$Snapshot.lastValidSampleAt
        evidenceManifestSha256 = [string]$Snapshot.evidenceManifestSha256
        evidenceFinalChainHash = [string]$Snapshot.evidenceFinalChainHash
        completedAt = [string]$Snapshot.lastValidSampleAt
    }
    $record.checksum = Get-GateWRecordChecksum ([pscustomobject]$record) @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid', 'lastValidSampleAt',
        'evidenceManifestSha256', 'evidenceFinalChainHash', 'completedAt'
    )
    $value = [pscustomobject]$record
    Assert-GateWCompletionMarkerRecord $value $Snapshot | Out-Null
    return $value
}

function Assert-GateWCompletionMarkerRecord
{
    param(
        [Parameter(Mandatory = $true)]$Marker,
        [Parameter(Mandatory = $true)]$Snapshot
    )

    $fields = @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid', 'lastValidSampleAt',
        'evidenceManifestSha256', 'evidenceFinalChainHash', 'completedAt', 'checksum'
    )
    Assert-GateWExactFields $Marker $fields 'COMPLETION_MARKER_SCHEMA_INVALID'
    $lastSample = ConvertTo-GateWUtcTimestamp `
        ([string]$Marker.lastValidSampleAt) 'COMPLETION_MARKER_SCHEMA_INVALID'
    $completedAt = ConvertTo-GateWUtcTimestamp `
        ([string]$Marker.completedAt) 'COMPLETION_MARKER_SCHEMA_INVALID'
    if ([string]$Marker.schemaVersion -cne 'gatew-soak-completion-marker-v2' -or
            [string]$Marker.runId -cne [string]$Snapshot.runId -or
            [string]$Marker.releaseCommit -cne [string]$Snapshot.releaseCommit -or
            [long]$Marker.mainPid -le 0 -or
            [long]$Marker.mainPid -ne [long]$Snapshot.mainPid -or
            [string]$Marker.lastValidSampleAt -cne [string]$Snapshot.lastValidSampleAt -or
            [string]$Marker.evidenceManifestSha256 -cne
                    [string]$Snapshot.evidenceManifestSha256 -or
            [string]$Marker.evidenceFinalChainHash -cne
                    [string]$Snapshot.evidenceFinalChainHash -or
            $completedAt -ne $lastSample -or
            [string]$Marker.checksum -cnotmatch $script:Sha256Pattern)
    {
        throw 'FAIL / COMPLETION_MARKER_SCHEMA_INVALID'
    }
    $expected = Get-GateWRecordChecksum $Marker @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid', 'lastValidSampleAt',
        'evidenceManifestSha256', 'evidenceFinalChainHash', 'completedAt'
    )
    if ([string]$Marker.checksum -cne $expected)
    {
        throw 'FAIL / COMPLETION_MARKER_CHECKSUM_INVALID'
    }
    return $Marker
}

function Test-GateWAcceptanceSnapshot
{
    param([Parameter(Mandatory = $true)]$Snapshot)

    $expectedFields = @(
        'schemaVersion', 'runId', 'releaseCommit', 'unitActiveState', 'unitSubState',
        'mainPid', 'initialMainPid', 'workerStartMainPid', 'nRestarts',
        'execMainStartTimestampMonotonic',
        'initialExecMainStartTimestampMonotonic', 'workerStartCount', 'earlyExitFactExists',
        'completionMarkerValid', 'acceptanceStartAt', 'plannedAcceptanceAt', 'clockMainPid',
        'clockRecordSha256', 'expectedClockRecordSha256', 'observedDurationSeconds',
        'requiredDurationSeconds', 'lastValidSampleAt', 'heartbeatObservedAt',
        'evidenceDecision', 'immutableReleaseDecision', 'sampleCount',
        'forbiddenEndpointCount', 'fallbackCount', 'rawResponseCount',
        'secretExposureCount', 'allSamplesKillSwitchEngaged',
        'evidenceManifestSha256', 'evidenceFinalChainHash'
    )
    try
    {
        Assert-GateWExactFields $Snapshot $expectedFields 'ACCEPTANCE_SNAPSHOT_SCHEMA_INVALID'
    }
    catch
    {
        return [pscustomobject][ordered]@{
            decision = 'FAIL / FORMAL_SOAK_ACCEPTANCE_REJECTED'
            accepted = $false
            failureCodes = @('ACCEPTANCE_SNAPSHOT_SCHEMA_INVALID')
            observedDurationSeconds = 0.0
            requiredDurationSeconds = 604800.0
        }
    }

    $failures = [Collections.Generic.List[string]]::new()
    if ([string]$Snapshot.schemaVersion -cne 'gatew-soak-acceptance-snapshot-v1' -or
            [string]$Snapshot.runId -cnotmatch $script:RunIdPattern -or
            [string]$Snapshot.releaseCommit -cnotmatch $script:CommitPattern -or
            -not (Test-GateWIntegralNumber $Snapshot.mainPid) -or
            -not (Test-GateWIntegralNumber $Snapshot.initialMainPid) -or
            -not (Test-GateWIntegralNumber $Snapshot.workerStartMainPid) -or
            -not (Test-GateWIntegralNumber $Snapshot.nRestarts) -or
            -not (Test-GateWIntegralNumber $Snapshot.execMainStartTimestampMonotonic) -or
            -not (Test-GateWIntegralNumber $Snapshot.initialExecMainStartTimestampMonotonic) -or
            -not (Test-GateWIntegralNumber $Snapshot.workerStartCount) -or
            -not (Test-GateWIntegralNumber $Snapshot.clockMainPid) -or
            -not (Test-GateWIntegralNumber $Snapshot.sampleCount) -or
            -not (Test-GateWIntegralNumber $Snapshot.forbiddenEndpointCount) -or
            -not (Test-GateWIntegralNumber $Snapshot.fallbackCount) -or
            -not (Test-GateWIntegralNumber $Snapshot.rawResponseCount) -or
            -not (Test-GateWIntegralNumber $Snapshot.secretExposureCount) -or
            $Snapshot.earlyExitFactExists -isnot [bool] -or
            $Snapshot.completionMarkerValid -isnot [bool] -or
            $Snapshot.allSamplesKillSwitchEngaged -isnot [bool])
    {
        $failures.Add('ACCEPTANCE_SNAPSHOT_SCHEMA_INVALID')
    }
    if ([string]$Snapshot.unitActiveState -cne 'active' -or
            [string]$Snapshot.unitSubState -cne 'running')
    {
        $failures.Add('UNIT_NOT_ACTIVE_RUNNING')
    }
    if ([long]$Snapshot.mainPid -le 0)
    {
        $failures.Add('MAIN_PID_ZERO')
    }
    if ([long]$Snapshot.mainPid -ne [long]$Snapshot.initialMainPid -or
            [long]$Snapshot.mainPid -ne [long]$Snapshot.workerStartMainPid)
    {
        $failures.Add('MAIN_PID_CHANGED')
    }
    if ([long]$Snapshot.nRestarts -ne 0)
    {
        $failures.Add('NRESTARTS_NONZERO')
    }
    if ([long]$Snapshot.execMainStartTimestampMonotonic -le 0 -or
            [long]$Snapshot.execMainStartTimestampMonotonic -ne
                    [long]$Snapshot.initialExecMainStartTimestampMonotonic)
    {
        $failures.Add('SECOND_EXEC_MAIN_START')
    }
    if ([int]$Snapshot.workerStartCount -ne 1)
    {
        $failures.Add('WORKER_START_COUNT_INVALID')
    }
    if ([bool]$Snapshot.earlyExitFactExists)
    {
        $failures.Add('EARLY_EXIT_FACT_PRESENT')
    }
    if (-not [bool]$Snapshot.completionMarkerValid)
    {
        $failures.Add('COMPLETION_MARKER_INVALID')
    }

    $acceptanceStart = [DateTimeOffset]::MinValue
    $plannedAcceptance = [DateTimeOffset]::MinValue
    $lastValidSample = [DateTimeOffset]::MinValue
    $heartbeat = [DateTimeOffset]::MinValue
    $timeValid = [DateTimeOffset]::TryParse(
            [string]$Snapshot.acceptanceStartAt, [ref]$acceptanceStart
    ) -and [DateTimeOffset]::TryParse(
            [string]$Snapshot.plannedAcceptanceAt, [ref]$plannedAcceptance
    ) -and [DateTimeOffset]::TryParse(
            [string]$Snapshot.lastValidSampleAt, [ref]$lastValidSample
    ) -and [DateTimeOffset]::TryParse(
            [string]$Snapshot.heartbeatObservedAt, [ref]$heartbeat
    ) -and $acceptanceStart.Offset -eq [TimeSpan]::Zero -and
            $plannedAcceptance.Offset -eq [TimeSpan]::Zero -and
            $lastValidSample.Offset -eq [TimeSpan]::Zero -and
            $heartbeat.Offset -eq [TimeSpan]::Zero
    $observedDuration = [double]$Snapshot.observedDurationSeconds
    $requiredDuration = [double]$Snapshot.requiredDurationSeconds
    if ([double]::IsNaN($observedDuration) -or [double]::IsInfinity($observedDuration) -or
            [double]::IsNaN($requiredDuration) -or [double]::IsInfinity($requiredDuration))
    {
        $timeValid = $false
    }
    if (-not $timeValid -or
            $requiredDuration -ne 604800.0 -or
            [Math]::Abs(
                    ($plannedAcceptance - $acceptanceStart).TotalSeconds -
                            $requiredDuration
            ) -gt 0.000001 -or
            [long]$Snapshot.clockMainPid -ne [long]$Snapshot.initialMainPid -or
            [string]$Snapshot.clockRecordSha256 -cnotmatch $script:Sha256Pattern -or
            [string]$Snapshot.clockRecordSha256 -cne [string]$Snapshot.expectedClockRecordSha256)
    {
        $failures.Add('ACCEPTANCE_CLOCK_DRIFT')
    }
    if ($observedDuration -lt $requiredDuration)
    {
        $failures.Add('OBSERVED_DURATION_INSUFFICIENT')
    }
    if (-not $timeValid -or $lastValidSample -lt $plannedAcceptance)
    {
        $failures.Add('LAST_VALID_SAMPLE_BEFORE_PLANNED')
    }
    if (-not $timeValid -or $heartbeat -lt $plannedAcceptance)
    {
        $failures.Add('HEARTBEAT_COVERAGE_INSUFFICIENT')
    }
    if ([string]$Snapshot.evidenceDecision -cne 'PASS / FORMAL_EVIDENCE_VERIFIED')
    {
        $failures.Add('EVIDENCE_INTEGRITY_NOT_VERIFIED')
    }
    if ([string]$Snapshot.immutableReleaseDecision -cne 'PASS / IMMUTABLE_RELEASE_VERIFIED')
    {
        $failures.Add('IMMUTABLE_RELEASE_NOT_VERIFIED')
    }
    if ([long]$Snapshot.sampleCount -le 0)
    {
        $failures.Add('SAMPLE_COUNT_INVALID')
    }
    if ([long]$Snapshot.forbiddenEndpointCount -ne 0)
    {
        $failures.Add('FORBIDDEN_ENDPOINT_NONZERO')
    }
    if ([long]$Snapshot.fallbackCount -ne 0)
    {
        $failures.Add('FALLBACK_NONZERO')
    }
    if ([long]$Snapshot.rawResponseCount -ne 0)
    {
        $failures.Add('RAW_RESPONSE_NONZERO')
    }
    if ([long]$Snapshot.secretExposureCount -ne 0)
    {
        $failures.Add('SECRET_EXPOSURE_NONZERO')
    }
    if (-not [bool]$Snapshot.allSamplesKillSwitchEngaged)
    {
        $failures.Add('KILL_SWITCH_NOT_ENGAGED')
    }
    if ([string]$Snapshot.evidenceManifestSha256 -cnotmatch $script:Sha256Pattern -or
            [string]$Snapshot.evidenceFinalChainHash -cnotmatch $script:Sha256Pattern)
    {
        $failures.Add('EVIDENCE_BINDING_INVALID')
    }

    if ($failures.Count -gt 0)
    {
        return [pscustomobject][ordered]@{
            decision = 'FAIL / FORMAL_SOAK_ACCEPTANCE_REJECTED'
            accepted = $false
            failureCodes = @($failures)
            observedDurationSeconds = [double]$Snapshot.observedDurationSeconds
            requiredDurationSeconds = [double]$Snapshot.requiredDurationSeconds
        }
    }
    return [pscustomobject][ordered]@{
        decision = 'PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED'
        accepted = $true
        failureCodes = @()
        observedDurationSeconds = [double]$Snapshot.observedDurationSeconds
        requiredDurationSeconds = [double]$Snapshot.requiredDurationSeconds
    }
}

function New-GateWAcceptanceProof
{
    param(
        [Parameter(Mandatory = $true)]$Snapshot,
        [Parameter(Mandatory = $true)][string]$VerifiedAt
    )

    $result = Test-GateWAcceptanceSnapshot $Snapshot
    if (-not [bool]$result.accepted)
    {
        throw 'FAIL / FORMAL_SOAK_ACCEPTANCE_REJECTED'
    }
    $null = ConvertTo-GateWUtcTimestamp $VerifiedAt 'ACCEPTANCE_PROOF_SCHEMA_INVALID'
    $proof = [ordered]@{
        schemaVersion = 'gatew-soak-acceptance-proof-v1'
        runId = [string]$Snapshot.runId
        releaseCommit = [string]$Snapshot.releaseCommit
        mainPid = [long]$Snapshot.mainPid
        nRestarts = [long]$Snapshot.nRestarts
        execMainStartTimestampMonotonic = [long]$Snapshot.execMainStartTimestampMonotonic
        acceptanceStartAt = [string]$Snapshot.acceptanceStartAt
        plannedAcceptanceAt = [string]$Snapshot.plannedAcceptanceAt
        observedDurationSeconds = [double]$Snapshot.observedDurationSeconds
        lastValidSampleAt = [string]$Snapshot.lastValidSampleAt
        heartbeatObservedAt = [string]$Snapshot.heartbeatObservedAt
        evidenceManifestSha256 = [string]$Snapshot.evidenceManifestSha256
        evidenceFinalChainHash = [string]$Snapshot.evidenceFinalChainHash
        verifiedAt = $VerifiedAt
        result = 'PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED'
    }
    $proof.checksum = Get-GateWRecordChecksum ([pscustomobject]$proof) @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid', 'nRestarts',
        'execMainStartTimestampMonotonic', 'acceptanceStartAt', 'plannedAcceptanceAt',
        'observedDurationSeconds', 'lastValidSampleAt', 'heartbeatObservedAt',
        'evidenceManifestSha256', 'evidenceFinalChainHash', 'verifiedAt', 'result'
    )
    $value = [pscustomobject]$proof
    Assert-GateWAcceptanceProof $value | Out-Null
    return $value
}

function Assert-GateWAcceptanceProof
{
    param([Parameter(Mandatory = $true)]$Proof)

    $fields = @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid', 'nRestarts',
        'execMainStartTimestampMonotonic', 'acceptanceStartAt', 'plannedAcceptanceAt',
        'observedDurationSeconds', 'lastValidSampleAt', 'heartbeatObservedAt',
        'evidenceManifestSha256', 'evidenceFinalChainHash', 'verifiedAt', 'result', 'checksum'
    )
    Assert-GateWExactFields $Proof $fields 'ACCEPTANCE_PROOF_SCHEMA_INVALID'
    $start = ConvertTo-GateWUtcTimestamp ([string]$Proof.acceptanceStartAt) 'ACCEPTANCE_PROOF_SCHEMA_INVALID'
    $planned = ConvertTo-GateWUtcTimestamp ([string]$Proof.plannedAcceptanceAt) 'ACCEPTANCE_PROOF_SCHEMA_INVALID'
    $lastSample = ConvertTo-GateWUtcTimestamp ([string]$Proof.lastValidSampleAt) 'ACCEPTANCE_PROOF_SCHEMA_INVALID'
    $heartbeat = ConvertTo-GateWUtcTimestamp ([string]$Proof.heartbeatObservedAt) 'ACCEPTANCE_PROOF_SCHEMA_INVALID'
    $null = ConvertTo-GateWUtcTimestamp ([string]$Proof.verifiedAt) 'ACCEPTANCE_PROOF_SCHEMA_INVALID'
    if ([string]$Proof.schemaVersion -cne 'gatew-soak-acceptance-proof-v1' -or
            [string]$Proof.runId -cnotmatch $script:RunIdPattern -or
            [string]$Proof.releaseCommit -cnotmatch $script:CommitPattern -or
            [long]$Proof.mainPid -le 0 -or [long]$Proof.nRestarts -ne 0 -or
            [long]$Proof.execMainStartTimestampMonotonic -le 0 -or
            [double]$Proof.observedDurationSeconds -lt 604800.0 -or
            ($planned - $start).TotalSeconds -ne 604800.0 -or
            $lastSample -lt $planned -or $heartbeat -lt $planned -or
            [string]$Proof.evidenceManifestSha256 -cnotmatch $script:Sha256Pattern -or
            [string]$Proof.evidenceFinalChainHash -cnotmatch $script:Sha256Pattern -or
            [string]$Proof.result -cne 'PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED' -or
            [string]$Proof.checksum -cnotmatch $script:Sha256Pattern)
    {
        throw 'FAIL / ACCEPTANCE_PROOF_SCHEMA_INVALID'
    }
    $expected = Get-GateWRecordChecksum $Proof @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid', 'nRestarts',
        'execMainStartTimestampMonotonic', 'acceptanceStartAt', 'plannedAcceptanceAt',
        'observedDurationSeconds', 'lastValidSampleAt', 'heartbeatObservedAt',
        'evidenceManifestSha256', 'evidenceFinalChainHash', 'verifiedAt', 'result'
    )
    if ([string]$Proof.checksum -cne $expected)
    {
        throw 'FAIL / ACCEPTANCE_PROOF_CHECKSUM_INVALID'
    }
    return $Proof
}

function New-GateWTerminalRecord
{
    param(
        [Parameter(Mandatory = $true)][string]$RunId,
        [Parameter(Mandatory = $true)][string]$ReleaseCommit,
        [Parameter(Mandatory = $true)][string]$AcceptanceResult,
        [Parameter(Mandatory = $true)][string]$TerminalReasonCode,
        [Parameter(Mandatory = $true)][string]$StopClassification,
        [AllowNull()]$AcceptanceStartAt,
        [AllowNull()]$PlannedAcceptanceAt,
        [Parameter(Mandatory = $true)][string]$AcceptanceVerificationChecksum,
        [Parameter(Mandatory = $true)][string]$EvidenceManifestSha256,
        [Parameter(Mandatory = $true)][string]$EvidenceFinalChainHash,
        [Parameter(Mandatory = $true)][string]$StopIntentChecksum,
        [Parameter(Mandatory = $true)][string]$FinalizerKind,
        [Parameter(Mandatory = $true)][string]$FinalizedAt
    )

    $record = [ordered]@{
        schemaVersion = 'gatew-soak-terminal-v2'
        runId = $RunId
        releaseCommit = $ReleaseCommit
        acceptanceResult = $AcceptanceResult
        terminalReasonCode = $TerminalReasonCode
        stopClassification = $StopClassification
        acceptanceStartAt = $AcceptanceStartAt
        plannedAcceptanceAt = $PlannedAcceptanceAt
        acceptanceVerificationChecksum = $AcceptanceVerificationChecksum
        evidenceManifestSha256 = $EvidenceManifestSha256
        evidenceFinalChainHash = $EvidenceFinalChainHash
        stopIntentChecksum = $StopIntentChecksum
        finalizerKind = $FinalizerKind
        finalizedAt = $FinalizedAt
        credentialAccessed = $false
        networkCalled = $false
        okxCalled = $false
        attempt10Created = $false
    }
    $record.checksum = Get-GateWRecordChecksum ([pscustomobject]$record) @(
        'schemaVersion', 'runId', 'releaseCommit', 'acceptanceResult', 'terminalReasonCode',
        'stopClassification', 'acceptanceStartAt', 'plannedAcceptanceAt',
        'acceptanceVerificationChecksum', 'evidenceManifestSha256', 'evidenceFinalChainHash',
        'stopIntentChecksum', 'finalizerKind', 'finalizedAt', 'credentialAccessed',
        'networkCalled', 'okxCalled', 'attempt10Created'
    )
    $value = [pscustomobject]$record
    Assert-GateWTerminalRecord $value | Out-Null
    return $value
}

function Assert-GateWTerminalRecord
{
    param([Parameter(Mandatory = $true)]$Terminal)

    $fields = @(
        'schemaVersion', 'runId', 'releaseCommit', 'acceptanceResult', 'terminalReasonCode',
        'stopClassification', 'acceptanceStartAt', 'plannedAcceptanceAt',
        'acceptanceVerificationChecksum', 'evidenceManifestSha256', 'evidenceFinalChainHash',
        'stopIntentChecksum', 'finalizerKind', 'finalizedAt', 'credentialAccessed',
        'networkCalled', 'okxCalled', 'attempt10Created', 'checksum'
    )
    Assert-GateWExactFields $Terminal $fields 'TERMINAL_SCHEMA_INVALID'
    $null = ConvertTo-GateWUtcTimestamp ([string]$Terminal.finalizedAt) 'TERMINAL_SCHEMA_INVALID'
    $allowedResults = @(
        'ACCEPTED_168H_READONLY_SOAK',
        'REJECTED_RUNTIME_EXIT',
        'REJECTED_UNAUTHORIZED_OR_UNKNOWN_STOP',
        'REJECTED_INSUFFICIENT_DURATION',
        'REJECTED_FINALIZER_ERROR'
    )
    if (([string]$Terminal.schemaVersion -cne 'gatew-soak-terminal-v2') -or
            ([string]$Terminal.runId -cnotmatch $script:RunIdPattern) -or
            ([string]$Terminal.releaseCommit -cnotmatch '^(?:[a-f0-9]{40}|UNKNOWN)$') -or
            ([string]$Terminal.acceptanceResult -cnotin $allowedResults) -or
            ([string]$Terminal.terminalReasonCode -cnotmatch $script:SafeCodePattern) -or
            ([string]$Terminal.stopClassification -cnotin @(
                'AUTHORIZED_CONTROLLED_STOP', 'UNAUTHORIZED_OR_UNKNOWN_STOP', 'NOT_PROVEN'
            )) -or ([string]$Terminal.finalizerKind -cnotin @(
                'AUTOMATIC_FAIL_CLOSE', 'EXPLICIT_ACCEPTANCE'
            )) -or $Terminal.credentialAccessed -isnot [bool] -or
            $Terminal.networkCalled -isnot [bool] -or
            $Terminal.okxCalled -isnot [bool] -or
            $Terminal.attempt10Created -isnot [bool] -or
            [bool]$Terminal.credentialAccessed -or [bool]$Terminal.networkCalled -or
            [bool]$Terminal.okxCalled -or [bool]$Terminal.attempt10Created -or
            [string]$Terminal.checksum -cnotmatch $script:Sha256Pattern)
    {
        throw 'FAIL / TERMINAL_SCHEMA_INVALID'
    }
    if ($null -ne $Terminal.acceptanceStartAt -or $null -ne $Terminal.plannedAcceptanceAt)
    {
        if ($null -eq $Terminal.acceptanceStartAt -or $null -eq $Terminal.plannedAcceptanceAt)
        {
            throw 'FAIL / TERMINAL_CLOCK_BINDING_INVALID'
        }
        $start = ConvertTo-GateWUtcTimestamp ([string]$Terminal.acceptanceStartAt) 'TERMINAL_CLOCK_BINDING_INVALID'
        $planned = ConvertTo-GateWUtcTimestamp ([string]$Terminal.plannedAcceptanceAt) 'TERMINAL_CLOCK_BINDING_INVALID'
        if (($planned - $start).TotalSeconds -ne 604800.0)
        {
            throw 'FAIL / TERMINAL_CLOCK_BINDING_INVALID'
        }
    }
    if ([string]$Terminal.acceptanceResult -eq 'ACCEPTED_168H_READONLY_SOAK')
    {
        if ([string]$Terminal.releaseCommit -cnotmatch $script:CommitPattern -or
                [string]$Terminal.stopClassification -cne 'AUTHORIZED_CONTROLLED_STOP' -or
                [string]$Terminal.finalizerKind -cne 'EXPLICIT_ACCEPTANCE' -or
                [string]$Terminal.acceptanceVerificationChecksum -cnotmatch $script:Sha256Pattern -or
                [string]$Terminal.evidenceManifestSha256 -cnotmatch $script:Sha256Pattern -or
                [string]$Terminal.evidenceFinalChainHash -cnotmatch $script:Sha256Pattern -or
                [string]$Terminal.stopIntentChecksum -cnotmatch $script:Sha256Pattern -or
                $null -eq $Terminal.acceptanceStartAt -or $null -eq $Terminal.plannedAcceptanceAt)
        {
            throw 'FAIL / ACCEPTED_TERMINAL_BINDING_INVALID'
        }
    }
    elseif ([string]$Terminal.finalizerKind -cne 'AUTOMATIC_FAIL_CLOSE' -or
            [string]$Terminal.acceptanceVerificationChecksum -cne 'NOT_APPLICABLE' -or
            [string]$Terminal.evidenceManifestSha256 -cne 'NOT_VERIFIED' -or
            [string]$Terminal.evidenceFinalChainHash -cne 'NOT_VERIFIED')
    {
        throw 'FAIL / REJECTED_TERMINAL_BINDING_INVALID'
    }
    if (([string]$Terminal.releaseCommit -ceq 'UNKNOWN' -and
            [string]$Terminal.acceptanceResult -cne 'REJECTED_FINALIZER_ERROR') -or
            ([string]$Terminal.stopClassification -ceq 'AUTHORIZED_CONTROLLED_STOP' -and
                    [string]$Terminal.stopIntentChecksum -cnotmatch $script:Sha256Pattern) -or
            ([string]$Terminal.stopClassification -cne 'AUTHORIZED_CONTROLLED_STOP' -and
                    [string]$Terminal.stopIntentChecksum -cnotin @('NOT_PRESENT', 'INVALID')))
    {
        throw 'FAIL / TERMINAL_AUTHORITY_BINDING_INVALID'
    }
    $expected = Get-GateWRecordChecksum $Terminal @(
        'schemaVersion', 'runId', 'releaseCommit', 'acceptanceResult', 'terminalReasonCode',
    'stopClassification', 'acceptanceStartAt', 'plannedAcceptanceAt',
    'acceptanceVerificationChecksum', 'evidenceManifestSha256', 'evidenceFinalChainHash',
    'stopIntentChecksum', 'finalizerKind', 'finalizedAt', 'credentialAccessed',
    'networkCalled', 'okxCalled', 'attempt10Created'
    )
    if ([string]$Terminal.checksum -cne $expected)
    {
        throw 'FAIL / TERMINAL_CHECKSUM_INVALID'
    }
    return $Terminal
}

Export-ModuleMember -Function @(
    'ConvertTo-GateWCompactJson',
    'Get-GateWSha256Text',
    'Get-GateWRecordChecksum',
    'New-GateWStopIntentRecord',
    'Assert-GateWStopIntentRecord',
    'New-GateWCompletionMarkerRecord',
    'Assert-GateWCompletionMarkerRecord',
    'Test-GateWAcceptanceSnapshot',
    'New-GateWAcceptanceProof',
    'Assert-GateWAcceptanceProof',
    'New-GateWTerminalRecord',
    'Assert-GateWTerminalRecord'
)
