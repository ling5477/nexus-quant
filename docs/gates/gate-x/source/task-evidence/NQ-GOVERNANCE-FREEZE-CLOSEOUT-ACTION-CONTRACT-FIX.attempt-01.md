# NQ-GOVERNANCE-FREEZE-CLOSEOUT-ACTION-CONTRACT-FIX — attempt-01

## 结论

`PASS / GENERIC_FREEZE_CLOSEOUT_ACTION_CONTRACT_FIXED / GATEX_5_ACCEPTED / GATEX_FREEZE_AUTHORIZED / READY_TO_COMMIT`

本轮只修复通用 freeze closeout action contract，并完成 GateX-5 post-CI authority acceptance。GateX 仍为 `IN_PROGRESS / NOT FROZEN`；actual freeze、strict archive、tag、GateY、Shadow Run 启动、LIVE 与交易写侧均未执行。

## Root cause

- Canonical contract 已有 `ACCEPTED|CI_GREEN|FREEZE_READY` 状态，但只映射到 GateW 历史类型 `GATE_FREEZE_CLOSEOUT`。
- Parser 仅能精确识别 `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION`，且 strict family 把所有 `NQ-GATEW-FREEZE-CLOSEOUT*` 收拢为 GateW 特例；`NQ-GATEX-FREEZE-CLOSEOUT` 因此返回 `UNKNOWN`。
- `check-current-authority.ps1` 又将 `ACCEPTED|CI_GREEN|FREEZE_READY` 硬限制为 `GateW-ATTEMPT-13-168H-ACCEPTANCE`，导致其他 Gate 无法使用同构 lifecycle。
- 根因是通用 Gate-level closeout 语义缺失，属于 Case B；不是 GateX 文案或单一 fixture 命名错误。

## Existing action inventory

| Action family | 修复前结果 | 本轮处理 |
| --- | --- | --- |
| `IMPLEMENTATION` | 通用 suffix 与既有严格 action 可识别 | 保持不变并由全量回归覆盖 |
| `REVIEW` | 通用 review suffix 可识别 | 保持不变并由全量回归覆盖 |
| `COMMIT_AND_PUSH` | 通用 commit suffix 可识别 | 保持不变并由全量回归覆盖 |
| `BLOCKED` | 通用 blocked/unblock suffix 可识别 | 保持不变并由全量回归覆盖 |
| GateW legacy closeout | 仅 `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION`，类型为 `GATE_FREEZE_CLOSEOUT` | 精确兼容保留，类型统一为 `FREEZE_CLOSEOUT`，Attempt-13 safety context 不变 |
| Generic closeout | 不存在；GateX action=`UNKNOWN` | 新增 `NQ-GATE<代号>-FREEZE-CLOSEOUT` 通用严格规则 |

## Case B 与 canonical action

- 决策：`Case B / GENERIC ACTION MISSING`。
- Candidate type：`FREEZE_CLOSEOUT`。
- Canonical pattern：`(?-i:^NQ-GATE[A-Z0-9]+-FREEZE-CLOSEOUT$)`。
- Gate-level binding：shared library 从 `work_batch` 提取严格 `Gate<代号>` 前缀，要求 action 精确等于 `NQ-<GATE>-FREEZE-CLOSEOUT`；不依赖 GateX allowlist，也不接受小写或附加 suffix。
- Historical precedence：有 scoped exact mapping 时仍优先执行 scoped mapping；GateW Attempt-13 legacy action 必须满足原有 `attempt/attempt_status/production_soak/live/kill_switch/active_gate_status` safety facts。

## Generic mapping

```text
implementation
→ commit
→ exact-head CI
→ accepted
→ FREEZE_CLOSEOUT
→ frozen/tagged（不在本任务执行）
```

本轮只建立：

```text
ACCEPTED|CI_GREEN|FREEZE_READY
→ FREEZE_CLOSEOUT
```

未新增 `FREEZE_PENDING`、`PRE_FREEZE_READY`、`CLOSEOUT_PENDING` 或其他状态。

## Positive lifecycle matrix

| Gate | Work batch | Status | Action | Type | Mapping |
| --- | --- | --- | --- | --- | --- |
| GateX | `GateX-5` | `ACCEPTED|CI_GREEN|FREEZE_READY` | `NQ-GATEX-FREEZE-CLOSEOUT` | `FREEZE_CLOSEOUT` | `true` |
| GateW | `GateW-4` | `ACCEPTED|CI_GREEN|FREEZE_READY` | `NQ-GATEW-FREEZE-CLOSEOUT` | `FREEZE_CLOSEOUT` | `true` |
| GateY generic fixture | `GateY-9` | `ACCEPTED|CI_GREEN|FREEZE_READY` | `NQ-GATEY-FREEZE-CLOSEOUT` | `FREEZE_CLOSEOUT` | `true` |

## Negative lifecycle matrix

以下 5 个状态分别对 GateX、GateW、GateY 三组同构 action 执行，共 15 个 negative mappings，结果均为 `false`：

