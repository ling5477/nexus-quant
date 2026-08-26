# NQ-GOVERNANCE-GATEY6-CONTINUATION-CONTRACT-HARDENING-AND-LIFECYCLE-RCA — attempt-01

## 结论

`PASS / GATEY6_CONTINUATION_GOVERNANCE_CONTRACT_HARDENED / CONTINUATION_MODEL_EXPLICIT / NO_GLOBAL_ACTION_TYPE_BROADENING / HISTORICAL_GATEW_SEMANTICS_PRESERVED / LIFECYCLE_HANG_ROOT_CAUSED / FULL_GOVERNANCE_REGRESSION_GREEN / P0_0 / P1_0 / GATEY_AUTHORITY_UNCHANGED / PENDING_INDEPENDENT_SECURITY_REVIEW`

本任务只修改 NQ governance contract、checker、治理回归和 evidence。未实施 GateY-6B，未修改 current GateY authority，未访问 credential，未调用 OKX，未执行 PLACE/CANCEL，未启动 production worker，未执行 production operation。

## Baseline

- branch：`dev`。
- `HEAD == origin/dev == 621736e9a282d0f7684e2527fe86fe8e1faf506d`。
- exact-head CI：`31774122178 / NQ CI Baseline / completed / success / bad jobs=0`。
- worktree/staged：起始均为空。
- current authority：`GateY-5 / ACCEPTED|CI_GREEN`；`GateY-6 / REVIEW_ACCEPTED|READY_TO_COMMIT / UNCOMMITTED / NOT_RUN`。
- 安全事实：`real_provider=NOT_IMPLEMENTED`、`private_trading=NOT_IMPLEMENTED`、`live=DISABLED`、`kill_switch=ENGAGED`。

## Original blocker 与 before/after

原合同 `schemaVersion=1.4.0` 的全局映射为：

```text
COMMITTED|CI_GREEN|CONTINUE_REQUIRED -> SECURITY_RISK_REVIEW
```

目标 action `NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-IMPLEMENTATION` 被解析为 `IMPLEMENTATION`，因此 before：

```text
Get-GovernanceExpectedNextActionType = SECURITY_RISK_REVIEW
Get-GovernanceNextActionType          = IMPLEMENTATION
Test-GovernanceNextActionForWorkBatch = False
```

after 保持 generic expected type 不变，仅对 exact tuple 计算 effective expected type：

```text
status       = COMMITTED|CI_GREEN|CONTINUE_REQUIRED
workBatch    = GateY-6
nextAction   = NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-IMPLEMENTATION
effective    = IMPLEMENTATION
legal        = True
```

## Three-option decision matrix

| Option | 结论 | 兼容性与安全判断 |
| --- | --- | --- |
| A — Formal sub-batch authority | REJECTED（拒绝） | 需要引入 `GateY-6A/6B` 机器 batch 语法，并连带修改 accepted/work progression、cross-document、freeze/archive 假设和 current authority；超出本轮最小边界，且 current authority 明确禁止修改。 |
| B — Exact typed continuation override | SELECTED（选中） | 只对 exact status + exact work batch + exact action 生效；generic continuation 仍是 `SECURITY_RISK_REVIEW`，GateW 行为不变。 |
| C — post-CI SECURITY_RISK_REVIEW | REJECTED（拒绝） | GateY-6A 已有独立 review；额外 review 只为迁就 matcher，会形成无意义 review loop，且仍不能自然表达进入 6B implementation。 |

`SELECTED_MODEL=OPTION_B_EXACT_TYPED_CONTINUATION_OVERRIDE`。

`RATIONALE=最小范围、精确授权、无 wildcard、无 current authority 迁移、无全局语义放宽`。

`COMPATIBILITY_IMPACT=schema 1.4.0 -> 1.5.0；1.4.0 与未来未知版本 fail closed；无关历史行为变化=0`。

`SECURITY_IMPACT=新增一条 exact capability；缺 status/workBatch/action 任一维度均拒绝；override action 不得回退 generic prefix`。

## Lifecycle hang reproduction 与 RCA

修改合同前使用外层有界执行直接运行 `test-governance-workflow-lifecycle.ps1`：

