[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
. (Join-Path $PSScriptRoot 'governance-workflow-lib.ps1')
$contractPath = Join-Path $PSScriptRoot 'governance-workflow-contract.json'
$contract = Get-GovernanceWorkflowContract $contractPath

function Assert-True {
    param([bool] $Condition, [string] $Message)
    if (-not $Condition) { throw $Message }
}

function ConvertTo-CanonicalFixtureText {
    param([Parameter(Mandatory = $true)][string] $Content)
    $normalized = $Content.Replace("`r`n", "`n")
    if ($normalized.Contains("`r")) { throw 'FIXTURE_MUTATION_FAILED bare CR is not supported' }
    return $normalized
}

function Set-DeterministicFixtureLine {
    param(
        [Parameter(Mandatory = $true)][string] $Content,
        [Parameter(Mandatory = $true)][string] $OldLine,
        [Parameter(Mandatory = $true)][string] $NewLine
    )
    $normalized = ConvertTo-CanonicalFixtureText $Content
    $lines = @($normalized -split "`n")
    $indexes = @()
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -ceq $OldLine) { $indexes += $index }
    }
    if ($indexes.Count -eq 0) { throw "FIXTURE_MUTATION_TARGET_MISSING line=$OldLine" }
    if ($indexes.Count -gt 1) { throw "FIXTURE_MUTATION_TARGET_AMBIGUOUS line=$OldLine count=$($indexes.Count)" }
    $lines[$indexes[0]] = $NewLine
    $mutated = $lines -join "`n"
    if ($mutated -ceq $normalized) { throw "FIXTURE_MUTATION_FAILED unchanged line=$OldLine" }
    return $mutated
}

function Remove-DeterministicFixtureLine {
    param(
        [Parameter(Mandatory = $true)][string] $Content,
        [Parameter(Mandatory = $true)][string] $Line
    )
    $normalized = ConvertTo-CanonicalFixtureText $Content
    $lines = @($normalized -split "`n")
    $indexes = @()
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -ceq $Line) { $indexes += $index }
    }
    if ($indexes.Count -eq 0) { throw "FIXTURE_MUTATION_TARGET_MISSING line=$Line" }
    if ($indexes.Count -gt 1) { throw "FIXTURE_MUTATION_TARGET_AMBIGUOUS line=$Line count=$($indexes.Count)" }
    $remaining = for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($index -ne $indexes[0]) { $lines[$index] }
    }
    $mutated = @($remaining) -join "`n"
    if ($mutated -ceq $normalized) { throw "FIXTURE_MUTATION_FAILED unchanged line=$Line" }
    return $mutated
}

function Set-AuthorityFixtureField {
    param(
        [Parameter(Mandatory = $true)][string] $Content,
        [Parameter(Mandatory = $true)][string] $Field,
        [Parameter(Mandatory = $true)][string] $OldValue,
        [Parameter(Mandatory = $true)][string] $NewValue
    )
    $normalized = ConvertTo-CanonicalFixtureText $Content
    $oldLine = "$Field=$OldValue"
    $newLine = "$Field=$NewValue"
    $mutated = Set-DeterministicFixtureLine $normalized $oldLine $newLine
    $originalLines = @($normalized -split "`n")
    $mutatedLines = @($mutated -split "`n")
    Assert-True ($originalLines.Count -eq $mutatedLines.Count) "FIXTURE_MUTATION_FAILED line count changed field=$Field"
    $changedLineCount = 0
    for ($index = 0; $index -lt $originalLines.Count; $index++) {
        if ($originalLines[$index] -cne $mutatedLines[$index]) { $changedLineCount++ }
    }
    Assert-True ($changedLineCount -eq 1) "FIXTURE_MUTATION_FAILED changed-line-count=$changedLineCount field=$Field"
    Assert-True (@($mutatedLines | Where-Object { $_ -ceq $oldLine }).Count -eq 0) "FIXTURE_MUTATION_FAILED old value remains field=$Field"
    Assert-True (@($mutatedLines | Where-Object { $_ -ceq $newLine }).Count -eq 1) "FIXTURE_MUTATION_FAILED new value count invalid field=$Field"
    $parsed = Read-GovernanceAuthorityBlock $mutated
    Assert-True ((Get-GovernancePropertyValue $parsed $Field) -ceq $NewValue) "FIXTURE_MUTATION_FAILED parsed value mismatch field=$Field"
    return $mutated
}

