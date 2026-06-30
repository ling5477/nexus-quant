# Current Stage

`docs/current/` 是 NexusQuant 的**当前事实入口**：只保留当前控制文档、当前权威基线和必要运行手册。
历史过程证据、治理 review/freeze、旧路径 compatibility stub 已移出 current，归档到 `docs/evidence/` 或 `docs/gates/`（见下方“历史证据位置”）。

当前阶段：**GateK finalized / frozen / tagged（tag：`nq-gatek-freeze`）；GateM = Exchange / MarketData Runtime Readiness；GateM runtime readiness FINALIZED / FROZEN / ACCEPTED / TAGGED（tag：`nq-gatem-freeze`）；GateM-5 Runtime Guarded UI IMPLEMENTED / SMOKE VERIFIED / CLOSED；GateM-6 Operational Readiness IMPLEMENTED / FINAL SMOKE VERIFIED / CLOSED；NQ-NEXT-PHASE-PLAN = PASS / PLAN ONLY / READY TO COMMIT；NQ-GATEN-PUBLIC-MARKETDATA-SANDBOX-PLAN = PASS / PLAN ONLY / READY TO COMMIT；GateN Public MarketData / Exchange Sandbox Planning baseline 已落档；GateN implementation NOT STARTED**。
AI not started；DH runtime not integrated；LIVE disabled；real provider / RealClient / real exchange adapter / real permission probe 未实现。既有 OKX/Binance adapter 含 legacy network-capable code，但未获准作为 real execution provider，且尚未达到 future-real readiness。旧 AI Paper Trading GateM 口径已降级为 future candidate / historical route note，不再是当前 GateM 定义。

GateL-1A..1E 已完成 No-Real exchange/marketdata 文档边界（contract / error model / capability matrix / readiness checklist）。当前进入 **GateM adapter readiness runtime enforcement**：GateM-0 在 `nq-adapter-api` 新增运行时 `AdapterReadinessService` / `DefaultAdapterReadinessService` 与 readiness 模型；GateM-1 进一步把 guard 接入行情订阅与交易动作入口（`ReadinessGuardedMarketDataAdapter` / `ReadinessGuardedTradingAdapter`），运行时强制 OKX / Binance / Noop fail-closed（NOT_READY / NO_REAL / UNKNOWN_REQUIRES_REVIEW）；GateM-2 把 marketdata Bean 经 `ReadinessGuardedAdapterFactory` 接入装配层；GateM-3 已把 `ExchangeAdapterConfiguration` 装配出的 OKX / Binance trading adapter 接入 readiness guard（**IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT**），使 `placeOrder` / `cancelOrder` / `getOrder` / `listOpenOrders` 在当前 no-real / LIVE disabled / not-ready 状态下 fail-closed；GateM-4 补一个更贴近真实调用路径的消费侧 runtime smoke（**IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT**），证明 scheduler 侧 `AdapterBackedTradingVenueGateway` 消费 Spring 装配的 OKX / Binance guarded trading adapter 时仍 fail-closed（降级为 `REMOTE_UNAVAILABLE`、message 脱敏、无 duplicate venue）；GateM-5A 新增只读 `GET /api/adapters/readiness`（**IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT**），供前端展示各 venue × capability 当前 readiness（NOOP/PAPER/SIM→NO_REAL、OKX/BINANCE→NOT_READY，全部 allowed=false、无 READY、脱敏、不触达真实交易所/credential，见 API.md）；GateM-5B 在 NQ Console 新增只读 adapter readiness 面板（路由 `/adapter-readiness`、菜单「适配器就绪」，**IMPLEMENTED / SELF-REVIEWED / READY TO COMMIT**），消费该 API 并 fail-closed 展示（OKX/Binance NOT_READY/不可用/LIVE 未授权、Noop NO_REAL、错误态显示 readiness API unavailable，绝不显示可交易）；GateM-5C 在真实本地 local 后端（18888 + 本地 PostgreSQL）下跑通该面板的真实后端 E2E（**PASS**），证明前端确实消费真实 `GET /api/adapters/readiness`（200、45 条全 fail-closed、无 secret），后端 0 次真实交易所外联。不允许真实交易所、LIVE、真实 credential、AI、DH runtime 或 future-real-ready。详见 STATUS.md / WORKLOG.md / TESTING.md。

GateM-5 Runtime Guarded UI 已收口为 **IMPLEMENTED / SMOKE VERIFIED / CLOSED**：5A Runtime Readiness Overview completed；5B Runtime ↔ MarketData readiness deep link completed；5C Paper / Trading boundary banners completed；5D Dashboard Runtime summary completed；5E Runtime Guarded UI final smoke passed。该收口不新增能力、不授权真实交易所、不启用 LIVE；Paper-ready / DB-fresh / permission probe `SKIPPED` 仍不得写成 real-ready。

GateM-6 Operational Readiness 已收口为 **IMPLEMENTED / FINAL SMOKE VERIFIED / CLOSED**；6A Runtime Operational Readiness Overview completed，6B Operational Readiness Summary API completed，6C Frontend Integration completed，6D Real Backend Smoke completed，6E Local Operational Runbook completed，6F Final Smoke passed。6B 新增只读 `GET /api/runtime/operational-readiness`，仅返回 safe DTO summary，当前全部 `ready=false`，不触达 adapter / permission probe / external exchange / DB / file / HTTP client。6D/6F 已在真实 local backend 下验证 `/api/runtime/operational-readiness` 与 `/runtime/readiness` 闭环；6E runbook 仅服务本地验证，不是 production deploy runbook，不代表 LIVE authorization。GateM-6 closeout 仍不启用 LIVE、不接真实交易所、不做真实 permission probe、不接 AI / DH runtime、不实现 RealClient / real provider。

