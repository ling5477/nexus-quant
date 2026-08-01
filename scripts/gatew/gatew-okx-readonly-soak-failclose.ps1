[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('finalize', 'self-test')]
    [string]$Action,

    [string]$RunId
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)
$script:RunIdPattern = '^gatew-soak-[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$'
$script:CommitPattern = '^[a-f0-9]{40}$'
$script:StateRoot = '/var/lib/nexus-quant/gatew-soak'
$script:ChownPath = '/usr/bin/chown'
$script:ChmodPath = '/usr/bin/chmod'
$script:LnPath = '/usr/bin/ln'
$script:LockRetrySeconds = 25
$script:StopIntentMaxAgeSeconds = 300
$script:RemediationContractPath = Join-Path $PSScriptRoot 'gatew-soak-remediation-contract.psm1'

if (-not (Test-Path -LiteralPath $script:RemediationContractPath -PathType Leaf))
{
    throw 'BLOCKED / REMEDIATION_CONTRACT_MISSING'
}
Import-Module $script:RemediationContractPath -Force

function Test-LinuxPlatform
{
    $platform = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    return $null -ne $platform -and [bool]$platform.Value
}

function Get-UtcNow
{
    return [DateTimeOffset]::UtcNow
}

function Assert-RunId
{
    param([Parameter(Mandatory = $true)][string]$Value)

    if ($Value -cnotmatch $script:RunIdPattern)
    {
        throw 'BLOCKED / RUN_ID_INVALID'
    }
}

function Assert-RootLinux
{
    if (-not (Test-LinuxPlatform) -or [Environment]::UserName -cne 'root')
    {
        throw 'BLOCKED / ROOT_LINUX_REQUIRED'
    }
}

function ConvertFrom-JsonPreservingTimestamps
{
    param([Parameter(Mandatory = $true)][string]$Text)

    $command = Get-Command ConvertFrom-Json -ErrorAction Stop
    if ( $command.Parameters.ContainsKey('DateKind'))
    {
        return ($Text | ConvertFrom-Json -DateKind String)
    }
    return ($Text | ConvertFrom-Json)
}

function Invoke-Native
{
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Arguments,
        [switch]$AllowFailure
    )

    $output = @(& $FilePath @Arguments 2>&1)
    $exitCode = [int]$LASTEXITCODE
    if ($exitCode -ne 0 -and -not $AllowFailure)
    {
        throw 'FAIL / FAILCLOSE_LOCAL_COMMAND_FAILED'
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Lines = @($output) }
}

function Get-RunRoot
{
    param([Parameter(Mandatory = $true)][string]$Value)

    Assert-RunId $Value
    return "$( $script:StateRoot )/$Value"
}

function Get-ControlRoot
{
    param([Parameter(Mandatory = $true)][string]$Value)

    return "$( Get-RunRoot $Value )/control"
}

function Assert-NoSymlink
{
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path))
    {
        throw 'BLOCKED / FAILCLOSE_PATH_INVALID'
    }
    $item = Get-Item -LiteralPath $Path -Force
    if ($null -ne $item.LinkType -or $item.Attributes.ToString() -match 'ReparsePoint')
    {
        throw 'BLOCKED / FAILCLOSE_SYMLINK_FORBIDDEN'
    }
}

function Assert-PathComponentsNoSymlink
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $normalized = [IO.Path]::GetFullPath($Path)
    $root = [IO.Path]::GetPathRoot($normalized)
    if ( [string]::IsNullOrWhiteSpace($root))
    {
        throw 'BLOCKED / FAILCLOSE_PATH_INVALID'
    }
    $current = $root
    foreach ($segment in @(
    $normalized.Substring($root.Length) -split '[\\/]' |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
    ))
    {
        $current = Join-Path $current $segment
        Assert-NoSymlink $current
    }
}

function Assert-ControlPath
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $controlRoot = [IO.Path]::GetFullPath((Get-ControlRoot $Value))
    $normalized = [IO.Path]::GetFullPath($Path)
    $comparison = if (Test-LinuxPlatform)
    {
        [StringComparison]::Ordinal
    }
    else
    {
        [StringComparison]::OrdinalIgnoreCase
    }
    if (-not $normalized.StartsWith(
            $controlRoot + [IO.Path]::DirectorySeparatorChar,
            $comparison
    ))
    {
        throw 'BLOCKED / FAILCLOSE_PATH_INVALID'
    }
    Assert-PathComponentsNoSymlink (Split-Path -Parent $normalized)
}

function Read-OptionalJson
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Path
    )

    Assert-ControlPath $Value $Path
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        return $null
    }
    Assert-NoSymlink $Path
    try
    {
        return ConvertFrom-JsonPreservingTimestamps (Get-Content -LiteralPath $Path -Raw)
    }
    catch
    {
        return $null
    }
}

