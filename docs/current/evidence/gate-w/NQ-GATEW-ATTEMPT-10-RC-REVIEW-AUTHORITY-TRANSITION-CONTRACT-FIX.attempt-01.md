# NQ-GATEW-ATTEMPT-10-RC-REVIEW-AUTHORITY-TRANSITION-CONTRACT-FIX — attempt-01

## 1. Task classification 与 operator authorization

- Task：`GOVERNANCE_CONTRACT_FIX / AUTHORITY_STATE_MACHINE_REMEDIATION / NEXT_ACTION_CLASSIFIER_FIX / PERMANENT_LIFECYCLE_REGRESSION / TASK_EVIDENCE / COMMIT_AND_EXACT_HEAD_CI`。
- 等级：L 级治理合同修复。
- 授权：`OPERATOR_AUTHORIZED_GOVERNANCE_CONTRACT_REMEDIATION`（操作人明确授权的治理合同整改）。
- 此授权仅覆盖治理合同、共享 classifier/transition validator、current authority checker 与永久回归；不构成 RC review、生产部署或 Attempt-10 启动授权。

## 2. Starting baseline

```text
repository=E:\Project\nexus-quant
branch=dev
starting_head=f78d56e474120e5751d94a8f6de4f121eb56d12e
origin_dev=f78d56e474120e5751d94a8f6de4f121eb56d12e
runtime_rc_source_commit=5a7e824e7e3edc470c55614523a12a2a84286856
runtime_rc_source_ci=30632959743 / completed / success / 10 of 10
control_exact_head_ci=30636002300 / completed / success / 10 of 10
```

前置检查时 worktree 与 staged 均为空，`HEAD == origin/dev`。两个既有 CI 均由 GitHub 只读查询确认，未触发 rerun。

## 3. Current authority before/after

本任务前后 current authority 均保持：

```text
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX
work_batch_status=IMPLEMENTED|CI_GREEN|RC_REVIEW_PENDING
work_batch_commit=5a7e824e7e3edc470c55614523a12a2a84286856
work_batch_ci_run=30632959743
Attempt-10=NOT_CREATED|NOT_AUTHORIZED
production_deployment=NOT_STARTED
live=DISABLED
next_action=NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW
```

治理修复没有把 RC source commit 替换为治理 commit，也没有宣称 RC review accepted、deployment authorized 或 Attempt-10 已创建。

## 4. Root cause 与修复前状态

修复前 canonical contract 只定义：

```text
GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-FIX
+ IMPLEMENTED|CI_GREEN|RC_REVIEW_PENDING
-> NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW
```

缺少 review PASS 后的 `REVIEW_ACCEPTED|READY_TO_COMMIT`、review commit/CI、exact-head CI 授权、deployment/start 与 168h acceptance 链路，也没有 preflight/permission/startup failure runtime invariant。直接复用 status-wide exact mapping 会让共享 `REVIEW_ACCEPTED|READY_TO_COMMIT`、`COMMITTED|CI_PENDING` 等状态误变为 GateW RC 专属，破坏其他 Gate。

另有一处 checker 兼容缺口：`COMMITTED|CI_FAILED|FIX_REQUIRED` 默认只接受数字 work batch predecessor；RC review 的独立命名 work batch 必须由 scoped exact triple 明确豁免，不能用 wildcard 放宽。

## 5. Canonical lifecycle after

正向链路：

```text
IMPLEMENTED|CI_GREEN|RC_REVIEW_PENDING
-> REVIEW_ACCEPTED|READY_TO_COMMIT
-> COMMITTED|CI_PENDING
-> ACCEPTED|CI_GREEN|DEPLOYMENT_AUTHORIZED
-> RUNNING|PENDING_168H
```

失败链路：

```text
RC review rejected
-> REVIEW_REJECTED|REMEDIATION_REQUIRED
-> NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-REMEDIATION

RC review commit CI failed
-> COMMITTED|CI_FAILED|FIX_REQUIRED
-> NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-CI-BLOCKER-FIX
```

`DEPLOYMENT_AUTHORIZED`、`PREFLIGHT_BLOCKED`、`PERMISSION_BLOCKED`、`SOAK_RUNNING`、`STARTUP_FAILED` 五个 runtime state 对以下字段做 exact validation：`attemptStatus`、`productionDeployment`、`live`、`killSwitch`、`worker`、`acceptanceClock`、`runIdReuse`、`autoRetry`。

