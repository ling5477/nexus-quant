# NQ-GATEW-OKX-READONLY-SOAK-SERVER-DEPLOYMENT-AND-CREDENTIAL-BOOTSTRAP — Attempt 06

## 1. Task classification

- 执行模式：`ATTEMPT_06 / RESUME_AFTER_RUNTIME_REMEDIATION`。
- 类型：`SERVER_DEPLOYMENT / EXISTING_ENCRYPTED_CREDENTIAL_REUSE / REAL_OKX_PRIVATE_READONLY_VALIDATION / ATOMIC_PERMISSION_METADATA_VERIFICATION / SEVEN_DAY_SOAK_START / TASK_EVIDENCE`。
- 等级：L 级高风险运行任务。
- 范围：NQ-only；permission hard gate 只允许一次真实 `GET /api/v5/account/config`，hard gate 通过后 soak 只允许 `GET /api/v5/account/config` 与 `GET /api/v5/account/balance`。
- 执行日期：`2026-07-16`。
- 最终结论：`BLOCKED / REAL_OKX_READONLY_PERMISSION_VERIFIED / ATOMIC_METADATA_WRITEBACK_VERIFIED / SOAK_LAUNCHER_FAILED / REAL_OKX_READONLY_SOAK_NOT_STARTED`（阻断 / OKX 只读权限已验证 / 原子 metadata 写回已验证 / soak launcher 失败 / 真实 soak 未开始）。

本 attempt 成功关闭了 server exact-head、runtime composition、credential permission/IP 与原子 metadata hard gate；supervisor self-test 也为 15/15 PASS。唯一 `start` attempt 在首个真实 soak cycle 的脱敏 evidence 写入阶段触发 `SOAK_LAUNCHER_FAILED`，随后自动重新 ENGAGE kill switch，未创建 supervisor/run-loop。不得把 manifest 的 `startedAt` 写成 168 小时 soak 已启动，也不得宣称 GateW 已冻结或七天验收通过。

## 2. Fixed baseline, CI and authority

| Item | Verified result |
| --- | --- |
| Repository / branch | `E:\Project\nexus-quant` / `dev` |
| Local `HEAD` / `origin/dev` | `c758b875093ae7a76efb233904a1105f59d451ab` / exact match |
| Fixed runtime commit | `c758b875093ae7a76efb233904a1105f59d451ab` |
| Fixed commit CI | `NQ CI Baseline` run `29503663554` |
| CI result | `completed / success`；`headSha` exact match；10 jobs；bad=0 |
| Current authority | GateW `IN_PROGRESS|NOT_FROZEN`；GateW-FREEZE `NOT_STARTED` |
| Authority next action | `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION` |

`docs/current/STATUS.md` 继续作为唯一 current authority。本 attempt 不修改 `STATUS.md` 或 `ROADMAP.md`，不把真实 permission 成功或 blocked run 写成 accepted batch、freeze candidate、release、tag 或七天 acceptance fact。

## 3. SSH, server isolation and GitHub authentication

- Server：`root@47.251.74.35:22`；hostname=`iZrj9gpab986sm4d0bb6agZ`；public-key/BatchMode SSH=`PASS`。
- NTP：`NTPSynchronized=yes`；root disk 40 GiB / available 26 GiB；pre-start memory available约 835 MiB；2 GiB swap、used=0。
- 公网 listener：仅 `0.0.0.0:22` 与 `[::]:22`；management app仅 `[::ffff:127.0.0.1]:18889`；PostgreSQL仅 `127.0.0.1:55432`。
- 旧 `sub2api*`、`nq-freeze-*`、`sing-box` containers保持 stopped；`iperf3` 为 `inactive / disabled`；未恢复、删除或 prune旧 workload/volume。
- `nqgatew` GitHub auth file owner/group/mode=`nqgatew:nqgatew/600`，canonical token entry count=1；authenticated `/user` 为 HTTP 200、rate limit=5000、OAuth scope header为空。
- Server-side `gh run view 29503663554`：`completed / success / c758b875... / 10 jobs / bad=0`。

Server isolation：`PASS / SERVER_ISOLATED`。Server GitHub authentication：`PASS / SERVER_EXACT_HEAD_CI_VERIFIED`。任何 token 片段均未写入本 evidence。

