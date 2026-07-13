# NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW — Attempt 01

Date：2026-07-13

Task classification：`PRE_IMPLEMENTATION_SECURITY_REVIEW / RISK_BOUNDARY_REVIEW / NO_SIDE_EFFECT_CONTRACT / VENUE_RULE_REVIEW / ORDER_PREVIEW_BASELINE / TASK_EVIDENCE`

Scope：NQ-only、docs/evidence only；未修改 Java、API、migration、frontend、research、scripts、deploy 或 CI。

Final decision：`BLOCKED / VENUE_RULE_FACTS_UNAVAILABLE`。

## 1. Preflight and GateW-2 acceptance

| Fact | Result |
| --- | --- |
| Branch / worktree | `dev`；worktree clean；staged empty |
| Starting HEAD | `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553` |
| `origin/dev` | `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553` |
| Exact-HEAD CI | `NQ CI Baseline` run `29230512781`，`completed / success`，`headSha=6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553` |
| GateW-2 implementation commit | `6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553`，已包含在 HEAD |
| GateW-2 conformance review | `PASS / SECURITY_CONFORMANCE_ACCEPTED / READY_TO_COMMIT`；P0=0、P1=0 |
| GateW-2 real smoke | `REAL_SMOKE=NOT_RUN`；不得用 CI/mock 解释为真实联通或远端权限已验证 |

Pre-edit governance commands：

- `scripts/docs/test-governance-workflow-lifecycle.ps1`：`PASS / GOVERNANCE_LIFECYCLE_REGRESSION`、`PASS / TASK_EVIDENCE_POLICY_VALID`。
- `scripts/docs/test-current-authority-next-action.ps1`：`PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION`。
- `scripts/docs/check-current-authority.ps1`：`PASS / CURRENT_AUTHORITY_CONSISTENT`；该结果只证明旧 authority 自洽，不证明其已反映 GateW-2 commit/CI。

## 2. Authority before and normalization

Authority before：

```text
accepted_batch=GateW-1
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateW-2
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-2-COMMIT-AND-PUSH
```

真实 Git/CI 已证明 GateW-2 commit 是 current exact HEAD 且 CI green，因此本轮按授权做最小 normalization：`accepted_batch=GateW-2 / ACCEPTED|CI_GREEN`。该 normalization 不表示 GateW-2 real smoke、远端 permission、LIVE 或交易授权。

## 3. Files and code paths inspected

Required governance/docs：`AGENTS.md`、`CLAUDE.md`、root `README.md`、`docs/current/GATEW_PLAN.md`、`STATUS.md`、`API.md`、`DB_SCHEMA.md`、`TESTING.md`、`WORKLOG.md`、`GOVERNANCE_WORKFLOW.md`、`ROADMAP.md`、`README.md`、`FACT_SOURCE_INDEX.md` 与 GateW evidence index。

重点代码与调用链：

- `backend/nq-api/.../TradingVerificationController.java`
- `backend/nq-core/.../OrderCommandService.java`
- `backend/nq-core/.../OrderCommandWriteService.java`
- `backend/nq-core/.../OrderLifecycleService.java`
- `backend/nq-adapter-api/.../TradingAdapter.java`
- `backend/nq-adapter-okx/.../OkxExchangeAdapter.java`
- `backend/nq-adapter-okx/.../OkxInstrumentsCache.java`
- `backend/nq-adapter-okx/.../OkxInstrument.java`
- `backend/nq-core/.../InstrumentCatalogItem.java`
- `backend/nq-infra/.../JdbcInstrumentCatalogRepository.java`
- `backend/nq-scheduler/.../AdapterInstrumentCatalogSyncService.java`
- `backend/nq-risk/**` 中 `PreTradeRiskService`、`DuplicateRequestRule`、`RateLimitRule`、`KillSwitchRiskRule` 与 settings/rules。
- `backend/nq-core/**`、`backend/nq-infra/**` 中 order/event/audit/risk-event/trade/ledger/account/position writer。
- `backend/nq-backtest/**` 中 `FeeModel`。
- `backend/nq-core/**` 与 `backend/nq-api/**` 中 Shadow preview、trading preflight 与 Spring composition 候选。

