"""对隔离产品库生成只读业务指纹，供备份恢复及后续升级比较使用。"""
import argparse
import hashlib
import json
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path


# 只读取业务事实；认证、凭据、令牌及后台心跳不属于可分享的升级基线。
TABLES = (
    'accounts', 'exchange_accounts', 'strategy_definitions', 'strategy_versions',
    'research_configs', 'marketdata_datasets', 'marketdata_dataset_coverage',
    'public_market_captures', 'backtest_configs', 'backtest_runs', 'backtest_eval_reports',
    'backtest_publish_records', 'sim_orders', 'sim_trades', 'sim_positions', 'sim_pnl_snapshots',
    'paper_trading_runs', 'continuous_sim_runs', 'continuous_sim_bars',
    'continuous_sim_operations', 'strategy_sim_decisions', 'strategy_runs',
    'orders', 'trades', 'ledger_events', 'ledger_entries', 'positions',
    'sim_asset_funding_receipts', 'risk_events', 'paper_run_daily_reports',
    'account_snapshots', 'account_equity_measurements',
)
FORMAT = 'NQ_BUSINESS_BASELINE_V1'
LIMIT = 10000


def identifier(value):
    if not re.fullmatch(r'[a-z_][a-z0-9_]{0,62}', value):
        raise ValueError('Unsupported SQL identifier')
    return '"' + value + '"'


def digest(value):
    return hashlib.sha256(value.encode('utf-8')).hexdigest()


def fingerprint(raw, reference=None):
    tables = {}
    for name in TABLES:
        table = raw[name]
        columns = sorted(table['columns'])
        keys = table['primaryKey']
        if not keys or not set(keys).issubset(columns):
            raise ValueError(f'{name}: primary key missing')
        if len(table['rows']) > LIMIT:
            raise ValueError(f'{name}: fixture exceeds {LIMIT} rows')
        if reference:
            expected = reference['tables'][name]
            if columns != expected['columns'] or keys != expected['primaryKey']:
                raise ValueError(f'{name}: baseline column/key contract changed')
        rows = {}
        for row_text in table['rows']:
            # PostgreSQL 输出的 JSON 数值保持原文本，不经过 Python 浮点转换。
            row = json.loads(row_text, parse_float=str, parse_int=str)
            key = json.dumps([row[k] for k in keys], ensure_ascii=False, separators=(',', ':'))
            if key in rows:
                raise ValueError(f'{name}: duplicate identity {key}')
            rows[key] = digest(row_text)
        tables[name] = {'columns': columns, 'primaryKey': keys, 'count': len(rows), 'rows': rows}
    return tables


def capture(container, database, user, version, commit, reference=None):
    # 标识符来自固定清单或已验证的基线列；SQL 无任何写入语句。
    identifier(database)
    if reference:
        validate(reference)
    parts = []
    for name in TABLES:
        table = identifier(name)
        projection = '*'
        if reference:
            projection = ','.join(identifier(c) for c in reference['tables'][name]['columns'])
        parts.append(f"""'{name}', json_build_object(
            'columns', (SELECT json_agg(column_name ORDER BY column_name)
                FROM information_schema.columns WHERE table_schema='public' AND table_name='{name}'
                {"AND column_name IN (" + ','.join("'"+c+"'" for c in reference['tables'][name]['columns']) + ')' if reference else ''}),
            'primaryKey', (SELECT json_agg(a.attname ORDER BY k.ord)
                FROM pg_index i CROSS JOIN LATERAL unnest(i.indkey) WITH ORDINALITY k(attnum,ord)
                JOIN pg_attribute a ON a.attrelid=i.indrelid AND a.attnum=k.attnum
                WHERE i.indrelid='public.{name}'::regclass AND i.indisprimary),
            'rows', COALESCE((SELECT json_agg(to_jsonb(t)::text)
                FROM (SELECT {projection} FROM public.{table} LIMIT {LIMIT + 1}) t), '[]'::json))""")
    # 单事务、单快照读取所有业务表；超时与容量上限防止误接大库后无界扫描。
    query = ("BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY; "
             "SET LOCAL statement_timeout='15s'; SET LOCAL lock_timeout='3s'; SET LOCAL timezone='UTC'; "
             "SELECT json_build_object('tables',json_build_object(" + ','.join(parts) + "),"
             "'schema', (SELECT json_agg(json_build_object('version',version,'checksum',checksum,'success',success) "
             "ORDER BY installed_rank) FROM public.flyway_schema_history)); COMMIT;")
    process = subprocess.run(['docker', 'exec', '-i', container, 'psql', '-X', '-qAt',
        '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', database], input=query,
        capture_output=True, text=True, encoding='utf-8', timeout=30)
    if process.returncode:
        # 不转发数据库 stderr，避免错误语境中出现业务内容或连接信息。
        raise RuntimeError(f'Read-only PostgreSQL capture failed: exit {process.returncode}')
    if len(process.stdout) > 32 * 1024 * 1024:
        raise ValueError('Fixture exceeds 32 MiB capture limit')
    raw = json.loads(process.stdout)
    return {'format': FORMAT, 'capturedAt': datetime.now(timezone.utc).isoformat(),
        'sourceVersion': version, 'sourceCommit': commit, 'container': container,
        'schema': raw['schema'], 'tables': fingerprint(raw['tables'], reference)}


