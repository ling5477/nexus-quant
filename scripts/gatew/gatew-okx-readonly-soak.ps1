[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('start', 'status', 'resume', 'stop', 'failure-stop', 'evidence-verify', 'cleanup', 'run-loop', 'self-test')]
    [string]$Action,

    [string]$RunId,

    [ValidateRange(60, 86400)]
    [int]$CadenceSeconds = 900,

    [ValidateRange(168, 720)]
    [int]$DurationHours = 168,

    [ValidateRange(0, 5)]
    [int]$MaxTransientRetries = 2,

    [ValidateRange(1, 10)]
    [int]$MaxConsecutiveAuthFailures = 3,

    [string]$StartingCiRun
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:ScriptPath = $PSCommandPath
$script:RepoRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path
$script:EvidenceRoot = [IO.Path]::GetFullPath((Join-Path $script:RepoRoot 'target\gatew-okx-readonly-soak'))
$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)
$script:GenesisHash = '0' * 64
$script:SampleFields = @(
    'sequence', 'observedAt', 'durationMs', 'resultStatus', 'reasonCode', 'httpStatusCategory',
    'permissionClassification', 'killSwitchObservedState', 'credentialAccessed', 'networkCalled',
    'allowedEndpointCategory', 'traceId', 'previousRecordHash', 'recordHash'
)
$script:TransientReasons = @('NETWORK_FAILURE', 'TIMEOUT', 'RATE_LIMITED', 'HTTP_ERROR', 'OKX_PROVIDER_ERROR')
$script:AuthenticationReasons = @('AUTHENTICATION_FAILURE', 'SIGNATURE_FAILURE')
$script:ImmediateStopReasons = @(
    'FORBIDDEN_ENDPOINT_ATTEMPTED',
    'IP_ALLOWLIST_FAILED',
    'PERMISSION_BLOCKED',
    'KILL_SWITCH_CHANGED_DURING_SAMPLE',
    'SOAK_DATABASE_CONTAINS_BUSINESS_DATA',
    'SOAK_DATABASE_NOT_LOCAL'
)
$script:RuntimeEnvironmentNames = @(
    'SPRING_PROFILES_ACTIVE', 'NQ_GATEW_OKX_READONLY_SOAK_ENABLED', 'CI', 'NQ_NO_OUTBOUND',
    'NQ_LIVE_ENABLED', 'NQ_REAL_ORDER_SUBMISSION_ENABLED', 'NQ_TRANSFER_ENABLED', 'NQ_WITHDRAW_ENABLED',
    'NQ_AI_ENABLED', 'NQ_DH_RUNTIME_ENABLED', 'NQ_REAL_PROVIDER_ENABLED', 'NQ_REAL_CLIENT_ENABLED',
    'NQ_REAL_EXCHANGE_ENABLED', 'NQ_GATEW_SOAK_DB_URL', 'NQ_GATEW_SOAK_DB_USER',
    'NQ_GATEW_SOAK_DB_PASSWORD', 'NQ_ACCOUNT_CREDENTIALS_MASTER_KEY', 'NQ_GATEW_SOAK_OWNER_ID',
    'NQ_GATEW_SOAK_ACCOUNT_ID', 'NQ_GATEW_SOAK_CURRENCIES', 'NQ_OKX_API_KEY', 'NQ_OKX_API_SECRET',
    'NQ_OKX_API_PASSPHRASE', 'NQ_OKX_REAL_API_KEY', 'NQ_OKX_REAL_API_SECRET', 'NQ_OKX_REAL_API_PASSPHRASE'
)

function Get-UtcNow {
    return [DateTimeOffset]::UtcNow
}

