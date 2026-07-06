# NQ Docs Current Cleanup — Round 2 Review

任务：`NQ-DOCS-CURRENT-LEANUP-R2-REVIEW`

日期：2026-06-19

执行轮次：**Round 2 / 3**（R1 implementation → **R2 review** → R3 final freeze）。只审查 R1，不追加迁移批次，不删除文档，不启动新治理长链路。

结论：**`NQ-DOCS-CURRENT-LEANUP-R2-REVIEW：PASS / ACCEPTED`**

> 审查对象：R1 implementation commit `ca77460f docs(governance): physically reduce docs/current (cleanup R1)`。工作区 clean，本轮只读审查 + 新增本审查文档并追加 STATUS / TESTING / WORKLOG。

---

## 1. current before / after 统计（核验通过）

```text
docs/current root markdown before = 96
docs/current root markdown after  = 46
moved out of current = 51
governance evidence moved = 17
GateJ stub moved = 14
CI stub moved = 20
BLOCKED_PER_FILE = 3
```

| 检查项 | 期望 | 实测 | 结论 |
| --- | ---: | ---: | --- |
| current root tracked .md（提交后） | 46 | 46 | PASS |
| `docs/evidence/governance/*.md` | 18（17 + README） | 18 | PASS |
| `docs/evidence/compatibility/gatej-current-stubs/*.md` | 15（14 + README） | 15 | PASS |
| `docs/evidence/compatibility/ci-current-stubs/*.md` | 21（20 + README） | 21 | PASS |

`after 46` 解释核验：R1 报告提交前为 `45 tracked + 1 new R1 report`；R1 commit `ca77460f` 提交后 R1 报告已 tracked，当前 `git ls-files docs/current` 根 .md = **46**，与 R1 报告一致。

---

## 2. governance evidence 迁移审查（PASS）

| 检查项 | 结论 |
| --- | --- |
| 17 个 governance evidence 从 `docs/current/` 移到 `docs/evidence/governance/` | PASS |
| git rename 语义保留历史 | PASS，R1 commit 中 17 个均为 **R100**（byte-identical 纯 rename，正文零改写） |
| `docs/evidence/governance/README.md` 存在 | PASS（tracked） |
| 17 个文件正文未改写 | PASS（R100 = 100% similarity） |
| current 不再堆放 G1～G6 review / freeze / implementation 过程证据 | PASS |
| 仓库内部重要导航已指向新位置 | PASS（`docs/README.md` 治理证据块改指 `docs/evidence/governance/`；`docs/current/README.md` 历史证据位置列出该目录） |
| inline-code / prose 历史路径示例 | 未要求改写；经核验无渲染为可点击 Markdown 链接的断链 |

---

## 3. GateJ stub 迁移审查（PASS）

| 检查项 | 结论 |
| --- | --- |
| 14 个 GateJ compatibility stub 移到 `docs/evidence/compatibility/gatej-current-stubs/` | PASS |
| `README.md` 存在 | PASS（tracked） |
| 14 个 moved stub canonical 链接按新深度补偿可解析 | PASS，`../../../gates/gate-j/<file>`，逐文件解析 **0 broken** |
| `docs/gates/gate-j/**` 未修改 | PASS（R1 commit 无该路径） |
| canonical GateJ 文件仍为权威 | PASS（gate-j 28 files 未变） |
| 未删除历史正文 | PASS（rename，非 delete） |
| 未修改 RUNBOOK | PASS（RUNBOOK 仍 current-control，未在 R1 commit 改动） |
| 未修改 9 份 DIVERGED（除 STATUS/TESTING/WORKLOG 允许追加） | PASS（仅 STATUS/TESTING/WORKLOG/README 改动；README 由 §E 显式授权重写；API/DB_SCHEMA/ARCHITECTURE/MODULES/ROADMAP 未改） |

---

## 4. CI stub 迁移审查（PASS）

