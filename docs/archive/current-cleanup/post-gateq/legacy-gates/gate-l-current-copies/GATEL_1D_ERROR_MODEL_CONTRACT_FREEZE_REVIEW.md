# GateL-1D Adapter Error Model Contract Freeze Review

任务：NQ-GATEL-1D-ERROR-MODEL-CONTRACT-FREEZE
日期：2026-06-23
分支：dev
任务类型：DOCUMENTATION_REVIEW + CONTRACT_FREEZE + ERROR_MODEL_FREEZE + SECURITY_BOUNDARY_REVIEW
结论：**PASS / FROZEN / ACCEPTED**。

> 本 freeze review 只冻结 GateL-1D adapter error model contract 与 review 结论。
> Freeze 不实现 adapter、不修改交易逻辑、不新增 API / DTO / migration / workflow，不授权真实交易所、LIVE、真实 credential、AI、DH runtime 或 adapter future-real-ready。

---

## 1. Task Classification

- Primary：`DOCUMENTATION_REVIEW`。
- Auxiliary：`CONTRACT_FREEZE`、`ERROR_MODEL_FREEZE`、`SECURITY_BOUNDARY_REVIEW`。
- Task level：GateL-1D freeze-review（docs-only；contract-only；no implementation）。

## 2. Scope

### Frozen objects

- `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT.md`
- `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT_REVIEW.md`

### Read-only evidence

- `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md`
- `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT_FREEZE_REVIEW.md`
- `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN.md`
- `docs/current/GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md`
- `docs/current/GATEL_PLAN.md`
- `docs/current/README.md`
- `docs/current/ROADMAP.md`
- `docs/current/STATUS.md`
- `docs/current/TESTING.md`
- `docs/current/WORKLOG.md`
- `backend/nq-adapter-api/**`
- `backend/nq-adapter-okx/**`
- `backend/nq-adapter-binance/**`

### Explicitly out of scope

- Java / TypeScript / Python code changes.
- API / DTO / migration / historical migration / workflow changes.
- frontend / research / scripts / deploy changes.
- `.env`, API key, secret, token, pem, key, jks, p12, log dump, backup, or credential material reads.
- External network or exchange calls.
- LIVE, AI, DH runtime, RealClient, real provider, real permission probe, real credential governance bridge.
- Order, cancel, transfer, withdrawal, or rawPayload field deletion.

## 3. Files Inspected

- `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT.md`
- `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT_REVIEW.md`
- `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT.md`
- `docs/current/GATEL_1C_CAPABILITY_MATRIX_CONTRACT_FREEZE_REVIEW.md`
- `docs/current/GATEL_1B_NO_REAL_HARDENING_PLAN.md`
- `docs/current/GATEL_1B_OVERALL_HARDENING_FREEZE_REVIEW.md`
- `docs/current/GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md`
- `backend/nq-adapter-api/.../service/NoopMarketDataAdapter.java`、`model/AdapterError.java`、`model/AdapterResultCategory.java`
- Bounded OKX/Binance adapter files via `git grep` for `disabled://`、`unconfigured()`、`NO_REAL_DISABLED`、`*_CREDENTIALS_MISSING`、`50035`、`-2013`/`-2011`。

## 4. Commands Run

- `git status --short`
- `git branch --show-current`
- `Read` bounded reads for GateL-1D contract / review、GateL-1C freeze review template、allowed GateL current docs、adapter files。
- `git grep -n "NO_REAL_DISABLED|FATAL_FAILURE|disabled://|unconfigured()|*_CREDENTIALS_MISSING|50035|-2013|-2011"` 跨 adapter main src 复核冻结不变量。
- Post-edit validation：`git diff --check` / `git diff --stat` / `git status --short` / bounded `rg` 禁止措辞检查 / scope check。

## 5. Freeze Verdict

**PASS / FROZEN / ACCEPTED**。

GateL-1D adapter error model contract 与 review 被接受为 frozen contract-only baseline。该 freeze 使 error model（error status enum、retry 语义、adapter/venue 与 trading/marketdata/credential/permission 路径矩阵、fail-closed 与禁止解释）成为后续 GateL-1E readiness checklist refinement 与 future-real 实现 Gate 的错误分类、retry、fail-closed、安全解释基线。

