# nexus-quant 契约规范（CONTRACTS）

> Archive Notice
> - 本文件是早期 Gate A 历史留档，不是当前阶段的 Source of Truth。
> - 当前阶段请优先阅读 `docs/current/*` 与 `docs/gates/gate-e/CONTRACTS.md`。
> - 若需查看 Gate A 冻结快照，请优先参考 `docs/gates/gate-a/CONTRACTS.md`。

> 项目：nexus-quant  
> 契约归口模块：`nq-contracts`（强制）  
> 版本：v1.0（Gate A）  
> 原则：契约先行；向后兼容；幂等与去重优先；traceId 全链路

---

## 1. 契约总览

### 1.1 契约类型
- HTTP 契约：auth/login 等
- 事件契约：Kafka topic / 内部事件总线
- 命令契约：下单/撤单等 command
- 快照契约：恢复/对账所需 snapshot（占位）
- 审计契约：关键操作审计记录（audit_logs）

### 1.2 命名规范
- Topic：`{domain}.{kind}.v{N}` 例：`order.event.v1`
- Event Type：`Domain.Action` 例：`Order.StatusChanged`
- 版本：Envelope 的 `version` 控制

---

## 2. traceId 规范（强制）

### 2.1 HTTP Header
- 网关生成/透传：`X-Trace-Id`
- 后端服务必须读取并写入 MDC
- 返回时也回传 `X-Trace-Id`

### 2.2 事件 Envelope
- Envelope 必带 `trace_id`
- 从 HTTP/策略/定时任务入口产生 trace_id

---

## 3. HTTP 契约（Gate A：Auth 最小可用）

> Gate A 只要求 auth/gateway “骨架可用”：能登录拿 JWT + 网关鉴权与 traceId 透传。  
> 权限细粒度与管理接口后置。

### 3.1 POST /auth/login
请求：
```json
{
  "username": "demo",
  "password": "******"
}
```

响应：
```json
{
  "access_token": "eyJhbGciOi...",
  "token_type": "Bearer",
  "expires_in": 3600
}
```

错误响应（统一结构建议）：
```json
{
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "invalid username or password",
  "trace_id": "trc-xxx"
}
```

### 3.2 JWT Claims（最小集合）
- `sub`：userId（字符串或数字字符串）
- `username`：用户名
- `roles`：角色列表（如 ["ADMIN"]）
- `iat` / `exp`：签发/过期时间（UTC）

---

## 4. 事件 Envelope（统一外壳）

### 4.1 字段定义（必须）
| Field | Type | Required | Notes |
|---|---|---|---|
| event_id | string(UUID) | Y | 全局唯一 |
| type | string | Y | 事件类型（如 Order.StatusChanged） |
| version | int | Y | 契约版本 |
| ts | string(ISO-8601) | Y | UTC 时间（Instant） |
| source | string | Y | 产生方（core/ledger/risk/adapter/...) |
| trace_id | string | Y | 全链路追踪 |
| key | string | Y | Kafka key（分区键） |
| payload | object | Y | 事件载荷 |

### 4.2 Envelope 示例
```json
{
  "event_id": "9f2d6c3b-0cbb-4b9f-a8c4-6a4c5e3c2a11",
  "type": "Order.StatusChanged",
  "version": 1,
  "ts": "2026-02-12T08:30:00Z",
  "source": "core",
  "trace_id": "trc-123",
  "key": "order:7c3d...",
  "payload": {}
}
```

---

## 5. Topic 规范（Gate A：先冻结，后接入）

### 5.1 Topic 列表（v1）
| Topic | Key | Producer | Consumer | Notes |
|---|---|---|---|---|
| order.command.v1 | order_id | strategy/api | core | 下单/撤单命令（Gate A 可占位） |
| order.event.v1 | order_id | core/adapter | replay/api | 订单状态事件 |
| trade.event.v1 | order_id | adapter | core/replay | 成交事件（最终事实） |
| position.event.v1 | account:symbol | core | api/replay | 可选 compact |
| ledger.event.v1 | account:currency | ledger | api/recon | 可选 compact |
| risk.event.v1 | scope_id | risk | api/replay | 风控事件 |
| audit.event.v1 | actor_id | api/auth | audit | 关键操作审计 |

