# GateR Evidence Matrix (GateR-0 to GateR-8)

## Scope

- 版本范围：`dev`（commit `f2507cb2`，无未提交本地改动）。
- 结论：`PASS / COMPLETED / RELEASE TAG PUSHED`（通过 / 已完成 / tag 已推送）。

## Evidence matrix

| Stage | Evidence / Commit | Evidence artifact | Boundary outcome |
|---|---|---|---|
| GateR-0 | `docs/gates/gate-r/source/GATER_PLAN.md` | 规划入口已建立 | 只允许规划，不代表 runtime 启动 |
| GateR-1 | `docs/gates/gate-r/source/GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md` | migration plan review 已通过 | migration 方案仅为事实源对齐，不等于交易授权 |
| GateR-2 | `d21bb9886` | `docs/current/DB_SCHEMA.md` + `backend/nq-infra/src/main/resources/db/migration/V32__gate_r_shadow_run_fact_model.sql` + `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcShadowRunFactRepositoryTest.java` | 本地 fact model 落地，仅本地事实写入与查询 |
| GateR-3 | `346c2314` | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunRunnerService.java` | runner skeleton 为 local no-side-effect；不启动 scheduler 或后台 runner |
| GateR-4 | `0391a044` | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/shadowrun/ShadowRunStateMachine.java` + `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunRunnerServiceTest.java` | structured decision trace 与 risk snapshot 为只读诊断 |
| GateR-5 | `3c20d53c` | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunConsistencyReportService.java` + `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunConsistencyReportServiceTest.java` | consistency report 仅输出本地对账状态 |
| GateR-6 | `b9a3a149` | `backend/nq-api/src/main/java/com/guidinglight/nexusquant/strategy/api/web/ShadowRunReadOnlyController.java` + `backend/nq-api/src/test/java/com/guidinglight/nexusquant/strategy/api/web/ShadowRunReadOnlyControllerTest.java` | read-only API，GET-only |
| GateR-7 | `3a06ad65` | `frontend/src/pages/shadow-runs/ShadowRunDetailPage.tsx` + `frontend/tests/e2e/shadow-run-detail-smoke.spec.ts` | detail / replay 为只读展示 |
| GateR-8 | `00e025d0` | `frontend/src/pages/shadow-runs/ShadowRunListPage.tsx` + `frontend/src/api/shadow-runs.ts` + `frontend/src/hooks/useShadowRunQueries.ts` | list / entrypoint + detail 路由（无执行按钮） |

## Notes

- GateR-2 到 GateR-8 全部仅承接只读事实与诊断展示，不包含写接口、交易提交、runner 调度、真实 provider 接入或 LIVE。
- 相关实现证据同时维护在 `docs/current/TESTING.md` 与 `docs/current/WORKLOG.md`。
