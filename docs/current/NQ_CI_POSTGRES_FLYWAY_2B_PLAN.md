# NQ CI PostgreSQL / Flyway 2B Plan

任务：NQ-CI-POSTGRES-FLYWAY-2B-PLAN / NQ-CI-POSTGRES-FLYWAY-2B-PLAN-REVIEW / NQ-CI-POSTGRES-FLYWAY-2B-IMPL / NQ-CI-POSTGRES-FLYWAY-2B-FIRST-RUN-REVIEW
日期：2026-06-14
状态：PLAN ACCEPTED；IMPLEMENTED / FIRST GREEN RUN CONFIRMED

## Current state

- Project: NexusQuant / NQ。
- Current branch: `dev`。
- Current stage: GateJ completed；Next: GateK-PLAN。
- `GATEK_PLAN.md` 已 freeze / accepted。
- NQ CI Batch 1 已冻结为当前 `dev` 最小 CI baseline。
- NQ CI Batch 2A PostgreSQL / Flyway empty DB migration smoke 已 FROZEN / ACCEPTED。
- GitHub Actions run `27501253175` completed / success；`postgres-flyway` job completed / success。
- Flyway empty DB migration smoke 已从 V1 跑到 V31，并 validate 31 migrations。
- Batch 2B schema artifact / docs review：IMPLEMENTED / FIRST GREEN RUN CONFIRMED。
- Batch 2C repository real PostgreSQL smoke：NOT STARTED。
- Batch 2D `nq-app` context smoke：NOT STARTED。
- Batch 2E CI-only seed watcher cleanup：NOT STARTED。
- Batch 3 no-outbound guard：PENDING。
- Batch 4 security guard / secret scan：PENDING。
- Batch 5 frontend E2E hardening：PENDING。
- AI：NOT STARTED。
- DH runtime：NOT INTEGRATED / not connected to NQ。
- LIVE：DISABLED。
- Real exchange adapter / real permission probe adapter：NOT IMPLEMENTED。

## Batch 2A frozen baseline

Batch 2A 当前冻结基线如下：

- 使用 GitHub Actions PostgreSQL service container `postgres:16`。
- 使用 CI-only database / user / password：`nq_ci` / `nq_ci_user` / `nq_ci_password`。
- 使用 direct Flyway API 执行 `migrate` + `validate`。
- 固定 `locations("classpath:db/migration")`、`baselineOnMigrate(false)`、`cleanDisabled(true)`、`outOfOrder(false)`。
- 校验 current version 为 `31`。
- 将 `flyway_schema_history` 打印到 job logs。
- 不启动 `nq-app` full context。
- 不触发 `AuthSeedConfiguration`。
- 不插入 legacy account seed、test fixture seed、real account seed 或 real exchange seed。
- 不运行 repository real DB smoke。
- 不运行 frontend E2E。
- 不启用 Testcontainers。
- 不注入真实 exchange credential，不开启 LIVE，不访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。

## Batch 2B goals

Batch 2B 的目标是规划并后续实现 schema artifact / docs review 能力，使 `postgres-flyway` 不只证明 empty DB 可迁移到 V31，还能留下可审查的 schema metadata artifact。

目标：

1. 生成 Flyway migration history artifact，记录 version、description、type、script、checksum、installed_on、success 等字段。
2. 生成 table / column artifact，覆盖 schema、table、column、data_type、nullable、default。
3. 生成 constraint / index artifact，覆盖 primary key、unique、foreign key、CHECK constraint 和 index。
4. 生成 table / column comments artifact，用于人工发现 `DB_SCHEMA.md` 与 migration output 的 drift。
5. 约束 artifact retention 与 redaction，避免保存 credential material、env secret、API key、token、cookie、private key 等敏感材料。
6. 建立 `DB_SCHEMA.md` drift review checklist；第一阶段人工 review，不直接脚本化 blocking。
7. 保持 Batch 2B 与 2C/2D/2E、Batch 3-5 的边界清晰。

## Non-goals

