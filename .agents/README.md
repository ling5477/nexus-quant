# NexusQuant Active Skills

`.agents/skills/**` 是唯一 active Skill 根目录；`.agents/history/**` 和 `.agents.audit-subject/**` 均为 `HISTORICAL / NON_AUTHORITATIVE`，不得自动加载。

<!-- nq-active-skills:start
db-schema-migration-review
frontend-antd-page-builder
frontend-product-ui-design
frontend-quality-regression
java-backend-maintenance
java-backend-regression-tests
nq-dh-workflow-router
nq-docs-writer
nq-java-engineering-standard
python-ops-tooling
python-project-development
ui-visual-system-polish
nq-active-skills:end -->

## 路由原则

- 每个任务最多一个 primary Skill；supporting Skill 必须记录显式触发理由。
- Router 只做 repository/status 解析、任务与风险分类、Skill 选择和 scope 定义。
- 普通 Java 不默认加载 `nq-java-engineering-standard`；高风险触发条件见根 `AGENTS.md` 与该 Skill。
- Skill 只能引用仓库 canonical `SIM / LIVE` 术语；venue `DEMO` 映射与历史 `DOME / REAL` 兼容不构成新的业务环境。
- active 集合由 `scripts/docs/test-agent-workflow-fixtures.ps1` 与实际目录双向校验。

## 历史材料

- [MERGE_MAP.md](history/MERGE_MAP.md)：历史 Skill 合并说明，仅供追溯。
- [AGENTS.frontend-skill-routing.md](history/AGENTS.frontend-skill-routing.md)：旧前端路由片段，仅供追溯。
