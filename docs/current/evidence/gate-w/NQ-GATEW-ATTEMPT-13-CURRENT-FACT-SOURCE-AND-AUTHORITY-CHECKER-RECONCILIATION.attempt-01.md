# NQ-GATEW-ATTEMPT-13-CURRENT-FACT-SOURCE-AND-AUTHORITY-CHECKER-RECONCILIATION — Attempt 01

## 1. 结论

`PASS / CURRENT_FACT_SOURCE_RECONCILED / AUTHORITY_CHECKER_REGRESSION_ADDED / READY_TO_COMMIT`（通过 / current 事实源已协调 / authority checker 回归已增加 / 可进入提交前复核）。

本任务只修正 current README 摘要并增强本地文档治理 checker；未访问服务器、未读取 soak 运行证据、未执行 Attempt-13 acceptance，也未修改 GateW runtime、GateX、LIVE 或 preparation 分支。

## 2. 基线与 authority

- Main repository：`E:\Project\nexus-quant`。
- Branch：`dev`；起始 `HEAD == origin/dev == 0e19d52a72e9d1c53991069917a5411da720a10f`；起始 worktree/staging clean。
- GateW：`IN_PROGRESS / NOT_FROZEN`。
- Current attempt：Attempt-13=`RUNNING / PENDING_168H`；production deployment=`STARTED`。
- Runtime release：`b103069d8bfcecccba0b4d590317ddccc66898b9`。
- RunId：`gatew-soak-20260801T180544Z-140bbcd1`。
- Planned acceptance：`2026-08-08T18:13:13.9139125Z`。
- Canonical next action：`NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE`。
- Attempt-09 保持 `REJECTED / FAILED_INSUFFICIENT_DURATION`，未重新验收。

## 3. 实现范围

- `docs/current/README.md`：删除 Attempt-13 `NOT_CREATED` 与旧 preparation/start action；写入当前 `RUNNING / PENDING_168H`、计划验收时间、唯一下一动作和 Attempt-09 历史拒绝边界。
- `scripts/docs/check-current-authority.ps1`：增加 `ReadmePath`、`nq-current-summary` 稳定摘要边界解析、machine current attempt/status 对照，以及 `CURRENT_ATTEMPT_STATUS_CONFLICT`、`CURRENT_NEXT_ACTION_CONFLICT` fail-closed 错误。
- `scripts/docs/test-current-authority-next-action.ps1`：覆盖 README 正常摘要、Attempt 状态冲突、旧 next action、大小写冲突、缺失摘要边界和历史 Attempt-09 不误报。
- `scripts/docs/test-governance-workflow-lifecycle.ps1`：让 disposable authority fixture 生成相同 current-summary 合同，并使 running fixture 的 attempt/status/deployment 与 machine authority 一致。

`nq-current-summary` 仅界定 README 的当前摘要正文，不使用 key-value schema，也不替代 `STATUS.md` 的唯一 machine authority。

## 4. Checker 规则

1. 当 `work_batch` 是通用 `...-ATTEMPT-<N>` 且 `work_batch_status` 可映射为 attempt state / authorization state 时，从 machine authority 得到 current attempt 与状态。
2. README 必须有且仅有一个 `nq-current-summary` 区域；只在该区域读取 current attempt/status 与唯一动作。
3. README attempt/status 必须与 machine authority 一致，否则返回 `CURRENT_ATTEMPT_STATUS_CONFLICT`。
4. README 或 ROADMAP 的 current next action 必须与 machine `next_action` 精确、大小写敏感一致，否则返回 `CURRENT_NEXT_ACTION_CONFLICT`。
5. current-summary 之外的历史 Attempt-09 `REJECTED` 和 fenced code example 不参与 current attempt 判定。
6. Checker 不扫描 `docs/gates/**`、历史 evidence、远端 tag、GitHub Actions 或运行证据，也未修改 Gate lifecycle、archive/release checker 职责。

## 5. 验证

| 命令 | 环境 | 结果 |
| --- | --- | --- |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-current-authority-next-action.ps1` | Windows PowerShell 5.1 | `PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION` |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-governance-workflow-lifecycle.ps1` | Windows PowerShell 5.1 | `PASS / GOVERNANCE_LIFECYCLE_REGRESSION`；`PASS / TASK_EVIDENCE_POLICY_VALID`；243 条 PASS |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1` | Windows PowerShell 5.1 | `AUTHORITY_CHECK errors=0`；`PASS / CURRENT_AUTHORITY_CONSISTENT` |
| `pwsh -NoProfile -File scripts/docs/test-current-authority-next-action.ps1` | PowerShell 7 | 150 条 PASS；最终 PASS |
| `pwsh -NoProfile -File scripts/docs/test-governance-workflow-lifecycle.ps1` | PowerShell 7 | 243 条 PASS；最终 PASS |
| `pwsh -NoProfile -File scripts/docs/check-current-authority.ps1` | PowerShell 7 | `AUTHORITY_CHECK errors=0`；最终 PASS |

完整 lifecycle 首轮因旧 fixture 未生成 README current-summary 而 fail-closed；补齐 fixture 后，第二轮发现 running fixture 仍使用旧 `NOT_CREATED / AUTHORIZED / NOT_STARTED` 默认值。两处均在测试 fixture 内最小修复，最终 Windows PowerShell 5.1 与 PowerShell 7 全绿；未放宽生产 checker。

## 6. 边界与 Findings

- P0：0。
- P1：0。README current summary drift 已修复，checker 可永久拒绝同类状态/next-action 冲突。
- P2：0。
- P3：0。
- GateW runtime impact：0；Attempt-13 仍 `RUNNING / PENDING_168H`。
- GateX impact：0；仍 `NOT_STARTED`。
- Production code / migration：均未修改。
- LIVE：仍 `DISABLED`；未访问 credential、OKX、服务器、unit、PID、heartbeat 或 hash chain。
- Preparation branch：仍 `UNMERGED`，未修改。

## 7. 交付状态

- 本轮未执行 commit 或 push；exact-head CI=`NOT_RUN`。
- 推荐 commit：`fix(gatew): reconcile Attempt-13 current authority summaries`。
- 下一运营动作：`NQ-GATEW-ATTEMPT-13-168H-ACCEPTANCE`，只能在 `2026-08-08T18:13:13.9139125Z` 之后执行。
- 下一开发线动作：`NQ-ARCHITECTURE-CODE-ORGANIZATION-FULL-AUDIT`，保持只读。

Final decision：`PASS / CURRENT_FACT_SOURCE_RECONCILED / AUTHORITY_CHECKER_REGRESSION_ADDED / READY_TO_COMMIT`。
