# Current Status

## 1. 当前总状态

- GateQ：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateQ release tag：`nq-gateq-freeze`。
- GateQ archive：`docs/gates/gate-q/`。
- GateP：`FROZEN / ACCEPTED / TAGGED / ARCHIVED`（已冻结 / 已接受 / 已打 tag / 已归档）。
- GateP release tag：`nq-gatep-freeze`。
- GateP archive：`docs/gates/gate-p/`。
- GateO 及更早 Gate：以 `docs/gates/**` 或 `docs/archive/**` 作为历史证据来源。
- GateR：`NQ-GATER-PLAN-SHADOW-RUN-OPERATIONALIZATION：PLAN READY / NOT IMPLEMENTED`（计划已就绪 / 未实现）。
- GateR-1：`NQ-GATER-1-SHADOW-RUN-DATA-MODEL-MIGRATION-PLAN-REVIEW：PASS / MIGRATION PLAN READY / NOT IMPLEMENTED`（通过 / migration 方案已就绪 / 未实现）。
- GateR-2：`NQ-GATER-2-SHADOW-RUN-LOCAL-FACT-MODEL-IMPLEMENTATION：IMPLEMENTED / PENDING REVIEW`（已实现 / 待复核）。
- GateR-2 P1 fix：`NQ-GATER-2-P1-FIX-ILLEGAL-TRANSITION-AUDIT-REQUIRES-NEW：IMPLEMENTED / PENDING REVIEW`（已实现 / 待复核）。
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
- Shadow run 写侧 local fact source：`IMPLEMENTED / PENDING REVIEW`（已实现 / 待复核），仅限本地 Shadow Run 事实表与 repository，不代表 runner 或交易授权。
- Shadow Run runner / scheduler / API / frontend page：`NOT IMPLEMENTED`（未实现）。

## 3. GateR-0 Planning Status

`docs/current/GATER_PLAN.md` 已建立 GateR-0 planning 入口，覆盖 Shadow Run 定义、状态机候选、最小数据模型候选、traceability、Paper vs Shadow consistency、risk / order intent preview 边界、候选 API / DTO / migration plan、前端候选页面、测试策略、安全边界、AI / DH runtime 边界、风险清单、Batch plan、validation commands、acceptance criteria、exit criteria 和 next concrete action。

该状态只表示 planning ready，不表示：

- GateR implementation started。
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

GateR-2 已新增 `V32__gate_r_shadow_run_fact_model.sql`，创建 `shadow_runs`、`shadow_run_events`、`shadow_run_snapshots`、`shadow_consistency_reports` 4 张本地事实表；已新增 Shadow Run domain model、状态机、repository port、JDBC implementation、repository/state machine/migration tests，并同步 `DB_SCHEMA.md`、`TESTING.md`、`WORKLOG.md`、`FACT_SOURCE_INDEX.md` 和 current 入口。

GateR-2 review P1 finding 已做最小修复：新增 `JdbcShadowRunIllegalTransitionAuditWriter`，使用 `TransactionTemplate` + `PROPAGATION_REQUIRES_NEW`（独立新事务）直接写入 `shadow_run_events`，使非法状态流转的 `ILLEGAL_STATE_TRANSITION_ATTEMPT`（非法状态流转尝试）审计事件不再依赖 `updateStatus()` 外层事务提交。`updateStatus()` 仍重新抛出原始 `ShadowRunStateTransitionException`，并保持 `shadow_runs.status` 与 `version` 不变。

该状态只表示 local fact model / repository 已实现并等待 review，不表示：

- GateR frozen / accepted。
- Shadow runner started。
- HTTP API implemented。
- frontend page implemented。
- LIVE ready 或 trading authorization。
- AI runtime started。
- DH runtime integrated。
- RealClient、real provider、private trading adapter 或 real permission probe implemented。

GateR-2 后续只能进入 P1 fix review / implementation review；review 通过前不得写 `READY TO COMMIT`（可进入提交前复核），不得启动 GateR-3 command skeleton，不得启动 Shadow runner。

## 6. Post-GateQ Current Cleanup

本轮将 `docs/current` tracked Markdown 从 125 个缩减为 17 个。108 个历史过程型 current copy 已通过 `git mv` 移入 `docs/archive/current-cleanup/post-gateq/**`，不删除历史证据，不移动 `docs/gates/gate-q/**` 已归档证据，不改 release tag 历史含义。

保留在 `docs/current` 的文件只承担当前事实入口、当前状态、路线、验证、工作记录、API、DB schema、架构/模块摘要、运行手册、前端设计系统入口和 Codex workflow 入口。已冻结 Gate 的过程证据只保留 archive pointer，不在 current 保留正文。

## 7. 当前验证口径

当前 GateR-2 是 backend implementation + Flyway migration + repository/state-machine/test + docs sync；本轮 P1 fix 只修改 backend infra repository/audit writer、infra tests 和 current docs。已运行 Maven 后端测试；frontend build、Playwright、pytest、mypy、ruff 未运行，因为本轮未修改 frontend、research、scripts、deploy、`.github`、API Controller 或页面。验证以 Git preflight、Maven backend tests、PostgreSQL smoke 选择器、diff check、forbidden-scope diff 和 broad rg boundary scan 为准，详见 [TESTING.md](TESTING.md)。
