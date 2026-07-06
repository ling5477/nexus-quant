# GateL-1C Capability Matrix Contract Review

任务：NQ-GATEL-1C-CAPABILITY-MATRIX-CONTRACT-REVIEW
日期：2026-06-23
分支：dev
任务类型：DOCUMENTATION_REVIEW + CONTRACT_REVIEW + ADAPTER_BOUNDARY_REVIEW
结论：**PASS / REVIEW ACCEPTED**。

> 本 review 只复核 `GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md` 是否足以作为 GateL-1C capability matrix contract 冻结对象。
> 本轮未实现 adapter、未修改交易逻辑、未新增 API/DTO/migration/workflow，未访问外网或交易所，未读取真实 credential。

---

## 1. Scope

### 已审查

- `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md`
- GateL current docs：`GATEL_PLAN.md`、`GATEL_1B_NO_REAL_HARDENING_PLAN.md`、`GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`
- Adapter evidence：`backend/nq-adapter-api/**`、`backend/nq-adapter-okx/**`、`backend/nq-adapter-binance/**`

### 明确未审查

- 其他 backend 模块、frontend、research、scripts、deploy、workflow、migration。
- `.env`、API key、secret、token、pem、key、jks、p12、日志 dump、backup。
- 任何真实交易所、真实 provider、真实 credential bridge、LIVE、AI、DH runtime。

### 本轮不涉及

- 不修代码，不新增或修改 Java / TypeScript / Python。
- 不新增 API / DTO / migration / workflow。
- 不实现 RealClient、real provider、真实 permission probe、真实 credential governance bridge。
- 不删除 `AdapterOrderAck` / `AdapterOrderSnapshot` 的 `rawPayload` 字段。
- 不把 adapter 标记为 future-real-ready。

## 2. Current Baseline Reviewed

- GateL canonical：**No-Real Exchange / MarketData Readiness**。
- GateL-1B overall No-Real hardening baseline：**FROZEN / ACCEPTED**。
- P1-A / P1-B / P1-C producer suppression / P1-D：**CLOSED / ACCEPTED**。
- P1-C rawPayload field deletion：**NOT DONE / SEPARATE COMPATIBILITY TASK**。
- Adapter readiness：**NOT READY / NOT FROZEN / NOT AUTHORIZED**。
- LIVE：**DISABLED**。
- AI：**NOT STARTED**。
- DH runtime：**NOT INTEGRATED**。
- RealClient / real provider / real permission probe / real credential governance bridge：**NOT IMPLEMENTED**。

## 3. Review Verdict

**PASS / REVIEW ACCEPTED**。

`GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md` 满足 GateL-1C contract-only 要求。该合同可以作为冻结的 capability matrix contract 使用，前提是继续按合同约束理解：matrix 只能表达当前能力状态，不能启用真实交易所能力、LIVE、真实 credential、AI、DH runtime 或 adapter future-real readiness。

GateL-1C review 接受后，推荐下一步进入 **NQ-GATEL-1D-ERROR-MODEL-CONTRACT**。不得直接进入 real adapter / real provider / RealClient / LIVE / AI / DH runtime。

## 4. Capability Enum Review

结论：**PASS**。

合同定义并使用了全部必需 status：

- `CLOSED_NO_REAL`
- `DISABLED_SENTINEL`
- `NO_REAL_DISABLED`
- `STUB_ONLY`
- `NOT_IMPLEMENTED`
- `FUTURE_REAL_REQUIRES_GATE`
- `FORBIDDEN_IN_GATEL`
- `UNKNOWN_REQUIRES_REVIEW`

审查结论：

- enum 语义明确区分 no-real hardening closure、disabled sentinel、stub、not implemented、future gate、GateL forbidden 与 unknown review。
- enum 未把 `CLOSED_NO_REAL` 写成真实交易所能力。
- enum 未把 `FUTURE_REAL_REQUIRES_GATE` 写成 future-real-ready。
- enum 支持后续 agent 区分 no-real / disabled / stub / future-gated / forbidden。

