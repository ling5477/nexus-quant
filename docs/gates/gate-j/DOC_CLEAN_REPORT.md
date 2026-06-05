# DOC-CLEAN-2 文档清理报告

日期：2026-05-22

## 1. 本次清理目标

在 GateJ-3-WO completed、Next: GateJ-FREEZE 的当前阶段，对项目文档进行结构梳理：

- 让 `docs/current/` 只承载当前事实和 GateJ 阶段规划，不再保留已冻结 Gate 的计划副本。
- 让 `docs/gates/` 只承载已完成 Gate 的冻结卷宗。
- 让 `docs/archive/` 只承载历史归档。
- 让 `README.md` / `AGENTS.md` / `CLAUDE.md` / `docs/README.md` / `docs/current/README.md` 入口清晰、当前事实唯一、重复最少。
- 本轮不涉及业务代码、API、migration、前端页面实现。

## 2. 删除文件清单

`docs/current/` 删除 12 个 GateH / GateI 计划副本（已在 `docs/gates/gate-h/` 与 `docs/gates/gate-i/` 中存在完全相同的冻结副本，`diff -q` 全部 `[same]`）：

- `docs/current/PLAN_GATEH.md`
- `docs/current/GATEH_API_PLAN.md`
- `docs/current/GATEH_DB_PLAN.md`
- `docs/current/GATEH_FRONTEND_PLAN.md`
- `docs/current/GATEH_TEST_PLAN.md`
- `docs/current/GATEH_WORK_ORDER.md`
- `docs/current/PLAN_GATEI.md`
- `docs/current/GATEI_API_PLAN.md`
- `docs/current/GATEI_DB_PLAN.md`
- `docs/current/GATEI_FRONTEND_PLAN.md`
- `docs/current/GATEI_TEST_PLAN.md`
- `docs/current/GATEI_WORK_ORDER.md`

## 3. 归档文件清单

本轮无新增归档：

- `docs/archive/gate-inputs/`、`docs/archive/legacy-root-docs/`、`docs/archive/rc1/` 既有结构清晰、已具备清晰目录与历史价值，本轮不移动。
- 上述删除的 12 个 GateH / GateI 计划副本已在 `docs/gates/gate-h/` 与 `docs/gates/gate-i/` 中作为 Gate 冻结卷宗保存，无需再次归档到 `docs/archive/`。

## 4. 保留文件清单摘要

`docs/current/` 最终保留：

- `README.md`、`STATUS.md`、`ROADMAP.md`、`WORKLOG.md`、`TESTING.md`
- `API.md`、`DB_SCHEMA.md`、`MODULES.md`、`ARCHITECTURE.md`、`RUNBOOK.md`
- `PLAN_GATEJ.md`、`GATEJ_API_PLAN.md`、`GATEJ_DB_PLAN.md`、`GATEJ_FRONTEND_PLAN.md`、`GATEJ_TEST_PLAN.md`、`GATEJ_WORK_ORDER.md`
- `DOC_CLEAN_REPORT.md`（本文件）

`docs/gates/` 保留 9 个历史 Gate 卷宗：

- `gate-a/` … `gate-g/`：早期 Gate 历史卷宗。
- `gate-h/`：GateH 冻结卷宗（交易工作台 + 历史行情 + dataset 绑定）。
- `gate-i/`：GateI 冻结卷宗（虚拟币量化 V1 完整闭环）。
- 暂不存在 `gate-j/`，待 GateJ-FREEZE 通过后再创建。

`docs/archive/` 保留：

- `gate-inputs/`：早期 Gate 输入材料与 checklist。
- `legacy-root-docs/`：早期根目录文档（ARCHITECTURE / CONTRACTS / DB_SCHEMA / DECISIONS 等）。
- `rc1/`：RC1 相关文档。

`docs/templates/`：保留模板（ADR / CHECKLIST / GATE_PLAN / WORK_ORDER）。

`docs/DOC_RULES.md`：保留。

根目录 `README.md`、`AGENTS.md`、`CLAUDE.md`：保留并同步当前阶段。

