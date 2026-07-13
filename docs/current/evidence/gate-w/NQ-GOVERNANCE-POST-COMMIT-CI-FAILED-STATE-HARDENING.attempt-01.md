# NQ-GOVERNANCE-POST-COMMIT-CI-FAILED-STATE-HARDENING Attempt 01

Final decision：`PASS / GOVERNANCE_HARDENING_ACCEPTED / READY_TO_COMMIT`（通过 / 治理加固已接受 / 可进入提交前复核）。P0=0、P1=0。

## 1. Task classification and scope

- Classification：`NQ-only / GOVERNANCE_CONTRACT_HARDENING / POST_COMMIT_CI_FAILURE_LIFECYCLE / AUTHORITY_RECONCILIATION / CHECKER_UPDATE / REGRESSION_FIXTURES / TASK_EVIDENCE`。
- 允许范围：canonical governance contract/shared library、current authority checker、两份治理 regression、current governance/authority docs、append-only ledger 与本 attempt evidence/index。
- 明确禁止：`.github/**`、`backend/**`、migration、frontend、research、deploy、业务代码/业务测试、`docs/gates/**`、`docs/archive/**`、`.agents/**`、POM/package/lock files、历史 GateW evidence。
- 本任务不修复 CI blocker，不 rerun GitHub Actions，不初始化 order preview、GateW-4、Freeze 或 release。

## 2. Starting Git and CI facts

- Branch：`dev`；worktree clean；staged empty。
- Starting `HEAD == origin/dev == 8b54adc6952775dc1a939aad7b0ae849f20f42cf`。
- Commit：`8b54adc6952775dc1a939aad7b0ae849f20f42cf feat(marketdata): persist OKX venue rule facts`。
- Exact-head CI：`NQ CI Baseline` run `29241698510`，`status=completed`、`conclusion=failure`、`headSha=8b54adc6952775dc1a939aad7b0ae849f20f42cf`。
- 已确认 blocker：`BackendCiLegacyAccountFixture` 与 `FlywaySmoke` 固定 `EXPECTED_VERSION=33`，而 Flyway 已 migrate/validate 到 V34。历史 conformance evidence 的 EOF 空行不在本任务修复范围，且对应 evidence 保持 immutable。

## 3. Original contract gap and selected state

原 contract 只表达 `COMMITTED|CI_PENDING` 与 `ACCEPTED|CI_GREEN`，无法准确表达 implementation 已提交、review 仍有效、exact-head CI 已实际失败且必须修复的事实。使用 `BLOCKED` 会错误丢失“已提交且 CI 已完成失败”的关键阶段。

新增唯一 canonical 状态：

```text
COMMITTED|CI_FAILED|FIX_REQUIRED
```

该状态表示 implementation 已提交、失败 run 已绑定、当前 batch 未接受、必须执行 CI blocker fix。它不表示 implementation/review 被撤销、代码已回滚、CI 尚未运行/仍在运行或 Gate 已冻结。

CI conclusion 采用单一事实表达：状态 token 本身唯一表示 failure，`work_batch_ci_run` 绑定失败 run；不新增 `work_batch_ci_conclusion`、`ci_status`、`ci_result` 或其他重复字段。RCA/job/log 保留在 task evidence/current docs。

## 4. Field invariants

- `work_batch_commit`：必须匹配 concrete 40-char hexadecimal SHA；`UNCOMMITTED`、`NONE`、短 SHA、非 hex 与空值全部 fail-closed。
- `work_batch_ci_run`：必须匹配正整数 run ID；`NOT_RUN`、`PENDING`、非数字、0、负数与空值全部 fail-closed。
- `accepted_batch`：failed work batch 只能保留同 active Gate 的上一 accepted batch；本次固定 `GateW-2 / ACCEPTED|CI_GREEN`，不得提前接受 GateW-3。
- `active_gate`：固定 `GateW / IN_PROGRESS|NOT_FROZEN`，不得由 failed 状态进入 Freeze/release。
- failed work-batch body 不得同时声明 `CI GREEN`；状态 token 的大小写、空格或未知 alias 变体不注册。

