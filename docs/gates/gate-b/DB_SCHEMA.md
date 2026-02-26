# Gate B DB SCHEMA（增量变更说明）

> 基线：Gate A 已通过 Flyway `V1__init.sql` 落地核心表结构（PostgreSQL）。
> Gate B 在此基础上实现“模拟盘最小交易闭环”，原则是 **最小增量、强约束、可复盘**：
> - 幂等：`orders(account_id, client_order_id)` 唯一
> - 状态机：`orders.status` 仅允许合法迁移
> - 成交：`trades` 关联 `orders`
> - 记账：`ledger_entries` 使用 `idempotency_key` 去重；`ledger_events` 作为投影入口
> - 回放：`event_store` 记录 Topic / schema_version / event_type / payload_json / key_value / trace_id
> - 审计：`audit_logs` + `risk_events` 必须覆盖关键动作

---

## 1. Baseline（Gate A 已具备）

### 1.1 账户与运行
- `accounts(account_id, account_code, venue, status, ...)`
- `strategy_runs(run_id, strategy_id, account_id, status, started_at, ended_at, trace_id, ...)`

### 1.2 订单与成交
- `orders(order_id PK, account_id FK, strategy_run_id FK, symbol, client_order_id, side, type, price, qty, status, reason, trace_id, ...)`
  - ✅ `UNIQUE (account_id, client_order_id)`：幂等键
  - ✅ `idx_orders_status_created` / `idx_orders_trace_id`

- `trades(trade_id PK, order_id FK, account_id FK, symbol, exchange, exchange_trade_id, price, qty, fee, fee_currency, trace_id, ts, ...)`
  - ✅ `UNIQUE (exchange, exchange_trade_id)`：对接真实交易所时可用
  - ✅ `idx_trades_order_ts` / `idx_trades_trace_id`

### 1.3 持仓与账户快照
- `positions(account_id, symbol) UNIQUE` + qty/available/frozen/avg_price + trace_id
- `account_snapshots(account_id, currency, balance/available/frozen, ts, trace_id)`

### 1.4 账本与事件
- `ledger_entries(entry_id PK, account_id, currency, delta, balance_after, direction, ref_type, ref_id, idempotency_key, trace_id, ts, ...)`
  - ✅ `uq_ledger_entries_idempotency_key WHERE idempotency_key IS NOT NULL`：记账幂等
- `ledger_events(ledger_event_id PK, entry_id FK, event_type, payload_json, trace_id, ...)`
  - ✅ 作为账本事件投影入口（GateB 使用）

- `event_store(event_id PK, topic, schema_version, event_type, payload_json, key_value, trace_id, ...)`
  - ✅ 用于回放/复盘（建议所有命令/事件都写入）

### 1.5 风控与审计
- `risk_events(risk_event_id PK, rule_id, scope, scope_id, decision, reason, severity, trace_id, ...)`
- `audit_logs(domain, action, actor_id, trace_id, detail_json, ...)`

---

## 2. Gate B 语义约束（不改表也要“写死口径”）

### 2.1 orders：状态机与幂等
- 幂等键：`(account_id, client_order_id)`
- `orders.status`：仅允许状态机驱动写入（禁止直接 SQL 修改绕过）
- `orders.reason`：仅在 REJECTED/CANCELED/RISK_REJECTED 等需要解释的状态填充

> 建议 Gate B 订单状态值（字符串）：
- NEW
- RISK_PASSED / RISK_REJECTED
- SENT
- ACCEPTED（可选）
- PARTIALLY_FILLED
- FILLED（终态）
- CANCELED（终态）
- REJECTED（终态）

### 2.2 trades：成交唯一性与去重
Gate B（paper）不需要 `exchange_trade_id`，但仍建议：
- paper 成交也生成稳定 trade_id（可由系统生成）
- 若撮合任务重复执行，必须通过：
  - 订单状态检查（FILLED 不再生成）
  - 或 trade 生成策略幂等（例如 trade_id 可基于 order_id + sequence）

