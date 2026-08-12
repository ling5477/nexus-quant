# GateX-4A Persistent Artifact Locator Schema Review（attempt-01）

## Task classification

- 任务：`NQ-GATEX-4A-PERSISTENT-ARTIFACT-LOCATOR-SCHEMA-REVIEW`。
- 归属：NQ-only。
- 分类：L 级 `SCHEMA_DESIGN_REVIEW`，辅助类型为 artifact provenance、storage locator security、backward compatibility 与 migration work order。
- 主 skill：`db-schema-migration-review`；`nq-dh-workflow-router` 仅用于 Gate/范围路由，`nq-docs-writer` 仅约束本 evidence 的事实源、语言与 anti-churn。
- 允许产物：本 review evidence；本轮不创建 V37，不修改 Java、Frontend、Python、scripts、governance contract 或部署配置。

## Review status

- Schema/security decision：`PASS`（通过）。
- Authority handoff：`BLOCKED / AUTHORITY_MAPPING_MISMATCH`（阻断 / authority 映射不兼容）。
- Overall：设计已唯一选定并可供后续 migration implementation 使用，但当前 governance contract 不允许在 `work_batch_status=BLOCKED` 时把 `next_action` 切换为 `...-IMPLEMENTATION`，因此本轮不能同步 current authority，也不能输出整体 `READY_FOR_MIGRATION_IMPLEMENTATION` 成功态。

## Starting HEAD

```text
branch=dev
HEAD=5f4824eecaac5cffbbc314fb8f767bd6ba45c29f
```

## origin/dev HEAD

```text
origin/dev=5f4824eecaac5cffbbc314fb8f767bd6ba45c29f
HEAD == origin/dev
```

## Authority before

```text
authority_schema=3
active_gate=GateX
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateX-3
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateX-4
work_batch_status=BLOCKED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-4-PERSISTENT-ARTIFACT-LOCATOR-REQUIRED-BLOCKED
live=DISABLED
```

`scripts/docs/check-current-authority.ps1` 在 review 前返回 `errors=0 / PASS`。

## Existing artifact storage facts

- 已读取 V1～V36 全部 migration 的 DDL 操作与对象演进，并直接审查与 publish/release/shadow 相关的 V7、V9、V10、V11、V19、V20、V21、V26、V32、V36。
- 当前最高 Flyway 版本为 V36；没有 `artifact_locator`、`artifact_uri`、`artifact_storage_key`、`manifest_locator`、`manifest_storage_key` 或等价持久化字段。
- server-owned locator：`NONE`。
- stable production layout：`NONE`。
- persistent storage key：`NONE`。
- configured trusted root：`NONE`。
- test-only `strategy-release-manifest.golden.json` / schema 不是 production filename/layout contract，不能据此选择方案 C。

## Publish model findings

- `V10__gate_f5_publish_records.sql` 创建 `backtest_publish_records`，`publish_record_id` 是主键，`backtest_run_id` 唯一；V19 在同一 aggregate 上增加 `strategy_version_id` 与 immutable version snapshot。
- `BacktestPublishRecord`、`BacktestPublishService` 与 `JdbcBacktestPublishRecordRepository` 当前均无 locator/storage key。
- `BacktestPublishService.publish(...)` 在成功路径构造完整 publish fact 后调用一次 repository `upsert`；`publish_record_id` 在失败重试时保持稳定。
- JDBC 按 `backtest_run_id` 冲突更新既有 publish row。后续实现必须防止该 upsert 静默重绑已经存在的 locator。
- GateX-1 production model 已冻结：`releaseAnchorId == publishRecordId == backtest_publish_records.publish_record_id`。新增第二 release UUID 或第二 release aggregate 会造成身份漂移。

## Python artifact output findings

