[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$gateyRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$repo = (Resolve-Path (Join-Path $gateyRoot '../..')).Path
$contract = Join-Path $gateyRoot 'gatey-readonly-release-contract.psm1'
$builder = Join-Path $gateyRoot 'build-gatey-readonly-release.ps1'
$deployment = Join-Path $gateyRoot 'invoke-gatey-readonly-deployment-contract.ps1'
$installer = Join-Path $gateyRoot 'install-gatey-readonly-release.ps1'
$runtimeDeployment = Join-Path $gateyRoot 'invoke-gatey-readonly-runtime-deployment.ps1'
$exactPilotControl = Join-Path $gateyRoot 'invoke-gatey-exact-pilot-scope.ps1'
$minimalLivePilotControl = Join-Path $gateyRoot 'invoke-gatey-minimal-live-pilot.ps1'
$systemdUnit = Join-Path $repo 'deploy/systemd/nq-gatey-readonly-qualification.service'
$runtimeEnvTemplate = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime.env.example'
$runtimeSecretsTemplate = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime.secrets.env.example'
$runtimePgpassTemplate = Join-Path $repo 'deploy/gatey/gatey-readonly-db.pgpass.example'
$runtimeTarget = Join-Path $repo 'deploy/gatey/gatey-readonly-runtime-target.json'
$gatewVerifier = Join-Path $repo 'scripts/gatew/verify-gatew-release.ps1'
$gatewContract = Join-Path $repo 'scripts/gatew/gatew-release-contract.psm1'
Import-Module $contract -Force -DisableNameChecking

$script:Cases = [Collections.Generic.List[string]]::new()
$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)

function Assert-Condition([bool]$Value, [string]$Message)
{
    if (-not $Value) { throw $Message }
}

function Complete-Case([string]$Name)
{
    $script:Cases.Add($Name)
}

function Write-TestText([string]$Path, [string]$Value)
{
    [IO.Directory]::CreateDirectory((Split-Path -Parent $Path)) | Out-Null
    [IO.File]::WriteAllText($Path, $Value, $script:Utf8NoBom)
}

