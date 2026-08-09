# GateW API Evidence Summary

GateW 没有新增正式 `/api/**` public HTTP endpoint。自 GateV frozen baseline 到 Attempt-13 acceptance，`backend/nq-api`
变更计数为 0；GateW 的 read-only probe、preview、reconciliation、risk preflight 与 soak control 均保持在
internal/adapter/operations 边界。

GateW-2 接受的是两个 typed private read-only diagnostic operation，不是通用 raw path，也不暴露 credential、header、signature
或 provider raw response。GateW-3 preview/reconciliation/risk preflight 不通过 HTTP API 触发交易链，也不构造可提交订单的应用命令。

对外能力边界继续由现有 [current API authority](../../current/API.md) 描述，但该 current 文档不决定 GateW 状态。本 archive
独立记录：GateW API delta 为 `NONE`，真实订单、撤单、transfer、withdraw 与 LIVE authorization 均未新增。

CI 与 task evidence 证明的是 internal typed contracts 和 default-deny behavior；不得把 mock、fixture、local diagnostic 或
read-only soak 写成交易 API 已可用。

若 GateX 未来规划真实资金评审，必须先独立冻结 API/policy/authorization/idempotency/audit contract；GateW freeze 不提供该授权。
