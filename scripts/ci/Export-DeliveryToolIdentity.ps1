[CmdletBinding()]
param(
    [string] $SupplyChainLockPath = 'scripts/ci/delivery-supply-chain-lock.json',
    [Parameter(Mandatory = $true)][string] $ToolName,
    [Parameter(Mandatory = $true)][string] $Platform,
    [Parameter(Mandatory = $true)][string] $Architecture,
    [Parameter(Mandatory = $true)][string] $GitHubEnvPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-RequiredText([object] $Object, [string] $Name, [string] $Context) {
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or [string]::IsNullOrWhiteSpace([string]$property.Value)) {
        throw "Missing required field: $Context.$Name"
    }
    return [string]$property.Value
}

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
$version = Get-RequiredText $tool 'version' "tools[$ToolName]"
$artifact = Get-RequiredText $tool 'artifact' "tools[$ToolName]"
$sha256 = Get-RequiredText $tool 'sha256' "tools[$ToolName]"
$downloadUrl = Get-RequiredText $tool 'downloadUrl' "tools[$ToolName]"
$checksumSource = Get-RequiredText $tool 'checksumSource' "tools[$ToolName]"
$expectedArtifact = "${ToolName}_${version}_${Platform}_${Architecture}.tar.gz"
$expectedBaseUrl = "https://github.com/$ToolName/$ToolName/releases/download/v$version"
if ($artifact -cne $expectedArtifact) { throw "Tool artifact identity mismatch: $artifact" }
if ($sha256 -cnotmatch '^[0-9a-f]{64}$') { throw "Tool checksum identity is invalid: $ToolName" }
if ($downloadUrl -cne "$expectedBaseUrl/$artifact") { throw "Tool download URL identity mismatch: $ToolName" }
if ($checksumSource -cne "$expectedBaseUrl/${ToolName}_${version}_checksums.txt") {
    throw "Tool checksum source identity mismatch: $ToolName"
}

$entries = [ordered]@{
    NQ_GITLEAKS_VERSION = $version
    NQ_GITLEAKS_ARTIFACT = $artifact
    NQ_GITLEAKS_PLATFORM = $Platform
    NQ_GITLEAKS_ARCHITECTURE = $Architecture
    NQ_GITLEAKS_SHA256 = $sha256
    NQ_GITLEAKS_DOWNLOAD_URL = $downloadUrl
    NQ_GITLEAKS_CHECKSUM_SOURCE = $checksumSource
}
foreach ($entry in $entries.GetEnumerator()) {
    Add-Content -LiteralPath $GitHubEnvPath -Value "$($entry.Key)=$($entry.Value)" -Encoding utf8
}
Write-Output "DELIVERY_TOOL_IDENTITY name=$ToolName version=$version platform=$Platform architecture=$Architecture source=$checksumSource"
