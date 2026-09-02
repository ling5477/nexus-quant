[CmdletBinding()]param([Parameter(Mandatory=$true)][string]$SourceCommit)
Set-StrictMode -Version Latest;$ErrorActionPreference='Stop'
$releaseRoot=Join-Path $env:RUNNER_TEMP 'nq-canonical-release';$admissionRoot='artifacts/delivery/provenance/release-admission.json';$digestPath='artifacts/delivery/provenance/release-admission.sha256'
$line=([IO.File]::ReadAllText([IO.Path]::GetFullPath($digestPath))).Trim();if($line-cnotmatch'^([0-9a-f]{64})(?:\s|$)'){throw 'BLOCKED / PRODUCTION_ADMISSION_DIGEST_INVALID'};$digest=$Matches[1]
$null=& (Join-Path $PSScriptRoot 'Test-NqCanonicalRelease.ps1') -ReleaseRoot $releaseRoot -ExpectedSourceCommit $SourceCommit -RequirePosix
& (Join-Path $PSScriptRoot 'Test-NqCanonicalReleaseAdmission.ps1') -ReleaseRoot $releaseRoot -AdmissionRootPath $admissionRoot -ExpectedAdmissionSha256 $digest -RequiredMode EXACT_HEAD_CI
