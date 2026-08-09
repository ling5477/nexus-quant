# NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE-TRANSITION-CONTRACT-FIX — attempt-01

## 1. Task classification 与授权边界

- Task：`GOVERNANCE_CONTRACT_FIX / 168H_ACCEPTANCE_SUCCESS_TRANSITION / RUNTIME_STATE_TRANSITION / FREEZE_CLOSEOUT_AUTHORIZATION / PERMANENT_LIFECYCLE_REGRESSION / TASK_EVIDENCE / COMMIT_AND_EXACT_HEAD_CI`。
- 等级：L 级治理合同修复。
- 授权只覆盖治理合同、共享 action classifier/transition validator、current authority checker、永久回归及本 evidence/ledger。
- 本任务不执行实际 168h acceptance，不停止或重启 Attempt-13 worker，不 freeze/archive/tag，也不修改任何生产 runtime fact。

## 2. Starting baseline

```text
repository=E:\Project\nexus-quant
branch=dev
starting_head=2caf5655983fa81db9bdb36eaa1bc40ca401ea51
origin_dev=2caf5655983fa81db9bdb36eaa1bc40ca401ea51
worktree=clean
staged=clean
```

写操作前已确认 `HEAD == origin/dev`。当前 authority 唯一来源为 `docs/current/STATUS.md` 顶部 `nq-current-authority` 区块。

## 3. Root cause 与 canonical lifecycle before

修复前合同只定义：

```text
RUNNING|PENDING_168H
-> NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE

RUNNING|PENDING_168H
-> FAILED|ACCEPTANCE_REJECTED|INCIDENT_REVIEW_COMPLETED
```

缺少 `RUNNING|PENDING_168H -> COMPLETED|ACCEPTED` 的 runtime completion 语义，也缺少 acceptance review、commit/CI 与 freeze-closeout authorization 的完整成功链。若直接依赖既有通用 `*-IMPLEMENTATION`、`*-COMMIT-AND-PUSH` 或大小写不敏感 pattern，近似 action 可能被错误归类，无法满足 Attempt-13 和 freeze action 的 exact-match 要求。

## 4. Canonical lifecycle after

成功链一次性定义为：

```text
GateW-OKX-READONLY-SOAK-ATTEMPT-13
+ RUNNING|PENDING_168H
-> GateW-ATTEMPT-13-168H-ACCEPTANCE
+ ACCEPTED|READY_TO_COMMIT
-> COMMITTED|CI_PENDING
-> ACCEPTED|CI_GREEN|FREEZE_READY
-> NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION
```

复用仓库已存在、语义相同的 canonical freeze action `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION`，不新增同义 `NQ-GATEW-FREEZE-CLOSEOUT`。

失败链保持：

```text
RUNNING|PENDING_168H
-> FAILED|ACCEPTANCE_REJECTED|INCIDENT_REVIEW_COMPLETED
-> NQ-GATEW-ATTEMPT-13-FAILURE-REMEDIATION-IMPLEMENTATION
```

失败链不授权 `FREEZE_READY`、freeze、tag、RunId reuse 或 automatic new Attempt。

## 5. Attempt runtime transition

`attempt13Runtime` 新增：

| Runtime state | attemptStatus | productionSoak | worker | acceptanceClock |
| --- | --- | --- | --- | --- |
| `SOAK_RUNNING` | `RUNNING|PENDING_168H` | `RUNNING` | `RUNNING` | `STARTED` |
| `SOAK_COMPLETED` | `COMPLETED|ACCEPTED` | `COMPLETED` | `STOPPED` | `COMPLETED` |
| `SOAK_REJECTED` | `FAILED|ACCEPTANCE_REJECTED` | `REJECTED` | `STOPPED` | `COMPLETED` |

成功 transition 只接受完整有序事件：

```text
ATTEMPT_13_168H_ACCEPTANCE_PASSED
ACCEPTANCE_VERDICT_RECORDED
WORKER_GRACEFULLY_STOPPED
SOAK_SEALED
```

