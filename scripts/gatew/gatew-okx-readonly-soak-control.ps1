[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet(
        'install', 'prepare', 'start', 'status', 'verify', 'stop', 'offline-fail',
        'unit-preflight', 'record-exit', 'self-test'
    )]
    [string]$Action,

    [string]$RunId,

    [ValidateSet('REAL', 'OFFLINE_ACCEPTANCE')]
    [string]$RunMode = 'OFFLINE_ACCEPTANCE',

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

$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)
$script:RunIdPattern = '^gatew-soak-[0-9]{8}T[0-9]{6}Z-[a-f0-9]{8}$'
$script:SafeCodePattern = '^[A-Z][A-Z0-9_]{1,95}$'
$script:StateRoot = '/var/lib/nexus-quant/gatew-soak'
$script:RuntimeRoot = '/run/nexus-quant/gatew-soak'
$script:LogRoot = '/var/log/nexus-quant/gatew-soak'
$script:ConfigRoot = '/etc/nexus-quant/gatew-soak'
$script:CredentialRoot = '/etc/nexus-quant/gatew-soak/credentials'
$script:LibexecRoot = '/usr/local/libexec/nexus-quant'
$script:LauncherRoot = '/opt/nexus-quant/gatew-soak/app/launcher'
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
$script:SystemctlPath = '/usr/bin/systemctl'
$script:SystemdAnalyzePath = '/usr/bin/systemd-analyze'
$script:SystemdCredsPath = '/usr/bin/systemd-creds'
$script:MavenPath = '/usr/bin/mvn'
$script:InstallPath = '/usr/bin/install'
$script:ChownPath = '/usr/bin/chown'
$script:ChmodPath = '/usr/bin/chmod'
$script:StatPath = '/usr/bin/stat'
$script:ReadlinkPath = '/usr/bin/readlink'
$script:LnPath = '/usr/bin/ln'

$configuredRepoRoot = [Environment]::GetEnvironmentVariable('NQ_GATEW_REPO_ROOT', 'Process')
if ([string]::IsNullOrWhiteSpace($configuredRepoRoot)) {
    $script:RepoRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
}
else {
    $script:RepoRoot = [IO.Path]::GetFullPath($configuredRepoRoot)
}

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

function Test-LinuxPlatform {
    $platform = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    return $null -ne $platform -and [bool]$platform.Value
}

function Get-UtcNow {
    return [DateTimeOffset]::UtcNow
}

function Assert-RunId {
    param([Parameter(Mandatory = $true)][string]$Value)

    if ($Value -cnotmatch $script:RunIdPattern) {
        throw 'BLOCKED / RUN_ID_INVALID'
    }
}

function New-RunId {
    return 'gatew-soak-{0}-{1}' -f `
        (Get-UtcNow).ToString('yyyyMMddTHHmmssZ'), `
        ([Guid]::NewGuid().ToString('N').Substring(0, 8))
}

function Assert-RootLinux {
    if (-not (Test-LinuxPlatform) -or [Environment]::UserName -ne 'root') {
        throw 'BLOCKED / ROOT_CONTROL_REQUIRED'
    }
}

function ConvertTo-TrimmedOutput {
    param([AllowNull()][object[]]$Value)

    return (($Value -join [Environment]::NewLine).Trim())
}

function Invoke-Native {
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [AllowEmptyCollection()][string[]]$Arguments = @(),
        [switch]$AllowFailure
    )

    if (-not (Test-Path -LiteralPath $FilePath -PathType Leaf)) {
        if ($AllowFailure) { return [pscustomobject]@{ ExitCode = 127; Lines = @() } }
        throw 'BLOCKED / REQUIRED_NATIVE_TOOL_MISSING'
    }
    $lines = @(& $FilePath @Arguments 2>$null)
    $exitCodeValue = [int]$LASTEXITCODE
    if (-not $AllowFailure -and $exitCodeValue -ne 0) {
        throw 'FAIL / NATIVE_COMMAND_FAILED'
    }
    return [pscustomobject]@{ ExitCode = $exitCodeValue; Lines = @($lines) }
}

