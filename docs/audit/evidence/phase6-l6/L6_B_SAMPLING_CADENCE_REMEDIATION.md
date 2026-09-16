# L6-B 绝对采样槽与计时守卫

## 历史故障事实

基线HEAD=`55ad67b16436ded33524c422bb24ef24d5ab38c0`，CI=`34979551797 /9 of 9 SUCCESS`。原run `583c7da1-65ba-4532-9ecd-0f2116f47d07` 保持 FAIL /NOT_ACCEPTED：761个有效样本，第761号记录在T+7615.421s启动，计划T+7610s，迟到5421ms。

前一槽index760在T+7600.009s启动，采集693ms，最迟约T+7600.703s已完成，因此不是上一槽仍在采集。旧sampler在任何collector执行前拒绝第761号记录，实际completion、controller GC/线程状态、目标时刻sampler是否runnable及actor锁等待均未记录。底层原因 **UNKNOWN**，历史样本是否本可在槽内完成也 **UNKNOWN**；新规则不能追认旧run。

最后已知actor为NQ0/g2/PID42400与NQ1/g1/PID35892，完整事实548/548/2192，duplicate/orphan0。前一有效样本560 Orders/backlog1、两侧pending0/tickFailed0、PG约313.64MiB、host可用约26.6GiB。command2250在T+7609.249340s发往NQ1/g1，6582.0156ms后SUCCESS，candidateCount100/newTrades1，前后pool active0/idle10/pending0。它与采样迟到重叠但因果关系UNKNOWN。完整actor GC、线程、队列、PG与OS前样本保存在本地取证目录的 `5421ms-reconstruction.json`。

## B专属采样合同

新入口为 `L6BSlotSampler`，复用原Collector/Stamp结构。A、storage/calibration与原 `L6ResourceSampler` 行为保持不变；生产delta0。

- `target(n)=formalStart+n*10s`，正式1080个逻辑槽、原180min不变。
- 槽为半开启动区间 `[target,nextTarget)`；真实采集开始必须在该槽内，纳秒completion **不晚于** nextTarget。恰好deadline完成可接受；多1ns为overrun。
- 2秒仅保留为jitter观测标签；无独立2秒启动/8秒采集SLA。`VALID_WITH_JITTER`是OBSERVATION，全部必测项与槽有效即可接受。
- 单次绝对target调度取代fixed-rate隐式补跑。启动时已经完整错过的槽只写 `MISSED_SLOT`，不调用collector、不填旧值。一次overrun结束后，其覆盖的已到期target也只记缺失，下一次真实采集等待未来target。
- `SLOT_OVERRUN`与孤立`MISSED_SLOT`保留 `PENDING_FINAL_EVALUATION`，继续安全且有效的后续观察；最终有任一无效槽即 `COMPLETED_NOT_ACCEPTED`，不得称1080个有效样本。
- 单个overrun可影响本槽和紧邻目标；第三个连续无效逻辑槽超出此孤立扰动包络，锁存 `SUSTAINED_SCHEDULER_DEGRADATION`。该规则在Java、proof contract与独立Python重放中逐值绑定，不能把真实持续退化降为warning。
- mandatory collector失败、陈旧token/PID、预算/ownership/安全错误立即锁存阻断。其余安全只读诊断在有界预算内尽量保留，主业务循环可观察到失败；证据写入失败亦阻断。
- 原始记录保留scheduled/actual start/completion/deadline/slack、scheduler attempt、sampler monitor及actor lifecycle锁等待；各collector保留起止/耗时/error。每次采样保留controller GC/heap/线程状态、锁拥有者及有界栈，加上原actor GC/线程/pool/队列、PG/OS指标。事后快照不是暂停时刻的线程轨迹；仍无法证明的底层原因继续UNKNOWN。

采集的HTTP、shell和SQL共用从真实采集开始派生的一个10秒**诊断执行预算**，每项读取剩余额度，无重试。它与槽截止分开：晚5.421秒后采集5秒可以保留完整诊断并记录overrun；真正操作超时或必测项缺失仍阻断。HTTP连接上限也由同一10秒包络派生。SQL秒级query timeout向上取整，PostgreSQL毫秒statement timeout及下一操作的剩余额度检查共同约束；不是新的生产查询SLA。原shell进程强制退出2秒、sampler shutdown10+5秒保留owned-resource清理语义。

## 与库存安全和重启边界的协调

`L6BProjectionGuard`不再把孤立gap重新升级为cadence abort。scheduled目标必须10秒对齐且严格前进，库存不得下降；有gap/overrun即重置受影响的61点增长窗口，不能把稀疏样本乘固定10秒冒充完整600秒。原冻结rate不降低，PG容量、reserve、restartStorage和need模型不变；仍用scheduled时刻计算保守剩余需求，且采集期间增长不被漏算。

restart目标2400/4800/7200秒不变；45秒计划窗口保留为实验覆盖条件，超界落盘并继续有效观察，最终不接受该次资格。原85秒stop-to-ready复合assert遗漏额外L6_CLOCK应答，移除重复断言并记录为operational expectation。各阶段原10秒stop、75秒READY、75秒clock协议bound逐项保持，分别记录stop/READY/clock完成边界；这不是扩大任何单项执行bound。原业务恢复总deadline及其完成后检查保持不变。

## 正式可达路径 timing guard inventory

“derived”指数字能否从本合同或既有业务/协议包络推导；未推导的操作超时不能冒称业务SLA。

