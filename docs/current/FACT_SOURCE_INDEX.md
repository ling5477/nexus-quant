# Current Fact Source Index

本文是 NexusQuant 当前事实源索引。用途是给 GateR frozen baseline、GateS-0 planning / fact-source reconciliation、GateS-1 read model work order、GateS-1 minimal backend read model implementation、GateS-1 frontend overview work order、GateS-1 frontend overview implementation、GateS-2 paper shadow consistency drilldown implementation、历史 Gate archive、LIVE / AI / DH / Integration-1 / real provider 边界判断提供统一入口，避免把已冻结证据、mock/test-support、readiness、preview、local fact、read-only API、frontend replay、comparison 或 Strategy Validation 误写成 runtime 授权。

## 1. 当前事实源优先级

当事实冲突时，按以下顺序解释当前状态：

1. 当前代码和实际验证结果。
2. [STATUS.md](STATUS.md)：当前项目状态。
3. [README.md](README.md)：current 入口和 archive pointer。
4. [ROADMAP.md](ROADMAP.md)：当前路线与下一阶段边界。
5. [GATES_0_PLAN.md](GATES_0_PLAN.md)：GateS-0 plan / fact-source reconciliation、GateS batch plan、GateS-1 read-model / frontend contract proposal；只表示 planning ready，不表示 implementation。
6. [GATES_1_READ_MODEL_WO.md](GATES_1_READ_MODEL_WO.md)：GateS-1 read model work order；只表示 owner / data source / DTO / API candidate / frontend IA / testing scope / boundary ready；implementation 当前事实以代码、[API.md](API.md)、[TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md) 和 [STATUS.md](STATUS.md) 为准。
7. [GATES_1_FRONTEND_OVERVIEW_WO.md](GATES_1_FRONTEND_OVERVIEW_WO.md)：GateS-1 frontend overview work order；只表示前端消费 `GET /api/shadow-runs/overview` 的 type / client / hook / UI placement / states / badges / test scope 曾完成规划；实现当前事实以代码、[STATUS.md](STATUS.md)、[TESTING.md](TESTING.md) 和 [WORKLOG.md](WORKLOG.md) 为准。
8. `frontend/src/pages/shadow-runs/ShadowRunListPage.tsx`、`frontend/src/api/shadow-runs.ts`、`frontend/src/hooks/useShadowRunQueries.ts`、`frontend/src/types/shadow-runs.ts`：GateS-1 frontend overview implementation 当前代码事实；仅表示现有列表页顶部 Overview Summary 已消费 backend overview，不表示 Dashboard v2、写侧动作、E2E、后端 API、migration 或交易授权。
9. `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/PaperShadowConsistencyDrilldownController.java`、`backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/PaperShadowConsistencyDrilldownQueryService.java`、`backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcPaperShadowConsistencyDrilldownQueryRepository.java`：GateS-2 paper shadow consistency drilldown 当前代码事实；仅表示 `GET /api/paper-shadow/consistency/drilldown` 后端只读 drilldown 已实现，不表示前端页面、Dashboard v2、runner / scheduler、写侧动作、migration 或交易授权。
10. [GATER_PLAN.md](GATER_PLAN.md)：GateR historical planning 和 GateR-2..8 implementation 指针；GateR 当前已被 freeze closeout superseded，不再作为下一阶段入口。
11. [API.md](API.md)：已实现 HTTP API 当前事实；GateR-6 / GateR-8 Shadow Run API 只包含 read-only GET endpoint，不包含写接口；GateS-1 当前 backend 只实现 `GET /api/shadow-runs/overview` 最小 read model；GateS-2 当前 backend 只实现 `GET /api/paper-shadow/consistency/drilldown` 最小 drilldown，不包含 GateS 全域 overview、写接口、runner trigger 或 scheduler trigger。
12. [TESTING.md](TESTING.md)：当前验证记录和未运行说明；GateR frozen baseline、GateS-0 docs-only validation、GateS-1 work order docs/read-only validation、GateS-1 backend implementation Maven validation、GateS-1 frontend overview work order docs/read-only validation、GateS-1 frontend overview implementation build validation 和 GateS-2 drilldown Maven validation 证据以此为当前验证入口。
13. [WORKLOG.md](WORKLOG.md)：当前任务记录；GateR frozen baseline、GateS-0 docs-only scope、GateS-1 work order scope、GateS-1 backend implementation scope、GateS-1 frontend overview work order scope、GateS-1 frontend overview implementation scope、GateS-2 drilldown implementation scope 和边界以此为工作记录入口。
14. [DB_SCHEMA.md](DB_SCHEMA.md)：已落地 Flyway schema 当前事实；GateR-2 的 `V32__gate_r_shadow_run_fact_model.sql` 和 4 张 Shadow Run local fact 表以此为当前 schema 入口；GateS-2 drilldown implementation 不新增 schema。
15. [GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md](GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md)：GateR-1 Shadow Run data model / migration plan review；只表示 migration 方案曾经就绪，不代表当前要新增 migration。
16. [../gates/gate-r/README.md](../gates/gate-r/README.md)：GateR 历史归档入口。
17. [../gates/gate-q/README.md](../gates/gate-q/README.md)：GateQ 历史归档入口。
18. [../gates/gate-p/README.md](../gates/gate-p/README.md)：GateP 历史归档入口。
19. [../gates/gate-o/README.md](../gates/gate-o/README.md)：GateO 历史归档入口。
20. [../archive/current-cleanup/post-gateq/README.md](../archive/current-cleanup/post-gateq/README.md)：post-GateQ current cleanup 审计和移动索引。

