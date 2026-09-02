[CmdletBinding()]param(
  [Parameter(Mandatory=$true)][ValidateSet('BACKUP_INTEGRITY','POST_RESTORE_VALIDATION')][string]$Capability,
  [Parameter(Mandatory=$true)][string]$ProofRoot,
  [Parameter(Mandatory=$true)][string]$ExpectedCommit
)
Set-StrictMode -Version Latest;$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSScriptRoot 'nq-canonical-database.psm1') -Force -DisableNameChecking
$root=[IO.Path]::GetFullPath($ProofRoot);$proofPath=Join-Path $root 'restore-proof.json'
if(-not(Test-Path $proofPath -PathType Leaf)){throw 'BLOCKED / RESTORE_PROOF_MISSING'}
$proof=Get-Content $proofPath -Raw|ConvertFrom-Json
if($Capability-ceq'BACKUP_INTEGRITY'){
  $dump=Join-Path $root 'current-schema.dump';$metadata=Join-Path $root 'current-schema.metadata.json'
  $backup=Test-NqCanonicalBackup $dump $metadata ([string]$proof.actualLatestMigration) $ExpectedCommit 16
  if([string]$backup.backupSha256-cne[string]$proof.backupSha256-or[long]$backup.backupSize-ne[long]$proof.backupSize){throw 'BLOCKED / BACKUP_PROOF_BINDING_INVALID'}
  [pscustomobject]@{decision='PASS / CANONICAL_BACKUP_CREATION_AND_INTEGRITY_VERIFIED';backupSha256=$backup.backupSha256}
}else{
  $required=@('tampered-backup','truncated-backup','wrong-schema-target','wrong-postgresql-major','restore-command-failure','post-restore-validation-mismatch','missing-flyway-history')
  if([string]$proof.decision-cne'PASS / CURRENT_SCHEMA_BACKUP_RESTORE_PROVEN'-or[string]$proof.actualLatestMigration-cnotmatch'^V[1-9][0-9]*$'-or[int]$proof.postgresqlServerMajor-ne16-or[int]$proof.pendingMigrations-ne0-or[string]$proof.flywayValidate-cne'PASS'-or[string]$proof.sourceCanary-cne[string]$proof.restoredCanary-or[string]$proof.repositorySmoke-cne'PASS'-or[string]$proof.applicationContextSmoke-cne'PASS'-or@($required|Where-Object{@($proof.negativeCases)-cnotcontains$_}).Count-ne0){throw 'BLOCKED / POST_RESTORE_VALIDATION_INVALID'}
  [pscustomobject]@{decision='PASS / CANONICAL_POST_RESTORE_VALIDATION_VERIFIED';schema=$proof.actualLatestMigration;pending=0}
}