| Guard / owner | 当前值与用途 | 分类 / authority / 处置 |
| --- | --- | --- |
| B periodic slot | 10s；1080 slots | EXPERIMENT_VALIDITY_BOUND；冻结180min与必测 cadence，保持 |
| 旧start/collection拆分 | 2s+8s | ARBITRARY_LEGACY；无独立SLA，B入口已移除，2s仅jitter标签 |
| fixed-rate调度 | 隐式catch-up | QUALIFICATION_INTERNAL；B改绝对单次调度与显式miss |
| slot completion | completion<=nextTarget | EXPERIMENT_VALIDITY_BOUND；从完整10s槽推导，isolated violation继续、最终不接受 |
| sustained gap | 连续3个无效逻辑槽 | EXPERIMENT_VALIDITY_BOUND；超出单次overrun及相邻槽包络，取证后锁存阻断 |
| B HTTP collector | connect<=10s，request<=剩余诊断额度 | QUALIFICATION_INTERNAL；由一个槽长派生，替换旧1s/2s；不可用必测证据仍失败 |
| B collector SQL/shell | 共享10s诊断额度，逐次剩余时间 | QUALIFICATION_INTERNAL；替换采集旧2s及B采集helper5s；真实失败不吞掉 |
| 其他controller SQL | PG statement2s、helper query5s | QUALIFICATION_INTERNAL；有界控制事实查询，非采样SLA；不属采集槽拆分，保持；失败表示控制事实不可读 |
| Venue control/facts HTTP | connect2s、request5s | QUALIFICATION_INTERNAL；有界控制协议，含副作用不盲重试，保持；不是reconcile deadline |
| Child READY/response | 各75s | QUALIFICATION_INTERNAL；原B0协议，超时身份不可信并闭锁；保持 |
| child command Future | 45s | QUALIFICATION_INTERNAL；原L6执行bound、45<75；真实hang仍阻断，保持 |
| reconcile delay | 完成后5s | OPERATIONAL_EXPECTATION；生产fixed-delay语义，无overlap/catch-up，保持 |
| StrategyRun/backlog及restart恢复 | min(600,max(20,2*ceil(orders/100)*10))秒 | BUSINESS_CORRECTNESS_BOUND；两次fair-cursor绕行包络，原deadline不扩大 |
| planned restart lateness | 45s窗口 | EXPERIMENT_VALIDITY_BOUND；原runner故障覆盖窗口，未声称生产bound；从立即abort改为完整取证后不接受 |
| stop-to-ready复合85s | 历史期望 | ARBITRARY_LEGACY重复合计；遗漏clock步骤，已去除阻断用途；原单项bound保持 |
| graceful stop/forced kill | 各10s | SAFETY_BOUND；owned进程退出，保持 |
| child executor shutdown | 15s | SAFETY_BOUND；原executor关闭等待，保持 |
| sampler shutdown | 10s+5s | SAFETY_BOUND；一个诊断预算后有界强制终止，survivor失败；保持 |
| resource command强制退出 | 2s | SAFETY_BOUND；只回收本次创建的进程，保持 |
| B0 helper/owned docker cleanup | 45s执行/5s清理 | QUALIFICATION_INTERNAL/SAFETY_BOUND；原外部命令与进程清理包络，保持 |
| PG readiness | 40s | QUALIFICATION_INTERNAL；T0前启动探测，不是late blocker，保持 |
| checkpoint尝试 | 完成后60s | OPERATIONAL_EXPECTATION；不是执行SLA；StoragePending不得伪造完整链，保持 |
| ACTIVE业务窗口 | 每600s有新完整链及timer进展 | EXPERIMENT_VALIDITY_BOUND；canonical窗口完整性，final oracle保持 |
| phase/pacing/drain cutoff | 600/9600/600s；原manifest pacing | BUSINESS_CORRECTNESS_BOUND/EXPERIMENT_VALIDITY_BOUND；绝对时间及activeEnd前准入保持 |
| 容量风险提前drain | 20s | SAFETY_BOUND；仅容量stop触发，正常600s drain不变 |
| 同代资源分析 | warmup600s、>=3个600s窗口、尾部590s | EXPERIMENT_VALIDITY_BOUND；自然GC/handles可比性，最终判定，保持 |
| A/storage transition/boundary | 2s/2s、await12s、boundary collect8s | B正式不可达；不改A/storage authority |
| 旧checkpoint等待 | 120s | B传入capacityStop后直接StoragePending，旧等待分支不可达；不改 |

本次已处理的ARBITRARY_LEGACY是采样2s/8s拆分和遗漏clock的85s复合阻断；显式操作协议timeout仍是有界执行合同，不能保证外部环境永远不触发，也不因此承诺未来必然PASS。

## 验证与证据边界

目标验证包含5421ms槽内完成、5421ms跨槽、完整漏槽、持续退化、deadline精确边界、陈旧身份、collector failure、无补拍，以及projection缺槽/库存/容量反例和原phase boundary。真实probe在独立PG/Venue/NQ进程中注入一次5421ms启动jitter及一次12500ms停顿；正式入口禁止注入。probe只能证明能力，不能代替180min资格。

本地测试、probe、独立审查及交付记录保存在 `docs/audit/evidence/phase6-l6/runs/L6_B_SAMPLING_CADENCE_20260916/`。这些尚未提交的运行证据用明确本地路径表示。正式重跑需绑定本轮最终commit与新的9/9 CI；正式证据stage0/commitNONE/pushNONE，所有历史FAIL及L6-A ACCEPTED保持不变。
