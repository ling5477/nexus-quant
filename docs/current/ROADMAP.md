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
