# GateE DB_SCHEMA
# GateE 数据模型与迁移事实

本文档以当前仓库已经落下的 GateE-0.2 schema 为准，不再停留在“建议新增”阶段。

---

## 1. 当前 migration 基线

- `V1__init.sql`
- `V2__gate_b_schema_hardening.sql`
- `V3__gate_c_adapter_router.sql`
- `V4__gate_c_trade_external_order_id_index.sql`
- `V5__gate_e_schema_contract_alignment.sql`
- `V6__schema_comments_backfill.sql`

GateE-0.2 的新增与收口全部集中在：

- `backend/nq-infra/src/main/resources/db/migration/V5__gate_e_schema_contract_alignment.sql`
- `backend/nq-infra/src/main/resources/db/migration/V6__schema_comments_backfill.sql`

---

## 2. 本次新增表

### 2.1 `strategy_definitions`

作用：

- GateE 策略定义 / 注册主表

核心字段：

- `strategy_id`：定义级主键
- `strategy_code`：注册业务唯一键
- `strategy_name`
- `strategy_type`
- `exchange_code`
- `account_id`
- `trade_env`
- `enabled`
- `config_snapshot`
- `version`
- `created_at`
- `updated_at`

约束与索引：

- `PRIMARY KEY (strategy_id)`
- `UNIQUE (strategy_code)`
- `chk_strategy_definitions_trade_env`
- `chk_strategy_definitions_version`
- `idx_strategy_definitions_enabled_scan`

结论：

- 本阶段不引入 `strategyInstanceId`
- 一个启用中的策略注册项按 `strategy_code` 唯一识别

### 2.2 `strategy_schedules`

作用：

- GateE schedule job / 计划配置主表

核心字段：

- `schedule_job_id`
- `strategy_id`
- `schedule_type`
- `cron_expr`
- `timezone`
- `enabled`
- `window_config`
- `dedup_scope`
- `exchange_code`
- `account_id`
- `trade_env`
- `last_triggered_at`
- `created_at`
- `updated_at`

约束与索引：

- `PRIMARY KEY (schedule_job_id)`
- `fk_strategy_schedules_strategy`
- `fk_strategy_schedules_account`
- `chk_strategy_schedules_trade_env`
- `chk_strategy_schedules_type`
- `chk_strategy_schedules_dedup_scope`
- `idx_strategy_schedules_strategy_enabled`
- `idx_strategy_schedules_enabled_scan`

结论：

- `schedule_job_id` 是调度级身份
- 当前仍不引入 `strategy_triggers`

---

## 3. 本次收口的现有表

### 3.1 `strategy_runs`

本次动作：

- `run_id -> strategy_run_id`
- `ended_at -> finished_at`
- 新增 `trigger_type`
- 新增 `exchange_code`
- 新增 `trade_env`
- 新增 `config_snapshot`
- 新增 `request_id`
- 新增 `error_message`
- 新增 `idx_strategy_runs_request_id`
- 新增 `idx_strategy_runs_exchange_account_status`
- 新增表注释与字段注释

当前结论：

- `strategy_runs` 继续作为单次策略运行事实表，不重建
- `strategy_run_id` 是运行级身份
- `request_id` 只是首次触发请求身份，不替代 `strategy_run_id`

### 3.2 `orders`

本次动作：

- 新增 `request_id`
- 新增 `dedup_key`
- 新增 `exchange_code`
- 新增 `trade_env`
- 新增 `exchange_order_id`
- 新增 `idx_orders_request_id`
- 新增 `uq_orders_account_dedup_key`
- 新增 `idx_orders_exchange_code_exchange_order_id`
- 增加 `trg_orders_gatee_metadata` 触发器
- 补表注释与字段注释

兼容策略：

- `venue` 保留，标记为历史兼容列
- `external_order_id` 保留，标记为历史兼容列
- 新口径分别是 `exchange_code` 与 `exchange_order_id`

### 3.3 `trades`

本次动作：

- 新增 `strategy_run_id`
- 新增 `exchange_code`
- 新增 `trade_env`
- 新增 `exchange_order_id`
- 新增 `fk_trades_strategy_run`
- 新增 `idx_trades_strategy_run_id`
- 新增 `idx_trades_exchange_code_exchange_order_id`
- 新增 `uq_trades_exchange_code_exchange_trade_id`
- 增加 `trg_trades_gatee_metadata` 触发器
- 补表注释与字段注释