function Set-OwnerMode
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$OwnerGroup,
        [Parameter(Mandatory = $true)][string]$Mode
    )

    if (-not (Test-LinuxPlatform))
    {
        return
    }
    Invoke-Native $script:ChownPath @($OwnerGroup, '--', $Path) | Out-Null
    Invoke-Native $script:ChmodPath @($Mode, '--', $Path) | Out-Null
}

function Write-BytesFlushed
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][byte[]]$Bytes
    )

    $stream = [IO.FileStream]::new(
            $Path,
            [IO.FileMode]::CreateNew,
            [IO.FileAccess]::Write,
            [IO.FileShare]::None,
            4096,
            [IO.FileOptions]::WriteThrough
    )
    try
    {
        $stream.Write($Bytes, 0, $Bytes.Length)
        $stream.Flush($true)
    }
    finally
    {
        $stream.Dispose()
    }
}

function Write-TextCreateOnce
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text
    )

    Assert-ControlPath $Value $Path
    if (Test-Path -LiteralPath $Path)
    {
        throw 'BLOCKED / IMMUTABLE_CONTROL_EXISTS'
    }
    $parent = Split-Path -Parent $Path
    $temporary = Join-Path $parent ('.create-' + [Guid]::NewGuid().ToString('N'))
    $bytes = $script:Utf8NoBom.GetBytes($Text)
    try
    {
        Write-BytesFlushed $temporary $bytes
        if (Test-LinuxPlatform)
        {
            Set-OwnerMode $temporary 'root:root' '600'
            $linked = Invoke-Native $script:LnPath @('--', $temporary, $Path) -AllowFailure
            if ($linked.ExitCode -ne 0)
            {
                if (Test-Path -LiteralPath $Path)
                {
                    throw 'BLOCKED / IMMUTABLE_CONTROL_EXISTS'
                }
                throw 'FAIL / CREATE_ONCE_COMMIT_FAILED'
            }
            Remove-Item -LiteralPath $temporary -Force
        }
        else
        {
            Move-Item -LiteralPath $temporary -Destination $Path
        }
    }
    finally
    {
        [Array]::Clear($bytes, 0, $bytes.Length)
        if (Test-Path -LiteralPath $temporary)
        {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
}

function Write-JsonCreateOnce
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Record
    )

    Write-TextCreateOnce $Value $Path ($Record | ConvertTo-Json -Depth 20)
}

function Write-JsonReplaceAtomic
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Record
    )

    Assert-ControlPath $Value $Path
    $temporary = Join-Path (Split-Path -Parent $Path) ('.replace-' + [Guid]::NewGuid().ToString('N'))
    $bytes = $script:Utf8NoBom.GetBytes(($Record | ConvertTo-Json -Depth 20))
    try
    {
        Write-BytesFlushed $temporary $bytes
        Set-OwnerMode $temporary 'root:root' '600'
        Move-Item -LiteralPath $temporary -Destination $Path -Force
    }
    finally
    {
        [Array]::Clear($bytes, 0, $bytes.Length)
        if (Test-Path -LiteralPath $temporary)
        {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
}

function Enter-FinalizerLock
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [ValidateRange(0, 30)][int]$RetrySeconds = $script:LockRetrySeconds
    )

    $path = "$( Get-ControlRoot $Value )/failclose.lock"
    Assert-ControlPath $Value $path
    $deadline = (Get-UtcNow).AddSeconds($RetrySeconds)
    do
    {
        try
        {
            $stream = [IO.FileStream]::new(
                    $path,
                    [IO.FileMode]::OpenOrCreate,
                    [IO.FileAccess]::ReadWrite,
                    [IO.FileShare]::None,
                    1,
                    [IO.FileOptions]::WriteThrough
            )
            Set-OwnerMode $path 'root:root' '600'
            return $stream
        }
        catch
        {
            if ((Get-UtcNow) -ge $deadline)
            {
                throw 'FAIL / TERMINAL_AUTHORITY_LOCK_UNAVAILABLE'
            }
            Start-Sleep -Milliseconds 100
        }
    } while ($true)
}

function Assert-ExactFields
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

