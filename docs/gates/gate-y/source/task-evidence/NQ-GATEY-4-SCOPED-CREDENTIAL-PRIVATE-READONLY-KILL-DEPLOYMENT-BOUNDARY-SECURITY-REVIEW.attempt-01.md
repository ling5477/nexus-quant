# NQ GateY-4 scoped credential/private read-only/kill/deployment boundary Security/Operations Review — attempt-01

## Review decision

`PASS / GATEY_4_SECURITY_OPERATIONS_REVIEW_ACCEPTED / P0_0 / P1_0 / SCOPED_CREDENTIAL_BOUNDARY_VERIFIED / PRIVATE_READONLY_BOUNDARY_VERIFIED / KILL_PROPAGATION_VERIFIED / LINUX_STABLE_HANDLE_RACES_VERIFIED / FILESYSTEM_STABLE_HANDLE_CLOSED_FOR_SUPPORTED_LINUX_RUNTIME / IMMUTABLE_WORKER_ADMISSION_VERIFIED / NO_MUTATING_EXCHANGE_CALL / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED / READY_TO_COMMIT`

真实私有只读 smoke 单独结论：`REAL_PRIVATE_READONLY_SMOKE_NOT_RUN / API_KEY_REQUIRED / REMOTE_PERMISSION_NOT_VERIFIED`。本结论不表示 remote permission、IP allowlist、private trading、production deployment、production worker、FIRST_REAL_ORDER、micro-live 或 LIVE 已获授权。

## Review target 与 baseline

- task：`NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-SECURITY-REVIEW`。
- ownership/classification：NQ-only；L 级 independent credential/private-readonly/kill/Linux TOCTOU/deployment admission/security review。
- repository/branch：`F:\project\nexus-quant` / `dev`。
- 起始 `HEAD == origin/dev == 6b5d918c0f90925fce5a6ab4862afbe4cc1522ef`，`git fetch origin` 成功，staged=0。
- authority before：`accepted_batch=GateY-3 / ACCEPTED|CI_GREEN`；`work_batch=GateY-4 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`。
- review object：当前未提交 GateY-4 实际 diff；不以 implementation report 代替代码、脚本与测试审查。
- 明确不涉及：frontend、research、CI workflow、V1～V39、V40+、DH authority、真实 provider、真实 mutating exchange、production deployment/start。

## Evidence inspected

- current authority：`README.md`、`docs/current/{README,STATUS,ROADMAP,TESTING,WORKLOG}.md`、GateY plan/initialization/implementation evidence。
- core/control plane：`livecontrol/deployment/**`、`livecontrol/kill/**`、`strategy/application/release/**` 及对应测试。
- infra/adapter/app：credential JIT executor、OKX private read-only probe、typed endpoint policy evidence、Spring profile/config/no-outbound/architecture tests。
- scripts：`scripts/gatey/verify-gatey4-worker-deployment-boundary.ps1` 与 regression runner。
- Git/migration：实际 diff/name-status/status；V1～V39 tracked diff=0，V40+=0，staged=0。

## Existing security capability reuse 与 authority

| Authority/capability | Disposition | Evidence/decision |
| --- | --- | --- |
| GateW credential repository/JIT executor | `REUSE + EXTEND` | exact credential reference/capability policy 在 JIT 前执行；未建第二 credential store。 |
| `OkxSpotEndpointGuard` | `REUSE` | 继续作为唯一 method/path/query owner；新增 `PrivateReadonlyDiagnosticEndpointContract` 只定义 GateY callable operation set/digest，并由 factory/admission 共用。 |
| `KillSwitchService` durable repository | `REUSE + EXTEND` | 唯一 kill source；envelope/claim/send/admission 均重读 current durable fact。 |
| GateW release verifier / GateX trusted-root verifier | `REUSE + EXTEND` | worker script 委托既有 release verifier；stable handle 在既有 manifest/root contract 后加 verified-open closure。 |
| GateY session/account/credential binding | `REUSE + EXTEND` | exact session/account/owner/reference/type/venue 绑定进入 admission/probe。 |
| 第二 credential/kill/endpoint/release/deployment authority | `NONE` | review 中删除 operation/digest 双重定义，未新增表、migration 或配置事实源。 |

