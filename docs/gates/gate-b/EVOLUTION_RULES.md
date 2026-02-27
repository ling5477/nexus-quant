# Gate B EVOLUTION RULES（强约束：写代码时必须遵守）

---

## 1. Contracts（兼容性）

1) 只允许新增消息类型/新增字段；禁止重命名/删除/改语义
2) 新增字段必须可选，并在 CONTRACTS.md 写明默认行为
3) 任何破坏性变更必须被拒绝（除非新开 Gate 并冻结迁移方案）

---

## 2. State Machine（状态机）

1) 订单状态变更必须通过状态机 API；禁止直接改 DB 状态
2) 非法迁移：抛错 + audit_logs 记录 +（可选）risk_events
3) 单测必须覆盖：
    - 全部合法迁移
    - 至少 5 个非法迁移用例

---

## 3. Idempotency（幂等）

1) client_order_id 必须贯穿入口、命令、事件、DB
2) orders 表必须有 UNIQUE 约束（建议 tenant_id + client_order_id）
3) at-least-once 重试不允许产生重复副作用（重复 trade/重复 ledger entries）

---

## 4. Ledger（账本）

1) 任何成交必须触发记账（同步或异步，但最终一致）
2) 记账必须平衡校验（按币种），不平衡必须失败可追溯
3) 禁止绕过 ledger 直接写 positions/account_snapshots（除非作为 ledger 的投影结果）

---

## 5. Observability（可观测）

1) traceId 必须贯穿：HTTP/任务/事件/日志/DB（至少可关联）
2) 关键动作必须写 audit_logs：下单、风控、成交、记账、失败

---

## 6. Scope Control（小步可审查）

1) 每个 PR 只实现一个闭环切片（例如：下单+幂等；或撮合+成交；或记账+校验）
2) 所有 PR 必须附带：
    - 验证命令
    - 关键表数据检查点
    - 失败用例（至少 1 个）

---

## 7. JDBC 参数绑定规范（防踩坑）

- `payload_json` / `detail_json` / `ledger_events.payload_json` 写入统一使用 `CAST(? AS jsonb)`。
- 所有 `TIMESTAMPTZ` 入参统一使用 `Timestamp.from(instant)` 后再绑定。
- 上述规则适用于新增 JDBC repository，避免 PG 驱动类型推断导致运行态失败。

---

## 8. 禁止事项（高压线）

- adapter 不得直接写 ledger/positions
- risk 不得直接下单（只能给决策）
- core 不得依赖 okx/binance 具体实现（只依赖 adapter-api）