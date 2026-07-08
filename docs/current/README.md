# Current Stage

`docs/current/` 是 NexusQuant 的当前事实入口，只保留当前控制文档、当前事实源、当前状态、当前路线、验证/工作记录、API、DB schema、运行手册、架构/模块摘要和仍需作为 current 使用的 Codex workflow 入口。已冻结 Gate 的过程型证据不再放在 current；历史证据以 `docs/gates/**`、`docs/archive/**` 为准。

## 当前状态

- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）；release tag 为 `nq-gateq-freeze`；历史证据入口为 [../gates/gate-q/README.md](../gates/gate-q/README.md)。
- GateP：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）；release tag 为 `nq-gatep-freeze`；历史证据入口为 [../gates/gate-p/README.md](../gates/gate-p/README.md)。
- GateO 及更早 Gate：只作为历史证据读取，入口为 `docs/gates/**` 或 `docs/archive/**`。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；historical planning 和批次证据入口为 [GATER_PLAN.md](GATER_PLAN.md)；release tag：`nq-gater-freeze`；archive pointer： [../gates/gate-r/README.md](../gates/gate-r/README.md)；GateR-8 已完成并 push，最新 GitHub Actions run `28852212136`（`NQ CI Baseline`）为 `success`（成功）。
- GateR-1：`NQ-GATER-1-SHADOW-RUN-DATA-MODEL-MIGRATION-PLAN-REVIEW：PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`（通过 / migration 方案已就绪 / 未实现）。当前 review 入口为 [GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md](GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md)。
- GateR-2：Shadow Run local fact model / `V32` / repository 已完成并接受；schema 入口为 [DB_SCHEMA.md](DB_SCHEMA.md)，验证入口为 [TESTING.md](TESTING.md)。
- GateR-3：Shadow Run runner skeleton 已完成；它只在调用方显式调用时写本地诊断 facts，不是 scheduler 或后台 runner。
- GateR-4：decision trace / risk snapshot / order intent preview 已完成。
- GateR-5：shadow consistency report service 已完成。
- GateR-6：Shadow Run read-only API 已完成；只提供 GET 查询，没有写接口。
- GateR-7：Shadow Run detail / replay view 已完成；只读展示，没有执行按钮。
- GateR-8：Shadow Run list / entrypoint 已完成；支持列表进入 detail / replay。
- GateS：下一阶段唯一推荐主线，目标为策略验证运营化与 Shadow 诊断闭环阶段。
- GateS-0：`PLAN / NOT IMPLEMENTED`（规划 / 未实现）；current plan review 入口为 [GATES_0_PLAN.md](GATES_0_PLAN.md)。
- GateS-1 work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）；work order 入口为 [GATES_1_READ_MODEL_WO.md](GATES_1_READ_MODEL_WO.md)。
- GateS-1 minimal backend read model：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `GET /api/shadow-runs/overview` 后端只读聚合，不代表 frontend page、GateS 全域 validation runtime、GateS-1 frozen / accepted、LIVE 或交易授权。
- GateS-1 frontend overview work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）；work order 入口为 [GATES_1_FRONTEND_OVERVIEW_WO.md](GATES_1_FRONTEND_OVERVIEW_WO.md)，只规划后续前端消费 `GET /api/shadow-runs/overview`，不代表前端已实现。
- GateS-1 frontend overview implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/shadow-runs` 顶部 Overview Summary、前端 type / client / query key / hook，不新增 route、Dashboard v2、后端 API、migration、E2E、LIVE、AI/DH runtime 或交易授权。
- GateS-2 paper shadow consistency drilldown backend implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `GET /api/paper-shadow/consistency/drilldown` 后端只读 drilldown，不代表前端页面、Dashboard v2、runner / scheduler、LIVE 或交易授权。
- GateS-2 frontend consistency drilldown implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/shadow-runs/:shadowRunId` detail / replay 页面消费 `GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}`，不新增 route、Dashboard v2、后端 API、migration、E2E、LIVE、AI/DH runtime 或交易授权。
- GateS-3 strategy evaluation gate runtime baseline：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `GET /api/strategy-validation/overview` 后端只读 validation overview，不代表 GateS-3 frozen / accepted、前端页面、Dashboard v2、runner / scheduler、LIVE 或交易授权。
- GateS-3 frontend strategy validation overview implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/validation` 顶部 Overview panel、前端 type / API client / query key / hook，不新增 route、Dashboard v2、后端 API、migration、E2E、LIVE、AI/DH runtime 或交易授权。
- GateS-4 Python offline evaluation artifact baseline：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `research/py` 离线 artifact、parameter grid、JSON writer / reader、checksum / validation 和 pytest / mypy / ruff，不代表 Java production binding、API、migration、Python ML ready、Python live execution ready、LIVE 或交易授权。
- GateS-5 frontend Strategy Validation / Shadow Workbench：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/validation` 页面中的只读 Workbench 区块，不新增 route、Dashboard v2、后端 API、migration、CI、Python artifact UI 接入、LIVE、AI/DH runtime 或交易授权。
- GateS-6 Incident Replay overview read model：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅表示 `GET /api/incidents/replay/overview` 后端只读诊断 overview 已实现，不表示 GateS-6 frozen / accepted、真实 incident runtime、Dashboard v2、runner / scheduler、LIVE 或交易授权。
- GateS-6 frontend Incident / Replay overview implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅表示现有 `/strategies/validation` 页面新增只读 Incident / Replay Overview panel 并消费后端 overview，不新增 route、Dashboard v2、E2E、后端 API、migration、Python、CI、LIVE、AI/DH runtime 或交易授权。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。

