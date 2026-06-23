# Current Stage

`docs/current/` 是 NexusQuant 的**当前事实入口**：只保留当前控制文档、当前权威基线和必要运行手册。
历史过程证据、治理 review/freeze、旧路径 compatibility stub 已移出 current，归档到 `docs/evidence/`（见下方“历史证据位置”）。

当前阶段：**GateJ completed；Next: GateK-PLAN；NQ GateK CI mainline COMPLETED / ACCEPTED**。
AI not started；DH not integrated；LIVE disabled；real provider / RealClient / real permission probe 未实现。既有 OKX/Binance adapter 含 legacy network-capable code，但未获准作为 real execution provider，且尚未达到 future-real readiness。

## 当前控制入口

- 当前状态：[STATUS.md](STATUS.md)
- 路线图：[ROADMAP.md](ROADMAP.md)
- 测试：[TESTING.md](TESTING.md)
- 工作日志：[WORKLOG.md](WORKLOG.md)
- 架构：[ARCHITECTURE.md](ARCHITECTURE.md) / [MODULES.md](MODULES.md)
- API / DB：[API.md](API.md) / [DB_SCHEMA.md](DB_SCHEMA.md)
- 运行手册：[RUNBOOK.md](RUNBOOK.md)

## 文档治理权威基线

- [NQ_DOCS_GOVERNANCE_PLAN.md](NQ_DOCS_GOVERNANCE_PLAN.md)
- [NQ_DOCS_AUTHORITY_INDEX.md](NQ_DOCS_AUTHORITY_INDEX.md)
- [NQ_DOCS_EVIDENCE_INDEX.md](NQ_DOCS_EVIDENCE_INDEX.md)
- [NQ_DOCS_MIGRATION_MAP.md](NQ_DOCS_MIGRATION_MAP.md)
- [NQ_DOCS_G1_IMPLEMENTATION.md](NQ_DOCS_G1_IMPLEMENTATION.md)

## CI current authority

- [NQ_CI_BASELINE_PLAN.md](NQ_CI_BASELINE_PLAN.md)
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
- [GATEL_PLAN.md](GATEL_PLAN.md)：GateL planning baseline（**PASS / ACCEPTED**；GateL implementation NOT STARTED）。GateL = 真实交易所接入前的 No-Real 交易适配器 / 市场数据 / permission probe / paper-live execution 边界就绪规划（GateL-1..5 + real exchange readiness checklist）。canonical（2026-06-22 经 `NQ-GATEL-CANONICAL-ROUTE-SYNC` 裁决）：**GateL = No-Real exchange/marketdata readiness**；旧口径「GateL = AI Paper Trading」作废，AI Paper Trading 后移到 GateM（NOT STARTED）。不接真实交易所、不读真实凭证、不外联、不启用 LIVE、不接 AI / DH runtime。
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
- [NQ_GATEK_POST_FREEZE_HANDOFF_PLAN.md](NQ_GATEK_POST_FREEZE_HANDOFF_PLAN.md)：GateK post-freeze handoff（CI/security + OKX no-outbound + endpoint defense closure；**PASS / READY FOR NEXT PHASE**；**NEXT PHASE = READY TO PLAN**；evidence matrix + frozen boundaries + regression rules + 下一阶段入口候选 + optional backlog）。
- [GATEK_ARCHITECTURE_BASELINE_REVIEW.md](GATEK_ARCHITECTURE_BASELINE_REVIEW.md)：GateK architecture baseline review（P0/P1=0，ACCEPTED WITH P2 FOLLOW-UP）。
- [NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md](NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md)：OKX bootstrap / test isolation / no-outbound 边界复审（§13 post-CI-security freeze 复审 = PASS / READY FOR FREEZE；P0/P1=0，P2=1 非阻断纵深防御 follow-up，P3=1 命名差异）。
- [NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md](NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md)：OKX bootstrap / test isolation / no-outbound 边界冻结卷宗（**FROZEN / ACCEPTED**，冻结 review commit `0b9c0b20`；原 P2 已经 post-freeze addendum 关闭，见下；regression boundary 见卷宗 §11）。
- [NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_PLAN.md](NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_PLAN.md)：OKX runtime config 默认 endpoint 纵深防御计划与实现（Path A，sentinel `disabled://`；**FROZEN / ACCEPTED**）。
- [NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_ADDENDUM.md](NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_ADDENDUM.md)：上述纵深防御的 post-freeze addendum（**FROZEN / ACCEPTED**；fix commit `c749cef7`，CI run `27926903155` / 9 jobs success；**P2 OkxRuntimeConfig default real endpoint defense = CLOSED / ACCEPTED**）。
- [FRONTEND_DESIGN_SYSTEM.md](FRONTEND_DESIGN_SYSTEM.md)：前端设计系统当前事实。
- [BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md](BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md)：回测权益/回撤曲线后端契约 planning（planning only，未实现）。
- DB / Credential 治理：`DB_SCHEMA_GOVERNANCE_PLAN.md`、`DB_SCHEMA_GOVERNANCE_REVIEW.md`、`CREDENTIAL_*`。
- NQ-DH Integration-0（contract / mock / docs，未实现 runtime 集成）：`NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`、`NQ_DH_INTEGRATION0_SECURITY_POLICY.md`、`NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`、`NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`。

## Codex Workflow 入口

- `../../AGENTS.md`：仓库级 Codex 开发指引、NQ/DH 边界与执行纪律。
- `NQ_DH_CODEX_PLUGIN_WORKFLOW.md`、`NQ_DH_WORKFLOW_ROUTER_SKILL.md`、`NQ_DH_CODEX_TASK_TEMPLATES.md`、`CODEX_PROJECT_INSTRUCTIONS.md`。

## 历史证据位置

当前 current 不再堆放历史过程证据；按主题分别归档：

- GateJ canonical records：`docs/gates/gate-j/`
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
GateK：规划 / 架构 / 产品化 / 部署化 / 可观测性 / 安全边界收口（NEXT）
  ↓
GateL：No-Real Exchange / MarketData Readiness → GateM：AI Paper Trading（NOT STARTED） → GateN：AI 小资金 LIVE → GateO：美股适配 → GateP：A 股适配
```
