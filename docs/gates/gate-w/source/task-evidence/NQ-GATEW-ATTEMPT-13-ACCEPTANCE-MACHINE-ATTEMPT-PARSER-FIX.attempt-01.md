# NQ-GATEW-ATTEMPT-13-ACCEPTANCE-MACHINE-ATTEMPT-PARSER-FIX — Attempt 01

## 1. Task classification 与结论

- Task classification：`GOVERNANCE_CHECKER_REMEDIATION / ACCEPTANCE_EVIDENCE_PRESERVATION / PERMANENT_REGRESSION / ACCEPTANCE_CLOSEOUT`。
- NQ-only；starting HEAD=`2fdeadfdc988bbdac9a858466948ccfa0a4acce1`；starting branch=`dev`；`HEAD == origin/dev`；staged 区为空。
- Final decision：`PASS / MACHINE_ATTEMPT_PARSER_FIXED / ATTEMPT_13_ACCEPTANCE_EVIDENCE_PRESERVED / GOVERNANCE_REGRESSION_GREEN / READY_TO_COMMIT`（通过 / machine-attempt parser 已修复 / Attempt-13 验收证据已保留 / 治理回归已通过 / 可进入提交）。

本修复不重新连接生产、不重跑 168h soak、不重启或再次停止已 seal worker，不创建 Attempt-14，不修改 schema `1.4.0` 合同，也不进入 freeze/archive/tag。

## 2. Root cause 与 parser before

旧 `Read-MachineCurrentAttemptAuthority` 使用 suffix-only regex：

```text
(?-i:^Gate[A-Z0-9]+(?:-[A-Z0-9_]+)*-ATTEMPT-(?<attemptId>[1-9][0-9]*)$)
```

该表达式要求 `ATTEMPT-13` 位于 `work_batch` 末尾，因此合法 canonical batch `GateW-ATTEMPT-13-168H-ACCEPTANCE` 返回 `IsApplicable=false`。同时旧实现把 `work_batch_status` 直接当作 Attempt runtime 状态，不能表达 acceptance batch 的 `ACCEPTED|READY_TO_COMMIT` 与后续 `ACCEPTED|CI_GREEN|FREEZE_READY`。

基线复现：`check-current-authority.ps1` exit `0`，但 `test-current-authority-next-action.ps1` exit `1`，精确失败为 `CURRENT_DOC_CASE_UNEXPECTED_PASS case=readme-attempt-status-conflict`。

## 3. Parser after

- parser 移入 checker 与 tests 共用的 `scripts/docs/governance-workflow-lib.ps1`；checker 中重复实现已删除。
- 先依据显式 `attempt/attempt_status` 或 legacy 两段 runtime status 判定 machine-attempt 语义；名称中历史性包含 `ATTEMPT-*`、但没有 runtime machine 语义的 RC work batch 继续保持不适用。
- 对适用 batch 只接受唯一、大小写严格、前后由字符串边界或 `-` 分隔的 `ATTEMPT-<digits>` segment。
- Attempt ID 必须与精确 `attempt=Attempt-<id>` 一致；runtime 状态从 `attempt_status` 读取，不再把 acceptance work-batch status 误当 Attempt runtime 状态。
- 非法、缺失、大小写错误、前导零、重复 segment 或 machine 字段不一致均返回 `IsApplicable=true / IsValid=false`，由 authority checker fail-closed。

## 4. Permanent fixtures

正向 fixtures（PowerShell 5.1 / 7 均 PASS）：

| work batch | 结果 |
| --- | --- |
| `GateW-ATTEMPT-13` | Attempt `13` |
| `GateW-ATTEMPT-13-168H-ACCEPTANCE` | Attempt `13` |
| `GateW-ATTEMPT-13-PREPARATION-AND-START` | Attempt `13` |
| `GateW-ATTEMPT-13-FAILURE-REMEDIATION-IMPLEMENTATION` | Attempt `13` |

负向 fixtures（PowerShell 5.1 / 7 均 `FAIL_CLOSED`）：