function Get-Sha256File {
    param([Parameter(Mandatory = $true)][string]$Path)

    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-LauncherBundleRoot {
    param([Parameter(Mandatory = $true)][string]$Commit)

    if ($Commit -cnotmatch '^[a-f0-9]{40}$') { throw 'BLOCKED / HARNESS_COMMIT_UNRESOLVED' }
    return "$($script:LauncherRoot)/$Commit"
}

function Assert-LauncherBundle {
    param(
        [Parameter(Mandatory = $true)][string]$Commit,
        [string]$ExpectedManifestSha256
    )

    $bundleRoot = Get-LauncherBundleRoot $Commit
    $manifestPath = Join-Path $bundleRoot 'manifest.json'
    Assert-PathBelowRoot $script:LauncherRoot $bundleRoot
    Assert-NoSymlink $script:LauncherRoot
    Assert-NoSymlink $bundleRoot
    Assert-NoSymlink $manifestPath
    if (Test-LinuxPlatform) {
        Assert-PosixContract $bundleRoot 'directory' '555' 'root' 'root'
        Assert-PosixContract $manifestPath 'regular file' '444' 'root' 'root'
    }
    $manifest = Read-JsonFile $manifestPath
    $fields = @($manifest.PSObject.Properties.Name)
    if (($fields -join '|') -ne 'schemaVersion|harnessCommit|mainClasses|artifacts' -or
        [string]$manifest.schemaVersion -ne 'gatew-soak-launcher-bundle-v1' -or
        [string]$manifest.harnessCommit -cne $Commit -or
        (@($manifest.mainClasses) -join '|') -cne (
            'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakCycleTest|' +
            'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakFailCloseTest'
        ) -or @($manifest.artifacts).Count -lt 3) {
        throw 'BLOCKED / LAUNCHER_BUNDLE_MANIFEST_INVALID'
    }
    $seen = @{}
    foreach ($artifact in @($manifest.artifacts)) {
        if ((@($artifact.PSObject.Properties.Name) -join '|') -ne 'relativePath|sha256' -or
            [string]$artifact.relativePath -cnotmatch '^(test-classes/[A-Za-z0-9_$/.-]+[.]class|lib/[0-9]{4}-[a-f0-9]{16}-[A-Za-z0-9_.-]+[.]jar)$' -or
            [string]$artifact.sha256 -cnotmatch '^[a-f0-9]{64}$' -or
            $seen.ContainsKey([string]$artifact.relativePath)) {
            throw 'BLOCKED / LAUNCHER_BUNDLE_MANIFEST_INVALID'
        }
        $seen[[string]$artifact.relativePath] = $true
        $path = Join-Path $bundleRoot ([string]$artifact.relativePath)
        Assert-PathBelowRoot $bundleRoot $path
        Assert-NoSymlink $path
        if (-not (Test-Path -LiteralPath $path -PathType Leaf) -or
            (Get-Sha256File $path) -cne [string]$artifact.sha256) {
            throw 'BLOCKED / LAUNCHER_BUNDLE_HASH_MISMATCH'
        }
        if (Test-LinuxPlatform) { Assert-PosixContract $path 'regular file' '444' 'root' 'root' }
    }
    foreach ($requiredClass in @(
        'test-classes/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakCycleTest.class',
        'test-classes/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakFailCloseTest.class'
    )) {
        if (-not $seen.ContainsKey($requiredClass)) {
            throw 'BLOCKED / LAUNCHER_BUNDLE_MANIFEST_INVALID'
        }
    }
    $actualArtifacts = @()
    foreach ($item in @(Get-ChildItem -LiteralPath $bundleRoot -Force -Recurse)) {
        Assert-NoSymlink $item.FullName
        if ($item.PSIsContainer) {
            if (Test-LinuxPlatform) { Assert-PosixContract $item.FullName 'directory' '555' 'root' 'root' }
            continue
        }
        if ($item.FullName -ceq $manifestPath) { continue }
        $relative = $item.FullName.Substring($bundleRoot.Length).TrimStart('/', '\').Replace('\', '/')
        $actualArtifacts += $relative
    }
    $actualArtifacts = @($actualArtifacts | Sort-Object -Unique)
    $declaredArtifacts = @($seen.Keys | Sort-Object -Unique)
    if (($actualArtifacts -join '|') -cne ($declaredArtifacts -join '|')) {
        throw 'BLOCKED / LAUNCHER_BUNDLE_MANIFEST_INVALID'
    }
    $manifestSha256 = Get-Sha256File $manifestPath
    if (-not [string]::IsNullOrWhiteSpace($ExpectedManifestSha256) -and
        $manifestSha256 -cne $ExpectedManifestSha256) {
        throw 'BLOCKED / LAUNCHER_BUNDLE_HASH_MISMATCH'
    }
    return [pscustomobject]@{
        Root = $bundleRoot
        ManifestPath = $manifestPath
        ManifestSha256 = $manifestSha256
        ArtifactCount = @($manifest.artifacts).Count
    }
}

function Build-LauncherBundle {
    param([Parameter(Mandatory = $true)][string]$Commit)

    Ensure-Directory $script:LauncherRoot 'root:root' '755'
    $bundleRoot = Get-LauncherBundleRoot $Commit
    if (Test-Path -LiteralPath $bundleRoot) {
        return Assert-LauncherBundle $Commit
    }

    $backendPom = Join-Path $script:RepoRoot 'backend/pom.xml'
    $build = Invoke-Native $script:MavenPath @(
        '--offline', '--quiet', '-f', $backendPom, '-pl', 'nq-app', '-am', '-DskipTests', 'install'
    ) -AllowFailure
    if ($build.ExitCode -ne 0) { throw 'FAIL / LAUNCHER_BUNDLE_BUILD_FAILED' }

    $classpathPath = Join-Path $script:RepoRoot 'backend/nq-app/target/gatew-formal-launcher-classpath.txt'
    if (Test-Path -LiteralPath $classpathPath) { Remove-Item -LiteralPath $classpathPath -Force }
    $classpath = Invoke-Native $script:MavenPath @(
        '--offline', '--quiet', '-f', $backendPom, '-pl', 'nq-app',
        '-DincludeScope=test', '-Dmdep.outputAbsoluteArtifactFilename=true',
        "-Dmdep.outputFile=$classpathPath", 'dependency:build-classpath'
    ) -AllowFailure
    if ($classpath.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $classpathPath -PathType Leaf)) {
        throw 'FAIL / LAUNCHER_CLASSPATH_BUILD_FAILED'
    }

    $stageRoot = "$($script:LauncherRoot)/.build-$Commit-$PID"
    Assert-PathBelowRoot $script:LauncherRoot $stageRoot
    if (Test-Path -LiteralPath $stageRoot) { throw 'BLOCKED / LAUNCHER_BUILD_PATH_EXISTS' }
    try {
        Ensure-Directory $stageRoot 'root:root' '700'
        $classRoot = Join-Path $stageRoot 'test-classes/com/guidinglight/nexusquant/app/gatew'
        $libRoot = Join-Path $stageRoot 'lib'
        Ensure-Directory $classRoot 'root:root' '700'
        Ensure-Directory $libRoot 'root:root' '700'
        $sourceClassRoot = Join-Path $script:RepoRoot `
            'backend/nq-app/target/test-classes/com/guidinglight/nexusquant/app/gatew'
        $classFiles = @(
            Get-ChildItem -LiteralPath $sourceClassRoot -File |
                Where-Object {
                    $_.Name -like 'GateWOkxReadonlySoakCycleTest*.class' -or
                    $_.Name -like 'GateWOkxReadonlySoakFailCloseTest*.class'
                } |
                Sort-Object Name
        )
        if ($classFiles.Count -lt 4 -or
            @($classFiles | Where-Object { $_.Name -eq 'GateWOkxReadonlySoakCycleTest.class' }).Count -ne 1 -or
            @($classFiles | Where-Object { $_.Name -eq 'GateWOkxReadonlySoakFailCloseTest.class' }).Count -ne 1) {
            throw 'FAIL / LAUNCHER_TEST_CLASSES_MISSING'
        }
        $artifacts = @()
        foreach ($file in $classFiles) {
            Assert-NoSymlink $file.FullName
            $destination = Join-Path $classRoot $file.Name
            Invoke-Native $script:InstallPath @(
                '-o', 'root', '-g', 'root', '-m', '0444', '--', $file.FullName, $destination
            ) | Out-Null
            $artifacts += [pscustomobject][ordered]@{
                relativePath = "test-classes/com/guidinglight/nexusquant/app/gatew/$($file.Name)"
                sha256 = Get-Sha256File $destination
            }
        }

        $classpathEntries = @(
            ((Get-Content -LiteralPath $classpathPath -Raw).Trim() -split
                [regex]::Escape([IO.Path]::PathSeparator)) |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
                Sort-Object -Unique
        )
        if ($classpathEntries.Count -lt 1) { throw 'FAIL / LAUNCHER_CLASSPATH_EMPTY' }
        $index = 0
        foreach ($entry in $classpathEntries) {
            $source = [IO.Path]::GetFullPath([string]$entry)
            if ([IO.Path]::GetExtension($source) -cne '.jar' -or
                -not (Test-Path -LiteralPath $source -PathType Leaf)) {
                throw 'FAIL / LAUNCHER_CLASSPATH_ARTIFACT_INVALID'
            }
            Assert-NoSymlink $source
            $sha256 = Get-Sha256File $source
            $name = '{0:D4}-{1}-{2}' -f $index, $sha256.Substring(0, 16), ([IO.Path]::GetFileName($source))
            if ($name -cnotmatch '^[0-9]{4}-[a-f0-9]{16}-[A-Za-z0-9_.-]+[.]jar$') {
                throw 'FAIL / LAUNCHER_CLASSPATH_ARTIFACT_INVALID'
            }
            $destination = Join-Path $libRoot $name
            Invoke-Native $script:InstallPath @(
                '-o', 'root', '-g', 'root', '-m', '0444', '--', $source, $destination
            ) | Out-Null
            $artifacts += [pscustomobject][ordered]@{
                relativePath = "lib/$name"
                sha256 = $sha256
            }
            $index++
        }
        Write-JsonCreateOnce (Join-Path $stageRoot 'manifest.json') ([ordered]@{
            schemaVersion = 'gatew-soak-launcher-bundle-v1'
            harnessCommit = $Commit
            mainClasses = @(
                'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakCycleTest',
                'com.guidinglight.nexusquant.app.gatew.GateWOkxReadonlySoakFailCloseTest'
            )
            artifacts = @($artifacts)
        })
        Set-OwnerMode (Join-Path $stageRoot 'manifest.json') 'root:root' '444'
        foreach ($directory in @(Get-ChildItem -LiteralPath $stageRoot -Directory -Recurse | Sort-Object FullName -Descending)) {
            Set-OwnerMode $directory.FullName 'root:root' '555'
        }
        Set-OwnerMode $stageRoot 'root:root' '555'
        Move-Item -LiteralPath $stageRoot -Destination $bundleRoot
    }
    finally {
        if (Test-Path -LiteralPath $stageRoot) {
            Assert-PathBelowRoot $script:LauncherRoot $stageRoot
            Remove-Item -LiteralPath $stageRoot -Recurse -Force
        }
    }
    Assert-FixedDetachedWorktree $Commit | Out-Null
    return Assert-LauncherBundle $Commit
}

function ConvertTo-CompactJson {
    param([Parameter(Mandatory = $true)]$Value)

    return ($Value | ConvertTo-Json -Compress -Depth 16)
}

function Read-JsonFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw 'BLOCKED / REQUIRED_CONTROL_FILE_MISSING'
    }
    Assert-PathComponentsNoSymlink $Path
    return (Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json)
}

function Write-BytesFlushed {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][byte[]]$Bytes
    )

    $stream = [IO.FileStream]::new(
        $Path,
        [IO.FileMode]::CreateNew,
        [IO.FileAccess]::Write,
        [IO.FileShare]::None,
        4096,
        [IO.FileOptions]::WriteThrough
    )
    try {
        $stream.Write($Bytes, 0, $Bytes.Length)
        $stream.Flush($true)
    }
    finally {
        $stream.Dispose()
    }
}

function Write-TextCreateOnce {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Text
    )

    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        throw 'BLOCKED / CONTROL_DIRECTORY_INVALID'
    }
    Assert-PathComponentsNoSymlink $parent
    if (Test-Path -LiteralPath $Path) {
        throw 'BLOCKED / IMMUTABLE_CONTROL_EXISTS'
    }
    $temporary = Join-Path $parent ('.create-' + [Guid]::NewGuid().ToString('N'))
    $bytes = $script:Utf8NoBom.GetBytes($Text)
    try {
        Write-BytesFlushed $temporary $bytes
        if (Test-LinuxPlatform) {
            Invoke-Native $script:ChmodPath @('600', '--', $temporary) | Out-Null
            $linked = Invoke-Native $script:LnPath @('--', $temporary, $Path) -AllowFailure
            if ($linked.ExitCode -ne 0) {
                if (Test-Path -LiteralPath $Path) {
                    throw 'BLOCKED / IMMUTABLE_CONTROL_EXISTS'
                }
                throw 'FAIL / CREATE_ONCE_COMMIT_FAILED'
            }
            Remove-Item -LiteralPath $temporary -Force
        }
        else {
            Move-Item -LiteralPath $temporary -Destination $Path
        }
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
        if (Test-Path -LiteralPath $temporary) {
            Remove-Item -LiteralPath $temporary -Force
        }
    }
}

function Write-JsonCreateOnce {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value
    )

    Write-TextCreateOnce $Path (ConvertTo-CompactJson $Value)
}

function Write-TextReplaceAtomic {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$Text
    )

    $parent = Split-Path -Parent $Path
    if (-not (Test-Path -LiteralPath $parent -PathType Container)) {
        throw 'BLOCKED / CONTROL_DIRECTORY_INVALID'
    }
    Assert-PathComponentsNoSymlink $parent
    if (Test-Path -LiteralPath $Path) { Assert-NoSymlink $Path }
    $temporary = Join-Path $parent ('.replace-' + [Guid]::NewGuid().ToString('N'))
    $bytes = $script:Utf8NoBom.GetBytes($Text)
    try {
        Write-BytesFlushed $temporary $bytes
        if (Test-LinuxPlatform) { Invoke-Native $script:ChmodPath @('600', '--', $temporary) | Out-Null }
        Move-Item -LiteralPath $temporary -Destination $Path -Force
    }
    finally {
        [Array]::Clear($bytes, 0, $bytes.Length)
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}

function Write-JsonReplaceAtomic {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Value
    )

    Write-TextReplaceAtomic $Path (ConvertTo-CompactJson $Value)
}

function Get-RunRoot {
    param([Parameter(Mandatory = $true)][string]$Value)

    Assert-RunId $Value
    return "$($script:StateRoot)/$Value"
}

function Get-ControlRoot {
    param([Parameter(Mandatory = $true)][string]$Value)
    return "$(Get-RunRoot $Value)/control"
}

function Get-EvidenceRoot {
    param([Parameter(Mandatory = $true)][string]$Value)
    return "$(Get-RunRoot $Value)/evidence"
}

function Get-RuntimeRoot {
    param([Parameter(Mandatory = $true)][string]$Value)
    Assert-RunId $Value
    return "$($script:RuntimeRoot)/$Value"
}

function Get-WorkerUnitName {
    param([Parameter(Mandatory = $true)][string]$Value)
    Assert-RunId $Value
    return "nq-gatew-soak@$Value.service"
}

function Get-FailCloseUnitName {
    param([Parameter(Mandatory = $true)][string]$Value)
    Assert-RunId $Value
    return "nq-gatew-soak-failclose@$Value.service"
}

function Enter-TerminalAuthorityLock {
    param([Parameter(Mandatory = $true)][string]$Value)

    $path = "$(Get-ControlRoot $Value)/failclose.lock"
    Assert-PathComponentsNoSymlink (Split-Path -Parent $path)
    try {
        $stream = [IO.FileStream]::new(
            $path,
            [IO.FileMode]::OpenOrCreate,
            [IO.FileAccess]::ReadWrite,
            [IO.FileShare]::None,
            1,
            [IO.FileOptions]::WriteThrough
        )
        if (Test-LinuxPlatform) { Set-OwnerMode $path 'root:root' '600' }
        return $stream
    }
    catch {
        throw 'FAIL / TERMINAL_AUTHORITY_LOCK_UNAVAILABLE'
    }
}

function Assert-PathBelowRoot {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Path
    )

    $normalizedRoot = [IO.Path]::GetFullPath($Root).TrimEnd(
        [IO.Path]::DirectorySeparatorChar,
        [IO.Path]::AltDirectorySeparatorChar
    )
    $normalizedPath = [IO.Path]::GetFullPath($Path)
    $comparison = if (Test-LinuxPlatform) {
        [StringComparison]::Ordinal
    }
    else {
        [StringComparison]::OrdinalIgnoreCase
    }
    if (-not $normalizedPath.StartsWith(
        $normalizedRoot + [IO.Path]::DirectorySeparatorChar,
        $comparison
    )) {
        throw 'BLOCKED / PATH_CONTRACT_INVALID'
    }
    Assert-PathComponentsNoSymlink $normalizedRoot
    Assert-PathComponentsNoSymlink $normalizedPath -AllowMissingTail
    if (Test-Path -LiteralPath $normalizedPath) {
        $realRoot = (Get-RealPath $normalizedRoot).TrimEnd(
            [IO.Path]::DirectorySeparatorChar,
            [IO.Path]::AltDirectorySeparatorChar
        )
        $realPath = Get-RealPath $normalizedPath
        if (-not $realPath.StartsWith(
            $realRoot + [IO.Path]::DirectorySeparatorChar,
            $comparison
        )) {
            throw 'BLOCKED / PATH_CONTRACT_INVALID'
        }
    }
}

function Assert-NoSymlink {
    param([Parameter(Mandatory = $true)][string]$Path)

    if (-not (Test-Path -LiteralPath $Path)) { throw 'BLOCKED / PATH_CONTRACT_INVALID' }
    $item = Get-Item -LiteralPath $Path -Force
    if ($null -ne $item.LinkType -or $item.Attributes.ToString() -match 'ReparsePoint') {
        throw 'BLOCKED / SYMLINK_PATH_FORBIDDEN'
    }
}

function Assert-PathComponentsNoSymlink {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$AllowMissingTail
    )

    $normalized = [IO.Path]::GetFullPath($Path)
    $pathRoot = [IO.Path]::GetPathRoot($normalized)
    if ([string]::IsNullOrWhiteSpace($pathRoot)) { throw 'BLOCKED / PATH_CONTRACT_INVALID' }
    $current = $pathRoot
    if (Test-Path -LiteralPath $current) { Assert-NoSymlink $current }
    $relative = $normalized.Substring($pathRoot.Length)
    foreach ($segment in @($relative -split '[\\/]' | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })) {
        $current = Join-Path $current $segment
        if (-not (Test-Path -LiteralPath $current)) {
            if ($AllowMissingTail) { return }
            throw 'BLOCKED / PATH_CONTRACT_INVALID'
        }
        Assert-NoSymlink $current
    }
}

function Get-RealPath {
    param([Parameter(Mandatory = $true)][string]$Path)

    Assert-PathComponentsNoSymlink $Path
    if (Test-LinuxPlatform) {
        $resolved = Invoke-Native $script:ReadlinkPath @('-f', '--', $Path) -AllowFailure
        $value = ConvertTo-TrimmedOutput $resolved.Lines
        if ($resolved.ExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($value)) {
            throw 'BLOCKED / PATH_CONTRACT_INVALID'
        }
        return [IO.Path]::GetFullPath($value)
    }
    return [IO.Path]::GetFullPath((Get-Item -LiteralPath $Path -Force).FullName)
}

function Set-OwnerMode {
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)][string]$OwnerGroup,
        [Parameter(Mandatory = $true)][string]$Mode
    )

    Assert-NoSymlink $Path
    Invoke-Native $script:ChownPath @('--', $OwnerGroup, $Path) | Out-Null
    Invoke-Native $script:ChmodPath @($Mode, '--', $Path) | Out-Null
}

function Get-PosixMetadata {
    param([Parameter(Mandatory = $true)][string]$Path)

    Assert-NoSymlink $Path
    $result = Invoke-Native $script:StatPath @('-c', '%F|%a|%U|%G', '--', $Path)
    $parts = (ConvertTo-TrimmedOutput $result.Lines).Split('|')
    if ($parts.Count -ne 4) { throw 'BLOCKED / PATH_CONTRACT_INVALID' }
    return [pscustomobject]@{ Type = $parts[0]; Mode = $parts[1]; Owner = $parts[2]; Group = $parts[3] }
}

function Assert-PosixContract {
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
        $metadata.Group -ne $ExpectedGroup) {
        throw 'BLOCKED / PATH_OWNERSHIP_CONTRACT_INVALID'
    }
}

function Ensure-Directory {
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

function Assert-RunDirectoryContract {
    param([Parameter(Mandatory = $true)][string]$Value)

    $runRoot = Get-RunRoot $Value
    $controlRoot = Get-ControlRoot $Value
    $evidenceRoot = Get-EvidenceRoot $Value
    Assert-PathBelowRoot $script:StateRoot $runRoot
    Assert-PathBelowRoot $runRoot $controlRoot
    Assert-PathBelowRoot $runRoot $evidenceRoot
    Assert-PosixContract $script:StateRoot 'directory' '710' 'root' $script:LinuxRuntimeGroup
    Assert-PosixContract $runRoot 'directory' '710' 'root' $script:LinuxRuntimeGroup
    Assert-PosixContract $controlRoot 'directory' '700' 'root' 'root'
    Assert-PosixContract $evidenceRoot 'directory' '700' $script:LinuxRuntimeUser $script:LinuxRuntimeGroup
}

function Assert-LiteralValue {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    if ($Value.IndexOf([char]0) -ge 0 -or
        $Value -match "[`r`n]" -or
        $Value -match '\$\{?[A-Za-z_][A-Za-z0-9_]*\}?' -or
        $Value -match '%[A-Za-z_][A-Za-z0-9_]*%' -or
        $Value -match '\$\(' -or
        $Value -match '`') {
        throw 'BLOCKED / CONFIG_VALUE_NOT_LITERAL'
    }
}

function ConvertTo-SystemdLiteral {
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)

    Assert-LiteralValue $Value
    return '"' + $Value.Replace('\', '\\').Replace('"', '\"') + '"'
}

function New-EnvironmentFileContent {
    param([Parameter(Mandatory = $true)][hashtable]$Values)

    $lines = foreach ($name in @($Values.Keys | Sort-Object)) {
        if ($name -notmatch '^[A-Z][A-Z0-9_]{1,95}$') { throw 'BLOCKED / CONFIG_KEY_INVALID' }
        "$name=$(ConvertTo-SystemdLiteral ([string]$Values[$name]))"
    }
    return ($lines -join "`n") + "`n"
}

function Get-LifecyclePath {
    param([Parameter(Mandatory = $true)][string]$Value)
    return "$(Get-ControlRoot $Value)/lifecycle.json"
}

function Set-LifecycleState {
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][string]$NextState,
        [Parameter(Mandatory = $true)][string]$ReasonCode
    )

    Assert-RunId $Value
    if ($ReasonCode -notmatch $script:SafeCodePattern) { throw 'BLOCKED / REASON_CODE_INVALID' }
    $path = Get-LifecyclePath $Value
    $current = Read-JsonFile $path
    $currentState = [string]$current.state
    if (-not $script:AllowedTransitions.ContainsKey($currentState) -or
        $script:AllowedTransitions[$currentState] -notcontains $NextState) {
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
    if (Test-LinuxPlatform) { Set-OwnerMode $path 'root:root' '600' }
    return Read-JsonFile $path
}

