[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$gateyRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repo = (Resolve-Path (Join-Path $gateyRoot '../..')).Path
$contract = Join-Path $gateyRoot 'gatey-readonly-release-contract.psm1'
$installer = Join-Path $gateyRoot 'install-gatey-readonly-release.ps1'
$deployment = Join-Path $gateyRoot 'invoke-gatey-readonly-deployment-contract.ps1'
$profile = Join-Path $repo 'backend/nq-app/src/main/resources/application-gatey-readonly-qualification.yml'
$engine = (Get-Process -Id $PID).Path
$serviceUser = 'nq-gatey-readonly-test'
$utf8 = [Text.UTF8Encoding]::new($false)
$cases = [Collections.Generic.List[string]]::new()

Import-Module $contract -Force -DisableNameChecking

function Assert-Condition([bool]$Value, [string]$Message)
{
    if (-not $Value) { throw $Message }
}

function Complete-Case([string]$Name)
{
    $cases.Add($Name)
}

function Write-Text([string]$Path, [string]$Value)
{
    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [IO.File]::WriteAllText($Path, $Value, $utf8)
}

function Add-Artifact([string]$Root, [string]$Relative, [string]$Source, [string]$Mode, [string]$Role)
{
    $target = Join-Path $Root $Relative
    [IO.Directory]::CreateDirectory((Split-Path -Parent $target)) | Out-Null
    [IO.File]::Copy($Source, $target, $false)
    return [pscustomobject][ordered]@{
        relativePath = $Relative
        size = (Get-Item -LiteralPath $target).Length
        sha256 = Get-GateYReadonlySha256File $target
        mode = $Mode
        role = $Role
    }
}

function New-SourceRelease([string]$Root, [string]$ReleaseId, [string]$MigrationRoot, [string]$JarText)
{
    $jarSource = Join-Path $Root 'source.jar'
    Write-Text $jarSource $JarText
    $artifacts = @(
        Add-Artifact $Root 'app/nq-app.jar' $jarSource '0644' 'application'
        Add-Artifact $Root 'config/application-gatey-readonly-qualification.yml' $profile '0644' 'runtime-profile'
        Add-Artifact $Root 'bin/gatey-readonly-release-contract.psm1' $contract '0644' 'release-contract'
        Add-Artifact $Root 'bin/invoke-gatey-readonly-deployment-contract.ps1' $deployment '0755' 'deployment-contract'
        Add-Artifact $Root 'bin/install-gatey-readonly-release.ps1' $installer '0755' 'release-installer'
    )
    Remove-Item -LiteralPath $jarSource -Force
    $manifest = New-GateYReadonlyReleaseManifest $ReleaseId '2026-08-19T00:00:00Z' $artifacts $MigrationRoot
    Write-GateYReadonlyCanonicalManifest (Join-Path $Root 'release-manifest.json') $manifest
    return $manifest
}

function Invoke-Installer([string[]]$Arguments, [int]$ExpectedExit)
{
    $output = @(& $engine -NoProfile -File $installer @Arguments 2>&1)
    $exitCode = [int]$LASTEXITCODE
    if ($exitCode -ne $ExpectedExit)
    {
        throw "INSTALLER_EXIT_INVALID expected=$ExpectedExit actual=$exitCode output=$($output -join ' ')"
    }
    return (($output -join [Environment]::NewLine) | ConvertFrom-Json)
}

function Assert-BlockedDecision($Result, [string]$Decision)
{
    Assert-Condition ([string]$Result.decision -ceq $Decision) "DECISION_INVALID:$($Result.decision)"
}

$linux = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
if ($null -eq $linux -or -not [bool]$linux.Value -or [Environment]::UserName -cne 'root')
{
    throw 'BLOCKED / DISPOSABLE_ROOT_LINUX_REQUIRED'
}

$tempRoot = Join-Path '/tmp' ('nq-gatey-linux-' + [Guid]::NewGuid().ToString('N'))
try
{
    & /usr/bin/id -u $serviceUser 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0)
    {
        & /usr/sbin/useradd --system --no-create-home --shell /usr/sbin/nologin $serviceUser
        if ($LASTEXITCODE -ne 0) { throw 'SERVICE_USER_FIXTURE_FAILED' }
    }

    [IO.Directory]::CreateDirectory($tempRoot) | Out-Null
    $migrations = Join-Path $tempRoot 'migrations'
    Write-Text (Join-Path $migrations 'V1__first.sql') ('SELECT 1;' + [char]10)
    Write-Text (Join-Path $migrations 'V2__second.sql') ('SELECT 2;' + [char]10)
    $installRoot = Join-Path $tempRoot 'opt'

    $releaseIdA = '1111111111111111111111111111111111111111'
    $sourceA = Join-Path $tempRoot 'source-a'
    $manifestA = New-SourceRelease $sourceA $releaseIdA $migrations 'source-a-artifact'
    $sourceVerified = Test-GateYReadonlyRelease $sourceA
    Assert-Condition ($sourceVerified.linkIntegrityVerified) 'SOURCE_LINK_INTEGRITY_NOT_VERIFIED'
    Complete-Case 'source-regular-files-verified'

    $installA = Invoke-Installer @(
        '-Action', 'install', '-SourceRoot', $sourceA, '-ReleaseId', $releaseIdA,
        '-InstallationRoot', $installRoot, '-ServiceUser', $serviceUser, '-DisposableTestRoot'
    ) 0
    Assert-Condition ([string]$installA.contractState -ceq 'INSTALLED_VERIFIED') 'INSTALL_A_NOT_VERIFIED'
    $installedA = [string]$installA.releaseRoot
    Complete-Case 'root-owned-posix-install-pass'
    Assert-Condition (-not [bool]$installA.serviceUserWritable) 'SERVICE_USER_WRITE_DENIAL_NOT_PROVEN'
    Complete-Case 'service-user-write-denied'

    $noOverwrite = Invoke-Installer @(
        '-Action', 'install', '-SourceRoot', $sourceA, '-ReleaseId', $releaseIdA,
        '-InstallationRoot', $installRoot, '-ServiceUser', $serviceUser, '-DisposableTestRoot'
    ) 2
    Assert-BlockedDecision $noOverwrite 'BLOCKED / RELEASE_ALREADY_EXISTS'
    Complete-Case 'existing-release-no-overwrite'

    $sourceHashBefore = Get-GateYReadonlySha256File (Join-Path $sourceA 'app/nq-app.jar')
    $installedHashBefore = Get-GateYReadonlySha256File (Join-Path $installedA 'app/nq-app.jar')
    [IO.File]::WriteAllText((Join-Path $sourceA 'app/nq-app.jar'), 'source-mutated-after-install', $utf8)
    $installedHashAfter = Get-GateYReadonlySha256File (Join-Path $installedA 'app/nq-app.jar')
    $metadata = (& /usr/bin/stat --format='%h|%d|%i' -- (Join-Path $installedA 'app/nq-app.jar')).Trim().Split('|')
    $sourceMetadata = (& /usr/bin/stat --format='%d|%i' -- (Join-Path $sourceA 'app/nq-app.jar')).Trim()
    Assert-Condition ($sourceHashBefore -cne (Get-GateYReadonlySha256File (Join-Path $sourceA 'app/nq-app.jar')) -and
            $installedHashBefore -ceq $installedHashAfter -and [long]$metadata[0] -eq 1 -and
            ($metadata[1] + '|' + $metadata[2]) -cne $sourceMetadata) 'INSTALLED_ARTIFACT_NOT_INDEPENDENT'
    Complete-Case 'source-mutation-does-not-change-install'

    $activationA = Invoke-Installer @(
        '-Action', 'activate', '-ReleaseId', $releaseIdA,
        '-InstallationRoot', $installRoot, '-ServiceUser', $serviceUser, '-DisposableTestRoot'
    ) 0
    Assert-Condition ([string]$activationA.currentTarget -ceq $installedA -and [bool]$activationA.atomicReplace) 'ACTIVATION_A_INVALID'
    Complete-Case 'atomic-current-initial-activation'

    $releaseIdB = '3333333333333333333333333333333333333333'
    $sourceB = Join-Path $tempRoot 'source-b'
    $null = New-SourceRelease $sourceB $releaseIdB $migrations 'source-b-artifact'
    $null = Test-GateYReadonlyRelease $sourceB
    $outside = Join-Path $tempRoot 'outside-b.jar'
    Write-Text $outside 'source-b-artifact'
    Remove-Item -LiteralPath (Join-Path $sourceB 'app/nq-app.jar') -Force
    & /usr/bin/ln -- $outside (Join-Path $sourceB 'app/nq-app.jar')
    $hardlinkInstall = Invoke-Installer @(
        '-Action', 'install', '-SourceRoot', $sourceB, '-ReleaseId', $releaseIdB,
        '-InstallationRoot', $installRoot, '-ServiceUser', $serviceUser, '-DisposableTestRoot'
    ) 2
    Assert-BlockedDecision $hardlinkInstall 'BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION'
    $currentAfterFailedInstall = (& /usr/bin/readlink -- (Join-Path $installRoot 'current')).Trim()
    Assert-Condition ($currentAfterFailedInstall -ceq $installedA) 'FAILED_INSTALL_MOVED_CURRENT'
    Complete-Case 'post-verification-hardlink-swap-rejected'
    Complete-Case 'failed-install-preserves-current'

    Remove-Item -LiteralPath $sourceB -Recurse -Force
    $null = New-SourceRelease $sourceB $releaseIdB $migrations 'source-b-artifact'
    $installB = Invoke-Installer @(
        '-Action', 'install', '-SourceRoot', $sourceB, '-ReleaseId', $releaseIdB,
        '-InstallationRoot', $installRoot, '-ServiceUser', $serviceUser, '-DisposableTestRoot'
    ) 0
    $activationB = Invoke-Installer @(
        '-Action', 'activate', '-ReleaseId', $releaseIdB,
        '-InstallationRoot', $installRoot, '-ServiceUser', $serviceUser, '-DisposableTestRoot'
    ) 0
    Assert-Condition ([string]$activationB.previousTarget -ceq $installedA -and
            [string]$activationB.currentTarget -ceq [string]$installB.releaseRoot -and
            (Test-Path -LiteralPath $installedA)) 'ATOMIC_SWITCH_OR_PREVIOUS_RELEASE_INVALID'
    Complete-Case 'atomic-current-switch-once'
    Complete-Case 'previous-release-preserved'

    $installedBJar = Join-Path ([string]$installB.releaseRoot) 'app/nq-app.jar'
    & /usr/bin/chmod 0666 -- $installedBJar
    $wrongMode = Invoke-Installer @(
        '-Action', 'verify', '-ReleaseId', $releaseIdB,
        '-InstallationRoot', $installRoot, '-ServiceUser', $serviceUser, '-DisposableTestRoot'
    ) 2
    Assert-BlockedDecision $wrongMode 'BLOCKED / RELEASE_POSIX_CONTRACT_VIOLATION'
    & /usr/bin/chmod 0644 -- $installedBJar
    Complete-Case 'wrong-mode-and-world-write-rejected'

    & /usr/bin/chown ($serviceUser + ':' + $serviceUser) -- $installedBJar
    $wrongOwner = Invoke-Installer @(
        '-Action', 'verify', '-ReleaseId', $releaseIdB,
        '-InstallationRoot', $installRoot, '-ServiceUser', $serviceUser, '-DisposableTestRoot'
    ) 2
    Assert-BlockedDecision $wrongOwner 'BLOCKED / RELEASE_POSIX_CONTRACT_VIOLATION'
    & /usr/bin/chown root:root -- $installedBJar
    Complete-Case 'wrong-owner-rejected'

    $outsideInstalled = Join-Path $tempRoot 'outside-installed.jar'
    [IO.File]::Copy($installedBJar, $outsideInstalled, $false)
    Remove-Item -LiteralPath $installedBJar -Force
    & /usr/bin/ln -- $outsideInstalled $installedBJar
    $hardlinkInstalled = Invoke-Installer @(
        '-Action', 'verify', '-ReleaseId', $releaseIdB,
        '-InstallationRoot', $installRoot, '-ServiceUser', $serviceUser, '-DisposableTestRoot'
    ) 2
    Assert-BlockedDecision $hardlinkInstalled 'BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION'
    Complete-Case 'installed-hardlink-rejected'

    Assert-Condition ($cases.Count -eq 13) "CASE_COUNT_INVALID:$($cases.Count)"
    [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_LINUX_INSTALLATION_REGRESSION'
        cases = $cases.Count
        serviceUser = $serviceUser
        productionMutation = $false
        disposableRoot = $tempRoot
        results = @($cases)
    } | ConvertTo-Json -Depth 6
}
catch
{
    [pscustomobject][ordered]@{
        decision = 'FAIL / GATEY_READONLY_LINUX_INSTALLATION_REGRESSION'
        casesPassed = $cases.Count
        detail = $_.Exception.Message
    } | ConvertTo-Json -Depth 4
    exit 2
}
finally
{
    if (Test-Path -LiteralPath $tempRoot)
    {
        $resolved = [IO.Path]::GetFullPath($tempRoot)
        if (-not $resolved.StartsWith('/tmp/nq-gatey-linux-', [StringComparison]::Ordinal))
        {
            throw 'TEMP_CLEANUP_PATH_INVALID'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