| fixture | 结果 |
| --- | --- |
| `GateW-ATTEMPT-12-168H-ACCEPTANCE` 与 `Attempt-13` machine docs | 拒绝 |
| `GateW-ATTEMPT-14-168H-ACCEPTANCE` 与 `Attempt-13` machine docs | 拒绝 |
| `GateW-ATTEMPT-13X-168H-ACCEPTANCE` | 拒绝 |
| `GateW-attempt-13-168H-ACCEPTANCE` | 拒绝 |
| `GateW-ATTEMPT-13-ATTEMPT-14-168H-ACCEPTANCE` | 拒绝 |
| `GateW-ATTEMPT--13-168H-ACCEPTANCE` / `ATTEMPT-` / `ATTEMPT-X` / `ATTEMPT-013` | 拒绝 |
| `readme-attempt-status-conflict` | `FAIL_CLOSED / CURRENT_ATTEMPT_STATUS_CONFLICT` |
| `roadmap-attempt-status-conflict` | `FAIL_CLOSED / CURRENT_AUTHORITY_CROSS_DOCUMENT_MISMATCH` |

## 5. Compatibility regression

| 验证 | 结果 |
| --- | --- |
| current authority checker | `PASS / CURRENT_AUTHORITY_CONSISTENT` |
| `test-current-authority-next-action.ps1` | PowerShell 5.1 / 7 均 `PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION` |
| `test-governance-workflow-lifecycle.ps1` | PowerShell 5.1 / 7 均 `PASS / GOVERNANCE_LIFECYCLE_REGRESSION`；Attempt-10、Attempt-13 acceptance success/failure、freeze hard gate 继续通过 |
| task-evidence policy | PowerShell 5.1 / 7 均 `PASS / TASK_EVIDENCE_POLICY_VALID` |
| `test-gate-archive-manifest.ps1` | PowerShell 5.1 / 7 均 `PASS / GATE_ARCHIVE_MANIFEST_REGRESSION` |
| GateV archive compatibility | `PASS / ARCHIVE_MANIFEST_COMPLETE`；warnings=`0`，errors=`0` |
| docs links | PowerShell 5.1 / 7 均 `PASS / DOC_LINKS_VALID`；errors=`0`；1 个既有 GateJ historical ledger warning |
| JSON parse | governance contract schema=`1.4.0`、archive manifest schema=`1.0.0`，均 PASS |
| PowerShell AST | 三个变更脚本均 PASS，errors=`0` |
| actual current evidence policy | path/item 均 `True` |
| `git diff --check` / forbidden scope | PASS；backend/frontend/research/deploy/.github=`0`；contract diff=`0` |

`scripts/docs/governance-workflow-contract.json` 未修改，schema 保持 `1.4.0`。产品 backend/frontend/Python 测试未在本地重跑，因为本轮只修改 PowerShell 治理 checker/tests 与 current evidence；提交后仍必须以 exact-head `NQ CI Baseline` 10/10 为接受 hard gate。

## 6. Acceptance evidence preservation

- Canonical evidence：[NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE.attempt-01.md](NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE.attempt-01.md)。
- 原草稿 SHA-256=`03A119198D8B2ED4499CAF04956F55EF5A2D7079401A7F5FAE905B9FAA39129D`；`git diff --cached --check` 发现末尾多余空行后，仅删除该 EOF whitespace，规范化后 SHA-256=`CECE12F3C32F9731BAA2DEBCD5787FF471E0433A9555BEB2E1D57BCC4043A86B`。正文、生产事实、数值与 verdict 未改变。
- Attempt=`13`、RunId=`gatew-soak-20260801T180544Z-140bbcd1`、release=`b103069d8bfcecccba0b4d590317ddccc66898b9`、samples=`656`、elapsed=`604820.4973147s`、maximum gap=`1797s / 1->2`、PID=`478613`、NRestarts=`0`、hash-chain/security counters 与 seal 结果均未修改或重新判断。
- 生产访问与操作：SSH/OKX/credential/生产 DB/systemd/worker/heartbeat/release/symlink/Attempt mutation/RunId mutation=`0`。
- Worker 保持 `inactive/dead`，MainPID/residual=`0/0`；LIVE=`DISABLED`，kill switch=`ENGAGED`。

## 7. Findings 与边界

- P0：0。
- P1：0。suffix-only parser P1 已由 shared、strict、fail-closed parser 与永久 regression 关闭。
- P2：1。原 acceptance evidence 已披露 maximum gap=`1797s`，冻结合同没有独立 maximum-gap threshold；本任务不补造 threshold，也不改写已确定的 production PASS。
- P3：0。

Acceptance commit/push 与 exact-head CI 在本文首次写入时仍未执行；CI 成功前 authority 不得写为 `FREEZE_READY`。回滚方式是在提交前删除本轮新增 evidence 并反向应用仅限三个治理脚本的 diff；提交后使用新 revert commit，禁止改写 production evidence 或共享历史。