### 2.3 ledger_entries：记账幂等与平衡校验
- Gate B 记账幂等：使用 `ledger_entries.idempotency_key`
  - 推荐格式：`LEDGER:<ref_type>:<ref_id>:<currency>:<direction>:<seq>`
  - 最简可用：`LEDGER:TRADE:<trade_id>`（但会限制一笔成交多币种分录，建议包含 currency/seq）

- Gate B 平衡校验（逻辑层）：
  - 对于同一成交（trade_id）产生的多条 ledger_entries：
    - 按 currency 聚合后，DEBIT 总和 == CREDIT 总和（或 delta 总和 == 0，取决于你的 delta/direction 口径）
  - 校验失败必须写：
    - `risk_events(scope='LEDGER', scope_id=trade_id, decision='REJECT')`（示例）
    - `audit_logs(domain='LEDGER', action='POST_FAILED', ...)`

> 注意：当前 `ledger_events` 结构是“每条 entry 对应一条 ledger_event（entry_id FK）”，
> Gate B 允许先这样用；后续 GateC 可以演进为“一个 ledger_event 关联多条 entries”（需要表结构调整，留到 GateC）。

### 2.4 event_store：回放与复盘
- Gate B 建议所有命令/关键事件写入 `event_store`：
  - topic：使用 `TopicNames.*_V1`
  - schema_version：对齐 `EventEnvelope.version`
  - event_type：对齐 `EventEnvelope.type`
  - payload_json：存 EventEnvelope（推荐存 envelope 全量，便于回放）
  - key_value：对齐 `EventEnvelope.key`（建议 client_order_id）

---

## 3. Gate B 建议的增量变更（可选但很实用）

> 如果你希望 Gate B 完全“零 DDL”，下面内容可以先作为建议；
> 但我推荐至少加 2~3 个索引/约束，会大幅提升可观测与恢复效率。

### 3.1 建议新增索引（V2 migration）
1) `orders(strategy_run_id)` 索引（方便按 run 查单）
2) `trades(account_id, ts DESC)` 索引（方便账户维度复盘）
3) `ledger_entries(trace_id)` 索引（便于 trace 排查）
4) `ledger_entries(ref_type, ref_id, ts DESC)` 已有 ref 索引 ✅（够用）

### 3.2 建议新增 CHECK 约束（V2 migration）
- `orders.qty > 0`
- `trades.qty > 0`、`trades.price > 0`
- `ledger_entries.direction IN ('DEBIT','CREDIT')`（若你方向枚举固定）
- `orders.side IN ('BUY','SELL')`、`orders.type IN ('MARKET','LIMIT')`（可选）

> Gate B 允许先在代码层校验，GateC 再上更硬的 DB CHECK。

---

## 4. Gate B 数据写入“必须点”（验收对齐）

一次完整闭环后，至少应出现：

- `strategy_runs`：新增 1 条（trace_id 关联）
- `orders`：新增 1 条（含 client_order_id、status、trace_id）
- `risk_events`：至少 1 条（PASS/REJECT，含 trace_id）
- `trades`：>= 1 条（含 trace_id、ts）
- `ledger_entries`：>= 2 条（借贷分录，idempotency_key 非空）
- `ledger_events`：>= ledger_entries 条数（每 entry 一条投影事件）
- `positions`：发生更新（trace_id 记录）
- `audit_logs`：至少 3 条（下单/风控/成交或记账，含 trace_id）
- `event_store`：至少记录命令 + 关键事件（topic/event_type/key_value/trace_id）

---

## 5. 备注：与 CONTRACTS 对齐

- EventEnvelope JSON key 与表字段对齐建议：
  - `event_store.event_type` == `EventEnvelope.type`
  - `event_store.schema_version` == `EventEnvelope.version`
  - `event_store.key_value` == `EventEnvelope.key`
  - `event_store.trace_id` == `EventEnvelope.trace_id`
  - `event_store.topic` == `TopicNames.*_V1`

- orders/trades/ledger_entries/risk_events/audit_logs 都已含 `trace_id` ✅，满足 Gate B 全链路定位要求。