function Get-RuntimeEnvironmentSnapshot {
    $snapshot = @{}
    foreach ($name in $script:RuntimeEnvironmentNames) {
        $snapshot[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    return $snapshot
}

function Assert-RuntimeEnvironment {
    param([Parameter(Mandatory = $true)][hashtable]$Environment)

    foreach ($name in @(
        'NQ_OKX_API_KEY', 'NQ_OKX_API_SECRET', 'NQ_OKX_API_PASSPHRASE',
        'NQ_OKX_REAL_API_KEY', 'NQ_OKX_REAL_API_SECRET', 'NQ_OKX_REAL_API_PASSPHRASE'
    )) {
        if (-not [string]::IsNullOrWhiteSpace([string]$Environment[$name])) {
            throw 'BLOCKED / DIRECT_OKX_CREDENTIAL_INPUT_FORBIDDEN'
        }
    }
    foreach ($name in @('NQ_ACCOUNT_CREDENTIALS_MASTER_KEY', 'NQ_GATEW_SOAK_OWNER_ID', 'NQ_GATEW_SOAK_ACCOUNT_ID')) {
        if ([string]::IsNullOrWhiteSpace([string]$Environment[$name])) {
            throw 'BLOCKED / API_KEY_REQUIRED'
        }
    }
    foreach ($name in @('NQ_GATEW_SOAK_DB_URL', 'NQ_GATEW_SOAK_DB_USER', 'NQ_GATEW_SOAK_DB_PASSWORD')) {
        if ([string]::IsNullOrWhiteSpace([string]$Environment[$name])) {
            throw 'BLOCKED / SOAK_DATABASE_CONFIG_REQUIRED'
        }
    }
    if ([string]$Environment.SPRING_PROFILES_ACTIVE -ne 'gatew-okx-readonly-soak') {
        throw 'BLOCKED / SOAK_PROFILE_REQUIRED'
    }
    if ([string]$Environment.NQ_GATEW_OKX_READONLY_SOAK_ENABLED -ne 'true') {
        throw 'BLOCKED / SOAK_FEATURE_FLAG_REQUIRED'
    }
    if ([string]$Environment.CI -eq 'true' -or [string]$Environment.NQ_NO_OUTBOUND -eq 'true') {
        throw 'BLOCKED / SOAK_OUTBOUND_FORBIDDEN_IN_CI'
    }
    foreach ($name in @(
        'NQ_LIVE_ENABLED', 'NQ_REAL_ORDER_SUBMISSION_ENABLED', 'NQ_TRANSFER_ENABLED', 'NQ_WITHDRAW_ENABLED',
        'NQ_AI_ENABLED', 'NQ_DH_RUNTIME_ENABLED', 'NQ_REAL_PROVIDER_ENABLED', 'NQ_REAL_CLIENT_ENABLED',
        'NQ_REAL_EXCHANGE_ENABLED'
    )) {
        if ([string]$Environment[$name] -ne 'false') {
            throw "BLOCKED / ${name}_MUST_BE_FALSE"
        }
    }
    if ([string]::IsNullOrWhiteSpace([string]$Environment.NQ_GATEW_SOAK_CURRENCIES)) {
        throw 'BLOCKED / SOAK_CURRENCY_ALLOWLIST_REQUIRED'
    }
}

function Get-Sha256Text {
    param([Parameter(Mandatory = $true)][string]$Text)

    $bytes = $script:Utf8NoBom.GetBytes($Text)
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try {
        $hash = $sha256.ComputeHash($bytes)
        return -join ($hash | ForEach-Object { $_.ToString('x2') })
    }
    finally {
        $sha256.Dispose()
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Get-Sha256File {
    param([Parameter(Mandatory = $true)][string]$Path)

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function ConvertTo-CompactJson {
    param([Parameter(Mandatory = $true)]$Value)

    return ($Value | ConvertTo-Json -Compress -Depth 12)
}

function Write-JsonAtomic {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value
    )

    $parent = Split-Path -Parent $Path
    [IO.Directory]::CreateDirectory($parent) | Out-Null
    $temporary = "$Path.tmp-$PID"
    [IO.File]::WriteAllText($temporary, (ConvertTo-CompactJson $Value), $script:Utf8NoBom)
    Move-Item -LiteralPath $temporary -Destination $Path -Force
}

function Read-JsonFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "required evidence file is missing"
    }
    return (Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json)
}

function Assert-RunId {
    param([Parameter(Mandatory = $true)][string]$Value)

    if ($Value -notmatch '^gatew-soak-[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$') {
        throw 'runId is invalid'
    }
}

function New-RunId {
    $timestamp = (Get-UtcNow).ToString('yyyyMMddTHHmmssZ')
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
    return "gatew-soak-$timestamp-$suffix"
}

function Get-RunDirectory {
    param([Parameter(Mandatory = $true)][string]$Value)

    Assert-RunId $Value
    $candidate = [IO.Path]::GetFullPath((Join-Path $script:EvidenceRoot $Value))
    if (-not $candidate.StartsWith($script:EvidenceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'run directory escaped evidence root'
    }
    return $candidate
}

function Get-HeadCommit {
    $value = (& git -C $script:RepoRoot rev-parse HEAD 2>$null)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($value)) {
        throw 'unable to resolve harness commit'
    }
    return $value.Trim().ToLowerInvariant()
}

function Assert-FixedDetachedWorktree {
    param([string]$ExpectedCommit)

    $status = @(& git -C $script:RepoRoot status --porcelain --untracked-files=no 2>$null)
    if ($LASTEXITCODE -ne 0 -or $status.Count -ne 0) {
        throw 'BLOCKED / HARNESS_WORKTREE_NOT_CLEAN'
    }
    $branch = [string](& git -C $script:RepoRoot branch --show-current 2>$null)
    $branch = $branch.Trim()
    if (-not [string]::IsNullOrWhiteSpace($branch)) {
        throw 'BLOCKED / FIXED_COMMIT_WORKTREE_REQUIRED'
    }
    $head = Get-HeadCommit
    if (-not [string]::IsNullOrWhiteSpace($ExpectedCommit) -and $head -ne $ExpectedCommit.ToLowerInvariant()) {
        throw 'BLOCKED / HARNESS_COMMIT_CHANGED'
    }
    return $head
}

function Assert-ExactHeadCi {
    param(
        [Parameter(Mandatory = $true)][string]$CiRun,
        [Parameter(Mandatory = $true)][string]$Commit
    )

    if ($CiRun -notmatch '^[0-9]+$') {
        throw 'BLOCKED / EXACT_HEAD_CI_REQUIRED'
    }
    $json = (& gh run view $CiRun --json status,conclusion,headSha,jobs 2>$null)
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($json)) {
        throw 'BLOCKED / EXACT_HEAD_CI_NOT_VERIFIABLE'
    }
    $ci = $json | ConvertFrom-Json
    $bad = @($ci.jobs | Where-Object { $_.status -ne 'completed' -or $_.conclusion -ne 'success' })
    if ($ci.status -ne 'completed' -or $ci.conclusion -ne 'success' -or
        $ci.headSha.ToLowerInvariant() -ne $Commit -or @($ci.jobs).Count -ne 10 -or $bad.Count -ne 0) {
        throw 'BLOCKED / EXACT_HEAD_CI_NOT_GREEN'
    }
}

function Invoke-SanitizedCycle {
    param(
        [Parameter(Mandatory = $true)][string]$CycleAction,
        [Parameter(Mandatory = $true)][string]$Directory
    )

    $cycleFile = Join-Path $Directory ".cycle-$PID.json"
    if (Test-Path -LiteralPath $cycleFile) {
        Remove-Item -LiteralPath $cycleFile -Force
    }
    $arguments = @(
        '-f', (Join-Path $script:RepoRoot 'backend\pom.xml'),
        '-pl', 'nq-app', '-am',
        '-Dtest=GateWOkxReadonlySoakCycleTest',
        '-Dsurefire.failIfNoSpecifiedTests=false',
        '-Dnq.gatew.okxReadonlySoak.required=true',
        "-Dnq.gatew.okxReadonlySoak.action=$CycleAction",
        "-Dnq.gatew.okxReadonlySoak.resultFile=$cycleFile",
        "-Dnq.gatew.okxReadonlySoak.repoRoot=$script:RepoRoot",
        '--quiet', 'test'
    )
    try {
        $null = & mvn @arguments 2>&1
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0 -or -not (Test-Path -LiteralPath $cycleFile -PathType Leaf)) {
            return [pscustomobject][ordered]@{
                resultStatus                  = 'HARD_FAILURE'
                reasonCode                    = 'SOAK_LAUNCHER_FAILED'
                httpStatusCategory            = 'NOT_CALLED'
                permissionClassification      = 'UNKNOWN'
                killSwitchObservedState       = 'UNKNOWN'
                credentialAccessed            = $false
                networkCalled                 = $false
                allowedEndpointCategory       = 'NONE'
                traceId                       = "gatew-soak-$([Guid]::NewGuid())"
                databaseFingerprint           = 'UNAVAILABLE'
                credentialReferenceFingerprint = 'UNAVAILABLE'
                flywayVersion                 = 'UNAVAILABLE'
                endpointAllowlistVersion      = 'gatew-okx-private-readonly-v1'
                observedAt                    = (Get-UtcNow).ToString('o')
                durationMs                    = 0
                authenticationFailure         = $false
                killSwitchVersion             = 0
            }
        }
        return Read-JsonFile $cycleFile
    }
    finally {
        if (Test-Path -LiteralPath $cycleFile) {
            Remove-Item -LiteralPath $cycleFile -Force
        }
    }
}

