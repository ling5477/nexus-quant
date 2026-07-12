# GateV API Evidence Summary

GateV-2 接受的 HTTP surface 固定为 3 个 GET 与 4 个 POST：case list、case detail、event list，以及 `acknowledge`、`escalate`、`resolve`、`close`。不存在 approve、authorize、execute、trade、delete、reopen 或任意 case-create endpoint。

API 使用 authenticated `ADMIN / OPERATOR`。`OPERATOR` 只访问本人 owner scope；`ADMIN` 仅能在固定 `NQ_LOCAL` tenant 内跨 owner 操作。客户端不能覆盖 tenant；跨 scope 以 not-found 方式 fail-closed，避免 ID 枚举。

Mutation 使用 `Idempotency-Key`、`expectedVersion` 与受限 reason。相同 key/hash replay 返回首次结果；key 复用不同 payload、version conflict、非法 state transition 或 evidence unavailable 均返回明确的 409/422 类错误。accepted transition 追加 durable event；拒绝与冲突进入脱敏 operational audit，不伪造 state event。

## 受控错误语义

- 未认证与无角色访问分别 fail-closed 为 401/403。
- 跨 owner/case scope 返回 404，避免资源枚举。
- version/state/idempotency conflict 返回 409，evidence unavailable 返回 422。

Controller/service/repository tests 覆盖 RBAC、owner scope、idempotency、optimistic locking、audit 与错误映射。所有 response 与 UI 语义持续声明 `notTradingAuthorization=true`、`liveDisabled=true`；API 不修改 Paper/Shadow、account、order、ledger、risk 或 strategy facts。
