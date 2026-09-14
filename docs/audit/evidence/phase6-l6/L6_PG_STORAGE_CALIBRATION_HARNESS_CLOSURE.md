# PG storage calibration harness closure

当前状态：`LOCAL_PASS / L6_PG_STORAGE_CALIBRATION_HARNESS_READY / EXACT_HEAD_CI_PENDING`。用户追加授权的一次100秒 smoke 已通过，修复候选源码指纹全程未变。下文保留首次失败、修复与授权前阻断历史；本次精确交付仍需绑定新 HEAD 的 CI。

## 起点与范围

branch=`audit/post-gatey-agent-baseline`，HEAD/upstream=`d9ff58f489c5514e55758459198c2c4acb914bca`，baseline CI=`34799693548 / 9 of 9 SUCCESS`。entry stage=0。已有未提交 evidence、只读诊断器和历史失败记录的 path/size/SHA 已保存于 [entry inventory](runs/L6_PG_STORAGE_HARNESS_CLOSURE_20260914/entry-inventory.json)，本轮未覆盖它们。

本轮实现限于 `backend/nq-app/src/test/**`。production、Flyway、生产配置、manifest、pacer 数值、.github、AGENTS、Skills、frontend、research 均未改。B0Processes 仅增加显式结果等待期限入口，原默认等待路径与 256MiB tmpfs 配置保持；未定义正式 L6 tmpfs 容量、正式 60% memory preflight 或正式 60min projection guard。

## 已实现合同

`L6StorageCalibrationTest` 是显式 `L6_PG_STORAGE_CALIBRATION` 入口。正式 duration 固定 300/1200/600 秒，smoke 明确选择 20/50/30 秒；不由 duration 推断 mode。原 FORMAL_L6_A 与旧 rate calibration 合同不变。

storage 模式直接读取既有 `L6FormalManifest` 与 committed JSON，复用 `L6DeterministicPacer` 的 `7117650000ns` interval、绝对 slot、无 catch-up burst 和 phase stop；未创建第二个 pacer。新的 run-manifest 分支仅接受显式 storage mode 与布尔 shortSmoke，核对原 manifest SHA。actor、Venue、checkpoint evidence 都保留显式 storage mode。

统一 10 秒 sampler 的 storage postgres collector 使用同一 repeatable-read DB snapshot 导出 orders、terminalOrders、fills、trades、TradeExecuted、ledgerEntries、fullChainCompleted/fullChainOrderIds，连同 tmpfs capacity/used/free/ratio、DB/WAL 与既有 allocated components、auditRows/eventRows/backlog。`oldestCandidateAgeMillis` 和 NONE 状态仍由同一 stamp 下 nq0/nq1 的 `candidateAge` 对象记录，scheduler/reconciliation 进度保留于既有 actor 字段。未知值继续 UNAVAILABLE，禁止回填零。

完整链 counter 读取 owned Venue fill 与 DB 关联：Order、Trade、TradeExecuted、四条 Ledger、ledger event fanout、Position/Snapshot；验证账户/环境/symbol、外部 fill identity、金额与投影。Position/Snapshot 是账户聚合，按实际已入账链重建后才计入集合。Order only、Trade only、缺 Event/Ledger/Position/Snapshot 不增加已完成数，replay 不累加，duplicate identity 拒绝。末端 counter 必须与原 canonical business oracle 的完整订单数一致。

边界观测与周期观测使用同一 collector 和串行同步：`sampleType=PERIODIC|PHASE_BOUNDARY`。WARMUP_END/MEASUREMENT_START 共用一个明确标记的 observation，MEASUREMENT_END/DRAIN_START 同理，另有 DRAIN_END；每条 `boundaryNames` 列出对应语义。同一 observation 只计一次，周期 sampleCount 与 boundarySampleCount 分开。边界记录目标时间、实际 monotonic 时间和 start lag，超过既有 2 秒容差拒绝，不把延迟样本伪称准点。

`L6StorageCapacityGuard` 是本次校准的局部停止线，余量为 `max(32MiB, 3 × 最大已观察相邻区间折算10秒增长)`。32MiB 对应两个 16MiB WAL 段的操作余量，不是正式容量值或正式容量充分性证明。风险锁存后禁止新的 producer command；free 回升也不恢复。drain 的新命令控制期限为 20 秒；在途单次 storage command 最多 5 秒，末端 collector 最多既有 8 秒，cleanup 另计，不能将此描述为全部资源必定在 20 秒内退出。

风险进入 DRAIN 并保存原因/真实订单基线；紧急退出也保留零时长 DRAIN_START/END，不能用未初始化的计数制造 delta。期间在安全期限内继续原 scheduler/reconciliation，结束保持 `BLOCKED / STORAGE_CALIBRATION_CAPACITY_AT_RISK`。不扩容、不删除数据、不降速、不重置计数。storage checkpoint 不进入旧 120 秒收敛等待，未完成状态由既有周期继续推进。

## 验证与唯一 smoke 的失败

修复后 32 个 Java 目标测试 PASS，fail/error/skip=0：storage contract 7、storage observation 3、resource sampler 6、formal contract 9、calibration contract 5、process output 2。Python counter 5 个测试 PASS；另只读复核旧失败 run 的 107 个真实完整 checkpoint，counter 与原证据的完整链数 1→256 相符。旧失败 run 不因此获得 PASS。