`DEPLOYMENT_AUTHORIZED -> SOAK_RUNNING` 只允许以下严格有序事件：

```text
PRODUCTION_PREFLIGHT_PASSED
PERMISSION_VERIFIED
IMMUTABLE_RELEASE_DEPLOYED
ATTEMPT_10_CREATED
WORKER_STARTED
FIRST_VALID_HEARTBEAT_CONFIRMED
ACCEPTANCE_CLOCK_STARTED
```

失败事件链分别固定为 `PRODUCTION_PREFLIGHT_BLOCKED`、`PERMISSION_VERIFICATION_BLOCKED` 与 `FIRST_VALID_HEARTBEAT_FAILED`；失败状态保持 kill switch engaged、RunId reuse forbidden、auto retry disabled。

## 6. Exact triples added

所有新增 mapping 均使用 `scope=WORK_BATCH`，旧式无 scope mapping 继续保持 status-wide fail-closed 语义。

| work_batch_status | work_batch | 唯一 next_action |
| --- | --- | --- |
| `REVIEW_ACCEPTED|READY_TO_COMMIT` | `GateW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW` | `NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-COMMIT-AND-PUSH` |
| `COMMITTED|CI_PENDING` | 同上 | `NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-CI-ACCEPTANCE` |
| `ACCEPTED|CI_GREEN|DEPLOYMENT_AUTHORIZED` | 同上 | `NQ-GATEW-ATTEMPT-10-PREPARATION-AND-START` |
| `RUNNING|PENDING_168H` | `GateW-OKX-READONLY-SOAK-ATTEMPT-10` | `NQ-GATEW-ATTEMPT-10-168H-ACCEPTANCE` |
| `REVIEW_REJECTED|REMEDIATION_REQUIRED` | RC review batch | `NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-REMEDIATION` |
| `COMMITTED|CI_FAILED|FIX_REQUIRED` | RC review batch | `NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW-CI-BLOCKER-FIX` |

原 `RC_REVIEW_PENDING` exact triple 保持不变，因而完整矩阵共 7 条。

## 7. Action classifier 与 validator changes

- `COMMIT-AND-PUSH` 继续复用 `COMMIT_AND_PUSH`。
- RC review CI acceptance 以 exact case-sensitive pattern 归类为 `CI_WAIT_OR_INVESTIGATION`。
- review remediation 以 exact alternation 归类为 `RELEASE_CANDIDATE_STABILIZATION_FIX`。
- Attempt-10 168h acceptance 与 Attempt-09 action 使用显式枚举 alternation 归类为 `SOAK_ACCEPTANCE`；work-batch scoped exact mapping 防止跨 Attempt。
- 未知、大小写变体、前后缀变体继续返回 `UNKNOWN`；未增加 contains matcher 或 permissive wildcard。
- shared validator 先解析 classifier，再优先执行当前 status+work-batch 的 scoped exact mapping；无 scoped mapping 时保留旧 status-wide exact 或 type+prefix fallback。
- lifecycle context 新增 `EXACT_PAIR`、CI `SAME`、runtime state/transition exact validation；RC review CI 授权强制 `POST_RC_REVIEW_CI_SUCCESS_AUTHORIZATION`、exact-head match 与 `ciConclusion=success`。
- current authority checker 仅在当前 triple 本身命中 scoped exact mapping 时跳过旧数字 work-batch predecessor 规则；普通 Gate strict override 行为不变。

## 8. Permanent fixtures

Positive fixtures：

1. 当前 `RC_REVIEW_PENDING -> REVIEW` exact triple。
2. `REVIEW_ACCEPTED|READY_TO_COMMIT -> COMMIT_AND_PUSH`。
3. `COMMITTED|CI_PENDING -> CI_ACCEPTANCE`。
4. `ACCEPTED|CI_GREEN|DEPLOYMENT_AUTHORIZED -> PREPARATION_AND_START`。
5. Attempt-10 `RUNNING|SOAK_IN_PROGRESS -> 168H_ACCEPTANCE`。
6. RC review rejected -> independent remediation。
7. RC review CI failed -> CI blocker fix。
8. 五个 runtime invariant state 与四条 runtime transition。

Negative fixtures 覆盖：RC pending/review accepted/CI pending/CI failed 直接部署；RC pending 直接 soak；Attempt-09 映射 Attempt-10；未知、suffix/lowercase action；错误 work batch+正确 status；正确 work batch+错误 status；deployment started 但 Attempt-10 未授权；running 时 LIVE enabled；running 时 kill switch disengaged；启动事件缺失/乱序；168h acceptance 前 freeze action。全部按预期 fail-closed。

