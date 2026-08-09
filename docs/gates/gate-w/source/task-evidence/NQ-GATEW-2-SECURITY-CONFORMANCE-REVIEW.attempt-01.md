# NQ-GATEW-2-SECURITY-CONFORMANCE-REVIEW Attempt 01

## 1. Review decision

- Task：`NQ-GATEW-2-SECURITY-CONFORMANCE-REVIEW-CONTINUE`。
- Classification：`SECURITY_CONFORMANCE_REVIEW_CONTINUATION + P1_MINIMAL_FIX + REGRESSION_TESTS + TASK_EVIDENCE`。
- Decision：`PASS / SECURITY_CONFORMANCE_ACCEPTED / READY_TO_COMMIT`（通过 / 安全符合性已接受 / 可进入提交前复核）。
- Findings after fixes：P0=0，P1=0，P2=1，P3=0。
- 本 evidence 只记录脱敏结论；不含 credential、签名材料、认证头、raw request/response、远端 UID 或余额数值。

## 2. Preflight and authority before

- Branch：`dev`。
- Review baseline：`HEAD == origin/dev == 2c7def771b8779c16b98810f09e5758161242ed6`。
- Exact-head CI：`NQ CI Baseline` run `29222532638`，`completed / success`，`headSha` 与 baseline 精确一致。
- Staged：0。
- Authority before：`accepted_batch=GateW-1 / ACCEPTED|CI_GREEN`；`work_batch=GateW-2 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；`next_action=NQ-GATEW-2-SECURITY-CONFORMANCE-REVIEW`。
- Security baseline：`NQ-GATEW-2-SECURITY-REVIEW.attempt-01.md` 对应 commit/CI 已包含在 baseline，未发现官方协议实质漂移。

## 3. Scope authority and comparison

Scope authority source：上一轮 implementation 最终报告中的精确 `git add --` 36 路径清单。正式写入本 review evidence 前按用户指定命令重新计算 `$actual` 并执行 `Compare-Object`。

结果：

```text
expected count=36
actual count=36
extra=0
missing=0
staged=0
Compare-Object output=<empty>
```

### 3.1 Expected paths

```text
backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/EndpointGuardReason.java
backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/EndpointPolicyDecision.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/JdkOkxPrivateReadTransport.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateCredentialContext.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateEnvironment.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateHttpExchange.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadError.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadException.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadOperation.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadRequest.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadResult.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadTransport.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateRequestSigner.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotCapabilityMatrix.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotEndpointGuard.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/JdkOkxPrivateReadTransportTest.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadRequestTest.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateRequestSignerTest.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotCapabilityMatrixTest.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotEndpointGuardTest.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/GateWOkxPrivateReadonlyConfiguration.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfiguration.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/GateWOkxPrivateReadonlyConfigurationTest.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/JdbcOkxPrivateCredentialExecutor.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateCredentialExecutor.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateProbeStatus.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateReadObservation.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateReadonlyProbeService.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/gatew/JdbcOkxPrivateCredentialExecutorTest.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateReadonlyProbeServiceTest.java
docs/current/evidence/gate-w/NQ-GATEW-2-OKX-SPOT-PRIVATE-READONLY-PROBE-IMPLEMENTATION.attempt-02.md
docs/current/evidence/gate-w/README.md
docs/current/GATEW_PLAN.md
docs/current/STATUS.md
docs/current/TESTING.md
docs/current/WORKLOG.md
```

### 3.2 Actual paths

```text
backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/EndpointGuardReason.java
backend/nq-adapter-api/src/main/java/com/guidinglight/nexusquant/adapter/api/model/EndpointPolicyDecision.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/JdkOkxPrivateReadTransport.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateCredentialContext.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateEnvironment.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateHttpExchange.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadError.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadException.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadOperation.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadRequest.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadResult.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadTransport.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateRequestSigner.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotCapabilityMatrix.java
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotEndpointGuard.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/JdkOkxPrivateReadTransportTest.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateReadRequestTest.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxPrivateRequestSignerTest.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotCapabilityMatrixTest.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotEndpointGuardTest.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/GateWOkxPrivateReadonlyConfiguration.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/ExchangeAdapterConfiguration.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/GateWOkxPrivateReadonlyConfigurationTest.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/JdbcOkxPrivateCredentialExecutor.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateCredentialExecutor.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateProbeStatus.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateReadObservation.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateReadonlyProbeService.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/gatew/JdbcOkxPrivateCredentialExecutorTest.java
backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/gatew/OkxPrivateReadonlyProbeServiceTest.java
docs/current/evidence/gate-w/NQ-GATEW-2-OKX-SPOT-PRIVATE-READONLY-PROBE-IMPLEMENTATION.attempt-02.md
docs/current/evidence/gate-w/README.md
docs/current/GATEW_PLAN.md
docs/current/STATUS.md
docs/current/TESTING.md
docs/current/WORKLOG.md
```

本 review evidence 是用户额外明确授权的新文件，不属于上述 implementation 36-path comparison 输入。完成 review 后允许范围为这 36 路径加本文件，共 37 路径。

## 4. Security conformance review

### 4.1 Typed operation and transport

- production operation 仍精确只有 account configuration 与 account balance 两个 GET typed operation；无 raw method/path/host/body/query map。
- global HTTPS host、method、path 与 canonical query 均在 wrapper 内固定；query 只接受 1..3 个 uppercase、去重、排序后的合法币种标识，逗号、空格、百分号、Unicode、`&`、`=` 等输入被拒绝。
- GateW-1 guard 在 transport 前执行；public capability 回归通过，mutating、funds movement、unknown operation/path 永久拒绝。
- redirect 为 NEVER，connect/request timeout 有上限，无 retry，单 transport 并发为 1；response subscriber 在接收过程中执行 256 KiB 上限并在超限时取消 subscription。

### 4.2 Signer and error boundary

- signer 使用构造注入的 `Clock`；UTC ISO-8601 毫秒 timestamp、uppercase GET、最终 path/query 与空 body 的签名输入保持一致，固定向量与 query-in-signature 测试通过。
- credential header 值在进入 JDK header builder 前拒绝 CR、LF、NUL 与其他 ISO control character；异常不回显输入。
- malformed provider JSON、credential JSON、JDBC/decrypt failure 不保留可能携带敏感内容的原始 cause/message；provider body、response byte buffer、credential buffer 在可清理路径被覆盖。
- HTTP/provider/auth/signature/rate-limit/clock-skew/environment/partial 等分类均为脱敏内部 taxonomy；无 raw body/header 透传。

### 4.3 Credential selection and escape boundary

- SQL 以 account join 同时约束 owner、exchange account、`exchange_code='OKX'`、account `ACTIVE`、exact `OKX_API_V5`、`is_active=true`、credential `ACTIVE`、非 revoked、非 rotated；`EXPIRED` 等状态因不是 `ACTIVE` 被排除，无 `LIMIT 1` 或其他 type fallback。
- 0 候选为 `CREDENTIAL_UNAVAILABLE`，1 候选才进入 decrypt，多个候选为 `CREDENTIAL_CONFLICT`；排除态与 conflict 均不解密。
- generic `<T>` callback 已移除。callback 只能返回脱敏 `OkxPrivateReadObservation`，仅获得 thread-bound、callback-bound typed read session；跨线程和 callback 返回后调用均拒绝。credential context 不暴露给 callback，也不能通过返回类型逃逸。
- callback 同步执行；可清理 credential 数组与 context copy 在 `finally`/`close` 清零。构造 context 时先校验全部字段再分配副本，避免部分构造失败遗留副本。

### 4.4 Probe, observation and persistence

- config 始终先于 balance；config 必须是单条、完整且 permission 集合精确为 `READ_ONLY`。Trade、Withdraw、unknown、blank、null、empty、多条或 malformed 均 blocked，balance zero-call。
- balance 仅在 config 安全后执行；空/多条 data、空 details、缺失字段、非数值金额或非法 timestamp 均为 incomplete。public observation 在 incomplete 时 `assetCount=null`，状态为 `PARTIAL`，不伪造 0。
- observation 固定 diagnostic/no-side-effect/not-trading-authorization/LIVE-disabled/order-not-submitted；未发现 account/order/ledger/position/audit/probe metadata 写入或持久化。

### 4.5 Spring and runtime boundary

- private beans 仅在 profile `gatew-okx-readonly`、feature flag true、`nq.env-safety.live-enabled` 显式存在且严格为 false 时装配；LIVE 属性缺失、true 或非法值均不装配。
- default、local、test、CI 不装配 private transport/executor/probe；private profile 不装配 mutating `TradingAdapter` 或 private WebSocket Bean。
- context 创建不执行 probe、decrypt 或网络；未新增 scheduler、runner、startup hook、Controller/API 或 frontend。

## 5. P1 candidates and minimal fixes

八项指定候选全部确认并在原 36 路径内最小关闭；无候选被驳回：

1. LIVE 缺省装配风险：改为属性必须显式存在且为 false，并覆盖 missing/false/true/invalid。
2. Credential SQL scope 不足：加入 account join、owner/OKX/account ACTIVE 与完整 credential lifecycle 约束。
3. Credential/JDBC/decrypt cause 泄漏：统一固定分类且不保留原始 cause/message。
4. Provider malformed JSON cause 泄漏：仅返回固定 `MALFORMED_RESPONSE`。
5. Config 多条 data：要求精确单条；多条一致 read-only 也 fail-closed，危险/未知/空权限均不继续。
6. Header injection：进入 JDK 前拒绝所有控制字符且固定错误文本。
7. Generic callback 逃逸：替换为固定返回 observation 的 scoped typed session，并做线程和生命周期失效控制。
8. Unknown balance 伪造 0：incomplete observation 的 `assetCount` 为 null，状态保持 `PARTIAL`。

审查中同时收紧：非成功 HTTP 路径响应 buffer 清理、真实 subscriber 超限取消、空币种 allowlist 拒绝、balance 单条 schema/空 details/数值完整性、单并发竞争与 JDBC failure 脱敏测试。

## 6. Validation and RCA

### 6.1 Targeted tests

- Adapter boundary targeted：12 tests，0 failures/errors。
- Credential/probe targeted：14 tests，0 failures/errors。
- Response-size：至少一个测试真实驱动 `LimitedBodySubscriber.onNext` 超限，确认 subscription 取消并返回 bounded 内部错误；并保留 transport-level oversize 分类测试。
- Credential escape：reflection 确认 callback 固定返回 observation；跨线程与 callback 到期 session 均被拒绝；credential buffer 在 callback 后清零。

### 6.2 Required Maven

最终源码状态：

```text
mvn -f backend/pom.xml -pl nq-adapter-api,nq-adapter-okx,nq-core,nq-infra,nq-app -am test
BUILD SUCCESS
23/23 reactor modules SUCCESS
Total time: 05:08 min

