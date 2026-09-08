# Phase6 L4 B0 real-process harness

当前结论（用户澄清后，2026-09-08）：`IMPLEMENTED / SELF_REVIEWED / B0_REAL_PROCESS_HARNESS_READY / P0_0 / P1_0 / READY_TO_COMMIT`。最终验证与精确文件清单见本文末尾“授权澄清后的实现与验收”。未 commit/push，未将本地 B0 验证写成 exact-head CI 或 B1–B5 qualification。

下面保留首次检查的历史记录。其 `BLOCKED`、`NOT_RUN`、仅 evidence 改动等描述只适用于用户澄清前，已被末尾实际实现和验证结果推进；不代表当前状态。初次 `PRODUCTION_TESTABILITY_GAP` 命名过宽，实际问题为 `KILL_INITIAL_STATE_AUTHORIZATION_AMBIGUOUS`，无需修改 production。

## 首次检查历史（授权澄清前）

日期：2026-09-08。任务分类：`L4_PROOF_INFRASTRUCTURE / REAL_PROCESS_HARNESS / TEST_INFRASTRUCTURE / NQ-only`。

Final decision：`BLOCKED / PRODUCTION_TESTABILITY_GAP`。更精确的原因是：本轮要求全新数据库、canonical 成功下单链路，同时要求所有环境 `kill disengage = 0`；现有 canonical 风控在该条件下必须拒单。这不是 production correctness defect，不应通过新增豁免或替换风控来修复。B0 未实现、未验收，不能进入 B1。

## Baseline

