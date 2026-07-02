# NQ-DH Integration-1 Contract Dry-run Plan

> 任务：`NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN`
> 仓库视角：NexusQuant（NQ）
> 日期：2026-07-02
> 状态：`COMPLETED / PLAN ONLY / NOT IMPLEMENTED`
> 前置事实源：`NQ-DH-I1-P0-FACTSOURCE-REBASE / PASS / CLOSED`

## 1. 当前状态

`COMPLETED`（已完成）表示本文件完成 Integration-1 contract dry-run 规划；`PLAN ONLY`（仅规划）表示只冻结合同、边界、测试与后续切片；`NOT IMPLEMENTED`（未实现）表示本轮没有任何 runtime、API、测试代码、fixture 文件、client、provider、dispatcher 或真实 HTTP 实现。

```text
NQ-DH-I1-P0-FACTSOURCE-REBASE: PASS / CLOSED.
P1 contract dry-run plan: COMPLETED / PLAN ONLY / NOT IMPLEMENTED.
Integration-1 implementation: NOT STARTED.
Integration-1 runtime: NOT STARTED.
Runtime integration: NOT STARTED.
DH integrated: NO / NOT_INTEGRATED.
LIVE: DISABLED.
AI / LangGraph runtime: NOT STARTED.
RealClient / real provider: NOT IMPLEMENTED.
GateO current work line: separate; not overwritten by this plan.
```

## 2. 目标

本规划只定义 NQ 与 DH 在不真实联调、不启动 runtime、不调用 provider、不执行交易的条件下，如何验证 dry-run contract 的消息、header、安全、tenant、trace、nonce、HMAC、错误码、审计语义和 no-side-effect 边界。

核心判定：

```text
DH 输出建议或 dry-run request。
NQ 只做 contract validation / security validation / dry-run response。
NQ 是交易事实源。
DH 不是交易执行方。
```

## 3. 非目标

以下内容不属于本轮，也不得由本规划直接授权：

- 不实现 Integration-1 runtime。
- 不新增真实 HTTP endpoint、Controller、client、provider、dispatcher 或 migration。
- 不新增或修改生产代码、测试代码、CI workflow、frontend、research、scripts 或 deploy。
- 不启动 NQ 调 DH，也不启动 DH 调 NQ。
- 不读取 NQ credential，不输出 credential material。
- 不调用真实交易所，不执行 permission probe。
- 不进入 order、cancel、transfer、withdraw、risk mutation、ledger mutation、Paper Run start / stop、strategy state mutation 或 private trading。
- 不接 AI / LangGraph runtime，不开启 LIVE。

## 4. Dry-run 定位

Integration-1 dry-run 是合同验证，不是 runtime integration。它只验证以下事实是否可被安全表达和拒绝：

- 消息字段是否完整、类型是否可判定、禁止字段是否可拒绝。
- `X-NQ-DH-*` header 是否一致、可签名、可追踪。
- `tenantId`、`requestId`、`traceId`、`timestamp`、`nonce`、HMAC、source allowlist 和 payload size gate 是否 fail-closed。
- dry-run response 是否能明确表达 accepted / rejected / security failure / replay / tenant mismatch / forbidden side effect。
- audit 语义是否足以证明没有交易副作用、没有 credential 访问、没有 provider 路径。

dry-run 不触发 NQ 写操作，不启动 Paper Run，不修改 strategy / order / position / ledger / risk facts，不读取 credential，不调用真实交易所，不接 AI runtime。

## 5. NQ 侧允许范围

后续 implementation review 可以在单独授权下选择 contract validation endpoint 或 fixture harness；本轮不决定新增真实 API。

允许规划：

- contract validation endpoint proposal 或 offline fixture harness proposal。
- mock / fake dry-run handler。
- tenant binding validation。
- `requestId` / `traceId` propagation。
- timestamp / nonce / replay protection validation。
- HMAC verification。
- payload size gate。
- source allowlist。
- audit event planning。
- error code mapping。
- dry-run result schema。

持续禁止：

