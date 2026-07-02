# NQ-DH Integration-1 Dry-run Contract Plan

> 任务：`NQ-DH-I1-P1-CONTRACT-DRYRUN-PLAN`
> 类型：`PLAN_ONLY + CONTRACT_DRYRUN_PLAN + CROSS_REPO_CONTRACT_MAPPING + SECURITY_BOUNDARY + NO_RUNTIME + NO_LIVE`
> 日期：2026-07-02
> 仓库视角：NexusQuant（NQ）
> 状态：`COMPLETED / PLAN ONLY / NOT IMPLEMENTED`

## 1. 结论

本文件是 NQ 侧 canonical contract dry-run plan。`COMPLETED` 只表示规划完成；`PLAN ONLY` 表示只冻结合同、边界和后续验证计划；`NOT IMPLEMENTED` 表示本轮没有新增 API、Controller、client、provider、dispatcher、Repository、Service、migration、测试代码、fixture 文件或真实 HTTP。

```text
NQ current main line: GateO.
NQ Integration-1 rebase input: GateN no-real public marketdata / exchange sandbox baseline.
DH baseline: DH-STAGE4-DECISION-PIPELINE-MVP / ACCEPTED / CLOSED.
Legacy DH-GATEK-DECISION-PIPELINE-MVP: SUPERSEDED / NAMING_REPLACED.
Old NQ-DH-GATEK-INTEGRATION1-PLAN-PACK: SUPERSEDED / REBASE_REQUIRED.
Integration-1 implementation: NOT STARTED.
Integration-1 runtime: NOT STARTED.
Runtime integration: NOT STARTED.
Real HTTP: NOT STARTED.
Real provider: NOT STARTED.
Agent / LangGraph runtime: NOT STARTED.
LIVE: DISABLED.
```

旧 `NQ_DH_INTEGRATION1_DRYRUN_PLAN.md` 保留为 P1 初稿 / residual reference；本文件是后续 `docs/current` 入口使用的 canonical P1 contract plan。

## 2. 合同方向

P1 固定合同方向：

```text
NQ -> DH: dry-run DecisionRequest.
DH -> NQ: read-only DecisionOutput.
NQ behavior: record / display / audit / manual review only.
DH behavior: structured read-only recommendation only.
```

DH 输出不是交易指令；NQ 不得把 DH action 映射为下单、撤单、风控事实修改、账本修改、持仓修改、Paper Run 启动或 LIVE 执行。

## 3. NQ -> DH dry-run request 规划

本节规划 future wire-level request，不修改当前 schema。进入 implementation 前，必须先在 `I1-P2-CONTRACT-FIXTURES-PLAN` 中决定是否扩展 schema 或用现有字段表达 dry-run。

### 3.1 计划请求体

```json
{
  "schemaVersion": "1.0.0",
  "requestId": "nq-dryrun-req-001",
  "traceId": "trace-i1-dryrun-001",
  "tenantId": "tenant-fixture",
  "source": "NQ_DRYRUN",
  "decisionType": "READ_ONLY_RECOMMENDATION",
  "dryRun": true,
  "requestedAt": "2026-07-02T00:00:00Z",
  "subject": {
    "symbol": "BTC-USDT",
    "market": "SPOT",
    "timeframe": "1m",
    "strategyRef": "strategy-fixture",
    "researchRef": "research-fixture"
  },
  "contextSnapshot": {
    "snapshotId": "snapshot-fixture-001",
    "capturedAt": "2026-07-02T00:00:00Z",
    "evidenceRefs": ["fixture:evidence-001"]
  }
}
```

### 3.2 request schema gap

| 计划字段 | 当前合同状态 | P1 结论 |
| --- | --- | --- |
| `requestId / traceId / tenantId` | DH schema 已存在 | 必须与 header、auth binding、audit record 一致。 |
| `source` | DH schema 已存在 | P1 建议 `NQ_DRYRUN`；P2 需 review allowlist。 |
| `decisionType` | DH schema 仅允许 `READ_ONLY_RECOMMENDATION` | 必须固定。 |
| `subject.symbol / market / timeframe` | DH schema 已存在 | 可直接表达只读标的上下文。 |
| `strategyRef / researchRef` | DH schema 可选字段 | 只允许脱敏只读引用。 |
| `contextSnapshot.evidenceRefs` | DH schema 已存在 | 优先使用现有 snapshot 表达 evidence refs。 |
| `dryRun` | 当前 DH schema 不存在 | 计划字段；不得在本轮写入 schema。 |
| `runRef` / 顶层 `evidenceRefs` | 当前 DH schema 不存在 | P2 决定映射或扩展。 |

