# GateY Minimal Live Pilot End-to-End — attempt-01

## 当前结论

`BLOCKED / CURRENT_PILOT_PREREQUISITE_NOT_VERIFIED / NO_REAL_ORDER`（阻断 / 当前pilot前置事实未验证 / 未发送真实订单）。本文件是 implementation→CI→deployment→pilot 的单一持续 evidence；production provisioning已恢复account=1、credential=1且operator固定BTC-USDT，但已部署`c47...` minimal-pilot入口无法在现有禁止部署/代码变更边界内刷新current permission/IP、BTC catalog与bestAsk，因此在credential JIT、OKX与PLACE前fail-closed。

```text
P0=0
P1=2
implementationCommit=b18450d1f3c5407d7b0cabddc12330e4c0cac62e
exactHeadCi=32626468825/completed/success
productionDeployment=PASS_EXACT_HEAD_V42_ACTIVE
activeRuntime=c47c8db317bbbef64989f247b087752bf2b46a3c
activeManifest=de1f52359619e6f38fc4671ec5c091bb5019acf3d4f953e14d402d45f0377c50
operatorPilotParameters=ACCOUNT_1_CREDENTIAL_1_BTC_USDT_BUY_LIMIT_CAP_10
historicalIdentity=OWNER_2_ACCOUNT_1_CREDENTIAL_1
productionSorRecovery=PASS_PROVISIONED
currentPrerequisite=BLOCKED_NOT_COMPOSED
credentialJitReads=0
okxCalls=0
PLACE=0
CANCEL=0
transfer=0
withdraw=0
LIVE=DISABLED
kill=ENGAGED
```

## 实现范围

- 新增 V42 三表：`pilot_execution_leases`、`pilot_execution_lease_intents`、`pilot_execution_lease_events`；全局唯一 lease，单 lease 最多一个 PLACE/一个 CANCEL，lifecycle/optimistic version/append-only/immutability 均由 PostgreSQL 约束。
- 新增 `PilotExecutionLease` domain/port/JDBC/service；默认 kill 为 ENGAGED，只有 exact ACTIVE/CONSUMED lease 可短时 DISENGAGE，reason 精确绑定 lease ID。
- 复用既有 `LiveSessionStateMachine`，单一 authenticated OPERATOR 写 internal approval event，不新增治理状态；旧独立审批能力保留为可选扩展，不再是 minimal pilot hard gate。
- 新增七参数 operator command：exchange account、credential reference、instrument、side、limit price、quantity、configured max notional；程序只计算 notional，不选择任何交易值。
- 新增 default-off scoped OKX composition，只暴露 typed Spot LIMIT PLACE、query、optional CANCEL、order status 与 fills；无 raw endpoint、MARKET、batch、algo、margin、derivatives、transfer、withdraw 或 fallback。
- PLACE 先写 Order/ExecutionIntent、bind+consume lease、durable `SEND_STARTED`，随后最多调用一次 provider PLACE；UNKNOWN 只 query，不 retry PLACE。
- SEND_STARTED 前在同一数据库事务重读 session/kill/lease/account/credential/permission/IP/withdraw/instrument/fee/balance/clock/max-notional facts。
- crash/UNKNOWN 保留 CONSUMED lease并立即 re-engage kill；同参数重入只允许 query-first recovery。既有 trade 也会继续补做幂等 ledger posting，避免 trade insert 成功、ledger 失败后的恢复断点。
- control script 复用 root-owned runtime/secrets env、显式 allowlist 清除额外环境、canonical verifier、manifest identity 与 machine-id SHA-256 server identity；不输出 credential 或 machine-id 原文。

## V42 与数据库审查

- V1～V41 未修改；V42 使用 `SET LOCAL lock_timeout='5s'` 与 `statement_timeout='60s'`。
- 三张表及全部字段均有中文 COMMENT；不保存 credential material、raw provider response、header 或签名。
- PostgreSQL 17.7 随机 schema：4 tests，0 failures/errors/skips；覆盖 V39/V40 upgrade、fresh replay、Flyway validate、lock timeout、append-only/immutability、global single pilot 与 concurrent double PLACE exactly-one。
- 本机 `localhost:5432/nexus_quant` 的旧 V42 草稿为空表；仅本地重建最终全局唯一索引并将 Flyway checksum repair 为 `-1136714581`。未连接或修改生产数据库。

## 验证

