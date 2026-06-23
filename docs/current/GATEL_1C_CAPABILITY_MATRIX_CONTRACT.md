# GateL-1C Capability Matrix Contract

任务：NQ-GATEL-1C-CAPABILITY-MATRIX-CONTRACT
日期：2026-06-23
分支：dev
结论：**PASS / CONTRACT FROZEN**
状态：**GateL-1C capability matrix contract frozen**；GateL-1B overall No-Real hardening baseline **FROZEN / ACCEPTED**；adapter readiness **NOT READY / NOT FROZEN / NOT AUTHORIZED**。

> 本文件只定义 capability matrix 合同，不实现 adapter、不修改交易逻辑、不新增 API / DTO / migration / workflow。
> capability matrix 只能表达当前能力状态，不能启用能力。任何 real exchange capability 在 GateL 内均不得作为已授权真实交易能力解释。

## 1. Scope

### 已检查

- `backend/nq-adapter-api/**`：adapter API、Noop marketdata/account stub、order ack/snapshot raw payload 字段、marketdata subscription ack、historical kline port。
- `backend/nq-adapter-okx/**`：OKX runtime config、credential placeholder、permission probe boundary、exchange adapter rawPayload producer suppression、historical kline adapter。
- `backend/nq-adapter-binance/**`：Binance runtime config、credential placeholder、permission probe boundary、exchange adapter rawPayload producer suppression、historical kline adapter。
- `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN.md`。
- `docs/current/GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md`。
- `docs/current/GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`。

### 明确不涉及

- Java / TypeScript / Python 代码修改。
- API / DTO / migration / historical migration / workflow / frontend / research / scripts / deploy 修改。
- `.env`、API key、secret、token、pem、key、jks、p12、日志 dump、backup。
- OKX / Binance / Bybit / Bitget / Coinbase / Gate / Kraken / Crypto.com / Hyperliquid 外联。
- LIVE、AI、DH runtime、RealClient、real provider、真实 permission probe、真实 credential governance bridge。
- 下单、撤单、转账、提现。
- `AdapterOrderAck` / `AdapterOrderSnapshot` rawPayload 字段删除。

## 2. Current Frozen Baseline

- GateL canonical：**No-Real Exchange / MarketData Readiness**。
- GateL-1B overall No-Real hardening baseline：**FROZEN / ACCEPTED**。
- P1-A：**CLOSED / ACCEPTED**，Binance endpoint default sentinel / no-outbound hardening frozen。
- P1-B：**CLOSED / ACCEPTED**，OKX/Binance runtime credential source hardening frozen。
- P1-C producer suppression：**CLOSED / ACCEPTED**，OKX/Binance `AdapterOrderAck` / `AdapterOrderSnapshot` rawPayload producer suppression frozen。
- P1-C rawPayload field deletion：**NOT DONE / SEPARATE COMPATIBILITY TASK**。
- P1-D：**CLOSED / ACCEPTED**，`NoopMarketDataAdapter` no-real disabled status hardening frozen。
- Adapter readiness：**NOT READY / NOT FROZEN / NOT AUTHORIZED**。
- LIVE：**DISABLED**。
- AI：**NOT STARTED**。
- DH runtime：**NOT INTEGRATED**。
- RealClient / real provider / real permission probe / real credential governance bridge：**NOT IMPLEMENTED**。

GateL-1B 关闭只代表 No-Real hardening baseline 完成，不代表 OKX / Binance adapter 已具备真实交易所接入资格，也不代表 future-real readiness。

## 3. Capability Status Enum

| Status | Contract meaning | Authorization effect |
| --- | --- | --- |
| `CLOSED_NO_REAL` | No-Real hardening 或 no-real contract 已冻结，当前只可作为禁实盘边界证据。 | 不启用真实能力。 |
| `DISABLED_SENTINEL` | 默认 endpoint / runtime path 指向 `disabled://` 或等价不可路由 sentinel。 | 不允许外联；显式 real endpoint 仍须另起 Gate。 |
| `NO_REAL_DISABLED` | Noop/no-real adapter 明确返回 disabled，不伪装为 success。 | 不允许解释成 provider 已就绪。 |
| `STUB_ONLY` | 仅存在 stub、boundary classifier、fixture、mock 或合同占位。 | 不允许访问真实交易所。 |
| `NOT_IMPLEMENTED` | 当前没有实现、没有 provider、没有 runtime bridge 或没有 adapter。 | 不存在可用能力。 |
| `FUTURE_REAL_REQUIRES_GATE` | 未来真实能力必须另起 Gate、专项安全审计、readiness checklist 和用户显式授权。 | 当前不得使用。 |
| `FORBIDDEN_IN_GATEL` | GateL 内明确禁止的能力。 | 当前和 GateL 内均不得实现或启用。 |
| `UNKNOWN_REQUIRES_REVIEW` | 当前证据不足，必须先只读 review。 | review 前不得启用或宣称支持。 |