该 freeze **不**启用任何能力，**不**授权真实交易所接入、LIVE、真实 credential、AI、DH runtime、RealClient、real provider、real permission probe、real credential governance bridge 或 adapter future-real-ready。

## 6. Frozen Error Model Facts

- GateL canonical：**No-Real Exchange / MarketData Readiness**。
- GateL-1B overall No-Real hardening baseline：**FROZEN / ACCEPTED**。
- GateL-1C capability matrix contract：**FROZEN / ACCEPTED**。
- GateL-1D error model contract：**FROZEN / ACCEPTED**。
- GateL-1D error model contract review：**PASS / REVIEW ACCEPTED**。
- P1-A：**CLOSED / ACCEPTED**。
- P1-B：**CLOSED / ACCEPTED**。
- P1-C producer suppression：**CLOSED / ACCEPTED**。
- P1-C rawPayload field deletion：**NOT DONE / SEPARATE COMPATIBILITY TASK**。
- P1-D：**CLOSED / ACCEPTED**。
- Adapter readiness：**NOT READY / NOT FROZEN / NOT AUTHORIZED**。
- LIVE：**DISABLED**。AI：**NOT STARTED**。DH runtime：**NOT INTEGRATED**。
- RealClient / real provider / real permission probe / real credential governance bridge：**NOT IMPLEMENTED**。

## 7. Error Status Enum Frozen

下列 GateL-1D 合同层 error status enum 被冻结（15 项，无缺漏、语义可区分）：

- `NO_REAL_DISABLED`
- `NETWORK_DISABLED`
- `CREDENTIALS_MISSING`
- `AUTH_FAILED`
- `PERMISSION_DENIED`
- `IP_NOT_ALLOWED`
- `RATE_LIMITED`
- `VENUE_UNAVAILABLE`
- `INVALID_SYMBOL`
- `UNSUPPORTED_OPERATION`
- `RISK_REJECTED`
- `ORDER_STATE_REJECTED`
- `LEDGER_REJECTED`
- `RAW_PAYLOAD_SUPPRESSED`
- `UNKNOWN_REQUIRES_REVIEW`

## 8. AdapterResultCategory Mapping Frozen

合同层 status 到既有 `AdapterResultCategory`（9 类：`SUCCESS` / `ACCEPTED` / `NOT_FOUND` / `DEFERRED` / `RETRYABLE_FAILURE` / `FATAL_FAILURE` / `THROTTLED` / `AUTH_FAILURE` / `REMOTE_UNAVAILABLE`）的映射被冻结，且与源码事实一致：

- `NO_REAL_DISABLED` → `FATAL_FAILURE`（`NoopMarketDataAdapter` 源码确认）。
- `NETWORK_DISABLED` → `FATAL_FAILURE`（`disabled://` sentinel loud fail-closed）。
- `CREDENTIALS_MISSING` / `AUTH_FAILED` / `PERMISSION_DENIED` / `IP_NOT_ALLOWED` → `AUTH_FAILURE`（OKX `50113`/`50110`/`50035`、Binance `-2015`/`-2014`/`-1022`/`BINANCE_CREDENTIALS_MISSING` 源码确认）。
- `RATE_LIMITED` → `THROTTLED`（OKX `50011`/429、Binance `-1003`/429/418）。
- `VENUE_UNAVAILABLE` → `REMOTE_UNAVAILABLE` / `RETRYABLE_FAILURE`（HTTP≥500 / `HTTP_CLIENT_ERROR` / timeout）。
- `INVALID_SYMBOL` / `UNSUPPORTED_OPERATION` / `UNKNOWN_REQUIRES_REVIEW` → `FATAL_FAILURE`（终态 / fail-closed by default）。
- `RISK_REJECTED` / `ORDER_STATE_REJECTED` / `LEDGER_REJECTED` → NQ core 事实源（adapter 透传，不自判）。
- `RAW_PAYLOAD_SUPPRESSED` → 安全边界标记（非错误恢复入口）。
- Binance `DEFERRED`（`-2013`/`-2011`）冻结为受控查询语义，不得无限轮询，非下单重试。

## 9. Retry Semantics Frozen

