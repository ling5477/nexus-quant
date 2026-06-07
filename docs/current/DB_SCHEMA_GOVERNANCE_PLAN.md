# DB Schema Governance Plan

任务：NQ-DB-SCHEMA-GOVERNANCE-REVIEW-BATCH-1 后续计划
日期：2026-06-06
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ。
原则：本计划只定义后续 schema governance 工作单，不代表本轮已经修改表结构，也不代表 GateK 进入实现阶段。

## 1. 总体原则

- Batch 2-5 必须单独开工，不能合并到本轮审查任务。
- 所有 schema 变更都必须新增 Flyway migration，禁止修改历史 migration。
- 任何 migration 都必须包含 `COMMENT ON TABLE` 或 `COMMENT ON COLUMN`，并同步更新 `docs/current/DB_SCHEMA.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`。
- 不引入 AI、DH integration、LIVE trading、真实下单、真实撤单、真实交易所私有链路。
- 不把 retention purge、archive、disable、revoke 写成普通删除接口。

## 2. Batch 2：comment-only migration

状态：已完成。已新增 `V26__schema_comment_business_normalization.sql`，本批只做长期业务注释归一化和 JSONB 敏感信息边界补充，未新增字段、索引、约束、数据变更或 Repository 行为。

### 允许范围

- 新增一个 comment-only Flyway migration。
- 只修改 PostgreSQL `COMMENT ON TABLE` 与 `COMMENT ON COLUMN`。
- 清理长期业务表中的 `GateE/GateF/GateH/GateI/RC1/第一版/最小口径` 等阶段语义。
- 保留必要业务边界，例如 Paper only、SIM/LIVE 隔离、JSONB 不保存密钥/token/cookie、稳定性检查不是最终 freeze 验收。

### 禁止事项

- 禁止新增、删除、重命名字段。
- 禁止新增或修改 CHECK、INDEX、FK、UNIQUE。
- 禁止改业务代码、Repository、API、DTO、前端、Python、部署。
- 禁止把 GateK-PLAN 写成 GateK 进入实现阶段。

### 验收标准

- `COMMENT ON` 语句可重复回放于全新数据库。
- `rg -n "GateE|GateF|GateH|GateI|RC1|第一版|最小口径" backend/nq-infra/src/main/resources/db/migration` 仅命中历史 migration 和必要边界说明，新增 migration 不引入新的阶段化长期注释。
- `mvn -f backend/pom.xml test` 通过，或说明 comment-only 变更为何只需 Flyway 启动验证。
- `docs/current/DB_SCHEMA.md` 更新实际治理结果，不写未执行验证为通过。

### 回滚方式

- 新增一个反向 comment-only migration，把注释恢复到 Batch 2 前版本。
- 不回滚历史 migration，不使用 `git reset --hard`。

### 必须单独开工

Batch 2 必须单独提交，不能与字段新增、Repository 过滤、retention job 合并。

### 本批执行结果

- 新增 `backend/nq-infra/src/main/resources/db/migration/V26__schema_comment_business_normalization.sql`。
- migration 仅包含 `COMMENT ON TABLE` 与 `COMMENT ON COLUMN`。
- 清理历史表/字段注释中的工程交付批次措辞，改为稳定业务语义。
- 对 `payload_json`、`snapshot_json`、`config_json`、`request_json`、`result_json`、`summary_json`、`detail_json` 等字段补充敏感信息禁入说明。
- 未修改历史 migration，未新增表字段，未实现逻辑删除或 retention purge。

## 3. Batch 3：配置/主数据表字段和约束补齐

状态：进行中。Batch 3-A 已新增 `V27__schema_master_table_governance.sql`，只治理 `roles`、legacy `accounts`、`instrument_catalog` 三类主数据 / 配置表。Batch 3-B 已新增 `V28__schema_research_backtest_config_governance.sql`，只治理 `research_configs`、`backtest_configs` 两类研究 / 回测配置表。Batch 3-A/3-B 均未处理 `positions`、`risk_events`、订单、成交、账本、审计、Paper facts、Backtest facts 或 marketdata timeseries。

### Batch 3-A 本批执行结果

