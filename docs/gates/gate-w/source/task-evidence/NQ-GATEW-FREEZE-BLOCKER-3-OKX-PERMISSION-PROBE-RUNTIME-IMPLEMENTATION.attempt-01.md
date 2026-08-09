# NQ-GATEW-FREEZE-BLOCKER-3-OKX-PERMISSION-PROBE-RUNTIME-IMPLEMENTATION — Attempt 01

## Task classification

- 类型：`SECURITY_REMEDIATION / RUNTIME_COMPOSITION_FIX / SAFE_HTTP_ERROR_CLASSIFICATION / LAUNCHER_SERIALIZATION / SUPERVISOR_HASH`。
- 当前状态：`PASS / RUNTIME_REMEDIATION_ACCEPTED / SERVER_GITHUB_AUTHENTICATED / READY_TO_COMMIT`（通过 / runtime remediation 已接受 / 服务器 GitHub 已认证 / 可提交）。
- Commit：`UNCOMMITTED`；remediation exact-head CI：`NOT_RUN`；服务器新 artifact deployment：`PENDING`。
- 真实 OKX probe 与 soak：`NOT_RERUN / NOT_STARTED`。

## Phase 0 — plaintext secret hygiene

- 服务器 `/root/.env`：`ABSENT`。
- Attempt-05 实际明文源：本机 ignored `E:\Project\nexus-quant\.env`；三个 `NQ_OKX_REAL_*` 字段已通过同卷 `MoveFileEx(REPLACE_EXISTING | WRITE_THROUGH)` 原子替换删除。
- 删除后字段 count=`0`；owner=`LING\Lingyu`；ACL inheritance protected；唯一 allow rule=`LING\Lingyu / FullControl`；临时文件 count=`0`。
- 服务器 `/opt/nexus-quant/gatew-soak/config/management.env`：owner/group=`nqgatew/nqgatew`、mode=`600`，三个字段 count=`0`。
- credential 聚合：rows/ACTIVE/encrypted non-empty=`1/1/1`；management health=`UP`。
- 字段名 count-only scan：root/nqgatew history、management/GateW logs、journal、temp、evidence、deployment config 均为 `0`。
- Attempt-05 已记录 exact-value exposure count=`0`；本轮删除后没有解密 DB credential 重建 secret pattern，因此未制造新的明文副本。Credential rotation：`NOT_REQUIRED_BY_AVAILABLE_EVIDENCE`。

## Existing HTTP_ERROR analysis

可用安全事实：

```text
FAILED|NULL|f|UNKNOWN|HTTP_ERROR|t
PERMISSION_PROBE_FAILED|1|HTTP_ERROR
PERMISSION_PROBE_STARTED|1|NULL
management logs HTTP_ERROR count=0
evidence HTTP_ERROR count=0
safe HTTP status category count=0
```

无法从这些事实确定失败位于 401、403、429、5xx、其他 status、OKX business code 或 response parse；未读取 raw body/header/signature/request/account data，也未重跑 probe。结论：`SAFE_DIAGNOSTIC_INSUFFICIENT`。

## Implementation

- `JdkOkxPrivateReadTransport`：新增 401/403/429/5xx/其他 status 分类；OKX code 只经 allowlist 映射；未知 code 为 `OKX_BUSINESS_REJECTED`；parse/timeout/IO 使用 canonical category；raw response byte array 继续在 `finally` 清零。
- `OkxRealReadonlyPermissionProbePort`：把 transport enum 映射到有限脱敏 probe category；response contract 异常统一 fail-closed，不保存 raw provider message。
- `CredentialPermissionProbeService`：无 permission observation 时保留最后已知 permission scope、withdraw flag 与 IP risk fact；finalize CAS conflict=`VERSION_CONFLICT`，metadata/audit 原子失败=`ATOMIC_WRITEBACK_FAILED`。
- Spring composition：新增 `application-gatew-okx-readonly-soak.yml`；real permission port 只在 explicit profile/flag 与所有安全布尔精确匹配时选择。`CI=true`、`NQ_NO_OUTBOUND=true`、`LIVE=true`、real exchange/client/provider 或交易写侧任一开启都回到 NoReal。
- Java time：生产配置复用 Spring-managed `ObjectMapper`；context regression 覆盖 `Instant / OffsetDateTime / LocalDateTime`。仓库内 test-only soak launcher 已使用 `findAndRegisterModules()` 且既有回归覆盖含 `Instant` 的 sanitized `CycleResult`；Attempt-05 已删除的一次性 launcher 不作为可复用 artifact。
- Supervisor：manifest commit identity 改为 supervisor Git blob object ID；上传字节使用独立 artifact SHA-256；evidence record hash-chain canonicalization 未修改。

