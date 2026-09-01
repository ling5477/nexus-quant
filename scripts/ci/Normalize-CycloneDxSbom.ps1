[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $InputPath,

    [Parameter(Mandatory = $true)]
    [string] $OutputPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function ConvertTo-CanonicalNode {
    param([AllowNull()][object] $Value)

    if ($null -eq $Value) { return $null }
    if ($Value -is [string] -or $Value -is [ValueType]) { return $Value }
    if ($Value -is [System.Collections.IDictionary]) {
        $ordered = [ordered]@{}
        foreach ($key in @($Value.Keys | ForEach-Object { [string]$_ } | Sort-Object)) {
            $ordered[$key] = ConvertTo-CanonicalNode $Value[$key]
        }
        return $ordered
    }
    if ($Value -is [System.Collections.IEnumerable]) {
        return @($Value | ForEach-Object { ConvertTo-CanonicalNode $_ })
    }

    $object = [ordered]@{}
    foreach ($property in @($Value.PSObject.Properties | Sort-Object Name)) {
        $object[$property.Name] = ConvertTo-CanonicalNode $property.Value
    }
    return $object
}

function Get-PropertyText {
    param([object] $Object, [string] $Name)
    if ($null -eq $Object) { return '' }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) { return '' }
    return [string]$property.Value
}

function Write-Utf8LfJson {
    param([string] $Path, [object] $Value)

    $parent = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($parent)) {
        New-Item -ItemType Directory -Path $parent -Force | Out-Null
    }
    $json = ($Value | ConvertTo-Json -Depth 100).Replace("`r`n", "`n").TrimEnd() + "`n"
    [IO.File]::WriteAllText($Path, $json, (New-Object Text.UTF8Encoding($false)))
}

$resolvedInput = (Resolve-Path -LiteralPath $InputPath).Path
try {
    $sbom = Get-Content -LiteralPath $resolvedInput -Raw -Encoding UTF8 | ConvertFrom-Json
} catch {
    throw "CycloneDX SBOM JSON is invalid: $($_.Exception.Message)"
}

if ([string]$sbom.bomFormat -cne 'CycloneDX' -or [string]::IsNullOrWhiteSpace([string]$sbom.specVersion)) {
    throw 'CycloneDX SBOM identity is invalid'
}
if (@($sbom.components).Count -eq 0) {
    throw 'CycloneDX SBOM must contain at least one component'
}

$sbom.PSObject.Properties.Remove('serialNumber')
if ($null -ne $sbom.metadata) {
    $sbom.metadata.PSObject.Properties.Remove('timestamp')
    $toolsProperty = $sbom.metadata.PSObject.Properties['tools']
    if ($null -ne $toolsProperty -and
            $null -ne $toolsProperty.Value -and
            $null -ne $toolsProperty.Value.PSObject.Properties['components']) {
        $sbom.metadata.tools.components = @($sbom.metadata.tools.components | Sort-Object {
            '{0}|{1}|{2}' -f (Get-PropertyText $_ 'name'), (Get-PropertyText $_ 'version'), (Get-PropertyText $_ 'group')
        })
    }
}
$sbom.components = @($sbom.components | Sort-Object {
    '{0}|{1}|{2}|{3}' -f (Get-PropertyText $_ 'bom-ref'), (Get-PropertyText $_ 'purl'), (Get-PropertyText $_ 'name'), (Get-PropertyText $_ 'version')
})
if ($null -ne $sbom.PSObject.Properties['dependencies']) {
    foreach ($dependency in @($sbom.dependencies)) {
        if ($null -ne $dependency.PSObject.Properties['dependsOn']) {
            $dependency.dependsOn = @($dependency.dependsOn | Sort-Object)
        }
    }
    $sbom.dependencies = @($sbom.dependencies | Sort-Object { Get-PropertyText $_ 'ref' })
}

$canonical = ConvertTo-CanonicalNode $sbom
Write-Utf8LfJson -Path $OutputPath -Value $canonical
$digest = (Get-FileHash -Algorithm SHA256 -LiteralPath $OutputPath).Hash.ToLowerInvariant()
Write-Output "CYCLONEDX_NORMALIZED components=$(@($sbom.components).Count) sha256=$digest output=$OutputPath"