- Starting HEAD：`c0823d8a9db6543db25e114535acbaa4f2dc6298`。
- Branch：`audit/post-gatey-agent-baseline`。
- 本地 HEAD、origin tracking ref 和 `git ls-remote origin refs/heads/audit/post-gatey-agent-baseline` 三者一致。
- [Exact-head CI 34218184352](https://github.com/ling5477/nexus-quant/actions/runs/34218184352)：通过 `gh run view` 当前只读查询确认 `completed / success / 9 of 9`，9 个 jobs 均 success，headSha 与 Starting HEAD 一致。这是基线 CI，不是本轮 B0 验证。
- 起始 `git status --short` 为空，`git diff --check` exit=0。
- C1/C2 CLOSED、F1/F2 CLOSED、P0/P1=0 为用户给定的基线接受事实；本轮不重新执行独立审查，不以 CI 代替独立审查。
- 当前 [STATUS](../../current/STATUS.md) machine block 仍为 pre-B0 `IMPLEMENTED|PENDING_REVIEW`、next action=`NQ-GATEAUDIT-PHASE6-PRE-B0-SAFETY-INDEPENDENT-REVIEW`；[ROADMAP](../../current/ROADMAP.md) 同样尚未同步。该差异单独记录，未修改 current authority，也不据此扩大或否定本轮显式测试基础设施授权。
- V48 是当前仓库 migration inventory；本轮未启动数据库，未声明已执行 Flyway。LIVE=DISABLED、kill=ENGAGED 是当前文档状态；未访问生产环境验证。
- F3/F4/F5/F6 保持 `OPEN / P2 / NON_BLOCKING_FOR_B0`，未整改。

## Existing harness inventory

以下 REUSE/EXTEND 是资产审计结论和后续复用位置，不表示已经完成代码复用或扩展。

| 分类 | 已有资产 | 能力与 B0 差距 |
| --- | --- | --- |
| REUSE | [TradingRestartRecoveryPostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TradingRestartRecoveryPostgresIntegrationTest.java) 的 RestartDatabase | 独立随机命名 database、Flyway migrate/validate 至 48、JDBC 最终事实查询、关闭时 terminate/drop。可复用生命周期；仍需明确 disposable PostgreSQL 实例身份、loopback 限制及清理读回。不能直接信任任意继承 datasource。 |
| EXTEND | 同文件 runChild/assertProcessBoundary | 已用 ProcessBuilder 启动真正 JVM，校验进程 PID、A 退出先于 B 启动；正常证明路径是退出后重启，强杀仅用于超时清理。B0 需要可控的业务事实就绪屏障、实际 kill、启动第二实例、进程退出与清理断言。 |
| EXTEND | [TradingRestartRecoveryProcessMain](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TradingRestartRecoveryProcessMain.java) | 已有 NexusQuantApplication + SpringApplicationBuilder、真实 repository/JDBC、reconcile 服务调用。RestartVenue/RestartOkxHttpClient 为子进程内合成实现；不具备独立 venue PID。placeOrder 测试准备直接将 kill_switch_states 改为 DISENGAGED，与本轮边界冲突，不能原样启动。 |
| REUSE | 同文件 R1/R2 事实断言 | 可复用 Trade/Ledger、positions/account snapshots、recovery audit 与重复对账不重复记账的不变量。R1 的 fail-once Ledger decorator 是历史证明机制，不能当作本轮要求的网络/进程故障。 |
| REUSE | [TradingChainPostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TradingChainPostgresIntegrationTest.java) | Spring/PostgreSQL composition 与 deterministic venue/fills、交易到账务断言；fake venue 在同 JVM，且 fixture 同样解除 kill。只能复用场景协议知识与断言，不能标为本轮 real-process smoke。 |
| REUSE | [DisposableFakeVenueLauncher](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/livecontrol/executionworker/DisposableFakeVenueLauncher.java) | 已有独立 main、127.0.0.1 HTTP server、venue-owned properties store、place/cancel/query/metrics、TIMEOUT_AFTER_STORE 的保存后延迟。该资产在 production source，B0 不修改。其 fake-worker 协议与 ordinary Order 的 OKX fills 协议不同，不能直接替代 canonical Trade/Ledger 闭环。 |
| REUSE | [LoopbackFakeExchangeHttpClientTest](../../../backend/nq-infra/src/test/java/com/guidinglight/nexusquant/livecontrol/execution/infra/fake/LoopbackFakeExchangeHttpClientTest.java) | 有 loopback URI 与序列化测试，但 server 与 checker 同 JVM，仅 query 协议，不构成独立 venue/restart 证明。 |
| REUSE | [ExchangeNoOutboundGuard](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/ExchangeNoOutboundGuard.java) | 现有 JDK ProxySelector 交易所域名 denylist 可作为一层防护；不是任意协议的操作系统出站隔离，也不能单独证明所有真实网络访问为零。 |
| MISSING | 当前 ordinary Order/fill 协议的独立 venue 连接 | 需要在 test scope 连接既有 Spring composition 与独立 venue-owned facts；不能引入第二套订单/账务状态机，不能借 dormant fake-worker 路径冒充 canonical qualification。 |
| MISSING | 可观察的 delivered/drop/close/delay | 现有 durable fake venue 提供 delay，但审计范围内未找到可直接复用的 test-only fault proxy，或独立请求到达确认后的 response-drop/close 实现。B0 只需补最小能力，不建设 chaos framework。 |
| MISSING | 本轮全约束下的成功 smoke | ENGAGED 全局风控阻断 fresh DB 中 canonical 下单，无 accepted order/fill 可供成功 smoke 和后续 recovery 使用。 |
| DELETE_DUPLICATE | 无 | 本轮未新建第二套平台，也未删除任何历史资产。 |

搜索范围为 backend/nq-app/src/test、backend/nq-infra/src/test、scripts 下进程、fake venue、HTTP server、proxy/fault 相关资产，以及发现的 production fake launcher。未发现不代表对所有历史归档进行穷尽扫描。

## 最小阻塞事实

1. [V35 migration](../../../backend/nq-infra/src/main/resources/db/migration/V35__gate_w4_durable_kill_switch.sql) 为 fresh database 写入 `GLOBAL_TRADING / ENGAGED`。
2. [TradingRuntimeConfiguration](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/trading/TradingRuntimeConfiguration.java) 的 riskGate 真实装配 KillSwitchRiskRule。
3. [KillSwitchRiskRule.evaluate](../../../backend/nq-risk/src/main/java/com/guidinglight/nexusquant/risk/service/KillSwitchRiskRule.java) 对 blocksOperations 无条件返回 `KILL_SWITCH_TRIGGERED / REJECT`；没有 SIM 豁免。
4. [OrderCommandWriteService.preparePlaceOrder](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java) 在拒绝时完成 `RISK_REJECTED` 并返回 completedResult。
5. [OrderCommandService.placeOrder](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java) 遇 completedResult 直接返回，因此不会调用 TradingVenueGateway.placeOrder。
6. Phase4 restart helper 的 RuntimeAccess.placeOrder 与 TradingChain fixture 都通过 SQL 将测试库 kill 改成 DISENGAGED 才执行成功路径。本轮不能复用该副作用，不能换成 mock RiskGate、伪造订单或写入 Trade/Ledger 终态。

因此拒单 smoke 可以安全执行，但不满足用户要求的 venue acceptance → Trade/Ledger smoke。最小待澄清项是：`kill disengage = 0` 是否包含 disposable 测试库，或仅约束真实/生产状态。本轮按明确字面边界执行，没有自行把它缩小为生产范围。该问题不要求先改 production；若全环境禁令保持，当前任务要求的成功 smoke 无法完成。

## 实际验证

环境：Windows / PowerShell，Java 21，Maven 3.9.12。没有 Linux real-process 支持证明，也没有 Windows B0 real-process 支持证明。

执行的最小目标测试（只选拒绝分支，不执行任何 DISENGAGED 测试 fixture）：

```powershell
mvn -o -f backend/pom.xml -pl nq-risk -am '-Dtest=KillSwitchRiskRuleTest#engagedUnknownMissingAndRepositoryFailureReject' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

结果：exit=0，Tests=1、Failures=0、Errors=0、Skipped=0。该 unit test 证明 ENGAGED/missing/repository failure 的拒绝规则；不冒充 Spring/JDBC/真实进程证明。首次命令中未引用第二个 `-D` 参数，PowerShell 导致 Maven 报 `Unknown lifecycle phase ".failIfNoSpecifiedTests=false"`，exit=1、测试未运行；修正参数引用后以上命令通过。

基线命令、GitHub CI readback 通过。文档链接直接逐项检查，收尾执行 status、diff --check、diff --stat、diff --name-only、diff；新增文件另作内容读回，未暂存文件不会出现在普通 git diff 中。

## B0 acceptance 状态

| 项目 | 本轮结果 |
| --- | --- |
| Controller / NQ JVM / Synthetic Venue JVM / fault proxy | NOT_STARTED；无 B0 启动命令、PID 或监听端口，未制造虚假身份 |
| PostgreSQL / DB identity / Flyway | NOT_STARTED；未创建 DB，无 DB identity，无实际 migration 结果 |
| Independent PID proof | NOT_RUN |
| Spring composition / transaction / JDBC / serialization proof | NOT_RUN；仅审计了历史实现 |
| Venue fact isolation | NOT_RUN |
| No direct DB mutation proof | 本轮数据库连接及写入=0；尚无新 Checker 可供行为验证 |
| No-fault smoke / restart smoke | NOT_RUN / BLOCKED |
| Response-loss capability | MISSING；未新增或验证 |
| ExecutionIntent / Receipt | 尚未进入链路；不声明有无，不以历史 fake-worker receipt 代替 ordinary Order 事实 |
| Real credential access / real exchange call / real PLACE / real CANCEL | 0 / 0 / 0 / 0 |
| Transfer / withdraw / LIVE enable / kill disengage | 0 / 0 / 0 / 0 |
| Real outbound | 真实交易所出站=0；基线核查使用了 Git/GitHub，只读网络访问不等于本轮 harness 出站证明 |
| Cleanup | 未启动 B0 子进程、venue、proxy 或 DB，因此无本轮 harness 进程/数据库待清理；Maven 已退出，常规 target 测试报告保留 |
| P0 / P1 | 未新增 production defect；B0 未验收，不能输出 B0 的 P0_0/P1_0 合格标记 |

## 变更与下一动作

Files changed / Added：仅本 evidence。Reused / Extended：审计完成，实际代码扩展=0。Deleted duplicate=0。Production files changed=0、Migration changed=0、CI workflow changed=0、credential/security boundary changed=0。未改 STATUS/ROADMAP/WORKLOG/TESTING，未改 F3/F4/F5/F6，未执行完整 B1–B5 matrix。

Commit recommendation：暂不生成 READY_TO_COMMIT 或成功实现的 git add 清单；没有 stage、commit、push、PR。本证据是阻塞记录，不适用 `test(l4): add real-process qualification harness` 成功提交标题。

Next action：明确 disposable 测试库的 kill 初始化边界后继续同一 B0；如仍禁止所有测试库解除 kill，则保持 BLOCKED。只有真实 B0 smoke/restart/fault/cleanup 验收通过后，才进入 `NQ-GATEAUDIT-PHASE6-L4-B1-ACCEPTED-TIMEOUT-AND-LOST-ACK`。

## 授权澄清后的实现与验收

用户明确授权：完全隔离、一次性 disposable DB 可以在 NQ JVM 启动前初始化 `kill=DISENGAGED`。生产/真实 kill DISENGAGE 仍为 0；场景内 SQL kill mutation、fake RiskGate、绕过 RiskGate 仍为 0。此前问题转为 `RESOLVED / DISPOSABLE_TEST_DB_INITIAL_DISENGAGED_ALLOWED`。

### Reused / Extended / Added / Deleted duplicate

- Reused：Phase4 `RestartDatabase` 的随机 DB、Flyway V48、JDBC lifecycle 与 drop 清理，原入口行为不变；Phase4 Trade/Ledger/position/account 和重复恢复不变量；canonical supply-chain lock 的 PostgreSQL 16 镜像。
- Extended：`TradingRestartRecoveryPostgresIntegrationTest.RestartDatabase.create` 增加显式 datasource overload，让 B0 使用其自身刚创建的实例而不是继承机器环境。原入口仅转调该 overload。F002 历史 R1/R2 fixture 没有重跑，其场景内 kill SQL 不作为 B0 资产执行。
- Added：[B0Fixture](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0Fixture.java)、[B0FixtureSafetyTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0FixtureSafetyTest.java)、[B0Processes](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0Processes.java)、[B0NqProcessMain](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0NqProcessMain.java)、[B0SyntheticVenueMain](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0SyntheticVenueMain.java)、[B0RealProcessHarnessTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0RealProcessHarnessTest.java)。均为 test scope；本文件是唯一 B0 evidence。
- 没有改动 production `DisposableFakeVenueLauncher`。其 fake-worker 协议不覆盖 ordinary Order 的 OKX fills，因此只为真实缺口新增小型 test-only venue 协议实现；不复制本地订单状态机、对账或账务逻辑。
- Fault proxy：不新增独立 proxy；venue 在持有独立事实后直接 close/drop/delay HTTP response，足以覆盖本轮最小能力。
- Deleted duplicate：0；实际删除文件=0。

### Fixture 与 Controller 分层

`B0Processes.Pg` 的 private constructor 仅由 `start()` 创建：新 UUID 容器、专属 identity label、tmpfs 数据目录、随机 127.0.0.1 端口。`B0Fixture.create` 只接受该所有权对象，没有任意 JDBC URL 的 bootstrap 入口；每次使用前读回 container label 与端口并比较。内部复用 Phase4 helper 新建 `nq_f002_b0_<32 hex>` 数据库并迁移至 V48。

初始化前校验 DB 名称、JDBC origin、profile、venue origin、环境键与对应值、尚未封存；连接后读回 current_database，检查无 Order、kill=ENGAGED，再生成 fixture identity。只有 success/restart 的前置 fixture 改为 DISENGAGED。bootstrap 在 NQ 启动前完成并封存，二次初始化永久拒绝。

bootstrap 只初始化账号、fixture 身份和初始 kill，未写 Order、Trade、Ledger、Intent/Receipt 预期事实。它创建三层角色：bootstrap owner 在初始化后关闭连接；NQ 使用非 owner `nq_b0_app`，明确撤销对 kill 两表、fixture identity 和 accounts 的写权限；Checker 使用只有 SELECT 权限的 `nq_b0_reader` 并开启 JDBC readOnly。应用与 Checker 的实际角色/权限均有运行时断言。场景开始后没有 SQL kill mutation。

8 个永久安全测试覆盖：合法 disposable 前置条件；缺失所有权/身份；shared/remote/非 PostgreSQL datasource、query/fragment smuggling；缺失 test profile；已启动后重新 bootstrap；credential/endpoint/Spring/Java option 注入；非 loopback venue；唯一 PostgreSQL socket 与 venue HTTP origin；子进程 fixture 参数不匹配。非法参数在 JDBC/进程启动前拒绝，错误为 `BLOCKED / UNSAFE_TEST_FIXTURE_TARGET`。真实 PG 场景另验证 bootstrap 二次调用拒绝与实际 DB role 权限。

### Controller / Spring / JDBC / serialization

Controller 是 Surefire JVM；通过受控 stdin 的 `PLACE / RECOVER / STOP` 命令触发子进程中的 canonical application service。没有新增 production API、generic provider endpoint 或 SQL mutation endpoint。

NQ 使用 `SpringApplicationBuilder(NexusQuantApplication.class, test configuration)`，profiles=`local,b0-test`，真实非 Web Spring Boot context。运行时断言 RiskGate 是 `PreTradeRiskService`，OrderCommandWriteService 是 Spring AOP proxy，gateway 是 production `AdapterBackedTradingVenueGateway`。只替换 test-scope adapter 的 transport 配置：production `OkxExchangeAdapter` + `OkxHttpClient` + `OkxInstrumentsCache` 连接独立 loopback venue，不注入真实凭证。

真实流向为：OrderCommandService → real RiskGate → Spring transactional write service → production gateway/OKX adapter → HTTP/JSON → venue-owned facts → production query/fills parsing → OkxRestReconcileService → real repository/JDBC → Trade/Ledger/Audit/Event。父进程在 A 仍存活且 `PLACE` 返回后通过独立只读连接读取 ACCEPTED，证明本地提交可见；A 死亡后仍可读，再由 B 继续恢复。Flyway 安装真实约束、索引与事务表，未使用 H2 或 in-memory local repositories。

### 最终 attempt-05 身份与事实

执行环境：Windows、PowerShell、Java 21.0.9、Spring Boot 3.5.10、Maven 3.9.12；Docker Desktop 中 PostgreSQL `16.15 (Debian 16.15-1.pgdg13+2)`。Linux host/JVM 未运行，不宣称跨平台已证明。

| 角色 | PID / identity |
| --- | --- |
| Controller | 42856 |
| Synthetic Venue | PID 26360，`http://127.0.0.1:13092`，独立进程内 venue-owned state，无 NQ datasource |
| Default-safety NQ | PID 37884 |
| No-fault smoke NQ | PID 37784 |
| Restart NQ A → B | 33932 → 57180；A 强杀确认退出后才启动 B |
| PostgreSQL | `nq-b0-3133ce06-2d06-4fe2-856a-1b5a9ca3ec3d`，container `e21d276c161227fd1b5438426f4cc11152bddcf84e1cf50b65dcb012033cf3c9`，host loopback port 39158 |
| Image | `postgres:16@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`，复用当前 CI lock |

三个 fresh DB 均通过 Flyway V48 与 DB identity 断言：

| 场景 | Database identity | 结果 |
| --- | --- | --- |
| Default safety | `nq_f002_b0_4c19eabffaa643a8ad98715cd1e89fbe` | 初始/最终 ENGAGED，real RiskGate REJECT，reason=KILL_SWITCH_TRIGGERED，Order=RISK_REJECTED，venue calls=0，Trade/Ledger=0 |
| Success smoke | `nq_f002_b0_4f18c27ffeb74ae0abc22f7a22f0fdb8` | 启动前 fixture DISENGAGED，real RiskGate ALLOW，Order `ord-d9d99463-0779-4154-83b9-434a8d58e016`，venue `b0-venue-1`，FILLED |
| Restart smoke | `nq_f002_b0_3a4b2a02ea7a4b41a5c7e18b90fa6c06` | A 提交 ACCEPTED、Trade=0 后强杀；B 在同一 DB 将同一 Order `ord-df2048ed-5f4f-48bc-b6bc-813e31cdecfb` 恢复 FILLED，venue `b0-venue-2`，不再 PLACE |

两个成功场景各断言：Trade=1，venue fill identity 匹配，price=123.45、qty=0.1、fee=0；Ledger entries=2、Ledger events=2、sum(delta)=0，position/account BTC=0.1；Audit 与 event_store 有对应 trace 事实。再次 canonical recovery 返回 0，Order/Trade/Ledger/LedgerEvent/position/account 全行快照不变。当前 ordinary adapter 链路没有执行 Intent/Receipt producer，实际两表 count=0；没有凭空补写 receipt。

Response-loss capability：Controller 对同一独立 venue 进行 3 个最小协议自检。DROP、CLOSE 均在保存 venue fact 后关闭 HTTP exchange，caller 实际收到 IOException，venue order count 分别增加 1；DELAY 保存后延迟 700ms，实际响应等待至少 500ms。DROP/CLOSE 共用“保存后关闭无响应”原语，未宣称两种不同 TCP packet 故障。正常 delivered 路径由成功 smoke 证明。这里只验能力，没有执行 B1 的 NQ accepted-timeout/lost-ACK qualification。

### 命令与验证历史

默认测试只运行安全负例；real-process suite 需要显式 `-Dnq.b0=true`。镜像预先按上述 digest 缓存；harness 使用 `--pull=never`，不会自动拉取。此次机器 Docker 原未启动，由本轮启动依赖服务并缓存该公开镜像；没有访问真实交易所。

从仓库根目录运行：

```powershell
mvn -o -f backend/pom.xml -pl nq-app -am '-Dnq.b0=true' '-Dtest=B0FixtureSafetyTest,B0RealProcessHarnessTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

最终 attempt-05：exit=0，**9 tests / 0 failures / 0 errors / 0 skips**；其中 8 安全测试，1 real-process integration test 包含 default/success/restart/fault/cleanup。Maven 总时间 38.103s，integration 24.13s。未执行 Full Maven、完整 B1–B5、发布 CI 或独立 review。

每个子进程命令为 `D:\Tool\Java\Java21\bin\java.exe @<run directory>/<label>.args`。args 文件包含 UTF-8/语言参数、当前 Surefire classpath 和对应 test main；没有凭证或 datasource。NQ 环境只含 OS 必需变量与本轮 `NQ_B0_DB/NQ_B0_VENUE/NQ_B0_PROFILE`。test main 用最高优先级启动参数指定 classpath config、datasource、关闭真实能力及后台外联；隔离 working directory 不加载机器外部配置。

最终本地原始日志：`artifacts/b0-attempt-05.log`；子进程 args/log：`backend/nq-app/target/b0-harness/54403df4-7756-4ab6-a39f-0f71283e2fac/`。这些是 ignored 本地验证输出，不在本次 git add 清单；上面的身份、事实与测试断言构成可随源码重现的精简证据。

| Attempt | 结果与修复 |
| --- | --- |
| 初次 compile/default run | 编译通过，安全测试 5/5；real-process 未显式启用，条件跳过 1，不冒充验收 |
| 01 | Docker Go template 在 Windows 引号解析失败；改为 JSON inspect；venue/container 清理完成 |
| 02 | 新出站白名单错误拦截 JDBC socket；仅增加本轮准确 DB socket，保留拒绝其他目标；子进程/container 清理完成 |
| 03 | Checker 使用不存在的 risk_events.rule_code；核对实际 schema 后改用 reason；default request 已安全拒绝，清理完成 |
| 04 | 8/8 通过，完整 real-process smoke/restart/fault/cleanup 通过 |
| 05 | 最终增加成交数值、账务净额与缺失所有权负例后重跑，9/9 通过；这是本次最终源码的验证 |

### No-real / cleanup / limitations

- Real credential read=0、real exchange call=0、real exchange PLACE/CANCEL=0、transfer/withdraw=0、LIVE enable=0、REAL/PRODUCTION KILL DISENGAGE=0、RUNTIME TEST-SCENARIO DIRECT KILL BYPASS=0。两次 disposable success fixture 的启动前 DISENGAGED 为明确授权 TEST_PRECONDITION。
- NQ 子进程清空继承环境，仅选取 OS 必需字段；拒绝 injected credential/endpoint/Spring/Java options；venue 拒绝 OK-ACCESS private headers。JDK 默认 ProxySelector 只允许准确 venue HTTP origin 与本轮 PostgreSQL socket。该层有永久负例，未宣称是 OS 级防火墙或任意第三方 native socket 拦截。
- 5 个子进程全部确认退出；三个数据库分别 drop 后通过 pg_database 读回不存在；container force-remove 后读回不存在；全局按本轮 label 查询容器残留=0。Docker Desktop 与缓存镜像属于运行依赖，未删除缓存、未停用共享 Docker 服务；本轮没有残留 NQ/venue/PG runtime 或 DB。
- venue state 为独立进程内内存，NQ 重启期间持续存活；未证明 venue 自身重启后的事实持久性。B0Processes 提供独立 start/kill 的基础，但未执行双活 NQ 并发、venue-restart 恢复或完整故障矩阵。
- 本轮是非 Web Spring application-service composition proof，不是 HTTP Controller 鉴权或 WebSocket qualification。B3 canonical ENGAGE 尚未实现/验证；当前 app role 故意禁止 kill SQL 写，后续 B3 需按其专属授权和 canonical command 设计能力，不可删掉 B0 的边界证明。
- current STATUS/ROADMAP 的 pre-B0 旧状态未同步；本文记录本轮本地 B0 实现，不自行制造 authority acceptance。F3/F4/F5/F6 未动。

### Self review / Git / final decision

自查确认：仅 test harness、test-only venue、test guard、evidence；production files=0、migration=0、CI workflow=0、production network/security composition=0。fixture 写入仅 bootstrap，Controller/Checker 无业务/kill mutation；独立进程与 SQL 权限均由实际结果验证；没有第二套本地交易状态机、账务或 reconciliation。P0=0、P1=0（本 B0 候选自查范围），不代表全项目重新审计清零。按用户本轮 review policy，不另开 Independent Review。

收尾检查包括 `git status --short`、`git diff --check`、`git diff --stat`、`git diff --name-only`、`git diff`，以及对六个 untracked Java 文件和本 evidence 的内容自查。普通 git diff 不包含 untracked 文件，因此精确清单同时列出新增文件。未执行 stage/commit/push。

精确暂存建议（仅生成，未执行）：

```powershell
git add -- backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TradingRestartRecoveryPostgresIntegrationTest.java backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0Fixture.java backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0FixtureSafetyTest.java backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0Processes.java backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0NqProcessMain.java backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0SyntheticVenueMain.java backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0RealProcessHarnessTest.java docs/audit/evidence/GATEAUDIT_PHASE6_L4_B0_REAL_PROCESS_HARNESS.md
```

Commit recommendation：`test(l4): add real-process qualification harness`。

Final decision：`IMPLEMENTED / SELF_REVIEWED / B0_REAL_PROCESS_HARNESS_READY / P0_0 / P1_0 / READY_TO_COMMIT`。

Next action：`NQ-GATEAUDIT-PHASE6-L4-B1-ACCEPTED-TIMEOUT-AND-LOST-ACK`；本轮未开始 B1。Git 发布动作仍待明确授权。

### 最终源码身份与收尾读回

以下为按仓库 attributes 规范化的 Git blob identity；没有执行 git add。attempt-05 后仅修改本 evidence，测试源码未再改变。

| Source | Git blob |
| --- | --- |
| TradingRestartRecoveryPostgresIntegrationTest.java | 91a113b505fbe298dff4e208b5986a781662dfe7 |
| B0Fixture.java | 6497e81bd785fa2a5f464ce8d5bf52bc5f4604c8 |
| B0FixtureSafetyTest.java | 3779c00d4f23122619fedd2531e9edc5d63aafb5 |
| B0NqProcessMain.java | 48e7694eff0280d703b1f06b60924f2e49caa6c1 |
| B0Processes.java | 15d8882a4dd85567daadb4095422bd4b1789291e |
| B0RealProcessHarnessTest.java | 340a01a5b48bb991dddee69a8235ecff38e0c002 |
| B0SyntheticVenueMain.java | 1374816d54b63c7f5dcc4f997ad5423041b9e7a0 |

最终 doc links checker：checked=19、warnings=0、errors=0；diff --check 通过，新增 Java 逐文件 whitespace 检查通过。独立 Get-Process 读回 26360/37884/37784/33932/57180 均不存在；Docker 按 nq.b0.identity label 查询无残留。
