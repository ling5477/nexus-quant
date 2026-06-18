# Current Stage

`docs/current/` 是 NexusQuant 当前事实入口。当前状态是 **GateJ completed；Next: GateK-PLAN；NQ CI baseline Batch 1 implemented / first green confirmed；NQ CI Batch 2A/2B/2C/2C hygiene/2D/2E FROZEN / ACCEPTED；Batch 3 no-outbound guard FROZEN / ACCEPTED；Batch 4B secret scan FROZEN / ACCEPTED；Batch 4C overall security artifact/log redaction baseline FROZEN / ACCEPTED（4C-B pre-upload artifact redaction gate FROZEN / ACCEPTED；4C-C log redaction proof FROZEN / ACCEPTED）；Batch 4F execution sequence SYNCED / ACCEPTED，4F-A READY FOR IMPLEMENTATION，4F-B/4F-C/4F-D/4F-E/4F-F NOT STARTED；Static workflow assertion OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED；Batch 5 PENDING；GateK product/runtime implementation not started；AI not started**。

## 当前状态

- GateH completed。
- GateI completed。
- GateJ-PLAN completed。
- GateJ-1-WO completed。
- GateJ-2-WO completed。
- GateJ-3-WO completed。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed。
- GateJ completed。
- Next: GateK-PLAN。
- NQ CI baseline Batch 1 implemented / first green confirmed；NQ CI Batch 2A PostgreSQL/Flyway smoke FROZEN / ACCEPTED；NQ CI Batch 2B FROZEN / ACCEPTED；Batch 2C FROZEN / ACCEPTED；Batch 2C hygiene fix FROZEN / ACCEPTED；Batch 2D FROZEN / ACCEPTED；Batch 2E FROZEN / ACCEPTED；Batch 3 no-outbound guard FROZEN / ACCEPTED（run `27634370657`）；Batch 4A plan review accepted；Batch 4B secret scan FROZEN / ACCEPTED（run `27674393780`，frozen baseline commit `31540de8`）；Batch 4C overall security artifact/log redaction baseline **FROZEN / ACCEPTED**（4C-A plan review PASS / ACCEPTED；4C-B pre-upload artifact redaction gate **FROZEN / ACCEPTED**，immutable green run `27701669084`，frozen baseline = `ci.yml` pre-upload redaction gate step blob `4a40ef78` / commit `c734102d`；4C-C log redaction proof **FROZEN / ACCEPTED**，freeze review，immutable green run `27732660516`，14 类 pattern 真实值命中 = 0，`NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md`；overall freeze review 见 `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`）；Batch 4F plan review **PASS / ACCEPTED**，execution sequence **SYNCED / ACCEPTED**，4F-A **READY FOR IMPLEMENTATION**，4F-B/4F-C/4F-D/4F-E/4F-F **NOT STARTED**（plan `NQ_CI_DEPENDENCY_AUDIT_PLAN.md`，review `NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md`）；Static workflow assertion **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；Batch 5 PENDING；GateK product/runtime implementation not started。
- AI not started。
- DH integration not started / not connected to NQ。
- LIVE disabled。
- Multi-exchange expansion not started。
- GateK architecture baseline review accepted with P2 follow-up；本轮 P2 follow-up 由 `ARCHITECTURE.md` / `MODULES.md` current wording sync 承接，不代表 GateK product/runtime implementation started。
- UI/UX professionalism remains post-freeze remediation。
- NQ / DH 三轮只读审计已完成；DH not integrated；Integration-0 allowed only as contract / mock / documentation work line, not runtime integration（详见 `STATUS.md`）。
- NQ-DH Integration-0 契约冻结已完成（contract / mock / docs，未实现集成）；DH P1-4 残留阻塞 Integration-1，详见 `NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`。
- NQ-DH Integration-0 mock / contract test 详细矩阵（15 项 × 16 字段 + 共享 fixture + forbidden side-effect checklist + 下一步代码任务草案）已设计，只写计划未写测试代码，详见 `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`。
- NQ-DH Integration-0 contract test 代码已实现（仅 `backend/nq-app/src/test/**`，test-only，INT0-T01..T15 共 16 用例，全部 mock/stub/内存校验，无真实集成）；`mvn -f backend/pom.xml test` 通过，nq-app 51 tests。
- NQ-DH Integration-0 safety gate：**CLOSED / ACCEPTED**（详见 `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`）。Runtime integration / Integration-1 / AI 仍 NOT STARTED；DH NOT INTEGRATED；LIVE DISABLED；Integration-1 前置为 DH P1-4 residual（rate limit / memory cap / replay nonce persistence）。
- 当前本地 PostgreSQL 默认端口固定为 `5432`。

