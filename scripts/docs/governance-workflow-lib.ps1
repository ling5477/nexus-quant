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
    if ($contract.schemaVersion -ne '1.5.0' -or $contract.authoritySchema -ne '3' -or
        -not $contract.authority -or -not $contract.authority.strictActionFamilyPatterns -or
        -not $contract.authority.strictNextActions -or -not $contract.lifecycles -or
        -not $contract.lifecycles.transitionPolicies -or -not $contract.lifecycles.attempt10Runtime -or
        -not $contract.lifecycles.attempt11Runtime -or -not $contract.lifecycles.attempt12Runtime -or
        -not $contract.lifecycles.attempt13Runtime -or
        -not $contract.evidence -or -not $contract.release) {
        throw "GOVERNANCE_CONTRACT_INVALID path=$Path"
    }
    if ($contract.release.remoteName -ne 'origin' -or $contract.release.expectedBranch -ne 'dev' -or
        $contract.release.workflowName -ne 'NQ CI Baseline') {
        throw "GOVERNANCE_CONTRACT_INVALID release_identity path=$Path"
    }

    $exactMappings = @(Get-GovernancePropertyValue $contract.authority 'exactNextActionMappings')
    $mappingKeys = New-Object 'System.Collections.Generic.HashSet[string]' ([System.StringComparer]::Ordinal)
    $knownActionTypes = @(
        @($contract.authority.statusToNextActionType.PSObject.Properties | ForEach-Object { [string]$_.Value }) +
        @($contract.authority.nextActionTypes | ForEach-Object { [string]$_.name }) +
        @($contract.authority.strictNextActions | ForEach-Object { [string]$_.type })
    ) | Select-Object -Unique
    foreach ($mapping in $exactMappings) {
        $status = [string](Get-GovernancePropertyValue $mapping 'workBatchStatus')
        $workBatch = [string](Get-GovernancePropertyValue $mapping 'workBatch')
        $nextAction = [string](Get-GovernancePropertyValue $mapping 'nextAction')
        if ([string]::IsNullOrWhiteSpace($status) -or [string]::IsNullOrWhiteSpace($workBatch) -or
            [string]::IsNullOrWhiteSpace($nextAction)) {
            throw "GOVERNANCE_CONTRACT_INVALID exact_mapping_required_dimension path=$Path"
        }
        if (@($contract.authority.workBatchStatuses) -cnotcontains $status) {
            throw "GOVERNANCE_CONTRACT_INVALID exact_mapping_unknown_status path=$Path status=$status"
        }
        $actualActionType = Get-GovernanceNextActionType $contract $nextAction
        if ($actualActionType -ceq 'UNKNOWN') {
            throw "GOVERNANCE_CONTRACT_INVALID exact_mapping_unknown_action path=$Path status=$status work_batch=$workBatch action=$nextAction"
        }
        # Exact dimensions must identify one policy. Authority requirements may narrow applicability,
        # but must not create order-dependent "first mapping wins" behavior for the same tuple.
        $mappingKey = $status + [char]0 + $workBatch + [char]0 + $nextAction
        if (-not $mappingKeys.Add($mappingKey)) {
            throw "GOVERNANCE_CONTRACT_INVALID duplicate_exact_mapping path=$Path status=$status work_batch=$workBatch action=$nextAction"
        }

        $override = Get-GovernancePropertyValue $mapping 'expectedActionTypeOverride'
        if ($null -ne $override) {
            $scope = [string](Get-GovernancePropertyValue $mapping 'scope')
            $overrideType = [string]$override
            if ($scope -cne 'WORK_BATCH' -or [string]::IsNullOrWhiteSpace($overrideType) -or
                $knownActionTypes -cnotcontains $overrideType -or
                $actualActionType -cne $overrideType) {
                throw "GOVERNANCE_CONTRACT_INVALID exact_mapping_type_override path=$Path status=$status work_batch=$workBatch action=$nextAction override=$overrideType"
            }
        }
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

    $isStrictFamily = @($Contract.authority.strictActionFamilyPatterns | Where-Object {
        $Action -match [string]$_
    }).Count -gt 0
    if ($isStrictFamily) {
        $strictMatches = @($Contract.authority.strictNextActions | Where-Object {
            [string]$_.action -ceq $Action
        })
        if ($strictMatches.Count -ne 1) { return 'UNKNOWN' }
        return [string]$strictMatches[0].type
    }

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

function Test-GovernanceMappingAuthorityRequirements {
    param([object] $Mapping, [object] $Context)

    $requirements = Get-GovernancePropertyValue $Mapping 'authorityRequirements'
    if ($null -eq $requirements) { return $true }
    if ($null -eq $Context) { return $false }
    foreach ($requirement in @($requirements.PSObject.Properties)) {
        $actual = Get-GovernanceContextValue $Context ([string]$requirement.Name)
        if ($null -eq $actual -or [string]$actual -cne [string]$requirement.Value) { return $false }
    }
    return $true
}

function Test-GovernanceExactNextActionMapping {
    param(
        [object] $Contract, [string] $Status, [string] $WorkBatch, [string] $Action,
        [object] $Context = $null
    )

    $mappings = Get-GovernancePropertyValue $Contract.authority 'exactNextActionMappings'
    if ($null -eq $mappings) { return $false }
    $matches = @($mappings | Where-Object {
        [string]$_.workBatchStatus -ceq $Status -and
        [string]$_.workBatch -ceq $WorkBatch -and
        [string]$_.nextAction -ceq $Action -and
        (Test-GovernanceMappingAuthorityRequirements $_ $Context)
    })
    return $matches.Count -eq 1
}

function Test-GovernanceScopedNextActionMapping {
    param(
        [object] $Contract, [string] $Status, [string] $WorkBatch, [string] $Action,
        [object] $Context = $null
    )

    $mappings = Get-GovernancePropertyValue $Contract.authority 'exactNextActionMappings'
    if ($null -eq $mappings) { return $false }
    $matches = @($mappings | Where-Object {
        [string](Get-GovernancePropertyValue $_ 'scope') -ceq 'WORK_BATCH' -and
        [string]$_.workBatchStatus -ceq $Status -and
        [string]$_.workBatch -ceq $WorkBatch -and
        [string]$_.nextAction -ceq $Action -and
        (Test-GovernanceMappingAuthorityRequirements $_ $Context)
    })
    return $matches.Count -eq 1
}

function Get-GovernanceExpectedNextActionTypeForWorkBatch {
    param(
        [object] $Contract, [string] $Status, [string] $WorkBatch, [string] $Action,
        [object] $Context = $null
    )

    $genericType = Get-GovernanceExpectedNextActionType $Contract $Status
    $mappings = Get-GovernancePropertyValue $Contract.authority 'exactNextActionMappings'
    if ($null -eq $mappings) { return $genericType }
    $matches = @($mappings | Where-Object {
        [string](Get-GovernancePropertyValue $_ 'scope') -ceq 'WORK_BATCH' -and
        [string]$_.workBatchStatus -ceq $Status -and
        [string]$_.workBatch -ceq $WorkBatch -and
        [string]$_.nextAction -ceq $Action -and
        (Test-GovernanceMappingAuthorityRequirements $_ $Context)
    })
    if ($matches.Count -gt 1) { return 'UNKNOWN' }
    if ($matches.Count -eq 0) { return $genericType }
    $override = Get-GovernancePropertyValue $matches[0] 'expectedActionTypeOverride'
    if ($null -eq $override) { return $genericType }
    return [string]$override
}

function Test-GovernanceNextActionForWorkBatch {
    param(
        [object] $Contract, [string] $Status, [string] $WorkBatch, [string] $Action,
        [object] $Context = $null
    )

    if ([string]::IsNullOrWhiteSpace($WorkBatch) -or [string]::IsNullOrWhiteSpace($Action)) { return $false }
    $expectedType = Get-GovernanceExpectedNextActionTypeForWorkBatch `
        $Contract $Status $WorkBatch $Action $Context
    $actualType = Get-GovernanceNextActionType $Contract $Action
    if ($expectedType -ceq 'UNKNOWN' -or $actualType -ceq 'UNKNOWN') { return $false }

    $exactMappings = Get-GovernancePropertyValue $Contract.authority 'exactNextActionMappings'
    $scopedMappings = @($exactMappings | Where-Object {
        [string](Get-GovernancePropertyValue $_ 'scope') -ceq 'WORK_BATCH' -and
        [string]$_.workBatchStatus -ceq $Status -and
        [string]$_.workBatch -ceq $WorkBatch
    })
    if ($scopedMappings.Count -gt 0) {
        return $actualType -ceq $expectedType -and
            (Test-GovernanceScopedNextActionMapping $Contract $Status $WorkBatch $Action $Context)
    }

    $reservedOverrideMappings = @($exactMappings | Where-Object {
        $null -ne (Get-GovernancePropertyValue $_ 'expectedActionTypeOverride') -and
        [string]$_.nextAction -ceq $Action
    })
    if ($reservedOverrideMappings.Count -gt 0) {
        # An override action is a capability bound to its exact tuple; it must never fall back
        # to a generic status/prefix rule when status or work batch differs.
        return $false
    }

    $statusMappings = @($exactMappings | Where-Object {
        $scope = Get-GovernancePropertyValue $_ 'scope'
        $null -eq $scope -and [string]$_.workBatchStatus -ceq $Status
    })
    if ($statusMappings.Count -gt 0) {
        return $actualType -ceq $expectedType -and
            (Test-GovernanceExactNextActionMapping $Contract $Status $WorkBatch $Action $Context)
    }

    if ($actualType -cne $expectedType) { return $false }
    if ($actualType -ceq 'FREEZE_CLOSEOUT') {
        # Freeze closeout 是 Gate-level action；上方高优先级 exact mapping 继续保护历史 batch-scoped contract。
        # Windows PowerShell 5.1 在 StrictMode 下需要先建立局部变量，避免条件分支中的首次赋值被当作未定义读取。
        $gateMatch = $null
        $gateMatch = [regex]::Match($WorkBatch, '^(?<gate>Gate[A-Z0-9]+)-')
        if (-not $gateMatch.Success) { return $false }
        $expectedAction = 'NQ-{0}-FREEZE-CLOSEOUT' -f $gateMatch.Groups['gate'].Value.ToUpperInvariant()
        return [string]::Equals($Action, $expectedAction, [System.StringComparison]::Ordinal)
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
    if ($policies.Count -gt 1 -and $null -ne $Context) {
        $contextFromWorkBatch = [string](Get-GovernanceContextValue $Context 'fromWorkBatch')
        $contextToWorkBatch = [string](Get-GovernanceContextValue $Context 'toWorkBatch')
        $exactPairPolicies = @($policies | Where-Object {
            [string](Get-GovernancePropertyValue $_ 'workBatchRelation') -ceq 'EXACT_PAIR' -and
            [string](Get-GovernancePropertyValue $_ 'fromWorkBatch') -ceq $contextFromWorkBatch -and
            [string](Get-GovernancePropertyValue $_ 'toWorkBatch') -ceq $contextToWorkBatch
        })
        if ($exactPairPolicies.Count -gt 0) { $policies = $exactPairPolicies }
    }
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
        'SAME' {
            if ($FromCi -cne $ToCi) { return $false }
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
            'EXACT_PAIR' {
                $fromWorkBatch = [string](Get-GovernanceContextValue $Context 'fromWorkBatch')
                $toWorkBatch = [string](Get-GovernanceContextValue $Context 'toWorkBatch')
                $expectedFromWorkBatch = [string](Get-GovernancePropertyValue $policy 'fromWorkBatch')
                $expectedToWorkBatch = [string](Get-GovernancePropertyValue $policy 'toWorkBatch')
                if ([string]::IsNullOrWhiteSpace($expectedFromWorkBatch) -or
                    [string]::IsNullOrWhiteSpace($expectedToWorkBatch) -or
                    $fromWorkBatch -cne $expectedFromWorkBatch -or
                    $toWorkBatch -cne $expectedToWorkBatch) { return $false }
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
            'EXACT_PAIR' {
                $fromAcceptedBatch = [string](Get-GovernanceContextValue $Context 'fromAcceptedBatch')
                $toAcceptedBatch = [string](Get-GovernanceContextValue $Context 'toAcceptedBatch')
                $expectedFromAcceptedBatch = [string](Get-GovernancePropertyValue $policy 'fromAcceptedBatch')
                $expectedToAcceptedBatch = [string](Get-GovernancePropertyValue $policy 'toAcceptedBatch')
                if (-not [string]::Equals($fromAcceptedBatch, $expectedFromAcceptedBatch, [System.StringComparison]::Ordinal) -or
                    -not [string]::Equals($toAcceptedBatch, $expectedToAcceptedBatch, [System.StringComparison]::Ordinal)) { return $false }
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
                $toAuthority = Get-GovernanceContextValue $Context 'toAuthority'
                if (-not (Test-GovernanceNextActionForWorkBatch `
                        $Contract $ToStatus $toWorkBatch $toNextAction $toAuthority)) { return $false }
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

    $attempt10RuntimeState = Get-GovernancePropertyValue $policy 'attempt10RuntimeState'
    if ($null -ne $attempt10RuntimeState) {
        $toRuntimeState = Get-GovernanceContextValue $Context 'toRuntimeState'
        if (-not (Test-GovernanceAttempt10RuntimeState $Contract ([string]$attempt10RuntimeState) $toRuntimeState)) { return $false }
    }

    $attempt10RuntimeTransition = Get-GovernancePropertyValue $policy 'attempt10RuntimeTransition'
    if ($null -ne $attempt10RuntimeTransition) {
        $fromRuntimeState = Get-GovernanceContextValue $Context 'fromRuntimeState'
        $toRuntimeState = Get-GovernanceContextValue $Context 'toRuntimeState'
        $runtimeEvents = @(Get-GovernanceContextValue $Context 'runtimeEvents')
        if (-not (Test-GovernanceAttempt10RuntimeTransition $Contract `
                ([string]$attempt10RuntimeTransition.from) ([string]$attempt10RuntimeTransition.to) `
                $fromRuntimeState $toRuntimeState $runtimeEvents)) { return $false }
    }

    $attempt11RuntimeState = Get-GovernancePropertyValue $policy 'attempt11RuntimeState'
    if ($null -ne $attempt11RuntimeState) {
        $toRuntimeState = Get-GovernanceContextValue $Context 'toRuntimeState'
        if (-not (Test-GovernanceAttemptRuntimeState $Contract 'attempt11Runtime' `
                ([string]$attempt11RuntimeState) $toRuntimeState)) { return $false }
    }

    $attempt11RuntimeTransition = Get-GovernancePropertyValue $policy 'attempt11RuntimeTransition'
    if ($null -ne $attempt11RuntimeTransition) {
        $fromRuntimeState = Get-GovernanceContextValue $Context 'fromRuntimeState'
        $toRuntimeState = Get-GovernanceContextValue $Context 'toRuntimeState'
        $runtimeEvents = @(Get-GovernanceContextValue $Context 'runtimeEvents')
        if (-not (Test-GovernanceAttemptRuntimeTransition $Contract 'attempt11Runtime' `
                ([string]$attempt11RuntimeTransition.from) ([string]$attempt11RuntimeTransition.to) `
                $fromRuntimeState $toRuntimeState $runtimeEvents)) { return $false }
    }

    $attempt12RuntimeState = Get-GovernancePropertyValue $policy 'attempt12RuntimeState'
    if ($null -ne $attempt12RuntimeState) {
        $toRuntimeState = Get-GovernanceContextValue $Context 'toRuntimeState'
        if (-not (Test-GovernanceAttemptRuntimeState $Contract 'attempt12Runtime' `
                ([string]$attempt12RuntimeState) $toRuntimeState)) { return $false }
    }

    $attempt12RuntimeTransition = Get-GovernancePropertyValue $policy 'attempt12RuntimeTransition'
    if ($null -ne $attempt12RuntimeTransition) {
        $fromRuntimeState = Get-GovernanceContextValue $Context 'fromRuntimeState'
        $toRuntimeState = Get-GovernanceContextValue $Context 'toRuntimeState'
        $runtimeEvents = @(Get-GovernanceContextValue $Context 'runtimeEvents')
        if (-not (Test-GovernanceAttemptRuntimeTransition $Contract 'attempt12Runtime' `
                ([string]$attempt12RuntimeTransition.from) ([string]$attempt12RuntimeTransition.to) `
                $fromRuntimeState $toRuntimeState $runtimeEvents)) { return $false }
    }

    $attempt13RuntimeState = Get-GovernancePropertyValue $policy 'attempt13RuntimeState'
    if ($null -ne $attempt13RuntimeState) {
        $toRuntimeState = Get-GovernanceContextValue $Context 'toRuntimeState'
        if (-not (Test-GovernanceAttemptRuntimeState $Contract 'attempt13Runtime' `
                ([string]$attempt13RuntimeState) $toRuntimeState)) { return $false }
    }

    $attempt13RuntimeTransition = Get-GovernancePropertyValue $policy 'attempt13RuntimeTransition'
    if ($null -ne $attempt13RuntimeTransition) {
        $fromRuntimeState = Get-GovernanceContextValue $Context 'fromRuntimeState'
        $toRuntimeState = Get-GovernanceContextValue $Context 'toRuntimeState'
        $runtimeEvents = @(Get-GovernanceContextValue $Context 'runtimeEvents')
        if (-not (Test-GovernanceAttemptRuntimeTransition $Contract 'attempt13Runtime' `
                ([string]$attempt13RuntimeTransition.from) ([string]$attempt13RuntimeTransition.to) `
                $fromRuntimeState $toRuntimeState $runtimeEvents)) { return $false }
    }
    return $true
}

function Test-GovernanceAttemptRuntimeState {
    param(
        [object] $Contract,
        [string] $RuntimeName,
        [string] $Name,
        [object] $State
    )

    if ($null -eq $State -or [string]::IsNullOrWhiteSpace($Name) -or
        [string]::IsNullOrWhiteSpace($RuntimeName)) { return $false }
    $runtime = Get-GovernancePropertyValue $Contract.lifecycles $RuntimeName
    if ($null -eq $runtime) { return $false }
    $definitions = @($runtime.states | Where-Object { [string]$_.name -ceq $Name })
    if ($definitions.Count -ne 1) { return $false }
    foreach ($field in @($runtime.fields)) {
        $expected = Get-GovernancePropertyValue $definitions[0] ([string]$field)
        $actual = Get-GovernanceContextValue $State ([string]$field)
        if ($null -eq $expected -or $null -eq $actual -or [string]$actual -cne [string]$expected) { return $false }
    }
    return $true
}

function Test-GovernanceAttemptRuntimeTransition {
    param(
        [object] $Contract,
        [string] $RuntimeName,
        [string] $From,
        [string] $To,
        [object] $FromState,
        [object] $ToState,
        [string[]] $Events
    )

    $runtime = Get-GovernancePropertyValue $Contract.lifecycles $RuntimeName
    if ($null -eq $runtime -or
        -not (Test-GovernanceAttemptRuntimeState $Contract $RuntimeName $From $FromState) -or
        -not (Test-GovernanceAttemptRuntimeState $Contract $RuntimeName $To $ToState)) { return $false }
    $transitions = @($runtime.transitions | Where-Object {
        [string]$_.from -ceq $From -and [string]$_.to -ceq $To
    })
    if ($transitions.Count -ne 1) { return $false }
    $requiredEvents = @($transitions[0].requiredEvents)
    $actualEvents = @($Events)
    if ($requiredEvents.Count -ne $actualEvents.Count) { return $false }
    for ($index = 0; $index -lt $requiredEvents.Count; $index++) {
        if ([string]$requiredEvents[$index] -cne [string]$actualEvents[$index]) { return $false }
    }
    return $true
}

function Test-GovernanceAttempt10RuntimeState {
    param([object] $Contract, [string] $Name, [object] $State)

    return Test-GovernanceAttemptRuntimeState $Contract 'attempt10Runtime' $Name $State
}

function Test-GovernanceAttempt10RuntimeTransition {
    param(
        [object] $Contract,
        [string] $From,
        [string] $To,
        [object] $FromState,
        [object] $ToState,
        [string[]] $Events
    )

    return Test-GovernanceAttemptRuntimeTransition $Contract 'attempt10Runtime' `
        $From $To $FromState $ToState $Events
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

function Read-MachineCurrentAttemptAuthority {
    param([hashtable] $Authority)

    $allowedAttemptStates = @('NOT_CREATED', 'CREATED', 'RUNNING', 'FAILED', 'STOPPED', 'ACCEPTED', 'COMPLETED')
    $allowedAuthorizationStates = @(
        'AUTHORIZED', 'NOT_AUTHORIZED', 'PENDING_168H', 'FAILED', 'STOPPED',
        'ACCEPTED', 'COMPLETED_168H'
    )
    $workBatch = if ($Authority.ContainsKey('work_batch')) {
        [string]$Authority.work_batch
    } else {
        ''
    }
    $workBatchStatusTokens = @()
    if ($Authority.ContainsKey('work_batch_status')) {
        $workBatchStatusTokens = @([string]$Authority.work_batch_status -split '\|')
    }
    $hasLegacyAttemptStatus = $workBatchStatusTokens.Count -eq 2 -and
        $allowedAttemptStates -ccontains $workBatchStatusTokens[0] -and
        $allowedAuthorizationStates -ccontains $workBatchStatusTokens[1]
    $hasMachineAttemptFields = $Authority.ContainsKey('attempt') -or
        $Authority.ContainsKey('attempt_status')
    if (-not $hasMachineAttemptFields -and -not $hasLegacyAttemptStatus) {
        return [pscustomobject]@{
            IsApplicable = $false
            IsValid = $true
            Reason = ''
        }
    }

    $attemptIntentMatches = @([regex]::Matches(
        $workBatch,
        '(?i)(?:^|-)ATTEMPT(?=-|$)'
    ))
    $attemptSegmentMatches = @([regex]::Matches(
        $workBatch,
        '(?-i)(?:^|-)ATTEMPT-(?<attemptId>[1-9][0-9]*)(?=-|$)'
    ))

    if ($attemptIntentMatches.Count -ne 1 -or $attemptSegmentMatches.Count -ne 1) {
        return [pscustomobject]@{
            IsApplicable = $true
            IsValid = $false
            Reason = "field=work_batch expected=one_canonical_attempt_segment actual=$workBatch"
        }
    }

    $workBatchAttemptId = [int]$attemptSegmentMatches[0].Groups['attemptId'].Value
    $statusTokens = $workBatchStatusTokens
    if ($hasMachineAttemptFields) {
        if (-not $Authority.ContainsKey('attempt')) {
            return [pscustomobject]@{
                IsApplicable = $true
                IsValid = $false
                Reason = 'field=attempt expected=Attempt-<id> actual=MISSING'
            }
        }
        $machineAttemptMatch = [regex]::Match(
            [string]$Authority.attempt,
            '(?-i:^Attempt-(?<attemptId>[1-9][0-9]*)$)'
        )
        if (-not $machineAttemptMatch.Success) {
            return [pscustomobject]@{
                IsApplicable = $true
                IsValid = $false
                Reason = "field=attempt expected=Attempt-$workBatchAttemptId actual=$($Authority.attempt)"
            }
        }

        $machineAttemptId = [int]$machineAttemptMatch.Groups['attemptId'].Value
        if ($machineAttemptId -ne $workBatchAttemptId) {
            return [pscustomobject]@{
                IsApplicable = $true
                IsValid = $false
                Reason = "field=attempt_id work_batch=$workBatchAttemptId machine=$machineAttemptId"
            }
        }
        if (-not $Authority.ContainsKey('attempt_status')) {
            return [pscustomobject]@{
                IsApplicable = $true
                IsValid = $false
                Reason = 'field=attempt_status expected=<state>|<authorization> actual=MISSING'
            }
        }
        $statusTokens = @([string]$Authority.attempt_status -split '\|')
    }
    if ($statusTokens.Count -ne 2) {
        return [pscustomobject]@{
            IsApplicable = $true
            IsValid = $false
            Reason = "field=attempt_status expected=<state>|<authorization> actual=$($Authority.attempt_status)"
        }
    }

    if ($allowedAttemptStates -cnotcontains $statusTokens[0] -or
        $allowedAuthorizationStates -cnotcontains $statusTokens[1]) {
        return [pscustomobject]@{
            IsApplicable = $true
            IsValid = $false
            Reason = "field=attempt_status state=$($statusTokens[0]) authorization=$($statusTokens[1])"
        }
    }

    return [pscustomobject]@{
        IsApplicable = $true
        IsValid = $true
        Reason = ''
        AttemptId = $workBatchAttemptId
        AttemptState = $statusTokens[0]
        AuthorizationState = $statusTokens[1]
    }
}