## Current Authority

| 用途 | 当前文件 |
| --- | --- |
| 当前事实入口 | [README.md](README.md) |
| 当前状态 | [STATUS.md](STATUS.md) |
| 当前路线 | [ROADMAP.md](ROADMAP.md) |
| GateR historical planning / readiness evidence | [GATER_PLAN.md](GATER_PLAN.md) |
| GateR-1 historical data model / migration plan review | [GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md](GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md) |
| GateR-2..8 Shadow Run closed-loop evidence | [STATUS.md](STATUS.md), [GATER_PLAN.md](GATER_PLAN.md), [API.md](API.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md) |
| GateS-0 planning / fact-source reconciliation | [GATES_0_PLAN.md](GATES_0_PLAN.md) |
| GateS-1 read model work order | [GATES_1_READ_MODEL_WO.md](GATES_1_READ_MODEL_WO.md) |
| GateS-1 frontend overview work order | [GATES_1_FRONTEND_OVERVIEW_WO.md](GATES_1_FRONTEND_OVERVIEW_WO.md) |
| GateS-1 frontend overview implementation | [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md), `frontend/src/pages/shadow-runs/ShadowRunListPage.tsx` |
| GateS-2 backend consistency drilldown implementation | [API.md](API.md), [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md), `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/PaperShadowConsistencyDrilldownController.java` |
| GateS-2 frontend consistency drilldown implementation | [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md), `frontend/src/pages/shadow-runs/ShadowRunDetailPage.tsx` |
| GateS-3 backend strategy validation overview implementation | [API.md](API.md), [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md), `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/StrategyValidationOverviewController.java` |
| GateS-3 frontend strategy validation overview implementation | [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md), `frontend/src/pages/strategies/StrategyValidationPage.tsx` |
| GateS-4 Python offline evaluation artifact baseline | [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md), [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md), `research/py/src/nq_research/evaluation/artifacts.py` |
| GateS-5 frontend Strategy Validation / Shadow Workbench | [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md), [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md), `frontend/src/pages/strategies/StrategyValidationPage.tsx`, `frontend/tests/e2e/strategy-validation-paper-shadow-smoke.spec.ts` |
| GateS-6 Incident Replay overview backend read model | [API.md](API.md), [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md), [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md), `backend/nq-api/src/main/java/com/guidinglight/nexusquant/monitoring/api/web/IncidentReplayOverviewController.java` |
| GateS-6 frontend Incident / Replay overview implementation | [STATUS.md](STATUS.md), [TESTING.md](TESTING.md), [WORKLOG.md](WORKLOG.md), [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md), `frontend/src/pages/strategies/StrategyValidationPage.tsx`, `frontend/src/types/incident-replay.ts` |
| 当前事实源优先级 | [FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) |
| 当前验证记录 | [TESTING.md](TESTING.md) |
| 当前工作记录 | [WORKLOG.md](WORKLOG.md) |
| 当前 API 事实 | [API.md](API.md) |
| 当前 DB schema 事实 | [DB_SCHEMA.md](DB_SCHEMA.md) |
| 当前架构/模块摘要 | [ARCHITECTURE.md](ARCHITECTURE.md) / [MODULES.md](MODULES.md) |
| 当前运行手册 | [RUNBOOK.md](RUNBOOK.md) |
| 当前前端设计系统 | [FRONTEND_DESIGN_SYSTEM.md](FRONTEND_DESIGN_SYSTEM.md) / [frontend/ref/nq-design-system/README.md](frontend/ref/nq-design-system/README.md) |
| 当前 Codex workflow | [CODEX_PROJECT_INSTRUCTIONS.md](CODEX_PROJECT_INSTRUCTIONS.md), [NQ_DH_CODEX_PLUGIN_WORKFLOW.md](NQ_DH_CODEX_PLUGIN_WORKFLOW.md), [NQ_DH_WORKFLOW_ROUTER_SKILL.md](NQ_DH_WORKFLOW_ROUTER_SKILL.md), [NQ_DH_CODEX_TASK_TEMPLATES.md](NQ_DH_CODEX_TASK_TEMPLATES.md) |

