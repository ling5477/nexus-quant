[CmdletBinding()]
param(
    [ValidateSet('install','start','stop','restart','status','doctor','backup','restore','uninstall','check-update','update','rollback')][string]$Action = 'start',
    [string]$InstallRoot = (Join-Path $env:USERPROFILE '.nexusquant'),
    [string]$PackageRoot = '',
    [string]$TargetPackageRoot,
    [string]$TrustedManifestSha256,
    [string]$BackupPath,
    [switch]$ConfirmUpdate,
    [switch]$ConfirmRollback,
    [switch]$PurgeData,
    [switch]$ConfirmPurge,
    [int]$FrontendPort = 18080,
    [int]$BackendPort = 18888,
    [ValidatePattern('^[a-z0-9][a-z0-9-]{0,48}$')][string]$ProjectName = 'nexusquant'
)
$ErrorActionPreference = 'Stop'
if (-not $PackageRoot) { $PackageRoot = Split-Path $PSScriptRoot -Parent }
function Assert-SafePath([string]$Path) {
    if ($Path -match '(^|[\\/])\.\.?([\\/]|$)') { throw 'Dot path segments are unsupported' }
    $absolute = [IO.Path]::GetFullPath($Path).TrimEnd('\','/')
    if ($absolute -match '[\r\n$#"''`]' -or $absolute -eq [IO.Path]::GetPathRoot($absolute).TrimEnd('\','/') -or $absolute -eq [IO.Path]::GetFullPath($env:USERPROFILE).TrimEnd('\','/')) { throw 'Unsafe installation or package directory' }
    $cursor = $absolute
    while ($cursor) {
        if ((Test-Path -LiteralPath $cursor) -and ((Get-Item -LiteralPath $cursor).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Reparse paths are unsupported' }
        $parent = [IO.Path]::GetDirectoryName($cursor)
        if ($parent -eq $cursor) { break }; $cursor = $parent
    }
    return $absolute
}
$InstallRoot = Assert-SafePath $InstallRoot
function Assert-NoReparseTree([string]$Path) {
    $queue = New-Object 'Collections.Generic.Queue[string]'; $queue.Enqueue($Path); $count = 0
    while ($queue.Count) {
        foreach ($item in Get-ChildItem -LiteralPath $queue.Dequeue() -Force) {
            if ((++$count) -gt 100000) { throw 'Purge tree exceeds safe inspection limit' }
            if ($item.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Reparse entry blocks purge' }
            if ($item.PSIsContainer) { $queue.Enqueue($item.FullName) }
        }
    }
}
function Remove-RuntimeEnvironment([Diagnostics.ProcessStartInfo]$Info) {
    # Compose 的 shell 环境优先于 --env-file；仅清洗子进程，避免越过安装身份或读取其他数据目录。
    $names = @('NQ_HOME','NQ_VERSION','SOURCE_SHA','RELEASE_SOURCE_HASH','SCHEMA_VERSION','PACKAGE_MANIFEST_SHA256','AUTO_UPDATE','ARCH','RUNTIME_PROFILE','BACKEND_IMAGE','FRONTEND_IMAGE','POSTGRES_IMAGE','BACKEND_MEMORY','BACKEND_CPUS','FRONTEND_MEMORY','FRONTEND_CPUS','POSTGRES_MEMORY','POSTGRES_CPUS','DB_PASSWORD','JWT_SECRET','CREDENTIALS_KEY','FRONTEND_PORT','BACKEND_PORT','PROJECT_NAME')
    foreach ($name in @($Info.EnvironmentVariables.Keys)) {
        if ($name -in $names -or $name -like 'COMPOSE_*' -or $name -eq 'DOCKER_DEFAULT_PLATFORM') { $Info.EnvironmentVariables.Remove($name) }
    }
}
function Invoke-External([string]$Executable, [string[]]$Arguments, [int]$TimeoutSeconds = 600, [switch]$CleanRuntimeEnvironment) {
    # 子进程 stderr 可能包含运行配置；只返回确定的阶段，不透传敏感内容。
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Executable
    $info.Arguments = (($Arguments | ForEach-Object { '"' + ([regex]::Replace([regex]::Replace($_, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1')) + '"' }) -join ' ')
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    if ($CleanRuntimeEnvironment) { Remove-RuntimeEnvironment $info }
    $process = New-Object Diagnostics.Process; $process.StartInfo = $info
    $safeFailure = 'unable to start'
    try {
        [void]$process.Start()
        $output = $process.StandardOutput.ReadToEndAsync(); $errorOutput = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit($TimeoutSeconds * 1000)) { $safeFailure='timeout'; $process.Kill(); throw 'External operation timed out' }
        if ($process.ExitCode -ne 0) { $safeFailure='exit code '+$process.ExitCode; throw 'External operation failed' }
        return $output.Result.Trim()
    } catch { throw ('External command stage failed: ' + [IO.Path]::GetFileName($Executable)+' ('+$safeFailure+')') }
    finally { $process.Dispose() }
}
function Invoke-Docker([string[]]$DockerArgs, [int]$TimeoutSeconds = 600) { Invoke-External $script:DockerExecutable $DockerArgs $TimeoutSeconds -CleanRuntimeEnvironment:($DockerArgs.Count -gt 0 -and $DockerArgs[0] -eq 'compose') }
function Protect-Directory([string]$Path) {
    $acl = New-Object Security.AccessControl.DirectorySecurity
    $acl.SetAccessRuleProtection($true, $false)
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.AddAccessRule((New-Object Security.AccessControl.FileSystemAccessRule($identity,'FullControl','ContainerInherit,ObjectInherit','None','Allow')))
    $directory = Get-Item -LiteralPath $Path
    if ($PSVersionTable.PSVersion.Major -ge 7) { [IO.FileSystemAclExtensions]::SetAccessControl($directory, $acl) } else { $directory.SetAccessControl($acl) }
}
function New-Secret {
    $bytes = New-Object byte[] 32; $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes); return ([BitConverter]::ToString($bytes)).Replace('-','').ToLowerInvariant() } finally { $rng.Dispose() }
}
function Write-Text([string]$Path, [string]$Text) {
    # 同目录原子替换加磁盘 flush；中断不能留下半份事务阶段或 VERSION。
    $temporary = $Path + '.' + [Guid]::NewGuid().ToString('N') + '.tmp'
    $bytes = (New-Object Text.UTF8Encoding($false)).GetBytes($Text)
    $stream = [IO.File]::Open($temporary, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
    try { $stream.Write($bytes,0,$bytes.Length); $stream.Flush($true) } finally { $stream.Dispose() }
    # Windows PowerShell 5 会把 string 参数的 $null 绑定为空路径；NullString 提供真正的 null，保留 .NET 的无备份原子替换。
    if (Test-Path -LiteralPath $Path) { [IO.File]::Replace($temporary,$Path,[NullString]::Value) } else { [IO.File]::Move($temporary,$Path) }
}
function Write-Metadata([string]$Path, [hashtable]$Values) { Write-Text $Path ((($Values.Keys | Sort-Object | ForEach-Object { $_ + '=' + $Values[$_] }) -join "`n") + "`n") }
function Parse-Metadata([string]$Text) {
    $result = @{}
    $lines = $Text -split '\r?\n'
    if ($lines.Count -gt 1 -and $lines[-1] -eq '') { $lines = $lines[0..($lines.Count-2)] }
    foreach ($line in $lines) {
        if ($line -notmatch '^([A-Z][A-Z0-9_]*)=([^\r\n]*)$' -or $result.ContainsKey($Matches[1])) { throw 'Invalid or duplicate metadata field' }
        $result[$Matches[1]] = $Matches[2]
    }
    return $result
}
function Read-Env([string]$Path) { Parse-Metadata ([IO.File]::ReadAllText($Path)) }
function Read-Config { Read-Env (Join-Path $InstallRoot 'config/runtime.env') }
function File-Hash([string]$Path) { (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant() }
function Assert-Hash([string]$Path, [string]$Expected) {
    if ($Expected -cnotmatch '^[a-f0-9]{64}$' -or (File-Hash $Path) -cne $Expected) { throw 'Release or backup checksum mismatch' }
}
function Get-HostArch {
    $value = $env:PROCESSOR_ARCHITEW6432; if (-not $value) { $value = $env:PROCESSOR_ARCHITECTURE }
    if ($value -eq 'AMD64') { return 'amd64' }; if ($value -eq 'ARM64') { return 'arm64' }; throw 'Unsupported Windows architecture'
}
function Read-Package([string]$Root, [string]$ExpectedHash, [string]$Arch) {
    $Root = Assert-SafePath $Root
    $path = Join-Path $Root ('package-' + $Arch + '.env')
    [void](Assert-SafePath $path)
    if ((Get-Item -LiteralPath $path).Length -gt 16384) { throw 'Package manifest exceeds size limit' }
    $bytes = [IO.File]::ReadAllBytes($path)
    if ($bytes.Length -gt 16384 -or ($bytes.Length -ge 3 -and $bytes[0] -eq 239 -and $bytes[1] -eq 187 -and $bytes[2] -eq 191)) { throw 'Package manifest size or encoding invalid' }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $manifestHash = ([BitConverter]::ToString($sha.ComputeHash($bytes))).Replace('-','').ToLowerInvariant() } finally { $sha.Dispose() }
    if ($ExpectedHash -and ($ExpectedHash -cnotmatch '^[a-f0-9]{64}$' -or $manifestHash -cne $ExpectedHash)) { throw 'Untrusted package manifest checksum' }
    $raw = (New-Object Text.UTF8Encoding($false,$true)).GetString($bytes)
    if ($raw.Contains("`r") -or -not $raw.EndsWith("`n",[StringComparison]::Ordinal)) { throw 'Package manifest must be UTF-8 LF metadata' }
    $metadata = Parse-Metadata $raw
    $fields = @('FORMAT','VERSION','SOURCE_SHA','RELEASE_SOURCE_HASH','SCHEMA_VERSION','ARCH','ARCHIVE','ARCHIVE_SHA256','BACKEND_IMAGE','FRONTEND_IMAGE','POSTGRES_IMAGE','BACKEND_CONFIG_DIGEST','FRONTEND_CONFIG_DIGEST','POSTGRES_CONFIG_DIGEST','COMPOSE_SHA256','RUNTIME_SHA256','PS_INSTALLER_SHA256','SH_INSTALLER_SHA256')
    if ($metadata.Count -ne $fields.Count) { throw 'Unknown or missing package manifest field' }
    foreach ($field in $fields) { if (-not $metadata.ContainsKey($field)) { throw 'Missing package manifest field' } }
    if ($metadata.FORMAT -cne '1' -or $metadata.VERSION -cnotmatch '^(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})$' -or $metadata.SOURCE_SHA -cnotmatch '^[a-f0-9]{40}$' -or $metadata.RELEASE_SOURCE_HASH -cnotmatch '^[a-f0-9]{64}$' -or $metadata.SCHEMA_VERSION -cnotmatch '^[1-9][0-9]{0,5}$' -or $metadata.ARCH -cne $Arch -or $metadata.ARCHIVE -cne ('images-' + $Arch + '.tar')) { throw 'Invalid release identity' }
    foreach ($key in @('BACKEND_IMAGE','FRONTEND_IMAGE','POSTGRES_IMAGE','BACKEND_CONFIG_DIGEST','FRONTEND_CONFIG_DIGEST','POSTGRES_CONFIG_DIGEST')) {
        if ($metadata[$key] -cnotmatch '^sha256:[a-f0-9]{64}$') { throw 'Exact declared immutable image identities required' }
    }
    foreach ($pair in @(@($metadata.ARCHIVE,'ARCHIVE_SHA256'),@('runtime/compose.yml','COMPOSE_SHA256'),@('runtime/runtime.yml','RUNTIME_SHA256'),@('installers/nexusquant.ps1','PS_INSTALLER_SHA256'),@('installers/nexusquant.sh','SH_INSTALLER_SHA256'))) {
        $file = Join-Path $Root $pair[0]; [void](Assert-SafePath $file); Assert-Hash $file $metadata[$pair[1]]
    }
    return @{Root=$Root; Manifest=$metadata; Hash=$manifestHash; Path=$path; Text=$raw}
}
function Set-PackageConfig([hashtable]$Config, [hashtable]$Package) {
    foreach ($field in @('VERSION','SOURCE_SHA','RELEASE_SOURCE_HASH','SCHEMA_VERSION')) {
        $key = $field; if ($field -eq 'VERSION') { $key = 'NQ_VERSION' }; $Config[$key] = $Package.Manifest[$field]
    }
    if (-not $Package.ResolvedImages) { throw 'Images must be verified before runtime configuration switch' }
    Assert-ImageMembership $Package.ResolvedImages $Package.Manifest
    foreach ($key in @('BACKEND_IMAGE','FRONTEND_IMAGE','POSTGRES_IMAGE')) { $Config[$key]=$Package.ResolvedImages[$key] }
    $Config.PACKAGE_MANIFEST_SHA256 = $Package.Hash; $Config.ARCH = $Package.Manifest.ARCH; $Config.AUTO_UPDATE = 'OFF'
}
function Assert-ImageMembership([hashtable]$Config, [hashtable]$Manifest) {
    foreach ($component in @('BACKEND','FRONTEND','POSTGRES')) {
        $imageKey=$component+'_IMAGE'; $configKey=$component+'_CONFIG_DIGEST'
        if ($Manifest[$imageKey] -cnotmatch '^sha256:[a-f0-9]{64}$' -or $Manifest[$configKey] -cnotmatch '^sha256:[a-f0-9]{64}$' -or @($Manifest[$imageKey],$Manifest[$configKey]) -cnotcontains $Config[$imageKey]) { throw 'Runtime image is outside the declared immutable identity pair' }
    }
}
function Install-Assets([hashtable]$Package) {
    foreach ($pair in @(@('runtime/compose.yml','compose.yml'),@('runtime/runtime.yml','runtime.yml'),@('installers/nexusquant.ps1','nexusquant.ps1'),@('installers/nexusquant.sh','nexusquant.sh'))) {
        Copy-Item -LiteralPath (Join-Path $Package.Root $pair[0]) -Destination (Join-Path $InstallRoot ('runtime/' + $pair[1])) -Force
    }
    foreach ($pair in @(@('compose.yml','COMPOSE_SHA256'),@('runtime.yml','RUNTIME_SHA256'),@('nexusquant.ps1','PS_INSTALLER_SHA256'),@('nexusquant.sh','SH_INSTALLER_SHA256'))) { Assert-Hash (Join-Path $InstallRoot ('runtime/' + $pair[0])) $Package.Manifest[$pair[1]] }
    Write-Text (Join-Path $InstallRoot 'runtime/package.env') $Package.Text
}
function Assert-Installed {
    $config = Read-Config; $manifestPath = Join-Path $InstallRoot 'runtime/package.env'
    Assert-Hash $manifestPath $config.PACKAGE_MANIFEST_SHA256
    $manifest = Read-Env $manifestPath
    foreach ($pair in @(@('compose.yml','COMPOSE_SHA256'),@('runtime.yml','RUNTIME_SHA256'),@('nexusquant.ps1','PS_INSTALLER_SHA256'),@('nexusquant.sh','SH_INSTALLER_SHA256'))) { Assert-Hash (Join-Path $InstallRoot ('runtime/' + $pair[0])) $manifest[$pair[1]] }
    foreach ($key in @('SOURCE_SHA','RELEASE_SOURCE_HASH','SCHEMA_VERSION','ARCH')) { if ($config[$key] -cne $manifest[$key]) { throw 'Installed runtime identity mismatch' } }
    Assert-ImageMembership $config $manifest
    if ($config.NQ_VERSION -cne $manifest.VERSION -or $config.AUTO_UPDATE -cne 'OFF' -or $config.NQ_HOME -cne $InstallRoot.Replace('\','/') -or $config.PROJECT_NAME -cnotmatch '^[a-z0-9][a-z0-9-]{0,48}$') { throw 'Installed configuration identity mismatch' }
    if ([IO.File]::ReadAllText((Join-Path $InstallRoot 'runtime/VERSION')).Trim() -cne $config.NQ_VERSION) { throw 'Committed installed VERSION identity mismatch' }
    return $config
}
function Assert-LocalDocker {
    # context inspect 仅读取本地元数据；不得为检查资格环境而连接远端 Docker。
    $endpoint = Invoke-Docker @('context','inspect','--format','{{.Endpoints.docker.Host}}') 15
    if ($env:DOCKER_HOST -and -not $env:DOCKER_CONTEXT) { $endpoint = $env:DOCKER_HOST }
    if ($endpoint -cnotmatch '^npipe:////\./pipe/(docker_engine|dockerDesktopLinuxEngine)$') { throw 'Windows qualification requires local Docker Desktop; remote Docker endpoints are rejected before connection' }
}
function Download-OfficialDocker([string]$Url, [string]$Destination) {
    $client = New-Object Net.WebClient
    $proxyValue = $env:HTTPS_PROXY; if (-not $proxyValue) { $proxyValue=$env:HTTP_PROXY }
    $direct = $false; $hostName=([Uri]$Url).DnsSafeHost
    foreach ($entry in ($env:NO_PROXY -split ',')) {
        $entry=$entry.Trim().TrimStart('.').ToLowerInvariant()
        if ($entry -eq '*' -or $entry -eq $hostName -or ($entry -and $hostName.EndsWith('.'+$entry,[StringComparison]::OrdinalIgnoreCase))) { $direct=$true }
    }
    try {
        if ($direct) {
            # 此请求的 bypass 不修改系统、Docker 或进程全局代理设置。
            $client.Proxy = $null
        } elseif ($proxyValue) {
            $proxyUri = New-Object Uri($proxyValue)
            if ($proxyUri.Scheme -notin @('http','https')) { throw 'Unsupported proxy transport' }
            $builder = New-Object UriBuilder($proxyUri); $builder.UserName=''; $builder.Password=''
            $proxy=New-Object Net.WebProxy($builder.Uri)
            if ($proxyUri.UserInfo) {
                $parts=$proxyUri.UserInfo.Split(':',2)
                $password=''; if ($parts.Count -gt 1) { $password=[Uri]::UnescapeDataString($parts[1]) }
                $proxy.Credentials=New-Object Net.NetworkCredential([Uri]::UnescapeDataString($parts[0]),$password)
            }
            $client.Proxy=$proxy
        }
        $download=$client.DownloadFileTaskAsync([Uri]$Url,$Destination)
        if (-not $download.Wait(600000)) { $client.CancelAsync(); throw 'Official download timed out' }
    } catch { throw 'Official Docker Desktop download failed; check detected proxy/network settings' }
    finally { $client.Dispose() }
}
function Ensure-Docker([bool]$AllowBootstrap) {
    $command = Get-Command docker -ErrorAction SilentlyContinue
    if ($command) { $script:DockerExecutable = $command.Source } else { $script:DockerExecutable = Join-Path $env:ProgramFiles 'Docker/Docker/resources/bin/docker.exe' }
    if (-not (Test-Path -LiteralPath $script:DockerExecutable)) {
        if (-not $AllowBootstrap) { throw 'Docker unavailable; install Docker Desktop from https://docs.docker.com/desktop/setup/install/windows-install/' }
        Write-Host 'Docker absent: downloading official Docker Desktop; UAC may be required.'
        $architecture = Get-HostArch
        $temporary = Assert-SafePath (Join-Path ([IO.Path]::GetTempPath()) ('nq-docker-' + [Guid]::NewGuid().ToString('N')))
        [void](New-Item -ItemType Directory -Path $temporary); Protect-Directory $temporary
        try {
            $installer = Join-Path $temporary 'Docker Desktop Installer.exe'
            Download-OfficialDocker ('https://desktop.docker.com/win/main/' + $architecture + '/Docker%20Desktop%20Installer.exe') $installer
            $signature = Get-AuthenticodeSignature -LiteralPath $installer
            if ($signature.Status -ne 'Valid' -or $signature.SignerCertificate.Subject -notmatch '(^|,\s*)O=Docker Inc(\.|,|$)') { throw 'Official Docker Desktop Authenticode verification failed' }
            $process = Start-Process -FilePath $installer -ArgumentList 'install','--accept-license','--quiet','--backend=wsl-2' -Verb RunAs -WindowStyle Hidden -PassThru
            try { if (-not $process.WaitForExit(900000)) { throw 'Docker Desktop installation timed out; complete pending Windows installation before retry' }; if ($process.ExitCode -ne 0) { throw 'Docker Desktop installation failed; restart Windows if requested' } } finally { $process.Dispose() }
        } finally {
            $verifiedTemporary = Assert-SafePath $temporary
            if (-not $verifiedTemporary.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\')+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe bootstrap cleanup path' }
            Remove-Item -LiteralPath $verifiedTemporary -Recurse -Force
        }
        if (-not (Test-Path -LiteralPath $script:DockerExecutable)) { throw 'Docker Desktop requires Windows restart or supported WSL2 components; complete official setup then rerun installer' }
    }
    Assert-LocalDocker
    try { $serverVersion = Invoke-Docker @('info','--format','{{.ServerVersion}}') 10 } catch {
        if (-not $AllowBootstrap) { throw 'Docker daemon unavailable; start Docker Desktop then retry' }
        $desktop = Join-Path $env:ProgramFiles 'Docker/Docker/Docker Desktop.exe'
        if (-not (Test-Path -LiteralPath $desktop)) { throw 'Docker installed but daemon stopped; start its supported daemon and rerun installer' }
        [void](Start-Process -FilePath $desktop -WindowStyle Hidden -PassThru)
        $deadline = [DateTime]::UtcNow.AddSeconds(300); $serverVersion = $null
        while ([DateTime]::UtcNow -lt $deadline) {
            try { $serverVersion = Invoke-Docker @('info','--format','{{.ServerVersion}}') 10; break } catch { Start-Sleep -Seconds 3 }
        }
        if (-not $serverVersion) { throw 'Docker daemon startup timed out; check Docker Desktop WSL2/virtualization and restart requirement' }
    }
    $composeVersion = Invoke-Docker @('compose','version','--short') 15
    if ($serverVersion -notmatch '^([0-9]+\.[0-9]+\.[0-9]+)' -or [version]$Matches[1] -lt [version]'24.0.0') { throw 'Docker Engine 24.0.0+ required; explicitly upgrade through official Docker Desktop before retry' }
    if ($composeVersion -notmatch '^v?([0-9]+\.[0-9]+\.[0-9]+)' -or [version]$Matches[1] -lt [version]'2.20.0') { throw 'Docker Compose 2.20.0+ required; explicitly upgrade through official Docker Desktop before retry' }
    $script:DockerVersion = $serverVersion; $script:ComposeVersion = $composeVersion
    $dockerArch = Invoke-Docker @('info','--format','{{.Architecture}}') 15
    if (($dockerArch -in @('amd64','x86_64') -and (Get-HostArch) -ne 'amd64') -or ($dockerArch -in @('arm64','aarch64') -and (Get-HostArch) -ne 'arm64') -or $dockerArch -notin @('amd64','x86_64','arm64','aarch64')) { throw 'Docker architecture does not match host package' }
    if ((Invoke-Docker @('info','--format','{{.OSType}}') 15) -ne 'linux') { throw 'Docker Desktop Linux containers required' }
}
function Select-Profile([double]$Cpu, [double]$RamGiB, [double]$DiskGiB) {
    foreach ($value in @($Cpu,$RamGiB,$DiskGiB)) { if ([double]::IsNaN($value) -or [double]::IsInfinity($value)) { throw 'Unknown capacity cannot select a runtime profile' } }
    if ($Cpu -lt 2 -or $RamGiB -lt 4 -or $DiskGiB -lt 10) { throw 'Requires at least 2 logical CPUs, 4 GiB Docker/host RAM and 10 GiB free disk' }
    if ($Cpu -lt 4 -or $RamGiB -lt 8) { return @{RUNTIME_PROFILE='LIGHT'; BACKEND_MEMORY='768m'; BACKEND_CPUS='1'; POSTGRES_MEMORY='512m'; POSTGRES_CPUS='0.5'; FRONTEND_MEMORY='128m'; FRONTEND_CPUS='0.25'} }
    if ($Cpu -lt 8 -or $RamGiB -lt 16) { return @{RUNTIME_PROFILE='STANDARD'; BACKEND_MEMORY='2048m'; BACKEND_CPUS='2'; POSTGRES_MEMORY='1024m'; POSTGRES_CPUS='1'; FRONTEND_MEMORY='256m'; FRONTEND_CPUS='0.5'} }
    return @{RUNTIME_PROFILE='PERFORMANCE'; BACKEND_MEMORY='4096m'; BACKEND_CPUS='4'; POSTGRES_MEMORY='2048m'; POSTGRES_CPUS='2'; FRONTEND_MEMORY='256m'; FRONTEND_CPUS='0.5'}
}
function Assert-HostBootstrap {
    $os = Get-CimInstance Win32_OperatingSystem -OperationTimeoutSec 10
    if ([version]$os.Version -lt [version]'10.0.19045') { throw 'Windows 10 22H2 or Windows 11 with supported Docker Desktop required' }
    $drive = New-Object IO.DriveInfo([IO.Path]::GetPathRoot($InstallRoot))
    if (-not $drive.IsReady -or $drive.DriveFormat -notin @('NTFS','ReFS') -or $InstallRoot.StartsWith('\\') -or $drive.AvailableFreeSpace -lt 10GB) { throw 'A local writable NTFS/ReFS installation directory with 10 GiB free space is required' }
    $system = Get-CimInstance Win32_ComputerSystem -OperationTimeoutSec 10
    [void](Select-Profile ([double]$system.NumberOfLogicalProcessors) ([double]$system.TotalPhysicalMemory / 1GB) ($drive.AvailableFreeSpace / 1GB))
}
function Host-Preflight {
    $os = Get-CimInstance Win32_OperatingSystem -OperationTimeoutSec 10
    $system = Get-CimInstance Win32_ComputerSystem -OperationTimeoutSec 10
    if ([version]$os.Version -lt [version]'10.0.19045') { throw 'Windows 10 22H2 or Windows 11 with supported Docker Desktop required' }
    $drive = New-Object IO.DriveInfo([IO.Path]::GetPathRoot($InstallRoot))
    if (-not $drive.IsReady -or $drive.DriveFormat -notin @('NTFS','ReFS') -or $InstallRoot.StartsWith('\\')) { throw 'A local writable NTFS/ReFS installation directory is required' }
    $disk = $drive.AvailableFreeSpace / 1GB
    $dockerCpu = [int](Invoke-Docker @('info','--format','{{.NCPU}}') 15)
    $dockerRam = [double](Invoke-Docker @('info','--format','{{.MemTotal}}') 15) / 1GB
    $cpu = [Math]::Min([double]$system.NumberOfLogicalProcessors, $dockerCpu)
    $ram = [Math]::Min([double]$system.TotalPhysicalMemory / 1GB, $dockerRam)
    $virtualization = 'UNKNOWN'
    try { $processors = @(Get-CimInstance Win32_Processor -OperationTimeoutSec 10); if ($processors.VirtualizationFirmwareEnabled -contains $true -or $system.HypervisorPresent) { $virtualization='AVAILABLE' } else { $virtualization='UNAVAILABLE' } } catch {}
    if ($virtualization -eq 'UNAVAILABLE') { throw 'Hardware virtualization unavailable; enable supported Windows virtualization before Docker installation' }
    $proxy = @{}
    foreach ($name in @('HTTP_PROXY','HTTPS_PROXY','NO_PROXY')) { $proxy[$name] = [bool][Environment]::GetEnvironmentVariable($name) }
    $systemProxy = 'UNKNOWN'
    try { $systemProxy = [bool](Get-ItemPropertyValue -LiteralPath 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Internet Settings' -Name ProxyEnable -ErrorAction Stop) } catch {}
    $facts = @{OS=$os.Caption; OS_VERSION=$os.Version; ARCH=(Get-HostArch); HOST_CPU=$system.NumberOfLogicalProcessors; HOST_RAM_GIB=[Math]::Round($system.TotalPhysicalMemory/1GB,2); DOCKER_CPU=$dockerCpu; DOCKER_RAM_GIB=[Math]::Round($dockerRam,2); FREE_DISK_GIB=[Math]::Round($disk,2); VIRTUALIZATION=$virtualization; DOCKER=$script:DockerVersion; COMPOSE=$script:ComposeVersion; HTTP_PROXY_PRESENT=$proxy.HTTP_PROXY; HTTPS_PROXY_PRESENT=$proxy.HTTPS_PROXY; NO_PROXY_PRESENT=$proxy.NO_PROXY; SYSTEM_PROXY_PRESENT=$systemProxy; FILESYSTEM=$drive.DriveFormat}
    Write-Host ('Host preflight: ' + ($facts | ConvertTo-Json -Compress))
    return (Select-Profile $cpu $ram $disk)
}
function Assert-Ports([int]$Front, [int]$Back) {
    if ($Front -lt 1024 -or $Front -gt 65535 -or $Back -lt 1024 -or $Back -gt 65535 -or $Front -eq $Back) { throw 'Invalid installation ports' }
    foreach ($port in @($Front,$Back)) {
        $listener = New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,$port)
        try { $listener.Start() } catch { throw ('Loopback port ' + $port + ' occupied; choose -FrontendPort / -BackendPort overrides') } finally { $listener.Stop() }
    }
}
function Compose([string[]]$Arguments) { Invoke-Docker (@('compose','--env-file',(Join-Path $InstallRoot 'config/runtime.env'),'-p',$ProjectName,'-f',(Join-Path $InstallRoot 'runtime/compose.yml')) + $Arguments) }
function Read-Schema { Compose @('exec','-T','postgres','psql','-U','nexusquant','-d','nexus_quant','-At','-v','ON_ERROR_STOP=1','-c','SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1') }
function Check-Health {
    $config = Read-Config
    try {
        $result = Invoke-RestMethod -Uri ('http://127.0.0.1:' + $config.BACKEND_PORT + '/actuator/health') -TimeoutSec 10
        if ($result.status -ne 'UP') { throw 'Health not UP' }
        [void](Invoke-WebRequest -UseBasicParsing -Uri ('http://127.0.0.1:' + $config.FRONTEND_PORT + '/') -TimeoutSec 10)
    } catch { throw 'Runtime backend/frontend health smoke failed' }
    $rejected = $false
    try { [void](Invoke-WebRequest -UseBasicParsing -Uri ('http://127.0.0.1:' + $config.BACKEND_PORT + '/api/auth/me') -TimeoutSec 10 -ErrorAction Stop) }
    catch { if ($_.Exception.Response -and [int]$_.Exception.Response.StatusCode -in @(401,403)) { $rejected = $true } }
    if (-not $rejected) { throw 'Unauthenticated API smoke did not reject access' }
    if ((Read-Schema) -cne $config.SCHEMA_VERSION) { throw 'Runtime schema does not match package' }
    $pgMajor = Compose @('exec','-T','postgres','psql','-U','nexusquant','-d','nexus_quant','-At','-v','ON_ERROR_STOP=1','-c',"SELECT current_setting('server_version_num')::int / 10000")
    if ($pgMajor -ne '16') { throw 'PostgreSQL 16 required' }
}
function Assert-RuntimePorts([hashtable]$Config) {
    foreach ($pair in @(@('frontend',$Config.FRONTEND_PORT),@('backend',$Config.BACKEND_PORT))) {
        $port = 0
        if (-not [int]::TryParse($pair[1],[ref]$port) -or $port -lt 1024 -or $port -gt 65535) { throw 'Invalid stored loopback port' }
        # Docker Desktop 停止容器后可能保留转发预留；host bind 的 10048 不能证明其他进程占用。
        # 只拒绝真实回环或通配监听；无监听时由 Docker 原子绑定及既有有界健康校验决定结果。
        $listeners = @([Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners() | Where-Object {
            $_.Port -eq $port -and ($_.Address.Equals([Net.IPAddress]::Loopback) -or $_.Address.Equals([Net.IPAddress]::Any) -or $_.Address.Equals([Net.IPAddress]::IPv6Any))
        })
        if ($listeners.Count -gt 0) {
            $container = Compose @('ps','-q',$pair[0])
            if (-not $container -or (Invoke-Docker @('port',$container,'8080/tcp') 15) -cne ('127.0.0.1:'+$port)) { throw ('Loopback port '+$port+' occupied by another process; preserve it and choose installer port overrides') }
        }
    }
}
function Start-Runtime([switch]$ForceRecreate) {
    $config = Read-Config
    Assert-RuntimePorts $config
    if ($ForceRecreate) {
        [void](Compose @('up','-d','--no-build','--pull','never','--wait','--wait-timeout','120','postgres'))
        [void](Compose @('up','-d','--no-build','--pull','never','--no-deps','--force-recreate','--wait','--wait-timeout','300','backend'))
        [void](Compose @('up','-d','--no-build','--pull','never','--no-deps','--force-recreate','--wait','--wait-timeout','120','frontend'))
    } else {
        [void](Compose @('up','-d','--no-build','--pull','never','--wait','--wait-timeout','300','postgres','backend'))
        [void](Compose @('up','-d','--no-build','--pull','never','--wait','--wait-timeout','120','frontend'))
    }
    Check-Health
}
function Verify-Images([hashtable]$Manifest) {
    $resolved=@{}
    foreach ($component in @('BACKEND','FRONTEND','POSTGRES')) {
        $key=$component+'_IMAGE'; $configKey=$component+'_CONFIG_DIGEST'
        # 两种 store 的查询身份不同；只尝试可信清单已声明的两个 ID，绝不接受 tag 或其他观察值。
        foreach ($candidate in @($Manifest[$key],$Manifest[$configKey])) {
            if ($candidate -cnotmatch '^sha256:[a-f0-9]{64}$') { throw 'Invalid declared immutable image identity' }
            try { $identity = Invoke-Docker @('image','inspect','--format','{{.Id}}|{{.Os}}|{{.Architecture}}',$candidate) 30 } catch { continue }
            if ($identity -cne ($candidate+'|linux|'+$Manifest.ARCH)) { throw 'Declared image identity or platform mismatch' }
            $resolved[$key]=$candidate; break
        }
        if (-not $resolved.ContainsKey($key)) { throw 'Neither declared immutable image identity is available after archive load' }
    }
    Assert-ImageMembership $resolved $Manifest
    $pgVersion = Invoke-Docker @('run','--rm','--network','none','--entrypoint','postgres',$resolved.POSTGRES_IMAGE,'--version') 60
    if ($pgVersion -notmatch '^postgres \(PostgreSQL\) 16\.') { throw 'Verified package requires PostgreSQL 16 image' }
    return $resolved
}
function Load-PackageImages([hashtable]$Package) {
    $archive = Join-Path $Package.Root $Package.Manifest.ARCHIVE
    [void](Assert-SafePath $archive)
    # Windows read sharing 阻止验证后被覆写/替换，Docker CLI 可并行读取已锁定归档。
    $stream = [IO.File]::Open($archive,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $digest = ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','').ToLowerInvariant()
        if ($digest -cne $Package.Manifest.ARCHIVE_SHA256) { throw 'Release archive changed after manifest verification' }
        [void](Invoke-Docker @('load','--input',$archive))
        $Package.ResolvedImages=Verify-Images $Package.Manifest
    } finally { $sha.Dispose(); $stream.Dispose() }
}
function New-Backup {
    $config = Assert-Installed; $schema = Read-Schema
    if ($schema -cne $config.SCHEMA_VERSION) { throw 'Backup schema identity mismatch' }
    $timestamp = [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)
    $destination = Join-Path $InstallRoot ('backups/' + $timestamp)
    [void](New-Item -ItemType Directory -Path $destination); Protect-Directory $destination
    $dumpPath = '/tmp/nexusquant-' + $timestamp + '.dump'
    try {
        [void](Compose @('exec','-T','postgres','pg_dump','-U','nexusquant','-d','nexus_quant','-Fc','-f',$dumpPath))
        $container = Compose @('ps','-q','postgres')
        [void](Invoke-Docker @('cp',($container + ':' + $dumpPath),(Join-Path $destination 'database.dump')))
    } finally { [void](Compose @('exec','-T','postgres','rm','-f',$dumpPath)) }
    Copy-Item -LiteralPath (Join-Path $InstallRoot 'config/runtime.env') -Destination (Join-Path $destination 'runtime.env')
    [void](New-Item -ItemType Directory -Path (Join-Path $destination 'assets'))
    foreach ($name in @('compose.yml','runtime.yml','nexusquant.ps1','nexusquant.sh','package.env','VERSION')) { Copy-Item -LiteralPath (Join-Path $InstallRoot ('runtime/' + $name)) -Destination (Join-Path $destination ('assets/' + $name)) }
    Write-Metadata (Join-Path $destination 'manifest.env') @{FORMAT='1'; APP_VERSION=$config.NQ_VERSION; SCHEMA_VERSION=$schema; POSTGRESQL_MAJOR='16'; TIMESTAMP=$timestamp; DATABASE_SHA256=(File-Hash (Join-Path $destination 'database.dump')); CONFIG_SHA256=(File-Hash (Join-Path $destination 'runtime.env')); PACKAGE_MANIFEST_SHA256=$config.PACKAGE_MANIFEST_SHA256; VERSION_SHA256=(File-Hash (Join-Path $destination 'assets/VERSION'))}
    return $destination
}
function Validate-Backup([string]$Path) {
    $Path = Assert-SafePath $Path; $manifest = Read-Env (Join-Path $Path 'manifest.env')
    $keys = @('FORMAT','APP_VERSION','SCHEMA_VERSION','POSTGRESQL_MAJOR','TIMESTAMP','DATABASE_SHA256','CONFIG_SHA256','PACKAGE_MANIFEST_SHA256','VERSION_SHA256')
    if ($manifest.Count -ne $keys.Count) { throw 'Invalid backup metadata' }; foreach ($key in $keys) { if (-not $manifest.ContainsKey($key)) { throw 'Missing backup metadata' } }
    if ($manifest.FORMAT -ne '1' -or $manifest.APP_VERSION -notmatch '^(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]{0,5})$' -or $manifest.SCHEMA_VERSION -notmatch '^[1-9][0-9]{0,5}$' -or $manifest.POSTGRESQL_MAJOR -ne '16') { throw 'Incompatible backup' }
    foreach ($pair in @(@('database.dump','DATABASE_SHA256'),@('runtime.env','CONFIG_SHA256'),@('assets/package.env','PACKAGE_MANIFEST_SHA256'),@('assets/VERSION','VERSION_SHA256'))) { [void](Assert-SafePath (Join-Path $Path $pair[0])); Assert-Hash (Join-Path $Path $pair[0]) $manifest[$pair[1]] }
    $package = Read-Env (Join-Path $Path 'assets/package.env')
    foreach ($pair in @(@('compose.yml','COMPOSE_SHA256'),@('runtime.yml','RUNTIME_SHA256'),@('nexusquant.ps1','PS_INSTALLER_SHA256'),@('nexusquant.sh','SH_INSTALLER_SHA256'))) { [void](Assert-SafePath (Join-Path $Path ('assets/' + $pair[0]))); Assert-Hash (Join-Path $Path ('assets/' + $pair[0])) $package[$pair[1]] }
    $saved = Read-Env (Join-Path $Path 'runtime.env')
    Assert-ImageMembership $saved $package
    foreach ($key in @('JWT_SECRET','CREDENTIALS_KEY','DB_PASSWORD')) { if ($saved[$key] -cnotmatch '^[a-f0-9]{64}$') { throw 'Invalid protected backup configuration' } }
    if ($saved.NQ_VERSION -cne $manifest.APP_VERSION -or $saved.SCHEMA_VERSION -cne $manifest.SCHEMA_VERSION -or $saved.PACKAGE_MANIFEST_SHA256 -cne $manifest.PACKAGE_MANIFEST_SHA256 -or $package.VERSION -cne $saved.NQ_VERSION -or $package.SCHEMA_VERSION -cne $saved.SCHEMA_VERSION) { throw 'Backup identity mismatch' }
    if ([IO.File]::ReadAllText((Join-Path $Path 'assets/VERSION')).Trim() -cne $saved.NQ_VERSION) { throw 'Backup committed VERSION mismatch' }
    return @{Path=$Path; Manifest=$manifest; Config=$saved; Package=$package}
}
function Restore-Database([string]$Path) {
    [void](Compose @('stop','--timeout','30','frontend','backend'))
    [void](Compose @('up','-d','--no-build','--pull','never','--wait','--wait-timeout','120','postgres'))
    $container = Compose @('ps','-q','postgres')
    try {
        [void](Invoke-Docker @('cp',(Join-Path $Path 'database.dump'),($container + ':/tmp/nexusquant-restore.dump')))
        # clean 只处理旧 dump 中的对象，必须重建隔离应用库才能移除目标版本新增对象。
        [void](Compose @('exec','-T','postgres','psql','-U','nexusquant','-d','postgres','-v','ON_ERROR_STOP=1','-c',"SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='nexus_quant' AND pid<>pg_backend_pid()"))
        [void](Compose @('exec','-T','postgres','dropdb','-U','nexusquant','--if-exists','nexus_quant'))
        [void](Compose @('exec','-T','postgres','createdb','-U','nexusquant','-O','nexusquant','-T','template0','nexus_quant'))
        [void](Compose @('exec','-T','postgres','pg_restore','-U','nexusquant','-d','nexus_quant','--no-owner','--exit-on-error','--single-transaction','/tmp/nexusquant-restore.dump'))
    } finally { [void](Compose @('exec','-T','postgres','rm','-f','/tmp/nexusquant-restore.dump')) }
}
function Confirm-Choice([switch]$Confirmed, [string]$Word) {
    if (-not $Confirmed -and (Read-Host ('Type ' + $Word + ' to confirm')) -cne $Word) { throw 'Operation not confirmed' }
}
function Diagnostic-Value([scriptblock]$ReadValue) { try { & $ReadValue } catch { return 'UNKNOWN' } }
function Save-Phase([string]$Directory, [hashtable]$Record, [string]$Phase) { $Record.PHASE=$Phase; Write-Metadata (Join-Path $Directory 'phase.env') $Record }
function Get-Transaction([string]$Id) {
    if ($Id -cnotmatch '^[0-9]{8}T[0-9]{9}Z-[a-f0-9]{8}$') { throw 'Invalid transaction identity' }
    $directory = Join-Path $InstallRoot ('runtime/transactions/' + $Id); [void](Assert-SafePath $directory)
    $record = Read-Env (Join-Path $directory 'phase.env')
    $fields = @('FORMAT','FROM_VERSION','TO_VERSION','STARTED_AT','COMPLETED_AT','SOURCE_MANIFEST_SHA256','TARGET_MANIFEST_SHA256','BACKUP_ID','BACKUP_MANIFEST_SHA256','QUIESCED_AT','SCHEMA_BEFORE','SCHEMA_AFTER','SOURCE_BACKEND_IMAGE','SOURCE_FRONTEND_IMAGE','SOURCE_POSTGRES_IMAGE','BACKEND_IMAGE','FRONTEND_IMAGE','POSTGRES_IMAGE','RESULT','ROLLBACK_RESULT','PHASE')
    if ($record.Count -ne $fields.Count -or $record.FORMAT -cne '1') { throw 'Invalid transaction record' }
    foreach ($field in $fields) { if (-not $record.ContainsKey($field)) { throw 'Missing transaction record field' } }
    if ($record.BACKUP_ID -cnotmatch '^[0-9]{8}T[0-9]{9}Z-[a-f0-9]{8}$') { throw 'Invalid transaction backup identity' }
    $backup = Validate-Backup (Join-Path $InstallRoot ('backups/' + $record.BACKUP_ID))
    if ($record.SOURCE_MANIFEST_SHA256 -cne $backup.Manifest.PACKAGE_MANIFEST_SHA256 -or (File-Hash (Join-Path $backup.Path 'manifest.env')) -cne $record.BACKUP_MANIFEST_SHA256 -or $backup.Config.NQ_HOME -cne $InstallRoot.Replace('\','/') -or $backup.Config.PROJECT_NAME -cne $ProjectName) { throw 'Transaction backup identity mismatch' }
    if ($backup.Config.NQ_VERSION -cne $record.FROM_VERSION -or $backup.Config.SCHEMA_VERSION -cne $record.SCHEMA_BEFORE) { throw 'Transaction source identity mismatch' }
    foreach ($key in @('BACKEND_IMAGE','FRONTEND_IMAGE','POSTGRES_IMAGE')) { if ($record['SOURCE_'+$key] -cne $backup.Config[$key]) { throw 'Transaction source digest mismatch' } }
    Assert-Hash (Join-Path $directory 'target.env') $record.TARGET_MANIFEST_SHA256
    $target = Read-Env (Join-Path $directory 'target.env')
    if ($target.VERSION -cne $record.TO_VERSION) { throw 'Transaction target identity mismatch' }
    Assert-ImageMembership $record $target
    return @{Directory=$directory; Record=$record; Backup=$backup; Target=$target}
}
function Stop-ProjectApps {
    if ($ProjectName -cnotmatch '^[a-z0-9][a-z0-9-]{0,48}$') { throw 'Invalid project identity for recovery' }
    foreach ($service in @('backend','frontend')) {
        $containers = Invoke-Docker @('ps','-q','--filter',('label=com.docker.compose.project='+$ProjectName),'--filter',('label=com.docker.compose.service='+$service)) 15
        if ($containers) {
            foreach ($container in ($containers -split '\r?\n')) {
                if ($container -cnotmatch '^[a-f0-9]{12,64}$') { throw 'Invalid application container identity' }
                [void](Invoke-Docker @('stop','--time','30',$container) 60)
            }
        }
    }
}
function Recover-Transaction([hashtable]$Transaction) {
    $record=$Transaction.Record; $backup=$Transaction.Backup
    # 不依赖可能写到一半的 compose，先按已验证项目/服务标签停止当前应用。
    Stop-ProjectApps
    $resolved=Verify-Images $backup.Package
    foreach ($name in @('compose.yml','runtime.yml','nexusquant.ps1','nexusquant.sh','package.env','VERSION')) { Copy-Item -LiteralPath (Join-Path $backup.Path ('assets/' + $name)) -Destination (Join-Path $InstallRoot ('runtime/' + $name)) -Force }
    $restoredConfig=$backup.Config.Clone()
    foreach ($key in @('BACKEND_IMAGE','FRONTEND_IMAGE','POSTGRES_IMAGE')) { $restoredConfig[$key]=$resolved[$key] }
    Write-Metadata (Join-Path $InstallRoot 'config/runtime.env') $restoredConfig
    [void](Compose @('stop','--timeout','30','frontend','backend'))
    # DB_MAY_CHANGE 在后端启动前落盘；该阶段之后恢复完整 dump，绝不 repair。
    if ($record.PHASE -ne 'PREPARED') { Restore-Database $backup.Path }
    [void](Assert-Installed); Start-Runtime -ForceRecreate
    $record.COMPLETED_AT=[DateTime]::UtcNow.ToString('o'); $record.ROLLBACK_RESULT='SUCCESS'; $record.RESULT='ROLLED_BACK'; $record.SCHEMA_AFTER=$backup.Manifest.SCHEMA_VERSION
    Save-Phase $Transaction.Directory $record 'ROLLED_BACK'
    Write-Metadata (Join-Path $Transaction.Directory 'rollback-receipt.env') $record
    $pendingPath=Join-Path $InstallRoot 'runtime/pending-update'; if (Test-Path -LiteralPath $pendingPath) { Remove-Item -LiteralPath $pendingPath -Force }
}
$lock = $null
try {
    foreach ($part in @('runtime','config','data','data/postgres','backups','logs','config/runtime.env','runtime/compose.yml','runtime/runtime.yml','runtime/package.env','runtime/VERSION','runtime/nexusquant.ps1','runtime/nexusquant.sh','runtime/pending-update','runtime/pending-restore.env','runtime/first-install.env','runtime/last-update')) { [void](Assert-SafePath (Join-Path $InstallRoot $part)) }
    [void](New-Item -ItemType Directory -Force -Path (Join-Path $InstallRoot 'runtime'))
    Protect-Directory $InstallRoot
    $lock=[IO.File]::Open((Join-Path $InstallRoot 'runtime/operation.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    $pendingPath=Join-Path $InstallRoot 'runtime/pending-update'
    if ((Test-Path -LiteralPath $pendingPath) -and $Action -ne 'rollback') { throw 'Interrupted update pending: run rollback with explicit confirmation; runtime remains fail-closed' }
    $pendingRestorePath=Join-Path $InstallRoot 'runtime/pending-restore.env'
    if ((Test-Path -LiteralPath $pendingRestorePath) -and $Action -ne 'restore') { throw 'Interrupted database restore pending: rerun restore with the same verified BackupPath before other lifecycle actions' }
    if ($Action -eq 'install') {
        $package=Read-Package $PackageRoot '' (Get-HostArch)
        $newConfig=-not (Test-Path -LiteralPath (Join-Path $InstallRoot 'config/runtime.env'))
        $firstInstallPath=Join-Path $InstallRoot 'runtime/first-install.env'
        $firstInstallPending=Test-Path -LiteralPath $firstInstallPath
        if ($firstInstallPending) {
            $firstInstall=Read-Env $firstInstallPath
            if ($firstInstall.Count -ne 2 -or $firstInstall.FORMAT -cne '1' -or $firstInstall.PACKAGE_MANIFEST_SHA256 -cne $package.Hash) { throw 'Incomplete first installation requires the same verified package' }
        }
        if ($newConfig -and -not $firstInstallPending -and (Test-Path -LiteralPath (Join-Path $InstallRoot 'data/postgres'))) { throw 'Existing data directory without runtime identity; restore protected installation metadata before continuing' }
        $fresh=$newConfig -or $firstInstallPending
        if (-not $newConfig) {
            if ($firstInstallPending) { $config=Read-Config } else { $config=Assert-Installed }
            if ($config.PACKAGE_MANIFEST_SHA256 -cne $package.Hash -or $config.PROJECT_NAME -cne $ProjectName -or $config.NQ_HOME -cne $InstallRoot.Replace('\','/')) { throw 'Rerun requires identical package and project identity; use explicit update' }
            foreach ($field in @('SOURCE_SHA','RELEASE_SOURCE_HASH','SCHEMA_VERSION','ARCH')) { if ($config[$field] -cne $package.Manifest[$field]) { throw 'Pending installation runtime identity mismatch' } }
            Assert-ImageMembership $config $package.Manifest
            if ($config.NQ_VERSION -cne $package.Manifest.VERSION -or $config.AUTO_UPDATE -cne 'OFF') { throw 'Pending installation version identity mismatch' }
        }
        Assert-HostBootstrap
        Ensure-Docker $true
        $profile=Host-Preflight
        if ($newConfig) { Assert-Ports $FrontendPort $BackendPort }
        Load-PackageImages $package
        if ($newConfig -and -not $firstInstallPending) { Write-Metadata $firstInstallPath @{FORMAT='1'; PACKAGE_MANIFEST_SHA256=$package.Hash} }
        foreach ($directory in @('config','data/postgres','logs','backups','runtime/transactions')) { [void](New-Item -ItemType Directory -Force -Path (Join-Path $InstallRoot $directory)) }
        if ($newConfig) {
            $config=@{NQ_HOME=$InstallRoot.Replace('\','/'); DB_PASSWORD=(New-Secret); JWT_SECRET=(New-Secret); CREDENTIALS_KEY=(New-Secret); FRONTEND_PORT="$FrontendPort"; BACKEND_PORT="$BackendPort"; PROJECT_NAME=$ProjectName}
            foreach ($key in $profile.Keys) { $config[$key]=$profile[$key] }; Set-PackageConfig $config $package
            Write-Metadata (Join-Path $InstallRoot 'config/runtime.env') $config
        }
        Install-Assets $package
        Write-Text (Join-Path $InstallRoot 'runtime/VERSION') ($package.Manifest.VERSION + "`n")
        Start-Runtime
        $admin=Compose @('exec','-T','postgres','psql','-U','nexusquant','-d','nexus_quant','-At','-v','ON_ERROR_STOP=1','-c',"SELECT count(*) FROM users u WHERE username='admin' AND enabled AND EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=u.id AND r.role_code='ADMIN')")
        if ($admin -ne '1') { throw 'Bootstrap admin unavailable; existing users preserved' }
        if ($fresh) {
            $mustChange=Compose @('exec','-T','postgres','psql','-U','nexusquant','-d','nexus_quant','-At','-v','ON_ERROR_STOP=1','-c',"SELECT must_change_password::text FROM users WHERE username='admin'")
            if ($firstInstallPending -and -not $newConfig -and $mustChange -eq 'false') { Write-Host 'Existing changed administrator password preserved after incomplete installer retry.' }
            else {
                try { $login=Invoke-RestMethod -Method Post -Uri ('http://127.0.0.1:'+$config.BACKEND_PORT+'/api/auth/login') -ContentType 'application/json' -Body '{"username":"admin","password":"123456"}' -TimeoutSec 10 } catch { throw 'Fresh administrator login smoke failed' }
                if (-not $login.mustChangePassword) { throw 'Fresh bootstrap requires password change' }
                Write-Host 'Initial username: admin'; Write-Host 'Initial password: 123456'; Write-Host 'Password change required on first login.'
            }
        } else { Write-Host 'Existing password, roles and runtime profile preserved.' }
        if (Test-Path -LiteralPath $firstInstallPath) { Remove-Item -LiteralPath $firstInstallPath -Force }
        Write-Host ('NexusQuant '+$config.NQ_VERSION+': http://127.0.0.1:'+$config.FRONTEND_PORT); return
    }
    if ($Action -eq 'rollback') {
        $config=Read-Config; $ProjectName=$config.PROJECT_NAME
        $idPath=Join-Path $InstallRoot 'runtime/last-update'; if (Test-Path -LiteralPath $pendingPath) { $idPath=$pendingPath }
        if (-not (Test-Path -LiteralPath $idPath)) { throw 'No known update transaction to rollback' }
        $transaction=Get-Transaction ([IO.File]::ReadAllText($idPath).Trim())
        if ($transaction.Record.PHASE -notin @('PREPARED','DB_MAY_CHANGE','SUCCESS','FAILED_ROLLBACK')) { throw 'Transaction is not rollback eligible' }
        if ($transaction.Record.PHASE -eq 'SUCCESS') {
            Assert-Hash (Join-Path $transaction.Directory 'receipt.env') (File-Hash (Join-Path $transaction.Directory 'phase.env'))
            if ($config.PACKAGE_MANIFEST_SHA256 -cne $transaction.Record.TARGET_MANIFEST_SHA256) { throw 'Installed version does not match known update receipt' }
        }
        Confirm-Choice -Confirmed:$ConfirmRollback -Word 'ROLLBACK'; Ensure-Docker $false
        Write-Text $pendingPath ((Split-Path $transaction.Directory -Leaf)+"`n")
        try { Recover-Transaction $transaction } catch {
            $transaction.Record.RESULT='ROLLBACK_FAILED'; $transaction.Record.ROLLBACK_RESULT='FAILED'; $transaction.Record.COMPLETED_AT=[DateTime]::UtcNow.ToString('o'); $transaction.Record.SCHEMA_AFTER='UNKNOWN'
            Save-Phase $transaction.Directory $transaction.Record 'FAILED_ROLLBACK'
            Write-Metadata (Join-Path $transaction.Directory ('failure-'+[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')+'.env')) $transaction.Record
            throw 'Rollback failed; transaction retained, runtime remains blocked'
        }; Write-Host 'ROLLBACK_SUCCESS'; return
    }
    $config=Assert-Installed; $ProjectName=$config.PROJECT_NAME
    if ($Action -eq 'check-update' -or $Action -eq 'update') {
        if (-not $TargetPackageRoot -or $TrustedManifestSha256 -cnotmatch '^[a-f0-9]{64}$') { throw 'TargetPackageRoot and independently trusted TrustedManifestSha256 required' }
        $target=Read-Package $TargetPackageRoot $TrustedManifestSha256 $config.ARCH
        $version=[IO.File]::ReadAllText((Join-Path $InstallRoot 'runtime/VERSION')).Trim()
        if ($version -cne $config.NQ_VERSION) { throw 'Installed VERSION mismatch' }
        $available=[version]$target.Manifest.VERSION -gt [version]$version
        if ($Action -eq 'check-update') { Write-Host ('Current: '+$version+'; available: '+$target.Manifest.VERSION+'; updateAvailable='+$available+'; AUTO_UPDATE=OFF'); return }
        $installedManifest=Read-Env (Join-Path $InstallRoot 'runtime/package.env')
        if (-not $available -or [int]$target.Manifest.SCHEMA_VERSION -lt [int]$config.SCHEMA_VERSION -or $target.Manifest.POSTGRES_CONFIG_DIGEST -cne $installedManifest.POSTGRES_CONFIG_DIGEST) { throw 'Update requires newer version, monotonic schema and unchanged verified PG16 configuration digest' }
        Confirm-Choice -Confirmed:$ConfirmUpdate -Word 'UPDATE'; Ensure-Docker $false
        [void](Host-Preflight)
        $id=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')+'-'+[Guid]::NewGuid().ToString('N').Substring(0,8)
        $directory=Join-Path $InstallRoot ('runtime/transactions/'+$id); [void](New-Item -ItemType Directory -Path $directory)
        Write-Text (Join-Path $directory 'target.env') $target.Text
        $record=@{FORMAT='1'; FROM_VERSION=$config.NQ_VERSION; TO_VERSION=$target.Manifest.VERSION; STARTED_AT=[DateTime]::UtcNow.ToString('o'); COMPLETED_AT='PENDING'; SOURCE_MANIFEST_SHA256=$config.PACKAGE_MANIFEST_SHA256; TARGET_MANIFEST_SHA256=$target.Hash; BACKUP_ID='NOT_CREATED'; BACKUP_MANIFEST_SHA256='NOT_CREATED'; QUIESCED_AT='NOT_QUIESCED'; SCHEMA_BEFORE=$config.SCHEMA_VERSION; SCHEMA_AFTER=$target.Manifest.SCHEMA_VERSION; SOURCE_BACKEND_IMAGE=$config.BACKEND_IMAGE; SOURCE_FRONTEND_IMAGE=$config.FRONTEND_IMAGE; SOURCE_POSTGRES_IMAGE=$config.POSTGRES_IMAGE; BACKEND_IMAGE=$target.Manifest.BACKEND_IMAGE; FRONTEND_IMAGE=$target.Manifest.FRONTEND_IMAGE; POSTGRES_IMAGE=$target.Manifest.POSTGRES_IMAGE; RESULT='PENDING'; ROLLBACK_RESULT='NOT_REQUIRED'}
        $quiesced=$false
        try {
            Load-PackageImages $target
            foreach ($key in @('BACKEND_IMAGE','FRONTEND_IMAGE','POSTGRES_IMAGE')) { $record[$key]=$target.ResolvedImages[$key] }
            $quiesced=$true
            [void](Compose @('stop','--timeout','30','frontend','backend'))
            $record.QUIESCED_AT=[DateTime]::UtcNow.ToString('o')
            $backupPath=New-Backup; [void](Validate-Backup $backupPath)
            $record.BACKUP_ID=Split-Path $backupPath -Leaf; $record.BACKUP_MANIFEST_SHA256=File-Hash (Join-Path $backupPath 'manifest.env')
            Save-Phase $directory $record 'PREPARED'; Write-Text $pendingPath ($id+"`n")
            Install-Assets $target; Set-PackageConfig $config $target; Write-Metadata (Join-Path $InstallRoot 'config/runtime.env') $config
            Save-Phase $directory $record 'DB_MAY_CHANGE'
            Start-Runtime -ForceRecreate
            Write-Text (Join-Path $InstallRoot 'runtime/VERSION') ($target.Manifest.VERSION+"`n")
            $record.COMPLETED_AT=[DateTime]::UtcNow.ToString('o'); $record.RESULT='UPDATE_SUCCESS'
            Save-Phase $directory $record 'SUCCESS'; Write-Metadata (Join-Path $directory 'receipt.env') $record
            Write-Text (Join-Path $InstallRoot 'runtime/last-update') ($id+"`n"); Remove-Item -LiteralPath $pendingPath -Force
        } catch {
            if (-not (Test-Path -LiteralPath $pendingPath)) {
                $record.COMPLETED_AT=[DateTime]::UtcNow.ToString('o'); $record.RESULT='UPDATE_FAILED_BEFORE_DB'; $record.ROLLBACK_RESULT='NOT_REQUIRED'; $record.SCHEMA_AFTER=$config.SCHEMA_VERSION
                Write-Metadata (Join-Path $directory 'failure-receipt.env') $record
                if ($quiesced) { Start-Runtime }
                throw 'Update failed before database change; previous database untouched'
            }
            $transaction=Get-Transaction $id
            try { Recover-Transaction $transaction } catch {
                $record.RESULT='UPDATE_FAILED'; $record.ROLLBACK_RESULT='FAILED'; $record.COMPLETED_AT=[DateTime]::UtcNow.ToString('o'); $record.SCHEMA_AFTER='UNKNOWN'
                Save-Phase $directory $record 'FAILED_ROLLBACK'; Write-Metadata (Join-Path $directory 'failure-receipt.env') $record
                throw 'Update failed and automatic rollback failed; explicit rollback required, runtime remains blocked'
            }
            throw 'Update failed; verified previous application and database restored'
        }
        Write-Host 'UPDATE_SUCCESS'; return
    }
    try { Ensure-Docker ($Action -in @('start','restart')) } catch {
        if ($Action -in @('status','doctor')) { Write-Host ('VERSION='+$config.NQ_VERSION+'; Docker=UNAVAILABLE; service/schema/security state=UNKNOWN; AUTO_UPDATE=OFF'); return }
        throw
    }
    switch ($Action) {
        start { Start-Runtime }
        stop { [void](Compose @('stop','--timeout','30')) }
        restart { [void](Compose @('stop','--timeout','30')); Start-Runtime }
        status {
            foreach ($service in @('postgres','backend','frontend')) {
                $container=Compose @('ps','-q','--all',$service)
                if (-not $container) { Write-Host ($service+': NOT_CREATED'); continue }
                $state=Invoke-Docker @('inspect','--format','{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}UNKNOWN{{end}}',$container) 15
                Write-Host ($service+': '+$state+'; image='+$config[($service.ToUpperInvariant()+'_IMAGE')])
            }
            Write-Host ('VERSION='+$config.NQ_VERSION+'; SOURCE_SHA='+$config.SOURCE_SHA+'; AUTO_UPDATE=OFF')
        }
        doctor {
            [void](Diagnostic-Value { Host-Preflight })
            Write-Host ('Installed version='+$config.NQ_VERSION+'; profile='+$config.RUNTIME_PROFILE+'; schema='+(Diagnostic-Value { Read-Schema }))
            $postgres=Diagnostic-Value { Compose @('exec','-T','postgres','psql','-U','nexusquant','-d','nexus_quant','-At','-v','ON_ERROR_STOP=1','-c','SELECT version()') }; Write-Host ('PostgreSQL='+$postgres)
            foreach ($service in @('postgres','backend','frontend')) { $container=Diagnostic-Value { Compose @('ps','-q',$service) }; if ($container -and $container -ne 'UNKNOWN') { Write-Host ($service+'='+(Diagnostic-Value { Invoke-Docker @('inspect','--format','{{.State.Status}} {{if .State.Health}}{{.State.Health.Status}}{{else}}UNKNOWN{{end}}',$container) 15 })) } else { Write-Host ($service+'=NOT_RUNNING_OR_UNKNOWN') } }
            $kill=Diagnostic-Value { Compose @('exec','-T','postgres','psql','-U','nexusquant','-d','nexus_quant','-At','-v','ON_ERROR_STOP=1','-c',"SELECT coalesce((SELECT status FROM kill_switch_states WHERE scope='GLOBAL_TRADING'),'UNKNOWN')") }; if ($kill -notin @('ENGAGED','DISENGAGED')) { $kill='UNKNOWN' }; Write-Host ('kill='+$kill)
            foreach ($key in @('NQ_CONTINUOUS_SIM_ENABLED','NQ_LIVE_ENABLED','NQ_REAL_EXCHANGE_ENABLED','NQ_REAL_PROVIDER_ENABLED','NQ_REAL_CLIENT_ENABLED')) { $value=Diagnostic-Value { Compose @('exec','-T','backend','printenv',$key) }; if ($value -notin @('true','false')) { $value='UNKNOWN' }; Write-Host ($key+'='+$value) }
            $availability='UNKNOWN'; if ($TargetPackageRoot -and $TrustedManifestSha256) { $target=Read-Package $TargetPackageRoot $TrustedManifestSha256 $config.ARCH; $availability=[version]$target.Manifest.VERSION -gt [version]$config.NQ_VERSION }; Write-Host ('updateAvailable='+$availability+'; AUTO_UPDATE=OFF')
        }
        backup { Write-Host ('Backup: '+(New-Backup)) }
        restore {
            if (-not $BackupPath) { throw 'BackupPath required' }; $backup=Validate-Backup $BackupPath
            if ($backup.Manifest.APP_VERSION -cne $config.NQ_VERSION -or $backup.Manifest.SCHEMA_VERSION -cne $config.SCHEMA_VERSION -or $backup.Manifest.PACKAGE_MANIFEST_SHA256 -cne $config.PACKAGE_MANIFEST_SHA256) { throw 'Normal restore requires identical version, schema and package identity' }
            $backupManifestHash=File-Hash (Join-Path $backup.Path 'manifest.env')
            if (Test-Path -LiteralPath $pendingRestorePath) {
                $restoreRecord=Read-Env $pendingRestorePath
                if ($restoreRecord.BACKUP_PATH -cne $backup.Path -or $restoreRecord.BACKUP_MANIFEST_SHA256 -cne $backupManifestHash) { throw 'Pending restore requires original verified backup' }
            }
            # DROP/CREATE 期间停止业务且持久化恢复标记，避免失败后 start 把空库重新初始化。
            Write-Metadata $pendingRestorePath @{FORMAT='1'; BACKUP_PATH=$backup.Path; BACKUP_MANIFEST_SHA256=$backupManifestHash; PHASE='DB_MAY_CHANGE'}
            Restore-Database $backup.Path
            foreach ($key in @('JWT_SECRET','CREDENTIALS_KEY')) { $config[$key]=$backup.Config[$key] }
            Write-Metadata (Join-Path $InstallRoot 'config/runtime.env') $config; Start-Runtime -ForceRecreate
            Remove-Item -LiteralPath $pendingRestorePath -Force
        }
        uninstall {
            if ($PurgeData) { Confirm-Choice -Confirmed:$ConfirmPurge -Word 'DELETE' }
            [void](Compose @('down','--timeout','30'))
            if ($PurgeData) {
                foreach ($name in @('data','backups')) {
                    $target=Assert-SafePath (Join-Path $InstallRoot $name)
                    if (-not $target.StartsWith($InstallRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Unsafe purge target' }
                    if (Test-Path -LiteralPath $target) { Assert-NoReparseTree $target; Remove-Item -LiteralPath $target -Recurse -Force }
                }
                Write-Metadata (Join-Path $InstallRoot 'runtime/first-install.env') @{FORMAT='1'; PACKAGE_MANIFEST_SHA256=$config.PACKAGE_MANIFEST_SHA256}
            }
        }
    }
} finally { if ($lock) { $lock.Dispose() } }