| Command / check | Result |
| --- | --- |
| focused Java | PASS（通过）；29 tests，0 failures/errors/skips |
| PostgreSQL required smoke | PASS（通过）；LiveSession fact model 4/4，Flyway continuous 1/1 |
| full Maven | PASS（通过）；23 modules，0 failures/errors；既有条件性 skipped 保留 |
| GateY contracts | PASS（通过）；exact scope 7、minimal pilot 19、release 31、runtime deployment 51、GateY4/GateY5 |
| GateW frozen | PASS（通过）；37/37、12/12、34/34 |
| Java standard | PASS（通过）；release 21 / Spring Boot 3.5.10 |
| Java Shadow | `VIOLATION_FOUND`（仅 Shadow）；官方输出 `NEW_CODE_VIOLATION_COUNT=0`，existing baseline/ruleset expansion 保留 |
| `git diff --check` | PASS（通过）；仅 line-ending 提示，无 whitespace error |

执行历史：首次 focused Maven 因 PowerShell dotted `-D` 参数未加引号而未进入 lifecycle；加引号后通过。首次 PostgreSQL run 暴露一个旧独立审批预期断言，与本轮 single-operator simplification 冲突；精确更新该断言后 4/4 通过。首次本地索引重建在中文 COMMENT 处被 Windows psql encoding 拒绝，事务未确认；随后用 ASCII DDL 精确重建本地索引。主 worktree Shadow 被既有不可读 artifact 干扰，detached 临时 worktree 对同一 dirty source 扫描后确认 new-code=0并已删除。

## Targeted P0/P1 Review

- 第二单：global unique lease + `(lease_id, action)` PK + intent CAS + SEND_STARTED + no-place-retry；P0/P1=0。
- kill 永久开放：lease expiry/send-time re-read/startup/finally recovery + exact reason/session binding；P0/P1=0。
- scope/max-notional/MARKET 绕过：Order/Intent/Binding/Lease/DB observations exact compare，LIMIT-only typed provider；P0/P1=0。
- crash：PLACE 后不确定结果保留 CONSUMED recovery fact，kill 立即 ENGAGED，后续只 query；P0/P1=0。
- reconciliation：client/exchange order identity、price/qty/executed/remainder/fill/fee/final state、trade/ledger/audit 均强制；existing trade 可修复 ledger；P0/P1=0。

## 未完成 hard gates

1. current permission/IP必须通过正式JIT刷新并落为`SUCCEEDED / TRADE / PASSED`；当前为`NOT_PROBED / NULL / NOT_CHECKED`，已部署minimal-pilot不调用permission probe。
2. BTC-USDT catalog与current bestAsk必须通过正式current-fact入口刷新；当前catalog row=0，已部署profile禁用catalog sync，minimal-pilot CLI仍要求外部预填price/quantity且prerequisite snapshot没有bestAsk。
3. 只有以上P1关闭、重新通过exact-head CI/部署并验证实时bestAsk/venue rules/fee/balance/clock与notional约束后，才允许exactly-one real LIMIT；本文件当前不声明真实pilot PASS。

推荐 commit：`feat(gatey): add crash-safe minimal live pilot execution`。

## Deployment incident（2026-08-23）

- exact-head CI run `32626468825`：10/10 jobs success。
- canonical release：release/source=`b18450d1f3c5407d7b0cabddc12330e4c0cac62e`，manifest=`d040140dade2a2f5059659ed030d44d0ca737bc761ff116b385aeb15854ba28d`，15 artifacts，schema target V42。
- server preflight：current=`1292b0e49d62ebb5f3f3809be75c6216b66277f8`，manifest=`e5895f47...cee3f`，14 artifacts，V41；systemd active/running、MainPID=`1060386`、NRestarts=0；health UP、kill ENGAGED、mutationRuntimeBound=false、tradingAuthorization=false。
- DB read-only aggregate：V41/failed0，session/scope/observation/intent/receipt/order/trade/ledger均0，V42 table absent。
- immutable install：new release installed/verified，POSIX/link integrity/service-user write denial通过；未切current。
- backup：`/var/lib/nexus-quant/gatey-readonly-qualification/backups/pre-v42-b18450d1.dump`，root:root/0600/links1，bytes=`676787`，SHA-256=`952ad83f8d82560b79c83678240640f672481e18e33e971c902d586b2912ddac`，`pg_restore --list`通过。
- incident：第一次 JShell命令因classpath wildcard被shell展开，把JAR误当source files并产生大量解析输出；终止后第二次正确引用的只读 `Flyway.info()` 会话无输出并被远端关闭。随后SSH三次连续banner timeout；TCP 22一度仍可连接。
- 未执行：任何代码路径中的 `Flyway.migrate()`、V42 DDL、current pointer切换、systemd restart/activation、credential JIT、OKX、PLACE/CANCEL、transfer/withdraw。
- 最后已验证安全状态是在incident前：旧release运行、V41、LIVE=false、kill=ENGAGED。incident后因SSH不可达，不能把这些值伪装成最新已复核事实。
- 恢复要求：通过云控制台/带外通道确认主机，终止残留JShell/bash/ssh诊断进程，核验旧runtime与V41事实；若任何状态漂移立即继续fail-closed。禁止直接重试migration或activation。