function Get-HeadCommit {
    $result = @(& git -C $script:RepoRoot rev-parse HEAD 2>$null)
    if ($LASTEXITCODE -ne 0) { throw 'BLOCKED / HARNESS_COMMIT_UNRESOLVED' }
    $value = ConvertTo-TrimmedOutput $result
    if ($value -notmatch '^[a-f0-9]{40}$') { throw 'BLOCKED / HARNESS_COMMIT_UNRESOLVED' }
    return $value
}

function Assert-FixedDetachedWorktree {
    param([string]$Commit)

    $status = @(& git -C $script:RepoRoot status --porcelain --untracked-files=all 2>$null)
    if ($LASTEXITCODE -ne 0 -or $status.Count -ne 0) { throw 'BLOCKED / HARNESS_WORKTREE_NOT_CLEAN' }
    $branch = ConvertTo-TrimmedOutput @(& git -C $script:RepoRoot branch --show-current 2>$null)
    if ($LASTEXITCODE -ne 0 -or -not [string]::IsNullOrWhiteSpace($branch)) {
        throw 'BLOCKED / FIXED_COMMIT_WORKTREE_REQUIRED'
    }
    $head = Get-HeadCommit
    if (-not [string]::IsNullOrWhiteSpace($Commit) -and $head -ne $Commit.ToLowerInvariant()) {
        throw 'BLOCKED / HARNESS_COMMIT_CHANGED'
    }
    return $head
}

function Assert-ExactHeadCi {
    param(
        [Parameter(Mandatory = $true)][string]$CiRun,
        [Parameter(Mandatory = $true)][string]$Commit
    )

    if ($CiRun -notmatch '^[0-9]+$') { throw 'BLOCKED / EXACT_HEAD_CI_REQUIRED' }
    $json = @(& gh run view $CiRun --json status,conclusion,headSha,jobs 2>$null)
    if ($LASTEXITCODE -ne 0 -or $json.Count -eq 0) { throw 'BLOCKED / EXACT_HEAD_CI_NOT_VERIFIABLE' }
    $ci = ($json -join "`n") | ConvertFrom-Json
    $bad = @($ci.jobs | Where-Object { $_.status -ne 'completed' -or $_.conclusion -ne 'success' })
    if ($ci.status -ne 'completed' -or $ci.conclusion -ne 'success' -or
        [string]$ci.headSha -ne $Commit -or @($ci.jobs).Count -ne 10 -or $bad.Count -ne 0) {
        throw 'BLOCKED / EXACT_HEAD_CI_NOT_GREEN'
    }
}

