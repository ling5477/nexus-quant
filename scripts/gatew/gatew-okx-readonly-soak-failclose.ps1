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
$script:SafeCodePattern = '^[A-Z][A-Z0-9_]{1,95}$'
$script:StateRoot = '/var/lib/nexus-quant/gatew-soak'
$script:RuntimeRoot = '/run/nexus-quant/gatew-soak'
$script:WorkerHelperName = 'gatew-okx-readonly-soak.ps1'
$script:SystemdRoot = '/etc/systemd/system'
$script:WorkerTemplate = 'nq-gatew-soak@.service'
$script:LinuxRuntimeUser = 'nqgatew'
$script:LinuxRuntimeGroup = 'nqgatew'
$script:SystemctlPath = '/usr/bin/systemctl'
$script:PowerShellPath = '/usr/bin/pwsh'
$script:JavaPath = '/usr/bin/java'
$script:ChownPath = '/usr/bin/chown'
$script:ChmodPath = '/usr/bin/chmod'
$script:StatPath = '/usr/bin/stat'
$script:ReadlinkPath = '/usr/bin/readlink'
$script:LnPath = '/usr/bin/ln'
$script:AllowedTransitions = @{
    PREPARING = @('STARTING', 'BLOCKED', 'OPERATOR_STOPPING')
    STARTING = @('RUNNING', 'FAILURE_STOPPING', 'OPERATOR_STOPPING', 'BLOCKED')
    RUNNING = @('FAILURE_STOPPING', 'OPERATOR_STOPPING', 'COMPLETED', 'BLOCKED')
    FAILURE_STOPPING = @('FAILURE_STOPPED', 'BLOCKED')
    OPERATOR_STOPPING = @('OPERATOR_STOPPED', 'BLOCKED')
    FAILURE_STOPPED = @()
    OPERATOR_STOPPED = @()
    COMPLETED = @()
    BLOCKED = @()
}
$script:SafeRecoveryStatuses = @('ENGAGE_NOT_REQUIRED_ALREADY_ENGAGED', 'ENGAGE_SUCCEEDED')
$script:RecoveryStatuses = @(
    'DB_LOCALITY_VERIFIED', 'OFFLINE_FIXTURE_DISENGAGED',
    'ENGAGE_NOT_REQUIRED_ALREADY_ENGAGED', 'ENGAGE_SUCCEEDED',
    'ENGAGE_FAILED_DB_ENV_INVALID', 'ENGAGE_FAILED_DB_AUTHENTICATION', 'ENGAGE_FAILED_DB_UNREACHABLE',
    'ENGAGE_FAILED_DB_CONTEXT_INIT', 'ENGAGE_FAILED_DB_DRIVER_INIT',
    'ENGAGE_FAILED_DB_DATASOURCE_CONFIG', 'ENGAGE_FAILED_DB_TEMPLATE_INIT', 'ENGAGE_FAILED_DB_LOCALITY',
    'ENGAGE_FAILED_DB_MIGRATION_LOAD', 'ENGAGE_FAILED_DB_MIGRATION_EXECUTE',
    'ENGAGE_FAILED_DB_MIGRATION_VALIDATE', 'ENGAGE_FAILED_DB_MIGRATION_HISTORY',
    'ENGAGE_FAILED_DB_SEED_INITIAL_STATE', 'ENGAGE_FAILED_DB_SEED_UPDATE',
    'ENGAGE_FAILED_DB_SEED_EVENT', 'ENGAGE_FAILED_DB_SEED_TRANSACTION',
    'ENGAGE_FAILED_WRITE', 'ENGAGE_FAILED_READBACK', 'ENGAGE_STATUS_UNKNOWN'
)

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
$script:ReleaseVerifierName = 'verify-gatew-release.ps1'

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
    if (-not (Test-LinuxPlatform) -or [Environment]::UserName -ne 'root')
    {
        throw 'BLOCKED / ROOT_FAILCLOSE_REQUIRED'
    }
}

function ConvertTo-CompactJson
{
    param([Parameter(Mandatory = $true)]$Value)
    return ($Value | ConvertTo-Json -Compress -Depth 16)
}

function ConvertTo-TrimmedOutput
{
    param([AllowNull()][object[]]$Value)
    return (($Value -join [Environment]::NewLine).Trim())
}

function Invoke-Native
{
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [AllowEmptyCollection()][string[]]$Arguments = @(),
        [switch]$AllowFailure
    )

    if (-not (Test-Path -LiteralPath $FilePath -PathType Leaf))
    {
        if ($AllowFailure)
        {
            return [pscustomobject]@{ ExitCode = 127; Lines = @() }
        }
        throw 'BLOCKED / REQUIRED_NATIVE_TOOL_MISSING'
    }
    $lines = @(& $FilePath @Arguments 2> $null)
    $exitCodeValue = [int]$LASTEXITCODE
    if (-not $AllowFailure -and $exitCodeValue -ne 0)
    {
        throw 'FAIL / NATIVE_COMMAND_FAILED'
    }
    return [pscustomobject]@{ ExitCode = $exitCodeValue; Lines = @($lines) }
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
        throw 'BLOCKED / REQUIRED_CONTROL_FILE_MISSING'
    }
    Assert-PathComponentsNoSymlink $Path
    return ConvertFrom-JsonPreservingTimestamps (Get-Content -LiteralPath $Path -Raw)
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
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text
    )

    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent -PathType Container))
    {
        throw 'BLOCKED / CONTROL_DIRECTORY_INVALID'
    }
    Assert-PathComponentsNoSymlink $parent
    if (Test-Path -LiteralPath $Path)
    {
        throw 'BLOCKED / IMMUTABLE_CONTROL_EXISTS'
    }
    $temporary = Join-Path $parent ('.create-' + [Guid]::NewGuid().ToString('N'))
    $bytes = $script:Utf8NoBom.GetBytes($Text)
    try
    {
        Write-BytesFlushed $temporary $bytes
        if (Test-LinuxPlatform)
        {
            Invoke-Native $script:ChmodPath @('600', '--', $temporary) | Out-Null
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
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value
    )
    Write-TextCreateOnce $Path (ConvertTo-CompactJson $Value)
}

function Write-JsonReplaceAtomic
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value
    )

    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent -PathType Container))
    {
        throw 'BLOCKED / CONTROL_DIRECTORY_INVALID'
    }
    Assert-PathComponentsNoSymlink $parent
    if (Test-Path -LiteralPath $Path)
    {
        Assert-NoSymlink $Path
    }
    $temporary = Join-Path $parent ('.replace-' + [Guid]::NewGuid().ToString('N'))
    $bytes = $script:Utf8NoBom.GetBytes((ConvertTo-CompactJson $Value))
    try
    {
        Write-BytesFlushed $temporary $bytes
        if (Test-LinuxPlatform)
        {
            Invoke-Native $script:ChmodPath @('600', '--', $temporary) | Out-Null
        }
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

function Get-EvidenceRoot
{
    param([Parameter(Mandatory = $true)][string]$Value)
    return "$( Get-RunRoot $Value )/evidence"
}

function Get-RuntimeRoot
{
    param([Parameter(Mandatory = $true)][string]$Value)
    Assert-RunId $Value
    return "$( $script:RuntimeRoot )/$Value"
}

function Get-WorkerUnitName
{
    param([Parameter(Mandatory = $true)][string]$Value)
    Assert-RunId $Value
    return "nq-gatew-soak@$Value.service"
}

function Enter-FinalizerLock
{
    $path = "$( Get-ControlRoot $RunId )/failclose.lock"
    Assert-PathComponentsNoSymlink (Split-Path -Parent $path)
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
        if (Test-LinuxPlatform)
        {
            Set-OwnerMode $path 'root:root' '600'
        }
        return $stream
    }
    catch
    {
        throw 'FAIL / FAILCLOSE_LOCK_UNAVAILABLE'
    }
}

function Assert-NoSymlink
{
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path))
    {
        throw 'BLOCKED / PATH_CONTRACT_INVALID'
    }
    $item = Get-Item -LiteralPath $Path -Force
    if ($null -ne $item.LinkType -or $item.Attributes.ToString() -match 'ReparsePoint')
    {
        throw 'BLOCKED / SYMLINK_PATH_FORBIDDEN'
    }
}

function Assert-PathComponentsNoSymlink
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$AllowMissingTail
    )

    $normalized = [IO.Path]::GetFullPath($Path)
    $pathRoot = [IO.Path]::GetPathRoot($normalized)
    if ( [string]::IsNullOrWhiteSpace($pathRoot))
    {
        throw 'BLOCKED / PATH_CONTRACT_INVALID'
    }
    $current = $pathRoot
    if (Test-Path -LiteralPath $current)
    {
        Assert-NoSymlink $current
    }
    $relative = $normalized.Substring($pathRoot.Length)
    foreach ($segment in @($relative -split '[\\/]' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }))
    {
        $current = Join-Path $current $segment
        if (-not (Test-Path -LiteralPath $current))
        {
            if ($AllowMissingTail)
            {
                return
            }
            throw 'BLOCKED / PATH_CONTRACT_INVALID'
        }
        Assert-NoSymlink $current
    }
}

