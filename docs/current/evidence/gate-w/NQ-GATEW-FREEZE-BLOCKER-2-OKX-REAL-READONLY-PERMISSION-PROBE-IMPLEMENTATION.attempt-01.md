# NQ-GATEW-FREEZE-BLOCKER-2-OKX-REAL-READONLY-PERMISSION-PROBE-IMPLEMENTATION — Attempt 01

## Task classification

- 类型：`EXCHANGE_INTEGRATION / SECURITY_IMPLEMENTATION / ATOMIC_METADATA_WRITEBACK / TESTS`。
- 范围：`nq-core / nq-infra / nq-app / nq-adapter-okx`；`nq-adapter-api` 无 diff。
- 当前结论：`PASS / OKX_REAL_READONLY_PERMISSION_PROBE_IMPLEMENTED / ATOMIC_METADATA_WRITEBACK_PROVEN / READY_TO_COMMIT`（通过 / 受控只读 probe 已实现 / 原子写回已证明 / 可提交）。
- commit/push/exact-head CI：`PENDING`；真实 Key、OKX 调用、服务器与 soak：`NOT_RUN / NOT_STARTED`。

## Implementation

- `OkxRealReadonlyPermissionProbePort` 只构造 `OkxPrivateReadRequest.accountConfiguration(expectedIp)`，固定 `PRODUCTION` 环境、`GET /api/v5/account/config`，并要求 request 为 OKX、LIVE credential、PAPER dry-run。
- permission 仅接受 `READ_ONLY`；Read 缺失、Trade/Withdraw 开启、未知 token、缺失字段全部 `FAILED`。只有 storage canonical `SUCCEEDED + READ_ONLY + withdraw=false + PASSED` 对应逻辑 `READ_ONLY_VERIFIED`。
- expected IP 只接受 IPv4/IPv6 literal；拒绝 hostname、CIDR、zone id、contains 和第三方 IP discovery。provider `ip` 必须逐 token 规范化后精确相等；缺失、未知、不匹配全部 fail-closed。
- transport 拒绝 redirect，使用既有 bounded timeout、concurrency=1、response byte cap、无自动 retry；补齐 OKX 业务码与 HTTP `401/403/429/non-2xx` 脱敏分类。
- permission request 已移除 `decryptedPayloadJson`，只携带 server-side owner/account/credential reference 与非敏感控制事实。
- JDBC executor 新增 exact credential-ID selection；count/decrypt 两次 SQL 都绑定同一 owner/account/type/credential ID，并继续限制 active、未撤销、未 rotation、OKX account。
- `CredentialPermissionProbeService` 使用两段 `TransactionOperations` 短事务，HTTP 位于事务外；finalize 单条 SQL 原子写 permission current facts，并以 `IN_PROGRESS` CAS 拒绝并发/重复写回。
- 未产生 permission observation 的认证/网络失败保留最后已知 `permission_scope` 与 `withdraw_enabled`，避免清除既有高风险事实；本次 probe status/error/IP observation 仍按失败写回。
- Spring 默认仍为 `NoRealExchangeCredentialPermissionProbePort`；真实 port 仅在 explicit flag、合法 expected IP、JDBC executor、real-exchange-enabled=true、LIVE/real-client/real-provider/no-outbound=false 全部精确满足时选择。应用启动不自动 probe。

## Files created

- `backend/nq-adapter-okx/.../OkxIpAddressNormalizer.java`
- `backend/nq-adapter-okx/.../OkxIpAllowlistStatus.java`
- `backend/nq-app/.../GateWOkxPermissionProbeProperties.java`
- `backend/nq-app/.../AccountPermissionProbeCompositionTest.java`
- `backend/nq-infra/.../OkxRealReadonlyPermissionProbePort.java`
- `backend/nq-infra/.../OkxRealReadonlyPermissionProbePortTest.java`

## Files changed

- `nq-adapter-okx`：private read request/result/transport/error 与 transport tests。
- `nq-core`：probe request/port/repository contract、两段事务 service 与回归 tests。
- `nq-infra`：exact credential executor、CAS repository writeback 与 tests。
- `nq-app`：fail-closed composition 与 properties。
- Docs：本任务三份 evidence、GateW evidence index、`TESTING.md`、`WORKLOG.md`。

## Validation

| Command | Result | Scope / Environment |
| --- | --- | --- |
| IntelliJ errors-only inspection | `PASS` | 23 个修改/新增 Java 文件，errors=0 |
| `mvn -f backend/pom.xml -pl nq-core,nq-infra,nq-app,nq-adapter-api,nq-adapter-okx -am test` | `PASS` | 23/23 modules `SUCCESS`；`BUILD SUCCESS`；CI/no-outbound/LIVE/AI/DH/real-exchange disabled |
| `mvn -f backend/pom.xml test` | `PASS` | 23/23 modules `SUCCESS`；`BUILD SUCCESS`；同一安全环境 |
| `CredentialPermissionProbeServiceTest` | `PASS` | 13 tests；两段短事务、CAS conflict、risk-fact preservation、LIVE controlled read-only boundary |

已知 warning：既有 SLF4J NOP、Mockito dynamic-agent/JDK future warning；P3、非阻断。前端/Python 无 diff，未运行。真实 PostgreSQL/credential/OKX/服务器/soak 未运行，不得写成通过。

## Boundary confirmation

- 无 Controller/API/scheduler/runner/migration/POM/dependency/交易写能力。
- 无 raw provider response、完整 IP allowlist、credential material、签名或 header 的持久化/日志/audit。
- `tradingAuthorized=false`、LIVE 仍 disabled；本实现只解除 `REAL_PERMISSION_PROBE_PATH_UNAVAILABLE` 代码 blocker，不证明 Key 已配置或真实 permission 已通过。

## Rollback

- 未启动外部 workload，无运行态清理动作。
- 提交后如需回滚，使用 `git revert <IMPLEMENTATION_COMMIT>`；不得 `reset --hard`，不得修改历史 blocked evidence。

## Next action

精确暂存、commit/push 后等待 implementation commit 的 exact-head `NQ CI Baseline` 10/10 GREEN；随后才允许进入 `NQ-GATEW-OKX-READONLY-SOAK-SERVER-DEPLOYMENT-AND-CREDENTIAL-BOOTSTRAP / ATTEMPT_05`。