## Final execution continuation（2026-08-24）

- SSH：用户仅提供 IdentityFile path reference；只验证文件存在且非空，未读取 key content。`hostname` 与 accepted host 一致；无 JShell 进程，长期独立 Java session 不属于本 incident，未误杀。
- 恢复复核：旧 current=`1292b0e4...`、V41/failed0、kill=ENGAGED、health UP；session/scope/observation/intent/receipt/order/trade/ledger=`0`。
- exact-head release：从 `c47c8db317bbbef64989f247b087752bf2b46a3c` 构建 15 artifacts，manifest=`de1f52359619e6f38fc4671ec5c091bb5019acf3d4f953e14d402d45f0377c50`、schema target=V42；local/server source/installed verifier、POSIX、link integrity 与 service-user write denial 全部 PASS（通过）。未激活 `b18450d1...` candidate。
- V41 backup：既有 `pre-v42-b18450d1.dump` 复核为 root:root/0600/links1、bytes=`676787`、SHA-256=`952ad83f8d82560b79c83678240640f672481e18e33e971c902d586b2912ddac`，`pg_restore --list` PASS（通过）。
- migration：manifest-bound 42-file closed set/hash通过；pinned Flyway image digest=`sha256:782c5c207ffb5ac6336139fda4f4295bd9991ef63ad36919406d4268740069bb` 执行唯一一次 V41→V42，随后 validate PASS；current/pending/failed=`42/0/0`，三张 V42 表均存在且计数0。临时 Flyway env-file 已删除，未输出 DB credential material。
- activation：旧 runtime canonical Stop 后 atomic activation 的 SSH controller连接变为 UNKNOWN；未重试 activation，而是 query/reconcile 确认 current、unit、PID、listener 与三项 runtime binding 均已切到 exact HEAD。独立 UnitPreflight/Health PASS，MainPID=`1135142`、NRestarts=0、LIVE=false、kill=ENGAGED、mutationRuntimeBound=false、tradingAuthorization=false。
- production SoR：`exchange_accounts=0`、`exchange_account_credentials=0`；历史 LIVE `orders/trades` instrument candidates=0。因此同时命中 `ACTIVE_ACCOUNT_OR_CREDENTIAL_NOT_FOUND` 与 `PILOT_INSTRUMENT_SELECTION_REQUIRED`，不得让 operator 重填不存在的 ID，也不得随机选择币种。
- 最终副作用：live session/lease/lease intent/lease event/execution intent/execution receipt/order/trade/ledger/audit 均为0；credential JIT/OKX/PLACE/CANCEL/transfer/withdraw=`0/0/0/0/0/0`。未生成 clientOrderId、idempotencyKey、requestId 或 traceId；kill保持ENGAGED。
- Final decision：`BLOCKED / ACTIVE_ACCOUNT_OR_CREDENTIAL_NOT_FOUND / PILOT_INSTRUMENT_SELECTION_REQUIRED / NO_REAL_ORDER / EXACT_HEAD_RUNTIME_HEALTHY / V42_PENDING_0_FAILED_0 / LIVE_DISABLED / KILL_ENGAGED / NO_PLACE_RETRY / NO_TRANSFER / NO_WITHDRAW / P0_0 / P1_0`。

## Production SoR restore continuation（2026-08-24）

- Git/runtime baseline：`HEAD == origin/dev == 7a08c2202017d0de765d9175320c40bad81b722b`，worktree clean；server current仍为`c47c8db3...`，V42/failed0、health UP、LIVE=false、kill=ENGAGED，account/credential/session/lease/intent/receipt/order/trade/ledger/audit均为0。
- historical account source：GateY-6C accepted real probe evidence与仍可查询的GateW dedicated PostgreSQL精确一致；唯一历史identity为ownerUserId=2、exchangeAccountId=1、OKX/LIVE/ACTIVE。未使用早期本机bootstrap account `900029`。
- historical credential source：同一历史DB只有credentialId=1，绑定account=1，type=`OKX_API_V5`、ACTIVE、permission probe SUCCEEDED、scope TRADE、withdraw=false、IP PASSED、keyVersion=1，encrypted payload非空且未撤销/未轮换。只读取metadata与payload存在布尔值，未读取或输出material。
- secure-store check：current root-only secrets中master-key字段存在且非空，runtime keyVersion=1；该事实只证明安全引用仍存在，不授权复制historical ciphertext、直接解密或绕过Credential Service。
- canonical recovery review：`ExchangeAccountCommandService`可创建account；`ExchangeAccountCredentialCommandService.upsert/rotate`与API只接受plaintext credential material并在当前DB重新加密。仓库没有historical encrypted credential import、跨DB recovery或owner identity restore合同；current DB users=1、roles=3、user_roles=0，historical ownerId=2不存在。直接SQL复制user/account/credential会绕过owner、审计、验证与幂等边界，明确未执行。
- instrument source：historical GateW config仅证明`NQ_GATEW_SOAK_CURRENCIES=USDT`；historical orders/trades=`0/0`；GateY-6C与accepted GateW real evidence中没有明确`*-USDT` instrument。可信候选数=0，不能把USDT balance probe推导成BTC/ETH/SOL交易对。
- production writes：account create=0、credential upsert/rotate=0、raw SQL mutation=0；credential JIT/OKX/PLACE/CANCEL/transfer/withdraw=`0/0/0/0/0/0`。未创建session、binding、lease或订单identity，kill保持ENGAGED。
- Final decision：`BLOCKED / PRODUCTION_ACCOUNT_CREDENTIAL_REPROVISION_REQUIRED / PILOT_INSTRUMENT_SELECTION_REQUIRED / NO_REAL_ORDER / HISTORICAL_IDENTITY_RECOVERED / CANONICAL_SOR_WRITE_0 / CREDENTIAL_MATERIAL_EXPOSURE_0 / LIVE_DISABLED / KILL_ENGAGED / P0_0 / P1_0`。因account/credential尚未恢复，不满足“只有instrument缺失”，本轮不向operator发起instrument单选。

