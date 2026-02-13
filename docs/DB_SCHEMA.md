# 数据库结构说明（DB_SCHEMA）

> Gate A：不要求实现完整 DDL，但要求**表清单、关键字段、索引策略与一致性口径**冻结，便于后续 Flyway 落地。

---

## 1. 设计原则

- 业务表与事件表分离：**事件是事实（不可变）**，业务表是投影（可重建）
- 关键写入路径必须可幂等（唯一约束）
- 以查询为导向设计索引（尤其：按 run_id / strategy_id / account_id / symbol / 时间区间）

---

## 2. 表清单（建议）

### 2.1 订单域（core）
- `orders`
  - 关键字段：`order_id`（PK）、`client_order_id`（UNIQUE）、`account_id`、`symbol`、`side`、`type`、`price`、`qty`、`status`、`created_at`
  - 索引：`(account_id, created_at)`、`(symbol, created_at)`、`(status, created_at)`
- `trades`
  - 关键字段：`trade_id`（PK）、`order_id`、`exchange_trade_id`（UNIQUE per exchange）、`price`、`qty`、`fee`、`ts`
  - 索引：`(order_id)`、`(ts)`、`(symbol, ts)`

### 2.2 仓位/账户（core）
- `positions`（投影，可重建）
  - 关键字段：`account_id`、`symbol`、`qty`、`avg_price`、`updated_at`
  - UNIQUE：`(account_id, symbol)`
- `accounts_snapshot`（快照，可选）
  - 关键字段：`account_id`、`balance`、`available`、`ts`

### 2.3 账本（ledger）
- `ledger_entries`（不可变流水）
  - 关键字段：`entry_id`（PK）、`account_id`、`currency`、`delta`、`balance_after`、`ref_type`、`ref_id`、`ts`
  - 索引：`(account_id, ts)`、`(ref_type, ref_id)`
  - 约束：同 `ref_type+ref_id` 必须幂等（可做 UNIQUE）

### 2.4 事件与审计
- `event_store`（或 outbox）
  - `event_id`（PK）、`topic`、`schema_version`、`event_type`、`payload_json`、`trace_id`、`created_at`
  - 索引：`(topic, created_at)`、`(trace_id)`、`(event_type, created_at)`
- `audit_logs`
  - 操作审计：who/what/when/where（IP/UA）+ trace_id

### 2.5 风控
- `risk_events`
  - `risk_event_id`、`rule_id`、`decision`（ALLOW/DENY）、`reason`、`trace_id`、`created_at`

---

## 3. 事务与一致性建议

- 订单创建 + 事件写入：推荐 Outbox 模式（同事务落库）
- ledger_entries：必须保证顺序性（按 account_id + ts 或序列号）
- positions：作为投影，允许延迟一致，但必须可重建

---

## 4. Flyway 迁移策略（Gate A 文档冻结）

- `V1__init.sql`：建表与关键约束
- 每次变更必须：
  - 新增迁移脚本（不修改历史脚本）
  - 更新 `docs/DECISIONS.md`（如影响契约/口径）

