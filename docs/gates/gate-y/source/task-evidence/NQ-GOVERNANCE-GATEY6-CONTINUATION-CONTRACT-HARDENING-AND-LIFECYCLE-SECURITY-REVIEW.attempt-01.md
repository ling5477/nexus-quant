# NQ-GOVERNANCE-GATEY6-CONTINUATION-CONTRACT-HARDENING-AND-LIFECYCLE-SECURITY-REVIEW — attempt-01

## 审查结论

`PASS / GATEY6_CONTINUATION_GOVERNANCE_SECURITY_REVIEW_ACCEPTED / OPTION_B_EXACT_OVERRIDE_VERIFIED / NO_GLOBAL_CONTINUATION_BROADENING / HISTORICAL_GATEW_SEMANTICS_PRESERVED / MALFORMED_AND_DUPLICATE_MAPPINGS_FAIL_CLOSED / CHECKER_LIBRARY_PARITY_VERIFIED / BOUNDED_CHILD_HARNESS_VERIFIED / LIFECYCLE_HANG_RCA_ACCEPTED / PS51_FULL_REGRESSION_GREEN / PS7_FULL_REGRESSION_GREEN / P0_0 / P1_0 / GATEY_AUTHORITY_UNCHANGED / READY_TO_COMMIT`

本审查接受 `OPTION_B_EXACT_TYPED_CONTINUATION_OVERRIDE`。唯一新增授权能力是 GateY-6 精确 continuation tuple 进入指定 `IMPLEMENTATION`；全局 continuation、历史 GateW、accepted/work batch preservation 与 current GateY machine authority 均未放宽。

## Review target 与范围

- Ownership：NQ-only governance。
- 起始 candidate：8 个已跟踪修改 + 1 个 RCA evidence 新文件；staged=`0`；unexpected/missing=`0/0`。
- 已审查：governance contract、library、current-authority checker、next-action regression、lifecycle regression、RCA evidence、`TESTING.md`、`WORKLOG.md` 与 GateY evidence index。
- 明确不涉及：backend、frontend、research、migration、deploy、`.github`、GateY business implementation、GateY-6B product code、credential、真实 provider、OKX private API、PLACE/CANCEL、production worker。
- Authority hard boundary：`docs/current/STATUS.md` diff=`0`；本审查不执行 GateY-6A post-CI authority reconciliation。

## Starting baseline

- branch：`dev`。
- `HEAD == origin/dev == 621736e9a282d0f7684e2527fe86fe8e1faf506d`。
- exact-head CI：`31774122178 / NQ CI Baseline / push / completed / success`；`headSha=621736e9a282d0f7684e2527fe86fe8e1faf506d`，由 `gh run view` 独立核验。
- current authority checker：errors=`0`。
- accepted batch：`GateY-5 / ACCEPTED|CI_GREEN`。
- work batch：`GateY-6 / REVIEW_ACCEPTED|READY_TO_COMMIT / UNCOMMITTED / NOT_RUN`。
- next action：`NQ-GATEY-6-EXPLICIT-MICRO-LIVE-AUTHORIZATION-PREFLIGHT-AND-WORK-ORDER-COMMIT-AND-PUSH`。
- 安全事实：`real_provider=NOT_IMPLEMENTED`、`private_trading=NOT_IMPLEMENTED`、`live=DISABLED`、`kill_switch=ENGAGED`。

## Contract 1.5.0 与 selected model

`schemaVersion` 从 `1.4.0` 升为 `1.5.0` 是 contract shape/semantics 变更：`exactNextActionMappings` 新增可选 `expectedActionTypeOverride`，并新增 effective expected-type precedence。因此版本 bump 有实际语义，不是无意义 bump。

Loader 行为：

- `1.5.0`：接受。
- `1.4.0`：拒绝。
- future/unknown `9.0.0`：拒绝。
- 失败统一进入 `GOVERNANCE_CONTRACT_INVALID` 或精确子错误；不存在 silent fallback。

Selected model 精确 tuple：