## 9. Validation

| Command / evidence | Result | Scope / environment |
| --- | --- | --- |
| `ConvertFrom-Json` | PASS | contract schema `1.3.0` |
| `powershell ... test-current-authority-next-action.ps1` | `PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION` | Windows PowerShell 5.1 |
| `pwsh ... test-current-authority-next-action.ps1` | 同上 | Windows PowerShell 7 |
| `powershell ... test-governance-workflow-lifecycle.ps1` | `PASS / GOVERNANCE_LIFECYCLE_REGRESSION`、`PASS / TASK_EVIDENCE_POLICY_VALID` | Windows PowerShell 5.1 |
| `pwsh ... test-governance-workflow-lifecycle.ps1` | 同上 | Windows PowerShell 7 |
| `test-gate-archive-manifest.ps1` | `PASS / GATE_ARCHIVE_MANIFEST_REGRESSION`、`PASS / TASK_EVIDENCE_POLICY_VALID` | strict override 与 archive 默认行为不变 |
| `check-current-authority.ps1` | `PASS / CURRENT_AUTHORITY_CONSISTENT` | unchanged current authority |
| `check-doc-links.ps1 -Roots docs/current` | `PASS / DOC_LINKS_VALID` | 147 checked / 0 errors / 1 个既有 GateJ warning |

首次调用 `check-doc-links.ps1` 遗漏 mandatory `-Roots` 参数，exit 1；以 `-Roots docs/current` 重跑通过，不把调用错误记为首轮通过。`.github/workflows` 未执行这些治理脚本，因此 disposable Linux PowerShell 7 条件不适用；未修改 CI workflow。

未运行 Maven、frontend、Python、PostgreSQL、Flyway、release builder/verifier、生产 SSH、systemd、真实 credential、OKX/private endpoint、Attempt-10、RunId、worker、168h clock、LIVE 或交易写侧；这些均不在本治理合同任务范围。

## 10. Findings

- P0：0。
- P1：0；原 authority transition 缺口已由 exact scoped contract 与永久回归关闭。
- P2：0。
- P3：0。
- 已知 warning：docs link checker 保留 1 个既有 `docs/current/TESTING.md -> GATEJ_TEST_PLAN.md` historical warning，不由本任务引入，不阻断。

## 11. Production boundary

- `production_deployment=NOT_STARTED`。
- Attempt-10 仍为 `NOT_CREATED|NOT_AUTHORIZED`。
- 未读取 credential，未连接生产，未调用 OKX，未启动 systemd/worker/clock。
- `live=DISABLED`，未新增或执行交易写路径。
- 未修改 release verifier/builder/contract/regression、archive checker、backend、frontend、research、deploy、`.github`、migration、`STATUS.md`、`ROADMAP.md`、`docs/gates` 或 `docs/archive`。

## 12. Commit、CI 与 rollback

治理实现 commit 使用 `fix(governance): define GateW RC review deployment transition`。由于 commit SHA 与 exact-head CI run 只能在提交后生成，本文件先记录 local validation；提交后的治理 commit/CI 将由同一任务追加到本 evidence，且不得冒充 RC source commit 或 RC review commit。

回滚：提交前仅反向撤销本任务精确 allowlist diff；提交后使用独立 governance rollback review 与 `git revert <governance-commit>`，原子回滚 contract/library/checker/tests/evidence/index/TESTING，再运行本文件列出的全部治理回归。禁止使用 `git reset --hard` 或 `git checkout -- .`。

## 13. Final decision 与 next action

本地决定：

```text
PASS /
AUTHORITY_TRANSITION_CONTRACT_DEFINED /
ACTION_CLASSIFIER_UPDATED /
FULL_LIFECYCLE_REGRESSION_GREEN /
CURRENT_AUTHORITY_UNCHANGED /
PRODUCTION_NOT_ACCESSED /
ATTEMPT_10_NOT_AUTHORIZED
```

提交与 exact-head CI 完成前，结论不包含 `COMMITTED / CI_GREEN`。完成后唯一业务 next action 仍是：

```text
NQ-GATEW-ATTEMPT-10-RELEASE-CANDIDATE-STABILIZATION-REVIEW
```

只有该独立 RC review 重新执行并按新合同通过，才可能进入 review commit/CI 与 deployment authorization；本任务本身不执行这些转换。
