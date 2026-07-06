# GateL-1D Adapter Error Model Contract Review

任务：NQ-GATEL-1D-ERROR-MODEL-CONTRACT-REVIEW
日期：2026-06-23
分支：dev
任务类型：DOCUMENTATION_REVIEW + ERROR_MODEL_CONTRACT_REVIEW + ADAPTER_BOUNDARY_REVIEW + SECURITY_BOUNDARY_REVIEW
结论：**PASS / REVIEW ACCEPTED（contract-only）**
状态：**GateL-1D error model contract 可作为 frozen contract-only baseline**；adapter readiness **NOT READY / NOT FROZEN / NOT AUTHORIZED**。

> 本文件只复核 `GATEL_1D_ERROR_MODEL_CONTRACT.md`，不实现 adapter、不改交易逻辑、不新增 API / DTO / migration / workflow。
> 复核接受不代表 GateL-1D implementation started，也不授权真实交易所、LIVE、真实 credential、AI、DH runtime 或 adapter future-real-ready。

## 1. Scope

### 已复核（只读）

- `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT.md`（复核主对象）。
- `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md`、`GATEL_1C_CAPABILITY_MATRIX_CONTRACT_FREEZE_REVIEW.md`。
- `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN.md`、`GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md`。
- `docs/current/GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`。
- `backend/nq-adapter-api/**`、`backend/nq-adapter-okx/**`、`backend/nq-adapter-binance/**`（错误模型源码事实校验）。

### 明确不涉及

- Java / TypeScript / Python 代码修改。
- API / DTO / migration / historical migration / workflow / frontend / research / scripts / deploy 修改。
- `.env`、API key、secret、token、pem、key、jks、p12、日志 dump、backup。
- 任何交易所外联、LIVE、AI、DH runtime、RealClient、real provider、真实 permission probe、真实 credential governance bridge。
- 下单、撤单、转账、提现；`AdapterOrderAck` / `AdapterOrderSnapshot` rawPayload 字段删除。

## 2. Review verdict

**PASS / REVIEW ACCEPTED。** `GATEL_1D_ERROR_MODEL_CONTRACT.md` 可作为 GateL-1D adapter error model 的 frozen contract-only baseline，建议下一步进入 `NQ-GATEL-1D-ERROR-MODEL-CONTRACT-FREEZE`，不得进入 implementation / real adapter。复核未发现 P0 / P1 阻断项；P2 为既知 follow-up，不阻断冻结。

复核结论要点：

- error status enum 15 项完整、语义可区分、无歧义。
- 合同层 status 到既有 `AdapterResultCategory`（9 类）的映射清晰，且与源码事实一致。
- retryable=false 列表未被写成可继续交易；RATE_LIMITED / VENUE_UNAVAILABLE 明确为受控 conditional retry；UNKNOWN_REQUIRES_REVIEW 默认 fail-closed。
- Noop / OKX / Binance / future-real placeholder / permission probe placeholder / marketdata placeholder 全覆盖；trading / marketdata / credential / permission path 全覆盖。
- 真实交易所、LIVE、真实 credential、AI、DH runtime 仍明确禁止；adapter readiness 仍 NOT READY / NOT FROZEN / NOT AUTHORIZED。

## 3. Error status enum review

复核 §3 的 15 项 error status，对照任务要求清单逐项确认存在且语义无歧义：