结论：`DUPLICATE_SECURITY_AUTHORITY=0`。deployment admission 是上述既有权威的纯 AND-gate consumer，不获得 credential lifecycle、strategy approval、risk authoring 或 trading authority。

## Credential material 与 capability boundary

- control-plane contract 仅含 credential reference、owner/account/session/venue/type、sanitized capability、permission digest、lifecycle/expiry/revoke/rotation/IP readiness；无 apiKey/secret/passphrase/signature/raw encrypted/decrypted material、Authorization/Cookie 或 raw private payload 字段。
- material 仍只在 infra/JIT session 内短生命周期使用；可变 buffer 在 `finally` 清理，不缓存、不进入 domain、manifest、worker package、CLI/process args、audit metadata、exception 或 logger。
- review 修复 exact-reference executor 的 legacy default fallback：未实现 exact lookup 的 executor 现在 fail-closed 为 `CREDENTIAL_UNAVAILABLE`，不能退化为 account/type 非精确查询。
- `ScopedCredentialCapabilityPolicy` 对任何 `revokedAt` 或 `rotatedAt` 非空均拒绝，即使 ACTIVE flags 与时间字段冲突；expired/stale/wrong owner/account/venue/type/reference、withdraw=true、IP unknown/unverified 均拒绝。
- GateY-4 唯一 callable capability 为 `PRIVATE_READONLY_DIAGNOSTIC`；`FUTURE_MICRO_LIVE` 与 forbidden/trade/funding/transfer/withdraw/margin/leverage/futures/market-order 语义均不可调用。
- 既有 JDBC/Jackson decrypt 路径不可避免地短暂形成 `String`，记录为已知 P2；其生命周期仍限于 infra/JIT session，未因本轮扩散或缓存。

## Endpoint 与 private read-only probe

- probe 只接收 typed config/balance request，不接受 caller supplied URL/path/method/host/query map；`OkxSpotEndpointGuard` 继续拒绝 scheme/authority/fragment、percent encoding、dot segment、alternate slash/backslash、unknown query/path/non-GET。
- `PrivateReadonlyDiagnosticEndpointContract` 集中 exact operation set 与 digest；factory 与 deployment admission 共同消费，消除重复 allowlist authority。
- negative fixtures 仅调用纯 guard，验证 PLACE/CANCEL/TRANSFER/WITHDRAW/funding mutation/unknown operation 不可达，不发送网络请求。
- profile `scoped-okx-private-readonly` explicit opt-in；default/CI/full backend 不装配真实 transport，不存在 fake→real 或 missing-bean fallback。
- probe 精确绑定 session/account/credential reference/type/venue/capability。review 增加 kill exact snapshot re-read：metadata 后、config 前、balance 前均重读 scope/status/version/updatedAt/source；任一变化或不再 `ENGAGED` 时返回 `KILL_SWITCH_CHANGED_DURING_PROBE`，后续 read 不执行。
- sanitized result 不包含 raw provider response。真实 smoke 未运行；remote permission 与 IP allowlist 未现场验证。

## Kill source、propagation 与 race

- 唯一事实源：durable `KillSwitchService` repository。envelope 包含 state、revision/version、stateUpdatedAt、observedAt、source、digest；digest 只作完整性/冲突检测，不声明为数字签名。
- `ENGAGED / UNKNOWN / MISSING / STALE / CONFLICT` 全部拒绝 future claim/send/start；只有 fresh `DISENGAGED`、exact current revision/source/digest 才能进入下一安全 gate，仍不是交易授权。
- envelope acceptance、claim、SEND_STARTED 与 deployment admission 均重新读取 current durable fact，不信 cached `DISENGAGED`。
- regression：claim 成功后 kill 改为 `ENGAGED`，SEND gate 拒绝；admission evidence 建成后 revision 改变，deployment admission 拒绝。

## Stable-handle、consumer side effect 与 memory safety

