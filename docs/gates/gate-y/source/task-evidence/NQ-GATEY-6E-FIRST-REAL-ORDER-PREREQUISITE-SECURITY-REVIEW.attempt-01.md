# NQ-GATEY-6E-FIRST-REAL-ORDER-PREREQUISITE-SECURITY-REVIEW — attempt-01

## 审查结论

`PASS / GATEY_6E_PREREQUISITE_SECURITY_REVIEW_ACCEPTED / P0_0 / P1_0 / TRUSTED_OBSERVATION_ACCEPTED / CREDENTIAL_JIT_BOUNDARY_ACCEPTED / REAL_PROVIDER_TRANSPORT_ACCEPTED / ORDER_IDENTITY_ACCEPTED / NO_BLIND_RETRY_ACCEPTED / REAL_MUTATION_RUNTIME_UNBOUND / V41_REGRESSION_PASS / OKX_CALL_0 / EXECUTION_INTENT_0 / EXCHANGE_MUTATION_0 / FIRST_REAL_ORDER_NOT_AUTHORIZED / LIVE_DISABLED / READY_TO_COMMIT`（通过 / GateY-6E prerequisite 安全审查已接受 / 可进入提交前复核）。

本次接受只覆盖未提交的 trusted prerequisite observation capability 与 credential-scoped typed OKX Spot transport capability。它不表示真实 provider runtime 已接受或启用，不物化 exact PilotScope，不创建 approval、ExecutionIntent 或 ExecutionReceipt，不授权第一笔真实订单、micro-live 或 LIVE。

## Baseline 与 reviewed diff