GateM freeze readiness review 已完成：**PASS / READY FOR GATEM FREEZE REVIEW / READY TO COMMIT**。P0/P1/P2 blocking = 0；允许进入 **NQ-GATEM-FREEZE-REVIEW**。该结论不是 freeze，不代表 production readiness、LIVE authorization 或 real provider ready。

GateM freeze review 已完成：**PASS / FROZEN / ACCEPTED / READY TO COMMIT**。GateM 当前冻结为 no-real runtime readiness baseline；该 freeze review 已由 **NQ-GATEM-RELEASE-TAG-AND-ARCHIVE** 消费。该冻结不代表 production readiness、LIVE authorization、real provider ready、真实 OKX / Binance private adapter implemented、AI started 或 DH runtime integrated。

GateM release tag and archive 已完成：**PASS / COMPLETED / RELEASE TAG PUSHED**。Release tag：`nq-gatem-freeze`；tag object：`f44c62833c5c9f895ee292eef7f5d497b23089cc`；tagged commit：`64194844813bdd3d6541d5a07c576af27b28e5db`。GateM 最终状态为 **FINALIZED / FROZEN / ACCEPTED / TAGGED**；下一阶段规划已完成：**NQ-NEXT-PHASE-PLAN = PASS / PLAN ONLY / READY TO COMMIT**；GateN planning baseline 已完成：**NQ-GATEN-PUBLIC-MARKETDATA-SANDBOX-PLAN = PASS / PLAN ONLY / READY TO COMMIT**。GateN implementation **NOT STARTED**。该 tag 与 GateN planning 均不代表 production readiness、LIVE authorization、real provider ready、真实 OKX / Binance private adapter implemented、AI started 或 DH runtime integrated。

GateM archive Batch 1 已执行：freeze readiness、freeze review、release tag / archive 长证据已归档到 [docs/gates/gate-m/](../gates/gate-m/README.md)。`docs/current` 仅保留当前摘要和 archive pointer；Batch 2-4 的 GateM Runtime UI、Operational Readiness、implementation evidence 暂未移动。

## 当前控制入口

- 当前状态：[STATUS.md](STATUS.md)
- 路线图：[ROADMAP.md](ROADMAP.md)
- 测试：[TESTING.md](TESTING.md)
- 工作日志：[WORKLOG.md](WORKLOG.md)
- 架构：[ARCHITECTURE.md](ARCHITECTURE.md) / [MODULES.md](MODULES.md)
- API / DB：[API.md](API.md) / [DB_SCHEMA.md](DB_SCHEMA.md)
- 运行手册：[RUNBOOK.md](RUNBOOK.md)
- 项目任务流程权威：[NQ_PROJECT_WORKFLOW_AUTHORITY.md](NQ_PROJECT_WORKFLOW_AUTHORITY.md)
- GateM historical archive：[docs/gates/gate-m/](../gates/gate-m/README.md)
- Next phase plan：[NQ_NEXT_PHASE_PLAN.md](NQ_NEXT_PHASE_PLAN.md)
- GateN public marketdata sandbox plan：[NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md](NQ_GATEN_PUBLIC_MARKETDATA_SANDBOX_PLAN.md)
- Post-GateM current archive inventory：[NQ_DOCS_POST_GATEM_CURRENT_ARCHIVE_INVENTORY.md](NQ_DOCS_POST_GATEM_CURRENT_ARCHIVE_INVENTORY.md)
- Post-GateM GateM archive plan review：[NQ_DOCS_POST_GATEM_GATEM_ARCHIVE_PLAN_REVIEW.md](NQ_DOCS_POST_GATEM_GATEM_ARCHIVE_PLAN_REVIEW.md)

## 文档治理权威基线

- [NQ_DOCS_GOVERNANCE_PLAN.md](NQ_DOCS_GOVERNANCE_PLAN.md)
- [NQ_DOCS_AUTHORITY_INDEX.md](NQ_DOCS_AUTHORITY_INDEX.md)
- [NQ_DOCS_EVIDENCE_INDEX.md](NQ_DOCS_EVIDENCE_INDEX.md)
- [NQ_DOCS_MIGRATION_MAP.md](NQ_DOCS_MIGRATION_MAP.md)
- [NQ_DOCS_G1_IMPLEMENTATION.md](NQ_DOCS_G1_IMPLEMENTATION.md)

## CI current authority

