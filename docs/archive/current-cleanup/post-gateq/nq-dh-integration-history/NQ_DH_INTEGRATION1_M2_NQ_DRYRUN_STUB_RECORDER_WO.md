# NQ-DH Integration-1 M2 NQ Dry-run Stub Recorder Work Order（NQ）

> Task: NQ-DH-I1-M2-NQ-DRYRUN-STUB-RECORDER-WO
> Status: COMPLETED / WORK_ORDER_ONLY / NQ_DRYRUN_STUB_RECORDER_PLANNED / NOT IMPLEMENTED
> Date: 2026-07-03
> Repository: NexusQuant dry-run worktree
> Source of truth: docs/current

## 1. 目标与边界

本工单只规划 NQ 侧 dry-run stub / request builder / DecisionOutput recorder 的后续工作订单。目标是在不实现任何 backend code、test code、fixture JSON、schema、contracts、golden_cases、API、Controller、Client、HTTP、provider 或 runtime 的前提下，明确未来 NQ 侧如何构造 dry-run request、接收 mock `DecisionOutput`、记录只读 summary，并验证 no-side-effect 边界。

本工单不是 implementation，不授权 NQ backend / frontend / research / scripts / deploy / `.github` / migration / contracts / golden_cases / fixture / API / Controller / runtime / 真实 HTTP / RealClient / real provider / AI / LangGraph / LIVE 变更。

NQ dev 主线 `F:\project\nexus-quant` 本轮只读，不允许写入。NQ-DH 相关 NQ 侧后续任务只能在 `F:\worktrees\nexus-quant-i1-dryrun` / `nq-dh-i1-dryrun` 执行。

## 2. 前置边界确认

```text
DH repository: F:\project\decision-hub
DH branch: dev
DH HEAD: 82fa6d9036bc7b2a883e1495967d7c5d7dcc11cf
DH precheck: clean

NQ dry-run worktree: F:\worktrees\nexus-quant-i1-dryrun
NQ dry-run branch: nq-dh-i1-dryrun
NQ dry-run HEAD: 8c44683162c63d2d349d6679e210214d7634ae06
NQ dry-run precheck: clean

NQ dev repository: F:\project\nexus-quant
NQ dev branch: dev
NQ dev HEAD: e62f1e437b5fee9a9e7193f1f777e3e49b343f28
NQ dev precheck: clean
NQ dev NQ-DH / Integration-1 dirty diff: none
WORKSTREAM_MIXED_BLOCKED: NO
```

NQ dev 本轮只读；未修改、未覆盖、未回滚任何 NQ dev 文件。

## 3. M2 总结论

```text
RECOMMENDED_STUB_SHAPE: test-support mock-only stub + in-memory recorder plan, no runtime HTTP client
WHY_NO_REAL_HTTP_NOW: DH M1 已确认 no runtime endpoint；NQ_DRYRUN source、error taxonomy、endpoint shape、schema alias 仍未进入实现级 review
REQUIRED_REVIEW_IF_REAL_CLIENT_NEEDED: API / contract / security / source allowlist / error taxonomy / no-side-effect / runtime isolation review
PROHIBITED_CAPABILITIES: real HTTP, RealClient, real provider, DH runtime connection, NQ runtime mutation, NQ DB access, order, cancel, risk mutation, ledger mutation, Paper Run start, LIVE, credential access, AI / LangGraph runtime
M2 close: YES
M3 work order allowed: YES, only as work-order-only
Implementation code allowed by M2: NO
```

M2 只允许把未来实现切成可审查的 test-support / mock-only 方向。后续如果必须新增真实 HTTP client、runtime DH client、Controller、API path、schema field、fixture JSON、golden case 或 JUnit 测试，必须在后续任务中单独授权并重新做边界确认。

## 4. NQ dry-run stub 规划

### RECOMMENDED_STUB_SHAPE

```text
test-support mock-only stub + in-memory recorder plan
no runtime endpoint
no HTTP client
no RealClient
no Spring runtime wiring
no provider
no LIVE
```

推荐理由：

- DH M1 已明确 `Option C / test-support mock-only / no runtime endpoint`。
- `source=NQ_DRYRUN` 仍为 `NEEDS_SECURITY_CONTRACT_CHANGE`，NQ 不能把它作为已实现 runtime source。
- 当前没有可调用的 DH dry-run API path；`POST /api/ai/feedback/nq` 是 feedback ingest，不是 decision dry-run endpoint。
- mock-only stub 能先验证 request builder、recorder 和 no-side-effect 设计，不提前引入网络、凭证、provider 或 runtime 状态。

### WHY_NO_REAL_HTTP_NOW

