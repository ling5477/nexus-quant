# NQ-DH Integration-1 NQ Runtime Client Work Order

> 任务：`NQ-DH-I1-NQ-RUNTIME-CLIENT-WO`
> 类型：`WORK_ORDER_ONLY + NQ_SCOPED_RUNTIME_CLIENT_IMPLEMENTATION_PLAN + CROSS_REPO_BOUNDARY_FREEZE + NO_CLIENT_IMPLEMENTATION + NO_REAL_HTTP + NO_REAL_PROVIDER + NO_LIVE`
> 日期：2026-07-04
> 仓库视角：NexusQuant dry-run worktree
> 状态：`CLOSED / ACCEPTED / WORK_ORDER_ONLY / NO_CLIENT_IMPLEMENTATION / NO_REAL_HTTP / NO_PROVIDER / NO_LIVE`

## 1. 目标

本 work order 只冻结下一轮 NQ limited dry-run runtime client implementation 的边界、文件范围、安全门槛、请求/响应约束、测试要求、禁止项、回滚要求和后续验收条件。

本轮不实现 NQ runtime client，不新增 Java 生产代码，不修改测试代码，不新增 HTTP client，不真实调用 DH，不改生产 runtime wiring，不修改 order / execution / risk / ledger / account / paper / live 链路。

当前事实：

```text
NQ-DH-I1-RUNTIME-API-CONTRACT-REVIEW: CLOSED / ACCEPTED
NQ-DH-I1-DH-RUNTIME-API-WO: CLOSED / ACCEPTED
NQ-DH-I1-DH-LIMITED-RUNTIME-ENDPOINT-IMPLEMENTATION: CLOSED / ACCEPTED
DH endpoint: POST /api/ai/decision-dry-runs
DH endpoint scope: DH-only inbound limited dry-run
NQ runtime client: NOT STARTED
NQ runtime client WO: CLOSED / ACCEPTED by this document
contracts/OpenAPI/schema/golden_cases formalization: NOT STARTED
NQ_DRYRUN: review-gated source, not in production allowlist
Runtime integration: NOT STARTED
DH integrated: NO
LIVE: DISABLED
Agent / LangGraph: NO
```

Readiness decision：

