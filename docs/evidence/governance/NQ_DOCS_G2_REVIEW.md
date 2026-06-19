# NQ Documentation Governance — G2 Current-Control Drift Repair Review

任务：`NQ-DOCS-GOVERNANCE-G2-CURRENT-CONTROL-DRIFT-REPAIR-REVIEW`

日期：2026-06-18

被审对象：G2 commit `3c1f5ec0`（`docs/README.md`、`docs/DOC_RULES.md`、`docs/current/{README,STATUS,ROADMAP,TESTING,WORKLOG,API,DB_SCHEMA}.md` + 新增 `NQ_DOCS_G2_CURRENT_CONTROL_REPAIR.md`）

任务类型：DOCUMENTATION_GOVERNANCE_REVIEW + CURRENT_CONTROL_AUDIT + NAVIGATION_AND_LINK_REVIEW + FROZEN_BASELINE_PROTECTION

> 本轮为**只读评审**。**没有移动、删除、重命名、归档任何文档**，未改 G1 五份冻结对象、`docs/gates/**`/`docs/archive/**`/`.agents/**`/`templates/**`、workflow、代码、测试、migration、依赖。仅新增本文并更新 `docs/current/README.md`/`STATUS.md`/`TESTING.md`/`WORKLOG.md`。

---

## 审查结论

**结论：`NQ-DOCS-GOVERNANCE-G2-CURRENT-CONTROL-DRIFT-REPAIR-REVIEW：PASS / ACCEPTED`**

- **G2 current-control drift repair = ACCEPTED / READY FOR FREEZE REVIEW**。
- P0 = 0；P1 = 0；P2 = 0；P3 = 2（信息性，不阻塞）。
- 允许进入 **G2 freeze review**。

---

## 逐项核验

### 1. GateJ / GateK 导航（PASS）

- `PLAN_GATEJ.md`、`GATEJ_WORK_ORDER.md` **仍可导航**，已从「当前 GateJ 规划/工作单」改为**历史/冻结证据入口**，权威指向 `docs/gates/gate-j/PLAN_GATEJ.md` / `GATEJ_WORK_ORDER.md`（未删除导航）。
- GateJ 明确为 **completed historical gate**（`docs/README.md` §当前入口前注 + §当前边界）。
- `docs/current/` 17 份 GateJ 文档仅被描述为 **NON_AUTHORITATIVE / FUTURE_SUPERSEDE_CANDIDATE / G3**（引用 `NQ_DOCS_MIGRATION_MAP.md` §1E）。
- 新增并正确链接当前控制入口：`docs/current/README.md`、`STATUS.md`、`ROADMAP.md`、`NQ_DOCS_AUTHORITY_INDEX.md`、`NQ_DOCS_EVIDENCE_INDEX.md`（§当前入口 + §文档治理）。

### 2. 当前状态口径（PASS）

`rg` 复核 current-control 文档当前（top）口径全部正确，无 backlog 误标为 completed：

- GateK CI mainline = **COMPLETED / ACCEPTED**（top 口径；产品/runtime not started 区分清晰）。
- Batch 5A = **FROZEN / ACCEPTED**，且 `docs/README.md`/`ROADMAP.md` 均显式声明 **“仅 4 个 no-backend smoke spec，不是 authenticated / backend E2E / 交易链路 / 真实 provider 覆盖”**。
- 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；5B-SMOKE = **BLOCKED BY 5B-ENV**；4F-B~4F-F = **OPTIONAL BACKLOG / NOT STARTED**；static assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**。
- G1 = **FROZEN / ACCEPTED**；G2 = **IMPLEMENTED / READY FOR REVIEW**（精确核验：全仓**无** “G2 = FROZEN” 表述；仅出现 `G2 = IMPLEMENTED / READY FOR REVIEW` 与历史条目内 `G2 = READY FOR IMPLEMENTATION`），未提前写成 FROZEN。

### 3. `docs/DOC_RULES.md` Rule 16（PASS）

- 五级优先级**完整、顺序正确、无冲突**：① 冻结证据保留优先 → ② current control 单一权威优先 → ③ 迁移前先建 index/redirect/compatibility mapping → ④ 复制仅过渡导航/必要快照且必须标注 authority → ⑤ 禁止为减少文件数删除或改写历史证据。
- “文档不重复” 与 “迁移或复制” 被正确解释：current 层避免并列权威；历史冻结证据保留；current 重复的实际收敛属后续 **G3 redirect-first**。
- **无** “现在删除 GateJ 历史文档” 的暗示（明确 G3 未开始、gate-j 权威副本永久保留）。

### 4. Current-control link hygiene（PASS）

- `docs/current/API.md` → `[GATEI_API_PLAN.md](../gates/gate-i/GATEI_API_PLAN.md)`；`docs/current/DB_SCHEMA.md` → `[GATEI_DB_PLAN.md](../gates/gate-i/GATEI_DB_PLAN.md)`。
- 两目标实测存在，相对路径从 `docs/current/` 可解析（`../gates/gate-i/…` → `docs/gates/gate-i/…`）。
- current 控制文档 malformed leading-slash Markdown link = **0**。
- GateH/GateJ 冻结快照内 `./GATEI_*` 历史链接**未改写**（4 处仍在；`docs/gates` 自 G1 freeze 零 diff）。

