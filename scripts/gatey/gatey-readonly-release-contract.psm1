Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)
$script:CommitPattern = '^[0-9a-f]{40}$'
$script:Sha256Pattern = '^[0-9a-f]{64}$'
$script:ProfileIdentity = 'gatey-readonly-qualification'

function Get-GateYReadonlySha256File
{
    param([Parameter(Mandatory = $true)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_ARTIFACT_MISSING'
    }
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-GateYReadonlySha256Text
{
    param([Parameter(Mandatory = $true)][string]$Text)
    $sha = [Security.Cryptography.SHA256]::Create()
    try
    {
        return [BitConverter]::ToString($sha.ComputeHash($script:Utf8NoBom.GetBytes($Text))).
            Replace('-', '').ToLowerInvariant()
    }
    finally
    {
        $sha.Dispose()
    }
}

function ConvertFrom-GateYReadonlyJson
{
    param([Parameter(Mandatory = $true)][string]$Text)
    $command = Get-Command ConvertFrom-Json -ErrorAction Stop
    if ($command.Parameters.ContainsKey('DateKind'))
    {
        return ($Text | ConvertFrom-Json -DateKind String)
    }
    return ($Text | ConvertFrom-Json)
}

function Get-GateYMigrationInventory
{
    param([Parameter(Mandatory = $true)][string]$MigrationRoot)

    if (-not (Test-Path -LiteralPath $MigrationRoot -PathType Container))
    {
        throw 'BLOCKED / MIGRATION_INVENTORY_MISSING'
    }
    $entries = @()
    foreach ($file in Get-ChildItem -LiteralPath $MigrationRoot -File -Filter 'V*.sql')
    {
        if ($file.Name -cnotmatch '^V([1-9][0-9]*)__([A-Za-z0-9_]+)\.sql$')
        {
            throw 'BLOCKED / MIGRATION_INVENTORY_NAME_INVALID'
        }
        $entries += [pscustomobject][ordered]@{
            version = [int]$Matches[1]
            fileName = $file.Name
            sha256 = Get-GateYReadonlySha256File $file.FullName
        }
    }
    $entries = @($entries | Sort-Object -Property @{ Expression = 'version'; Ascending = $true })
    if ($entries.Count -eq 0)
    {
        throw 'BLOCKED / MIGRATION_INVENTORY_EMPTY'
    }
    $seen = [Collections.Generic.HashSet[int]]::new()
    for ($index = 0; $index -lt $entries.Count; $index++)
    {
        $version = [int]$entries[$index].version
        if (-not $seen.Add($version) -or $version -ne ($index + 1))
        {
            throw 'BLOCKED / MIGRATION_INVENTORY_NOT_CONTIGUOUS'
        }
    }
    $canonical = ($entries | ForEach-Object {
        'V{0}|{1}|{2}' -f $_.version, $_.fileName, $_.sha256
    }) -join "`n"
    $canonical += "`n"
    return [pscustomobject][ordered]@{
        minimumVersion = 'V1'
        targetVersion = 'V' + [string]$entries[-1].version
        migrationRange = 'V1..V' + [string]$entries[-1].version
        inventorySha256 = Get-GateYReadonlySha256Text $canonical
        migrations = $entries
    }
}

function Sort-GateYReleaseArtifacts
{
    param([Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Artifacts)
    [object[]]$values = @($Artifacts)
    [Array]::Sort($values, [Comparison[object]]{
        param($left, $right)
        return [StringComparer]::Ordinal.Compare(
            [string]$left.relativePath,
            [string]$right.relativePath
        )
    })
    return $values
}

function Assert-GateYReleaseArtifacts
{
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Artifacts,
        [switch]$AllowLegacyExactPilotControlSurfaceAbsent
    )
    $paths = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($artifact in $Artifacts)
    {
        $path = [string]$artifact.relativePath
        if ($path -cnotmatch '^[A-Za-z0-9._/-]+$' -or $path.StartsWith('/') -or
                $path -match '(^|/)(\.|\.\.)(/|$)' -or $path.Contains('//') -or
                $path.EndsWith('/') -or -not $paths.Add($path) -or
                [long]$artifact.size -lt 0 -or [string]$artifact.sha256 -cnotmatch $script:Sha256Pattern -or
                [string]$artifact.mode -cnotmatch '^0[4567][0-7]{2}$' -or
                [string]::IsNullOrWhiteSpace([string]$artifact.role))
        {
            throw 'BLOCKED / RELEASE_ARTIFACT_CONTRACT_INVALID'
        }
    }
    $legacyRequired = @(
        'app/nq-app.jar',
        'config/application-gatey-readonly-qualification.yml',
        'bin/gatey-readonly-release-contract.psm1',
        'bin/invoke-gatey-readonly-deployment-contract.ps1',
        'bin/install-gatey-readonly-release.ps1',
        'bin/invoke-gatey-readonly-runtime-deployment.ps1',
        'config/nq-gatey-readonly-qualification.service',
        'config/gatey-readonly-runtime.env.example',
        'config/gatey-readonly-runtime.secrets.env.example',
        'config/gatey-readonly-db.pgpass.example',
        'config/gatey-readonly-runtime-target.json',
        'bin/verify-gatew-release.ps1',
        'bin/gatew-release-contract.psm1'
    )
    foreach ($required in $legacyRequired)
    {
        if (-not $paths.Contains($required))
        {
            throw 'BLOCKED / RELEASE_REQUIRED_ARTIFACT_MISSING'
        }
    }
    $exactPilotControl = 'bin/invoke-gatey-exact-pilot-scope.ps1'
    if (-not $paths.Contains($exactPilotControl))
    {
        if (-not $AllowLegacyExactPilotControlSurfaceAbsent -or
                $paths.Count -ne $legacyRequired.Count)
        {
            throw 'BLOCKED / RELEASE_REQUIRED_ARTIFACT_MISSING'
        }
    }
    if (-not $paths.Contains('bin/invoke-gatey-minimal-live-pilot.ps1') -and
            -not $AllowLegacyExactPilotControlSurfaceAbsent)
    {
        throw 'BLOCKED / RELEASE_REQUIRED_ARTIFACT_MISSING'
    }
}

function New-GateYReadonlyReleaseManifest
{
    param(
        [Parameter(Mandatory = $true)][string]$SourceCommit,
        [Parameter(Mandatory = $true)][string]$SourceCommitTimestamp,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][object[]]$Artifacts,
        [Parameter(Mandatory = $true)][string]$MigrationRoot
    )

    if ($SourceCommit -cnotmatch $script:CommitPattern -or
            $SourceCommitTimestamp -cnotmatch '^20[0-9]{2}-[01][0-9]-[0-3][0-9]T[0-2][0-9]:[0-5][0-9]:[0-5][0-9]Z$')
    {
        throw 'BLOCKED / RELEASE_SOURCE_IDENTITY_INVALID'
    }
    $orderedArtifacts = @(Sort-GateYReleaseArtifacts $Artifacts)
    Assert-GateYReleaseArtifacts $orderedArtifacts
    $migration = Get-GateYMigrationInventory $MigrationRoot
    $app = @($orderedArtifacts | Where-Object { $_.relativePath -ceq 'app/nq-app.jar' })
    if ($app.Count -ne 1)
    {
        throw 'BLOCKED / RELEASE_APPLICATION_IDENTITY_INVALID'
    }
    return [pscustomobject][ordered]@{
        schemaVersion = 'gatey-readonly-release.v1'
        releaseId = $SourceCommit
        sourceCommit = $SourceCommit
        sourceCommitTimestamp = $SourceCommitTimestamp
        buildTimestampPolicy = 'SOURCE_COMMIT_UTC_SECONDS'
        requiredRuntime = [pscustomobject][ordered]@{
            os = 'linux'
            javaMajor = 21
            powershellMajor = 7
        }
        buildProvenance = [pscustomobject][ordered]@{
            sourceTreeMode = 'EXACT_GIT_COMMIT_BLOB_BYTES'
            cleanWorktree = $true
            buildLocation = 'OPERATOR_WORKSTATION_ONLY'
            serverBuildAllowed = $false
            mavenCommand = 'mvn -f backend/pom.xml -pl nq-app -am clean package spring-boot:repackage -DskipTests'
            outputTimestamp = $SourceCommitTimestamp
        }
        application = [pscustomobject][ordered]@{
            artifactPath = 'app/nq-app.jar'
            artifactSha256 = [string]$app[0].sha256
            profileIdentity = $script:ProfileIdentity
            bindAddress = '127.0.0.1'
            managementHealthPath = '/actuator/health'
            managementInfoPath = '/actuator/info'
        }
        schema = $migration
        ownership = [pscustomobject][ordered]@{
            releaseOwner = 'root'
            serviceUser = 'nq-gatey-readonly'
            releasesRoot = '/opt/nexus-quant/releases'
            currentPointer = '/opt/nexus-quant/current'
        }
        safety = [pscustomobject][ordered]@{
            factClassification = 'EXPECTED_CONFIGURATION'
            live = 'DISABLED'
            killSwitch = 'ENGAGED'
            spotExecutionProviderBeans = 0
            executionWorkerBindings = 0
            tradingAuthorization = $false
        }
        deployment = [pscustomobject][ordered]@{
            immutableReleaseDirectory = '/opt/nexus-quant/releases/<release-id>'
            atomicCurrentPointer = $true
            serverBuildAllowed = $false
            overwritePreviousReleaseAllowed = $false
            sequence = @(
                'VERIFY_RELEASE', 'INSPECT_FLYWAY_HISTORY', 'DERIVE_PENDING_MIGRATIONS',
                'BACKUP', 'VERIFY_BACKUP', 'VERIFY_ROLLBACK_CONTRACT', 'MIGRATE_FORWARD_ONLY',
                'VERIFY_FLYWAY_TARGET', 'ACTIVATE_ATOMIC_POINTER', 'VERIFY_HEALTH'
            )
        }
        artifacts = $orderedArtifacts
    }
}

function Assert-GateYReleaseManifestRuntimeFactBoundary($Manifest)
{
    if ($null -eq $Manifest.safety -or
            [string]$Manifest.safety.factClassification -cne 'EXPECTED_CONFIGURATION')
    {
        throw 'BLOCKED / RELEASE_MANIFEST_RUNTIME_FACT_INVALID'
    }
    $allowedSafetyFields = @(
        'factClassification', 'live', 'killSwitch', 'spotExecutionProviderBeans',
        'executionWorkerBindings', 'tradingAuthorization'
    )
    $actualSafetyFields = @($Manifest.safety.PSObject.Properties.Name)
    if (@($allowedSafetyFields | Where-Object { $actualSafetyFields -notcontains $_ }).Count -ne 0 -or
            @($actualSafetyFields | Where-Object { $allowedSafetyFields -notcontains $_ }).Count -ne 0)
    {
        throw 'BLOCKED / RELEASE_MANIFEST_RUNTIME_FACT_INVALID'
    }
    foreach ($forbidden in @(
        'startupCredentialReads', 'startupOkxGetCalls', 'startupOkxPostCalls',
        'runtimeHealthy', 'killSwitchObserved', 'databaseConnected'
    ))
    {
        if ($null -ne $Manifest.PSObject.Properties[$forbidden] -or
                $null -ne $Manifest.safety.PSObject.Properties[$forbidden])
        {
            throw 'BLOCKED / RELEASE_MANIFEST_RUNTIME_FACT_INVALID'
        }
    }
}

function ConvertTo-GateYReadonlyCanonicalManifestJson
{
    param([Parameter(Mandatory = $true)]$Manifest)
    return ((ConvertTo-GateYReadonlyCanonicalJsonValue $Manifest) + "`n")
}

function ConvertTo-GateYReadonlyCanonicalJsonString
{
    param([Parameter(Mandatory = $true)][AllowEmptyString()][string]$Value)
    $builder = [Text.StringBuilder]::new()
    [void]$builder.Append('"')
    foreach ($character in $Value.ToCharArray())
    {
        $code = [int]$character
        switch ($code)
        {
            8 { [void]$builder.Append('\b'); continue }
            9 { [void]$builder.Append('\t'); continue }
            10 { [void]$builder.Append('\n'); continue }
            12 { [void]$builder.Append('\f'); continue }
            13 { [void]$builder.Append('\r'); continue }
            34 { [void]$builder.Append('\"'); continue }
            92 { [void]$builder.Append('\\'); continue }
        }
        if ($code -lt 32)
        {
            [void]$builder.Append(('\u{0:x4}' -f $code))
        }
        else
        {
            [void]$builder.Append($character)
        }
    }
    [void]$builder.Append('"')
    return $builder.ToString()
}

function ConvertTo-GateYReadonlyCanonicalJsonValue
{
    param([AllowNull()]$Value)
    if ($null -eq $Value) { return 'null' }
    if ($Value -is [string]) { return ConvertTo-GateYReadonlyCanonicalJsonString ([string]$Value) }
    if ($Value -is [bool]) { return $(if ([bool]$Value) { 'true' } else { 'false' }) }
    if ($Value -is [DateTime])
    {
        return ConvertTo-GateYReadonlyCanonicalJsonString `
            ([DateTime]$Value).ToUniversalTime().ToString(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                [Globalization.CultureInfo]::InvariantCulture
            )
    }
    if ($Value -is [DateTimeOffset])
    {
        return ConvertTo-GateYReadonlyCanonicalJsonString `
            ([DateTimeOffset]$Value).ToUniversalTime().ToString(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                [Globalization.CultureInfo]::InvariantCulture
            )
    }
    if ($Value -is [byte] -or $Value -is [sbyte] -or $Value -is [int16] -or
            $Value -is [uint16] -or $Value -is [int32] -or $Value -is [uint32] -or
            $Value -is [int64] -or $Value -is [uint64] -or $Value -is [decimal] -or
            $Value -is [double] -or $Value -is [single])
    {
        return ([IFormattable]$Value).ToString($null, [Globalization.CultureInfo]::InvariantCulture)
    }
    if ($Value -is [Collections.IDictionary])
    {
        $names = @($Value.Keys | ForEach-Object { [string]$_ })
        [Array]::Sort([string[]]$names, [StringComparer]::Ordinal)
        $pairs = @($names | ForEach-Object {
            (ConvertTo-GateYReadonlyCanonicalJsonString $_) + ':' +
                (ConvertTo-GateYReadonlyCanonicalJsonValue $Value[$_])
        })
        return '{' + ($pairs -join ',') + '}'
    }
    if ($Value -is [Collections.IEnumerable])
    {
        $items = @($Value | ForEach-Object { ConvertTo-GateYReadonlyCanonicalJsonValue $_ })
        return '[' + ($items -join ',') + ']'
    }
    $properties = @($Value.PSObject.Properties | Where-Object { $_.MemberType -match 'Property$' })
    $names = @($properties | ForEach-Object { [string]$_.Name })
    [Array]::Sort([string[]]$names, [StringComparer]::Ordinal)
    $pairs = @($names | ForEach-Object {
        $name = $_
        (ConvertTo-GateYReadonlyCanonicalJsonString $name) + ':' +
            (ConvertTo-GateYReadonlyCanonicalJsonValue $Value.$name)
    })
    return '{' + ($pairs -join ',') + '}'
}

function Write-GateYReadonlyCanonicalManifest
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Manifest
    )
    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [IO.File]::WriteAllBytes(
        $Path,
        $script:Utf8NoBom.GetBytes((ConvertTo-GateYReadonlyCanonicalManifestJson $Manifest))
    )
}

