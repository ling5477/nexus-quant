# GateY-6E first real order prerequisite implementation — attempt-02

## 任务分类与结论

- Task classification：`CODE_CHANGE / TRUSTED_REAL_OBSERVATION / OKX_REAL_PROVIDER_TRANSPORT / PRIVATE_TRADING_BOUNDARY`；NQ-only、高风险 capability implementation。
- 主 skill：`java-backend-maintenance`；先由 `nq-dh-workflow-router` 固定 NQ/Gate/LIVE 边界，`nq-docs-writer` 仅在全量验证通过后同步本 evidence。
- 结论：`PASS / GATEY_6E_FIRST_ORDER_PREREQUISITES_IMPLEMENTED / V41_REUSED / TRUSTED_REAL_OBSERVATION_CAPABILITY_IMPLEMENTED / REAL_PROVIDER_TRANSPORT_CAPABILITY_IMPLEMENTED / REAL_MUTATION_RUNTIME_UNBOUND / NO_BLIND_RETRY / OKX_CALL_0 / EXECUTION_INTENT_0 / EXCHANGE_MUTATION_0 / FIRST_REAL_ORDER_NOT_AUTHORIZED / LIVE_DISABLED / KILL_ENGAGED / PENDING_INDEPENDENT_SECURITY_REVIEW`。

## Baseline 与边界

```text
branch=dev
initial_worktree=clean
staged=0
HEAD=origin/dev=1770c38655e16fa8708e4363bcdc4fda007f46c9
commit_subject=fix(gatey): align OKX minimum order constraint semantics
CI=31952505427 / completed / success
accepted_batch=GateY-6D
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-6E
work_batch_status=NOT_STARTED
live=DISABLED
kill_switch=ENGAGED
```

本轮只实现 capability 并使用 deterministic fake HTTP/test credential 验证。未读取真实 credential，未调用真实 OKX API，未物化 PilotScope/approval，未创建 ExecutionIntent/Receipt，未执行 PLACE/CANCEL/TRANSFER/WITHDRAW，未启动 worker，未 enable LIVE，未 disengage kill switch。

## Official OKX v5 contract verification

