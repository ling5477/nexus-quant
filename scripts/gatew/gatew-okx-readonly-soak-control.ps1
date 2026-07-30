[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
            'precreate-prerequisite', 'prepare', 'start', 'status', 'verify',
            'verify-evidence', 'verify-acceptance', 'verify-terminal', 'finalize-acceptance',
            'stop', 'offline-fail',
            'record-fresh-ssh', 'start-acceptance-clock',
            'unit-preflight', 'record-exit', 'self-test'
    )]
    [string]$Action,

    [string]$RunId,

    [ValidateSet('REAL_READONLY_SOAK', 'OFFLINE_ISOLATED_ACCEPTANCE')]
    [string]$RunMode = 'OFFLINE_ISOLATED_ACCEPTANCE',

    [string]$ExpectedCommit,
    [string]$StartingCiRun,
    [string]$DatabaseUrl,
    [string]$DatabaseUser,
    [string]$DatabaseSchema,
    [string]$DatabasePasswordSourceFile,
    [string]$MasterKeySourceFile,
    [string]$OfflineDatabasePasswordSourceFile,

    [ValidateRange(1, 30)]
    [int]$SmokeHeartbeatSeconds = 2,

    [long]$PreviousMainPid = 0,
    [long]$MinimumHeartbeatSequence = -1,
    [string]$PreviousHeartbeatObservedAt,
    [switch]$CleanupOfflineDropIn,

    [string]$ServiceResult,
    [string]$ExitCode,
    [string]$ExitStatus,
    [long]$LastKnownMainPid = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:RemediationContractPath = Join-Path $PSScriptRoot 'gatew-soak-remediation-contract.psm1'
if (-not (Test-Path -LiteralPath $script:RemediationContractPath -PathType Leaf))
{
    throw 'BLOCKED / REMEDIATION_CONTRACT_MISSING'
}
Import-Module $script:RemediationContractPath -Force

$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)
$script:RunIdPattern = '^gatew-soak-[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$'
$script:SafeCodePattern = '^[A-Z][A-Z0-9_]{1,95}$'
$script:StateRoot = '/var/lib/nexus-quant/gatew-soak'
$script:RuntimeRoot = '/run/nexus-quant/gatew-soak'
$script:LogRoot = '/var/log/nexus-quant/gatew-soak'
$script:CredentialRoot = '/etc/nexus-quant/gatew-soak/credentials'
$script:PreCreateDescriptorPath = '/etc/nexus-quant/gatew-soak/precreate-prerequisite.json'
$script:PreCreateResultRoot = '/run'
$script:SystemdRoot = '/etc/systemd/system'
$script:RuntimeSystemdRoot = '/run/systemd/system'
$script:WorkerTemplate = 'nq-gatew-soak@.service'
$script:FailCloseTemplate = 'nq-gatew-soak-failclose@.service'
$script:WorkerHelperName = 'gatew-okx-readonly-soak.ps1'
$script:ControlHelperName = 'gatew-okx-readonly-soak-control.ps1'
$script:FailCloseHelperName = 'gatew-okx-readonly-soak-failclose.ps1'
$script:LinuxRuntimeUser = 'nqgatew'
$script:LinuxRuntimeGroup = 'nqgatew'
$script:PowerShellPath = '/usr/bin/pwsh'
$script:LinuxJavaPath = '/usr/bin/java'
$script:SystemdCredsPath = '/usr/bin/systemd-creds'
$script:DatabasePasswordCredentialName = 'db-password'
$script:SystemctlPath = '/usr/bin/systemctl'
$script:ChownPath = '/usr/bin/chown'
$script:ChmodPath = '/usr/bin/chmod'
$script:StatPath = '/usr/bin/stat'
$script:ReadlinkPath = '/usr/bin/readlink'
$script:LnPath = '/usr/bin/ln'
$script:IdPath = '/usr/bin/id'
$script:StopIntentMaxAgeSeconds = 300

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
$script:TerminalStates = @('FAILURE_STOPPED', 'OPERATOR_STOPPED', 'COMPLETED', 'BLOCKED')

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

function New-RunId
{
    return 'gatew-soak-{0}-{1}' -f `
        (Get-UtcNow).ToString('yyyyMMddTHHmmssZ'),   `
          ([Guid]::NewGuid().ToString('N').Substring(0, 8))
}

function Assert-RootLinux
{
    if (-not (Test-LinuxPlatform) -or [Environment]::UserName -ne 'root')
    {
        throw 'BLOCKED / ROOT_CONTROL_REQUIRED'
    }
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

function Get-Sha256File
{
    param([Parameter(Mandatory = $true)][string]$Path)

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function New-ReleaseVerifierParameters
{
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [string]$ExpectedId,
        [string]$ExpectedManifest
    )

    $parameters = @{ ReleaseRoot = $Root }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedId))
    {
        $parameters.ExpectedReleaseId = $ExpectedId
    }
    if (-not [string]::IsNullOrWhiteSpace($ExpectedManifest))
    {
        $parameters.ExpectedManifestSha256 = $ExpectedManifest
    }
    return $parameters
}

function Get-ReleaseIdentity
{
    param(
        [string]$ExpectedReleaseId,
        [string]$ExpectedManifestSha256
    )

    $verifier = Join-Path $script:ReleaseRoot "bin/$( $script:ReleaseVerifierName )"
    if (-not (Test-Path -LiteralPath $verifier -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_VERIFIER_MISSING'
    }
    $verifierParameters = New-ReleaseVerifierParameters `
        $script:ReleaseRoot $ExpectedReleaseId $ExpectedManifestSha256
    $output = @(& $verifier @verifierParameters 2> $null)
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

function Assert-FrozenReleaseBinding
{
    param(
        [Parameter(Mandatory = $true)]$Config,
        [switch]$RequireEnvironment
    )

    $expectedFields = @(
        'schemaVersion', 'runId', 'runMode', 'databaseUrl', 'databaseUser', 'databaseSchema',
        'offlineHeartbeatSeconds', 'releaseId', 'sourceCommit', 'sourceTreeMode',
        'releaseManifestSha256', 'releaseRoot', 'startingCiRun', 'workerUnit', 'failCloseUnit',
        'acceptanceClockStarted', 'acceptanceStartAt', 'plannedAcceptanceAt', 'preparedAt'
    )
    if ((@($Config.PSObject.Properties.Name) -join '|') -cne ($expectedFields -join '|') -or
            [string]$Config.schemaVersion -ne 'gatew-soak-frozen-config-v3' -or
            [string]$Config.runMode -notin @('REAL_READONLY_SOAK', 'OFFLINE_ISOLATED_ACCEPTANCE') -or
            [string]$Config.releaseId -cnotmatch '^(?:[a-f0-9]{40}|candidate-[a-f0-9]{12}-[a-f0-9]{16}-[0-9]{8}T[0-9]{6}Z)$' -or
            [string]$Config.sourceCommit -cnotmatch '^[a-f0-9]{40}$' -or
            [string]$Config.sourceTreeMode -notin @('CANDIDATE', 'EXACT_COMMIT') -or
            [string]$Config.releaseManifestSha256 -cnotmatch '^[a-f0-9]{64}$' -or
            [string]$Config.startingCiRun -cnotmatch '^[1-9][0-9]{0,19}$' -or
            [bool]$Config.acceptanceClockStarted -or
            $null -ne $Config.acceptanceStartAt -or $null -ne $Config.plannedAcceptanceAt)
    {
        throw 'BLOCKED / FROZEN_RELEASE_BINDING_INVALID'
    }
    if ([string]$Config.runMode -eq 'REAL_READONLY_SOAK' -and
            ([string]$Config.sourceTreeMode -ne 'EXACT_COMMIT' -or
                    [string]$Config.releaseId -cne [string]$Config.sourceCommit))
    {
        throw 'BLOCKED / REAL_RUN_EXACT_RELEASE_REQUIRED'
    }

    $identity = Get-ReleaseIdentity ([string]$Config.releaseId) ([string]$Config.releaseManifestSha256)
    if ([string]$identity.sourceCommit -cne [string]$Config.sourceCommit -or
            [string]$identity.sourceTreeMode -cne [string]$Config.sourceTreeMode -or
            [IO.Path]::GetFullPath([string]$identity.releaseRoot) -cne
                    [IO.Path]::GetFullPath([string]$Config.releaseRoot))
    {
        throw 'BLOCKED / FROZEN_RELEASE_BINDING_CHANGED'
    }
    if ($RequireEnvironment)
    {
        $environmentBinding = @{
            NQ_GATEW_RELEASE_ROOT = [string]$Config.releaseRoot
            NQ_GATEW_RELEASE_ID = [string]$Config.releaseId
            NQ_GATEW_RELEASE_MANIFEST_SHA256 = [string]$Config.releaseManifestSha256
        }
        foreach ($name in $environmentBinding.Keys)
        {
            $value = [Environment]::GetEnvironmentVariable($name, 'Process')
            if ([string]$value -cne [string]$environmentBinding[$name])
            {
                throw 'BLOCKED / RELEASE_ENVIRONMENT_BINDING_CHANGED'
            }
        }
    }
    return $identity
}

function ConvertTo-CompactJson
{
    param([Parameter(Mandatory = $true)]$Value)

    return ($Value | ConvertTo-Json -Compress -Depth 16)
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
        [Parameter(Mandatory = $true)][AllowEmptyString()][string]$Text,
        [string]$LinuxOwnerGroup = 'root:root',
        [ValidatePattern('^[0-7]{3,4}$')][string]$LinuxMode = '600'
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
            # owner/mode 必须在 hard-link 发布前生效，避免 reader 看见尚不可读的 clock inode。
            Invoke-Native $script:ChownPath @('--', $LinuxOwnerGroup, $temporary) | Out-Null
            Invoke-Native $script:ChmodPath @($LinuxMode, '--', $temporary) | Out-Null
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
        [Parameter(Mandatory = $true)]$Value,
        [string]$LinuxOwnerGroup = 'root:root',
        [ValidatePattern('^[0-7]{3,4}$')][string]$LinuxMode = '600'
    )

    Write-TextCreateOnce $Path (ConvertTo-CompactJson $Value) $LinuxOwnerGroup $LinuxMode
}

function Write-TextReplaceAtomic
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Text
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
    $bytes = $script:Utf8NoBom.GetBytes($Text)
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

function Write-JsonReplaceAtomic
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value
    )

    Write-TextReplaceAtomic $Path (ConvertTo-CompactJson $Value)
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

function Get-FailCloseUnitName
{
    param([Parameter(Mandatory = $true)][string]$Value)
    Assert-RunId $Value
    return "nq-gatew-soak-failclose@$Value.service"
}

function Enter-TerminalAuthorityLock
{
    param([Parameter(Mandatory = $true)][string]$Value)

    $path = "$( Get-ControlRoot $Value )/failclose.lock"
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
        throw 'FAIL / TERMINAL_AUTHORITY_LOCK_UNAVAILABLE'
    }
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
    if ($metadata.Type -notlike "*$ExpectedType*" -or
            $metadata.Mode -ne $ExpectedMode -or
            $metadata.Owner -ne $ExpectedOwner -or
            $metadata.Group -ne $ExpectedGroup)
    {
        throw 'BLOCKED / PATH_OWNERSHIP_CONTRACT_INVALID'
    }
}

function Ensure-Directory
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$OwnerGroup,
        [Parameter(Mandatory = $true)][string]$Mode
    )

    $normalized = [IO.Path]::GetFullPath($Path)
    Assert-PathComponentsNoSymlink $normalized -AllowMissingTail
    [IO.Directory]::CreateDirectory($normalized) | Out-Null
    Assert-PathComponentsNoSymlink $normalized
    Set-OwnerMode $normalized $OwnerGroup $Mode
}

function Assert-RunDirectoryContract
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
    Assert-PosixContract $evidenceRoot 'directory' '700' $script:LinuxRuntimeUser $script:LinuxRuntimeGroup
}

function Assert-LiteralValue
{
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    if ($Value.IndexOf([char]0) -ge 0 -or
            $Value -match "[`r`n]" -or
            $Value -match '\$\{?[A-Za-z_][A-Za-z0-9_]*\}?' -or
            $Value -match '%[A-Za-z_][A-Za-z0-9_]*%' -or
            $Value -match '\$\(' -or
            $Value -match '`')
    {
        throw 'BLOCKED / CONFIG_VALUE_NOT_LITERAL'
    }
}

function Assert-PreCreateDescriptorValue
{
    param([Parameter(Mandatory = $true)]$Descriptor)

    $fields = @(
        'schemaVersion', 'databaseHost', 'databasePort', 'databaseName', 'databaseUser',
        'passwordSecretFile', 'managementLoopbackUrl', 'expectedCredentialType', 'expectedEnvironment'
    )
    if ((@($Descriptor.PSObject.Properties.Name) -join '|') -cne ($fields -join '|') -or
            [string]$Descriptor.schemaVersion -cne 'gatew-precreate-prerequisite-v1' -or
            [string]$Descriptor.databaseHost -notin @('127.0.0.1', 'localhost') -or
            ($Descriptor.databasePort -isnot [int] -and $Descriptor.databasePort -isnot [long]) -or
            [long]$Descriptor.databasePort -lt 1 -or [long]$Descriptor.databasePort -gt 65535 -or
            [string]$Descriptor.databaseName -cnotmatch '^[A-Za-z][A-Za-z0-9_]{0,62}$' -or
            [string]$Descriptor.databaseUser -cnotmatch '^[A-Za-z_][A-Za-z0-9_-]{0,62}$' -or
            [string]$Descriptor.passwordSecretFile -cne "$( $script:CredentialRoot )/db-password.cred" -or
            [string]$Descriptor.managementLoopbackUrl -cne 'http://127.0.0.1:18889/actuator/health' -or
            [string]$Descriptor.expectedCredentialType -cne 'OKX_API_V5' -or
            [string]$Descriptor.expectedEnvironment -cne 'LIVE')
    {
        throw 'BLOCKED / PRECREATE_DESCRIPTOR_INVALID'
    }
    foreach ($field in @(
        'databaseHost', 'databaseName', 'databaseUser', 'passwordSecretFile',
        'managementLoopbackUrl', 'expectedCredentialType', 'expectedEnvironment'
    ))
    {
        Assert-LiteralValue ([string]$Descriptor.PSObject.Properties[$field].Value)
    }
    return $Descriptor
}

function Read-PreCreateDescriptor
{
    if (-not (Test-LinuxPlatform))
    {
        throw 'BLOCKED / PRECREATE_LINUX_REQUIRED'
    }
    Assert-PathComponentsNoSymlink $script:PreCreateDescriptorPath
    if ((Get-Item -LiteralPath $script:PreCreateDescriptorPath -Force).Length -gt 4096)
    {
        throw 'BLOCKED / PRECREATE_DESCRIPTOR_INVALID'
    }
    Assert-PosixContract $script:PreCreateDescriptorPath 'regular file' '600' 'root' 'root'
    $descriptor = Assert-PreCreateDescriptorValue (Read-JsonFile $script:PreCreateDescriptorPath)
    $secretFile = [string]$descriptor.passwordSecretFile
    Assert-PathBelowRoot $script:CredentialRoot $secretFile
    Assert-PosixContract $secretFile 'regular file' '600' 'root' 'root'
    $secretSize = (Get-Item -LiteralPath $secretFile -Force).Length
    if ($secretSize -lt 1 -or $secretSize -gt 16384)
    {
        throw 'BLOCKED / PRECREATE_SECRET_REFERENCE_INVALID'
    }
    return $descriptor
}

function Get-PreCreateDatabaseUrl
{
    param([Parameter(Mandatory = $true)]$Descriptor)

    return 'jdbc:postgresql://{0}:{1}/{2}' -f
        [string]$Descriptor.databaseHost,
        [long]$Descriptor.databasePort,
        [string]$Descriptor.databaseName
}

function Get-PreCreateLauncherClassPath
{
    foreach ($path in @(
        "$( $script:ReleaseRoot )/launcher/test-support.jar",
        "$( $script:ReleaseRoot )/launcher/modules",
        "$( $script:ReleaseRoot )/launcher/lib"
    ))
    {
        if (-not (Test-Path -LiteralPath $path))
        {
            throw 'BLOCKED / PRECREATE_LAUNCHER_BUNDLE_MISSING'
        }
        Assert-PathComponentsNoSymlink $path
    }
    return @(
        "$( $script:ReleaseRoot )/launcher/test-support.jar",
        "$( $script:ReleaseRoot )/launcher/modules/*",
        "$( $script:ReleaseRoot )/launcher/lib/*"
    ) -join [IO.Path]::PathSeparator
}

function Read-PreCreateDatabasePassword
{
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $script:SystemdCredsPath -PathType Leaf))
    {
        throw 'BLOCKED / PRECREATE_SECRET_READER_MISSING'
    }
    $lines = @(& $script:SystemdCredsPath decrypt `
            "--name=$( $script:DatabasePasswordCredentialName )" --newline=no $Path - 2> $null)
    if ($LASTEXITCODE -ne 0 -or $lines.Count -ne 1)
    {
        throw 'BLOCKED / PRECREATE_DATABASE_SECRET_UNAVAILABLE'
    }
    $value = [string]$lines[0]
    if ([string]::IsNullOrWhiteSpace($value) -or $value.Length -gt 16384 -or
            $value.IndexOf([char]0) -ge 0 -or $value -match "[`r`n]")
    {
        throw 'BLOCKED / PRECREATE_DATABASE_SECRET_UNAVAILABLE'
    }
    return $value
}

function Assert-PreCreateReadback
{
    param([Parameter(Mandatory = $true)]$Value)

    $fields = @(
        'killSwitchEngaged', 'credentialConfigured', 'activeCredentialCount', 'credentialType',
        'credentialLocalStatus', 'tradePermissionExpectedDisabled',
        'withdrawPermissionExpectedDisabled', 'postgresReachable', 'managementHealthy'
    )
    if ((@($Value.PSObject.Properties.Name) -join '|') -cne ($fields -join '|') -or
            ($Value.activeCredentialCount -isnot [int] -and $Value.activeCredentialCount -isnot [long]) -or
            [long]$Value.activeCredentialCount -lt 0 -or
            [string]$Value.credentialType -notin @('OKX_API_V5', 'UNKNOWN', 'CONFLICT') -or
            [string]$Value.credentialLocalStatus -notin @(
                'ACTIVE', 'DISABLED', 'REVOKED', 'EXPIRED', 'ROTATED', 'UNKNOWN', 'CONFLICT'
            ))
    {
        throw 'BLOCKED / PRECREATE_READBACK_INVALID'
    }
    foreach ($field in @(
        'killSwitchEngaged', 'credentialConfigured', 'tradePermissionExpectedDisabled',
        'withdrawPermissionExpectedDisabled', 'postgresReachable', 'managementHealthy'
    ))
    {
        if ($Value.PSObject.Properties[$field].Value -isnot [bool])
        {
            throw 'BLOCKED / PRECREATE_READBACK_INVALID'
        }
    }
    $serialized = ConvertTo-CompactJson $Value
    if ($serialized -match '(?i)jdbc|password|api[-_]?key|secret|passphrase|signature|encrypted[_-]?payload|decrypted[_-]?payload|account')
    {
        throw 'BLOCKED / PRECREATE_READBACK_INVALID'
    }
    return $Value
}

function New-PreCreateResult
{
    param(
        [Parameter(Mandatory = $true)][string]$CheckedAt,
        [AllowNull()]$Readback
    )

    $available = $null -ne $Readback
    $ready = $available -and
        [bool]$Readback.postgresReachable -and [bool]$Readback.managementHealthy -and
        [bool]$Readback.killSwitchEngaged -and [bool]$Readback.credentialConfigured -and
        [long]$Readback.activeCredentialCount -eq 1 -and
        [string]$Readback.credentialType -eq 'OKX_API_V5' -and
        [string]$Readback.credentialLocalStatus -eq 'ACTIVE' -and
        [bool]$Readback.tradePermissionExpectedDisabled -and
        [bool]$Readback.withdrawPermissionExpectedDisabled
    return [pscustomobject][ordered]@{
        schemaVersion = 'gatew-precreate-prerequisite-result-v1'
        checkedAt = $CheckedAt
        postgresReachable = $available -and [bool]$Readback.postgresReachable
        managementHealthy = $available -and [bool]$Readback.managementHealthy
        killSwitchEngaged = $available -and [bool]$Readback.killSwitchEngaged
        credentialConfigured = $available -and [bool]$Readback.credentialConfigured
        activeCredentialCount = if ($available) { [long]$Readback.activeCredentialCount } else { 0L }
        credentialType = if ($available) { [string]$Readback.credentialType } else { 'UNKNOWN' }
        credentialLocalStatus = if ($available) { [string]$Readback.credentialLocalStatus } else { 'UNKNOWN' }
        tradePermissionExpectedDisabled = $available -and [bool]$Readback.tradePermissionExpectedDisabled
        withdrawPermissionExpectedDisabled = $available -and [bool]$Readback.withdrawPermissionExpectedDisabled
        readyForAttemptCreation = $ready
        diagnosticOnly = $true
        noSideEffect = $true
        credentialMaterialExposed = $false
    }
}

function Assert-PreCreateResult
{
    param([Parameter(Mandatory = $true)]$Value)

    $fields = @(
        'schemaVersion', 'checkedAt', 'postgresReachable', 'managementHealthy', 'killSwitchEngaged',
        'credentialConfigured', 'activeCredentialCount', 'credentialType', 'credentialLocalStatus',
        'tradePermissionExpectedDisabled', 'withdrawPermissionExpectedDisabled',
        'readyForAttemptCreation', 'diagnosticOnly', 'noSideEffect', 'credentialMaterialExposed'
    )
    $checkedAt = [DateTimeOffset]::MinValue
    if ((@($Value.PSObject.Properties.Name) -join '|') -cne ($fields -join '|') -or
            [string]$Value.schemaVersion -cne 'gatew-precreate-prerequisite-result-v1' -or
            -not [DateTimeOffset]::TryParse([string]$Value.checkedAt, [ref]$checkedAt) -or
            ($Value.activeCredentialCount -isnot [int] -and $Value.activeCredentialCount -isnot [long]) -or
            [long]$Value.activeCredentialCount -lt 0 -or
            [string]$Value.credentialType -notin @('OKX_API_V5', 'UNKNOWN', 'CONFLICT') -or
            [string]$Value.credentialLocalStatus -notin @(
                'ACTIVE', 'DISABLED', 'REVOKED', 'EXPIRED', 'ROTATED', 'UNKNOWN', 'CONFLICT'
            ) -or
            -not [bool]$Value.diagnosticOnly -or -not [bool]$Value.noSideEffect -or
            [bool]$Value.credentialMaterialExposed -or
            (ConvertTo-CompactJson $Value) -match
                '(?i)jdbc|password|api[-_]?key|secret|passphrase|signature|encrypted[_-]?payload|decrypted[_-]?payload|account')
    {
        throw 'BLOCKED / PRECREATE_RESULT_INVALID'
    }
    foreach ($field in @(
        'postgresReachable', 'managementHealthy', 'killSwitchEngaged', 'credentialConfigured',
        'tradePermissionExpectedDisabled', 'withdrawPermissionExpectedDisabled',
        'readyForAttemptCreation', 'diagnosticOnly', 'noSideEffect', 'credentialMaterialExposed'
    ))
    {
        if ($Value.PSObject.Properties[$field].Value -isnot [bool])
        {
            throw 'BLOCKED / PRECREATE_RESULT_INVALID'
        }
    }
    return $Value
}

function Invoke-PreCreateJavaReadback
{
    param([Parameter(Mandatory = $true)]$Descriptor)

    Get-ReleaseIdentity | Out-Null
    if (-not (Test-Path -LiteralPath $script:LinuxJavaPath -PathType Leaf))
    {
        throw 'BLOCKED / PRECREATE_JAVA_RUNTIME_MISSING'
    }
    $token = [Guid]::NewGuid().ToString('N')
    $resultFile = "$( $script:PreCreateResultRoot )/nq-gatew-precreate-prerequisite-$token.json"
    if (Test-Path -LiteralPath $resultFile)
    {
        throw 'BLOCKED / PRECREATE_RESULT_PATH_CONFLICT'
    }
    $password = Read-PreCreateDatabasePassword ([string]$Descriptor.passwordSecretFile)
    $values = [ordered]@{
        SPRING_PROFILES_ACTIVE = 'gatew-okx-readonly-soak'
        NQ_GATEW_OKX_READONLY_SOAK_ENABLED = 'true'
        NQ_GATEW_RUN_MODE = 'REAL_READONLY_SOAK'
        CI = 'false'
        NQ_NO_OUTBOUND = 'false'
        NQ_LIVE_ENABLED = 'false'
        NQ_REAL_ORDER_SUBMISSION_ENABLED = 'false'
        NQ_TRANSFER_ENABLED = 'false'
        NQ_WITHDRAW_ENABLED = 'false'
        NQ_AI_ENABLED = 'false'
        NQ_DH_RUNTIME_ENABLED = 'false'
        NQ_REAL_PROVIDER_ENABLED = 'false'
        NQ_REAL_CLIENT_ENABLED = 'false'
        NQ_REAL_EXCHANGE_ENABLED = 'false'
        NQ_GATEW_SOAK_DB_URL = Get-PreCreateDatabaseUrl $Descriptor
        NQ_GATEW_SOAK_DB_USER = [string]$Descriptor.databaseUser
        NQ_GATEW_SOAK_DB_PASSWORD = $password
        NQ_GATEW_MANAGEMENT_HEALTH_URL = [string]$Descriptor.managementLoopbackUrl
        NQ_ACCOUNT_CREDENTIALS_MASTER_KEY = $null
        NQ_GATEW_SOAK_OWNER_ID = $null
        NQ_GATEW_SOAK_ACCOUNT_ID = $null
        NQ_GATEW_SOAK_CURRENCIES = $null
        NQ_GATEW_FORMAL_EVIDENCE_ROOT = $null
        NQ_GATEW_SECRET_SOURCE = $null
        NQ_GATEW_FORMAL_SYSTEMD = $null
        CREDENTIALS_DIRECTORY = $null
        NQ_OKX_API_KEY = $null
        NQ_OKX_API_SECRET = $null
        NQ_OKX_API_PASSPHRASE = $null
        NQ_OKX_REAL_API_KEY = $null
        NQ_OKX_REAL_API_SECRET = $null
        NQ_OKX_REAL_API_PASSPHRASE = $null
    }
    $previous = @{ }
    try
    {
        foreach ($name in $values.Keys)
        {
            $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
            [Environment]::SetEnvironmentVariable($name, $values[$name], 'Process')
        }
        $arguments = @(
            '-Xms16m', '-Xmx256m', '-Dfile.encoding=UTF-8',
            '-Dnq.gatew.okxReadonlySoak.required=true',
            '-Dnq.gatew.okxReadonlySoak.action=precreate-prerequisite',
            "-Dnq.gatew.okxReadonlySoak.resultFile=$resultFile",
            "-Dnq.gatew.okxReadonlySoak.repoRoot=$( $script:ReleaseRoot )",
            '-cp', (Get-PreCreateLauncherClassPath),
            'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakCycleTest$PrerequisiteMain'
        )
        try
        {
            $null = & $script:LinuxJavaPath @arguments 2> $null
        }
        catch
        {
            # Java/JDBC details may contain sensitive connection material and are never emitted.
        }
        if (-not (Test-Path -LiteralPath $resultFile -PathType Leaf))
        {
            throw 'BLOCKED / PRECREATE_READBACK_UNAVAILABLE'
        }
        return Assert-PreCreateReadback (Read-JsonFile $resultFile)
    }
    finally
    {
        foreach ($name in $values.Keys)
        {
            [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
        }
        $password = $null
        if (Test-Path -LiteralPath $resultFile)
        {
            Remove-Item -LiteralPath $resultFile -Force
        }
    }
}

function Invoke-PreCreatePrerequisiteEvaluation
{
    param([switch]$ForPrepare)

    $checkedAt = (Get-UtcNow).ToString('o')
    try
    {
        Assert-RootLinux
        if (-not $ForPrepare -and -not [string]::IsNullOrWhiteSpace($RunId))
        {
            throw 'BLOCKED / PRECREATE_RUN_ID_FORBIDDEN'
        }
        $descriptor = Read-PreCreateDescriptor
        return [pscustomobject][ordered]@{
            Result = Assert-PreCreateResult `
                (New-PreCreateResult $checkedAt (Invoke-PreCreateJavaReadback $descriptor))
            Descriptor = $descriptor
        }
    }
    catch
    {
        return [pscustomobject][ordered]@{
            Result = Assert-PreCreateResult (New-PreCreateResult $checkedAt $null)
            Descriptor = $null
        }
    }
}