- order command / cancel command。
- transfer / withdraw。
- Paper Run start / stop。
- strategy state mutation。
- risk result mutation。
- ledger mutation。
- credential read。
- provider call。
- real exchange call。
- live permission probe。

## 6. DH 侧允许范围

DH 侧只能输出候选、建议、反馈或 contract-test payload，且必须保持 mock / fake / fixture 语境。

允许规划：

- `StrategyCandidate`
- `DecisionAdvice`
- `FeedbackDryRunRequest`
- `RiskOpinion`
- `ContractTestRequest`
- dry-run fixture
- mock outbound payload
- audit-friendly trace context

持续禁止：

- order intent execution。
- wallet execution。
- private trading adapter。
- direct NQ DB write。
- direct NQ credential read。
- direct provider call。
- direct real exchange call。
- bypass NQ risk。
- start Paper Run。
- enable LIVE。

## 7. Header / auth / replay 策略

P1 dry-run 沿用 Integration-0 已接受的 canonical security strategy，但只作为 dry-run contract plan，不代表 runtime implementation completed。

Canonical header proposal：

```text
Content-Type: application/json
X-NQ-DH-Source
X-NQ-DH-Tenant-Id
X-NQ-DH-Request-Id
X-NQ-DH-Trace-Id
X-NQ-DH-Timestamp
X-NQ-DH-Nonce
X-NQ-DH-Signature
X-NQ-DH-Signature-Version
```

规则：

- `X-NQ-DH-*` 是 P1 dry-run 的唯一 canonical header family；legacy `X-DH-NQ-*` 不得作为新合同入口，若与 canonical header 并存且值冲突必须 `HEADER_CONFLICT` fail-closed。
- `tenantId` 的权威来源是 NQ 侧 tenant binding policy；header、body 和 fixture binding 必须一致，不一致返回 `TENANT_MISMATCH`。
- `requestId` / `traceId` 由发送方提供、NQ 校验和回显；NQ 生成 `auditRef` 用于本地审计引用。`requestId` 不等于 `traceId` 时不得互相覆盖。
- `timestamp` 必须为 RFC3339 / ISO-8601 UTC `Z`；默认 skew window 沿用 ±300s，超窗返回 `TIMESTAMP_SKEW`。
- `nonce` 必须在 dry-run 验证范围内唯一；replay 返回 `REPLAY_NONCE`。Integration-0 的 test-only 内存 nonce store 不足以授权 runtime，P1 implementation 前必须明确可验证的 replay store 或 fixture harness 行为。
- `source` 必须命中 allowlist，例如 `DH_STAGE4_MOCK`、`DH_CONTRACT_TEST`、`NQ_FIXTURE_HARNESS`；否则返回 `SOURCE_NOT_ALLOWED`。
- payload size 默认沿用 64 KiB 上限；超限返回 `PAYLOAD_TOO_LARGE`。
- HMAC 只验证 canonical string，不记录 raw signature material，不记录 shared secret，不把 signing material 写入仓库。fixture 可使用脱敏占位或 test-only mock signer。
- signature mismatch 返回 `INVALID_SIGNATURE`，且不得 fallback 到无签名 dry-run。

## 8. Dry-run contract schema

本 schema 是 future contract proposal，不是已实现 API。

### DryRunRequest

```json
{
  "source": "DH_CONTRACT_TEST",
  "tenantId": "tenant-fixture",
  "requestId": "req-fixture-001",
  "traceId": "trace-fixture-001",
  "timestamp": "2026-07-02T00:00:00Z",
  "nonce": "nonce-fixture-001",
  "candidateId": "candidate-fixture-001",
  "strategyCode": "strategy-fixture",
  "symbol": "BTC-USDT",
  "timeframe": "1m",
  "actionType": "ADVICE_ONLY",
  "confidence": 0.42,
  "evidenceRefs": ["fixture:evidence-001"],
  "riskNotes": ["dry-run only"],
  "dryRunMode": "CONTRACT_VALIDATION",
  "manualApprovalRequired": true
}
```

字段边界：

