# Roadmap

## 总路线

```text
DOC-CLEAN / BASELINE-FIX completed
  ↓
GateH：交易工作台 + 历史行情 + dataset 绑定 completed
  ↓
GateI：虚拟币量化 V1 完整闭环 completed
  ↓
GateJ：Paper Trading 稳定运行 completed
  ↓
GateK：规划 / 架构 / 产品化 / 部署化 / 可观测性 / 安全边界收口 ← NEXT
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

## 当前阶段

- DOC-CLEAN completed。
- BASELINE-FIX completed。
- GateH completed。
- GateI completed。
- GateJ-PLAN completed。
- GateJ-1-WO completed。
- GateJ-2-WO completed。
- GateJ-3-WO completed。
- GateJ-FREEZE 30m / 1h / 24h / 7d acceptance passed。
- GateJ completed。
- Next: GateK-PLAN（planning / 收口工作线；GateK 产品/runtime 实现仍 not started）。
- **NQ GateK CI mainline = COMPLETED / ACCEPTED**（CI 状态权威以 STATUS.md + NQ_CI_BASELINE_PLAN.md 为准）：Batch 5A no-backend frontend E2E = FROZEN / ACCEPTED（仅 4 个 no-backend smoke spec，非 authenticated/backend E2E coverage）；Batch 5B-ENV runtime no-outbound = P1 SECURITY ENHANCEMENT / **FROZEN / ACCEPTED**（freeze 卷宗 NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md，evidence run `27876451289` / headSha `8ba140d9` / 8 jobs success；规划 NQ_CI_SECURITY_BATCH_5B_ENV_PLAN.md，review NQ_CI_SECURITY_BATCH_5B_ENV_PLAN_REVIEW.md）；Batch 5B-SMOKE = **FROZEN / ACCEPTED**（implementation plan **REVIEWED / ACCEPTED**，implementation **DONE**，ci.yml `ci-security-smoke` job 复用 EnvSafety / no-outbound / NoReal 最小 smoke；first run evidence PASS（run `27903497008`，9 jobs success）；freeze **FROZEN / ACCEPTED**，卷宗 `NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md`；**Batch 5B = CLOSED / ACCEPTED**；只允许 no-real / no-outbound / mock / fake / NoReal 路径）；Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED；Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。
- 文档治理：G1 authority/evidence index = FROZEN / ACCEPTED；G2 current-control drift repair = IMPLEMENTED / READY FOR REVIEW；G3~G6 = NOT STARTED（治理入口见 `NQ_DOCS_AUTHORITY_INDEX.md` / `NQ_DOCS_EVIDENCE_INDEX.md`）。
- GateK CI/security baseline = **FROZEN / ACCEPTED**（final freeze 卷宗 `NQ_CI_SECURITY_FINAL_FREEZE.md`；Batch 1–5 全部 FROZEN/ACCEPTED 或 CLOSED；evidence run 27903497008 / 27876451289 / 27904207910 success；后续 workflow/guard/env 改动须重新 evidence + freeze）。
- GateK post-freeze 专项收口 = **FROZEN / ACCEPTED**：NQ-TEST-ISOLATION-OKX-BOOTSTRAP-NO-OUTBOUND（freeze commit `8a2fbe4a`）+ NQ-OKX-RUNTIME-CONFIG-DEFAULT-ENDPOINT-DEFENSE（impl `c749cef7` / addendum `7d9330c3` / CI run `27926903155` 9 jobs success；P2 OkxRuntimeConfig default real endpoint defense = CLOSED / ACCEPTED；OKX runtime 默认 endpoint = `disabled://` sentinel）。
- GateK post-freeze handoff = **PASS / READY FOR NEXT PHASE**（handoff 卷宗 `NQ_GATEK_POST_FREEZE_HANDOFF_PLAN.md`）；**NEXT PHASE = READY TO PLAN**（无 P0/P1 阻断项）。下一阶段入口候选（不在本轮启动，由本路线图决定）：GateL PLAN / Integration-1 PLAN / Market data next batch PLAN / Trading adapter no-real contract PLAN。
- GateK implementation not started。
- **NQ-GATEL-PLAN = PASS / ACCEPTED（PLANNING BASELINE）**（2026-06-22）：GateL planning baseline 落档 `GATEL_PLAN.md`，范围 = 真实交易所接入前的 No-Real 交易适配器 / 市场数据 / permission probe / paper-live execution 边界就绪（GateL-1 adapter contract → GateL-2 marketdata no-real pipeline → GateL-3 permission probe contract → GateL-4 paper-first execution boundary → GateL-5 real exchange readiness checklist）。docs-only，不实现、不接真实交易所、不读真实凭证、不外联、不启用 LIVE、不接 AI / DH runtime。**GateL implementation NOT STARTED**。
- **NQ-GATEL-1-EXCHANGE-ADAPTER-CONTRACT-REVIEW = CONDITIONAL PASS / DOCS-CONTRACT ONLY**（2026-06-22）：review 文档 `GATEL_1_EXCHANGE_ADAPTER_CONTRACT_REVIEW.md` 已落档。P0=0；P1=4（Binance default endpoint、进程 credential、`rawPayload`、stub success marker）；现有合同不得标记 future-real-ready。GateL-1A 已冻结 review fact baseline；P1/P2 保持 OPEN。**GateL implementation NOT STARTED；真实交易所未授权。**
- **NQ-GATEL-1A-EXCHANGE-ADAPTER-CONTRACT-REVIEW-FREEZE = PASS / FROZEN / ACCEPTED**（2026-06-22）：只冻结 review 事实、P1/P2 与处理顺序；adapter readiness = **NOT READY / NOT FROZEN**。后续顺序：1B No-Real hardening plan → 1C capability matrix → 1D error model → 1E readiness checklist refinement。下一步 `NQ-GATEL-1B-NO-REAL-HARDENING-PLAN`，仍为 docs/plan-only。
- **NQ-GATEL-1B-NO-REAL-HARDENING-PLAN = PASS / PLAN READY FOR REVIEW**（2026-06-22）：规划 A endpoint → B credential source → C raw payload → D Noop semantics 四个最小切片；不需要 migration，不新增 HTTP API。P1/P2 仍 OPEN；implementation NOT STARTED；adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED。下一步仅 `NQ-GATEL-1B-NO-REAL-HARDENING-PLAN-REVIEW`。
- **NQ-GATEL-1B-NO-REAL-HARDENING-PLAN-REVIEW = PASS / ACCEPTED AS PLAN REVIEW BASELINE**（2026-06-22）：A/B/C/D 拆分、顺序、测试、验收与回滚通过审查；A 限定 sentinel-only/fail-closed，B 限定移除 process credential source，C producer suppression 与字段删除分开，D 不新增 DTO/API。四项 P1 仍 OPEN；implementation NOT STARTED；adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED。下一步仅 plan freeze。
- **NQ-GATEL-1B-NO-REAL-HARDENING-PLAN-FREEZE = PASS / FROZEN / ACCEPTED**（2026-06-22）：冻结 plan + review 组合基线；A/B 必须拆开，C producer suppression 与字段删除拆开，D 不新增 DTO/API，不需要 migration。四项 P1 仍 OPEN / RETAINED；implementation NOT STARTED；adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED。下一步唯一允许 `NQ-GATEL-1B-A-IMPL`，禁止直接 real adapter。
- **NQ-GATEL-1B-A-IMPL = PASS / IMPLEMENTED；PENDING REVIEW**（2026-06-22）：只实现 P1-A（Binance 默认 REST/WS endpoint → no-real sentinel `disabled://`，`normalizeWsUrl` 与 `BinanceWsProtocol.resolveUserDataWsApiUrl` 不再回退 testnet/mainnet，`disabled://` 请求期 loud fail-closed）；显式 env override 行为不变。`mvn -f backend/pom.xml -o -pl nq-adapter-binance -am test` BUILD SUCCESS（50/0/0/1 skipped）。**P1-B/C/D 仍 OPEN / RETAINED**；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；不代表允许真实 Binance 接入。下一步 `NQ-GATEL-1B-A-IMPL-REVIEW`。
- **NQ-GATEL-1B-A-IMPL-FREEZE = PASS / FROZEN / ACCEPTED；P1-A CLOSED / ACCEPTED**（2026-06-22）：冻结 implementation commit `04ddb774`（详见 `GATEL_1B_A_IMPL_FREEZE_REVIEW.md`）。`git show --check` / `git diff --check HEAD^ HEAD` 无 whitespace，main src 无 testnet/mainnet 默认 host，`mvn -o -pl nq-adapter-binance -am test` BUILD SUCCESS（50/0/0/1 skipped）。**P1-A CLOSED / ACCEPTED**；**P1-B/C/D 仍 OPEN / RETAINED**；GateL-1B 整体 No-Real hardening freeze NOT DONE（待 B/C/D）；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；不代表允许真实 Binance 接入或 future-real-ready。下一步 `NQ-GATEL-1B-B-IMPL`。
- **NQ-GATEL-1B-B-IMPL = PASS / IMPLEMENTED；PENDING REVIEW**（2026-06-22）：只实现 P1-B（OKX/Binance runtime credential source hardening）。`OkxRuntimeConfig` / `BinanceRuntimeConfig` 不再从进程环境（env / system property / .env）读取 credential material，默认 `*.unconfigured()`；非敏感 endpoint/timeout/reconnect 仍按显式 env 解析；authenticated/signed 请求 unconfigured 时网络前 fail-closed（OKX_CREDENTIALS_MISSING / BINANCE_CREDENTIALS_MISSING），失败信息不含 credential。未实现真实 credential governance bridge。`mvn -o -pl nq-adapter-okx,nq-adapter-binance -am test` BUILD SUCCESS（OKX 32 / Binance 51 / 0 fail / 1 skipped）。**P1-A 仍 CLOSED**；**P1-C/P1-D 仍 OPEN / RETAINED**；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；不代表允许真实 OKX/Binance 接入。下一步 `NQ-GATEL-1B-B-IMPL-REVIEW`。
- **NQ-GATEL-1B-B-IMPL-FREEZE = PASS / FROZEN / ACCEPTED；P1-B CLOSED / ACCEPTED**（2026-06-22）：冻结 implementation commit `ad7f58b0`（详见 `GATEL_1B_B_IMPL_FREEZE_REVIEW.md`）。`git show --check` / `git diff --check HEAD^ HEAD` 无 whitespace，runtime config 无 credential env 读取，P1-A sentinel 未回退，`mvn -o -pl nq-adapter-okx,nq-adapter-binance -am test` BUILD SUCCESS（OKX 32 / Binance 51 / 0 fail / 1 skipped）。**P1-B CLOSED / ACCEPTED**；**P1-A 仍 CLOSED**；**P1-C/P1-D 仍 OPEN / RETAINED**；GateL-1B 整体 No-Real hardening freeze NOT DONE（待 C/D）；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；未实现真实 credential governance bridge；不代表允许真实 OKX/Binance 接入或 future-real-ready。下一步 `NQ-GATEL-1B-C-IMPL`。
- **NQ-GATEL-1B-C-IMPL = PASS / IMPLEMENTED；PENDING REVIEW**（2026-06-22）：只实现 P1-C producer suppression。`OkxExchangeAdapter` / `BinanceExchangeAdapter` 的 `AdapterOrderAck` / `AdapterOrderSnapshot` producer 固定 `rawPayload=null`，不再传 provider full body、headers、signature、exception diagnostic 或 `snapshot.rawPayload()`；`rawPayload` record component 删除未做，另起兼容性任务。`mvn -o -pl nq-adapter-okx,nq-adapter-binance -am test` BUILD SUCCESS（OKX 34 / Binance 51 / 0 fail / 1 skipped）。**P1-A/P1-B 仍 CLOSED / ACCEPTED**；**P1-C producer suppression IMPLEMENTED / PENDING REVIEW**；**P1-D 仍 OPEN / RETAINED**；GateL-1B 整体 hardening freeze NOT DONE；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；不代表允许真实 OKX/Binance 接入。下一步 `NQ-GATEL-1B-C-IMPL-REVIEW`。
- **NQ-GATEL-1B-C-IMPL-FREEZE = PASS / FROZEN / ACCEPTED；P1-C producer suppression CLOSED / ACCEPTED**（2026-06-22）：冻结 implementation commit `316497ad`（详见 `GATEL_1B_C_IMPL_FREEZE_REVIEW.md`）。`git show --check` / `git diff --check HEAD^ HEAD` 无 whitespace，OKX/Binance ack/snapshot producer 均使用 `suppressedOrderRawPayload()` 且返回 null，`mvn -o -pl nq-adapter-okx,nq-adapter-binance -am test` BUILD SUCCESS（OKX 34 / Binance 51 / 0 fail / 1 skipped）。**P1-A/P1-B/P1-C producer suppression CLOSED / ACCEPTED**；**P1-C field deletion NOT DONE**；**P1-D OPEN / RETAINED**；GateL-1B overall hardening freeze NOT DONE（待 D）；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；不代表允许真实 OKX/Binance 接入。下一步 `NQ-GATEL-1B-D-IMPL`。
- **NQ-GATEL-1B-D-IMPL = PASS / IMPLEMENTED；后续已 freeze-close**（2026-06-22）：只实现 P1-D Noop marketdata status hardening。`NoopMarketDataAdapter` bars / trades / order-book 订阅统一返回 `subscribed=false`、`AdapterError.code=NO_REAL_DISABLED`、`category=FATAL_FAILURE`、`retryable=false`，不再表现为普通真实 success；不新增 DTO/API/migration/workflow，不接真实 provider。`mvn -o -pl nq-adapter-api,nq-adapter-okx,nq-adapter-binance -am test` BUILD SUCCESS（adapter-api 3 / OKX 34 / Binance 51 / 0 fail / 1 skipped）。冻结结论见 `GATEL_1B_D_IMPL_FREEZE_REVIEW.md`；GateL-1B overall hardening 仍 NOT FROZEN，adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；不代表允许真实 marketdata / real adapter / LIVE。
- **NQ-GATEL-1B-D-IMPL-FREEZE = PASS / FROZEN / ACCEPTED；P1-D CLOSED / ACCEPTED**（2026-06-23）：冻结 implementation commit `7e442eb7`（详见 `GATEL_1B_D_IMPL_FREEZE_REVIEW.md`）。`git show --check` / `git diff --check HEAD^ HEAD` 无 whitespace，Noop bars/trades/order-book 均返回 `subscribed=false + NO_REAL_DISABLED + FATAL_FAILURE + retryable=false`，`mvn -o -pl nq-adapter-api,nq-adapter-okx,nq-adapter-binance -am test` BUILD SUCCESS（adapter-api 3 / OKX 34 / Binance 51 / 0 fail / 1 skipped）。**P1-A/P1-B/P1-C producer suppression/P1-D CLOSED / ACCEPTED**；**P1-C field deletion NOT DONE**；GateL-1B overall hardening **NOT FROZEN**；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED；不代表允许真实 marketdata / real adapter / LIVE。下一步 `NQ-GATEL-1B-OVERALL-HARDENING-FREEZE-REVIEW`。
- **NQ-GATEL-1B-OVERALL-HARDENING-FREEZE-REVIEW = PASS / FROZEN / ACCEPTED**（2026-06-23）：冻结 GateL-1B A/B/C/D 组合 No-Real hardening baseline（详见 `GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md`）。复核 commits `04ddb774` / `ad7f58b0` / `316497ad` / `7e442eb7` 与 A/B/C/D freeze 文档；`mvn -f backend/pom.xml -o -pl nq-adapter-api,nq-adapter-okx,nq-adapter-binance -am test` BUILD SUCCESS（nq-contracts 1、adapter-api 3、OKX 34、Binance 51；0 fail / 0 error / 1 skipped）。**P1-A / P1-B / P1-C producer suppression / P1-D CLOSED / ACCEPTED**；**P1-C rawPayload field deletion NOT DONE / SEPARATE COMPATIBILITY TASK**；adapter readiness 仍 **NOT READY / NOT FROZEN / NOT AUTHORIZED**；不代表允许真实交易所、real adapter、LIVE、真实 credential、AI 或 DH runtime。下一步 `NQ-GATEL-1C-CAPABILITY-MATRIX-CONTRACT`。
- **NQ-GATEL-1C-CAPABILITY-MATRIX-CONTRACT = PASS / CONTRACT FROZEN**（2026-06-23）：新增 `GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md`，冻结 capability status enum 与 Noop / OKX / Binance / future-real / permission probe / marketdata placeholder 矩阵。OKX/Binance existing adapters 仍不是 future-real-ready，也不等于真实交易所授权；所有 real exchange capability 均为 `FUTURE_REAL_REQUIRES_GATE` 或 `FORBIDDEN_IN_GATEL`；adapter readiness 仍 **NOT READY / NOT FROZEN / NOT AUTHORIZED**；LIVE、真实 credential、AI、DH runtime 仍不允许。Review 已通过，见 `GATEL_1C_CAPABILITY_MATRIX_CONTRACT_REVIEW.md`。
- **NQ-GATEL-1C-CAPABILITY-MATRIX-CONTRACT-REVIEW = PASS / REVIEW ACCEPTED**（2026-06-23）：只读复核 GateL-1C capability matrix contract 的 enum、adapter/venue、trading、marketdata、credential/endpoint/permission 与 forbidden interpretation；P0/P1/P2 无阻断项。结论：合同可作为 GateL-1C frozen contract-only baseline；不授权真实交易所、LIVE、真实 credential、AI、DH runtime 或 adapter future-real-ready。下一步 `NQ-GATEL-1D-ERROR-MODEL-CONTRACT`。
- **路线图 GateL 语义已裁决（canonical，2026-06-22）**：经 `NQ-GATEL-CANONICAL-ROUTE-SYNC` 裁决，**GateL canonical = No-Real Exchange / MarketData Readiness**（planning / contract / readiness）。旧口径「GateL = AI Paper Trading」作废；**AI Paper Trading 后移到 GateM**（后续独立 AI/DH 阶段，当前 NOT STARTED），AI 小资金 LIVE → GateN，美股 → GateO，A 股 → GateP。AI Paper Trading 不属于 GateL 入场任务（见 `GATEL_PLAN.md` §6 / §10）。
- AI not started。
- DH integration not started / not connected to NQ。
- LIVE disabled。
- RealClient / real provider / real permission probe not implemented；既有 OKX/Binance adapter 含 legacy network-capable code，但未获准作为 real execution provider，且尚未达到 future-real readiness。
- Multi-exchange expansion not started。

