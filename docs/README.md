# NexusQuant 文档入口

本目录只作为文档导航，不承载业务细节。当前事实以 `docs/current/` 为准；历史 Gate 与归档文档只读参考，不能作为当前开发依据。

## 当前入口

- 当前事实入口：`docs/current/README.md`
- 当前状态：`docs/current/STATUS.md`
- 当前路线：`docs/current/ROADMAP.md`
- 当前架构：`docs/current/ARCHITECTURE.md`
- 当前模块：`docs/current/MODULES.md`
- 当前 API：`docs/current/API.md`
- 当前数据库：`docs/current/DB_SCHEMA.md`
- 当前验证：`docs/current/TESTING.md`
- 当前运行手册：`docs/current/RUNBOOK.md`
- 当前工作日志：`docs/current/WORKLOG.md`
- 当前 GateJ 规划：`docs/current/PLAN_GATEJ.md`
- 当前 GateJ 工作单：`docs/current/GATEJ_WORK_ORDER.md`
- 文档清理报告：`docs/current/DOC_CLEAN_REPORT.md`

## 历史与规则

- 历史 Gate 卷宗：`docs/gates/`
  - `docs/gates/gate-h/`：GateH completed freeze snapshot（交易工作台、历史行情、dataset 绑定）
  - `docs/gates/gate-i/`：GateI completed freeze snapshot（虚拟币量化 V1 完整闭环）
  - `docs/gates/gate-j/`：GateJ completed freeze snapshot（Paper Trading 稳定运行与 GateJ-FREEZE 验收）
  - `docs/gates/gate-a..g/`：早期 Gate 历史卷宗
- 归档文档：`docs/archive/`
- 文档规则：`docs/DOC_RULES.md`
- 模板：`docs/templates/`

## 当前边界

- GateH completed。
- GateI completed。
- GateJ completed。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed。
- Next: GateK-PLAN。
- AI not started。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。
- UI/UX professionalism remains post-freeze remediation。

## 文档使用规则

- `docs/current` 是当前事实源，唯一开发入口。
- `docs/gates` 是历史 Gate 冻结卷宗，只读参考。
- 已完成 Gate 的计划文档（PLAN / WORK_ORDER / API_PLAN / DB_PLAN / FRONTEND_PLAN / TEST_PLAN）只保留在 `docs/gates/gate-x/`，不在 `docs/current/` 重复。
- `docs/archive` 只归档，不作为当前开发依据。
- 未冻结 Gate 的计划文档保留在 `docs/current/`，Gate 完成并冻结后迁移或复制到 `docs/gates/gate-x/`；当前事实仍以 `docs/current/` 为准。

## 文档治理（Documentation Governance）

> 以下为 G1 新增的治理入口（仅索引指针）。本节**不**修订上文导航或既有规则表述；当前控制文档的状态/导航漂移与“不重复 vs 迁移或复制”规则一致性留待 **G2** 处理。

- 权威入口索引：`docs/current/NQ_DOCS_AUTHORITY_INDEX.md`（每领域唯一当前权威 + 辅证 + 历史证据）。
- 历史证据索引：`docs/current/NQ_DOCS_EVIDENCE_INDEX.md`（GateJ freeze / CI Batch / 4C / 4F-A / backlog / DB / credential / NQ-DH 证据入口）。
- 逐文件迁移映射：`docs/current/NQ_DOCS_MIGRATION_MAP.md`（覆盖 278 基线 md/txt 的 recommended action / target / batch）。
- 治理计划与评审：`docs/current/NQ_DOCS_GOVERNANCE_PLAN.md`、`NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md`、`NQ_DOCS_G1_IMPLEMENTATION.md`。

治理原则（retain-first）：

- **先建索引/映射，再移动**；历史链接先 redirect 兼容，再目录收口。
- `docs/gates/**`、`docs/archive/**`、`.agents/**`、`docs/templates/**` 一律 **RETAIN_IN_PLACE**，不在 G1~G6 移动。
- 删除单独显式可审计批次，**默认不删除**；不通过删除/压缩历史证据实现“精简”。
- G1 仅新增索引，未移动/删除/重命名/归档任何文档；G2~G6 未开始。