function Invoke-PreCreatePrerequisite
{
    return (Invoke-PreCreatePrerequisiteEvaluation).Result
}

function ConvertTo-SystemdLiteral
{
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    Assert-LiteralValue $Value
    return '"' + $Value.Replace('\', '\\').Replace('"', '\"') + '"'
}

function New-EnvironmentFileContent
{
    param([Parameter(Mandatory = $true)][hashtable]$Values)

    $lines = foreach ($name in @($Values.Keys | Sort-Object))
    {
        if ($name -notmatch '^[A-Z][A-Z0-9_]{1,95}$')
        {
            throw 'BLOCKED / CONFIG_KEY_INVALID'
        }
        "$name=$( ConvertTo-SystemdLiteral ([string]$Values[$name]) )"
    }
    return ($lines -join "`n") + "`n"
}

function Get-LifecyclePath
{
    param([Parameter(Mandatory = $true)][string]$Value)
    return "$( Get-ControlRoot $Value )/lifecycle.json"
}

function Set-LifecycleState
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$NextState,
        [Parameter(Mandatory = $true)][string]$ReasonCode
    )

    Assert-RunId $Value
    if ($ReasonCode -notmatch $script:SafeCodePattern)
    {
        throw 'BLOCKED / REASON_CODE_INVALID'
    }
    $path = Get-LifecyclePath $Value
    $current = Read-JsonFile $path
    $currentState = [string]$current.state
    if (-not $script:AllowedTransitions.ContainsKey($currentState) -or
            $script:AllowedTransitions[$currentState] -notcontains $NextState)
    {
        throw 'BLOCKED / ILLEGAL_LIFECYCLE_TRANSITION'
    }
    Write-JsonReplaceAtomic $path ([ordered]@{
        schemaVersion = 'gatew-soak-lifecycle-v1'
        runId = $Value
        state = $NextState
        stateSequence = [long]$current.stateSequence + 1
        reasonCode = $ReasonCode
        observedAt = (Get-UtcNow).ToString('o')
    })
    if (Test-LinuxPlatform)
    {
        Set-OwnerMode $path 'root:root' '600'
    }
    return Read-JsonFile $path
}