function Get-ChainState {
    param([Parameter(Mandatory = $true)][string]$Directory)

    $samplesPath = Join-Path $Directory 'samples.jsonl'
    if (-not (Test-Path -LiteralPath $samplesPath -PathType Leaf)) {
        throw 'samples.jsonl is missing'
    }
    $expectedSequence = 1L
    $previousHash = $script:GenesisHash
    $count = 0L
    foreach ($line in Get-Content -LiteralPath $samplesPath) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        $record = $line | ConvertFrom-Json
        $fields = @($record.PSObject.Properties.Name)
        if (@(Compare-Object $script:SampleFields $fields).Count -ne 0) {
            throw 'sample evidence schema is invalid'
        }
        if ([long]$record.sequence -ne $expectedSequence) {
            throw 'sample sequence is missing or duplicated'
        }
        if ($record.previousRecordHash -ne $previousHash) {
            throw 'sample previousRecordHash is invalid'
        }
        $hashInput = [ordered]@{}
        foreach ($field in $script:SampleFields) {
            if ($field -ne 'recordHash') {
                $hashInput[$field] = $record.$field
            }
        }
        $expectedHash = Get-Sha256Text (ConvertTo-CompactJson $hashInput)
        if ($record.recordHash -ne $expectedHash) {
            throw 'sample recordHash is invalid'
        }
        $previousHash = $record.recordHash
        $expectedSequence++
        $count++
    }
    return [pscustomobject]@{ Count = $count; LastHash = $previousHash; NextSequence = $expectedSequence }
}