function Get-HistoricalEvidenceSnapshot {
    param([Parameter(Mandatory = $true)][string]$CurrentRunId)

    $roots = @(
        '/opt/nexus-quant/gatew-soak/evidence/gatew-okx-readonly-soak',
        $script:StateRoot
    )
    $records = @()
    foreach ($root in $roots) {
        if (-not (Test-Path -LiteralPath $root -PathType Container)) { continue }
        foreach ($file in Get-ChildItem -LiteralPath $root -File -Recurse -ErrorAction Stop) {
            if ($file.FullName -like "$(Get-RunRoot $CurrentRunId)*") { continue }
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

function Test-HistoricalEvidenceImmutable {
    param([Parameter(Mandatory = $true)][string]$Value)

    $path = "$(Get-ControlRoot $Value)/historical-evidence-hashes.json"
    $before = @(Read-JsonFile $path)
    $after = @(Get-HistoricalEvidenceSnapshot $Value)
    return (ConvertTo-CompactJson $before) -ceq (ConvertTo-CompactJson $after)
}

function Install-EncryptedCredential {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Destination,
        [string]$Source
    )

    if ([string]::IsNullOrWhiteSpace($Source)) {
        if (-not (Test-Path -LiteralPath $Destination -PathType Leaf)) {
            throw 'BLOCKED / ENCRYPTED_CREDENTIAL_SOURCE_REQUIRED'
        }
        Assert-PathComponentsNoSymlink $Destination
        Set-OwnerMode $Destination 'root:root' '600'
        return
    }
    $sourcePath = [IO.Path]::GetFullPath($Source)
    $allowedSource = $sourcePath.StartsWith('/etc/nexus-quant/', [StringComparison]::Ordinal) -or
        $sourcePath.StartsWith('/opt/nexus-quant/gatew-soak/config/', [StringComparison]::Ordinal)
    if (-not $allowedSource -or -not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) {
        throw 'BLOCKED / ENCRYPTED_CREDENTIAL_SOURCE_INVALID'
    }
    Assert-PathComponentsNoSymlink $sourcePath
    $sourceMetadata = Get-PosixMetadata $sourcePath
    if ($sourceMetadata.Owner -ne 'root' -or $sourceMetadata.Mode -notin @('400', '600')) {
        throw 'BLOCKED / ENCRYPTED_CREDENTIAL_SOURCE_INVALID'
    }
    if (Test-Path -LiteralPath $Destination) {
        throw 'BLOCKED / ENCRYPTED_CREDENTIAL_ALREADY_EXISTS'
    }
    Assert-PathComponentsNoSymlink (Split-Path -Parent $Destination)
    $temporary = "$Destination.tmp-$PID"
    try {
        $result = Invoke-Native $script:SystemdCredsPath @('encrypt', "--name=$Name", '--', $sourcePath, $temporary) -AllowFailure
        if ($result.ExitCode -ne 0 -or -not (Test-Path -LiteralPath $temporary -PathType Leaf)) {
            throw 'FAIL / ENCRYPTED_CREDENTIAL_CREATE_FAILED'
        }
        Set-OwnerMode $temporary 'root:root' '600'
        Move-Item -LiteralPath $temporary -Destination $Destination
    }
    finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
    }
}

function Copy-InstalledFile {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination,
        [Parameter(Mandatory = $true)][string]$Mode,
        [Parameter(Mandatory = $true)][string]$BackupRoot
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Leaf)) { throw 'BLOCKED / INSTALL_SOURCE_MISSING' }
    Assert-PathComponentsNoSymlink $Source
    Assert-PathComponentsNoSymlink (Split-Path -Parent $Destination)
    if (Test-Path -LiteralPath $Destination -PathType Leaf) {
        Assert-PathComponentsNoSymlink $Destination
        Ensure-Directory $BackupRoot 'root:root' '700'
        Copy-Item -LiteralPath $Destination -Destination (Join-Path $BackupRoot ([IO.Path]::GetFileName($Destination)))
    }
    Invoke-Native $script:InstallPath @('-D', '-o', 'root', '-g', 'root', '-m', $Mode, '--', $Source, $Destination) | Out-Null
}

function Get-InstalledArtifactContracts {
    return @(
        [pscustomobject]@{
            Field = 'workerUnitSha256'
            Source = Join-Path $script:RepoRoot "deploy/systemd/$($script:WorkerTemplate)"
            Destination = "$($script:SystemdRoot)/$($script:WorkerTemplate)"
        },
        [pscustomobject]@{
            Field = 'failCloseUnitSha256'
            Source = Join-Path $script:RepoRoot "deploy/systemd/$($script:FailCloseTemplate)"
            Destination = "$($script:SystemdRoot)/$($script:FailCloseTemplate)"
        },
        [pscustomobject]@{
            Field = 'workerHelperSha256'
            Source = Join-Path $script:RepoRoot "scripts/gatew/$($script:WorkerHelperName)"
            Destination = "$($script:LibexecRoot)/$($script:WorkerHelperName)"
        },
        [pscustomobject]@{
            Field = 'controlHelperSha256'
            Source = Join-Path $script:RepoRoot "scripts/gatew/$($script:ControlHelperName)"
            Destination = "$($script:LibexecRoot)/$($script:ControlHelperName)"
        },
        [pscustomobject]@{
            Field = 'failCloseHelperSha256'
            Source = Join-Path $script:RepoRoot "scripts/gatew/$($script:FailCloseHelperName)"
            Destination = "$($script:LibexecRoot)/$($script:FailCloseHelperName)"
        }
    )
}

function Assert-InstalledArtifactsMatchCheckout {
    param([AllowNull()]$Config)

    foreach ($contract in Get-InstalledArtifactContracts) {
        if (-not (Test-Path -LiteralPath $contract.Source -PathType Leaf) -or
            -not (Test-Path -LiteralPath $contract.Destination -PathType Leaf)) {
            throw 'BLOCKED / VERSIONED_ARTIFACT_MISSING'
        }
        Assert-PathComponentsNoSymlink $contract.Source
        Assert-PathComponentsNoSymlink $contract.Destination
        $sourceHash = Get-Sha256File $contract.Source
        $installedHash = Get-Sha256File $contract.Destination
        if ($sourceHash -cne $installedHash) {
            throw 'BLOCKED / VERSIONED_ARTIFACT_CHECKOUT_MISMATCH'
        }
        if ($null -ne $Config -and
            ([string]$Config.($contract.Field) -cnotmatch '^[a-f0-9]{64}$' -or
            [string]$Config.($contract.Field) -cne $installedHash)) {
            throw 'BLOCKED / FROZEN_ARTIFACT_HASH_MISMATCH'
        }
    }
}

function Install-FormalTooling {
    Assert-RootLinux
    $head = Assert-FixedDetachedWorktree $ExpectedCommit
    $launcherBundle = Build-LauncherBundle $head
    Ensure-Directory $script:StateRoot "root:$($script:LinuxRuntimeGroup)" '710'
    Ensure-Directory $script:LogRoot "root:$($script:LinuxRuntimeGroup)" '710'
    Ensure-Directory $script:ConfigRoot 'root:root' '750'
    Ensure-Directory $script:CredentialRoot 'root:root' '700'
    Ensure-Directory $script:LibexecRoot 'root:root' '755'
    $backupRoot = "$($script:StateRoot)/deploy-backups/$((Get-UtcNow).ToString('yyyyMMddTHHmmssZ'))-$($head.Substring(0, 12))"
    $sources = @{
        $script:WorkerHelperName = Join-Path $script:RepoRoot 'scripts/gatew/gatew-okx-readonly-soak.ps1'
        $script:ControlHelperName = Join-Path $script:RepoRoot 'scripts/gatew/gatew-okx-readonly-soak-control.ps1'
        $script:FailCloseHelperName = Join-Path $script:RepoRoot 'scripts/gatew/gatew-okx-readonly-soak-failclose.ps1'
    }
    foreach ($name in $sources.Keys) {
        Copy-InstalledFile $sources[$name] "$($script:LibexecRoot)/$name" '755' $backupRoot
    }
    Copy-InstalledFile `
        (Join-Path $script:RepoRoot "deploy/systemd/$($script:WorkerTemplate)") `
        "$($script:SystemdRoot)/$($script:WorkerTemplate)" '644' $backupRoot
    Copy-InstalledFile `
        (Join-Path $script:RepoRoot "deploy/systemd/$($script:FailCloseTemplate)") `
        "$($script:SystemdRoot)/$($script:FailCloseTemplate)" '644' $backupRoot
    Assert-InstalledArtifactsMatchCheckout $null

    Install-EncryptedCredential 'db-password' "$($script:CredentialRoot)/db-password.cred" $DatabasePasswordSourceFile
    Install-EncryptedCredential 'credential-master-key' `
        "$($script:CredentialRoot)/credential-master-key.cred" $MasterKeySourceFile
    $offlineSource = if ([string]::IsNullOrWhiteSpace($OfflineDatabasePasswordSourceFile)) {
        $DatabasePasswordSourceFile
    }
    else {
        $OfflineDatabasePasswordSourceFile
    }
    Install-EncryptedCredential 'db-password' `
        "$($script:CredentialRoot)/offline-db-password.cred" $offlineSource

    $verify = Invoke-Native $script:SystemdAnalyzePath @(
        'verify',
        "$($script:SystemdRoot)/$($script:WorkerTemplate)",
        "$($script:SystemdRoot)/$($script:FailCloseTemplate)"
    ) -AllowFailure
    if ($verify.ExitCode -ne 0) { throw 'FAIL / SYSTEMD_STATIC_VALIDATION_FAILED' }
    Invoke-Native $script:SystemctlPath @('daemon-reload') | Out-Null
    foreach ($template in @($script:WorkerTemplate, $script:FailCloseTemplate)) {
        $enabled = Invoke-Native $script:SystemctlPath @('is-enabled', $template) -AllowFailure
        $enabledText = ConvertTo-TrimmedOutput $enabled.Lines
        if ($enabledText -notin @('disabled', 'static', 'indirect')) {
            throw 'BLOCKED / FORMAL_UNIT_ENABLEMENT_UNSAFE'
        }
    }
    return [pscustomobject]@{
        decision = 'PASS / FORMAL_SYSTEMD_TOOLING_INSTALLED'
        commit = $head
        workerUnitSha256 = Get-Sha256File "$($script:SystemdRoot)/$($script:WorkerTemplate)"
        failCloseUnitSha256 = Get-Sha256File "$($script:SystemdRoot)/$($script:FailCloseTemplate)"
        workerHelperSha256 = Get-Sha256File "$($script:LibexecRoot)/$($script:WorkerHelperName)"
        controlHelperSha256 = Get-Sha256File "$($script:LibexecRoot)/$($script:ControlHelperName)"
        failCloseHelperSha256 = Get-Sha256File "$($script:LibexecRoot)/$($script:FailCloseHelperName)"
        launcherBundleRoot = $launcherBundle.Root
        launcherBundleManifestSha256 = $launcherBundle.ManifestSha256
        launcherBundleArtifactCount = $launcherBundle.ArtifactCount
        permanentEnablement = 'disabled'
        backupRoot = if (Test-Path -LiteralPath $backupRoot) { $backupRoot } else { $null }
    }
}