- retryable=false（终态，禁止继续交易）冻结：`NO_REAL_DISABLED`、`NETWORK_DISABLED`、`CREDENTIALS_MISSING`、`AUTH_FAILED`、`PERMISSION_DENIED`、`IP_NOT_ALLOWED`、`UNSUPPORTED_OPERATION`、`RISK_REJECTED`、`ORDER_STATE_REJECTED`、`LEDGER_REJECTED`、`RAW_PAYLOAD_SUPPRESSED`，附加 `INVALID_SYMBOL`。
- `RATE_LIMITED` 冻结为 conditional retry：必须 backoff / circuit breaker / rate-limit policy，禁止无限重试，超限降级终态上报。
- `VENUE_UNAVAILABLE` 冻结为 conditional retry：必须 circuit breaker / kill switch / no-outbound (no-live) guard，禁止绕过 kill switch / no-outbound guard。
- `UNKNOWN_REQUIRES_REVIEW` 冻结为默认 fail-closed、不可重试，除非后续合同明确分类。

## 10. Adapter / Venue Matrix Frozen

- Noop adapter：marketdata 订阅冻结为 `NO_REAL_DISABLED`（`FATAL_FAILURE` / `subscribed=false` / `retryable=false`），account 为空 SIM snapshot stub；非真实 success，非 future-real-ready。
- OKX adapter：既有 `OkxErrorClassifier` 映射 + 未配置 `CREDENTIALS_MISSING` / `NETWORK_DISABLED` fail-closed；`NOT READY / NOT FROZEN / NOT AUTHORIZED`，非 future-real-ready。
- Binance adapter：既有 `BinanceErrorClassifier` 映射 + 未配置 fail-closed + `DEFERRED` 受控查询语义；`NOT READY / NOT FROZEN / NOT AUTHORIZED`，非 future-real-ready。
- Future-real adapter placeholder：`FUTURE_REAL_REQUIRES_GATE` / `NOT_IMPLEMENTED`；无运行时错误路径。
- Permission probe placeholder：boundary classifier + forbidden endpoint fail-closed only；真实 probe `NOT_IMPLEMENTED`。
- Marketdata no-real / future-real placeholder：no-real = `NO_REAL_DISABLED`；future-real = `FUTURE_REAL_REQUIRES_GATE`。

证据保持一致（源码 `git grep` 复核）：

- `NoopMarketDataAdapter` 返回 `subscribed=false + NO_REAL_DISABLED + FATAL_FAILURE + retryable=false`。
- OKX 默认 endpoint 仍为 `disabled://okx-not-configured` / `disabled://okx-ws-not-configured`。
- Binance 默认 endpoint 仍为 `disabled://binance-not-configured` / `disabled://binance-ws-not-configured`。
- OKX/Binance credential source 仍为 `*.unconfigured()` / no-real default；HTTP client 网络前 `*_CREDENTIALS_MISSING` fail-closed。
- OKX/Binance permission probe boundary 仍为 classifier + forbidden-endpoint guard，非真实 permission probe adapter。

trading / marketdata / credential / permission path 全覆盖（详见合同 §6 / §7 / §8）。

## 11. Forbidden Interpretation Frozen

freeze 锁定以下禁止解释：

- error model 不能启用任何真实交易所错误处理或真实交易能力。
- `NO_REAL_DISABLED` 不能当作成功或 provider 就绪。
- `NETWORK_DISABLED` / `disabled://` sentinel 不能当作可用真实 endpoint，不能绕过 no-outbound guard。
- `CREDENTIALS_MISSING` 不能 fallback 到 env / system property / .env credential。
- `AUTH_FAILED` / `PERMISSION_DENIED` / `IP_NOT_ALLOWED` 不能自动提升权限或继续交易。
- `RATE_LIMITED` 不能无限重试；`VENUE_UNAVAILABLE` 不能绕过 kill switch / no-outbound guard。
- `RISK_REJECTED` / `ORDER_STATE_REJECTED` / `LEDGER_REJECTED` 由 NQ core 事实源决定，adapter 不得覆盖或绕过。
- `RAW_PAYLOAD_SUPPRESSED` 是安全边界，不是错误恢复入口。
- `UNKNOWN_REQUIRES_REVIEW` 不能当作可乐观重试的瞬时错误。
- 任一 retryable=false status 不能写成“可继续交易”“可重试后下单”“可绕过边界”。
- OKX / Binance 既有 adapter 不能当作 future-real-ready 或真实交易授权。
- LIVE、真实 credential、AI、DH runtime、RealClient、real provider、real permission probe、real credential bridge 仍 disallowed / not implemented。
- adapter 不得绕过 `RiskGate` / `OrderStateMachine` / Ledger / Audit。