### 3.3 request 禁止字段

任何 future request 若出现下列字段或等价语义，必须 `FORBIDDEN_FIELD` / fail-closed：

```text
apiKey
apiSecret
passphrase
token
cookie
accountId
subAccountId
orderId
clientOrderId
positionId
brokerCredential
venueCredential
quantity
price
leverage
side = BUY / SELL
placeOrder
cancelOrder
mutateRisk
mutateLedger
paperRunStart
liveRunStart
```

## 4. DH -> NQ dry-run response 规划

### 4.1 计划响应体

```json
{
  "decisionId": "decision-fixture-001",
  "requestId": "nq-dryrun-req-001",
  "traceId": "trace-i1-dryrun-001",
  "tenantId": "tenant-fixture",
  "decisionType": "READ_ONLY_RECOMMENDATION",
  "status": "OBSERVATION_ONLY",
  "policyStatus": "ALLOWED",
  "action": "OBSERVE",
  "riskLevel": "LOW",
  "confidence": 0.42,
  "reasonCodes": ["DRYRUN_OBSERVATION_ONLY"],
  "evidenceRefs": ["fixture:evidence-001"],
  "providerStatus": "MOCKED",
  "traceSummary": {
    "traceId": "trace-i1-dryrun-001",
    "stepCount": 3
  },
  "replayRef": "replay-fixture-001",
  "auditRef": "audit-fixture-001",
  "dryRun": true,
  "forbiddenActions": [
    "PLACE_ORDER",
    "CANCEL_ORDER",
    "MUTATE_NQ_STATE",
    "READ_NQ_DB",
    "WRITE_NQ_DB"
  ],
  "createdAt": "2026-07-02T00:00:01Z",
  "schemaVersion": "1.0.0"
}
```

### 4.2 response schema gap

| 计划字段 | 当前合同状态 | P1 结论 |
| --- | --- | --- |
| `requestId / traceId / tenantId` | DH output schema 已存在 | NQ 必须校验回显一致性。 |
| `decisionType` | 仅允许 `READ_ONLY_RECOMMENDATION` | 必须固定。 |
| `action` | 仅允许 `ABSTAIN / OBSERVE / NO_TRADE / LONG_BIAS / SHORT_BIAS` | `LONG_BIAS / SHORT_BIAS` 只是只读倾向，不是 `BUY / SELL`。 |
| `riskLevel / policyStatus / providerStatus` | 已存在 | high risk、provider failure、policy denied 默认 fail-closed。 |
| `forbiddenActions` | 已存在且必须包含五项 | 缺任一项即 unsafe。 |
| `reasonCodes / evidenceRefs` | 已存在 | 只允许脱敏摘要和引用。 |
| `decisionId / confidence / traceSummary / replayRef / auditRef / dryRun` | 当前 schema 不存在 | P2/P4 决定 envelope、schema extension 或 NQ 本地 metadata 方案。 |

### 4.3 NQ no-side-effect 边界

- `ABSTAIN` 是默认 fail-closed action。
- `OBSERVE / NO_TRADE` 是只读观察或不交易建议。
- `LONG_BIAS / SHORT_BIAS` 不得映射为 `BUY / SELL`、order side、strategy state mutation 或 risk approval。
- NQ 只允许记录、展示、审计或转人工复核 DH decision summary。
- NQ 不得根据 DH action 直接下单、撤单、修改 risk / ledger / position / strategy / Paper Run / LIVE state。

## 5. Wire-level header / security

P1 沿用 Integration-0 已接受的 canonical header 和安全语义，不覆盖旧合同。

```text
Content-Type: application/json
X-NQ-DH-Request-Id
X-NQ-DH-Trace-Id
X-NQ-DH-Tenant-Id
X-NQ-DH-Source
X-NQ-DH-Timestamp
X-NQ-DH-Nonce
X-NQ-DH-Signature
```

`X-NQ-DH-Schema-Version` 是 P2 review 候选；若未来加入，必须与 body `schemaVersion` 一致，不一致 `CONTRACT_INVALID` / fail-closed。