## 4. Venue / Adapter Matrix

| Adapter / venue | Current contract status | Current capability interpretation | Readiness / authorization |
| --- | --- | --- | --- |
| Noop adapter | `NO_REAL_DISABLED` for marketdata subscription；`STUB_ONLY` for account snapshot | Contract wiring only；marketdata bars/trades/order-book returns disabled ack, not success；account returns empty SIM snapshot. | Not a real exchange adapter；not future-real-ready. |
| OKX adapter | `CLOSED_NO_REAL` hardening baseline + `DISABLED_SENTINEL` endpoint default + unconfigured credential default | Legacy network-capable code exists, but current contract treats real OKX capability as not authorized. | `NOT READY / NOT FROZEN / NOT AUTHORIZED`；not future-real-ready. |
| Binance adapter | `CLOSED_NO_REAL` hardening baseline + `DISABLED_SENTINEL` endpoint default + unconfigured credential default | Legacy network-capable code exists, but current contract treats real Binance capability as not authorized. | `NOT READY / NOT FROZEN / NOT AUTHORIZED`；not future-real-ready. |
| Future-real adapter placeholder | `FUTURE_REAL_REQUIRES_GATE` | Planning placeholder only. | Requires separate Gate, design review, security audit, no-outbound/readiness evidence, and user authorization. |
| Permission probe placeholder | `STUB_ONLY` / real probe `NOT_IMPLEMENTED` | OKX/Binance boundary classifiers exist; real probe adapter is not implemented. | Real permission probe requires separate Gate and allowlisted read-only endpoint design. |
| Marketdata no-real placeholder | `NO_REAL_DISABLED` / `STUB_ONLY` | Noop realtime subscription is disabled; fixture/mock/data contract may be used only as no-real evidence. | Not a real marketdata provider. |
| Marketdata future-real placeholder | `FUTURE_REAL_REQUIRES_GATE` | Future public marketdata provider placeholder only. | Requires separate Gate, rate limit policy, no-outbound review, and explicit endpoint authorization. |

## 5. Trading Capability Matrix

| Capability | Noop adapter | OKX adapter | Binance adapter | Future-real placeholder | GateL interpretation |
| --- | --- | --- | --- | --- | --- |
| spot trading | `STUB_ONLY` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | No current real spot trading authorization. |
| margin trading | `NOT_IMPLEMENTED` | `FORBIDDEN_IN_GATEL` | `FORBIDDEN_IN_GATEL` | `FUTURE_REAL_REQUIRES_GATE` | Forbidden in GateL. |
| futures / perpetual | `NOT_IMPLEMENTED` | `FORBIDDEN_IN_GATEL` | `FORBIDDEN_IN_GATEL` | `FUTURE_REAL_REQUIRES_GATE` | Forbidden in GateL. |
| place order | `STUB_ONLY` / no real execution | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | No real order placement. |
| cancel order | `STUB_ONLY` / no real execution | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | No real cancel path. |
| query order | `STUB_ONLY` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | No authorized private order query. |
| account balance | `STUB_ONLY` empty SIM snapshot | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | No real account balance access. |
| REST private trading | `NOT_IMPLEMENTED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | Requires credential bridge, RiskGate, state machine, ledger, audit, and explicit Gate. |
| audit / ledger ownership | `STUB_ONLY` | `FORBIDDEN_IN_GATEL` to bypass NQ ownership | `FORBIDDEN_IN_GATEL` to bypass NQ ownership | `FUTURE_REAL_REQUIRES_GATE` | Adapter must never own ledger or audit mutation. |
| risk gate dependency | `CLOSED_NO_REAL` as boundary rule | `FORBIDDEN_IN_GATEL` to bypass RiskGate | `FORBIDDEN_IN_GATEL` to bypass RiskGate | `FUTURE_REAL_REQUIRES_GATE` | Any future real order must pass NQ `RiskGate` before adapter execution. |

## 6. Marketdata Capability Matrix

| Capability | Noop adapter | OKX adapter | Binance adapter | Marketdata future-real placeholder | GateL interpretation |
| --- | --- | --- | --- | --- | --- |
| REST public marketdata | `NOT_IMPLEMENTED` for Noop | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | Existing historical adapters are not current real provider authorization. |
| WebSocket public marketdata | `NO_REAL_DISABLED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | No real WS public subscription in GateL. |
| WebSocket private/user stream | `NOT_IMPLEMENTED` | `FORBIDDEN_IN_GATEL` | `FORBIDDEN_IN_GATEL` | `FUTURE_REAL_REQUIRES_GATE` | Private/user stream requires credentials and is forbidden in GateL. |
| historical OHLCV | `NOT_IMPLEMENTED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | Legacy GateH adapters exist; current GateL contract does not authorize real exchange reads. |
| ticker | `NOT_IMPLEMENTED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | No current ticker provider authorization. |
| orderbook | `NO_REAL_DISABLED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | Noop returns disabled; real orderbook requires future Gate. |
| trades | `NO_REAL_DISABLED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | Noop returns disabled; real trades stream requires future Gate. |
| bars subscription | `NO_REAL_DISABLED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | Noop returns disabled; real bars subscription requires future Gate. |
| trades subscription | `NO_REAL_DISABLED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | Noop returns disabled; real trades subscription requires future Gate. |
| order-book subscription | `NO_REAL_DISABLED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | Noop returns disabled; real order-book subscription requires future Gate. |
| testnet/sandbox marketdata | `NOT_IMPLEMENTED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | Testnet/sandbox is still external and not allowed by this contract. |

