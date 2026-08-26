# NQ GateY-4 scoped credential/private read-only/kill/deployment boundary implementation — attempt-01

## 结论

`PASS / GATEY_4_SECURITY_BOUNDARY_IMPLEMENTED / SCOPED_CREDENTIAL_ENFORCED / PRIVATE_READONLY_PROBE_IMPLEMENTED / KILL_PROPAGATION_ENFORCED / FILESYSTEM_STABLE_HANDLE_CLOSED / IMMUTABLE_WORKER_ADMISSION_IMPLEMENTED / NO_MUTATING_EXCHANGE_CALL / PENDING_INDEPENDENT_REVIEW / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED`

远端只读 smoke 单独结论：`REAL_PRIVATE_READONLY_SMOKE_NOT_RUN / API_KEY_REQUIRED`。未发现可由本轮安全使用的既有 credential reference，未读取、创建、轮换或打印任何 credential material。

## Baseline 与范围

- repository/branch：`F:\project\nexus-quant` / `dev`。
- 起始 worktree/staged：clean / empty。
- `HEAD == origin/dev == 6b5d918c0f90925fce5a6ab4862afbe4cc1522ef`。
- baseline CI：run `31659232390`，`NQ CI Baseline / completed / success`，`headSha=6b5d918c...`，10 jobs / bad=0。
- authority before：`accepted_batch=GateY-3 / ACCEPTED|CI_GREEN`；`work_batch=GateY-4 / NOT_STARTED / NONE / NOT_RUN`；next action 为本 implementation task。
- scope：NQ-only；未修改 DH authority、V1～V39、数据库 schema、前端、CI workflow 或 production runtime。

## Audit disposition

| 能力 | 处置 | 说明 |
| --- | --- | --- |
| GateW release/install/verifier | `REUSE` | deployment 脚本委托 `scripts/gatew/verify-gatew-release.ps1`，未建立第二 release authority。 |
| GateW typed endpoint guard/private read transport | `REUSE + EXTEND` | 复用 `OkxSpotEndpointGuard` 与既有 config/balance typed requests；只新增 GateY-4 evidence/composition。 |
| credential store/JIT executor | `REUSE + EXTEND` | 复用既有 repository 与 `JdbcOkxPrivateCredentialExecutor`；只在 JIT material access 前增加 exact reference/capability policy。 |
| durable kill-switch owner | `REUSE + EXTEND` | 唯一事实源仍为 `KillSwitchService`/durable repository；新增 envelope/policy/claim-send re-read gate，无第二表或配置。 |
| GateY-2 session/account/credential binding | `REUSE + EXTEND` | deployment evidence 精确绑定 session、credential reference、account 与 venue。 |
| GateY-3 send-time kill check | `REUSE + EXTEND` | worker operation gate 在 envelope acceptance、claim、send 各阶段重读唯一 kill fact。 |
| 新 credential DB / kill table / endpoint source / deployment authority | `DO_NOT_USE` | 未创建。 |

Migration decision：`V1-V39 unchanged / V40=NONE`。本轮不需要 forward migration。

## 实现证据

### Scoped credential 与 private read-only

- 控制面 record 只持 owner/account/reference/venue/type/capability/lifecycle/permission digest/rotation-revoke/IP readiness；不复制 masked key、raw payload 或 material。
- typed capability 固定为 `PRIVATE_READONLY_DIAGNOSTIC`、`FUTURE_MICRO_LIVE`、`FORBIDDEN`；GateY-4 只有第一项可调用。
- policy 要求 exact `OKX_SPOT`、`OKX_API_V5`、ACTIVE/VERIFIED/SUCCEEDED、fresh remote permission fact、`READ_ONLY` digest、withdraw=false、IP configured 且 remote IP verified。expired/revoked/stale/mismatch/unknown/future 均 fail-closed。
- probe 是显式非默认 profile；先验证 kill=`ENGAGED`、account/env/exact credential reference/type/capability，再进入既有 JIT executor；只执行 reviewed `account/config` 与 `account/balance` typed GET。
- config/permission/IP 不能证明时停止在 balance 前，sanitized observation 不保存 raw private response。default startup、CI、unit/full test 均不调用真实 exchange。

