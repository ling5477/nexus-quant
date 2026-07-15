# NQ-GATEW-OKX-READONLY-SOAK-SERVER-DEPLOYMENT-AND-CREDENTIAL-BOOTSTRAP — Attempt 04

## 1. Task classification

- 执行模式：`ATTEMPT_04 / SERVER_ISOLATION_REMEDIATION_AND_RESUME`。
- 类型：`SERVER_ISOLATION_REMEDIATION / WORKLOAD_SUSPENSION / FIXED_COMMIT_DEPLOYMENT / ISOLATED_POSTGRESQL / CREDENTIAL_BOOTSTRAP / REAL_OKX_PRIVATE_READONLY_VALIDATION / SEVEN_DAY_SOAK_START / TASK_EVIDENCE`。
- 等级：L 级高风险运行任务。
- 范围：NQ-only；只允许 OKX production private read-only，禁止下单、撤单、划转、提现、账户配置修改、AI、DH runtime 与真实交易写侧。
- 尝试时间：`2026-07-15`。
- 最终结论：`BLOCKED / API_KEY_REQUIRED / EXACT_HEAD_CI_NOT_VERIFIABLE / REAL_PERMISSION_PROBE_PATH_UNAVAILABLE`（阻断 / 缺少 OKX credential / 服务器侧无法验证 exact-head CI / fixed commit 不存在受支持的真实 permission metadata 落库路径）。

本 attempt 已完成服务器隔离、fixed artifact 部署、专用 PostgreSQL 与管理单元准备，但未完成 credential bootstrap、真实 OKX probe 或 168 小时 soak 启动。不得将部分完成项写成任务成功、GateW freeze 或七天验收通过。

## 2. Local, fixed baseline and authority

| Item | Verified result |
| --- | --- |
| Main repository | `E:\Project\nexus-quant` / branch `dev` / clean |
| Main `HEAD` | `115e1c8840d2683701b3a8a7c4d629ccbe25dbd5` |
| `origin/dev` | `115e1c8840d2683701b3a8a7c4d629ccbe25dbd5` |
| Fixed worktree | `E:\Project\nexus-quant-gatew-soak-ae73ebc7` / clean / detached HEAD |
| Fixed commit | `ae73ebc79b7bc661513b5968c505f67261b18847` |
| Fixed CI historical evidence | run `29349982797` 曾在 attempt-03 验证为 `completed / success / 10 of 10`；本 attempt 的服务器侧 hard gate 未复核通过 |
| Current authority | GateW `IN_PROGRESS / NOT_FROZEN`；GateW-FREEZE `NOT_STARTED`；`LIVE=DISABLED` |
| Next authority action | `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION` |

`docs/current/STATUS.md` 仍是唯一 current authority。未修改 `STATUS.md`、`ROADMAP.md`、`TESTING.md` 或 `WORKLOG.md`。

## 3. Server isolation remediation

SSH 公钥登录继续通过：server `47.251.74.35`、hostname `iZrj9gpab986sm4d0bb6agZ`、remote user `root`、Ubuntu 24.04.4 LTS。

以下非 GateW workloads 已安全停止并保留：

| Workload | Current state | Original restart/enable state | Current restart/enable state |
| --- | --- | --- | --- |
| `sub2api` | stopped | `unless-stopped` | `no` |
| `sub2api-postgres` | stopped | `unless-stopped` | `no` |
| `sub2api-redis` | stopped | `unless-stopped` | `no` |
| `nq-freeze-nginx` | stopped | `unless-stopped` | `no` |
| `nq-freeze-app` | stopped | `unless-stopped` | `no` |
| `nq-freeze-postgres` | stopped | `unless-stopped` | `no` |
| `iperf3.service` | `inactive / disabled` | `active / enabled` | `inactive / disabled` |

没有删除既有 container、image、network、volume、bind directory 或配置。已保留 Sub2API data/PostgreSQL/Redis mounts、匿名 PostgreSQL volume 与 `/opt/nexus-quant/data/postgres`。

当前 listener：

- 公网：仅 `0.0.0.0:22` 与 `[::]:22`。
- GateW PostgreSQL：仅 `127.0.0.1:55432`。
- GateW management app：仅 `[::ffff:127.0.0.1]:18889`。
- `5179`、`18808`、`18888`、`5201` 的多节点外部检查在停止 workloads 后均为 timeout/refused；Windows `Test-NetConnection` 因本机透明 TCP proxy 返回伪阳性，未作为通过证据。

隔离结论：`PASS / SERVER_ISOLATED`。本任务未修改 UFW、sshd、Docker daemon、云安全组或基础网络服务。

## 4. Resource and dependency result