`docs/gates/**` 与 `docs/archive/**` 是历史证据或归档引用，不覆盖 `docs/current` 当前事实入口。已从 `docs/current` 移出的过程型长文档不得再作为 current authority 引用。

## 2. 当前阶段声明

- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档），release tag `nq-gateq-freeze`。
- GateP：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档），release tag `nq-gatep-freeze`。
- GateO 及更早 Gate：历史证据来源为 `docs/gates/**` 或 `docs/archive/**`。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；release tag：`nq-gater-freeze`；GateR 归档入口：`docs/gates/gate-r/README.md`；GateR-8 已完成并 push，最新 GitHub Actions run `28852212136`（`NQ CI Baseline`）为 `success`（成功）。
- GateR-1：`NQ-GATER-1-SHADOW-RUN-DATA-MODEL-MIGRATION-PLAN-REVIEW：PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`（通过 / migration 方案已就绪 / 未实现）。
- GateR-2：Shadow Run local fact model / `V32` / repository 已完成并接受。
- GateR-3：Shadow Run runner skeleton 已完成；它不是 scheduler 或后台 runner。
- GateR-4：decision trace / risk snapshot / order intent preview 已完成。
- GateR-5：shadow consistency report service 已完成。
- GateR-6：Shadow Run read-only API 已完成；没有写接口。
- GateR-7：Shadow Run detail / replay view 已完成；没有执行按钮。
- GateR-8：Shadow Run list / entrypoint 已完成。
- GateS：下一阶段唯一推荐主线，目标为策略验证运营化与 Shadow 诊断闭环阶段。
- GateS-0：`PLAN / NOT IMPLEMENTED`（规划 / 未实现）。
- GateS-1 work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）。
- GateS-1 minimal backend read model：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅表示 `GET /api/shadow-runs/overview` 后端只读聚合已实现，不表示 GateS-1 frozen / accepted、frontend page、GateS 全域 validation runtime、LIVE 或交易授权。
- GateS-1 frontend overview work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）；仅表示前端消费 `GET /api/shadow-runs/overview` 的规划已就绪。
- GateS-1 frontend overview implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅表示现有 `/strategies/shadow-runs` 顶部 Overview Summary 已实现，不表示 GateS-1 frozen / accepted、Dashboard v2、E2E、LIVE 或交易授权。
- GateS-2 paper shadow consistency drilldown implementation：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅表示 `GET /api/paper-shadow/consistency/drilldown` 后端只读 drilldown 已实现，不表示 GateS-2 frozen / accepted、前端页面、Dashboard v2、runner / scheduler、LIVE 或交易授权。
- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。