- 不新增 workflow。
- 不修改 Java / TypeScript / Python 代码。
- 不修改测试代码。
- 不新增 API。
- 不新增 migration。
- 不修改历史 migration。
- 不修改 backend 生产逻辑。
- 不修改 frontend。
- 不修改 research。
- 不修改 scripts / deploy。
- 不启动 `nq-app` full context。
- 不运行 repository real DB smoke。
- 不修改或移除 CI-only seed watcher。
- 不启用 Testcontainers。
- 不实现 no-outbound guard。
- 不实现 gitleaks / secret scan。
- 不加入 frontend E2E hardening。
- 不开启 LIVE，不接 AI，不接 DH runtime，不实现 NQ RealClient 或真实 Provider。
- 不调用真实交易所，不实现真实 OKX / Binance permission probe adapter。
- 不读取、打印、复制或输出真实 credential material。
- 不把 Batch 2B 写成 frozen / accepted，直到 Batch 2B freeze review 完成。

## Proposed artifacts

Batch 2B implementation 生成以下 artifact。所有 artifact 必须来自 disposable CI PostgreSQL database，不得包含业务数据或 seed data。

| Artifact | Purpose | Minimum fields | Sensitive data policy |
| --- | --- | --- | --- |
| `flyway-info.txt` | 审查 migration history、顺序、checksum、success 状态。 | `installed_rank`、`version`、`description`、`type`、`script`、`checksum`、`installed_on`、`execution_time`、`success` | 只来自 `flyway_schema_history`，不含 credential material。 |
| `schema-tables.txt` | 审查 current schema 中的 table / view 基线。 | `table_schema`、`table_name`、`table_type` | 不导出 data rows。 |
| `schema-columns.txt` | 审查 columns、类型、nullable、default。 | `table_schema`、`table_name`、`column_name`、`data_type`、`is_nullable`、`column_default` | 仅 metadata，不含 row values。 |
| `schema-constraints.txt` | 审查 PK / UNIQUE / FK / CHECK。 | `constraint_name`、`constraint_type`、`table_name`、`definition` | 仅 DDL metadata。 |
| `schema-indexes.txt` | 审查 index 名称、table、definition。 | `schemaname`、`tablename`、`indexname`、`indexdef` | 仅 DDL metadata。 |
| `schema-comments.txt` | 审查 table / column comments，辅助 `DB_SCHEMA.md` drift review。 | object type、schema、table、column、comment | 注释中不得出现 secret；如命中敏感词必须 fail 或进入 security review。 |
| optional `schema-dump.sql` | 人工排障用 schema-only dump。 | `pg_dump --schema-only --no-owner --no-privileges` output | 不含 data；不上传 data dump。 |

## Artifact generation commands

以下命令族已进入 Batch 2B implementation。Workflow 从 CI-only PostgreSQL service env 派生 libpq connection string；不要把 JDBC URL 直接传给 `psql`。

```bash
export PGPASSWORD="${NQ_FLYWAY_DB_PASSWORD}"
psql "host=localhost port=5432 dbname=nq_ci user=nq_ci_user sslmode=disable" \
  -v ON_ERROR_STOP=1 \
  -Atc "SELECT installed_rank, version, description, type, script, checksum, installed_on, execution_time, success FROM flyway_schema_history ORDER BY installed_rank;" \
  > flyway-info.txt
```

Tables：

```bash
psql "$NQ_CI_PSQL_URL" -v ON_ERROR_STOP=1 -F $'\t' -P footer=off -c "
SELECT table_schema, table_name, table_type
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_schema, table_name;
" > schema-tables.txt
```

Columns：

```bash
psql "$NQ_CI_PSQL_URL" -v ON_ERROR_STOP=1 -F $'\t' -P footer=off -c "
SELECT table_schema, table_name, ordinal_position, column_name, data_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = 'public'
ORDER BY table_schema, table_name, ordinal_position;
" > schema-columns.txt
```

Constraints：

