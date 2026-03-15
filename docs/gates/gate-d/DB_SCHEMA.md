# GateD DB_SCHEMA
# GateD 数据库变更说明

## 1. 目标

GateD 数据库部分的目标不是再制造一条空 migration，而是在现有 `orders / trades / ledger_entries / event_store / positions / account_snapshots` 基础上确认：
- 冻结所需字段是否已经存在
- 关键索引与幂等约束是否已经可用
- 当前 schema 基线能否完成新库 init 与老库 upgrade

---

## 2. 当前冻结基线

当前数据库冻结基线为：`V1 -> V4`

已验证事实：
- 新库可直接从空库迁到 `V4`
- 老库可从 `V3` 平滑升级到 `V4`
- 本批未发现新的 GateD schema delta，因此无额外 GateD migration 必要

---

## 3. orders 表（当前事实）

当前已存在：
- `order_id`
- `account_id`
- `venue`
- `symbol`
- `side`
- 历史列名 `type`
- `price`
- 历史列名 `qty`
- `client_order_id`
- `external_order_id`
- `status`
- 历史列名 `reason`
- `trace_id`
- `created_at`
- `updated_at`

当前已验证索引 / 约束：
- `uq_orders_account_client_order`
- `idx_orders_trace_id`
- `idx_orders_venue_external_order_id`

说明：
- `request_id / idempotency_key / reject_code / reject_message / version` 尚未以独立 schema 字段全面收口，当前通过执行链语义、审计与事件口径补齐；后续若需要 schema 化，顺延到 GateE / 后续治理批。

---

## 4. trades 表（当前事实）

当前已存在：
- `trade_id`
- `order_id`
- `account_id`
- `symbol`
- `exchange`
- `exchange_trade_id`
- `external_order_id`
- `price`
- `qty`
- `fee`
- `fee_currency`
- `trace_id`
- `ts`
- `created_at`

当前已验证索引 / 约束：
- `uq_trades_exchange_trade`
- `idx_trades_order_ts`
- `idx_trades_trace_id`
- `idx_trades_exchange_external_order_id`

---

## 5. ledger_entries 表（当前事实）

当前已存在：
- `entry_id`
- `account_id`
- `currency`
- `delta`
- `balance_after`
- `direction`
- `ref_type`
- `ref_id`
- `idempotency_key`
- `trace_id`
- `ts`
- `created_at`

当前已验证索引 / 约束：
- `uq_ledger_entries_idempotency_key`
- `idx_ledger_entries_account_ts`
- `idx_ledger_entries_ref`
- `idx_ledger_entries_trace_id`

---

## 6. positions / account_snapshots（当前事实）

### positions
当前已存在：
- `account_id`
- `symbol`
- `qty`
- `available_qty`
- `frozen_qty`
- `avg_price`
- `trace_id`
- `updated_at`

当前已验证索引 / 约束：
- `uq_positions_account_symbol`
- `idx_positions_account_updated`

### account_snapshots
当前已存在：
- `account_id`
- `currency`
- `balance`
- `available`
- `frozen`
- `ts`
- `trace_id`
- `created_at`

当前已验证索引：
- `idx_account_snapshots_account_ts`

说明：
- 当前快照能力已满足 GateD 最小冻结口径；更强的真实 venue 拉取与查询一致性增强顺延到 GateE。

---

## 7. event_store / audit_logs（当前事实）

### event_store
当前已存在：
- `event_id`
- `topic`
- `schema_version`
- `event_type`
- `payload_json`
- `key_value`
- `trace_id`
- `created_at`

当前已验证索引：
- `idx_event_store_topic_created`
- `idx_event_store_trace_id`
- `idx_event_store_type_created`

### audit_logs
当前已存在：
- `domain`
- `action`
- `actor_id`
- `trace_id`
- `detail_json`
- `created_at`

当前已验证索引：
- `idx_audit_logs_domain_created`
- `idx_audit_logs_actor_created`
- `idx_audit_logs_trace_id`

---

## 8. 结论

- GateD 当前数据库冻结基线为 `V1 -> V4`
- 现有 schema 已支撑 GateD 主线冻结，不存在必须在 GateD 期间补出的新增 migration
- 若后续出现真实 schema 演化需求，应在 GateE / 后续治理批中按事实新增 migration，而不是为迎合历史占位文案制造空迁移