function Invoke-AuthorityFixture {
    param([string] $Content, [string] $ContractContent)
    $id = [guid]::NewGuid().ToString('N')
    $statusPath = Join-Path ([System.IO.Path]::GetTempPath()) "nq-authority-$id.md"
    $fixtureContractPath = Join-Path ([System.IO.Path]::GetTempPath()) "nq-authority-contract-$id.json"
    [System.IO.File]::WriteAllText($statusPath, $Content, (New-Object System.Text.UTF8Encoding($false)))
    try {
        $arguments = @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', (Join-Path $PSScriptRoot 'check-current-authority.ps1'), '-StatusPath', $statusPath)
        if (-not [string]::IsNullOrEmpty($ContractContent)) {
            [System.IO.File]::WriteAllText($fixtureContractPath, $ContractContent, (New-Object System.Text.UTF8Encoding($false)))
            $arguments += @('-ContractPath', $fixtureContractPath)
        }
        $hostExe = (Get-Process -Id $PID).Path
        $output = & $hostExe @arguments 2>&1
        return [pscustomobject]@{ ExitCode = $LASTEXITCODE; Output = (($output | ForEach-Object { $_.ToString() }) -join "`n") }
    } finally {
        Remove-Item -LiteralPath $statusPath -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $fixtureContractPath -Force -ErrorAction SilentlyContinue
    }
}

function Assert-AuthorityRejected {
    param([object] $Result, [string] $Name, [string] $EvidencePattern)
    Assert-True ($Result.ExitCode -ne 0) "Authority fixture was accepted: $Name"
    Assert-True ($Result.Output -match 'BLOCKED / CURRENT_AUTHORITY_CONFLICT') "Authority fixture returned the wrong blocking token: $Name output=$($Result.Output)"
    if ($EvidencePattern) { Assert-True ($Result.Output -match $EvidencePattern) "Authority fixture lacked evidence: $Name pattern=$EvidencePattern output=$($Result.Output)" }
    Write-Output "PASS rejected=$Name"
}

$positiveActions = @(
    @{ Action = 'NQ-MODULE-IMPLEMENTATION'; Type = 'IMPLEMENTATION' },
    @{ Action = 'NQ-MODULE-INDEPENDENT-REVIEW'; Type = 'REVIEW' },
    @{ Action = 'NQ-MODULE-COMMIT'; Type = 'COMMIT' },
    @{ Action = 'NQ-WAIT-CI'; Type = 'CI' },
    @{ Action = 'NQ-MODULE-REMEDIATION'; Type = 'FIX' },
    @{ Action = 'NQ-FULL-REPOSITORY-AUDIT-AND-CONSOLIDATION'; Type = 'AUDIT' },
    @{ Action = 'NONE'; Type = 'NONE' }
)
foreach ($case in $positiveActions) {
    Assert-True ((Get-GovernanceNextActionType $contract $case.Action) -ceq $case.Type) "Action type mismatch: $($case.Action)"
    Write-Output "PASS action=$($case.Action) type=$($case.Type)"
}

$ambiguousActions = @(
    'NQ-GOVERNANCE-REVIEW-AND-COMMIT',
    'NQ-GOVERNANCE-FIX-AND-COMMIT',
    'NQ-GATE-FREEZE-AND-RELEASE',
    'NQ-CI-FIX-AND-REVIEW'
)
foreach ($action in $ambiguousActions) {
    Assert-True ((Get-GovernanceNextActionType $contract $action) -ceq 'AMBIGUOUS') "Mixed next action was not classified as ambiguous: $action"
    Assert-True (-not (Test-GovernanceNextActionForWorkBatch $contract 'IMPLEMENTED|PENDING_REVIEW' 'Generic-Batch' $action $null)) "Mixed next action was accepted: $action"
    Write-Output "PASS ambiguous-action=$action result=REJECT"
}
Assert-True (Test-GovernanceNextActionForWorkBatch $contract 'IMPLEMENTED|PENDING_REVIEW' 'Generic-Batch' 'NQ-MODULE-INDEPENDENT-REVIEW' $null) 'Unique review action was rejected.'
Assert-True (-not (Test-GovernanceNextActionForWorkBatch $contract 'IMPLEMENTED|PENDING_REVIEW' 'Generic-Batch' 'NQ-MODULE-COMMIT' $null)) 'Review gate bypassed.'

