# Current Status

## 项目定位

NexusQuant 是通用量化交易平台，第一阶段聚焦虚拟币量化交易，后续复用账户、行情、策略、回测、评估、发布、风控、交易、复盘、AI 信号协议等底座扩展到美股和 A 股。

## 当前完成状态

- NQ GateL-1B No-Real hardening plan（2026-06-22）：**NQ-GATEL-1B-NO-REAL-HARDENING-PLAN = PASS / PLAN READY FOR REVIEW；PLANNING ONLY / NOT IMPLEMENTED**。详见 `GATEL_1B_NO_REAL_HARDENING_PLAN.md`。针对四项冻结 P1 规划 A Binance endpoint sentinel/guard、B OKX/Binance process credential source、C raw payload producer suppression + 后续字段删除、D Noop `subscribed=false + NO_REAL_DISABLED` 四个最小切片。实施顺序 A → B → C → D；默认独立实现与 review，不合成大批次。不需要 migration，不新增 HTTP API。P1/P2 全部 OPEN / RETAINED；adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED。
  下一步仅允许 `NQ-GATEL-1B-NO-REAL-HARDENING-PLAN-REVIEW`；不得直接 implementation。LIVE DISABLED；AI NOT STARTED；DH runtime NOT INTEGRATED；real exchange/credential/provider/permission probe 继续禁止。
  验证：docs-only 定向源码/测试结构复核、链接/状态/范围/敏感值/diff 检查；未跑 Maven/frontend/Python，未访问网络、交易所、DB，未读取 credential material。回滚为删除 plan 并还原 6 份 current docs。
- NQ GateL-1A exchange adapter contract review freeze（2026-06-22）：**NQ-GATEL-1A-EXCHANGE-ADAPTER-CONTRACT-REVIEW-FREEZE = PASS / FROZEN / ACCEPTED**。详见 `GATEL_1_EXCHANGE_ADAPTER_CONTRACT_FREEZE_REVIEW.md`。冻结对象仅为 GateL-1 review 的事实、P1/P2 与后续处理顺序；adapter readiness = **NOT READY / NOT FROZEN / NOT AUTHORIZED**。四项 P1 与四项 P2 全部 OPEN / RETAINED，未修复、未关闭。后续顺序冻结为 GateL-1B No-Real hardening plan → 1C capability matrix contract → 1D error model contract → 1E future-real readiness checklist refinement。下一步仅允许 `NQ-GATEL-1B-NO-REAL-HARDENING-PLAN`，不直接改代码。
  验证：docs-only 定向源码复核、链接/术语/状态/diff 范围检查；未跑 Maven/frontend/Python，未访问网络、交易所、DB，未读取 credential material。GateL implementation NOT STARTED；LIVE DISABLED；AI NOT STARTED；DH runtime NOT INTEGRATED。
  回滚：删除 freeze review 并还原 6 份 current docs 条目与顺序调整；无 runtime/DB/workflow/credential/provider/exchange 副作用。
- NQ GateL-1 exchange adapter contract review（2026-06-22）：**NQ-GATEL-1-EXCHANGE-ADAPTER-CONTRACT-REVIEW = CONDITIONAL PASS / DOCS-CONTRACT ONLY**；GateL implementation **NOT STARTED**。详见 `GATEL_1_EXCHANGE_ADAPTER_CONTRACT_REVIEW.md`。确认 adapter-api 已拆 Trading/MarketData/Account/Historical ports，core 执行链在 adapter IO 前经过 RiskGate + OrderStateMachine，ledger/audit 保持 NQ 事实主权；但现有合同不能冻结为 future-real-ready。P0=0；P1=4：Binance 默认 endpoint 仍指向 testnet/mainnet、OKX/Binance adapter 默认构造链读取进程 credential、order ack/snapshot 暴露 `rawPayload`、Noop marketdata 普通 success 无 STUB/NO_REAL 标记；P2=4：capability、error/retry、marketdata public/private、architecture wiring contract 缺口。GateL-1A 已冻结 review fact baseline；P1/P2 仍 OPEN。后续顺序：1B No-Real hardening plan → 1C capability → 1D error → 1E readiness checklist；不实现真实 provider、RealClient、permission probe 或 LIVE。
  验证：docs-only；执行限定目录静态检索、关键文件逐行审查、链接/术语/diff 检查；未跑 Maven/frontend/Python，未访问网络/交易所/DB。Rollback：删除 review 文档并还原 6 份 current docs 条目。
  边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real permission probe；GateL implementation NOT STARTED。
- NQ GateL canonical route sync（2026-06-22）：**NQ-GATEL-CANONICAL-ROUTE-SYNC = PASS / DOCS-ONLY**。修正 `GATEL_PLAN.md` 报告的 P1 路线图语义冲突。**Canonical 裁决：GateL = No-Real Exchange / MarketData Readiness**（planning / contract / readiness）；旧口径「GateL = AI Paper Trading」作废；**AI Paper Trading 后移到 GateM**（后续独立 AI/DH 阶段，当前 NOT STARTED），AI 小资金 LIVE → GateN，美股适配 → GateO，A 股适配 → GateP。GateL 拆分固定为 GateL-1 exchange adapter contract review / GateL-2 market data no-real pipeline / GateL-3 permission probe contract / GateL-4 paper-first execution boundary / GateL-5 real exchange readiness checklist。AI Paper Trading 不属于 GateL 入场任务。已同步 README（root 无 GateL 定义，未改）/ docs-current README / ROADMAP / STATUS / GATEL_PLAN / TESTING / WORKLOG。真实交易所接入仍须 readiness checklist 全满足 + 专项安全审计 + 用户显式授权后另起 Gate。本轮 docs-only：未改 backend / frontend / research / scripts / deploy / workflow / migration / 代码 / 测试；AI NOT STARTED；DH runtime NOT INTEGRATED；LIVE DISABLED；RealClient / real provider / real permission probe NOT IMPLEMENTED；不把 GateL plan 写成 implementation started。Rollback：还原本轮 6 份 docs 的 GateL 路线/定义入口即可，无 runtime/DB/credential/provider/exchange 副作用。
  边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。
- NQ GateL planning（2026-06-22）：**NQ-GATEL-PLAN = PASS / ACCEPTED（PLANNING BASELINE）**；**GateL implementation NOT STARTED**。详见 `GATEL_PLAN.md`。
  本轮把 GateL planning baseline 落档：GateL = 真实交易所接入前的 No-Real 交易适配器 / 市场数据 / permission probe / paper-live execution 边界就绪规划。拆分 GateL-1（exchange adapter contract review，最小可执行批次）/ GateL-2（market data no-real pipeline）/ GateL-3（permission probe contract）/ GateL-4（paper-first execution boundary）/ GateL-5（real exchange readiness checklist）。只读盘点 adapter 契约与既有代码：`nq-adapter-api`（TradingAdapter/MarketDataAdapter/AccountAdapter/HistoricalKlineAdapter + AdapterError/AdapterResultCategory 9 类）、OKX/Binance adapter（含 PermissionProbeBoundary；GateL-1 后续确认仅 OKX 默认 endpoint 为 `disabled://` sentinel，Binance 仍有外部默认 URL）、core marketdata（HistoricalKlineProvider/HistoricalMarketDataPort/InstrumentCatalog/FixtureMarketdata）、core trading（OrderCommandService/OrderLifecycleService/OrderStateMachine）、risk（RiskGate + 9 rules + KillSwitch）、ledger（LedgerService/TradeLedgerPostingService）、GateK 安全基线（EnvSafetyValidator/NoOutboundExchangeGuard/NoRealExchangeCredentialPermissionProbePort）。
  硬性问题答案固定：不能直接接真实 OKX/Binance；不能启用 LIVE；不能实现 RealClient；不能读真实 API key；不能让 DH runtime 接入交易执行链路。GateL 不得削弱 GateK 已冻结的 no-outbound/EnvSafety/secret-scan/redaction/NoReal probe/sentinel endpoint；GateL 与 Integration-1（NQ-DH 真实只读通道）独立。
  P0=0；**P1=0（原路线图 GateL 语义冲突已由 `NQ-GATEL-CANONICAL-ROUTE-SYNC` 裁决关闭：canonical = No-Real exchange/marketdata readiness；AI Paper Trading 后移到 GateM）**；P2=2（adapter capability/error matrix 未集中成文；`application-ci.yml`/`application-paper.yml` 命名差异，均非阻断）。本轮 docs-only：未改代码 / API / migration / workflow / frontend / research / scripts / deploy；未实现 GateL 任何能力；未触发 Actions；未读取真实凭证；未外联。Rollback：删除 `GATEL_PLAN.md` 并还原 README/ROADMAP/STATUS/TESTING/WORKLOG 本轮状态入口即可，无 runtime/DB/credential/provider/exchange 副作用。
  边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。
- NQ GateK post-freeze handoff（2026-06-22）：**NQ-GATEK-POST-FREEZE-HANDOFF-PLAN = PASS / READY FOR NEXT PHASE**；**NEXT PHASE = READY TO PLAN**。详见 `NQ_GATEK_POST_FREEZE_HANDOFF_PLAN.md`。
  确认 GateK CI/security + OKX bootstrap no-outbound + OkxRuntimeConfig endpoint defense 已全部收口（FROZEN / ACCEPTED；P2 CLOSED），形成进入下一阶段规划前的交接。Evidence matrix：GateK final freeze `8d126f9f`；OKX no-outbound freeze `8a2fbe4a`；endpoint defense impl `c749cef7`；endpoint defense addendum `7d9330c3`；CI run `27926903155`（9 jobs success）/ 5B-SMOKE run `27903497008`（9 jobs success）/ 5B-ENV run `27876451289`（8 jobs success）。
  Frozen boundaries：CI workflow baseline / no-outbound guard / EnvSafetyValidator / NoReal permission probe / OKX runtime default endpoint sentinel / test·ci·paper·local no-real / `.env.example` placeholder-only / secret scan·redaction·frontend no-backend E2E 均 frozen。Regression rules：改动 ci.yml / EnvSafetyValidator / no-outbound guard / NoReal probe / OkxRuntimeConfig / OKX adapter construction·bootstrap·recovery·catalog sync / `application*.yml` defaults / `.env.example` / CI env guard / 任何 real exchange adapter·provider·RealClient 路径，须重新 review + CI evidence + freeze/addendum。
  下一阶段入口候选（不在本轮启动，由路线图决定）：GateL PLAN / Integration-1 PLAN / Market data next batch PLAN / Trading adapter no-real contract PLAN。Optional backlog（NOT BLOCKING）：Batch 4F-B..4F-F / static workflow assertion；`application-ci.yml`/`application-paper.yml` 命名差异（P3）。
  P0=0；P1=0；P2=CLOSED；P3=1（命名差异，非阻断）。无阻断项。本轮 docs-only：未改代码 / workflow / 配置 / 测试 / migration / frontend / research / scripts / deploy；未实现下一阶段功能。Rollback：revert handoff docs commit 即可，无 runtime/DB/credential/provider/exchange 副作用。
  边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。
- NQ OKX runtime config 默认 endpoint 纵深防御 post-freeze addendum（2026-06-22）：**NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE = FROZEN / ACCEPTED**；**P2 OkxRuntimeConfig default real endpoint defense = CLOSED / ACCEPTED**。详见 addendum 卷宗 `NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_ADDENDUM.md`。
  把 fix commit `c749cef7`（`OkxRuntimeConfig` 默认 endpoint → `disabled://okx-not-configured` / `disabled://okx-ws-not-configured` sentinel）以 post-freeze addendum 固化为 parent freeze `NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND`（FROZEN / ACCEPTED）的 addendum。CI evidence：run `27926903155` / `NQ CI Baseline` / push / dev / headSha `c749cef7` / completed / success / 9 jobs all success（diff-check / no-outbound-guard / ci-security-smoke / backend / postgres-flyway / frontend / frontend-no-backend-e2e / research / secret-scan）。
  Security conclusion：code-level real OKX default host removed；explicit env override 行为不变；no-real default 边界强化；no-outbound 边界未削弱；EnvSafetyValidator / workflow / `application*.yml` / `.env.example` / migration / frontend / research / scripts / deploy 均未改。本轮 docs-only。
  P0=0；P1=0；**P2=CLOSED / ACCEPTED**；P3=1（`application-ci.yml`/`application-paper.yml` 命名差异，非阻断）。Regression boundary：后续改动 `OkxRuntimeConfig` 默认值 / adapter construction / EnvSafetyValidator / no-outbound guard / permission probe / CI env guard / profile defaults 须重新 review + CI evidence + addendum/freeze。Rollback：revert addendum docs commit 回 READY；revert `c749cef7` 恢复旧默认（显式 env 下行为不变）；docs addendum 无 runtime/DB/credential/provider/exchange 副作用。
  边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。
