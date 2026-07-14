# NQ GateW-3 LIMIT-only Dry-run Order Preview Implementation Attempt-01

> 日期：2026-07-14
> 前置合同：`PASS / LIMIT_ONLY_INTERNAL_PREVIEW_REVIEW_ACCEPTED`
> 实现状态：`IMPLEMENTED|PENDING_REVIEW`

## 1. Frozen review contract

本实现严格限定为 `OKX + SPOT + BUY|SELL + LIMIT`、internal application service、local persisted facts、read-only、deterministic、fail-closed。MARKET 及 STOP/TRIGGER/ICEBERG/TWAP/POST_ONLY/IOC/FOK 永久拒绝；不提供交易、LIVE、account 或 execution authorization。

## 2. Files changed

Production：

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/instrument/port/InstrumentCatalogReadPort.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/instrument/port/InstrumentCatalogRepository.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/instrument/VenueRuleFreshnessEvaluator.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/orderpreview/DryRunOrderPreviewRequest.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/orderpreview/DryRunOrderPreviewResult.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/orderpreview/DryRunOrderPreviewService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/orderpreview/OrderPreviewFindingCode.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/orderpreview/OrderPreviewStatus.java`

Tests：

- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/marketdata/domain/instrument/VenueRuleFreshnessEvaluatorTest.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/trading/application/orderpreview/DryRunOrderPreviewServiceTest.java`

未修改 `nq-risk`、`nq-infra`、`nq-app` production、migration、Controller/API、frontend、adapter、deploy 或 workflow。

## 3. Architecture

- `InstrumentCatalogReadPort` 只暴露 bounded `findByExchangeAndSymbols`；existing `InstrumentCatalogRepository` 继承该端口，因此现有 JDBC bean 可作为 read port 注入，未新增 repository implementation、SQL 或表。
- `DryRunOrderPreviewService` 的业务依赖只有 `InstrumentCatalogReadPort` 与 `VenueRuleFreshnessEvaluator`。production code 不持有 `TradingAdapter`、provider/HTTP、credential/account/balance、risk、order writer、ledger、audit 或 event port。
- `VenueRuleFreshnessEvaluator.evaluateAt` 以输入 `evaluationTime` 作为比较基准；不替换 `observedAt`、不刷新 snapshot、不写库，使相同输入与 facts 的结果确定。
- 时间复杂度为 O(1)：每次只发起一次 bounded local query，输入固定为一个 symbol；无循环 DB/API 调用、无分页/内存膨胀风险。

## 4. Input / output

Input：`exchange`、`symbol`、`side`、`orderType`、`requestedQuantity`、`requestedLimitPrice`、`evaluationTime`、`traceId`。price/quantity 使用 `BigDecimal`，不接收 credential、URL、raw provider response 或 account balance。

Output 独立给出 `structuralStatus`、`venueFactStatus`、`riskStatus`、`accountStatus`、`executionReadiness`；固定 `diagnosticOnly=true`、`noSideEffect=true`、`orderSubmitted=false`、`executionReadiness=BLOCKED`。结果类型没有 `orderId` 字段。

## 5. Checks and taxonomy

检查 instrument existence、OKX、SPOT、LIVE、local facts completeness/freshness/schema/checksum、positive price/quantity、tick/step alignment、minimum quantity、maximum LIMIT quantity、USDT quote 下 maximum LIMIT USD notional 与 `grossNotional=price*quantity`。

无 maximum fact 时不填 0 或无限大；显式返回 `MAX_LIMIT_QUANTITY_UNKNOWN` / `MAX_LIMIT_NOTIONAL_UNKNOWN`。minimum notional、fee、account permission 保持 unknown；balance 与 stateful risk 保持 not evaluated；execution 始终 `EXECUTION_NOT_AUTHORIZED`。

`blockers`、`warnings`、`unknowns`、`notEvaluated` 四类集合分离。taxonomy 覆盖 attempt-02 冻结的全部 code，并增加 input/type/local-read/max-fact unknown 的精确 fail-closed code。

