# GateY-6 Explicit Micro-Live Authorization Preflight and Work Order

> 状态：`REVIEW ACCEPTED / READY TO COMMIT`（审查已接受 / 可进入提交前复核）
> 本文是工作单与准入前置，不是交易授权。`FIRST_REAL_ORDER=NOT_AUTHORIZED`、`EXPLICIT_MICRO_LIVE_AUTHORIZATION=NOT_GRANTED`、`LIVE=DISABLED`、`kill_switch=ENGAGED`。

## 1. Objective

在不读取 credential、不访问 OKX、不启动 worker、不发送订单的前提下，重建 GateY-6 第一笔真实订单的完整 hard-gate matrix，并把系统能力与具体 pilot 绑定严格分离。本文冻结后续最小工程范围、exact pilot scope schema、授权语义、真实对账闭环、错误分类、kill contract 与 120h controlled micro-live soak 验收合同。

## 2. Non-goals

- 不实现、启用或声明 real provider/private trading 已完成。
- 不创建真实 credential，不读取 secret，不验证远端 account permission 或 IP allowlist。
- 不调用 OKX，不执行 `PLACE`、`QUERY`、`CANCEL`、fill/account read 或任何资金 mutation。
- 不选择真实 symbol、金额、账户、operator identity、时间窗口或 approval expiry。
- 不创建 V40，不改 V39，不改 backend/frontend/research/scripts/deploy/CI。
- 不 disengage kill switch，不开启 LIVE，不启动 120h soak。
- 不把 GateY-1～5 的 local/fake/synthetic evidence 扩大为具体 pilot readiness。

## 3. Current accepted baseline

| Source | Exact binding | 本工作单可复用能力 | 资格限制 |
| --- | --- | --- | --- |
| GateX-5 / GateX-FREEZE | GateX freeze commit `299ab30bd2e243314be2dc609cb244cd5388027b`；release/admission implementation `3336bd8153845d5368a0d65a9c72d3566dc9bd35`；acceptance head `a383be750f51d063d429bc25fad80e60dffb7014`；CI `31512467501` | release/admission materialization 与 immutable digest capability | 不绑定 GateY-6 pilot release，不授权真实订单 |
| GateY-2 | implementation/acceptance head `19ac2d1cdc7a1982f97fb0e1b0e62c081d003018`；CI `31608725854` | V39 `LiveSession`、`OperatorApproval`、risk/release/account/credential/symbol/window/scope-hash durable facts | local/disposable schema evidence；不是 production migration 或 pilot materialization |
| GateY-3 | implementation/acceptance head `1f2ad2324166872a567a0420b71a8b4a5b68f7f1`；CI `31622259352` | durable intent/receipt、intentId idempotency、`SEND_STARTED`、query-first unknown recovery、NO BLIND RETRY | fake provider contract；不证明 OKX mutation/query/cancel/fill semantics |
| GateY-4 | canonical implementation `44ac9b3c014bcd7a46499c4180053742e64c7709`；acceptance head `b3a6b1fd550d8ccb5132c7b16942a4b11b67f78e`；CI `31679311259` | scoped credential reference/JIT、private read-only boundary、kill propagation、supported Linux stable handle | credential 仅 `PRIVATE_READONLY_DIAGNOSTIC`；remote permission/IP 未验证；private mutation 被拒绝 |
| GateY-5 | implementation `8d594f1a0000678e4817f3ec80de19ac975da992`；acceptance head `88f6f7f25a81f55fe17984df335546ad2033c61f`；CI `31761584826` | isolated fake worker、replay/recovery、rollback/restore/incident drill、operator visibility、lock-window evidence | `PRODUCTION_LOCK_WINDOW_NOT_MEASURED=CLOSED_FOR_REVIEWED_SYNTHETIC_DISPOSABLE_GATEY_SCALE`；不等于 production migration/SLA/real venue readiness |

## 4. Status model and evidence rule

每个 gate 都包含 `capabilityStatus`、`pilotBindingStatus`、`finalGateStatus`。状态只允许 `PASS`、`NOT_MET`、`NOT_VERIFIABLE`。只有 capability 与 pilot binding 同时为 `PASS` 时，final 才能为 `PASS`；任一能力缺失时 final 为 `NOT_MET`，能力存在但 exact pilot 外部事实尚不可验证时 final 为 `NOT_VERIFIABLE`。Manifest reader必须fail closed：missing gate/status/evidence、unknown status/gate、invalid status combination或count mismatch均不得产生PASS；unknown事实归一为`NOT_VERIFIABLE`，结构/枚举/逻辑错误直接拒绝manifest并保持`FIRST_REAL_ORDER=NOT_AUTHORIZED`。

证据 ID 的 exact binding：