## 5. Adapter / Venue Matrix Review

结论：**PASS**。

合同覆盖了全部必需 adapter / venue：

- Noop adapter：`NO_REAL_DISABLED` / `STUB_ONLY`，明确不是真实 success，也不是 future-real-ready。
- OKX adapter：`CLOSED_NO_REAL` + `DISABLED_SENTINEL` + unconfigured credential，明确不是真实交易所授权，也不是 future-real-ready。
- Binance adapter：`CLOSED_NO_REAL` + `DISABLED_SENTINEL` + unconfigured credential，明确不是真实交易所授权，也不是 future-real-ready。
- Future-real adapter placeholder：`FUTURE_REAL_REQUIRES_GATE` / `NOT_IMPLEMENTED`。
- Permission probe placeholder：boundary classifier only；real probe `NOT_IMPLEMENTED`。
- Marketdata no-real / future-real placeholder：no-real 为 `NO_REAL_DISABLED` / `STUB_ONLY`，future-real 为 `FUTURE_REAL_REQUIRES_GATE`。

源码证据一致：

- `NoopMarketDataAdapter` 固定返回 `NO_REAL_DISABLED`、`subscribed=false`、`FATAL_FAILURE`、`retryable=false`。
- `OkxRuntimeConfig` 默认 REST/WS endpoint 为 `disabled://okx-not-configured` / `disabled://okx-ws-not-configured`，credential 为 `OkxApiCredentials.unconfigured()`。
- `BinanceRuntimeConfig` 默认 REST/WS endpoint 为 `disabled://binance-not-configured` / `disabled://binance-ws-not-configured`，credential 为 `BinanceApiCredentials.unconfigured()`。
- `OkxPermissionProbeBoundary` / `BinancePermissionProbeBoundary` 只是 forbidden endpoint 与错误分类边界，不是真实 probe adapter。

## 6. Trading Capability Review

结论：**PASS**。

合同覆盖了必需 trading dimensions：

- spot trading
- margin trading
- futures / perpetual
- place order
- cancel order
- query order
- account balance
- REST private trading
- audit / ledger ownership
- risk gate dependency

审查结论：

- OKX / Binance 的 spot、place、cancel、query、balance 均未写成当前真实能力；真实能力均要求 `FUTURE_REAL_REQUIRES_GATE`。
- margin trading、futures / perpetual 在 GateL 内保持 `FORBIDDEN_IN_GATEL` 或 future-gated，不构成当前能力。
- 下单、撤单、查单、余额等私有能力均未被授权为真实交易所能力。
- 合同明确 adapter 不能绕过 `RiskGate`、`OrderStateMachine`、`Ledger`、`Audit`。

## 7. Marketdata Capability Review

结论：**PASS**。

合同覆盖了必需 marketdata dimensions：

- REST public marketdata
- WebSocket public marketdata
- WebSocket private/user stream
- historical OHLCV
- ticker
- orderbook
- trades
- bars / trades / order-book subscription

审查结论：

- NoopMarketDataAdapter 被标记为 `NO_REAL_DISABLED`，没有写成真实 success。
- OKX / Binance REST public marketdata、WS public marketdata、ticker、orderbook、trades、subscription 均未写成当前真实 provider 能力。
- historical OHLCV 明确不等于真实交易所授权；legacy historical adapters 的存在不能解释为 current real provider readiness。
- WebSocket private/user stream 未被授权。

## 8. Credential / Endpoint / Permission Review

结论：**PASS**。

合同覆盖了必需 credential / endpoint / permission dimensions：

- credential source
- endpoint default
- permission probe
- raw payload boundary
- testnet/sandbox
- rate limit policy
- retry policy
- kill switch / no-outbound guard

审查结论：