function Test-GateYLinuxPlatform
{
    $value = Get-Variable -Name IsLinux -ErrorAction SilentlyContinue
    return $null -ne $value -and [bool]$value.Value
}

function Test-GateYWindowsPlatform
{
    $value = Get-Variable -Name IsWindows -ErrorAction SilentlyContinue
    if ($null -ne $value) { return [bool]$value.Value }
    return $env:OS -ceq 'Windows_NT'
}

function Invoke-GateYNative
{
    param(
        [Parameter(Mandatory = $true)][string]$FilePath,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$Arguments
    )
    if (-not (Test-Path -LiteralPath $FilePath -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_LINK_IDENTITY_UNAVAILABLE'
    }
    $lines = @(& $FilePath @Arguments 2>$null)
    if ([int]$LASTEXITCODE -ne 0)
    {
        throw 'BLOCKED / RELEASE_LINK_IDENTITY_UNAVAILABLE'
    }
    return ,@($lines)
}

function Assert-GateYPathComponentsNoLink
{
    param([Parameter(Mandatory = $true)][string]$Path)
    $current = Get-Item -LiteralPath ([IO.Path]::GetFullPath($Path)) -Force -ErrorAction Stop
    while ($null -ne $current)
    {
        $linkType = $current.PSObject.Properties['LinkType']
        if (($null -ne $linkType -and $null -ne $linkType.Value) -or
                $current.Attributes.ToString().Contains('ReparsePoint'))
        {
            throw 'BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION'
        }
        $current = if ($current -is [IO.DirectoryInfo]) {
            $current.Parent
        } elseif ($current -is [IO.FileInfo]) {
            $current.Directory
        } else {
            $null
        }
    }
}

function Get-GateYLinuxMetadata
{
    param([Parameter(Mandatory = $true)][string]$Path)
    $lines = Invoke-GateYNative '/usr/bin/stat' @(
        '--format=%F|%h|%U|%a|%d|%i', '--', $Path
    )
    if ($lines.Count -ne 1)
    {
        throw 'BLOCKED / RELEASE_LINK_IDENTITY_UNAVAILABLE'
    }
    $parts = @(([string]$lines[0]).Split('|'))
    if ($parts.Count -ne 6)
    {
        throw 'BLOCKED / RELEASE_LINK_IDENTITY_UNAVAILABLE'
    }
    return [pscustomobject][ordered]@{
        type = $parts[0]
        linkCount = [long]$parts[1]
        owner = $parts[2]
        mode = $parts[3]
        device = $parts[4]
        inode = $parts[5]
    }
}

function Assert-GateYRegularFileIdentity
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [switch]$RequirePosix,
        [string]$ExpectedOwner = 'root',
        [string]$ExpectedMode = '0644'
    )
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_ARTIFACT_MISSING'
    }
    Assert-GateYPathComponentsNoLink $Path
    $item = Get-Item -LiteralPath $Path -Force
    $linkType = $item.PSObject.Properties['LinkType']
    if (($null -ne $linkType -and $null -ne $linkType.Value) -or
            $item.Attributes.ToString().Contains('ReparsePoint'))
    {
        throw 'BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION'
    }
    if (Test-GateYLinuxPlatform)
    {
        $metadata = Get-GateYLinuxMetadata $Path
        if ([string]$metadata.type -cne 'regular file' -or [long]$metadata.linkCount -ne 1)
        {
            throw 'BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION'
        }
        if ($RequirePosix -and
                ([string]$metadata.owner -cne $ExpectedOwner -or
                        ('0' + [string]$metadata.mode) -cne $ExpectedMode))
        {
            throw 'BLOCKED / RELEASE_POSIX_CONTRACT_VIOLATION'
        }
        return $metadata
    }
    if (Test-GateYWindowsPlatform)
    {
        if ($RequirePosix)
        {
            throw 'BLOCKED / RELEASE_POSIX_METADATA_UNAVAILABLE'
        }
        $fsutil = Join-Path $env:SystemRoot 'System32/fsutil.exe'
        $links = @(Invoke-GateYNative $fsutil @('hardlink', 'list', $Path) |
            Where-Object { -not [string]::IsNullOrWhiteSpace([string]$_) })
        if ($links.Count -ne 1)
        {
            throw 'BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION'
        }
        return [pscustomobject][ordered]@{
            type = 'regular file'
            linkCount = 1
            owner = $null
            mode = $null
            device = $null
            inode = $null
        }
    }
    throw 'BLOCKED / RELEASE_LINK_IDENTITY_UNAVAILABLE'
}

