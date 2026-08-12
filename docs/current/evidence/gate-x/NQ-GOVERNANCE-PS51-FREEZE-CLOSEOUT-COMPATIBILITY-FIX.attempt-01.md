# NQ-GOVERNANCE-PS51-FREEZE-CLOSEOUT-COMPATIBILITY-FIX — Attempt 01

## Task classification

- 任务归属：NQ-only。
- 任务类型：`GOVERNANCE_COMPATIBILITY_FIX / POWERSHELL_5_1 / REGRESSION_TEST / HIGH_RISK_GOVERNANCE`。
- 执行状态：`PASS / PS51_FREEZE_CLOSEOUT_COMPATIBILITY_FIXED / GOVERNANCE_SEMANTICS_UNCHANGED / CROSS_VERSION_REGRESSION_GREEN / READY_TO_COMMIT`（通过 / PowerShell 5.1 freeze closeout 兼容性已修复 / 治理语义未改变 / 跨版本回归全绿 / 可进入提交前复核）。
- 本轮只修 shared governance library 的运行时兼容性；未执行 GateX archive、freeze commit、tag、post-tag verification 或 GateY 初始化。

## Starting HEAD

| 项目 | 事实 |
| --- | --- |
| Branch | `dev` |
| Starting HEAD | `9848ce24bf565d05d8cfdc7a248c3c0d98c68be8` |
| `origin/dev` after fetch | `9848ce24bf565d05d8cfdc7a248c3c0d98c68be8` |
| Worktree / staged before fix | clean / empty |
| Local tag `nq-gatex-freeze` | `NOT_EXISTS` |
| Remote tag `nq-gatex-freeze` | `NOT_EXISTS` |

## Failure reproduction / baseline

Windows PowerShell 5.1 执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass `
  -File scripts/docs/check-current-authority.ps1
```

稳定返回：

```text
The variable '$gateMatch' cannot be retrieved because it has not been set.
FullyQualifiedErrorId=VariableIsUndefined
exit=1
```

捕获到的 script stack 精确指向：

```text
Test-GovernanceNextActionForWorkBatch
scripts/docs/governance-workflow-lib.ps1:181
check-current-authority.ps1:413
```

同一 HEAD 使用 PowerShell 7 执行：

```powershell
pwsh -NoProfile -File scripts/docs/check-current-authority.ps1
```

基线为：

```text
AUTHORITY_CHECK errors=0
PASS / CURRENT_AUTHORITY_CONSISTENT
exit=0
```

因此问题满足 `PS5.1=FAIL / PS7=PASS` 的可复现前置，不是 current authority 字段缺失，也不是 issue 已被等价修复。

## Root cause

`Test-GovernanceNextActionForWorkBatch` 在 generic `FREEZE_CLOSEOUT` 条件分支中首次赋值 `$gateMatch`。Windows PowerShell 5.1 在调用方和 shared library 都启用 `Set-StrictMode -Version Latest` 时，把该条件分支内的首次赋值路径处理为未定义变量读取并抛出 `VariableIsUndefined`；PowerShell 7 不触发该运行时差异。

故障发生在 shared compatibility implementation，不在 `check-current-authority.ps1`、`governance-workflow-contract.json`、archive manifest 或 release checker 语义中。

## Exact compatibility fix

在现有 regex 匹配前显式建立局部变量：

```powershell
$gateMatch = $null
$gateMatch = [regex]::Match($WorkBatch, '^(?<gate>Gate[A-Z0-9]+)-')
```

保留原有显式成功判断、Gate scope 提取、ordinal action 比较和 fail-closed 返回。未关闭或降低 StrictMode，未吞异常，未增加 PS5.1 bypass，未硬编码 GateX/GateW/GateY，未修改 regex，未放宽 action 或 lifecycle token。

## Governance semantics before / after

| 语义 | Before | After |
| --- | --- | --- |
| `ACCEPTED|CI_GREEN|FREEZE_READY → FREEZE_CLOSEOUT` | contract 已存在；PS5.1 运行时异常 | contract 不变；PS5.1/PS7 都可执行 |
| Gate scope | 从 `WorkBatch` 的 `Gate*` 前缀精确提取 | 不变 |
| Action case | ordinal、大小写敏感 | 不变 |
| Unknown/suffix/cross-Gate | fail closed | 不变 |
| GateW strict legacy mapping | exact mapping + authority requirements | 不变 |
| StrictMode | `Latest` | `Latest` |

Governance semantics changed：`NO`。StrictMode changed：`NO`。

## Cross-version matrix

Windows PowerShell 5.1 与 PowerShell 7 对同一 14-case matrix 的逐行 JSON 输出完全一致：

| Case | Action type | Valid |
| --- | --- | --- |
| GateX / `GateX-5` / canonical closeout | `FREEZE_CLOSEOUT` | `true` |
| GateW / `GateW-4` / canonical closeout | `FREEZE_CLOSEOUT` | `true` |
| GateY / `GateY-9` / canonical closeout | `FREEZE_CLOSEOUT` | `true` |
| GateX batch + GateW action | `FREEZE_CLOSEOUT` | `false` |
| lowercase action | `UNKNOWN` | `false` |
| invalid suffix | `UNKNOWN` | `false` |
| batch name embedded in action | `UNKNOWN` | `false` |
| unknown action | `UNKNOWN` | `false` |
| `NOT_STARTED` | `FREEZE_CLOSEOUT` | `false` |
| `IMPLEMENTED|PENDING_REVIEW` | `FREEZE_CLOSEOUT` | `false` |
| `REVIEW_ACCEPTED|READY_TO_COMMIT` | `FREEZE_CLOSEOUT` | `false` |
| `COMMITTED|CI_PENDING` | `FREEZE_CLOSEOUT` | `false` |
| `BLOCKED` | `FREEZE_CLOSEOUT` | `false` |
| GateW legacy `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION` + exact authority context | `FREEZE_CLOSEOUT` | `true` |