mvn -f backend/pom.xml test
BUILD SUCCESS
23/23 reactor modules SUCCESS
Total time: 02:30 min
```

保留的真实 RCA：

- 首次 continuation reactor 在 test compilation 阶段发现一个缺失 import；补回测试所需类型 import 后重跑通过。
- 收紧 `ccy` 为必须提供后，partial-balance fixture 仍传空 allowlist，导致预期 `PARTIAL` 实际先 fail-closed 为 `BLOCKED`；只将该 fixture 改为显式 `BTC` allowlist，targeted 14 tests 与两条最终 Maven 命令均通过。
- 两次仅用于快速 targeted 的 PowerShell Maven 参数未完整引用，分别产生 parser/lifecycle 参数错误；改为引用完整 `-D...` 参数后命令通过。这些是命令行调用错误，不是源码编译/测试失败。
- 环境有既存全局 Maven `settings.xml` 未识别 `profiles` 标签警告；不影响本轮 23/23 build success，且该系统配置不在任务范围。

### 6.3 Other verification

- Governance lifecycle/task-evidence policy：PASS；next-action regression：PASS；current authority：`errors=0 / PASS`，明确接受 `REVIEW_ACCEPTED|READY_TO_COMMIT -> COMMIT_AND_PUSH`。
- Current doc links：66 checked，1 个既有 GateJ historical warning，0 errors，PASS。
- Static/security scan：限定 22 个实际 production paths，排除 generated/sensitive 目录；persistence scan 无匹配；startup hook 无匹配；mutating 命中仅为 GateW-1 capability matrix 中永久 deny 的 order rows；logging 命中仅为既有 bootstrap fallback warning，private profile 不装配该 Bean；无 credential/raw provider logging 或 generic private production entry。
- Forbidden scope：frontend、research、scripts、deploy、`.github`、migration、`docs/gates`、`docs/archive`、`.agents`、`backend/pom.xml` diff count 均为 0；`git diff --check` 无错误。
- Final scope：37 paths = 已核准 implementation 36 paths + 本 review evidence 1 path；staged=0。
- Full Maven 的既有 local tests 可访问本机 PostgreSQL；未访问生产数据库，未修改 migration。

## 7. Findings

### P0

- 0。

### P1

- 0（上述候选和审查追加缺口均已关闭并回归）。

### P2

- 1：JDBC/Jackson/JDK API 边界仍需短暂使用不可可靠清零的 immutable `String`（包括配置 master key、解密 JSON 的中间表示及 JDK header value）。生命周期限制在 infrastructure/transport 同步调用内，未进入 core、DTO、cache、event、future、日志、evidence 或数据库；可清理数组均覆盖。彻底消除需超出本批次的 credential storage/driver/HTTP primitive 重构。

### P3

- 0（reviewed diff 内）。

## 8. Immutable evidence verification

写入本 review 前及最终收尾均以 SHA-256 复核以下不可变文件：

```text
NQ-GATEW-2-OKX-SPOT-PRIVATE-READONLY-PROBE-IMPLEMENTATION.attempt-01.md
B0D2F55E96EC1D37FB08AB3C9FCF56E09E9FF264B7F6428287B932F4EEED4444