def validate(snapshot):
    if snapshot.get('format') != FORMAT or set(snapshot.get('tables', {})) != set(TABLES):
        raise ValueError('Incomplete business baseline')
    for name, table in snapshot['tables'].items():
        if not table.get('columns') or not table.get('primaryKey'):
            raise ValueError(f'{name}: missing column/key contract')
        for column in table['columns'] + table['primaryKey']:
            identifier(column)
        if table['count'] != len(table['rows']) or table['count'] > LIMIT:
            raise ValueError(f'{name}: inconsistent row count')
        if not all(re.fullmatch(r'[0-9a-f]{64}', h) for h in table['rows'].values()):
            raise ValueError(f'{name}: invalid row fingerprint')


def compare(before, after):
    validate(before)
    validate(after)
    changes = []
    for name in TABLES:
        old, new = before['tables'][name], after['tables'][name]
        if old['columns'] != new['columns'] or old['primaryKey'] != new['primaryKey']:
            changes.append({'table': name, 'kind': 'COLUMN_OR_KEY_DRIFT'})
        missing = sorted(old['rows'].keys() - new['rows'].keys())
        added = sorted(new['rows'].keys() - old['rows'].keys())
        changed = sorted(k for k in old['rows'].keys() & new['rows'].keys() if old['rows'][k] != new['rows'][k])
        if missing or added or changed:
            changes.append({'table': name, 'missing': missing, 'added': added, 'changed': changed})
    # 升级允许追加 migration，既有 migration 的身份和 checksum 不得改变。
    old_schema, new_schema = before['schema'], after['schema']
    if not old_schema or new_schema[:len(old_schema)] != old_schema or not all(r['success'] for r in new_schema):
        changes.append({'kind': 'MIGRATION_HISTORY_DRIFT'})
    return {'result': 'MATCH' if not changes else 'DIFFERENT', 'changes': changes,
        'tablesCompared': len(TABLES), 'beforeVersion': before['sourceVersion'], 'afterVersion': after['sourceVersion']}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    cap = sub.add_parser('capture')
    cap.add_argument('--container', required=True)
    cap.add_argument('--database', default='nexus_quant')
    cap.add_argument('--user', default='nexusquant')
    cap.add_argument('--source-version', required=True)
    cap.add_argument('--source-commit', required=True)
    cap.add_argument('--reference', type=Path)
    cap.add_argument('--output', type=Path, required=True)
    diff = sub.add_parser('compare')
    diff.add_argument('--before', type=Path, required=True)
    diff.add_argument('--after', type=Path, required=True)
    args = parser.parse_args()
    read = lambda p: json.loads(p.read_text(encoding='utf-8'))
    if args.command == 'capture':
        if args.output.exists():
            raise ValueError('Refusing to overwrite existing baseline')
        value = capture(args.container, args.database, args.user, args.source_version, args.source_commit,
            read(args.reference) if args.reference else None)
        args.output.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        print(json.dumps({'result': 'CAPTURED', 'counts': {t: v['count'] for t, v in value['tables'].items()}}))
        return 0
    value = compare(read(args.before), read(args.after))
    print(json.dumps(value, ensure_ascii=False, indent=2))
    return 0 if value['result'] == 'MATCH' else 1


if __name__ == '__main__':
    raise SystemExit(main())
