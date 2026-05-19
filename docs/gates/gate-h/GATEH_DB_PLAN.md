# GateH DB Plan

本文件只规划 GateH DB 结构，不新增 migration，不修改业务表结构。数据库事实仍以 Flyway migrations 为准。

## 第一版范围

- `exchange_code`：`OKX`、`BINANCE`
- `market_type`：`SPOT`
- `interval`：`1m`、`5m`、`15m`、`1h`、`4h`、`1d`
- `symbol`：`BTC-USDT`、`ETH-USDT`、`SOL-USDT`
- data type：OHLCV K 线

## `instrument_catalog` 当前状态与 GateH 增强点

当前状态：

- 当前系统已有行情与 instrument 基础表能力，具体结构以 migrations 为准。
- GateH 前，`instrument_catalog` 还不是所有交易对选择和行情接入的唯一前端来源。

GateH 增强点：

- 统一字段口径：`exchange_code`、`market_type`、`symbol`、`base_asset`、`quote_asset`、`status`、`source`、`updated_at`。
- 补充交易精度：`price_precision`、`quantity_precision`、`min_order_quantity`、`min_notional`。
- 支持 SPOT 第一版交易对目录同步。
- 作为交易工作台、行情查询、接入任务和数据集创建的交易对来源。

建议唯一约束：

- `exchange_code + market_type + symbol`

建议索引：

- `exchange_code + market_type + status`
- `base_asset + quote_asset`

## `marketdata_bars` 当前状态与 GateH 增强点

当前状态：

- 当前系统已有 `marketdata_bars` 基础表能力，具体字段以 migrations 为准。
- GateH 需要强化来源追溯、质量状态、原始 payload 和幂等写入。

GateH 规划字段：

- `exchange_code`
- `market_type`
- `symbol`
- `interval`
- `open_time`
- `close_time`
- `open_price`
- `high_price`
- `low_price`
- `close_price`
- `volume`
- `quote_volume`
- `trade_count`
- `source`
- `quality_status`
- `raw_payload_json`
- `ingested_at`

唯一约束：

- `exchange_code + market_type + symbol + interval + open_time`

建议索引：

- `exchange_code + market_type + symbol + interval + open_time`
- `exchange_code + market_type + symbol + interval + close_time`
- `quality_status + ingested_at`
- `source + ingested_at`

数据质量字段：

- `quality_status`：建议值 `PENDING`、`VALID`、`GAP_DETECTED`、`DUPLICATE_IGNORED`、`INVALID`。
- 后续可按需要增加 `quality_reason`、`validated_at`、`gap_count`，但 GateH migration 需另行评审。

原始 payload 保存策略：

- `raw_payload_json` 保存交易所单条 K 线原始响应的最小可追溯内容。
- 不保存敏感凭证和请求签名。
- 只保留与复核价格、数量、交易笔数、时间戳有关的字段。

幂等策略：

- 写入以唯一约束为准。
- 相同唯一键再次写入时，价格和数量一致则跳过或更新 `ingested_at`，不一致则标记冲突并进入质量检查。
- 所有接入 run 记录写入条数、跳过条数、冲突条数。

## `marketdata_ingestion_jobs` 草案

用途：

- 描述历史行情接入任务配置。

规划字段：

- `job_id`
- `exchange_code`
- `market_type`
- `symbols_json`
- `intervals_json`
- `from_time`
- `to_time`
- `mode`
- `schedule`
- `enabled`
- `status`
- `created_by`
- `created_at`
- `updated_at`

唯一约束：

- 可选：`exchange_code + market_type + symbols_hash + intervals_hash + from_time + to_time + mode`

建议索引：

- `status + enabled`
- `exchange_code + market_type`
- `updated_at`

## `marketdata_ingestion_runs` 草案

用途：

- 记录每次接入任务执行结果。

规划字段：

- `run_id`
- `job_id`
- `exchange_code`
- `market_type`
- `symbol`
- `interval`
- `from_time`
- `to_time`
- `status`
- `requested_count`
- `inserted_count`
- `updated_count`
- `skipped_count`
- `conflict_count`
- `gap_count`
- `error_code`
- `error_message`
- `started_at`
- `finished_at`

建议索引：

- `job_id + started_at`
- `status + started_at`
- `exchange_code + market_type + symbol + interval + started_at`

## `marketdata_datasets` 草案

用途：

- 描述回测可绑定的行情数据集范围和质量状态。

规划字段：

- `dataset_id`
- `name`
- `description`
- `exchange_code`
- `market_type`
- `symbols_json`
- `intervals_json`
- `from_time`
- `to_time`
- `quality_status`
- `bar_count`
- `source`
- `created_by`
- `created_at`
- `updated_at`

唯一约束：

- 可选：`name`
- 可选：`exchange_code + market_type + symbols_hash + intervals_hash + from_time + to_time`

建议索引：

- `exchange_code + market_type`
- `quality_status`
- `created_at`

## `backtest_configs` 与 dataset 绑定草案

用途：

- 让回测配置绑定真实历史行情数据集，并让结果可追溯。

规划方式：

- 方案 A：在 `backtest_configs` 增加 `dataset_id`。
- 方案 B：新增 `backtest_config_datasets` 绑定表，支持后续多数据集扩展。

GateH 推荐：

- 若当前回测配置一次只需要一个数据集，先采用方案 A。
- 若已有结构更适合多数据集，采用方案 B，但必须保持最小 migration。

回测结果追溯：

- `backtest_runs` 或结果表需要记录 `dataset_id` 快照。
- 结果摘要需要能回溯到 `exchange_code`、`market_type`、`symbols`、`intervals`、`from_time`、`to_time`、`quality_status`。

## 数据回填策略

- 第一阶段只回填 `OKX`、`BINANCE`、`SPOT` 的 `BTC-USDT`、`ETH-USDT`、`SOL-USDT`。
- 回填按 symbol、interval、time range 分片执行。
- 每个分片写入前检查已有范围，避免重复拉取。
- 每次 run 记录缺口、重复、冲突和失败原因。
- 回填失败允许按 `job_id` / `run_id` 重跑。

## 本轮限制

- 本轮不新增 migration。
- 本轮不修改业务表结构。
- 本轮不写 SQL 实现。
- 本轮不写交易所历史数据抓取代码。