- `actionType` 只允许 advice / review / feedback / contract-test 语义；任何 place/cancel/transfer/withdraw/private-trading 语义必须拒绝。
- `confidence` 只能用于建议可信度，不得映射到 order size、risk decision 或 live execution。
- `evidenceRefs` 只能引用脱敏 fixture / mock evidence，不得引用 raw prompt、credential、provider payload 或私有交易数据。
- 不得加入 `secret`、`token`、`credential`、`apiKey`、`passphrase`、`orderId`、`realTradeId`、`liveExecutionId` 字段。

### DryRunResponse

```json
{
  "accepted": false,
  "decision": "REJECTED",
  "rejectionCode": "UNSUPPORTED_DRYRUN_ACTION",
  "rejectionReason": "dry-run contract forbids trading side effects",
  "tenantId": "tenant-fixture",
  "requestId": "req-fixture-001",
  "traceId": "trace-fixture-001",
  "auditRef": "audit-fixture-001",
  "dryRunOnly": true,
  "noTradingSideEffect": true,
  "noCredentialAccess": true,
  "createdAt": "2026-07-02T00:00:01Z"
}
```

响应边界：

- `accepted=true` 只能表示 contract dry-run accepted，不代表交易接受、不代表风险允许、不代表 Paper Run 启动。
- 所有 response 必须固定 `dryRunOnly=true`、`noTradingSideEffect=true`、`noCredentialAccess=true`。
- `auditRef` 只能是脱敏审计引用，不得包含 request body、signature、secret、credential 或 provider payload。

## 9. 错误码规划

错误码只用于 dry-run contract，不代表 runtime 已实现。任何疑似交易副作用必须 fail-closed。

| Code | 含义 | 默认处理 |
| --- | --- | --- |
| `INVALID_SIGNATURE` | HMAC mismatch 或签名缺失 | reject / no fallback |
| `REPLAY_NONCE` | nonce 重放 | reject / audit replay |
| `TIMESTAMP_SKEW` | timestamp 超出窗口或格式错误 | reject |
| `SOURCE_NOT_ALLOWED` | source 不在 allowlist | reject |
| `TENANT_MISMATCH` | header/body/binding tenant 不一致 | reject |
| `PAYLOAD_TOO_LARGE` | payload 超过上限 | reject |
| `HEADER_CONFLICT` | canonical 与 legacy header 冲突 | reject |
| `UNSUPPORTED_DRYRUN_ACTION` | actionType 不属于 dry-run advice/review/feedback | reject |
| `TRADING_SIDE_EFFECT_FORBIDDEN` | 发现 order/risk/ledger/Paper Run mutation 企图 | reject / high-severity audit |
| `CREDENTIAL_ACCESS_FORBIDDEN` | 发现 credential access 企图 | reject / high-severity audit |
| `RUNTIME_INTEGRATION_DISABLED` | 触发 runtime integration 路径 | reject |
| `LIVE_DISABLED` | 触发 LIVE 语义 | reject |
| `PROVIDER_DISABLED` | 触发 provider / RealClient / exchange call 语义 | reject |

## 10. Audit / log 规划

必须规划的 audit event：

- `DRYRUN_REQUEST_RECEIVED`
- `DRYRUN_SIGNATURE_VERIFIED`
- `DRYRUN_SIGNATURE_REJECTED`
- `DRYRUN_NONCE_REPLAY_REJECTED`
- `DRYRUN_TENANT_BINDING_ACCEPTED`
- `DRYRUN_TENANT_BINDING_REJECTED`
- `DRYRUN_ACCEPTED`
- `DRYRUN_REJECTED`
- `DRYRUN_FORBIDDEN_TRADING_SIDE_EFFECT_BLOCKED`
- `DRYRUN_CREDENTIAL_ACCESS_BLOCKED`
- `DRYRUN_PROVIDER_PATH_BLOCKED`

日志与 audit metadata 只能包含脱敏字段：event type、tenantId、source、requestId、traceId、auditRef、result、error code、payload size class、timestamp validation result、nonce validation result、signature verification result。

禁止日志：