function Install-OfflineDropIns {
    param([Parameter(Mandatory = $true)][string]$Value)

    $workerDropIn = "$($script:RuntimeSystemdRoot)/$(Get-WorkerUnitName $Value).d"
    $failCloseDropIn = "$($script:RuntimeSystemdRoot)/$(Get-FailCloseUnitName $Value).d"
    Ensure-Directory $workerDropIn 'root:root' '755'
    Ensure-Directory $failCloseDropIn 'root:root' '755'
    $credentialPath = "$($script:CredentialRoot)/offline-db-password.cred"
    $workerContent = @"
[Service]
LoadCredentialEncrypted=
LoadCredentialEncrypted=db-password:$credentialPath
IPAddressDeny=any
IPAddressAllow=localhost
"@
    $failCloseContent = @"
[Service]
LoadCredentialEncrypted=
LoadCredentialEncrypted=db-password:$credentialPath
"@
    Write-TextReplaceAtomic (Join-Path $workerDropIn 'offline.conf') $workerContent
    Write-TextReplaceAtomic (Join-Path $failCloseDropIn 'offline.conf') $failCloseContent
    Set-OwnerMode (Join-Path $workerDropIn 'offline.conf') 'root:root' '644'
    Set-OwnerMode (Join-Path $failCloseDropIn 'offline.conf') 'root:root' '644'
    Invoke-Native $script:SystemctlPath @('daemon-reload') | Out-Null
}

function Remove-OfflineDropIns {
    param([Parameter(Mandatory = $true)][string]$Value)

    foreach ($directory in @(
        "$($script:RuntimeSystemdRoot)/$(Get-WorkerUnitName $Value).d",
        "$($script:RuntimeSystemdRoot)/$(Get-FailCloseUnitName $Value).d"
    )) {
        if (Test-Path -LiteralPath $directory -PathType Container) {
            Assert-PathBelowRoot $script:RuntimeSystemdRoot $directory
            Remove-Item -LiteralPath $directory -Recurse -Force
        }
    }
    Invoke-Native $script:SystemctlPath @('daemon-reload') | Out-Null
}

function Prepare-FormalRun {
    Assert-RootLinux
    $head = Assert-FixedDetachedWorktree $ExpectedCommit
    $launcherBundle = Assert-LauncherBundle $head
    Assert-InstalledArtifactsMatchCheckout $null
    if ([string]::IsNullOrWhiteSpace($StartingCiRun)) { throw 'BLOCKED / EXACT_HEAD_CI_REQUIRED' }
    Assert-ExactHeadCi $StartingCiRun $head
    $effectiveRunId = if ([string]::IsNullOrWhiteSpace($RunId)) { New-RunId } else { $RunId }
    Assert-RunId $effectiveRunId
    $runRoot = Get-RunRoot $effectiveRunId
    if (Test-Path -LiteralPath $runRoot) { throw 'BLOCKED / RUN_ID_ALREADY_EXISTS' }
    if ([string]::IsNullOrWhiteSpace($DatabaseUrl)) {
        $DatabaseUrl = [Environment]::GetEnvironmentVariable('NQ_GATEW_SOAK_DB_URL', 'Process')
    }
    if ([string]::IsNullOrWhiteSpace($DatabaseUser)) {
        $DatabaseUser = [Environment]::GetEnvironmentVariable('NQ_GATEW_SOAK_DB_USER', 'Process')
    }
    Assert-LiteralValue $DatabaseUrl
    Assert-LiteralValue $DatabaseUser
    if ($DatabaseUrl -notmatch '^jdbc:postgresql://(127\.0\.0\.1|localhost):[0-9]{1,5}/[A-Za-z0-9_]*(gatew|soak)[A-Za-z0-9_]*$' -or
        $DatabaseUser -notmatch '^[A-Za-z_][A-Za-z0-9_-]{0,62}$') {
        throw 'BLOCKED / SOAK_DATABASE_CONFIG_INVALID'
    }
    if ($RunMode -eq 'OFFLINE_ACCEPTANCE') {
        $DatabaseSchema = "gatew_offline_$($effectiveRunId.Substring($effectiveRunId.Length - 8))"
    }
    elseif ([string]::IsNullOrWhiteSpace($DatabaseSchema)) {
        $DatabaseSchema = 'public'
    }
    if (($RunMode -eq 'OFFLINE_ACCEPTANCE' -and $DatabaseSchema -notmatch '^gatew_offline_[a-f0-9]{8}$') -or
        ($RunMode -eq 'REAL' -and $DatabaseSchema -ne 'public')) {
        throw 'BLOCKED / SOAK_DATABASE_SCHEMA_INVALID'
    }
    $credentialName = if ($RunMode -eq 'OFFLINE_ACCEPTANCE') { 'offline-db-password.cred' } else { 'db-password.cred' }
    if (-not (Test-Path -LiteralPath "$($script:CredentialRoot)/$credentialName" -PathType Leaf) -or
        ($RunMode -eq 'REAL' -and
        -not (Test-Path -LiteralPath "$($script:CredentialRoot)/credential-master-key.cred" -PathType Leaf))) {
        throw 'BLOCKED / ENCRYPTED_CREDENTIAL_REQUIRED'
    }

    Ensure-Directory $script:StateRoot "root:$($script:LinuxRuntimeGroup)" '710'
    Ensure-Directory $runRoot "root:$($script:LinuxRuntimeGroup)" '710'
    Ensure-Directory (Get-ControlRoot $effectiveRunId) 'root:root' '700'
    Ensure-Directory (Get-EvidenceRoot $effectiveRunId) `
        "$($script:LinuxRuntimeUser):$($script:LinuxRuntimeGroup)" '700'
    $controlRoot = Get-ControlRoot $effectiveRunId
    $evidenceRoot = Get-EvidenceRoot $effectiveRunId
    $workerValues = @{
        NQ_GATEW_RUN_MODE = $RunMode
        NQ_GATEW_SOAK_DB_URL = $DatabaseUrl
        NQ_GATEW_SOAK_DB_USER = $DatabaseUser
        NQ_GATEW_SOAK_DB_SCHEMA = $DatabaseSchema
        NQ_GATEW_FORMAL_EVIDENCE_ROOT = $evidenceRoot
        NQ_GATEW_SECRET_SOURCE = 'SYSTEMD_CREDENTIALS'
        NQ_GATEW_FORMAL_SYSTEMD = 'true'
        NQ_GATEW_OFFLINE_HEARTBEAT_SECONDS = [string]$SmokeHeartbeatSeconds
    }
    if ($RunMode -eq 'REAL') {
        foreach ($name in @(
            'SPRING_PROFILES_ACTIVE', 'NQ_GATEW_OKX_READONLY_SOAK_ENABLED', 'CI', 'NQ_NO_OUTBOUND',
            'NQ_LIVE_ENABLED', 'NQ_REAL_ORDER_SUBMISSION_ENABLED', 'NQ_TRANSFER_ENABLED', 'NQ_WITHDRAW_ENABLED',
            'NQ_AI_ENABLED', 'NQ_DH_RUNTIME_ENABLED', 'NQ_REAL_PROVIDER_ENABLED', 'NQ_REAL_CLIENT_ENABLED',
            'NQ_REAL_EXCHANGE_ENABLED', 'NQ_GATEW_SOAK_OWNER_ID', 'NQ_GATEW_SOAK_ACCOUNT_ID',
            'NQ_GATEW_SOAK_CURRENCIES'
        )) {
            $value = [Environment]::GetEnvironmentVariable($name, 'Process')
            Assert-LiteralValue ([string]$value)
            $workerValues[$name] = [string]$value
        }
    }
    $failCloseValues = @{
        NQ_GATEW_RUN_MODE = $RunMode
        NQ_GATEW_SOAK_DB_URL = $DatabaseUrl
        NQ_GATEW_SOAK_DB_USER = $DatabaseUser
        NQ_GATEW_SOAK_DB_SCHEMA = $DatabaseSchema
        NQ_GATEW_SECRET_SOURCE = 'SYSTEMD_CREDENTIALS'
        NQ_GATEW_FORMAL_SYSTEMD = 'true'
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
        schemaVersion = 'gatew-soak-frozen-config-v1'
        runId = $effectiveRunId
        runMode = $RunMode
        databaseUrl = $DatabaseUrl
        databaseUser = $DatabaseUser
        databaseSchema = $DatabaseSchema
        offlineHeartbeatSeconds = $SmokeHeartbeatSeconds
        harnessCommit = $head
        startingCiRun = $StartingCiRun
        workerUnit = Get-WorkerUnitName $effectiveRunId
        failCloseUnit = Get-FailCloseUnitName $effectiveRunId
        workerUnitSha256 = Get-Sha256File "$($script:SystemdRoot)/$($script:WorkerTemplate)"
        failCloseUnitSha256 = Get-Sha256File "$($script:SystemdRoot)/$($script:FailCloseTemplate)"
        workerHelperSha256 = Get-Sha256File "$($script:LibexecRoot)/$($script:WorkerHelperName)"
        controlHelperSha256 = Get-Sha256File "$($script:LibexecRoot)/$($script:ControlHelperName)"
        failCloseHelperSha256 = Get-Sha256File "$($script:LibexecRoot)/$($script:FailCloseHelperName)"
        launcherBundleManifestSha256 = $launcherBundle.ManifestSha256
        acceptanceClockStarted = $false
        preparedAt = (Get-UtcNow).ToString('o')
    })
    Write-JsonCreateOnce (Join-Path $controlRoot 'historical-evidence-hashes.json') `
        @(Get-HistoricalEvidenceSnapshot $effectiveRunId)

    $startedAt = Get-UtcNow
    Write-TextCreateOnce (Join-Path $evidenceRoot 'samples.jsonl') ''
    Write-TextCreateOnce (Join-Path $evidenceRoot 'failures.jsonl') ''
    Write-JsonCreateOnce (Join-Path $evidenceRoot 'manifest.json') ([ordered]@{
        runId = $effectiveRunId
        harnessCommit = $head
        startingCiRun = $StartingCiRun
        startedAt = $startedAt.ToString('o')
        plannedEndAt = $startedAt.AddHours(168).ToString('o')
        durationHours = 168
        cadenceSeconds = 60
        maxTransientRetries = 2
        maxConsecutiveAuthFailures = 3
        venue = 'OKX'
        environment = $RunMode
        profile = 'gatew-okx-readonly-soak'
        applicationVersion = "0.1.0-SNAPSHOT+$($head.Substring(0, 12))"
        endpointAllowlistVersion = 'gatew-okx-private-readonly-v1'
        flywayVersion = '35'
        hostFingerprint = 'FORMAL_SYSTEMD_ROOT_CONTROLLED'
        supervisorScriptGitBlob = (& git -C $script:RepoRoot rev-parse "${head}:scripts/gatew/gatew-okx-readonly-soak.ps1").Trim()
        supervisorArtifactSha256 = Get-Sha256File "$($script:LibexecRoot)/$($script:WorkerHelperName)"
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
    foreach ($file in Get-ChildItem -LiteralPath $evidenceRoot -File) {
        Set-OwnerMode $file.FullName "$($script:LinuxRuntimeUser):$($script:LinuxRuntimeGroup)" '600'
    }
    foreach ($file in Get-ChildItem -LiteralPath $controlRoot -File) {
        Set-OwnerMode $file.FullName 'root:root' '600'
    }
    Assert-RunDirectoryContract $effectiveRunId
    if ($RunMode -eq 'OFFLINE_ACCEPTANCE') { Install-OfflineDropIns $effectiveRunId }
    Set-LifecycleState $effectiveRunId 'STARTING' 'FORMAL_UNIT_START_AUTHORIZED' | Out-Null
    return [pscustomobject]@{
        decision = 'PASS / FORMAL_SOAK_PREPARED'
        runId = $effectiveRunId
        runMode = $RunMode
        lifecycleState = 'STARTING'
        historicalEvidenceCount = @((Read-JsonFile (Join-Path $controlRoot 'historical-evidence-hashes.json'))).Count
        acceptanceClockStarted = $false
    }
}

function ConvertFrom-SystemctlShow {
    param([Parameter(Mandatory = $true)][object[]]$Lines)

    $values = @{}
    foreach ($lineValue in $Lines) {
        $line = [string]$lineValue
        $index = $line.IndexOf('=')
        if ($index -gt 0) { $values[$line.Substring(0, $index)] = $line.Substring($index + 1) }
    }
    foreach ($required in @(
        'LoadState', 'ActiveState', 'SubState', 'MainPID', 'ExecMainStatus', 'FragmentPath',
        'User', 'Group', 'Restart', 'KillMode', 'RuntimeDirectory', 'StateDirectory',
        'IPAddressDeny', 'IPAddressAllow'
    )) {
        if (-not $values.ContainsKey($required)) { $values[$required] = '' }
    }
    $mainPid = 0L
    $execStatusValue = 0
    [long]::TryParse([string]$values.MainPID, [ref]$mainPid) | Out-Null
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
    }
}

function Get-UnitState {
    param([Parameter(Mandatory = $true)][string]$UnitName)

    $properties = @(
        'LoadState', 'ActiveState', 'SubState', 'MainPID', 'ExecMainStatus', 'FragmentPath',
        'User', 'Group', 'Restart', 'KillMode', 'RuntimeDirectory', 'StateDirectory',
        'IPAddressDeny', 'IPAddressAllow'
    )
    $result = Invoke-Native $script:SystemctlPath @(
        'show', $UnitName, '--no-pager', "--property=$($properties -join ',')"
    ) -AllowFailure
    if ($result.ExitCode -ne 0) { throw 'FAIL / SYSTEMD_STATE_UNAVAILABLE' }
    return ConvertFrom-SystemctlShow $result.Lines
}

function Assert-FormalWorkerState {
    param(
        [Parameter(Mandatory = $true)]$State,
        [Parameter(Mandatory = $true)][string]$Value,
        [switch]$AllowInactive
    )

    $fragment = "$($script:SystemdRoot)/$($script:WorkerTemplate)"
    $base = $State.LoadState -eq 'loaded' -and
        $State.FragmentPath -eq $fragment -and
        $State.User -eq $script:LinuxRuntimeUser -and
        $State.Group -eq $script:LinuxRuntimeGroup -and
        $State.Restart -eq 'no' -and
        $State.KillMode -eq 'mixed'
    if (-not $base) { throw 'FAIL / FORMAL_UNIT_CONTRACT_INVALID' }
    if (-not $AllowInactive -and
        ($State.ActiveState -ne 'active' -or $State.SubState -ne 'running' -or $State.MainPID -le 0)) {
        throw 'FAIL / FORMAL_UNIT_NOT_RUNNING'
    }
    if ($AllowInactive -and $State.ActiveState -eq 'inactive' -and $State.MainPID -ne 0) {
        throw 'FAIL / FORMAL_UNIT_PID_NOT_ZERO'
    }
    $config = Read-JsonFile "$(Get-ControlRoot $Value)/frozen-config.json"
    if ([string]$config.runMode -eq 'OFFLINE_ACCEPTANCE') {
        if ([string]$State.IPAddressDeny -notmatch '(?i)(any|0\.0\.0\.0/0|::/0)' -or
            [string]$State.IPAddressAllow -notmatch '(?i)(localhost|127\.0\.0\.0/8|::1)') {
            throw 'FAIL / OFFLINE_NETWORK_POLICY_INVALID'
        }
    }
}

function Get-HeartbeatSequence {
    param([Parameter(Mandatory = $true)]$Heartbeat)

    $property = $Heartbeat.PSObject.Properties['lastSequence']
    if ($null -eq $property) { $property = $Heartbeat.PSObject.Properties['sequence'] }
    if ($null -eq $property) { return 0L }
    return [long]$property.Value
}

function Wait-ForWorkerReady {
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [Parameter(Mandatory = $true)][long]$RequiredSequence
    )

    $deadline = (Get-UtcNow).AddMinutes(10)
    while ((Get-UtcNow) -lt $deadline) {
        $terminalPath = "$(Get-ControlRoot $Value)/terminal-status.json"
        if (Test-Path -LiteralPath $terminalPath -PathType Leaf) {
            throw 'FAIL / WORKER_TERMINATED_DURING_START'
        }
        $state = Get-UnitState (Get-WorkerUnitName $Value)
        if ($state.ActiveState -eq 'failed' -or ($state.ActiveState -eq 'inactive' -and $state.MainPID -eq 0)) {
            Start-Sleep -Milliseconds 250
            continue
        }
        $heartbeat = Read-JsonFile "$(Get-EvidenceRoot $Value)/heartbeat.json"
        if ((Get-HeartbeatSequence $heartbeat) -ge $RequiredSequence -and
            [string]$heartbeat.state -eq 'RUNNING') {
            return $heartbeat
        }
        Start-Sleep -Milliseconds 250
    }
    throw 'FAIL / WORKER_READY_TIMEOUT'
}