## Historical Evidence

- GateQ historical archive: [../gates/gate-q/README.md](../gates/gate-q/README.md)。
- GateP historical archive: [../gates/gate-p/README.md](../gates/gate-p/README.md)。
- GateO historical archive: [../gates/gate-o/README.md](../gates/gate-o/README.md)。
- GateM / GateN historical archives: [../gates/gate-m/README.md](../gates/gate-m/README.md), [../gates/gate-n/README.md](../gates/gate-n/README.md)。
- Post-GateQ current cleanup archive: [../archive/current-cleanup/post-gateq/README.md](../archive/current-cleanup/post-gateq/README.md)。

## Current Is Not

- 不是 GateS implemented / frozen / accepted。
- 不是 GateS-1 frozen / accepted。
- 不是 GateS 全域 frontend、Dashboard v2 或 Strategy Validation Center。
- 不是 Shadow Run write API implemented。
- 不是 Shadow runner scheduler 或后台任务 started。
- 不是 Shadow Run live execution started。
- 不是实盘就绪。
- 不代表真实 provider 已启用。
- 不是私有交易适配已启用。
- 不是 DH runtime 已集成。
- 不是 AI 已开始。
- 不是 Integration-1 runtime started。
- 不是 Shadow Live runner enabled。
- 不是 trading authorization。

## Current Cleanup Rule

`docs/current` 不再承载 GateO/P/Q、GateK/L/M/N、CI、credential、DB governance、NQ-DH Integration 或旧 docs cleanup 的过程型长文档。后续如需引用历史证据，只链接 `docs/gates/**` 或 `docs/archive/current-cleanup/post-gateq/**`；GateR frozen evidence 以 [../gates/gate-r/README.md](../gates/gate-r/README.md) 作为历史归档入口，GateR-2..8 的 current API / schema / validation 摘要只保留在 [STATUS.md](STATUS.md)、[API.md](API.md)、[DB_SCHEMA.md](DB_SCHEMA.md)、[TESTING.md](TESTING.md) 和 [WORKLOG.md](WORKLOG.md)。GateS-1 work order 入口为 [GATES_1_READ_MODEL_WO.md](GATES_1_READ_MODEL_WO.md)；GateS-1 minimal backend read model 当前入口为 [API.md](API.md)、[STATUS.md](STATUS.md)、[TESTING.md](TESTING.md) 和 [WORKLOG.md](WORKLOG.md)；GateS-1 frontend overview work order 入口为 [GATES_1_FRONTEND_OVERVIEW_WO.md](GATES_1_FRONTEND_OVERVIEW_WO.md)；GateS-1 frontend overview implementation 当前入口为 [STATUS.md](STATUS.md)、[TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md) 与 `frontend/src/pages/shadow-runs/ShadowRunListPage.tsx`；GateS-2 backend drilldown 当前入口为 [API.md](API.md)、[STATUS.md](STATUS.md)、[TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md)；GateS-2 frontend consistency drilldown 当前入口为 [STATUS.md](STATUS.md)、[TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md) 与 `frontend/src/pages/shadow-runs/ShadowRunDetailPage.tsx`；GateS-3 backend validation overview 当前入口为 [API.md](API.md)、[STATUS.md](STATUS.md)、[TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md)；GateS-3 frontend strategy validation overview 当前入口为 [STATUS.md](STATUS.md)、[TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md) 与 `frontend/src/pages/strategies/StrategyValidationPage.tsx`；GateS-4 Python artifact baseline 当前入口为 [STATUS.md](STATUS.md)、[TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md)、[FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md) 与 `research/py/src/nq_research/evaluation/artifacts.py`；GateS-5 frontend Workbench 当前入口为 [STATUS.md](STATUS.md)、[TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md)、[FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md)、`frontend/src/pages/strategies/StrategyValidationPage.tsx` 和 `frontend/tests/e2e/strategy-validation-paper-shadow-smoke.spec.ts`；GateS-6 backend Incident Replay overview 当前入口为 [API.md](API.md)、[STATUS.md](STATUS.md)、[TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md)、[FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md)；GateS-6 frontend Incident / Replay overview 当前入口为 [STATUS.md](STATUS.md)、[TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md)、[FACT_SOURCE_INDEX.md](FACT_SOURCE_INDEX.md)、`frontend/src/pages/strategies/StrategyValidationPage.tsx` 和 `frontend/src/types/incident-replay.ts`。后续不得把这些最小 read model、frontend overview summary、frontend drilldown panel、strategy validation overview panel、offline artifact baseline、Workbench 或 Incident / Replay overview panel 扩写成 GateS freeze / accepted、Dashboard v2、写接口、scheduler、后台 runner、Incident Center、AI 决策中心、LIVE、AI/DH runtime、RealClient、real provider、private trading adapter、real permission probe 或真实交易路径。
