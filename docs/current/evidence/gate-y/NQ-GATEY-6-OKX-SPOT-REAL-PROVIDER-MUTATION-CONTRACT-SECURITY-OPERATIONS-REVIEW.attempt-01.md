# NQ-GATEY-6 OKX Spot real-provider mutation contract Security/Operations Review — attempt-01

## 1. 结论与边界

- 结论：`PASS / GATEY_6B_REAL_PROVIDER_MUTATION_CONTRACT_SECURITY_REVIEW_ACCEPTED / P0_0 / P1_0 / READY_TO_COMMIT`（通过 / GateY-6B contract 安全复核已接受 / 无 P0/P1 / 可进入提交前复核）。
- 范围：`SpotExecutionProviderPort`、typed requests/results/errors/clientOrderId、OKX Spot typed adapter/operation/transport/state translator、endpoint guard、default wiring 与 architecture tests。
- 明确不涉及：credential、signer、真实 transport、OKX network、真实 PLACE/CANCEL/QUERY、worker/runtime 接线、migration、deploy、micro-live、`FIRST_REAL_ORDER` 或 LIVE。
- authority：accepted batch 保持 `GateY-5 / ACCEPTED|CI_GREEN`；GateY-6 仅到 `REVIEW_ACCEPTED|READY_TO_COMMIT / UNCOMMITTED / NOT_RUN`。

## 2. Baseline 与 candidate inventory

- branch=`dev`；`HEAD == origin/dev == 2a00d1e3cbab8bec38f344090b0636bf69b78cd1`；baseline exact-head CI=`31804169275 / completed / success`。
- review 起始 candidate=`13 tracked + 13 untracked = 26 paths`；staged=`0`；unexpected/missing/unrelated=`0/0/0`；`git diff --check` 通过。
- 模块：`nq-core` 持有 execution application/provider port 与 normalized contract；`nq-adapter-okx` 只实现 adapter contract；`nq-app` 仅增加 default-zero-bean 与 ArchUnit 回归；current docs 仅同步事实。
- legacy `OkxExchangeAdapter` diff=`0`；frontend/research/deploy/`.github`/migration/governance contract/hard-gate manifest diff=`0`。

## 3. Architecture、reachability 与 transport isolation

- dependency direction 为 `execution application -> SpotExecutionProviderPort <- nq-adapter-okx implementation`；core/strategy/controller 不依赖 OKX DTO 或 adapter，无第二套 Order/Trade/Position/Ledger/ExecutionIntent/Receipt/Reconciliation 真相。
- `OkxSpotProviderAdapter`、`OkxSpotProviderTransport` 的 production registration/implementation、`@Component/@Service/@Bean/@Import`、worker binding、runtime caller 均为 `0`；默认 Spring context 的 `SpotExecutionProviderPort` beans=`0`。
- transport surface 仅五个 typed methods：`PLACE_LIMIT / QUERY_ORDER / CANCEL_ORDER / READ_ORDER / READ_FILLS`；无 host/baseUrl/raw URL/method/path/header/body/generic execute escape hatch。
- runtime mutation reachable paths=`0`；production transport implementations=`0`；real credential/signature lookup=`0`；provider-attributable DNS/socket/OKX/fallback calls=`0`。

## 4. Security/operations review 与整改

Review 初查发现并在同一 candidate 内关闭：

1. P1 `MUTATION_RESULT_CERTAINTY_UNSAFE`：transport 已调用后仍可能信任 `mutationMayHaveReachedVenue=false`。整改后 PLACE/CANCEL transport invocation 对 timeout/HTTP/rate-limit/oversize/malformed/unknown 始终按可能已发送处理，返回 UNKNOWN/query-first，mutation retry 恒为 false。
2. P1 `UNBOUNDED_ORDER_RESPONSE_FILLS`：PLACE/QUERY/CANCEL order response 的 fills 未执行 100 条上限。整改后 translator 在构造 normalized observation 前执行 cap，101 条返回 `RESPONSE_TOO_LARGE`。
3. P2 `RESPONSE_CAP_POST_CHECK_ONLY`：仅凭完整 response metadata 后置检查。整改后所有 typed command 携带 `ResponseReadLimit(maximumResponseBytes, maximumFillRecords)`，合同要求未来 transport 在分配或完整读取 body 前执行；metadata 检查保留为第二道防线。当前无 production transport，因此不宣称已实现真实网络流式 cap。
4. P2 `WEAK_RESULT_AND_FILL_INVARIANTS`：补齐 positive fill price/quantity、最多100条、trade ID唯一、order quantity/state、UNKNOWN certainty、cancel disposition 与 fill-page 不变量。

Review 额外补强：`SpotExecutionProviderPort` 明确 mutation 只能由既有 GateY-3 execution owner 在 durable `SEND_STARTED` 成功落库后调用；port 不创建或替代该事实。新增攻击性测试覆盖 false certainty hint、UNKNOWN→NOT_FOUND 不重发、pre-read cap 下传、101/duplicate/malformed order fills、duplicate/zero/oversize fill reads。

## 5. Contract review