- OKX endpoint default = `DISABLED_SENTINEL`。
- Binance endpoint default = `DISABLED_SENTINEL`。
- OKX / Binance credential source = unconfigured / no-real default。
- real credential governance bridge = `NOT_IMPLEMENTED`。
- real permission probe = `NOT_IMPLEMENTED`。
- testnet/sandbox 没有被写成安全默认值或 LIVE 授权。
- rate limit / retry / circuit breaker / kill switch 均为 future gate 条件，不是当前真实能力。
- rawPayload producer suppression 已完成，但 rawPayload field deletion 未完成；合同未把两者混淆。

## 9. Forbidden Interpretation Review

结论：**PASS**。

合同明确禁止以下误读：

- 把 OKX / Binance existing adapter 写成 future-real-ready。
- 把 OKX / Binance existing adapter 等同于真实交易所授权。
- 把 GateL-1B P1-A/B/C/D closure 写成 adapter readiness。
- 把 `NoopMarketDataAdapter` disabled 状态写成真实 success。
- 把 `disabled://` sentinel 写成真实 endpoint。
- 把 unconfigured credential 写成 credential readiness。
- 把 permission boundary classifier 写成真实 permission probe。
- 把 historical OHLCV legacy adapter 写成真实 marketdata 授权。
- 把 producer suppression 写成 rawPayload field deletion。
- 绕过 RiskGate / OrderStateMachine / Ledger / Audit。
- 启用 LIVE / AI / DH runtime / RealClient / real provider / real credential bridge / real permission probe。

## 10. Findings

### P0

- 无。

### P1

- 无。

### P2

- 无阻断项。

### Residual / Follow-up

- `rawPayload` field deletion 仍是独立 compatibility task，未在 GateL-1C review 中关闭。
- GateL-1D error model contract 尚未开始。
- GateL-1E future-real readiness checklist refinement 尚未开始。

## 11. Commands Run

- `Get-Location`
- `git status --short`
- `git branch --show-current`
- `Get-Content` bounded reads for task attachment, project instructions, current GateL docs, and allowed adapter files.
- `rg --files backend/nq-adapter-okx`
- `rg --files backend/nq-adapter-binance`
- `rg -n "disabled://|unconfigured|PermissionProbe|suppressedOrderRawPayload|rawPayload" backend/nq-adapter-okx backend/nq-adapter-binance`
- Post-edit validation commands are recorded in `TESTING.md` and final task output.

## 12. Acceptance Criteria

- Capability status enum completeness：**PASS**。
- Adapter / venue coverage：**PASS**。
- Trading capability coverage：**PASS**。
- Marketdata capability coverage：**PASS**。
- Credential / endpoint / permission coverage：**PASS**。
- Forbidden interpretation coverage：**PASS**。
- No-real / disabled / stub not misrepresented as real capability：**PASS**。
- Adapter readiness remains NOT READY / NOT FROZEN / NOT AUTHORIZED：**PASS**。
- Docs-only scope：**PASS**。

## 13. Rollback

- Delete `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT_REVIEW.md`.
- Restore this task's edits in `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md`, `GATEL_PLAN.md`, `README.md`, `ROADMAP.md`, `STATUS.md`, `TESTING.md`, and `WORKLOG.md`.
- No code, DB, migration, workflow, runtime, credential, provider, exchange, LIVE, AI, or DH side effect exists from this docs-only review.

## 14. Recommended Next Task

**NQ-GATEL-1D-ERROR-MODEL-CONTRACT**。

If a separate process closeout is required before 1D, use a docs-only GateL-1C freeze-close task first. It must not implement real adapter, real provider, RealClient, LIVE, AI, DH runtime, rawPayload field deletion, real credential governance bridge, or real permission probe.

## 15. Final Recommendation

**NQ-GATEL-1C-CAPABILITY-MATRIX-CONTRACT-REVIEW：PASS / REVIEW ACCEPTED。**

是否允许真实交易所接入：**NO**。
是否允许 LIVE：**NO**。
是否允许真实 credential：**NO**。
是否允许 AI / DH runtime：**NO**。
是否允许将 adapter 标记为 future-real-ready：**NO**。
推荐下一步：**NQ-GATEL-1D-ERROR-MODEL-CONTRACT**。
