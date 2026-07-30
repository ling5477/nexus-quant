[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('install', 'install-units', 'activate', 'verify', 'configure-precreate', 'self-test')]
    [string]$Action,

    [string]$SourceBundle,
    [string]$ReleaseId,
    [switch]$InstallUnits
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:OptRoot = '/opt/nexus-quant'
$script:ReleasesRoot = '/opt/nexus-quant/releases'
$script:CurrentPath = '/opt/nexus-quant/current'
$script:SystemdRoot = '/etc/systemd/system'
$script:GateWConfigRoot = '/etc/nexus-quant/gatew-soak'
$script:PreCreateDescriptorPath = '/etc/nexus-quant/gatew-soak/precreate-prerequisite.json'
$script:ManagementEnvironmentPath = '/opt/nexus-quant/gatew-soak/config/management.env'
$script:CredentialRoot = '/etc/nexus-quant/gatew-soak/credentials'
$script:DatabasePasswordSecretPath = '/etc/nexus-quant/gatew-soak/credentials/db-password.cred'
$script:WorkerTemplate = 'nq-gatew-soak@.service'
$script:FailCloseTemplate = 'nq-gatew-soak-failclose@.service'
$script:ReleaseIdPattern = '^(?:[a-f0-9]{40}|candidate-[a-f0-9]{12}-[a-f0-9]{16})$'
$script:RunuserPath = '/usr/sbin/runuser'
$script:SystemctlPath = '/usr/bin/systemctl'
$script:SystemdAnalyzePath = '/usr/bin/systemd-analyze'
$script:InstallPath = '/usr/bin/install'
$script:ChownPath = '/usr/bin/chown'
$script:ChmodPath = '/usr/bin/chmod'
$script:LnPath = '/usr/bin/ln'
$script:MvPath = '/usr/bin/mv'
$script:ReadlinkPath = '/usr/bin/readlink'

function Test-LinuxPlatform
{
    $platform = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    return $null -ne $platform -and [bool]$platform.Value
}

function Assert-RootLinux
{
    if (-not (Test-LinuxPlatform) -or [Environment]::UserName -ne 'root')
    {
        throw 'BLOCKED / ROOT_RELEASE_INSTALL_REQUIRED'
    }
}

function Invoke-Native
{
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Arguments,
        [switch]$AllowFailure
    )

    if (-not (Test-Path -LiteralPath $FilePath -PathType Leaf))
    {
        if ($AllowFailure)
        {
            return [pscustomobject]@{ ExitCode = 127; Lines = @() }
        }
        throw 'BLOCKED / RELEASE_INSTALL_TOOL_MISSING'
    }
    $lines = @(& $FilePath @Arguments 2> $null)
    $exitCodeValue = [int]$LASTEXITCODE
    if (-not $AllowFailure -and $exitCodeValue -ne 0)
    {
        throw 'FAIL / RELEASE_INSTALL_COMMAND_FAILED'
    }
    return [pscustomobject]@{ ExitCode = $exitCodeValue; Lines = @($lines) }
}

function Assert-ReleaseId
{
    param([Parameter(Mandatory = $true)][string]$Value)
    if ($Value -cnotmatch $script:ReleaseIdPattern)
    {
        throw 'BLOCKED / RELEASE_ID_INVALID'
    }
}

function Get-ReleaseRoot
{
    param([Parameter(Mandatory = $true)][string]$Value)
    Assert-ReleaseId $Value
    return "$( $script:ReleasesRoot )/$Value"
}

function Assert-PathBelowReleases
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $root = [IO.Path]::GetFullPath($script:ReleasesRoot).TrimEnd('/')
    $normalized = [IO.Path]::GetFullPath($Path)
    if (-not $normalized.StartsWith($root + '/', [StringComparison]::Ordinal))
    {
        throw 'BLOCKED / RELEASE_INSTALL_PATH_INVALID'
    }
}

