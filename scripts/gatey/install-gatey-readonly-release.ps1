[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('install', 'verify', 'activate')]
    [string]$Action,
    [string]$SourceRoot,
    [string]$ReleaseId,
    [string]$InstallationRoot = '/opt/nexus-quant',
    [string]$ServiceUser = 'nq-gatey-readonly',
    [switch]$DisposableTestRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$contractPath = Join-Path $PSScriptRoot 'gatey-readonly-release-contract.psm1'
Import-Module $contractPath -Force -DisableNameChecking

function Invoke-Native
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Arguments,
        [switch]$AllowFailure
    )
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_INSTALL_TOOL_MISSING'
    }
    $lines = @(& $Path @Arguments 2>$null)
    $exitCode = [int]$LASTEXITCODE
    if (-not $AllowFailure -and $exitCode -ne 0)
    {
        throw 'FAIL / RELEASE_INSTALL_COMMAND_FAILED'
    }
    return [pscustomobject]@{ ExitCode = $exitCode; Lines = @($lines) }
}

function Assert-RootLinux
{
    $linux = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    if ($null -eq $linux -or -not [bool]$linux.Value)
    {
        throw 'BLOCKED / ROOT_LINUX_INSTALL_REQUIRED'
    }
    $identity = Invoke-Native '/usr/bin/id' @('-u')
    if (($identity.Lines -join '').Trim() -cne '0')
    {
        throw 'BLOCKED / ROOT_LINUX_INSTALL_REQUIRED'
    }
}

function Assert-InstallationRoot
{
    $script:ResolvedInstallationRoot = [IO.Path]::GetFullPath($InstallationRoot).TrimEnd('/')
    if ($DisposableTestRoot)
    {
        if (-not $script:ResolvedInstallationRoot.StartsWith('/tmp/', [StringComparison]::Ordinal))
        {
            throw 'BLOCKED / DISPOSABLE_INSTALL_ROOT_INVALID'
        }
    }
    elseif ($script:ResolvedInstallationRoot -cne '/opt/nexus-quant')
    {
        throw 'BLOCKED / INSTALLATION_ROOT_INVALID'
    }
    $script:ReleasesRoot = Join-Path $script:ResolvedInstallationRoot 'releases'
    $script:CurrentPath = Join-Path $script:ResolvedInstallationRoot 'current'
}

function Ensure-RootDirectory([string]$Path)
{
    [IO.Directory]::CreateDirectory($Path) | Out-Null
    Invoke-Native '/usr/bin/chown' @('root:root', '--', $Path) | Out-Null
    Invoke-Native '/usr/bin/chmod' @('0755', '--', $Path) | Out-Null
}

function Get-ReleaseRoot([string]$Value)
{
    if ($Value -cnotmatch '^[0-9a-f]{40}$')
    {
        throw 'BLOCKED / RELEASE_ID_INVALID'
    }
    return Join-Path $script:ReleasesRoot $Value
}

