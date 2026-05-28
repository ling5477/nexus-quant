param(
    [switch]$SkipBackendBuild,
    [switch]$SkipFrontendBuild,
    [string]$ReleaseName = "nq-gatej-freeze-release"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

<#
GateJ-FREEZE release packager.

Why:
- The ECS host must not download Maven/npm dependencies or build application
  artifacts during freeze acceptance.
- The release package contains only runtime artifacts and deployment helpers.
- Generated jar/dist/zip output stays under release/ and must not be committed.
#>

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoRoot = (Resolve-Path (Join-Path $scriptDir "..")).Path
$releaseRoot = Join-Path $repoRoot "release"
$releaseDir = Join-Path $releaseRoot $ReleaseName
$zipPath = Join-Path $releaseRoot "$ReleaseName.zip"

if ($ReleaseName -notmatch '^[A-Za-z0-9._-]+$') {
    throw "ReleaseName may only contain letters, numbers, dot, underscore, and dash."
}

function Invoke-CheckedCommand {
    param(
        [Parameter(Mandatory = $true)]
        [string]$FilePath,
        [Parameter(Mandatory = $true)]
        [string[]]$ArgumentList,
        [Parameter(Mandatory = $true)]
        [string]$WorkingDirectory
    )

    $resolvedFilePath = $FilePath
    if ($IsWindows) {
        $cmdCommand = Get-Command "$FilePath.cmd" -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -ne $cmdCommand) {
            $resolvedFilePath = $cmdCommand.Source
        }
    }

    Write-Host "Running: $resolvedFilePath $($ArgumentList -join ' ')"
    $process = Start-Process -FilePath $resolvedFilePath -ArgumentList $ArgumentList -WorkingDirectory $WorkingDirectory -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Command failed with exit code $($process.ExitCode): $resolvedFilePath $($ArgumentList -join ' ')"
    }
}

function Copy-RequiredFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Source,
        [Parameter(Mandatory = $true)]
        [string]$Destination
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Leaf)) {
        throw "Required file not found: $Source"
    }
    $parent = Split-Path -Parent $Destination
    New-Item -ItemType Directory -Force -Path $parent | Out-Null
    Copy-Item -LiteralPath $Source -Destination $Destination -Force
}

function Copy-RequiredDirectory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Source,
        [Parameter(Mandatory = $true)]
        [string]$Destination
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
        throw "Required directory not found: $Source"
    }
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    Copy-Item -Path (Join-Path $Source "*") -Destination $Destination -Recurse -Force
}

Set-Location $repoRoot

if (-not $SkipBackendBuild) {
    Invoke-CheckedCommand -FilePath "mvn" -ArgumentList @("-f", "backend/pom.xml", "-pl", "nq-app", "-am", "package", "spring-boot:repackage", "-DskipTests") -WorkingDirectory $repoRoot
}

if (-not $SkipFrontendBuild) {
    Invoke-CheckedCommand -FilePath "npm" -ArgumentList @("run", "build") -WorkingDirectory (Join-Path $repoRoot "frontend")
}

$jarCandidates = @(Get-ChildItem -Path (Join-Path $repoRoot "backend/nq-app/target") -Filter "*.jar" -File |
    Where-Object { $_.Name -notlike "*.original" -and $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-javadoc.jar" } |
    Sort-Object LastWriteTime -Descending)

if ($jarCandidates.Count -eq 0) {
    throw "No runnable nq-app jar found under backend/nq-app/target. Run without -SkipBackendBuild first."
}

$distDir = Join-Path $repoRoot "frontend/dist"
if (-not (Test-Path -LiteralPath $distDir -PathType Container)) {
    throw "frontend/dist not found. Run without -SkipFrontendBuild first."
}

if (Test-Path -LiteralPath $releaseDir) {
    $resolvedReleaseDir = Resolve-Path $releaseDir
    $resolvedReleaseRoot = Resolve-Path $releaseRoot -ErrorAction SilentlyContinue
    if ($null -eq $resolvedReleaseRoot -or -not $resolvedReleaseDir.Path.StartsWith($resolvedReleaseRoot.Path, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to remove path outside release root: $resolvedReleaseDir"
    }
    Remove-Item -LiteralPath $releaseDir -Recurse -Force
}

New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null

Copy-RequiredFile -Source $jarCandidates[0].FullName -Destination (Join-Path $releaseDir "app/nq-app.jar")
Copy-RequiredDirectory -Source $distDir -Destination (Join-Path $releaseDir "frontend/dist")
Copy-RequiredFile -Source (Join-Path $repoRoot "deploy/docker-compose.freeze.yml") -Destination (Join-Path $releaseDir "docker-compose.freeze.yml")
Copy-RequiredFile -Source (Join-Path $repoRoot "deploy/nginx/default.conf") -Destination (Join-Path $releaseDir "nginx/default.conf")
Copy-RequiredFile -Source (Join-Path $repoRoot "deploy/.env.freeze.example") -Destination (Join-Path $releaseDir ".env.freeze.example")

$scriptNames = @(
    "deploy-freeze.sh",
    "seed-freeze-user.sh",
    "health-check.sh",
    "backup-db.sh",
    "freeze-health-loop.sh"
)

foreach ($scriptName in $scriptNames) {
    Copy-RequiredFile -Source (Join-Path $repoRoot "scripts/$scriptName") -Destination (Join-Path $releaseDir "scripts/$scriptName")
}

$gitCommit = (git rev-parse HEAD).Trim()
$gitBranch = (git rev-parse --abbrev-ref HEAD).Trim()
$buildTime = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")

$releaseInfo = @"
# GateJ-FREEZE Release Info

- Release name: $ReleaseName
- Build time: $buildTime
- Git branch: $gitBranch
- Git commit: $gitCommit
- Backend jar source: $($jarCandidates[0].FullName)
- Frontend dist source: $distDir

## Boundary

- AI disabled.
- DH disabled.
- REAL/LIVE trading disabled.
- OKX/Binance recovery disabled.
- No Java business code, React business code, API, or migration is generated by this package.

## Server Entry

1. Copy the extracted package to `/opt/nexus-quant`.
2. Copy `.env.freeze.example` to `.env.freeze` and fill placeholders outside Git.
3. Do not `source .env.freeze`; if the freeze password contains shell metacharacters, leave the password placeholder and enter it interactively in the seed script.
4. Run `chmod +x scripts/*.sh`.
5. Run `./scripts/deploy-freeze.sh`.
6. Run `./scripts/seed-freeze-user.sh`.
7. Verify login with `curl` and a browser before starting freeze acceptance.
8. Run `./scripts/health-check.sh`.
"@

Set-Content -LiteralPath (Join-Path $releaseDir "RELEASE_INFO.md") -Value $releaseInfo -Encoding UTF8

if (Test-Path -LiteralPath $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}

$zipItems = Get-ChildItem -LiteralPath $releaseDir -Force | ForEach-Object { $_.FullName }
Compress-Archive -LiteralPath $zipItems -DestinationPath $zipPath -Force

Write-Host "Release directory: $releaseDir"
Write-Host "Release zip: $zipPath"