| Field | Verified result |
| --- | --- |
| Memory after suspension | `MemAvailable` 约 0.8–1.1 GiB；GateW Java RSS 约 266 MiB |
| Swap | 2 GiB，当前未使用；没有新增 swap |
| Root disk | 40 GiB total / 26 GiB available / 32% used |
| Java | OpenJDK `21.0.11` |
| PowerShell | `7.6.3` |
| Maven | `3.8.7` |
| PostgreSQL client | `16.14` |
| Docker / Compose | `29.5.0` / `v5.1.3` |

Java 使用 `-Xms64m -Xmx256m`。已有 swap 且根磁盘余量充足；未升级无关系统组件，未替换 Docker。

## 5. Fixed deployment and management unit

- Runtime user：`nqgatew`。
- Deployment directory：`/opt/nexus-quant/gatew-soak`。
- 子目录：`app/`、`scripts/`、`config/`、`logs/`、`evidence/`、`runtime/`。
- `config/` mode 为 `700`；敏感配置文件 mode 为 `600`，本 evidence 未读取或输出其内容。
- Server sparse checkout：`/opt/nexus-quant/gatew-soak/app/repo`，只包含 `backend` 与 `scripts/gatew`，fixed commit 为 `ae73ebc79b7bc661513b5968c505f67261b18847`，tracked worktree clean。
- Artifact：`/opt/nexus-quant/gatew-soak/app/nq-app.jar`。
- Artifact SHA-256：`a943fdbeb720d9ee6bc06fe3e3883a3368c00ea021dcc083720451da624442c5`，与本地 fixed-worktree 构建产物一致。
- Supervisor：`/opt/nexus-quant/gatew-soak/app/repo/scripts/gatew/gatew-okx-readonly-soak.ps1`。
- Supervisor SHA-256：`b3367365dcb91fdc2432cb78b1382c3b3b8f34019a4b30a20d009f6ef4cf6dc8`。
- Management PID：`3707601`（复检时存活）；health 为 `UP`，只监听 loopback `18889`。
- Profile：`freeze`；`bootstrap-admin=false`。
- LIVE、real order、transfer、withdraw、AI、DH、real provider/client/exchange、marketdata outbound、instrument sync、recovery、WebSocket、validation scheduler 均显式关闭。

本地 fixed worktree 与 Linux server 的 supervisor self-test 均为 `11/11 PASS`；self-test 不调用 private network，不保留临时 run evidence。当前持久 evidence 只有脱敏 `bootstrap-precredential.json`，其结果为 `API_KEY_REQUIRED`、`credentialAccessed=false`、`networkCalled=false`。

## 6. Dedicated PostgreSQL

- Container：`nq-gatew-postgres`，current state `Up`，restart policy `no`。
- Volume：`nq-gatew-postgres-data`。
- Database：`nq_gatew_okx_readonly_soak`。
- Binding：仅 `127.0.0.1:55432`；没有公网发布。
- Flyway：V1→V35，`35/35` success。
- `kill_switch_states` 与 `kill_switch_events` 存在。
- `GLOBAL_TRADING|ENGAGED|1`；默认保持 fail-closed。
- 非控制业务表非空数量：`0`。

PostgreSQL 使用 host network 并显式绑定 loopback，是因为 fixed harness 要求数据库内 `inet_server_addr()` 为 loopback；Docker bridge/NAT 地址会被 hard gate 判定为 `SOAK_DATABASE_NOT_LOCAL`。该选择没有扩大公网暴露。

## 7. Credential bootstrap blocker

- 用户于本 attempt 明确确认：实盘 OKX key 尚未配置。
- 数据库安全聚合查询结果：credential rows=`0`、active rows=`0`、non-empty encrypted payload rows=`0`、active conflict=`0`。
- `ExchangeAccountId=1` 的 OKX/LIVE/ACTIVE account skeleton 已通过受支持的管理 API 创建，但没有 credential row。
- 已准备 masked interactive bootstrap：`/opt/nexus-quant/gatew-soak/scripts/bootstrap-okx-credential.ps1`，owner/mode 为 `nqgatew:nqgatew / 700`；本 attempt 未运行它。
- API Key、Secret、Passphrase 没有进入对话、Git、命令参数、shell history、日志、evidence 或临时文件。

结论：`BLOCKED / API_KEY_REQUIRED`。在 credential 不存在时禁止调用 OKX 或启动 supervisor。

## 8. Exact-head CI blocker

服务器以 `nqgatew` 执行 `gh auth status -h github.com` 返回 `You are not logged into any GitHub hosts`。因此 fixed supervisor 的 `Assert-ExactHeadCi` 无法在实际运行主机验证 run `29349982797`、commit `ae73ebc...` 与 10/10 jobs。

结论：`BLOCKED / EXACT_HEAD_CI_NOT_VERIFIABLE`。历史 attempt 的 CI 成功事实不替代当前 runtime hard gate；不得绕过或手工填入通过状态。

## 9. Real permission/IP metadata path blocker

fixed commit 的代码事实为：

