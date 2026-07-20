[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
            'start', 'status', 'resume', 'stop', 'failure-stop', 'evidence-verify', 'cleanup', 'run-loop', 'self-test'
    )]
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
$configuredReleaseRoot = [Environment]::GetEnvironmentVariable('NQ_GATEW_RELEASE_ROOT', 'Process')
if ( [string]::IsNullOrWhiteSpace($configuredReleaseRoot))
{
    $script:ReleaseRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
}
else
{
    $script:ReleaseRoot = [IO.Path]::GetFullPath($configuredReleaseRoot)
}
$script:WorkspaceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$script:EvidenceRoot = [IO.Path]::GetFullPath((Join-Path $script:WorkspaceRoot 'target\gatew-okx-readonly-soak'))
$script:FormalStateRoot = '/var/lib/nexus-quant/gatew-soak'
$script:FormalRuntimeRoot = '/run/nexus-quant/gatew-soak'
$script:LinuxJavaPath = '/usr/bin/java'
$script:ReleaseVerifierName = 'verify-gatew-release.ps1'
$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)
$script:GenesisHash = '0' * 64
$script:LauncherSchemaVersion = 'gatew-soak-launcher-v2'
$script:EvidenceSchemaV1 = 'gatew-soak-evidence-v1'
$script:EvidenceSchemaV2 = 'gatew-soak-evidence-v2'
$script:LinuxReadlinkPath = '/usr/bin/readlink'
$script:LauncherFields = @(
    'schemaVersion', 'cycleId', 'observedAt', 'durationMs', 'resultStatus', 'reasonCode',
    'httpStatusCategory', 'permissionClassification', 'killSwitchObservedState',
    'credentialAccessed', 'networkCalled', 'allowedEndpointCategory',
    'accountConfigProbeStatus', 'balanceProbeStatus', 'traceId'
)
$script:SupervisorCycleFields = @($script:LauncherFields + 'realCycleOutcomeProven')
$script:SampleFieldsV1 = @(
    'sequence', 'observedAt', 'durationMs', 'resultStatus', 'reasonCode', 'httpStatusCategory',
    'permissionClassification', 'killSwitchObservedState', 'credentialAccessed', 'networkCalled',
    'allowedEndpointCategory', 'traceId', 'previousRecordHash', 'recordHash'
)
$script:SampleFieldsV2 = @(
    'schemaVersion', 'sequence', 'cycleId', 'observedAt', 'durationMs', 'resultStatus', 'reasonCode',
    'httpStatusCategory', 'permissionClassification', 'killSwitchObservedState',
    'credentialAccessed', 'networkCalled', 'allowedEndpointCategory',
    'accountConfigProbeStatus', 'balanceProbeStatus', 'realCycleOutcomeProven',
    'traceId', 'previousRecordHash', 'recordHash'
)
$script:TransientReasons = @(
    'NETWORK_IO_ERROR', 'NETWORK_TIMEOUT', 'HTTP_RATE_LIMITED', 'HTTP_SERVER_ERROR',
    'HTTP_UNEXPECTED_STATUS', 'OKX_BUSINESS_REJECTED',
    'NETWORK_FAILURE', 'TIMEOUT', 'RATE_LIMITED', 'HTTP_ERROR', 'OKX_PROVIDER_ERROR'
)
$script:AuthenticationReasons = @(
    'HTTP_UNAUTHORIZED', 'HTTP_FORBIDDEN', 'OKX_AUTHENTICATION_FAILED', 'OKX_SIGNATURE_INVALID',
    'OKX_TIMESTAMP_INVALID', 'AUTHENTICATION_FAILURE', 'SIGNATURE_FAILURE'
)
$script:ImmediateStopReasons = @(
    'FORBIDDEN_ENDPOINT_ATTEMPTED',
    'IP_ALLOWLIST_FAILED',
    'OKX_IP_NOT_ALLOWED',
    'OKX_PERMISSION_DENIED',
    'PERMISSION_BLOCKED',
    'KILL_SWITCH_CHANGED_DURING_SAMPLE',
    'SOAK_DATABASE_CONTAINS_BUSINESS_DATA',
    'SOAK_DATABASE_NOT_LOCAL'
)
$script:RuntimeEnvironmentNames = @(
    'NQ_GATEW_RUN_MODE',
    'SPRING_PROFILES_ACTIVE', 'NQ_GATEW_OKX_READONLY_SOAK_ENABLED', 'CI', 'NQ_NO_OUTBOUND',
    'NQ_LIVE_ENABLED', 'NQ_REAL_ORDER_SUBMISSION_ENABLED', 'NQ_TRANSFER_ENABLED', 'NQ_WITHDRAW_ENABLED',
    'NQ_AI_ENABLED', 'NQ_DH_RUNTIME_ENABLED', 'NQ_REAL_PROVIDER_ENABLED', 'NQ_REAL_CLIENT_ENABLED',
    'NQ_REAL_EXCHANGE_ENABLED', 'NQ_GATEW_SOAK_DB_URL', 'NQ_GATEW_SOAK_DB_USER',
    'NQ_GATEW_SOAK_DB_PASSWORD', 'NQ_ACCOUNT_CREDENTIALS_MASTER_KEY', 'NQ_GATEW_SOAK_OWNER_ID',
    'NQ_GATEW_SOAK_ACCOUNT_ID', 'NQ_GATEW_SOAK_CURRENCIES', 'NQ_OKX_API_KEY', 'NQ_OKX_API_SECRET',
    'NQ_OKX_API_PASSPHRASE', 'NQ_OKX_REAL_API_KEY', 'NQ_OKX_REAL_API_SECRET', 'NQ_OKX_REAL_API_PASSPHRASE'
)
$script:DirectOkxEnvironmentNames = @(
    'NQ_OKX_API_KEY', 'NQ_OKX_API_SECRET', 'NQ_OKX_API_PASSPHRASE',
    'NQ_OKX_REAL_API_KEY', 'NQ_OKX_REAL_API_SECRET', 'NQ_OKX_REAL_API_PASSPHRASE'
)
$script:MaterializedRuntimeEnvironmentNames = @(
$script:RuntimeEnvironmentNames | Where-Object { $script:DirectOkxEnvironmentNames -notcontains $_ }
)

function Get-UtcNow
{
    return [DateTimeOffset]::UtcNow
}

function Test-LinuxPlatform
{
    # Windows PowerShell 5.1 has no $IsLinux automatic variable.
    $platform = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    return $null -ne $platform -and [bool]$platform.Value
}

function Test-FormalSystemdWorker
{
    return (Test-LinuxPlatform) -and
            [Environment]::GetEnvironmentVariable('NQ_GATEW_FORMAL_SYSTEMD', 'Process') -eq 'true'
}

function ConvertTo-TrimmedNativeOutput
{
    param([AllowNull()][object[]]$Value)

    return (($Value -join [Environment]::NewLine).Trim())
}

function ConvertTo-CanonicalUtcTimestamp
{
    param([Parameter(Mandatory = $true)]$Value)

    # PowerShell 7 将 ISO-8601 JSON 字符串自动解析为 DateTime；统一回 UTC round-trip 格式，保证跨引擎 hash 一致。
    # Windows PowerShell 5.1 + StrictMode 对首次 value-type cast 需要显式的本地变量初始化。
    $parsedObservedAt = [DateTimeOffset]::MinValue
    $parsedObservedAt = [DateTimeOffset]$Value
    $utcObservedAt = $parsedObservedAt.UtcDateTime
    return $utcObservedAt.ToString('o')
}

function Get-RuntimeEnvironmentSnapshot
{
    $snapshot = @{ }
    foreach ($name in $script:RuntimeEnvironmentNames)
    {
        $snapshot[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
    }
    return $snapshot
}

function Assert-RuntimeEnvironment
{
    param([Parameter(Mandatory = $true)][hashtable]$Environment)

    if ([string]$Environment.NQ_GATEW_RUN_MODE -ne 'REAL_READONLY_SOAK')
    {
        throw 'BLOCKED / SOAK_RUN_MODE_INVALID'
    }

    foreach ($name in $script:DirectOkxEnvironmentNames)
    {
        if (-not [string]::IsNullOrWhiteSpace([string]$Environment[$name]))
        {
            throw 'BLOCKED / DIRECT_OKX_CREDENTIAL_INPUT_FORBIDDEN'
        }
    }
    foreach ($name in @('NQ_ACCOUNT_CREDENTIALS_MASTER_KEY', 'NQ_GATEW_SOAK_OWNER_ID', 'NQ_GATEW_SOAK_ACCOUNT_ID'))
    {
        if ( [string]::IsNullOrWhiteSpace([string]$Environment[$name]))
        {
            throw 'BLOCKED / API_KEY_REQUIRED'
        }
    }
    foreach ($name in @('NQ_GATEW_SOAK_DB_URL', 'NQ_GATEW_SOAK_DB_USER', 'NQ_GATEW_SOAK_DB_PASSWORD'))
    {
        if ( [string]::IsNullOrWhiteSpace([string]$Environment[$name]))
        {
            throw 'BLOCKED / SOAK_DATABASE_CONFIG_REQUIRED'
        }
    }
    foreach ($name in $script:MaterializedRuntimeEnvironmentNames)
    {
        $value = [string]$Environment[$name]
        # systemd EnvironmentFile does not expand variables; require materialized literal values.
        if (-not ([string]::IsNullOrWhiteSpace($value)))
        {
            if ($value -match '^\$\{?[A-Za-z_][A-Za-z0-9_]*\}?$')
            {
                throw 'BLOCKED / RUNTIME_ENVIRONMENT_NOT_LITERAL'
            }
        }
    }
    if ([string]$Environment.SPRING_PROFILES_ACTIVE -ne 'gatew-okx-readonly-soak')
    {
        throw 'BLOCKED / SOAK_PROFILE_REQUIRED'
    }
    if ([string]$Environment.NQ_GATEW_OKX_READONLY_SOAK_ENABLED -ne 'true')
    {
        throw 'BLOCKED / SOAK_FEATURE_FLAG_REQUIRED'
    }
    if ([string]$Environment.CI -eq 'true' -or [string]$Environment.NQ_NO_OUTBOUND -eq 'true')
    {
        throw 'BLOCKED / SOAK_OUTBOUND_FORBIDDEN_IN_CI'
    }
    foreach ($name in @(
        'NQ_LIVE_ENABLED', 'NQ_REAL_ORDER_SUBMISSION_ENABLED', 'NQ_TRANSFER_ENABLED', 'NQ_WITHDRAW_ENABLED',
        'NQ_AI_ENABLED', 'NQ_DH_RUNTIME_ENABLED', 'NQ_REAL_PROVIDER_ENABLED', 'NQ_REAL_CLIENT_ENABLED',
        'NQ_REAL_EXCHANGE_ENABLED'
    ))
    {
        if ([string]$Environment[$name] -ne 'false')
        {
            throw "BLOCKED / ${name}_MUST_BE_FALSE"
        }
    }
    if ( [string]::IsNullOrWhiteSpace([string]$Environment.NQ_GATEW_SOAK_CURRENCIES))
    {
        throw 'BLOCKED / SOAK_CURRENCY_ALLOWLIST_REQUIRED'
    }
}

function Get-Sha256Text
{
    param([Parameter(Mandatory = $true)][string]$Text)

    $bytes = $script:Utf8NoBom.GetBytes($Text)
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try
    {
        $hash = $sha256.ComputeHash($bytes)
        return -join ($hash | ForEach-Object { $_.ToString('x2') })
    }
    finally
    {
        $sha256.Dispose()
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function Get-Sha256File
{
    param([Parameter(Mandatory = $true)][string]$Path)

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function ConvertTo-CompactJson
{
    param([Parameter(Mandatory = $true)]$Value)

    return ($Value | ConvertTo-Json -Compress -Depth 12)
}

function Write-JsonAtomic
{
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

function Write-JsonCreateOnce
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value
    )

    $bytes = $script:Utf8NoBom.GetBytes((ConvertTo-CompactJson $Value))
    $stream = $null
    try
    {
        $stream = [IO.FileStream]::new(
                $Path,
                [IO.FileMode]::CreateNew,
                [IO.FileAccess]::Write,
                [IO.FileShare]::None,
                4096,
                [IO.FileOptions]::WriteThrough
        )
        $stream.Write($bytes, 0, $bytes.Length)
        $stream.Flush($true)
    }
    finally
    {
        if ($null -ne $stream)
        {
            $stream.Dispose()
        }
        [Array]::Clear($bytes, 0, $bytes.Length)
    }
}

function ConvertFrom-JsonPreservingTimestamps
{
    param([Parameter(Mandatory = $true)][string]$Json)

    $parameters = @{ }
    if ((Get-Command ConvertFrom-Json).Parameters.ContainsKey('DateKind'))
    {
        $parameters.DateKind = 'String'
    }
    return ($Json | ConvertFrom-Json @parameters)
}

function Read-JsonFile
{
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        throw "required evidence file is missing"
    }
    return ConvertFrom-JsonPreservingTimestamps (Get-Content -LiteralPath $Path -Raw)
}

function Test-IntegralNumber
{
    param([AllowNull()]$Value)

    return $Value -is [byte] -or $Value -is [sbyte] -or
            $Value -is [int16] -or $Value -is [uint16] -or
            $Value -is [int32] -or $Value -is [uint32] -or
            $Value -is [int64] -or $Value -is [uint64]
}

function Assert-ExactFields
{
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string[]]$Expected,
        [Parameter(Mandatory = $true)][string]$Category
    )

    $actual = @($Value.PSObject.Properties.Name)
    if (($actual -join '|') -ne ($Expected -join '|'))
    {
        throw "$Category schema is invalid"
    }
}

