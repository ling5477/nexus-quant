# NQ-GOVERNANCE-POST-FIX-CI-GREEN-CONTINUATION-HARDENING Attempt 01

Final decision：`IMPLEMENTED / PENDING_REVIEW`（已实现 / 等待独立审查）。P0=0、P1=0。本 evidence 记录 governance contract hardening，不修改 current authority；不得据此宣称 GateW-3 已 accepted、GateW-4 已初始化或 GateW 已 freeze。

## 1. Task classification and scope

- Classification：`NQ-only / GOVERNANCE_CONTRACT_HARDENING / POST_FIX_CI_SUCCESS_RECONCILIATION / SAME_BATCH_CONTINUATION_LIFECYCLE / CHECKER_UPDATE / REGRESSION_FIXTURES / TASK_EVIDENCE`。
- 允许范围：canonical governance contract/shared library、current authority checker、两份治理 regression、`GOVERNANCE_WORKFLOW.md`、本 attempt evidence 与 GateW evidence index。
- 明确禁止：`.github/**`、`backend/**`、`frontend/**`、`research/**`、`deploy/**`、migration、业务代码/业务测试、`docs/current/STATUS.md`、current README、`ROADMAP.md`、`GATEW_PLAN.md`、`docs/gates/**`、`docs/archive/**`、`.agents/**`、POM/package/lock files 与历史 GateW evidence。
- 本任务不暂存、不 commit/push、不创建 PR/tag、不 rerun GitHub Actions，不调用真实交易所或凭证，不启动 LIVE、GateW-4、GateW Freeze 或 order preview implementation。

## 2. Starting Git and exact-head CI facts

- Repository / branch：`E:\Project\nexus-quant / dev`。
- Starting worktree：clean；staged empty。
- Starting `HEAD == origin/dev == fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28`。
- Failed authority commit/run：`54c7bdd2caee5602441ce983b33c4cd2466ee263 / 29253811976 / completed / failure`。
- Fix commit/success run：`fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28 / 29260881801 / completed / success`。
- Run `29260881801` 的 `headSha=fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28`，与 starting HEAD exact match。
- 10 个实际 jobs 全部 `completed / success`：`Diff check`、`Backend Maven test`、`PostgreSQL / Flyway smoke`、`Frontend no-backend E2E (Batch 5A)`、`CI security smoke`、`Secret scan`、`No-outbound guard`、`Frontend build`、`Frontend backend E2E smoke`、`Research quality gate`。没有要求不存在的 governance/docs job。

## 3. Contract gap and selected canonical state

原 contract 只能在 fix success 后选择 `ACCEPTED|CI_GREEN` 或继续保留不真实的 failed/pending 状态，无法表达“当前 fix commit exact-head CI 已绿，但同一 numbered batch 仍有后续安全审查和受控实现”。

新增唯一 canonical 状态：

```text
COMMITTED|CI_GREEN|CONTINUE_REQUIRED
```

该状态不是 `ACCEPTED|CI_GREEN` alias。它表达当前 commit 已有 exact-head success run，但当前 work batch 尚未整体完成；accepted batch 必须保持最近完整接受的前序 batch，active Gate 保持未冻结，下一 numbered batch 不得初始化。

## 4. Why GateW-3 is not accepted

GateW-3 venue-rule facts 与 CI fix 的技术子切片已通过 exact-head CI，但 GateW-3 仍包含 dry-run order preview 的 security/risk review attempt-02 与后续受控实现。因此：

- 当前技术子切片已绿；
- GateW-3 整体尚未完成；
- `accepted_batch` 不能从 GateW-2 提前推进到 GateW-3；
- `COMMITTED|CI_FAILED|FIX_REQUIRED → ACCEPTED|CI_GREEN` 继续被拒绝；
- `COMMITTED|CI_GREEN|CONTINUE_REQUIRED → ACCEPTED|CI_GREEN` 也被拒绝。

## 5. CI conclusion strategy

