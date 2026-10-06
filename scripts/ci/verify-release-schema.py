"""只读验证正式 V1 的源身份、完整 schema 和全新安装静态事实。"""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

queries = {'relations': 'SELECT '
              'c.relname,c.relkind,c.relpersistence,c.relrowsecurity,c.relforcerowsecurity,c.relreplident,c.reloptions '
              "FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND "
              "c.relname NOT LIKE 'flyway_schema_history%' AND c.relkind IN ('r','p','v','m','S')",
 'attributes': 'SELECT c.relname,a.attnum,a.attname,format_type(a.atttypid,a.atttypmod) '
               'type,a.attnotnull,a.attidentity,a.attgenerated,a.attstorage,a.attcompression,pg_get_expr(d.adbin,d.adrelid) '
               'default_expr FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON '
               'n.oid=c.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum WHERE '
               "n.nspname='public' AND c.relkind IN ('r','p') AND a.attnum>0 AND NOT a.attisdropped AND "
               "c.relname!='flyway_schema_history'",
 'constraints': 'SELECT '
                'c.relname,k.conname,k.contype,k.condeferrable,k.condeferred,k.convalidated,k.conislocal,k.coninhcount,k.confupdtype,k.confdeltype,k.confmatchtype,pg_get_constraintdef(k.oid) '
                'definition FROM pg_constraint k JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON '
                "n.oid=c.relnamespace WHERE n.nspname='public' AND c.relname!='flyway_schema_history'",
 'indexes': 'SELECT t.relname table_name,c.relname '
            'name,i.indisunique,i.indisprimary,i.indisexclusion,i.indimmediate,i.indisclustered,i.indisvalid,i.indcheckxmin,i.indisready,i.indislive,i.indisreplident,pg_get_indexdef(i.indexrelid) '
            'definition FROM pg_index i JOIN pg_class c ON c.oid=i.indexrelid JOIN pg_class t ON '
            "t.oid=i.indrelid JOIN pg_namespace n ON n.oid=t.relnamespace WHERE n.nspname='public' AND "
            "t.relname!='flyway_schema_history'",
 'triggers': 'SELECT '
             'c.relname,t.tgname,t.tgenabled,t.tgdeferrable,t.tginitdeferred,p.proname,pg_get_triggerdef(t.oid) '
             'definition FROM pg_trigger t JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON '
             "n.oid=c.relnamespace JOIN pg_proc p ON p.oid=t.tgfoid WHERE n.nspname='public' AND NOT "
             "t.tgisinternal AND c.relname!='flyway_schema_history'",
 'functions': 'SELECT p.proname,pg_get_function_identity_arguments(p.oid) args,pg_get_function_result(p.oid) '
              'result,l.lanname,p.prokind,p.prosecdef,p.proleakproof,p.proisstrict,p.proretset,p.provolatile,p.proparallel,p.procost,p.prorows,p.proconfig,pg_get_functiondef(p.oid) '
              'definition FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace JOIN pg_language l ON '
              "l.oid=p.prolang WHERE n.nspname='public'",
 'sequences': 'SELECT sequencename,data_type,start_value,min_value,max_value,increment_by,cycle,cache_size '
              "FROM pg_sequences WHERE schemaname='public'"}
queries['comments']="""SELECT 'relation' kind,c.relname identity,a.attname column_name,d.description FROM pg_description d JOIN pg_class c ON d.classoid='pg_class'::regclass AND c.oid=d.objoid JOIN pg_namespace n ON n.oid=c.relnamespace LEFT JOIN pg_attribute a ON a.attrelid=c.oid AND a.attnum=d.objsubid WHERE n.nspname='public' AND c.relname NOT LIKE 'flyway_schema_history%'
UNION ALL SELECT 'function',p.proname||'('||pg_get_function_identity_arguments(p.oid)||')',NULL,d.description FROM pg_description d JOIN pg_proc p ON d.classoid='pg_proc'::regclass AND p.oid=d.objoid JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
UNION ALL SELECT 'constraint',c.relname||'.'||k.conname,NULL,d.description FROM pg_description d JOIN pg_constraint k ON d.classoid='pg_constraint'::regclass AND k.oid=d.objoid JOIN pg_class c ON c.oid=k.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public'
UNION ALL SELECT 'trigger',c.relname||'.'||t.tgname,NULL,d.description FROM pg_description d JOIN pg_trigger t ON d.classoid='pg_trigger'::regclass AND t.oid=d.objoid JOIN pg_class c ON c.oid=t.tgrelid JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public'
UNION ALL SELECT 'extension',e.extname,NULL,d.description FROM pg_description d JOIN pg_extension e ON d.classoid='pg_extension'::regclass AND e.oid=d.objoid WHERE e.extname='pgcrypto'"""
queries['extensions']='SELECT extname,extversion FROM pg_extension ORDER BY extname'
queries['enums']="SELECT t.typname,e.enumlabel,e.enumsortorder FROM pg_type t JOIN pg_enum e ON e.enumtypid=t.oid JOIN pg_namespace n ON n.oid=t.typnamespace WHERE n.nspname='public'"
queries['views']="SELECT c.relname,pg_get_viewdef(c.oid) definition FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('v','m')"

