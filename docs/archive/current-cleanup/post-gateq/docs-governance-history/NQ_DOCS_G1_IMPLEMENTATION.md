# NQ Documentation Governance — G1 Implementation Record

任务：`NQ-DOCS-GOVERNANCE-G1-AUTHORITY-EVIDENCE-INDEX`

日期：2026-06-18

状态：**G1 = IMPLEMENTED / READY FOR REVIEW**

> G1 唯一目标：建立未来文档收口所需的**权威入口、历史证据索引、逐文件迁移映射**，并在**不移动任何文件**的前提下收敛 P2-1 / P2-2 / P2-3。
>
> **本轮没有移动、删除、重命名或归档任何文档**，未改写任何冻结快照文本或链接，未改 `.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration、测试、依赖。

---

## 1. Git 实测计数（canonical baseline）

命令（排除 `node_modules/ target/ build/ dist/ test-results/`）：

```powershell
cd E:\project\nexus-quant
$docs = git ls-files | Where-Object { $_ -match '\.(md|txt)$' -and $_ -notmatch '^(node_modules|target|build|dist|test-results)/' }
$docs.Count
```

```bash
git ls-files "*.md" "*.txt" | grep -vE '^(node_modules|target|build|dist|test-results)/' | wc -l
```

| 区域 | canonical baseline | 现 HEAD | 备注 |
| --- | --- | --- | --- |
| 全量 md/txt | **278** | 279 | HEAD = 278 + `NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md`（盘点后提交，additive +1） |
| `docs/current` 根 | **75** | 76 | +1 = review doc |
| `docs/current/frontend` | **3** | 3 | — |
| `docs/gates` | **152** | 152 | — |
| `docs/archive` | **21** | 21 | — |
| `docs/templates` | **4** | 4 | — |
| `.agents` | **13** | 13 | — |
| scattered | **10** | 10 | repo root 3 + `.github` 1 + `docs` 根 2 + frontend 代码 2 + research 2 |

reconcile：基线 278 → HEAD 279（+review）→ **G1 完成后工作树 283**（+ `NQ_DOCS_AUTHORITY_INDEX.md` / `NQ_DOCS_EVIDENCE_INDEX.md` / `NQ_DOCS_MIGRATION_MAP.md` / `NQ_DOCS_G1_IMPLEMENTATION.md` 共 4 份 G1 新增）。

---

## 2. P2 收敛证据

### P2-1：计划计数错误 — CLOSED

- 旧口径 `277`、表格逐行求和 `290`、`docs/current/frontend = 15`、`docs/archive = 22` 均已**废弃**。
- canonical 计数替换为 Git 实测：278 / 75 / 3 / 21 / 152（见 §1）。
- 落地位置：`NQ_DOCS_MIGRATION_MAP.md` §0/§5、本文 §1、`NQ_DOCS_GOVERNANCE_PLAN.md` 顶部 **G1 计数订正** 段。新增的 5 份治理文档中**不再出现** `277` / `290` 作为当前口径。

### P2-2：GateJ 去重集合错误 — CLOSED

- current ↔ gate-j blob-identical = **18**（固定）。
- 未来 superseded 收敛候选 = **17**（固定，见 `NQ_DOCS_MIGRATION_MAP.md` §1E 完整清单）。
- `RUNBOOK.md` = 第 **18** 份 blob-identical，但 **RETAIN_IN_PLACE，不纳入 superseded 去重**（`MIGRATION_MAP` §1A）。
- 9 份 DIVERGED（`API/ARCHITECTURE/DB_SCHEMA/MODULES/README/ROADMAP/STATUS/TESTING/WORKLOG`）= current 活文档 vs gate 快照的**分层事实**，**不列为删除 / 替代 / 重复候选**（`MIGRATION_MAP` §1A、`AUTHORITY_INDEX`）。
- blob 比对命令：

```bash
for f in $(git ls-files "docs/current/*.md" | grep -vE 'docs/current/.+/' | sed 's#docs/current/##'); do
  gj="docs/gates/gate-j/$f"
  if git ls-files --error-unmatch "$gj" >/dev/null 2>&1; then
    [ "$(git hash-object docs/current/$f)" = "$(git hash-object $gj)" ] && echo "IDENTICAL $f" || echo "DIVERGED $f"
  fi