function Get-RealPath
{
    param([Parameter(Mandatory = $true)][string]$Path)

    Assert-PathComponentsNoSymlink $Path
    if (Test-LinuxPlatform)
    {
        $resolved = Invoke-Native $script:ReadlinkPath @('-f', '--', $Path) -AllowFailure
        $value = ConvertTo-TrimmedOutput $resolved.Lines
        if ($resolved.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($value))
        {
            throw 'BLOCKED / PATH_CONTRACT_INVALID'
        }
        return [IO.Path]::GetFullPath($value)
    }
    return [IO.Path]::GetFullPath((Get-Item -LiteralPath $Path -Force).FullName)
}

function Get-PosixMetadata
{
    param([Parameter(Mandatory = $true)][string]$Path)

    Assert-NoSymlink $Path
    $result = Invoke-Native $script:StatPath @('-c', '%F|%a|%U|%G', '--', $Path)
    $parts = (ConvertTo-TrimmedOutput $result.Lines).Split('|')
    if ($parts.Count -ne 4)
    {
        throw 'BLOCKED / PATH_CONTRACT_INVALID'
    }
    return [pscustomobject]@{ Type = $parts[0]; Mode = $parts[1]; Owner = $parts[2]; Group = $parts[3] }
}

function Assert-PosixContract
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$ExpectedType,
        [Parameter(Mandatory = $true)][string]$ExpectedMode,
        [Parameter(Mandatory = $true)][string]$ExpectedOwner,
        [Parameter(Mandatory = $true)][string]$ExpectedGroup
    )

    $metadata = Get-PosixMetadata $Path
    if ($metadata.Type -notlike "*$ExpectedType*" -or $metadata.Mode -ne $ExpectedMode -or
            $metadata.Owner -ne $ExpectedOwner -or $metadata.Group -ne $ExpectedGroup)
    {
        throw 'BLOCKED / PATH_OWNERSHIP_CONTRACT_INVALID'
    }
}

function Set-OwnerMode
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$OwnerGroup,
        [Parameter(Mandatory = $true)][string]$Mode
    )

    Assert-NoSymlink $Path
    Invoke-Native $script:ChownPath @('--', $OwnerGroup, $Path) | Out-Null
    Invoke-Native $script:ChmodPath @($Mode, '--', $Path) | Out-Null
}

function Assert-RunPathContract
{
    param([Parameter(Mandatory = $true)][string]$Value)

    $runRoot = Get-RunRoot $Value
    $controlRoot = Get-ControlRoot $Value
    $evidenceRoot = Get-EvidenceRoot $Value
    Assert-PathBelowRoot $script:StateRoot $runRoot
    Assert-PathBelowRoot $runRoot $controlRoot
    Assert-PathBelowRoot $runRoot $evidenceRoot
    Assert-PosixContract $script:StateRoot 'directory' '710' 'root' $script:LinuxRuntimeGroup
    Assert-PosixContract $runRoot 'directory' '710' 'root' $script:LinuxRuntimeGroup
    Assert-PosixContract $controlRoot 'directory' '710' 'root' $script:LinuxRuntimeGroup
    Assert-PosixContract $evidenceRoot 'directory' '700' `
        $script:LinuxRuntimeUser $script:LinuxRuntimeGroup
}

function Get-Sha256File
{
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Assert-PathBelowRoot
{
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $normalizedRoot = [IO.Path]::GetFullPath($Root).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar
    )
    $normalizedPath = [IO.Path]::GetFullPath($Path)
    $comparison = if (Test-LinuxPlatform)
    {
        [StringComparison]::Ordinal
    }
    else
    {
        [StringComparison]::OrdinalIgnoreCase
    }
    if (-not $normalizedPath.StartsWith(
            $normalizedRoot + [IO.Path]::DirectorySeparatorChar,
            $comparison
    ))
    {
        throw 'BLOCKED / PATH_CONTRACT_INVALID'
    }
    Assert-PathComponentsNoSymlink $normalizedRoot
    Assert-PathComponentsNoSymlink $normalizedPath -AllowMissingTail
    if (Test-Path -LiteralPath $normalizedPath)
    {
        $realRoot = (Get-RealPath $normalizedRoot).TrimEnd(
                [IO.Path]::DirectorySeparatorChar,
                [IO.Path]::AltDirectorySeparatorChar
        )
        $realPath = Get-RealPath $normalizedPath
        if (-not $realPath.StartsWith(
                $realRoot + [IO.Path]::DirectorySeparatorChar,
                $comparison
        ))
        {
            throw 'BLOCKED / PATH_CONTRACT_INVALID'
        }
    }
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
        -ExpectedReleaseId $ExpectedReleaseId -ExpectedManifestSha256 $ExpectedManifestSha256 2> $null)
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

function Get-ReleaseLauncherClassPath
{
    foreach ($path in @(
        "$( $script:ReleaseRoot )/launcher/test-support.jar",
        "$( $script:ReleaseRoot )/launcher/modules",
        "$( $script:ReleaseRoot )/launcher/lib"
    ))
    {
        if (-not (Test-Path -LiteralPath $path))
        {
            throw 'BLOCKED / RELEASE_LAUNCHER_MISSING'
        }
        Assert-PathComponentsNoSymlink $path
    }
    return @(
        "$( $script:ReleaseRoot )/launcher/test-support.jar",
        "$( $script:ReleaseRoot )/launcher/modules/*",
        "$( $script:ReleaseRoot )/launcher/lib/*"
    ) -join [IO.Path]::PathSeparator
}

function Assert-ExactFields
{
    param(
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string[]]$Expected
    )

    if ((@($Value.PSObject.Properties.Name) -join '|') -ne ($Expected -join '|'))
    {
        throw 'BLOCKED / CLOSED_SCHEMA_INVALID'
    }
}

function Assert-LiteralValue
{
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    if ($Value.IndexOf([char]0) -ge 0 -or $Value -match "[`r`n]" -or
            $Value -match '\$\{?[A-Za-z_][A-Za-z0-9_]*\}?' -or
            $Value -match '%[A-Za-z_][A-Za-z0-9_]*%' -or $Value -match '\$\(' -or $Value -match '`')
    {
        throw 'BLOCKED / CONFIG_VALUE_NOT_LITERAL'
    }
}

function Assert-NoProviderMaterial
{
    foreach ($name in @(
        'NQ_OKX_API_KEY', 'NQ_OKX_API_SECRET', 'NQ_OKX_API_PASSPHRASE',
        'NQ_OKX_REAL_API_KEY', 'NQ_OKX_REAL_API_SECRET', 'NQ_OKX_REAL_API_PASSPHRASE',
        'NQ_ACCOUNT_CREDENTIALS_MASTER_KEY', 'NQ_GATEW_SOAK_OWNER_ID',
        'NQ_GATEW_SOAK_ACCOUNT_ID', 'NQ_GATEW_SOAK_CURRENCIES'
    ))
    {
        if (-not [string]::IsNullOrWhiteSpace(
                [Environment]::GetEnvironmentVariable($name, 'Process')
        ))
        {
            throw 'BLOCKED / FAILCLOSE_PROVIDER_MATERIAL_FORBIDDEN'
        }
    }
    $credentialRoot = [Environment]::GetEnvironmentVariable('CREDENTIALS_DIRECTORY', 'Process')
    if ( [string]::IsNullOrWhiteSpace($credentialRoot))
    {
        throw 'BLOCKED / FAILCLOSE_CREDENTIAL_MISSING'
    }
    $normalized = [IO.Path]::GetFullPath($credentialRoot)
    $expectedCredentialRoot = [IO.Path]::GetFullPath(
            "/run/credentials/nq-gatew-soak-failclose@$RunId.service"
    )
    if ($normalized -cne $expectedCredentialRoot -or
            -not (Test-Path -LiteralPath $normalized -PathType Container))
    {
        throw 'BLOCKED / FAILCLOSE_CREDENTIAL_DIRECTORY_INVALID'
    }
    Assert-PathComponentsNoSymlink $normalized
    $entries = @(Get-ChildItem -LiteralPath $normalized -Force)
    if ($entries.Count -ne 1 -or $entries[0].PSIsContainer -or $entries[0].Name -ne 'db-password')
    {
        throw 'BLOCKED / FAILCLOSE_CREDENTIAL_SCOPE_INVALID'
    }
    Assert-PathComponentsNoSymlink $entries[0].FullName
}

