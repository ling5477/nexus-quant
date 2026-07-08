# NexusQuant

NexusQuant 是通用量化交易平台。当前事实入口以 `docs/current/` 为准；`docs/gates/` 保存已完成 Gate 的冻结卷宗；`docs/archive/` 保存历史归档和本轮 current cleanup 归档。

## 当前状态

- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateQ release tag：`nq-gateq-freeze`。
- GateQ archive：`docs/gates/gate-q/README.md`。
- GateP：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateP release tag：`nq-gatep-freeze`。
- GateO 及更早 Gate：以 `docs/gates/**` 或 `docs/archive/**` 作为历史证据。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag：`nq-gater-freeze`；归档入口：`docs/gates/gate-r/README.md`；GateR-8 已完成并 push，最新 GitHub Actions run `28852212136`（`NQ CI Baseline`）为 `success`（成功）。
- GateR-1：`NQ-GATER-1-SHADOW-RUN-DATA-MODEL-MIGRATION-PLAN-REVIEW：PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`（通过 / migration 方案已就绪 / 未实现）。
- GateR-2：Shadow Run local fact model / `V32` / repository 已完成并接受。
- GateR-3：Shadow Run runner skeleton 已完成；它不是 scheduler 或后台 runner。
- GateR-4：decision trace / risk snapshot / order intent preview 已完成。
- GateR-5：shadow consistency report service 已完成。
- GateR-6：Shadow Run read-only API 已完成；没有写接口。
- GateR-7：Shadow Run detail / replay view 已完成；没有执行按钮。
- GateR-8：Shadow Run list / entrypoint 已完成。
- GateS：下一阶段唯一推荐主线，目标为策略验证运营化与 Shadow 诊断闭环阶段。
- GateS-0：`PLAN / NOT IMPLEMENTED`（规划 / 未实现）；规划审查入口：`docs/current/GATES_0_PLAN.md`。
- GateS-1 work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）；work order 入口：`docs/current/GATES_1_READ_MODEL_WO.md`。
- GateS-1 minimal backend read model：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `GET /api/shadow-runs/overview` 后端只读聚合，不代表 frontend page、GateS 全域 validation runtime、GateS-1 frozen / accepted、LIVE 或交易授权。
- GateS-1 frontend overview implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/shadow-runs` 列表页顶部 Overview Summary、前端 type / client / query key / hook，不新增 route、Dashboard v2、写侧动作、E2E、后端 API、migration、LIVE、AI/DH runtime 或交易授权。
- GateS-2 paper shadow consistency drilldown backend implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `GET /api/paper-shadow/consistency/drilldown` 后端只读 drilldown，不代表前端页面、Dashboard v2、runner / scheduler、LIVE 或交易授权。
- GateS-2 frontend consistency drilldown implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/shadow-runs/:shadowRunId` detail / replay 页面消费 `GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}`，不新增 route、Dashboard v2、写侧动作、E2E、后端 API、migration、LIVE、AI/DH runtime 或交易授权。
- GateS-3 strategy evaluation gate runtime baseline：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `GET /api/strategy-validation/overview` 后端只读 validation overview，不代表 GateS-3 frozen / accepted、前端页面、Dashboard v2、runner / scheduler、LIVE 或交易授权。
- GateS-3 frontend strategy validation overview implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/validation` 顶部 Overview panel、前端 type / API client / query key / hook，不新增 route、Dashboard v2、写侧动作、E2E、后端 API、migration、LIVE、AI/DH runtime 或交易授权。
- GateS-4 Python offline evaluation artifact baseline：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `research/py` 离线 artifact、parameter grid、writer / reader、checksum / validation 和 pytest / mypy / ruff，不代表 Java production binding、API、migration、Python ML ready、Python live execution ready、LIVE 或交易授权。
- GateS-5 frontend Strategy Validation / Shadow Workbench：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/validation` 页面只读 Workbench，复用 Strategy Validation overview、Shadow Run overview 与 Paper vs Shadow drilldown，不新增 route、Dashboard v2、后端 API、migration、CI、Python artifact UI 接入、LIVE、AI/DH runtime 或交易授权。
- GateS-6 Incident Replay overview read model：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `GET /api/incidents/replay/overview` 后端只读诊断 overview，不代表 GateS-6 frozen / accepted、真实 incident runtime、Dashboard v2、runner / scheduler、LIVE 或交易授权。
- GateS-6 frontend Incident / Replay overview implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/validation` 页面只读 Incident / Replay Overview panel、前端 type / API client / query key / hook 和 `npm run build` 本地验证，不新增 route、Dashboard v2、E2E、后端 API、migration、Python、CI、LIVE、AI/DH runtime 或交易授权。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient / real provider / private trading adapter / real permission probe：`NOT IMPLEMENTED`（未实现）。

## Current Docs

- `docs/current/README.md`：当前事实入口。
- `docs/current/STATUS.md`：当前状态。
- `docs/current/ROADMAP.md`：当前路线。
- `docs/current/FACT_SOURCE_INDEX.md`：事实源优先级。
- `docs/current/GATER_PLAN.md`：GateR historical Shadow Run operationalization planning evidence。
- `docs/current/GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md`：GateR-1 historical Shadow Run 数据模型与 migration 方案审查。
- `docs/current/GATES_0_PLAN.md`：GateS-0 fact-source reconciliation、planning review、read-model / frontend contract baseline。
- `docs/current/GATES_1_READ_MODEL_WO.md`：GateS-1 Shadow Run operational read model work order。
- GateS-3 backend / frontend 当前事实入口：`docs/current/API.md`、`docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、`docs/current/FACT_SOURCE_INDEX.md`。
- GateS-4 / GateS-5 / GateS-6 当前事实入口：`docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、`docs/current/FACT_SOURCE_INDEX.md`、`research/py/src/nq_research/evaluation/artifacts.py`、`frontend/src/pages/strategies/StrategyValidationPage.tsx`、`frontend/src/types/incident-replay.ts`。
- `docs/current/DB_SCHEMA.md`：当前 DB schema 事实；包含 GateR-2 Shadow Run local fact model 的 `V32` 说明。
- `docs/current/TESTING.md`：验证记录。
- `docs/current/WORKLOG.md`：工作记录。
- `docs/current/API.md`：当前 API 事实。
- `docs/current/ARCHITECTURE.md` / `docs/current/MODULES.md`：当前架构和模块边界摘要。
- `docs/current/RUNBOOK.md`：当前本地运行手册。

## Historical Archives

- GateQ archive: `docs/gates/gate-q/README.md`。
- GateP archive: `docs/gates/gate-p/README.md`。
- GateO archive: `docs/gates/gate-o/README.md`。
- GateM / GateN archives: `docs/gates/gate-m/README.md`, `docs/gates/gate-n/README.md`。
- Post-GateQ current cleanup archive: `docs/archive/current-cleanup/post-gateq/README.md`。

## Boundary

GateQ archive 不代表真实交易授权，不开启 LIVE，不接 AI / DH runtime，不实现 RealClient、real provider、private trading adapter、real permission probe，也不启动 Shadow Live runner。GateR-2 到 GateR-8 已形成 Shadow Run local fact / runner skeleton / decision trace / consistency report / read-only API / frontend list-detail-replay 闭环，但 Shadow Run 仍是 read-only diagnostic local fact（只读诊断本地事实）和 no-side-effect（无副作用）能力，不是 trading authorization，不是实盘就绪，不是 Shadow Live trading enabled。GateR 当前为 `FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；GateS 当前仅为推荐下一主线、GateS-0 `PLAN / NOT IMPLEMENTED`、GateS-1 work order `PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`、GateS-1 minimal backend read model `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`、GateS-1 frontend overview implementation `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`、GateS-2 backend drilldown implementation `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`、GateS-2 frontend consistency drilldown implementation `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`、GateS-3 backend strategy validation overview implementation `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`、GateS-3 frontend strategy validation overview implementation `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`、GateS-4 Python offline artifact baseline `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`、GateS-5 frontend Strategy Validation / Shadow Workbench `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`、GateS-6 backend Incident Replay overview read model `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT` 和 GateS-6 frontend Incident / Replay overview implementation `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`，不代表 GateS freeze、acceptance、Dashboard v2、完整 Incident Center、GateS 全域 validation runtime、LIVE、AI/DH runtime、real provider、Python ML ready、Python live execution ready 或交易授权。
