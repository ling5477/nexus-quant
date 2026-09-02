[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$ReleaseRoot,
    [Parameter(Mandatory=$true)][string]$OutputPath,
    [string]$DigestOutputPath,
    [ValidateSet('TEST_ONLY','EXACT_HEAD_CI')][string]$Mode='TEST_ONLY'
)
Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'nq-canonical-release.psm1') -Force -DisableNameChecking
$utf8=[Text.UTF8Encoding]::new($false)
$root=[IO.Path]::GetFullPath($ReleaseRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
$output=[IO.Path]::GetFullPath($OutputPath)
if($output.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'BLOCKED / ADMISSION_ROOT_MUST_BE_EXTERNAL'}
$verified=Test-NqCanonicalRelease $root
$manifest=Get-Content (Join-Path $root 'release-manifest.json') -Raw|ConvertFrom-Json
if($Mode-ceq'EXACT_HEAD_CI'){
    if(-not[bool]$manifest.deployable -or [string]$manifest.sourceState-cne'COMMITTED_CLEAN' -or
            $env:GITHUB_ACTIONS-cne'true' -or $env:CI-cne'true' -or
            [string]$env:GITHUB_SHA-cne[string]$manifest.sourceCommit -or
            [string]::IsNullOrWhiteSpace($env:GITHUB_RUN_ID)){
        throw 'BLOCKED / EXACT_HEAD_CI_ADMISSION_CONTEXT_INVALID'
    }
    $eligible=$true
    $executionIdentity="$($env:GITHUB_RUN_ID):$($env:GITHUB_RUN_ATTEMPT)"
}else{
    if([bool]$manifest.deployable){throw 'BLOCKED / TEST_ONLY_ADMISSION_REQUIRES_NON_DEPLOYABLE_RELEASE'}
    $eligible=$false
    $executionIdentity='SYNTHETIC_TEST_ONLY'
}
$artifactIdentity=Get-NqSha256Text ((@($manifest.artifacts|Sort-Object relativePath|ForEach-Object{"$([string]$_.relativePath)|$([long]$_.size)|$([string]$_.sha256)"})-join"`n")+"`n")
$record=[pscustomobject][ordered]@{
    schemaVersion='nq-canonical-release-admission.v1'
    admissionMode=$Mode
    authorizationEligible=$eligible
    sourceCommit=[string]$manifest.sourceCommit
    sourceTreeIdentity=[string]$manifest.sourceTreeIdentity
    producerContractDigest=Get-NqProducerContractDigest $root
    releaseId=[string]$manifest.releaseId
    releaseManifestDigest=Get-NqSha256File (Join-Path $root 'release-manifest.json')
    releaseRootDigest=Get-NqReleaseRootDigest $root
    requiredSchemaVersion=[string]$manifest.requiredSchemaTarget
    postgresqlMajor=[int]$manifest.requiredPostgresqlMajor
    artifactSetIdentity=$artifactIdentity
    executionIdentity=$executionIdentity
}
[IO.Directory]::CreateDirectory((Split-Path -Parent $output))|Out-Null
[IO.File]::WriteAllText($output,($record|ConvertTo-Json -Depth 12 -Compress),$utf8)
$sha=Get-NqSha256File $output
if(-not[string]::IsNullOrWhiteSpace($DigestOutputPath)){
    $digestPath=[IO.Path]::GetFullPath($DigestOutputPath)
    if($digestPath.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'BLOCKED / ADMISSION_ROOT_MUST_BE_EXTERNAL'}
    [IO.Directory]::CreateDirectory((Split-Path -Parent $digestPath))|Out-Null
    [IO.File]::WriteAllText($digestPath,$sha+"  "+[IO.Path]::GetFileName($output)+"`n",$utf8)
}
[pscustomobject][ordered]@{decision='PASS / NQ_CANONICAL_RELEASE_ADMISSION_CREATED';admissionMode=$Mode;authorizationEligible=$eligible;admissionRootPath=$output;admissionSha256=$sha;releaseId=$verified.releaseId}