function Get-PosixMetadata
{
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path))
    {
        throw 'BLOCKED / PRECREATE_CONFIG_PATH_INVALID'
    }
    $item = Get-Item -LiteralPath $Path -Force
    if ($null -ne $item.LinkType -or $item.Attributes.ToString() -match 'ReparsePoint')
    {
        throw 'BLOCKED / PRECREATE_CONFIG_PATH_INVALID'
    }
    $result = Invoke-Native '/usr/bin/stat' @('-c', '%F|%a|%U|%G', '--', $Path)
    $parts = (($result.Lines -join "`n").Trim()).Split('|')
    if ($parts.Count -ne 4)
    {
        throw 'BLOCKED / PRECREATE_CONFIG_PATH_INVALID'
    }
    return [pscustomobject]@{ Type = $parts[0]; Mode = $parts[1]; Owner = $parts[2]; Group = $parts[3] }
}

function Assert-StableAbsolutePath
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $normalized = [IO.Path]::GetFullPath($Path)
    $resolved = Invoke-Native $script:ReadlinkPath @('-f', '--', $normalized)
    if ((($resolved.Lines -join "`n").Trim()) -cne $normalized)
    {
        throw 'BLOCKED / PRECREATE_CONFIG_PATH_INVALID'
    }
}

function Assert-RootFileContract
{
    param([Parameter(Mandatory = $true)][string]$Path)

    $metadata = Get-PosixMetadata $Path
    if ($metadata.Type -notlike '*regular file*' -or $metadata.Mode -ne '600' -or
            $metadata.Owner -ne 'root' -or $metadata.Group -ne 'root')
    {
        throw 'BLOCKED / PRECREATE_CONFIG_OWNERSHIP_INVALID'
    }
}

function ConvertFrom-EnvironmentLiteral
{
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    $literal = $Value.Trim()
    if ($literal.Length -ge 2 -and
            (($literal[0] -eq '"' -and $literal[$literal.Length - 1] -eq '"') -or
                    ($literal[0] -eq "'" -and $literal[$literal.Length - 1] -eq "'")))
    {
        $literal = $literal.Substring(1, $literal.Length - 2)
    }
    if ($literal.IndexOf([char]0) -ge 0 -or $literal -match "[`r`n]" -or
            $literal -match '\$\(' -or $literal -match '`' -or $literal -match '[|<>]')
    {
        throw 'BLOCKED / PRECREATE_SOURCE_CONFIG_INVALID'
    }
    return $literal
}

function Read-PreCreateSourceValues
{
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        throw 'BLOCKED / PRECREATE_SOURCE_CONFIG_INVALID'
    }
    $required = @(
        'NQ_GATEW_SOAK_DB_URL', 'NQ_GATEW_SOAK_DB_USER', 'NQ_GATEW_SOAK_DB_PASSWORD',
        'NQ_DB_URL', 'NQ_DB_USER', 'NQ_DB_PASSWORD'
    )
    $seen = @{ }
    $values = @{ }
    foreach ($lineValue in Get-Content -LiteralPath $Path)
    {
        $line = [string]$lineValue
        if ($line -notmatch '^([A-Z][A-Z0-9_]*)=(.*)$')
        {
            continue
        }
        $name = [string]$Matches[1]
        if ($name -notin $required)
        {
            continue
        }
        if ( $seen.ContainsKey($name))
        {
            throw 'BLOCKED / PRECREATE_SOURCE_CONFIG_INVALID'
        }
        $seen[$name] = $true
        $literal = ConvertFrom-EnvironmentLiteral ([string]$Matches[2])
        if ($name -eq 'NQ_DB_PASSWORD')
        {
            if ( [string]::IsNullOrWhiteSpace($literal))
            {
                throw 'BLOCKED / PRECREATE_SOURCE_CONFIG_INVALID'
            }
            $values[$name] = 'PRESENT_REDACTED'
        }
        else
        {
            $values[$name] = $literal
        }
    }
    if (@($required | Where-Object { -not $seen.ContainsKey($_) }).Count -ne 0 -or
            [string]$values.NQ_GATEW_SOAK_DB_URL -cne '${NQ_DB_URL}' -or
            [string]$values.NQ_GATEW_SOAK_DB_USER -cne '${NQ_DB_USER}' -or
            [string]$values.NQ_GATEW_SOAK_DB_PASSWORD -cne '${NQ_DB_PASSWORD}')
    {
        throw 'BLOCKED / PRECREATE_SOURCE_CONFIG_INVALID'
    }
    foreach ($literal in @([string]$values.NQ_DB_URL, [string]$values.NQ_DB_USER))
    {
        if ($literal -match '\$\{?[A-Za-z_][A-Za-z0-9_]*\}?' -or
                $literal -match '%[A-Za-z_][A-Za-z0-9_]*%')
        {
            throw 'BLOCKED / PRECREATE_SOURCE_CONFIG_INVALID'
        }
    }
    return $values
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
            [string]$Descriptor.passwordSecretFile -cne $script:DatabasePasswordSecretPath -or
            [string]$Descriptor.managementLoopbackUrl -cne 'http://127.0.0.1:18889/actuator/health' -or
            [string]$Descriptor.expectedCredentialType -cne 'OKX_API_V5' -or
            [string]$Descriptor.expectedEnvironment -cne 'LIVE')
    {
        throw 'BLOCKED / PRECREATE_DESCRIPTOR_INVALID'
    }
    return $Descriptor
}

