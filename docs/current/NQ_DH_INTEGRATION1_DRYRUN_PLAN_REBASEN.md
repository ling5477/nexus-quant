# NQ-DH Integration-1 Dry-run Plan RebaseN

> 任务：`NQ-DH-INTEGRATION1-DRYRUN-PLAN-REBASEN`  
> 类型：`PLAN_ONLY + NQ_GATE_N_REBASE + INTEGRATION1_DRYRUN_BOUNDARY + CONTRACT_PLANNING + SECURITY_BOUNDARY + NO_RUNTIME + NO_LIVE`  
> 日期：2026-07-02  
> 仓库视角：NexusQuant（NQ）  
> 结论：`PASS / PLAN ONLY / READY FOR P0 FACTSOURCE REBASE`  
> 下一步：`NQ-DH-I1-P0-FACTSOURCE-REBASE / NOT STARTED`

## 1. 当前事实重定位

本计划只重新规划 NQ-DH Integration-1 dry-run，不启动 runtime，不实现真实 HTTP，不新增 API / migration / client / provider / tests。

当前事实：

```text
NQ current planning baseline: GateN no-real public marketdata / exchange sandbox baseline frozen and tagged.
NQ GateO O-1 controlled public outbound guard: PASS / ACCEPTED / FROZEN; GateO stage NOT COMPLETED.
DH GateK Decision Pipeline MVP: CLOSED / ACCEPTED.
DH K1-K8: CLOSED.
Integration-0 safety gate: CLOSED / ACCEPTED.
P1-4 residual: CLOSED.
Header alignment: CLOSED.
Timestamp alignment: CLOSED / ACCEPTED.
Code reality audit blockers: fixed.
DH security state: FULL.
DH fail-closed state: FULL.
Old NQ-DH-GATEK-INTEGRATION1-PLAN-PACK: SUPERSEDED / REBASE_REQUIRED.
Integration-1: NOT STARTED.
Runtime integration: NOT STARTED.
DH integrated: NO.
AI / Agent runtime: NOT STARTED.
LangGraph runtime: NOT STARTED.
LIVE: DISABLED.
```

旧 `NQ-DH-GATEK-INTEGRATION1-PLAN-PACK` 只保留为历史风险输入，不再作为当前主线。新的 Integration-1 必须基于 NQ GateN 之后的事实源重新规划；第一阶段只能是 dry-run。dry-run 不等于 runtime integration，不等于真实 HTTP，不等于真实交易，不等于 LIVE。

## 2. 目标

Integration-1 dry-run 的目标是建立后续实现门槛，而不是实现真实联调。

1. 验证 NQ 能构造符合 DH `DecisionRequest` 合同的请求。
2. 验证 DH 能返回符合 `DecisionOutput` 语义的只读建议。
3. 验证 `requestId / traceId / tenantId / decisionId` 全链路可追踪。
4. 验证 NQ 只记录 DH 输出，不执行 DH 输出。
5. 验证 DH 输出不包含真实交易指令。
6. 验证 no-live-trade guarantee。
7. 验证 dry-run 可被审计、可 replay、可 golden case 回归。
8. 验证 Integration-1 后续 runtime 前还需要独立 implementation gate。

## 3. 非目标

本计划明确不做：

```text
不启动 Integration-1 runtime
不真实 HTTP 联调
不新增真实 NQ client
不新增真实 DH client
不新增 RealClient
不新增真实 provider
不接交易所
不读取密钥
不读取或写入 NQ DB
不修改 NQ 交易核心
不启动 Paper Run
不启动 LIVE
不接 AI / LangGraph / LLM
不让 DH 输出成为交易指令
不让 NQ 执行 DH 输出
不把 dry-run 写成 runtime integration
不把 DH 写成 integrated
```

## 4. NQ / DH 职责边界

### 4.1 NQ 负责

- 账户。
- 行情。
- 策略执行。
- 回测。
- paper trading。
- order lifecycle。
- hard risk gate。
- ledger。
- trading audit。
- broker / exchange adapter。
- 真实交易风控。
- 对 DH 输出只记录、不执行。

### 4.2 DH 负责

- structured decision output。
- risk opinion。
- decision audit。
- decision snapshot。
- decision replay read model。
- provider guard。
- mock-only dry-run response。
- prompt / tool / context governance 的后续规划。
- 不直接下单。
- 不改 NQ 状态。
- 不读写 NQ DB。
- 不接真实 exchange / broker。

## 5. 合同规划

本节是后续合同 review 的输入，不修改当前 JSON Schema。当前 DH `dh-decision-request.schema.json` 和 `dh-decision-output.schema.json` 已冻结 `READ_ONLY_RECOMMENDATION`、只读 action vocabulary 和固定 `forbiddenActions`；但 `dryRun`、`decisionId`、`confidence`、`replayRef`、`auditRef` 等字段尚未全部进入当前 schema。若后续需要 wire-level 字段扩展，必须单独执行 contract review，不得在本计划中直接实现。