- `research/py/src/nq_research/evaluation/artifacts.py` 的 `write_evaluation_artifact(path, ...)` 使用 caller 提供的 `Path`，并创建 `path.parent`。
- Python writer 是离线通用 JSON writer；没有 server-owned root、publish identity binding、固定输出目录或稳定 production filename。
- evaluation artifact 的 `checksum` 是该 Python JSON payload 的校验事实，不是 GateX Strategy Release artifact-set digest，也不是 storage locator。
- 不得从 Python 临时路径、测试 `tmp_path`、artifact id 或 checksum 推导持久化 storage key。

## Trusted-root configuration findings

- production `@ConfigurationProperties` 当前只覆盖账户凭证、安全与 validation scheduler 等既有配置，没有 Strategy Release artifact root/storage provider 配置。
- `application-*.yml` 未定义 artifact trusted root。
- 未来必须新增 server-side typed configuration；不得回退到 `user.home`、working directory、`java.io.tmpdir`、客户端 path 或环境猜测。

## Design A review

方案 A：在 `backtest_publish_records` 增加 `artifact_storage_key` 与 `manifest_storage_key`。

- Identity：与现有 canonical publish/release anchor 同行绑定，无第二身份。
- Ownership：locator 是“该 publish/release 的 artifact 在哪里”的 immutable release fact，属于 publish aggregate；不会把 artifact 内容或客户端 filesystem path 塞入 research snapshot。
- Query：GateX-1 provenance 查询仍是一次按主键 SELECT，只增加两个 nullable 字段，无新 join。
- Transaction：新 publish 可在同一 DB write 中固化 publish provenance 与两个 key；失败重试只能在未成功发布时完成首次绑定。
- Compatibility：历史行保持两列均为 NULL，明确表示 `LEGACY_ARTIFACT_UNBOUND`。
- Extensibility：key 对应用保持 opaque；本地文件、对象存储或后续 provider 由 server-side resolver/config 解释。切换 provider 必须保留 key namespace 或走显式 forward migration，不能改写历史 row。
- Cost：两个 nullable short `VARCHAR`、三个 CHECK、两个 partial unique index、一个 immutable trigger；无第二表、FK 或 join。

结论：满足 identity、fail-closed、historical compatibility、immutability、最小 schema 与单一 release truth，选择 A。

## Design B review

方案 B：新增 `strategy_release_artifact_bindings` 1:1 binding table。

- 优点：可以把 storage lifecycle 从 research publish row 物理分离，也便于未来单独管理 provider metadata。
- 缺点：`publish_record_id` 仍需 PK/UNIQUE + FK 才能保证 1:1；创建 publish 与 binding 需要跨表事务；读取多一个 join；容易被误解为第二 release aggregate。
- 若表内再保存 `artifact_digest`，会在 Strategy Release manifest digest 与 `shadow_runs.artifact_digest` 之外制造第三套 digest truth。
- 当前不存在多版本 binding、provider history 或独立 lifecycle 需求，新增表没有足够事实支持。

结论：拒绝。它用额外 aggregate/事务/join 解决当前不存在的多绑定问题。

## Design C review

方案 C：只存一个 `artifact_storage_key`，按固定 convention 定位 manifest。

- 只有冻结 manifest filename 与 artifact directory layout 后才安全。
- 当前 production source/resources 没有稳定 filename/layout；test golden/schema 不能提升为 production convention。
- 现在选择 C 等同于发明目录约定，并会把 storage layout 变成隐式 contract。

结论：拒绝。待未来确有独立 contract freeze 时可通过新版本演进，但不得作为 V37 的假设。

## SELECTED DESIGN

```text
SELECTED DESIGN = A
```

在 `backtest_publish_records` 增加一对 nullable、server-owned、opaque key：

```text
artifact_storage_key VARCHAR(128) NULL
manifest_storage_key VARCHAR(128) NULL
```

两列必须同时 NULL 或同时非 NULL。非 NULL key 只允许单段 ASCII opaque identifier：

```regex
^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$
```

