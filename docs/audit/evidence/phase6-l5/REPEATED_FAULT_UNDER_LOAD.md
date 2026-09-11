# Phase6 L5 repeated fault under load：资格阻断

**BLOCKED / L5_REPEATED_FAULT_HARNESS_GAP / NOT_ACCEPTED**。

Task：`NQ-GATEAUDIT-PHASE6-L5-REPEATED-FAULT-UNDER-LOAD`。Classification：`HIGH_RISK / SCALE_QUALIFICATION / REPEATED_FAULT / MULTI_PROCESS_RECOVERY / NQ-only`。

F1完成3个独立run；F2有2个完整验证run，Trade提交后死亡的最终新run未命中所选屏障；F3尚无最终候选下的3次资格。不能接受本批或进入aggregate/L6。新增已证实生产P0=0、P1=0，不把未完成范围记成正确性PASS。

## 身份与范围

- branch=`audit/post-gatey-agent-baseline`；starting/ending HEAD=`991187fe772ad8b03746a4a9ddfc3ea9010e5896`。
- 入口fill manifest的1,832项全部匹配，manifest SHA-256=`fd1091e5497fe8b80583c99efa19003e5729e4059c92bff705c03f18ebb7f9e1`。既有fill、projection生产候选及V49/V50/V51保持不变；1,659个生产输入与入口逐字节一致。
- 最终候选1,835项，fingerprint=`1958e6af80032712b7137ecfe30e2a90f2962ed6df082308f8e773633c7d3991`。仅11个qualification入口、driver、observer和oracle文件增改，清单见[summary](summary.json)的`repeatedFaultBatch.harnessDelta`。生产/migration本轮delta=0；工作区原有生产改动保留。
- [规划](../GATEAUDIT_PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN.md) SHA-256=`80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`，不变。
- 运行时输入固定于`930290903060d57f5e2fb74ece3c8d1b9806ae7a315173107a5be32e9946b3f6`对应manifest后，最后一次变化仅为`l5_measurement.py`离线pool反例选取。五个已完成run的raw字节不变，已用最终oracle重新执行全部业务、并发和故障反例。两份manifest及精确差异保留在target；不把离线更改描述成新的JVM运行。
- 沿用L5BoundedWorkloadTest、continuous sampler、原会计重建及cursor oracle、Synthetic Venue和隔离PG16/V51。没有第二套load system、真实provider、LIVE或生产数据操作。

## 已完成的紧凑事实

以下每个run都是fresh DB、fresh workload identity、fresh进程启动；同时NQ=4、actor=4，240候选=228普通+12策略，4策略×3窗口。F1为每run一波4个响应不确定请求；F2为每个完整run一次指定边界强杀。没有把独立run次数冒充同一DB内的多轮长期累积证明。

| run | 边界 | Order/Trade/Ledger | actionable peak/final | reservations/wraps/coverage | BTC Position |
| --- | --- | --- | --- | --- | --- |
| F1-R1 | B1_ACCEPTED_TIMEOUT | 240/240/960 | 72 / 0 | 166/48/240 | 24.0 |
| F1-R2 | B1_LOST_ACK | 240/240/960 | 71 / 0 | 165/48/240 | 24.0 |
| F1-R3 | B1_ACCEPTED_TIMEOUT | 240/240/960 | 72 / 0 | 168/49/240 | 24.0 |
| F2-R1 | AFTER_VENUE_ACCEPTED | 240/240/960 | 68 / 0 | 194/59/240 | 24.0 |
| F2-R3 | V51_B | 240/240/960 | 70 / 0 | 200/63/240 | 24.0 |

五个完整run每个均：PLACE=240、CANCEL=0；Trade=240、TradeExecuted=240、Ledger=960、LedgerEvent=960、Snapshot=480；StrategyRun=12 SUCCEEDED、work=12、同窗口/同run身份唯一。独立从Trade、Order.side和base fee重建BTC=24.0，与Position.qty/available及latest BTC Snapshot=24.0完全一致；USDT按canonical Ledger SUM(delta)=0.00，与latest Snapshot=0一致。不是把Ledger净额当BTC数量。