- 新增 `roles.updated_at`，补齐角色主数据更新时间；`roles.role_code` 唯一约束已存在，未重复新增。
- 新增 `accounts.updated_at`，并为 legacy `accounts.status` 增加 `ACTIVE / DISABLED` CHECK；迁移会先把非 `ACTIVE / DISABLED` 历史状态归一到 `DISABLED`，避免约束回放失败。
- `instrument_catalog` 已有 `created_at/updated_at` 和 `exchange_code + exchange_symbol`、`exchange_code + internal_symbol` 唯一约束；本批新增 `instrument_type IN ('SPOT')` 和 `status` 非空大写代码 CHECK。
- `instrument_catalog.status` 暂不采用 `ACTIVE / DISABLED / DELISTED` canonical 枚举，因为当前同步链路保存交易所原生 instrument 状态，强行改枚举会破坏现有 adapter upsert 与测试数据；canonical 状态抽象列为后续待确认项。
- 未新增 `roles.status`：当前 Repository 只通过 `role_code` 解析授权，尚无角色禁用 / 归档读取语义；避免新增未使用字段。
- 未修改 Java Repository / Domain / DTO 业务语义：新增字段都有 DB 默认值，新增约束与现有写入路径兼容；本批只同步了既有 package/path 不一致导致的后端测试装配问题。

### Batch 3-A 映射关系

- 用户候选 `roles` 对应当前 schema 中 `V1__init.sql` 创建的 `roles`。
- 用户候选 `accounts` 对应当前 schema 中 `V1__init.sql` 创建的 legacy `accounts`；正式账户配置表 `exchange_accounts` 已有状态与审计时间字段，本批未改。
- 用户候选 `instrument_catalog` 对应当前 schema 中 `V15__gateh_pre_instrument_catalog.sql` 创建的 `instrument_catalog`。

### Batch 3-B 本批执行结果

- `research_configs` 已有 `created_at/updated_at`，本批不重复新增；新增 `status`，允许值为 `ACTIVE / ARCHIVED / DISABLED`，默认 `ACTIVE`。
- `research_configs` 新增 `archived_at/archived_by/archive_reason`，用于归档元数据；归档一致性 CHECK 要求只有 `status=ARCHIVED` 时才允许存在归档元数据，且归档状态必须有 `archived_at`。
- `backtest_configs` 已有 `created_at/updated_at`，本批不重复新增；新增 `status`，允许值为 `ACTIVE / ARCHIVED / DISABLED`，默认 `ACTIVE`。
- `backtest_configs` 新增 `archived_at/archived_by/archive_reason`，用于归档元数据；归档一致性 CHECK 要求只有 `status=ARCHIVED` 时才允许存在归档元数据，且归档状态必须有 `archived_at`。
- 两张表的 `updated_at` 注释已收口为配置元数据最后更新时间，不表示回测运行、评估结果、发布记录或交易事实更新时间。
- `archive_reason` 注释明确不得保存密钥、token、API secret、私钥、助记词、cookie 或交易所凭证。
- 未修改 Java Repository / Domain / DTO 业务语义：新增字段都有 DB 默认值，现有 insert/select 路径不需要新增归档字段；Batch 4 如需默认过滤或归档接口，必须单独开工。

### Batch 3-B 映射关系

- 用户候选 `research_configs` 对应当前 schema 中 `V7__gate_f1_research_backtest_skeleton.sql` 创建的 `research_configs`。
- 用户候选 `backtest_configs` 对应当前 schema 中 `V7__gate_f1_research_backtest_skeleton.sql` 创建的 `backtest_configs`；该表在 `V18` 增加 dataset 绑定字段，在 `V20` 增加 strategy version / 参数 / 配置快照字段，本批只治理配置生命周期字段。

### 允许范围

- 新增 Flyway migration 补齐低风险字段或约束。
- 候选字段：
  - `roles.updated_at`
  - `accounts.updated_at`
  - `positions.created_at`（不属于 Batch 3-A，后续单独评估）
  - `exchange_account_credentials.revoked_by`
  - `exchange_account_credentials.revoke_reason`
  - `research_configs.status`（Batch 3-B 已完成）
  - `backtest_configs.status`（Batch 3-B 已完成）
- 候选约束：
  - `accounts.status IN ('ACTIVE','DISABLED')`
  - `instrument_catalog.instrument_type IN ('SPOT')`
  - `instrument_catalog.status` 原生状态治理（Batch 3-A 已先收口为非空大写代码；canonical 枚举待确认）
  - `risk_events.decision` 与 `risk_events.severity` 的业务枚举（不属于 Batch 3-A，后续单独评估）