| 检查项 | 结论 |
| --- | --- |
| 20 个 CI compatibility stub 移到 `docs/evidence/compatibility/ci-current-stubs/` | PASS |
| `README.md` 存在 | PASS（tracked） |
| 20 个 moved stub canonical 链接按新深度补偿可解析 | PASS，`../../ci/<file>`，逐文件解析 **0 broken** |
| `docs/evidence/ci/**` canonical CI evidence 未修改 | PASS（R1 commit 无该路径；20 canonical + README 未变） |
| `NQ_CI_BASELINE_PLAN.md` 未修改 / 未移动 / 未降级 | PASS（仍在 `docs/current/`） |
| `NQ_CI_SECURITY_GUARD_PLAN.md` 未修改 / 未移动 / 未降级 | PASS（仍在 `docs/current/`） |
| `docs/baselines/CI_BASELINE_INDEX.md` 未修改 | PASS（R1 commit 无该路径） |
| 未删除历史正文 | PASS |

---

## 5. BLOCKED_PER_FILE 审查（PASS / 接受为 known compatibility residual）

3 个 blocked 文件仍保留在 `docs/current`：

```text
docs/current/GATEJ_API_PLAN.md
docs/current/GATEJ_DB_PLAN.md
docs/current/GATEJ_TEST_PLAN.md
```

| 文件 | 标记 | 入链来源 | 入链可解析 |
| --- | --- | --- | --- |
| `GATEJ_API_PLAN.md` | `BLOCKED_PER_FILE / DIVERGED_INBOUND_LINK` | `API.md:233`（`./GATEJ_API_PLAN.md`） | 是（同目录） |
| `GATEJ_DB_PLAN.md` | `BLOCKED_PER_FILE / DIVERGED_INBOUND_LINK` | `DB_SCHEMA.md:375`（`./GATEJ_DB_PLAN.md`） | 是（同目录） |
| `GATEJ_TEST_PLAN.md` | `BLOCKED_PER_FILE / DIVERGED_INBOUND_LINK` | `TESTING.md:3579`（`./GATEJ_TEST_PLAN.md`，R1 追加日志后行号由 3552 位移，链接仍解析） | 是（同目录） |

审查结论：

- 这 3 个文件保留在 current 是**接受结果**，不是 R1 失败。
- R2 **不强行移动**它们，**不修改** API.md / DB_SCHEMA.md / TESTING.md 来强行清空 blocked。
- **接受为 known compatibility residual**，留待 R3 final freeze 冻结为已知兼容残留。
- canonical 仍在 `docs/gates/gate-j/`。

---

## 6. current README 收敛审查（PASS）

`docs/current/README.md` 已重写为真正 current 入口页，覆盖全部要求项（核验 18 处导航引用齐全）：

- 当前状态 `STATUS.md`、路线图 `ROADMAP.md`、测试 `TESTING.md`、工作日志 `WORKLOG.md`。
- 架构 `ARCHITECTURE.md` / `MODULES.md`、API/DB `API.md` / `DB_SCHEMA.md`、运行手册 `RUNBOOK.md`。
- 文档治理权威 `NQ_DOCS_AUTHORITY_INDEX.md` / `NQ_DOCS_EVIDENCE_INDEX.md` / `NQ_DOCS_MIGRATION_MAP.md`（含 `NQ_DOCS_GOVERNANCE_PLAN.md` / `NQ_DOCS_G1_IMPLEMENTATION.md`）。
- CI current authority `NQ_CI_BASELINE_PLAN.md` / `NQ_CI_SECURITY_GUARD_PLAN.md`。
- 历史证据位置：`docs/gates/gate-j/`、`docs/evidence/ci/`、`docs/evidence/governance/`、`docs/evidence/compatibility/`。

不再把大量 review / freeze / process evidence 作为 current 主入口。PASS。

---

## 7. link rewrite / broken link 审查（PASS）

| 检查项 | 结论 |
| --- | --- |
| moved governance evidence 重要导航已改向 `docs/evidence/governance/` | PASS（governance 文件本无指向其的 Markdown 超链接，仅 prose；导航由新 README + docs/README 承载） |
| moved GateJ stub canonical 链接可解析 | PASS（0 broken） |
| moved CI stub canonical 链接可解析 | PASS（0 broken） |
| `docs/README.md` 中 GateJ / CI / governance 入口正确 | PASS |
| 全仓 fragment 入链未被忽略 | PASS（三组移出对象 `<file>.md#` fragment 入链 = 0） |
| 是否存在仍指向 moved 文件旧 current 路径的 live Markdown 链接 | PASS，**0 处**（git grep 无 `](./<moved>` 或 `](docs/current/<moved>`） |
| 非链接 prose / inline-code 历史路径示例 | 未要求改写；无实际渲染断链 |