每个完整run initial/new/processed/final actionable=60/180/240/0，unresolved=0；故障停止后仍完成剩余生产与恢复，再最终归零。持续采样覆盖故障前、中、后，订单和账务在恢复后继续增加；旧60个持久候选未成交时，新候选仍被处理。正常末尾重启的cursor持久身份及revision、强杀前已落盘的预留记录、全体候选覆盖均通过。

五个完整run的duplicate mutation/accounting、lost logical work、durable orphan均为0。真实overfill guard复用已接受的生产负例证据，源码不变，本批未制造非法fill。资源仅在本批有界运行内观察；不能据fresh DB独立重复推断同一实例长期资源累积稳定性。

## 阻断的直接证据

最新最终run：`backend/nq-app/target/l5-repeated-fault/final-F2-2.log`，Maven失败，原始proof及SHA见summary.failures。

选定JVM已装配既有`ARM_B4_TRADE_COMMIT`，原driver继续在候选预算内投喂，但其余actor先处理新成交，目标在15秒观测预算内没有出现`B4_CUT AFTER_TRADE_COMMIT`。控制器停止；没有为命中屏障扩规模、暂停生产正确性检查或继续反复碰运气。

该失败截面：112 Order/V49，52 Trade/TradeExecuted，208 Ledger，Position=5.2；60个ACCEPTED Order是刻意保留的未成交旧前缀。尚有128个计划候选未生成，策略尚未进入。不能把这些事实解释为永久backlog、丢单或生产停滞，也不能给完整240候选计PASS。后续F3最终repetitions未执行。

另一个同运行时候选run已完成240候选和恢复，但Maven在离线`pool_not_released`反例处失败：反例选择了已被强杀JVM最后一个sample。原文件为`final-F2-2-failed-export.log`，原始事实未改。已修正为选择正常收尾JVM，但仍保留该次失败，未将其用于填满F2接受条件。

需要进一步在现有driver中将目标业务调用与所选屏障稳定关联，避免其他actor抢先消费后目标永远无新事务；继续保持并发真实性、同一有限工作负载及STOP规则。本轮未实现可靠闭合，P3=`OPEN / BLOCKING_QUALIFICATION`。F4只有部分与F2组合的压力观察，尚非完整接受。

## 已做的最小harness修正与失败历史

- 独立故障启动入口只允许四个精确B4/V51控制命令；普通L5仍拒绝故障、LIVE和任意旧命令。已验证入口负例。
- 保留真实限频，策略一次产生4个PLACE后增加driver间隔。此前RATE_LIMIT_EXCEEDED保留为投喂失败。
- 正常关闭sampler先等待有界在途读，避免shutdownNow制造自身InterruptedException。未吞掉采样异常。
- 强杀前持续落盘有界cursor observer；只改测试观测，不新增业务锁或SQL写入。
- V51准备后尚无Venue Order时，仅L5模式复用B2已接受的51603 NOT_FOUND协议，替代成功空数据造成的OKX_EMPTY_DATA。
- 早期脚本编码失败、环境白名单拒绝、clean-command拒绝、未命中、采样和offline negative失败均保留在`backend/nq-app/target/l5-repeated-fault/`。首个控制器中止后只手动回收了身份核实的owned PG；不冒充自动cleanup PASS。

## 验证、资源与卫生

`SELF_REVIEWED / NO_ACCEPTANCE`，未新增Independent Review。Full Maven未运行，沿用已接受1919 tests / failures0 / errors0 / conditional skips140。目标cleanup/命令测试实际7/7 PASS（cleanup4、clean contract2、fault entry1），离线oracle测试4/4 PASS；较早一次cleanup条件跳过已明确保留，未计PASS。