规则：

- Timestamp 必须为 RFC3339 / ISO-8601 UTC `Z`；epoch seconds、epoch milliseconds、数字时区偏移均拒绝。
- 默认 skew window 沿用 +/-300 秒；超窗 `TIMESTAMP_SKEW`。
- Nonce replay 以 `source + nonce + requestId` 或等价组合校验；重放 `NONCE_REPLAY`，必须 fail-closed。
- HMAC signatureMaterial 至少包含 source、tenantId、requestId、traceId、timestamp、nonce、`sha256(body)`；保持 value-based，不记录 raw signature material 或 shared secret。
- Source allowlist 初始只规划 `NQ_DRYRUN`、`DH_CONTRACT_TEST`、`JOINT_MOCK_VALIDATION`；正式列表必须后续 review。
- Payload size gate 默认 64 KiB；超限 `PAYLOAD_TOO_LARGE`，不得截断后接受。
- Rate limit 按 tenant + source + route 或等价维度；超限 `RATE_LIMITED`。
- tenant / requestId / traceId 必须在 header、body、auth binding、audit record 中一致；不一致 `TENANT_MISMATCH` 或 `CONTRACT_INVALID`。
- 禁止 credential、token、signature raw material、raw request secret、raw provider payload、full prompt、full context 入日志或持久化。

## 6. Error taxonomy / fail-closed

| Error | 触发条件 | fail-closed 行为 |
| --- | --- | --- |
| `AUTH_FAILED` | 缺少认证、认证主体无效 | 拒绝；不执行业务逻辑。 |
| `SIGNATURE_INVALID` | HMAC 缺失、格式错误或不匹配 | 拒绝；不 fallback 到无签名 dry-run。 |
| `TIMESTAMP_SKEW` | timestamp 非 UTC `Z` 或超窗 | 拒绝。 |
| `NONCE_REPLAY` | nonce / requestId 重放 | 拒绝并落 replay audit。 |
| `SOURCE_DENIED` | source 不在 allowlist | 拒绝。 |
| `PAYLOAD_TOO_LARGE` | payload 超 64 KiB 或后续上限 | 拒绝；不得截断接受。 |
| `RATE_LIMITED` | tenant/source/route 超限 | 拒绝或 429；不得进入 provider / decision path。 |
| `CONTRACT_INVALID` | schema、enum、required 字段或 header/body binding 不合法 | 拒绝。 |
| `FORBIDDEN_FIELD` | credential、order、account、quantity、price、side 等禁字段 | 拒绝并高优先级 audit。 |
| `TENANT_MISMATCH` | header/body/auth tenant 不一致 | 拒绝。 |
| `PROVIDER_DISABLED` | DH provider 被禁用或未授权 | `ABSTAIN` / fail-closed。 |
| `PROVIDER_TIMEOUT` | DH provider 超时 | `ABSTAIN` / fail-closed。 |
| `PROVIDER_BUDGET_EXCEEDED` | DH provider 预算或调用上限超出 | `ABSTAIN` / fail-closed。 |
| `RISK_BLOCKED` | 风险策略拒绝 directional bias | `ABSTAIN` 或 `BLOCKED`。 |
| `INTERNAL_FAIL_CLOSED` | 未知异常、audit 写入失败或不可分类错误 | 默认 `ABSTAIN` / 拒绝。 |

NQ 接到任何失败只记录和审计，不触发交易。任何未知错误都不得提升为 `LONG_BIAS / SHORT_BIAS`，更不得解释为 `BUY / SELL / PLACE_ORDER / CANCEL_ORDER`。

## 7. Trace / audit / replay

```text
requestId: NQ 构造 dry-run request 时生成；用于幂等、audit、重复请求识别。
traceId: NQ 与 DH 贯穿同一 dry-run 链路；用于跨系统排查。
decisionId: DH response 规划项；当前 schema 未实现，P2/P4 需决定生成与承载方式。
auditRef: DH / NQ 各自审计引用；不是执行凭证。
replayRef: DH replay read model 引用；只读审计入口，不重跑 provider，不驱动交易。
```

