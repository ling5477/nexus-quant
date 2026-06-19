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

- GateJ 规划（权威：`docs/gates/gate-j/PLAN_GATEJ.md`；旧 current 兼容 stub 已归档至 `docs/evidence/compatibility/gatej-current-stubs/PLAN_GATEJ.md`）
- GateJ 工作单（权威：`docs/gates/gate-j/GATEJ_WORK_ORDER.md`；兼容 stub 已归档至 `docs/evidence/compatibility/gatej-current-stubs/GATEJ_WORK_ORDER.md`）
- 文档清理报告（权威：`docs/gates/gate-j/DOC_CLEAN_REPORT.md`；兼容 stub 已归档至 `docs/evidence/compatibility/gatej-current-stubs/DOC_CLEAN_REPORT.md`）

## 历史与规则

- 历史 Gate 卷宗：`docs/gates/`
  - `docs/gates/gate-h/`：GateH completed freeze snapshot（交易工作台、历史行情、dataset 绑定）
  - `docs/gates/gate-i/`：GateI completed freeze snapshot（虚拟币量化 V1 完整闭环）
  - `docs/gates/gate-j/`：GateJ completed freeze snapshot（Paper Trading 稳定运行与 GateJ-FREEZE 验收）
  - `docs/gates/gate-a..g/`：早期 Gate 历史卷宗
- 证据归档：`docs/evidence/`
  - `docs/evidence/ci/`：CI historical evidence（canonical；导航 `docs/evidence/ci/README.md`）
  - `docs/evidence/governance/`：文档治理 G1～G6 plan/review/freeze/implementation/final freeze 过程证据（导航 `docs/evidence/governance/README.md`）
  - `docs/evidence/compatibility/gatej-current-stubs/`：GateJ 旧 current 路径兼容 stub 归档副本（canonical 仍在 `docs/gates/gate-j/`）
  - `docs/evidence/compatibility/ci-current-stubs/`：CI 旧 current 路径兼容 stub 归档副本（canonical 仍在 `docs/evidence/ci/`）
- baseline 索引：`docs/baselines/CI_BASELINE_INDEX.md`
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
- 文档治理：**G1～G6 = FROZEN / ACCEPTED**；**NQ Docs Governance Consolidation = FROZEN / ACCEPTED**（过程证据见 `docs/evidence/governance/`）。当前进行 **current 目录物理瘦身（NQ-DOCS-CURRENT-CLEANUP，Round 1/3 IMPLEMENTED / READY FOR REVIEW）**，详见 `docs/current/NQ_DOCS_CURRENT_CLEANUP_R1_IMPLEMENTATION.md`。
- AI not started；DH integration not started / not connected to NQ；LIVE disabled；RealClient / real provider / real exchange adapter not implemented。
- Multi-exchange expansion not started；UI/UX professionalism remains post-freeze remediation。

## 文档使用规则

- `docs/current` 是当前事实源，唯一开发入口。
- `docs/gates` 是历史 Gate 冻结卷宗，只读参考。
- 已完成 Gate 的计划文档（PLAN / WORK_ORDER / API_PLAN / DB_PLAN / FRONTEND_PLAN / TEST_PLAN）只保留在 `docs/gates/gate-x/`，不在 `docs/current/` 长期并存重复。
- `docs/archive` 只归档，不作为当前开发依据。
- 未冻结 Gate 的计划文档保留在 `docs/current/`，Gate 完成并冻结后在 `docs/gates/gate-x/` 留权威冻结快照；当前事实仍以 `docs/current/` 为准。

> **“不重复” 与 “迁移或复制” 的关系**（G2 收敛，权威见 `docs/DOC_RULES.md` 规则 16）：Gate 冻结时在 `gate-x/` 留权威快照（复制/迁移），current 侧不长期并存 superseded 重复（不重复）；两者不矛盾。GateJ superseded 副本已由 G3 收敛为 redirect-first 兼容 stub，gate-j 权威副本永久保留。当前 cleanup（Round 1/3）进一步把这些兼容 stub 从 `docs/current/` 物理移出归档到 `docs/evidence/compatibility/gatej-current-stubs/`（3 份因受保护 DIVERGED 入链标记 BLOCKED_PER_FILE 暂留 current），canonical 仍以 `docs/gates/gate-j/` 为准；未删除任何历史正文。

## 文档治理（Documentation Governance）

> G1～G6 全链路已冻结（**NQ Docs Governance Consolidation = FROZEN / ACCEPTED**）。当前在最终冻结之后做 current 目录物理瘦身（NQ-DOCS-CURRENT-CLEANUP，Round 1/3）。

G1 冻结索引（FROZEN / ACCEPTED；仍在 current 作为当前权威基线）：

- 治理计划：`docs/current/NQ_DOCS_GOVERNANCE_PLAN.md`。
- 权威入口索引：`docs/current/NQ_DOCS_AUTHORITY_INDEX.md`（每领域唯一当前权威 + 辅证 + 历史证据）。
- 历史证据索引：`docs/current/NQ_DOCS_EVIDENCE_INDEX.md`（GateJ freeze / CI Batch / 4C / 4F-A / backlog / DB / credential / NQ-DH 证据入口）。
- 逐文件迁移映射：`docs/current/NQ_DOCS_MIGRATION_MAP.md`（覆盖 278 基线 md/txt 的 recommended action / target / batch）。
- G1 实施记录：`docs/current/NQ_DOCS_G1_IMPLEMENTATION.md`。

Documentation Governance Evidence（G1～G6 plan / review / freeze / implementation / final freeze 过程证据；均 HISTORICAL_EVIDENCE / RETAIN_IN_PLACE，**不计入 G1 的 278 / 283 计数**）：

- 已从 `docs/current/` 物理移出并归档到 **`docs/evidence/governance/`**（导航 `docs/evidence/governance/README.md`），正文按 `git mv` 原样保留，未改写。

治理原则（retain-first）：

- **先建索引/映射，再移动**；历史链接先 redirect 兼容，再目录收口。
- `docs/gates/**`、`docs/archive/**`、`.agents/**`、`docs/templates/**` 一律 **RETAIN_IN_PLACE**，不在 G1~G6 移动。
- 删除单独显式可审计批次，**默认不删除**；不通过删除/压缩历史证据实现“精简”。
- G1 仅新增索引；G2 仅修复当前控制层漂移与可修链接，未移动/删除/重命名/归档任何文档，未改 G1 五份冻结对象、未改冻结快照正文或历史链接；G3~G6 未开始。

### 冻结快照内历史链接兼容入口

`docs/gates/gate-h/` 与 `docs/gates/gate-j/` 的 `API.md` / `DB_SCHEMA.md` 各有指向 `./GATEI_API_PLAN.md` / `./GATEI_DB_PLAN.md` 的历史相对链接（共 4 处），目标在各自 gate 目录内不存在（属冻结快照事实，**不改写**）。其真实目标为 GateI 冻结卷宗：

- GateI API 规划：`docs/gates/gate-i/GATEI_API_PLAN.md`
- GateI DB 规划：`docs/gates/gate-i/GATEI_DB_PLAN.md`