function Assert-FrozenConfig
{
    param([Parameter(Mandatory = $true)]$Config)

    Assert-ExactFields $Config @(
        'schemaVersion', 'runId', 'runMode', 'databaseUrl', 'databaseUser', 'databaseSchema',
        'offlineHeartbeatSeconds', 'releaseId', 'sourceCommit', 'sourceTreeMode',
        'releaseManifestSha256', 'releaseRoot', 'startingCiRun', 'workerUnit', 'failCloseUnit',
        'acceptanceClockStarted', 'acceptanceStartAt', 'plannedAcceptanceAt', 'preparedAt'
    )
    if ([string]$Config.schemaVersion -ne 'gatew-soak-frozen-config-v3' -or
            [string]$Config.runId -ne $RunId -or
            [string]$Config.runMode -notin @('REAL_READONLY_SOAK', 'OFFLINE_ISOLATED_ACCEPTANCE') -or
            [string]$Config.releaseId -cnotmatch '^(?:[a-f0-9]{40}|candidate-[a-f0-9]{12}-[a-f0-9]{16}-[0-9]{8}T[0-9]{6}Z)$' -or
            [string]$Config.sourceCommit -cnotmatch '^[a-f0-9]{40}$' -or
            [string]$Config.sourceTreeMode -notin @('CANDIDATE', 'EXACT_COMMIT') -or
            [string]$Config.releaseManifestSha256 -cnotmatch '^[a-f0-9]{64}$' -or
            [string]$Config.startingCiRun -cnotmatch '^[1-9][0-9]{0,19}$' -or
            [bool]$Config.acceptanceClockStarted -or
            $null -ne $Config.acceptanceStartAt -or $null -ne $Config.plannedAcceptanceAt)
    {
        throw 'BLOCKED / FROZEN_CONFIG_INVALID'
    }
    if ([string]$Config.runMode -eq 'REAL_READONLY_SOAK' -and
            ([string]$Config.sourceTreeMode -ne 'EXACT_COMMIT' -or
                    [string]$Config.releaseId -cne [string]$Config.sourceCommit))
    {
        throw 'BLOCKED / REAL_RUN_EXACT_RELEASE_REQUIRED'
    }
    foreach ($value in @($Config.databaseUrl, $Config.databaseUser, $Config.databaseSchema))
    {
        Assert-LiteralValue ([string]$value)
    }
    if ([string]$Config.databaseUrl -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost):[0-9]{1,5}/[A-Za-z0-9_]*(gatew|soak)[A-Za-z0-9_]*$' -or
            [string]$Config.databaseUser -notmatch '^[A-Za-z_][A-Za-z0-9_-]{0,62}$' -or
            ([string]$Config.runMode -eq 'REAL_READONLY_SOAK' -and [string]$Config.databaseSchema -ne 'public') -or
            ([string]$Config.runMode -eq 'OFFLINE_ISOLATED_ACCEPTANCE' -and
                    [string]$Config.databaseSchema -notmatch '^gatew_offline_[a-f0-9]{8}$'))
    {
        throw 'BLOCKED / FROZEN_DATABASE_CONFIG_INVALID'
    }
    $environmentContract = @{
        NQ_GATEW_RUN_MODE = [string]$Config.runMode
        NQ_GATEW_SOAK_DB_URL = [string]$Config.databaseUrl
        NQ_GATEW_SOAK_DB_USER = [string]$Config.databaseUser
        NQ_GATEW_SOAK_DB_SCHEMA = [string]$Config.databaseSchema
        NQ_GATEW_SECRET_SOURCE = 'SYSTEMD_CREDENTIALS'
        NQ_GATEW_FORMAL_SYSTEMD = 'true'
        NQ_GATEW_RELEASE_ROOT = [string]$Config.releaseRoot
        NQ_GATEW_RELEASE_ID = [string]$Config.releaseId
        NQ_GATEW_RELEASE_MANIFEST_SHA256 = [string]$Config.releaseManifestSha256
    }
    foreach ($name in $environmentContract.Keys)
    {
        if ([Environment]::GetEnvironmentVariable($name, 'Process') -cne $environmentContract[$name])
        {
            throw 'BLOCKED / FAILCLOSE_ENVIRONMENT_CONTRACT_INVALID'
        }
    }
    if (-not [string]::IsNullOrWhiteSpace(
            [Environment]::GetEnvironmentVariable('NQ_GATEW_SOAK_DB_PASSWORD', 'Process')
    ))
    {
        throw 'BLOCKED / FAILCLOSE_SECRET_ENV_FORBIDDEN'
    }
    $identity = Get-ReleaseIdentity ([string]$Config.releaseId) ([string]$Config.releaseManifestSha256)
    if ([string]$identity.sourceCommit -cne [string]$Config.sourceCommit -or
            [string]$identity.sourceTreeMode -cne [string]$Config.sourceTreeMode -or
            [IO.Path]::GetFullPath([string]$identity.releaseRoot) -cne
                    [IO.Path]::GetFullPath([string]$Config.releaseRoot))
    {
        throw 'BLOCKED / FROZEN_RELEASE_BINDING_CHANGED'
    }
    Assert-NoProviderMaterial
}

function Get-AcceptanceClockProjection
{
    $path = "$( Get-ControlRoot $RunId )/acceptance-clock-start.json"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        return [pscustomobject]@{
            acceptanceClockStarted = $false
            acceptanceStartAt = $null
            plannedAcceptanceAt = $null
        }
    }
    Assert-PosixContract $path 'regular file' '640' 'root' $script:LinuxRuntimeGroup
    $clock = Read-JsonFile $path
    Assert-ExactFields $clock @(
        'schemaVersion', 'runId', 'firstValidConfigPassAt', 'firstValidBalancePassAt',
        'freshSshVerificationAt', 'mainPid', 'sameMainPid', 'heartbeatAdvanced',
        'hashChainValid', 'forbiddenEndpointCount', 'secretExposureCount',
        'acceptanceStartAt', 'plannedAcceptanceAt', 'acceptanceClockStarted'
    )
    $configAt = [DateTimeOffset]::MinValue
    $balanceAt = [DateTimeOffset]::MinValue
    $freshAt = [DateTimeOffset]::MinValue
    $startAt = [DateTimeOffset]::MinValue
    $plannedAt = [DateTimeOffset]::MinValue
    $schemaInvalid = [string]$clock.schemaVersion -ne 'gatew-soak-acceptance-clock-v1' -or
            [string]$clock.runId -ne $RunId -or [long]$clock.mainPid -le 0 -or
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
    if ($schemaInvalid -or $startAt -ne $latestPrerequisite -or $plannedAt -ne $startAt.AddHours(168))
    {
        throw 'BLOCKED / ACCEPTANCE_CLOCK_RECORD_INVALID'
    }
    return [pscustomobject]@{
        acceptanceClockStarted = $true
        acceptanceStartAt = [string]$clock.acceptanceStartAt
        plannedAcceptanceAt = [string]$clock.plannedAcceptanceAt
    }
}

function New-RecoveryFailure
{
    param([Parameter(Mandatory = $true)][string]$Status)
    return [pscustomobject][ordered]@{
        schemaVersion = 'gatew-soak-failclose-v1'
        action = 'engage'
        observedAt = (Get-UtcNow).ToString('o')
        recoveryStatus = $Status
        killSwitchObservedState = 'UNKNOWN'
        killSwitchVersion = 0L
        credentialAccessed = $false
        networkCalled = $false
    }
}

function Assert-RecoveryResult
{
    param(
        [Parameter(Mandatory = $true)]$Result,
        [Parameter(Mandatory = $true)][string]$ExpectedAction
    )

    Assert-ExactFields $Result @(
        'schemaVersion', 'action', 'observedAt', 'recoveryStatus', 'killSwitchObservedState',
        'killSwitchVersion', 'credentialAccessed', 'networkCalled'
    )
    if ([string]$Result.schemaVersion -ne 'gatew-soak-failclose-v1' -or
            [string]$Result.action -ne $ExpectedAction -or
            [string]$Result.recoveryStatus -notin $script:RecoveryStatuses -or
            [string]$Result.killSwitchObservedState -notin @('UNKNOWN', 'DISENGAGED', 'ENGAGED') -or
            $Result.credentialAccessed -isnot [bool] -or [bool]$Result.credentialAccessed -or
            $Result.networkCalled -isnot [bool] -or [bool]$Result.networkCalled -or
            [long]$Result.killSwitchVersion -lt 0)
    {
        throw 'FAIL / RECOVERY_RESULT_SCHEMA_INVALID'
    }
}

