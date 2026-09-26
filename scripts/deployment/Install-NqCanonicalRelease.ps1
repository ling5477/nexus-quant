[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('preflight', 'install', 'install-for-bootstrap', 'verify', 'observe-database', 'bootstrap-current', 'activate', 'rollback', 'recover', 'test-hold-lock')]
    [string]$Action,
    [Parameter(Mandatory = $true)][string]$InstallationRoot,
    [string]$SourceRoot,
    [string]$ReleaseId,
    [string]$ExpectedSourceCommit,
    [string]$AdmissionRootPath,
    [string]$ExpectedAdmissionSha256,
    [string]$DatabaseStatePath,
    [string]$PsqlPath,
    [string]$DatabaseHost,
    [int]$DatabasePort,
    [string]$DatabaseName,
    [string]$DatabaseUser,
    [string]$TestDatabaseSchemaVersion,
    [int]$TestPostgresqlMajor,
    [ValidateRange(-1440,0)][int]$TestDatabaseObservedAtOffsetMinutes=0,
    [ValidateRange(0,10000)][int]$TestDatabaseFailedMigrationCount=0,
    [ValidateRange(1,120)][int]$OperationLockTimeoutSeconds=15,
    [ValidateRange(0,60000)][int]$TestLockHoldMilliseconds=0,
    [ValidateRange(0,60000)][int]$TestPreparationHoldMilliseconds=0,
    [ValidateRange(0,60000)][int]$TestRecordHoldMilliseconds=0,
    [ValidateSet('NONE', 'AUTHORITY_PREWRITE', 'RECORD_PREWRITE', 'PREPARATION', 'POINTER_SWAP', 'COMPLETION_WRITE', 'HEAD_WRITE')]
    [string]$TestFault = 'NONE',
    [switch]$TestProductionPolicy,
    [switch]$ConfirmDisposable,
    [switch]$ConfirmProduction
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSScriptRoot 'nq-canonical-release.psm1') -Force -DisableNameChecking
$script:Utf8NoBom = [Text.UTF8Encoding]::new($false)

