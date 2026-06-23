# GateL-1E Future-Real Readiness Checklist Refinement

任务：NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT
日期：2026-06-23
分支：dev
任务类型：DOCUMENTATION + READINESS_CHECKLIST_REFINEMENT + SECURITY_BOUNDARY_REVIEW + FUTURE_REAL_GATE_PLANNING
结论：**PASS / CHECKLIST CREATED / PENDING REVIEW（checklist-only）**
状态：**GateL-1E readiness checklist CREATED / PENDING REVIEW**；adapter readiness **NOT READY / NOT FROZEN / NOT AUTHORIZED**。

> 本文件只细化 future-real readiness checklist，定义未来若要进入真实交易所接入 Gate 必须满足的准入门槛。
> 本 checklist **不授权**真实交易所接入、**不启用** LIVE、**不实现** real adapter / real provider / RealClient / 真实 permission probe / real credential governance bridge。
> 满足或引用本 checklist 不构成授权；任何真实能力仍须在独立 Gate + 独立安全审计 + 用户显式授权后才允许启动。

## 1. Scope

### 已检查（只读）

- `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN.md`、`GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md`。
- `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md`、`GATEL_1C_CAPABILITY_MATRIX_CONTRACT_FREEZE_REVIEW.md`。
- `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT.md`、`GATEL_1D_ERROR_MODEL_CONTRACT_FREEZE_REVIEW.md`。
- `docs/current/GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`。
- `backend/nq-adapter-api/**`、`backend/nq-adapter-okx/**`、`backend/nq-adapter-binance/**`（仅核对 readiness 事实）。
- 既有安全基线组件（仅核对名称与边界，未改）：`EnvSafetyValidator` / `EnvSafetyGuardConfiguration`、`NoOutboundExchangeGuardTest`、`NoRealExchangeCredentialPermissionProbePort`、`KillSwitchService` / `KillSwitchRiskRule`、`RiskGate` / `NoopRiskGate` / `PreTradeRiskService`、`OrderStateMachine` / `InMemoryOrderStateMachine`、`AuditLogRepository` / `JdbcAuditLogRepository`、`JdbcLedgerPostingRepository`、`CredentialPermissionProbeService`。

### 明确不涉及

- Java / TypeScript / Python 代码修改。
- API / DTO / migration / historical migration / workflow / frontend / research / scripts / deploy 修改。
- `.env`、API key、secret、token、pem、key、jks、p12、日志 dump、backup。
- OKX / Binance / Bybit / Bitget / Coinbase / Gate / Kraken / Crypto.com / Hyperliquid 外联。
- LIVE、AI、DH runtime、RealClient、real provider、真实 permission probe、真实 credential governance bridge。
- 下单、撤单、转账、提现；`AdapterOrderAck` / `AdapterOrderSnapshot` rawPayload 字段删除。

## 2. Current Frozen Baseline

- GateL canonical：**No-Real Exchange / MarketData Readiness**。
- GateL-1B overall No-Real hardening baseline：**FROZEN / ACCEPTED**。
- GateL-1C capability matrix contract：**FROZEN / ACCEPTED**。
- GateL-1D error model contract：**FROZEN / ACCEPTED**。
- P1-A / P1-B / P1-C producer suppression / P1-D：**CLOSED / ACCEPTED**。
- P1-C rawPayload field deletion：**NOT DONE / SEPARATE COMPATIBILITY TASK**。
- Adapter readiness：**NOT READY / NOT FROZEN / NOT AUTHORIZED**。
- LIVE：**DISABLED**。AI：**NOT STARTED**。DH runtime：**NOT INTEGRATED**。
- RealClient / real provider / real permission probe / real credential governance bridge：**NOT IMPLEMENTED**。

本 checklist 引用 GateL-1C capability matrix 与 GateL-1D error model 作为能力状态与错误分类基线，不放宽任一冻结边界。

## 3. Non-Authorization Statement

- 本 checklist 是**准入门槛定义**，不是授权、不是 readiness 证明、不是 future-real-ready 标记。
- checklist 项目“已满足”不代表可进入真实交易；所有项目满足后仍须独立 Gate + 安全审计 + 用户显式授权。
- 不允许在 GateL 内授权真实交易所；不允许把 checklist 通过解释为授权。
- 不允许从 No-Real GateL 直接进入实盘；real execution 必须经独立 Gate。
- adapter readiness 在本轮结束后仍为 **NOT READY / NOT FROZEN / NOT AUTHORIZED**。

