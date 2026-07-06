# DB Schema Governance Review

任务：NQ-DB-SCHEMA-GOVERNANCE-REVIEW-BATCH-1
日期：2026-06-06
范围：只读审查 Flyway migration、`docs/current` 数据库/API 文档、JDBC Repository 与相关 Domain/Application/API 语义。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ。

## 1. 当前结论

当前数据库结构能支撑 GateH / GateI / GateJ 已完成能力，尤其是账户上下文、凭证版本、行情 K 线、dataset 绑定、回测追溯、策略版本、发布、Paper Trading 运行、风控回写、曲线快照、复盘、调度、心跳、日报、告警、恢复与稳定性检查。

本轮不建议立即修改历史 migration，也不建议本轮新增 migration。当前主要治理问题是长期 schema 注释混入 Gate 阶段语义、早期主数据表审计字段不一致、部分状态/类型字段缺 CHECK 约束、事实/事件/时序表缺少显式 retention policy 文档，以及未来如引入逻辑删除时 Repository 默认过滤尚未设计。

总体分级：条件通过。允许进入后续 Batch 2-5 做分批治理，但必须单独开工、单独 migration、单独验证，不能与 GateK-PLAN、AI、DH、LIVE 或业务功能实现合并。

## 2. 表结构总体评价

- 表域覆盖完整：用户/角色、legacy trading account、正式 exchange account、credential、交易事实、账本、风险、审计、事件、策略、调度、研究、回测、评估、发布、行情、dataset、Paper Trading 与 GateJ 稳定性数据均已落库。
- 可追溯性较好：订单、成交、账本、审计、回测、发布、Paper run、日报、告警、恢复和稳定性检查具备业务主键、时间字段、快照 JSON 或外键血缘。
- 删除治理尚未统一：当前 50 张表均未使用 `deleted_at / deleted_by / delete_reason`；代码中也没有 `deleted_at IS NULL` 的默认过滤模型。仅 `user_roles` 存在授权关系重绑时的物理删除。
- 状态治理已有基础：`exchange_accounts.status=ACTIVE/DISABLED`、`exchange_account_credentials.verification_status=REVOKED`、`strategy_versions.status=ARCHIVED`、`marketdata_datasets.status=ARCHIVED`、Paper run 相关状态均提供了状态替代删除的方向。
- 注释治理需要收口：不少长期业务表的 `COMMENT ON TABLE` 仍包含 `GateE / GateF / GateH / GateI / RC1 / 第一版 / 最小口径`，这些应改为长期业务语义。

## 3. 表级治理矩阵

