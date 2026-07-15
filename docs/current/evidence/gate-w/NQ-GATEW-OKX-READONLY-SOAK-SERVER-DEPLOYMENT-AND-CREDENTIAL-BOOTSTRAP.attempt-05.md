# NQ-GATEW-OKX-READONLY-SOAK-SERVER-DEPLOYMENT-AND-CREDENTIAL-BOOTSTRAP — Attempt 05

## 1. Task classification

- 执行模式：`ATTEMPT_05 / RESUME_AFTER_PERMISSION_PROBE_IMPLEMENTATION`。
- 类型：`SERVER_DEPLOYMENT / SECURITY_CONFIGURATION / CREDENTIAL_BOOTSTRAP / REAL_OKX_PRIVATE_READONLY_VALIDATION / SEVEN_DAY_SOAK_START / TASK_EVIDENCE`。
- 等级：L 级高风险运行任务。
- 范围：NQ-only；真实网络只允许 OKX `GET /api/v5/account/config` permission/IP hard gate，以及 hard gate 成功后 soak allowlist 中的 `GET /api/v5/account/balance`。
- 尝试时间：`2026-07-15`。
- 最终结论：`BLOCKED / OKX_REJECTED / HTTP_ERROR`（阻断 / OKX 请求未取得可接受结果 / HTTP 错误）。

本 attempt 已完成 fixed commit 部署、credential 密文录入、一次真实 `/account/config` probe、失败 metadata 原子写回与 supervisor self-test；真实 permission/IP hard gate 未通过，因此未调用 balance、未启动 168 小时 soak。不得将 `withdraw_enabled=false` 的保守存储值解释为远端 Withdraw permission 已验证，也不得宣称 GateW 已冻结或七天验收通过。

## 2. Fixed baseline, CI and authority

| Item | Verified result |
| --- | --- |
| Repository | `E:\Project\nexus-quant` |
| Branch | `dev` |
| Local `HEAD` / `origin/dev` | `013620eb95ed88116f8aee209f986fe279d6835f` / exact match |
| Fixed runtime commit | `013620eb95ed88116f8aee209f986fe279d6835f` |
| Fixed commit CI | `NQ CI Baseline` run `29428210696` |
| CI result | `completed / success`；`headSha` exact match；10 jobs；bad=0 |
| Current authority | GateW `IN_PROGRESS / NOT_FROZEN`；GateW-FREEZE `NOT_STARTED` |
| Next authority action | `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION` |

`docs/current/STATUS.md` 继续作为唯一 current authority。本 attempt 不修改 `STATUS.md` 或 `ROADMAP.md`，也不把 fixed runtime commit 或本次失败 probe 写成 accepted batch、freeze candidate 或 release fact。

## 3. SSH, server isolation and outbound prerequisites

- Server：`root@47.251.74.35:22`；hostname=`iZrj9gpab986sm4d0bb6agZ`。
- SSH public-key authentication：`PASS`；NTP synchronized：`PASS`。
- 旧 `sub2api`、`sub2api-postgres`、`sub2api-redis`、`nq-freeze-nginx`、`nq-freeze-app`、`nq-freeze-postgres` 与 `iperf3` workloads 继续停止；未删除 container、volume 或原数据。
- 公网入站应用层复核：NQ `18889` 与 PostgreSQL `55432` 从公网 timeout；只有 SSH `22` 可用。
- Server listener：management app 仅 `[::ffff:127.0.0.1]:18889`；PostgreSQL 仅 `127.0.0.1:55432`。
- OKX HTTPS/DNS outbound 已足以完成一次 `/api/v5/account/config` 请求；未开放任何新入站端口。
- Server `nqgatew` 的 `gh auth status` 仍返回未登录。fixed commit CI 已在本地 GitHub CLI 精确核验，但 server-side supervisor exact-head CI hard gate 仍不可用。

Server isolation：`PASS / SERVER_ISOLATED`。GitHub authentication：`BLOCKED / EXACT_HEAD_CI_NOT_VERIFIABLE_ON_SERVER`。

## 4. Fixed deployment

| Item | Result |
| --- | --- |
| Runtime user | `nqgatew` |
| Deployment directory | `/opt/nexus-quant/gatew-soak` |
| Server sparse repository | detached `013620eb95ed88116f8aee209f986fe279d6835f`；tracked diff empty |
| Executable JAR | `/opt/nexus-quant/gatew-soak/app/nq-app.jar` |
| JAR SHA-256 | local/remote `0972cce1f523932eb625db0470e4f070cbff46ed8502948f3047f18dbd336cb3` |
| Fixed commit bundle SHA-256 | `6f6da99b4da3f78fee33222e9bb7047850f5dae9a9468d0d1e7f1f64d1c5b1c4` |
| Supervisor canonical Git-content SHA-256 | local/remote `git show HEAD`=`d61689f1e9d1fe2f6b448b94301a3ab49ff0cb61e4c72e1fc124dc2c04453cd1` |
| Supervisor remote checkout byte SHA-256 | `b3367365dcb91fdc2432cb78b1382c3b3b8f34019a4b30a20d009f6ef4cf6dc8`；EOL checkout representation，`git diff` 为空 |
| Previous deployment backup | `/opt/nexus-quant/gatew-soak/runtime/attempt-05-backup` |
| Management listener / health | loopback `18889` / `UP` |
| Management PID | `3714561`（2026-07-15 运行中复核值；不是 soak supervisor PID） |