## 5. Lifecycle transitions

正常失败：

```text
COMMITTED|CI_PENDING
→ COMMITTED|CI_FAILED|FIX_REQUIRED
```

要求同一 implementation commit，目标 run 为具体正整数失败 run。

Authority catch-up：

```text
REVIEW_ACCEPTED|READY_TO_COMMIT
→ COMMITTED|CI_FAILED|FIX_REQUIRED
```

仅在显式 reconciliation flag 下允许，从 `UNCOMMITTED / NOT_RUN` 追赶到 concrete commit/run。该边不是推荐正常路径；正常路径仍先进入 `COMMITTED|CI_PENDING`。

Recovery：

```text
COMMITTED|CI_FAILED|FIX_REQUIRED
→ COMMITTED|CI_PENDING
→ ACCEPTED|CI_GREEN
```

第一条边必须更换为新的 fix commit，并使用 contract 的 `PENDING` CI 表达；同 commit recovery 被拒绝。failed 直接进入 green 的 transition 不存在并由回归明确拒绝。

## 6. Next-action contract

- 新类型：`CI_BLOCKER_FIX`。
- Matcher：`(?i)^NQ-[A-Z0-9]+(?:-[A-Z0-9]+)*-CI-BLOCKER-FIX(?:-REVIEW|-COMMIT-AND-PUSH)?$`。
- Positive：`NQ-GATEW-3-CI-BLOCKER-FIX`、`NQ-GATEW-3-CI-BLOCKER-FIX-REVIEW`、`NQ-GATEW-3-CI-BLOCKER-FIX-COMMIT-AND-PUSH`。
- Negative：普通 implementation、模糊 `FIX`、migration/security/普通 blocker、非法 suffix 不会成为 `CI_BLOCKER_FIX`。
- Checker 额外把 `next_action` prefix 绑定到 current `work_batch`，因此 `NQ-GATEW-4-CI-BLOCKER-FIX` 虽具有 canonical 类型，也不能成为 GateW-3 的合法 current action。
- `CI_BLOCKER_FIX` matcher 排在既有 `REVIEW`/`COMMIT_AND_PUSH` matcher 之前，保证三个 phase 归入同一语义类型，同时不弱化其他 action 类型。

## 7. Checker and shared library changes

- Contract version 从 `1.0.0` 升为 `1.1.0`；authority schema 仍为 `3`，不新增 authority 字段。
- Shared library 新增 transition-context 纯函数：验证 from/to 字段策略、reconciliation flag、commit relation 与 CI relation；不访问 Git、GitHub 或网络。
- Authority checker 注册新状态，严格验证 commit/run、active/work binding、failed accepted predecessor、work-batch action binding 与 failed/green body contradiction。
- Work status registry 改为 case-sensitive exact token；checker body subject 收紧为精确中英文冒号分隔，并使用 ASCII-safe Unicode escape，避免 Windows PowerShell 5 编码歧义。
- Release/Archive checker 经审计后无需修改：二者明确不读取 work-batch authority，职责保持隔离。

## 8. Regression fixtures

Positive cases：

1. pending → failed：PASS。
2. ready-to-commit → failed 且显式 reconciliation：PASS。
3. failed + base `CI-BLOCKER-FIX` action：PASS。
4. failed + review phase：PASS。
5. failed + commit-and-push phase：PASS。
6. failed → pending，使用新的 fix commit：PASS。

Negative cases 覆盖：`UNCOMMITTED`、`NONE`、短/非 hex SHA；`NOT_RUN`、`PENDING`、非数字、0、空 run；普通 implementation、GateW-4、模糊/migration/security/ordinary blocker/freeze action；accepted batch 提前、active Gate frozen、failed 直接 green、pending 使用 failed-only action、未知 alias、lowercase/空格状态变体、同 commit recovery、未标记 reconciliation、failed body 同时声明 green。全部按预期 fail-closed。

## 9. Current authority reconciliation

