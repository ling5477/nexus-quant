# L6 Minimal Calibration Harness Closure

**LOCAL_PASS / L6_MINIMAL_CALIBRATION_HARNESS_READY / PENDING_EXACT_HEAD_CI**。本记录绑定precommit验证候选，交付后的新commit/CI单独记录；不复用旧CI作为新实现证明。

Previous blocker=`CALIBRATION_HARNESS_CHANGE_REQUIRED`；本轮在test/tooling范围补齐。历史 `L6_FORMAL_CALIBRATION_MANIFEST_MISSING`、`L6_FORMAL_CALIBRATION_EVIDENCE_INSUFFICIENT`仍是原时点事实。正式calibration仍NOT_RUN，没有canonical manifest，L6仍NOT_ACCEPTED。

## 身份、范围与架构复用

起始branch=`audit/post-gatey-agent-baseline`，HEAD/upstream=`1208abd904c318b832af152c9c52f8a5f4b46df3`，tree=`cf989b24331f38b9a3618b1fd58de68d3a26c302`，旧CI=`34739867074 / 9 of 9 SUCCESS`。先保存3970个Git可见文件及11份既有未提交evidence的path/size/SHA-256；这11份历史evidence全部原字节保留，不纳入本次stage。

Production/config/Flyway/.github/frontend/research/AGENTS/Skills delta=0。只修改或新增16个test/tooling文件及本报告、机器汇总；SELF_REVIEWED / NO_INDEPENDENT_REVIEW。最终候选原字节hash在机器汇总；technical-inputs-before-smoke.json保留实际smoke输入，smoke前后全部technical inputs一致。交付检查随后只删除Python单元测试文件末尾多余空行，运行实现不变；机器汇总单列测试时与最终SHA-256。

| 复用项 | 最小变化 |
| --- | --- |
| B0Processes / B0Fixture / L6NqProcessMain / L5VenueProcessMain | 沿用owned PG16/V51、Synthetic Venue、2个真实Spring actor、loopback和环境白名单；无第二套load framework |
| L6RuntimeResources / L6ResourceSampler | 两模式经同一个sampler工厂使用同一collector、单位、availability、token和10s绝对采样时钟；新增schedule接口只提供时长及phase标签 |
| L6BusinessCheckpoint / l6_oracle.py | 提取原正式checkpoint，不删除状态迁移/唯一性/账务/Position/Snapshot/orphan断言；显式CALIBRATION可用600订单预算，formal原240预算不变 |
| L6GcEvidence | JVM GC count/time与lastGcInfo的真实heapUsedAfterGc；同时采jvmUptime，重复事件按PID/collector/id识别；不强制GC |
| L6CalibrationEvidence / offline analyzer | admission/completion观测时间、暂停区间、eligible分母与完整原始序列；只产生候选/观测，不自动冻结manifest或noise band |

## Mode、timing与输入

独立入口 `L6CalibrationTest`，显式 `mode=CALIBRATION`；正式calibration固定300s warmup+600s measurement，cleanup/drain额外有界且不计测量分母。原formal 600/2400/600不变。只有显式 `nq.l6.calibration.smoke=true` 使用10/40秒测试时长，smoke无法输出healthyRateCandidate、formalCalibrationAccepted或L6Accepted。

受控输入为两条合法strategy每5秒到期一次，两actor并发scan真实服务；每5秒一轮、每actor一条pending、600总订单预算。这个输入形状不是最大吞吐测试，不是最终L6 rate，也没有硬编码最终2/min或1/s。RiskGate、正常reconciliation与timers保持。发现可执行积压暂停scanner投喂，保持FILL/reconcile；checkpoint最多120秒收敛，超限失败。拒绝fault/Kill组合参数，无故障注入、人工丢包、重启、真实provider、credentials或LIVE。

## Rate raw evidence

progress.ndjson和逐checkpoint完整facts/venue提供Order、fill、Trade、TradeExecuted、Ledger、Position/Snapshot与StrategyRun/work对应关系。只有共享oracle验证成功后的完整Order身份集合才记completion。每identity保留首次admittedObservedElapsedMillis及completedObservedElapsedMillis；它们是controller对已持久化事实的保守观测上界，不是伪造DB提交时刻。

候选分子只含measurement内admission且在measurement结束前观测全链完成的identity；warmup/carry-out/暂停内completion排除。分母为measurement窗口扣除BOUNDARY与显式BACKPRESSURE区间的并集；setup、warmup、cleanup均不进入。没有用created orders/whole-run elapsed替代。raw同时保留未扣除墙钟区间与全部pause，下一calibration任务可评价该公式及观测偏差；本轮不接受healthy rate或25%结果。

