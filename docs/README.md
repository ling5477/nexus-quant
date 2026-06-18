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
- 权威入口索引：`docs/current/NQ_DOCS_AUTHORITY_INDEX.md`
- 历史证据索引：`docs/current/NQ_DOCS_EVIDENCE_INDEX.md`

> GateJ 已是 **completed historical gate**，其权威冻结卷宗在 `docs/gates/gate-j/`。以下 GateJ 文档为**历史/冻结证据入口**（`docs/current/` 内同名副本与 gate-j blob 一致，属 NON_AUTHORITATIVE / FUTURE_SUPERSEDE_CANDIDATE，G3 redirect 后收敛，权威以 gate-j 为准；详见 `docs/current/NQ_DOCS_MIGRATION_MAP.md` §1E）：

- GateJ 规划（历史/冻结证据，权威：`docs/gates/gate-j/PLAN_GATEJ.md`）：`docs/current/PLAN_GATEJ.md`
- GateJ 工作单（历史/冻结证据，权威：`docs/gates/gate-j/GATEJ_WORK_ORDER.md`）：`docs/current/GATEJ_WORK_ORDER.md`
- 文档清理报告（历史证据）：`docs/current/DOC_CLEAN_REPORT.md`

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

> 当前状态权威以 `docs/current/STATUS.md` 为准；本节为导航摘要。

- GateH / GateI / GateJ completed（GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed，均为 completed historical gate）。
- **NQ GateK CI mainline = COMPLETED / ACCEPTED**（GateK 产品/runtime 实现仍 not started）。
- **Batch 5A no-backend frontend E2E = FROZEN / ACCEPTED**（仅 4 个 no-backend smoke spec，**不是** authenticated/backend E2E coverage）。
- **Batch 5B-ENV runtime no-outbound = P1 SECURITY ENHANCEMENT / NOT STARTED**；**Batch 5B-SMOKE authenticated E2E = BLOCKED BY 5B-ENV**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**；**Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**。
- 文档治理：**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 current-control drift repair = IMPLEMENTED / READY FOR REVIEW**；G3~G6 = NOT STARTED。
- AI not started；DH integration not started / not connected to NQ；LIVE disabled；RealClient / real provider / real exchange adapter not implemented。
- Multi-exchange expansion not started；UI/UX professionalism remains post-freeze remediation。

## 文档使用规则

- `docs/current` 是当前事实源，唯一开发入口。
- `docs/gates` 是历史 Gate 冻结卷宗，只读参考。
- 已完成 Gate 的计划文档（PLAN / WORK_ORDER / API_PLAN / DB_PLAN / FRONTEND_PLAN / TEST_PLAN）只保留在 `docs/gates/gate-x/`，不在 `docs/current/` 长期并存重复。
- `docs/archive` 只归档，不作为当前开发依据。
- 未冻结 Gate 的计划文档保留在 `docs/current/`，Gate 完成并冻结后在 `docs/gates/gate-x/` 留权威冻结快照；当前事实仍以 `docs/current/` 为准。

> **“不重复” 与 “迁移或复制” 的关系**（G2 收敛，权威见 `docs/DOC_RULES.md` 规则 16）：Gate 冻结时在 `gate-x/` 留权威快照（复制/迁移），current 侧不长期并存 superseded 重复（不重复）；两者不矛盾。实际移除 current 重复副本属 **G3**，须 redirect-first，且 gate-x 权威副本永久保留。当前 `docs/current/` 内 17 份 GateJ superseded 副本尚未移除（G3 未开始），按上述规则其权威以 `docs/gates/gate-j/` 为准。

## 文档治理（Documentation Governance）

> G1 已冻结（authority/evidence index + migration map）；G2 已修复当前控制层导航/状态/规则/链接漂移（本轮）。G3~G6 未开始。

G1 冻结索引（FROZEN / ACCEPTED）：

- 权威入口索引：`docs/current/NQ_DOCS_AUTHORITY_INDEX.md`（每领域唯一当前权威 + 辅证 + 历史证据）。
- 历史证据索引：`docs/current/NQ_DOCS_EVIDENCE_INDEX.md`（GateJ freeze / CI Batch / 4C / 4F-A / backlog / DB / credential / NQ-DH 证据入口）。
- 逐文件迁移映射：`docs/current/NQ_DOCS_MIGRATION_MAP.md`（覆盖 278 基线 md/txt 的 recommended action / target / batch）。

Documentation Governance Evidence（review / freeze / G2 implementation；均 HISTORICAL_EVIDENCE / RETAIN_IN_PLACE，**不计入 G1 的 278 / 283 计数**）：

- 治理计划与计划评审：`docs/current/NQ_DOCS_GOVERNANCE_PLAN.md`、`docs/current/NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md`。
- G1 实施与评审：`docs/current/NQ_DOCS_G1_IMPLEMENTATION.md`、`docs/current/NQ_DOCS_G1_REVIEW.md`、`docs/current/NQ_DOCS_G1_FREEZE_REVIEW.md`。
- G2 实施：`docs/current/NQ_DOCS_G2_CURRENT_CONTROL_REPAIR.md`。

治理原则（retain-first）：

- **先建索引/映射，再移动**；历史链接先 redirect 兼容，再目录收口。
- `docs/gates/**`、`docs/archive/**`、`.agents/**`、`docs/templates/**` 一律 **RETAIN_IN_PLACE**，不在 G1~G6 移动。
- 删除单独显式可审计批次，**默认不删除**；不通过删除/压缩历史证据实现“精简”。
- G1 仅新增索引；G2 仅修复当前控制层漂移与可修链接，未移动/删除/重命名/归档任何文档，未改 G1 五份冻结对象、未改冻结快照正文或历史链接；G3~G6 未开始。

### 冻结快照内历史链接兼容入口

`docs/gates/gate-h/` 与 `docs/gates/gate-j/` 的 `API.md` / `DB_SCHEMA.md` 各有指向 `./GATEI_API_PLAN.md` / `./GATEI_DB_PLAN.md` 的历史相对链接（共 4 处），目标在各自 gate 目录内不存在（属冻结快照事实，**不改写**）。其真实目标为 GateI 冻结卷宗：

- GateI API 规划：`docs/gates/gate-i/GATEI_API_PLAN.md`
- GateI DB 规划：`docs/gates/gate-i/GATEI_DB_PLAN.md`
