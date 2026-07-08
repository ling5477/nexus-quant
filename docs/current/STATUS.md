# Current Status

## 1. 当前总状态

- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateQ release tag：`nq-gateq-freeze`。
- GateQ archive：`docs/gates/gate-q/`。
- GateP：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateP release tag：`nq-gatep-freeze`。
- GateP archive：`docs/gates/gate-p/`。
- GateO 及更早 Gate：以 `docs/gates/**` 或 `docs/archive/**` 作为历史证据来源。
- GateR：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；GateR-8 已完成并 push，commit `00e025d0e9f422f1b9aedbd409ee576e8892af12`，GitHub Actions run `28852212136`（`NQ CI Baseline`）为 `success`（成功）。
- GateR-1：`NQ-GATER-1-SHADOW-RUN-DATA-MODEL-MIGRATION-PLAN-REVIEW：PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`（通过 / migration 方案已就绪 / 未实现）。
- GateR-2：`NQ-GATER-2-SHADOW-RUN-LOCAL-FACT-MODEL-IMPLEMENTATION：IMPLEMENTED / VERIFIED COMMIT ACCEPTED`（已实现 / verified commit 已接受），commit `d21bb9886c60bbe7b40b09b7c01b4325c6899ca0`。
- GateR-2 P1 fix：已纳入 GateR-2 verified commit acceptance（verified commit 接受范围）。
- GateR-3：`NQ-GATER-3-SHADOW-RUN-RUNNER-SKELETON-IMPLEMENTATION：IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功）。
- GateR-4：`NQ-GATER-4-SHADOW-RUN-DECISION-TRACE-IMPLEMENTATION：IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功）。
- GateR-5：`NQ-GATER-5-SHADOW-CONSISTENCY-REPORT-IMPLEMENTATION：IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功）。
- GateR-6：`NQ-GATER-6-SHADOW-RUN-READ-ONLY-API-IMPLEMENTATION：IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功）。
- GateR-7：`NQ-GATER-7-FRONTEND-SHADOW-RUN-DETAIL-REPLAY-VIEW：IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功）。
- GateR-8：`NQ-GATER-8-SHADOW-RUN-LIST-AND-ENTRYPOINT-IMPLEMENTATION：IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功），commit `00e025d0e9f422f1b9aedbd409ee576e8892af12`。
- GateR release tag：`nq-gater-freeze`（release tag 已创建并推送）。
- GateS：下一阶段唯一推荐主线，目标为策略验证运营化与 Shadow 诊断闭环阶段。
- GateS-0：`PLAN / NOT IMPLEMENTED`（规划 / 未实现），当前 plan review baseline 为 [GATES_0_PLAN.md](GATES_0_PLAN.md)。
- GateS-1 work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核），入口为 [GATES_1_READ_MODEL_WO.md](GATES_1_READ_MODEL_WO.md)。
- GateS-1 minimal backend read model：`NQ-GATES-1-READ-MODEL-IMPLEMENTATION：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `GET /api/shadow-runs/overview`、DTO、core query service / query port、JDBC SELECT-only adapter 和后端测试，不代表 GateS-1 frozen / accepted、frontend page、GateS 全域 validation runtime 或交易授权。
- GateS-1 frontend overview work order：`NQ-GATES-1-FRONTEND-OVERVIEW-WO：PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核）；仅规划后续前端如何消费 `GET /api/shadow-runs/overview`，不代表前端已实现。
- GateS-1 frontend overview implementation：`NQ-GATES-1-FRONTEND-OVERVIEW-IMPLEMENTATION：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/shadow-runs` 顶部 Overview Summary、前端 type / client / query key / hook 和 `npm run build` 本地验证，不代表 GateS-1 frozen / accepted、Dashboard v2、后端 API、migration、E2E、LIVE 或交易授权。
- GateS-2 paper shadow consistency drilldown implementation：`NQ-GATES-2-PAPER-SHADOW-CONSISTENCY-DRILLDOWN-IMPLEMENTATION：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `GET /api/paper-shadow/consistency/drilldown`、DTO、core query service / query port、JDBC SELECT-only adapter 和后端测试，不代表 GateS-2 frozen / accepted、前端页面、Dashboard v2、runner / scheduler、LIVE 或交易授权。
- GateS-2 frontend consistency drilldown implementation：`NQ-GATES-2-FRONTEND-CONSISTENCY-DRILLDOWN-IMPLEMENTATION：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/shadow-runs/:shadowRunId` detail / replay 页面消费 `GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}` 的前端 type / API client / query key / hook / UI panel 和 `npm run build` 本地验证，不新增 route、Dashboard v2、后端 API、migration、E2E、LIVE、AI/DH runtime 或交易授权。
- GateS-3 strategy evaluation gate runtime baseline：`NQ-GATES-3-STRATEGY-EVALUATION-GATE-RUNTIME-BASELINE：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `GET /api/strategy-validation/overview`、DTO、core query service / query port、JDBC SELECT-only adapter 和后端测试，不代表 GateS-3 frozen / accepted、前端页面、Dashboard v2、scheduler、runner、LIVE 或交易授权。
- GateS-3 frontend strategy validation overview implementation：`NQ-GATES-3-FRONTEND-STRATEGY-VALIDATION-OVERVIEW-IMPLEMENTATION：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/validation` 页面顶部 Strategy Validation Overview panel、前端 type / API client / query key / hook 和 `npm run build` 本地验证，不新增 route、Dashboard v2、后端 API、migration、E2E、LIVE、AI/DH runtime 或交易授权。
- GateS-4 Python offline evaluation artifact baseline：`NQ-GATES-4-PYTHON-EVALUATION-ARTIFACT-BASELINE：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 Python research 离线 artifact 数据结构、parameter grid、JSON writer / reader、checksum / validation 和 pytest / mypy / ruff 本地验证，不代表 GateS-4 frozen / accepted、Java 生产绑定、API、migration、CI、LIVE、Python ML ready、Python live execution ready、AI/DH runtime 或交易授权。
- GateS-5 frontend Strategy Validation / Shadow Workbench：`NQ-GATES-5-FRONTEND-STRATEGY-VALIDATION-SHADOW-WORKBENCH：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/validation` 页面中的只读 Workbench 区块、现有 hooks/API client 复用、现有 smoke 更新和 `npm run build` / 目标 Playwright 验证，不新增 route、Dashboard v2、后端 API、migration、CI、Python artifact UI 接入、LIVE、AI/DH runtime 或交易授权。
- GateS-6 Incident / Replay read model implementation：`NQ-GATES-6-INCIDENT-REPLAY-READ-MODEL-IMPLEMENTATION：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖 `GET /api/incidents/replay/overview`、DTO、core query service / query port、JDBC SELECT-only adapter 和后端测试，不代表 GateS-6 frozen / accepted、前端页面、Dashboard v2、migration、CI、runner、scheduler、LIVE、AI/DH runtime 或交易授权。
- GateS-6 frontend Incident / Replay overview implementation：`NQ-GATES-6-FRONTEND-INCIDENT-REPLAY-OVERVIEW-IMPLEMENTATION：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核）；仅覆盖现有 `/strategies/validation` 页面消费 `GET /api/incidents/replay/overview` 的只读 overview panel、前端 type / API client / query key / hook 和 `npm run build` 本地验证，不新增 route、Dashboard v2、E2E、后端 API、migration、Python、CI、LIVE、AI/DH runtime 或交易授权。
- GateS freeze readiness review：`NQ-GATES-FREEZE-READINESS-REVIEW：READY FOR FREEZE CLOSEOUT`（可进入 freeze closeout）；仅表示 GateS-0 到 GateS-6 当前证据、CI 和边界已完成冻结前 readiness 审查，不表示 freeze 已执行、accepted 已完成或 release tag 已创建。
- 本轮 cleanup：`NQ-DOCS-CURRENT-POST-GATEQ-CLEANUP：IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实施 / 已自审 / 可进入提交前复核）。