| Status | 存在 | 语义判定 |
| --- | --- | --- |
| `NO_REAL_DISABLED` | 是 | no-real / stub 明确禁用，非 success；retryable=false。无歧义。 |
| `NETWORK_DISABLED` | 是 | `disabled://` sentinel / no-outbound 拦截，fail-closed；retryable=false。无歧义。 |
| `CREDENTIALS_MISSING` | 是 | credential 未配置，网络前 fail-closed；明确禁止 fallback。无歧义。 |
| `AUTH_FAILED` | 是 | 凭证无效 / 签名错误 / key 失效；不自动提升权限。无歧义。 |
| `PERMISSION_DENIED` | 是 | 凭证有效但缺 scope；不自动扩权。无歧义。 |
| `IP_NOT_ALLOWED` | 是 | IP allowlist 未通过；不自动改写出口。无歧义。 |
| `RATE_LIMITED` | 是 | 限流；conditional retry + backoff。无歧义。 |
| `VENUE_UNAVAILABLE` | 是 | 5xx / 不可达；conditional retry + circuit breaker / kill switch。无歧义。 |
| `INVALID_SYMBOL` | 是 | symbol 不支持；业务终态 retryable=false。无歧义。 |
| `UNSUPPORTED_OPERATION` | 是 | 能力不支持 / GateL 禁止；终态。无歧义。 |
| `RISK_REJECTED` | 是 | NQ RiskGate 拒绝；NQ core 拥有。无歧义。 |
| `ORDER_STATE_REJECTED` | 是 | OrderStateMachine 拒绝；NQ core 拥有。无歧义。 |
| `LEDGER_REJECTED` | 是 | ledger / 账务拒绝；NQ core 拥有。无歧义。 |
| `RAW_PAYLOAD_SUPPRESSED` | 是 | 安全边界标记，非恢复入口。无歧义。 |
| `UNKNOWN_REQUIRES_REVIEW` | 是 | 未分类，默认 fail-closed。无歧义。 |

判定：**15 项完整，无缺漏，无歧义。**

## 4. AdapterResultCategory mapping review

复核 §2.1 + §3 的映射，并用 `git grep` 校验源码事实：

- 既有 `AdapterResultCategory` 9 类（`SUCCESS` / `ACCEPTED` / `NOT_FOUND` / `DEFERRED` / `RETRYABLE_FAILURE` / `FATAL_FAILURE` / `THROTTLED` / `AUTH_FAILURE` / `REMOTE_UNAVAILABLE`）与合同引用一致。
- `NO_REAL_DISABLED` → `FATAL_FAILURE`：与 `NoopMarketDataAdapter`（`code=NO_REAL_DISABLED`、`category=FATAL_FAILURE`、`retryable=false`）一致（源码确认）。
- `RATE_LIMITED` → `THROTTLED`：OKX `50011` / HTTP 429、Binance `-1003` / 429 / 418 → THROTTLED，源码确认。
- `VENUE_UNAVAILABLE` → `REMOTE_UNAVAILABLE` / `RETRYABLE_FAILURE`：HTTP≥500 / `HTTP_CLIENT_ERROR` → REMOTE_UNAVAILABLE，timeout → RETRYABLE_FAILURE，源码确认。
- `CREDENTIALS_MISSING` / `AUTH_FAILED` / `PERMISSION_DENIED` / `IP_NOT_ALLOWED` → `AUTH_FAILURE`：OKX `50113` / `50110` / `50035`、Binance `-2015` / `-2014` / `-1022` / `BINANCE_CREDENTIALS_MISSING` → AUTH_FAILURE，源码确认。
- `query order` `NOT_FOUND`（OKX `51603`）作为可审计降级、非可重试错误，与 `OkxErrorClassifier` / `OkxErrorCode` 一致。
- Binance `DEFERRED`（`-2013` / `-2011`）在 Binance row 标注为“受控查询语义，不得无限轮询”，与 `BinanceErrorClassifier` 一致（DEFERRED 在 Binance 为 retryable=true，但属 query-confirm 语义，非下单重试）。

判定：**映射清晰，与源码事实一致。** 见 §7 P2-1 关于合同层细粒度 status 在既有 category 上的折叠说明。

## 5. Retry semantics review

- retryable=false 集合（§4.1）：`NO_REAL_DISABLED`、`NETWORK_DISABLED`、`CREDENTIALS_MISSING`、`AUTH_FAILED`、`PERMISSION_DENIED`、`IP_NOT_ALLOWED`、`UNSUPPORTED_OPERATION`、`RISK_REJECTED`、`ORDER_STATE_REJECTED`、`LEDGER_REJECTED`、`RAW_PAYLOAD_SUPPRESSED`，附加 `INVALID_SYMBOL`。与任务要求清单完全一致，且无任一项被写成“可继续交易”。
- `RATE_LIMITED`（§4.2）：明确 conditional retry，必须 backoff / circuit breaker / rate-limit policy，**禁止无限重试**，超限降级为终态并上报。符合要求。
- `VENUE_UNAVAILABLE`（§4.2）：明确 conditional retry，必须 circuit breaker / kill switch / no-outbound guard，**禁止绕过 kill switch / no-outbound guard**。符合“no-live guard”要求。
- `UNKNOWN_REQUIRES_REVIEW`（§4.3）：明确默认 fail-closed、不可重试，除非后续合同明确分类。符合要求。