当前不允许真实 HTTP 的原因：

- DH dry-run endpoint shape 尚未进入 API / contract / security review。
- canonical error taxonomy 尚未全部实现，M2 只能规划 recorder normalization。
- `decisionId / confidence / traceSummary / replayRef / auditRef / X-NQ-DH-Schema-Version` 仍为 `DOC_ONLY_ALIAS` 或 future envelope planning。
- 真实 HTTP client 会扩大凭证、超时、重试、日志脱敏、网络隔离和 side-effect 风险，但本轮没有实现授权。
- Integration-1 runtime、DH runtime integration、real provider、AI / LangGraph 和 LIVE 均为 `NOT STARTED` 或 `DISABLED`。

### REQUIRED_REVIEW_IF_REAL_CLIENT_NEEDED

若未来确实需要 real client / runtime HTTP，必须另起 review：

```text
DH API path / Controller / OpenAPI review
NQ client package boundary review
source allowlist and tenant binding review
canonical error taxonomy review
HMAC signatureMaterial and header binding review
timeout / retry / rate-limit / circuit-breaker review
no-order / no-risk / no-ledger / no-paper / no-live side-effect review
credential non-access and log redaction review
test profile / runtime profile isolation review
rollback and kill-switch review
```

### PROHIBITED_CAPABILITIES

```text
real HTTP
RealClient
real provider
DH runtime connection
NQ runtime mutation
NQ DB read/write
order placement
order cancellation
position / account / ledger mutation
risk mutation
Paper Run start
credential / token / API secret / passphrase access
AI / Agent runtime
LangGraph runtime
LIVE
```

## 5. NQ dry-run request builder 规划

未来 request builder / factory 只能从安全的 read-only context、fixture 或 test-support 输入构造，不得从账户、订单、持仓、凭证对象直接取敏感字段。

### 允许字段

```text
requestId
traceId
tenantId
source，当前 NQ_DRYRUN 仍需要 security contract review
timestamp RFC3339 UTC Z
nonce
signature，value-based signatureMaterial
decisionType = READ_ONLY_RECOMMENDATION
subject / symbol / market / timeframe
contextSnapshot / evidence summary，仅限脱敏只读信息
```

### 禁止字段

```text
accountId
subAccountId
orderId
clientOrderId
positionId
credential / token / apiKey / apiSecret / passphrase
BUY / SELL
quantity / price / leverage
placeOrder / cancelOrder
mutateRisk / mutateLedger
paperRunStart / liveRunStart
raw prompt / raw context / raw provider response
```

### 构造规则

- 任一 forbidden field 命中都必须 fail-closed。
- `source=NQ_DRYRUN` 在 security contract review 通过前只能作为 future source plan，不得写成已实现 allowlist。
- `timestamp` 必须是 RFC3339 / ISO-8601 UTC `Z`，不得使用 epoch seconds、epoch milliseconds 或数字时区偏移。
- `signatureMaterial` 必须是 value-based canonical material；不得把 header name、raw secret 或签名原串写入日志、response 或 recorder。
- request builder 不得触达 account、order、position、ledger、credential、exchange adapter、risk mutation、Paper Run 或 LIVE service。

## 6. NQ DecisionOutput recorder 规划

未来 recorder 只记录 summary，不执行任何交易或状态修改。recorder 的输入必须来自 mock-only `DecisionOutput` 或 fail-closed response summary，不得接真实 DH runtime response。

### 允许记录的只读字段

```text
requestId
traceId
tenantId
action whitelist
riskLevel
policyStatus
providerStatus
reasonCodes / reasons
forbiddenActions
fail-closed status
audit summary
receivedAt / recordedAt test-support timestamp
```

### 非 required stored fields

```text
decisionId: DOC_ONLY_ALIAS unless future schema review accepts wire field
confidence: DOC_ONLY_ALIAS unless future schema review accepts wire field
traceSummary: DOC_ONLY_ALIAS / future envelope planning
replayRef: DOC_ONLY_ALIAS unless future replay API/schema review accepts wire field
auditRef: DOC_ONLY_ALIAS unless future audit/schema review accepts wire field
X-NQ-DH-Schema-Version: DOC_ONLY_ALIAS / future header review
```

### recorder 安全规则

- `ABSTAIN / OBSERVE / NO_TRADE / LONG_BIAS / SHORT_BIAS` 只能作为 display / audit / manual review 输入。
- `LONG_BIAS / SHORT_BIAS` 是只读倾向，不等于 `BUY / SELL`。
- error response 只能记录为 blocked / rejected / fail-closed summary，不触发交易。
- recorder 不调用 order、cancel、risk mutation、ledger mutation、Paper Run、LIVE、provider、exchange adapter、credential 或 NQ DB private state。
- recorder 不保存 credential、token、API secret、passphrase、raw signature、raw secret、raw provider response、内部异常栈、SQL 或包名。