- branch=`dev`；`HEAD == origin/dev == 1770c38655e16fa8708e4363bcdc4fda007f46c9`；baseline CI=`31952505427 / completed / success`；进入本轮 staged=`0`。
- authority before：`GateY-6E / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；next action 为本 Security Review。
- 完整审查 28 个原始 working-tree diff 文件，包括 production Java、tests、V41/current evidence 与 authority 文档；15/15 main-source security surfaces 已闭合。
- Codex Security diff scan：`c33f878b-03fa-4aee-9374-530f886b94d0`；snapshot digest=`codex-security-snapshot/v1:sha256:df27856fe5e7b809f4019c82d1682f2e6c6b48e4566267096ffecc2ac2401e4b`；coverage=`complete`；final findings=`0`。
- 不修改 V41/schema/migration、frontend、research、scripts、deployment、CI 或 runtime wiring；不访问真实 credential material，不调用真实 OKX API，不执行真实交易。

## Findings 与最小修复

### P0

- 无。

### P1

初始确认两个 P1，均在本 review 内按授权做最小修复并重新验证，final open=`0`。

1. `SIGNED_FILLS_QUERY_PARAMETER_INJECTION`
   - 原实现将 venue 返回的 `ordId` 直接拼入 fills query；恶意或异常值可注入 `&limit=...` 等 query delimiter，使签名与请求覆盖非预期参数。
   - 修复：在构造 exact signed fills path 前执行 RFC 3986 query-value encoding；空格固定为 `%20`，星号为 `%2A`，`~` 保持 canonical。
   - 回归：`percentEncodesVenueOrderIdBeforeBuildingSignedFillsQuery` 验证 encoded exact URI 与 signature input一致。
2. `MUTATION_DUPLICATE_CLIENT_ID_FALSE_DEFINITIVE_REJECTION`
   - OKX documented `51016 / HTTP 200` 表示 client order ID already exists。原实现把所有非零 item `sCode` 映射为 `DEFINITIVELY_REJECTED / queryRequired=false`，可能错误证明 venue 未接受本次或先前同 identity 的订单。
   - 修复：PLACE root non-zero一律进入 mutation uncertainty；item `51000` 保留明确参数拒绝，`51016`、`50011`、permission 与 unknown code均返回 `UNKNOWN / queryRequired=true`。CANCEL root/item non-zero同样 query-first。
   - 回归：`duplicateClientOrderIdAndRootRateLimitRemainQueryFirst` 验证 no blind retry。

### P2

- 无。

### P3

- 无。

另一个候选为 read-only Spring profile 中 concrete transport class 具备 mutation capability。main-source call graph 与 Spring context 共同证明 production `SpotExecutionProviderPort`、worker、scheduler、startup mutation 与 `ExecutionIntent → real transport` caller均为0，因此 disposition=`suppressed / high confidence`，不是当前 runtime reachability finding。

## Trusted observation authority

- operator request只提供 account/credential reference 与目标 symbol等控制输入，不能提交 instrument/fee/balance/clock observation value、source、schema、identity、time 或 hash。
- instrument observation固定复用 V41 v2；`VENUE_NOT_PUBLISHED` 时 minimum-order-value 与 currency必须双 `NULL`，不得重新引入人工 notional。
- instrument来自 account instruments；fee来自 exact fee group；balance只取 USDT `availBal`；clock/skew来自 public server time response。
- observation set identity、source/schema、observed time与canonical hash由server构造；partial、malformed、stale、source mismatch、symbol mismatch与 torn set均 fail closed。
- 四类 observation由一次 authority call形成完整 snapshot；不允许 operator value 与 trusted envelope 混合。

## Credential / JIT boundary

- credential material只存在既有 `JdbcOkxPrivateCredentialExecutor` JIT callback，callback外不缓存、不返回、不复制 API key/secret/passphrase。
- 未复制 credential resolver或signer；typed transport通过既有 callback消费credential，调用结束即释放引用。
- request URI、DTO、exception、log 与 committed evidence不包含 credential material；tests仅使用 synthetic credentials。
- review中 real credential read=`0`，authenticated OKX call=`0`。

## Provider closed set 与 HTTP/SSRF safety

- closed operation set精确为 `PLACE_LIMIT / QUERY_ORDER / CANCEL_ORDER / READ_ORDER / READ_FILLS`；无 raw URL/path、generic execute、batch/algo/amend、market order、margin/leverage/borrow、transfer或withdraw。
- PLACE固定 `SPOT / tdMode=cash / ordType=limit`，使用稳定 `clOrdId`；transport不自动resize、reprice或增加minimum notional。
- base URI只来自受控 configuration并要求 HTTPS；caller不能提供 host、scheme或raw path；`HttpClient.Redirect.NEVER`。
- request timeout、bounded response bytes、bounded fills count、strict mandatory-field validation均存在；malformed、truncated、oversize与unexpected shape fail closed。
- method/path/query/body先canonical再签名；authentication headers由transport固定构造，不接受 caller header injection。

## Order / fill identity

- QUERY/READ/CANCEL均验证 returned `instId/clOrdId` 与请求 exact相等；identity mismatch不进入成功结果。
- Fills固定 `instId + clOrdId → exact order query → exact ordId → fills(instId + encoded ordId + bounded window)`。
- 不存在全账户 fills scan、模糊匹配、未校验 exchange order ID 或 cross-symbol/cross-session association。
- 每条 fill必须回绑 exact `ordId/instId`，不属于目标订单的记录 fail closed。

## Mutation uncertainty / no blind retry

- connect/read timeout、reset、truncated/malformed response、5xx、429、permission、client exception after send与unknown venue code，只要不能证明 venue 未收到 mutation，均为 `UNKNOWN / queryRequired=true`。
- duplicate `clOrdId` `51016` 不能作为“未下单”证明；必须先按 stable identity query。
- 只有明确的 business parameter rejection可 `DEFINITIVELY_REJECTED / queryRequired=false`。
- PLACE/CANCEL retry=`0`；无 blind retry、auto resize、auto reprice或auto notional increase；cancel race同样 query-first。

## Runtime-unbound proof

静态 main-source call graph与 Spring context共同证明：

```text
SpotExecutionProviderPort production worker binding=0
startup mutation=0
scheduled mutation=0
ExecutionIntent → real transport caller=0
```

- 新 transport与authority不是自动 component；production context中 `SpotExecutionProviderPort` bean=0。
- provider/authority无 startup、scheduled或worker mutation reachability。
- `real_provider/private_trading=NOT_IMPLEMENTED` 继续表示真实 runtime尚未接受并启用；class/capability存在不等于 runtime implemented。

## V41 regression 与 architecture

- required PostgreSQL 17.10实际执行 V39/V40→V41、V1→V41、v1历史兼容、v2 `VENUE_NOT_PUBLISHED` 双 NULL、Java/PostgreSQL canonical parity、no fake backfill、append-only、complete set、lock-timeout与atomic rollback，0 skip。
- V41与V1～V40均未在本 review修改；未创建 V42。
- OKX protocol只在adapter；account拥有credential；livecontrol负责编排prerequisite；execution拥有mutation port；JDBC只在infra。
- `nq-api`无SQL/JDBC或infra DTO泄漏；无第二套 SoR；`ModuleBoundaryArchTest` 与 `PackageBoundaryArchTest`通过。

## Side-effect counters

```text
real credential read=0
real OKX API call=0
real PilotScope=0
real OperatorApproval=0
ExecutionIntent=0
ExecutionReceipt=0
PLACE=0
CANCEL=0
TRANSFER=0
WITHDRAW=0
worker start=0
LIVE enable=0
kill disengage=0
```

Synthetic local HTTP server不计作OKX call。Disposable PostgreSQL仅绑定loopback，完成后已停止并由 `--rm` 自动删除。

## Validation

| Command / check | Result | Scope / environment / warnings |
| --- | --- | --- |
| initial adapter/provider focused after remediation | PASS（通过） | 23/23 reactor dependencies；24 tests；failures/errors/skipped=`0/0/0` |
| Spring runtime-unbound focused | PASS（通过） | 23/23 modules；22 tests；failures/errors/skipped=`0/0/0` |
| required PostgreSQL V41 focused | PASS（通过） | PostgreSQL 17.10 loopback；11 tests；failures/errors/skipped=`0/0/0`；V41 path实际执行 |
| expanded trusted observation/provider/HTTP/identity/UNKNOWN/GateY/ArchUnit focused | PASS（通过） | 23/23 modules；143 tests；failures/errors/skipped=`0/0/0` |
| `mvn -f backend/pom.xml test` | PASS（通过） | 23/23 modules；1540 tests；failures/errors/skipped=`0/0/48`；54.437s |
| Codex Security scan | PASS（通过） | coverage=`complete`；15 surfaces；findings=`0`；report sealed |
| `scripts/docs/check-current-authority.ps1` | PASS（通过） | `errors=0`；`GateY-6E / REVIEW_ACCEPTED|READY_TO_COMMIT / UNCOMMITTED / NOT_RUN`；next action一致 |
| `scripts/docs/check-doc-links.ps1` corrected array invocation | PASS WITH HISTORICAL WARNINGS（通过并有历史警告） | `360 checked / 14 warnings / 0 errors`；warnings均为append-only历史路径 |
| `git diff --check` | PASS（通过） | exit=`0`；无whitespace error，仅既有LF→CRLF warning |

48 个 skip为既有conditional/manual integration；required PostgreSQL已单独0 skip实跑。首次focused Maven因PowerShell未引用 `-Dsurefire.failIfNoSpecifiedTests=false` 而exit=`1`，未进入编译；修正引用后通过。Codex Security draft首次因coverage schema版本字段不匹配被输入校验拒绝且未写artifact，按返回schema重提后唯一一次completion成功。scan 的 working-tree-changed warning对应本review内两个P1最小修复；最终工作树已由focused与full backend重验。文档链接脚本按无参任务命令首次exit=`1 / mandatory Roots`，改用脚本要求的array invocation后通过。

## Authority after、风险与回滚

```text
work_batch=GateY-6E
work_batch_status=REVIEW_ACCEPTED|READY_TO_COMMIT
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEY-6E-COMMIT-AND-PUSH