function Assert-FrozenConfig
{
    param(
        [Parameter(Mandatory = $true)]$Config,
        [Parameter(Mandatory = $true)][string]$Value
    )

    Assert-ExactFields $Config @(
        'schemaVersion', 'runId', 'runMode', 'databaseUrl', 'databaseUser', 'databaseSchema',
        'offlineHeartbeatSeconds', 'releaseId', 'sourceCommit', 'sourceTreeMode',
        'releaseManifestSha256', 'releaseRoot', 'startingCiRun', 'workerUnit', 'failCloseUnit',
        'acceptanceClockStarted', 'acceptanceStartAt', 'plannedAcceptanceAt', 'preparedAt'
    ) 'FAILCLOSE_CONFIG_INVALID'
    if (([string]$Config.schemaVersion -cne 'gatew-soak-frozen-config-v3') -or
            ([string]$Config.runId -cne $Value) -or
            ([string]$Config.runMode -cnotin @(
                'REAL_READONLY_SOAK', 'OFFLINE_ISOLATED_ACCEPTANCE'
            )) -or ([string]$Config.sourceCommit -cnotmatch $script:CommitPattern) -or
            ([string]$Config.sourceTreeMode -cnotin @('CANDIDATE', 'EXACT_COMMIT')) -or
            ([string]$Config.releaseManifestSha256 -cnotmatch '^[a-f0-9]{64}$') -or
            $Config.acceptanceClockStarted -isnot [bool] -or
            [bool]$Config.acceptanceClockStarted -or
            $null -ne $Config.acceptanceStartAt -or $null -ne $Config.plannedAcceptanceAt)
    {
        throw 'FAIL / FAILCLOSE_CONFIG_INVALID'
    }
    return $Config
}

function Assert-AcceptanceClock
{
    param(
        [Parameter(Mandatory = $true)]$Clock,
        [Parameter(Mandatory = $true)][string]$Value
    )

    Assert-ExactFields $Clock @(
        'schemaVersion', 'runId', 'firstValidConfigPassAt', 'firstValidBalancePassAt',
        'firstValidHeartbeatAt', 'freshSshVerificationAt', 'mainPid', 'sameMainPid',
        'heartbeatAdvanced', 'hashChainValid', 'forbiddenEndpointCount',
        'rawResponseCount', 'secretExposureCount',
        'acceptanceStartAt', 'plannedAcceptanceAt', 'acceptanceClockStarted'
    ) 'FAILCLOSE_CLOCK_INVALID'
    $firstHeartbeat = [DateTimeOffset]::MinValue
    $start = [DateTimeOffset]::MinValue
    $planned = [DateTimeOffset]::MinValue
    if ([string]$Clock.schemaVersion -cne 'gatew-soak-acceptance-clock-v2' -or
            [string]$Clock.runId -cne $Value -or [long]$Clock.mainPid -le 0 -or
            $Clock.sameMainPid -isnot [bool] -or -not [bool]$Clock.sameMainPid -or
            $Clock.heartbeatAdvanced -isnot [bool] -or -not [bool]$Clock.heartbeatAdvanced -or
            $Clock.hashChainValid -isnot [bool] -or -not [bool]$Clock.hashChainValid -or
            [long]$Clock.forbiddenEndpointCount -ne 0 -or
            [long]$Clock.rawResponseCount -ne 0 -or
            [long]$Clock.secretExposureCount -ne 0 -or
            $Clock.acceptanceClockStarted -isnot [bool] -or
            -not [bool]$Clock.acceptanceClockStarted -or
            -not [DateTimeOffset]::TryParse(
                [string]$Clock.firstValidHeartbeatAt, [ref]$firstHeartbeat
            ) -or
            -not [DateTimeOffset]::TryParse([string]$Clock.acceptanceStartAt, [ref]$start) -or
            -not [DateTimeOffset]::TryParse([string]$Clock.plannedAcceptanceAt, [ref]$planned) -or
            $firstHeartbeat.Offset -ne [TimeSpan]::Zero -or
            $start.Offset -ne [TimeSpan]::Zero -or $planned.Offset -ne [TimeSpan]::Zero -or
            $start -ne $firstHeartbeat -or
            ($planned - $start).TotalSeconds -ne 604800.0)
    {
        throw 'FAIL / FAILCLOSE_CLOCK_INVALID'
    }
    return [pscustomobject]@{
        acceptanceStartAt = [string]$Clock.acceptanceStartAt
        plannedAcceptanceAt = [string]$Clock.plannedAcceptanceAt
        planned = $planned
    }
}

function Assert-ExitFact
{
    param(
        [Parameter(Mandatory = $true)]$ExitFact,
        [Parameter(Mandatory = $true)][string]$Value
    )

    Assert-ExactFields $ExitFact @(
        'schemaVersion', 'runId', 'serviceResult', 'exitCode', 'exitStatus',
        'lastKnownMainPid', 'stopClassification', 'stopIntentChecksum', 'recordedAt'
    ) 'EXIT_FACT_INVALID'
    $recordedAt = [DateTimeOffset]::MinValue
    if (([string]$ExitFact.schemaVersion -cne 'gatew-soak-exit-fact-v2') -or
            ([string]$ExitFact.runId -cne $Value) -or
            ([string]$ExitFact.serviceResult -cnotmatch '^[A-Za-z0-9_-]{0,64}$') -or
            ([string]$ExitFact.exitCode -cnotmatch '^[A-Za-z0-9_-]{0,32}$') -or
            ([string]$ExitFact.exitStatus -cnotmatch '^[A-Za-z0-9_-]{0,32}$') -or
            [long]$ExitFact.lastKnownMainPid -lt 0 -or
            ([string]$ExitFact.stopClassification -cnotin @(
                'AUTHORIZED_CONTROLLED_STOP', 'UNAUTHORIZED_OR_UNKNOWN_STOP'
            )) -or -not [DateTimeOffset]::TryParse(
                [string]$ExitFact.recordedAt, [ref]$recordedAt
            ) -or $recordedAt.Offset -ne [TimeSpan]::Zero)
    {
        throw 'FAIL / EXIT_FACT_INVALID'
    }
    return $recordedAt
}

