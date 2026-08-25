# GateY Minimal Live Pilot End-to-End — attempt-01

## 当前结论

`IMPLEMENTED / LOCAL_GREEN / CI_PENDING / NO_REAL_ORDER`（已实现 / 本地验证通过 / CI 待执行 / 未发送真实订单）。本文件继续作为 implementation→CI→deployment→pilot 的单一持续 evidence；V43、五类 prerequisite、permission refresh、BTC-USDT catalog refresh、current bestAsk binding 与自动 quantity 已完成本地实现和 P0/P1 review。exact-head commit/CI、production V42→V43、runtime activation 与真实 pilot 尚未执行，故仍保持 fail-closed。

```text
P0=0
P1=0
implementationCommit=PENDING
exactHeadCi=PENDING
productionDeployment=PASS_EXACT_HEAD_V42_ACTIVE
activeRuntime=c47c8db317bbbef64989f247b087752bf2b46a3c
activeManifest=de1f52359619e6f38fc4671ec5c091bb5019acf3d4f953e14d402d45f0377c50
operatorPilotParameters=ACCOUNT_1_CREDENTIAL_1_BTC_USDT_BUY_LIMIT_CAP_10
historicalIdentity=OWNER_2_ACCOUNT_1_CREDENTIAL_1
productionSorRecovery=PASS_PROVISIONED
currentPrerequisite=LOCAL_IMPLEMENTATION_VERIFIED_CI_PENDING
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