- [NQ_CI_BASELINE_PLAN.md](NQ_CI_BASELINE_PLAN.md)
- [NQ_CI_FRONTEND_E2E_BACKEND_PLAN.md](NQ_CI_FRONTEND_E2E_BACKEND_PLAN.md)：真实 local/test 后端 + 前端 adapter readiness E2E readiness smoke 的 CI 基线（5A plan **PASS / PLAN ONLY / NOT IMPLEMENTED**；5A plan review **PASS / ACCEPTED AS BATCH 5B IMPLEMENTATION BASELINE**；5B implementation **IMPLEMENTED**；5C first-run review **FAIL / FIRST-RUN-FIX REQUIRED**；5C-fix implemented；5C re-run review **PASS / RE-RUN GREEN**，run `28035713236` / commit `ba3f4c69`；5D freeze review **PASS / FROZEN / ACCEPTED**）。冻结对象仅为 `frontend-e2e-backend-smoke` 窄口 job + `adapter-readiness-panel-backend-smoke.spec.ts --project=chromium`，目标 job、redaction gate、artifact upload 均 success；artifact 仅 text-only `backend.log` / `health.json` 且内容扫描无 secret-like / real exchange host / outbound error；未上传 Playwright trace/screenshot/report/video；不冻结 full E2E、real provider、real permission probe、LIVE、AI 或 DH runtime。
- [NQ_CI_SECURITY_GUARD_PLAN.md](NQ_CI_SECURITY_GUARD_PLAN.md)
- [NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md](NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md)：Batch 5B-ENV env/secret/no-outbound guard（plan ACCEPTED；implementation **FROZEN / ACCEPTED**，freeze evidence run `27876451289`；current Batch 5B-SMOKE **FROZEN / ACCEPTED**，见 `NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md`）。
- [NQ_CI_SECURITY_BATCH_5B_ENV_PLAN_REVIEW.md](NQ_CI_SECURITY_BATCH_5B_ENV_PLAN_REVIEW.md)：Batch 5B-ENV plan review（PASS / ACCEPTED）。
- [NQ_CI_SECURITY_BATCH_5B_ENV_FIRST_RUN_REVIEW.md](NQ_CI_SECURITY_BATCH_5B_ENV_FIRST_RUN_REVIEW.md)：Batch 5B-ENV first-run review（first run RED → fix-forward → fix rerun GREEN → FROZEN，见 §12）。
- [NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md](NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md)：Batch 5B-ENV freeze 卷宗（**FROZEN / ACCEPTED**，evidence run `27876451289` / headSha `8ba140d9`；freeze-time smoke remained blocked；current Batch 5B-SMOKE **FROZEN / ACCEPTED**，见 `NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md`）。
- [NQ_CI_SECURITY_BATCH_5B_SMOKE_PREFLIGHT_PLAN.md](NQ_CI_SECURITY_BATCH_5B_SMOKE_PREFLIGHT_PLAN.md)：Batch 5B-SMOKE preflight plan（**REVIEWED / ACCEPTED**；该 plan 编写时 smoke 为 PLANNED / NOT STARTED，current Batch 5B-SMOKE **FROZEN / ACCEPTED**；no-real / no-outbound / no-secret-read）。
- [NQ_CI_SECURITY_BATCH_5B_SMOKE_IMPLEMENTATION_PLAN.md](NQ_CI_SECURITY_BATCH_5B_SMOKE_IMPLEMENTATION_PLAN.md)：Batch 5B-SMOKE implementation plan（**REVIEWED / ACCEPTED**）；Batch 5B-SMOKE = **FROZEN / ACCEPTED**（ci.yml `ci-security-smoke` job，复用 EnvSafety / no-outbound / NoReal 最小 smoke；first run evidence PASS（run `27903497008`，9 jobs success）；freeze **FROZEN / ACCEPTED**，卷宗 `NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md`）；Batch 5B = **CLOSED / ACCEPTED**。
- [NQ_CI_SECURITY_FINAL_BASELINE_REVIEW.md](NQ_CI_SECURITY_FINAL_BASELINE_REVIEW.md)：GateK CI/security final baseline review（**PASS / READY FOR FINAL FREEZE**；Batch 5B = CLOSED / ACCEPTED；final freeze 见 `NQ_CI_SECURITY_FINAL_FREEZE.md`）。
- [NQ_CI_SECURITY_FINAL_FREEZE.md](NQ_CI_SECURITY_FINAL_FREEZE.md)：GateK CI/security final freeze 卷宗（**GateK CI/security = FROZEN / ACCEPTED**；Batch 1–5 全部 FROZEN/ACCEPTED 或 CLOSED；evidence run 27903497008 / 27876451289 / 27904207910 success）。
- CI baseline 导航索引：`docs/baselines/CI_BASELINE_INDEX.md`

## GateK / 规划与其他当前控制文档

