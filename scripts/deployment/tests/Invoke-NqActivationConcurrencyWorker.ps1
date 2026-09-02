[CmdletBinding()]param(
  [Parameter(Mandatory=$true)][string]$InstallerPath,
  [Parameter(Mandatory=$true)][ValidateSet('ACTIVATE','ROLLBACK','RECOVER','HOLD')][string]$Operation,
  [Parameter(Mandatory=$true)][string]$InstallationRoot,
  [string]$ReleaseId,
  [string]$DatabaseStatePath,
  [string]$ExpectedSourceCommit,
  [int]$LockTimeoutSeconds=15,
  [int]$HoldMilliseconds=0
)
Set-StrictMode -Version Latest;$ErrorActionPreference='Stop'
try{
  $common=@{InstallationRoot=$InstallationRoot;ExpectedSourceCommit=$ExpectedSourceCommit;ConfirmDisposable=$true;OperationLockTimeoutSeconds=$LockTimeoutSeconds}
  if($HoldMilliseconds-gt0){$common.TestLockHoldMilliseconds=$HoldMilliseconds}
  if($Operation-ceq'ACTIVATE'){$result=& $InstallerPath -Action activate -ReleaseId $ReleaseId -DatabaseStatePath $DatabaseStatePath @common}
  elseif($Operation-ceq'ROLLBACK'){$result=& $InstallerPath -Action rollback -DatabaseStatePath $DatabaseStatePath @common}
  elseif($Operation-ceq'RECOVER'){$result=& $InstallerPath -Action recover @common}
  else{$result=& $InstallerPath -Action test-hold-lock @common}
  [pscustomobject]@{success=$true;operation=$Operation;releaseId=$ReleaseId;result=$result}|ConvertTo-Json -Depth 12 -Compress
}catch{
  [pscustomobject]@{success=$false;operation=$Operation;releaseId=$ReleaseId;error=$_.Exception.Message}|ConvertTo-Json -Compress
  exit 3
}
