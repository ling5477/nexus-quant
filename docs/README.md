# NexusQuant 文档入口

本目录只做导航，不承载动态阶段副本。

## Current

- [状态与 machine authority](current/STATUS.md)
- [下一允许动作](current/ROADMAP.md)
- [事实源索引](current/FACT_SOURCE_INDEX.md)
- [治理流程](current/GOVERNANCE_WORKFLOW.md)
- [架构](current/ARCHITECTURE.md)
- [模块](current/MODULES.md)
- [API](current/API.md)
- [数据库](current/DB_SCHEMA.md)
- [测试证据](current/TESTING.md)
- [工作日志](current/WORKLOG.md)
- [运行手册](current/RUNBOOK.md)

## Audit / History

- [Repository Audit Bootstrap Charter](audit/AUDIT_BOOTSTRAP_CHARTER.md)
- `docs/gates/**`：frozen Gate 卷宗，只读历史事实。
- `docs/archive/**`：通用历史归档，不参与 current 判定。
- [文档规则](DOC_RULES.md)

动态 Gate、tag、work batch、LIVE 与 kill switch 只从 `current/STATUS.md` 的 machine block 读取；README、Skill、模板和 archive 均不得复制为独立 current authority。