| 表 | 分类 | 生命周期 | 删除策略 | 时间字段建议 | 结论 |
| --- | --- | --- | --- | --- | --- |
| `users` | USER_ACCOUNT | STATUS_MUTABLE | DISABLE_ONLY | 保留 `created_at/updated_at`；不优先加 `deleted_at` | 用 `enabled=false` 表示停用，禁止物理删除。 |
| `roles` | CONFIG_MASTER | METADATA_MUTABLE | DISABLE_ONLY | 建议 Batch 3 补 `updated_at`；可评估 `enabled` | 角色是权限主数据，不建议物理删除。 |
| `user_roles` | USER_ACCOUNT | FULLY_MUTABLE | HARD_DELETE_ALLOWED | 建议保留 `granted_at`；不强制 `updated_at/deleted_at` | 授权关系表允许重绑时物理删除再插入。 |
| `accounts` | USER_ACCOUNT | STATUS_MUTABLE | DISABLE_ONLY | 建议 Batch 3 补 `updated_at`；不加 `deleted_at` | legacy 账户表保留兼容，不新增删除接口。 |
| `exchange_accounts` | USER_ACCOUNT | STATUS_MUTABLE | DISABLE_ONLY | 已有 `created_at/updated_at` | 使用 `status=DISABLED`，不建议逻辑删除。 |
| `exchange_account_credentials` | CREDENTIAL | STATUS_MUTABLE | REVOKE_ONLY | 已有 `created_at/updated_at/revoked_at`；可后续补 `revoked_by/revoke_reason` | 凭证必须以 `REVOKED + revoked_at` 收口，禁止普通删除。 |
| `strategy_definitions` | STRATEGY_CONFIG | STATUS_MUTABLE | DISABLE_ONLY | 已有 `created_at/updated_at` | 用 `enabled=false` 停用；不建议 `deleted_at`。 |
| `strategy_versions` | STRATEGY_VERSION | IMMUTABLE | ARCHIVE_STATUS | 已有 `created_at/updated_at`；可保留状态更新时间 | 版本快照应不可变；使用 `ARCHIVED`。 |
| `strategy_schedules` | STRATEGY_CONFIG | STATUS_MUTABLE | DISABLE_ONLY | 已有 `created_at/updated_at` | 调度配置用 `enabled=false` 停用。 |
| `strategy_runs` | OPERATIONAL_EVENT | APPEND_ONLY | NO_DELETE | 保留 `created_at/started_at/finished_at`；不加 `updated_at` | 运行事实应保留，不做普通删除。 |
| `orders` | TRADING_FACT | STATUS_MUTABLE | NO_DELETE | 已有 `created_at/updated_at` | 订单事实禁止逻辑/物理删除。 |
| `trades` | TRADING_FACT | APPEND_ONLY | NO_DELETE | 保留 `ts/created_at`；不加 `updated_at` | 成交事实禁止删除。 |
| `positions` | SNAPSHOT | FULLY_MUTABLE | NO_DELETE | 可补 `created_at`；保留 `updated_at` | 当前持仓投影不可删除，应由成交/账本重建或更新。 |
| `account_snapshots` | SNAPSHOT | APPEND_ONLY | RETENTION_PURGE | 保留 `ts/created_at`；不加 `updated_at` | 快照可按 retention 物理清理。 |
| `ledger_entries` | LEDGER_FACT | IMMUTABLE | NO_DELETE | 保留 `ts/created_at`；不加 `updated_at` | 账本事实禁止逻辑/物理删除。 |
| `ledger_events` | AUDIT_EVENT | APPEND_ONLY | NO_DELETE | 保留 `created_at`；不加 `updated_at` | 账本事件保留审计证据。 |
| `risk_events` | RISK_EVENT | APPEND_ONLY | NO_DELETE | 保留 `created_at`；不加 `updated_at` | 风控事件不可删除。 |
| `event_store` | OPERATIONAL_EVENT | APPEND_ONLY | RETENTION_PURGE | 保留 `created_at`；不加 `updated_at` | 可按事件主题和留存周期清理。 |
| `audit_logs` | AUDIT_EVENT | APPEND_ONLY | NO_DELETE | 保留 `created_at`；不加 `updated_at` | 审计日志禁止普通删除。 |
| `research_configs` | RESEARCH_CONFIG | METADATA_MUTABLE | ARCHIVE_STATUS | 已有 `created_at/updated_at`；建议后续补 `status=ARCHIVED` | 研究配置应可归档，不应物理删除。 |
| `backtest_configs` | BACKTEST_CONFIG | METADATA_MUTABLE | ARCHIVE_STATUS | 已有 `created_at/updated_at`；建议后续补 `status=ARCHIVED` | 回测配置要保留可复现血缘。 |
| `backtest_runs` | BACKTEST_FACT | STATUS_MUTABLE | NO_DELETE | 已有 `created_at/updated_at/requested_at/started_at/finished_at` | run 事实禁止删除。 |
| `sim_orders` | BACKTEST_FACT | STATUS_MUTABLE | NO_DELETE | 已有 `created_at/updated_at` | 模拟订单是回测事实，不做删除。 |
| `sim_trades` | BACKTEST_FACT | APPEND_ONLY | NO_DELETE | 保留 `traded_at/created_at`；建议评估移除或冻结 `updated_at` 语义 | 模拟成交事实不应普通删除。 |
| `sim_positions` | SNAPSHOT | FULLY_MUTABLE | NO_DELETE | 已有 `created_at/updated_at` | run+symbol 当前模拟持仓，不做删除。 |
| `sim_pnl_snapshots` | SNAPSHOT | APPEND_ONLY | RETENTION_PURGE | 保留 `snapshot_time/created_at`；不加 `updated_at` | 大量快照可按 run 归档/留存清理。 |
| `backtest_eval_reports` | EVALUATION_RESULT | STATUS_MUTABLE | NO_DELETE | 已有 `created_at/updated_at/evaluated_at` | 评估报告保留追溯，不删除。 |
| `backtest_publish_records` | PUBLISH_RESULT | STATUS_MUTABLE | NO_DELETE | 已有 `created_at/updated_at/published_at` | 发布事实不可删除。 |
| `marketdata_bars` | MARKETDATA_TIMESERIES | APPEND_ONLY | RETENTION_PURGE | 保留 `open_time/close_time/ingested_at`；不加 `created_at/updated_at` | 行情 K 线以业务时间为主，适合 retention。 |
| `instrument_catalog` | CONFIG_MASTER | METADATA_MUTABLE | DISABLE_ONLY | 已有 `created_at/updated_at/synced_at` | 用 `status` 表示上下架，不做删除。 |
| `marketdata_ingestion_jobs` | MARKETDATA_JOB | STATUS_MUTABLE | ARCHIVE_STATUS | 已有 `created_at/updated_at`；可后续补 `ARCHIVED` | 接入任务建议归档而非删除。 |
| `marketdata_ingestion_runs` | MARKETDATA_JOB | APPEND_ONLY | RETENTION_PURGE | 保留 `started_at/finished_at/created_at`；不加 `updated_at` | 运行记录可按任务和时间留存清理。 |
| `marketdata_datasets` | MARKETDATA_TIMESERIES | STATUS_MUTABLE | ARCHIVE_STATUS | 已有 `created_at/updated_at` | 已有 `ARCHIVED`，不建议 `deleted_at`。 |
| `marketdata_dataset_coverage` | DERIVED_REPORT | APPEND_ONLY | RETENTION_PURGE | 保留 `created_at`；不加 `updated_at` | 覆盖刷新结果可按 dataset 留存。 |
| `paper_trading_runs` | PAPER_RUNTIME | STATUS_MUTABLE | NO_DELETE | 已有 `created_at/updated_at/started_at/stopped_at` | Paper run 是运行事实，禁止删除。 |
| `paper_trading_orders` | PAPER_FACT | STATUS_MUTABLE | NO_DELETE | 已有 `created_at/updated_at` | Paper 订单事实禁止删除。 |
| `paper_trading_trades` | PAPER_FACT | APPEND_ONLY | NO_DELETE | 保留 `traded_at/created_at`；不加 `updated_at` | Paper 成交事实禁止删除。 |
| `paper_trading_positions` | SNAPSHOT | FULLY_MUTABLE | NO_DELETE | 已有 `created_at/updated_at` | 当前持仓投影不删除。 |
| `paper_risk_check_results` | RISK_EVENT | APPEND_ONLY | NO_DELETE | 保留 `created_at`；不加 `updated_at` | 风控检查事实不可删除。 |
| `equity_curve_snapshots` | SNAPSHOT | APPEND_ONLY | RETENTION_PURGE | 保留 `snapshot_time/created_at`；不加 `updated_at` | 资金曲线可按 run 留存策略清理。 |
| `position_curve_snapshots` | SNAPSHOT | APPEND_ONLY | RETENTION_PURGE | 保留 `snapshot_time/created_at`；不加 `updated_at` | 持仓曲线可按 run 留存策略清理。 |
| `trade_replay_records` | AUDIT_EVENT | APPEND_ONLY | NO_DELETE | 保留 `replay_time/created_at`；不加 `updated_at` | 交易复盘是审计链路，不删除。 |
| `emergency_stop_events` | RISK_EVENT | STATUS_MUTABLE | NO_DELETE | 保留 `triggered_at/resolved_at/created_at`；不强制 `updated_at` | 停机事件不可删除，可状态更新。 |
| `paper_run_schedules` | PAPER_RUNTIME | STATUS_MUTABLE | DISABLE_ONLY | 已有 `created_at/updated_at` | 调度计划用 `DISABLED/PAUSED`。 |
| `paper_run_schedule_fires` | OPERATIONAL_EVENT | APPEND_ONLY | RETENTION_PURGE | 保留 `fired_at/finished_at/created_at`；不加 `updated_at` | 调度触发记录可按 retention 清理。 |
| `paper_run_heartbeats` | OPERATIONAL_EVENT | APPEND_ONLY | RETENTION_PURGE | 保留 `heartbeat_time/created_at`；不加 `updated_at` | 心跳是典型高频事件，必须 retention。 |
| `paper_run_daily_reports` | DERIVED_REPORT | APPEND_ONLY | RETENTION_PURGE | 保留 `report_date/generated_at/created_at`；不加 `updated_at` | 日报可重算或按 run 留存。 |
| `paper_run_alerts` | OPERATIONAL_EVENT | STATUS_MUTABLE | NO_DELETE | 已有 `created_at/updated_at/acknowledged_at/resolved_at` | 告警用 `ACKED/RESOLVED`，不删除。 |
| `paper_run_recovery_events` | OPERATIONAL_EVENT | APPEND_ONLY | NO_DELETE | 保留 `started_at/finished_at/created_at`；不加 `updated_at` | 恢复/重试事件必须保留审计。 |
| `paper_run_stability_checks` | EVALUATION_RESULT | APPEND_ONLY | RETENTION_PURGE | 保留 `created_at/check_window_*`；不加 `updated_at` | 稳定性结果可按验收窗口留存。 |

