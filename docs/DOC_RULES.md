# Documentation Rules

1. `docs/current/STATUS.md` 的 machine block 是 current stage、安全状态与 next action 的唯一 authority。
2. `docs/current/**` 只保存当前能力、路线、运行说明和 evidence ledger；不得保存重复的历史流程链。
3. `docs/gates/**` 是 frozen Gate 证据，`docs/archive/**` 是历史归档；二者不覆盖 current authority，也不得就地改写。
4. 文档事实必须来自 Git、代码、测试、CI 或明确 authority；未执行验证写“未验证”，失败不得写成通过。
5. current 事实冲突时停止对应写入并输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`。
6. 说明性正文以简体中文为主；路径、命令、代码标识、状态 token 与协议原文保留英文。
7. 文档任务最小化：普通代码默认不改 docs；测试基线按需更新 TESTING/WORKLOG；authority、API、DB schema 或治理合同变化才更新对应 owner。
8. 不制造 plan/review/freeze 文档链；freeze archive 角色由 machine manifest/checker 决定。
9. 历史迁移 retain-first：先有索引和兼容指针，再做独立、可审查、可回滚的移动；默认不删除。
10. credential、Secret、Cookie、原始签名串、生产数据和未脱敏响应不得写入文档或 evidence。
11. canonical 交易环境是 `SIM / LIVE`；venue `DEMO` 只能表达对 `SIM` 的适配映射，历史 `DOME / REAL` 只标记 legacy/import compatibility。
12. 全仓审计从 machine policy 的 `audit.bootstrapCharter` 解析唯一 Charter，默认只读且不自动整改、发布或修改 authority；字段或目标无效时 fail-closed。
13. 提交前运行最相关的 authority、links、workflow/fixture checker 与 `git diff --check`，并列出未验证项和回滚。