function Test-ValidStopIntent
{
    param(
        [AllowNull()]$Intent,
        [Parameter(Mandatory = $true)]$ExitFact,
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$ReleaseCommit
    )

    if ($null -eq $Intent)
    {
        return $false
    }
    try
    {
        Assert-GateWStopIntentRecord $Intent | Out-Null
        $requestedAt = [DateTimeOffset]::Parse([string]$Intent.requestedAt)
        $recordedAt = [DateTimeOffset]::Parse([string]$ExitFact.recordedAt)
        $intentAge = $recordedAt - $requestedAt
        return [string]$Intent.runId -ceq $Value -and
                [string]$Intent.releaseCommit -ceq $ReleaseCommit -and
                [string]$ExitFact.stopClassification -ceq 'AUTHORIZED_CONTROLLED_STOP' -and
                [string]$ExitFact.stopIntentChecksum -ceq [string]$Intent.checksum -and
                $intentAge.TotalSeconds -ge 0 -and
                $intentAge.TotalSeconds -le $script:StopIntentMaxAgeSeconds
    }
    catch
    {
        return $false
    }
}

function Test-SignalStop
{
    param([Parameter(Mandatory = $true)]$ExitFact)

    return ([string]$ExitFact.exitStatus -cin @('TERM', 'KILL', 'INT', 'HUP', 'ABRT')) -or
            ([string]$ExitFact.serviceResult -cin @('signal', 'timeout', 'watchdog'))
}

function Test-CompletionMarker
{
    param(
        [AllowNull()]$Marker,
        [Parameter(Mandatory = $true)][string]$Value,
        [AllowNull()]$ClockProjection
    )

    if ($null -eq $Marker -or $null -eq $ClockProjection)
    {
        return $false
    }
    try
    {
        Assert-ExactFields $Marker @(
            'schemaVersion', 'runId', 'lastSuccessfulCycleSequence',
            'lastHeartbeatSequence', 'completedAt'
        ) 'COMPLETION_MARKER_INVALID'
        $completedAt = [DateTimeOffset]::MinValue
        return [string]$Marker.schemaVersion -ceq 'gatew-soak-completion-marker-v1' -and
                [string]$Marker.runId -ceq $Value -and
                [long]$Marker.lastSuccessfulCycleSequence -gt 0 -and
                [long]$Marker.lastHeartbeatSequence -ge
                        [long]$Marker.lastSuccessfulCycleSequence -and
                [DateTimeOffset]::TryParse([string]$Marker.completedAt, [ref]$completedAt) -and
                $completedAt.Offset -eq [TimeSpan]::Zero -and
                $completedAt -ge [DateTimeOffset]$ClockProjection.planned
    }
    catch
    {
        return $false
    }
}