禁止 `/`、`\`、`:`、空白、控制字符、`.`/`..` segment、drive prefix、absolute path、URL 与客户端 filesystem path。

## Rejected designs

- B：不必要的第二 release aggregate、join 与跨表事务；若复制 digest 还会制造第三套 provenance truth。
- C：依赖当前不存在的 manifest filename/layout；违反“不用目录习惯当事实”的约束。

## Ownership decision

- `publishRecordId` 是 release identity 与唯一 ownership anchor。
- locator/storage key 是 publish/release immutable fact；artifact bytes 与 manifest bytes 仍归 server-side storage provider 管理。
- trusted root/provider configuration 是部署配置，不持久化到每条 publish row。
- locator 只表达 `where`；digest 只表达 `what`，二者不可互相推导。

## Locator/storage-key contract

- `artifact_storage_key`：定位该 release 的 artifact-set root/container 的 opaque server-owned key。
- `manifest_storage_key`：定位该 release manifest object 的 opaque server-owned key。
- 两个 key 均由受信 server storage component 产生；public API/客户端不得提交或覆盖。
- key 不是 path、URL、digest、publishRecordId 派生目录或 provider credential。
- 两个 key 分别设置 partial UNIQUE，禁止两个 publish identity 共享 artifact root 或 manifest object。

## Manifest locator contract

- V37 不冻结 manifest filename、扩展名或相对目录 layout。
- `manifest_storage_key` 独立定位 manifest object；resolver 读取、限长、校验为 regular file/object 后反序列化 `strategy-release-manifest.v1`。
- manifest 内 `artifactFiles[].relativePath` 继续由 GateX-1 verifier 校验；不得把 manifest storage key 当作 artifact relative path。

## Trusted-root relationship

未来 resolution 顺序必须固定为：

```text
typed configured trusted root/provider
  + persisted opaque storage key
  -> validate key
  -> resolve direct child under configured root/provider namespace
  -> normalize
  -> real-path containment
  -> inspect every path component with NOFOLLOW_LINKS
  -> reject symlink/reparse/special file
  -> load bounded manifest
  -> use resolved artifact-set root + manifest in GateX-1 verifier
```

- artifact key 与 manifest key 可以共用一个 typed storage root，也可以由同一 typed storage configuration 暴露两个受控 namespace；不得把 root 写入 row。
- 配置缺失、key 缺失、对象不存在、containment 无法证明或平台 link guarantee 不可用时统一 fail-closed。

## Release identity relationship

```text
publishRecordId = releaseAnchorId
```

- 两个 storage key 只绑定该 row 的 `publish_record_id`。
- 不新增 release UUID，不允许 release A 解析 release B 的 storage key。

## Artifact digest relationship

- Strategy Release `artifactDigest`：manifest 中 artifact-set 内容 provenance，经 GateX-1 verifier 验证后成为 release content fact。
- `shadow_runs.artifact_digest`：Shadow Run 创建时冻结上述已验证 release digest 的 snapshot；它记录“当次 Shadow 使用了什么内容”。
- V37 不在 `backtest_publish_records` 或新表重复保存 digest。
- locator/digest 不可互推：禁止用 digest 拼 key，也禁止仅凭 locator 宣称内容已验证。

## Immutability model

- 新 publish：优先在成功 publish 的同一 DB write 中写入两个 key，形成 atomic immutable release fact。
- 失败重试：仅允许既有 `FAILED` 且两个 key 均为 NULL 的同一 publish row，在转为 `SUCCEEDED` 时完成唯一一次 NULL → pair 绑定。
- 成功 publish 后：禁止任何 key → 另一 key、key → NULL 或普通 rebind/update。
- DB trigger 必须保护 immutable 边界；repository 的 conflict update 必须保留既有非 NULL key，并对不同 key fail-closed。
- 修复只能新建 release 或执行另行审批的 forward remediation；V37 不提供 PATCH locator/rebind API。

## Historical compatibility

- V37 只增加 nullable columns；历史 row 不改写。
- 旧 binary 在 migration 后仍可读写原列，但 rollout 窗口应暂停 publish，避免旧实例继续产生新的 unbound successful publish。
- Java model/repository 必须把 pair NULL 读成 legacy unbound，而不是默认 key。

## Legacy-unbound semantics

```text
artifact_storage_key IS NULL
AND manifest_storage_key IS NULL
=> LEGACY_ARTIFACT_UNBOUND
```

- GateX-4 API：`artifact verification unavailable`。
- admission preview：`BLOCKED/UNAVAILABLE`。
- 不返回推测 path，不尝试 filesystem scan，不降级为 digest-only verification。

## Backfill decision

```text
NO FAKE BACKFILL
```

禁止从本机目录、working directory、publishRecordId、artifactDigest、Python artifactId/checksum 或测试 convention 反推 storage key。

## Recommended schema

以下是下一任务应创建的 V37 目标 DDL；本轮未创建或执行：

```sql
-- V37__gate_x4_persistent_artifact_locator.sql
ALTER TABLE backtest_publish_records
    ADD COLUMN artifact_storage_key VARCHAR(128),
    ADD COLUMN manifest_storage_key VARCHAR(128);

