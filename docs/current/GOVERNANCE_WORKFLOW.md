# NQ Governance Workflow

本文是 NQ Gate 治理流程唯一的人类可读执行规则。机器可读状态、生命周期、`next_action`、evidence 路径和 hard blocker 枚举以 `scripts/docs/governance-workflow-contract.json` 为准；checker 通过 `scripts/docs/governance-workflow-lib.ps1` 读取同一契约，不得各自复制完整状态列表。

## 1. Checker 职责

| Checker | 只负责 | 明确不负责 |
| --- | --- | --- |
| `check-gate-archive.ps1` | strict manifest、mandatory/conditional role、role 唯一性、archive 路径、README link、non-role task evidence、unknown/empty/symlink fail-closed | current authority、work batch、`next_action`、CI、tag target、remote tag |
| `check-current-authority.ps1` | schema v3、active Gate、accepted/work batch、合法状态组合、commit/CI 字段格式、`next_action` 类型、固定安全边界、archive/freeze/release readiness status 前置 | Gate archive、role、tag object、GitHub Actions、remote tag |
| `check-gate-release.ps1` | release commit object、预期分支 ancestry、exact-HEAD CI、annotated tag、local/remote object 与 peeled target | archive role、work batch 状态、业务能力 |

`check-gate-archive.ps1 -PreTag` 只表示校验 archive candidate 的结构、role、link 和 evidence；tag 可以尚不存在，它不会读取 tag、CI 或 tagged authority。兼容参数要求 post-tag release validation 时，Archive wrapper 必须委托完整 Release checker，不得复制其实现。

`COMMITTED|CI_GREEN|CONTINUE_REQUIRED`（已提交 / CI 已通过 / 同 batch 仍需继续）不属于 freeze candidate，也不属于 accepted batch status。Archive/freeze workflow 必须先运行 `check-current-authority.ps1 -ReadinessMode ARCHIVE_FREEZE`，release workflow 必须先运行 `-ReadinessMode RELEASE`；两种 mode 都会在调用实际 Archive/Release checker 前拒绝 continuation。实际 Archive/Release checker 只验证已经满足前置的 archive/release 对象，不能把 continuation 状态升级为 freeze 或 release 授权。

## 2. 普通任务流程

普通任务完成实现、自审和必要测试后可直接进入提交，不强制独立 review：

```text
NOT_STARTED
→ IMPLEMENTED|SELF_REVIEWED
→ COMMITTED|CI_PENDING
→ ACCEPTED|CI_GREEN
```

`ACCEPTED|CI_GREEN` 表示整个 numbered work batch 已完成并被接受。若当前提交 exact-head CI 已成功，但同一 numbered work batch 仍有已授权后续工作，不得使用该终态，必须进入 `COMMITTED|CI_GREEN|CONTINUE_REQUIRED`。`IMPLEMENTED|PENDING_REVIEW` 与 `REVIEW_ACCEPTED|READY_TO_COMMIT` 不属于普通任务必经状态。机械 commit/push 不创建空洞 evidence，不为单个状态同步制造 docs-only commit。

## 3. 高风险任务流程

以下类型必须保留独立 review：migration、CI workflow、credential/secret、LIVE、real provider、private adapter、真实外联、交易写主链、risk/ledger/audit 核心语义、大范围 Spring 装配以及 P0/P1 修复。

```text
NOT_STARTED
→ IMPLEMENTED|PENDING_REVIEW
→ REVIEW_ACCEPTED|READY_TO_COMMIT
→ COMMITTED|CI_PENDING
→ ACCEPTED|CI_GREEN
```

review 未接受时不得直接写为 `ACCEPTED|CI_GREEN`。即使 exact-head CI 成功，只要同一 numbered work batch 仍有后续安全审查或受控实现，也不得推进 accepted batch。风险分类不确定时按高风险处理，并在 evidence 中说明判断依据。

## 4. Post-commit CI failure 生命周期

当 implementation 已提交且 exact-head CI 已实际完成并失败时，canonical 状态必须是 `COMMITTED|CI_FAILED|FIX_REQUIRED`（已提交 / CI 已失败 / 必须修复）。该状态保留 implementation commit 与已接受 review 的事实，不表示代码回滚、implementation 撤销、CI 尚未运行、CI 仍在运行或 batch 已接受。

字段不变量：