function New-AutomaticRejectionTerminal
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [AllowNull()]$Config,
        [AllowNull()]$Clock,
        [AllowNull()]$ExitFact,
        [AllowNull()]$StopIntent,
        [AllowNull()]$CompletionMarker,
        [Parameter(Mandatory = $true)][string]$FinalizedAt
    )

    $releaseCommit = 'UNKNOWN'
    $configValid = $false
    if ($null -ne $Config)
    {
        try
        {
            Assert-FrozenConfig $Config $Value | Out-Null
            $releaseCommit = [string]$Config.sourceCommit
            $configValid = $true
        }
        catch
        {
            $configValid = $false
        }
    }

    $clockProjection = $null
    $clockInvalid = $false
    if ($null -ne $Clock)
    {
        try
        {
            $clockProjection = Assert-AcceptanceClock $Clock $Value
        }
        catch
        {
            $clockInvalid = $true
        }
    }

    $exitRecordedAt = $null
    $exitValid = $false
    if ($null -ne $ExitFact)
    {
        try
        {
            $exitRecordedAt = Assert-ExitFact $ExitFact $Value
            $exitValid = $true
        }
        catch
        {
            $exitValid = $false
        }
    }

    $acceptanceResult = 'REJECTED_FINALIZER_ERROR'
    $terminalReasonCode = 'FAILCLOSE_CONFIG_INVALID'
    $stopClassification = 'NOT_PROVEN'
    $stopIntentChecksum = 'NOT_PRESENT'
    if ($configValid -and $clockInvalid)
    {
        $terminalReasonCode = 'FAILCLOSE_CLOCK_INVALID'
    }
    elseif ($configValid -and -not $exitValid)
    {
        $acceptanceResult = 'REJECTED_UNAUTHORIZED_OR_UNKNOWN_STOP'
        $terminalReasonCode = 'EXIT_FACT_MISSING_OR_INVALID'
        $stopClassification = 'UNAUTHORIZED_OR_UNKNOWN_STOP'
    }
    elseif ($configValid -and $exitValid)
    {
        $intentValid = Test-ValidStopIntent `
            $StopIntent $ExitFact $Value $releaseCommit
        if ($intentValid)
        {
            $stopClassification = 'AUTHORIZED_CONTROLLED_STOP'
            $stopIntentChecksum = [string]$StopIntent.checksum
            if ($null -eq $clockProjection -or
                    [DateTimeOffset]$exitRecordedAt -lt [DateTimeOffset]$clockProjection.planned)
            {
                $acceptanceResult = 'REJECTED_INSUFFICIENT_DURATION'
                $terminalReasonCode = 'AUTHORIZED_STOP_BEFORE_ACCEPTANCE'
            }
            else
            {
                $acceptanceResult = 'REJECTED_RUNTIME_EXIT'
                $terminalReasonCode = 'AUTHORIZED_STOP_WITHOUT_ACCEPTED_TERMINAL'
            }
        }
        elseif (Test-SignalStop $ExitFact)
        {
            $acceptanceResult = 'REJECTED_UNAUTHORIZED_OR_UNKNOWN_STOP'
            $terminalReasonCode = 'UNAUTHORIZED_OR_UNKNOWN_STOP'
            $stopClassification = 'UNAUTHORIZED_OR_UNKNOWN_STOP'
            $stopIntentChecksum = if ($null -eq $StopIntent)
            {
                'NOT_PRESENT'
            }
            else
            {
                'INVALID'
            }
        }
        else
        {
            $acceptanceResult = 'REJECTED_RUNTIME_EXIT'
            $terminalReasonCode = if (Test-CompletionMarker $CompletionMarker $Value $clockProjection)
            {
                'WORKER_EXIT_AFTER_COMPLETION_WITHOUT_FINALIZER'
            }
            else
            {
                'WORKER_EXIT_WITHOUT_EXPLICIT_ACCEPTANCE'
            }
            $stopClassification = 'NOT_PROVEN'
            $stopIntentChecksum = if ($null -eq $StopIntent)
            {
                'NOT_PRESENT'
            }
            else
            {
                'INVALID'
            }
        }
    }

    return New-GateWTerminalRecord `
        -RunId $Value `
        -ReleaseCommit $releaseCommit `
        -AcceptanceResult $acceptanceResult `
        -TerminalReasonCode $terminalReasonCode `
        -StopClassification $stopClassification `
        -AcceptanceStartAt $( if ($null -eq $clockProjection)
    {
        $null
    }
    else
    {
        [string]$clockProjection.acceptanceStartAt
    } ) `
        -PlannedAcceptanceAt $( if ($null -eq $clockProjection)
    {
        $null
    }
    else
    {
        [string]$clockProjection.plannedAcceptanceAt
    } ) `
        -AcceptanceVerificationChecksum 'NOT_APPLICABLE' `
        -EvidenceManifestSha256 'NOT_VERIFIED' `
        -EvidenceFinalChainHash 'NOT_VERIFIED' `
        -StopIntentChecksum $stopIntentChecksum `
        -FinalizerKind 'AUTOMATIC_FAIL_CLOSE' `
        -FinalizedAt $FinalizedAt
}

function Update-RejectedLifecycleBestEffort
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)]$Terminal
    )

    $path = "$( Get-ControlRoot $Value )/lifecycle.json"
    $current = Read-OptionalJson $Value $path
    if ($null -eq $current)
    {
        return 'NOT_PRESENT'
    }
    $state = [string]$current.state
    if ($state -cin @('FAILURE_STOPPED', 'OPERATOR_STOPPED', 'COMPLETED', 'BLOCKED'))
    {
        return 'NO_CHANGE'
    }
    $sequence = [long]$current.stateSequence
    if ($state -cin @('STARTING', 'RUNNING'))
    {
        $sequence++
        Write-JsonReplaceAtomic $Value $path ([ordered]@{
            schemaVersion = 'gatew-soak-lifecycle-v1'
            runId = $Value
            state = 'FAILURE_STOPPING'
            stateSequence = $sequence
            reasonCode = [string]$Terminal.terminalReasonCode
            observedAt = (Get-UtcNow).ToString('o')
        })
        Set-OwnerMode $path 'root:root' '600'
        $state = 'FAILURE_STOPPING'
    }
    $nextState = if ($state -ceq 'OPERATOR_STOPPING' -and
            [string]$Terminal.stopClassification -ceq 'AUTHORIZED_CONTROLLED_STOP')
    {
        'OPERATOR_STOPPED'
    }
    elseif ($state -cin @('STARTING', 'RUNNING', 'FAILURE_STOPPING'))
    {
        'FAILURE_STOPPED'
    }
    else
    {
        'BLOCKED'
    }
    Write-JsonReplaceAtomic $Value $path ([ordered]@{
        schemaVersion = 'gatew-soak-lifecycle-v1'
        runId = $Value
        state = $nextState
        stateSequence = $sequence + 1
        reasonCode = [string]$Terminal.terminalReasonCode
        observedAt = (Get-UtcNow).ToString('o')
    })
    Set-OwnerMode $path 'root:root' '600'
    return 'UPDATED'
}