```bash
psql "$NQ_CI_PSQL_URL" -v ON_ERROR_STOP=1 -F $'\t' -P footer=off -c "
SELECT con.conname AS constraint_name,
       CASE con.contype
         WHEN 'p' THEN 'PRIMARY KEY'
         WHEN 'u' THEN 'UNIQUE'
         WHEN 'f' THEN 'FOREIGN KEY'
         WHEN 'c' THEN 'CHECK'
         ELSE con.contype::text
       END AS constraint_type,
       rel.relname AS table_name,
       pg_get_constraintdef(con.oid) AS definition
FROM pg_constraint con
JOIN pg_class rel ON rel.oid = con.conrelid
JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
WHERE nsp.nspname = 'public'
ORDER BY rel.relname, con.conname;
" > schema-constraints.txt
```

Indexes：

```bash
psql "$NQ_CI_PSQL_URL" -v ON_ERROR_STOP=1 -F $'\t' -P footer=off -c "
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;
" > schema-indexes.txt
```

Comments：

```bash
psql "$NQ_CI_PSQL_URL" -v ON_ERROR_STOP=1 -F $'\t' -P footer=off -c "
SELECT 'table' AS object_type,
       n.nspname AS schema_name,
       c.relname AS table_name,
       NULL::text AS column_name,
       obj_description(c.oid, 'pg_class') AS comment
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname = 'public' AND c.relkind IN ('r', 'p')
UNION ALL
SELECT 'column' AS object_type,
       n.nspname AS schema_name,
       c.relname AS table_name,
       a.attname AS column_name,
       col_description(c.oid, a.attnum) AS comment
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN pg_attribute a ON a.attrelid = c.oid
WHERE n.nspname = 'public'
  AND c.relkind IN ('r', 'p')
  AND a.attnum > 0
  AND NOT a.attisdropped
ORDER BY schema_name, table_name, object_type, column_name NULLS FIRST;
" > schema-comments.txt
```

Optional schema-only dump：

```bash
pg_dump --schema-only --no-owner --no-privileges "$NQ_CI_PSQL_URL" > schema-dump.sql
```

## Artifact retention policy

- PR / branch review：建议 artifact retention 7 days。
- `dev` push：建议 artifact retention 14 days；如后续 migration review 需要，可提升到 30 days。
- 不保存 raw application logs、runtime env dump、database data dump、credential payload、HTTP request / response body。
- 不上传 `.env`、`*.key`、`*.pem`、token dump、cookie dump、secret dump。
- schema-only artifact 应与 run id、commit sha 和 job name 绑定，便于追溯。

## Redaction / secret safety

Batch 2B artifact 必须执行以下安全边界：

- Artifact 只导出 schema metadata，不导出 table data。
- Artifact 不包含 `.env`、API key、secret、passphrase、token、cookie、private key、mnemonic、credential material。
- `schema-comments.txt` 如命中敏感词，应作为 P0/P1 review finding 处理；不得直接把敏感注释作为可接受 artifact 发布。
- CI env 只允许 CI-only PostgreSQL service values；不得注入 production DB credential 或 real exchange credential。
- 不打印 full environment；不执行 `env` / `printenv` 全量输出。
- 不访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken。
- LIVE disabled；AI not started；DH runtime not integrated。

Recommended artifact redaction check for implementation planning：

```bash
rg -n "apiKey|secret|passphrase|token|private key|mnemonic|cookie|credential|BEGIN .*PRIVATE KEY" \
  flyway-info.txt schema-tables.txt schema-columns.txt schema-constraints.txt schema-indexes.txt schema-comments.txt schema-dump.sql
```

命中并不一定都是泄露；字段名和禁止说明可能合理存在。但 implementation 必须把真实 material、env secret 和 data values 视为 blocking。

## DB_SCHEMA.md drift review checklist

Batch 2B 第一阶段只做人工 review checklist，不直接脚本化 blocking。

Checklist：

- `DB_SCHEMA.md` 声明的 current maximum migration 是否与 Flyway artifact 一致。
- `DB_SCHEMA.md` 中提到的关键表是否存在于 `schema-tables.txt`。
- `DB_SCHEMA.md` 中提到的关键字段是否存在于 `schema-columns.txt`。
- 状态字段的 allowed values 是否能在 `schema-constraints.txt` 中找到对应 CHECK。
- 关键唯一约束、foreign key、index 是否能在 `schema-constraints.txt` / `schema-indexes.txt` 中找到。
- 新增表是否有 table comment。
- 新增字段是否有 column comment。
- JSONB / payload / snapshot / metadata 字段 comment 是否包含用途、结构边界和敏感信息禁入规则。
- Credential 相关 comment 是否明确禁止保存 API key、secret、passphrase、token、cookie、private key、raw request、raw response、headers、signature、decrypted payload。
- `DB_SCHEMA.md` 如落后于 migration artifact，应登记 docs drift，不在 2B-1 自动改 schema 或 migration。

