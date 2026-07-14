# NQ-GATEW-3 Risk Preflight Implementation Review — Attempt 01

## Review target

- 基线：`435c78fd06acba9cfe308404b1b49138bda373ff`。
- review scope：本轮 7 个新增 `nq-core` Java/test 文件与三份 risk-preflight evidence；current authority 只做 pre-commit projection。
- review 方法：重新检查实际 untracked paths、逐类 contract、compiled class zero-call、targeted/full Maven 与 forbidden-scope searches。

## Findings

### P0

- 无。

### P1

- 无。

### P2

- 无。

### P3

- review 中发现初版把多个不同 ACTIVE credential type 误判 conflict；根据现有 repository contract，已最小修复为“count/type 数量不一致或重复 type 才 conflict”，并新增两个不同 type PASS 与重复 type BLOCKED 回归。修复后 focused/targeted/full tests 全部通过。

## Conformance review

- 无 `PreTradeRiskService`、`RiskRuleRegistry`、`RiskContext`、`PlaceOrderCommand` 或 stateful rule execution。
- 无 public API、Spring bean 自动装配、network、repository、credential、write/audit/ledger/event dependency。
- preview BLOCKED/NOT_EVALUATED 与 reconciliation mismatch/stale/partial/future/NOT_EVALUATED 均 fail-closed。
- account/credential/marketdata 只表达 local metadata，不推断 remote permission、balance 或真实账户健康。
- UNKNOWN 与 NOT_EVALUATED 未降级；result constructor 固定 execution blocked 与 tradingAuthorized=false。
- existing `TradingPreflightReadinessService`、Controller/API、`nq-risk` production semantics 无 diff。

## Validation

- focused：31/31 PASS。
- required targeted reactor：23/23 modules SUCCESS，BUILD SUCCESS。
- full Maven：23/23 modules SUCCESS，BUILD SUCCESS。
- 运行环境：`CI=true`、`NQ_NO_OUTBOUND=true`、AI/DH/real exchange disabled；未访问 OKX。
- warnings：既有 SLF4J NOP、Mockito dynamic-agent/JDK future warning；非本轮失败。

## Boundary confirmation

`NO FULL PRETRADE RISK CHAIN / NO PLACE_ORDER_COMMAND / NO REAL RISK ALLOW / NO CONTROLLER / NO REST API / NO FRONTEND / NO MIGRATION / NO PERSISTENCE / NO SCHEDULER / NO NETWORK / NO CREDENTIAL MATERIAL / NO ORDER OR RISK MUTATION / NO LEDGER-AUDIT-EVENT WRITE / NO LIVE / NO SHADOW / NO DH / NO AI`。

## Decision

`PASS / GATEW_3_RISK_PREFLIGHT_ACCEPTED / READY_TO_COMMIT`（通过 / GateW-3 risk preflight 已接受 / 可进入提交前复核）。

Pre-commit authority 必须保持 accepted predecessor GateW-2，并投影为：

```text
work_batch=GateW-3
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-3-RISK-PREFLIGHT-COMMIT-AND-PUSH
```

该 decision 不表示已 commit/push、CI green、GateW-3 accepted、GateW frozen 或 GateW-4 started。