## 4. Future-Real Gate Entry Checklist（总览，准入门槛）

进入任何真实交易所接入 Gate 前，下列每一类 checklist（§5–§15）必须全部满足、留证、并经独立安全审计与用户显式授权。任一项未满足即 fail-closed，不得进入真实交易。

| 门类 | 章节 | 准入要求 |
| --- | --- | --- |
| 安全前置 | §5 | no-outbound / EnvSafety / sentinel / redaction / kill switch 全部保持 fail-closed。 |
| Credential | §6 | credential governance bridge 设计 + lifecycle + scope binding，禁止进程环境派生。 |
| Network / no-outbound | §7 | endpoint allowlist + sentinel 回归 + no-outbound guard 更新与证据。 |
| Adapter 实现 | §8 | future-real adapter / RealClient / real provider 各自独立 Gate。 |
| Permission probe | §9 | real probe 仅 allowlisted read-only；order/cancel/withdraw/transfer fail-closed。 |
| Marketdata | §10 | public 先于 private；testnet/sandbox 仍须 Gate。 |
| Trading execution | §11 | paper-first；最小资金 / 权限 / symbol / order type。 |
| Risk / order / ledger / audit | §12 | RiskGate / OrderStateMachine / Ledger / Audit 不可绕过。 |
| Testing / CI | §13 | unit / contract / fail-closed / redaction / replay / CI evidence。 |
| Rollout / rollback / incident | §14 | 分阶段 rollout + 回滚到 disabled/no-real + incident plan。 |
| 用户授权 | §15 | 显式授权 + 独立 Gate 名 + 独立 freeze。 |

## 5. Security Checklist（安全前置条件）

- [ ] `NoOutboundExchangeGuard` 保持 fail-closed，覆盖 OKX/Binance/Bybit/Bitget/Coinbase/Gate/Kraken/Crypto.com/Hyperliquid，且 CI `no-outbound-guard` job 保留。
- [ ] `EnvSafetyValidator` / `EnvSafetyGuardConfiguration` 启动期 fail-closed；LIVE/AI/DH/real-provider/real-client/real-exchange profile 缺省为 false。
- [ ] 默认 endpoint 保持 `disabled://` sentinel（OKX/Binance REST+WS），真实 endpoint 仅显式 env opt-in。
- [ ] secret scan gate 保留；`.env.example` placeholder-only，不含真实凭证。
- [ ] redaction policy 覆盖 provider body / headers / signature / cookie / token / private key path / query string。
- [ ] no raw provider payload propagation：rawPayload producer suppression 保持 CLOSED（GateL-1D `RAW_PAYLOAD_SUPPRESSED` 安全边界）。
- [ ] `KillSwitchService` / `KillSwitchRiskRule` 可用且优先级高于任何真实下单路径。
- [ ] circuit breaker / rate limit policy 落地（对应 GateL-1D `RATE_LIMITED` / `VENUE_UNAVAILABLE` 受控 conditional retry）。
- [ ] rollback plan、incident plan、audit log 在真实接入前已定义并留证。

## 6. Credential Checklist（凭证前置条件）

- [ ] credential governance bridge 设计 + 独立安全审计；adapter 不从 env / system property / .env 派生 credential active material（对应 GateL-1D `CREDENTIALS_MISSING` 禁止 fallback）。
- [ ] credential lifecycle：创建 / 轮换 / 失效 / 撤销 / 审计全链路定义。
- [ ] owner / account / tenant / active credential version / permission scope binding 明确，由治理桥注入而非 adapter 猜测。
- [ ] credential material 不进入日志 / diff / 报告 / audit / ledger / 错误信息；脱敏验证留证。
- [ ] 默认 `*.unconfigured()` 保持；未配置时 authenticated 请求网络前 fail-closed（`OKX_CREDENTIALS_MISSING` / `BINANCE_CREDENTIALS_MISSING`）。
- [ ] permission scope allowlist：默认 read-only，trade 权限分阶段显式开启。
- [ ] 禁止默认开启 withdraw / transfer 权限；任何资金转移能力须单独授权 Gate。

## 7. Network / No-Outbound Checklist（网络前置条件）