### 5.1 NQ -> DH dry-run request

计划请求字段：

```text
requestId
traceId
tenantId
source = NQ_DRYRUN
decisionType = READ_ONLY_RECOMMENDATION
subject.symbol
subject.market
subject.timeframe
contextSnapshot / evidenceRefs
requestedAt
schemaVersion
dryRun = true
```

当前合同映射：

- `requestId / traceId / tenantId / source / decisionType / subject / contextSnapshot / requestedAt / schemaVersion` 已在 K1 request schema 中存在。
- `contextSnapshot.evidenceRefs` 已存在；若 NQ 只传 `evidenceRefs` 而不传完整 snapshot，需在 P1 明确使用 `contextSnapshot` 还是新增顶层字段。
- `dryRun=true` 是 Integration-1 dry-run 语义建议字段；当前 schema 尚未包含，P1 必须选择：新增 schema 字段，或通过 `source=NQ_DRYRUN` 与 `decisionType=READ_ONLY_RECOMMENDATION` 表达 dry-run，不得无 review 破坏 `additionalProperties=false`。

禁止字段：

```text
orderId
accountId
apiKey
apiSecret
passphrase
leverage
quantity
price
side = BUY / SELL
venueCredential
brokerCredential
placeOrder
cancelOrder
```

### 5.2 DH -> NQ dry-run response

计划响应字段：

```text
decisionId
requestId
traceId
tenantId
decisionType = READ_ONLY_RECOMMENDATION
action in ABSTAIN / OBSERVE / NO_TRADE / LONG_BIAS / SHORT_BIAS
riskLevel
policyStatus
confidence
reasons / reasonCodes
forbiddenActions
replayRef / auditRef
dryRun = true
```

`forbiddenActions` 固定五项：

```text
PLACE_ORDER
CANCEL_ORDER
MUTATE_NQ_STATE
READ_NQ_DB
WRITE_NQ_DB
```

当前合同映射：

- `requestId / traceId / tenantId / decisionType / action / status / riskLevel / policyStatus / providerStatus / forbiddenActions / reasonCodes / evidenceRefs / createdAt / schemaVersion` 已在 K1 output schema 中存在。
- `decisionId / confidence / reasons / replayRef / auditRef / dryRun` 属 dry-run response envelope 规划项；P2/P4 必须决定是否扩展 schema、使用 audit/replay reference wrapper，或保持 current schema 并由 NQ 记录本地 dry-run metadata。
- `action` 只能是 `ABSTAIN / OBSERVE / NO_TRADE / LONG_BIAS / SHORT_BIAS`；禁止 `BUY / SELL / PLACE_ORDER / CANCEL_ORDER / MARKET_ORDER / LIMIT_ORDER`。

## 6. 安全协议规划

Integration-1 dry-run 必须继续沿用 Integration-0 已冻结的安全边界：

- canonical header：`X-NQ-DH-*`。
- timestamp：RFC3339 / ISO-8601 UTC `Z`；拒绝 epoch seconds、epoch milliseconds、数字时区偏移。
- nonce replay：`source + nonce + requestId` 或等价组合必须可防重放；真实 runtime 前必须持久化或集中化。
- HMAC value-based `signatureMaterial`；header name 不入签。
- `requestId / traceId / tenantId` binding：header/body/认证主体必须一致，header 不覆盖权威来源。
- source allowlist：dry-run source 仅允许 `NQ_DRYRUN` 或后续 review 接受的等价常量。
- payload size gate：默认 64 KiB，超限拒绝，不截断接受。
- rate limit：按 tenant + source + route 或等价维度限流，超限 fail-closed。
- fail-closed status code：schema/字段 400，认证 401，source/tenant 403，replay/idempotency 409，payload 413，gate disabled 423，rate limit 429。
- forbidden fields / forbidden capabilities：禁止 credential、order execution、NQ DB、NQ mutation、LIVE、provider secret。
- no credential logging：禁止 token / secret / passphrase / signature raw material / raw payload 落日志。
- no raw secret persistence：禁止 raw secret、raw provider response、raw prompt/full context 持久化。
- no trading side-effect：DH 输出只能被 NQ 记录为 dry-run evidence，不能进入 order / risk mutation / paper run start 路径。

## 7. 后续批次规划

