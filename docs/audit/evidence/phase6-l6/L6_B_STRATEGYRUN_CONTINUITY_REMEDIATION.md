# L6-B StrategyRun restart continuity 整改

## 结论与边界

根因是 `CASE_3_RUNNER_CREATED_INVALID_OVERLAP / CASE_1_TRANSIENT_BUSY_EXPECTED`：runner 只检查 Order backlog，可能在 Order 已完成而 StrategyRun 尚等待下一次 canonical recovery projection 时准入同策略新请求；数据库正确返回 `strategy_run_active`，旧命令循环却因此退出。

本轮 production delta=0，V51、admission uniqueness、busy guard、订单/成交/账务状态机均未改。整改仅涉及 9 个 test 源码文件。L6-A ACCEPTED 保留；此前 L6-B FAIL 保留；本报告接受整改验证，不代表完整 L6-B/L6 接受。正式 180min 必须绑定整改提交的新 exact-head CI，从 T=0 另行执行。

## 历史 restart #2 时间线

历史候选 `b19445d47e1131ff0a5f4ce2a6cc807a7bdb05e9`，raw run `2c70b4b7-7139-49ee-ac1f-435363e0f96c`。

| 事件 | 原始事实 |
| --- | --- |
| 最后成功 slot675 | T+4804.4351813s 准入，T+4804.5399691s 返回；strategy=`l6-strategy-2`，request=`l6p-0675` |
| 重启前 active owner | `run-f5d0c2d1-111b-4501-9fa6-f3e8d211b3f4`，RUNNING，immutable dispatch work 存在，effective quantity=0.1 |
| 业务状态 | Order `ord-7c83f5b0-f50e-456e-b559-86080fd7cf7b` 为 ACCEPTED/version3；该单尚无 Trade/入账，必须继续恢复 |
| restart requested | T+4805.1357532s，2026-09-15T08:02:15.7388536Z |
| old generation stopped | PID5832，2026-09-15T08:02:15.955419Z |
| new generation started / ready | PID36112，start=08:02:16.545Z，ready=T+4810.9567211s |
| 恢复完成 | T+4835.8739217s，cursor1920→1928，backlog1→0；全部395个run均SUCCEEDED，395 Orders/395 Trades/1580 Ledger，duplicates0/orphans0 |
| 首个新 admission | slot679，T+4837.9446466s→4838.046299s；新run=`run-0e6815bc-fef2-4c8e-a14f-7310601c740a`，新order=`ord-9c17677a-19d0-4e74-b188-b58a7ba3d3a7`，同strategy2 |
| 首次后续 observer scan | NQ-0 T+4841.4819491s；NQ-1 T+4841.5861497s；各scan2个disabled schedule，未准入新run |
| 拒绝与退出 | slot680记录backpressure；下一个slot681在nq_admit_strategy_work收到SQLSTATE55000/精确strategy_run_active；命令main退出，controller报告child exited |
| 失败归档完成 | 2026-09-15T08:02:59.0879487Z；485/1080 samples、两次恢复、最终drain及第三次restart未执行 |

真正后续阻塞者是新建679，旧675已经SUCCEEDED。owner由恢复checkpoint全部395终态、之后唯一成功准入679、终态不可重开约束与同策略busy拒绝共同确定。历史没有失败瞬间完整DB snapshot，也没有逐次 first recovery tick/candidate IDs/project()/resume() 返回记录；不能声称这些历史观测已存在，亦不能断言679在失败瞬间的最后账务/投影状态。

历史归档 `runs/L6_B_180MIN_20260915_b19445d4/formal-01-raw.zip` SHA256=`f4a7748333c2553eebcf176add87d31c10ace0c922a1a8137bb1f18aa8a2675e` 保留。详细重建保存于本地整改目录的 `historical-timeline-complete.json`。这些本地原始证据不混入整改提交。

## 真实复现与状态 owner

旧runtime复现run=`1300c33b-cade-4f8f-9c22-b4c43195c4fe`：真实PG/独立NQ进程/原始675→679→681切点，kill旧代、同PG恢复675，再接纳679。测试用 `FOR NO KEY UPDATE` 仅暂缓679的projection，真实fill/reconcile已完成Order/Trade/Ledger，run仍RUNNING；681触发busy并导致旧候选新代退出。锁持有789ms；不是用statement timeout代替原失败，也没有SQL伪造业务状态。测试exit0表示捕获预期旧失败，不是qualification PASS。

