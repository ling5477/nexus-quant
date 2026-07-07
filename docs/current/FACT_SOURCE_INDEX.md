# Current Fact Source Index

本文是 NexusQuant 当前事实源索引。用途是给 GateR planning、GateR-2..8 Shadow Run closed-loop implementation、历史 Gate archive、LIVE / AI / DH / Integration-1 / real provider 边界判断提供统一入口，避免把已冻结证据、mock/test-support、readiness、preview、local fact、read-only API、frontend replay 或 comparison 误写成 runtime 授权。

## 1. 当前事实源优先级

当事实冲突时，按以下顺序解释当前状态：

1. 当前代码和实际验证结果。
2. [STATUS.md](STATUS.md)：当前项目状态。
3. [README.md](README.md)：current 入口和 archive pointer。
4. [ROADMAP.md](ROADMAP.md)：当前路线与下一阶段边界。
5. [GATER_PLAN.md](GATER_PLAN.md)：GateR Shadow Run operationalization planning 和 GateR-2..8 implementation 指针；GateR-8 已完成并 push，下一步是 GateR freeze closeout。
6. [API.md](API.md)：已实现 HTTP API 当前事实；GateR-6 / GateR-8 Shadow Run API 只包含 read-only GET endpoint，不包含写接口。
7. [TESTING.md](TESTING.md)：当前验证记录和未运行说明；GateR-2..8 的 Maven、Playwright、forbidden-scope、latest CI green 证据以此为当前验证入口。
8. [WORKLOG.md](WORKLOG.md)：当前任务记录；GateR-2..8 实现范围、commit、CI 和边界以此为工作记录入口。
9. [DB_SCHEMA.md](DB_SCHEMA.md)：已落地 Flyway schema 当前事实；GateR-2 的 `V32__gate_r_shadow_run_fact_model.sql` 和 4 张 Shadow Run local fact 表以此为当前 schema 入口。
10. [GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md](GATER_1_SHADOW_RUN_DATA_MODEL_MIGRATION_PLAN_REVIEW.md)：GateR-1 Shadow Run data model / migration plan review；只表示 migration 方案已就绪，不代表 review 本身创建表。
11. [../gates/gate-q/README.md](../gates/gate-q/README.md)：GateQ 历史归档入口。
12. [../gates/gate-p/README.md](../gates/gate-p/README.md)：GateP 历史归档入口。
13. [../gates/gate-o/README.md](../gates/gate-o/README.md)：GateO 历史归档入口。
14. [../archive/current-cleanup/post-gateq/README.md](../archive/current-cleanup/post-gateq/README.md)：本轮 post-GateQ current cleanup 审计和移动索引。

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
- 不得把 GateR readiness 写成 trading authorization 或 LIVE ready。
- 不得把 GateR-1 migration plan review 本身写成 migration implemented；migration implemented 的当前事实只来自 GateR-2 `V32__gate_r_shadow_run_fact_model.sql`。
- 不得把 GateR-2..8 已完成误写成未实现、trading authorization 或 LIVE ready。
- Shadow Run read-only API / DTO 已实现；仍不得写成 write API、runner start endpoint、execute endpoint 或 trading endpoint。
- Shadow Run frontend list / detail / replay view 已实现；仍不得写成带 start / stop / execute / rerun / approve / trade 执行按钮的页面。
- Shadow Run runner skeleton 已实现；仍不得写成 scheduler、后台 runner、runner started、shadow live trading enabled 或 live-ready。
- 不得把 LIVE 写成 ready / enabled。
- 不得把 AI 写成 started。
- 不得把 DH runtime 写成 integrated。
- 不得把 Integration-1 mock/test-support 写成 runtime started。
- 不得把 RealClient、real provider、private trading adapter 或 real permission probe 写成 implemented。
- 不得把 public marketdata readiness、Data Quality、permission readiness、risk preflight、preview、comparison、binding preview 或 archive closeout 写成 trading authorization。
- 不得把 Python offline foundation 写成 ML ready 或 live execution ready。