仓库不存在独立 `backend/nq-trading` module；订单编排和写侧实际分布在 `nq-core`、`nq-api`、`nq-infra`，相关 scheduler/reconciliation 位于 `nq-scheduler`。审计使用递归 `target` 排除，未将构建产物作为源码事实。

## 4. Existing order write chain and mutating side effects

现有真实写链为：

```text
POST /api/trading/orders
→ TradingVerificationController
→ OrderCommandService.placeOrder
→ OrderCommandWriteService.preparePlaceOrder
→ TradingVenueGateway.placeOrder
→ TradingAdapter.placeOrder / OkxExchangeAdapter
```

该链在调用 venue 前已生成 `ord-<UUID>` 候选 ID，并可写 command event、order、audit、risk event 与状态；成功后还可写 external order ID。cancel、lifecycle、reconciliation、trade ledger posting 继续写 order/trade/event/ledger/account snapshot/position projection。Shadow Run 虽存在 `OrderIntentPreview`，但 runner 会持久化 run、facts、snapshot 与 events，因此不满足本任务 no-side-effect contract。

Mutating side-effect inventory：

| Component / port | Side effect | GateW-3 decision |
| --- | --- | --- |
| `OrderCommandService` / `OrderCommandWriteService` | 生成候选 order ID；写 order/event/audit/risk state；调用 venue | 永久禁止依赖或调用 |
| `TradingVenueGateway` / `TradingAdapter` | `placeOrder`、`cancelOrder` 与 private venue mutation | 永久禁止依赖或调用 |
| Order/trade repositories | order/trade/fill/status persistence | 禁止 |
| `EventStoreAppender` / audit / risk-event writer | business event、audit、risk event persistence | 禁止 |
| `TradeLedgerPostingService` | ledger/account snapshot/position projection | 禁止 |
| Reconciliation/scheduler/runner | 后台调用与本地状态写入 | 禁止新增或复用 |
| Credential executor / GateW-2 transport | decrypt、private request | 禁止依赖或调用 |

结论：GateW-3 必须是独立本地计算链；不得向真实写链传 `dryRun=true`，不得通过 mock mutating port 假装物理隔离。

## 5. Existing preview, risk, venue-rule and fee facts

### 5.1 Preview/dry-run capability

- 源码中没有 `OrderPreview`、`DryRunOrderPreview` 或 `OrderPreviewRequest` 等语义等价实现。
- `GET /api/strategies/shadow-live/preview` 是策略/Shadow readiness 诊断，不是 venue-aware order intent preview。
- `GET /api/trading/preflight/readiness` 是 GET-only aggregate，可作为 no-side-effect 返回风格参考，但其账户选择允许默认/首个账户 fallback，不符合 GateW-3 “账户必须显式选择”。
- 现有 Shadow `OrderIntentPreview` 缺少完整 venue rule，且所在 runner 有持久化副作用，不可复用。

因此不存在 `EQUIVALENT_IMPLEMENTATION_EXISTS`；第一实现切片仍应是 internal application service + tests，不新增 Controller。

### 5.2 Risk preflight

`PreTradeRiskService` 同时包含纯计算与有状态规则。`DuplicateRequestRule` 会写 recent-request map，`RateLimitRule` 会写 request deque；直接调用会消费预览状态/额度，违反 no-side-effect。`OrderCommandWriteService` 还会持久化 risk event。