## 4. 字段缺口矩阵

| 缺口 | 涉及表 | 建议 |
| --- | --- | --- |
| 主数据缺少 `updated_at` | `roles`、`accounts` | Batch 3 评估补齐，避免后台编辑或兼容数据修正时缺少更新时间。 |
| 关联表缺少授权操作者 | `user_roles` | 可后续补 `granted_by`；不建议补 `deleted_at`，关系重绑允许物理删除。 |
| legacy 当前投影缺 `created_at` | `positions` | 可在 Batch 3 补 `created_at`，保留 `updated_at` 表示投影刷新时间。 |
| 凭证撤销缺操作者和原因 | `exchange_account_credentials` | 可补 `revoked_by/revoke_reason`，与 `revoked_at` 形成完整审计。 |
| 配置表无归档状态 | `research_configs`、`backtest_configs` | 建议新增 `status`，允许 `ACTIVE/ARCHIVED`，不要加普通删除。 |
| 行情/心跳/曲线无 retention 标记 | `marketdata_bars`、`paper_run_heartbeats`、`equity_curve_snapshots` 等 | 不建议加 `deleted_at`；应新增文档化 retention policy 和清理任务。 |
| 审计/事实表缺删除字段 | `orders`、`trades`、`ledger_entries`、`audit_logs` 等 | 这是正确方向，不建议补 `deleted_at`。 |

