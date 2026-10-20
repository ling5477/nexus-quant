"""正式包构建入口及真实小型tar图验证；Docker引擎是SIMULATED，不代表真机构建或运行。"""
import json
import hashlib
import io
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import tarfile
import unittest

from test_installer import REPO, digest


def make_oci_archive(path, mode='valid'):
    """构造真实的小型 OCI/Docker hybrid tar；镜像引擎仍是隔离模拟。"""
    blobs, legacy, descriptors, refs = {}, [], [], {}

    def blob(value):
        raw = json.dumps(value, separators=(',', ':'), sort_keys=True).encode()
        identity = 'sha256:' + hashlib.sha256(raw).hexdigest()
        blobs['blobs/sha256/' + identity[7:]] = raw
        return identity, len(raw)

    for component in ('backend', 'frontend', 'postgres'):
        tag = f'nexusquant/{component}:1.0.0' if component != 'postgres' else 'postgres:16.15'
        config_id, config_size = blob({'os': 'linux', 'architecture': 'arm64' if mode == 'platform' else 'amd64',
                                      'rootfs': {'type': 'layers', 'diff_ids': []},
                                      'config': {'Labels': {'fixture-component': component}}})
        manifest_id, manifest_size = blob({'schemaVersion': 2, 'mediaType': 'application/vnd.oci.image.manifest.v1+json',
            'config': {'mediaType': 'application/vnd.oci.image.config.v1+json',
                       'digest': 'sha256:' + 'e' * 64 if mode == 'binding' else config_id, 'size': config_size},
            'layers': [], 'annotations': {'fixture-component': component}})
        platform = {'mediaType': 'application/vnd.oci.image.manifest.v1+json', 'digest': manifest_id,
                    'size': manifest_size, 'platform': {'os': 'linux', 'architecture': 'amd64'}}
        native_id, native_size = blob({'schemaVersion': 2, 'mediaType': 'application/vnd.oci.image.index.v1+json',
                                      'manifests': [platform, platform] if mode == 'ambiguous' else [platform]})
        descriptors.append({'mediaType': 'application/vnd.oci.image.index.v1+json', 'digest': native_id,
                            'size': native_size, 'annotations': {'io.containerd.image.name': tag}})
        legacy.append({'Config': 'blobs/sha256/' + config_id[7:],
                       'RepoTags': ['synthetic:wrong'] if mode == 'tag' else [tag],
                       'Layers': ['blobs/sha256/' + 'e' * 64] if mode == 'layers' else []})
        refs[component] = {'tag': tag, 'nativeImageId': native_id, 'configDigest': config_id,
                           'os': 'linux', 'architecture': 'amd64', 'repoDigests': []}
    blobs['manifest.json'] = json.dumps(legacy).encode()
    blobs['index.json'] = json.dumps({'schemaVersion': 2, 'manifests': descriptors}).encode()
    blobs['oci-layout'] = b'{"imageLayoutVersion":"1.0.0"}'
    if mode == 'legacy':
        del blobs['index.json']
    if mode in ('config-content', 'native-content'):
        key = 'configDigest' if mode == 'config-content' else 'nativeImageId'
        blobs['blobs/sha256/' + refs['backend'][key][7:]] += b' '
    if mode == 'missing':
        del blobs['blobs/sha256/' + refs['backend']['nativeImageId'][7:]]
    with tarfile.open(path, 'w', format=tarfile.USTAR_FORMAT) as archive:
        for name, raw in blobs.items():
            entry = tarfile.TarInfo(name); entry.size = len(raw)
            archive.addfile(entry, io.BytesIO(raw))
        if mode == 'duplicate':
            entry = tarfile.TarInfo('manifest.json'); entry.size = len(blobs['manifest.json'])
            archive.addfile(entry, io.BytesIO(blobs['manifest.json']))
    return refs


