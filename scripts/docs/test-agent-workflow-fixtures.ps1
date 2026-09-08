[CmdletBinding()]
param(
    [string] $PolicyPath = 'scripts/docs/agent-workflow-policy.json',
    [string] $FixturePath = 'scripts/docs/agent-workflow-fixtures.json',
    [string] $AgentRoot = '.agents'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path

function Resolve-InputPath([string] $Path) {
    if ([IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path $repoRoot $Path
}
function Read-Json([string] $Path) {
    return [IO.File]::ReadAllText((Resolve-InputPath $Path)) | ConvertFrom-Json
}
function Copy-Data($Value) {
    $copy = ($Value | ConvertTo-Json -Depth 30) | ConvertFrom-Json
    if ($copy -is [array]) { foreach ($item in $copy) { Write-Output $item } } else { return $copy }
}
function Assert-Condition([bool] $Condition, [string] $Code) {
    if (-not $Condition) { throw $Code }
}
function Assert-Strings($Values, [string] $Code, [switch] $AllowEmpty) {
    Assert-Condition ($Values -is [array]) $Code
    if (-not $AllowEmpty) { Assert-Condition ($Values.Count -gt 0) $Code }
    foreach ($value in $Values) {
        Assert-Condition ($value -is [string] -and -not [string]::IsNullOrWhiteSpace($value) -and $value -ceq $value.Trim()) $Code
    }
    Assert-Condition (@($Values | Sort-Object -Unique).Count -eq $Values.Count) $Code
}
function Assert-SameSet($Actual, $Expected, [string] $Code) {
    $left = (@($Actual | Sort-Object) | ConvertTo-Json -Compress)
    $right = (@($Expected | Sort-Object) | ConvertTo-Json -Compress)
    Assert-Condition ($left -ceq $right) $Code
}

function Read-SkillInventory([string] $Root) {
    $skillRoot = Join-Path (Resolve-InputPath $Root) 'skills'
    Assert-Condition (Test-Path -LiteralPath $skillRoot -PathType Container) 'SKILL_ROOT_MISSING'
    $items = @(Get-ChildItem -LiteralPath $skillRoot -Recurse -Force)
    foreach ($item in $items) {
        Assert-Condition (-not ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) 'SKILL_REPARSE_POINT'
    }
    $inventory = @()
    foreach ($directory in @(Get-ChildItem -LiteralPath $skillRoot -Directory -Force)) {
        $file = Join-Path $directory.FullName 'SKILL.md'
        Assert-Condition (Test-Path -LiteralPath $file -PathType Leaf) 'SKILL_FILE_MISSING'
        $inventory += [pscustomobject]@{ id = $directory.Name; text = [IO.File]::ReadAllText($file) }
    }
    Assert-Condition (@($items | Where-Object { -not $_.PSIsContainer -and $_.Name -ceq 'SKILL.md' }).Count -eq $inventory.Count) 'NESTED_SKILL_UNDECLARED'
    return $inventory
}

# 此处只保留安全语义下限；能力名称、数量与触发词均从 policy 读取。
$minimumProofs = @{
    migration = @('POSTGRESQL_PROOF', 'FORWARD_ONLY', 'HISTORICAL_MIGRATION_IMMUTABLE')
    schema = @('POSTGRESQL_PROOF', 'SCHEMA_COMPATIBILITY')
    trading = @('POSTGRESQL_PROOF', 'STATE_IDEMPOTENCY_RISK_AUDIT')
    accounting = @('POSTGRESQL_PROOF', 'ACCOUNTING_CONSISTENCY')
    concurrency = @('TRANSACTION_CONCURRENCY_PROOF')
    credential = @('EXPLICIT_AUTHORIZATION', 'SECRET_PROTECTION')
    security = @('SECURITY_BOUNDARY_PROOF')
    live = @('EXPLICIT_AUTHORIZATION', 'CURRENT_AUTHORITY', 'ENVIRONMENT_ISOLATION')
    real_provider = @('EXPLICIT_AUTHORIZATION', 'CURRENT_AUTHORITY', 'ENVIRONMENT_ISOLATION')
    production = @('EXPLICIT_AUTHORIZATION', 'DEPLOYMENT_RECOVERY_PROOF')
    ci_release_trust = @('TRUST_BOUNDARY_PROOF', 'EXACT_HEAD_CI')
}

function Assert-Policy($Policy, $Inventory) {
    Assert-Condition ($Policy.schemaVersion -eq 2) 'POLICY_SCHEMA_INVALID'
    Assert-Condition ($Policy.canonicalSkills -is [array] -and $Policy.canonicalSkills.Count -gt 0) 'CANONICAL_INVENTORY_EMPTY'
    Assert-Strings $Policy.retiredSkills 'RETIRED_INVENTORY_INVALID'
    $ids = @($Policy.canonicalSkills | ForEach-Object { $_.id })
    Assert-Condition (@($ids | Sort-Object -Unique).Count -eq $ids.Count) 'DUPLICATE_CANONICAL_IDENTITY'
    $triggers = @()
    foreach ($skill in $Policy.canonicalSkills) {
        Assert-Condition ($skill.id -is [string] -and $skill.id -cmatch '^[a-z0-9]+(?:-[a-z0-9]+)*$') 'INVALID_SKILL_ID'
        Assert-Condition ($Policy.retiredSkills -cnotcontains $skill.id) 'LEGACY_ACTIVE_SKILL'
        Assert-Strings $skill.triggers 'SKILL_TRIGGER_INVALID'
        Assert-Strings $skill.riskTags 'SKILL_RISK_INVALID' -AllowEmpty
        foreach ($tag in $skill.riskTags) { Assert-Condition ($Policy.riskRequirements.PSObject.Properties.Name -ccontains $tag) 'UNKNOWN_RISK' }
        $triggers += $skill.triggers
    }
    Assert-Condition (@($triggers | Sort-Object -Unique).Count -eq $triggers.Count) 'DUPLICATE_TRIGGER'
    foreach ($item in $Inventory) {
        Assert-Condition ($Policy.retiredSkills -cnotcontains $item.id) 'LEGACY_ACTIVE_SKILL'
    }
    Assert-SameSet @($Inventory | ForEach-Object { $_.id }) $ids 'INVENTORY_MISMATCH'
    foreach ($item in $Inventory) {
        $front = [regex]::Match($item.text, '\A---\r?\n(?<body>.*?)\r?\n---(?:\r?\n|$)', [Text.RegularExpressions.RegexOptions]::Singleline)
        Assert-Condition $front.Success 'SKILL_FRONTMATTER_INVALID'
        $names = [regex]::Matches($front.Groups['body'].Value, '(?m)^name:\s*(?<value>[^\r\n]+)\r?$')
        Assert-Condition ($names.Count -eq 1 -and $names[0].Groups['value'].Value.Trim() -ceq $item.id) 'SKILL_IDENTITY_MISMATCH'
        $descriptions = [regex]::Matches($front.Groups['body'].Value, '(?m)^description:[ \t]*(?<value>[^\r\n]*)\r?$')
        Assert-Condition ($descriptions.Count -eq 1 -and -not [string]::IsNullOrWhiteSpace($descriptions[0].Groups['value'].Value)) 'SKILL_DESCRIPTION_MISSING'
    }
    foreach ($tag in $minimumProofs.Keys) {
        Assert-Condition ($Policy.riskRequirements.PSObject.Properties.Name -ccontains $tag) 'HIGH_RISK_REQUIREMENT_WEAKENED'
        $rule = $Policy.riskRequirements.$tag
        Assert-Condition ($rule.risk -ceq 'HIGH_RISK' -and $rule.independentReview -is [bool] -and $rule.independentReview) 'HIGH_RISK_REQUIREMENT_WEAKENED'
        Assert-Strings $rule.proofs 'HIGH_RISK_REQUIREMENT_WEAKENED'
        foreach ($proof in $minimumProofs[$tag]) {
            Assert-Condition ($rule.proofs -ccontains $proof) 'HIGH_RISK_REQUIREMENT_WEAKENED'
        }
    }
    foreach ($property in $Policy.riskRequirements.PSObject.Properties) {
        Assert-Condition ($property.Value.risk -ceq 'HIGH_RISK' -and $property.Value.independentReview -is [bool] -and $property.Value.independentReview) 'HIGH_RISK_REQUIREMENT_WEAKENED'
        Assert-Strings $property.Value.proofs 'HIGH_RISK_REQUIREMENT_WEAKENED'
    }
}

# fixture 已提供语义标签；这里不冒充自然语言分类器，也不强制真实任务先调用路由器。
function Resolve-Capabilities($Policy, $Task) {
    Assert-Strings $Task.capabilities 'INVALID_CAPABILITIES' -AllowEmpty
    Assert-Strings $Task.riskTags 'INVALID_RISK_TAGS' -AllowEmpty
    $skills = @()
    $effectiveRisks = @($Task.riskTags)
    foreach ($trigger in $Task.capabilities) {
        $matched = @($Policy.canonicalSkills | Where-Object { $_.triggers -ccontains $trigger })
        Assert-Condition ($matched.Count -eq 1) 'UNKNOWN_CAPABILITY'
        $skills += $matched[0].id
        $effectiveRisks += $matched[0].riskTags
    }
    $proofs = @()
    foreach ($tag in @($effectiveRisks | Sort-Object -Unique)) {
        Assert-Condition ($Policy.riskRequirements.PSObject.Properties.Name -ccontains $tag) 'UNKNOWN_RISK'
        $proofs += $Policy.riskRequirements.$tag.proofs
    }
    $highRisk = $effectiveRisks.Count -gt 0
    return [pscustomobject]@{ skills = @($skills | Sort-Object -Unique); risk = $(if ($highRisk) { 'HIGH_RISK' } else { 'ORDINARY' }); independentReview = $highRisk; proofs = @($proofs | Sort-Object -Unique) }
}
function Assert-Case($Policy, $Case) {
    $actual = Resolve-Capabilities $Policy $Case.input
    Assert-SameSet $actual.skills $Case.expected.skills 'FIXTURE_MISMATCH'
    Assert-SameSet $actual.proofs $Case.expected.proofs 'FIXTURE_MISMATCH'
    Assert-Condition ($Case.expected.independentReview -is [bool] -and $actual.independentReview -eq $Case.expected.independentReview -and $actual.risk -ceq $Case.expected.risk) 'FIXTURE_MISMATCH'
}

$policy = Read-Json $PolicyPath
$fixtures = Read-Json $FixturePath
$inventory = @(Read-SkillInventory $AgentRoot)
Assert-Policy $policy $inventory
Assert-Condition ($fixtures.schemaVersion -eq 2 -and $fixtures.cases.Count -gt 0 -and $fixtures.negativeCases.Count -gt 0) 'FIXTURE_SCHEMA_INVALID'
Assert-Strings @($fixtures.cases | ForEach-Object { $_.id }) 'DUPLICATE_FIXTURE_ID'
Assert-Strings @($fixtures.negativeCases | ForEach-Object { $_.id }) 'DUPLICATE_FIXTURE_ID'
foreach ($case in $fixtures.cases) {
    Assert-Case $policy $case
    Write-Output "PASS fixture=$($case.id)"
}
# 每个声明的触发和高风险标签都必须有正向样本，不能通过删除困难样本制造通过。
foreach ($trigger in @($policy.canonicalSkills | ForEach-Object { $_.triggers })) {
    Assert-Condition (@($fixtures.cases | Where-Object { $_.input.capabilities -ccontains $trigger }).Count -gt 0) 'TRIGGER_COVERAGE_MISSING'
}
foreach ($tag in $policy.riskRequirements.PSObject.Properties.Name) {
    Assert-Condition (@($fixtures.cases | Where-Object { $_.input.riskTags -ccontains $tag }).Count -gt 0) 'RISK_COVERAGE_MISSING'
}
$requiredMutations = @('unknown_target','duplicate_identity','legacy_active','missing_file','extra_file','wrong_name','empty_description','unknown_capability','unknown_risk','ordinary_all','risk_downgrade','missing_postgres','credential_downgrade','trading_downgrade','unsafe_identity','duplicate_trigger','trigger_risk_downgrade')
Assert-SameSet @($fixtures.negativeCases | ForEach-Object { $_.mutation }) $requiredMutations 'NEGATIVE_COVERAGE_MISSING'
foreach ($negative in $fixtures.negativeCases) {
    $candidate = Copy-Data $policy
    $view = @(Copy-Data $inventory)
    $ordinary = Copy-Data (@($fixtures.cases | Where-Object { $_.input.capabilities.Count -eq 0 -and $_.input.riskTags.Count -eq 0 })[0])
    $errorCode = $null
    try {
        switch ($negative.mutation) {
            'unknown_target' { $candidate.canonicalSkills[0].id = 'undeclared-capability' }
            'duplicate_identity' { $candidate.canonicalSkills += @(Copy-Data $candidate.canonicalSkills[0]) }
            'legacy_active' { $view += [pscustomobject]@{ id=$candidate.retiredSkills[0]; text='legacy' } }
            'missing_file' { $view = @($view | Select-Object -Skip 1) }
            'extra_file' { $view += [pscustomobject]@{ id='unlisted-capability'; text='unlisted' } }
            'wrong_name' { $view[0].text = $view[0].text -replace '(?m)^name:.*$', 'name: wrong-name' }
            'empty_description' { $view[0].text = $view[0].text -replace '(?m)^description:.*$', 'description: ' }
            'unknown_capability' { $ordinary.input.capabilities = @('unknown-capability') }
            'unknown_risk' { $ordinary.input.riskTags = @('unknown-risk') }
            'ordinary_all' { $ordinary.expected.skills = @($candidate.canonicalSkills | ForEach-Object { $_.id }) }
            'risk_downgrade' { $candidate.riskRequirements.migration.risk = 'ORDINARY' }
            'missing_postgres' { $candidate.riskRequirements.migration.proofs = @('FORWARD_ONLY','HISTORICAL_MIGRATION_IMMUTABLE') }
            'credential_downgrade' { $candidate.riskRequirements.credential.independentReview = $false }
            'trading_downgrade' { $candidate.riskRequirements.trading.proofs = @('POSTGRESQL_PROOF') }
            'unsafe_identity' { $candidate.canonicalSkills[0].id = '../escape' }
            'duplicate_trigger' { $candidate.canonicalSkills += [pscustomobject]@{ id='additional-capability'; triggers=@($candidate.canonicalSkills[0].triggers[0]); riskTags=@() } }
            'trigger_risk_downgrade' {
                $riskSkill = @($candidate.canonicalSkills | Where-Object { $_.riskTags.Count -gt 0 })[0]
                $ordinary = Copy-Data (@($fixtures.cases | Where-Object { $_.input.riskTags.Count -eq 0 -and $_.input.capabilities -ccontains $riskSkill.triggers[0] })[0])
                $riskSkill.riskTags = @()
            }
            default { throw 'UNKNOWN_NEGATIVE_MUTATION' }
        }
        Assert-Policy $candidate $view
        Assert-Case $candidate $ordinary
    } catch { $errorCode = $_.Exception.Message }
    Assert-Condition ($errorCode -ceq $negative.expectedError) "NEGATIVE_RESULT_MISMATCH id=$($negative.id) expected=$($negative.expectedError) actual=$errorCode"
    Write-Output "PASS negative=$($negative.id) rejected=$errorCode"
}
# 新增真实能力只需 policy/目录/fixture 同步，不需要修改校验器内的数量常量。
$extended = Copy-Data $policy
$extended.canonicalSkills += [pscustomobject]@{ id='extension-probe'; triggers=@('extension_probe'); riskTags=@() }
$extendedView = @($inventory) + @([pscustomobject]@{ id='extension-probe'; text="---`nname: extension-probe`ndescription: Temporary extension proof.`n---`n" })
Assert-Policy $extended $extendedView
Assert-Case $extended ([pscustomobject]@{ input=[pscustomobject]@{ capabilities=@('extension_probe'); riskTags=@() }; expected=[pscustomobject]@{ skills=@('extension-probe'); risk='ORDINARY'; independentReview=$false; proofs=@() } })
Write-Output "PASS dynamic-inventory-extension=$($extended.canonicalSkills.Count)"
Write-Output "SUMMARY canonical=$($policy.canonicalSkills.Count) filesystem=$($inventory.Count) fixtures=$($fixtures.cases.Count) negative=$($fixtures.negativeCases.Count) legacy-active=0 duplicate-identities=0 unknown-targets=0"
