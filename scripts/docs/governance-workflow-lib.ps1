Set-StrictMode -Version Latest

function Get-GovernancePropertyValue {
    param([object] $Object, [string] $Name)
    if ($null -eq $Object) { return $null }
    $properties = @($Object.psobject.Properties | Where-Object { $_.Name -ceq $Name })
    if ($properties.Count -eq 1) { return $properties[0].Value }
    return $null
}

function Assert-GovernanceSafetyProfileDefinition {
    param(
        [Parameter(Mandatory = $true)][object] $Profile,
        [Parameter(Mandatory = $true)][string[]] $RequiredFields,
        [Parameter(Mandatory = $true)][string] $ProfileName
    )
    $actualFields = @($Profile.psobject.Properties.Name)
    if ($actualFields.Count -ne $RequiredFields.Count) {
        throw "Safety profile field count invalid: profile=$ProfileName expected=$($RequiredFields.Count) actual=$($actualFields.Count)"
    }
    foreach ($field in $RequiredFields) {
        if ($actualFields -cnotcontains $field) { throw "Safety profile field missing: profile=$ProfileName field=$field" }
        $allowed = @(Get-GovernancePropertyValue $Profile $field)
        if ($allowed.Count -eq 0) { throw "Safety profile allowed values missing: profile=$ProfileName field=$field" }
        $tokens = New-Object System.Collections.Generic.List[string]
        foreach ($value in $allowed) {
            if ($null -eq $value -or $value -isnot [string] -or [string]::IsNullOrWhiteSpace($value) -or $value -cne $value.Trim()) {
                throw "Safety profile value invalid: profile=$ProfileName field=$field"
            }
            $tokens.Add([string]$value)
        }
        if (@($tokens | Sort-Object -Unique).Count -ne $tokens.Count) {
            throw "Safety profile value duplicated: profile=$ProfileName field=$field"
        }
    }
}

function Get-GovernanceWorkflowContract {
    param([Parameter(Mandatory = $true)][string] $Path)
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "Governance contract not found: $Path" }
    $content = [System.IO.File]::ReadAllText($Path, (New-Object System.Text.UTF8Encoding($false)))
    try { $contract = $content | ConvertFrom-Json } catch { throw "Governance contract JSON invalid: $($_.Exception.Message)" }
    if ([string]$contract.schemaVersion -notmatch '^2\.') { throw "Unsupported governance contract schema: $($contract.schemaVersion)" }
    foreach ($name in @('authority', 'lifecycles', 'evidence', 'release', 'hardBlockers')) {
        if ($null -eq (Get-GovernancePropertyValue $contract $name)) { throw "Governance contract field missing: $name" }
    }
    foreach ($name in @('ordinary', 'highRisk', 'ciFailed', 'blocked', 'freeze', 'release', 'soak')) {
        if ($null -eq (Get-GovernancePropertyValue $contract.lifecycles $name)) { throw "Governance lifecycle missing: $name" }
    }
    $names = @($contract.authority.nextActionTypes | ForEach-Object { [string]$_.name })
    if (@($names | Sort-Object -Unique).Count -ne $names.Count) { throw 'Duplicate next-action type.' }
    foreach ($definition in @($contract.authority.nextActionTypes)) {
        if ([string]::IsNullOrWhiteSpace([string]$definition.name) -or [string]::IsNullOrWhiteSpace([string]$definition.pattern)) {
            throw 'Next-action type definition is incomplete.'
        }
        try { $null = [regex]::new([string]$definition.pattern) } catch { throw "Next-action regex invalid: $($definition.name)" }
    }

    $requiredAuthorityFields = @($contract.authority.requiredFields | ForEach-Object { [string]$_ })
    if ($requiredAuthorityFields.Count -eq 0 -or @($requiredAuthorityFields | Sort-Object -Unique).Count -ne $requiredAuthorityFields.Count) {
        throw 'Authority required fields are missing or duplicated.'
    }
    $safetyFields = @($contract.authority.safetyProfileRequiredFields | ForEach-Object { [string]$_ })
    if ($safetyFields.Count -eq 0 -or @($safetyFields | Sort-Object -Unique).Count -ne $safetyFields.Count) {
        throw 'Safety profile required fields are missing or duplicated.'
    }
    foreach ($field in $safetyFields) {
        if ($requiredAuthorityFields -cnotcontains $field) { throw "Safety profile field is not an authority field: $field" }
    }
    $profiles = Get-GovernancePropertyValue $contract.authority 'safetyProfiles'
    if ($null -eq $profiles) { throw 'Safety profiles are missing.' }
    $defaultProfile = Get-GovernancePropertyValue $profiles 'default'
    if ($null -eq $defaultProfile) { throw 'Default safety profile is missing.' }
    Assert-GovernanceSafetyProfileDefinition $defaultProfile $safetyFields 'default'
    $byActiveGate = Get-GovernancePropertyValue $profiles 'byActiveGate'
    if ($null -eq $byActiveGate) { throw 'Active Gate safety profiles are missing.' }
    foreach ($profileProperty in @($byActiveGate.psobject.Properties)) {
        if ([string]::IsNullOrWhiteSpace($profileProperty.Name) -or $profileProperty.Name -cne $profileProperty.Name.Trim()) {
            throw 'Active Gate safety profile name is invalid.'
        }
        Assert-GovernanceSafetyProfileDefinition $profileProperty.Value $safetyFields $profileProperty.Name
    }
    return $contract
}

