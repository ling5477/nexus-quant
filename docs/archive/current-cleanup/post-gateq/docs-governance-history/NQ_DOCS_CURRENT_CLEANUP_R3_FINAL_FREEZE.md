# NQ Docs Current Cleanup — Round 3 Final Freeze

任务：`NQ-DOCS-CURRENT-LEANUP-R3-FINAL-FREEZE`

日期：2026-06-19

执行轮次：**Round 3 / 3**（R1 implementation → R2 review → **R3 final freeze**）。最终冻结轮，不再实施变更，不开 Round 4。

结论：**`NQ-DOCS-CURRENT-LEANUP-R3-FINAL-FREEZE：PASS / ACCEPTED / FROZEN`**

> 冻结对象：`docs/current` 物理瘦身结果（R1 commit `ca77460f` + R2 review commit `d4095ded`）。R3 仅新增本 final freeze 文档并追加 STATUS / TESTING / WORKLOG，不移动/删除/重命名/归档/stub 化/复制任何文档，不改代码/workflow/migration/测试/依赖。

---

## 1. R1 / R2 结果一致性（核验通过）

冻结的 cleanup-result 基线：

```text
docs/current root markdown before = 96
docs/current root markdown after  = 46
moved out of current = 51
governance evidence moved = 17
GateJ stub moved = 14
CI stub moved = 20
known compatibility residual = 3
historical evidence deleted = 0
code / workflow / migration changed = 0
```

R1 commit `ca77460f` 与 R2 review commit `d4095ded` 结论一致，均已核验通过。

### 1.1 current 实际 tracked 计数与 audit-trail 说明（如实记录）

`docs/current` 根目录 cleanup-result 基线为 **46**（= 当前控制入口 + 治理权威 + CI authority + 3 known residual + R1 implementation 报告）。

此后每轮 review/freeze 会按 R3 §2(5) 把自身 audit-trail 文档保留在 current：

| 时点 | current root tracked `.md` | 说明 |
| --- | ---: | --- |
| R1 implementation 提交后 | 46 | cleanup-result 冻结基线 |
| R2 review 提交后（`d4095ded`） | 47 | +1 `NQ_DOCS_CURRENT_CLEANUP_R2_REVIEW.md`（current-control audit trail） |
| R3 final freeze 提交后（本轮） | 48 | +1 `NQ_DOCS_CURRENT_CLEANUP_R3_FINAL_FREEZE.md`（current-control audit trail） |

口径冻结：**cleanup-result current markdown count = 46**（physical reduction 基线）；R2/R3 的 review/freeze 文档属 cleanup/governance audit-trail，按设计保留在 current，不计入 physical-reduction 基线。两者均如实可由 `git ls-files docs/current/*.md` 复核（live = 48）。R3 不要求继续压缩到更小数量。

---

## 2. current 目录最终语义（核验通过）

`docs/current` 现在主要保留以下类别（不再堆放历史过程证据）：

| 类别 | 文件 |
| --- | --- |
| 当前控制入口（10） | README.md, STATUS.md, ROADMAP.md, TESTING.md, WORKLOG.md, API.md, DB_SCHEMA.md, ARCHITECTURE.md, MODULES.md, RUNBOOK.md |
| 当前治理权威（5） | NQ_DOCS_GOVERNANCE_PLAN.md, NQ_DOCS_AUTHORITY_INDEX.md, NQ_DOCS_EVIDENCE_INDEX.md, NQ_DOCS_MIGRATION_MAP.md, NQ_DOCS_G1_IMPLEMENTATION.md |
| 当前 CI authority（2） | NQ_CI_BASELINE_PLAN.md, NQ_CI_SECURITY_GUARD_PLAN.md |
| known compatibility residual（3） | GATEJ_API_PLAN.md, GATEJ_DB_PLAN.md, GATEJ_TEST_PLAN.md |
| cleanup / governance audit trail | NQ_DOCS_CURRENT_CLEANUP_R1_IMPLEMENTATION.md, NQ_DOCS_CURRENT_CLEANUP_R2_REVIEW.md, NQ_DOCS_CURRENT_CLEANUP_R3_FINAL_FREEZE.md（本文） |
| 其他保留当前控制 / 规划 / 审计文档 | GATEK_PLAN.md, GATEK_ARCHITECTURE_BASELINE_REVIEW.md, FRONTEND_DESIGN_SYSTEM.md, BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md, CODEX_PROJECT_INSTRUCTIONS.md, NQ_DH_CODEX_PLUGIN_WORKFLOW.md, NQ_DH_CODEX_TASK_TEMPLATES.md, NQ_DH_WORKFLOW_ROUTER_SKILL.md, DB_SCHEMA_GOVERNANCE_PLAN.md, DB_SCHEMA_GOVERNANCE_REVIEW.md, CREDENTIAL_*（10）, NQ_DH_INTEGRATION0_*（4）, NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md |

