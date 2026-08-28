---
name: nq-dh-workflow-router
description: Route NexusQuant repository work by resolving the repository, reading current STATUS authority, classifying task and risk, choosing one primary Skill plus explicitly justified supporting Skills, and defining scope. Use for NQ governance, mixed-scope, Gate, audit, freeze, release, credential, trading, or otherwise ambiguous repository tasks.
---

# NQ Workflow Router

本 Skill 只负责路由，不复制 Java、文档、archive、release、security 或交易领域实现规则。

## 1. Repository 与 authority

1. 用 `git rev-parse --show-toplevel` 确认唯一仓库。
2. 读取根 `AGENTS.md` 与 `docs/current/STATUS.md` 的 `nq-current-authority` 区块。
3. 当前事实冲突时输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。
4. 全仓审计从 `scripts/docs/agent-workflow-policy.json` 的 `audit.bootstrapCharter` 解析并读取 repository-declared Audit Bootstrap Charter；字段缺失、路径非 canonical 或目标不存在时 fail-closed。被审计 Skill/checker 不是 authority。

不得从分支名、任务名、旧会话、archive 或模型记忆推断 current state。

## 2. 分类

为任务记录一个 primary task type 和一个 risk level。机器口径以 `scripts/docs/agent-workflow-policy.json` 为准。

- task type：后端实现、后端测试、前端实现、migration、CI、文档、安全审计、credential/真实交易、freeze、release、blocked 或全仓审计。
- risk：`ORDINARY`、`HIGH_RISK`、`AUDIT`、`BLOCKED`。

credential、真实交易、authority mutation、migration、CI 权限、安全边界、freeze/release 默认 `HIGH_RISK`；缺少明确授权时 fail-closed。

## 3. Skill 选择

- 最多一个 primary Skill。
- supporting Skill 仅在主 Skill 无法覆盖一个明确子范围时选择，并在执行前记录触发理由。
- 插件按当前能力缺口选择，不固定完整流水线。
- active Skill 只来自 `.agents/README.md` 与实际 `.agents/skills/**` 的交集；缺失或未声明即阻断治理验证。
- 普通 Java primary 为 `java-backend-maintenance`；测试 primary 为 `java-backend-regression-tests`。
- `nq-java-engineering-standard` 只能作为高风险 Java supporting Skill：跨模块架构、大范围 Spring wiring、事务/并发核心、trading/risk/ledger/audit 核心、Java/Spring/Maven 版本升级、ArchUnit/Checkstyle/PMD/SpotBugs 规则变化或全仓 Java 审计。
- 文档 primary 为 `nq-docs-writer`；archive/release 规则只引用 machine contract/checker，不在 Skill 中重写。

## 4. Scope

执行前输出：repository、target files、excluded files、expected output、validation、权限边界。默认排除生成物、缓存、日志、凭证和任务无关模块；只有明确的全仓审计可扩大只读范围。

Router 不授予网络、credential、server、Git publication、authority mutation 或真实外部副作用权限。

## A. Role

- Role type: `ROUTER`
- Primary responsibility: `ROUTING_CLASSIFICATION`

本 Skill 是 routing-only owner：把当前 repository authority、任务类型和风险转换为一个 primary Skill、必要的 supporting Skills 与最小执行范围。

## B. Trigger

- Positive：NexusQuant governance、混合范围、Gate、audit、freeze、release、credential、交易相关或 primary Skill 不明确的任务。
- Exclusion：primary Skill 已由有效 machine route 唯一确定的普通领域任务，以及任何领域实现、测试设计或文档写作本身。

## C. Input / Context

只读取 repository root、根 `AGENTS.md`、`docs/current/STATUS.md` 的 machine authority、`scripts/docs/agent-workflow-policy.json` 和与分类直接相关的目标路径；仅全仓审计按 policy 解析 Audit Bootstrap Charter。不得预加载所有 Skills 或领域标准库。

## D. Required Actions

1. Detect the repository.
2. Read current authority.
3. Classify the primary task type.
4. Classify risk and authorization boundaries.
5. Select exactly one primary Skill.
6. Select only justified supporting Skills.
7. Define included scope, excluded scope, expected output and validation class.

## E. Validation

- Required：repository 唯一、authority 无冲突、primary Skill 唯一且 active、scope 明确。
- Conditional：全仓审计验证 charter 路径；高风险 route 验证独立 review 与权限限制。
- Not applicable：Maven、Playwright、migration、文档链接等领域验证，由执行或验证 owner 决定。

## F. Output Contract

输出 repository、authority result、task type、risk、primary Skill、supporting Skills 及理由、included/excluded scope、validation class 和 blocker；不输出伪造的领域完成结论。

## G. Non-goals

不实现业务、不设计测试、不维护 docs/archive/release lifecycle、不执行完整验证、不解释 Java standards，也不授予任何额外权限。

## H. Overlap / Ownership

Router 对分类与选择是 `PRIMARY_OWNER`；被选择的领域 Skill 对实现或验证是 `PRIMARY_OWNER`，Router 仅为 `SUPPORTING_OWNER`。具体 pair 与唯一 ownership 以 machine responsibility matrix 为准。