## 5. docs/current 最终结构

```
docs/current/
├── README.md
├── STATUS.md
├── ROADMAP.md
├── WORKLOG.md
├── TESTING.md
├── API.md
├── DB_SCHEMA.md
├── MODULES.md
├── ARCHITECTURE.md
├── RUNBOOK.md
├── PLAN_GATEJ.md
├── GATEJ_API_PLAN.md
├── GATEJ_DB_PLAN.md
├── GATEJ_FRONTEND_PLAN.md
├── GATEJ_TEST_PLAN.md
├── GATEJ_WORK_ORDER.md
└── DOC_CLEAN_REPORT.md
```

不再保留 GateH / GateI 计划副本。

## 6. docs/gates 最终结构

```
docs/gates/
├── README.md
├── gate-a/
├── gate-b/
├── gate-c/
├── gate-d/
├── gate-e/
├── gate-f/
├── gate-g/
├── gate-h/   ← GateH 冻结卷宗
└── gate-i/   ← GateI 冻结卷宗
```

`gate-j/` 不存在，待 GateJ-FREEZE 通过后再创建。

## 7. docs/archive 优化结果

`docs/archive/` 本轮未调整结构，已有目录划分清晰：

```
docs/archive/
├── gate-inputs/        ← 早期 Gate 输入材料
├── legacy-root-docs/   ← 早期根目录历史文档
└── rc1/                ← RC1 相关历史文档
```

未发现明确的重复/无引用副本需要删除。如后续发现重复，可单独安排清理。

## 8. 已修正的过期状态

- `docs/README.md`：移除"Next: GateJ-PLAN"等过期状态，同步至 `GateJ-3-WO completed / Next: GateJ-FREEZE`；新增 GateJ 规划与 DOC_CLEAN_REPORT 入口；明确"已完成 Gate 的计划文档只保留在 `docs/gates/gate-x/`，不在 `docs/current/` 重复"的规则。
- `docs/current/README.md`：从"GateI completed / Next: GateJ-PLAN"同步至 `GateJ-3-WO completed / Next: GateJ-FREEZE`；新增 GateJ 规划文件清单与历史 Gate 冻结卷宗指引；明确 GateJ-FREEZE 边界。
- `README.md`：移除过期的 `docs/current/PLAN_GATEI.md`、`docs/current/GATEI_WORK_ORDER.md` 引用（已删除），改为指向当前 GateJ 规划文档；扩展"当前明确不做"清单；明确 E2E skipped 与 GateJ 主链无关。
- `CLAUDE.md` / `AGENTS.md`：在 Current stage 之外新增"GateJ-FREEZE 允许范围 / 禁止范围"小节，明确 GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结。

## 9. 未删除但仍需观察的文件

- `docs/current/ARCHITECTURE.md`：当前架构概览，仍在使用，本轮不移动；如后续被 STATUS / MODULES 覆盖，可考虑归档到 `docs/archive/legacy-root-docs/` 之外的更新目录。
- `docs/current/RUNBOOK.md`：本地启动与常见问题，仍在使用，本轮不移动。
- `.qoder/repowiki/zh/content/**`：第三方工具生成的中文 repowiki 内容（不在 `docs/` 下），不属于 docs/current/gates/archive 体系；如后续阻碍维护，可在独立轮次单独清理。
- `frontend/README.md`、`research/py/README.md`、`research/py/datasets/README.md`：各子模块自有 README，不属于 docs 体系，保留。

## 10. 当前结论

- 文档结构已收口到 GateJ-FREEZE 前的稳定状态。
- `docs/current/` 当前事实唯一；`docs/gates/` 历史归档明确；已完成 Gate 的计划文档不再在 `docs/current/` 与 `docs/gates/` 之间重复。
- README / AGENTS / CLAUDE / docs/README / docs/current/README 全部同步至 `GateJ-3-WO completed / Next: GateJ-FREEZE / AI not started / GateK not started`。
- 本轮无业务代码、API、migration、前端页面实现变更。
- 允许进入 GateJ-FREEZE，但 GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新业务功能。