- `work_batch_commit` 必须是 concrete 40-char hexadecimal SHA；不得使用 `UNCOMMITTED`、`NONE`、`NOT_RUN`、空值或短 SHA。
- `work_batch_ci_run` 必须是正整数 GitHub Actions run ID；不得使用 `NOT_RUN`、`PENDING`、`NONE`、空值、非数字、0 或负数。状态本身已唯一表达 CI conclusion 为 failure，不新增重复的 conclusion/status 字段。
- `accepted_batch` 必须继续指向同一 active Gate 的上一真正 `ACCEPTED|CI_GREEN` batch；当前 failed `work_batch` 不得提前成为 accepted batch。
- `active_gate_status` 必须保持 `IN_PROGRESS|NOT_FROZEN`；failed 状态不得初始化下一 batch、进入 Freeze 或 release。
- 唯一 next-action 类型为 `CI_BLOCKER_FIX`。它只匹配明确的 `CI-BLOCKER-FIX` task，并允许 base、`-REVIEW`、`-COMMIT-AND-PUSH` 三个 phase；checker 还会把 action task prefix 绑定到当前 `work_batch`。

正常失败路径：

```text
COMMITTED|CI_PENDING
→ COMMITTED|CI_FAILED|FIX_REQUIRED
```

两端必须保持同一 implementation commit；失败状态把 `work_batch_ci_run` 固定为已完成失败 run 的正整数 ID。

当 current authority 落后于已经发生的 Git/CI 事实时，允许以下 reconciliation：

```text
REVIEW_ACCEPTED|READY_TO_COMMIT
→ COMMITTED|CI_FAILED|FIX_REQUIRED
```

这只是 authority catch-up，不是推荐的正常路径。它必须显式标记 reconciliation，且从 `UNCOMMITTED / NOT_RUN` 追赶到 concrete commit/failed run；正常路径仍应先进入 `COMMITTED|CI_PENDING`。

常规 CI blocker 修复提交 lifecycle 为：

```text
COMMITTED|CI_FAILED|FIX_REQUIRED
→ COMMITTED|CI_PENDING
```

该 transition 仅在 CI blocker fix 已完成 review、commit 并 push 后使用；`work_batch_commit` 必须换为新的 fix commit，`work_batch_ci_run` 使用 contract 的 `PENDING` 表达和既有 pending-CI action。之后只有整个 numbered work batch 已完成时才允许 `COMMITTED|CI_PENDING → ACCEPTED|CI_GREEN`；同 batch 仍有后续工作时必须走下一节的 continuation 状态。禁止从 failed 直接进入 `ACCEPTED|CI_GREEN`。

`BLOCKED` 表示提交前或执行中的前置条件阻断；`COMMITTED|CI_FAILED|FIX_REQUIRED` 表示代码已经提交且 exact-head CI 已完成并失败。`COMMITTED|CI_PENDING` 表示 CI 尚未完成或正在等待结果。三者不得互相替代。

该状态本身不是 Git rollback 指令。若最终选择 revert，revert 也必须经过 review、new commit、push 和 `COMMITTED|CI_PENDING`，再由 exact-head CI 决定是否接受；不得只改 authority 文案伪造恢复。

## 5. CI green 但同 batch 继续

`COMMITTED|CI_GREEN|CONTINUE_REQUIRED`（已提交 / CI 已通过 / 同 batch 仍需继续）是唯一 canonical continuation 状态。它表示当前 work batch 已有具体提交，该提交的 exact-head CI 已完成并成功，但同一个 numbered work batch 仍有明确、已授权的后续审查或实现。它不是 `ACCEPTED|CI_GREEN` 的 alias，也不初始化下一 numbered batch，不推进 `accepted_batch`，不冻结 active Gate。

两种 green 状态的边界：

- `ACCEPTED|CI_GREEN`：整个 numbered work batch 已完成并接受；可以推进 `accepted_batch`。
- `COMMITTED|CI_GREEN|CONTINUE_REQUIRED`：仅当前提交技术子切片已绿；`accepted_batch` 必须保留最近完整接受的前序 batch。

字段不变量：