```text
status=COMMITTED|CI_GREEN|CONTINUE_REQUIRED
workBatch=GateY-6
nextAction=NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-IMPLEMENTATION
expectedActionTypeOverride=IMPLEMENTATION
```

全局 mapping 保持：

```text
COMMITTED|CI_GREEN|CONTINUE_REQUIRED -> SECURITY_RISK_REVIEW
```

Contract 未增加 `NQ-GATEY-6-.*-IMPLEMENTATION`、`GateY-6.*IMPLEMENTATION` 或其他 wildcard；override 绑定完整 action string。

## Override structural validation

Loader 对 exact mappings 统一要求 `workBatchStatus`、`workBatch`、`nextAction` 非空；status 必须属于 canonical `workBatchStatuses`，action 必须能解析为已知 action type。对带 override 的 mapping 额外要求：

- `scope == WORK_BATCH`；
- override 非空；
- override 是 known action type；
- override 与 `Get-GovernanceNextActionType(nextAction)` 完全相等。

以下 malformed fixtures 全部以 `GOVERNANCE_CONTRACT_INVALID` fail closed：

| Fixture | Result |
| --- | --- |
| override on wrong scope | REJECT（拒绝） |
| unknown override type | REJECT（拒绝） |
| blank override | REJECT（拒绝） |
| override/action type mismatch | REJECT（拒绝） |
| missing `workBatchStatus` | REJECT（拒绝） |
| missing `workBatch` | REJECT（拒绝） |
| missing `nextAction` | REJECT（拒绝） |
| unknown status | REJECT（拒绝） |
| unknown action | REJECT（拒绝） |
| unsupported old/future version | REJECT（拒绝） |

## Exact mapping uniqueness 与 effective precedence

- duplicate key 定义为 ordinal exact `status + NUL + workBatch + NUL + nextAction`。
- 重复 exact tuple 在 contract load 阶段抛 `duplicate_exact_mapping`；不存在 first-match-wins 或 last-match-wins。
- 同 status/workBatch 的不同 exact action 仍可作为显式 contract decisions；每个 action 的 matcher 必须恰好命中一条。
- effective precedence：exact scoped mapping override 优先；没有 exact match 时使用 generic `statusToNextActionType`。
- override action 被保留给其 exact tuple；错误 status 或 work batch 不允许回退到 generic prefix。

## Matcher matrix

Positive GateY-6：

```text
generic expected type   = SECURITY_RISK_REVIEW
effective expected type = IMPLEMENTATION
actual action type      = IMPLEMENTATION
LEGAL                   = true
```

Negative 与 near-match 均 `LEGAL=false`：

- GateW continuation + arbitrary implementation。
- GateY-5 continuation + exact target action。
- GateY-6 wrong/arbitrary implementation。
- exact target + `-X`、`-REVIEW` suffix。
- 去掉 `CONTRACT` 的 mutation implementation。
- 缩短为 real-provider implementation。
- lowercase action。
- `NQ-GATEY-06-*`、`NQ-GATEY-6A-*`。
- GateY-6 `SECURITY-RISK-REVIEW` 伪装 override。
- wrong status、wrong work batch、wrong status + wrong work batch。
- unknown status、unknown work batch/action/action type。

Historical GateW compatibility：canonical GateW continuation 仍只接受 `SECURITY_RISK_REVIEW`；arbitrary implementation、archive、archive-move、freeze、release 与 vague review 均拒绝。历史行为变化=`0`。

## GateY-6 lifecycle semantics

现有 high-risk lifecycle 保持以下链条：

```text
REVIEW_ACCEPTED|READY_TO_COMMIT
-> COMMITTED|CI_PENDING
-> COMMITTED|CI_GREEN|CONTINUE_REQUIRED
-> IMPLEMENTED|PENDING_REVIEW
```

本审查专门新增 GateY-6 context fixtures：

