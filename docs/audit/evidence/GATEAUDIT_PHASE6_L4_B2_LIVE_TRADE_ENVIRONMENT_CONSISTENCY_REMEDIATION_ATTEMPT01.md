# B2 LIVE Trade 环境一致性整改 Attempt-01

日期：2026-09-09。任务：`NQ-GATEAUDIT-PHASE6-L4-B2-LIVE-TRADE-ENVIRONMENT-CONSISTENCY-REMEDIATION`。

**IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW / B2_LIVE_TRADE_ENVIRONMENT_REMEDIATED / P0_0 / LOCAL_P1_0**

**B2 = NOT_ACCEPTED**。本轮为实现与自查，不是独立接受。本会话现已参与实现，不能再充当该新候选的独立 reviewer。

## 基线与失败审查保留

起始 branch=`audit/post-gatey-agent-baseline`，HEAD 与 origin tracking ref 均为 `d1cedb6debfaa3dbeacb0e95ef8599c69e5f9da3`。已执行用户指定的 status/branch/HEAD/origin/diff --check；开始时存在上一轮 B2 的7个 production 与8个 test候选，不把它称作干净 checkout。stage=0、commit=NONE、push=NONE。

[上轮失败 Independent Review 原文](l4-b2-live-env-remediation-attempt01/previous-independent-review.txt) 为原始文件字节副本；原外部文件继续保留，raw SHA-256 见 manifest。失败结论及上轮 Full Maven 的环境污染不被本轮成功结果覆盖。原 B2 race 与 terminal remediation evidence 保持原样。

本轮只修改既有候选中的2个 production文件、3个 test文件，并新增本证据及附件。既有 B2/C1/C2 修改的来源仍属于前一轮；没有修改 migration/V48、.github、AGENTS/Skills、STATUS/ROADMAP 或 frozen history。

## 根因与 canonical environment owner

实际路径为 `venue fill → OkxRestReconcileService → PaperTradeRecord → JdbcTradeRepository.insert → TradeLedgerGateway`；PaperMatching、Binance reconciliation 与历史 pilot 的 Trade 创建也委托同一仓储。

唯一环境事实来源为已持久化 `orders.trade_env`，对应已有 `OrderRecord.tradeEnv` 与 `PlaceOrderRequest.tradeEnv` 的 SIM/LIVE 合同。该事实源存在且可靠，无需新增 SoR、enum、Trade environment 自由参数或 schema。

原 JdbcTradeRepository.insert 不写 trade_env，V5 中 `DEFAULT SIM` 在 BEFORE trigger 前生效，trigger只在 NULL/blank 时继承父订单。因此 LIVE订单产生SIM Trade；此前新增强成交证明正确拒绝这种不一致，阻断 CANCELLED → FILLED。

本轮 [JdbcTradeRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/scheduler/infra/jdbc/JdbcTradeRepository.java) 在原有短事务/订单行锁内显式 INSERT trade_env，取值为 `(SELECT trade_env FROM orders WHERE order_id=?)`。父 identity 与用于数量锁的 orderId一致；无 profile、adapter名称、synthetic标志或默认常量推断。没有新增方法参数，PaperTradeRecord及其构造契约未变，普通调用方无法指定一个独立于父订单的Trade环境。

同一仓储的三个读取入口 `findByOrderId/findAllByOrderId/findByExchangeAndExchangeTradeId` 在查询中连接父订单，读取两侧环境；不一致抛 `TRADE_ORDER_ENVIRONMENT_MISMATCH`。这是拒绝读取损坏事实，不是过滤掉它后继续累计，也不自动 UPDATE历史Trade。完整数量证明原有 trade_env guard保留。

[OkxRestReconcileService](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java) 捕获上述异常后，沿用 `OKX_LEDGER_RECOVERY_INCOMPLETE` canonical audit，reason=`TRADE_ORDER_ENVIRONMENT_MISMATCH`，随后抛错。拒绝发生在状态对齐、新Trade写入和Ledger重放前；未把该异常误记为数量分页截断。

Trade identity、exchangeTradeId唯一约束、数量/fee计算、Ledger posting规则、资产方向均未改变。发现的关联问题仅为环境传播/读取一致性，未扩展到账本模型整改。

## SIM / LIVE terminal convergence 与真实进程

[B2RealProcessProofTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B2RealProcessProofTest.java) 保留原9次SIM场景，新增3次LIVE标识场景。每次真实Spring NQ JVM、真实RiskGate、事务代理、canonical Order/Trade/Ledger、HTTP/JSON、独立Synthetic Venue JVM与PostgreSQL16.15/V48。