## 7. no-side-effect 测试规划

M2 只规划测试类型，本轮不写测试代码、不创建 fixture JSON。后续 M3 如单独授权，可基于现有 Integration0 test-only 风格补充 joint mock fixtures and contract tests。

未来测试矩阵必须覆盖：

```text
no-real-http guard
no-order guard
no-cancel-order guard
no-risk-mutation guard
no-ledger-mutation guard
no-paper-run-start guard
no-live guard
no-credential read/log/persist guard
forbidden field scan
duplicate requestId / idempotency
tenant mismatch
source denied
provider failure response only records
high risk / no evidence fail-closed response only records
long/short bias never maps to BUY/SELL
recorder does not call execution/risk/ledger/paper/live services
```

测试实现原则：

- 使用 test-only spy / tracker / fake repository / in-memory recorder；不得使用真实 HTTP、真实 provider、真实 credential、真实交易所或真实 NQ DB。
- 接受路径与拒绝路径都必须断言 side-effect count 为 0。
- 重复 `requestId` 应测试幂等或重复记录拒绝策略；具体存储窗口仍需后续 implementation review。
- tenant mismatch、source denied、provider failure、高风险/无证据 fail-closed 只能记录，不得驱动交易链路。

## 8. M3 入场条件

```text
ALLOW_M2_WO_CLOSE: YES
ALLOW_I1_M3_JOINT_MOCK_FIXTURES_AND_CONTRACT_TESTS_WO: YES
ALLOW_I1_DRYRUN_MOCK_IMPLEMENTATION_CODE: NO
ALLOW_SCHEMA_CHANGE: NO
ALLOW_CONTRACTS_MODIFICATION: NO
ALLOW_FIXTURE_IMPLEMENTATION: NO
ALLOW_GOLDEN_CASES_MODIFICATION: NO
ALLOW_API_CONTROLLER_CHANGE: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_INTEGRATION_1_RUNTIME: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

M3 已完成 work-order-only 收口，结论见 `docs/current/NQ_DH_INTEGRATION1_M3_JOINT_MOCK_FIXTURES_AND_CONTRACT_TESTS_WO.md`。M3 未创建 fixture JSON、测试代码、schema/contracts/golden_cases、API、Controller、runtime、真实 HTTP、real provider、AI / LangGraph 或 LIVE。

M3 已收口为：

```text
NQ-DH-I1-M3-JOINT-MOCK-FIXTURES-AND-CONTRACT-TESTS-WO / COMPLETED / WORK_ORDER_ONLY / FINAL_WO_BEFORE_IMPLEMENTATION / NOT IMPLEMENTED
```

M3 之后不再继续创建 M4/M5 大规划文档。若进入下一步，必须只走受控 IMP0 test-support implementation。

## 9. 验收标准

- M2 work order 已记录 `RECOMMENDED_STUB_SHAPE`、`WHY_NO_REAL_HTTP_NOW`、`REQUIRED_REVIEW_IF_REAL_CLIENT_NEEDED` 与 `PROHIBITED_CAPABILITIES`。
- request builder 允许/禁止字段已明确，forbidden field 必须 fail-closed。
- recorder 只读 summary 边界已明确，`LONG_BIAS / SHORT_BIAS` 不映射 `BUY / SELL`。
- no-side-effect 测试规划覆盖 HTTP、order、cancel、risk、ledger、paper、LIVE、credential、tenant/source、idempotency 和 provider failure。
- readiness decision 只允许 M3 work order，不允许 implementation code。
- NQ worktree 禁止范围 diff 为空；NQ dev 未写入且 NQ-DH / Integration-1 dirty diff 为空。

## 10. 回滚要求

回滚只需 revert 本轮 NQ dry-run worktree 与 DH current docs 的 M2 文档变更，并重跑：

```powershell
git diff --check
git diff --name-only -- backend frontend research scripts deploy .github "backend/**/db/migration"
```

NQ dev 不应出现任何回滚动作，因为本轮不修改 NQ dev。

## 11. 下一步

```text
NQ-DH-I1-IMP0-CONTRACT-GAP-TEST-SUPPORT-IMPLEMENTATION / NOT STARTED / CONTROLLED_IMPLEMENTATION_BATCH_ALLOWED
```

下一步只能进入受控 test-support implementation batch；不得直接创建 runtime、真实 HTTP、real provider、AI / LangGraph 或 LIVE。