失败 transition 继续要求 rejection、worker stop 与 incident evidence preservation。两种终态均保持 `live=DISABLED`、`killSwitch=ENGAGED`、`runIdReuse=FORBIDDEN`、`autoRetry=DISABLED`。

## 6. Acceptance work-batch 与 freeze-closeout mappings

| work_batch_status | work_batch | 唯一 next_action |
| --- | --- | --- |
| `ACCEPTED|READY_TO_COMMIT` | `GateW-ATTEMPT-13-168H-ACCEPTANCE` | `NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE-COMMIT-AND-PUSH` |
| `COMMITTED|CI_PENDING` | 同上 | `NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE-CI-ACCEPTANCE` |
| `COMMITTED|CI_FAILED|FIX_REQUIRED` | 同上 | `NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE-CI-BLOCKER-FIX` |
| `ACCEPTED|CI_GREEN|FREEZE_READY` | 同上 | `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION` |

以上 mapping 的 acceptance/freeze 路线全部精确要求：

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
attempt=Attempt-13
attempt_status=COMPLETED|ACCEPTED
production_soak=COMPLETED
live=DISABLED
kill_switch=ENGAGED
```

`ACCEPTED|CI_GREEN|FREEZE_READY` 仅在 commit 保持相同、exact-head CI `success` 且上述 runtime/safety facts 全部满足时可达。该状态只是 freeze closeout 的前置授权，不表示 GateW 已 freeze/tag，更不表示 LIVE 或交易授权。

## 7. Action classifier changes

- contract schema 从 `1.3.0` 升至 `1.4.0`；旧 checker 对未知 schema fail-closed。
- Attempt-13 acceptance、Attempt-13 failure remediation 与 GateW freeze-closeout 进入 strict action family。
- strict family 先执行 case-sensitive exact action lookup；未知、大小写变体、缺后缀或额外后缀统一返回 `UNKNOWN`。
- 既有通用 classifier 继续服务其他 Gate/action；strict family 阻断其对 Attempt-13/freeze 近似字符串的误接收。
- scoped mapping validator 新增 exact authority requirements，不以 fuzzy contains、permissive wildcard 或跨 Attempt fallback 替代。

## 8. Positive fixtures

1. 当前 `RUNNING|PENDING_168H -> NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE` 保持通过。
2. `SOAK_RUNNING -> SOAK_COMPLETED` 与完整 acceptance events 通过。
3. `ACCEPTED|READY_TO_COMMIT -> ACCEPTANCE-COMMIT-AND-PUSH` 通过。
4. `COMMITTED|CI_PENDING -> ACCEPTANCE-CI-ACCEPTANCE` 通过。
5. exact-head CI success + 完整 safety facts -> `ACCEPTED|CI_GREEN|FREEZE_READY -> NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION` 通过。
6. `SOAK_RUNNING -> SOAK_REJECTED -> failure remediation` 通过。
7. 既有 Attempt-09/10/11/12 deployment/soak lifecycle、release/tag 与 archive strict override regression 保持通过。

## 9. Negative fixtures

永久负例覆盖：RUNNING 直接 freeze、RUNNING 直接进入 freeze-ready、未 commit 直接 freeze、CI pending/failed 直接 freeze、exact-head mismatch、acceptance events 缺失、Attempt ID 不一致、Attempt-12/14 cross mapping、错误 work batch/status、未知 action、大小写变体、额外后缀、LIVE enabled、kill switch disengaged、production soak 未完成、Attempt 仍 RUNNING、active gate 已 frozen、Attempt FAILED 进入 freeze。全部按预期 fail-closed。

## 10. Validation

| Command / evidence | Result | Scope / environment |
| --- | --- | --- |
| contract `ConvertFrom-Json` 与 scoped assertions | `PASS / CONTRACT_JSON_PARSED_AND_SCOPED` | schema `1.4.0`；仅 Attempt-13 新增 `productionSoak`/完成/拒绝态 |
| `test-current-authority-next-action.ps1` | `PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION` | Windows PowerShell 5.1 与 PowerShell 7 |
| `test-governance-workflow-lifecycle.ps1` | `PASS / GOVERNANCE_LIFECYCLE_REGRESSION`、`PASS / TASK_EVIDENCE_POLICY_VALID` | Windows PowerShell 5.1 与 PowerShell 7 |
| `test-gate-archive-manifest.ps1` | `PASS / GATE_ARCHIVE_MANIFEST_REGRESSION`、`PASS / TASK_EVIDENCE_POLICY_VALID` | Windows PowerShell 5.1 与 PowerShell 7 |
| real GateV strict archive/release checker | `PASS / ARCHIVE_MANIFEST_COMPLETE`、`PASS / GATE_RELEASE_VALID` | tag `nq-gatev-freeze`；release CI `29191677441` exact-head success |
| `check-current-authority.ps1` | `PASS / CURRENT_AUTHORITY_CONSISTENT` | current RUNNING authority unchanged |
| `check-doc-links.ps1 -Roots docs/current` | `PASS / DOC_LINKS_VALID` | 162 checked / 0 errors / 1 个既有 GateJ historical warning |
| `git diff --check` | PASS | 无 whitespace error |

首次调用 `check-doc-links.ps1` 遗漏 mandatory `-Roots`，exit 1 且未开始 link scan；修正为 `-Roots docs/current` 后通过，不把该调用错误写成首轮通过。

Maven、frontend、Python 产品测试未运行，因为本轮不修改产品代码。最终 evidence/index/ledger 写入后必须重跑上述治理回归、scope 与 diff 检查。

## 11. Current authority after

本治理修复不改变业务 authority：

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-OKX-READONLY-SOAK-ATTEMPT-13
work_batch_status=RUNNING|PENDING_168H
Attempt-13=RUNNING|PENDING_168H
RunId=gatew-soak-20260801T180544Z-140bbcd1
live=DISABLED
kill_switch=ENGAGED
next_action=NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE
```