# NULL ACL 表示 PostgreSQL 默认权限，不能当作无授权；比较实际生效的 owner/PUBLIC/命名角色拓扑。
queries['effective_acl'] = """WITH objects AS (
    SELECT 'schema' AS kind, n.nspname AS identity, n.nspowner AS owner, n.nspacl AS acl, 'n'::"char" AS acl_kind
    FROM pg_namespace n WHERE n.nspname='public'
    UNION ALL
    SELECT CASE WHEN c.relkind='S' THEN 'sequence' ELSE 'relation' END, c.relname, c.relowner, c.relacl,
           CASE WHEN c.relkind='S' THEN 's' ELSE 'r' END::"char"
    FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m','S') AND c.relname NOT LIKE 'flyway_schema_history%'
    UNION ALL
    SELECT 'column', c.relname||'.'||a.attname, c.relowner, a.attacl, 'c'::"char"
    FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace
    WHERE n.nspname='public' AND c.relkind IN ('r','p','v','m') AND a.attnum>0 AND NOT a.attisdropped
      AND a.attacl IS NOT NULL AND c.relname!='flyway_schema_history'
    UNION ALL
    SELECT 'function', p.proname||'('||pg_get_function_identity_arguments(p.oid)||')', p.proowner, p.proacl, 'f'::"char"
    FROM pg_proc p JOIN pg_namespace n ON n.oid=p.pronamespace WHERE n.nspname='public'
) SELECT o.kind,o.identity,
    CASE WHEN a.grantee=0 THEN 'PUBLIC' WHEN a.grantee=o.owner THEN 'OWNER' ELSE pg_get_userbyid(a.grantee)::text END AS grantee,
    CASE WHEN a.grantor=o.owner THEN 'OWNER' ELSE pg_get_userbyid(a.grantor)::text END AS grantor,
    a.privilege_type,a.is_grantable
FROM objects o CROSS JOIN LATERAL aclexplode(coalesce(o.acl,acldefault(o.acl_kind,o.owner))) a"""
queries['default_acl'] = """SELECT CASE WHEN d.defaclrole=(SELECT oid FROM pg_roles WHERE rolname=current_user)
        THEN 'OWNER' ELSE pg_get_userbyid(d.defaclrole)::text END AS owner,
    coalesce(n.nspname,'GLOBAL') AS namespace,d.defaclobjtype,
    CASE WHEN a.grantee=0 THEN 'PUBLIC' WHEN a.grantee=d.defaclrole THEN 'OWNER' ELSE pg_get_userbyid(a.grantee)::text END AS grantee,
    CASE WHEN a.grantor=d.defaclrole THEN 'OWNER' ELSE pg_get_userbyid(a.grantor)::text END AS grantor,
    a.privilege_type,a.is_grantable
FROM pg_default_acl d LEFT JOIN pg_namespace n ON n.oid=d.defaclnamespace
CROSS JOIN LATERAL aclexplode(d.defaclacl) a WHERE d.defaclnamespace=0 OR n.nspname='public'"""
def catalog(execute):
    model={kind:execute(q) for kind,q in queries.items()}
    for table in sorted({r['relname'] for r in model['attributes']}):
        rows=sorted((r for r in model['attributes'] if r['relname']==table),key=lambda r:r['attnum'])
        for ordinal,row in enumerate(rows,1):row.pop('attnum');row['ordinal']=ordinal
    for kind,rows in model.items():model[kind]=sorted(rows,key=lambda r:json.dumps(r,sort_keys=True,ensure_ascii=False))
    model['counts']={'tables':sum(r['relkind'] in ('r','p') for r in model['relations']),'columns':len(model['attributes']),'constraints':len(model['constraints']),'indexes':len(model['indexes']),'functions':len(model['functions']),'triggers':len(model['triggers']),'sequences':len(model['sequences']),'comments':len(model['comments']),'views':len(model['views']),'enums':len(model['enums'])}
    return model
def canonical_definition(text):
    import re
    text=text.replace('\r\n','\n')
    text=re.sub(r"\(('(?:[^']|'')*'::character varying)\)::text",lambda m:m[1].replace('::character varying','::text'),text)
    text=re.sub(r'\(\(ARRAY\[([^\]]+)\]\)::text\[\]\)',lambda m:'(ARRAY['+m[1].replace('::character varying','::text')+'])',text)
    return text
def sha(model):return hashlib.sha256(json.dumps(model,sort_keys=True,separators=(',',':'),ensure_ascii=False).encode('utf8')).hexdigest()