- 字段新增必须给出默认值、回填策略、锁表影响和兼容性说明。

### 禁止事项

- 禁止给订单、成交、账本、审计、风控事件、回测/Paper 事实表批量加 `deleted_at`。
- 禁止让 `marketdata_bars`、心跳、曲线、日报等时序/快照表使用 soft delete。
- 禁止修改历史 migration。
- 禁止改 Repository 行为，除非字段已经落库且本批范围明确包含兼容读取。

### 验收标准

- Flyway 从空库可完整迁移到最新版本。
- 后端 `mvn -f backend/pom.xml test` 通过。
- 新增字段和约束均有 `COMMENT ON COLUMN`。
- JSONB 或敏感相关字段注释明确不保存密钥、token、cookie、exchange secret。
- 对现有数据的默认值和回填结果有验证 SQL。

### 回滚方式

- 字段新增回滚通过后续 migration 标记废弃或删除字段前先停写；约束回滚通过后续 migration `DROP CONSTRAINT`。
- 对大表字段不做同步大事务回滚；如涉及大表，先单独设计 expand/contract。

### 必须单独开工

Batch 3 不能与 Batch 4 Repository 默认过滤合并。先落 schema，再改代码。Batch 3-B 已落地 `research_configs/backtest_configs` 的状态和归档元数据；Repository 默认过滤、归档接口、状态流转测试仍属于 Batch 4。

## 4. Batch 4：Repository 查询过滤、逻辑删除/归档接口、测试

状态：4-A / 4-B 已完成。Batch 4-A 只接管 `research_configs`、`backtest_configs` 的 V28 status/archive 字段读语义和新运行状态闸门；Batch 4-B 增加两张配置表的受控归档命令。两批均未新增 migration，未新增前端 / Python / 部署改动，未处理 credentials、positions、risk_events、订单、成交、账本、审计、Paper facts、Backtest facts、评估结果、发布记录或 marketdata timeseries。

### Batch 4-A 本批执行结果

- `research_configs` 默认 Repository 列表查询排除 `status=ARCHIVED`；`DISABLED` 仍在默认列表中可见。
- `backtest_configs` 默认 Repository 列表查询排除 `status=ARCHIVED`；`DISABLED` 仍在默认列表中可见。
- 两张配置表按 ID 查询不增加 status 过滤，保留 archived 配置的历史追溯能力。
- Repository 新增 includeArchived 内部查询路径；本轮不新增外部 HTTP API 查询参数。
- Domain / DTO 同步 `status`、`archived_at`、`archived_by`、`archive_reason` 读模型字段。
- 新建 backtest config 要求 research config 为 `ACTIVE`；新建 backtest run 要求 research config 与 backtest config 都为 `ACTIVE`。
- 新增/修改后端测试覆盖默认列表隐藏 ARCHIVED、DISABLED 默认可见、ARCHIVED 按 ID 可读、非 ACTIVE 配置不能创建新 run。

### Batch 4-B 本批执行结果

- 新增 `POST /api/research-configs/{configId}/archive`，把研究配置标记为 `ARCHIVED`，写入 `archived_at/archived_by/archive_reason/updated_at`。
- 新增 `POST /api/backtest-configs/{configId}/archive`，把回测配置标记为 `ARCHIVED`，写入 `archived_at/archived_by/archive_reason/updated_at`。
- 归档命令幂等：重复归档返回当前详情，不覆盖首次归档元数据。
- `archiveReason` 可空，最长 1024 字符；应用层拒绝明显包含密钥、token、API secret、私钥、助记词等敏感材料的原因文本。
- `archived_by` 由 API 层从当前认证主体解析；没有认证主体时使用 `system`，该点作为当前最小实现风险保留。
- 本批不新增 `includeArchived` HTTP 查询参数；默认列表隐藏 `ARCHIVED`，详情按 ID 仍可读取。

### 允许范围

- 仅在 Batch 3 已新增 `status` 或归档字段后，修改对应 Repository 默认查询。
- 候选行为：
  - `research_configs.status=ARCHIVED` 后默认列表过滤或显式展示。
  - `backtest_configs.status=ARCHIVED` 后默认列表过滤或显式展示。
  - `instrument_catalog.status=DISABLED/DELISTED` 后前端和 API 不默认参与可交易筛选。
  - `exchange_accounts.status=DISABLED` 和 `exchange_account_credentials.verification_status=REVOKED` 保持已有状态语义。
