"""正式 POSIX 入口的隔离 SIMULATED 回归；不能替代真实平台资格证据。"""
import hashlib
import os
from pathlib import Path
import shutil
import stat
import subprocess
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[3]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def metadata(path):
    return dict(line.split('=', 1) for line in path.read_text().splitlines())


def shell_path(path):
    value = Path(path).as_posix()
    return '/' + value[0].lower() + value[2:] if os.name == 'nt' else value


def make_package(root, version='1.0.0', schema='1', image_digit='a'):
    root.mkdir()
    for name in ('runtime', 'installers'):
        shutil.copytree(REPO / 'release' / name, root / name)
    (root / 'VERSION').write_text(version + '\n', encoding='utf-8', newline='\n')
    archive = root / 'images-amd64.tar'
    archive.write_bytes(b'SYNTHETIC_IMAGE_ARCHIVE_' + version.encode())
    fields = {'FORMAT': '1', 'VERSION': version, 'SOURCE_SHA': '1' * 40,
              'RELEASE_SOURCE_HASH': '2' * 64, 'SCHEMA_VERSION': schema, 'ARCH': 'amd64',
              'ARCHIVE': archive.name, 'ARCHIVE_SHA256': digest(archive),
              'BACKEND_IMAGE': 'sha256:' + image_digit * 64,
              'FRONTEND_IMAGE': 'sha256:' + 'b' * 64, 'POSTGRES_IMAGE': 'sha256:' + 'c' * 64,
              'BACKEND_CONFIG_DIGEST': 'sha256:' + ('4' if image_digit == 'a' else '5') * 64,
              'FRONTEND_CONFIG_DIGEST': 'sha256:' + '6' * 64,
              'POSTGRES_CONFIG_DIGEST': 'sha256:' + '7' * 64}
    for asset, field in (('runtime/compose.yml', 'COMPOSE_SHA256'),
                         ('runtime/runtime.yml', 'RUNTIME_SHA256'),
                         ('installers/nexusquant.ps1', 'PS_INSTALLER_SHA256'),
                         ('installers/nexusquant.sh', 'SH_INSTALLER_SHA256')):
        fields[field] = digest(root / asset)
    manifest = root / 'package-amd64.env'
    manifest.write_text(''.join(f'{key}={value}\n' for key, value in fields.items()),
                        encoding='utf-8', newline='\n')
    return manifest


