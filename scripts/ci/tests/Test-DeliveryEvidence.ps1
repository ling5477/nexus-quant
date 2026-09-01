[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$artifactRoot = Join-Path $repo 'artifacts'
New-Item -ItemType Directory -Path $artifactRoot -Force | Out-Null
$runRoot = Join-Path $artifactRoot ('phase5a-delivery-evidence-test-' + [Guid]::NewGuid().ToString('N'))
$scripts = Join-Path $repo 'scripts\ci'

function Assert-Condition([bool] $Condition, [string] $Message) {
    if (-not $Condition) { throw $Message }
}

function Write-Json([string] $Path, [object] $Value) {
    $json = ($Value | ConvertTo-Json -Depth 40).Replace("`r`n", "`n").TrimEnd() + "`n"
    [IO.File]::WriteAllText($Path, $json, (New-Object Text.UTF8Encoding($false)))
}

function Assert-Rejected([string] $Name, [scriptblock] $Command) {
    $rejected = $false
    try { & $Command }
    catch { $rejected = $true }
    Assert-Condition $rejected "Negative provenance case unexpectedly passed: $Name"
    Write-Output "PROVENANCE_NEGATIVE_REJECTED=$Name"
}

try {
    $backendRoot = Join-Path $runRoot 'backend'
    $frontendRoot = Join-Path $runRoot 'frontend'
    New-Item -ItemType Directory -Path (Join-Path $backendRoot 'artifacts') -Force | Out-Null
    New-Item -ItemType Directory -Path (Join-Path $frontendRoot 'artifacts/dist') -Force | Out-Null
    [IO.File]::WriteAllText((Join-Path $backendRoot 'artifacts/nq-app.bin'), 'backend-fixture')
    [IO.File]::WriteAllText((Join-Path $frontendRoot 'artifacts/dist/index.html'), '<html>fixture</html>')

    $rawTemplate = @'
{
  "bomFormat": "CycloneDX",
  "specVersion": "1.5",
  "serialNumber": "SERIAL_SENTINEL",
  "metadata": {"timestamp": "TIMESTAMP_SENTINEL"},
  "components": [
    {"type":"library","name":"zeta","version":"1"},
    {"type":"library","name":"alpha","version":"1"}
  ],
  "dependencies": []
}
'@
    $rawOne = Join-Path $runRoot 'raw-one.json'
    $rawTwo = Join-Path $runRoot 'raw-two.json'
    [IO.File]::WriteAllText($rawOne, $rawTemplate.Replace('SERIAL_SENTINEL','one').Replace('TIMESTAMP_SENTINEL','one'))
    [IO.File]::WriteAllText($rawTwo, $rawTemplate.Replace('SERIAL_SENTINEL','two').Replace('TIMESTAMP_SENTINEL','two'))
    $normalizedOne = Join-Path $backendRoot 'backend-sbom.cdx.json'
    $normalizedTwo = Join-Path $runRoot 'normalized-two.json'
    & (Join-Path $scripts 'Normalize-CycloneDxSbom.ps1') -InputPath $rawOne -OutputPath $normalizedOne | Out-Null
    & (Join-Path $scripts 'Normalize-CycloneDxSbom.ps1') -InputPath $rawTwo -OutputPath $normalizedTwo | Out-Null
    Assert-Condition ((Get-FileHash $normalizedOne).Hash -ceq (Get-FileHash $normalizedTwo).Hash) 'Normalized SBOM is not deterministic'
    Copy-Item -LiteralPath $normalizedOne -Destination (Join-Path $frontendRoot 'frontend-sbom.cdx.json')

    $backendManifest = Join-Path $backendRoot 'backend-artifact-manifest.json'
    $frontendManifest = Join-Path $frontendRoot 'frontend-artifact-manifest.json'
    & (Join-Path $scripts 'New-DeliveryArtifactManifest.ps1') -ArtifactPath (Join-Path $backendRoot 'artifacts') -RepositoryRoot $backendRoot -ArtifactSetName 'backend-application' -OutputPath $backendManifest | Out-Null
    & (Join-Path $scripts 'New-DeliveryArtifactManifest.ps1') -ArtifactPath (Join-Path $frontendRoot 'artifacts') -RepositoryRoot $frontendRoot -ArtifactSetName 'frontend-production-dist' -OutputPath $frontendManifest | Out-Null
    & (Join-Path $scripts 'Test-DeliveryArtifactSafety.ps1') -EvidenceRoot $backendRoot | Out-Null
    & (Join-Path $scripts 'Test-DeliveryArtifactSafety.ps1') -EvidenceRoot $frontendRoot | Out-Null

    $workflowFixture = Join-Path $runRoot 'workflow.yml'
    $supplyChainFixture = Join-Path $runRoot 'supply-chain-lock.json'
    [IO.File]::WriteAllText($workflowFixture, "name: fixture`n")
    [IO.File]::WriteAllText($supplyChainFixture, "{`"schemaVersion`":`"fixture`"}`n")
    $provenanceOne = Join-Path $runRoot 'provenance-one.json'
    $provenanceTwo = Join-Path $runRoot 'provenance-two.json'
    $arguments = @{
        SourceCommit = '0123456789abcdef0123456789abcdef01234567'
        CiRunId = '12345'
        CiRunAttempt = '1'
        Repository = 'example/nexus-quant'
        Ref = 'refs/heads/test'
        WorkflowName = 'NQ CI Baseline'
        WorkflowPath = '.github/workflows/ci.yml'
        WorkflowFilePath = $workflowFixture
        SupplyChainLockPath = $supplyChainFixture
        JavaVersion = '21'
        NodeVersion = '22'
        NpmVersion = '11'
        BackendEvidenceRoot = $backendRoot
        FrontendEvidenceRoot = $frontendRoot
        BackendSbomPath = $normalizedOne
        FrontendSbomPath = (Join-Path $frontendRoot 'frontend-sbom.cdx.json')
        BackendArtifactManifestPath = $backendManifest
        FrontendArtifactManifestPath = $frontendManifest
    }
    & (Join-Path $scripts 'New-InternalProvenance.ps1') @arguments -OutputPath $provenanceOne | Out-Null
    & (Join-Path $scripts 'New-InternalProvenance.ps1') @arguments -OutputPath $provenanceTwo | Out-Null
    Assert-Condition ((Get-FileHash $provenanceOne).Hash -ceq (Get-FileHash $provenanceTwo).Hash) 'Internal provenance is not deterministic'

    $provenanceValidator = Join-Path $scripts 'Test-InternalProvenance.ps1'
    $validatorArguments = @{
        ExpectedSourceCommit = $arguments.SourceCommit
        ExpectedCiRunId = $arguments.CiRunId
        ExpectedCiRunAttempt = $arguments.CiRunAttempt
        ExpectedRepository = $arguments.Repository
        ExpectedRef = $arguments.Ref
        ExpectedWorkflowName = $arguments.WorkflowName
        ExpectedWorkflowPath = $arguments.WorkflowPath
        ExpectedJavaVersion = $arguments.JavaVersion
        ExpectedNodeVersion = $arguments.NodeVersion
        ExpectedNpmVersion = $arguments.NpmVersion
        WorkflowFilePath = $arguments.WorkflowFilePath
        SupplyChainLockPath = $arguments.SupplyChainLockPath
        BackendEvidenceRoot = $arguments.BackendEvidenceRoot
        FrontendEvidenceRoot = $arguments.FrontendEvidenceRoot
        BackendSbomPath = $arguments.BackendSbomPath
        FrontendSbomPath = $arguments.FrontendSbomPath
        BackendArtifactManifestPath = $arguments.BackendArtifactManifestPath
        FrontendArtifactManifestPath = $arguments.FrontendArtifactManifestPath
    }
    & $provenanceValidator @validatorArguments -ProvenancePath $provenanceOne | Out-Null
    & $provenanceValidator @validatorArguments -ProvenancePath $provenanceTwo | Out-Null

    Assert-Rejected 'wrong-commit' {
        $wrong = $validatorArguments.Clone()
        $wrong.ExpectedSourceCommit = 'ffffffffffffffffffffffffffffffffffffffff'
        & $provenanceValidator @wrong -ProvenancePath $provenanceOne | Out-Null
    }
    Assert-Rejected 'wrong-run-id' {
        $wrong = $validatorArguments.Clone()
        $wrong.ExpectedCiRunId = '99999'
        & $provenanceValidator @wrong -ProvenancePath $provenanceOne | Out-Null
    }
    Assert-Rejected 'missing-provenance' {
        & $provenanceValidator @validatorArguments -ProvenancePath (Join-Path $runRoot 'missing-provenance.json') | Out-Null
    }
    Assert-Rejected 'missing-sbom' {
        $missing = $validatorArguments.Clone()
        $missing.BackendSbomPath = Join-Path $runRoot 'missing-backend-sbom.json'
        & $provenanceValidator @missing -ProvenancePath $provenanceOne | Out-Null
    }

    $backendSbomBytes = [IO.File]::ReadAllBytes($normalizedOne)
    try {
        Add-Content -LiteralPath $normalizedOne -Value ' '
        Assert-Rejected 'tampered-sbom' {
            & $provenanceValidator @validatorArguments -ProvenancePath $provenanceOne | Out-Null
        }
    } finally {
        [IO.File]::WriteAllBytes($normalizedOne, $backendSbomBytes)
    }

    $backendManifestBytes = [IO.File]::ReadAllBytes($backendManifest)
    try {
        $tamperedManifest = Get-Content -Raw -LiteralPath $backendManifest | ConvertFrom-Json
        $tamperedManifest.aggregateSha256 = ('0' * 64)
        Write-Json $backendManifest $tamperedManifest
        Assert-Rejected 'tampered-manifest' {
            & $provenanceValidator @validatorArguments -ProvenancePath $provenanceOne | Out-Null
        }
    } finally {
        [IO.File]::WriteAllBytes($backendManifest, $backendManifestBytes)
    }

    $backendArtifact = Join-Path $backendRoot 'artifacts/nq-app.bin'
    $backendArtifactBytes = [IO.File]::ReadAllBytes($backendArtifact)
    try {
        Add-Content -LiteralPath $backendArtifact -Value 'readback-tamper'
        Assert-Rejected 'tampered-artifact-digest' {
            & $provenanceValidator @validatorArguments -ProvenancePath $provenanceOne | Out-Null
        }
    } finally {
        [IO.File]::WriteAllBytes($backendArtifact, $backendArtifactBytes)
    }

    foreach ($caseName in @('tampered-backend-digest', 'tampered-frontend-digest')) {
        $variantPath = Join-Path $runRoot "$caseName.json"
        $variant = Get-Content -Raw -LiteralPath $provenanceOne | ConvertFrom-Json
        $subjectName = if ($caseName -eq 'tampered-backend-digest') { 'backend-application' } else { 'frontend-production-dist' }
        ($variant.subjects | Where-Object name -eq $subjectName).sha256 = ('0' * 64)
        Write-Json $variantPath $variant
        Assert-Rejected $caseName {
            & $provenanceValidator @validatorArguments -ProvenancePath $variantPath | Out-Null
        }
    }

    $missingFieldPath = Join-Path $runRoot 'missing-field.json'
    $missingField = Get-Content -Raw -LiteralPath $provenanceOne | ConvertFrom-Json
    $missingField.ci.PSObject.Properties.Remove('runAttempt')
    Write-Json $missingFieldPath $missingField
    Assert-Rejected 'missing-required-field' {
        & $provenanceValidator @validatorArguments -ProvenancePath $missingFieldPath | Out-Null
    }

    $wrongSchemaPath = Join-Path $runRoot 'wrong-schema.json'
    $wrongSchema = Get-Content -Raw -LiteralPath $provenanceOne | ConvertFrom-Json
    $wrongSchema.schemaVersion = 'nq-internal-provenance-v999'
    Write-Json $wrongSchemaPath $wrongSchema
    Assert-Rejected 'unexpected-schema-version' {
        & $provenanceValidator @validatorArguments -ProvenancePath $wrongSchemaPath | Out-Null
    }

    Add-Content -LiteralPath $backendArtifact -Value 'tamper'
    $tamperRejected = $false
    try { & (Join-Path $scripts 'New-InternalProvenance.ps1') @arguments -OutputPath (Join-Path $runRoot 'tampered.json') | Out-Null }
    catch { $tamperRejected = $_.Exception.Message -match 'Artifact integrity mismatch' }
    Assert-Condition $tamperRejected 'Tampered artifact was not rejected'

    $unsafe = Join-Path $frontendRoot 'artifacts/unsafe.txt'
    [IO.File]::WriteAllText($unsafe, ('github_' + 'pat_' + 'abcdefghijklmnopqrstuvwxyz123456'))
    $secretRejected = $false
    try { & (Join-Path $scripts 'Test-DeliveryArtifactSafety.ps1') -EvidenceRoot $frontendRoot | Out-Null }
    catch { $secretRejected = $true }
    Assert-Condition $secretRejected 'Secret-like delivery artifact was not rejected'

    Write-Output 'DELIVERY_EVIDENCE_TEST normalized=PASS deterministic=PASS readback=PASS provenance-negative=PASS tamper=REJECTED secret=REJECTED'
} finally {
    if (Test-Path -LiteralPath $runRoot) {
        $resolvedRoot = (Resolve-Path -LiteralPath $artifactRoot).Path
        $resolvedRun = (Resolve-Path -LiteralPath $runRoot).Path
        if (-not $resolvedRun.StartsWith($resolvedRoot + [IO.Path]::DirectorySeparatorChar)) {
            throw 'Refusing to remove test artifacts outside repository artifacts root'
        }
        Remove-Item -LiteralPath $resolvedRun -Recurse -Force
    }
}
