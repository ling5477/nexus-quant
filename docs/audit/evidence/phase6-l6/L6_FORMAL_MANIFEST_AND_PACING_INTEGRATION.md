# L6 Formal Manifest and Deterministic Pacing Integration

**LOCAL_INTEGRATION_PASS / PRECISE_DELIVERY_PENDING / NEW_HEAD_CI_PENDING**。短时集成证明通过；最终接受仍待精确交付和新HEAD CI，不能启动60min/180min。

## 身份与只读输入

Baseline=`d94f8512108fd7f8e9cbdeee14cfc8b064f2d5d8`，branch=`audit/post-gatey-agent-baseline`，入口upstream相同、stage=0。原始Git可见文件path/size/SHA-256 inventory保存在本任务target目录；原有evidence未覆盖。

Canonical manifest=`L6_FORMAL_CALIBRATION_MANIFEST.json`，原字节SHA-256=`922253c8934e32b7626ced2f9e42407a2a4313c665256b89c483e847ce6abaa6`，前后相同。healthy rate、25% rate、interval和noiseBands只读，不重新计算。Loader保留schema1原有`formalCalibrationAccepted=true`及接受decision token语义，没有为增加status字段改写历史manifest。source calibration HEAD/tree/runId/CI、rate有效性、单位/纳秒表达、接受的correctness、noise字段、10s采样完整性均fail closed；缺失/坏JSON/重复键/尾部JSON/不支持schema/非接受状态等在PG/Venue/NQ/timer之前拒绝。

输入记录path/SHA/semantic fingerprint，结束检查原字节SHA。子进程读取run-local parameters并验证canonical manifest SHA，不新增环境键、不扩大B0Fixture环境白名单。

## 架构与不变量

正式`L6ActiveStabilityTest`非diagnostic入口转入`L6FormalManifest.start`，通过preflight后才创建`L6FormalRuntime`。正式与短smoke共用loader、pacer、capacity、sampler、canonical trigger和oracle；短模式仅将阶段缩短到20/50/30秒，正式仍600/2400/600秒。

Pacer使用monotonic `start + n * 7117650000ns`绝对slot。每slot最多一个新的MANUAL strategy trigger身份，复用原StrategyManualTriggerService→Risk/Execution→ordinary Order；不直接写入Order/Trade/Ledger。actor按slot轮换，固定request identity，重复命令fail closed，不盲重试。

真实分钟cron配置保留，fixture关闭schedule admission，真实scanner仍独立运行并验证triggeredCount=0；V51 recovery、Validation timer、对账与Paper controls继续运行。scheduler扫描结果单独导出，不能拿它作为workload到达率。此范围证明scanner/timer进度，不声称cron仍能自主创建订单。

slot记录slotIndex/scheduledElapsed/actualElapsed/decision/driftMillis/logicalOrderId/completedElapsed。过期slot记PAUSED_BACKPRESSURE，无补发债务；若相邻实际dispatch间隔不足，跳过当前slot，不延后其绝对deadline。多次服务耗时不累积到后续deadline。最大瞬时producer rate按实际dispatch间隔导出，并核对不超过冻结authority。

WARMUP/ACTIVE启用producer，DRAIN禁用。父子JVM通过一次往返取得保守单调时钟映射，子进程在入口及DB重复身份检查后校验phase deadline；canonical调用若跨phase边界，父进程保存STOPPED及迟到Order身份并失败，不以迟到的drain基数掩盖。该机制是截止保护和越界fail-closed检测，不声称能强行撤销进行中的canonical事务。

正式最大可能订单数只根据可投喂duration/interval/slot0计算：60min为422，run budget=Venue capacity=422≤3000。L5默认300与calibration600保持。没有从healthy rate重新计算25%或改noise band。原sampler、calibration analyzer算法和production代码变化均为0。

## 验证与审查