### 5. Frozen-snapshot compatibility（PASS）

- `docs/README.md` 仅**新增**「冻结快照内历史链接兼容入口」，指向真实 GateI 目标 `docs/gates/gate-i/GATEI_API_PLAN.md` / `GATEI_DB_PLAN.md`。
- 该兼容导航**未伪称已修复**冻结快照正文（明确「属冻结快照事实，不改写」）。
- `docs/gates/**`、`docs/archive/**`、`.agents/**`、`templates/**` 自 G1 freeze **零 diff**。

### 6. Documentation governance evidence（PASS）

- `docs/README.md` 提供 Documentation Governance Evidence 导航，覆盖：governance plan review（`NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md`）、G1 review（`NQ_DOCS_G1_REVIEW.md`）、G1 freeze review（`NQ_DOCS_G1_FREEZE_REVIEW.md`）、G2 implementation（`NQ_DOCS_G2_CURRENT_CONTROL_REPAIR.md`）。
- 明确标注均为 **HISTORICAL_EVIDENCE / RETAIN_IN_PLACE**，且 **不计入 G1 的 278 / 283**。
- **未修改** G1 冻结的 `NQ_DOCS_EVIDENCE_INDEX.md`（自 G1 freeze 零 diff）。
- **P3 保留**：物理治理 evidence 小节尚未进入 G1 evidence index，须后续受控基线修订或目录治理批次处理（见 Findings P3-1）。

### 7. 范围与冻结保护（PASS）

- G1 五份冻结对象自 G1 freeze commit `7eb7ae53` **working-tree / commit diff = 0**。
- **278 / 283 未被改写**（仅在治理导航/evidence 上下文作为 G1 基线引用；ROADMAP/API/DB_SCHEMA 无杂散基线计数 token）。
- **未移动、删除、重命名、归档任何文档**（G2 commit 仅内容编辑 9 份允许文件 + 新增 1 份）。
- G3~G6 未启动；5B-ENV / 5B-SMOKE / 4F-B~4F-F 未启动；workflow/code/test/migration/依赖 diff = 0。

---

## Findings

### P0 / P1 / P2

- 无。

### P3（信息性，不阻塞冻结）

- **P3-1（carried-over）**：G1 冻结的 `NQ_DOCS_EVIDENCE_INDEX.md` 仍无物理「文档治理 evidence」小节列出 review/freeze/G2 文档。G2 已用 `docs/README.md` Documentation Governance Evidence 导航 + standing rule 按类治理（HISTORICAL_EVIDENCE / RETAIN_IN_PLACE，不计入 278/283），属合规过渡；物理进入 evidence index 须走后续受控基线修订（不能在不触发 G1 冻结失效的前提下本轮做）。
- **P3-2（历史日志，非 drift）**：`docs/current/STATUS.md` 早期里程碑条目（5A implementation / first-run / freeze 等）按 as-of-time 记载 `NQ GateK CI mainline = IN PROGRESS`。这些是**追加式时间序日志**，已被顶部最新条目（`COMPLETED / ACCEPTED`）正确取代；改写它们将**篡改历史记录**，违反治理「不改写历史」原则。结论：保留正确，非 drift，无需动作；当前权威态以 STATUS 顶部条目为准。

---

## 检查 / 修改 / 验证 / 风险 / 回滚

- **检查文件（只读）**：G2 commit `3c1f5ec0` 全量 diff；G1 五份冻结对象；`docs/gates/gate-i|gate-h|gate-j` 链接目标；`docs/README.md`/`docs/DOC_RULES.md`/`docs/current/{README,STATUS,ROADMAP,TESTING,WORKLOG,API,DB_SCHEMA}.md`；`git diff 7eb7ae53..HEAD`、`git hash-object`、相对路径解析。
- **修改文件（本轮）**：新增 `docs/current/NQ_DOCS_G2_REVIEW.md`；更新 `docs/current/README.md`/`STATUS.md`/`TESTING.md`/`WORKLOG.md`（仅追加评审记录）。
- **验证**：docs-only；G1 五对象与 gates/archive/.agents/templates/code/workflow diff = 0；malformed link = 0 且目标解析正确；G2 无 “FROZEN” 误写；`git diff --check` clean。
- **风险**：零迁移、零代码、零不可逆操作。
- **回滚**：删除本文并 revert 4 份 current 文档本轮追加段即可完全回滚。

---

## 状态结论（原样）

```text
NQ Docs Governance Plan = FROZEN FOR G1 BASELINE
G1 authority/evidence index = FROZEN / ACCEPTED
G2 current-control drift repair = ACCEPTED / READY FOR FREEZE REVIEW
G3～G6 = NOT STARTED
NQ GateK CI mainline = COMPLETED / ACCEPTED
Batch 5A = FROZEN / ACCEPTED
Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED
Batch 5B-SMOKE = BLOCKED BY 5B-ENV
LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现
```