function Finalize-FormalRun
{
    Assert-RootLinux
    Assert-RunId $RunId
    $controlRoot = Get-ControlRoot $RunId
    if (-not (Test-Path -LiteralPath $controlRoot -PathType Container))
    {
        throw 'BLOCKED / CONTROL_DIRECTORY_INVALID'
    }
    Assert-PathComponentsNoSymlink $controlRoot
    $started = [Diagnostics.Stopwatch]::StartNew()
    $lock = Enter-FinalizerLock $RunId
    try
    {
        $terminalPath = "$controlRoot/terminal-status.json"
        $existing = Read-OptionalJson $RunId $terminalPath
        if ($null -ne $existing)
        {
            Assert-GateWTerminalRecord $existing | Out-Null
            $lifecycleUpdate = if ([string]$existing.acceptanceResult -ceq
                    'ACCEPTED_168H_READONLY_SOAK')
            {
                'NOT_UPDATED_ACCEPTANCE_AUTHORITY_EXTERNAL'
            }
            else
            {
                try
                {
                    Update-RejectedLifecycleBestEffort $RunId $existing
                }
                catch
                {
                    'NOT_UPDATED_FAIL_CLOSED'
                }
            }
            return [pscustomobject][ordered]@{
                decision = 'NO_CHANGE / TERMINAL_ALREADY_FINALIZED'
                runId = $RunId
                acceptanceResult = [string]$existing.acceptanceResult
                terminalChecksum = [string]$existing.checksum
                lifecycleUpdate = $lifecycleUpdate
                elapsedMilliseconds = [long]$started.ElapsedMilliseconds
                heavyVerifierCalled = $false
                credentialAccessed = $false
                networkCalled = $false
                okxCalled = $false
                attempt10Created = $false
            }
        }
        $terminal = New-AutomaticRejectionTerminal `
            -Value $RunId `
            -Config (Read-OptionalJson $RunId "$controlRoot/frozen-config.json") `
            -Clock (Read-OptionalJson $RunId "$controlRoot/acceptance-clock-start.json") `
            -ExitFact (Read-OptionalJson $RunId "$controlRoot/exit-fact.json") `
            -StopIntent (Read-OptionalJson $RunId "$controlRoot/stop-intent.json") `
            -CompletionMarker $null `
            -FinalizedAt ((Get-UtcNow).ToString('o'))
        Assert-GateWTerminalRecord $terminal | Out-Null
        Write-JsonCreateOnce $RunId $terminalPath $terminal
        Set-OwnerMode $terminalPath 'root:root' '600'
        $lifecycleUpdate = try
        {
            Update-RejectedLifecycleBestEffort $RunId $terminal
        }
        catch
        {
            'NOT_UPDATED_FAIL_CLOSED'
        }
        return [pscustomobject][ordered]@{
            decision = 'PASS / AUTOMATIC_FAIL_CLOSE_FINALIZED'
            runId = $RunId
            acceptanceResult = [string]$terminal.acceptanceResult
            terminalReasonCode = [string]$terminal.terminalReasonCode
            stopClassification = [string]$terminal.stopClassification
            terminalChecksum = [string]$terminal.checksum
            lifecycleUpdate = $lifecycleUpdate
            elapsedMilliseconds = [long]$started.ElapsedMilliseconds
            heavyVerifierCalled = $false
            credentialAccessed = $false
            networkCalled = $false
            okxCalled = $false
            attempt10Created = $false
        }
    }
    finally
    {
        $started.Stop()
        if ($null -ne $lock)
        {
            $lock.Dispose()
        }
    }
}

function New-SelfTestConfig
{
    param([Parameter(Mandatory = $true)][string]$Value)

    return [pscustomobject][ordered]@{
        schemaVersion = 'gatew-soak-frozen-config-v3'
        runId = $Value
        runMode = 'REAL_READONLY_SOAK'
        databaseUrl = 'REDACTED'
        databaseUser = 'REDACTED'
        databaseSchema = 'public'
        offlineHeartbeatSeconds = 5
        releaseId = '1111111111111111111111111111111111111111'
        sourceCommit = '1111111111111111111111111111111111111111'
        sourceTreeMode = 'EXACT_COMMIT'
        releaseManifestSha256 = ('2' * 64)
        releaseRoot = '/opt/nexus-quant/current'
        startingCiRun = '1'
        workerUnit = "nq-gatew-soak@$Value.service"
        failCloseUnit = "nq-gatew-soak-failclose@$Value.service"
        acceptanceClockStarted = $false
        acceptanceStartAt = $null
        plannedAcceptanceAt = $null
        preparedAt = '2026-07-22T11:00:00.0000000Z'
    }
}

function New-SelfTestClock
{
    param([Parameter(Mandatory = $true)][string]$Value)

    return [pscustomobject][ordered]@{
        schemaVersion = 'gatew-soak-acceptance-clock-v2'
        runId = $Value
        firstValidConfigPassAt = '2026-07-22T11:19:50.0000000Z'
        firstValidBalancePassAt = '2026-07-22T11:19:51.0000000Z'
        firstValidHeartbeatAt = '2026-07-22T11:19:52.0000000Z'
        freshSshVerificationAt = '2026-07-22T11:19:59.5201964Z'
        mainPid = 4074358L
        sameMainPid = $true
        heartbeatAdvanced = $true
        hashChainValid = $true
        forbiddenEndpointCount = 0
        rawResponseCount = 0
        secretExposureCount = 0
        acceptanceStartAt = '2026-07-22T11:19:52.0000000Z'
        plannedAcceptanceAt = '2026-07-29T11:19:52.0000000Z'
        acceptanceClockStarted = $true
    }
}

function New-SelfTestExitFact
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [string]$ExitStatus = 'TERM',
        [string]$ServiceResult = 'success',
        [string]$StopClassification = 'UNAUTHORIZED_OR_UNKNOWN_STOP',
        [string]$StopIntentChecksum = 'NOT_PRESENT',
        [string]$RecordedAt = '2026-07-27T22:25:46.8916254Z'
    )

    return [pscustomobject][ordered]@{
        schemaVersion = 'gatew-soak-exit-fact-v2'
        runId = $Value
        serviceResult = $ServiceResult
        exitCode = if ($ExitStatus -ceq '0')
        {
            'exited'
        }
        else
        {
            'killed'
        }
        exitStatus = $ExitStatus
        lastKnownMainPid = 0L
        stopClassification = $StopClassification
        stopIntentChecksum = $StopIntentChecksum
        recordedAt = $RecordedAt
    }
}

function Invoke-FailCloseSelfTest
{
    $run = 'gatew-soak-20260722T111144Z-ac00f878'
    $config = New-SelfTestConfig $run
    $clock = New-SelfTestClock $run
    $caseCount = 0
    $started = [Diagnostics.Stopwatch]::StartNew()

    $unknown = New-AutomaticRejectionTerminal `
        $run $config $clock (New-SelfTestExitFact $run) $null $null `
        '2026-07-27T22:25:47.0000000Z'
    if ([string]$unknown.acceptanceResult -cne 'REJECTED_UNAUTHORIZED_OR_UNKNOWN_STOP' -or
            [string]$unknown.stopClassification -cne 'UNAUTHORIZED_OR_UNKNOWN_STOP')
    {
        throw 'unknown stop self-test failed'
    }
    Assert-GateWTerminalRecord $unknown | Out-Null
    $caseCount++

    $requestedAt = '2026-07-27T22:25:40.0000000Z'
    $intent = New-GateWStopIntentRecord `
        $run 'gatew-stop-20260727T222540Z-0123abcd' $requestedAt 1000L `
        'OPERATOR_STOP_REQUESTED' ([string]$config.sourceCommit)
    $authorizedExit = New-SelfTestExitFact `
        $run 'TERM' 'success' 'AUTHORIZED_CONTROLLED_STOP' ([string]$intent.checksum)
    $authorized = New-AutomaticRejectionTerminal `
        $run $config $clock $authorizedExit $intent $null `
        '2026-07-27T22:25:47.0000000Z'
    if ([string]$authorized.acceptanceResult -cne 'REJECTED_INSUFFICIENT_DURATION' -or
            [string]$authorized.stopClassification -cne 'AUTHORIZED_CONTROLLED_STOP')
    {
        throw 'authorized stop self-test failed'
    }
    $staleAuthorizedExit = New-SelfTestExitFact `
        $run 'TERM' 'success' 'AUTHORIZED_CONTROLLED_STOP' ([string]$intent.checksum) `
        '2026-07-27T22:35:40.0000000Z'
    $staleIntent = New-AutomaticRejectionTerminal `
        $run $config $clock $staleAuthorizedExit $intent $null `
        '2026-07-27T22:35:41.0000000Z'
    if ([string]$staleIntent.acceptanceResult -cne
            'REJECTED_UNAUTHORIZED_OR_UNKNOWN_STOP' -or
            [string]$staleIntent.stopClassification -cne 'UNAUTHORIZED_OR_UNKNOWN_STOP')
    {
        throw 'stale stop intent self-test failed'
    }
    $caseCount++

    $normalExit = New-SelfTestExitFact `
        $run '0' 'success' 'UNAUTHORIZED_OR_UNKNOWN_STOP' 'NOT_PRESENT' `
        '2026-07-29T11:20:01.0000000Z'
    $runtime = New-AutomaticRejectionTerminal `
        $run $config $clock $normalExit $null $null `
        '2026-07-29T11:20:02.0000000Z'
    if ([string]$runtime.acceptanceResult -cne 'REJECTED_RUNTIME_EXIT')
    {
        throw 'runtime exit self-test failed'
    }
    $caseCount++

    $invalidConfig = New-AutomaticRejectionTerminal `
        $run $null $clock $normalExit $null $null `
        '2026-07-29T11:20:02.0000000Z'
    if ([string]$invalidConfig.acceptanceResult -cne 'REJECTED_FINALIZER_ERROR')
    {
        throw 'invalid config self-test failed'
    }
    $caseCount++

    $temporary = Join-Path ([IO.Path]::GetTempPath()) (
    'nq-gatew-failclose-' + [Guid]::NewGuid().ToString('N')
    )
    $previousStateRoot = $script:StateRoot
    try
    {
        [IO.Directory]::CreateDirectory($temporary) | Out-Null
        $script:StateRoot = $temporary
        $control = Get-ControlRoot $run
        [IO.Directory]::CreateDirectory($control) | Out-Null
        $terminalPath = "$control/terminal-status.json"
        Write-JsonCreateOnce $run $terminalPath $unknown
        $reloaded = Read-OptionalJson $run $terminalPath
        Assert-GateWTerminalRecord $reloaded | Out-Null
        $caseCount++
        $secondRejected = $false
        try
        {
            Write-JsonCreateOnce $run $terminalPath $authorized
        }
        catch
        {
            $secondRejected = $_.Exception.Message -ceq 'BLOCKED / IMMUTABLE_CONTROL_EXISTS'
        }
        if (-not $secondRejected)
        {
            throw 'terminal create-once self-test failed'
        }
        $caseCount++

        $firstLock = Enter-FinalizerLock $run 0
        try
        {
            $secondRejected = $false
            try
            {
                $secondLock = Enter-FinalizerLock $run 0
                $secondLock.Dispose()
            }
            catch
            {
                $secondRejected = $_.Exception.Message -ceq
                        'FAIL / TERMINAL_AUTHORITY_LOCK_UNAVAILABLE'
            }
            if (-not $secondRejected)
            {
                throw 'terminal lock self-test failed'
            }
        }
        finally
        {
            $firstLock.Dispose()
        }
        $caseCount++
    }
    finally
    {
        $script:StateRoot = $previousStateRoot
        $resolvedTemporary = [IO.Path]::GetFullPath($temporary)
        $resolvedSystemTemporary = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if (Test-Path -LiteralPath $resolvedTemporary)
        {
            if (-not $resolvedTemporary.StartsWith(
                    $resolvedSystemTemporary,
                    [StringComparison]::OrdinalIgnoreCase
            ))
            {
                throw 'temporary cleanup boundary self-test failed'
            }
            Remove-Item -LiteralPath $resolvedTemporary -Recurse -Force
        }
    }
    $started.Stop()
    if ($started.Elapsed.TotalSeconds -ge 30)
    {
        throw 'bounded execution self-test failed'
    }
    $caseCount++

    return [pscustomobject][ordered]@{
        decision = 'PASS / LIGHTWEIGHT_FAILCLOSE_SELF_TEST'
        cases = $caseCount
        elapsedMilliseconds = [long]$started.ElapsedMilliseconds
        boundedUnder30Seconds = $true
        terminalCreateOnce = 'PASS'
        terminalLock = 'PASS'
        heavyVerifierCalled = $false
        credentialAccessed = $false
        networkCalled = $false
        okxCalled = $false
        attempt10Created = $false
    }
}

try
{
    $result = switch ($Action)
    {
        'finalize' {
            Finalize-FormalRun
        }
        'self-test' {
            Invoke-FailCloseSelfTest
        }
    }
    if ($null -ne $result)
    {
        $result | ConvertTo-Json -Depth 12
    }
}
catch
{
    $message = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$')
    {
        $_.Exception.Message
    }
    else
    {
        'FAIL / LIGHTWEIGHT_FAILCLOSE_INTERNAL_ERROR'
    }
    $failure = [ordered]@{ decision = $message; runId = $RunId }
    if ($Action -eq 'self-test')
    {
        $failure.selfTestDetail = $_.Exception.Message
    }
    [pscustomobject]$failure | ConvertTo-Json
    exit 2
}