---

## 8. 禁止范围审查（PASS）

R1 commit `ca77460f` 未触碰任何禁止范围（`git show --name-only` 过滤后为空）；当前工作区 clean。

| 路径 | diff |
| --- | --- |
| `docs/gates/**` | empty |
| `docs/evidence/ci/**` | empty |
| `docs/archive/**` | empty |
| `docs/baselines/**` | empty |
| `.agents/**`、`templates/**` | empty |
| `.github/workflows/ci.yml` | empty |
| `backend/**`、`frontend/**`、`research/**`、`scripts/**`、`deploy/**` | empty |
| `backend/**/db/migration` | empty |

确认：无代码 / workflow / migration / 测试 / 依赖 / 运行时逻辑变更；无 LIVE / AI / DH runtime / RealClient / real provider 相关改动。

---

## 9. Findings

| Severity | Findings |
| --- | --- |
| P0 | 0 |
| P1 | 0 |
| P2 | 0 |
| P3 | 2（信息性，见下） |

- P3-1：17 个 governance 文件正文中存在 **inline-code 形式**的相对路径示例（如 `` `](../gates/gate-i/...)` ``），随目录深度语义偏移，但均在 backtick 代码片段内、非渲染超链接，按 R1 §B “不改写正文” 保留；不构成断链。建议 R3 仅在 final freeze 记录中标注为 known cosmetic residual，不改写历史正文。
- P3-2：STATUS / WORKLOG 历史 as-of-time 日志中对已移出文件的 prose / backtick 提及未逐条改写（append-only 历史记录），属设计内保留；不构成断链。

均不阻断冻结。

---

## 10. 检查文件 / 修改文件 / validation / 风险与回滚

### 检查文件（只读）

- R1 commit `ca77460f`（`git show --name-status -M`：51 R / 17 R100 governance / 0 forbidden-scope）。
- `docs/current/`（root tracked .md = 46）、`docs/evidence/governance/`、`docs/evidence/compatibility/gatej-current-stubs/`、`docs/evidence/compatibility/ci-current-stubs/`。
- `docs/current/README.md`、`docs/README.md`、3 个导航 README。
- BLOCKED 3 文件、CI authority 2 文件、RUNBOOK（存在性 + 入链解析）。

### 修改文件（本轮允许）

- 新增：`docs/current/NQ_DOCS_CURRENT_CLEANUP_R2_REVIEW.md`
- 更新：`docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`

### validation

docs-only review，未运行后端/前端/Python/CI 测试（无代码/workflow/migration/依赖/运行时变更）。`git diff --check` clean（仅 LF/CRLF warning）。moved stub canonical 链接逐文件解析 0 broken；fragment 入链 0；无 live 链接指向 moved 文件旧 current 路径。

### 风险与回滚边界

风险：低。R2 仅新增 1 审查文档 + 追加 3 份 current-control 记录，不动 R1 结果、canonical、冻结对象。回滚：

```powershell
git restore -- docs/current/STATUS.md docs/current/TESTING.md docs/current/WORKLOG.md
Remove-Item -LiteralPath docs/current/NQ_DOCS_CURRENT_CLEANUP_R2_REVIEW.md -Force
```

如已提交：`git revert <r2-commit>`。不得通过回滚 R2 去改写 R1 moved files、canonical evidence、G1～G6 冻结对象或历史证据。

---

## 11. 最终状态声明

```text
NQ Docs Current Cleanup = ACCEPTED / READY FOR FINAL FREEZE
Round = 2 / 3
Round 3 = FINAL FREEZE
docs/current = PHYSICALLY REDUCED
current markdown count = 46
moved out of current = 51
known compatibility residual = 3
No historical evidence deleted
No code/workflow/migration changed
G1-G6 governance baseline remains historical reference
NQ GateK CI mainline = COMPLETED / ACCEPTED
LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现
```
