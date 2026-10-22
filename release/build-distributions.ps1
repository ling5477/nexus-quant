#requires -Version 7.4
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$OutputDirectory,
    [ValidateSet('NQ_SELF_HOSTED','NQ_PUBLIC_DEMO')][string[]]$Distributions = @('NQ_SELF_HOSTED','NQ_PUBLIC_DEMO'),
    [ValidateSet('amd64','arm64')][string]$Architecture = 'amd64'
)
$ErrorActionPreference = 'Stop'
$sourceRoot = Split-Path $PSScriptRoot -Parent
$outputPath = [IO.Path]::GetFullPath($OutputDirectory)
if (Test-Path -LiteralPath $outputPath) { throw 'Output directory must not exist' }
if (@($Distributions | Select-Object -Unique).Count -ne $Distributions.Count) { throw 'Duplicate distribution' }

# 此入口仅构建本机镜像，不启动容器，不生成正式发布包或授予部署资格。
function Invoke-LocalDocker([string[]]$Arguments, [int]$TimeoutSeconds = 1800) {
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = (Get-Command docker -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source
    $start.UseShellExecute = $false; $start.CreateNoWindow = $true
    $start.RedirectStandardOutput = $true; $start.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $start.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $start
    try {
        [void]$process.Start()
        $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) { $process.Kill($true); throw 'Distribution build timed out' }
        $process.WaitForExit()
        if ($process.ExitCode -ne 0) { throw ('Distribution Docker operation failed: ' + $Arguments[0] + ': ' + $stderr.Result) }
        return $stdout.Result.Trim()
    } finally { $process.Dispose() }
}
function Source-Hash {
    $paths = @(git -C $sourceRoot ls-files -c -o --exclude-standard -- backend frontend release/docker .dockerignore THIRD_PARTY_NOTICES.md DISCLAIMER.md)
    if ($LASTEXITCODE -ne 0) { throw 'Build source inventory unavailable' }
    $lines = foreach ($path in ($paths | Sort-Object -Unique)) {
        $fullPath = Join-Path $sourceRoot $path
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) { throw 'Build source path missing' }
        if ((Get-Item -LiteralPath $fullPath).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Build source links unsupported' }
        $path + ':' + (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant()
    }
    [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes(($lines -join "`n") + "`n"))).ToLowerInvariant()
}
$endpoint = $env:DOCKER_HOST
if ($env:DOCKER_CONTEXT -or -not $endpoint) {
    $context = Invoke-LocalDocker @('context','show') 30
    $endpoint = Invoke-LocalDocker @('context','inspect',$context,'--format','{{.Endpoints.docker.Host}}') 30
}
if ($endpoint -cnotmatch '^(npipe:////\./pipe/(docker_engine|dockerDesktopLinuxEngine)$|unix:///)') { throw 'Local Docker endpoint required' }
$startingHead = (git -C $sourceRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0 -or $startingHead -notmatch '^[a-f0-9]{40}$') { throw 'Source commit unavailable' }
$dirtyPaths = @(git -C $sourceRoot status --porcelain=v1)
if ($LASTEXITCODE -ne 0) { throw 'Source status unavailable' }
$buildId = [guid]::NewGuid().ToString('N')
$sourceHash = Source-Hash
[void](New-Item -ItemType Directory -Path $outputPath)
$records = [Collections.Generic.List[object]]::new()
foreach ($distribution in $Distributions) {
    $images = [ordered]@{}
    foreach ($component in @('backend','frontend')) {
        $tag = "nexusquant/$component`:$($distribution.ToLowerInvariant())-$buildId"
        [void](Invoke-LocalDocker @('buildx','build','--platform',"linux/$Architecture",'--load',
            '--build-arg',"NQ_DISTRIBUTION=$distribution",'--label',"org.opencontainers.image.revision=$startingHead",
            '-f',(Join-Path $PSScriptRoot "docker/$component.Dockerfile"),'-t',$tag,$sourceRoot))
        $image = @(Invoke-LocalDocker @('image','inspect',$tag) 30 | ConvertFrom-Json)[0]
        if ($image.Config.Labels.'com.nexusquant.distribution' -cne $distribution -or $image.Architecture -cne $Architecture) {
            throw 'Built image distribution or architecture mismatch'
        }
        $images[$component] = [ordered]@{tag=$tag; imageId=$image.Id; distribution=$distribution}
    }
    $records.Add([ordered]@{distribution=$distribution; images=$images})
}
if ((git -C $sourceRoot rev-parse HEAD).Trim() -cne $startingHead) { throw 'Source commit changed during build' }
if ((Source-Hash) -cne $sourceHash) { throw 'Build sources changed during build' }
$metadata = [ordered]@{
    sourceCommit=$startingHead; sourceDirty=($dirtyPaths.Count -gt 0); sourceSha256=$sourceHash; buildId=$buildId
    architecture=$Architecture; artifacts=$records; publicDeployment='NOT_READY'; publicEmailDelivery='NOT_QUALIFIED'
}
[IO.File]::WriteAllText((Join-Path $outputPath 'distribution-images.json'),($metadata | ConvertTo-Json -Depth 8)+"`n",[Text.UTF8Encoding]::new($false))
Write-Host ('Paired distribution image identities: ' + (Join-Path $outputPath 'distribution-images.json'))
