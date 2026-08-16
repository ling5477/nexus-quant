# Current DB Schema

数据库结构以 Flyway migrations 为准。本文只记录当前数据库事实入口，不复制完整 DDL。

## 本地数据库规则

- 本地 PostgreSQL 默认端口：`5432`。
- 本地 JDBC 默认地址：`jdbc:postgresql://localhost:5432/nexus_quant`。
- `application-local.yml` 支持 `NQ_DB_URL` 覆盖。
- `application-local.yml` 支持 `NQ_DB_PORT` 覆盖，默认 `5432`。

## 当前已有表域

当前数据库已包含用户、账户、凭证、订单、成交、持仓、策略、调度、研究、回测、评估、发布、行情基础表。具体字段、索引、约束以 `backend/**/db/migration` 下的 Flyway migration 为准。

## Schema Comment Governance

`V26__schema_comment_business_normalization.sql` 已完成数据库注释业务语义归一化。本次只更新 PostgreSQL `COMMENT ON TABLE` 与 `COMMENT ON COLUMN`，不新增表、字段、索引、约束或数据变更；重点是把长期注释改为稳定业务含义，并为 JSONB、payload、snapshot、config、request、result、summary、detail 等字段补充敏感信息禁入边界。

## Schema Master Table Governance

`V27__schema_master_table_governance.sql` 已完成 Batch 3-A 主数据 / 配置表最小结构治理。本批只处理 `roles`、legacy `accounts`、`instrument_catalog`：

- `roles`：新增 `updated_at`，用于记录角色主数据维护时间；`role_code` 唯一约束已在 `V1` 存在，本批不重复新增。
- `accounts`：新增 `updated_at`；将历史异常状态归一到 `DISABLED` 后新增 `chk_accounts_status`，允许值为 `ACTIVE / DISABLED`；`account_code` 唯一约束已在 `V1` 存在。
- `instrument_catalog`：保留既有 `exchange_code + exchange_symbol`、`exchange_code + internal_symbol` 唯一约束；新增 `instrument_type` 现货枚举约束；新增 `status` 非空大写代码约束。当前 `status` 仍承载交易所原生 instrument 状态，不在本批强制改成 NQ canonical 状态。
- 本批未处理 `positions`、`risk_events`、订单、成交、账本、审计、Paper facts、Backtest facts 或 marketdata timeseries。

`V28__schema_research_backtest_config_governance.sql` 已完成 Batch 3-B 研究 / 回测配置表治理。本批只处理 `research_configs`、`backtest_configs`：

- `research_configs`：保留既有 `created_at/updated_at`；新增 `status`，允许值为 `ACTIVE / ARCHIVED / DISABLED`；新增 `archived_at`、`archived_by`、`archive_reason`，用于记录配置归档元数据。
- `backtest_configs`：保留既有 `created_at/updated_at`；新增 `status`，允许值为 `ACTIVE / ARCHIVED / DISABLED`；新增 `archived_at`、`archived_by`、`archive_reason`，用于记录配置归档元数据。
- 两张表的归档一致性约束均要求：只有 `status=ARCHIVED` 时才允许存在归档元数据，且归档状态必须有 `archived_at`；`archived_by` 与 `archive_reason` 可为空。
- 两张表的 `updated_at` 注释已明确为配置元数据最后更新时间，不表示回测运行、评估结果、发布记录或交易事实更新时间。
- `archive_reason` 注释明确禁止保存密钥、token、API secret、私钥、助记词、cookie 或交易所凭证。
- Batch 3-B 本身未新增 Repository 默认过滤、归档业务 API、逻辑删除、物理删除或 retention purge。
- 本批未处理回测事实表、评估结果表、发布记录、Paper facts、orders、trades、ledger、risk_events、positions、marketdata timeseries 或 credentials。

## Credential Revocation Schema Governance

`V29__schema_credential_revocation_governance.sql` 已完成 Batch 5-B credential revocation schema-only 治理。本批只处理 `exchange_account_credentials` 和新增 `credential_audit_logs`。

- `exchange_account_credentials`：新增 `credential_status`，允许值为 `ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED`，用于把凭证生命周期从 `verification_status` 校验状态中拆出。
- `exchange_account_credentials`：新增 `revoked_by`、`revoke_reason`、`rotated_at`、`rotated_by`，用于记录撤销与轮换元数据；`revoke_reason` 注释明确禁止保存密钥、token、API secret、私钥、助记词、cookie、passphrase 或交易所凭证。
- `exchange_account_credentials`：新增 `last_used_at`、`failed_auth_count`，用于记录使用时间和认证失败计数；`failed_auth_count` 有非负 CHECK 约束。
- `exchange_account_credentials`：新增 `permission_scope`、`withdraw_enabled`、`ip_allowlist_required`、`external_secret_ref`、`key_alias`，用于权限与外部密钥引用元数据；`permission_scope` 经 V31 扩展后允许 `READ_ONLY / TRADE / FUNDING` 或 `NULL`，`withdraw_enabled` 默认 `FALSE`，`ip_allowlist_required` 默认 `TRUE`。
- 历史 `verification_status='REVOKED'` 或 `is_active=false` 记录按现有轮换语义回填为 `credential_status='ROTATED'`，并用 `revoked_at/updated_at` 补齐 `rotated_at`；本回填不读取、不输出、不复制任何真实 credential material。
- `credential_audit_logs`：新增 append-only 审计日志表，记录 `CREATED / VERIFIED / FAILED_VERIFICATION / DISABLED / ENABLED / REVOKED / ROTATED / EXPIRED / USED / ACCESS_DENIED / PERMISSION_PROBE_STARTED / PERMISSION_PROBE_SUCCEEDED / PERMISSION_PROBE_FAILED / PERMISSION_PROBE_SKIPPED` 事件，包含 credential/account 外键、actor、reason、metadata、created_at；`ENABLED` 表示 `DISABLED` credential 经校验后重新启用，permission probe 事件只表示后续权限探活审计语义已准备。
- `credential_audit_logs.metadata` 注释明确只允许保存脱敏状态、结果码、request id、策略判断等审计上下文，不得保存密钥、token、API secret、私钥、助记词、cookie、passphrase、签名、headers、request body、raw response、明文 payload 或交易所凭证。
- Batch 5-C 已在应用代码中接入 V29 生命周期字段：active summary / active material 查询默认同时要求 `is_active=true` 和 `credential_status='ACTIVE'`；旧 active 版本轮换时写为 `credential_status='ROTATED'`，不再把 `verification_status` 继续写成 `REVOKED`。
- Batch 5-C 已新增 `revoke / disable / expire` 最小 command API，并通过 `credential_audit_logs` 追加 `REVOKED / DISABLED / EXPIRED` 审计事件；audit metadata 只保存脱敏状态和来源。
- Batch 5-D-B 未新增 migration；显式 rotate command 复用 V12 active partial unique index、V29 `rotated_at / rotated_by` 和 `credential_audit_logs`，在单事务内锁定旧 ACTIVE credential、旧 credential 标记 `ROTATED`、新 credential 创建为 `ACTIVE`，并追加旧 `ROTATED` / 新 `CREATED` audit log。audit metadata 只保存 old/new credentialId、credentialType、状态、来源和 reasonPresent，不保存 secret、token、private key、passphrase、明文 payload 或交易所凭证。
- Batch 5-E-B 未新增 migration、未修改历史 migration；应用层已把 active summary / active material 改为 deterministic selection：无 `credentialType` 时先列出同 account 所有 `credential_status='ACTIVE' AND is_active=true` 候选，0 条返回未配置，1 条返回该 credential，多条返回业务冲突；指定 `credentialType` 时只读取对应 ACTIVE credential。V12 `(exchange_account_id, credential_type) WHERE is_active = TRUE` partial unique index 保持不变，V29 `permission_scope` 仍只作为治理元数据，不用于交易权限判断。
- `V30__schema_credential_enable_audit_event.sql` 已完成 Batch 5-F-B schema-only 治理：仅重建 `credential_audit_logs.event_type` CHECK，增加 `ENABLED`，并更新 `credential_audit_logs` 表、`event_type` 和 `metadata` 注释；未新增字段，未修改 `exchange_account_credentials`，未做数据 backfill。
- Batch 5-F-C 未新增 migration、未修改历史 migration；应用层已新增 credential enable command：只允许 `DISABLED` 且 `is_active=false` 的 credential 经本地结构性校验后恢复为 `ACTIVE`，同事务内检查同 account + credentialType 无其他 ACTIVE，写入 `ENABLED` audit log。enable 不清零 `failed_auth_count`，不清空 `revoked_at / rotated_at` 历史字段，不把 `permission_scope=NULL` 解释为 `TRADE`。
- `V31__schema_credential_permission_probe.sql` 已完成 permission probe schema-only 治理：新增 `permission_probe_status`、`last_permission_probe_at`、`last_permission_probe_error`、`ip_allowlist_probe_status`；扩展 `permission_scope` CHECK 支持 `FUNDING`；扩展 `credential_audit_logs.event_type` CHECK 支持 `PERMISSION_PROBE_STARTED / PERMISSION_PROBE_SUCCEEDED / PERMISSION_PROBE_FAILED / PERMISSION_PROBE_SKIPPED`；同步新增字段和 metadata 敏感信息禁入 COMMENT。
- V31 为既有 credential 记录提供安全默认值：`permission_probe_status='NOT_PROBED'`、`ip_allowlist_probe_status='NOT_CHECKED'`，只表示尚未做真实权限探活，不改变业务可用性语义。
- V31 未新增 `withdraw_enabled = FALSE` 硬 CHECK：现有字段已 `NOT NULL DEFAULT FALSE`，但本轮未查询生产/本地现有数据证明所有既有行均为 false，因此只更新注释并把强制 false CHECK 留给后续单独数据确认批次；`withdraw_enabled=true` 不得被视为可接受生产状态。
- Permission probe 最小 code/API/test 已实现并接入 V31 字段：Service claim 时写 `permission_probe_status='IN_PROGRESS'`；完成、失败或策略跳过时写回 `permission_probe_status`、`permission_scope`、`ip_allowlist_probe_status`、`last_permission_probe_at`、`last_permission_probe_error` 和必要时递增 `failed_auth_count`。成功不自动清零 `failed_auth_count`；`permission_scope=NULL` 不被当作 `TRADE`；`withdraw_enabled=true` 在代码层视为风险并跳过 probe。本轮未新增或修改 migration，未接真实交易所 adapter、前端、Python、部署、AI、DH、LIVE 或真实交易路径。
- Permission probe guarded backend implementation 已冻结为 no-real-exchange baseline；冻结不改变 V31 schema，不新增 migration，不表示真实交易所权限可用。真实 adapter、真实 HTTP 探活、LIVE probe、AI/DH credential access 仍未实现。
- P3 cleanup 仅修复 NoReal fake result 的 requestId / traceId 字段质量，不改变任何 V31 字段、CHECK、COMMENT 或 migration 语义。

