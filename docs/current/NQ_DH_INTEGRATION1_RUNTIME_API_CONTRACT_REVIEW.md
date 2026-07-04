# NQ-DH Integration-1 Runtime API Contract Review

> 任务：`NQ-DH-I1-RUNTIME-API-CONTRACT-REVIEW`
> 类型：`REVIEW_ONLY + RUNTIME_API_CONTRACT_SECURITY_REVIEW + CROSS_REPO_BOUNDARY_REVIEW + POST_PR_MERGE_BASELINE + NO_RUNTIME_IMPLEMENTATION + NO_LIVE`
> 日期：2026-07-04
> 仓库视角：NexusQuant（NQ）
> 状态：`CLOSED / ACCEPTED / REVIEW_ONLY / NO_RUNTIME`

## 1. 结论

本轮只评审 limited dry-run runtime 的 API / contract / security 前置条件，不实现 NQ runtime client，不改 NQ production code，不新增测试、fixture、schema、contracts、golden_cases、migration、API、Controller、HTTP 调用、provider、AI / LangGraph 或 LIVE。

结论：**允许关闭本 review；允许后续拆出 DH runtime API work order 与 NQ limited dry-run client work order；不允许现在实现 runtime client 或真实 HTTP**。

Readiness decision：

```text
ALLOW_RUNTIME_API_CONTRACT_REVIEW_CLOSE: YES
ALLOW_DH_RUNTIME_API_WO: YES
ALLOW_NQ_RUNTIME_CLIENT_WO: YES
ALLOW_RUNTIME_IMPLEMENTATION_NOW: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_API_CONTROLLER_CHANGE_NOW: NO
ALLOW_SCHEMA_CHANGE_NOW: NO
ALLOW_CONTRACTS_MODIFICATION_NOW: NO
ALLOW_GOLDEN_CASES_MODIFICATION: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 2. Post-PR baseline

NQ dev 已包含 mock baseline PR：

```text
NQ dev branch: dev
NQ dev current HEAD: b856cf07155de26f87fad9c21234c1a8a07b964a
PR #12 merge commit: 578eb65e Merge pull request #12 from ling5477/nq-dh-i1-dryrun
PR #12 merge relationship: 578eb65e is ancestor of current dev / origin/dev
NQ dev scoped NQ-DH / Integration-1 dirty diff: none
NQ dev staged scoped NQ-DH / Integration-1 diff: none
```

当前 worktree：

```text
Path: E:\Project\nexus-quant-i1-dryrun
Branch: nq-dh-i1-runtime-api-contract-review
HEAD: 578eb65e851086d0668bbebef74c319df1e5d63c
Tracking: origin/nq-dh-i1-runtime-api-contract-review
Branch base at creation: PR #12 merge commit 578eb65e
Current origin/dev during final read-only check: b856cf07155de26f87fad9c21234c1a8a07b964a
```

扫描结论：

- `NqDhIntegration1*`、`NQ_DH_INTEGRATION1*`、`nq-dh/integration1` 只出现在 docs/current、NQ test-support 与 test resources。
- NQ `backend/**/src/main/**` 未发现 `NQ_DRYRUN`、`NqDhIntegration1`、DH runtime client、dry-run runtime client 或 NQ-DH runtime API token。
- 广域 `HttpClient / RealClient / credential / BUY / SELL / order / risk / ledger / paper / live` 命中为既有 adapter、public marketdata、trading、credential、docs prohibition 或 test guard；未发现 NQ-DH runtime actual risk。

## 3. Recommended runtime option

推荐：**Option D：先冻结 API contract / error taxonomy / envelope，再拆 DH/NQ implementation**。

原因：

- PR #12 已把 IMP0-IMP3 mock/test-support baseline 合并到 NQ dev，当前应在新分支上继续 review，不应继续在旧 `nq-dh-i1-dryrun` 分支正向开发。
- API endpoint、`NQ_DRYRUN` source allowlist、error taxonomy、schema envelope、HMAC material、runtime nonce persistence、kill switch 和 rollback 都还没有正式冻结。
- DH endpoint 与 NQ client 必须拆开实施，NQ 侧不得因为 DH work order 启动就提前创建真实 client。

## 4. DH endpoint contract input

NQ 侧接受的 future DH endpoint 方向：

```text
Method: POST
Path: /api/ai/decision-dry-runs
Purpose: NQ -> DH limited dry-run readonly DecisionOutput
Current state: NOT IMPLEMENTED
NQ client state: NOT IMPLEMENTED
```

NQ 未来只允许发送：

- canonical `X-NQ-DH-*` headers。
- `NQ_DRYRUN` source，且必须先通过 source allowlist review。
- RFC3339 / ISO-8601 UTC `Z` timestamp。
- nonce、requestId、traceId、tenantId、schemaVersion。
- read-only `DecisionRequest` 字段和脱敏 context / evidence summary。

NQ 禁止发送：

```text
credential / API secret / passphrase / token / cookie
account secret
raw prompt
raw provider response
accountId / orderId / positionId as execution context
BUY / SELL
quantity / price / leverage
placeOrder / cancelOrder
paperRunStart / liveRunStart
risk mutation / ledger mutation
```

## 5. NQ source allowlist review

`NQ_DRYRUN` 当前仍为 review-gated source，**本轮不允许进入 production allowlist**。

进入 allowlist 前必须满足：

```text
tenant/source pair allowlist
dev/test/manual profile first
production disabled unless later approved
persistent nonce replay guard
tenant/requestId/traceId binding
rate limit
payload cap
memory cap
HMAC
schema version
kill switch
redacted logging
no credential / no account secret
no order / no risk / no ledger / no Paper / no LIVE
```

未满足时，NQ 必须预期 DH 返回 `SOURCE_DENIED`，并只记录拒绝，不执行任何业务动作。

## 6. Error taxonomy review

canonical errors 必须在实现前冻结为正式 enum / contract。建议最小集合：

```text
SOURCE_DENIED
SIGNATURE_INVALID
TIMESTAMP_SKEW
NONCE_REPLAY
TENANT_MISMATCH
REQUEST_ID_MISMATCH
TRACE_ID_MISMATCH
CONTRACT_INVALID
FORBIDDEN_FIELD
PAYLOAD_TOO_LARGE
RATE_LIMITED
PROVIDER_DISABLED
PROVIDER_TIMEOUT
PROVIDER_BUDGET_EXCEEDED
POLICY_DENIED
RISK_BLOCKED
AUDIT_WRITE_FAILED
REPLAY_WRITE_FAILED
KILL_SWITCH_OPEN
RUNTIME_DISABLED
INTERNAL_FAIL_CLOSED
UNKNOWN
```

NQ 处理原则：

- auth / source / timestamp / nonce / tenant / contract 错误只记录拒绝，不重试成交易动作。
- provider timeout / budget exceeded / policy denied / risk blocked / unknown 都视为 fail-closed。
- unknown error 不允许被映射成 `LONG_BIAS`、`SHORT_BIAS`、`BUY` 或 `SELL`。
- NQ response recorder 只保存 readonly summary、error code、requestId、traceId、tenantId、audit/replay refs 和 hash，不保存敏感原文。

## 7. Schema / envelope review

当前 `dryRun / decisionId / confidence / traceSummary / replayRef / auditRef / X-NQ-DH-Schema-Version` 仍是 `DOC_ONLY_ALIAS` 或 future planning。**本轮允许后续合同把它们提升为正式 envelope 字段，但本轮不改 schema、contracts、golden_cases 或 fixture JSON**。

建议：

```text
Required future response fields:
  schemaVersion / dryRun / decisionId / requestId / traceId / tenantId /
  decisionType / action / status / riskLevel / policyStatus / providerStatus /
  forbiddenActions / createdAt

Optional future response fields:
  confidence / traceSummary / replayRef / auditRef / reasonCodes / evidenceRefs / errors
```

existing mock fixtures 不应在本轮迁移；如后续 schema/envelope 改动，需要单独 migration / compatibility review。

## 8. HMAC / replay / binding review

HMAC material 必须冻结后才能实现。推荐候选：

```text
METHOD + "\n" +
PATH + "\n" +
source + "\n" +
tenantId + "\n" +
requestId + "\n" +
traceId + "\n" +
timestamp + "\n" +
nonce + "\n" +
schemaVersion + "\n" +
sha256(canonicalRequestBody)
```

NQ 必须遵守：

- 每次 request 使用 fresh nonce；不得用透明 retry 重放同一 nonce。
- retry 如未来允许，必须保持同一 requestId 的幂等记录语义并使用新 nonce。
- tenantId、requestId、traceId 的 header/body 必须一致。
- timestamp 必须 UTC `Z`。
- body 必须 canonical JSON 后签名。

## 9. NQ no-side-effect runtime boundary

未来 limited client 不得触发：

```text
order placement
order cancellation
risk mutation
ledger mutation
Paper Run start
LIVE
credential read
account secret read
NQ DB mutation based on DH output
```

`LONG_BIAS / SHORT_BIAS` 只能是 read-only analytical bias，不得映射为：

```text
BUY
SELL
PLACE_ORDER
CANCEL_ORDER
order side
quantity
price
leverage
Paper Run input
LIVE execution input
```

Duplicate `requestId` 只允许用于 recorder 幂等和审计；不得触发交易或二次执行。DH output 不得作为自动交易信号进入 NQ trading / paper / live pipeline。

## 10. Feature flag / kill switch / environment isolation

后续 NQ client work order 必须冻结：

```text
NQ dry-run client flag: default disabled
NQ outbound flag: default no-outbound
DH endpoint base URL: absent by default
source allowlist kill switch: required
client circuit breaker: required
short timeout: required
transparent retry: disabled by default
payload / response size cap: required
redacted logging: required
profile isolation: dev/test/manual first; production disabled unless later approved
rollback: disable flags + remove allowlist + revert NQ client PR independently
```

## 11. Audit / trace / replay

NQ recorder 可保存：

- requestId、traceId、tenantId、source、schemaVersion。
- DH decisionId、action、status、riskLevel、policyStatus、providerStatus、reasonCodes。
- `traceSummary`、`auditRef`、`replayRef` 的安全引用。
- request / response hash、error code 和 redacted summary。

NQ recorder 禁止保存：

- credential、API secret、passphrase、token、cookie、private key。
- raw prompt。
- raw provider response。
- HMAC secret、signature 原文或完整敏感 header。
- account secret、private endpoint payload。

## 12. Implementation slicing

后续必须拆分：

```text
1. NQ-DH-I1-DH-RUNTIME-API-WO
2. NQ-DH-I1-DH-LIMITED-RUNTIME-ENDPOINT-IMPLEMENTATION
3. NQ-DH-I1-NQ-LIMITED-DRYRUN-CLIENT-WO
4. NQ-DH-I1-NQ-LIMITED-DRYRUN-CLIENT-IMPLEMENTATION
5. NQ-DH-I1-JOINT-RUNTIME-DRYRUN-TESTS
6. NQ-DH-I1-RUNTIME-CLOSE-REVIEW
```

NQ 侧实现只能在 NQ worktree 分支上做，不得直接在 `E:\Project\nexus-quant` dev worktree 改 Integration-1 内容。

## 13. Risks / blockers before implementation

- DH endpoint 不存在。
- NQ client 不存在。
- `NQ_DRYRUN` allowlist 未冻结。
- error taxonomy 未冻结。
- envelope 字段仍未转正式 schema。
- HMAC material 未冻结。
- runtime nonce persistence、rate limit、payload cap、memory cap、kill switch、circuit breaker 未实现。
- no-side-effect runtime tests 尚未创建。

这些 blocker 不阻止本 review 关闭，但阻止 runtime implementation now。

## 14. Boundary confirmation

```text
NQ production code changed: NO
NQ test code changed: NO
contracts changed: NO
golden_cases changed: NO
fixture JSON changed: NO
API / Controller added: NO
migration added: NO
runtime client added: NO
real HTTP started: NO
real provider connected: NO
credential read/output: NO
order / risk / ledger / Paper / LIVE mutation: NO
AI / LangGraph runtime started: NO
LIVE enabled: NO
NQ dev modified: NO
```

## 15. Next concrete action

```text
NEXT_ACTION: NQ-DH-I1-DH-RUNTIME-API-WO / NOT STARTED / WORK_ORDER_ONLY / NO_RUNTIME_IMPLEMENTATION
```

NQ 侧后续 client 只能在 DH API work order 和合同冻结后进入独立 NQ work order；不得直接实现 runtime client。