function New-PreCreateDescriptor
{
    param(
        [Parameter(Mandatory = $true)][string]$ManagementEnvironment,
        [Parameter(Mandatory = $true)][string]$SecretFile
    )

    $values = Read-PreCreateSourceValues $ManagementEnvironment
    $url = [string]$values.NQ_DB_URL
    if (-not $url.StartsWith('jdbc:postgresql://', [StringComparison]::Ordinal))
    {
        throw 'BLOCKED / PRECREATE_SOURCE_CONFIG_INVALID'
    }
    try
    {
        $uri = [Uri]::new($url.Substring('jdbc:'.Length))
    }
    catch
    {
        throw 'BLOCKED / PRECREATE_SOURCE_CONFIG_INVALID'
    }
    $databaseName = $uri.AbsolutePath.TrimStart('/')
    if ($uri.Scheme -cne 'postgresql' -or $uri.Host -notin @('127.0.0.1', 'localhost') -or
            $uri.Port -lt 1 -or $uri.Port -gt 65535 -or
            -not [string]::IsNullOrWhiteSpace($uri.UserInfo) -or
            -not [string]::IsNullOrWhiteSpace($uri.Query) -or
            -not [string]::IsNullOrWhiteSpace($uri.Fragment) -or
            $databaseName -cnotmatch '^[A-Za-z][A-Za-z0-9_]{0,62}$' -or
            [string]$values.NQ_DB_USER -cnotmatch '^[A-Za-z_][A-Za-z0-9_-]{0,62}$' -or
            $SecretFile -cne $script:DatabasePasswordSecretPath)
    {
        throw 'BLOCKED / PRECREATE_SOURCE_CONFIG_INVALID'
    }
    return Assert-PreCreateDescriptorValue ([pscustomobject][ordered]@{
        schemaVersion = 'gatew-precreate-prerequisite-v1'
        databaseHost = $uri.Host
        databasePort = [int]$uri.Port
        databaseName = $databaseName
        databaseUser = [string]$values.NQ_DB_USER
        passwordSecretFile = $SecretFile
        managementLoopbackUrl = 'http://127.0.0.1:18889/actuator/health'
        expectedCredentialType = 'OKX_API_V5'
        expectedEnvironment = 'LIVE'
    })
}