## 3. Historical Archive Pointers

| 历史阶段 | 入口 |
| --- | --- |
| GateQ | `docs/gates/gate-q/README.md` |
| GateP | `docs/gates/gate-p/README.md` |
| GateO | `docs/gates/gate-o/README.md` |
| GateR | `docs/gates/gate-r/README.md` |
| GateN | `docs/gates/gate-n/README.md` |
| GateM | `docs/gates/gate-m/README.md` |
| GateJ/K/L current copies moved by this cleanup | `docs/archive/current-cleanup/post-gateq/README.md` |
| CI / credential / DB governance / NQ-DH Integration history moved by this cleanup | `docs/archive/current-cleanup/post-gateq/README.md` |

## 4. 禁止误写清单

- GateR 已完成 freeze closeout，状态为 `FROZEN / ACCEPTED / TAGGED`；不得误读为 LIVE、trading authorization、AI / DH runtime 或 private trading 启动。
- GateS 当前仅为下一阶段推荐主线，GateS-0 为 `PLAN / NOT IMPLEMENTED`；不得写成 implemented、frozen 或 accepted。
- GateS-1 work order 当前为 `PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`；GateS-1 minimal backend read model 当前为 `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`；GateS-1 frontend overview implementation 当前为 `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`；GateS-2 paper shadow consistency drilldown implementation 当前为 `IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`。不得误写成 GateS-1 / GateS-2 frozen / accepted、GateS 全域 validation runtime 已实现、Dashboard v2 started、runner / scheduler started 或交易授权。
- GateS-1 frontend overview implementation 只是在现有 `/strategies/shadow-runs` 顶部增加 Overview Summary；不得误写成新增 route、E2E、写侧动作、AI 决策中心或 Shadow Live trading。
- GateS-2 paper shadow consistency drilldown implementation 只是在后端增加 `GET /api/paper-shadow/consistency/drilldown` 只读诊断 endpoint；不得误写成前端页面、Dashboard v2、写侧动作、report 自动生成、snapshot 自动创建、runner / scheduler trigger、AI 决策中心或 Shadow Live trading。
- 不得把 GateR readiness 写成 trading authorization 或 LIVE ready。
- 不得把 GateR-1 migration plan review 本身写成 migration implemented；migration implemented 的当前事实只来自 GateR-2 `V32__gate_r_shadow_run_fact_model.sql`。
- 不得把 GateR-2..8 已完成误写成未实现、trading authorization 或 LIVE ready。
- Shadow Run read-only API / DTO 已实现；仍不得写成 write API、runner start endpoint、execute endpoint 或 trading endpoint。
- Shadow Run frontend list / detail / replay view 已实现；仍不得写成带 start / stop / execute / rerun / approve / trade 执行按钮的页面。
- Shadow Run overview backend read model 与 frontend summary 已实现；仍不得写成 write API、runner start endpoint、scheduler、Dashboard v2、GateS 全域 validation overview、Strategy Validation approval、trading authorization 或 LIVE ready。
- Shadow Run runner skeleton 已实现；仍不得写成 scheduler、后台 runner、runner started、shadow live trading enabled 或 live-ready。
- 不得把 LIVE 写成 ready / enabled。
- 不得把 AI 写成 started。
- 不得把 DH runtime 写成 integrated。
- 不得把 Integration-1 mock/test-support 写成 runtime started。
- 不得把 RealClient、real provider、private trading adapter 或 real permission probe 写成 implemented。
- 不得把 public marketdata readiness、Data Quality、permission readiness、risk preflight、preview、comparison、binding preview 或 archive closeout 写成 trading authorization。
- 不得把 Python offline foundation 写成 ML ready 或 live execution ready。