| 批次 | 名称 | 目标 | 允许 | 禁止 | 关闭条件 |
| --- | --- | --- | --- | --- | --- |
| I1-P0 | NQ / DH factsource rebase | 同步两仓当前事实源，移除旧 GateK Integration-1 作为当前主线 | docs/current 同步、旧口径标记 `SUPERSEDED / REBASE_REQUIRED` | runtime、API、代码、测试 | 两仓状态均指向 GateN rebase dry-run line |
| I1-P1 | NQ dry-run request contract plan | 规划 NQ 如何构造 `DecisionRequest` | docs / contract / test-support plan | runtime dispatcher、真实 HTTP、NQ client | request 字段、禁止字段、schema 扩展策略明确 |
| I1-P2 | DH dry-run receive boundary plan | 规划 DH 是否复用未来入口或需要新入口 | docs-only API boundary plan | 新增 API / Controller / migration | API 变更是否需要单独 review 明确 |
| I1-P3 | NQ mock dispatcher / no-outbound guard plan | 规划 test-support dispatcher 与 no-outbound guard | mock / fixture / test-support plan | real outbound、真实 DH 调用、执行输出 | NQ 只记录不执行、no real outbound guard 明确 |
| I1-P4 | DH mock dry-run validation plan | 复用 DH K6 mock NQ dry-run tests 并补 GateN 语境 | mock-only validation plan | runtime、真实 NQ、真实 HTTP | GateN context、replay、golden regression 计划明确 |
| I1-P5 | joint dry-run implementation gate | 定义 P0-P4 关闭后的 implementation 入场门槛 | gate review / acceptance criteria | LIVE、real provider、下单、runtime integration 默认放行 | P0-P4 全部关闭且 implementation 仍单独授权 |

P0-P4 关闭前，`ALLOW_INTEGRATION1_DRYRUN_IMPLEMENTATION` 必须保持 `NO`。P5 即使允许 implementation，也仍不允许 LIVE、真实 provider、真实交易、NQ DB、AI / LangGraph runtime 或 DH 输出驱动交易。

## 8. 测试规划矩阵

后续 dry-run implementation gate 至少需要规划或实现以下测试；本轮不新增测试代码：

| 测试类型 | 目的 | 初始建议 |
| --- | --- | --- |
| NQ contract tests | NQ request shape、forbidden fields、schemaVersion、dryRun/source 语义 | I1-P1 |
| DH contract tests | DH receive/response shape、action vocabulary、forbiddenActions | I1-P2/P4 |
| joint mock fixture tests | 双仓共享 fixture，验证 request/response 可互认 | I1-P3/P4 |
| no-outbound tests | 确认 mock dispatcher 不触达真实 DH/NQ/交易所 host | I1-P3 |
| no-live-trade tests | 确认无 order / cancel / paper start / LIVE side effect | I1-P3/P4 |
| forbidden fields tests | 拒绝 apiKey / secret / accountId / orderId / side / price / quantity | I1-P1/P4 |
| HMAC / timestamp / nonce tests | canonical header、UTC `Z`、replay、signature material | I1-P1/P2 |
| tenant mismatch tests | header/body/auth tenant binding 不一致 fail-closed | I1-P1/P2 |
| replay reference tests | DH audit / replay reference 可追踪且 tenant-isolated | I1-P4 |
| idempotency tests | 同 requestId 同 payload 幂等，换 payload 冲突 | I1-P1/P3 |
| payload size tests | 64 KiB gate，不截断接受 | I1-P1/P2 |
| rate limit tests | tenant/source/route 维度限流，超限 429 | I1-P2/P3 |
| audit shape tests | receive/reject/record/replay 事件脱敏、可追踪 | I1-P4 |
| golden case regression tests | dry-run fixture 纳入 deterministic golden baseline | I1-P4 |

## 9. 验收标准

本计划关闭必须满足：

```text
NQ 当前阶段明确为 GateN 后的 current fact rebase input，且 GateO 当前事实不被覆盖。
DH 当前阶段明确为 GateK Decision Pipeline MVP CLOSED / ACCEPTED。
旧 NQ-DH-GATEK-INTEGRATION1-PLAN-PACK 标记 SUPERSEDED / REBASE_REQUIRED。
Integration-1 第一阶段仅为 dry-run planning。
合同字段、禁止字段、安全协议、批次、测试矩阵已定义。
Readiness decision 明确阻断 implementation / runtime / real HTTP / real provider / AI / LangGraph / LIVE。
验证命令真实执行并写入 TESTING / WORKLOG；失败时记录真实原因。
```

## 10. Readiness decision

```text
ALLOW_INTEGRATION1_DRYRUN_PLAN_CLOSE: YES
ALLOW_NQ_DH_I1_P0_FACTSOURCE_REBASE: YES
ALLOW_INTEGRATION1_DRYRUN_IMPLEMENTATION: NO
ALLOW_INTEGRATION_1_RUNTIME: NO
ALLOW_REAL_HTTP: NO
ALLOW_REAL_PROVIDER: NO
ALLOW_AGENT_PHASE: NO
ALLOW_LANGGRAPH_RUNTIME: NO
ALLOW_LIVE: NO
```

## 11. 下一步

如果本计划验证通过，下一步只允许进入：

```text
NQ-DH-I1-P0-FACTSOURCE-REBASE / NOT STARTED
```

P0 仍是 docs/factsource rebase，不是 runtime implementation。若后续发现 NQ GateN / GateO 与 DH GateK facts 仍有 current-source 冲突，则改走：

```text
NQ-DH-INTEGRATION1-DRYRUN-PLAN-REBASEN-FIX
```

