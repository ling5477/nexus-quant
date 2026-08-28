[CmdletBinding()]
param(
    [string] $PolicyPath = 'scripts/docs/agent-workflow-policy.json',
    [string] $FixturePath = 'scripts/docs/agent-workflow-fixtures.json',
    [string] $AgentRoot = '.agents',
    [string] $CandidateAgentsPath,
    [string] $CandidateClaudePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Resolve-RepoPath {
    param([string] $Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path $repoRoot $Path
}

function Read-Json {
    param([string] $Path)
    return [System.IO.File]::ReadAllText((Resolve-RepoPath $Path), (New-Object System.Text.UTF8Encoding($false))) | ConvertFrom-Json
}

function Get-Property {
    param([object] $Object, [string] $Name)
    if ($null -eq $Object) { return $null }
    $property = @($Object.psobject.Properties | Where-Object { $_.Name -ceq $Name })
    if ($property.Count -eq 1) { return $property[0].Value }
    return $null
}

function Assert-True {
    param([bool] $Condition, [string] $Message)
    if (-not $Condition) { throw $Message }
}

function Assert-Equal {
    param([object] $Expected, [object] $Actual, [string] $Message)
    $expectedJson = ConvertTo-Json $Expected -Compress -Depth 20
    $actualJson = ConvertTo-Json $Actual -Compress -Depth 20
    if ($expectedJson -cne $actualJson) { throw "$Message expected=$expectedJson actual=$actualJson" }
}

function Assert-StringArray {
    param([object] $Value, [string] $Code, [int] $Minimum = 1)
    $items = @($Value)
    if ($items.Count -lt $Minimum) { throw "$Code count=$($items.Count) minimum=$Minimum" }
    foreach ($item in $items) {
        if ($item -isnot [string] -or [string]::IsNullOrWhiteSpace([string]$item) -or [string]$item -cne ([string]$item).Trim()) {
            throw "$Code invalid-string"
        }
    }
    if (@($items | Sort-Object -Unique).Count -ne $items.Count) { throw "$Code duplicated-value" }
    return $items
}

function Get-SkillCapabilitySection {
    param([string] $Content, [string] $SkillName, [string] $Heading, [string] $SectionName)

    $headingPattern = '(?m)^##\s+{0}\s*$' -f [regex]::Escape($Heading)
    $headingMatches = [regex]::Matches($Content, $headingPattern)
    if ($headingMatches.Count -ne 1) {
        throw "SKILL_CAPABILITY_SECTION_MISSING skill=$SkillName section=$SectionName count=$($headingMatches.Count)"
    }

    $bodyStart = $headingMatches[0].Index + $headingMatches[0].Length
    $nextHeadingRegex = New-Object System.Text.RegularExpressions.Regex('(?m)^##\s+.+?\s*$')
    $nextHeading = $nextHeadingRegex.Match($Content, $bodyStart)
    $bodyEnd = if ($nextHeading.Success) { $nextHeading.Index } else { $Content.Length }
    return $Content.Substring($bodyStart, $bodyEnd - $bodyStart)
}

function Get-MeaningfulSkillLines {
    param([string] $Body)

    $withoutComments = [regex]::Replace($Body, '(?s)<!--.*?-->', '')
    return @(($withoutComments -split '\r?\n') | Where-Object {
        $line = $_.Trim()
        if ([string]::IsNullOrWhiteSpace($line)) { return $false }
        if ($line -match '^#{1,6}(?:\s|$)') { return $false }
        if ($line -match '(?i)^(?:(?:[-*+]\s+)|(?:\d+[.)]\s+))?(?:[`*_~]+\s*)?(?:TBD|TODO|PLACEHOLDER|N/?A|NONE)(?:\s*[`*_~]+)?[\s.:;-]*$') { return $false }
        return $true
    })
}

function Get-SkillCanonicalDeclaration {
    param([string] $Content, [string] $RoleBody, [string] $SkillName, [string] $FieldName)

    $declarationPattern = '(?m)^\s*(?:-\s+)?{0}:\s*.*$' -f [regex]::Escape($FieldName)
    $pattern = '(?m)^\s*-\s+{0}:\s*`(?<value>[A-Z][A-Z0-9_]*)`\s*$' -f [regex]::Escape($FieldName)
    $allDeclarations = [regex]::Matches($Content, $declarationPattern)
    $roleDeclarations = [regex]::Matches($RoleBody, $declarationPattern)
    $allMatches = [regex]::Matches($Content, $pattern)
    $roleMatches = [regex]::Matches($RoleBody, $pattern)
    if ($allDeclarations.Count -ne 1 -or $roleDeclarations.Count -ne 1 -or $allMatches.Count -ne 1 -or $roleMatches.Count -ne 1) {
        throw "SKILL_CAPABILITY_DECLARATION_INVALID skill=$SkillName field=$FieldName declarations=$($allDeclarations.Count) role-declarations=$($roleDeclarations.Count) canonical=$($allMatches.Count) role-canonical=$($roleMatches.Count)"
    }
    return $roleMatches[0].Groups['value'].Value
}

function Assert-SkillCapabilityMatrix {
    param([object] $Policy, [string] $SkillRoot, [switch] $ValidateFiles)

    $capabilities = @(Get-Property $Policy 'skillCapabilities')
    $activeSkills = @($Policy.activeSkills | ForEach-Object { [string]$_ } | Sort-Object)
    if ($capabilities.Count -ne 12) { throw "SKILL_CAPABILITY_COUNT expected=12 actual=$($capabilities.Count)" }
    $capabilityNames = @($capabilities | ForEach-Object { [string]$_.name } | Sort-Object)
    Assert-Equal $activeSkills $capabilityNames 'SKILL_CAPABILITY_INVENTORY_MISMATCH'

    $requiredFields = @(
        'name', 'roleType', 'role', 'triggerClass', 'positiveTriggers', 'exclusions', 'inputContext',
        'requiredActions', 'validation', 'outputContract', 'nonGoals', 'primaryResponsibilities',
        'supportingResponsibilities', 'overlapsWith', 'requiresSkills'
    )
    $roleTypes = @('ROUTER', 'PRIMARY_EXECUTION', 'SUPPORTING_CONSTRAINT', 'PRIMARY_VALIDATION')
    $primaryOwners = @{}

    foreach ($capability in $capabilities) {
        $name = [string](Get-Property $capability 'name')
        $fields = @($capability.psobject.Properties.Name)
        if ($fields.Count -ne $requiredFields.Count) { throw "SKILL_CAPABILITY_FIELD_COUNT skill=$name expected=$($requiredFields.Count) actual=$($fields.Count)" }
        foreach ($field in $requiredFields) {
            if ($fields -cnotcontains $field) { throw "SKILL_CAPABILITY_FIELD_MISSING skill=$name field=$field" }
        }
        if ($roleTypes -cnotcontains [string]$capability.roleType) { throw "SKILL_ROLE_TYPE_INVALID skill=$name" }
        if ($capability.role -isnot [string] -or [string]::IsNullOrWhiteSpace([string]$capability.role)) { throw "SKILL_ROLE_MISSING skill=$name" }
        $null = Assert-StringArray $capability.triggerClass "SKILL_TRIGGER_CLASS_MISSING skill=$name"
        $null = Assert-StringArray $capability.positiveTriggers "SKILL_POSITIVE_TRIGGER_MISSING skill=$name"
        $null = Assert-StringArray $capability.exclusions "SKILL_EXCLUSION_MISSING skill=$name"
        $null = Assert-StringArray $capability.inputContext "SKILL_INPUT_CONTEXT_MISSING skill=$name"
        $null = Assert-StringArray $capability.requiredActions "TRIGGER_ONLY_SKILL skill=$name" 3
        $null = Assert-StringArray $capability.outputContract "SKILL_OUTPUT_CONTRACT_MISSING skill=$name"
        $null = Assert-StringArray $capability.nonGoals "SKILL_NON_GOALS_MISSING skill=$name"
        $primaryResponsibilities = @(Assert-StringArray $capability.primaryResponsibilities "SKILL_PRIMARY_RESPONSIBILITY_MISSING skill=$name")
        $null = Assert-StringArray $capability.supportingResponsibilities "SKILL_SUPPORTING_RESPONSIBILITIES_INVALID skill=$name" 0
        $null = Assert-StringArray $capability.requiresSkills "SKILL_DEPENDENCIES_INVALID skill=$name" 0

        $validationFields = @($capability.validation.psobject.Properties.Name)
        foreach ($field in @('required', 'conditional', 'notApplicable')) {
            if ($validationFields -cnotcontains $field) { throw "SKILL_VALIDATION_CLASS_MISSING skill=$name class=$field" }
            $null = Assert-StringArray (Get-Property $capability.validation $field) "SKILL_VALIDATION_CLASS_EMPTY skill=$name class=$field"
        }
        if ($validationFields.Count -ne 3) { throw "SKILL_VALIDATION_FIELDS_INVALID skill=$name" }

        foreach ($responsibility in $primaryResponsibilities) {
            if ($primaryOwners.ContainsKey($responsibility)) {
                throw "DUPLICATED_PRIMARY_OWNERSHIP responsibility=$responsibility owners=$($primaryOwners[$responsibility]),$name"
            }
            $primaryOwners[$responsibility] = $name
        }
    }

    foreach ($capability in $capabilities) {
        $name = [string]$capability.name
        foreach ($dependency in @($capability.requiresSkills)) {
            if ($activeSkills -cnotcontains [string]$dependency -or [string]$dependency -ceq $name) {
                throw "SKILL_DEPENDENCY_INVALID skill=$name dependency=$dependency"
            }
        }
        foreach ($overlap in @($capability.overlapsWith)) {
            $target = [string](Get-Property $overlap 'skill')
            $responsibility = [string](Get-Property $overlap 'responsibility')
            $primaryOwner = [string](Get-Property $overlap 'primaryOwner')
            $supportingOwner = [string](Get-Property $overlap 'supportingOwner')
            if ($activeSkills -cnotcontains $target -or $target -ceq $name) { throw "SKILL_OVERLAP_TARGET_INVALID skill=$name target=$target" }
            if (-not $primaryOwners.ContainsKey($responsibility) -or [string]$primaryOwners[$responsibility] -cne $primaryOwner) {
                throw "SKILL_OVERLAP_PRIMARY_INVALID skill=$name responsibility=$responsibility owner=$primaryOwner"
            }
            if ($activeSkills -cnotcontains $supportingOwner -or $supportingOwner -ceq $primaryOwner) {
                throw "SKILL_OVERLAP_SUPPORTING_INVALID skill=$name responsibility=$responsibility owner=$supportingOwner"
            }
            if (@($name, $target) -cnotcontains $primaryOwner -or @($name, $target) -cnotcontains $supportingOwner) {
                throw "SKILL_OVERLAP_PAIR_INVALID skill=$name target=$target responsibility=$responsibility"
            }
        }
    }

    $inDegree = @{}
    $dependents = @{}
    foreach ($skill in $activeSkills) {
        $inDegree[$skill] = 0
        $dependents[$skill] = New-Object System.Collections.Generic.List[string]
    }
    foreach ($capability in $capabilities) {
        $name = [string]$capability.name
        foreach ($dependency in @($capability.requiresSkills)) {
            $inDegree[$name] = [int]$inDegree[$name] + 1
            $dependents[[string]$dependency].Add($name)
        }
    }
    $queue = New-Object System.Collections.Queue
    foreach ($skill in $activeSkills) { if ([int]$inDegree[$skill] -eq 0) { $queue.Enqueue($skill) } }
    $visited = 0
    while ($queue.Count -gt 0) {
        $skill = [string]$queue.Dequeue()
        $visited++
        foreach ($dependent in $dependents[$skill]) {
            $inDegree[$dependent] = [int]$inDegree[$dependent] - 1
            if ([int]$inDegree[$dependent] -eq 0) { $queue.Enqueue($dependent) }
        }
    }
    if ($visited -ne $activeSkills.Count) { throw "CIRCULAR_SKILL_DEPENDENCIES visited=$visited total=$($activeSkills.Count)" }

    if ($ValidateFiles) {
        $requiredSections = @(
            [pscustomobject]@{ Heading = 'A. Role'; Name = 'Role' },
            [pscustomobject]@{ Heading = 'B. Trigger'; Name = 'Trigger' },
            [pscustomobject]@{ Heading = 'C. Input / Context'; Name = 'Input / Context' },
            [pscustomobject]@{ Heading = 'D. Required Actions'; Name = 'Required Actions' },
            [pscustomobject]@{ Heading = 'E. Validation'; Name = 'Validation' },
            [pscustomobject]@{ Heading = 'F. Output Contract'; Name = 'Output Contract' },
            [pscustomobject]@{ Heading = 'G. Non-goals'; Name = 'Non-goals' },
            [pscustomobject]@{ Heading = 'H. Overlap / Ownership'; Name = 'Overlap / Ownership' }
        )
        $markdownContracts = @{}
        foreach ($capability in $capabilities) {
            $name = [string]$capability.name
            $skillFile = Join-Path $SkillRoot "$name\SKILL.md"
            if (-not (Test-Path -LiteralPath $skillFile -PathType Leaf)) { throw "SKILL_FILE_MISSING skill=$name" }
            $content = [System.IO.File]::ReadAllText($skillFile)
            $frontMatter = [regex]::Match($content, '(?s)\A---\r?\n(?<body>.*?)\r?\n---\r?\n')
            if (-not $frontMatter.Success) { throw "SKILL_FRONT_MATTER_INVALID skill=$name" }
            $frontBody = $frontMatter.Groups['body'].Value
            if ($frontBody -notmatch ("(?m)^name:\s*{0}\s*$" -f [regex]::Escape($name))) { throw "SKILL_FRONT_MATTER_NAME_INVALID skill=$name" }
            $description = [regex]::Match($frontBody, '(?m)^description:\s*(?<value>.+?)\s*$')
            if (-not $description.Success -or [string]::IsNullOrWhiteSpace($description.Groups['value'].Value)) { throw "SKILL_DESCRIPTION_MISSING skill=$name" }
            if ($frontBody -match '(?i)TODO|TBD|PLACEHOLDER|\{\{[^}]+\}\}') { throw "SKILL_FRONT_MATTER_PLACEHOLDER skill=$name" }

            $sectionBodies = @{}
            foreach ($section in $requiredSections) {
                $body = Get-SkillCapabilitySection $content $name ([string]$section.Heading) ([string]$section.Name)
                $sectionBodies[[string]$section.Name] = $body
                $meaningfulLines = @(Get-MeaningfulSkillLines $body)
                if ([string]$section.Name -ceq 'Required Actions') {
                    $actionItems = @($meaningfulLines | Where-Object { $_ -match '^\s*(?:[-*+]|\d+[.)])\s+\S' })
                    if ($actionItems.Count -eq 0) { throw "SKILL_REQUIRED_ACTIONS_EMPTY skill=$name section=Required Actions" }
                } elseif ($meaningfulLines.Count -eq 0) {
                    throw "SKILL_CAPABILITY_SECTION_EMPTY skill=$name section=$($section.Name)"
                }
            }

            $markdownContracts[$name] = [pscustomobject]@{
                roleType = Get-SkillCanonicalDeclaration $content ([string]$sectionBodies['Role']) $name 'Role type'
                primaryResponsibility = Get-SkillCanonicalDeclaration $content ([string]$sectionBodies['Role']) $name 'Primary responsibility'
            }
        }

        foreach ($capability in $capabilities) {
            $name = [string]$capability.name
            $markdownContract = $markdownContracts[$name]
            if ([string]$markdownContract.roleType -cne [string]$capability.roleType) {
                throw "MACHINE_SKILL_CONTRACT_DRIFT skill=$name field=roleType markdown=$($markdownContract.roleType) policy=$($capability.roleType)"
            }
        }

        foreach ($capability in $capabilities) {
            $name = [string]$capability.name
            $policyResponsibilities = @($capability.primaryResponsibilities)
            if ($policyResponsibilities.Count -ne 1) {
                throw "SKILL_PRIMARY_RESPONSIBILITY_COUNT skill=$name expected=1 actual=$($policyResponsibilities.Count)"
            }
            if ([string]$capability.roleType -ceq 'SUPPORTING_CONSTRAINT' -and
                [string]$markdownContracts[$name].primaryResponsibility -cne [string]$policyResponsibilities[0]) {
                throw "MACHINE_SKILL_CONTRACT_DRIFT skill=$name field=primaryResponsibility markdown=$($markdownContracts[$name].primaryResponsibility) policy=$($policyResponsibilities[0])"
            }
        }

        $markdownPrimaryOwners = @{}
        foreach ($capability in $capabilities) {
            $name = [string]$capability.name
            $responsibility = [string]$markdownContracts[$name].primaryResponsibility
            if (-not $markdownPrimaryOwners.ContainsKey($responsibility)) {
                $markdownPrimaryOwners[$responsibility] = New-Object System.Collections.Generic.List[string]
            }
            $markdownPrimaryOwners[$responsibility].Add($name)
        }
        foreach ($responsibility in @($markdownPrimaryOwners.Keys | Sort-Object)) {
            $owners = @($markdownPrimaryOwners[$responsibility] | Sort-Object)
            if ($owners.Count -gt 1) {
                throw "DUPLICATED_PRIMARY_OWNERSHIP responsibility=$responsibility markdown-owners=$($owners -join ',')"
            }
        }

        foreach ($capability in $capabilities) {
            $name = [string]$capability.name
            $policyResponsibility = [string]@($capability.primaryResponsibilities)[0]
            if ([string]$markdownContracts[$name].primaryResponsibility -cne $policyResponsibility) {
                throw "MACHINE_SKILL_CONTRACT_DRIFT skill=$name field=primaryResponsibility markdown=$($markdownContracts[$name].primaryResponsibility) policy=$policyResponsibility"
            }
        }
    }

    return [pscustomobject]@{
        activeSkills = $activeSkills.Count
        roleDefined = $capabilities.Count
        triggerDefined = $capabilities.Count
        inputContextDefined = $capabilities.Count
        requiredActionsDefined = $capabilities.Count
        validationDefined = $capabilities.Count
        outputContractDefined = $capabilities.Count
        nonGoalsDefined = $capabilities.Count
        overlapOwnershipDefined = $capabilities.Count
        primaryResponsibilities = $primaryOwners.Count
    }
}

function Assert-SkillCapabilityMutationRejected {
    param([string] $Name, [object] $Policy, [string] $ExpectedCode)
    $rejected = $false
    try {
        $null = Assert-SkillCapabilityMatrix $Policy ''
    } catch {
        if ($_.Exception.Message -notmatch [regex]::Escape($ExpectedCode)) {
            throw "Capability mutation rejected for the wrong reason: name=$Name expected=$ExpectedCode actual=$($_.Exception.Message)"
        }
        $rejected = $true
    }
    if (-not $rejected) { throw "Invalid Skill capability mutation was accepted: $Name" }
    Write-Output "PASS capability-mutation=$Name result=REJECT"
}

function Assert-SkillCapabilityTempMutationRejected {
    param(
        [string] $Name,
        [object] $Policy,
        [string] $SkillRoot,
        [scriptblock] $Mutation,
        [string] $ExpectedCode
    )

    $tempBase = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd([System.IO.Path]::DirectorySeparatorChar)
    $tempRoot = Join-Path $tempBase ('nq-skill-capability-' + [guid]::NewGuid().ToString('N'))
    $tempSkillRoot = Join-Path $tempRoot 'skills'
    try {
        $null = New-Item -ItemType Directory -Path $tempRoot
        $null = Copy-Item -LiteralPath $SkillRoot -Destination $tempSkillRoot -Recurse
        $tempPolicyPath = Join-Path $tempRoot 'agent-workflow-policy.json'
        [System.IO.File]::WriteAllText(
            $tempPolicyPath,
            (ConvertTo-Json $Policy -Depth 100),
            (New-Object System.Text.UTF8Encoding($false))
        )
        $tempPolicy = [System.IO.File]::ReadAllText($tempPolicyPath, (New-Object System.Text.UTF8Encoding($false))) | ConvertFrom-Json
        $null = & $Mutation $tempPolicy $tempSkillRoot

        $rejected = $false
        try {
            $null = Assert-SkillCapabilityMatrix $tempPolicy $tempSkillRoot -ValidateFiles
        } catch {
            if ($_.Exception.Message -notmatch [regex]::Escape($ExpectedCode)) {
                throw "Capability temp mutation rejected for the wrong reason: name=$Name expected=$ExpectedCode actual=$($_.Exception.Message)"
            }
            $rejected = $true
        }
        if (-not $rejected) { throw "Invalid Skill capability temp mutation was accepted: $Name" }
        Write-Output "PASS capability-temp-mutation=$Name result=REJECT error=$ExpectedCode"
    } finally {
        $tempParent = [System.IO.Path]::GetDirectoryName($tempRoot)
        $tempLeaf = [System.IO.Path]::GetFileName($tempRoot)
        if ($tempParent -ine $tempBase -or -not $tempLeaf.StartsWith('nq-skill-capability-', [System.StringComparison]::Ordinal)) {
            throw "TEMP_FIXTURE_CLEANUP_PATH_INVALID path=$tempRoot"
        }
        if (Test-Path -LiteralPath $tempRoot) { Remove-Item -LiteralPath $tempRoot -Recurse -Force }
    }
}

function Get-ActiveRuntimeContent {
    param([string] $Content, [string] $SurfaceName)
    $active = $Content
    foreach ($classification in @('historical-reference', 'example-or-negative-fixture')) {
        $escaped = [regex]::Escape($classification)
        $startPattern = '<!--\s*nq-runtime-scan:{0}:start\s*-->' -f $escaped
        $endPattern = '<!--\s*nq-runtime-scan:{0}:end\s*-->' -f $escaped
        $starts = [regex]::Matches($active, $startPattern).Count
        $ends = [regex]::Matches($active, $endPattern).Count
        if ($starts -ne $ends) { throw "RUNTIME_CLASSIFICATION_UNBALANCED surface=$SurfaceName classification=$classification" }
        $blockPattern = "(?ms)$startPattern.*?$endPattern"
        $active = [regex]::Replace($active, $blockPattern, '')
    }
    return $active
}

function Assert-NoSpecificActiveRuntimeRules {
    param([object[]] $Surfaces)
    $taskIdPattern = '\bNQ-GATE[A-Z][A-Z0-9_-]*\b|\bAttempt[-_ ]?(?:[0-9]+|\*|\[[^\]]*\]|\\d)'
    $gatePattern = '\bPOST_GATE[A-Z0-9_-]*\b|\bGate[A-Z]\b'
    foreach ($surface in $Surfaces) {
        $active = Get-ActiveRuntimeContent ([string]$surface.Content) ([string]$surface.Name)
        if ($active -cmatch $taskIdPattern) { throw "TASK_ID_SPECIFIC_RUNTIME_RULES found surface=$($surface.Name)" }
        if ($active -cmatch $gatePattern) { throw "GATE_SPECIFIC_ACTIVE_RUNTIME_RULES found surface=$($surface.Name)" }
    }
}

function Assert-AuditBootstrapPolicy {
    param([object] $Policy, [switch] $ValidateTarget)
    $auditPolicy = Get-Property $Policy 'audit'
    if ($null -eq $auditPolicy) { throw 'AUDIT_BOOTSTRAP_POLICY_MISSING' }
    $fields = @($auditPolicy.psobject.Properties.Name)
    if ($fields.Count -ne 1 -or $fields -cnotcontains 'bootstrapCharter') { throw 'AUDIT_BOOTSTRAP_POLICY_FIELDS_INVALID' }
    $charter = Get-Property $auditPolicy 'bootstrapCharter'
    if ($charter -isnot [string] -or [string]::IsNullOrWhiteSpace($charter) -or $charter -cne $charter.Trim() -or
        [System.IO.Path]::IsPathRooted($charter) -or $charter.Contains('\') -or
        $charter -notmatch '^docs/audit/[A-Z0-9][A-Z0-9_-]*\.md$') {
        throw 'AUDIT_BOOTSTRAP_PATH_INVALID'
    }
    $auditRoot = [System.IO.Path]::GetFullPath((Join-Path $repoRoot 'docs/audit'))
    $resolved = [System.IO.Path]::GetFullPath((Join-Path $repoRoot $charter))
    if (-not $resolved.StartsWith($auditRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw 'AUDIT_BOOTSTRAP_PATH_OUTSIDE_ROOT'
    }
    if ($ValidateTarget) {
        if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) { throw 'AUDIT_BOOTSTRAP_CHARTER_MISSING' }
        $charters = @(Get-ChildItem -LiteralPath $auditRoot -File | Where-Object { $_.Name -match '(?i)AUDIT_BOOTSTRAP_CHARTER\.md$' })
        if ($charters.Count -ne 1 -or $charters[0].FullName -cne $resolved) { throw "ACTIVE_AUDIT_CHARTERS_INVALID count=$($charters.Count)" }
    }
    return [string]$charter
}

function Assert-RuntimeMutationRejected {
    param([string] $Name, [string] $Content, [string] $ExpectedCode)
    $rejected = $false
    try {
        Assert-NoSpecificActiveRuntimeRules @([pscustomobject]@{ Name = $Name; Content = $Content })
    } catch {
        if ($_.Exception.Message -notmatch [regex]::Escape($ExpectedCode)) {
            throw "Runtime mutation rejected for the wrong reason: name=$Name expected=$ExpectedCode actual=$($_.Exception.Message)"
        }
        $rejected = $true
    }
    if (-not $rejected) { throw "Gate/Task-specific runtime mutation was accepted: $Name" }
    Write-Output "PASS runtime-mutation=$Name result=REJECT"
}

function Assert-AuditPolicyMutationRejected {
    param([string] $Name, [object] $Policy, [string] $ExpectedCode)
    $rejected = $false
    try {
        $null = Assert-AuditBootstrapPolicy $Policy -ValidateTarget
    } catch {
        if ($_.Exception.Message -notmatch [regex]::Escape($ExpectedCode)) {
            throw "Audit policy mutation rejected for the wrong reason: name=$Name expected=$ExpectedCode actual=$($_.Exception.Message)"
        }
        $rejected = $true
    }
    if (-not $rejected) { throw "Invalid audit bootstrap policy was accepted: $Name" }
    Write-Output "PASS audit-policy-mutation=$Name result=REJECT"
}

function Assert-RouteSafetyInvariants {
    param([string] $Category, [object] $Route)
    switch ($Category) {
        'RELEASE_TAG' {
            if ($Route.gitPublicationPermission -cne 'EXPLICIT_AUTHORIZATION_REQUIRED') { throw 'RELEASE_PUBLICATION_NOT_EXPLICIT' }
            if ($Route.independentReview -ne $true) { throw 'RELEASE_REVIEW_REQUIRED' }
        }
        'GATE_FREEZE' {
            if ($Route.independentReview -ne $true) { throw 'GATE_FREEZE_REVIEW_REQUIRED' }
        }
        'SECURITY_AUDIT' {
            if ($Route.credentialPermission -cne 'DENIED') { throw 'SECURITY_AUDIT_CREDENTIAL_DENIED' }
        }
        'FULL_REPOSITORY_AUDIT' {
            if ($Route.serverPermission -cne 'DENIED') { throw 'FULL_AUDIT_SERVER_DENIED' }
            if ($Route.authorityMutationPermission -cne 'DENIED') { throw 'FULL_AUDIT_AUTHORITY_DENIED' }
            if ($Route.gitPublicationPermission -cne 'DENIED') { throw 'FULL_AUDIT_PUBLICATION_DENIED' }
        }
        'CREDENTIAL_OR_REAL_EXCHANGE' {
            if ($Route.credentialPermission -cne 'DENIED') { throw 'SENSITIVE_CREDENTIAL_DENIED' }
            if ($Route.riskLevel -cne 'BLOCKED') { throw 'SENSITIVE_ROUTE_NOT_BLOCKED' }
        }
        'JAVA_CHANGE' {
            if ($Route.independentReview -ne $false) { throw 'ORDINARY_JAVA_REVIEW_NOT_OPTIONAL' }
            foreach ($forbidden in @('INDEPENDENT_SECURITY_REVIEW', 'CODERABBIT', 'FULL_SHADOW_SCAN', 'FULL_JAVA_SHADOW_SCAN')) {
                if (@($Route.requiredValidations) -ccontains $forbidden) { throw "ORDINARY_JAVA_OVER_GOVERNED validation=$forbidden" }
            }
        }
        'FRONTEND_CHANGE' {
            if (@($Route.requiredValidations) -ccontains 'FIGMA_REQUIRED') { throw 'ORDINARY_FRONTEND_FIGMA_REQUIRED' }
            if (@($Route.supportingSkills).Count -ne 0) { throw 'ORDINARY_FRONTEND_ALL_SKILLS_LOADED' }
        }
    }
}

function Assert-AgentRoute {
    param([object] $Policy, [string] $Category, [object] $Route, [object] $Expected)
    $requiredFields = @(
        'taskType', 'riskLevel', 'primarySkill', 'supportingSkills', 'requiredValidations', 'independentReview',
        'docsBudget', 'networkPermission', 'credentialPermission', 'serverPermission',
        'gitPublicationPermission', 'authorityMutationPermission'
    )
    $actualFields = @($Route.psobject.Properties.Name)
    if ($actualFields.Count -ne $requiredFields.Count) { throw "ROUTE_FIELD_COUNT category=$Category expected=$($requiredFields.Count) actual=$($actualFields.Count)" }
    foreach ($field in $requiredFields) {
        if ($actualFields -cnotcontains $field) { throw "ROUTE_FIELD_MISSING category=$Category field=$field" }
    }
    Assert-RouteSafetyInvariants $Category $Route

    foreach ($field in @('taskType', 'riskLevel', 'docsBudget', 'networkPermission', 'credentialPermission', 'serverPermission', 'gitPublicationPermission', 'authorityMutationPermission')) {
        $allowed = @(Get-Property $Policy.enums $field)
        $actual = [string](Get-Property $Route $field)
        if ($allowed.Count -eq 0 -or $allowed -cnotcontains $actual) { throw "ENUM_INVALID category=$Category field=$field value=$actual" }
    }
    if ((Get-Property $Route 'independentReview') -isnot [bool]) { throw "BOOLEAN_INVALID category=$Category field=independentReview" }
    if (@($Policy.activeSkills) -cnotcontains [string]$Route.primarySkill) { throw "PRIMARY_SKILL_INACTIVE category=$Category" }
    foreach ($skill in @($Route.supportingSkills)) {
        if (@($Policy.activeSkills) -cnotcontains [string]$skill) { throw "SUPPORTING_SKILL_INACTIVE category=$Category skill=$skill" }
    }
    if (@($Route.supportingSkills | Sort-Object -Unique).Count -ne @($Route.supportingSkills).Count) { throw "SUPPORTING_SKILL_DUPLICATED category=$Category" }
    $requiredValidations = @($Route.requiredValidations)
    if ($requiredValidations.Count -eq 0) { throw "VALIDATION_MISSING category=$Category" }
    if (@($requiredValidations | Sort-Object -Unique).Count -ne $requiredValidations.Count) { throw "VALIDATION_DUPLICATED category=$Category" }
    foreach ($validation in $requiredValidations) {
        if (@($Policy.enums.requiredValidations) -cnotcontains [string]$validation) { throw "VALIDATION_ENUM_INVALID category=$Category value=$validation" }
    }

    if ($null -ne $Expected) {
        $expectedFields = @($Expected.psobject.Properties.Name)
        if ($expectedFields.Count -ne $requiredFields.Count) { throw "EXPECTED_FIELD_COUNT category=$Category expected=$($requiredFields.Count) actual=$($expectedFields.Count)" }
        foreach ($field in $requiredFields) {
            if ($expectedFields -cnotcontains $field) { throw "EXPECTED_FIELD_MISSING category=$Category field=$field" }
            Assert-Equal (Get-Property $Expected $field) (Get-Property $Route $field) "Fixture output mismatch category=$Category field=$field"
        }
    }
}

$policy = Read-Json $PolicyPath
$fixtureSet = Read-Json $FixturePath
if ($policy.schemaVersion -cne '1.2.0' -or $fixtureSet.schemaVersion -cne '1.2.0') { throw 'Unsupported Agent workflow schema.' }
$auditCharterPath = Assert-AuditBootstrapPolicy $policy -ValidateTarget
if ($null -eq $policy.enums) { throw 'Agent workflow enums missing.' }
foreach ($enumProperty in @($policy.enums.psobject.Properties)) {
    $values = @($enumProperty.Value | ForEach-Object { [string]$_ })
    if ($values.Count -eq 0 -or @($values | Sort-Object -Unique).Count -ne $values.Count) { throw "Agent workflow enum invalid: $($enumProperty.Name)" }
}
if (@($policy.activeSkills | Sort-Object -Unique).Count -ne @($policy.activeSkills).Count) { throw 'Active Skills must be unique.' }

$fixtures = @($fixtureSet.fixtures)
if ($fixtures.Count -ne 12) { throw "Expected 12 fixtures, found $($fixtures.Count)." }
if (@($fixtures.id | Sort-Object -Unique).Count -ne 12) { throw 'Fixture ids must be unique.' }
foreach ($fixture in $fixtures) {
    $route = Get-Property $policy.routes ([string]$fixture.category)
    if ($null -eq $route) { throw "Unknown fixture category id=$($fixture.id) category=$($fixture.category)" }
    if ($null -eq $fixture.expected) { throw "Fixture expected output missing id=$($fixture.id)" }
    Assert-AgentRoute $policy ([string]$fixture.category) $route $fixture.expected
    Write-Output ("PASS fixture={0} taskType={1} risk={2} primary={3}" -f $fixture.id, $route.taskType, $route.riskLevel, $route.primarySkill)
}

$mutations = @($fixtureSet.maliciousMutations)
if ($mutations.Count -lt 6 -or @($mutations.id | Sort-Object -Unique).Count -ne $mutations.Count) { throw 'Malicious mutation fixtures are missing or duplicated.' }
foreach ($mutation in $mutations) {
    $mutatedPolicy = (ConvertTo-Json $policy -Depth 100) | ConvertFrom-Json
    $route = Get-Property $mutatedPolicy.routes ([string]$mutation.category)
    if ($null -eq $route) { throw "Mutation category missing: $($mutation.id)" }
    $property = @($route.psobject.Properties | Where-Object { $_.Name -ceq [string]$mutation.field })
    if ($property.Count -ne 1) { throw "Mutation field missing: $($mutation.id)" }
    $property[0].Value = $mutation.value
    $rejected = $false
    try {
        Assert-AgentRoute $mutatedPolicy ([string]$mutation.category) $route $null
    } catch {
        if ($_.Exception.Message -notmatch [regex]::Escape([string]$mutation.expectedError)) {
            throw "Mutation rejected for the wrong reason id=$($mutation.id) expected=$($mutation.expectedError) actual=$($_.Exception.Message)"
        }
        $rejected = $true
    }
    if (-not $rejected) { throw "Malicious mutation was accepted: $($mutation.id)" }
    Write-Output "PASS malicious-mutation=$($mutation.id) result=REJECT"
}

$migration = Get-Property $policy.routes 'MIGRATION_CHANGE'
foreach ($required in @('FORWARD_ONLY', 'HISTORICAL_MIGRATION_IMMUTABLE')) {
    if (@($migration.requiredValidations) -cnotcontains $required) { throw "Migration invariant missing: $required" }
}
$docs = Get-Property $policy.routes 'DOCS_ONLY'
if ($docs.networkPermission -cne 'DENIED' -or @($docs.requiredValidations) -match 'MAVEN|PLAYWRIGHT|NOTION') { throw 'Docs-only route has unrelated work.' }
$audit = Get-Property $policy.routes 'FULL_REPOSITORY_AUDIT'
foreach ($required in @('AUDIT_BOOTSTRAP_CHARTER', 'FULL_INVENTORY', 'NO_AUTO_REMEDIATION')) {
    if (@($audit.requiredValidations) -cnotcontains $required) { throw "Full audit invariant missing: $required" }
}

$resolvedAgentRoot = Resolve-RepoPath $AgentRoot
$skillRoot = Join-Path $resolvedAgentRoot 'skills'
if (-not (Test-Path -LiteralPath $skillRoot -PathType Container)) { throw "Agent Skill root missing: $skillRoot" }
$actualSkills = @(Get-ChildItem -LiteralPath $skillRoot -Directory | Where-Object {
    Test-Path -LiteralPath (Join-Path $_.FullName 'SKILL.md') -PathType Leaf
} | ForEach-Object { $_.Name } | Sort-Object)
$declaredSkills = @($policy.activeSkills | ForEach-Object { [string]$_ } | Sort-Object)
Assert-Equal $declaredSkills $actualSkills 'Active Skill directories differ from policy.'
$capabilitySummary = Assert-SkillCapabilityMatrix $policy $skillRoot -ValidateFiles

$triggerOnlyMutation = (ConvertTo-Json $policy -Depth 100) | ConvertFrom-Json
$triggerOnlyMutation.skillCapabilities[0].requiredActions = @()
Assert-SkillCapabilityMutationRejected 'trigger-only-skill' $triggerOnlyMutation 'TRIGGER_ONLY_SKILL'

$duplicateOwnerMutation = (ConvertTo-Json $policy -Depth 100) | ConvertFrom-Json
$duplicateOwnerMutation.skillCapabilities[1].primaryResponsibilities = @([string]$duplicateOwnerMutation.skillCapabilities[0].primaryResponsibilities[0])
Assert-SkillCapabilityMutationRejected 'duplicated-primary-owner' $duplicateOwnerMutation 'DUPLICATED_PRIMARY_OWNERSHIP'

$circularDependencyMutation = (ConvertTo-Json $policy -Depth 100) | ConvertFrom-Json
$firstCapabilityName = [string]$circularDependencyMutation.skillCapabilities[0].name
$secondCapabilityName = [string]$circularDependencyMutation.skillCapabilities[1].name
$circularDependencyMutation.skillCapabilities[0].requiresSkills = @($secondCapabilityName)
$circularDependencyMutation.skillCapabilities[1].requiresSkills = @($firstCapabilityName)
Assert-SkillCapabilityMutationRejected 'circular-skill-dependency' $circularDependencyMutation 'CIRCULAR_SKILL_DEPENDENCIES'

Assert-SkillCapabilityTempMutationRejected 'A-required-actions-destroyed' $policy $skillRoot {
    param([object] $tempPolicy, [string] $tempSkillRoot)
    $skillFile = Join-Path $tempSkillRoot 'java-backend-maintenance\SKILL.md'
    $content = [System.IO.File]::ReadAllText($skillFile)
    $requiredActionsPattern = '(?ms)(^## D\. Required Actions\s*$\r?\n).*?(?=^## E\. Validation\s*$)'
    $requiredActionsMatches = [regex]::Matches($content, $requiredActionsPattern)
    if ($requiredActionsMatches.Count -ne 1) { throw "TEMP_MUTATION_TARGET_INVALID name=A count=$($requiredActionsMatches.Count)" }
    $mutated = [regex]::Replace($content, $requiredActionsPattern, '$1')
    [System.IO.File]::WriteAllText($skillFile, $mutated, (New-Object System.Text.UTF8Encoding($false)))
} 'SKILL_REQUIRED_ACTIONS_EMPTY'

Assert-SkillCapabilityTempMutationRejected 'B-duplicate-frontend-primary-ownership' $policy $skillRoot {
    param([object] $tempPolicy, [string] $tempSkillRoot)
    $skillFile = Join-Path $tempSkillRoot 'frontend-product-ui-design\SKILL.md'
    $content = [System.IO.File]::ReadAllText($skillFile)
    $original = '- Primary responsibility: `BUSINESS_UX_DESIGN`'
    if ([regex]::Matches($content, [regex]::Escape($original)).Count -ne 1) { throw 'TEMP_MUTATION_TARGET_INVALID name=B' }
    $mutated = $content.Replace($original, '- Primary responsibility: `FRONTEND_IMPLEMENTATION`')
    [System.IO.File]::WriteAllText($skillFile, $mutated, (New-Object System.Text.UTF8Encoding($false)))
} 'DUPLICATED_PRIMARY_OWNERSHIP'

Assert-SkillCapabilityTempMutationRejected 'C-java-standard-steals-implementation' $policy $skillRoot {
    param([object] $tempPolicy, [string] $tempSkillRoot)
    $skillFile = Join-Path $tempSkillRoot 'nq-java-engineering-standard\SKILL.md'
    $content = [System.IO.File]::ReadAllText($skillFile)
    $originalRole = '- Role type: `SUPPORTING_CONSTRAINT`'
    $originalResponsibility = '- Primary responsibility: `HIGH_RISK_JAVA_CONSTRAINT_EVALUATION`'
    if ([regex]::Matches($content, [regex]::Escape($originalRole)).Count -ne 1 -or
        [regex]::Matches($content, [regex]::Escape($originalResponsibility)).Count -ne 1) {
        throw 'TEMP_MUTATION_TARGET_INVALID name=C'
    }
    $mutated = $content.Replace($originalRole, '- Role type: `PRIMARY_EXECUTION`')
    $mutated = $mutated.Replace($originalResponsibility, '- Primary responsibility: `JAVA_BACKEND_IMPLEMENTATION`')
    [System.IO.File]::WriteAllText($skillFile, $mutated, (New-Object System.Text.UTF8Encoding($false)))
} 'MACHINE_SKILL_CONTRACT_DRIFT'

Assert-SkillCapabilityTempMutationRejected 'D-circular-skill-dependency' $policy $skillRoot {
    param([object] $tempPolicy, [string] $tempSkillRoot)
    $firstCapabilityName = [string]$tempPolicy.skillCapabilities[0].name
    $secondCapabilityName = [string]$tempPolicy.skillCapabilities[1].name
    $tempPolicy.skillCapabilities[0].requiresSkills = @($secondCapabilityName)
    $tempPolicy.skillCapabilities[1].requiresSkills = @($firstCapabilityName)
} 'CIRCULAR_SKILL_DEPENDENCIES'

foreach ($skill in $actualSkills) {
    $skillFile = Join-Path $skillRoot "$skill\SKILL.md"
    $skillText = [System.IO.File]::ReadAllText($skillFile)
    if ($skillText -notmatch ("(?m)^name:\s*{0}\s*$" -f [regex]::Escape($skill))) { throw "Skill front matter name differs from directory: $skill" }
}
foreach ($routeProperty in $policy.routes.psobject.Properties) {
    Assert-AgentRoute $policy $routeProperty.Name $routeProperty.Value $null
}

$readme = [System.IO.File]::ReadAllText((Join-Path $resolvedAgentRoot 'README.md'))
$match = [regex]::Match($readme, '(?s)<!--\s*nq-active-skills:start\s*(?<body>.*?)\s*nq-active-skills:end\s*-->')
if (-not $match.Success) { throw 'Active Skill README block missing.' }
$readmeSkills = @(($match.Groups['body'].Value -split '\r?\n') | ForEach-Object { $_.Trim() } | Where-Object { $_ } | Sort-Object)
Assert-Equal $declaredSkills $readmeSkills 'Active Skill README differs from policy.'

if ([string]::IsNullOrWhiteSpace($CandidateAgentsPath)) {
    $CandidateAgentsPath = if ([System.IO.Path]::GetFileName($resolvedAgentRoot) -ceq '.agents.review-subject') {
        '.review-subject/AGENTS.md'
    } else {
        'AGENTS.md'
    }
}
if ([string]::IsNullOrWhiteSpace($CandidateClaudePath)) {
    $CandidateClaudePath = if ([System.IO.Path]::GetFileName($resolvedAgentRoot) -ceq '.agents.review-subject') {
        '.review-subject/CLAUDE.md'
    } else {
        'CLAUDE.md'
    }
}

$runtimeSurfacePaths = [ordered]@{
    'candidate-agents' = Resolve-RepoPath $CandidateAgentsPath
    'candidate-claude' = Resolve-RepoPath $CandidateClaudePath
    'active-router' = Join-Path $skillRoot 'nq-dh-workflow-router\SKILL.md'
    'active-docs-writer' = Join-Path $skillRoot 'nq-docs-writer\SKILL.md'
    'governance-contract' = Join-Path $repoRoot 'scripts\docs\governance-workflow-contract.json'
    'governance-library' = Join-Path $repoRoot 'scripts\docs\governance-workflow-lib.ps1'
    'authority-checker' = Join-Path $repoRoot 'scripts\docs\check-current-authority.ps1'
    'agent-policy' = Resolve-RepoPath $PolicyPath
    'audit-bootstrap-charter' = Resolve-RepoPath $auditCharterPath
}
$runtimeSurfaces = New-Object System.Collections.Generic.List[object]
foreach ($surface in $runtimeSurfacePaths.GetEnumerator()) {
    if (-not (Test-Path -LiteralPath $surface.Value -PathType Leaf)) { throw "ACTIVE_RUNTIME_SURFACE_MISSING surface=$($surface.Key) path=$($surface.Value)" }
    $runtimeSurfaces.Add([pscustomobject]@{
        Name = [string]$surface.Key
        Content = [System.IO.File]::ReadAllText([string]$surface.Value)
    })
}
Assert-NoSpecificActiveRuntimeRules $runtimeSurfaces

$routerContent = [string](@($runtimeSurfaces | Where-Object { $_.Name -ceq 'active-router' })[0].Content)
$docsWriterContent = [string](@($runtimeSurfaces | Where-Object { $_.Name -ceq 'active-docs-writer' })[0].Content)
Assert-RuntimeMutationRejected 'router-post-gate-charter' ($routerContent + "`nFULL_REPOSITORY_AUDIT -> POST_GATEY_AUDIT_BOOTSTRAP_CHARTER") 'GATE_SPECIFIC_ACTIVE_RUNTIME_RULES'
Assert-RuntimeMutationRejected 'docs-writer-gate-audit-rule' ($docsWriterContent + "`nGateY audit must use a Gate-specific charter.") 'GATE_SPECIFIC_ACTIVE_RUNTIME_RULES'
$policyRuntimeMutation = (ConvertTo-Json $policy -Depth 100) | ConvertFrom-Json
$policyRuntimeMutation.audit | Add-Member -NotePropertyName 'runtimeMatcher' -NotePropertyValue 'GateY|Attempt-13|NQ-GATEY-ROUTE'
Assert-RuntimeMutationRejected 'policy-task-id-matcher' ($policyRuntimeMutation | ConvertTo-Json -Depth 100) 'TASK_ID_SPECIFIC_RUNTIME_RULES'

$missingAuditPolicy = (ConvertTo-Json $policy -Depth 100) | ConvertFrom-Json
$missingAuditPolicy.psobject.Properties.Remove('audit')
Assert-AuditPolicyMutationRejected 'missing-audit-policy' $missingAuditPolicy 'AUDIT_BOOTSTRAP_POLICY_MISSING'
$outsideAuditPolicy = (ConvertTo-Json $policy -Depth 100) | ConvertFrom-Json
$outsideAuditPolicy.audit.bootstrapCharter = '../AUDIT_BOOTSTRAP_CHARTER.md'
Assert-AuditPolicyMutationRejected 'outside-audit-root' $outsideAuditPolicy 'AUDIT_BOOTSTRAP_PATH_INVALID'
$missingCharterPolicy = (ConvertTo-Json $policy -Depth 100) | ConvertFrom-Json
$missingCharterPolicy.audit.bootstrapCharter = 'docs/audit/MISSING_AUDIT_BOOTSTRAP_CHARTER.md'
Assert-AuditPolicyMutationRejected 'missing-audit-charter' $missingCharterPolicy 'AUDIT_BOOTSTRAP_CHARTER_MISSING'

$claude = [System.IO.File]::ReadAllText((Resolve-RepoPath $CandidateClaudePath))
if ($claude -match '(?im)^\s*(Current stage|Next allowed)\s*:|Gate[A-Z].*completed|\b[0-9]+\s+tests\b') { throw 'CLAUDE_CURRENT_STAGE_CLAIMS found.' }

Write-Output "SUMMARY fixtures=12 passed=12 malicious-mutations=$($mutations.Count) rejected=$($mutations.Count) capability-negative=3 capability-temp-negative=4 ROLE_DEFINED=$($capabilitySummary.roleDefined)/12 TRIGGER_DEFINED=$($capabilitySummary.triggerDefined)/12 INPUT_CONTEXT_DEFINED=$($capabilitySummary.inputContextDefined)/12 REQUIRED_ACTIONS_DEFINED=$($capabilitySummary.requiredActionsDefined)/12 VALIDATION_DEFINED=$($capabilitySummary.validationDefined)/12 OUTPUT_CONTRACT_DEFINED=$($capabilitySummary.outputContractDefined)/12 NON_GOALS_DEFINED=$($capabilitySummary.nonGoalsDefined)/12 OVERLAP_OWNERSHIP_DEFINED=$($capabilitySummary.overlapOwnershipDefined)/12 TRIGGER_ONLY_SKILLS=0 MACHINE_SKILL_CONTRACT_DRIFT=0 DUPLICATED_PRIMARY_OWNERSHIP=0 CIRCULAR_SKILL_DEPENDENCIES=0 runtime-negative=3 audit-policy-negative=3 MISSING_SKILLS=0 UNDECLARED_SKILLS=0 ACTIVE_AUDIT_CHARTERS=1 TASK_ID_SPECIFIC_RUNTIME_RULES=0 GATE_SPECIFIC_ACTIVE_RUNTIME_RULES=0 CLAUDE_CURRENT_STAGE_CLAIMS=0"
exit 0