- `work_batch_commit` 必须是 concrete 40-char hexadecimal SHA；`UNCOMMITTED`、`NONE`、空值、短 SHA 与非 hex 全部拒绝。
- `work_batch_ci_run` 必须是正整数 success run ID；`NOT_RUN`、`PENDING`、`NONE`、0 与负数全部拒绝。
- `accepted_batch_status` 必须保持 `ACCEPTED|CI_GREEN`，且 accepted batch 必须是 work batch 的已完成直接前序，不能等于或提前推进到当前 work batch。
- `active_gate_status` 必须保持 `IN_PROGRESS|NOT_FROZEN`；不得初始化下一 numbered batch、Gate Freeze 或 release。

CI success 继续采用单一事实表达：状态 token 本身表达当前 commit 的 exact-head CI `conclusion=success`，`work_batch_ci_run` 绑定该 run；不得新增 `work_batch_ci_conclusion`、`ci_result`、`ci_green` 或 `success_run`。Authority checker 仍是纯本地校验器，不访问 Git、GitHub 或网络；exact-head、conclusion 与 commit/run 对应关系由 task preflight 和 evidence 提供。

正常 pending 成功但 batch 继续：

```text
COMMITTED|CI_PENDING
→ COMMITTED|CI_GREEN|CONTINUE_REQUIRED
```

要求 commit、work batch、accepted batch 均不变；目标 CI run 为具体正整数；若 pending authority 已绑定具体 run ID，成功时必须保持同一 run，只有 `PENDING` 占位可落成具体 run；transition context 必须提供 `exactHeadMatch=true`、`ciConclusion=success`，且 next action 属于同一 work batch。

Post-fix success reconciliation：

```text
COMMITTED|CI_FAILED|FIX_REQUIRED
→ COMMITTED|CI_GREEN|CONTINUE_REQUIRED
```

该直接 transition 不是 failed → accepted。它只允许 `mode=POST_FIX_CI_SUCCESS_RECONCILIATION`，并要求 authority catch-up 显式开启、new fix commit 与 failed commit 不同、new success run 与 failed run 不同、`exactHeadMatch=true`、`ciConclusion=success`、work/accepted batch 均不变。仍必须拒绝 `COMMITTED|CI_FAILED|FIX_REQUIRED → ACCEPTED|CI_GREEN`。

同 batch 下一项完成但尚待 review：

```text
COMMITTED|CI_GREEN|CONTINUE_REQUIRED
→ IMPLEMENTED|PENDING_REVIEW
→ REVIEW_ACCEPTED|READY_TO_COMMIT
→ COMMITTED|CI_PENDING
```

第一条 transition 必须保持 work/accepted batch 不变，把 commit 重置为 `UNCOMMITTED`、CI run 重置为 `NOT_RUN`，并把 next action 绑定到同 batch review。`COMMITTED|CI_GREEN|CONTINUE_REQUIRED → ACCEPTED|CI_GREEN` 不存在；只有后续全部冻结内容完成并重新进入 `COMMITTED|CI_PENDING` 后，才允许最终 batch acceptance。

Continuation 的 canonical next-action 类型为 `SECURITY_RISK_REVIEW`。Matcher 只接受明确的 `SECURITY-RISK-REVIEW` 与可选 `ATTEMPT-<正整数>`；允许 `ATTEMPT-02`，拒绝 `ATTEMPT-00`、普通或模糊 review、`FIX`、`CI-BLOCKER-FIX`、implementation、archive、freeze 与 release task。shared library 与 authority checker 还会精确绑定 `NQ-<WORK_BATCH>-` prefix，因此 GateW-3 不接受 GateW-4、GateX 或 Gate-level Freeze action。

GateW-3 的 venue-rule facts 与 CI fix 已通过 exact-head CI，不等于整个 GateW-3 已完成；dry-run order preview 仍需 security/risk review attempt-02 与后续受控实现。因此 GateW-3 技术子切片可进入 continuation，但 `accepted_batch` 仍应保持 GateW-2，GateW-4 与 GateW Freeze 均不得启动。该 target authority 只能由独立 review 任务写入；本次 contract hardening 保持 `STATUS.md` 的旧 FAILED snapshot 不变。

## 6. Freeze 流程

Freeze 固定为：

```text
archive candidate + 人工审查
→ freeze candidate commit
→ exact-HEAD CI green
→ release closeout commit
→ exact-HEAD CI green
→ annotated tag
→ post-tag Archive + Release validation
```