function Append-Sample {
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)]$Cycle
    )

    $state = Get-ChainState $Directory
    $hashInput = [ordered]@{
        sequence                 = [long]$state.NextSequence
        observedAt               = [string]$Cycle.observedAt
        durationMs               = [long]$Cycle.durationMs
        resultStatus             = [string]$Cycle.resultStatus
        reasonCode               = [string]$Cycle.reasonCode
        httpStatusCategory       = [string]$Cycle.httpStatusCategory
        permissionClassification = [string]$Cycle.permissionClassification
        killSwitchObservedState  = [string]$Cycle.killSwitchObservedState
        credentialAccessed       = [bool]$Cycle.credentialAccessed
        networkCalled            = [bool]$Cycle.networkCalled
        allowedEndpointCategory  = [string]$Cycle.allowedEndpointCategory
        traceId                  = [string]$Cycle.traceId
        previousRecordHash       = [string]$state.LastHash
    }
    $record = [ordered]@{}
    foreach ($key in $hashInput.Keys) { $record[$key] = $hashInput[$key] }
    $record.recordHash = Get-Sha256Text (ConvertTo-CompactJson $hashInput)
    $line = ConvertTo-CompactJson $record
    [IO.File]::AppendAllText((Join-Path $Directory 'samples.jsonl'), $line + [Environment]::NewLine, $script:Utf8NoBom)
    if ($Cycle.resultStatus -ne 'PASSED_READ_ONLY') {
        [IO.File]::AppendAllText((Join-Path $Directory 'failures.jsonl'), $line + [Environment]::NewLine, $script:Utf8NoBom)
    }
    return [pscustomobject]$record
}

function Write-Heartbeat {
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][string]$State,
        [Parameter(Mandatory = $true)][string]$ReasonCode,
        [long]$Sequence = 0,
        [int]$ConsecutiveAuthenticationFailures = 0
    )

    Write-JsonAtomic (Join-Path $Directory 'heartbeat.json') ([ordered]@{
        runId                             = Split-Path -Leaf $Directory
        state                             = $State
        reasonCode                        = $ReasonCode
        observedAt                        = (Get-UtcNow).ToString('o')
        lastSequence                      = $Sequence
        consecutiveAuthenticationFailures = $ConsecutiveAuthenticationFailures
    })
}

function Test-Evidence {
    param([Parameter(Mandatory = $true)][string]$Directory)

    $manifest = Read-JsonFile (Join-Path $Directory 'manifest.json')
    if ($manifest.runId -ne (Split-Path -Leaf $Directory)) { throw 'manifest runId mismatch' }
    if ($manifest.profile -ne 'gatew-okx-readonly-soak') { throw 'manifest profile mismatch' }
    if ($manifest.venue -ne 'OKX') { throw 'manifest venue mismatch' }
    if ([int]$manifest.durationHours -lt 168) { throw 'manifest duration is below 168 hours' }
    if ([int]$manifest.cadenceSeconds -lt 60) { throw 'manifest cadence is invalid' }
    if (Test-Path -LiteralPath (Join-Path $Directory 'final-summary.json')) {
        throw 'final-summary.json is acceptance-task-only'
    }
    foreach ($required in @('samples.jsonl', 'failures.jsonl', 'heartbeat.json')) {
        if (-not (Test-Path -LiteralPath (Join-Path $Directory $required) -PathType Leaf)) {
            throw "$required is missing"
        }
    }
    $chain = Get-ChainState $Directory
    foreach ($path in Get-ChildItem -LiteralPath $Directory -File) {
        $text = Get-Content -LiteralPath $path.FullName -Raw
        if ($text -match '(?i)"(apiKey|api_key|secret|passphrase|signature|cookie|rawBody|rawHeaders|rawResponse|accountId|balance|position|order)"\s*:' -or
            $text -match '(?i)https?://') {
            throw 'evidence contains a forbidden material shape'
        }
    }
    return [pscustomobject]@{
        runId       = $manifest.runId
        harnessCommit = $manifest.harnessCommit
        sampleCount = $chain.Count
        lastHash    = $chain.LastHash
        result      = 'PASS / HASH_CHAIN_VERIFIED'
    }
}

function Get-SupervisorState {
    param([Parameter(Mandatory = $true)][string]$Directory)

    $path = Join-Path $Directory 'supervisor.json'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        return [pscustomobject]@{ Running = $false; Pid = 0; StartedAt = $null }
    }
    $control = Read-JsonFile $path
    $process = Get-Process -Id ([int]$control.pid) -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        return [pscustomobject]@{ Running = $false; Pid = [int]$control.pid; StartedAt = $control.startedAt }
    }
    $actual = $process.StartTime.ToUniversalTime()
    $expected = [DateTimeOffset]::Parse([string]$control.startedAt).UtcDateTime
    $sameProcess = [Math]::Abs(($actual - $expected).TotalSeconds) -lt 5
    return [pscustomobject]@{ Running = $sameProcess; Pid = [int]$control.pid; StartedAt = $control.startedAt }
}