`docs/current/STATUS.md` 与 `docs/current/ROADMAP.md` 未修改。成功/失败 post-acceptance transition 已定义，但未被本任务执行。

## 12. Findings 与 production boundary

- P0：0。
- P1：0；`AUTHORITY_TRANSITION_UNDEFINED` 已由 exact contract 与永久回归关闭。
- P2：0。
- P3：0。
- 既有 warning：`docs/current/TESTING.md -> GATEJ_TEST_PLAN.md` historical link，非本任务引入，不阻断。

本任务 production boundary：

```text
production SSH=0
OKX calls=0
credential access=0
production DB access=0
systemd changes=0
worker stop/restart=0
current symlink changes=0
Attempt changes=0
RunId changes=0
heartbeat changes=0
acceptance/finalize=0
freeze/archive/tag=0
```

## 13. Commit、CI 与 rollback

```text
commit=PENDING
message=fix(governance): define GateW 168h acceptance transition
push=PENDING
workflow=NQ CI Baseline
ci_run=PENDING
status=PENDING
conclusion=PENDING
head_sha=PENDING
jobs=PENDING
bad_jobs=PENDING
```

本 evidence 不预写未来 commit 或 CI GREEN。提交后必须由任务最终输出核验 exact-head `completed / success / 10 jobs / bad=0`。

回滚：提交前仅反向撤销本任务精确 allowlist diff；提交后以独立 governance rollback review 和 `git revert <commit>` 回滚 contract/library/checker/tests/evidence/index/TESTING/WORKLOG，再运行本文件列出的全部治理回归。禁止使用 `git reset --hard` 或 `git checkout -- .`。

## 14. Final decision 与 next action

本地决定：

```text
PASS /
ATTEMPT_13_ACCEPTANCE_TRANSITION_DEFINED /
RUNTIME_COMPLETION_TRANSITION_DEFINED /
FREEZE_CLOSEOUT_ACTION_DEFINED /
FULL_LIFECYCLE_REGRESSION_GREEN /
CURRENT_AUTHORITY_UNCHANGED /
READY_TO_COMMIT /
CI_PENDING /
PRODUCTION_NOT_ACCESSED
```

本治理 commit 取得 exact-head CI GREEN 后，唯一下一动作仍为：

```text
NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE
```

该下一任务才可按新合同执行实际 acceptance、graceful seal 与 authority transition；本任务不执行这些动作。