function Assert-GateYPosixDirectoryTree
{
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [string]$ExpectedOwner = 'root'
    )
    if (-not (Test-GateYLinuxPlatform))
    {
        throw 'BLOCKED / RELEASE_POSIX_METADATA_UNAVAILABLE'
    }
    foreach ($directory in @(
        Get-Item -LiteralPath $Root -Force
        Get-ChildItem -LiteralPath $Root -Directory -Recurse -Force
    ))
    {
        Assert-GateYPathComponentsNoLink $directory.FullName
        $metadata = Get-GateYLinuxMetadata $directory.FullName
        if ([string]$metadata.type -cne 'directory' -or [string]$metadata.owner -cne $ExpectedOwner -or
                [string]$metadata.mode -cne '755')
        {
            throw 'BLOCKED / RELEASE_POSIX_CONTRACT_VIOLATION'
        }
    }
}

function Test-GateYReadonlyRelease
{
    param(
        [Parameter(Mandatory = $true)][string]$ReleaseRoot,
        [switch]$RequirePosix,
        [switch]$AllowLegacyExactPilotControlSurfaceAbsent,
        [string]$ExpectedOwner = 'root'
    )
    if (-not (Test-Path -LiteralPath $ReleaseRoot -PathType Container))
    {
        throw 'BLOCKED / RELEASE_ROOT_MISSING'
    }
    $root = [IO.Path]::GetFullPath($ReleaseRoot).TrimEnd('\', '/')
    Assert-GateYPathComponentsNoLink $root
    if ($RequirePosix) { Assert-GateYPosixDirectoryTree $root $ExpectedOwner }
    $manifestPath = Join-Path $root 'release-manifest.json'
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf))
    {
        throw 'BLOCKED / RELEASE_MANIFEST_MISSING'
    }
    $null = Assert-GateYRegularFileIdentity $manifestPath -RequirePosix:$RequirePosix -ExpectedOwner $ExpectedOwner -ExpectedMode '0644'
    $text = [IO.File]::ReadAllText($manifestPath, [Text.Encoding]::UTF8)
    $manifest = ConvertFrom-GateYReadonlyJson $text
    $canonical = ConvertTo-GateYReadonlyCanonicalManifestJson $manifest
    if ($text -cne $canonical -or [string]$manifest.schemaVersion -cne 'gatey-readonly-release.v1' -or
            [string]$manifest.releaseId -cnotmatch $script:CommitPattern -or
            [string]$manifest.sourceCommit -cne [string]$manifest.releaseId -or
            [int]$manifest.requiredRuntime.javaMajor -ne 21 -or
            [string]$manifest.application.profileIdentity -cne $script:ProfileIdentity -or
            [string]$manifest.application.bindAddress -cne '127.0.0.1')
    {
        throw 'BLOCKED / RELEASE_MANIFEST_CONTRACT_INVALID'
    }
    Assert-GateYReleaseManifestRuntimeFactBoundary $manifest
    Assert-GateYReleaseArtifacts @($manifest.artifacts) `
        -AllowLegacyExactPilotControlSurfaceAbsent:$AllowLegacyExactPilotControlSurfaceAbsent
    $declared = [Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach ($artifact in $manifest.artifacts)
    {
        $relative = [string]$artifact.relativePath
        $null = $declared.Add($relative)
        $path = [IO.Path]::GetFullPath((Join-Path $root $relative))
        $comparison = if (Test-GateYLinuxPlatform) {
            [StringComparison]::Ordinal
        } else {
            [StringComparison]::OrdinalIgnoreCase
        }
        if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, $comparison))
        {
            throw 'BLOCKED / RELEASE_ARTIFACT_CONTRACT_INVALID'
        }
        $null = Assert-GateYRegularFileIdentity $path -RequirePosix:$RequirePosix -ExpectedOwner $ExpectedOwner -ExpectedMode ([string]$artifact.mode)
        if ((Get-Item -LiteralPath $path -Force).Length -ne [long]$artifact.size -or
                (Get-GateYReadonlySha256File $path) -cne [string]$artifact.sha256)
        {
            throw 'BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH'
        }
    }
    $actual = @(Get-ChildItem -LiteralPath $root -File -Recurse -Force | ForEach-Object {
        $relative = $_.FullName.Substring($root.Length + 1).Replace('\', '/')
        if ($relative -cne 'release-manifest.json')
        {
            $null = Assert-GateYRegularFileIdentity $_.FullName
            $relative
        }
    })
    if (@($actual | Where-Object { -not $declared.Contains($_) }).Count -ne 0 -or
            @($actual).Count -ne @($manifest.artifacts).Count)
    {
        throw 'BLOCKED / RELEASE_UNDECLARED_ARTIFACT'
    }
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_RELEASE_VERIFIED'
        releaseId = [string]$manifest.releaseId
        manifestSha256 = Get-GateYReadonlySha256File $manifestPath
        artifactCount = @($manifest.artifacts).Count
        schemaTarget = [string]$manifest.schema.targetVersion
        profileIdentity = [string]$manifest.application.profileIdentity
        linkIntegrityVerified = $true
        posixVerified = [bool]$RequirePosix
    }
}

function Get-GateYMigrationPlan
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$AppliedVersions
    )
    $inventory = @($Manifest.schema.migrations)
    $applied = @($AppliedVersions | ForEach-Object {
        if ($_ -cnotmatch '^V([1-9][0-9]*)$') { throw 'BLOCKED / FLYWAY_HISTORY_INVALID' }
        [int]$Matches[1]
    } | Sort-Object -Unique)
    for ($index = 0; $index -lt $applied.Count; $index++)
    {
        if ($applied[$index] -ne ($index + 1) -or $applied[$index] -gt $inventory.Count)
        {
            throw 'BLOCKED / FLYWAY_HISTORY_DIVERGED'
        }
    }
    $pending = @($inventory | Where-Object { [int]$_.version -gt $applied.Count })
    return [pscustomobject][ordered]@{
        currentVersion = if ($applied.Count -eq 0) { 'EMPTY' } else { 'V' + $applied[-1] }
        targetVersion = [string]$Manifest.schema.targetVersion
        pendingVersions = @($pending | ForEach-Object { 'V' + [string]$_.version })
        forwardOnly = $true
    }
}

function Get-GateYAuditEvidenceSha256
{
    param([Parameter(Mandatory = $true)]$Evidence)
    $unsigned = [ordered]@{}
    foreach ($property in @($Evidence.PSObject.Properties))
    {
        if ($property.Name -cne 'auditSha256')
        {
            $unsigned[$property.Name] = $property.Value
        }
    }
    return Get-GateYReadonlySha256Text ((ConvertTo-GateYReadonlyCanonicalJsonValue $unsigned) + [char]10)
}

function New-GateYAuditEvidence
{
    param(
        [Parameter(Mandatory = $true)]
        [ValidateSet('FLYWAY_HISTORY', 'BACKUP_VERIFICATION', 'RESTORE_VERIFICATION', 'COMPATIBILITY', 'HEALTH')]
        [string]$EvidenceType,
        [Parameter(Mandatory = $true)]
        [ValidateSet('PASS', 'NOT_VERIFIED')]
        [string]$ObservationResult,
        [Parameter(Mandatory = $true)][string]$ObservationKind,
        [Parameter(Mandatory = $true)]$ObservedFacts
    )
    $evidence = [ordered]@{
        schemaVersion = 'gatey-deployment-audit-evidence.v2'
        evidenceType = $EvidenceType
        evidenceRole = 'AUDIT_EVIDENCE_ONLY'
        observationResult = $ObservationResult
        recordedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
        observationKind = $ObservationKind
        authorizationEligible = $false
    }
    foreach ($property in @($ObservedFacts.PSObject.Properties))
    {
        if ($evidence.Contains($property.Name) -or $property.Name -ceq 'auditSha256')
        {
            throw 'BLOCKED / DEPLOYMENT_AUDIT_EVIDENCE_SCHEMA_INVALID'
        }
        $evidence[$property.Name] = $property.Value
    }
    $value = [pscustomobject]$evidence
    $evidence.auditSha256 = Get-GateYAuditEvidenceSha256 $value
    return [pscustomobject]$evidence
}

function Assert-GateYAuditEvidence
{
    param(
        [Parameter(Mandatory = $true)]$Evidence,
        [Parameter(Mandatory = $true)]
        [ValidateSet('FLYWAY_HISTORY', 'BACKUP_VERIFICATION', 'RESTORE_VERIFICATION', 'COMPATIBILITY', 'HEALTH')]
        [string]$ExpectedType
    )
    if ([string]$Evidence.schemaVersion -cne 'gatey-deployment-audit-evidence.v2' -or
            [string]$Evidence.evidenceType -cne $ExpectedType -or
            [string]$Evidence.evidenceRole -cne 'AUDIT_EVIDENCE_ONLY' -or
            [bool]$Evidence.authorizationEligible -or
            [string]$Evidence.auditSha256 -cnotmatch $script:Sha256Pattern -or
            [string]$Evidence.auditSha256 -cne (Get-GateYAuditEvidenceSha256 $Evidence))
    {
        throw 'BLOCKED / DEPLOYMENT_AUDIT_EVIDENCE_INVALID'
    }
}

function Write-GateYAuditEvidence
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]$Evidence
    )
    Assert-GateYAuditEvidence $Evidence ([string]$Evidence.evidenceType)
    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [IO.File]::WriteAllBytes(
        $Path,
        $script:Utf8NoBom.GetBytes((ConvertTo-GateYReadonlyCanonicalManifestJson $Evidence))
    )
}

function Read-GateYAuditEvidence
{
    param(
        [Parameter(Mandatory = $true)][string]$Path,
        [Parameter(Mandatory = $true)]
        [ValidateSet('FLYWAY_HISTORY', 'BACKUP_VERIFICATION', 'RESTORE_VERIFICATION', 'COMPATIBILITY', 'HEALTH')]
        [string]$ExpectedType
    )
    $null = Assert-GateYRegularFileIdentity $Path
    $text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8)
    $evidence = ConvertFrom-GateYReadonlyJson $text
    if ($text -cne (ConvertTo-GateYReadonlyCanonicalManifestJson $evidence))
    {
        throw 'BLOCKED / DEPLOYMENT_AUDIT_EVIDENCE_INVALID'
    }
    Assert-GateYAuditEvidence $evidence $ExpectedType
    return $evidence
}

function Assert-GateYDisposableEvidencePath
{
    param([Parameter(Mandatory = $true)][string]$Path)
    $fullPath = [IO.Path]::GetFullPath($Path)
    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    if (-not $fullPath.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase))
    {
        throw 'BLOCKED / DISPOSABLE_EVIDENCE_PATH_REQUIRED'
    }
    return $fullPath
}

function Read-GateYCanonicalEvidence
{
    param([Parameter(Mandatory = $true)][string]$Path)
    $null = Assert-GateYRegularFileIdentity $Path
    $text = [IO.File]::ReadAllText($Path, [Text.Encoding]::UTF8)
    $value = ConvertFrom-GateYReadonlyJson $text
    if ($text -cne (ConvertTo-GateYReadonlyCanonicalManifestJson $value))
    {
        throw 'BLOCKED / VERIFIER_INPUT_CANONICAL_FORMAT_INVALID'
    }
    return $value
}

function Assert-GateYEvidenceFields
{
    param(
        [Parameter(Mandatory = $true)]$Evidence,
        [Parameter(Mandatory = $true)][string[]]$ExpectedFields
    )
    $actual = @($Evidence.PSObject.Properties.Name) | Sort-Object
    $expected = @($ExpectedFields) | Sort-Object
    if (($actual -join '|') -cne ($expected -join '|'))
    {
        throw 'BLOCKED / VERIFIER_INPUT_SCHEMA_INVALID'
    }
}

function Test-GateYFlywayHistoryObservation
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$ManifestSha256,
        [Parameter(Mandatory = $true)][string]$ObservationPath
    )
    $path = Assert-GateYDisposableEvidencePath $ObservationPath
    $observation = Read-GateYCanonicalEvidence $path
    Assert-GateYEvidenceFields $observation @(
        'schemaVersion', 'databaseIdentity', 'targetSchemaVersion',
        'appliedFlywayVersions', 'observedAt'
    )
    if ([string]$observation.schemaVersion -cne 'gatey-disposable-flyway-observation.v1' -or
            [string]::IsNullOrWhiteSpace([string]$observation.databaseIdentity) -or
            [string]$observation.targetSchemaVersion -cne [string]$Manifest.schema.targetVersion -or
            [string]$observation.observedAt -cnotmatch '^20[0-9]{2}-[01][0-9]-[0-3][0-9]T[0-2][0-9]:[0-5][0-9]:[0-5][0-9]Z$')
    {
        throw 'BLOCKED / FLYWAY_HISTORY_OBSERVATION_INVALID'
    }
    $null = Get-GateYMigrationPlan $Manifest @($observation.appliedFlywayVersions)
    return New-GateYAuditEvidence FLYWAY_HISTORY PASS `
        DISPOSABLE_FLYWAY_HISTORY_FILE_VERIFICATION ([pscustomobject][ordered]@{
            releaseManifestSha256 = $ManifestSha256
            databaseIdentity = [string]$observation.databaseIdentity
            targetSchemaVersion = [string]$observation.targetSchemaVersion
            appliedFlywayVersions = @($observation.appliedFlywayVersions)
            observedAt = [string]$observation.observedAt
            observationSha256 = Get-GateYReadonlySha256File $path
        })
}

