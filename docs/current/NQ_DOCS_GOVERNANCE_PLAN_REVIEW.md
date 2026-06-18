# NQ Documentation Governance Inventory & Plan — Review

任务：`NQ-DOCS-GOVERNANCE-INVENTORY-PLAN-REVIEW`

日期：2026-06-18

被审对象：`docs/current/NQ_DOCS_GOVERNANCE_PLAN.md`

任务类型：DOCUMENTATION_GOVERNANCE_PLAN_REVIEW + INFORMATION_ARCHITECTURE_REVIEW + HISTORICAL_EVIDENCE_PRESERVATION_REVIEW

> 本轮为**只读评审**。**没有移动、删除、重命名任何文档**，未修改任何历史 freeze/review 事实结论，未改 `.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration、测试、依赖。仅新增本文并更新 `docs/current/README.md`/`STATUS.md`/`TESTING.md`/`WORKLOG.md`。

---

## 审查结论

**结论：有条件通过（PASS / ACCEPTED WITH P2 CONDITIONS）**

- `NQ-DOCS-GOVERNANCE-INVENTORY-PLAN-REVIEW：PASS / ACCEPTED`（带 P2 收口条件）。
- **NQ Docs Governance Plan = ACCEPTED AS IMPLEMENTATION BASELINE**（治理原则、目标结构、不可删除清单、redirect-先行、删除单独显式默认空——全部正确并已逐项核验）。
- **G1 authority/evidence index = READY FOR IMPLEMENTATION**（条件：G1 必须把盘点计数从 `git ls-files` + blob 比对**重算后**写入索引与迁移映射，不得照抄计划 §2 的数字；见 P2-1/P2-2）。
- **G2 ~ G6 = NOT STARTED**。
- P0 = 0；P1 = 0；P2 = 3（计数/枚举不一致，G3 前必须收敛）；P3 = 2（命名/规则一致性）。

不判 BLOCKED 的依据：所有与**冻结证据保护、历史链接策略、权威单一真源、分层事实识别、实施顺序、删除纪律**相关的结构性属性均已核验为正确；本轮发现的缺口全部是**计数/枚举数字不准**，可被 G1（additive-only、零风险）自然吸收，且不造成任何证据、链接、residual 或权威口径丢失。

---

## 范围

- **已审查**：`docs/current/NQ_DOCS_GOVERNANCE_PLAN.md` 全文；`docs/current/README.md`、`STATUS.md`；`docs/README.md`、`docs/DOC_RULES.md`；全仓 `git ls-files "*.md" "*.txt"` 枚举与按区域计数；`docs/current` 根 vs `docs/gates/gate-j` 同名文件逐一 blob 比对；6 处 broken link 与目标存在性；`docs/current/frontend/`、`docs/archive/`、`research/py`、repo-root、`.agents/` 计数。
- **未审查（保持只读，不触碰）**：各 freeze/review 文档的事实结论文本；`docs/gates/**` 冻结快照内容正确性（仅核验链接与 blob 一致性）；`.github/workflows/ci.yml`；backend/frontend/research/scripts/deploy/migration/测试/依赖。
- **明确不涉及**：任何文件移动/删除/重命名；5B-ENV / 5B-SMOKE / 4F-B~4F-F 实施；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter。

---

## 核验证据（命令实跑）

| 项 | 计划口径 | git 实测 | 结论 |
| --- | --- | --- | --- |
| md/txt 总数（排除 node_modules/target/build/dist/test-results） | 277 | **278** | 计划低 1 |
| `docs/current` 根 *.md | 74 | **75** | 计划低 1（多半未计入新增的 `NQ_DOCS_GOVERNANCE_PLAN.md` 本身） |
| `docs/current/frontend/**` md/txt | 15 | **3**（`NQ_DESIGN_TOKENS_V2.md`、`NQ_FRONTEND_BUILD_MATRIX.md`、`ref/nq-design-system/README.md`） | 计划高 12（重大计数错误） |
| `docs/gates/**` md/txt | 152 | **152** | 一致 |
| `docs/archive/**` md/txt | 22 | **21** | 计划高 1 |
| `docs/templates/**` | 4 | 4 | 一致 |
| `.agents/**` md/txt | 13 | 13 | 一致 |
| `docs/` 根（README/DOC_RULES） | 2 | 2 | 一致 |
| repo-root（README/AGENTS/CLAUDE） | 3 | 3 | 一致 |
| `research/py` README | 2 | 2 | 一致 |
| frontend 代码内 README | 2 | 2（`frontend/README.md`、`frontend/src/nq-design-system/README.md`） | 一致 |
| **覆盖性（孤儿文件）** | — | **0 orphan**：每个 md/txt 都落在某一盘点前缀下 | 分类覆盖完整 |

> 计划 §2 表格**内部自相矛盾**：逐行求和 = 290，却声明 total = 277；git 实测 total = 278。

### `docs/current` 根 ↔ `docs/gates/gate-j` blob 逐一比对

- **IDENTICAL = 18 份**（blob 完全一致）：
  `AUDIT_FIX_REPORT.md`、`DOC_CLEAN_REPORT.md`、`FULL_SECURITY_AUDIT_REPORT.md`、`GATEJ_API_PLAN.md`、`GATEJ_DB_PLAN.md`、`GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md`、`GATEJ_FREEZE_DEPLOYMENT.md`、`GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`、`GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md`、`GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md`、`GATEJ_FRONTEND_PLAN.md`、`GATEJ_TEST_PLAN.md`、`GATEJ_WORK_ORDER.md`、`PLAN_GATEJ.md`、`PRE_FREEZE_AUDIT_FIX_PLAN.md`、`PRE_FREEZE_AUDIT_REPORT.md`、`REPO_SIZE_AUDIT_REPORT.md`、`RUNBOOK.md`。
- 其中 17 份为 GateJ 过程/计划类 superseded duplicate（计划 §2.1 D 的去重候选）；`RUNBOOK.md` 第 18 份虽 blob 一致，但计划 §2.1 A 正确地另列为 CURRENT_CONTROL（自 GateJ 未更新的活文档，**保留**，非去重对象）——此区分合理。
- **计数偏差**：计划 §2.1 D / §4.1 / STATUS 一律称 “16 份”，但 §2.1 D 代码块实际枚举 **17** 个文件名（含带注释的 `REPO_SIZE_AUDIT_REPORT.md`）；真正的 superseded-duplicate 集合 = **17**，blob-identical 总数 = **18**。“16” 在全文一致地少计。
- **DIVERGED = 9 份**（current 活文档 vs gate-j 快照，内容不同）：`API.md`、`ARCHITECTURE.md`、`DB_SCHEMA.md`、`MODULES.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`。**与计划 §4.1 的分层事实清单完全一致**，计划正确地判为“正常分层、非重复、不删除”。✓

### Broken link（6 处，全部命中、与计划一致）

| 文件:行 | 链接 | 目标存在? | 性质 | 计划处理 | 核验 |
| --- | --- | --- | --- | --- | --- |
| `docs/current/API.md:171` | `/docs/gates/gate-i/GATEI_API_PLAN.md` | 是（gate-i 下存在） | malformed 前导 `/`（从 FS 根解析失败） | G2 docs-only 修复 | ✓ 正确 |
| `docs/current/DB_SCHEMA.md:239` | `/docs/gates/gate-i/GATEI_DB_PLAN.md` | 是 | 同上 | G2 docs-only 修复 | ✓ 正确 |
| `docs/gates/gate-h/API.md:133` | `./GATEI_API_PLAN.md` | **否**（gate-h 下无 GATEI_*） | 冻结快照内历史链接 | redirect index，不改快照 | ✓ 正确 |
| `docs/gates/gate-h/DB_SCHEMA.md:177` | `./GATEI_DB_PLAN.md` | **否** | 同上（冻结） | redirect index | ✓ 正确 |
| `docs/gates/gate-j/API.md:133` | `./GATEI_API_PLAN.md` | **否**（gate-j 下无 GATEI_*） | 同上（冻结） | redirect index | ✓ 正确 |
| `docs/gates/gate-j/DB_SCHEMA.md:177` | `./GATEI_DB_PLAN.md` | **否** | 同上（冻结） | redirect index | ✓ 正确 |

### `docs/README.md` 状态漂移（计划 §4.2，已核验）

- 第 17–18 行把 `PLAN_GATEJ.md`、`GATEJ_WORK_ORDER.md` 列为“当前 GateJ 规划/工作单”——二者属已完成/冻结 GateJ 文档，非当前入口。**STALE**。
- 第 32–42 行“当前边界”止于 `Next: GateK-PLAN`，**未含** GateK CI mainline COMPLETED、Batch 1~5A、5B-ENV/5B-SMOKE、4F backlog；与 `docs/current/STATUS.md`（权威，已更新）不一致。**STALE**。
- 第 48 行自身规则（“已完成 Gate 的计划文档只保留在 `gates/gate-x/`，不在 `docs/current/` 重复”）被 current 内 17 份 GateJ blob-identical 副本**违反**；计划已正确识别。
- 第 50 行“迁移**或复制**到 `gates/gate-x/`”与第 48 行“不重复”自相矛盾（“复制”会留下重复）——属规则内部不一致（见 P3-2）。

---

## Findings

### P0

- 无。

### P1

- 无。冻结证据、历史链接、权威口径、P2/P3 residual 在本计划下均不会丢失：`docs/gates/**`（152）全部列为不可删除；CI Batch 1~5A freeze/review/proof、安全/redaction、DB、credential、NQ-DH 合同与安全边界、含 P2/P3 residual 的记录在 §7 逐项保留；冻结快照内 4 处链接用 redirect 处理而非篡改；9 份 DIVERGED 正确判为分层事实；16/17/18 份 blob-identical 仅列为未来收敛候选、gate-j 权威副本保留、G3 前必须先建 redirect、默认不删除。

### P2（G3 前必须收敛；可在 G1 内吸收）

- **P2-1 盘点计数不准 / 表格内部不自洽**。计划 §2：total 277（实 278）、`docs/current/frontend` 15（实 3）、`docs/archive` 22（实 21）、`docs/current` 根 74（实 75）；且 §2 逐行求和 = 290 ≠ 声明 277。
  - 修复：G1 在建 authority/evidence index 与 migration map 时，**计数一律由 `git ls-files` 重算**，不照抄 §2 数字；并补一句“计数以 git-verified 为准”的口径声明。
- **P2-2 GateJ 去重集合计数不准（16 vs 17 vs 18）**。superseded-duplicate 实测 = 17；blob-identical 总数 = 18（含 `RUNBOOK.md`，保留）。
  - 修复：G3 的逐文件迁移映射**必须基于 git-verified 的 17 份 superseded 列表**（见本文“blob 比对”节），不得用 “16”；`RUNBOOK.md` 明确排除在去重之外（保留 current）。该枚举已具备，仅需把表头数字与代码块对齐。
- **P2-3 非 `docs/current` 根区域只有目录级分类，无逐文件迁移映射**。`docs/gates`（152）、`docs/archive`（21）、`.agents`（13）、`templates`（4）只做目录级汇总分类。
  - 影响评估：**不阻塞**——计划 §5/§8 中 G3/G4/G5 的物理迁移对象**全部位于 `docs/current` 根**（17 份 GateJ 去重 + 22 份 `NQ_CI_*`），且这些对象在 §2.1 C/D 已逐文件枚举；gates/archive/.agents/templates 在目标结构中标注“不动”。因此“不得在无逐文件映射的情况下进入 G3/G4/G5”这一红线，对真正发生移动的文件**已满足**。
  - 仍按任务要求记为 P2：G1 的 migration map 应**显式写明** “gates/archive/.agents/templates = 保留不动（directory-level retain rule），本阶段无逐文件迁移”，把“目录级保留”从隐含升级为显式保留规则，避免后续误判为待迁移。

### P3（一致性，非阻塞）

- **P3-1 命名漂移处理保守、正确，但未给最终命名收敛策略**。计划 §4.3 记录 `PLAN_GATEJ.md` vs `GATEJ_*_PLAN.md`、`NQ_CI_*` batch 编号位置不一，并明确**本轮不重命名**——处理保守正确；建议 G1 的 evidence index 仅定义“排序键/展示别名”，不物理重命名历史文件（避免动冻结/历史路径）。
- **P3-2 `docs/README.md` 第 48 vs 50 行规则自相矛盾**（“不重复” vs “迁移或复制”）。建议 G2 修漂移时一并澄清为“Gate 完成→current 计划副本迁移到 gates 后从 current 移除（不长期双存）”，与 DOC_RULES.md 对齐。

---

## 逐文件迁移映射是否足以支持 G1？

**足以支持 G1，且 G1 本就是 additive-only。** 具体判定：

- G1 只新增 `docs/baselines/CI_BASELINE_INDEX.md`（CI evidence index）、authority index、migration map，并更新 `docs/README.md`/`docs/DOC_RULES.md` 的 GateK/CI 口径与 evidence 分层声明——**不移动任何文件**，零迁移风险。
- G3/G4/G5 真正移动的文件全部在 `docs/current` 根并已逐文件枚举（17 份 GateJ 去重在 §2.1 D，22 份 `NQ_CI_*` 在 §2.1 C），逐文件映射在 §5.1 给出触发条件与目标。
- **最小补充（纳入 G1，不另开任务）**：
  1. migration map 的所有计数改为 git-verified（P2-1）。
  2. GateJ 去重列表锚定 git-verified 的 17 份 + 明确排除 `RUNBOOK.md`（P2-2）。
  3. 显式写入 “gates/archive/.agents/templates = retain-in-place，本阶段不做逐文件迁移”（P2-3）。

满足以上 3 点后，逐文件迁移映射足以驱动 G3/G4/G5；在此之前 **G3/G4/G5 不得启动**。

---

## 是否允许进入 G1 authority/evidence index implementation？

**允许（READY FOR IMPLEMENTATION）**，条件：

- G1 严格 additive-only：只新增索引/映射 + 更新 `docs/README.md`/`docs/DOC_RULES.md` 导航与分层声明，**不移动/删除/重命名**任何文件。
- G1 内同时收敛 P2-1 / P2-2 / P2-3（计数与枚举以 git 为准）。
- G1 完成后才允许 G2（docs-only 漂移与可修链接修复）；G3/G4/G5 须在逐文件迁移映射、历史链接 redirect 兼容方案、回滚清单全部就绪后才能开始；G6 默认不执行删除。

---

## 风险

- **影响面**：本轮零代码/零迁移，仅文档评审 + current 控制文档增量更新。
- **触发条件 / 最坏结果**：若忽略 P2 直接进入 G3，迁移批次可能以“16 份”错误枚举操作，遗漏第 17 份 superseded 副本或误把 `RUNBOOK.md` 当去重对象移除——但 gate-j 权威副本始终保留，且 G3 要求先建 redirect、单文件 `git mv` 可逆，最坏结果可由 git 历史完全恢复，无不可逆证据丢失。
- **残留**：`docs/README.md` 漂移、6 处 broken link（2 current + 4 frozen）在本轮**按设计保留**，分别留待 G2 / redirect index 处理，不在本评审轮修改。

---

## 检查文件 / 修改文件 / 验证 / 回滚边界

- **检查文件（只读）**：`docs/current/NQ_DOCS_GOVERNANCE_PLAN.md`、`docs/current/README.md`、`docs/current/STATUS.md`、`docs/README.md`、`docs/DOC_RULES.md`、`docs/current/API.md`、`docs/current/DB_SCHEMA.md`、`docs/gates/gate-h|gate-j/{API,DB_SCHEMA}.md`、`git ls-files` 枚举、blob 比对。
- **修改文件（本轮）**：新增 `docs/current/NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md`；更新 `docs/current/README.md`、`docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`（仅追加“governance plan review accepted with P2 conditions”记录）。
- **验证**：docs-only，无需构建/测试；验证方式为链接与计数的 git 实测（已在“核验证据”节给出），并以 `git diff --check` + 禁止范围 `git diff` 为空收尾。
- **回滚边界**：删除新增的 `NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md`，并 revert 上述 4 个 current 文档的本轮追加段即可完全回滚；不涉及任何不可逆操作。

---

## 状态结论（原样）

- **NQ Docs Governance Plan = ACCEPTED AS IMPLEMENTATION BASELINE**（带 P2 收口条件）。
- **G1 authority/evidence index = READY FOR IMPLEMENTATION**（条件：G1 内收敛 P2-1/P2-2/P2-3，计数以 git-verified 为准）。
- **G2 ~ G6 = NOT STARTED**。
- **NQ GateK CI mainline = COMPLETED / ACCEPTED**。
- **Batch 5A = FROZEN / ACCEPTED**。
- **Batch 5B-ENV = P1 SECURITY ENHANCEMENT / NOT STARTED**。
- **Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。
- **Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED**；Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。
- **LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现**。

## 本轮变更声明

- 本轮**没有移动、删除或重命名任何文档**，未修改任何历史 freeze/review 文档的事实结论。
- 未改 `.github/workflows/ci.yml`、backend、frontend、research、scripts、deploy、migration、测试、依赖。
- 未启动 5B-ENV / 5B-SMOKE / 4F-B~4F-F，未开启 LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter。
- 仅新增本评审文档并在 `docs/current/README.md`/`STATUS.md`/`TESTING.md`/`WORKLOG.md` 追加评审记录。