- raw signature material。
- raw secret。
- credential。
- full body。
- API key。
- token。
- private key。
- passphrase。
- provider raw request / raw response。

## 11. Implementation batches

本节只规划未来切片，不授权本轮 implementation。

### Batch P1-A：contract schema + fixture plan

- 只落文档 / JSON fixture / contract examples。
- 不接 runtime，不新增真实 endpoint。
- 输出 dry-run schema、positive/negative fixture catalog、forbidden field list、fixture hygiene checklist。

### Batch P1-B：NQ dry-run validator skeleton

- mock handler 或 fixture harness。
- no side effect、no provider、no DB mutation。
- 只验证 header、tenant、timestamp、nonce、HMAC、payload size、forbidden action、response flags。
- 不启动 NQ 调 DH。

### Batch P1-C：DH dry-run outbound mock

- fake client / mock outbound payload。
- no real HTTP、no runtime integration。
- 不写 NQ DB，不读 NQ credential，不绕过 NQ risk。

### Batch P1-D：contract tests

- happy path。
- signature fail。
- replay fail。
- tenant mismatch。
- payload too large。
- forbidden action。
- no order / risk / ledger / Paper Run mutation。

### Batch P1-E：dry-run acceptance review

- 确认 no runtime / no provider / no LIVE。
- 确认 P0/P1/P2 findings 已关闭或登记。
- 决定是否允许后续单独进入 runtime-readiness planning；默认不允许直接跳到 runtime integration。

## 12. 测试计划

进入任何 implementation 前，必须先冻结测试计划；进入任何 runtime 前，必须有可运行测试证明下列断言：

- no real HTTP test。
- no provider test。
- no credential access test。
- no order mutation test。
- no risk mutation test。
- no Paper Run mutation test。
- no ledger mutation test。
- tenant mismatch fail-closed。
- replay nonce fail-closed。
- signature mismatch fail-closed。
- source allowlist fail-closed。
- payload too large fail-closed。
- traceId / requestId propagation。
- `dryRunOnly=true` assertion。
- `noTradingSideEffect=true` assertion。
- `noCredentialAccess=true` assertion。

## 13. P0/P1/P2/P3 风险

P0：

- 文档把 Integration-1 runtime 写成 started / implemented。
- 文档把 DH 写成 integrated。
- 文档把 LIVE / RealClient / real provider / AI trading 写成启用。
- 文档包含真实 credential material。
- 文档要求真实交易所调用或真实下单。
- dry-run 规划允许 order / risk / ledger / Paper Run mutation。

P1：

- dry-run 与 runtime implementation 边界不清。
- NQ-DH 与 GateO 状态互相污染。
- header / tenant / nonce / HMAC 安全策略不清。
- WORK_ORDER 指向真实联调但无安全前置。
- 错误码和测试边界不完整。

P2：

- fixture catalog 尚未落成实际 JSON 文件。
- `NQ_DH_INTEGRATION1_DRYRUN_PLAN_REBASEN.md` 文件名仍保留 P0 历史可读性问题。
- README / docs/current/README 同时保留 P0 baseline 与 P1 plan 入口，存在轻微重复。

P3：

- 中英状态枚举混排，但用于保持合同字段和状态值精确。

## 14. Final decision

```text
NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN: COMPLETED / PLAN ONLY / NOT IMPLEMENTED.
ALLOW_P1_A_CONTRACT_SCHEMA_FIXTURE_PLAN_REVIEW: YES.
ALLOW_P1_IMPLEMENTATION_FROM_THIS_TASK: NO.
ALLOW_INTEGRATION1_RUNTIME: NO.
ALLOW_REAL_HTTP: NO.
ALLOW_REAL_PROVIDER: NO.
ALLOW_LIVE: NO.
ALLOW_AI_LANGGRAPH_RUNTIME: NO.
```

下一步只能是独立授权的 `NQ-DH-I1-P1-A-CONTRACT-SCHEMA-FIXTURE-PLAN-REVIEW` 或同等 contract fixture planning/review；不得直接进入 runtime integration。