- 首轮定向Java16 tests通过；独立review发现累计deadline漂移及DRAIN边界漏计，两项均为harness P1。
- 修订后Java18 tests通过，0 failure/error/skip；新增连续100次各100ms服务耗时的绝对grid证明，以及late admission不能进入成功drain的反例。
- 原calibration analyzer/oracle离线回归3 tests通过，覆盖重复账务、partial chain、missing/stale采样拒绝；没有重新运行calibration或改原raw。
- 真正独立REVIEW_ONLY复核修订代码候选可接受，未关闭P0/P1=0；复核前后候选fingerprint相同、stage=0。该结论仅属代码层，不替代smoke或新HEAD CI。

首次短smoke run=`f2a895a6-0202-42d1-844f-df4ae648ccf8`。Maven入口在fixture.initialize被`BLOCKED / UNSAFE_TEST_FIXTURE_TARGET`拒绝，原因是初版加入了不在B0环境白名单中的manifest元数据键。PG/Venue已创建并清理；NQ/timer/workload未启动，owned survivors=0。完整失败原文、XML与raw保留。代码已改用run-local parameters，不扩大安全allowlist。

用户明确授权额外一次100秒重试。重试前10个源码SHA全部匹配独立复核结束指纹，fixture修复已在复核候选内；启动后实现变化=0。首次失败保留为SETUP/INFRASTRUCTURE_FAILURE，未追认为成功。

本次run=`fa631e1a-71b4-4223-9fb5-44a0932e01c8`，FORMAL_L6_A短模式20/50/30秒，实际总计100.0063122秒，Maven退出0。6个EMITTED、4个PAUSED_BACKPRESSURE、5个DRAIN SKIPPED_PHASE；slot绝对deadline均为index×7.11765秒。实际最小dispatch间隔7.1215073秒，最大瞬时producer rate=0.14041971142822532 orders/s≤冻结0.14049580971247533，无补发。跳过slot意味着实际吞吐可低于冻结上限，不声称所有相邻emitted均相隔一个interval。

5个checkpoint、6条完整业务链（6 Order / 6 Trade / 24 Ledger / Position=0.6），重复mutation/accounting=0；40次真实scanner扫描、triggered=0，timer/recovery及Validation进度可见。DRAIN新workload=0，10/10资源采样MEASURED，mandatory missing=0，最终backlog=0、oldestCandidateAge=NONE/null，owned NQ/Venue/controller及容器均已消失。manifest入口/出口SHA与semantic fingerprint一致。

[运行证明](runs/fa631e1a-71b4-4223-9fb5-44a0932e01c8/proof.json) / [原始pacing](runs/fa631e1a-71b4-4223-9fb5-44a0932e01c8/pacing.ndjson) / [核对结果](runs/fa631e1a-71b4-4223-9fb5-44a0932e01c8/verification.json)。短smoke不替代正式60min稳定性资格。

## 证据与交付

[Machine summary](L6_FORMAL_MANIFEST_AND_PACING_INTEGRATION.json)绑定本轮源文件和raw SHA。完整日志/XML位于`backend/nq-app/target/l6-formal-manifest-pacing-integration/`，失败run位于`backend/nq-app/target/l6-formal/f2a895a6-0202-42d1-844f-df4ae648ccf8/`。所有历史evidence与canonical manifest保留原字节。

候选delivery allowlist仅包含test tooling、canonical calibration manifest、calibration接受证据、本任务及必要L6证据。本报告记录交付前候选：commit/push及新exact-head CI待执行，最终交付身份另行记录。旧34753536151仅属baseline，不能作为新候选CI。Integration/L6-A/L6仍NOT_ACCEPTED；60min/180min均NOT_RUN。

历史链接闭包：两份既有attempt报告有6个归档相对链接失效。新增5份逐字节相同的原始证据副本及1个计划导航文件，既有67份L6证据SHA全部不变；未修改历史报告、实现或检查脚本。

交付字节保真：新增仅覆盖本次allowlist证据路径的`.gitattributes -text`，防止Git换行转换改变冻结manifest和raw SHA。原始Maven日志按原字节封装为ZIP，成员hash可核对；本地原日志保留。该变更仅属证据封装，不改变运行实现。