## 6. Rounding and numeric policy

- 不静默舍入；tick/step mismatch 直接阻断。
- 不提供 normalized execution value 或 suggestion。
- production/test 均未使用 `double` / `float`。
- BigDecimal 对齐使用 exact remainder，数值比较使用 `compareTo`；trailing zero 不改变结论。

## 7. Test evidence

快速模块回归：

```text
mvn -f backend/pom.xml -pl nq-core -am test
Tests run in nq-core: 288
Failures: 0
Errors: 0
Skipped: 0
Reactor: 5/5 SUCCESS
BUILD SUCCESS
```

定向复测（修正 PowerShell `-D` quoting 后）：

```text
mvn -f backend/pom.xml -pl nq-core -am '-Dtest=DryRunOrderPreviewServiceTest,VenueRuleFreshnessEvaluatorTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
DryRunOrderPreviewServiceTest: 37/37 passed
VenueRuleFreshnessEvaluatorTest: 8/8 passed
Total: 45, failures=0, errors=0, skipped=0
BUILD SUCCESS
```

37 个 preview tests 覆盖用户要求的 32 项场景，并补充 local-read exception、null input、安全 invariant、schema missing/unsupported 分支。

最终 targeted 与 full regression：

```text
mvn -f backend/pom.xml -pl nq-core,nq-risk,nq-infra,nq-app -am test
23/23 reactor modules SUCCESS
BUILD SUCCESS

mvn -f backend/pom.xml test
23/23 reactor modules SUCCESS
BUILD SUCCESS
```

两次均设置 `CI=true / NQ_NO_OUTBOUND=true / NQ_AI_ENABLED=false / NQ_DH_RUNTIME_ENABLED=false / NQ_REAL_EXCHANGE_ENABLED=false`。本地 integration tests 只验证 PostgreSQL V34/current schema；OKX base URL 保持 disabled，未调用真实 OKX。Spring test-only 自动生成 development password warning 未写入 evidence 值，也不代表读取真实 credential。

## 8. Zero-call / no-side-effect proof

- dependency-reflection test 证明 service 没有 network/provider/TradingAdapter/credential/account/balance dependency。
- `CountingReadPort` 证明合法请求恰好一次 local read；structural blocker 为零次 read。
- result record reflection 证明无 order ID。
- safety invariant test 阻止构造 `executionReadiness != BLOCKED`、`diagnosticOnly=false`、`noSideEffect=false` 或 `orderSubmitted=true` 的结果。
- 没有 DB write port、transaction、event、audit、ledger 或 order command 引用；不可能从该对象图触发 order transition。

## 9. Authority transition

Governance library 已验证：

```text
COMMITTED|CI_GREEN|CONTINUE_REQUIRED
-> IMPLEMENTED|PENDING_REVIEW

from commit=fd6a8b2044891fa7edfcba7b5a31cd6dc8636b28
from CI=29260881801
to commit=UNCOMMITTED
to CI=NOT_RUN
work batch=GateW-3 (same)
accepted batch=GateW-2 (same)
next action=NQ-GATEW-3-LIMIT-ONLY-DRY-RUN-ORDER-PREVIEW-REVIEW
result=PASS
```

## 10. Boundaries and rollback

固定边界：NO CONTROLLER / REST / FRONTEND / MIGRATION / PREVIEW PERSISTENCE / NETWORK / OKX HTTP / PRIVATE ENDPOINT / CREDENTIAL / BALANCE FETCH / ORDER SUBMISSION / CANCELLATION / STATE CHANGE / LEDGER / AUDIT / RISK MUTATION / LIVE / SHADOW ENABLE / DH / AI。

回滚方式：在未提交阶段移除新增 `orderpreview` 文件与 `InstrumentCatalogReadPort`，恢复 repository 继承关系和 evaluator/test 增量；提交后使用普通 revert commit 回滚 Commit A，不改写 Git 历史。回滚不会影响 V34 或现有 venue-rule facts。