- LIMIT-only：venue 固定 `OKX_SPOT`、order type 只有 `LIMIT`；null/zero/negative price/quantity 与 intent/clientOrderId/session/context tampering 在 transport 前拒绝。
- clientOrderId：由 intent UUID 稳定转换为 exact 32 lowercase hex；同 intent 重建稳定、不同 intent 不同；caller-provided value 必须与 intent canonical identity 相等。
- idempotency：provider 无 mutation retry loop；每次 PLACE/CANCEL 最多一次 fake mutation call；UNKNOWN 与 UNKNOWN+NOT_FOUND 只允许 query-first，不推导可重发 PLACE。
- state translation：live/open、partial、filled、canceled、rejected 精确映射；blank/null/future state、clientOrderId mismatch、quantity/fill malformed 全部 fail closed 为 UNKNOWN。
- cancel：OPEN 才是普通 controlled candidate；PARTIALLY_FILLED 默认 query-first；FILLED/CANCELED/REJECTED/NOT_FOUND 不 mutation；UNKNOWN 只 query；timeout/race 不盲重试。
- error/sanitization：14 类 typed error 的 certainty/recommendation/audit code 不含 raw body/header/URL/credential；所有结果 record 只携带 bounded IDs、quantity、timestamp 与 sanitized enum/code。
- clock/rate limit：只消费 caller preflight clock observation；missing/stale/over-limit 在 mutation 前 fail closed。rate limit 只产生 pause/backoff/query recommendation，不存在 mutation scheduler。

## 6. Static scan classification

对 26-path candidate 的 Java/XML 扫描 `http:// / https:// / okx.com / api.okx / HttpClient / WebClient / RestClient / URL / URI / Socket / credential / secret / passphrase / signature / Authorization / OK-ACCESS`：

- `TYPE_ONLY`：Maven POM schema URI；`OkxSpotEndpointGuard` 使用 `java.net.URI` 做纯本地 parse/default-deny 校验，不打开连接。
- `COMMENT`：port/adapter/transport/error JavaDoc 的否定性边界文字。
- `TEST_ONLY`：`invalid.example` 负例、ArchUnit 禁止依赖字符串、readiness 脱敏断言与 `OkxHttpClient` reflection negative probe。
- `EXECUTABLE_RISK=0`；credential store/env/file reads=`0`；signer/private header/raw payload=`0`。

Codex Security 与 CodeRabbit 均为 `NOT RUN`：项目 active skills allowlist 不包含这两项，本轮以 scoped static scan、ArchUnit、Spring wiring、no-outbound guard、fake invocation counters 与 full regression 替代，不冒充外部扫描结果。

## 7. Validation evidence

| 验证 | 结果 |
| --- | --- |
| focused provider/endpoint/private-read | PASS；core 9 + adapter 25 = 34，fail/error/skip=`0/0/0` |
| default wiring/ArchUnit/no-outbound | PASS；27 tests，fail/error/skip=`0/0/0` |
| GateY-3 | `ExecutionIntentRuntimeTest` 11 PASS |
| GateY-4 | endpoint/private-read/credential hardening 13 PASS |
| GateY-5 | loopback fake + disposable worker 4 PASS |
| full backend | 23/23 modules `BUILD SUCCESS`；Surefire 1478 tests，failures=0、errors=0、skipped=44 |
| governance matcher | target action actual/expected=`COMMIT_AND_PUSH/COMMIT_AND_PUSH`，valid=`True` |
| migration/manifest | migration diff=0；manifest diff=0，`PASS/NOT_MET/NOT_VERIFIABLE=0/25/5` |
| no-egress | required no-outbound guard、typed fake counters与零 production transport通过；OKX/network/real mutation=`0/0/0` |

## 8. Findings、残余风险与下一动作

- P0：0。
- P1：0 open；2 closed in candidate。
- P2：0 open；2 closed in candidate。未来真实 transport 必须单独证明 socket/body 消费前 byte cap、credential boundary、signing、timeout/rate limit 与 real no-egress-to-allowlisted-egress transition，本 review 不提前接受。
- P3：0。
- residual blockers：对本次 contract review 为无；对真实 provider/private trading/第一笔真实订单仍全部阻断，hard gates 未提升。
- 唯一下一动作：`NQ-GATEY-6-OKX-SPOT-REAL-PROVIDER-MUTATION-CONTRACT-COMMIT-AND-PUSH`。
- 推荐 commit：`feat(gatey): add OKX Spot real-provider mutation contract`。

最终结论：`PASS / GATEY_6B_REAL_PROVIDER_MUTATION_CONTRACT_SECURITY_REVIEW_ACCEPTED / REAL_MUTATION_RUNTIME_UNREACHABLE / NO_REAL_TRANSPORT / NO_REAL_CREDENTIAL / NO_EGRESS / LIMIT_ONLY_VERIFIED / ENDPOINT_ALLOWLIST_VERIFIED / STABLE_CLIENT_ORDER_ID_VERIFIED / UNKNOWN_QUERY_FIRST_VERIFIED / NO_BLIND_RETRY_VERIFIED / STATE_AWARE_CANCEL_VERIFIED / SANITIZED_OUTCOME_VERIFIED / DEFAULT_RUNTIME_FAIL_CLOSED / MIGRATION_UNCHANGED / P0_0 / P1_0 / REAL_PROVIDER_NOT_IMPLEMENTED / PRIVATE_TRADING_NOT_IMPLEMENTED / FIRST_REAL_ORDER_NOT_AUTHORIZED / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED / READY_TO_COMMIT`。
