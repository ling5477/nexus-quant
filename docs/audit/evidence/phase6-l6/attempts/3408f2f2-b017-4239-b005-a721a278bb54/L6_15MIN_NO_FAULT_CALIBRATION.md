# L6 15min No-Fault Calibration — Attempt 01

**BLOCKED / CALIBRATION_INFRASTRUCTURE_FAILURE**。Run=`3408f2f2-b017-4239-b005-a721a278bb54`。本次正式attempt失败，未形成可冻结的healthy rate或noise bands；未创建 `L6_FORMAL_CALIBRATION_MANIFEST.json`，L6仍NOT_ACCEPTED，60min/180min未启动。

## 候选与执行

Branch=`audit/post-gatey-agent-baseline`，HEAD/upstream=`3f2671bf9e5292d0d0194c0b4f830ae8c2ba8525`，tree=`d5ef2879f7e9e927e5872ef97ff813294c5acd48`。只读复核[exact-head CI 34750388230](https://github.com/ling5477/nexus-quant/actions/runs/34750388230)：9/9 SUCCESS。本轮没有新提交、push或CI运行。入口保存3984个Git可见文件的path/size/SHA-256，结束全部一致，包含13份既有未提交evidence；stage始终0，production/test/harness/config delta=0。

Windows / JDK21；owned PostgreSQL16.15 / V51，1 Synthetic Venue、2 NQ actor、1 controller，loopback隔离fixture。固定CALIBRATION、smoke=false，300s warmup+600s measurement，无故障注入、重启、Kill transition或真实provider。STATUS machine保留较早阶段事实；本轮隔离运行范围来自用户明确的本任务授权，不扩张生产/LIVE权限。

```powershell
$env:MAVEN_OPTS='-Xmx512m'
mvn -o -f backend/pom.xml -pl nq-app -am test '-Dtest=L6CalibrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6.calibration=true' '-Dnq.l6.calibration.smoke=false' '-DargLine=-Xmx512m'
```

真实运行仅一次。Maven退出1，JUnit 1 test / 1 failure / 0 errors / 0 skips。`AssertionError: L6 work failed bounded convergence`，栈位于 `L6BusinessCheckpoint.java:31`，由 `L6CalibrationTest.phase` 调用。没有现场修复、参数修改或重新运行。

## 阻塞原因

已提交calibration入口声明600订单预算、两策略每5秒产生合法窗口；15分钟持续输入约360订单。复用的 `L5VenueProcessMain.java:6` 固定启用boundedWorkload，而 `B0SyntheticVenueMain.java:140-141` 在300个不同订单后直接返回HTTP429 / `L5_ORDER_BUDGET`。预算在同一已提交harness组合内不一致，属于本轮P1 test/harness blocker。

动态事实吻合该边界：checkpoint150证明300个Order/fill/Trade/TradeExecuted完整完成、1200 Ledger、Position/Snapshot BTC=30；其后最新采样为302 Order、300 terminal/Trade/succeeded run，2个未收敛候选。共享checkpoint持续进行既有reconciliation，120秒有界收敛失败后清理退出。最后HTTP429响应正文和失败时完整DB checkpoint未被失败导出路径保留，因此预算路径是静态代码与动态停止边界的归因，不伪称拥有那两次HTTP响应抓取。Venue采样的`rejected=0`指executor任务拒绝，不能拿它否定订单HTTP预算拒绝。

## 时间、进度和速率

T0=`2026-09-13T10:07:08.983026500Z`；warmup实际300001ms，通过60个checkpoint，完成120条全链。measurement计划绝对窗口[300000,900000)ms；最后全链完成观测在748554ms，最后资源样本在870001ms。失败路径没有导出精确单调failure timestamp或measurement end；不能用15:02 Maven whole-run耗时声称完成900秒业务窗口。

measurement已观测新增180个完成链，总计300；随后业务进度停止，最终600秒窗口未完成。healthySustainedRate、eligibleSteadySeconds、完整暂停/背压秒数、25PercentRaw、finalL6ArrivalRate全部不可用/null；不把已完成的局部窗口计算成正式rate。冻结公式没有修改，失败中的未结束等待也不伪造为完整暂停区间。

## 采样与noise bands

统一10秒sampler保留88个有效样本：warmup30、measurement58（目标60，缺最后2个计划时隙，因为run失败结束）。已采样观察的mandatory missing=0、stale reuse=0、cadence violation=0，最大start lag16ms、collection408ms；**窗口覆盖不完整**，不报告MANDATORY_10S_SAMPLING_COMPLETE。

机器summary保留measurement各资源sampleCount/min/max/range以及log/audit/event绝对值、逐拍delta和per-order delta；全部DIAGNOSTIC/NOT_FREEZABLE。GC用PID/collector/id去重，结合actor JVM uptime和样本elapsed定位事件，warmup事件排除；即便有measurement内G1 Young低谷，失败且不完整的run仍不能冻结heap band。普通heap min/max只作诊断，未作为heap noise band。

失败前资源与业务序列均保留；per-full-chain完整窗口增长及成功quiet/drain行为未建立，不能用累计log/audit增长直接断言leak。唯一阻塞是上述预算合同冲突引发运行未完成，并非已完成运行后另有一个GC缺项。

## 正确性与清理

150份已成功checkpoint全部用原oracle离线复核通过，既有唯一性/账务/Position/Snapshot/orphan断言保留；其覆盖截止300个已完成链。该范围duplicates=0、orphans=0，未发现新增生产P0/P1；最后2个未收敛链缺少最终全量正确性验收，不将全run填写P0_0/P1_0或CORRECTNESS_PRESERVED。

最后SQL backlog=2、correctnessRequiredUnresolved=2；SQL actionable=0不能代表恢复工作已经结束，两actor eligibleCandidateCount均2、oldestCandidateAge约119.8秒，并非NONE。Hikari pending均0、acquisition timeout delta全程0、最后idle-in-transaction=0，command queue与Venue queue均0。

异常路径关闭owned actors，终止Venue并移除owned PG；随后只读核对NQ10944/5880、Venue30164、controller20944均无存活进程，owned PG container `13ca8a755a03dac6748fe39d1445c0978ea7de48cd835c29baa957805eb32180`不存在。owned survivors=0。Venue异常清理日志`B0_KILLED`不是本轮注入的故障或运行期Kill transition。业务drain未通过，与资源清理成功分开记录；既有无关容器nq-c1-occ-20260907未触碰。

## 证据与后续边界

- [失败原始run](runs/3408f2f2-b017-4239-b005-a721a278bb54/calibration-run.json)
- [统一资源序列](metrics/3408f2f2-b017-4239-b005-a721a278bb54/resource-samples.ndjson)
- [机器汇总、partial noise诊断与raw SHA-256索引](metrics/3408f2f2-b017-4239-b005-a721a278bb54/summary.json)
- [已完成checkpoint索引](runs/3408f2f2-b017-4239-b005-a721a278bb54/checkpoints.ndjson)
- [producer/full-chain进度](runs/3408f2f2-b017-4239-b005-a721a278bb54/progress.ndjson)

原始完整checkpoint JSON、actor/Venue日志、参数和Maven/XML保留在ignored `backend/nq-app/target/l6-calibration/3408f2f2-b017-4239-b005-a721a278bb54/` 与 `backend/nq-app/target/l6-formal-calibration-20260913-attempt01/`，总计约447MiB；机器汇总逐文件绑定hash。失败证据不覆写既有smoke或历史accepted/blocked证据。

P1 test/harness blocker=1；新增生产P0/P1未被本轮确认，历史P2/P3保持。后续需单独授权处理calibration与Synthetic Venue预算合同，再经验证交付后另起正式attempt，从T=0重新执行。本轮到保全、分类、停止为止；禁止启动60min/180min。