- 启动后持续产生 fixture PASS；前半状态机、evidence path、authority fixtures 均在推进。
- 约 60 秒时已到 `authority-green-continuation-gatew3`；后续仍继续推进。
- 进程快照：root `powershell.exe` PID `47480`，累计 CPU `7.234s`；唯一直接 child 当时正在 disposable `authority-repo/scripts/docs/check-current-authority.ps1`。
- 终止前最后可见成功 marker：`green-continuation-next-batch-action-rejected`。
- 原 `Invoke-Checker` 使用同步 `& powershell ... 2>&1`，没有 child timeout；`Invoke-FixtureGit` 同样没有 timeout。
- suite 实际包含 100+ 串行 PowerShell/git child invocation；调用方若把全量输出赋值捕获，在结束前不会看到 marker，表现为长时间“无输出”。
- 未观察到固定 fixture deadlock、checker recursion、git lock 或 temp repo lock；改造后相同 suite 在 PS5.1 与 PS7 均稳定完成。

RCA 分类：`PERFORMANCE_REGRESSION`。具体根因是大量串行进程启动的可预期长尾，叠加缺少 per-child timeout 与 START marker，以及外层整段输出捕获造成的无可见进度；不是已证实的 `TEST_HARNESS_DEADLOCK`。

RCA harness 的 120 秒目标边界因工具轮询/旁路快照开销，实际中止约在 160 秒；测试进程收到中断后 current temp fixture 由 `finally` 清理。另清理了一份前次人工中断遗留、已校验位于 `%TEMP%` 且命中专用前缀的 disposable fixture；无仓库文件被该清理影响。

## Test harness remediation

- `Invoke-BoundedChildProcess` 使用当前 shell executable，兼容 Windows PowerShell 5.1 与 PowerShell 7。
- `System.Diagnostics.Process` + `ReadToEndAsync()` 并发读取 stdout/stderr，避免 pipe fill；保留真实 exit code。
- 每个 checker/git child 在执行前输出精简 `START child=... kind=... checker=... callsiteLine=... timeoutSeconds=60`。
- checker/git 局部 timeout 固定为 60 秒；timeout 抛出 `CHILD_PROCESS_TIMEOUT`，不会 skip-as-pass。
- timeout 记录 PID、CPU、direct children、last output，并用 `taskkill /T /F` 清理 disposable process tree；suite `finally` 删除 temp repo。
- 1 秒 timeout probe 已验证 timeout 必然 FAIL，且被杀 PID 不再存在。

实现中曾验证并修复一个 PS5.1 wrapper 兼容问题：`Start-Process -PassThru` 在重定向/轮询组合下没有提供可用 `ExitCode`，会把失败误判；最终实现改为 `System.Diagnostics.Process`，正负 checker exit code 均在 full regression 中正确断言。

## Contract 与 library changes

- `schemaVersion` 从 `1.4.0` 升级为 `1.5.0`；loader 只接受 `1.5.0`。
- exact mapping 新字段 `expectedActionTypeOverride` 只允许 `scope=WORK_BATCH`，值必须是已知 action type，且必须等于 exact action 的实际解析类型。
- 新增 GateY-6 exact tuple，override=`IMPLEMENTATION`。
- `Get-GovernanceExpectedNextActionTypeForWorkBatch` 计算 exact effective type；generic status mapping 不变。
- 带 override 的 exact action 被保留给其 exact tuple；错误 status 或 work batch 不得回退 generic prefix。
- loader 对相同 exact status/workBatch/action tuple 的重复 mapping 直接抛 `GOVERNANCE_CONTRACT_INVALID duplicate_exact_mapping`，消除 first-mapping-wins。
- exact/scoped matcher 必须恰好命中一次；0 或多次均 fail closed。
- checker 使用 effective expected type，并在 next-action 文档漂移时保留既有错误码、追加 `CURRENT_AUTHORITY_CROSS_DOCUMENT_MISMATCH`。

## Backward compatibility matrix

