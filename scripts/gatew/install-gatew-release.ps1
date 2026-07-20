[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('install', 'install-units', 'activate', 'verify', 'self-test')]
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
$script:WorkerTemplate = 'nq-gatew-soak@.service'
$script:FailCloseTemplate = 'nq-gatew-soak-failclose@.service'
$script:ReleaseIdPattern = '^(?:[a-f0-9]{40}|candidate-[a-f0-9]{12}-[a-f0-9]{16}-[0-9]{8}T[0-9]{6}Z)$'
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
        'candidate-0123456789ab-0123456789abcdef-20260719T000000Z'
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
    return [pscustomobject]@{
        decision = 'PASS / RELEASE_INSTALLER_SELF_TEST'
        releaseIdAllowlist = 'PASS'
        unitEnablementAllowlist = 'PASS'
        candidateActivationGuard = 'PASS / STATIC_CONTRACT'
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