## 12. Findings

### P0

- 无。

### P1

- 无。

### P2（保留为 follow-up，不阻断 freeze）

- P2-1：合同层细粒度 status `CREDENTIALS_MISSING` / `AUTH_FAILED` / `PERMISSION_DENIED` / `IP_NOT_ALLOWED` 在既有 `AdapterResultCategory` 层折叠为单一 `AUTH_FAILURE`（源码确认）。合同已声明 status 为“合同约束 ↔ 代码实现”关系；运行时若需区分须另起实现 Gate，且不得借细分削弱 fail-closed。
- P2-2：`RAW_PAYLOAD_SUPPRESSED` 仅冻结 producer suppression 安全边界；rawPayload field deletion 仍是 separate compatibility task。
- P2-3：真实 `RATE_LIMITED` / `VENUE_UNAVAILABLE` 的 backoff / circuit breaker / kill switch policy 实现属 future-real，须在 GateL-1E readiness checklist 与后续实现 Gate 落地；本 freeze 只冻结解释口径。

### Residual / Follow-up

- 合同 §16「Next Task Recommendation」标题下仍写合同自身任务名（语义应为推荐 review）；review 已执行并接受。本 freeze 将权威下一步固定为 `NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT`（见 §15）；该措辞为非阻断 follow-up，可在 GateL-1E 期间一并清理。
- GateL-1E future-real readiness checklist refinement 未在本 freeze 启动。

## 13. Validation

本 docs-only freeze 要求：

- `git diff --check`
- `git diff --stat`
- `git status --short`
- bounded `rg` 检查 retryable=false 错误未被写成可继续交易；`real exchange` / `LIVE` / `future-real-ready` 仅在 NO / 禁止 / 否定语境；`CREDENTIALS_MISSING` 无 fallback 语义。
- scope check：仅 `docs/current/**` 变更。

不需要 Maven / frontend / Python 测试，因为本轮未改 Java / TypeScript / Python、API、DTO、migration、workflow 或运行时逻辑；源码读取仅用于复核冻结不变量。

## 14. Adapter Readiness Verdict

**NOT READY / NOT FROZEN / NOT AUTHORIZED**。

GateL-1D freeze 只冻结 error model contract baseline，不冻结真实 adapter readiness，不能被引用为接入 OKX / Binance、启用 LIVE、注入真实 credential 或将 adapter 标记 future-real-ready 的许可。

## 15. Recommended Next Task

**NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT**。

下一任务须保持 contract / documentation 工作线，除非另行授权。不得实现真实 adapter、real provider、RealClient、LIVE、AI、DH runtime、rawPayload field deletion、real credential governance bridge 或 real permission probe；不得把 GateL-1D / GateL-1E 写成 implementation started。

## 16. Rollback

- 删除 `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT_FREEZE_REVIEW.md`。
- 还原本 freeze 对 `docs/current/GATEL_1D_ERROR_MODEL_CONTRACT.md`、`GATEL_PLAN.md`、`README.md`、`ROADMAP.md`、`STATUS.md`、`TESTING.md`、`WORKLOG.md` 的同步。
- 无 runtime / DB / provider / exchange / credential / workflow / frontend / research / script / deploy / AI / DH / LIVE 副作用。

## 17. Final Recommendation

**NQ-GATEL-1D-ERROR-MODEL-CONTRACT-FREEZE：PASS / FROZEN / ACCEPTED。**

- 是否允许真实交易所接入：**NO**。
- 是否允许 LIVE：**NO**。
- 是否允许真实 credential：**NO**。
- 是否允许 AI / DH runtime：**NO**。
- 是否允许将 adapter 标记为 future-real-ready：**NO**。
- 推荐下一步：**NQ-GATEL-1E-READINESS-CHECKLIST-REFINEMENT**。
