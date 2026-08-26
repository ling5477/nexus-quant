# GateY-6C scoped credential/IP/private permission read-only verification implementation — attempt-04

## Task classification

- 类型：`CONTROLLED_CREDENTIAL_BOOTSTRAP / REAL_PRIVATE_READONLY_VERIFICATION / SECURITY_BOUNDARY_VALIDATION / NO_EXCHANGE_MUTATION`（受控凭证录入 / 真实私有只读验证 / 安全边界校验 / 无交易所变更）。
- 归属：NQ-only / GateY-6C；风险等级 L。
- 日期：2026-08-15（Asia/Shanghai）。
- 结果：`PASS / IMPLEMENTED / PENDING REVIEW`（通过 / 已实现 / 待独立审查）。唯一真实 OKX 请求成功验证 READ、TRADE、WITHDRAW absent 与 IP matched；exchange mutation 始终为 0。

## Starting baseline and authority before

- repository=`E:\\Project\\nexus-quant`，branch=`dev`。
- attempt-03 的 GateY-6C implementation diff 与 focused/GateW/GateY-4/GateY-6B/ArchUnit/full-backend green evidence 全部保留；没有重做或回退。
- staged=`0`；写操作前 `git diff --check` exit=`0`，仅有既有 LF→CRLF 工作区提示。
- authority before：work batch=`GateY-6C / NOT_STARTED / NONE / NOT_RUN`。
- safety baseline：real provider/private trading=`NOT_IMPLEMENTED / NOT_IMPLEMENTED`，LIVE=`DISABLED`，kill switch=`ENGAGED`，`FIRST_REAL_ORDER`/micro-live=`NOT_AUTHORIZED / NOT_AUTHORIZED`。

## Existing secure-path audit and bootstrap

- 复用既有 `ExchangeAccountCredentialController` credential upsert/rotate API 与 Accounts credential drawer；owner 由认证主体解析，响应只返回脱敏 summary。
- 复用 `JdbcExchangeAccountCredentialRepository` 的 master-key encryption 与 `JdbcOkxPrivateCredentialExecutor` JIT callback；未新增临时 credential loader、明文 credential 文件、第二套 secret resolver、signer 或 HTTP client。
- 最终真实验证使用服务器既有 NQ credential-management UI/API 在隔离 runtime 中录入/解析的 exact credential；先前本机 account `900029` / credential `27` 只属于早期 bootstrap 过程，未用于本次服务器真实 probe。
- exact metadata：ownerUserId=`2`、username=`gatew-bootstrap-admin`、exchangeAccountId=`1`、credentialId=`1`、exchange=`OKX`、tradeEnv=`LIVE`、credentialType=`OKX_API_V5`、credentialStatus=`ACTIVE`。
- metadata 查询未选择 `encrypted_payload`，未调用解密表达式，也未输出 key、secret、passphrase、signature、Authorization/header、request body 或 raw response。

## Credential exposure accounting

- OKX credential material access 仅发生在既有 JIT executor callback 内；聊天、终端输出、文档与 evidence 中的 OKX credential material exposure=`0`。
- 辅助 NQ 管理密码曾被误输入普通 PowerShell 并发生一次终端回显：`AUXILIARY_NQ_ADMIN_PASSWORD_EXPOSURE_INCIDENT=1 / ROTATED`。不得把该事件误写为所有凭证暴露均为 0。
- 事件处置后再次轮换并只做脱敏结构校验：`bcryptShape=true`、`enabled=true`、roles=`3`、updatedAt=`2026-08-15T07:53:14.422019Z`；没有读取或记录密码值。
- credential upsert、管理密码轮换、probe audit/writeback 是 NQ control-plane 写操作，不计入 exchange mutation；本报告不将 NQ control-plane mutation count 误写为 0。

## Expected egress IP and isolated runtime