function Read-Manifest([string]$Root)
{
    $path = Join-Path $Root 'release-manifest.json'
    if (-not (Test-Path -LiteralPath $path -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_MANIFEST_MISSING'
    }
    return Get-Content -LiteralPath $path -Raw | ConvertFrom-Json
}

function Set-InstalledPermissions([string]$Root, $Manifest)
{
    Invoke-Native '/usr/bin/chown' @('-R', 'root:root', '--', $Root) | Out-Null
    foreach ($directory in @(
        Get-Item -LiteralPath $Root -Force
        Get-ChildItem -LiteralPath $Root -Directory -Recurse -Force
    ))
    {
        Invoke-Native '/usr/bin/chmod' @('0755', '--', $directory.FullName) | Out-Null
    }
    Invoke-Native '/usr/bin/chmod' @('0644', '--', (Join-Path $Root 'release-manifest.json')) | Out-Null
    foreach ($artifact in @($Manifest.artifacts))
    {
        Invoke-Native '/usr/bin/chmod' @(
            [string]$artifact.mode, '--', (Join-Path $Root ([string]$artifact.relativePath))
        ) | Out-Null
    }
}

function Assert-ServiceUserCannotWrite([string]$Root)
{
    $probe = Invoke-Native '/usr/sbin/runuser' @(
        '-u', $ServiceUser, '--', '/usr/bin/test', '-w', $Root
    ) -AllowFailure
    if ($probe.ExitCode -eq 0)
    {
        throw 'BLOCKED / RELEASE_WRITABLE_BY_SERVICE_USER'
    }
    if ($probe.ExitCode -ne 1)
    {
        throw 'BLOCKED / RELEASE_SERVICE_USER_WRITE_PROBE_INVALID'
    }
}

function Copy-VerifiedRelease([string]$Source, [string]$Stage, $Manifest)
{
    [IO.Directory]::CreateDirectory($Stage) | Out-Null
    $sourceManifest = Join-Path $Source 'release-manifest.json'
    $null = Assert-GateYRegularFileIdentity $sourceManifest
    [IO.File]::Copy($sourceManifest, (Join-Path $Stage 'release-manifest.json'), $false)
    foreach ($artifact in @($Manifest.artifacts))
    {
        $relative = [string]$artifact.relativePath
        $sourcePath = Join-Path $Source $relative
        $null = Assert-GateYRegularFileIdentity $sourcePath
        if ((Get-Item -LiteralPath $sourcePath -Force).Length -ne [long]$artifact.size -or
                (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant() -cne
                [string]$artifact.sha256)
        {
            throw 'BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH'
        }
        $targetPath = Join-Path $Stage $relative
        [IO.Directory]::CreateDirectory((Split-Path -Parent $targetPath)) | Out-Null
        [IO.File]::Copy($sourcePath, $targetPath, $false)
    }
}

function Install-Release
{
    if ([string]::IsNullOrWhiteSpace($SourceRoot))
    {
        throw 'BLOCKED / RELEASE_SOURCE_REQUIRED'
    }
    $source = [IO.Path]::GetFullPath($SourceRoot).TrimEnd('/')
    $verifiedSource = Test-GateYReadonlyRelease $source
    $manifest = Read-Manifest $source
    if (-not [string]::IsNullOrWhiteSpace($ReleaseId) -and $ReleaseId -cne [string]$manifest.releaseId)
    {
        throw 'BLOCKED / RELEASE_ID_MISMATCH'
    }
    Ensure-RootDirectory $script:ResolvedInstallationRoot
    Ensure-RootDirectory $script:ReleasesRoot
    $releaseRoot = Get-ReleaseRoot ([string]$manifest.releaseId)
    if (Test-Path -LiteralPath $releaseRoot)
    {
        throw 'BLOCKED / RELEASE_ALREADY_EXISTS'
    }
    $stage = Join-Path $script:ReleasesRoot ('.install-' + [string]$manifest.releaseId + '-' + $PID)
    if (Test-Path -LiteralPath $stage)
    {
        throw 'BLOCKED / RELEASE_INSTALL_STAGE_EXISTS'
    }
    try
    {
        Copy-VerifiedRelease $source $stage $manifest
        Set-InstalledPermissions $stage $manifest
        $null = Test-GateYReadonlyRelease $stage -RequirePosix
        Assert-ServiceUserCannotWrite $stage
        Invoke-Native '/usr/bin/mv' @('-T', '-n', '--', $stage, $releaseRoot) | Out-Null
        if (Test-Path -LiteralPath $stage)
        {
            throw 'BLOCKED / RELEASE_ALREADY_EXISTS'
        }
        $verifiedInstalled = Test-GateYReadonlyRelease $releaseRoot -RequirePosix
        Assert-ServiceUserCannotWrite $releaseRoot
        return [pscustomobject][ordered]@{
            decision = 'PASS / GATEY_READONLY_RELEASE_INSTALLED_VERIFIED'
            contractState = 'INSTALLED_VERIFIED'
            releaseId = $verifiedInstalled.releaseId
            releaseRoot = $releaseRoot
            manifestSha256 = $verifiedInstalled.manifestSha256
            sourceManifestSha256 = $verifiedSource.manifestSha256
            copiedIndependentFiles = $true
            linkIntegrityVerified = $true
            posixVerified = $true
            serviceUserWritable = $false
        }
    }
    finally
    {
        if (Test-Path -LiteralPath $stage)
        {
            $resolvedStage = [IO.Path]::GetFullPath($stage)
            if (-not $resolvedStage.StartsWith($script:ReleasesRoot + '/', [StringComparison]::Ordinal))
            {
                throw 'FAIL / RELEASE_STAGE_CLEANUP_PATH_INVALID'
            }
            Remove-Item -LiteralPath $resolvedStage -Recurse -Force
        }
    }
}

function Verify-Release
{
    $releaseRoot = Get-ReleaseRoot $ReleaseId
    $verified = Test-GateYReadonlyRelease $releaseRoot -RequirePosix
    Assert-ServiceUserCannotWrite $releaseRoot
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_INSTALLED_RELEASE_VERIFIED'
        contractState = 'INSTALLED_VERIFIED'
        releaseId = $verified.releaseId
        releaseRoot = $releaseRoot
        manifestSha256 = $verified.manifestSha256
        linkIntegrityVerified = $true
        posixVerified = $true
        serviceUserWritable = $false
    }
}

function Activate-Release
{
    $verified = Verify-Release
    $releaseRoot = [string]$verified.releaseRoot
    $previousTarget = $null
    if (Test-Path -LiteralPath $script:CurrentPath)
    {
        $currentItem = Get-Item -LiteralPath $script:CurrentPath -Force
        if ([string]$currentItem.LinkType -cne 'SymbolicLink')
        {
            throw 'BLOCKED / CURRENT_POINTER_CONTRACT_INVALID'
        }
        $previousTarget = ((Invoke-Native '/usr/bin/readlink' @('--', $script:CurrentPath)).Lines -join '').Trim()
    }
    $temporary = Join-Path $script:ResolvedInstallationRoot ('.current-' + [Guid]::NewGuid().ToString('N'))
    try
    {
        Invoke-Native '/usr/bin/ln' @('-s', '--', $releaseRoot, $temporary) | Out-Null
        Invoke-Native '/usr/bin/chown' @('-h', 'root:root', '--', $temporary) | Out-Null
        Invoke-Native '/usr/bin/mv' @('-T', '-f', '--', $temporary, $script:CurrentPath) | Out-Null
    }
    finally
    {
        if (Test-Path -LiteralPath $temporary)
        {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
    $target = ((Invoke-Native '/usr/bin/readlink' @('--', $script:CurrentPath)).Lines -join '').Trim()
    $metadata = ((Invoke-Native '/usr/bin/stat' @('--format=%F|%U', '--', $script:CurrentPath)).Lines -join '').Trim()
    if ($target -cne $releaseRoot -or $metadata -cne 'symbolic link|root')
    {
        throw 'FAIL / CURRENT_POINTER_POST_ACTIVATION_INVALID'
    }
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_CURRENT_ATOMICALLY_ACTIVATED'
        contractState = 'INSTALLED_VERIFIED'
        releaseId = $verified.releaseId
        currentPointer = $script:CurrentPath
        previousTarget = $previousTarget
        currentTarget = $target
        atomicReplace = $true
        previousReleasePreserved = $null -eq $previousTarget -or (Test-Path -LiteralPath $previousTarget)
        healthRequiredForAcceptance = $true
    }
}

try
{
    Assert-RootLinux
    Assert-InstallationRoot
    $result = switch ($Action)
    {
        'install' { Install-Release }
        'verify' { Verify-Release }
        'activate' { Activate-Release }
    }
    $result | ConvertTo-Json -Depth 8
}
catch
{
    $decision = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$') {
        $_.Exception.Message
    } else {
        'FAIL / GATEY_READONLY_RELEASE_INSTALL_INTERNAL_ERROR'
    }
    [pscustomobject]@{ decision = $decision; contractState = 'BLOCKED' } | ConvertTo-Json
    exit 2
}