- NQ OKX runtime config 默认 endpoint 纵深防御实现（2026-06-22）：**NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE = IMPLEMENTED / PENDING CI RUN**（已由 post-freeze addendum 收口为 FROZEN / ACCEPTED，见上）。详见 `NQ_OKX_RUNTIME_CONFIG_DEFAULT_ENDPOINT_DEFENSE_PLAN.md`。
  按已接受的 PLAN-REVIEW（Path A，sentinel 定稿 `disabled://`）实施：`OkxRuntimeConfig` 代码级真实默认 endpoint 改为非真实 sentinel —— `DEFAULT_BASE_URL=disabled://okx-not-configured`、`DEFAULT_DOME_WS_PRIVATE_URL=DEFAULT_REAL_WS_PRIVATE_URL=disabled://okx-ws-not-configured`。真实 endpoint 仅显式 env（`NQ_OKX_BASE_URL`/`NQ_OKX_WS_URL` 及 dome/real 前缀）opt-in；显式 env 行为不变；请求期非法 scheme loud fail-closed，不命中真实 OKX，也不被 no-outbound denylist 误判。
  未改 `EnvSafetyValidator` / `EnvSafetyGuardConfiguration` / `NoOutboundExchangeGuardTest` / `NoRealExchangeCredentialPermissionProbePort` / workflow / `application*.yml` / `.env.example` / migration / frontend / research / scripts / deploy。
  本地验证：`OkxRuntimeConfigTest` 4/0/0/0 + `OkxExchangeAdapterBootstrapNoOutboundTest` 1/0/0/0；no-outbound 套件 `NoReal` 1/0/0/0 + `EnvSafety` 8/0/0/0 + `NoOutbound` 3/0/0/0；全量 `mvn -f backend/pom.xml test` **BUILD SUCCESS**（0 fail / 0 error）。静态 grep：真实 host 仅存在于显式 env override 测试与历史/说明文档，默认常量已是 sentinel。
  P0=0；P1=0；P2 = IMPLEMENTED / PENDING CI RUN（原纵深防御项）；P3=1（`application-ci.yml`/`application-paper.yml` 命名差异，非阻断）。
  后续：`NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE-CI-RUN-REVIEW` 采证 → 以 post-freeze addendum 触发 `NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND` 复审 + freeze addendum 后将 P2 标记 CLOSED（依 freeze 卷宗 §11 regression boundary，不静默并入既有 freeze）。Rollback：revert 本实现 commit 即回 frozen 默认（www.okx.com），显式 env 下行为不变，无 runtime/DB/credential/provider/exchange 副作用。
  边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。
- NQ OKX bootstrap / test isolation / no-outbound 专项冻结（2026-06-22）：**NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND = FROZEN / ACCEPTED**。详见 freeze 卷宗 `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_FREEZE.md`。
  冻结对象：OKX bootstrap / test isolation / no-outbound boundary。Review commit `0b9c0b20`，review result PASS / READY FOR FREEZE，review HEAD `e3b12e33`。
  Frozen conclusions：OKX bootstrap 仅为 stub/fallback 边界（非真实交易就绪，stub baseUrl=`http://127.0.0.1`，authenticated 抛 `OKX_ADAPTER_BOOTSTRAP_STUB`）；启动期不访问真实 OKX；test/ci/paper/local 不自动启用真实交易所；permission probe 默认 NoReal / SKIPPED / REAL_EXCHANGE_PROBE_DISABLED；no-outbound guard 覆盖 OKX 及交易所 host 且 fail-closed（CI `no-outbound-guard` job 保留）；`.env.example` placeholder-only；LIVE/AI/DH/RealClient/real provider/real exchange adapter 仍 disabled / not implemented。
  Test evidence：`NoRealExchangeCredentialPermissionProbePortTest` 1/0/0/0 + `EnvSafetyValidatorTest` 8/0/0/0 + `NoOutboundExchangeGuardTest` 3/0/0/0，`BUILD SUCCESS`。
  Findings：P0=0；P1=0；P2=1（非阻断，`OkxRuntimeConfig` 代码级真实 host 默认值未纳入启动期 EnvSafety endpoint 校验，转 backlog **NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE-PLAN**，本轮不修复）；P3=1（非阻断，`application-ci.yml` / `application-paper.yml` 命名差异）。
  Regression boundary：后续改动 OKX runtime config / exchange adapter construction / no-outbound guard / EnvSafetyValidator / profile defaults / permission probe / CI env guard 须重新 review + freeze。Rollback：revert freeze docs commit 即回 PASS / READY FOR FREEZE，无 runtime/DB/credential/provider/exchange 副作用。本轮 docs-only：未改 workflow / backend / Java / TS / Python / `application*.yml` / `.env.example` / migration / frontend / research / scripts / deploy / 测试。
  边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。
- NQ OKX bootstrap / test isolation / no-outbound 专项复审（2026-06-22）：**NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND = PASS / READY FOR FREEZE**。详见 `NQ_TEST_ISOLATION_OKX_BOOTSTRAP_NO_OUTBOUND_REVIEW.md` §13。
  post-CI-security freeze（`8d126f9f`）之后对 OKX bootstrap / adapter / runtime / probe / `EnvSafetyValidator` / `ExchangeNoOutboundGuard` / `OkxRecoveryService` 启动钩子 / `application*.yml` / `.env.example` / `ci.yml` 的独立只读复审。复审 HEAD `e3b12e33`，分支 `dev`，复审前 working tree clean。
  OKX bootstrap：存在启动兜底 stub 工厂（stub baseUrl=`http://127.0.0.1`，authenticated 直接抛 `OKX_ADAPTER_BOOTSTRAP_STUB`）；adapter 构造惰性；启动期不访问真实 OKX。test/ci/paper/local 不自动启用真实连接；no-outbound / no-real 边界成立。permission probe 默认 NoReal（SKIPPED / REAL_EXCHANGE_PROBE_DISABLED，不创建 HTTP client、不访问交易所）。no-outbound guard 覆盖 OKX/Binance/Bybit/Bitget/Gate/Coinbase/Kraken/Crypto/Hyperliquid，denylist fail-closed，CI `no-outbound-guard` job 保留。profile `LIVE/AI/DH/real-provider/real-client/real-exchange` absence => false，test profile `no-outbound=true`，`EnvSafetyValidator` fail-closed。`.env.example` placeholder-only。
  本地只读复核测试：`NoRealExchangeCredentialPermissionProbePortTest` 1/0/0/0 + `EnvSafetyValidatorTest` 8/0/0/0 + `NoOutboundExchangeGuardTest` 3/0/0/0，`BUILD SUCCESS`。
  Findings：P0=0；P1=0；P2=1（`OkxRuntimeConfig` 代码级真实 host 默认值未纳入启动期 EnvSafety endpoint 校验，非阻断，后续单独任务，本轮不修复）；P3=1（`application-ci.yml` / `application-paper.yml` 命名预期差异，非阻断）。
  是否允许进入 freeze：**允许**（本轮 docs-only commit gate，不 freeze、不修复 P2）。本轮为 docs-only：未改 workflow / backend / Java / TS / Python / `application*.yml` / `.env.example` / migration / frontend / research / scripts / deploy / 测试。
  边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。
- GateK CI Security final freeze（2026-06-21）：**GateK CI/security = FROZEN / ACCEPTED**。详见 `NQ_CI_SECURITY_FINAL_FREEZE.md`。
  冻结对象：GateK CI/security baseline（NQ CI Baseline 9-job 管线 + 对应 backend guard/validator/port 测试 + docs/current CI/security 事实源）。
  Batch 1 implemented/green；Batch 2A–2E / 3 / 4B / 4C / 4F-A / 5A / 5B-ENV / 5B-SMOKE 均 **FROZEN / ACCEPTED**；**Batch 5B = CLOSED / ACCEPTED**；4F-B..4F-F / static assertion = OPTIONAL BACKLOG / NOT IMPLEMENTED（NOT BLOCKING）。
  Evidence（只读复核 success）：5B-SMOKE run `27903497008`（headSha `9b467fbc`，9 jobs all success）、5B-ENV run `27876451289`（headSha `8ba140d9`）、docs-only freeze run `27904207910`（headSha `3158e8ad`）。`.github/workflows/ci.yml` 自 `9b467fbc` 未变。
  Regression：后续 workflow / guard / env profile 改动须重新采集 first-run evidence 并单独 freeze。Rollback：revert final freeze docs commit 即回 READY FOR FINAL FREEZE，无 runtime/DB/credential/provider/exchange 副作用。
  边界：No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。
- GateK CI Security final baseline review（2026-06-21）：**PASS / READY FOR FINAL FREEZE**。详见 `NQ_CI_SECURITY_FINAL_BASELINE_REVIEW.md`。
  Batch 矩阵：Batch 1 implemented / first green；Batch 2A–2E / 3 / 4B / 4C / 4F-A / 5A / 5B-ENV / 5B-SMOKE 均 **FROZEN / ACCEPTED**；**Batch 5B = CLOSED / ACCEPTED**；4F-B..4F-F = OPTIONAL BACKLOG / NOT STARTED（不阻断）。
  5B-SMOKE freeze evidence run `27903497008`（NQ CI Baseline / push / headSha `9b467fbc` / success / 9 jobs all success）仍可查；`.github/workflows/ci.yml` 自 `9b467fbc` 未变；HEAD `3158e8ad`，local dev = origin/dev（已同步），working tree clean。
  GateK CI/security = **READY FOR FINAL FREEZE**（本轮不 final freeze、不改 workflow/code/migration、不启动新功能）。No real credential read；No outbound call；No LIVE；No AI；No DH runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。
- GateK CI Security Batch 5B-SMOKE freeze（2026-06-21）：**FROZEN / ACCEPTED**；**Batch 5B = CLOSED / ACCEPTED**。详见 `NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md`。
  冻结对象：implementation commit `9b467fbc`（ci-security-smoke job）+ first run evidence commit `9a98041a`（run `27903497008`，NQ CI Baseline / push / headSha `9b467fbc` / completed / success / 9 jobs all success）。`.github/workflows/ci.yml` 自 `9b467fbc` 后未变。
  ci-security-smoke success / no-outbound-guard success / secret-scan success；smoke 12 tests / 0 fail（NoReal 1 + EnvSafety 8 + NoOutbound 3）；NoReal permission probe remains **SKIPPED**。
  边界：No real credential read；No outbound call；No LIVE；No Paper / DH / AI runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。freeze 无 DB / runtime / credential / provider / exchange 副作用（docs-only）。
  状态：Batch 5B-SMOKE = **FROZEN / ACCEPTED**；Batch 5B = **CLOSED / ACCEPTED**。下一步只做 **NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE_COMMIT_GATE**（提交 freeze docs）；本轮不提交 freeze docs。
- GateK CI Security Batch 5B-SMOKE first run evidence（2026-06-21）：**PASS / READY FOR REVIEW**。详见 `NQ_CI_SECURITY_BATCH_5B_SMOKE_FIRST_RUN_EVIDENCE.md`。
  run = `NQ CI Baseline` / push / dev / **completed / success**，run ID `27903497008`，headSha `9b467fbc21e3ce685572dc3ec84104fd945fa0fb`（= implementation commit `9b467fbc`），createdAt `2026-06-21T11:54:52Z` / updatedAt `2026-06-21T11:56:34Z`，URL `https://github.com/ling5477/nexus-quant/actions/runs/27903497008`。
  9 jobs 全部 success：diff-check / no-outbound-guard / **ci-security-smoke** / backend / postgres-flyway / frontend / frontend-no-backend-e2e / research / secret-scan。
  ci-security-smoke：env-name assertion step 通过；smoke 测试 BUILD SUCCESS —— `NoRealExchangeCredentialPermissionProbePortTest` 1/0/0/0 + `EnvSafetyValidatorTest` 8/0/0/0 + `NoOutboundExchangeGuardTest` 3/0/0/0（合计 12 tests / 0 fail）；NoReal permission probe remains **SKIPPED / REAL_EXCHANGE_PROBE_DISABLED**。
  边界：No real credential read；No outbound call；No LIVE；No Paper / DH / AI runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe。
  状态：First run evidence = **PASS / READY FOR REVIEW**；Freeze = **NOT STARTED**。下一步进入 **NQ_CI_SECURITY_BATCH_5B_SMOKE_FIRST_RUN_REVIEW**，只读复核 evidence；不提交、不 freeze。