class PackageBuilderTest(unittest.TestCase):
    def setUp(self):
        self.shell = shutil.which('pwsh')
        if not self.shell:
            self.skipTest('PowerShell 7 required for package builder regression')
        self.temp = tempfile.TemporaryDirectory(prefix='nq-package-builder-')
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.export = self.root / 'source'; (self.export / 'release').mkdir(parents=True)
        self.builder = self.export / 'release/build-package.ps1'
        shutil.copyfile(REPO / 'release/build-package.ps1', self.builder)
        (self.export / 'VERSION').write_text('1.0.0\n', newline='\n')
        self.output = self.root / 'package'
        self.bin = self.root / 'bin'; self.bin.mkdir()
        sentinel = self.root / 'docker-called'
        # 即使验证回归误达外调，fixture也不会执行宿主Docker。
        (self.bin / 'docker.cmd').write_text('@echo off\n@echo called > "%NQ_DOCKER_SENTINEL%"\n@exit /b 1\n')
        if os.name != 'nt':
            fake = self.bin / 'docker'
            fake.write_text('#!/bin/sh\necho called > "$NQ_DOCKER_SENTINEL"\nexit 1\n')
            fake.chmod(0o755)
        self.env = os.environ | {'PATH': str(self.bin) + os.pathsep + os.environ['PATH'],
                                 'NQ_DOCKER_SENTINEL': str(sentinel)}
        self.sentinel = sentinel

    def identity(self, **extra):
        identity = {'sourceCommit': '1' * 40, 'releaseTreeSha256': '2' * 64, 'targetVersion': '1.0.0',
                    'files': [{'path': str(p.relative_to(self.export)).replace('\\', '/'), 'sha256': digest(p)}
                              for p in sorted(self.export.rglob('*')) if p.is_file() and p.name != '.release-source.json']}
        identity.update(extra)
        (self.export / '.release-source.json').write_text(json.dumps(identity), encoding='utf-8')
        return identity

    def rejected(self, expected):
        result = subprocess.run([self.shell, '-NoProfile', '-NonInteractive', '-File', str(self.builder),
                                 '-OutputDirectory', str(self.output), '-Architectures', 'amd64'],
                                env=self.env, capture_output=True, text=True,
                                encoding='utf-8', errors='replace', timeout=20)
        self.assertNotEqual(0, result.returncode)
        self.assertIn(expected, result.stderr)
        self.assertFalse(self.output.exists()); self.assertFalse(self.sentinel.exists())

    def test_missing_identity_rejected_before_docker(self):
        self.rejected('verified immutable release-source export')

    def archive_helper(self, archive, refs, success=True, expected=''):
        refs_path = self.root / 'refs.json'; refs_path.write_text(json.dumps(refs), encoding='utf-8')
        runner = self.root / 'archive-helper.ps1'
        runner.write_text('''$ErrorActionPreference='Stop'
$tokens=$null; $errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($env:NQ_BUILDER_SOURCE,[ref]$tokens,[ref]$errors)
if($errors.Count) { throw 'Production builder parse failed' }
$ast.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst]},$false) | ForEach-Object { . ([scriptblock]::Create($_.Extent.Text)) }
$refs=Get-Content -LiteralPath $env:NQ_ARCHIVE_REFS -Raw | ConvertFrom-Json -AsHashtable
Archive-ImageIdentities $env:NQ_ARCHIVE_PATH $refs amd64
$refs | ConvertTo-Json -Depth 9
''', encoding='utf-8', newline='\n')
        result = subprocess.run([self.shell, '-NoProfile', '-NonInteractive', '-File', str(runner)],
                                env=self.env | {'NQ_BUILDER_SOURCE': str(self.builder),
                                    'NQ_ARCHIVE_REFS': str(refs_path), 'NQ_ARCHIVE_PATH': str(archive)},
                                capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=30)
        self.assertEqual(success, result.returncode == 0, result.stderr[-1200:])
        if expected:
            self.assertIn(expected, result.stderr)
        return result

    def test_archive_graph_positive_and_metadata_tamper_negative(self):
        archive = self.root / 'fixture.tar'
        refs = make_oci_archive(archive)
        result = self.archive_helper(archive, refs)
        actual = json.loads(result.stdout)
        self.assertEqual(refs['backend']['configDigest'], actual['backend']['configDigest'])
        self.assertNotEqual(actual['backend']['nativeImageId'], actual['backend']['configDigest'])
        for mode, expected in (('legacy', 'requires containerd OCI image store'),
            ('config-content', 'config content digest mismatch'), ('native-content', 'native index content digest mismatch'),
            ('binding', 'native index/config binding mismatch'), ('platform', 'config platform mismatch'),
            ('layers', 'layer binding mismatch'), ('missing', 'metadata unavailable'),
            ('duplicate', 'Invalid or duplicate'), ('ambiguous', 'target platform manifest unavailable'),
            ('tag', 'config identity unavailable')):
            with self.subTest(mode=mode):
                refs = make_oci_archive(archive, mode)
                self.archive_helper(archive, refs, success=False, expected=expected)

    def complete_export(self):
        for name in ('runtime', 'installers', 'docker'):
            shutil.copytree(REPO / 'release' / name, self.export / 'release' / name)
        for name in ('README.md', 'INSTALL.md', 'CHANGELOG.md', 'LICENSE', 'DISCLAIMER.md', 'THIRD_PARTY_NOTICES.md'):
            if name in ('THIRD_PARTY_NOTICES.md', 'DISCLAIMER.md'):
                shutil.copyfile(REPO / name, self.export / name)
            else:
                (self.export / name).write_text('SYNTHETIC BUILDER FIXTURE\n', encoding='utf-8', newline='\n')
        migration = self.export / 'backend/fixture/db/migration/V1__fixture.sql'
        migration.parent.mkdir(parents=True); migration.write_text('SELECT 1;\n', newline='\n')
        self.identity()

    def fake_docker_engine(self, archive, refs):
        script = self.root / 'fake-docker.py'
        script.write_text('''import json,os,shutil,sys
from pathlib import Path
args=sys.argv[1:]
with open(os.environ['NQ_DOCKER_SENTINEL'],'a',encoding='utf-8') as calls: calls.write(json.dumps(args)+'\\n')
refs=json.loads(Path(os.environ['NQ_FAKE_REFS']).read_text())
if args[0]=='context': print('fixture' if args[1]=='show' else os.environ.get('NQ_FAKE_ENDPOINT','unix:///fixture.sock'))
elif args[0]=='info': print('29.8.0')
elif args[0]=='image':
    tag=args[-1]
    match=next(value for value in refs.values() if value['tag']==tag)
    print(json.dumps([{'Id':match['nativeImageId'],'Os':'linux','Architecture':'amd64','RepoDigests':[]}]))
elif args[0]=='save': shutil.copyfile(os.environ['NQ_FAKE_ARCHIVE'],args[args.index('-o')+1])
elif args[0] not in ('buildx','pull'): sys.exit(1)
''', encoding='utf-8', newline='\n')
        refs_path = self.root / 'fake-refs.json'; refs_path.write_text(json.dumps(refs), encoding='utf-8')
        self.env.update(NQ_FAKE_REFS=str(refs_path), NQ_FAKE_ARCHIVE=str(archive),
                        NQ_FAKE_SCRIPT=str(script), NQ_FAKE_PYTHON=sys.executable,
                        DOCKER_HOST='', DOCKER_CONTEXT='')
        if os.name == 'nt':
            # 原生exe只转发固定fixture脚本；正式builder仍走其ProcessStart/argv代码。
            source = self.root / 'fixture-runner.cs'
            source.write_text(r'''using System;
using System.Diagnostics;
using System.Linq;
public class FixtureDocker {
 static string Q(string value) { return "\"" + value.Replace("\"", "\\\"") + "\""; }
 public static int Main(string[] args) {
  var info=new ProcessStartInfo(); info.FileName=Environment.GetEnvironmentVariable("NQ_FAKE_PYTHON");
  info.Arguments=Q(Environment.GetEnvironmentVariable("NQ_FAKE_SCRIPT"))+" "+String.Join(" ",args.Select(Q));
  info.UseShellExecute=false; info.CreateNoWindow=true; info.RedirectStandardOutput=true; info.RedirectStandardError=true;
  using(var child=Process.Start(info)) {
   var output=child.StandardOutput.ReadToEndAsync(); var error=child.StandardError.ReadToEndAsync();
   if(!child.WaitForExit(30000)) { child.Kill(); return 124; }
   Console.Out.Write(output.Result); Console.Error.Write(error.Result); return child.ExitCode;
  }
 }
}
''', encoding='utf-8')
            compiler = self.root / 'compile.ps1'
            compiler.write_text("$ErrorActionPreference='Stop'\nAdd-Type -Path $env:NQ_FAKE_SOURCE -OutputAssembly $env:NQ_FAKE_EXECUTABLE -OutputType ConsoleApplication\n", newline='\n')
            env = self.env | {'NQ_FAKE_SOURCE': str(source), 'NQ_FAKE_EXECUTABLE': str(self.bin / 'docker.exe'),
                'PSModulePath': str(Path(os.environ['SystemRoot']) / 'System32/WindowsPowerShell/v1.0/Modules')}
            result = subprocess.run([shutil.which('powershell'), '-NoProfile', '-NonInteractive', '-ExecutionPolicy',
                                     'Bypass', '-File', str(compiler)], env=env, capture_output=True,
                                    text=True, encoding='utf-8', errors='replace', timeout=30)
            self.assertEqual(0, result.returncode, result.stderr[-1200:])
        else:
            fake = self.bin / 'docker'
            fake.write_text('#!/bin/sh\nexec "$NQ_FAKE_PYTHON" "$NQ_FAKE_SCRIPT" "$@"\n', newline='\n')
            fake.chmod(0o755)

    def test_formal_builder_positive_with_real_archive_graph_and_simulated_engine(self):
        self.complete_export()
        archive = self.root / 'valid.tar'; refs = make_oci_archive(archive)
        self.fake_docker_engine(archive, refs)
        result = subprocess.run([self.shell, '-NoProfile', '-NonInteractive', '-File', str(self.builder),
                                 '-OutputDirectory', str(self.output), '-Architectures', 'amd64'],
                                env=self.env, capture_output=True, text=True,
                                encoding='utf-8', errors='replace', timeout=60)
        self.assertEqual(0, result.returncode, result.stderr[-1200:])
        from test_installer import metadata
        manifest = metadata(self.output / 'package-amd64.env')
        self.assertEqual(18, len(manifest))
        for component in ('backend', 'frontend', 'postgres'):
            prefix = component.upper()
            self.assertEqual(refs[component]['nativeImageId'], manifest[prefix + '_IMAGE'])
            self.assertEqual(refs[component]['configDigest'], manifest[prefix + '_CONFIG_DIGEST'])
            self.assertNotEqual(manifest[prefix + '_IMAGE'], manifest[prefix + '_CONFIG_DIGEST'])
        self.assertEqual(digest(archive), manifest['ARCHIVE_SHA256'])
        self.assertEqual(digest(self.output / 'package-amd64.env'),
                         (self.output / 'package-amd64.env.sha256').read_text().strip())
        self.assertTrue(self.sentinel.exists())

        for name in ('README.md', 'INSTALL.md', 'CHANGELOG.md', 'LICENSE', 'DISCLAIMER.md', 'THIRD_PARTY_NOTICES.md', 'VERSION'):
            self.assertEqual((self.export / name).read_bytes(), (self.output / name).read_bytes(), name)
        # 正式包保留 canonical 双语风险声明；模拟镜像测试不能替代 Production UI 验证。
        disclaimer = (self.output / 'DISCLAIMER.md').read_bytes()
        self.assertEqual((REPO / 'DISCLAIMER.md').read_bytes(), disclaimer)
        self.assertIn('## 中文'.encode('utf-8'), disclaimer)
        self.assertIn(b'## English', disclaimer)

    def test_builder_local_context_priority_rejects_remote_before_daemon_access(self):
        self.complete_export()
        archive = self.root / 'valid.tar'; refs = make_oci_archive(archive)
        self.fake_docker_engine(archive, refs)
        for index, (context, host, endpoint, accepted) in enumerate((
                ('synthetic-remote', 'unix:///fixture.sock', 'tcp://fixture.invalid:2376', False),
                ('synthetic-local', 'tcp://fixture.invalid:2376', 'unix:///fixture.sock', True),
                ('', 'unix:///fixture.sock', 'tcp://fixture.invalid:2376', True),
                ('', 'tcp://fixture.invalid:2376', 'unix:///fixture.sock', False))):
            with self.subTest(case=index):
                self.sentinel.unlink(missing_ok=True)
                output = self.root / ('guard-package-' + str(index))
                result = subprocess.run([self.shell, '-NoProfile', '-NonInteractive', '-File', str(self.builder),
                    '-OutputDirectory', str(output), '-Architectures', 'amd64'],
                    env=self.env | {'DOCKER_CONTEXT': context, 'DOCKER_HOST': host, 'NQ_FAKE_ENDPOINT': endpoint},
                    capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=60)
                self.assertEqual(accepted, result.returncode == 0, result.stderr[-1200:])
                calls = ([json.loads(line) for line in self.sentinel.read_text().splitlines()]
                         if self.sentinel.exists() else [])
                if accepted:
                    self.assertTrue((output / 'package-amd64.env').exists())
                    self.assertTrue(any(call[0] == 'info' for call in calls))
                else:
                    self.assertIn('requires a local Docker endpoint', result.stderr)
                    self.assertFalse(output.exists())
                    self.assertTrue(all(call[0] == 'context' for call in calls))

    def test_source_changed_or_extra_source_rejected_before_docker(self):
        self.identity(); (self.export / 'VERSION').write_text('1.0.0\nTAMPERED\n', newline='\n')
        self.rejected('Invalid VERSION')
        (self.export / 'VERSION').write_text('1.0.0\n', newline='\n')
        self.builder.write_text(self.builder.read_text(encoding='utf-8') + '\n# 合成篡改\n', encoding='utf-8')
        self.rejected('Export source changed')
        self.identity(); (self.export / 'unlisted.txt').write_text('synthetic')
        self.rejected('Unexpected file in source export')

    def test_unsafe_and_duplicate_source_paths_rejected_before_docker(self):
        identity = self.identity()
        identity['files'].append({'path': '../escape', 'sha256': '0' * 64})
        (self.export / '.release-source.json').write_text(json.dumps(identity))
        self.rejected('Unsafe export path')
        identity['files'][-1] = identity['files'][0]
        (self.export / '.release-source.json').write_text(json.dumps(identity))
        self.rejected('Duplicate source path')


if __name__ == '__main__':
    unittest.main()