判定：**retry 语义完整、安全、无可继续交易误写。**

## 6. Error matrix review

### Adapter / venue error matrix（§5）

Noop、OKX、Binance、future-real placeholder、permission probe placeholder、marketdata no-real / future-real placeholder 全覆盖。OKX/Binance 默认错误语义锚定既有 classifier + 未配置 fail-closed；future-real / permission probe / marketdata future-real 均标注 `FUTURE_REAL_REQUIRES_GATE` 或 `NOT_IMPLEMENTED`；全部标注非 future-real-ready。判定：覆盖完整。

### Trading path error matrix（§6）

place / cancel / query / account balance / REST private / WS private / risk gate / order state machine / ledger / audit 全覆盖。RISK_REJECTED / ORDER_STATE_REJECTED / LEDGER_REJECTED 由 NQ core 拥有、adapter 透传不绕过；私有路径 fail-closed 或 `FORBIDDEN_IN_GATEL`。判定：覆盖完整，所有权边界正确。

### Marketdata path error matrix（§7）

REST public / WS public / historical OHLCV / ticker / orderbook / trades / bars·trades·order-book subscription 全覆盖。Noop disabled 路径标注 `NO_REAL_DISABLED`（非 success）；historical 失败抛 `HistoricalKlineAdapterException`（不吞错）与源码一致。判定：覆盖完整。

### Credential / permission error matrix（§8）

credential source / endpoint default / permission probe / REST private permission check / raw payload boundary / rate limit / kill switch 全覆盖。`CREDENTIALS_MISSING` 禁止 fallback；`NETWORK_DISABLED` fail-closed；permission probe forbidden endpoint fail-closed 且不回传 raw response；`RAW_PAYLOAD_SUPPRESSED` 为安全边界。判定：覆盖完整。

## 7. Forbidden interpretation review

复核 §10 + §9：

- `NO_REAL_DISABLED` 不等于成功 — 已明确（§3 / §10）。
- `NETWORK_DISABLED` / `disabled://` sentinel fail-closed，不得绕过 no-outbound — 已明确。
- `CREDENTIALS_MISSING` 不得 fallback env / system property / .env — 已明确（§3 / §8 / §10），源码确认 runtime config 不读 env credential。
- `AUTH_FAILED` / `PERMISSION_DENIED` / `IP_NOT_ALLOWED` 不得自动提升权限、不得继续交易 — 已明确。
- `RATE_LIMITED` 不得无限重试；`VENUE_UNAVAILABLE` 不得绕过 kill switch / no-outbound guard — 已明确。
- `RISK_REJECTED` / `ORDER_STATE_REJECTED` / `LEDGER_REJECTED` 由 NQ core 拥有，adapter 不绕过 — 已明确。
- `RAW_PAYLOAD_SUPPRESSED` 是安全边界，非错误恢复入口 — 已明确。
- `UNKNOWN_REQUIRES_REVIEW` fail-closed — 已明确。
- error model 不构成真实交易所 / LIVE / AI / DH / RealClient / real provider / real credential / real permission probe 授权 — 已明确。
- OKX / Binance 既有 adapter 不得当作 future-real-ready — 已明确。

判定：**禁止解释完整、与 GateL-1C 一致，无授权语义泄漏。**

## 8. Source fact verification

`git grep` 校验合同所引源码事实（无代码改动，working tree clean）：

- `OkxErrorClassifier`：`50035` 归入 AUTH_FAILURE（line 36）；`OkxPermissionProbeBoundary` `50035` → `IP_ALLOWLIST_FAILED`（line 48）。合同将 `IP_NOT_ALLOWED` 映射到 AUTH_FAILURE category 并引用 permission probe IP 字符串，二者一致。
- `BinanceErrorClassifier`：`-2013` / `-2011` → DEFERRED（line 33），与合同 Binance row 一致。
- `NoopMarketDataAdapter`：`NO_REAL_DISABLED_CODE` + `AdapterResultCategory.FATAL_FAILURE` + `subscribed=false`，与合同一致。
- `OkxHttpClient` / `BinanceHttpClient`：网络前抛 `OKX_CREDENTIALS_MISSING` / `BINANCE_CREDENTIALS_MISSING`；`OkxRuntimeConfig` / `BinanceRuntimeConfig` 不从 env 读 credential material，与合同 fail-closed 描述一致。

