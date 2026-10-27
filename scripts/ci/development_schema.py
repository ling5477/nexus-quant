"""开发 CI 自动发现迁移并与独立重建库比较；正式发行合同保持冻结。"""
import argparse
import copy
import hashlib
import importlib.util
import json
import os
import re
import subprocess
import uuid
from pathlib import Path
from urllib.parse import urlsplit, urlunsplit

ROOT = Path(__file__).resolve().parents[2]
DIRECTORY = "backend/nq-infra/src/main/resources/db/migration"
SPEC = importlib.util.spec_from_file_location("release_schema_contract", ROOT / "scripts/ci/verify-release-schema.py")
CONTRACT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(CONTRACT)


def normalized_sha(raw):
    return hashlib.sha256(raw.replace(b"\r\n", b"\n")).hexdigest()


def git(root, *args):
    result = subprocess.run(["git", "-C", str(root), *args], capture_output=True, timeout=20)
    if result.returncode:
        raise ValueError("Migration base revision is unavailable; fetch its history first")
    return result.stdout


def inventory(root, base_ref="HEAD^1"):
    directory = root / DIRECTORY
    rows = []
    for path in sorted(directory.iterdir()):
        match = re.fullmatch(r"V([1-9][0-9]*)__([A-Za-z0-9_]+)\.sql", path.name)
        if path.is_symlink() or not path.is_file() or not match:
            raise ValueError("Unexpected migration path: " + path.name)
        sql = path.read_bytes()
        if re.search(rb"(?i)\b(?:gate(?:audit|[a-z][0-9]*|[a-z]-[a-z0-9]+)|phase[0-9]*|stage[0-9]*|attempt[0-9]*|pilot)\b", sql):
            raise ValueError("Historical stage semantics in migration: " + path.name)
        rows.append({"version": match[1], "script": path.name, "sha256": normalized_sha(sql)})
    rows.sort(key=lambda row: int(row["version"]))
    if not rows or len(rows) > 1000 or [int(row["version"]) for row in rows] != list(range(1, len(rows) + 1)):
        raise ValueError("Migration versions must be unique and contiguous from V1")
    actual = {row["script"]: row["sha256"] for row in rows}
    # V44 快照只作为历史不可变锚点；后续版本从源码发现，不再手工更新版本、数量或 fingerprint。
    baseline = json.loads((root / "scripts/ci/development-schema-manifest.json").read_bytes())
    for row in baseline["migrations"]:
        if actual.get(row["script"]) != row["sha256"]:
            raise ValueError("Frozen migration was changed or removed: " + row["script"])
    # Git 父版本保护后续已合入迁移；不能只冻结 V44 以前而允许每次重写较新历史。
    paths = git(root, "ls-tree", "-r", "--name-only", base_ref, "--", DIRECTORY).decode().splitlines()
    if not paths:
        raise ValueError("Migration base inventory is empty")
    for name in paths:
        if actual.get(Path(name).name) != normalized_sha(git(root, "show", base_ref + ":" + name)):
            raise ValueError("Existing migration was changed or removed: " + name)
    return rows


def write_source(root, output, base_ref):
    rows = inventory(root, base_ref)
    source = {"contractKind": "DEVELOPMENT_ONLY", "migrationBase": base_ref, "migrations": rows,
              "groups": [{"category": "INCLUDE_RUNTIME", "paths": [DIRECTORY + "/" + row["script"] for row in rows]}]}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_bytes((json.dumps(source, indent=2) + "\n").encode())
    return rows


def psql(connection, query, read_only=True):
    statement = query
    if read_only:
        statement = ("BEGIN READ ONLY; SET LOCAL statement_timeout='10s'; SELECT coalesce(json_agg(x),'[]'::json) FROM ("
                     + query + ") x; COMMIT;")
    result = subprocess.run(["psql", connection, "-X", "-qAt", "-v", "ON_ERROR_STOP=1"],
                            input=statement, capture_output=True, text=True, encoding="utf8", timeout=30)
    if result.returncode:
        # 连接参数和错误详情可能包含秘密；不把它们写入 CI 诊断。
        raise ValueError("Development schema PostgreSQL operation failed")
    return json.loads(result.stdout) if read_only else None


def database_connection(connection, name):
    if connection.startswith(("postgresql://", "postgres://")):
        parts = urlsplit(connection)
        if parts.query:
            raise ValueError("Use a PostgreSQL URI without query parameters for the reference database")
        return urlunsplit(parts._replace(path="/" + name))
    return connection + " dbname=" + name