## 5. 删除策略矩阵

| 策略 | 表 |
| --- | --- |
| NO_DELETE | `strategy_runs`, `orders`, `trades`, `positions`, `ledger_entries`, `ledger_events`, `risk_events`, `audit_logs`, `backtest_runs`, `sim_orders`, `sim_trades`, `sim_positions`, `backtest_eval_reports`, `backtest_publish_records`, `paper_trading_runs`, `paper_trading_orders`, `paper_trading_trades`, `paper_trading_positions`, `paper_risk_check_results`, `trade_replay_records`, `emergency_stop_events`, `paper_run_alerts`, `paper_run_recovery_events` |
| DISABLE_ONLY | `users`, `roles`, `accounts`, `exchange_accounts`, `strategy_definitions`, `strategy_schedules`, `instrument_catalog`, `paper_run_schedules` |
| REVOKE_ONLY | `exchange_account_credentials` |
| ARCHIVE_STATUS | `strategy_versions`, `research_configs`, `backtest_configs`, `marketdata_ingestion_jobs`, `marketdata_datasets` |
| RETENTION_PURGE | `account_snapshots`, `event_store`, `sim_pnl_snapshots`, `marketdata_bars`, `marketdata_ingestion_runs`, `marketdata_dataset_coverage`, `equity_curve_snapshots`, `position_curve_snapshots`, `paper_run_schedule_fires`, `paper_run_heartbeats`, `paper_run_daily_reports`, `paper_run_stability_checks` |
| HARD_DELETE_ALLOWED | `user_roles` |
| SOFT_DELETE | 当前不建议任何表直接采用通用 soft delete；如未来新增租户级配置目录，可单独评估。 |
| REBUILDABLE_CACHE | 当前 migration 未发现纯缓存表。 |