已执行一次授权的 100 秒 smoke，run=`681bd1d1-ec7b-4af0-9939-21590c74810c`。实际在约 20.473 秒的第一边界失败：边界 token 使用 `UUID:boundary:1`，被既有只接受 `UUID:digits` 的 metrics endpoint 返回 HTTP 400；未放宽 endpoint 协议。随后 close 抛同一个异常实例导致 Self-suppression，原始采样失败仍在 raw evidence 中。

此前三个 periodic samples 为 MEASURED，fullChainCompleted=0/1/2、tmpfs used=48910336/49451008/49434624 bytes；第一 boundary 为 UNAVAILABLE。没有完成 measurement 或 drain，不将此称为正常 smoke PASS。两个 NQ、Venue/controller 与 owned PG 已退出；[cleanup](runs/L6_PG_STORAGE_HARNESS_CLOSURE_20260914/failed-smoke-cleanup.json) 保留核验。

后续修复将 boundary token 限定为既有协议内不与 periodic 0..499 重叠的 501..512，sampleIndex/type/name 仍独立。close 包装异常以保留原始 cause。新增真实 loopback endpoint 测试直接执行 periodic/boundary token，以及 try-with-resources 失败路径测试，均通过。未运行第二次 storage smoke。

用户明确要求“只执行一次短时 storage-calibration-mode smoke”，因此额外一次 smoke 已单独请求授权。该限制来自本次用户任务，不是工具自动审批或 Skill 限制；在取得授权前不启动新的 runtime。

## 证据与接受边界

[失败候选](runs/L6_PG_STORAGE_HARNESS_CLOSURE_20260914/candidate.json) 与 [修复候选](runs/L6_PG_STORAGE_HARNESS_CLOSURE_20260914/candidate-after-smoke-fix.json) 分别保留 15 文件 LF-canonical SHA，不覆盖候选历史。[原始验证包](runs/L6_PG_STORAGE_HARNESS_CLOSURE_20260914/failed-smoke-and-validation.zip) 包含完整失败 raw run、日志与 XML；[summary](runs/L6_PG_STORAGE_HARNESS_CLOSURE_20260914/summary-before-retry.json) 绑定各 raw file 哈希及验证结果。

独立审查发现并促成了长等待、生产锁覆盖外部等待、紧急 drain 基线、Event/Position 关联缺口的修复。失败候选审查前后指纹相等，stage=0；由于 smoke 失败，审查结论为 BLOCKED。修复候选仍需要成功 smoke 才能交付。

产品新增 P0/P1=0/0；harness smoke 接受 P1 保持 OPEN。该授权前检查点 stage=0、commit=NONE、push=NONE，新 exact-head CI=NOT_RUN。35min calibration、60min、180min 均未运行。L6-A/L6 NOT_ACCEPTED，正式容量 NOT_FROZEN。


## 追加授权的最终候选 smoke

用户授权 `ONE_ADDITIONAL_100S_STORAGE_CALIBRATION_SMOKE / retry count=1`，并将第一次失败分类为 `SETUP/HARNESS_PROTOCOL_FAILURE`。原失败 raw 与失败候选指纹不改；没有第三次 smoke、额外 readiness 或 review。

追加 run=`89f91f58-c269-4ce6-8024-e412ca2154d9`：`STORAGE_CALIBRATION_SMOKE_PASS`。10 个 periodic samples、3 个 PHASE_BOUNDARY observations，共13个唯一token；边界名称覆盖 warmup-end、measurement-start、measurement-end、drain-start、drain-end，无重复计数。最大周期起始lag=15ms、边界起始lag=468ms、collection=570ms。

所有采样都有 TradeExecuted/fullChain 与 tmpfs/DB/WAL 分项；tmpfs capacity=268435456 bytes，used+free=capacity。fullChainCompleted=6；capacity-risk=false；DRAIN new workload=0；final backlog=0、oldestCandidateAge=NONE；最终canonical oracle duplicates=0、orphans=0；cleanup PASS、owned survivors=0；manifest SHA不变。

绝对阶段时钟为 T0→20s→70s→100s。首次同步采样位于warmup内；raw driver phase记录在首次采样完成后约574ms开始，不能将该driver记录的19.43秒误称为完整warmup墙时；实际边界采样时间为20.468s、70.462s、100.445s，保留真实延迟，不重写为理想时间。

[重试接受明细](runs/L6_PG_STORAGE_HARNESS_CLOSURE_20260914/smoke-retry-acceptance.json) / [原始run与验证包](runs/L6_PG_STORAGE_HARNESS_CLOSURE_20260914/authorized-smoke-retry.zip) / [raw hashes](runs/L6_PG_STORAGE_HARNESS_CLOSURE_20260914/authorized-smoke-raw-manifest.json)。32个Java目标测试与5个Python测试的有效证据复用；修复后独立增量复核已通过，记录见 [review](runs/L6_PG_STORAGE_HARNESS_CLOSURE_20260914/independent-review-after-fix.json)。

本轮harness smoke接受P1已由修复候选的完整成功smoke关闭，产品新增P0/P1=0/0。production delta=0，pacing与manifest delta=0，L5/default与storage tmpfs=256MiB不变。35分钟正式storage calibration仍NOT_RUN，L6-A/L6仍NOT_ACCEPTED；容量未冻结、正式60% preflight与正式projection guard未实现。

满足精确暂存/commit/push前置条件；新exact-head CI成功后才可声明DELIVERED。下一任务仅为正式storage calibration，本轮不启动该运行。
