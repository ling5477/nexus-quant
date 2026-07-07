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
- Shadow Run scheduler：`NOT IMPLEMENTED`（未实现）。
- GateS frontend / GateS 全域 validation runtime / GateS freeze：`NOT IMPLEMENTED`（未实现）/ `NOT STARTED`（未开始）。

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

GateR frozen baseline 的代码验证和 CI 证据以 [TESTING.md](TESTING.md)、[WORKLOG.md](WORKLOG.md) 和 `docs/gates/gate-r/` 归档为准。GateS-1 minimal backend read model 本轮已运行 `mvn -f backend/pom.xml -pl nq-api,nq-core,nq-infra -am test`，结果为 `BUILD SUCCESS`（构建成功）。本轮未运行 frontend build / E2E、Python pytest / mypy / ruff，因为未修改 `frontend/**` 或 `research/**`。

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
- frontend page / route / API client 已实现。
- Paper / Strategy / MarketData / Risk / Incident 全域聚合已实现。
- runner / scheduler started。
- migration implemented。
- LIVE、AI、DH runtime、RealClient、real provider、private trading adapter 或 real permission probe started / implemented。
- trading authorization、live-ready、trade approval 或 Shadow Live trading enabled。