function Assert-LauncherCycleResult
{
    param([Parameter(Mandatory = $true)]$Cycle)

    Assert-ExactFields -Value $Cycle -Expected $script:LauncherFields -Category 'launcher cycle'
    foreach ($field in $script:LauncherFields)
    {
        $value = $Cycle.PSObject.Properties[$field].Value
        if ($null -eq $value -or $value -is [array] -or $value -is [System.Collections.IDictionary])
        {
            throw 'launcher cycle contains null or container data'
        }
    }
    foreach ($field in @(
        'schemaVersion', 'cycleId', 'resultStatus', 'reasonCode', 'httpStatusCategory',
        'permissionClassification', 'killSwitchObservedState', 'allowedEndpointCategory',
        'accountConfigProbeStatus', 'balanceProbeStatus', 'traceId'
    ))
    {
        if ($Cycle.PSObject.Properties[$field].Value -isnot [string])
        {
            throw 'launcher cycle text field type is invalid'
        }
    }
    if ([string]$Cycle.schemaVersion -ne $script:LauncherSchemaVersion)
    {
        throw 'launcher cycle schemaVersion is invalid'
    }
    if ([string]$Cycle.cycleId -notmatch '^gatew-cycle-[a-f0-9]{32}$')
    {
        throw 'launcher cycle cycleId is invalid'
    }
    if ($Cycle.observedAt -isnot [string] -and
            $Cycle.observedAt -isnot [DateTime] -and
            $Cycle.observedAt -isnot [DateTimeOffset])
    {
        throw 'launcher cycle observedAt type is invalid'
    }
    ConvertTo-CanonicalUtcTimestamp $Cycle.observedAt | Out-Null
    if (-not (Test-IntegralNumber $Cycle.durationMs) -or [long]$Cycle.durationMs -lt 0)
    {
        throw 'launcher cycle durationMs is invalid'
    }
    if (@('BOOTSTRAP_READY', 'ENGAGED', 'PASSED_READ_ONLY', 'BLOCKED', 'TRANSIENT_FAILURE', 'HARD_FAILURE', 'FAILED') -notcontains [string]$Cycle.resultStatus)
    {
        throw 'launcher cycle resultStatus is invalid'
    }
    if ([string]$Cycle.reasonCode -notmatch '^[A-Z][A-Z0-9_]{1,95}$')
    {
        throw 'launcher cycle reasonCode is invalid'
    }
    if (@('SUCCESS_2XX', 'RATE_LIMITED_429', 'EXCHANGE_ERROR', 'AUTH_ERROR', 'NETWORK_ERROR', 'NOT_AVAILABLE', 'NOT_CALLED') -notcontains [string]$Cycle.httpStatusCategory)
    {
        throw 'launcher cycle httpStatusCategory is invalid'
    }
    if (@(
        'METADATA_READ_ONLY', 'READ_ONLY_WITH_IP_ALLOWLIST', 'UNKNOWN', 'UNSAFE_OR_INCOMPLETE',
        'UNSAFE_OR_UNKNOWN', 'WITHDRAW_ENABLED', 'READ_ONLY_UNVERIFIED_IP'
    ) -notcontains [string]$Cycle.permissionClassification)
    {
        throw 'launcher cycle permissionClassification is invalid'
    }
    if (@('DISENGAGED', 'ENGAGED', 'UNKNOWN') -notcontains [string]$Cycle.killSwitchObservedState)
    {
        throw 'launcher cycle killSwitchObservedState is invalid'
    }
    if ($Cycle.credentialAccessed -isnot [bool] -or $Cycle.networkCalled -isnot [bool])
    {
        throw 'launcher cycle boolean type is invalid'
    }
    if (@(
        'NONE', 'ACCOUNT_CONFIGURATION_READ', 'ACCOUNT_CONFIG_AND_BALANCE_READ',
        'FORBIDDEN_OR_UNKNOWN', 'OFFLINE_LOCAL_FIXTURE_READ'
    ) -notcontains [string]$Cycle.allowedEndpointCategory)
    {
        throw 'launcher cycle allowedEndpointCategory is invalid'
    }
    foreach ($field in @('accountConfigProbeStatus', 'balanceProbeStatus'))
    {
        $status = [string]$Cycle.PSObject.Properties[$field].Value
        if (@('NOT_RUN', 'SUCCEEDED', 'BLOCKED', 'FAILED', 'UNKNOWN') -notcontains $status)
        {
            throw "launcher cycle $field is invalid"
        }
    }
    if ([string]$Cycle.traceId -notmatch '^gatew-soak-[a-f0-9-]{36}$')
    {
        throw 'launcher cycle traceId is invalid'
    }
    $serialized = ConvertTo-CompactJson $Cycle
    if ($serialized -match '(?i)https?://|/api/v5/')
    {
        throw 'launcher cycle contains a forbidden network material shape'
    }
    $onlinePass = [bool]$Cycle.credentialAccessed -and [bool]$Cycle.networkCalled -and
            [string]$Cycle.killSwitchObservedState -eq 'ENGAGED' -and
            [string]$Cycle.allowedEndpointCategory -eq 'ACCOUNT_CONFIG_AND_BALANCE_READ' -and
            [string]$Cycle.accountConfigProbeStatus -eq 'SUCCEEDED' -and
            [string]$Cycle.balanceProbeStatus -eq 'SUCCEEDED'
    $offlinePass = -not [bool]$Cycle.credentialAccessed -and -not [bool]$Cycle.networkCalled -and
            [string]$Cycle.killSwitchObservedState -eq 'DISENGAGED' -and
            [string]$Cycle.allowedEndpointCategory -eq 'OFFLINE_LOCAL_FIXTURE_READ' -and
            [string]$Cycle.accountConfigProbeStatus -eq 'SUCCEEDED' -and
            [string]$Cycle.balanceProbeStatus -eq 'SUCCEEDED'
    if ([string]$Cycle.resultStatus -eq 'PASSED_READ_ONLY' -and -not ($onlinePass -or $offlinePass))
    {
        throw 'launcher PASS does not prove both allowed read-only probes'
    }
    $noEndpoint = [string]$Cycle.allowedEndpointCategory -eq 'NONE'
    $configOnly = [string]$Cycle.allowedEndpointCategory -eq 'ACCOUNT_CONFIGURATION_READ'
    $configAndBalance = [string]$Cycle.allowedEndpointCategory -eq 'ACCOUNT_CONFIG_AND_BALANCE_READ'
    $offlineFixture = [string]$Cycle.allowedEndpointCategory -eq 'OFFLINE_LOCAL_FIXTURE_READ'
    $configKnown = [string]$Cycle.accountConfigProbeStatus -in @('SUCCEEDED', 'BLOCKED', 'FAILED')
    $balanceKnown = [string]$Cycle.balanceProbeStatus -in @('SUCCEEDED', 'BLOCKED', 'FAILED')
    if ($noEndpoint -and ([bool]$Cycle.credentialAccessed -or [bool]$Cycle.networkCalled))
    {
        throw 'no-endpoint launcher evidence contains credential/network provenance'
    }
    if (-not $noEndpoint -and -not $offlineFixture -and
            (-not [bool]$Cycle.credentialAccessed -or -not [bool]$Cycle.networkCalled))
    {
        throw 'endpoint launcher evidence lacks credential/network provenance'
    }
    if ($offlineFixture -and ([bool]$Cycle.credentialAccessed -or [bool]$Cycle.networkCalled -or
            -not $configKnown -or -not $balanceKnown))
    {
        throw 'offline fixture launcher evidence has unsafe provenance'
    }
    if ($noEndpoint -and
            -not (([string]$Cycle.accountConfigProbeStatus -eq 'NOT_RUN' -and [string]$Cycle.balanceProbeStatus -eq 'NOT_RUN') -or
                    ([string]$Cycle.accountConfigProbeStatus -eq 'UNKNOWN' -and [string]$Cycle.balanceProbeStatus -eq 'UNKNOWN')))
    {
        throw 'no-endpoint launcher evidence has inconsistent probe statuses'
    }
    if ($configOnly -and (-not $configKnown -or [string]$Cycle.balanceProbeStatus -ne 'NOT_RUN'))
    {
        throw 'config-only launcher evidence has inconsistent probe statuses'
    }
    if ($configAndBalance -and (-not $configKnown -or -not $balanceKnown))
    {
        throw 'config-and-balance launcher evidence has incomplete probe statuses'
    }
    if (-not $configAndBalance -and -not $noEndpoint -and -not $offlineFixture -and
            [string]$Cycle.balanceProbeStatus -ne 'NOT_RUN')
    {
        throw 'balance probe status is outside the allowed endpoint category'
    }
}

function ConvertTo-SupervisorCycle
{
    param(
        [Parameter(Mandatory = $true)]$Cycle,
        [Parameter(Mandatory = $true)][bool]$RealCycleOutcomeProven
    )

    Assert-LauncherCycleResult $Cycle
    $safe = [ordered]@{ }
    foreach ($field in $script:LauncherFields)
    {
        $safe[$field] = $Cycle.PSObject.Properties[$field].Value
    }
    $safe.realCycleOutcomeProven = $RealCycleOutcomeProven
    $result = [pscustomobject]$safe
    Assert-SupervisorCycle $result
    return $result
}

function Assert-SupervisorCycle
{
    param([Parameter(Mandatory = $true)]$Cycle)

    Assert-ExactFields -Value $Cycle -Expected $script:SupervisorCycleFields -Category 'supervisor cycle'
    $launcher = [ordered]@{ }
    foreach ($field in $script:LauncherFields)
    {
        $launcher[$field] = $Cycle.PSObject.Properties[$field].Value
    }
    Assert-LauncherCycleResult ([pscustomobject]$launcher)
    if ($Cycle.realCycleOutcomeProven -isnot [bool])
    {
        throw 'supervisor cycle provenance type is invalid'
    }
    if (-not [bool]$Cycle.realCycleOutcomeProven -and
            ([string]$Cycle.resultStatus -ne 'FAILED' -or [string]$Cycle.reasonCode -ne 'LAUNCHER_OUTPUT_UNAVAILABLE'))
    {
        throw 'fallback cycle provenance is invalid'
    }
    if (-not [bool]$Cycle.realCycleOutcomeProven -and
            ([string]$Cycle.allowedEndpointCategory -ne 'NONE' -or
                    [string]$Cycle.accountConfigProbeStatus -ne 'UNKNOWN' -or
                    [string]$Cycle.balanceProbeStatus -ne 'UNKNOWN' -or
                    [bool]$Cycle.credentialAccessed -or [bool]$Cycle.networkCalled))
    {
        throw 'fallback cycle endpoint provenance is invalid'
    }
    if ([bool]$Cycle.realCycleOutcomeProven -and [string]$Cycle.reasonCode -eq 'LAUNCHER_OUTPUT_UNAVAILABLE')
    {
        throw 'parsed launcher cycle cannot use fallback classification'
    }
    if ([bool]$Cycle.realCycleOutcomeProven -and
            ([string]$Cycle.accountConfigProbeStatus -eq 'UNKNOWN' -or
                    [string]$Cycle.balanceProbeStatus -eq 'UNKNOWN'))
    {
        throw 'parsed launcher cycle contains an unproven probe status'
    }
}

function New-FallbackCycle
{
    $fallback = [pscustomobject][ordered]@{
        schemaVersion = $script:LauncherSchemaVersion
        cycleId = "gatew-cycle-$([Guid]::NewGuid().ToString('N') )"
        observedAt = (Get-UtcNow).ToString('o')
        durationMs = 0L
        resultStatus = 'FAILED'
        reasonCode = 'LAUNCHER_OUTPUT_UNAVAILABLE'
        httpStatusCategory = 'NOT_AVAILABLE'
        permissionClassification = 'UNKNOWN'
        killSwitchObservedState = 'UNKNOWN'
        credentialAccessed = $false
        networkCalled = $false
        allowedEndpointCategory = 'NONE'
        accountConfigProbeStatus = 'UNKNOWN'
        balanceProbeStatus = 'UNKNOWN'
        traceId = "gatew-soak-$([Guid]::NewGuid() )"
    }
    return ConvertTo-SupervisorCycle $fallback $false
}

function Get-EvidenceSchemaVersion
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    $manifest = Read-JsonFile (Join-Path $Directory 'manifest.json')
    $property = $manifest.PSObject.Properties['evidenceSchemaVersion']
    if ($null -eq $property -or [string]::IsNullOrWhiteSpace([string]$property.Value))
    {
        return $script:EvidenceSchemaV1
    }
    $version = [string]$property.Value
    if ($version -notin @($script:EvidenceSchemaV1, $script:EvidenceSchemaV2))
    {
        throw 'evidence schema version is unsupported'
    }
    return $version
}

function Get-SampleFields
{
    param([Parameter(Mandatory = $true)][string]$EvidenceSchemaVersion)

    if ($EvidenceSchemaVersion -eq $script:EvidenceSchemaV1)
    {
        return $script:SampleFieldsV1
    }
    if ($EvidenceSchemaVersion -eq $script:EvidenceSchemaV2)
    {
        return $script:SampleFieldsV2
    }
    throw 'evidence schema version is unsupported'
}

function Assert-RunId
{
    param([Parameter(Mandatory = $true)][string]$Value)

    if ($Value -cnotmatch '^gatew-soak-[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$')
    {
        throw 'runId is invalid'
    }
}

function New-WindowsLoopProcessArguments
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$SupervisorPath
    )

    Assert-RunId $Value
    # Preserve the accepted Windows PowerShell 5.1/7 Start-Process contract.
    $arguments = @(
        '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $SupervisorPath + '"'),
        '-Action', 'run-loop', '-RunId', $Value
    )
    return $arguments
}

function New-RunId
{
    $timestamp = (Get-UtcNow).ToString('yyyyMMddTHHmmssZ')
    $suffix = [Guid]::NewGuid().ToString('N').Substring(0, 8)
    return "gatew-soak-$timestamp-$suffix"
}

function Assert-NoPathComponentLink
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $normalized = [IO.Path]::GetFullPath($Path)
    $pathRoot = [IO.Path]::GetPathRoot($normalized)
    if ( [string]::IsNullOrWhiteSpace($pathRoot))
    {
        throw 'BLOCKED / FORMAL_PATH_CONTRACT_INVALID'
    }
    $current = $pathRoot
    $relative = $normalized.Substring($pathRoot.Length)
    foreach ($segment in @($relative -split '[\\/]' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }))
    {
        $current = Join-Path $current $segment
        if (-not (Test-Path -LiteralPath $current))
        {
            throw 'BLOCKED / FORMAL_PATH_CONTRACT_INVALID'
        }
        $item = Get-Item -LiteralPath $current -Force
        if ($null -ne $item.LinkType -or $item.Attributes.ToString() -match 'ReparsePoint')
        {
            throw 'BLOCKED / FORMAL_PATH_SYMLINK_FORBIDDEN'
        }
    }
}

function Get-RunDirectory
{
    param([Parameter(Mandatory = $true)][string]$Value)

    Assert-RunId $Value
    if (Test-FormalSystemdWorker)
    {
        $configured = [Environment]::GetEnvironmentVariable('NQ_GATEW_FORMAL_EVIDENCE_ROOT', 'Process')
        $expected = [IO.Path]::GetFullPath("$( $script:FormalStateRoot )/$Value/evidence")
        if ([string]::IsNullOrWhiteSpace($configured) -or
                [IO.Path]::GetFullPath($configured) -cne $expected -or
                -not (Test-Path -LiteralPath $expected -PathType Container))
        {
            throw 'BLOCKED / FORMAL_EVIDENCE_ROOT_INVALID'
        }
        Assert-NoPathComponentLink $expected
        $realPath = Invoke-LinuxNativeCommand `
            -FilePath $script:LinuxReadlinkPath `
            -Arguments @('-f', '--', $expected)
        if ($realPath.ExitCode -ne 0 -or
                (ConvertTo-TrimmedNativeOutput $realPath.Lines) -cne $expected)
        {
            throw 'BLOCKED / FORMAL_EVIDENCE_ROOT_INVALID'
        }
        return $expected
    }
    $candidate = [IO.Path]::GetFullPath((Join-Path $script:EvidenceRoot $Value))
    if (-not $candidate.StartsWith($script:EvidenceRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase))
    {
        throw 'run directory escaped evidence root'
    }
    return $candidate
}

function Get-EvidenceRunId
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    $leaf = Split-Path -Leaf $Directory
    if ($leaf -eq 'evidence' -and (Test-FormalSystemdWorker))
    {
        $leaf = Split-Path -Leaf (Split-Path -Parent $Directory)
    }
    Assert-RunId $leaf
    return $leaf
}