缺失/违例采样、未完成窗口、零全链完成、暂停占比达到一半或更多均不给候选；连续3次增大的backlog样本拒绝，600总量及120s收敛预算另行兜底。正常调度paced等待不是背压；消费者未收敛期间的观测等待另存BACKPRESSURE区间，避免静默把停产算成steady时间。

## Noise raw evidence

相同collector保留heap used/committed/max、GC、threads、队列、Hikari、PG连接/idle-in-transaction、handles/FD、candidate age、scheduler/cursor、audit/event、log/temp、owned身份的统一10s series。新增order数可将log/audit/event绝对值与delta按新增order数归一；无新order或首拍时normalized delta=null，不能把未知值填0。

离线输出calibration-analysis.json按phase/source/field列sampleCount/min/max/maxMinusMin和原始sampleIndex；所有frozenNoiseBand=false。GC单列真实post-collection用量及事件id/uptime；observedPhase不是GC发生phase，需结合原jvmUptime解读，不能把startup GC当measurement低谷。没有GC时为NO_GC_OBSERVED，不造低谷或接受资源稳定性。

## Tests与唯一短时smoke

最终定向：16 Java tests、0 failures/errors/skips，包含3个Python离线测试（由JUnit调用）。覆盖可控时钟5/10与10/40/10、同采样实现90/360拍、窗口边界、暂停扣除、零全链、mostly-paused、backlog runaway、missing/stale/gap、GC低谷导出、handles/temp/log series及normalized零分母。共享oracle的重复Trade/TradeExecuted/Ledger与缺Ledger反例全部拒绝；测试fixture是已有两订单synthetic checkpoint副本，仅作为单元测试输入。

一次真实calibration-mode smoke：runId=`9e1feeca-f3d0-4eac-8e92-f4aa99f1e38b`；Windows 11 / JDK21；owned PostgreSQL16.15/V51；2 NQ+1 Venue+1 controller。10s warmup+40s measurement；实际phase分别10.010s与39.989s，绝对测量窗口[10000,50000)ms，边界11ms明确扣除。5个完整10秒样本（warmup1、measurement4），7 collectors；最大启动延迟14ms、最大采集380ms，mandatory missing=0、cadence violations=0。

20 Order/fill/Trade/TradeExecuted、80 Ledger、20 SUCCEEDED StrategyRun/work；11 checkpoints共享oracle全部通过，Position/Snapshot BTC=2。测量段eligible full chains=16，eligible seconds=33.042，paused=6.958s，其中backpressure=6.947s。**smoke healthyRateCandidate=null、l6FinalArrivalRate=null**，不据此冻结结果。自然GC事件识别4个，保留原样，不宣称15分钟noise band充分。

Paper正常控制、CRITICAL正例、daily report同日幂等控制通过；Validation沿用精确expected gap合同，未放宽SHADOW_RUNS/freshness。duplicate mutation/accounting/orphan/同态transition均0，最终actionable=0、candidate age=NONE、Hikari pending=0、idle-in-transaction=0。cleanup drain=302ms，不计测量；owned actors/Venue/PG及端口清理通过，owned survivors=0。没有重跑readiness或正式15/60/180min。

初次离线测试路径定位错误和首次定向的反例异常类型误写均为本轮test错误，已修正；targeted-01.log保留失败，targeted-02.log及XML保留最终成功。没有失败动态smoke，没有生产修复或原始证据改写。stage-assets：1939 scanned / 173 exceptions / errors=0。

## 命令、交付与下一动作

```powershell
mvn -o -f backend/pom.xml -pl nq-app -am test '-Dtest=L6CalibrationContractTest,L6ResourceSamplerTest,L6ReadinessContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xmx512m'
mvn -o -f backend/pom.xml -pl nq-app -am test '-Dtest=L6CalibrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6.calibration=true' '-Dnq.l6.calibration.smoke=true' '-DargLine=-Xmx512m'
```

精确提交本轮16个test/tooling文件+2份closure evidence，旧11份未提交evidence不stage。当前是LOCAL_PASS，新的exact-head CI必须在commit/push之后验证，不能使用旧34739867074替代。提交后结果单独记录delivery acceptance，避免把先于CI的事实写成CI已通过。

P0=0、P1=0；本轮无未处置新增P2/P3。原P2/P3及历史证据不足结论保持，不宣称全仓库问题清零。Raw/tests/inventory/SHA-256索引见机器汇总。

新HEAD CI GREEN后唯一下一任务：`NQ-GATEAUDIT-PHASE6-L6-15MIN-NO-FAULT-CALIBRATION`。下一任务才在exact-head上执行 `-Dnq.l6.calibration=true -Dnq.l6.calibration.smoke=false` 的300/600秒运行，判断真实raw是否足够并生成manifest；本任务不运行该命令。只有manifest接受后才可进入L6-A 10/40/10。