- supported runtime 前置：Linux、regular file、`SecureDirectoryStream`、逐级 `NOFOLLOW_LINKS`、non-null/stable `fileKey`；任一不满足即 fail-closed，Windows/其他 filesystem 不获 production authorization。
- reader 在既有 trusted-root/manifest 验证后，从同一 stable source handle snapshot/digest；随后复核 source identity、source digest、path/root identity 与最终 manifest closure。
- review 关闭 `POST_VERIFY_SIDE_EFFECT_BEFORE_FINAL_CLOSURE`：生产 API 从 callback 型 `verifyAndConsume` 改为无 callback 的 `verifyAndSnapshot`，只在全部 final closure PASS 后返回 `StableArtifactSnapshotResult`。callback 只能随后经 `consumeVerified` 触发，API shape 上无法在 closure 前产生进程、网络、交易或 durable side effect。
- result 为 thread-bound、one-shot、`AutoCloseable`；不暴露 backing arrays。成功、callback failure、partial consume、验证失败、allocation/IO failure 与 close 都执行清零；重复/跨线程消费拒绝。
- snapshot 总量在分配前和增量读取时均受默认 64 MiB cap；oversize、short/concurrent truncate、OutOfMemory/partial allocation 均 fail-closed 并 best-effort cleanup。
- race matrix 包含 replace-after-verify、rename/swap、parent-directory swap、symlink swap、path traversal、case collision、file↔directory、manifest replacement、digest mismatch、truncate-after-verify、same-path different identity、partial consume、snapshot oversize。

### Linux independent verification

- environment：WSL2 Ubuntu，Linux `6.6.87.2-microsoft-standard-WSL2`；测试临时目录 `/tmp/nq-gatey4-stable-handle-tests` 位于 ext filesystem；不是 production release/current。
- JDK：Adoptium Temurin 21.0.12 官方 GitHub release，官方 SHA-256 校验通过；测试后删除精确临时 cache 目录。
- command：`mvn -o -Dmaven.repo.local=/mnt/d/Tool/Maven/maven-repository -f backend/pom.xml -pl nq-core -am -Dtest=VerifiedOpenStrategyArtifactReaderTest,TrustedRootStrategyArtifactVerifierTest -Dsurefire.failIfNoSpecifiedTests=false -Djava.io.tmpdir=/tmp/nq-gatey4-stable-handle-tests test`。
- result：`Tests run: 14, Failures: 0, Errors: 0, Skipped: 0`；`BUILD SUCCESS`。其中 reader 11/0/0/0、trusted-root verifier 3/0/0/0；前置 test 明确断言 `SecureDirectoryStream`、regular file 与 non-null `fileKey`。
- orchestration evidence：首次文件定位猜测错误未写文件；WSL 实例重启后 `/tmp` JDK 不存在的一次尝试未进入测试；一次 Maven 调度因 Windows localRepository 路径误解释而尝试 Aliyun mirror，TLS/model resolution 失败且未运行测试。最终使用 verified JDK + offline mounted repository 得到上述可归因 PASS。
- disposition：`FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED=CLOSED_FOR_SUPPORTED_LINUX_RUNTIME`；明确不是 universally closed，`OTHER_OS_DEV_RUNTIME_NOT_AUTHORIZED`。

## Immutable worker package、deployment admission 与 scripts

- worker evidence 绑定 source commit、release identity、canonical manifest/artifact digest、verified object identity、trusted root/non-writable、service/process/config/session/credential/account/venue/endpoints/current kill identity。
- symlink、writable/shared-writable、wrong owner/mode、digest/manifest/object/path mismatch、unexpected artifact、credential material、LIVE approval、strategy/risk authoring、arbitrary endpoint 任一出现均拒绝。
- deployment admission 为全条件 AND gate；任一 `MISSING/UNKNOWN/STALE/MISMATCH/CONFLICT` 返回 `START_DENIED`。`tradingAuthorization=false` 在 GateY-4 固定成立。
- script 仅 VERIFY/ADMISSION，委托 GateW verifier并验证 Linux/root/identity；无 `systemctl start/restart/enable`、production `docker run`、真实 network/exchange command、secret env/process args 或 fail-open。
- regression：`PASS / GATEY4_DEPLOYMENT_BOUNDARY_REGRESSION cases=delegate-release,linux-root,identity,no-start,no-secret,no-network`。

## Spring/profile、fake/real 与 architecture hygiene