```text
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN

active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN

work_batch=GateW-3
work_batch_status=COMMITTED|CI_FAILED|FIX_REQUIRED
work_batch_commit=8b54adc6952775dc1a939aad7b0ae849f20f42cf
work_batch_ci_run=29241698510

next_action=NQ-GATEW-3-CI-BLOCKER-FIX
```

`STATUS.md`、current README、`ROADMAP.md`、`GATEW_PLAN.md` 与 `FACT_SOURCE_INDEX.md` 已统一说明：venue-rule implementation 已提交，migration conformance review 已通过，exact-head CI 已失败，GateW-3 尚未 accepted，order preview attempt-02 尚未获准，下一步只修复 CI blocker。`GATEW_PLAN.md` 中 pre-commit 状态只作为明确标注的历史 snapshot 保留，不参与 current authority。

## 10. Validation

| Check | Result |
| --- | --- |
| JSON `ConvertFrom-Json` | PASS |
| `test-governance-workflow-lifecycle.ps1` | `PASS / GOVERNANCE_LIFECYCLE_REGRESSION`；`PASS / TASK_EVIDENCE_POLICY_VALID` |
| `test-current-authority-next-action.ps1` | `PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION` |
| `check-current-authority.ps1` | `PASS / CURRENT_AUTHORITY_CONSISTENT` |
| `check-doc-links.ps1 -Roots docs/current` | `PASS / DOC_LINKS_VALID`；保留 1 个既有 GateJ historical warning，errors=0 |
| `git diff --check` | PASS |
| forbidden-scope diff | `.github/backend/frontend/research/deploy/migration/docs/gates/docs/archive/.agents/pom.xml` 全部 0 |

未运行 Maven、frontend tests、Python tests、PostgreSQL、Flyway、OKX 或 GitHub Actions rerun；本任务只修改 governance scripts/docs，CI blocker fix 尚未实施。

## 11. Immutable evidence SHA-256

任务开始与治理 diff 完成后复核值一致：

| Evidence | SHA-256 |
| --- | --- |
| `NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW.attempt-01.md` | `72e58fd75339cca661bb4afc085d15ca516f1727e2adbffaa2dce55ad070dae1` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-SCHEMA-SECURITY-REVIEW.attempt-01.md` | `9404ddccb79357df1052d76e4815a02207081ae5235c13e3a1197ba250bf26af` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-IMPLEMENTATION.attempt-01.md` | `9975b2983a7e8d07ee40bbfe44d0ca07e562e8e1667048284850a419c0a151e9` |
| `NQ-GATEW-3-VENUE-RULE-FACTS-MIGRATION-CONFORMANCE-REVIEW.attempt-01.md` | `6852971d74874a6c70a645d239eff9f531289e16b5ed92625a4664103be8643e` |

## 12. Findings, boundary and rollback

- P0：0。
- P1：0。
- P2：CI blocker 本身仍未修复；本 governance commit 推送后可能再次被相同 V33 hard-code 阻断，必须由下一任务处理。
- P3：1 个既有 GateJ historical link warning；不属于本任务范围。
- Boundary：未修改/执行 CI workflow、backend、migration、业务/业务测试、frontend、research、deploy、archive、历史 GateW evidence；未访问 credential，未调用 OKX，未开启 LIVE/Shadow/AI/DH/Integration 或交易写侧。
- Rollback：提交前只反向撤销本 task 的精确 allowlist diff；提交后使用独立治理 rollback review + `git revert <governance-commit>`。Contract/library/checker/fixtures/current docs 必须原子回滚；由于真实 CI failure 不会因文档回滚消失，禁止只回退 authority 为 pre-commit 假状态，必须同时提供等价的 truthful failed-state 表达。

## 13. Next action

```text
NQ-GATEW-3-CI-BLOCKER-FIX
```

只修复已确认的 CI blocker；完成 review 后形成新的 fix commit/push，将 authority 恢复为 `COMMITTED|CI_PENDING`。禁止 failed → green 直跳、GateW-4 初始化、Freeze、order preview attempt-02 或 LIVE/交易授权。