def assert_fresh_facts(facts, baseline, migrations=()):
    expected = copy.deepcopy(baseline["freshInstallFacts"])
    # V44 快照保持原样；V48 只引入一个空实验游标，不能自动吸收其他静态事实变化。
    if any(row["version"] == "48" and row["script"] == "V48__strategy_experiments.sql"
           for row in migrations):
        cursors = expected["facts"]["tenant_work_scan_cursors"]
        cursors.append({"work_kind": "EXPERIMENT", "last_work_id": ""})
        cursors.sort(key=lambda row: json.dumps(row, sort_keys=True))
        expected["rowCounts"]["tenant_work_scan_cursors"] += 1
    if facts["facts"] != expected["facts"] or facts["roleSequence"] != expected["roleSequence"]:
        raise ValueError("Fresh-install seed/control facts changed")
    for name, count in facts["rowCounts"].items():
        if count != expected["rowCounts"].get(name, 0):
            raise ValueError("Unexpected fresh-install business rows: " + name)
    if set(expected["rowCounts"]) - set(facts["rowCounts"]):
        raise ValueError("Existing fresh-install relation is missing")


def verify_database(root, connection, classpath, source, rows, base_ref):
    baseline = json.loads((root / "scripts/ci/development-schema-manifest.json").read_bytes())
    name = "nq_ci_reference_" + uuid.uuid4().hex
    reference = database_connection(connection, name)
    jdbc = os.environ.get("NQ_FLYWAY_DB_URL", "")
    if not jdbc.startswith("jdbc:postgresql://"):
        raise ValueError("Reference migration requires NQ_FLYWAY_DB_URL")
    parts = urlsplit(jdbc[5:])
    if parts.query:
        raise ValueError("Use a JDBC URL without query parameters for the reference database")
    environment = os.environ.copy()
    environment["NQ_FLYWAY_DB_URL"] = "jdbc:" + urlunsplit(parts._replace(path="/" + name))
    psql(connection, "CREATE DATABASE " + name, read_only=False)
    try:
        migrated = subprocess.run(["java", "-cp", classpath, "ReleaseMigrationSmoke", str(source.resolve())],
                                  cwd=root, env=environment, capture_output=True, text=True, encoding="utf8", timeout=180)
        if migrated.returncode:
            raise ValueError("Independent reference migration/validate/replay failed")
        if inventory(root, base_ref) != rows:
            raise ValueError("Migration sources changed during reference construction")
        execute = lambda query: psql(reference, query)
        history = execute("SELECT version,description,type,script,checksum,success FROM flyway_schema_history ORDER BY installed_rank")
        if history[:len(baseline["flywayHistory"])] != baseline["flywayHistory"]:
            raise ValueError("Frozen Flyway history changed")
        model = CONTRACT.catalog(execute)
        facts = CONTRACT.seeds(execute, model, baseline["seedTables"])
        assert_fresh_facts(facts, baseline, rows)
        manifest = copy.deepcopy(baseline)
        manifest.update(contractKind="DEVELOPMENT_REFERENCE", sourceManifest=str(source.resolve()),
                        migrations=rows, flywayVersion=rows[-1]["version"], flywayHistory=history,
                        normalizedSchemaSha256=CONTRACT.sha(CONTRACT.normalize(model)),
                        schemaCounts=model["counts"], freshInstallFacts=facts)
        CONTRACT.verify_source(root, manifest)
        actual = lambda query: psql(connection, query)
        result = CONTRACT.verify_database(actual, manifest)
        # 最终验证也绑定相同源码身份，避免生成清单后再消费已变化的目录。
        if inventory(root, base_ref) != rows:
            raise ValueError("Migration sources changed during database verification")
        result.update(source="PASS", contractKind="DEVELOPMENT_REFERENCE", referenceDatabase=name,
                      migrationSourceSha256=CONTRACT.sha(rows))
        return result
    finally:
        # 仅清理本次随机创建的参考库；被测数据库只读，不调用 clean 或 repair。
        psql(connection, "DROP DATABASE " + name + " WITH (FORCE)", read_only=False)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--repository-root", type=Path, default=ROOT)
    parser.add_argument("--base-ref", default=os.environ.get("NQ_MIGRATION_BASE_REF", "HEAD^1"))
    parser.add_argument("--source-output", type=Path, required=True)
    parser.add_argument("--psql-url")
    parser.add_argument("--reference-classpath")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    rows = write_source(args.repository_root, args.source_output, args.base_ref)
    result = {"source": "PASS", "migrationCount": len(rows), "flywayVersion": rows[-1]["version"]}
    if bool(args.psql_url) != bool(args.reference_classpath):
        parser.error("--psql-url and --reference-classpath must be supplied together")
    if args.psql_url:
        result.update(verify_database(args.repository_root, args.psql_url, args.reference_classpath,
                                      args.source_output, rows, args.base_ref))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_bytes((json.dumps(result, indent=2) + "\n").encode())
    print("DEVELOPMENT_SCHEMA_CONTRACT_PASS " + json.dumps(result, sort_keys=True))


if __name__ == "__main__":
    main()
