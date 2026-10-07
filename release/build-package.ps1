#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateSet('amd64','arm64')][string[]]$Architectures = @('amd64','arm64')
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$output = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $output) { throw 'Output directory must not exist' }
if ($output.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Package output must be outside the exported source' }
if (@($Architectures | Select-Object -Unique).Count -ne $Architectures.Count) { throw 'Duplicate architecture' }
$version = (Get-Content -LiteralPath (Join-Path $root 'VERSION') -Raw).Trim()
if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid VERSION' }

# 只从冻结导出构建；工作区、测试目录或未纳入清单的源码不能成为隐式镜像输入。
$identityPath = Join-Path $root '.release-source.json'
if (-not (Test-Path -LiteralPath $identityPath) -or (Test-Path -LiteralPath (Join-Path $root '.git'))) { throw 'Build from a verified immutable release-source export' }
$identity = Get-Content -LiteralPath $identityPath -Raw | ConvertFrom-Json
if ($identity.sourceCommit -notmatch '^[a-f0-9]{40}$' -or $identity.releaseTreeSha256 -notmatch '^[a-f0-9]{64}$' -or $identity.targetVersion -ne $version) { throw 'Invalid release-source identity' }
$expectedFiles = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
[void]$expectedFiles.Add('.release-source.json')
foreach ($entry in $identity.files) { if (-not $expectedFiles.Add($entry.path)) { throw 'Duplicate source path' } }
foreach ($item in (Get-ChildItem -LiteralPath $root -Recurse -Force)) {
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Export symlink unsupported' }
    if (-not $item.PSIsContainer) {
        $relative = $item.FullName.Substring($root.Length + 1).Replace('\','/')
        if (-not $expectedFiles.Contains($relative)) { throw 'Unexpected file in source export; export a clean candidate before package build' }
    }
}
foreach ($entry in $identity.files) {
    $path = [IO.Path]::GetFullPath((Join-Path $root $entry.path))
    if (-not $path.StartsWith($root + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe export path' }
    $item = Get-Item -LiteralPath $path
    if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Export symlink unsupported' }
    if ((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256) { throw 'Export source changed before package build' }
}
$schemaVersions = @($identity.files | Where-Object { $_.path -match '/db/migration/V(\d+)__[^/]+\.sql$' } | ForEach-Object { [int]([regex]::Match($_.path,'/db/migration/V(\d+)__').Groups[1].Value) })
if ($schemaVersions.Count -eq 0) { throw 'No declared release schema' }
$schemaVersion = ($schemaVersions | Measure-Object -Maximum).Maximum

function Invoke-PackageDocker([string[]]$Arguments, [int]$TimeoutSeconds = 1800) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = (Get-Command docker -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true
    $start.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $start
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync()
        $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) { $process.Kill($true); throw 'Package Docker operation timed out' }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw ('Package Docker stage failed: ' + $Arguments[0]) }
        return $stdout.Result.Trim()
    } finally { $process.Dispose() }
}
function Image-Identity([string]$Tag, [string]$Architecture) {
    $image = @(Invoke-PackageDocker @('image','inspect',$Tag) 30 | ConvertFrom-Json)[0]
    if ($image.Os -ne 'linux' -or $image.Architecture -ne $Architecture) { throw 'Built image platform mismatch' }
    if ($image.Id -notmatch '^sha256:[a-f0-9]{64}$') { throw 'Immutable image configuration digest unavailable' }
    return [ordered]@{tag=$Tag; nativeImageId=$image.Id; repoDigests=@($image.RepoDigests); os=$image.Os; architecture=$image.Architecture}
}
function Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Write-Lf([string]$Path, [string]$Text) { [IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false)) }
function Read-ArchiveFiles([string]$Path, [string[]]$Names) {
    # 只读取有界 JSON 元数据，不解压镜像层或将 archive 路径写入文件系统。
    $wanted = [Collections.Generic.HashSet[string]]::new($Names,[StringComparer]::Ordinal)
    $found = @{}; $count=0
    $stream = [IO.File]::OpenRead($Path)
    $reader = [System.Formats.Tar.TarReader]::new($stream,$true)
    try {
        while ($entry=$reader.GetNextEntry($false)) {
            if ((++$count) -gt 20000) { throw 'Image archive entry limit exceeded' }
            if (-not $wanted.Contains($entry.Name)) { continue }
            if ($found.ContainsKey($entry.Name) -or $entry.Length -le 0 -or $entry.Length -gt 1048576 -or $entry.EntryType -notin @([System.Formats.Tar.TarEntryType]::RegularFile,[System.Formats.Tar.TarEntryType]::V7RegularFile)) { throw 'Invalid or duplicate image archive metadata' }
            $buffer=[IO.MemoryStream]::new()
            try { $entry.DataStream.CopyTo($buffer); $found[$entry.Name]=$buffer.ToArray() } finally { $buffer.Dispose() }
        }
        foreach ($name in $Names) { if (-not $found.ContainsKey($name)) { throw ('Required OCI/Docker image archive metadata unavailable: '+$name+'; package builder requires containerd OCI image store') } }
        return $found
    } finally { $reader.Dispose(); $stream.Dispose() }
}
function Bytes-Digest([byte[]]$Bytes) { 'sha256:' + [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($Bytes)).ToLowerInvariant() }
function Archive-ImageIdentities([string]$Path, [hashtable]$Refs, [string]$Architecture) {
    $rootMetadata=Read-ArchiveFiles $Path @('manifest.json','index.json')
    $legacy=@([Text.Encoding]::UTF8.GetString($rootMetadata['manifest.json']) | ConvertFrom-Json)
    $index=[Text.Encoding]::UTF8.GetString($rootMetadata['index.json']) | ConvertFrom-Json
    if ($legacy.Count -ne 3 -or $index.schemaVersion -ne 2) { throw 'Unexpected OCI/Docker archive layout' }
    $entries=@{}; $names=[Collections.Generic.List[string]]::new()
    foreach ($component in @('backend','frontend','postgres')) {
        $reference=$Refs[$component]
        $archiveMatches=@($legacy | Where-Object { $reference.tag -cin $_.RepoTags })
        if ($archiveMatches.Count -ne 1 -or $archiveMatches[0].Config -cnotmatch '^blobs/sha256/[a-f0-9]{64}$') { throw 'Image archive config identity unavailable' }
        $native=@($index.manifests | Where-Object { $_.digest -ceq $reference.nativeImageId })
        if ($native.Count -ne 1 -or $native[0].mediaType -cne 'application/vnd.oci.image.index.v1+json') { throw 'Package builder requires native OCI index plus Docker-compatible archive' }
        $entries[$component]=$archiveMatches[0]
        $names.Add($archiveMatches[0].Config); $names.Add('blobs/sha256/'+$reference.nativeImageId.Substring(7))
    }
    $blobs=Read-ArchiveFiles $Path $names.ToArray()
    $platformDescriptors=@{}; $platformNames=[Collections.Generic.List[string]]::new()
    foreach ($component in @('backend','frontend','postgres')) {
        $reference=$Refs[$component]; $configPath=$entries[$component].Config
        $configDigest=Bytes-Digest $blobs[$configPath]
        if ($configDigest -cne ('sha256:'+$configPath.Substring(13))) { throw 'Archive config content digest mismatch' }
        $config=[Text.Encoding]::UTF8.GetString($blobs[$configPath]) | ConvertFrom-Json
        if ($config.os -cne 'linux' -or $config.architecture -cne $Architecture) { throw 'Archive config platform mismatch' }
        $nativeBytes=$blobs['blobs/sha256/'+$reference.nativeImageId.Substring(7)]
        if ((Bytes-Digest $nativeBytes) -cne $reference.nativeImageId) { throw 'Archive native index content digest mismatch' }
        $nativeIndex=[Text.Encoding]::UTF8.GetString($nativeBytes) | ConvertFrom-Json
        $descriptors=@($nativeIndex.manifests | Where-Object { $_.platform.os -ceq 'linux' -and $_.platform.architecture -ceq $Architecture })
        if ($descriptors.Count -ne 1 -or $descriptors[0].digest -cnotmatch '^sha256:[a-f0-9]{64}$') { throw 'Archive target platform manifest unavailable' }
        $reference['configDigest']=$configDigest
        $platformDescriptors[$component]=$descriptors[0]
        $platformNames.Add('blobs/sha256/'+$descriptors[0].digest.Substring(7))
    }
    $platformBlobs=Read-ArchiveFiles $Path $platformNames.ToArray()
    foreach ($component in @('backend','frontend','postgres')) {
        $descriptor=$platformDescriptors[$component]
        $bytes=$platformBlobs['blobs/sha256/'+$descriptor.digest.Substring(7)]
        if ((Bytes-Digest $bytes) -cne $descriptor.digest) { throw 'Archive platform manifest digest mismatch' }
        $platform=[Text.Encoding]::UTF8.GetString($bytes) | ConvertFrom-Json
        if ($platform.config.digest -cne $Refs[$component].configDigest) { throw 'Archive native index/config binding mismatch' }
        $expectedLayers=@($platform.layers | ForEach-Object { 'blobs/sha256/'+$_.digest.Substring(7) })
        if (($expectedLayers -join '|') -cne (@($entries[$component].Layers) -join '|')) { throw 'Archive OCI/Docker layer binding mismatch' }
    }
}

# 构建只能使用本机引擎，防止环境变量或远程 context 将资格制品操作发往共享服务。
$dockerEndpoint = $env:DOCKER_HOST
if ($env:DOCKER_CONTEXT -or -not $dockerEndpoint) {
    $dockerContext = Invoke-PackageDocker @('context','show') 30
    $dockerEndpoint = Invoke-PackageDocker @('context','inspect',$dockerContext,'--format','{{.Endpoints.docker.Host}}') 30
}
if ($dockerEndpoint -cnotmatch '^(npipe:////\./pipe/(docker_engine|dockerDesktopLinuxEngine)$|unix:///)') { throw 'Package build requires a local Docker endpoint' }
[void](Invoke-PackageDocker @('info','--format','{{.ServerVersion}}') 30)
[void](New-Item -ItemType Directory -Path $output)
Copy-Item -LiteralPath (Join-Path $root 'VERSION') -Destination $output
foreach ($name in @('runtime','installers')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $output -Recurse }
foreach ($name in @('README.md','INSTALL.md','CHANGELOG.md','LICENSE','DISCLAIMER.md')) { Copy-Item -LiteralPath (Join-Path $root $name) -Destination $output }
foreach ($arch in $Architectures) {
    $refs = @{}
    foreach ($component in @('backend','frontend')) {
        Write-Host ('Building ' + $component + ' linux/' + $arch)
        [void](Invoke-PackageDocker @('buildx','build','--platform',"linux/$arch",'--load','-f',(Join-Path $PSScriptRoot "docker/$component.Dockerfile"),'-t',"nexusquant/${component}:$version",$root))
        $refs[$component] = Image-Identity "nexusquant/${component}:$version" $arch
    }
    [void](Invoke-PackageDocker @('pull','--platform',"linux/$arch",'postgres:16.15') 600)
    $refs.postgres = Image-Identity 'postgres:16.15' $arch
    $archiveName = "images-$arch.tar"
    $archive = Join-Path $output $archiveName
    [void](Invoke-PackageDocker @('save','-o',$archive,"nexusquant/backend:$version","nexusquant/frontend:$version",'postgres:16.15') 600)
    Archive-ImageIdentities $archive $refs $arch
    $archiveHash = Hash $archive
    Write-Lf ($archive + '.sha256') ($archiveHash + "`n")
    $manifest = [ordered]@{
        FORMAT='1'; VERSION=$version; SOURCE_SHA=$identity.sourceCommit
        RELEASE_SOURCE_HASH=$identity.releaseTreeSha256; SCHEMA_VERSION=[string]$schemaVersion
        ARCH=$arch; ARCHIVE=$archiveName; ARCHIVE_SHA256=$archiveHash
        BACKEND_IMAGE=$refs.backend.nativeImageId; FRONTEND_IMAGE=$refs.frontend.nativeImageId; POSTGRES_IMAGE=$refs.postgres.nativeImageId
        BACKEND_CONFIG_DIGEST=$refs.backend.configDigest; FRONTEND_CONFIG_DIGEST=$refs.frontend.configDigest; POSTGRES_CONFIG_DIGEST=$refs.postgres.configDigest
        COMPOSE_SHA256=(Hash (Join-Path $output 'runtime/compose.yml'))
        RUNTIME_SHA256=(Hash (Join-Path $output 'runtime/runtime.yml'))
        PS_INSTALLER_SHA256=(Hash (Join-Path $output 'installers/nexusquant.ps1'))
        SH_INSTALLER_SHA256=(Hash (Join-Path $output 'installers/nexusquant.sh'))
    }
    $manifestPath = Join-Path $output "package-$arch.env"
    Write-Lf $manifestPath ((($manifest.GetEnumerator() | ForEach-Object { $_.Key + '=' + $_.Value }) -join "`n") + "`n")
    Write-Lf ($manifestPath + '.sha256') ((Hash $manifestPath) + "`n")
    Write-Lf (Join-Path $output "package-$arch.images.json") (($refs | ConvertTo-Json -Depth 6) + "`n")
    Write-Host ('Manifest identity ' + $arch + ': ' + (Hash $manifestPath))
}
Write-Host ('Prebuilt qualification package: ' + $output + ' (final release and LICENSE pending)')