function Get-ReleaseIdentity
{
    param(
        [Parameter(Mandatory = $true)][string]$ExpectedReleaseId,
        [Parameter(Mandatory = $true)][string]$ExpectedManifestSha256
    )

    $verifier = Join-Path $script:ReleaseRoot "bin/$( $script:ReleaseVerifierName )"
    if (-not (Test-Path -LiteralPath $verifier -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_VERIFIER_MISSING'
    }
    $output = @(& $verifier -ReleaseRoot $script:ReleaseRoot `
            -ExpectedReleaseId $ExpectedReleaseId -ExpectedManifestSha256 $ExpectedManifestSha256 `
            -SkipPosix 2> $null)
    if ($LASTEXITCODE -ne 0 -or $output.Count -eq 0)
    {
        throw 'BLOCKED / RELEASE_VERIFY_FAILED'
    }
    try
    {
        $identity = ConvertFrom-JsonPreservingTimestamps ($output -join "`n")
        if ([string]$identity.decision -ne 'PASS / IMMUTABLE_RELEASE_VERIFIED')
        {
            throw 'BLOCKED / RELEASE_VERIFY_FAILED'
        }
        $script:ReleaseRoot = [IO.Path]::GetFullPath([string]$identity.releaseRoot)
        return $identity
    }
    catch
    {
        throw 'BLOCKED / RELEASE_VERIFY_FAILED'
    }
}

function Assert-FormalReleaseBinding
{
    param([Parameter(Mandatory = $true)]$Manifest)

    $releaseId = [string]$Manifest.releaseId
    $manifestSha256 = [string]$Manifest.releaseManifestSha256
    Assert-ReleaseIdentityValues $releaseId $manifestSha256 ([string]$Manifest.harnessCommit)
    $environmentContract = @{
        NQ_GATEW_RELEASE_ROOT = [string]$script:ReleaseRoot
        NQ_GATEW_RELEASE_ID = $releaseId
        NQ_GATEW_RELEASE_MANIFEST_SHA256 = $manifestSha256
    }
    foreach ($name in $environmentContract.Keys)
    {
        if ([Environment]::GetEnvironmentVariable($name, 'Process') -cne $environmentContract[$name])
        {
            throw 'BLOCKED / FORMAL_RELEASE_ENVIRONMENT_CHANGED'
        }
    }
    $identity = Get-ReleaseIdentity $releaseId $manifestSha256
    if ([string]$identity.sourceCommit -cne [string]$Manifest.harnessCommit)
    {
        throw 'BLOCKED / FORMAL_RELEASE_BINDING_CHANGED'
    }
    return $identity
}

function Assert-ReleaseIdentityValues
{
    param(
        [Parameter(Mandatory = $true)][string]$ReleaseId,
        [Parameter(Mandatory = $true)][string]$ManifestSha256,
        [Parameter(Mandatory = $true)][string]$SourceCommit
    )

    if ($ReleaseId -cnotmatch '^(?:[a-f0-9]{40}|candidate-[a-f0-9]{12}-[a-f0-9]{16}-[0-9]{8}T[0-9]{6}Z)$' -or
            $ManifestSha256 -cnotmatch '^[a-f0-9]{64}$' -or
            $SourceCommit -cnotmatch '^[a-f0-9]{40}$')
    {
        throw 'BLOCKED / FORMAL_RELEASE_BINDING_INVALID'
    }
}

function Get-FormalLauncherClassPath
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    if (-not (Test-FormalSystemdWorker))
    {
        throw 'BLOCKED / FORMAL_SYSTEMD_WORKER_REQUIRED'
    }
    $manifest = Read-JsonFile (Join-Path $Directory 'manifest.json')
    Assert-FormalReleaseBinding $manifest | Out-Null
    foreach ($path in @(
        "$( $script:ReleaseRoot )/launcher/test-support.jar",
        "$( $script:ReleaseRoot )/launcher/modules",
        "$( $script:ReleaseRoot )/launcher/lib"
    ))
    {
        if (-not (Test-Path -LiteralPath $path))
        {
            throw 'BLOCKED / FORMAL_LAUNCHER_BUNDLE_MISSING'
        }
        Assert-NoPathComponentLink $path
    }
    return @(
        "$( $script:ReleaseRoot )/launcher/test-support.jar",
        "$( $script:ReleaseRoot )/launcher/modules/*",
        "$( $script:ReleaseRoot )/launcher/lib/*"
    ) -join [IO.Path]::PathSeparator
}

function Invoke-FormalJavaLauncher
{
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet(
                'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakCycleTest',
                'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakCycleTest$PrerequisiteMain',
                'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakFailCloseTest'
        )]
        [string]$MainClass,
        [Parameter(Mandatory = $true)][hashtable]$SystemProperties,
        [Parameter(Mandatory = $true)][string]$Directory
    )

    if (-not (Test-Path -LiteralPath $script:LinuxJavaPath -PathType Leaf))
    {
        throw 'BLOCKED / FORMAL_JAVA_RUNTIME_MISSING'
    }
    $arguments = @('-Xms16m', '-Xmx256m', '-Dfile.encoding=UTF-8')
    foreach ($name in @($SystemProperties.Keys | Sort-Object))
    {
        if ($name -cnotmatch '^[a-z][A-Za-z0-9.]{2,127}$')
        {
            throw 'BLOCKED / FORMAL_LAUNCHER_PROPERTY_INVALID'
        }
        $value = [string]$SystemProperties[$name]
        if ($value.IndexOf([char]0) -ge 0 -or $value -match "[`r`n]")
        {
            throw 'BLOCKED / FORMAL_LAUNCHER_PROPERTY_INVALID'
        }
        $arguments += "-D$name=$value"
    }
    $arguments += @('-cp', (Get-FormalLauncherClassPath $Directory), $MainClass)
    try
    {
        $null = & $script:LinuxJavaPath @arguments 2> $null
    }
    catch
    {
        # Raw Java/JDBC/provider output is never evidence; only the closed result file is consumed.
    }
    return [int]$LASTEXITCODE
}

function Invoke-SanitizedCycle
{
    param(
        [Parameter(Mandatory = $true)][string]$CycleAction,
        [Parameter(Mandatory = $true)][string]$Directory
    )

    $cycleFile = Join-Path $Directory ".cycle-$PID.json"
    if (Test-Path -LiteralPath $cycleFile)
    {
        Remove-Item -LiteralPath $cycleFile -Force
    }
    try
    {
        if (Test-FormalSystemdWorker)
        {
            Invoke-FormalJavaLauncher `
                'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakCycleTest' `
                @{
                'nq.gatew.okxReadonlySoak.required' = 'true'
                'nq.gatew.okxReadonlySoak.action' = $CycleAction
                'nq.gatew.okxReadonlySoak.resultFile' = $cycleFile
                'nq.gatew.okxReadonlySoak.repoRoot' = $script:ReleaseRoot
            } `
                $Directory | Out-Null
        }
        if (Test-Path -LiteralPath $cycleFile -PathType Leaf)
        {
            try
            {
                $parsed = Read-JsonFile $cycleFile
                return ConvertTo-SupervisorCycle $parsed $true
            }
            catch
            {
                # Only a missing or non-conformant launcher result may use fallback.
            }
        }
        return New-FallbackCycle
    }
    finally
    {
        if (Test-Path -LiteralPath $cycleFile)
        {
            Remove-Item -LiteralPath $cycleFile -Force
        }
    }
}

function Invoke-SanitizedPrerequisiteReadback
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    if (-not (Test-FormalSystemdWorker) -or
            [Environment]::GetEnvironmentVariable('NQ_GATEW_RUN_MODE', 'Process') -ne 'REAL_READONLY_SOAK')
    {
        throw 'BLOCKED / REAL_PREREQUISITE_WORKER_REQUIRED'
    }
    $resultFile = Join-Path $Directory ".prerequisite-$PID.json"
    if (Test-Path -LiteralPath $resultFile)
    {
        Remove-Item -LiteralPath $resultFile -Force
    }
    try
    {
        Invoke-FormalJavaLauncher `
            'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakCycleTest$PrerequisiteMain' `
            @{
            'nq.gatew.okxReadonlySoak.required' = 'true'
            'nq.gatew.okxReadonlySoak.action' = 'prerequisite'
            'nq.gatew.okxReadonlySoak.resultFile' = $resultFile
            'nq.gatew.okxReadonlySoak.repoRoot' = $script:ReleaseRoot
        } `
            $Directory | Out-Null
        if (-not (Test-Path -LiteralPath $resultFile -PathType Leaf))
        {
            throw 'FAIL / PREREQUISITE_READBACK_UNAVAILABLE'
        }
        $result = Read-JsonFile $resultFile
        Assert-ExactFields -Value $result -Expected @(
            'killSwitchEngaged', 'credentialConfigured', 'activeCredentialCount', 'credentialType',
            'credentialLocalStatus', 'tradePermissionExpectedDisabled',
            'withdrawPermissionExpectedDisabled', 'postgresReachable', 'managementHealthy'
        ) -Category 'prerequisite readback'
        foreach ($field in @(
            'killSwitchEngaged', 'credentialConfigured', 'tradePermissionExpectedDisabled',
            'withdrawPermissionExpectedDisabled', 'postgresReachable', 'managementHealthy'
        ))
        {
            if ($result.PSObject.Properties[$field].Value -isnot [bool])
            {
                throw 'FAIL / PREREQUISITE_READBACK_SCHEMA_INVALID'
            }
        }
        $serialized = ConvertTo-CompactJson $result
        if (-not (Test-IntegralNumber $result.activeCredentialCount) -or
                [int]$result.activeCredentialCount -lt 0 -or
                [string]$result.credentialType -notin @('OKX_API_V5', 'UNKNOWN', 'CONFLICT') -or
                [string]$result.credentialLocalStatus -notin @(
                    'ACTIVE', 'DISABLED', 'REVOKED', 'EXPIRED', 'ROTATED', 'UNKNOWN', 'CONFLICT'
                ) -or
                $serialized -match '(?i)https?://|api[-_]?key|passphrase|signature|encrypted[_-]?payload|jdbc[^\"]*password')
        {
            throw 'FAIL / PREREQUISITE_READBACK_SCHEMA_INVALID'
        }
        if (-not [bool]$result.killSwitchEngaged -or
                -not [bool]$result.credentialConfigured -or
                [int]$result.activeCredentialCount -ne 1 -or
                [string]$result.credentialType -ne 'OKX_API_V5' -or
                [string]$result.credentialLocalStatus -ne 'ACTIVE' -or
                -not [bool]$result.tradePermissionExpectedDisabled -or
                -not [bool]$result.withdrawPermissionExpectedDisabled -or
                -not [bool]$result.postgresReachable -or
                -not [bool]$result.managementHealthy)
        {
            throw 'FAIL / PREREQUISITE_READBACK_NOT_READY'
        }
        Write-JsonCreateOnce (Join-Path $Directory 'prerequisite-readback.json') $result
        return $result
    }
    finally
    {
        if (Test-Path -LiteralPath $resultFile)
        {
            Remove-Item -LiteralPath $resultFile -Force
        }
    }
}

function Assert-OfflineBootstrapResult
{
    param([Parameter(Mandatory = $true)]$Result)

    Assert-ExactFields -Value $Result -Expected @(
        'schemaVersion', 'action', 'observedAt', 'recoveryStatus', 'killSwitchObservedState',
        'killSwitchVersion', 'credentialAccessed', 'networkCalled'
    ) -Category 'offline bootstrap'
    if ([string]$Result.schemaVersion -ne 'gatew-soak-failclose-v1' -or
            [string]$Result.action -ne 'offline-bootstrap' -or
            [string]$Result.killSwitchObservedState -ne 'DISENGAGED' -or
            $Result.credentialAccessed -isnot [bool] -or [bool]$Result.credentialAccessed -or
            $Result.networkCalled -isnot [bool] -or [bool]$Result.networkCalled -or
            -not (Test-IntegralNumber $Result.killSwitchVersion) -or [long]$Result.killSwitchVersion -lt 1)
    {
        $failureCode = Get-OfflineBootstrapFailureCode ([string]$Result.recoveryStatus)
        if ($failureCode -ne 'OFFLINE_BOOTSTRAP_RESULT_INVALID')
        {
            throw "FAIL / $failureCode"
        }
        throw 'FAIL / OFFLINE_BOOTSTRAP_RESULT_INVALID'
    }
    if ([string]$Result.recoveryStatus -ne 'OFFLINE_FIXTURE_DISENGAGED')
    {
        throw "FAIL / $( Get-OfflineBootstrapFailureCode ([string]$Result.recoveryStatus) )"
    }
}

function Get-OfflineBootstrapFailureCode
{
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$RecoveryStatus)

    $failureCode = switch ($RecoveryStatus)
    {
        'ENGAGE_FAILED_DB_ENV_INVALID' {
            'OFFLINE_BOOTSTRAP_DB_ENV_INVALID'
        }
        'ENGAGE_FAILED_DB_AUTHENTICATION' {
            'OFFLINE_BOOTSTRAP_DB_AUTHENTICATION_FAILED'
        }
        'ENGAGE_FAILED_DB_UNREACHABLE' {
            'OFFLINE_BOOTSTRAP_DB_UNREACHABLE'
        }
        'ENGAGE_FAILED_DB_CONTEXT_INIT' {
            'OFFLINE_BOOTSTRAP_DB_CONTEXT_INIT_FAILED'
        }
        'ENGAGE_FAILED_DB_DRIVER_INIT' {
            'OFFLINE_BOOTSTRAP_DB_DRIVER_INIT_FAILED'
        }
        'ENGAGE_FAILED_DB_DATASOURCE_CONFIG' {
            'OFFLINE_BOOTSTRAP_DB_DATASOURCE_CONFIG_FAILED'
        }
        'ENGAGE_FAILED_DB_TEMPLATE_INIT' {
            'OFFLINE_BOOTSTRAP_DB_TEMPLATE_INIT_FAILED'
        }
        'ENGAGE_FAILED_DB_LOCALITY' {
            'OFFLINE_BOOTSTRAP_DB_LOCALITY_FAILED'
        }
        'ENGAGE_FAILED_DB_MIGRATION_LOAD' {
            'OFFLINE_BOOTSTRAP_DB_MIGRATION_LOAD_FAILED'
        }
        'ENGAGE_FAILED_DB_MIGRATION_EXECUTE' {
            'OFFLINE_BOOTSTRAP_DB_MIGRATION_EXECUTE_FAILED'
        }
        'ENGAGE_FAILED_DB_MIGRATION_VALIDATE' {
            'OFFLINE_BOOTSTRAP_DB_MIGRATION_VALIDATE_FAILED'
        }
        'ENGAGE_FAILED_DB_MIGRATION_HISTORY' {
            'OFFLINE_BOOTSTRAP_DB_MIGRATION_HISTORY_FAILED'
        }
        'ENGAGE_FAILED_DB_SEED_INITIAL_STATE' {
            'OFFLINE_BOOTSTRAP_DB_SEED_INITIAL_STATE_FAILED'
        }
        'ENGAGE_FAILED_DB_SEED_UPDATE' {
            'OFFLINE_BOOTSTRAP_DB_SEED_UPDATE_FAILED'
        }
        'ENGAGE_FAILED_DB_SEED_EVENT' {
            'OFFLINE_BOOTSTRAP_DB_SEED_EVENT_FAILED'
        }
        'ENGAGE_FAILED_DB_SEED_TRANSACTION' {
            'OFFLINE_BOOTSTRAP_DB_SEED_TRANSACTION_FAILED'
        }
        'ENGAGE_FAILED_WRITE' {
            'OFFLINE_BOOTSTRAP_DB_WRITE_FAILED'
        }
        'ENGAGE_FAILED_READBACK' {
            'OFFLINE_BOOTSTRAP_DB_READBACK_FAILED'
        }
        'ENGAGE_STATUS_UNKNOWN' {
            'OFFLINE_BOOTSTRAP_DB_STATUS_UNKNOWN'
        }
        default {
            'OFFLINE_BOOTSTRAP_RESULT_INVALID'
        }
    }
    return $failureCode
}