## Research / Backtest Config Archive Semantics

Batch 4-A 已接管 V28 新增的 `research_configs` / `backtest_configs` 生命周期字段；Batch 4-B 已增加受控归档命令。两批均未新增 migration，也未实现物理删除或 retention purge。

- `research_configs` 默认 Repository 列表查询排除 `status='ARCHIVED'`；`status='DISABLED'` 仍出现在默认列表中。
- `backtest_configs` 默认 Repository 列表查询排除 `status='ARCHIVED'`；`status='DISABLED'` 仍出现在默认列表中。
- 两张配置表的按 ID 查询不按 `status` 过滤，允许读取 archived 配置，用于历史 backtest run、evaluation、publish record 追溯。
- Repository 保留内部 includeArchived 查询路径，但本轮不向外部 HTTP API 增加 `includeArchived` 参数。
- 新建 backtest config 要求关联的 research config 为 `ACTIVE`。
- 新建 backtest run 要求关联的 research config 与 backtest config 都为 `ACTIVE`。
- `POST /api/research-configs/{configId}/archive` 和 `POST /api/backtest-configs/{configId}/archive` 会把配置标记为 `ARCHIVED`，并写入 `archived_at`、`archived_by`、`archive_reason`、`updated_at`。
- 归档命令幂等：已归档配置重复归档返回当前详情，不覆盖首次归档时间、操作者或原因。
- `archive_reason` 可空，应用层会限制长度并拒绝明显包含密钥、token、API secret、私钥、助记词等敏感材料的原因文本。
- `updated_at` 仍只表示配置元数据最后更新时间；归档命令会同步更新 `updated_at`。

## GateH-2 当前 Marketdata 结构

GateH-2 新增 Flyway migration：

- `V16__gate_h2_marketdata_ingestion.sql`
- `V17__gate_h2_ingestion_created_by_width.sql`

`marketdata_bars` 当前支持：

- 维度字段：`exchange_code`、`market_type`、`symbol`、`interval`、`open_time`、`close_time`。
- OHLCV 字段：`open_price`、`high_price`、`low_price`、`close_price`、`volume`、`quote_volume`、`trade_count`。
- 溯源字段：`source`、`quality_status`、`raw_payload_json`、`ingested_at`。
- 唯一约束：`exchange_code + market_type + symbol + interval + open_time`，用于保证历史 K 线幂等 upsert。
- 关键索引：`idx_marketdata_bars_scope_time_desc`，用于按交易所、市场、交易对、周期和时间倒序查询。

GateH-2 新增 `marketdata_ingestion_jobs`：