function Configure-PreCreateDescriptor
{
    Assert-RootLinux
    Assert-StableAbsolutePath $script:GateWConfigRoot
    Assert-StableAbsolutePath $script:ManagementEnvironmentPath
    Assert-StableAbsolutePath $script:DatabasePasswordSecretPath
    $configRootMetadata = Get-PosixMetadata $script:GateWConfigRoot
    if ($configRootMetadata.Type -notlike '*directory*' -or
            $configRootMetadata.Owner -ne 'root' -or $configRootMetadata.Group -ne 'root' -or
            $configRootMetadata.Mode -notin @('700', '750', '755'))
    {
        throw 'BLOCKED / PRECREATE_CONFIG_OWNERSHIP_INVALID'
    }
    $sourceMetadata = Get-PosixMetadata $script:ManagementEnvironmentPath
    if ($sourceMetadata.Type -notlike '*regular file*' -or $sourceMetadata.Mode -ne '600' -or
            $sourceMetadata.Owner -notin @('root', 'nqgatew') -or
            $sourceMetadata.Group -cne $sourceMetadata.Owner)
    {
        throw 'BLOCKED / PRECREATE_SOURCE_CONFIG_OWNERSHIP_INVALID'
    }
    Assert-RootFileContract $script:DatabasePasswordSecretPath
    $descriptor = New-PreCreateDescriptor `
        $script:ManagementEnvironmentPath $script:DatabasePasswordSecretPath
    if (Test-Path -LiteralPath $script:PreCreateDescriptorPath)
    {
        Assert-RootFileContract $script:PreCreateDescriptorPath
        if ((Get-Item -LiteralPath $script:PreCreateDescriptorPath -Force).Length -gt 4096)
        {
            throw 'BLOCKED / PRECREATE_DESCRIPTOR_INVALID'
        }
        Assert-PreCreateDescriptorValue `
            (Get-Content -LiteralPath $script:PreCreateDescriptorPath -Raw | ConvertFrom-Json) | Out-Null
    }
    $temporary = "$( $script:GateWConfigRoot )/.precreate-prerequisite-$PID-$([Guid]::NewGuid().ToString('N') ).json"
    try
    {
        if (-not (Test-Path -LiteralPath $script:GateWConfigRoot -PathType Container))
        {
            throw 'BLOCKED / PRECREATE_CONFIG_PATH_INVALID'
        }
        [IO.File]::WriteAllText(
                $temporary,
                (($descriptor | ConvertTo-Json -Depth 4) + "`n"),
                [Text.UTF8Encoding]::new($false)
        )
        Invoke-Native $script:ChownPath @('--', 'root:root', $temporary) | Out-Null
        Invoke-Native $script:ChmodPath @('0600', '--', $temporary) | Out-Null
        Invoke-Native $script:MvPath @('-T', '--', $temporary, $script:PreCreateDescriptorPath) | Out-Null
        Assert-RootFileContract $script:PreCreateDescriptorPath
        Assert-PreCreateDescriptorValue `
            (Get-Content -LiteralPath $script:PreCreateDescriptorPath -Raw | ConvertFrom-Json) | Out-Null
        return [pscustomobject]@{
            decision = 'PASS / PRECREATE_DESCRIPTOR_CONFIGURED'
            descriptorPath = $script:PreCreateDescriptorPath
            descriptorOwner = 'root:root'
            descriptorMode = '0600'
            secretReferenceContract = 'PASS / ROOT_OWNED_0600'
            credentialMaterialExposed = $false
        }
    }
    finally
    {
        if (Test-Path -LiteralPath $temporary)
        {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
}

function Read-Manifest
{
    param([Parameter(Mandatory = $true)][string]$Root)

    $path = Join-Path $Root 'release-manifest.json'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_MANIFEST_MISSING'
    }
    return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}

function Invoke-ReleaseVerifier
{
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$ExpectedId,
        [switch]$Staging
    )

    $verifier = Join-Path $Root 'bin/verify-gatew-release.ps1'
    if (-not (Test-Path -LiteralPath $verifier -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_VERIFIER_MISSING'
    }
    $arguments = @('-NoProfile', '-File', $verifier, '-ReleaseRoot', $Root, '-ExpectedReleaseId', $ExpectedId)
    if ($Staging)
    {
        $arguments += '-SkipPosix'
    }
    $output = @(& '/usr/bin/pwsh' @arguments 2> $null)
    if ($LASTEXITCODE -ne 0 -or $output.Count -eq 0)
    {
        throw 'BLOCKED / RELEASE_VERIFY_FAILED'
    }
    try
    {
        $result = ($output -join "`n") | ConvertFrom-Json
        if ([string]$result.decision -ne 'PASS / IMMUTABLE_RELEASE_VERIFIED')
        {
            throw 'BLOCKED / RELEASE_VERIFY_FAILED'
        }
        return $result
    }
    catch
    {
        throw 'BLOCKED / RELEASE_VERIFY_FAILED'
    }
}

function Ensure-RootDirectory
{
    param([Parameter(Mandatory = $true)][string]$Path)

    [IO.Directory]::CreateDirectory($Path) | Out-Null
    Invoke-Native $script:ChownPath @('--', 'root:root', $Path) | Out-Null
    Invoke-Native $script:ChmodPath @('0755', '--', $Path) | Out-Null
}