- [ ] endpoint allowlist 设计：仅允许已授权 venue / 环境 / host。
- [ ] no-outbound guard 更新与 sentinel 回归证据（`disabled://` 仍 loud fail-closed）。
- [ ] IP allowlist / venue-side permission 配置与验证（对应 GateL-1D `IP_NOT_ALLOWED`）。
- [ ] testnet/sandbox endpoint 仍视为外部，须显式 Gate + no-outbound review，不默认安全。
- [ ] rate limit policy + backoff + circuit breaker 网络层落地，禁止无限重试。
- [ ] 真实 endpoint 仅显式 env opt-in，代码级默认不得指向真实交易所 host。

## 8. Adapter Implementation Checklist（适配器实现前置条件）

- [ ] RealClient / real provider 仍 **NOT IMPLEMENTED**；进入实现须各自独立 Gate。
- [ ] future-real adapter 必须另起 Gate（设计 review + 安全审计 + readiness 证据 + 用户授权）。
- [ ] real credential governance bridge 必须另起 Gate。
- [ ] permission probe real adapter 必须另起 Gate。
- [ ] rawPayload field deletion 若实施，必须作为 separate compatibility task（不在 future-real adapter Gate 内夹带）。
- [ ] adapter 仍只做交易所适配，不直接写库、不拥有 ledger / audit、不绕过架构边界。
- [ ] error model 实现须与 GateL-1D 合同映射一致，不得新增 enum 削弱 fail-closed。

## 9. Permission Probe Checklist（权限探测前置条件）

- [ ] real permission probe 仅调用 allowlisted read-only endpoint。
- [ ] order / cancel / withdraw / transfer / blank endpoint 一律 fail-closed（保持 `OkxPermissionProbeBoundary` / `BinancePermissionProbeBoundary` forbidden endpoint 边界）。
- [ ] probe 结果不回传 raw response / signature / headers / credential material 到 Service / 日志 / audit metadata。
- [ ] 默认 `NoRealExchangeCredentialPermissionProbePort`（SKIPPED / REAL_EXCHANGE_PROBE_DISABLED）保持，real probe 启用须显式授权 Gate。
- [ ] probe 错误分类复用 GateL-1D `AUTH_FAILED` / `PERMISSION_DENIED` / `IP_NOT_ALLOWED` / `RATE_LIMITED` / `VENUE_UNAVAILABLE` 脱敏映射。

## 10. Marketdata Checklist（行情前置条件）

- [ ] Noop marketdata 保持 `NO_REAL_DISABLED`（非 success），real marketdata provider 须独立 Gate。
- [ ] REST/WS public marketdata 先于 private/user stream；private/user stream 需凭证，GateL 内禁止。
- [ ] historical OHLCV legacy adapter 不被当作当前真实 provider 授权。
- [ ] real marketdata 须 rate limit policy + no-outbound review + 显式 endpoint 授权。
- [ ] testnet/sandbox marketdata 仍须 Gate，不默认安全。

## 11. Trading Execution Checklist（交易执行前置条件）

- [ ] paper-first execution boundary：real execution 前必须先在 paper / SIM 验证闭环。
- [ ] real execution 限定最小资金、最小权限、最小 symbol、最小 order type。
- [ ] 明确 allowed venue / allowed account / allowed symbol / allowed strategy。
- [ ] 明确 max notional / max order count / daily loss limit / kill-switch threshold（配置化、保守默认）。
- [ ] read-only → trade permission 分阶段；不默认开启 trade。
- [ ] 禁止默认开启 withdraw / transfer；任何资金转移单独授权。
- [ ] 下单 / 撤单 / 回调 / 消息消费幂等（idempotency key + 幂等窗口 + 重复请求语义）。

## 12. Risk / Order / Ledger / Audit Checklist（业务一致性前置条件）

- [ ] `RiskGate`（`PreTradeRiskService` + 风控规则 + `KillSwitchRiskRule`）不可绕过；任何真实下单先过 RiskGate（对应 GateL-1D `RISK_REJECTED`）。
- [ ] `OrderStateMachine` / `InMemoryOrderStateMachine` 不可绕过；状态流转校验当前状态，禁止无条件覆盖（对应 `ORDER_STATE_REJECTED`）。
- [ ] Ledger（`JdbcLedgerPostingRepository`）一致性由 NQ core 拥有；adapter 不写 ledger（对应 `LEDGER_REJECTED`）。
- [ ] Audit（`AuditLogRepository` / `JdbcAuditLogRepository`）由 NQ core 拥有；真实执行须留可追溯 audit，且脱敏。
- [ ] RISK_REJECTED / ORDER_STATE_REJECTED / LEDGER_REJECTED 仍由 NQ core 事实源决定，adapter 只透传。
- [ ] 事务边界最小化；禁止在 DB 事务中等待交易所长耗时响应；多表写入明确 Outbox / 幂等 / 补偿。