function Test-GateYReleaseSchemaCompatibility
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$ManifestSha256,
        [AllowNull()][string]$PreviousReleaseRoot
    )
    $decision = 'UNKNOWN'
    $previousReleaseId = 'UNKNOWN'
    $previousManifestSha256 = ('0' * 64)
    $proofDigest = ('0' * 64)
    $proofType = 'NO_VERIFIED_PREVIOUS_RELEASE'
    if (-not [string]::IsNullOrWhiteSpace($PreviousReleaseRoot) -and
            (Test-Path -LiteralPath $PreviousReleaseRoot -PathType Container))
    {
        $fullPreviousRoot = [IO.Path]::GetFullPath($PreviousReleaseRoot)
        $canonicalCurrent = Test-GateYLinuxPlatform -and
            $fullPreviousRoot -ceq '/opt/nexus-quant/current'
        if (-not $canonicalCurrent)
        {
            $null = Assert-GateYDisposableEvidencePath $fullPreviousRoot
        }
        $previous = Test-GateYReadonlyRelease $PreviousReleaseRoot `
            -RequirePosix:$canonicalCurrent `
            -AllowLegacyExactPilotControlSurfaceAbsent
        $previousManifest = Get-Content -LiteralPath (Join-Path $PreviousReleaseRoot 'release-manifest.json') `
            -Raw | ConvertFrom-Json
        $previousReleaseId = [string]$previous.releaseId
        $previousManifestSha256 = [string]$previous.manifestSha256
        $proofDigest = [string]$previousManifest.schema.inventorySha256
        $proofType = if ($canonicalCurrent) {
            'VERIFIED_INSTALLED_CURRENT_RELEASE_SCHEMA_IDENTITY'
        } else {
            'DISPOSABLE_RELEASE_SCHEMA_IDENTITY_NON_AUTHORITATIVE'
        }
        if ($canonicalCurrent -and
                [string]$previousManifest.schema.targetVersion -ceq [string]$Manifest.schema.targetVersion -and
                [string]$previousManifest.schema.inventorySha256 -ceq [string]$Manifest.schema.inventorySha256)
        {
            $decision = 'COMPATIBLE'
        }
    }
    return New-GateYAuditEvidence COMPATIBILITY PASS $proofType ([pscustomobject][ordered]@{
        previousReleaseId = $previousReleaseId
        previousReleaseManifestSha256 = $previousManifestSha256
        targetReleaseId = [string]$Manifest.releaseId
        releaseManifestSha256 = $ManifestSha256
        targetSchemaVersion = [string]$Manifest.schema.targetVersion
        compatibilityDecision = $decision
        proofDigest = $proofDigest
    })
}

function Test-GateYBackupArtifact
{
    param(
        [Parameter(Mandatory = $true)][string]$ManifestSha256,
        [Parameter(Mandatory = $true)][string]$ExpectedFlywayVersion,
        [Parameter(Mandatory = $true)][string]$ExpectedDatabaseIdentity,
        [Parameter(Mandatory = $true)][string]$BackupArtifactPath
    )
    $path = Assert-GateYDisposableEvidencePath $BackupArtifactPath
    $metadata = Assert-GateYRegularFileIdentity $path
    $artifact = Read-GateYCanonicalEvidence $path
    Assert-GateYEvidenceFields $artifact @(
        'schemaVersion', 'databaseIdentity', 'flywaySourceVersion',
        'releaseManifestSha256', 'createdAt', 'backupTool', 'backupToolVersion', 'payload'
    )
    if ([string]$artifact.schemaVersion -cne 'gatey-disposable-backup.v1' -or
            [string]$artifact.databaseIdentity -cne $ExpectedDatabaseIdentity -or
            [string]$artifact.flywaySourceVersion -cne $ExpectedFlywayVersion -or
            [string]$artifact.releaseManifestSha256 -cne $ManifestSha256 -or
            [string]$artifact.createdAt -cnotmatch '^20[0-9]{2}-[01][0-9]-[0-3][0-9]T[0-2][0-9]:[0-5][0-9]:[0-5][0-9]Z$' -or
            [string]::IsNullOrWhiteSpace([string]$artifact.backupTool) -or
            [string]::IsNullOrWhiteSpace([string]$artifact.backupToolVersion) -or
            $null -eq $artifact.payload)
    {
        throw 'BLOCKED / BACKUP_ARTIFACT_VERIFICATION_FAILED'
    }
    $item = Get-Item -LiteralPath $path
    $owner = if (Test-GateYLinuxPlatform) {
        [string]$metadata.owner
    } else {
        'sha256:' + (Get-GateYReadonlySha256Text ([string](Get-Acl -LiteralPath $path).Owner))
    }
    $mode = if (Test-GateYLinuxPlatform) { '0' + [string]$metadata.mode } else { 'WINDOWS_ACL' }
    return New-GateYAuditEvidence BACKUP_VERIFICATION PASS `
        DISPOSABLE_BACKUP_ARTIFACT_VERIFICATION ([pscustomobject][ordered]@{
            databaseIdentity = [string]$artifact.databaseIdentity
            flywaySourceVersion = [string]$artifact.flywaySourceVersion
            backupArtifactSha256 = Get-GateYReadonlySha256File $path
            backupArtifactSize = [long]$item.Length
            backupCreatedAt = [string]$artifact.createdAt
            backupOwner = $owner
            backupMode = $mode
            backupTool = [string]$artifact.backupTool
            backupToolVersion = [string]$artifact.backupToolVersion
            releaseManifestSha256 = $ManifestSha256
        })
}