function Invoke-OfflineTestSupport
{
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('offline-bootstrap', 'offline-sample', 'offline-controlled-failure')]
        [string]$OfflineAction,
        [Parameter(Mandatory = $true)][string]$Directory
    )

    if (-not (Test-FormalSystemdWorker) -or
            [Environment]::GetEnvironmentVariable('NQ_GATEW_RUN_MODE', 'Process') -ne 'OFFLINE_ISOLATED_ACCEPTANCE')
    {
        throw 'BLOCKED / OFFLINE_FORMAL_WORKER_REQUIRED'
    }
    $resultFile = Join-Path $Directory ".offline-$OfflineAction-$PID.json"
    if (Test-Path -LiteralPath $resultFile)
    {
        Remove-Item -LiteralPath $resultFile -Force
    }
    try
    {
        Invoke-FormalJavaLauncher `
            'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakFailCloseTest' `
            @{
            'nq.gatew.soakFailClose.required' = 'true'
            'nq.gatew.soakFailClose.action' = $OfflineAction
            'nq.gatew.soakFailClose.resultFile' = $resultFile
            'nq.gatew.soakFailClose.runId' = $RunId
        } `
            $Directory | Out-Null
        if (-not (Test-Path -LiteralPath $resultFile -PathType Leaf))
        {
            if ($OfflineAction -eq 'offline-bootstrap')
            {
                throw 'FAIL / OFFLINE_BOOTSTRAP_LAUNCHER_FAILED'
            }
            return New-FallbackCycle
        }
        try
        {
            $parsed = Read-JsonFile $resultFile
            if ($OfflineAction -eq 'offline-bootstrap')
            {
                Assert-OfflineBootstrapResult $parsed
                return $parsed
            }
            return ConvertTo-SupervisorCycle $parsed $true
        }
        catch
        {
            if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$')
            {
                throw
            }
            if ($OfflineAction -eq 'offline-bootstrap')
            {
                throw 'FAIL / OFFLINE_BOOTSTRAP_RESULT_INVALID'
            }
            return New-FallbackCycle
        }
    }
    finally
    {
        if (Test-Path -LiteralPath $resultFile)
        {
            Remove-Item -LiteralPath $resultFile -Force
        }
    }
}

function Get-ChainState
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    $samplesPath = Join-Path $Directory 'samples.jsonl'
    if (-not (Test-Path -LiteralPath $samplesPath -PathType Leaf))
    {
        throw 'samples.jsonl is missing'
    }
    $evidenceSchemaVersion = Get-EvidenceSchemaVersion $Directory
    $sampleFields = @(Get-SampleFields $evidenceSchemaVersion)
    $expectedSequence = 1L
    $previousHash = $script:GenesisHash
    $count = 0L
    foreach ($line in Get-Content -LiteralPath $samplesPath)
    {
        if ( [string]::IsNullOrWhiteSpace($line))
        {
            continue
        }
        $record = ConvertFrom-JsonPreservingTimestamps $line
        $fields = @($record.PSObject.Properties.Name)
        if (($fields -join '|') -ne ($sampleFields -join '|'))
        {
            throw 'sample evidence schema is invalid'
        }
        if ($evidenceSchemaVersion -eq $script:EvidenceSchemaV2)
        {
            $supervisorCycle = [ordered]@{ }
            foreach ($field in $script:LauncherFields)
            {
                $supervisorCycle[$field] = $record.PSObject.Properties[$field].Value
            }
            $supervisorCycle.realCycleOutcomeProven = $record.realCycleOutcomeProven
            Assert-SupervisorCycle ([pscustomobject]$supervisorCycle)
        }
        if ([long]$record.sequence -ne $expectedSequence)
        {
            throw 'sample sequence is missing or duplicated'
        }
        if ($record.previousRecordHash -ne $previousHash)
        {
            throw 'sample previousRecordHash is invalid'
        }
        $hashInput = [ordered]@{ }
        foreach ($field in $sampleFields)
        {
            if ($field -ne 'recordHash')
            {
                $hashInput[$field] = if ($field -eq 'observedAt')
                {
                    ConvertTo-CanonicalUtcTimestamp $record.$field
                }
                else
                {
                    $record.$field
                }
            }
        }
        $expectedHash = Get-Sha256Text (ConvertTo-CompactJson $hashInput)
        if ($record.recordHash -ne $expectedHash)
        {
            throw 'sample recordHash is invalid'
        }
        $previousHash = $record.recordHash
        $expectedSequence++
        $count++
    }
    return [pscustomobject]@{
        Count = $count
        LastHash = $previousHash
        NextSequence = $expectedSequence
        EvidenceSchemaVersion = $evidenceSchemaVersion
    }
}

function Append-Sample
{
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)]$Cycle
    )

    Assert-SupervisorCycle $Cycle
    $state = Get-ChainState $Directory
    if ($state.EvidenceSchemaVersion -ne $script:EvidenceSchemaV2)
    {
        throw 'BLOCKED / LEGACY_EVIDENCE_NOT_APPENDABLE'
    }
    $hashInput = [ordered]@{
        schemaVersion = [string]$Cycle.schemaVersion
        sequence = [long]$state.NextSequence
        cycleId = [string]$Cycle.cycleId
        observedAt = (ConvertTo-CanonicalUtcTimestamp $Cycle.observedAt)
        durationMs = [long]$Cycle.durationMs
        resultStatus = [string]$Cycle.resultStatus
        reasonCode = [string]$Cycle.reasonCode
        httpStatusCategory = [string]$Cycle.httpStatusCategory
        permissionClassification = [string]$Cycle.permissionClassification
        killSwitchObservedState = [string]$Cycle.killSwitchObservedState
        credentialAccessed = [bool]$Cycle.credentialAccessed
        networkCalled = [bool]$Cycle.networkCalled
        allowedEndpointCategory = [string]$Cycle.allowedEndpointCategory
        accountConfigProbeStatus = [string]$Cycle.accountConfigProbeStatus
        balanceProbeStatus = [string]$Cycle.balanceProbeStatus
        realCycleOutcomeProven = [bool]$Cycle.realCycleOutcomeProven
        traceId = [string]$Cycle.traceId
        previousRecordHash = [string]$state.LastHash
    }
    $record = [ordered]@{ }
    foreach ($key in $hashInput.Keys)
    {
        $record[$key] = $hashInput[$key]
    }
    $record.recordHash = Get-Sha256Text (ConvertTo-CompactJson $hashInput)
    $line = ConvertTo-CompactJson $record
    [IO.File]::AppendAllText((Join-Path $Directory 'samples.jsonl'), $line + [Environment]::NewLine, $script:Utf8NoBom)
    if ($Cycle.resultStatus -ne 'PASSED_READ_ONLY')
    {
        [IO.File]::AppendAllText((Join-Path $Directory 'failures.jsonl'), $line + [Environment]::NewLine, $script:Utf8NoBom)
    }
    return [pscustomobject]$record
}

function Write-Heartbeat
{
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][string]$State,
        [Parameter(Mandatory = $true)][string]$ReasonCode,
        [long]$Sequence = 0,
        [int]$ConsecutiveAuthenticationFailures = 0
    )

    Write-JsonAtomic (Join-Path $Directory 'heartbeat.json') ([ordered]@{
        runId = Get-EvidenceRunId $Directory
        state = $State
        reasonCode = $ReasonCode
        observedAt = (Get-UtcNow).ToString('o')
        lastSequence = $Sequence
        consecutiveAuthenticationFailures = $ConsecutiveAuthenticationFailures
    })
}

function Get-FormalOfflineAcceptanceState
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    $records = @()
    foreach ($line in Get-Content -LiteralPath (Join-Path $Directory 'samples.jsonl'))
    {
        if (-not [string]::IsNullOrWhiteSpace($line))
        {
            $records += ConvertFrom-JsonPreservingTimestamps $line
        }
    }
    if ($records.Count -ne 3)
    {
        throw 'FAIL / OFFLINE_ACCEPTANCE_CYCLE_COUNT_INVALID'
    }
    foreach ($index in 0..1)
    {
        $expectedSequence = [long]($index + 1)
        $record = $records[$index]
        if ([long]$record.sequence -ne $expectedSequence -or
                [string]$record.resultStatus -ne 'PASSED_READ_ONLY' -or
                [string]$record.reasonCode -ne 'OFFLINE_READONLY_FIXTURE_ACCEPTED' -or
                [string]$record.allowedEndpointCategory -ne 'OFFLINE_LOCAL_FIXTURE_READ' -or
                [string]$record.accountConfigProbeStatus -ne 'SUCCEEDED' -or
                [string]$record.balanceProbeStatus -ne 'SUCCEEDED' -or
                -not [bool]$record.realCycleOutcomeProven -or
                [bool]$record.credentialAccessed -or [bool]$record.networkCalled)
        {
            throw 'FAIL / OFFLINE_ACCEPTANCE_PASS_PROVENANCE_INVALID'
        }
    }
    $failure = $records[2]
    if ([long]$failure.sequence -ne 3 -or [string]$failure.resultStatus -ne 'FAILED' -or
            [string]$failure.reasonCode -ne 'CONTROLLED_OFFLINE_CYCLE_3_FAILURE' -or
            [string]$failure.allowedEndpointCategory -ne 'NONE' -or
            [string]$failure.accountConfigProbeStatus -ne 'NOT_RUN' -or
            [string]$failure.balanceProbeStatus -ne 'NOT_RUN' -or
            -not [bool]$failure.realCycleOutcomeProven -or
            [bool]$failure.credentialAccessed -or [bool]$failure.networkCalled)
    {
        throw 'FAIL / OFFLINE_ACCEPTANCE_FAILURE_PROVENANCE_INVALID'
    }
    $failureRecords = @()
    foreach ($line in Get-Content -LiteralPath (Join-Path $Directory 'failures.jsonl'))
    {
        if (-not [string]::IsNullOrWhiteSpace($line))
        {
            $failureRecords += ConvertFrom-JsonPreservingTimestamps $line
        }
    }
    if ($failureRecords.Count -ne 1 -or
            [string]$failureRecords[0].recordHash -cne [string]$failure.recordHash)
    {
        throw 'FAIL / OFFLINE_ACCEPTANCE_FAILURE_LEDGER_INVALID'
    }
    $heartbeat = Read-JsonFile (Join-Path $Directory 'heartbeat.json')
    if ([string]$heartbeat.runId -cne (Get-EvidenceRunId $Directory) -or
            [string]$heartbeat.state -ne 'FAILURE_STOPPING' -or
            [string]$heartbeat.reasonCode -ne 'CONTROLLED_OFFLINE_CYCLE_3_FAILURE' -or
            [long]$heartbeat.lastSequence -ne 3)
    {
        throw 'FAIL / OFFLINE_ACCEPTANCE_HEARTBEAT_INVALID'
    }
    return [pscustomobject][ordered]@{
        cycleCount = 3
        cycle1 = 'PASS'
        cycle2 = 'PASS'
        cycle3 = 'CONTROLLED_FAILURE'
        lastSuccessfulCycleSequence = 2L
        lastHeartbeatSequence = 3L
        credentialAccessed = $false
        networkCalled = $false
    }
}

function Test-Evidence
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    $manifest = Read-JsonFile (Join-Path $Directory 'manifest.json')
    if (Test-FormalSystemdWorker)
    {
        Assert-FormalReleaseBinding $manifest | Out-Null
    }
    if ($manifest.runId -ne (Get-EvidenceRunId $Directory))
    {
        throw 'manifest runId mismatch'
    }
    if ($manifest.profile -ne 'gatew-okx-readonly-soak')
    {
        throw 'manifest profile mismatch'
    }
    if ($manifest.venue -ne 'OKX')
    {
        throw 'manifest venue mismatch'
    }
    if ([int]$manifest.durationHours -lt 168)
    {
        throw 'manifest duration is below 168 hours'
    }
    if ([int]$manifest.cadenceSeconds -lt 60)
    {
        throw 'manifest cadence is invalid'
    }
    if (Test-Path -LiteralPath (Join-Path $Directory 'final-summary.json'))
    {
        throw 'final-summary.json is acceptance-task-only'
    }
    foreach ($required in @('samples.jsonl', 'failures.jsonl', 'heartbeat.json'))
    {
        if (-not (Test-Path -LiteralPath (Join-Path $Directory $required) -PathType Leaf))
        {
            throw "$required is missing"
        }
    }
    $chain = Get-ChainState $Directory
    $validRealPassSamples = 0L
    $fallbackSamples = 0L
    $forbiddenEndpointCount = 0L
    foreach ($line in Get-Content -LiteralPath (Join-Path $Directory 'samples.jsonl'))
    {
        if ( [string]::IsNullOrWhiteSpace($line))
        {
            continue
        }
        $sample = ConvertFrom-JsonPreservingTimestamps $line
        if ([string]$sample.allowedEndpointCategory -eq 'FORBIDDEN_OR_UNKNOWN' -or
                [string]$sample.reasonCode -eq 'FORBIDDEN_ENDPOINT_ATTEMPTED')
        {
            $forbiddenEndpointCount++
        }
        if ($chain.EvidenceSchemaVersion -eq $script:EvidenceSchemaV2)
        {
            if ([string]$sample.resultStatus -eq 'PASSED_READ_ONLY' -and
                    [bool]$sample.realCycleOutcomeProven -and
                    [string]$sample.accountConfigProbeStatus -eq 'SUCCEEDED' -and
                    [string]$sample.balanceProbeStatus -eq 'SUCCEEDED')
            {
                $validRealPassSamples++
            }
            if (-not [bool]$sample.realCycleOutcomeProven)
            {
                $fallbackSamples++
            }
        }
    }
    foreach ($path in Get-ChildItem -LiteralPath $Directory -File)
    {
        $text = Get-Content -LiteralPath $path.FullName -Raw
        if ($text -match '(?i)"(api[-_]?key|secret[-_]?key|secret|passphrase|signature|cookie|raw[-_]?(body|headers|response|request)|account[-_]?id|sub[-_]?account|balance|available[-_]?balance|cash[-_]?balance|equity|currency|asset|position|amount|size|order)"\s*:' -or
                $text -match '(?i)https?://')
        {
            throw 'evidence contains a forbidden material shape'
        }
    }
    $offlineAcceptance = if ([string]$manifest.environment -eq 'OFFLINE_ISOLATED_ACCEPTANCE' -and
            [long]$chain.Count -eq 3)
    {
        Get-FormalOfflineAcceptanceState $Directory
    }
    else
    {
        $null
    }
    return [pscustomobject]@{
        runId = $manifest.runId
        harnessCommit = $manifest.harnessCommit
        releaseId = Get-OptionalPropertyValue $manifest 'releaseId'
        releaseManifestSha256 = Get-OptionalPropertyValue $manifest 'releaseManifestSha256'
        sampleCount = $chain.Count
        lastHash = $chain.LastHash
        evidenceSchemaVersion = $chain.EvidenceSchemaVersion
        validRealPassSamples = $validRealPassSamples
        fallbackSamples = $fallbackSamples
        rawResponseCount = 0
        secretExposureCount = 0
        forbiddenEndpointCount = $forbiddenEndpointCount
        offlineAcceptance = $offlineAcceptance
        result = 'PASS / HASH_CHAIN_VERIFIED'
    }
}

function Invoke-LinuxNativeCommand
{
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [AllowEmptyCollection()][string[]]$Arguments = @()
    )

    if (-not (Test-LinuxPlatform) -or -not (Test-Path -LiteralPath $FilePath -PathType Leaf))
    {
        return [pscustomobject]@{ ExitCode = 127; Lines = @() }
    }
    try
    {
        # Formal worker只允许调用固定绝对路径做只读路径校验，不提供提权或 systemd 生命周期能力。
        $workingRoot = if (Test-FormalSystemdWorker)
        {
            $script:ReleaseRoot
        }
        else
        {
            $script:WorkspaceRoot
        }
        Set-Location -LiteralPath $workingRoot
        $output = @(& $FilePath @Arguments 2> $null)
        return [pscustomobject]@{ ExitCode = [int]$LASTEXITCODE; Lines = @($output) }
    }
    catch
    {
        return [pscustomobject]@{ ExitCode = 127; Lines = @() }
    }
}
function Get-SupervisorState
{
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [switch]$AllowInactive
    )

    if (Test-LinuxPlatform)
    {
        throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
    }
    $path = Join-Path $Directory 'supervisor.json'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        return [pscustomobject]@{ Running = $false; Pid = 0; StartedAt = $null }
    }
    $control = Read-JsonFile $path
    $process = Get-Process -Id ([int]$control.pid) -ErrorAction SilentlyContinue
    if ($null -eq $process)
    {
        return [pscustomobject]@{ Running = $false; Pid = [int]$control.pid; StartedAt = $control.startedAt }
    }
    $actual = $process.StartTime.ToUniversalTime()
    $expected = [DateTimeOffset]::Parse([string]$control.startedAt).UtcDateTime
    $sameProcess = [Math]::Abs(($actual - $expected).TotalSeconds) -lt 5
    return [pscustomobject]@{ Running = $sameProcess; Pid = [int]$control.pid; StartedAt = $control.startedAt }
}
function Start-LoopProcess
{
    param([Parameter(Mandatory = $true)][string]$Value)

    if (Test-LinuxPlatform)
    {
        throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
    }
    $executable = (Get-Process -Id $PID).Path
    $arguments = @(New-WindowsLoopProcessArguments $Value $script:ScriptPath)
    $process = Start-Process -FilePath $executable -ArgumentList $arguments -WindowStyle Hidden -PassThru
    $startedAt = $process.StartTime.ToUniversalTime().ToString('o')
    $directory = Get-RunDirectory $Value
    Write-JsonAtomic (Join-Path $directory 'supervisor.json') ([ordered]@{
        pid = $process.Id
        startedAt = $startedAt
        runId = $Value
    })
    return $process
}
function Stop-FailClosed
{
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][string]$ReasonCode,
        [Parameter(Mandatory = $true)][string]$State,
        [int]$AuthenticationFailures = 0
    )

    # Linux kill-switch authority只属于独立 root finalizer；worker失败必须以非零退出触发它。
    if (Test-LinuxPlatform)
    {
        throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
    }
    $engage = Invoke-SanitizedCycle 'engage' $Directory
    $chain = Get-ChainState $Directory
    if ($engage.resultStatus -ne 'ENGAGED' -or $engage.killSwitchObservedState -ne 'ENGAGED')
    {
        Write-Heartbeat $Directory 'STOP_FAILURE' 'KILL_SWITCH_ENGAGE_FAILED' $chain.Count $AuthenticationFailures
        throw 'FAIL / KILL_SWITCH_ENGAGE_FAILED'
    }
    Write-Heartbeat $Directory $State $ReasonCode $chain.Count $AuthenticationFailures
}
function Resolve-Blocker
{
    param([Parameter(Mandatory = $true)][string]$ReasonCode)

    $decision = switch ($ReasonCode)
    {
        'API_KEY_REQUIRED' {
            'BLOCKED / API_KEY_REQUIRED'
        }
        'PERMISSION_BLOCKED' {
            'BLOCKED / CREDENTIAL_PERMISSION_NOT_READONLY'
        }
        'CREDENTIAL_PERMISSION_NOT_READONLY' {
            'BLOCKED / CREDENTIAL_PERMISSION_NOT_READONLY'
        }
        'UNSAFE_CREDENTIAL_PERMISSIONS' {
            'BLOCKED / UNSAFE_CREDENTIAL_PERMISSIONS'
        }
        'IP_ALLOWLIST_FAILED' {
            'BLOCKED / IP_ALLOWLIST_REQUIRED'
        }
        'IP_ALLOWLIST_REQUIRED' {
            'BLOCKED / IP_ALLOWLIST_REQUIRED'
        }
        'SOAK_KILL_SWITCH_FIXTURE_NOT_SAFE' {
            'BLOCKED / SOAK_KILL_SWITCH_FIXTURE_NOT_SAFE'
        }
        default {
            "BLOCKED / $ReasonCode"
        }
    }
    return $decision
}

function Get-OptionalPropertyValue
{
    param(
        [Parameter(Mandatory = $true)]$Object,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property)
    {
        return $null
    }
    return $property.Value
}

function Start-Soak
{
    if (Test-LinuxPlatform)
    {
        throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
    }
    $head = 'artifact-' + (Get-Sha256File $script:ScriptPath)
    Assert-RuntimeEnvironment (Get-RuntimeEnvironmentSnapshot)
    if ( [string]::IsNullOrWhiteSpace($StartingCiRun))
    {
        throw 'BLOCKED / EXACT_HEAD_CI_REQUIRED'
    }
    if ($StartingCiRun -cnotmatch '^[1-9][0-9]{0,19}$')
    {
        throw 'BLOCKED / EXACT_HEAD_CI_METADATA_REQUIRED'
    }
    $effectiveRunId = if ( [string]::IsNullOrWhiteSpace($RunId))
    {
        New-RunId
    }
    else
    {
        $RunId
    }
    $directory = Get-RunDirectory $effectiveRunId
    if (Test-Path -LiteralPath $directory)
    {
        throw 'BLOCKED / RUN_ID_ALREADY_EXISTS'
    }
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    [IO.File]::WriteAllText((Join-Path $directory 'samples.jsonl'), '', $script:Utf8NoBom)
    [IO.File]::WriteAllText((Join-Path $directory 'failures.jsonl'), '', $script:Utf8NoBom)
    Write-Heartbeat $directory 'PREPARING' 'HARD_GATES_PENDING'

    $bootstrap = Invoke-SanitizedCycle 'bootstrap' $directory
    if ($bootstrap.resultStatus -ne 'BOOTSTRAP_READY')
    {
        $engage = Invoke-SanitizedCycle 'engage' $directory
        if ($engage.resultStatus -ne 'ENGAGED' -or $engage.killSwitchObservedState -ne 'ENGAGED')
        {
            throw 'FAIL / KILL_SWITCH_ENGAGE_FAILED'
        }
        Write-Heartbeat $directory 'BLOCKED' ([string]$bootstrap.reasonCode)
        throw (Resolve-Blocker ([string]$bootstrap.reasonCode))
    }

    $ownershipTransferred = $false
    try
    {
        $startedAt = Get-UtcNow
        $manifest = [ordered]@{
            runId = $effectiveRunId
            harnessCommit = $head
            startingCiRun = $StartingCiRun
            startedAt = $startedAt.ToString('o')
            plannedEndAt = $startedAt.AddHours($DurationHours).ToString('o')
            durationHours = $DurationHours
            cadenceSeconds = $CadenceSeconds
            maxTransientRetries = $MaxTransientRetries
            maxConsecutiveAuthFailures = $MaxConsecutiveAuthFailures
            venue = 'OKX'
            environment = 'REAL_OKX_PRIVATE_READONLY'
            profile = 'gatew-okx-readonly-soak'
            applicationVersion = "0.1.0-SNAPSHOT+$($head.Substring(0, 12) )"
            endpointAllowlistVersion = 'gatew-okx-private-readonly-v1'
            flywayVersion = '35'
            hostFingerprint = Get-Sha256Text "$env:COMPUTERNAME|$( [Environment]::OSVersion.VersionString )"
            supervisorArtifactSha256 = Get-Sha256File $script:ScriptPath
            launcherSchemaVersion = $script:LauncherSchemaVersion
            evidenceSchemaVersion = $script:EvidenceSchemaV2
        }
        Write-JsonAtomic (Join-Path $directory 'manifest.json') $manifest

        $first = Invoke-SanitizedCycle 'sample' $directory
        $record = Append-Sample $directory $first
        if ($first.resultStatus -ne 'PASSED_READ_ONLY')
        {
            Stop-FailClosed $directory ([string]$first.reasonCode) 'BLOCKED'
            throw (Resolve-Blocker ([string]$first.reasonCode))
        }

        $process = Start-LoopProcess $effectiveRunId
        Write-Heartbeat $directory 'RUNNING' 'SOAK_STARTED' $record.sequence
        $ownershipTransferred = $true
        return [pscustomobject]@{
            decision = 'PASS / REAL_OKX_READONLY_SOAK_PREPARED / SOAK_STARTED / SEVEN_DAY_ACCEPTANCE_PENDING'
            runId = $effectiveRunId
            harnessCommit = $head
            startingCiRun = $StartingCiRun
            startedAt = $manifest.startedAt
            plannedEndAt = $manifest.plannedEndAt
            cadenceSeconds = $CadenceSeconds
            supervisorPid = $process.Id
            unitName = $null
            mainPid = [long]$process.Id
            supervisorStartedAt = $process.StartTime.ToUniversalTime().ToString('o')
            evidenceDirectory = $directory
        }
    }
    finally
    {
        if (-not $ownershipTransferred)
        {
            $engage = Invoke-SanitizedCycle 'engage' $directory
            if ($engage.resultStatus -ne 'ENGAGED' -or $engage.killSwitchObservedState -ne 'ENGAGED')
            {
                throw 'FAIL / KILL_SWITCH_ENGAGE_FAILED'
            }
        }
    }
}
function Wait-ForCadenceOrControl
{
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][int]$Seconds,
        [Parameter(Mandatory = $true)][DateTimeOffset]$PlannedEndAt
    )

    $deadline = (Get-UtcNow).AddSeconds($Seconds)
    if ($deadline -gt $PlannedEndAt)
    {
        $deadline = $PlannedEndAt
    }
    while ((Get-UtcNow) -lt $deadline)
    {
        if (Test-Path -LiteralPath (Join-Path $Directory 'stop-request.json'))
        {
            return $false
        }
        $remaining = [Math]::Max(1, ($deadline - (Get-UtcNow)).TotalSeconds)
        Start-Sleep -Seconds ([int][Math]::Min(5,[Math]::Ceiling($remaining)))
    }
    return $true
}

function Write-FormalWorkerStart
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    Write-JsonCreateOnce (Join-Path $Directory 'worker-start.json') ([ordered]@{
        schemaVersion = 'gatew-soak-worker-start-v1'
        runId = $RunId
        mainPid = [long]$PID
        unitName = "nq-gatew-soak@$RunId.service"
        startedAt = (Get-UtcNow).ToString('o')
    })
}

function Get-FormalAcceptanceClockPath
{
    return "$( $script:FormalStateRoot )/$RunId/control/acceptance-clock-start.json"
}

function Read-FormalAcceptanceClock
{
    $path = Get-FormalAcceptanceClockPath
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        throw 'BLOCKED / ACCEPTANCE_CLOCK_NOT_STARTED'
    }
    Assert-NoPathComponentLink $path
    $clock = Read-JsonFile $path
    Assert-ExactFields -Value $clock -Expected @(
        'schemaVersion', 'runId', 'firstValidConfigPassAt', 'firstValidBalancePassAt',
        'freshSshVerificationAt', 'mainPid', 'sameMainPid', 'heartbeatAdvanced',
        'hashChainValid', 'forbiddenEndpointCount', 'secretExposureCount',
        'acceptanceStartAt', 'plannedAcceptanceAt', 'acceptanceClockStarted'
    ) -Category 'acceptance clock'
    $configAt = [DateTimeOffset]::MinValue
    $balanceAt = [DateTimeOffset]::MinValue
    $freshAt = [DateTimeOffset]::MinValue
    $startAt = [DateTimeOffset]::MinValue
    $plannedAt = [DateTimeOffset]::MinValue
    $schemaInvalid = [string]$clock.schemaVersion -ne 'gatew-soak-acceptance-clock-v1' -or
            [string]$clock.runId -ne $RunId -or [long]$clock.mainPid -ne [long]$PID -or
            -not [bool]$clock.sameMainPid -or -not [bool]$clock.heartbeatAdvanced -or
            -not [bool]$clock.hashChainValid -or [int]$clock.forbiddenEndpointCount -ne 0 -or
            [int]$clock.secretExposureCount -ne 0 -or -not [bool]$clock.acceptanceClockStarted -or
            -not [DateTimeOffset]::TryParse([string]$clock.firstValidConfigPassAt, [ref]$configAt) -or
            -not [DateTimeOffset]::TryParse([string]$clock.firstValidBalancePassAt, [ref]$balanceAt) -or
            -not [DateTimeOffset]::TryParse([string]$clock.freshSshVerificationAt, [ref]$freshAt) -or
            -not [DateTimeOffset]::TryParse([string]$clock.acceptanceStartAt, [ref]$startAt) -or
            -not [DateTimeOffset]::TryParse([string]$clock.plannedAcceptanceAt, [ref]$plannedAt)
    $latestPrerequisite = @($configAt, $balanceAt, $freshAt) |
            Sort-Object -Descending | Select-Object -First 1
    if ($schemaInvalid -or $startAt -ne $latestPrerequisite -or
            $plannedAt -ne $startAt.AddHours(168))
    {
        throw 'BLOCKED / ACCEPTANCE_CLOCK_RECORD_INVALID'
    }
    return $clock
}

function Wait-ForFormalAcceptanceClock
{
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][long]$LastSequence,
        [ValidateRange(1, 30)][int]$HeartbeatSeconds = 5
    )

    while (-not (Test-Path -LiteralPath (Get-FormalAcceptanceClockPath) -PathType Leaf))
    {
        Write-Heartbeat $Directory 'RUNNING' 'ACCEPTANCE_CLOCK_START_PENDING' $LastSequence
        Start-Sleep -Seconds $HeartbeatSeconds
    }
    $clock = Read-FormalAcceptanceClock
    Write-Heartbeat $Directory 'RUNNING' 'ACCEPTANCE_CLOCK_STARTED' $LastSequence
    return $clock
}

function Write-FormalCompletionMarker
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    $chain = Get-ChainState $Directory
    $lastSuccessfulSequence = 0L
    foreach ($line in Get-Content -LiteralPath (Join-Path $Directory 'samples.jsonl'))
    {
        if ( [string]::IsNullOrWhiteSpace($line))
        {
            continue
        }
        $sample = ConvertFrom-JsonPreservingTimestamps $line
        if ([string]$sample.resultStatus -eq 'PASSED_READ_ONLY')
        {
            $lastSuccessfulSequence = [long]$sample.sequence
        }
    }
    if ($lastSuccessfulSequence -lt 1 -or $lastSuccessfulSequence -gt [long]$chain.Count)
    {
        throw 'FAIL / NATURAL_COMPLETION_PASS_SEQUENCE_INVALID'
    }

    Write-JsonCreateOnce (Join-Path $Directory 'completion-marker.json') ([ordered]@{
        schemaVersion = 'gatew-soak-completion-marker-v1'
        runId = $RunId
        lastSuccessfulCycleSequence = $lastSuccessfulSequence
        lastHeartbeatSequence = [long]$chain.Count
        completedAt = (Get-UtcNow).ToString('o')
    })
    Write-Heartbeat $Directory 'COMPLETING' 'NATURAL_COMPLETION_MARKER_WRITTEN' ([long]$chain.Count)
}

function Run-FormalOfflineAcceptance
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    $heartbeatRaw = [Environment]::GetEnvironmentVariable('NQ_GATEW_OFFLINE_HEARTBEAT_SECONDS', 'Process')
    $heartbeatSeconds = 0
    if (-not [int]::TryParse($heartbeatRaw, [ref]$heartbeatSeconds) -or
            $heartbeatSeconds -lt 1 -or $heartbeatSeconds -gt 30)
    {
        throw 'BLOCKED / OFFLINE_HEARTBEAT_CONFIG_INVALID'
    }
    Invoke-OfflineTestSupport 'offline-bootstrap' $Directory | Out-Null
    foreach ($sequence in 1..2)
    {
        if ($sequence -gt 1)
        {
            Start-Sleep -Seconds $heartbeatSeconds
        }
        $cycle = Invoke-OfflineTestSupport 'offline-sample' $Directory
        $record = Append-Sample $Directory $cycle
        if ([string]$cycle.resultStatus -ne 'PASSED_READ_ONLY' -or
                [string]$cycle.allowedEndpointCategory -ne 'OFFLINE_LOCAL_FIXTURE_READ' -or
                [bool]$cycle.credentialAccessed -or [bool]$cycle.networkCalled -or
                [string]$cycle.accountConfigProbeStatus -ne 'SUCCEEDED' -or
                [string]$cycle.balanceProbeStatus -ne 'SUCCEEDED')
        {
            Write-Heartbeat $Directory 'FAILURE_STOPPING' 'OFFLINE_PASS_PROVENANCE_INVALID' $record.sequence
            throw 'FAIL / OFFLINE_PASS_PROVENANCE_INVALID'
        }
        Write-Heartbeat $Directory 'RUNNING' 'OFFLINE_READONLY_FIXTURE_ACCEPTED' $record.sequence
    }

    Wait-ForFormalAcceptanceClock $Directory 2L $heartbeatSeconds | Out-Null

    $failureMarker = "$( $script:FormalRuntimeRoot )/$RunId/offline-cycle-3-failure"
    while (-not (Test-Path -LiteralPath $failureMarker -PathType Leaf))
    {
        Start-Sleep -Seconds $heartbeatSeconds
        Write-Heartbeat $Directory 'RUNNING' 'OFFLINE_CYCLE_3_FAILURE_PENDING' 2
    }
    $controlled = Invoke-OfflineTestSupport 'offline-controlled-failure' $Directory
    $controlledRecord = Append-Sample $Directory $controlled
    if ([string]$controlled.resultStatus -ne 'FAILED' -or
            [string]$controlled.reasonCode -ne 'CONTROLLED_OFFLINE_CYCLE_3_FAILURE' -or
            [bool]$controlled.credentialAccessed -or [bool]$controlled.networkCalled)
    {
        Write-Heartbeat $Directory 'FAILURE_STOPPING' 'OFFLINE_CONTROLLED_FAILURE_NOT_PROVEN' `
            $controlledRecord.sequence
        throw 'FAIL / OFFLINE_CONTROLLED_FAILURE_NOT_PROVEN'
    }
    Write-Heartbeat $Directory 'FAILURE_STOPPING' 'CONTROLLED_OFFLINE_CYCLE_3_FAILURE' `
        $controlledRecord.sequence
    # A nonzero worker exit delegates engage and terminal authority to the independent finalizer.
    throw 'FAIL / CONTROLLED_OFFLINE_CYCLE_3_FAILURE'
}

function Run-FormalRealSoak
{
    param(
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)]$Manifest
    )

    Invoke-SanitizedPrerequisiteReadback $Directory | Out-Null
    $bootstrap = Invoke-SanitizedCycle 'bootstrap' $Directory
    if ([string]$bootstrap.resultStatus -ne 'BOOTSTRAP_READY')
    {
        Write-Heartbeat $Directory 'FAILURE_STOPPING' ([string]$bootstrap.reasonCode)
        throw "FAIL / $( [string]$bootstrap.reasonCode )"
    }
    $first = Invoke-SanitizedCycle 'sample' $Directory
    $firstRecord = Append-Sample $Directory $first
    if ([string]$first.resultStatus -ne 'PASSED_READ_ONLY')
    {
        Write-Heartbeat $Directory 'FAILURE_STOPPING' ([string]$first.reasonCode) $firstRecord.sequence
        throw "FAIL / $( [string]$first.reasonCode )"
    }
    Write-Heartbeat $Directory 'RUNNING' 'READ_ONLY_SAMPLE_ACCEPTED' $firstRecord.sequence

    $clock = Wait-ForFormalAcceptanceClock $Directory $firstRecord.sequence
    $plannedEndAt = [DateTimeOffset]::Parse([string]$clock.plannedAcceptanceAt)
    $authFailures = 0
    while ((Get-UtcNow) -lt $plannedEndAt)
    {
        Wait-ForCadenceOrControl $Directory ([int]$Manifest.cadenceSeconds) $plannedEndAt | Out-Null
        if ((Get-UtcNow) -ge $plannedEndAt)
        {
            break
        }
        $retry = 0
        do
        {
            $cycle = Invoke-SanitizedCycle 'sample' $Directory
            $record = Append-Sample $Directory $cycle
            if ([string]$cycle.resultStatus -eq 'PASSED_READ_ONLY')
            {
                $authFailures = 0
                Write-Heartbeat $Directory 'RUNNING' 'READ_ONLY_SAMPLE_ACCEPTED' $record.sequence
                break
            }
            $reason = [string]$cycle.reasonCode
            Write-Heartbeat $Directory 'DEGRADED' $reason $record.sequence $authFailures
            if ($script:ImmediateStopReasons -contains $reason -or
                    [string]$cycle.resultStatus -in @('BLOCKED', 'HARD_FAILURE', 'FAILED'))
            {
                Write-Heartbeat $Directory 'FAILURE_STOPPING' $reason $record.sequence $authFailures
                throw "FAIL / $reason"
            }
            if ($script:AuthenticationReasons -contains $reason)
            {
                $authFailures++
                if ($authFailures -ge [int]$Manifest.maxConsecutiveAuthFailures)
                {
                    Write-Heartbeat $Directory 'FAILURE_STOPPING' 'CONSECUTIVE_AUTHENTICATION_FAILURES' `
                        $record.sequence $authFailures
                    throw 'FAIL / CONSECUTIVE_AUTHENTICATION_FAILURES'
                }
                break
            }
            if ($script:TransientReasons -contains $reason -and
                    $retry -lt [int]$Manifest.maxTransientRetries)
            {
                $retry++
                Wait-ForCadenceOrControl $Directory ([Math]::Min(120, 30 * $retry)) $plannedEndAt | Out-Null
                continue
            }
            break
        } while ($true)
    }
    Write-FormalCompletionMarker $Directory
}