ALTER TABLE backtest_publish_records
    ADD CONSTRAINT chk_backtest_publish_artifact_keys_pair
        CHECK (
            (artifact_storage_key IS NULL AND manifest_storage_key IS NULL)
            OR
            (artifact_storage_key IS NOT NULL AND manifest_storage_key IS NOT NULL)
        ) NOT VALID,
    ADD CONSTRAINT chk_backtest_publish_artifact_storage_key
        CHECK (
            artifact_storage_key IS NULL
            OR artifact_storage_key ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
        ) NOT VALID,
    ADD CONSTRAINT chk_backtest_publish_manifest_storage_key
        CHECK (
            manifest_storage_key IS NULL
            OR manifest_storage_key ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$'
        ) NOT VALID;

ALTER TABLE backtest_publish_records
    VALIDATE CONSTRAINT chk_backtest_publish_artifact_keys_pair;
ALTER TABLE backtest_publish_records
    VALIDATE CONSTRAINT chk_backtest_publish_artifact_storage_key;
ALTER TABLE backtest_publish_records
    VALIDATE CONSTRAINT chk_backtest_publish_manifest_storage_key;

CREATE UNIQUE INDEX uq_backtest_publish_artifact_storage_key
    ON backtest_publish_records (artifact_storage_key)
    WHERE artifact_storage_key IS NOT NULL;

CREATE UNIQUE INDEX uq_backtest_publish_manifest_storage_key
    ON backtest_publish_records (manifest_storage_key)
    WHERE manifest_storage_key IS NOT NULL;

CREATE FUNCTION prevent_backtest_publish_artifact_locator_rebind()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF OLD.artifact_storage_key IS DISTINCT FROM NEW.artifact_storage_key
       OR OLD.manifest_storage_key IS DISTINCT FROM NEW.manifest_storage_key THEN
        IF OLD.artifact_storage_key IS NOT NULL
           OR OLD.manifest_storage_key IS NOT NULL THEN
            RAISE EXCEPTION 'strategy release artifact locator is immutable';
        END IF;
        IF OLD.publish_status <> 'FAILED'
           OR NEW.publish_status <> 'SUCCEEDED'
           OR NEW.artifact_storage_key IS NULL
           OR NEW.manifest_storage_key IS NULL THEN
            RAISE EXCEPTION 'strategy release artifact locator binding is not allowed';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_backtest_publish_artifact_locator_immutable
BEFORE UPDATE OF artifact_storage_key, manifest_storage_key
ON backtest_publish_records
FOR EACH ROW
EXECUTE FUNCTION prevent_backtest_publish_artifact_locator_rebind();