- GateK CI Security Batch 5B-SMOKE implementation（2026-06-21）：**IMPLEMENTED / READY FOR REVIEW**。
  结论：Batch 5B-SMOKE = **IMPLEMENTED / READY FOR REVIEW**；Implementation = **DONE / READY FOR REVIEW**；First run evidence = **NOT STARTED**；Freeze = **NOT STARTED**。
  实现：`.github/workflows/ci.yml` 新增独立最小 `ci-security-smoke` job —— 含 CI env-name assertion step（仅按 env 名称做存在性检查，未写入真实凭证 / 真实交易所 env / 真实 endpoint），并复用 `EnvSafetyValidatorTest` / `NoOutboundExchangeGuardTest`（nq-app）+ `NoRealExchangeCredentialPermissionProbePortTest`（nq-infra）的最小安全 smoke。未新增业务测试、未引入真实 adapter / provider / exchange client。
  本地最小验证（未触发 GitHub Actions）：`EnvSafetyValidatorTest` 8/0/0/0 + `NoOutboundExchangeGuardTest` 3/0/0/0（nq-app）+ `NoRealExchangeCredentialPermissionProbePortTest` 1/0/0/0（nq-infra），合计 **12 tests / 0 failures**；NoReal permission probe 保持 **SKIPPED / REAL_EXCHANGE_PROBE_DISABLED**。
  边界：No real credential read；No outbound call；No LIVE；No Paper trading runtime；No DH runtime；No AI runtime；No RealClient；No real provider；No real exchange adapter；No real permission probe；未改 migration / frontend / research / scripts / deploy / `.env.example`。
  下一步：进入 **NQ_CI_SECURITY_BATCH_5B_SMOKE_IMPLEMENTATION_REVIEW**，只读复核 implementation；不提交、不触发 Actions、不进入 freeze。
- GateK CI Security Batch 5B-SMOKE implementation plan（2026-06-21）：**IMPLEMENTATION PLAN READY / READY FOR REVIEW**。详见 `NQ_CI_SECURITY_BATCH_5B_SMOKE_IMPLEMENTATION_PLAN.md`。
  结论：Batch 5B-ENV = **FROZEN / ACCEPTED**；Batch 5B-SMOKE-PREFLIGHT = **REVIEWED / ACCEPTED**；Batch 5B-SMOKE implementation = **NOT STARTED**。本轮只编制 implementation plan，不新增 CI job，不新增测试，不改 workflow / backend / migration / frontend / research / scripts / deploy / `.env.example`，不运行或触发 GitHub Actions。
  下一轮 implementation job 名称定稿为 **ci-security-smoke**；P2 已转化为 implementation execution checklist（EnvSafety / no-outbound coverage 复用，最小 NoReal / placeholder / CI env-name smoke 补齐），P3 job name drift 已关闭。
  下一步：进入 **NQ_CI_SECURITY_BATCH_5B_SMOKE_IMPLEMENTATION_PLAN_REVIEW**，只读复核 implementation plan；不得直接执行 implementation。
- GateK CI Security Batch 5B-SMOKE preflight plan（2026-06-21）：**REVIEWED / ACCEPTED**。详见 `NQ_CI_SECURITY_BATCH_5B_SMOKE_PREFLIGHT_PLAN.md`。
  结论：5B-ENV-A..E 对 planning 的前置门槛已满足：ci/test/paper profile 边界 frozen；`EnvSafetyValidator` fail-closed guard frozen；no-outbound guard 与 EnvSafety guard 兼容 frozen；secret placeholder / `.env.example` 边界 frozen；CI trigger 与 8 job baseline frozen。
  状态：Batch 5B-ENV = **FROZEN / ACCEPTED**；Batch 5B-SMOKE = **PLANNED / NOT STARTED**。本轮未启动 implementation，未改 workflow / Java / TypeScript / Python / migration / frontend / research / scripts / deploy / `.env.example`。
  后续 smoke 必须保持 no-real / no-outbound，只允许 mock / fake / NoReal 路径；不得读取真实 `.env` 或 secret；不得访问 OKX / Binance / Bybit / Gate / Coinbase / Kraken / Crypto / Hyperliquid；不得开启 LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter / real permission probe。
- GateK CI Security Batch 5B-ENV FROZEN（2026-06-21）：**FROZEN / ACCEPTED**。详见 freeze 卷宗 `NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md`。
  freeze evidence = green run `27876451289`（NQ CI Baseline / push / completed / success，headSha `8ba140d96d84b7e2ae5f379043779bfeb925e2fc`，8 jobs（diff-check / no-outbound-guard / backend / postgres-flyway / frontend / frontend-no-backend-e2e / research / secret-scan）all success，`EnvSafetyValidatorTest` 8/0/0/0 + `NoOutboundExchangeGuardTest` 3/0/0/0 实跑非 skip）。
  历史：first run RED（run `27875157176`，根因 = workflow 注入被 `NoOutboundExchangeGuardTest` 禁止的 env 名 `NQ_LIVE_ENABLED`/`NQ_REAL_PROVIDER_ENABLED`/`NQ_REAL_CLIENT_ENABLED`）→ fix-forward（fix commit `8ba140d9`，删除该 3 项 CI job env 注入，no-outbound guard 未放宽、`NoOutboundExchangeGuardTest` 未改）→ rerun GREEN。
  冻结边界：env guard frozen、no-outbound compatibility frozen、secret placeholder boundary frozen、CI trigger boundary frozen（`pull_request:[dev]` + `push:[dev]` + `workflow_dispatch`，8 job 未删，未新增 secret）。本 freeze 为 docs-only：未读取真实 .env / secrets / credentials；未调用真实交易所；未新增 HTTP client / migration / API；未改 workflow / 代码 / 测试 / 配置 / frontend / research / scripts / deploy；未启动 5B-SMOKE；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter / real permission probe 均未开启或实现。
  5B-ENV freeze-time 记录中 Batch 5B-SMOKE 仍为 **STILL BLOCKED**；当前已由后续 preflight + implementation plan 推进为 **IMPLEMENTATION PLAN READY / READY FOR REVIEW**，但 implementation 仍 **NOT STARTED**。
- NQ docs **current 目录物理瘦身 Round 3 final freeze** completed（2026-06-19）：**PASS / ACCEPTED / FROZEN**。详见 `NQ_DOCS_CURRENT_CLEANUP_R3_FINAL_FREEZE.md`。
  冻结 `docs/current` 物理瘦身结果（R1 commit `ca77460f` + R2 review commit `d4095ded`，二者一致）：**NQ Docs Current Cleanup = FROZEN / ACCEPTED / CLOSED**；Round = 3 / 3；**Round 4 = NOT ALLOWED**。cleanup-result 基线：current root markdown 96 → **46**；moved out of current = **51**（governance 17 + GateJ stub 14 + CI stub 20）；known compatibility residual = **3**；P3 informational = **2**；historical evidence deleted = 0；code/workflow/migration changed = 0。
  计数口径：`current markdown count = 46` 为 physical-reduction cleanup-result 基线；R2/R3 的 review/freeze audit-trail 文档按设计保留在 current，使 live `git ls-files docs/current/*.md` = **48**（R3 提交后），如实可复核，二者不矛盾。
  移出对象位置冻结：governance 17 → `docs/evidence/governance/`（18 .md，R1 commit 17 个均 R100 byte-identical 纯 rename）；GateJ stub 14 → `docs/evidence/compatibility/gatej-current-stubs/`（15 .md，canonical 链接 `../../../gates/gate-j/` 0 broken）；CI stub 20 → `docs/evidence/compatibility/ci-current-stubs/`（21 .md，canonical 链接 `../../ci/` 0 broken）。canonical GateJ（28）/ CI evidence（20）/ CI authority（2）/ RUNBOOK / G1～G6 冻结对象 / docs/gates·evidence-ci·baselines·archive·.agents·templates / workflow / 代码 / migration 全部未改。
  3 个 BLOCKED 文件冻结为 **accepted known compatibility residual**：`GATEJ_API_PLAN.md` / `GATEJ_DB_PLAN.md` / `GATEJ_TEST_PLAN.md`（DIVERGED_INBOUND_LINK，入链 API.md:233 / DB_SCHEMA.md:375 / TESTING.md:3579，均同目录解析正常，canonical 在 `docs/gates/gate-j/`）；未来处理须单独开小型 link-rewrite proposal，不开 Round 4。两个 P3（governance inline-code 路径示例 / 历史 prose 提及）为 informational，非 broken link，不修复、不延长到 Round 4。Findings P0/P1/P2=0，P3=2。
  NQ GateK CI mainline = **COMPLETED / ACCEPTED**；G1～G6 governance baseline 仍为历史参考；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。
- NQ docs **current 目录物理瘦身 Round 2 review** completed（2026-06-19）：**PASS / ACCEPTED**。详见 `NQ_DOCS_CURRENT_CLEANUP_R2_REVIEW.md`。
  审查 R1 implementation commit `ca77460f`：current root tracked .md = **46**（提交后核验，与 R1 一致）；governance evidence 17（R1 commit 中 17 个均 **R100** byte-identical 纯 rename，正文零改写）→ `docs/evidence/governance/`（18 .md）；GateJ stub 14 → `docs/evidence/compatibility/gatej-current-stubs/`（15 .md）；CI stub 20 → `docs/evidence/compatibility/ci-current-stubs/`（21 .md）。moved stub canonical 链接逐文件解析 **0 broken**；fragment 入链 = 0；无 live 链接指向 moved 文件旧 current 路径。
  R1 commit 未触碰任何禁止范围（`docs/gates/**`、`docs/evidence/ci/**`、`docs/baselines/**`、`docs/archive/**`、`.agents/**`、`templates/**`、`.github/workflows/ci.yml`、backend/frontend/research/scripts/deploy/migration/测试/依赖均 0 改动）；canonical GateJ 28、CI evidence 20、CI authority 2、RUNBOOK 均保留未改。
  **3 个 BLOCKED 文件接受为 known compatibility residual**：`GATEJ_API_PLAN.md` / `GATEJ_DB_PLAN.md` / `GATEJ_TEST_PLAN.md`（DIVERGED_INBOUND_LINK，入链 API.md/DB_SCHEMA.md/TESTING.md），保留在 current，canonical 仍在 `docs/gates/gate-j/`；R2 不强行移动、不改写其入链。Findings P0/P1/P2=0，P3=2（governance inline-code 路径示例 / STATUS·WORKLOG 历史 prose 提及，均非断链，不阻断冻结）。
  **NQ Docs Current Cleanup = ACCEPTED / READY FOR FINAL FREEZE**；Round = 2 / 3（R3 = FINAL FREEZE）；current markdown = 46；moved = 51；known compatibility residual = 3；未删除历史证据；未改代码/workflow/migration；G1～G6 governance baseline 仍为历史参考。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。
- NQ docs **current 目录物理瘦身 Round 1 implementation** completed（2026-06-19）：**PASS / READY FOR REVIEW**。详见 `NQ_DOCS_CURRENT_CLEANUP_R1_IMPLEMENTATION.md`（已随本轮从 current 移出的 governance/final-freeze 证据正文现归档于 `docs/evidence/governance/`）。
  把 `docs/current` 从“安全保留态”收敛为“真正 current 控制态”：`docs/current` 根目录 Markdown **96 → 46**（移出 51 + 新增 1 报告）。移出明细：governance evidence **17** → `docs/evidence/governance/`；GateJ 旧路径 compatibility stub **14** → `docs/evidence/compatibility/gatej-current-stubs/`；CI 旧路径 compatibility stub **20** → `docs/evidence/compatibility/ci-current-stubs/`。全部用 `git mv` 保留历史，未删除任何历史正文。
  BLOCKED = **3**：`GATEJ_API_PLAN.md` / `GATEJ_DB_PLAN.md` / `GATEJ_TEST_PLAN.md` 因入链位于受保护 DIVERGED 活文档（`API.md` / `DB_SCHEMA.md` / `TESTING.md`），标记 `BLOCKED_PER_FILE / DIVERGED_INBOUND_LINK`，保留在 current（canonical 仍在 `docs/gates/gate-j/`）。三组移出对象 fragment 入链全仓 = 0。
  stub 自身 canonical 链接已深度补偿（GateJ `../../../gates/gate-j/`、CI `../../ci/`）；新增 3 个导航 README；重写 `docs/current/README.md` 为真正 current 入口页；更新 `docs/README.md` 历史证据位置。未修改 canonical GateJ（`docs/gates/gate-j/**`）、canonical CI evidence（`docs/evidence/ci/**`）、G1 五份冻结对象正文、`.github/workflows/ci.yml`、backend/frontend/research/scripts/deploy/migration/测试/依赖。
  **NQ Docs Current Cleanup = IMPLEMENTED / READY FOR REVIEW**；Round = 1 / 3（R2 = REVIEW，R3 = FINAL FREEZE）；G1～G6 governance baseline 仍为历史参考；未删除历史证据；未改代码/workflow/migration。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。