最终对本批日志登记的108个PID、22个PG容器及40个端口核验：owned JVM=0、owned容器=0、端口无占用；既有不属于本批的容器未操作。各完整run pool max10/JVM、driver queue<=1、Venue executor<=4/queue<=16；逐PID峰值和终态在summary中。被强杀JVM以实际死亡验收资源释放，不伪造其最后sample为idle0。

最终stage-assets=1900 scanned / 173 exceptions / errors0；固定Gitleaks 8.18.4扫描13个本轮变更文件，findings0；六类secret negatives全部REJECT；links=2 checked / warnings0 / errors0；git diff --check PASS、staged paths0。完整记录见summary.repeatedFaultBatch.validation；卫生PASS不能解除故障资格阻断。原始日志/采样只留target，不进入Git。

既有ordinary concurrent INSERT loser P2、wildcard-import P3及`HISTORICAL_PROJECTION_REPAIR_REQUIRED / NON_BLOCKING_FOR_CURRENT_QUALIFICATION`保持原处置；本批没有历史repair。stage=0、commit=NONE、push=NONE，current authority不变。

**Next action：修复现有driver的确定性fault targeting后继续本任务；不进入L5 aggregate acceptance或L6。**


## Deterministic targeting harness remediation（2026-09-11）

本节是后续 test-only remediation 结论；以上原始 BLOCKED、失败和五个完整运行记录保留。Task=`NQ-GATEAUDIT-PHASE6-L5-REPEATED-FAULT-DETERMINISTIC-TARGETING-HARNESS-REMEDIATION`，starting HEAD=`991187fe772ad8b03746a4a9ddfc3ea9010e5896`，review=`SELF_REVIEWED`。本轮未修改 current authority。

旧屏障截取“该 JVM 下一次 Trade 调用”，其它 actor 抢先消费后可能永远无法命中。本次扩展已有 B4 事务外层 advice，预定 fresh database/run、logical Order 85、client=`l5 + database last20 + 0085`、fill=`b0-fill-b0-venue-85`、trace=`l5-trace-85`、Trade derived key=`OKX / b0-fill-b0-venue-85` 和 owner PID。第85次 PLACE 在本波其余三次之前确认，前84次已完成，避免以随机 Trade 顺序选目标。生产 Trade UUID 生成不变，由独立只读 reader 按 derived key 绑定实际 Trade/Order。

真实 transaction contract：`JdbcTradeRepository.insertWithRequiredEvent` 在同一事务写 Trade 与 required TradeExecuted。因此 boundary 是 **Trade + TradeExecuted durable，目标 Ledger 尚为0**；不是 Trade 与 Event 之间，也不是进入方法/即将提交。B4 外层 advice 等待原调用返回并验证 transactionActive=false；reader 再独立确认唯一 Trade、匹配 client 的 Order、1 Event、0 Ledger 后才允许 kill。

Handshake=`ARMED → TARGET_MATCHED → DURABLE_BOUNDARY_CONFIRMED → FAULT_READY → FAULT_INJECTED → RECOVERY_RELEASED`。old PID=33152，replacement PID=24288，fault timestamp=1789121050038 epoch-ms；完整时间、实际随机身份、cut/recovery facts 见 raw locator。最多6次真实 bounded reconciliation step（每次limit40），不是重试随机 fault run。reader network=5s，pause=5s，step=5s，kill前gate deadline=15s，barrier release=20s，既有child command=75s、kill=10s。超时产生 `BLOCKED / FAULT_TARGET_NOT_REACHED`，不继续注入 kill。

本次 gate=1620ms；短时暂停四个 qualification actor并等待其本地 workload slot quiescent，随后只放行owner真实对账。gate不持有业务DB锁、不改ownership/cursor、不写业务表。kill后立即释放另外三个actor，再启动replacement；整体保持4 JVM / 4 actors / 240候选 / 4策略×3窗口。限制：当前窗口暂停全部qualification消费，不支持仅排除单个fill；不据此声称故障瞬间仍有四actor消费。

