# GateD DB_SCHEMA
# GateD 数据库变更说明

## 1. 目标

GateD 数据库变更的目标不是另起一套新表，而是在现有 orders / trades / ledger / event_store 基础上补齐执行闭环所需的约束、索引、幂等与追踪字段。

---

## 2. 建议迁移

建议新增迁移脚本：
- `V5__gate_d_execution_closure.sql`

---

## 3. orders 表建议

至少确认或补齐以下字段：
- `order_id`
- `account_id`
- `venue`
- `symbol`
- `side`
- `order_type`
- `price`
- `quantity`
- `executed_quantity`
- `avg_price`
- `client_order_id`
- `external_order_id`
- `idempotency_key`
- `request_id`
- `trace_id`
- `status`
- `reject_code`
- `reject_message`
- `created_at`
- `updated_at`
- `version`

建议索引：
- 唯一：`uk_orders_client_order_id`
- 唯一：`uk_orders_idempotency_key`
- 普通：`idx_orders_external_order_id`
- 组合：`idx_orders_account_status`
- 组合：`idx_orders_account_symbol_status`
- 普通：`idx_orders_trace_id`

---

## 4. trades 表建议

至少确认或补齐以下字段：
- `trade_id`
- `order_id`
- `venue`
- `account_id`
- `symbol`
- `external_order_id`
- `exchange_trade_id`
- `side`
- `price`
- `quantity`
- `fee`
- `fee_asset`
- `trade_ts`
- `trace_id`
- `created_at`

建议索引：
- 唯一：`uk_trades_venue_exchange_trade_id`
- 普通：`idx_trades_order_id`
- 普通：`idx_trades_external_order_id`
- 普通：`idx_trades_trace_id`

---

## 5. ledger_entries 表建议

至少确认：
- `idempotency_key`
- `trace_id`
- `order_id`
- `trade_id`
- `account_id`
- `symbol`
- `entry_type`
- `asset`
- `amount`
- `created_at`

建议索引：
- 唯一：`uk_ledger_entries_idempotency_key`
- 普通：`idx_ledger_entries_order_id`
- 普通：`idx_ledger_entries_trade_id`
- 普通：`idx_ledger_entries_trace_id`

---

## 6. positions / account_snapshots 建议

### positions
至少确认：
- `account_id`
- `venue`
- `symbol`
- `quantity`
- `available_quantity`
- `avg_cost`
- `updated_at`
- `trace_id`

建议索引：
- 唯一：`uk_positions_account_venue_symbol`
- 普通：`idx_positions_trace_id`

### account_snapshots
至少确认：
- `account_id`
- `venue`
- `asset`
- `balance`
- `available`
- `frozen`
- `snapshot_time`
- `trace_id`

建议索引：
- 组合：`idx_account_snapshots_account_asset_time`
- 普通：`idx_account_snapshots_trace_id`

---

## 7. event_store / audit_logs 建议

### event_store
至少确认：
- `event_id`
- `trace_id`
- `aggregate_type`
- `aggregate_id`
- `event_type`
- `payload_json`
- `source`
- `created_at`

建议索引：
- `idx_event_store_trace_id`
- `idx_event_store_aggregate`
- `idx_event_store_event_type`

### audit_logs
至少确认：
- `trace_id`
- `request_id`
- `client_order_id`
- `external_order_id`
- `account_id`
- `symbol`
- `venue`
- `action`
- `reason`
- `created_at`

---

## 8. 迁移原则

- 新环境可一次初始化
- 老环境可平滑升级
- 不破坏 GateA / GateB / GateC 既有数据
- 所有新增唯一键都要先核查历史脏数据