function Assert-ExecutionBoundary {
    $root = [IO.Path]::GetFullPath($InstallationRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
    if ($ConfirmDisposable -eq $ConfirmProduction) { throw 'BLOCKED / EXACTLY_ONE_INSTALLATION_BOUNDARY_REQUIRED' }
    if ($ConfirmProduction) {
        if (-not $IsLinux -or [Environment]::UserName -cne 'root' -or $root -cne '/opt/nexus-quant') {
            throw 'BLOCKED / PRODUCTION_INSTALLATION_BOUNDARY_INVALID'
        }
        if ($TestFault -cne 'NONE' -or $TestLockHoldMilliseconds-gt0 -or $TestPreparationHoldMilliseconds-gt0 -or $TestRecordHoldMilliseconds-gt0 -or $Action-ceq'test-hold-lock' -or -not [string]::IsNullOrWhiteSpace($TestDatabaseSchemaVersion) -or $TestDatabaseObservedAtOffsetMinutes-ne0 -or $TestDatabaseFailedMigrationCount-ne0) {
            throw 'BLOCKED / TEST_CONTROL_FORBIDDEN_IN_PRODUCTION'
        }
    } else {
        if ($TestProductionPolicy -and -not $ConfirmDisposable) { throw 'BLOCKED / TEST_PRODUCTION_POLICY_BOUNDARY_INVALID' }
        $temp = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)
        if (-not $root.StartsWith($temp + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
            throw 'BLOCKED / DISPOSABLE_INSTALLATION_ROOT_INVALID'
        }
    }
    return $root
}

function Assert-ReleaseId([string]$Value) {
    if ($Value -cnotmatch '^(?:nq-[0-9a-f]{12}-[0-9a-f]{16}|nq-test-[0-9a-f]{24})$') { throw 'BLOCKED / RELEASE_ID_INVALID' }
}
function Get-ReleasesRoot([string]$Root) { Join-Path $Root 'releases' }
function Get-ReleaseRoot([string]$Root, [string]$Id) { Assert-ReleaseId $Id; Join-Path (Get-ReleasesRoot $Root) $Id }

function Get-CurrentReleaseId([string]$Root) {
    if ($IsLinux) {
        $current = Join-Path $Root 'current'
        $item=Get-Item -LiteralPath $current -Force -ErrorAction SilentlyContinue
        if($null-eq$item){return 'NONE'}
        if([string]$item.LinkType-cne'SymbolicLink'){throw 'BLOCKED / CURRENT_POINTER_INVALID'}
        $target = @(& /usr/bin/readlink '-f' '--' $current 2>$null)
        if ($LASTEXITCODE -ne 0 -or $target.Count -ne 1) { throw 'BLOCKED / CURRENT_POINTER_INVALID' }
        $id = Split-Path -Leaf ([string]$target[0])
        Assert-ReleaseId $id
        $expected=Get-ReleaseRoot $Root $id
        if([string]$target[0]-cne$expected){throw 'BLOCKED / CURRENT_POINTER_INVALID'}
        $raw=@(& /usr/bin/readlink '--' $current 2>$null)
        if($LASTEXITCODE-ne0-or$raw.Count-ne1-or([string]$raw[0]-cne$expected-and[string]$raw[0]-cne("releases/$id"))){throw 'BLOCKED / CURRENT_POINTER_INVALID'}
    } else {
        $pointer = Join-Path $Root 'current.release'
        if (-not (Test-Path -LiteralPath $pointer -PathType Leaf)) { return 'NONE' }
        $id = ([IO.File]::ReadAllText($pointer, [Text.Encoding]::UTF8)).Trim()
    }
    Assert-ReleaseId $id
    return $id
}

function Get-InstallationIdentity([string]$Root) {
    Get-NqSha256Text ([IO.Path]::GetFullPath($Root).Replace('\', '/').ToLowerInvariant())
}
function Get-KeyPath([string]$Root) { Join-Path $Root '.activation-authority.key' }
function Get-JournalPath([string]$Root) { Join-Path $Root 'activation-journal.json' }
function Get-HeadPath([string]$Root) { Join-Path $Root 'activation-head.json' }
function Get-OperationLockPath([string]$Root) { Join-Path $Root '.activation-operation.lock' }
function Get-BootstrapRecordPath([string]$Root) { Join-Path $Root 'bootstrap-predecessor.json' }

function Get-LinuxPathIdentity([string]$Path, [string]$Kind) {
    $metadata=@(& /usr/bin/stat '--format=%F|%h|%U|%a|%d|%i' '--' $Path 2>$null)
    if($LASTEXITCODE-ne0-or$metadata.Count-ne1){throw 'BLOCKED / LEGACY_CURRENT_IDENTITY_INVALID'}
    $parts=([string]$metadata[0]).Split('|')
    $owner=if($ConfirmProduction){'root'}else{(@(& /usr/bin/id '-un')-join'').Trim()}
    if($parts.Count-ne6-or$parts[0]-cne$Kind-or($Kind-ceq'symbolic link'-and[long]$parts[1]-ne1)-or$parts[2]-cne$owner){throw 'BLOCKED / LEGACY_CURRENT_IDENTITY_INVALID'}
    if($Kind-cne'symbolic link'){
        $mode=[Convert]::ToInt32($parts[3],8)
        if(($mode-band0x12)-ne0){throw 'BLOCKED / LEGACY_CURRENT_IDENTITY_INVALID'}
    }
    return [string]$metadata[0]
}

function Get-LegacyPredecessor([string]$Root) {
    if(-not$IsLinux){throw 'BLOCKED / LEGACY_CURRENT_IDENTITY_INVALID'}
    $releases=Get-ReleasesRoot $Root;$current=Join-Path $Root 'current'
    $null=Get-LinuxPathIdentity $Root 'directory'
    $null=Get-LinuxPathIdentity $releases 'directory'
    $linkIdentity=Get-LinuxPathIdentity $current 'symbolic link'
    $raw=@(& /usr/bin/readlink '--' $current 2>$null)
    if($LASTEXITCODE-ne0-or$raw.Count-ne1){throw 'BLOCKED / LEGACY_CURRENT_IDENTITY_INVALID'}
    $reference=[string]$raw[0]
    if($reference-cmatch'^releases/([0-9a-f]{40})$'){$sha=$Matches[1]}
    elseif($reference-cmatch('^'+[regex]::Escape($releases)+'/([0-9a-f]{40})$')){$sha=$Matches[1]}
    else{throw 'BLOCKED / LEGACY_CURRENT_IDENTITY_INVALID'}
    $expected=Join-Path $releases $sha
    $targetIdentity=Get-LinuxPathIdentity $expected 'directory'
    $resolved=@(& /usr/bin/readlink '-f' '--' $current 2>$null)
    if($LASTEXITCODE-ne0-or$resolved.Count-ne1-or[string]$resolved[0]-cne$expected){throw 'BLOCKED / LEGACY_CURRENT_IDENTITY_INVALID'}
    [pscustomobject]@{kind='UNMANAGED_NON_CANONICAL';resolvedPath=$expected;sourceSha=$sha;targetIdentitySha256=Get-NqSha256Text $targetIdentity;pointerIdentitySha256=Get-NqSha256Text $linkIdentity}
}

function Assert-BootstrapHistory([string]$Root,[string]$TargetId) {
    $head=Read-ActivationHead $Root
    if([long]$head.generation-ne0){throw 'BLOCKED / EXISTING_CANONICAL_ACTIVATION_HISTORY'}
    $path=Get-JournalPath $Root
    $hasJournal=Test-Path -LiteralPath $path -PathType Leaf
    if($hasJournal){
        $journal=Read-Signed $Root $path 'nq-canonical-activation-journal.v2'
        if([string]$journal.operation-cne'BOOTSTRAP_CURRENT'-or[string]$journal.currentReleaseId-cne$TargetId-or[string]$journal.state-ceq'COMPLETED'){
            throw 'BLOCKED / EXISTING_CANONICAL_ACTIVATION_HISTORY'
        }
    }
    $recordPath=Get-BootstrapRecordPath $Root
    if(Test-Path -LiteralPath $recordPath -PathType Leaf){
        $record=Read-Signed $Root $recordPath 'nq-canonical-bootstrap-predecessor.v1'
        if([string]$record.currentReleaseId-cne$TargetId){throw 'BLOCKED / BOOTSTRAP_TARGET_CONFLICT'}
        if(-not$hasJournal){
            if([string]$record.currentSourceCommit-cne$ExpectedSourceCommit){throw 'BLOCKED / BOOTSTRAP_SOURCE_CONFLICT'}
            $legacy=Get-LegacyPredecessor $Root
            if([string]$legacy.resolvedPath-cne[string]$record.legacyResolvedPath-or
               [string]$legacy.sourceSha-cne[string]$record.legacySourceSha-or
               [string]$legacy.targetIdentitySha256-cne[string]$record.legacyTargetIdentitySha256-or
               [string]$legacy.pointerIdentitySha256-cne[string]$record.legacyPointerIdentitySha256){
                throw 'BLOCKED / LEGACY_CURRENT_IDENTITY_INVALID'
            }
        }
    }
}

function Assert-OperationLockIdentity([string]$Path) {
    if(-not(Test-Path $Path -PathType Leaf)){throw 'BLOCKED / ACTIVATION_OPERATION_LOCK_IDENTITY_INVALID'}
    $item=Get-Item -LiteralPath $Path -Force
    if($null-ne$item.LinkType-or(($item.Attributes-band[IO.FileAttributes]::ReparsePoint)-ne0)){throw 'BLOCKED / ACTIVATION_OPERATION_LOCK_IDENTITY_INVALID'}
    if($IsLinux){
        $metadata=@(& /usr/bin/stat '--format=%F|%h|%U|%a' '--' $Path 2>$null);$expectedOwner=if($ConfirmProduction){'root'}else{(@(& /usr/bin/id '-un')-join'').Trim()}
        if($LASTEXITCODE-ne0-or$metadata.Count-ne1){throw 'BLOCKED / ACTIVATION_OPERATION_LOCK_IDENTITY_INVALID'}
        $parts=([string]$metadata[0]).Split('|')
        if($parts.Count-ne4-or$parts[0]-notin@('regular file','regular empty file')-or[long]$parts[1]-ne1-or$parts[2]-cne$expectedOwner-or$parts[3]-cne'600'){throw 'BLOCKED / ACTIVATION_OPERATION_LOCK_IDENTITY_INVALID'}
    }else{
        $fsutil=Join-Path $env:SystemRoot 'System32/fsutil.exe';$links=@(& $fsutil hardlink list $Path 2>$null|Where-Object{-not[string]::IsNullOrWhiteSpace($_)})
        if($LASTEXITCODE-ne0-or$links.Count-ne1){throw 'BLOCKED / ACTIVATION_OPERATION_LOCK_IDENTITY_INVALID'}
    }
}

function Enter-ActivationOperationLock([string]$Root) {
    [IO.Directory]::CreateDirectory($Root)|Out-Null
    $path=Get-OperationLockPath $Root
    if(Test-Path $path){Assert-OperationLockIdentity $path}
    $deadline=[DateTime]::UtcNow.AddSeconds($OperationLockTimeoutSeconds);$stream=$null
    while($null-eq$stream-and[DateTime]::UtcNow-lt$deadline){
        try{$stream=[IO.File]::Open($path,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)}catch [IO.IOException]{Start-Sleep -Milliseconds 50}
    }
    if($null-eq$stream){throw 'BLOCKED / ACTIVATION_OPERATION_LOCK_TIMEOUT'}
    try{
        if($IsLinux){& /usr/bin/chmod 0600 '--' $path;if($LASTEXITCODE-ne0){throw 'FAIL / ACTIVATION_OPERATION_LOCK_MODE_FAILED'}}
        Assert-OperationLockIdentity $path
        $payload=[Text.UTF8Encoding]::new($false).GetBytes("installation=$(Get-InstallationIdentity $Root)`npid=$PID`n")
        $stream.SetLength(0);$stream.Write($payload,0,$payload.Length);$stream.Flush($true)
        if($TestLockHoldMilliseconds-gt0){Start-Sleep -Milliseconds $TestLockHoldMilliseconds}
        return $stream
    }catch{$stream.Dispose();throw}
}

function Assert-KeyIdentity([string]$Path) {
    if(-not(Test-Path $Path -PathType Leaf)){throw 'BLOCKED / ACTIVATION_AUTHORITY_KEY_INVALID'}
    $item=Get-Item -LiteralPath $Path -Force
    if($null-ne$item.LinkType-or(($item.Attributes-band[IO.FileAttributes]::ReparsePoint)-ne0)){throw 'BLOCKED / ACTIVATION_AUTHORITY_KEY_IDENTITY_INVALID'}
    if($IsLinux){
        $metadata=@(& /usr/bin/stat '--format=%F|%h|%U|%a' '--' $Path 2>$null)
        $expectedOwner=if($ConfirmProduction){'root'}else{(@(& /usr/bin/id '-un')-join'').Trim()}
        if($LASTEXITCODE-ne0-or$metadata.Count-ne1){throw 'BLOCKED / ACTIVATION_AUTHORITY_KEY_IDENTITY_INVALID'}
        $parts=([string]$metadata[0]).Split('|')
        if($parts.Count-ne4-or$parts[0]-cne'regular file'-or[long]$parts[1]-ne1-or$parts[2]-cne$expectedOwner-or$parts[3]-cne'600'){throw 'BLOCKED / ACTIVATION_AUTHORITY_KEY_IDENTITY_INVALID'}
    }else{
        $fsutil=Join-Path $env:SystemRoot 'System32/fsutil.exe';$links=@(& $fsutil hardlink list $Path 2>$null|Where-Object{ -not[string]::IsNullOrWhiteSpace($_)})
        if($LASTEXITCODE-ne0-or$links.Count-ne1){throw 'BLOCKED / ACTIVATION_AUTHORITY_KEY_IDENTITY_INVALID'}
    }
}

function Write-PrivateTemporaryFile([string]$Path,[byte[]]$Bytes) {
    $options=[IO.FileStreamOptions]::new()
    $options.Mode=[IO.FileMode]::CreateNew;$options.Access=[IO.FileAccess]::Write;$options.Share=[IO.FileShare]::None
    if($IsLinux){$options.UnixCreateMode=[IO.UnixFileMode]::UserRead -bor [IO.UnixFileMode]::UserWrite}
    $stream=[IO.File]::Open($Path,$options)
    try{$stream.Write($Bytes,0,$Bytes.Length);$stream.Flush($true)}finally{$stream.Dispose()}
}

function Initialize-Key([string]$Root) {
    [IO.Directory]::CreateDirectory($Root) | Out-Null
    $path = Get-KeyPath $Root
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        $authorityPaths=@((Get-HeadPath $Root),(Get-JournalPath $Root),(Get-BootstrapRecordPath $Root),(Join-Path $Root 'database-state.json'))
        if(-not[string]::IsNullOrWhiteSpace($DatabaseStatePath)){$authorityPaths+=([IO.Path]::GetFullPath($DatabaseStatePath))}
        foreach($authorityPath in $authorityPaths){
            if(Test-Path -LiteralPath $authorityPath -PathType Leaf){throw 'BLOCKED / ACTIVATION_AUTHORITY_KEY_MISSING'}
        }
        $bytes = [byte[]]::new(32)
        [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
        $temporary = Join-Path $Root ('.authority-' + [Guid]::NewGuid().ToString('N'))
        try {
            Write-PrivateTemporaryFile $temporary ($script:Utf8NoBom.GetBytes([Convert]::ToBase64String($bytes)))
            try { [IO.File]::Move($temporary, $path, $false) } catch {
                if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { throw }
            }
        } finally {
            [Array]::Clear($bytes, 0, $bytes.Length)
            if(Test-Path -LiteralPath $temporary -PathType Leaf){Remove-Item -LiteralPath $temporary -Force}
        }
    }
    Assert-KeyIdentity $path
    return $path
}

function Get-Key([string]$Root) {
    try { $key = [Convert]::FromBase64String(([IO.File]::ReadAllText((Initialize-Key $Root))).Trim()) }
    catch { throw 'BLOCKED / ACTIVATION_AUTHORITY_KEY_INVALID' }
    if ($key.Length -ne 32) { [Array]::Clear($key, 0, $key.Length); throw 'BLOCKED / ACTIVATION_AUTHORITY_KEY_INVALID' }
    return $key
}

function Get-Payload($Record) {
    $copy = [ordered]@{}
    foreach ($property in $Record.PSObject.Properties) {
        if ($property.Name -cne 'integrityHmacSha256') { $copy[$property.Name] = $property.Value }
    }
    $copy | ConvertTo-Json -Depth 16 -Compress
}

function Get-RecordDigest($Record) { Get-NqSha256Text ($Record|ConvertTo-Json -Depth 16 -Compress) }

function Get-Hmac([string]$Root, $Record) {
    $key = Get-Key $Root
    $hmac = [Security.Cryptography.HMACSHA256]::new($key)
    try {
        -join ($hmac.ComputeHash($script:Utf8NoBom.GetBytes((Get-Payload $Record))) | ForEach-Object { $_.ToString('x2') })
    } finally { $hmac.Dispose(); [Array]::Clear($key, 0, $key.Length) }
}

function Write-Signed([string]$Root, [string]$Path, $Record) {
    $Record.integrityHmacSha256 = Get-Hmac $Root $Record
    $temporary = Join-Path $Root ('.signed-' + [Guid]::NewGuid().ToString('N') + '.json')
    $bytes=$script:Utf8NoBom.GetBytes(($Record | ConvertTo-Json -Depth 16 -Compress))
    try{Write-PrivateTemporaryFile $temporary $bytes;[IO.File]::Move($temporary, $Path, $true)}
    finally{if(Test-Path -LiteralPath $temporary -PathType Leaf){Remove-Item -LiteralPath $temporary -Force}}
    return $Record
}

function Assert-SignedIdentity([string]$Path) {
    if(-not$IsLinux){return}
    $metadata=@(& /usr/bin/stat '--format=%F|%h|%U|%a' '--' $Path 2>$null)
    $owner=if($ConfirmProduction){'root'}else{(@(& /usr/bin/id '-un')-join'').Trim()}
    if($LASTEXITCODE-ne0-or$metadata.Count-ne1){throw 'BLOCKED / TRUSTED_RECORD_IDENTITY_INVALID'}
    $parts=([string]$metadata[0]).Split('|')
    if($parts.Count-ne4-or$parts[0]-cne'regular file'-or[long]$parts[1]-ne1-or$parts[2]-cne$owner-or$parts[3]-cne'600'){
        throw 'BLOCKED / TRUSTED_RECORD_IDENTITY_INVALID'
    }
}

function Read-Signed([string]$Root, [string]$Path, [string]$Schema) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw 'BLOCKED / TRUSTED_RECORD_MISSING' }
    Assert-SignedIdentity $Path
    try { $record = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json } catch { throw 'BLOCKED / TRUSTED_RECORD_INVALID' }
    if ([string]$record.schemaVersion -cne $Schema -or
            [string]$record.installationIdentity -cne (Get-InstallationIdentity $Root) -or
            [string]$record.integrityHmacSha256 -cnotmatch '^[0-9a-f]{64}$' -or
            [string]$record.integrityHmacSha256 -cne (Get-Hmac $Root $record)) {
        throw 'BLOCKED / TRUSTED_RECORD_INTEGRITY_INVALID'
    }
    return $record
}

function Get-Release([string]$Root, [string]$Id) {
    $releaseRoot = Get-ReleaseRoot $Root $Id
    $verification = Test-NqCanonicalRelease $releaseRoot -ExpectedSourceCommit $ExpectedSourceCommit -RequirePosix:($IsLinux)
    $manifest = Get-Content -LiteralPath (Join-Path $releaseRoot 'release-manifest.json') -Raw | ConvertFrom-Json
    if (($ConfirmProduction -or $TestProductionPolicy) -and -not [bool]$manifest.deployable) { throw 'BLOCKED / NON_DEPLOYABLE_RELEASE_FORBIDDEN' }
    Assert-ReleaseAdmission $Root $releaseRoot $Id
    [pscustomobject]@{ Root=$releaseRoot; Verification=$verification; Manifest=$manifest }
}

function Assert-LinuxTrustedAdmissionFile([string]$Path,[string]$ExpectedMode) {
    if(-not$IsLinux){return}
    $metadata=@(& /usr/bin/stat '--format=%F|%h|%U|%a' '--' $Path 2>$null)
    if($LASTEXITCODE-ne0-or$metadata.Count-ne1){throw 'BLOCKED / ADMISSION_TRUST_ROOT_METADATA_INVALID'}
    $parts=([string]$metadata[0]).Split('|')
    if($parts.Count-ne4-or$parts[0]-cne'regular file'-or[long]$parts[1]-ne1-or$parts[2]-cne'root'-or('0'+$parts[3])-cne$ExpectedMode){throw 'BLOCKED / ADMISSION_TRUST_ROOT_METADATA_INVALID'}
}

function Assert-ReleaseAdmission([string]$Root,[string]$ReleasePath,[string]$Id) {
    $verifier=Join-Path $PSScriptRoot 'Test-NqCanonicalReleaseAdmission.ps1'
    if($ConfirmProduction){
        $admission="/etc/nexus-quant/release-admission/$Id.json"
        $digestPath="/etc/nexus-quant/release-admission/$Id.sha256"
        if(-not(Test-Path $admission -PathType Leaf)-or-not(Test-Path $digestPath -PathType Leaf)){throw 'BLOCKED / PRODUCTION_ADMISSION_ROOT_MISSING'}
        Assert-LinuxTrustedAdmissionFile $admission '0644';Assert-LinuxTrustedAdmissionFile $digestPath '0644'
        $digestLine=([IO.File]::ReadAllText($digestPath)).Trim()
        if($digestLine-cnotmatch '^([0-9a-f]{64})(?:\s|$)'){throw 'BLOCKED / PRODUCTION_ADMISSION_DIGEST_INVALID'}
        $null=& $verifier -ReleaseRoot $ReleasePath -AdmissionRootPath $admission -ExpectedAdmissionSha256 $Matches[1] -RequiredMode EXACT_HEAD_CI
        return
    }
    if($TestProductionPolicy){
        $trustedDirectory=Join-Path $Root 'trusted-release-admission';$admission=Join-Path $trustedDirectory "$Id.json";$digestPath=Join-Path $trustedDirectory "$Id.sha256"
        if(-not(Test-Path $admission -PathType Leaf)-or-not(Test-Path $digestPath -PathType Leaf)){throw 'BLOCKED / EXTERNAL_ADMISSION_ROOT_REQUIRED'}
        $digestLine=([IO.File]::ReadAllText($digestPath)).Trim();if($digestLine-cnotmatch'^([0-9a-f]{64})(?:\s|$)'){throw 'BLOCKED / PRODUCTION_ADMISSION_DIGEST_INVALID'}
        $null=& $verifier -ReleaseRoot $ReleasePath -AdmissionRootPath $admission -ExpectedAdmissionSha256 $Matches[1] -RequiredMode EXACT_HEAD_CI
        return
    }
    if(-not[string]::IsNullOrWhiteSpace($AdmissionRootPath)){
        if([string]::IsNullOrWhiteSpace($ExpectedAdmissionSha256)){throw 'BLOCKED / EXTERNAL_ADMISSION_ROOT_REQUIRED'}
        $null=& $verifier -ReleaseRoot $ReleasePath -AdmissionRootPath $AdmissionRootPath -ExpectedAdmissionSha256 $ExpectedAdmissionSha256 -RequiredMode TEST_ONLY
    }
}

function Set-Modes([string]$Root, $Manifest) {
    if (-not $IsLinux) { return }
    & /usr/bin/chmod 0755 '--' $Root
    foreach ($directory in Get-ChildItem -LiteralPath $Root -Directory -Recurse -Force) { & /usr/bin/chmod 0755 '--' $directory.FullName }
    & /usr/bin/chmod 0644 '--' (Join-Path $Root 'release-manifest.json')
    foreach ($artifact in $Manifest.artifacts) { & /usr/bin/chmod ([string]$artifact.unixMode) '--' (Join-Path $Root ([string]$artifact.relativePath)) }
    if ($LASTEXITCODE -ne 0) { throw 'FAIL / RELEASE_INSTALL_MODE_FAILED' }
}

function Set-Pointer([string]$Root, [string]$Id) {
    if ($TestFault -ceq 'POINTER_SWAP') { throw 'FAIL / TEST_POINTER_SWAP_FAILURE' }
    $target = Get-ReleaseRoot $Root $Id
    if ($IsLinux) {
        $current = Join-Path $Root 'current'
        $next = Join-Path $Root ('.current-' + [Guid]::NewGuid().ToString('N'))
        try {
            & /usr/bin/ln '-s' '--' $target $next
            if ($LASTEXITCODE -ne 0) { throw 'FAIL / RELEASE_ACTIVATION_LINK_FAILED' }
            & /usr/bin/mv '-Tf' '--' $next $current
            if ($LASTEXITCODE -ne 0) { throw 'FAIL / RELEASE_ACTIVATION_RENAME_FAILED' }
        } finally { if (Test-Path -LiteralPath $next) { Remove-Item -LiteralPath $next -Force } }
    } else {
        if (-not $ConfirmDisposable) { throw 'BLOCKED / WINDOWS_ACTIVATION_DISPOSABLE_ONLY' }
        $pointer = Join-Path $Root 'current.release'
        $next = Join-Path $Root ('.current-' + [Guid]::NewGuid().ToString('N'))
        [IO.File]::WriteAllText($next, $Id, $script:Utf8NoBom)
        [IO.File]::Move($next, $pointer, $true)
    }
}

function New-Journal([string]$Root, [string]$Operation, [string]$Previous, [string]$Current, [string]$DatabaseSchema) {
    $head=Read-ActivationHead $Root
    if(([long]$head.generation-eq0-and$Previous-cne'NONE')-or([long]$head.generation-gt0-and[string]$head.currentReleaseId-cne$Previous)){throw 'BLOCKED / ACTIVATION_HEAD_POINTER_MISMATCH'}
    $journal=[pscustomobject][ordered]@{
        schemaVersion='nq-canonical-activation-journal.v2'; installationIdentity=Get-InstallationIdentity $Root
        generation=([long]$head.generation+1);previousHeadDigest=[string]$head.headDigest
        transactionId=[Guid]::NewGuid().ToString('N'); operation=$Operation; state='PREPARED';activationDigest=''
        previousReleaseId=$Previous; currentReleaseId=$Current; databaseSchemaVersion=$DatabaseSchema
        preparedAt=[DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ'); completedAt=$null; integrityHmacSha256=''
    }
    if($null-ne$head.PSObject.Properties['bootstrapPredecessorDigest']){
        $journal|Add-Member -NotePropertyName bootstrapPredecessorDigest -NotePropertyValue ([string]$head.bootstrapPredecessorDigest)
        $journal|Add-Member -NotePropertyName bootstrapTransactionId -NotePropertyValue ([string]$head.bootstrapTransactionId)
    }
    $journal.activationDigest=Get-NqSha256Text ("$($journal.generation)|$($journal.transactionId)|$Previous|$Current|$DatabaseSchema|$($journal.previousHeadDigest)")
    return $journal
}

function Assert-HeadBootstrapAnchor([string]$Root,$Head) {
    $digestProperty=$Head.PSObject.Properties['bootstrapPredecessorDigest']
    $transactionProperty=$Head.PSObject.Properties['bootstrapTransactionId']
    if($null-eq$digestProperty-and$null-eq$transactionProperty){return}
    if($null-eq$digestProperty-or$null-eq$transactionProperty-or
       [string]$digestProperty.Value-cnotmatch'^[0-9a-f]{64}$'-or
       [string]$transactionProperty.Value-cnotmatch'^[0-9a-f]{32}$'){
        throw 'BLOCKED / BOOTSTRAP_PREDECESSOR_INTEGRITY_INVALID'
    }
    $record=Read-Signed $Root (Get-BootstrapRecordPath $Root) 'nq-canonical-bootstrap-predecessor.v1'
    if([string]$record.predecessorKind-cne'UNMANAGED_NON_CANONICAL'-or
       [string]$record.transactionId-cne[string]$transactionProperty.Value-or
       (Get-RecordDigest $record)-cne[string]$digestProperty.Value){
        throw 'BLOCKED / BOOTSTRAP_PREDECESSOR_INTEGRITY_INVALID'
    }
}

function Read-ActivationHead([string]$Root) {
    $path=Get-HeadPath $Root
    if(-not(Test-Path $path -PathType Leaf)){return [pscustomobject]@{generation=0;transactionId='NONE';currentReleaseId='NONE';previousReleaseId='NONE';previousHeadDigest=('0'*64);activationDigest=('0'*64);headDigest=('0'*64)}}
    $head=Read-Signed $Root $path 'nq-canonical-activation-head.v1'
    $copy=$head|Select-Object * -ExcludeProperty headDigest,integrityHmacSha256
    if([string]$head.headDigest-cne(Get-RecordDigest $copy)){throw 'BLOCKED / ACTIVATION_HEAD_DIGEST_INVALID'}
    Assert-HeadBootstrapAnchor $Root $head
    return $head
}

function Write-ActivationHead([string]$Root,$Journal) {
    if($TestFault-ceq'HEAD_WRITE'){throw 'FAIL / TEST_HEAD_WRITE_FAILURE'}
    $head=[pscustomobject][ordered]@{
        schemaVersion='nq-canonical-activation-head.v1';installationIdentity=Get-InstallationIdentity $Root
        generation=[long]$Journal.generation;transactionId=[string]$Journal.transactionId
        currentReleaseId=[string]$Journal.currentReleaseId;previousReleaseId=[string]$Journal.previousReleaseId
        previousHeadDigest=[string]$Journal.previousHeadDigest;activationDigest=[string]$Journal.activationDigest
        headDigest='';integrityHmacSha256=''
    }
    if($null-ne$Journal.PSObject.Properties['bootstrapPredecessorDigest']){
        $head|Add-Member -NotePropertyName bootstrapPredecessorDigest -NotePropertyValue ([string]$Journal.bootstrapPredecessorDigest)
        $head|Add-Member -NotePropertyName bootstrapTransactionId -NotePropertyValue ([string]$Journal.bootstrapTransactionId)
    }
    $head.headDigest=Get-RecordDigest ($head|Select-Object * -ExcludeProperty headDigest,integrityHmacSha256)
    Write-Signed $Root (Get-HeadPath $Root) $head
}

function Complete-Journal([string]$Root, $Journal, [string]$State) {
    if ($State -ceq 'COMPLETED' -and $TestFault -ceq 'COMPLETION_WRITE') { throw 'FAIL / TEST_COMPLETION_WRITE_FAILURE' }
    $Journal.state=$State; $Journal.completedAt=[DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
    $written=Write-Signed $Root (Get-JournalPath $Root) $Journal
    if($State-ceq'COMPLETED'){$null=Write-ActivationHead $Root $written}
    return $written
}

function Assert-BootstrapRecord([string]$Root,$Journal) {
    $record=Read-Signed $Root (Get-BootstrapRecordPath $Root) 'nq-canonical-bootstrap-predecessor.v1'
    if([string]$record.predecessorKind-cne'UNMANAGED_NON_CANONICAL'-or
       [string]$record.transactionId-cne[string]$Journal.transactionId-or
       [string]$record.currentReleaseId-cne[string]$Journal.currentReleaseId-or
       [string]$record.databaseSchemaVersion-cne[string]$Journal.databaseSchemaVersion-or
       [string]$Journal.bootstrapPredecessorDigest-cne(Get-RecordDigest $record)){
        throw 'BLOCKED / BOOTSTRAP_PREDECESSOR_INTEGRITY_INVALID'
    }
    return $record
}

function Recover-Journal([string]$Root,[bool]$AllowLegacyBootstrap=$false) {
    $path=Get-JournalPath $Root
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) { return $null }
    $journal=Read-Signed $Root $path 'nq-canonical-activation-journal.v2'
    $head=Read-ActivationHead $Root
    $bootstrap=[string]$journal.operation-ceq'BOOTSTRAP_CURRENT'
    if($bootstrap){$record=Assert-BootstrapRecord $Root $journal}
    if([string]$journal.state-ceq'COMPLETED'){
        if([long]$head.generation-eq[long]$journal.generation-and[string]$head.transactionId-ceq[string]$journal.transactionId-and[string]$head.activationDigest-ceq[string]$journal.activationDigest){
            if((Get-CurrentReleaseId $Root)-cne[string]$journal.currentReleaseId){throw 'BLOCKED / ACTIVATION_HEAD_POINTER_MISMATCH'}
            return $journal
        }
        if([long]$head.generation-eq([long]$journal.generation-1)-and[string]$head.headDigest-ceq[string]$journal.previousHeadDigest-and(Get-CurrentReleaseId $Root)-ceq[string]$journal.currentReleaseId){$null=Write-ActivationHead $Root $journal;return $journal}
        throw 'BLOCKED / STALE_ACTIVATION_AUTHORITY'
    }
    if([string]$journal.state-ceq'ABORTED'){
        if([long]$head.generation-ne([long]$journal.generation-1)-or[string]$head.headDigest-cne[string]$journal.previousHeadDigest){throw 'BLOCKED / STALE_ACTIVATION_AUTHORITY'}
        return $journal
    }
    if ([string]$journal.state -cne 'PREPARED' -or [long]$journal.generation-ne([long]$head.generation+1)-or[string]$journal.previousHeadDigest-cne[string]$head.headDigest) { throw 'BLOCKED / STALE_ACTIVATION_AUTHORITY' }
    $actual=if($bootstrap){
        try{Get-CurrentReleaseId $Root}catch{
            if(-not$AllowLegacyBootstrap){throw}
            $legacy=Get-LegacyPredecessor $Root
            if([string]$legacy.resolvedPath-cne[string]$record.legacyResolvedPath-or
               [string]$legacy.sourceSha-cne[string]$record.legacySourceSha-or
               [string]$legacy.targetIdentitySha256-cne[string]$record.legacyTargetIdentitySha256-or
               [string]$legacy.pointerIdentitySha256-cne[string]$record.legacyPointerIdentitySha256){
                throw 'BLOCKED / LEGACY_CURRENT_IDENTITY_INVALID'
            }
            'LEGACY'
        }
    }else{Get-CurrentReleaseId $Root}
    if ($actual -ceq [string]$journal.currentReleaseId) {
        if($bootstrap){
            $releaseRoot=Get-ReleaseRoot $Root ([string]$journal.currentReleaseId)
            $null=Test-NqCanonicalRelease $releaseRoot -ExpectedSourceCommit ([string]$record.currentSourceCommit) -RequirePosix:($IsLinux)
            Assert-ReleaseAdmission $Root $releaseRoot ([string]$journal.currentReleaseId)
        }
        return Complete-Journal $Root $journal 'COMPLETED'
    }
    if($bootstrap-and$actual-ceq'LEGACY'){return Complete-Journal $Root $journal 'ABORTED'}
    if ($actual -ceq [string]$journal.previousReleaseId) { return Complete-Journal $Root $journal 'ABORTED' }
    throw 'BLOCKED / UNKNOWN_ACTIVATION_STATE'
}

function Read-DatabaseState([string]$Root) {
    if ([string]::IsNullOrWhiteSpace($DatabaseStatePath)) { throw 'BLOCKED / DATABASE_STATE_EVIDENCE_REQUIRED' }
    $state=Read-Signed $Root ([IO.Path]::GetFullPath($DatabaseStatePath)) 'nq-canonical-database-state.v1'
    if ([int]$state.postgresqlServerMajor -ne 16 -or [string]$state.currentSchemaVersion -cnotmatch '^V[1-9][0-9]*$') {
        throw 'BLOCKED / UNSUPPORTED_POSTGRESQL_MAJOR'
    }
    if($null-ne$state.PSObject.Properties['failedMigrationCount']-and
       ([string]$state.failedMigrationCount-cnotmatch'^[0-9]+$'-or[int]$state.failedMigrationCount-ne0)){
        throw 'BLOCKED / DATABASE_MIGRATION_HISTORY_INVALID'
    }
    return $state
}

function Write-DatabaseState([string]$Root) {
    if (-not [string]::IsNullOrWhiteSpace($TestDatabaseSchemaVersion)) {
        if (-not $ConfirmDisposable -or $TestDatabaseSchemaVersion -cnotmatch '^V[1-9][0-9]*$') { throw 'BLOCKED / TEST_DATABASE_STATE_INVALID' }
        $schema=$TestDatabaseSchemaVersion; $major=$TestPostgresqlMajor;$failedMigrations=$TestDatabaseFailedMigrationCount
    } else {
        if ([string]::IsNullOrWhiteSpace($PsqlPath) -or -not (Test-Path -LiteralPath $PsqlPath -PathType Leaf) -or
                [string]::IsNullOrWhiteSpace($DatabaseHost) -or $DatabasePort -lt 1 -or
                [string]::IsNullOrWhiteSpace($DatabaseName) -or [string]::IsNullOrWhiteSpace($DatabaseUser)) {
            throw 'BLOCKED / DATABASE_OBSERVATION_INPUT_INVALID'
        }
        $versionOutput=@(& $PsqlPath --host $DatabaseHost --port $DatabasePort --username $DatabaseUser --dbname $DatabaseName `
            --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 --command 'SHOW server_version_num;' 2>$null)
        if ($LASTEXITCODE -ne 0 -or $versionOutput.Count -ne 1 -or [string]$versionOutput[0] -cnotmatch '^([0-9]+)') { throw 'BLOCKED / DATABASE_OBSERVATION_FAILED' }
        $major=[Math]::Floor([int64]$Matches[1] / 10000)
        $schemaOutput=@(& $PsqlPath --host $DatabaseHost --port $DatabasePort --username $DatabaseUser --dbname $DatabaseName `
            --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 `
            --command "SELECT 'V' || version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1;" 2>$null)
        if ($LASTEXITCODE -ne 0 -or $schemaOutput.Count -ne 1) { throw 'BLOCKED / DATABASE_OBSERVATION_FAILED' }
        $schema=([string]$schemaOutput[0]).Trim()
        $failedOutput=@(& $PsqlPath --host $DatabaseHost --port $DatabasePort --username $DatabaseUser --dbname $DatabaseName `
            --no-psqlrc --tuples-only --no-align --set ON_ERROR_STOP=1 `
            --command 'SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success;' 2>$null)
        if($LASTEXITCODE-ne0-or$failedOutput.Count-ne1-or([string]$failedOutput[0]).Trim()-cnotmatch'^[0-9]+$'){
            throw 'BLOCKED / DATABASE_OBSERVATION_FAILED'
        }
        $failedMigrations=[int]([string]$failedOutput[0]).Trim()
    }
    if ($major -ne 16) { throw 'BLOCKED / UNSUPPORTED_POSTGRESQL_MAJOR' }
    $path=if([string]::IsNullOrWhiteSpace($DatabaseStatePath)){Join-Path $Root 'database-state.json'}else{[IO.Path]::GetFullPath($DatabaseStatePath)}
    $record=[pscustomobject][ordered]@{
        schemaVersion='nq-canonical-database-state.v1'; installationIdentity=Get-InstallationIdentity $Root
        currentSchemaVersion=$schema; postgresqlServerMajor=$major;failedMigrationCount=$failedMigrations
        observedAt=[DateTime]::UtcNow.AddMinutes($TestDatabaseObservedAtOffsetMinutes).ToString('yyyy-MM-ddTHH:mm:ssZ'); integrityHmacSha256=''
    }
    Write-Signed $Root $path $record
}

function Invoke-Activation([string]$Root,[string]$TargetId,[string]$Operation,$DatabaseState) {
    $target=Get-Release $Root $TargetId
    if ([string]$DatabaseState.currentSchemaVersion -cne [string]$target.Manifest.requiredSchemaTarget) {
        if ($Operation -ceq 'CODE_ROLLBACK') { throw 'BLOCKED / DATABASE_RECOVERY_REQUIRED' }
        throw 'BLOCKED / RELEASE_DATABASE_SCHEMA_INCOMPATIBLE'
    }
    $previous=Get-CurrentReleaseId $Root
    if ($TestFault -ceq 'AUTHORITY_PREWRITE') { throw 'FAIL / TEST_AUTHORITY_PREWRITE_FAILURE' }
    $journal=New-Journal $Root $Operation $previous $TargetId ([string]$DatabaseState.currentSchemaVersion)
    $null=Write-Signed $Root (Get-JournalPath $Root) $journal
    try { Set-Pointer $Root $TargetId } catch { $null=Complete-Journal $Root $journal 'ABORTED'; throw }
    $completed=Complete-Journal $Root $journal 'COMPLETED'
    [pscustomobject][ordered]@{
        schemaVersion='nq-canonical-activation-result.v2'; transactionId=[string]$completed.transactionId
        generation=[long]$completed.generation
        operation=$Operation; previousReleaseId=$previous; currentReleaseId=$TargetId
        databaseSchemaVersion=[string]$DatabaseState.currentSchemaVersion; state=[string]$completed.state; atomicReplace=$true
    }
}

function Invoke-CurrentBootstrap([string]$Root,[string]$TargetId,$DatabaseState) {
    if(-not$IsLinux-or(-not$ConfirmProduction-and-not$TestProductionPolicy)){
        throw 'BLOCKED / BOOTSTRAP_PRODUCTION_POLICY_REQUIRED'
    }
    if([string]::IsNullOrWhiteSpace($ExpectedSourceCommit)-or$ExpectedSourceCommit-cnotmatch'^[0-9a-f]{40}$'){
        throw 'BLOCKED / EXPECTED_SOURCE_COMMIT_REQUIRED'
    }
    $headBefore=Read-ActivationHead $Root
    if([long]$headBefore.generation-ne0){throw 'BLOCKED / EXISTING_CANONICAL_ACTIVATION_HISTORY'}
    $journalPath=Get-JournalPath $Root
    if(Test-Path -LiteralPath $journalPath -PathType Leaf){
        $existing=Read-Signed $Root $journalPath 'nq-canonical-activation-journal.v2'
        if([string]$existing.operation-cne'BOOTSTRAP_CURRENT'-or[string]$existing.currentReleaseId-cne$TargetId){throw 'BLOCKED / BOOTSTRAP_TARGET_CONFLICT'}
        $existingRecord=Assert-BootstrapRecord $Root $existing
        if([string]$existingRecord.currentSourceCommit-cne$ExpectedSourceCommit){throw 'BLOCKED / BOOTSTRAP_SOURCE_CONFLICT'}
        $recovered=Recover-Journal $Root $true
        if([string]$recovered.state-ceq'COMPLETED'){
            return [pscustomobject]@{decision='PASS / CANONICAL_CURRENT_BOOTSTRAP_RECOVERED';transactionId=[string]$recovered.transactionId;currentReleaseId=$TargetId;state='COMPLETED'}
        }
    }
    Assert-BootstrapHistory $Root $TargetId
    $legacy=Get-LegacyPredecessor $Root
    $target=Get-Release $Root $TargetId
    if(-not[bool]$target.Manifest.deployable){throw 'BLOCKED / NON_DEPLOYABLE_RELEASE_FORBIDDEN'}
    if([string]$DatabaseState.currentSchemaVersion-cne[string]$target.Manifest.requiredSchemaTarget){throw 'BLOCKED / RELEASE_DATABASE_SCHEMA_INCOMPATIBLE'}
    if($null-eq$DatabaseState.PSObject.Properties['failedMigrationCount']-or[int]$DatabaseState.failedMigrationCount-ne0){
        throw 'BLOCKED / DATABASE_MIGRATION_HISTORY_INVALID'
    }
    $observed=[DateTimeOffset]::MinValue
    $validObserved=if($DatabaseState.observedAt-is[DateTime]){
        if($DatabaseState.observedAt.Kind-ne[DateTimeKind]::Utc){$false}
        else{$observed=[DateTimeOffset]::new([DateTime]$DatabaseState.observedAt);$true}
    }else{[DateTimeOffset]::TryParseExact([string]$DatabaseState.observedAt,'yyyy-MM-ddTHH:mm:ssZ',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::AssumeUniversal,[ref]$observed)}
    if(-not$validObserved-or
       $observed-gt[DateTimeOffset]::UtcNow.AddMinutes(1)-or$observed-lt[DateTimeOffset]::UtcNow.AddMinutes(-15)){
        throw 'BLOCKED / DATABASE_STATE_STALE'
    }
    if($TestFault-ceq'AUTHORITY_PREWRITE'){throw 'FAIL / TEST_AUTHORITY_PREWRITE_FAILURE'}
    $journal=New-Journal $Root 'BOOTSTRAP_CURRENT' 'NONE' $TargetId ([string]$DatabaseState.currentSchemaVersion)
    $record=[pscustomobject][ordered]@{
        schemaVersion='nq-canonical-bootstrap-predecessor.v1';installationIdentity=Get-InstallationIdentity $Root
        transactionId=[string]$journal.transactionId;predecessorKind='UNMANAGED_NON_CANONICAL'
        legacyResolvedPath=[string]$legacy.resolvedPath;legacySourceSha=[string]$legacy.sourceSha
        legacyTargetIdentitySha256=[string]$legacy.targetIdentitySha256;legacyPointerIdentitySha256=[string]$legacy.pointerIdentitySha256
        observedAt=[DateTime]::UtcNow.ToString('yyyy-MM-ddTHH:mm:ssZ')
        currentReleaseId=$TargetId;currentSourceCommit=[string]$target.Manifest.sourceCommit
        databaseSchemaVersion=[string]$DatabaseState.currentSchemaVersion;databaseStateDigest=Get-RecordDigest $DatabaseState
        integrityHmacSha256=''
    }
    $record=Write-Signed $Root (Get-BootstrapRecordPath $Root) $record
    if($TestRecordHoldMilliseconds-gt0){Start-Sleep -Milliseconds $TestRecordHoldMilliseconds}
    if($TestFault-ceq'RECORD_PREWRITE'){throw 'FAIL / TEST_RECORD_PREWRITE_FAILURE'}
    $journal|Add-Member -NotePropertyName bootstrapPredecessorDigest -NotePropertyValue (Get-RecordDigest $record)
    $journal|Add-Member -NotePropertyName bootstrapTransactionId -NotePropertyValue ([string]$journal.transactionId)
    $journal.activationDigest=Get-NqSha256Text ("$($journal.activationDigest)|$($journal.bootstrapPredecessorDigest)")
    $null=Write-Signed $Root $journalPath $journal
    if($TestPreparationHoldMilliseconds-gt0){Start-Sleep -Milliseconds $TestPreparationHoldMilliseconds}
    if($TestFault-ceq'PREPARATION'){throw 'FAIL / TEST_PREPARATION_FAILURE'}
    $again=Get-LegacyPredecessor $Root
    if([string]$again.resolvedPath-cne[string]$legacy.resolvedPath-or
       [string]$again.sourceSha-cne[string]$legacy.sourceSha-or
       [string]$again.targetIdentitySha256-cne[string]$legacy.targetIdentitySha256-or
       [string]$again.pointerIdentitySha256-cne[string]$legacy.pointerIdentitySha256){
        throw 'BLOCKED / LEGACY_CURRENT_IDENTITY_INVALID'
    }
    try{Set-Pointer $Root $TargetId}catch{
        $failure=$_
        $actual=try{Get-CurrentReleaseId $Root}catch{'LEGACY'}
        if($actual-ceq'LEGACY'){$null=Recover-Journal $Root $true}
        throw $failure
    }
    $completed=Complete-Journal $Root $journal 'COMPLETED'
    [pscustomobject]@{decision='PASS / CANONICAL_CURRENT_BOOTSTRAPPED';transactionId=[string]$completed.transactionId;currentReleaseId=$TargetId;legacyPredecessorKind='UNMANAGED_NON_CANONICAL';state='COMPLETED';atomicReplace=$true}
}

$root=Assert-ExecutionBoundary
$releasesRoot=Get-ReleasesRoot $root
if($Action -eq 'observe-database'){Write-DatabaseState $root;exit 0}

if($Action -eq 'preflight'){
    $verification=if([string]::IsNullOrWhiteSpace($SourceRoot)){$null}else{Test-NqCanonicalRelease $SourceRoot -ExpectedSourceCommit $ExpectedSourceCommit}
    if(($ConfirmProduction -or $TestProductionPolicy) -and $null-ne$verification -and -not [bool]$verification.deployable){throw 'BLOCKED / NON_DEPLOYABLE_RELEASE_FORBIDDEN'}
    if($null-ne$verification){Assert-ReleaseAdmission $root ([IO.Path]::GetFullPath($SourceRoot)) ([string]$verification.releaseId)}
    [pscustomobject][ordered]@{decision='PASS / NQ_CANONICAL_INSTALL_PREFLIGHT';installationRoot=$root;currentReleaseId=if(Test-Path $root){Get-CurrentReleaseId $root}else{'NONE'};sourceReleaseId=if($null-eq$verification){'NONE'}else{$verification.releaseId};sourceDeployable=if($null-eq$verification){$null}else{$verification.deployable};production=[bool]$ConfirmProduction;gitCheckoutRequired=$false;mavenRequired=$false}
    exit 0
}

[IO.Directory]::CreateDirectory($releasesRoot)|Out-Null
if($Action -in @('install','install-for-bootstrap')){
    if($Action-ceq'install'){$null=Get-CurrentReleaseId $root}
    else{
        if(-not$IsLinux-or(-not$ConfirmProduction-and-not$TestProductionPolicy)){throw 'BLOCKED / BOOTSTRAP_PRODUCTION_POLICY_REQUIRED'}
        $bootstrapInstallLock=Enter-ActivationOperationLock $root
        try{Assert-BootstrapHistory $root ''; $null=Get-LegacyPredecessor $root}
        finally{$bootstrapInstallLock.Dispose()}
    }
    if([string]::IsNullOrWhiteSpace($SourceRoot)){throw 'BLOCKED / RELEASE_SOURCE_REQUIRED'}
    $verification=Test-NqCanonicalRelease $SourceRoot -ExpectedSourceCommit $ExpectedSourceCommit
    if(($ConfirmProduction -or $TestProductionPolicy) -and -not [bool]$verification.deployable){throw 'BLOCKED / NON_DEPLOYABLE_RELEASE_FORBIDDEN'}
    Assert-ReleaseAdmission $root ([IO.Path]::GetFullPath($SourceRoot)) ([string]$verification.releaseId)
    $id=[string]$verification.releaseId;$target=Get-ReleaseRoot $root $id
    if(Test-Path $target){throw 'BLOCKED / IMMUTABLE_RELEASE_ALREADY_EXISTS'}
    $staging=Join-Path $releasesRoot ('.install-'+$id+'-'+[Guid]::NewGuid().ToString('N'))
    try{Copy-Item $SourceRoot $staging -Recurse;$manifest=Get-Content (Join-Path $staging 'release-manifest.json') -Raw|ConvertFrom-Json;Set-Modes $staging $manifest;$null=Test-NqCanonicalRelease $staging -ExpectedSourceCommit $ExpectedSourceCommit -RequirePosix:($IsLinux);[IO.Directory]::Move($staging,$target);[pscustomobject][ordered]@{decision='PASS / NQ_CANONICAL_RELEASE_INSTALLED';releaseId=$id;releaseRoot=$target;deployable=[bool]$manifest.deployable;activated=$false;existingReleaseMutated=$false}}
    finally{if(Test-Path $staging){Remove-Item $staging -Recurse -Force}}
    exit 0
}
if($Action -eq 'verify'){if([string]::IsNullOrWhiteSpace($ReleaseId)){throw 'BLOCKED / RELEASE_ID_REQUIRED'};$null=Get-CurrentReleaseId $root;(Get-Release $root $ReleaseId).Verification;exit 0}

if($Action-ceq'test-hold-lock'-and-not$ConfirmDisposable){throw 'BLOCKED / TEST_CONTROL_FORBIDDEN_IN_PRODUCTION'}
$operationLock=Enter-ActivationOperationLock $root
try{
    if($Action-ceq'test-hold-lock'){
        [pscustomobject]@{decision='PASS / TEST_ACTIVATION_OPERATION_LOCK_HELD';installationIdentity=Get-InstallationIdentity $root;pid=$PID}
        exit 0
    }
    if($Action-ceq'bootstrap-current'){
        if([string]::IsNullOrWhiteSpace($ReleaseId)){throw 'BLOCKED / RELEASE_ID_REQUIRED'}
        $databaseState=Read-DatabaseState $root
        Invoke-CurrentBootstrap $root $ReleaseId $databaseState
        exit 0
    }
    $null=Get-CurrentReleaseId $root
    if($Action-ceq'recover'){Recover-Journal $root;exit 0}
    $null=Recover-Journal $root
    $databaseState=Read-DatabaseState $root
    if($Action -eq 'activate'){
        if([string]::IsNullOrWhiteSpace($ReleaseId)){throw 'BLOCKED / RELEASE_ID_REQUIRED'}
        Invoke-Activation $root $ReleaseId 'ACTIVATE' $databaseState
    }elseif($Action -eq 'rollback'){
        if(-not [string]::IsNullOrWhiteSpace($ReleaseId)){throw 'BLOCKED / CALLER_CONTROLLED_ROLLBACK_TARGET_FORBIDDEN'}
        $journal=Read-Signed $root (Get-JournalPath $root) 'nq-canonical-activation-journal.v2';$head=Read-ActivationHead $root
        if([string]$journal.state -cne 'COMPLETED' -or [string]$journal.previousReleaseId -ceq 'NONE' -or (Get-CurrentReleaseId $root) -cne [string]$journal.currentReleaseId){throw 'BLOCKED / TRUSTED_LAST_ACTIVATION_INVALID'}
        if([long]$journal.generation-ne[long]$head.generation-or[string]$journal.transactionId-cne[string]$head.transactionId-or[string]$journal.activationDigest-cne[string]$head.activationDigest){throw 'BLOCKED / STALE_ACTIVATION_AUTHORITY'}
        $previous=Get-Release $root ([string]$journal.previousReleaseId)
        if([string]$databaseState.currentSchemaVersion -cne [string]$previous.Manifest.requiredSchemaTarget){throw 'BLOCKED / DATABASE_RECOVERY_REQUIRED'}
        Invoke-Activation $root ([string]$journal.previousReleaseId) 'CODE_ROLLBACK' $databaseState
    }
}finally{if($null-ne$operationLock){$operationLock.Dispose()}}