function Start-LoopProcess {
    param([Parameter(Mandatory = $true)][string]$Value)

    $executable = (Get-Process -Id $PID).Path
    $arguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $script:ScriptPath + '"'),
        '-Action', 'run-loop', '-RunId', $Value
    )
    $process = Start-Process -FilePath $executable -ArgumentList $arguments -WindowStyle Hidden -PassThru
    $startedAt = $process.StartTime.ToUniversalTime().ToString('o')
    $directory = Get-RunDirectory $Value
    Write-JsonAtomic (Join-Path $directory 'supervisor.json') ([ordered]@{
        pid       = $process.Id
        startedAt = $startedAt
        runId     = $Value
    })
    return $process
}

function Stop-FailClosed {
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][string]$ReasonCode,
        [Parameter(Mandatory = $true)][string]$State,
        [int]$AuthenticationFailures = 0
    )

    $engage = Invoke-SanitizedCycle 'engage' $Directory
    $chain = Get-ChainState $Directory
    if ($engage.resultStatus -ne 'ENGAGED' -or $engage.killSwitchObservedState -ne 'ENGAGED') {
        Write-Heartbeat $Directory 'STOP_FAILURE' 'KILL_SWITCH_ENGAGE_FAILED' $chain.Count $AuthenticationFailures
        throw 'FAIL / KILL_SWITCH_ENGAGE_FAILED'
    }
    Write-Heartbeat $Directory $State $ReasonCode $chain.Count $AuthenticationFailures
}

function Resolve-Blocker {
    param([Parameter(Mandatory = $true)][string]$ReasonCode)

    $decision = switch ($ReasonCode) {
        'API_KEY_REQUIRED' { 'BLOCKED / API_KEY_REQUIRED' }
        'PERMISSION_BLOCKED' { 'BLOCKED / CREDENTIAL_PERMISSION_NOT_READONLY' }
        'CREDENTIAL_PERMISSION_NOT_READONLY' { 'BLOCKED / CREDENTIAL_PERMISSION_NOT_READONLY' }
        'UNSAFE_CREDENTIAL_PERMISSIONS' { 'BLOCKED / UNSAFE_CREDENTIAL_PERMISSIONS' }
        'IP_ALLOWLIST_FAILED' { 'BLOCKED / IP_ALLOWLIST_REQUIRED' }
        'IP_ALLOWLIST_REQUIRED' { 'BLOCKED / IP_ALLOWLIST_REQUIRED' }
        'SOAK_KILL_SWITCH_FIXTURE_NOT_SAFE' { 'BLOCKED / SOAK_KILL_SWITCH_FIXTURE_NOT_SAFE' }
        default { "BLOCKED / $ReasonCode" }
    }
    return $decision
}

