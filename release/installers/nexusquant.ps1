[CmdletBinding()]
param(
    [ValidateSet('install','start','stop','restart','backup','restore','uninstall')][string]$Action = 'start',
    [string]$InstallRoot = (Join-Path $env:USERPROFILE '.nexusquant'),
    [string]$PackageRoot = (Split-Path $PSScriptRoot -Parent),
    [string]$BackupPath,
    [switch]$PurgeData,
    [int]$FrontendPort = 18080,
    [int]$BackendPort = 18888,
    [ValidatePattern('^[a-z0-9][a-z0-9-]{0,48}$')][string]$ProjectName = 'nexusquant'
)
$ErrorActionPreference = 'Stop'
$InstallRoot = [IO.Path]::GetFullPath($InstallRoot).TrimEnd('\','/')
if ($InstallRoot -match '[
$#"'']') { throw 'Unsupported configuration directory characters' }
if ($InstallRoot -eq [IO.Path]::GetPathRoot($InstallRoot) -or $InstallRoot -eq [IO.Path]::GetFullPath($env:USERPROFILE)) { throw 'Unsafe installation directory' }
foreach ($part in @($InstallRoot, (Join-Path $InstallRoot 'data'), (Join-Path $InstallRoot 'backups'))) {
    if ((Test-Path -LiteralPath $part) -and ((Get-Item -LiteralPath $part).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Reparse installation paths are unsupported' }
}
function Invoke-Docker([string[]]$DockerArgs) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = 'docker'
    $info.Arguments = (($DockerArgs | ForEach-Object { '"' + $_.Replace('"','\"') + '"' }) -join ' ')
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    try {
        [void]$process.Start()
        $out = $process.StandardOutput.ReadToEndAsync()
        $err = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(600000)) { $process.Kill(); throw 'Docker operation timed out' }
        if ($process.ExitCode -ne 0) { throw ('Docker operation failed: ' + $err.Result) }
        return $out.Result.Trim()
    } finally { $process.Dispose() }
}
function Protect-Directory([string]$Path) {
    # 仅创建 DACL，避免继承 SACL 后 Set-Acl 要求当前进程不具备的审计权限。
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($identity,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
    $acl.AddAccessRule($rule)
    $directory = Get-Item -LiteralPath $Path
    if ($PSVersionTable.PSVersion.Major -ge 7) {
        [IO.FileSystemAclExtensions]::SetAccessControl($directory, $acl)
    } else { $directory.SetAccessControl($acl) }
}
function New-Secret {
    $bytes = New-Object byte[] 32
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes); return ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant() }
    finally { $rng.Dispose() }
}
function Write-Text([string]$Path, [string]$Text) { [IO.File]::WriteAllText($Path,$Text,(New-Object Text.UTF8Encoding($false))) }
function Compose([string[]]$Arguments) {
    Invoke-Docker (@('compose','--env-file',(Join-Path $InstallRoot 'config/runtime.env'),'-p',$ProjectName,'-f',(Join-Path $InstallRoot 'runtime/compose.yml')) + $Arguments)
}
function Read-Env([string]$Path) {
    $result = @{}
    Get-Content -LiteralPath $Path | ForEach-Object {
        if ($_ -match '^([A-Z][A-Z0-9_]*)=(.*)$') { if ($result.ContainsKey($Matches[1])) { throw 'Duplicate metadata field' }; $result[$Matches[1]] = $Matches[2] }
    }
    return $result
}
function Read-Config { Read-Env (Join-Path $InstallRoot 'config/runtime.env') }
function Check-Health {
    $config = Read-Config
    $result = Invoke-RestMethod -Uri ('http://127.0.0.1:' + $config.BACKEND_PORT + '/actuator/health') -TimeoutSec 10
    if ($result.status -ne 'UP') { throw 'Backend health is not UP' }
    [void](Invoke-WebRequest -UseBasicParsing -Uri ('http://127.0.0.1:' + $config.FRONTEND_PORT + '/') -TimeoutSec 10)
}
function Start-Runtime {
    [void](Compose @('up','-d','--no-build','--pull','never','--wait','--wait-timeout','300','postgres','backend'))
    [void](Compose @('up','-d','--no-build','--pull','never','--wait','--wait-timeout','120','frontend'))
    Check-Health
}
[void](Invoke-Docker @('info','--format','{{.ServerVersion}}'))
[void](Invoke-Docker @('compose','version','--short'))
[void](New-Item -ItemType Directory -Force -Path (Join-Path $InstallRoot 'runtime'))
Protect-Directory $InstallRoot
$lock = [IO.File]::Open((Join-Path $InstallRoot 'runtime/operation.lock'), [IO.FileMode]::OpenOrCreate, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
try {
if ($Action -eq 'install') {
    if ($FrontendPort -lt 1024 -or $FrontendPort -gt 65535 -or $BackendPort -lt 1024 -or $BackendPort -gt 65535 -or $FrontendPort -eq $BackendPort) { throw 'Invalid installation ports' }
    $versionPath = Join-Path $PackageRoot 'VERSION'
    if (-not (Test-Path $versionPath)) { $versionPath = Join-Path (Split-Path $PackageRoot -Parent) 'VERSION' }
    $version = (Get-Content -LiteralPath $versionPath -Raw).Trim()
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Invalid VERSION' }
    $arch = Invoke-Docker @('info','--format','{{.Architecture}}')
    if ($arch -in @('x86_64','amd64')) { $arch = 'amd64' } elseif ($arch -in @('aarch64','arm64')) { $arch = 'arm64' } else { throw 'Unsupported Docker architecture' }
    $archive = Join-Path $PackageRoot ('images-' + $arch + '.tar')
    $expected = (Get-Content -LiteralPath ($archive + '.sha256') -Raw).Trim()
    if ($expected -notmatch '^[a-f0-9]{64}$' -or (Get-FileHash $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expected) { throw 'Release image checksum mismatch' }
    [void](Invoke-Docker @('load','--input',$archive))
    foreach ($dir in @('config','data/postgres','logs','backups','runtime')) { [void](New-Item -ItemType Directory -Force -Path (Join-Path $InstallRoot $dir)) }
    Protect-Directory $InstallRoot
    $envPath = Join-Path $InstallRoot 'config/runtime.env'
    $fresh = -not (Test-Path -LiteralPath $envPath)
    if ($fresh) {
        Write-Text $envPath ("NQ_HOME=" + $InstallRoot.Replace('\','/') + "`nNQ_VERSION=$version`nDB_PASSWORD=$(New-Secret)`nJWT_SECRET=$(New-Secret)`nCREDENTIALS_KEY=$(New-Secret)`nFRONTEND_PORT=$FrontendPort`nBACKEND_PORT=$BackendPort`nPROJECT_NAME=$ProjectName`n")
    } else {
        $existing = Read-Config
        if ($existing.NQ_VERSION -ne $version -or $existing.PROJECT_NAME -ne $ProjectName) { throw 'Rerun requires identical version and project identity' }
    }
    Copy-Item -LiteralPath (Join-Path $PackageRoot 'runtime/compose.yml') -Destination (Join-Path $InstallRoot 'runtime/compose.yml')
    Copy-Item -LiteralPath (Join-Path $PackageRoot 'runtime/runtime.yml') -Destination (Join-Path $InstallRoot 'runtime/runtime.yml')
    Write-Text (Join-Path $InstallRoot 'runtime/VERSION') ($version + "`n")
    Copy-Item -LiteralPath $PSCommandPath -Destination (Join-Path $InstallRoot 'runtime/nexusquant.ps1') -Force
    Start-Runtime
    $admin = Compose @('exec','-T','postgres','psql','-U','nexusquant','-d','nexus_quant','-At','-c',"SELECT count(*) FROM users u WHERE username='admin' AND enabled AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code='ADMIN')")
    if ($admin -ne '1') { throw 'Bootstrap admin is unavailable; existing users were preserved' }
    if ($fresh) {
        $config = Read-Config
        $login = Invoke-RestMethod -Method Post -Uri ('http://127.0.0.1:' + $config.BACKEND_PORT + '/api/auth/login') -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}' -TimeoutSec 10
        if (-not $login.mustChangePassword) { throw 'Fresh bootstrap requires password change' }
        Write-Host 'Initial username: admin'
        Write-Host 'Initial password: 123456'
        Write-Host 'Password change required on first login.'
    } else { Write-Host 'Existing password and roles preserved.' }
    $config = Read-Config
    Write-Host ('NexusQuant ' + $version + ': http://127.0.0.1:' + $config.FRONTEND_PORT)
    return
}
$config = Read-Config
$ProjectName = $config.PROJECT_NAME
switch ($Action) {
    start { Start-Runtime }
    stop { [void](Compose @('stop','--timeout','30')) }
    restart { [void](Compose @('stop','--timeout','30')); Start-Runtime }
    backup {
        $schema = Compose @('exec','-T','postgres','psql','-U','nexusquant','-d','nexus_quant','-At','-c','SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1')
        if ($schema -ne '1') { throw 'Only V1 backups supported' }
        $timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')
        $dest = Join-Path $InstallRoot ('backups/' + $timestamp)
        [void](New-Item -ItemType Directory -Path $dest)
        Protect-Directory $dest
        $dumpPath = '/tmp/nexusquant-' + $timestamp + '.dump'
        try {
            [void](Compose @('exec','-T','postgres','pg_dump','-U','nexusquant','-d','nexus_quant','-Fc','-f',$dumpPath))
            $container = Compose @('ps','-q','postgres')
            [void](Invoke-Docker @('cp',($container + ':' + $dumpPath),(Join-Path $dest 'database.dump')))
        } finally { [void](Compose @('exec','-T','postgres','rm','-f',$dumpPath)) }
        Copy-Item -LiteralPath (Join-Path $InstallRoot 'config/runtime.env') -Destination (Join-Path $dest 'runtime.env')
        $manifest = @{FORMAT=1; APP_VERSION=$config.NQ_VERSION; SCHEMA_VERSION='1'; POSTGRESQL_MAJOR=16; TIMESTAMP=$timestamp; DATABASE_SHA256=(Get-FileHash (Join-Path $dest 'database.dump')).Hash.ToLowerInvariant(); CONFIG_SHA256=(Get-FileHash (Join-Path $dest 'runtime.env')).Hash.ToLowerInvariant()}
        Write-Text (Join-Path $dest 'manifest.env') ((($manifest.Keys | Sort-Object | ForEach-Object { $_ + '=' + $manifest[$_] }) -join "`n") + "`n")
        Write-Host ('Backup: ' + $dest)
    }
    restore {
        if (-not $BackupPath) { throw 'BackupPath required' }
        $manifest = Read-Env (Join-Path $BackupPath 'manifest.env')
        if ($manifest.FORMAT -ne '1' -or $manifest.APP_VERSION -ne $config.NQ_VERSION -or $manifest.SCHEMA_VERSION -ne '1' -or $manifest.POSTGRESQL_MAJOR -ne '16') { throw 'Incompatible backup' }
        foreach ($pair in @(@('database.dump','DATABASE_SHA256'),@('runtime.env','CONFIG_SHA256'))) {
            if ((Get-FileHash (Join-Path $BackupPath $pair[0])).Hash.ToLowerInvariant() -ne $manifest.($pair[1])) { throw 'Backup checksum mismatch' }
        }
        $restored = Read-Env (Join-Path $BackupPath 'runtime.env')
        foreach ($key in @('JWT_SECRET','CREDENTIALS_KEY')) {
            if ($restored[$key] -notmatch '^[a-f0-9]{64}$') { throw 'Invalid backup key metadata' }
            $config[$key] = $restored[$key]
        }
        [void](Compose @('stop','--timeout','30','frontend','backend'))
        [void](Compose @('up','-d','--no-build','--pull','never','--wait','--wait-timeout','120','postgres'))
        $container = Compose @('ps','-q','postgres')
        try {
            [void](Invoke-Docker @('cp',(Join-Path $BackupPath 'database.dump'),($container + ':/tmp/nexusquant-restore.dump')))
            [void](Compose @('exec','-T','postgres','pg_restore','-U','nexusquant','-d','nexus_quant','--clean','--if-exists','--no-owner','--exit-on-error','--single-transaction','/tmp/nexusquant-restore.dump'))
        } finally { [void](Compose @('exec','-T','postgres','rm','-f','/tmp/nexusquant-restore.dump')) }
        Write-Text (Join-Path $InstallRoot 'config/runtime.env') ((($config.Keys | Sort-Object | ForEach-Object { $_ + '=' + $config[$_] }) -join "`n") + "`n")
        Start-Runtime
    }
    uninstall {
        [void](Compose @('down','--timeout','30'))
        if ($PurgeData) {
            foreach ($name in @('data','backups')) {
                $target = [IO.Path]::GetFullPath((Join-Path $InstallRoot $name))
                if (-not $target.StartsWith($InstallRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe purge target' }
                if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target -Recurse -Force }
            }
        }
    }
}

} finally { $lock.Dispose() }
