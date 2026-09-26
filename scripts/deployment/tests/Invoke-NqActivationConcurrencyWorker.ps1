[CmdletBinding()]param(
  [Parameter(Mandatory=$true)][string]$InstallerPath,
  [Parameter(Mandatory=$true)][ValidateSet('ACTIVATE','ROLLBACK','RECOVER','HOLD','BOOTSTRAP')][string]$Operation,
  [Parameter(Mandatory=$true)][string]$InstallationRoot,
  [string]$ReleaseId,
  [string]$DatabaseStatePath,
  [string]$ExpectedSourceCommit,
  [int]$LockTimeoutSeconds=15,
  [int]$HoldMilliseconds=0,
  [int]$PreparationHoldMilliseconds=0,
  [int]$RecordHoldMilliseconds=0
)
Set-StrictMode -Version Latest;$ErrorActionPreference='Stop'
try{
  $common=@{InstallationRoot=$InstallationRoot;ExpectedSourceCommit=$ExpectedSourceCommit;ConfirmDisposable=$true;OperationLockTimeoutSeconds=$LockTimeoutSeconds}
  if($HoldMilliseconds-gt0){$common.TestLockHoldMilliseconds=$HoldMilliseconds}
  if($PreparationHoldMilliseconds-gt0){$common.TestPreparationHoldMilliseconds=$PreparationHoldMilliseconds}
  if($RecordHoldMilliseconds-gt0){$common.TestRecordHoldMilliseconds=$RecordHoldMilliseconds}
  if($Operation-ceq'ACTIVATE'){$result=& $InstallerPath -Action activate -ReleaseId $ReleaseId -DatabaseStatePath $DatabaseStatePath @common}
  elseif($Operation-ceq'ROLLBACK'){$result=& $InstallerPath -Action rollback -DatabaseStatePath $DatabaseStatePath @common}
  elseif($Operation-ceq'RECOVER'){$result=& $InstallerPath -Action recover @common}
  elseif($Operation-ceq'BOOTSTRAP'){$result=& $InstallerPath -Action bootstrap-current -ReleaseId $ReleaseId -DatabaseStatePath $DatabaseStatePath -TestProductionPolicy @common}
  else{$result=& $InstallerPath -Action test-hold-lock @common}
  [pscustomobject]@{success=$true;operation=$Operation;releaseId=$ReleaseId;result=$result}|ConvertTo-Json -Depth 12 -Compress
}catch{
  [pscustomobject]@{success=$false;operation=$Operation;releaseId=$ReleaseId;error=$_.Exception.Message}|ConvertTo-Json -Compress
  exit 3
}
