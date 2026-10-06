"""安装器 fixture 测试；不代表 macOS/Linux 真机 qualification。"""
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class PosixInstallerTest(unittest.TestCase):
    def test_install_rerun_operations_and_purge(self):
        shell = os.environ.get('NQ_TEST_BASH') or shutil.which('bash')
        if not shell:
            self.skipTest('bash is required for installer fixture verification')
        repo = Path(__file__).resolve().parents[3]
        with tempfile.TemporaryDirectory(prefix='nq-installer-fixture-') as work:
            root = Path(work)
            package = root / 'package'
            package.mkdir()
            shutil.copyfile(repo / 'VERSION', package / 'VERSION')
            for name in ('runtime', 'installers'):
                shutil.copytree(repo / 'release' / name, package / name)
            archive = package / 'images-amd64.tar'
            archive.write_bytes(b'SYNTHETIC_IMAGE_ARCHIVE')
            archive.with_suffix('.tar.sha256').write_text(hashlib.sha256(archive.read_bytes()).hexdigest())
            fakebin = root / 'bin'
            fakebin.mkdir()
            fixtures = {
                'docker': '''#!/bin/sh
case "$1" in
info) case "$*" in *Architecture*) echo x86_64;; *) echo 29.8.0;; esac;;
load) :;;
compose) case "$*" in *pg_dump*) printf 'SYNTHETIC_DATABASE_DUMP';; *psql*) echo 1;; *version*) echo 2.39.4;; *) :;; esac;;
esac
''',
                'curl': '#!/bin/sh\nprintf \'{"status":"UP","mustChangePassword":true}\'\n',
            }
            for name, content in fixtures.items():
                path = fakebin / name
                path.write_text(content, encoding='utf-8', newline='\n')
                path.chmod(0o755)

            def shell_path(path):
                value = path.as_posix()
                return '/' + value[0].lower() + value[2:] if os.name == 'nt' else value

            env = os.environ.copy()
            env['NQ_INSTALL_ROOT'] = shell_path(root / 'home')
            env['NQ_PACKAGE_ROOT'] = shell_path(package)
            env['NQ_FIXTURE_BIN'] = shell_path(fakebin)
            initializer = root / 'init.sh'
            initializer.write_text('export PATH="$NQ_FIXTURE_BIN:$PATH"\n', newline='\n')
            env['BASH_ENV'] = shell_path(initializer)

            def run(*args, success=True):
                result = subprocess.run([shell, shell_path(package / 'installers/nexusquant.sh'), *args],
                                        env=env, capture_output=True, text=True, timeout=30)
                if success:
                    self.assertEqual(0, result.returncode, result.stderr[:300])
                return result

            for script in (package / 'installers').glob('*.sh'):
                subprocess.run([shell, '-n', shell_path(script)], check=True, timeout=10)
            run('install')
            config = (root / 'home/config/runtime.env').read_bytes()
            run('install')
            self.assertEqual(config, (root / 'home/config/runtime.env').read_bytes())
            for operation in ('start', 'stop', 'restart', 'backup'):
                run(operation)
            backup = next((root / 'home/backups').iterdir())
            run('restore', shell_path(backup))
            run('uninstall')
            self.assertTrue((root / 'home/data').exists())
            manifest = backup / 'manifest.env'
            manifest.write_text(manifest.read_text().replace('APP_VERSION=1.0.0', 'APP_VERSION=2.0.0'), newline='\n')
            self.assertNotEqual(0, run('restore', shell_path(backup), success=False).returncode)
            run('uninstall', '--purge-data')
            self.assertFalse((root / 'home/data').exists())
            self.assertFalse((root / 'home/backups').exists())