class PosixFixture:
    """只在临时目录模拟宿主和 Docker；生产脚本没有测试开关。"""
    def __init__(self, directory, shell):
        self.root, self.shell = Path(directory), shell
        self.home, self.package, self.target = (self.root / name for name in ('home', 'package', 'target'))
        self.manifest = make_package(self.package)
        self.target_manifest = make_package(self.target, '1.0.1', '2', 'd')
        self.bin, self.state = self.root / 'bin', self.root / 'state'
        self.bin.mkdir(); self.state.mkdir()
        (self.root / 'tmp').mkdir()
        (self.state / 'facts').write_text('1\nFACTS_A\n', newline='\n')
        (self.state / 'calls').write_text('')
        self.env = os.environ | {
            'NQ_INSTALL_ROOT': shell_path(self.home), 'NQ_PACKAGE_ROOT': shell_path(self.package),
            'NQ_FIXTURE_BIN': shell_path(self.bin), 'NQ_FIX_STATE': shell_path(self.state),
            'NQ_FIX_CPU': '8', 'NQ_FIX_RAM': '17179869184', 'NQ_FIX_DISK': '20971520',
            'TMPDIR': shell_path(self.root / 'tmp'),
            'NQ_FIX_DOCKER_VERSION': '29.8.0', 'NQ_FIX_COMPOSE_VERSION': '2.39.4',
            'DOCKER_HOST': '', 'DOCKER_CONTEXT': '',
            'NQ_FIX_FAIL': '', 'NQ_FIX_PORT_CONFLICT': '', 'NQ_FIX_STORE': 'CONTAINERD'}
        self.initializer = self.root / 'init.sh'
        self.initializer.write_text('export PATH="$NQ_FIXTURE_BIN:$PATH"\n', newline='\n')
        self.env['BASH_ENV'] = shell_path(self.initializer)
        self._scripts()

    def script(self, name, body):
        path = self.bin / name
        path.write_text('#!/bin/sh\n' + body, encoding='utf-8', newline='\n')
        path.chmod(0o755)

    def _scripts(self):
        self.script('uname', 'case "$1" in -s) echo Darwin;; -m) echo x86_64;; esac\n')
        self.script('sw_vers', 'echo 14.7\n')
        self.script('sysctl', '''case "$*" in
*hw.logicalcpu*) echo "$NQ_FIX_CPU";; *hw.memsize*) echo "$NQ_FIX_RAM";;
*kern.hv_support*) echo 1;; esac
''')
        self.script('df', '''printf 'Filesystem 1024-blocks Used Available Capacity Mounted\\n'
printf 'fixture 999999999 0 %s 1%% /\\n' "$NQ_FIX_DISK"
''')
        self.script('lsof', '[ -n "$NQ_FIX_PORT_CONFLICT" ] && { echo occupied; exit 0; }; exit 1\n')
        self.script('scutil', 'echo "HTTPEnable : 0"\n')
        self.script('open', '''printf 'OPEN\\n' >> "$NQ_FIX_STATE/calls"
[ "$NQ_FIX_FAIL" != DAEMON_START ] || exit 1
rm -f "$NQ_FIX_STATE/daemon-stopped"
''')
        self.script('curl', '''case "$*" in
*desktop.docker.com*) printf 'DOWNLOAD\\n' >> "$NQ_FIX_STATE/calls"; exit 1;;
*/api/auth/login*) echo CURL_LOGIN >> "$NQ_FIX_STATE/calls"; printf '{"status":"UP","mustChangePassword":true}';;
*/api/auth/me*) printf 401;; *) printf '{"status":"UP","mustChangePassword":true}';; esac
''')
        self.script('id', 'case "$1" in -u) echo 1000;; -un) echo fixture;; *) echo fixture;; esac\n')
        self.script('docker', '''printf '%s\\n' "$*" >> "$NQ_FIX_STATE/calls"
if [ "$1" = compose ] && [ "$2" != version ] && [ -n "${NQ_HOME:-}${BACKEND_IMAGE:-}${FRONTEND_IMAGE:-}${POSTGRES_IMAGE:-}" ]; then
    echo AMBIENT_COMPOSE_OVERRIDE >> "$NQ_FIX_STATE/calls"; exit 1
fi
case "$1" in
info)
    [ ! -f "$NQ_FIX_STATE/daemon-stopped" ] || exit 1
    case "$*" in *ServerVersion*) echo "$NQ_FIX_DOCKER_VERSION";; *Architecture*) echo x86_64;;
    *NCPU*) echo "$NQ_FIX_CPU";; *MemTotal*) echo "$NQ_FIX_RAM";; *OSType*) echo linux;; esac;;
context) echo unix:///fixture.sock;;
load) [ "$NQ_FIX_FAIL" != LOAD ] || exit 1;;
image)
    [ "$NQ_FIX_FAIL" != IMAGE ] || exit 1
    for arg do case "$arg" in sha256:*) identity=$arg;; esac; done
    case "$identity" in
    sha256:aaaaaaaa*|sha256:bbbbbbbb*|sha256:cccccccc*|sha256:dddddddd*|sha256:88888888*)
        [ "$NQ_FIX_STORE" != CLASSIC ] || exit 1;;
    sha256:44444444*|sha256:55555555*|sha256:66666666*|sha256:77777777*)
        [ "$NQ_FIX_STORE" != CONTAINERD ] || exit 1;;
    *) exit 1;; esac
    [ "$NQ_FIX_FAIL" != WRONG_ID ] || identity=sha256:eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee
    image_arch=amd64; [ "$NQ_FIX_FAIL" != WRONG_PLATFORM ] || image_arch=arm64
    case "$*" in *'{{.Id}}|{{.Os}}|{{.Architecture}}'*) echo "$identity|linux|$image_arch";;
    *Architecture*) echo "$image_arch";; *Os*) echo linux;; *) echo "$identity";; esac;;
run)
    case "$*" in *sha256:77777777*) [ "$NQ_FIX_STORE" != CONTAINERD ] || exit 1;;
    *sha256:cccccccc*|*sha256:88888888*) [ "$NQ_FIX_STORE" != CLASSIC ] || exit 1;;
    *) exit 1;; esac
    echo 'postgres (PostgreSQL) 16.15';;
inspect)
    case "$*" in *Config.Env*) echo 'SIM=ENABLED LIVE=DISABLED REAL_EXCHANGE=DISABLED REAL_PROVIDER=DISABLED REAL_CLIENT=DISABLED';;
    *) echo 'running healthy';; esac;;
compose)
    case "$*" in
    *'compose version --short'*) echo "$NQ_FIX_COMPOSE_VERSION";;
    *pg_dump*) cat "$NQ_FIX_STATE/facts";;
    *pg_restore*) [ "$NQ_FIX_FAIL" != RESTORE ] || exit 1; cat > "$NQ_FIX_STATE/facts";;
    *psql*) case "$*" in *flyway_schema_history*) head -n 1 "$NQ_FIX_STATE/facts";;
        *must_change_password*) echo "${NQ_FIX_CHANGED:-t}";;
        *server_version_num*) echo 16;; *'SELECT version()'*) echo 'PostgreSQL 16.15';;
        *kill_switch_states*) echo ENGAGED;; *) echo 1;; esac;;
    *'up -d'*) case "$*" in *backend*)
        schema=$(sed -n 's/^SCHEMA_VERSION=//p' "$NQ_INSTALL_ROOT/config/runtime.env")
        if [ "$schema" = 2 ]; then
            printf '2\\nFACTS_A\\nFACTS_B\\n' > "$NQ_FIX_STATE/facts"
            [ "$NQ_FIX_FAIL" != AFTER_DB ] || exit 1
            if [ "$NQ_FIX_FAIL" = INTERRUPT ]; then
                kill -9 "$(cat "$NQ_INSTALL_ROOT/runtime/.operation-lock/pid")"; exit 1
            fi
        fi;; esac
        mkdir -p "$NQ_INSTALL_ROOT/data/postgres"; echo 16 > "$NQ_INSTALL_ROOT/data/postgres/PG_VERSION";;
    *'ps '*) echo fixture-container;; esac;;
esac
''')

    def run(self, *args, success=True, **env):
        result = subprocess.run([self.shell, shell_path(self.package / 'installers/nexusquant.sh'), *args],
                                env=self.env | env, capture_output=True, text=True, timeout=60)
        if success and result.returncode:
            raise AssertionError(f'{args}: {result.returncode}\n{result.stdout[-1000:]}\n{result.stderr[-1000:]}')
        if not success and not result.returncode:
            raise AssertionError(f'{args}: expected refusal, got success')
        return result

    def target_args(self):
        return '--package', shell_path(self.target), '--manifest-sha256', digest(self.target_manifest)

    def calls(self):
        return (self.state / 'calls').read_text()

    def clear_calls(self):
        (self.state / 'calls').write_text('')

    def facts(self):
        return (self.state / 'facts').read_text()

    def config(self):
        # 比较完整字节的hash；失败诊断也不能把随机认证密钥写入测试日志。
        return digest(self.home / 'config/runtime.env')

    def receipts(self):
        return [metadata(p) for p in (self.home / 'runtime/transactions').glob('*/receipt.env')]

    def tree(self):
        return {str(p.relative_to(self.home)): digest(p) for p in self.home.rglob('*') if p.is_file()}