## 6. 注释阶段语义问题清单

以下表/字段注释包含阶段语义，应在 Batch 2 使用 comment-only migration 清理为长期业务语义：

- `strategy_definitions`、`strategy_schedules`、`strategy_runs`、`orders`：含 `GateE`。
- `research_configs`、`backtest_configs`、`backtest_runs`、`sim_orders`、`sim_trades`、`sim_positions`、`sim_pnl_snapshots`、`backtest_eval_reports`、`backtest_publish_records`：含 `GateF`。
- `exchange_accounts`、`exchange_account_credentials`、`marketdata_bars`：含 `RC1`。
- `instrument_catalog`：含 `GateH-PRE`。
- `marketdata_ingestion_jobs`、`marketdata_ingestion_runs`：含 `GateH-2`。
- `marketdata_datasets`、`marketdata_dataset_coverage`：含 `GateH-3`。
- `strategy_versions`：含 `GateI-1`。
- `paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades`、`paper_trading_positions`：含 `GateI-3`。
- `paper_risk_check_results`、`equity_curve_snapshots`、`position_curve_snapshots`、`trade_replay_records`、`emergency_stop_events`：含 `GateI-4`。
- `paper_run_stability_checks`：含 `第一版最小口径` 和 `非 GateJ-FREEZE 最终验收`，建议保留业务边界但去掉阶段化表达。

## 7. CHECK 约束问题清单

| 表/字段 | 当前风险 | 建议 |
| --- | --- | --- |
| `accounts.status` | legacy 状态无 CHECK，迁移到 `exchange_accounts` 时已做映射，但旧表仍可写脏状态。 | Batch 3 评估加 `CHECK (status IN ('ACTIVE','DISABLED'))` 或冻结 legacy 写入。 |
| `instrument_catalog.status` | 主数据状态无 CHECK，Repository upsert 可写任意状态。 | Batch 3 加 `ACTIVE/DISABLED/DELISTED` 等业务枚举。 |
| `instrument_catalog.instrument_type` | 当前注释说固定 SPOT，但无 CHECK。 | 加 `CHECK (instrument_type IN ('SPOT'))`，后续多市场扩展再扩枚举。 |
| `risk_events.decision/severity` | 风控事件严重程度和决策缺枚举约束。 | 加 `CHECK`，例如 `decision IN ('ALLOW','REJECT','WARN')`，severity 与告警口径对齐。 |
| `ledger_events.event_type`、`event_store.event_type/topic` | 事件类型较开放。 | 不强制立刻 CHECK；先在文档中规定事件命名和 schema version 策略。 |
| `sim_trades.side`、`trade_replay_records.event_type/side` | 部分事实字段未完全约束。 | Batch 3/4 结合 domain enum 补约束。 |

## 8. 索引与查询风险清单

- `users.username`、`roles.role_code`、`user_roles` 主键可支撑登录与授权；`JdbcAuthUserRepository` 重绑角色使用 `DELETE FROM user_roles WHERE user_id = ?`，这是关系表允许物理删除的唯一明确证据。
- `orders` / `trades` / `account_snapshots` 已覆盖交易工作台按账户、状态、symbol、trace、时间倒序查询；不建议因删除策略新增全局过滤索引。
- `instrument_catalog` 有 `(exchange_code, status, internal_symbol)` 索引，但 status 无 CHECK；若未来加状态，应保持该索引。
- `marketdata_bars` 已有范围时间索引和唯一约束；retention 清理前应确认按 `open_time` 或 scope+time 分批，不做全表锁式清理。
- `paper_run_heartbeats`、`paper_run_schedule_fires`、`paper_run_alerts` 已覆盖 GateJ monitor 查询；Batch 5 retention 应按 `paper_run_id + 时间` 小批清理。
- 如果后续引入 `deleted_at`，所有 Repository 查询和唯一索引都要重新设计；本轮结论是不引入通用 soft delete。

## 9. JSONB / 敏感信息风险清单