function Start-FormalRun {
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
    if ([string]$lifecycle.state -ne 'STARTING') { throw 'BLOCKED / RUN_NOT_STARTABLE' }
    $config = Read-JsonFile "$(Get-ControlRoot $RunId)/frozen-config.json"
    Assert-FixedDetachedWorktree ([string]$config.harnessCommit) | Out-Null
    Invoke-Native $script:SystemctlPath @('start', (Get-WorkerUnitName $RunId)) | Out-Null
    $requiredSequence = if ([string]$config.runMode -eq 'OFFLINE_ACCEPTANCE') { 2L } else { 1L }
    $heartbeat = Wait-ForWorkerReady $RunId $requiredSequence
    $state = Get-UnitState (Get-WorkerUnitName $RunId)
    Assert-FormalWorkerState $state $RunId
    Set-LifecycleState $RunId 'RUNNING' 'FORMAL_WORKER_RUNNING' | Out-Null
    return [pscustomobject]@{
        decision = 'PASS / FORMAL_SYSTEMD_SOAK_STARTED'
        runId = $RunId
        runMode = $config.runMode
        unitName = Get-WorkerUnitName $RunId
        activeState = $state.ActiveState
        subState = $state.SubState
        mainPid = $state.MainPID
        heartbeatSequence = Get-HeartbeatSequence $heartbeat
        heartbeatObservedAt = $heartbeat.observedAt
        acceptanceClockStarted = $false
    }
}

function Get-ResidualWorkerProcesses {
    param([Parameter(Mandatory = $true)][string]$Value)

    $matches = @()
    if (-not (Test-Path -LiteralPath '/proc' -PathType Container)) { return @() }
    foreach ($directory in Get-ChildItem -LiteralPath '/proc' -Directory -ErrorAction SilentlyContinue) {
        if ($directory.Name -notmatch '^[0-9]+$') { continue }
        $path = Join-Path $directory.FullName 'cmdline'
        try {
            $command = [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($path))
            if ($command -match [regex]::Escape($script:WorkerHelperName) -and
                $command -match [regex]::Escape($Value) -and
                $command -match 'run-loop') {
                $matches += [long]$directory.Name
            }
        }
        catch {
            # /proc entry may disappear during exact identity enumeration.
        }
    }
    return @($matches)
}

function Show-FormalStatus {
    Assert-RootLinux
    Assert-RunId $RunId
    $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
    $state = Get-UnitState (Get-WorkerUnitName $RunId)
    Assert-FormalWorkerState $state $RunId -AllowInactive
    $heartbeat = Read-JsonFile "$(Get-EvidenceRoot $RunId)/heartbeat.json"
    $sequence = Get-HeartbeatSequence $heartbeat
    if ($PreviousMainPid -gt 0 -and $state.MainPID -ne $PreviousMainPid) {
        throw 'FAIL / FRESH_SESSION_MAIN_PID_CHANGED'
    }
    if ($MinimumHeartbeatSequence -ge 0 -and $sequence -lt $MinimumHeartbeatSequence) {
        throw 'FAIL / FRESH_SESSION_HEARTBEAT_NOT_ADVANCED'
    }
    if (-not [string]::IsNullOrWhiteSpace($PreviousHeartbeatObservedAt)) {
        $previousHeartbeat = [DateTimeOffset]::MinValue
        $currentHeartbeat = [DateTimeOffset]::MinValue
        if (-not [DateTimeOffset]::TryParse($PreviousHeartbeatObservedAt, [ref]$previousHeartbeat) -or
            -not [DateTimeOffset]::TryParse([string]$heartbeat.observedAt, [ref]$currentHeartbeat) -or
            $currentHeartbeat -le $previousHeartbeat) {
            throw 'FAIL / FRESH_SESSION_HEARTBEAT_NOT_ADVANCED'
        }
    }
    $terminalPath = "$(Get-ControlRoot $RunId)/terminal-status.json"
    $terminal = if (Test-Path -LiteralPath $terminalPath -PathType Leaf) { Read-JsonFile $terminalPath } else { $null }
    return [pscustomobject]@{
        runId = $RunId
        lifecycleState = $lifecycle.state
        terminalStatus = if ($null -eq $terminal) { $null } else { $terminal.terminalStatus }
        unitName = Get-WorkerUnitName $RunId
        loadState = $state.LoadState
        activeState = $state.ActiveState
        subState = $state.SubState
        mainPid = $state.MainPID
        residualProcessCount = @(Get-ResidualWorkerProcesses $RunId).Count
        heartbeatSequence = $sequence
        heartbeatObservedAt = $heartbeat.observedAt
        unitFragmentPath = $state.FragmentPath
        unitUser = $state.User
        acceptanceClockStarted = $false
    }
}