- NQ documentation governance **final freeze review** completed（2026-06-19）：**PASS / ACCEPTED / FROZEN**。详见 `docs/evidence/governance/NQ_DOCS_GOVERNANCE_FINAL_FREEZE_REVIEW.md`（R1 已从 current 移出归档）。
  确认 NQ post-CI 文档治理收口链路 **G1～G6 全部闭合**并冻结为最终文档治理基线：**NQ Docs Governance Consolidation = FROZEN / ACCEPTED**。G1 五份冻结对象 zero drift（唯一 current authority 模型 / 历史证据导航模型 / 迁移唯一来源 / 278-283 快照口径未回写）；G2 current-control 语义基线（GateJ=historical、GateK CI mainline=COMPLETED、5A≠authenticated/backend、5B/4F/static 未误标 completed、Rule 16 五级优先级完整、API/DB_SCHEMA GateI 相对链接无 malformed）；G3 17 个 GateJ stub 指向 `../gates/gate-j/`、17 canonical 存在、fragment 入链 0、RUNBOOK 仍 current-control、9 份 DIVERGED 未误处理；G4 20 个 CI canonical evidence 在 `docs/evidence/ci/`、20 个 source stub 指向 `../evidence/ci/`、2 份 CI current authority 完整、CI_BASELINE_INDEX / evidence README 仅导航；G5 executable candidates=0 / implementation=SKIPPED；G6 DELETE_CANDIDATES=0 / deletion list 未创建。
  最终冻结**不是** current-control 文档 blob lock：`STATUS / TESTING / WORKLOG / ROADMAP / README` 后续仍可追加真实状态，但不得破坏已冻结的权威、证据链、兼容路径、目录语义与默认不删除原则。失效条件见 final freeze review §6（修改 G1 基线 / 改写 GateJ canonical / 改写 CI evidence 映射 / current authority 降级 / backlog 误写 completed / 删除移动归档保留证据 / 创建 deletion list / 混入代码 workflow migration 变更）。
  本轮只新增 `NQ_DOCS_GOVERNANCE_FINAL_FREEZE_REVIEW.md` 并更新 STATUS / TESTING / WORKLOG；未修改 G1～G6 冻结对象、docs/gates/archive/evidence/baselines、.agents/templates、workflow、backend、frontend、research、scripts、deploy、migration、测试或依赖。
  **NQ Docs Governance Consolidation = FROZEN / ACCEPTED**；**G1～G5 = FROZEN / ACCEPTED**；**G5 executable candidates = 0**；**G5 implementation = SKIPPED / NOT APPLICABLE**；**G6 deletion batch = DEFAULT EMPTY / ACCEPTED**；**DELETE_CANDIDATES = 0**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance **G6 default-empty deletion review** completed（2026-06-19）：**PASS / ACCEPTED**。详见 `NQ_DOCS_G6_DEFAULT_EMPTY_DELETION_REVIEW.md`。
  确认本轮文档治理不删除任何文档，删除批次默认为空：**`DELETE_CANDIDATES = 0`**，未创建 deletion list，未删除、移动、重命名、归档、stub 化或复制任何文档。删除候选唯一合法来源是受控 deletion proposal，本治理周期未发起；冻结 Migration Map 明确无 `DELETE NOW`，`ARCHIVE_CANDIDATE` / `FUTURE_MOVE_CANDIDATE` / superseded 均不等于当前可删除；不得由 “G5 executable candidates = 0” 推导删除。
  保留对象全部核验存在且未改动：`docs/gates/**`（gate-j 28 files）、`docs/archive/**`（22 files）、`docs/evidence/ci/**`（21 files）、`docs/baselines/CI_BASELINE_INDEX.md`、两份 CI current authority（`NQ_CI_BASELINE_PLAN.md` / `NQ_CI_SECURITY_GUARD_PLAN.md`）、`RUNBOOK.md`、G3 17 个 GateJ stub、G4 20 个 CI source stub、9 份 DIVERGED current 活文档、G1～G5 plan/review/freeze/implementation 证据、含 P2/P3 residual / backlog 的记录。
  G6 不等于 archive cleanup / repo size cleanup / docs pruning / 删除 superseded current path；当前治理周期默认不删除。未来删除必须另起 deletion proposal，逐文件审查、逐文件回滚、逐文件证明不破坏证据链和链接兼容。
  本轮只新增 `NQ_DOCS_G6_DEFAULT_EMPTY_DELETION_REVIEW.md` 并更新 STATUS / TESTING / WORKLOG；未修改 G1～G5 冻结对象、docs/gates/archive/evidence/baselines、.agents/templates、workflow、backend、frontend、research、scripts、deploy、migration、测试或依赖。
  **G1～G5 = FROZEN / ACCEPTED**；**G5 executable candidates = 0**；**G5 implementation = SKIPPED / NOT APPLICABLE**；**G6 deletion batch = DEFAULT EMPTY / ACCEPTED**；**DELETE_CANDIDATES = 0**；**NQ Docs Governance Consolidation = READY FOR FINAL FREEZE REVIEW**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。
- NQ documentation governance **G5 freeze review** completed（2026-06-19）：**PASS / ACCEPTED / FROZEN**。详见 `NQ_DOCS_G5_FREEZE_REVIEW.md`。
  冻结 G5 directory closure preflight / review 的 no-op baseline：Migration Map 中可执行 G5 候选（`recommended_action = FUTURE_MOVE_CANDIDATE` 且 `migration_batch = G5`）为 **0**；唯一 `FUTURE_MOVE_CANDIDATE` 为 §1D 且 batch = G4；§1B / §1C 的 `G5 可选` 只是说明性文字，batch 均为 `NONE`，不得扩展为候选。
  空逐文件矩阵、`ELIGIBLE_FOR_G5_IMPLEMENTATION = 0`、`BLOCKED_PER_FILE = 0`、`RETAIN_IN_PLACE = 0` for G5 candidates 均冻结为正确结果。G5 implementation = **SKIPPED / NOT APPLICABLE**；无 implementation commit、无 moved files、无 redirected files、无 created target directories、无 deletion candidates。
  本轮只新增 `NQ_DOCS_G5_FREEZE_REVIEW.md` 并更新 STATUS / TESTING / WORKLOG；未修改 Migration Map、G1～G4 冻结对象、docs/gates/archive/.agents/templates、workflow、backend、frontend、research、scripts、deploy、migration、测试或依赖。
  **G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED**；**G4 CI evidence routing = FROZEN / ACCEPTED**；**G5 directory closure preflight = FROZEN / ACCEPTED**；**G5 executable candidates = 0**；**G5 implementation = SKIPPED / NOT APPLICABLE**；**G6 deletion batch = READY FOR DEFAULT-EMPTY REVIEW**。
  NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。
- NQ documentation governance **G5 directory closure preflight review** completed（2026-06-19）：**PASS / ACCEPTED**。详见 `NQ_DOCS_G5_PREFLIGHT_REVIEW.md`。
  审查 G5 preflight commit `8917d99d`：Migration Map 中可执行 G5 候选（`recommended_action = FUTURE_MOVE_CANDIDATE` 且 `migration_batch = G5`）为 **0**；唯一 `FUTURE_MOVE_CANDIDATE` 为 §1D 且 batch = G4；§1B / §1C 的 `G5 可选` 只是说明性文字，batch 均为 `NONE`，不得扩展为候选。
  空逐文件矩阵、ordinary/fragment 入链对象 0、target conflict 对象 0、redirect-first 设计对象 0 均为正确结果。最新 preflight commit 只触达 4 个允许文件；G1～G4 冻结对象、docs/gates/archive/.agents/templates、workflow、code、migration zero drift。
  **G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED**；**G4 CI evidence routing = FROZEN / ACCEPTED**；**G5 directory closure preflight = ACCEPTED / READY FOR FREEZE REVIEW**；**G5 executable candidates = 0**；**G6 deletion batch = NOT STARTED / DEFAULT EMPTY**。
  NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。
- NQ documentation governance **G4 freeze review** completed（2026-06-19）：**PASS / ACCEPTED / FROZEN**。冻结 G4 CI evidence routing semantic / structural baseline：Migration Map §1D 22 个候选可追溯；20 个 canonical historical evidence 与 783bfa68^ 原 source blob 20 / 20 一致；20 个 old-path compatibility stub 保留原 H1、relative canonical link、NON_AUTHORITATIVE / SUPERSEDED_BY_CI_EVIDENCE_RECORD 与 G4 routing 说明；source fragment 入链 0；2 个 current authority（NQ_CI_BASELINE_PLAN.md、NQ_CI_SECURITY_GUARD_PLAN.md）完整正文与 authority 地位保留；docs/evidence/ci/README.md 与 docs/baselines/CI_BASELINE_INDEX.md 仅为导航，不取代 current status 或 current authority；G1 五份冻结对象、G2/G3 语义、docs/gates/archive/.agents/templates、workflow/code/migration zero drift。详见 NQ_DOCS_G4_FREEZE_REVIEW.md。**G4 CI evidence routing = FROZEN / ACCEPTED**；**G5 = READY FOR IMPLEMENTATION**；**G6 = NOT STARTED**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。