GateW-3 只能提取或新建只读 preview port，并显式选择纯规则；不得调用有状态 rule、不得写 risk event、不得消费额度、不得预留资金、不得创建 approval。kill switch 只允许读取现有状态。账户 scope、symbol allowlist、order type、quantity/notional、precision、max single-order notional、可验证的 exposure/loss/drawdown、data freshness、permission readiness 与 LIVE-disabled 都必须保守传播；关键事实缺失返回 `BLOCKED` 或 `UNKNOWN`。

### 5.3 Venue/instrument rule source

发现的真实来源只有：

1. `OkxInstrumentsCache`：请求 OKX public instruments 后缓存 `tickSz`、`lotSz`、`minSz`、`state`；`lastRefreshAt` 只在 cache 内部，未进入 `OkxInstrument` snapshot。请求时网络 refresh 不允许用于 GateW-3。
2. `OkxBootstrapFallbackFactory`：存在少量 hard-coded bootstrap fallback，但同样只有 `tickSz`、`lotSz`、`minSz`，不能冒充完整且新鲜的 OKX venue rule。
3. 本地 `instrument_catalog` / `InstrumentCatalogItem`：保存 exchange/internal symbol、base/quote、status、tick size、step size、minimum quantity、source、syncedAt；可作为未来本地 resolver 的事实源。

源码精确搜索未发现 `maxMktSz`、`maxLmtSz`、`maxQuantity`、`minNotional`、`minimumNotional` 或等价完整 OKX Spot rule。当前本地模型缺少至少：

- maximum quantity；
- minimum notional；
- 可由 preview 返回的 venue-rule version；
- 明确 `observedAt/freshUntil` freshness policy；
- 能证明最多 3 个 GateW symbol 的完整、同版本 rule snapshot。

这正命中任务 hard gate：`BLOCKED / VENUE_RULE_FACTS_UNAVAILABLE`。不得用“常见 OKX 规则”、bootstrap fallback、risk defaults 或请求时 public API 补齐。

### 5.4 Fee model

现有显式计算只发现 backtest `FeeModel`，其 rate 由调用者提供；部分 backtest/paper 默认可补零，但该行为不适用于 GateW-3。仓库未发现可证明为当前 OKX Spot 账户无关 fee schedule 的本地事实，也不得读取真实账户 fee tier 或 private endpoint。

冻结决策：`feeEstimateStatus=UNKNOWN`，fee amount 不补零；仍可返回其他诊断，但 preview 不得标记完整通过。fee estimate 必须标明 `ESTIMATE_ONLY`、非结算、非交易所承诺、非最终成交费用。

## 6. Frozen implementation contract

### 6.1 Physical call chain

```text
OrderPreviewRequest
→ OrderIntentNormalizer
→ LocalVenueRuleResolver
→ FeeEstimateCalculator
→ RiskPreflightPreview
→ DryRunOrderPreviewResult
```

所有接口必须位于不依赖 `TradingAdapter`、`TradingVenueGateway`、order command/write/lifecycle、repository writer、private OKX transport 或 credential executor 的模块边界。不得新增 scheduler、runner、startup hook、background refresh 或 network client。

### 6.2 Input contract

候选字段：`ownerId`、`exchangeAccountId`、固定 `exchange=OKX`、固定 `marketType=SPOT`、`symbol`、`side`、`orderType`、`quantity` XOR `quoteAmount`、nullable `limitPrice`、nullable `strategyVersionId`、`traceId`、`requestId`。

约束：

- owner/account 必须显式提供并做 owner scope；禁止默认账户。
- symbol 只能来自 GateW 最多 3 个 allowlist，并在本地规则中存在。
- 仅 `BUY / SELL`；未知值 fail-closed。
- 首个切片只允许 `LIMIT`。在没有可靠本地 reference price/slippage contract 前，`MARKET` 后置。
- 禁止 margin/futures/options/leverage、transfer/withdraw、raw endpoint、credential 与 provider DTO。
- `quantity`、`quoteAmount`、`limitPrice` 使用 `BigDecimal`；禁止 float/double。

### 6.3 Result contract

