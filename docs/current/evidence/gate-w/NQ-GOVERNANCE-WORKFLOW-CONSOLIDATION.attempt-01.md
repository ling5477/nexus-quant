# NQ-GOVERNANCE-WORKFLOW-CONSOLIDATION attempt-01

## Task classification

`GOVERNANCE_REFACTOR + CHECKER_RESPONSIBILITY_SPLIT + LIFECYCLE_CONTRACT + TASK_EVIDENCE_SUPPORT + REGRESSION_TESTS`。NQ-only、GateW 前置高风险治理任务；最终状态限制为 `IMPLEMENTED / PENDING_REVIEW`（已实现 / 待独立复核）。

## 原治理问题与 GateV 冲突

- Archive checker 同时校验 archive role、current authority、local/remote tag 与 GitHub CI，结构、状态和 release 责任耦合。
- Authority checker 自身硬编码完整 status/action 列表，并读取 tag 与 commit ancestry，导致 schema 演进需要在多个脚本和 fixture 重复修改。
- Freeze 的 `IMPLEMENTED|PENDING_REVIEW` candidate 与独立 `REVIEW_ACCEPTED` authority commit 之间存在无价值中间提交压力。
- strict archive 把所有非 role 文件视为 unknown；nested evidence README 会命中 archive-entry alias，task attempt 无法随 archive durable 固化。

## Checker 责任拆分

- Archive：只检查 manifest、role、path、link、evidence、unknown/empty/symlink。
- Authority：只检查 schema v3、active/accepted/work 状态、字段格式、`next_action` 与固定安全边界。
- Release：只检查 release commit/branch ancestry、exact-HEAD CI、annotated tag、local/remote object 与 peeled target。

## Lifecycle contract

新增 `scripts/docs/governance-workflow-contract.json` 作为 machine-readable 唯一契约，`governance-workflow-lib.ps1` 统一读取。普通任务采用 self-review 直提交流程；高风险任务保留独立 review；Freeze 不要求独立 review-authority commit。`active_gate_status` 只允许 `IN_PROGRESS|NOT_FROZEN`。

## Evidence policy

- Current：`docs/current/evidence/<line>/<TASK-ID>.attempt-<NN>.md`。
- Archive：`docs/gates/<gate>/source/task-evidence/<TASK-ID>.attempt-<NN>.md`。
- Archive task evidence 是 non-role source evidence；nested README 不占 role、不触发 unknown。
- 仅允许明确安全扩展名与 canonical 文件名；path traversal、encoded traversal、empty、symlink/reparse point fail-closed。

## 兼容性决定

- 保留 `check-current-authority.ps1`、`check-gate-archive.ps1`、`test-current-authority-next-action.ps1`、`test-gate-archive-manifest.ps1` 入口。
- 保留 `CURRENT_AUTHORITY_CONSISTENT`、`GATE_ARCHIVE_PRETAG_VALID`、`ARCHIVE_MANIFEST_COMPLETE`、`GATE_ARCHIVE_MANIFEST_REGRESSION` 输出 token。
- 新增 `GATE_RELEASE_VALID`、`GOVERNANCE_LIFECYCLE_REGRESSION`、`TASK_EVIDENCE_POLICY_VALID`。
- `-PreTag` 语义收敛为纯 archive structure/evidence；不再检查 tag 是否存在或 authority 状态。
- 兼容的 post-tag release orchestration 只委托完整 Release checker，不复制 release 逻辑。

## Fixture 与 GateV 回归

- 普通 lifecycle：`NOT_STARTED`、self-reviewed、CI pending、CI green 合法；不经过 review 合法；倒退失败。
- 高风险 lifecycle：pending review、review accepted、commit、CI green 合法；未 review 直接 accepted 失败。
- Freeze：不要求 review-authority commit；candidate 可从 pending review 进入。
- Evidence：current/archive path、nested README non-role、not-counted/not-unknown 通过；unknown source、duplicate archive entry、empty、invalid/traversal 与 symlink 拒绝。
- Release disposable repository：annotated tag 通过；lightweight、wrong target、remote missing、exact-HEAD CI mismatch 失败。
- 真实 GateV：Authority、post-tag Archive 与 Release checker 均通过；tag object 与 peeled target未修改。
- Doc links：`docs/current` 51 links / 1 个既有 GateJ historical ledger warning / 0 errors；`docs/gates/gate-v` 12 links / 0 warnings / 0 errors。

## Files changed

脚本：contract、shared helper、Archive/Authority/Release checker、三个 regression entry。文档：本文对应 canonical workflow、current README/fact index、append-only TESTING/WORKLOG 与 GateW evidence index。未修改 GateV archive、业务代码、migration、CI workflow 或 tag。

## Findings

- P0：0。
- P1：0（实现自审结果；仍需唯一独立 review）。
- P2：Release checker 的 GitHub proof 依赖可用的 `gh` 认证与网络；失败时 fail-closed，不提供 skip 参数。`docs/current/TESTING.md` 保留 1 个既有 GateJ historical ledger link warning，不是本 diff 引入。
- P3：0。

## Rollback

在未暂存/提交前，按本 evidence 的 files changed 清单删除新增文件并恢复修改文件即可；不得使用会覆盖用户其他改动的 `git reset --hard`。提交后应使用独立 revert commit，不移动或覆盖任何 release tag。

## Final decision

`IMPLEMENTED / PENDING_REVIEW`。未启动 GateW planning，未修改任何业务能力状态。

## Next action

`NQ-GOVERNANCE-WORKFLOW-CONSOLIDATION-REVIEW`。