function Get-HistoricalEvidenceSnapshot
{
    param([Parameter(Mandatory = $true)][string]$CurrentRunId)

    $roots = @(
        '/opt/nexus-quant/gatew-soak/evidence/gatew-okx-readonly-soak',
        $script:StateRoot
    )
    $records = @()
    foreach ($root in $roots)
    {
        if (-not (Test-Path -LiteralPath $root -PathType Container))
        {
            continue
        }
        foreach ($file in Get-ChildItem -LiteralPath $root -File -Recurse -ErrorAction Stop)
        {
            if ($file.FullName -like "$( Get-RunRoot $CurrentRunId )*")
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
    param([Parameter(Mandatory = $true)][string]$Value)

    $path = "$( Get-ControlRoot $Value )/historical-evidence-hashes.json"
    $before = @(Read-JsonFile $path)
    $after = @(Get-HistoricalEvidenceSnapshot $Value)
    return (ConvertTo-CompactJson $before) -ceq (ConvertTo-CompactJson $after)
}

function Install-OfflineDropIns
{
    param([Parameter(Mandatory = $true)][string]$Value)

    $workerDropIn = "$( $script:RuntimeSystemdRoot )/$( Get-WorkerUnitName $Value ).d"
    Ensure-Directory $workerDropIn 'root:root' '755'
    $credentialPath = "$( $script:CredentialRoot )/offline-db-password.cred"
    $workerContent = @"
[Service]
LoadCredentialEncrypted=
LoadCredentialEncrypted=db-password:$credentialPath
IPAddressDeny=any
IPAddressAllow=localhost
"@
    Write-TextReplaceAtomic (Join-Path $workerDropIn 'offline.conf') $workerContent
    Set-OwnerMode (Join-Path $workerDropIn 'offline.conf') 'root:root' '644'
    Invoke-Native $script:SystemctlPath @('daemon-reload') | Out-Null
}

function Remove-OfflineDropIns
{
    param([Parameter(Mandatory = $true)][string]$Value)

    foreach ($directory in @(
        "$( $script:RuntimeSystemdRoot )/$( Get-WorkerUnitName $Value ).d",
        "$( $script:RuntimeSystemdRoot )/$( Get-FailCloseUnitName $Value ).d"
    ))
    {
        if (Test-Path -LiteralPath $directory -PathType Container)
        {
            Assert-PathBelowRoot $script:RuntimeSystemdRoot $directory
            Remove-Item -LiteralPath $directory -Recurse -Force
        }
    }
    Invoke-Native $script:SystemctlPath @('daemon-reload') | Out-Null
}

function Prepare-FormalRun
{
    Assert-RootLinux
    if ($RunMode -eq 'REAL_READONLY_SOAK')
    {
        if (-not [string]::IsNullOrWhiteSpace($DatabaseUrl) -or
                -not [string]::IsNullOrWhiteSpace($DatabaseUser) -or
                -not [string]::IsNullOrWhiteSpace($DatabasePasswordSourceFile))
        {
            throw 'BLOCKED / REAL_DATABASE_OPERATOR_INPUT_FORBIDDEN'
        }
        $preCreateEvaluation = Invoke-PreCreatePrerequisiteEvaluation -ForPrepare
        if (-not [bool]$preCreateEvaluation.Result.readyForAttemptCreation -or
                $null -eq $preCreateEvaluation.Descriptor)
        {
            throw 'BLOCKED / PRECREATE_PREREQUISITE_REQUIRED'
        }
        $preCreateDescriptor = $preCreateEvaluation.Descriptor
        $DatabaseUrl = Get-PreCreateDatabaseUrl $preCreateDescriptor
        $DatabaseUser = [string]$preCreateDescriptor.databaseUser
    }
    $release = Get-ReleaseIdentity
    if (-not [string]::IsNullOrWhiteSpace($ExpectedCommit) -and
            [string]$release.sourceCommit -cne $ExpectedCommit.ToLowerInvariant())
    {
        throw 'BLOCKED / RELEASE_SOURCE_COMMIT_MISMATCH'
    }
    if ($StartingCiRun -cnotmatch '^[1-9][0-9]{0,19}$')
    {
        throw 'BLOCKED / EXACT_HEAD_CI_METADATA_REQUIRED'
    }
    if ($RunMode -eq 'REAL_READONLY_SOAK' -and
            ([string]$release.sourceTreeMode -ne 'EXACT_COMMIT' -or
                    [string]$release.releaseId -cne [string]$release.sourceCommit))
    {
        throw 'BLOCKED / REAL_RUN_EXACT_RELEASE_REQUIRED'
    }
    $effectiveRunId = if ( [string]::IsNullOrWhiteSpace($RunId))
    {
        New-RunId
    }
    else
    {
        $RunId
    }
    Assert-RunId $effectiveRunId
    $runRoot = Get-RunRoot $effectiveRunId
    if (Test-Path -LiteralPath $runRoot)
    {
        throw 'BLOCKED / RUN_ID_ALREADY_EXISTS'
    }
    if ($RunMode -eq 'OFFLINE_ISOLATED_ACCEPTANCE' -and [string]::IsNullOrWhiteSpace($DatabaseUrl))
    {
        $DatabaseUrl = [Environment]::GetEnvironmentVariable('NQ_GATEW_SOAK_DB_URL', 'Process')
    }
    if ($RunMode -eq 'OFFLINE_ISOLATED_ACCEPTANCE' -and [string]::IsNullOrWhiteSpace($DatabaseUser))
    {
        $DatabaseUser = [Environment]::GetEnvironmentVariable('NQ_GATEW_SOAK_DB_USER', 'Process')
    }
    Assert-LiteralValue $DatabaseUrl
    Assert-LiteralValue $DatabaseUser
    if ($DatabaseUrl -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost):[0-9]{1,5}/[A-Za-z0-9_]*(gatew|soak)[A-Za-z0-9_]*$' -or
            $DatabaseUser -notmatch '^[A-Za-z_][A-Za-z0-9_-]{0,62}$')
    {
        throw 'BLOCKED / SOAK_DATABASE_CONFIG_INVALID'
    }
    if ($RunMode -eq 'OFFLINE_ISOLATED_ACCEPTANCE')
    {
        $DatabaseSchema = "gatew_offline_$($effectiveRunId.Substring($effectiveRunId.Length - 8) )"
    }
    elseif ([string]::IsNullOrWhiteSpace($DatabaseSchema))
    {
        $DatabaseSchema = 'public'
    }
    if (($RunMode -eq 'OFFLINE_ISOLATED_ACCEPTANCE' -and $DatabaseSchema -notmatch '^gatew_offline_[a-f0-9]{8}$') -or
            ($RunMode -eq 'REAL_READONLY_SOAK' -and $DatabaseSchema -ne 'public'))
    {
        throw 'BLOCKED / SOAK_DATABASE_SCHEMA_INVALID'
    }
    $credentialName = if ($RunMode -eq 'OFFLINE_ISOLATED_ACCEPTANCE')
    {
        'offline-db-password.cred'
    }
    else
    {
        'db-password.cred'
    }
    if (-not (Test-Path -LiteralPath "$( $script:CredentialRoot )/$credentialName" -PathType Leaf) -or
            ($RunMode -eq 'REAL_READONLY_SOAK' -and
                    -not (Test-Path -LiteralPath "$( $script:CredentialRoot )/credential-master-key.cred" -PathType Leaf)))
    {
        throw 'BLOCKED / ENCRYPTED_CREDENTIAL_REQUIRED'
    }

    Ensure-Directory $script:StateRoot "root:$( $script:LinuxRuntimeGroup )" '710'
    Ensure-Directory $runRoot "root:$( $script:LinuxRuntimeGroup )" '710'
    Ensure-Directory (Get-ControlRoot $effectiveRunId) "root:$( $script:LinuxRuntimeGroup )" '710'
    Ensure-Directory (Get-EvidenceRoot $effectiveRunId) `
        "$( $script:LinuxRuntimeUser ):$( $script:LinuxRuntimeGroup )" '700'
    $controlRoot = Get-ControlRoot $effectiveRunId
    $evidenceRoot = Get-EvidenceRoot $effectiveRunId
    $workerValues = @{
        NQ_GATEW_RELEASE_ROOT = [string]$release.releaseRoot
        NQ_GATEW_RELEASE_ID = [string]$release.releaseId
        NQ_GATEW_RELEASE_MANIFEST_SHA256 = [string]$release.manifestSha256
        NQ_GATEW_RUN_MODE = $RunMode
        NQ_GATEW_SOAK_DB_URL = $DatabaseUrl
        NQ_GATEW_SOAK_DB_USER = $DatabaseUser
        NQ_GATEW_SOAK_DB_SCHEMA = $DatabaseSchema
        NQ_GATEW_FORMAL_EVIDENCE_ROOT = $evidenceRoot
        NQ_GATEW_SECRET_SOURCE = 'SYSTEMD_CREDENTIALS'
        NQ_GATEW_FORMAL_SYSTEMD = 'true'
        NQ_GATEW_OFFLINE_HEARTBEAT_SECONDS = [string]$SmokeHeartbeatSeconds
    }
    if ($RunMode -eq 'REAL_READONLY_SOAK')
    {
        foreach ($name in @(
            'SPRING_PROFILES_ACTIVE', 'NQ_GATEW_OKX_READONLY_SOAK_ENABLED', 'CI', 'NQ_NO_OUTBOUND',
            'NQ_LIVE_ENABLED', 'NQ_REAL_ORDER_SUBMISSION_ENABLED', 'NQ_TRANSFER_ENABLED', 'NQ_WITHDRAW_ENABLED',
            'NQ_AI_ENABLED', 'NQ_DH_RUNTIME_ENABLED', 'NQ_REAL_PROVIDER_ENABLED', 'NQ_REAL_CLIENT_ENABLED',
            'NQ_REAL_EXCHANGE_ENABLED', 'NQ_GATEW_SOAK_OWNER_ID', 'NQ_GATEW_SOAK_ACCOUNT_ID',
            'NQ_GATEW_SOAK_CURRENCIES'
        ))
        {
            $value = [Environment]::GetEnvironmentVariable($name, 'Process')
            Assert-LiteralValue ([string]$value)
            $workerValues[$name] = [string]$value
        }
    }
    $failCloseValues = @{
        NQ_GATEW_RELEASE_ROOT = [string]$release.releaseRoot
        NQ_GATEW_RELEASE_ID = [string]$release.releaseId
        NQ_GATEW_RELEASE_MANIFEST_SHA256 = [string]$release.manifestSha256
    }
    Write-TextCreateOnce (Join-Path $controlRoot 'worker.env') (New-EnvironmentFileContent $workerValues)
    Write-TextCreateOnce (Join-Path $controlRoot 'failclose.env') (New-EnvironmentFileContent $failCloseValues)
    Write-JsonCreateOnce (Get-LifecyclePath $effectiveRunId) ([ordered]@{
        schemaVersion = 'gatew-soak-lifecycle-v1'
        runId = $effectiveRunId
        state = 'PREPARING'
        stateSequence = 1L
        reasonCode = 'FORMAL_PREPARE_STARTED'
        observedAt = (Get-UtcNow).ToString('o')
    })
    Write-JsonCreateOnce (Join-Path $controlRoot 'frozen-config.json') ([ordered]@{
        schemaVersion = 'gatew-soak-frozen-config-v3'
        runId = $effectiveRunId
        runMode = $RunMode
        databaseUrl = $DatabaseUrl
        databaseUser = $DatabaseUser
        databaseSchema = $DatabaseSchema
        offlineHeartbeatSeconds = $SmokeHeartbeatSeconds
        releaseId = [string]$release.releaseId
        sourceCommit = [string]$release.sourceCommit
        sourceTreeMode = [string]$release.sourceTreeMode
        releaseManifestSha256 = [string]$release.manifestSha256
        releaseRoot = [string]$release.releaseRoot
        startingCiRun = $StartingCiRun
        workerUnit = Get-WorkerUnitName $effectiveRunId
        failCloseUnit = Get-FailCloseUnitName $effectiveRunId
        acceptanceClockStarted = $false
        acceptanceStartAt = $null
        plannedAcceptanceAt = $null
        preparedAt = (Get-UtcNow).ToString('o')
    })
    Write-JsonCreateOnce (Join-Path $controlRoot 'historical-evidence-hashes.json') `
        @(Get-HistoricalEvidenceSnapshot $effectiveRunId)

    $preparedAt = Get-UtcNow
    Write-TextCreateOnce (Join-Path $evidenceRoot 'samples.jsonl') ''
    Write-TextCreateOnce (Join-Path $evidenceRoot 'failures.jsonl') ''
    Write-JsonCreateOnce (Join-Path $evidenceRoot 'manifest.json') ([ordered]@{
        runId = $effectiveRunId
        harnessCommit = [string]$release.sourceCommit
        releaseId = [string]$release.releaseId
        releaseManifestSha256 = [string]$release.manifestSha256
        startingCiRun = $StartingCiRun
        preparedAt = $preparedAt.ToString('o')
        acceptanceClockStarted = $false
        acceptanceStartAt = $null
        plannedAcceptanceAt = $null
        durationHours = 168
        cadenceSeconds = 60
        maxTransientRetries = 2
        maxConsecutiveAuthFailures = 3
        venue = 'OKX'
        environment = $RunMode
        profile = 'gatew-okx-readonly-soak'
        applicationVersion = "0.1.0-SNAPSHOT+$(([string]$release.sourceCommit).Substring(0, 12) )"
        endpointAllowlistVersion = 'gatew-okx-private-readonly-v1'
        flywayVersion = '35'
        hostFingerprint = 'FORMAL_SYSTEMD_ROOT_CONTROLLED'
        supervisorArtifactSha256 = Get-Sha256File "$( $script:ReleaseRoot )/bin/$( $script:WorkerHelperName )"
        launcherSchemaVersion = 'gatew-soak-launcher-v2'
        evidenceSchemaVersion = 'gatew-soak-evidence-v2'
    })
    Write-JsonCreateOnce (Join-Path $evidenceRoot 'heartbeat.json') ([ordered]@{
        runId = $effectiveRunId
        state = 'PREPARING'
        reasonCode = 'FORMAL_SYSTEMD_START_PENDING'
        observedAt = (Get-UtcNow).ToString('o')
        lastSequence = 0L
        consecutiveAuthenticationFailures = 0
    })
    foreach ($file in Get-ChildItem -LiteralPath $evidenceRoot -File)
    {
        Set-OwnerMode $file.FullName "$( $script:LinuxRuntimeUser ):$( $script:LinuxRuntimeGroup )" '600'
    }
    foreach ($file in Get-ChildItem -LiteralPath $controlRoot -File)
    {
        Set-OwnerMode $file.FullName 'root:root' '600'
    }
    Assert-RunDirectoryContract $effectiveRunId
    if ($RunMode -eq 'OFFLINE_ISOLATED_ACCEPTANCE')
    {
        Install-OfflineDropIns $effectiveRunId
    }
    Set-LifecycleState $effectiveRunId 'STARTING' 'FORMAL_UNIT_START_AUTHORIZED' | Out-Null
    return [pscustomobject]@{
        decision = 'PASS / FORMAL_SOAK_PREPARED'
        runId = $effectiveRunId
        runMode = $RunMode
        releaseId = [string]$release.releaseId
        releaseManifestSha256 = [string]$release.manifestSha256
        lifecycleState = 'STARTING'
        historicalEvidenceCount = @((Read-JsonFile (Join-Path $controlRoot 'historical-evidence-hashes.json'))).Count
        acceptanceClockStarted = $false
        acceptanceStartAt = $null
        plannedAcceptanceAt = $null
    }
}

function ConvertFrom-SystemctlShow
{
    param([Parameter(Mandatory = $true)][object[]]$Lines)

    $values = @{ }
    foreach ($lineValue in $Lines)
    {
        $line = [string]$lineValue
        $index = $line.IndexOf('=')
        if ($index -gt 0)
        {
            $values[$line.Substring(0, $index)] = $line.Substring($index + 1)
        }
    }
    foreach ($required in @(
        'LoadState', 'ActiveState', 'SubState', 'MainPID', 'ExecMainStatus', 'FragmentPath',
        'User', 'Group', 'Restart', 'KillMode', 'RuntimeDirectory', 'StateDirectory',
        'IPAddressDeny', 'IPAddressAllow', 'NRestarts', 'ExecMainStartTimestampMonotonic'
    ))
    {
        if (-not $values.ContainsKey($required))
        {
            $values[$required] = ''
        }
    }
    $mainPid = 0L
    $nRestarts = 0L
    $execMainStartTimestampMonotonic = 0L
    $execStatusValue = 0
    [long]::TryParse([string]$values.MainPID, [ref]$mainPid) | Out-Null
    [long]::TryParse([string]$values.NRestarts, [ref]$nRestarts) | Out-Null
    [long]::TryParse(
            [string]$values.ExecMainStartTimestampMonotonic,
            [ref]$execMainStartTimestampMonotonic
    ) | Out-Null
    [int]::TryParse([string]$values.ExecMainStatus, [ref]$execStatusValue) | Out-Null
    return [pscustomobject]@{
        LoadState = [string]$values.LoadState
        ActiveState = [string]$values.ActiveState
        SubState = [string]$values.SubState
        MainPID = $mainPid
        ExecMainStatus = $execStatusValue
        FragmentPath = [string]$values.FragmentPath
        User = [string]$values.User
        Group = [string]$values.Group
        Restart = [string]$values.Restart
        KillMode = [string]$values.KillMode
        RuntimeDirectory = [string]$values.RuntimeDirectory
        StateDirectory = [string]$values.StateDirectory
        IPAddressDeny = [string]$values.IPAddressDeny
        IPAddressAllow = [string]$values.IPAddressAllow
        NRestarts = $nRestarts
        ExecMainStartTimestampMonotonic = $execMainStartTimestampMonotonic
    }
}

function Get-UnitState
{
    param([Parameter(Mandatory = $true)][string]$UnitName)

    $properties = @(
        'LoadState', 'ActiveState', 'SubState', 'MainPID', 'ExecMainStatus', 'FragmentPath',
        'User', 'Group', 'Restart', 'KillMode', 'RuntimeDirectory', 'StateDirectory',
        'IPAddressDeny', 'IPAddressAllow', 'NRestarts', 'ExecMainStartTimestampMonotonic'
    )
    $result = Invoke-Native $script:SystemctlPath @(
        'show', $UnitName, '--no-pager', "--property=$( $properties -join ',' )"
    ) -AllowFailure
    if ($result.ExitCode -ne 0)
    {
        throw 'FAIL / SYSTEMD_STATE_UNAVAILABLE'
    }
    return ConvertFrom-SystemctlShow $result.Lines
}

function Assert-FormalWorkerState
{
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][string]$Value,
        [switch]$AllowInactive
    )

    $fragment = "$( $script:SystemdRoot )/$( $script:WorkerTemplate )"
    $base = $State.LoadState -eq 'loaded' -and
            $State.FragmentPath -eq $fragment -and
            $State.User -eq $script:LinuxRuntimeUser -and
            $State.Group -eq $script:LinuxRuntimeGroup -and
            $State.Restart -eq 'no' -and
            $State.KillMode -eq 'mixed'
    if (-not $base)
    {
        throw 'FAIL / FORMAL_UNIT_CONTRACT_INVALID'
    }
    if (-not $AllowInactive -and
            ($State.ActiveState -ne 'active' -or $State.SubState -ne 'running' -or $State.MainPID -le 0))
    {
        throw 'FAIL / FORMAL_UNIT_NOT_RUNNING'
    }
    if ($AllowInactive -and $State.ActiveState -eq 'inactive' -and $State.MainPID -ne 0)
    {
        throw 'FAIL / FORMAL_UNIT_PID_NOT_ZERO'
    }
    $config = Read-JsonFile "$( Get-ControlRoot $Value )/frozen-config.json"
    if ([string]$config.runMode -eq 'OFFLINE_ISOLATED_ACCEPTANCE')
    {
        if ([string]$State.IPAddressDeny -notmatch '(?i)(any|0\.0\.0\.0/0|::/0)' -or
                [string]$State.IPAddressAllow -notmatch '(?i)(localhost|127\.0\.0\.0/8|::1)')
        {
            throw 'FAIL / OFFLINE_NETWORK_POLICY_INVALID'
        }
    }
}

function Get-HeartbeatSequence
{
    param([Parameter(Mandatory = $true)]$Heartbeat)

    $property = $Heartbeat.PSObject.Properties['lastSequence']
    if ($null -eq $property)
    {
        $property = $Heartbeat.PSObject.Properties['sequence']
    }
    if ($null -eq $property)
    {
        return 0L
    }
    return [long]$property.Value
}

function Wait-ForWorkerReady
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][long]$RequiredSequence
    )

    $deadline = (Get-UtcNow).AddMinutes(10)
    while ((Get-UtcNow) -lt $deadline)
    {
        $terminalPath = "$( Get-ControlRoot $Value )/terminal-status.json"
        if (Test-Path -LiteralPath $terminalPath -PathType Leaf)
        {
            throw 'FAIL / WORKER_TERMINATED_DURING_START'
        }
        $state = Get-UnitState (Get-WorkerUnitName $Value)
        if ($state.ActiveState -eq 'failed' -or ($state.ActiveState -eq 'inactive' -and $state.MainPID -eq 0))
        {
            Start-Sleep -Milliseconds 250
            continue
        }
        $heartbeat = Read-JsonFile "$( Get-EvidenceRoot $Value )/heartbeat.json"
        if ((Get-HeartbeatSequence $heartbeat) -ge $RequiredSequence -and
                [string]$heartbeat.state -eq 'RUNNING')
        {
            return $heartbeat
        }
        Start-Sleep -Milliseconds 250
    }
    throw 'FAIL / WORKER_READY_TIMEOUT'
}

function Get-UnitStartSnapshotPath
{
    param([Parameter(Mandatory = $true)][string]$Value)
    return "$( Get-ControlRoot $Value )/unit-start-snapshot.json"
}

function Assert-WorkerStartRecord
{
    param(
        [Parameter(Mandatory = $true)]$Record,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $fields = @('schemaVersion', 'runId', 'mainPid', 'unitName', 'startedAt')
    $startedAt = [DateTimeOffset]::MinValue
    if ((@($Record.PSObject.Properties.Name) -join '|') -cne ($fields -join '|') -or
            [string]$Record.schemaVersion -cne 'gatew-soak-worker-start-v1' -or
            [string]$Record.runId -cne $Value -or
            [long]$Record.mainPid -le 0 -or
            [string]$Record.unitName -cne (Get-WorkerUnitName $Value) -or
            -not [DateTimeOffset]::TryParse([string]$Record.startedAt, [ref]$startedAt) -or
            $startedAt.Offset -ne [TimeSpan]::Zero)
    {
        throw 'FAIL / WORKER_START_RECORD_INVALID'
    }
    return $Record
}

function Assert-UnitStartSnapshot
{
    param(
        [Parameter(Mandatory = $true)]$Snapshot,
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$ReleaseCommit
    )

    $fields = @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid', 'nRestarts',
        'execMainStartTimestampMonotonic', 'recordedAt', 'checksum'
    )
    $recordedAt = [DateTimeOffset]::MinValue
    if ((@($Snapshot.PSObject.Properties.Name) -join '|') -cne ($fields -join '|') -or
            [string]$Snapshot.schemaVersion -cne 'gatew-soak-unit-start-v1' -or
            [string]$Snapshot.runId -cne $Value -or
            [string]$Snapshot.releaseCommit -cne $ReleaseCommit -or
            [long]$Snapshot.mainPid -le 0 -or [long]$Snapshot.nRestarts -ne 0 -or
            [long]$Snapshot.execMainStartTimestampMonotonic -le 0 -or
            -not [DateTimeOffset]::TryParse([string]$Snapshot.recordedAt, [ref]$recordedAt) -or
            [string]$Snapshot.checksum -cnotmatch '^[a-f0-9]{64}$')
    {
        throw 'FAIL / UNIT_START_SNAPSHOT_INVALID'
    }
    $expectedChecksum = Get-GateWRecordChecksum $Snapshot @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid', 'nRestarts',
        'execMainStartTimestampMonotonic', 'recordedAt'
    )
    if ([string]$Snapshot.checksum -cne $expectedChecksum)
    {
        throw 'FAIL / UNIT_START_SNAPSHOT_INVALID'
    }
    return $Snapshot
}

