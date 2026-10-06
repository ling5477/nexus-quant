[CmdletBinding()]
param([Parameter(Mandatory)][string]$OutputDirectory, [ValidateSet('amd64','arm64')][string[]]$Architectures = @('amd64','arm64'))
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$version = (Get-Content (Join-Path $root 'VERSION') -Raw).Trim()
if (Test-Path -LiteralPath $OutputDirectory) { throw 'Output directory must not exist' }
[void](New-Item -ItemType Directory -Path $OutputDirectory)
Copy-Item (Join-Path $root 'VERSION') $OutputDirectory
foreach ($name in @('runtime','installers')) { Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination $OutputDirectory -Recurse }
foreach ($name in @('README.md','INSTALL.md','CHANGELOG.md')) { Copy-Item -LiteralPath (Join-Path $root $name) -Destination $OutputDirectory }
foreach ($arch in $Architectures) {
    foreach ($component in @('backend','frontend')) {
        docker buildx build --platform "linux/$arch" --load -f (Join-Path $PSScriptRoot "docker/$component.Dockerfile") -t "nexusquant/${component}:$version" $root
        if ($LASTEXITCODE -ne 0) { throw 'Image build failed' }
    }
    docker pull --platform "linux/$arch" postgres:16.15
    if ($LASTEXITCODE -ne 0) { throw 'PG16 image pull failed' }
    $archive = Join-Path $OutputDirectory "images-$arch.tar"
    docker save -o $archive "nexusquant/backend:$version" "nexusquant/frontend:$version" postgres:16.15
    if ($LASTEXITCODE -ne 0) { throw 'Image export failed' }
    [IO.File]::WriteAllText(($archive + '.sha256'), (Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant())
}
Write-Host "Prebuilt package: $OutputDirectory (LICENSE pending; final release blocked)"