## 4. Fixed artifact and runtime composition

| Item | Result |
| --- | --- |
| Runtime user | `nqgatew` |
| Deployment | `/opt/nexus-quant/gatew-soak` |
| Server repository | detached `c758b875...`；tracked diff=0 |
| Supervisor Git blob | `dd842be84a64fb6999f315aa628fa7b7d0015102` |
| Supervisor uploaded SHA-256 | `c730d6b2dbddfdef42c77ad80265d8f91788ce77d27b42d4fd029e9a0f012a5c` |
| JAR SHA-256 | `09adc29f44109a238114a94c8c9c754b76cfbf4e7e7eef48ccb43586e2ffeefa` |
| Management app | PID `3746185`；profile=`gatew-okx-readonly-soak`；health/readiness=`UP` |
| Selected permission port | `OkxRealReadonlyPermissionProbePort` loaded；NoReal port not loaded |
| Automatic network on startup | `0`；metadata/audit timestamp与count在 context smoke/restart后不变 |

### Deployment composition RCA and minimal repair

初始 management PID 的启动时间早于 fixed JAR 落盘时间 1 秒，且 owner-only `management.env` 中两个更高优先级 direct properties仍为 false，导致新旧进程都选择 NoReal。loopback-only context introspection给出：

```text
exchangeCredentialPermissionProbePort=NoRealExchangeCredentialPermissionProbePort
GateWOkxPrivateReadonlyConfiguration=NEGATIVE
nq.gatew.okx-private-readonly.enabled found different value
```

本 attempt 先用进程级 override在 `127.0.0.1:18890` 证明 Real port、health UP、OKX endpoint log count=0、DB metadata/audit不变，再原子更新两个布尔配置并受控重启正式 18889：

```text
NQ_GATEW_OKX_PRIVATE_READONLY_ENABLED=true
NQ_GATEW_OKX_PRIVATE_READONLY_PERMISSION_PROBE_ENABLED=true
```

`LIVE/order/transfer/withdraw/AI/DH/real provider/client/exchange` 全部继续为 false。配置与备份均保持 `nqgatew:nqgatew/600`；回滚源为 `/opt/nexus-quant/gatew-soak/runtime/attempt-06-backup/management.env.before-composition`。

## 5. PostgreSQL, credential and pre-probe facts

- PostgreSQL：`nq_gatew_okx_readonly_soak / 127.0.0.1:55432`；Flyway latest=`35`、success=true。
- OKX/LIVE/ACTIVE account count=1；credential rows/ACTIVE/encrypted non-empty=`1/1/1`；same-type active conflict=0。
- Credential reference仅使用脱敏 fingerprint；`key_version=1`；未重新录入、轮换、展示或解密 credential。
- Direct secret schema columns=0；进程 direct OKX secret env non-empty count=0。
- Pre-probe metadata保留 Attempt-05事实：`FAILED / permission_scope=NULL / withdraw=false / ip=UNKNOWN / HTTP_ERROR`；`IN_PROGRESS=0`；STARTED/SUCCEEDED/FAILED audit=`1/0/1`。
- Permission probe前 `GLOBAL_TRADING / ENGAGED / version=1`。

Credential reuse：`PASS / EXISTING_ENCRYPTED_CREDENTIAL_REUSED`。Plaintext或raw provider material未写入命令参数、Git、日志或 evidence。

## 6. Single real permission/IP probe

本 attempt 建立不可重复 server sentinel后，仅通过 localhost authenticated management API触发一次 production permission port。应用内部唯一真实 permission调用为：

```text
GET /api/v5/account/config
```

未在 permission hard gate前调用 balance，且没有重试 permission probe。

| Probe fact | Verified result |
| --- | --- |
| API HTTP category | `SUCCESS_2XX` |
| Credential reference match | `true`；fingerprint匹配 |
| `permission_probe_status` | `SUCCEEDED` |
| Permission | `READ_ONLY`；Read enabled、Trade disabled |
| Withdraw | `false` |
| Expected IP | `47.251.74.35` |
| IP allowlist | `PASSED` |
| Safe error category | `NULL` |
| observedAt / requestId / traceId | 均存在；原值不进入本 evidence |