done   # → 18 IDENTICAL / 9 DIVERGED
```

### P2-3：非 docs/current 根仅目录级分类 — CLOSED

- `NQ_DOCS_MIGRATION_MAP.md` 已提供覆盖全部 278 基线的逐文件 / 等效逐文件清单。
- 对 `docs/gates/**`（152）、`docs/archive/**`（21）、`.agents/**`（13）、`docs/templates/**`（4）统一标注：

```text
recommended_action = RETAIN_IN_PLACE
migration_batch    = NONE
move_precondition  = NOT_APPLICABLE
```

- 覆盖性自洽：75 + 3 + 10 + 152 + 21 + 13 + 4 = **278** 基线（`MIGRATION_MAP` §5）。

---

## 3. 生成规则

| 文档 | 规则 |
| --- | --- |
| `NQ_DOCS_AUTHORITY_INDEX.md` | 14 领域 × {唯一当前权威 / 辅证 / 历史证据}；单一权威自检；历史证据不替代 current |
| `NQ_DOCS_EVIDENCE_INDEX.md` | 9 类证据入口（GateJ freeze / GateK CI mainline / Batch 1~5A / 4C / 4F-A / backlog-residual / DB / credential / NQ-DH）；只链接不复制 |
| `NQ_DOCS_MIGRATION_MAP.md` | 10 字段/条；recommended action 仅 5 取值；无 DELETE NOW；ARCHIVE_CANDIDATE ≠ 可立即删除；覆盖 278 + reconcile 增量 |
| `NQ_DOCS_G1_IMPLEMENTATION.md` | 本文：计数、P2 收敛、生成规则、验证、G1~G6 边界 |

---

## 4. 验证

```powershell
git status --short
git diff --check
git diff --name-status
git diff -- .github/workflows/ci.yml
git diff -- backend frontend research scripts deploy
git diff -- "backend/**/db/migration"
rg -n "277|290" docs/current/NQ_DOCS_AUTHORITY_INDEX.md docs/current/NQ_DOCS_EVIDENCE_INDEX.md docs/current/NQ_DOCS_MIGRATION_MAP.md docs/current/NQ_DOCS_G1_IMPLEMENTATION.md
```

预期：禁止范围 diff 为空；新增 4 份 G1 文档不含 `277`/`290` 当前口径；`docs/current` 5 份 + `docs/README.md` + `docs/DOC_RULES.md` 为允许更新。

---

## 5. G1~G6 边界（明确）

- **G1（本轮）= IMPLEMENTED / READY FOR REVIEW**：仅新增 authority/evidence/migration 索引 + G1 记录，并新增治理入口到 `docs/README.md`/`docs/DOC_RULES.md`；**未移动 / 删除 / 重命名 / 归档任何文档**。
- **G2 = NOT STARTED**：处理当前控制文档的**状态/导航漂移**与**可修链接**（`docs/current/{API,DB_SCHEMA}.md` 2 处前导 `/`；`docs/README.md` GateJ 口径漂移）+ `docs/README.md` 第 48 vs 50 行 P3 规则矛盾。**本轮未处理**。
- **G3 = NOT STARTED**：GateJ **17** 份 superseded 候选的 **redirect-first** 收敛（移除 current 副本，权威保留 gate-j）。
- **G4 = NOT STARTED**：CI evidence 实际归位 `docs/evidence/ci/` + `docs/baselines/CI_BASELINE_INDEX.md`。
- **G5 = NOT STARTED**：目录级收口（`baselines/`/`evidence/` 物理建立、导航统一）。
- **G6 = NOT STARTED**：默认**不执行删除**；任何删除须独立逐文件审查、0 入链证明、可回滚。

---

## 6. 状态结论

- **NQ Docs Governance Plan = P2 CONDITIONS CLOSED / READY FOR G1 REVIEW**。
- **G1 = IMPLEMENTED / READY FOR REVIEW**。
- **G2 ~ G6 = NOT STARTED**。
- **NQ GateK CI mainline = COMPLETED / ACCEPTED**。
- **Batch 5A = FROZEN / ACCEPTED**。
- **Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**；Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。
- **LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现**。