## 当前不是

- 当前不是 AI 自动交易阶段。
- 当前不允许 AI 直接下单。
- 当前不允许真实 LIVE 下单。
- AI 接入必须等 Paper Trading 稳定后再进入（最早 GateK）。
- GateJ 是 Paper Trading 稳定运行阶段，不是 AI 阶段。
- GateJ-FREEZE 已完成连续运行验收与冻结，不夹带 AI、不夹带新业务功能。
- 当前不是 GateK 产品 / runtime 实现阶段；NQ CI baseline Batch 1 只是最小测试基线，不代表 AI、DH runtime、LIVE 或真实交易所扩展启动。
- 当前不代表 UI/UX 专业化已完成。
- 当前不应描述为面向公开用户的生产就绪。

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
GateL：AI Paper Trading
  ↓
GateM：AI 小资金 LIVE
  ↓
GateN：美股适配
  ↓
GateO：A 股适配
```

## 当前事实文件

- `STATUS.md`：当前项目状态与未完成项。
- `ARCHITECTURE.md`：当前架构事实与依赖边界；已同步 GateJ completed / GateK planning baseline accepted / GateK implementation not started / AI not started / DH runtime not integrated / LIVE disabled / real adapter not implemented 口径。
- `MODULES.md`：模块 owner、职责和禁止反向依赖；已同步 backend 分层、frontend Design System v1、research/py 独立工具链和 permission probe no-real baseline。
- `API.md`：当前 API 分类入口。
- `BACKTEST_EQUITY_DRAWDOWN_SERIES_API_PLAN.md`：回测权益/回撤曲线后端契约 planning;结论为 `GET /api/backtest-runs/{runId}/pnl-snapshots`(表 `sim_pnl_snapshots`)已存在,无需新增后端 API/表/migration,B1 曲线 unavailable 属前端未接线,后续前端切片(B1.1)消费既有端点 + 派生 drawdown。planning only,未实现。
- `DB_SCHEMA.md`：数据库事实入口。
- `CREDENTIAL_REVOCATION_GOVERNANCE_REVIEW.md`：Credential revocation Batch 5-A 只读审计报告。
- `CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md`：Credential revocation Batch 5-B schema completed、Batch 5-C code/API/test completed、Batch 5-D-B explicit rotate command implemented、Batch 5-E-B deterministic active material selection implemented、Batch 5-F-C enable command implemented 与 permission probe 最小 code/API/test implemented 事实。
- `CREDENTIAL_ROTATE_GOVERNANCE_REVIEW.md`：Credential rotate Batch 5-D-A 只读审计报告；作为 5-D-B 实现前的审计快照保留。
- `CREDENTIAL_ACTIVE_MATERIAL_SELECTION_REVIEW.md`：Credential active material Batch 5-E-A 只读审计报告；记录 active summary / active material 多 credential type 选择风险与 5-E-B 建议。
- `CREDENTIAL_ACTIVE_CREDENTIAL_UNIQUENESS_REVIEW.md`：Credential active credential Batch 5-E-C 只读审计报告；记录 account 全局 active 唯一约束取舍、5-E-D migration 决策和 Batch 5-F enable 审计前置条件。
- `CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md`：Credential enable Batch 5-F-A 只读审计报告；记录可恢复状态、不可恢复状态和 Batch 5-F-B schema-only `ENABLED` audit event 准备事实；Batch 5-F-C 已实现最小 enable command。
- `CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md`：Credential governance Batch 5-G 冻结复核报告；Batch 5-G-A 已完成 P3 文案 cleanup，冻结 Batch 5-A ~ 5-F-C 的 schema、API、Service、Repository、audit log、测试和文档边界。
- `CREDENTIAL_PERMISSION_PROBE_FREEZE_REVIEW.md`：Credential permission probe 当前权威冻结结论；guarded backend baseline 已接受冻结，默认 `NoRealExchangeCredentialPermissionProbePort -> SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`，真实 OKX/Binance adapter 仍未实现。
- `CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md`：Credential permission probe code/API/test 历史设计审计与实现记录；保留独立 port、Service、POST/GET API、JDBC 写回、adapter boundary tests 和 no-real-exchange guard 的证据链。
- `CREDENTIAL_PERMISSION_PROBE_DESIGN_REVIEW.md`：Credential permission probe 历史设计审计与 V31 schema-only 记录；保留权限建模、Paper/LIVE 隔离、脱敏和 future real adapter 入场条件。
- `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md`：OKX adapter bootstrap no-outbound 只读审计报告；记录 local integration test 启动期 OKX public instruments 外联触发路径、根因、Binance 对照和后续 FIX 建议；本轮未修改代码、未调用真实交易所。
- `NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`：NQ-DH Integration-0 契约冻结主文档（禁止能力 / 可开放能力 / header / 数据契约 / 不可信输入 / 验收 / Integration-1 blockers）；contract-only，未实现集成。
- `NQ_DH_INTEGRATION0_SECURITY_POLICY.md`：NQ-DH Integration-0 安全策略（header / 签名 / 防重放 / tenant / payload / 脱敏 / 审计）；契约设计，未实现代码。
- `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`：NQ-DH Integration-0 mock / contract test 设计（15 项）+ 详细矩阵（实现已落地，见提交 `fc922d06`）。
- `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`：NQ-DH Integration-0 safety gate close / acceptance（PASS / CLOSED / ACCEPTED）；Runtime integration / Integration-1 / AI not started，DH not integrated，LIVE disabled。
- `TESTING.md`：统一验证命令与本次验证记录。
- `RUNBOOK.md`：本地启动与常见问题。
- `ROADMAP.md`：总路线。
- `GATEK_PLAN.md`：GateK planning-only 阶段规划；用于冻结 GateK 目标、非目标、主线拆分、验收标准、风险、backlog、安全审计前置和执行顺序。
- `GATEK_ARCHITECTURE_BASELINE_REVIEW.md`：GateK architecture baseline review；审查 backend/frontend/research/docs/test/security 边界，结论为 P0/P1=0、ACCEPTED WITH P2 FOLLOW-UP，未启动 GateK implementation。其 P2 文档漂移 follow-up 对应 `ARCHITECTURE.md` / `MODULES.md` current wording sync。
- `NQ_CI_BASELINE_PLAN.md`：NQ CI baseline 文档；Batch 1 已新增 `.github/workflows/ci.yml`，GitHub Actions run `27496906788` first green confirmed；Batch 2A PostgreSQL-Flyway smoke 已由 GitHub Actions run `27501253175` first green confirmed，并经 freeze review 固化为 FROZEN / ACCEPTED；Batch 2B schema artifact baseline 已由 GitHub Actions run `27521750442` first green confirmed，并经 freeze review 固化为 FROZEN / ACCEPTED；Batch 2C repository-only real PostgreSQL smoke 已由 GitHub Actions run `27535619157` first green confirmed，并经 freeze review 固化为 FROZEN / ACCEPTED；Batch 2C hygiene fix 已由 GitHub Actions run `27550583713` first green confirmed，并经 freeze review 固化为 FROZEN / ACCEPTED；Batch 2D first run `27590822405` failed（`venue must not be blank`，已修），second run `27592872701` failed at `securityFilterChain`（`webEnvironment = NONE` 下无 `HttpSecurity`，已修），third run `27596768301` failed in the test body with `NotAMockException` after servlet web context startup，FIRST-RUN-FIX #3 后 GitHub Actions run `27601707199` completed / success，并经 freeze review 固化为 FROZEN / ACCEPTED；Batch 3 no-outbound guard 已实现最小 workflow / test-scope guard，并由 GitHub Actions run `27634370657` first green confirmed（6 jobs green），经 Batch 3E freeze review 固化为 FROZEN / ACCEPTED；secret scan、frontend E2E hardening 仍是后续批次。
- `NQ_CI_POSTGRES_FLYWAY_PLAN.md`：NQ CI Batch 2 PostgreSQL / Flyway 文档；Batch 2A 已新增 GitHub Actions `postgres-flyway` job，使用 PostgreSQL service container 和 direct Flyway API 验证 empty DB V1-V31 migration smoke，并在 run `27501253175` 完成首次 green review 与 freeze review；Batch 2B 已在该 job 中实现 schema artifact generation / upload，并在 run `27521750442` 完成 first green run review 与 freeze review；Batch 2C repository real DB smoke 已实现，并在 run `27535619157` 完成 first green run review 与 freeze review；Batch 2C hygiene fix 已在 run `27550583713` 完成 first green run review 与 freeze review；Batch 2D app context smoke 当前为 FROZEN / ACCEPTED，run `27601707199` confirmed `NqAppContextPostgresSmokeTest` tests=1 / skipped=0 / failures=0 / errors=0；Batch 2E seed watcher cleanup 当前为 FROZEN / ACCEPTED，run `27610448572` backend Maven test 失败根因为 `ResearchBacktestHappyPathLocalTest` 缺少 legacy `accounts` row，已改为迁移完成后同步插入 CI-only legacy fixture；run `27614046762` confirmed backend Maven test and `postgres-flyway` job success，并经 freeze review 接受为当前 `dev` baseline。
- `NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`：NQ CI Batch 2B schema artifact / docs review 计划、implementation、first-run review 与 freeze review 记录；实现并冻结 `flyway-info`、schema tables / columns / constraints / indexes / comments artifacts、schema-only dump、retention、redaction 和 `DB_SCHEMA.md` drift review checklist；当前为 FROZEN / ACCEPTED。
- `NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`：NQ CI Batch 2C repository real PostgreSQL smoke 文件；盘点 repository / JDBC / Spring context 测试边界，冻结 2C-1 / 2C-2 / 2C-3 切片、seed / fixture、transaction / cleanup、安全和 rollback 策略；当前为 FROZEN / ACCEPTED。
- `NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`：NQ CI Batch 2D `nq-app` context smoke planning + implementation + first-run review + freeze review 文件；盘点 `@SpringBootTest` / profile / `AuthSeedConfiguration` / runner / scheduler / adapter / no-real probe 边界，并实现 CI-only fake profile / explicit properties、no seed、rollback 和 required-check 评估；FIRST-RUN-FIX #3 后 run `27601707199` confirmed `NqAppContextPostgresSmokeTest` tests=1 / skipped=0 / failures=0 / errors=0；当前为 FROZEN / ACCEPTED。
- `NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md`：Batch 2E CI-only seed watcher cleanup 文件；`backend` job background seed watcher 已删除；first-run fix 在 `Run backend tests` 前新增同步 post-Flyway CI-only legacy `accounts` fixture，显式校验不创建 `exchange_accounts` 或 credential rows；run `27614046762` confirmed backend Maven test and `postgres-flyway` job success；当前为 FROZEN / ACCEPTED。
- `NQ_CI_NO_OUTBOUND_GUARD_PLAN.md`：GateK CI Batch 3 no-outbound guard plan / implementation 文件；盘点 OKX / Binance adapter、public API、WebSocket、scheduler / recovery / monitor、permission probe、application profiles、CI env / secrets 风险，并在 Batch 3B 落地最小 `No-outbound guard` job、exchange denylist、test-scope `ProxySelector` guard、NoReal port assertion 与 app-context startup interception；并由 GitHub Actions run `27634370657` first green confirmed，经 Batch 3E freeze review 固化为 FROZEN / ACCEPTED；Batch 4 / Batch 5 仍 PENDING。
- `NQ_CI_SECURITY_GUARD_PLAN.md`：GateK CI Batch 4 security guard / secret scan plan + implementation 文件；Batch 4A plan review ACCEPTED；Batch 4B secret scan FROZEN / ACCEPTED（run `27674393780`，frozen baseline commit `31540de8`）；Batch 4C overall security artifact/log redaction baseline FROZEN / ACCEPTED（4C-B pre-upload artifact redaction gate FROZEN / ACCEPTED，4C-C log redaction proof FROZEN / ACCEPTED；overall freeze review 见 `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`）；Batch 4F execution sequence SYNCED / ACCEPTED，4F-A READY FOR IMPLEMENTATION，4F-B 至 4F-F NOT STARTED；Batch 5 PENDING。
- `NQ_CI_DEPENDENCY_AUDIT_PLAN.md`：GateK CI Batch 4F dependency audit / supply-chain audit planning 文件；plan review 已由 `NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md` 接受为 implementation baseline；execution sequence 已同步为 4F-A dependency audit input / toolchain preflight、4F-B sanitized advisory audit summary、4F-C SBOM report-only、4F-D PR dependency delta review、4F-E GitHub Actions / CLI supply-chain pinning、4F-F Dependabot / Renovate governance；4F-A READY FOR IMPLEMENTATION，4F-B 至 4F-F NOT STARTED；未修改 workflow / code / tests / migration / frontend / research / scripts / deploy。
- `NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md`：GateK CI Batch 4F dependency audit / supply-chain audit plan review 主文档；结论 PASS / ACCEPTED，Batch 4F plan ACCEPTED AS IMPLEMENTATION BASELINE，execution sequence SYNCED / ACCEPTED，4F-A READY FOR IMPLEMENTATION，4F-B 至 4F-F NOT STARTED，P0/P1=0，Batch 4C FROZEN / ACCEPTED，Static workflow assertion OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED，Batch 5 PENDING。
- `NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`：GateK CI Batch 4C artifact / log redaction proof 当前文件；4C-A plan review ACCEPTED；4C-B pre-upload artifact redaction gate FROZEN / ACCEPTED（immutable run `27701669084`）；4C-C log redaction proof FROZEN / ACCEPTED（freeze review，green run `27732660516`，14 类 pattern 真实值命中 = 0）；Batch 4C overall FROZEN / ACCEPTED（overall freeze review 见 `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`）；复用 Batch 4B pattern，不重复 4B，不做 Batch 4F dependency audit / Batch 5 frontend E2E hardening。
- `NQ_CI_LOG_REDACTION_PROOF_PLAN.md`：GateK CI Batch 4C-C log redaction proof 文件；plan + plan review（28 项）+ log proof + freeze review。基于 immutable green run `27732660516`（7/7 jobs green，ci.yml blob `4a40ef78` 与 HEAD 等价）的 review-time per-job log proof：7 jobs 全复核，14 类 pattern 真实值命中 = 0，仅 disposable CI 值（`123456` / `nq_ci_password`）/ Spring ephemeral dev password / platform `***` mask / step-script 回显非阻断 FP，proof 不输出 secret value / 完整匹配行；本轮未改 ci.yml（静态断言列为 optional future hardening / not implemented）、未读本地 logs、未上传 logs artifact。当前 **FROZEN / ACCEPTED**；Batch 4C overall 当前 **FROZEN / ACCEPTED**；不做 Batch 4F / Batch 5。
- `NQ_CI_LOG_REDACTION_PROOF_FREEZE_REVIEW.md`：GateK CI Batch 4C-C freeze review 主文档；结论 PASS / ACCEPTED / FROZEN，P0/P1/P2 blockers = 0；其历史结论在 4C-C 子基线冻结时明确 Batch 4C overall 仍 NOT FROZEN，后续已由 `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md` 收口为 Batch 4C overall FROZEN / ACCEPTED。
- `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`：GateK CI Batch 4C overall freeze review 主文档；结论 PASS / ACCEPTED / FROZEN，Batch 4C-B / 4C-C 均 FROZEN / ACCEPTED，P0/P1=0，static workflow assertion OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED；该 4C freeze review 不实现 Batch 4F，当前 Batch 4F planning 另见 `NQ_CI_DEPENDENCY_AUDIT_PLAN.md`；Batch 5 PENDING。
- `WORKLOG.md`：执行日志。

## Codex Workflow 入口

- `../../AGENTS.md`：仓库级 Codex 开发指引、NQ/DH 边界与执行纪律。
- `NQ_DH_CODEX_PLUGIN_WORKFLOW.md`：NQ/DH 插件路由、任务类型和标准工作流。
- `NQ_DH_WORKFLOW_ROUTER_SKILL.md`：`nq-dh-workflow-router` active skill 的源规格与维护规范。
- `NQ_DH_CODEX_TASK_TEMPLATES.md`：常用代码审查、前端优化、图表、交易所字段、Gate 报告、部署审查和 DH Integration-0 模板。
- `CODEX_PROJECT_INSTRUCTIONS.md`：可复制到 Codex Project Instructions 的完整规则。

## 当前 Gate / planning 文件

- `GATEK_PLAN.md`：GateK-PLAN 当前规划文件；不代表 GateK implementation started。
- `GATEK_ARCHITECTURE_BASELINE_REVIEW.md`：GateK architecture baseline review 当前审查报告；不代表 GateK implementation started；`ARCHITECTURE.md` / `MODULES.md` 是其 P2 follow-up 后的 current architecture / modules fact source。
- `NQ_CI_BASELINE_PLAN.md`：NQ CI baseline 当前文件；Batch 1 最小 workflow 已实现并完成首次 green run review，Batch 2A PostgreSQL-Flyway smoke、Batch 2B schema artifact baseline 与 Batch 2C repository real PostgreSQL smoke 均已冻结为 FROZEN / ACCEPTED。
- `NQ_CI_POSTGRES_FLYWAY_PLAN.md`：Batch 2 PostgreSQL / Flyway 当前文件；2A / 2B / 2C 均为 FROZEN / ACCEPTED；2C-HYGIENE-FIX 已 FROZEN / ACCEPTED；2D 为 FROZEN / ACCEPTED；2E 为 FROZEN / ACCEPTED；下一步只允许 Batch 3A plan review / fix、Batch 3B implementation、Batch 4 / Batch 5 later planning 或暂停 CI 线。
- `NQ_CI_POSTGRES_FLYWAY_2B_PLAN.md`：Batch 2B schema artifact / docs review 文件；记录 plan accepted、artifact implementation、first green run evidence、freeze review evidence 和冻结边界。
- `NQ_CI_POSTGRES_FLYWAY_2C_PLAN.md`：Batch 2C repository real PostgreSQL smoke 文件；已 FROZEN / ACCEPTED 为当前 `dev` repository-only real DB 最小验证基线，不启动 `nq-app` context、不使用真实 seed、不接真实交易所。
- `NQ_CI_POSTGRES_FLYWAY_2D_PLAN.md`：Batch 2D `nq-app` context smoke planning + implementation + first-run review + freeze review 文件；FROZEN / ACCEPTED，不新增 migration、不启动真实 provider、不触发真实交易所；Batch 3B 已在该 app-context smoke 上追加 no-outbound guard interception，first CI run evidence 仍 pending。
- `NQ_CI_POSTGRES_FLYWAY_2E_PLAN.md`：Batch 2E CI-only seed watcher cleanup 文件；已修改 workflow 删除 background seed watcher；first-run fix 已新增同步 post-Flyway CI-only legacy `accounts` fixture，并校验不创建 `exchange_accounts` 或 credential rows；run `27614046762` first green confirmed，freeze review 已接受为 FROZEN / ACCEPTED。
- `NQ_CI_NO_OUTBOUND_GUARD_PLAN.md`：Batch 3 no-outbound guard plan / implementation / freeze 文件；当前为 FROZEN / ACCEPTED（run `27634370657`），是当前 `dev` no-outbound guard baseline，后续只能进入 Batch 4 planning、Batch 3 parity/hygiene follow-up，或暂停 CI 线。
- `NQ_CI_SECURITY_GUARD_PLAN.md`：Batch 4 security guard / secret scan 当前 plan + implementation 文件；Batch 4A plan review ACCEPTED；Batch 4B FROZEN / ACCEPTED（run `27674393780`，frozen baseline commit `31540de8`）；Batch 4C overall security artifact/log redaction baseline FROZEN / ACCEPTED（4C-B pre-upload artifact redaction gate FROZEN / ACCEPTED，immutable run `27701669084`；4C-C log redaction proof FROZEN / ACCEPTED，immutable run `27732660516`，14 类 pattern 真实值命中 = 0；overall freeze review 见 `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`）；Batch 4F execution sequence SYNCED / ACCEPTED，4F-A READY FOR IMPLEMENTATION，4F-B 至 4F-F NOT STARTED；下一步只允许 4F-A dependency audit preflight、optional static assertion、Batch 5 planning，或暂停 CI 线；Batch 4F 不得写成 implemented，Batch 5 仍 PENDING。
- `NQ_CI_DEPENDENCY_AUDIT_PLAN.md`：Batch 4F dependency audit / supply-chain audit planning 文件；覆盖 Maven / npm / Python audit、GitHub Actions supply-chain、action SHA pinning、SBOM、Dependency Review、Dependabot / Renovate、CI blocking / advisory 分层、raw artifact / report hygiene、与 Batch 4C redaction baseline 和 Batch 5 frontend E2E hardening 的关系；plan review PASS / ACCEPTED，execution sequence SYNCED / ACCEPTED，4F-A READY FOR IMPLEMENTATION，4F-B 至 4F-F NOT STARTED。
- `NQ_CI_SECURITY_GUARD_BATCH_4F_PLAN_REVIEW.md`：Batch 4F plan review 当前文件；结论 PASS / ACCEPTED，Batch 4F plan ACCEPTED AS IMPLEMENTATION BASELINE，execution sequence SYNCED / ACCEPTED，4F-A READY FOR IMPLEMENTATION，4F-B 至 4F-F NOT STARTED，P0/P1=0，workflow/code/test/migration/frontend/research/scripts/deploy diff 均为空。
- `NQ_CI_ARTIFACT_LOG_REDACTION_PLAN.md`：Batch 4C artifact / log redaction proof 当前文件；4C-A plan review PASS / ACCEPTED；4C-B pre-upload redaction gate FROZEN / ACCEPTED（immutable run `27701669084`，frozen baseline = `ci.yml` pre-upload redaction gate step blob `4a40ef78` / commit `c734102d`）；4C-C log redaction proof FROZEN / ACCEPTED（immutable run `27732660516`，14 类 pattern 真实值命中 = 0）；Batch 4C overall FROZEN / ACCEPTED（overall freeze review `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`）；下一步只允许 optional static assertion、Batch 4F-A dependency audit preflight、Batch 5 planning，或暂停 CI 线；该 4C 文件不实现 Batch 4F / Batch 5。
- `NQ_CI_LOG_REDACTION_PROOF_PLAN.md`：Batch 4C-C log redaction proof 当前文件；**FROZEN / ACCEPTED**（plan + plan review 28 项 + log proof + freeze review）。基于 immutable green run `27732660516`（7/7 jobs green，ci.yml blob `4a40ef78` 与 HEAD 等价）的 review-time per-job log proof：7 jobs（Diff check / No-outbound guard / Backend Maven test / PostgreSQL / Flyway smoke / Frontend build / Research quality gate / Secret scan）全复核，14 类 pattern（完整 AKIA/ASIA、sk-/sk-ant-/sk-proj-、github_pat_/ghp_/gho_、xoxb-/xoxp-、PEM、value-bearing 凭证赋值、creds-in-URL、signature、raw req/resp、encrypted_payload/decrypted_payload、Spring 生成 password、disposable DB 值、platform mask、printenv/set -x）真实值命中 = 0，仅 disposable CI 值 / Spring ephemeral dev password / platform `***` mask / step-script 回显非阻断 FP（逐项说明）；finding 只输出 job / category / rule / safe excerpt，不输出 secret value / 完整匹配行；不读取本地 logs / 不上传 logs artifact / 不自扫 streaming 日志、静态断言列为 optional future hardening / not implemented（本轮未改 ci.yml）。Batch 4C overall FROZEN / ACCEPTED；不做 Batch 4F dependency audit / Batch 5 frontend E2E hardening。
- `PLAN_GATEJ.md`：GateJ 阶段规划。
- `GATEJ_API_PLAN.md`：GateJ API 规划。
- `GATEJ_DB_PLAN.md`：GateJ DB 规划。
- `GATEJ_FRONTEND_PLAN.md`：GateJ 前端规划。
- `GATEJ_TEST_PLAN.md`：GateJ 测试规划。
- `GATEJ_WORK_ORDER.md`：GateJ 工作单（含 GateJ-FREEZE 范围）。
- `GATEJ_FREEZE_FINAL_ACCEPTANCE_REPORT.md`：GateJ-FREEZE 最终验收报告。
- `DOC_CLEAN_REPORT.md`：最近一次文档清理报告。

## 历史 Gate 冻结卷宗

已完成 Gate 的计划文档不在 `docs/current/` 重复，统一保存在 `docs/gates/`：

- `docs/gates/gate-h/`：GateH 冻结卷宗。
- `docs/gates/gate-i/`：GateI 冻结卷宗。
- `docs/gates/gate-j/`：GateJ 冻结卷宗。
- `docs/gates/gate-a..g/`：早期 Gate 历史卷宗。