- 已明确禁入敏感信息的 JSONB：`marketdata_ingestion_jobs.request_json`、`marketdata_ingestion_runs.raw_summary_json`、`marketdata_bars.raw_payload_json`、`strategy_versions.*_snapshot_json`、`backtest_configs.*_snapshot_json`、`backtest_runs.*_snapshot_json`、`paper_*` snapshot/result/report JSON 多数已写明不保存密钥/token/cookie。
- 需补注释或统一措辞的 JSONB：`strategy_definitions.config_snapshot`、`strategy_schedules.window_config`、`strategy_runs.config_snapshot`、`ledger_events.payload_json`、`event_store.payload_json`、`audit_logs.detail_json`、`research_configs.strategy_snapshot/config_json`、`backtest_configs.config_json/evaluation_spec_json`、`backtest_eval_reports.report_json`、`backtest_publish_records.publish_snapshot_json/evaluation_summary_json`。
- `exchange_account_credentials.encrypted_payload` 是密文字段，不是 JSONB；禁止任何文档、日志或测试输出密文、明文、token、cookie 或 exchange secret。

## 10. P0 / P1 / P2 / P3 分级发现

### P0

无。未发现当前 schema 已开启 LIVE trading、AI、DH integration、真实交易所私有链路或凭证明文存储。

### P1

- 阶段语义注释进入长期业务注释，影响后续 GateK-PLAN 和生产治理文档可信度。建议 Batch 2 优先 comment-only migration 清理。
- 删除策略尚未形成统一治理文档。当前没有 `deleted_at` 模型，若后续局部新增 soft delete，会导致 Repository 查询不一致。

### P2

- `roles`、`accounts`、`positions` 等早期表审计时间字段不统一。
- `accounts.status`、`instrument_catalog.status/instrument_type`、`risk_events.decision/severity` 等字段缺 CHECK。
- 部分 JSONB 注释未明确敏感信息禁入。
- retention policy 未落文档和任务，行情、心跳、曲线、日报、稳定性检查长期增长后会影响维护成本。

### P3

- `sim_trades`、`sim_positions` 等部分回测事实表保留 `updated_at`，语义上更接近事实/投影混合，后续可只做文档澄清，不必立即改字段。
- legacy `accounts` 与正式 `exchange_accounts` 并存，短期可接受，但长期应减少新代码对 legacy 表的直接依赖。

## 11. 不建议修改的表清单和原因

- 订单、成交、账本、审计、风控事件：`orders`、`trades`、`ledger_entries`、`ledger_events`、`audit_logs`、`risk_events`。原因：事实和审计证据应不可删除。
- 回测/Paper 事实：`backtest_runs`、`sim_orders`、`sim_trades`、`paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades`。原因：影响复现、评估、发布和运行追溯。
- 凭证表不应做普通删除：`exchange_account_credentials`。原因：应使用撤销语义和审计字段。
- 行情/快照/心跳不应使用 soft delete：`marketdata_bars`、`account_snapshots`、`paper_run_heartbeats`、`equity_curve_snapshots`、`position_curve_snapshots`。原因：应按业务时间 retention purge。

## 12. 建议优先修改的表清单和原因

- Batch 2 comment-only：优先清理所有含 `GateE/GateF/GateH/GateI/RC1/第一版` 的表和字段注释。
- Batch 3 字段和约束：`roles`、`accounts`、`positions`、`instrument_catalog`、`risk_events`、`research_configs`、`backtest_configs`、`exchange_account_credentials`。
- Batch 4 Repository 行为：仅当 Batch 3 引入归档/停用状态后，补默认过滤和状态流转测试；不要在没有字段/migration 的情况下改 Repository。
- Batch 5 retention：`marketdata_bars`、`marketdata_ingestion_runs`、`event_store`、`account_snapshots`、`sim_pnl_snapshots`、`equity_curve_snapshots`、`position_curve_snapshots`、`paper_run_schedule_fires`、`paper_run_heartbeats`、`paper_run_daily_reports`、`paper_run_stability_checks`。

## 13. 本轮未执行项

本轮只做文档审查，没有修改业务代码、API、Repository、DTO、前端、Python、部署脚本或 Flyway migration。未运行 `mvn`、`npm`、`pytest/mypy/ruff`，不得把这些验证写成本轮通过。