兼容策略：

- `exchange` 保留，标记为历史兼容列
- `external_order_id` 保留，标记为历史兼容列
- 新口径分别是 `exchange_code` 与 `exchange_order_id`

---

## 4. 最终身份口径

### 4.1 定义级

- `strategy_id`

### 4.2 运行级

- `strategy_run_id`

### 4.3 请求级

- `request_id`

### 4.4 去重级

- `dedup_key`

规则：

- `strategy_id`、`strategy_run_id`、`request_id`、`dedup_key` 禁止混用
- `request_id` 可记录在 `strategy_runs` 与 `orders`，但它仍是请求级身份

---

## 5. 最终维度口径

### 5.1 交易所维度

- canonical：`exchange_code`
- 历史兼容：`orders.venue`、`trades.exchange`

### 5.2 账户维度

- canonical：`account_id`

### 5.3 环境维度

- canonical：`trade_env`
- 固定枚举：`SIM` / `LIVE`

说明：

- 本次通过 schema + 注释把 `trade_env` 落库
- 当前兼容阶段默认值为 `SIM`，后续 GateE-1 要由注册与触发入口显式写入

---

## 6. 最终订单口径

- `order_id`：内部订单主键
- `client_order_id`：客户端订单号 / 幂等业务号
- `exchange_order_id`：交易所订单号

兼容债务：

- `external_order_id` 继续保留为兼容列，并通过 trigger 与 `exchange_order_id` 同步

---

## 7. 兼容债务与迁移方向

- `PlaceOrderCommand.strategy_id`
  - 当前仍存在
  - 实际执行血缘语义应迁到 `strategy_run_id`
  - GateE-0.2 先在文档和 schema 口径写死，不在本批大改业务主逻辑

- `orders.venue` / `trades.exchange`
  - 当前代码仍大量使用
  - 本次新增 `exchange_code` 作为 canonical 列
  - 后续分批把代码读写迁移到 `exchange_code`

- `orders.external_order_id` / `trades.external_order_id`
  - 当前代码仍使用
  - 本次新增 `exchange_order_id` 作为 canonical 列
  - 通过 trigger 保持兼容

---

## 8. 结论

- GateE-0.2 已把 GateE-1 / GateE-2 需要的最小定义层、调度层、运行层、订单层和成交层 schema 一次收口
- 本次没有引入 `strategy_instances`、`strategy_triggers`、查询宽表或 GateF 研究表
- 当前 remaining work 已切换为 GateE-0.3 与后续 GateE-1 主链实现

---

## 8.1 GateE-2.3 查询面约束

- `strategy_runs`、`orders`、`trades` 当前具备稳定的 `strategy_run_id` 血缘，已足够支撑最小 run 查询面
- `ledger_entries / ledger_events` 当前没有 `strategy_run_id` 外键
- `risk_events` 当前没有 `strategy_run_id` 外键
- `event_store / audit_logs` 当前主要按 `trace_id` 与业务键追踪，未形成稳定 run 外键

结论：

- GateE-2.3 直接聚合 `orders` 与 `trades`
- `ledger / risk / event / audit` 在本阶段只返回限制说明，不扩 schema

---

## 9. GateE-0.2-comment-fix 与整库注释回补结论

按 migration 实际扫描，当前整库表清单为 16 张：

- `users`
- `roles`
- `user_roles`
- `accounts`
- `strategy_runs`
- `orders`
- `trades`
- `positions`
- `account_snapshots`
- `ledger_entries`
- `ledger_events`
- `risk_events`
- `event_store`
- `audit_logs`
- `strategy_definitions`
- `strategy_schedules`

其中：

- 已在 GateE-0.2 主体中完成注释收口的核心表：`strategy_definitions`、`strategy_schedules`、`strategy_runs`、`orders`、`trades`
- 本次通过 `V6__schema_comments_backfill.sql` 回补的剩余表：`users`、`roles`、`user_roles`、`accounts`、`positions`、`account_snapshots`、`ledger_entries`、`ledger_events`、`risk_events`、`event_store`、`audit_logs`

结论：

- 当前整库 16 张表均已具备 `COMMENT ON TABLE`
- 剩余基础表的关键字段已通过 `V6` 补齐 `COMMENT ON COLUMN`
- GateE-0.2 的整库注释回补范围已闭合
