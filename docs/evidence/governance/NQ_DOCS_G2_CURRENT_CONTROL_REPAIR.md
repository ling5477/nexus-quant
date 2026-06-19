# NQ Documentation Governance — G2 Current-Control Drift Repair

任务：`NQ-DOCS-GOVERNANCE-G2-CURRENT-CONTROL-DRIFT-REPAIR`

日期：2026-06-18

状态：**G2 = IMPLEMENTED / READY FOR REVIEW**

任务类型：DOCUMENTATION_GOVERNANCE_IMPLEMENTATION + CURRENT_CONTROL_CONSOLIDATION + NAVIGATION_REPAIR + LINK_HYGIENE

> 本轮只修复当前控制层文档的导航/状态/规则/可修链接漂移。**未移动、删除、重命名、归档任何文档**；**未修改 G1 五份冻结对象**；**未改写 `docs/gates/**`/`docs/archive/**` 冻结正文或历史链接**；未改 `.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration、测试、依赖。

---

## 0. G1 冻结边界（本轮严格遵守，未触碰）

- 原始治理基线 **278** / G1 implementation snapshot **283** —— **未回写、未重算、未修改**。
- review/freeze/G2 文档（含本文）= **HISTORICAL_EVIDENCE / RETAIN_IN_PLACE**，**不计入 278 / 283**。
- G1 五份冻结对象（`NQ_DOCS_GOVERNANCE_PLAN.md` / `NQ_DOCS_AUTHORITY_INDEX.md` / `NQ_DOCS_EVIDENCE_INDEX.md` / `NQ_DOCS_MIGRATION_MAP.md` / `NQ_DOCS_G1_IMPLEMENTATION.md`）**本轮 working-tree diff = 0**。

---

## 1. `docs/README.md` 导航漂移修复

- **§当前入口**：移除 “当前 GateJ 规划 / 当前 GateJ 工作单” 的 *current* 定位，改为**历史/冻结证据入口**——`PLAN_GATEJ.md` / `GATEJ_WORK_ORDER.md` 标注 NON_AUTHORITATIVE / FUTURE_SUPERSEDE_CANDIDATE，权威指向 `docs/gates/gate-j/`（未删除导航，仅改语义）。新增当前控制入口：`NQ_DOCS_AUTHORITY_INDEX.md`、`NQ_DOCS_EVIDENCE_INDEX.md`（`README/STATUS/ROADMAP` 已在入口）。
- **§当前边界**：从 “Next: GateK-PLAN” 单点扩展为当前事实摘要——GateJ = completed historical gate；**NQ GateK CI mainline = COMPLETED / ACCEPTED**（GateK 产品/runtime not started）；**Batch 5A = FROZEN / ACCEPTED**（明确仅 4 个 no-backend smoke spec，**非** authenticated/backend E2E coverage）；**5B-ENV = P1 / NOT STARTED**；**5B-SMOKE = BLOCKED BY 5B-ENV**；**4F-B~4F-F = OPTIONAL BACKLOG / NOT STARTED**；static assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED；**G1 = FROZEN / ACCEPTED；G2 = IMPLEMENTED / READY FOR REVIEW；G3~G6 = NOT STARTED**。
- **§文档治理**：更新 G1 “留待 G2” 旧注；新增 **Documentation Governance Evidence** 导航 + 冻结快照历史链接兼容入口（见 §4）。

## 2. `docs/DOC_RULES.md` 规则冲突收敛

- 新增 **规则 16**，以 5 级优先级收敛 “文档不重复” 与 “迁移或复制” 的张力：① 冻结证据保留优先 → ② current control 单一权威优先 → ③ 迁移前先建 index/redirect/compatibility mapping → ④ 复制仅限过渡导航/必要快照且必须标注 authority → ⑤ 不得为减少文件数删除或改写历史证据。
- 结论：“不重复”=current 不长期并存 superseded 重复；“迁移或复制”=Gate 冻结在 gate-x 留权威快照；二者不矛盾，current 重复的实际移除属 **G3**（redirect-first）。
- `docs/README.md` §文档使用规则 同步加一段引用规则 16 的说明。**未重命名历史文件，未改 `docs/gates/**`/`docs/archive/**`**。

## 3. 当前控制文档状态同步（修复的旧口径）

| 文件 | 旧口径（drift） | 修复后 |
| --- | --- | --- |
| `docs/README.md` §当前边界 | 止于 “Next: GateK-PLAN”，无 CI mainline/Batch/G1/G2 | 补全 CI mainline COMPLETED / 5A FROZEN / 5B-ENV P1 / 5B-SMOKE BLOCKED / 4F backlog / static / G1 FROZEN / G2 IMPLEMENTED |
| `docs/current/ROADMAP.md` §当前阶段 | 止于 “Next: GateK-PLAN / GateK implementation not started” | 补 CI mainline COMPLETED、Batch 状态、G1/G2、real adapter not implemented（CI 权威指向 `STATUS.md`） |
| `docs/current/ROADMAP.md` §路线原则 | “Batch 4 PLAN ONLY / NOT IMPLEMENTED；Batch 5 PENDING”（严重过期） | 改为 CI mainline COMPLETED / ACCEPTED，逐 Batch 当前状态，5B/4F backlog 明确未完成 |

- 未把 optional backlog / P1 enhancement / blocked 写成 completed；Batch 5A 明确**非** authenticated/backend E2E coverage。
- `docs/current/STATUS.md` / `README.md` 已在前序轮携带完整当前口径，本轮仅追加 G2 记录，不改既有事实。

## 4. 可修链接修复 + 冻结快照兼容入口

- **已修（current 控制文档，语法/相对路径）**：
  - `docs/current/API.md:171`：`](/docs/gates/gate-i/GATEI_API_PLAN.md)` → `](../gates/gate-i/GATEI_API_PLAN.md)`。
  - `docs/current/DB_SCHEMA.md:239`：`](/docs/gates/gate-i/GATEI_DB_PLAN.md)` → `](../gates/gate-i/GATEI_DB_PLAN.md)`。
- **未改（冻结快照，4 处历史链接）**：`docs/gates/gate-h/{API,DB_SCHEMA}.md`、`docs/gates/gate-j/{API,DB_SCHEMA}.md` 内 `./GATEI_API_PLAN.md` / `./GATEI_DB_PLAN.md`（目标在各自 gate 目录不存在）。**不改写快照文本**；改在 `docs/README.md` §“冻结快照内历史链接兼容入口” 增加说明，指向真实目标 `docs/gates/gate-i/GATEI_API_PLAN.md` / `GATEI_DB_PLAN.md`。

## 5. 文档治理 evidence 导航

- 在 `docs/README.md` 新增 **Documentation Governance Evidence** 小节，指向：governance plan review（`NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md`）、G1 review（`NQ_DOCS_G1_REVIEW.md`）、G1 freeze review（`NQ_DOCS_G1_FREEZE_REVIEW.md`）、G2 implementation（本文）。
- 明确这些 review/freeze/G2 文档 = HISTORICAL_EVIDENCE / RETAIN_IN_PLACE，**不计入 G1 的 278 / 283**。
- **未修改被冻结的 `NQ_DOCS_EVIDENCE_INDEX.md`**；G2 期间新增 evidence 仅记录在本文与 current `README.md`/`STATUS.md`，evidence index 的物理更新留待后续受控流程（不在本轮、避免触发 G1 冻结失效）。

---

## 6. 边界与不可触碰证明

- G1 五份冻结对象 working-tree diff = **0**。
- 278 / 283 **未被改写**。
- **未移动、删除、重命名、归档任何文档**（仅内容编辑当前控制文档 + 新增本文）。
- `docs/gates` / `docs/archive` / `.agents` / `templates` diff = **0**。
- `.github/workflows/ci.yml`、backend/frontend/research/scripts/deploy/migration diff = **0**。
- G3~G6 未开始；5B-ENV / 5B-SMOKE / 4F-B~4F-F 未启动；LIVE / AI / DH runtime / RealClient / real provider 未开启、未接入、未实现。

## 7. 修改文件清单（本轮）

- 新增：`docs/current/NQ_DOCS_G2_CURRENT_CONTROL_REPAIR.md`（本文）。
- 更新：`docs/README.md`、`docs/DOC_RULES.md`、`docs/current/README.md`、`docs/current/STATUS.md`、`docs/current/ROADMAP.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、`docs/current/API.md`、`docs/current/DB_SCHEMA.md`。

## 8. 验证 / 风险 / 回滚

- **验证**：docs-only；`git diff` 限于允许文件；G1 五对象与 gates/archive/.agents/templates/code/workflow diff 为空；malformed leading-slash 链接归零；`git diff --check` clean。
- **风险**：零迁移、零代码、零不可逆操作。
- **回滚**：删除本文并 revert 上述 9 份允许更新文件的本轮 diff 即可完全回滚。

## 9. 状态结论（原样）

- **G1 authority/evidence index = FROZEN / ACCEPTED**。
- **G2 = IMPLEMENTED / READY FOR REVIEW**。
- **G3 ~ G6 = NOT STARTED**。
- **NQ GateK CI mainline = COMPLETED / ACCEPTED**。
- **Batch 5A = FROZEN / ACCEPTED**。
- **Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**；Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。
- **LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现**。