- NQ documentation governance **G4 CI evidence routing review** completed（2026-06-19）：**PASS / ACCEPTED**。审查 implementation commit `783bfa68`：Migration Map §1D 22 个候选可追溯；20 个 routed canonical evidence 与 `783bfa68^` 中原 source blob 20 / 20 一致；20 个 old-path stub 合规；2 个 current authority（`NQ_CI_BASELINE_PLAN.md`、`NQ_CI_SECURITY_GUARD_PLAN.md`）完整保留，状态为预期 `BLOCKED_PER_FILE / CURRENT_AUTHORITY`；`docs/evidence/ci/` 恰有 20 个 routed canonical evidence；`CI_BASELINE_INDEX.md` 仅为导航索引，不取代 current authority 或 `STATUS.md` current-status authority；fragment 入链 0；G1/G2/G3/禁止范围 zero drift。详见 `NQ_DOCS_G4_REVIEW.md`。**G4 CI evidence routing = ACCEPTED / READY FOR FREEZE REVIEW**；**G5~G6 = NOT STARTED**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance **G4 CI evidence routing** completed（2026-06-19）：**IMPLEMENTED / READY FOR REVIEW**。只从 G1 冻结的 `NQ_DOCS_MIGRATION_MAP.md` §1D 提取 G4 候选：22 total，20 `REDIRECT_STUB_CREATED`，2 `BLOCKED_PER_FILE / CURRENT_AUTHORITY`（`NQ_CI_BASELINE_PLAN.md`、`NQ_CI_SECURITY_GUARD_PLAN.md` 保留 current authority pointer）。20 份 historical CI evidence 已字节一致归位到 `docs/evidence/ci/`，旧 `docs/current/` path 保留 non-authoritative compatibility stub；新增 `docs/evidence/ci/README.md`、`docs/baselines/CI_BASELINE_INDEX.md`、`NQ_DOCS_G4_CI_EVIDENCE_ROUTING.md`。G1 五份冻结对象未修改；G2/G3 冻结语义未修改；GateJ 17 stub、RUNBOOK、9 份 DIVERGED 未修改；`docs/gates/**` / `docs/archive/**` / `.agents/**` / `templates/**` / workflow / code / migration 未修改。**G4 CI evidence routing = IMPLEMENTED / READY FOR REVIEW**；**G5~G6 = NOT STARTED**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance **G3 freeze review** completed（2026-06-19）：**PASS / ACCEPTED / FROZEN**。冻结 G3 GateJ redirect-first compatibility-path 语义/结构基线：17 个 current stub 必须持续存在并仅作为 `NON_AUTHORITATIVE / SUPERSEDED_BY_CANONICAL_GATEJ_RECORD` 兼容入口，canonical target 固定为 `docs/gates/gate-j/<same filename>`，权威全文仅位于 gate-j。核验：17 / 17 stub 模板 PASS；canonical blob `HEAD^` / `HEAD` / worktree zero drift；fragment 入链 0；RUNBOOK 保持 `INDEX_AS_CURRENT_CONTROL / RETAIN_IN_PLACE`；9 份 DIVERGED 未被 stub 化；G1 五份冻结对象 diff=0；`docs/gates/**` / `docs/archive/**` / `.agents/**` / `templates/**` / workflow / code / migration diff=0。G3 freeze 是 compatibility-path semantic / structural baseline freeze，不是 `STATUS.md` / `TESTING.md` / `WORKLOG.md` 的 blob lock。详见 `NQ_DOCS_G3_FREEZE_REVIEW.md`。**G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED**；**G4 = READY FOR IMPLEMENTATION**；**G5~G6 = NOT STARTED**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance **G3 GateJ redirect-first consolidation review** completed（2026-06-19）：**PASS / ACCEPTED**。审查 implementation commit `102c824d`：17 / 17 current redirect stub 合规，0 `BLOCKED_PER_FILE`，pre-conversion blob（`HEAD^:docs/current/<file>`）与 canonical gate-j blob 17 / 17 一致，canonical gate-j worktree zero drift，`<name>.md#` fragment 入链 0，G3 implementation report 完整。`RUNBOOK.md` 未修改；9 份 DIVERGED 未被 redirect/stub 化（`STATUS.md` / `TESTING.md` / `WORKLOG.md` 仅保留允许的治理记录）；G1 五份冻结对象 diff=0；`docs/gates/**` / `docs/archive/**` / `.agents/**` / `templates/**` / workflow / code / migration diff=0。详见 `NQ_DOCS_G3_REVIEW.md`。**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 GateJ redirect-first consolidation = ACCEPTED / READY FOR FREEZE REVIEW**；**G4~G6 = NOT STARTED**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance **G3 GateJ redirect-first consolidation** completed（2026-06-19）：**IMPLEMENTED / READY FOR REVIEW**。将 `docs/current/` 中 17 份 GateJ byte-identical 非权威副本就地收敛为 redirect-first 兼容 stub（保留路径、未删除/移动/重命名）：**17 REDIRECT_STUB_CREATED / 0 BLOCKED_PER_FILE**（先决核验：HEAD blob-identity 17/17、canonical 存在 17/17、`#fragment` 入链 0）。**权威全文永久保留在 `docs/gates/gate-j/`（byte-for-byte，PRES_OK）**；每份 stub 指向 `../gates/gate-j/<file>` 并标 `NON_AUTHORITATIVE / SUPERSEDED_BY_CANONICAL_GATEJ_RECORD`。`RUNBOOK.md` 未触碰；9 份 DIVERGED 活文档未做 redirect/stub 处理，其中 `STATUS.md`、`TESTING.md`、`WORKLOG.md` 仅按本任务允许更新范围追加 G3 状态、验证与工作日志记录；G1 五份冻结对象 diff=0；`docs/gates/**`/`docs/archive/**`/`.agents/**`/`templates/**`/code/workflow diff=0；未删除/移动/重命名任何文件。详见 `NQ_DOCS_G3_GATEJ_REDIRECT_CONSOLIDATION.md`。**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 = IMPLEMENTED / READY FOR REVIEW**；**G4~G6 = NOT STARTED**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance **G2 freeze review** completed（2026-06-19）：**PASS / ACCEPTED / FROZEN**。G2 冻结为 **semantic baseline freeze**（语义修复断言 + 导航模型 + Rule 16 优先级 + current-control link hygiene），**不是**把持续更新的 current-control 文档锁成 immutable blob —— `STATUS/WORKLOG/TESTING/ROADMAP/README` 仍可正常追加带日期记录与导航，仅“恢复已修复缺陷或越过冻结证据边界”才使其失效（失效条件 8 项 / 允许维护 6 项见 `NQ_DOCS_G2_FREEZE_REVIEW.md` §6/§7）。锚点 G2 repair `3c1f5ec0` / accept `7de61114`；G1 五份冻结对象自 `7eb7ae53` 零 drift；docs/gates/archive/.agents/templates/code/workflow 跨 G2 零 drift。核验：GateJ 历史/冻结证据入口语义、GateK CI mainline COMPLETED、5A FROZEN（显式非 authenticated/backend coverage）、5B-ENV/5B-SMOKE/4F/static 未误标 completed、G1 FROZEN、G2 未提前误写 FROZEN、Rule 16 五级完整、两处链接 `../gates/gate-i/` 可解析且 malformed=0、冻结快照 4 处 `./GATEI_*` 未改、evidence 导航齐全且不回写 278/283。P0/P1/P2=0，P3=3（evidence-index 物理 section / docs/README.md evidence 枚举至 G2 implementation 后续受控补充 / STATUS 早期 as-of-time 日志非 drift）。**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 = READY FOR IMPLEMENTATION**；**G4~G6 = NOT STARTED**。详见 `NQ_DOCS_G2_FREEZE_REVIEW.md`。**NQ Docs Governance Plan = FROZEN FOR G1 BASELINE**；**G1 authority/evidence index = FROZEN / ACCEPTED**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance **G2 review** completed：**PASS / ACCEPTED**。只读评审 G2 commit `3c1f5ec0`。P0=0 / P1=0 / P2=0 / P3=2（信息性：evidence-index 物理治理小节留待后续受控修订；STATUS 早期里程碑条目的 as-of-time “mainline IN PROGRESS” 属追加式历史日志，已被顶部条目取代，非 drift、不改写）。核验通过：GateJ 导航改为历史/冻结证据入口（权威 gate-j，17 份 NON_AUTHORITATIVE/FUTURE_SUPERSEDE_CANDIDATE/G3）；当前状态口径正确（GateK CI mainline COMPLETED、5A FROZEN 且明确非 authenticated/backend coverage、5B-ENV P1/5B-SMOKE BLOCKED/4F backlog/static、G1 FROZEN、G2 仅 IMPLEMENTED 未误写 FROZEN）；DOC_RULES Rule 16 五级优先级完整无冲突；两处 malformed link 修复为 `../gates/gate-i/` 且目标可解析、malformed=0；冻结快照 4 处 `./GATEI_*` 未改写、仅加兼容入口；governance evidence 导航齐全且标 HISTORICAL_EVIDENCE/RETAIN_IN_PLACE 不计入 278/283；G1 五份冻结对象零 drift；docs/gates/archive/.agents/templates/code/workflow diff=0；G3~G6 未启动。**G2 current-control drift repair = ACCEPTED / READY FOR FREEZE REVIEW**。详见 `NQ_DOCS_G2_REVIEW.md`。**NQ Docs Governance Plan = FROZEN FOR G1 BASELINE**；**G1 authority/evidence index = FROZEN / ACCEPTED**；**G3~G6 = NOT STARTED**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance **G2 current-control drift repair** completed：**G2 = IMPLEMENTED / READY FOR REVIEW**。修复当前控制层导航/状态/规则/可修链接漂移：`docs/README.md` 把 GateJ 计划/工作单从 current 入口改为历史/冻结证据入口（权威指向 `docs/gates/gate-j/`）、§当前边界补全 GateK CI mainline COMPLETED / 5A FROZEN（仅 no-backend smoke，非 authenticated/backend coverage）/ 5B-ENV P1 NOT STARTED / 5B-SMOKE BLOCKED / 4F-B~4F-F backlog / static assertion / G1 FROZEN / G2；`docs/DOC_RULES.md` 新增规则 16 以 5 级优先级收敛“不重复 vs 迁移或复制”冲突；`docs/current/ROADMAP.md` 同步 CI mainline 与 Batch 状态（修复旧“Batch 4 PLAN ONLY / Batch 5 PENDING”过期口径）；修复 `docs/current/API.md`/`DB_SCHEMA.md` 各 1 处 malformed 前导 `/` 链接为相对 `../gates/gate-i/`；冻结快照 4 处历史链接不改写，仅在 `docs/README.md` 增兼容入口；新增 Documentation Governance Evidence 导航。**未移动/删除/重命名/归档任何文档，未改 G1 五份冻结对象，未改冻结快照正文/链接，未回写 278/283。** 详见 `NQ_DOCS_G2_CURRENT_CONTROL_REPAIR.md`。**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 = IMPLEMENTED / READY FOR REVIEW**；**G3~G6 = NOT STARTED**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance **G1 freeze review** completed：**PASS / ACCEPTED / FROZEN**。冻结锚点 HEAD `a0157973`，5 份冻结对象（`NQ_DOCS_GOVERNANCE_PLAN.md` / `NQ_DOCS_AUTHORITY_INDEX.md` / `NQ_DOCS_EVIDENCE_INDEX.md` / `NQ_DOCS_MIGRATION_MAP.md` / `NQ_DOCS_G1_IMPLEMENTATION.md`）自 G1 implementation commit `c3a2cf83` 起零 drift（blob 见 `NQ_DOCS_G1_FREEZE_REVIEW.md` §1）。**计数边界冻结**：原始治理基线 **278**（不可变）、G1 implementation snapshot **283**（= 278 + 5 增量，不可变）；review/freeze evidence（`NQ_DOCS_G1_REVIEW.md`、`NQ_DOCS_G1_FREEZE_REVIEW.md` 及后续同类）按 standing rule 归 **HISTORICAL_EVIDENCE / RETAIN_IN_PLACE**，**不回写 278/283**（live 工作树计数单调增长但基线不变，防递归）。核验通过：authority index 14 领域唯一权威无并列；GateJ blob-identical 18 / superseded 17 / RUNBOOK 第18份 RETAIN_IN_PLACE / 9 DIVERGED 分层事实；evidence index 9 类入口齐全、backlog 未误标 completed、只链接不复制；migration map 10 字段齐全、gates/archive/.agents/templates 全 RETAIN_IN_PLACE/NONE/NOT_APPLICABLE、无 DELETE NOW、零 orphan；governance commit 未触碰 docs/gates、docs/archive、code、workflow。P0/P1/P2=0，P3=2（evidence-index 物理列出 review/freeze 文档留待 G2；future 目标目录尚不存在属预期）。**NQ Docs Governance Plan = FROZEN FOR G1 BASELINE**；**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 = READY FOR IMPLEMENTATION**；**G3~G6 = NOT STARTED**。详见 `NQ_DOCS_G1_FREEZE_REVIEW.md`。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance **G1 review** completed：**PASS / ACCEPTED**。只读评审 4 份 G1 文档（authority/evidence index、migration map、G1 implementation）+ 7 份 G1 更新。P0=0 / P1=0 / P2=0 / P3=3（信息性 by-design：未来目标目录 `docs/evidence|baselines` 尚不存在属预期、DH 外部审计报告跨仓指针、run-id 子串非旧口径）。核验通过：283 份当前对象（278 基线 + 5 增量）**唯一无冲突治理处理、零 orphan**；GateJ 模型 **blob-identical 18 / superseded 17 / RUNBOOK 第18份 RETAIN_IN_PLACE / 9 DIVERGED 分层事实**；authority index 14 领域唯一权威无并列；evidence index 9 类入口齐全、只链接不复制、backlog 未误标 completed；migration map 10 字段齐全、gates/archive/.agents/templates 全 RETAIN_IN_PLACE/NONE/NOT_APPLICABLE、无 DELETE NOW；governance commit 对禁止范围 diff 为空。**NQ Docs Governance Plan = ACCEPTED AS IMPLEMENTATION BASELINE**；**G1 authority/evidence index = ACCEPTED / READY FOR FREEZE REVIEW**；**G2~G6 = NOT STARTED**。详见 `NQ_DOCS_G1_REVIEW.md`。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance **G1 authority/evidence index** completed：**G1 = IMPLEMENTED / READY FOR REVIEW**。新增 `NQ_DOCS_AUTHORITY_INDEX.md`（14 领域唯一权威 + 辅证 + 历史证据）、`NQ_DOCS_EVIDENCE_INDEX.md`（GateJ freeze / CI Batch 1~5A / 4C / 4F-A / backlog-residual / DB / credential / NQ-DH 证据入口，只链接不复制）、`NQ_DOCS_MIGRATION_MAP.md`（逐文件 / 等效逐文件清单覆盖 278 基线 md/txt，recommended action 仅 5 取值、无 DELETE NOW、ARCHIVE_CANDIDATE≠可立即删除）、`NQ_DOCS_G1_IMPLEMENTATION.md`（计数 / P2 收敛 / G1~G6 边界）。**P2 全部 CLOSED**：P2-1 计数订正为 git 实测（总数 **278** / current 根 **75** / frontend **3** / archive **21** / gates **152**，废弃 277/290/15/22/74）；P2-2 固定 blob-identical **18** / superseded 收敛候选 **17** / `RUNBOOK.md` 第 18 份 RETAIN_IN_PLACE、9 份 DIVERGED 为分层事实非删除候选；P2-3 对 `docs/gates`/`docs/archive`/`.agents`/`templates` 统一标注 RETAIN_IN_PLACE / NONE / NOT_APPLICABLE。**本轮未移动/删除/重命名/归档任何文档**，未改写冻结快照文本或链接。`docs/README.md`/`docs/DOC_RULES.md` 仅新增治理入口与 retain-first 原则（状态/导航漂移与既有规则矛盾留待 G2）。**NQ Docs Governance Plan = P2 CONDITIONS CLOSED / READY FOR G1 REVIEW**；**G1 = IMPLEMENTED / READY FOR REVIEW**；**G2~G6 = NOT STARTED**。详见 `NQ_DOCS_G1_IMPLEMENTATION.md`。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance inventory & plan **review** completed：**PASS / ACCEPTED WITH P2 CONDITIONS**。只读评审 `NQ_DOCS_GOVERNANCE_PLAN.md`，P0=0 / P1=0 / P2=3 / P3=2。结构性属性（冻结证据保护、历史链接 redirect、9 份 DIVERGED 分层事实、blob-identical 仅列未来收敛候选并保留 gate-j 权威副本、删除单独显式默认空）全部核验通过。git 实测纠正计划计数：总数 **278**（计划 277）、`docs/current/frontend` 实 **3**（计划 15）、`docs/archive` 实 **21**（计划 22）、`docs/current` 根 **75**（计划 74）；`docs/current` vs `gate-j` blob 比对 = **18 IDENTICAL / 9 DIVERGED**，其中 superseded duplicate = **17**（计划全文称 16，`RUNBOOK.md` 第 18 份保留为 CURRENT_CONTROL）；6 处 broken link 与计划处理一致（0 orphan，分类覆盖完整）。**NQ Docs Governance Plan = ACCEPTED AS IMPLEMENTATION BASELINE**；**G1 authority/evidence index = READY FOR IMPLEMENTATION**（条件：G1 内用 git-verified 计数与 17 份去重列表收敛 P2-1/P2-2/P2-3）；**G2~G6 = NOT STARTED**。本轮未移动/删除/重命名任何文档，未改历史 freeze/review 事实结论。详见 `NQ_DOCS_GOVERNANCE_PLAN_REVIEW.md`。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- NQ documentation governance inventory & plan completed：**PASS / READY FOR REVIEW**（documentation governance plan ready，**未收口**）。只读盘点全仓 277 份 md/txt：`docs/current` 根 74 份、`docs/current/` 共 89、`docs/gates` 152、`docs/archive` 22。建立分类矩阵（CURRENT_CONTROL / CANONICAL_BASELINE / HISTORICAL_EVIDENCE / ARCHIVE_CANDIDATE / DUPLICATE_OR_SUPERSEDED）、authority matrix、目标结构（current/baselines/evidence/archive）与迁移映射、不可删除冻结证据清单、G1~G6 实施批次（先索引后移动、历史链接先 redirect、删除单独显式默认不删）。关键发现：16 份 GateJ 过程/计划文档在 `docs/current/` 与 `docs/gates/gate-j/` blob 完全一致（DUPLICATE_OR_SUPERSEDED candidate）；`docs/README.md` 导航停留 GateJ 口径、未含 GateK/CI（状态漂移）；22 份 `NQ_CI_*` 散落根目录无唯一 evidence index；6 处 broken markdown 链接（2 处 current malformed 前导 `/`，4 处在冻结 gate-h/gate-j 快照内不改事实）。**本轮未移动/删除/重命名任何文档，未改历史 freeze/review 事实结论。** 详见 `NQ_DOCS_GOVERNANCE_PLAN.md`。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- GateK CI Batch 5A no-backend E2E freeze review completed：**PASS / ACCEPTED / FROZEN**。**Batch 5A = FROZEN / ACCEPTED**：两次 immutable GitHub Actions green run 一致且工作流/配置零 drift —— 首跑 run `27750279096`（commit `861c3e78`，job `82098741200`，`4 passed (7.3s)`）+ freeze run `27750976632`（commit `3d26c84d` first-run-review docs-only，push→dev，completed/success，job `82101090359`，`4 passed (6.8s)`）。零 drift 证据：`.github/workflows/ci.yml` blob `6941d60ade2bfce456e203f708b633e595285178` 与 `frontend/playwright.ci.config.ts` blob `d039fe82fbf7db6f55c3e6fc089bac59a2fe9014` 在两 commit 完全一致；两 commit 间改动仅 5 个 `docs/current` 文件（docs-only）。Run 2 核验：permissions Contents: read / Metadata: read；Node 22.22.3；npm ci added 183；Chromium 1208 only（Firefox/Webkit 0）；vite build 成功；命令显式四 spec，`Running 4 tests using 1 worker` / `4 passed`，其余 23 spec 0 次、无 skip-as-pass；`/api`/jdbc/postgres/flyway/docker/loginToConsole/seed/storageState/okx/binance/upload-artifact 命中 0；无 service 容器，cleanup `rm -rf` 运行。bootstrap（checkout/Node/npm/Chromium CDN）属引导网络访问，业务出站 0。冻结基线 = 上述两 blob + 四 spec allowlist；任何改动使冻结失效需重审。详见 `NQ_CI_FRONTEND_E2E_5A_FREEZE_REVIEW.md`。**Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**；**Batch 5B-SMOKE = BLOCKED BY 5B-ENV**；Batch 4C = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- GateK CI Batch 5A no-backend E2E first-run review completed：**PASS / READY FOR FREEZE REVIEW**。**Batch 5A = FIRST RUN PASSED / READY FOR FREEZE REVIEW**：immutable GitHub Actions run `27750279096`（workflow `NQ CI Baseline`、event push、branch dev、commit `861c3e78ddd1733292c5376a1f059532fd6dc846`）整体 success，其中 job `Frontend no-backend E2E (Batch 5A)`（id `82098741200`）success、约 56s（< 15min timeout）。证据：`permissions` 生效为 Contents: read / Metadata: read；Node 22.22.3；`npm ci` added 183；`npx playwright install --with-deps chromium` 仅装 Chromium 1208（Firefox/Webkit 下载 0）；vite build 成功；命令显式列出四 spec，`Running 4 tests using 1 worker` / `4 passed (7.3s)`，其余 23 个 spec 0 次出现、无 skip-as-pass；`/api`/postgres/jdbc/flyway/docker/loginToConsole/seed/storageState/okx/binance 命中均为 0；无 service 容器、无 `upload-artifact`、cleanup `rm -rf` 运行成功。CI bootstrap 下载（checkout/Node/npm registry/Chromium CDN）属引导网络访问，业务层出站为 0。P3 记录：runner 级 Node20→24 action wrapper 弃用警告，不影响应用 Node 22 与结论。详见 `NQ_CI_FRONTEND_E2E_5A_FIRST_RUN_REVIEW.md`。**Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**；**Batch 5B-SMOKE = BLOCKED BY 5B-ENV**；Batch 4C = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- GateK CI Batch 5A no-backend E2E implementation completed：**IMPLEMENTED / READY FOR FIRST-RUN**。在 `.github/workflows/ci.yml` 新增独立 job `frontend-no-backend-e2e`（`permissions: contents: read`、`timeout-minutes: 15`、Node 22、`npm ci`、`npx playwright install --with-deps chromium`、`npm run build`、loopback `vite preview` 127.0.0.1:5179）并新增 `frontend/playwright.ci.config.ts`（Chromium only / workers=1 / retries=0 / trace=screenshot=video=off / line reporter / 不用 storageState / `reuseExistingServer:false` / `forbidOnly:true`）。只跑四个 no-backend spec（仓库真实路径 `frontend/tests/e2e/`，非任务书写的 `frontend/e2e/`）：`login-page-smoke`、`design-system-table-smoke`、`design-system-live-query-smoke`、`design-system-backtest-chart-smoke`；命令显式列出四 spec，config `testMatch` 二次限定，`--list` 证明 Total: 4 tests in 4 files，未扩大到其余 23 个 spec。本地真实验证：`npm run build` 成功、四 spec **4 passed (10.2s)**、无 artifact 生成/上传。**未**启动 backend/PostgreSQL/Flyway/认证/seed/账户写入/外网/真实 provider，**未**调用 `loginToConsole()`，**未**修改 Batch 4C redaction 规则。所有 authenticated/backend-required spec 仍不在 required gate；trace/video/screenshot/artifact 上传保持禁用。**Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**；**Batch 5B-SMOKE = BLOCKED BY 5B-ENV**；Batch 4C = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。详见 `NQ_CI_FRONTEND_E2E_5A_IMPLEMENTATION.md`。
- GateK CI Batch 5 frontend E2E plan review completed：**PASS / ACCEPTED**。**Batch 5 plan = ACCEPTED AS IMPLEMENTATION BASELINE**；**Batch 5A = READY FOR IMPLEMENTATION**（最终 allowlist = `login-page-smoke` / `design-system-table-smoke` / `design-system-live-query-smoke` / `design-system-backtest-chart-smoke`，经源码核实纯 loopback / 无后端 / 无 token / 无账户写入 / 无外网，无存疑 spec 需移出）；**Batch 5B-ENV = P1 PREREQUISITE / NOT STARTED**；**Batch 5B-SMOKE = BLOCKED BY 5B-ENV**。runtime no-outbound P1 仅阻断 5B，不阻断纯 no-backend 5A。本轮只更新 `docs/current`，未修改 workflow/spec/前端/后端/seed/migration/依赖，未运行 Playwright/backend/PostgreSQL/Flyway/浏览器安装，未上传任何 artifact。详见 `NQ_CI_FRONTEND_E2E_PLAN_REVIEW.md`。Batch 4C = **FROZEN / ACCEPTED**；Batch 4F-A = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- GateK CI Batch 5 frontend E2E hardening plan completed：**PASS / READY FOR REVIEW**。Batch 5 = **PLAN ONLY / NOT IMPLEMENTED**；首个建议基线是 4 个 no-backend Playwright spec 的 bounded allowlist，backend-required E2E 必须先完成 job-local PostgreSQL/Flyway、同步 auth/legacy fixture、真实 backend/preview readiness、runtime no-outbound enforcement 与 fresh-DB repeat proof。当前 27 个 spec 未在本轮执行，`backtest-detail-smoke.spec.ts` 页面级 case = **PENDING BACKEND ENV / NOT VERIFIED IN CI**；不得写成 passed。本轮未上传 trace/screenshot/video/HTML report/test-results/raw logs。Batch 4C = **FROZEN / ACCEPTED**；Batch 4F-A = **FROZEN / ACCEPTED**；Batch 4F-B 至 4F-F = **OPTIONAL BACKLOG / NOT STARTED**；NQ GateK CI mainline = **IN PROGRESS**；LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter = 未开启、未接入、未实现。
- legacy console gate completed。
- RC1 completed and frozen。
- GateH-PRE completed。
- DOC-CLEAN completed。
- BASELINE-FIX completed。
- GateH-PLAN completed。
- GateH-1-WO completed。
- GateH-2-WO completed。
- GateH-3-WO completed。
- GateH completed。
- GateI-PLAN completed。
- GateI-1-WO completed。
- GateI-2-WO completed.
- GateI-3-WO completed。
- GateI-3-FIX completed。
- GateI-4-WO completed。
- GateI-4-FIX completed。
- GateI completed。
- GateJ-PLAN completed。
- GateJ-1-WO completed。
- GateJ-2-WO completed。
- GateJ-3-WO completed。
- DOC-CLEAN-2 completed。
- PRE-FREEZE-CODE-AUDIT completed。
- PRE-FREEZE-CODE-AUDIT second pass completed。
- AUDIT-FIX completed。
- GateJ-FREEZE-FIX completed。
- GateJ-FREEZE-FIX-SECOND-PASS completed。
- GateJ-FREEZE-FIX-3 completed。
- GateJ-FREEZE-FIX-4 completed。
- GateJ-FREEZE-FIX-5 local release reproducibility fix completed（ECS 复验已通过）。
- GateJ-FREEZE-FIX-6 local freeze sync guard and console text cleanup completed（ECS 复验已通过）。
- GateJ-FREEZE-FIX-7 local freeze console UI text and filter control cleanup completed（ECS 复验已通过）。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed。
- GateJ completed。

