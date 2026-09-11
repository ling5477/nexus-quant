# Phase6 L6-A Active Stability

Task=`NQ-GATEAUDIT-PHASE6-L6-A-ACTIVE-STABILITY`；`HIGH_RISK / LONG_RUNNING_QUALIFICATION / ACTIVE_SOAK / RESOURCE_STABILITY / NQ-only`。

**BLOCKED / L6_ACTIVE_STABILITY_READINESS_FAILED**。正式60分钟active soak未开始，L6=`NOT_ACCEPTED`。失败发生在独立短时readiness的warm-up内，不能计为长期资格或用首个绿色检查点替代全程证明。生产与migration均未修改；未故障注入、未重启工作进程、未执行真实交易。

## 基线与范围

HEAD=`23548b75093a62d7614e16f8abcaf9ff2ea32ed7`，branch=`audit/post-gatey-agent-baseline`，入口工作区clean、stage=0，HEAD=origin。L5 immutable CI=`34608208969 / completed / success`，继承已接受L4/L5，不将本失败改写为历史L5失败。

[冻结规划](../GATEAUDIT_PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN.md) SHA-256=`80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`。本次用户要求60分钟全active，不含warm-up/drain，优先于旧规划时长分配；原规划文件不改。

新增test-only入口复用B0隔离PG16.15/V51、L5 Synthetic Venue、真实Spring/RiskGate/V49/V50/V51及账务链。正式计划为2 JVM、2 actor、2策略、每分钟2个窗口、最多240订单；正常validation 5min/初始30s，V51 recovery 5s；资源10s、账务约30s采样。初始研究/策略fixture在启动前创建，运行期controller仅SELECT；独立PaperRun由真实服务创建，未SQL伪造ordinary成功事实。

修改范围是qualification launcher/driver、轻量资源采样与oracle。B0日志读取改为增量、完整行读取，避免长跑反复加载完整日志；L5 helper仅调整包内可见性。详见[机器汇总](summary.json)及[短时指标](metrics/summary.json)。所有新增harness仍未通过L6 readiness，不能声称已具备60分钟资格能力。

## 首次失败与因果边界

readiness命令：`mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=L6ActiveStabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6=true' '-Dnq.l6.diagnostic=true'`。该命令仅选中1个测试，不是Full Maven suite。先前一次未正确引用PowerShell参数的命令在启动测试前失败，日志同样保留。

真实运行在首个完整检查点验证2 Order/2 Trade/2 TradeExecuted/8 Ledger、2 SUCCEEDED StrategyRun、Position BTC0.2、latest BTC0.2/USDT0、actionable=0。随后新一分钟窗口产生另外两个StrategyRun；第二actor日志返回`RECONCILE 2`，第一actor在对账中抛出`IllegalStateException: invalid order transition: FILLED -> FILLED`，F007记录`okx_reconcile.FAILURE=1`，异常穿透stdin测试入口使该JVM退出。Maven=`1 test / 1 failure / 0 errors / 0 skips / BUILD FAILURE`。

[OkxRestReconcileService.alignOrderStatus](../../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java)先查询currentStatus并排除同态/终态，之后才调用[OrderCommandWriteService.transitionOrder](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java)的事务回读。另一actor可在两次读取之间完成FILLED；`transitionOrderAttempt`先做状态机校验，后CAS，因此本次同态拒绝发生在CAS保护之前。这一调用链与真实异常一致，但没有冻结该并发时序的独立复现。

确认的是production对账调用异常；JVM退出是test launcher传播异常的结果，不能据此声称生产scheduler永久停止。故障后的完整DB/Venue快照未捕获，owned DB已由finally清理，不能推断所有4个订单已完成、最终无重复/无orphan，也不能把首个检查点外推为失败时最终事实。

Finding=`L6_RECONCILIATION_SAME_STATE_RACE / QUALIFICATION_BLOCKING / IMPACT_TRIAGE_REQUIRED`。P0 confirmed=0、P1 confirmed=0；尚未证明重复账务、损坏、永久停滞或资源耗尽，不武断分配P1，也不把该故障降为可忽略项。未捕获最终快照是本次证据限制，后续fixture需在teardown前保留失败现场。

## 已取得与未取得的观测

- active duration=0；warm-up未完成；drain未完成。仅1个完整正确性检查点、2个5s业务采样；后续窗口只有日志观察，不计最终账务PASS。
- 独立Paper CREATED正常monitor无告警；真实CRITICAL正例1条，F007 EMITTED=1；同日期report生成两次仍1条，alertCount=1。该链不与ordinary Ledger自动关联，report的0交易/收益不是账务oracle。
- 取得heap/GC、threads、Hikari active/idle/pending/max、V51 tick start/end、OS handles/working set和日志/文件大小。短时样本不能评估rolling plateau、长期泄漏、fairness衰减或60分钟forward progress。validation首次30s触发前已终止，mandatory timer覆盖尚未完成。
- 完整快照oracle正例PASS；6个离线变异（Position、Snapshot、PLACE、Event、Ledger、orphan）全部拒绝。不是新的动态资格运行。
- 清理：owned NQ JVM=0、Venue JVM=0、PG容器=0；失败前原始日志和采样保存在ignored target。没有清理其他任务资源。
- `LOG_ROTATION_NOT_YET_QUALIFIED`留待后续observability/logging baseline；不单独将其判为本次失败原因。

## 保留项与下一步

ordinary concurrent INSERT loser P2、wildcard-import P3仍OPEN/NON_BLOCKING。`HISTORICAL_PROJECTION_REPAIR_REQUIRED`仍OPEN，保留pre-freeze/release-baseline义务，未修历史数据。>4 JVM、>240候选、real exchange、LIVE、multi-day stability及inactive/retired路径不计PASS。

先独立处置同态对账并发窗口及影响范围；若需修改production correctness，应另行授权并完成必要风险证明与真正独立审查，之后重新进入L6-A。当前不进入规划的L6-B `RESTART-CONTINUITY-AND-AGGREGATE`。

本轮stage=0、commit=NONE、push=NONE；Full Maven及正式60分钟运行均NOT_RUN。卫生结果见summary的hygiene字段；卫生通过不解除资格阻断。