canonical recovery按reserve candidates→project→若非终态则gateway.resume运行。Order终态到StrategyRun终态之间允许等待独立recovery tick；合法active必须继续等待，不能按PID死亡或TTL清除。诊断trace在整改回归真实记录了project/resume返回值与durable status：老代resume675返回ACCEPTED/idempotentHit=true且run仍RUNNING；新代project对675、679、683成功并投影SUCCEEDED。该trace仅允许显式diagnostic probe，正式参数不能启用。

## 整改语义

- `L6BAdmission` 在controller的数据库观察事务内读取Order backlog及active StrategyRun身份。存在任一未收敛事实即backpressure；超时复用既有 `min(600,max(20,2*ceil(orders/100)*10))` 秒恢复包络，并锁定 `RESTART_CONTINUITY_NOT_CONVERGED`。
- `L6BRuntime` 每次poll使用durable gate；重启后同时要求active为空、backlog归零、cursor足够推进、recovery tick已完成，再验证完整业务checkpoint。记录重启前owner和恢复后空active集合。
- `L6QualificationControls` 仅L6-B使用精确拒绝处理。仅PSQLException、SQLSTATE55000、server message精确等于strategy_run_active，且再次确认该request没有创建run，才能返回 `SKIPPED_BUSY / admissionRolledBack=true`。其他异常继续失败。
- `L6DeterministicPacer` 对该明确拒绝消耗当前slot并记录backpressure，不重发、不补发。interval、absolute deadline、duration、last successful dispatch间隔算法不变。
- `l6_b_analyzer.py` 验证before/recovered gzip SHA、原active owner身份与SUCCEEDED投影，并要求final admission barrier解除。资源噪声/稳定性标准不变。

PG/storage/hard budgets、calibration manifest、restart schedule、多代采样器、Venue delay均未改；同PG数据库事实继续跨代拥有业务authority。

## 验证

| 验证 | 实测结果 |
| --- | --- |
| 旧runtime回归 | 捕获strategy_run_active及generation continuity failure，原证据单独保留 |
| 整改回归a6ca45cc | 3 Orders/3 Trades/12 Ledger，duplicates0/orphans0；合法active仍拒绝、未fill实际等待20s后闭锁、同PG恢复、新代存活、重复observer/reconcile无重复副作用 |
| targeted-01 | Admission3 + continuity1 + FormalContract9，13 tests，failures/errors/skips=0 |
| targeted-02 | Admission4（含formal诊断拒绝）+ FormalContract9 + short probe1，14 tests，failures/errors/skips=0 |
| Python analyzer | 7/7 PASS，含owner错绑及未收敛负例 |
| 300秒短probe9255fa2e | 30 samples、5代、3次restart；恢复用时12.285/11.768/11.886s；21 Orders/21 Trades/84 Ledger，duplicates0/orphans0 |
| 原始证据重放 | 6 checkpoints、3个StrategyRun barrier、12个samePG/Venue边界全部通过 |
| drain/cleanup | backlog0、final barrier解除、owned survivors0、candidateUnchanged=true |
| 独立审查 | REVIEW_ONLY PASS；P0=0/P1=0；stage0；9源码start/end fingerprint相等：5b5045a661e4dc223a62a0bb46f9c66d94604f72297bd821e158e3bb9d854391 |

完整日志/XML/raw保存在本地 `runs/L6_B_STRATEGYRUN_REMEDIATION_20260915/`。targeted-01 raw ZIP SHA256=`95c1f8d2a6c7e5294945d33f32defd971a4443ea6f276784af4e00a94b6ff76c`；targeted-02 probe ZIP SHA256=`f25c15a3562d1e2d6db5801c5c1227a534dc864bc97a9382dc55cb6f74be2ab2`。

## 交付后资格门槛

精确提交仅9个test源码和本报告，排除用户原有L6-A dirty文件、历史formal failure和原始运行artifact。取得新提交全部exact-head CI成功后，重新执行当前内存60% gate、动态PG sizing及全部formal preflight；通过后直接运行完整180min。正式计时期间冻结候选/fixture/合同/标准，正式证据stage=0、commit=NONE、push=NONE。