- expected egress IP 来源为 operator 明确提供的受控服务器固定公网出口事实；没有调用公共 “what is my IP” 网站。
- key 已由 operator 绑定到 `47.251.74.35`；同一 literal 配置到 `nq.okx.private-readonly-diagnostics.permission-probe.expected-ip` 的 stable equivalent。
- 隔离 runtime profile=`scoped-okx-private-readonly`，listener=`127.0.0.1:18890`，不对公网监听。
- probe 前再次确认：LIVE/real exchange/real client/real provider/order/transfer/withdraw=`false`，scheduler/Flyway/recovery/WebSocket=`false`，default runtime provider=`NoReal`，`SpotExecutionProviderPort` real transport=`0`，worker real-provider binding=`0`。
- GateW running units=`0`，current release 未更改，`GLOBAL_TRADING=ENGAGED`。

## Runtime composition defect and minimal Java fix

首次隔离启动暴露 `OkxRecoveryService` 在 scoped profile 下仍会注册的明确实现缺陷。按用户边界，仅做以下最小 Java/test 修复：

- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRecoveryService.java`：增加 `@Profile("!scoped-okx-private-readonly")`。
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/config/account/OkxPrivateReadOnlyPermissionProbeSpringContextTest.java`：断言 scoped profile 不注册 recovery/scheduler bean。
- focused regression：`OkxRecoveryServiceTest=2/2`、Spring context=`10/10`，合计 `12 tests / 0 failures / 0 errors`。
- runtime artifact SHA-256=`cd54c3e7ba1953b333ae7d8c57528cb3ceebda57495e68ed229c4eb37af36ca3`。
- 未新增或启用 scheduler、recovery、worker、provider、交易 endpoint 或 mutation transport。

## One-shot real private read-only probe

- helper 固定只允许一次本地 NQ probe request；真实交易所 operation 固定为 `GET /api/v5/account/config`。
- real OKX call count=`1`；retry count=`0`；没有自动 retry。
- 远端只返回到既有 parser/policy，未打印或持久化 raw response。
- 最终脱敏 writeback：`permission_probe_status=SUCCEEDED`、`permission_scope=TRADE`、`withdraw_enabled=false`、`ip_allowlist_probe_status=PASSED`、`failed_auth_count=0`、`last_permission_probe_error=NULL`、`last_permission_probe_at=2026-08-15T07:59:26.328184Z`。

判定：

- Remote READ=`VERIFIED`。
- Remote TRADE=`VERIFIED`。
- Remote WITHDRAW=`ABSENT`。
- IP binding=`MATCHED`。
- `INHERENT_OKX_TRADE_PERMISSION_RESIDUAL=ACKNOWLEDGED`：OKX `TRADE` permission 固有残留不等于 NQ 资金移动授权。
- `NQ_FUNDS_MOVEMENT=DENIED`：NQ 无 transfer/withdraw operation，order/transfer/withdraw flags 保持 false，kill switch 保持 engaged。

## Audit proof and single-call accounting

对远端 PostgreSQL 仅执行 allowlisted、`BEGIN READ ONLY` 的聚合查询；条件限定 credential/account=`1/1`、`2026-08-15 07:59:00Z` 至 `08:00:00Z` 与四种 probe event。未输出完整 JSON、actor/reason、requestId/traceId 或 payload。

- probe events total=`2`。
- `PERMISSION_PROBE_STARTED=1`。
- `PERMISSION_PROBE_SUCCEEDED=1`。
- `PERMISSION_PROBE_FAILED=0`。
- `PERMISSION_PROBE_SKIPPED=0`。
- 成功事件：`permissionExpectation=GATEY_PILOT_READINESS`、`readPermissionDetected=true`、`tradePermissionDetected=true`、`withdrawPermissionDetected=false`、`inherentOkxTradePermissionResidual=true`。
- 查询事务已输出 `ROLLBACK`；其后 heredoc terminator 被 `psql` 当作额外文本产生语法错误，但不改变已返回并回滚的只读结果。此前连接入口识别尝试均在 SQL 前退出。

## Mutation and forbidden-call accounting

- Exchange mutation count=`0`。
- PLACE/CANCEL/TRANSFER/WITHDRAW=`0/0/0/0`。
- borrow/leverage/derivatives/funding mutation=`0`。
- scheduler/startup probe=`0/0`；real provider worker binding=`0`。
- 未开启 LIVE，未解除 kill switch，未启动 GateW unit，未修改 `/opt/nexus-quant/current`。

## Isolation cleanup

