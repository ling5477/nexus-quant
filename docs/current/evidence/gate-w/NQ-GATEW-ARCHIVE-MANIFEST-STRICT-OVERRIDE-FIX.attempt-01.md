# NQ-GATEW-ARCHIVE-MANIFEST-STRICT-OVERRIDE-FIX — Attempt 01

## Task classification

- 归属：NQ-only。
- 类型：`GOVERNANCE_MANIFEST_FIX / STRICT_GATE_ARCHIVE_CONTRACT / ARCHIVE_REGRESSION / TASK_EVIDENCE`。
- 起始基线：`dev` clean，`HEAD == origin/dev == 9a90379196ce4fe0cefe3e737b354a5b94f27fa5`。
- 起始 authority：GateW=`IN_PROGRESS|NOT_FROZEN`，Attempt-13=`COMPLETED|ACCEPTED`，production soak=`COMPLETED`，work batch=`ACCEPTED|CI_GREEN|FREEZE_READY`，LIVE=`DISABLED`，kill switch=`ENGAGED`，next action=`NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION`。

## Scope

本任务只补齐 `scripts/docs/gate-archive-manifest.json` 的 GateW strict override，并扩展 `scripts/docs/test-gate-archive-manifest.ps1` 的永久回归。未创建 `docs/gates/gate-w/`，未执行 freeze/tag，未修改 archive checker、governance contract/shared lib、current authority、Attempt-13、业务代码、migration、CI workflow 或 GateW runtime scripts。

## Root cause

`legacyThroughGate=gate-t`，GateW 属于 post-legacy strict Gate；但 `strictGateOverrides` 只有 `gate-u` 与 `gate-v`。因此 GateW archive candidate 即使未来具备完整 role，也会先被 `check-gate-archive.ps1` 以 `STRICT_GATE_OVERRIDE_MISSING gate=gate-w` fail-closed，形成 `BLOCKED / ARCHIVE_MANIFEST_INCOMPLETE`。

## GateW strict override

- Canonical tag：`nq-gatew-freeze`。
- Pre-tag：`allowPreTagArchiveState=true`；`expectedTagTarget=null`，不预言未来 tag target。
- Mandatory roles（复用现有 schema）：`archive-entry`、`freeze-closeout`、`freeze-readiness`、`plan-or-reconstructed-baseline`、`batch-evidence-matrix`、`testing-evidence`、`boundary-statement`、`known-limitations`。
- Conditional roles（按 GateW 实际能力面）：`backend-db-evidence`、`api-evidence`、`frontend-evidence`、`runtime-scheduling-evidence`。
- 未要求 `python-boundary-evidence`：GateW 未形成 Python/research implementation archive 义务；不为不存在的产品 artifact 造 role。
- Manifest schema 保持 `1.0.0`；governance workflow contract schema 保持 `1.4.0`，均未升级或放宽。

## Task evidence handling

`source/task-evidence/**` 继续由 schema 1.4.0 的 `archiveRoot` 与 `archivePathPattern` 约束，并由现有 checker 作为 `non-role` 处理。永久 fixture 证明 nested `README.md` 不抢占顶层 `archive-entry`，Attempt-10 `FAILED`、Attempt-11 `BLOCKED`、Attempt-13 `PASS` 均可作为非空、合法命名的 raw evidence；删除顶层 `README.md` 后 nested README 不能补足 canonical role。

## Regression coverage

正向 fixture：GateW strict override contract、canonical tag、valid pre-tag candidate、valid post-tag structure、nested evidence README、FAILED/BLOCKED/PASS raw attempts、GateU/GateV compatibility。

负向 fixture：GateW override 缺失、canonical tag 缺失、canonical tag 错误、required role 缺失、duplicate role、unknown top-level file、task evidence 被误当 canonical role、GateV evidence 混入 GateW、broken README link、空/空白/placeholder/非法命名 evidence、unknown source file、重复 archive entry。所有负例均以非零退出或 mutation contract rejection fail-closed。

## Validation

| 验证 | 结果 |
| --- | --- |
| `test-gate-archive-manifest.ps1` | PowerShell 5.1 / 7：PASS；`GATE_ARCHIVE_MANIFEST_REGRESSION`、`TASK_EVIDENCE_POLICY_VALID` |
| GateW disposable pre-tag/post-tag fixture | PASS；未创建真实 GateW archive/tag |
| `check-gate-archive.ps1 -Gate gate-u` | PASS；warnings=`0`，errors=`0` |
| `check-gate-archive.ps1 -Gate gate-v` | PASS；warnings=`0`，errors=`0` |
| `test-current-authority-next-action.ps1` | PowerShell 5.1 / 7：PASS |
| `test-governance-workflow-lifecycle.ps1` | PowerShell 5.1 / 7：PASS；task-evidence policy PASS |
| `check-current-authority.ps1` | PASS；errors=`0` |
| `check-doc-links.ps1` | current=`166/1 warning/0 errors`；GateU=`12/0/0`；GateV=`12/0/0` |
| JSON parse / PowerShell AST | PASS；manifest 可解析；测试脚本双引擎 AST errors=`0` |

首轮回归曾因 broken-link fixture 已写 GateW、调用仍指向 GateV 而意外成功；修正为 GateW 后双引擎重跑通过。首轮 GateU/GateV link 聚合命令把逗号连接值误作单一路径而 exit 1；拆分为两个明确 `-Roots` 调用后重跑通过。两次均为测试编排问题，不改变 checker 语义，且未记作首轮通过。

## Boundary confirmation

- Production/SSH/OKX/credential/生产 DB/systemd/worker/release/symlink operations=`0`。
- LIVE 保持 `DISABLED`，kill switch 保持 `ENGAGED`；未新增订单、撤单、转账、提现或任何交易写路径。
- GateW 保持 `IN_PROGRESS|NOT_FROZEN`；未创建 archive，未 freeze/tag，未修改 Attempt-13。
- `STATUS.md`、`ROADMAP.md`、backend、frontend、research、deploy、`.github`、migration、`scripts/gatew` 均无修改。

## Findings and decision

- P0=0，P1=0，任务特定 P2=0，P3=0。
- 既有 GateW maximum-gap threshold P2 保持不变，本任务不修改 acceptance/freeze 业务判定。
- 本地结论：`PASS / GATEW_STRICT_ARCHIVE_OVERRIDE_DEFINED / CANONICAL_TAG_DEFINED / ARCHIVE_MANIFEST_REGRESSION_GREEN / GATEU_GATEV_COMPATIBILITY_GREEN / CURRENT_AUTHORITY_UNCHANGED / READY_TO_COMMIT / CI_PENDING / PRODUCTION_NOT_ACCESSED`。
- 本任务 commit 与 exact-head CI 在本 evidence 入 commit 前不可预写；提交后按任务 hard gate 核验。
- 唯一下一动作：本任务 exact-head CI green 后执行 `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION`。