- 任务字段：`job_id`、`exchange_code`、`market_type`、`symbol`、`interval`、`start_time`、`end_time`。
- 状态字段：`status`，允许值 `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`PARTIAL`。
- 审计字段：`source`、`created_by`、`created_at`、`updated_at`、`request_json`。
- 关键索引：`idx_marketdata_ingestion_jobs_scope_updated`，用于任务列表按范围和更新时间查询。

GateH-2 新增 `marketdata_ingestion_runs`：

- 运行字段：`run_id`、`job_id`、`status`、`started_at`、`finished_at`。
- 请求/实际范围字段：`requested_start_time`、`requested_end_time`、`actual_start_time`、`actual_end_time`。
- 统计字段：`fetched_bars`、`inserted_bars`、`updated_bars`、`skipped_bars`。
- 排障字段：`error_message`、`raw_summary_json`、`created_at`。
- 外键：`job_id` 关联 `marketdata_ingestion_jobs.job_id`。
- 关键索引：`idx_marketdata_ingestion_runs_job_started`，用于任务详情按运行开始时间倒序查询。

## GateM-2E no-migration Marketdata Readiness Aggregation

GateM-2E 未新增 migration、未修改历史 migration、未新增表、字段、索引或约束。`GET /api/marketdata/readiness` 只复用既有表聚合：

- `marketdata_bars`：按 `exchange_code + market_type + symbol + interval` 与可选时间窗口统计 `barCount`、`firstBarTime`、`lastBarTime`、`qualityStatusSummary`、`unknownQualityCount`，并根据 open time 序列估算 `expectedBarCount` 与 `gapCount`。
- `marketdata_ingestion_jobs` / `marketdata_ingestion_runs`：按同一 scope 聚合派生 `lastSuccessAt`、`lastFailureAt` 与最新 run 状态，用于 `sourceHealthReason`。这些字段是 derived evidence，不是持久 source-health 状态。
- `marketdata_datasets` / `marketdata_dataset_coverage`：仍是 dataset 质量事实；本轮 API 不要求必须存在 dataset/coverage，也不新增 dataset 依赖。

边界：该聚合不读取 `raw_payload_json` 内容，不读取 credential material，不调用历史行情 provider / adapter，不访问外部交易所，不启用 LIVE。`backendSupportLevel=NO_MIGRATION_MVP` 表示当前仍是现有表支撑的 MVP，不代表 source health 全量持久化完成。

## 注释与 JSONB 约定

- GateH-2 新增表均包含 `COMMENT ON TABLE`。
- GateH-2 新增字段均包含 `COMMENT ON COLUMN`。
- `request_json` 保存任务创建请求快照，不保存密钥、token、cookie。
- `raw_payload_json` 保存单根 K 线的交易所原始 payload 快照，用于审计和排障。
- `raw_summary_json` 保存单次运行统计摘要，不作为业务查询主结构。

## GateH-3 当前 Dataset 与 Backtest 绑定结构

GateH-3 新增 Flyway migration：

- `V18__gate_h3_marketdata_dataset_binding.sql`

GateH-3 新增 `marketdata_datasets`：

- 范围字段：`dataset_id`、`dataset_name`、`exchange_code`、`market_type`、`symbol`、`interval`、`start_time`、`end_time`。
- 状态字段：`status`，允许值 `CREATED`、`READY`、`INVALID`、`ARCHIVED`。
- 质量字段：`quality_status`，允许值 `OK`、`GAP_DETECTED`、`INCOMPLETE`、`INVALID`。
- 统计字段：`bar_count`、`gap_count`。
- 审计字段：`source`、`created_by`、`created_at`、`updated_at`、`request_json`。
- 唯一约束：`dataset_name + exchange_code + market_type + symbol + interval + start_time + end_time`，用于避免同名同范围重复 dataset。
- 关键索引：`idx_marketdata_datasets_scope_updated` 支持 dataset 列表按范围查询；`idx_marketdata_datasets_quality_status` 支持质量状态筛选。

GateH-3 新增 `marketdata_dataset_coverage`：

- 范围字段：`coverage_id`、`dataset_id`、`range_start_time`、`range_end_time`。
- 覆盖统计字段：`expected_bars`、`actual_bars`、`missing_bars`、`duplicate_bars`、`invalid_bars`。
- 质量字段：`quality_status`。
- 排障字段：`summary_json`、`created_at`。
- 外键：`dataset_id` 关联 `marketdata_datasets.dataset_id`。
- 关键索引：`idx_marketdata_dataset_coverage_dataset_created` 支持 dataset 详情按刷新时间查询覆盖记录。

GateH-3 变更 `backtest_configs`：

- 新增 `dataset_id`，可空，外键关联 `marketdata_datasets.dataset_id`。
- 新增 `dataset_snapshot_json`，默认 `{}`，保存绑定时 dataset 的 exchange、market、symbol、interval、time range、quality、bar/gap 等快照。
- 新增索引 `idx_backtest_configs_dataset_id`，用于按 dataset 回查绑定配置。

GateH-3 变更 `backtest_runs`：

- 新增 `dataset_snapshot_json`，默认 `{}`。
- run 创建时从 `backtest_configs.dataset_snapshot_json` 固化快照，后续 config 重新绑定不会改写历史 run。

注释要求：`V18` 新增表均包含 `COMMENT ON TABLE`，新增字段均包含 `COMMENT ON COLUMN`。

## 当前边界

- GateH-3 不修改回测引擎核心算法。
- GateH-3 不新增 AI 模块、不新增 AI 自动交易接口。
- GateH-3 不接合约、资金费率、深度、逐笔成交、链上数据、新闻资讯。
- GateH-3 不新增美股/A 股适配。

## GateI-1 当前 Strategy Version 与 Publish 结构

GateI-1 新增 Flyway migration：

- `V19__gate_i1_strategy_versions.sql`

GateI-1 新增 `strategy_versions`：

- 身份字段：`strategy_version_id`，业务主键。
- 策略归属字段：`strategy_code`，外键关联 `strategy_definitions.strategy_code`。
- 版本字段：`version`、`version_name`。
- 状态字段：`status`，允许值 `DRAFT`、`ACTIVE`、`ARCHIVED`。
- 快照字段：`param_snapshot_json`、`config_snapshot_json`、`source_snapshot_json`，均为 JSONB，不保存密钥、token、cookie。
- 校验字段：`checksum`，由策略编码、版本号和快照内容计算，用于发布追溯和变更核对。
- 审计字段：`created_by`、`created_at`、`updated_at`。
- 唯一约束：`strategy_code + version`，用于保证同一策略编码下版本号不重复。
- 关键索引：`idx_strategy_versions_code_version` 支持按策略编码和版本号查询；`idx_strategy_versions_status_updated` 支持按状态和更新时间筛选；`idx_strategy_versions_created_at` 支持按创建时间排序。

GateI-1 变更 `backtest_publish_records`：

- 新增 `strategy_version_id`，可空，外键关联 `strategy_versions.strategy_version_id`。
- 新增 `version_snapshot_json`，默认 `{}`，发布时固化策略版本快照。
- 新增索引 `idx_backtest_publish_records_strategy_version_id`，用于按策略版本回查发布记录。

注释要求：

- `V19` 新增表包含 PostgreSQL `COMMENT ON TABLE`。
- `V19` 所有新增字段包含 PostgreSQL `COMMENT ON COLUMN`。
- `status` 字段注释写明允许值。
- JSONB 字段注释写明用途、结构边界和敏感信息禁入规则。
- 时间字段注释写明创建时间、更新时间语义。

## GateI DB Planning Entry

## GateI-2 当前 Backtest Traceability 与 Evaluation 结构

GateI-2 新增 Flyway migration：

- `V20__gate_i2_backtest_traceability.sql`

GateI-2 变更 `backtest_configs`：

- 新增 `strategy_version_id`，可空，外键关联 `strategy_versions.strategy_version_id`。
- 新增 `strategy_version_snapshot_json`，默认 `{}`，绑定 strategy version 时固化版本快照，不保存 token、cookie、密钥。
- 新增 `param_snapshot_json`，默认 `{}`，绑定 strategy version 时固化参数快照。
- 新增 `config_snapshot_json`，默认 `{}`，第一版从既有 `config_json` 回填，用于回测配置自身快照。
- 复用 GateH-3 已有 `dataset_id` 和 `dataset_snapshot_json`。
- 新增索引 `idx_backtest_configs_strategy_version_id`；继续复用 `idx_backtest_configs_dataset_id`。

GateI-2 变更 `backtest_runs`：

- 新增 `strategy_version_id`，可空，创建 run 时从 `backtest_configs` 固化。
- 新增 `strategy_version_snapshot_json`，默认 `{}`，创建 run 时固化策略版本快照。
- 新增 `param_snapshot_json`，默认 `{}`，创建 run 时固化参数快照。
- 新增 `config_snapshot_json`，默认 `{}`，第一版从既有 `backtest_config_snapshot` 回填。
- 复用 GateH-3 已有 `dataset_snapshot_json`，创建 run 时从配置固化 dataset snapshot。
- 新增索引 `idx_backtest_runs_strategy_version_id`；继续复用 `idx_backtest_runs_backtest_config_id`。

GateI-2 变更 `backtest_eval_reports`：

- 新增 `total_return`，第一版与 `total_return_rate` 同口径。
- 新增 `annualized_return`，按评估权益快照首尾时间差折算；时间差不可用时为空。
- 新增 `profit_loss_ratio`，口径为闭合盈利交易总收益 / 闭合亏损交易绝对值；亏损为 0 时返回 0。
- 新增 `metrics_json`，保存 total return、annualized return、max drawdown、win rate、profit/loss ratio、trade count、Sharpe 等展示指标。
- 新增索引 `idx_backtest_eval_reports_backtest_run_id`，用于按 run 回查评估报告。

注释要求：

- `V20` 未新增表。
- `V20` 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- JSONB 快照字段注释均写明用途和敏感信息禁入规则。
- 评估指标字段注释写明核心口径、边界条件和空值语义。

GateI-2 不修改历史 migration，不新增无注释表，不新增无注释字段，不修改策略核心算法、回测核心算法或交易核心状态机。

## GateI DB Planning Entry

GateI DB 规划入口为 [GATEI_DB_PLAN.md](../gates/gate-i/GATEI_DB_PLAN.md)。GateI-1 已落地策略版本与发布绑定最小结构；GateI-2 已落地回测追溯与评估指标增强；GateI-3/4 尚未开始。

GateI 后续规划重点：

- `strategy_versions`。
- `strategy_publish_versions` 或 `publish_records` 增强。
- `backtest_configs` 增强。
- `backtest_runs` 结果追溯增强。
- `backtest_eval_reports` 指标增强。
- `paper_trading_runs`。
- `paper_trading_orders`。
- `paper_trading_trades`。
- `risk_check_results`。
- `equity_curve_snapshots`。
- `position_curve_snapshots`。
- `trade_replay_records`。
- `emergency_stop_events`。

GateI 后续如果新增 migration，所有新增表必须包含 PostgreSQL `COMMENT ON TABLE`，所有新增字段必须包含 `COMMENT ON COLUMN`。JSONB 快照字段必须说明用途、结构边界和敏感信息禁入规则。

GateI-1 / GateI-2 不修改策略核心算法、不修改回测核心算法、不进入 Paper Trading、不接入 AI。

## GateI-3 Paper Trading 结构

GateI-3 新增 Flyway migration：

- `V21__gate_i3_paper_trading.sql`

GateI-3 新增 `paper_trading_runs`：

- 身份字段：`paper_run_id`，业务主键。
- 发布引用：`publish_id`，外键关联 `backtest_publish_records.publish_record_id`。
- 策略版本引用：`strategy_version_id`，外键关联 `strategy_versions.strategy_version_id`。
- 状态字段：`status`，允许值 `CREATED`、`RUNNING`、`STOPPED`、`FAILED`。
- 运行维度：`trade_env`（SIM/LIVE）、`exchange_code`、`market_type`、`symbol`、`interval_code`。
- 时间字段：`started_at`、`stopped_at`、`created_at`、`updated_at`。
- 快照字段：`publish_snapshot_json`、`strategy_version_snapshot_json`、`dataset_snapshot_json`、`param_snapshot_json`、`config_snapshot_json`。
- 审计字段：`created_by`。
- 索引：`idx_paper_runs_publish_id`、`idx_paper_runs_strategy_version_id`、`idx_paper_runs_status`。

GateI-3 新增 `paper_trading_orders`：

- 身份字段：`paper_order_id`，业务主键。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 订单字段：`symbol`、`side`（BUY/SELL）、`order_type`、`quantity`、`price`。
- 状态字段：`status`，允许值 `CREATED`、`FILLED`、`CANCELED`、`REJECTED`。
- 信号字段：`reason`、`raw_signal_json`。
- 时间字段：`created_at`、`updated_at`。
- 索引：`idx_paper_orders_run_id`、`idx_paper_orders_run_symbol_status`。

GateI-3 新增 `paper_trading_trades`：

- 身份字段：`paper_trade_id`，业务主键。
- 归属字段：`paper_order_id`、`paper_run_id`，分别外键关联。
- 成交字段：`symbol`、`side`、`quantity`、`price`、`fee`、`traded_at`。
- 时间字段：`created_at`。
- 索引：`idx_paper_trades_run_id`、`idx_paper_trades_order_id`、`idx_paper_trades_symbol_time`。

GateI-3 新增 `paper_trading_positions`：

- 身份字段：`paper_position_id`，业务主键。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 持仓字段：`symbol`、`quantity`、`avg_price`、`unrealized_pnl`、`realized_pnl`。
- 唯一约束：`paper_run_id + symbol`。
- 时间字段：`updated_at`、`created_at`。
- 索引：`idx_paper_positions_run_id`。

注释要求：

- `V21` 所有新增表均包含 PostgreSQL `COMMENT ON TABLE`。
- `V21` 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态字段注释写明允许值。
- JSONB 快照字段注释写明用途和敏感信息禁入规则。

GateI-3 不修改历史 migration，不新增无注释表，不新增无注释字段，不修改策略核心算法、回测核心算法或交易核心状态机。

## GateI-4 Paper Trading Monitor 结构

GateI-4 新增 Flyway migration：

- `V22__gate_i4_paper_trading_monitor.sql`

GateI-4 新增 `paper_risk_check_results`：

- 身份字段：`risk_result_id`，业务主键。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 检查字段：`check_type`、`status`（PASSED/REJECTED/WARNING）、`severity`（LOW/MEDIUM/HIGH/CRITICAL）、`message`。
- 快照字段：`input_snapshot_json`、`result_snapshot_json`。
- 时间字段：`created_at`。
- 索引：`idx_risk_results_run_id_time`。

GateI-4 新增 `equity_curve_snapshots`：

- 身份字段：`snapshot_id`，业务主键。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 曲线字段：`total_equity`、`cash_balance`、`position_value`、`unrealized_pnl`、`realized_pnl`、`drawdown`、`drawdown_pct`。
- 时间字段：`snapshot_time`、`created_at`。
- 索引：`idx_equity_curve_run_id_time`。

GateI-4 新增 `position_curve_snapshots`：

- 身份字段：`snapshot_id`，业务主键。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 持仓字段：`symbol`、`quantity`、`avg_price`、`market_price`、`market_value`、`unrealized_pnl`、`weight_pct`。
- 时间字段：`snapshot_time`、`created_at`。
- 索引：`idx_position_curve_run_id_time`。

GateI-4 新增 `trade_replay_records`：

- 身份字段：`replay_id`，业务主键。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 事件字段：`event_type`、`event_time`、`description`。
- 快照字段：`decision_snapshot_json`、`risk_snapshot_json`、`market_snapshot_json`。
- 时间字段：`created_at`。
- 索引：`idx_replay_run_id_time`。

GateI-4 新增 `emergency_stop_events`：

- 身份字段：`emergency_stop_id`，业务主键。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 触发字段：`trigger_type`（MANUAL/RISK_LIMIT/SYSTEM_ERROR）、`status`（TRIGGERED/APPLIED/FAILED/RESOLVED）、`reason`、`triggered_by`。
- 时间字段：`triggered_at`、`resolved_at`、`created_at`。
- 快照字段：`request_json`、`result_json`。
- 索引：`idx_emergency_stop_run_id_time`。

注释要求：

- `V22` 所有新增表均包含 PostgreSQL `COMMENT ON TABLE`。
- `V22` 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态字段注释写明允许值。
- JSONB 快照字段注释写明用途和敏感信息禁入规则。

GateI-4 不修改历史 migration，不新增无注释表，不新增无注释字段，不修改策略核心算法、回测核心算法或交易核心状态机。

## GateJ DB Planning Entry

GateJ DB 历史规划入口为 [GATEJ_DB_PLAN.md](../gates/gate-j/GATEJ_DB_PLAN.md)。该链接仅用于历史追溯，不决定当前 Gate。

GateJ 规划新增 7 张表：

- `paper_run_schedules`：Paper run 调度计划。
- `paper_run_schedule_fires`：调度触发记录。
- `paper_run_heartbeats`：Paper run 心跳记录。
- `paper_run_daily_reports`：Paper run 日报。
- `paper_run_alerts`：Paper run 告警事件。
- `paper_run_recovery_events`：恢复和重试事件。
- `paper_run_stability_checks`：连续运行验收结果。

GateJ 后续如果新增 migration，所有新增表必须包含 PostgreSQL `COMMENT ON TABLE`，所有新增字段必须包含 `COMMENT ON COLUMN`。JSONB 快照字段必须说明用途、结构边界和敏感信息禁入规则。状态字段必须有 CHECK 约束。

GateJ 不修改历史 migration，不接 AI。

## GateJ-1 Paper Run Schedule 结构

GateJ-1 新增 Flyway migration：

- `V23__gate_j1_paper_run_schedules.sql`

GateJ-1 新增 `paper_run_schedules`：

- 身份字段：`schedule_id`，业务主键，格式 `sch-<uuid>`。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 调度字段：`schedule_name`、`cron_expr`、`timezone`（默认 UTC）。
- 状态字段：`status`，允许值 `ENABLED`、`DISABLED`、`PAUSED`，CHECK 约束。
- 时间字段：`next_fire_time`、`last_fire_time`、`created_at`、`updated_at`。
- 审计字段：`created_by`、`request_json`。
- 索引：`idx_paper_run_schedules_run_id`、`idx_paper_run_schedules_status`、`idx_paper_run_schedules_next_fire`（partial：status='ENABLED'）。

GateJ-1 新增 `paper_run_schedule_fires`：

- 身份字段：`fire_id`，业务主键，格式 `fir-<uuid>`。
- 归属字段：`schedule_id` 外键关联 `paper_run_schedules`，`paper_run_id` 外键关联 `paper_trading_runs`。
- 状态字段：`status`，允许值 `RUNNING`、`SUCCEEDED`、`FAILED`、`SKIPPED`，CHECK 约束。
- 时间字段：`fired_at`、`finished_at`、`duration_ms`、`created_at`。
- 排障字段：`result_json`、`error_message`。
- 索引：`idx_schedule_fires_schedule_id`（按 fired_at DESC）、`idx_schedule_fires_run_id`、`idx_schedule_fires_fired_at`。

GateJ-1 新增 `paper_run_heartbeats`：

- 身份字段：`heartbeat_id`，业务主键，格式 `hbt-<uuid>`。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 状态字段：`status`，允许值 `OK`、`LAGGING`、`STOPPED`、`UNKNOWN`，CHECK 约束。
- 时间字段：`heartbeat_time`、`last_event_time`、`last_order_time`、`last_trade_time`、`created_at`。
- 指标字段：`lag_seconds`、`summary_json`。
- 索引：`idx_heartbeats_run_id_time`（按 heartbeat_time DESC）。

注释要求：

- `V23` 所有新增表均包含 PostgreSQL `COMMENT ON TABLE`。
- `V23` 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态字段注释写明允许值。
- JSONB 快照字段注释写明用途和敏感信息禁入规则。

GateJ-1 不修改历史 migration，不新增无注释表，不新增无注释字段，不修改策略核心算法、回测核心算法或交易核心状态机。

## GateJ-2 新增表（Paper Trading 监控、日报与告警）

GateJ-2 新增 Flyway migration：

- `V24__gate_j2_paper_run_daily_reports_alerts.sql`

GateJ-2 新增 `paper_run_daily_reports`：

- 身份字段：`report_id`，业务主键，格式 `rpt-<uuid>`。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 日期字段：`report_date`，UTC 日期。
- 状态字段：`status`，允许值 `GENERATED`、`PARTIAL`、`FAILED`，CHECK 约束。
- 资金指标：`total_equity`、`daily_pnl`、`daily_return`、`max_drawdown`（可空，缺数据时为 null）。
- 计数指标：`order_count`、`trade_count`、`alert_count`、`risk_reject_count`，默认 0。
- 数据字段：`report_json`（JSONB，明细数据），注释写明不保存密钥/token/cookie。
- 时间字段：`generated_at`、`created_at`。
- 唯一约束：`uq_daily_reports_run_date (paper_run_id, report_date)`，保证按日幂等。
- 索引：`idx_daily_reports_run_id_date`（按 report_date DESC）、`idx_daily_reports_status`。

GateJ-2 新增 `paper_run_alerts`：

- 身份字段：`alert_id`，业务主键，格式 `alt-<uuid>`。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 分类字段：`alert_type`（HEARTBEAT_LAG / SCHEDULE_FIRE_FAILED / RISK_WARNING / EMERGENCY_STOP / SYSTEM_NOTICE 等业务类型）。
- 严重程度：`severity`，允许值 `LOW`、`MEDIUM`、`HIGH`、`CRITICAL`，CHECK 约束。
- 状态字段：`status`，允许值 `OPEN`、`ACKED`、`RESOLVED`，CHECK 约束。
- 内容字段：`title`、`message`、`source`（SCHEDULE / HEARTBEAT / RISK / MONITOR / MANUAL）。
- 快照字段：`event_snapshot_json`（JSONB），注释写明不保存密钥/token/cookie。
- 审计字段：`acknowledged_by`、`acknowledged_at`、`resolved_at`。
- 时间字段：`created_at`、`updated_at`。
- 索引：`idx_alerts_run_id_created`（按 created_at DESC）、`idx_alerts_status`、`idx_alerts_severity`。

注释要求：

- `V24` 所有新增表均包含 PostgreSQL `COMMENT ON TABLE`。
- `V24` 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态、严重程度字段注释写明允许值。
- JSONB 快照字段注释写明用途和敏感信息禁入规则。

GateJ-2 不修改历史 migration，不新增无注释表，不新增无注释字段，不修改策略核心算法、回测核心算法或交易核心状态机。

## GateJ-3 新增表（Paper Trading 恢复事件与稳定性验收）

GateJ-3 新增 Flyway migration：

- `V25__gate_j3_paper_run_recovery_stability.sql`

GateJ-3 新增 `paper_run_recovery_events`：

- 身份字段：`recovery_event_id`，业务主键，格式 `rec-<uuid>`。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 类型字段：`recovery_type`，CHECK 约束允许值 `MANUAL_RECOVER`、`RETRY_FAILED_STEP`、`HEARTBEAT_LAG_RECOVER`、`SCHEDULE_FIRE_RECOVER`。
- 状态字段：`status`，CHECK 约束允许值 `STARTED`、`SUCCEEDED`、`FAILED`、`SKIPPED`。
- 内容字段：`reason`（TEXT）、`request_json`（JSONB，请求快照）、`result_json`（JSONB，结果快照）。
- 时间字段：`started_at`（开始时间）、`finished_at`（完成时间，可空）、`created_at`。
- 索引：`idx_recovery_events_run_id_created`（按 created_at DESC）、`idx_recovery_events_status`、`idx_recovery_events_type`、`idx_recovery_events_created_at`。
- JSONB 字段注释明确不保存密钥/token/cookie。

GateJ-3 新增 `paper_run_stability_checks`：

- 身份字段：`stability_check_id`，业务主键，格式 `stb-<uuid>`。
- 归属字段：`paper_run_id`，外键关联 `paper_trading_runs.paper_run_id`。
- 窗口字段：`check_window_start`、`check_window_end`，CHECK 约束 `check_window_end > check_window_start`。
- 状态字段：`status`，CHECK 约束允许值 `PASSED`、`FAILED`、`PARTIAL`。
- 指标字段：`uptime_ratio`（NUMERIC(5,4)，CHECK 0~1）、`heartbeat_count`、`alert_count`、`failed_fire_count`、`recovery_count`、`report_count`。
- 摘要字段：`summary_json`（JSONB，明细计数 / 判定原因），注释写明不保存密钥/token/cookie。
- 时间字段：`created_at`。
- 唯一约束：`uq_stability_checks_run_window (paper_run_id, check_window_start, check_window_end)`，保证同窗口幂等。
- 索引：`idx_stability_checks_run_id_created`（按 created_at DESC）、`idx_stability_checks_status`、`idx_stability_checks_window_start`、`idx_stability_checks_window_end`。

注释要求：

- `V25` 所有新增表均包含 PostgreSQL `COMMENT ON TABLE`。
- `V25` 所有新增字段均包含 PostgreSQL `COMMENT ON COLUMN`。
- 状态、类型字段注释写明允许值。
- `uptime_ratio` 注释写明取值范围（0~1）和第一版口径。
- `paper_run_stability_checks` 表注释明确"第一版最小口径，非 GateJ-FREEZE 最终验收"。
- JSONB 字段注释写明用途和敏感信息禁入规则。

GateJ-3 不修改历史 migration，不新增无注释表，不新增无注释字段，不修改策略核心算法、回测核心算法或交易核心状态机。

## GateR-2 Shadow Run 本地事实模型

GateR-2 新增 Flyway migration：

- `V32__gate_r_shadow_run_fact_model.sql`

本批实现 Shadow Run local fact model 的最小后端持久化基础，状态为 `IMPLEMENTED / PENDING REVIEW`（已实现 / 待复核）。该状态只表示本地事实表、domain/state machine、repository 和测试已落地等待 review，不表示 GateR frozen，不表示 Shadow runner 已启动，不表示 HTTP API、前端页面、AI/DH runtime、LIVE 或真实交易能力已启用。

GateR-2 新增 `shadow_runs`：

- 身份字段：`id`，`UUID` 主键。
- 追溯字段：`strategy_version_id`、`dataset_id`、`evaluation_id`、`publish_id`、`paper_run_id`，分别引用现有 strategy version、marketdata dataset、evaluation report、publish record 和 paper trading run；删除策略保持 `NO ACTION`，不级联删除审计事实。
- 状态字段：`status`，CHECK 约束允许 `CREATED / PRECHECKING / READY / RUNNING / STOP_REQUESTED / STOPPED / COMPLETED / BLOCKED / FAILED / CANCELLED`。
- 时间窗口：`window_start`、`window_end`，两者同时存在时要求 `window_end >= window_start`。
- 无副作用边界：`side_effect_policy`、`no_order_submission`、`no_credential_access`、`no_private_endpoint`、`no_ledger_mutation`、`no_account_mutation`、`no_external_private_io`，所有 no-* flag 默认且必须为 `TRUE`。
- 授权边界：`authorization_boundary`，CHECK 约束允许 `DIAGNOSTIC_ONLY / REVIEW_ONLY / REPLAY_ONLY`；不表达交易授权。
- 追踪与幂等：`request_id`、`idempotency_key`、`trace_id`、`version`；`idempotency_key` 唯一，`version` 用于 repository 乐观锁。
- 复盘信息：`blockers`、`warnings`、`next_steps`，均为 JSONB，默认空数组，注释明确不得保存 credential material、private payload、真实账户余额、真实订单状态或真实交易授权。
- 索引：`idx_shadow_runs_idempotency_key`（unique）、`idx_shadow_runs_status_created_at`、`idx_shadow_runs_strategy_dataset`、`idx_shadow_runs_paper_run_id`。

GateR-2 新增 `shadow_run_events`：

- 身份字段：`id`，`UUID` 主键。
- 归属字段：`shadow_run_id`，外键关联 `shadow_runs(id)`。
- 事件字段：`event_type`，CHECK 约束允许 `CREATED / PRECHECK_STARTED / PRECHECK_PASSED / PRECHECK_BLOCKED / RUN_STARTED / STOP_REQUESTED / STOPPED / COMPLETED / FAILED / CANCELLED / ILLEGAL_STATE_TRANSITION_ATTEMPT / SNAPSHOT_CAPTURED / CONSISTENCY_REPORT_GENERATED`。
- 状态流转字段：`from_status`、`to_status`，分别复用 Shadow Run status 枚举 CHECK，可为空。
- 排障字段：`reason_code`、`message`、`metadata`；`metadata` 为 JSONB，默认空对象，注释明确禁止保存 credential、raw request、raw response、private endpoint payload、真实订单 ID 或真实账户数据。
- 追踪字段：`request_id`、`trace_id`、`created_at`。
- 索引：`idx_shadow_run_events_run_created_at`。

GateR-2 新增 `shadow_run_snapshots`：

- 身份字段：`id`，`UUID` 主键。
- 归属字段：`shadow_run_id`，外键关联 `shadow_runs(id)`。
- 类型字段：`snapshot_type`，CHECK 约束允许 `INPUT_MARKETDATA / STRATEGY_DECISION / RISK_PREFLIGHT / ORDER_INTENT_PREVIEW`。
- 顺序与来源：`sequence_no`、`source`、`schema_version`、`checksum`。
- 内容字段：`payload`，JSONB；只允许脱敏输入、决策、风控预检和 order intent preview，不允许 credential、private request/response、真实账户余额、真实订单状态或真实交易授权。
- 时间与追踪：`captured_at`、`trace_id`、`created_at`。
- 唯一约束：`uq_shadow_snapshots_run_type_seq (shadow_run_id, snapshot_type, sequence_no)`。
- 索引：`idx_shadow_run_snapshots_run_type_sequence`。

GateR-2 新增 `shadow_consistency_reports`：

- 身份字段：`id`，`UUID` 主键。
- 归属字段：`shadow_run_id`，外键关联 `shadow_runs(id)`；`paper_run_id` 可空引用 `paper_trading_runs(paper_run_id)`。
- 对比状态：`comparison_status`，CHECK 约束允许 `CONSISTENT / DIVERGED / NOT_COMPARABLE / PARTIAL / FAILED`。
- 复盘字段：`metric_delta`、`divergence_reasons`、`limitations`，均为 JSONB，只表达脱敏差异分析，不表达 approval、authorization 或 live-ready。
- 时间与追踪：`generated_at`、`trace_id`、`created_at`。
- 索引：`idx_shadow_consistency_reports_run_generated`、`idx_shadow_consistency_reports_paper_generated`。

GateR-2 注释与边界：

- `V32` 所有新增表均包含 PostgreSQL `COMMENT ON TABLE`。
- `V32` 关键字段均包含 PostgreSQL `COMMENT ON COLUMN`，中文说明 Shadow Run 是本地事实，不保存 credential material，不代表 LIVE ready，不产生真实交易副作用。
- 本批不新增 HTTP Controller，不新增 API endpoint，不新增前端页面，不启动 Shadow runner，不调用真实交易所，不读取 `.env` 或 credential 文件，不修改真实账户、资金、订单或 ledger 状态。

## GateV-1 Durable Validation Review 本地事实模型

GateV-1 新增 Flyway migration：

- `V33__gate_v_validation_review_fact_model.sql`

当前状态为 `IMPLEMENTED / REVIEW ACCEPTED`（已实现 / 复核已接受）。该状态只表示 GateV-1 schema、domain/state machine、repository 与真实 PostgreSQL 专项复核通过，不表示 GateV accepted/frozen，不表示 review API、scheduler、frontend 或自动 materialization 已实现。

`validation_review_cases`：

- `id` 为 UUID 主键；`tenant_key` 当前由服务端固定为 `NQ_LOCAL`。
- `owner_id`、`created_by` 与各 lifecycle actor 使用现有 `users.id BIGINT`，外键删除策略为 `RESTRICT`。
- `state` 仅允许 `OPEN / ACKNOWLEDGED / ESCALATED / RESOLVED / CLOSED`；`CLOSED` 为终态。
- `severity` 仅允许 `INFO / WARNING / HIGH / CRITICAL`，只表达复核优先级。
- `version >= 0` 用于 optimistic locking；每个 accepted transition 递增 1。
- `evidence_anchor` 为 JSONB object，只保存脱敏本地证据锚点；不保存 credential、账户余额、真实订单或 private payload。
- Actor/time pair、state/time 与时间顺序均有 CHECK 约束。
- 索引覆盖 tenant/owner/state、tenant/state/severity、两个 bounded list 的 `updated_at/id` 稳定排序和 evidence type/source；未增加全局 case 去重约束。

`validation_review_events`：

- 记录 `ACKNOWLEDGED / ESCALATED / RESOLVED / CLOSED` accepted transition，按 append-only 使用。
- `(review_case_id, tenant_key)` 复合外键引用 case，删除策略为 `RESTRICT`，避免跨 tenant event。
- `(review_case_id, idempotency_key)` 唯一；同 key+同 request hash 返回首次 event，同 key+不同 hash fail-closed。
- DB CHECK 与 domain state machine 使用同一固定合法流转图，直接 SQL 也不能写入跳转或自循环 accepted event。
- `case_version` 保存 transition 后版本；`request_id`、`trace_id` 和脱敏 metadata 用于本地审计链。
- 索引覆盖 case/event 顺序、tenant/actor/time 与 trace ID。

GateV-1 所有新增表和关键字段均有中文 COMMENT，明确本地人工复核不代表交易授权、不表示 LIVE ready、不修改 strategy、Paper、Shadow、risk、account、order 或 ledger，也不保存 credential material。

## GateW-3 OKX Spot Venue-rule Current Facts

GateW-3 新增 forward-only Flyway migration：

- `V34__gate_w3_venue_rule_facts.sql`

当前状态为 `REVIEW ACCEPTED / READY TO COMMIT`（复核已接受 / 可进入提交前复核）。V34 只扩展既有 `instrument_catalog`，不建立第二张 venue-rule 表，不修改历史 migration，不保存 raw provider payload、header、credential、signature 或 private account data。该状态不是 committed 或 CI green。

- `tick_size`、`step_size`、`min_quantity` 扩宽为 `NUMERIC(38,18)`。
- 新增 nullable numeric：`max_limit_quantity`、`max_market_size`、`max_limit_notional_usd`、`max_market_notional_usd`，均要求 null 或大于 0。
- `max_market_size_unit` 与 `max_market_size` 必须同时为空，或同时存在且 unit 固定为 `USDT`。
- 新增 nullable provenance/freshness facts：`source_schema_version`、`observed_at`、`next_rule_effective_at`、`rule_checksum`。
- `rule_checksum` 必须为 null 或 64 位 lowercase hex；`observed_at <= synced_at`；`next_rule_effective_at` 存在时必须有 `observed_at` 且严格晚于它。
- `synced_at` 继续只表示数据库写入时间，不等于 provider observation time。
- 旧行不回填 0、当前时间、schema version 或 checksum；缺失 GateW facts 时由 freshness contract 返回 unavailable/unknown/blocked。
- 保留既有两个 unique constraints 与 status lookup index，不新增重复索引。

disposable PostgreSQL 16.14 验证：fresh V1→V34 与 V33→V34 均到 version 34；V33 legacy row 新列保持 null，precision、约束、中文 comments、既有 key/index 与 repository lifecycle 通过。单行、73,728-byte 样本在 V34 后 `pg_relation_filepath` 变化，表明本样本发生 table rewrite；独立 lock observation 确认 `ALTER TABLE ... TYPE` 请求 `AccessExclusiveLock`。该风险按 P2 保留，部署前必须按目标表规模与维护窗口复核，不能把 disposable 结果外推成生产无锁结论。

## GateX-2 Shadow Run Strategy Release Provenance

GateX-2 新增 forward-only Flyway migration：

- `V36__gate_x2_shadow_run_provenance.sql`

当前状态为 `REVIEW ACCEPTED / READY TO COMMIT`（复核已接受 / 可进入提交前复核）。该状态只表示 provenance schema、domain/JDBC persistence 与 PostgreSQL 回归已通过独立迁移复核，不表示 committed、CI green、Release-to-Shadow admission、LIVE ready 或交易授权。

- `shadow_runs.artifact_digest` 为 `VARCHAR(64) NULL`，保存 Shadow Run 创建时已经验证的 Strategy Release artifact-set SHA-256。
- `chk_shadow_runs_artifact_digest_sha256` 允许 `NULL` 或严格 64 位 lowercase hex；空字符串、大写、63/65 位和非十六进制值均拒绝。
- `chk_shadow_runs_artifact_requires_publish` 要求 digest 非空时 `publish_id` 必须非空；允许历史无绑定与仅 `publish_id` 绑定。
- 两个 CHECK 先以 `NOT VALID` 创建，再通过 `VALIDATE CONSTRAINT` 扫描历史行；迁移结束时均为已验证约束。`VALIDATE` 自身使用 `ShareUpdateExclusiveLock`，但 PostgreSQL 17.7 实测表明 Flyway 默认单事务会同时保留前序 `ALTER TABLE` 的 `AccessExclusiveLock` 到提交，因此不能把验证扫描描述为整体弱锁。部署前必须检查目标表行数/大小、长事务、锁队列和写入速率，并配置受控 `lock_timeout` / `statement_timeout`；超时值由部署环境观测决定，不在 migration 中硬编码。
- 不为 `(publish_id, artifact_digest)` 增加 unique constraint；同一已验证 release 可以产生多个 Shadow Run。
- 不执行 `UPDATE`，不推测、不计算、不回填历史 digest。迁移前的 legacy row 在升级后保持 `artifact_digest=NULL`。
- domain 只由持久化事实派生 `LEGACY_UNBOUND / LEGACY_PUBLISH_ONLY / RELEASE_BOUND`；不新增独立 binding-mode 列，避免冗余状态漂移。
- repository 只在 create 时写入 `publish_id` 与 `artifact_digest`；同一 `idempotency_key` 仅在两个 provenance anchor 均一致时返回既有行，冲突使用 `SHADOW_RUN_IDEMPOTENCY_PROVENANCE_CONFLICT` fail-closed；lifecycle update SQL 不触碰两个 provenance 字段，保证状态流转不能改写已绑定 provenance。
- 字段和约束均有中文 COMMENT，明确 digest 不表示 admission、交易批准或 LIVE ready。

disposable PostgreSQL 17.7 验证覆盖 fresh `V1→V36`、upgrade `V35→V36`、legacy 无绑定、legacy publish-only、release-bound create/read、幂等冲突拒绝、非法值拒绝、同一 release 多 run与状态生命周期 provenance immutability；upgrade 中 V36 执行约 `0.012s`，仅代表小样本。独立 10,000 行 lock probe 在同一事务完成 add-column/add-check/validate 后同时观察到 `AccessExclusiveLock=true` 与 `ShareUpdateExclusiveLock=true`，容器随后删除。全量 Maven 还按既有 local profile 将本机开发库 `nexus_quant.public` 从 V35 正常迁到 V36；未访问生产数据库。

## GateX-4B Persistent Artifact Locator

GateX-4B 新增 forward-only Flyway migration：

- `V37__gate_x4b_persistent_artifact_locator.sql`

当前状态为 `IMPLEMENTED / PENDING REVIEW`（已实现 / 待独立复核）。该状态只表示 locator schema、domain/JDBC persistence、内部 publish typed input 与真实 disposable PostgreSQL 回归已完成；不表示 committed、CI green、artifact producer、trusted-root resolver、GateX-4 API/UI、Shadow Run creation、LIVE ready 或交易授权。

- `backtest_publish_records.artifact_storage_key` 与 `manifest_storage_key` 均为 `VARCHAR(128) NULL`。二者必须同时为 `NULL` 或同时非 `NULL`；历史 `NULL/NULL` 派生为 `LEGACY_ARTIFACT_UNBOUND`。
- 非空 key 必须匹配 `^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$`，并额外禁止任意 `..`；因此 slash、backslash、colon、空串、absolute path、URI 和 129 字符值均被拒绝。key 是 server-owned opaque identifier，不是 filesystem path、trusted root、digest 或客户端输入。
- V37 不执行 `UPDATE`，不 backfill，也不从 publish ID、digest、cwd、临时目录或历史目录布局猜测 locator。
- 三个 CHECK 先以 `NOT VALID` 创建再执行 `VALIDATE CONSTRAINT`；字段与约束均有中文 COMMENT，说明 legacy、敏感边界与允许值。
- 数据库 trigger 保护 SQL 级不可变边界：只允许 `FAILED + NULL/NULL` row 在转为 `SUCCEEDED` 时完成一次有效 pair 绑定；已绑定 pair 的 rebind、清空均以 SQLSTATE `23514` fail-closed。repository 不暴露普通 locator update/rebind API；同 pair 可幂等重放，不同 pair 冲突拒绝且不覆盖旧值。
- 未增加 `UNIQUE artifact_storage_key` 或 `UNIQUE manifest_storage_key`。当前 storage/provider contract 不能证明“一个 key 永远全局唯一属于一个 publish release”，因此不引入缺乏正式业务 invariant 的索引扫描和写约束；PostgreSQL 回归明确验证跨 release 重复 pair 当前允许。
- publish HTTP contract 未变；普通 publish 继续写入 unbound pair。内部 `publishWithArtifactLocator(...)` 只接受已验证 typed locator，供未来受控 artifact pipeline 使用；当前状态为 `PERSISTENCE_READY / PRODUCER_NOT_YET_CONNECTED`，不得伪造 key。

disposable PostgreSQL 17.7 验证覆盖 fresh `V1→V37`、upgrade `V36→V37`、`Flyway.validate`、历史 row no-backfill/可读、合法 pair 写读、partial pair 与非法格式拒绝、JDBC 幂等/冲突、trigger 首次绑定与不可变、无 UNIQUE 下 duplicate behavior。fresh 与 upgrade 小样本均为 2 rows、relation 8,192 bytes、indexes 65,536 bytes、long transactions 0、lock waits 0；这些数据只描述 localhost disposable database，不能外推生产安全。生产部署仍保留 P2：必须在受控窗口只读核对目标表行数/大小、长事务、锁等待和写入速率，并为 `ADD COLUMN` / `ADD CHECK` / `VALIDATE` / trigger DDL 配置停止条件；本轮未实测生产表规模或锁窗口。

## GateY-2 LIVE Session Control-plane Fact Model

`V39__gate_y2_live_session_fact_model.sql` 已创建六张新表；该 migration 未修改 V1～V38、无 historical backfill、无现有表 rewrite，也不启用 LIVE：

- `risk_limit_sets`：不可变、版本化的 LIVE session 风险规则定义，金额使用 `NUMERIC(38,8)`，canonical digest schema 固定为 `risk-limit-set.v1`；不是运行期 `risk_events` 的替代品。
- `live_sessions`：可变 control-plane aggregate，绑定 owner、OKX LIVE account、release admission digest/revision、exact credential reference、risk set digest、scope hash、execution window、optimistic version 与 `next_event_sequence`；同一 `exchange_account_id + venue` 最多一个 non-terminal session。
- `live_session_events`：按 `(session_id, sequence_no)` 唯一的 append-only 有序事件；event sequence 由锁定 session row 后读取/递增 counter 生成，不使用 `MAX+1`。
- `operator_approvals`：append-only 人工决策事实，绑定 exact scope/release/risk digest、经实时 `users/user_roles/roles` RBAC 校验后的 approver role snapshot 与 expiry；审批不等于交易所权限、kill switch 释放或 LIVE 授权。V39 不 seed `LIVE_APPROVER`，缺少/撤销/禁用授权均由应用 fail-closed。
- `execution_intents`、`execution_receipts`：仅落 schema、状态/check/unique/index/immutability contract，供 GateY-3 后续实现；GateY-2 没有 worker、dispatch、provider 或 HTTP 路径。

PostgreSQL trigger 直接拒绝 `risk_limit_sets` 的 UPDATE/DELETE，以及 `live_session_events`、`operator_approvals`、`execution_receipts` 的 UPDATE/DELETE；`live_sessions` 与 `execution_intents` 只允许合同内的受控 version/state 更新。所有新增表和字段都有中文 `COMMENT`，敏感字段注释明确禁止 credential material、raw request/response、headers 和签名。migration 保留 `SET LOCAL lock_timeout='5s'` 与 `SET LOCAL statement_timeout='60s'`。

Disposable PostgreSQL 17.7 已验证 fresh `V1→V38` fixture 后 `V38→V39`、`Flyway.validate`、六表/注释/trigger、包含 `roles/user_roles` 的历史 fingerprint 不变、约束/非法精度/非法状态/非法窗口、单活 partial unique、append-only/immutable direct SQL rejection、引用一致性 fail-closed、creator identity 绑定、无 `LIVE_APPROVER` 拒绝、授予后并发审批与 8-way event sequence。该本地 PASS 不授权 production migration；`PRODUCTION_LOCK_WINDOW_NOT_MEASURED` 继续保留。

## GateY-6D Pilot Scope 与 Prerequisite Fact Model

`V40__gate_y6d_pilot_scope_prerequisite_fact_model.sql` 已按冻结 work order 实现；当前为 `IMPLEMENTED / PENDING INDEPENDENT MIGRATION SECURITY REVIEW`（已实现 / 等待独立迁移安全复核）。该状态不改变 GateY-6D machine authority，不表示真实 pilot、独立审批、FIRST_REAL_ORDER、micro-live 或 LIVE 已授权。

- `pilot_scope_bindings`：每个 `live_sessions.session_id` 最多一个 immutable `pilot-scope.v1` binding；保存 exact instrument/fee/balance/clock source contract、freshness ceiling、endpoint policy、provider artifact、worker release 与 lowercase SHA-256 `pilot_scope_hash`。数据库重建 canonical payload/hash，拒绝 supplied mismatch、late binding、UPDATE 与 DELETE。
- `pilot_prerequisite_observations`：append-only 四类 typed facts，允许值为 `INSTRUMENT_METADATA / FEE_SCHEDULE / BALANCE_SNAPSHOT / CLOCK_SYNC`；通过 source observation identity 与 observation-set/type 两组 unique constraint 实现幂等和完整集合约束。
- `pilot_instrument_observation_items`：保存 instrument observation 的 exact symbol、trading status、tick/lot/minimum size/value；composite FK 只允许挂到 `INSTRUMENT_METADATA`，deferred trigger 校验 exact symbol set 与 digest。
- `operator_approvals` 仅新增 `scope_schema_version` 与 nullable `pilot_scope_id`。历史行在 migration 内真实标记为 `approval-scope.v1` 后立即移除 default，`pilot_scope_id` 保持 `NULL`；pilot approval 必须通过 `(session_id, pilot_scope_id, scope_hash)` composite FK exact 绑定 scope，legacy approval 不能用于 pilot。
- 三张新表、全部新字段均有中文 COMMENT；digest、状态、identity/version、maximum-age/skew、variant 与 amount 均有 CHECK/FK/unique/index；scope/observation/item 均有 immutable/append-only guard。
- complete-set validation 使用 deferred constraint trigger，在 commit 前要求四类 observation 与 instrument exact set 全部完整；同 identity+同 payload 幂等，同 identity+不同 payload conflict，并发由数据库 unique/locking 裁决。
- migration 不执行历史 approval 批量 `UPDATE`，不创建历史 pilot scope/observation/item，不制造 digest/source/observedAt；V1～V39 不变。

Disposable PostgreSQL 17.7 已验证 V39→V40、V1→V40 full replay、Flyway validate、Java/PostgreSQL canonical parity、no-fake-backfill、约束/trigger、幂等/并发、legacy/new approval compatibility 与 timeout transaction rollback。小 fixture 的 V39→V40 约 70ms，冲突锁下 bounded timeout 约 5.08s；这些数字不能外推 production SLA，production migration 仍未授权。

### GateY-6E minimum order value 语义前向修正（V41）

`V41__gate_y6e_minimum_order_value_semantic_remediation.sql` 只修正 V40 将独立 `minimum_order_value > 0 / currency=USDT` 误当成 mandatory venue-authored fact 的语义缺陷，不修改 V1～V40，也不授权真实 pilot、provider、订单或 LIVE。

- `pilot_instrument_observation_items` 新增非空 `minimum_order_value_evidence_class`，允许 `VENUE_PUBLISHED / VENUE_NOT_PUBLISHED / LEGACY_V40_REQUIRED`。`VENUE_PUBLISHED` 必须有正数 value 与非空 currency；`VENUE_NOT_PUBLISHED` 必须同时保持 value/currency 为 `NULL`；`LEGACY_V40_REQUIRED` 只允许保留 V40 历史行的原正数 USDT 形态。
- 历史行通过 `ADD COLUMN ... DEFAULT 'LEGACY_V40_REQUIRED'` 的 metadata-safe 路径无损标记，并在同一 migration 立即 `DROP DEFAULT`；没有 `UPDATE`、没有 value/currency 改写、没有把历史值重新解释为 venue-published。
- instrument observation 新增 `instrument-metadata-observation.v2`；migration 后的新 production instrument observation 只能插入 v2。历史 v1 继续可回读，其 canonical bytes/digest 不变；v1 item 必须标为 `LEGACY_V40_REQUIRED`，v2 item 禁止使用该 legacy class。
- v2 canonical item 总是编码 `minimumOrderValueEvidenceClass`；只有 `VENUE_PUBLISHED` 才编码 value/currency。`VENUE_NOT_PUBLISHED` 不编码两个 nullable 字段，形成唯一确定性 representation。外层 `pilot-scope.v1` 合同未变，instrument metadata digest 变化自然进入 exact scope hash。
- V41 重建 PostgreSQL instrument digest 与 observation payload hash 函数，保持 Java/PostgreSQL byte-for-byte parity；新 insert guard、evidence CHECK 与既有 append-only、immutable、exact-set、complete-set deferred validation 共同 fail closed。

本地 PostgreSQL 17.7 随机 schema 已验证 populated V40→V41、fresh V1→V41 replay、Flyway validate/checksum、historical v1 fingerprint/canonical bytes 不变、no-fake-backfill、v1/v2 共存、v2 evidence 约束、Java/PostgreSQL parity、append-only、identity/idempotency/conflict、complete-set 与 lock-timeout 回归。该验证未连接生产数据库，不构成 production migration 授权；production lock window/target scale 仍待独立 migration security review。