function Start-Soak {
    $head = Assert-FixedDetachedWorktree
    Assert-RuntimeEnvironment (Get-RuntimeEnvironmentSnapshot)
    if ([string]::IsNullOrWhiteSpace($StartingCiRun)) { throw 'BLOCKED / EXACT_HEAD_CI_REQUIRED' }
    Assert-ExactHeadCi $StartingCiRun $head
    $effectiveRunId = if ([string]::IsNullOrWhiteSpace($RunId)) { New-RunId } else { $RunId }
    $directory = Get-RunDirectory $effectiveRunId
    if (Test-Path -LiteralPath $directory) { throw 'BLOCKED / RUN_ID_ALREADY_EXISTS' }
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    [IO.File]::WriteAllText((Join-Path $directory 'samples.jsonl'), '', $script:Utf8NoBom)
    [IO.File]::WriteAllText((Join-Path $directory 'failures.jsonl'), '', $script:Utf8NoBom)
    Write-Heartbeat $directory 'PREPARING' 'HARD_GATES_PENDING'

    $bootstrap = Invoke-SanitizedCycle 'bootstrap' $directory
    if ($bootstrap.resultStatus -ne 'BOOTSTRAP_READY') {
        $engage = Invoke-SanitizedCycle 'engage' $directory
        if ($engage.resultStatus -ne 'ENGAGED' -or $engage.killSwitchObservedState -ne 'ENGAGED') {
            throw 'FAIL / KILL_SWITCH_ENGAGE_FAILED'
        }
        Write-Heartbeat $directory 'BLOCKED' ([string]$bootstrap.reasonCode)
        throw (Resolve-Blocker ([string]$bootstrap.reasonCode))
    }

    $ownershipTransferred = $false
    try {
        $startedAt = Get-UtcNow
        $manifest = [ordered]@{
            runId                         = $effectiveRunId
            harnessCommit                 = $head
            startingCiRun                 = $StartingCiRun
            startedAt                     = $startedAt.ToString('o')
            plannedEndAt                  = $startedAt.AddHours($DurationHours).ToString('o')
            durationHours                 = $DurationHours
            cadenceSeconds                = $CadenceSeconds
            maxTransientRetries           = $MaxTransientRetries
            maxConsecutiveAuthFailures    = $MaxConsecutiveAuthFailures
            venue                         = 'OKX'
            environment                   = 'REAL_OKX_PRIVATE_READONLY'
            profile                       = 'gatew-okx-readonly-soak'
            applicationVersion            = "0.1.0-SNAPSHOT+$($head.Substring(0, 12))"
            databaseFingerprint           = [string]$bootstrap.databaseFingerprint
            credentialReferenceFingerprint = [string]$bootstrap.credentialReferenceFingerprint
            endpointAllowlistVersion      = [string]$bootstrap.endpointAllowlistVersion
            flywayVersion                 = [string]$bootstrap.flywayVersion
            hostFingerprint               = Get-Sha256Text "$env:COMPUTERNAME|$([Environment]::OSVersion.VersionString)"
            supervisorScriptSha256        = Get-Sha256File $script:ScriptPath
            evidenceSchemaVersion         = 'gatew-soak-evidence-v1'
        }
        Write-JsonAtomic (Join-Path $directory 'manifest.json') $manifest

        $first = Invoke-SanitizedCycle 'sample' $directory
        $record = Append-Sample $directory $first
        if ($first.resultStatus -ne 'PASSED_READ_ONLY') {
            Stop-FailClosed $directory ([string]$first.reasonCode) 'BLOCKED'
            throw (Resolve-Blocker ([string]$first.reasonCode))
        }

        $process = Start-LoopProcess $effectiveRunId
        Write-Heartbeat $directory 'RUNNING' 'SOAK_STARTED' $record.sequence
        $ownershipTransferred = $true
        return [pscustomobject]@{
            decision       = 'PASS / REAL_OKX_READONLY_SOAK_PREPARED / SOAK_STARTED / SEVEN_DAY_ACCEPTANCE_PENDING'
            runId          = $effectiveRunId
            harnessCommit  = $head
            startingCiRun  = $StartingCiRun
            startedAt      = $manifest.startedAt
            plannedEndAt   = $manifest.plannedEndAt
            cadenceSeconds = $CadenceSeconds
            supervisorPid  = $process.Id
            evidenceDirectory = $directory
        }
    }
    finally {
        if (-not $ownershipTransferred) {
            $engage = Invoke-SanitizedCycle 'engage' $directory
            if ($engage.resultStatus -ne 'ENGAGED' -or $engage.killSwitchObservedState -ne 'ENGAGED') {
                throw 'FAIL / KILL_SWITCH_ENGAGE_FAILED'
            }
        }
    }
}

function Wait-ForCadenceOrControl {
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][int]$Seconds,
        [Parameter(Mandatory = $true)][DateTimeOffset]$PlannedEndAt
    )

    $deadline = (Get-UtcNow).AddSeconds($Seconds)
    if ($deadline -gt $PlannedEndAt) { $deadline = $PlannedEndAt }
    while ((Get-UtcNow) -lt $deadline) {
        if (Test-Path -LiteralPath (Join-Path $Directory 'stop-request.json')) { return $false }
        $remaining = [Math]::Max(1, ($deadline - (Get-UtcNow)).TotalSeconds)
        Start-Sleep -Seconds ([int][Math]::Min(5, [Math]::Ceiling($remaining)))
    }
    return $true
}

function Run-SoakLoop {
    $directory = Get-RunDirectory $RunId
    $manifest = Read-JsonFile (Join-Path $directory 'manifest.json')
    Assert-FixedDetachedWorktree ([string]$manifest.harnessCommit) | Out-Null
    Test-Evidence $directory | Out-Null
    $plannedEndAt = [DateTimeOffset]::Parse([string]$manifest.plannedEndAt)
    $authFailures = 0

    while ((Get-UtcNow) -lt $plannedEndAt) {
        Wait-ForCadenceOrControl $directory ([int]$manifest.cadenceSeconds) $plannedEndAt | Out-Null
        $stopPath = Join-Path $directory 'stop-request.json'
        if (Test-Path -LiteralPath $stopPath) {
            $request = Read-JsonFile $stopPath
            $state = if ($request.kind -eq 'failure') { 'FAILURE_STOPPED' } else { 'STOPPED' }
            Stop-FailClosed $directory ([string]$request.reasonCode) $state $authFailures
            return
        }
        if ((Get-UtcNow) -ge $plannedEndAt) { break }

        $retry = 0
        do {
            $cycle = Invoke-SanitizedCycle 'sample' $directory
            $record = Append-Sample $directory $cycle
            if ($cycle.resultStatus -eq 'PASSED_READ_ONLY') {
                $authFailures = 0
                Write-Heartbeat $directory 'RUNNING' 'READ_ONLY_SAMPLE_ACCEPTED' $record.sequence
                break
            }

            $reason = [string]$cycle.reasonCode
            Write-Heartbeat $directory 'DEGRADED' $reason $record.sequence $authFailures
            if ($script:ImmediateStopReasons -contains $reason -or $cycle.resultStatus -eq 'HARD_FAILURE') {
                Stop-FailClosed $directory $reason 'FAILURE_STOPPED' $authFailures
                return
            }
            if ($script:AuthenticationReasons -contains $reason -or [bool]$cycle.authenticationFailure) {
                $authFailures++
                Write-Heartbeat $directory 'DEGRADED' $reason $record.sequence $authFailures
                if ($authFailures -ge [int]$manifest.maxConsecutiveAuthFailures) {
                    Stop-FailClosed $directory 'CONSECUTIVE_AUTHENTICATION_FAILURES' 'FAILURE_STOPPED' $authFailures
                    return
                }
                break
            }
            if ($script:TransientReasons -contains $reason -and $retry -lt [int]$manifest.maxTransientRetries) {
                $retry++
                $continueRetry = Wait-ForCadenceOrControl $directory ([Math]::Min(120, 30 * $retry)) $plannedEndAt
                if (-not $continueRetry) { break }
                continue
            }
            break
        } while ($true)
    }
    Stop-FailClosed $directory 'SEVEN_DAY_ELAPSED_ACCEPTANCE_REQUIRED' 'ELAPSED_PENDING_ACCEPTANCE' $authFailures
}