function Test-GovernanceExactTokenSet {
    param([string] $Value, [string[]] $Expected)
    $actual = @($Value -split '\|' | Where-Object { $_ })
    if ($actual.Count -ne $Expected.Count) { return $false }
    foreach ($token in $Expected) { if ($actual -cnotcontains $token) { return $false } }
    return $true
}

function Get-GovernanceNextActionType {
    param([object] $Contract, [string] $Action)
    $matchingTypes = @($Contract.authority.nextActionTypes | Where-Object {
        [regex]::IsMatch($Action, [string]$_.pattern)
    } | ForEach-Object { [string]$_.name })
    if ($matchingTypes.Count -eq 0) { return 'UNKNOWN' }
    if ($matchingTypes.Count -gt 1) { return 'AMBIGUOUS' }
    return $matchingTypes[0]
}

function ConvertFrom-GovernanceJsonArray {
    param([Parameter(Mandatory = $true)][string] $Json)
    if ($Json -notmatch '^\s*\[' -or $Json -notmatch '\]\s*$') { throw 'Expected a top-level JSON array.' }
    try { $parsed = $Json | ConvertFrom-Json } catch { throw "JSON array invalid: $($_.Exception.Message)" }
    # Windows PowerShell 5.1 emits the top-level array as one Object[] pipeline item.
    # Enumerating the parsed value explicitly preserves each CI run as an independent object on every host.
    return @($parsed | ForEach-Object { $_ })
}

function Select-GovernanceReleaseCiRun {
    param(
        [Parameter(Mandatory = $true)][object[]] $Runs,
        [Parameter(Mandatory = $true)][string] $WorkflowName,
        [Parameter(Mandatory = $true)][string] $HeadSha
    )
    $selected = $null
    $selectedDatabaseId = [long]::MinValue
    foreach ($run in $Runs) {
        if ($null -eq $run -or $run -is [System.Array]) { continue }
        $databaseId = Get-GovernancePropertyValue $run 'databaseId'
        $workflow = Get-GovernancePropertyValue $run 'workflowName'
        $head = Get-GovernancePropertyValue $run 'headSha'
        $status = Get-GovernancePropertyValue $run 'status'
        $conclusion = Get-GovernancePropertyValue $run 'conclusion'
        if ($databaseId -is [System.Array] -or $workflow -is [System.Array] -or $head -is [System.Array] -or
            $status -is [System.Array] -or $conclusion -is [System.Array]) { continue }
        if ([string]$databaseId -notmatch '^[1-9][0-9]*$') { continue }
        if ([string]$workflow -cne $WorkflowName -or [string]$head -cne $HeadSha -or
            [string]$status -cne 'completed' -or [string]$conclusion -cne 'success') { continue }
        $numericDatabaseId = [long]$databaseId
        if ($numericDatabaseId -gt $selectedDatabaseId) {
            $selected = $run
            $selectedDatabaseId = $numericDatabaseId
        }
    }
    return $selected
}

function Get-GovernanceExpectedNextActionType {
    param([object] $Contract, [string] $Status)
    $allowed = @(Get-GovernancePropertyValue $Contract.authority.allowedNextActionTypesByStatus $Status)
    if ($allowed.Count -eq 0) { return 'UNKNOWN' }
    return [string]$allowed[0]
}

function Get-GovernanceExpectedNextActionTypeForWorkBatch {
    param([object] $Contract, [string] $Status, [string] $WorkBatch, [object] $Context)
    return Get-GovernanceExpectedNextActionType $Contract $Status
}

function Test-GovernanceNextActionForWorkBatch {
    param([object] $Contract, [string] $Status, [string] $WorkBatch, [string] $Action, [object] $Context)
    $allowed = @(Get-GovernancePropertyValue $Contract.authority.allowedNextActionTypesByStatus $Status)
    if ($allowed.Count -eq 0) { return $false }
    $actual = Get-GovernanceNextActionType $Contract $Action
    return $allowed -ccontains $actual
}