- [GATEK_PLAN.md](GATEK_PLAN.md)：GateK planning-only；不代表 GateK implementation started。
- [NQ_GATEK_PAPER_EXECUTION_INTELLIGENCE_PLAN.md](NQ_GATEK_PAPER_EXECUTION_INTELLIGENCE_PLAN.md)：GateK Paper Execution Intelligence 分批规划（K1 执行诊断后端 → K2 诊断 UI → K3 策略评估 → K4 每日复盘 → K5 可选页面拆分；**PLAN / PENDING IMPLEMENTATION**；不改 backend/frontend/migration，不接 AI/LIVE）。
- [NQ_GATEK_PAPER_TRADING_PAGE_SPLIT_PLAN.md](NQ_GATEK_PAPER_TRADING_PAGE_SPLIT_PLAN.md)：GateK K5 Paper Trading 页面拆分规划（推荐方案 D：单入口 `/paper-trading` + 子路由 `runs/portfolio/diagnostics/reviews`，旧入口 index redirect 兼容；分批 K5-A 抽组件 → K5-B 加子路由 → K5-C 逐群迁移 → K5-D 拆 E2E → K5-E 瘦身旧页；**PLAN ONLY / PENDING IMPLEMENTATION**；不改 backend/frontend/migration/测试，不接 AI/DH/LIVE）。
- [NQ_GATEK_ARCHITECTURE_FREEZE.md](NQ_GATEK_ARCHITECTURE_FREEZE.md)
- [NQ_GATEK_CI_SECURITY_CONTRACT.md](NQ_GATEK_CI_SECURITY_CONTRACT.md)
- [NQ_GATEK_ARCHIVE_AND_HANDOVER.md](NQ_GATEK_ARCHIVE_AND_HANDOVER.md)
- [GATEL_PLAN.md](GATEL_PLAN.md)：GateL planning baseline（**PASS / ACCEPTED**；GateL implementation NOT STARTED）。GateL = 真实交易所接入前的 No-Real 交易适配器 / 市场数据 / permission probe / paper-live execution 边界就绪规划（GateL-1..5 + real exchange readiness checklist）。canonical（2026-06-22 经 `NQ-GATEL-CANONICAL-ROUTE-SYNC` 裁决）：**GateL = No-Real exchange/marketdata readiness**；旧口径「GateL = AI Paper Trading」作废；后续曾将 AI Paper Trading 记为 GateM，但该历史口径已被 `NQ-GATEM-STATE-ROUTE-RECONCILIATION` superseded。当前 GateM = Exchange / MarketData Runtime Readiness。不接真实交易所、不读真实凭证、不外联、不启用 LIVE、不接 AI / DH runtime。
- [GATEL_1_EXCHANGE_ADAPTER_CONTRACT_REVIEW.md](GATEL_1_EXCHANGE_ADAPTER_CONTRACT_REVIEW.md)：GateL-1 adapter contract review（**CONDITIONAL PASS / DOCS-CONTRACT ONLY**）。确认现有 OKX/Binance 为 legacy network-capable code，不是纯 NoReal stub；登记 Binance 默认 endpoint、进程 credential、`rawPayload`、stub success 语义四项 P1。GateL implementation 仍 NOT STARTED，未授权真实交易所。
- [GATEL_1_EXCHANGE_ADAPTER_CONTRACT_FREEZE_REVIEW.md](GATEL_1_EXCHANGE_ADAPTER_CONTRACT_FREEZE_REVIEW.md)：GateL-1A review fact baseline freeze（**PASS / FROZEN / ACCEPTED**）。冻结 GateL-1 review 事实、四项 P1、四项 P2 与后续顺序；P1/P2 仍 OPEN，adapter readiness **NOT READY / NOT FROZEN**。下一步 GateL-1B No-Real hardening plan；不授权真实交易所、LIVE、AI 或 DH runtime。
- [GATEL_1B_NO_REAL_HARDENING_PLAN.md](GATEL_1B_NO_REAL_HARDENING_PLAN.md)：GateL-1B No-Real hardening planning baseline（**PASS / PLAN READY FOR REVIEW；NOT IMPLEMENTED**）。规划 A Binance endpoint、B runtime credential source、C rawPayload、D Noop status 四个独立切片；P1 仍 OPEN，adapter readiness NOT READY。下一步仅 plan review。
- [GATEL_1B_NO_REAL_HARDENING_PLAN_REVIEW.md](GATEL_1B_NO_REAL_HARDENING_PLAN_REVIEW.md)：GateL-1B plan review（**PASS / ACCEPTED AS PLAN REVIEW BASELINE；NOT IMPLEMENTED**）。确认 A/B/C/D 拆分、测试、验收与回滚可冻结；A 限定 sentinel-only，B 限定 process credential removal。四项 P1 仍 OPEN，adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED。下一步仅 plan freeze。
- [GATEL_1B_NO_REAL_HARDENING_PLAN_FREEZE_REVIEW.md](GATEL_1B_NO_REAL_HARDENING_PLAN_FREEZE_REVIEW.md)：GateL-1B plan baseline freeze（**PASS / FROZEN / ACCEPTED；IMPLEMENTATION NOT STARTED**）。冻结 plan + review 的 A/B/C/D 拆分、强制顺序和安全约束；P1 仍 OPEN，adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED。GateL-1B-A（Binance endpoint sentinel/no-outbound）已 **CLOSED / ACCEPTED / FROZEN**（`NQ-GATEL-1B-A-IMPL-FREEZE`，commit `04ddb774`，详见 `GATEL_1B_A_IMPL_FREEZE_REVIEW.md`）；下一步 `NQ-GATEL-1B-B-IMPL`，不是 real adapter。
- [GATEL_1B_A_IMPL_FREEZE_REVIEW.md](GATEL_1B_A_IMPL_FREEZE_REVIEW.md)：GateL-1B-A implementation freeze（**PASS / FROZEN / ACCEPTED；P1-A CLOSED / ACCEPTED**）。冻结 commit `04ddb774` 的 Binance 默认 endpoint no-real sentinel / no-outbound hardening 实现与测试；P1-B/C/D 仍 OPEN，adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED，不代表允许真实 Binance 接入。GateL-1B-B（OKX/Binance runtime credential source）已 **CLOSED / ACCEPTED / FROZEN**（`NQ-GATEL-1B-B-IMPL-FREEZE`，commit `ad7f58b0`，详见 `GATEL_1B_B_IMPL_FREEZE_REVIEW.md`）；下一步 `NQ-GATEL-1B-C-IMPL`。
- [GATEL_1B_B_IMPL_FREEZE_REVIEW.md](GATEL_1B_B_IMPL_FREEZE_REVIEW.md)：GateL-1B-B implementation freeze（**PASS / FROZEN / ACCEPTED；P1-B CLOSED / ACCEPTED**）。冻结 commit `ad7f58b0` 的 OKX/Binance runtime credential source hardening（默认不再读取进程环境 credential、`*.unconfigured()` 占位、private op 网络前 fail-closed）；P1-A 仍 CLOSED，P1-C/P1-D 仍 OPEN，adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED，未实现真实 credential bridge，不代表允许真实 OKX/Binance 接入。下一步 `NQ-GATEL-1B-C-IMPL`。
- `NQ-GATEL-1B-C-IMPL`（记录于 [GATEL_1B_NO_REAL_HARDENING_PLAN.md](GATEL_1B_NO_REAL_HARDENING_PLAN.md) §19）：GateL-1B-C producer suppression（**PASS / IMPLEMENTED；PENDING REVIEW**）。OKX/Binance `AdapterOrderAck` / `AdapterOrderSnapshot` producer 固定 `rawPayload=null`，不再传播 provider raw response；`rawPayload` 字段删除未做，另起兼容性任务；P1-D 仍 OPEN，adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED，不代表允许真实 OKX/Binance 接入。下一步 `NQ-GATEL-1B-C-IMPL-REVIEW`。
- [GATEL_1B_C_IMPL_FREEZE_REVIEW.md](GATEL_1B_C_IMPL_FREEZE_REVIEW.md)：GateL-1B-C implementation freeze（**PASS / FROZEN / ACCEPTED；P1-C producer suppression CLOSED / ACCEPTED**）。冻结 commit `316497ad` 的 OKX/Binance order ack/snapshot rawPayload producer suppression；`rawPayload` 字段删除未做，P1-D 仍 OPEN，adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED，不代表允许真实 OKX/Binance 接入。下一步 `NQ-GATEL-1B-D-IMPL`。
- `NQ-GATEL-1B-D-IMPL`（记录于 [GATEL_1B_NO_REAL_HARDENING_PLAN.md](GATEL_1B_NO_REAL_HARDENING_PLAN.md) §21）：GateL-1B-D Noop marketdata status hardening（**PASS / IMPLEMENTED；后续已 freeze-close**）。`NoopMarketDataAdapter` bars / trades / order-book 订阅统一返回 `subscribed=false`、`NO_REAL_DISABLED`、`FATAL_FAILURE`、`retryable=false`；P1-A/P1-B/P1-C producer suppression 仍 CLOSED / ACCEPTED，P1-C rawPayload 字段删除仍 NOT DONE，adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED，不代表允许真实 marketdata / real adapter / LIVE。冻结结论见 `GATEL_1B_D_IMPL_FREEZE_REVIEW.md`。
- [GATEL_1B_D_IMPL_FREEZE_REVIEW.md](GATEL_1B_D_IMPL_FREEZE_REVIEW.md)：GateL-1B-D implementation freeze（**PASS / FROZEN / ACCEPTED；P1-D CLOSED / ACCEPTED**）。冻结 commit `7e442eb7` 的 NoopMarketDataAdapter no-real status hardening；P1-A/P1-B/P1-C producer suppression 仍 CLOSED / ACCEPTED，P1-C rawPayload 字段删除仍 NOT DONE / SEPARATE COMPATIBILITY TASK，adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED，GateL-1B overall hardening NOT FROZEN；不代表允许真实 marketdata、real adapter、LIVE 或 future-real-ready。下一步 `NQ-GATEL-1B-OVERALL-HARDENING-FREEZE-REVIEW`。
- [GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md](GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md)：GateL-1B overall No-Real hardening freeze（**PASS / FROZEN / ACCEPTED**）。冻结 A/B/C/D 组合证据：P1-A / P1-B / P1-C producer suppression / P1-D 均 CLOSED / ACCEPTED；P1-C rawPayload 字段删除仍 NOT DONE / SEPARATE COMPATIBILITY TASK；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；不授权真实交易所、real adapter、LIVE、真实 credential、AI 或 DH runtime。下一步 `NQ-GATEL-1C-CAPABILITY-MATRIX-CONTRACT`。
- [GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md](GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md)：GateL-1C capability matrix contract（**PASS / FROZEN / ACCEPTED**）。冻结 `CLOSED_NO_REAL` / `DISABLED_SENTINEL` / `NO_REAL_DISABLED` / `STUB_ONLY` / `NOT_IMPLEMENTED` / `FUTURE_REAL_REQUIRES_GATE` / `FORBIDDEN_IN_GATEL` / `UNKNOWN_REQUIRES_REVIEW` 状态枚举与 Noop / OKX / Binance / future-real / permission probe / marketdata placeholder 能力矩阵；OKX/Binance existing adapters 仍不是 future-real-ready，也不等于真实交易所授权；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；LIVE、真实 credential、AI、DH runtime 仍不允许。下一步 `NQ-GATEL-1D-ERROR-MODEL-CONTRACT`。
- [GATEL_1C_CAPABILITY_MATRIX_CONTRACT_REVIEW.md](GATEL_1C_CAPABILITY_MATRIX_CONTRACT_REVIEW.md)：GateL-1C capability matrix contract review（**PASS / REVIEW ACCEPTED**）。只读复核 enum、adapter/venue、trading、marketdata、credential/endpoint/permission、forbidden interpretation，确认合同未把 no-real / disabled / stub 写成真实交易能力；不授权真实交易所、LIVE、真实 credential、AI、DH runtime 或 adapter future-real-ready。下一步 `NQ-GATEL-1D-ERROR-MODEL-CONTRACT`。
- [GATEL_1C_CAPABILITY_MATRIX_CONTRACT_FREEZE_REVIEW.md](GATEL_1C_CAPABILITY_MATRIX_CONTRACT_FREEZE_REVIEW.md)：GateL-1C capability matrix contract freeze（**PASS / FROZEN / ACCEPTED**）。冻结 capability matrix contract + review 结论为后续 GateL-1D / GateL-1E 的能力边界基线；matrix 只能表达状态，不能启用能力；不授权真实交易所、LIVE、真实 credential、AI、DH runtime 或 adapter future-real-ready。下一步 `NQ-GATEL-1D-ERROR-MODEL-CONTRACT`。
- [GATEL_1D_ERROR_MODEL_CONTRACT.md](GATEL_1D_ERROR_MODEL_CONTRACT.md)：GateL-1D adapter error model contract（**PASS / FROZEN / ACCEPTED；contract-only**）。冻结 error status enum（`NO_REAL_DISABLED` / `NETWORK_DISABLED` / `CREDENTIALS_MISSING` / `AUTH_FAILED` / `PERMISSION_DENIED` / `IP_NOT_ALLOWED` / `RATE_LIMITED` / `VENUE_UNAVAILABLE` / `INVALID_SYMBOL` / `UNSUPPORTED_OPERATION` / `RISK_REJECTED` / `ORDER_STATE_REJECTED` / `LEDGER_REJECTED` / `RAW_PAYLOAD_SUPPRESSED` / `UNKNOWN_REQUIRES_REVIEW`）与 retry 语义（retryable=false 终态集合；conditional 仅受控 RATE_LIMITED / VENUE_UNAVAILABLE；UNKNOWN fail-closed），并映射既有 `AdapterResultCategory`。统一 OKX / Binance / Noop / permission probe / future-real placeholder 的分类、fail-closed 与安全解释边界；`NO_REAL_DISABLED` 非成功、`CREDENTIALS_MISSING` 不 fallback、RISK/ORDER/LEDGER 由 NQ core 拥有、`RAW_PAYLOAD_SUPPRESSED` 是安全边界。真实交易所错误处理须另起 Gate；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；LIVE、真实 credential、AI、DH runtime、adapter future-real-ready 仍不允许。下一步 `NQ-GATEL-1D-ERROR-MODEL-CONTRACT-REVIEW`。
- [GATEL_1D_ERROR_MODEL_CONTRACT_REVIEW.md](GATEL_1D_ERROR_MODEL_CONTRACT_REVIEW.md)：GateL-1D adapter error model contract review（**PASS / REVIEW ACCEPTED；contract-only**）。只读复核 error status enum（15 项）、合同层 status ↔ 既有 `AdapterResultCategory`（9 类）映射、retry 语义、adapter/venue 与 trading/marketdata/credential/permission 路径矩阵、fail-closed 与禁止解释；`git grep` 校验 OKX `50035` / Binance `-2013`·`-2011` / Noop `NO_REAL_DISABLED` / `*_CREDENTIALS_MISSING` 源码事实与合同一致。P0/P1=0；P2 为既知 follow-up（细粒度 status 在既有 category 折叠为 AUTH_FAILURE、rawPayload field deletion 独立任务、真实 backoff/circuit breaker policy 属 future-real），不阻断冻结。adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；真实交易所、LIVE、真实 credential、AI、DH runtime、adapter future-real-ready 仍不允许。下一步 `NQ-GATEL-1D-ERROR-MODEL-CONTRACT-FREEZE`。
- [GATEL_1D_ERROR_MODEL_CONTRACT_FREEZE_REVIEW.md](GATEL_1D_ERROR_MODEL_CONTRACT_FREEZE_REVIEW.md)：GateL-1D adapter error model contract freeze（**PASS / FROZEN / ACCEPTED**）。冻结 error model contract + review 为 GateL-1E readiness checklist refinement 与 future-real 实现 Gate 的错误分类、retry、fail-closed、安全解释基线；error status enum（15 项）、合同层 status ↔ 既有 `AdapterResultCategory`（9 类）映射、retry 语义（retryable=false 终态集合；conditional 仅受控 RATE_LIMITED / VENUE_UNAVAILABLE；UNKNOWN fail-closed）与路径矩阵全部固化。`git grep` 复核 Noop `NO_REAL_DISABLED` / OKX·Binance `disabled://` sentinel / `*.unconfigured()` credential 冻结不变量仍成立。冻结不启用能力；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；真实交易所、LIVE、真实 credential、AI、DH runtime、adapter future-real-ready 仍不允许。下一步 `NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT`。
- [GATEL_1E_READINESS_CHECKLIST_REFINEMENT.md](GATEL_1E_READINESS_CHECKLIST_REFINEMENT.md)：GateL-1E future-real readiness checklist refinement（**PASS / CHECKLIST CREATED / PENDING REVIEW；checklist-only**）。细化未来若进入真实交易所接入 Gate 必须满足的准入门槛（代码实现 / 安全 / credential / network·no-outbound / adapter 实现 / permission probe / marketdata / trading execution / risk·order·ledger·audit / testing·CI / rollout·rollback·incident / 用户显式授权 12 类）；锚定 `EnvSafetyValidator` / `NoOutboundExchangeGuard` / `NoRealExchangeCredentialPermissionProbePort` / `KillSwitchService` / `RiskGate` / `OrderStateMachine` / `AuditLogRepository` / `JdbcLedgerPostingRepository` 既有基线，引用 GateL-1C/1D 冻结合同。checklist 只定义准入门槛，不授权真实交易所、不启用 LIVE、不实现 real adapter；checklist 项“满足”不构成授权。adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；real exchange access / LIVE / real credential / AI / DH runtime / future-real-ready 全部 NO。下一步 `NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT-REVIEW`。
- [GATEL_1E_READINESS_CHECKLIST_REFINEMENT_REVIEW.md](GATEL_1E_READINESS_CHECKLIST_REFINEMENT_REVIEW.md)：GateL-1E future-real readiness checklist refinement review（**PASS / REVIEW ACCEPTED；checklist-only**）。只读复核非授权声明与 12 类准入门类（security / credential / network·no-outbound / adapter / permission probe / marketdata / trading execution / risk·order·ledger·audit / testing·CI / rollout·rollback·incident / 用户授权）；`git grep` 校验安全基线组件、`NoRealExchangeCredentialPermissionProbePort` = `REAL_EXCHANGE_PROBE_DISABLED`/SKIPPED、OKX/Binance permission probe forbidden endpoint 源码事实与 checklist 一致。P0/P1=0；P2 为既知 follow-up（真实 backoff/circuit breaker/kill switch policy、credential governance bridge、real probe 属 future-real；rawPayload field deletion 独立任务；具体阈值待 future-real 配置化），不阻断冻结。checklist 只定义准入门槛，不构成授权；未放宽 GateL-1B/1C/1D 任一冻结边界。adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；real exchange access / LIVE / real credential / AI / DH runtime / future-real-ready 全部 NO。下一步 `NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT-FREEZE`。
- [NQ_GATEK_POST_FREEZE_HANDOFF_PLAN.md](NQ_GATEK_POST_FREEZE_HANDOFF_PLAN.md)：GateK post-freeze handoff（CI/security + OKX no-outbound + endpoint defense closure；**PASS / READY FOR NEXT PHASE**；**NEXT PHASE = READY TO PLAN**；evidence matrix + frozen boundaries + regression rules + 下一阶段入口候选 + optional backlog）。
- [GATEK_ARCHITECTURE_BASELINE_REVIEW.md](GATEK_ARCHITECTURE_BASELINE_REVIEW.md)：GateK architecture baseline review（P0/P1=0，ACCEPTED WITH P2 FOLLOW-UP）。
- [NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md](NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md)：OKX bootstrap / test isolation / no-outbound 边界复审（§13 post-CI-security freeze 复审 = PASS / READY FOR FREEZE；P0/P1=0，P2=1 非阻断纵深防御 follow-up，P3=1 命名差异）。
- [NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md](NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md)：OKX bootstrap / test isolation / no-outbound 边界冻结卷宗（**FROZEN / ACCEPTED**，冻结 review commit `0b9c0b20`；原 P2 已经 post-freeze addendum 关闭，见下；regression boundary 见卷宗 §11）。
- [NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_PLAN.md](NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_PLAN.md)：OKX runtime config 默认 endpoint 纵深防御计划与实现（Path A，sentinel `disabled://`；**FROZEN / ACCEPTED**）。
- [NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_ADDENDUM.md](NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_ADDENDUM.md)：上述纵深防御的 post-freeze addendum（**FROZEN / ACCEPTED**；fix commit `c749cef7`，CI run `27926903155` / 9 jobs success；**P2 OkxRuntimeConfig default real endpoint defense = CLOSED / ACCEPTED**）。
- [FRONTEND_DESIGN_SYSTEM.md](FRONTEND_DESIGN_SYSTEM.md)：前端设计系统当前事实。
- [BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md](BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md)：回测权益/回撤曲线后端契约 planning（planning only，未实现）。
- [NQ_GATEM_2D_MARKETDATA_SOURCE_HEALTH_PLAN.md](NQ_GATEM_2D_MARKETDATA_SOURCE_HEALTH_PLAN.md)：GateM-2D MarketData source health / freshness / gap / ingestion readiness 后端聚合 planning（**PLAN ONLY / NOT IMPLEMENTED**）。推荐后续以 no-migration MVP 新增只读 `GET /api/marketdata/readiness`，仅聚合现有本地 DB facts；不新增 API 实现、不新增 migration、不改前端、不接真实交易所、不启用 LIVE / AI / DH runtime。
- GateM-2E MarketData readiness backend MVP：新增只读 `GET /api/marketdata/readiness`（**IMPLEMENTED / READY FOR REVIEW**），仅基于本地 `marketdata_bars` 与 `marketdata_ingestion_jobs/runs` 聚合 `NO_MIGRATION_MVP` readiness summary；不新增 migration、不改 frontend、不调用 adapter / 外部交易所、不读取 credential、不启用 LIVE / AI / DH runtime。该 MVP 不代表 source health 全量完成或持久 source policy 已实现。
- [frontend/NQ_GATEM_5_RUNTIME_GUARDED_UI_PLAN.md](frontend/NQ_GATEM_5_RUNTIME_GUARDED_UI_PLAN.md)：GateM-5 Runtime Guarded UI planning（historical plan；当前 GateM-5 Runtime Guarded UI 已 **IMPLEMENTED / SMOKE VERIFIED / CLOSED**）。Runtime Guarded UI 已通过 5A Runtime Readiness Overview、5B Runtime ↔ MarketData readiness deep link、5C Paper / Trading boundary banners、5D Dashboard Runtime summary、5E final smoke 收口；后续 GateM-6 Operational Readiness 已完成 6A/6B/6C/6D/6E/6F，并收口为 **IMPLEMENTED / FINAL SMOKE VERIFIED / CLOSED**。不触发真实交易所、credential、采集、adapter call、LIVE、AI 或 DH runtime；Paper-ready / DB-fresh / permission probe `SKIPPED` 不构成 real-ready。
- [NQ_GATEM_6_OPERATIONAL_READINESS_PLAN.md](NQ_GATEM_6_OPERATIONAL_READINESS_PLAN.md)：GateM-6 Operational Readiness baseline（**IMPLEMENTED / FINAL SMOKE VERIFIED / CLOSED**）。6A overview、6B safe summary API、6C frontend integration、6D real backend smoke、6E local runbook、6F final smoke 已完成；后续 freeze readiness review 与 freeze review 已完成。6B 只读 `GET /api/runtime/operational-readiness` safe DTO summary；6D/6F 真实 local backend smoke 已验证 API/UI 闭环；6E 本地 runbook 仅用于 local validation；不新增 migration、workflow、生产 deploy runbook；不改 actuator / adapter readiness / MarketData readiness / Trading / Paper Trading；不启用 LIVE / AI / DH runtime / real provider。
- GateM freeze / release historical evidence 已归档到 [docs/gates/gate-m/freeze/](../gates/gate-m/freeze/)：GateM stage-level freeze readiness review、GateM freeze review、GateM release tag / archive record。当前事实只保留摘要：GateM = **FINALIZED / FROZEN / ACCEPTED / TAGGED**，release tag = `nq-gatem-freeze`，baseline = no-real runtime readiness；不授权 LIVE、real provider、RealClient、真实 private trading、real permission probe、AI 或 DH runtime。
- [NQ_GATEM_6_LOCAL_OPERATIONAL_RUNBOOK.md](NQ_GATEM_6_LOCAL_OPERATIONAL_RUNBOOK.md)：GateM-6E local operational readiness runbook（**PASS / DOCS ONLY / READY TO COMMIT**）。记录本地启动后端、检查 `/actuator/health`、认证调用 `GET /api/runtime/operational-readiness`、访问 `/runtime/readiness`、禁止动作清单、停止后端并确认 health unreachable；仅服务本地验证，不是 production deploy，不代表 LIVE authorization。
- [frontend/NQ_GATEM_2H_MARKETDATA_POSITIVE_BARS_FIXTURE_PLAN.md](frontend/NQ_GATEM_2H_MARKETDATA_POSITIVE_BARS_FIXTURE_PLAN.md)：GateM-2H MarketData positive bars fixture planning（**PLAN ONLY / NOT IMPLEMENTED**）。规划后续用 test-only、显式、fake-source bars fixture 覆盖 real-backend positive branch；不新增 migration、不新增 production API、不修改 backend/frontend 代码、不接真实交易所、不启用 LIVE / AI / DH runtime。
- DB / Credential 治理：`DB_SCHEMA_GOVERNANCE_PLAN.md`、`DB_SCHEMA_GOVERNANCE_REVIEW.md`、`CREDENTIAL_*`。
- NQ-DH Integration-0（contract / mock / docs，未实现 runtime 集成）：`NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`、`NQ_DH_INTEGRATION0_SECURITY_POLICY.md`、`NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`、`NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`。
- NQ-DH timestamp format alignment：**CLOSED / ACCEPTED（2026-06-28）**。Canonical timestamp = RFC3339 / ISO-8601 UTC `Z`；DH/NQ INT0 均拒绝 epoch seconds / epoch milliseconds / 数字时区偏移；±300s replay window 不变。CLOSED 不授权 Integration-1 runtime；DH not integrated；Runtime integration NOT STARTED；LIVE disabled。

