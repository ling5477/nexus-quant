# GateY API Evidence Summary

GateY 的控制面接口只接受 server-owned session、scope、authority、lease、risk、credential reference 与 durable execution facts。高风险动作由后端校验 identity、状态、版本、expiry、kill 与 exact scope；前端或调用方不能提交 raw credential、任意 private URL、任意 method/path 或绕过 typed operation policy。

## Accepted controls

- Exact session/scope/authority/lease identity 由后端绑定。
- PLACE 只允许 BUY LIMIT 与已批准 BTC-USDT scope。
- Mutation 不自动 retry；UNKNOWN 只允许 query-first recovery。
- CANCEL 不是强制 pilot 动作，最终实际计数为 0。

Minimal live pilot 由一次显式人工受控 controller invocation 完成。Freeze 后该入口不得再次执行 pilot：禁止 Attempt-02、第二 PLACE、PLACE retry、CANCEL、transfer、withdraw 或重新 DISENGAGE kill。Query-only recovery 只能消费已有 intent/clientOrderId/receipt/order facts，不能进入 `executeNewPilot()`。

错误路径保持 fail closed：UNKNOWN 只能 query，permission/IP/clock/rate/identity/scope/lease/authority 不一致均拒绝新 mutation。API、日志与 evidence 不暴露 SQL、异常栈、raw private response、credential、签名、cookie 或完整 durable clientOrderId。

GateY freeze 不代表通用交易 API、自动策略 LIVE、批量下单、多账户、多交易所、transfer/withdraw 或 arbitrary private endpoint 已授权。任何后续 API 演进必须来自全仓审计后的独立任务，并继续保留后端权限与风险校验。

## Validation boundary

API/controller 相关 regression 已进入 GateY minimal 100/100 与 exact-head CI。Freeze 本身不重新调用 controller，只运行静态/fixture/checker 验证。