继续使用单一事实表达：`work_batch_status=COMMITTED|CI_GREEN|CONTINUE_REQUIRED` 本身表达当前 commit 的 exact-head CI `conclusion=success`，`work_batch_ci_run` 绑定具体 success run。未新增 `work_batch_ci_conclusion`、`ci_result`、`ci_green` 或 `success_run`。

Authority checker 不访问 Git、GitHub 或网络；exact-head、conclusion 与 commit/run 对应关系由本任务 preflight、GitHub run evidence 与 transition external evidence 提供。

## 6. Field invariants

- `work_batch_commit`：必须是 concrete 40-char hexadecimal SHA；拒绝 `UNCOMMITTED`、`NONE`、空值、短 SHA 与非 hex。
- `work_batch_ci_run`：必须是正整数；拒绝 `NOT_RUN`、`PENDING`、`NONE`、非数字、0 与负数。
- `accepted_batch_status`：必须为 `ACCEPTED|CI_GREEN`。
- `accepted_batch`：必须是 current work batch 的直接已完成前序，且不得等于 current work batch；GateW-3 对应 GateW-2。
- `active_gate / active_gate_status`：必须保持 `GateW / IN_PROGRESS|NOT_FROZEN`。
- 状态 token：严格区分大小写、空格和未知 alias；pure library 与 authority checker 都 fail-closed。

## 7. Transition policies

### Pending success but same batch continues

```text
COMMITTED|CI_PENDING
→ COMMITTED|CI_GREEN|CONTINUE_REQUIRED
```

要求 same commit、same work batch、same accepted batch、concrete success run、`exactHeadMatch=true`、`ciConclusion=success`，并把 next action 绑定到同一 work batch；若 pending authority 已绑定具体 run ID，目标必须保持同一 run，只有 `PENDING` 占位可落成具体 run。

### Post-fix success reconciliation

```text
COMMITTED|CI_FAILED|FIX_REQUIRED
→ COMMITTED|CI_GREEN|CONTINUE_REQUIRED
```

仅允许 `mode=POST_FIX_CI_SUCCESS_RECONCILIATION` 且显式 authority catch-up；要求 new commit、new run、same work batch、same accepted batch、`exactHeadMatch=true`、`ciConclusion=success` 与同 batch next action。相同 failed commit、相同 failed run、缺 mode、exact-head false 或非 success conclusion 全部拒绝。

当前真实正例：

```text
54c7bdd2caee5602441ce983b33c4cd2466ee263 / 29253811976
→
fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28 / 29260881801
```

### Same-batch next uncommitted work

```text
COMMITTED|CI_GREEN|CONTINUE_REQUIRED
→ IMPLEMENTED|PENDING_REVIEW
→ REVIEW_ACCEPTED|READY_TO_COMMIT
→ COMMITTED|CI_PENDING
```

第一条 transition 要求 work/accepted batch 保持不变，commit 转为 `UNCOMMITTED`，CI run 转为 `NOT_RUN`，next action 为同 batch review。只有 GateW-3 全部冻结内容完成并重新进入 `COMMITTED|CI_PENDING` 后，才允许最终 `→ ACCEPTED|CI_GREEN`。

## 8. Next-action mapping and same-batch relation

- 新增 action type：`SECURITY_RISK_REVIEW`。
- Matcher 只分类明确的 `SECURITY-RISK-REVIEW` 与可选 `ATTEMPT-<正整数>`；`ATTEMPT-02` 合法，`ATTEMPT-00` 非法。
- Target：`NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-02`。
- shared library 与 authority checker 使用精确 `NQ-<WORK_BATCH>-` prefix 绑定 work batch；GateW-3 action 不接受 GateW-4 或 GateX。
- 普通/模糊 review、`FIX`、`CI-BLOCKER-FIX`、implementation、freeze 与 release task 不能成为 continuation 状态的 next action。

## 9. Contract, library and checker changes

