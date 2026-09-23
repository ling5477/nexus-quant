# GateAUDIT API evidence

Current API ownership 由 Java backend 与 [API contract](../../current/API.md) 持有。Authenticated user/account/tenant scope、Risk、Execution、Order、Trade、Ledger、research/backtest 与 operational review 各自遵守既有 owner；客户端不能提交或覆盖 server-owned authority、actor、tenant、requestId 或 traceId。

## Accepted API controls

- Trading mutation 必须经过 canonical Risk / Execution path；frontend、Python、AI、DH 或 archive 文档均无直接 mutation authority。
- Mutation 采用稳定 idempotency identity、状态迁移与并发冲突处理；UNKNOWN external result 走 query/reconciliation/recovery，不盲目重试不可撤销动作。
- `Idempotency-Key`、expected version、canonical request hash 与事务边界保护重复执行；幂等 replay 不追加第二份 accepted side effect。
- Stable error identity 使用 `errorId → errorKey → code → HTTP category → UNKNOWN_ERROR` 的确定性 precedence。
- `NQ-TRD-1001 / ORDER_VERSION_CONFLICT` 保留 typed identity，同时兼容 legacy `STATE_CONFLICT`；未知错误保留 code/traceId。
- Error UX 不直接把 backend message 当主展示，不对 mutation/version conflict 自动 retry。
- auth/trading/research API 边界互不混淆；research/backtest 接口不产生真实交易副作用。

Frontend/error technical acceptance 见 [Error Catalog verification](../../error-catalog/VERIFICATION.md)，pair=`1b4c87129f2a79e13e379aa56501042ddd5bd42f / 35684433673`。Phase7-D 不修改 controller、API schema、idempotency contract、error catalog 或 runtime behavior。

## Safety boundary

LIVE=`DISABLED`，real provider/private trading=`NOT_IMPLEMENTED`。本 archive 不授权新的 LIVE endpoint、真实 PLACE/CANCEL、transfer/withdraw、credential API、generic private endpoint、scheduler mutation 或 production request。API 文档与 CI 通过均不能替代用户明确授权和 current runtime authority。