$base = @'
# Current Status Fixture

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateQ
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gateq-freeze
last_frozen_gate_commit=1111111111111111111111111111111111111111
active_gate=GateAUDIT
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateQ-1
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=2222222222222222222222222222222222222222
accepted_batch_acceptance_head=2222222222222222222222222222222222222222
accepted_batch_ci_run=12345
work_batch=Governance-Baseline
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GOVERNANCE-INDEPENDENT-REVIEW
production_soak=COMPLETED
kill_switch=ENGAGED
live=DISABLED
shadow_trading=NOT_ENABLED
ai=NOT_STARTED
dh_runtime=NOT_INTEGRATED
integration_runtime=NOT_STARTED
real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
nq-current-authority:end -->
'@

$missingTargetRejected = $false
try { $null = Set-AuthorityFixtureField $base 'production_soak' 'MISSING' 'FAILED' }
catch {
    Assert-True ($_.Exception.Message -match '^FIXTURE_MUTATION_TARGET_MISSING') "Missing-target guard returned wrong error: $($_.Exception.Message)"
    $missingTargetRejected = $true
}
Assert-True $missingTargetRejected 'Missing-target guard did not reject the fixture.'
Write-Output 'PASS mutation-guard=target-missing'

$duplicatedTarget = Set-DeterministicFixtureLine $base 'production_soak=COMPLETED' "production_soak=COMPLETED`nproduction_soak=COMPLETED"
$ambiguousTargetRejected = $false
try { $null = Set-AuthorityFixtureField $duplicatedTarget 'production_soak' 'COMPLETED' 'FAILED' }
catch {
    Assert-True ($_.Exception.Message -match '^FIXTURE_MUTATION_TARGET_AMBIGUOUS') "Ambiguous-target guard returned wrong error: $($_.Exception.Message)"
    $ambiguousTargetRejected = $true
}
Assert-True $ambiguousTargetRejected 'Ambiguous-target guard did not reject the fixture.'
Write-Output 'PASS mutation-guard=target-ambiguous'

$bareCrFixture = (ConvertTo-CanonicalFixtureText $base).Replace("production_soak=COMPLETED`n", "production_soak=COMPLETED`r")
$bareCrRejected = $false
try { $null = Set-AuthorityFixtureField $bareCrFixture 'production_soak' 'COMPLETED' 'FAILED' }
catch {
    Assert-True ($_.Exception.Message -match '^FIXTURE_MUTATION_FAILED bare CR') "Bare-CR guard returned wrong error: $($_.Exception.Message)"
    $bareCrRejected = $true
}
Assert-True $bareCrRejected 'Bare-CR guard did not reject the fixture.'
Write-Output 'PASS mutation-guard=bare-cr'

$valid = Invoke-AuthorityFixture $base $null
Assert-True ($valid.ExitCode -eq 0 -and $valid.Output -match 'PASS / CURRENT_AUTHORITY_VALID') "Valid authority rejected: $($valid.Output)"
Write-Output 'PASS fixture=current-authority-valid'