## 13. Testing / CI Checklist（测试前置条件）

- [ ] unit tests（adapter 映射、错误分类、fail-closed）。
- [ ] contract tests（GateL-1C capability / GateL-1D error model 合同）。
- [ ] no-outbound smoke tests（保持 `NoOutboundExchangeGuardTest` 绿）。
- [ ] credential redaction tests（无 secret / token / signature 泄漏）。
- [ ] permission probe dry-run tests（forbidden endpoint fail-closed）。
- [ ] sandbox/testnet isolation tests，仅在明确授权 Gate 内执行。
- [ ] fail-closed tests（`disabled://` sentinel、`*_CREDENTIALS_MISSING`、`UNKNOWN_REQUIRES_REVIEW`）。
- [ ] rate-limit / circuit-breaker tests（`RATE_LIMITED` / `VENUE_UNAVAILABLE` 受控重试，禁止无限重试）。
- [ ] replay / idempotency tests（下单 / 撤单 / 回调 / 消息重复）。
- [ ] order state machine tests（非法状态流转拒绝）。
- [ ] risk gate tests（拒绝路径 + kill switch）。
- [ ] ledger / audit tests（一致性 + 脱敏）。
- [ ] CI evidence（run id + headSha + jobs success 留证）。
- [ ] GateL-1B/1C/1D invariant regression evidence（Noop `NO_REAL_DISABLED`、`disabled://` sentinel、`*.unconfigured()`、producer suppression、enum/retry 语义未回退）。

## 14. Rollout / Rollback / Incident Checklist（发布前置条件）

- [ ] 分阶段 rollout：read-only → 最小资金 paper-adjacent → 最小资金 LIVE，逐阶段授权。
- [ ] rollback plan：可一键回滚到 disabled / no-real 默认（`disabled://` sentinel + `*.unconfigured()`）。
- [ ] kill switch 演练与触发阈值定义。
- [ ] incident plan：异常停机、风控拒绝、交易所故障、凭证泄漏的处置与上报路径。
- [ ] PAPER / LIVE 硬隔离；LIVE 默认 DISABLED，开启须独立授权。
- [ ] 可观测性：关键业务路径日志（脱敏）+ 监控指标 + 告警。

## 15. Explicit User Authorization Checklist（授权前置条件）

- [ ] 用户对目标 venue / 环境 / 账户 scope / 能力 / 资金上限的显式授权。
- [ ] 独立 Gate 名称（不复用 GateL；real execution 不在 GateL 范围内）。
- [ ] 独立安全审计（专项，覆盖 §5–§14）。
- [ ] 独立 CI freeze（按实际实现影响选择 Maven / frontend / Python / CI scope）。
- [ ] 独立 rollout plan + 独立 rollback plan。
- [ ] 不允许在 GateL 内授权真实交易所。
- [ ] 不允许把 checklist 通过解释为授权。

## 16. Forbidden Interpretations

以下解释一律禁止：

- 把本 readiness checklist 当作真实交易授权或 future-real-ready 标记。
- 把任一 checklist 项“满足”当作可进入真实交易、可启用 LIVE、可注入真实 credential。
- 把 GateL-1B/1C/1D 冻结当作 adapter readiness 或真实交易所授权。
- 把 OKX / Binance 既有 adapter 代码当作 future-real-ready。
- 把 `disabled://` sentinel / `*.unconfigured()` / Noop `NO_REAL_DISABLED` 当作可用真实能力。
- 把 checklist 完成当作可跳过独立 Gate / 独立安全审计 / 用户显式授权。
- 把 GateL-1E 写成 implementation started 或 real adapter started。
- 借细化 checklist 之名削弱 no-outbound / EnvSafety / sentinel / redaction / kill switch / RiskGate / OrderStateMachine / Ledger / Audit 任一边界。

## 17. Acceptance Criteria

