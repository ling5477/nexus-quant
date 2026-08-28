---
name: nq-docs-writer
description: Write or reconcile NexusQuant documentation from verified repository facts, with Chinese-first prose, current-document ownership, conflict handling, and minimal documentation validation. Use for docs-only work or as an explicitly justified supporting Skill when implementation changes require documentation.
---

# NQ Docs Writer

本 Skill 只负责已验证事实的表达，不充当治理总控，不复制 archive、release 或任务生命周期。

## 1. 事实表达

- `docs/current/STATUS.md` 的 machine authority 是 current stage 与安全状态的唯一来源。
- Git、代码、测试和 CI 结果必须真实执行或可复验；未运行写“未验证”，失败不得写成通过。
- current 文档冲突时停止对应事实写入，输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`，列出冲突路径与证据。
- historical archive 仅用于追溯，不覆盖 current facts；不得重写 frozen history。

## 2. 中文与术语

- 说明性正文以简体中文为主；路径、命令、类型、字段、状态 token 与协议原文保留英文。
- 状态首次出现时给出中文含义，避免把 planning、implemented、reviewed、accepted、frozen 混写。
- 交易环境只引用仓库 canonical `SIM / LIVE`；venue `DEMO` 是适配层映射，历史 `DOME / REAL` 仅标记 legacy/import compatibility。

## 3. current docs 职责

- `STATUS.md`：机器 authority 与当前安全/阶段摘要。
- `ROADMAP.md`：下一允许动作与边界，不覆盖 STATUS。
- `API.md`：稳定对外 API 事实。
- `DB_SCHEMA.md`：schema、migration、索引、约束与注释事实。
- `TESTING.md`：真实验证结果及未验证项。
- `WORKLOG.md`：按时间追加的工作证据，不参与 current authority 判定。
- `FACT_SOURCE_INDEX.md`：各事实域的 owner 与优先级。

## 4. 写作预算

- 普通代码任务默认不改 docs；必要时仅追加最小 WORKLOG 记录。
- 测试基线可同步 TESTING/WORKLOG。
- current authority、治理合同、API、DB schema 或用户明确要求变化时，才修改对应 owner 文档。
- 不制造新的 plan/review/freeze 文档链，不为保持形式一致而复制历史段落。

## 5. Archive / Authority / Release

这些边界只引用：

- `scripts/docs/governance-workflow-contract.json`
- `scripts/docs/check-current-authority.ps1`
- `scripts/docs/gate-archive-manifest.json`
- `scripts/docs/check-gate-archive.ps1`
- `scripts/docs/check-gate-release.ps1`

不得在本 Skill 中维护 Gate-specific、Attempt-specific、Task-ID-specific lifecycle。

## 6. 最小验证

1. 检查事实源、术语、链接、路径与标题层级。
2. 运行 `scripts/docs/check-doc-links.ps1`；authority 变化时运行 `check-current-authority.ps1`。
3. 用 `git diff --check` 和范围 diff 确认没有修改 frozen archive 或业务代码。
4. 报告已验证、未验证、风险与回滚。

## A. Role

- Role type: `PRIMARY_EXECUTION`
- Primary responsibility: `VERIFIED_DOCUMENTATION`

本 Skill 独立负责把已验证的 repository facts 转换为最小、可追溯且归属正确的 NexusQuant 文档变更。

## B. Trigger

- Positive：docs-only 任务，或实现变化确实需要同步 owner 文档时作为明确 justified supporting Skill。
- Exclusion：没有事实变化的普通代码任务、Gate/release/archive lifecycle 决策、CI authorization、Task-ID mapping 和业务实现。

## C. Input / Context

先读取目标文档、`FACT_SOURCE_INDEX.md` 指向的 owner 和能够证明待写事实的最少代码、测试或 machine contract；authority 事实才读取 `STATUS.md` 与对应 checker。不得遍历全部 current/history 文档来制造上下文。

## D. Required Actions

1. Determine the document type and owning document.
2. Resolve the verified fact source.
3. Determine whether writing is authorized and necessary.
4. Reconcile conflicts or stop the affected write.
5. Make the smallest documentation change.
6. Validate changed facts, paths, links and protected boundaries.
7. Report changed facts and unresolved conflicts.

## E. Validation

- Required：changed facts 与证据一致，路径/链接/标题有效，范围 diff 无业务代码或 frozen history。
- Conditional：文档链接变更运行 `check-doc-links.ps1`；authority 变更运行 `check-current-authority.ps1`；machine governance 引用按对应 checker 验证。
- Not applicable：文档未涉及的 Maven、Playwright、shadow scan 和 release 网络验证。

## F. Output Contract

输出文档类型、fact source、changed facts、changed files、验证结果、未解决冲突、风险和回滚；未执行的事实验证必须标为未验证。

## G. Non-goals

不拥有 Gate lifecycle、release lifecycle、archive machine roles、Task-ID mapping 或 CI authorization；只引用对应 machine governance control，不复制其语义。

## H. Overlap / Ownership

本 Skill对 verified documentation 是 `PRIMARY_OWNER`；实现 Skill 仅提供事实与变更影响，machine governance 对 lifecycle/authority 是 `PRIMARY_OWNER`。交叉项以 responsibility matrix 为准。
