[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$artifactRoot = Join-Path $repo 'artifacts'
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
# Keep isolated Java source/report paths below Windows PowerShell 5.1's MAX_PATH boundary.
$runRoot = Join-Path $artifactRoot ('ci-' + [Guid]::NewGuid().ToString('N').Substring(0, 8))
$workflowValidator = Join-Path $repo 'scripts\ci\Test-CanonicalDeliveryWorkflow.ps1'
$archiveValidator = Join-Path $repo 'scripts\ci\Test-DeliveryToolArchive.ps1'
$realWorkflow = Join-Path $repo '.github\workflows\ci.yml'
$realLock = Join-Path $repo 'scripts\ci\delivery-supply-chain-lock.json'
$realRestoreDrill = Join-Path $repo 'scripts\deployment\Invoke-NqCanonicalRestoreDrill.ps1'
$realDeploymentContract = Join-Path $repo 'deploy\canonical\deployment-contract.json'
$realSystemdUnit = Join-Path $repo 'deploy\canonical\nq-canonical.service'
$realProductionYaml = Join-Path $repo 'backend/nq-app/src/main/resources/application-prod.yml'
$realSpringFactories = Join-Path $repo 'backend/nq-app/src/main/resources/META-INF/spring.factories'
$realSecretProfileTest = Join-Path $repo 'backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/env/ProductionSecretProfileRegressionTest.java'
$realProductionInitializer = Join-Path $repo 'backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/env/ProductionConfigurationApplicationContextInitializer.java'
$script:negativeCount = 0

function Assert-Condition([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

function Write-Json([string] $Path, [object] $Value) {
    $json = ($Value | ConvertTo-Json -Depth 40).Replace("`r`n", "`n").TrimEnd() + "`n"
    [IO.File]::WriteAllText($Path, $json, (New-Object Text.UTF8Encoding($false)))
}

function New-Case([string] $Name) {
    $root = Join-Path $runRoot $Name
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    $workflow = Join-Path $root 'ci.yml'
    $lock = Join-Path $root 'delivery-supply-chain-lock.json'
    Copy-Item -LiteralPath $realWorkflow -Destination $workflow
    Copy-Item -LiteralPath $realLock -Destination $lock
    return [pscustomobject]@{ Root = $root; Workflow = $workflow; Lock = $lock }
}

function Assert-WorkflowRejected([string] $Name, [scriptblock] $Mutate, [string] $ExpectedMessage = '') {
    $case = New-Case $Name
    & $Mutate $case
    $rejected = $false
    try { & $workflowValidator -ContractOnly -WorkflowPath $case.Workflow -SupplyChainLockPath $case.Lock | Out-Null }
    catch { $rejected = [string]::IsNullOrEmpty($ExpectedMessage) -or $_.Exception.Message -like $ExpectedMessage }
    Assert-Condition $rejected "Negative supply-chain case unexpectedly passed: $Name"
    $script:negativeCount += 1
    Write-Output "NEGATIVE_REJECTED=$Name"
}

function Assert-ProductionConfigurationRejected([string] $Name, [scriptblock] $Mutate) {
    $root = Join-Path $runRoot $Name
    New-Item -ItemType Directory -Path $root -Force | Out-Null
    $systemdUnit = Join-Path $root 'nq-canonical.service'
    $deploymentContract = Join-Path $root 'deployment-contract.json'
    Copy-Item -LiteralPath $realSystemdUnit -Destination $systemdUnit
    Copy-Item -LiteralPath $realDeploymentContract -Destination $deploymentContract
    $productionYaml = Join-Path $root 'application-prod.yml'
    $springFactories = Join-Path $root 'spring.factories'
    Copy-Item -LiteralPath $realProductionYaml -Destination $productionYaml
    Copy-Item -LiteralPath $realSpringFactories -Destination $springFactories
    $secretProfileTest = Join-Path $root 'ProductionSecretProfileRegressionTest.java'
    Copy-Item -LiteralPath $realSecretProfileTest -Destination $secretProfileTest
    $case = [pscustomobject]@{ SystemdUnit = $systemdUnit; DeploymentContract = $deploymentContract; ProductionYaml = $productionYaml; SpringFactories = $springFactories; SecretProfileTest = $secretProfileTest }
    & $Mutate $case
    $rejected = $false
    try {
        & $workflowValidator -ContractOnly -WorkflowPath $realWorkflow -SupplyChainLockPath $realLock `
            -SystemdUnitPath $systemdUnit -DeploymentContractPath $deploymentContract `
            -ProductionYamlPath $productionYaml -SpringFactoriesPath $springFactories `
            -ProductionSecretProfileTestPath $secretProfileTest | Out-Null
    } catch { $rejected = $true }
    Assert-Condition $rejected "Negative production configuration case unexpectedly passed: $Name"
    $script:negativeCount += 1
    Write-Output "NEGATIVE_REJECTED=$Name"
}

function Copy-BackendSourceForMutation([string] $Destination) {
    $source = Join-Path $repo 'backend'
    foreach ($file in Get-ChildItem -LiteralPath $source -File -Recurse -Force) {
        $relativePath = $file.FullName.Substring($source.Length).TrimStart('\', '/')
        if ($relativePath -match '(^|[\\/])target([\\/]|$)') {
            continue
        }
        $target = Join-Path $Destination $relativePath
        New-Item -ItemType Directory -Path (Split-Path -Parent $target) -Force | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $target
    }
}

function Assert-ProductionConfigCapabilityRejected([string] $Name, [scriptblock] $Mutate) {
    $root = Join-Path $runRoot ($Name.Split('-')[0])
    $backend = Join-Path $root 'backend'
    Copy-BackendSourceForMutation $backend
    $initializer = Join-Path $backend 'nq-app/src/main/java/com/guidinglight/nexusquant/app/config/env/ProductionConfigurationApplicationContextInitializer.java'
    Assert-Condition (Test-Path -LiteralPath $initializer -PathType Leaf) 'Production initializer mutation target is missing'
    & $Mutate $initializer

    $log = Join-Path $root 'canonical-admission.log'
    $admissionRejected = $false
    try {
        # The same default entry used by canonical CI must propagate the real Maven failure.
        # ContractOnly is intentionally absent: a static marker rejection cannot satisfy this proof.
        & $workflowValidator -BackendRoot $backend *> $log
    } catch {
        $admissionRejected = $_.Exception.Message -like 'CANONICAL_ADMISSION_REJECTED / PRODUCTION_CONFIG_REGRESSION_FAILED*'
    }
    Assert-Condition $admissionRejected "Canonical admission did not reject the actual Maven failure: $Name"
    $failures = 0
    $tests = 0
    foreach ($testName in @('ProductionConfigurationApplicationContextInitializerTest', 'ProductionSecretProfileRegressionTest')) {
        $reportPath = Join-Path $backend "nq-app/target/surefire-reports/TEST-com.guidinglight.nexusquant.app.config.env.$testName.xml"
        Assert-Condition (Test-Path -LiteralPath $reportPath) "Mutation did not execute the selected Java regression: $Name"
        [xml] $report = Get-Content -LiteralPath $reportPath -Raw
        Assert-Condition ([int]$report.testsuite.errors -eq 0 -and [int]$report.testsuite.skipped -eq 0) "Mutation failed for an unrelated error or skipped tests: $Name"
        $tests += [int]$report.testsuite.tests
        $failures += [int]$report.testsuite.failures
    }
    Assert-Condition ($tests -gt 0 -and $failures -gt 0) "Mutation lacked Java assertion failures: $Name"
    $script:negativeCount += 1
    Write-Output "MANDATORY_PRODUCTION_CONFIG_CAPABILITY_REJECTED=$Name tests=$tests assertion-failures=$failures chain=SOURCE_MAVEN_REQUIRED_CAPABILITY_CANONICAL_ADMISSION"
}

function Remove-CriticalE2eStepBlocks([string] $Content) {
    $names = @(
        'Run loopback critical E2E allowlist',
        'Run real-backend critical E2E allowlist'
    )
    $matches = @()
    foreach ($name in $names) {
        $pattern = '(?ms)^      - name:\s*' + [regex]::Escape($name) + '\s*\r?\n.*?(?=^      - name:|^  [A-Za-z0-9_-]+:\s*$|\z)'
        $match = [regex]::Match($Content, $pattern)
        Assert-Condition $match.Success "Unable to locate critical E2E step fixture: $name"
        $matches += $match
    }
    $remaining = $Content
    foreach ($match in @($matches | Sort-Object Index -Descending)) {
        $remaining = $remaining.Remove($match.Index, $match.Length)
    }
    $specs = @(
        [regex]::Matches(($matches.Value -join "`n"), '[A-Za-z0-9-]+\.spec\.ts') |
            ForEach-Object Value |
            Sort-Object -Unique
    )
    return [pscustomobject]@{
        Content = $remaining
        Blocks = @($matches | Sort-Object Index | ForEach-Object Value)
        Specs = $specs
    }
}

function Move-CriticalE2eStepsToOptionalJob([object] $Case, [bool] $Conditional) {
    $removed = Remove-CriticalE2eStepBlocks (Get-Content -Raw -LiteralPath $Case.Workflow)
    $conditionLine = if ($Conditional) { '    if: ${{ false }}' + "`n" } else { '' }
    $job = "  optional-critical-e2e:`n    name: Optional critical E2E fixture`n    runs-on: ubuntu-latest`n${conditionLine}    steps:`n" +
        ($removed.Blocks -join "`n") + "`n"
    $content = $removed.Content.Replace('  delivery-provenance:', $job + '  delivery-provenance:')
    [IO.File]::WriteAllText($Case.Workflow, $content)
}

try {
    New-Item -ItemType Directory -Path $runRoot -Force | Out-Null
    $baselineOutput = @(& $workflowValidator -WorkflowPath $realWorkflow -SupplyChainLockPath $realLock)
    Assert-Condition ($baselineOutput -ccontains 'PRODUCTION_CONFIG_REGRESSION=EXECUTED_PASS') 'Baseline Java capability was not executed'
    Assert-Condition ($baselineOutput -ccontains 'CANONICAL_ADMISSION=ACCEPTED') 'Baseline canonical admission did not pass'
    Write-Output 'BASELINE_CANONICAL_ADMISSION=ACCEPTED_JAVA_EXECUTED'
    & (Join-Path $PSScriptRoot 'Test-NqWorkflowYaml.Tests.ps1')
    $contractOutput = @(& {
        function mvn { throw 'ContractOnly must never execute Maven' }
        & $workflowValidator -ContractOnly
    })
    Assert-Condition ($contractOutput -ccontains 'CANONICAL_ADMISSION=NOT_EVALUATED_CONTRACT_ONLY') 'ContractOnly must explicitly withhold admission'
    Assert-Condition (-not ($contractOutput -ccontains 'CANONICAL_ADMISSION=ACCEPTED')) 'ContractOnly granted admission'

    # Each semantic case is a valid YAML key representation; no escape-specific production rule.
    $f008Name = 'Run production configuration fail-closed regression'
    $semanticCases = @(
        @{ Id='S01-plain-soft-fail'; Anchor="      - name: $f008Name"; Field='        continue-on-error: false'; Error='*must omit continue-on-error*' },
        @{ Id='S02-quoted-soft-fail'; Anchor="      - name: $f008Name"; Field='        "continue-on-error": ${{ true }}'; Error='*must omit continue-on-error*' },
        @{ Id='S03-unicode-hyphen'; Anchor="      - name: $f008Name"; Field='        "continue\u002don-error": ${{ true }}'; Error='*must omit continue-on-error*' },
        @{ Id='S04-plain-if'; Anchor="      - name: $f008Name"; Field='        if: success()'; Error='*is conditional*' },
        @{ Id='S05-quoted-if'; Anchor="      - name: $f008Name"; Field='        "if": ${{ true }}'; Error='*is conditional*' },
        @{ Id='S06-unicode-if'; Anchor="      - name: $f008Name"; Field='        "\u0069f": ${{ false }}'; Error='*is conditional*' },
        @{ Id='S07-backend-job-escaped-key'; Anchor='  backend:'; Field='    "continue\u002don-error": false'; Error='*must omit continue-on-error*' },
        @{ Id='S08-F008-step-escaped-key'; Anchor="      - name: $f008Name"; Field='        "\u0069f": always()'; Error='*is conditional*' }
    )
    foreach ($semanticCase in $semanticCases) {
        Assert-WorkflowRejected $semanticCase.Id {
            param($case)
            $text = Get-Content -Raw -LiteralPath $case.Workflow
            [IO.File]::WriteAllText($case.Workflow, $text.Replace($semanticCase.Anchor, $semanticCase.Anchor + "`n" + $semanticCase.Field))
        }.GetNewClosure() $semanticCase.Error
    }
    foreach ($mixed in @($false, $true)) {
        $semanticId = if ($mixed) { 'S10-mixed-plain-escaped' } else { 'S09-dual-escaped-soft-fail' }
        Assert-WorkflowRejected $semanticId {
            param($case)
            $text = Get-Content -Raw -LiteralPath $case.Workflow
            foreach ($name in @('Run backend tests', 'Run production configuration fail-closed regression')) {
                $field = if ($mixed -and $name -eq 'Run backend tests') { 'continue-on-error: false' } else { '"continue\u002don-error": ${{ true }}' }
                $text = $text.Replace("      - name: $name", "      - name: $name`n        $field")
            }
            [IO.File]::WriteAllText($case.Workflow, $text)
        }.GetNewClosure() '*must omit continue-on-error*'
    }
    $invalidStructures = [ordered]@{
        'malformed-yaml' = { param($text) $text + "`ninvalid: [`n" }
        'duplicate-decoded-key' = { param($text) $text.Replace("      - name: $f008Name", "      - name: $f008Name`n        if: false`n" + '        "\u0069f": false') }
        'jobs-missing' = { param($text) $text.Replace("jobs:`n", "renamed-jobs:`n") }
        'jobs-sequence' = { param($text) $text.Replace("jobs:`n", "jobs: []`nrenamed-jobs:`n") }
        'backend-scalar' = { param($text) [regex]::Replace($text, '(?ms)^  backend:\r?\n.*?(?=^  [A-Za-z0-9_-]+:\s*$|\z)', "  backend: invalid`n") }
        'steps-mapping' = { param($text) [regex]::Replace($text, '(?ms)^  backend:\r?\n.*?(?=^  [A-Za-z0-9_-]+:\s*$|\z)', "  backend:`n    name: Backend regression`n    steps: {}`n") }
        'step-scalar' = { param($text) [regex]::Replace($text, '(?ms)^  backend:\r?\n.*?(?=^  [A-Za-z0-9_-]+:\s*$|\z)', "  backend:`n    name: Backend regression`n    steps: [false]`n") }
        'run-sequence' = { param($text) $text.Replace('run: mvn -f backend/pom.xml -pl nq-app -am test -Dtest=ProductionConfigurationApplicationContextInitializerTest,ProductionSecretProfileRegressionTest -Dsurefire.failIfNoSpecifiedTests=false', 'run: []') }
    }
    foreach ($structure in $invalidStructures.GetEnumerator()) {
        Assert-WorkflowRejected ('semantic-structure-' + $structure.Key) {
            param($case)
            $text = (Get-Content -Raw -LiteralPath $case.Workflow).Replace("`r`n", "`n")
            [IO.File]::WriteAllText($case.Workflow, (& $structure.Value $text))
        }.GetNewClosure() 'YAML_SEMANTIC_*'
    }
    # Reproduce the former default-mode ACCEPTED exploit, not just its ContractOnly counterpart.
    $defaultSemanticCase = New-Case 'semantic-default-admission-escaped-key'
    $text = Get-Content -Raw -LiteralPath $defaultSemanticCase.Workflow
    [IO.File]::WriteAllText($defaultSemanticCase.Workflow,
        $text.Replace("      - name: $f008Name", "      - name: $f008Name`n" + '        "continue\u002don-error": ${{ true }}'))
    $defaultRejected = $false
    try { & $workflowValidator -WorkflowPath $defaultSemanticCase.Workflow | Out-Null }
    catch { $defaultRejected = $_.Exception.Message -like '*must omit continue-on-error*' }
    Assert-Condition $defaultRejected 'Default admission accepted the escaped-key workflow'
    $script:negativeCount++
    Write-Output 'NEGATIVE_REJECTED=semantic-default-admission-escaped-key'
    $requiredJobLine = @($baselineOutput | Where-Object { $_ -like 'REQUIRED_JOB_IDS=*' })
    Assert-Condition ($requiredJobLine.Count -eq 1) 'Canonical validator did not expose one required-job identity set'
    $requiredJobIds = @(($requiredJobLine[0] -replace '^REQUIRED_JOB_IDS=', '').Split(',') | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    Assert-Condition ($requiredJobIds.Count -eq 9) "Expected nine canonical required jobs; found=$($requiredJobIds.Count)"

    foreach ($variable in @('NQ_PROD_DB_URL','NQ_PROD_DB_USER','NQ_PROD_DB_PASSWORD','NQ_SECURITY_SECRET','NQ_ACCOUNT_CREDENTIALS_MASTER_KEY')) {
        Assert-ProductionConfigurationRejected "prod-fallback-$variable" {
            param($case)
            $text = Get-Content -Raw -LiteralPath $case.ProductionYaml
            $placeholder = '${' + $variable + '}'
            Assert-Condition ($text.Contains($placeholder)) 'Fallback mutation target missing'
            $fallback = switch ($variable) {
                'NQ_PROD_DB_URL' { 'jdbc:postgresql://db-prod:5432/nexus_quant' }
                'NQ_PROD_DB_USER' { 'postgres' }
                'NQ_PROD_DB_PASSWORD' { 'change_me' }
                'NQ_SECURITY_SECRET' { 'change-me-change-me-change-me-change-me' }
                'NQ_ACCOUNT_CREDENTIALS_MASTER_KEY' { 'change-me-account-credentials-master-key-123456' }
            }
            [IO.File]::WriteAllText($case.ProductionYaml, $text.Replace($placeholder, '${' + $variable + ':' + $fallback + '}'))
        }.GetNewClosure()
    }
    Assert-ProductionConfigurationRejected 'prod-local-service-profile' {
        param($case)
        $text = (Get-Content -Raw -LiteralPath $case.SystemdUnit).Replace('--spring.profiles.active=prod', '--spring.profiles.active=prod,local')
        [IO.File]::WriteAllText($case.SystemdUnit, $text)
    }
    Assert-ProductionConfigurationRejected 'prod-local-approved-profile-set' {
        param($case)
        $contract = Get-Content -Raw -LiteralPath $case.DeploymentContract | ConvertFrom-Json
        $contract.runtimeConfiguration.approvedActiveProfiles = @('prod', 'local')
        Write-Json $case.DeploymentContract $contract
    }
    Assert-ProductionConfigurationRejected 'prod-registration-missing' {
        param($case)
        [IO.File]::Delete($case.SpringFactories)
    }
    Assert-ProductionConfigurationRejected 'prod-registration-corrupt' {
        param($case)
        [IO.File]::WriteAllText($case.SpringFactories, 'org.springframework.context.ApplicationContextInitializer=invalid.MissingInitializer')
    }
    Assert-ProductionConfigurationRejected 'prod-yaml-duplicate-key' {
        param($case)
        [IO.File]::AppendAllText($case.ProductionYaml, "`nnq:`n  security:`n    secret: unsafe`n")
    }
    Assert-ProductionConfigurationRejected 'prod-yaml-extra-document' {
        param($case)
        [IO.File]::AppendAllText($case.ProductionYaml, "`n---`nspring:`n  datasource:`n    username: postgres`n")
    }
    Assert-ProductionConfigurationRejected 'prod-secret-profile-regression-removed' {
        param($case)
        [IO.File]::Delete($case.SecretProfileTest)
    }

    Assert-ProductionConfigurationRejected 'production-profile-removed' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.SystemdUnit).Replace(
            ' --spring.profiles.active=prod',
            ''
        )
        [IO.File]::WriteAllText($case.SystemdUnit, $content)
    }
    Assert-ProductionConfigurationRejected 'production-marker-removed' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.SystemdUnit).Replace(
            ' --nq.production-configuration=true',
            ''
        )
        [IO.File]::WriteAllText($case.SystemdUnit, $content)
    }
    Assert-ProductionConfigurationRejected 'production-required-key-removed' {
        param($case)
        $contract = Get-Content -Raw -LiteralPath $case.DeploymentContract | ConvertFrom-Json
        $contract.runtimeConfiguration.requiredDataSourceKeys = @(
            $contract.runtimeConfiguration.requiredDataSourceKeys | Where-Object { $_ -cne 'NQ_PROD_DB_PASSWORD' }
        )
        Write-Json $case.DeploymentContract $contract
    }
    Assert-ProductionConfigurationRejected 'production-secret-bundling-enabled' {
        param($case)
        $contract = Get-Content -Raw -LiteralPath $case.DeploymentContract | ConvertFrom-Json
        $contract.runtimeConfiguration.secretsBundled = $true
        Write-Json $case.DeploymentContract $contract
    }

    Assert-ProductionConfigCapabilityRejected 'R06-prod-local-profile-mixing' {
        param($initializer)
        $source = Get-Content -Raw -LiteralPath $initializer
        $from = '!APPROVED_PRODUCTION_PROFILES.equals(effectiveProfiles)'
        Assert-Condition ($source.Contains($from)) 'R06 mutation target missing'
        [IO.File]::WriteAllText($initializer, $source.Replace($from, '!effectiveProfiles.contains(PROD_PROFILE)'))
    }
    Assert-ProductionConfigCapabilityRejected 'R09-marker-false-default-prod-bypass' {
        param($initializer)
        $source = Get-Content -Raw -LiteralPath $initializer
        $from = 'boolean productionConfiguration = productionMarker || productionProfile;'
        Assert-Condition ($source.Contains($from)) 'R09 mutation target missing'
        [IO.File]::WriteAllText($initializer, $source.Replace($from, 'boolean productionConfiguration = productionMarker;'))
    }
    Assert-ProductionConfigCapabilityRejected 'R10-include-group-effective-profile-bypass' {
        param($initializer)
        $source = Get-Content -Raw -LiteralPath $initializer
        $from = 'return Set.copyOf(Arrays.asList(selectedProfiles));'
        Assert-Condition ($source.Contains($from)) 'R10 mutation target missing'
        [IO.File]::WriteAllText($initializer, $source.Replace(
                $from,
                'return Set.of(environment.getProperty("spring.profiles.active", PROD_PROFILE));'
        ))
    }

    Assert-WorkflowRejected 'action-sha-mismatch' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace([string]$lock.actions[0].commit, ('0' * 40))
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'action-missing-from-lock' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $lock.actions = @($lock.actions | Select-Object -Skip 1)
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'extra-active-action' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $lock.actions += [pscustomobject][ordered]@{
            repository = 'actions/cache'
            tag = 'v4.0.2'
            commit = ('1' * 40)
            usage = 'ACTIVE_CI'
            expectedOccurrences = 1
            source = 'https://api.github.com/repos/actions/cache/git/refs/tags/v4.0.2'
        }
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'gitleaks-version-mismatch' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        ($lock.tools | Where-Object name -eq 'gitleaks').version = '8.18.5'
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'postgres-tag-mismatch' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $image = $lock.images | Where-Object usage -eq 'ACTIVE_CI_PINNED'
        $old = "$($image.name):$($image.tag)@$($image.digest)"
        $replacement = "$($image.name):mismatch@$($image.digest)"
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = [regex]::Replace($content, [regex]::Escape($old), $replacement, 1)
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'postgres-digest-mismatch' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $image = $lock.images | Where-Object usage -eq 'ACTIVE_CI_PINNED'
        $old = "$($image.name):$($image.tag)@$($image.digest)"
        $replacement = "$($image.name):$($image.tag)@sha256:$('0' * 64)"
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = [regex]::Replace($content, [regex]::Escape($old), $replacement, 1)
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'missing-required-lock-entry' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $lock.tools = @($lock.tools | Where-Object name -ne 'gitleaks')
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'duplicate-identity' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $lock.actions += $lock.actions[0]
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'unsupported-schema' {
        param($case)
        $lock = Get-Content -Raw -LiteralPath $case.Lock | ConvertFrom-Json
        $lock.schemaVersion = 'nq-delivery-supply-chain-lock-v999'
        Write-Json $case.Lock $lock
    }
    Assert-WorkflowRejected 'lock-validator-if-false' {
        param($case)
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace(
            '      - name: Validate canonical delivery workflow contract',
            "      - name: Validate canonical delivery workflow contract`n        if: " + '${{ false }}'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'lock-validator-continue-on-error' {
        param($case)
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace(
            '      - name: Validate canonical delivery workflow contract',
            "      - name: Validate canonical delivery workflow contract`n        continue-on-error: true"
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    foreach ($requiredJobId in $requiredJobIds) {
        $jobId = $requiredJobId
        $ifMutation = {
            param($case)
            $content = Get-Content -Raw -LiteralPath $case.Workflow
            $content = $content.Replace(
                "  ${jobId}:",
                "  ${jobId}:`n    if: " + '${{ false }}'
            )
            [IO.File]::WriteAllText($case.Workflow, $content)
        }.GetNewClosure()
        Assert-WorkflowRejected "required-job-if-false-$jobId" $ifMutation

        $softFailMutation = {
            param($case)
            $content = Get-Content -Raw -LiteralPath $case.Workflow
            $content = $content.Replace(
                "  ${jobId}:",
                "  ${jobId}:`n    continue-on-error: true"
            )
            [IO.File]::WriteAllText($case.Workflow, $content)
        }.GetNewClosure()
        Assert-WorkflowRejected "required-job-soft-fail-$jobId" $softFailMutation
    }
    Assert-WorkflowRejected 'critical-e2e-conditional-relocation' {
        param($case)
        Move-CriticalE2eStepsToOptionalJob $case $true
    }
    Assert-WorkflowRejected 'critical-e2e-non-required-relocation' {
        param($case)
        Move-CriticalE2eStepsToOptionalJob $case $false
    }
    Assert-WorkflowRejected 'critical-e2e-execution-removed-text-retained' {
        param($case)
        $removed = Remove-CriticalE2eStepBlocks (Get-Content -Raw -LiteralPath $case.Workflow)
        $comments = ($removed.Specs | ForEach-Object { "# retained-non-executable-spec=$_" }) -join "`n"
        [IO.File]::WriteAllText($case.Workflow, $comments + "`n" + $removed.Content)
    }
    Assert-WorkflowRejected 'npm-lock-bypass' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.Workflow).Replace('run: npm ci', 'run: npm install')
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'playwright-latest' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.Workflow).Replace(
            'npx --no-install playwright install --with-deps chromium',
            'npx playwright@latest install --with-deps chromium'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'maven-plugin-unpinned' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.Workflow).Replace(
            'org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom',
            'org.cyclonedx:cyclonedx-maven-plugin:LATEST:makeAggregateBom'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'gitleaks-scan-removed' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.Workflow).Replace(
            './scripts/ci/Invoke-VerifiedGitleaks.ps1',
            './scripts/ci/Missing-GitleaksConsumer.ps1'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'provenance-continue-on-error' {
        param($case)
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace(
            '      - name: Validate internal provenance admission',
            "      - name: Validate internal provenance admission`n        continue-on-error: true"
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'provenance-if-false' {
        param($case)
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace(
            '      - name: Validate internal provenance admission',
            "      - name: Validate internal provenance admission`n        if: " + '${{ false }}'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'provenance-validator-removed' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.Workflow).Replace(
            './scripts/ci/Test-InternalProvenance.ps1',
            './scripts/ci/Missing-ProvenanceValidator.ps1'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    Assert-WorkflowRejected 'provenance-validator-after-upload' {
        param($case)
        $content = Get-Content -Raw -LiteralPath $case.Workflow
        $content = $content.Replace('      - name: Validate internal provenance admission', '      - name: __NQ_ADMISSION_PLACEHOLDER__')
        $content = $content.Replace('      - name: Upload backend delivery evidence', '      - name: Validate internal provenance admission')
        $content = $content.Replace('      - name: __NQ_ADMISSION_PLACEHOLDER__', '      - name: Upload backend delivery evidence')
        [IO.File]::WriteAllText($case.Workflow, $content)
    }
    $criticalStepNames=@(
        'Run backend tests',
        'Run production configuration fail-closed regression',
        'Build canonical release and external admission',
        'Verify canonical release and external admission',
        'Install and activate admitted canonical release',
        'Run current-schema backup and restore drill',
        'Verify canonical backup creation and integrity',
        'Verify canonical post-restore validation'
    )
    foreach($criticalName in $criticalStepNames){
        $caseName=($criticalName.ToLowerInvariant()-replace'[^a-z0-9]+','-').Trim('-')
        $name=$criticalName
        Assert-WorkflowRejected "$caseName-removed" {
            param($case);$content=Get-Content $case.Workflow -Raw;$pattern='(?ms)^      - name:\s*'+[regex]::Escape($name)+'\s*\r?\n.*?(?=^      - name:|^  [A-Za-z0-9_-]+:\s*$|\z)';[IO.File]::WriteAllText($case.Workflow,[regex]::Replace($content,$pattern,''))
        }.GetNewClosure()
        Assert-WorkflowRejected "$caseName-conditional" {
            param($case);$content=(Get-Content $case.Workflow -Raw).Replace("      - name: $name","      - name: $name`n        if: "+'${{ false }}');[IO.File]::WriteAllText($case.Workflow,$content)
        }.GetNewClosure()
        Assert-WorkflowRejected "$caseName-soft-fail" {
            param($case);$content=(Get-Content $case.Workflow -Raw).Replace("      - name: $name","      - name: $name`n        continue-on-error: true");[IO.File]::WriteAllText($case.Workflow,$content)
        }.GetNewClosure()
        Assert-WorkflowRejected "$caseName-failure-ignored" {
            param($case);$content=Get-Content $case.Workflow -Raw;$pattern='(?m)^(\s*run:\s*[^\r\n]+)$';$stepPattern='(?ms)(^      - name:\s*'+[regex]::Escape($name)+'\s*\r?\n.*?)(?=^      - name:|^  [A-Za-z0-9_-]+:\s*$|\z)';$match=[regex]::Match($content,$stepPattern);if(-not$match.Success){throw "Missing critical fixture: $name"};$mutated=[regex]::Replace($match.Value,$pattern,'$1 || true',1);[IO.File]::WriteAllText($case.Workflow,$content.Remove($match.Index,$match.Length).Insert($match.Index,$mutated))
        }.GetNewClosure()
    }
    Assert-WorkflowRejected 'production-config-regression-unrelated-test' {
        param($case)
        $content = (Get-Content -Raw -LiteralPath $case.Workflow).Replace(
            '-Dtest=ProductionConfigurationApplicationContextInitializerTest,ProductionSecretProfileRegressionTest',
            '-Dtest=EnvSafetyValidatorTest'
        )
        [IO.File]::WriteAllText($case.Workflow, $content)
    }

    # Required capabilities omit this key entirely. Cover literals, expressions and both owner levels
    # without implementing GitHub's expression evaluator in the validator or accepting false aliases.
    $f008Name = 'Run production configuration fail-closed regression'
    $selector = '-Dtest=ProductionConfigurationApplicationContextInitializerTest,ProductionSecretProfileRegressionTest'
    $softFailValues = @('true', 'false', '${{ true }}', '${{ 1 == 1 }}', '${{ !false }}')
    for ($index = 0; $index -lt $softFailValues.Count; $index++) {
        $value = $softFailValues[$index]
        Assert-WorkflowRejected ('M{0:D2}-F008-continue-on-error' -f ($index + 1)) {
            param($case)
            $text = (Get-Content -Raw $case.Workflow).Replace("      - name: $f008Name", "      - name: $f008Name`n        continue-on-error: $value")
            [IO.File]::WriteAllText($case.Workflow, $text)
        }.GetNewClosure()
        Assert-WorkflowRejected ('required-backend-job-continue-on-error-{0}' -f ($index + 1)) {
            param($case)
            $text = (Get-Content -Raw $case.Workflow).Replace("  backend:`n", "  backend:`n    continue-on-error: $value`n")
            [IO.File]::WriteAllText($case.Workflow, $text)
        }.GetNewClosure()
    }
    Assert-WorkflowRejected 'M06-F008-removed' {
        param($case)
        $text = Get-Content -Raw $case.Workflow
        $pattern = '(?ms)^      - name: Run production configuration fail-closed regression\r?\n.*?(?=^      - name:|\z)'
        [IO.File]::WriteAllText($case.Workflow, [regex]::Replace($text, $pattern, ''))
    }
    Assert-WorkflowRejected 'M07-F008-conditional' {
        param($case)
        $text = (Get-Content -Raw $case.Workflow).Replace("      - name: $f008Name", "      - name: $f008Name`n        if: "+'${{ always() }}')
        [IO.File]::WriteAllText($case.Workflow, $text)
    }.GetNewClosure()
    Assert-WorkflowRejected 'M08-F008-selector-removed' {
        param($case)
        $text = (Get-Content -Raw $case.Workflow).Replace($selector, '-Dtest=ProductionConfigurationApplicationContextInitializerTest')
        [IO.File]::WriteAllText($case.Workflow, $text)
    }.GetNewClosure()
    Assert-WorkflowRejected 'M09-F008-unrelated-selectors' {
        param($case)
        $text = (Get-Content -Raw $case.Workflow).Replace($selector, '-Dtest=EnvSafetyValidatorTest,NoOutboundExchangeGuardTest')
        [IO.File]::WriteAllText($case.Workflow, $text)
    }.GetNewClosure()
    foreach ($wrapper in @(' || true', '; exit 0', ' || :', ' -Dmaven.test.failure.ignore=true')) {
        Assert-WorkflowRejected ('M10-F008-failure-ignore-' + ($wrapper -replace '[^a-zA-Z0-9]', '_')) {
            param($case)
            $text = (Get-Content -Raw $case.Workflow).Replace('-Dsurefire.failIfNoSpecifiedTests=false', '-Dsurefire.failIfNoSpecifiedTests=false' + $wrapper)
            [IO.File]::WriteAllText($case.Workflow, $text)
        }.GetNewClosure()
    }
    Assert-WorkflowRejected 'M11-full-backend-expression-soft-fail' {
        param($case)
        $text = (Get-Content -Raw $case.Workflow).Replace('      - name: Run backend tests', "      - name: Run backend tests`n        continue-on-error: "+'${{ true }}')
        [IO.File]::WriteAllText($case.Workflow, $text)
    }
    Assert-WorkflowRejected 'M12-dual-backend-expression-soft-fail' {
        param($case)
        $text = Get-Content -Raw $case.Workflow
        foreach ($name in @('Run backend tests', 'Run production configuration fail-closed regression')) {
            $text = $text.Replace("      - name: $name", "      - name: $name`n        continue-on-error: "+'${{ true }}')
        }
        [IO.File]::WriteAllText($case.Workflow, $text)
    }
    foreach ($duplicate in @($false, $true)) {
        $matrixId = if ($duplicate) { 'M14-duplicate-weakened-F008' } else { 'M13-F008-non-required-owner' }
        Assert-WorkflowRejected $matrixId {
            param($case)
            $text = Get-Content -Raw $case.Workflow
            $pattern = '(?ms)^      - name: Run production configuration fail-closed regression\r?\n.*?(?=^      - name:|\z)'
            $match = [regex]::Match($text, $pattern)
            Assert-Condition $match.Success 'F008 mutation target missing'
            $block = $match.Value
            if ($duplicate) {
                $text = $text.Insert($match.Index, $block.Replace('        shell: bash', '        continue-on-error: ${{ true }}' + "`n        shell: bash"))
            } else {
                $text = $text.Remove($match.Index, $match.Length) + "`n  optional-production-config:`n    name: Optional production configuration`n    runs-on: ubuntu-latest`n    steps:`n" + $block
            }
            [IO.File]::WriteAllText($case.Workflow, $text)
        }.GetNewClosure()
    }
    Assert-WorkflowRejected 'canonical-admission-downgraded-to-contract-only' {
        param($case)
        $text = (Get-Content -Raw $case.Workflow).Replace('./scripts/ci/Test-CanonicalDeliveryWorkflow.ps1', './scripts/ci/Test-CanonicalDeliveryWorkflow.ps1 -ContractOnly')
        [IO.File]::WriteAllText($case.Workflow, $text)
    }
    Assert-WorkflowRejected 'F008-quoted-soft-fail-key' {
        param($case)
        $text = (Get-Content -Raw $case.Workflow).Replace('      - name: Run production configuration fail-closed regression', "      - name: Run production configuration fail-closed regression`n        `"continue-on-error`": "+'${{ true }}')
        [IO.File]::WriteAllText($case.Workflow, $text)
    }

    $archiveCase = New-Case 'gitleaks-archive-checksum'
    $lock = Get-Content -Raw -LiteralPath $archiveCase.Lock | ConvertFrom-Json
    $tool = $lock.tools | Where-Object name -eq 'gitleaks'
    $archive = Join-Path $archiveCase.Root ([string]$tool.artifact)
    [IO.File]::WriteAllText($archive, 'verified archive fixture', [Text.Encoding]::UTF8)
    $tool.sha256 = (Get-FileHash -Algorithm SHA256 -LiteralPath $archive).Hash.ToLowerInvariant()
    Write-Json $archiveCase.Lock $lock
    & $archiveValidator -SupplyChainLockPath $archiveCase.Lock -ToolName gitleaks -Platform linux -Architecture x64 -ArchivePath $archive | Out-Null

    $checksumMismatchLock = Join-Path $archiveCase.Root 'checksum-mismatch-lock.json'
    $lock.tools[0].sha256 = ('0' * 64)
    Write-Json $checksumMismatchLock $lock
    $checksumRejected = $false
    try {
        & $archiveValidator -SupplyChainLockPath $checksumMismatchLock -ToolName gitleaks -Platform linux -Architecture x64 -ArchivePath $archive | Out-Null
    } catch { $checksumRejected = $true }
    Assert-Condition $checksumRejected 'Gitleaks checksum mismatch unexpectedly passed'

    Add-Content -LiteralPath $archive -Value 'tamper'
    $tamperRejected = $false
    try {
        & $archiveValidator -SupplyChainLockPath $archiveCase.Lock -ToolName gitleaks -Platform linux -Architecture x64 -ArchivePath $archive | Out-Null
    } catch { $tamperRejected = $true }
    Assert-Condition $tamperRejected 'Tampered gitleaks archive unexpectedly passed'

    Write-Output "MUTATIONS_REJECTED=$script:negativeCount"
    Write-Output 'SUPPLY_CHAIN_TEST real=PASS actions-negative=PASS tools-negative=PASS images-negative=PASS consumer-negative=PASS provenance-admission-negative=PASS schema-negative=PASS tamper-negative=PASS'
} finally {
    if (Test-Path -LiteralPath $runRoot) {
        $resolvedArtifacts = (Resolve-Path -LiteralPath $artifactRoot).Path
        $resolvedRun = (Resolve-Path -LiteralPath $runRoot).Path
        if (-not $resolvedRun.StartsWith($resolvedArtifacts + [IO.Path]::DirectorySeparatorChar)) {
            throw 'Refusing to remove supply-chain test artifacts outside repository artifacts root'
        }
        # Java can create nested class names beyond MAX_PATH even when the source-copy root is short.
        # Use Windows' extended literal path only after the ordinary absolute containment check above.
        $cleanupPath = if ([IO.Path]::DirectorySeparatorChar -eq '\') { '\\?\' + $resolvedRun } else { $resolvedRun }
        Remove-Item -LiteralPath $cleanupPath -Recurse -Force
    }
}