$safetyMutations = @(
    @{ Field = 'production_soak'; From = 'COMPLETED'; To = 'FAILED' },
    @{ Field = 'live'; From = 'DISABLED'; To = 'ENABLED' },
    @{ Field = 'kill_switch'; From = 'ENGAGED'; To = 'DISENGAGED' },
    @{ Field = 'shadow_trading'; From = 'NOT_ENABLED'; To = 'ENABLED' },
    @{ Field = 'ai'; From = 'NOT_STARTED'; To = 'STARTED' },
    @{ Field = 'dh_runtime'; From = 'NOT_INTEGRATED'; To = 'INTEGRATED' },
    @{ Field = 'integration_runtime'; From = 'NOT_STARTED'; To = 'STARTED' },
    @{ Field = 'real_provider'; From = 'NOT_IMPLEMENTED'; To = 'IMPLEMENTED' },
    @{ Field = 'private_trading'; From = 'NOT_IMPLEMENTED'; To = 'IMPLEMENTED' }
)
foreach ($mutation in $safetyMutations) {
    $mutated = Set-AuthorityFixtureField $base $mutation.Field $mutation.From $mutation.To
    Write-Output "PASS mutation-self-check=$($mutation.Field)"
    $result = Invoke-AuthorityFixture $mutated $null
    Assert-AuthorityRejected $result ("safety-{0}" -f $mutation.Field) 'SAFETY_PROFILE_VIOLATION'
}
Write-Output 'FIXTURE_MUTATION_SELF_CHECKS=PASS'

$missingField = Invoke-AuthorityFixture (Remove-DeterministicFixtureLine $base 'ai=NOT_STARTED') $null
Assert-AuthorityRejected $missingField 'required-field-missing' 'FIELD_MISSING'
$invalidValue = Invoke-AuthorityFixture (Set-DeterministicFixtureLine $base 'active_gate_status=IN_PROGRESS|NOT_FROZEN' 'active_gate_status=INVALID') $null
Assert-AuthorityRejected $invalidValue 'invalid-value' 'ACTIVE_GATE_STATUS_INVALID'
$malformedContract = Invoke-AuthorityFixture $base '{not-json'
Assert-AuthorityRejected $malformedContract 'contract-malformed' 'AUTHORITY_CONTRACT_INVALID'

$profileContract = [System.IO.File]::ReadAllText($contractPath, (New-Object System.Text.UTF8Encoding($false))) | ConvertFrom-Json
$profileContract.authority.safetyProfiles.byActiveGate.GateAUDIT.psobject.Properties.Remove('production_soak')
$malformedProfileJson = ConvertTo-Json $profileContract -Depth 100
$malformedProfile = Invoke-AuthorityFixture $base $malformedProfileJson
Assert-AuthorityRejected $malformedProfile 'profile-malformed' 'AUTHORITY_CONTRACT_INVALID'

$whitespaceCases = @(
    @{ Name = 'key-leading-whitespace'; Content = (Set-DeterministicFixtureLine $base 'production_soak=COMPLETED' ' production_soak=COMPLETED') },
    @{ Name = 'key-trailing-whitespace'; Content = (Set-DeterministicFixtureLine $base 'production_soak=COMPLETED' 'production_soak =COMPLETED') },
    @{ Name = 'value-leading-whitespace'; Content = (Set-DeterministicFixtureLine $base 'production_soak=COMPLETED' 'production_soak= COMPLETED') },
    @{ Name = 'value-trailing-whitespace'; Content = (Set-DeterministicFixtureLine $base 'production_soak=COMPLETED' 'production_soak=COMPLETED ') },
    @{ Name = 'extra-machine-line-whitespace'; Content = (Set-DeterministicFixtureLine $base 'production_soak=COMPLETED' " `nproduction_soak=COMPLETED") }
)
foreach ($case in $whitespaceCases) {
    $result = Invoke-AuthorityFixture $case.Content $null
    Assert-AuthorityRejected $result $case.Name 'AUTHORITY_SCHEMA_INVALID'
}

$checkerText = [System.IO.File]::ReadAllText((Join-Path $PSScriptRoot 'check-current-authority.ps1'))
foreach ($forbidden in @('PlanPath', 'Attempt-13', 'GATEW-specific', 'GateV default')) {
    Assert-True (-not $checkerText.Contains($forbidden)) "Gate-specific checker residue found: $forbidden"
}

Write-Output 'SUMMARY current-authority-next-action positive-actions=7 ambiguous-actions=4 safety-negative=9 schema-negative=4 whitespace-negative=5 failed=0'
exit 0