Permission hard gate：`PASS / REAL_OKX_READONLY_PERMISSION_VERIFIED`。该结果不表示 `TRADE_AUTHORIZED`、`LIVE_READY`、`CAN_TRADE` 或 `ORDER_APPROVED`。

## 7. Atomic metadata writeback

- Post-probe row：`SUCCEEDED / READ_ONLY / withdraw=false / ip_allowlist_required=true / PASSED / error=NULL`。
- `last_permission_probe_at` non-null；`updated_at` 相对 Attempt-05 baseline已推进，作为该 schema现有的 row-version事实；`key_version` 保持 1，未轮换 credential。
- CAS transition：`IN_PROGRESS -> SUCCEEDED`；residual `IN_PROGRESS=0`。
- Audit count：STARTED/SUCCEEDED/FAILED=`2/1/1`；本次各新增 STARTED 1、SUCCEEDED 1，FAILED未新增。
- Latest success audit：`fromStatus=IN_PROGRESS / toStatus=SUCCEEDED / detectedScope=READ_ONLY / ipAllowlistStatus=PASSED`。
- Forbidden audit key shape count=0；raw provider material count=0。

Atomic metadata：`PASS / ATOMIC_METADATA_WRITEBACK_VERIFIED`。

## 8. Supervisor self-test

Fixed supervisor self-test：

```text
PASS / SUPERVISOR_SELF_TEST
cases=15
hashChain=PASS
appendOnlySequence=PASS
resumePreservedExistingSamples=PASS
duplicateSequenceRejected=PASS
detachedBranchOutputHandled=PASS
detachedCommitBlobLookup=PASS
windowsCrlfGitBlob=PASS
linuxLfGitBlob=PASS
uploadedArtifactSha256=PASS
finalSummaryNotGenerated=true
cleanupReleasedTemporaryDirectory=true
noPrivateNetworkCalled=true
```

Self-test前后 persistent run dirs/evidence files/final-summary=`0/0/0`。

## 9. Soak start attempt and blocked state

Supervisor parameter introspection确认 actions=`start/status/resume/stop/failure-stop/evidence-verify/cleanup/run-loop/self-test`。本 attempt使用：

```text
durationHours=168
cadenceSeconds=900
startingCiRun=29503663554
currencyAllowlist=USDT
```

唯一 start attempt创建了 manifest，并执行 bootstrap与首 cycle；SSH前台连接在等待期间断开，但远端同一 start进程继续，未发送第二次 start。最终状态：

| Field | Result |
| --- | --- |
| RunId | `gatew-soak-20260716T145410Z-230ae5be` |
| Manifest startedAt | `2026-07-16T14:55:26.5733341Z`；仅表示 run manifest创建，不表示 soak RUNNING |
| PlannedEndAt | `2026-07-23T14:55:26.5733341Z` |
| Evidence directory | `/opt/nexus-quant/gatew-soak/evidence/gatew-okx-readonly-soak/gatew-soak-20260716T145410Z-230ae5be` |
| Heartbeat | `BLOCKED / SOAK_LAUNCHER_FAILED` |
| Supervisor/run-loop PID | `NONE / process count=0` |
| Durable sample count | `1`；fallback hard-failure record，不是有效 real PASS sample |
| Failure count | `1` |
| Hash chain | `PASS / HASH_CHAIN_VERIFIED` |
| `supervisor.json` | absent |
| `final-summary.json` | absent |
| Kill switch after failure | `GLOBAL_TRADING / ENGAGED / version=3` |
| Secret/raw shape count | `0` |

168 小时 soak：`NOT_STARTED`。不得将 manifest或一次 blocked fallback sample解释为 `REAL_OKX_READONLY_SOAK_STARTED`。

## 10. Root cause of `SOAK_LAUNCHER_FAILED`

固定 commit中的 Java launcher存在自相矛盾的 evidence sanitizer：