## 当前执行状态

- 2026-05-30 GateJ-FREEZE UI + UX smoke review 已完成并形成 `GATEJ_FREEZE_UI_UX_SMOKE_REPORT.md`：本次为 Chrome 浏览器只读巡检，不是运行稳定性验收；Functional stability: PASS，UI/UX professionalism: FAIL；当前 7d 连续运行验收继续，不因 UI/UX 问题中断。
- GateJ-FREEZE 最终验收事实：30m observation PASS，1h acceptance PASS，24h acceptance PASS，7d acceptance PASS，GateJ completed: yes。
- FIX-5 / FIX-6 / FIX-7 已完成并通过 ECS 复验；安全组已确认 `5179` 只允许本人 IP 访问。
- UI/UX smoke review 发现的 Dashboard 工程实现文案、freeze 写按钮可点击、Instrument Catalog 同步入口未前端禁用、Paper Trading / Schedules / Runs 缺摘要等问题应作为 post-freeze remediation 跟踪，不应写成后端或运行稳定性 FAIL。
- Current stage: GateJ completed。
- Next: GateK-PLAN。
- GateK implementation: not started。GateK-PLAN 只做 GateJ 后的 planning / architecture / productization / deployment / observability / security boundary 收口，不代表实现已启动。
- GateK CI Batch 4F-A dependency audit input / toolchain preflight freeze review completed：**PASS / ACCEPTED / FROZEN**（preflight `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT.md`；preflight review `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_REVIEW.md`；freeze review `NQ_CI_DEPENDENCY_AUDIT_PREFLIGHT_FREEZE_REVIEW.md`）。Batch 4F plan review = **PASS / ACCEPTED**；Batch 4F plan = **ACCEPTED AS IMPLEMENTATION BASELINE**；Batch 4F execution sequence = **SYNCED / ACCEPTED**；Batch 4F-A preflight = **FROZEN / ACCEPTED**。Python local audit = **NOT READY**，P2 保留为 4F-B execution prerequisite；4F-B 若覆盖 Python，必须使用已确认的真实解释器路径或 `actions/setup-python@v5`。4F-B sanitized summary 的 10 个 mandatory fields 已冻结，`scope` 为 bounded field；vulnerability findings 仅 report-only/advisory。Batch 4F-B 至 4F-F = **NOT STARTED**。Batch 4C overall = **FROZEN / ACCEPTED**；Static workflow assertion = **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；Batch 5 = **PENDING**。本轮未改 workflow / code / test / migration / frontend / research / scripts / deploy，未运行 dependency audit、scanner、SBOM、构建或测试，未开启 LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter。
- GateK CI Batch 4C overall security artifact/log redaction baseline freeze review completed：**PASS / ACCEPTED / FROZEN**（overall review `NQ_CI_SECURITY_GUARD_BATCH_4C_FREEZE_REVIEW.md`；Batch 4C-B pre-upload artifact redaction gate **FROZEN / ACCEPTED**，immutable green run `27701669084`；Batch 4C-C log redaction proof **FROZEN / ACCEPTED**，immutable green run `27732660516`，7/7 jobs green，14 类 high-risk pattern 真实值命中 = 0；4C-B / 4C-C P0/P1=0）。Batch 4C overall = **FROZEN / ACCEPTED**；Static workflow assertion 仍 **OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED**；Batch 4F-A preflight 后续已 **FROZEN / ACCEPTED**，Python local audit = **NOT READY**，4F-B 至 4F-F = **NOT STARTED**；Batch 5 **PENDING**。本轮未改 workflow / code / test / migration / frontend / research / scripts / deploy，未上传 logs artifact，未开启 LIVE / AI / DH runtime / RealClient / real provider / real exchange adapter。
- GateJ-3-WO 已完成。
- PRE-FREEZE-CODE-AUDIT second pass 已完成：无 P0；Claude 第一轮 P1-1 / P1-2 验证缺口已由 Codex 实际重跑关闭；P1-3 不阻塞；P1-4 已闭环 GATEJ_FREEZE_ACCEPTANCE_TEMPLATE。详见 `PRE_FREEZE_AUDIT_REPORT.md` 与 `PRE_FREEZE_AUDIT_FIX_PLAN.md`。
- FULL_SECURITY_AUDIT 报告中的 P1 已由 AUDIT-FIX 关闭：旧 OKX dome 验收脚本已移出 `scripts/` 可执行区并归档到 `docs/archive/scripts/`，原路径只保留阻断 stub；`/__gated/**` 仍仅为历史路径。
- GateJ-FREEZE-FIX 已修复 ECS freeze 登录页敏感信息暴露与登录 401 根因：生产构建登录页不再展示 legacy console gate、本地端口、默认账号密码、认证 API 与 Authorization header 示例；freeze profile 不再执行 local 默认用户 seed；新增 `scripts/seed-freeze-user.sh` 通过服务器环境变量生成 BCrypt hash 并写入验收用户。
- GateJ-FREEZE-FIX-SECOND-PASS 已完成：`frontend/dist` 与新 release 解压内容未命中敏感登录页泄露串；freeze compose/template 使用 `NQ_PROFILE=freeze`；Git 未追踪 release/dist/env/jar/zip/dump/log/evidence；详见 `GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md`。
- GateJ-FREEZE-FIX-3 已完成：`scripts/seed-freeze-user.sh` 不再使用跨 statement 生命周期不稳定的临时表，改为单个 `psql` session/transaction 内 upsert freeze 用户、启用用户、重绑角色并校验 BCrypt；部署文档明确禁止手工 `source .env.freeze`，特殊字符密码建议通过 seed 脚本交互式隐藏输入。
- GateJ-FREEZE-FIX-4 已完成：修复 `seed-freeze-user.sh` 交互式隐藏输入路径，避免 `read` 后视觉换行进入命令替换返回值并被单行校验误判；ECS 仍需用真实 Bash/TTY 复验后才能继续 GateJ-FREEZE 首次启动验收。
- GateJ-FREEZE-FIX-5 本地修复已完成：新增 `.gitattributes` 强制 shell/yaml/PowerShell 换行策略，仓库 `scripts/*.sh` 已归一为 LF，`scripts/build-freeze-release.ps1` 在 zip 前对 staging `scripts/*.sh` 做 LF 兜底转换；新 release 本地解压检查确认 zip 内 `.sh` 不含 CRLF。ECS 仍需重新上传新 release 后直接执行 `bash -n`、`backup-db.sh`、`freeze-health-loop.sh` 与 `health-check-7d.log` 写入 `UP` 验证，未通过前不得进入 GateJ-FREEZE 首次启动验收。
- GateJ-FREEZE-FIX-6 本地修复已完成：freeze profile 默认禁用 Instrument Catalog 外部同步，`/api/instruments/sync` 在禁用或 Binance exchangeInfo 失败时返回 409 受控错误，不再进入 `api_unhandled_exception`；前端 Instrument Catalog 与 Header 已清理 `GateH-PRE` / `LOCAL` 可见残留，dist/release 扫描未命中禁止串。ECS 仍需重新上传新 release 后验证浏览器同步 Catalog 不再显示 internal server error，日志不再出现 `api_unhandled_exception path=/api/instruments/sync`。
- GateJ-FREEZE-FIX-7 本地修复已完成：清理 freeze 控制台页面中 `GateH-1`、`GateH-2`、`GateI-3`、`GateH-PRE`、`GateG`、`LOCAL`、`Gate-3` 与开发接口说明残留；将 Marketdata / Strategies / Schedules / Runs / Paper Trading / Evaluations / Publishes 等页面枚举筛选改为 Ant Design Select，并将 Marketdata 与 Backtests 时间输入改为 DatePicker 后转换 ISO 字符串提交。ECS 仍需重新上传新 release 后浏览器复验页面文案与筛选控件，并确认 Instrument Catalog sync 仍是受控提示。
- E2E/Vite 本地端口已从 `4173` 调整为 `5179`，避开 Windows TCP excluded range `4141-4240`；AUDIT-FIX 完整 E2E 已通过。
- 后端 `mvn -f backend/pom.xml test` BUILD SUCCESS（23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors）。
- 前端 `npm run build` 通过（仍有 Vite chunk > 500 kB P2 警告）。
- E2E `npm run test:e2e` 本轮实际执行通过：24 passed / 1 skipped / 0 failed；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路，GateJ 主链未 skip。
- Python `pytest / mypy / ruff` 本轮实际执行通过：pytest 2 passed，mypy 8 source files no issues，ruff all checks passed。
- Flyway 当前版本 V25（gate j3 paper run recovery stability）。
- GateJ-FREEZE 连续运行验收已完成：起点 2026-05-29 14:53:20 +08:00；7d checkpoint 2026-06-05 14:53:24 +08:00；health-loop 样本数 2025；health-loop 最新样本 2026-06-05 15:40:58 +08:00。
- after-7d checkpoint 中 `docker compose logs --since=7d` 不被当前 Compose 识别，已补跑合法窗口 `--since=168h`；`/opt/nexus-quant/freeze-evidence/reports/after-7d/nq-app-error-scan-168h.txt` 的 `wc -l = 0`，未命中 `api_unhandled_exception`、`Binance request failed`、`status=451`、`BCrypt`、`Encoded password`、`authentication required`、`ERROR`、`Exception`、`OutOfMemory`、`OOM`。
- nginx / nq-app / postgres 均为 Up 7 days，其中 postgres healthy；18888 health 为 UP，5179 health 为 UP；after-7d.sql 已生成，266K；磁盘约 30G 可用，使用率约 21%；Swap 0B 使用；5179 安全组已确认只允许本人 IP 访问。
- UI/UX smoke review：Functional stability PASS，UI/UX professionalism FAIL；该问题不影响 GateJ-FREEZE 稳定性验收，但必须作为 post-freeze remediation，不能宣称 UI/UX 专业化已完成。
- DH integration not started / not connected to NQ。
- Multi-exchange expansion not started。
- No new backend/frontend business code, API, migration, deployment, AI, DH, or real trading path was added in GateJ-FREEZE-FINAL-DOC。
- Codex workflow documentation hardening completed：已新增 NQ/DH 插件路由、Workflow Router Skill 说明、任务模板和 Project Instructions 文档；本轮只做规则文档固化，未修改业务代码、部署配置、API、migration、AI、DH 或真实交易路径。
- Codex workflow documentation consistency fix completed：已将 Router Skill 状态与 `AGENTS.md` active skills 对齐，`nq-dh-workflow-router` 作为当前项目 active skill 使用；`CODEX_PROJECT_INSTRUCTIONS.md` 已补充 Router 前置分类规则。本轮只修改 Markdown 文档，未修改业务代码、部署配置、API、migration、AI、DH 或真实交易路径。
- Codex workflow output format consistency fix completed：标准输出字段已统一为 `Findings`，不再把 `Summary` 作为必填输出字段。本轮只修改 Markdown / Skill 文档，未修改业务代码、部署配置、API、migration、AI、DH 或真实交易路径。
- Credential revocation governance Batch 5-C completed：后端已接入 `credential_status` 生命周期字段、active material 默认只读取 `ACTIVE`、新增 `revoke / disable / expire` 最小 API 与 append-only audit log 写入；本轮未新增 migration、前端、Python、部署、AI、DH、LIVE 或真实交易所私有链路。
- AI 尚未开始。GateK-PLAN 仅做边界规划，不启动 AI 信号、AI runtime 或 AI Paper Trading。
- GateJ 不是 AI 阶段。GateJ 只做 Paper Trading 稳定运行。