## 7. Credential / Endpoint / Permission Capability Matrix

| Capability | Noop adapter | OKX adapter | Binance adapter | Future-real placeholder | Current contract |
| --- | --- | --- | --- | --- | --- |
| credential source | `STUB_ONLY` / no credential | `CLOSED_NO_REAL` unconfigured default | `CLOSED_NO_REAL` unconfigured default | `FUTURE_REAL_REQUIRES_GATE` | Real credential bridge **NOT IMPLEMENTED**. |
| endpoint default | `NOT_IMPLEMENTED` | `DISABLED_SENTINEL` (`disabled://okx-not-configured`, WS sentinel) | `DISABLED_SENTINEL` (`disabled://binance-not-configured`, WS sentinel) | `FUTURE_REAL_REQUIRES_GATE` | Defaults must not imply network reachability. |
| permission probe | `STUB_ONLY` / no-real skipped | `STUB_ONLY` boundary classifier only; real probe `NOT_IMPLEMENTED` | `STUB_ONLY` boundary classifier only; real probe `NOT_IMPLEMENTED` | `FUTURE_REAL_REQUIRES_GATE` | No real permission probe adapter exists. |
| REST private permission check | `NOT_IMPLEMENTED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | Requires allowlisted read-only endpoints and redacted audit. |
| raw payload boundary | `CLOSED_NO_REAL` no provider body | `CLOSED_NO_REAL` producer suppression; field deletion `NOT_IMPLEMENTED` | `CLOSED_NO_REAL` producer suppression; field deletion `NOT_IMPLEMENTED` | `FUTURE_REAL_REQUIRES_GATE` | Producer suppression is closed; field deletion remains separate compatibility task. |
| rate limit policy | `STUB_ONLY` / no outbound | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | No real exchange rate-limit policy is frozen. |
| retry policy | `STUB_ONLY` / no outbound | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | No real exchange retry policy is frozen. |
| kill switch / no-outbound guard | `CLOSED_NO_REAL` | `CLOSED_NO_REAL` baseline must remain fail-closed | `CLOSED_NO_REAL` baseline must remain fail-closed | `FUTURE_REAL_REQUIRES_GATE` | No-outbound guard is lower bound; future real must prove controlled opt-in. |
| testnet/sandbox endpoint | `NOT_IMPLEMENTED` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | `FUTURE_REAL_REQUIRES_GATE` | Testnet/sandbox is not free to use; it still requires Gate and no-outbound review. |

## 8. Risk / Ledger / Audit Ownership Rules

1. Adapter cannot bypass `RiskGate`.
2. Adapter cannot bypass `OrderStateMachine`.
3. Adapter cannot write ledger entries or own ledger consistency.
4. Adapter cannot own audit mutation or omit audit-relevant identifiers when future real execution is authorized.
5. Adapter cannot select credential active material, tenant, account, owner, active version, or permission scope from process environment.
6. Adapter cannot expose provider raw body, headers, signature source, credential material, private key path content, cookie, token, or query with sensitive fields to core / HTTP API / logs / audit / ledger.
7. Adapter cannot make LIVE or real endpoint decisions from `tradeEnv` alone.
8. Adapter cannot treat testnet/sandbox as safe by default; all external endpoints require explicit Gate and no-outbound/readiness review.

## 9. Forbidden Interpretation

The following interpretations are forbidden:

- Treating OKX / Binance existing adapter code as future-real-ready.
- Treating OKX / Binance existing adapter code as real exchange authorization.
- Treating GateL-1B P1-A/B/C/D closure as anything beyond No-Real hardening baseline completion.
- Treating `NoopMarketDataAdapter` disabled ack as real success.
- Treating `disabled://` sentinel as a configured exchange endpoint.
- Treating unconfigured credential placeholders as real credential readiness.
- Treating permission boundary classifiers as real permission probe implementation.
- Treating historical OHLCV legacy adapters as current real exchange marketdata authorization.
- Treating producer rawPayload suppression as rawPayload field deletion.
- Treating this capability matrix as enabling LIVE, AI, DH runtime, RealClient, real provider, real credential bridge, or real permission probe.
- Allowing adapter code to bypass RiskGate, OrderStateMachine, Ledger, or Audit ownership.