- `AccountModuleConfiguration` 只装配 `NoRealExchangeCredentialPermissionProbePort`。
- `CredentialPermissionProbeService` 对 `tradeEnv=LIVE` 直接返回 `LIVE_CREDENTIAL_BLOCKED`，且只接受 `PAPER + dryRun + paperSafetyConfirmed`。
- `NoRealExchangeCredentialPermissionProbePort` 只返回 `SKIPPED / REAL_EXCHANGE_PROBE_DISABLED`，不会访问 OKX。
- Credential create/rotate API 不接受 `permission_scope`、`withdraw_enabled`、`ip_allowlist_required`、`permission_probe_status` 或 `ip_allowlist_probe_status` 作为客户端输入。
- Fixed soak cycle 在调用 `JdbcOkxPrivateCredentialExecutor` 之前先读取这些 metadata 并执行 fail-closed gate；它不会先调用 `/api/v5/account/config` 再把真实分类回写。

因此当前 fixed commit 没有受支持的真实 `/account/config` permission/IP probe 与 metadata 落库路径。直接 SQL 更新、把默认值当成真实结果、或乐观写入 `READ_ONLY / false / true / SUCCEEDED / PASSED` 都会伪造 hard-gate evidence，明确禁止。

结论：`BLOCKED / REAL_PERMISSION_PROBE_PATH_UNAVAILABLE`。该 blocker 不能通过录入 credential 或服务器配置单独关闭。

## 10. Real probe and soak result

| Field | Result |
| --- | --- |
| Allowed endpoints | 仅 `GET /api/v5/account/config`、`GET /api/v5/account/balance` |
| OKX network calls | `0` |
| Read permission | `NOT_VERIFIED` |
| Trade permission | `NOT_VERIFIED` |
| Withdraw permission | `NOT_VERIFIED` |
| IP allowlist | `NOT_VERIFIED` |
| Real probe | `NOT_RUN` |
| Forbidden endpoint count | `0` |
| Secret exposure count | `0` |
| RunId | `NONE` |
| StartedAt / PlannedEndAt | `NOT_CREATED / NOT_CREATED` |
| Soak supervisor PID | `NONE`；PID `3707601` 是 localhost management app，不是 soak supervisor |
| Manifest / samples / heartbeat | `NOT_CREATED / NOT_CREATED / NOT_CREATED` |
| Hash chain | `NOT_RUN` |
| Final summary | `NOT_CREATED` |

未达到成功状态 `REAL_OKX_READONLY_SOAK_STARTED / SEVEN_DAY_ACCEPTANCE_PENDING`。GateW-FREEZE 继续为 `NOT_STARTED`。

## 11. Recovery and preservation

脱敏恢复清单：`/opt/nexus-quant/gatew-soak/evidence/recovery-manifest.txt`。

当前保持旧 workloads 停止，不执行恢复。若用户明确中止本准备线，应先确认没有 soak run，停止 localhost management app 与 `nq-gatew-postgres`，再按 manifest 恢复原 restart policies、依赖容器、应用、frontend proxy；`iperf3` 仅在明确需要时恢复。不得使用 `docker compose down -v`、删除 volume 或破坏性 prune。

## 12. Findings

- P0：无。
- P1：`API_KEY_REQUIRED`；credential 不存在，真实 permission、IP allowlist 与 private read-only sample 均无法执行。
- P1：`REAL_PERMISSION_PROBE_PATH_UNAVAILABLE`；fixed commit 的 production wiring 只含 no-real port，现有 permission probe 又显式拒绝 LIVE，无法形成可信 metadata 闭环。
- P1：`EXACT_HEAD_CI_NOT_VERIFIABLE`；服务器 `nqgatew` 未认证 GitHub，supervisor runtime CI hard gate 无法通过。
- P2：服务器内存余量有限；当前保守 JVM + 2 GiB swap 可继续准备，但 168 小时运行仍需持续观察 OOM/swap pressure，不能以 swap 代替稳定性证明。
- P3：无。

## 13. Boundary, rollback and next action

- 未修改 backend、frontend、research、scripts、deploy、`.github`、migration、harness、fixed worktree、authority、TESTING 或 WORKLOG。
- 未开启 `LIVE`，未新增 real provider/RealClient/private trading，未下单、撤单、转账、提现或修改 OKX account configuration。
- 未恢复旧 workloads；没有删除任何既有数据或基础设施资源。

如仅撤销本地 evidence，删除本 attempt 文件并移除 GateW evidence index 对应行；不得使用 `git reset --hard`。服务器回滚按脱敏 recovery manifest 执行，且必须由独立明确授权触发。

下一具体动作不是直接启动 soak，而是一个边界明确的 blocker-remediation 任务：为 fixed soak line 提供可审查、只允许 `GET /api/v5/account/config` 的真实 permission/IP probe 与原子 metadata 写回，实现后补回归与 exact-head CI；随后由用户在服务器交互式录入 credential、为 `nqgatew` 配置最小 GitHub Actions read 认证，再重新执行 server bootstrap 与 soak start。credential 不得粘贴到对话中。