1. 成功 real sample要求 transport operations精确为 `OKX_ACCOUNT_CONFIGURATION_READ` + `OKX_ACCOUNT_BALANCE_READ`，并把 `allowedEndpointCategory` 写成 `ACCOUNT_CONFIG_AND_BALANCE_READ`。
2. `writeSanitizedResult` 随后把序列化 JSON中任意 `balance` 子串判为 forbidden，并抛出 `failed to write sanitized cycle result`。
3. Maven cycle因此未产生 `.cycle-*.json`；PowerShell supervisor按设计追加 fallback `SOAK_LAUNCHER_FAILED`，重新 ENGAGE kill switch并阻止后台 run-loop。

代码证据：

- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/gatew/GateWOkxReadonlySoakCycleTest.java:228`：成功条件要求 config + balance 两个 typed read operation。
- 同文件 `:481`：`writeSanitizedResult` 开始。
- 同文件 `:492`：forbidden substring列表包含 `balance`。
- 同文件 `:765`：两个允许 operation映射为 `ACCOUNT_CONFIG_AND_BALANCE_READ`。

这证明只允许的 config/balance operation已进入首 cycle，但其真实结果无法写入 durable sanitized sample；fallback record中的 `networkCalled=false / allowedEndpointCategory=NONE` 只描述 launcher fallback，不可用来反向证明真实 cycle未尝试网络。禁止降低 sanitizer或伪造 sample后继续。

## 11. Findings

- P0：无。
- P1：`SOAK_LAUNCHER_FAILED`；evidence sanitizer拒绝自身允许的 `ACCOUNT_CONFIG_AND_BALANCE_READ` category，导致首个真实 sample无法持久化，168 小时 acceptance clock未开始。
- P1：fallback sample丢失真实 cycle的 network/endpoint outcome，只能证明 supervisor fail-closed，不能作为有效 real sample；必须修复并补 regression后重新 exact-head CI/deploy。
- P2（已关闭）：server management process曾早于 fixed JAR落盘，且两个 direct property=false覆盖 profile；已用 atomic config repair、loopback context smoke和正式重启关闭。
- P2：长时间前台 SSH在 start等待中断开，未来 retry应使用受控 detached wrapper或 service-level start result capture，同时保持 single-attempt sentinel。
- P3：脚本缺少可显示的 comment-based `Get-Help` syntax；本 attempt用 `Get-Command` parameter/ValidateSet introspection取得真实参数，不影响本次 blocker判断。

## 12. Boundary, rollback and next action

- 未修改 backend/frontend/research/scripts/deploy/.github/migration、permission probe contract、endpoint allowlist、V31/V35、Gate archive、`STATUS.md` 或 `ROADMAP.md`。
- 未重新录入/轮换/展示 credential；未启用 LIVE、order/cancel/transfer/withdraw、scheduler、Shadow、AI、DH runtime、real provider/client/exchange写侧。
- Permission hard gate只有一次 `GET /api/v5/account/config`；soak start cycle只允许 typed config/balance reads。没有 POST/PUT/PATCH/DELETE或交易写 endpoint证据。
- Management app继续 loopback-only、Real read-only port loaded、无自动 probe；kill switch保持 ENGAGED。配置回滚源已保留但未执行。
- 当前无 supervisor，stop command=`NOT_APPLICABLE / ALREADY_BLOCKED_AND_ENGAGED`。不得为“停止”再次运行 sample或盲目发送 start。

本地文档回滚：删除本 attempt文件，移除 evidence index对应行，并回退本轮 `TESTING.md` / `WORKLOG.md` append；不得使用 `git reset --hard`。服务器配置回滚：在独立授权下原子恢复 `/opt/nexus-quant/gatew-soak/runtime/attempt-06-backup/management.env.before-composition` 并受控重启 management app；不得删除 PostgreSQL volume或 blocked-run evidence。

下一具体动作：`NQ-GATEW-FREEZE-BLOCKER-1-SOAK-LAUNCHER-EVIDENCE-SANITIZER-REMEDIATION`。只修复 allowed endpoint category与 sanitizer的一致性，补“成功 config+balance sample可写且仍拒绝账户/余额值/raw material”回归，完成 commit/push/exact-head CI和服务器 fixed artifact部署后，再由新的 `ATTEMPT_07`执行一次 start。不得重跑 permission probe；不得进入 `NQ-GATEW-FREEZE-BLOCKER-1-REAL-OKX-READONLY-SOAK-ACCEPTANCE`，因为 168 小时 clock尚未开始。