## NQ / DH 三轮审计同步（2026-06-11，DOC-SYNC-GATEK-PRE-AND-INT0-REGISTRATION）

本轮只做事实源文档同步，不修改任何代码，不启动 Integration-0 实现，不启动 GateK 实现。

三轮只读审计已完成：

- 第一轮：NQ 全仓只读审计 completed。
- 第二轮：DH 全仓只读审计 completed。
- 第三轮：NQ-DH 联合边界审计 completed（见 DH 仓库 `docs/current/NQ_DH_INTEGRATION_SECURITY_AUDIT_REPORT.md`）。
- 三轮审计汇总 completed。

NQ 当前阶段口径（必须按此理解，不得误判）：

- Current: GateJ completed。
- Next: GateK-PLAN。
- GateK implementation: not started。
- AI: not started。
- DH: not integrated（NQ 侧仍无 DH 入站端点、无 DH client、无 feedback outbox）。
- LIVE: disabled。
- Integration-0: allowed only as contract / mock / documentation work line, not runtime integration。

Integration-0 允许范围（仅文档与契约线，不是真实集成）：

- 只读边界规划、契约冻结、mock / stub / contract test、安全策略文档。
- 不允许真实联调、NQ RealClient、真实 Provider、真实交易、读取凭证、读写 NQ DB、开启 LIVE。

DH 侧事实（来自第二轮与第三轮审计）：

- DH 当前无真实 NQ 调用、无真实 Provider、无交易能力。
- DH P1-1 / P1-2 / P1-3 已关闭（认证+租户隔离、HMAC/timestamp/nonce 防重放+source allowlist+payload 上限、ProviderTrustPolicy）。
- DH P1-4 部分关闭：限流（rate limit）、内存仓储上限（memory cap）、replay nonce 持久化仍缺失；该残留不阻塞 Integration-0，但阻塞 Integration-1。