function Resume-Soak {
    $directory = Get-RunDirectory $RunId
    $manifest = Read-JsonFile (Join-Path $directory 'manifest.json')
    Assert-FixedDetachedWorktree ([string]$manifest.harnessCommit) | Out-Null
    Test-Evidence $directory | Out-Null
    $state = Get-SupervisorState $directory
    if ($state.Running) { throw 'BLOCKED / SOAK_ALREADY_RUNNING' }
    if ((Get-UtcNow) -ge [DateTimeOffset]::Parse([string]$manifest.plannedEndAt)) {
        throw 'BLOCKED / SEVEN_DAY_ELAPSED_ACCEPTANCE_REQUIRED'
    }
    $process = Start-LoopProcess $RunId
    $chain = Get-ChainState $directory
    Write-Heartbeat $directory 'RUNNING' 'SOAK_RESUMED' ($chain.NextSequence - 1)
    return [pscustomobject]@{ decision = 'SOAK_RESUMED'; runId = $RunId; supervisorPid = $process.Id }
}

function Request-Stop {
    param([bool]$Failure)

    $directory = Get-RunDirectory $RunId
    $manifest = Read-JsonFile (Join-Path $directory 'manifest.json')
    Assert-FixedDetachedWorktree ([string]$manifest.harnessCommit) | Out-Null
    $state = Get-SupervisorState $directory
    $kind = if ($Failure) { 'failure' } else { 'graceful' }
    $reason = if ($Failure) { 'OPERATOR_FAILURE_STOP' } else { 'OPERATOR_GRACEFUL_STOP' }
    Write-JsonAtomic (Join-Path $directory 'stop-request.json') ([ordered]@{
        kind = $kind
        reasonCode = $reason
        requestedAt = (Get-UtcNow).ToString('o')
    })
    if (-not $state.Running) {
        Stop-FailClosed $directory $reason $(if ($Failure) { 'FAILURE_STOPPED' } else { 'STOPPED' })
    }
    return [pscustomobject]@{ decision = 'STOP_REQUESTED'; runId = $RunId; kind = $kind; supervisorPid = $state.Pid }
}

function Show-Status {
    $directory = Get-RunDirectory $RunId
    $manifest = Read-JsonFile (Join-Path $directory 'manifest.json')
    $heartbeat = Read-JsonFile (Join-Path $directory 'heartbeat.json')
    $process = Get-SupervisorState $directory
    $chain = Get-ChainState $directory
    return [pscustomobject]@{
        runId         = $RunId
        state         = $heartbeat.state
        reasonCode    = $heartbeat.reasonCode
        processRunning = $process.Running
        supervisorPid = $process.Pid
        sampleCount   = $chain.Count
        startedAt     = $manifest.startedAt
        plannedEndAt  = $manifest.plannedEndAt
        harnessCommit = $manifest.harnessCommit
    }
}

function Cleanup-RunControlFiles {
    $directory = Get-RunDirectory $RunId
    $state = Get-SupervisorState $directory
    if ($state.Running) { throw 'BLOCKED / SOAK_STILL_RUNNING' }
    Test-Evidence $directory | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath $directory -File) {
        if ($file.Name -like '.cycle-*.json' -or $file.Name -in @('stop-request.json', 'supervisor.json')) {
            Remove-Item -LiteralPath $file.FullName -Force
        }
    }
    return [pscustomobject]@{
        decision = 'CONTROL_FILES_CLEANED_EVIDENCE_RETAINED'
        runId = $RunId
        retained = @('manifest.json', 'samples.jsonl', 'failures.jsonl', 'heartbeat.json')
        databaseDropped = $false
    }
}

