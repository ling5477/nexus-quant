# Current DB Schema

数据库结构以 Flyway migrations 为准。本文只记录当前数据库事实入口，不复制完整 DDL。

## 本地数据库规则

- 本地 PostgreSQL 默认端口：`5432`。
- 本地 JDBC 默认地址：`jdbc:postgresql://localhost:5432/nexus_quant`。
- `application-local.yml` 支持 `NQ_DB_URL` 覆盖。
- `application-local.yml` 支持 `NQ_DB_PORT` 覆盖，默认 `5432`。

## 当前已有表域

当前数据库已包含用户、账户、凭证、订单、成交、持仓、策略、调度、研究、回测、评估、发布、行情基础表。具体字段、索引、约束以 `backend/**/db/migration` 下的 Flyway migration 为准。

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