function Invoke-RecoveryLauncher
{
    param(
        [Parameter(Mandatory = $true)][ValidateSet('verify', 'engage')][string]$RecoveryAction,
        [Parameter(Mandatory = $true)][int]$Attempt,
        [Parameter(Mandatory = $true)]$Config
    )

    $path = "$( Get-ControlRoot $RunId )/.recovery-$RecoveryAction-$PID-$Attempt.json"
    if (Test-Path -LiteralPath $path)
    {
        Remove-Item -LiteralPath $path -Force
    }
    if (-not (Test-Path -LiteralPath $script:JavaPath -PathType Leaf))
    {
        return New-RecoveryFailure 'ENGAGE_STATUS_UNKNOWN'
    }
    $classPath = Get-ReleaseLauncherClassPath
    $arguments = @(
        '-Xms16m', '-Xmx256m', '-Dfile.encoding=UTF-8',
        '-Dnq.gatew.soakFailClose.required=true',
        "-Dnq.gatew.soakFailClose.action=$RecoveryAction",
        "-Dnq.gatew.soakFailClose.resultFile=$path",
        "-Dnq.gatew.soakFailClose.runId=$RunId",
        '-cp', $classPath,
        'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakFailCloseTest'
    )
    $launcherResult = $null
    try
    {
        $null = & $script:JavaPath @arguments 2> $null
    }
    catch
    {
        # Raw Java/JDBC output is never evidence; only the closed launcher result is accepted.
    }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        $launcherResult = New-RecoveryFailure 'ENGAGE_STATUS_UNKNOWN'
    }
    else
    {
        try
        {
            $result = Read-JsonFile $path
            Assert-RecoveryResult $result $RecoveryAction
            $launcherResult = $result
        }
        catch
        {
            $launcherResult = New-RecoveryFailure 'ENGAGE_STATUS_UNKNOWN'
        }
    }
    if (Test-Path -LiteralPath $path)
    {
        Remove-Item -LiteralPath $path -Force
    }
    return $launcherResult
}

function Invoke-BoundedRecovery
{
    param([Parameter(Mandatory = $true)]$Config)

    $verify = Invoke-RecoveryLauncher 'verify' 0 $Config
    if ([string]$verify.recoveryStatus -ne 'DB_LOCALITY_VERIFIED' -or
            [string]$verify.killSwitchObservedState -notin @('DISENGAGED', 'ENGAGED'))
    {
        return New-RecoveryFailure ([string]$verify.recoveryStatus)
    }
    $last = New-RecoveryFailure 'ENGAGE_STATUS_UNKNOWN'
    foreach ($attempt in 1..3)
    {
        $last = Invoke-RecoveryLauncher 'engage' $attempt $Config
        if ([string]$last.recoveryStatus -in $script:SafeRecoveryStatuses -and
                [string]$last.killSwitchObservedState -eq 'ENGAGED')
        {
            return $last
        }
        if ($attempt -lt 3)
        {
            Start-Sleep -Milliseconds (250 * $attempt)
        }
    }
    return $last
}

function Get-UnitState
{
    $result = Invoke-Native $script:SystemctlPath @(
        'show', (Get-WorkerUnitName $RunId), '--no-pager',
        '--property=LoadState,ActiveState,SubState,MainPID,FragmentPath,User,Group'
    ) -AllowFailure
    if ($result.ExitCode -ne 0)
    {
        throw 'FAIL / SYSTEMD_STATE_UNAVAILABLE'
    }
    $values = @{ }
    foreach ($lineValue in $result.Lines)
    {
        $line = [string]$lineValue
        $index = $line.IndexOf('=')
        if ($index -gt 0)
        {
            $values[$line.Substring(0, $index)] = $line.Substring($index + 1)
        }
    }
    foreach ($required in @('LoadState', 'ActiveState', 'SubState', 'MainPID', 'FragmentPath', 'User', 'Group'))
    {
        if (-not $values.ContainsKey($required))
        {
            $values[$required] = ''
        }
    }
    $mainPid = 0L
    [long]::TryParse([string]$values.MainPID, [ref]$mainPid) | Out-Null
    return [pscustomobject]@{
        LoadState = [string]$values.LoadState
        ActiveState = [string]$values.ActiveState
        SubState = [string]$values.SubState
        MainPID = $mainPid
        FragmentPath = [string]$values.FragmentPath
        User = [string]$values.User
        Group = [string]$values.Group
    }
}

function Get-ResidualWorkerProcesses
{
    $processIds = @()
    foreach ($directory in Get-ChildItem -LiteralPath '/proc' -Directory -ErrorAction SilentlyContinue)
    {
        if ($directory.Name -notmatch '^[0-9]+$')
        {
            continue
        }
        try
        {
            $command = [Text.Encoding]::UTF8.GetString(
                    [IO.File]::ReadAllBytes((Join-Path $directory.FullName 'cmdline'))
            )
            if ($command -match [regex]::Escape($script:WorkerHelperName) -and
                    $command -match [regex]::Escape($RunId) -and $command -match 'run-loop')
            {
                $processIds += [long]$directory.Name
            }
        }
        catch
        {
            # /proc entry may disappear while the finalizer enumerates exact worker identity.
        }
    }
    return @($processIds)
}

function Test-WorkerCleanupComplete
{
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][int]$ResidualProcessCount,
        [Parameter(Mandatory = $true)][bool]$RuntimeDirectoryPresent
    )

    if ($State.ActiveState -ne 'inactive' -or $State.MainPID -ne 0)
    {
        return $false
    }
    if ($ResidualProcessCount -ne 0 -or $RuntimeDirectoryPresent)
    {
        return $false
    }
    return $true
}

function Stop-AndObserveWorker
{
    $before = Get-UnitState
    if ($before.FragmentPath -ne "$( $script:SystemdRoot )/$( $script:WorkerTemplate )" -or
            $before.User -ne $script:LinuxRuntimeUser -or $before.Group -ne $script:LinuxRuntimeGroup)
    {
        throw 'FAIL / FORMAL_UNIT_CONTRACT_INVALID'
    }
    $lastKnown = [long]$before.MainPID
    if ($before.ActiveState -notin @('inactive', 'failed'))
    {
        Invoke-Native $script:SystemctlPath @('stop', (Get-WorkerUnitName $RunId)) -AllowFailure | Out-Null
    }
    $deadline = (Get-UtcNow).AddSeconds(30)
    do
    {
        $state = Get-UnitState
        if ($state.MainPID -eq 0 -and $state.ActiveState -in @('inactive', 'failed'))
        {
            break
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-UtcNow) -lt $deadline)
    if ($state.ActiveState -eq 'failed' -and $state.MainPID -eq 0)
    {
        Invoke-Native $script:SystemctlPath @('reset-failed', (Get-WorkerUnitName $RunId)) -AllowFailure | Out-Null
    }
    $cleanupDeadline = (Get-UtcNow).AddSeconds(30)
    do
    {
        $state = Get-UnitState
        $residual = @(Get-ResidualWorkerProcesses)
        $runtimeDirectoryPresent = Test-Path -LiteralPath (Get-RuntimeRoot $RunId)
        if (Test-WorkerCleanupComplete $state $residual.Count $runtimeDirectoryPresent)
        {
            break
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-UtcNow) -lt $cleanupDeadline)
    return [pscustomobject]@{
        LastKnownMainPid = $lastKnown
        ActiveState = $state.ActiveState
        MainPID = [long]$state.MainPID
        ResidualProcessCount = $residual.Count
        RuntimeDirectoryPresent = $runtimeDirectoryPresent
        Safe = Test-WorkerCleanupComplete $state $residual.Count $runtimeDirectoryPresent
    }
}