## 2. 禁止边界

- LIVE：`DISABLED`（关闭）。
- AI：`NOT STARTED`（未开始）。
- DH runtime：`NOT INTEGRATED`（未集成）。
- Integration-1：`NOT STARTED / mock-test-support only where applicable`（未开始 / 仅在适用处保留 mock 测试支撑）。
- RealClient：`NOT IMPLEMENTED`（未实现）。
- real provider：`NOT IMPLEMENTED`（未实现）。
- private trading adapter：`NOT IMPLEMENTED`（未实现）。
- real permission probe：`NOT IMPLEMENTED`（未实现）。
- Shadow Live runner：`NOT STARTED`（未开始）。
- Shadow run 写侧 local fact source：`IMPLEMENTED / VERIFIED COMMIT ACCEPTED`（已实现 / verified commit 已接受），仅限本地 Shadow Run 事实表与 repository，不代表交易授权。
- Shadow Run runner skeleton：`IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功），仅限本地同步调用骨架，不是后台运行。
- Shadow Run decision trace previews：`IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功），仅限本地结构化 trace / risk / order intent preview，不是交易授权。
- Shadow consistency report service：`IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功），仅限调用方本地只读 Paper / Shadow summary 比较与本地 report 持久化，不是交易授权。
- Shadow Run read-only API：`IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功），仅限 GET list / detail / events / snapshots / latest consistency report，不是写接口、runner trigger 或交易授权。
- Shadow Run list frontend page：`IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功），仅限 `/strategies/shadow-runs` 只读列表、status 筛选和进入 detail，不提供写侧操作，不是交易授权。
- Shadow Run detail / replay frontend page：`IMPLEMENTED / PUSHED / CI SUCCESS`（已实现 / 已推送 / CI 成功），仅限 `/strategies/shadow-runs/:shadowRunId` 只读查看本地 facts，不提供写侧操作，不是交易授权。
- Shadow Run overview backend read model：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核），仅限 `GET /api/shadow-runs/overview` 只读聚合本地 Shadow Run facts，不提供写侧 endpoint，不是 runner trigger 或交易授权。
- Shadow Run overview frontend work order：`PLAN READY / NOT IMPLEMENTED / READY TO COMMIT`（规划已就绪 / 未实现 / 可进入提交前复核），仅限规划 type / client / hook / UI placement / states / badges / test scope，不是前端实现。
- Shadow Run overview frontend summary：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核），仅在现有 `/strategies/shadow-runs` 列表页顶部消费 `GET /api/shadow-runs/overview` 并展示只读 Overview Summary，不新增 route、Dashboard v2、写侧动作、E2E、后端 API、migration 或交易授权。
- Paper shadow consistency drilldown backend read model：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核），仅限 `GET /api/paper-shadow/consistency/drilldown` 按单个 `shadowRunId` 只读聚合本地 Shadow Run / consistency / snapshot / event facts，不创建 report，不追加 event / snapshot，不提供写侧 endpoint，不是 runner trigger、scheduler trigger 或交易授权。
- Strategy validation overview backend read model：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核），仅限 `GET /api/strategy-validation/overview` 只读聚合本地 strategy / evaluation / publish / Paper / Shadow evidence，不提供写侧 endpoint，不是 strategy approval、runner trigger、scheduler trigger 或交易授权。
- Strategy validation overview frontend panel：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核），仅限现有 `/strategies/validation` 页面顶部消费 `GET /api/strategy-validation/overview` 并展示 counts、latestDecision、decisionReasons、limitations、blockers / warnings / nextSteps、evidenceAnchors、traceId 和固定安全边界 badges；不是 Dashboard v2、写侧动作或交易授权。
- Python offline evaluation artifact baseline：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核），仅限 `research/py` 离线 JSON artifact / parameter grid / checksum / validation 工具和测试；不是 Java production fact import，不是 API，不是 runner，不是 ML ready、live execution ready 或交易授权。
- Strategy Validation / Shadow Workbench frontend block：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核），仅限现有 `/strategies/validation` 页面聚合 Strategy Validation overview、Shadow Run overview 与 Paper vs Shadow drilldown 的只读展示；不是新增 route、Dashboard v2、Python artifact UI 接入、写侧动作、Shadow Live trading enabled 或交易授权。
- Incident Replay overview backend read model：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核），仅限 `GET /api/incidents/replay/overview` 只读聚合本地 Shadow / consistency / Paper alert / recovery / trade replay 诊断事实；不是写侧 endpoint、真实 incident runtime、runner trigger、scheduler trigger、LIVE ready 或交易授权。
- Incident Replay overview frontend panel：`IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT`（已实现 / 已自审 / 可进入提交前复核），仅限现有 `/strategies/validation` 页面展示 incidentSeverity、counts、latestEvidence、blockers / warnings / nextSteps、evidenceAnchors、traceId 和固定安全边界 badges；不是新增 route、Dashboard v2、Incident Center、AI 决策中心、写侧动作、自动处置、实盘就绪或交易授权。
- Shadow Run scheduler：`NOT IMPLEMENTED`（未实现）。
- GateS 全域 frontend / Dashboard v2 / GateS 全域 validation runtime / GateS freeze：`NOT IMPLEMENTED`（未实现）/ `NOT STARTED`（未开始）。

## 3. GateR-0 Planning Status

`docs/current/GATER_PLAN.md` 已建立 GateR-0 planning 入口，覆盖 Shadow Run 定义、状态机候选、最小数据模型候选、traceability、Paper vs Shadow consistency、risk / order intent preview 边界、候选 API / DTO / migration plan、前端候选页面、测试策略、安全边界、AI / DH runtime 边界、风险清单、Batch plan、validation commands、acceptance criteria、exit criteria 和 next concrete action。

该 GateR-0 状态只表示 planning ready，不表示：

- GateR-0 已启动实现。
- Shadow Run local fact implemented。
- API implemented。
- migration implemented。
- frontend page implemented。
- test implemented。
- Shadow runner started。
- LIVE / AI / DH runtime started。

## 4. GateR-1 Data Model / Migration Plan Review Status

`docs/current/GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md` 已完成 Shadow Run 数据模型、状态机、表结构候选、索引、外键、JSONB 脱敏、敏感字段禁止、migration 版本、回滚策略、`DB_SCHEMA.md` 后续更新计划和 GateR-2 entry criteria 审查。

该状态只表示 migration plan ready，不表示：

- migration implemented。
- Shadow Run table created。
- Shadow Run record created。
- Shadow runner started。
- API implemented。
- frontend page implemented。
- test implemented。
- LIVE / AI / DH runtime started。

GateR-1 结论建议后续 GateR-2 以独立 implementation 任务进入本地 Shadow Run fact model / repository 落地；GateR-2 仍必须遵守 no-LIVE、no-private-endpoint、no-credential-access、no-order-submission、no-ledger-mutation 边界。

## 5. GateR-2 Shadow Run Local Fact Model Implementation Status

GateR-2 已通过 verified commit 接受，commit `d21bb9886c60bbe7b40b09b7c01b4325c6899ca0`。该 commit 新增 `V32__gate_r_shadow_run_fact_model.sql`，创建 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports` 4 张本地事实表；已新增 Shadow Run domain model、状态机、repository port、JDBC implementation、repository/state machine/migration tests，并同步 `DB_SCHEMA.md`、`TESTING.md`、`WORKLOG.md`、`FACT_SOURCE_INDEX.md` 和 current 入口。

