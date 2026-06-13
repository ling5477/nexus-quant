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

GateI DB 规划入口为 [GATEI_DB_PLAN.md](./GATEI_DB_PLAN.md)。GateI-1 已落地策略版本与发布绑定最小结构；GateI-2 已落地回测追溯与评估指标增强；GateI-3/4 尚未开始。

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

GateJ DB 规划入口为 [GATEJ_DB_PLAN.md](./GATEJ_DB_PLAN.md)。本轮只做规划，不新增 migration。

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