Future 2B-2 may script selected checks after manual checklist stabilizes. 2B-1 不直接引入 blocking drift checker。

## Merge-blocking strategy

- 2B planning-only：已完成并通过 review。
- 2B first implementation：已在 `postgres-flyway` job 内生成并上传 artifact；job failure 仍 blocking，不使用 `continue-on-error` 或 soft-fail。
- 2B first green review：确认 artifact 生成稳定、无 secret、可读、与 V1-V31 baseline 一致。
- 2B accepted 后：可考虑把 artifact generation 纳入 `postgres-flyway` required path，尤其是 migration / backend / DB docs changes。
- Docs-only changes 可只要求 `git diff --check` 和 docs stage wording guard，但不得写成 full CI passed。
- migration / DB docs / backend schema-sensitive changes 应触发 PostgreSQL / Flyway smoke + artifact review。

## Batch 2C/2D/2E boundary

- Batch 2B 不跑 repository real PostgreSQL smoke；该项仍属于 Batch 2C。
- Batch 2B 不启动 `nq-app` context；该项仍属于 Batch 2D。
- Batch 2B 不修改 CI-only seed watcher；该项仍属于 Batch 2E。
- Batch 2B 不引入 Testcontainers；Testcontainers 如需进入，应在 2C 或独立 test enhancement review 中评估。
- Batch 2B 不插入 seed，artifact 必须来自 empty DB migration smoke 后的 schema metadata。

## Batch 3-5 boundary

- Batch 2B 不实现 no-outbound guard；Batch 3 remains PENDING。
- Batch 2B 不实现 gitleaks / secret scan；Batch 4 remains PENDING。
- Batch 2B 不实现 frontend E2E hardening；Batch 5 remains PENDING。
- Batch 2B 不新增 frontend job，不改 frontend code，不改 Playwright。
- Batch 2B 不接 AI、DH runtime、LIVE、NQ RealClient、real provider 或 real exchange permission probe adapter。

## P0/P1/P2/P3 risk list

| Priority | Risk | Decision |
| --- | --- | --- |
| P0 | Artifact accidentally contains credential material, `.env`, token, private key, cookie, raw request / response, or DB data rows. | Block implementation / review; artifact must be schema metadata only and redaction-checked. |
| P0 | Batch 2B implementation mutates workflow into app context / repository DB smoke / seed / Testcontainers work. | Reject as scope violation; split into 2C/2D/2E. |
| P0 | CI enables LIVE, real provider, NQ RealClient, DH runtime, or real exchange access. | Forbidden; must remain disabled / not integrated. |
| P1 | `DB_SCHEMA.md` drift is discovered but treated as schema implementation work. | Record drift and fix docs separately; do not modify migration in 2B planning. |
| P1 | Artifact commands rely on JDBC URL directly in `psql`, causing future implementation failure. | Plan explicit libpq connection string or `PG*` env mapping. |
| P1 | Schema-only dump is too large or too noisy for routine review. | Keep optional; start with focused text artifacts. |
| P2 | PostgreSQL version mismatch remains (`postgres:16` CI vs local compose default `postgres:17.7`). | Record compatibility risk; do not change version in 2B planning. |
| P2 | Comments artifact can contain sensitive words as field names or forbidden examples. | Manual review must distinguish field names / prohibitions from material leakage. |
| P3 | Artifact names or retention policy drift. | Use stable names and 7-14 day default retention. |
| P3 | 2B wording is misread as frozen / accepted. | Keep status `IMPLEMENTED / FIRST GREEN RUN CONFIRMED` until Batch 2B freeze review is complete. |

## Implementation batches