保存并比较 beforeArm/afterArm、gateBefore/gateAfter、atCut/barrierAfter 三组完整业务快照，相等；production对账步骤自然产生的事实位于比较窗口之间。独立oracle拒绝3类快照变更、wrong owner/fill/Trade、缺失目标Trade/Event、过早Ledger、乱序handshake、late arming、额外kill及资源残留。NON_TARGET不拦截、wrong JVM不认领、同target只认领一次、release不重置认领权。business row mutation by harness=0，arbitrary fault kill=0。

最终目标验证=`10 tests / 0 failures / 0 errors / 0 skips`，含完整F2、4类真实清理fixture、4个barrier/identity单测及入口合同。RELEASE / ASSERTION / EXCEPTION 三个独立fixture都命中精确窗口，TIMEOUT fixture不提供目标fill且未命中，四者故障kill=0、owned NQ/Venue/PG=0。完整F2证明精确owner kill和replacement，最终owned=0且PG/Venue端口释放。生命周期 teardown 对所属进程的终止单独属于清理，不冒充 fault injection。未来DB connection loss / commit-response loss / recovery-pressure只做身份作用域表达单测；未来hook接线与正式F3/F4未验证、未计PASS。

完整dry proof：240 Order/V49/Trade/TradeExecuted、960 Ledger/ledger events、12 StrategyRun/dispatch work，Position/Snapshot BTC24，final backlog=0。business mutation negatives=19/19，concurrent negatives=9/9，精确fault negatives=20/20全部拒绝。此稳定候选完整运行 **计入一个F2 repetition（driver slot 2）**，不重复计算早期exploratory run。旧F2 slot1 AFTER_VENUE_ACCEPTED与slot3 V51_B仍为原边界correctness PASS；它们均不能证明指定Trade在kill前durable，故不是本精确边界的REUSED_ACCEPTED_PROOF。若恢复任务要求该边界三次重复，则目前1/3，尚缺2次；本轮不机械重跑旧run、不宣称F2 family ACCEPTED。F1保持3/3复用；F3/F4 NOT_COMPLETE。

候选manifest=`backend/nq-app/target/l5-targeting/final-candidate-v2.json`，SHA256=`53f7b9c7f8fe1434db88649bf78b5fe2b9c463de4a2341868edfbe6d72de19b9`，1838 inputs；相对任务入口仅8个test/harness文件变化或新增，1659 production inputs不变，production delta=0、migration delta=0。Full Maven=`NOT_REQUIRED`，复用已接受1919 / 0 / 0 / 140 conditional skips。

Raw=`backend/nq-app/target/l5-bounded/dde5ca9d-cd16-4698-9199-849e5b49dd8b/C3/raw-proof.json`，SHA256=`f2142a320c1d344c130616d7daa9c1c9e8c53bb1b06461eeafd398ee87bfb237`。稳定日志=`backend/nq-app/target/l5-targeting/final-stable02.log`，SHA256=`6e136f2b10f76142a0e53c6a870df67150d0d4b15700a5f2209cdae75ca44d4a`。清理fixture逐项locator/hash与完整handshake见相邻summary新增的 `deterministicTargetingRemediationBatch`；既有summary对象内容保持不变。

失败保留：`backend/nq-app/target/l5-targeting/harness-tests01.log` 为新增test泛型编译错误；`final-deterministic01.log` 为完整运行后exporter严格拒绝新增根字段，均不计PASS。修复将cleanup放入已有concurrent对象，未放宽schema。`deterministic-attempt01.log` 是早期候选探索，不计最终repetition。

验证文件位于 `backend/nq-app/target/l5-targeting/`：`stage-assets.log`、`secret-validation.json`、`links.log`、`final-integrity.json`。P0=0、P1=0；既有P2 ordinary INSERT loser、P3 wildcard-import与历史projection repair处置不变；仅关闭本targeting资格blocker。stage=0、commit=NONE、push=NONE。