function Run-SoakLoop
{
    $directory = Get-RunDirectory $RunId
    $manifest = Read-JsonFile (Join-Path $directory 'manifest.json')
    if (Test-FormalSystemdWorker)
    {
        Assert-FormalReleaseBinding $manifest | Out-Null
    }
    Test-Evidence $directory | Out-Null
    Assert-RunLoopAllowed $directory
    if (Test-FormalSystemdWorker)
    {
        Write-FormalWorkerStart $directory
        $mode = [Environment]::GetEnvironmentVariable('NQ_GATEW_RUN_MODE', 'Process')
        if ($mode -eq 'OFFLINE_ISOLATED_ACCEPTANCE')
        {
            Run-FormalOfflineAcceptance $directory
            return
        }
        if ($mode -eq 'REAL_READONLY_SOAK')
        {
            Run-FormalRealSoak $directory $manifest
            return
        }
        throw 'BLOCKED / FORMAL_RUN_MODE_INVALID'
    }
    if (Test-LinuxPlatform)
    {
        throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
    }
    $plannedEndAt = [DateTimeOffset]::Parse([string]$manifest.plannedEndAt)
    $authFailures = 0

    while ((Get-UtcNow) -lt $plannedEndAt)
    {
        Wait-ForCadenceOrControl $directory ([int]$manifest.cadenceSeconds) $plannedEndAt | Out-Null
        $stopPath = Join-Path $directory 'stop-request.json'
        if (Test-Path -LiteralPath $stopPath)
        {
            $request = Read-JsonFile $stopPath
            $state = if ($request.kind -eq 'failure')
            {
                'FAILURE_STOPPED'
            }
            else
            {
                'STOPPED'
            }
            Stop-FailClosed $directory ([string]$request.reasonCode) $state $authFailures
            return
        }
        if ((Get-UtcNow) -ge $plannedEndAt)
        {
            break
        }

        $retry = 0
        do
        {
            $cycle = Invoke-SanitizedCycle 'sample' $directory
            $record = Append-Sample $directory $cycle
            if ($cycle.resultStatus -eq 'PASSED_READ_ONLY')
            {
                $authFailures = 0
                Write-Heartbeat $directory 'RUNNING' 'READ_ONLY_SAMPLE_ACCEPTED' $record.sequence
                break
            }

            $reason = [string]$cycle.reasonCode
            Write-Heartbeat $directory 'DEGRADED' $reason $record.sequence $authFailures
            if ($script:ImmediateStopReasons -contains $reason -or
                    $cycle.resultStatus -in @('BLOCKED', 'HARD_FAILURE', 'FAILED'))
            {
                Stop-FailClosed $directory $reason 'FAILURE_STOPPED' $authFailures
                return
            }
            if ($script:AuthenticationReasons -contains $reason)
            {
                $authFailures++
                Write-Heartbeat $directory 'DEGRADED' $reason $record.sequence $authFailures
                if ($authFailures -ge [int]$manifest.maxConsecutiveAuthFailures)
                {
                    Stop-FailClosed $directory 'CONSECUTIVE_AUTHENTICATION_FAILURES' 'FAILURE_STOPPED' $authFailures
                    return
                }
                break
            }
            if ($script:TransientReasons -contains $reason -and $retry -lt [int]$manifest.maxTransientRetries)
            {
                $retry++
                $continueRetry = Wait-ForCadenceOrControl $directory ([Math]::Min(120, 30 * $retry)) $plannedEndAt
                if (-not $continueRetry)
                {
                    break
                }
                continue
            }
            break
        } while ($true)
    }
    Stop-FailClosed $directory 'SEVEN_DAY_ELAPSED_ACCEPTANCE_REQUIRED' 'ELAPSED_PENDING_ACCEPTANCE' $authFailures
}

