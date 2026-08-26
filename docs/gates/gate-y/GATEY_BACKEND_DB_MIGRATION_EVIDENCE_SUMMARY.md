# GateY Backend, DB and Migration Evidence Summary

## Backend facts

GateY 建立 LiveSession/approval/risk、ExecutionIntent/Receipt、typed OKX Spot LIMIT provider contract、exact pilot binding、operator authority、lease、query-only reconciliation 与 durable-first final close。`nq-api` 不写 SQL，`nq-core` 不依赖 JDBC，JDBC/PostgreSQL 实现位于 infra 边界；worker 不拥有策略准出、风险规则或 credential lifecycle。

## V39–V46

GateY 通过 forward-only migration 从 V39 推进到 V46，覆盖 control-plane facts、pilot scope/prerequisite、venue/order identity、operator authority、lease 与 final recovery 所需约束。历史 migration 未修改；新增表/字段使用中文业务注释，敏感 material 不进入 JSON/DB evidence。

V43/V44/V45/V46 的 implementation、review、deployment、blocked/fail 与 remediation 细节在 `source/task-evidence/**` 原样保留。本 freeze 不运行 migration，不修改 schema，不写生产数据库。

## Transaction, idempotency and reconciliation

PLACE 前以 durable authority/lease/scope fail closed；PLACE 后状态不确定时只允许 query-by-clientOrderId，禁止 blind PLACE retry。最终恢复使用合法 `SEND_STARTED → UNKNOWN → RECONCILED`，receipt/order/trade/ledger 同步由事务与 CAS/版本保护；durable facts 完整后跳过重复 venue query，只终态化 session/lease。

## Residual

`Order.externalOrderId=NULL` 保留为 `P2 / ORDER_VENUE_IDENTITY_MODEL_CONSISTENCY_RESIDUAL`。Receipt=`QUERY_CONFIRMED`，Trade 已持久化 venue identity，reconciliation 已通过；本 freeze 不修改订单事实或代码。后续全仓审计检查 Order/ExecutionReceipt/Trade 的 venue identity ownership 是否需要统一。