## 路线原则

- 先把虚拟币做成完整 V1（GateI 已完成）。
- GateH 已完成交易工作台、历史行情接入和 dataset 绑定。
- GateI 已完成虚拟币量化 V1 完整闭环（策略版本、发布、回测追溯、评估增强、Paper Trading 运行闭环、风控回写、资金曲线、持仓曲线、交易复盘、异常停机）。
- GateJ 已完成 Paper Trading 稳定运行验收。
- GateK-PLAN 用于规划 GateJ 后的事实源收口、架构与测试基线、前端产品化、CI / 可观测性 / 部署基线、安全 hardening 和 Integration-0 只读登记；不能直接实现 AI、DH runtime、LIVE、真实交易所扩展或真实 adapter。
- NQ_CI_BASELINE_PLAN.md 已作为 CI baseline 文档落档（CI 状态权威以 STATUS.md 为准）；**NQ GateK CI mainline = COMPLETED / ACCEPTED**：Batch 1 first green，Batch 2 PostgreSQL/Flyway、Batch 3 no-outbound guard、Batch 4B secret scan、Batch 4C artifact/log redaction、Batch 4F-A dependency-audit preflight、Batch 5A no-backend frontend E2E 均 FROZEN / ACCEPTED；Batch 5B-ENV = FROZEN / ACCEPTED（freeze evidence run `27876451289`，卷宗 NQ_CI_SECURITY_BATCH_5B_ENV_FREEZE.md），Batch 5B-SMOKE = FROZEN / ACCEPTED（implementation plan reviewed / accepted；implementation DONE；ci-security-smoke job 已落地；first run evidence PASS（run 27903497008，9 jobs success）；freeze FROZEN / ACCEPTED，卷宗 NQ_CI_SECURITY_BATCH_5B_SMOKE_FREEZE.md；Batch 5B CLOSED / ACCEPTED），Batch 4F-B 至 4F-F = OPTIONAL BACKLOG / NOT STARTED，Static workflow assertion = OPTIONAL FUTURE HARDENING / NOT IMPLEMENTED。
- `NQ_CI_NO_OUTBOUND_GUARD_PLAN.md` 已作为 Batch 3 plan / implementation / freeze baseline 落档；Batch 3B 已实现最小 workflow / test-scope no-outbound guard，并由 GitHub Actions run `27634370657` first green confirmed（6 jobs green），经 Batch 3E freeze review 固化为 FROZEN / ACCEPTED，是当前 `dev` no-outbound guard baseline。
- GateL 进入 No-Real Exchange / MarketData Readiness（planning / contract / readiness，不实盘、不接真实交易所）。
- AI Paper Trading 是后续独立阶段（GateM），当前 NOT STARTED。
- GateN 才允许 AI 小资金 LIVE。
- 美股/A 股复用虚拟币 V1 沉淀的通用底座。

