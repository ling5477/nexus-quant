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

    def test_package_parser_success_and_strict_negative_fields(self):
        expression = '$null=Read-Package $env:NQ_PS_PACKAGE $env:NQ_PS_TRUST amd64'
        self.evaluate(expression)
        original = self.manifest.read_bytes()
        cases = (original + b'FORMAT=1\n', original + b'UNKNOWN=1\n',
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
        self.evaluate('''$info=New-Object Diagnostics.ProcessStartInfo
$info.EnvironmentVariables['NQ_HOME']='synthetic-untrusted'
$info.EnvironmentVariables['BACKEND_IMAGE']='synthetic:untrusted'
$info.EnvironmentVariables['DB_PASSWORD']='synthetic-untrusted'
$info.EnvironmentVariables['COMPOSE_FILE']='synthetic-untrusted'
$info.EnvironmentVariables['HTTP_PROXY']='synthetic-proxy'
$info.EnvironmentVariables['DOCKER_HOST']='synthetic-docker'
Remove-RuntimeEnvironment $info
foreach($name in @('NQ_HOME','BACKEND_IMAGE','DB_PASSWORD','COMPOSE_FILE')) {
    if($info.EnvironmentVariables.ContainsKey($name)) { throw ('Ambient runtime override survived: '+$name) }
}
if($info.EnvironmentVariables['HTTP_PROXY'] -ne 'synthetic-proxy' -or $info.EnvironmentVariables['DOCKER_HOST'] -ne 'synthetic-docker') { throw 'Unrelated host setting changed' }
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