| Evidence ID | Source gate/batch | Evidence file | Implementation commit | Acceptance head | CI run | Test/drill identity | Scope qualification |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `E-X-ADMISSION` | GateX-5 / GateX-FREEZE | `docs/gates/gate-x/GATEX_BATCH_0_5_EVIDENCE_MATRIX.md` | `3336bd8153845d5368a0d65a9c72d3566dc9bd35` | `a383be750f51d063d429bc25fad80e60dffb7014` | `31512467501` | GateX-5 hard gates `18/18` 与 admission materialization closure | capability-only；无 GateY-6 release binding |
| `E-Y2-FACT` | GateY-2 | `docs/current/evidence/gate-y/NQ-GATEY-2-LIVE-SESSION-FACT-MODEL-MIGRATION-SECURITY-REVIEW.attempt-01.md` | `19ac2d1cdc7a1982f97fb0e1b0e62c081d003018` | 同 implementation | `31608725854` | focused PostgreSQL integration PASS；full disposable backend `270/0` | local/disposable V39 facts；无 production/pilot materialization |
| `E-Y3-EXECUTION` | GateY-3 | `docs/current/evidence/gate-y/NQ-GATEY-3-EXECUTION-INTENT-RECEIPT-FAKE-EXCHANGE-SECURITY-REVIEW.attempt-01.md` | `1f2ad2324166872a567a0420b71a8b4a5b68f7f1` | 同 implementation | `31622259352` | `ExecutionIntentRuntimeTest 10/0/0/0`；PostgreSQL concurrency；fake isolation | fake-only；不证明 OKX mutation/recovery |
| `E-Y4-SECURITY` | GateY-4 | `docs/current/evidence/gate-y/NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-SECURITY-REVIEW.attempt-01.md` | `44ac9b3c014bcd7a46499c4180053742e64c7709` | `b3a6b1fd550d8ccb5132c7b16942a4b11b67f78e` | `31679311259` | supported Linux stable-handle `14/0/0/0`；kill fail-close；private read-only tests | read-only only；real smoke `NOT_RUN / API_KEY_REQUIRED`；remote permission/IP 未验证 |
| `E-Y5-OPS` | GateY-5 | `docs/current/evidence/gate-y/NQ-GATEY-5-ISOLATED-WORKER-DRYRUN-ROLLBACK-RESTORE-LOCK-WINDOW-SECURITY-OPERATIONS-REVIEW.attempt-01.md` | `8d594f1a0000678e4817f3ec80de19ac975da992` | `88f6f7f25a81f55fe17984df335546ad2033c61f` | `31761584826` | full backend GREEN；frontend build；E2E `86 passed / 1 canonical skipped`；lock/restore/replay/incident drills | fake/synthetic/disposable only；lock closure 不是 production SLA |
| `E-Y5-ACCEPTANCE` | GateY-5 post-CI acceptance | `docs/current/evidence/gate-y/NQ-GATEY-5-POST-CI-ACCEPTANCE-AND-GATEY-6-INITIALIZATION.attempt-01.md` | `8d594f1a0000678e4817f3ec80de19ac975da992` | `88f6f7f25a81f55fe17984df335546ad2033c61f` | `31761584826` | exact-head CI success；failed CI `31727172181` preserved | GateY-6 initialization only；FIRST_REAL_ORDER 未授权 |
| `E-CURRENT-OKX-AUDIT` | GateY-6 preflight | `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotEndpointGuard.java` | `8a3b2981668b53b492a9a46a6b4b381f7f656782` | `NOT_APPLICABLE` | `31764829976` | exact-head baseline CI；current-code inspection of adapter/guard/transport/readiness | legacy code fact；不是 GateY-6 real-provider acceptance |
| `E-CURRENT-RBAC-AUDIT` | GateY-6 preflight | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/application/LiveSessionControlService.java` | `8a3b2981668b53b492a9a46a6b4b381f7f656782` | `NOT_APPLICABLE` | `31764829976` | exact-head baseline CI；creator/approver inequality inspection | capability present；具体双主体未选择 |

## 5. FIRST_REAL_ORDER hard-gate matrix

| ID | Hard gate | Type | Capability | Pilot binding | Final | Evidence | Blocker / next required action |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `G01` | release/admission evidence | PLAN | PASS | NOT_MET | NOT_MET | `E-X-ADMISSION` | materialize exact pilot release/admission and bind immutable digests |
| `G02` | exact strategy release digest | PLAN | PASS | NOT_MET | NOT_MET | `E-X-ADMISSION`,`E-Y2-FACT` | select release ID/digest/artifact digest and freeze scope hash |
| `G03` | immutable risk-limit-set | PLAN | PASS | NOT_MET | NOT_MET | `E-Y2-FACT` | approve exact risk-limit-set ID/digest and immutable thresholds |
| `G04` | LiveSession + OperatorApproval | PLAN | PASS | NOT_MET | NOT_MET | `E-Y2-FACT` | create exact pilot facts only after prior security batches are accepted |
| `G05` | ExecutionIntent/Receipt traceability | PLAN | PASS | NOT_MET | NOT_MET | `E-Y3-EXECUTION` | port reviewed contract to real provider and bind exact pilot session |
| `G06` | intentId idempotency | PLAN | PASS | NOT_MET | NOT_MET | `E-Y3-EXECUTION` | prove stable client order ID mapping against OKX contract tests |
| `G07` | private endpoint allowlist | PLAN | NOT_MET | NOT_MET | NOT_MET | `E-CURRENT-OKX-AUDIT` | implement typed exact mutation allowlist; arbitrary path remains forbidden |
| `G08` | scoped pilot credential READ + TRADE permission / WITHDRAW forbidden | PLAN | NOT_MET | NOT_VERIFIABLE | NOT_MET | `E-Y4-SECURITY` | verify dedicated key has READ + TRADE, lacks WITHDRAW and is IP-bound；明确接受 OKX TRADE 固有 funding-transfer capability，但不把它解释为 NQ 资金移动授权 |
| `G09` | funds-movement containment and withdraw-deny proof | PLAN | PASS | NOT_VERIFIABLE | NOT_VERIFIABLE | `E-Y4-SECURITY` | NQ local containment capability存在；exact key 的 WITHDRAW absence、NQ runtime不可达证明及适用的account-level restriction仍需分层核验 |
| `G10` | IP allowlist | PLAN | PASS | NOT_VERIFIABLE | NOT_VERIFIABLE | `E-Y4-SECURITY` | exact pilot egress IP and remote key binding are unresolved |
| `G11` | kill propagation | PLAN | PASS | NOT_MET | NOT_MET | `E-Y4-SECURITY`,`E-Y5-OPS` | bind exact session/revision/deadline and independent disengage authorization |
| `G12` | order/fill/account/position reconciliation | PLAN | PASS | NOT_MET | NOT_MET | `E-Y3-EXECUTION`,`E-Y5-OPS` | implement and review real OKX query/fill/account convergence |
| `G13` | unknown-order recovery | PLAN | PASS | NOT_MET | NOT_MET | `E-Y3-EXECUTION`,`E-Y5-OPS` | real query-by-clientOrderId is not a GateY worker capability |
| `G14` | partial-fill/cancel/retry semantics | PLAN | PASS | NOT_MET | NOT_MET | `E-Y5-OPS` | prove OKX partial fill/cancel race semantics; retain no blind retry |
| `G15` | immutable release/rollback | PLAN | PASS | NOT_MET | NOT_MET | `E-Y5-OPS` | build/review exact real-worker artifact and bind pilot release |
| `G16` | backup/restore | PLAN | PASS | NOT_MET | NOT_MET | `E-Y5-OPS` | rehearse exact pilot storage/release facts without production mutation |
| `G17` | incident drill | PLAN | PASS | NOT_MET | NOT_MET | `E-Y5-OPS` | add real-provider contract scenarios before pilot materialization |
| `G18` | production-like lock-window closure | PLAN | PASS | NOT_VERIFIABLE | NOT_VERIFIABLE | `E-Y5-OPS` | only synthetic disposable GateY scale is closed; exact runtime/deployment binding absent |
| `G19` | filesystem stable-handle closure | PLAN | PASS | NOT_VERIFIABLE | NOT_VERIFIABLE | `E-Y4-SECURITY` | supported Linux capability exists; exact pilot host/release handle is unresolved |
| `G20` | exact operator explicit authorization | PLAN | PASS | NOT_MET | NOT_MET | `E-Y2-FACT`,`E-Y5-ACCEPTANCE` | exact scope absent; current request is not authorization |
| `G21` | real provider mutation path implemented/reviewed | HARD_GATE_GAP_CANDIDATE | NOT_MET | NOT_MET | NOT_MET | `E-CURRENT-OKX-AUDIT` | implement worker-safe OKX Spot LIMIT provider with fake/stub tests only |
| `G22` | private trading path implemented/reviewed | HARD_GATE_GAP_CANDIDATE | NOT_MET | NOT_MET | NOT_MET | `E-CURRENT-OKX-AUDIT` | current readiness and endpoint guard fail-close mutation |
| `G23` | instrument metadata freshness | HARD_GATE_GAP_CANDIDATE | NOT_MET | NOT_MET | NOT_MET | `E-CURRENT-OKX-AUDIT` | add bounded freshness/expiry contract and fail-close stale metadata |
| `G24` | instrument trading status / tick size / lot size / minimum order size / minimum order value | HARD_GATE_GAP_CANDIDATE | NOT_MET | NOT_MET | NOT_MET | `E-CURRENT-OKX-AUDIT` | trading status、tick/lot、minimum size/value与exact pilot facts均未绑定 |
| `G25` | available balance | HARD_GATE_GAP_CANDIDATE | PASS | NOT_VERIFIABLE | NOT_VERIFIABLE | `E-Y4-SECURITY` | read-only balance capability exists; exact pilot account/balance unavailable |
| `G26` | fee assumptions | HARD_GATE_GAP_CANDIDATE | NOT_MET | NOT_VERIFIABLE | NOT_MET | `E-CURRENT-OKX-AUDIT` | capability缺少accepted fee contract；actual pilot fee tier/source不可验证，需冻结freshness、estimate/observed语义与loss-cap treatment |
| `G27` | clock/time synchronization | HARD_GATE_GAP_CANDIDATE | NOT_MET | NOT_MET | NOT_MET | `E-CURRENT-OKX-AUDIT` | add clock-skew preflight, signed-request tolerance and stop threshold |
| `G28` | venue order-state translation | HARD_GATE_GAP_CANDIDATE | NOT_MET | NOT_MET | NOT_MET | `E-CURRENT-OKX-AUDIT` | legacy mapping is not reviewed GateY worker contract; cover all states/races |
| `G29` | real query-by-clientOrderId support | HARD_GATE_GAP_CANDIDATE | NOT_MET | NOT_MET | NOT_MET | `E-CURRENT-OKX-AUDIT` | typed single-order query and unknown-result recovery are not ported/reviewed |
| `G30` | creator / approver separation | HARD_GATE_GAP_CANDIDATE | PASS | NOT_MET | NOT_MET | `E-Y2-FACT`,`E-CURRENT-RBAC-AUDIT` | GateY-2 accepted evidence与current code均拒绝creator=self-approver；exact independent identities仍未绑定 |

Final counts：`PASS=0`、`NOT_MET=25`、`NOT_VERIFIABLE=5`。没有任何 gate 可授权真实订单。

## 6. Real-provider gap classification

| Component | Classification | Current fact | Minimal next contract |
| --- | --- | --- | --- |
| legacy `OkxExchangeAdapter` request/parse logic | REUSE | 存在 legacy PLACE/QUERY/CANCEL/fill code，但不是 GateY worker authority | 只复用已核对的 field/body parsing；不得直接接入 worker |
| endpoint guard | EXTEND | typed private read-only allowlist；`PRIVATE_MUTATING` 被拒绝 | 新增 GateY-specific typed exact allowlist、method/path/operation/order-type 联合校验 |
| signing path | REUSE | transport 有 signing 与 timeout | 复用 signing primitive；禁止 raw signature、header/body 进入日志/evidence |
| credential reference/JIT | EXTEND | 仅 read-only capability，短生命周期访问合同存在 | 新增 exact TRADE scope、expiry/revoke、single worker identity 与 zero-persistence invariant |
| request transport | EXTEND | 有 bounded timeout；缺少 GateY mutation response cap/rate policy | add response-size cap、bounded concurrency/rate limit、no automatic mutation retry |
| response normalization | EXTEND | legacy parsing 存在 | typed result + internal error codes；禁止向 UI 暴露 raw response |
| order-state translation | EXTEND | legacy mapping 未被 GateY review 接受 | 覆盖 live/partially_filled/filled/canceled/rejected/unknown 与 illegal transition |
| stable clientOrderId | REUSE + EXTEND | GateY-3 intentId/idempotency capability已接受 | 冻结 deterministic mapping、长度/字符约束与 collision tests |
| query order by clientOrderId | EXTEND | legacy endpoint 存在，current typed private read set缺少 single-order query | GateY typed query-first recovery；bounded result、exact account/symbol binding |
| cancel | EXTEND | legacy cancel 存在 | state-aware controlled cancel；禁止 blind cancel/retry |
| fill retrieval | EXTEND | private read capability有 bounded fill endpoint | bind session/account/symbol/time cursor and convergence checks |
| error taxonomy | NOT_IMPLEMENTED | 现有异常不足以形成 GateY real mutation决策表 | 实现下文 taxonomy、audit code 与 operator action |
| timeout policy | EXTEND | HTTP timeout存在 | mutation timeout一律进入 unknown-result/query-first，不自动 PLACE retry |
| rate limit/backpressure | NOT_IMPLEMENTED | 无 GateY mutation级令牌/并发合同 | 单 account bounded limiter、max inflight、Retry-After handling、stop threshold |
| clock skew | NOT_IMPLEMENTED | 无 GateY pilot时钟健康 gate | pre-start/continuous skew check；超限停止新 intent |
| raw URL/arbitrary endpoint/fallback | FORBIDDEN | 不属于 frozen scope | 无任何配置或 runtime escape hatch |
| MARKET/margin/leverage/futures/options/borrow/transfer/withdraw/funding/subaccount transfer | FORBIDDEN | 不属于 GateY-6 | compile-time/typed policy + runtime default deny + regression tests |

Candidate endpoint allowlist（仅供后续实现与审查，本轮未调用）：

| Operation | Method and path | Constraints |
| --- | --- | --- |
| PLACE_LIMIT | `POST /api/v5/trade/order` | exact pilot account；approved spot symbol；LIMIT-only；exact clientOrderId；one-at-a-time first-order mode |
| QUERY_ORDER | `GET /api/v5/trade/order` | exact `instId` + trusted `clOrdId`，或已持久化可信 `ordId` |
| CANCEL_ORDER | `POST /api/v5/trade/cancel-order` | only known open order；exact `instId` + `clOrdId/ordId`；no blind retry |
| READ_OPEN_ORDERS | `GET /api/v5/trade/orders-pending` | bounded exact account/symbol recovery and reconciliation |
| READ_ORDER_HISTORY | `GET /api/v5/trade/orders-history` | bounded exact account/symbol/time cursor |
| READ_FILLS | `GET /api/v5/trade/fills` | bounded exact account/symbol/order cursor |
| READ_BALANCE | `GET /api/v5/account/balance` | preflight/reconciliation only；no funding action |
| READ_ACCOUNT_CONFIG | `GET /api/v5/account/config` | permission/account mode/IP facts only；no mutation |

## 7. Credential readiness and future procedure

### 7.1 Permission safety model

本工作单固定以下三层安全模型；三层事实必须独立观察、独立记录，不得合并成一个 permission PASS：

| Layer | Required fact | Boundary |
| --- | --- | --- |
| Layer A — Remote API Key permission | future pilot key必须 `READ=REQUIRED`、`TRADE=REQUIRED`、`WITHDRAW=FORBIDDEN`、`IP_BINDING=REQUIRED` | OKX `TRADE` permission固有包含 funding-transfer capability；不得记录 `remoteTransferCapability=false`，也不得把该能力解释为已授权的 NQ funds-movement path |
| Layer B — Exchange/account policy | exact pilot account/sub-account若存在额外 transfer restriction，只能单独记录 `VERIFIED / NOT_VERIFIABLE / NOT_APPLICABLE` | 某一种 sub-account transfer被限制，不证明所有 funding transfer不可执行；account-level policy不得与API-key permission合并为PASS |
| Layer C — NQ application/runtime | `FUNDS_MOVEMENT=DENY`；TRANSFER/WITHDRAW operation均不暴露 | `SpotExecutionProviderPort`和已审查worker surface均无transfer/withdraw；typed endpoint policy default-deny funds movement；raw/arbitrary private path不可表达 |

因此：`remote permission capability != application authorization != FIRST_REAL_ORDER authorization != LIVE authorization`。`FIRST_REAL_ORDER=NOT_AUTHORIZED`继续成立。

已知残余风险固定为 `INHERENT_OKX_TRADE_PERMISSION_RESIDUAL`：未来 pilot key 因 `TRADE` 而保留OKX远端funding-transfer capability。该风险不属于NQ application containment失败，但必须显式保留，并继续依赖既有防御纵深：dedicated pilot credential、IP allowlist、WITHDRAW absent、single account scope、tiny capital cap、single-order cap、daily-loss cap、typed provider、NQ funds-movement unreachable、kill switch engaged与manual approval。以上均引用本工作单既有hard gates/scope/risk/kill/approval合同，不新增重复hard gate。

| Contract | Status | Required behavior |
| --- | --- | --- |
| credential reference | PASS / CAPABILITY ONLY | durable facts只保存 opaque reference；不得保存 credential material |
| credential scope | NOT_MET | dedicated GateY pilot key；OKX Spot；READ + TRADE required；WITHDRAW forbidden；IP binding required；显式接受TRADE固有funding-transfer capability |
| permission probe | NOT_VERIFIABLE | future read-only verification只读取 `GET /api/v5/account/config` 的sanitized `perm`/`ip`事实，证明READ + TRADE present、WITHDRAW absent与IP binding；适用的account-level transfer restriction单独记录，不执行transfer probe或任何mutation |
| expiry | NOT_MET | expiry早于/等于 pilot approval window，并有 fail-close clock check |
| rotation/revoke | NOT_MET | freeze rotation plan、revoke trigger、post-session revoke/disable verification |
| JIT access | PASS / CAPABILITY ONLY | 仅被 exact worker identity按 exact credential reference短时读取；不得进入 control plane |
| logging/redaction | PASS / CAPABILITY ONLY | 禁止 raw credential、签名串、private headers/body；只记录 reference/status/audit code |

Pilot status 固定为：`SCOPED_PILOT_CREDENTIAL=NOT_MET`、`READ_TRADE_PERMISSION=NOT_VERIFIABLE`、`WITHDRAW_FORBIDDEN=NOT_VERIFIABLE`、`IP_ALLOWLIST=NOT_VERIFIABLE`、`ACCOUNT_LEVEL_TRANSFER_RESTRICTION=NOT_VERIFIABLE`；NQ application contract继续为 `FUNDS_MOVEMENT=DENY`。这些状态不得误写为remote transfer capability absent。

未来 credential 必须通过 NQ credential management/JIT 管理，由独立 GateY pilot key 承载。禁止通过 chat、Markdown、Git/env file、shell history、CLI argument、test fixture 或 evidence file 传递 credential material。

## 8. Exact pilot scope schema

下列字段全部是 mandatory；当前不制造具体值：

| Field | Current value | Binding rule |
| --- | --- | --- |
| `pilotSessionId` | UNRESOLVED | immutable primary pilot identity |
| `venue` | `OKX_SPOT` | frozen single venue |
| `exchangeAccountReference` | UNRESOLVED | opaque reference；single account |
| `credentialReference` | UNRESOLVED | exact JIT reference |
| `ownerIdentity` | UNRESOLVED | exact operator owner |
| `creatorIdentity` | UNRESOLVED | must differ from approver |
| `approverIdentity` | UNRESOLVED | exact independent LIVE_APPROVER |
| `strategyReleaseId` | UNRESOLVED | immutable accepted release |
| `strategyReleaseDigest` | UNRESOLVED | included in scope hash |
| `artifactDigest` | UNRESOLVED | exact worker artifact |
| `riskLimitSetId` | UNRESOLVED | immutable risk set |
| `riskLimitSetDigest` | UNRESOLVED | included in scope hash |
| `symbolAllowlist` | UNRESOLVED | 1–2 approved spot symbols |
| `capitalCap` | UNRESOLVED | `EXPLICIT_AUTHORITY_REQUIRED` |
| `singleOrderNotionalCap` | UNRESOLVED | `EXPLICIT_AUTHORITY_REQUIRED`；far below capital cap |
| `symbolPositionCap` | UNRESOLVED | bounded per symbol |
| `dailyLossCap` | UNRESOLVED | `EXPLICIT_AUTHORITY_REQUIRED`；far below pilot capital |
| `maxOpenOrders` | UNRESOLVED | first real order contract forces at most one |
| `maxIntradayOrders` | UNRESOLVED | bounded; first-order acceptance permits one PLACE only |
| `executionWindowStart` | UNRESOLVED | immutable UTC instant |
| `executionWindowEnd` | UNRESOLVED | immutable UTC instant |
| `approvalExpiresAt` | UNRESOLVED | no later than execution window end |
| `pilotScopeHash` | UNRESOLVED | canonical digest of every field above and endpoint/order constraints |

### 8.1 Pilot prerequisite bindings

下列事实不增加hard-gate数量，但都是第一单前的mandatory exact authority。Immutable contract事实进入`pilotScopeHash`；fresh observation事实绑定session/preflight并有独立freshness gate。缺失、过期或无法验证时拒绝第一单：

| Binding | Current value | Scope / freshness effect |
| --- | --- | --- |
| `instrumentMetadataDigest` / `instrumentMetadataAsOf` | UNRESOLVED | 绑定trading status、tick、lot、minimum size/value；constraint变化使approval失效，stale observation拒绝preflight |
| `feeScheduleDigest` / `feeScheduleAsOf` / `feeTierEvidenceClass` | UNRESOLVED | 区分estimate与observed；fee policy/tier authority变化使approval失效，未知actual tier保持NOT_VERIFIABLE |
| `balanceSnapshotDigest` / `balanceSnapshotAsOf` | UNRESOLVED | fresh private read；不把金额写入evidence；过期/不足/unknown拒绝preflight |
| `clockSyncObservationDigest` / `clockSyncObservedAt` / `signedTimestampSource` / `maximumToleratedSkew` | UNRESOLVED | 阈值需明确authority；stale/超限/venue timestamp rejection拒绝preflight |
| `endpointPolicyVersion` / `endpointPolicyDigest` | UNRESOLVED | immutable scope input；任何method/path/operation/order-type policy变化使approval失效 |
| `providerContractIdentity` / `providerArtifactDigest` / `workerReleaseDigest` | UNRESOLVED | immutable scope input；provider或worker identity变化使approval失效 |

任一pilot scope字段、immutable prerequisite digest、symbol、cap、window、identity、provider identity或endpoint policy变化，已有approval立即`INVALID`并回到`APPROVAL_PENDING`。Fresh balance/clock/metadata observation过期或不收敛时无需伪造scope变化，但必须拒绝preflight并取得新observed fact；若新observation暴露instrument/fee/policy constraint变化，则必须生成新scope hash和新approval。

## 9. Symbol and capital/risk contracts

Symbol selection只冻结标准，不选择 symbol：spot only、high liquidity、instrument trading status可交易、metadata freshness可验证、tick size/lot size/minimum order size/minimum order value已知、available balance充足、spread bounded、query/fill reconciliation受支持。未来选定的1～2个symbol必须进入exact scope hash；metadata stale或status非tradable时`FIRST_REAL_ORDER=DENIED`。

当前 accepted authority没有具体 micro-live金额。因此 `capitalCap`、`singleOrderNotionalCap`、`symbolPositionCap`、`dailyLossCap`、`maxOpenOrders`、`maxIntradayOrders` 均保持 `UNRESOLVED / EXPLICIT_AUTHORITY_REQUIRED`。只冻结关系：single order notional远低于 capital cap；daily loss cap远低于 pilot capital；position与order count有硬上限；LIMIT-only；任何数值缺失都拒绝授权。

## 10. LiveSession, approval and explicit authorization

V39/domain 已能表达 session、account reference、credential reference、release、risk、symbols、capital、window、approval、expiry 与 legacy `approval-scope.v1`；GateY-6D audit confirmed forward migration is required before exact pilot materialization. Mandatory prerequisite facts、fresh observation identity/freshness、versioned canonical `pilotScopeHash` 与 approval exact binding 不能由 V39 无损表达，必须先完成独立 forward migration work order；不得伪回填历史 session 或在 materialization task 内临时改义 V39 字段。

`LiveSessionControlService` 能拒绝creator与approver为同一identity，且GateY-2 accepted evidence明确覆盖该规则，故separation capability为PASS；具体pilot的两个独立authenticated主体均未选择，pilot binding为`NOT_MET`。不存在已接受的single-operator exception，禁止同一identity以不同role伪装双主体。

当前授权事实固定：

```text
EXPLICIT_MICRO_LIVE_AUTHORIZATION=NOT_GRANTED
FIRST_REAL_ORDER=NOT_AUTHORIZED
MICRO_LIVE=NOT_AUTHORIZED
```

未来授权必须明确表达并绑定 exact scope，例如：“我授权 NQ 针对 scope hash X，在该 scope 指定的 OKX pilot account、release、risk、symbol、notional与window下执行 GateY-6第一笔真实 LIMIT order。”“继续”“开始 GateY-6”“按计划做”或历史口头许可均无效。

## 11. Kill contract

- Pre-start：kill必须为 `ENGAGED` 且 revision、source、freshness一致；`UNKNOWN/MISSING/STALE/CONFLICT` 均拒绝第一单。
- Disengage：只能由绑定 exact scope的独立显式授权触发；worker不得自授权；有独立审计 event与短时 deadline。
- Propagation：后续实现必须冻结 bounded propagation deadline，并以进程、network mutation gate与durable intent claim三处验证。
- Unexpected revision：立即停止新 intent，未发送 intent保持未发送；已 `SEND_STARTED` intent仅 query-first reconciliation。
- Immediate engage：任何 stop severity事件立即 engage kill，阻止新 intent，无自动 restart。
- Post-first-order/post-session：完成对账后 kill重新 `ENGAGED`；第一单后进入 `LIVE_PAUSED`，不得自动第二单。

## 12. Real reconciliation contract

最小闭环必须同时覆盖：local `ExecutionIntent`、local `ExecutionReceipt`、local `Order`、remote order by clientOrderId、remote fills、local `Trade`、local `Position`、local `Ledger` 与 account balance snapshot。

状态定义：

- `MATCHED`：identity、state、quantity、price/fee、fills、position、ledger与balance movement在规则内收敛。
- `UNKNOWN`：mutation result不确定；只允许 query-by-clientOrderId，不允许新 PLACE。
- `DIVERGED`：local/remote事实明确不一致；engage kill并冻结session。
- `RECONCILIATION_BLOCKED`：permission、network、rate limit、clock或data gap导致无法证明收敛；停止新 PLACE并进入人工处置。

任何非 `MATCHED` 状态都禁止新 PLACE。严重 divergence、scope/identity mismatch或长期 unknown 必须 engage kill；operator处理和审计结论不得覆盖原始 durable evidence。

## 13. FIRST_REAL_ORDER state path and cancel semantics

设计路径（本任务不执行）：

```text
APPROVAL_PENDING
→ APPROVED
→ LIVE_WARMUP
→ exact preflight PASS
→ exact ExecutionIntent creation
→ worker claim
→ durable SEND_STARTED
→ exactly one real LIMIT PLACE
→ ExecutionReceipt
→ query/reconcile
→ LIVE_PAUSED
→ manual inspection + full reconciliation
→ kill ENGAGED
```

Acceptance固定为 one PLACE only、no automatic second PLACE、manual inspection、full reconciliation complete、kill re-engaged。Cancel不是强制测试动作：known open且安全时才可成为controlled CANCEL候选；filled时不cancel；partial fill先reconcile；unknown只query。任何 cancel timeout/result unknown也必须query-first，禁止blind cancel或mutation retry。

## 14. Real error taxonomy

| Error class | Retry/query policy | Kill/pause effect | Operator action | Audit code |
| --- | --- | --- | --- | --- |
| transport timeout | no PLACE retry；query by clientOrderId | pause new intents；unresolved则kill | verify network then reconcile | `REAL_TRANSPORT_TIMEOUT` |
| HTTP error | 4xx no retry；bounded 5xx query-first | pause；repeated/unknown则kill | inspect sanitized status/request ID | `REAL_HTTP_ERROR` |
| exchange business rejection | no mutation retry until cause fixed | pause affected intent | inspect typed reason and scope | `REAL_BUSINESS_REJECTED` |
| permission denied | no retry | immediate kill/freeze | verify dedicated key scope | `REAL_PERMISSION_DENIED` |
| IP restriction | no retry | immediate kill/freeze | verify exact egress allowlist | `REAL_IP_RESTRICTED` |
| timestamp/clock skew | no retry until clock healthy；query if send uncertain | immediate pause；threshold breach kill | repair time source and re-preflight | `REAL_CLOCK_SKEW` |
| rate limit | honor bounded backoff for reads only；no blind mutation retry | pause new intents；repeated breach kill | reduce rate/inflight and reconcile | `REAL_RATE_LIMITED` |
| insufficient balance | no retry | pause/freeze | reconcile balance and scope cap | `REAL_BALANCE_INSUFFICIENT` |
| instrument restriction | no retry | freeze symbol | refresh metadata and reapprove changed scope | `REAL_INSTRUMENT_RESTRICTED` |
| invalid price/size | no retry | freeze intent/symbol | refresh tick/lot/min value; new approval if scope changes | `REAL_PRICE_SIZE_INVALID` |
| unknown result | query-only by clientOrderId | no new PLACE；deadline breach kill | reconcile remote/local facts | `REAL_RESULT_UNKNOWN` |
| partial fill | no replacement PLACE；query fills/order first | live paused | reconcile quantity/fee/position/ledger | `REAL_PARTIAL_FILL` |
| cancel race | no blind cancel retry | live paused | query order/fills and converge | `REAL_CANCEL_RACE` |

## 15. 120h controlled soak contract

Candidate acceptance：duration=`120h`、manual start、single account、single release、single owner、bounded 1～2 symbols、micro capital、LIMIT-only、continuous reconciliation、stable worker/release identity、kill always available、complete immutable audit。第一单需要独立 acceptance后，才可由另一个明确批次进入 soak；本工作单不启动它。

必须记录：orders attempted/accepted/rejected、fills、partial fills、cancels、unknown results、reconciliation cases、kill events、risk denials、latency、fees/slippage、position drift、ledger drift与permission drift。每条事实绑定session/account/release/risk/scope hash/order/intent/run identity，不记录 credential material。

Immediate-stop conditions：credential permission drift；WITHDRAW unexpectedly enabled；NQ transfer/withdraw operation变为可达；适用的account-level transfer restriction发生不利漂移；IP allowlist mismatch；unexpected endpoint；fake/real fallback ambiguity；kill inconsistency；worker identity/release/risk/scope hash mismatch；unresolved unknown；reconciliation blocked；order/fill/position/ledger divergence；risk cap/daily loss breach；instrument metadata inconsistency；clock failure；secret leakage；unexpected real network path。OKX `TRADE` 固有funding-transfer capability本身是已知残余，不伪装成首次观察到的异常；任何NQ funds-movement reachability仍立即停止。

任一 stop-severity 条件触发：`ENGAGE KILL`、`STOP NEW INTENTS`、`NO AUTO RESTART`、terminal/frozen session、保留 evidence并进入独立incident review。

## 16. GateY-6 batch decomposition

1. `GateY-6A`（本工作单）：FIRST_REAL_ORDER hard-gate reconstruction、capability/pilot separation、work-order/manifest；无产品代码、无 credential、无 network。
2. `GateY-6B`：OKX Spot real-provider mutation contract、typed endpoint guard、stable clientOrderId、query/cancel/fill、error taxonomy、rate/clock/metadata contracts；固定`NO REAL CREDENTIAL / NO OKX NETWORK / NO REAL MUTATION / FAKE-STUB-CONTRACT-TESTS ONLY`。
3. `GateY-6C`：scoped credential/IP/private permission lifecycle与真实read-only verification设计/执行；`NO MUTATION`，不得PLACE/CANCEL，独立安全审查。
4. `GateY-6D`：exact pilot scope/prerequisite materialization、release/risk/account/window/identity binding与独立approval；kill保持`ENGAGED`，不得创建或发送order intent。
5. `GateY-6E`：exact scope显式授权与exactly-one tiny LIMIT execution；只能在前置gate全部PASS、独立review与exact-head CI后由用户另行明确授权；完成后进入`LIVE_PAUSED`，不得自动开始120h soak。
6. `GateY-6F`：120h controlled micro-live soak、continuous reconciliation、terminal closeout；不得从第一单自动进入。

Real mutation必须与 provider implementation、credential verification、pilot binding分批。每批独立实现、review、commit、exact-head CI、authority acceptance；后批不得借前批“代码存在”绕过 acceptance。

## 17. Acceptance criteria for this work order

- 30个hard gates均有三层status、evidence、blocker与next action；final counts为 `0/25/5`。
- 10个 `HARD_GATE_GAP_CANDIDATE` 被显式记录，不把legacy adapter误报为real provider。
- exact pilot schema完整，所有无authority字段保持 `UNRESOLVED`。
- credential/IP/permission保持真实的NOT_MET/NOT_VERIFIABLE。
- authorization/first order/micro-live未授予，LIVE关闭，kill engaged。
- backend/frontend/research/migration/scripts/deploy/.github diff均为0。
- JSON、authority、links、diff与secret-like field checks通过；manifest missing/unknown/invalid语义显式fail closed。
- 独立Security/Operations Review完成且P0/P1=0后，状态才可到`REVIEW_ACCEPTED|READY_TO_COMMIT`；review不得授权provider、credential或真实订单。

## 18. Rollback / abort

未提交时，回滚仅删除本轮新增的work order/manifest/evidence并恢复本轮current docs行；不得使用破坏性Git命令覆盖用户变更。提交后使用独立revert commit撤销本批docs。任何独立审查P0/P1、manifest计数/路径错误、authority conflict或安全边界不一致都使本工作单 `BLOCKED`，保持FIRST_REAL_ORDER未授权、kill engaged、LIVE disabled。

## 19. Do-not-build list

本工作单不构建real credential UI/API、raw REST client、provider fallback、second venue、market order、margin/leverage/futures/options/borrow、transfer/withdraw/funding、AI/LLM execution、DH runtime execution、unattended execution、自动第二单、自动restart、V40或第二套execution ledger。

## 20. Unique next engineering route

当前real provider与private trading均未实现，第一工程blocker是worker-safe provider contract。因此在本工作单独立安全/运维审查与exact-head CI接受后，唯一建议工程任务为：

```text
NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-IMPLEMENTATION
```

该任务仍必须`NO REAL CREDENTIAL / NO OKX NETWORK / NO REAL MUTATION / FAKE-STUB-CONTRACT-TESTS ONLY`，并接受独立安全审查后才能进入credential verification或pilot materialization。