### Endpoint allowlist

- 唯一 source 是既有 `OkxSpotEndpointGuard`；GateY-4 exact operations 为 `OKX_ACCOUNT_CONFIGURATION_READ` 与 `OKX_ACCOUNT_BALANCE_READ`。
- 既有 guard 回归覆盖 unknown capability/path/method、URL/encoded path/dot segment；PLACE/CANCEL/TRANSFER/WITHDRAW 永久 deny。GateY-4 evidence factory 再验证 submission/cancel/transfer/withdraw 全部不可达。
- raw URL、arbitrary path/method、caller-supplied endpoint 均不进入 GateY-4 probe contract。

### Kill propagation

- envelope 绑定 scope/state/version/stateUpdatedAt/observedAt/source/digest；digest 用于完整性/冲突检测，不伪装为签名。
- `ENGAGED / UNKNOWN / MISSING / STALE / CONFLICT` 全部 deny；只有 fresh、current、版本/来源/摘要一致的 `DISENGAGED` 可继续后续 gates。
- claim 后 durable owner 变为 `ENGAGED` 时，SEND 阶段重新读取并拒绝；stale cached DISENGAGED 与 acceptance 前 revision change 均拒绝。

### Stable handle

- 策略：Linux supported runtime 的 verified-open source handle + bounded private immutable snapshot。先复用 GateX trusted-root/manifest verifier，再要求 root/file identity 有 `fileKey`，使用 `SecureDirectoryStream` 逐级 `NOFOLLOW_LINKS` 打开；从同一 source handle 读取并 digest 到不暴露 backing array 的私有内存 snapshot，再复核 source identity/path/root 与最终 manifest closure。snapshot 默认总量 hard cap 为 64 MiB，可配置但不得超过 JVM array 上限，超限 fail-closed。
- 独立 review 已将 API 收口为 `verifyAndSnapshot`：所有 closure PASS 后才返回 thread-bound、one-shot、`AutoCloseable` 的 `StableArtifactSnapshotResult`；callback 只能经返回值的 `consumeVerified` 触发，无法在最终 closure 前产生副作用。成功、失败、partial consume、callback failure 与 `close()` 均清零私有 backing arrays。
- 自审曾发现 consumer 直接读取可变 source handle 会在最终拒绝前观察到原地 truncate/write 后字节；独立 review 又发现旧 callback API 可在最终 closure 前产生不可逆副作用。两项 P1 均已通过 private snapshot + post-closure one-shot result API 关闭，并补充 truncate、callback 不可达、partial/failure cleanup 与总量上限回归。
- race matrix：replace-after-verify/same-path different identity、rename/directory swap、symlink swap、path traversal、case collision、file→directory、directory→file、manifest mismatch、digest mismatch、truncate-after-verify、partial consume 均有测试合同。
- implementation disposition：`SUPPORTED_RUNTIME_CLOSED / OTHER_OS_DEV_RUNTIME_NOT_AUTHORIZED`。本实现阶段 Windows 只验证 fail-closed；后续独立 review 已在 WSL2 Ubuntu 的 Linux ext filesystem 上验证 `SecureDirectoryStream`、`NOFOLLOW_LINKS`、non-null `fileKey` 与 14/14 race/closure tests、0 relevant skips。独立结论见同目录 Security/Operations Review evidence。

### Immutable worker packaging 与 deployment admission