function Record-UnitStartSnapshot
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)]$Config
    )

    if ([long]$State.MainPID -le 0 -or [long]$State.NRestarts -ne 0 -or
            [long]$State.ExecMainStartTimestampMonotonic -le 0)
    {
        throw 'FAIL / UNIT_START_SNAPSHOT_INVALID'
    }
    $workerStartPath = "$( Get-EvidenceRoot $Value )/worker-start.json"
    $workerStart = Read-JsonFile $workerStartPath
    Assert-WorkerStartRecord $workerStart $Value | Out-Null
    if ([long]$workerStart.mainPid -ne [long]$State.MainPID)
    {
        throw 'FAIL / UNIT_START_SNAPSHOT_INVALID'
    }
    Set-OwnerMode $workerStartPath 'root:root' '600'
    Assert-PosixContract $workerStartPath 'regular file' '600' 'root' 'root'
    $record = [ordered]@{
        schemaVersion = 'gatew-soak-unit-start-v1'
        runId = $Value
        releaseCommit = [string]$Config.sourceCommit
        mainPid = [long]$State.MainPID
        nRestarts = [long]$State.NRestarts
        execMainStartTimestampMonotonic = [long]$State.ExecMainStartTimestampMonotonic
        recordedAt = (Get-UtcNow).ToString('o')
    }
    $record.checksum = Get-GateWRecordChecksum ([pscustomobject]$record) @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid', 'nRestarts',
        'execMainStartTimestampMonotonic', 'recordedAt'
    )
    $created = Commit-CreateOnceJsonIdempotent `
        (Get-UnitStartSnapshotPath $Value) $record 'UNIT_START_SNAPSHOT_CONFLICT'
    $snapshot = Read-JsonFile (Get-UnitStartSnapshotPath $Value)
    Assert-UnitStartSnapshot $snapshot $Value ([string]$Config.sourceCommit) | Out-Null
    if (-not $created)
    {
        throw 'FAIL / SECOND_EXEC_MAIN_START'
    }
    return $snapshot
}

function Start-FormalRun
{
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
    if ([string]$lifecycle.state -ne 'STARTING')
    {
        throw 'BLOCKED / RUN_NOT_STARTABLE'
    }
    $config = Read-JsonFile "$( Get-ControlRoot $RunId )/frozen-config.json"
    Assert-FrozenReleaseBinding $config | Out-Null
    Invoke-Native $script:SystemctlPath @('start', (Get-WorkerUnitName $RunId)) | Out-Null
    $requiredSequence = if ([string]$config.runMode -eq 'OFFLINE_ISOLATED_ACCEPTANCE')
    {
        2L
    }
    else
    {
        1L
    }
    $heartbeat = Wait-ForWorkerReady $RunId $requiredSequence
    $state = Get-UnitState (Get-WorkerUnitName $RunId)
    Assert-FormalWorkerState $state $RunId
    $unitStart = Record-UnitStartSnapshot $RunId $state $config
    Set-LifecycleState $RunId 'RUNNING' 'FORMAL_WORKER_RUNNING' | Out-Null
    return [pscustomobject]@{
        decision = 'PASS / FORMAL_SYSTEMD_SOAK_STARTED'
        runId = $RunId
        runMode = $config.runMode
        unitName = Get-WorkerUnitName $RunId
        activeState = $state.ActiveState
        subState = $state.SubState
        mainPid = $state.MainPID
        nRestarts = $state.NRestarts
        execMainStartTimestampMonotonic = $state.ExecMainStartTimestampMonotonic
        unitStartSnapshotChecksum = $unitStart.checksum
        heartbeatSequence = Get-HeartbeatSequence $heartbeat
        heartbeatObservedAt = $heartbeat.observedAt
        acceptanceClockStarted = $false
        acceptanceStartAt = $null
        plannedAcceptanceAt = $null
    }
}

function ConvertTo-UtcRfc3339
{
    param([Parameter(Mandatory = $true)]$Value)

    $parsed = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse([string]$Value, [ref]$parsed))
    {
        throw 'BLOCKED / ACCEPTANCE_CLOCK_TIMESTAMP_INVALID'
    }
    return $parsed.UtcDateTime.ToString('o')
}

function Get-AcceptanceClockPath
{
    param([Parameter(Mandatory = $true)][string]$Value)
    return "$( Get-ControlRoot $Value )/acceptance-clock-start.json"
}

function Assert-AcceptanceClockRecord
{
    param(
        [Parameter(Mandatory = $true)]$Record,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $expected = @(
        'schemaVersion', 'runId', 'firstValidConfigPassAt', 'firstValidBalancePassAt',
        'freshSshVerificationAt', 'mainPid', 'sameMainPid', 'heartbeatAdvanced',
        'hashChainValid', 'forbiddenEndpointCount', 'secretExposureCount',
        'acceptanceStartAt', 'plannedAcceptanceAt', 'acceptanceClockStarted'
    )
    $firstConfig = [DateTimeOffset]::MinValue
    $firstBalance = [DateTimeOffset]::MinValue
    $freshSsh = [DateTimeOffset]::MinValue
    $acceptanceStart = [DateTimeOffset]::MinValue
    $plannedAcceptance = [DateTimeOffset]::MinValue
    $schemaInvalid = (@($Record.PSObject.Properties.Name) -join '|') -cne ($expected -join '|') -or
            [string]$Record.schemaVersion -ne 'gatew-soak-acceptance-clock-v1' -or
            [string]$Record.runId -ne $Value -or [long]$Record.mainPid -le 0 -or
            -not [bool]$Record.sameMainPid -or -not [bool]$Record.heartbeatAdvanced -or
            -not [bool]$Record.hashChainValid -or [int]$Record.forbiddenEndpointCount -ne 0 -or
            [int]$Record.secretExposureCount -ne 0 -or -not [bool]$Record.acceptanceClockStarted -or
            -not [DateTimeOffset]::TryParse([string]$Record.firstValidConfigPassAt, [ref]$firstConfig) -or
            -not [DateTimeOffset]::TryParse([string]$Record.firstValidBalancePassAt, [ref]$firstBalance) -or
            -not [DateTimeOffset]::TryParse([string]$Record.freshSshVerificationAt, [ref]$freshSsh) -or
            -not [DateTimeOffset]::TryParse([string]$Record.acceptanceStartAt, [ref]$acceptanceStart) -or
            -not [DateTimeOffset]::TryParse([string]$Record.plannedAcceptanceAt, [ref]$plannedAcceptance)
    $latestPrerequisite = @($firstConfig, $firstBalance, $freshSsh) |
            Sort-Object -Descending | Select-Object -First 1
    if ($schemaInvalid -or $acceptanceStart -ne $latestPrerequisite -or
            $plannedAcceptance -ne $acceptanceStart.AddHours(168))
    {
        throw 'BLOCKED / ACCEPTANCE_CLOCK_RECORD_INVALID'
    }
}

function Get-AcceptanceClockProjection
{
    param([Parameter(Mandatory = $true)][string]$Value)

    $path = Get-AcceptanceClockPath $Value
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        return [pscustomobject]@{
            acceptanceClockStarted = $false
            acceptanceStartAt = $null
            plannedAcceptanceAt = $null
        }
    }
    $record = Read-JsonFile $path
    Assert-AcceptanceClockRecord $record $Value
    if (Test-LinuxPlatform)
    {
        Assert-PosixContract $path 'regular file' '640' 'root' $script:LinuxRuntimeGroup
    }
    return [pscustomobject]@{
        acceptanceClockStarted = $true
        acceptanceStartAt = [string]$record.acceptanceStartAt
        plannedAcceptanceAt = [string]$record.plannedAcceptanceAt
    }
}

function Get-AcceptanceClockBindingPath
{
    param([Parameter(Mandatory = $true)][string]$Value)
    return "$( Get-ControlRoot $Value )/acceptance-clock-binding.json"
}

function Assert-AcceptanceClockBinding
{
    param(
        [Parameter(Mandatory = $true)]$Binding,
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)]$Config
    )

    $fields = @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid',
        'acceptanceClockSha256', 'boundAt', 'checksum'
    )
    $boundAt = [DateTimeOffset]::MinValue
    if ((@($Binding.PSObject.Properties.Name) -join '|') -cne ($fields -join '|') -or
            [string]$Binding.schemaVersion -cne 'gatew-soak-acceptance-clock-binding-v1' -or
            [string]$Binding.runId -cne $Value -or
            [string]$Binding.releaseCommit -cne [string]$Config.sourceCommit -or
            [long]$Binding.mainPid -le 0 -or
            [string]$Binding.acceptanceClockSha256 -cnotmatch '^[a-f0-9]{64}$' -or
            -not [DateTimeOffset]::TryParse([string]$Binding.boundAt, [ref]$boundAt) -or
            [string]$Binding.checksum -cnotmatch '^[a-f0-9]{64}$')
    {
        throw 'FAIL / ACCEPTANCE_CLOCK_BINDING_INVALID'
    }
    $expectedChecksum = Get-GateWRecordChecksum $Binding @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid',
        'acceptanceClockSha256', 'boundAt'
    )
    if ([string]$Binding.checksum -cne $expectedChecksum -or
            [string]$Binding.acceptanceClockSha256 -cne
                    (Get-Sha256File (Get-AcceptanceClockPath $Value)))
    {
        throw 'FAIL / ACCEPTANCE_CLOCK_DRIFT'
    }
    return $Binding
}

function Commit-AcceptanceClockBinding
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)]$Config,
        [Parameter(Mandatory = $true)][long]$MainPid
    )

    $record = [ordered]@{
        schemaVersion = 'gatew-soak-acceptance-clock-binding-v1'
        runId = $Value
        releaseCommit = [string]$Config.sourceCommit
        mainPid = $MainPid
        acceptanceClockSha256 = Get-Sha256File (Get-AcceptanceClockPath $Value)
        boundAt = (Get-UtcNow).ToString('o')
    }
    $record.checksum = Get-GateWRecordChecksum ([pscustomobject]$record) @(
        'schemaVersion', 'runId', 'releaseCommit', 'mainPid',
        'acceptanceClockSha256', 'boundAt'
    )
    Commit-CreateOnceJsonIdempotent `
        (Get-AcceptanceClockBindingPath $Value) $record 'ACCEPTANCE_CLOCK_BINDING_CONFLICT' | Out-Null
    $binding = Read-JsonFile (Get-AcceptanceClockBindingPath $Value)
    Assert-AcceptanceClockBinding $binding $Value $Config | Out-Null
    return $binding
}

function Commit-CreateOnceJsonIdempotent
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value,
        [Parameter(Mandatory = $true)][string]$ConflictCode,
        [string]$LinuxOwnerGroup = 'root:root',
        [ValidatePattern('^[0-7]{3,4}$')][string]$LinuxMode = '600'
    )

    $expected = ConvertTo-CompactJson $Value
    if (Test-Path -LiteralPath $Path -PathType Leaf)
    {
        if ((Get-Content -LiteralPath $Path -Raw).Trim() -ceq $expected)
        {
            return $false
        }
        throw "BLOCKED / $ConflictCode"
    }
    try
    {
        Write-JsonCreateOnce $Path $Value $LinuxOwnerGroup $LinuxMode
    }
    catch
    {
        if ((Test-Path -LiteralPath $Path -PathType Leaf) -and
                (Get-Content -LiteralPath $Path -Raw).Trim() -ceq $expected)
        {
            return $false
        }
        throw "BLOCKED / $ConflictCode"
    }
    return $true
}

function Record-FreshSshVerification
{
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    $path = "$( Get-ControlRoot $RunId )/fresh-ssh-verification.json"
    if (Test-Path -LiteralPath $path -PathType Leaf)
    {
        $existing = Read-JsonFile $path
        return [pscustomobject]@{
            decision = 'NO_CHANGE / FRESH_SSH_ALREADY_RECORDED'
            runId = $RunId
            mainPid = $existing.mainPid
            freshSshVerificationAt = $existing.freshSshVerificationAt
        }
    }
    $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
    if ([string]$lifecycle.state -ne 'RUNNING')
    {
        throw 'BLOCKED / RUN_NOT_RUNNING'
    }
    if ($PreviousMainPid -le 0 -or [string]::IsNullOrWhiteSpace($PreviousHeartbeatObservedAt) -or
            $MinimumHeartbeatSequence -lt 0)
    {
        throw 'BLOCKED / FRESH_SSH_BASELINE_REQUIRED'
    }
    $state = Get-UnitState (Get-WorkerUnitName $RunId)
    Assert-FormalWorkerState $state $RunId
    if ($state.MainPID -ne $PreviousMainPid)
    {
        throw 'FAIL / FRESH_SESSION_MAIN_PID_CHANGED'
    }
    $workerStart = Read-JsonFile "$( Get-EvidenceRoot $RunId )/worker-start.json"
    if ([long]$workerStart.mainPid -ne $state.MainPID)
    {
        throw 'FAIL / FRESH_SESSION_MAIN_PID_CHANGED'
    }
    $heartbeat = Read-JsonFile "$( Get-EvidenceRoot $RunId )/heartbeat.json"
    $sequence = Get-HeartbeatSequence $heartbeat
    $previousHeartbeat = [DateTimeOffset]::MinValue
    $currentHeartbeat = [DateTimeOffset]::MinValue
    if ($sequence -lt $MinimumHeartbeatSequence -or
            -not [DateTimeOffset]::TryParse($PreviousHeartbeatObservedAt, [ref]$previousHeartbeat) -or
            -not [DateTimeOffset]::TryParse([string]$heartbeat.observedAt, [ref]$currentHeartbeat) -or
            $currentHeartbeat -le $previousHeartbeat)
    {
        throw 'FAIL / FRESH_SESSION_HEARTBEAT_NOT_ADVANCED'
    }
    $record = [ordered]@{
        schemaVersion = 'gatew-soak-fresh-ssh-v1'
        runId = $RunId
        mainPid = $state.MainPID
        baselineHeartbeatSequence = $MinimumHeartbeatSequence
        observedHeartbeatSequence = $sequence
        baselineHeartbeatObservedAt = ConvertTo-UtcRfc3339 $PreviousHeartbeatObservedAt
        observedHeartbeatAt = ConvertTo-UtcRfc3339 $heartbeat.observedAt
        sameMainPid = $true
        heartbeatAdvanced = $true
        freshSshVerificationAt = (Get-UtcNow).UtcDateTime.ToString('o')
    }
    $created = Commit-CreateOnceJsonIdempotent $path $record 'FRESH_SSH_ALREADY_RECORDED'
    Set-OwnerMode $path 'root:root' '600'
    return [pscustomobject]@{
        decision = if ($created)
        {
            'PASS / FRESH_SSH_RECORDED'
        }
        else
        {
            'NO_CHANGE / FRESH_SSH_ALREADY_RECORDED'
        }
        runId = $RunId
        mainPid = $record.mainPid
        heartbeatAdvanced = $true
        freshSshVerificationAt = $record.freshSshVerificationAt
    }
}

function Get-FirstValidPassTimestamps
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$Mode
    )

    $firstConfig = $null
    $firstBalance = $null
    $requiredKillSwitch = if ($Mode -eq 'REAL_READONLY_SOAK')
    {
        'ENGAGED'
    }
    else
    {
        'DISENGAGED'
    }
    foreach ($line in Get-Content -LiteralPath "$( Get-EvidenceRoot $Value )/samples.jsonl")
    {
        if ( [string]::IsNullOrWhiteSpace($line))
        {
            continue
        }
        $sample = ConvertFrom-JsonPreservingTimestamps $line
        if ([string]$sample.resultStatus -ne 'PASSED_READ_ONLY' -or
                -not [bool]$sample.realCycleOutcomeProven -or
                [string]$sample.killSwitchObservedState -ne $requiredKillSwitch)
        {
            continue
        }
        $observedAt = ConvertTo-UtcRfc3339 $sample.observedAt
        if ($null -eq $firstConfig -and [string]$sample.accountConfigProbeStatus -eq 'SUCCEEDED')
        {
            $firstConfig = $observedAt
        }
        if ($null -eq $firstBalance -and [string]$sample.balanceProbeStatus -eq 'SUCCEEDED')
        {
            $firstBalance = $observedAt
        }
    }
    return [pscustomobject]@{ firstConfig = $firstConfig; firstBalance = $firstBalance }
}

function New-AcceptanceClockRecord
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [AllowNull()][string]$FirstConfigAt,
        [AllowNull()][string]$FirstBalanceAt,
        [AllowNull()][string]$FreshSshAt,
        [long]$MainPid,
        [bool]$SameMainPid,
        [bool]$HeartbeatAdvanced,
        [bool]$HashChainValid,
        [int]$ForbiddenEndpointCount,
        [int]$SecretExposureCount
    )

    if ([string]::IsNullOrWhiteSpace($FirstConfigAt) -or
            [string]::IsNullOrWhiteSpace($FirstBalanceAt))
    {
        throw 'BLOCKED / ACCEPTANCE_CLOCK_VALID_SAMPLES_REQUIRED'
    }
    if ( [string]::IsNullOrWhiteSpace($FreshSshAt))
    {
        throw 'BLOCKED / ACCEPTANCE_CLOCK_FRESH_SSH_REQUIRED'
    }
    if ($MainPid -le 0 -or -not $SameMainPid)
    {
        throw 'FAIL / ACCEPTANCE_CLOCK_MAIN_PID_CHANGED'
    }
    if (-not $HeartbeatAdvanced)
    {
        throw 'FAIL / ACCEPTANCE_CLOCK_HEARTBEAT_NOT_ADVANCED'
    }
    if (-not $HashChainValid -or $ForbiddenEndpointCount -ne 0 -or $SecretExposureCount -ne 0)
    {
        throw 'FAIL / ACCEPTANCE_CLOCK_EVIDENCE_INVALID'
    }
    $configAt = [DateTimeOffset]::Parse((ConvertTo-UtcRfc3339 $FirstConfigAt))
    $balanceAt = [DateTimeOffset]::Parse((ConvertTo-UtcRfc3339 $FirstBalanceAt))
    $freshAt = [DateTimeOffset]::Parse((ConvertTo-UtcRfc3339 $FreshSshAt))
    $acceptanceAt = @($configAt, $balanceAt, $freshAt) |
            Sort-Object -Descending | Select-Object -First 1
    return [ordered]@{
        schemaVersion = 'gatew-soak-acceptance-clock-v1'
        runId = $Value
        firstValidConfigPassAt = $configAt.UtcDateTime.ToString('o')
        firstValidBalancePassAt = $balanceAt.UtcDateTime.ToString('o')
        freshSshVerificationAt = $freshAt.UtcDateTime.ToString('o')
        mainPid = $MainPid
        sameMainPid = $true
        heartbeatAdvanced = $true
        hashChainValid = $true
        forbiddenEndpointCount = 0
        secretExposureCount = 0
        acceptanceStartAt = $acceptanceAt.UtcDateTime.ToString('o')
        plannedAcceptanceAt = $acceptanceAt.AddHours(168).UtcDateTime.ToString('o')
        acceptanceClockStarted = $true
    }
}