Management app 最终保持 `freeze` profile。`gatew-okx-readonly` 单 profile 缺 datasource；`freeze,gatew-okx-readonly` 组合又因 `OkxRecoveryService` / `AdapterInstrumentCatalogSyncService` 强依赖被该 profile 排除的 `OkxExchangeAdapter` 而无法启动。遵守“不得修改 production code”边界，本次真实 probe 由一次性 operational launcher 直接装配 fixed artifact 内的 production service/repository/executor/port；launcher 完成后已删除。

## 5. Dedicated PostgreSQL and Flyway

- Container：`nq-gatew-postgres`，restart policy 保持 `no`。
- Database：`nq_gatew_okx_readonly_soak`；不复用 Sub2API、开发或生产数据库。
- Listener：仅 `127.0.0.1:55432`。
- Flyway：V1→V35，`35/35` success，latest version=`35`。
- `exchange_account_credentials`、permission probe metadata、`kill_switch_states`、`kill_switch_events` 均存在。
- Kill switch production fact：`GLOBAL_TRADING / ENGAGED / version=1`。

未修改 V31、V35，未手工跳过 migration，未调用 production disengage API。

## 6. Credential bootstrap and encrypted storage

- 用户明确授权读取项目根目录 `.env` 中既有 OKX production key；`.env` 由 `.gitignore` 排除，未进入 `git status` 或 Git。
- 实际使用字段名：`NQ_OKX_REAL_API_KEY`、`NQ_OKX_REAL_API_SECRET`、`NQ_OKX_REAL_API_PASSPHRASE`；值未输出到对话、命令参数、shell history、Git、日志或 evidence。
- 传输方式：本地进程读取后经 SSH stdin 内存传输；未上传 `.env`，未创建 credential 明文临时文件。
- Credential create：通过 localhost-only management API 完成；credential reference=`1`，type=`OKX_API_V5`，status=`ACTIVE`，`isActive=true`。
- DB 聚合验证：rows=`1`；active rows=`1`；non-empty `encrypted_payload` rows=`1`；同类型 active conflict=`0`。
- 禁止的 direct OKX secret environment fields：全部 `ABSENT`。
- 使用本次三项 exact secret 的日志/evidence 扫描：exposure count=`0`；未保存 raw request/response。

Credential bootstrap：`PASS / CREDENTIAL_BOOTSTRAPPED / ENCRYPTED_STORAGE_VERIFIED`。任务结束后应从根目录明文 `.env` 删除这组三项实盘 Key，改用受控本地 secret store；本任务不代替用户执行该本地凭证清理。

## 7. Real permission/IP probe

一次性 operational launcher 从 deployed executable JAR 的 `BOOT-INF/classes` 与 `BOOT-INF/lib/*` 装配以下 production components：

- `CredentialPermissionProbeService`
- JDBC credential/account/audit repositories
- `JdbcOkxPrivateCredentialExecutor`
- `OkxRealReadonlyPermissionProbePort`
- `TransactionTemplate`

网络调用严格限制为一次 `GET /api/v5/account/config`。未调用 `GET /api/v5/account/balance`，也未调用 order、amend、cancel、transfer、withdraw、leverage、position mode、sub-account mutation、private WebSocket 或任何非 GET endpoint。

业务调用及数据库写回已完成；launcher 随后在序列化脱敏结果中的 `Instant observedAt` 时因未注册 Jackson JSR-310 module 退出。DB 是最终事实源；为避免重复真实 private call，本 attempt 未重跑 probe。

| Probe fact | DB result / interpretation |
| --- | --- |
| `credential_id` / type | `1` / `OKX_API_V5` |
| `permission_probe_status` | `FAILED` |
| `permission_scope` | `NULL`；Read permission=`NOT_VERIFIED` |
| `withdraw_enabled` | `false`；仅为保守存储值，Withdraw permission=`NOT_VERIFIED` |
| Trade permission | `NOT_VERIFIED`；不得从 `permission_scope=NULL` 推导 disabled |
| `ip_allowlist_required` | `true`；仅为本地要求 |
| `ip_allowlist_probe_status` | `UNKNOWN`；IP allowlist=`NOT_VERIFIED` |
| `last_permission_probe_error` | `HTTP_ERROR` |
| `last_permission_probe_at` | non-null |
| residual `IN_PROGRESS` rows | `0` |
| audit | `PERMISSION_PROBE_STARTED=1`；`PERMISSION_PROBE_FAILED=1` |

Metadata writeback：`PASS / ATOMIC_FAILURE_METADATA_WRITEBACK`。Credential hard gate：`BLOCKED / OKX_REJECTED / HTTP_ERROR`。没有取得 `READ_ONLY_VERIFIED`，不得写 `Read enabled`、`Trade disabled verified`、`Withdraw disabled verified` 或 `IP matched`。

