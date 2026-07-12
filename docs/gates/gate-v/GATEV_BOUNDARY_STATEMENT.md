# GateV Boundary Statement

GateV 只冻结本地诊断证据、durable 人工复核、受控只读调度与 review workbench 的实现证据。case state 和 operator action 表示“已查看、已升级、已处理、已关闭”本地复核事项，不表示交易批准、风险批准、上线批准或执行授权。

允许的 operator actions 仅为：`acknowledge`、`escalate`、`resolve`、`close`。禁止把任何状态映射为 `APPROVED_FOR_TRADING`、`TRADE_AUTHORIZED`、`LIVE_READY`、`CAN_TRADE` 或 `ORDER_APPROVED`。

## 数据与权限边界

- durable facts 只保存脱敏 evidence anchor 与人工复核事件。
- `OPERATOR` 受 owner scope 限制，`ADMIN` 仍受固定 tenant 限制。
- idempotency、version 与 audit 只保证本地 lifecycle 一致性，不产生执行许可。

GateV 不代表 LIVE enabled、Shadow trading enabled、trading authorization、real provider implemented、private trading implemented、real permission probe implemented，也不允许真实订单、撤单、转账或提现。

GateV 不启动 AI runtime，不集成 DH runtime，不启动 Integration runtime；不表示 Python ML ready 或 Python live execution ready。Python manifest preview 明确为 `No-file residual / NOT IMPLEMENTED`。

Scheduler 默认关闭且只读；API/workbench 不修改 strategy publish、evaluation result、Paper/Shadow run、risk decision、account、order 或 ledger。NQ-only 归档不声明或修改 DH current authority。

当前 GateV 仍为 `IN_PROGRESS|NOT_FROZEN`，`nq-gatev-freeze` 仍不存在。本声明不能替代后续 closeout review、archive commit exact-HEAD CI 或独立 tag 操作。