function Start-AcceptanceClock
{
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    $clockPath = Get-AcceptanceClockPath $RunId
    $config = Read-JsonFile "$( Get-ControlRoot $RunId )/frozen-config.json"
    Assert-FrozenReleaseBinding $config | Out-Null
    if (Test-Path -LiteralPath $clockPath -PathType Leaf)
    {
        $binding = Read-JsonFile (Get-AcceptanceClockBindingPath $RunId)
        Assert-AcceptanceClockBinding $binding $RunId $config | Out-Null
        $projection = Get-AcceptanceClockProjection $RunId
        return [pscustomobject]@{
            decision = 'NO_CHANGE / ACCEPTANCE_CLOCK_ALREADY_STARTED'
            runId = $RunId
            acceptanceClockStarted = $projection.acceptanceClockStarted
            acceptanceStartAt = $projection.acceptanceStartAt
            plannedAcceptanceAt = $projection.plannedAcceptanceAt
            acceptanceClockBindingChecksum = $binding.checksum
        }
    }
    $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
    if ([string]$lifecycle.state -ne 'RUNNING')
    {
        throw 'BLOCKED / RUN_NOT_RUNNING'
    }
    $fresh = Read-JsonFile "$( Get-ControlRoot $RunId )/fresh-ssh-verification.json"
    $freshExpected = @(
        'schemaVersion', 'runId', 'mainPid', 'baselineHeartbeatSequence', 'observedHeartbeatSequence',
        'baselineHeartbeatObservedAt', 'observedHeartbeatAt', 'sameMainPid', 'heartbeatAdvanced',
        'freshSshVerificationAt'
    )
    if ((@($fresh.PSObject.Properties.Name) -join '|') -cne ($freshExpected -join '|') -or
            [string]$fresh.schemaVersion -ne 'gatew-soak-fresh-ssh-v1' -or
            [string]$fresh.runId -ne $RunId -or -not [bool]$fresh.sameMainPid -or
            -not [bool]$fresh.heartbeatAdvanced)
    {
        throw 'BLOCKED / FRESH_SSH_VERIFICATION_INVALID'
    }
    $state = Get-UnitState (Get-WorkerUnitName $RunId)
    Assert-FormalWorkerState $state $RunId
    $workerStart = Read-JsonFile "$( Get-EvidenceRoot $RunId )/worker-start.json"
    if ($state.MainPID -ne [long]$fresh.mainPid -or $state.MainPID -ne [long]$workerStart.mainPid)
    {
        throw 'FAIL / ACCEPTANCE_CLOCK_MAIN_PID_CHANGED'
    }
    $heartbeat = Read-JsonFile "$( Get-EvidenceRoot $RunId )/heartbeat.json"
    $heartbeatAt = [DateTimeOffset]::MinValue
    $freshHeartbeatAt = [DateTimeOffset]::MinValue
    if (-not [DateTimeOffset]::TryParse([string]$heartbeat.observedAt, [ref]$heartbeatAt) -or
            -not [DateTimeOffset]::TryParse([string]$fresh.observedHeartbeatAt, [ref]$freshHeartbeatAt) -or
            $heartbeatAt -lt $freshHeartbeatAt)
    {
        throw 'FAIL / ACCEPTANCE_CLOCK_HEARTBEAT_NOT_ADVANCED'
    }
    $evidence = Invoke-WorkerEvidenceVerify $RunId
    if ([string]$evidence.result -ne 'PASS / HASH_CHAIN_VERIFIED' -or
            [int]$evidence.forbiddenEndpointCount -ne 0 -or [int]$evidence.secretExposureCount -ne 0)
    {
        throw 'FAIL / ACCEPTANCE_CLOCK_EVIDENCE_INVALID'
    }
    $passes = Get-FirstValidPassTimestamps $RunId ([string]$config.runMode)
    if ($null -eq $passes.firstConfig -or $null -eq $passes.firstBalance)
    {
        throw 'BLOCKED / ACCEPTANCE_CLOCK_VALID_SAMPLES_REQUIRED'
    }
    $record = New-AcceptanceClockRecord `
        $RunId ([string]$passes.firstConfig) ([string]$passes.firstBalance) `
        ([string]$fresh.freshSshVerificationAt) $state.MainPID $true $true $true `
        ([int]$evidence.forbiddenEndpointCount) ([int]$evidence.secretExposureCount)
    $created = Commit-CreateOnceJsonIdempotent $clockPath $record `
        'ACCEPTANCE_CLOCK_ALREADY_STARTED_DIFFERENT' "root:$( $script:LinuxRuntimeGroup )" '640'
    Set-OwnerMode $clockPath "root:$( $script:LinuxRuntimeGroup )" '640'
    $binding = Commit-AcceptanceClockBinding $RunId $config $state.MainPID
    $projection = Get-AcceptanceClockProjection $RunId
    return [pscustomobject]@{
        decision = if ($created)
        {
            'PASS / ACCEPTANCE_CLOCK_STARTED'
        }
        else
        {
            'NO_CHANGE / ACCEPTANCE_CLOCK_ALREADY_STARTED'
        }
        runId = $RunId
        acceptanceClockStarted = $projection.acceptanceClockStarted
        acceptanceStartAt = $projection.acceptanceStartAt
        plannedAcceptanceAt = $projection.plannedAcceptanceAt
        acceptanceClockBindingChecksum = $binding.checksum
    }
}

function Get-ResidualWorkerProcesses
{
    param([Parameter(Mandatory = $true)][string]$Value)

    $processIds = @()
    if (-not (Test-Path -LiteralPath '/proc' -PathType Container))
    {
        return @()
    }
    foreach ($directory in Get-ChildItem -LiteralPath '/proc' -Directory -ErrorAction SilentlyContinue)
    {
        if ($directory.Name -notmatch '^[0-9]+$')
        {
            continue
        }
        $path = Join-Path $directory.FullName 'cmdline'
        try
        {
            $command = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($path))
            if ($command -match [regex]::Escape($script:WorkerHelperName) -and
                    $command -match [regex]::Escape($Value) -and
                    $command -match 'run-loop')
            {
                $processIds += [long]$directory.Name
            }
        }
        catch
        {
            # /proc entry may disappear during exact identity enumeration.
        }
    }
    return @($processIds)
}

function Show-FormalStatus
{
    Assert-RootLinux
    Assert-RunId $RunId
    $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
    $state = Get-UnitState (Get-WorkerUnitName $RunId)
    Assert-FormalWorkerState $state $RunId -AllowInactive
    $heartbeat = Read-JsonFile "$( Get-EvidenceRoot $RunId )/heartbeat.json"
    $sequence = Get-HeartbeatSequence $heartbeat
    if ($PreviousMainPid -gt 0 -and $state.MainPID -ne $PreviousMainPid)
    {
        throw 'FAIL / FRESH_SESSION_MAIN_PID_CHANGED'
    }
    if ($MinimumHeartbeatSequence -ge 0 -and $sequence -lt $MinimumHeartbeatSequence)
    {
        throw 'FAIL / FRESH_SESSION_HEARTBEAT_NOT_ADVANCED'
    }
    if (-not [string]::IsNullOrWhiteSpace($PreviousHeartbeatObservedAt))
    {
        $previousHeartbeat = [DateTimeOffset]::MinValue
        $currentHeartbeat = [DateTimeOffset]::MinValue
        if (-not [DateTimeOffset]::TryParse($PreviousHeartbeatObservedAt, [ref]$previousHeartbeat) -or
                -not [DateTimeOffset]::TryParse([string]$heartbeat.observedAt, [ref]$currentHeartbeat) -or
                $currentHeartbeat -le $previousHeartbeat)
        {
            throw 'FAIL / FRESH_SESSION_HEARTBEAT_NOT_ADVANCED'
        }
    }
    $terminalPath = "$( Get-ControlRoot $RunId )/terminal-status.json"
    $terminal = if (Test-Path -LiteralPath $terminalPath -PathType Leaf)
    {
        $value = Read-JsonFile $terminalPath
        Assert-GateWTerminalRecord $value | Out-Null
        $value
    }
    else
    {
        $null
    }
    $clock = Get-AcceptanceClockProjection $RunId
    $unitStartPath = Get-UnitStartSnapshotPath $RunId
    $unitStart = if (Test-Path -LiteralPath $unitStartPath -PathType Leaf)
    {
        Read-JsonFile $unitStartPath
    }
    else
    {
        $null
    }
    $proofPath = "$( Get-ControlRoot $RunId )/acceptance-verification.json"
    $proof = if (Test-Path -LiteralPath $proofPath -PathType Leaf)
    {
        $value = Read-JsonFile $proofPath
        Assert-GateWAcceptanceProof $value | Out-Null
        $value
    }
    else
    {
        $null
    }
    return [pscustomobject]@{
        runId = $RunId
        lifecycleState = $lifecycle.state
        acceptanceResult = if ($null -eq $terminal)
        {
            $null
        }
        else
        {
            $terminal.acceptanceResult
        }
        terminalStatus = if ($null -eq $terminal)
        {
            $null
        }
        else
        {
            Get-TerminalLifecycleStatus $terminal
        }
        terminalChecksum = if ($null -eq $terminal)
        {
            $null
        }
        else
        {
            $terminal.checksum
        }
        unitName = Get-WorkerUnitName $RunId
        loadState = $state.LoadState
        activeState = $state.ActiveState
        subState = $state.SubState
        mainPid = $state.MainPID
        initialMainPid = if ($null -eq $unitStart)
        {
            $null
        }
        else
        {
            $unitStart.mainPid
        }
        nRestarts = $state.NRestarts
        execMainStartTimestampMonotonic = $state.ExecMainStartTimestampMonotonic
        initialExecMainStartTimestampMonotonic = if ($null -eq $unitStart)
        {
            $null
        }
        else
        {
            $unitStart.execMainStartTimestampMonotonic
        }
        residualProcessCount = @(Get-ResidualWorkerProcesses $RunId).Count
        heartbeatSequence = $sequence
        heartbeatObservedAt = $heartbeat.observedAt
        heartbeatState = $heartbeat.state
        heartbeatReasonCode = $heartbeat.reasonCode
        unitFragmentPath = $state.FragmentPath
        unitUser = $state.User
        acceptanceClockStarted = $clock.acceptanceClockStarted
        acceptanceStartAt = $clock.acceptanceStartAt
        plannedAcceptanceAt = $clock.plannedAcceptanceAt
        completionMarkerExists = Test-Path -LiteralPath `
            "$( Get-ControlRoot $RunId )/completion-marker.json" -PathType Leaf
        acceptanceVerified = $null -ne $proof
        acceptanceProofChecksum = if ($null -eq $proof)
        {
            $null
        }
        else
        {
            $proof.checksum
        }
        exitFactExists = Test-Path -LiteralPath `
            "$( Get-ControlRoot $RunId )/exit-fact.json" -PathType Leaf
    }
}

function Wait-ForTerminal
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [ValidateRange(1, 300)][int]$Seconds = 120
    )

    $path = "$( Get-ControlRoot $Value )/terminal-status.json"
    $deadline = (Get-UtcNow).AddSeconds($Seconds)
    while ((Get-UtcNow) -lt $deadline)
    {
        if (Test-Path -LiteralPath $path -PathType Leaf)
        {
            return Read-JsonFile $path
        }
        Start-Sleep -Milliseconds 250
    }
    throw 'FAIL / TERMINAL_STATUS_TIMEOUT'
}

function Get-TerminalLifecycleStatus
{
    param([Parameter(Mandatory = $true)]$Terminal)

    Assert-GateWTerminalRecord $Terminal | Out-Null
    if ([string]$Terminal.acceptanceResult -ceq 'ACCEPTED_168H_READONLY_SOAK')
    {
        return 'COMPLETED'
    }
    if ([string]$Terminal.stopClassification -ceq 'AUTHORIZED_CONTROLLED_STOP')
    {
        $intentPath = "$( Get-ControlRoot ([string]$Terminal.runId) )/stop-intent.json"
        if (Test-Path -LiteralPath $intentPath -PathType Leaf)
        {
            $intent = Read-JsonFile $intentPath
            Assert-GateWStopIntentRecord $intent | Out-Null
            if ([string]$intent.checksum -ceq [string]$Terminal.stopIntentChecksum -and
                    [string]$intent.reasonCode -ceq 'OPERATOR_STOP_REQUESTED')
            {
                return 'OPERATOR_STOPPED'
            }
        }
    }
    return 'FAILURE_STOPPED'
}

function Get-EffectiveRequesterUid
{
    $sudoUid = [Environment]::GetEnvironmentVariable('SUDO_UID', 'Process')
    $parsed = 0L
    if (-not [string]::IsNullOrWhiteSpace($sudoUid) -and
            [long]::TryParse($sudoUid, [ref]$parsed) -and $parsed -ge 0)
    {
        return $parsed
    }
    $result = Invoke-Native $script:IdPath @('-u')
    if ($result.ExitCode -ne 0 -or
            -not [long]::TryParse((ConvertTo-TrimmedOutput $result.Lines), [ref]$parsed) -or
            $parsed -lt 0)
    {
        throw 'FAIL / REQUESTER_UID_UNAVAILABLE'
    }
    return $parsed
}