| Work batch / fixture | 原 expected behavior | 新行为 | Changed |
| --- | --- | --- | --- |
| `GateW-3` canonical continuation security-risk review | 接受 exact same-batch `SECURITY_RISK_REVIEW` | 接受 | no |
| `GateW-3` arbitrary implementation | 拒绝 | 拒绝 | no |
| GateW continuation archive/freeze/release actions | 拒绝 | 拒绝 | no |
| GateW continuation readiness for archive/release | 拒绝 | 拒绝 | no |
| high-risk lifecycle continuation transitions | 保持 canonical transition contract | 保持 | no |
| `GateY-6` exact target implementation | 原 blocker：拒绝 | exact tuple 接受 | yes，预期且唯一 |
| 其他 GateY status/work batch/action 组合 | 拒绝或遵循原 generic contract | 拒绝或原行为 | no |

仓库范围内 `COMMITTED|CI_GREEN|CONTINUE_REQUIRED` 引用已扫描；`docs/gates/**` 历史 evidence 未修改。`unrelated historical behavior changes=0`。

## Positive 与 negative tests

Positive：

- library exact GateY-6 continuation override=`LEGAL`。
- disposable authority fixture：`GateY-5 accepted` + `GateY-6 continuation` + exact-head CI run `31774122178` + exact 6B implementation action，`check-current-authority=PASS`。
- STATUS/ROADMAP/current README/root README 一致时 PASS。

Negative 全部 PASS（拒绝）：

- GateW continuation + arbitrary implementation。
- GateY-5 continuation + GateY-6 action。
- GateY-6 wrong implementation、near-match、lowercase、`IMPLEMENTATION-REVIEW`、arbitrary `*-IMPLEMENTATION`。
- wrong status、wrong work batch。
- cross-document next-action drift，返回 `CURRENT_AUTHORITY_CROSS_DOCUMENT_MISMATCH`。
- old `1.4.0`、future `9.0.0`、duplicate exact mapping。

## Validation

| Command | Shell | Result |
| --- | --- | --- |
| `scripts/docs/test-current-authority-next-action.ps1` | Windows PowerShell 5.1 | PASS / `CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION` |
| 同上 | PowerShell 7 | PASS / `CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION` |
| `scripts/docs/test-governance-workflow-lifecycle.ps1` | Windows PowerShell 5.1 | PASS / 138 bounded child invocations / 约 4m20s / `GOVERNANCE_LIFECYCLE_REGRESSION` / `TASK_EVIDENCE_POLICY_VALID` |
| 同上 | PowerShell 7 | PASS / 138 bounded child invocations / 约 1m33s / `GOVERNANCE_LIFECYCLE_REGRESSION` / `TASK_EVIDENCE_POLICY_VALID` |
| `scripts/docs/check-current-authority.ps1` | Windows PowerShell 5.1 | PASS / errors=0 |
| 同上 | PowerShell 7 | PASS / errors=0 |

只读 parse 诊断的首次嵌套 `powershell -Command` 调用因外层 `pwsh` 提前展开变量而失败，其中 JSON 诊断还因未设置 Stop 错误地返回 exit 0；无写操作。改用单引号隔离并设置 `$ErrorActionPreference='Stop'` 后，JSON 与四个 PowerShell 脚本在 PS5.1/PS7 均 parse PASS；最终结果仅采用修正后的重跑。

## Findings、边界与 disposition

- P0：0。
- P1：0。
- P2：0。
- P3：1，full lifecycle 仍是 process-heavy 串行 regression；PS5.1 约 4m20s，但每个 child 已局部有界且可观测，不阻断本轮 correctness/security closure。
- current GateY authority：未修改，仍为 `GateY-5 accepted / GateY-6 REVIEW_ACCEPTED|READY_TO_COMMIT`。
- product/backend/frontend/research/migration/deploy/trading runtime diff：0。
- credential access/OKX calls/PLACE/CANCEL/production worker/production operation：`0/0/0/0/0/0`。
- `FIRST_REAL_ORDER=NOT_AUTHORIZED`、`MICRO_LIVE=NOT_AUTHORIZED`、`EXPLICIT_MICRO_LIVE_AUTHORIZATION=NOT_GRANTED`、`LIVE=DISABLED`、`kill_switch=ENGAGED`。
- task disposition：`IMPLEMENTED|PENDING_REVIEW`，不写入 GateY machine authority。
- next action：`NQ-GOVERNANCE-GATEY6-CONTINUATION-CONTRACT-HARDENING-AND-LIFECYCLE-SECURITY-REVIEW`。