Final decision=`PASS / L5_REPEATED_FAULT_DETERMINISTIC_TARGETING_READY / FAULT_TARGETING_HARNESS_GAP_CLOSED / TRADE_POST_COMMIT_TARGETING_DETERMINISTIC / NO_BUSINESS_FACT_MUTATION / CLEANUP_PROVEN / P0_0 / P1_0 / READY_TO_RESUME_REPEATED_FAULT_QUALIFICATION`。

Next action=`NQ-GATEAUDIT-PHASE6-L5-REPEATED-FAULT-UNDER-LOAD-RESUME`；不进入aggregate acceptance。

最终验证：stage-assets errors=0；links checked=2 / warnings=0 / errors=0；Gitleaks 8.18.4固定包hash及canonical CI配置验证通过，candidate findings=0；六类secret negatives全部拒绝，credential字段未被canonicalization隐藏；diff check通过、staged files=0。


## Repeated-fault qualification resume（2026-09-11）

Task=`NQ-GATEAUDIT-PHASE6-L5-REPEATED-FAULT-UNDER-LOAD-RESUME`；classification=`HIGH_RISK / SCALE_QUALIFICATION / REPEATED_FAULT / MULTI_PROCESS_RECOVERY / NQ-only`；review=`SELF_REVIEWED`。Starting HEAD=`991187fe772ad8b03746a4a9ddfc3ea9010e5896`，branch=`audit/post-gatey-agent-baseline`，stage=0。

入口1838 inputs匹配；projection/fill-idempotency/V49/V50/V51未漂移，1659 production inputs不变。Planning hash=`80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`。最终manifest=`backend/nq-app/target/l5-fault-resume/final-candidate.json`，SHA256=`797463469707f83919deb517dcad4efaed329003a972808a7c29a03b1134d126`。

F1-R1/R2/R3=`REUSED_PASS`，没有运行F1。旧F2两个不同边界的correctness observations保留，不计精确Trade-post-commit repetition。前一任务deterministic full proof复用为exact R1；本轮只新增exact R2/R3。

| Run | Boundary | Initial / new / peak / final | Cursor reservations / wraps / coverage | Result |
| --- | --- | --- | --- | --- |
| F2-R2 | AFTER_TRADE_COMMIT | 60 / 180 / 68 / 0 | 194 / 60 / 240 | PASS |
| F2-R3 | AFTER_TRADE_COMMIT | 60 / 180 / 70 / 0 | 199 / 61 / 240 | PASS |
| F3-R1 | BEFORE_PAUSE | 60 / 180 / 72 / 0 | 193 / 59 / 240 | PASS |
| F3-R2 | AFTER_PAUSE | 60 / 180 / 71 / 0 | 193 / 59 / 240 | PASS |
| F3-R3 | AFTER_PAUSE | 60 / 180 / 72 / 0 | 190 / 59 / 240 | PASS |

F2 exact=`3/3 PASS`。每轮fresh PG/Venue/database identities/NQ进程；逻辑Order85、fill/trace/derived Trade及owner在workload前绑定，独立reader确认Trade+TradeExecuted durable、目标Ledger=0，再kill exact owner并释放竞争actor。复用已接受六步handshake，不靠固定sleep猜目标。

F3=`3/3 PASS`：R1 ACK COMMIT未转发，独立reader见目标Order=SENT；R2/R3服务端COMMIT确认而响应被扣留，独立reader见目标Order=ACCEPTED。两类均在既有B4 wire control处kill持有者、DROP_PENDING关闭所属连接，replacement按持久真相恢复。故障中的ACK更新结果与此前已接受的Venue PLACE分开；未知ACK结果不会重发PLACE。R3使用driver `repetition=2`选择相同after-commit语义，但PG/Venue/workload/进程全部新建，单独原始证据，符合before/after至少各一次、共三次。F3 plan对象记录预定client/owner；其中armedBeforeWorkloadMillis是plan记录时间，实际ARM命令在目标波次前发送。

