#requires -Version 7.0
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
    $start.FileName = (Get-Command docker -CommandType Application -ErrorAction Stop).Source
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
    # config digest 在两种 Docker image store 中都保留；OCI index digest 仅作为独立审计字段。
    return [ordered]@{tag=$Tag; imageConfigDigest=$image.Id; repoDigests=@($image.RepoDigests); os=$image.Os; architecture=$image.Architecture}
}
function Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Write-Lf([string]$Path, [string]$Text) { [IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false)) }

# 构建只能使用本机引擎，防止环境变量或远程 context 将资格制品操作发往共享服务。
$dockerEndpoint = $env:DOCKER_HOST
if (-not $dockerEndpoint) {
    $dockerContext = Invoke-PackageDocker @('context','show') 30
    $dockerEndpoint = Invoke-PackageDocker @('context','inspect',$dockerContext,'--format','{{.Endpoints.docker.Host}}') 30
}
if ($dockerEndpoint -cnotmatch '^(npipe:////\./pipe/(docker_engine|dockerDesktopLinuxEngine)$|unix:///)') { throw 'Package build requires a local Docker endpoint' }
[void](Invoke-PackageDocker @('info','--format','{{.ServerVersion}}') 30)
[void](New-Item -ItemType Directory -Path $output)
Copy-Item -LiteralPath (Join-Path $root 'VERSION') -Destination $output
foreach ($name in @('runtime','installers')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $output -Recurse }
foreach ($name in @('README.md','INSTALL.md','CHANGELOG.md')) { Copy-Item -LiteralPath (Join-Path $root $name) -Destination $output }
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
    $archiveHash = Hash $archive
    Write-Lf ($archive + '.sha256') ($archiveHash + "`n")
    $manifest = [ordered]@{
        FORMAT='1'; VERSION=$version; SOURCE_SHA=$identity.sourceCommit
        RELEASE_SOURCE_HASH=$identity.releaseTreeSha256; SCHEMA_VERSION=[string]$schemaVersion
        ARCH=$arch; ARCHIVE=$archiveName; ARCHIVE_SHA256=$archiveHash
        BACKEND_IMAGE=$refs.backend.imageConfigDigest; FRONTEND_IMAGE=$refs.frontend.imageConfigDigest; POSTGRES_IMAGE=$refs.postgres.imageConfigDigest
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