- transient unit `nq-gatey6c-attempt04-fixed.service` 已停止并 `reset-failed`；最终 `LoadState=not-found`。
- listener `127.0.0.1:18890` 已关闭；GateW running units=`0`；current release 仍存在。
- remote artifact 已删除：`/opt/nexus-quant/gatey6c-attempt04/cd54c3e7ba1953b`、`/tmp/nq-gatey6c-attempt04-cd54c3e7ba1953b.jar`。
- 本地 SSH tunnel 与 Vite 会话已中断关闭；in-app browser open tabs=`0`。
- 删除只覆盖上述明确临时 artifact；current release、GateW evidence、数据库与 credential record 均未删除。

## Validation

| Command / check | Result | Scope / warning |
| --- | --- | --- |
| 起始 `git status --short` / `git diff --check` / `git diff --stat` | PASS（通过） | attempt-03 diff 保留；staged=0；无无关模块；仅 LF→CRLF 提示 |
| exact credential metadata | PASS（通过） | owner/account/credential/type/status 精确解析；未读取 material |
| pre-probe safety facts | PASS（通过） | scoped profile、expected IP、NoReal、LIVE disabled、kill engaged、全部 mutation flags false |
| authenticated OKX probe | PASS（通过） | 唯一 `GET /api/v5/account/config`；READ+TRADE verified、WITHDRAW absent、IP matched；call=1、retry=0 |
| allowlisted audit query | PASS WITH NON-BLOCKING TRAILING PARSER ERROR（通过，尾随解析错误不阻断） | STARTED/SUCCEEDED=`1/1`，FAILED/SKIPPED=`0/0`；事务已 rollback；无 payload 输出 |
| focused recovery/Spring tests | PASS（通过） | `2+10=12` tests，failures/errors=`0/0` |
| isolated runtime cleanup | PASS（通过） | unit not-found、listener absent、artifact absent、GateW units=0、current release preserved |

attempt-03 已记录 focused/GateW/GateY-4/GateY-6B/ArchUnit/full backend 全绿。本 attempt 未重跑 frontend、Python、migration 或 full backend；它们不在最小 runtime composition 修复范围，且真实 probe 不得重复。

## Findings

- P0：无 open finding。辅助管理密码回显事件已轮换处置并保留事实，不影响 OKX credential material exposure=`0`。
- P1：无。runtime composition 缺陷已做最小修复并由 12 tests 覆盖。
- P2：无。
- P3：无。

## Authority after and final decision

- work batch=`GateY-6C`。
- work batch status=`IMPLEMENTED|PENDING_REVIEW`（已实现 / 待审查）。
- work batch commit=`UNCOMMITTED`；CI run=`NOT_RUN`。
- next action=`NQ-GATEY-6C-SCOPED-CREDENTIAL-IP-PRIVATE-PERMISSION-READONLY-VERIFICATION-SECURITY-REVIEW`。
- real provider/private trading=`NOT_IMPLEMENTED / NOT_IMPLEMENTED`；LIVE=`DISABLED`；kill switch=`ENGAGED`。
- `FIRST_REAL_ORDER=NOT_AUTHORIZED`；micro-live=`NOT_AUTHORIZED`。
- 最终结论：`PASS / READ_VERIFIED / TRADE_VERIFIED / WITHDRAW_ABSENT / IP_MATCHED / SINGLE_REAL_OKX_GET / RETRY_0 / EXCHANGE_MUTATION_0 / NQ_FUNDS_MOVEMENT_DENIED / INHERENT_OKX_TRADE_PERMISSION_RESIDUAL_ACKNOWLEDGED / IMPLEMENTED / PENDING_REVIEW / LIVE_DISABLED`。

## Rollback and next action

- 本轮 product rollback 仅需移除 `OkxRecoveryService` profile guard 与对应 Spring regression；不得回退 attempt-03 其他实现。
- 文档回滚应恢复本 attempt 前的 authority/evidence/ledger 文本；不得使用破坏性 Git 命令覆盖用户改动。
- 下一步只能执行独立 Security Review；不得再次运行真实 probe，不得开始 commit/push、LIVE、first real order 或 micro-live。
- 建议 commit message：`feat(gatey): verify scoped OKX pilot credential readiness`。