46 是本轮可接受的最终 physical-reduction 状态，不在 R3 继续压缩。

---

## 3. 移出 current 的对象冻结（核验通过）

### 3.1 Governance evidence — `docs/evidence/governance/`

| 检查项 | 结论 |
| --- | --- |
| 17 个 G1～G6 governance process evidence | PASS |
| README.md | PASS（存在） |
| 文件正文未改写 | PASS（R1 commit 17 个均 R100 byte-identical 纯 rename） |
| current 不再堆放这些过程证据 | PASS |
| 目录 `.md` 总数 | 18（17 + README） |

### 3.2 GateJ compatibility stubs — `docs/evidence/compatibility/gatej-current-stubs/`

| 检查项 | 结论 |
| --- | --- |
| 14 个 moved GateJ compatibility stub | PASS |
| README.md | PASS（存在） |
| 每个 stub canonical 链接可解析到 `docs/gates/gate-j/` | PASS（`../../../gates/gate-j/<file>`，逐文件解析 0 broken） |
| `docs/gates/gate-j/**` canonical records 未修改 | PASS（28 files 未变） |
| 目录 `.md` 总数 | 15（14 + README） |

### 3.3 CI compatibility stubs — `docs/evidence/compatibility/ci-current-stubs/`

| 检查项 | 结论 |
| --- | --- |
| 20 个 moved CI compatibility stub | PASS |
| README.md | PASS（存在） |
| 每个 stub canonical 链接可解析到 `docs/evidence/ci/` | PASS（`../../ci/<file>`，逐文件解析 0 broken） |
| `docs/evidence/ci/**` canonical evidence 未修改 | PASS（20 canonical + README 未变） |
| 两份 CI current authority 仍留 current、未降级 | PASS（`NQ_CI_BASELINE_PLAN.md`、`NQ_CI_SECURITY_GUARD_PLAN.md`） |
| 目录 `.md` 总数 | 21（20 + README） |

---

## 4. known compatibility residual 冻结

以下 3 个文件冻结为 **accepted known compatibility residual**，继续留在 `docs/current`：

```text
docs/current/GATEJ_API_PLAN.md
docs/current/GATEJ_DB_PLAN.md
docs/current/GATEJ_TEST_PLAN.md
```

冻结原因：`BLOCKED_PER_FILE / DIVERGED_INBOUND_LINK`

| 文件 | 入链来源 | 入链可解析 |
| --- | --- | --- |
| `GATEJ_API_PLAN.md` | `API.md:233`（`./GATEJ_API_PLAN.md`） | 是（同目录） |
| `GATEJ_DB_PLAN.md` | `DB_SCHEMA.md:375`（`./GATEJ_DB_PLAN.md`） | 是（同目录） |
| `GATEJ_TEST_PLAN.md` | `TESTING.md:3579`（`./GATEJ_TEST_PLAN.md`） | 是（同目录） |

R3 明确：

- 这 3 个文件是 **accepted known compatibility residual**。
- 保留在 current **不代表** R1/R2 失败。
- R3 **不强行移动**它们，**不修改** API.md / DB_SCHEMA.md / TESTING.md 入链。
- 未来若要处理，必须**单独开小型 link-rewrite proposal**，不得纳入本轮、不得开 Round 4。
- canonical 全文仍在 `docs/gates/gate-j/`。

---

## 5. P3 信息性问题冻结（不阻断 final freeze）

```text
P3-1：17 个 governance 文件正文 inline-code 形式相对路径示例，非渲染超链接。
P3-2：STATUS / WORKLOG 历史 prose 对已移出文件的提及，属 append-only 历史记录。
```

R3 明确：

- 两个 P3 均为 **informational**。
- 均**不构成 broken link**（已核验：fragment 入链 0，无 live 链接指向 moved 文件旧 current 路径）。
- **不要求在 R3 修复**。
- **不得因 P3 延长到 Round 4**。