```text
previewStatus=READY_FOR_REVIEW|BLOCKED|PARTIAL|UNKNOWN
normalizedOrderIntent
venueRuleSnapshot
quantityValidation
priceValidation
notionalEstimate
feeEstimate
riskDecision
blockers
warnings
nextSteps
generatedAt
traceId

diagnosticOnly=true
noSideEffect=true
notTradingAuthorization=true
liveDisabled=true
orderSubmitted=false
realOrderId=null
```

`READY_FOR_REVIEW` 只表示本地结果可供人工查看；禁止输出或映射为 `READY_TO_TRADE`、`TRADE_APPROVED`、`LIVE_READY`、`AUTHORIZED_FOR_TRADING`。不得生成任何类似真实 venue order ID 的非空值。

### 6.4 Venue freshness and rounding policy

- Rule snapshot 必须包含 symbol/base/quote、tick/step、min/max quantity、min notional、price/quantity precision、instrument status、source、version、`observedAt`、`freshUntil`。
- source 只允许本地 DB 中已验证的 public metadata 或 immutable fixture；默认请求路径禁止访问 OKX public/private API。
- 缺失、冲突、disabled、无法识别 source/version、`generatedAt > freshUntil` 均 fail-closed。
- quantity/price/notional 全程 `BigDecimal`。候选 quantity 只能使用 `RoundingMode.DOWN` 计算展示值，绝不扩大订单；price 必须精确对齐 tick。任何输入需要调整时必须返回 requested/normalized 差异与 blocker/warning，不能静默接受为 ready。归一化后为零、低于 min quantity/min notional 或高于 max quantity/单笔上限均 blocked。

### 6.5 Risk and permission boundary

- risk preview 只返回诊断；不写任何 state/event，不消费额度，不预留资金，不创建 approval。
- 有状态 duplicate/rate-limit rule 不可直接调用；如需展示，必须使用只读 snapshot 或返回 UNKNOWN。
- GateW-2 implementation/CI 已接受，但 `REAL_SMOKE=NOT_RUN`，因此远端 permission readiness 只能是 `UNKNOWN`/blocker，不能写成 verified。
- LIVE 始终 false；任何 live enabled/unknown 都 fail-closed。

## 7. API, persistence, credential and network decisions

```text
API=NO_NEW_CONTROLLER
PERSISTENCE=NONE
MIGRATION=NONE
CREDENTIAL_ACCESS=NONE
PRIVATE_ENDPOINT=NONE
REAL_NETWORK=NONE
API_KEY=NOT_REQUIRED
REAL_SMOKE=NOT_APPLICABLE
```

第一切片仅 internal application service + tests。当前没有语义等价 order preview endpoint，因此不扩展 API。preview 不做幂等记录；相同输入与相同本地 rule/risk snapshot 必须产生确定性结果。后续若另行批准 API，必须独立冻结 method/path、认证、owner scope、rate/page/batch limits、error taxonomy 与 zero-side-effect tests。

## 8. Required implementation tests

实现至少覆盖：

1. 未调用任何 mutating port。
2. 不创建订单或订单 ID。
3. 不写 order/trade/position/ledger/account/event/audit/risk event。
4. 相同输入和相同 snapshots 产生确定性 preview。
5. quantity/price/notional 使用 `BigDecimal`。
6. rounding 明确且不静默扩大订单。
7. venue rule 缺失、过期、冲突、disabled fail-closed。
8. fee 缺失返回 UNKNOWN，不补零。
9. risk blocker 保守传播；有状态 rule zero-call。
10. unknown permission 不通过。
11. LIVE 始终 false。
12. `orderSubmitted=false`、`realOrderId=null`。
13. futures/margin/leverage/options 被拒绝。
14. transfer/withdraw 被拒绝。
15. credential executor/decrypt zero-call。
16. OKX public/private transport zero-call。
17. 测试不依赖网络。
18. 不新增 scheduler/runner/startup hook。
19. 静态扫描 preview production path 无 `placeOrder`/`cancelOrder`。
20. targeted tests 与全量 `mvn -f backend/pom.xml test` 通过。

