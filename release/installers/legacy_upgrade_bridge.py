"""一次性 V34→V47 桥接；执行前须从独立可信渠道核验本文件及过渡清单。"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import sys
import time
import types
import urllib.request


class Refused(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise Refused(message)


def sha(path):
    with safe(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def safe(path):
    path = Path(path).absolute()
    require(path != Path(path.anchor) and path != Path.home(), 'UNSAFE_PATH')
    require('..' not in path.parts, 'PATH_TRAVERSAL_REFUSED')
    for part in (path, *path.parents):
        require(not part.is_symlink() and not part.is_junction(), 'LINK_PATH_REFUSED')
    require(not any(c in str(path) for c in '\n\r\x00,$#\'"='), 'AMBIGUOUS_PATH')
    return path


def metadata(path):
    raw = safe(path).read_bytes()
    require(len(raw) <= 16384 and raw.endswith(b'\n') and b'\r' not in raw
            and b'\0' not in raw and not raw.startswith(b'\xef\xbb\xbf'), 'INVALID_METADATA')
    result = {}
    for line in raw.decode('utf-8').splitlines():
        key, sep, value = line.partition('=')
        require(sep and re.fullmatch('[A-Z][A-Z0-9_]*', key) and key not in result
                and value, 'INVALID_METADATA_FIELDS')
        result[key] = value
    return result


def run(args, timeout=180, input=None, env=None):
    try:
        p = subprocess.run([str(a) for a in args], input=input, capture_output=True,
                           timeout=timeout, env=env)
    except (OSError, subprocess.TimeoutExpired):
        raise Refused('EXTERNAL_COMMAND_UNAVAILABLE_OR_TIMEOUT') from None
    # 工具错误可能含配置/凭据；只传播固定阶段，不打印原始 stdout/stderr。
    require(p.returncode == 0, 'EXTERNAL_COMMAND_FAILED')
    require(len(p.stdout) <= 32 * 1024 * 1024, 'EXTERNAL_OUTPUT_LIMIT')
    return p.stdout.decode('utf-8').strip()


def protect(path):
    if os.name == 'nt':
        sid = run(['whoami', '/user', '/fo', 'csv', '/nh']).split(',')[-1].strip('"')
        require(re.fullmatch(r'S-1-[0-9-]+', sid), 'WINDOWS_IDENTITY_UNAVAILABLE')
        run(['icacls', path, '/inheritance:r', '/grant:r', f'*{sid}:(OI)(CI)F',
             '*S-1-5-18:(OI)(CI)F'])
    else:
        path.chmod(0o700)


def atomic(path, content):
    path = safe(path)
    # 崩溃遗留副本保留供审计；独占操作锁下用新临时身份推进已验证的规范状态。
    temp = path.with_name('.' + path.name + '.' + secrets.token_hex(8) + '.tmp')
    with temp.open('xb') as stream:
        os.chmod(temp, 0o600)
        stream.write(content)
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temp, path)
    if os.name != 'nt':
        fd = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(fd)
        finally:
            os.close(fd)


def write_json(path, value):
    atomic(path, (json.dumps(value, sort_keys=True, indent=2) + '\n').encode())


def write_env(path, value):
    atomic(path, ''.join(f'{k}={v}\n' for k, v in sorted(value.items())).encode())


PACKAGE_FIELDS = set('FORMAT VERSION SOURCE_SHA RELEASE_SOURCE_HASH SCHEMA_VERSION ARCH ARCHIVE '
                     'ARCHIVE_SHA256 BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE BACKEND_CONFIG_DIGEST '
                     'FRONTEND_CONFIG_DIGEST POSTGRES_CONFIG_DIGEST COMPOSE_SHA256 RUNTIME_SHA256 '
                     'PS_INSTALLER_SHA256 SH_INSTALLER_SHA256'.split())
ASSETS = {'runtime/compose.yml': 'COMPOSE_SHA256', 'runtime/runtime.yml': 'RUNTIME_SHA256',
          'installers/nexusquant.ps1': 'PS_INSTALLER_SHA256', 'installers/nexusquant.sh': 'SH_INSTALLER_SHA256'}
INSTALLED = {'compose.yml': 'COMPOSE_SHA256', 'runtime.yml': 'RUNTIME_SHA256',
             'nexusquant.ps1': 'PS_INSTALLER_SHA256', 'nexusquant.sh': 'SH_INSTALLER_SHA256'}


def package(root, expected, arch):
    root = safe(root)
    file = root / f'package-{arch}.env'
    require(re.fullmatch('[a-f0-9]{64}', expected) and sha(file) == expected, 'UNTRUSTED_PACKAGE')
    m = metadata(file)
    require(set(m) == PACKAGE_FIELDS and m['FORMAT'] == '1' and m['ARCH'] == arch
            and m['ARCHIVE'] == f'images-{arch}.tar', 'PACKAGE_CONTRACT_MISMATCH')
    require(re.fullmatch('[a-f0-9]{40}', m['SOURCE_SHA']) and
            re.fullmatch('[a-f0-9]{64}', m['RELEASE_SOURCE_HASH']), 'SOURCE_IDENTITY_INVALID')
    for key in ('BACKEND_IMAGE', 'FRONTEND_IMAGE', 'POSTGRES_IMAGE', 'BACKEND_CONFIG_DIGEST',
                'FRONTEND_CONFIG_DIGEST', 'POSTGRES_CONFIG_DIGEST'):
        require(re.fullmatch('sha256:[a-f0-9]{64}', m[key]), 'MUTABLE_IMAGE_REFUSED')
    for file, key in ASSETS.items():
        require(sha(root / file) == m[key], 'PACKAGE_ASSET_CHANGED')
    require(sha(root / m['ARCHIVE']) == m['ARCHIVE_SHA256'], 'PACKAGE_ARCHIVE_CHANGED')
    return m


def process_alive(pid):
    require(isinstance(pid, int) and pid > 0, 'INVALID_LOCK_OWNER')
    if os.name != 'nt':
        try:
            os.kill(pid, 0)
            return True
        except ProcessLookupError:
            return False
        except PermissionError:
            return True
    import ctypes
    from ctypes import wintypes
    api = ctypes.WinDLL('kernel32', use_last_error=True)
    api.OpenProcess.argtypes = [wintypes.DWORD, wintypes.BOOL, wintypes.DWORD]
    api.OpenProcess.restype = wintypes.HANDLE
    handle = api.OpenProcess(0x100000, False, pid)
    if not handle:
        return ctypes.get_last_error() != 87
    api.WaitForSingleObject.argtypes = [wintypes.HANDLE, wintypes.DWORD]
    api.CloseHandle.argtypes = [wintypes.HANDLE]
    try:
        return api.WaitForSingleObject(handle, 0) != 0
    finally:
        api.CloseHandle(handle)


@contextmanager
def installation_lock(root, recover_hash=None):
    """同时占用两种原版操作锁；不自动清理无法证明归属的遗留锁。"""
    runtime = root / 'runtime'
    lockdir = runtime / '.operation-lock'
    handle = None
    lockfile = None
    if os.name == 'nt':
        import ctypes
        from ctypes import wintypes
        api = ctypes.WinDLL('kernel32', use_last_error=True)
        api.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                                   wintypes.LPVOID, wintypes.DWORD, wintypes.DWORD, wintypes.HANDLE]
        api.CreateFileW.restype = wintypes.HANDLE
        handle = api.CreateFileW(str(runtime / 'operation.lock'), 0xC0000000, 0, None, 4, 0x80, None)
        require(handle != wintypes.HANDLE(-1).value, 'LEGACY_WINDOWS_LOCK_BUSY')
    else:
        import fcntl
        lockfile = (runtime / 'operation.lock').open('a+b')
        try:
            fcntl.flock(lockfile, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except OSError:
            lockfile.close()
            raise Refused('BRIDGE_LOCK_BUSY') from None
    owned = False
    reclaim = runtime / '.lock-recovery'
    try:
        if lockdir.exists():
            require(recover_hash, 'LEGACY_POSIX_LOCK_BUSY_OR_STALE')
            reclaim.mkdir()
            try:
                owner = json.loads(safe(lockdir / 'bridge-owner').read_bytes())
                require(owner['transitionSha256'] == recover_hash and not process_alive(owner['pid']),
                        'BRIDGE_LOCK_OWNER_STILL_ACTIVE_OR_UNKNOWN')
                (lockdir / 'pid').unlink(missing_ok=True)
                (lockdir / 'bridge-owner').unlink()
                lockdir.rmdir()
            finally:
                reclaim.rmdir()
        lockdir.mkdir()
        owned = True
        # 附加 owner 使旧 shell 即使 PID 命名空间不同，也不能 rmdir 后误接管锁。
        (lockdir / 'bridge-owner').write_text(json.dumps({'pid': os.getpid(),
                    'transitionSha256': recover_hash}), encoding='ascii')
        (lockdir / 'pid').write_text(str(os.getpid()) + '\n', encoding='ascii')
        yield
    finally:
        if owned:
            (lockdir / 'pid').unlink(missing_ok=True)
            (lockdir / 'bridge-owner').unlink(missing_ok=True)
            lockdir.rmdir()
        if handle is not None:
            api.CloseHandle.argtypes = [wintypes.HANDLE]
            api.CloseHandle(handle)
        if lockfile:
            lockfile.close()


class Bridge:
    def __init__(self, args):
        self.args = args
        self.root = safe(args.install_root)
        self.runtime = self.root / 'runtime'
        self.transition = safe(args.transition)
        require(sha(self.transition) == args.trusted_transition_sha256, 'UNTRUSTED_TRANSITION')
        self.trust = json.loads(self.transition.read_bytes())
        fields = {'format', 'bridgeSourceCommit', 'bridgeSha256', 'baselineToolSha256',
                  'sourceManifestSha256', 'targetManifestSha256', 'targetArchiveSha256',
                  'targetSourceCommit', 'architecture', 'installationConfigSha256', 'projectName'}
        require(set(self.trust) == fields and self.trust['format'] == 'NQ_LEGACY_BRIDGE_V1',
                'INVALID_TRANSITION_CONTRACT')
        for key in fields - {'format', 'bridgeSourceCommit', 'targetSourceCommit', 'architecture', 'projectName'}:
            require(re.fullmatch('[a-f0-9]{64}', self.trust[key]), 'INVALID_TRANSITION_HASH')
        for key in ('bridgeSourceCommit', 'targetSourceCommit'):
            require(re.fullmatch('[a-f0-9]{40}', self.trust[key]) or
                    (key == 'bridgeSourceCommit' and self.trust[key] == 'UNCOMMITTED'
                     and args.local_candidate), 'INVALID_TRANSITION_COMMIT')
        require(sha(Path(__file__)) == self.trust['bridgeSha256'], 'BRIDGE_ARTIFACT_MISMATCH')
        require(self.trust['architecture'] in ('amd64', 'arm64') and
                re.fullmatch('[a-z0-9][a-z0-9-]{0,48}', self.trust['projectName']), 'INVALID_PLATFORM')
        tool = safe(args.baseline_tool)
        tool_bytes = tool.read_bytes()
        require(hashlib.sha256(tool_bytes).hexdigest() == self.trust['baselineToolSha256'], 'BASELINE_TOOL_MISMATCH')
        # 只执行刚核验的源码字节，既不写缓存，也不接受未绑定的 pyc 替代源码。
        self.baseline = types.ModuleType('bridge_business_baseline')
        self.baseline.__file__ = str(tool)
        exec(compile(tool_bytes, str(tool), 'exec'), self.baseline.__dict__)
        self.project = self.trust['projectName']
        self.directory = None
        self.record = None
        self.env = {k: v for k, v in os.environ.items() if not k.startswith('COMPOSE_')}
        for k in ('NQ_HOME NQ_VERSION SOURCE_SHA RELEASE_SOURCE_HASH SCHEMA_VERSION PACKAGE_MANIFEST_SHA256 '
                  'AUTO_UPDATE ARCH RUNTIME_PROFILE BACKEND_IMAGE FRONTEND_IMAGE POSTGRES_IMAGE '
                  'BACKEND_MEMORY BACKEND_CPUS FRONTEND_MEMORY FRONTEND_CPUS POSTGRES_MEMORY POSTGRES_CPUS '
                  'DB_PASSWORD RUNTIME_DB_PASSWORD DB_ROLE_FORMAT JWT_SECRET CREDENTIALS_KEY FRONTEND_PORT '
                  'BACKEND_PORT PROJECT_NAME DOCKER_DEFAULT_PLATFORM').split():
            self.env.pop(k, None)

    def docker(self, *args, timeout=180, input=None):
        return run(['docker', *args], timeout, input, self.env)

    def compose(self, *args):
        return self.docker('compose', '--env-file', self.root / 'config/runtime.env', '-p', self.project,
                           '-f', self.runtime / 'compose.yml', *args, timeout=600)

    def sql(self, container, query):
        return self.docker('exec', '-i', container, 'psql', '-XqAt', '-v', 'ON_ERROR_STOP=1',
                           '-U', 'nexusquant', '-d', 'nexus_quant',
                           input=("SET statement_timeout='30s'; SET lock_timeout='5s'; " + query).encode())

    def image(self, m, component):
        for key in (component + '_IMAGE', component + '_CONFIG_DIGEST'):
            try:
                value = json.loads(self.docker('image', 'inspect', m[key]))[0]
            except Refused:
                continue
            require(value['Id'] == m[key] and value['Os'] == 'linux'
                    and value['Architecture'] == m['ARCH'], 'IMAGE_IDENTITY_MISMATCH')
            return m[key]
        raise Refused('DECLARED_IMAGE_NOT_LOADED')

    def preflight(self):
        require(not any((self.runtime / p).exists() for p in ('pending-update', 'pending-restore.env',
                    'first-install.env', 'first-install-pending', 'bridge-active.env')), 'PENDING_LIFECYCLE')
        c = metadata(self.root / 'config/runtime.env')
        require(sha(self.root / 'config/runtime.env') == self.trust['installationConfigSha256'],
                'ORIGINAL_INSTALLATION_CHANGED')
        require(c['NQ_HOME'] == self.root.as_posix() and c['PROJECT_NAME'] == self.project and
                c['NQ_VERSION'] == '1.0.0' and c['SCHEMA_VERSION'] == '34' and c['AUTO_UPDATE'] == 'OFF',
                'NOT_EXPECTED_LEGACY_INSTALLATION')
        require(c['PACKAGE_MANIFEST_SHA256'] == self.trust['sourceManifestSha256'], 'WRONG_SOURCE_PACKAGE')
        self.source = package(self.args.source_package, self.trust['sourceManifestSha256'], c['ARCH'])
        self.target = package(self.args.target_package, self.trust['targetManifestSha256'], c['ARCH'])
        require(self.source['VERSION'] == '1.0.0' and self.source['SCHEMA_VERSION'] == '34'
                and self.target['VERSION'] == '1.0.1' and self.target['SCHEMA_VERSION'] == '47'
                and self.target['SOURCE_SHA'] == self.trust['targetSourceCommit']
                and sha(self.args.target_archive) == self.trust['targetArchiveSha256']
                and c['ARCH'] == self.trust['architecture'], 'TRANSITION_IDENTITY_MISMATCH')
        require(sha(self.runtime / 'package.env') == self.trust['sourceManifestSha256']
                and (self.runtime / 'VERSION').read_text().strip() == '1.0.0', 'INSTALLED_PACKAGE_CHANGED')
        for name, key in INSTALLED.items():
            require(sha(self.runtime / name) == self.source[key], 'ORIGINAL_INSTALLER_OR_ASSET_CHANGED')
        for key in ('DB_PASSWORD', 'JWT_SECRET', 'CREDENTIALS_KEY'):
            require(re.fullmatch('[a-f0-9]{64}', c[key]), 'INVALID_ORIGINAL_CREDENTIAL_IDENTITY')
        require('RUNTIME_DB_PASSWORD' not in c and 'DB_ROLE_FORMAT' not in c, 'NOT_LEGACY_ROLE_CONFIG')
        for component in ('BACKEND', 'FRONTEND', 'POSTGRES'):
            require(c[component + '_IMAGE'] in (self.source[component + '_IMAGE'],
                    self.source[component + '_CONFIG_DIGEST']), 'SOURCE_IMAGE_NOT_DECLARED')
            self.image(self.source, component)
            self.image(self.target, component)
        context = self.docker('context', 'inspect')
        endpoint = self.env.get('DOCKER_HOST') or json.loads(context)[0]['Endpoints']['docker']['Host']
        require(endpoint.startswith(('npipe://', 'unix://')), 'REMOTE_DOCKER_REFUSED')
        containers = json.loads(self.docker('inspect', self.pg_container()))
        mounts = containers[0]['Mounts']
        require(any(Path(m['Source']).as_posix().lower() == (self.root / 'data/postgres').as_posix().lower()
                    and m['Destination'] == '/var/lib/postgresql/data' for m in mounts), 'WRONG_DATABASE_MOUNT')
        require(containers[0]['Config']['Labels']['com.docker.compose.project'] == self.project,
                'WRONG_DATABASE_PROJECT')
        self.config = c
        return {'preflight': 'VERIFIED',
                'sourceManifestSha256': self.trust['sourceManifestSha256'],
                'targetManifestSha256': self.trust['targetManifestSha256']}

    def pg_container(self):
        value = self.compose('ps', '-q', '--all', 'postgres')
        require(re.fullmatch('[a-f0-9]{12,64}', value), 'DATABASE_CONTAINER_UNAVAILABLE')
        return value

    def phase(self, value):
        self.record['phase'] = value
        write_json(self.directory / 'phase.json', self.record)

    def capture(self, container, reference=None):
        return self.baseline.capture(container, 'nexus_quant', 'nexusquant', 'bridge',
                                     self.trust['targetSourceCommit'], reference)

    def facts(self, before, after, strict=True):
        result = self.baseline.compare(before, after)
        # 原始 DIFFERENT 永远保留；只准显式解释唯一新增权益观测，原键/字段必须原样。
        allowed = all(x.get('table') == 'account_equity_measurements' and not x.get('missing')
                      and not x.get('changed') and len(x.get('added', [])) == 1
                      for x in result['changes'])
        require(result['result'] == 'MATCH' or (not strict and allowed), 'ORIGINAL_BUSINESS_FACTS_CHANGED')
        return result

    def auth_hash(self, container):
        # 比较旧列的全部值；新增 email 等空列不应被误报为旧密码/身份改变。
        if 'authColumns' not in self.record:
            self.record['authColumns'] = {}
            self.record['authKeys'] = {}
            for table in ('users', 'roles', 'user_roles'):
                columns = self.sql(container, "SELECT column_name FROM information_schema.columns WHERE table_schema='public' "
                                   f"AND table_name='{table}' ORDER BY column_name").splitlines()
                require(columns and all(re.fullmatch('[a-z_][a-z0-9_]*', c) for c in columns), 'INVALID_AUTH_COLUMN_CONTRACT')
                self.record['authColumns'][table] = columns
                identity = "user_id::text||':'||role_id::text" if table == 'user_roles' else 'id::text'
                keys = self.sql(container, f'SELECT {identity} FROM {table} ORDER BY {identity}').splitlines()
                require(keys and len(keys) <= 10000 and all(re.fullmatch('[0-9]+(:[0-9]+)?', k) for k in keys), 'INVALID_AUTH_KEYS')
                self.record['authKeys'][table] = keys
        result = {}
        require(set(self.record['authColumns']) == {'users', 'roles', 'user_roles'}, 'AUTH_TABLE_CONTRACT_CHANGED')
        for table, columns in self.record['authColumns'].items():
            require(all(re.fullmatch('[a-z_][a-z0-9_]*', c) for c in columns), 'INVALID_AUTH_COLUMN_CONTRACT')
            projection = ','.join('"' + c + '"' for c in columns)
            require(int(self.sql(container, f'SELECT count(*) FROM {table}')) <= 10000, 'AUTH_FIXTURE_LIMIT')
            keys = self.record['authKeys'][table]
            require(keys and all(re.fullmatch('[0-9]+(:[0-9]+)?', k) for k in keys), 'INVALID_AUTH_KEYS')
            identity = "user_id::text||':'||role_id::text" if table == 'user_roles' else 'id::text'
            where = identity + ' IN (' + ','.join("'" + k + "'" for k in keys) + ')'
            require(int(self.sql(container, f'SELECT count(*) FROM {table} WHERE {where}')) == len(keys), 'ORIGINAL_AUTH_KEYS_MISSING')
            result[table] = self.sql(container, "SELECT md5(coalesce(string_agg(to_jsonb(t)::text,'' ORDER BY to_jsonb(t)::text),'')) "
                                    f'FROM (SELECT {projection} FROM {table} WHERE {where}) t')
        return hashlib.sha256(json.dumps(result, sort_keys=True).encode()).hexdigest()

    def stop_services(self, services):
        # 恢复不可依赖可能写到一半的 Compose/配置；仅按已绑定项目及服务标签停机。
        for service in services:
            filters = ('--filter', 'label=com.docker.compose.project=' + self.project,
                       '--filter', 'label=com.docker.compose.service=' + service)
            ids = self.docker('ps', '-q', *filters).splitlines()
            for ident in ids:
                require(re.fullmatch('[a-f0-9]{12,64}', ident), 'INVALID_CONTAINER_IDENTITY')
                self.docker('stop', '--timeout', '30', ident)
            require(not self.docker('ps', '-q', *filters), 'SERVICE_STILL_RUNNING')

    def stop(self):
        self.stop_services(('frontend', 'backend', 'maintenance'))

    def snapshot(self, image, data, archive, restore=False):
        args = ['run', '--rm', '--network', 'none', '--entrypoint', 'sh',
                '--mount', f'type=bind,src={data},dst=/data' + ('' if restore else ',readonly'),
                '--mount', f'type=bind,src={archive.parent},dst=/backup' + (',readonly' if restore else ''), image]
        command = ('test ! -e /data/PG_VERSION && test -z "$(ls -A /data)" && tar xpf /backup/cluster.tar -C /data'
                   if restore else 'test "$(cat /data/PG_VERSION)" = 16 && test ! -e /data/postmaster.pid '
                   '&& test -z "$(find /data -type l -print -quit)" && tar cpf /backup/cluster.tar -C /data .')
        self.docker(*args, '-ec', command, timeout=600)

    def clone_start(self, name, image, data):
        self.docker('run', '-d', '--name', name, '--network', 'none', '--mount',
                    f'type=bind,src={data},dst=/var/lib/postgresql/data', image)
        for _ in range(60):
            try:
                require(self.sql(name, 'SELECT 1') == '1', 'DATABASE_NOT_READY')
                return
            except Refused:
                time.sleep(1)
        raise Refused('CLONE_START_TIMEOUT')

    def stop_clone(self, name):
        self.docker('stop', '--time', '30', name)
        self.docker('rm', name)

    def database_identity(self, container):
        return self.sql(container, "SELECT json_build_object('system', (SELECT system_identifier::text FROM pg_control_system()),"
                        "'locale', (SELECT json_agg(json_build_object('name',datname,'encoding',encoding,'collate',datcollate,"
                        "'ctype',datctype,'provider',datlocprovider,'version',datcollversion) ORDER BY datname) FROM pg_database),"
                        "'extensions',(SELECT json_agg(json_build_object('name',extname,'version',extversion) ORDER BY extname) FROM pg_extension))")

    def verify_clone(self, image, name, before, auth, identity):
        require(self.database_identity(name) == identity, 'PHYSICAL_IDENTITY_OR_LOCALE_CHANGED')
        self.facts(before, self.capture(name, before))
        require(self.auth_hash(name) == auth, 'ORIGINAL_AUTHENTICATION_CHANGED')
        # default collation 的版本由 pg_database 管理；其 pg_collation.collversion 合法为空。
        require(self.sql(name, "SELECT count(*) FROM pg_collation WHERE collversion IS NOT NULL AND collversion IS DISTINCT FROM pg_collation_actual_version(oid)") == '0',
                'COLLATION_VERSION_MISMATCH')
        require(self.sql(name, "SELECT count(*) FROM pg_database WHERE datallowconn AND datcollversion IS DISTINCT FROM pg_database_collation_actual_version(oid)") == '0',
                'DATABASE_COLLATION_VERSION_MISMATCH')
        require(self.sql(name, "SELECT count(*) FROM pg_index WHERE NOT indisvalid OR NOT indisready") == '0', 'INVALID_INDEX')

    def execute(self):
        require(self.args.confirm == 'BRIDGE 1.0.0 TO 1.0.1', 'EXPLICIT_CONFIRMATION_REQUIRED')
        with installation_lock(self.root, sha(self.transition)):
            self.preflight()
            ident = time.strftime('%Y%m%dT%H%M%S', time.gmtime()) + '000Z-' + secrets.token_hex(4)
            self.directory = self.runtime / 'bridge-transactions' / ident
            self.directory.mkdir(parents=True)
            protect(self.directory)
            self.record = {'format': 'NQ_LEGACY_BRIDGE_TRANSACTION_V1', 'id': ident,
                           'transitionSha256': sha(self.transition), 'phase': 'VERIFIED',
                           'sourceConfigSha256': self.trust['installationConfigSha256'], 'files': {}}
            for name in (*INSTALLED, 'package.env', 'VERSION'):
                shutil.copyfile(self.runtime / name, self.directory / name)
            shutil.copyfile(self.root / 'config/runtime.env', self.directory / 'runtime.env')
            self.record['files'] = {p.name: sha(p) for p in self.directory.iterdir() if p.is_file()}
            staged = self.directory / 'target-assets'
            staged.mkdir()
            for file, key in ASSETS.items():
                destination = staged / Path(file).name
                shutil.copyfile(safe(self.args.target_package) / file, destination)
                require(sha(destination) == self.target[key], 'TARGET_CHANGED_DURING_STAGING')
            manifest = safe(self.args.target_package) / f'package-{self.config["ARCH"]}.env'
            shutil.copyfile(manifest, staged / 'package.env')
            require(sha(staged / 'package.env') == self.trust['targetManifestSha256'], 'TARGET_MANIFEST_CHANGED')
            self.phase('VERIFIED')
            write_env(self.runtime / 'bridge-active.env', {'FORMAT': '1', 'TRANSACTION_ID': ident,
                       'TRANSITION_SHA256': sha(self.transition)})
            try:
                self.stop()
                self.phase('QUIESCED')
                self.compose('up', '-d', '--no-build', '--pull', 'never', '--wait', 'postgres')
                container = self.pg_container()
                before = self.capture(container)
                require(before['schema'][-1]['version'] == '34', 'SOURCE_SCHEMA_NOT_V34')
                require(self.sql(container, "SELECT count(*) FROM pg_tablespace WHERE spcname NOT IN ('pg_default','pg_global')") == '0',
                        'EXTERNAL_TABLESPACE_UNSUPPORTED')
                write_json(self.directory / 'business-before.json', before)
                self.record['authHash'] = self.auth_hash(container)
                self.record['databaseIdentity'] = self.database_identity(container)
                # 起点是未改变的旧库；在停库/备份之前先持久化稳定事务事实。
                self.phase('QUIESCED')
                backup = self.directory / 'backup'
                backup.mkdir()
                (backup / 'assets').mkdir()
                for file in (*INSTALLED, 'package.env', 'VERSION'):
                    shutil.copyfile(self.directory / file, backup / 'assets' / file)
                shutil.copyfile(self.directory / 'runtime.env', backup / 'runtime.env')
                dump = '/tmp/nq-bridge-' + ident + '.dump'
                self.docker('exec', container, 'pg_dump', '-U', 'nexusquant', '-d', 'nexus_quant', '-Fc', '-f', dump)
                self.docker('cp', container + ':' + dump, backup / 'database.dump')
                self.docker('exec', container, 'rm', '-f', dump)
                write_env(backup / 'manifest.env', {'FORMAT': '1', 'APP_VERSION': '1.0.0', 'SCHEMA_VERSION': '34',
                    'POSTGRESQL_MAJOR': '16', 'TIMESTAMP': ident, 'DATABASE_SHA256': sha(backup / 'database.dump'),
                    'CONFIG_SHA256': sha(backup / 'runtime.env'), 'PACKAGE_MANIFEST_SHA256': self.trust['sourceManifestSha256'],
                    'VERSION_SHA256': sha(backup / 'assets/VERSION')})
                self.record['backupManifestSha256'] = sha(backup / 'manifest.env')
                self.compose('stop', '--timeout', '30', 'postgres')
                self.snapshot(self.config['POSTGRES_IMAGE'], self.root / 'data/postgres', backup / 'cluster.tar')
                self.record['clusterSha256'] = sha(backup / 'cluster.tar')
                clone = self.directory / 'restore-proof'
                clone.mkdir()
                self.snapshot(self.config['POSTGRES_IMAGE'], clone, backup / 'cluster.tar', True)
                name = 'nq-bridge-proof-' + secrets.token_hex(6)
                old_image = self.config['POSTGRES_IMAGE']
                new_image = self.image(self.target, 'POSTGRES')
                self.clone_start(name, old_image, clone)
                try:
                    self.verify_clone(old_image, name, before, self.record['authHash'], self.record['databaseIdentity'])
                    old_version = self.docker('exec', name, 'postgres', '--version')
                    old_locale = self.docker('exec', name, 'locale', '-a')
                    old_libs = self.docker('exec', name, 'ldd', '/usr/lib/postgresql/16/bin/postgres')
                    old_packages = self.docker('exec', name, 'dpkg-query', '-W', '-f=${binary:Package} ${Version}\n')
                finally:
                    self.stop_clone(name)
                self.clone_start(name, new_image, clone)
                try:
                    self.verify_clone(new_image, name, before, self.record['authHash'], self.record['databaseIdentity'])
                    require(self.docker('exec', name, 'postgres', '--version') == old_version, 'PG_BINARY_VERSION_CHANGED')
                    require(self.docker('exec', name, 'locale', '-a') == old_locale, 'LOCALE_SET_CHANGED')
                    new_libs = self.docker('exec', name, 'ldd', '/usr/lib/postgresql/16/bin/postgres')
                    new_packages = self.docker('exec', name, 'dpkg-query', '-W', '-f=${binary:Package} ${Version}\n')
                    old_map = dict(line.split(' ', 1) for line in old_packages.splitlines())
                    new_map = dict(line.split(' ', 1) for line in new_packages.splitlines())
                    require(set(old_map) == set(new_map) and {k.split(':')[0] for k in old_map if old_map[k] != new_map[k]}
                            == {'liblzma5', 'xz-utils'}, 'UNQUALIFIED_OS_PACKAGE_DIFFERENCE')
                    require(all(new_map.get(k) == '5.8.1-1+deb13u2' for k in new_map
                                if k.split(':')[0] in ('liblzma5', 'xz-utils')),
                            'REQUIRED_XZ_SECURITY_UPDATE_MISSING')
                    self.docker('exec', name, 'pg_amcheck', '-U', 'nexusquant', '--all', '--install-missing', timeout=600)
                    write_json(self.directory / 'image-transition.json', {'old': old_image, 'new': new_image,
                        'version': old_version, 'oldLibraries': old_libs, 'newLibraries': new_libs,
                        'oldPackages': old_map, 'newPackages': new_map,
                        'restoredFacts': 'MATCH', 'physicalIdentity': 'MATCH', 'indexCheck': 'PASS'})
                finally:
                    self.stop_clone(name)
                self.phase('BACKUP_VERIFIED')
                # 两个原版标记互相阻止 rollback/restore，旧安装器不消费桥接事务或回执。
                require(not (self.runtime / 'transactions' / ident).exists(), 'LEGACY_TRANSACTION_COLLISION')
                atomic(self.runtime / 'pending-update', (ident + '\n').encode())
                write_env(self.runtime / 'pending-restore.env', {'FORMAT': '1', 'BACKUP_PATH': str(backup),
                           'BACKUP_MANIFEST_SHA256': self.record['backupManifestSha256']})
                self.phase('TARGET_STAGED')
                config = self.config.copy()
                config['RUNTIME_DB_PASSWORD'] = secrets.token_hex(32)
                config['DB_ROLE_FORMAT'] = '2'
                for key in ('SOURCE_SHA', 'RELEASE_SOURCE_HASH', 'SCHEMA_VERSION', 'ARCH'):
                    config[key] = self.target[key]
                config['NQ_VERSION'] = self.target['VERSION']
                config['PACKAGE_MANIFEST_SHA256'] = self.trust['targetManifestSha256']
                for component in ('BACKEND', 'FRONTEND', 'POSTGRES'):
                    config[component + '_IMAGE'] = self.image(self.target, component)
                write_env(self.directory / 'target-runtime.env', config)
                self.record['targetConfigSha256'] = sha(self.directory / 'target-runtime.env')
                self.record['databaseMayHaveChanged'] = True
                self.phase('DB_TRANSITION_STARTED')
                for file in ASSETS:
                    require(sha(staged / Path(file).name) == self.target[ASSETS[file]], 'STAGED_TARGET_CHANGED')
                    atomic(self.runtime / Path(file).name, (staged / Path(file).name).read_bytes())
                os.chmod(self.runtime / 'runtime.yml', 0o644)
                atomic(self.runtime / 'package.env', (staged / 'package.env').read_bytes())
                atomic(self.root / 'config/runtime.env', (self.directory / 'target-runtime.env').read_bytes())
                self.compose('up', '-d', '--no-build', '--pull', 'never', '--force-recreate', '--wait', 'postgres')
                container = self.pg_container()
                require(self.database_identity(container) == self.record['databaseIdentity'], 'TARGET_DATA_REINITIALIZED')
                self.compose('--profile', 'maintenance', 'run', '--rm', '--no-deps', '--pull', 'never', 'maintenance')
                self.compose('--profile', 'maintenance', 'run', '--rm', '--no-deps', '--pull', 'never',
                             'maintenance', '--verify-runtime')
                require(self.sql(container, "SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1") == '47', 'TARGET_SCHEMA_MISMATCH')
                comparison = self.facts(before, self.capture(container, before))
                write_json(self.directory / 'business-after-migration.json', comparison)
                require(self.auth_hash(container) == self.record['authHash'], 'AUTHENTICATION_CHANGED')
                # 只做无租户上下文的真实负例；正向租户访问由产品认证HTTP链验证，禁止伪造上下文。
                require(self.sql(container, 'SET ROLE nq_runtime; SELECT count(*) FROM accounts') == '0', 'RLS_CONTEXTLESS_READ_LEAK')
                require(self.sql(container, "SELECT count(*) FROM accounts WHERE tenant_id <> '00000000-0000-4000-8000-000000000001'::uuid") == '0',
                        'LEGACY_ACCOUNT_TENANT_MISMATCH')
                write_json(self.directory / 'database-role-validation.json', {'maintenanceVerifyRuntime': 'PASS',
                    'contextlessAccountRows': 0, 'originalAccountsTenant': 'NQ_LOCAL', 'schema': '47'})
                self.phase('ROLES_AND_SCHEMA_VERIFIED')
                self.compose('up', '-d', '--no-build', '--pull', 'never', '--no-deps', '--force-recreate',
                             '--wait', '--wait-timeout', '300', 'backend')
                self.compose('up', '-d', '--no-build', '--pull', 'never', '--no-deps', '--force-recreate',
                             '--wait', '--wait-timeout', '120', 'frontend')
                self.verify_runtime(config)
                after = self.capture(container, before)
                write_json(self.directory / 'business-after.json', after)
                write_json(self.directory / 'business-comparison.json', self.facts(before, after, False))
                self.phase('TARGET_RUNTIME_VERIFIED')
                atomic(self.runtime / 'VERSION', b'1.0.1\n')
                self.phase('COMMITTED')
                write_json(self.directory / 'receipt.json', self.record)
                self.clear_guards()
                return {'result': 'COMMITTED', 'transaction': ident}
            except BaseException:
                # 不自动用旧镜像接管可能已迁移的库；恢复必须由持久事务和再次明确授权驱动。
                try:
                    self.stop()
                    self.stop_services(('postgres',))
                except Refused:
                    pass
                try:
                    self.record['failureClass'] = ('FAILED_AFTER_DATABASE_CHANGE' if self.record.get('databaseMayHaveChanged')
                                                   else 'FAILED_BEFORE_DATABASE_CHANGE')
                    self.record['recovery'] = 'RECOVERY_REQUIRED'
                    write_json(self.directory / 'failure.json', self.record)
                    self.phase(self.record['phase'])
                except (OSError, Refused):
                    pass
                raise

    def verify_runtime(self, config):
        container = self.compose('ps', '-q', 'backend')
        value = json.loads(self.docker('inspect', container))[0]
        env = dict(x.split('=', 1) for x in value['Config']['Env'] if '=' in x)
        require(value['Image'] in (self.target['BACKEND_IMAGE'], self.target['BACKEND_CONFIG_DIGEST']), 'RUNNING_BACKEND_IMAGE_MISMATCH')
        require(env.get('NQ_PROD_DB_USER') == 'nq_runtime' and env.get('NQ_PROD_DB_PASSWORD') == config['RUNTIME_DB_PASSWORD']
                and config['DB_PASSWORD'] not in env.values(), 'MAINTENANCE_CREDENTIAL_IN_APPLICATION')
        for key in ('NQ_LIVE_ENABLED', 'NQ_REAL_EXCHANGE_ENABLED', 'NQ_REAL_PROVIDER_ENABLED', 'NQ_REAL_CLIENT_ENABLED'):
            require(env.get(key) == 'false', 'LIVE_OR_PROVIDER_NOT_DISABLED')
        require(env.get('NQ_VERSION') == '1.0.1' and env.get('SOURCE_SHA') == self.trust['targetSourceCommit']
                and env.get('PACKAGE_MANIFEST_SHA256') == self.trust['targetManifestSha256']
                and env.get('SCHEMA_VERSION') == '47', 'RUNNING_PACKAGE_IDENTITY_MISMATCH')
        http = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        # 产品版本接口受认证保护；桥接器不索取业务用户口令，也不绕过认证合同。
        with http.open(f'http://127.0.0.1:{config["BACKEND_PORT"]}/actuator/health', timeout=15) as response:
            health = json.loads(response.read())
        require(health.get('status') == 'UP', 'HTTP_HEALTH_FAILED')

    def load_transaction(self):
        ident = self.args.transaction
        require(ident and re.fullmatch('[0-9]{8}T[0-9]{9}Z-[a-f0-9]{8}', ident), 'EXPLICIT_TRANSACTION_REQUIRED')
        self.directory = self.runtime / 'bridge-transactions' / ident
        self.record = json.loads(safe(self.directory / 'phase.json').read_bytes())
        require(self.record['id'] == ident and self.record['transitionSha256'] == sha(self.transition), 'TRANSACTION_IDENTITY_MISMATCH')
        require(self.record['phase'] in ('VERIFIED', 'QUIESCED', 'BACKUP_VERIFIED', 'TARGET_STAGED',
                'DB_TRANSITION_STARTED', 'ROLES_AND_SCHEMA_VERIFIED', 'TARGET_RUNTIME_VERIFIED',
                'COMMITTED', 'RECOVERY_REQUIRED', 'RECOVERED_TO_V100', 'FAILED_RECOVERY'), 'UNKNOWN_TRANSACTION_PHASE')
        active_path = self.runtime / 'bridge-active.env'
        if active_path.exists():
            active = metadata(active_path)
            require(active == {'FORMAT': '1', 'TRANSACTION_ID': ident, 'TRANSITION_SHA256': sha(self.transition)}, 'BRIDGE_OWNERSHIP_MISMATCH')
        else:
            require(self.args.action == 'status' and self.record['phase'] in ('COMMITTED','RECOVERED_TO_V100'), 'MISSING_ACTIVE_TRANSACTION')
        require(set(self.record['files']) == {*INSTALLED, 'package.env', 'VERSION', 'runtime.env'}, 'INCOMPLETE_RECOVERY_ASSETS')
        for name, digest in self.record['files'].items():
            require(name in (*INSTALLED, 'package.env', 'VERSION', 'runtime.env')
                    and sha(self.directory / name) == digest, 'RECOVERY_ASSET_CHANGED')
        require(self.record['files']['runtime.env'] == self.trust['installationConfigSha256'], 'RECOVERY_SOURCE_CHANGED')
        require(sha(self.directory / 'package.env') == self.trust['sourceManifestSha256'], 'RECOVERY_PACKAGE_CHANGED')
        original = metadata(self.directory / 'package.env')
        for name, key in INSTALLED.items():
            require(sha(self.directory / name) == original[key], 'RECOVERY_ORIGINAL_ASSET_CHANGED')
        if (self.runtime / 'pending-update').exists():
            require((self.runtime / 'pending-update').read_text().strip() == ident, 'PENDING_UPDATE_NOT_OWNED')
        if (self.runtime / 'pending-restore.env').exists():
            require(metadata(self.runtime / 'pending-restore.env') == {'FORMAT': '1',
                    'BACKUP_PATH': str(self.directory / 'backup'),
                    'BACKUP_MANIFEST_SHA256': self.record['backupManifestSha256']}, 'PENDING_RESTORE_NOT_OWNED')
        return self.record

    def clear_guards(self):
        for name in ('pending-update', 'pending-restore.env', 'bridge-active.env'):
            (self.runtime / name).unlink(missing_ok=True)

    def recover(self):
        return self._recover()

    @contextmanager
    def recovery_guard(self):
        try:
            yield
        except BaseException:
            # 回执/解锁同样属于恢复事务；任何失败在仍持锁时停业务并保留可重试身份。
            for services in (('frontend', 'backend', 'maintenance'), ('postgres',)):
                try:
                    self.stop_services(services)
                except Refused:
                    pass
            try:
                pending = self.runtime / 'pending-update'
                if not pending.exists():
                    atomic(pending, (self.record['id'] + '\n').encode())
                if 'backupManifestSha256' in self.record and not (self.runtime / 'pending-restore.env').exists():
                    write_env(self.runtime / 'pending-restore.env', {'FORMAT': '1',
                              'BACKUP_PATH': str(self.directory / 'backup'),
                              'BACKUP_MANIFEST_SHA256': self.record['backupManifestSha256']})
                self.record['recoveryFailed'] = True
                # 已提交目标只能补完交接，不能因恢复回执失败被改判为可回到旧库。
                self.phase('COMMITTED' if self.record['phase'] == 'COMMITTED' else 'FAILED_RECOVERY')
            except (OSError, Refused):
                pass
            raise

    def start_recovered_application(self, config=None):
        try:
            self.compose('up', '-d', '--no-build', '--pull', 'never', '--no-deps', '--force-recreate',
                         '--wait', '--wait-timeout', '300', 'backend', 'frontend')
            if config is not None:
                self.verify_runtime(config)
        except BaseException:
            self.stop()
            raise

    def _recover(self):
        require(self.args.confirm == 'RECOVER 1.0.0', 'EXPLICIT_RECOVERY_CONFIRMATION_REQUIRED')
        # 先只读验证事务，再回收属于该清单且原进程已退出的桥接锁。
        self.load_transaction()
        with installation_lock(self.root, sha(self.transition)), self.recovery_guard():
            self.load_transaction()
            if self.record['phase'] == 'COMMITTED':
                config = metadata(self.root / 'config/runtime.env')
                require(sha(self.root / 'config/runtime.env') == self.record['targetConfigSha256'], 'COMMITTED_CONFIG_CHANGED')
                target = metadata(self.runtime / 'package.env')
                self.target = target
                require(sha(self.runtime / 'package.env') == self.trust['targetManifestSha256'], 'COMMITTED_PACKAGE_CHANGED')
                for name, key in INSTALLED.items():
                    require(sha(self.runtime / name) == target[key], 'COMMITTED_ASSET_CHANGED')
                self.stop()
                self.compose('up', '-d', '--no-build', '--pull', 'never', '--wait', 'postgres')
                self.compose('--profile', 'maintenance', 'run', '--rm', '--no-deps', '--pull', 'never',
                             'maintenance', '--verify-runtime')
                self.start_recovered_application(config)
                write_json(self.directory / 'receipt.json', self.record)
                self.clear_guards()
                return {'result': 'COMMITTED', 'transaction': self.record['id']}
            self.stop()
            self.stop_services(('postgres',))
            changed = self.record.get('databaseMayHaveChanged', False)
            self.phase('RECOVERY_REQUIRED')
            if changed:
                backup = self.directory / 'backup/cluster.tar'
                require(sha(backup) == self.record['clusterSha256'], 'RECOVERY_BACKUP_CHANGED')
                config = metadata(self.directory / 'runtime.env')
                displaced = self.root / ('data/bridge-displaced-' + self.record['id'])
                if not self.record.get('restoreCopyReady'):
                    # 失败副本保留；新尝试用独立名称，禁止清理未知或未完成数据树。
                    restored = self.root / ('data/bridge-restored-' + secrets.token_hex(8))
                    restored.mkdir()
                    self.snapshot(config['POSTGRES_IMAGE'], restored, backup, True)
                    self.record['restoreCopy'] = str(restored)
                    self.record['restoreCopyReady'] = True
                    self.phase('RECOVERY_REQUIRED')
                restored = safe(self.record['restoreCopy'])
                require(restored.parent == self.root / 'data' and restored.name.startswith('bridge-restored-'), 'UNSAFE_RESTORE_COPY')
                if not displaced.exists():
                    (self.root / 'data/postgres').rename(displaced)
                if restored.exists():
                    require(not (self.root / 'data/postgres').exists(), 'RESTORE_TARGET_OCCUPIED')
                    restored.rename(self.root / 'data/postgres')
                require((self.root / 'data/postgres').is_dir(), 'RESTORED_DATA_NOT_INSTALLED')
            for name in (*INSTALLED, 'package.env', 'VERSION'):
                atomic(self.runtime / name, (self.directory / name).read_bytes())
            os.chmod(self.runtime / 'runtime.yml', 0o644)
            atomic(self.root / 'config/runtime.env', (self.directory / 'runtime.env').read_bytes())
            self.compose('up', '-d', '--no-build', '--pull', 'never', '--force-recreate', '--wait', 'postgres')
            if (self.directory / 'business-before.json').exists():
                before = json.loads((self.directory / 'business-before.json').read_bytes())
                self.facts(before, self.capture(self.pg_container(), before))
                require(self.auth_hash(self.pg_container()) == self.record['authHash'], 'RESTORED_AUTH_CHANGED')
            self.start_recovered_application()
            self.phase('RECOVERED_TO_V100')
            write_json(self.directory / 'recovery-receipt.json', self.record)
            self.clear_guards()
            return {'result': 'RECOVERED_TO_V100', 'transaction': self.record['id']}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('action', choices=('preflight', 'execute', 'status', 'recover'))
    for name in ('install-root', 'transition', 'trusted-transition-sha256', 'baseline-tool'):
        p.add_argument('--' + name, required=True)
    p.add_argument('--source-package')
    p.add_argument('--target-package')
    p.add_argument('--target-archive')
    p.add_argument('--confirm')
    p.add_argument('--transaction')
    p.add_argument('--local-candidate', action='store_true', help='仅限任务隔离验证；不授予正式来源资格')
    args = p.parse_args()
    try:
        b = Bridge(args)
        if args.action == 'status':
            record = b.load_transaction()
            result = {'transaction': record['id'], 'phase': record['phase']}
        else:
            result = getattr(b, args.action)()
        print(json.dumps(result, sort_keys=True))
        return 0
    except (Refused, OSError, ValueError, KeyError, TypeError) as failure:
        print(json.dumps({'result': 'BLOCKED', 'reason': str(failure) if isinstance(failure, Refused)
                          else 'BRIDGE_VALIDATION_OR_OPERATION_FAILED',
                          'recovery': 'Inspect persisted transaction; do not start an installer manually'}))
        return 4


if __name__ == '__main__':
    raise SystemExit(main())
