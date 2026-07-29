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
    if ($contract.schemaVersion -ne '1.2.0' -or $contract.authoritySchema -ne '3' -or
        -not $contract.authority -or -not $contract.lifecycles -or
        -not $contract.lifecycles.transitionPolicies -or -not $contract.evidence -or -not $contract.release) {
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

    $property = $Object.PSObject.Properties | Where-Object { $_.Name -ceq $Name } | Select-Object -First 1
    if ($null -eq $property) { return $null }
    return $property.Value
}

function Test-GovernanceExactTokenSet {
    param([string] $Value, [string[]] $Expected)

    $tokens = @($Value -split '\|')
    if ($tokens.Count -ne $Expected.Count -or @($tokens | Select-Object -Unique).Count -ne $Expected.Count) { return $false }
    foreach ($token in $Expected) {
        if ($tokens -cnotcontains $token) { return $false }
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

function Get-GovernanceContextValue {
    param([object] $Context, [string] $Name)

    if ($null -eq $Context) { return $null }
    if ($Context -is [System.Collections.IDictionary]) {
        foreach ($key in $Context.Keys) {
            if ([string]$key -ceq $Name) { return $Context[$key] }
        }
        return $null
    }
    return Get-GovernancePropertyValue $Context $Name
}

function Test-GovernanceExactNextActionMapping {
    param([object] $Contract, [string] $Status, [string] $WorkBatch, [string] $Action)

    $mappings = Get-GovernancePropertyValue $Contract.authority 'exactNextActionMappings'
    if ($null -eq $mappings) { return $false }
    foreach ($mapping in @($mappings)) {
        if ([string]$mapping.workBatchStatus -ceq $Status -and
            [string]$mapping.workBatch -ceq $WorkBatch -and
            [string]$mapping.nextAction -ceq $Action) {
            return $true
        }
    }
    return $false
}

function Test-GovernanceNextActionForWorkBatch {
    param([object] $Contract, [string] $Status, [string] $WorkBatch, [string] $Action)

    if ([string]::IsNullOrWhiteSpace($WorkBatch) -or [string]::IsNullOrWhiteSpace($Action)) { return $false }
    $expectedType = Get-GovernanceExpectedNextActionType $Contract $Status
    $actualType = Get-GovernanceNextActionType $Contract $Action
    if ($expectedType -ceq 'UNKNOWN' -or $actualType -cne $expectedType) { return $false }

    $exactMappings = Get-GovernancePropertyValue $Contract.authority 'exactNextActionMappings'
    $statusMappings = @($exactMappings | Where-Object { [string]$_.workBatchStatus -ceq $Status })
    if ($statusMappings.Count -gt 0) {
        return Test-GovernanceExactNextActionMapping $Contract $Status $WorkBatch $Action
    }

    $expectedPrefix = 'NQ-{0}-' -f $WorkBatch.ToUpperInvariant()
    return $Action.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase)
}

function Test-GovernanceReadinessStatus {
    param(
        [object] $Contract,
        [ValidateSet('ARCHIVE_FREEZE', 'RELEASE')] [string] $Mode,
        [string] $Status
    )

    switch ($Mode) {
        'ARCHIVE_FREEZE' { return @($Contract.lifecycles.freeze.candidateEntryStatuses) -ccontains $Status }
        'RELEASE' { return @($Contract.authority.acceptedBatchStatuses) -ccontains $Status }
        default { return $false }
    }
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
    return @($definition.transitions) -ccontains ("{0}->{1}" -f $From, $To)
}

function Test-GovernanceLifecycleTransitionContext {
    param(
        [object] $Contract,
        [ValidateSet('ordinary', 'highRisk')] [string] $Lifecycle,
        [string] $FromStatus,
        [string] $ToStatus,
        [string] $FromCommit,
        [string] $FromCi,
        [string] $ToCommit,
        [string] $ToCi,
        [bool] $AuthorityCatchUp = $false,
        [object] $Context = $null
    )

    if (-not (Test-GovernanceLifecycleTransition $Contract $Lifecycle $FromStatus $ToStatus)) { return $false }

    $fromFieldPolicy = Get-GovernancePropertyValue $Contract.authority.workStatusFieldPolicies $FromStatus
    $toFieldPolicy = Get-GovernancePropertyValue $Contract.authority.workStatusFieldPolicies $ToStatus
    if ($null -eq $fromFieldPolicy -or $null -eq $toFieldPolicy) { return $false }
    if ($FromCommit -notmatch [string]$fromFieldPolicy.commitPattern -or $FromCi -notmatch [string]$fromFieldPolicy.ciPattern) { return $false }
    if ($ToCommit -notmatch [string]$toFieldPolicy.commitPattern -or $ToCi -notmatch [string]$toFieldPolicy.ciPattern) { return $false }

    $policies = @($Contract.lifecycles.transitionPolicies | Where-Object {
        $_.from -ceq $FromStatus -and $_.to -ceq $ToStatus
    })
    if ($policies.Count -ne 1) { return $false }
    $policy = $policies[0]

    switch ([string]$policy.authorityCatchUp) {
        'REQUIRED' { if (-not $AuthorityCatchUp) { return $false } }
        'FORBIDDEN' { if ($AuthorityCatchUp) { return $false } }
        default { return $false }
    }

    switch ([string]$policy.commitRelation) {
        'SAME' {
            if (-not [string]::Equals($FromCommit, $ToCommit, [System.StringComparison]::OrdinalIgnoreCase)) { return $false }
        }
        'CHANGED' {
            if ([string]::Equals($FromCommit, $ToCommit, [System.StringComparison]::OrdinalIgnoreCase)) { return $false }
        }
        'FROM_UNCOMMITTED_TO_CONCRETE' {
            if ($FromCommit -cne 'UNCOMMITTED' -or $ToCommit -notmatch '^[0-9a-f]{40}$') { return $false }
        }
        'TO_UNCOMMITTED' {
            if ($ToCommit -cne 'UNCOMMITTED') { return $false }
        }
        default { return $false }
    }

    switch ([string]$policy.ciRelation) {
        'PENDING_OR_SAME' {
            if ($FromCi -cne 'PENDING' -and $FromCi -cne $ToCi) { return $false }
        }
        'NOT_RUN_TO_CONCRETE' {
            if ($FromCi -cne 'NOT_RUN' -or $ToCi -notmatch '^[1-9][0-9]*$') { return $false }
        }
        'TO_PENDING' {
            if ($ToCi -cne 'PENDING') { return $false }
        }
        'PENDING_OR_SAME_TO_CONCRETE' {
            if ($ToCi -notmatch '^[1-9][0-9]*$' -or ($FromCi -cne 'PENDING' -and $FromCi -cne $ToCi)) { return $false }
        }
        'CHANGED_TO_CONCRETE' {
            if ($ToCi -notmatch '^[1-9][0-9]*$' -or $FromCi -ceq $ToCi) { return $false }
        }
        'TO_NOT_RUN' {
            if ($ToCi -cne 'NOT_RUN') { return $false }
        }
        default { return $false }
    }

    $requiredMode = Get-GovernancePropertyValue $policy 'mode'
    if ($null -ne $requiredMode -and (Get-GovernanceContextValue $Context 'mode') -cne [string]$requiredMode) { return $false }

    $workBatchRelation = Get-GovernancePropertyValue $policy 'workBatchRelation'
    if ($null -ne $workBatchRelation) {
        switch ([string]$workBatchRelation) {
            'SAME' {
                $fromWorkBatch = [string](Get-GovernanceContextValue $Context 'fromWorkBatch')
                $toWorkBatch = [string](Get-GovernanceContextValue $Context 'toWorkBatch')
                if ([string]::IsNullOrWhiteSpace($fromWorkBatch) -or
                    -not [string]::Equals($fromWorkBatch, $toWorkBatch, [System.StringComparison]::Ordinal)) { return $false }
            }
            default { return $false }
        }
    }

    $acceptedBatchRelation = Get-GovernancePropertyValue $policy 'acceptedBatchRelation'
    if ($null -ne $acceptedBatchRelation) {
        switch ([string]$acceptedBatchRelation) {
            'SAME' {
                $fromAcceptedBatch = [string](Get-GovernanceContextValue $Context 'fromAcceptedBatch')
                $toAcceptedBatch = [string](Get-GovernanceContextValue $Context 'toAcceptedBatch')
                if ([string]::IsNullOrWhiteSpace($fromAcceptedBatch) -or
                    -not [string]::Equals($fromAcceptedBatch, $toAcceptedBatch, [System.StringComparison]::Ordinal)) { return $false }
            }
            default { return $false }
        }
    }

    $nextActionRelation = Get-GovernancePropertyValue $policy 'nextActionRelation'
    if ($null -ne $nextActionRelation) {
        switch ([string]$nextActionRelation) {
            'TO_STATUS_SAME_WORK_BATCH' {
                $toWorkBatch = [string](Get-GovernanceContextValue $Context 'toWorkBatch')
                $toNextAction = [string](Get-GovernanceContextValue $Context 'toNextAction')
                if (-not (Test-GovernanceNextActionForWorkBatch $Contract $ToStatus $toWorkBatch $toNextAction)) { return $false }
            }
            default { return $false }
        }
    }

    $evidenceRequirements = Get-GovernancePropertyValue $policy 'evidenceRequirements'
    if ($null -ne $evidenceRequirements) {
        $externalEvidence = Get-GovernanceContextValue $Context 'externalEvidence'
        if ($null -eq $externalEvidence) { return $false }
        $requiredExactHeadMatch = Get-GovernancePropertyValue $evidenceRequirements 'exactHeadMatch'
        if ($requiredExactHeadMatch -eq $true -and (Get-GovernanceContextValue $externalEvidence 'exactHeadMatch') -ne $true) { return $false }
        $requiredConclusion = Get-GovernancePropertyValue $evidenceRequirements 'ciConclusion'
        if ($null -ne $requiredConclusion -and (Get-GovernanceContextValue $externalEvidence 'ciConclusion') -cne [string]$requiredConclusion) { return $false }
    }
    return $true
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

    $authorityMatches = [regex]::Matches(
        $Content,
        '(?s)<!--[ \t]*nq-current-authority:start[ \t]*\r?\n(?<body>.*?)\r?\nnq-current-authority:end[ \t]*-->'
    )
    if ($authorityMatches.Count -ne 1) { return $null }
    $authority = @{}
    foreach ($line in ($authorityMatches[0].Groups['body'].Value -split '\r?\n')) {
        if ([string]::IsNullOrWhiteSpace($line)) { continue }
        # Machine authority token 的行和值边界必须原样保留；静默 Trim 会把非 canonical 状态 alias
        # 错误接受为 authority 事实。
        if ($line -cne $line.Trim()) { return $null }
        $lineMatch = [regex]::Match($line, '^(?<key>[a-z0-9_]+)=(?<value>.+)$')
        if (-not $lineMatch.Success) { return $null }
        $key = [string]$lineMatch.Groups['key'].Value
        if ($authority.ContainsKey($key)) { return $null }
        $value = [string]$lineMatch.Groups['value'].Value
        if ($value -cne $value.Trim()) { return $null }
        $authority[$key] = $value
    }
    return $authority
}