function Wait-ForTerminal {
    param(
        [Parameter(Mandatory = $true)][string]$Value,
        [ValidateRange(1, 300)][int]$Seconds = 120
    )

    $path = "$(Get-ControlRoot $Value)/terminal-status.json"
    $deadline = (Get-UtcNow).AddSeconds($Seconds)
    while ((Get-UtcNow) -lt $deadline) {
        if (Test-Path -LiteralPath $path -PathType Leaf) { return Read-JsonFile $path }
        Start-Sleep -Milliseconds 250
    }
    throw 'FAIL / TERMINAL_STATUS_TIMEOUT'
}

function Request-OperatorStop {
    Assert-RootLinux
    Assert-RunId $RunId
    $terminalPath = "$(Get-ControlRoot $RunId)/terminal-status.json"
    if (Test-Path -LiteralPath $terminalPath -PathType Leaf) {
        $terminal = Read-JsonFile $terminalPath
        return [pscustomobject]@{ decision = 'NO_CHANGE / TERMINAL_RUN'; runId = $RunId; terminalStatus = $terminal.terminalStatus }
    }
    $lock = Enter-TerminalAuthorityLock $RunId
    try {
        if (Test-Path -LiteralPath $terminalPath -PathType Leaf) {
            $terminal = Read-JsonFile $terminalPath
            return [pscustomobject]@{
                decision = 'NO_CHANGE / TERMINAL_RUN'
                runId = $RunId
                terminalStatus = $terminal.terminalStatus
            }
        }
        $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
        if ([string]$lifecycle.state -notin @('PREPARING', 'STARTING', 'RUNNING')) {
            throw 'BLOCKED / RUN_NOT_OPERATOR_STOPPABLE'
        }
        Write-JsonCreateOnce "$(Get-ControlRoot $RunId)/intent.json" ([ordered]@{
            schemaVersion = 'gatew-soak-intent-v1'
            runId = $RunId
            intent = 'OPERATOR_STOP'
            reasonCode = 'OPERATOR_STOP_REQUESTED'
            requestedAt = (Get-UtcNow).ToString('o')
        })
        Set-OwnerMode "$(Get-ControlRoot $RunId)/intent.json" 'root:root' '600'
        Set-LifecycleState $RunId 'OPERATOR_STOPPING' 'OPERATOR_STOP_REQUESTED' | Out-Null
    }
    finally {
        if ($null -ne $lock) { $lock.Dispose() }
    }
    Invoke-Native $script:SystemctlPath @('stop', (Get-WorkerUnitName $RunId)) -AllowFailure | Out-Null
    $finalizerStart = Invoke-Native $script:SystemctlPath @('start', (Get-FailCloseUnitName $RunId)) -AllowFailure
    if ($finalizerStart.ExitCode -ne 0) { throw 'FAIL / FAILCLOSE_FINALIZER_START_FAILED' }
    $terminal = Wait-ForTerminal $RunId
    if ([string]$terminal.terminalStatus -ne 'OPERATOR_STOPPED') {
        throw 'FAIL / OPERATOR_STOP_NOT_PROVEN'
    }
    return [pscustomobject]@{
        decision = 'PASS / OPERATOR_STOPPED'
        runId = $RunId
        terminalStatus = $terminal.terminalStatus
        killSwitchObservedState = $terminal.killSwitchObservedState
        mainPid = $terminal.lastKnownMainPid
        residualProcessCount = $terminal.residualProcessCount
    }
}

function Inject-OfflineFailure {
    Assert-RootLinux
    Assert-RunId $RunId
    $config = Read-JsonFile "$(Get-ControlRoot $RunId)/frozen-config.json"
    $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
    if ([string]$config.runMode -ne 'OFFLINE_ACCEPTANCE' -or [string]$lifecycle.state -ne 'RUNNING') {
        throw 'BLOCKED / OFFLINE_FAILURE_INJECTION_NOT_ALLOWED'
    }
    $runtimeRoot = Get-RuntimeRoot $RunId
    Assert-PosixContract $runtimeRoot 'directory' '700' $script:LinuxRuntimeUser $script:LinuxRuntimeGroup
    $path = Join-Path $runtimeRoot 'offline-cycle-3-failure'
    Write-TextCreateOnce $path 'CONTROLLED_OFFLINE_CYCLE_3_FAILURE'
    Set-OwnerMode $path "root:$($script:LinuxRuntimeGroup)" '440'
    $terminal = Wait-ForTerminal $RunId
    if ([string]$terminal.terminalStatus -ne 'FAILURE_STOPPED') {
        throw 'FAIL / OFFLINE_FAILURE_STOP_NOT_PROVEN'
    }
    return [pscustomobject]@{
        decision = 'PASS / CONTROLLED_OFFLINE_FAILURE_CLOSED'
        runId = $RunId
        terminalStatus = $terminal.terminalStatus
        terminalReasonCode = $terminal.terminalReasonCode
        killSwitchRecoveryStatus = $terminal.killSwitchRecoveryStatus
        killSwitchObservedState = $terminal.killSwitchObservedState
        mainPid = $terminal.lastKnownMainPid
        residualProcessCount = $terminal.residualProcessCount
    }
}

function Invoke-WorkerEvidenceVerify {
    param([Parameter(Mandatory = $true)][string]$Value)

    $previousFormal = [Environment]::GetEnvironmentVariable('NQ_GATEW_FORMAL_SYSTEMD', 'Process')
    $previousEvidence = [Environment]::GetEnvironmentVariable('NQ_GATEW_FORMAL_EVIDENCE_ROOT', 'Process')
    try {
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_SYSTEMD', 'true', 'Process')
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_EVIDENCE_ROOT', (Get-EvidenceRoot $Value), 'Process')
        $result = @(& $script:PowerShellPath -NoProfile -File `
            "$($script:LibexecRoot)/$($script:WorkerHelperName)" `
            -Action evidence-verify -RunId $Value 2>$null)
        if ($LASTEXITCODE -ne 0) { throw 'FAIL / EVIDENCE_VERIFY_FAILED' }
        try { return (($result -join "`n") | ConvertFrom-Json) } catch { throw 'FAIL / EVIDENCE_VERIFY_FAILED' }
    }
    finally {
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_SYSTEMD', $previousFormal, 'Process')
        [Environment]::SetEnvironmentVariable('NQ_GATEW_FORMAL_EVIDENCE_ROOT', $previousEvidence, 'Process')
    }
}

function Verify-FormalRun {
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    $config = Read-JsonFile "$(Get-ControlRoot $RunId)/frozen-config.json"
    Assert-FixedDetachedWorktree ([string]$config.harnessCommit) | Out-Null
    Assert-InstalledArtifactsMatchCheckout $config
    Assert-LauncherBundle ([string]$config.harnessCommit) `
        ([string]$config.launcherBundleManifestSha256) | Out-Null
    $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
    $state = Get-UnitState (Get-WorkerUnitName $RunId)
    Assert-FormalWorkerState $state $RunId -AllowInactive
    $evidence = Invoke-WorkerEvidenceVerify $RunId
    $terminalPath = "$(Get-ControlRoot $RunId)/terminal-status.json"
    $terminal = if (Test-Path -LiteralPath $terminalPath -PathType Leaf) { Read-JsonFile $terminalPath } else { $null }
    $historicalImmutable = Test-HistoricalEvidenceImmutable $RunId
    if ($null -ne $terminal) {
        Assert-PosixContract $terminalPath 'regular file' '600' 'root' 'root'
        if (-not $historicalImmutable -or -not [bool]$terminal.historicalEvidenceImmutable) {
            throw 'FAIL / HISTORICAL_EVIDENCE_CHANGED'
        }
        $residualProcessCount = @(Get-ResidualWorkerProcesses $RunId).Count
        $runtimeDirectoryPresent = Test-Path -LiteralPath (Get-RuntimeRoot $RunId)
        if ($state.ActiveState -ne 'inactive' -or $state.MainPID -ne 0 -or
            $residualProcessCount -ne 0 -or $runtimeDirectoryPresent) {
            throw 'FAIL / TERMINAL_PROCESS_CONTRACT_INVALID'
        }
    }
    if ([string]$config.runMode -eq 'OFFLINE_ACCEPTANCE') {
        if ($null -eq $terminal -or [string]$lifecycle.state -ne 'FAILURE_STOPPED' -or
            [string]$terminal.terminalStatus -ne 'FAILURE_STOPPED' -or
            [string]$terminal.killSwitchRecoveryStatus -notin @(
                'ENGAGE_NOT_REQUIRED_ALREADY_ENGAGED', 'ENGAGE_SUCCEEDED'
            ) -or [string]$terminal.killSwitchObservedState -ne 'ENGAGED' -or
            [long]$terminal.lastSuccessfulCycleSequence -ne 2 -or
            [long]$terminal.lastHeartbeatSequence -ne 3 -or
            [bool]$terminal.acceptanceClockStarted -or
            [bool]$terminal.credentialAccessed -or [bool]$terminal.networkCalled -or
            $null -eq $evidence.offlineAcceptance -or
            [int]$evidence.offlineAcceptance.cycleCount -ne 3 -or
            [string]$evidence.offlineAcceptance.cycle1 -ne 'PASS' -or
            [string]$evidence.offlineAcceptance.cycle2 -ne 'PASS' -or
            [string]$evidence.offlineAcceptance.cycle3 -ne 'CONTROLLED_FAILURE' -or
            [bool]$evidence.offlineAcceptance.credentialAccessed -or
            [bool]$evidence.offlineAcceptance.networkCalled) {
            throw 'FAIL / FULL_OFFLINE_ACCEPTANCE_NOT_PROVEN'
        }
    }
    if ($CleanupOfflineDropIn) { Remove-OfflineDropIns $RunId }
    return [pscustomobject]@{
        decision = 'PASS / FORMAL_SOAK_VERIFIED'
        runId = $RunId
        lifecycleState = $lifecycle.state
        terminalStatus = if ($null -eq $terminal) { $null } else { $terminal.terminalStatus }
        hashChain = $evidence.result
        sampleCount = $evidence.sampleCount
        cycle1 = if ($null -eq $evidence.offlineAcceptance) { $null } else { $evidence.offlineAcceptance.cycle1 }
        cycle2 = if ($null -eq $evidence.offlineAcceptance) { $null } else { $evidence.offlineAcceptance.cycle2 }
        cycle3 = if ($null -eq $evidence.offlineAcceptance) { $null } else { $evidence.offlineAcceptance.cycle3 }
        killSwitchRecoveryStatus = if ($null -eq $terminal) { $null } else { $terminal.killSwitchRecoveryStatus }
        killSwitchObservedState = if ($null -eq $terminal) { $null } else { $terminal.killSwitchObservedState }
        mainPid = $state.MainPID
        residualProcessCount = @(Get-ResidualWorkerProcesses $RunId).Count
        runtimeDirectoryPresent = Test-Path -LiteralPath (Get-RuntimeRoot $RunId)
        historicalEvidenceImmutable = $historicalImmutable
        acceptanceClockStarted = $false
    }
}

function Invoke-UnitPreflight {
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    foreach ($name in @('worker.env', 'failclose.env', 'frozen-config.json', 'lifecycle.json')) {
        Assert-PosixContract "$(Get-ControlRoot $RunId)/$name" 'regular file' '600' 'root' 'root'
    }
    $config = Read-JsonFile "$(Get-ControlRoot $RunId)/frozen-config.json"
    Assert-FixedDetachedWorktree ([string]$config.harnessCommit) | Out-Null
    Assert-InstalledArtifactsMatchCheckout $config
    Assert-LauncherBundle ([string]$config.harnessCommit) `
        ([string]$config.launcherBundleManifestSha256) | Out-Null
    $runtimeRoot = Get-RuntimeRoot $RunId
    Assert-PosixContract $runtimeRoot 'directory' '700' $script:LinuxRuntimeUser $script:LinuxRuntimeGroup
    return [pscustomobject]@{ decision = 'PASS / FORMAL_UNIT_PREFLIGHT'; runId = $RunId }
}