### Batch 2B-1: artifact plan review

Status: ACCEPTED。

- Freeze artifact list。
- Freeze retention / redaction policy。
- Freeze DB_SCHEMA.md drift checklist。
- Do not modify workflow。

### Batch 2B-2: artifact implementation, if approved

Status: IMPLEMENTED。

- Update existing `postgres-flyway` job or reviewed workflow path to generate artifacts.
- Use `psql` / `pg_catalog` / `information_schema` commands after Flyway migrate + validate.
- Upload text artifacts with short retention.
- Add redaction scan for artifact files.
- Do not start app context or repository DB tests.

### Batch 2B-3: first green review

Status: COMPLETED / FIRST GREEN RUN CONFIRMED。

Evidence:

- GitHub Actions run `27521750442` (`NQ CI Baseline`, dev push, commit `c62ebddd5a522bbdf72bc018064b9eb36d8fe9e1`) completed with conclusion `success`.
- `PostgreSQL / Flyway smoke` job completed with conclusion `success`.
- `Run empty database Flyway smoke`, `Generate PostgreSQL schema artifacts`, `Check PostgreSQL schema artifacts`, and `Upload PostgreSQL schema artifacts` steps all completed with conclusion `success`.
- Artifact `nq-postgres-flyway-schema-artifacts` uploaded with artifact id `7628309014`, digest `sha256:a06957e02f55761047aff197d5954b2fbb2e2269f590b598b79549a5e72155e5`, size `74662` bytes, `expires_at=2026-06-29T03:14:04Z`, matching 14-day retention for `dev` push.
- Downloaded artifact contained exactly the required 7 files: `flyway-info.txt`, `schema-tables.txt`, `schema-columns.txt`, `schema-constraints.txt`, `schema-indexes.txt`, `schema-comments.txt`, `schema-dump.sql`.
- Downloaded artifact local checks found no data-row markers in `schema-dump.sql` and no high-risk credential / raw request / raw response pattern in artifact files.

- Review GitHub Actions run and artifact contents.
- Confirm V1-V31 metadata, comments, constraints and indexes are present.
- Confirm no secrets / credentials / data rows.
- Decide whether 2B can enter freeze review.

### Batch 2B-4: docs drift cleanup, if needed

Status: NOT STARTED。

- If artifacts reveal `DB_SCHEMA.md` drift, update docs in a separate docs-only task.
- Do not modify migration history or production code.

## Validation commands

Implementation validation for this task:

```powershell
git status --short
git diff --check
git diff --stat
git ls-files .github
git diff -- .github
git diff -- backend
git diff -- frontend
git diff -- research
git diff -- scripts
git diff -- deploy
git diff -- backend/**/db/migration
rg "continue-on-error|skipTests|LIVE=true|LIVE_ENABLED|apiKey|secret|passphrase|token|private key|OKX|Binance|Bybit|Gate|Coinbase|Kraken" .github/workflows/ci.yml docs/current
rg "printenv|^[[:space:]]*env$|pg_dump --schema-only --no-owner --no-privileges|information_schema|pg_catalog|pg_indexes|flyway_schema_history" .github/workflows/ci.yml docs/current/NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md docs/current/NQ_CI_POSTGRES_FLYWAY_PLAN.md
```

No Maven / npm / Python test is required for 2B first-run review because this task reviews GitHub Actions run evidence and updates docs only; it does not modify Java / TypeScript / Python code, tests, migration, frontend, research, scripts or deploy. GitHub Actions run `27521750442` is the first green run evidence for Batch 2B.

## Review decision

Review decision: PASS / ACCEPTED FOR FIRST GREEN RUN.

## Next concrete action

Next concrete action: `NQ-CI-POSTGRES-FLYWAY-2B-FREEZE-REVIEW` or `NQ-CI-POSTGRES-FLYWAY-2C-PLAN`。

Do not mix Batch 2C/2D/2E、Batch 3 no-outbound guard、Batch 4 security scan、Batch 5 frontend E2E hardening、AI、DH runtime、LIVE、real provider、NQ RealClient or real exchange permission probe adapter into Batch 2B implementation or first-run review.