- `worker-deployment-admission`、`scoped-okx-private-readonly` 都是 explicit opt-in，默认/CI 不启用；排除 OKX/Binance trading adapter、WebSocket 与 real transport，不启动 worker/process/network。
- fake 只存在 test fixture，不注册为 real fallback；no-outbound test 证明 full backend 无真实 HTTP/DNS/socket/credential lookup。
- production naming scan：`backend/**` 中 `GateY4|gateY4|gatey4|GateY-4|GATEY4|nq.gatey4` 为 0；scripts/evidence 允许保留阶段名。
- ownership：credential material→infra/JIT；reference/capability→control-plane；kill→既有 unique owner；endpoint guard→既有 unique owner；deployment admission→livecontrol/deployment；artifact verification→既有 strategy artifact/release owner。
- `ModuleBoundaryArchTest` 与 `PackageBoundaryArchTest` green；无 domain→infra/provider DTO、JDBC→orchestration、script→business state machine。

## Validation

| Command/check | Result |
| --- | --- |
| Linux focused stable-handle | 14/0/0/0，`BUILD SUCCESS` |
| Windows focused backend | core 23、adapter-okx 9、infra 23、app 36；合计 91/0/0/10 skipped；23 modules success |
| script regression | 6/6 cases PASS |
| `mvn -f backend/pom.xml test` | 23/23 modules；1450 tests / 0 failures / 0 errors / 40 skipped；`BUILD SUCCESS` |
| ArchUnit | 12/12 PASS |
| migration | V1～V39 diff=0；V40+=0 |
| backend stage naming | 0 |
| no outbound/side effect | real credential lookup/exchange HTTP/DNS/socket/PLACE/CANCEL/transfer/withdraw/worker start/deploy=0 |
| CI | `NOT_RUN`，不得表述为 CI green |

Known warnings：Maven global settings unknown `profiles`、Mockito dynamic agent、SLF4J no-provider、unchecked/deprecation、Windows LF→CRLF。Windows full-test skips 为既有 environment/manual/integration 与 Linux-only assumptions；Linux acceptance tests 的 relevant skips=0。

## Findings 与 corrections

### P0

- 无。

### P1

- `POST_VERIFY_SIDE_EFFECT_BEFORE_FINAL_CLOSURE`：已通过 post-closure one-shot snapshot API 关闭。
- exact credential reference silent fallback：已改为 default fail-closed 并回归。
- probe config→balance kill race：已在每次网络 read 前重读 exact kill snapshot并回归。
- revoked/rotated lifecycle conflict：已改为任一 lifecycle conflict fail-closed 并回归。
- endpoint operation/digest duplicated authority：已提取唯一稳定 contract 并由 factory/admission 共同消费。
- session venue null handling：已改为 blank-check fail-closed 并回归。
- 最终 open P1：0。

### P2

- `PRODUCTION_LOCK_WINDOW_NOT_MEASURED=OPEN`；GateY-5 production-like volume clone 测量前，production migration deployment/worker/FIRST_REAL_ORDER 继续禁止。
- 既有 JDBC/Jackson decrypt path 的短生命周期 `String`；当前仍限制在 infra/JIT session，mutable material `finally` 清理，无缓存/日志/控制面逃逸。

### P3

- 无。

## Boundary、authority 与 handoff

- authority after：GateY-3 继续 `ACCEPTED|CI_GREEN`；GateY-4=`REVIEW_ACCEPTED|READY_TO_COMMIT / UNCOMMITTED / NOT_RUN`。
- real smoke：`NOT_RUN / API_KEY_REQUIRED`；remote permission=`NOT_VERIFIED`；IP allowlist remote verification=`NOT_VERIFIABLE`。
- security/trading：LIVE=`DISABLED`；production worker start=0；real PLACE/CANCEL/transfer/withdraw=0；micro-live orders=0；未 stage/commit/push/PR/tag/deploy。
- residual blockers：不阻断本次 review/commit handoff；`PRODUCTION_LOCK_WINDOW_NOT_MEASURED` 阻断 production deployment、worker 与 FIRST_REAL_ORDER。CI 仍为 `NOT_RUN`。
- next：`NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-COMMIT-AND-PUSH`。
- commit recommendation：`feat(gatey): enforce scoped credential and deployment boundary`。