- evidence 绑定 GateW release verifier result、exact commit/release/manifest/artifact digest、root ownership/non-writable、same verified object、immutable package、service/runtime/root/config identity、session/credential/account/venue、typed endpoint policy 与 current kill envelope。
- package 明确拒绝 credential material、LIVE approval、strategy authority、risk-rule authoring 与 arbitrary endpoints。
- 任一 missing/unknown/mismatch/tamper/writable/forbidden/stale 条件返回 `START_DENIED`；`START_ADMITTED` 的 `tradingAuthorization` 仍固定为 false。
- `worker-deployment-admission` 与 `scoped-okx-private-readonly` profile 均显式排除 OKX/Binance trading/WS Bean；configuration 创建 policy/admission objects，不启动 process、worker 或 network。生产类、Bean、profile、property 与 service identity 均使用稳定领域语义，不携带 GateY-4 阶段名。
- deployment script 只做 non-mutating verification，真实模式要求 Linux root 并委托 GateW verifier；输出 start/trading authorization 均为 false。

## 验证

| Command / check | 结果 |
| --- | --- |
| focused `nq-app -am` GateY-4/core/infra/app/ArchUnit/no-outbound | PASS；core 最终 20/0/0/7 skipped，infra 12/0/0，app 36/0/0；memory-snapshot final focused 9/0/0/7 skipped + app 2/0/0；23 modules success |
| `powershell -File scripts/gatey/tests/run-gatey4-deployment-boundary-regression.ps1` | PASS；delegate-release/linux-root/identity/no-start/no-secret/no-network |
| `mvn -f backend/pom.xml test` | PASS；最终 23/23 modules success；1443 tests / 0 failures / 0 errors / 38 skipped；V1→V39 validate，V40=0 |
| ArchUnit | PASS；`ModuleBoundaryArchTest` 与 `PackageBoundaryArchTest` green |
| no-outbound required focused guard | PASS；3/3，real exchange HTTP/DNS/socket/credential lookup/mutating endpoint=0 |
| diff/security scan | PASS；`git diff --check` errors=0；定向检查 secret/path/symlink/TOCTOU/command/env/fallback/endpoint/kill bypass，无 P0/P1 |
| real private read-only smoke | NOT RUN；`API_KEY_REQUIRED`；真实 read call=0、mutation=0 |

既有非阻断 warning：Maven 全局 settings 的未知 `profiles` tag、Mockito dynamic agent、SLF4J no-provider、unchecked/deprecation、Windows LF→CRLF。full test 的 environment/manual/integration skip 真实记录为 38；其中 stable-handle Linux race/snapshot-limit 7 个在 Windows skip。

## Findings、残留与边界

- P0=0；P1=0；P3=0。
- P2：`PRODUCTION_LOCK_WINDOW_NOT_MEASURED` 保留，GateY-5 才可在 production-like volume clone 测量。
- `FILESYSTEM_STABLE_HANDLE_LIMITATION_INHERITED`：GateY-4 implementation disposition 为 `SUPPORTED_RUNTIME_CLOSED`；非 Linux/无 `SecureDirectoryStream` runtime=`NOT_AUTHORIZED`。
- real smoke：`NOT_RUN / API_KEY_REQUIRED`；IP remote fact 未现场验证，故不伪造 remote PASS。
- CI=`NOT_RUN`，独立 Security/Operations Review 未执行；production migration deployment、production worker start、FIRST_REAL_ORDER 均未授权。
- side effects：real PLACE/CANCEL/transfer/withdraw=`0/0/0/0`；production worker started=0；micro-live orders=0；LIVE=`DISABLED`；kill switch=`ENGAGED`。

## Authority after

- accepted：`GateY-3 / ACCEPTED|CI_GREEN` 保持。
- work：`GateY-4 / IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`。
- next：`NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-SECURITY-REVIEW`。
- 未 stage、commit、push、PR、tag、deploy 或启动 production worker。

> Review correction：本文件记录 implementation 阶段结果；独立 review 对 callback API、exact credential fallback、probe kill re-read、lifecycle conflict 与 endpoint contract authority 做了后续最小修复。current 接受事实以 [Security/Operations Review evidence](NQ-GATEY-4-SCOPED-CREDENTIAL-PRIVATE-READONLY-KILL-DEPLOYMENT-BOUNDARY-SECURITY-REVIEW.attempt-01.md) 和 [STATUS.md](../../STATUS.md) 为准。