- Contract schema 从 `1.1.0` 升为 `1.2.0`；authority schema 保持 `3`，未新增 authority 字段。
- Contract 注册唯一 continuation state、body pattern、field policy、action mapping、三条 high-risk edge 与三份 context policy；未把新状态加入 `acceptedBatchStatuses` 或 freeze candidate。
- Shared library 保持旧 positional transition caller 兼容，在末尾增加可选 context；新增 mode、external evidence、work/accepted batch 与 next-action relation 校验，并提供 `ARCHIVE_FREEZE` / `RELEASE` readiness status 纯函数。
- Shared library 把 status property/transition lookup 收紧为 case-sensitive，消除 lowercase alias 在 pure function 中被接受的缺口。
- Authority checker 复用 canonical action+work-batch helper，并为 continuation 强制 numbered predecessor、字段格式、active Gate、body not accepted 与 same-batch action；新增可选 `-ReadinessMode ARCHIVE_FREEZE|RELEASE` 作为实际 archive/release checker 前的组合前置。
- Release/Archive checker 不修改：二者不负责 active work-batch 状态，也不能升级 authority。Lifecycle integration fixture 已真实调用 authority checker 的两个 readiness mode，continuation 均以 `GATE_READINESS_STATUS_INVALID` fail-closed；同时保留 pending-review archive/freeze 与 accepted release 正例，证明前置不是恒假。实际 archive/release checker 只能在 readiness 前置通过后运行。

## 10. Regression fixtures

Positive cases：

1. pending → green continuation，same commit + success run：PASS。
2. failed → green continuation，new commit/new run + explicit reconciliation：PASS。
3. 真实 `54c7bdd… / 29253811976 → fd6a8b2… / 29260881801`：PASS。
4. `accepted_batch=GateW-2 / work_batch=GateW-3 / continuation` authority：PASS。
5. target security-risk review attempt-02 与 GateW-3 same-batch relation：PASS。
6. green continuation → pending review，commit/CI reset 且 same batch：PASS。

Negative cases 覆盖：failed direct accepted；continuation direct accepted；same failed commit；same failed run；缺 reconciliation mode；`exactHeadMatch=false`；non-success conclusion；非法 commit/run；accepted=work；accepted predecessor 错误；work batch GateW-4；active Gate frozen；GateW-4/CI blocker/freeze/release/implementation/vague review action；unknown/lowercase/space status；continuation → pending review 时 work/accepted batch 改变；continuation 进入 `ARCHIVE_FREEZE` 或 `RELEASE` readiness checker。全部按预期 fail-closed，且原 direct-green negative fixture 未删除或弱化。

## 11. Current authority decision and target authority

本任务根据明确 scope 保持 current authority 不变：

```text
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-3
work_batch_status=COMMITTED|CI_FAILED|FIX_REQUIRED
work_batch_commit=54c7bdd2caee5602441ce983b33c4cd2466ee263
work_batch_ci_run=29253811976
next_action=NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH
```

只有下一独立 review 接受本 contract 后，才允许把 target authority 写为：

```text
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-3
work_batch_status=COMMITTED|CI_GREEN|CONTINUE_REQUIRED
work_batch_commit=fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28
work_batch_ci_run=29260881801
next_action=NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW-ATTEMPT-02
```

## 12. Validation

| Check | Result |
| --- | --- |
| JSON `ConvertFrom-Json` | PASS |
| PowerShell AST parse（4 个修改脚本） | PASS |
| `test-governance-workflow-lifecycle.ps1` | `PASS / GOVERNANCE_LIFECYCLE_REGRESSION`；`PASS / TASK_EVIDENCE_POLICY_VALID` |
| Archive/freeze/release readiness integration | continuation 在 `ARCHIVE_FREEZE` 与 `RELEASE` mode 均返回 `GATE_READINESS_STATUS_INVALID`；两条对应正例 PASS |
| `test-current-authority-next-action.ps1` | `PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION` |
| `check-current-authority.ps1` against unchanged FAILED authority | `PASS / CURRENT_AUTHORITY_CONSISTENT` |
| `check-doc-links.ps1 -Roots docs/current` | `PASS / DOC_LINKS_VALID`；checked=78、errors=0、保留 1 个既有 GateJ historical warning |
| `git diff --check` | PASS |
| forbidden-scope diff | `.github/backend/frontend/research/deploy/migration/current authority/docs/gates/docs/archive/.agents/pom.xml/package.json` 全部 0 |

