[CmdletBinding()]
param(
    [string] $WorkflowPath = '.github/workflows/ci.yml',
    [string] $SupplyChainLockPath = 'scripts/ci/delivery-supply-chain-lock.json',
    [string] $RestoreDrillPath = 'scripts/deployment/Invoke-NqCanonicalRestoreDrill.ps1',
    [string] $ReleaseRegressionPath = 'scripts/deployment/tests/Test-NqCanonicalRelease.Tests.ps1',
    [string] $ReleaseBuilderPath = 'scripts/deployment/New-NqCanonicalRelease.ps1',
    [string] $InstallerPath = 'scripts/deployment/Install-NqCanonicalRelease.ps1',
    [string] $DeploymentContractPath = 'deploy/canonical/deployment-contract.json'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Assert-Condition([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

function Get-RequiredProperty([object] $Object, [string] $Name, [string] $Context) {
    if ($null -eq $Object) { throw "Missing required object: $Context" }
    $property = $Object.PSObject.Properties[$Name]
    if ($null -eq $property -or $null -eq $property.Value) {
        throw "Missing required field: $Context.$Name"
    }
    return $property.Value
}

function Get-RequiredText([object] $Object, [string] $Name, [string] $Context) {
    $value = [string](Get-RequiredProperty $Object $Name $Context)
    if ([string]::IsNullOrWhiteSpace($value)) { throw "Blank required field: $Context.$Name" }
    return $value
}

function Assert-Unique([object[]] $Items, [scriptblock] $KeySelector, [string] $Context) {
    $seen = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach ($item in $Items) {
        $key = [string](& $KeySelector $item)
        Assert-Condition ($seen.Add($key)) "Duplicate $Context identity: $key"
    }
}

function Get-WorkflowSteps([string] $WorkflowContent) {
    $stepPattern = '(?ms)^      - name:\s*(?<name>[^\r\n]+)\r?\n(?<body>.*?)(?=^      - name:|^  [A-Za-z0-9_-]+:\s*$|\z)'
    $steps = @()
    foreach ($match in [regex]::Matches($WorkflowContent, $stepPattern)) {
        $jobMatches = [regex]::Matches(
            $WorkflowContent.Substring(0, $match.Index),
            '(?m)^  (?<job>[A-Za-z0-9_-]+):\s*$'
        )
        Assert-Condition ($jobMatches.Count -gt 0) "Unable to resolve workflow job for step: $($match.Groups['name'].Value)"
        $steps += [pscustomobject]@{
            Name = $match.Groups['name'].Value.Trim()
            Body = $match.Groups['body'].Value
            Text = $match.Value
            Index = $match.Index
            Job = $jobMatches[$jobMatches.Count - 1].Groups['job'].Value
        }
    }
    Assert-Condition ($steps.Count -gt 0) 'No named workflow steps were found'
    return $steps
}

function Get-WorkflowJobs([string] $WorkflowContent) {
    $jobsMarker = [regex]::Match($WorkflowContent, '(?m)^jobs:\s*$')
    Assert-Condition $jobsMarker.Success 'Workflow jobs mapping is missing'
    $jobsContent = $WorkflowContent.Substring($jobsMarker.Index + $jobsMarker.Length)
    $jobPattern = '(?ms)^  (?<id>[A-Za-z0-9_-]+):\s*\r?\n(?<body>.*?)(?=^  [A-Za-z0-9_-]+:\s*$|\z)'
    $jobs = @()
    foreach ($match in [regex]::Matches($jobsContent, $jobPattern)) {
        $nameMatch = [regex]::Match($match.Groups['body'].Value, '(?m)^    name:\s*(?<name>[^\r\n#]+?)\s*$')
        Assert-Condition $nameMatch.Success "Required job name is missing: $($match.Groups['id'].Value)"
        $jobs += [pscustomobject]@{
            Id = $match.Groups['id'].Value
            Name = $nameMatch.Groups['name'].Value.Trim()
            Body = $match.Groups['body'].Value
            Text = $match.Value
            Index = $jobsMarker.Index + $jobsMarker.Length + $match.Index
        }
    }
    Assert-Condition ($jobs.Count -gt 0) 'No workflow jobs were found'
    return $jobs
}

function Assert-RequiredJob([object[]] $Jobs, [string] $Id, [string] $ExpectedName) {
    $matches = @($Jobs | Where-Object { [string]$_.Id -ceq $Id })
    Assert-Condition ($matches.Count -eq 1) "Required workflow job missing or duplicated: $Id"
    $job = $matches[0]
    Assert-Condition ([string]$job.Name -ceq $ExpectedName) "Required workflow job name mismatch: $Id"
    Assert-Condition (-not [regex]::IsMatch($job.Body, '(?m)^    if:\s*')) "Required workflow job is conditional: $Id"
    $continueMatches = [regex]::Matches($job.Body, '(?m)^    continue-on-error:\s*(?<value>[^\r\n#]+)')
    Assert-Condition ($continueMatches.Count -le 1) "Required workflow job has duplicate continue-on-error: $Id"
    if ($continueMatches.Count -eq 1) {
        Assert-Condition ($continueMatches[0].Groups['value'].Value.Trim() -ceq 'false') "Required workflow job can soft-fail: $Id"
    }
    return $job
}

function Assert-RequiredStep(
    [object[]] $Steps,
    [string] $Name,
    [string] $ExpectedJob = ''
) {
    $matches = @($Steps | Where-Object { [string]$_.Name -ceq $Name })
    Assert-Condition ($matches.Count -eq 1) "Required workflow step missing or duplicated: $Name"
    $step = $matches[0]
    if (-not [string]::IsNullOrWhiteSpace($ExpectedJob)) {
        Assert-Condition ([string]$step.Job -ceq $ExpectedJob) "Workflow step is in the wrong job: $Name"
    }
    Assert-Condition (-not [regex]::IsMatch($step.Text, '(?m)^\s+continue-on-error:\s*true\s*$')) "Required workflow step soft-fails: $Name"
    Assert-Condition (-not [regex]::IsMatch($step.Text, '(?m)^\s+if:\s*')) "Required workflow step is conditional: $Name"
    return $step
}

function Assert-SinglePurposeStep([object[]]$Steps,[string]$Name,[string]$Job,[string]$Invocation) {
    $step=Assert-RequiredStep $Steps $Name $Job
    $runPattern='(?m)^\s*run:\s*'+[regex]::Escape($Invocation)+'\s*$'
    Assert-Condition ([regex]::Matches($step.Body,$runPattern).Count-eq1) "Critical step invocation mismatch: $Name"
    Assert-Condition (-not[regex]::IsMatch($step.Body,'(?i)(\|\|\s*true|;\s*exit\s+0|try\s*\{|catch\s*\{|ErrorAction\s+SilentlyContinue|LASTEXITCODE)')) "Critical step can ignore failure: $Name"
    return $step
}

$content = Get-Content -LiteralPath $WorkflowPath -Raw -Encoding UTF8
$steps = @(Get-WorkflowSteps $content)
$jobs = @(Get-WorkflowJobs $content)
try {
    $supplyChainLock = Get-Content -LiteralPath $SupplyChainLockPath -Raw -Encoding UTF8 | ConvertFrom-Json
} catch {
    throw "Invalid supply-chain lock JSON: $($_.Exception.Message)"
}
Assert-Condition ((Get-RequiredText $supplyChainLock 'schemaVersion' 'lock') -ceq 'nq-delivery-supply-chain-lock-v1') 'Supply-chain lock schema is invalid'

# This ordered map is the single source for canonical required job IDs and their check names.
$requiredJobs = [ordered]@{
    'diff-check' = 'Repository hygiene and governance'
    'no-outbound-guard' = 'Runtime safety and no-outbound'
    'backend' = 'Backend regression'
    'postgres-flyway' = 'PostgreSQL and Flyway'
    'frontend-critical' = 'Frontend build and critical E2E'
    'research' = 'Research quality'
    'secret-scan' = 'Secret scanning'
    'java-engineering-shadow' = 'Java architecture guard'
    'delivery-provenance' = 'Delivery SBOM and provenance'
}
$requiredJobIds = @($requiredJobs.Keys)
foreach ($entry in $requiredJobs.GetEnumerator()) {
    [void](Assert-RequiredJob $jobs ([string]$entry.Key) ([string]$entry.Value))
}

# Every action occurrence must match one active lock identity, including its human-readable version label.
$actionMatches = [regex]::Matches($content, '(?m)^\s*uses:\s*([^@\s]+)@([^\s#]+)\s*(?:#\s*(\S+))?\s*$')
Assert-Condition ($actionMatches.Count -gt 0) 'No GitHub Actions identities were found'
$lockedActions = @(Get-RequiredProperty $supplyChainLock 'actions' 'lock')
Assert-Condition ($lockedActions.Count -gt 0) 'Supply-chain lock actions are empty'
Assert-Unique $lockedActions { param($item) Get-RequiredText $item 'repository' 'lock.actions[]' } 'action repository'
$activeActions = @($lockedActions | Where-Object { [string]$_.usage -ceq 'ACTIVE_CI' })
Assert-Condition ($activeActions.Count -eq $lockedActions.Count) 'All lock actions must explicitly be ACTIVE_CI'
foreach ($locked in $activeActions) {
    $repository = Get-RequiredText $locked 'repository' 'lock.actions[]'
    $tag = Get-RequiredText $locked 'tag' "lock.actions[$repository]"
    $commit = Get-RequiredText $locked 'commit' "lock.actions[$repository]"
    $occurrences = [int](Get-RequiredProperty $locked 'expectedOccurrences' "lock.actions[$repository]")
    Assert-Condition ($repository -cmatch '^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$') "Invalid action repository: $repository"
    Assert-Condition ($tag -cmatch '^v[0-9]+\.[0-9]+\.[0-9]+$') "Invalid action version label: $repository"
    Assert-Condition ($commit -cmatch '^[0-9a-f]{40}$') "Invalid locked action commit: $repository"
    Assert-Condition ($occurrences -gt 0) "Invalid expected action occurrence count: $repository"
    $actual = @($actionMatches | Where-Object {
        $_.Groups[1].Value -ceq $repository -and
        $_.Groups[2].Value -ceq $commit -and
        $_.Groups[3].Value -ceq $tag
    }).Count
    Assert-Condition ($actual -eq $occurrences) "Locked action occurrence mismatch: $repository expected=$occurrences actual=$actual"
}
foreach ($match in $actionMatches) {
    $repository = $match.Groups[1].Value
    $commit = $match.Groups[2].Value
    $tag = $match.Groups[3].Value
    Assert-Condition ($commit -cmatch '^[0-9a-f]{40}$') "Mutable action identity: $repository@$commit"
    Assert-Condition ($tag -cmatch '^v[0-9]+\.[0-9]+\.[0-9]+$') "Action version comment missing: $repository@$commit"
    $lockedMatch = @($activeActions | Where-Object {
        [string]$_.repository -ceq $repository -and
        [string]$_.commit -ceq $commit -and
        [string]$_.tag -ceq $tag
    })
    Assert-Condition ($lockedMatch.Count -eq 1) "Workflow action is not an exact lock identity: $repository@$commit # $tag"
}

# Active tools are either dynamically consumed from the lock or compared to it; identities are not duplicated here.
$lockedTools = @(Get-RequiredProperty $supplyChainLock 'tools' 'lock')
Assert-Condition ($lockedTools.Count -gt 0) 'Supply-chain lock tools are empty'
Assert-Unique $lockedTools { param($item) Get-RequiredText $item 'name' 'lock.tools[]' } 'tool name'
$activeTools = @($lockedTools | Where-Object { [string]$_.usage -ceq 'ACTIVE_CI' })
Assert-Condition ($activeTools.Count -eq $lockedTools.Count) 'All lock tools must explicitly be ACTIVE_CI'

$gitleaksMatches = @($activeTools | Where-Object { [string]$_.name -ceq 'gitleaks' })
Assert-Condition ($gitleaksMatches.Count -eq 1) 'Exactly one active gitleaks lock identity is required'
$gitleaks = $gitleaksMatches[0]
$gitleaksVersion = Get-RequiredText $gitleaks 'version' 'lock.tools[gitleaks]'
$gitleaksArtifact = Get-RequiredText $gitleaks 'artifact' 'lock.tools[gitleaks]'
$gitleaksPlatform = Get-RequiredText $gitleaks 'platform' 'lock.tools[gitleaks]'
$gitleaksArchitecture = Get-RequiredText $gitleaks 'architecture' 'lock.tools[gitleaks]'
$gitleaksSha256 = Get-RequiredText $gitleaks 'sha256' 'lock.tools[gitleaks]'
$gitleaksDownloadUrl = Get-RequiredText $gitleaks 'downloadUrl' 'lock.tools[gitleaks]'
$gitleaksChecksumSource = Get-RequiredText $gitleaks 'checksumSource' 'lock.tools[gitleaks]'
$gitleaksBaseUrl = "https://github.com/gitleaks/gitleaks/releases/download/v$gitleaksVersion"
Assert-Condition ($gitleaksVersion -cmatch '^[0-9]+\.[0-9]+\.[0-9]+$') 'Invalid gitleaks version identity'
Assert-Condition ($gitleaksPlatform -ceq 'linux' -and $gitleaksArchitecture -ceq 'x64') 'Unsupported gitleaks platform identity'
Assert-Condition ($gitleaksArtifact -ceq "gitleaks_${gitleaksVersion}_${gitleaksPlatform}_${gitleaksArchitecture}.tar.gz") 'Gitleaks artifact/version identity mismatch'
Assert-Condition ($gitleaksSha256 -cmatch '^[0-9a-f]{64}$') 'Invalid gitleaks checksum identity'
Assert-Condition ($gitleaksDownloadUrl -ceq "$gitleaksBaseUrl/$gitleaksArtifact") 'Gitleaks download URL identity mismatch'
Assert-Condition ($gitleaksChecksumSource -ceq "$gitleaksBaseUrl/gitleaks_${gitleaksVersion}_checksums.txt") 'Gitleaks official checksum source identity mismatch'
Assert-Condition ([regex]::Matches($content, 'Export-DeliveryToolIdentity\.ps1').Count -eq 1) 'Workflow must load gitleaks identity from the lock exactly once'
Assert-Condition ([regex]::Matches($content, 'Test-DeliveryToolArchive\.ps1').Count -eq 2) 'Workflow must verify original and tampered gitleaks archives'
foreach ($variable in @('NQ_GITLEAKS_VERSION', 'NQ_GITLEAKS_ARTIFACT', 'NQ_GITLEAKS_DOWNLOAD_URL')) {
    Assert-Condition ($content.Contains($variable)) "Workflow does not consume exported lock identity: $variable"
}
Assert-Condition (-not [regex]::IsMatch($content, '(?m)^\s+GITLEAKS_(VERSION|.*SHA256):')) 'Workflow duplicates a canonical gitleaks version or checksum'
Assert-Condition (-not [regex]::IsMatch($content, '(?im)^.*sudo.*gitleaks.*$')) 'Gitleaks flow must not use sudo'
Assert-Condition (-not $content.Contains('/usr/local/bin/gitleaks')) 'Gitleaks flow must not write /usr/local/bin'
$gitleaksIdentityStep = Assert-RequiredStep $steps 'Load canonical gitleaks identity' 'secret-scan'
$gitleaksInstallStep = Assert-RequiredStep $steps 'Install verified gitleaks CLI in runner temp' 'secret-scan'
$gitleaksScanStep = Assert-RequiredStep $steps 'Run pinned gitleaks secret scan (tracked working tree, no history)' 'secret-scan'
Assert-Condition ($gitleaksIdentityStep.Body.Contains('Export-DeliveryToolIdentity.ps1')) 'Gitleaks identity step does not consume the canonical lock exporter'
Assert-Condition ($gitleaksInstallStep.Body.Contains('realpath "${work}/bin/gitleaks"')) 'Gitleaks executable is not resolved to an absolute isolated path'
Assert-Condition ($gitleaksInstallStep.Body.Contains("NQ_GITLEAKS_BIN=%s\n")) 'Verified gitleaks absolute path is not persisted'
Assert-Condition (-not $gitleaksInstallStep.Body.Contains('GITHUB_PATH')) 'Gitleaks must not rely on PATH persistence'
Assert-Condition ($gitleaksScanStep.Body.Contains('Invoke-VerifiedGitleaks.ps1')) 'Canonical gitleaks consumer invocation is missing'
Assert-Condition ($gitleaksScanStep.Body.Contains('-BinaryPath "${NQ_GITLEAKS_BIN}"')) 'Canonical gitleaks scan is not bound to the verified absolute path'
Assert-Condition (-not [regex]::IsMatch($content, '(?m)^\s*gitleaks\s+detect(?:\s|\\)')) 'Bare gitleaks scan invocation is forbidden'
Assert-Condition ([regex]::Matches($content, 'Invoke-VerifiedGitleaks\.ps1').Count -eq 1) 'Verified gitleaks consumer must run exactly once'
Assert-Condition ([regex]::Matches($content, 'Test-GitleaksExecution\.Tests\.ps1').Count -eq 1) 'Gitleaks isolation regression must run exactly once'

$cycloneDxMatches = @($activeTools | Where-Object { [string]$_.name -ceq 'cyclonedx-maven-plugin' })
Assert-Condition ($cycloneDxMatches.Count -eq 1) 'Exactly one active CycloneDX Maven plugin identity is required'
$cycloneDxVersion = Get-RequiredText $cycloneDxMatches[0] 'version' 'lock.tools[cyclonedx-maven-plugin]'
$cycloneDxWorkflowMatches = [regex]::Matches($content, 'org\.cyclonedx:cyclonedx-maven-plugin:([^:\s]+):makeAggregateBom')
Assert-Condition ($cycloneDxWorkflowMatches.Count -eq 1) 'Workflow must invoke the CycloneDX Maven plugin exactly once'
Assert-Condition ($cycloneDxWorkflowMatches[0].Groups[1].Value -ceq $cycloneDxVersion) 'CycloneDX Maven plugin version does not match lock'

# Every active service image occurrence must exactly match a lock name/tag/digest and vice versa.
$lockedImages = @(Get-RequiredProperty $supplyChainLock 'images' 'lock')
Assert-Condition ($lockedImages.Count -gt 0) 'Supply-chain lock images are empty'
Assert-Unique $lockedImages { param($item) "$(Get-RequiredText $item 'name' 'lock.images[]'):$(Get-RequiredText $item 'tag' 'lock.images[]')" } 'image name/tag'
$activeImages = @($lockedImages | Where-Object { [string]$_.usage -ceq 'ACTIVE_CI_PINNED' })
Assert-Condition ($activeImages.Count -gt 0) 'At least one active pinned image identity is required'
$imageMatches = [regex]::Matches($content, '(?m)^\s*image:\s*([A-Za-z0-9._/-]+):([^@\s]+)@(sha256:[0-9a-f]+)\s*$')
Assert-Condition ($imageMatches.Count -gt 0) 'No digest-pinned workflow service images were found'
$allImageLines = [regex]::Matches($content, '(?m)^\s*image:\s*([^\s]+)\s*$')
Assert-Condition ($allImageLines.Count -eq $imageMatches.Count) 'Mutable or malformed workflow service image identity exists'
foreach ($locked in $activeImages) {
    $name = Get-RequiredText $locked 'name' 'lock.images[]'
    $tag = Get-RequiredText $locked 'tag' "lock.images[$name]"
    $digest = Get-RequiredText $locked 'digest' "lock.images[${name}:${tag}]"
    $expectedOccurrences = [int](Get-RequiredProperty $locked 'expectedOccurrences' "lock.images[${name}:${tag}]")
    Assert-Condition ($digest -cmatch '^sha256:[0-9a-f]{64}$') "Invalid locked image digest: ${name}:${tag}"
    Assert-Condition ($expectedOccurrences -gt 0) "Invalid expected image occurrence count: ${name}:${tag}"
    $actual = @($imageMatches | Where-Object {
        $_.Groups[1].Value -ceq $name -and $_.Groups[2].Value -ceq $tag -and $_.Groups[3].Value -ceq $digest
    }).Count
    Assert-Condition ($actual -eq $expectedOccurrences) "Locked image occurrence mismatch: ${name}:${tag} expected=$expectedOccurrences actual=$actual"
}
foreach ($match in $imageMatches) {
    $name = $match.Groups[1].Value
    $tag = $match.Groups[2].Value
    $digest = $match.Groups[3].Value
    $lockedMatch = @($activeImages | Where-Object {
        [string]$_.name -ceq $name -and [string]$_.tag -ceq $tag -and [string]$_.digest -ceq $digest
    })
    Assert-Condition ($lockedMatch.Count -eq 1) "Workflow image is not an exact lock identity: ${name}:${tag}@$digest"
}

$expectedNames = @($requiredJobs.Values)
foreach ($name in $expectedNames) {
    Assert-Condition ([regex]::Matches($content, "(?m)^    name: $([regex]::Escape($name))$").Count -eq 1) "Required check name missing or duplicated: $name"
}
Assert-Condition (-not [regex]::IsMatch($content, '(?m)^\s+continue-on-error:\s*true\s*$')) 'Canonical workflow must not contain continue-on-error: true'
Assert-Condition ([regex]::Matches($content, '(?m)^\s+run:\s+npm run build\s*$').Count -eq 1) 'Frontend production build must execute exactly once'
Assert-Condition (-not $content.Contains('ci-security-smoke:')) 'Duplicate CI security job still exists'
foreach ($test in @('EnvSafetyValidatorTest', 'NoOutboundExchangeGuardTest', 'NoRealExchangeCredentialPermissionProbePortTest')) {
    Assert-Condition ($content.Contains($test)) "Runtime safety regression is missing: $test"
}
Assert-Condition (-not $content.Contains('id-token: write')) 'Platform attestation permission must remain disabled'
Assert-Condition ($content.Contains('npm sbom --sbom-format cyclonedx')) 'Frontend SBOM generator is missing'
Assert-Condition ([regex]::Matches($content, 'Test-DeliveryArtifactSafety\.ps1').Count -eq 2) 'Both delivery evidence producers must run the artifact safety gate'
# Canonical dependency consumers must be reachable, fail closed, and consume repository locks.
$lockValidationStep = Assert-RequiredStep $steps 'Validate canonical delivery workflow contract' 'diff-check'
Assert-Condition ($lockValidationStep.Body.Contains('Test-CanonicalDeliveryWorkflow.ps1')) 'Canonical lock validator invocation is missing'
Assert-Condition ($lockValidationStep.Body.Contains('Test-CanonicalDeliveryWorkflow.Tests.ps1')) 'Canonical lock mutation regressions are not executed'
Assert-Condition ($lockValidationStep.Body.Contains('Test-GitleaksExecution.Tests.ps1')) 'Gitleaks execution regressions are not executed'
Assert-Condition ($lockValidationStep.Body.Contains('Test-NqCanonicalRelease.Tests.ps1')) 'Canonical release regression is not executed'

$releaseBuildStep=Assert-SinglePurposeStep $steps 'Build canonical release and external admission' 'frontend-critical' './scripts/deployment/Build-NqCanonicalReleaseCi.ps1 -SourceCommit $env:NQ_SOURCE_COMMIT'
$releaseVerifyStep=Assert-SinglePurposeStep $steps 'Verify canonical release and external admission' 'frontend-critical' './scripts/deployment/Test-NqCanonicalReleaseCi.ps1 -SourceCommit $env:NQ_SOURCE_COMMIT'
$releaseInstallStep=Assert-SinglePurposeStep $steps 'Install and activate admitted canonical release' 'frontend-critical' './scripts/deployment/Install-NqCanonicalReleaseCi.ps1 -SourceCommit $env:NQ_SOURCE_COMMIT'
$restoreStep=Assert-SinglePurposeStep $steps 'Run current-schema backup and restore drill' 'postgres-flyway' './scripts/deployment/Invoke-NqCanonicalRestoreCi.ps1 -SourceCommit $env:NQ_SOURCE_COMMIT'
$backupStep=Assert-SinglePurposeStep $steps 'Verify canonical backup creation and integrity' 'postgres-flyway' "./scripts/deployment/Test-NqCanonicalRestoreProof.ps1 -Capability BACKUP_INTEGRITY -ProofRoot 'artifacts/phase5b-current-schema-restore' -ExpectedCommit `$env:NQ_SOURCE_COMMIT"
$postRestoreStep=Assert-SinglePurposeStep $steps 'Verify canonical post-restore validation' 'postgres-flyway' "./scripts/deployment/Test-NqCanonicalRestoreProof.ps1 -Capability POST_RESTORE_VALIDATION -ProofRoot 'artifacts/phase5b-current-schema-restore' -ExpectedCommit `$env:NQ_SOURCE_COMMIT"

Assert-Condition (Test-Path -LiteralPath $RestoreDrillPath -PathType Leaf) 'Restore drill implementation is missing'
Assert-Condition (Test-Path -LiteralPath $ReleaseRegressionPath -PathType Leaf) 'Canonical release regression implementation is missing'
Assert-Condition (Test-Path -LiteralPath $ReleaseBuilderPath -PathType Leaf) 'Canonical release builder implementation is missing'
Assert-Condition (Test-Path -LiteralPath $InstallerPath -PathType Leaf) 'Canonical installer implementation is missing'
Assert-Condition (Test-Path -LiteralPath $DeploymentContractPath -PathType Leaf) 'Canonical deployment contract is missing'
$restoreImplementation = Get-Content -LiteralPath $RestoreDrillPath -Raw -Encoding UTF8
$releaseRegression = Get-Content -LiteralPath $ReleaseRegressionPath -Raw -Encoding UTF8
$releaseBuilder = Get-Content -LiteralPath $ReleaseBuilderPath -Raw -Encoding UTF8
$installerImplementation = Get-Content -LiteralPath $InstallerPath -Raw -Encoding UTF8
try { $deploymentContract = Get-Content -LiteralPath $DeploymentContractPath -Raw -Encoding UTF8 | ConvertFrom-Json } catch { throw 'Canonical deployment contract JSON is invalid' }
Assert-Condition (-not $releaseBuilder.Contains('AllowCandidateWorktree')) 'Deployable release builder retains dirty-worktree bypass'
foreach($marker in @('DEPLOYABLE_RELEASE_REQUIRES_CLEAN_COMMITTED_TREE','UNCOMMITTED_CANDIDATE','candidate-sha256:',
        'BackendArtifactManifestPath','FrontendArtifactManifestPath','DEPLOYABLE_ARTIFACT_SET_MISMATCH')) {
    Assert-Condition ($releaseBuilder.Contains($marker)) "Release source identity capability is missing: $marker"
}
foreach($marker in @('NON_DEPLOYABLE_RELEASE_FORBIDDEN','nq-canonical-activation-journal.v2','HMACSHA256',
        'CALLER_CONTROLLED_ROLLBACK_TARGET_FORBIDDEN','DATABASE_RECOVERY_REQUIRED','DATABASE_STATE_EVIDENCE_REQUIRED',
        'nq-canonical-activation-head.v1','STALE_ACTIVATION_AUTHORITY','Assert-KeyIdentity',
        'Enter-ActivationOperationLock','ACTIVATION_OPERATION_LOCK_TIMEOUT','FileShare]::None')) {
    Assert-Condition ($installerImplementation.Contains($marker)) "Activation/rollback capability is missing: $marker"
}
Assert-Condition ([int]$deploymentContract.database.postgresqlServerMajor -eq 16 -and
        [int]$deploymentContract.database.backupToolMajor -eq 16 -and
        [int]$deploymentContract.database.restoreToolMajor -eq 16) 'Canonical PostgreSQL major contract must be 16/16/16'
Assert-Condition ([string]$deploymentContract.releaseAdmission.productionMode -ceq 'EXACT_HEAD_CI' -and
        [bool]$deploymentContract.releaseAdmission.bundleSelfProofAllowed -eq $false -and
        [bool]$deploymentContract.releaseAdmission.callerProvidedTrustRootAllowed -eq $false) 'Canonical external admission contract is invalid'
$wrapperContracts=[ordered]@{
    'scripts/deployment/Build-NqCanonicalReleaseCi.ps1'=@('New-NqCanonicalRelease.ps1','New-NqCanonicalReleaseAdmission.ps1','EXACT_HEAD_CI')
    'scripts/deployment/Test-NqCanonicalReleaseCi.ps1'=@('Test-NqCanonicalRelease.ps1','Test-NqCanonicalReleaseAdmission.ps1','EXACT_HEAD_CI')
    'scripts/deployment/Install-NqCanonicalReleaseCi.ps1'=@('Install-NqCanonicalRelease.ps1','trusted-release-admission','TestProductionPolicy')
    'scripts/deployment/Invoke-NqCanonicalRestoreCi.ps1'=@('Invoke-NqCanonicalRestoreDrill.ps1','phase5b-current-schema-restore')
    'scripts/deployment/Test-NqCanonicalRestoreProof.ps1'=@('Test-NqCanonicalBackup','BACKUP_INTEGRITY','POST_RESTORE_VALIDATION')
}
foreach($wrapper in $wrapperContracts.GetEnumerator()){
    Assert-Condition (Test-Path $wrapper.Key -PathType Leaf) "Canonical critical wrapper missing: $($wrapper.Key)"
    $wrapperText=Get-Content $wrapper.Key -Raw -Encoding UTF8
    foreach($marker in $wrapper.Value){Assert-Condition $wrapperText.Contains($marker) "Canonical critical wrapper contract missing: $($wrapper.Key) marker=$marker"}
}
foreach ($marker in @('Test-NqCanonicalBackup', "Invoke-Flyway 'validate'", 'Invoke-Smoke $target',
        'tampered-backup', 'truncated-backup', 'wrong-schema-target', 'restore-command-failure',
        'post-restore-validation-mismatch', 'missing-flyway-history')) {
    Assert-Condition ($restoreImplementation.Contains($marker)) "Restore drill security capability is missing: $marker"
}
Assert-Condition (-not [regex]::IsMatch($restoreImplementation, '(?s)if\s*\(\$false\)\s*\{.{0,500}Test-NqCanonicalBackup')) 'Backup integrity admission is conditional'
Assert-Condition (-not $restoreImplementation.Contains('Test-NqCanonicalBackup -ErrorAction SilentlyContinue')) 'Backup integrity admission can soft-fail'
foreach ($marker in @('same-source-same-artifact-deterministic-identity', 'manifest-missing-rejected',
        'hard-link-rejected', 'immutable-install-and-atomic-initial-activation', 'verified-code-rollback',
        'cross-process-activation-serialization-eight-processes','activation-vs-rollback-serialized',
        'recovery-vs-activation-serialized','process-termination-releases-operation-lock')) {
    Assert-Condition ($releaseRegression.Contains($marker)) "Canonical release regression capability is missing: $marker"
}

$npmInstallStep = Assert-RequiredStep $steps 'Install frontend dependencies' 'frontend-critical'
Assert-Condition ([regex]::IsMatch($npmInstallStep.Body, '(?m)^\s*run:\s*npm ci\s*$')) 'Canonical frontend install must use npm ci'
Assert-Condition (-not [regex]::IsMatch($content, '(?m)^\s*run:\s*npm install(?:\s|$)')) 'npm install must not replace the lockfile-enforced canonical install'
Assert-Condition (-not [regex]::IsMatch($content, '(?i)(--no-package-lock|package-lock\s*=\s*false|@latest)')) 'Dynamic or lockfile-bypassing npm resolution is forbidden'

$playwrightInstallStep = Assert-RequiredStep $steps 'Install Playwright Chromium' 'frontend-critical'
Assert-Condition ([regex]::IsMatch($playwrightInstallStep.Body, '(?m)^\s*run:\s*npx --no-install playwright install --with-deps chromium\s*$')) 'Playwright browser install must use the repository-locked package'

$frontendBuildStep = Assert-RequiredStep $steps 'Build frontend production artifact' 'frontend-critical'
$backendBuildStep = Assert-RequiredStep $steps 'Package backend application artifact' 'frontend-critical'
Assert-Condition ([regex]::IsMatch($frontendBuildStep.Body, '(?m)^\s*run:\s*npm run build\s*$')) 'Canonical frontend production build invocation is missing'
Assert-Condition ($backendBuildStep.Body.Contains('mvn -f backend/pom.xml -pl nq-app -am -DskipTests install')) 'Canonical backend reactor install invocation is missing'
Assert-Condition ($backendBuildStep.Body.Contains('mvn -f backend/nq-app/pom.xml -DskipTests package spring-boot:repackage')) 'Executable Spring Boot application artifact build is missing'

$loopbackE2eStep = Assert-RequiredStep $steps 'Run loopback critical E2E allowlist' 'frontend-critical'
$realBackendE2eStep = Assert-RequiredStep $steps 'Run real-backend critical E2E allowlist' 'frontend-critical'
$loopbackSpecs = @(
    'login-page-smoke.spec.ts',
    'strategy-validation-paper-shadow-smoke.spec.ts',
    'validation-review-workbench-smoke.spec.ts'
)
$realBackendSpecs = @(
    'runtime-operational-readiness-final-smoke.spec.ts',
    'adapter-readiness-panel-backend-smoke.spec.ts'
)
Assert-Condition ($loopbackE2eStep.Body.Contains('npm run test:e2e --')) 'Loopback critical E2E command is missing'
Assert-Condition ($realBackendE2eStep.Body.Contains('npm run test:e2e --')) 'Real-backend critical E2E command is missing'
foreach ($spec in $loopbackSpecs) {
    Assert-Condition ([regex]::Matches($loopbackE2eStep.Body, [regex]::Escape($spec)).Count -eq 1) "Loopback critical E2E command does not bind exactly one spec: $spec"
}
foreach ($spec in $realBackendSpecs) {
    Assert-Condition ([regex]::Matches($realBackendE2eStep.Body, [regex]::Escape($spec)).Count -eq 1) "Real-backend critical E2E command does not bind exactly one spec: $spec"
}
$criticalE2eBody = $loopbackE2eStep.Body + "`n" + $realBackendE2eStep.Body
foreach ($rejected in @('backtest-detail-smoke.spec.ts', 'paper-trading-run-smoke.spec.ts')) {
    Assert-Condition (-not $criticalE2eBody.Contains($rejected)) "Unproven E2E candidate entered blocking CI execution: $rejected"
}

# Provenance admission must validate the real local artifacts before any delivery upload.
$frontendEvidenceStep = Assert-RequiredStep $steps 'Generate normalized frontend CycloneDX SBOM and artifact manifest' 'frontend-critical'
$backendEvidenceStep = Assert-RequiredStep $steps 'Generate normalized backend CycloneDX SBOM and artifact manifest' 'frontend-critical'
$generateStep = Assert-RequiredStep $steps 'Generate internal provenance manifest' 'frontend-critical'
$admissionStep = Assert-RequiredStep $steps 'Validate internal provenance admission' 'frontend-critical'
$backendUploadStep = Assert-RequiredStep $steps 'Upload backend delivery evidence' 'frontend-critical'
$frontendUploadStep = Assert-RequiredStep $steps 'Upload frontend delivery evidence' 'frontend-critical'
$provenanceUploadStep = Assert-RequiredStep $steps 'Upload internal provenance manifest' 'frontend-critical'
$postUploadReadbackStep = Assert-RequiredStep $steps 'Validate uploaded provenance readback' 'delivery-provenance'

Assert-Condition ($frontendEvidenceStep.Index -lt $generateStep.Index -and $backendEvidenceStep.Index -lt $generateStep.Index) 'SBOM and artifact manifests must exist before provenance generation'
Assert-Condition ($generateStep.Index -lt $admissionStep.Index) 'Provenance admission must follow generation'
foreach ($uploadStep in @($backendUploadStep, $frontendUploadStep, $provenanceUploadStep)) {
    Assert-Condition ($admissionStep.Index -lt $uploadStep.Index) "Delivery artifact upload occurs before provenance admission: $($uploadStep.Name)"
}
Assert-Condition ($generateStep.Body.Contains('New-InternalProvenance.ps1')) 'Canonical provenance generator invocation is missing'
Assert-Condition ($admissionStep.Body.Contains('Test-InternalProvenance.ps1')) 'Pre-upload provenance validator invocation is missing'
Assert-Condition ($admissionStep.Body.Contains("-ProvenancePath 'artifacts/delivery/provenance/internal-provenance.json'")) 'Pre-upload validator does not consume the local generated provenance'
Assert-Condition ($admissionStep.Body.Contains("-BackendEvidenceRoot 'artifacts/delivery/backend'")) 'Pre-upload validator does not bind the local backend artifact set'
Assert-Condition ($admissionStep.Body.Contains("-FrontendEvidenceRoot 'artifacts/delivery/frontend'")) 'Pre-upload validator does not bind the local frontend artifact set'
Assert-Condition ($postUploadReadbackStep.Body.Contains('Test-InternalProvenance.ps1')) 'Post-upload provenance readback proof is missing'
Assert-Condition ([regex]::Matches($content, 'Test-InternalProvenance\.ps1').Count -eq 2) 'Provenance validator must run once before upload and once after readback'

$criticalCapabilities = [ordered]@{
    'supply-chain-lock-validator' = $lockValidationStep
    'gitleaks-canonical-scan' = $gitleaksScanStep
    'backend-artifact-build' = $backendBuildStep
    'frontend-production-build' = $frontendBuildStep
    'npm-lock-enforced-install' = $npmInstallStep
    'playwright-locked-consumer' = $playwrightInstallStep
    'frontend-sbom-producer' = $frontendEvidenceStep
    'frontend-manifest-producer' = $frontendEvidenceStep
    'cyclonedx-backend-sbom-producer' = $backendEvidenceStep
    'backend-manifest-producer' = $backendEvidenceStep
    'provenance-producer' = $generateStep
    'pre-upload-provenance-admission' = $admissionStep
    'backend-delivery-upload' = $backendUploadStep
    'frontend-delivery-upload' = $frontendUploadStep
    'provenance-delivery-upload' = $provenanceUploadStep
    'post-upload-provenance-readback' = $postUploadReadbackStep
    'critical-e2e-loopback-execution' = $loopbackE2eStep
    'critical-e2e-real-backend-execution' = $realBackendE2eStep
    'canonical-release-build' = $releaseBuildStep
    'canonical-release-verifier' = $releaseVerifyStep
    'canonical-release-install-activation' = $releaseInstallStep
    'current-schema-restore-drill' = $restoreStep
    'backup-integrity-check' = $backupStep
    'post-restore-validation' = $postRestoreStep
}
foreach ($capability in $criticalCapabilities.GetEnumerator()) {
    $owner = [string]$capability.Value.Job
    Assert-Condition ($requiredJobIds -ccontains $owner) "Security-critical capability is owned by a non-required job: $($capability.Key) owner=$owner"
}

$candidate = $expectedNames | ConvertTo-Json -Compress
Write-Output "ACTION_OCCURRENCES=$($actionMatches.Count)"
Write-Output "ACTION_IDENTITIES=$($activeActions.Count)"
Write-Output 'MUTABLE_ACTION_IDENTITIES=0'
Write-Output "ACTIVE_TOOL_IDENTITIES=$($activeTools.Count)"
Write-Output "ACTIVE_IMAGE_OCCURRENCES=$($imageMatches.Count)"
Write-Output 'FRONTEND_PRODUCTION_BUILDS=1'
Write-Output 'GITLEAKS_ABSOLUTE_CONSUMER=PASS'
Write-Output 'PROVENANCE_PREUPLOAD_ADMISSION=PASS'
Write-Output 'SUPPLY_CHAIN_CONSUMERS=npm-ci,playwright-no-install,cyclonedx-pinned,gitleaks-absolute'
Write-Output "REQUIRED_JOBS_UNCONDITIONAL=$($requiredJobIds.Count)"
Write-Output "REQUIRED_JOB_IDS=$($requiredJobIds -join ',')"
Write-Output "CRITICAL_CAPABILITIES_OWNERSHIP=$($criticalCapabilities.Count)"
Write-Output 'CRITICAL_CAPABILITIES_MISSING=0'
Write-Output 'CRITICAL_CAPABILITIES_UNKNOWN=0'
Write-Output "CRITICAL_E2E_SPECS_BOUND=$($loopbackSpecs.Count + $realBackendSpecs.Count)"
Write-Output "REQUIRED_CHECK_CANDIDATE=$candidate"
Write-Output 'REMOTE_ENFORCEMENT=NOT_APPLIED'
