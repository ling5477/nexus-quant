<#
.SYNOPSIS
Loads the canonical governance contract and exposes shared pure functions.
.NOTES
This helper does not access GitHub or mutate files, commits, branches, or tags.
#>
Set-StrictMode -Version Latest

function Get-GovernanceWorkflowContract {
    param([string] $Path = (Join-Path $PSScriptRoot 'governance-workflow-contract.json'))

    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
        throw "GOVERNANCE_CONTRACT_NOT_FOUND path=$Path"
    }
    $content = [System.IO.File]::ReadAllText($Path, (New-Object System.Text.UTF8Encoding($false)))
    $contract = $content | ConvertFrom-Json
    # A checker must understand the exact contract shape before it can use any policy from it.
    # Unknown versions fail closed instead of being treated as a forward-compatible extension.
    if ($contract.schemaVersion -ne '1.0.0' -or $contract.authoritySchema -ne '3' -or
        -not $contract.authority -or -not $contract.lifecycles -or -not $contract.evidence -or -not $contract.release) {
        throw "GOVERNANCE_CONTRACT_INVALID path=$Path"
    }
    if ($contract.release.remoteName -ne 'origin' -or $contract.release.expectedBranch -ne 'dev' -or
        $contract.release.workflowName -ne 'NQ CI Baseline') {
        throw "GOVERNANCE_CONTRACT_INVALID release_identity path=$Path"
    }
    return $contract
}

function Get-GovernancePropertyValue {
    param([object] $Object, [string] $Name)

    $property = $Object.PSObject.Properties | Where-Object { $_.Name -eq $Name } | Select-Object -First 1
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Test-GovernanceExactTokenSet {
    param([string] $Value, [string[]] $Expected)

    $tokens = @($Value -split '\|')
    if ($tokens.Count -ne $Expected.Count -or @($tokens | Select-Object -Unique).Count -ne $Expected.Count) { return $false }
    foreach ($token in $Expected) {
        if ($tokens -notcontains $token) { return $false }
    }
    return $true
}

function Get-GovernanceNextActionType {
    param([object] $Contract, [string] $Action)

    foreach ($definition in @($Contract.authority.nextActionTypes)) {
        if ($Action -match [string]$definition.pattern) { return [string]$definition.name }
    }
    return 'UNKNOWN'
}

function Get-GovernanceExpectedNextActionType {
    param([object] $Contract, [string] $Status)

    $value = Get-GovernancePropertyValue $Contract.authority.statusToNextActionType $Status
    if ($null -eq $value) { return 'UNKNOWN' }
    return [string]$value
}

function Get-GovernanceWorkStatusPattern {
    param([object] $Contract, [string] $Status)

    $value = Get-GovernancePropertyValue $Contract.authority.workStatusBodyPatterns $Status
    if ($null -eq $value) { return '(?!)' }
    return [string]$value
}

function Test-GovernanceLifecycleTransition {
    param([object] $Contract, [ValidateSet('ordinary', 'highRisk')] [string] $Lifecycle, [string] $From, [string] $To)

    $definition = Get-GovernancePropertyValue $Contract.lifecycles $Lifecycle
    if ($null -eq $definition) { return $false }
    return @($definition.transitions) -contains ("{0}->{1}" -f $From, $To)
}

function Test-GovernanceEvidencePath {
    param([object] $Contract, [ValidateSet('current', 'archive')] [string] $Scope, [string] $RelativePath)

    if ([string]::IsNullOrWhiteSpace($RelativePath)) { return $false }
    $normalized = $RelativePath.Replace('\', '/')
    if ($normalized -match '(^|/)\.\.(/|$)|%2e|%2f|%5c|[\x00-\x1f]') { return $false }
    $pattern = if ($Scope -eq 'current') { [string]$Contract.evidence.currentPathPattern } else { [string]$Contract.evidence.archivePathPattern }
    return $normalized -match $pattern
}

function Test-GovernanceEvidenceItem {
    param([object] $Contract, [object] $Item, [ValidateSet('current', 'archive')] [string] $Scope, [string] $RelativePath)

    if (-not (Test-GovernanceEvidencePath $Contract $Scope $RelativePath)) { return $false }
    if ([bool]$Contract.evidence.rejectSymlinks -and (($Item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)) { return $false }
    if (@($Contract.evidence.safeExtensions) -notcontains [System.IO.Path]::GetExtension($RelativePath).ToLowerInvariant()) { return $false }
    if (-not $Item.PSIsContainer) {
        if ($Item.Length -le 0) { return $false }
        $fullNameProperty = $Item.PSObject.Properties | Where-Object { $_.Name -eq 'FullName' } | Select-Object -First 1
        if ($null -eq $fullNameProperty -or -not (Test-Path -LiteralPath $fullNameProperty.Value -PathType Leaf)) { return $false }
        $content = [System.IO.File]::ReadAllText($fullNameProperty.Value, (New-Object System.Text.UTF8Encoding($false)))
        $minimum = if ([System.IO.Path]::GetFileName($RelativePath) -eq [string]$Contract.evidence.indexFileName) {
            [int]$Contract.evidence.minimumIndexNonWhitespaceCharacters
        } else {
            [int]$Contract.evidence.minimumAttemptNonWhitespaceCharacters
        }
        if (($content -replace '\s', '').Length -lt $minimum -or $content.Trim() -match [string]$Contract.evidence.placeholderPattern) { return $false }
    }
    return $true
}

function Read-GovernanceAuthorityBlock {
    param([string] $Content)

    $matches = [regex]::Matches($Content, '(?s)<!--\s*nq-current-authority:start\s*(.*?)\s*nq-current-authority:end\s*-->')
    if ($matches.Count -ne 1) { return $null }
    $authority = @{}
    foreach ($line in ($matches[0].Groups[1].Value -split '\r?\n')) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrWhiteSpace($trimmed)) { continue }
        if ($trimmed -notmatch '^(?<key>[a-z0-9_]+)=(?<value>.+)$' -or $authority.ContainsKey($Matches.key)) { return $null }
        $authority[$Matches.key] = $Matches.value.Trim()
    }
    return $authority
}