- `fromWorkBatch=GateY-6 -> toWorkBatch=GateY-6`：接受。
- `fromAcceptedBatch=GateY-5 -> toAcceptedBatch=GateY-5`：接受。
- 隐式 accepted batch promotion `GateY-5 -> GateY-6`：拒绝。
- Option A 式 work sub-batch `GateY-6 -> GateY-6B`：拒绝。
- transition 后 commit/CI 回到 `UNCOMMITTED / NOT_RUN`，符合下一 implementation pending review 的字段策略。

因此 Option A 语义未混入 Option B，GateY-6B 仍是同一个 machine `work_batch=GateY-6` 下的后续实现阶段。

## Checker/library parity 与 cross-document fail closed

Disposable authority positive fixture 使用：

```text
accepted_batch=GateY-5
work_batch=GateY-6
work_batch_status=COMMITTED|CI_GREEN|CONTINUE_REQUIRED
work_batch_commit=<40-hex fixture commit>
work_batch_ci_run=31774122178
next_action=NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-IMPLEMENTATION
```

Library 与 `check-current-authority.ps1` 均判定合法。STATUS/ROADMAP/current README/root README 同步时 PASS；ROADMAP drift、wrong action、lowercase、wrong status、wrong work batch 均 fail closed。既有 current-authority regression 还覆盖 README、root README、ROADMAP 与 STATUS 的 cross-document consistency；checker/library 未出现相反结论。

## Lifecycle harness security review

### Command/argument boundary

- `UseShellExecute=false`；使用 `FileName + native argv encoding`，不经过 `cmd.exe /c` 或拼接式 `powershell -Command <untrusted string>`。
- argv probe 对 spaces、quotes、Unicode path、parentheses、ampersand、trailing backslash 做 UTF-8/base64 round-trip，PS5.1/PS7 均保持 exact values。
- Git child 非零 exit 保留 stderr 并 fail closed；未把 negative Git 变成 false pass。

### stdout/stderr deadlock safety

- stdout/stderr 在 child start 后立即分别调用 `ReadToEndAsync()`，并发排空两个 pipe。
- synthetic child 向两个 stream 各输出 5,000 行并写入 `STDOUT_DONE`/`STDERR_DONE`；两个 shell 均在 20 秒 bound 内完成。

### Timeout 与 observability

- 普通 checker/git timeout 固定 60 秒；timeout 抛 `CHILD_PROCESS_TIMEOUT`，不是 warning、skip 或 PASS。
- error 包含 child identity、kind、executable、真实 callsite line、timeout、PID、CPU、direct children 与 bounded last output。
- 每个 child 均有 `START` 与 `END`；正常/非零 exit/timeout 均有 resolution。
- 两个不同 wrapper callsite 输出不同真实行号，不会固定报告 helper 内部行。

### Process-tree cleanup 与 PID reuse

- `taskkill.exe /PID <exact PID> /T /F` 只针对当前 `System.Diagnostics.Process` 对象创建的 disposable child；不按 process name 或 wildcard 终止。
- cleanup 前检查 `Process.HasExited`，结束后等待并再次确认；未对已退出对象无条件 taskkill。
- synthetic root/direct child/grandchild 三层树 timeout 后三个 PID 全部消失。
- Windows numeric PID 在 `HasExited` 检查与 `taskkill` 之间仍有理论上的极短复用竞态；当前实现已满足 handle-bound process object + pre-kill exit check，未发现可实际误杀 unrelated process 的路径，不构成 P1。若未来把 helper 用于非 disposable process，建议改用 Windows Job Object。

### Temp cleanup

- normal PASS、intentional timeout、child nonzero failure 均通过 suite `finally` 清理。
- 两次 full regression 完成后 `%TEMP%\nq-governance-lifecycle-*` remaining=`0`。

## Full regressions