## 10. Future-Real Prerequisites

Any future real exchange capability requires a separate Gate and must satisfy at least:

- User explicit authorization for the target venue, environment, account scope, and capability.
- Credential governance bridge design and security review; no process-env credential material selection by adapter.
- Endpoint allowlist, no-outbound guard update, and sentinel regression evidence.
- Permission probe design limited to allowlisted read-only endpoints; order/cancel/withdraw/transfer endpoints fail-closed.
- Rate limit, retry, timeout, circuit breaker, backoff, and kill switch policy.
- PAPER / LIVE hard isolation and rollback to disabled/no-real.
- RiskGate, OrderStateMachine, Ledger, and Audit integration proof.
- Redaction and raw payload policy covering provider body, headers, signatures, cookies, tokens, private key paths, and query strings.
- Testnet/sandbox evidence before any LIVE consideration; testnet/sandbox alone still does not authorize LIVE.
- Dedicated review/freeze with Maven/frontend/Python/CI scope chosen by the actual implementation impact.

## 11. Acceptance Criteria

- Required status enum is defined and used.
- Noop, OKX, Binance, future-real, permission probe, marketdata no-real/future-real placeholders are covered.
- Trading, marketdata, credential, endpoint, permission, raw payload, risk, ledger, audit dimensions are covered.
- OKX / Binance existing adapters are explicitly not future-real-ready and not real exchange authorization.
- GateL-1B closure is explicitly limited to No-Real hardening baseline.
- `NoopMarketDataAdapter` is `NO_REAL_DISABLED`, not success.
- Binance and OKX default endpoint are `DISABLED_SENTINEL`.
- OKX/Binance credential source is unconfigured/no-real default; real credential bridge is `NOT_IMPLEMENTED`.
- rawPayload producer suppression is closed; rawPayload field deletion is not done.
- Adapter readiness remains `NOT READY / NOT FROZEN / NOT AUTHORIZED`.
- LIVE, real credential, AI, DH runtime, real provider, RealClient, real permission probe remain disallowed / not implemented.

## 12. Findings

### P0

- 无。本轮 docs-only contract，没有 runtime、DB、credential、provider、exchange、LIVE、AI 或 DH side effect。

### P1

- 无。本合同明确禁止将 no-real / disabled / stub 状态解释成真实交易所能力。

### P2

- `GATEL_PLAN.md` 早期硬性问题与现有资产盘点中仍保留 Binance 默认 endpoint 旧描述；本轮同步为 GateL-1B-A/overall freeze 后的 sentinel 事实。
- `rawPayload` field deletion 仍是 separate compatibility task；本合同只冻结 producer suppression 状态。
- Future-real readiness checklist 仍需 GateL-1E refinement；本合同不替代 checklist。

## 13. Commands Run

- `Get-Location`
- `git status --short`
- `git branch --show-current`
- `Get-Content` bounded reads for project instructions, current GateL docs, and allowed adapter files.
- `rg --files backend/nq-adapter-api backend/nq-adapter-okx backend/nq-adapter-binance`
- Bounded `rg -n` checks for sentinel endpoint, unconfigured credential, rawPayload producer suppression, Noop disabled status, GateL current doc status, and forbidden wording.
- Post-edit validation commands are recorded in `TESTING.md` and final task output.

## 14. Rollback

- Delete `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md`.
- Restore this task's edits in `docs/current/GATEL_PLAN.md`, `README.md`, `ROADMAP.md`, `STATUS.md`, `TESTING.md`, and `WORKLOG.md`.
- No code, DB, migration, workflow, runtime, credential, provider, exchange, LIVE, AI, or DH side effect exists from this docs-only contract.

## 15. Next Task Recommendation

**NQ-GATEL-1C-CAPABILITY-MATRIX-CONTRACT-REVIEW**。

The review must remain read-only. It must not implement real adapter, real provider, RealClient, LIVE, AI, DH runtime, rawPayload field deletion, real credential governance bridge, or real permission probe.

## 16. Final Recommendation

**NQ-GATEL-1C-CAPABILITY-MATRIX-CONTRACT：PASS / CONTRACT FROZEN。**

是否允许真实交易所接入：**NO**。
是否允许 LIVE：**NO**。
是否允许真实 credential：**NO**。
是否允许 AI / DH runtime：**NO**。
是否允许将 adapter 标记为 future-real-ready：**NO**。
推荐下一步：**NQ-GATEL-1C-CAPABILITY-MATRIX-CONTRACT-REVIEW**。