核验日期：2026-08-16。只读取 [OKX API v5 官方文档](https://www.okx.com/docs-v5/en/)，未访问任何真实 `/api/v5/**` endpoint。

| Contract | 核验结果 | 实现约束 |
| --- | --- | --- |
| [Account instruments](https://www.okx.com/docs-v5/en/#trading-account-rest-api-get-instruments) | SPOT instrument 返回 `state/tickSz/lotSz/minSz/groupId`；`groupId` 是 instrument 到 fee group 的权威映射 | exact `instType=SPOT&instId`；不推导 notional；V41 v2 固定 `VENUE_NOT_PUBLISHED`、value/currency 双 NULL |
| [Fee rates](https://www.okx.com/docs-v5/en/#trading-account-rest-api-get-fee-rates) | `feeGroup[].groupId` 必须与 instrument `groupId` 精确匹配；读取 `level/maker/taker/ts` | 多 group 必须唯一命中 exact group，top-level 与 group maker/taker 必须一致，provider timestamp 必须 fresh |
| [Balance](https://www.okx.com/docs-v5/en/#trading-account-rest-api-get-balance) | USDT 可用余额字段为 `details[].availBal` | exact `ccy=USDT`；禁止使用 total/equity/cashBal 替代 |
| [System time](https://www.okx.com/docs-v5/en/#public-data-rest-api-get-system-time) | `ts` 提供 OKX server time | local before/after midpoint + server time 计算并复核 skew；不一致、过期或超限 fail closed |
| [Place order](https://www.okx.com/docs-v5/en/#order-book-trading-trade-post-place-order) | `clOrdId` 最多 32 位且仅大小写字母数字 | 复用 GateY-6B `intent UUID -> 32 lowercase hex` provider identity；不修改 GateY-3 已冻结的 44 位 execution identity |
| Query/cancel/fills | query/cancel 使用 exact `instId + clOrdId`；fills 以 `ordId` 精确过滤 | `GET order(instId+clOrdId) -> ordId -> GET fills(instId+ordId+bounded window/limit)`；禁止扫描账户 fills |

允许 endpoint closed set保持为四个 prerequisite GET 与 `POST/GET order`、`POST cancel-order`、`GET fills`；没有 raw path、method、query 或 generic execute escape hatch。

## Trusted prerequisite observation capability

- 新增非 Spring bean `OkxPilotPrerequisiteObservationAuthority`，复用 `PilotPrerequisiteObservationAuthority`、V41 instrument v2、exact-reference `OkxPrivateCredentialExecutor`、既有 signer 与 bounded JDK transport。
- exact owner/account/credential 的单一 JIT callback 内采集 instrument、fee、USDT available balance 与 server time；callback 返回后 session失效，credential char arrays 与请求/响应 byte arrays按路径清零。
- observation set/id/source/schema/identity/digest/hash全部由 server生成；operator不能提交 observation value、observedAt、source、identity 或 hash。
- instrument digest、fee digest 与 immutable scope constant-time比对；source/schema/fee evidence/signed timestamp source必须精确匹配。
- malformed/missing/partial/source mismatch、stale fee、超出最短 freshness window 的 collection、server-time/skew 内部不一致全部返回固定脱敏错误；不返回 partial set。
- instrument item使用 `instrument-metadata-observation.v2`；`minimumOrderValueEvidenceClass=VENUE_NOT_PUBLISHED`，`minimumOrderValue=null`，`minimumOrderValueCurrency=null`，没有伪造或推导 notional。
- production Spring runtime 仍只装配既有 unavailable authority；本 capability class无 `@Component/@Service/@Bean`。

## Real provider typed transport capability

- 新增 `OkxPrivateRealTransport` 与 package-private `OkxJdkRealClient`，扩展既有 `JdkOkxPrivateReadTransport`；同一 signer、同一 JDK client、同一 semaphore，redirect=`NEVER`，retry=`0`。
- `OkxCredentialScopedSpotProviderTransport` 非 Spring bean，构造时绑定 exact owner/account/credential/session 与 1～2 个 canonical、sorted、unique `*-USDT` symbol；越 scope 请求在 credential callback 前拒绝，environment固定 `PRODUCTION`。
- PLACE 只生成 `instId/tdMode=cash/side/ordType=limit/clOrdId/px/sz`，只发送一次 POST；ACK 不是终态，随后 exact query。market/post_only/IOC/FOK/algo/batch/amend/margin/leverage/borrow均无 typed入口。
- CANCEL 只生成 `instId + clOrdId`，只发送一次 POST；ACK 后 exact query，race保持 `queryRequired=true`。
- QUERY/READ ORDER 必须验证返回 `instId/clOrdId` 精确一致；unknown state/identity/quantity均 fail closed。
- FILLS 先按 `instId + clOrdId` 查询订单解析 exact `ordId`，再按 exact `instId + ordId + begin/end/limit` 读取；每条 fill复核 `instId/ordId/clOrdId`，response bytes与fill records均有上限。
- timeout/reset、response parse failure、ambiguous HTTP/5xx、429、401/403在 mutation send 后保持 `UNKNOWN / queryRequired=true`；明确 OKX business rejection为 `DEFINITIVELY_REJECTED / queryRequired=false`，无 retry、无 auto resize。
- `SpotExecutionProviderPort` 未注册 bean、未接 execution worker；real mutation runtime=`UNBOUND`。

## Architecture 与安全自审

- OKX protocol保持在 `nq-adapter-okx`；credential owner/JDBC保持在 account/infra；pilot orchestration保持在 livecontrol；execution继续拥有 mutation port；无第二 SoR、无新增 migration、无 API/DTO 或 cross-domain application dependency。
- GateY-3 execution clientOrderId冻结算法未修改；GateY-6B provider identity继续是无截断、可逆的 intent UUID 32 hex，避免 silent replay/idempotency contract drift。
- authenticated headers、credential、raw provider payload与原始 exception cause不进入 DTO、log、audit、evidence或返回错误。
- P0/P1/P2/P3=`0/0/0/0`；仍需独立 security review，不构成 capability acceptance、runtime binding或交易授权。

## Validation

| Command / check | Result |
| --- | --- |
| `mvn -f backend/pom.xml -pl nq-infra -am -DskipTests compile` | PASS；16/16 modules |
| focused adapter/core/infra tests | PASS；59 tests、failures/errors=`0/0`（stale-window补强后对应 named suites再次通过） |
| focused app/ArchUnit command | PASS；23/23 reactor modules；named provider/credential/observation/control-plane/V41/architecture suites全通过 |
| `mvn -f backend/pom.xml test` | PASS；23/23 modules；317 reports、1538 tests、failures/errors/skipped=`0/0/48`；`BUILD SUCCESS`，53.599s |
| IDEA reformat / inspections | PASS；关键新类无 error；保留少量非阻断 style/duplicate-code warning |
| `scripts/docs/check-current-authority.ps1` | PASS；首次因 root/current README 与 ROADMAP 三处旧 implementation pointer 为 exit=`1`、errors=`8`；最小同步为 security review 后 exit=`0`、errors=`0` |
| `scripts/docs/check-doc-links.ps1` | PASS；checked=`357`、existing warnings=`14`、errors=`0` |
| `git diff --check` / forbidden-area diff | PASS；无 whitespace error；frontend/research/scripts/migration/`nq-api` diff=`0`；仅有既有 LF→CRLF 非阻断提示 |

全量测试日志中的 Mockito dynamic-agent、SLF4J NOP、deprecation/unchecked、测试用 generated security password 与预期异常 stack trace为既有非阻断 warning；没有真实 egress 或交易副作用。

## Authority after

```text
accepted_batch=GateY-6D
accepted_batch_status=ACCEPTED|CI_GREEN
work_batch=GateY-6E
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-6E-FIRST-REAL-ORDER-PREREQUISITE-SECURITY-REVIEW
EXACT_PILOT_SCOPE=NOT_MATERIALIZED
FIRST_REAL_ORDER=NOT_AUTHORIZED
MICRO_LIVE=NOT_AUTHORIZED
live=DISABLED
kill_switch=ENGAGED
```

Machine `real_provider/private_trading` 在 independent security review接受前仍保持 `NOT_IMPLEMENTED`，用于避免把未审查 capability误写成 accepted/runtime-available path；本 evidence只声明 capability code已实现且 runtime unbound。

## Exact changed files

```text
README.md
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/JdkOkxPrivateReadTransport.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxJdkRealClient.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPilotPrerequisiteRequest.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPilotPrerequisiteSnapshot.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateHttpExchange.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateRealTransport.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateRequestSigner.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderAdapter.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderTransport.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/JdkOkxRealTransportTest.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateRequestSignerTest.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/PilotObservationCanonicalEncoder.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/provider/SpotExecutionProviderPort.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/JdbcOkxPrivateCredentialExecutor.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateCredentialExecutor.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxCredentialScopedSpotProviderTransport.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthority.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/okx/readonly/JdbcOkxPrivateCredentialExecutorTest.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxCredentialScopedSpotProviderTransportTest.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthorityTest.java
docs/current/STATUS.md
docs/current/README.md
docs/current/ROADMAP.md
docs/current/TESTING.md
docs/current/WORKLOG.md
docs/current/evidence/gate-y/README.md
docs/current/evidence/gate-y/NQ-GATEY-6E-FIRST-REAL-ORDER-PREREQUISITE-IMPLEMENTATION.attempt-02.md
```

## 回滚、提交与下一步

- 未 stage/commit/push/deploy。回滚时只反向应用本 evidence exact changed-file allowlist，禁止整仓 reset/restore。
- 建议独立 review通过后 commit：`feat(gatey): implement first real order prerequisites`。
- 下一动作唯一为 `NQ-GATEY-6E-FIRST-REAL-ORDER-PREREQUISITE-SECURITY-REVIEW`；它不是第一笔真实订单授权。