未运行 Maven、frontend tests、Python tests、PostgreSQL、Flyway、OKX 或 GitHub Actions rerun；本任务只修改 governance scripts/docs，业务代码和 CI workflow 无变化。

## 13. Immutable evidence verification

任务开始与结束的 SHA-256 完全相同；`IMMUTABLE_HASH_MISMATCHES=0`，`IMMUTABLE_DIFF_LINES=0`：

| Evidence | SHA-256 before | SHA-256 after |
| --- | --- | --- |
| `NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW.attempt-01.md` | `72E58FD75339CCA661BB4AFC085D15CA516F1727E2ADBFFAA2DCE55AD070DAE1` | `72E58FD75339CCA661BB4AFC085D15CA516F1727E2ADBFFAA2DCE55AD070DAE1` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-SCHEMA-SECURITY-REVIEW.attempt-01.md` | `9404DDCCB79357DF1052D76E4815A02207081AE5235C13E3A1197BA250BF26AF` | `9404DDCCB79357DF1052D76E4815A02207081AE5235C13E3A1197BA250BF26AF` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION.attempt-01.md` | `9975B2983A7E8D07EE40BBFE44D0CA07E562E8E1667048284850A419C0A151E9` | `9975B2983A7E8D07EE40BBFE44D0CA07E562E8E1667048284850A419C0A151E9` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW.attempt-01.md` | `6852971D74874A6C70A645D239EFF9F531289E16B5ED92625A4664103BE8643E` | `6852971D74874A6C70A645D239EFF9F531289E16B5ED92625A4664103BE8643E` |
| `NQ-GOVERNANCE-POST-COMMIT-CI-FAILED-STATE-HARDENING.attempt-01.md` | `A70C035D32F87E249D5E5C7A8810C9F75965E2854563E2E34B1AAEE723F09D97` | `A70C035D32F87E249D5E5C7A8810C9F75965E2854563E2E34B1AAEE723F09D97` |
| `NQ-GATEW-3-CI-BLOCKER-FIX.attempt-01.md` | `19CFFFC81044C2869CB96376CDD3501C64F2551AF424866DBAF86EBE56C88E6D` | `19CFFFC81044C2869CB96376CDD3501C64F2551AF424866DBAF86EBE56C88E6D` |
| `NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW.attempt-01.md` | `FA5211E23ED07B0DBA2ECC8DBB1981687DB988555A0ABA849CE121AC66A1BC62` | `FA5211E23ED07B0DBA2ECC8DBB1981687DB988555A0ABA849CE121AC66A1BC62` |

## 14. Findings, boundaries and rollback

- P0：0。
- P1：0。
- P2：root `README.md` 仍有既有 GateW summary drift；该文件不决定 current authority，且不在本任务 allowlist，未修改。
- P3：`CLAUDE.md` 仍保留旧 GateJ 阶段文案；`STATUS.md` 为唯一 current authority，本任务不扩大范围修订该历史入口。
- Boundary：未修改 `.github`、backend、frontend、research、deploy、migration、current authority、Gate/archive、agents、依赖文件或历史 evidence；未读取 credential，未调用 OKX，未开启 LIVE/Shadow/AI/DH/Integration 或交易写侧。
- Rollback：commit 前只反向撤销本 task 的精确 allowlist diff；不得使用 `git reset --hard` 或 `git checkout -- .`。提交后使用独立 governance rollback review 与 `git revert <governance-commit>`，并原子回滚 contract/library/checker/fixtures/docs/evidence index，重新运行本 evidence 的全部治理验证。

## 15. Next action

```text
NQ-GOVERNANCE-POST-FIX-CI-GREEN-CONTINUATION-REVIEW
```

Review 接受前不得修改 current authority；review 后才可执行独立 authority reconciliation，并把 GateW-3 next action 指向 dry-run order preview security/risk review attempt-02。