function Set-ReleasePermissions
{
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)]$Manifest
    )

    Assert-PathBelowReleases $Root
    Invoke-Native $script:ChownPath @('-R', 'root:root', '--', $Root) | Out-Null
    foreach ($directory in @(Get-ChildItem -LiteralPath $Root -Directory -Recurse -Force))
    {
        Invoke-Native $script:ChmodPath @('0755', '--', $directory.FullName) | Out-Null
    }
    Invoke-Native $script:ChmodPath @('0755', '--', $Root) | Out-Null
    $manifestPath = Join-Path $Root 'release-manifest.json'
    Invoke-Native $script:ChmodPath @('0644', '--', $manifestPath) | Out-Null
    foreach ($artifact in @($Manifest.artifacts))
    {
        $path = Join-Path $Root ([string]$artifact.relativePath)
        Invoke-Native $script:ChmodPath @([string]$artifact.mode, '--', $path) | Out-Null
    }
}

function Assert-NoActiveGateWInstances
{
    $result = Invoke-Native $script:SystemctlPath @(
        'list-units', '--all', '--plain', '--no-legend',
        'nq-gatew-soak@*.service', 'nq-gatew-soak-failclose@*.service'
    ) -AllowFailure
    if ($result.ExitCode -ne 0)
    {
        throw 'BLOCKED / GATEW_UNIT_STATE_UNAVAILABLE'
    }
    foreach ($line in @($result.Lines))
    {
        $parts = @(([string]$line).Trim() -split '\s+')
        if ($parts.Count -ge 4 -and $parts[2] -in @('active', 'activating', 'deactivating', 'reloading'))
        {
            throw 'BLOCKED / ACTIVE_GATEW_UNIT_PRESENT'
        }
    }
}

function Assert-NqgatewCannotWrite
{
    param([Parameter(Mandatory = $true)][string]$Root)

    $test = Invoke-Native $script:RunuserPath @(
        '-u', 'nqgatew', '--', '/usr/bin/test', '-w', $Root
    ) -AllowFailure
    if ($test.ExitCode -eq 0)
    {
        throw 'BLOCKED / RELEASE_WRITABLE_BY_RUNTIME_USER'
    }
    if ($test.ExitCode -ne 1)
    {
        throw 'BLOCKED / RELEASE_RUNTIME_WRITE_TEST_INVALID'
    }
}

