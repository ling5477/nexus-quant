# NQ-GATEW-4 Blocker-1 Kill Switch Remediation Review Attempt 01

## Review target

- Task：`NQ-GATEW-4-BLOCKER-1-KILL-SWITCH-DURABILITY-AND-STOP-PROPAGATION-REMEDIATION`。
- 类型：`SECURITY_REMEDIATION / RISK_MAINLINE_CHANGE / FLYWAY_MIGRATION_CONDITIONAL`。
- 唯一目标：关闭 `KILL_SWITCH_DURABILITY_NOT_PROVEN`；不继续 restore、incident、soak、human-review evidence 或 freeze。
- Starting HEAD：`d006417442080b2851dde1f2e05faf05eb5fe028`，与 `origin/dev` 一致。
- Starting exact-head CI：`NQ CI Baseline / 29332758765 / completed / success / 10 jobs / bad=0`。

## Authority baseline

`accepted_batch=GateW-3 / ACCEPTED|CI_GREEN`，`work_batch=GateW-4 / NOT_STARTED`，`next_action=NQ-GATEW-4-IMPLEMENTATION`，`live=DISABLED`。本 review 不改变这些事实。

## Existing capability and conflict review

| Component | Current state source | Default state | Restart behavior | Failure behavior | Mutation surface | Audit behavior | Credential access | Network access | Required remediation |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `KillSwitchService`（修复前） | 进程内 `AtomicBoolean` | `false`，即未熔断 | 重启丢失 | 无 repository failure 语义 | public `enable()` / `disable()` | 无 | 无 | 无 | durable snapshot、默认 ENGAGED、fail-closed、移除 release surface |
| `KillSwitchRiskRule`（修复前） | `isEnabled()` boolean | 默认通过该规则 | 随进程重置 | 无 missing/error/unknown 语义 | read-only evaluate | 复用下游 risk result | 无 | 无 | 改读 durable snapshot，非显式 DISENGAGED 全部拒绝 |
| `TradingRuntimeConfiguration`（修复前） | `new KillSwitchService()` | 未熔断 | 每次装配重置 | 无持久化 fallback | 创建进程对象 | 无 | 无 | 无 | 强制注入 durable repository；无 in-memory fallback |
| GateW private probe（修复前） | 无 kill switch dependency | 可进入 account/credential path | 无 stop state | 无 kill-switch fail-closed | 人工只读 probe | observation only | 会进入 credential executor | 会进入 private read transport | 在 account、credential、request/signature/transport 前检查 snapshot |
| `emergency_stop_events` | SIM/Paper run 事件 | 非 global current state | 可持久 | 仅 Paper stop 语义 | 包含 resolve lifecycle | Paper event history | 无 | 无 | 不复用；scope 与 release 语义不等价 |
| 通用 `audit_logs` / `event_store` | generic append facts | 无 kill-switch seed | 可持久 | 无 current-state/version 原子合同 | generic append | 有，但不能表达 scope/version 唯一性 | 无 | 无 | 新增最小专用 current-state + append-only event 模型 |

结论：仓库内不存在等价但未装配的 durable global kill switch；未触发 `EXISTING_DURABLE_KILL_SWITCH_CONFLICT`。V34 是修复前最高 migration，因此 next version 为 V35，不是预先硬编码版本。

## Accepted design

- `kill_switch_states` 保存唯一 `GLOBAL_TRADING` current state，seed 为 `ENGAGED / version=1`。
- `kill_switch_events` 保存 append-only 状态变化；`UNIQUE(scope,state_version)`，FK 使用 `ON DELETE RESTRICT`。
- snapshot 包含 `scope/status/version/reasonCode/source/updatedAt/observedAt/traceId`。
- `ENGAGED`、`UNKNOWN`、missing、读取/解析失败、缺失或未来 timestamp 全部 `BLOCKED`；只有显式 `DISENGAGED` 可继续下一只读检查，且不构成交易授权。
- production application surface 只有 `snapshot()` 与 `engage(...)`；不实现 `disable/disengage/release/reset/clear`。
- private probe 的 stop check 必须先于 account lookup、credential resolution、request/signature 与 transport。

## Findings

### P0

- 无。

### P1

- 原始 P1：`KILL_SWITCH_DURABILITY_NOT_PROVEN`；本 review 接受上述最小 remediation 设计，等待 implementation/conformance 证明关闭。

### P2

- 无。

### P3

- 无。

## Boundary confirmation

不新增 Controller、HTTP mutation、scheduler、runner、真实 provider、credential 输入、OKX real call、order/cancel/transfer/withdraw、ledger/account/position mutation、LIVE enable 或 human approval/release lifecycle。

## Decision

`PASS / REMEDIATION_DESIGN_ACCEPTED / IMPLEMENTATION_AUTHORIZED`（通过 / 修复设计已接受 / 授权本 blocker 的最小实现）。