## 9. Findings

### P0

- 无。当前轮未改代码、未调用真实服务、未触达 credential 或交易写侧。

### P1

- **P1-1 / implementation blocker**：当前本地 OKX Spot instrument facts 缺少 maximum quantity、minimum notional、rule version/freshness contract，无法构造任务要求的完整、可追踪 venue rule snapshot。结论固定为 `BLOCKED / VENUE_RULE_FACTS_UNAVAILABLE`。

### P2

- 仓库无可靠本地 OKX Spot fee schedule；GateW-3 只能返回 `feeEstimateStatus=UNKNOWN`。
- 现有 risk pipeline 混合纯规则与会写内存额度/去重状态的规则；实施必须建立独立 pure preview port，不能直接复用整个 `PreTradeRiskService`。
- GateW-2 real smoke 未执行，permission readiness 仍为 UNKNOWN。

### P3

- root `README.md` 与 `CLAUDE.md` 存在低 authority 的历史阶段摘要；本任务禁止修改它们。`docs/current/STATUS.md` 继续是唯一 current authority。

## 10. Authority after, rollback and next action

Authority after：

```text
accepted_batch=GateW-2
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553
accepted_batch_acceptance_head=6543e0965fe1f1b8c31b87ea75b9d20bc9d9d553
accepted_batch_ci_run=29230512781

active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-3
work_batch_status=BLOCKED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-3-BLOCKED
```

Unblock criteria：先以独立、明确授权的任务建立或证明完整本地 OKX Spot venue rule facts，包括 max quantity、min notional、source/version/observedAt/freshUntil，并通过 schema/migration/security review（若确实需要 migration）。在该事实存在前，不得开始 `NQ-GATEW-3-IMPLEMENTATION`，不得初始化 GateW-4。

Rollback：仅回退本 attempt 新文件和同批 current-doc additions/authority normalization；不使用 `git reset --hard`，不修改 GateW-2 commit、tag、业务代码或历史 evidence。

Review decision：

```text
NQ-GATEW-3-DRY-RUN-ORDER-PREVIEW-SECURITY-RISK-REVIEW：
BLOCKED / VENUE_RULE_FACTS_UNAVAILABLE
```

Next action：`NQ-GATEW-3-BLOCKED`。成功状态 `PASS / SECURITY_RISK_REVIEW_ACCEPTED / IMPLEMENTATION_AUTHORIZED` 未达到。

## 11. Post-edit validation record

| Validation | Result |
| --- | --- |
| `git diff --check` | PASS；仅有 Git 的 LF→CRLF working-copy 提示，无 whitespace error |
| Governance lifecycle regression | `PASS / GOVERNANCE_LIFECYCLE_REGRESSION`；task evidence policy valid |
| Next-action regression | `PASS / CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION`；`BLOCKED` canonical action 受支持 |
| Current authority checker | `PASS / CURRENT_AUTHORITY_CONSISTENT`；errors=0 |
| Current doc links | `PASS / DOC_LINKS_VALID`；67 checked、0 errors、1 个既有 GateJ historical warning |
| Maven/frontend/Python | NOT RUN；docs/evidence-only，代码修改被禁止 |
| Staged state | empty；未执行 `git add` |
| Forbidden scope | backend/frontend/research/scripts/deploy/`.github`/migration/`docs/gates`/`docs/archive`/`.agents`/root `README.md` diff 均为 0 |
| Sensitive filename scan | 0 matches；未读取或修改 credential material |

最终 status 只有 8 个 allowed current-doc modifications 与本 attempt 新文件；未 stage、commit 或 push。Git 对 tracked working-copy 输出 LF→CRLF 提示，该提示不是 whitespace error，未做无关行尾格式化。