## BTC-USDT final execution continuation（2026-08-24）

- Git/runtime：`HEAD == origin/dev == d7d03cc53b4f1479aa7ac8efa554ae9d5dca8983`；server current仍为`c47c8db3...`，V42/failed0、health UP、LIVE=false、kill=ENGAGED。
- provisioning closeout：18891 listener absent，`/run/nq-gatey-provisioning.env` absent；保留一个inactive/failed provisioning DB-check transient unit，未reset或修改systemd。18890 runtime active/running。
- canonical SoR：account1=`owner2 / OKX / LIVE / ACTIVE`；credential1=`OKX_API_V5 / ACTIVE / VERIFIED`，唯一account/credential计数=`1/1`。provisioning blocker关闭；本轮未重新provision或读取credential material。
- zero baseline：V42/failed0，session/lease/intent/receipt/order/trade/ledger/audit均0。
- current permission blocker：credential stored facts为`permissionProbeStatus=NOT_PROBED`、`permissionScope=NULL`、`ipAllowlistProbeStatus=NOT_CHECKED`。`StoredFactExactPilotBindingAuthority`要求`SUCCEEDED / TRADE / PASSED`且`lastPermissionProbeAt`非空；已部署minimal-pilot只启用`gatey-readonly-qualification` profile，不装配`scoped-okx-private-readonly` permission-probe composition，也不调用`/account/config` writeback。因此不能把历史GateY-6C permission当current refresh。
- BTC catalog blocker：operator已固定BTC-USDT，但production `instrument_catalog`中BTC-USDT row=0。`MinimalLivePilotControlService`在JIT observation前先要求唯一catalog row；current profile显式`catalog-sync.enabled=false`，故直接启动pilot会以`PILOT_INSTRUMENT_REFERENCE_MISMATCH`拒绝。
- bestAsk blocker：`invoke-gatey-minimal-live-pilot.ps1`要求外部传入`limitPrice`与`quantity`；`OkxJdkRealClient.observePrerequisites()`只采集instrument、fee、USDT balance与server time，不采集current bestAsk。仓库没有可在本轮no-code/no-deployment边界内调用的正式bestAsk→exact order参数CLI。
- Findings：P0=0；P1-1=`MINIMAL_PILOT_CURRENT_PERMISSION_REFRESH_NOT_COMPOSED`；P1-2=`MINIMAL_PILOT_CURRENT_BEST_ASK_AND_CATALOG_REFRESH_NOT_COMPOSED`。关闭它们需要代码、测试、exact-head CI与重新部署；附件同时禁止重新部署与新review，因此本轮不做现场修复。
- final authoritative readback：BTC catalog=0，permission/scope/IP=`NOT_PROBED/NULL/NOT_CHECKED`，session/lease/intent/receipt/order/trade/ledger/audit全0；health UP、LIVE=false、kill=ENGAGED。credential JIT/OKX/PLACE/CANCEL/transfer/withdraw=`0/0/0/0/0/0`，未生成order identities。
- Final decision：`BLOCKED / CURRENT_PILOT_PREREQUISITE_NOT_VERIFIED / NO_REAL_ORDER / BTC_USDT_SELECTED / ACCOUNT_1_REUSED / CREDENTIAL_1_REUSED / LIVE_FALSE / KILL_ENGAGED / PLACE_0 / CANCEL_0 / NO_TRANSFER / NO_WITHDRAW / P0_0 / P1_2`。