## 8. Supervisor self-test and soak state

Fixed supervisor self-test 输出：

```text
PASS / SUPERVISOR_SELF_TEST
cases=11
hashChain=PASS
appendOnlySequence=PASS
resumePreservedExistingSamples=PASS
duplicateSequenceRejected=PASS
detachedBranchOutputHandled=PASS
finalSummaryNotGenerated=true
cleanupReleasedTemporaryDirectory=true
noPrivateNetworkCalled=true
```

Self-test 只使用临时目录，不调用 private network；完成后临时目录已清理。

| Field | Result |
| --- | --- |
| RunId | `NONE` |
| StartedAt / PlannedEndAt | `NOT_CREATED / NOT_CREATED` |
| Cadence | planned `900s`；未启动 |
| Soak supervisor PID | `NONE` |
| Evidence directory | planned `/opt/nexus-quant/gatew-soak/evidence/<runId>`；未创建 run directory |
| Manifest count | `0` |
| Initial real sample count | `0` |
| Heartbeat | `NOT_CREATED` |
| Hash chain | self-test `PASS`；real run `NOT_RUN` |
| Forbidden endpoint count | `0` |
| Raw response count | `0` |
| Final-summary count | `0` |

168 小时 soak：`NOT_STARTED`。Permission/IP hard gate 失败时禁止启动；runId、PID、startedAt、plannedEndAt、manifest、sample、heartbeat 均不存在。

## 9. Stop and recovery

- 当前没有 soak run，因此 stop command=`NOT_APPLICABLE`，未伪造或发送 stop request。
- 若未来 run 已存在且触发安全停止，命令模板为：`pwsh -File /opt/nexus-quant/gatew-soak/app/repo/scripts/gatew/gatew-okx-readonly-soak.ps1 -Action failure-stop -RunId <runId>`。
- 脱敏恢复清单：`/opt/nexus-quant/gatew-soak/evidence/recovery-manifest.txt`。
- 当前继续保持旧 workloads 停止；不得执行 `docker compose down -v`、删除 volume、prune 或未经授权的旧 workload 恢复。

## 10. Findings

- P0：无。
- P1：`OKX_REJECTED / HTTP_ERROR`；真实 `/account/config` 未返回可接受 permission/IP observation，Read/Trade/Withdraw/IP 均未验证，168 小时 soak 被 hard gate 阻断。
- P1：`EXACT_HEAD_CI_NOT_VERIFIABLE_ON_SERVER`；`nqgatew` GitHub CLI 未认证，supervisor server-side exact-head CI hard gate 仍不能执行。
- P2：Spring profile composition 不完整；目标 read-only profile 无法同时装配 datasource、real permission probe 与其既有依赖，management app 只能保持 `freeze`，本次靠一次性 launcher 完成受控 probe。启动 soak 前需形成受支持的 runtime composition。
- P2：服务器内存余量有限；当前保守 JVM 与 2 GiB swap 可支持管理单元，但未被 168 小时运行证明。
- P2：supervisor canonical Git content 与 remote checkout byte hash 因 EOL representation 不同；tracked diff 为空、自测通过，但正式启动前应固定传输字节口径并复核一致 hash。
- P3：一次性 launcher 缺 Jackson JSR-310 module，导致写回后脱敏结果序列化失败；未影响 DB 最终失败事实，但 blocker remediation 应覆盖 operational result serialization。

## 11. Boundary, rollback and next action

- 未修改 backend production code、migration、Controller、scheduler、frontend、research、CI workflow、permission probe contract、endpoint allowlist、V31 或 V35。
- 未开启 LIVE、Shadow trading、real order submission、transfer、withdraw、AI、DH runtime、Integration runtime 或 private trading。
- 唯一真实 OKX call 为 `GET /api/v5/account/config`；没有资金、订单、持仓或账户标识写入 evidence。
- Attempt-01/02/03/04 保持原样；本 attempt 不覆盖历史 blocker。

本地文档回滚：删除本 attempt 文件，移除 GateW evidence index 对应行，并回退本轮 `TESTING.md` / `WORKLOG.md` append；不得使用 `git reset --hard`。服务器部署回滚：使用 `/opt/nexus-quant/gatew-soak/runtime/attempt-05-backup` 与 recovery manifest，在独立授权下恢复；不得删除数据库 volume 或 credential evidence。

下一具体动作应是独立 blocker-remediation：先在不重跑真实 probe、不启动 soak 的前提下，调查服务器到 OKX `/account/config` 非 2xx 的脱敏原因，并修复受支持的 Spring runtime composition、server-side GitHub Actions read authentication 与 launcher JSR-310 serialization；形成新 exact-head CI 后再由新 attempt 执行一次 permission/IP hard gate。当前 authority 保持 `GateW=IN_PROGRESS|NOT_FROZEN`、`GateW-FREEZE=NOT_STARTED`、`next_action=NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION`。