- 代码实现前置条件已细化（§8）：RealClient / real provider NOT IMPLEMENTED；future-real adapter / real credential bridge / permission probe real adapter 各自另起 Gate；rawPayload field deletion 作为 separate compatibility task；禁止从 No-Real GateL 直接进入实盘。
- 安全前置条件已细化（§5）：no-outbound guard / EnvSafetyValidator / `disabled://` sentinel / secret scan / credential lifecycle / scope binding / permission scope allowlist / IP allowlist / rate limit / circuit breaker / kill switch / audit log / rollback / incident / redaction / no raw payload propagation 全覆盖。
- 测试前置条件已细化（§13）：unit / contract / no-outbound smoke / redaction / probe dry-run / sandbox isolation / fail-closed / rate-limit·circuit-breaker / replay·idempotency / order state machine / risk gate / ledger·audit / CI evidence / GateL-1B·1C·1D regression evidence 全覆盖。
- 业务与交易前置条件已细化（§11 / §12）：RiskGate / OrderStateMachine / Ledger / Audit 不可绕过；paper-first；最小资金·权限·symbol·order type；allowed venue/account/symbol/strategy；max notional / max order count / daily loss limit / kill-switch threshold；read-only→trade 分阶段；禁止默认 withdraw/transfer。
- 权限与授权前置条件已细化（§15）：用户显式授权 / 独立 Gate 名 / 独立安全审计 / 独立 CI freeze / 独立 rollout / 独立 rollback；不允许 GateL 内授权；不允许 checklist 解释为授权。
- 输出状态明确：checklist CREATED / PENDING REVIEW；adapter readiness NOT READY / NOT FROZEN / NOT AUTHORIZED；real exchange access / LIVE / real credential / AI / DH runtime / future-real-ready 全部 NO。
- 本轮 checklist-only：未改代码、未新增 API / DTO / migration / workflow；仅 `docs/current/**` 变更。

## 18. Findings

### P0

- 无。本轮 docs-only checklist refinement，没有 runtime / DB / credential / provider / exchange / LIVE / AI / DH side effect。

### P1

- 无。checklist 明确为准入门槛，不构成授权；未放宽任一冻结边界。

### P2

- 本 checklist 为 refinement / PENDING REVIEW；建议后续 `NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT-REVIEW` 复核后再决定是否 freeze。
- §8 rawPayload field deletion 仍是 separate compatibility task，不在本 checklist 或 future-real adapter Gate 内夹带。
- checklist 的真实 backoff / circuit breaker / kill switch policy、credential governance bridge、real probe 均为 future-real，须在独立实现 Gate 落地并各自留证。

## 19. Commands Run

- `git status --short` / `git branch --show-current`。
- `git grep -l` 核对安全基线组件名（`EnvSafetyValidator`、`NoOutboundExchangeGuardTest`、`NoRealExchangeCredentialPermissionProbePort`、`KillSwitchService`、`RiskGate`、`OrderStateMachine`、`AuditLogRepository`、`JdbcLedgerPostingRepository`）。
- bounded reads：GateL-1B/1C/1D 冻结文档与 GateL current docs。
- Post-edit validation：`git diff --check` / `git diff --stat` / `git status --short` / bounded `rg` 禁止措辞检查 / scope check。

## 20. Rollback

- 删除 `docs/current/GATEL_1E_READINESS_CHECKLIST_REFINEMENT.md`。
- 还原本轮对 `docs/current/GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md` 的同步。
- 无 code / DB / migration / workflow / runtime / credential / provider / exchange / LIVE / AI / DH side effect。

## 21. Next Task Recommendation

**NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT-REVIEW**。

下一任务须保持 docs-only，除非另行授权。不得实现真实 adapter、real provider、RealClient、LIVE、AI、DH runtime、rawPayload field deletion、real credential governance bridge 或 real permission probe；不得把 checklist 写成真实交易授权；不得把 GateL-1E 写成 implementation started。

## 22. Final Recommendation

**NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT：PASS / CHECKLIST CREATED / PENDING REVIEW。**

- GateL-1E readiness checklist：**CREATED / PENDING REVIEW**。
- adapter readiness：**NOT READY / NOT FROZEN / NOT AUTHORIZED**。
- 是否允许真实交易所接入：**NO**。
- 是否允许 LIVE：**NO**。
- 是否允许真实 credential：**NO**。
- 是否允许 AI / DH runtime：**NO**。
- 是否允许将 adapter 标记为 future-real-ready：**NO**。
- 推荐下一步：**NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT-REVIEW**。
