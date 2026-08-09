# NQ-GATEW-4 Blocker-1 Kill Switch Remediation Conformance Review Attempt 01

## Review target

对 Blocker-1 remediation 的真实 production/test/migration/docs diff 重新进行安全、持久化、stop propagation 与 forbidden-scope review。该 review 不接受 GateW-4，不继续 restore/incident/soak/human-review/freeze。

## Evidence checked

- immutable state/snapshot、service、repository port、JDBC adapter 与 V35 migration。
- `KillSwitchRiskRule`、`RiskRuleRegistry`、`PreTradeRiskService` regression。
- GateW private probe、credential executor 调用边界、private transport 调用边界与 Spring composition。
- required targeted/full Maven、fresh PostgreSQL/Flyway、两个独立 Spring context restart test。
- 工作区 scope、原始 BLOCKED evidence SHA、current authority 与禁止目录 diff。

## Conformance results

| Contract | Result | Evidence |
| --- | --- | --- |
| 默认 ENGAGED | PASS | V35 seed + migration contract + real PostgreSQL snapshot |
| 重启保持 ENGAGED | PASS | 两个独立 Spring context，同一 disposable PostgreSQL schema |
| missing/error/invalid/timestamp anomaly fail-closed | PASS | `KillSwitchServiceTest` 与 service exception mapping |
| 无 production `AtomicBoolean` state | PASS | production diff/static search |
| 无 production release/disengage | PASS | service public surface 仅 `snapshot/engage`；repository 仅 `findByScope/engage` |
| risk rule 使用 durable snapshot | PASS | ENGAGED/UNKNOWN/error/missing reject；DISENGAGED 仅该规则通过 |
| private probe stop-first | PASS | snapshot 在 account/credential/request/transport 前执行 |
| credential zero-call | PASS | ENGAGED/UNKNOWN/read failure 时 callback count=0 |
| network zero-call | PASS | ENGAGED/UNKNOWN/read failure 时 transport operations empty |
| production durable composition | PASS | Spring 注入 `KillSwitchStateRepository`，无 in-memory fallback |
| audit/event durability | PASS | state update + event append 同一 transaction；scope/version unique；no cascade delete |
| LIVE boundary | PASS | `live=DISABLED`，无 enable 或交易授权语义 |

## DDL review

总体结论：通过。

- P0/P1/P2/P3：均无。
- current-state PK/unique scope、status/version/text CHECK、中文 COMMENT 完整。
- `kill_switch_events` 只有 repository INSERT 路径，无 UPDATE/DELETE application surface；FK `ON DELETE RESTRICT`。
- migration 为修复前最高 V34 的 next version V35；未修改历史 migration，无 backfill 或大表锁表风险。

## Validation

- targeted reactor：23/23 modules `SUCCESS`，`BUILD SUCCESS`。
- `KillSwitchServiceTest 4/4`、`KillSwitchRiskRuleTest 2/2`、`PreTradeRiskServiceTest 5/5`。
- `OkxPrivateReadonlyProbeServiceTest 7/7`、`GateWOkxPrivateReadonlyConfigurationTest 5/5`、migration contract 1/1。
- dedicated PostgreSQL restart test：1/1 passed、0 skipped；V1→V35 成功，容器/schema 清理成功。
- full Maven：23/23 modules `SUCCESS`，`BUILD SUCCESS`。
- governance 与 staged-diff checks 在 commit 前执行；exact-head CI 在 push 后执行，因此此 evidence 不提前声称 CI green。

## Findings

### P0

- 无。

### P1

- 无；原 `KILL_SWITCH_DURABILITY_NOT_PROVEN` 在本 remediation 范围内已由 durable/restart/fail-closed/stop-first 证据关闭。

### P2

- 无。

### P3

- 既有 SLF4J NOP、Mockito dynamic-agent/JDK future warning；不由本 diff 引入，不阻断。

## Boundary confirmation

- 无 Controller/API、POST/PUT/PATCH/DELETE、scheduler/runner、adapter 修改、frontend、Python、scripts、deploy、CI 或 POM/package/lock diff。
- 无真实 credential、真实 OKX/network、order/cancel/transfer/withdraw、ledger/account/position mutation。
- 未实现 human approval、release/disengage、restore、incident、soak 或 GateW-4 freeze。
- Authority 继续为 GateW-3 accepted、GateW-4 NOT_STARTED、next action `NQ-GATEW-4-IMPLEMENTATION`。

## Decision

`PASS / KILL_SWITCH_DURABILITY_REMEDIATED / STOP_PROPAGATION_PROVEN / READY_TO_COMMIT`（通过 / 持久性 blocker 已修复 / 停止传播已证明 / 可进入提交前复核）。