> 说明：Gate A 中 adapter 还未实现，但 topic 必须先冻结并放在 `nq-contracts`。

### 5.2 分区键（Key）规则
- `order_id`：同一订单事件有序
- `account_id:symbol`：同一账户同一标的持仓更新有序
- `account_id:currency`：同一币种账本事件有序

---

## 6. Command 契约（OrderCommand）

### 6.1 PlaceOrderCommand
字段：
- order_id（系统生成）
- account_id
- symbol
- client_order_id（幂等键，必填）
- side（BUY/SELL）
- type（LIMIT/MARKET，Gate A 可先 LIMIT）
- price（市价可空）
- qty
- time_in_force（GTC/IOC/FOK，可选）
- meta（JSON：strategy_id 等）

示例：
```json
{
  "order_id": "7c3d...",
  "account_id": 1,
  "symbol": "BTC-USDT",
  "client_order_id": "cli-20260212-0001",
  "side": "BUY",
  "type": "LIMIT",
  "price": "48000.12",
  "qty": "0.01",
  "time_in_force": "GTC",
  "meta": { "strategy_id": "sma-1" }
}
```

### 6.2 CancelOrderCommand
字段：
- order_id 或 (account_id + client_order_id)
- reason（可选）

---

## 7. Event 契约（核心事件）

### 7.1 Order.StatusChanged
payload：
- order_id
- account_id
- client_order_id
- status（NEW/ACKED/...）
- ext_order_id（Gate B 之后）
- reason（REJECT/FAILED 时必填）
- ts

### 7.2 Trade.Filled
payload：
- trade_id（去重键，必须）
- order_id
- ext_trade_id（Gate B 之后）
- price / qty
- fee_amount / fee_currency
- liquidity（MAKER/TAKER，可选）
- ts

规则：
- Trade 为最终事实，可纠偏订单状态与账本/持仓

### 7.3 Position.Updated
payload：
- account_id
- symbol
- qty
- available_qty
- frozen_qty
- ts

### 7.4 Ledger.EntryCreated
payload：
- entry_id（去重键）
- account_id
- currency
- amount（建议：带符号；或 direction+abs）
- direction（DEBIT/CREDIT）
- ref_type（TRADE/FEE/TRANSFER/FREEZE/UNFREEZE）
- ref_id
- ts

### 7.5 Risk.Decision
payload：
- scope（ORDER/ACCOUNT/STRATEGY）
- scope_id
- decision（ALLOW/REJECT）
- reason_code
- severity
- ts

---

## 8. 幂等与去重规则（强制）

### 8.1 命令幂等
- 以 `(account_id, client_order_id)` 唯一约束作为硬幂等
- 重复 PlaceOrderCommand 行为必须定义：
  - 返回已存在 order_id 或返回错误（在 DECISIONS 记录）

### 8.2 事件去重
- Trade：以 `trade_id` 去重
- LedgerEntry：以 `entry_id` 去重
- 消费者必须幂等：重复消费不改变最终状态

---

## 9. 版本演进规则（向后兼容）

- 只允许加字段，不允许删字段/改语义
- 新字段必须可选并有默认行为
- 破坏性变更必须升级 `version` 并保留旧版解析器

---

## 10. Schema 文件（占位目录建议）
- `docs/schema/v1/order.command.schema.json`
- `docs/schema/v1/order.event.schema.json`
- `docs/schema/v1/trade.event.schema.json`
- `docs/schema/v1/ledger.event.schema.json`

---


---

## 附录：契约演进规则

事件契约的兼容/破坏性变更规则、双写/迁移建议见：`docs/EVOLUTION_RULES.md`。