## Files created

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/CredentialPermissionProbeWritebackException.java`
- `backend/nq-app/src/main/resources/application-gatew-okx-readonly-soak.yml`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/GateWOkxPermissionProbeSpringContextTest.java`
- 本任务三份 attempt evidence。

## Files changed

- `backend/nq-adapter-okx`：transport/error taxonomy 与 fake HTTP server tests。
- `backend/nq-core`：risk-fact-preserving finalize、atomic/version failure taxonomy 与 tests。
- `backend/nq-infra`：real permission probe canonical mapping 与 tests。
- `backend/nq-app`：explicit soak profile composition、NoReal/Real context tests 与 Java time mapper regression。
- `scripts/gatew/gatew-okx-readonly-soak.ps1`：Git blob/artifact hash 口径与 self-test。
- Docs：GateW evidence index、`TESTING.md`、`WORKLOG.md`。

## Validation

安全环境：`CI=true / NQ_NO_OUTBOUND=true / NQ_AI_ENABLED=false / NQ_DH_RUNTIME_ENABLED=false / NQ_REAL_EXCHANGE_ENABLED=false`。

| Command | Result | Scope |
| --- | --- | --- |
| IntelliJ reformat + errors-only inspection | `PASS` | 相关 Java 文件，errors=0 |
| targeted Maven after RCA rerun | `PASS` | required modules with `-am`；23/23 modules `SUCCESS / BUILD SUCCESS` |
| `mvn -f backend/pom.xml test` | `PASS` | 23/23 modules `SUCCESS / BUILD SUCCESS`；`nq-app` 190 tests、0 failures、0 errors、8 existing skipped |
| supervisor `-Action self-test` | `PASS` | 15 cases；CRLF/LF Git blob、detached commit lookup、uploaded artifact SHA-256、hash-chain 与 no-private-network 全部 PASS |
| `git diff --check` | `PASS` | 无 whitespace error；仅 checkout EOL warning |

测试使用 loopback fake HTTP server；没有访问 OKX、没有读取 credential、没有启动 probe/soak。Frontend/Python 无 diff，未运行。

## Server GitHub authentication

- `nqgatew gh auth status`：`PASS`；active account=`ling5477`，未输出 token。
- `/user` 与 Actions run API：`HTTP 200`，authenticated rate limit=`5000`，`X-OAuth-Scopes` 为空；指定 run `29428210696` 读取为 `completed / success / 013620eb...`。
- `hosts.yml`：`/opt/nexus-quant/gatew-soak/.config/gh/hosts.yml`，owner/group=`nqgatew/nqgatew`、mode=`600`、canonical token entry count=`1`；临时 installer 已删除。
- 兼容性 RCA：`gh 2.45.0` 读取 top-level `oauth_token`；newer nested layout 不生效。最终仅移动已在内存验证的 token到该版本 canonical layout，没有输出 token、没有保留 broad OAuth credential。

Post-commit gates：remediation commit、push、exact-head CI 10 jobs 与 CI-green 后服务器 artifact deployment 尚未执行，不冒充当前已通过。

## Boundary confirmation

- 无新 API/Controller/scheduler/migration/dependency/order/cancel/transfer/withdraw。
- `tradingAuthorized=false`、`liveDisabled=true`；真实 permission probe 未重跑，soak 未启动。
- Authority 保持 GateW `IN_PROGRESS|NOT_FROZEN`、GateW-FREEZE `NOT_STARTED`。

## Next action

创建 commit `fix(account): harden OKX permission probe runtime`，push `dev`，并等待 remediation exact-head `NQ CI Baseline` 10 jobs。