COMMENT ON COLUMN backtest_publish_records.artifact_storage_key IS
    '服务端生成的 Strategy Release artifact-set opaque storage key；为空表示历史未绑定，禁止保存绝对路径、URL、digest、客户端路径或 trusted root';
COMMENT ON COLUMN backtest_publish_records.manifest_storage_key IS
    '服务端生成的 Strategy Release manifest opaque storage key；与 artifact_storage_key 成对绑定，为空表示历史未绑定，不冻结 filename/layout';
COMMENT ON CONSTRAINT chk_backtest_publish_artifact_keys_pair ON backtest_publish_records IS
    'artifact 与 manifest storage key 必须同时为空或同时存在；同时为空表示 LEGACY_ARTIFACT_UNBOUND';
COMMENT ON CONSTRAINT chk_backtest_publish_artifact_storage_key ON backtest_publish_records IS
    'artifact_storage_key 只允许最多 128 字符的单段 ASCII opaque identifier，禁止 path、URL 与控制字符';
COMMENT ON CONSTRAINT chk_backtest_publish_manifest_storage_key ON backtest_publish_records IS
    'manifest_storage_key 只允许最多 128 字符的单段 ASCII opaque identifier，禁止 path、URL 与控制字符';
COMMENT ON FUNCTION prevent_backtest_publish_artifact_locator_rebind() IS
    '禁止 Strategy Release artifact locator 静默重绑；只允许尚未成功发布的 FAILED row 在成功重试时完成首次成对绑定';
COMMENT ON TRIGGER trg_backtest_publish_artifact_locator_immutable ON backtest_publish_records IS
    '数据库层保护 publish 后 artifact/manifest storage key 不可变';
```

## Recommended constraints

- pair CHECK：两列全 NULL 或全非 NULL。
- 两个格式 CHECK：单段、ASCII、最大 128 字符；拒绝 path/URL 形状。
- immutable trigger：只允许 `FAILED + NULL pair` 在成功重试时首次绑定；禁止成功 release 重绑。
- 不增加 `publish_status => key required` 的 CHECK：否则无法无损表达历史成功但 unbound 的记录，也会破坏滚动兼容性。新 publish 必须由应用层测试保证写 pair。

## Recommended indexes/FKs

- `uq_backtest_publish_artifact_storage_key`：partial UNIQUE，防止 artifact root 跨 release 共享。
- `uq_backtest_publish_manifest_storage_key`：partial UNIQUE，防止 manifest object 跨 release 共享。
- 不新增普通查询索引：读取仍按 `publish_record_id` 主键；locator 不作为列表过滤条件。
- 不新增 FK：storage provider/root 不是数据库 row；不存在可引用的 provider table。

## Migration lock/risk assessment

- `ADD COLUMN ... NULL` 在当前 PostgreSQL 版本通常是 catalog-only，但仍短暂取得 `ACCESS EXCLUSIVE` lock；必须设置 bounded `lock_timeout`/部署停止条件。
- `ADD CHECK ... NOT VALID` 短时持锁；后续 `VALIDATE CONSTRAINT` 使用较弱锁但扫描整表。
- partial UNIQUE index build 会扫描表并阻塞冲突写；普通 Flyway transaction 中不能用 `CREATE INDEX CONCURRENTLY`。
- trigger/function 创建是 metadata change，但 trigger 从创建后立即影响 update。
- 当前 repository 没有 table cardinality/size 事实；本轮未连接或写入数据库，因此实际规模为未验证项。部署前必须采集 `pg_class.reltuples`、`pg_total_relation_size`、长事务与锁等待。
- 若表规模或写入量超过维护窗口阈值，需把 concurrent index build 设计为经独立审查的 non-transactional Flyway migration；不得在 V37 临时改策略。
- migration 与旧应用 schema 兼容，但旧实例会产生 unbound success；部署窗口必须暂停 publish 或先下线旧 writer，再启用新 writer。

建议的只读 pre-deploy SQL：

```sql
SELECT count(*) AS publish_rows,
       count(*) FILTER (WHERE publish_status = 'SUCCEEDED') AS succeeded_rows,
       pg_size_pretty(pg_total_relation_size('backtest_publish_records')) AS total_size