```text
CROSS_VERSION_PARITY=True
PS51_EXIT=0
PS7_EXIT=0
CASES=14
```

现有 repository regression 还对 GateX/GateW/GateY 三个正例和五状态 × 三 Gate 的 15 个负例执行断言，并验证非法 suffix、lowercase 和 batch-embedded action 为 `UNKNOWN`。

## Regression results

| Suite | Windows PowerShell 5.1 | PowerShell 7 |
| --- | --- | --- |
| `test-current-authority-next-action.ps1` | `PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION`，exit 0，181 lines | 同左，exit 0，181 lines |
| `test-governance-workflow-lifecycle.ps1` | `PASS / GOVERNANCE_LIFECYCLE_REGRESSION`，`PASS / TASK_EVIDENCE_POLICY_VALID`，exit 0，279 lines | 同左，exit 0，279 lines |
| `test-gate-archive-manifest.ps1` | `PASS / GATE_ARCHIVE_MANIFEST_REGRESSION`，`PASS / TASK_EVIDENCE_POLICY_VALID`，exit 0，29 lines | 同左，exit 0，29 lines |
| `check-current-authority.ps1` | `errors=0 / CURRENT_AUTHORITY_CONSISTENT`，exit 0 | `errors=0 / CURRENT_AUTHORITY_CONSISTENT`，exit 0 |

## Gate-specific patch audit

执行：

```powershell
rg -n 'GATEX|NQ-GATEX|StartsWith.*GATEX|GateX.*FREEZE' scripts/docs --glob '*.ps1'
```

命中均位于 `test-governance-workflow-lifecycle.ps1` 或 `test-current-authority-next-action.ps1` 的既有 fixture。生产 shared library 没有新增 GateX-specific `if`、`switch`、allowlist 或 bypass。结论：`PASS / NO_GATE_SPECIFIC_COMPATIBILITY_PATCH`。

## Archive / release checker impact

- `governance-workflow-contract.json`：未修改。
- `gate-archive-manifest.json`：未修改。
- `check-gate-archive.ps1`：未修改。
- `check-gate-release.ps1`：未修改。
- Lifecycle regression 中 release annotated positive 仍为 `PASS / GATE_RELEASE_VALID`。
- 独立 archive manifest regression 的 pre-tag/post-tag、delegated release positive/negative fixtures 在 PS5.1/PS7 均通过。

## GateX authority / freeze boundary after fix

Current authority 保持：

```text
active_gate=GateX
active_gate_status=IN_PROGRESS|NOT_FROZEN
accepted_batch=GateX-5
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateX-FREEZE
work_batch_status=ACCEPTED|CI_GREEN|FREEZE_READY
next_action=NQ-GATEX-FREEZE-CLOSEOUT
live=DISABLED
shadow_trading=NOT_ENABLED
```

Freeze/tag state：GateX archive 未创建，freeze commit 未创建，`nq-gatex-freeze` 本地/远端均不存在，GateY 未初始化。

## Files changed

- 修改：`scripts/docs/governance-workflow-lib.ps1`，仅显式初始化 `$gateMatch` 并增加兼容性 Why 注释。
- 新增：`docs/current/evidence/gate-x/NQ-GOVERNANCE-PS51-FREEZE-CLOSEOUT-COMPATIBILITY-FIX.attempt-01.md`。
- 未修改现有测试：两份既有治理回归已完整覆盖要求，不为相同断言制造重复测试。
- 未修改 `TESTING.md` / `WORKLOG.md`：本 attempt evidence 已提供完整、可归档的高风险治理修复记录，避免重复账本 churn。

## Findings

### P0

- 0。

### P1

- 0。`PS51_FREEZE_CLOSEOUT_VARIABLE_UNDEFINED` 已由 shared compatibility fix 与双引擎回归关闭。

### P2

1. `PRODUCTION_LOCK_WINDOW_NOT_MEASURED`：GateX 既有 non-LIVE deployment residual，本修复未触碰。
2. `FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED`：GateX 既有 filesystem residual，本修复未触碰。

### P3

- 0。

## Side-effect and prohibited-scope audit

- backend/frontend/research/migration/`.github` 修改：0。
- credential/private exchange/network/order/trading/LIVE side effect：0。
- Shadow runner/scheduler/GateY/AI/DH runtime：未启动、未修改。
- GateX archive、manifest override、PreTag、freeze commit、tag、post-tag verification：未执行。

## Rollback / commit recommendation / next action

- Rollback：从 `scripts/docs/governance-workflow-lib.ps1` 删除 `$gateMatch = $null` 及其相邻兼容性注释，并删除本 evidence；这会恢复 PS5.1 的已知失败，因此只用于撤销本未提交变更。
- Commit recommendation：`fix(governance): restore PowerShell 5.1 freeze action compatibility`。
- 唯一下一动作：`NQ-GATEX-FREEZE-CLOSEOUT`。

## Final decision

```text
PASS /
PS51_FREEZE_CLOSEOUT_COMPATIBILITY_FIXED /
GOVERNANCE_SEMANTICS_UNCHANGED /
CROSS_VERSION_REGRESSION_GREEN /
READY_TO_COMMIT
```