function Get-HistoricalEvidenceSnapshot
{
    $roots = @('/opt/nexus-quant/gatew-soak/evidence/gatew-okx-readonly-soak', $script:StateRoot)
    $records = @()
    foreach ($root in $roots)
    {
        if (-not (Test-Path -LiteralPath $root -PathType Container))
        {
            continue
        }
        foreach ($file in Get-ChildItem -LiteralPath $root -File -Recurse -ErrorAction Stop)
        {
            if ($file.FullName -like "$( Get-RunRoot $RunId )*")
            {
                continue
            }
            $relative = $file.FullName.Substring($root.Length).TrimStart('/', '\')
            $records += [pscustomobject][ordered]@{
                root = $root
                path = $relative.Replace('\', '/')
                sha256 = Get-Sha256File $file.FullName
            }
        }
    }
    return @($records | Sort-Object root, path)
}

function Test-HistoricalEvidenceImmutable
{
    $before = @(Read-JsonFile "$( Get-ControlRoot $RunId )/historical-evidence-hashes.json")
    $after = @(Get-HistoricalEvidenceSnapshot)
    return (ConvertTo-CompactJson $before) -ceq (ConvertTo-CompactJson $after)
}

function Get-EvidenceState
{
    $heartbeat = Read-JsonFile "$( Get-EvidenceRoot $RunId )/heartbeat.json"
    $lastHeartbeat = [long]$heartbeat.lastSequence
    $lastSuccessful = 0L
    $lastSequence = 0L
    $controlledFailureProven = $false
    $samplesPath = "$( Get-EvidenceRoot $RunId )/samples.jsonl"
    Assert-PathComponentsNoSymlink $samplesPath
    foreach ($line in Get-Content -LiteralPath $samplesPath)
    {
        if ( [string]::IsNullOrWhiteSpace($line))
        {
            continue
        }
        $sample = ConvertFrom-JsonPreservingTimestamps $line
        $sequence = [long]$sample.sequence
        if ($sequence -le $lastSequence)
        {
            throw 'FAIL / EVIDENCE_SEQUENCE_INVALID'
        }
        $lastSequence = $sequence
        if ([string]$sample.resultStatus -eq 'PASSED_READ_ONLY')
        {
            $lastSuccessful = $sequence
        }
        if ($sequence -eq 3 -and [string]$sample.resultStatus -eq 'FAILED' -and
                [string]$sample.reasonCode -eq 'CONTROLLED_OFFLINE_CYCLE_3_FAILURE' -and
                -not [bool]$sample.credentialAccessed -and -not [bool]$sample.networkCalled)
        {
            $controlledFailureProven = $true
        }
    }
    return [pscustomobject]@{
        LastSuccessfulCycleSequence = $lastSuccessful
        LastHeartbeatSequence = $lastHeartbeat
        LastSampleSequence = $lastSequence
        ControlledOfflineFailureProven = $controlledFailureProven
    }
}

function Read-OptionalJson
{
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        return $null
    }
    return Read-JsonFile $Path
}

function Assert-ExitFact
{
    param([AllowNull()]$ExitFact)

    if ($null -eq $ExitFact)
    {
        throw 'BLOCKED / SYSTEMD_EXIT_FACT_MISSING'
    }
    Assert-ExactFields $ExitFact @(
        'schemaVersion', 'runId', 'serviceResult', 'exitCode', 'exitStatus',
        'lastKnownMainPid', 'recordedAt'
    )
    $recordedAt = [DateTimeOffset]::MinValue
    if ([string]$ExitFact.schemaVersion -ne 'gatew-soak-exit-fact-v1' -or
            [string]$ExitFact.runId -ne $RunId -or
            [string]$ExitFact.serviceResult -notin @(
                'success', 'exit-code', 'signal', 'core-dump', 'watchdog', 'start-limit-hit',
                'timeout', 'resources', 'protocol', 'oom-kill'
            ) -or [string]$ExitFact.exitCode -notin @('exited', 'killed', 'dumped', '') -or
            [string]$ExitFact.exitStatus -notmatch '^[A-Za-z0-9_-]{0,32}$' -or
            [long]$ExitFact.lastKnownMainPid -lt 0 -or
            -not [DateTimeOffset]::TryParse([string]$ExitFact.recordedAt, [ref]$recordedAt) -or
            $recordedAt -gt (Get-UtcNow).AddMinutes(5))
    {
        throw 'BLOCKED / SYSTEMD_EXIT_FACT_INVALID'
    }
}

function Invoke-EvidenceVerify
{
    $previousFormal = [Environment]::GetEnvironmentVariable('NQ_GATEW_FORMAL_SYSTEMD', 'Process')
    $previousEvidence = [Environment]::GetEnvironmentVariable('NQ_GATEW_FORMAL_EVIDENCE_ROOT', 'Process')
    try
    {
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_SYSTEMD', 'true', 'Process')
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_EVIDENCE_ROOT', (Get-EvidenceRoot $RunId), 'Process')
        $lines = @(& $script:PowerShellPath -NoProfile -File `
            "$( $script:ReleaseRoot )/bin/$( $script:WorkerHelperName )" `
            -Action evidence-verify -RunId $RunId 2> $null)
        if ($LASTEXITCODE -ne 0 -or $lines.Count -eq 0)
        {
            throw 'FAIL / EVIDENCE_VERIFY_FAILED'
        }
        try
        {
            $result = ConvertFrom-JsonPreservingTimestamps ($lines -join "`n")
        }
        catch
        {
            throw 'FAIL / EVIDENCE_VERIFY_FAILED'
        }
        if ([string]$result.result -ne 'PASS / HASH_CHAIN_VERIFIED' -or
                [string]$result.lastHash -cnotmatch '^[a-f0-9]{64}$' -or
                [long]$result.sampleCount -lt 0)
        {
            throw 'FAIL / EVIDENCE_VERIFY_FAILED'
        }
        return $result
    }
    finally
    {
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_SYSTEMD', $previousFormal, 'Process')
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_EVIDENCE_ROOT', $previousEvidence, 'Process')
    }
}

function Resolve-TerminalDecision
{
    param(
        [AllowNull()]$Intent,
        [AllowNull()]$Completion,
        [AllowNull()]$ExitFact,
        [Parameter(Mandatory = $true)]$Config,
        [Parameter(Mandatory = $true)]$Evidence
    )

    try
    {
        Assert-ExitFact $ExitFact
    }
    catch
    {
        return [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'SYSTEMD_EXIT_FACT_NOT_PROVEN' }
    }
    $preparedAt = [DateTimeOffset]::MinValue
    $exitRecordedAt = [DateTimeOffset]::Parse([string]$ExitFact.recordedAt)
    if (-not [DateTimeOffset]::TryParse([string]$Config.preparedAt, [ref]$preparedAt) -or
            $exitRecordedAt -lt $preparedAt)
    {
        return [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'SYSTEMD_EXIT_FACT_NOT_PROVEN' }
    }

    if ($null -ne $Intent)
    {
        Assert-ExactFields $Intent @('schemaVersion', 'runId', 'intent', 'reasonCode', 'requestedAt')
        $requestedAt = [DateTimeOffset]::MinValue
        if ([string]$Intent.schemaVersion -ne 'gatew-soak-intent-v1' -or
                [string]$Intent.runId -ne $RunId -or [string]$Intent.intent -ne 'OPERATOR_STOP' -or
                [string]$Intent.reasonCode -ne 'OPERATOR_STOP_REQUESTED' -or
                -not [DateTimeOffset]::TryParse([string]$Intent.requestedAt, [ref]$requestedAt) -or
                $requestedAt -lt $preparedAt)
        {
            throw 'BLOCKED / OPERATOR_INTENT_INVALID'
        }
        return [pscustomobject]@{ Status = 'OPERATOR_STOPPED'; Reason = 'OPERATOR_STOP_CONFIRMED' }
    }
    if ($null -ne $Completion)
    {
        Assert-ExactFields $Completion @(
            'schemaVersion', 'runId', 'lastSuccessfulCycleSequence',
            'lastHeartbeatSequence', 'completedAt'
        )
        $completedAt = [DateTimeOffset]::MinValue
        if ([string]$Config.runMode -ne 'REAL_READONLY_SOAK' -or [string]$Completion.runId -ne $RunId -or
                [string]$Completion.schemaVersion -ne 'gatew-soak-completion-marker-v1' -or
                [long]$Completion.lastSuccessfulCycleSequence -ne $Evidence.LastSuccessfulCycleSequence -or
                [long]$Completion.lastHeartbeatSequence -ne $Evidence.LastHeartbeatSequence -or
                -not [DateTimeOffset]::TryParse([string]$Completion.completedAt, [ref]$completedAt) -or
                $completedAt -lt $preparedAt -or
                $null -eq $ExitFact -or [string]$ExitFact.serviceResult -ne 'success' -or
                [string]$ExitFact.exitCode -ne 'exited' -or [string]$ExitFact.exitStatus -ne '0')
        {
            return [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'NATURAL_COMPLETION_NOT_PROVEN' }
        }
        return [pscustomobject]@{ Status = 'COMPLETED'; Reason = 'NATURAL_COMPLETION_PROVEN' }
    }
    if ([string]$Config.runMode -eq 'OFFLINE_ISOLATED_ACCEPTANCE' -and
            -not [bool]$Evidence.ControlledOfflineFailureProven)
    {
        return [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'OFFLINE_CYCLE_3_FAILURE_NOT_PROVEN' }
    }
    if ([string]$ExitFact.serviceResult -eq 'success' -and
            [string]$ExitFact.exitCode -eq 'exited' -and [string]$ExitFact.exitStatus -eq '0')
    {
        return [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'SYSTEMD_WORKER_FAILURE_NOT_PROVEN' }
    }
    return [pscustomobject]@{ Status = 'FAILURE_STOPPED'; Reason = 'SYSTEMD_WORKER_FAILURE_CONFIRMED' }
}

function Get-LifecyclePath
{
    return "$( Get-ControlRoot $RunId )/lifecycle.json"
}

function Set-LifecycleState
{
    param(
        [Parameter(Mandatory = $true)][string]$NextState,
        [Parameter(Mandatory = $true)][string]$ReasonCode
    )

    if ($ReasonCode -notmatch $script:SafeCodePattern)
    {
        throw 'BLOCKED / REASON_CODE_INVALID'
    }
    $path = Get-LifecyclePath
    $current = Read-JsonFile $path
    $currentState = [string]$current.state
    if ($currentState -eq $NextState)
    {
        return
    }
    if (-not $script:AllowedTransitions.ContainsKey($currentState) -or
            $script:AllowedTransitions[$currentState] -notcontains $NextState)
    {
        throw 'BLOCKED / ILLEGAL_LIFECYCLE_TRANSITION'
    }
    Write-JsonReplaceAtomic $path ([ordered]@{
        schemaVersion = 'gatew-soak-lifecycle-v1'
        runId = $RunId
        state = $NextState
        stateSequence = [long]$current.stateSequence + 1
        reasonCode = $ReasonCode
        observedAt = (Get-UtcNow).ToString('o')
    })
    Set-OwnerMode $path 'root:root' '600'
}

function Complete-Lifecycle
{
    param([Parameter(Mandatory = $true)][string]$TerminalStatus)

    $current = [string](Read-JsonFile (Get-LifecyclePath)).state
    if ($current -eq $TerminalStatus)
    {
        return
    }
    if ($TerminalStatus -eq 'FAILURE_STOPPED')
    {
        if ($current -in @('STARTING', 'RUNNING'))
        {
            Set-LifecycleState 'FAILURE_STOPPING' 'SYSTEMD_WORKER_FAILURE_CONFIRMED'
        }
        Set-LifecycleState 'FAILURE_STOPPED' 'FAILCLOSE_RECOVERY_PROVEN'
        return
    }
    if ($TerminalStatus -eq 'OPERATOR_STOPPED')
    {
        if ($current -in @('PREPARING', 'STARTING', 'RUNNING'))
        {
            Set-LifecycleState 'OPERATOR_STOPPING' 'OPERATOR_STOP_REQUESTED'
        }
        Set-LifecycleState 'OPERATOR_STOPPED' 'OPERATOR_STOP_CONFIRMED'
        return
    }
    Set-LifecycleState $TerminalStatus $( if ($TerminalStatus -eq 'COMPLETED')
    {
        'NATURAL_COMPLETION_PROVEN'
    }
    else
    {
        'FAILCLOSE_SAFETY_NOT_PROVEN'
    } )
}

function Validate-ExistingTerminal
{
    param([Parameter(Mandatory = $true)]$Terminal)

    Assert-ExactFields $Terminal @(
        'schemaVersion', 'runId', 'terminalStatus', 'terminalReasonCode', 'terminalAt', 'unitName',
        'lastKnownMainPid', 'lastSuccessfulCycleSequence', 'lastHeartbeatSequence',
        'killSwitchRecoveryStatus', 'killSwitchObservedState', 'acceptanceClockStarted',
        'acceptanceStartAt', 'plannedAcceptanceAt',
        'historicalEvidenceImmutable', 'residualProcessCount', 'runtimeDirectoryPresent',
        'evidenceManifestSha256', 'evidenceFinalChainHash', 'credentialAccessed', 'networkCalled'
    )
    if ([string]$Terminal.schemaVersion -ne 'gatew-soak-terminal-v1' -or
            [string]$Terminal.runId -ne $RunId -or
            [string]$Terminal.terminalStatus -notin @('FAILURE_STOPPED', 'OPERATOR_STOPPED', 'COMPLETED', 'BLOCKED') -or
            [string]$Terminal.terminalReasonCode -notmatch $script:SafeCodePattern -or
            [bool]$Terminal.credentialAccessed -or
            [bool]$Terminal.networkCalled -or [long]$Terminal.lastKnownMainPid -lt 0 -or
            [long]$Terminal.lastSuccessfulCycleSequence -lt 0 -or
            [long]$Terminal.lastHeartbeatSequence -lt 0 -or
            [int]$Terminal.residualProcessCount -lt -1 -or
            [string]$Terminal.evidenceManifestSha256 -cnotmatch '^(UNKNOWN|[a-f0-9]{64})$' -or
            [string]$Terminal.evidenceFinalChainHash -cnotmatch '^(UNKNOWN|[a-f0-9]{64})$')
    {
        throw 'BLOCKED / TERMINAL_SCHEMA_INVALID'
    }
    if ([bool]$Terminal.acceptanceClockStarted)
    {
        $acceptanceStart = [DateTimeOffset]::MinValue
        $plannedAcceptance = [DateTimeOffset]::MinValue
        if (-not [DateTimeOffset]::TryParse([string]$Terminal.acceptanceStartAt, [ref]$acceptanceStart) -or
                -not [DateTimeOffset]::TryParse([string]$Terminal.plannedAcceptanceAt, [ref]$plannedAcceptance) -or
                $plannedAcceptance -ne $acceptanceStart.AddHours(168))
        {
            throw 'BLOCKED / TERMINAL_SCHEMA_INVALID'
        }
    }
    elseif ($null -ne $Terminal.acceptanceStartAt -or $null -ne $Terminal.plannedAcceptanceAt)
    {
        throw 'BLOCKED / TERMINAL_SCHEMA_INVALID'
    }
    if ([string]$Terminal.terminalStatus -ne 'BLOCKED' -and
            ([string]$Terminal.killSwitchRecoveryStatus -notin $script:SafeRecoveryStatuses -or
                    [string]$Terminal.killSwitchObservedState -ne 'ENGAGED' -or
                    -not [bool]$Terminal.historicalEvidenceImmutable -or
                    [int]$Terminal.residualProcessCount -ne 0 -or [bool]$Terminal.runtimeDirectoryPresent -or
                    [string]$Terminal.evidenceManifestSha256 -cnotmatch '^[a-f0-9]{64}$' -or
                    [string]$Terminal.evidenceFinalChainHash -cnotmatch '^[a-f0-9]{64}$'))
    {
        throw 'BLOCKED / TERMINAL_SAFETY_CONTRACT_INVALID'
    }
}

function Assert-ExistingTerminalReturnable
{
    param([Parameter(Mandatory = $true)]$Terminal)

    if ([string]$Terminal.terminalStatus -eq 'BLOCKED')
    {
        throw 'FAIL / FAILCLOSE_SAFETY_NOT_PROVEN'
    }
}

function Finalize-FormalRun
{
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunPathContract $RunId
    $lock = Enter-FinalizerLock
    try
    {
        $terminalPath = "$( Get-ControlRoot $RunId )/terminal-status.json"
        if (Test-Path -LiteralPath $terminalPath -PathType Leaf)
        {
            Assert-PosixContract $terminalPath 'regular file' '600' 'root' 'root'
            $existing = Read-JsonFile $terminalPath
            Validate-ExistingTerminal $existing
            Complete-Lifecycle ([string]$existing.terminalStatus)
            Assert-ExistingTerminalReturnable $existing
            return [pscustomobject]@{
                decision = 'NO_CHANGE / TERMINAL_ALREADY_EXISTS'
                runId = $RunId
                terminalStatus = $existing.terminalStatus
                killSwitchObservedState = $existing.killSwitchObservedState
            }
        }

        $config = $null
        $configValid = $false
        try
        {
            $config = Read-JsonFile "$( Get-ControlRoot $RunId )/frozen-config.json"
            Assert-FrozenConfig $config
            $configValid = $true
        }
        catch
        {
            $configValid = $false
        }

        $recovery = New-RecoveryFailure 'ENGAGE_STATUS_UNKNOWN'
        if ($configValid)
        {
            try
            {
                $recovery = Invoke-BoundedRecovery $config
            }
            catch
            {
                $recovery = New-RecoveryFailure 'ENGAGE_STATUS_UNKNOWN'
            }
        }
        $recoverySafe = [string]$recovery.recoveryStatus -in $script:SafeRecoveryStatuses -and
                [string]$recovery.killSwitchObservedState -eq 'ENGAGED'

        $worker = [pscustomobject]@{
            LastKnownMainPid = 0L
            ActiveState = 'UNKNOWN'
            MainPID = 0L
            ResidualProcessCount = -1
            RuntimeDirectoryPresent = $true
            Safe = $false
        }
        try
        {
            $worker = Stop-AndObserveWorker
        }
        catch
        {
            # BLOCKED terminal below preserves unknown process cleanup instead of claiming PID/residual safety.
        }

        $historicalImmutable = $false
        try
        {
            $historicalImmutable = Test-HistoricalEvidenceImmutable
        }
        catch
        {
            $historicalImmutable = $false
        }
        $evidence = [pscustomobject]@{
            LastSuccessfulCycleSequence = 0L
            LastHeartbeatSequence = 0L
            LastSampleSequence = 0L
            ControlledOfflineFailureProven = $false
        }
        $evidenceVerified = $null
        try
        {
            $evidence = Get-EvidenceState
            $evidenceVerified = Invoke-EvidenceVerify
        }
        catch
        {
            $evidenceVerified = $null
        }

        $intent = $null
        $completion = $null
        $exitFact = $null
        $controlFactsValid = $true
        $clock = [pscustomobject]@{
            acceptanceClockStarted = $false
            acceptanceStartAt = $null
            plannedAcceptanceAt = $null
        }
        try
        {
            $intent = Read-OptionalJson "$( Get-ControlRoot $RunId )/intent.json"
            $completion = Read-OptionalJson "$( Get-EvidenceRoot $RunId )/completion-marker.json"
            $exitFact = Read-OptionalJson "$( Get-ControlRoot $RunId )/exit-fact.json"
            $clock = Get-AcceptanceClockProjection
        }
        catch
        {
            $controlFactsValid = $false
        }

        $terminalDecision = [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'FROZEN_CONFIG_NOT_PROVEN' }
        if ($configValid -and $controlFactsValid -and $null -ne $evidenceVerified)
        {
            try
            {
                $terminalDecision = Resolve-TerminalDecision $intent $completion $exitFact $config $evidence
            }
            catch
            {
                $terminalDecision = [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'TERMINAL_FACTS_NOT_PROVEN' }
            }
        }
        if (-not $recoverySafe)
        {
            $terminalDecision = [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'KILL_SWITCH_RECOVERY_UNCONFIRMED' }
        }
        elseif (-not [bool]$worker.Safe)
        {
            $terminalDecision = [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'WORKER_PROCESS_CLEANUP_UNCONFIRMED' }
        }
        elseif ($null -eq $evidenceVerified)
        {
            $terminalDecision = [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'EVIDENCE_INTEGRITY_UNCONFIRMED' }
        }
        elseif (-not $historicalImmutable)
        {
            $terminalDecision = [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'HISTORICAL_EVIDENCE_CHANGED' }
        }
        elseif ($configValid -and [string]$config.runMode -eq 'OFFLINE_ISOLATED_ACCEPTANCE' -and
                -not [bool]$clock.acceptanceClockStarted)
        {
            $terminalDecision = [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'ACCEPTANCE_CLOCK_NOT_PROVEN' }
        }
        elseif ([string]$terminalDecision.Status -eq 'COMPLETED' -and
                -not [bool]$clock.acceptanceClockStarted)
        {
            $terminalDecision = [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'ACCEPTANCE_CLOCK_NOT_PROVEN' }
        }

        $workerStart = $null
        try
        {
            $workerStart = Read-OptionalJson "$( Get-EvidenceRoot $RunId )/worker-start.json"
        }
        catch
        {
            $terminalDecision = [pscustomobject]@{ Status = 'BLOCKED'; Reason = 'WORKER_START_FACT_NOT_PROVEN' }
        }
        $lastKnownMainPid = [long]$worker.LastKnownMainPid
        if ($lastKnownMainPid -eq 0 -and $null -ne $workerStart -and
                $null -ne $workerStart.PSObject.Properties['mainPid'])
        {
            $lastKnownMainPid = [long]$workerStart.mainPid
        }
        if ($lastKnownMainPid -eq 0 -and $null -ne $exitFact -and
                $null -ne $exitFact.PSObject.Properties['lastKnownMainPid'])
        {
            $lastKnownMainPid = [long]$exitFact.lastKnownMainPid
        }
        $manifestPath = "$( Get-EvidenceRoot $RunId )/manifest.json"
        $manifestSha256 = if (Test-Path -LiteralPath $manifestPath -PathType Leaf)
        {
            try
            {
                Get-Sha256File $manifestPath
            }
            catch
            {
                'UNKNOWN'
            }
        }
        else
        {
            'UNKNOWN'
        }
        $finalChainHash = if ($null -eq $evidenceVerified)
        {
            'UNKNOWN'
        }
        else
        {
            [string]$evidenceVerified.lastHash
        }
        $terminal = [ordered]@{
            schemaVersion = 'gatew-soak-terminal-v1'
            runId = $RunId
            terminalStatus = [string]$terminalDecision.Status
            terminalReasonCode = [string]$terminalDecision.Reason
            terminalAt = (Get-UtcNow).ToString('o')
            unitName = Get-WorkerUnitName $RunId
            lastKnownMainPid = $lastKnownMainPid
            lastSuccessfulCycleSequence = [long]$evidence.LastSuccessfulCycleSequence
            lastHeartbeatSequence = [long]$evidence.LastHeartbeatSequence
            killSwitchRecoveryStatus = [string]$recovery.recoveryStatus
            killSwitchObservedState = [string]$recovery.killSwitchObservedState
            acceptanceClockStarted = [bool]$clock.acceptanceClockStarted
            acceptanceStartAt = $clock.acceptanceStartAt
            plannedAcceptanceAt = $clock.plannedAcceptanceAt
            historicalEvidenceImmutable = $historicalImmutable
            residualProcessCount = [int]$worker.ResidualProcessCount
            runtimeDirectoryPresent = [bool]$worker.RuntimeDirectoryPresent
            evidenceManifestSha256 = $manifestSha256
            evidenceFinalChainHash = $finalChainHash
            credentialAccessed = $false
            networkCalled = $false
        }
        Write-JsonCreateOnce $terminalPath $terminal
        Set-OwnerMode $terminalPath 'root:root' '600'
        Validate-ExistingTerminal ([pscustomobject]$terminal)
        Complete-Lifecycle ([string]$terminalDecision.Status)
        if ([string]$terminalDecision.Status -eq 'BLOCKED')
        {
            throw 'FAIL / FAILCLOSE_SAFETY_NOT_PROVEN'
        }
        return [pscustomobject]@{
            decision = 'PASS / INDEPENDENT_FAILCLOSE_FINALIZED'
            runId = $RunId
            terminalStatus = $terminalDecision.Status
            killSwitchRecoveryStatus = $recovery.recoveryStatus
            killSwitchObservedState = $recovery.killSwitchObservedState
            mainPid = $worker.MainPID
            residualProcessCount = $worker.ResidualProcessCount
            runtimeDirectoryPresent = $worker.RuntimeDirectoryPresent
            historicalEvidenceImmutable = $historicalImmutable
            evidenceFinalChainHash = $finalChainHash
            credentialAccessed = $false
            networkCalled = $false
        }
    }
    finally
    {
        if ($null -ne $lock)
        {
            $lock.Dispose()
        }
    }
}

function Invoke-FailCloseSelfTest
{
    $caseCount = 0
    $roundTripTimestamp = '2026-07-20T17:39:01.8426894Z'
    $parsedTimestamp = (ConvertFrom-JsonPreservingTimestamps `
            "{`"observedAt`":`"$roundTripTimestamp`"}").observedAt
    if ($parsedTimestamp -isnot [string] -or [string]$parsedTimestamp -cne $roundTripTimestamp)
    {
        throw 'JSON timestamp preservation self-test failed'
    }
    $caseCount++
    if ( [IO.File]::ReadAllText($PSCommandPath).Contains('$' + 'matches = @()'))
    {
        throw 'automatic Matches collision self-test failed'
    }
    $caseCount++
    $validRunId = 'gatew-soak-20260718T000000Z-0123abcd'
    $script:RunId = $validRunId
    Assert-RunId $validRunId
    $caseCount++
    foreach ($invalid in @('../escape', 'gatew-soak-bad', 'gatew-soak-20260718T000000Z-0123ABCD'))
    {
        $blocked = $false
        try
        {
            Assert-RunId $invalid
        }
        catch
        {
            $blocked = $true
        }
        if (-not $blocked)
        {
            throw 'runId self-test failed'
        }
        $caseCount++
    }
    foreach ($status in $script:RecoveryStatuses)
    {
        $result = New-RecoveryFailure $status
        Assert-RecoveryResult $result 'engage'
        $caseCount++
    }
    if ($script:SafeRecoveryStatuses.Count -ne 2 -or
            $script:SafeRecoveryStatuses -contains 'ENGAGE_FAILED_WRITE')
    {
        throw 'recovery taxonomy self-test failed'
    }
    $caseCount++

    $preparedFixtureAt = (Get-UtcNow).AddMinutes(-2)
    $exitFixtureAt = $preparedFixtureAt.AddMinutes(1)
    $config = [pscustomobject]@{
        runMode = 'REAL_READONLY_SOAK'
        preparedAt = $preparedFixtureAt.ToString('o')
    }
    $evidence = [pscustomobject]@{
        LastSuccessfulCycleSequence = 2L
        LastHeartbeatSequence = 3L
        ControlledOfflineFailureProven = $false
    }
    $failureExitFact = [pscustomobject][ordered]@{
        schemaVersion = 'gatew-soak-exit-fact-v1'
        runId = $validRunId
        serviceResult = 'exit-code'
        exitCode = 'exited'
        exitStatus = '2'
        lastKnownMainPid = 123L
        recordedAt = $exitFixtureAt.ToString('o')
    }
    $failureDecision = Resolve-TerminalDecision $null $null $failureExitFact $config $evidence
    if ($failureDecision.Status -ne 'FAILURE_STOPPED')
    {
        throw 'valid failure exit fact self-test failed'
    }
    $caseCount++

    $intent = [pscustomobject][ordered]@{
        schemaVersion = 'gatew-soak-intent-v1'
        runId = $validRunId
        intent = 'OPERATOR_STOP'
        reasonCode = 'OPERATOR_STOP_REQUESTED'
        requestedAt = $preparedFixtureAt.AddSeconds(30).ToString('o')
    }
    $operatorDecision = Resolve-TerminalDecision $intent $null $failureExitFact $config $evidence
    if ($operatorDecision.Status -ne 'OPERATOR_STOPPED')
    {
        throw 'operator intent separation self-test failed'
    }
    $caseCount++

    $missingExitDecision = Resolve-TerminalDecision $null $null $null $config $evidence
    if ($missingExitDecision.Status -ne 'BLOCKED')
    {
        throw 'missing exit fact self-test failed'
    }
    $caseCount++
    $successExitFact = [pscustomobject][ordered]@{
        schemaVersion = 'gatew-soak-exit-fact-v1'
        runId = $validRunId
        serviceResult = 'success'
        exitCode = 'exited'
        exitStatus = '0'
        lastKnownMainPid = 123L
        recordedAt = $exitFixtureAt.ToString('o')
    }
    $successWithoutCompletion = Resolve-TerminalDecision $null $null $successExitFact $config $evidence
    if ($successWithoutCompletion.Status -ne 'BLOCKED')
    {
        throw 'success exit fact failure-forgery self-test failed'
    }
    $caseCount++

    $offlineConfig = [pscustomobject]@{
        runMode = 'OFFLINE_ISOLATED_ACCEPTANCE'
        preparedAt = $preparedFixtureAt.ToString('o')
    }
    $offlineEvidence = [pscustomobject]@{
        LastSuccessfulCycleSequence = 2L
        LastHeartbeatSequence = 3L
        ControlledOfflineFailureProven = $true
    }
    $offlineDecision = Resolve-TerminalDecision $null $null $failureExitFact $offlineConfig $offlineEvidence
    if ($offlineDecision.Status -ne 'FAILURE_STOPPED')
    {
        throw 'controlled offline failure binding self-test failed'
    }
    $caseCount++

    $temporaryRoot = Join-Path $script:WorkspaceRoot 'target/gatew-okx-readonly-soak/failclose-self-test'
    $temporary = Join-Path $temporaryRoot ([Guid]::NewGuid().ToString('N'))
    try
    {
        [IO.Directory]::CreateDirectory($temporary) | Out-Null
        $path = Join-Path $temporary 'terminal.json'
        Write-TextCreateOnce $path '{"terminalStatus":"FAILURE_STOPPED"}'
        $rejected = $false
        try
        {
            Write-TextCreateOnce $path '{"terminalStatus":"OPERATOR_STOPPED"}'
        }
        catch
        {
            $rejected = $true
        }
        if (-not $rejected -or
                (Get-Content -LiteralPath $path -Raw) -ne '{"terminalStatus":"FAILURE_STOPPED"}')
        {
            throw 'terminal create-once self-test failed'
        }
        $caseCount++

        $emptyPath = Join-Path $temporary 'zero-length.jsonl'
        Write-TextCreateOnce $emptyPath ''
        $emptyRejected = $false
        try
        {
            Write-TextCreateOnce $emptyPath ''
        }
        catch
        {
            $emptyRejected = $true
        }
        if (-not $emptyRejected -or (Get-Item -LiteralPath $emptyPath).Length -ne 0)
        {
            throw 'zero-length create-once self-test failed'
        }
        $caseCount++

        if ($script:SystemdRoot -ne '/etc/systemd/system' -or
                $script:WorkerTemplate -ne 'nq-gatew-soak@.service' -or
                (Get-WorkerUnitName $validRunId) -ne "nq-gatew-soak@$validRunId.service")
        {
            throw 'formal worker unit path self-test failed'
        }
        $caseCount++

        $cleanState = [pscustomobject]@{ ActiveState = 'inactive'; MainPID = 0L }
        $activeState = [pscustomobject]@{ ActiveState = 'active'; MainPID = 123L }
        if (-not (Test-WorkerCleanupComplete $cleanState 0 $false) -or
                (Test-WorkerCleanupComplete $activeState 0 $false) -or
                (Test-WorkerCleanupComplete $cleanState 1 $false) -or
                (Test-WorkerCleanupComplete $cleanState 0 $true))
        {
            throw 'worker cleanup polling condition self-test failed'
        }
        $caseCount++

        $previousStateRoot = $script:StateRoot
        $firstLock = $null
        try
        {
            $script:StateRoot = Join-Path $temporary 'state-root'
            $lockControlRoot = Get-ControlRoot $validRunId
            [IO.Directory]::CreateDirectory($lockControlRoot) | Out-Null
            $firstLock = Enter-FinalizerLock
            $lockRejected = $false
            $secondLock = $null
            try
            {
                $secondLock = Enter-FinalizerLock
            }
            catch
            {
                $lockRejected = $_.Exception.Message -eq 'FAIL / FAILCLOSE_LOCK_UNAVAILABLE'
            }
            finally
            {
                if ($null -ne $secondLock)
                {
                    $secondLock.Dispose()
                }
            }
            if (-not $lockRejected)
            {
                throw 'failclose lock self-test failed'
            }
            $caseCount++
        }
        finally
        {
            if ($null -ne $firstLock)
            {
                $firstLock.Dispose()
            }
            $script:StateRoot = $previousStateRoot
        }

        $blockedTerminal = [pscustomobject][ordered]@{
            schemaVersion = 'gatew-soak-terminal-v1'
            runId = $validRunId
            terminalStatus = 'BLOCKED'
            terminalReasonCode = 'KILL_SWITCH_RECOVERY_UNCONFIRMED'
            terminalAt = '2026-07-18T00:02:00Z'
            unitName = "nq-gatew-soak@$validRunId.service"
            lastKnownMainPid = 123L
            lastSuccessfulCycleSequence = 2L
            lastHeartbeatSequence = 3L
            killSwitchRecoveryStatus = 'ENGAGE_STATUS_UNKNOWN'
            killSwitchObservedState = 'UNKNOWN'
            acceptanceClockStarted = $false
            acceptanceStartAt = $null
            plannedAcceptanceAt = $null
            historicalEvidenceImmutable = $false
            residualProcessCount = -1
            runtimeDirectoryPresent = $true
            evidenceManifestSha256 = 'UNKNOWN'
            evidenceFinalChainHash = 'UNKNOWN'
            credentialAccessed = $false
            networkCalled = $false
        }
        Validate-ExistingTerminal $blockedTerminal
        $blockedRetryRejected = $false
        try
        {
            Assert-ExistingTerminalReturnable $blockedTerminal
        }
        catch
        {
            $blockedRetryRejected = $_.Exception.Message -eq 'FAIL / FAILCLOSE_SAFETY_NOT_PROVEN'
        }
        if (-not $blockedRetryRejected)
        {
            throw 'existing BLOCKED retry self-test failed'
        }
        $caseCount++
    }
    finally
    {
        if (Test-Path -LiteralPath $temporary)
        {
            Remove-Item -LiteralPath $temporary -Recurse -Force
        }
    }
    return [pscustomobject]@{
        decision = 'PASS / INDEPENDENT_FAILCLOSE_SELF_TEST'
        cases = $caseCount
        boundedRecoveryAttempts = 3
        terminalCreateOnce = 'PASS / O_EXCL_OR_ATOMIC_LINK'
        zeroLengthCreateOnce = 'PASS / LENGTH_0 / SECOND_CREATE_REJECTED'
        operatorFailureTerminalSeparation = 'PASS'
        exitFactBinding = 'PASS / failure+operator+missing+success'
        finalizerLock = 'PASS / MUTUAL_EXCLUSION'
        existingBlockedReturnsNonZero = 'PASS'
        formalWorkerUnitPath = 'PASS / SYSTEMD_ROOT_AND_TEMPLATE_BOUND'
        workerCleanupPolling = 'PASS / INACTIVE+PID0+RESIDUAL0+RUNTIME_ABSENT'
        automaticMatchesCollision = 'PASS / FORBIDDEN'
        credentialAccessed = $false
        networkCalled = $false
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
        'FAIL / INDEPENDENT_FAILCLOSE_INTERNAL_ERROR'
    }
    $failure = [ordered]@{ decision = $message; runId = $RunId }
    if ($Action -eq 'self-test')
    {
        $failure.selfTestDetail = $_.Exception.Message
    }
    [pscustomobject]$failure | ConvertTo-Json
    exit 2
}