判定：**合同所引源码事实全部准确。**

## 9. Findings

### P0

- 无。本轮 docs-only review，没有 runtime、DB、credential、provider、exchange、LIVE、AI 或 DH side effect。

### P1

- 无。合同未把 disabled / missing / denied / unknown / suppressed 错误写成可重试或可继续真实交易；未把 real exchange / LIVE / future-real-ready 写成 allowed。

### P2

- P2-1（既知，非阻断）：合同层细粒度 status `CREDENTIALS_MISSING` / `AUTH_FAILED` / `PERMISSION_DENIED` / `IP_NOT_ALLOWED` 在既有 `AdapterResultCategory` 层均折叠为单一 `AUTH_FAILURE`（源码确认）。合同已在 §2.1 / §13 P2 说明 status 是“合同约束 ↔ 代码实现”关系；未来若需在运行时区分这些子类，须另起实现 Gate，且不得借细分削弱 fail-closed。本合同作为解释口径冻结可接受。
- P2-2（既知，非阻断）：`RAW_PAYLOAD_SUPPRESSED` 仅冻结 producer suppression 安全边界；rawPayload field deletion 仍是 separate compatibility task，不在本合同范围。
- P2-3（既知，非阻断）：真实 `RATE_LIMITED` / `VENUE_UNAVAILABLE` 的 backoff / circuit breaker / kill switch policy 实现属 future-real，须在 GateL-1E readiness checklist 与后续实现 Gate 落地；本合同只冻结解释口径。

以上 P2 均为 follow-up 性质，不阻断 GateL-1D 冻结。

## 10. Doc consistency note

复核发现合同 §16「Next Task Recommendation」标题下仍写 `NQ-GATEL-1D-ERROR-MODEL-CONTRACT`（即合同自身任务名），其语义应为“下一步推荐 review”。本 review 已经执行并接受；建议在 GateL-1D freeze 时把合同 §16 的下一步明确为 `NQ-GATEL-1D-ERROR-MODEL-CONTRACT-FREEZE`，避免下一步指向歧义。本轮 review 不强制改写合同正文，仅记录为措辞 follow-up（P2 级以下，非阻断）。

## 11. Commands run

- `git status --short` / `git branch --show-current`（dev，预检 clean）。
- bounded reads：`GATEL_1D_ERROR_MODEL_CONTRACT.md` 全文 + 允许的 GateL current docs + adapter 源码。
- `git grep -n` 校验 OKX `50035`、Binance `-2013`/`-2011`、Noop `NO_REAL_DISABLED`/`FATAL_FAILURE`、`*_CREDENTIALS_MISSING` 源码事实。
- 后置文档验证：`git diff --check` / `git diff --stat` / bounded `rg` 禁止措辞检查 / scope check（仅 `docs/current/**`）。

## 12. Rollback

- 删除 `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT_REVIEW.md`。
- 还原本轮对 `docs/current/GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md` 的同步。
- 无 code / DB / migration / workflow / runtime / credential / provider / exchange / LIVE / AI / DH side effect。

## 13. Next task recommendation

**NQ-GATEL-1D-ERROR-MODEL-CONTRACT-FREEZE**（docs-only）。

冻结对象仅为 GateL-1D error model contract + 本 review 的事实与 P2 follow-up；不得进入 implementation / real adapter；不得实现真实 provider、RealClient、LIVE、AI、DH runtime、rawPayload field deletion、real credential governance bridge 或 real permission probe；不得把 GateL-1D 写成 implementation started。

## 14. Final recommendation

**NQ-GATEL-1D-ERROR-MODEL-CONTRACT-REVIEW：PASS / REVIEW ACCEPTED。**

- 是否允许真实交易所接入：**NO**。
- 是否允许 LIVE：**NO**。
- 是否允许真实 credential：**NO**。
- 是否允许 AI / DH runtime：**NO**。
- 是否允许将 adapter 标记为 future-real-ready：**NO**。
- 推荐下一步：**NQ-GATEL-1D-ERROR-MODEL-CONTRACT-FREEZE**。