- NQ 只记录 DH decision summary、reasonCodes、evidenceRefs、auditRef/replayRef 和 no-side-effect assertion。
- NQ 不保存 DH provider raw secret，不保存 raw prompt，不保存 raw provider response。
- replayRef / auditRef 只是审计引用，不是授权、执行凭证或交易许可。

## 8. 测试矩阵规划

本轮不写测试代码。后续 P2/P3/P4/P5 至少覆盖：

| Case | 目标 |
| --- | --- |
| valid dry-run request | request/response happy path 可互认。 |
| invalid signature | HMAC mismatch fail-closed。 |
| timestamp skew | 超窗或非 UTC `Z` fail-closed。 |
| nonce replay | 重放 fail-closed。 |
| source denied | 非 allowlist source fail-closed。 |
| payload too large | 64 KiB gate fail-closed。 |
| rate limited | tenant/source/route 超限 fail-closed。 |
| tenant mismatch | header/body/auth tenant 不一致 fail-closed。 |
| forbidden field: credential | apiKey/apiSecret/passphrase/token/cookie 等拒绝。 |
| forbidden field: order/account/quantity/side | orderId/accountId/quantity/price/leverage/BUY/SELL 等拒绝。 |
| DH provider disabled | response `ABSTAIN` / fail-closed。 |
| DH provider timeout | response `ABSTAIN` / fail-closed。 |
| DH budget exceeded | response `ABSTAIN` / fail-closed。 |
| high risk blocked | `HIGH/BLOCKED` 不允许 directional bias。 |
| no evidence fail-closed | 默认 `ABSTAIN`。 |
| NQ no-order guarantee | 不触发 order state machine / place / cancel。 |
| NQ no-risk-mutation guarantee | 不修改 NQ risk facts。 |
| NQ no-paper-run-start guarantee | 不启动 Paper Run。 |
| NQ no-live guarantee | LIVE 保持 disabled，不进入 real trading。 |
| replayRef / auditRef shape | 只读引用可追踪，不含 secret/raw payload。 |
| golden case compatibility | deterministic golden baseline 可复现。 |
| idempotency / duplicate requestId | 同 requestId 同 payload 可幂等，换 payload fail-closed。 |
| no real outbound scan | mock/stub/harness 不触达真实 NQ/DH/交易所 host。 |

## 9. 后续批次

| Batch | 状态 | 目标 | 禁止 |
| --- | --- | --- | --- |
| `I1-P2-CONTRACT-FIXTURES-PLAN` | `NOT STARTED` | 规划双仓 fixture / schema / golden case 对齐，不写 runtime。 | 不写 runtime、不新增真实 endpoint、不真实 HTTP。 |
| `I1-P3-NQ-DRYRUN-STUB-TEST-PLAN` | `NOT STARTED` | 规划 NQ 侧 stub / no-outbound / no-order 测试，不写真实 client。 | 不接 DH runtime、不触发 order/risk/ledger/Paper/LIVE。 |
| `I1-P4-DH-DRYRUN-ENTRY-PLAN` | `NOT STARTED` | 规划 DH 侧 dry-run 入口需求，不实现 Controller/API。 | 不新增 API path、Controller、migration。 |
| `I1-P5-JOINT-MOCK-VALIDATION-PLAN` | `NOT STARTED` | 规划联合 mock 验证，不真实 HTTP。 | 不启动 NQ/DH runtime。 |
| `I1-P6-IMPLEMENTATION-GATE-REVIEW` | `NOT STARTED` | 判断是否允许进入 implementation。 | 即使进入 implementation，也仍禁止 LIVE、real provider、自动下单。 |

## 10. Readiness decision

```text
ALLOW_I1_P1_CONTRACT_PLAN_CLOSE: YES
ALLOW_I1_P2_CONTRACT_FIXTURES_PLAN: YES
ALLOW_INTEGRATION1_DRYRUN_IMPLEMENTATION: NO
ALLOW_INTEGRATION_1_RUNTIME: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 11. 边界确认

本 P1 不修改生产代码；不修改测试代码；不修改 `contracts/**`；不修改 `golden_cases/**`；不新增 API；不新增 migration；不新增 Controller / Client / Repository / Service；不真实 HTTP；不启动 runtime；不读取 credential；不接 provider；不接 AI / LangGraph；不进入 Paper Run；不进入 LIVE；不让 DH 输出进入 order、risk mutation、paper/live trading 或 private trading 路径。