Freeze candidate 可从 `IMPLEMENTED|PENDING_REVIEW` 直接提交，不强制创建 `REVIEW_ACCEPTED` authority commit。tag 创建前必须通过 candidate 与 release closeout 两次 exact-HEAD CI；tag 必须是 annotated tag，local/remote object 与 peeled target 必须一致，禁止移动或覆盖。

## 7. Active Gate 与 next_action

- 未冻结 active Gate 只使用 `active_gate_status=IN_PROGRESS|NOT_FROZEN`。
- planning 是否开始由 `work_batch_status` 表达，不再使用 `active_gate_status=PLAN|NOT_STARTED`。
- 合法 `work_batch_status`、状态到 canonical `next_action` 类型的映射由 machine contract 统一定义。
- checker 只接受可识别的 canonical action token；非法倒退、状态/action 不匹配和字段格式不符均 fail-closed。

## 8. Task evidence

Current evidence：

```text
docs/current/evidence/<line>/README.md
docs/current/evidence/<line>/<TASK-ID>.attempt-<NN>.md
```

Frozen archive evidence：

```text
docs/gates/<gate>/source/task-evidence/README.md
docs/gates/<gate>/source/task-evidence/<TASK-ID>.attempt-<NN>.md
```

规则：

- 每次实际任务使用不可覆盖的两位 attempt 序号；重跑新增 attempt。
- 普通 task evidence 与业务代码同一 commit 固化；`BLOCKED` attempt 可与后续成功 attempt 同一业务 commit 固化。
- `source/task-evidence/**` 是 non-role source evidence，不参与 mandatory/conditional role 计数。
- nested `source/task-evidence/README.md` 永不匹配 `archive-entry`；顶层 `docs/gates/<gate>/README.md` 仍是唯一 archive entry。
- 当前只允许 contract 明确列出的安全扩展名与文件名；空文件、路径穿越、编码穿越、symlink/reparse point、approved root 以外文件全部 fail-closed。
- approved evidence 不触发 `UNKNOWN_ARCHIVE_FILE`；其他未知 archive 文件继续失败。

## 9. Current 核心文档职责

- `STATUS.md`：唯一 current stage authority。
- `ROADMAP.md`：唯一下一允许路线，不覆盖 STATUS。
- `GOVERNANCE_WORKFLOW.md`：治理执行规则，不决定 current Gate。
- `FACT_SOURCE_INDEX.md`：authority 分层与 residual 索引。
- `TESTING.md` / `WORKLOG.md`：append-only evidence ledger，不参与 current Gate 判定。
- `docs/current/evidence/**`：当前任务的 durable attempt evidence，不复制阶段 authority。

## 10. Hard blocker 与 review 风险级别

Machine contract 固化 dirty/alignment/CI、authority conflict、archive allowlist/manifest/link、task evidence、release commit/CI/tag/remote 等 hard blocker。任何 hard blocker 必须输出精确 `BLOCKED`，不得用 warning 绕过。

- P0：可能破坏 release/tag、交易/凭证边界或允许错误状态推进，必须阻断。
- P1：checker 职责或契约不一致、evidence fail-open、exact-HEAD/remote proof 缺失，必须阻断。
- P2：不影响当前接受决定但应在后续真实任务修复的覆盖或可维护性问题。
- P3：表达、可读性或非阻断优化。

## 11. 禁止 churn

- 不为普通任务强制独立 review。
- 不为 Freeze 每个中间 authority 状态制造 docs-only commit。
- 不为机械 commit/push 创建空 evidence。
- 不把 plan/review/freeze 拆成多份重复文档；治理规则只维护本文与 machine contract。
- 后续 blocker 应回到职责边界、contract 或 regression matrix 一次性修复，不再按单个 blocker 在多个 checker 中追加兼容分支。

## 12. GateW 启用方式

本治理 contract 只定义状态、checker 与 evidence 约束，不授权 GateW 业务实现、GateW-4、Freeze、release、LIVE 或交易写侧。GateW 真实任务继续在 `docs/current/evidence/gate-w/` 创建不可覆盖 attempt；GateW Freeze 时再把 accepted attempts 复制到 `docs/gates/gate-w/source/task-evidence/`，并由 Archive 与 Release checker 分别验证。current Gate、work batch 与下一动作始终只读取 `STATUS.md`；本文件中的 GateW-3 continuation 说明只是 contract 应用示例，不是 authority reconciliation。