| Status | `FREEZE_CLOSEOUT` mapping |
| --- | --- |
| `NOT_STARTED` | reject |
| `IMPLEMENTED|PENDING_REVIEW` | reject |
| `REVIEW_ACCEPTED|READY_TO_COMMIT` | reject |
| `COMMITTED|CI_PENDING` | reject |
| `BLOCKED` | reject |

另验证 `NQ-GATEX-FREEZE-CLOSEOUT-LATER`、`NQ-GATEX-5-FREEZE-CLOSEOUT`、小写 action 与 GateW legacy 非精确变体均为 `UNKNOWN`。

## Gate-specific patch audit

- Contract pattern 只使用 `GATE[A-Z0-9]+`，未新增 GateX exact action、GateX case、GateX allowlist 或 `StartsWith("NQ-GATEX")`。
- Shared mapping 只从 work batch 提取通用 `Gate<代号>`，并做 ordinal exact action 比对。
- 两个治理测试套件均覆盖 GateX、GateW 与 GateY generic fixture；不是仅添加 GateX fixture。
- GateY 仅作为内存测试 fixture，未启动 GateY、未写入 GateY current authority 或 archive。

## Regression result

| Check | Result |
| --- | --- |
| `scripts/docs/test-governance-workflow-lifecycle.ps1` | PASS；`GOVERNANCE_LIFECYCLE_REGRESSION` 与 `TASK_EVIDENCE_POLICY_VALID` |
| `scripts/docs/test-current-authority-next-action.ps1` | PASS；generic action matrix、generic GateX authority fixture 与既有 action regression 全通过 |
| `scripts/docs/test-gate-archive-manifest.ps1` | PASS；`GATE_ARCHIVE_MANIFEST_REGRESSION` 与 `TASK_EVIDENCE_POLICY_VALID` |
| `scripts/docs/check-current-authority.ps1` | PASS；`errors=0 / CURRENT_AUTHORITY_CONSISTENT` |
| `scripts/docs/check-doc-links.ps1 -Roots README.md,docs/current` | PASS；213 checked / 0 errors / 1 个既有 GateJ ledger warning |

`check-gate-archive.ps1`、archive role logic、release checker、tag verification semantics 均未修改。Lifecycle regression 中既有 annotated tag、wrong target、remote missing、exact-head mismatch 与 stale tracking ref fail-closed fixtures 继续通过。

链接检查首次通过嵌套 `powershell -File` 调用时丢失 `-Roots` 数组边界，脚本在扫描前以 positional parameter error 退出；随后使用当前 PowerShell 直接调用并传显式数组，真实扫描结果为上述 PASS。该命令封装失败未被记录为文档通过。

## GateX-5 authority promotion

真实 Git/CI 事实：

- Starting `HEAD == origin/dev == a383be750f51d063d429bc25fad80e60dffb7014`。
- GateX-5 base commit=`ac4b1ba10f7ac10f973707e97c52b56a6b5aec6f`，forward-remediation implementation commit=`3336bd8153845d5368a0d65a9c72d3566dc9bd35`；两者均为 acceptance head ancestor。
- `NQ CI Baseline` run=`31512467501 / completed / success / 10 jobs / bad=0`，`headSha=a383be750f51d063d429bc25fad80e60dffb7014`。

Authority before：

```text
accepted_batch=GateX-4
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateX-5
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEX-5-COMMIT-AND-PUSH
```

Authority after：

```text
accepted_batch=GateX-5
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=3336bd8153845d5368a0d65a9c72d3566dc9bd35
accepted_batch_acceptance_head=a383be750f51d063d429bc25fad80e60dffb7014
accepted_batch_ci_run=31512467501
work_batch=GateX-FREEZE
work_batch_status=ACCEPTED|CI_GREEN|FREEZE_READY
work_batch_commit=3336bd8153845d5368a0d65a9c72d3566dc9bd35
work_batch_ci_run=31512467501
next_action=NQ-GATEX-FREEZE-CLOSEOUT
```

Safety facts remain：`active_gate=GateX`、`active_gate_status=IN_PROGRESS|NOT_FROZEN`、`LIVE=DISABLED`、`shadow_trading=NOT_ENABLED`。

## Findings 与边界

- P0=0。
- P1=0；原 `GOVERNANCE_GATEX_FREEZE_ACTION_UNMAPPED` 已关闭。
- P2=2：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED`、`FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED` 继续保留到 actual freeze archive，不阻断 non-LIVE `CREATED` materialization。
- P3=1：既有工具链 warning，非本轮引入。
- Archive checker impact：代码/manifest/role logic 变更为 0；只执行既有 regression。
- Release checker impact：代码与 tag verification semantics 变更为 0；未创建、移动、覆盖或验证 GateX tag。
- Business scope：backend/frontend/research/migration/CI workflow/credential/trading/Shadow runner/LIVE 变更或调用为 0。

## 下一动作

唯一下一动作：`NQ-GATEX-FREEZE-CLOSEOUT`。

该下一任务才可按 hard gate 执行 actual freeze、完整 pre-tag archive、archive/authority/link checks 与后续 tag 流程；本任务未提前执行这些动作。
