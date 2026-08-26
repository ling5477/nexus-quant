# GateY Minimal Live Pilot End-to-End — attempt-01

## 当前结论

`EXECUTION_SCOPE_FIX_LOCAL_GREEN / CI_PENDING / NO_REAL_ORDER`（执行scope修复本地通过 / CI 待执行 / 未发送真实订单）。V46 已完成exact-head CI、production backup、V45→V46 migration与exact runtime部署；唯一后续controller调用合法生成ordinal2 lease，但因gateway仍错误复用已清空的`strategyRunId`承载pilot scope，在durable ExecutionIntent/PLACE前fail closed。当前修复以独立`executionScopeId`传递lease/intent identity，strategyRunId继续为null；CI成功前不再次部署或调用controller。

```text
P0=0
P1=0
implementationCommit=PENDING
exactHeadCi=PENDING
productionDeployment=V46_EXACT_HEAD_CURRENT_RUNTIME_STOPPED
activeRuntime=979d69c760dc07f220e7c4cb7bf55385120c8992
activeManifest=5c0ac60becb2adf6f75e6f4330d41e1d65a03f02035f0d315af13b7c317850c3
operatorPilotParameters=ACCOUNT_1_CREDENTIAL_2_BTC_USDT_BUY_LIMIT_CAP_10
currentPrerequisite=EXECUTION_SCOPE_FIX_LOCAL_VERIFIED_CI_PENDING
authority=CLOSED
session=RECONCILIATION_BLOCKED
lease=FAILED_ORDINAL_2
activeLease=0
PLACE=0
CANCEL=0
transfer=0
withdraw=0
LIVE=false
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

1. 先建立最小forward migration，为typed `MARKET_SNAPSHOT`或等价强约束variant承载instrument、bestAsk、bestAskObservedAt、marketSnapshotDigest与source identity；必须参与DB canonical hash重建、append-only/immutability与freshness。
2. 在新schema之上关闭permission refresh与typed BTC-USDT public metadata/ticker组合，禁止raw endpoint、脚本curl或把bestAsk塞进instrument/clock/reason/correlation字段。
3. 只有migration、代码、测试、targeted review、exact-head CI与受控部署全部通过后，才允许继续同一attempt刷新current facts并执行exactly-one real LIMIT。

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

## Prerequisite remediation schema hard gate（2026-08-24）

- 授权：用户明确允许一次关闭P1-01/P1-02、代码/测试/review/commit/CI/deploy与同attempt pilot；默认no-new-migration，但在确证现有持久化模型无法承载时允许提出migration blocker。
- schema evidence：V40 `pilot_prerequisite_observations.observation_type`只允许`INSTRUMENT_METADATA / FEE_SCHEDULE / BALANCE_SNAPSHOT / CLOCK_SYNC`；variant CHECK、payload hash重建函数、freshness lookup与item FK均按四类固定。V41只调整instrument minimum-order-value语义，仍保持四类variant；V42只新增pilot lease三表。
- binding evidence：`ExactPilotBinding`持久化`observationSetId`与`OrderEnvelope.price/quantity/notional`，但没有marketSnapshotDigest或bestAskObservedAt。仅保存order price不能证明它来自current bestAsk；把digest编码进request/trace/correlation或其他字段不属于typed fact，也无法由DB重建验证。
- migration review：安全最小候选必须是forward-only V43（或下一可用版本），新增typed market snapshot承载与中文COMMENT，扩展observation type/variant、canonical payload hash、domain/JDBC mapping、freshness、exact binding validation与PostgreSQL regression。需评估CHECK/函数替换锁窗口；现有表为空，因此无需历史回填，不得修改V40/V41/V42。
- prohibited shortcuts：未把bestAsk塞入instrument digest、clock observation、minimumOrderValue、lease event、audit reason或correlation；未新增JSON旁路；未调用raw OKX或写production DB。
- current production：account1/credential1/BTC-USDT operator decision保留；credential JIT/OKX/PLACE/CANCEL/transfer/withdraw=`0/0/0/0/0/0`，session/lease/intent/receipt/order/trade/ledger/audit全0；LIVE=false、kill=ENGAGED。
- Final decision：`BLOCKED / CURRENT_MARKET_SNAPSHOT_PERSISTENCE_MIGRATION_REQUIRED / NO_REAL_ORDER / P0_0 / P1_2 / PLACE_0 / CANCEL_0 / LIVE_FALSE / KILL_ENGAGED / NO_TRANSFER / NO_WITHDRAW`。本轮不生成半成品代码，不commit/push/deploy；下一动作必须先获得最小V43实施范围确认，之后继续同一attempt，不创建Attempt-02。

## V43 completion and final pilot implementation（2026-08-25）

- Scope：继续同一 `NQ-GATEY-MINIMAL-LIVE-PILOT-END-TO-END.attempt-01`；新增且仅新增 V43，未创建 Attempt-02，未修改 V40/V41/V42，未访问 production 或 OKX。
- V43：新增 `MARKET_SNAPSHOT` typed variant，保存 `marketSnapshotDigest / marketInstrument / bestAsk`，`observedAt` 与 source identity 复用 immutable envelope；完整保留 V41 四类 variant 约束，五类 set deferred validator、append-only、固定 OKX ticker source、session instrument、lowercase SHA-256、`bestAsk > 0` 与最多 8 位小数均由 PostgreSQL hard gate 校验。
- canonical parity：Java/DB 对 market snapshot digest 与 prerequisite envelope payload hash 使用相同字段顺序、quoted decimal、UTC microsecond 与 UTF-8 SHA-256；disposable PostgreSQL 已验证 byte parity、tamper、invalid digest/source、`bestAsk<=0` 与不完整 set 拒绝。
- permission：复用 `CredentialPermissionProbeService`、`OkxRealReadonlyPermissionProbePort` 与 `GATEY_PILOT_READINESS`；minimal pilot 先执行唯一受控 `GET /api/v5/account/config` refresh，只有 `SUCCEEDED / TRADE / withdraw=false / IP PASSED / fresh` 才继续。
- catalog/market：typed OKX account instrument metadata 经 `InstrumentCatalogService` 和 canonical repository bounded upsert/readback；typed public ticker读取 exact BTC-USDT `askPx/ts`，无 raw endpoint、脚本 curl 或全市场 sync。
- auto order：operator 输入收敛为 account/credential/BTC-USDT/BUY/10 USDT；`limitPrice=current fresh bestAsk` 且必须 tick-valid。`quantity` 使用 `min(available USDT, cap) - abs(taker fee) reserve - 0.10 USDT safety buffer` 后按 lotSize 向下取整，并校验 minQty、published minNotional（若存在）、balance、fee reserve 与 `notional<=10`。
- exact binding：`ExactPilotBinding` 升级为 v2，显式绑定 market observation identity + digest；stored-fact authority 强制 instrument、price、freshness、canonical digest 与五类 current set 一致。price drift、expired market 与 digest drift均 no PLACE。
- order closeout：PLACE 路径仍最多一次且禁止 retry；timeout/unknown query-first。OPEN/PARTIAL 先观察 2 秒并再次 query，之后最多 CANCEL 一次；最终 reconciliation/lease/kill 合同保持不变。
- PostgreSQL：本地 PostgreSQL 17.7 随机 schema 5/5 PASS（通过），覆盖 V1→V43、V40 historical preservation、精确 V42→V43、pending=0、failed history=0、五类 JDBC replay、append-only、并发与 lock timeout；所有随机 schema 已清理。
- Validation：production compile 与全仓 testCompile PASS；full Maven 23 modules PASS；GateY exact/minimal/release/runtime/GateY4/GateY5=`7/25/31/51/PASS/PASS`；GateW frozen=`37/12/34`；Authority checker与Java governance PASS；Shadow=`NEW_CODE_VIOLATION_COUNT=0`；custom secret backstop 44 files/0 findings。Pinned gitleaks 留待 exact-head CI。
- Targeted review：V43 variant/hash/lock、五类语义、market freshness/source、permission writeback、catalog path、binding digest、10U cap、PLACE/CANCEL cardinality、secret handling均已检查；P0=0、P1=0。
- Production boundary：server/production DB/credential material/OKX/PLACE/CANCEL/transfer/withdraw=`0/0/0/0/0/0/0/0`；production仍为 runtime=`c47c8db3...`、V42、LIVE=false、kill=ENGAGED，PLACE/CANCEL/transfer/withdraw=`0/0/0/0`。
- Current decision：`IMPLEMENTED / LOCAL_GREEN / P0_0 / P1_0 / CI_PENDING / DEPLOYMENT_NOT_STARTED / NO_REAL_ORDER`。
- Next：精确暂存、commit `fix(gatey): complete current market prerequisites for live pilot`、push `origin/dev` 并等待 exact-head CI；只有 CI 全绿后才构建 immutable release、备份、V42→V43、激活并继续同一 attempt 的唯一一次真实 pilot。

## V43 production deployment and pilot bootstrap blocker（2026-08-25）

- Git/CI：implementation commit与`origin/dev`均为`13081d8bf675fc3234cfd2488a67fd071dbbb2ff`；exact-head CI run `32812501391` 为10/10 success。
- Release：15-artifact immutable release安装与installed verifier均PASS；manifest SHA-256=`e4958089006829fdbd949f8f44750f994b887f6e3926ae8e569f4fd9470e9910`，POSIX/link/root ownership与service-user write denial通过；旧`b18450d1...` candidate未激活。
- Backup：新建pre-V43 backup `pre-v43-13081d8b-20260825T081700Z.dump`；root:root/0600/link1、bytes=`701419`、SHA-256=`b786f170f1eae5d6a88e1cec2617495eef44a5f8a481a2388a4ab89391e05440`，`pg_restore --list` 1748 entries并通过。首次`pg_dump -X`在连接与文件创建前因非法参数退出，精确路径复核为不存在后使用新名称成功，未覆盖或删除旧backup。
- Migration：从exact release nested `nq-infra` JAR提取并验证43-file closed set/hash；pinned Flyway image digest=`sha256:782c5c207ffb5ac6336139fda4f4295bd9991ef63ad36919406d4268740069bb`唯一一次执行V42→V43，validate PASS；最终current/pending/failed/long-lock=`43/0/0/0`，kill=`ENGAGED`。数据库credential只经root-owned reference由外部进程消费，material未输出。
- Runtime：runtime env原子绑定exact release/manifest并从唯一accepted事实恢复expected IP=`47.251.74.35`；canonical Stop旧runtime后Activate成功。Current=`13081d8b...`、MainPID=`1172512`、NRestarts=0、health UP、source/release exact、LIVE=false、kill=ENGAGED、mutationRuntimeBound=false、tradingAuthorization=false。
- Pilot invocation：最终基线为V43/failed0/kill ENGAGED/account1/credential1/activeLease0/PLACE0/CANCEL0/order0/trade0/ledger0。唯一一次controller调用在Spring context初始化阶段返回`BLOCKED / MINIMAL_LIVE_PILOT_INVOCATION_FAILED`；根因是CLI固定`--spring.main.web-application-type=none`，但`SecurityConfiguration.securityFilterChain(HttpSecurity)`仍无条件装配，non-web context没有`HttpSecurity` Bean。
- Side effects：失败发生在permission refresh与全部application runner逻辑之前；final permission=`NOT_PROBED / scope NULL / IP NOT_CHECKED / withdraw=false`，BTC-USDT catalog=0；session/scope/observation/lease/leaseIntent/executionIntent/receipt/order/trade/ledger/audit全0。credential JIT/OKX GET/OKX POST/PLACE/CANCEL/transfer/withdraw=`0/0/0/0/0/0/0`，未生成clientOrderId/idempotencyKey/requestId/traceId，无reconciliation divergence。
- P1：`MINIMAL_LIVE_PILOT_NON_WEB_SECURITY_CONTEXT_BOOTSTRAP_FAILURE`。禁止用servlet端口抢占、关闭Security、现场参数注入或第二次controller调用绕过；必须先做最小代码修复、真实CLI context回归、targeted review、exact-head CI与immutable redeploy。
- Final decision：`BLOCKED / V43_DEPLOYED / EXACT_HEAD_RUNTIME_HEALTHY / MINIMAL_LIVE_PILOT_NON_WEB_SECURITY_CONTEXT_BOOTSTRAP_FAILURE / NO_REAL_ORDER / LIVE_FALSE / KILL_ENGAGED / PLACE_0 / CANCEL_0 / NO_PLACE_RETRY / NO_TRANSFER / NO_WITHDRAW / P0_0 / P1_1`。

## Non-web Security context remediation implementation（2026-08-25）

- Scope：继续同一attempt-01；只修复Spring composition，不修改V43、market/permission/catalog/quantity、lease/kill/provider/reconciliation或脚本参数。
- Implementation：仅为`SecurityConfiguration.securityFilterChain(HttpSecurity)`增加`@ConditionalOnWebApplication(type=SERVLET)`；web-neutral的`PasswordEncoder`、`TokenService`、`AuthService`、`CurrentUserProfileService`保持原装配，未新增第二套Security或profile/task-id特判。
- Non-web regression：真实`SpringApplication`固定`WebApplicationType.NONE`成功启动，`SecurityFilterChain` bean count=0且通用认证beans存在；actual `MinimalLivePilotConfiguration`在non-web context中两个ApplicationRunner均成功实例化，permission/credential/order mocks零交互。
- Servlet regression：`SecurityFilterChain` bean count=1；JWT filter仍位于AuthorizationFilter之前；既有login permitAll、GET认证、write ADMIN/OPERATOR、401/403行为共12 tests全绿，无`permitAll /api/**`扩大。
- Validation：production compile/test-compile 23 modules PASS；focused=`15/15`；full Maven 23 modules PASS；GateY=`7/25/31/51 + GateY4/GateY5`；GateW=`37/12/34`；Authority/Java governance/custom secret backstop PASS；detached Shadow=`NEW_CODE_VIOLATION_COUNT=0`。
- Validation history：首次Maven命令将生命周期误写为`testCompile`而在编译前退出；首次focused的测试假设错误要求不存在的form-login filter，改为验证实际AuthorizationFilter后15/15。首次full Maven仅因本机localhost开发库旧V43 checksum失败；当前migration SHA-256仍精确为`f41dbb3...`且diff=0，使用Flyway 11.7.2仅对本机history执行一次repair后full Maven通过，production未连接或迁移。主worktree Shadow被既有不可读artifact ACL阻断，短路径detached worktree对同一三文件diff通过并已移除，未改ACL或用户目录。
- Targeted review：non-web不再要求HttpSecurity；Servlet security未削弱；无GateY/profile/task-id hack；credential/secret边界与交易逻辑diff=0。P0=0、P1=0。
- Production boundary：server继续current=`13081d8b...`、V43/failed0、health UP、LIVE=false、kill=ENGAGED；本轮production访问、controller、credential JIT、OKX、lease、PLACE、CANCEL、transfer、withdraw均为0。
- Current decision：`IMPLEMENTED / LOCAL_GREEN / P0_0 / P1_0 / CI_PENDING / DEPLOYMENT_NOT_STARTED / CONTROLLER_NOT_RETRIED / NO_REAL_ORDER`。

## Non-web remediation CI and exact-release reproducibility blocker（2026-08-25）

- Implementation/CI：commit=`90d7ff52623ebeef43317a52194b1f5e60745b63`，`HEAD==origin/dev`；exact-head CI run=`32817687018 / completed / success / 10 jobs`，non-web Security P1关闭。
- Build path A：CRLF detached clean worktree的fresh canonical builder首次返回generic internal error且无release；同Maven goals成功后canonical builder clean重建PASS，release=`90d7ff52...`、15 artifacts、V43、manifest=`30fa3510c9cc85c9c53f867c84e8ccddfb9b38d1a7a82e7f1d3e1e309e40ebc7`。
- Server install：path A release上传16 files并immutable install/verify PASS；POSIX/link/root ownership/service-user write denial通过。该candidate只安装、未InstallUnit、未更新runtime env、未Stop/Activate。
- Repro path B：使用`core.autocrlf=false`的独立LF clean clone对同一commit构建；fresh canonical builder同样先generic失败且无release，exact Maven warm build后canonical clean build PASS，manifest=`049588c05547cc1af87b4dcd9b61f82d25f346fea2df2cafeea62f5959738639`。
- Divergence：同一releaseId/sourceCommit、artifactCount=15、schemaTarget=V43却产生不同manifest；CRLF build的unit为1891 bytes/53 CRLF，既有LF unit为1838 bytes/53 LF。`systemd-analyze verify`虽PASS，但单次verifier不能覆盖cross-checkout reproducibility，故命中`EXACT_RELEASE_CROSS_CHECKOUT_REPRODUCIBILITY_DIVERGENCE` P1。
- Cleanup：两个本轮临时build worktree/clone均按精确绝对路径移除；未改既有artifact ACL、未删除主仓库`target/`、未删除服务器immutable candidate。
- Final production readback：current=`13081d8b...`、unit active、health UP、V43/failed0、kill ENGAGED；activeLease/intent/receipt/order/trade/ledger/audit=`0/0/0/0/0/0/0`。controller retry/JIT/OKX/PLACE/CANCEL/transfer/withdraw=`0/0/0/0/0/0/0`。
- Final decision：`BLOCKED / NON_WEB_SECURITY_CONTEXT_REMEDIATED / EXACT_HEAD_CI_GREEN / EXACT_RELEASE_CROSS_CHECKOUT_REPRODUCIBILITY_DIVERGENCE / CANDIDATE_NOT_ACTIVATED / NO_REAL_ORDER / LIVE_FALSE / KILL_ENGAGED / PLACE_0 / CANCEL_0 / P0_0 / P1_1`。

## Final convergence：DB write window、exact deployment 与 credential hard blocker（2026-08-25）

- Release chain：`89aad8dd...` 使用 `git cat-file --batch` 从 exact commit blob bytes materialize 3064 tracked files；LF/CRLF checkout 的15 artifacts、JAR、migration inventory与manifest bytes全部一致。后续 `99ae7a5b...`、`6b31999e...`、`0aaf840b...` 分别关闭minimal-pilot configuration ordering、`TradingVenueGateway` ambiguity与exact-binding ordering，四个exact-head CI均10/10 success。
- DB write window：forward-fix commit=`496ed9f22ae27582696571e2afb96c89dfd63fb9`，CI run=`32836087190 / completed / success / 10 jobs`。只对26张pilot主链表授予精确`INSERT/UPDATE`组合，对6条实际`nextval` sequence临时授予`USAGE`；禁止DELETE/TRUNCATE/schema CREATE/all-table/default privilege。Controller前要求常驻runtime已停止，grant前要求table DML与sequence `USAGE/UPDATE`基线为0，finally精确REVOKE并readback为0；连接/statement/process timeout分别为5s/30s/45s。
- SQL validation incident：测试提取命令因PowerShell `$script:`展开错误，误将grant块按commit执行；约4秒内立即执行同源REVOKE并返回`PILOT_DATABASE_WRITE_WINDOW_CLOSED`。窗口期间旧runtime虽active但`LIVE=false / tradingAuthorization=false`；随后table DML=0、sequence `USAGE|UPDATE`=0、lease/intent/order/trade/ledger/audit=0、kill=ENGAGED、health UP。事件未隐藏，未发生OKX或交易调用。
- Validation：pilot contract=81/81、release contract=31/31、runtime contract=51/51、builder self-test PASS且source mode=`EXACT_GIT_COMMIT_BLOB_BYTES`、full Maven 23 modules PASS、Java governance PASS、detached Shadow `NEW_CODE_VIOLATION_COUNT=0`；主worktree Shadow仅因既有不可读artifact返回checker exit 3，clean detached worktree复核关闭。Linux installer专项在Windows与无pwsh的WSL不可运行，后续由服务器真实install/POSIX verifier覆盖并通过。
- Deployment：canonical release=`496ed9f22ae27582696571e2afb96c89dfd63fb9`，manifest=`c5af3f1466a8600ffa5c2abb40adf2d69a60528916d3a37d2774c2486f2e5655`，15 artifacts、V43、source mode exact Git blob bytes。Local/staging hash verifier、installed POSIX/link/root ownership、service-user write denial均PASS；code-only activation完成，无migration、无新backup。最终current exact、MainPID=`1192572`、NRestarts=0、health UP、V43/failed0、LIVE=false、tradingAuthorization=false。
- Controller safety：第一次transient启动通过`/opt/nexus-quant/current` symlink在release verifier前返回`RELEASE_LINK_INTEGRITY_VIOLATION`，permission/ACL/OKX/lease/intent/order仍全0；证明PLACE=0后改用exact immutable path和显式`ReleaseRoot`继续同一attempt，不构成PLACE retry。第二次启动打开受控DB窗口并执行唯一current permission refresh，之后finally成功REVOKE；两个transient units均已reset为not-found。
- External fact：account1/credential1仍为唯一ACTIVE候选；真实OKX private read-only permission refresh确定性写回`FAILED / HTTP_UNAUTHORIZED`、failedAuthCount=1、withdraw=false、permissionScope=NULL、IP=`NOT_CHECKED`。credential audit仅新增`PERMISSION_PROBE_STARTED/FAILED`两条脱敏事实；未读取或输出credential material。
- Final readback：BTC-USDT catalog/session/scope/observation/lease/activeLease/leaseIntent/executionIntent/receipt/order/trade/ledger/event_store/audit均0；PLACE=0、CANCEL=0、transfer=0、withdraw=0；runtime table DML=0、sequence `USAGE|UPDATE`=0；kill=ENGAGED、LIVE=false、health UP。
- Final decision：`BLOCKED / OPERATOR_INPUT_REQUIRED / CREDENTIAL_AUTHENTICATION_FAILED / HTTP_UNAUTHORIZED / NO_REAL_ORDER / ACCOUNT_1_REUSED / CREDENTIAL_REFERENCE_1_REUSED / BTC_USDT / BUY_LIMIT / PILOT_CAP_10_USDT / PLACE_0 / CANCEL_0 / NO_PLACE_RETRY / LIVE_FALSE / KILL_ENGAGED / ACTIVE_LEASE_0 / NO_TRANSFER / NO_WITHDRAW / P0_0 / LOCAL_P1_0`。
- Next：`NQ-GATEY-6F-CREDENTIAL-AUTHENTICATION-REMEDIATION-BLOCKED`。Operator只能通过既有安全credential管理路径修复或轮换material，不得在聊天、仓库、日志或文档中提供明文；修复后继续本attempt-01，仅重跑current permission prerequisite，禁止Attempt-02与第二PLACE。

## Credential2 resume、role-lock remediation 与 authority architecture blocker（2026-08-25）

- SoR recovery：production唯一ACTIVE OKX LIVE account仍为account1；唯一ACTIVE+VERIFIED `OKX_API_V5` credential自动解析为credential2，旧credential1未进入候选。起始PLACE/CANCEL/order/trade/ledger/activeLease与临时DB权限均0。
- Current permission：credential2执行真实`GATEY_PILOT_READINESS`后写回`SUCCEEDED / TRADE / IP PASSED / withdraw=false / failedAuthCount=0`，credential authentication blocker关闭；未读取或输出credential material。
- Role-lock defect：permission成功后，`JdbcLiveControlAuthorization`的`FOR SHARE OF users/user_roles/roles`因PostgreSQL行锁权限失败；普通SELECT已存在。production单事务探针证明最小列权限`users.id / user_roles.user_id / roles.id UPDATE`足够并ROLLBACK。Forward-fix commit=`97e04a5bbce453b4b8a4392d5c3b6880a200d427`将三列纳入临时grant/revoke与baseline/final readback；pilot/release/runtime=`90/31/51 PASS`、builder exact-blob self-test、full Maven 23 modules与exact-head CI run=`32858550250 / success / 11 jobs`均通过。
- Redeploy：canonical release=`97e04a5b...`、manifest=`64e62c61207535c8b041be31eea3b5f7e3f0503f507c07c87f7ee8848407a504`、15 artifacts、V43；installed POSIX/link/root ownership/service-user write denial与code-only activation/health均PASS，无migration/new backup。
- Authority blocker：role lock通过后，formal `JdbcPilotScopeAuthorityResolver.resolveMinimal`在`minimal-live-pilot-strategy-release-id`处fail-closed；runtime env中minimal strategy/risk IDs均不存在。production SoR精确为`strategy_release_admission_state=0`、`backtest_publish_records=0`、`risk_limit_sets=0`，而V39 `live_sessions.strategy_release_id/risk_limit_set_id`通过FK强制绑定真实admission/risk facts。现有代码也没有minimal path的risk creation或admission materialization。
- Safety：该失败发生在session/scope/observation/binding/lease/intent/PLACE前；finally后table DML=0、column UPDATE=0、sequence `USAGE|UPDATE`=0。最终runtime exact/health UP、LIVE=false、kill=ENGAGED、activeLease/session/scope/observation/intent/receipt/order/trade/ledger/audit全0，PLACE/CANCEL/transfer/withdraw=`0/0/0/0`。
- Decision：`BLOCKED / MAJOR_ARCHITECTURE_DECISION_REQUIRED / MINIMAL_PILOT_STRATEGY_RELEASE_AND_RISK_AUTHORITY_NOT_MATERIALIZED / ACTIVE_CREDENTIAL_2_VERIFIED / CURRENT_PERMISSION_REFRESH_VERIFIED / NO_REAL_ORDER / PLACE_0 / CANCEL_0 / LIVE_FALSE / KILL_ENGAGED / NO_TRANSFER / NO_WITHDRAW / LOCAL_P1_0`。
- Next：`NQ-GATEY-6F-MINIMAL-PILOT-STRATEGY-RISK-AUTHORITY-DECISION-BLOCKED`。Operator需选择：提供既有可信Strategy Release admission/risk-set facts，或显式授权新的minimal-session架构与必要forward migration；禁止synthetic/raw SQL fabrication、随机候选、Attempt-02或第二PLACE。

## V44 Operator Pilot authority implementation 与本地复核（2026-08-26）

- Operator architecture decision：用户明确选择 `OPTION_2 / OPERATOR_DRIVEN_MINIMAL_SESSION`；本轮继续同一 `NQ-GATEY-MINIMAL-LIVE-PILOT-END-TO-END.attempt-01`，未创建 Attempt-02。BTC-USDT pilot 被建模为人工显式授权，不再要求或创建 synthetic `strategy_release_admission`、`backtest_publish_record`、`risk_limit_set`。
- V44 schema：新增 `operator_pilot_authorities`，并为 `live_sessions` 增加互斥 `authority_type / operator_pilot_authority_id / operator_pilot_authority_digest`；历史 session 只 backfill `authority_type=STRATEGY`，原 strategy/risk identity 不变。`STRATEGY` 与 `OPERATOR_PILOT` 的双 authority、空 authority、缺失条件字段全部由 CHECK/FK/trigger fail closed。
- Operator authority：新增 domain/encoder/port/service/JDBC，只负责 canonical digest、owner/account/credential、BTC-USDT/BUY/LIMIT、`maxNotional<=10`、PLACE/CANCEL 各1次、transfer/withdraw=false、validity/status 与 scope validation；没有第二套 strategy、risk、approval 或 order engine。
- Pilot binding/lease：operator session、pilot scope 与 ExactPilotBinding 使用独立 canonical schema 分支；STRATEGY 既有 canonical bytes 回归保持通过。Lease 显式绑定同一 operator authority；authority/session/binding/lease/notional/window 任一漂移均拒绝。Lease 进入 CLOSED/FAILED/EXPIRED 时，authority 在同一数据库事务关闭或过期。
- Send-time hard gate：SEND_STARTED 前同一 JDBC gate 重读 session、kill、lease、operator authority、account、credential、1分钟permission freshness、instrument、fee、balance、clock、MARKET_SNAPSHOT digest/price/freshness 与10U envelope；PLACE仍为1次且retry=0，CANCEL最多1次。Strategy分支保留原行为，并继续走既有状态机与风险 identity。
- Forward-only remediation：V43 的 `best_ask NUMERIC(38,18)` 与 `scale(best_ask)<=8` 组合会把合法8位输入补零为18位并误拒绝；V44 将其等价改为 `best_ask=round(best_ask,8)`，不修改V43，仍拒绝第9位及之后的非零精度。
- Release tooling：Windows PowerShell 5.1 的 `ProcessStartInfo.ArgumentList`、native stdin BOM 与双管道背压会阻断 exact Git blob builder self-test；已增加PS5 binary temp batch兼容分支，仍逐路径/逐Git blob hash复核并保持 `EXACT_GIT_COMMIT_BLOB_BYTES`。PS5/PS7 self-test均为44 migrations、tracked files=3064、tamper rejected。
- PostgreSQL：本地 PostgreSQL 17.7 随机 schema 中，V1→V44、V39 populated→V44、V43→V44、Flyway validate/pending/failed、historical STRATEGY backfill、valid OPERATOR_PILOT、dual/no/missing authority、canonical digest、lease authority closeout均PASS（通过）；随机 schema 已清理，未修改production DB。
- Application regression：Operator authority domain、minimal resolver/materialization、stored-fact ExactPilotBinding、operator canonical JSON round-trip、expiry、wrong instrument/SELL/MARKET、>10U、authority mismatch 与 existing STRATEGY golden cases均PASS（通过）。Existing one-PLACE/one-CANCEL、UNKNOWN query-first、no-place-retry、ledger/reconciliation tests继续通过。
- Validation：production compile与test-compile通过；official Flyway 11.7.2 在隔离随机schema执行V1→V44/validate后，full Maven 23 modules通过；GateY exact/minimal/release/runtime/GateY4/GateY5=`7/90/31/51/PASS/PASS`；GateW frozen=`37/12/34`；Authority与Java governance通过；detached clean Shadow=`NEW_CODE_VIOLATION_COUNT=0`、existing/ruleset expansion=`144/14`；staged secret backstop=`39 files / 0 findings`。本机未安装gitleaks，pinned Gitleaks仍由exact-head CI执行。
- Local validation history：早期full Maven曾把本地开发库迁到当时的V44草稿；V44后续继续收紧后，canonical local context因checksum mismatch拒绝启动。未对该本地history做repair或hand DDL；最终全量回归改在新建随机schema执行final V44，并补一行脱敏legacy account fixture满足既有测试前置，结束后仅删除该随机schema。Production DB未连接、未repair、未迁移。
- Targeted review：只审查authority type isolation、STRATEGY regression、operator scope、10U hard cap、exact binding、lease、第二PLACE/CANCEL、secret handling、V44 migration、permission/market freshness与release reproducibility；P0=0、P1=0。不做全仓重新审计。
- Production boundary：本阶段production访问、migration、backup、deployment、credential JIT、OKX GET/POST、PLACE/CANCEL/transfer/withdraw均为0；没有读取或输出credential material。`LIVE=false / kill=ENGAGED / activeLease=0` 本轮未重新从production读取，必须在CI通过后的deployment preflight重新验证，不能沿用历史值冒充current readback。
- Current decision：`IMPLEMENTED / LOCAL_GREEN / V44_OPERATOR_PILOT_AUTHORITY_READY_FOR_COMMIT / NO_SYNTHETIC_STRATEGY_AUTHORITY / STRATEGY_SESSION_REGRESSION_GREEN / P0_0 / P1_0 / CI_PENDING / DEPLOYMENT_NOT_STARTED / NO_REAL_ORDER / PLACE_0 / CANCEL_0 / NO_PLACE_RETRY / NO_TRANSFER / NO_WITHDRAW`。
- Next：精确暂存本轮实现与本文件、commit `feat(gatey): add operator pilot live-session authority`、push `origin/dev` 并等待 exact-head CI 全绿；CI 成功前禁止 production V43→V44、runtime activation、kill window 或 PLACE。

## V44 production、canonical-time remediation 与 trusted scope bootstrap blocker（2026-08-26）

- Implementation/CI：V44 implementation commit=`56218ae13a0af79aa7da519f7a14546c18084207`，`HEAD==origin/dev`；exact-head CI run=`32876056462 / completed / success / 11 jobs`。Canonical release manifest=`428edd878ef0b105a64a910ed49567cceef4b641f616ccbd90634b36bf63bf0e`，15 artifacts、schema target V44、source mode=`EXACT_GIT_COMMIT_BLOB_BYTES`；local、installed、POSIX、link、root ownership 与 service-user write denial均PASS（通过）。
- Production preflight/backup：hostname=`iZrj9gpab986sm4d0bb6agZ`；起始current=`97e04a5b...`、manifest=`64e62c61...`、V43/failed0、health UP、LIVE=false、kill=ENGAGED，account1与唯一ACTIVE+VERIFIED credential2精确成立；session/lease/intent/receipt/order/trade/ledger/audit、PLACE/CANCEL与临时权限均0。Pre-V44 backup=`/var/lib/nexus-quant/gatey-readonly-qualification/backups/pre-v44-56218ae1-20260825T174157Z.dump`，root:root/0600/link1、bytes=`729661`、SHA-256=`3a5573e8762c4f6972b3bf72691a4d0736775c9f261b7081148fb3e6dad948c9`、`pg_restore --list` 1845 entries。首次`pg_dump -X`在连接前因非法参数退出并创建0-byte文件；确认普通单链接0-byte后精确删除，新名称重做成功，未覆盖旧backup。
- Migration/deployment：从exact release nested `nq-infra` JAR提取44-file closed set，逐文件hash与inventory=`69c22c25...a587`全部匹配；pinned Flyway image digest=`sha256:782c5c207ffb5ac6336139fda4f4295bd9991ef63ad36919406d4268740069bb`唯一一次执行V43→V44并validate。最终current/pending/failed/long-transaction=`44/0/0/0`，V44 history success，新表与全部pilot主链计数0；root-only Flyway env-file已删除。Runtime env只原子替换release/source/manifest三字段，V44 release激活后health UP、LIVE=false、kill=ENGAGED。
- First controller blocker：第一次V44 transient controller在`OperatorPilotAuthority` canonical digest前因`Clock.instant()`纳秒精度超过PostgreSQL/canonical微秒合同而阻断：`canonical instant must have at most microsecond precision`。失败在authority/session/scope/observation/binding/lease/intent/PLACE前；finally后临时table DML/column UPDATE/sequence权限=`0/0/0`，PLACE/CANCEL=`0/0`，kill=ENGAGED。
- Forward remediation：commit=`3250342ceabaa023073a78bf50509dd2f4bc0229`只在受注入Clock边界执行`truncatedTo(MICROS)`并新增`123456789ns -> 123456us`回归，不放宽canonical encoder、不改V44/状态机/订单/lease/kill。Focused相关测试、最终隔离V1→V44 full Maven 23 modules、GateY=`7/90/31/51`、Authority、Java governance、clean detached Shadow `NEW_CODE_VIOLATION_COUNT=0`均通过；本机旧V44 history未repair。Exact-head CI run=`32881969165 / completed / success / 11 jobs`，包含pinned Gitleaks。Remediation release manifest=`c85fcfc1f0764f23db7032232d6a12002dc2b182000d862dea9be22c268bf320`，15 artifacts、V44、exact Git blob source；code-only activation通过，无第二次migration/backup。
- Trusted scope blocker：修复后controller在`JdbcPilotScopeAuthorityResolver.resolveRuntimeAuthority`固定阻断`PILOT_RUNTIME_AUTHORITY_NOT_CONFIGURED`。生产`runtime.env`与`secrets.env`中`NQ_LIVE_CONTROL_PILOT_MATERIALIZATION_*`键计数均0，受控目录也不存在既有exact-scope authority/input文件。缺失项不只是部署identity，还包括当前instrument digest、当前fee digest/tier、四类freshness上限、clock skew上限、endpoint-policy/provider/worker identity与digest。GateY-6D明确禁止operator/agent伪造动态observation或用test常量冒充trusted source；因此不得手工写入占位digest/threshold继续。
- Final safety readback：current=`3250342c...`、MainPID=`1211429`、NRestarts=0、unit active/running、health UP、V44/failed0、LIVE=false、kill=ENGAGED。authority/session/scope/observation/catalog/lease/activeLease/intent/receipt/order/trade/ledger/audit均0；PLACE/CANCEL=`0/0`，transfer/withdraw=`0/0`，临时table DML/column UPDATE/sequence权限=`0/0/0`。Credential2 current permission仍为`SUCCEEDED / TRADE / IP PASSED / withdraw=false`；credential audit共8条脱敏事件，未读取或输出credential material。
- Final decision：`BLOCKED / MAJOR_ARCHITECTURE_DECISION_REQUIRED / TRUSTED_OPERATOR_PILOT_SCOPE_BOOTSTRAP_NOT_MATERIALIZED / V44_DEPLOYED / EXACT_HEAD_CI_GREEN / ACTIVE_CREDENTIAL_2_VERIFIED / CURRENT_PERMISSION_REFRESH_VERIFIED / NO_REAL_ORDER / PLACE_0 / CANCEL_0 / NO_PLACE_RETRY / LIVE_FALSE / KILL_ENGAGED / ACTIVE_LEASE_0 / NO_TRANSFER / NO_WITHDRAW / P0_0 / P1_1`。
- Next：继续同一Attempt-01且禁止Attempt-02/第二PLACE。Operator必须在以下安全路线中做新决定：提供由既有可信流程生成并可验证的root-owned exact-scope authority（当前不存在），或授权实现`TRUSTED_OPERATOR_PILOT_SCOPE_BOOTSTRAP`，使一次只读OKX observation原子产生当前instrument/fee约束并与同一snapshot绑定，同时明确freshness/skew/endpoint/provider/worker policy；不得由聊天输入动态observation、手工SQL、测试常量或占位digest替代。

## Trusted bootstrap、pre-PLACE incident 与唯一 lease 终态 blocker（2026-08-26）

- Trusted bootstrap chain：`6d81d678312ba6b7cf7efba3e735260efe548e11`实现同一 credential-JIT OKX snapshot 同时生成 instrument/fee immutable constraints 与五类 observations，外部 OKX 调用保持在数据库 transaction 之前；exact-head CI run=`32919825899 / success / 11 jobs`。随后`6a219fda9e2f20f57e46692efdf27a483566658a`将available balance在trusted adapter边界按8位向下规范化，CI run=`32921007531 / success / 11 jobs`。
- Row-lock remediation：V40 trigger 的`FOR KEY SHARE`要求最小column UPDATE privilege；`6c231d4c3a60d0742fe0bd10cb0d82e176ea95d8`仅将`pilot_scope_bindings.pilot_scope_id UPDATE`纳入临时grant/revoke/readback，CI run=`32922699085 / success / 11 jobs`，GateY minimal contract=`90/90 PASS`。Canonical release manifest=`36d03ea47f72cb125c33355013530d790dd94887dba0b1a52b11d282e2ab0e60` code-only激活健康，无重复migration。
- Snapshot-time defect：该release的controller在同一OKX snapshot完成后被`trusted prerequisite observation does not match immutable pilot scope`拒绝。根因是operator ticker provider timestamp来自采集期间，但通用STRATEGY validator要求所有observed/recorded time等于采集前DB `resolvedAt`。失败发生在materialization transaction提交前；authority/session/scope/observation/lease/intent/PLACE/order均0，kill ENGAGED，临时权限全部撤销。
- Forward remediation：`51efdd15b66ec5f895269a4168115ea28d9989b5`保持STRATEGY校验原样，仅让OPERATOR_PILOT使用同一snapshot已验证的post-collection local midpoint作为共同`recordedAt`，并继续强制5秒最短collection window、100ms skew、exact recorder/source/symbol与freshness。Focused=`20/20 PASS`；隔离V1→V44 full Maven=`23/23 modules PASS`；GateY=`7/90/31/51 + GateY4/GateY5 PASS`；GateW frozen=`37/12/34 PASS`；Java governance PASS；clean detached Shadow=`NEW_CODE_VIOLATION_COUNT=0`。Targeted review P0=0/P1=0。
- Commit/CI/deployment：commit已push `origin/dev`；exact-head CI run=`32925189271 / completed / success / 11 jobs`。Canonical release=`51efdd15...`、manifest=`b586ecc72db01c88fe4163a97fb6846b0bb6e2787bc2019fcda968d1ed250901`、15 artifacts、V44、exact Git blob source；installed POSIX/link/root ownership/service-user write denial均PASS。Activation SSH返回UNKNOWN后只读收敛为current/PID/listener/exact health一致，未重发Activate；无migration、无新backup。
- Pre-PLACE incident：新release controller成功物化1个operator authority、1个OPERATOR_PILOT session、1个scope、5类observations、ExactPilotBinding与1个ACTIVE lease；随后runner在`orders.placeOrder()`之前因production `exchange_accounts.exchange_account_id=1`的`legacy_account_id=NULL`抛出`legacy account identity bridge is required`。该检查位于lease cleanup `try/finally`之前，导致kill暂时为DISENGAGED、session=`LIVE_ACTIVE`、lease=`ACTIVE`；但lease intent、ExecutionIntent、ExecutionReceipt、Order、Trade、Ledger、Audit与PLACE/CANCEL均为0，未生成或发送第二PLACE。
- Fail-close recovery：未重试PLACE；再次启动同一controller只用于触发最高优先级`recoverAtStartup()`。恢复将唯一lease终态化为`EXPIRED`、operator authority终态化为`EXPIRED`并把kill恢复为`ENGAGED`；随后新materialization在existing non-terminal session唯一约束处阻断，仍未创建lease intent或PLACE。最终临时table DML/column UPDATE/sequence权限=`0/0/0`。
- Final production readback：current=`51efdd15...`、MainPID=`1237382`、NRestarts=0、read-only runtime active/running、loopback health UP、V44/failed0、LIVE=false、kill=`ENGAGED/version3/PILOT_STARTUP_RECOVERY`、mutationRuntimeBound=false、tradingAuthorization=false。Authority=`EXPIRED`、session=`LIVE_ACTIVE`、lease=`EXPIRED`、activeLease=0、scope=1、observations=5；PLACE/CANCEL/receipt/order/trade/ledger/audit=`0/0/0/0/0/0/0`，transfer/withdraw=`0/0`。Credential material未读取或输出。
- Open P1：V42的`uq_pilot_execution_leases_single_pilot`只允许全局一个durable lease，且状态机禁止`EXPIRED → ACTIVE`；existing `LIVE_ACTIVE` session也禁止第二个non-terminal session。当前授权同时禁止修改历史migration、第二lease、Attempt-02与状态机绕过，因此无法在本轮安全地产生第一笔PLACE。继续至少需要新的forward migration/明确rearm contract、pre-PLACE ACTIVE-lease recovery、session recovery与canonical legacy account bridge决策；这属于新的重大架构决定。
- Final decision：`BLOCKED / MAJOR_ARCHITECTURE_DECISION_REQUIRED / LEGACY_ACCOUNT_IDENTITY_BRIDGE_MISSING / PRE_PLACE_ACTIVE_LEASE_EXPIRED_WITHOUT_INTENT / UNIQUE_LEASE_ALREADY_CONSUMED_AS_IDENTITY / NO_REAL_ORDER / PLACE_0 / CANCEL_0 / NO_PLACE_RETRY / ACTIVE_LEASE_0 / LIVE_FALSE / KILL_ENGAGED / NO_TRANSFER / NO_WITHDRAW / P0_0 / P1_1`。
- Next：继续保持Attempt-01且禁止Attempt-02/第二PLACE。Operator需明确授权新的forward migration与prepared-lease rearm/recovery + legacy bridge最小模型，或决定以`NO_REAL_ORDER`关闭Attempt-01；在该决定前禁止controller、PLACE、CANCEL、手工SQL rearm、修改V42/V44、transfer或withdraw。

## V45 zero-intent recovery 与 canonical legacy bridge 实现（2026-08-26）

- Authorization：operator明确批准新增且仅新增V45，继续原Attempt-01，PLACE total始终`<=1`、retry=0，禁止Attempt-02与第二PLACE。V42/V43/V44未修改，无Flyway repair或production手工DDL。
- Replacement model：`pilot_execution_leases`新增`predecessor_lease_id / recovery_decision_id / replacement_ordinal / replacement_reason`；旧lease保持EXPIRED且identity/state不可变。V42全局row唯一索引替换为single-open、single-replacement、single-predecessor-successor；replacement ordinal固定1，reason固定`PRE_PLACE_ZERO_INTENT_FAILURE`。
- Recovery decision：新增append-only `pilot_pre_place_recovery_decisions`，只持久化`REPLACEMENT_ALLOWED_ZERO_INTENT`及PLACE intent、SEND_STARTED、ExecutionIntent、ExecutionReceipt、Order、Trade与Ledger七项零计数。Decision insert与successor lease insert分别重新锁定predecessor并独立重算zero-proof；UNKNOWN、ACTIVE/CONSUMED predecessor、任何side-effect fact或第二successor均fail closed。
- Attempt exactly-once：`pilot_execution_lease_intents`新增全局PLACE/CANCEL唯一索引；所有lease合计最多一个PLACE link与一个CANCEL link。PostgreSQL integration实测PLACE link #1成功、#2拒绝；这不是允许第二次PLACE或blind retry。
- Session recovery：旧OPERATOR_PILOT session通过既有`LiveSessionStateMachine`依次执行`STOP → BEGIN_RECONCILE → RECONCILE_BLOCK → RESOLVE_AND_CLOSE`，最终`LIVE_RECONCILED`。Recovery专用入口必须重新验证operator、append-only decision、旧lease/session/account/credential/authority identity与kill ENGAGED；不要求已过期authority重新ACTIVE，也不直接SQL改state。
- Canonical legacy bridge：复用`exchange_accounts.legacy_account_id`；正式bridge service以`nq-okx-live-<exchangeAccountId>`确定性创建唯一`accounts` identity，同事务readback并写脱敏audit。V45补`legacy_account_id → accounts.account_id` FK、NULL→non-NULL/INSERT canonical trigger与映射不可变约束；不复制credential、不增加transfer/withdraw或额外交易授权。
- Runtime DB window：controller临时权限闭集只增加`accounts INSERT`、recovery decision `SELECT/INSERT`、bridge所需精确column UPDATE与`accounts_account_id_seq USAGE`；仍禁止DELETE/TRUNCATE/schema CREATE/GRANT ALL/default privileges，finally精确REVOKE/readback。
- PostgreSQL：V1→V45、精确V44→V45与validate均PASS；active predecessor、ExecutionIntent/Order、SEND_STARTED/Receipt拒绝；旧lease immutable；两条并发replacement请求exactly-one；second replacement与PLACE #2拒绝；bridge deterministic/idempotent。所有随机schema均已清理。
- Validation：production compile/testCompile 23 modules PASS；focused unit/contract=`8/8 PASS`；full Maven=`23/23 modules PASS`；GateY exact/minimal/release/runtime=`7/100/31/51 PASS`，GateY4/GateY5 PASS；GateW frozen=`37/12/34 PASS`；Authority与Java governance PASS。主worktree Shadow仍被既有不可读artifact ACL阻断，必须在exact commit的clean detached worktree复核。
- Validation history：首次testCompile仅因既有unit构造器未补recovery port而失败；兼容构造器保持replacement fail-closed后通过。首次V45 integration的future/authority-window夹具被V44 trigger正确拒绝，改用真实时钟/窗口后通过。首次local context因`@Transactional final class`无法CGLIB代理而失败，移除final后真实context通过。首次full Maven仅缺少non-web context recovery mock，补齐后23 modules通过。所有失败均保留为执行历史，未伪装PASS。
- Targeted P0/P1 review：仅审查replacement exactly-once/concurrency、legacy bridge、session recovery、PLACE boundary与migration。补充关闭两项review问题：bridge FK+INSERT trigger、terminal-session幂等返回前先验证actor/decision。最终P0=0/P1=0；未做全仓重新审计。
- Production boundary：本实现阶段未迁移/部署V45，未调用credential/OKX/controller，未创建replacement或bridge production fact。Production仍为current=`51efdd15...`、V44、health UP、LIVE=false、kill ENGAGED、authority/lease EXPIRED、session LIVE_ACTIVE、activeLease0、PLACE/CANCEL/order/trade/ledger=`0/0/0/0/0`。
- Current decision：`IMPLEMENTED / LOCAL_GREEN / V45_ZERO_INTENT_REPLACEMENT_READY_FOR_COMMIT / CANONICAL_LEGACY_ACCOUNT_BRIDGE_READY_FOR_COMMIT / PLACE_0 / NO_PLACE_RETRY / LIVE_FALSE / KILL_ENGAGED / P0_0 / P1_0 / CI_PENDING / PRODUCTION_UNCHANGED`。
- Next：精确暂存V45、recovery/bridge实现与测试、controller contract和本文件；commit `fix(gatey): allow zero-intent pilot lease recovery`，push origin/dev并等待exact-head CI。CI全绿前禁止production backup/migration/deployment/controller/PLACE。

## V45 production、zero-place recovery 与 exact sizing remediation（2026-08-26）

- Release/backup：V45 implementation commit=`f67c6645a63e5aa294260de47d6c875a522ff8ac`，exact-head CI run=`32930662341 / success / 11 jobs`。Canonical release manifest=`8fea6e28dfb331b127b09e82396fc2d3f77bd3112213c378c6a53ac74d179235`、15 artifacts、schema target V45；installed verifier、POSIX与link integrity均PASS（通过）。Pre-V45 backup=`/var/lib/nexus-quant/gatey-readonly-qualification/backups/pre-v45-f67c6645-20260826T043700Z.dump`，root:root/0600/link1、bytes=`772175`、SHA-256=`2b64cf5b0eae1b7c5059f3916f801cbc10e56587f942817c7f9641454397243f`，`pg_restore --list` 1902 entries。
- Migration/activation：从exact fat JAR提取45-file closed set，inventory=`d95b81280e47874c8afb616b551400019e571dd9221fd0571e4c1987cac8398c`与逐文件hash全部匹配，V45 SHA-256=`ffe71370fbd44a1da849fc5eb4f4d289081c6c0d67ffe96dc8b8bad4b775b293`。Pinned image digest=`sha256:782c5c207ffb5ac6336139fda4f4295bd9991ef63ad36919406d4268740069bb`自报Flyway 11.20.3；唯一一次V44→V45成功并validate 45/45，current/pending/failed=`45/0/0`。Runtime identity三字段原子替换，canonical Stop/Activate明确返回health UP，current=`f67c6645...`、MainPID=`1242602`、NRestarts=0、LIVE=false、kill=ENGAGED。
- ReleaseRoot path gate：以`/opt/nexus-quant/current`作为controller `ReleaseRoot`时，release verifier在任何DB grant、credential JIT、OKX或PLACE前以`RELEASE_LINK_INTEGRITY_VIOLATION`拒绝；immutable `/opt/nexus-quant/releases/f67c6645...` verifier独立PASS。失败后PLACE/SEND_STARTED/intent/clientOrderId/order/trade/ledger与临时权限仍全0；随后只使用exact immutable release path继续，不构成PLACE retry。
- V45 recovery facts：exact controller完成canonical bridge，`exchange_accounts.exchange_account_id=1`稳定映射到唯一legacy account；append-only recovery decision row=1，状态=`REPLACEMENT_ALLOWED_ZERO_INTENT`并绑定既有EXPIRED predecessor。旧session=`LIVE_RECONCILED`，旧lease继续`EXPIRED`；新authority=`ACTIVE`，新session=`APPROVAL_PENDING`。Replacement lease尚未创建，activeLease=0；各行精确identity保留在production关系与append-only event中，不复制到文档。
- Sizing incident：controller在`calculateOrderParameters`中因`price * maximum-lot quantity`产生第9位非零小数，被`setScale(8, UNNECESSARY)`抛出`ArithmeticException: Rounding necessary`。失败发生在ExactPilotBinding、replacement lease、ExecutionIntent、clientOrderId和PLACE前；最终PLACE/CANCEL/SEND_STARTED/intent/receipt/order/trade/ledger=`0/0/0/0/0/0/0/0`，kill=`ENGAGED/version3`，table/column/sequence临时权限=`0/0/0`。
- Minimal remediation：仅在`MinimalLivePilotControlService`把预算上限lot数向下对齐为能使`price * quantity`精确落入8位小数的最小整数倍；不舍入price或notional，不改变lot multiple、fee reserve、balance、10U cap、lease、状态机、provider或retry合同。Production-like regression以price=`111963.4`、lot=`0.00000001`证明8833 lots向下对齐为8830 lots，quantity=`0.00008830`、exact notional=`9.88636822`。
- Expired preparation remediation：sizing失败已留下一个无binding/lease/intent的`APPROVAL_PENDING` session；现有materialization每次生成新session identity，直接继续会命中single non-terminal hard gate。新增恢复只在同一V45 recovery decision与exact owner/account/credential/instrument/maxNotional下，锁定已过execution window且无exact binding、lease、ExecutionIntent或PLACE link的preparation；session经既有状态机`REJECT`终态化，authority在同一事务置`EXPIRED`。ACTIVE window、scope漂移、binding/lease/intent存在或多候选全部fail closed；不改变replacement decision/ordinal或PLACE retry合同。
- Validation：focused=`5/5 PASS`；final V1→V45一次性本地数据库、最小脱敏legacy account fixture下full Maven=`23/23 modules PASS`，`nq-app=313 tests / 0 failures / 0 errors / 34 conditional skips`，一次性数据库已删除；GateY minimal/release/runtime=`100/31/51 PASS`；Java governance PASS。共享本地`nexus_quant`因历史V44草稿checksum mismatch未repair；其首次full Maven失败不计为PASS。
- Preparation recovery validation：PostgreSQL required integration=`3/3 PASS / 0 skipped`，覆盖V44→V45、expired orphan REJECT+authority EXPIRED、并发replacement exactly-one与PLACE #2拒绝，随机schema已清理；final V1→V45一次性数据库full Maven再次`23/23 modules PASS`，GateY minimal=`100/100 PASS`，Java governance与Authority checker均PASS。
- Replacement row-lock incident：recovery release成功终态化第一个expired preparation并创建新的verified binding；replacement lease INSERT随后在V45 trigger的recovery decision `FOR KEY SHARE`处因临时角色缺少最小column UPDATE privilege而拒绝。失败发生在lease/ExecutionIntent/clientOrderId/PLACE前；最终PLACE/SEND_STARTED/intent/receipt/order/trade/ledger与临时权限仍全0，kill ENGAGED。Remediation只为`pilot_pre_place_recovery_decisions.decision_id`增加finally可撤销的临时column UPDATE，并让expired preparation recovery接受最多一个未消费binding；binding consume、lease、intent或PLACE link任一存在仍fail closed。Controller contract table/column window cardinality已同步，PostgreSQL required integration=`3/3 PASS / 0 skipped`且明确覆盖unconsumed binding，GateY minimal=`100/100 PASS`。
- Targeted review：只审查BigDecimal canonical scale、向下lot对齐、exact product、预算/费用、状态机与PLACE边界；P0=0、P1=0。无新增dependency、migration、API、transaction或外部调用。
- Current decision：`SIZING_AND_EXPIRED_PREPARATION_REMEDIATION_LOCAL_GREEN / CI_PENDING / CODE_ONLY_RELEASE_PENDING / V45_PRODUCTION_ACTIVE_SCHEMA / OLD_SESSION_TERMINATED / CANONICAL_LEGACY_ACCOUNT_BRIDGE_VERIFIED / REPLACEMENT_LEASE_NOT_CREATED / PLACE_0 / NO_PLACE_RETRY / LIVE_FALSE / KILL_ENGAGED / P0_0 / P1_0`。
- Next：精确提交本次两份Java文件、回归测试与本evidence，push `origin/dev`并等待exact-head CI；随后构建/安装/激活code-only V45 release，重新证明PLACE仍为0后继续同一Attempt-01。禁止Attempt-02与第二PLACE。

## Replacement terminal blocker 与 order identity remediation（2026-08-26）

- Exact deployment：expired-preparation recovery commit=`575d654aa78581c08ceb69e155650849b8f62044`的exact-head CI run=`32935187420 / success / 11 jobs`；row-lock remediation commit=`1762b76d84b702fcb9af07040dc51205fa878300`的CI run=`32936221449 / success / 11 jobs`。最终deployed release=`1762b76d...`、manifest=`e1a38ed64a2c0cb16f568aea17c955b33b824d438f6ffb0f25e8e425bc4598c9`、V45；两次均为code-only activation，无migration/backup/repair。
- Preparation recovery：第一个sizing orphan以DB zero-proof经状态机`REJECT`，第二个unconsumed-binding orphan也在execution window过期后合法`REJECT`；authority均`EXPIRED`。V45 predecessor继续`EXPIRED`且未修改。
- Replacement row-lock：controller脚本精确临时授予`pilot_pre_place_recovery_decisions.decision_id UPDATE`后，V45 trigger的`FOR KEY SHARE`通过；grant在finally撤销，table/column/sequence临时权限最终=`0/0/0`。
- Order identity incident：唯一replacement ordinal=1成功创建并短时ACTIVE，但`OrderCommandService`本地事务在任何ExecutionIntent、clientOrderId持久化或provider PLACE前失败。根因是runner把`leaseId|placeIntentId`错误传入`PlaceOrderRequest.strategyRunId`，长度超过`strategy_runs.run_id / orders.strategy_run_id VARCHAR(64)`，且operator pilot本就不得伪造strategy run identity。Runner finally将replacement lease终态化为`FAILED`、session置`RECONCILIATION_BLOCKED`、authority=`CLOSED`并将kill恢复为`ENGAGED/version5/PILOT_FAILED`。
- Final production facts：两条lease分别为predecessor `EXPIRED / ordinal0 / unconsumed`与唯一replacement `FAILED / ordinal1 / unconsumed`；activeLease=0。PLACE/CANCEL/SEND_STARTED/ExecutionIntent/ExecutionReceipt/Order/Trade/Ledger=`0/0/0/0/0/0/0/0`，transfer/withdraw=`0/0`，LIVE=false，临时权限=`0/0/0`。Credential material未读取或输出。
- Local order identity remediation：runner构造operator `PlaceOrderRequest`时固定`strategyRunId=null`，继续使用既有clientOrderId/idempotencyKey与普通`ord-<UUID>`本地order identity；不修改订单生成器、exchange request、lease或PLACE语义。Focused=`2/2 PASS`，GateY minimal contract=`100/100 PASS`；未部署该修复，未再次访问OKX/controller。
- Hard blocker：V45已用完本pilot唯一replacement；其终态`FAILED`不可复活，V45禁止second successor/replacement，且用户明确禁止修改历史lease、Attempt-02、第二replacement与第二PLACE。继续需要新的operator架构决定，不能因PLACE仍为0自行放宽replacement语义。
- Final decision：`BLOCKED / MAJOR_ARCHITECTURE_DECISION_REQUIRED / UNIQUE_REPLACEMENT_TERMINAL_PRE_PLACE / ORDER_IDENTITY_LOCAL_FIX_READY / NO_REAL_ORDER / PLACE_0 / CANCEL_0 / NO_PLACE_RETRY / ACTIVE_LEASE_0 / LIVE_FALSE / KILL_ENGAGED / NO_TRANSFER / NO_WITHDRAW / P0_0 / P1_1`。
- Next：operator必须选择以`NO_REAL_ORDER`关闭Attempt-01，或明确授权新的forward-only terminal-replacement recovery contract；新合同必须保持PLACE total<=1、retry=0，不得复活FAILED lease、不得手工SQL改状态、不得创建Attempt-02，并需独立说明是否允许且如何限制第二successor。当前禁止controller、PLACE、CANCEL、transfer、withdraw。

## V46 terminal lease regeneration 本地实现与复核（2026-08-26）

- Authorization：operator明确批准`PRE_PLACE_TERMINAL_LEASE_REGENERATION`，继续原Attempt-01；禁止Attempt-02、第二PLACE、PLACE retry、历史lease复活/复用/删除/修改与production手工DDL。Lease仅为短时执行窗口；Attempt-01 execution boundary继续承担exactly-once。
- Forward migration：只新增`V46__gate_y_attempt_level_terminal_lease_regeneration.sql`，未修改V42–V45。`replacement_ordinal`由SMALLINT前向扩为INTEGER，successor必须是唯一leaf predecessor的`ordinal+1`；移除V45 single-replacement特例，保留single-origin、single-open、per-predecessor successor唯一、decision唯一与全局PLACE/CANCEL唯一索引。
- Durable zero proof：新decision固定`PRE_PLACE_REGENERATION_ALLOWED`；decision与successor trigger均锁定predecessor并验证terminal、unconsumed、无successor、activeLease=0，以及全OPERATOR_PILOT lineage的PLACE link、SEND_STARTED、ExecutionIntent、ExecutionReceipt、Order、Trade/Fill、Ledger全部为0。Provider PLACE只能在durable SEND_STARTED之后发生，因此SEND_STARTED=0同时证明provider PLACE=0；任一事实出现后永久拒绝regeneration。
- Java/session：domain不再设置ordinal特例或task-specific上限，ordinal只从数据库判定返回；新lease reason固定`PRE_PLACE_TERMINAL_REGENERATION`。Predecessor session按当前状态经既有状态机收敛：ACTIVE/PAUSED→STOP、STOPPED→BEGIN_RECONCILE、RECONCILING→RECONCILE_BLOCK、RECONCILIATION_BLOCKED→RESOLVE_AND_CLOSE，preparation状态→KILL；terminal状态只读返回，不复活旧session或authority。
- Production-shaped regression：V45合法历史`lease0 EXPIRED / ordinal0 → lease1 FAILED / ordinal1`先落库，再执行精确V45→V46；随后验证ordinal2、ordinal3与任意正ordinal domain合同。并发regeneration最多一个winner；并发PLACE claimant最多一个winner；PLACE fact出现并将lease终态化后再次regeneration精确返回`REPLACEMENT_FORBIDDEN_SIDE_EFFECT_STARTED`。历史lineage update、重复successor/decision、ACTIVE/CONSUMED predecessor与ExecutionIntent/SEND_STARTED/Order/Receipt路径均拒绝。
- Order identity：current HEAD `f648064d1192a96a88d3ff1ee820e5038e0c7e0a`已包含operator synthetic `strategyRunId=null`修复；focused与full Maven覆盖production-shaped local order identity，不再把lease/intent组合冒充strategy run。该commit尚未部署，production仍运行`1762b76d...`。
- Validation：focused domain/service/migration contract=`12/12 PASS`；PostgreSQL required integration=`4/4 PASS / 0 skipped`，覆盖V1→V46、空V45→V46与带ordinal0/1历史事实的V45→V46，随机schema均已清理。Disposable V46数据库下full Maven=`23/23 modules PASS`，`nq-app=315 tests / 0 failures / 0 errors / 34 conditional skips`，数据库已强制删除。
- Governance regression：GateY exact/minimal/release/runtime=`7/100/31/51 PASS`，GateY4、GateY5 lock/post-restore均PASS；GateW frozen=`37/12/34 PASS`；current Authority errors=0，Java governance PASS。主工作区Shadow仍被既有`artifacts/pre-clean-3-pip-tmp` ACL阻断；应用同一diff/untracked V46文件的detached worktree复核`NEW_CODE_VIOLATION_COUNT=0`并已删除。Custom secret backstop=`13 files / 0 findings`；本机未安装gitleaks，pinned scan留待exact-head CI。
- Validation history：首次required PostgreSQL命令只因本机不存在`nexus_quant_test`而在SQLSTATE 3D000退出，改用既有`nexus_quant`随机schema后进入断言；V44 authority expiry trigger正确拒绝未到期EXPIRED夹具，改为真实3秒窗口后通过。GateY release regression首次仍期望migration count 45，builder self-test实际正确返回46；只同步contract期望为46后31/31通过。所有失败均未写成产品通过。
- Target cleanup：按operator授权删除仓库根`target/`的114个未跟踪可再生成文件，共258241937 bytes；删除前精确验证路径为`E:\Project\nexus-quant\target`且tracked count=0。未删除`backend/**/target`；后续Maven只重新生成模块构建输出。
- Targeted P0/P1 review：只审查attempt-level exactly-once、regeneration/PLACE concurrency、V45历史兼容、session terminalization、order identity与V46 migration。补充关闭PLACE后永久拒绝的测试覆盖缺口；最终P0=0、P1=0。
- Production boundary：本阶段production访问、backup、migration、deployment、credential JIT、OKX、controller、PLACE/CANCEL/transfer/withdraw均为0。最后已知production继续是V45 runtime=`1762b76d...`、lease0=`EXPIRED/unconsumed`、lease1=`FAILED/unconsumed`、activeLease=0、PLACE/SEND_STARTED/Intent/Receipt/Order/Trade/Ledger均0、LIVE=false、kill=ENGAGED；部署前必须重新只读确认，不能把历史值冒充current readback。
- Current decision：`V46_IMPLEMENTED / LOCAL_GREEN / PRE_PLACE_TERMINAL_LEASE_REGENERATION_VERIFIED_LOCALLY / ATTEMPT_LEVEL_EXACTLY_ONCE_VERIFIED_LOCALLY / ORDER_IDENTITY_FIX_VERIFIED / PLACE_0 / NO_PLACE_RETRY / LIVE_FALSE / KILL_ENGAGED / P0_0 / P1_0 / CI_PENDING / PRODUCTION_UNCHANGED`。
- Next：精确暂存V46、Java/repository/session变更、回归、GateY release migration-count合同与本evidence；commit `fix(gatey): generalize zero-side-effect pilot lease recovery`，push `origin/dev`并等待exact-head CI。CI全绿前禁止production backup/V45→V46/deployment/controller/PLACE。

## V46 production 与 dedicated execution scope remediation（2026-08-26）

- Commit/CI：V46 implementation commit=`979d69c760dc07f220e7c4cb7bf55385120c8992`已push `origin/dev`；exact-head `NQ CI Baseline` run=`32943454540 / completed / success / 10 jobs`，包含Backend Maven、PostgreSQL/Flyway、pinned Gitleaks、Java Shadow、no-outbound与E2E。
- Immutable release：canonical builder输出15 artifacts、schema target V46、source mode=`EXACT_GIT_COMMIT_BLOB_BYTES`，manifest=`5c0ac60becb2adf6f75e6f4330d41e1d65a03f02035f0d315af13b7c317850c3`。服务器installer与独立verify确认source/installed manifest一致，root/POSIX/link integrity通过，service user不可写；upload staging已删除。
- Migration closed set：从exact fat JAR内唯一`nq-infra` nested JAR提取46份migration并逐项比对manifest，版本连续V1..V46；V46 SHA-256=`fa0ccf7265841949ee77881c2e35c9f64065c1759fcaa3ac3f618cd4ca0b3ea1`，inventory=`cdbe1278f5d13b4640148a7b92cf79e1140bb12fa3555a6f981a0921b557719c`。
- Backup：最终pre-V46 backup=`/var/lib/nexus-quant/gatey-readonly-qualification/backups/pre-v46-979d69c7-20260826T075416Z.dump`，root:root/0600/link1、bytes=`787927`、SHA-256=`74874ad810e90c85844dcccf42e4d64bdf4fb3fb05dc67113a6296151238150a`，`pg_restore --list` 1848 entries。前三次尝试分别被runtime role新表权限、错误socket port与不存在的postgres DB role拒绝，partial均由trap删除；最终复用container内既有`nqgatew` local socket identity，不读取或输出password/container env。
- Migration/activation：pinned Flyway image digest=`sha256:782c5c207ffb5ac6336139fda4f4295bd9991ef63ad36919406d4268740069bb`自报11.20.3；pre-info精确为V45且只V46 pending，唯一一次migrate应用1条，validate 46/46，post-info V46 success，临时migration staging删除。旧runtime停止、三项release identity原子更新后canonical Activate+Health通过；current=`979d69c7...`、MainPID=`1253755`、NRestarts=0，未触发rollback。
- Pre-PLACE hard gate：独立health绑定exact release/source；DB为V46/failed0、kill=`ENGAGED/version5`、account1 bridge存在、credential2=`ACTIVE/VERIFIED/SUCCEEDED/TRADE/PASSED/withdraw=false`。历史lease0/1=`EXPIRED/FAILED`且unconsumed，leaf ordinal1无successor，decision=1、activeLease=0；PLACE/CANCEL/SEND_STARTED/Intent/Receipt/Order/Trade/Ledger与临时权限全部0。
- Controller incident：停止read-only runtime后只调用一次exact immutable controller。V46合法追加decision2并生成ordinal2 lease；runner调用`OrderCommandService`后在`MinimalPilotTradingVenueGateway.requirePlaceInvocation()`拒绝`PILOT_PROVIDER_SCOPE_REQUIRED`。Finally将ordinal2 lease置`FAILED/unconsumed`、session置`RECONCILIATION_BLOCKED`、kill恢复`ENGAGED/version7`，临时权限归零。
- No-PLACE proof：incident后decision=2，lineage=`ordinal0 EXPIRED → ordinal1 FAILED → ordinal2 FAILED`，activeLease=0；PLACE/CANCEL/SEND_STARTED/ExecutionIntent/ExecutionReceipt/Order/Trade/Ledger仍全部0。没有clientOrderId durable fact、provider PLACE或UNKNOWN，因此未执行query-by-clientOrderId；未再次调用controller/PLACE。
- RCA：前一修复正确将operator `PlaceOrderRequest.strategyRunId`设为null，避免本地Order伪造strategy identity；但gateway仍从该字段解析`leaseId|intentId`，形成互斥合同。修复为内部`PlaceOrderRequest.executionScopeId`：普通/strategy调用通过兼容构造器保持null；pilot显式传入`leaseId|placeIntentId`，gateway同时强制source正确、`strategyRunId==null`与两个UUID格式，不把execution scope持久化为strategy。
- Validation：focused=`13/13 PASS`，覆盖OrderCommand、dedicated scope正向、synthetic strategy scope反向与pilot request mapping；disposable V46 DB下full Maven=`23/23 modules PASS`、`nq-app=315 tests / 0 failures / 0 errors / 34 conditional skips`，数据库已删除。GateY minimal=`100/100 PASS`，Java governance PASS。
- Production boundary：当前production schema V46、current pointer仍为`979d69c7...`，runtime按controller合同保持stopped；kill ENGAGED、activeLease0、PLACE=0、无PLACE retry、transfer/withdraw=0。Execution-scope修复尚未commit/CI/deploy，禁止在此状态再次调用controller。
- Current decision：`EXECUTION_SCOPE_FIX_IMPLEMENTED / LOCAL_GREEN / V46_PRODUCTION_ACTIVE_SCHEMA / V46_RUNTIME_CURRENT_STOPPED / PRE_PLACE_TERMINAL_LEASE_REGENERATION_VERIFIED / PLACE_0 / NO_PLACE_RETRY / LIVE_FALSE / KILL_ENGAGED / P0_0 / P1_0 / CI_PENDING`。
- Next：精确提交五份execution-scope实现/测试与本evidence，push `origin/dev`并等待exact-head CI；随后构建/安装/激活code-only V46 release，再次证明全execution facts仍为0后继续Attempt-01。若届时出现PLACE/SEND_STARTED/UNKNOWN，只允许query/reconciliation，永久禁止第二PLACE。
