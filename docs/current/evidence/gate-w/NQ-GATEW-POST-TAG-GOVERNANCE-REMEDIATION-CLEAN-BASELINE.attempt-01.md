# GateW post-tag governance remediation clean baseline — attempt-01

## 任务与基线

- Task：`NQ-GATEW-POST-TAG-GOVERNANCE-REMEDIATION-CLEAN-BASELINE`。
- Classification：`WORKTREE_PRESERVATION / GOVERNANCE_CHECKER_REMEDIATION / POST_TAG_VERIFICATION / FINAL_AUTHORITY_SYNC / EXACT_HEAD_CI`。
- 起始分支与 commit：`dev / 16376de28be78eea58afbe1374847ee07ca2ccc7`，且 `HEAD == origin/dev`。
- Freeze CI：run `31299729114 / completed / success / 10 jobs / bad=0`。
- Release：tag=`nq-gatew-freeze`，tag object=`1fd434ada697136d51636a3040587704ec2ae1d9`，peeled commit=`16376de28be78eea58afbe1374847ee07ca2ccc7`。
- 六个未验证 authority-sync 草稿已先保存为仓库外 patch，再精确恢复到 clean freeze baseline；旧 patch 未应用、未暂存、未提交、未推送。

## P1 root cause 与最小修复

### P1-1：Non-Attempt current work batch

`Read-MachineCurrentAttemptAuthority` 已能把没有 machine Attempt fields、且不是 legacy Attempt runtime status 的 work batch 返回为
`IsApplicable=false / IsValid=true`。缺陷位于 `test-current-authority-next-action.ps1`：它把 `IsApplicable=false` 无条件视为
`CURRENT_MACHINE_ATTEMPT_FIXTURE_INVALID`，并让后续 current fixture 必须解析 Attempt ID。

修复后：

- `GateX-PLAN / NOT_STARTED / NQ-GATEX-PLAN-IMPLEMENTATION` 明确得到 machine Attempt `NOT_APPLICABLE`，再执行普通 exact triple validation。
- Non-Attempt current authority 继续执行 current checker、STATUS/ROADMAP exact next-action 一致性与 README 一致性验证。
- Attempt-scoped current authority 继续执行 Attempt ID/status、README/STATUS/ROADMAP consistency、malformed/duplicate/cross-Attempt fail-closed。
- unknown status、work batch、action 与 cross-Gate action 不会默认通过。

### P1-2：archive → release `ExpectedCommit` delegation

`check-gate-archive.ps1` 原先只向 `check-gate-release.ps1` 传递 `Gate` 与可选 `ExpectedTag`。在 committed authority 尚未完成
post-tag sync 时，release checker 无法从 `STATUS.md` 推导 GateW freeze commit，因而返回
`EXPECTED_COMMIT_MISSING_OR_INVALID`。

修复后 archive checker 从 canonical tag 解析 peeled commit，将其作为 `ExpectedCommit` 传给 release checker。Remote tag、annotated tag、
tag target、exact-head CI、release contract 和 branch alignment 仍由 release checker 独立负责；未在 archive checker 复制 release
业务逻辑，也未 hardcode GateW SHA。

## 回归与验证

| Command / evidence | Result | Scope / environment / warning |
| --- | --- | --- |
| `test-current-authority-next-action.ps1` | PASS（通过） | PowerShell 5.1 / 7；GateX non-Attempt positive、Attempt-13 positive、malformed/duplicate/cross-Attempt 与 unknown triple fail-closed |
| `test-governance-workflow-lifecycle.ps1` | PASS | PowerShell 5.1 / 7；release wrong target、remote missing、exact-head mismatch、stale branch 等负例继续 fail-closed |
| `test-gate-archive-manifest.ps1` | PASS | PowerShell 5.1 / 7；新增 disposable annotated-tag delegation，correct commit PASS，commit mismatch/illegal tag/missing tag FAIL |
| GateU / GateV archive compatibility | PASS | `ARCHIVE_MANIFEST_COMPLETE`，warnings=`0`，errors=`0` |
| GateW strict post-tag archive | PASS | PS5.1 / PS7；`ARCHIVE_MANIFEST_COMPLETE`，delegated `GATE_RELEASE_VALID` |
| direct GateW release checker | PASS | expected commit=`16376de28be78eea58afbe1374847ee07ca2ccc7`；exact-head CI run `31299729114` |
| current authority checker | PASS | clean freeze baseline 保持 GateW freeze closeout 前 committed authority，不提前同步 GateX |
| docs links | PASS | 300 checked / 1 个既有 GateJ historical ledger warning / 0 errors |
| PowerShell AST / JSON / diff / scope | PASS | PS5.1 / PS7；contract/manifest 未改；backend/frontend/research/deploy/.github/migration diff=`0` |

RCA：IDE formatter 对 PowerShell 两文件产生全文件机械重排，已完整回滚后重放最小 patch；未保留格式噪声。全量验证第一次 shell
调度因错误的短 timeout 被终止，未形成测试结论；正式重跑中 docs links 首次把三个 roots 聚合为单一路径而 exit 1，改用 PowerShell
array 后 `errors=0`。这些无效调用均未写成首轮通过。

## 边界、风险与状态

- P0=0，P1=0，任务特定 P2=0，P3=0；既有 GateW maximum gap=`1797s` P2 不变，本任务不重写 acceptance。
- Production SSH、OKX、credential、生产 DB、systemd、worker、heartbeat、release/current symlink、Attempt/RunId 操作=`0`。
- order/cancel/transfer/withdraw/LIVE execution=`0`；LIVE=`DISABLED`，kill switch=`ENGAGED`。
- 未修改 GateW archive、Attempt-13 acceptance、tag、contract、manifest、backend、frontend、research、deploy、CI workflow 或 migration。
- Remediation commit/push 与该 commit 的 exact-head CI 在本 evidence 写入时为 `PENDING`，不得提前描述为 GREEN。
- 本地结论：`PASS / DIRTY_AUTHORITY_DRAFT_PRESERVED / CLEAN_FREEZE_BASELINE_RESTORED / POST_TAG_GOVERNANCE_REMEDIATED / READY_TO_COMMIT / CI_PENDING / PRODUCTION_NOT_ACCESSED`。

下一动作：精确暂存本任务 allowlist，提交并推送 remediation；只有 exact-head `NQ CI Baseline` 达到
`completed / success / 10 jobs / bad=0` 后，才重新执行 post-tag verification 并重新生成 final authority sync。