每轮4 NQ JVM / 4 reconciliation actors / 240候选 / 4策略×3窗口；PLACE=240（每逻辑身份恰一次），CANCEL=0，Trade=240，TradeExecuted=240，Ledger=960，StrategyRun/work=12，durable orphan=0。19类业务负例、9类并发负例逐轮拒绝；精确F2追加20类故障负例，F3追加7类故障负例。独立Trade/Ledger重建Position BTC24、latest Snapshot BTC24/USDT0等价，重复读与恢复后无额外mutation/accounting、lost work=0、unresolved=0。

Effective execution已由所有C3的既有策略路径覆盖：12个durable work的requested quantity=0.1005、effective quantity=0.100，wire sz、Order qty、Trade/账务都绑定effective truth；终态FILLED、phantom residual=0。无需重审或修改normalization。

F4=`PASS / SUFFICIENT_COMBINED_PROOF`，主证据F2-R3（其余完整run支持）：60个持久旧前缀+180新候选，四actor真实重叠消费，fault kill+replacement+后续restart，12个strategy windows；newerFilledWithOlderPersistent=180、全240候选被游标观察、最终backlog=0。没有叠加三故障，也没有额外F4运行；不重复计算repetition。

连续采样涵盖pre-fault、fault、replacement recovery和post-recovery，每周期后orders/Trade/Ledger与strategy恢复进度。各轮drain后active/pending/queue回落并清理所属JVM、Venue、PG/proxy、端口；跨轮残留为0。结论仅限此次bounded repetitions，不外推长期soak无泄漏。具体active/idle/pending、threads、queue峰值、cursor、raw/log locator和hash在summary新增对象中。

测试：5个完整新增fault run及2个driver合同测试通过；Full Maven未重跑，复用1919 / 0 failures / 0 errors / 140 conditional skips。production delta=0、migration delta=0。最终唯一test源码差异是L5BoundedWorkloadTest添加F3预定目标元数据；按每轮manifest记录真实执行候选。此前F3-R1中effective开关关闭，执行路径与最终路径一致，已接受F2的业务/故障控制完全不变，保留原候选身份。

失败历史保留：F3-R2.log在初始化时被原fixture环境校验拒绝，业务未启动；F3-R2-attempt02.log中misaligned ordinary quantity按当前合同REJECTED，ACK屏障无法到达。两者均为本轮harness输入选择错误，没有production finding；扩展已完整撤回，未放宽环境检查或normalization。所属资源均已回收；未把失败attempt计入PASS。

P0=0、P1=0；既有P2/P3与historical projection repair处置保持，前一targeting blocker仍为CLOSED。stage=0、commit=NONE、push=NONE。安全/hygiene结果及最终指纹核对见target/l5-fault-resume/最终验证文件。

Final decision=`PASS / PHASE6_L5_REPEATED_FAULT_UNDER_LOAD_ACCEPTED / F1_REUSED_3_OF_3 / F2_TRADE_POST_COMMIT_3_OF_3 / F3_DATABASE_AMBIGUITY_3_OF_3 / F4_COMBINED_RECOVERY_PRESSURE_PROVEN / REPEATED_FAULT_RECOVERY_CONVERGED / MULTIJVM_CORRECTNESS_PRESERVED / NO_DUPLICATE_MUTATION / NO_DUPLICATE_ACCOUNTING / NO_DURABLE_ORPHAN / RESOURCE_RECOVERY_BOUNDED / P0_0 / P1_0 / READY_FOR_L5_AGGREGATE_ACCEPTANCE`。

L5仍非ACCEPTED，current authority未更改。唯一下一动作=`NQ-GATEAUDIT-PHASE6-L5-AGGREGATE-QUALIFICATION-ACCEPTANCE`。

最终hygiene：stage-assets errors=0；固定Gitleaks 8.18.4 findings=0；secret negatives=6/6 REJECT；links=PASS；git diff --check=PASS。所属进程最终复核44个，存活0；staged=0。