function Assert-RunResumable
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    $schemaVersion = Get-EvidenceSchemaVersion $Directory
    $heartbeat = Read-JsonFile (Join-Path $Directory 'heartbeat.json')
    if ($schemaVersion -ne $script:EvidenceSchemaV2)
    {
        throw 'BLOCKED / LEGACY_EVIDENCE_NOT_RESUMABLE'
    }
    if ([string]$heartbeat.state -notin @('RUNNING', 'DEGRADED'))
    {
        throw 'BLOCKED / TERMINAL_RUN_NOT_RESUMABLE'
    }
}

function Assert-RunLoopAllowed
{
    param([Parameter(Mandatory = $true)][string]$Directory)

    $schemaVersion = Get-EvidenceSchemaVersion $Directory
    $heartbeat = Read-JsonFile (Join-Path $Directory 'heartbeat.json')
    if ($schemaVersion -ne $script:EvidenceSchemaV2)
    {
        throw 'BLOCKED / LEGACY_EVIDENCE_NOT_RUNNABLE'
    }
    if ([string]$heartbeat.state -notin @('PREPARING', 'RUNNING', 'DEGRADED'))
    {
        throw 'BLOCKED / TERMINAL_RUN_NOT_RUNNABLE'
    }
}

function Resume-Soak
{
    if (Test-LinuxPlatform)
    {
        throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
    }
    $directory = Get-RunDirectory $RunId
    $manifest = Read-JsonFile (Join-Path $directory 'manifest.json')
    Test-Evidence $directory | Out-Null
    Assert-RunResumable $directory
    $state = Get-SupervisorState $directory -AllowInactive
    if ($state.Running)
    {
        throw 'BLOCKED / SOAK_ALREADY_RUNNING'
    }
    if ((Get-UtcNow) -ge [DateTimeOffset]::Parse([string]$manifest.plannedEndAt))
    {
        throw 'BLOCKED / SEVEN_DAY_ELAPSED_ACCEPTANCE_REQUIRED'
    }
    $process = Start-LoopProcess $RunId
    $chain = Get-ChainState $directory
    Write-Heartbeat $directory 'RUNNING' 'SOAK_RESUMED' ($chain.NextSequence - 1)
    return [pscustomobject]@{
        decision = 'SOAK_RESUMED'
        runId = $RunId
        supervisorPid = $process.Id
        unitName = $null
        mainPid = [long]$process.Id
        supervisorStartedAt = $process.StartTime.ToUniversalTime().ToString('o')
    }
}
function Request-Stop
{
    param([bool]$Failure)

    if (Test-LinuxPlatform)
    {
        throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
    }
    $directory = Get-RunDirectory $RunId
    $manifest = Read-JsonFile (Join-Path $directory 'manifest.json')
    $heartbeat = Read-JsonFile (Join-Path $directory 'heartbeat.json')
    if ([string]$heartbeat.state -in @('BLOCKED', 'STOPPED', 'FAILURE_STOPPED', 'ELAPSED_PENDING_ACCEPTANCE'))
    {
        return [pscustomobject]@{
            decision = 'NO_CHANGE / TERMINAL_RUN'
            runId = $RunId
            state = $heartbeat.state
            reasonCode = $heartbeat.reasonCode
        }
    }
    $state = Get-SupervisorState $directory -AllowInactive
    $kind = if ($Failure)
    {
        'failure'
    }
    else
    {
        'graceful'
    }
    $reason = if ($Failure)
    {
        'OPERATOR_FAILURE_STOP'
    }
    else
    {
        'OPERATOR_GRACEFUL_STOP'
    }
    Write-JsonAtomic (Join-Path $directory 'stop-request.json') ([ordered]@{
        kind = $kind
        reasonCode = $reason
        requestedAt = (Get-UtcNow).ToString('o')
    })
    if (-not $state.Running)
    {
        Stop-FailClosed $directory $reason $( if ($Failure)
        {
            'FAILURE_STOPPED'
        }
        else
        {
            'STOPPED'
        } )
    }
    return [pscustomobject]@{ decision = 'STOP_REQUESTED'; runId = $RunId; kind = $kind; supervisorPid = $state.Pid }
}
function Show-Status
{
    if (Test-LinuxPlatform)
    {
        throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
    }
    $directory = Get-RunDirectory $RunId
    $manifest = Read-JsonFile (Join-Path $directory 'manifest.json')
    $heartbeat = Read-JsonFile (Join-Path $directory 'heartbeat.json')
    $process = Get-SupervisorState $directory
    $chain = Get-ChainState $directory
    return [pscustomobject]@{
        runId = $RunId
        state = $heartbeat.state
        reasonCode = $heartbeat.reasonCode
        processRunning = $process.Running
        supervisorPid = $process.Pid
        sampleCount = $chain.Count
        startedAt = $manifest.startedAt
        plannedEndAt = $manifest.plannedEndAt
        harnessCommit = $manifest.harnessCommit
        unitName = Get-OptionalPropertyValue $process 'UnitName'
        unitLoadState = Get-OptionalPropertyValue $process 'LoadState'
        unitActiveState = Get-OptionalPropertyValue $process 'ActiveState'
        unitSubState = Get-OptionalPropertyValue $process 'SubState'
        unitExecMainStatus = Get-OptionalPropertyValue $process 'ExecMainStatus'
        unitFragmentPath = Get-OptionalPropertyValue $process 'FragmentPath'
        unitUser = Get-OptionalPropertyValue $process 'User'
        supervisorStartedAt = $process.StartedAt
        heartbeatObservedAt = Get-OptionalPropertyValue $process 'HeartbeatObservedAt'
    }
}

