"""PowerShell 正式函数的 AST 隔离测试；不代表 Windows 安装资格证据。"""
import os
from pathlib import Path
import shutil
import socket
import subprocess
import tempfile
import unittest

from test_installer import REPO, digest, make_package


class PowerShellLifecycleTest(unittest.TestCase):
    def setUp(self):
        self.shell = shutil.which('powershell') or shutil.which('pwsh')
        if not self.shell or os.name != 'nt':
            self.skipTest('Windows PowerShell function regression requires Windows')
        self.temp = tempfile.TemporaryDirectory(prefix='nq-ps-functions-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.package = self.root / 'package'
        self.manifest = make_package(self.package)
        self.runner = self.root / 'functions.ps1'
        self.runner.write_text('''$ErrorActionPreference='Stop'
$tokens=$null; $errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($env:NQ_PS_SOURCE,[ref]$tokens,[ref]$errors)
if ($errors.Count) { throw 'Production PowerShell parse failed' }
$ast.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst]},$false) | ForEach-Object {
    . ([scriptblock]::Create($_.Extent.Text))
}
. ([scriptblock]::Create([IO.File]::ReadAllText($env:NQ_PS_EXPRESSION)))
''', encoding='utf-8', newline='\n')

    def evaluate(self, expression, success=True, **env):
        expr = self.root / 'expression.ps1'
        expr.write_text(expression, encoding='utf-8', newline='\n')
        child_env = os.environ | {'NQ_PS_SOURCE': str(REPO / 'release/installers/nexusquant.ps1'),
                                   'NQ_PS_EXPRESSION': str(expr), 'NQ_PS_PACKAGE': str(self.package),
                                   'NQ_PS_TRUST': digest(self.manifest)} | env
        # PS7宿主的模块路径不能污染PS5函数验证进程。
        if Path(self.shell).name.lower() == 'powershell.exe':
            child_env['PSModulePath'] = str(Path(os.environ['SystemRoot']) / 'System32/WindowsPowerShell/v1.0/Modules')
        result = subprocess.run([self.shell, '-NoProfile', '-NonInteractive', '-ExecutionPolicy', 'Bypass',
                                 '-File', str(self.runner)], env=child_env, capture_output=True,
                                text=True, encoding='utf-8', errors='replace', timeout=20)
        self.assertEqual(success, result.returncode == 0, result.stderr[-1200:])
        return result

    def test_all_profiles_boundaries_and_resources(self):
        expression = '''$cases=@(@(2,4,10,'LIGHT','768m','512m','128m'),
@(4,8,10,'STANDARD','2048m','1024m','256m'),@(8,16,10,'PERFORMANCE','4096m','2048m','256m'),
@(8,7.99,10,'LIGHT','768m','512m','128m'),@(7,16,10,'STANDARD','2048m','1024m','256m'))
foreach($case in $cases) {
    $profile=Select-Profile $case[0] $case[1] $case[2]
    if ($profile.RUNTIME_PROFILE -ne $case[3] -or $profile.BACKEND_MEMORY -ne $case[4] -or $profile.POSTGRES_MEMORY -ne $case[5] -or $profile.FRONTEND_MEMORY -ne $case[6]) { throw 'Profile boundary/resource regression' }
}
foreach($case in @(@(1,4,10),@(2,3.99,10),@(2,4,9.99),@([double]::NaN,4,10),
@([double]::PositiveInfinity,4,10),@(2,[double]::NaN,10),@(2,4,[double]::PositiveInfinity))) {
    $rejected=$false; try { $null=Select-Profile $case[0] $case[1] $case[2] } catch { $rejected=$true }
    if(-not $rejected) { throw 'Insufficient capacity accepted' }
}
'''
        self.evaluate(expression)

    def test_actual_powershell_atomic_writer_creates_and_replaces_runtime_metadata(self):
        self.evaluate('''$directory=Join-Path $env:NQ_PS_PACKAGE 'atomic writer proof'
[void][IO.Directory]::CreateDirectory($directory)
$version=Join-Path $directory 'VERSION'
Write-Text $version "1.0.0`n"
Write-Text $version "1.0.1`n"
if([IO.File]::ReadAllText($version) -cne "1.0.1`n") { throw 'Existing VERSION was not atomically replaced' }
$metadata=Join-Path $directory 'phase.env'
Write-Metadata $metadata @{FORMAT='1';PHASE='PENDING';TEXT='初始资格事实'}
Write-Metadata $metadata @{FORMAT='1';PHASE='DB_MAY_CHANGE';TEXT='保留完整资格事实'}
Write-Metadata $metadata @{FORMAT='1';PHASE='SUCCESS';TEXT='完整覆盖已完成'}
$expected="FORMAT=1`nPHASE=SUCCESS`nTEXT=完整覆盖已完成`n"
$actual=[IO.File]::ReadAllBytes($metadata)
$expectedBytes=(New-Object Text.UTF8Encoding($false)).GetBytes($expected)
if([Convert]::ToBase64String($actual) -cne [Convert]::ToBase64String($expectedBytes)) {
    throw 'Metadata overwrite changed UTF-8 encoding, newlines, ordering, or content'
}
if(@(Get-ChildItem -LiteralPath $directory -Filter '*.tmp').Count -ne 0) {
    throw 'Successful atomic replacement left a pending temporary file'
}
''')

    def test_actual_powershell_atomic_writer_locked_target_preserves_original(self):
        self.evaluate('''$target=Join-Path $env:NQ_PS_PACKAGE 'locked-target.env'
Write-Text $target "FORMAT=1`nPHASE=OLD_COMPLETE`n"
$original=[IO.File]::ReadAllBytes($target)
$handle=[IO.File]::Open($target,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::None)
$rejected=$false
try { try { Write-Text $target "FORMAT=1`nPHASE=NEW_COMPLETE`n" } catch { $rejected=$true } }
finally { $handle.Dispose() }
if(-not $rejected) { throw 'Locked target overwrite falsely reported success' }
if([Convert]::ToBase64String([IO.File]::ReadAllBytes($target)) -cne [Convert]::ToBase64String($original)) {
    throw 'Failed atomic replacement damaged prior complete metadata'
}
''')

    def test_package_parser_success_and_strict_negative_fields(self):
        expression = '$null=Read-Package $env:NQ_PS_PACKAGE $env:NQ_PS_TRUST amd64'
        self.evaluate(expression)
        original = self.manifest.read_bytes()
        cases = (original + b'FORMAT=1\n', original + b'UNKNOWN=1\n',
                 original + b'BACKEND_CONFIG_DIGEST=sha256:' + b'4' * 64 + b'\n',
                 original.replace(b'BACKEND_CONFIG_DIGEST=sha256:' + b'4' * 64 + b'\n', b''),
                 original.replace(b'BACKEND_CONFIG_DIGEST=sha256:' + b'4' * 64,
                                  b'BACKEND_CONFIG_DIGEST=synthetic:mutable'),
                 original.replace(b'FORMAT=1\n', b''), original.replace(b'\n', b'\r\n'),
                 b'\xef\xbb\xbf' + original, original.rstrip(b'\n'),
                 original.replace(b'ARCHIVE=images-amd64.tar', b'ARCHIVE=../images-amd64.tar'),
                 original.replace(b'BACKEND_IMAGE=sha256:' + b'a' * 64, b'BACKEND_IMAGE=postgres:latest'))
        for index, raw in enumerate(cases):
            with self.subTest(case=index):
                self.manifest.write_bytes(raw)
                self.evaluate(expression, success=False)
        self.manifest.write_bytes(original)
        self.evaluate(expression, success=False, NQ_PS_TRUST='0' * 64)
        (self.package / 'images-amd64.tar').write_bytes(b'TAMPERED')
        self.evaluate(expression, success=False)

    def test_dual_store_resolver_and_membership_use_only_declared_pairs(self):
        self.evaluate('''$script:FixtureManifest=(Read-Package $env:NQ_PS_PACKAGE $env:NQ_PS_TRUST amd64).Manifest
function Invoke-Docker([string[]]$DockerArgs,[int]$TimeoutSeconds=600) {
    $candidate=@($DockerArgs | Where-Object { $_ -match '^sha256:' })[0]
    $allowed=if($script:FixtureStore -eq 'CLASSIC') { @($script:FixtureManifest.BACKEND_CONFIG_DIGEST,$script:FixtureManifest.FRONTEND_CONFIG_DIGEST,$script:FixtureManifest.POSTGRES_CONFIG_DIGEST) } else { @($script:FixtureManifest.BACKEND_IMAGE,$script:FixtureManifest.FRONTEND_IMAGE,$script:FixtureManifest.POSTGRES_IMAGE) }
    if($candidate -notin $allowed) { throw 'Fixture identity is not addressable in this image store' }
    if($DockerArgs[0] -eq 'run') { return 'postgres (PostgreSQL) 16.15' }
    if($script:WrongObservedIdentity) { return ('sha256:'+('e'*64)+'|linux|amd64') }
    return ($candidate+'|linux|amd64')
}
foreach($store in @('CONTAINERD','CLASSIC')) {
    $script:FixtureStore=$store; $script:WrongObservedIdentity=$false
    $resolved=Verify-Images $script:FixtureManifest
    $expected=if($store -eq 'CLASSIC') { $script:FixtureManifest.BACKEND_CONFIG_DIGEST } else { $script:FixtureManifest.BACKEND_IMAGE }
    if($resolved.BACKEND_IMAGE -cne $expected) { throw 'Resolver did not use the available declared identity' }
    $config=@{}; foreach($key in $script:FixtureManifest.Keys) { $config[$key]=$script:FixtureManifest[$key] }
    foreach($key in $resolved.Keys) { $config[$key]=$resolved[$key] }
    Assert-ImageMembership $config $script:FixtureManifest
    $config.BACKEND_IMAGE='sha256:'+('e'*64)
    $rejected=$false; try { Assert-ImageMembership $config $script:FixtureManifest } catch { $rejected=$true }
    if(-not $rejected) { throw 'Runtime identity outside the declared pair accepted' }
}
$script:FixtureStore='CONTAINERD'; $script:WrongObservedIdentity=$true
$rejected=$false; try { $null=Verify-Images $script:FixtureManifest } catch { $rejected=$true }
if(-not $rejected) { throw 'Observed identity mismatch accepted' }
''')

    def test_safe_paths_and_live_port_occupant_are_preserved(self):
        sock = socket.socket()
        self.addCleanup(sock.close)
        sock.bind(('127.0.0.1', 0)); sock.listen()
        self.evaluate('Assert-Ports ([int]$env:NQ_PS_PORT) 65534', success=False,
                      NQ_PS_PORT=str(sock.getsockname()[1]))
        self.assertEqual(0, sock.getsockopt(socket.SOL_SOCKET, socket.SO_ERROR))
        self.evaluate('$null=Assert-SafePath ([IO.Path]::GetPathRoot($env:NQ_PS_PACKAGE))', success=False)
        self.evaluate('$null=Assert-SafePath ($env:NQ_PS_PACKAGE + "/../other")', success=False)

    def test_compose_child_environment_cleans_identity_and_preserves_host_settings(self):
        self.evaluate('''$env:DOCKER_DEFAULT_PLATFORM='synthetic-parent-platform'
$info=New-Object Diagnostics.ProcessStartInfo
$info.EnvironmentVariables['NQ_HOME']='synthetic-untrusted'
$info.EnvironmentVariables['BACKEND_IMAGE']='synthetic:untrusted'
$info.EnvironmentVariables['DB_PASSWORD']='synthetic-untrusted'
$info.EnvironmentVariables['COMPOSE_FILE']='synthetic-untrusted'
$info.EnvironmentVariables['DOCKER_DEFAULT_PLATFORM']='synthetic-parent-platform'
$info.EnvironmentVariables['HTTP_PROXY']='synthetic-proxy'
$info.EnvironmentVariables['DOCKER_HOST']='synthetic-docker'
Remove-RuntimeEnvironment $info
foreach($name in @('NQ_HOME','BACKEND_IMAGE','DB_PASSWORD','COMPOSE_FILE','DOCKER_DEFAULT_PLATFORM')) {
    if($info.EnvironmentVariables.ContainsKey($name)) { throw ('Ambient runtime override survived: '+$name) }
}
if($info.EnvironmentVariables['HTTP_PROXY'] -ne 'synthetic-proxy' -or $info.EnvironmentVariables['DOCKER_HOST'] -ne 'synthetic-docker') { throw 'Unrelated host setting changed' }
if($env:DOCKER_DEFAULT_PLATFORM -cne 'synthetic-parent-platform') { throw 'Parent Docker platform changed' }
''')

    def test_existing_runtime_reservation_defers_to_docker_and_bind_failure_stops_health(self):
        sockets = [socket.socket(), socket.socket()]
        for sock in sockets:
            self.addCleanup(sock.close)
            # 只 bind 不 listen，真实制造 host 不能绑定但没有 LISTEN 的保留端口。
            sock.bind(('127.0.0.1', 0))
        self.evaluate('''$script:FixtureConfig=@{FRONTEND_PORT=$env:NQ_PS_FRONT;BACKEND_PORT=$env:NQ_PS_BACK}
$probe=New-Object Net.Sockets.TcpListener([Net.IPAddress]::Loopback,[int]$env:NQ_PS_FRONT)
$hostBindRejected=$false
try { $probe.Start() } catch { $hostBindRejected=$true } finally { $probe.Stop() }
if(-not $hostBindRejected) { throw 'Reservation fixture failed to block host bind' }
function Read-Config { return $script:FixtureConfig }
function Compose([string[]]$Arguments) {
    if($Arguments[0] -cne 'up' -or '--wait' -notin $Arguments -or '--wait-timeout' -notin $Arguments) {
        throw 'Existing runtime did not defer to bounded Docker bind authority'
    }
    $script:UpCalls++
    if($script:FailDockerBind) { throw 'Fixture Docker actual bind failure' }
}
function Invoke-Docker([string[]]$DockerArgs,[int]$TimeoutSeconds=600) { throw 'No LISTEN permits no ownership lookup' }
function Check-Health { $script:HealthCalls++ }
$script:UpCalls=0; $script:HealthCalls=0; $script:FailDockerBind=$false
Start-Runtime
if($script:UpCalls -ne 2 -or $script:HealthCalls -ne 1) { throw 'Reservation blocked bounded runtime start' }
$script:UpCalls=0; $script:HealthCalls=0; $script:FailDockerBind=$true
$rejected=$false; try { Start-Runtime } catch { $rejected=$true }
if(-not $rejected -or $script:UpCalls -ne 1 -or $script:HealthCalls -ne 0) {
    throw 'Docker bind failure reached health or successful completion'
}
''', NQ_PS_FRONT=str(sockets[0].getsockname()[1]), NQ_PS_BACK=str(sockets[1].getsockname()[1]))

    def test_existing_runtime_preserves_true_external_listener_before_docker_up(self):
        external = socket.socket()
        backend = socket.socket()
        self.addCleanup(external.close)
        self.addCleanup(backend.close)
        external.bind(('127.0.0.1', 0)); external.listen()
        backend.bind(('127.0.0.1', 0))
        self.evaluate('''function Read-Config { return @{FRONTEND_PORT=$env:NQ_PS_FRONT;BACKEND_PORT=$env:NQ_PS_BACK} }
$script:UpCalls=0; $script:HealthCalls=0
function Compose([string[]]$Arguments) {
    if($Arguments[0] -eq 'ps') { return '' }
    $script:UpCalls++; throw 'Docker must not start while a foreign LISTEN is present'
}
function Check-Health { $script:HealthCalls++ }
$message=''; try { Start-Runtime } catch { $message=$_.Exception.Message }
if($message -notlike '*occupied by another process*' -or $script:UpCalls -ne 0 -or $script:HealthCalls -ne 0) {
    throw 'External listener was not rejected before any start operation'
}
''', NQ_PS_FRONT=str(external.getsockname()[1]), NQ_PS_BACK=str(backend.getsockname()[1]))
        self.assertEqual(0, external.getsockopt(socket.SOL_SOCKET, socket.SO_ERROR))

    def test_existing_runtime_allows_only_running_own_exact_published_port(self):
        published = socket.socket()
        backend = socket.socket()
        self.addCleanup(published.close)
        self.addCleanup(backend.close)
        published.bind(('127.0.0.1', 0)); published.listen()
        backend.bind(('127.0.0.1', 0))
        self.evaluate('''function Read-Config { return @{FRONTEND_PORT=$env:NQ_PS_FRONT;BACKEND_PORT=$env:NQ_PS_BACK} }
function Compose([string[]]$Arguments) {
    if($Arguments[0] -eq 'ps') {
        if($Arguments.Count -ne 3 -or $Arguments[1] -cne '-q' -or $Arguments[2] -cne 'frontend') {
            throw 'Ownership proof must use running service in the already bound Compose project'
        }
        return 'fixture-owned-running-container'
    }
    if($Arguments[0] -cne 'up') { throw 'Unexpected runtime command' }
    $script:UpCalls++
}
function Invoke-Docker([string[]]$DockerArgs,[int]$TimeoutSeconds=600) {
    if(($DockerArgs -join '|') -cne 'port|fixture-owned-running-container|8080/tcp') {
        throw 'Published-port proof queried another container or port'
    }
    return $script:PublishedPort
}
function Check-Health { $script:HealthCalls++ }
$script:PublishedPort='127.0.0.1:'+$env:NQ_PS_FRONT
$script:UpCalls=0; $script:HealthCalls=0
Start-Runtime
if($script:UpCalls -ne 2 -or $script:HealthCalls -ne 1) { throw 'Owned running port was rejected' }
$script:PublishedPort='0.0.0.0:'+$env:NQ_PS_FRONT
$script:UpCalls=0; $script:HealthCalls=0
$rejected=$false; try { Start-Runtime } catch { $rejected=$true }
if(-not $rejected -or $script:UpCalls -ne 0 -or $script:HealthCalls -ne 0) { throw 'Mismatched publication accepted' }
''', NQ_PS_FRONT=str(published.getsockname()[1]), NQ_PS_BACK=str(backend.getsockname()[1]))
        self.assertEqual(0, published.getsockopt(socket.SOL_SOCKET, socket.SO_ERROR))

    def test_local_docker_guard_honors_context_priority_before_daemon_access(self):
        self.evaluate('''function Invoke-Docker([string[]]$DockerArgs,[int]$TimeoutSeconds=600) {
    if(($DockerArgs[0] -cne 'context') -or ($DockerArgs[1] -cne 'inspect')) { throw 'Guard accessed a daemon' }
    return $script:FixtureEndpoint
}
foreach($case in @(
    @('synthetic-remote','npipe:////./pipe/docker_engine','tcp://fixture.invalid:2376',$false),
    @('synthetic-local','tcp://fixture.invalid:2376','npipe:////./pipe/docker_engine',$true),
    @('','npipe:////./pipe/docker_engine','tcp://fixture.invalid:2376',$true),
    @('','tcp://fixture.invalid:2376','npipe:////./pipe/docker_engine',$false))) {
    $env:DOCKER_CONTEXT=$case[0]; $env:DOCKER_HOST=$case[1]; $script:FixtureEndpoint=$case[2]
    $accepted=$true; try { Assert-LocalDocker } catch { $accepted=$false }
    if($accepted -ne $case[3]) { throw 'Docker local endpoint/context priority regression' }
}
''')

    def test_actual_powershell5_file_defaults_reach_safe_path_rejection(self):
        if Path(self.shell).name.lower() != 'powershell.exe':
            self.skipTest('Actual Windows PowerShell 5 entry point regression')
        child_env = os.environ | {'PSModulePath': str(Path(os.environ['SystemRoot']) /
                                                    'System32/WindowsPowerShell/v1.0/Modules')}
        for name in ('nexusquant.ps1', 'install.ps1'):
            with self.subTest(entry=name):
                result = subprocess.run([self.shell, '-NoProfile', '-NonInteractive', '-ExecutionPolicy',
                                         'Bypass', '-File', str(REPO / 'release/installers' / name),
                                         '-InstallRoot', Path(os.environ['SystemRoot']).anchor],
                                        env=child_env, capture_output=True, text=True,
                                        encoding='utf-8', errors='replace', timeout=20)
                self.assertNotEqual(0, result.returncode, result.stderr[-1200:])
                self.assertIn('Unsafe installation', result.stderr)
                self.assertNotIn('Split-Path', result.stderr)


if __name__ == '__main__':
    unittest.main()
