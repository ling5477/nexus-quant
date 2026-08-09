# GateW Backend, DB and Migration Evidence Summary

GateW 自 GateV frozen baseline 至 Attempt-13 acceptance 的代码范围包含 backend 内部 capability/guard、private read-only
transport、venue-rule facts、diagnostic preview/reconciliation/risk preflight、durable kill switch 与 soak runtime support。

## Schema

- `V34__gate_w3_venue_rule_facts.sql`：为 allowlisted venue-rule facts 建立 forward-only schema；包含项目要求的中文
  table/column comments。
- `V35__gate_w4_durable_kill_switch.sql`：建立 durable kill-switch state；状态与安全边界由 migration contract test 约束。
- 历史 migration 未修改；credential、token、cookie、signature 与 raw provider response 不进入 schema。

## Backend Boundary

GateW-1/2 通过 typed capability 与 endpoint guard 限制 private read-only surface；GateW-3 只执行 bounded local read、pure
comparator/evaluator 与 public venue metadata facts；GateW-4 将 kill switch、restore、incident 和 evidence binding 固化为
fail-closed operational safety。

主要 acceptance heads 为 GateW-1 `31c8171d...`、GateW-2 `6543e096...`、GateW-3 `178b4951...`、GateW-4 `07b94f89...`，对应
exact-head CI 均成功。完整 commit/CI/task evidence 见 [matrix](GATEW_BATCH_1_4_EVIDENCE_MATRIX.md)
与 [task evidence index](source/task-evidence/README.md)。

本 summary 不宣称 backend 已具备真实下单、撤单、转账、提现、LIVE 或 private trading authorization；这些能力在 GateW profile
下保持不可达或未实现。