real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
EXACT_PILOT_SCOPE=NOT_MATERIALIZED
FIRST_REAL_ORDER=NOT_AUTHORIZED
MICRO_LIVE=NOT_AUTHORIZED
LIVE=DISABLED
kill_switch=ENGAGED
```

- 本轮未stage、commit、push、deploy；commit/push与exact-head CI属于下一独立动作。
- 风险：transport capability已存在于代码，但production runtime静态与Spring均不可达；任何后续binding必须重新进行独立授权与安全审查。
- 回滚：仅按下方exact changed-file allowlist逐文件反向应用本批次未提交diff；不得使用 `reset --hard`、整仓restore/checkout或覆盖用户已有改动。只回滚本review时，删除本evidence并反向patch本轮current authority与两处transport/test最小修复。
- 建议commit：`feat(gatey): implement first real order prerequisites`。

## Exact changed files / git add allowlist

Review通过后的exact allowlist：

```powershell
git add -- `
  README.md `
  backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/JdkOkxPrivateReadTransport.java `
  backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxJdkRealClient.java `
  backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPilotPrerequisiteRequest.java `
  backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPilotPrerequisiteSnapshot.java `
  backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateHttpExchange.java `
  backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateRealTransport.java `
  backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateRequestSigner.java `
  backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderAdapter.java `
  backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotProviderTransport.java `
  backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/JdkOkxRealTransportTest.java `
  backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateRequestSignerTest.java `
  backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/domain/PilotObservationCanonicalEncoder.java `
  backend/nq-core/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/application/provider/SpotExecutionProviderPort.java `
  backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/JdbcOkxPrivateCredentialExecutor.java `
  backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/okx/readonly/OkxPrivateCredentialExecutor.java `
  backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxCredentialScopedSpotProviderTransport.java `
  backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthority.java `
  backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/okx/readonly/JdbcOkxPrivateCredentialExecutorTest.java `
  backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxCredentialScopedSpotProviderTransportTest.java `
  backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/infra/okx/OkxPilotPrerequisiteObservationAuthorityTest.java `
  docs/current/README.md `
  docs/current/ROADMAP.md `
  docs/current/STATUS.md `
  docs/current/TESTING.md `
  docs/current/WORKLOG.md `
  docs/current/evidence/gate-y/README.md `
  docs/current/evidence/gate-y/NQ-GATEY-6E-FIRST-REAL-ORDER-PREREQUISITE-IMPLEMENTATION.attempt-02.md `
  docs/current/evidence/gate-y/NQ-GATEY-6E-FIRST-REAL-ORDER-PREREQUISITE-SECURITY-REVIEW.attempt-01.md
```