---

## 6. README 与导航冻结（核验通过）

| 文件 | 导航职责 | 结论 |
| --- | --- | --- |
| `docs/current/README.md` | current 入口页（当前控制 / 治理权威 / CI authority / 历史证据位置，18 处导航引用齐全） | PASS |
| `docs/README.md` | 指向 current / gates / evidence / compatibility 正确位置 | PASS |
| `docs/evidence/governance/README.md` | 历史治理证据导航，不替代 current authority | PASS |
| `docs/evidence/compatibility/gatej-current-stubs/README.md` | 旧 current path stub 保留副本说明，不替代 canonical（gate-j） | PASS |
| `docs/evidence/compatibility/ci-current-stubs/README.md` | 旧 current path stub 保留副本说明，不替代 canonical（evidence/ci） | PASS |

确认：未把历史 evidence 当作 current authority；未把 blocked / backlog / optional 写成 completed。

---

## 7. 禁止范围最终核验（PASS）

R1 commit `ca77460f` 与 R2 commit `d4095ded` 均未触碰任何禁止范围；当前工作区 clean。

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

确认：无代码 / workflow / migration / 测试 / 依赖 / 运行时逻辑变更；无 LIVE / AI / DH runtime / RealClient / real provider 改动；未删除历史正文；未创建 deletion list。

---

## 8. Findings

| Severity | Findings |
| --- | --- |
| P0 | 0 |
| P1 | 0 |
| P2 | 0 |
| P3 | 2（informational，见 §5；不阻断冻结，不进入 Round 4） |

---

## 9. 检查文件 / 修改文件 / validation / 风险与回滚

### 检查文件（只读）

- R1 commit `ca77460f`、R2 commit `d4095ded`（`git show --name-status -M`）。
- `docs/current/`（root tracked .md = 47 当前；cleanup-result 基线 46）、`docs/evidence/governance/`（18）、`docs/evidence/compatibility/gatej-current-stubs/`（15）、`docs/evidence/compatibility/ci-current-stubs/`（21）。
- canonical：`docs/gates/gate-j/`（28）、`docs/evidence/ci/`（20 NQ_CI + README）。
- BLOCKED 3、CI authority 2、RUNBOOK、5 个导航 README（存在性 + 链接解析）。

### 修改文件（本轮允许）

- 新增：`docs/current/NQ_DOCS_CURRENT_CLEANUP_R3_FINAL_FREEZE.md`
- 更新：`docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`

### validation

docs-only final freeze，未运行后端/前端/Python/CI 测试（无代码/workflow/migration/依赖/运行时变更）。`git diff --check` clean（仅 LF/CRLF warning）。moved stub canonical 链接逐文件解析 0 broken；fragment 入链 0；无 live 链接指向 moved 文件旧 current 路径。

### 风险与回滚边界

风险：低。R3 仅新增 1 final freeze 文档 + 追加 3 份 current-control 记录；不动 R1/R2 结果、canonical、冻结对象。回滚：

```powershell
git restore -- docs/current/STATUS.md docs/current/TESTING.md docs/current/WORKLOG.md
Remove-Item -LiteralPath docs/current/NQ_DOCS_CURRENT_CLEANUP_R3_FINAL_FREEZE.md -Force
```

如已提交：`git revert <r3-commit>`。不得通过回滚 R3 去改写 R1/R2 moved files、canonical evidence、G1～G6 冻结对象或历史证据。

---

## 10. 最终冻结状态声明

```text
NQ Docs Current Cleanup = FROZEN / ACCEPTED / CLOSED
Round = 3 / 3
Round 4 = NOT ALLOWED
docs/current = PHYSICALLY REDUCED
current markdown count = 46
moved out of current = 51
known compatibility residual = 3
P3 informational = 2
No historical evidence deleted
No deletion list created
No code/workflow/migration changed
G1-G6 governance baseline remains historical reference
NQ GateK CI mainline = COMPLETED / ACCEPTED
LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现
```

> 计数口径说明：`current markdown count = 46` 为冻结的 physical-reduction cleanup-result 基线；R2/R3 的 review/freeze audit-trail 文档按 §2(5) 保留在 current，使 live `git ls-files docs/current/*.md` = 48（R3 提交后），如实可复核，二者不矛盾。
