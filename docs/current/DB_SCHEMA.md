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
- 本批未新增 Repository 默认过滤、归档业务 API、逻辑删除、物理删除或 retention purge；这些行为如需启用，必须进入后续 Batch 4。
- 本批未处理回测事实表、评估结果表、发布记录、Paper facts、orders、trades、ledger、risk_events、positions、marketdata timeseries 或 credentials。

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