## Codex Workflow 入口

- `../../AGENTS.md`：仓库级 Codex 开发指引、NQ/DH 边界与执行纪律。
- `NQ_DH_CODEX_PLUGIN_WORKFLOW.md`、`NQ_DH_WORKFLOW_ROUTER_SKILL.md`、`NQ_DH_CODEX_TASK_TEMPLATES.md`、`CODEX_PROJECT_INSTRUCTIONS.md`。

## 历史证据位置

当前 current 不再堆放历史过程证据；按主题分别归档：

- GateJ canonical records：`docs/gates/gate-j/`
- GateM historical archive：`docs/gates/gate-m/`（Batch 1 已归档 freeze / release / closeout evidence；Batch 2-4 暂未移动）
- 早期 Gate 历史卷宗：`docs/gates/gate-h/`、`docs/gates/gate-i/`、`docs/gates/gate-a..g/`
- CI historical evidence：`docs/evidence/ci/`（导航 `docs/evidence/ci/README.md`）
- Documentation governance evidence（G1～G6 plan/review/freeze/implementation/final freeze）：`docs/evidence/governance/`（导航 `docs/evidence/governance/README.md`）
- Compatibility stubs（旧 current 路径兼容 stub 归档副本）：`docs/evidence/compatibility/`
  - GateJ：`docs/evidence/compatibility/gatej-current-stubs/`
  - CI：`docs/evidence/compatibility/ci-current-stubs/`

