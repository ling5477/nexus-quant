# NQ Governance Workflow

本文是 NQ Gate 治理流程唯一的人类可读执行规则。机器可读状态、生命周期、`next_action`、evidence 路径和 hard blocker 枚举以 `scripts/docs/governance-workflow-contract.json` 为准；checker 通过 `scripts/docs/governance-workflow-lib.ps1` 读取同一契约，不得各自复制完整状态列表。

## 1. Checker 职责

| Checker | 只负责 | 明确不负责 |
| --- | --- | --- |
| `check-gate-archive.ps1` | strict manifest、mandatory/conditional role、role 唯一性、archive 路径、README link、non-role task evidence、unknown/empty/symlink fail-closed | current authority、work batch、`next_action`、CI、tag target、remote tag |
| `check-current-authority.ps1` | schema v3、active Gate、accepted/work batch、合法状态组合、commit/CI 字段格式、`next_action` 类型、固定安全边界 | Gate archive、role、tag object、GitHub Actions、remote tag |
| `check-gate-release.ps1` | release commit object、预期分支 ancestry、exact-HEAD CI、annotated tag、local/remote object 与 peeled target | archive role、work batch 状态、业务能力 |

`check-gate-archive.ps1 -PreTag` 只表示校验 archive candidate 的结构、role、link 和 evidence；tag 可以尚不存在，它不会读取 tag、CI 或 tagged authority。兼容参数要求 post-tag release validation 时，Archive wrapper 必须委托完整 Release checker，不得复制其实现。

## 2. 普通任务流程

普通任务完成实现、自审和必要测试后可直接进入提交，不强制独立 review：

```text
NOT_STARTED
→ IMPLEMENTED|SELF_REVIEWED
→ COMMITTED|CI_PENDING
→ ACCEPTED|CI_GREEN
```

`IMPLEMENTED|PENDING_REVIEW` 与 `REVIEW_ACCEPTED|READY_TO_COMMIT` 不属于普通任务必经状态。机械 commit/push 不创建空洞 evidence，不为单个状态同步制造 docs-only commit。

## 3. 高风险任务流程

以下类型必须保留独立 review：migration、CI workflow、credential/secret、LIVE、real provider、private adapter、真实外联、交易写主链、risk/ledger/audit 核心语义、大范围 Spring 装配以及 P0/P1 修复。

```text
NOT_STARTED
→ IMPLEMENTED|PENDING_REVIEW
→ REVIEW_ACCEPTED|READY_TO_COMMIT
→ COMMITTED|CI_PENDING
→ ACCEPTED|CI_GREEN
```

review 未接受时不得直接写为 `ACCEPTED|CI_GREEN`。风险分类不确定时按高风险处理，并在 evidence 中说明判断依据。

## 4. Freeze 流程

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

## 5. Active Gate 与 next_action

- 未冻结 active Gate 只使用 `active_gate_status=IN_PROGRESS|NOT_FROZEN`。
- planning 是否开始由 `work_batch_status` 表达，不再使用 `active_gate_status=PLAN|NOT_STARTED`。
- 合法 `work_batch_status`、状态到 canonical `next_action` 类型的映射由 machine contract 统一定义。
- checker 只接受可识别的 canonical action token；非法倒退、状态/action 不匹配和字段格式不符均 fail-closed。

## 6. Task evidence

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

## 7. Current 核心文档职责

- `STATUS.md`：唯一 current stage authority。
- `ROADMAP.md`：唯一下一允许路线，不覆盖 STATUS。
- `GOVERNANCE_WORKFLOW.md`：治理执行规则，不决定 current Gate。
- `FACT_SOURCE_INDEX.md`：authority 分层与 residual 索引。
- `TESTING.md` / `WORKLOG.md`：append-only evidence ledger，不参与 current Gate 判定。
- `docs/current/evidence/**`：当前任务的 durable attempt evidence，不复制阶段 authority。

## 8. Hard blocker 与 review 风险级别

Machine contract 固化 dirty/alignment/CI、authority conflict、archive allowlist/manifest/link、task evidence、release commit/CI/tag/remote 等 hard blocker。任何 hard blocker 必须输出精确 `BLOCKED`，不得用 warning 绕过。

- P0：可能破坏 release/tag、交易/凭证边界或允许错误状态推进，必须阻断。
- P1：checker 职责或契约不一致、evidence fail-open、exact-HEAD/remote proof 缺失，必须阻断。
- P2：不影响当前接受决定但应在后续真实任务修复的覆盖或可维护性问题。
- P3：表达、可读性或非阻断优化。

## 9. 禁止 churn

- 不为普通任务强制独立 review。
- 不为 Freeze 每个中间 authority 状态制造 docs-only commit。
- 不为机械 commit/push 创建空 evidence。
- 不把 plan/review/freeze 拆成多份重复文档；治理规则只维护本文与 machine contract。
- 后续 blocker 应回到职责边界、contract 或 regression matrix 一次性修复，不再按单个 blocker 在多个 checker 中追加兼容分支。

## 10. GateW 启用方式

本治理任务只启用 GateW evidence 路径与 checker contract，不启动 GateW planning 或业务实现。GateW 后续真实任务从 `docs/current/evidence/gate-w/` 创建 attempt evidence；GateW Freeze 时再把 accepted attempts 复制到 `docs/gates/gate-w/source/task-evidence/`，并由 Archive 与 Release checker 分别验证。current Gate、work batch 与下一动作仍只读取 `STATUS.md`。
