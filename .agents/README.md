# NexusQuant 能力与参考

本索引按任务影响查询，不是强制 router。普通 Java 和文档小改可零 Skill；历史目录不属于活跃规则，Skill 不授予运行或 Git 权限。各 Skill description 供 Codex/Claude/GPT-6 Astra 按任务语义选择，正文及 references 仅按需读取。

| 实际任务 | 能力与参考 |
| --- | --- |
| 普通 Java bug | 零 Skill；[工程标准索引](skills/nq-trading-correctness-proof/references/engineering-boundaries.md)命中主题 |
| reconciliation、订单/成交/账务/恢复正确性 | [交易证明](skills/nq-trading-correctness-proof/SKILL.md)及 proof-selection |
| schema、Flyway migration、回填 | [数据库迁移](skills/nq-postgres-migration-review/SKILL.md)及 database-proof |
| 页面、组件、CSS、交互 | [前端产品](skills/nq-frontend-state-design/SKILL.md)及 frontend-engineering 对应小节 |
| 发布验收、部署或恢复 | [发布部署](skills/nq-release-deployment/SKILL.md)及 delivery |
| research 可复现性、实验正式复用 | [Research](skills/nq-research-reproducibility/SKILL.md)及 engineering |
| README typo、普通历史说明 | 零 Skill；文本/相关链接/diff 检查 |

`strategy` 文案不自动选择交易证明，文档里的 `database` 不选择迁移，历史 `deploy` 不选择发布。先判断本次改变的行为及风险，再选能力；仅文件路径或关键词不足以升级风险。

跨领域测试、独立审查、Git、证据和 subagent 选择唯一维护于[回归与交付合同](skills/nq-trading-correctness-proof/references/regression-delivery.md)。重复故障按需查[机制参考](skills/nq-trading-correctness-proof/references/engineering-lessons.md)；普通 Python/helper 直接查[Python 工程](skills/nq-research-reproducibility/references/engineering.md)。

任务提示使用 [Work Order](../docs/templates/WORK_ORDER.md)。能力 inventory 与语义事实映射在 scripts/docs/agent-workflow-policy.json，回归验证该映射及安全下限，不模拟自然语言模型、固定 Skill 数量或要求每次调用 router。