## 当前不是

- 当前不是 AI 自动交易阶段；不允许 AI 直接下单；不允许真实 LIVE 下单。
- AI 接入必须等 Paper Trading 稳定后再进入（最早 GateK 之后单独评审）。
- 当前不是 GateK 产品 / runtime 实现阶段；CI mainline 完成不代表 AI、DH runtime、LIVE 或真实交易所扩展启动。
- 当前不代表 UI/UX 专业化已完成，也不应描述为面向公开用户的生产就绪。

## 项目路线

```text
DOC-CLEAN / BASELINE-FIX completed
  ↓
GateH：交易工作台 + 历史行情 + dataset 绑定 completed
  ↓
GateI：虚拟币量化 V1 完整闭环 completed
  ↓
GateJ：Paper Trading 稳定运行 completed
  ↓
GateK：规划 / 架构 / 产品化 / 部署化 / 可观测性 / 安全边界收口 completed
  ↓
GateL：No-Real Exchange / MarketData Readiness completed
  ↓
GateM：Exchange / MarketData Runtime Readiness（FINALIZED / FROZEN / ACCEPTED / TAGGED；tag：`nq-gatem-freeze`；no-real runtime readiness baseline）
  ↓
GateN：Public MarketData / Exchange Sandbox Planning（PASS / PLAN ONLY / READY TO COMMIT；implementation NOT STARTED）
  ↓
Future AI Paper Trading candidate（NOT CURRENT GATEM；AI/DH runtime boundaries must be separately planned）
  ↓
Future AI small-funds LIVE candidate（DEFERRED；requires separate AI / DH / LIVE authorization planning）
  ↓
GateO：美股适配
  ↓
GateP：A 股适配
```
