# Phase6 L4 B3 Kill-in-flight qualification

Task classification：NQ-only / HIGH_RISK / L4_QUALIFICATION / REAL_PROCESS_KILL_SWITCH_PROOF。

Starting HEAD：`bbe5f8d98eb3a5dc28b9ebf9a80e4e9ba02b27bc`；branch=`audit/post-gatey-agent-baseline`；本地 HEAD 等于 origin tracking ref，起始 worktree clean、diff check 通过。stage=0、commit=NONE、push=NONE。

## Baseline 与范围

用户给定 B0/B1/B2 已接受基线。本轮只读核验 B1 exact-head pair=`d1cedb6debfaa3dbeacb0e95ef8599c69e5f9da3 / 34246407667`；B2 精确交付 pair=`e927fe107ce5cac4eb828f86b95561d977785b53 / 34327322619`，9/9 jobs SUCCESS；起始 HEAD 的 CI=`34329493444 / completed / success`。B2 失败 CI=`34318927962`仍作为历史保留。上述均不等于本轮 B3 候选 CI。

STATUS/ROADMAP 仍记载 pre-B0；本轮沿用用户明确授权的隔离 qualification 范围，不修 current authority，不根据这些冲突文档宣称 B3 ACCEPTED 或获得真实操作许可。B3 原无已提交 qualification evidence。本轮未发现新的 production P0/P1。

Real credential=0；real exchange=0；real provider=0；LIVE enable=0；production kill DISENGAGE=0。B3 仅 SIM。B2 既有回归中的 LIVE 仅是隔离 fixture 的持久化环境字段，不启用 LIVE runtime capability。

## 当前真实 canonical Kill contract

- Kill owner：`KillSwitchService` application service / `KillSwitchStateRepository` port / `JdbcKillSwitchStateRepository` PostgreSQL adapter。GLOBAL_TRADING 状态在 `kill_switch_states`，历史在 `kill_switch_events`。未知、缺记录、读库异常和未来时间戳 fail-closed。
- Canonical ENGAGE：`KillSwitchService.engage(expectedVersion, reasonCode, updatedBy, traceId)` → Spring transactional repository → row lock、version CAS、状态更新与事件追加；B3 调用真实服务，不以 Controller SQL 或内存 state 替代。生产代码另有 exact active pilot lease 专用 `disengageForPilot`；本轮没有调用它，不能根据旧 Javadoc 错称代码绝对不存在该方法。
- RiskGate check point：`OrderCommandService.placeOrder` → `OrderCommandWriteService.preparePlaceOrder`，其中真实 `PreTradeRiskService` / `KillSwitchRiskRule`（order=10）读 durable snapshot。拒绝记录 Risk decision=REJECT、rule/reason=`KILL_SWITCH_TRIGGERED`，Order=`RISK_REJECTED`，返回 completed result，不调用 venue。
- Mutation admission point：真实 Kill 检查在 prepare 事务内，随后 RISK_PASSED → SENT，事务提交后调用 `AdapterBackedTradingVenueGateway` → `OkxExchangeAdapter`。此 ordinary path 在 gateway / adapter 发送前没有第二次 Kill check。本次 A 截点已是 HTTP PLACE request 到达合成 venue、尚未 accepted，明确晚于最终 admission，故允许该请求接受一次，不要求已发出的请求变成零。
- 已 admitted / 已发送 / 已接受命令：ENGAGE 不撤销已通过 admission 的请求，不抹掉 venue fact，不自动 CANCEL。ACK 仍按现有 OCC finalize；不确定结果沿用 deferred/query-first recovery，不新增 Kill 机制。
- Reconciliation under Kill：`OkxRestReconcileService.reconcileOnce` → C2 reserve candidates → query order/fills → 本地 Order/Trade/Ledger 收敛。该路径没有 Kill 拦截。这里“只读恢复”指对 venue 只读，必要本地持久化照常发生；并非禁止本地账务写入。

代码定位：[Kill service](../../../backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchService.java)、[Kill repository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/risk/infra/jdbc/JdbcKillSwitchStateRepository.java)、[ordinary orchestration](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java)、[write admission](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java)、[ordinary reconciliation](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java)。

## Test-only 组成与确定时序

复用 B0Fixture、B0Processes、真实 Spring NQ JVM、B2SyntheticVenueMain、B2 账务 oracle；没有第二套交易链。Controller 使用 `nq_b0_reader`、REPEATABLE_READ business snapshots，无业务写连接。B3 bootstrap 只在 NQ JVM 启动前将新 disposable DB 的 Kill 置为 DISENGAGED/version2，并封存 fixture。仅该 DB 给非 owner `nq_b0_app` 增加 kill states UPDATE、events INSERT，以便真实 repository 执行 ENGAGE；默认 B0/B1/B2 仍撤销这些权限。NQ stdin 新命令只调用 canonical ENGAGE service，没有 SQL / DISENGAGE 接口；bootstrap 后场景不直接写 Kill。

每行全新 DB / Venue PID / NQ PID；restart 另有不同 NQ PID。PG16.15、Flyway V48、Java21.0.9 / Spring Boot3.5.10。镜像沿用当前 lock 中 postgres16 digest，`--pull=never`、tmpfs、loopback 映射；child environment 白名单、real RiskGate 和 write proxy 类型断言、唯一 synthetic HTTP origin 与 owned JDBC socket。

venue monitor 屏障在等待时释放锁，允许撮合/查询/control 实际并发；显式 HOLD/RELEASE 控制 acceptance 或 ACK。`KILL_DURABLE_OBSERVED` 是 Controller 读到 canonical ENGAGE commit 后才发送的观测标记，不写 Kill。因屏障仍闭合，A 的 acceptance 必定晚于 durable Kill；B/restart 的 accepted 必定早于 ENGAGE，ACK 未完成。先后关系使用同一个 Venue JVM 的递增 event sequence；epochMillis 记录真实时间，不跨 JVM 比较 nanoTime。ACK generated / response-delivered 分开；restart 后旧响应即使尝试发送，也不代表被已死亡 JVM 接收。

| Matrix | 截点与结果 | 独立 runs |
|---|---|---|
| A / PRE_ACCEPT | SENT + PLACE request received，accepted=0、ACK=0 → canonical ENGAGE → 新命令拒绝 → release acceptance → admitted PLACE 接受一次 → fills/query 收敛 | 3/3 PASS |
| B / POST_ACCEPT_PRE_ACK | accepted=1、ACK=0 → ENGAGE → 新命令拒绝 → fills4+3+3 → query-first FILLED → release stale ACK；完整 business snapshot 不变 | 3/3 PASS |
| C / PENDING_RECONCILIATION | accepted 与 3 笔 venue fills 已存在、本地 Trade=0 → ENGAGE → 新命令拒绝 → query/fill/Ledger 收敛 | 3/3 PASS |
| D / Post-Kill new command | 各行以不同 client_order_id 新提交 ordinary PLACE，真实 RiskGate 拒绝；调用前后完整 venue facts 相等 | 12/12 PASS |
| Restart / RESTART_PRE_ACK | accepted=1、local SENT、ACK=0 → ENGAGE → kill NQ A 并确认退出 → fills → NQ B / 同 PG → Kill 保持 ENGAGED → ordinary query-first recovery | 3/3 PASS |

## PID、状态、计数与实际时间

共12次 B3 real-process runs；27个互异 child PIDs（12 Venue + 12 NQ A + 3 NQ B），均不同于 Controller。每行 venue accepted PLACE=1、received PLACE requests=1、blind PLACE retry=0、CANCEL=0。新命令为全新 account/client 幂等身份，不以旧订单幂等命中冒充 RiskGate 拒绝；restart 后另外重放该已拒绝身份保持拒绝，这一重放不另计新的 D 场景。

每行实际 fills=4/3/3、fees=0/0.01/0.02，Trade=3、Ledger entries/events=10/10。逐 fill 验证价格、数量、fee、USDT、本金与 fee DEBIT/CREDIT 幂等键，持仓=10；重复报告及普通 replay 的整个 business snapshot 相等。成对账务的 USDT 净分录=0，不把它冒充真实钱包结算证明。

| Run | DB identity reference | NQ A | Venue | NQ B | admitted Order status/version | new Order status/version | Kill version |
|---|---|---:|---:|---:|---|---|---:|
| PRE_ACCEPT-1 | `SYNTH-L4:B3:R01:DATABASE:001` | 25992 | 65956 | — | FILLED/4 | RISK_REJECTED/1 | 3 |
| POST_ACCEPT_PRE_ACK-1 | `SYNTH-L4:B3:R02:DATABASE:001` | 36376 | 69420 | — | FILLED/3 | RISK_REJECTED/1 | 3 |
| PENDING_RECONCILIATION-1 | `SYNTH-L4:B3:R03:DATABASE:001` | 45612 | 61988 | — | FILLED/4 | RISK_REJECTED/1 | 3 |
| RESTART_PRE_ACK-1 | `SYNTH-L4:B3:R04:DATABASE:001` | 48084 | 25188 | 45068 | FILLED/3 | RISK_REJECTED/1 | 3 |
| PRE_ACCEPT-2 | `SYNTH-L4:B3:R05:DATABASE:001` | 40784 | 52816 | — | FILLED/4 | RISK_REJECTED/1 | 3 |
| POST_ACCEPT_PRE_ACK-2 | `SYNTH-L4:B3:R06:DATABASE:001` | 70792 | 31992 | — | FILLED/3 | RISK_REJECTED/1 | 3 |
| PENDING_RECONCILIATION-2 | `SYNTH-L4:B3:R07:DATABASE:001` | 26320 | 15100 | — | FILLED/4 | RISK_REJECTED/1 | 3 |
| RESTART_PRE_ACK-2 | `SYNTH-L4:B3:R08:DATABASE:001` | 9572 | 75296 | 45652 | FILLED/3 | RISK_REJECTED/1 | 3 |
| PRE_ACCEPT-3 | `SYNTH-L4:B3:R09:DATABASE:001` | 40032 | 36340 | — | FILLED/4 | RISK_REJECTED/1 | 3 |
| POST_ACCEPT_PRE_ACK-3 | `SYNTH-L4:B3:R10:DATABASE:001` | 52108 | 4168 | — | FILLED/3 | RISK_REJECTED/1 | 3 |
| PENDING_RECONCILIATION-3 | `SYNTH-L4:B3:R11:DATABASE:001` | 56028 | 68632 | — | FILLED/4 | RISK_REJECTED/1 | 3 |
| RESTART_PRE_ACK-3 | `SYNTH-L4:B3:R12:DATABASE:001` | 55168 | 49140 | 34052 | FILLED/3 | RISK_REJECTED/1 | 3 |

下面保留每行完整 canonical 观测快照，含 Kill transition time、acceptance/ACK/fill/query 时间与 sequence、requestId 固定测试标签 b0-request 及实际稳定 client/order identity（幂等身份为account/client，不依赖该描述性requestId）、Order/version、Trade/Ledger facts、replay 与 restart 快照。原始运行身份和进程日志只留 `backend/nq-app/target/`；可从 `b3-targeted-attempt02.log` 的 B3_EVIDENCE 行定位 raw 文件，不向 Git 发布 raw identity 或其编码/hash 替身。

## 失败历史与自查

attempt01 在 A/R1 的新命令断言失败：测试误把 Order 终态写为 REJECTED，真实 canonical 状态为 RISK_REJECTED。真实 NQ 日志已返回 RISK_REJECTED；canonical ENGAGE committed、venue facts 未增加。failure=`expected: <1> but was: <0>`，位置为旧版 B3RealProcessProofTest 第93行。没有发现 production correctness defect；只修测试 oracle 为精确 RISK_REJECTED，随后 attempt02 全矩阵12/12通过。没有修 production 后继续宣称 qualification PASS。

失败当时 NQ PID=24312、Venue PID=40892；进程及 owned PG 已清理，首轮 Maven exit1，34.449s。原始失败日志保留于 target/b3-targeted-attempt01.log。下一折叠段保留该次 canonical 失败快照。一次性 oracle 状态拼写不属于已关闭的 synthetic identity / hash drift 重复机制；本轮沿用现有导出器，没有批次例外或 allowlist 修改。

SELF_REVIEWED：逐项检查 canonical path、真实 risk/write proxy、bootstrap 封存、acceptance/ACK 屏障位置、接收请求计数（重复请求即使拒绝也计数）、不同 client identity、Kill 状态+事件+version、只读 checker、OCC snapshot equality、逐 fill 账务、PID 集合与 cleanup。test-only 候选按用户 Review policy 不另开 Independent Review。

## 验证与交付边界

B3 targeted attempt02：16 tests、0 failures/errors/skips，exit0，2:14；其中 B3 JUnit1 承载12次矩阵，B0 fixture safety8、Kill service/rule7。命令：

