# GateV Implementation Baseline

GateV 的目标是把 GateU 的本地只读诊断证据推进为 durable operator review 和受控只读自动化，同时保持交易授权边界关闭。

## 已完成能力

1. `Durable Review Fact Model`：独立 case/event facts、forward-only V33 migration、repository 与严格 state machine。
2. `Operator Review Lifecycle API`：3 个 GET、4 个有限 POST、RBAC、owner/tenant scope、optimistic locking、idempotency 与 audit。
3. `PostgreSQL Advisory Scheduler Lock`：transaction-level try lock、稳定 key mapping、独立 read-only transaction。
4. `Controlled Read-only Scheduler`：默认关闭、bounded local aggregate、lock contention safe skip、无业务写侧。
5. `Validation Review Workbench`：既有页面内 queue/detail/events/actions、保守错误态与 Playwright coverage。

状态机只允许 `OPEN -> ACKNOWLEDGED/ESCALATED`、`ACKNOWLEDGED -> ESCALATED/RESOLVED`、`ESCALATED -> RESOLVED`、`RESOLVED -> CLOSED`。operator action token 仅为 `acknowledge`、`escalate`、`resolve`、`close`。

这些事实不表示 LIVE、Shadow trading、real provider、private trading、permission probe、订单/撤单/转账/提现、AI、DH、Integration runtime 或 Python live execution 已准备。Python manifest preview 为 `No-file residual / NOT IMPLEMENTED`，不阻断本次 pre-tag closeout implementation。