function Record-ExitFact {
    Assert-RootLinux
    Assert-RunId $RunId
    Assert-RunDirectoryContract $RunId
    $lifecycle = Read-JsonFile (Get-LifecyclePath $RunId)
    if ([string]$lifecycle.state -notin @(
        'STARTING', 'RUNNING', 'FAILURE_STOPPING', 'OPERATOR_STOPPING'
    )) {
        throw 'FAIL / SYSTEMD_EXIT_FACT_INVALID'
    }
    if ($LastKnownMainPid -eq 0) {
        $mainPidValue = [Environment]::GetEnvironmentVariable('MAINPID', 'Process')
        $parsedMainPid = 0L
        if ([long]::TryParse($mainPidValue, [ref]$parsedMainPid) -and $parsedMainPid -ge 0) {
            $LastKnownMainPid = $parsedMainPid
        }
    }
    $workerStartPath = "$(Get-EvidenceRoot $RunId)/worker-start.json"
    if (Test-Path -LiteralPath $workerStartPath -PathType Leaf) {
        $workerStart = Read-JsonFile $workerStartPath
        if ((@($workerStart.PSObject.Properties.Name) -join '|') -ne
            'schemaVersion|runId|mainPid|unitName|startedAt' -or
            [string]$workerStart.schemaVersion -ne 'gatew-soak-worker-start-v1' -or
            [string]$workerStart.runId -ne $RunId -or
            [string]$workerStart.unitName -ne (Get-WorkerUnitName $RunId) -or
            [long]$workerStart.mainPid -le 0) {
            throw 'FAIL / SYSTEMD_EXIT_FACT_INVALID'
        }
        if ($LastKnownMainPid -eq 0) {
            $LastKnownMainPid = [long]$workerStart.mainPid
        }
        elseif ($LastKnownMainPid -ne [long]$workerStart.mainPid) {
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
        $LastKnownMainPid -lt 0) {
        throw 'FAIL / SYSTEMD_EXIT_FACT_INVALID'
    }
    $path = "$(Get-ControlRoot $RunId)/exit-fact.json"
    if (Test-Path -LiteralPath $path -PathType Leaf) {
        $existing = Read-JsonFile $path
        if ((@($existing.PSObject.Properties.Name) -join '|') -ne
            'schemaVersion|runId|serviceResult|exitCode|exitStatus|lastKnownMainPid|recordedAt' -or
            [string]$existing.schemaVersion -ne 'gatew-soak-exit-fact-v1' -or
            [string]$existing.runId -ne $RunId) {
            throw 'FAIL / SYSTEMD_EXIT_FACT_INVALID'
        }
        return [pscustomobject]@{ decision = 'NO_CHANGE / EXIT_FACT_EXISTS'; runId = $RunId }
    }
    Write-JsonCreateOnce $path ([ordered]@{
        schemaVersion = 'gatew-soak-exit-fact-v1'
        runId = $RunId
        serviceResult = $ServiceResult
        exitCode = $ExitCode
        exitStatus = $ExitStatus
        lastKnownMainPid = $LastKnownMainPid
        recordedAt = (Get-UtcNow).ToString('o')
    })
    Set-OwnerMode $path 'root:root' '600'
    return [pscustomobject]@{ decision = 'PASS / EXIT_FACT_RECORDED'; runId = $RunId }
}

function Invoke-ControlSelfTest {
    $caseCount = 0
    $validRunId = 'gatew-soak-20260718T000000Z-0123abcd'
    Assert-RunId $validRunId
    $caseCount++
    foreach ($invalid in @('../escape', 'gatew-soak-bad', 'gatew-soak-20260718T000000Z-0123ABCD')) {
        $blocked = $false
        try { Assert-RunId $invalid } catch { $blocked = $true }
        if (-not $blocked) { throw 'runId self-test failed' }
        $caseCount++
    }
    foreach ($literal in @('plain', 'jdbc:postgresql://127.0.0.1:5432/gatew_soak', '')) {
        Assert-LiteralValue $literal
        $caseCount++
    }
    foreach ($invalid in @('$VALUE', '${VALUE}', '%VALUE%', '$(whoami)', "line`nbreak")) {
        $blocked = $false
        try { Assert-LiteralValue $invalid } catch { $blocked = $true }
        if (-not $blocked) { throw 'literal config self-test failed' }
        $caseCount++
    }
    if ($script:AllowedTransitions.RUNNING -notcontains 'FAILURE_STOPPING' -or
        $script:AllowedTransitions.RUNNING -notcontains 'OPERATOR_STOPPING' -or
        $script:AllowedTransitions.FAILURE_STOPPED.Count -ne 0 -or
        $script:AllowedTransitions.OPERATOR_STOPPED.Count -ne 0 -or
        $script:AllowedTransitions.COMPLETED.Count -ne 0 -or
        $script:AllowedTransitions.BLOCKED.Count -ne 0) {
        throw 'lifecycle state machine self-test failed'
    }
    $caseCount += 4
    if ($script:AllowedTransitions.RUNNING -contains 'FAILURE_STOPPED' -or
        $script:AllowedTransitions.RUNNING -contains 'OPERATOR_STOPPED' -or
        'FAILURE_STOPPED' -ceq 'OPERATOR_STOPPED') {
        throw 'operator/failure terminal separation self-test failed'
    }
    $caseCount++
    $temporaryRoot = Join-Path $script:RepoRoot 'target/gatew-okx-readonly-soak/control-self-test'
    $temporary = Join-Path $temporaryRoot ([Guid]::NewGuid().ToString('N'))
    $symlinkTest = 'NOT_AVAILABLE'
    try {
        [IO.Directory]::CreateDirectory($temporary) | Out-Null
        $path = Join-Path $temporary 'create-once.json'
        Write-TextCreateOnce $path '{"safe":true}'
        $rejected = $false
        try { Write-TextCreateOnce $path '{"safe":false}' } catch { $rejected = $true }
        if (-not $rejected -or (Get-Content -LiteralPath $path -Raw) -ne '{"safe":true}') {
            throw 'create-once self-test failed'
        }
        $caseCount++

        $pathEscapeRejected = $false
        try { Assert-PathBelowRoot $temporary (Join-Path $temporary '..\escape') } catch {
            $pathEscapeRejected = $_.Exception.Message -eq 'BLOCKED / PATH_CONTRACT_INVALID'
        }
        if (-not $pathEscapeRejected) { throw 'lexical path escape self-test failed' }
        $caseCount++

        $linkTarget = Join-Path $temporary 'link-target'
        $linkPath = Join-Path $temporary 'link-path'
        [IO.Directory]::CreateDirectory($linkTarget) | Out-Null
        try {
            New-Item -ItemType SymbolicLink -Path $linkPath -Target $linkTarget -ErrorAction Stop | Out-Null
            $symlinkRejected = $false
            try { Assert-PathBelowRoot $temporary $linkPath } catch {
                $symlinkRejected = $_.Exception.Message -eq 'BLOCKED / SYMLINK_PATH_FORBIDDEN'
            }
            if (-not $symlinkRejected) { throw 'symlink/reparse self-test failed' }
            $symlinkTest = 'PASS / REJECTED'
            $caseCount++
        }
        catch {
            if ($_.Exception.Message -eq 'symlink/reparse self-test failed') { throw }
            $symlinkTest = 'NOT_AVAILABLE / PLATFORM_PRIVILEGE'
        }

        $previousStateRoot = $script:StateRoot
        try {
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
            try { Set-LifecycleState $lifecycleRunId 'RUNNING' 'FORMAL_WORKER_RUNNING' | Out-Null } catch {
                $illegalRejected = $_.Exception.Message -eq 'BLOCKED / ILLEGAL_LIFECYCLE_TRANSITION'
            }
            if (-not $illegalRejected) { throw 'illegal transition self-test failed' }
            $caseCount++
        }
        finally {
            $script:StateRoot = $previousStateRoot
        }
    }
    finally {
        if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Recurse -Force }
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
        noNetworkCalled = $true
        credentialAccessed = $false
    }
}

try {
    $result = switch ($Action) {
        'install' { Install-FormalTooling }
        'prepare' { Prepare-FormalRun }
        'start' { Start-FormalRun }
        'status' { Show-FormalStatus }
        'verify' { Verify-FormalRun }
        'stop' { Request-OperatorStop }
        'offline-fail' { Inject-OfflineFailure }
        'unit-preflight' { Invoke-UnitPreflight }
        'record-exit' { Record-ExitFact }
        'self-test' { Invoke-ControlSelfTest }
    }
    if ($null -ne $result) { $result | ConvertTo-Json -Depth 12 }
}
catch {
    $message = if ($_.Exception.Message -match '^(BLOCKED|FAIL) / [A-Z0-9_]+$') {
        $_.Exception.Message
    }
    else {
        'FAIL / FORMAL_CONTROL_INTERNAL_ERROR'
    }
    $failure = [ordered]@{ decision = $message; runId = $RunId }
    if ($Action -eq 'self-test') { $failure.selfTestDetail = $_.Exception.Message }
    [pscustomobject]$failure | ConvertTo-Json
    exit 2
}
