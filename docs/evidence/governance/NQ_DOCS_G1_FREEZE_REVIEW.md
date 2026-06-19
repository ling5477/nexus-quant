# NQ Documentation Governance — G1 Freeze Review

任务：`NQ-DOCS-GOVERNANCE-G1-FREEZE-REVIEW`

日期：2026-06-18

任务类型：DOCUMENTATION_GOVERNANCE_FREEZE_REVIEW + AUTHORITY_MODEL_VERIFICATION + EVIDENCE_PRESERVATION_AUDIT + MIGRATION_MAP_SNAPSHOT_REVIEW

> 本轮为**只读冻结复核**。**没有移动、删除、重命名、归档任何文档**，未改 `docs/gates/**`/`docs/archive/**` 冻结正文或链接，未改 `.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration、测试、依赖。仅新增本文并更新 `docs/current/README.md`/`STATUS.md`/`TESTING.md`/`WORKLOG.md`。本文**不修改** 5 份冻结对象。

---

## 审查结论

**结论：`NQ-DOCS-GOVERNANCE-G1-FREEZE-REVIEW：PASS / ACCEPTED / FROZEN`**

- **NQ Docs Governance Plan = FROZEN FOR G1 BASELINE**。
- **G1 authority/evidence index = FROZEN / ACCEPTED**。
- **G2 = READY FOR IMPLEMENTATION**。
- **G3 ~ G6 = NOT STARTED**。
- P0 = 0；P1 = 0；P2 = 0；P3 = 2（信息性 / G2 docs-only follow-up，不阻塞冻结）。

---

## 1. 冻结基线（immutable snapshot）

冻结锚点：HEAD = `a01579739ef176b0443103d69c55d8bf6845c0b6`（branch `dev`）。5 份冻结对象 blob（自 G1 implementation commit `c3a2cf83` 起**零 drift**，`git diff --name-only c3a2cf83..HEAD` 对 5 对象为空）：

| 冻结对象 | blob (git hash-object) |
| --- | --- |
| `docs/current/NQ_DOCS_GOVERNANCE_PLAN.md` | `0ee21735531d73e7fede68bcab635fbece4f3e1a` |
| `docs/current/NQ_DOCS_AUTHORITY_INDEX.md` | `71e31b5d4ddc65c9ae1a6cae4b33ede82e747b60` |
| `docs/current/NQ_DOCS_EVIDENCE_INDEX.md` | `8b18e36dc5cba83b814bb0e1d5f7b010f0dbabf0` |
| `docs/current/NQ_DOCS_MIGRATION_MAP.md` | `6eb2706df4539e94a5b174342a33543f1e4a5793` |
| `docs/current/NQ_DOCS_G1_IMPLEMENTATION.md` | `4dece64e8b5822f9093c2528a8098a866cf82377` |

> 任意上述 blob 变化即使 G1 冻结失效，须重新审查（见 §7）。

---

## 2. 快照与计数边界（明确、不冲突、防递归）

这是本轮冻结的核心定义，固定如下三层口径，互不冲突：

| 口径 | 数值 | 含义 | 状态 |
| --- | --- | --- | --- |
| **原始治理基线** | **278** | 盘点时刻全仓 md/txt（含 `NQ_DOCS_GOVERNANCE_PLAN.md`，不含任何 review/index 增量） | **FROZEN（不可变）** |
| **G1 implementation snapshot** | **283** | 278 基线 + 5 份 implementation 增量 | **FROZEN（不可变）** |
| live 工作树 md/txt | 284（本文落地后 285） | snapshot + review/freeze evidence（G1_REVIEW、本 freeze review …） | 随治理 evidence 累积增长，**不回写 278/283** |

**283 G1 implementation snapshot 的 5 份增量**（= migration map §1F 中除 PLAN 外的 5 项；PLAN 本身计入 278）：

```text
1. NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md   （PLAN 的评审；implementation delta）
2. NQ_DOCS_AUTHORITY_INDEX.md
3. NQ_DOCS_EVIDENCE_INDEX.md
4. NQ_DOCS_MIGRATION_MAP.md
5. NQ_DOCS_G1_IMPLEMENTATION.md
```

**防递归规则（本轮冻结确立的 standing rule）**：

- `NQ_DOCS_G1_REVIEW.md`（G1 实现评审）与 `NQ_DOCS_G1_FREEZE_REVIEW.md`（本文）及**后续同类文档治理 review/freeze evidence** = **HISTORICAL_EVIDENCE / RETAIN_IN_PLACE**，**不回写进 278 基线，也不回写进 283 implementation snapshot**。
- 因此 live 工作树计数会随治理 evidence 累积单调增长（284 → 285 → …），但 **278 与 283 永久冻结、不因新增 review/freeze 文档而变化**。该 standing rule 即任务所述“review/freeze 文档通过 evidence 规则标记为 HISTORICAL_EVIDENCE / RETAIN_IN_PLACE，而非导致基线计数无限递归”。
- 旧口径辨识：`NQ_DOCS_GOVERNANCE_PLAN.md:226` 的 `27750279096`/`27750976632` 为 CI run-id（含 `277` 子串，事实引用）；`MIGRATION_MAP`/`G1_IMPLEMENTATION` 中的 `277`/`290` 仅为“已废弃”订正说明。两者**均不是**当前口径错误。

> 注：本文区分两个 review —— `NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md`（PLAN 评审，**在** 283 内）vs `NQ_DOCS_G1_REVIEW.md`（G1 实现评审，**不在** 283 内）。

---

## 3. Authority index 冻结核验

- **14 领域**，每领域唯一 current authority，**无并列**（一文件可锚定多领域，如 `STATUS.md` 同为“项目总状态”与“CI 当前状态”权威，属一对多，非同领域多权威）。
- GateJ 权威仍为 `docs/gates/gate-j/`（冻结卷宗）。
- `docs/current/` 17 份 GateJ 候选保持 **NON_AUTHORITATIVE / FUTURE_SUPERSEDE_CANDIDATE**。
- `docs/current/RUNBOOK.md` 保持 **INDEX_AS_CURRENT_CONTROL / RETAIN_IN_PLACE**（blob-identical 第 18 份，不入 supersede 集合）。
- 9 份 DIVERGED（`API/ARCHITECTURE/DB_SCHEMA/MODULES/README/ROADMAP/STATUS/TESTING/WORKLOG`）保持 current 活文档 vs gate snapshot 分层事实，**未标记为重复/删除候选**。
- 复核：authority 表 14 行；`git hash-object` 比对 current 根 vs gate-j = **18 IDENTICAL / 9 DIVERGED**（与冻结口径一致）。

## 4. Evidence index 冻结核验

- 9 类入口齐全：GateJ freeze / GateK CI mainline / CI Batch 1~5A plan-review-firstrun-freeze / 4C redaction / 4F-A preflight / 数据库治理 / credential governance / NQ-DH Integration-0 合同与安全 / backlog-residual。
- backlog/residual 状态保持未完成或 blocked：**5B-ENV = P1 / NOT STARTED；5B-SMOKE = BLOCKED BY 5B-ENV；4F-B~4F-F = OPTIONAL BACKLOG / NOT STARTED；static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**（无 completed 误标）。
- evidence index **只链接、不复制、不改写、不弱化**冻结结论（两处声明）。
- **文档治理 review/freeze evidence 规则**：依 §2 standing rule，`NQ_DOCS_G1_REVIEW.md` 与本 freeze review = HISTORICAL_EVIDENCE / RETAIN_IN_PLACE。该规则由本 freeze review 正式确立并记入 `STATUS.md`/`WORKLOG.md`；evidence index 物理新增“文档治理 evidence”小节属 G2 docs-only follow-up（见 P3-1），**本轮不改冻结对象**。

## 5. Migration map 冻结核验

- 278 基线文档**唯一、无冲突**治理记录，**零 orphan**（§1~§4 全覆盖；§5 覆盖表 75+3+10+152+21+13+4 = 278）。
- 每组保留 10 字段（path / 主分类 / domain tags / authority level / lifecycle / recommended action / target location / migration batch / link compatibility strategy / retention rationale）。
- `docs/gates/` / `docs/archive/` / `.agents/` / `docs/templates/`（§4，190 份）统一 `RETAIN_IN_PLACE` / `migration_batch = NONE` / `move_precondition = NOT_APPLICABLE`。
- **无 `DELETE NOW`**（肯定用法 = 0；全部为否定语境）。
- `FUTURE_ARCHIVE_CANDIDATE` / `FUTURE_SUPERSEDE_CANDIDATE` 均不等于可立即删除（§4B already-archived / RETAIN_IN_PLACE；§1E 权威副本保留 gate-j、移除 ≠ 删除证据）。

## 6. G1 边界确认

- 本轮及全部 governance commit（`e3b12e33..a0157973`）**未移动、删除、重命名、归档任何文档**。
- **未改** `docs/gates/**` / `docs/archive/**` 正文或历史链接（`git diff --name-only e3b12e33..c3a2cf83 -- docs/gates docs/archive …` 为空）。
- **未提前处理**（继续保留给后续 Gate）：
  - `docs/README.md` “不重复” vs “迁移或复制” P3 规则矛盾 → **G2**。
  - `docs/README.md` GateJ 导航与 GateK/CI 状态漂移 → **G2**。
  - `docs/current` `API.md`/`DB_SCHEMA.md` malformed 前导 `/` 链接 → **G2**。
  - GateJ 17 份候选实际收敛 → **G3**。
  - CI evidence 实际目录归位 → **G4**；目录收口 → **G5**。

---

## 7. 冻结失效条件（任一触发须重新审查 G1）

1. 修改 Authority Index 中任一领域的唯一权威归属。
2. 修改 Evidence Index 中冻结证据入口或 backlog 状态。
3. 修改 Migration Map 中任何主分类 / recommended action / target location / migration batch / retention rationale。
4. 将 G1 snapshot 计数（278 / 283）与后续 review/freeze 文档混为同一统计对象。
5. 提前移动、删除、重命名或归档任一 mapped 文档。
6. 任一 §1 冻结 blob 发生变化。

---

## Findings

### P0 / P1 / P2

- 无。

### P3（信息性 / G2 docs-only follow-up，不阻塞冻结）

- **P3-1**：evidence index 当前无独立“文档治理 evidence”小节物理列出 `NQ_DOCS_G1_REVIEW.md` / `NQ_DOCS_G1_FREEZE_REVIEW.md`。本 freeze review 已用 §2 standing rule 将其归类 HISTORICAL_EVIDENCE / RETAIN_IN_PLACE（按类治理，非 orphan）。物理新增 evidence-index 小节留待 **G2**（现在改动会触发 §7-2 冻结失效，故不在本轮做）。
- **P3-2**：migration map §1D 的 CI 目标目录 `docs/evidence/ci/` 与 §5 的 `docs/baselines/CI_BASELINE_INDEX.md` 当前不存在，属 FUTURE_MOVE_CANDIDATE 预期、由 move_precondition 门控，非 broken path；authority index 引用 DH 仓 `NQ_DH_INTEGRATION_SECURITY_AUDIT_REPORT.md` 已标“外部只读”，正确排除在 278 之外。

---

## 检查 / 修改 / 验证 / 风险 / 回滚

- **检查文件（只读）**：5 份冻结对象 + `NQ_DOCS_G1_REVIEW.md`；`docs/README.md`/`docs/DOC_RULES.md`；`git ls-files` 全量枚举；current 根↔gate-j blob 比对；governance commit `git diff --name-only`、`git show --stat`、`git hash-object`。
- **修改文件（本轮）**：新增 `docs/current/NQ_DOCS_G1_FREEZE_REVIEW.md`；更新 `docs/current/README.md`/`STATUS.md`/`TESTING.md`/`WORKLOG.md`（仅追加冻结记录）。
- **验证**：docs-only；HEAD `a0157973`；5 冻结对象自 `c3a2cf83` 零 drift；authority 14 行 / blob 18 IDENTICAL / DELETE NOW 肯定用法 0 / §4 retain block 在；禁止范围 `git diff` 为空；`git diff --check` clean。
- **风险**：零迁移、零代码、零不可逆操作。
- **回滚**：删除 `NQ_DOCS_G1_FREEZE_REVIEW.md` 并 revert 4 份 current 文档本轮追加段即可完全回滚。

---

## 状态结论（原样）

- **NQ Docs Governance Plan = FROZEN FOR G1 BASELINE**。
- **G1 authority/evidence index = FROZEN / ACCEPTED**。
- **G2 = READY FOR IMPLEMENTATION**。
- **G3 ~ G6 = NOT STARTED**。
- **NQ GateK CI mainline = COMPLETED / ACCEPTED**。
- **Batch 5A = FROZEN / ACCEPTED**。
- **Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**；Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。
- **LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现**。