function Write-ControlledStopIntent
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$ReasonCode
    )

    $controlRoot = Get-ControlRoot $Value
    $path = "$controlRoot/stop-intent.json"
    $config = Read-JsonFile "$controlRoot/frozen-config.json"
    Assert-FrozenReleaseBinding $config | Out-Null
    if (Test-Path -LiteralPath $path -PathType Leaf)
    {
        $existing = Read-JsonFile $path
        Assert-GateWStopIntentRecord $existing | Out-Null
        $requestedAt = [DateTimeOffset]::Parse([string]$existing.requestedAt)
        $now = Get-UtcNow
        if ([string]$existing.runId -cne $Value -or
                [string]$existing.reasonCode -cne $ReasonCode -or
                [string]$existing.releaseCommit -cne [string]$config.sourceCommit -or
                $requestedAt -gt $now)
        {
            throw 'BLOCKED / STOP_INTENT_CONFLICT'
        }
        if (($now - $requestedAt).TotalSeconds -le $script:StopIntentMaxAgeSeconds)
        {
            return $existing
        }
        if (Test-Path -LiteralPath "$controlRoot/exit-fact.json" -PathType Leaf)
        {
            throw 'BLOCKED / STOP_INTENT_STALE_AFTER_EXIT'
        }
        $retiredPath = "$controlRoot/stop-intent-retired-$( [string]$existing.requestId ).json"
        Assert-PathBelowRoot $controlRoot $retiredPath
        if (Test-Path -LiteralPath $retiredPath)
        {
            throw 'BLOCKED / STOP_INTENT_RETIREMENT_CONFLICT'
        }
        Move-Item -LiteralPath $path -Destination $retiredPath
        Set-OwnerMode $retiredPath 'root:root' '600'
    }
    $requestedAt = (Get-UtcNow).ToString('o')
    $requestId = 'gatew-stop-{0}-{1}' -f
    ([DateTimeOffset]::Parse($requestedAt).ToString('yyyyMMddTHHmmssZ')),
    ([Guid]::NewGuid().ToString('N').Substring(0, 8))
    $record = New-GateWStopIntentRecord `
        $Value $requestId $requestedAt (Get-EffectiveRequesterUid) $ReasonCode ([string]$config.sourceCommit)
    Write-JsonCreateOnce $path $record
    Set-OwnerMode $path 'root:root' '600'
    $written = Read-JsonFile $path
    Assert-GateWStopIntentRecord $written | Out-Null
    return $written
}

function Request-OperatorStop
{
    Assert-RootLinux
    Assert-RunId $RunId
    $terminalPath = "$( Get-ControlRoot $RunId )/terminal-status.json"
    if (Test-Path -LiteralPath $terminalPath -PathType Leaf)
    {
        $terminal = Read-JsonFile $terminalPath
        Assert-GateWTerminalRecord $terminal | Out-Null
        return [pscustomobject]@{
            decision = 'NO_CHANGE / TERMINAL_RUN'
            runId = $RunId
            terminalStatus = Get-TerminalLifecycleStatus $terminal
            acceptanceResult = [string]$terminal.acceptanceResult
        }
    }
    $lock = Enter-TerminalAuthorityLock $RunId
    try
    {
        if (Test-Path -LiteralPath $terminalPath -PathType Leaf)
        {
            $terminal = Read-JsonFile $terminalPath
            Assert-GateWTerminalRecord $terminal | Out-Null
            return [pscustomobject]@{
                decision = 'NO_CHANGE / TERMINAL_RUN'
                runId = $RunId
                terminalStatus = Get-TerminalLifecycleStatus $terminal
                acceptanceResult = [string]$terminal.acceptanceResult
            }
        }
        $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
        if ([string]$lifecycle.state -notin @(
            'PREPARING', 'STARTING', 'RUNNING', 'OPERATOR_STOPPING'
        ))
        {
            throw 'BLOCKED / RUN_NOT_OPERATOR_STOPPABLE'
        }
        $intent = Write-ControlledStopIntent $RunId 'OPERATOR_STOP_REQUESTED'
        if ([string]$lifecycle.state -cne 'OPERATOR_STOPPING')
        {
            Set-LifecycleState $RunId 'OPERATOR_STOPPING' 'OPERATOR_STOP_REQUESTED' | Out-Null
        }
    }
    finally
    {
        if ($null -ne $lock)
        {
            $lock.Dispose()
        }
    }
    Invoke-Native $script:SystemctlPath @('stop', (Get-WorkerUnitName $RunId)) -AllowFailure | Out-Null
    $finalizerStart = Invoke-Native $script:SystemctlPath @('start', (Get-FailCloseUnitName $RunId)) -AllowFailure
    if ($finalizerStart.ExitCode -ne 0)
    {
        throw 'FAIL / FAILCLOSE_FINALIZER_START_FAILED'
    }
    $terminal = Wait-ForTerminal $RunId
    Assert-GateWTerminalRecord $terminal | Out-Null
    if ([string]$terminal.stopClassification -ne 'AUTHORIZED_CONTROLLED_STOP' -or
            [string]$terminal.acceptanceResult -notin @(
                'REJECTED_RUNTIME_EXIT', 'REJECTED_INSUFFICIENT_DURATION'
            ))
    {
        throw 'FAIL / OPERATOR_STOP_NOT_PROVEN'
    }
    return [pscustomobject]@{
        decision = 'PASS / AUTHORIZED_CONTROLLED_STOP_REJECTED'
        runId = $RunId
        acceptanceResult = $terminal.acceptanceResult
        stopClassification = $terminal.stopClassification
        stopIntentChecksum = $intent.checksum
    }
}

function Inject-OfflineFailure
{
    Assert-RootLinux
    Assert-RunId $RunId
    $config = Read-JsonFile "$( Get-ControlRoot $RunId )/frozen-config.json"
    $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
    if ([string]$config.runMode -ne 'OFFLINE_ISOLATED_ACCEPTANCE' -or [string]$lifecycle.state -ne 'RUNNING')
    {
        throw 'BLOCKED / OFFLINE_FAILURE_INJECTION_NOT_ALLOWED'
    }
    $runtimeRoot = Get-RuntimeRoot $RunId
    Assert-PosixContract $runtimeRoot 'directory' '700' $script:LinuxRuntimeUser $script:LinuxRuntimeGroup
    $path = Join-Path $runtimeRoot 'offline-cycle-3-failure'
    Write-TextCreateOnce $path 'CONTROLLED_OFFLINE_CYCLE_3_FAILURE'
    Set-OwnerMode $path "root:$( $script:LinuxRuntimeGroup )" '440'
    $terminal = Wait-ForTerminal $RunId
    Assert-GateWTerminalRecord $terminal | Out-Null
    $terminalStatus = Get-TerminalLifecycleStatus $terminal
    if ($terminalStatus -cne 'FAILURE_STOPPED' -or
            [string]$terminal.acceptanceResult -cne 'REJECTED_RUNTIME_EXIT')
    {
        throw 'FAIL / OFFLINE_FAILURE_STOP_NOT_PROVEN'
    }
    $exitFact = Read-JsonFile "$( Get-ControlRoot $RunId )/exit-fact.json"
    return [pscustomobject]@{
        decision = 'PASS / CONTROLLED_OFFLINE_FAILURE_CLOSED'
        runId = $RunId
        terminalStatus = $terminalStatus
        acceptanceResult = [string]$terminal.acceptanceResult
        terminalReasonCode = $terminal.terminalReasonCode
        stopClassification = [string]$terminal.stopClassification
        killSwitchRecoveryStatus = 'NOT_PERFORMED_LIGHTWEIGHT_FAILCLOSE'
        killSwitchObservedState = 'NOT_VERIFIED'
        mainPid = [long]$exitFact.lastKnownMainPid
        residualProcessCount = @(Get-ResidualWorkerProcesses $RunId).Count
    }
}

function Invoke-WorkerEvidenceVerify
{
    param([Parameter(Mandatory = $true)][string]$Value)

    $previousFormal = [Environment]::GetEnvironmentVariable('NQ_GATEW_FORMAL_SYSTEMD', 'Process')
    $previousEvidence = [Environment]::GetEnvironmentVariable('NQ_GATEW_FORMAL_EVIDENCE_ROOT', 'Process')
    $previousReleaseRoot = [Environment]::GetEnvironmentVariable('NQ_GATEW_RELEASE_ROOT', 'Process')
    $previousReleaseId = [Environment]::GetEnvironmentVariable('NQ_GATEW_RELEASE_ID', 'Process')
    $previousManifestSha256 = [Environment]::GetEnvironmentVariable('NQ_GATEW_RELEASE_MANIFEST_SHA256', 'Process')
    try
    {
        $config = Read-JsonFile "$( Get-ControlRoot $Value )/frozen-config.json"
        Assert-FrozenReleaseBinding $config | Out-Null
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_SYSTEMD', 'true', 'Process')
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_EVIDENCE_ROOT', (Get-EvidenceRoot $Value), 'Process')
        [Environment]::SetEnvironmentVariable('NQ_GATEW_RELEASE_ROOT', [string]$config.releaseRoot, 'Process')
        [Environment]::SetEnvironmentVariable('NQ_GATEW_RELEASE_ID', [string]$config.releaseId, 'Process')
        [Environment]::SetEnvironmentVariable(
                'NQ_GATEW_RELEASE_MANIFEST_SHA256', [string]$config.releaseManifestSha256, 'Process'
        )
        $result = @(& $script:PowerShellPath -NoProfile -File `
            "$( $script:ReleaseRoot )/bin/$( $script:WorkerHelperName )" `
            -Action evidence-verify -RunId $Value 2> $null)
        if ($LASTEXITCODE -ne 0)
        {
            throw 'FAIL / EVIDENCE_VERIFY_FAILED'
        }
        try
        {
            return ConvertFrom-JsonPreservingTimestamps ($result -join "`n")
        }
        catch
        {
            throw 'FAIL / EVIDENCE_VERIFY_FAILED'
        }
    }
    finally
    {
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_SYSTEMD', $previousFormal, 'Process')
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_EVIDENCE_ROOT', $previousEvidence, 'Process')
        [Environment]::SetEnvironmentVariable('NQ_GATEW_RELEASE_ROOT', $previousReleaseRoot, 'Process')
        [Environment]::SetEnvironmentVariable('NQ_GATEW_RELEASE_ID', $previousReleaseId, 'Process')
        [Environment]::SetEnvironmentVariable(
                'NQ_GATEW_RELEASE_MANIFEST_SHA256', $previousManifestSha256, 'Process'
        )
    }
}

function Verify-FormalEvidence
{
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    $config = Read-JsonFile "$( Get-ControlRoot $RunId )/frozen-config.json"
    $identity = Assert-FrozenReleaseBinding $config
    $evidence = Invoke-WorkerEvidenceVerify $RunId
    if ([string]$evidence.result -cne 'PASS / HASH_CHAIN_VERIFIED' -or
            [long]$evidence.sampleCount -lt 0 -or
            [long]$evidence.forbiddenEndpointCount -lt 0 -or
            [long]$evidence.fallbackSamples -lt 0 -or
            [long]$evidence.rawResponseCount -lt 0 -or
            [long]$evidence.secretExposureCount -lt 0 -or
            [string]$evidence.lastHash -cnotmatch '^[a-f0-9]{64}$')
    {
        throw 'FAIL / FORMAL_EVIDENCE_INVALID'
    }
    $manifestPath = "$( Get-EvidenceRoot $RunId )/manifest.json"
    return [pscustomobject][ordered]@{
        decision = 'PASS / FORMAL_EVIDENCE_VERIFIED'
        runId = $RunId
        releaseCommit = [string]$identity.sourceCommit
        immutableRelease = [string]$identity.decision
        hashChain = [string]$evidence.result
        sampleCount = [long]$evidence.sampleCount
        lastHash = [string]$evidence.lastHash
        evidenceSchemaVersion = [string]$evidence.evidenceSchemaVersion
        forbiddenEndpointCount = [long]$evidence.forbiddenEndpointCount
        fallbackCount = [long]$evidence.fallbackSamples
        rawResponseCount = [long]$evidence.rawResponseCount
        secretExposureCount = [long]$evidence.secretExposureCount
        evidenceManifestSha256 = Get-Sha256File $manifestPath
    }
}

function Get-AcceptanceSampleProjection
{
    param([Parameter(Mandatory = $true)][string]$Value)

    $lastValidSampleAt = $null
    $sampleCount = 0L
    $allKillSwitchEngaged = $true
    foreach ($line in Get-Content -LiteralPath "$( Get-EvidenceRoot $Value )/samples.jsonl")
    {
        if ( [string]::IsNullOrWhiteSpace($line))
        {
            continue
        }
        $sample = ConvertFrom-JsonPreservingTimestamps $line
        $sampleCount++
        if ([string]$sample.killSwitchObservedState -cne 'ENGAGED')
        {
            $allKillSwitchEngaged = $false
        }
        if ([string]$sample.resultStatus -ceq 'PASSED_READ_ONLY' -and
                [bool]$sample.realCycleOutcomeProven -and
                [string]$sample.accountConfigProbeStatus -ceq 'SUCCEEDED' -and
                [string]$sample.balanceProbeStatus -ceq 'SUCCEEDED')
        {
            $lastValidSampleAt = ConvertTo-UtcRfc3339 $sample.observedAt
        }
    }
    return [pscustomobject]@{
        sampleCount = $sampleCount
        lastValidSampleAt = $lastValidSampleAt
        allSamplesKillSwitchEngaged = $allKillSwitchEngaged
    }
}

function Test-AcceptanceCompletionMarker
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)]$Snapshot
    )

    $path = "$( Get-ControlRoot $Value )/completion-marker.json"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        return $false
    }
    try
    {
        Assert-PosixContract $path 'regular file' '600' 'root' 'root'
        $marker = Read-JsonFile $path
        Assert-GateWCompletionMarkerRecord $marker $Snapshot | Out-Null
        return $true
    }
    catch
    {
        return $false
    }
}

function Commit-AcceptanceCompletionMarker
{
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)]$Snapshot
    )

    $path = "$( Get-ControlRoot $Value )/completion-marker.json"
    $record = New-GateWCompletionMarkerRecord $Snapshot
    Commit-CreateOnceJsonIdempotent `
        $path $record 'COMPLETION_MARKER_CONFLICT' 'root:root' '600' | Out-Null
    Set-OwnerMode $path 'root:root' '600'
    $written = Read-JsonFile $path
    Assert-GateWCompletionMarkerRecord $written $Snapshot | Out-Null
    return $written
}

function New-FormalAcceptanceSnapshot
{
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    $config = Read-JsonFile "$( Get-ControlRoot $RunId )/frozen-config.json"
    if ([string]$config.runMode -cne 'REAL_READONLY_SOAK')
    {
        throw 'BLOCKED / REAL_ACCEPTANCE_MODE_REQUIRED'
    }
    $identity = Assert-FrozenReleaseBinding $config
    $unitStart = Read-JsonFile (Get-UnitStartSnapshotPath $RunId)
    Assert-UnitStartSnapshot $unitStart $RunId ([string]$config.sourceCommit) | Out-Null
    $workerStartPath = "$( Get-EvidenceRoot $RunId )/worker-start.json"
    Assert-PosixContract $workerStartPath 'regular file' '600' 'root' 'root'
    $workerStartFiles = @(Get-ChildItem -LiteralPath (Get-EvidenceRoot $RunId) `
        -File -Filter 'worker-start*.json')
    $workerStart = Read-JsonFile $workerStartPath
    Assert-WorkerStartRecord $workerStart $RunId | Out-Null
    $clock = Read-JsonFile (Get-AcceptanceClockPath $RunId)
    Assert-AcceptanceClockRecord $clock $RunId
    $clockBinding = Read-JsonFile (Get-AcceptanceClockBindingPath $RunId)
    Assert-AcceptanceClockBinding $clockBinding $RunId $config | Out-Null
    $evidence = Verify-FormalEvidence
    $samples = Get-AcceptanceSampleProjection $RunId
    $heartbeat = Read-JsonFile "$( Get-EvidenceRoot $RunId )/heartbeat.json"
    $state = Get-UnitState (Get-WorkerUnitName $RunId)
    $lastValidSampleAt = if ( [string]::IsNullOrWhiteSpace([string]$samples.lastValidSampleAt))
    {
        '1970-01-01T00:00:00.0000000Z'
    }
    else
    {
        [string]$samples.lastValidSampleAt
    }
    $acceptanceStart = [DateTimeOffset]::Parse([string]$clock.acceptanceStartAt)
    $lastValidSample = [DateTimeOffset]::Parse($lastValidSampleAt)
    $observedDuration = [Math]::Max(0.0, ($lastValidSample - $acceptanceStart).TotalSeconds)
    $snapshot = [pscustomobject][ordered]@{
        schemaVersion = 'gatew-soak-acceptance-snapshot-v1'
        runId = $RunId
        releaseCommit = [string]$config.sourceCommit
        unitActiveState = [string]$state.ActiveState
        unitSubState = [string]$state.SubState
        mainPid = [long]$state.MainPID
        initialMainPid = [long]$unitStart.mainPid
        workerStartMainPid = [long]$workerStart.mainPid
        nRestarts = [long]$state.NRestarts
        execMainStartTimestampMonotonic = [long]$state.ExecMainStartTimestampMonotonic
        initialExecMainStartTimestampMonotonic = [long]$unitStart.execMainStartTimestampMonotonic
        workerStartCount = $workerStartFiles.Count
        earlyExitFactExists = Test-Path -LiteralPath "$( Get-ControlRoot $RunId )/exit-fact.json"
        completionMarkerValid = $false
        acceptanceStartAt = [string]$clock.acceptanceStartAt
        plannedAcceptanceAt = [string]$clock.plannedAcceptanceAt
        clockMainPid = [long]$clock.mainPid
        clockRecordSha256 = Get-Sha256File (Get-AcceptanceClockPath $RunId)
        expectedClockRecordSha256 = [string]$clockBinding.acceptanceClockSha256
        observedDurationSeconds = $observedDuration
        requiredDurationSeconds = 604800.0
        lastValidSampleAt = $lastValidSampleAt
        heartbeatObservedAt = [string]$heartbeat.observedAt
        evidenceDecision = [string]$evidence.decision
        immutableReleaseDecision = [string]$identity.decision
        sampleCount = [long]$evidence.sampleCount
        forbiddenEndpointCount = [long]$evidence.forbiddenEndpointCount
        fallbackCount = [long]$evidence.fallbackCount
        rawResponseCount = [long]$evidence.rawResponseCount
        secretExposureCount = [long]$evidence.secretExposureCount
        allSamplesKillSwitchEngaged = [bool]$samples.allSamplesKillSwitchEngaged
        evidenceManifestSha256 = [string]$evidence.evidenceManifestSha256
        evidenceFinalChainHash = [string]$evidence.lastHash
    }
    $snapshot.completionMarkerValid = Test-AcceptanceCompletionMarker $RunId $snapshot
    return $snapshot
}

function Verify-FormalAcceptance
{
    $snapshot = New-FormalAcceptanceSnapshot
    $result = Test-GateWAcceptanceSnapshot $snapshot
    $markerPath = "$( Get-ControlRoot $RunId )/completion-marker.json"
    if (-not [bool]$result.accepted -and
            -not (Test-Path -LiteralPath $markerPath -PathType Leaf))
    {
        $preMarkerSnapshot = ConvertFrom-JsonPreservingTimestamps (
            $snapshot | ConvertTo-Json -Depth 20
        )
        $preMarkerSnapshot.completionMarkerValid = $true
        $preMarkerResult = Test-GateWAcceptanceSnapshot $preMarkerSnapshot
        if ([bool]$preMarkerResult.accepted)
        {
            Commit-AcceptanceCompletionMarker $RunId $snapshot | Out-Null
            $snapshot.completionMarkerValid = Test-AcceptanceCompletionMarker $RunId $snapshot
            $result = Test-GateWAcceptanceSnapshot $snapshot
        }
    }
    if (-not [bool]$result.accepted)
    {
        return [pscustomobject][ordered]@{
            decision = [string]$result.decision
            runId = $RunId
            failureCodes = @($result.failureCodes)
            observedDurationSeconds = [double]$result.observedDurationSeconds
            requiredDurationSeconds = [double]$result.requiredDurationSeconds
            acceptanceProofChecksum = $null
        }
    }
    $path = "$( Get-ControlRoot $RunId )/acceptance-verification.json"
    $proof = if (Test-Path -LiteralPath $path -PathType Leaf)
    {
        $existing = Read-JsonFile $path
        Assert-GateWAcceptanceProof $existing | Out-Null
        if ([string]$existing.runId -cne [string]$snapshot.runId -or
                [string]$existing.releaseCommit -cne [string]$snapshot.releaseCommit -or
                [long]$existing.mainPid -ne [long]$snapshot.mainPid -or
                [long]$existing.nRestarts -ne [long]$snapshot.nRestarts -or
                [long]$existing.execMainStartTimestampMonotonic -ne
                        [long]$snapshot.execMainStartTimestampMonotonic -or
                [string]$existing.acceptanceStartAt -cne [string]$snapshot.acceptanceStartAt -or
                [string]$existing.plannedAcceptanceAt -cne [string]$snapshot.plannedAcceptanceAt -or
                [double]$existing.observedDurationSeconds -ne
                        [double]$snapshot.observedDurationSeconds -or
                [string]$existing.lastValidSampleAt -cne [string]$snapshot.lastValidSampleAt -or
                [string]$existing.evidenceManifestSha256 -cne
                        [string]$snapshot.evidenceManifestSha256 -or
                [string]$existing.evidenceFinalChainHash -cne
                        [string]$snapshot.evidenceFinalChainHash)
        {
            throw 'BLOCKED / ACCEPTANCE_PROOF_CONFLICT'
        }
        $existing
    }
    else
    {
        $newProof = New-GateWAcceptanceProof `
            -Snapshot $snapshot `
            -VerifiedAt ((Get-UtcNow).ToString('o'))
        Write-JsonCreateOnce $path $newProof
        Set-OwnerMode $path 'root:root' '600'
        $newProof
    }
    return [pscustomobject][ordered]@{
        decision = 'PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED'
        runId = $RunId
        releaseCommit = $snapshot.releaseCommit
        mainPid = $snapshot.mainPid
        nRestarts = $snapshot.nRestarts
        observedDurationSeconds = $snapshot.observedDurationSeconds
        requiredDurationSeconds = $snapshot.requiredDurationSeconds
        lastValidSampleAt = $snapshot.lastValidSampleAt
        heartbeatObservedAt = $snapshot.heartbeatObservedAt
        evidenceManifestSha256 = $snapshot.evidenceManifestSha256
        evidenceFinalChainHash = $snapshot.evidenceFinalChainHash
        acceptanceProofChecksum = $proof.checksum
    }
}

