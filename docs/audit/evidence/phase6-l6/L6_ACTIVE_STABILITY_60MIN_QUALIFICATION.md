# L6 Active Stability 60min Qualification — Preflight 03

**BLOCKED / QUALIFICATION_INFRASTRUCTURE_FAILURE**。Reason=`L6_A_FROZEN_CONTRACT_NOT_CONSUMED`。

Attempt=`L6_A_60MIN_20260913_PREFLIGHT_03_d94f8512`。本轮在正式T=0之前发现已交付入口无法执行冻结pacing合同，按用户“发现tooling defect保存并停止、禁止harness修改”的要求停止。**60min NOT_STARTED / L6-A NOT_ACCEPTED / L6 NOT_ACCEPTED / 180min NOT_STARTED**。未启动Maven qualification、NQ、Venue、controller或PG；不是已运行60分钟后的FAIL。

## 已通过的输入核对

Branch=`audit/post-gatey-agent-baseline`；HEAD/upstream=`d94f8512108fd7f8e9cbdeee14cfc8b064f2d5d8`，tree=`0c010b7d80327f5e8ec994cd6a7073e6853fe3ea`；stage=0，tracked diff=0。起始4014个Git可见文件均为已交付候选和已知L6 evidence，未发现未解释production/test/harness/config drift。

实时只读核对 [exact-head CI 34753536151](https://github.com/ling5477/nexus-quant/actions/runs/34753536151)：completed/success，9/9 SUCCESS，headSha精确匹配。

Canonical manifest=`L6_FORMAL_CALIBRATION_MANIFEST.json`，size=134009 bytes；entry SHA-256与exit SHA-256均为：

```text
922253c8934e32b7626ced2f9e42407a2a4313c665256b89c483e847ce6abaa6
```

直接读取并核对：healthySustainedRate=`0.5619832388499013 orders/s`，finalL6ArrivalRate=`0.14049580971247533 orders/s`，pacingInterval=`7.11765s`，pacingIntervalNanos=`7117650000`。值与本轮接受事实一致，noiseBands存在且原样保留；未重新计算rate、25%、interval或noise bands。**Calibration继续ACCEPTED；阻断不是manifest drift或value mismatch。**

## 已确认的运行入口缺口

单一finding：**P1 / TEST_HARNESS / L6_A_FROZEN_CONTRACT_NOT_CONSUMED / QUALIFICATION_BLOCKING**。

| 当前源码 | 已交付行为 | 与本轮合同的关系 |
| --- | --- | --- |
| `L6ActiveStabilityTest.java:49,75` | formal capacity校验后进入executeSoak，调用seed(fixture)；没有manifest读取 | 没有将冻结输入绑定到运行入口 |
| `L6ActiveStabilityTest.java:199` | 非calibration模式两条策略均使用 `0 * * * * *` cron | 仍为两条分钟窗口，不是每7.11765s一个正式到达 |
| `L6ActiveStabilityTest.java:145–169` | produce时扫描两个actor，循环等待5秒 | 没有冻结的deterministic admission pacing或相应runtime参数 |
| `QualificationCapacity.java:27` 与 `L6ActiveStabilityTest.java:146,160` | formal run budget/Venue capacity/maximum均240，reserve与working-set检查仍固定240 | 旧workload容量合同未与manifest驱动的正式producer接合 |

`L6DurationContract.java:16` 已有正确600/2400/600秒常量；现有统一10s sampler也已交付。这两项不补足arrival合同。不能仅增加运行时间、设置不存在的参数、替换为calibration入口或现场包装出新harness。

对tracked `backend` 与 `scripts`执行 `git grep`，`L6_FORMAL_CALIBRATION_MANIFEST|finalL6ArrivalRate|pacingInterval|noiseBands|healthySustainedRate` 的消费者命中为0；同时核对正式入口、调用路径和其完整源码，没有找到其他已交付入口。搜索命令、退出码、输出及6份带行号committed source快照已保存，绑定exact HEAD/blob/working SHA。缺少noise consumer是同一冻结合同接入缺口；即使事后离线比较资源，也不能补救错误的runtime pacing。

本轮**没有实际触发240上限**，不把内部合法的240/240/240合同称为动态capacity failure，也不从冻结rate重新计算并选择新budget。未评估新producer实施所需的具体预算值。没有证明production correctness失败。

## 未运行的资格项

计划仍为600s WARMUP + 2400s ACTIVE + 600s DRAIN，总3600s，必须未来另从T=0开始。本轮runtimeRunId、phase timestamps、actual durations、business/full-chain counts、Validation状态、backlog、Hikari timeout、noise窗口比较、leak判定均为null / NOT_MEASURED / NOT_ASSESSED。0个样本和0个checkpoint仅表示未启动，不填成mandatoryMissing=0或correctness=0。

Paper normal control、CRITICAL positive control、daily report、same-date regeneration均NOT_RUN；不复用calibration或readiness结果冒充本tier。PG实际版本/V51本轮未创建、未实测；主机Windows NT10.0.26200.0、Java21.0.9已只读确认。

未创建本run owned runtime，owned resources created=0、survivors=0，cleanup=`NONE_REQUIRED_NO_RUNTIME_CREATED`。没有清理其他任务资源。没有运行Full Maven、readiness、calibration、60min或180min。

本轮新增confirmed P0=0、P1=1（harness blocker）、P2=0、P3=0。生产正确性结果未验证；历史ordinary concurrent INSERT loser P2、wildcard-import P3及历史义务保持原处置，不清零或重新分类。

## 证据、历史保全与下一步

- [正式machine summary](L6_ACTIVE_STABILITY_60MIN_QUALIFICATION.json) 与 [未运行测量状态](L6_ACTIVE_STABILITY_60MIN_QUALIFICATION.metrics.json)。
- [本次preflight快照](runs/L6_A_60MIN_20260913_PREFLIGHT_03_d94f8512/preflight.json) 与 [metrics taxonomy](metrics/L6_A_60MIN_20260913_PREFLIGHT_03_d94f8512/summary.json)。
- 旧root preflight01的MD/JSON/metrics原字节保存在 `attempts/L6_A_60MIN_20260913_PREFLIGHT_01_23a7135a/`；其他历史attempt、calibration raw、接受报告和canonical manifest均不改写。
- Raw检查证据在 ignored `backend/nq-app/target/l6-a-60min-preflight-20260913-03-d94f8512/`；machine summary逐文件绑定path/size/SHA-256，包含CI、manifest entry/exit、起始inventory、committed source及search。

上一轮“READY_TO_RESTART”对可运行性的表述过早：calibration输入已齐备，但60min入口消费合同尚未证明。本轮明确区分这两层，不撤销有效calibration，也不以它替代正式harness接入。

本轮只更新qualification evidence；production/test/harness/sampler/config/manifest变化=0。stage=0、commit=NONE、push=NONE。完整起止核对另见本attempt的 `final-integrity.json`。

下一步需要另行授权补齐并交付正式harness的manifest/pacing/容量接入，再从T=0重新申请并执行L6-A；本轮不修、不继续原timer。**禁止启动180min。**