## NQ-DH Integration-0 safety gate（2026-06-12，CLOSED / ACCEPTED）

- Integration-0 safety gate close / acceptance：**PASS / CLOSED / ACCEPTED**，详见 `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`。
- 已完成链路：三轮审计 + 汇总 → 事实源同步 → 契约冻结 → contract test 矩阵设计 → contract test 代码实现（NQ 16 + DH 16）→ implementation review（PASS）→ 本次验收关闭。
- 验收依据：NQ `mvn -f backend/pom.xml test` BUILD SUCCESS（nq-app 51 tests / 0 failures，Integration-0 16 passed，ArchUnit 全绿）；DH `mvn test` BUILD SUCCESS（dh-domain 86 tests / 0 failures，Integration-0 16 passed，ArchitectureTest 12 条全绿，PostgresContainerSmokeTest 既有环境性 skip）。两侧均覆盖 INT0-T01..T15，含 negative path、audit event shape、forbidden side-effect。
- 边界保持：Runtime integration NOT STARTED；Integration-1 NOT STARTED；DH NOT INTEGRATED；AI NOT STARTED；LIVE DISABLED；无生产代码 / API / migration / RealClient / 真实 Provider / 真实 HTTP / 真实 NQ / 真实交易所 / 凭证读取 / NQ DB 读写 / 交易副作用。
- Integration-1 前置 blocker：DH P1-4 residual（rate limit / memory cap / replay nonce persistence，修复后须重跑 contract tests，T06 须以持久化 nonce 重跑，并新增 429 限流与 bounded store 测试）；header `X-DH-NQ-*` 与 `X-NQ-DH-*` 对齐；真实通道安全前置（单独开工 + 设计审计 + staging/paper-only + LIVE disabled + 无凭证落日志 + no trading side-effect + 安全审查）。
- 下一步只允许：Integration-0 acceptance/归档、Integration-1 planning-only audit、DH P1-4 residual fix planning、GateK-PLAN 文档规划。禁止直接 Integration-1 实现 / 真实只读通道 / 真实 HTTP / RealClient / Provider / LIVE / AI 自动交易。

## 当前未完成状态

- 虚拟币量化 V1 已在 GateI 完整闭环完成；当前未完成的是公开生产就绪、UI/UX 专业化收口、AI/LIVE/美股/A 股等后续阶段。
- Paper Trading 稳定运行 GateJ 已完成；UI/UX professionalism 仍是 post-freeze remediation。
- 尚未进入 AI 自动交易。
- 尚未进入美股/A 股适配。

## 后续路线

```text
DOC-CLEAN / BASELINE-FIX
  ↓
GateH：交易工作台 + 历史行情 + dataset 绑定 completed
  ↓
GateI-PLAN
  ↓
GateI：虚拟币量化 V1 完整闭环
  ↓
GateJ：Paper Trading 稳定运行 completed
  ↓
GateK：规划 / 架构 / 产品化 / 部署化 / 可观测性 / 安全边界收口（NEXT）
  ↓
GateL：No-Real Exchange / MarketData Readiness（planning / contract / readiness）
  ↓
GateM：AI Paper Trading（后续独立 AI/DH 阶段，当前 NOT STARTED）
  ↓
GateN：AI 小资金 LIVE
  ↓
GateO：美股适配
  ↓
GateP：A 股适配
```

## 本地环境约定

- PostgreSQL 默认端口：`5432`。
- `local` profile 默认连接 `localhost:5432`。
- `docker-compose` 默认映射 `5432:5432`。

## 当前验证基线

- 后端 `mvn -f backend/pom.xml test` 已通过。
- 前端 `npm run build` 已通过。
- E2E `npm run test:e2e` 已通过，结果为 5 passed / 3 skipped。
- GateH-2 后 E2E `npm run test:e2e` 已通过，结果为 9 passed / 3 skipped。
- GateH-3 后 E2E `npm run test:e2e` 已通过，结果为 10 passed / 4 skipped。
- GateH-3 的 backtest dataset binding UI smoke 因当前本地库没有可绑定 backtest config 种子而 skip；后端 controller 测试覆盖绑定 API。
- GateI-1 后端 `mvn -f backend/pom.xml test` 已通过。
- GateI-1 前端 `npm run build` 已通过。
- GateI-1 E2E `npm run test:e2e` 已通过，结果为 13 passed / 3 skipped。
- GateI-2 后端 `mvn -f backend/pom.xml test` 已通过。
- GateI-2 前端 `npm run build` 已通过。
- GateI-2 后端 local profile 启动已通过，Flyway 当前版本为 `20`。
- GateI-2 E2E `npm run test:e2e` 已通过，结果为 17 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路，不影响 GateI-2 主链。
- GateI-3 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests，0 failures）。
- GateI-3 前端 `npm run build` 已通过。
- GateI-3 E2E `npm run test:e2e` 已通过，结果为 18 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateI-3 主链。
- GateI-3 Flyway 当前版本为 `21`。
- GateI-4 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests，0 failures，含 PaperTradingMonitorServiceTest 5 用例）。
- GateI-4 前端 `npm run build` 已通过。
- GateI-4 Flyway 当前版本为 `22`。
- GateI-4-FIX E2E `npm run test:e2e` 已通过，结果为 19 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路。
- Python `pytest`、`mypy`、`ruff` 已通过。
- GateJ-1 后端 `mvn -f backend/pom.xml test` 已通过（35 tests / 0 failures）。
- GateJ-1 前端 `npm run build` 已通过。
- GateJ-1 E2E `npm run test:e2e` 已通过，结果为 20 passed / 1 skipped。
- GateJ-1 Flyway 当前版本为 `23`。
- GateJ-2 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests / 0 failures，含 PaperRunMonitorServiceTest 12 用例）。
- GateJ-2 前端 `npm run build` 已通过。
- GateJ-2 E2E `npm run test:e2e` 已通过，结果为 22 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateJ-2 主链。
- GateJ-2 Flyway 当前版本为 `24`。
- GateJ-3 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，含 PaperRunRecoveryServiceTest 9 用例 + PaperRunStabilityCheckServiceTest 10 用例 + PaperRunMonitorRunServiceTest 8 用例）。
- GateJ-3 前端 `npm run build` 已通过。
- GateJ-3 E2E `npm run test:e2e` 已通过，结果为 24 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateJ-3 主链。
- GateJ-3 Flyway 当前版本为 `25`。

## PRE-FREEZE-CODE-AUDIT 验证记录（2026-05-22）

- 后端 `mvn -f backend/pom.xml test`：通过（BUILD SUCCESS，0 failures、0 errors；archunit 模块边界与包边界全部通过）。
- 前端 `npm run build`：通过（Vite 通过，dist/index.js ≈ 1.48 MB，仍有 chunk > 500 kB 警告）。
- `npm run test:e2e`：本轮未实际重跑（沿用 GateJ-3-WO 24 passed / 1 skipped 基线）；P1-1 要求 GateJ-FREEZE 入场前补跑。
- Python `pytest / mypy / ruff`：本轮未实际重跑（当前 shell 仅 WindowsApps stub，无真实 Python 解释器；沿用 BASELINE-FIX-2 / GateJ-3 通过基线）；P1-2 要求 GateJ-FREEZE 入场前补跑。
- 详见 `PRE_FREEZE_AUDIT_REPORT.md` 与 `PRE_FREEZE_AUDIT_FIX_PLAN.md`。

## PRE-FREEZE-CODE-AUDIT second pass 验证记录（2026-05-22）

- 后端 `mvn -f backend/pom.xml test`：通过（Reactor BUILD SUCCESS；23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors）。
- 前端 `npm run build`：通过（Vite build 成功；仍有 chunk > 500 kB 警告）。
- E2E `npm run test:e2e`：通过（后端 local profile 启动成功，Flyway 当前版本 25；完整 Playwright 24 passed / 1 skipped / 0 failed；唯一 skipped 为 `E2E_TRADE_ORDER_ID` 未配置的既有订单详情链路）。
- Python `python -m pytest -q`：通过（2 passed）。
- Python `python -m mypy src`：通过（Success: no issues found in 8 source files）。
- Python `python -m ruff check .`：通过（All checks passed）。
- API / DB / Paper-LIVE 隔离 / AI 边界二次抽查未发现 P0/P1。
- 结论：允许进入 GateJ-FREEZE，但 GateJ-FREEZE 必须单独开工，只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。

## AUDIT-FIX 验证记录（2026-05-26）

- P1 关闭：`scripts/gated_okx_dome_verify.ps1` 已变为安全阻断 stub，旧脚本归档到 `docs/archive/scripts/gated_okx_dome_verify.ps1`；当前可执行 API 不包含 `/__gated/**`。
- E2E 端口修复：`frontend/playwright.config.ts`、`frontend/tests/e2e/run-e2e.mjs`、`frontend/vite.config.ts`、`frontend/.env.example` 已统一从 `4173` 调整为 `5179`。
- 验证结果：`mvn -f backend/pom.xml test` 通过；`cd frontend && npm run build` 通过；启动后端 local profile 后 `cd frontend && npm run test:e2e` 通过，结果 24 passed / 1 skipped / 0 failed。
- 本轮不新增 API、不新增 migration、不修改交易下单/风控/撮合/恢复/调度核心逻辑、不接 AI。
- 验证结果详见 `AUDIT_FIX_REPORT.md`。

## GateI 当前边界

- GateI 已整体完成。
- GateI-1 实现策略版本与发布记录绑定。
- GateI-2 实现回测配置、评估指标、结果追溯增强。
- GateI-3 实现 SIM/Paper Trading 运行闭环最小版本。
- GateI-4 实现风控回写、资金曲线、持仓曲线、交易复盘、异常停机最小结构。
- AI、AI 信号、AI 自动交易、AI Paper Trading 仍未开始。
- GateJ-3-WO 已完成（异常恢复、失败重试、稳定性验收结构、HEARTBEAT_LAG/SCHEDULE_FIRE_FAILED 自动告警最小落库）。
- DOC-CLEAN-2 已完成（删除 docs/current/ 中 GateH/GateI 计划副本）。
- PRE-FREEZE-CODE-AUDIT second pass 已完成（无 P0；E2E 与 Python 基线均已实际重跑通过，详见 PRE_FREEZE_AUDIT_REPORT.md）。
- GateI 的历史下一步 GateJ 已完成；当前状态是 GateJ completed / Next: GateK-PLAN，AI 仍 not started。

---

## NQ-DOCS-GOVERNANCE-G5-DIRECTORY-CLOSURE-PREFLIGHT（2026-06-19）

- NQ documentation governance **G5 directory closure preflight** completed（2026-06-19）：**IMPLEMENTED / READY FOR REVIEW**。只从 G1 冻结的 `docs/current/NQ_DOCS_MIGRATION_MAP.md` 精确抽取 `recommended_action = FUTURE_MOVE_CANDIDATE` 且 `migration_batch = G5` 的候选，结果为 **0 total / 0 ELIGIBLE_FOR_G5_IMPLEMENTATION / 0 BLOCKED_PER_FILE / 0 RETAIN_IN_PLACE**。Migration Map 中 `FUTURE_MOVE_CANDIDATE` 仅出现在 §1D 且 batch = G4；`G5 可选` 仅为 §1B/§1C 的说明性文字且 batch = NONE，不得扩展为候选。新增 `NQ_DOCS_G5_DIRECTORY_CLOSURE_PREFLIGHT.md`；未移动、删除、重命名、复制、归档、stub 化任何文档；未创建 target 目录或 canonical 文件；未修改 G1～G4 冻结对象、docs/gates/archive/.agents/templates、workflow、code、migration。**G1 authority/evidence index = FROZEN / ACCEPTED**；**G2 current-control drift repair = FROZEN / ACCEPTED**；**G3 GateJ redirect-first consolidation = FROZEN / ACCEPTED**；**G4 CI evidence routing = FROZEN / ACCEPTED**；**G5 directory closure preflight = IMPLEMENTED / READY FOR REVIEW**；**G6 deletion batch = NOT STARTED / DEFAULT EMPTY**。NQ GateK CI mainline = **COMPLETED / ACCEPTED**；Batch 5A = **FROZEN / ACCEPTED**；Batch 5B-ENV = **P1 SECURITY ENHANCEMENT / NOT STARTED**；Batch 5B-SMOKE = **BLOCKED BY 5B-ENV**；LIVE / AI / DH runtime / RealClient / real provider = 未开启、未接入、未实现。