## 当前边界

- Current stage: GateJ completed。
- Next: GateK-PLAN。
- NQ / DH 三轮只读审计已完成；DH not integrated；GateK implementation not started；AI not started；LIVE disabled。
- Integration-0 allowed only as contract / mock / documentation work line, not runtime integration；它是独立文档与契约工作线，不等于 GateK 实现，也不是真实集成。
- NQ-DH Integration-0 契约冻结已完成（contract / mock / docs）；下一步只允许 mock / contract test 设计或安全文档固化，禁止真实联调；真实通道必须等 Integration-1 并先修复 DH P1-4 残留（rate limit / memory cap / replay nonce 持久化）。
- NQ-DH Integration-0 mock / contract test 详细矩阵（15 项）已设计完成，contract test 代码已实现并通过 implementation review；**Integration-0 safety gate CLOSED / ACCEPTED**（见 `NQ_DH_INTEGRATION0_ACCEPTANCE_REPORT.md`）。
- 下一步只允许 Integration-1 planning-only audit / DH P1-4 residual fix planning / GateK-PLAN 文档规划；禁止直接 Integration-1 实现、真实只读通道、真实 HTTP、RealClient、Provider、LIVE、AI 自动交易。
- 不接入 AI。
- 不做 AI 信号。
- 不做 AI Paper Trading。
- 不做真实 LIVE 下单。
- 不接入 DH。
- 不新增多交易所扩展。
- 不做美股/A 股。
- 不做合约全量。
- 不做高频。
- 不做复杂因子平台。
- UI/UX professionalism remains post-freeze remediation。