FROM backtest_publish_records;

SELECT pid, state, xact_start, query_start
FROM pg_stat_activity
WHERE datname = current_database()
  AND xact_start IS NOT NULL
ORDER BY xact_start;
```

## Forward remediation strategy

- Flyway 已应用后不做 down migration，不删除 V37，不修改历史 migration。
- 应用回滚：回滚 Java reader/writer 时保留两列、约束、索引与 trigger；旧应用忽略新列。
- 数据修复：不 UPDATE locator，不扫 filesystem；损坏/丢失 artifact 必须新 publish/new release，或另开有审批、审计与独立 migration 的 forward remediation。
- 若新部署在 migration 后失败，停止新 publish，回滚应用，保留 schema；不得 drop columns 造成已写 key 丢失。

## GateX-1 integration

- `JdbcStrategyReleaseProvenanceRepository` 的单次 SELECT 增加两个 key。
- 新 resolver 从 typed configuration + keys 加载 bounded manifest，并把解析后的 artifact-set root 与 manifest 传给现有 `StrategyReleaseProductionService` / `TrustedRootStrategyArtifactVerifier`。
- GateX-1 verifier 的 containment、NOFOLLOW_LINKS、digest 与 sensitive-content 规则不降低、不绕过。

## GateX-2 integration

- V37 不新增 digest column。
- Strategy Release manifest 的 verified `artifactDigest` 继续作为 release content provenance。
- 创建 Shadow 时把该 digest snapshot 写入 `shadow_runs.artifact_digest`；locator 不写入 Shadow row。

## GateX-3 integration

- admission 只接受 server-resolved 且 GateX-1 `VERIFIED` 的 Strategy Release。
- legacy unbound、resolver/config failure、manifest missing、key invalid 或 digest mismatch 均返回 fail-closed decision，不创建 Shadow、不授权交易。

## GateX-4 unblock conditions

1. V37 fresh install 与 V36→V37 upgrade PostgreSQL tests 通过。
2. Java model/repository 支持 nullable pair，upsert 不重绑既有 key。
3. publish 成功路径只能接收受信 server storage component 生成的 pair，并在同一 DB write 固化；public API 不接收 path/URL/key。
4. typed trusted-root/storage config 无 fallback；resolver 完成 containment、NOFOLLOW_LINKS、size cap 与安全错误转换。
5. legacy unbound API/admission tests 证明 `BLOCKED/UNAVAILABLE`。
6. immutability、duplicate key、invalid key、partial pair、missing object、symlink/reparse 与 digest mismatch tests 通过。
7. GateX-1/2/3 regression 与 full backend tests 通过。
8. authority contract 能合法表达 review 后的 migration implementation next action。

## P0

- 无。

## P1

- `AUTHORITY_MAPPING_MISMATCH`：现有 `governance-workflow-contract.json` 将 `BLOCKED` 状态的 next action type 固定为 `BLOCKED`；`NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-IMPLEMENTATION` 被解析为 `IMPLEMENTATION`。按任务约束不得修改 governance contract，因此本轮不能把 authority 切到 4B。

## P2

- 实际 `backtest_publish_records` 表规模、长事务与 migration lock duration 未在真实数据库验证；4B 必须在执行 migration 前运行只读容量/锁检查。
- partial UNIQUE index 在 Flyway 单事务内的锁窗口需按真实规模复核；超阈值时必须另行审查 non-transactional concurrent index 方案。

## P3

- `idea-mcp`、`filesystem` 与 `postgres` MCP 本会话未暴露；按仓库降级到 PowerShell + `rg` 读取源码、migration、config 与 Git index。文件事实可信度高，真实数据库规模结论未验证。

## Files created

- `docs/current/evidence/gate-x/NQ-GATEX-4A-PERSISTENT-ARTIFACT-LOCATOR-SCHEMA-REVIEW.attempt-01.md`

## Files changed

- 除本 evidence 外无本轮文件修改。
- 未修改 `STATUS.md`、`ROADMAP.md`、`TESTING.md`、`WORKLOG.md`、README 或 `DB_SCHEMA.md`，避免在 authority mapping 不合法时制造 current conflict。
- Backend/Frontend/Research/Scripts/GitHub/Deploy/Migration changes：0。

## Evidence file

本文件即 attempt-01 schema/security/ownership evidence；它记录设计决策，不宣称 V37 已实现或 GateX-4 已解除阻断。

## Authority after

保持不变：

```text
work_batch=GateX-4
work_batch_status=BLOCKED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-4-PERSISTENT-ARTIFACT-LOCATOR-REQUIRED-BLOCKED
```

原因：候选 4B action 与当前 governance status-to-action mapping 不兼容。

## Next implementation work order

设计上唯一后续 implementation work order 为：

```text
NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-IMPLEMENTATION
```

精确范围：

- 新增且只新增 `V37__gate_x4_persistent_artifact_locator.sql`，实现本文 DDL；不得修改 V1～V36。
- `BacktestPublishRecord` 增加两个 nullable key；新增明确的 `LEGACY_ARTIFACT_UNBOUND` 派生语义。
- `JdbcBacktestPublishRecordRepository` 的 INSERT/SELECT/mapper 增加两列；conflict update 只允许合法首次绑定，既有非 NULL pair 不同则 fail-closed。
- `BacktestPublishService` 的成功 publish write path 接收受信 internal storage component 产出的 pair；不得从 public request、digest、publishRecordId 或本机目录生成/推导。
- `JdbcStrategyReleaseProvenanceRepository` 读取 key；新增 typed configuration/resolver 的接口边界，但不得使用 working-directory/user.home/temp fallback。
- 历史 NULL pair 保持 NULL；API/admission 后续读到 legacy unbound 必须 `BLOCKED/UNAVAILABLE`。
- PostgreSQL tests：fresh V1→V37、upgrade V36→V37、legacy rows no-backfill、pair/format/unique/trigger、FAILED→SUCCEEDED 首次绑定、成功 release rebind rejection。
- Java tests：正常 publish、失败重试、idempotent retry、conflicting key、legacy read、resolver/config missing fail-closed、绝对路径/URL/path-like key rejection。
- 禁止：Frontend/Python、LIVE、真实交易、Shadow create/start、client path/key API、digest duplication、filesystem guessing、普通 PATCH/rebind、governance contract 修改、commit/push。

该 work order 当前只是设计产物；必须先由合法 authority mapping 授权后才能执行。

## Review decision

```text
SCHEMA REVIEW = PASS
SELECTED DESIGN = A
SECURITY BOUNDARY = FROZEN IN THIS EVIDENCE
AUTHORITY HANDOFF = BLOCKED
```

## Next action

- 设计层下一动作：`NQ-GATEX-4B-PERSISTENT-ARTIFACT-LOCATOR-MIGRATION-IMPLEMENTATION`。
- 治理层当前合法 machine next action：仍为 `NQ-GATEX-4-PERSISTENT-ARTIFACT-LOCATOR-REQUIRED-BLOCKED`。
- 在 governance owner 提供无需修改本任务禁止文件的合法映射/授权前，不得启动 4B。

## Final decision

```text
BLOCKED /
AUTHORITY_MAPPING_MISMATCH /
PERSISTENT_ARTIFACT_LOCATOR_SCHEMA_SELECTED_A /
SECURITY_BOUNDARY_REVIEWED /
NO_MIGRATION_OR_PRODUCT_CODE_WRITTEN
```
