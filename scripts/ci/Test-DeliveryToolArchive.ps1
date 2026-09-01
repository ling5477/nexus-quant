[CmdletBinding()]
param(
    [string] $SupplyChainLockPath = 'scripts/ci/delivery-supply-chain-lock.json',
    [Parameter(Mandatory = $true)][string] $ToolName,
    [Parameter(Mandatory = $true)][string] $Platform,
    [Parameter(Mandatory = $true)][string] $Architecture,
    [Parameter(Mandatory = $true)][string] $ArchivePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

try {
    $lock = Get-Content -LiteralPath $SupplyChainLockPath -Raw -Encoding UTF8 | ConvertFrom-Json
} catch {
    throw "Invalid supply-chain lock JSON: $($_.Exception.Message)"
}
if ([string]$lock.schemaVersion -cne 'nq-delivery-supply-chain-lock-v1') {
    throw 'Unsupported supply-chain lock schema'
}
$matches = @($lock.tools | Where-Object {
    [string]$_.name -ceq $ToolName -and
    [string]$_.platform -ceq $Platform -and
    [string]$_.architecture -ceq $Architecture -and
    [string]$_.usage -ceq 'ACTIVE_CI'
})
if ($matches.Count -ne 1) {
    throw "Expected one active tool identity: $ToolName/$Platform/$Architecture; found=$($matches.Count)"
}
$tool = $matches[0]
$resolvedArchive = (Resolve-Path -LiteralPath $ArchivePath).Path
$item = Get-Item -LiteralPath $resolvedArchive -Force
if ($item.PSIsContainer -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
    throw 'Tool archive must be a regular file'
}
if ($item.Name -cne [string]$tool.artifact) {
    throw "Tool archive identity mismatch: actual=$($item.Name) expected=$($tool.artifact)"
}
$actualSha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $resolvedArchive).Hash.ToLowerInvariant()
if ($actualSha256 -cne [string]$tool.sha256) {
    throw "Tool archive checksum mismatch: name=$ToolName"
}
Write-Output "DELIVERY_TOOL_ARCHIVE=PASS name=$ToolName version=$($tool.version) sha256=$actualSha256"