```powershell
mvn -o -f backend/pom.xml -pl nq-app -am '-Dnq.b3=true' '-Dtest=B3RealProcessProofTest,B0FixtureSafetyTest,KillSwitchServiceTest,KillSwitchRiskRuleTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

相关回归共60个不同 JUnit tests，最终全部具备有效通过证据。首次组合执行为59 PASS / 1 fixture-parameter failure / 0 errors / 0 skips，exit1，12:09；单项修正后1/1 PASS、exit0，14.875s。没有把首次组合 run 写成 exit0，也没有机械重跑已有效通过的59项。

| Suite | Tests | 最终结果 |
|---|---:|---|
| KillSwitchRestartDurabilityPostgresIntegrationTest | 1 | 首次参数拒绝；单项补跑 PASS |
| B0FixtureSafetyTest | 8 | PASS |
| B0RealProcessHarnessTest | 1 | PASS |
| B1RealProcessProofTest | 1 | PASS |
| B2RealProcessProofTest | 1 | PASS |
| B2TerminalCorrectionPostgresIntegrationTest | 13 | PASS |
| L4PlanBlockerPostgresIntegrationTest | 22 | PASS |
| ReconciliationCursorPostgresIntegrationTest | 6 | PASS |
| KillSwitchRiskRuleTest | 2 | PASS |
| KillSwitchServiceTest | 5 | PASS |

B0 原 harness smoke通过；B1 JUnit承载6/6 real-process runs；B2 JUnit承载48/48 real-process runs，SIM/LIVE fixture 字段各24。B2 PostgreSQL13、C1/L4 blockers22、C2 cursor6通过。本轮没有重新审 C1/B1/B2 的已接受合同。额外 Kill PostgreSQL1是旧的双 Spring-context durability/optimistic/idempotent 测试，属于 isolated schema 回归，不冒充 B3 的跨 JVM 证明；其测试专用 schema seed 与 B3 pre-start sealed DB fixture 相互独立。

第二项失败历史：首轮 regression runner 漏传该旧测试独立要求的 `nq.postgres.smoke.password`，`configured()` 在业务测试前拒绝，failure=`missing required local disposable PostgreSQL properties`，不是数据库或 Kill 业务失败。补齐非秘密 disposable fixture 值后仅重跑该类，无生产或测试源码改动。日志分别保留 `target/b3-regressions/targeted.log` 与 `target/b3-kill-pg-attempt02/targeted.log`。

回归使用本轮新建、label/ID校验的loopback PG16 tmpfs实例，数据库名 `nq_l4_blocker`；没有读取已有数据库或凭证。组合 Maven 使用 `-o -f backend/pom.xml -pl nq-app -am`、上述10个精确 `-Dtest=` 类、`-Dsurefire.failIfNoSpecifiedTests=false`、`-Dnq.b0=true -Dnq.b1=true -Dnq.b2=true -Dnq.b2.pg=true -Dnq.l4.blockers.enabled=true`，显式传入本轮 owned URL 的 `spring.datasource.*` 与 `nq.postgres.smoke.*`。子进程使用环境白名单；首次只漏后者 password，补跑只选择 KillSwitchRestartDurabilityPostgresIntegrationTest 并传入全部 smoke URL/user/password/required 参数。密码字符串仅为公开隔离 fixture `review-public-fixture`，容器使用本轮 loopback trust，不是实际 credential。

B3共27个child进程在各场景结束时确认退出，12个新DB均验证删除，owned PG删除后remaining0；首轮失败的2个child同样清理。B0/B1/B2 regression harness记录各自ProcessHandle退出与owned容器删除；外层两个独占PG在label/ID核对后删除。未清理或停止任何非本轮资源。


Synthetic export：既有 exporter tests4/4；本批12个完整证明、336个 typed identities 的双射与完整逆映射相等，raw identity leaks=0；失败attempt使用独立 `B3-A1` namespace，成功矩阵使用 `B3`，避免不同attempt共用identity references。没有改 runtime 随机身份、导出器或历史兼容 allowlist。pinned Gitleaks8.18.4 使用当前 CI config 与参数，6/6秘密负例 REJECT、3339个安全文件扫描0 findings；该既有扫描工具不自动包含此未跟踪 B3 Markdown，因此另对该完整文件执行同配置扫描，最终完整文件同配置扫描 exit0、0 findings。stage-assets scanned1820、exceptions173、errors0；没有 bound asset 内容变化。

Production files changed=0；1308个 tracked src/main 文件当前 raw SHA-256 manifest fingerprint=`9a1defa244a7dcd829be7c5c50b95b7902253da3ebce9d4b64e9f09677c44c73`。原始字节 fingerprint 在验证前后相同，起始及终态Git均无production改动；1308文件与HEAD内容一致。额外 checkout-filter比较发现12个既有LF/混合换行文件，与HEAD差异仅CRLF表示，未修改或归一化这些文件，不把自动重新展开的CRLF当作起始raw字节。production、Flyway、.github、AGENTS/Skills、current authority、frozen evidence 均无变更；未运行 Full Maven。本轮 B3 CI=NOT_RUN，必须后续精确交付的 exact-head canonical CI 验收。

Files changed：B0Fixture.java（封存前 B3 ENGAGE 权限）、B0NqProcessMain.java（真实 ENGAGE、新 client PLACE）、B2SyntheticVenueMain.java（pre-accept barrier、请求计数与时间）、B2RealProcessProofTest.java（复用 oracle 的包内可见性）、B3RealProcessProofTest.java（新矩阵）及本唯一 B3 evidence。

P0=0；P1=0。Final decision：

```text
PASS / L4_B3_KILL_IN_FLIGHT_PROVEN / KILL_ENGAGED_DURABLE /
NO_POST_KILL_NEW_MUTATION / IN_FLIGHT_TRUTH_CONVERGED / NO_BLIND_RETRY /
RESTART_PRESERVED_KILL / P0_0 / P1_0 / READY_FOR_PRECISE_DELIVERY
```

B3=`QUALIFIED / READY_FOR_DELIVERY`；review=`SELF_REVIEWED / READY_FOR_PRECISE_DELIVERY`。尚非 ACCEPTED。Commit recommendation：本候选可进入精确交付任务，届时按用户授权操作并以 exact-head canonical CI 验收。

Limitations：隔离 Windows/PG16 real-process qualification；没有真实 credential/provider/exchange、生产或 LIVE enable、Linux/压力/容量测试，不宣称 B4–B6 或 full L4。外部已发送且通过 admission 的请求可能在 ENGAGE 后接受一次，这是已核实的 ordinary contract。本轮未测试修改该合同或自动撤单语义。

成功后的 Next action：`NQ-GATEAUDIT-PHASE6-L4-B3-PRECISE-DELIVERY`。本轮 stage=0、commit=NONE、push=NONE；可建议进入精确交付，但未执行或授权扩展后续 Git 动作。


最终test-only源文件 raw SHA-256：

| File | SHA-256 |
|---|---|
| B0Fixture.java | `0f8dc76540eb6af8fd1bcdb43d5a4044b12ff00ed1a3bd5886ed80f6d6208a76` |
| B0NqProcessMain.java | `ebf6e8ebc237c0850d99024bcf8a5619aef8e02e2e23137d7ad79085cf006ce0` |
| B2SyntheticVenueMain.java | `b516a014a20d216e5e5fa6d201f4b064ce1c3d46341ff8e8edc8a905e2b07135` |
| B2RealProcessProofTest.java | `ffcc38afee63f39e60ebf1a18538f1c370d3e016a24694fdad4fe697b6759799` |
| B3RealProcessProofTest.java | `8be98e2d03595f47885dd14fe5c1966984f0bb1848a4fb51ab612be342001486` |

回归后仅把一处既有harness注释同步为“可并行对账或canonical ENGAGE”；没有新的行为改动。最终 diff check 与本文件相对链接5/5通过。默认PS5脚本调用曾被本机execution policy阻止，改用已安装pwsh运行同一只读checker通过，未更改execution policy。

## 原始失败的 canonical 记录

<details>
<summary>attempt01 / PRE_ACCEPT-1 / test oracle failure</summary>

```json
{
  "scenario": "PRE_ACCEPT",
  "repeat": 1,
  "controllerPid": 31596,
  "orderEnvironment": "SIM",
  "venuePid": 40892,
  "database": "SYNTH-L4:B3-A1:R01:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:7602",
  "nqPid": 24312,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3-A1:R01:ORDER:001",
        "trace_id": "SYNTH-L4:B3-A1:R01:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3-A1:R01:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:54:25.521112+08:00",
        "request_id": "SYNTH-L4:B3-A1:R01:REQUEST:001",
        "updated_at": "2026-09-09T16:54:25.569916+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3-A1:R01:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3-A1:R01:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3-A1:R01:TRACE:001",
        "created_at": "2026-09-09T16:54:25.514263+08:00",
        "risk_event_id": "SYNTH-L4:B3-A1:R01:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3-A1:R01:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 40892,
    "places": 0,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": null,
    "fills": [],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_ACCEPTANCE",
        "nanoTime": 843538239471500,
        "epochMillis": 1788944065476
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843538370729800,
        "epochMillis": 1788944065608
      }
    ]
  },
  "engageStartedEpochMillis": 1788944065642,
  "engageCompletedEpochMillis": 1788944065746,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3-A1:R01:TRACE:002",
      "updated_at": "2026-09-09T16:54:25.643997+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3-A1:R01:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:54:21.342975+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3-A1:R01:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:54:25.643997+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "result": "FAIL",
  "failureType": "org.opentest4j.AssertionFailedError",
  "failure": "expected: <1> but was: <0>"
}
```

</details>

## attempt02 完整 canonical 矩阵

<details>
<summary>PRE_ACCEPT-1 / PASS</summary>

```json
{
  "scenario": "PRE_ACCEPT",
  "repeat": 1,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 65956,
  "database": "SYNTH-L4:B3:R01:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:8434",
  "nqPid": 25992,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R01:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R01:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.308955+08:00",
        "request_id": "SYNTH-L4:B3:R01:REQUEST:001",
        "updated_at": "2026-09-09T16:55:35.35795+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R01:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R01:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.337123+08:00",
        "risk_event_id": "SYNTH-L4:B3:R01:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R01:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 65956,
    "places": 0,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": null,
    "fills": [],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_ACCEPTANCE",
        "nanoTime": 843608027120100,
        "epochMillis": 1788944135264
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843608160982200,
        "epochMillis": 1788944135398
      }
    ]
  },
  "engageStartedEpochMillis": 1788944135433,
  "engageCompletedEpochMillis": 1788944135536,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R01:TRACE:002",
      "updated_at": "2026-09-09T16:55:35.435282+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:55:31.134919+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:55:35.435282+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R01:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R01:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.543881+08:00",
        "request_id": "SYNTH-L4:B3:R01:REQUEST:001",
        "updated_at": "2026-09-09T16:55:35.549017+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R01:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R01:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R01:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.308955+08:00",
        "request_id": "SYNTH-L4:B3:R01:REQUEST:001",
        "updated_at": "2026-09-09T16:55:35.35795+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R01:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R01:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.547046+08:00",
        "risk_event_id": "SYNTH-L4:B3:R01:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R01:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.337123+08:00",
        "risk_event_id": "SYNTH-L4:B3:R01:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R01:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "recoveryStartedEpochMillis": 1788944135780,
  "recoveryCompletedEpochMillis": 1788944135881,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R01:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R01:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.543881+08:00",
        "request_id": "SYNTH-L4:B3:R01:REQUEST:001",
        "updated_at": "2026-09-09T16:55:35.549017+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R01:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R01:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R01:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.308955+08:00",
        "request_id": "SYNTH-L4:B3:R01:REQUEST:001",
        "updated_at": "2026-09-09T16:55:35.803771+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R01:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R01:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R01:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:55:35.775+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R01:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R01:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.807947+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R01:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R01:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R01:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R01:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R01:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.830748+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R01:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R01:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R01:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R01:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R01:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.847597+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R01:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R01:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R01:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:55:35.775+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:35.775+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:010"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:004"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:006"
        },
        "ledger_event_id": 4
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:55:35.855973+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:55:35.775+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:55:35.775+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R01:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.547046+08:00",
        "risk_event_id": "SYNTH-L4:B3:R01:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R01:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.337123+08:00",
        "risk_event_id": "SYNTH-L4:B3:R01:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R01:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R01:TRACE:002",
      "updated_at": "2026-09-09T16:55:35.435282+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:55:31.134919+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:55:35.435282+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R01:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R01:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.543881+08:00",
        "request_id": "SYNTH-L4:B3:R01:REQUEST:001",
        "updated_at": "2026-09-09T16:55:35.549017+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R01:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R01:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R01:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.308955+08:00",
        "request_id": "SYNTH-L4:B3:R01:REQUEST:001",
        "updated_at": "2026-09-09T16:55:35.803771+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R01:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R01:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R01:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:55:35.775+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R01:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R01:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.807947+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R01:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R01:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R01:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R01:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R01:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.830748+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R01:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R01:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R01:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R01:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R01:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.847597+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R01:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R01:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R01:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:55:35.775+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:35.775+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R01:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R01:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:010"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:004"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R01:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R01:ENTRY:006"
        },
        "ledger_event_id": 4
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:55:35.855973+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:55:35.775+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:55:35.775+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.815062+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:55:35.777+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.83384+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:55:35.778+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:35.850414+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R01:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.547046+08:00",
        "risk_event_id": "SYNTH-L4:B3:R01:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R01:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R01:TRACE:001",
        "created_at": "2026-09-09T16:55:35.337123+08:00",
        "risk_event_id": "SYNTH-L4:B3:R01:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R01:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 65956,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R01:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R01:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944135665"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R01:FILL:001",
        "ordId": "SYNTH-L4:B3:R01:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944135775"
      },
      {
        "tradeId": "SYNTH-L4:B3:R01:FILL:002",
        "ordId": "SYNTH-L4:B3:R01:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944135777"
      },
      {
        "tradeId": "SYNTH-L4:B3:R01:FILL:003",
        "ordId": "SYNTH-L4:B3:R01:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944135778"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_ACCEPTANCE",
        "nanoTime": 843608027120100,
        "epochMillis": 1788944135264
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843608160982200,
        "epochMillis": 1788944135398
      },
      {
        "sequence": 4,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843608303141800,
        "epochMillis": 1788944135540
      },
      {
        "sequence": 5,
        "type": "RELEASE_ACCEPTANCE",
        "nanoTime": 843608420302400,
        "epochMillis": 1788944135658
      },
      {
        "sequence": 6,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843608427777400,
        "epochMillis": 1788944135665
      },
      {
        "sequence": 7,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843608427789700,
        "epochMillis": 1788944135665
      },
      {
        "sequence": 8,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843608428167100,
        "epochMillis": 1788944135665
      },
      {
        "sequence": 9,
        "type": "FILL",
        "nanoTime": 843608537677400,
        "epochMillis": 1788944135775,
        "tradeId": "SYNTH-L4:B3:R01:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 10,
        "type": "FILL",
        "nanoTime": 843608539563900,
        "epochMillis": 1788944135777,
        "tradeId": "SYNTH-L4:B3:R01:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 11,
        "type": "FILL",
        "nanoTime": 843608541038700,
        "epochMillis": 1788944135778,
        "tradeId": "SYNTH-L4:B3:R01:FILL:003",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 12,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843608542248900,
        "epochMillis": 1788944135779
      },
      {
        "sequence": 13,
        "type": "QUERY_ORDER",
        "nanoTime": 843608557538500,
        "epochMillis": 1788944135795,
        "state": "filled"
      },
      {
        "sequence": 14,
        "type": "QUERY_FILLS",
        "nanoTime": 843608559678400,
        "epochMillis": 1788944135796,
        "reportCount": 6
      },
      {
        "sequence": 15,
        "type": "QUERY_FILLS",
        "nanoTime": 843608665715600,
        "epochMillis": 1788944135903,
        "reportCount": 6
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>

<details>
<summary>POST_ACCEPT_PRE_ACK-1 / PASS</summary>

```json
{
  "scenario": "POST_ACCEPT_PRE_ACK",
  "repeat": 1,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 69420,
  "database": "SYNTH-L4:B3:R02:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:8459",
  "nqPid": 36376,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R02:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R02:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:43.698217+08:00",
        "request_id": "SYNTH-L4:B3:R02:REQUEST:001",
        "updated_at": "2026-09-09T16:55:43.744515+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R02:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R02:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:43.726483+08:00",
        "risk_event_id": "SYNTH-L4:B3:R02:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R02:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 69420,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R02:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R02:VENUE:001",
      "instId": "BTC-USDT",
      "state": "live",
      "px": "100",
      "sz": "10",
      "accFillSz": "0",
      "avgPx": "0",
      "uTime": "1788944143795"
    },
    "fills": [],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843616423069100,
        "epochMillis": 1788944143660
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843616551215000,
        "epochMillis": 1788944143789
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843616558246600,
        "epochMillis": 1788944143795
      }
    ]
  },
  "engageStartedEpochMillis": 1788944143820,
  "engageCompletedEpochMillis": 1788944143925,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R02:TRACE:002",
      "updated_at": "2026-09-09T16:55:43.821856+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:55:39.538804+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:55:43.821856+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R02:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R02:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:43.93262+08:00",
        "request_id": "SYNTH-L4:B3:R02:REQUEST:001",
        "updated_at": "2026-09-09T16:55:43.937191+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R02:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R02:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R02:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:43.698217+08:00",
        "request_id": "SYNTH-L4:B3:R02:REQUEST:001",
        "updated_at": "2026-09-09T16:55:43.744515+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R02:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R02:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:43.936101+08:00",
        "risk_event_id": "SYNTH-L4:B3:R02:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R02:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:43.726483+08:00",
        "risk_event_id": "SYNTH-L4:B3:R02:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R02:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "recoveryStartedEpochMillis": 1788944144061,
  "recoveryCompletedEpochMillis": 1788944144172,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R02:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R02:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:43.93262+08:00",
        "request_id": "SYNTH-L4:B3:R02:REQUEST:001",
        "updated_at": "2026-09-09T16:55:43.937191+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R02:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R02:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R02:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:43.698217+08:00",
        "request_id": "SYNTH-L4:B3:R02:REQUEST:001",
        "updated_at": "2026-09-09T16:55:44.088134+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R02:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R02:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R02:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:55:44.057+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R02:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R02:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.093078+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R02:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R02:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R02:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R02:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R02:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.115221+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R02:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R02:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R02:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R02:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R02:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.131863+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R02:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R02:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R02:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:55:44.057+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:44.057+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:003"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:005"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:010"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:004"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:006"
        },
        "ledger_event_id": 3
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:55:44.140336+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:55:44.057+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:55:44.057+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R02:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:43.936101+08:00",
        "risk_event_id": "SYNTH-L4:B3:R02:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R02:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:43.726483+08:00",
        "risk_event_id": "SYNTH-L4:B3:R02:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R02:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "staleAckSnapshotUnchanged": true,
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R02:TRACE:002",
      "updated_at": "2026-09-09T16:55:43.821856+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:55:39.538804+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:55:43.821856+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R02:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R02:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:43.93262+08:00",
        "request_id": "SYNTH-L4:B3:R02:REQUEST:001",
        "updated_at": "2026-09-09T16:55:43.937191+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R02:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R02:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R02:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:43.698217+08:00",
        "request_id": "SYNTH-L4:B3:R02:REQUEST:001",
        "updated_at": "2026-09-09T16:55:44.088134+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R02:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R02:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R02:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:55:44.057+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R02:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R02:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.093078+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R02:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R02:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R02:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R02:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R02:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.115221+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R02:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R02:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R02:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R02:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R02:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.131863+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R02:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R02:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R02:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:55:44.057+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:44.057+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R02:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R02:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:003"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:005"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:010"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:004"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R02:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R02:ENTRY:006"
        },
        "ledger_event_id": 3
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:55:44.140336+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:55:44.057+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:55:44.057+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.100116+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:55:44.058+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.117907+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:55:44.059+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:44.134645+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R02:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:43.936101+08:00",
        "risk_event_id": "SYNTH-L4:B3:R02:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R02:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R02:TRACE:001",
        "created_at": "2026-09-09T16:55:43.726483+08:00",
        "risk_event_id": "SYNTH-L4:B3:R02:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R02:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 69420,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R02:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R02:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944143795"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R02:FILL:001",
        "ordId": "SYNTH-L4:B3:R02:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944144057"
      },
      {
        "tradeId": "SYNTH-L4:B3:R02:FILL:002",
        "ordId": "SYNTH-L4:B3:R02:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944144058"
      },
      {
        "tradeId": "SYNTH-L4:B3:R02:FILL:003",
        "ordId": "SYNTH-L4:B3:R02:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944144059"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843616423069100,
        "epochMillis": 1788944143660
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843616551215000,
        "epochMillis": 1788944143789
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843616558246600,
        "epochMillis": 1788944143795
      },
      {
        "sequence": 5,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843616691370600,
        "epochMillis": 1788944143928
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843616820045900,
        "epochMillis": 1788944144057,
        "tradeId": "SYNTH-L4:B3:R02:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843616821299800,
        "epochMillis": 1788944144058,
        "tradeId": "SYNTH-L4:B3:R02:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843616822890500,
        "epochMillis": 1788944144059,
        "tradeId": "SYNTH-L4:B3:R02:FILL:003",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 9,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843616824285000,
        "epochMillis": 1788944144061
      },
      {
        "sequence": 10,
        "type": "QUERY_ORDER",
        "nanoTime": 843616838883600,
        "epochMillis": 1788944144075,
        "state": "filled"
      },
      {
        "sequence": 11,
        "type": "QUERY_FILLS",
        "nanoTime": 843616844784600,
        "epochMillis": 1788944144081,
        "reportCount": 6
      },
      {
        "sequence": 12,
        "type": "QUERY_FILLS",
        "nanoTime": 843616953001900,
        "epochMillis": 1788944144190,
        "reportCount": 6
      },
      {
        "sequence": 13,
        "type": "RELEASE_PLACE",
        "nanoTime": 843617075490400,
        "epochMillis": 1788944144312
      },
      {
        "sequence": 14,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843617075864000,
        "epochMillis": 1788944144313
      },
      {
        "sequence": 15,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843617076064600,
        "epochMillis": 1788944144313
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>

<details>
<summary>PENDING_RECONCILIATION-1 / PASS</summary>

```json
{
  "scenario": "PENDING_RECONCILIATION",
  "repeat": 1,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 61988,
  "database": "SYNTH-L4:B3:R03:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:8475",
  "nqPid": 45612,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ACKED_BY_ADAPTER",
        "status": "ACCEPTED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R03:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R03:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:53.603659+08:00",
        "request_id": "SYNTH-L4:B3:R03:REQUEST:001",
        "updated_at": "2026-09-09T16:55:53.747116+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R03:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R03:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R03:VENUE:001"
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R03:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:53.631537+08:00",
        "risk_event_id": "SYNTH-L4:B3:R03:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R03:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 61988,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R03:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R03:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944153741"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R03:FILL:001",
        "ordId": "SYNTH-L4:B3:R03:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944153818"
      },
      {
        "tradeId": "SYNTH-L4:B3:R03:FILL:002",
        "ordId": "SYNTH-L4:B3:R03:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944153820"
      },
      {
        "tradeId": "SYNTH-L4:B3:R03:FILL:003",
        "ordId": "SYNTH-L4:B3:R03:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944153822"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843626496809100,
        "epochMillis": 1788944153734
      },
      {
        "sequence": 3,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843626503841700,
        "epochMillis": 1788944153741
      },
      {
        "sequence": 4,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843626503854500,
        "epochMillis": 1788944153741
      },
      {
        "sequence": 5,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843626504528400,
        "epochMillis": 1788944153741
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843626580927200,
        "epochMillis": 1788944153818,
        "tradeId": "SYNTH-L4:B3:R03:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843626582966200,
        "epochMillis": 1788944153820,
        "tradeId": "SYNTH-L4:B3:R03:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843626584453200,
        "epochMillis": 1788944153822,
        "tradeId": "SYNTH-L4:B3:R03:FILL:003",
        "qty": "3",
        "fee": "0.02"
      }
    ]
  },
  "engageStartedEpochMillis": 1788944153835,
  "engageCompletedEpochMillis": 1788944153940,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R03:TRACE:002",
      "updated_at": "2026-09-09T16:55:53.836094+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:55:49.556407+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:55:53.836094+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R03:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R03:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:53.947272+08:00",
        "request_id": "SYNTH-L4:B3:R03:REQUEST:001",
        "updated_at": "2026-09-09T16:55:53.952471+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R03:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ACKED_BY_ADAPTER",
        "status": "ACCEPTED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R03:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R03:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:53.603659+08:00",
        "request_id": "SYNTH-L4:B3:R03:REQUEST:001",
        "updated_at": "2026-09-09T16:55:53.747116+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R03:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R03:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R03:VENUE:001"
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R03:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:53.948762+08:00",
        "risk_event_id": "SYNTH-L4:B3:R03:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R03:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:53.631537+08:00",
        "risk_event_id": "SYNTH-L4:B3:R03:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R03:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "recoveryStartedEpochMillis": 1788944154060,
  "recoveryCompletedEpochMillis": 1788944154175,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R03:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R03:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:53.947272+08:00",
        "request_id": "SYNTH-L4:B3:R03:REQUEST:001",
        "updated_at": "2026-09-09T16:55:53.952471+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R03:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R03:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R03:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:53.603659+08:00",
        "request_id": "SYNTH-L4:B3:R03:REQUEST:001",
        "updated_at": "2026-09-09T16:55:54.081965+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R03:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R03:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R03:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:55:53.818+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R03:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R03:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.085241+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R03:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R03:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R03:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R03:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R03:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.107011+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R03:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R03:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R03:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R03:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R03:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.123263+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R03:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R03:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R03:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:55:53.818+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:53.818+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:003:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:003:LEDGER:2"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:007"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:009"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:001"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:008"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:004"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:010"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:002"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:006"
        },
        "ledger_event_id": 4
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:55:54.133416+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:55:53.818+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:55:53.818+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R03:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:53.948762+08:00",
        "risk_event_id": "SYNTH-L4:B3:R03:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R03:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:53.631537+08:00",
        "risk_event_id": "SYNTH-L4:B3:R03:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R03:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R03:TRACE:002",
      "updated_at": "2026-09-09T16:55:53.836094+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:55:49.556407+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:55:53.836094+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R03:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R03:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:53.947272+08:00",
        "request_id": "SYNTH-L4:B3:R03:REQUEST:001",
        "updated_at": "2026-09-09T16:55:53.952471+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R03:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R03:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R03:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:53.603659+08:00",
        "request_id": "SYNTH-L4:B3:R03:REQUEST:001",
        "updated_at": "2026-09-09T16:55:54.081965+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R03:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R03:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R03:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:55:53.818+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R03:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R03:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.085241+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R03:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R03:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R03:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R03:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R03:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.107011+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R03:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R03:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R03:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R03:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R03:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.123263+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R03:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R03:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R03:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:55:53.818+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:53.818+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:003:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R03:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R03:TRADE:003:LEDGER:2"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:007"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:009"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:001"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:008"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:004"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:010"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:002"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R03:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R03:ENTRY:006"
        },
        "ledger_event_id": 4
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:55:54.133416+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:55:53.818+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:55:53.818+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.091642+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:55:53.82+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.11005+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:55:53.822+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:55:54.12574+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R03:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:53.948762+08:00",
        "risk_event_id": "SYNTH-L4:B3:R03:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R03:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R03:TRACE:001",
        "created_at": "2026-09-09T16:55:53.631537+08:00",
        "risk_event_id": "SYNTH-L4:B3:R03:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R03:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 61988,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R03:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R03:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944153741"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R03:FILL:001",
        "ordId": "SYNTH-L4:B3:R03:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944153818"
      },
      {
        "tradeId": "SYNTH-L4:B3:R03:FILL:002",
        "ordId": "SYNTH-L4:B3:R03:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944153820"
      },
      {
        "tradeId": "SYNTH-L4:B3:R03:FILL:003",
        "ordId": "SYNTH-L4:B3:R03:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944153822"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843626496809100,
        "epochMillis": 1788944153734
      },
      {
        "sequence": 3,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843626503841700,
        "epochMillis": 1788944153741
      },
      {
        "sequence": 4,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843626503854500,
        "epochMillis": 1788944153741
      },
      {
        "sequence": 5,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843626504528400,
        "epochMillis": 1788944153741
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843626580927200,
        "epochMillis": 1788944153818,
        "tradeId": "SYNTH-L4:B3:R03:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843626582966200,
        "epochMillis": 1788944153820,
        "tradeId": "SYNTH-L4:B3:R03:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843626584453200,
        "epochMillis": 1788944153822,
        "tradeId": "SYNTH-L4:B3:R03:FILL:003",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 9,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843626706194200,
        "epochMillis": 1788944153943
      },
      {
        "sequence": 10,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843626822608100,
        "epochMillis": 1788944154060
      },
      {
        "sequence": 11,
        "type": "QUERY_ORDER",
        "nanoTime": 843626837050800,
        "epochMillis": 1788944154073,
        "state": "filled"
      },
      {
        "sequence": 12,
        "type": "QUERY_FILLS",
        "nanoTime": 843626838932600,
        "epochMillis": 1788944154075,
        "reportCount": 6
      },
      {
        "sequence": 13,
        "type": "QUERY_FILLS",
        "nanoTime": 843626956311000,
        "epochMillis": 1788944154193,
        "reportCount": 6
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>

<details>
<summary>RESTART_PRE_ACK-1 / PASS</summary>

```json
{
  "scenario": "RESTART_PRE_ACK",
  "repeat": 1,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 25188,
  "database": "SYNTH-L4:B3:R04:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:11909",
  "nqPid": 48084,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R04:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R04:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:03.474696+08:00",
        "request_id": "SYNTH-L4:B3:R04:REQUEST:001",
        "updated_at": "2026-09-09T16:56:03.520952+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R04:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R04:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:03.502805+08:00",
        "risk_event_id": "SYNTH-L4:B3:R04:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R04:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 25188,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R04:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R04:VENUE:001",
      "instId": "BTC-USDT",
      "state": "live",
      "px": "100",
      "sz": "10",
      "accFillSz": "0",
      "avgPx": "0",
      "uTime": "1788944163567"
    },
    "fills": [],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843636201690700,
        "epochMillis": 1788944163438
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843636322828500,
        "epochMillis": 1788944163559
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843636330233100,
        "epochMillis": 1788944163567
      }
    ]
  },
  "engageStartedEpochMillis": 1788944163597,
  "engageCompletedEpochMillis": 1788944163705,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R04:TRACE:002",
      "updated_at": "2026-09-09T16:56:03.599448+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:55:59.43305+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:03.599448+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R04:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R04:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:03.712227+08:00",
        "request_id": "SYNTH-L4:B3:R04:REQUEST:001",
        "updated_at": "2026-09-09T16:56:03.718239+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R04:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R04:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R04:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:03.474696+08:00",
        "request_id": "SYNTH-L4:B3:R04:REQUEST:001",
        "updated_at": "2026-09-09T16:56:03.520952+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R04:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R04:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:03.714957+08:00",
        "risk_event_id": "SYNTH-L4:B3:R04:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R04:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:03.502805+08:00",
        "risk_event_id": "SYNTH-L4:B3:R04:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R04:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "oldProcessDeadBeforeRestart": true,
  "restartPid": 45068,
  "killAfterRestart": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R04:TRACE:002",
      "updated_at": "2026-09-09T16:56:03.599448+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:55:59.43305+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:03.599448+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "recoveryStartedEpochMillis": 1788944167335,
  "recoveryCompletedEpochMillis": 1788944167546,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R04:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R04:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:03.712227+08:00",
        "request_id": "SYNTH-L4:B3:R04:REQUEST:001",
        "updated_at": "2026-09-09T16:56:03.718239+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R04:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R04:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R04:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:03.474696+08:00",
        "request_id": "SYNTH-L4:B3:R04:REQUEST:001",
        "updated_at": "2026-09-09T16:56:07.406932+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R04:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R04:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R04:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:03.822+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R04:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R04:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.413902+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R04:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R04:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R04:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R04:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R04:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.444978+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R04:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R04:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R04:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R04:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R04:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.461874+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R04:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R04:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R04:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:03.822+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:03.822+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:003:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:003:LEDGER:2"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:001"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:009"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:007"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:005"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:006"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:002"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:004"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:008"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:010"
        },
        "ledger_event_id": 8
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:07.472948+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:03.822+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:03.822+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R04:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:03.714957+08:00",
        "risk_event_id": "SYNTH-L4:B3:R04:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R04:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:03.502805+08:00",
        "risk_event_id": "SYNTH-L4:B3:R04:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R04:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R04:TRACE:002",
      "updated_at": "2026-09-09T16:56:03.599448+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:55:59.43305+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:03.599448+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R04:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R04:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:03.712227+08:00",
        "request_id": "SYNTH-L4:B3:R04:REQUEST:001",
        "updated_at": "2026-09-09T16:56:03.718239+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R04:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R04:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R04:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:03.474696+08:00",
        "request_id": "SYNTH-L4:B3:R04:REQUEST:001",
        "updated_at": "2026-09-09T16:56:07.406932+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R04:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R04:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R04:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:03.822+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R04:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R04:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.413902+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R04:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R04:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R04:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R04:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R04:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.444978+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R04:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R04:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R04:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R04:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R04:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.461874+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R04:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R04:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R04:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:03.822+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:03.822+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:003:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R04:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R04:TRADE:003:LEDGER:2"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:001"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:009"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:007"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:005"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:006"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:002"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:004"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:008"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R04:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R04:ENTRY:010"
        },
        "ledger_event_id": 8
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:07.472948+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:03.822+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:03.822+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.428771+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:03.824+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.448002+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:03.826+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:07.464879+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R04:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:03.714957+08:00",
        "risk_event_id": "SYNTH-L4:B3:R04:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R04:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R04:TRACE:001",
        "created_at": "2026-09-09T16:56:03.502805+08:00",
        "risk_event_id": "SYNTH-L4:B3:R04:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R04:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 25188,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R04:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R04:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944163567"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R04:FILL:001",
        "ordId": "SYNTH-L4:B3:R04:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944163822"
      },
      {
        "tradeId": "SYNTH-L4:B3:R04:FILL:002",
        "ordId": "SYNTH-L4:B3:R04:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944163824"
      },
      {
        "tradeId": "SYNTH-L4:B3:R04:FILL:003",
        "ordId": "SYNTH-L4:B3:R04:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944163826"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843636201690700,
        "epochMillis": 1788944163438
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843636322828500,
        "epochMillis": 1788944163559
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843636330233100,
        "epochMillis": 1788944163567
      },
      {
        "sequence": 5,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843636471246700,
        "epochMillis": 1788944163708
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843636585909600,
        "epochMillis": 1788944163822,
        "tradeId": "SYNTH-L4:B3:R04:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843636587510500,
        "epochMillis": 1788944163824,
        "tradeId": "SYNTH-L4:B3:R04:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843636589419000,
        "epochMillis": 1788944163826,
        "tradeId": "SYNTH-L4:B3:R04:FILL:003",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 9,
        "type": "RELEASE_PLACE",
        "nanoTime": 843636590656100,
        "epochMillis": 1788944163828
      },
      {
        "sequence": 10,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843636590982500,
        "epochMillis": 1788944163828
      },
      {
        "sequence": 11,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843640096937000,
        "epochMillis": 1788944167334
      },
      {
        "sequence": 12,
        "type": "QUERY_ORDER",
        "nanoTime": 843640136676300,
        "epochMillis": 1788944167373,
        "state": "filled"
      },
      {
        "sequence": 13,
        "type": "QUERY_FILLS",
        "nanoTime": 843640163877800,
        "epochMillis": 1788944167401,
        "reportCount": 6
      },
      {
        "sequence": 14,
        "type": "QUERY_FILLS",
        "nanoTime": 843640329118600,
        "epochMillis": 1788944167566,
        "reportCount": 6
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>

<details>
<summary>PRE_ACCEPT-2 / PASS</summary>

```json
{
  "scenario": "PRE_ACCEPT",
  "repeat": 2,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 52816,
  "database": "SYNTH-L4:B3:R05:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:11945",
  "nqPid": 40784,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R05:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R05:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.482473+08:00",
        "request_id": "SYNTH-L4:B3:R05:REQUEST:001",
        "updated_at": "2026-09-09T16:56:13.529927+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R05:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R05:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.513086+08:00",
        "risk_event_id": "SYNTH-L4:B3:R05:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R05:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 52816,
    "places": 0,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": null,
    "fills": [],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_ACCEPTANCE",
        "nanoTime": 843646208545600,
        "epochMillis": 1788944173445
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843646331669900,
        "epochMillis": 1788944173568
      }
    ]
  },
  "engageStartedEpochMillis": 1788944173604,
  "engageCompletedEpochMillis": 1788944173710,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R05:TRACE:002",
      "updated_at": "2026-09-09T16:56:13.605011+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:09.468066+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:13.605011+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R05:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R05:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.717184+08:00",
        "request_id": "SYNTH-L4:B3:R05:REQUEST:001",
        "updated_at": "2026-09-09T16:56:13.722415+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R05:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R05:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R05:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.482473+08:00",
        "request_id": "SYNTH-L4:B3:R05:REQUEST:001",
        "updated_at": "2026-09-09T16:56:13.529927+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R05:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R05:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.721169+08:00",
        "risk_event_id": "SYNTH-L4:B3:R05:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R05:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.513086+08:00",
        "risk_event_id": "SYNTH-L4:B3:R05:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R05:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "recoveryStartedEpochMillis": 1788944173946,
  "recoveryCompletedEpochMillis": 1788944174051,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R05:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R05:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.717184+08:00",
        "request_id": "SYNTH-L4:B3:R05:REQUEST:001",
        "updated_at": "2026-09-09T16:56:13.722415+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R05:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R05:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R05:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.482473+08:00",
        "request_id": "SYNTH-L4:B3:R05:REQUEST:001",
        "updated_at": "2026-09-09T16:56:13.968117+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R05:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R05:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R05:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:13.941+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R05:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R05:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.972867+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R05:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R05:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R05:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R05:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R05:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.99511+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R05:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R05:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R05:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R05:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R05:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.012343+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R05:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R05:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R05:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:13.941+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:13.941+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:003:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:003:LEDGER:2"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:009"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:010"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:004"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:006"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:002"
        },
        "ledger_event_id": 1
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:14.021261+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:13.941+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:13.941+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R05:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.721169+08:00",
        "risk_event_id": "SYNTH-L4:B3:R05:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R05:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.513086+08:00",
        "risk_event_id": "SYNTH-L4:B3:R05:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R05:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R05:TRACE:002",
      "updated_at": "2026-09-09T16:56:13.605011+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:09.468066+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:13.605011+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R05:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R05:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.717184+08:00",
        "request_id": "SYNTH-L4:B3:R05:REQUEST:001",
        "updated_at": "2026-09-09T16:56:13.722415+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R05:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R05:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R05:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.482473+08:00",
        "request_id": "SYNTH-L4:B3:R05:REQUEST:001",
        "updated_at": "2026-09-09T16:56:13.968117+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R05:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R05:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R05:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:13.941+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R05:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R05:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.972867+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R05:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R05:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R05:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R05:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R05:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.99511+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R05:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R05:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R05:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R05:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R05:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.012343+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R05:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R05:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R05:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:13.941+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:13.941+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:003:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R05:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R05:TRADE:003:LEDGER:2"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:009"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:010"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:004"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:006"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R05:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R05:ENTRY:002"
        },
        "ledger_event_id": 1
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:14.021261+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:13.941+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:13.941+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.980003+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:13.943+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:13.998409+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:13.944+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:14.01533+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R05:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.721169+08:00",
        "risk_event_id": "SYNTH-L4:B3:R05:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R05:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R05:TRACE:001",
        "created_at": "2026-09-09T16:56:13.513086+08:00",
        "risk_event_id": "SYNTH-L4:B3:R05:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R05:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 52816,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R05:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R05:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944173834"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R05:FILL:001",
        "ordId": "SYNTH-L4:B3:R05:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944173941"
      },
      {
        "tradeId": "SYNTH-L4:B3:R05:FILL:002",
        "ordId": "SYNTH-L4:B3:R05:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944173943"
      },
      {
        "tradeId": "SYNTH-L4:B3:R05:FILL:003",
        "ordId": "SYNTH-L4:B3:R05:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944173944"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_ACCEPTANCE",
        "nanoTime": 843646208545600,
        "epochMillis": 1788944173445
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843646331669900,
        "epochMillis": 1788944173568
      },
      {
        "sequence": 4,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843646476694600,
        "epochMillis": 1788944173714
      },
      {
        "sequence": 5,
        "type": "RELEASE_ACCEPTANCE",
        "nanoTime": 843646589113300,
        "epochMillis": 1788944173826
      },
      {
        "sequence": 6,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843646596408100,
        "epochMillis": 1788944173834
      },
      {
        "sequence": 7,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843646596420000,
        "epochMillis": 1788944173834
      },
      {
        "sequence": 8,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843646596710400,
        "epochMillis": 1788944173834
      },
      {
        "sequence": 9,
        "type": "FILL",
        "nanoTime": 843646705077000,
        "epochMillis": 1788944173941,
        "tradeId": "SYNTH-L4:B3:R05:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 10,
        "type": "FILL",
        "nanoTime": 843646706599800,
        "epochMillis": 1788944173943,
        "tradeId": "SYNTH-L4:B3:R05:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 11,
        "type": "FILL",
        "nanoTime": 843646707885800,
        "epochMillis": 1788944173944,
        "tradeId": "SYNTH-L4:B3:R05:FILL:003",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 12,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843646708919700,
        "epochMillis": 1788944173945
      },
      {
        "sequence": 13,
        "type": "QUERY_ORDER",
        "nanoTime": 843646723267800,
        "epochMillis": 1788944173961,
        "state": "filled"
      },
      {
        "sequence": 14,
        "type": "QUERY_FILLS",
        "nanoTime": 843646725310800,
        "epochMillis": 1788944173963,
        "reportCount": 6
      },
      {
        "sequence": 15,
        "type": "QUERY_FILLS",
        "nanoTime": 843646833167700,
        "epochMillis": 1788944174070,
        "reportCount": 6
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>

<details>
<summary>POST_ACCEPT_PRE_ACK-2 / PASS</summary>

```json
{
  "scenario": "POST_ACCEPT_PRE_ACK",
  "repeat": 2,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 31992,
  "database": "SYNTH-L4:B3:R06:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:2203",
  "nqPid": 70792,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R06:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R06:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.510003+08:00",
        "request_id": "SYNTH-L4:B3:R06:REQUEST:001",
        "updated_at": "2026-09-09T16:56:23.558085+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R06:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R06:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.538316+08:00",
        "risk_event_id": "SYNTH-L4:B3:R06:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R06:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 31992,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R06:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R06:VENUE:001",
      "instId": "BTC-USDT",
      "state": "live",
      "px": "100",
      "sz": "10",
      "accFillSz": "0",
      "avgPx": "0",
      "uTime": "1788944183607"
    },
    "fills": [],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843656236732700,
        "epochMillis": 1788944183473
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843656362990200,
        "epochMillis": 1788944183600
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843656369417200,
        "epochMillis": 1788944183607
      }
    ]
  },
  "engageStartedEpochMillis": 1788944183629,
  "engageCompletedEpochMillis": 1788944183738,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R06:TRACE:002",
      "updated_at": "2026-09-09T16:56:23.631764+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:19.479608+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:23.631764+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R06:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R06:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.745316+08:00",
        "request_id": "SYNTH-L4:B3:R06:REQUEST:001",
        "updated_at": "2026-09-09T16:56:23.750856+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R06:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R06:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R06:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.510003+08:00",
        "request_id": "SYNTH-L4:B3:R06:REQUEST:001",
        "updated_at": "2026-09-09T16:56:23.558085+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R06:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R06:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.747469+08:00",
        "risk_event_id": "SYNTH-L4:B3:R06:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R06:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.538316+08:00",
        "risk_event_id": "SYNTH-L4:B3:R06:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R06:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "recoveryStartedEpochMillis": 1788944183862,
  "recoveryCompletedEpochMillis": 1788944183974,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R06:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R06:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.745316+08:00",
        "request_id": "SYNTH-L4:B3:R06:REQUEST:001",
        "updated_at": "2026-09-09T16:56:23.750856+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R06:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R06:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R06:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.510003+08:00",
        "request_id": "SYNTH-L4:B3:R06:REQUEST:001",
        "updated_at": "2026-09-09T16:56:23.886928+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R06:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R06:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R06:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:23.858+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R06:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R06:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.891087+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R06:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R06:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R06:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R06:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R06:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.916274+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R06:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R06:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R06:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R06:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R06:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.934549+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R06:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R06:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R06:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:23.858+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:23.858+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:006"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:004"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:010"
        },
        "ledger_event_id": 7
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:23.945394+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:23.858+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:23.858+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R06:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.747469+08:00",
        "risk_event_id": "SYNTH-L4:B3:R06:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R06:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.538316+08:00",
        "risk_event_id": "SYNTH-L4:B3:R06:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R06:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "staleAckSnapshotUnchanged": true,
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R06:TRACE:002",
      "updated_at": "2026-09-09T16:56:23.631764+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:19.479608+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:23.631764+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R06:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R06:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.745316+08:00",
        "request_id": "SYNTH-L4:B3:R06:REQUEST:001",
        "updated_at": "2026-09-09T16:56:23.750856+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R06:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R06:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R06:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.510003+08:00",
        "request_id": "SYNTH-L4:B3:R06:REQUEST:001",
        "updated_at": "2026-09-09T16:56:23.886928+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R06:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R06:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R06:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:23.858+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R06:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R06:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.891087+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R06:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R06:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R06:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R06:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R06:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.916274+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R06:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R06:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R06:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R06:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R06:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.934549+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R06:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R06:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R06:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:23.858+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:23.858+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R06:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R06:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:006"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:004"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R06:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R06:ENTRY:010"
        },
        "ledger_event_id": 7
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:23.945394+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:23.858+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:23.858+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.898584+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:23.859+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.919625+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:23.86+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:23.937789+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R06:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.747469+08:00",
        "risk_event_id": "SYNTH-L4:B3:R06:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R06:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R06:TRACE:001",
        "created_at": "2026-09-09T16:56:23.538316+08:00",
        "risk_event_id": "SYNTH-L4:B3:R06:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R06:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 31992,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R06:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R06:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944183607"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R06:FILL:001",
        "ordId": "SYNTH-L4:B3:R06:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944183858"
      },
      {
        "tradeId": "SYNTH-L4:B3:R06:FILL:002",
        "ordId": "SYNTH-L4:B3:R06:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944183859"
      },
      {
        "tradeId": "SYNTH-L4:B3:R06:FILL:003",
        "ordId": "SYNTH-L4:B3:R06:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944183860"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843656236732700,
        "epochMillis": 1788944183473
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843656362990200,
        "epochMillis": 1788944183600
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843656369417200,
        "epochMillis": 1788944183607
      },
      {
        "sequence": 5,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843656504672200,
        "epochMillis": 1788944183742
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843656621152300,
        "epochMillis": 1788944183858,
        "tradeId": "SYNTH-L4:B3:R06:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843656622332800,
        "epochMillis": 1788944183859,
        "tradeId": "SYNTH-L4:B3:R06:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843656623622100,
        "epochMillis": 1788944183860,
        "tradeId": "SYNTH-L4:B3:R06:FILL:003",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 9,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843656624777300,
        "epochMillis": 1788944183861
      },
      {
        "sequence": 10,
        "type": "QUERY_ORDER",
        "nanoTime": 843656637718400,
        "epochMillis": 1788944183874,
        "state": "filled"
      },
      {
        "sequence": 11,
        "type": "QUERY_FILLS",
        "nanoTime": 843656643873800,
        "epochMillis": 1788944183881,
        "reportCount": 6
      },
      {
        "sequence": 12,
        "type": "QUERY_FILLS",
        "nanoTime": 843656754995100,
        "epochMillis": 1788944183992,
        "reportCount": 6
      },
      {
        "sequence": 13,
        "type": "RELEASE_PLACE",
        "nanoTime": 843656875573800,
        "epochMillis": 1788944184112
      },
      {
        "sequence": 14,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843656875817500,
        "epochMillis": 1788944184112
      },
      {
        "sequence": 15,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843656876143800,
        "epochMillis": 1788944184113
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>

<details>
<summary>PENDING_RECONCILIATION-2 / PASS</summary>

```json
{
  "scenario": "PENDING_RECONCILIATION",
  "repeat": 2,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 15100,
  "database": "SYNTH-L4:B3:R07:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:14786",
  "nqPid": 26320,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ACKED_BY_ADAPTER",
        "status": "ACCEPTED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R07:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R07:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.470208+08:00",
        "request_id": "SYNTH-L4:B3:R07:REQUEST:001",
        "updated_at": "2026-09-09T16:56:33.609775+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R07:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R07:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R07:VENUE:001"
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R07:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.499356+08:00",
        "risk_event_id": "SYNTH-L4:B3:R07:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R07:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 15100,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R07:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R07:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944193604"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R07:FILL:001",
        "ordId": "SYNTH-L4:B3:R07:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944193685"
      },
      {
        "tradeId": "SYNTH-L4:B3:R07:FILL:002",
        "ordId": "SYNTH-L4:B3:R07:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944193686"
      },
      {
        "tradeId": "SYNTH-L4:B3:R07:FILL:003",
        "ordId": "SYNTH-L4:B3:R07:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944193687"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843666360486200,
        "epochMillis": 1788944193598
      },
      {
        "sequence": 3,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843666367219700,
        "epochMillis": 1788944193604
      },
      {
        "sequence": 4,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843666367232600,
        "epochMillis": 1788944193604
      },
      {
        "sequence": 5,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843666367845900,
        "epochMillis": 1788944193604
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843666447331700,
        "epochMillis": 1788944193685,
        "tradeId": "SYNTH-L4:B3:R07:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843666449092900,
        "epochMillis": 1788944193686,
        "tradeId": "SYNTH-L4:B3:R07:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843666450119900,
        "epochMillis": 1788944193687,
        "tradeId": "SYNTH-L4:B3:R07:FILL:003",
        "qty": "3",
        "fee": "0.02"
      }
    ]
  },
  "engageStartedEpochMillis": 1788944193699,
  "engageCompletedEpochMillis": 1788944193806,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R07:TRACE:002",
      "updated_at": "2026-09-09T16:56:33.70158+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:29.482518+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:33.70158+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R07:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R07:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.813552+08:00",
        "request_id": "SYNTH-L4:B3:R07:REQUEST:001",
        "updated_at": "2026-09-09T16:56:33.819067+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R07:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ACKED_BY_ADAPTER",
        "status": "ACCEPTED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R07:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R07:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.470208+08:00",
        "request_id": "SYNTH-L4:B3:R07:REQUEST:001",
        "updated_at": "2026-09-09T16:56:33.609775+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R07:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R07:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R07:VENUE:001"
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R07:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.816768+08:00",
        "risk_event_id": "SYNTH-L4:B3:R07:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R07:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.499356+08:00",
        "risk_event_id": "SYNTH-L4:B3:R07:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R07:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "recoveryStartedEpochMillis": 1788944193925,
  "recoveryCompletedEpochMillis": 1788944194041,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R07:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R07:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.813552+08:00",
        "request_id": "SYNTH-L4:B3:R07:REQUEST:001",
        "updated_at": "2026-09-09T16:56:33.819067+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R07:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R07:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R07:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.470208+08:00",
        "request_id": "SYNTH-L4:B3:R07:REQUEST:001",
        "updated_at": "2026-09-09T16:56:33.947963+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R07:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R07:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R07:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:33.685+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R07:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R07:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.952433+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R07:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R07:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R07:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R07:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R07:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.974921+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R07:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R07:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R07:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R07:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R07:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.991209+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R07:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R07:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R07:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:33.685+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:33.685+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:003"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:010"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:007"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:008"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:006"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:004"
        },
        "ledger_event_id": 5
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:34.000311+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:33.685+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:33.685+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R07:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.816768+08:00",
        "risk_event_id": "SYNTH-L4:B3:R07:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R07:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.499356+08:00",
        "risk_event_id": "SYNTH-L4:B3:R07:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R07:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R07:TRACE:002",
      "updated_at": "2026-09-09T16:56:33.70158+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:29.482518+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:33.70158+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R07:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R07:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.813552+08:00",
        "request_id": "SYNTH-L4:B3:R07:REQUEST:001",
        "updated_at": "2026-09-09T16:56:33.819067+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R07:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R07:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R07:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.470208+08:00",
        "request_id": "SYNTH-L4:B3:R07:REQUEST:001",
        "updated_at": "2026-09-09T16:56:33.947963+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R07:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R07:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R07:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:33.685+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R07:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R07:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.952433+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R07:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R07:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R07:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R07:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R07:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.974921+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R07:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R07:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R07:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R07:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R07:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.991209+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R07:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R07:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R07:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:33.685+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:33.685+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R07:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R07:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:003"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:010"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:007"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:008"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:006"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R07:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R07:ENTRY:004"
        },
        "ledger_event_id": 5
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:34.000311+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:33.685+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:33.685+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.958779+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:33.686+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.97752+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:33.687+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:33.994123+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R07:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.816768+08:00",
        "risk_event_id": "SYNTH-L4:B3:R07:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R07:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R07:TRACE:001",
        "created_at": "2026-09-09T16:56:33.499356+08:00",
        "risk_event_id": "SYNTH-L4:B3:R07:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R07:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 15100,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R07:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R07:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944193604"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R07:FILL:001",
        "ordId": "SYNTH-L4:B3:R07:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944193685"
      },
      {
        "tradeId": "SYNTH-L4:B3:R07:FILL:002",
        "ordId": "SYNTH-L4:B3:R07:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944193686"
      },
      {
        "tradeId": "SYNTH-L4:B3:R07:FILL:003",
        "ordId": "SYNTH-L4:B3:R07:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944193687"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843666360486200,
        "epochMillis": 1788944193598
      },
      {
        "sequence": 3,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843666367219700,
        "epochMillis": 1788944193604
      },
      {
        "sequence": 4,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843666367232600,
        "epochMillis": 1788944193604
      },
      {
        "sequence": 5,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843666367845900,
        "epochMillis": 1788944193604
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843666447331700,
        "epochMillis": 1788944193685,
        "tradeId": "SYNTH-L4:B3:R07:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843666449092900,
        "epochMillis": 1788944193686,
        "tradeId": "SYNTH-L4:B3:R07:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843666450119900,
        "epochMillis": 1788944193687,
        "tradeId": "SYNTH-L4:B3:R07:FILL:003",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 9,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843666572985400,
        "epochMillis": 1788944193810
      },
      {
        "sequence": 10,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843666688538600,
        "epochMillis": 1788944193925
      },
      {
        "sequence": 11,
        "type": "QUERY_ORDER",
        "nanoTime": 843666703788500,
        "epochMillis": 1788944193941,
        "state": "filled"
      },
      {
        "sequence": 12,
        "type": "QUERY_FILLS",
        "nanoTime": 843666705503500,
        "epochMillis": 1788944193942,
        "reportCount": 6
      },
      {
        "sequence": 13,
        "type": "QUERY_FILLS",
        "nanoTime": 843666822670000,
        "epochMillis": 1788944194059,
        "reportCount": 6
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>

<details>
<summary>RESTART_PRE_ACK-2 / PASS</summary>

```json
{
  "scenario": "RESTART_PRE_ACK",
  "repeat": 2,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 75296,
  "database": "SYNTH-L4:B3:R08:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:14820",
  "nqPid": 9572,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R08:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R08:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:43.588214+08:00",
        "request_id": "SYNTH-L4:B3:R08:REQUEST:001",
        "updated_at": "2026-09-09T16:56:43.633751+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R08:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R08:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:43.617316+08:00",
        "risk_event_id": "SYNTH-L4:B3:R08:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R08:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 75296,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R08:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R08:VENUE:001",
      "instId": "BTC-USDT",
      "state": "live",
      "px": "100",
      "sz": "10",
      "accFillSz": "0",
      "avgPx": "0",
      "uTime": "1788944203680"
    },
    "fills": [],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843676313962300,
        "epochMillis": 1788944203551
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843676435775500,
        "epochMillis": 1788944203672
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843676443219800,
        "epochMillis": 1788944203680
      }
    ]
  },
  "engageStartedEpochMillis": 1788944203709,
  "engageCompletedEpochMillis": 1788944203815,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R08:TRACE:002",
      "updated_at": "2026-09-09T16:56:43.710848+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:39.527661+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:43.710848+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R08:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R08:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:43.822764+08:00",
        "request_id": "SYNTH-L4:B3:R08:REQUEST:001",
        "updated_at": "2026-09-09T16:56:43.827194+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R08:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R08:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R08:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:43.588214+08:00",
        "request_id": "SYNTH-L4:B3:R08:REQUEST:001",
        "updated_at": "2026-09-09T16:56:43.633751+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R08:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R08:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:43.825504+08:00",
        "risk_event_id": "SYNTH-L4:B3:R08:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R08:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:43.617316+08:00",
        "risk_event_id": "SYNTH-L4:B3:R08:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R08:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "oldProcessDeadBeforeRestart": true,
  "restartPid": 45652,
  "killAfterRestart": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R08:TRACE:002",
      "updated_at": "2026-09-09T16:56:43.710848+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:39.527661+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:43.710848+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "recoveryStartedEpochMillis": 1788944207333,
  "recoveryCompletedEpochMillis": 1788944207550,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R08:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R08:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:43.822764+08:00",
        "request_id": "SYNTH-L4:B3:R08:REQUEST:001",
        "updated_at": "2026-09-09T16:56:43.827194+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R08:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R08:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R08:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:43.588214+08:00",
        "request_id": "SYNTH-L4:B3:R08:REQUEST:001",
        "updated_at": "2026-09-09T16:56:47.401659+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R08:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R08:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R08:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:43.934+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R08:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R08:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.406673+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R08:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R08:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R08:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R08:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R08:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.437806+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R08:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R08:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R08:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R08:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R08:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.454416+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R08:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R08:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R08:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:43.934+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:43.934+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:005"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:006"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:010"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:003"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:004"
        },
        "ledger_event_id": 5
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:47.466171+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:43.934+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:43.934+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R08:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:43.825504+08:00",
        "risk_event_id": "SYNTH-L4:B3:R08:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R08:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:43.617316+08:00",
        "risk_event_id": "SYNTH-L4:B3:R08:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R08:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R08:TRACE:002",
      "updated_at": "2026-09-09T16:56:43.710848+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:39.527661+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:43.710848+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R08:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R08:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:43.822764+08:00",
        "request_id": "SYNTH-L4:B3:R08:REQUEST:001",
        "updated_at": "2026-09-09T16:56:43.827194+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R08:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R08:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R08:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:43.588214+08:00",
        "request_id": "SYNTH-L4:B3:R08:REQUEST:001",
        "updated_at": "2026-09-09T16:56:47.401659+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R08:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R08:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R08:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:43.934+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R08:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R08:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.406673+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R08:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R08:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R08:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R08:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R08:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.437806+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R08:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R08:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R08:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R08:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R08:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.454416+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R08:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R08:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R08:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:43.934+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:43.934+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R08:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R08:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:005"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:006"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:010"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:003"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R08:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R08:ENTRY:004"
        },
        "ledger_event_id": 5
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:47.466171+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:43.934+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:43.934+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.422012+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:43.936+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.440911+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:43.938+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:47.45772+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R08:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:43.825504+08:00",
        "risk_event_id": "SYNTH-L4:B3:R08:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R08:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R08:TRACE:001",
        "created_at": "2026-09-09T16:56:43.617316+08:00",
        "risk_event_id": "SYNTH-L4:B3:R08:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R08:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 75296,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R08:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R08:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944203680"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R08:FILL:001",
        "ordId": "SYNTH-L4:B3:R08:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944203934"
      },
      {
        "tradeId": "SYNTH-L4:B3:R08:FILL:002",
        "ordId": "SYNTH-L4:B3:R08:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944203936"
      },
      {
        "tradeId": "SYNTH-L4:B3:R08:FILL:003",
        "ordId": "SYNTH-L4:B3:R08:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944203938"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843676313962300,
        "epochMillis": 1788944203551
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843676435775500,
        "epochMillis": 1788944203672
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843676443219800,
        "epochMillis": 1788944203680
      },
      {
        "sequence": 5,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843676581629100,
        "epochMillis": 1788944203818
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843676698022000,
        "epochMillis": 1788944203934,
        "tradeId": "SYNTH-L4:B3:R08:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843676699684900,
        "epochMillis": 1788944203936,
        "tradeId": "SYNTH-L4:B3:R08:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843676701084900,
        "epochMillis": 1788944203938,
        "tradeId": "SYNTH-L4:B3:R08:FILL:003",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 9,
        "type": "RELEASE_PLACE",
        "nanoTime": 843676702154200,
        "epochMillis": 1788944203939
      },
      {
        "sequence": 10,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843676702511200,
        "epochMillis": 1788944203939
      },
      {
        "sequence": 11,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843680096005000,
        "epochMillis": 1788944207333
      },
      {
        "sequence": 12,
        "type": "QUERY_ORDER",
        "nanoTime": 843680132583500,
        "epochMillis": 1788944207369,
        "state": "filled"
      },
      {
        "sequence": 13,
        "type": "QUERY_FILLS",
        "nanoTime": 843680158024800,
        "epochMillis": 1788944207395,
        "reportCount": 6
      },
      {
        "sequence": 14,
        "type": "QUERY_FILLS",
        "nanoTime": 843680330823900,
        "epochMillis": 1788944207567,
        "reportCount": 6
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>

<details>
<summary>PRE_ACCEPT-3 / PASS</summary>

```json
{
  "scenario": "PRE_ACCEPT",
  "repeat": 3,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 36340,
  "database": "SYNTH-L4:B3:R09:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:12693",
  "nqPid": 40032,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R09:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R09:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.397067+08:00",
        "request_id": "SYNTH-L4:B3:R09:REQUEST:001",
        "updated_at": "2026-09-09T16:56:53.444794+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R09:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R09:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.424779+08:00",
        "risk_event_id": "SYNTH-L4:B3:R09:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R09:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 36340,
    "places": 0,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": null,
    "fills": [],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_ACCEPTANCE",
        "nanoTime": 843686123995500,
        "epochMillis": 1788944213361
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843686245065800,
        "epochMillis": 1788944213482
      }
    ]
  },
  "engageStartedEpochMillis": 1788944213516,
  "engageCompletedEpochMillis": 1788944213625,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R09:TRACE:002",
      "updated_at": "2026-09-09T16:56:53.518145+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:49.493039+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:53.518145+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R09:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R09:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.63212+08:00",
        "request_id": "SYNTH-L4:B3:R09:REQUEST:001",
        "updated_at": "2026-09-09T16:56:53.637446+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R09:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R09:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R09:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.397067+08:00",
        "request_id": "SYNTH-L4:B3:R09:REQUEST:001",
        "updated_at": "2026-09-09T16:56:53.444794+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R09:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R09:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.633921+08:00",
        "risk_event_id": "SYNTH-L4:B3:R09:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R09:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.424779+08:00",
        "risk_event_id": "SYNTH-L4:B3:R09:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R09:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "recoveryStartedEpochMillis": 1788944213865,
  "recoveryCompletedEpochMillis": 1788944213967,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R09:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R09:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.63212+08:00",
        "request_id": "SYNTH-L4:B3:R09:REQUEST:001",
        "updated_at": "2026-09-09T16:56:53.637446+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R09:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R09:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R09:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.397067+08:00",
        "request_id": "SYNTH-L4:B3:R09:REQUEST:001",
        "updated_at": "2026-09-09T16:56:53.887104+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R09:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R09:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R09:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:53.859+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R09:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R09:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.890072+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R09:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R09:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R09:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R09:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R09:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.911757+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R09:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R09:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R09:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R09:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R09:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.928667+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R09:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R09:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R09:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:53.859+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:53.859+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:005"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:007"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:003"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:010"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:006"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:008"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:004"
        },
        "ledger_event_id": 5
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:53.938989+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:53.859+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:53.859+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R09:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.633921+08:00",
        "risk_event_id": "SYNTH-L4:B3:R09:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R09:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.424779+08:00",
        "risk_event_id": "SYNTH-L4:B3:R09:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R09:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R09:TRACE:002",
      "updated_at": "2026-09-09T16:56:53.518145+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:49.493039+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:56:53.518145+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R09:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R09:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.63212+08:00",
        "request_id": "SYNTH-L4:B3:R09:REQUEST:001",
        "updated_at": "2026-09-09T16:56:53.637446+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R09:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R09:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R09:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.397067+08:00",
        "request_id": "SYNTH-L4:B3:R09:REQUEST:001",
        "updated_at": "2026-09-09T16:56:53.887104+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R09:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R09:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R09:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:56:53.859+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R09:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R09:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.890072+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R09:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R09:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R09:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R09:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R09:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.911757+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R09:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R09:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R09:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R09:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R09:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.928667+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R09:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R09:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R09:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:56:53.859+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:53.859+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R09:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R09:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:005"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:007"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:003"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:009"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:010"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:006"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:008"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R09:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R09:ENTRY:004"
        },
        "ledger_event_id": 5
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:56:53.938989+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:56:53.859+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:56:53.859+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.896757+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:56:53.861+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.9147+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:56:53.863+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:56:53.93126+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R09:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.633921+08:00",
        "risk_event_id": "SYNTH-L4:B3:R09:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R09:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R09:TRACE:001",
        "created_at": "2026-09-09T16:56:53.424779+08:00",
        "risk_event_id": "SYNTH-L4:B3:R09:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R09:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 36340,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R09:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R09:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944213751"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R09:FILL:001",
        "ordId": "SYNTH-L4:B3:R09:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944213859"
      },
      {
        "tradeId": "SYNTH-L4:B3:R09:FILL:002",
        "ordId": "SYNTH-L4:B3:R09:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944213861"
      },
      {
        "tradeId": "SYNTH-L4:B3:R09:FILL:003",
        "ordId": "SYNTH-L4:B3:R09:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944213863"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_ACCEPTANCE",
        "nanoTime": 843686123995500,
        "epochMillis": 1788944213361
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843686245065800,
        "epochMillis": 1788944213482
      },
      {
        "sequence": 4,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843686391746100,
        "epochMillis": 1788944213629
      },
      {
        "sequence": 5,
        "type": "RELEASE_ACCEPTANCE",
        "nanoTime": 843686507438200,
        "epochMillis": 1788944213745
      },
      {
        "sequence": 6,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843686514624300,
        "epochMillis": 1788944213751
      },
      {
        "sequence": 7,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843686514636400,
        "epochMillis": 1788944213751
      },
      {
        "sequence": 8,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843686514983700,
        "epochMillis": 1788944213752
      },
      {
        "sequence": 9,
        "type": "FILL",
        "nanoTime": 843686622169800,
        "epochMillis": 1788944213859,
        "tradeId": "SYNTH-L4:B3:R09:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 10,
        "type": "FILL",
        "nanoTime": 843686623973700,
        "epochMillis": 1788944213861,
        "tradeId": "SYNTH-L4:B3:R09:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 11,
        "type": "FILL",
        "nanoTime": 843686625932300,
        "epochMillis": 1788944213863,
        "tradeId": "SYNTH-L4:B3:R09:FILL:003",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 12,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843686627119900,
        "epochMillis": 1788944213864
      },
      {
        "sequence": 13,
        "type": "QUERY_ORDER",
        "nanoTime": 843686641692400,
        "epochMillis": 1788944213879,
        "state": "filled"
      },
      {
        "sequence": 14,
        "type": "QUERY_FILLS",
        "nanoTime": 843686643652900,
        "epochMillis": 1788944213881,
        "reportCount": 6
      },
      {
        "sequence": 15,
        "type": "QUERY_FILLS",
        "nanoTime": 843686746530100,
        "epochMillis": 1788944213983,
        "reportCount": 6
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>

<details>
<summary>POST_ACCEPT_PRE_ACK-3 / PASS</summary>

```json
{
  "scenario": "POST_ACCEPT_PRE_ACK",
  "repeat": 3,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 4168,
  "database": "SYNTH-L4:B3:R10:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:4235",
  "nqPid": 52108,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R10:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R10:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.549632+08:00",
        "request_id": "SYNTH-L4:B3:R10:REQUEST:001",
        "updated_at": "2026-09-09T16:57:03.596228+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R10:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R10:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.578214+08:00",
        "risk_event_id": "SYNTH-L4:B3:R10:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R10:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 4168,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R10:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R10:VENUE:001",
      "instId": "BTC-USDT",
      "state": "live",
      "px": "100",
      "sz": "10",
      "accFillSz": "0",
      "avgPx": "0",
      "uTime": "1788944223646"
    },
    "fills": [],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843696278397800,
        "epochMillis": 1788944223516
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843696401745500,
        "epochMillis": 1788944223638
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843696408859100,
        "epochMillis": 1788944223646
      }
    ]
  },
  "engageStartedEpochMillis": 1788944223672,
  "engageCompletedEpochMillis": 1788944223781,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R10:TRACE:002",
      "updated_at": "2026-09-09T16:57:03.674228+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:59.512723+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:57:03.674228+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R10:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R10:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.788993+08:00",
        "request_id": "SYNTH-L4:B3:R10:REQUEST:001",
        "updated_at": "2026-09-09T16:57:03.79392+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R10:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R10:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R10:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.549632+08:00",
        "request_id": "SYNTH-L4:B3:R10:REQUEST:001",
        "updated_at": "2026-09-09T16:57:03.596228+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R10:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R10:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.791446+08:00",
        "risk_event_id": "SYNTH-L4:B3:R10:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R10:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.578214+08:00",
        "risk_event_id": "SYNTH-L4:B3:R10:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R10:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "recoveryStartedEpochMillis": 1788944223903,
  "recoveryCompletedEpochMillis": 1788944224014,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R10:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R10:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.788993+08:00",
        "request_id": "SYNTH-L4:B3:R10:REQUEST:001",
        "updated_at": "2026-09-09T16:57:03.79392+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R10:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R10:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R10:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.549632+08:00",
        "request_id": "SYNTH-L4:B3:R10:REQUEST:001",
        "updated_at": "2026-09-09T16:57:03.929202+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R10:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R10:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R10:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:57:03.899+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R10:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R10:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.932429+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R10:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R10:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R10:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R10:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R10:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.972609+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R10:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R10:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R10:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R10:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R10:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.954839+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R10:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R10:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R10:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:57:03.899+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:03.899+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:003"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:001"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:007"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:004"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:002"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:008"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:009"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:005"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:010"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:006"
        },
        "ledger_event_id": 8
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:57:03.983934+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:57:03.899+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:57:03.899+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "snapshot_id": 5
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "snapshot_id": 3
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R10:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.791446+08:00",
        "risk_event_id": "SYNTH-L4:B3:R10:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R10:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.578214+08:00",
        "risk_event_id": "SYNTH-L4:B3:R10:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R10:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "staleAckSnapshotUnchanged": true,
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R10:TRACE:002",
      "updated_at": "2026-09-09T16:57:03.674228+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:56:59.512723+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:57:03.674228+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R10:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R10:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.788993+08:00",
        "request_id": "SYNTH-L4:B3:R10:REQUEST:001",
        "updated_at": "2026-09-09T16:57:03.79392+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R10:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R10:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R10:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.549632+08:00",
        "request_id": "SYNTH-L4:B3:R10:REQUEST:001",
        "updated_at": "2026-09-09T16:57:03.929202+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R10:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R10:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R10:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:57:03.899+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R10:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R10:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.932429+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R10:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R10:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R10:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R10:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R10:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.972609+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R10:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R10:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R10:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R10:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R10:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.954839+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R10:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R10:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R10:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:57:03.899+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:03.899+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:003:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R10:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R10:TRADE:003:LEDGER:1"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:003"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:001"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:007"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:004"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:002"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:008"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:009"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:005"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:010"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R10:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R10:ENTRY:006"
        },
        "ledger_event_id": 8
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:57:03.983934+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:57:03.899+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:57:03.899+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.938991+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:57:03.902+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.976018+08:00",
        "snapshot_id": 5
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:57:03.9+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:03.957925+08:00",
        "snapshot_id": 3
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R10:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.791446+08:00",
        "risk_event_id": "SYNTH-L4:B3:R10:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R10:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R10:TRACE:001",
        "created_at": "2026-09-09T16:57:03.578214+08:00",
        "risk_event_id": "SYNTH-L4:B3:R10:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R10:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 4168,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R10:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R10:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944223646"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R10:FILL:001",
        "ordId": "SYNTH-L4:B3:R10:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944223899"
      },
      {
        "tradeId": "SYNTH-L4:B3:R10:FILL:003",
        "ordId": "SYNTH-L4:B3:R10:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944223900"
      },
      {
        "tradeId": "SYNTH-L4:B3:R10:FILL:002",
        "ordId": "SYNTH-L4:B3:R10:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944223902"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843696278397800,
        "epochMillis": 1788944223516
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843696401745500,
        "epochMillis": 1788944223638
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843696408859100,
        "epochMillis": 1788944223646
      },
      {
        "sequence": 5,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843696547761300,
        "epochMillis": 1788944223785
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843696662782800,
        "epochMillis": 1788944223899,
        "tradeId": "SYNTH-L4:B3:R10:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843696663897200,
        "epochMillis": 1788944223900,
        "tradeId": "SYNTH-L4:B3:R10:FILL:003",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843696665180700,
        "epochMillis": 1788944223902,
        "tradeId": "SYNTH-L4:B3:R10:FILL:002",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 9,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843696666269200,
        "epochMillis": 1788944223903
      },
      {
        "sequence": 10,
        "type": "QUERY_ORDER",
        "nanoTime": 843696680062900,
        "epochMillis": 1788944223916,
        "state": "filled"
      },
      {
        "sequence": 11,
        "type": "QUERY_FILLS",
        "nanoTime": 843696686001900,
        "epochMillis": 1788944223923,
        "reportCount": 6
      },
      {
        "sequence": 12,
        "type": "QUERY_FILLS",
        "nanoTime": 843696795121700,
        "epochMillis": 1788944224032,
        "reportCount": 6
      },
      {
        "sequence": 13,
        "type": "RELEASE_PLACE",
        "nanoTime": 843696915090300,
        "epochMillis": 1788944224152
      },
      {
        "sequence": 14,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843696915447300,
        "epochMillis": 1788944224152
      },
      {
        "sequence": 15,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843696915731900,
        "epochMillis": 1788944224152
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>

<details>
<summary>PENDING_RECONCILIATION-3 / PASS</summary>

```json
{
  "scenario": "PENDING_RECONCILIATION",
  "repeat": 3,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 68632,
  "database": "SYNTH-L4:B3:R11:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:4270",
  "nqPid": 56028,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ACKED_BY_ADAPTER",
        "status": "ACCEPTED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R11:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R11:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.417146+08:00",
        "request_id": "SYNTH-L4:B3:R11:REQUEST:001",
        "updated_at": "2026-09-09T16:57:13.56091+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R11:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R11:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R11:VENUE:001"
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R11:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.448336+08:00",
        "risk_event_id": "SYNTH-L4:B3:R11:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R11:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 68632,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R11:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R11:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944233554"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R11:FILL:001",
        "ordId": "SYNTH-L4:B3:R11:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944233630"
      },
      {
        "tradeId": "SYNTH-L4:B3:R11:FILL:002",
        "ordId": "SYNTH-L4:B3:R11:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944233631"
      },
      {
        "tradeId": "SYNTH-L4:B3:R11:FILL:003",
        "ordId": "SYNTH-L4:B3:R11:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944233633"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843706310524500,
        "epochMillis": 1788944233548
      },
      {
        "sequence": 3,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843706317218300,
        "epochMillis": 1788944233554
      },
      {
        "sequence": 4,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843706317229500,
        "epochMillis": 1788944233554
      },
      {
        "sequence": 5,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843706317837100,
        "epochMillis": 1788944233555
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843706393195600,
        "epochMillis": 1788944233630,
        "tradeId": "SYNTH-L4:B3:R11:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843706394813000,
        "epochMillis": 1788944233631,
        "tradeId": "SYNTH-L4:B3:R11:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843706396106100,
        "epochMillis": 1788944233633,
        "tradeId": "SYNTH-L4:B3:R11:FILL:003",
        "qty": "3",
        "fee": "0.02"
      }
    ]
  },
  "engageStartedEpochMillis": 1788944233645,
  "engageCompletedEpochMillis": 1788944233753,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R11:TRACE:002",
      "updated_at": "2026-09-09T16:57:13.646841+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:57:09.538464+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:57:13.646841+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R11:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R11:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.759432+08:00",
        "request_id": "SYNTH-L4:B3:R11:REQUEST:001",
        "updated_at": "2026-09-09T16:57:13.764437+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R11:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ACKED_BY_ADAPTER",
        "status": "ACCEPTED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R11:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R11:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.417146+08:00",
        "request_id": "SYNTH-L4:B3:R11:REQUEST:001",
        "updated_at": "2026-09-09T16:57:13.56091+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R11:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R11:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R11:VENUE:001"
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R11:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.762978+08:00",
        "risk_event_id": "SYNTH-L4:B3:R11:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R11:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.448336+08:00",
        "risk_event_id": "SYNTH-L4:B3:R11:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R11:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "recoveryStartedEpochMillis": 1788944233872,
  "recoveryCompletedEpochMillis": 1788944233986,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R11:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R11:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.759432+08:00",
        "request_id": "SYNTH-L4:B3:R11:REQUEST:001",
        "updated_at": "2026-09-09T16:57:13.764437+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R11:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R11:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R11:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.417146+08:00",
        "request_id": "SYNTH-L4:B3:R11:REQUEST:001",
        "updated_at": "2026-09-09T16:57:13.892044+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R11:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R11:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R11:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:57:13.63+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R11:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R11:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.897072+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R11:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R11:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R11:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R11:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R11:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.92022+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R11:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R11:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R11:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R11:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R11:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.938368+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R11:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R11:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R11:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:57:13.63+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:13.63+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:003:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:003:LEDGER:2"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:007"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:006"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:009"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:008"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:010"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:004"
        },
        "ledger_event_id": 6
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:57:13.947055+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:57:13.63+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:57:13.63+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R11:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.762978+08:00",
        "risk_event_id": "SYNTH-L4:B3:R11:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R11:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.448336+08:00",
        "risk_event_id": "SYNTH-L4:B3:R11:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R11:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R11:TRACE:002",
      "updated_at": "2026-09-09T16:57:13.646841+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:57:09.538464+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:57:13.646841+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R11:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R11:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.759432+08:00",
        "request_id": "SYNTH-L4:B3:R11:REQUEST:001",
        "updated_at": "2026-09-09T16:57:13.764437+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R11:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 4,
        "order_id": "SYNTH-L4:B3:R11:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R11:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.417146+08:00",
        "request_id": "SYNTH-L4:B3:R11:REQUEST:001",
        "updated_at": "2026-09-09T16:57:13.892044+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R11:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R11:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R11:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:57:13.63+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R11:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R11:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.897072+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R11:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R11:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R11:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R11:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R11:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.92022+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R11:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R11:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R11:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R11:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R11:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.938368+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R11:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R11:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R11:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:57:13.63+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:13.63+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:003:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R11:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R11:TRADE:003:LEDGER:2"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:007"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:003"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:006"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:009"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:008"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:010"
        },
        "ledger_event_id": 8
      },
      {
        "entry_id": "SYNTH-L4:B3:R11:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R11:ENTRY:004"
        },
        "ledger_event_id": 6
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:57:13.947055+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:57:13.63+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:57:13.63+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.904079+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:57:13.631+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.923327+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:57:13.633+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:13.941279+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R11:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.762978+08:00",
        "risk_event_id": "SYNTH-L4:B3:R11:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R11:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R11:TRACE:001",
        "created_at": "2026-09-09T16:57:13.448336+08:00",
        "risk_event_id": "SYNTH-L4:B3:R11:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R11:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 68632,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R11:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R11:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944233554"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R11:FILL:001",
        "ordId": "SYNTH-L4:B3:R11:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944233630"
      },
      {
        "tradeId": "SYNTH-L4:B3:R11:FILL:002",
        "ordId": "SYNTH-L4:B3:R11:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944233631"
      },
      {
        "tradeId": "SYNTH-L4:B3:R11:FILL:003",
        "ordId": "SYNTH-L4:B3:R11:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944233633"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843706310524500,
        "epochMillis": 1788944233548
      },
      {
        "sequence": 3,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843706317218300,
        "epochMillis": 1788944233554
      },
      {
        "sequence": 4,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843706317229500,
        "epochMillis": 1788944233554
      },
      {
        "sequence": 5,
        "type": "PLACE_RESPONSE_DELIVERED",
        "nanoTime": 843706317837100,
        "epochMillis": 1788944233555
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843706393195600,
        "epochMillis": 1788944233630,
        "tradeId": "SYNTH-L4:B3:R11:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843706394813000,
        "epochMillis": 1788944233631,
        "tradeId": "SYNTH-L4:B3:R11:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843706396106100,
        "epochMillis": 1788944233633,
        "tradeId": "SYNTH-L4:B3:R11:FILL:003",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 9,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843706518935300,
        "epochMillis": 1788944233756
      },
      {
        "sequence": 10,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843706633110200,
        "epochMillis": 1788944233870
      },
      {
        "sequence": 11,
        "type": "QUERY_ORDER",
        "nanoTime": 843706647411000,
        "epochMillis": 1788944233884,
        "state": "filled"
      },
      {
        "sequence": 12,
        "type": "QUERY_FILLS",
        "nanoTime": 843706649227100,
        "epochMillis": 1788944233886,
        "reportCount": 6
      },
      {
        "sequence": 13,
        "type": "QUERY_FILLS",
        "nanoTime": 843706767101400,
        "epochMillis": 1788944234004,
        "reportCount": 6
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>

<details>
<summary>RESTART_PRE_ACK-3 / PASS</summary>

```json
{
  "scenario": "RESTART_PRE_ACK",
  "repeat": 3,
  "controllerPid": 20140,
  "orderEnvironment": "SIM",
  "venuePid": 49140,
  "database": "SYNTH-L4:B3:R12:DATABASE:001",
  "venueEndpoint": "http://127.0.0.1:8620",
  "nqPid": 55168,
  "postgres": "16.15 (Debian 16.15-1.pgdg13+2)",
  "schema": "V48",
  "beforeKill": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R12:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R12:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:23.428191+08:00",
        "request_id": "SYNTH-L4:B3:R12:REQUEST:001",
        "updated_at": "2026-09-09T16:57:23.474819+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R12:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R12:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:23.45498+08:00",
        "risk_event_id": "SYNTH-L4:B3:R12:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R12:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "venueBeforeKill": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 49140,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R12:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R12:VENUE:001",
      "instId": "BTC-USDT",
      "state": "live",
      "px": "100",
      "sz": "10",
      "accFillSz": "0",
      "avgPx": "0",
      "uTime": "1788944243518"
    },
    "fills": [],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843716155464500,
        "epochMillis": 1788944243392
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843716275129100,
        "epochMillis": 1788944243512
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843716281334900,
        "epochMillis": 1788944243518
      }
    ]
  },
  "engageStartedEpochMillis": 1788944243551,
  "engageCompletedEpochMillis": 1788944243660,
  "killAfterEngage": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R12:TRACE:002",
      "updated_at": "2026-09-09T16:57:23.552761+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:57:19.516234+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:57:23.552761+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "afterNewCommand": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R12:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R12:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:23.667141+08:00",
        "request_id": "SYNTH-L4:B3:R12:REQUEST:001",
        "updated_at": "2026-09-09T16:57:23.672156+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R12:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "ORDER_ROUTED_TO_ADAPTER",
        "status": "SENT",
        "symbol": "BTC-USDT",
        "version": 2,
        "order_id": "SYNTH-L4:B3:R12:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R12:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:23.428191+08:00",
        "request_id": "SYNTH-L4:B3:R12:REQUEST:001",
        "updated_at": "2026-09-09T16:57:23.474819+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R12:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      }
    ],
    "trades": [],
    "ledger_entries": [],
    "ledger_events": [],
    "positions": [],
    "account_snapshots": [],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R12:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:23.668688+08:00",
        "risk_event_id": "SYNTH-L4:B3:R12:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R12:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:23.45498+08:00",
        "risk_event_id": "SYNTH-L4:B3:R12:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R12:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "postKillNewCommand": "RISK_REJECTED / KILL_SWITCH_TRIGGERED / venue calls=0",
  "oldProcessDeadBeforeRestart": true,
  "restartPid": 34052,
  "killAfterRestart": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R12:TRACE:002",
      "updated_at": "2026-09-09T16:57:23.552761+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:57:19.516234+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:57:23.552761+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "recoveryStartedEpochMillis": 1788944247287,
  "recoveryCompletedEpochMillis": 1788944247503,
  "afterRecoveryReplay": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R12:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R12:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:23.667141+08:00",
        "request_id": "SYNTH-L4:B3:R12:REQUEST:001",
        "updated_at": "2026-09-09T16:57:23.672156+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R12:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R12:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R12:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:23.428191+08:00",
        "request_id": "SYNTH-L4:B3:R12:REQUEST:001",
        "updated_at": "2026-09-09T16:57:27.360123+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R12:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R12:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R12:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:57:23.779+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R12:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R12:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.364673+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R12:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R12:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R12:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R12:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R12:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.397329+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R12:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R12:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R12:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R12:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R12:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.415441+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R12:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R12:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R12:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:57:23.779+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:23.779+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:003:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:003:LEDGER:2"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:003"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:009"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:006"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:004"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:010"
        },
        "ledger_event_id": 8
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:57:27.426933+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:57:23.779+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:57:23.779+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R12:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:23.668688+08:00",
        "risk_event_id": "SYNTH-L4:B3:R12:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R12:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:23.45498+08:00",
        "risk_event_id": "SYNTH-L4:B3:R12:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R12:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalKill": {
    "state": {
      "scope": "GLOBAL_TRADING",
      "source": "OPERATOR_ENGAGE",
      "status": "ENGAGED",
      "version": 3,
      "trace_id": "SYNTH-L4:B3:R12:TRACE:002",
      "updated_at": "2026-09-09T16:57:23.552761+08:00",
      "updated_by": "B3_TEST",
      "reason_code": "B3_IN_FLIGHT"
    },
    "events": [
      {
        "scope": "GLOBAL_TRADING",
        "source": "FLYWAY_MIGRATION",
        "actor_id": "SYSTEM",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:003",
        "to_status": "ENGAGED",
        "from_status": null,
        "occurred_at": "2026-09-09T16:57:19.516234+08:00",
        "reason_code": "DEFAULT_SAFE_BOOTSTRAP",
        "state_version": 1
      },
      {
        "scope": "GLOBAL_TRADING",
        "source": "OPERATOR_ENGAGE",
        "actor_id": "B3_TEST",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:002",
        "to_status": "ENGAGED",
        "from_status": "DISENGAGED",
        "occurred_at": "2026-09-09T16:57:23.552761+08:00",
        "reason_code": "B3_IN_FLIGHT",
        "state_version": 3
      }
    ]
  },
  "finalDb": {
    "orders": [
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "KILL_SWITCH_TRIGGERED",
        "status": "RISK_REJECTED",
        "symbol": "BTC-USDT",
        "version": 1,
        "order_id": "SYNTH-L4:B3:R12:ORDER:002",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R12:CLIENT:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:23.667141+08:00",
        "request_id": "SYNTH-L4:B3:R12:REQUEST:001",
        "updated_at": "2026-09-09T16:57:23.672156+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R12:CLIENT:002",
        "strategy_run_id": null,
        "exchange_order_id": null,
        "external_order_id": null
      },
      {
        "qty": 10.0,
        "side": "BUY",
        "type": "LIMIT",
        "price": 100.0,
        "venue": "OKX",
        "reason": "RECONCILE_STATUS_ALIGN",
        "status": "FILLED",
        "symbol": "BTC-USDT",
        "version": 3,
        "order_id": "SYNTH-L4:B3:R12:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "dedup_key": "1:SYNTH-L4:B3:R12:CLIENT:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:23.428191+08:00",
        "request_id": "SYNTH-L4:B3:R12:REQUEST:001",
        "updated_at": "2026-09-09T16:57:27.360123+08:00",
        "exchange_code": "OKX",
        "client_order_id": "SYNTH-L4:B3:R12:CLIENT:001",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R12:VENUE:001",
        "external_order_id": "SYNTH-L4:B3:R12:VENUE:001"
      }
    ],
    "trades": [
      {
        "ts": "2026-09-09T16:57:23.779+08:00",
        "fee": 0.0,
        "qty": 4.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R12:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R12:TRADE:001",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.364673+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R12:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R12:FILL:001",
        "external_order_id": "SYNTH-L4:B3:R12:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "fee": 0.01,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R12:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R12:TRADE:002",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.397329+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R12:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R12:FILL:002",
        "external_order_id": "SYNTH-L4:B3:R12:VENUE:001"
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "fee": 0.02,
        "qty": 3.0,
        "price": 100.0,
        "symbol": "BTC-USDT",
        "exchange": "OKX",
        "order_id": "SYNTH-L4:B3:R12:ORDER:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "trade_id": "SYNTH-L4:B3:R12:TRADE:003",
        "trade_env": "SIM",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.415441+08:00",
        "fee_currency": "USDT",
        "exchange_code": "OKX",
        "strategy_run_id": null,
        "exchange_order_id": "SYNTH-L4:B3:R12:VENUE:001",
        "exchange_trade_id": "SYNTH-L4:B3:R12:FILL:003",
        "external_order_id": "SYNTH-L4:B3:R12:VENUE:001"
      }
    ],
    "ledger_entries": [
      {
        "ts": "2026-09-09T16:57:23.779+08:00",
        "delta": 400.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:001",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:001:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:23.779+08:00",
        "delta": -400.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:001",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:002",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "balance_after": -400.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:001:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "delta": 0.01,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:003",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:002:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "delta": -0.01,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:004",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "balance_after": -0.01,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:002:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:005",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:002:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:002",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:006",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:002:LEDGER:2"
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "delta": -0.02,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:007",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "balance_after": -0.02,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:003:LEDGER:FEE_1"
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "delta": 0.02,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:008",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:003:LEDGER:FEE_2"
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "delta": -300.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:009",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "DEBIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "balance_after": -300.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:003:LEDGER:1"
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "delta": 300.0,
        "ref_id": "SYNTH-L4:B3:R12:TRADE:003",
        "currency": "USDT",
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:010",
        "ref_type": "TRADE",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "direction": "CREDIT",
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "balance_after": 0.0,
        "idempotency_key": "SYNTH-L4:B3:R12:TRADE:003:LEDGER:2"
      }
    ],
    "ledger_events": [
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:001",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 400.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:001"
        },
        "ledger_event_id": 2
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:007",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.02,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:007"
        },
        "ledger_event_id": 9
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:003",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.01,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:003"
        },
        "ledger_event_id": 6
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:005",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:005"
        },
        "ledger_event_id": 3
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:002",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -400.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:002"
        },
        "ledger_event_id": 1
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:009",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -300.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:009"
        },
        "ledger_event_id": 7
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:006",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:006"
        },
        "ledger_event_id": 4
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:004",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": -0.01,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:004"
        },
        "ledger_event_id": 5
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:008",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 0.02,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:008"
        },
        "ledger_event_id": 10
      },
      {
        "entry_id": "SYNTH-L4:B3:R12:ENTRY:010",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "event_type": "POSTED",
        "payload_json": {
          "delta": 300.0,
          "entry_id": "SYNTH-L4:B3:R12:ENTRY:010"
        },
        "ledger_event_id": 8
      }
    ],
    "positions": [
      {
        "id": 1,
        "qty": 10.0,
        "symbol": "BTC-USDT",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "avg_price": 100.0,
        "account_id": 1,
        "frozen_qty": 0.0,
        "updated_at": "2026-09-09T16:57:27.426933+08:00",
        "available_qty": 10.0
      }
    ],
    "account_snapshots": [
      {
        "ts": "2026-09-09T16:57:23.779+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "snapshot_id": 2
      },
      {
        "ts": "2026-09-09T16:57:23.779+08:00",
        "frozen": 0.0,
        "balance": 4.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 4.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.381114+08:00",
        "snapshot_id": 1
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "snapshot_id": 4
      },
      {
        "ts": "2026-09-09T16:57:23.78+08:00",
        "frozen": 0.0,
        "balance": 7.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 7.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.400303+08:00",
        "snapshot_id": 3
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "frozen": 0.0,
        "balance": 0.0,
        "currency": "USDT",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 0.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "snapshot_id": 6
      },
      {
        "ts": "2026-09-09T16:57:23.782+08:00",
        "frozen": 0.0,
        "balance": 10.0,
        "currency": "BTC",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "available": 10.0,
        "account_id": 1,
        "created_at": "2026-09-09T16:57:27.418542+08:00",
        "snapshot_id": 5
      }
    ],
    "risk_events": [
      {
        "scope": "ORDER",
        "reason": "KILL_SWITCH_TRIGGERED",
        "rule_id": "KILL_SWITCH_TRIGGERED",
        "decision": "REJECT",
        "scope_id": "SYNTH-L4:B3:R12:ORDER:002",
        "severity": "HIGH",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:23.668688+08:00",
        "risk_event_id": "SYNTH-L4:B3:R12:RISK:002"
      },
      {
        "scope": "ORDER",
        "reason": "RISK_RULES_PASSED",
        "rule_id": "RISK_RULES_PASSED",
        "decision": "ALLOW",
        "scope_id": "SYNTH-L4:B3:R12:ORDER:001",
        "severity": "LOW",
        "trace_id": "SYNTH-L4:B3:R12:TRACE:001",
        "created_at": "2026-09-09T16:57:23.45498+08:00",
        "risk_event_id": "SYNTH-L4:B3:R12:RISK:001"
      }
    ],
    "execution_intents": [],
    "execution_receipts": [],
    "database": "SYNTH-L4:B3:R12:DATABASE:001",
    "correction_audit": [],
    "correction_events": []
  },
  "finalVenue": {
    "code": "0",
    "msg": "",
    "data": [],
    "pid": 49140,
    "places": 1,
    "cancels": 0,
    "pendingCancel": false,
    "placeRequests": 1,
    "order": {
      "clOrdId": "SYNTH-L4:B3:R12:CLIENT:001",
      "ordId": "SYNTH-L4:B3:R12:VENUE:001",
      "instId": "BTC-USDT",
      "state": "filled",
      "px": "100",
      "sz": "10",
      "accFillSz": "10",
      "avgPx": "100",
      "uTime": "1788944243518"
    },
    "fills": [
      {
        "tradeId": "SYNTH-L4:B3:R12:FILL:001",
        "ordId": "SYNTH-L4:B3:R12:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "4",
        "fee": "0",
        "feeCcy": "USDT",
        "ts": "1788944243779"
      },
      {
        "tradeId": "SYNTH-L4:B3:R12:FILL:002",
        "ordId": "SYNTH-L4:B3:R12:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.01",
        "feeCcy": "USDT",
        "ts": "1788944243780"
      },
      {
        "tradeId": "SYNTH-L4:B3:R12:FILL:003",
        "ordId": "SYNTH-L4:B3:R12:VENUE:001",
        "instId": "BTC-USDT",
        "side": "buy",
        "fillPx": "100",
        "fillSz": "3",
        "fee": "-0.02",
        "feeCcy": "USDT",
        "ts": "1788944243782"
      }
    ],
    "events": [
      {
        "sequence": 2,
        "type": "HOLD_PLACE",
        "nanoTime": 843716155464500,
        "epochMillis": 1788944243392
      },
      {
        "sequence": 3,
        "type": "PLACE_REQUEST_RECEIVED",
        "nanoTime": 843716275129100,
        "epochMillis": 1788944243512
      },
      {
        "sequence": 4,
        "type": "PLACE_ACCEPTED",
        "nanoTime": 843716281334900,
        "epochMillis": 1788944243518
      },
      {
        "sequence": 5,
        "type": "KILL_DURABLE_OBSERVED",
        "nanoTime": 843716426203800,
        "epochMillis": 1788944243663
      },
      {
        "sequence": 6,
        "type": "FILL",
        "nanoTime": 843716541952500,
        "epochMillis": 1788944243779,
        "tradeId": "SYNTH-L4:B3:R12:FILL:001",
        "qty": "4",
        "fee": "0"
      },
      {
        "sequence": 7,
        "type": "FILL",
        "nanoTime": 843716543453600,
        "epochMillis": 1788944243780,
        "tradeId": "SYNTH-L4:B3:R12:FILL:002",
        "qty": "3",
        "fee": "0.01"
      },
      {
        "sequence": 8,
        "type": "FILL",
        "nanoTime": 843716544934400,
        "epochMillis": 1788944243782,
        "tradeId": "SYNTH-L4:B3:R12:FILL:003",
        "qty": "3",
        "fee": "0.02"
      },
      {
        "sequence": 9,
        "type": "RELEASE_PLACE",
        "nanoTime": 843716546040700,
        "epochMillis": 1788944243783
      },
      {
        "sequence": 10,
        "type": "PLACE_ACK_GENERATED",
        "nanoTime": 843716546240400,
        "epochMillis": 1788944243784
      },
      {
        "sequence": 11,
        "type": "DUPLICATE_REPORTS_ENABLED",
        "nanoTime": 843720048844200,
        "epochMillis": 1788944247286
      },
      {
        "sequence": 12,
        "type": "QUERY_ORDER",
        "nanoTime": 843720089577900,
        "epochMillis": 1788944247326,
        "state": "filled"
      },
      {
        "sequence": 13,
        "type": "QUERY_FILLS",
        "nanoTime": 843720116796000,
        "epochMillis": 1788944247353,
        "reportCount": 6
      },
      {
        "sequence": 14,
        "type": "QUERY_FILLS",
        "nanoTime": 843720284672900,
        "epochMillis": 1788944247521,
        "reportCount": 6
      }
    ]
  },
  "blindRetries": 0,
  "result": "PASS"
}
```

</details>