function Add-TestArtifact([string]$Root, [string]$RelativePath, [string]$Source, [string]$Mode, [string]$Role)
{
    $target = Join-Path $Root $RelativePath
    [IO.Directory]::CreateDirectory((Split-Path -Parent $target)) | Out-Null
    Copy-Item -LiteralPath $Source -Destination $target
    return [pscustomobject][ordered]@{
        relativePath = $RelativePath.Replace('\', '/')
        size = (Get-Item -LiteralPath $target).Length
        sha256 = Get-GateYReadonlySha256File $target
        mode = $Mode
        role = $Role
    }
}

function New-TestRelease(
    [string]$Root,
    [string]$MigrationRoot,
    [string]$SourceCommit = '1111111111111111111111111111111111111111'
)
{
    Write-TestText (Join-Path $Root 'app-source.jar') 'deterministic-application-artifact'
    $artifacts = @(
        Add-TestArtifact $Root 'app/nq-app.jar' (Join-Path $Root 'app-source.jar') '0644' 'application'
        Add-TestArtifact $Root 'config/application-gatey-readonly-qualification.yml' (Join-Path $repo 'backend/nq-app/src/main/resources/application-gatey-readonly-qualification.yml') '0644' 'runtime-profile'
        Add-TestArtifact $Root 'bin/gatey-readonly-release-contract.psm1' $contract '0644' 'release-contract'
        Add-TestArtifact $Root 'bin/invoke-gatey-readonly-deployment-contract.ps1' $deployment '0755' 'deployment-contract'
        Add-TestArtifact $Root 'bin/install-gatey-readonly-release.ps1' $installer '0755' 'release-installer'
        Add-TestArtifact $Root 'bin/invoke-gatey-readonly-runtime-deployment.ps1' $runtimeDeployment '0755' 'runtime-deployment-orchestrator'
        Add-TestArtifact $Root 'bin/invoke-gatey-exact-pilot-scope.ps1' $exactPilotControl '0755' 'exact-pilot-control-surface'
        Add-TestArtifact $Root 'bin/invoke-gatey-minimal-live-pilot.ps1' $minimalLivePilotControl '0755' 'minimal-live-pilot-control-surface'
        Add-TestArtifact $Root 'config/nq-gatey-readonly-qualification.service' $systemdUnit '0644' 'systemd-runtime-contract'
        Add-TestArtifact $Root 'config/gatey-readonly-runtime.env.example' $runtimeEnvTemplate '0644' 'runtime-environment-template'
        Add-TestArtifact $Root 'config/gatey-readonly-runtime.secrets.env.example' $runtimeSecretsTemplate '0600' 'runtime-secret-environment-template'
        Add-TestArtifact $Root 'config/gatey-readonly-db.pgpass.example' $runtimePgpassTemplate '0600' 'database-credential-reference-template'
        Add-TestArtifact $Root 'config/gatey-readonly-runtime-target.json' $runtimeTarget '0644' 'runtime-target-contract'
        Add-TestArtifact $Root 'bin/verify-gatew-release.ps1' $gatewVerifier '0755' 'gatew-rollback-verifier'
        Add-TestArtifact $Root 'bin/gatew-release-contract.psm1' $gatewContract '0644' 'gatew-rollback-contract'
    )
    Remove-Item -LiteralPath (Join-Path $Root 'app-source.jar') -Force
    $manifest = New-GateYReadonlyReleaseManifest $SourceCommit '2026-08-19T00:00:00Z' $artifacts $MigrationRoot
    Write-GateYReadonlyCanonicalManifest (Join-Path $Root 'release-manifest.json') $manifest
    return $manifest
}

function Expect-Blocked([scriptblock]$Action, [string]$Decision)
{
    try
    {
        & $Action
        throw "expected decision not raised: $Decision"
    }
    catch
    {
        if ($_.Exception.Message -cne $Decision) { throw }
    }
}

function Write-TestJson([string]$Path, $Value)
{
    Write-TestText $Path (ConvertTo-GateYReadonlyCanonicalManifestJson $Value)
}

function New-AuditEvidenceFixtures($Manifest, [string]$ManifestSha256, [string]$EvidenceRoot)
{
    $flywayObservation = [pscustomobject][ordered]@{
        schemaVersion = 'gatey-disposable-flyway-observation.v1'
        databaseIdentity = 'sanitized:test-db'
        targetSchemaVersion = 'V2'
        appliedFlywayVersions = @('V1')
        observedAt = '2026-08-19T00:01:00Z'
    }
    $backupArtifact = [pscustomobject][ordered]@{
        schemaVersion = 'gatey-disposable-backup.v1'
        databaseIdentity = 'sanitized:test-db'
        flywaySourceVersion = 'V1'
        releaseManifestSha256 = $ManifestSha256
        createdAt = '2026-08-19T00:03:00Z'
        backupTool = 'pg_dump'
        backupToolVersion = '17'
        payload = [pscustomobject][ordered]@{
            tables = @('flyway_schema_history', 'live_session')
            rows = 2
        }
    }
    $flywayPath = Join-Path $EvidenceRoot 'flyway-history-observation.json'
    $backupPath = Join-Path $EvidenceRoot 'backup-artifact.json'
    Write-TestJson $flywayPath $flywayObservation
    Write-TestJson $backupPath $backupArtifact
    $receipts = [pscustomobject][ordered]@{
        flyway = Test-GateYFlywayHistoryObservation $Manifest $ManifestSha256 $flywayPath
        compatibility = Test-GateYReleaseSchemaCompatibility $Manifest $ManifestSha256
        backup = $null
        restore = $null
        backupPath = $backupPath
    }
    $receipts.backup = Test-GateYBackupArtifact `
        $ManifestSha256 'V1' 'sanitized:test-db' $backupPath
    $receipts.restore = Test-GateYRestoreEvidence `
        $ManifestSha256 $receipts.backup 'V1' $backupPath
    Write-GateYAuditEvidence (Join-Path $EvidenceRoot 'flyway-history-audit.json') $receipts.flyway
    Write-GateYAuditEvidence (Join-Path $EvidenceRoot 'compatibility-audit.json') $receipts.compatibility
    Write-GateYAuditEvidence (Join-Path $EvidenceRoot 'backup-verification-audit.json') $receipts.backup
    Write-GateYAuditEvidence (Join-Path $EvidenceRoot 'restore-verification-audit.json') $receipts.restore
    return $receipts
}

function New-ForgedReceipt([string]$ReceiptType, [string]$ProofType, $Facts)
{
    $producer = @{
        FLYWAY_HISTORY = 'nq-gatey-flyway-history-verifier'
        BACKUP_VERIFICATION = 'nq-gatey-backup-verifier'
        RESTORE_VERIFICATION = 'nq-gatey-restore-verifier'
        COMPATIBILITY = 'nq-gatey-compatibility-verifier'
        HEALTH = 'nq-gatey-post-activation-health-verifier'
    }[$ReceiptType]
    $value = [ordered]@{
        schemaVersion = 'gatey-deployment-receipt.v1'
        receiptType = $ReceiptType
        producerIdentity = $producer
        producerVersion = '2'
        verificationResult = 'VERIFIED'
        verifiedAt = '2026-08-19T00:10:00Z'
        proofType = $ProofType
    }
    foreach ($property in @($Facts.PSObject.Properties))
    {
        $value[$property.Name] = $property.Value
    }
    $unsigned = [pscustomobject]$value
    $value.receiptSha256 = Get-GateYReadonlySha256Text `
        (ConvertTo-GateYReadonlyCanonicalManifestJson $unsigned)
    return [pscustomobject]$value
}

function Get-ReceiptObservedFacts($Receipt)
{
    $facts = [ordered]@{}
    foreach ($property in @($Receipt.PSObject.Properties))
    {
        if ($property.Name -notin @(
            'schemaVersion', 'receiptType', 'producerIdentity', 'producerVersion',
            'verificationResult', 'verifiedAt', 'proofType', 'receiptSha256'
        ))
        {
            $facts[$property.Name] = $property.Value
        }
    }
    return [pscustomobject]$facts
}

function New-ReleaseHardLink([string]$Outside, [string]$Inside)
{
    Remove-Item -LiteralPath $Inside -Force
    if ($env:OS -ceq 'Windows_NT')
    {
        New-Item -ItemType HardLink -Path $Inside -Target $Outside | Out-Null
    }
    else
    {
        & /usr/bin/ln -- $Outside $Inside
        if ($LASTEXITCODE -ne 0) { throw 'HARDLINK_FIXTURE_FAILED' }
    }
}

$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('nq-gatey-readonly-contract-' + [Guid]::NewGuid().ToString('N'))
try
{
    [IO.Directory]::CreateDirectory($tempRoot) | Out-Null
    $migrations = Join-Path $tempRoot 'migrations'
    Write-TestText (Join-Path $migrations 'V1__first.sql') ('SELECT 1;' + [char]10)
    Write-TestText (Join-Path $migrations 'V2__second.sql') ('SELECT 2;' + [char]10)

    $inventory = Get-GateYMigrationInventory $migrations
    Assert-Condition ($inventory.targetVersion -ceq 'V2' -and $inventory.migrationRange -ceq 'V1..V2') 'MIGRATION_INVENTORY_INVALID'
    Complete-Case 'migration-inventory-derived'

    $releaseA = Join-Path $tempRoot 'release-a'
    $releaseB = Join-Path $tempRoot 'release-b'
    $manifestA = New-TestRelease $releaseA $migrations
    $manifestB = New-TestRelease $releaseB $migrations
    Assert-Condition ((ConvertTo-GateYReadonlyCanonicalManifestJson $manifestA) -ceq (ConvertTo-GateYReadonlyCanonicalManifestJson $manifestB)) 'CANONICAL_MANIFEST_NOT_DETERMINISTIC'
    Complete-Case 'canonical-manifest-deterministic'
    $forbiddenRuntimeFacts = @(
        'startupCredentialReads', 'startupOkxGetCalls', 'startupOkxPostCalls',
        'runtimeHealthy', 'killSwitchObserved', 'databaseConnected'
    )
    Assert-Condition (
        [string]$manifestA.safety.factClassification -ceq 'EXPECTED_CONFIGURATION' -and
        @($forbiddenRuntimeFacts | Where-Object {
            $null -ne $manifestA.PSObject.Properties[$_] -or
            $null -ne $manifestA.safety.PSObject.Properties[$_]
        }).Count -eq 0
    ) 'RELEASE_MANIFEST_RUNTIME_FACT_BOUNDARY_INVALID'
    Complete-Case 'release-manifest-cannot-assert-runtime-counter-zero'

    $verified = Test-GateYReadonlyRelease $releaseA
    Assert-Condition ($verified.artifactCount -eq 15 -and $verified.linkIntegrityVerified) 'RELEASE_VERIFICATION_FAILED'
    Complete-Case 'independent-regular-file-pass'

    $legacyRelease = Join-Path $tempRoot 'legacy-release'
    Copy-Item -LiteralPath $releaseA -Destination $legacyRelease -Recurse
    $legacyManifestPath = Join-Path $legacyRelease 'release-manifest.json'
    $legacyManifest = Get-Content -LiteralPath $legacyManifestPath -Raw | ConvertFrom-Json
    $legacyManifest.artifacts = @($legacyManifest.artifacts | Where-Object {
        [string]$_.relativePath -notin @(
            'bin/invoke-gatey-exact-pilot-scope.ps1',
            'bin/invoke-gatey-minimal-live-pilot.ps1'
        )
    })
    Remove-Item -LiteralPath (Join-Path $legacyRelease 'bin/invoke-gatey-exact-pilot-scope.ps1') -Force
    Remove-Item -LiteralPath (Join-Path $legacyRelease 'bin/invoke-gatey-minimal-live-pilot.ps1') -Force
    Write-GateYReadonlyCanonicalManifest $legacyManifestPath $legacyManifest
    Expect-Blocked { Test-GateYReadonlyRelease $legacyRelease } `
        'BLOCKED / RELEASE_REQUIRED_ARTIFACT_MISSING'
    $legacyVerified = Test-GateYReadonlyRelease $legacyRelease `
        -AllowLegacyExactPilotControlSurfaceAbsent
    Assert-Condition ($legacyVerified.artifactCount -eq 13) 'LEGACY_RELEASE_VERIFICATION_FAILED'
    Complete-Case 'previous-legacy-exact-pilot-surface-absence-explicitly-accepted'

    $incompleteLegacyRelease = Join-Path $tempRoot 'incomplete-legacy-release'
    Copy-Item -LiteralPath $legacyRelease -Destination $incompleteLegacyRelease -Recurse
    $incompleteManifestPath = Join-Path $incompleteLegacyRelease 'release-manifest.json'
    $incompleteManifest = Get-Content -LiteralPath $incompleteManifestPath -Raw | ConvertFrom-Json
    $incompleteManifest.artifacts = @($incompleteManifest.artifacts | Where-Object {
        [string]$_.relativePath -cne 'bin/install-gatey-readonly-release.ps1'
    })
    Remove-Item -LiteralPath (Join-Path $incompleteLegacyRelease 'bin/install-gatey-readonly-release.ps1') -Force
    Write-GateYReadonlyCanonicalManifest $incompleteManifestPath $incompleteManifest
    Expect-Blocked {
        Test-GateYReadonlyRelease $incompleteLegacyRelease `
            -AllowLegacyExactPilotControlSurfaceAbsent
    } 'BLOCKED / RELEASE_REQUIRED_ARTIFACT_MISSING'
    Complete-Case 'previous-legacy-other-required-artifact-still-rejected'

    $forgedRuntimeRelease = Join-Path $tempRoot 'forged-runtime-release'
    Copy-Item -LiteralPath $releaseA -Destination $forgedRuntimeRelease -Recurse
    $forgedRuntimeManifestPath = Join-Path $forgedRuntimeRelease 'release-manifest.json'
    $forgedRuntimeManifest = Get-Content -LiteralPath $forgedRuntimeManifestPath -Raw | ConvertFrom-Json
    $forgedRuntimeManifest.safety | Add-Member -NotePropertyName startupCredentialReads `
        -NotePropertyValue 0
    Write-GateYReadonlyCanonicalManifest $forgedRuntimeManifestPath $forgedRuntimeManifest
    Expect-Blocked { Test-GateYReadonlyRelease $forgedRuntimeRelease } `
        'BLOCKED / RELEASE_MANIFEST_RUNTIME_FACT_INVALID'
    Complete-Case 'caller-runtime-zero-field-rejected'

    [IO.File]::AppendAllText((Join-Path $releaseB 'app/nq-app.jar'), 'tamper', $script:Utf8NoBom)
    Expect-Blocked { Test-GateYReadonlyRelease $releaseB } 'BLOCKED / RELEASE_ARTIFACT_HASH_MISMATCH'
    Complete-Case 'artifact-tamper-rejected'

    $hardlinkRelease = Join-Path $tempRoot 'hardlink-release'
    $null = New-TestRelease $hardlinkRelease $migrations
    $outside = Join-Path $tempRoot 'outside.jar'
    Write-TestText $outside 'deterministic-application-artifact'
    New-ReleaseHardLink $outside (Join-Path $hardlinkRelease 'app/nq-app.jar')
    Expect-Blocked { Test-GateYReadonlyRelease $hardlinkRelease } 'BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION'
    Complete-Case 'external-hardlink-rejected'

    $parentLinkRelease = Join-Path $tempRoot 'parent-link-release'
    $null = New-TestRelease $parentLinkRelease $migrations
    $outsideApp = Join-Path $tempRoot 'outside-app'
    [IO.Directory]::CreateDirectory($outsideApp) | Out-Null
    Copy-Item -LiteralPath (Join-Path $parentLinkRelease 'app/nq-app.jar') -Destination (Join-Path $outsideApp 'nq-app.jar')
    Remove-Item -LiteralPath (Join-Path $parentLinkRelease 'app') -Recurse -Force
    if ($env:OS -ceq 'Windows_NT')
    {
        New-Item -ItemType Junction -Path (Join-Path $parentLinkRelease 'app') -Target $outsideApp | Out-Null
    }
    else
    {
        & /usr/bin/ln -s -- $outsideApp (Join-Path $parentLinkRelease 'app')
        if ($LASTEXITCODE -ne 0) { throw 'PARENT_LINK_FIXTURE_FAILED' }
    }
    Expect-Blocked { Test-GateYReadonlyRelease $parentLinkRelease } 'BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION'
    Complete-Case 'parent-link-traversal-rejected'

    $plan = Get-GateYMigrationPlan $manifestA @('V1')
    Assert-Condition ($plan.currentVersion -ceq 'V1' -and $plan.pendingVersions[0] -ceq 'V2') 'MIGRATION_PLAN_INVALID'
    Complete-Case 'pending-migration-derived-from-history'
    Expect-Blocked { Get-GateYMigrationPlan $manifestA @('V2') } 'BLOCKED / FLYWAY_HISTORY_DIVERGED'
    Complete-Case 'diverged-history-rejected'

    $evidenceRoot = Join-Path $tempRoot 'evidence'
    [IO.Directory]::CreateDirectory($evidenceRoot) | Out-Null
    $receipts = New-AuditEvidenceFixtures $manifestA $verified.manifestSha256 $evidenceRoot
    $flyway = $receipts.flyway
    $compatibility = $receipts.compatibility
    $backup = $receipts.backup
    $restore = $receipts.restore
    Assert-Condition (
        [string]$compatibility.compatibilityDecision -ceq 'UNKNOWN' -and
        [string]$backup.evidenceRole -ceq 'AUDIT_EVIDENCE_ONLY' -and
        [string]$restore.evidenceRole -ceq 'AUDIT_EVIDENCE_ONLY' -and
        -not [bool]$backup.authorizationEligible -and
        -not [bool]$restore.authorizationEligible
    ) 'AUDIT_EVIDENCE_INVALID'
    Complete-Case 'public-verifier-returns-audit-evidence-only'

    $serializedBackup = Read-GateYAuditEvidence `
        (Join-Path $evidenceRoot 'backup-verification-audit.json') BACKUP_VERIFICATION
    Assert-Condition (-not [bool]$serializedBackup.authorizationEligible) 'SERIALIZED_AUDIT_BECAME_TRUSTED'
    Complete-Case 'serialized-audit-evidence-remains-untrusted'

    Expect-Blocked {
        Assert-GateYRollbackContract $manifestA $verified.manifestSha256 $flyway $compatibility $backup $restore
    } 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
    Complete-Case 'public-verifier-chain-cannot-mint-authorization'

    $forgedCompatible = New-ForgedReceipt COMPATIBILITY CALLER_ASSERTION ([pscustomobject]@{})
    Expect-Blocked {
        Assert-GateYRollbackContract $manifestA $verified.manifestSha256 $forgedCompatible
    } 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
    Complete-Case 'caller-compatibility-assertion-rejected'

    $forgedBackup = New-ForgedReceipt BACKUP_VERIFICATION CALLER_METADATA ([pscustomobject]@{})
    Expect-Blocked {
        Assert-GateYRollbackContract $manifestA $verified.manifestSha256 $forgedBackup
    } 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
    Complete-Case 'caller-backup-restore-chain-rejected'

    $health = Test-GateYPostActivationHealth `
        $manifestA $verified.manifestSha256 $releaseA (Join-Path $tempRoot 'missing-current')
    Assert-Condition (
        [string]$health.evidenceRole -ceq 'AUDIT_EVIDENCE_ONLY' -and
        [string]$health.observationResult -ceq 'NOT_VERIFIED'
    ) 'HEALTH_AUDIT_EVIDENCE_INVALID'
    Expect-Blocked {
        Assert-GateYHealthReceipt $health $manifestA $verified.manifestSha256
    } 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
    Complete-Case 'caller-health-assertion-rejected'

    $engine = (Get-Process -Id $PID).Path
    $deploymentOutput = @(& $engine -NoProfile -File $deployment -ReleaseRoot $releaseA -EvidenceRoot $evidenceRoot -Phase PRE_DEPLOYMENT 2>&1)
    Assert-Condition ($LASTEXITCODE -eq 2) 'SYNTHETIC_DEPLOYMENT_CONTRACT_MUST_BLOCK'
    $deploymentResult = ($deploymentOutput -join [Environment]::NewLine) | ConvertFrom-Json
    Assert-Condition (
        [string]$deploymentResult.contractState -ceq 'BLOCKED' -and
        [string]$deploymentResult.decision -cne 'PASS / GATEY_READONLY_PRE_DEPLOYMENT_READY'
    ) 'SYNTHETIC_DEPLOYMENT_REACHED_AUTHORIZATION'
    Complete-Case 'synthetic-deployment-cannot-reach-pre-deployment-ready'

    $deploymentSource = Get-Content -LiteralPath $deployment -Raw
    foreach ($forbiddenMarker in @(
        'Test-GateYFlywayHistoryObservation', 'Test-GateYBackupArtifact',
        'Test-GateYRestoreEvidence', 'Assert-GateYHealthReceipt', 'receiptSha256'
    ))
    {
        Assert-Condition (-not $deploymentSource.Contains($forbiddenMarker)) "CALLER_EVIDENCE_STILL_AUTHORIZES:$forbiddenMarker"
    }
    Complete-Case 'deployment-evaluator-does-not-consume-caller-evidence'

    $auditClone = ($backup | ConvertTo-Json -Depth 8 | ConvertFrom-Json)
    Expect-Blocked {
        Assert-GateYRollbackContract $manifestA $verified.manifestSha256 $auditClone
    } 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
    Complete-Case 'audit-evidence-clone-cannot-authorize'

    $staleAuditSha256 = [string]$backup.auditSha256
    $staleArtifact = Get-Content -LiteralPath $receipts.backupPath -Raw | ConvertFrom-Json
    $staleArtifact.payload.rows = 99
    Write-TestJson $receipts.backupPath $staleArtifact
    $freshAssessment = Invoke-GateYSyntheticRollbackAssessment `
        $manifestA $verified.manifestSha256 (Join-Path $evidenceRoot 'flyway-history-observation.json') $receipts.backupPath
    $freshBackup = @($freshAssessment.auditEvidence | Where-Object { $_.evidenceType -ceq 'BACKUP_VERIFICATION' })[0]
    Assert-Condition (
        [string]$freshBackup.auditSha256 -cne $staleAuditSha256 -and
        -not [bool]$freshAssessment.authorizationEligible -and
        -not [bool]$freshAssessment.deploymentAcceptance
    ) 'STALE_AUDIT_WAS_NOT_REVERIFIED'
    Expect-Blocked {
        Assert-GateYRollbackContract $manifestA $verified.manifestSha256 $backup
    } 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
    Complete-Case 'stale-audit-evidence-cannot-authorize'

    $postOutput = @(& $engine -NoProfile -File $deployment -ReleaseRoot $releaseA -EvidenceRoot $evidenceRoot -Phase POST_ACTIVATION 2>&1)
    Assert-Condition ($LASTEXITCODE -eq 2) 'FORGED_POST_ACTIVATION_MUST_BLOCK'
    $postResult = ($postOutput -join [Environment]::NewLine) | ConvertFrom-Json
    Assert-Condition (
        [string]$postResult.contractState -ceq 'BLOCKED' -and
        [string]$postResult.decision -cne 'PASS / GATEY_READONLY_POST_ACTIVATION_ACCEPTED'
    ) 'FORGED_HEALTH_REACHED_POST_ACTIVATION'
    Complete-Case 'caller-health-cannot-accept-post-activation'

    $otherReleaseRoot = Join-Path $tempRoot 'other-release'
    $otherManifest = New-TestRelease `
        $otherReleaseRoot $migrations '2222222222222222222222222222222222222222'
    $otherVerified = Test-GateYReadonlyRelease $otherReleaseRoot
    $otherReleaseCompatibility = Test-GateYReleaseSchemaCompatibility `
        $otherManifest $otherVerified.manifestSha256
    Expect-Blocked {
        Assert-GateYRollbackContract $manifestA $verified.manifestSha256 $otherReleaseCompatibility
    } 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
    Complete-Case 'cross-release-audit-evidence-cannot-authorize'

    $otherMigrations = Join-Path $tempRoot 'other-migrations'
    Write-TestText (Join-Path $otherMigrations 'V1__first.sql') ('SELECT 1;' + [char]10)
    Write-TestText (Join-Path $otherMigrations 'V2__second.sql') ('SELECT 2;' + [char]10)
    Write-TestText (Join-Path $otherMigrations 'V3__third.sql') ('SELECT 3;' + [char]10)
    $otherSchemaRoot = Join-Path $tempRoot 'other-schema-release'
    $otherSchemaManifest = New-TestRelease `
        $otherSchemaRoot $otherMigrations '3333333333333333333333333333333333333333'
    $otherSchemaVerified = Test-GateYReadonlyRelease $otherSchemaRoot
    $otherSchemaObservationPath = Join-Path $tempRoot 'other-schema-observation.json'
    Write-TestJson $otherSchemaObservationPath ([pscustomobject][ordered]@{
        schemaVersion = 'gatey-disposable-flyway-observation.v1'
        databaseIdentity = 'sanitized:test-db'
        targetSchemaVersion = 'V3'
        appliedFlywayVersions = @('V1')
        observedAt = '2026-08-19T00:11:00Z'
    })
    $otherSchemaFlyway = Test-GateYFlywayHistoryObservation `
        $otherSchemaManifest $otherSchemaVerified.manifestSha256 $otherSchemaObservationPath
    Expect-Blocked {
        Assert-GateYRollbackContract $manifestA $verified.manifestSha256 $otherSchemaFlyway
    } 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
    Complete-Case 'cross-schema-audit-evidence-cannot-authorize'

    $secondBackupPath = Join-Path $tempRoot 'second-backup-artifact.json'
    $secondBackupArtifact = Get-Content -LiteralPath $receipts.backupPath -Raw | ConvertFrom-Json
    $secondBackupArtifact.payload.rows = 3
    Write-TestJson $secondBackupPath $secondBackupArtifact
    $secondBackup = Test-GateYBackupArtifact `
        $verified.manifestSha256 'V1' 'sanitized:test-db' $secondBackupPath
    $secondRestore = Test-GateYRestoreEvidence `
        $verified.manifestSha256 $secondBackup 'V1' $secondBackupPath
    Expect-Blocked {
        Assert-GateYRollbackContract $manifestA $verified.manifestSha256 $secondRestore
    } 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
    Complete-Case 'restore-audit-evidence-cannot-authorize'

    $digestTamperedCompatibility = Test-GateYReleaseSchemaCompatibility `
        $manifestA $verified.manifestSha256
    $digestTamperedCompatibility.auditSha256 = ('f' * 64)
    Expect-Blocked {
        Assert-GateYRollbackContract $manifestA $verified.manifestSha256 $digestTamperedCompatibility
    } 'BLOCKED / RECEIPT_AUDIT_EVIDENCE_CANNOT_AUTHORIZE'
    Complete-Case 'modified-audit-evidence-cannot-authorize'

    $verifiedSyntheticChain = Invoke-GateYSyntheticRollbackAssessment `
        $manifestA $verified.manifestSha256 (Join-Path $evidenceRoot 'flyway-history-observation.json') $secondBackupPath
    Assert-Condition (
        [string]$verifiedSyntheticChain.databaseRecovery -ceq 'SYNTHETIC_VERIFIED_BACKUP_AND_RESTORE' -and
        -not [bool]$verifiedSyntheticChain.authorizationEligible -and
        -not [bool]$verifiedSyntheticChain.deploymentAcceptance
    ) 'VERIFIER_SYNTHETIC_CHAIN_NOT_ACCEPTED'
    Complete-Case 'normal-synthetic-evaluator-path-is-audit-only'

    $builderSource = Get-Content -LiteralPath $builder -Raw
    foreach ($marker in @(
        'EXACT_COMMIT_WORKTREE_NOT_CLEAN', 'JAVA_MAJOR_VERSION_MISMATCH',
        'New-ExactCommitSourceMaterialization', 'Assert-ExactCommitMaterialization',
        'EXACT_GIT_COMMIT_BLOB_BYTES', 'Write-ExactCommitBlobTree',
        "'cat-file', '--batch'", 'Copy-GitBatchBlob',
        'RELEASE_SOURCE_BLOB_HASH_MISMATCH', 'RELEASE_SOURCE_BLOB_PROCESS_FAILED',
        'RELEASE_SOURCE_FILE_COUNT_MISMATCH', 'Invoke-ExactSourceApplicationBuild',
        '-Dproject.build.outputTimestamp=', 'Assert-ApplicationJarContract',
        'RELEASE_APPLICATION_MIGRATION_MISMATCH', 'BUILT_VERIFIED',
        'installationRequired = $true'
    ))
    {
        Assert-Condition $builderSource.Contains($marker) "BUILDER_MARKER_MISSING:$marker"
    }
    Assert-Condition (
        $builderSource.Contains('$mavenOutput = @(& $maven @arguments)') -and
        $builderSource -notmatch '(?m)^\s*& \$maven @arguments\s*$'
    ) 'BUILDER_MAVEN_OUTPUT_LEAKS_TO_RETURN_VALUE'
    Complete-Case 'builder-exact-clean-contract'

    $builderSelfTestOutput = @(& $engine -NoProfile -File $builder -ContractSelfTest 2>&1)
    Assert-Condition ($LASTEXITCODE -eq 0) 'BUILDER_SELF_TEST_PROCESS_FAILED'
    $builderSelfTest = ($builderSelfTestOutput -join [Environment]::NewLine) | ConvertFrom-Json
    Assert-Condition (
        [int]$builderSelfTest.migrationCount -eq 43 -and
        [bool]$builderSelfTest.tamperedMigrationRejected -and
        [bool]$builderSelfTest.canonicalMaterializationVerified -and
        [int]$builderSelfTest.trackedFiles -gt 0 -and
        [string]$builderSelfTest.sourceMode -ceq 'EXACT_GIT_COMMIT_BLOB_BYTES'
    ) 'BUILDER_SELF_TEST_RESULT_INVALID'
    Complete-Case 'builder-fat-jar-migration-binding'

    foreach ($forbidden in @('systemctl start', 'Invoke-WebRequest', 'Invoke-RestMethod', 'ssh ', 'psql '))
    {
        Assert-Condition ($deploymentSource.IndexOf($forbidden, [StringComparison]::OrdinalIgnoreCase) -lt 0) "DEPLOYMENT_SIDE_EFFECT_PRESENT:$forbidden"
    }
    Complete-Case 'deployment-contract-no-server-write'

    Assert-Condition ($script:Cases.Count -eq 31) "CASE_COUNT_INVALID:$($script:Cases.Count)"
    [pscustomobject][ordered]@{
        decision = 'PASS / GATEY_READONLY_RELEASE_CONTRACT_REGRESSION'
        cases = $script:Cases.Count
        manifestSha256 = $verified.manifestSha256
        schemaTarget = $verified.schemaTarget
        credentialAccessed = $false
        networkCalled = $false
        serverMutation = $false
        results = @($script:Cases)
    } | ConvertTo-Json -Depth 6
}
catch
{
    [pscustomobject][ordered]@{
        decision = 'FAIL / GATEY_READONLY_RELEASE_CONTRACT_REGRESSION'
        casesPassed = $script:Cases.Count
        detail = $_.Exception.Message
    } | ConvertTo-Json -Depth 4
    exit 2
}
finally
{
    if (Test-Path -LiteralPath $tempRoot)
    {
        $tempBase = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        $resolved = [IO.Path]::GetFullPath($tempRoot)
        if (-not $resolved.StartsWith($tempBase, [StringComparison]::OrdinalIgnoreCase))
        {
            throw 'TEMP_CLEANUP_PATH_INVALID'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