function Set-AtomicSymlink
{
    param(
        [Parameter(Mandatory = $true)][string]$LinkPath,
        [Parameter(Mandatory = $true)][string]$Target
    )

    $parent = Split-Path -Parent $LinkPath
    if (-not (Test-Path -LiteralPath $parent -PathType Container))
    {
        throw 'BLOCKED / RELEASE_SYMLINK_PARENT_INVALID'
    }
    $temporary = Join-Path $parent ('.nq-link-' + [Guid]::NewGuid().ToString('N'))
    try
    {
        Invoke-Native $script:LnPath @('-s', '--', $Target, $temporary) | Out-Null
        Invoke-Native $script:ChownPath @('-h', 'root:root', '--', $temporary) | Out-Null
        Invoke-Native $script:MvPath @('-T', '-f', '--', $temporary, $LinkPath) | Out-Null
    }
    finally
    {
        if (Test-Path -LiteralPath $temporary)
        {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
}

function Install-FormalUnitLinks
{
    param([Parameter(Mandatory = $true)][string]$UnitReleaseRoot)

    Assert-NoActiveGateWInstances
    foreach ($template in @($script:WorkerTemplate, $script:FailCloseTemplate))
    {
        $target = Join-Path $UnitReleaseRoot "systemd/$template"
        if (-not (Test-Path -LiteralPath $target -PathType Leaf))
        {
            throw 'BLOCKED / RELEASE_SYSTEMD_UNIT_MISSING'
        }
        Set-AtomicSymlink "$( $script:SystemdRoot )/$template" $target
    }
    Invoke-Native $script:SystemdAnalyzePath @(
        'verify', "$( $script:SystemdRoot )/$( $script:WorkerTemplate )",
        "$( $script:SystemdRoot )/$( $script:FailCloseTemplate )"
    ) | Out-Null
    Invoke-Native $script:SystemctlPath @('daemon-reload') | Out-Null
    foreach ($template in @($script:WorkerTemplate, $script:FailCloseTemplate))
    {
        $enabled = Invoke-Native $script:SystemctlPath @('is-enabled', $template) -AllowFailure
        $text = (($enabled.Lines -join "`n").Trim())
        if (-not (Test-SafeUnitEnablementState $text))
        {
            throw 'BLOCKED / FORMAL_UNIT_ENABLEMENT_UNSAFE'
        }
    }
    Assert-NoActiveGateWInstances
}

function Test-SafeUnitEnablementState
{
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$State)

    # systemctl reports a unit file linked into the search path as "linked";
    # this is the expected inactive state for release-owned template symlinks.
    return $State -in @('disabled', 'static', 'indirect', 'linked')
}

function Install-Release
{
    Assert-RootLinux
    if ( [string]::IsNullOrWhiteSpace($SourceBundle))
    {
        throw 'BLOCKED / RELEASE_SOURCE_REQUIRED'
    }
    $sourceRoot = [IO.Path]::GetFullPath($SourceBundle)
    if (-not (Test-Path -LiteralPath $sourceRoot -PathType Container))
    {
        throw 'BLOCKED / RELEASE_SOURCE_REQUIRED'
    }
    $sourceManifest = Read-Manifest $sourceRoot
    $sourceReleaseId = [string]$sourceManifest.releaseId
    Assert-ReleaseId $sourceReleaseId
    if (-not [string]::IsNullOrWhiteSpace($ReleaseId) -and $ReleaseId -cne $sourceReleaseId)
    {
        throw 'BLOCKED / RELEASE_ID_MISMATCH'
    }
    Invoke-ReleaseVerifier $sourceRoot $sourceReleaseId -Staging | Out-Null
    Ensure-RootDirectory $script:OptRoot
    Ensure-RootDirectory $script:ReleasesRoot
    $releaseRoot = Get-ReleaseRoot $sourceReleaseId
    if (Test-Path -LiteralPath $releaseRoot)
    {
        $verified = Invoke-ReleaseVerifier $releaseRoot $sourceReleaseId
        Assert-NqgatewCannotWrite $releaseRoot
        if ($InstallUnits)
        {
            Install-FormalUnitLinks $releaseRoot
        }
        return [pscustomobject]@{
            decision = 'NO_CHANGE / IMMUTABLE_RELEASE_ALREADY_INSTALLED'
            releaseId = $sourceReleaseId
            releaseRoot = $releaseRoot
            manifestSha256 = $verified.manifestSha256
            nqgatewWritable = $false
            unitsInstalled = [bool]$InstallUnits
        }
    }

    $stageRoot = "$( $script:ReleasesRoot )/.install-$sourceReleaseId-$PID"
    Assert-PathBelowReleases $stageRoot
    if (Test-Path -LiteralPath $stageRoot)
    {
        throw 'BLOCKED / RELEASE_INSTALL_STAGE_EXISTS'
    }
    try
    {
        [IO.Directory]::CreateDirectory($stageRoot) | Out-Null
        Copy-Item -Path (Join-Path $sourceRoot '*') -Destination $stageRoot -Recurse
        $stageManifest = Read-Manifest $stageRoot
        if ([string]$stageManifest.releaseId -cne $sourceReleaseId)
        {
            throw 'BLOCKED / RELEASE_ID_MISMATCH'
        }
        Set-ReleasePermissions $stageRoot $stageManifest
        Invoke-ReleaseVerifier $stageRoot $sourceReleaseId | Out-Null
        Invoke-Native $script:MvPath @('-T', '--', $stageRoot, $releaseRoot) | Out-Null
        $verified = Invoke-ReleaseVerifier $releaseRoot $sourceReleaseId
        Assert-NqgatewCannotWrite $releaseRoot
        if ($InstallUnits)
        {
            Install-FormalUnitLinks $releaseRoot
        }
        return [pscustomobject]@{
            decision = 'PASS / ROOT_OWNED_RELEASE_INSTALLED'
            releaseId = $sourceReleaseId
            releaseRoot = $releaseRoot
            manifestSha256 = $verified.manifestSha256
            artifactCount = $verified.artifactCount
            nqgatewWritable = $false
            unitsInstalled = [bool]$InstallUnits
        }
    }
    finally
    {
        if (Test-Path -LiteralPath $stageRoot)
        {
            Assert-PathBelowReleases $stageRoot
            Remove-Item -LiteralPath $stageRoot -Recurse -Force
        }
    }
}

function Install-UnitsForRelease
{
    Assert-RootLinux
    Assert-ReleaseId $ReleaseId
    $releaseRoot = Get-ReleaseRoot $ReleaseId
    $verified = Invoke-ReleaseVerifier $releaseRoot $ReleaseId
    Assert-NqgatewCannotWrite $releaseRoot
    Install-FormalUnitLinks $releaseRoot
    return [pscustomobject]@{
        decision = 'PASS / FORMAL_UNITS_BOUND_TO_FIXED_RELEASE'
        releaseId = $ReleaseId
        releaseRoot = $releaseRoot
        manifestSha256 = $verified.manifestSha256
        activeGateWUnits = 0
    }
}

function Activate-Release
{
    Assert-RootLinux
    Assert-ReleaseId $ReleaseId
    $releaseRoot = Get-ReleaseRoot $ReleaseId
    $verified = Invoke-ReleaseVerifier $releaseRoot $ReleaseId
    $manifest = Read-Manifest $releaseRoot
    if ([string]$manifest.sourceTreeMode -ne 'EXACT_COMMIT' -or
            [string]$manifest.sourceCommit -cne $ReleaseId)
    {
        throw 'BLOCKED / CANDIDATE_RELEASE_ACTIVATION_FORBIDDEN'
    }
    Assert-NqgatewCannotWrite $releaseRoot
    Assert-NoActiveGateWInstances
    Set-AtomicSymlink $script:CurrentPath $releaseRoot
    Install-FormalUnitLinks $script:CurrentPath
    $resolvedCurrent = ((Invoke-Native $script:ReadlinkPath @('-f', '--', $script:CurrentPath)).Lines -join "`n").Trim()
    if ($resolvedCurrent -cne $releaseRoot)
    {
        throw 'FAIL / CURRENT_RELEASE_SWITCH_FAILED'
    }
    $currentVerification = Invoke-ReleaseVerifier $script:CurrentPath $ReleaseId
    return [pscustomobject]@{
        decision = 'PASS / EXACT_COMMIT_RELEASE_ACTIVATED'
        releaseId = $ReleaseId
        releaseRoot = $releaseRoot
        current = $script:CurrentPath
        currentTarget = $resolvedCurrent
        manifestSha256 = $currentVerification.manifestSha256
        nqgatewWritable = $false
        activeGateWUnits = 0
        rollback = 'atomically switch /opt/nexus-quant/current to a previously verified release and daemon-reload'
    }
}

function Verify-InstalledRelease
{
    Assert-RootLinux
    Assert-ReleaseId $ReleaseId
    $root = Get-ReleaseRoot $ReleaseId
    $verified = Invoke-ReleaseVerifier $root $ReleaseId
    Assert-NqgatewCannotWrite $root
    return [pscustomobject]@{
        decision = 'PASS / ROOT_OWNED_RELEASE_VERIFIED'
        releaseId = $ReleaseId
        releaseRoot = $root
        manifestSha256 = $verified.manifestSha256
        artifactCount = $verified.artifactCount
        nqgatewWritable = $false
    }
}

function Invoke-InstallerSelfTest
{
    foreach ($valid in @(
        '0123456789abcdef0123456789abcdef01234567',
        'candidate-0123456789ab-0123456789abcdef'
    ))
    {
        Assert-ReleaseId $valid
    }
    $blocked = $false
    try
    {
        Assert-ReleaseId '../escape'
    }
    catch
    {
        $blocked = $true
    }
    if (-not $blocked)
    {
        throw 'release id self-test failed'
    }
    foreach ($safeState in @('disabled', 'static', 'indirect', 'linked'))
    {
        if (-not (Test-SafeUnitEnablementState $safeState))
        {
            throw 'safe unit enablement state self-test failed'
        }
    }
    foreach ($unsafeState in @('', 'enabled', 'enabled-runtime', 'linked-runtime', 'masked'))
    {
        if (Test-SafeUnitEnablementState $unsafeState)
        {
            throw 'unsafe unit enablement state self-test failed'
        }
    }
    $fixtureRoot = Join-Path ([IO.Path]::GetTempPath()) ('nq-gatew-installer-' + [Guid]::NewGuid().ToString('N'))
    try
    {
        [IO.Directory]::CreateDirectory($fixtureRoot) | Out-Null
        $fixture = Join-Path $fixtureRoot 'management.env'
        [IO.File]::WriteAllText($fixture, @'
NQ_GATEW_SOAK_DB_URL=${NQ_DB_URL}
NQ_GATEW_SOAK_DB_USER=${NQ_DB_USER}
NQ_GATEW_SOAK_DB_PASSWORD=${NQ_DB_PASSWORD}
NQ_DB_URL=jdbc:postgresql://127.0.0.1:5432/nexus_quant
NQ_DB_USER=nq_runtime
NQ_DB_PASSWORD=fixture-only
IGNORED_FIELD=value
'@,[Text.UTF8Encoding]::new($false))
        $descriptor = New-PreCreateDescriptor $fixture $script:DatabasePasswordSecretPath
        if ([string]$descriptor.databaseName -cne 'nexus_quant' -or
                [string]$descriptor.databaseUser -cne 'nq_runtime')
        {
            throw 'precreate descriptor source self-test failed'
        }
        foreach ($unsafe in @(
            '${UNRESOLVED}', '$(whoami)', '`whoami`', 'nq_runtime|id'
        ))
        {
            $blocked = $false
            try
            {
                ConvertFrom-EnvironmentLiteral $unsafe | Out-Null
                if ($unsafe -eq '${UNRESOLVED}')
                {
                    $candidate = Get-Content -LiteralPath $fixture -Raw
                    $candidate = $candidate.Replace('NQ_DB_USER=nq_runtime', "NQ_DB_USER=$unsafe")
                    $unsafeFixture = Join-Path $fixtureRoot ([Guid]::NewGuid().ToString('N') + '.env')
                    [IO.File]::WriteAllText($unsafeFixture, $candidate,[Text.UTF8Encoding]::new($false))
                    New-PreCreateDescriptor $unsafeFixture $script:DatabasePasswordSecretPath | Out-Null
                }
            }
            catch
            {
                $blocked = $true
            }
            if (-not $blocked)
            {
                throw 'precreate source rejection self-test failed'
            }
        }
    }
    finally
    {
        if (Test-Path -LiteralPath $fixtureRoot)
        {
            Remove-Item -LiteralPath $fixtureRoot -Recurse -Force
        }
    }
    return [pscustomobject]@{
        decision = 'PASS / RELEASE_INSTALLER_SELF_TEST'
        releaseIdAllowlist = 'PASS'
        unitEnablementAllowlist = 'PASS'
        candidateActivationGuard = 'PASS / STATIC_CONTRACT'
        preCreateDescriptor = 'PASS / FIXED_REFERENCE_CHAIN / CLOSED_SCHEMA'
        credentialAccessed = $false
        networkCalled = $false
    }
}

try
{
    $result = switch ($Action)
    {
        'install' {
            Install-Release
        }
        'install-units' {
            Install-UnitsForRelease
        }
        'activate' {
            Activate-Release
        }
        'verify' {
            Verify-InstalledRelease
        }
        'configure-precreate' {
            Configure-PreCreateDescriptor
        }
        'self-test' {
            Invoke-InstallerSelfTest
        }
    }
    $result | ConvertTo-Json -Depth 8
}
catch
{
    $message = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$')
    {
        $_.Exception.Message
    }
    else
    {
        'FAIL / RELEASE_INSTALL_INTERNAL_ERROR'
    }
    $failure = [ordered]@{ decision = $message }
    if ($Action -eq 'self-test')
    {
        $failure.selfTestDetail = $_.Exception.Message
    }
    [pscustomobject]$failure | ConvertTo-Json
    exit 2
}
