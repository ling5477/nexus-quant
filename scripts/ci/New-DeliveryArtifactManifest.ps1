[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $ArtifactPath,

    [Parameter(Mandatory = $true)]
    [string] $RepositoryRoot,

    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[a-z0-9][a-z0-9-]{2,63}$')]
    [string] $ArtifactSetName,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-WithinRoot {
    param([string] $Root, [string] $Target)

    $rootPrefix = $Root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar
    $comparison = if ($env:OS -eq 'Windows_NT') { [StringComparison]::OrdinalIgnoreCase } else { [StringComparison]::Ordinal }
    if ($Target -cne $Root -and -not $Target.StartsWith($rootPrefix, $comparison)) {
        throw "Artifact path escapes repository root: $Target"
    }
}

function Get-Sha256Text {
    param([string] $Text)
    $bytes = [Text.Encoding]::UTF8.GetBytes($Text)
    $algorithm = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($algorithm.ComputeHash($bytes)) -replace '-', '').ToLowerInvariant() }
    finally { $algorithm.Dispose() }
}

function Get-RelativePath {
    param([string] $Root, [string] $Target)
    if (@([IO.Path].GetMethods() | Where-Object Name -eq 'GetRelativePath').Count -gt 0) {
        return [IO.Path]::GetRelativePath($Root, $Target)
    }
    $rootUri = New-Object Uri(($Root.TrimEnd([IO.Path]::DirectorySeparatorChar) + [IO.Path]::DirectorySeparatorChar))
    $targetUri = New-Object Uri($Target)
    return [Uri]::UnescapeDataString($rootUri.MakeRelativeUri($targetUri).ToString()).Replace('/', '\')
}

function Write-Utf8LfJson {
    param([string] $Path, [object] $Value)
    $parent = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $json = ($Value | ConvertTo-Json -Depth 20).Replace("`r`n", "`n").TrimEnd() + "`n"
    [IO.File]::WriteAllText($Path, $json, (New-Object Text.UTF8Encoding($false)))
}

$root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
$target = (Resolve-Path -LiteralPath $ArtifactPath).Path
Assert-WithinRoot -Root $root -Target $target

$targetItem = Get-Item -LiteralPath $target -Force
if (($targetItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw 'Artifact root must not be a link or reparse point'
}
$items = @(
    if ($targetItem.PSIsContainer) {
        Get-ChildItem -LiteralPath $target -File -Recurse -Force
    } else {
        $targetItem
    }
)
if ($items.Count -eq 0) { throw 'Artifact set must contain at least one file' }

$entries = @()
foreach ($item in $items) {
    if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
        throw "Artifact file must not be a link or reparse point: $($item.FullName)"
    }
    Assert-WithinRoot -Root $root -Target $item.FullName
    $relative = (Get-RelativePath $root $item.FullName).Replace('\', '/')
    if ($relative -match '(^|/)\.\.(/|$)' -or [IO.Path]::IsPathRooted($relative)) {
        throw "Artifact relative path is unsafe: $relative"
    }
    $entries += [pscustomobject][ordered]@{
        relativePath = $relative
        size = [long]$item.Length
        sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $item.FullName).Hash.ToLowerInvariant()
    }
}
$entries = @($entries | Sort-Object relativePath)
$projection = ($entries | ForEach-Object { '{0}|{1}|{2}' -f $_.relativePath, $_.size, $_.sha256 }) -join "`n"
$manifest = [ordered]@{
    schemaVersion = 'nq-delivery-artifact-manifest-v1'
    artifactSetName = $ArtifactSetName
    fileCount = $entries.Count
    aggregateSha256 = Get-Sha256Text ($projection + "`n")
    files = $entries
}
Write-Utf8LfJson -Path $OutputPath -Value $manifest
Write-Output "ARTIFACT_MANIFEST name=$ArtifactSetName files=$($entries.Count) aggregateSha256=$($manifest.aggregateSha256)"