GateR-2 review P1 finding 已纳入 verified commit 接受范围：`JdbcShadowRunIllegalTransitionAuditWriter` 使用 `TransactionTemplate` + `PROPAGATION_REQUIRES_NEW`（独立新事务）直接写入 `shadow_run_events`，使非法状态流转的 `ILLEGAL_STATE_TRANSITION_ATTEMPT`（非法状态流转尝试）审计事件不再依赖 `updateStatus()` 外层事务提交。`updateStatus()` 仍重新抛出原始 `ShadowRunStateTransitionException`，并保持 `shadow_runs.status` 与 `version` 不变。

该状态只表示 local fact model / repository 已实现并通过 verified commit 接受。GateR 后续已完成 `FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；该状态仍不表示：

- GateS implemented / frozen / accepted。
- Shadow runner 后台启动。
- HTTP API implemented。
- frontend page implemented。
- LIVE ready 或 trading authorization。
- AI runtime started。
- DH runtime integrated。
- RealClient、real provider、private trading adapter 或 real permission probe implemented。

GateR-3 已新增本地 Shadow Run runner skeleton：通过 `ShadowRunRunnerService` 使用 `ShadowRunFactRepository` 创建本地 run、通过 `ShadowRunStateMachine` 推进 `CREATED -> PRECHECKING -> READY -> RUNNING -> COMPLETED / BLOCKED / FAILED`，并写入 `INPUT_MARKETDATA / STRATEGY_DECISION / RISK_PREFLIGHT / ORDER_INTENT_PREVIEW` 4 类只读快照。GateR-4 已将 `STRATEGY_DECISION / RISK_PREFLIGHT / ORDER_INTENT_PREVIEW` 扩展为 structured decision trace、risk allow/block/warn snapshot 和 `previewOnly=true` 的 order intent preview envelope，并把 blocker / warning / nextSteps 写入 result。GateR-5 已新增 `ShadowConsistencyReportService`，只消费调用方提供的本地只读 Paper / Shadow summary，生成 `CONSISTENT / DIVERGED / NOT_COMPARABLE / PARTIAL / FAILED`（一致 / 偏离 / 不可比 / 部分可比 / 失败）report，并复用既有 repository port 写入 `shadow_consistency_reports`。GateR-6 已新增 Shadow Run read-only API，只通过 GET 读取本地 run detail、events、snapshots 与 latest consistency report。GateR-7 已新增前端 Shadow Run detail / replay 只读页面，只展示本地 facts、events、snapshots、latest consistency report 和 no-side-effect flags。GateR-8 已新增 Shadow Run 只读列表 API 与 `/strategies/shadow-runs` 列表入口，只展示本地 run summary、status 筛选、no-side-effect flags 和进入 detail 的导航。该状态不代表写接口、scheduler、后台 runner、LIVE、AI/DH runtime、RealClient、real provider、private trading adapter 或交易授权已启用。

## 6. Post-GateQ Current Cleanup

本轮将 `docs/current` tracked Markdown 从 125 个缩减为 17 个。108 个历史过程型 current copy 已通过 `git mv` 移入 `docs/archive/current-cleanup/post-gateq/**`，不删除历史证据，不移动 `docs/gates/gate-q/**` 已归档证据，不改 release tag 历史含义。

保留在 `docs/current` 的文件只承担当前事实入口、当前状态、路线、验证、工作记录、API、DB schema、架构/模块摘要、运行手册、前端设计系统入口和 Codex workflow 入口。已冻结 Gate 的过程证据只保留 archive pointer，不在 current 保留正文。

## 7. 当前验证口径

GateR frozen baseline 的代码验证和 CI 证据以 [TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md) 和 `docs/gates/gate-r/` 归档为准。GateS-1 minimal backend read model 已运行 `mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test`，结果为 `BUILD SUCCESS`（构建成功）。GateS-1 frontend overview implementation 已运行 `npm run build`，结果为 `PASS`（通过）；未新增或运行 E2E，因为该轮明确禁止新增 E2E，且当前 frontend 没有独立 component/smoke test runner。GateS-2 paper shadow consistency drilldown backend implementation 已运行 `mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test`，结果为 `BUILD SUCCESS`（构建成功）。GateS-2 frontend consistency drilldown implementation 本轮已运行 `npm run build`，结果为 `PASS`（通过）；未新增或运行 E2E，因为本轮明确禁止新增 E2E，且当前 frontend 没有独立 component/smoke test runner。GateS-3 strategy evaluation gate runtime baseline 已运行 `mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test`，结果为 `BUILD SUCCESS`（构建成功）。

## 8. GateS-0 Planning Status

`docs/current/GATES_0_PLAN.md` 已建立 GateS-0 planning / fact-source reconciliation 入口，覆盖 GateR frozen baseline、current docs drift findings、GateS recommended objective、non-goals、GateS-0 到 GateS-FREEZE batch plan、GateS-1 backend read-model contract proposal、GateS-1 frontend page contract proposal、统一术语、forbidden wording / actions、P0/P1/P2/P3 risks、validation commands、acceptance criteria 和 next concrete action。

该状态只表示 GateS planning baseline ready，不表示：

- GateS-1 implementation started。
- backend read model implemented。
- API implemented。
- frontend page implemented。
- migration implemented。
- test implemented。
- LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe started / implemented。

## 9. GateS-1 Read Model Work Order Status

`docs/current/GATES_1_READ_MODEL_WO.md` 已建立 GateS-1 Shadow Run operational read model work order 入口，覆盖 read model owner、source tables / existing APIs、data source mapping、candidate DTO contract、candidate API contract、frontend IA contract、status / enum semantics、evidence anchor model、blocker / warning / nextSteps model、no-side-effect boundary、security / credential boundary、LIVE / AI / DH / Integration boundary、testing scope、implementation slice recommendation、P0/P1/P2/P3 findings、acceptance criteria 和 next concrete action。

该状态只表示 GateS-1 work order ready，不表示：

- GateS-1 frozen / accepted。
- frontend page implemented。
- migration implemented。
- runner / scheduler started。
- LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe started / implemented。

## 10. GateS-1 Minimal Backend Read Model Implementation Status

本轮已实现 `NQ-GATES-1-READ-MODEL-IMPLEMENTATION`：新增 `GET /api/shadow-runs/overview` 的最小后端 read model。实现范围限定为 `nq-api` GET-only Controller / DTO、`nq-core` read model contract / query service / query port、`nq-infra` JDBC SELECT-only adapter，以及 API / service / repository 测试。

该 read model 只读取 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports` 本地事实，返回 `diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`，并聚合 counts、latestRun、latestConsistency、divergenceSeverity、blockers、warnings、nextSteps 和 evidenceAnchors。

该状态不表示：

- GateS-1 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- GateS 全域 validation overview 已实现。
- GateS 全域 frontend、Dashboard v2 或新 route 已实现。
- Paper / Strategy / MarketData / Risk / Incident 全域聚合已实现。
- runner / scheduler started。
- migration implemented。
- LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe started / implemented。
- trading authorization、live-ready、trade approval 或 Shadow Live trading enabled。

## 11. GateS-1 Frontend Overview Work Order Status

`docs/current/GATES_1_FRONTEND_OVERVIEW_WO.md` 已建立 GateS-1 Shadow Run overview frontend work order 入口，覆盖 current frontend baseline、backend endpoint contract、API type plan、API client / hook plan、UI placement decision、UI state plan、boundary badge plan、color / wording rules、test scope、non-goals、P0/P1/P2/P3 findings、acceptance criteria 和 next concrete action。

该状态只表示 frontend work order ready，不表示：

- GateS-1 frontend implemented。
- GateS-1 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- route / page / API client / hook / type 已修改。
- E2E 已新增。
- Dashboard v2 已启动。
- runner / scheduler started。
- LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe started / implemented。
- trading authorization、trade approval 或 Shadow Live trading enabled。

## 12. GateS-1 Frontend Overview Implementation Status

本轮已实现 `NQ-GATES-1-FRONTEND-OVERVIEW-IMPLEMENTATION`：在现有 `/strategies/shadow-runs` 列表页顶部增加 Overview Summary，并最小消费 `GET /api/shadow-runs/overview`。

实现范围限定为：

- `frontend/src/types/shadow-runs.ts`：新增 `ShadowRunOverviewResponse`、latest run / latest consistency / blocker / warning / nextStep / evidence anchor / divergence severity types。
- `frontend/src/api/shadow-runs.ts`：新增 `getShadowRunOverview()`，仅发起 `GET /api/shadow-runs/overview`。
- `frontend/src/api/query-keys.ts`：新增 canonical query key `['shadow-runs', 'overview']`。
- `frontend/src/hooks/useShadowRunQueries.ts`：新增 `useShadowRunOverview()`，沿用 `retry: false`，不启用 polling，不写入 Zustand。
- `frontend/src/pages/shadow-runs/ShadowRunListPage.tsx`：在现有列表页顶部新增 Overview Summary，覆盖 loading / error / empty / normal / stale / diverged / blocked / failed 展示，固定展示 LIVE / real provider / private trading / diagnostic only / not trading authorization / AI-DH runtime boundary badges。

该状态不表示：

- GateS-1 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- Dashboard v2、新 route、AI 决策中心或 GateS 全域 validation runtime 已实现。
- 后端 API、migration、Python、CI workflow 或 E2E 已新增。
- 写侧、交易或资金操作入口已新增或启用。
- LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe started / implemented。
- trading authorization、trade approval、Shadow Live trading enabled、Python ML readiness 或 Python live execution readiness。

## 16. GateS-4 Python Offline Evaluation Artifact Baseline Status

本轮已实现 `NQ-GATES-4-PYTHON-EVALUATION-ARTIFACT-BASELINE`：在 `research/py` 离线研究域新增 evaluation artifact baseline、最小 parameter grid、JSON writer / reader、checksum 和 validation。

实现范围限定为：

- `research/py/src/nq_research/evaluation/parameters.py`：新增 `ParameterSet`、`expand_parameter_grid()` 和 `build_parameter_set_id()`；空 grid 返回单个空参数集，非空 grid 按 key 稳定排序展开，不启动 Optuna / Ray / 并行任务 / runner。
- `research/py/src/nq_research/evaluation/artifacts.py`：新增 `EvaluationArtifact`、`build_evaluation_artifact()`、`write_evaluation_artifact()`、`read_evaluation_artifact()`、`compute_checksum()`、`validate_artifact()` 和 sensitive field guard；checksum 不包含 checksum 字段自身。
- `research/py/tests/test_evaluation_artifacts.py`：覆盖 parameter grid、parameterSetId、artifact write/read、checksum、tamper detection、必填字段、强制 `diagnosticOnly=true`、`notTradingAuthorization=true`、`liveExecutionReady=false`、禁止敏感字段、fake metrics fixture 边界和 no-network guard。
- `docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、`docs/current/FACT_SOURCE_INDEX.md`：最小同步当前事实与验证记录。

该 artifact baseline 固定要求 `schemaVersion=python-evaluation-artifact.v1`、`source=PYTHON_OFFLINE`、`diagnosticOnly=true`、`notTradingAuthorization=true`、`liveExecutionReady=false`。测试中的 metrics 仅为 `FAKE_METRICS_FIXTURE`（fixture 假指标），并强制 `realTradingPerformance=false`。

该状态不表示：

- GateS-4 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- Java 后端 API、DB migration、CI workflow、前端页面、runner / scheduler 或 production fact import 已新增。
- artifact 已绑定 Java 生产链路、已导入数据库或可驱动 Paper / Shadow / LIVE。
- Python ML ready、Python live execution ready、strategy approval、trading authorization、trade approval 或 LIVE enable。
- AI runtime started、DH runtime integrated、RealClient / real provider / private trading adapter / real permission probe implemented。

## 17. GateS-5 Frontend Strategy Validation / Shadow Workbench Status

本轮已实现 `NQ-GATES-5-FRONTEND-STRATEGY-VALIDATION-SHADOW-WORKBENCH`：在现有 `/strategies/validation` 页面增加最小 Strategy Validation / Shadow Workbench 区块，聚合 Strategy Validation overview、Shadow Run overview 与 Paper vs Shadow consistency drilldown 的只读运营视角。

实现范围限定为：

- `frontend/src/pages/strategies/StrategyValidationPage.tsx`：复用现有 `useStrategyValidationOverview()`、`useShadowRunOverview()` 和 `usePaperShadowConsistencyDrilldown()`，新增 Workbench 区块，展示 validation counts、latest decision、Shadow run counts、latest run / consistency、divergence severity、blockers / warnings / nextSteps、evidence anchors、traceId 和 Shadow detail 链接。
- `frontend/tests/e2e/strategy-validation-paper-shadow-smoke.spec.ts`：更新现有 smoke fixture 与断言，覆盖 Workbench Strategy Validation counts、Shadow Run counts、boundary badges、`APPROVED`（验证层通过）非交易授权语义，以及误导性交易文案不得出现。
- `docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、`docs/current/FACT_SOURCE_INDEX.md`、`docs/current/README.md` 和 `README.md`：最小同步当前事实与验证记录。

该 Workbench 固定展示 `LIVE DISABLED`（LIVE 关闭）、`Real provider NOT IMPLEMENTED`（真实 provider 未实现）、`Private trading NOT IMPLEMENTED`（私有交易未实现）、`Validation is not trading authorization`（验证不是交易授权）、`Shadow Run is diagnostic only`（Shadow Run 仅诊断）和 `AI/DH runtime not integrated`（AI/DH runtime 未集成）。`APPROVED` 只显示为 validation 层通过，不表示交易授权。

该状态不表示：

- GateS-5 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- 新 route、Dashboard v2、后端 API、DB migration、CI workflow、Python research code 或 Python artifact UI 接入已新增。
- runner / scheduler、Paper run、Shadow run、Shadow Live trading、真实交易按钮或写侧 client 已启动或新增。
- 实盘就绪、trade approval、trading authorization、real provider、private trading adapter、real permission probe、AI started 或 DH integrated。

## 18. GateS-6 Incident Replay Overview Read Model Status

本轮已实现 `NQ-GATES-6-INCIDENT-REPLAY-READ-MODEL-IMPLEMENTATION`：新增 `GET /api/incidents/replay/overview` 的最小后端 Incident / Replay GET-only read model，用于只读查看本地诊断证据聚合状态。

实现范围限定为：

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/monitoring/api/web/IncidentReplayOverviewController.java`、`IncidentReplayOverviewResponse.java`：新增 GET-only Controller 与 HTTP response DTO。
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/monitoring/application/incident/**`、`backend/nq-core/src/main/java/com/guidinglight/nexusquant/monitoring/domain/port/**`：新增 Incident Replay severity、read model、query service、query port 和 facts contract。
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayOverviewQueryRepository.java`：新增 JDBC SELECT-only adapter，只读取本地诊断 fact tables。
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/monitoring/api/web/IncidentReplayOverviewControllerTest.java`、`backend/nq-core/src/test/java/com/guidinglight/nexusquant/monitoring/application/incident/IncidentReplayOverviewQueryServiceTest.java`、`backend/nq-infra/src/test/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayOverviewQueryRepositoryTest.java`：覆盖 GET-only、boundary flags、severity、敏感 / 误导字段过滤、SQL 只读和禁止表范围。
- `docs/current/API.md`、`docs/current/STATUS.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`、`docs/current/FACT_SOURCE_INDEX.md`：最小同步当前事实与验证记录。

该 read model 只读取 `shadow_run_events`、`shadow_consistency_reports`、`paper_run_alerts`、`paper_run_recovery_events`、`trade_replay_records` 本地事实，返回 `diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`，并聚合 counts、latestEvidence、incidentSeverity、blockers、warnings、nextSteps 和 evidenceAnchors。当前没有独立 incident 表或 runtime readiness incident fact table，接口会以 `SOURCE_NOT_AVAILABLE` warning 明确边界。

该后端 read model 状态本身不表示：

- GateS-6 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- 前端页面、Dashboard v2、DB migration、CI workflow、Python research code、runner、scheduler 或 production incident runtime 已新增。
- incident / alert / recovery / replay 被创建、确认、解决、重试、启动或停止。
- 写侧、交易、资金、account、ledger、order 或 position 操作入口已新增或启用。
- 实盘就绪、trade approval、trading authorization、real provider、private trading adapter、real permission probe、AI started 或 DH integrated。

## 19. GateS-6 Frontend Incident / Replay Overview Implementation Status

本轮已实现 `NQ-GATES-6-FRONTEND-INCIDENT-REPLAY-OVERVIEW-IMPLEMENTATION`：在现有 `/strategies/validation` 页面增加 Incident / Replay Overview 只读区块，最小消费 `GET /api/incidents/replay/overview`。

实现范围限定为：

- `frontend/src/types/incident-replay.ts`：新增 `IncidentReplayOverviewResponse`、`IncidentReplayLatestEvidence`、`IncidentReplayEvidenceAnchor`、`IncidentReplayBlocker`、`IncidentReplayWarning`、`IncidentReplayNextStep` 和 `IncidentReplaySeverity` 前端类型。
- `frontend/src/api/incident-replay.ts`：新增 `getIncidentReplayOverview()`，仅调用 GET `/incidents/replay/overview`。
- `frontend/src/api/query-keys.ts`：新增 canonical query key `['incidents', 'replay', 'overview']`。
- `frontend/src/hooks/useIncidentReplayOverview.ts`：新增 `useIncidentReplayOverview()`，沿用 TanStack Query，`retry: false`，不启用 polling，不写入 Zustand。
- `frontend/src/pages/strategies/StrategyValidationPage.tsx`：在现有 Strategy Validation 页面新增 Incident / Replay Overview panel，展示 incidentSeverity、totalEvidenceItems、shadowEventCount、consistencyDivergenceCount、paperAlertCount、recoveryEventCount、replayEventCount、latestEvidence、blockers / warnings / nextSteps、evidenceAnchors、traceId 和固定安全边界 badges。

该 panel 覆盖 loading、error、empty、normal / none、warning、high、critical、source unavailable 和 partial data 展示。`HIGH`（高诊断优先级）与 `CRITICAL`（严重诊断优先级）只表示人工复核优先级，不表示自动处置、交易授权、实盘就绪或真实 incident runtime。

该状态不表示：

- GateS-6 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- 新 route、Dashboard v2、完整 Incident Center、AI 决策中心、E2E、后端 API、DB migration、CI workflow、Python research code、runner、scheduler 或 production incident runtime 已新增。
- incident / alert / recovery / replay 被创建、确认、解决、重试、启动或停止。
- start / stop / execute / trade、真实交易按钮、写侧 client、真实交易所调用、credential 读取或 account / ledger / order / position 操作已新增或启用。
- LIVE ready、trade approval、trading authorization、real provider、private trading adapter、real permission probe、AI started 或 DH integrated。

## 13. GateS-2 Frontend Consistency Drilldown Implementation Status

本轮已实现 `NQ-GATES-2-FRONTEND-CONSISTENCY-DRILLDOWN-IMPLEMENTATION`：在现有 `/strategies/shadow-runs/:shadowRunId` detail / replay 页面新增 Paper vs Shadow Consistency Drilldown panel，并最小消费 `GET /api/paper-shadow/consistency/drilldown?shadowRunId={shadowRunId}`。

实现范围限定为：

- `frontend/src/types/shadow-runs.ts`：新增 `PaperShadowConsistencyDrilldownResponse`、shadow run、latest consistency、snapshot / event summary、evidence anchor、blocker、warning、nextStep、`PaperShadowComparisonStatus` 和 divergence severity types。
- `frontend/src/api/shadow-runs.ts`：新增 `getPaperShadowConsistencyDrilldown(shadowRunId)`，仅发起 GET 请求。
- `frontend/src/api/query-keys.ts`：新增 canonical query key `['paper-shadow', 'consistency-drilldown', shadowRunId]`。
- `frontend/src/hooks/useShadowRunQueries.ts`：新增 `usePaperShadowConsistencyDrilldown(shadowRunId)`，`shadowRunId` 缺失时 disabled，沿用 `retry: false`，不启用 polling，不写入 Zustand。
- `frontend/src/pages/shadow-runs/ShadowRunDetailPage.tsx`：新增 Consistency Drilldown panel，覆盖 loading / error / missing / no report / normal / stale / diverged / blocked / failed 展示，固定显示 LIVE disabled、Real provider NOT IMPLEMENTED、Private trading NOT IMPLEMENTED、Shadow Run is diagnostic only、Not trading authorization、AI/DH runtime not integrated boundary badges。

该状态不表示：

- GateS-2 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- Dashboard v2、新 route、AI 决策中心或 GateS 全域 validation runtime 已实现。
- 后端 API、migration、Python、CI workflow、E2E 或真实 provider 已新增。
- 写侧、交易或资金操作入口已新增或启用。
- LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe started / implemented。
- trading authorization、trade approval、Shadow Live trading enabled、Python ML readiness 或 Python live execution readiness。

## 14. GateS-3 Strategy Evaluation Gate Runtime Baseline Status

本轮已实现 `NQ-GATES-3-STRATEGY-EVALUATION-GATE-RUNTIME-BASELINE`：新增 `GET /api/strategy-validation/overview` 的最小后端 read model，用于查看 Strategy Evaluation Gate runtime baseline 的 validation-only 聚合状态。

实现范围限定为：

- `nq-api`：GET-only Controller 与 HTTP response DTO。
- `nq-core`：`StrategyValidationDecision` enum、overview read model、query service、query port 和 facts contract。
- `nq-infra`：JDBC SELECT-only adapter，只读取本地 evidence tables。
- tests：API / core service / JDBC repository 最小回归测试。
- docs/current：最小同步 API、STATUS、TESTING、WORKLOG、FACT_SOURCE_INDEX。

该 read model 只读取 `strategy_versions`、`backtest_runs`、`backtest_eval_reports`、`backtest_publish_records`、`paper_trading_runs`、`shadow_runs`、`shadow_consistency_reports` 本地事实，返回 `diagnosticOnly=true`、`noSideEffect=true`、`notTradingAuthorization=true`、`liveDisabled=true`、`realProviderImplemented=false`、`privateTradingImplemented=false`、`aiDhRuntimeIntegrated=false`，并聚合 total / evaluated / approvedForValidation / rejectedForValidation / needsReview / blocked、latestDecision、blockers、warnings、nextSteps 和 evidenceAnchors。

`APPROVED`（验证层通过）只表示 validation evidence 暂时满足后续 review，不表示交易授权、LIVE enable、strategy 实盘就绪或 trade approval。

该状态不表示：

- GateS-3 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- 前端页面、Dashboard v2、新 route 或 E2E 已实现。
- 新 migration、Python research、CI workflow 或 `nq-app` context 已修改。
- evaluation / publish / Paper / Shadow run 被创建、启动或停止。
- 写侧、交易或资金操作入口已新增或启用。
- LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe started / implemented。
- trading authorization、trade approval、Shadow Live trading enabled、Python ML readiness 或 Python live execution readiness。

## 15. GateS-3 Frontend Strategy Validation Overview Implementation Status

本轮已实现 `NQ-GATES-3-FRONTEND-STRATEGY-VALIDATION-OVERVIEW-IMPLEMENTATION`：在现有 `/strategies/validation` 页面顶部增加 Strategy Validation Overview panel，并最小消费 `GET /api/strategy-validation/overview`。

实现范围限定为：

- `frontend/src/types/strategy-validation.ts`：新增 `StrategyValidationOverviewResponse`、latestDecision、evidence anchor、blocker、warning、nextStep 和 `StrategyValidationDecision` types。
- `frontend/src/api/strategy-validation.ts`：新增 `getStrategyValidationOverview()`，仅发起 `GET /api/strategy-validation/overview`。
- `frontend/src/api/query-keys.ts`：新增 canonical query key `['strategy-validation', 'overview']`。
- `frontend/src/hooks/useStrategyValidationQueries.ts`：新增 `useStrategyValidationOverview()`，沿用 `retry: false`，不启用 polling，不写入 Zustand。
- `frontend/src/pages/strategies/StrategyValidationPage.tsx`：在现有页面顶部新增 Overview panel，覆盖 loading / error / empty / no evidence / blocked / rejected / needs review / approved 展示，固定显示 LIVE DISABLED、Real provider NOT IMPLEMENTED、Private trading NOT IMPLEMENTED、Validation is not trading authorization、Not trading authorization、AI/DH runtime not integrated boundary badges。

`APPROVED`（验证层通过）只表示 validation evidence 暂时满足后续 review，不表示交易授权、LIVE enable、strategy 实盘就绪或 trade approval。

该状态不表示：

- GateS-3 `FROZEN`（已冻结）或 `ACCEPTED`（已接受）。
- Dashboard v2、新 route、AI 决策中心或 GateS 全域 validation runtime 已实现。
- 后端 API、migration、Python research、CI workflow、E2E 或真实 provider 已新增。
- evaluation / publish / Paper / Shadow run 被创建、启动或停止。
- 写侧、交易或资金操作入口已新增或启用。
- LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe started / implemented。
- trading authorization、trade approval、Shadow Live trading enabled、Python ML readiness 或 Python live execution readiness。
