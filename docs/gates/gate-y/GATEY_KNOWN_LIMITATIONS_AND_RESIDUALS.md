# GateY Known Limitations and Residuals

## P0 / P1

- P0：0。
- P1：0。唯一 pilot 的 exactly-once、reconciliation、ledger、terminal close、kill 与 LIVE=false 已接受。

## P2 residual

### `ORDER_VENUE_IDENTITY_MODEL_CONSISTENCY_RESIDUAL`

当前 `Order.externalOrderId=NULL`。同时 Receipt=`QUERY_CONFIRMED`，Trade 已持久化 venue identity，Order/Trade/Ledger reconciliation 已通过，因此它不是 GateY freeze blocker。

本 freeze 禁止修改订单事实或代码来清零字段。后续 `NQ-FULL-REPOSITORY-AUDIT-AND-CONSOLIDATION` 应检查 Order、ExecutionReceipt、Trade 三者的 venue identity ownership、查询与持久化一致性，并决定是否需要 forward-only 模型收口。

## Scope limitations

GateY 未证明多订单稳定性、多账户、多交易所、market order、合约/杠杆、HA、长期 soak、自动策略 LIVE、AI/DH execution、transfer 或 withdraw。Pilot release 不得作为这些能力的生产准入依据。

## Historical failures and immutability

全部 BLOCKED / FAIL / remediation、V43～V46、credential correction、trusted bootstrap、release reproducibility、non-web security、operator authority、lease recovery、canonical legacy bridge 与 reconciliation 历史均保存在 `source/task-evidence/**`。Freeze/tag 后不得重写为全部首轮成功；只能使用 forward addendum、hotfix 或 superseding tag。
