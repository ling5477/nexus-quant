# Gate B MODULES（模块职责与依赖）

---

## 1. 模块职责（Gate B 视角）

| 模块 | 职责 | 禁止事项 |
|---|---|---|
| nq-app | 启动与装配 | 不承载业务逻辑 |
| nq-contracts | 命令/事件/DTO | 禁止破坏兼容性 |
| nq-common | traceId、ErrorCode、数值策略 | 禁止写业务状态 |
| nq-core | 订单编排、状态机、幂等 | 不依赖具体 adapter 实现 |
| nq-risk | 风控规则与决策 | 不直接下单、不直接改订单状态 |
| nq-ledger | 记账、校验、回放 | 不直接调用 adapter |
| nq-infra | DB/Flyway/Repo 基础设施 | 不写领域逻辑 |
| nq-scheduler | 触发策略/撮合/对账 | 不实现业务规则 |
| nq-observability | trace/log/audit 辅助 | 不影响业务语义 |
| nq-adapter-api | 适配器接口 | 不落地具体交易所逻辑 |
| nq-adapter-okx/binance | 真实交易所适配（GateB 不接网） | GateB 不允许出网 |
| （推荐新增）nq-adapter-paper | 模拟适配器与撮合 | 不绕过 core/ledger 直接写 DB |

> TODO: 如果你决定不新增 `nq-adapter-paper`，在这里写清楚 paper 模式放在哪个模块，并说明隔离方式。

---

## 2. 依赖规则（必须遵守）
- nq-core 只能依赖：nq-contracts、nq-common、nq-infra（repo接口）、nq-adapter-api（接口）
- nq-risk 只能依赖：nq-common、nq-contracts、nq-infra（读取数据）
- nq-ledger 只能依赖：nq-common、nq-contracts、nq-infra
- adapter-* 不得依赖 core/ledger 的实现类（只通过接口或事件交互）

---

## 3. Gate B 关键接口（建议）
- OrderService（core）：place/cancel/transition/query
- RiskEngine（risk）：evaluate(orderIntent) -> decision
- AdapterClient（adapter-api）：sendOrder/cancelOrder
- LedgerService（ledger）：post(trade) -> result
- AuditService（observability）：record(action)

---

## 4. 包结构建议（占位）
> TODO: 当你开始实现时，把主要包/类名填在这里，方便 Code Review 对齐边界。