function Test-GovernanceReadinessStatus {
    param([object] $Contract, [string] $Mode, [string] $Status)
    return @($Contract.authority.workBatchStatuses) -ccontains $Status
}

function Get-GovernanceWorkStatusPattern {
    param([object] $Contract, [string] $Status)
    return [regex]::Escape($Status)
}

function Test-GovernanceLifecycleTransition {
    param([object] $Contract, [string] $Lifecycle, [string] $From, [string] $To)
    $definition = Get-GovernancePropertyValue $Contract.lifecycles $Lifecycle
    if ($null -eq $definition) { return $false }
    foreach ($pair in @($definition.transitions)) {
        if (@($pair).Count -eq 2 -and [string]$pair[0] -ceq $From -and [string]$pair[1] -ceq $To) { return $true }
    }
    return $false
}

function Test-GovernanceLifecycleTransitionContext {
    param(
        [object] $Contract, [string] $Lifecycle, [string] $FromStatus, [string] $ToStatus,
        [string] $FromCommit, [string] $FromCi, [string] $ToCommit, [string] $ToCi,
        [bool] $AuthorityCatchUp, [object] $Context
    )
    if (-not (Test-GovernanceLifecycleTransition $Contract $Lifecycle $FromStatus $ToStatus)) { return $false }
    $policy = Get-GovernancePropertyValue $Contract.authority.workStatusFieldPolicies $ToStatus
    if ($null -eq $policy) { return $false }
    if ($ToCommit -notmatch [string]$policy.commitPattern -or $ToCi -notmatch [string]$policy.ciPattern) { return $false }
    return $true
}

function Test-GovernanceEvidencePath {
    param([object] $Contract, [string] $Scope, [string] $RelativePath)
    $normalized = $RelativePath.Replace('\', '/')
    $pattern = if ($Scope -ceq 'current') { [string]$Contract.evidence.currentPathPattern } elseif ($Scope -ceq 'archive') { [string]$Contract.evidence.archivePathPattern } else { return $false }
    return [regex]::IsMatch($normalized, $pattern)
}

function Test-GovernanceEvidenceItem {
    param([object] $Contract, [object] $Item, [string] $Scope, [string] $RelativePath)
    if (-not (Test-GovernanceEvidencePath $Contract $Scope $RelativePath)) { return $false }
    if ($Contract.evidence.rejectSymlinks -eq $true -and (($Item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0)) { return $false }
    $extension = [System.IO.Path]::GetExtension($Item.Name)
    if (@($Contract.evidence.safeExtensions) -cnotcontains $extension) { return $false }
    $content = [System.IO.File]::ReadAllText($Item.FullName, (New-Object System.Text.UTF8Encoding($false)))
    $minimum = if ($Item.Name -ceq [string]$Contract.evidence.indexFileName) { [int]$Contract.evidence.minimumIndexNonWhitespaceCharacters } else { [int]$Contract.evidence.minimumAttemptNonWhitespaceCharacters }
    if (($content -replace '\s', '').Length -lt $minimum) { return $false }
    if ($content -match [string]$Contract.evidence.placeholderPattern) { return $false }
    return $true
}

function Read-GovernanceAuthorityBlock {
    param([Parameter(Mandatory = $true)][string] $Content)
    $normalized = $Content.Replace("`r`n", "`n")
    if ($normalized.Contains("`r")) { throw 'Machine authority contains unsupported line endings.' }
    $matches = [regex]::Matches($normalized, '(?ms)^<!-- nq-current-authority:start\n(?<body>.*?)\nnq-current-authority:end -->$')
    if ($matches.Count -ne 1) { throw "Expected exactly one nq-current-authority block, found $($matches.Count)." }
    $authority = [ordered]@{}
    foreach ($line in ($matches[0].Groups['body'].Value -split '\n')) {
        if ([string]::IsNullOrEmpty($line) -or $line -cne $line.Trim()) { throw "Non-canonical authority line: $line" }
        if ($line -notmatch '^(?<key>[a-z][a-z0-9_]*)=(?<value>.+)$') { throw "Malformed authority line: $line" }
        $key = $Matches['key']; $value = $Matches['value']
        if ($value -cne $value.Trim()) { throw "Non-canonical authority value: $key" }
        if ($authority.Contains($key)) { throw "Duplicate authority field: $key" }
        $authority[$key] = $value
    }
    return [pscustomobject]$authority
}