| Shell | Script | Exit | Duration | Child START/END | Intentional timeout probes | Unexpected timeout/failure |
| --- | --- | ---: | ---: | ---: | ---: | ---: |
| Windows PowerShell 5.1.26100.9168 | `test-current-authority-next-action.ps1` | 0 | 26.948s | N/A | 0 | 0/0 |
| PowerShell 7.6.4 | `test-current-authority-next-action.ps1` | 0 | 11.650s | N/A | 0 | 0/0 |
| Windows PowerShell 5.1.26100.9168 | `test-governance-workflow-lifecycle.ps1` | 0 | 280.199s | 144/144 | 2 | 0/0 |
| PowerShell 7.6.4 | `test-governance-workflow-lifecycle.ps1` | 0 | 103.352s | 144/144 | 2 | 0/0 |
| Windows PowerShell 5.1.26100.9168 | `check-current-authority.ps1` | 0 | 2.556s | N/A | 0 | 0/0 |
| PowerShell 7.6.4 | `check-current-authority.ps1` | 0 | 1.023s | N/A | 0 | 0/0 |

两次 full lifecycle 都输出：

```text
PASS / GOVERNANCE_LIFECYCLE_REGRESSION
PASS / TASK_EVIDENCE_POLICY_VALID
unexpectedTimeoutCount=0
unexpectedFailureCount=0
```

`expectedTimeoutProbeCount=2` 是专门验证 timeout fail/cleanup 的 intentional probes，不是 suite instability。没有 single ordinary child >60s、unbounded wait、process leak 或 temp leak。

## Hang RCA validation

接受 RCA 分类 `PERFORMANCE_REGRESSION`：PS5.1 280.199s、PS7 103.352s 与 144 个 process-heavy 串行 child 结构相符；两次完整 suite 均稳定结束，所有 START 都有 END，ordinary child timeout=`0`。bounded helper 没有把固定 deadlock 简单改写为 60 秒 failure。

审查中新增 process-tree fixture 的首个 PS7 诊断运行曾因 fixture 自身跨层传递带空格 pwsh path 时被 `Start-Process -ArgumentList` 拆分，导致 root 提前 exit；最小修复为每层从自身 PID 解析 shell path，重跑 PS7/PS5.1 full regression 均通过。该失败没有仓库外副作用或残留进程。

另一次 parse-only 诊断命令因 `powershell -Command` 参数位置错误，在读取脚本前 ParserError；改为 explicit literal path 后，三个 PowerShell 文件在 PS5.1/PS7 均 parse PASS。两项诊断失败均保留为 RCA，不写成通过。

## Findings

### P0

- 无。

### P1

- 无。

### P2

- 无。审查中识别的 malformed coverage、near-match coverage、process-tree cleanup、dual-stream、argv、callsite 与 child resolution 证明缺口已在允许范围内最小整改并通过双 shell full regression。

### P3

- 1：lifecycle suite 仍为 process-heavy 串行测试；PS5.1 约 4m40s。当前 runtime 有界、每个 child 60 秒、可观测且无 leak，本轮不做并行化或性能重构。

## Boundary confirmation

- `docs/current/STATUS.md`、`ROADMAP.md`、root/current README：本审查 diff=`0`。
- backend/frontend/research/migration/deploy/`.github` product diff=`0`。
- credential access、OKX call、PLACE、CANCEL、production worker、production operation=`0`。
- `FIRST_REAL_ORDER=NOT_AUTHORIZED`。
- `MICRO_LIVE=NOT_AUTHORIZED`。
- `EXPLICIT_MICRO_LIVE_AUTHORIZATION=NOT_GRANTED`。
- `real_provider=NOT_IMPLEMENTED`、`private_trading=NOT_IMPLEMENTED`。
- `LIVE=DISABLED`、`kill_switch=ENGAGED`。

## Decision 与 next action

Review decision：`REVIEW_ACCEPTED / READY_TO_COMMIT`。该状态仅记录本 governance review，不写入 GateY machine authority。

唯一下一动作：

```text
NQ-GOVERNANCE-GATEY6-CONTINUATION-CONTRACT-HARDENING-AND-LIFECYCLE-COMMIT-AND-PUSH
```

推荐 commit：

```text
fix(governance): harden GateY-6 continuation contract and lifecycle tests
```

后续顺序仍必须是 governance fix commit → exact-head CI green → GateY-6A post-CI authority reconciliation → authority-sync commit → exact-head CI green → 独立 GateY-6B implementation；本 review 不授权跳步。