```text
ALLOW_NQ_RUNTIME_CLIENT_WO_CLOSE: YES
ALLOW_NQ_LIMITED_RUNTIME_CLIENT_IMPLEMENTATION_WO: YES
ALLOW_NQ_RUNTIME_CLIENT_IMPLEMENTATION_NOW: NO
ALLOW_REAL_HTTP_NOW: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_SCHEMA_FORMALIZATION_NOW: NO
ALLOW_CONTRACTS_MODIFICATION_NOW: NO
ALLOW_GOLDEN_CASES_MODIFICATION_NOW: NO
ALLOW_DH_CODE_CHANGE_NOW: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 2. 本轮范围

本轮允许修改：

```text
docs/current/NQ_DH_INTEGRATION1_NQ_RUNTIME_CLIENT_WO.md
docs/current/README.md
docs/current/STATUS.md
docs/current/ROADMAP.md
docs/current/WORK_ORDER.md
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/API.md
```

本轮只读：

```text
E:/Project/decision-hub/docs/current/**
E:/Project/nexus-quant/docs/current/**
backend/**/src/main/**
backend/**/src/test/**
contracts/**
golden_cases/**
```

本轮禁止：

```text
NQ runtime client implementation
Java production code change
Java test code change
HTTP client implementation
WebClient / RestTemplate / OkHttp / HttpClient wiring
real DH call
real outbound HTTP
real provider
credential read or forwarding
contracts / OpenAPI / JSON Schema / golden_cases / fixture JSON change
migration
order / execution / risk mutation / ledger / account / paper / live production path change
LONG_BIAS / SHORT_BIAS mapped to BUY / SELL
DH output as automatic trading signal
Integration-1 runtime start
Runtime integration started wording
DH integrated wording
LIVE enablement
Agent / LangGraph runtime
```

## 3. 下一轮 implementation 允许边界

下一轮只有在用户明确启动 `NQ-DH-I1-NQ-LIMITED-RUNTIME-CLIENT-IMPLEMENTATION` 后，才允许实现 limited dry-run client。该 implementation 仍必须满足：

```text
default disabled
dev/test profile only
production disabled
no LIVE
no trading side effect
no order creation
no cancel order
no risk mutation
no ledger mutation
no Paper Run start
no account mutation
no strategy state mutation
no provider call
no credential forwarding
no autonomous trading signal
```

实现入口必须先选择一个 no-side-effect 集成点：

```text
test-only support service
dry-run recorder
isolated Integration-1 adapter package
```

禁止直接挂入：

```text
order service
execution engine
risk mutation
ledger
account
paper run start
live run start
strategy scheduler execution path
exchange adapter
```

## 4. 调用方向冻结

下一轮 implementation 的唯一允许方向：

```text
NQ limited dry-run client -> DH POST /api/ai/decision-dry-runs
```

禁止方向：

```text
DH -> NQ runtime callback
DH -> NQ DB
DH -> NQ order/risk/ledger/paper/live
NQ -> real provider through DH
NQ -> exchange through DH
```

该方向冻结不代表 runtime integration 已 started。NQ 只能记录 dry-run result，不得执行 DH output。

## 5. Feature flag / kill switch

下一轮 implementation 必须默认 fail-closed，并冻结以下配置要求。若 NQ 现有配置命名有更贴近本地风格的前缀，可在 implementation 前做一次命名 review；如无既有风格，使用以下命名：

```properties
nq.dh.integration1.runtime.enabled=false
nq.dh.integration1.runtime.client.enabled=false
nq.dh.integration1.runtime.source=NQ_DRYRUN
nq.dh.integration1.runtime.endpoint-url=
nq.dh.integration1.runtime.production-enabled=false
nq.dh.integration1.runtime.kill-switch=true
```

强制规则：

- 默认关闭。
- dev/test only。
- production disabled unless later approved。
- emergency kill switch。
- missing config fail-closed。
- endpoint url missing fail-closed。
- timeout fail-closed。
- parse failure fail-closed。
- response validation failure fail-closed。
- no fallback to trading action。

## 6. Request envelope

下一轮 implementation 必须生成 request envelope：

```text
requestId
traceId
tenantId
source = NQ_DRYRUN
timestamp = RFC3339 / ISO-8601 UTC Z
nonce
schemaVersion
dryRun = true
decisionContext
forbiddenCapabilities
```

`forbiddenCapabilities` 必须至少包含：

```text
PLACE_ORDER
CANCEL_ORDER
MUTATE_NQ_STATE
READ_NQ_DB
WRITE_NQ_DB
START_PAPER_RUN
START_LIVE_RUN
CALL_PROVIDER
FORWARD_CREDENTIAL
```

必须禁止：

```text
credential
apiKey
apiSecret
passphrase
accountSecret
executableOrder
BUY
SELL
PLACE_ORDER
CANCEL_ORDER
quantity as executable instruction
leverage as executable instruction
order price as executable instruction
```

`decisionContext` 只能是 read-only、脱敏、可审计上下文，不得包含原始 credential、raw provider response、raw prompt、可执行订单或账户密钥材料。

## 7. Header / signature material

下一轮 implementation 必须对齐 DH 已冻结规则：

- 使用 canonical `X-NQ-DH-*` header。
- `timestamp` 必须为 UTC `Z`。
- `nonce` 每次请求唯一。
- HMAC material 必须按已冻结 value-based 规则生成。
- header name 不进入 signature material。
- `tenantId / source / requestId / traceId` 必须参与安全绑定。
- 不允许 legacy `X-DH-NQ-*`。
- 不允许 anonymous source。
- 不允许 source fallback。

最低 header 形态：

```text
X-NQ-DH-Source
X-NQ-DH-Tenant-Id
X-NQ-DH-Request-Id
X-NQ-DH-Trace-Id
X-NQ-DH-Timestamp
X-NQ-DH-Nonce
X-NQ-DH-Schema-Version
X-NQ-DH-Signature
```

推荐 HMAC value material：

```text
method
path
source
tenantId
requestId
traceId
timestamp
nonce
schemaVersion
bodyHash
```

## 8. Retry / timeout / idempotency

下一轮 implementation 必须满足：

- 默认无自动重试。
- timeout 必须短且可配置。
- timeout 后 fail-closed，只记录不执行。
- 不得复用 nonce 重试。
- 如后续允许 retry，必须生成新 nonce，并保留 `originalCorrelationId`。
- duplicate `requestId` 不得触发交易行为。
- 所有失败只记录 dry-run failure。

任何 retry 设计都必须继续保持 no order / no execution / no risk / no ledger / no paper / no live side effect。

## 9. Response handling

下一轮 implementation 必须解析并验证：

```text
decisionId
dryRun = true
action
confidence
riskLevel
reasons
traceSummary
replayRef
auditRef
schemaVersion
error envelope
```

强制规则：

- `action` 只允许 `OBSERVE / NO_TRADE / LONG_BIAS / SHORT_BIAS`。
- `BUY / SELL` 必须拒绝。
- `PLACE_ORDER / CANCEL_ORDER` 必须拒绝。
- executable quantity / leverage / order price 必须拒绝。
- `LONG_BIAS / SHORT_BIAS` 只记录为 bias。
- DH output 不得进入 NQ execution / risk mutation / ledger / paper / live。
- fail-closed response 只写 audit/log，不执行。

NQ 记录形态只能是 dry-run record / audit summary，不得升级为 strategy state、order intent、risk mutation、ledger entry、Paper Run input 或 LIVE input。

## 10. Audit / logging / redaction

下一轮 implementation 必须要求：

- 记录 `requestId / traceId / tenantId / decisionId / auditRef`。
- 记录 dry-run result。
- 记录 fail-closed reason。
- 不记录 secret。
- 不记录 HMAC secret。
- 不记录 token / cookie / apiKey / apiSecret / passphrase。
- 不记录 raw credential。
- 不记录 executable order payload。
- 日志 redacted。
- NQ 只保存 dry-run record，不改变交易状态。

日志与 audit 的默认原则是 summary-only、least sensitive、traceable、fail-closed。任何 raw payload 保存都必须另起 security review，当前不允许。

## 11. Error taxonomy

下一轮 implementation 必须映射：

```text
SIGNATURE_INVALID
TIMESTAMP_INVALID
TIMESTAMP_OUT_OF_WINDOW
NONCE_REPLAY
TENANT_MISMATCH
SOURCE_DENIED
PAYLOAD_TOO_LARGE
RATE_LIMITED
MEMORY_LIMIT_EXCEEDED
POLICY_DENIED
PROVIDER_DISABLED
PROVIDER_TIMEOUT
BUDGET_EXCEEDED
UNKNOWN_ERROR
CLIENT_DISABLED
CLIENT_TIMEOUT
CLIENT_PARSE_ERROR
RESPONSE_POLICY_VIOLATION
```

规则：

- `UNKNOWN_ERROR` fail-closed。
- `CLIENT_TIMEOUT` fail-closed。
- `RESPONSE_POLICY_VIOLATION` fail-closed。
- security failure 不得 fallback 成功。
- DH error 不得转为 NQ trading signal。
- provider / budget / policy 类错误只记录 dry-run failure 或 readonly abstain，不触发 NQ mutation。

## 12. 测试要求

下一轮 implementation 必须新增或更新测试，并且不得执行真实外部 HTTP。

Client disabled：

- feature flag disabled -> no call。
- kill switch enabled -> no call。
- endpoint url missing -> fail-closed。
- production profile -> disabled。

Request generation：

- timestamp UTC `Z`。
- epoch seconds 不产生。
- epoch milliseconds 不产生。
- nonce unique。
- HMAC generated。
- `X-NQ-DH-*` headers present。
- legacy `X-DH-NQ-*` headers absent。
- `dryRun=true`。
- `source=NQ_DRYRUN`。
- no credential fields。
- `forbiddenCapabilities` included。

Response handling：

- valid `OBSERVE` accepted as record-only。
- valid `NO_TRADE` accepted as record-only。
- `LONG_BIAS` accepted as bias-only。
- `SHORT_BIAS` accepted as bias-only。
- `BUY` rejected。
- `SELL` rejected。
- `PLACE_ORDER` rejected。
- executable quantity rejected。
- `dryRun=false` rejected。
- missing `decisionId` rejected。
- invalid `schemaVersion` rejected。
- error envelope mapped fail-closed。

Boundary tests：

- no order mutation。
- no execution call。
- no ledger mutation。
- no risk mutation。
- no paper run start。
- no live run start。
- no exchange adapter call。
- no provider call。
- no credential logging。
- no real external HTTP in tests。

## 13. 回滚要求

下一轮 implementation 必须提供可回滚策略：

- 删除或禁用 NQ limited dry-run client 不得影响 order / execution / risk / ledger / account / paper / live。
- feature flag default disabled 可以立即回退到 no-call。
- kill switch 必须覆盖所有 outbound attempt。
- endpoint URL 缺失必须保持 fail-closed。
- recorder / audit 写入失败不得 fallback 到交易行为。
- 回滚后 NQ_DRYRUN 仍不得进入 production allowlist。

## 14. 后续拆分

本 WO 关闭后，后续只能拆分为小步：

```text
NQ-DH-I1-NQ-LIMITED-RUNTIME-CLIENT-IMPLEMENTATION
NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TESTS-WO
NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TESTS
NQ-DH-I1-RUNTIME-CLOSE-REVIEW
```

任何 schema/OpenAPI/contracts/golden_cases formalization 必须另起独立 review，不得混入 NQ client implementation。

## 15. Validation requirements

本 WO close 必须至少执行并记录：

```powershell
git status --short
git branch --show-current
git diff --check
git diff --stat
git diff --name-only -- `
  backend/**/src/main `
  backend/**/src/test `
  frontend `
  research `
  scripts `
  deploy `
  .github `
  "backend/**/db/migration"
rg -n "NQ_DRYRUN|decision-dry-runs|WebClient|RestTemplate|OkHttp|HttpClient|RealClient|apiKey|apiSecret|passphrase|credential|token|cookie|BUY|SELL|PLACE_ORDER|CANCEL_ORDER|placeOrder|cancelOrder|paperRunStart|liveRunStart|mutateRisk|mutateLedger|ledger|execution|order" docs/current backend
```

如只改 docs，可以不跑 full Maven test，但不得声称 full test PASS。若运行 Maven，必须记录真实结果、skip 和环境限制。

## 16. Boundary confirmation

```text
NQ runtime client implemented: NO
HTTP client added: NO
DH called: NO
real outbound HTTP: NO
real provider connected: NO
NQ dev modified: NO
NQ production code changed: NO
NQ test code changed: NO
contracts changed: NO
OpenAPI changed: NO
JSON Schema changed: NO
golden_cases changed: NO
fixture JSON changed: NO
migration added: NO
order/execution/risk/ledger/account/paper/live touched: NO
credential read/output: NO
Agent / LangGraph runtime started: NO
LIVE enabled: NO
Runtime integration started: NO
DH integrated: NO
```

## 17. Next concrete action

如果本 WO close 通过，下一步为：

```text
NQ-DH-I1-NQ-LIMITED-RUNTIME-CLIENT-IMPLEMENTATION / NOT STARTED / CONTROLLED_IMPLEMENTATION / DEFAULT_DISABLED / DEV_TEST_ONLY / NO_LIVE
```

该 next action 不等于本轮授权 implementation now；必须由用户另起实现任务后才能进入代码修改。