class PosixInstallerTest(unittest.TestCase):
    def setUp(self):
        self.shell = os.environ.get('NQ_TEST_BASH') or (
            r'D:\Tool\Git\bin\bash.exe' if os.name == 'nt' else shutil.which('bash'))
        if not self.shell or not Path(self.shell).exists():
            self.skipTest('Git Bash or bash required for SIMULATED fixture')
        self.temp = tempfile.TemporaryDirectory(prefix='nq-lifecycle-simulated-')
        self.addCleanup(self.temp.cleanup)
        self.fixture = PosixFixture(self.temp.name, self.shell)

    def test_install_rerun_operations_and_purge(self):
        f = self.fixture
        for script in (f.package / 'installers').glob('*.sh'):
            subprocess.run([self.shell, '-n', shell_path(script)], check=True, timeout=10)
        f.run('install'); config = f.config()
        f.run('install'); self.assertEqual(config, f.config())
        for operation in ('start', 'stop', 'restart', 'status', 'doctor', 'backup'):
            result = f.run(operation)
            if operation == 'doctor':
                self.assertIn('KILL_STATE=ENGAGED', result.stdout)
                self.assertIn('UPDATE=UNKNOWN', result.stdout)
                for key in ('DB_PASSWORD', 'JWT_SECRET', 'CREDENTIALS_KEY'):
                    self.assertFalse(metadata(f.home / 'config/runtime.env')[key] in result.stdout,
                                     'doctor exposed a restricted authentication configuration value')
        backup = next((f.home / 'backups').iterdir())
        (f.state / 'facts').write_text('1\nFACTS_A\nFACTS_B\n', newline='\n')
        f.run('restore', shell_path(backup)); self.assertEqual('1\nFACTS_A\n', f.facts())
        f.run('uninstall'); self.assertTrue((f.home / 'data').exists()); self.assertTrue(backup.exists())
        before = f.tree(); f.clear_calls()
        f.run('uninstall', '--purge-data', success=False)
        self.assertEqual(before, f.tree()); self.assertNotIn(' down ', f.calls())
        f.run('uninstall', '--purge-data', '--confirm-purge')
        self.assertFalse((f.home / 'data').exists()); self.assertFalse((f.home / 'backups').exists())
        self.assertTrue((f.home / 'runtime/first-install-pending').exists())
        f.run('install')
        self.assertEqual(config, f.config())
        self.assertFalse((f.home / 'runtime/first-install-pending').exists())

    def test_interrupted_bootstrap_preserves_already_changed_admin(self):
        f = self.fixture
        f.run('install'); config = f.config()
        (f.home / 'runtime/first-install-pending').write_text('FRESH\n', newline='\n')
        f.clear_calls(); f.run('install', NQ_FIX_CHANGED='f')
        self.assertEqual(config, f.config())
        self.assertNotIn('CURL_LOGIN', f.calls())
        self.assertFalse((f.home / 'runtime/first-install-pending').exists())

    def test_manifest_rejections_precede_docker_or_config_mutation(self):
        f = self.fixture; original = f.manifest.read_bytes()
        cases = {'duplicate': original + b'FORMAT=1\n', 'unknown': original + b'UNKNOWN=1\n',
                 'duplicate_config': original + b'BACKEND_CONFIG_DIGEST=sha256:' + b'4' * 64 + b'\n',
                 'missing_config': original.replace(b'BACKEND_CONFIG_DIGEST=sha256:' + b'4' * 64 + b'\n', b''),
                 'mutable_config': original.replace(b'BACKEND_CONFIG_DIGEST=sha256:' + b'4' * 64,
                                                    b'BACKEND_CONFIG_DIGEST=synthetic:mutable'),
                 'missing': original.replace(b'FORMAT=1\n', b''),
                 'archive_path': original.replace(b'ARCHIVE=images-amd64.tar', b'ARCHIVE=../images-amd64.tar'),
                 'mutable_image': original.replace(b'BACKEND_IMAGE=sha256:' + b'a' * 64,
                                                   b'BACKEND_IMAGE=nexusquant/backend:latest'),
                 'crlf': original.replace(b'\n', b'\r\n'), 'bom': b'\xef\xbb\xbf' + original,
                 'missing_final_lf': original.rstrip(b'\n'),
                 'tamper': original.replace(digest(f.package / 'images-amd64.tar').encode(), b'0' * 64)}
        for name, raw in cases.items():
            with self.subTest(name=name):
                f.manifest.write_bytes(raw); f.clear_calls(); f.run('install', success=False)
                self.assertEqual('', f.calls()); self.assertFalse((f.home / 'config/runtime.env').exists())
        f.manifest.write_bytes(original)

    def test_check_update_verified_read_only_and_no_trust_autoaccept(self):
        f = self.fixture; f.run('install'); before = f.tree(); f.clear_calls()
        self.assertIn('Update available', f.run('check-update', *f.target_args()).stdout)
        self.assertEqual(before, f.tree()); self.assertEqual('', f.calls())
        for arguments in (('--package', shell_path(f.target)),
                          ('--package', shell_path(f.target), '--manifest-sha256', '0' * 64)):
            f.run('check-update', *arguments, success=False)
            self.assertEqual(before, f.tree()); self.assertEqual('', f.calls())
        (f.target / 'runtime/runtime.yml').write_bytes(b'TAMPERED')
        f.run('check-update', *f.target_args(), success=False)
        self.assertEqual(before, f.tree()); self.assertEqual('', f.calls())

    def test_profiles_boundary_and_insufficient_capacity(self):
        f = self.fixture
        for index, (cpu, ram, expected) in enumerate((('2', '4294967296', 'LIGHT'),
            ('4', '8589934592', 'STANDARD'), ('8', '17179869184', 'PERFORMANCE'),
            ('8', '8589934591', 'LIGHT'), ('7', '17179869184', 'STANDARD'))):
            with self.subTest(cpu=cpu, ram=ram):
                home = f.root / f'home-{index}'
                f.run('install', NQ_INSTALL_ROOT=shell_path(home), NQ_FIX_CPU=cpu, NQ_FIX_RAM=ram)
                config = metadata(home / 'config/runtime.env')
                self.assertEqual(expected, config['RUNTIME_PROFILE']); self.assertEqual('OFF', config['AUTO_UPDATE'])
        for index, values in enumerate(({'NQ_FIX_CPU': '1'}, {'NQ_FIX_RAM': '4294967295'},
                                        {'NQ_FIX_DISK': '10485759'})):
            home = f.root / f'refused-{index}'
            f.run('install', success=False, NQ_INSTALL_ROOT=shell_path(home), **values)
            self.assertFalse((home / 'config/runtime.env').exists())

    def test_old_docker_compose_daemon_and_port_conflict(self):
        f = self.fixture
        for values in ({'NQ_FIX_DOCKER_VERSION': '23.0.6'}, {'NQ_FIX_COMPOSE_VERSION': '2.19.9'}):
            f.run('install', success=False, **values); self.assertFalse((f.home / 'config/runtime.env').exists())
        (f.state / 'daemon-stopped').touch()
        f.run('install', success=False, NQ_FIX_FAIL='DAEMON_START'); self.assertFalse((f.home / 'config/runtime.env').exists())
        f.run('install'); self.assertIn('OPEN', f.calls())
        other = f.root / 'occupied-home'; f.clear_calls()
        f.run('install', success=False, NQ_INSTALL_ROOT=shell_path(other), NQ_FIX_PORT_CONFLICT='1')
        self.assertNotIn('load ', f.calls()); self.assertFalse((other / 'config/runtime.env').exists())

    def test_remote_docker_host_rejected_before_any_daemon_access(self):
        f = self.fixture
        f.run('install', success=False, DOCKER_HOST='tcp://synthetic-host.invalid:2376')
        self.assertEqual('', f.calls())
        self.assertFalse((f.home / 'config/runtime.env').exists())

    def test_ambient_compose_identity_cannot_override_verified_runtime(self):
        f = self.fixture
        ambient = {'NQ_HOME': '/synthetic-untrusted-home', 'BACKEND_IMAGE': 'synthetic:untrusted',
                   'FRONTEND_IMAGE': 'synthetic:untrusted', 'POSTGRES_IMAGE': 'synthetic:untrusted'}
        f.run('install', **ambient)
        config = metadata(f.home / 'config/runtime.env')
        self.assertEqual(shell_path(f.home), config['NQ_HOME'])
        self.assertEqual('sha256:' + 'a' * 64, config['BACKEND_IMAGE'])
        f.run('status', **ambient)
        self.assertNotIn('AMBIENT_COMPOSE_OVERRIDE', f.calls())

    def test_missing_docker_official_download_failure_is_bounded(self):
        f = self.fixture; (f.bin / 'docker').unlink()
        if Path('/Applications/Docker.app/Contents/Resources/bin/docker').exists():
            self.skipTest('Preserve host Docker.app; absent case requires isolated host')
        # 用固定工具白名单构造PATH；即使Linux宿主有/usr/bin/docker也不会命中它。
        tools = ('dirname', 'grep', 'sed', 'awk', 'wc', 'cut', 'tr', 'od', 'chmod', 'mkdir',
                 'cp', 'mv', 'sync', 'date', 'cat', 'sleep', 'timeout', 'dd', 'mktemp', 'rm',
                 'rmdir', 'head', 'tail', 'basename', 'cmp', 'sha256sum', 'shasum')
        lookup = 'for tool in ' + ' '.join(tools) + '; do command -v "$tool" || :; done'
        clean_env = {key: value for key, value in os.environ.items() if key not in ('BASH_ENV', 'ENV')}
        locations = subprocess.run([self.shell, '-c', lookup], env=clean_env, capture_output=True,
                                   text=True, encoding='utf-8', check=True, timeout=10).stdout.splitlines()
        for location in locations:
            name = location.rsplit('/', 1)[-1]
            f.script(name, 'exec ' + "'" + location.replace("'", "'\\''") + "'" + ' "$@"\n')
        f.initializer.write_text('export PATH="$NQ_FIXTURE_BIN"\n', newline='\n')
        result = f.run('install', success=False)
        self.assertIn('Official Docker DMG download failed', result.stderr)
        self.assertIn('DOWNLOAD', f.calls()); self.assertFalse((f.home / 'config/runtime.env').exists())

    def test_update_requires_confirmation_before_mutation(self):
        f = self.fixture; f.run('install'); before = f.tree(); facts = f.facts(); f.clear_calls()
        f.run('update', *f.target_args(), success=False)
        self.assertEqual(before, f.tree()); self.assertEqual(facts, f.facts())
        self.assertNotIn('load ', f.calls()); self.assertNotIn(' stop ', f.calls())

    def test_no_host_java_node_psql_dependency_and_proxy_value_redacted(self):
        f = self.fixture
        for name in ('java', 'node', 'psql'):
            f.script(name, 'echo FORBIDDEN_HOST_TOOL >> "$NQ_FIX_STATE/calls"; exit 127\n')
        proxy = 'http://synthetic-user:synthetic-password@fixture.invalid:8123'
        result = f.run('install', HTTPS_PROXY=proxy)
        self.assertNotIn('FORBIDDEN_HOST_TOOL', f.calls())
        self.assertIn('PROXY=PRESENT', result.stdout)
        self.assertNotIn(proxy, result.stdout + result.stderr)

    def test_before_database_failure_leaves_facts_identity_unchanged(self):
        f = self.fixture; f.run('install'); before = f.tree(); f.clear_calls()
        f.run('update', *f.target_args(), '--yes', success=False, NQ_FIX_FAIL='LOAD')
        after = {key: value for key, value in f.tree().items() if not key.replace('\\', '/').startswith('runtime/attempts/')}
        self.assertEqual(before, after); self.assertEqual('1\nFACTS_A\n', f.facts())
        self.assertNotIn(' stop ', f.calls())
        attempt, = (f.home / 'runtime/attempts').glob('*.env')
        record = metadata(attempt)
        self.assertEqual('UPDATE_FAILED_BEFORE_DB', record['RESULT'])
        self.assertEqual('NONE', record['BACKUP_ID'])

    def test_after_database_failure_restores_exact_previous_facts_schema_and_assets(self):
        f = self.fixture; f.run('install'); config = f.config()
        f.run('update', *f.target_args(), '--yes', success=False, NQ_FIX_FAIL='AFTER_DB')
        self.assertEqual('1\nFACTS_A\n', f.facts()); self.assertEqual(config, f.config())
        self.assertEqual('1.0.0', (f.home / 'runtime/VERSION').read_text().strip())
        self.assertFalse((f.home / 'runtime/pending-update').exists())
        receipt, = f.receipts()
        self.assertEqual('UPDATE_FAILED', receipt['RESULT']); self.assertEqual('ROLLBACK_SUCCESS', receipt['ROLLBACK_RESULT'])
        self.assertEqual('1', receipt['SCHEMA_BEFORE']); self.assertEqual('2', receipt['SCHEMA_AFTER'])
        f.run('start')

    def test_dual_store_resolution_records_actual_ids_and_restores_known_backup(self):
        f = self.fixture
        for store, old_digit, target_digit, pg_digit in (
                ('CONTAINERD', 'a', 'd', 'c'), ('CLASSIC', '4', '5', '7')):
            with self.subTest(store=store):
                f.home = f.root / ('home-' + store.lower())
                f.env.update(NQ_INSTALL_ROOT=shell_path(f.home), NQ_FIX_STORE=store)
                (f.state / 'facts').write_text('1\nFACTS_A\n', newline='\n')
                f.run('install'); before = f.config()
                config = metadata(f.home / 'config/runtime.env')
                self.assertEqual('sha256:' + old_digit * 64, config['BACKEND_IMAGE'])
                installed = metadata(f.home / 'runtime/package.env')
                self.assertEqual('sha256:' + '4' * 64, installed['BACKEND_CONFIG_DIGEST'])
                self.assertEqual('sha256:' + pg_digit * 64, config['POSTGRES_IMAGE'])
                f.run('update', *f.target_args(), '--yes')
                config = metadata(f.home / 'config/runtime.env')
                self.assertEqual('sha256:' + target_digit * 64, config['BACKEND_IMAGE'])
                installed = metadata(f.home / 'runtime/package.env')
                self.assertEqual('sha256:' + '5' * 64, installed['BACKEND_CONFIG_DIGEST'])
                receipt, = f.receipts()
                self.assertEqual('sha256:' + target_digit * 64, receipt['BACKEND_IMAGE'])
                self.assertEqual('sha256:' + old_digit * 64, receipt['SOURCE_BACKEND_IMAGE'])
                backup = metadata(f.home / 'backups' / receipt['BACKUP_ID'] / 'runtime.env')
                self.assertEqual('sha256:' + old_digit * 64, backup['BACKEND_IMAGE'])
                backup_manifest = metadata(f.home / 'backups' / receipt['BACKUP_ID'] / 'assets/package.env')
                self.assertEqual('sha256:' + '4' * 64, backup_manifest['BACKEND_CONFIG_DIGEST'])
                f.run('rollback', '--yes')
                self.assertEqual(before, f.config())
                self.assertEqual('1\nFACTS_A\n', f.facts())

    def test_wrong_observed_id_or_platform_rejected_before_database_start(self):
        f = self.fixture
        for mode in ('WRONG_ID', 'WRONG_PLATFORM'):
            with self.subTest(mode=mode):
                f.clear_calls()
                f.run('install', success=False, NQ_FIX_FAIL=mode)
                self.assertNotIn(' up ', f.calls())
                self.assertFalse((f.home / 'config/runtime.env').exists())
                self.assertEqual('1\nFACTS_A\n', f.facts())

    def test_selected_runtime_id_outside_declared_pair_rejected_before_docker(self):
        f = self.fixture; f.run('install')
        path = f.home / 'config/runtime.env'
        path.write_bytes(path.read_bytes().replace(b'BACKEND_IMAGE=sha256:' + b'a' * 64,
                                                  b'BACKEND_IMAGE=sha256:' + b'e' * 64))
        f.clear_calls(); f.run('start', success=False)
        self.assertEqual('', f.calls())
        self.assertEqual('1\nFACTS_A\n', f.facts())

    def test_postgres_compatibility_uses_config_digest_and_not_native_index(self):
        f = self.fixture; f.run('install')
        f.target_manifest.write_bytes(f.target_manifest.read_bytes().replace(
            b'POSTGRES_IMAGE=sha256:' + b'c' * 64, b'POSTGRES_IMAGE=sha256:' + b'8' * 64))
        f.run('update', *f.target_args(), '--yes')
        self.assertEqual('sha256:' + '8' * 64, metadata(f.home / 'config/runtime.env')['POSTGRES_IMAGE'])
        f.run('rollback', '--yes')
        before = f.config(); f.clear_calls()
        f.target_manifest.write_bytes(f.target_manifest.read_bytes().replace(
            b'POSTGRES_CONFIG_DIGEST=sha256:' + b'7' * 64,
            b'POSTGRES_CONFIG_DIGEST=sha256:' + b'9' * 64))
        f.run('update', *f.target_args(), '--yes', success=False)
        self.assertEqual(before, f.config()); self.assertEqual('1\nFACTS_A\n', f.facts())
        self.assertNotIn('load ', f.calls()); self.assertNotIn(' stop ', f.calls())

    @unittest.skipIf(os.name == 'nt', 'Real POSIX file modes require a POSIX filesystem; Git Bash cannot prove them')
    def test_real_posix_static_runtime_readable_and_secrets_private_across_asset_lifecycle(self):
        """真实POSIX文件系统权限回归；宿主身份、Docker和数据库仍是SIMULATED。"""
        f = self.fixture

        def assert_modes(expected_runtime):
            for path in (f.home, f.home / 'config', f.home / 'backups', f.home / 'runtime'):
                self.assertEqual(0o700, stat.S_IMODE(path.stat().st_mode), str(path.relative_to(f.root)))
            self.assertEqual(0o600, stat.S_IMODE((f.home / 'config/runtime.env').stat().st_mode))
            runtime = f.home / 'runtime/runtime.yml'
            self.assertEqual(0o644, stat.S_IMODE(runtime.stat().st_mode),
                             'Non-root backend UID requires read permission on the bind-mounted static file')
            self.assertEqual(expected_runtime, digest(runtime))
            for backup in (f.home / 'backups').iterdir():
                if backup.is_dir():
                    for name in ('runtime.env', 'database.dump', 'assets/runtime.yml'):
                        self.assertEqual(0o600, stat.S_IMODE((backup / name).stat().st_mode),
                                         'Backup assets and authentication configuration must stay private')

        original_runtime = digest(f.package / 'runtime/runtime.yml')
        target_runtime = f.target / 'runtime/runtime.yml'
        target_runtime.write_bytes(target_runtime.read_bytes() + '\n# 隔离权限回归的目标静态配置。\n'.encode('utf-8'))
        fields = metadata(f.target_manifest); fields['RUNTIME_SHA256'] = digest(target_runtime)
        f.target_manifest.write_text(''.join(f'{key}={value}\n' for key, value in fields.items()),
                                     encoding='utf-8', newline='\n')
        f.run('install'); original_config = f.config(); assert_modes(original_runtime)
        f.run('update', *f.target_args(), '--yes'); assert_modes(digest(target_runtime))
        f.run('rollback', '--yes'); assert_modes(original_runtime)
        self.assertEqual(original_config, f.config()); self.assertEqual('1\nFACTS_A\n', f.facts())
        f.run('backup')
        backup = sorted((f.home / 'backups').iterdir())[-1]
        (f.state / 'facts').write_text('1\nFACTS_A\nFACTS_B\n', newline='\n')
        f.run('restore', shell_path(backup)); assert_modes(original_runtime)
        self.assertEqual(original_config, f.config()); self.assertEqual('1\nFACTS_A\n', f.facts())

    def test_ubuntu_apt_proxy_helper_preserves_only_requested_child_keys(self):
        f = self.fixture
        source = (f.package / 'installers/nexusquant.sh').read_text(encoding='utf-8')
        parts = source.split('\nhost_identity\n')
        self.assertEqual(2, len(parts), 'Production lifecycle main entry marker changed')
        runner = f.root / 'apt-helper.sh'
        runner.write_text(parts[0] + '''
HTTP_PROXY=$NQ_INPUT_UPPER_HTTP; HTTPS_PROXY=$NQ_INPUT_UPPER_HTTPS; NO_PROXY=$NQ_INPUT_UPPER_NO
http_proxy=$NQ_INPUT_LOWER_HTTP; https_proxy=$NQ_INPUT_LOWER_HTTPS; no_proxy=$NQ_INPUT_LOWER_NO
export HTTP_PROXY HTTPS_PROXY NO_PROXY http_proxy https_proxy no_proxy
ubuntu_apt update
[ "$http_proxy" = "$NQ_EXPECT_PARENT_HTTP" ] || fail 'Parent http proxy changed'
[ "$https_proxy" = "$NQ_EXPECT_PARENT_HTTPS" ] || fail 'Parent https proxy changed'
[ "$no_proxy" = "$NQ_EXPECT_PARENT_NO" ] || fail 'Parent no_proxy changed'
printf '%s\\n' PARENT_PROXY_UNCHANGED
''', encoding='utf-8', newline='\n')
        f.script('sudo', '''[ "$1" = '--preserve-env=http_proxy,https_proxy,no_proxy' ] || exit 1
[ "$http_proxy" = "$NQ_EXPECT_APT_HTTP" ] || exit 1
[ "$https_proxy" = "$NQ_EXPECT_APT_HTTPS" ] || exit 1
[ "$no_proxy" = "$NQ_EXPECT_APT_NO" ] || exit 1
printf '%s\\n' "$*" >> "$NQ_FIX_STATE/calls"
printf '%s\\n' SIMULATED_APT_PROXY_VERIFIED
''')
        for lower in (True, False):
            with self.subTest(lowercase_priority=lower):
                # Windows环境块不区分同名变量大小写；在Bash内部构造两组真实变量。
                values = {'NQ_INPUT_UPPER_HTTP': 'http://synthetic:upper@upper.invalid:8123',
                          'NQ_INPUT_UPPER_HTTPS': 'http://synthetic:upper@upper.invalid:8124',
                          'NQ_INPUT_UPPER_NO': 'upper.invalid',
                          'NQ_INPUT_LOWER_HTTP': 'http://synthetic:lower@lower.invalid:8123' if lower else '',
                          'NQ_INPUT_LOWER_HTTPS': 'http://synthetic:lower@lower.invalid:8124' if lower else '',
                          'NQ_INPUT_LOWER_NO': 'lower.invalid' if lower else ''}
                for short in ('HTTP', 'HTTPS', 'NO'):
                    values['NQ_EXPECT_PARENT_' + short] = values['NQ_INPUT_LOWER_' + short]
                    values['NQ_EXPECT_APT_' + short] = (values['NQ_INPUT_LOWER_' + short]
                                                       or values['NQ_INPUT_UPPER_' + short])
                f.clear_calls()
                result = subprocess.run([self.shell, shell_path(runner)], env=f.env | values,
                                        capture_output=True, text=True, encoding='utf-8', timeout=20)
                self.assertEqual(0, result.returncode, 'Production APT helper rejected the bounded proxy fixture')
                self.assertIn('SIMULATED_APT_PROXY_VERIFIED', result.stdout)
                self.assertIn('PARENT_PROXY_UNCHANGED', result.stdout)
                self.assertIn('--preserve-env=http_proxy,https_proxy,no_proxy', f.calls())
                self.assertIn('timeout --kill-after=10 850 apt-get', f.calls())
                self.assertNotIn('sudo -E', f.calls())
                for value in values.values():
                    if value:
                        self.assertFalse(value in result.stdout + result.stderr + f.calls(),
                                         'A proxy value appeared in APT helper logs or argv')

    def test_successful_update_known_rollback_and_backup_tamper_refusal(self):
        f = self.fixture; f.run('install'); old_config = f.config()
        f.run('update', *f.target_args(), '--yes')
        self.assertEqual('2\nFACTS_A\nFACTS_B\n', f.facts())
        self.assertEqual('1.0.1', (f.home / 'runtime/VERSION').read_text().strip())
        receipt, = f.receipts(); self.assertEqual('UPDATE_SUCCESS', receipt['RESULT'])
        self.assertEqual(digest(f.target_manifest), receipt['TARGET_MANIFEST_SHA256'])
        self.assertEqual('sha256:' + 'd' * 64, receipt['BACKEND_IMAGE'])
        backup = f.home / 'backups' / receipt['BACKUP_ID'] / 'database.dump'
        original = backup.read_bytes(); backup.write_bytes(b'TAMPERED'); f.clear_calls()
        f.run('rollback', '--yes', success=False); self.assertNotIn(' stop ', f.calls())
        self.assertEqual('2\nFACTS_A\nFACTS_B\n', f.facts()); backup.write_bytes(original)
        f.run('rollback', '--yes')
        self.assertEqual('1\nFACTS_A\n', f.facts()); self.assertEqual(old_config, f.config())
        self.assertEqual('1.0.0', (f.home / 'runtime/VERSION').read_text().strip())

    def test_rollback_rejects_tampered_receipt_digest_and_arbitrary_target(self):
        f = self.fixture
        f.run('install'); f.run('update', *f.target_args(), '--yes')
        transaction = next((f.home / 'runtime/transactions').iterdir())
        receipt_path = transaction / 'receipt.env'
        original_receipt = receipt_path.read_bytes()
        receipt_path.write_bytes(original_receipt.replace(b'BACKEND_IMAGE=sha256:' + b'd' * 64,
                                                        b'BACKEND_IMAGE=sha256:' + b'e' * 64))
        f.clear_calls(); f.run('rollback', '--yes', success=False)
        self.assertNotIn(' stop ', f.calls()); self.assertEqual('2\nFACTS_A\nFACTS_B\n', f.facts())
        receipt_path.write_bytes(original_receipt)
        target_path = transaction / 'target.env'; original_target = target_path.read_bytes()
        target_path.write_bytes(original_target.replace(b'BACKEND_IMAGE=sha256:' + b'd' * 64,
                                                      b'BACKEND_IMAGE=sha256:' + b'e' * 64))
        f.clear_calls(); f.run('rollback', '--yes', success=False)
        self.assertNotIn(' stop ', f.calls()); target_path.write_bytes(original_target)
        f.run('rollback', '--package', shell_path(f.target), '--yes', success=False)
        f.run('rollback', '--yes')
        self.assertEqual('1\nFACTS_A\n', f.facts())

    def test_failed_normal_restore_blocks_start_and_retry_same_backup_only(self):
        f = self.fixture
        f.run('install'); f.run('backup')
        backup = next((f.home / 'backups').iterdir())
        (f.state / 'facts').write_text('1\nFACTS_A\nFACTS_B\n', newline='\n')
        f.run('restore', shell_path(backup), success=False, NQ_FIX_FAIL='RESTORE')
        self.assertTrue((f.home / 'runtime/pending-restore.env').exists())
        f.clear_calls(); f.run('start', success=False); self.assertNotIn(' up ', f.calls())
        f.run('restore', shell_path(backup))
        self.assertEqual('1\nFACTS_A\n', f.facts())
        self.assertFalse((f.home / 'runtime/pending-restore.env').exists())
        f.run('start')

    def test_interrupted_target_start_retains_journal_blocks_start_and_recovers(self):
        f = self.fixture; f.run('install'); config = f.config()
        f.run('update', *f.target_args(), '--yes', success=False, NQ_FIX_FAIL='INTERRUPT')
        self.assertTrue((f.home / 'runtime/pending-update').exists())
        self.assertEqual('2\nFACTS_A\nFACTS_B\n', f.facts()); f.clear_calls()
        f.run('start', success=False); self.assertNotIn(' up ', f.calls())
        f.run('rollback', '--yes')
        self.assertEqual('1\nFACTS_A\n', f.facts()); self.assertEqual(config, f.config())
        self.assertFalse((f.home / 'runtime/pending-update').exists())


if __name__ == '__main__':
    unittest.main()