function Invoke-SelfTest {
    $testRunId = New-RunId
    $directory = Get-RunDirectory $testRunId
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    try {
        $missingCredentialRejected = $false
        try { Assert-RuntimeEnvironment @{} } catch {
            $missingCredentialRejected = $_.Exception.Message -eq 'BLOCKED / API_KEY_REQUIRED'
        }
        if (-not $missingCredentialRejected) { throw 'missing credential preflight was not rejected' }
        [IO.File]::WriteAllText((Join-Path $directory 'samples.jsonl'), '', $script:Utf8NoBom)
        [IO.File]::WriteAllText((Join-Path $directory 'failures.jsonl'), '', $script:Utf8NoBom)
        Write-JsonAtomic (Join-Path $directory 'manifest.json') ([ordered]@{
            runId = $testRunId; harnessCommit = '0' * 40; startingCiRun = '0'
            startedAt = (Get-UtcNow).ToString('o'); plannedEndAt = (Get-UtcNow).AddHours(168).ToString('o')
            durationHours = 168; cadenceSeconds = 900; venue = 'OKX'
            profile = 'gatew-okx-readonly-soak'; environment = 'SELF_TEST_NO_NETWORK'
        })
        Write-Heartbeat $directory 'SELF_TEST' 'NO_PRIVATE_NETWORK_CALLED'
        $cycle = [pscustomobject]@{
            observedAt = (Get-UtcNow).ToString('o'); durationMs = 1; resultStatus = 'PASSED_READ_ONLY'
            reasonCode = 'READ_ONLY_SAMPLE_ACCEPTED'; httpStatusCategory = 'SUCCESS_2XX'
            permissionClassification = 'READ_ONLY_WITH_IP_ALLOWLIST'; killSwitchObservedState = 'DISENGAGED'
            credentialAccessed = $true; networkCalled = $true
            allowedEndpointCategory = 'ACCOUNT_CONFIG_AND_BALANCE_READ'; traceId = 'gatew-soak-self-test'
        }
        $first = Append-Sample $directory $cycle
        $second = Append-Sample $directory $cycle
        $state = Get-ChainState $directory
        if ($first.sequence -ne 1 -or $second.sequence -ne 2 -or $state.Count -ne 2 -or
            $second.previousRecordHash -ne $first.recordHash) {
            throw 'hash-chain self-test failed'
        }
        Test-Evidence $directory | Out-Null
        $beforeResume = @(Get-Content -LiteralPath (Join-Path $directory 'samples.jsonl'))
        $third = Append-Sample $directory $cycle
        $afterResume = @(Get-Content -LiteralPath (Join-Path $directory 'samples.jsonl'))
        if ($third.sequence -ne 3 -or $afterResume.Count -ne 3 -or
            $beforeResume[0] -ne $afterResume[0] -or $beforeResume[1] -ne $afterResume[1]) {
            throw 'resume append-only self-test failed'
        }
        Test-Evidence $directory | Out-Null
        [IO.File]::AppendAllText(
            (Join-Path $directory 'samples.jsonl'),
            (ConvertTo-CompactJson $third) + [Environment]::NewLine,
            $script:Utf8NoBom
        )
        $duplicateRejected = $false
        try { Get-ChainState $directory | Out-Null } catch { $duplicateRejected = $true }
        if (-not $duplicateRejected) { throw 'duplicate sequence was not rejected' }
        return [pscustomobject]@{
            decision = 'PASS / SUPERVISOR_SELF_TEST'
            cases = 10
            hashChain = 'PASS'
            appendOnlySequence = 'PASS'
            resumePreservedExistingSamples = 'PASS'
            duplicateSequenceRejected = 'PASS'
            missingCredentialRejected = 'PASS / API_KEY_REQUIRED'
            finalSummaryNotGenerated = (-not (Test-Path -LiteralPath (Join-Path $directory 'final-summary.json')))
            cleanupReleasedTemporaryDirectory = $true
            noPrivateNetworkCalled = $true
        }
    }
    finally {
        if (Test-Path -LiteralPath $directory) {
            $resolved = [IO.Path]::GetFullPath($directory)
            if ($resolved.StartsWith($script:EvidenceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
                Remove-Item -LiteralPath $resolved -Recurse -Force
            }
        }
    }
}

try {
    $result = switch ($Action) {
        'start' { Start-Soak }
        'status' { Show-Status }
        'resume' { Resume-Soak }
        'stop' { Request-Stop $false }
        'failure-stop' { Request-Stop $true }
        'evidence-verify' { Test-Evidence (Get-RunDirectory $RunId) }
        'cleanup' { Cleanup-RunControlFiles }
        'run-loop' { Run-SoakLoop; [pscustomobject]@{ decision = 'SUPERVISOR_EXITED'; runId = $RunId } }
        'self-test' { Invoke-SelfTest }
    }
    if ($null -ne $result) {
        $result | ConvertTo-Json -Depth 8
    }
}
catch {
    $message = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$') {
        $_.Exception.Message
    }
    else {
        'FAIL / SUPERVISOR_INTERNAL_ERROR'
    }
    $failure = [ordered]@{ decision = $message; runId = $RunId }
    if ($Action -eq 'self-test') {
        $failure.selfTestDetail = $_.Exception.Message
    }
    [pscustomobject]$failure | ConvertTo-Json
    exit 2
}
