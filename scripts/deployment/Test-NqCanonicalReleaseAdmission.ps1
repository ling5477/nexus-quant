[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ReleaseRoot,
    [Parameter(Mandatory=$true)][string]$AdmissionRootPath,
    [Parameter(Mandatory=$true)][ValidatePattern('^[0-9a-f]{64}$')][string]$ExpectedAdmissionSha256,
    [ValidateSet('TEST_ONLY','EXACT_HEAD_CI')][string]$RequiredMode='TEST_ONLY'
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'nq-canonical-release.psm1') -Force -DisableNameChecking
$root=[IO.Path]::GetFullPath($ReleaseRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
$admissionPath=[IO.Path]::GetFullPath($AdmissionRootPath)
if($admissionPath.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'BLOCKED / ADMISSION_ROOT_MUST_BE_EXTERNAL'}
if(-not(Test-Path $admissionPath -PathType Leaf)-or(Get-NqSha256File $admissionPath)-cne$ExpectedAdmissionSha256){throw 'BLOCKED / EXTERNAL_ADMISSION_ROOT_MISMATCH'}
try{$admission=Get-Content $admissionPath -Raw|ConvertFrom-Json}catch{throw 'BLOCKED / EXTERNAL_ADMISSION_ROOT_INVALID'}
$fields=@($admission.PSObject.Properties.Name|Sort-Object)
$expected=@('admissionMode','artifactSetIdentity','authorizationEligible','executionIdentity','postgresqlMajor','producerContractDigest','releaseId','releaseManifestDigest','releaseRootDigest','requiredSchemaVersion','schemaVersion','sourceCommit','sourceTreeIdentity')|Sort-Object
$verified=Test-NqCanonicalRelease $root
$manifest=Get-Content (Join-Path $root 'release-manifest.json') -Raw|ConvertFrom-Json
$artifactIdentity=Get-NqSha256Text ((@($manifest.artifacts|Sort-Object relativePath|ForEach-Object{"$([string]$_.relativePath)|$([long]$_.size)|$([string]$_.sha256)"})-join"`n")+"`n")
if(($fields-join'|')-cne($expected-join'|')-or[string]$admission.schemaVersion-cne'nq-canonical-release-admission.v1'-or
        [string]$admission.admissionMode-cne$RequiredMode-or[string]$admission.sourceCommit-cne[string]$manifest.sourceCommit-or
        [string]$admission.sourceTreeIdentity-cne[string]$manifest.sourceTreeIdentity-or[string]$admission.releaseId-cne[string]$manifest.releaseId-or
        [string]$admission.producerContractDigest-cne(Get-NqProducerContractDigest $root)-or
        [string]$admission.releaseManifestDigest-cne(Get-NqSha256File (Join-Path $root 'release-manifest.json'))-or
        [string]$admission.releaseRootDigest-cne(Get-NqReleaseRootDigest $root)-or
        [string]$admission.requiredSchemaVersion-cne[string]$manifest.requiredSchemaTarget-or
        [int]$admission.postgresqlMajor-ne16-or[string]$admission.artifactSetIdentity-cne$artifactIdentity){throw 'BLOCKED / EXTERNAL_ADMISSION_BINDING_INVALID'}
if(($RequiredMode-ceq'EXACT_HEAD_CI'-and(-not[bool]$admission.authorizationEligible-or-not[bool]$manifest.deployable))-or
        ($RequiredMode-ceq'TEST_ONLY'-and([bool]$admission.authorizationEligible-or[bool]$manifest.deployable))){throw 'BLOCKED / EXTERNAL_ADMISSION_AUTHORIZATION_INVALID'}
[pscustomobject][ordered]@{decision='PASS / NQ_CANONICAL_RELEASE_ADMITTED';releaseId=$verified.releaseId;admissionMode=$RequiredMode;admissionSha256=$ExpectedAdmissionSha256;externalTrustRoot=$true}
