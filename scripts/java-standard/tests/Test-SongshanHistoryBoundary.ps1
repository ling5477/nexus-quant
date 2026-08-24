[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$standardRoot = Join-Path $repoRoot 'docs\standards\java'
$currentSongshanPath = Join-Path $standardRoot 'alibaba-songshan-rule-mapping.yaml'
$historySongshanPath = Join-Path $standardRoot 'history\alibaba-songshan-rule-mapping.yaml'
$huangshanPath = Join-Path $standardRoot 'alibaba-huangshan-rule-mapping.yaml'
$verifierPath = Join-Path $repoRoot 'scripts\java-standard\verify-java-engineering-standard.ps1'
$readmePath = Join-Path $standardRoot 'README.md'
$historyPath = Join-Path $standardRoot 'source-history.json'

function Assert-Condition([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "SONGSHAN_HISTORY_BOUNDARY_FAILED: $Message" }
}

Assert-Condition (-not (Test-Path -LiteralPath $currentSongshanPath)) 'Songshan mapping remains in current standards root'
Assert-Condition (Test-Path -LiteralPath $historySongshanPath -PathType Leaf) 'Songshan history mapping is missing'
$historySha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $historySongshanPath).Hash.ToLowerInvariant()
$historyBlob = (& git -C $repoRoot hash-object -- $historySongshanPath).Trim()
Assert-Condition ($historySha256 -eq 'b246db0a3119b1d10c43b8ca876fcd3c7e6dea7b7b9c3d8563e590f08edaa0aa') 'Songshan history content changed'
Assert-Condition ($historyBlob -eq 'b3df1f6d1cb4114c230b20e9df1b5b515d94e419') 'Songshan history Git blob changed'
$songshanText = Get-Content -Raw -Encoding UTF8 -LiteralPath $historySongshanPath
Assert-Condition ($songshanText -match '(?m)^status:\s*"SUPERSEDED"\s*$') 'Songshan history status is not SUPERSEDED'

$huangshanBlob = (& git -C $repoRoot hash-object -- $huangshanPath).Trim()
Assert-Condition ($huangshanBlob -eq '7cefee190c885a11e3e9e92d2926f8f564eac55d') 'Huangshan mapping Git blob changed'

$verifierText = Get-Content -Raw -Encoding UTF8 -LiteralPath $verifierPath
Assert-Condition (-not $verifierText.Contains('alibaba-songshan-rule-mapping.yaml')) 'current verifier still names Songshan mapping as an input'
$readmeText = Get-Content -Raw -Encoding UTF8 -LiteralPath $readmePath
Assert-Condition ($readmeText.Contains('history/alibaba-songshan-rule-mapping.yaml')) 'standards README does not expose history location'
$sourceHistory = Get-Content -Raw -Encoding UTF8 -LiteralPath $historyPath | ConvertFrom-Json
$songshan = @($sourceHistory.lineage | Where-Object { $_.edition -eq 'Songshan' })
$huangshan = @($sourceHistory.lineage | Where-Object { $_.edition -eq 'Huangshan' })
Assert-Condition ($songshan.Count -eq 1 -and $songshan[0].status -eq 'SUPERSEDED') 'Songshan provenance is not historical'
Assert-Condition ($huangshan.Count -eq 1 -and $huangshan[0].status -eq 'CURRENT_EXTERNAL_REFERENCE') 'Huangshan is not the unique current external reference'

Write-Output 'SONGSHAN_HISTORY_BOUNDARY_TEST=PASS'
Write-Output 'CURRENT_ACTIVE_SONGSHAN_INPUT_COUNT=0'
Write-Output 'SONGSHAN_MAPPING_STATUS=HISTORY_ONLY'
Write-Output "SONGSHAN_HISTORY_SHA256=$historySha256"
Write-Output "SONGSHAN_HISTORY_BLOB=$historyBlob"