function Cleanup-RunControlFiles
{
    if (Test-LinuxPlatform)
    {
        throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
    }
    $directory = Get-RunDirectory $RunId
    $state = Get-SupervisorState $directory
    if ($state.Running)
    {
        throw 'BLOCKED / SOAK_STILL_RUNNING'
    }
    Test-Evidence $directory | Out-Null
    foreach ($file in Get-ChildItem -LiteralPath $directory -File)
    {
        if ($file.Name -like '.cycle-*.json' -or $file.Name -in @('stop-request.json', 'supervisor.json'))
        {
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

function Invoke-SelfTest
{
    $directories = @()
    $caseCount = 0
    $roundTripTimestamp = '2026-07-20T17:39:01.8426894Z'
    $parsedTimestamp = (ConvertFrom-JsonPreservingTimestamps `
            "{`"observedAt`":`"$roundTripTimestamp`"}").observedAt
    if ($parsedTimestamp -isnot [string] -or [string]$parsedTimestamp -cne $roundTripTimestamp)
    {
        throw 'JSON timestamp preservation self-test failed'
    }
    $caseCount++
    $bootstrapFailureCodes = @{
        ENGAGE_FAILED_DB_ENV_INVALID = 'OFFLINE_BOOTSTRAP_DB_ENV_INVALID'
        ENGAGE_FAILED_DB_AUTHENTICATION = 'OFFLINE_BOOTSTRAP_DB_AUTHENTICATION_FAILED'
        ENGAGE_FAILED_DB_UNREACHABLE = 'OFFLINE_BOOTSTRAP_DB_UNREACHABLE'
        ENGAGE_FAILED_DB_CONTEXT_INIT = 'OFFLINE_BOOTSTRAP_DB_CONTEXT_INIT_FAILED'
        ENGAGE_FAILED_DB_DRIVER_INIT = 'OFFLINE_BOOTSTRAP_DB_DRIVER_INIT_FAILED'
        ENGAGE_FAILED_DB_DATASOURCE_CONFIG = 'OFFLINE_BOOTSTRAP_DB_DATASOURCE_CONFIG_FAILED'
        ENGAGE_FAILED_DB_TEMPLATE_INIT = 'OFFLINE_BOOTSTRAP_DB_TEMPLATE_INIT_FAILED'
        ENGAGE_FAILED_DB_LOCALITY = 'OFFLINE_BOOTSTRAP_DB_LOCALITY_FAILED'
        ENGAGE_FAILED_DB_MIGRATION_LOAD = 'OFFLINE_BOOTSTRAP_DB_MIGRATION_LOAD_FAILED'
        ENGAGE_FAILED_DB_MIGRATION_EXECUTE = 'OFFLINE_BOOTSTRAP_DB_MIGRATION_EXECUTE_FAILED'
        ENGAGE_FAILED_DB_MIGRATION_VALIDATE = 'OFFLINE_BOOTSTRAP_DB_MIGRATION_VALIDATE_FAILED'
        ENGAGE_FAILED_DB_MIGRATION_HISTORY = 'OFFLINE_BOOTSTRAP_DB_MIGRATION_HISTORY_FAILED'
        ENGAGE_FAILED_DB_SEED_INITIAL_STATE = 'OFFLINE_BOOTSTRAP_DB_SEED_INITIAL_STATE_FAILED'
        ENGAGE_FAILED_DB_SEED_UPDATE = 'OFFLINE_BOOTSTRAP_DB_SEED_UPDATE_FAILED'
        ENGAGE_FAILED_DB_SEED_EVENT = 'OFFLINE_BOOTSTRAP_DB_SEED_EVENT_FAILED'
        ENGAGE_FAILED_DB_SEED_TRANSACTION = 'OFFLINE_BOOTSTRAP_DB_SEED_TRANSACTION_FAILED'
        ENGAGE_FAILED_WRITE = 'OFFLINE_BOOTSTRAP_DB_WRITE_FAILED'
        ENGAGE_FAILED_READBACK = 'OFFLINE_BOOTSTRAP_DB_READBACK_FAILED'
        ENGAGE_STATUS_UNKNOWN = 'OFFLINE_BOOTSTRAP_DB_STATUS_UNKNOWN'
    }
    foreach ($entry in $bootstrapFailureCodes.GetEnumerator())
    {
        if ((Get-OfflineBootstrapFailureCode $entry.Key) -ne $entry.Value)
        {
            throw 'offline bootstrap failure taxonomy self-test failed'
        }
        $caseCount++
    }
    $testRunId = New-RunId
    $directory = Get-RunDirectory $testRunId
    $directories += $directory
    [IO.Directory]::CreateDirectory($directory) | Out-Null
    try
    {
        $runtimeEnvironment = @{ }
        foreach ($name in $script:RuntimeEnvironmentNames)
        {
            $runtimeEnvironment[$name] = ''
        }
        $runtimeEnvironment.NQ_GATEW_RUN_MODE = 'REAL_READONLY_SOAK'
        $runtimeEnvironment.SPRING_PROFILES_ACTIVE = 'gatew-okx-readonly-soak'
        $runtimeEnvironment.NQ_GATEW_OKX_READONLY_SOAK_ENABLED = 'true'
        $runtimeEnvironment.CI = 'false'
        $runtimeEnvironment.NQ_NO_OUTBOUND = 'false'
        foreach ($name in @(
            'NQ_LIVE_ENABLED', 'NQ_REAL_ORDER_SUBMISSION_ENABLED', 'NQ_TRANSFER_ENABLED', 'NQ_WITHDRAW_ENABLED',
            'NQ_AI_ENABLED', 'NQ_DH_RUNTIME_ENABLED', 'NQ_REAL_PROVIDER_ENABLED', 'NQ_REAL_CLIENT_ENABLED',
            'NQ_REAL_EXCHANGE_ENABLED'
        ))
        {
            $runtimeEnvironment[$name] = 'false'
        }
        $runtimeEnvironment.NQ_GATEW_SOAK_DB_URL = 'jdbc:postgresql://127.0.0.1:55432/nq_gatew_soak_fixture'
        $runtimeEnvironment.NQ_GATEW_SOAK_DB_USER = 'fixture-user'
        $runtimeEnvironment.NQ_GATEW_SOAK_DB_PASSWORD = 'test'
        $runtimeEnvironment.NQ_ACCOUNT_CREDENTIALS_MASTER_KEY = 'test'
        $runtimeEnvironment.NQ_GATEW_SOAK_OWNER_ID = '1'
        $runtimeEnvironment.NQ_GATEW_SOAK_ACCOUNT_ID = '1'
        $runtimeEnvironment.NQ_GATEW_SOAK_CURRENCIES = 'BTC'
        Assert-RuntimeEnvironment $runtimeEnvironment
        $variableEnvironment = @{ }
        foreach ($name in $runtimeEnvironment.Keys)
        {
            $variableEnvironment[$name] = $runtimeEnvironment[$name]
        }
        $variableEnvironment.NQ_GATEW_SOAK_DB_URL = '${NQ_GATEW_SOAK_DB_URL_LITERAL}'
        $variableReferenceRejected = $false
        try
        {
            Assert-RuntimeEnvironment $variableEnvironment
        }
        catch
        {
            $variableReferenceRejected = $_.Exception.Message -eq 'BLOCKED / RUNTIME_ENVIRONMENT_NOT_LITERAL'
        }
        if (-not $variableReferenceRejected)
        {
            throw 'runtime variable reference was not rejected'
        }
        $caseCount++

        $workerSource = [IO.File]::ReadAllText($script:ScriptPath)
        $forbiddenLinuxAuthorityTokens = @(
            ('systemd' + '-run'),
            ('linux' + '-smoke-'),
            ('Start-Linux' + 'TransientUnit'),
            ('Stop-Linux' + 'TransientUnit'),
            ('New-Linux' + 'TransientUnitArguments')
        )
        foreach ($token in $forbiddenLinuxAuthorityTokens)
        {
            if ( $workerSource.Contains($token))
            {
                throw 'legacy Linux production authority remains'
            }
        }
        $caseCount++

        $windowsArguments = @(New-WindowsLoopProcessArguments $testRunId $script:ScriptPath)
        $expectedWindowsArguments = @(
            '-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', ('"' + $script:ScriptPath + '"'),
            '-Action', 'run-loop', '-RunId', $testRunId
        )
        if (($windowsArguments -join [char]31) -cne ($expectedWindowsArguments -join [char]31))
        {
            throw 'accepted Windows Start-Process arguments changed'
        }
        $caseCount++

        $missingCredentialRejected = $false
        try
        {
            Assert-RuntimeEnvironment @{ NQ_GATEW_RUN_MODE = 'REAL_READONLY_SOAK' }
        }
        catch
        {
            $missingCredentialRejected = $_.Exception.Message -eq 'BLOCKED / API_KEY_REQUIRED'
        }
        if (-not $missingCredentialRejected)
        {
            throw 'missing credential preflight was not rejected'
        }
        $caseCount++
        Assert-ReleaseIdentityValues `
            'candidate-0123456789ab-0123456789abcdef-20260719T000000Z' `
            ('a' * 64) ('b' * 40)
        $invalidReleaseRejected = $false
        try
        {
            Assert-ReleaseIdentityValues '../escape' ('a' * 64) ('b' * 40)
        }
        catch
        {
            $invalidReleaseRejected = $_.Exception.Message -eq 'BLOCKED / FORMAL_RELEASE_BINDING_INVALID'
        }
        if (-not $invalidReleaseRejected)
        {
            throw 'invalid release identity self-test failed'
        }
        $caseCount += 2
        [IO.File]::WriteAllText((Join-Path $directory 'samples.jsonl'), '', $script:Utf8NoBom)
        [IO.File]::WriteAllText((Join-Path $directory 'failures.jsonl'), '', $script:Utf8NoBom)
        Write-JsonAtomic (Join-Path $directory 'manifest.json') ([ordered]@{
            runId = $testRunId; harnessCommit = '0' * 40; startingCiRun = '0'
            startedAt = (Get-UtcNow).ToString('o'); plannedEndAt = (Get-UtcNow).AddHours(168).ToString('o')
            durationHours = 168; cadenceSeconds = 900; venue = 'OKX'
            profile = 'gatew-okx-readonly-soak'; environment = 'SELF_TEST_NO_NETWORK'
            launcherSchemaVersion = $script:LauncherSchemaVersion
            evidenceSchemaVersion = $script:EvidenceSchemaV2
        })
        Write-Heartbeat $directory 'SELF_TEST' 'NO_PRIVATE_NETWORK_CALLED'

        $safeLauncher = [pscustomobject][ordered]@{
            schemaVersion = $script:LauncherSchemaVersion
            cycleId = 'gatew-cycle-0123456789abcdef0123456789abcdef'
            observedAt = '2026-07-16T00:00:00Z'
            durationMs = 1L
            resultStatus = 'PASSED_READ_ONLY'
            reasonCode = 'READ_ONLY_SAMPLE_ACCEPTED'
            httpStatusCategory = 'SUCCESS_2XX'
            permissionClassification = 'READ_ONLY_WITH_IP_ALLOWLIST'
            killSwitchObservedState = 'ENGAGED'
            credentialAccessed = $true
            networkCalled = $true
            allowedEndpointCategory = 'ACCOUNT_CONFIG_AND_BALANCE_READ'
            accountConfigProbeStatus = 'SUCCEEDED'
            balanceProbeStatus = 'SUCCEEDED'
            traceId = 'gatew-soak-123e4567-e89b-12d3-a456-426614174000'
        }
        Assert-LauncherCycleResult $safeLauncher
        $safeCycle = ConvertTo-SupervisorCycle $safeLauncher $true
        $caseCount++

        $formalOfflineRunId = New-RunId
        $formalOfflineDirectory = Get-RunDirectory $formalOfflineRunId
        $directories += $formalOfflineDirectory
        [IO.Directory]::CreateDirectory($formalOfflineDirectory) | Out-Null
        [IO.File]::WriteAllText((Join-Path $formalOfflineDirectory 'samples.jsonl'), '', $script:Utf8NoBom)
        [IO.File]::WriteAllText((Join-Path $formalOfflineDirectory 'failures.jsonl'), '', $script:Utf8NoBom)
        Write-JsonAtomic (Join-Path $formalOfflineDirectory 'manifest.json') ([ordered]@{
            runId = $formalOfflineRunId; harnessCommit = '0' * 40; startingCiRun = '0'
            startedAt = '2026-07-18T00:00:00Z'; plannedEndAt = '2026-07-25T00:00:00Z'
            durationHours = 168; cadenceSeconds = 60; venue = 'OKX'
            profile = 'gatew-okx-readonly-soak'; environment = 'OFFLINE_ISOLATED_ACCEPTANCE'
            launcherSchemaVersion = $script:LauncherSchemaVersion
            evidenceSchemaVersion = $script:EvidenceSchemaV2
        })
        $offlineCycles = @()
        foreach ($index in 1..2)
        {
            $offlineData = [ordered]@{ }
            foreach ($field in $script:LauncherFields)
            {
                $offlineData[$field] = $safeLauncher.PSObject.Properties[$field].Value
            }
            $offlineData.cycleId = 'gatew-cycle-' + ([string]$index * 32)
            $offlineData.observedAt = "2026-07-18T00:00:0$( $index )Z"
            $offlineData.resultStatus = 'PASSED_READ_ONLY'
            $offlineData.reasonCode = 'OFFLINE_READONLY_FIXTURE_ACCEPTED'
            $offlineData.httpStatusCategory = 'NOT_CALLED'
            $offlineData.permissionClassification = 'METADATA_READ_ONLY'
            $offlineData.killSwitchObservedState = 'DISENGAGED'
            $offlineData.credentialAccessed = $false
            $offlineData.networkCalled = $false
            $offlineData.allowedEndpointCategory = 'OFFLINE_LOCAL_FIXTURE_READ'
            $offlineData.accountConfigProbeStatus = 'SUCCEEDED'
            $offlineData.balanceProbeStatus = 'SUCCEEDED'
            $offlineData.traceId = "gatew-soak-00000000-0000-0000-0000-00000000000$index"
            $offlineCycles += ConvertTo-SupervisorCycle ([pscustomobject]$offlineData) $true
        }
        $offlineFailureData = [ordered]@{ }
        foreach ($field in $script:LauncherFields)
        {
            $offlineFailureData[$field] = $safeLauncher.PSObject.Properties[$field].Value
        }
        $offlineFailureData.cycleId = 'gatew-cycle-' + ('3' * 32)
        $offlineFailureData.observedAt = '2026-07-18T00:00:03Z'
        $offlineFailureData.resultStatus = 'FAILED'
        $offlineFailureData.reasonCode = 'CONTROLLED_OFFLINE_CYCLE_3_FAILURE'
        $offlineFailureData.httpStatusCategory = 'NOT_CALLED'
        $offlineFailureData.permissionClassification = 'UNKNOWN'
        $offlineFailureData.killSwitchObservedState = 'UNKNOWN'
        $offlineFailureData.credentialAccessed = $false
        $offlineFailureData.networkCalled = $false
        $offlineFailureData.allowedEndpointCategory = 'NONE'
        $offlineFailureData.accountConfigProbeStatus = 'NOT_RUN'
        $offlineFailureData.balanceProbeStatus = 'NOT_RUN'
        $offlineFailureData.traceId = 'gatew-soak-00000000-0000-0000-0000-000000000003'
        $offlineCycles += ConvertTo-SupervisorCycle ([pscustomobject]$offlineFailureData) $true
        foreach ($offlineCycle in $offlineCycles)
        {
            Append-Sample $formalOfflineDirectory $offlineCycle | Out-Null
        }
        Write-Heartbeat $formalOfflineDirectory 'FAILURE_STOPPING' `
            'CONTROLLED_OFFLINE_CYCLE_3_FAILURE' 3
        $formalOfflineVerification = Test-Evidence $formalOfflineDirectory
        if ($formalOfflineVerification.sampleCount -ne 3 -or
                $formalOfflineVerification.offlineAcceptance.cycle1 -ne 'PASS' -or
                $formalOfflineVerification.offlineAcceptance.cycle2 -ne 'PASS' -or
                $formalOfflineVerification.offlineAcceptance.cycle3 -ne 'CONTROLLED_FAILURE' -or
                $formalOfflineVerification.offlineAcceptance.credentialAccessed -or
                $formalOfflineVerification.offlineAcceptance.networkCalled)
        {
            throw 'formal offline acceptance fixture was not proven'
        }
        $caseCount++

        $blockedData = [ordered]@{ }
        foreach ($field in $script:LauncherFields)
        {
            $blockedData[$field] = $safeLauncher.PSObject.Properties[$field].Value
        }
        $blockedData.cycleId = 'gatew-cycle-1123456789abcdef0123456789abcdef'
        $blockedData.resultStatus = 'BLOCKED'
        $blockedData.reasonCode = 'PERMISSION_BLOCKED'
        $blockedData.httpStatusCategory = 'AUTH_ERROR'
        $blockedData.permissionClassification = 'UNSAFE_OR_INCOMPLETE'
        $blockedData.allowedEndpointCategory = 'ACCOUNT_CONFIGURATION_READ'
        $blockedData.accountConfigProbeStatus = 'SUCCEEDED'
        $blockedData.balanceProbeStatus = 'NOT_RUN'
        $blockedData.traceId = 'gatew-soak-223e4567-e89b-12d3-a456-426614174000'
        $blockedCycle = ConvertTo-SupervisorCycle ([pscustomobject]$blockedData) $true
        $caseCount++

        $failedData = [ordered]@{ }
        foreach ($field in $script:LauncherFields)
        {
            $failedData[$field] = $safeLauncher.PSObject.Properties[$field].Value
        }
        $failedData.cycleId = 'gatew-cycle-2123456789abcdef0123456789abcdef'
        $failedData.resultStatus = 'HARD_FAILURE'
        $failedData.reasonCode = 'PARTIAL_RESPONSE'
        $failedData.httpStatusCategory = 'NOT_AVAILABLE'
        $failedData.permissionClassification = 'UNKNOWN'
        $failedData.accountConfigProbeStatus = 'SUCCEEDED'
        $failedData.balanceProbeStatus = 'FAILED'
        $failedData.traceId = 'gatew-soak-323e4567-e89b-12d3-a456-426614174000'
        $failedCycle = ConvertTo-SupervisorCycle ([pscustomobject]$failedData) $true
        $caseCount++

        $fallbackCycle = New-FallbackCycle
        if ([bool]$fallbackCycle.realCycleOutcomeProven -or
                [string]$fallbackCycle.resultStatus -ne 'FAILED' -or
                [string]$fallbackCycle.reasonCode -ne 'LAUNCHER_OUTPUT_UNAVAILABLE')
        {
            throw 'fallback provenance self-test failed'
        }
        $caseCount++

        $unsafeCases = @(
            [pscustomobject]@{ Field = 'balance'; Value = 100L },
            [pscustomobject]@{ Field = 'availableBalance'; Value = '100' },
            [pscustomobject]@{ Field = 'balance_detail'; Value = [pscustomobject]@{ equity = '100' } },
            [pscustomobject]@{ Field = 'currency'; Value = 'USDT' },
            [pscustomobject]@{ Field = 'asset'; Value = 'USDT' },
            [pscustomobject]@{ Field = 'accountId'; Value = 'account-1' },
            [pscustomobject]@{ Field = 'rawResponse'; Value = 'provider-payload' },
            [pscustomobject]@{ Field = 'raw_request'; Value = 'provider-request' },
            [pscustomobject]@{ Field = 'RAW_HEADERS'; Value = 'provider-headers' },
            [pscustomobject]@{ Field = 'apiSecret'; Value = 'credential-material' },
            [pscustomobject]@{ Field = 'AVAILABLE_BALANCE'; Value = '100' },
            [pscustomobject]@{ Field = 'unknownField'; Value = 'UNKNOWN' },
            [pscustomobject]@{ Field = 'balanceProbeStatus'; Value = [pscustomobject]@{ status = 'SUCCEEDED' } },
            [pscustomobject]@{ Field = 'balanceProbeStatus'; Value = 'AVAILABLE' },
            [pscustomobject]@{ Field = 'networkCalled'; Value = $false }
        )
        $unsafeRejected = 0
        foreach ($unsafeCase in $unsafeCases)
        {
            $candidate = [ordered]@{ }
            foreach ($field in $script:LauncherFields)
            {
                $candidate[$field] = $safeLauncher.PSObject.Properties[$field].Value
            }
            $candidate[[string]$unsafeCase.Field] = $unsafeCase.Value
            try
            {
                Assert-LauncherCycleResult ([pscustomobject]$candidate)
            }
            catch
            {
                $unsafeRejected++
                $caseCount++
            }
        }
        if ($unsafeRejected -ne $unsafeCases.Count)
        {
            throw 'unsafe launcher fixture was accepted'
        }

        $first = Append-Sample $directory $safeCycle
        $second = Append-Sample $directory $blockedCycle
        $third = Append-Sample $directory $failedCycle
        $fourth = Append-Sample $directory $fallbackCycle
        $state = Get-ChainState $directory
        if ($first.sequence -ne 1 -or $second.sequence -ne 2 -or $third.sequence -ne 3 -or
                $fourth.sequence -ne 4 -or $state.Count -ne 4 -or
                $second.previousRecordHash -ne $first.recordHash -or
                $fourth.previousRecordHash -ne $third.recordHash)
        {
            throw 'hash-chain self-test failed'
        }
        $caseCount++
        Test-Evidence $directory | Out-Null
        $verification = Test-Evidence $directory
        if ($verification.validRealPassSamples -ne 1 -or $verification.fallbackSamples -ne 1 -or
                $verification.rawResponseCount -ne 0 -or $verification.secretExposureCount -ne 0)
        {
            throw 'fallback/pass/raw/secret evidence counts are invalid'
        }
        $caseCount++
        $beforeResume = @(Get-Content -LiteralPath (Join-Path $directory 'samples.jsonl'))
        $fifth = Append-Sample $directory $safeCycle
        $afterResume = @(Get-Content -LiteralPath (Join-Path $directory 'samples.jsonl'))
        if ($fifth.sequence -ne 5 -or $afterResume.Count -ne 5)
        {
            throw 'resume append-only self-test failed'
        }
        for ($index = 0; $index -lt $beforeResume.Count; $index++) {
            if ($beforeResume[$index] -ne $afterResume[$index])
            {
                throw 'resume append-only self-test modified existing samples'
            }
        }
        $caseCount++
        Test-Evidence $directory | Out-Null

        $samplesPath = Join-Path $directory 'samples.jsonl'
        $originalSamples = [IO.File]::ReadAllBytes($samplesPath)
        $tamperedLines = @(Get-Content -LiteralPath $samplesPath)
        $tampered = ConvertFrom-JsonPreservingTimestamps $tamperedLines[0]
        $tampered.reasonCode = 'TAMPERED_SAMPLE'
        $tamperedLines[0] = ConvertTo-CompactJson $tampered
        [IO.File]::WriteAllText($samplesPath, ($tamperedLines -join [Environment]::NewLine) + [Environment]::NewLine, $script:Utf8NoBom)
        $tamperRejected = $false
        try
        {
            Get-ChainState $directory | Out-Null
        }
        catch
        {
            $tamperRejected = $true
        }
        [IO.File]::WriteAllBytes($samplesPath, $originalSamples)
        if (-not $tamperRejected)
        {
            throw 'tampered hash-chain sample was not rejected'
        }
        $caseCount++

        [IO.File]::AppendAllText(
                $samplesPath,
                $afterResume[0] + [Environment]::NewLine,
                $script:Utf8NoBom
        )
        $duplicateRejected = $false
        try
        {
            Get-ChainState $directory | Out-Null
        }
        catch
        {
            $duplicateRejected = $true
        }
        [IO.File]::WriteAllBytes($samplesPath, $originalSamples)
        if (-not $duplicateRejected)
        {
            throw 'duplicate sequence was not rejected'
        }
        $caseCount++

        $canonicalTimestamp = ConvertTo-CanonicalUtcTimestamp '2026-07-16T08:00:00+08:00'
        if ($canonicalTimestamp -ne '2026-07-16T00:00:00.0000000Z')
        {
            throw 'timestamp canonicalization self-test failed'
        }
        $caseCount++

        $currentCulture = [Threading.Thread]::CurrentThread.CurrentCulture
        try
        {
            [Threading.Thread]::CurrentThread.CurrentCulture = [Globalization.CultureInfo]::GetCultureInfo('tr-TR')
            $cultureHash = (Get-ChainState $directory).LastHash
        }
        finally
        {
            [Threading.Thread]::CurrentThread.CurrentCulture = $currentCulture
        }
        if ($cultureHash -ne (Get-ChainState $directory).LastHash)
        {
            throw 'culture-independent hash self-test failed'
        }
        $caseCount++

        $sampleLines = @(Get-Content -LiteralPath $samplesPath)
        $failureLines = @(Get-Content -LiteralPath (Join-Path $directory 'failures.jsonl'))
        $lineEndingHashes = @()
        foreach ($newline in @("`n", "`r`n"))
        {
            $copyRunId = New-RunId
            $copyDirectory = Get-RunDirectory $copyRunId
            $directories += $copyDirectory
            [IO.Directory]::CreateDirectory($copyDirectory) | Out-Null
            Write-JsonAtomic (Join-Path $copyDirectory 'manifest.json') ([ordered]@{
                runId = $copyRunId; harnessCommit = '0' * 40; startingCiRun = '0'
                startedAt = '2026-07-16T00:00:00Z'; plannedEndAt = '2026-07-23T00:00:00Z'
                durationHours = 168; cadenceSeconds = 900; venue = 'OKX'
                profile = 'gatew-okx-readonly-soak'; environment = 'SELF_TEST_NO_NETWORK'
                launcherSchemaVersion = $script:LauncherSchemaVersion
                evidenceSchemaVersion = $script:EvidenceSchemaV2
            })
            Write-Heartbeat $copyDirectory 'SELF_TEST' 'NO_PRIVATE_NETWORK_CALLED'
            [IO.File]::WriteAllText((Join-Path $copyDirectory 'samples.jsonl'), ($sampleLines -join $newline) + $newline, $script:Utf8NoBom)
            [IO.File]::WriteAllText((Join-Path $copyDirectory 'failures.jsonl'), ($failureLines -join $newline) + $newline, $script:Utf8NoBom)
            $copyVerification = Test-Evidence $copyDirectory
            $lineEndingHashes += $copyVerification.lastHash
        }
        if ($lineEndingHashes.Count -ne 2 -or $lineEndingHashes[0] -ne $lineEndingHashes[1])
        {
            throw 'line-ending-independent hash self-test failed'
        }
        $caseCount++

        $heartbeatPath = Join-Path $directory 'heartbeat.json'
        $heartbeatBytes = [IO.File]::ReadAllBytes($heartbeatPath)
        Write-Heartbeat $directory 'BLOCKED' 'LAUNCHER_OUTPUT_UNAVAILABLE'
        $terminalResumeRejected = $false
        try
        {
            Assert-RunResumable $directory
        }
        catch
        {
            $terminalResumeRejected = $_.Exception.Message -eq 'BLOCKED / TERMINAL_RUN_NOT_RESUMABLE'
        }
        $terminalRunLoopRejected = $false
        try
        {
            Assert-RunLoopAllowed $directory
        }
        catch
        {
            $terminalRunLoopRejected = $_.Exception.Message -eq 'BLOCKED / TERMINAL_RUN_NOT_RUNNABLE'
        }
        [IO.File]::WriteAllBytes($heartbeatPath, $heartbeatBytes)
        if (-not $terminalResumeRejected -or -not $terminalRunLoopRejected)
        {
            throw 'terminal v2 run was resumable or runnable'
        }
        $caseCount++

        $legacyRunId = New-RunId
        $legacyDirectory = Get-RunDirectory $legacyRunId
        $directories += $legacyDirectory
        [IO.Directory]::CreateDirectory($legacyDirectory) | Out-Null
        Write-JsonAtomic (Join-Path $legacyDirectory 'manifest.json') ([ordered]@{
            runId = $legacyRunId; harnessCommit = '0' * 40; startingCiRun = '0'
            startedAt = '2026-07-16T00:00:00Z'; plannedEndAt = '2026-07-23T00:00:00Z'
            durationHours = 168; cadenceSeconds = 900; venue = 'OKX'
            profile = 'gatew-okx-readonly-soak'; environment = 'SELF_TEST_NO_NETWORK'
            evidenceSchemaVersion = $script:EvidenceSchemaV1
        })
        Write-Heartbeat $legacyDirectory 'BLOCKED' 'SOAK_LAUNCHER_FAILED'
        $legacyHashInput = [ordered]@{
            sequence = 1L
            observedAt = '2026-07-16T00:00:00.0000000Z'
            durationMs = 0L
            resultStatus = 'HARD_FAILURE'
            reasonCode = 'SOAK_LAUNCHER_FAILED'
            httpStatusCategory = 'NOT_CALLED'
            permissionClassification = 'UNKNOWN'
            killSwitchObservedState = 'UNKNOWN'
            credentialAccessed = $false
            networkCalled = $false
            allowedEndpointCategory = 'NONE'
            traceId = 'gatew-soak-v1-self-test'
            previousRecordHash = $script:GenesisHash
        }
        $legacyRecord = [ordered]@{ }
        foreach ($key in $legacyHashInput.Keys)
        {
            $legacyRecord[$key] = $legacyHashInput[$key]
        }
        $legacyRecord.recordHash = Get-Sha256Text (ConvertTo-CompactJson $legacyHashInput)
        $legacyLine = ConvertTo-CompactJson $legacyRecord
        [IO.File]::WriteAllText((Join-Path $legacyDirectory 'samples.jsonl'), $legacyLine + [Environment]::NewLine, $script:Utf8NoBom)
        [IO.File]::WriteAllText((Join-Path $legacyDirectory 'failures.jsonl'), $legacyLine + [Environment]::NewLine, $script:Utf8NoBom)
        $legacyPaths = @('manifest.json', 'samples.jsonl', 'failures.jsonl', 'heartbeat.json')
        $legacyHashesBefore = @{ }
        foreach ($name in $legacyPaths)
        {
            $legacyHashesBefore[$name] = Get-Sha256File (Join-Path $legacyDirectory $name)
        }
        $legacyVerification = Test-Evidence $legacyDirectory
        if ($legacyVerification.evidenceSchemaVersion -ne $script:EvidenceSchemaV1 -or
                $legacyVerification.sampleCount -ne 1 -or $legacyVerification.validRealPassSamples -ne 0)
        {
            throw 'legacy v1 evidence compatibility self-test failed'
        }
        $caseCount++
        $legacyAppendRejected = $false
        try
        {
            Append-Sample $legacyDirectory $safeCycle | Out-Null
        }
        catch
        {
            $legacyAppendRejected = $_.Exception.Message -eq 'BLOCKED / LEGACY_EVIDENCE_NOT_APPENDABLE'
        }
        if (-not $legacyAppendRejected)
        {
            throw 'legacy evidence accepted a new append'
        }
        $caseCount++
        $legacyResumeRejected = $false
        try
        {
            Assert-RunResumable $legacyDirectory
        }
        catch
        {
            $legacyResumeRejected = $_.Exception.Message -eq 'BLOCKED / LEGACY_EVIDENCE_NOT_RESUMABLE'
        }
        $legacyRunLoopRejected = $false
        try
        {
            Assert-RunLoopAllowed $legacyDirectory
        }
        catch
        {
            $legacyRunLoopRejected = $_.Exception.Message -eq 'BLOCKED / LEGACY_EVIDENCE_NOT_RUNNABLE'
        }
        if (-not $legacyResumeRejected -or -not $legacyRunLoopRejected)
        {
            throw 'legacy blocked run was resumable or runnable'
        }
        $caseCount++
        foreach ($name in $legacyPaths)
        {
            if ($legacyHashesBefore[$name] -ne (Get-Sha256File (Join-Path $legacyDirectory $name)))
            {
                throw 'legacy blocked evidence was modified'
            }
        }
        $caseCount++

        return [pscustomobject]@{
            decision = 'PASS / SUPERVISOR_SELF_TEST'
            cases = $caseCount
            hashChain = 'PASS'
            tamperDetection = 'PASS'
            appendOnlySequence = 'PASS'
            resumePreservedExistingSamples = 'PASS'
            duplicateSequenceRejected = 'PASS'
            safeSuccessFixture = 'PASS'
            safeBlockedFixture = 'PASS'
            safeFailedFixture = 'PASS'
            fallbackFixture = 'PASS / LAUNCHER_OUTPUT_UNAVAILABLE / realCycleOutcomeProven=false'
            fallbackExcludedFromValidPass = 'PASS'
            unsafeFixtureRejections = $unsafeRejected
            temporalCanonicalization = 'PASS'
            localeIndependentHash = 'PASS'
            lineEndingIndependentHash = 'PASS'
            canonicalFixtureHash = $first.recordHash
            legacyV1HashVerification = 'PASS'
            legacyBlockedRunImmutable = 'PASS'
            terminalRunResumeRejected = 'PASS'
            terminalRunLoopRejected = 'PASS'
            missingCredentialRejected = 'PASS / API_KEY_REQUIRED'
            releaseIdentityValidation = 'PASS / ID+MANIFEST_HASH+SOURCE_COMMIT'
            invalidReleaseIdentityRejected = 'PASS'
            formalRuntimeGitDependency = 'REMOVED'
            linuxProductionAuthority = 'PASS / FORMAL_ROOT_CONTROL_ONLY'
            formalOfflineAcceptanceFixture = 'PASS / cycle1+2 read-only / cycle3 controlled failure / no credential / no network'
            offlineBootstrapFailureTaxonomy = 'PASS / CLOSED_FIXED_CODES'
            automaticEngageRecoveryFixture = 'PASS / killSwitchObservedState=ENGAGED'
            finalSummaryNotGenerated = (-not (Test-Path -LiteralPath (Join-Path $directory 'final-summary.json')))
            cleanupReleasedTemporaryDirectory = $true
            noPrivateNetworkCalled = $true
        }
    }
    finally
    {
        foreach ($candidate in @($directories | Select-Object -Unique))
        {
            if (-not (Test-Path -LiteralPath $candidate))
            {
                continue
            }
            $resolved = [IO.Path]::GetFullPath($candidate)
            $insideManagedRoot = $resolved.StartsWith(
                    $script:EvidenceRoot + [IO.Path]::DirectorySeparatorChar,
                    [StringComparison]::OrdinalIgnoreCase
            )
            if ($insideManagedRoot)
            {
                Remove-Item -LiteralPath $resolved -Recurse -Force
            }
        }
    }
}

try
{
    $result = switch ($Action)
    {
        'start' {
            if (Test-LinuxPlatform)
            {
                throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
            }
            Start-Soak
        }
        'status' {
            if (Test-LinuxPlatform)
            {
                throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
            }
            Show-Status
        }
        'resume' {
            if (Test-LinuxPlatform)
            {
                throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
            }
            Resume-Soak
        }
        'stop' {
            if (Test-LinuxPlatform)
            {
                throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
            }
            Request-Stop $false
        }
        'failure-stop' {
            if (Test-LinuxPlatform)
            {
                throw 'BLOCKED / FORMAL_ROOT_CONTROL_REQUIRED'
            }
            Request-Stop $true
        }
        'evidence-verify' {
            Test-Evidence (Get-RunDirectory $RunId)
        }
        'cleanup' {
            Cleanup-RunControlFiles
        }
        'run-loop' {
            Run-SoakLoop; [pscustomobject]@{ decision = 'SUPERVISOR_EXITED'; runId = $RunId }
        }
        'self-test' {
            Invoke-SelfTest
        }
    }
    if ($null -ne $result)
    {
        $result | ConvertTo-Json -Depth 8
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
        'FAIL / SUPERVISOR_INTERNAL_ERROR'
    }
    $failure = [ordered]@{ decision = $message; runId = $RunId }
    if ($Action -eq 'self-test')
    {
        $failure.selfTestDetail = $_.Exception.Message
    }
    [pscustomobject]$failure | ConvertTo-Json
    exit 2
}