NQ-GATEW-2-OKX-SPOT-PRIVATE-READONLY-PROBE-IMPLEMENTATION.attempt-02.md
D3CBD5598CED40706C385BCD80AED0EF90705031917A0BF8F2DF27AD9F3E5811

NQ-GATEW-2-SECURITY-REVIEW.attempt-01.md
8FFAFCE3F86B7BEA59B08494F039AFFE4FB4FF81500EEBB0452799B929979DC0
```

## 9. Boundary, rollback and authority after

- `REAL_SMOKE=NOT_RUN`；`API_KEY=NOT_REQUIRED`。未调用 OKX、未读取或使用真实 credential。
- 无 order/cancel/amend/transfer/withdraw；无 LIVE、private trading、Controller/API、frontend、migration、dependency、scheduler/runner 或持久化 observation。
- 本 review 及测试通过不构成 `READY_FOR_LIVE`、交易授权或下单批准。
- Rollback：在未 stage/commit 状态下，仅对本轮 36 个 implementation 路径和本 review evidence 做逐文件反向 patch；不得 reset/clean，不得改动不可变 attempts。Spring rollback 可通过不启用 profile/flag 保持 private beans 不装配。
- Authority after：`accepted_batch=GateW-1 / ACCEPTED|CI_GREEN`；`work_batch=GateW-2 / REVIEW_ACCEPTED|READY_TO_COMMIT / UNCOMMITTED / NOT_RUN`；`next_action=NQ-GATEW-2-COMMIT-AND-PUSH`。
- Commit recommendation：`feat(okx): implement guarded private read-only probe`。
- Next action：`NQ-GATEW-2-COMMIT-AND-PUSH`。本轮未 stage、commit、push、创建 PR 或 tag。