function Verify-FormalTerminal
{
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    $controlRoot = Get-ControlRoot $RunId
    $path = "$controlRoot/terminal-status.json"
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        throw 'FAIL / TERMINAL_STATUS_MISSING'
    }
    $terminalCandidates = @(Get-ChildItem -LiteralPath $controlRoot `
        -File -Filter 'terminal-status*.json')
    if ($terminalCandidates.Count -ne 1 -or
            [IO.Path]::GetFullPath([string]$terminalCandidates[0].FullName) -cne
                    [IO.Path]::GetFullPath($path))
    {
        throw 'FAIL / TERMINAL_RESULT_CONFLICT'
    }
    $terminal = Read-JsonFile $path
    Assert-GateWTerminalRecord $terminal | Out-Null
    $config = Read-JsonFile "$controlRoot/frozen-config.json"
    $identity = Assert-FrozenReleaseBinding $config
    if ([string]$terminal.runId -cne $RunId -or
            [string]$terminal.releaseCommit -cne [string]$identity.sourceCommit)
    {
        throw 'FAIL / TERMINAL_RELEASE_BINDING_INVALID'
    }
    $clock = Get-AcceptanceClockProjection $RunId
    if ([bool]$clock.acceptanceClockStarted)
    {
        $clockBinding = Read-JsonFile (Get-AcceptanceClockBindingPath $RunId)
        Assert-AcceptanceClockBinding $clockBinding $RunId $config | Out-Null
    }
    if ([bool]$clock.acceptanceClockStarted -and
            ([string]$terminal.acceptanceStartAt -cne [string]$clock.acceptanceStartAt -or
                    [string]$terminal.plannedAcceptanceAt -cne [string]$clock.plannedAcceptanceAt))
    {
        throw 'FAIL / TERMINAL_CLOCK_BINDING_INVALID'
    }
    if (-not [bool]$clock.acceptanceClockStarted -and
            ($null -ne $terminal.acceptanceStartAt -or $null -ne $terminal.plannedAcceptanceAt))
    {
        throw 'FAIL / TERMINAL_CLOCK_BINDING_INVALID'
    }
    if ([string]$terminal.acceptanceResult -ceq 'ACCEPTED_168H_READONLY_SOAK')
    {
        $proofPath = "$( Get-ControlRoot $RunId )/acceptance-verification.json"
        if (-not (Test-Path -LiteralPath $proofPath -PathType Leaf))
        {
            throw 'BLOCKED / ACCEPTANCE_VERIFY_REQUIRED'
        }
        $proof = Read-JsonFile $proofPath
        Assert-GateWAcceptanceProof $proof | Out-Null
        if (-not (Test-AcceptanceCompletionMarker $RunId $proof))
        {
            throw 'FAIL / TERMINAL_COMPLETION_MARKER_BINDING_INVALID'
        }
        if ([string]$terminal.terminalReasonCode -cne 'ACCEPTANCE_RESULT_FINALIZED' -or
                [string]$proof.runId -cne [string]$terminal.runId -or
                [string]$proof.releaseCommit -cne [string]$terminal.releaseCommit -or
                [string]$proof.acceptanceStartAt -cne [string]$terminal.acceptanceStartAt -or
                [string]$proof.plannedAcceptanceAt -cne [string]$terminal.plannedAcceptanceAt -or
                [string]$terminal.acceptanceVerificationChecksum -cne [string]$proof.checksum -or
                [string]$terminal.evidenceManifestSha256 -cne [string]$proof.evidenceManifestSha256 -or
                [string]$terminal.evidenceFinalChainHash -cne [string]$proof.evidenceFinalChainHash)
        {
            throw 'FAIL / TERMINAL_ACCEPTANCE_BINDING_INVALID'
        }
    }
    if ([string]$terminal.stopClassification -ceq 'AUTHORIZED_CONTROLLED_STOP')
    {
        $intentPath = "$controlRoot/stop-intent.json"
        if (-not (Test-Path -LiteralPath $intentPath -PathType Leaf))
        {
            throw 'FAIL / TERMINAL_STOP_INTENT_BINDING_INVALID'
        }
        $intent = Read-JsonFile $intentPath
        Assert-GateWStopIntentRecord $intent | Out-Null
        $requestedAt = [DateTimeOffset]::Parse([string]$intent.requestedAt)
        $finalizedAt = [DateTimeOffset]::Parse([string]$terminal.finalizedAt)
        if (([string]$intent.runId -cne [string]$terminal.runId) -or
                ([string]$intent.releaseCommit -cne [string]$terminal.releaseCommit) -or
                ([string]$intent.checksum -cne [string]$terminal.stopIntentChecksum) -or
                ([string]$intent.reasonCode -cnotin @(
                    'OPERATOR_STOP_REQUESTED', 'ACCEPTANCE_FINALIZATION'
                )) -or $requestedAt -gt $finalizedAt -or
                (($finalizedAt - $requestedAt).TotalSeconds -gt
                        $script:StopIntentMaxAgeSeconds -or
                (([string]$terminal.acceptanceResult -ceq 'ACCEPTED_168H_READONLY_SOAK') -and
                        ([string]$intent.reasonCode -cne 'ACCEPTANCE_FINALIZATION'))))
        {
            throw 'FAIL / TERMINAL_STOP_INTENT_BINDING_INVALID'
        }
    }
    $state = Get-UnitState (Get-WorkerUnitName $RunId)
    Assert-FormalWorkerState $state $RunId -AllowInactive
    if ([string]$state.ActiveState -cne 'inactive' -or [long]$state.MainPID -ne 0)
    {
        throw 'FAIL / TERMINAL_PROCESS_CONTRACT_INVALID'
    }
    return [pscustomobject][ordered]@{
        decision = 'PASS / FORMAL_TERMINAL_VERIFIED'
        runId = $RunId
        acceptanceResult = [string]$terminal.acceptanceResult
        terminalChecksum = [string]$terminal.checksum
        releaseCommit = [string]$terminal.releaseCommit
        stopClassification = [string]$terminal.stopClassification
    }
}

function Complete-AcceptedLifecycle
{
    param([Parameter(Mandatory = $true)][string]$Value)

    $lifecycle = Read-JsonFile (Get-LifecyclePath $Value)
    if ([string]$lifecycle.state -ceq 'COMPLETED')
    {
        return $lifecycle
    }
    if ([string]$lifecycle.state -cne 'RUNNING')
    {
        throw 'BLOCKED / TERMINAL_LIFECYCLE_CONFLICT'
    }
    return Set-LifecycleState $Value 'COMPLETED' 'ACCEPTANCE_RESULT_FINALIZED'
}

function Finalize-FormalAcceptance
{
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    $lock = Enter-TerminalAuthorityLock $RunId
    try
    {
        $terminalPath = "$( Get-ControlRoot $RunId )/terminal-status.json"
        if (Test-Path -LiteralPath $terminalPath -PathType Leaf)
        {
            $existing = Read-JsonFile $terminalPath
            Assert-GateWTerminalRecord $existing | Out-Null
            $proofPath = "$( Get-ControlRoot $RunId )/acceptance-verification.json"
            if (-not (Test-Path -LiteralPath $proofPath -PathType Leaf))
            {
                throw 'BLOCKED / ACCEPTANCE_VERIFY_REQUIRED'
            }
            $existingProof = Read-JsonFile $proofPath
            Assert-GateWAcceptanceProof $existingProof | Out-Null
            if ([string]$existing.acceptanceResult -cne 'ACCEPTED_168H_READONLY_SOAK' -or
                    [string]$existing.runId -cne $RunId -or
                    [string]$existing.releaseCommit -cne [string]$existingProof.releaseCommit -or
                    [string]$existing.acceptanceVerificationChecksum -cne
                            [string]$existingProof.checksum -or
                    [string]$existing.evidenceManifestSha256 -cne
                            [string]$existingProof.evidenceManifestSha256 -or
                    [string]$existing.evidenceFinalChainHash -cne
                            [string]$existingProof.evidenceFinalChainHash)
            {
                throw 'BLOCKED / TERMINAL_RESULT_CONFLICT'
            }
            Verify-FormalTerminal | Out-Null
            Complete-AcceptedLifecycle $RunId | Out-Null
            return [pscustomobject]@{
                decision = 'NO_CHANGE / ACCEPTANCE_RESULT_ALREADY_FINALIZED'
                runId = $RunId
                acceptanceResult = [string]$existing.acceptanceResult
                terminalChecksum = [string]$existing.checksum
            }
        }
        $proofPath = "$( Get-ControlRoot $RunId )/acceptance-verification.json"
        if (-not (Test-Path -LiteralPath $proofPath -PathType Leaf))
        {
            throw 'BLOCKED / ACCEPTANCE_VERIFY_REQUIRED'
        }
        $proof = Read-JsonFile $proofPath
        Assert-GateWAcceptanceProof $proof | Out-Null
        if ([string]$proof.result -cne 'PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED')
        {
            throw 'BLOCKED / ACCEPTANCE_VERIFY_REQUIRED'
        }
        if (-not (Test-AcceptanceCompletionMarker $RunId $proof))
        {
            throw 'BLOCKED / ACCEPTANCE_COMPLETION_MARKER_REQUIRED'
        }
        $config = Read-JsonFile "$( Get-ControlRoot $RunId )/frozen-config.json"
        Assert-FrozenReleaseBinding $config | Out-Null
        $clock = Get-AcceptanceClockProjection $RunId
        $state = Get-UnitState (Get-WorkerUnitName $RunId)
        if ([string]$state.ActiveState -cne 'active' -or [string]$state.SubState -cne 'running' -or
                [long]$state.MainPID -ne [long]$proof.mainPid -or [long]$state.NRestarts -ne 0 -or
                [long]$state.ExecMainStartTimestampMonotonic -ne
                        [long]$proof.execMainStartTimestampMonotonic -or
                [string]$config.sourceCommit -cne [string]$proof.releaseCommit -or
                [string]$clock.acceptanceStartAt -cne [string]$proof.acceptanceStartAt -or
                [string]$clock.plannedAcceptanceAt -cne [string]$proof.plannedAcceptanceAt)
        {
            throw 'BLOCKED / ACCEPTANCE_PROOF_STALE'
        }
        $intent = Write-ControlledStopIntent $RunId 'ACCEPTANCE_FINALIZATION'
        Invoke-Native $script:SystemctlPath @('stop', (Get-WorkerUnitName $RunId)) | Out-Null
        $deadline = (Get-UtcNow).AddSeconds(20)
        do
        {
            $state = Get-UnitState (Get-WorkerUnitName $RunId)
            if ([string]$state.ActiveState -ceq 'inactive' -and [long]$state.MainPID -eq 0)
            {
                break
            }
            Start-Sleep -Milliseconds 100
        } while ((Get-UtcNow) -lt $deadline)
        if ([string]$state.ActiveState -cne 'inactive' -or [long]$state.MainPID -ne 0)
        {
            throw 'FAIL / ACCEPTANCE_WORKER_STOP_FAILED'
        }
        $terminal = New-GateWTerminalRecord `
            -RunId $RunId `
            -ReleaseCommit ([string]$proof.releaseCommit) `
            -AcceptanceResult 'ACCEPTED_168H_READONLY_SOAK' `
            -TerminalReasonCode 'ACCEPTANCE_RESULT_FINALIZED' `
            -StopClassification 'AUTHORIZED_CONTROLLED_STOP' `
            -AcceptanceStartAt ([string]$proof.acceptanceStartAt) `
            -PlannedAcceptanceAt ([string]$proof.plannedAcceptanceAt) `
            -AcceptanceVerificationChecksum ([string]$proof.checksum) `
            -EvidenceManifestSha256 ([string]$proof.evidenceManifestSha256) `
            -EvidenceFinalChainHash ([string]$proof.evidenceFinalChainHash) `
            -StopIntentChecksum ([string]$intent.checksum) `
            -FinalizerKind 'EXPLICIT_ACCEPTANCE' `
            -FinalizedAt ((Get-UtcNow).ToString('o'))
        Write-JsonCreateOnce $terminalPath $terminal
        Set-OwnerMode $terminalPath 'root:root' '600'
        Verify-FormalTerminal | Out-Null
        Complete-AcceptedLifecycle $RunId | Out-Null
        return [pscustomobject][ordered]@{
            decision = 'PASS / ACCEPTANCE_RESULT_FINALIZED'
            runId = $RunId
            acceptanceResult = 'ACCEPTED_168H_READONLY_SOAK'
            terminalChecksum = [string]$terminal.checksum
            stopIntentChecksum = [string]$intent.checksum
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

function Verify-FormalRun
{
    throw 'BLOCKED / VERIFY_ACTION_SPLIT_REQUIRED'
}

function Invoke-UnitPreflight
{
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    foreach ($name in @('worker.env', 'failclose.env', 'frozen-config.json', 'lifecycle.json'))
    {
        Assert-PosixContract "$( Get-ControlRoot $RunId )/$name" 'regular file' '600' 'root' 'root'
    }
    $config = Read-JsonFile "$( Get-ControlRoot $RunId )/frozen-config.json"
    Assert-FrozenReleaseBinding $config -RequireEnvironment | Out-Null
    $runtimeRoot = Get-RuntimeRoot $RunId
    Assert-PosixContract $runtimeRoot 'directory' '700' $script:LinuxRuntimeUser $script:LinuxRuntimeGroup
    return [pscustomobject]@{ decision = 'PASS / FORMAL_UNIT_PREFLIGHT'; runId = $RunId }
}

function Record-ExitFact
{
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
    if ([string]$lifecycle.state -notin @(
        'STARTING', 'RUNNING', 'FAILURE_STOPPING', 'OPERATOR_STOPPING'
    ))
    {
        throw 'FAIL / SYSTEMD_EXIT_FACT_INVALID'
    }
    if ($LastKnownMainPid -eq 0)
    {
        $mainPidValue = [Environment]::GetEnvironmentVariable('MAINPID', 'Process')
        $parsedMainPid = 0L
        if ([long]::TryParse($mainPidValue, [ref]$parsedMainPid) -and $parsedMainPid -ge 0)
        {
            $LastKnownMainPid = $parsedMainPid
        }
    }
    $workerStartPath = "$( Get-EvidenceRoot $RunId )/worker-start.json"
    if (Test-Path -LiteralPath $workerStartPath -PathType Leaf)
    {
        $workerStart = Read-JsonFile $workerStartPath
        if ((@($workerStart.PSObject.Properties.Name) -join '|') -ne
                'schemaVersion|runId|mainPid|unitName|startedAt' -or
                [string]$workerStart.schemaVersion -ne 'gatew-soak-worker-start-v1' -or
                [string]$workerStart.runId -ne $RunId -or
                [string]$workerStart.unitName -ne (Get-WorkerUnitName $RunId) -or
                [long]$workerStart.mainPid -le 0)
        {
            throw 'FAIL / SYSTEMD_EXIT_FACT_INVALID'
        }
        if ($LastKnownMainPid -eq 0)
        {
            $LastKnownMainPid = [long]$workerStart.mainPid
        }
        elseif ($LastKnownMainPid -ne [long]$workerStart.mainPid)
        {
            throw 'FAIL / SYSTEMD_EXIT_FACT_INVALID'
        }
    }
    $safeServiceResults = @(
        'success', 'exit-code', 'signal', 'core-dump', 'watchdog', 'start-limit-hit',
        'timeout', 'resources', 'protocol', 'oom-kill'
    )
    $safeExitCodes = @('exited', 'killed', 'dumped', '')
    if ($safeServiceResults -notcontains $ServiceResult -or
            $safeExitCodes -notcontains $ExitCode -or
            $ExitStatus -notmatch '^[A-Za-z0-9_-]{0,32}$' -or
            $LastKnownMainPid -lt 0)
    {
        throw 'FAIL / SYSTEMD_EXIT_FACT_INVALID'
    }
    $recordedAt = Get-UtcNow
    $stopClassification = 'UNAUTHORIZED_OR_UNKNOWN_STOP'
    $stopIntentChecksum = 'NOT_PRESENT'
    $stopIntentPath = "$( Get-ControlRoot $RunId )/stop-intent.json"
    if (Test-Path -LiteralPath $stopIntentPath -PathType Leaf)
    {
        try
        {
            $stopIntent = Read-JsonFile $stopIntentPath
            Assert-GateWStopIntentRecord $stopIntent | Out-Null
            $config = Read-JsonFile "$( Get-ControlRoot $RunId )/frozen-config.json"
            $requestedAt = [DateTimeOffset]::Parse([string]$stopIntent.requestedAt)
            if ([string]$stopIntent.runId -cne $RunId -or
                    [string]$stopIntent.releaseCommit -cne [string]$config.sourceCommit -or
                    $requestedAt -gt $recordedAt -or
                    ($recordedAt - $requestedAt).TotalSeconds -gt
                            $script:StopIntentMaxAgeSeconds)
            {
                throw 'FAIL / STOP_INTENT_BINDING_INVALID'
            }
            $stopClassification = 'AUTHORIZED_CONTROLLED_STOP'
            $stopIntentChecksum = [string]$stopIntent.checksum
        }
        catch
        {
            $stopClassification = 'UNAUTHORIZED_OR_UNKNOWN_STOP'
            $stopIntentChecksum = 'INVALID'
        }
    }
    $path = "$( Get-ControlRoot $RunId )/exit-fact.json"
    if (Test-Path -LiteralPath $path -PathType Leaf)
    {
        $existing = Read-JsonFile $path
        if ((@($existing.PSObject.Properties.Name) -join '|') -ne
                'schemaVersion|runId|serviceResult|exitCode|exitStatus|lastKnownMainPid|stopClassification|stopIntentChecksum|recordedAt' -or
                [string]$existing.schemaVersion -ne 'gatew-soak-exit-fact-v2' -or
                [string]$existing.runId -ne $RunId)
        {
            throw 'FAIL / SYSTEMD_EXIT_FACT_INVALID'
        }
        return [pscustomobject]@{ decision = 'NO_CHANGE / EXIT_FACT_EXISTS'; runId = $RunId }
    }
    Write-JsonCreateOnce $path ([ordered]@{
        schemaVersion = 'gatew-soak-exit-fact-v2'
        runId = $RunId
        serviceResult = $ServiceResult
        exitCode = $ExitCode
        exitStatus = $ExitStatus
        lastKnownMainPid = $LastKnownMainPid
        stopClassification = $stopClassification
        stopIntentChecksum = $stopIntentChecksum
        recordedAt = $recordedAt.ToString('o')
    })
    Set-OwnerMode $path 'root:root' '600'
    return [pscustomobject]@{ decision = 'PASS / EXIT_FACT_RECORDED'; runId = $RunId }
}

function Invoke-ControlSelfTest
{
    $caseCount = 0
    if ( [IO.File]::ReadAllText($PSCommandPath).Contains('$' + 'matches = @()'))
    {
        throw 'automatic Matches collision self-test failed'
    }
    $caseCount++
    $descriptorFixture = [pscustomobject][ordered]@{
        schemaVersion = 'gatew-precreate-prerequisite-v1'
        databaseHost = '127.0.0.1'
        databasePort = 5432
        databaseName = 'nexus_quant'
        databaseUser = 'nq_runtime'
        passwordSecretFile = "$( $script:CredentialRoot )/db-password.cred"
        managementLoopbackUrl = 'http://127.0.0.1:18889/actuator/health'
        expectedCredentialType = 'OKX_API_V5'
        expectedEnvironment = 'LIVE'
    }
    Assert-PreCreateDescriptorValue $descriptorFixture | Out-Null
    if ((Get-PreCreateDatabaseUrl $descriptorFixture) -cne
            'jdbc:postgresql://127.0.0.1:5432/nexus_quant')
    {
        throw 'precreate descriptor URL self-test failed'
    }
    $caseCount += 2
    foreach ($mutation in @(
        @{ unknownField = 'forbidden' },
        @{ databaseHost = '${DB_HOST}' },
        @{ databaseName = '$(whoami)' },
        @{ databaseUser = 'user`id' },
        @{ managementLoopbackUrl = 'http://127.0.0.1:18889/actuator/health|id' },
        @{ passwordSecretFile = '/tmp/db-password' }
    ))
    {
        $candidate = (ConvertTo-CompactJson $descriptorFixture | ConvertFrom-Json)
        foreach ($name in $mutation.Keys)
        {
            if ($null -eq $candidate.PSObject.Properties[$name])
            {
                $candidate | Add-Member -NotePropertyName $name -NotePropertyValue $mutation[$name]
            }
            else
            {
                $candidate.PSObject.Properties[$name].Value = $mutation[$name]
            }
        }
        $blocked = $false
        try
        {
            Assert-PreCreateDescriptorValue $candidate | Out-Null
        }
        catch
        {
            $blocked = $true
        }
        if (-not $blocked)
        {
            throw 'precreate descriptor rejection self-test failed'
        }
        $caseCount++
    }
    $safeReadback = [pscustomobject][ordered]@{
        killSwitchEngaged = $true
        credentialConfigured = $true
        activeCredentialCount = 1
        credentialType = 'OKX_API_V5'
        credentialLocalStatus = 'ACTIVE'
        tradePermissionExpectedDisabled = $true
        withdrawPermissionExpectedDisabled = $true
        postgresReachable = $true
        managementHealthy = $true
    }
    Assert-PreCreateReadback $safeReadback | Out-Null
    $preCreateResult = Assert-PreCreateResult `
        (New-PreCreateResult '2026-07-21T00:00:00Z' $safeReadback)
    if (-not [bool]$preCreateResult.readyForAttemptCreation -or
            [bool]$preCreateResult.credentialMaterialExposed)
    {
        throw 'precreate result self-test failed'
    }
    $caseCount += 2
    $source = [IO.File]::ReadAllText($PSCommandPath)
    $prepareStart = $source.IndexOf('function Prepare-FormalRun', [StringComparison]::Ordinal)
    $prepareEnd = $source.IndexOf('function ConvertFrom-SystemctlShow', $prepareStart, [StringComparison]::Ordinal)
    $prepareSource = $source.Substring($prepareStart, $prepareEnd - $prepareStart)
    $gateIndex = $prepareSource.IndexOf(
            'Invoke-PreCreatePrerequisiteEvaluation -ForPrepare',
            [StringComparison]::Ordinal
    )
    $runIdIndex = $prepareSource.IndexOf('New-RunId', [StringComparison]::Ordinal)
    $directoryIndex = $prepareSource.IndexOf('Ensure-Directory', [StringComparison]::Ordinal)
    if ($gateIndex -lt 0 -or $runIdIndex -lt 0 -or $directoryIndex -lt 0 -or
            $gateIndex -gt $runIdIndex -or $gateIndex -gt $directoryIndex)
    {
        throw 'precreate execution order self-test failed'
    }
    $caseCount++
    $roundTripTimestamp = '2026-07-20T17:39:01.8426894Z'
    $parsedTimestamp = (ConvertFrom-JsonPreservingTimestamps `
            "{`"observedAt`":`"$roundTripTimestamp`"}").observedAt
    if ($parsedTimestamp -isnot [string] -or [string]$parsedTimestamp -cne $roundTripTimestamp -or
            (ConvertTo-UtcRfc3339 $parsedTimestamp) -cne $roundTripTimestamp)
    {
        throw 'JSON timestamp preservation self-test failed'
    }
    $caseCount++
    $verifierParameters = New-ReleaseVerifierParameters '/release' ('a' * 40) ('b' * 64)
    if ($verifierParameters.Count -ne 3 -or
            [string]$verifierParameters.ReleaseRoot -ne '/release' -or
            [string]$verifierParameters.ExpectedReleaseId -ne ('a' * 40) -or
            [string]$verifierParameters.ExpectedManifestSha256 -ne ('b' * 64))
    {
        throw 'release verifier parameter self-test failed'
    }
    $caseCount++
    $validRunId = 'gatew-soak-20260718T000000Z-0123abcd'
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
    foreach ($literal in @('plain', 'jdbc:postgresql://127.0.0.1:5432/gatew_soak', ''))
    {
        Assert-LiteralValue $literal
        $caseCount++
    }
    foreach ($invalid in @('$VALUE', '${VALUE}', '%VALUE%', '$(whoami)', "line`nbreak"))
    {
        $blocked = $false
        try
        {
            Assert-LiteralValue $invalid
        }
        catch
        {
            $blocked = $true
        }
        if (-not $blocked)
        {
            throw 'literal config self-test failed'
        }
        $caseCount++
    }
    if ($script:AllowedTransitions.RUNNING -notcontains 'FAILURE_STOPPING' -or
            $script:AllowedTransitions.RUNNING -notcontains 'OPERATOR_STOPPING' -or
            $script:AllowedTransitions.FAILURE_STOPPED.Count -ne 0 -or
            $script:AllowedTransitions.OPERATOR_STOPPED.Count -ne 0 -or
            $script:AllowedTransitions.COMPLETED.Count -ne 0 -or
            $script:AllowedTransitions.BLOCKED.Count -ne 0)
    {
        throw 'lifecycle state machine self-test failed'
    }
    $caseCount += 4
    if ($script:AllowedTransitions.RUNNING -contains 'FAILURE_STOPPED' -or
            $script:AllowedTransitions.RUNNING -contains 'OPERATOR_STOPPED' -or
            'FAILURE_STOPPED' -ceq 'OPERATOR_STOPPED')
    {
        throw 'operator/failure terminal separation self-test failed'
    }
    $caseCount++
    $temporaryRoot = Join-Path $script:WorkspaceRoot 'target/gatew-okx-readonly-soak/control-self-test'
    $temporary = Join-Path $temporaryRoot ([Guid]::NewGuid().ToString('N'))
    $symlinkTest = 'NOT_AVAILABLE'
    try
    {
        [IO.Directory]::CreateDirectory($temporary) | Out-Null
        $path = Join-Path $temporary 'create-once.json'
        Write-TextCreateOnce $path '{"safe":true}'
        $rejected = $false
        try
        {
            Write-TextCreateOnce $path '{"safe":false}'
        }
        catch
        {
            $rejected = $true
        }
        if (-not $rejected -or (Get-Content -LiteralPath $path -Raw) -ne '{"safe":true}')
        {
            throw 'create-once self-test failed'
        }
        $caseCount++

        $emptyPath = Join-Path $temporary 'create-once-empty.jsonl'
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

        $clockRunId = 'gatew-soak-20260718T000002Z-2345abcd'
        $clockBase = @{
            Value = $clockRunId
            FirstConfigAt = '2026-07-18T00:00:01Z'
            FirstBalanceAt = '2026-07-18T00:00:02Z'
            FreshSshAt = '2026-07-18T00:00:03Z'
            MainPid = 123L
            SameMainPid = $true
            HeartbeatAdvanced = $true
            HashChainValid = $true
            ForbiddenEndpointCount = 0
            SecretExposureCount = 0
        }
        foreach ($failureCase in @(
            @{ FirstBalanceAt = $null },
            @{ FirstConfigAt = $null },
            @{ FreshSshAt = $null },
            @{ SameMainPid = $false },
            @{ HeartbeatAdvanced = $false },
            @{ HashChainValid = $false },
            @{ ForbiddenEndpointCount = 1 },
            @{ SecretExposureCount = 1 }
        ))
        {
            $parameters = @{ } + $clockBase
            foreach ($key in $failureCase.Keys)
            {
                $parameters[$key] = $failureCase[$key]
            }
            $rejected = $false
            try
            {
                New-AcceptanceClockRecord @parameters | Out-Null
            }
            catch
            {
                $rejected = $true
            }
            if (-not $rejected)
            {
                throw 'acceptance clock prerequisite self-test failed'
            }
            $caseCount++
        }
        $clockRecord = New-AcceptanceClockRecord @clockBase
        Assert-AcceptanceClockRecord ([pscustomobject]$clockRecord) $clockRunId
        if ([string]$clockRecord.acceptanceStartAt -ne '2026-07-18T00:00:03.0000000Z' -or
                ([DateTimeOffset]::Parse([string]$clockRecord.plannedAcceptanceAt) -
                        [DateTimeOffset]::Parse([string]$clockRecord.acceptanceStartAt)).TotalHours -ne 168)
        {
            throw 'acceptance clock max/+168h self-test failed'
        }
        $caseCount += 2

        $clockPath = Join-Path $temporary 'acceptance-clock-start.json'
        if (-not (Commit-CreateOnceJsonIdempotent $clockPath $clockRecord 'ACCEPTANCE_CLOCK_CONFLICT'))
        {
            throw 'acceptance clock first create self-test failed'
        }
        if (Commit-CreateOnceJsonIdempotent $clockPath $clockRecord 'ACCEPTANCE_CLOCK_CONFLICT')
        {
            throw 'acceptance clock idempotency self-test failed'
        }
        $differentClock = [ordered]@{ } + $clockRecord
        $differentClock.plannedAcceptanceAt = '2026-07-25T00:00:04.0000000Z'
        $conflictRejected = $false
        try
        {
            Commit-CreateOnceJsonIdempotent $clockPath $differentClock 'ACCEPTANCE_CLOCK_CONFLICT' | Out-Null
        }
        catch
        {
            $conflictRejected = $_.Exception.Message -eq 'BLOCKED / ACCEPTANCE_CLOCK_CONFLICT'
        }
        if (-not $conflictRejected)
        {
            throw 'acceptance clock second-write self-test failed'
        }
        $caseCount += 3

        $failedClockPath = Join-Path $temporary 'failed-clock.json'
        try
        {
            $invalid = @{ } + $clockBase
            $invalid.FreshSshAt = $null
            New-AcceptanceClockRecord @invalid | Out-Null
        }
        catch
        {
            # Expected: failed prerequisites must not create a clock file.
        }
        if (Test-Path -LiteralPath $failedClockPath)
        {
            throw 'failed acceptance clock wrote timestamps'
        }
        $caseCount++

        $pathEscapeRejected = $false
        try
        {
            Assert-PathBelowRoot $temporary (Join-Path $temporary '..\escape')
        }
        catch
        {
            $pathEscapeRejected = $_.Exception.Message -eq 'BLOCKED / PATH_CONTRACT_INVALID'
        }
        if (-not $pathEscapeRejected)
        {
            throw 'lexical path escape self-test failed'
        }
        $caseCount++

        $linkTarget = Join-Path $temporary 'link-target'
        $linkPath = Join-Path $temporary 'link-path'
        [IO.Directory]::CreateDirectory($linkTarget) | Out-Null
        try
        {
            New-Item -ItemType SymbolicLink -Path $linkPath -Target $linkTarget -ErrorAction Stop | Out-Null
            $symlinkRejected = $false
            try
            {
                Assert-PathBelowRoot $temporary $linkPath
            }
            catch
            {
                $symlinkRejected = $_.Exception.Message -eq 'BLOCKED / SYMLINK_PATH_FORBIDDEN'
            }
            if (-not $symlinkRejected)
            {
                throw 'symlink/reparse self-test failed'
            }
            $symlinkTest = 'PASS / REJECTED'
            $caseCount++
        }
        catch
        {
            if ($_.Exception.Message -eq 'symlink/reparse self-test failed')
            {
                throw
            }
            $symlinkTest = 'NOT_AVAILABLE / PLATFORM_PRIVILEGE'
        }

        $previousStateRoot = $script:StateRoot
        try
        {
            $script:StateRoot = Join-Path $temporary 'state-root'
            $lifecycleRunId = 'gatew-soak-20260718T000001Z-0123abcd'
            $controlRoot = Get-ControlRoot $lifecycleRunId
            [IO.Directory]::CreateDirectory($controlRoot) | Out-Null
            Write-JsonCreateOnce (Get-LifecyclePath $lifecycleRunId) ([ordered]@{
                schemaVersion = 'gatew-soak-lifecycle-v1'
                runId = $lifecycleRunId
                state = 'FAILURE_STOPPED'
                stateSequence = 5L
                reasonCode = 'FAILCLOSE_RECOVERY_PROVEN'
                observedAt = (Get-UtcNow).ToString('o')
            })
            $illegalRejected = $false
            try
            {
                Set-LifecycleState $lifecycleRunId 'RUNNING' 'FORMAL_WORKER_RUNNING' | Out-Null
            }
            catch
            {
                $illegalRejected = $_.Exception.Message -eq 'BLOCKED / ILLEGAL_LIFECYCLE_TRANSITION'
            }
            if (-not $illegalRejected)
            {
                throw 'illegal transition self-test failed'
            }
            $caseCount++
        }
        finally
        {
            $script:StateRoot = $previousStateRoot
        }
    }
    finally
    {
        if (Test-Path -LiteralPath $temporary)
        {
            Remove-Item -LiteralPath $temporary -Recurse -Force
        }
    }
    return [pscustomobject]@{
        decision = 'PASS / FORMAL_CONTROL_SELF_TEST'
        cases = $caseCount
        runIdAllowlist = 'PASS'
        literalConfig = 'PASS'
        terminalStatesHaveNoOutgoingTransition = 'PASS'
        illegalTransitionRejected = 'PASS'
        lexicalPathEscapeRejected = 'PASS'
        symlinkOrReparseRejected = $symlinkTest
        operatorFailureTerminalSeparation = 'PASS'
        terminalCreateOnce = 'PASS / O_EXCL_OR_ATOMIC_LINK'
        zeroLengthCreateOnce = 'PASS / LENGTH_0 / SECOND_CREATE_REJECTED'
        releaseVerifierParameters = 'PASS / HASHTABLE_SPLATTING'
        automaticMatchesCollision = 'PASS / FORBIDDEN'
        preCreatePrerequisite = 'PASS / BEFORE_RUN_ID_AND_DIRECTORY / CLOSED_SCHEMA'
        noNetworkCalled = $true
        credentialAccessed = $false
    }
}

try
{
    $result = switch ($Action)
    {
        'precreate-prerequisite' {
            Invoke-PreCreatePrerequisite
        }
        'prepare' {
            Prepare-FormalRun
        }
        'start' {
            Start-FormalRun
        }
        'status' {
            Show-FormalStatus
        }
        'verify' {
            Verify-FormalRun
        }
        'verify-evidence' {
            Verify-FormalEvidence
        }
        'verify-acceptance' {
            Verify-FormalAcceptance
        }
        'verify-terminal' {
            Verify-FormalTerminal
        }
        'finalize-acceptance' {
            Finalize-FormalAcceptance
        }
        'stop' {
            Request-OperatorStop
        }
        'offline-fail' {
            Inject-OfflineFailure
        }
        'record-fresh-ssh' {
            Record-FreshSshVerification
        }
        'start-acceptance-clock' {
            Start-AcceptanceClock
        }
        'unit-preflight' {
            Invoke-UnitPreflight
        }
        'record-exit' {
            Record-ExitFact
        }
        'self-test' {
            Invoke-ControlSelfTest
        }
    }
    if ($null -ne $result)
    {
        $result | ConvertTo-Json -Depth 12
        if ($Action -eq 'precreate-prerequisite' -and
                -not [bool]$result.readyForAttemptCreation)
        {
            exit 2
        }
        if ($Action -eq 'verify-acceptance' -and
                [string]$result.decision -cne 'PASS / FORMAL_SOAK_ACCEPTANCE_VERIFIED')
        {
            exit 2
        }
    }
}
catch
{
    if ($Action -eq 'precreate-prerequisite')
    {
        New-PreCreateResult ((Get-UtcNow).ToString('o')) $null | ConvertTo-Json -Depth 4
        exit 2
    }
    $message = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$')
    {
        $_.Exception.Message
    }
    else
    {
        'FAIL / FORMAL_CONTROL_INTERNAL_ERROR'
    }
    $failure = [ordered]@{ decision = $message; runId = $RunId }
    if ($Action -eq 'self-test')
    {
        $failure.selfTestDetail = $_.Exception.Message
    }
    [pscustomobject]$failure | ConvertTo-Json
    exit 2
}