[B0NqProcessMain](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B0NqProcessMain.java) 仅增加受限fixture命令PLACE_B2_LIVE，经已有PlaceOrderRequest显式声明LIVE并走真实OrderCommandService；Trade的环境仍只能从已落库父Order继承。Synthetic adapter的既有SIM transport标签不决定Trade环境，进一步证明传播不依赖adapter标签。所有网络仍被限制为本轮loopback synthetic origin/DB；真实LIVE capability/real-provider开关保持false，真实credential/provider/交易调用为0。

本节LIVE指已持久化的订单环境字段，不是执行真实交易的授权或生产运行。B0私有DB启动前一次性TEST_PRECONDITION及角色封存沿用已存在harness，未新增运行中kill bypass。Controller用reader角色只读业务表。

| 环境 / 场景 | NQ PID | 重启 PID | Venue PID | 最终 Order/version | executed/remaining |
| --- | --- | --- | --- | --- | --- |
| LIVE partial cancel | 72556 | — | 9588 | CANCELLED/6 | 4/6 |
| LIVE full race | 57440 | — | 18580 | FILLED/7 | 10/0 |
| LIVE restart race | 6976 | 68328 | 46016 | FILLED/7 | 10/0 |
| SIM partial/full/restart，各3次 | 见12份proof与manifest | 同左 | 同左 | 部分CANCELLED/6；足量FILLED/7 | 4/6或10/0 |

Controller PID=72304。LIVE与SIM每笔Trade均与对应Order环境一致。

- Partial：Q=10 → fill4/fee0 durable → cancel ACK → venue cancel effect，Order=CANCELLED，remaining=6。
- Full：fill4 → cancel ACK、本地CANCELLED/6 → fills3+3、fees0.01/0.02 → venue FILLED → ordinary recovery → FILLED/7，remaining=0。重复报告6条只累计3个unique fills。
- Restart：旧NQ确认被杀后venue才产生剩余fills，新PID用同一DB普通reconciliation恢复；PLACE总数仍1。
- Replay：再次RECOVER=0，Order/Trade/Ledger/ledger_events/positions/account_snapshots及纠正audit/event完整快照不变。部分版本不变；完整只version+1，纠正event/audit各1。
- Accounting：逐fill核对100×4=400、100×3=300、100×3=300及0/0.01/0.02USDT费用、canonical分录key/delta/currency/direction；不按固定分录数判断。部分观察为1Trade/2Ledger，完整为3Trade/10Ledger；不把既有成对账本模型解释成真实钱包结算证明。

## PostgreSQL 对抗与相关回归

[B2TerminalCorrectionPostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B2TerminalCorrectionPostgresIntegrationTest.java) 共13次PASS。新增LIVE部分→足量环境继承、LIVE overfill、两个方向的environment mismatch拒绝。损坏场景只在隔离PG fixture使用SQL改坏Trade环境，用于证明拒绝；不是用SQL制造real-process通过结果。

两个方向均证明：三个仓储读取入口拒绝、terminal service拒绝、atomic CAS=0、reconcile抛出对应错误并记录一次canonical audit、Ledger=0、Order/version与原错误Trade环境均不变。没有偷偷修复历史事实或跨环境凑足数量。

保留既有overfill=10.00000001、并发额外fill、并发不同fill超量拒绝、三次并发终态CAS仅一次成功、event失败整事务回滚与普通状态机禁止CANCELLED→FILLED的证明；没有clamp。

C1/C2相关PG=28次PASS，包括stale PLACE/CANCEL、ABA、pre-cancel、OCC/version、共享总limit、cursor持久化/公平性、venue过滤、CANCELLED missing-fill discovery、identity-missing no-query及Trade/Ledger replay。V48和cursor实现未修改，不重新授予C1/C2接受身份。

## 历史 LIVE reachability（一次只读检查）

**REACHABLE_BUT_PERSISTED_ENVIRONMENT_NOT_PROVEN**

检查归档 [pilot summary](../../gates/gate-y/GATEY_MINIMAL_LIVE_PILOT_EVIDENCE_SUMMARY.md)、[end-to-end evidence](../../gates/gate-y/source/task-evidence/NQ-GATEY-MINIMAL-LIVE-PILOT-END-TO-END.attempt-01.md)，并只读查看发布commit `8e3dd0cf6104eb85f36a0e434ca51ea9d903705a` 中MinimalLivePilotConfiguration/JdbcTradeRepository：pilot `reconcile → persistFills → PaperTradeRecord → trades.insert` 确实使用同一仓储路径，历史INSERT同样未显式写Trade环境。