- 补充 JUnit / repository integration / service regression tests。

### 禁止事项

- 禁止新增真实下单、撤单路径。
- 禁止把归档接口用于删除事实、审计、订单、成交、账本、风控、Paper run。
- 禁止改前端展示，除非本批工作单明确包含页面验收。
- 禁止没有 migration 就先写 `deleted_at IS NULL`。

### 验收标准

- 后端 `mvn -f backend/pom.xml test` 通过。
- 新增或修改的 Repository 查询有测试覆盖。
- 归档/停用/撤销状态流转有幂等测试和非法状态测试。
- API 文档明确默认过滤和显式查询语义。

### 回滚方式

- 通过后续代码提交恢复查询条件，或通过状态字段保留但不默认过滤。
- 不删除已新增字段；字段弃用需单独 migration 和兼容窗口。

### 必须单独开工

Batch 4 必须在 Batch 3 后执行，不能与 comment-only 或 retention purge 合并。

## 4.5. Batch 5-A：credential revocation governance review

状态：已完成只读审计。本批只新增/更新 `docs/current` 文档，未新增 migration，未修改 Java、Repository、API、前端、Python 或部署脚本。

本批结论：

- 当前正式 credential 表为 `exchange_account_credentials`，账户元数据表为 `exchange_accounts`。
- `exchange_account_credentials` 已有 `encrypted_payload`、`key_version`、`cipher_suite`、`masked_access_key`、`verification_status`、`is_active`、`revoked_at`、`rotated_from_credential_id`、`last_verified_at`、`last_verification_error`。
- 现有轮换语义是新增版本并把旧 active 版本标记为 `REVOKED`，但缺少独立不可恢复撤销命令、撤销操作者、撤销原因、轮换操作者、权限范围、last used、failed auth count、IP allowlist、withdraw disabled 证明和独立 audit log。
- 当前 API response 只返回 masked 摘要，不应返回 secret、token、private key、passphrase 或 decrypted payload。
- `exchange_account_credentials` 不得 hard delete；后续应使用状态和 append-only audit log 保留安全证据。

后续拆分与执行状态：

- Batch 5-B：已新增 `V29__schema_credential_revocation_governance.sql`，只做 credential revocation schema migration 和文档同步，不改代码。
- Batch 5-C：已在 Batch 5-B 后接入 Repository / Service / API / tests，不接 AI、DH、LIVE 或真实交易。

详见：

- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_REVIEW.md`
- `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`

## 4.6. Batch 5-B：credential revocation schema governance

状态：已完成 schema-only migration。本批新增 `backend/nq-infra/src/main/resources/db/migration/V29__schema_credential_revocation_governance.sql`，只处理 `exchange_account_credentials` 和 `credential_audit_logs`，未修改 Java、Repository、Service、Controller、DTO、前端、Python 或部署脚本。

本批执行结果：

- `exchange_account_credentials` 新增 `credential_status`，允许值为 `ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED`，并保留 `verification_status` 作为校验状态。
- `exchange_account_credentials` 新增撤销、轮换、使用、失败计数、权限元数据和外部密钥引用字段：`revoked_by`、`revoke_reason`、`rotated_at`、`rotated_by`、`last_used_at`、`failed_auth_count`、`permission_scope`、`withdraw_enabled`、`ip_allowlist_required`、`external_secret_ref`、`key_alias`。
- 历史 `verification_status='REVOKED'` 或 `is_active=false` 记录按现有轮换旧版本语义回填为 `credential_status='ROTATED'`，避免把轮换旧版本误写成不可恢复撤销。
- `permission_scope` 允许 `READ_ONLY / TRADE` 或 `NULL`；`withdraw_enabled` 默认 `FALSE`；`ip_allowlist_required` 默认 `TRUE`；`failed_auth_count` 有非负 CHECK 约束。
- 新增 `credential_audit_logs` append-only 审计日志表，记录 `CREATED / VERIFIED / FAILED_VERIFICATION / DISABLED / REVOKED / ROTATED / EXPIRED / USED / ACCESS_DENIED` 事件。
- 所有新增字段和新增表均包含 `COMMENT`，敏感文本和 JSONB metadata 注释明确禁止保存密钥、token、API secret、私钥、助记词、cookie、passphrase、签名、明文 payload 或交易所凭证。

Batch 5-B 未执行项：

- 未实现 revoke endpoint、rotate endpoint、active material 读取改造、Repository 默认过滤、Service 状态流转或 API response 字段接入。
- 未接入 KMS / Secret Manager 真实外部服务。
- 未接 AI、DH、LIVE 或真实交易。

Batch 5-C 执行结果：

- Repository / Service / API / tests 已接入 `credential_status` 生命周期字段。
- active summary / active material 查询默认只读取 `credential_status='ACTIVE'` 且 `is_active=true` 的凭证。
- 已新增 `revoke / disable / expire` 最小 command API；本轮未新增 rotate endpoint 或 enable endpoint。
- `credential_audit_logs` 已用于追加 `REVOKED / DISABLED / EXPIRED` 生命周期审计事件，metadata 只保存脱敏状态和来源。
- 未新增 migration、KMS / Secret Manager 真实外部服务、AI、DH、LIVE、真实交易所权限探活或真实交易路径。

## 5. Batch 5：大表 retention policy

### 允许范围

- 为高增长表制定 retention policy 文档和可审计清理脚本/任务。
- 候选表：
  - `marketdata_bars`
  - `marketdata_ingestion_runs`
  - `event_store`
  - `account_snapshots`
  - `sim_pnl_snapshots`
  - `equity_curve_snapshots`
  - `position_curve_snapshots`
  - `paper_run_schedule_fires`
  - `paper_run_heartbeats`
  - `paper_run_daily_reports`
  - `paper_run_stability_checks`
- 清理条件必须按业务时间、run id、dataset id 或 scope 分批，并输出 dry-run 统计。

### 禁止事项

- 禁止对 `orders`、`trades`、`ledger_entries`、`audit_logs`、`risk_events`、`paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades` 做 retention purge。
- 禁止无 dry-run 的批量删除。
- 禁止直接清理生产数据。
- 禁止把 retention purge 写成用户可随意触发的普通删除接口。

### 验收标准

- 每张 retention 表有留存周期、清理条件、索引依赖、批大小、dry-run SQL、执行 SQL、审计日志策略。
- 清理脚本必须支持 dry-run 和实际执行分离。
- 对 `marketdata_bars` 等大表必须确认使用时间索引或 scope+time 索引，避免全表扫描和长事务。
- 后端测试或脚本测试通过；如只是文档计划，必须明确未执行代码验证。

### 回滚方式

- 清理前必须先生成备份或导出清单。
- 对已物理清理的数据，只能通过备份恢复或重新 ingest/rebuild，不能依赖 soft delete 回滚。
- retention job 首次运行必须小窗口、可观测、可停止。

### 必须单独开工

Batch 5 必须单独开工。它涉及物理清理风险，不能与 schema 字段补齐、Repository 过滤或业务功能合并。

## 6. 批次依赖关系

```text
Batch 2 comment-only migration
  -> Batch 3 fields/check constraints
  -> Batch 4 Repository filtering and status APIs
  -> Batch 5-A credential revocation governance review
  -> Batch 5-B credential revocation schema completed
  -> Batch 5-C credential revocation Repository/API/tests

Batch 5 retention policy can be planned in parallel,
but execution must be separate and must not run before dry-run evidence exists.
```

## 7. 默认验证命令

后端 schema / Repository 批次：

```powershell
mvn -f backend/pom.xml test
```

文档或 comment-only 批次补充：

```powershell
git diff --check
rg -n "<禁写状态短语>" docs/current
```

如只改文档，不运行 Maven，必须在 `docs/current/TESTING.md` 或当轮报告中写清未运行原因，不能写成通过。

## 8. 风险说明

- 当前最主要风险不是缺少 `deleted_at`，而是误把所有表统一套 soft delete，破坏事实、审计、交易、账本、回测和 Paper run 追溯。
- `deleted_at` 只适合少数用户可编辑目录型数据；NQ 当前更适合 `DISABLED / REVOKED / ARCHIVED / RETENTION_PURGE` 的组合策略。
- retention purge 是物理删除，必须比普通 schema 变更更谨慎，必须带 dry-run、备份或可重建路径。
