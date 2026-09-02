[CmdletBinding()]param([Parameter(Mandatory=$true)][string]$SourceCommit)
Set-StrictMode -Version Latest;$ErrorActionPreference='Stop'
if($env:GITHUB_ACTIONS-cne'true'-or$env:CI-cne'true'-or[string]$env:GITHUB_SHA-cne$SourceCommit){throw 'BLOCKED / EXACT_HEAD_CI_ADMISSION_CONTEXT_INVALID'}
$releaseRoot=Join-Path $env:RUNNER_TEMP 'nq-canonical-release';$admissionRoot='artifacts/delivery/provenance/release-admission.json';$admissionDigest='artifacts/delivery/provenance/release-admission.sha256'
$null=& (Join-Path $PSScriptRoot 'New-NqCanonicalRelease.ps1') -ExpectedCommit $SourceCommit `
  -BackendArtifactPath 'artifacts/delivery/backend/artifacts/nq-app.jar' -BackendArtifactManifestPath 'artifacts/delivery/backend/backend-artifact-manifest.json' `
  -FrontendArtifactRoot 'artifacts/delivery/frontend/artifacts/dist' -FrontendArtifactManifestPath 'artifacts/delivery/frontend/frontend-artifact-manifest.json' -OutputRoot $releaseRoot
& (Join-Path $PSScriptRoot 'New-NqCanonicalReleaseAdmission.ps1') -ReleaseRoot $releaseRoot -OutputPath $admissionRoot -DigestOutputPath $admissionDigest -Mode EXACT_HEAD_CI