归档保存Order=FILLED/LIVE、Trade=1、Ledger=4，但没有该Trade实际trade_env字段读回。代码路径/默认值能证明风险可达，不能替代历史持久化字段证据，因此不声称历史misbinding已被证明，不触发第三类停止结论。production DB未连接，历史evidence未改，真实pilot未重跑，GateY不重开。后续若独立只读授权证明第三类，必须另行停止处理历史事实，不能自动改库。

## 验证身份与 Full Maven

[Manifest](l4-b2-live-env-remediation-attempt01/manifest.json) 绑定15个最终候选Java源码Git canonical blob与12份新proof，源码fingerprint=`1262ace8561a134343d32f92b6efeaaeb7bcc22875683c5957e546c044d6e77e`。算法：sources顺序的 `path + 空格 + gitBlob` 用LF连接、无末尾LF，取UTF-8 SHA-256。测试后重新核对一致。

| 验证 | 结果 |
| --- | --- |
| 定向回归 | 189 tests，0 failures/errors/skips，exit0，2:43；其中2条B2 JUnit tests承载12个真实进程场景 |
| 实现阶段Full Maven（仅一次） | 1836 tests，0 failures/errors，84条件性skips，exit0，1:34；实际未skip=1752 |
| production环境增量回滚patch | `git apply --reverse --check` PASS；未应用 |

详细[定向摘要](l4-b2-live-env-remediation-attempt01/targeted-summary.txt)、[全量摘要](l4-b2-live-env-remediation-attempt01/full-maven-summary.txt)、[原始日志归档](l4-b2-live-env-remediation-attempt01/run-logs.zip)。归档保留定向/全量原始日志和各Synthetic Venue/NQ/restart日志。Full Maven原始输出存在平台编码差异，摘要仅从原始字节提取ASCII测试统计行，不改原始日志；raw SHA见manifest。

Full Maven精确命令 `mvn -f backend/pom.xml test`；独立子进程通过环境白名单清除非canonical覆盖。SPRING_PROFILES_ACTIVE=UNSET、NQ overrides=[]、Java options=UNSET；测试通过自身声明选择profile。仅设置本轮loopback datasource及非空公开fixture密码；未打印或读取真实secret。配置记录见 [full-environment.json](l4-b2-live-env-remediation-attempt01/full-environment.json)。

定向DB=`nq_l4_blocker`。全量单独新建DB=`nq_b2_env_full`，Flyway使用原V1–V48，在开跑前确认Order/Trade/Ledger=0，只seed该既有Research测试要求的1个account。未使用共享脏库，未设置全局profile或人为设置空密码。公开fixture密码不用于生产；prepare/run脚本仅是本轮执行记录，历史端口/容器不可直接复用。

上述Full Maven仅为实现证据；最终接受仍以独立review中对固定新候选、干净canonical test environment的一次Full Maven为准。不能因本轮绿色而改写前一review的失败事实。

## 回滚、清理与下一步

[production-environment-only.patch](l4-b2-live-env-remediation-attempt01/production-environment-only.patch) 只包含本轮两个production文件的增量，基于开始时的旧候选还原并与其manifest Git blobs核对；反向check成功。若要撤回，先重核候选/已有用户改动，再只反向应用该增量；不撤销上轮B2 terminal修复或历史evidence。回滚会恢复本环境P1，不能同时声称整改成功。没有schema回滚，不对既有错误环境事实自动回填。

专用PG容器ID=`ff21a630905d3ff75cfe7d5230200ef0eb82176d53983d4c2031f674ef9bafd6`，label=`nq.b2.env.identity=01a083f7`，loopback port40037。完成后核验identity/label，只删除该自有容器。B0自有容器由harness清理并输出remaining0；相关label枚举为空。未停Docker或删镜像。stage=0、commit=NONE、push=NONE。

最终执行status、diff --check/stat/name-only/diff读回与源码/证据hash核验；原有候选之外仅本轮五个Java文件变化和新evidence。没有migration、权限、安全开关、真实provider、账务规则或交易额度扩展。

下一动作：`NQ-GATEAUDIT-PHASE6-L4-B2-LIVE-TRADE-ENVIRONMENT-INDEPENDENT-CORRECTNESS-REVIEW`，须由未参与此候选实现的独立会话进行。B2仍NOT_ACCEPTED，未运行或完成剩余完整qualification matrix。