def normalize(model):
    model = json.loads(json.dumps(model))
    for kind in ('functions', 'constraints', 'indexes'):
        for row in model[kind]:
            row['definition'] = canonical_definition(row['definition'])
            if kind == 'constraints' and row['conname'] in {
                'chk_execution_scope_bindings_age_skew',
                'chk_risk_limit_sets_counts', 'chk_risk_limit_sets_market_data'
            }:
                if ' OR ' in row['definition']:
                    raise ValueError('Unexpected disjunction in pure AND constraint')
                row['definition'] = re.sub(r'[()\s]', '', row['definition'])
    for kind in queries:
        model[kind] = sorted(model[kind], key=lambda r: json.dumps(r, sort_keys=True, ensure_ascii=False))
    return model

# 仅忽略安装时间；业务状态、版本、身份与所有空值均参与 seed 合同。
SEED_TABLES = ('roles', 'kill_switch_states', 'kill_switch_events',
               'scheduled_job_controls', 'strategy_run_recovery_scan_cursor')
INSTALL_TIMESTAMPS = ('created_at', 'updated_at', 'occurred_at')

def seeds(execute, model):
    rows = {}
    tables = sorted(r['relname'] for r in model['relations'] if r['relkind'] in ('r', 'p'))
    counts = execute(' UNION ALL '.join(
        'SELECT ' + "'" + table + "'" + ' AS name, count(*) AS count FROM "' + table + '"'
        for table in tables))
    excluded = "ARRAY['created_at','updated_at','occurred_at']"
    for table in SEED_TABLES:
        data = execute('SELECT to_jsonb(t) - ' + excluded + ' AS fact FROM "' + table + '" t')
        rows[table] = sorted((r['fact'] for r in data), key=lambda r: json.dumps(r, sort_keys=True))
    return {'rowCounts': {r['name']: r['count'] for r in counts}, 'facts': rows,
            'roleSequence': execute('SELECT last_value, is_called FROM roles_id_seq')}

def verify_source(root, manifest):
    directory = root / 'backend/nq-infra/src/main/resources/db/migration'
    paths = sorted(p.name for p in directory.iterdir() if p.is_file())
    if paths != [manifest['baselineFile']]:
        raise ValueError('Active migration directory must contain exactly the release V1 SQL')
    sql = (directory / manifest['baselineFile']).read_bytes().replace(b'\r\n', b'\n')
    if hashlib.sha256(sql).hexdigest() != manifest['baselineSqlSha256']:
        raise ValueError('Release baseline source checksum mismatch')
    if re.search(rb'(?i)\b(?:gate(?:audit|[a-z][0-9]*|[a-z]-[a-z0-9]+)|phase[0-9]*|stage[0-9]*|attempt[0-9]*|pilot)\b', sql):
        raise ValueError('Historical stage semantics in release baseline')

def verify_database(execute, manifest):
    version = execute("SELECT current_setting('server_version_num')::integer AS version")[0]['version']
    if version // 10000 != manifest['postgresqlMajor']:
        raise ValueError('Release baseline requires PostgreSQL 16')
    history = execute('SELECT version,description,type,script,success FROM flyway_schema_history ORDER BY installed_rank')
    if history != manifest['flywayHistory']:
        raise ValueError('Database is not a single successful release V1 installation')
    model = catalog(execute)
    normalized = normalize(model)
    if model['counts'] != manifest['schemaCounts'] or sha(normalized) != manifest['normalizedSchemaSha256']:
        raise ValueError('Release schema fingerprint mismatch')
    seed = seeds(execute, model)
    if seed != manifest['freshInstallFacts']:
        raise ValueError('Release fresh-install seed/control contract mismatch')
    return {'schemaSha256': sha(normalized), 'schemaCounts': model['counts'],
            'seedSha256': sha(seed), 'history': history}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repository-root', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--manifest', type=Path)
    parser.add_argument('--psql-url', help='PostgreSQL connection URI; password comes from PGPASSWORD')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    manifest_path = args.manifest or args.repository_root / 'scripts/ci/release-schema-manifest.json'
    manifest = json.loads(manifest_path.read_text(encoding='utf8'))
    verify_source(args.repository_root, manifest)
    result = {'source': 'PASS'}
    if args.psql_url:
        def execute(query):
            # 每次查询只读且有服务器和进程超时；诊断不打印连接参数或查询返回的数据。
            command = ['psql', args.psql_url, '-X', '-qAt', '-v', 'ON_ERROR_STOP=1']
            statement = "BEGIN READ ONLY; SET LOCAL statement_timeout='10s'; SELECT coalesce(json_agg(x),'[]'::json) FROM (" + query + ') x; COMMIT;'
            completed = subprocess.run(command, input=statement, capture_output=True,
                                       text=True, encoding='utf8', timeout=30)
            if completed.returncode:
                raise ValueError('Read-only release schema query failed')
            return json.loads(completed.stdout)
        result.update(verify_database(execute, manifest))
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf8')
    print('RELEASE_SCHEMA_CONTRACT_PASS ' + json.dumps(result, sort_keys=True))

if __name__ == '__main__':
    main()
