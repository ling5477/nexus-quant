"""包构建正式入口必须在任何 Docker mutation 前拒绝错误导出身份。"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from test_installer import REPO, digest


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
                              for p in (self.builder, self.export / 'VERSION')]}
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
