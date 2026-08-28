# Governance Workflow

本文是 NQ 通用治理流程的人类可读入口。机器状态、next-action 分类、生命周期、安全 profile、evidence 与 release 规则以 `scripts/docs/governance-workflow-contract.json` 为准；checker 通过 `scripts/docs/governance-workflow-lib.ps1` 读取同一合同。

## 1. 通用生命周期

普通任务：

```text
NOT_STARTED
→ IMPLEMENTED|SELF_REVIEWED
→ COMMITTED|CI_PENDING
→ ACCEPTED|CI_GREEN
```

高风险任务：

```text
NOT_STARTED
→ IMPLEMENTED|PENDING_REVIEW
→ REVIEW_ACCEPTED|READY_TO_COMMIT
→ COMMITTED|CI_PENDING
→ ACCEPTED|CI_GREEN
```

合同另行定义 `CI_FAILED`、`BLOCKED`、`FREEZE`、`RELEASE` 与可选 `SOAK` 转移。通用运行合同不得绑定具体 Gate、Attempt、RC task 或 task ID；历史例外只能存在于 frozen profile、历史 fixture 或 archive evidence。

## 2. current authority

- `docs/current/STATUS.md` 的 `nq-current-authority` 区块是 current stage、安全状态、work batch 与 next action 的唯一 authority。
- machine authority 行必须使用 canonical `key=value` 格式；key、value 或整行的前后空白均拒绝，不通过 `Trim()` 修复错误输入。
- `check-current-authority.ps1` 校验 schema、状态、commit/CI 字段、唯一 next-action 类型和合同定义的完整 safety/runtime profile。
- next-action 必须只命中一个合同 matcher；0 个命中为 `UNKNOWN`，多个命中为 `AMBIGUOUS`，两者都 fail-closed。
- safety state 的 required fields 与 allowed exact values 全部由合同 profile 决定；未来合法升级只修改可审查 policy/profile，不在 checker 源码中增加 Gate 专项分支。
- current facts 冲突时输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。

## 3. 任务路由

- 人类规则：根 `AGENTS.md` 与 `.agents/README.md`。
- machine policy：`scripts/docs/agent-workflow-policy.json`。
- regression fixtures：`scripts/docs/agent-workflow-fixtures.json` 与 `test-agent-workflow-fixtures.ps1`；每个 fixture 保存完整 expected behavior，测试 exact match、enum 与跨字段安全 invariant，并对恶意 permission/review mutation 做拒绝回归。
- 每个任务最多一个 primary Skill；supporting Skill 必须有显式理由。

## 4. Audit

全仓审计必须从 machine policy 的 `audit.bootstrapCharter` 解析并读取唯一 repository-declared Audit Bootstrap Charter；字段或目标无效时 fail-closed。audit 默认只读，被审计 Agent/Skill/checker 的自我声明不构成 authority；Inventory 不自动整改、不修改 authority、不发布、不触发真实外部副作用。

## 5. Archive 与 Release

- `gate-archive-manifest.json` 提供 future Gate 默认严格策略；frozen Gate 的额外要求保存在 `historicalProfiles`。
- `check-gate-archive.ps1` 保持 unknown file fail-closed、reparse rejection、mandatory role、thin-role、evidence path 与 README link 检查。
- `check-gate-release.ps1` 独立验证 annotated tag、local/remote object、peeled target、branch ancestry 与 exact-head CI；workflow、head、status、conclusion、databaseId 必须来自同一个 CI run object。
- Archive checker 不重写 release 校验；要求 release 时在当前 PowerShell host 内委托完整 release checker，避免 host 降级。

## 6. 验证入口

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-doc-links.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-gate-release-ci-runs.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-current-authority-next-action.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-gate-archive-manifest.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-governance-workflow-lifecycle.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-agent-workflow-fixtures.ps1
```

未执行不得写成通过；commit、push、PR、tag、部署与服务器/credential/交易操作必须另有显式授权。