function Test-GateYRestoreEvidence
{
    param(
        [Parameter(Mandatory = $true)][string]$ManifestSha256,
        [Parameter(Mandatory = $true)]$BackupAuditEvidence,
        [Parameter(Mandatory = $true)][string]$ExpectedFlywayVersion,
        [Parameter(Mandatory = $true)][string]$BackupArtifactPath
    )
    Assert-GateYAuditEvidence $BackupAuditEvidence 'BACKUP_VERIFICATION'
    $path = Assert-GateYDisposableEvidencePath $BackupArtifactPath
    if ((Get-GateYReadonlySha256File $path) -cne [string]$BackupAuditEvidence.backupArtifactSha256)
    {
        throw 'BLOCKED / RESTORE_BACKUP_BINDING_INVALID'
    }
    $restoreRoot = Join-Path ([IO.Path]::GetTempPath()) ('nq-gatey-restore-' + [Guid]::NewGuid().ToString('N'))
    $startedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    try
    {
        [IO.Directory]::CreateDirectory($restoreRoot) | Out-Null
        $restoredPath = Join-Path $restoreRoot 'restored-backup.json'
        Copy-Item -LiteralPath $path -Destination $restoredPath
        $restored = Read-GateYCanonicalEvidence $restoredPath
        if ([string]$restored.schemaVersion -cne 'gatey-disposable-backup.v1' -or
                [string]$restored.releaseManifestSha256 -cne $ManifestSha256 -or
                [string]$restored.flywaySourceVersion -cne $ExpectedFlywayVersion -or
                (Get-GateYReadonlySha256File $restoredPath) -cne [string]$BackupAuditEvidence.backupArtifactSha256)
        {
            throw 'BLOCKED / RESTORE_EVIDENCE_VERIFICATION_FAILED'
        }
        return New-GateYAuditEvidence RESTORE_VERIFICATION PASS `
            DISPOSABLE_RESTORE_EXECUTION_VERIFICATION ([pscustomobject][ordered]@{
                backupArtifactSha256 = [string]$BackupAuditEvidence.backupArtifactSha256
                restoreTargetIdentity = 'disposable:' + [IO.Path]::GetFileName($restoreRoot)
                restoreStartedAt = $startedAt
                restoreCompletedAt = [DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
                restoreResult = 'PASS'
                restoredFlywayVersion = [string]$restored.flywaySourceVersion
                integrityChecks = @('canonical-backup', 'artifact-sha256', 'release-binding', 'flyway-version')
                releaseManifestSha256 = $ManifestSha256
            })
    }
    finally
    {
        if (Test-Path -LiteralPath $restoreRoot)
        {
            Remove-Item -LiteralPath $restoreRoot -Recurse -Force
        }
    }
}

function Test-GateYPostActivationHealth
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$ManifestSha256,
        [Parameter(Mandatory = $true)][string]$ReleaseRoot,
        [Parameter(Mandatory = $true)][string]$CurrentPointerPath
    )
    $release = Test-GateYReadonlyRelease $ReleaseRoot
    $currentMatches = $false
    if (Test-Path -LiteralPath $CurrentPointerPath)
    {
        $currentMatches = (Resolve-Path -LiteralPath $CurrentPointerPath).Path -ceq
            (Resolve-Path -LiteralPath $ReleaseRoot).Path
    }
    return New-GateYAuditEvidence HEALTH NOT_VERIFIED NO_LIVE_JVM_HEALTH_PROBE ([pscustomobject][ordered]@{
        releaseManifestSha256 = $ManifestSha256
        releaseId = [string]$release.releaseId
        releaseExists = $true
        installationVerificationPassed = $true
        currentPointsToExpectedRelease = $currentMatches
        expectedRuntimeIdentityMatches = ([string]$Manifest.application.profileIdentity -ceq $script:ProfileIdentity)
        requiredLocalHealthEvidenceExists = $false
        realHealthProbeExecuted = $false
    })
}

function Assert-GateYProductionAuthorizationContext
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$ManifestSha256,
        [Parameter(Mandatory = $true)][string]$ReleaseRoot
    )
    if (-not (Test-GateYLinuxPlatform))
    {
        throw 'BLOCKED / DEPLOYMENT_AUTHORIZATION_LINUX_REQUIRED'
    }
    $expectedRoot = '/opt/nexus-quant/releases/' + [string]$Manifest.releaseId
    $actualRoot = [IO.Path]::GetFullPath($ReleaseRoot).TrimEnd('/')
    if ($actualRoot -cne $expectedRoot)
    {
        throw 'BLOCKED / DEPLOYMENT_AUTHORIZATION_RELEASE_CONTEXT_INVALID'
    }
    $release = Test-GateYReadonlyRelease $actualRoot -RequirePosix
    if ([string]$release.releaseId -cne [string]$Manifest.releaseId -or
            [string]$release.manifestSha256 -cne $ManifestSha256)
    {
        throw 'BLOCKED / DEPLOYMENT_AUTHORIZATION_RELEASE_CONTEXT_INVALID'
    }
    return $release
}

function Assert-GateYRollbackContract
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$ManifestSha256,
        [Parameter(Mandatory = $true)]$ReleaseRoot,
        [AllowNull()]$LegacyCompatibilityEvidence,
        [AllowNull()]$LegacyBackupEvidence,
        [AllowNull()]$LegacyRestoreEvidence
    )
    if ($ReleaseRoot -isnot [string] -or $null -ne $LegacyCompatibilityEvidence -or
            $null -ne $LegacyBackupEvidence -or $null -ne $LegacyRestoreEvidence)
    {
        throw 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
    }
    $null = Assert-GateYProductionAuthorizationContext $Manifest $ManifestSha256 $ReleaseRoot
    $null = Test-GateYReleaseSchemaCompatibility $Manifest $ManifestSha256 '/opt/nexus-quant/current'
    throw 'BLOCKED / ROLLBACK_CURRENT_VERIFICATION_NOT_IMPLEMENTED'
}

function Invoke-GateYSyntheticRollbackAssessment
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$ManifestSha256,
        [Parameter(Mandatory = $true)][string]$FlywayObservationPath,
        [Parameter(Mandatory = $true)][string]$BackupArtifactPath
    )
    $flyway = Test-GateYFlywayHistoryObservation $Manifest $ManifestSha256 $FlywayObservationPath
    $migration = Get-GateYMigrationPlan $Manifest @($flyway.appliedFlywayVersions)
    $compatibility = Test-GateYReleaseSchemaCompatibility $Manifest $ManifestSha256
    $backup = Test-GateYBackupArtifact `
        $ManifestSha256 ([string]$migration.currentVersion) ([string]$flyway.databaseIdentity) $BackupArtifactPath
    $restore = Test-GateYRestoreEvidence `
        $ManifestSha256 $backup ([string]$migration.currentVersion) $BackupArtifactPath
    return [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_SYNTHETIC_ROLLBACK_ASSESSMENT'
        authorizationEligible = $false
        deploymentAcceptance = $false
        codeRollback = 'SYNTHETIC_REQUIRES_DATABASE_RECOVERY'
        databaseRecovery = 'SYNTHETIC_VERIFIED_BACKUP_AND_RESTORE'
        migration = $migration
        auditEvidence = @($flyway, $compatibility, $backup, $restore)
    }
}

function Assert-GateYHealthReceipt
{
    param(
        [Parameter(Mandatory = $true)]$AuditEvidence,
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$ManifestSha256
    )
    throw 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
}

function Assert-GateYPostActivationHealth
{
    param(
        [Parameter(Mandatory = $true)]$Manifest,
        [Parameter(Mandatory = $true)][string]$ManifestSha256,
        [Parameter(Mandatory = $true)][string]$ReleaseRoot,
        [Parameter(Mandatory = $true)][string]$CurrentPointerPath
    )
    $null = Assert-GateYProductionAuthorizationContext $Manifest $ManifestSha256 $ReleaseRoot
    if ($CurrentPointerPath -cne '/opt/nexus-quant/current' -or
            -not (Test-Path -LiteralPath $CurrentPointerPath) -or
            (Resolve-Path -LiteralPath $CurrentPointerPath).Path -cne
                (Resolve-Path -LiteralPath $ReleaseRoot).Path)
    {
        throw 'BLOCKED / QUALIFICATION_HEALTH_NOT_VERIFIED'
    }
    throw 'BLOCKED / QUALIFICATION_HEALTH_NOT_VERIFIED'
}

Export-ModuleMember -Function @(
    'Get-GateYReadonlySha256File',
    'Get-GateYReadonlySha256Text',
    'Get-GateYMigrationInventory',
    'Sort-GateYReleaseArtifacts',
    'New-GateYReadonlyReleaseManifest',
    'ConvertTo-GateYReadonlyCanonicalManifestJson',
    'Write-GateYReadonlyCanonicalManifest',
    'Test-GateYReadonlyRelease',
    'Assert-GateYRegularFileIdentity',
    'Get-GateYMigrationPlan',
    'Write-GateYAuditEvidence',
    'Read-GateYAuditEvidence',
    'Test-GateYFlywayHistoryObservation',
    'Test-GateYReleaseSchemaCompatibility',
    'Test-GateYBackupArtifact',
    'Test-GateYRestoreEvidence',
    'Test-GateYPostActivationHealth',
    'Invoke-GateYSyntheticRollbackAssessment',
    'Assert-GateYRollbackContract',
    'Assert-GateYPostActivationHealth',
    'Assert-GateYHealthReceipt'
)
