# Phase6 L5/L6 范围与 qualification 计划

任务：`NQ-GATEAUDIT-PHASE6-L5-L6-SCOPE-AND-QUALIFICATION-PLAN`。
分类：`HIGH_RISK / QUALIFICATION_PLANNING / SCALE_AND_STABILITY / REVIEW_ONLY / NQ-only`。
日期：2026-09-11。这里只形成计划；新增本文是用户明确允许的唯一写入例外。所有下述 workload、采样、故障和批次均为 **PROPOSED / NOT_RUN**，不是本轮运行结果或新 authority。

## 1. 起点、来源与定义权限

开始时分支=`audit/post-gatey-agent-baseline`；HEAD=`991187fe772ad8b03746a4a9ddfc3ea9010e5896`；本地 origin 引用相同，`git ls-remote origin refs/heads/audit/post-gatey-agent-baseline` 也返回相同 SHA；worktree clean、index empty。本次没有 fetch、stage、commit、push。

[STATUS](../../current/STATUS.md) 的 machine block 指定本次 planning 为 next action；L4=`ACCEPTED`，B0–B5=`ACCEPTED`，B6=`AGGREGATE_ACCEPTED`。固定 L4 technical pair 为 `3d103cea2072b3c2d9d1009cc5841c18a958ee80 / 34501806297 / 9 of 9 SUCCESS`；这是当前仓库接受记录，本轮没有重新查询该 CI 或重跑技术证明。repository schema=V51，不推断生产数据库版本。L4 P0/P1=0/0，不代表全仓库无缺陷。

| 来源 | 分类 | 可用于本计划的事实 / 不得推导的结论 |
| --- | --- | --- |
| [STATUS §5](../../current/STATUS.md)、[ROADMAP](../../current/ROADMAP.md) | CURRENT AUTHORITY | L5/L6 后续规模、故障与长期运行验证；要求本任务解析 scope，未给各自 canonical 数字/时长 |
| [L4 failure matrix plan](GATEAUDIT_PHASE6_L4_FAILURE_MATRIX_PLAN.md) §边界 | HISTORICAL IDEA / FUTURE OBLIGATION | scale、random chaos、soak 等曾因 L4 未接受而延期；延期解除不等于全部必须执行 |
| [B6 aggregate](GATEAUDIT_PHASE6_L4_B6_AGGREGATE_QUALIFICATION_ACCEPTANCE.md) | CURRENT ACCEPTED PROOF | 28 eligible rows 已接受，14 inactive rows 不计 PASS；承接 B0–B5，不重新打开 L4 |
| [TESTING](../../current/TESTING.md)、[WORKLOG](../../current/WORKLOG.md) | HISTORICAL EVIDENCE | Phase4/5 时点记录 L4/L5/L6 PROVE_FIRST、后置依赖；旧 NOT_STARTED 不覆盖最新 STATUS |
| [GateW plan](../../current/GATEW_PLAN.md)、[GateY frozen plan](../../gates/gate-y/GATEY_PLAN.md)、[GateJ roadmap](../../gates/gate-j/ROADMAP.md) | HISTORICAL IDEA / FROZEN HISTORY | 7 天/168h read-only、120h micro-live、1h/24h/7d 等属于不同 Gate，不恢复为 L5/L6 门槛，也不授予真实 provider 权限 |
| B6 的 typed pilot / intent worker inventory | RETIRED ASSET / FUTURE OBLIGATION | RETIRED_COMPATIBILITY_ONLY、DORMANT_NO_CURRENT_ENTRYPOINT 维持原状 |
| 本次用户任务 §23–24 | 本轮规划要求 | Frontend/Error Catalog 的相对位置与 Phase7 依赖；当前 docs/audit 与 ROADMAP 未检出相反的 Phase7 细分合同 |

搜索覆盖 current authority/ledgers、docs 内 L5/L6/Phase7 与历史 soak plans、scripts、CI、main/test Java、application profiles。没有找到与候选分工相冲突的现行 L5/L6 canonical 定义；历史数字属于其他 Gate。因当前 authority 明确委托本任务定义范围，可接受下面的责任划分，无需制造历史阶段事实。本文不做 STATUS/ROADMAP authority sync。

## 2. 责任与处置

**L5 — Scale & Repeated Fault Qualification**：固定有限 workload，在并发、数量、积压和少量重复故障下证明已接受 invariants 保持成立。以 workload 完成及 drain 为结束条件，持续时间不是独立证明变量。

**L6 — Active Runtime Stability Qualification**：只在 L5 已接受的并发/速率包络内，持续执行业务、采样、定时恢复和重复启动，证明时间累积不会导致资源泄漏、永久积压或事实损坏。持续时间与同进程/同数据库连续性是独立证明变量，不再探索更大吞吐。

两者共同 non-goals：不重证 lost ACK、cancel/fill、basic Kill、commit response loss、stale sender、same-window admission、StrategyRun crash recovery 的 L4 单场景结论；不做 HFT benchmark、任意 latency SLA、大集群、全 fault 笛卡尔积、生产容量承诺、真实交易或前端改造。

| 能力 / 工作 | 处置 | 执行规则 |
| --- | --- | --- |
| L4 已接受基础不变量及 B0–B5 infrastructure | REUSE_ACCEPTED_PROOF | 引用 B6 映射和原 manifest；只补 scale/repetition/duration 增量 |
| 有界 workload、backlog、重复 fault、持续资源稳定 | PROVE_FIRST | 先冻结试验合同再运行；未证明前不预设 production 有缺陷 |
| 既有 harness 多身份参数化、bounded 驱动、采样及聚合导出 | IMPLEMENT_NOW（后续首批范围） | 仅 test/tooling 增量；本轮实现=0；不能建立第二套 chaos framework |
| production remediation / migration | DEFER_UNTIL_TRIGGER | 只有可复现的新规模/时长失败且获相应实施授权才处理；独立审查后接受 |
| HTTP 连接内部统计、Venue 自身恢复、超大规模、日历跨日 | DEFER_UNTIL_TRIGGER | 按 §5/§8 的具体触发；不得影响当前不依赖它的证据 |
| 真实 OKX soak、micro-live、大型 Prometheus/Grafana 建设、HFT SLO | NOT_REQUIRED | 不纳入当前 L5/L6；真实交易另有授权路线 |
| dormant/retired runtime | DEFER_UNTIL_TRIGGER | 真正 canonical reactivation 后重新做 reachability 与资格判定 |

## 3. 当前资产与可复用程度

源码导航：[smoke harness](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke)、[scheduler runtime](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler)、[strategy runtime](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application)、[migrations](../../../backend/nq-infra/src/main/resources/db/migration)、[F007 evidence](GATEAUDIT_PHASE5_F007_MINIMUM_OPERATIONAL_OBSERVABILITY_IMPLEMENTATION.md)。下表“有测试”指文件及接受证据存在，本轮未执行。

| Capability | Current implementation / production reachable | Existing harness | Existing metric / threshold | Existing test/evidence | Reuse decision / gap |
| --- | --- | --- | --- | --- | --- |
| B0 real-process / multi-JVM | 真实 Spring、RiskGate、事务代理、adapter gateway；test-only launcher | B0Processes.Child、B0NqProcessMain、B0Fixture | PID、ready/result、命令 timeout；非持续 metrics exporter | B0RealProcessHarnessTest、B0FixtureSafetyTest、B6 | 复用隔离/启动/屏障；命令序列与固定身份需参数化，不能把循环单用例当 soak |
| PostgreSQL | 单 PG16、应用真实 JDBC/Flyway schema | B0Processes.Pg：digest 锁定、pull=never、loopback 随机端口、owned identity、tmpfs | 启动等待40s、外部命令45s是 harness timeout，非业务 SLA | B0 与 B1–B5 PG proof | 复用；保持同一数据库跨 NQ restart，最终校验 owned cleanup；tmpfs 数据增长计入主机内存预算 |
| Synthetic Venue | 独立 JVM 内存事实；不连 NQ DB | B0SyntheticVenueMain 多订单 map；B2SyntheticVenueMain 单 order 与 cancel/fill 控制 | /facts、请求/PLACE/fill/event 计数；4 worker、socket backlog16 | B1RealProcessProofTest、B2RealProcessProofTest | B0 用于多身份；B2 单订单控制须按身份扩展或明确串行子流，不能把单 order 字段当多订单支持；executor 默认排队无显式上界，首批限制并采样驱动/venue 队列 |
| 响应丢失/延迟 | adapter→loopback HTTP 真实路径 | B0 mode、B2 HOLD/RELEASE、独立 venue facts | ACK delivered/dropped/delayed 与 barrier deadlines | B1/B2 accepted | 复用代表性延迟/丢失，不新建网络 chaos 平台 |
| DB fault/commit ambiguity | 真实事务 + PG wire 边界 | B4PgWireProxyMain、B4TransactionFaults、B4ProcessFaults | before/after COMMIT、连接终止、事务事实；非总 DB transaction meter | B4RealProcessProofTest、B5V51CommitProcessTest | 复用限定连接故障；不对共享 PG 或宿主网络注入 |
| kill/restart | owned NQ 子 JVM 死亡与重启；durable recovery | B0Processes、B5*RecoveryProcessTest、B5DurableLifecycleCrashRecoveryTest | PID、exit、PG 状态、venue 请求数量 | B3/B4/B5、B6 | 同一 run 内重启 NQ；Venue 保持存活，不假装内存 Venue 能跨自身重启保存事实 |
| 手工/策略扫描 | StrategyManualTriggerService、StrategyScheduleScanService 真实业务入口 | B5QualificationControls.runStrategy/RUN_B5_STRATEGY，固定 b5 schedule | run/admission DB facts；没有通用多策略 workload 完成度汇总 | B5StrategyScanConcurrencyTest、B5AdmissionProcessTest | 参数化真实服务调用；不复活未注册交易 @Scheduled |
| 当前 scheduler | ValidationEvidenceScheduler 为只读 refresh；V51 StrategyRunRecoveryTick 为独立恢复 tick | B5SchedulerNqProcessMain、B5V51NqRecoveryMain、B5V51RecoveryTickProcessTest | validation_refresh F007；V51 tick 默认5s、允许1–60s；validation 默认5min、允许1s–24h | B5 final/B6、OperationalSchedulerMetricsTest | B0 initial-delay=24h、命令手调不证明 timer 长期触发；后续仅隔离 profile 覆盖 delay 并采真实 tick |
| reconciliation | OkxRestReconcileService.reconcileOnce、LedgerReconcileScheduler.reconcileOnce；方法可用不等于历史 timer 已注册 | B0/B2/B4 recovery 命令与 DB oracle | OKX DEFAULT_LIMIT=100；V51 recovery scan 的有界候选50；F007 unresolved 是累计事件 | B2/B4/B5 accepted、OperationalReconciliationMetricsTest | 复用单轮方法与游标；驱动有界定时调用，独立查询 backlog/oldest age |
| V49/V50/V51 | durable PLACE authority、strategy/window unique admission、durable dispatch work/Order lineage/cancel finality/cursor | B5Admission/Authority/V51* tests | unique/index/check 与业务/SQL oracle，无“V51 health”捷径 | B5 final、B6 | schema 保持 V51；逐业务 identity 检查，不用总 PLACE<=1 替代多订单证明 |
| Trade/Event/Ledger | accepted TradeExecuted atomic fan-out、账务重放 | B4TradeEventPostgresTest、B5FinalQualificationInteractionTest | unique Trade/Event、精确 qty/fee/balance/replay；B6 fee fixture 每 Trade 四分录 | B4/B5/B6 | 复用 oracle；四分录仅对应该有手续费 fixture，不能泛化全部账务 |
| Kill | KillSwitchService/RiskGate + ordinary authority | B3RealProcessProofTest、B5 interaction | snapshot version、拒绝事实、in-flight 收敛 | B3/B6 | 只在隔离 SIM fixture 操作，生产 engaged 不变 |
| monitoring/alert | PaperRunMonitorService.createAlert 与持久化 alerts；research PaperRun 不是普通 Order 的同义词 | PaperRunMonitor*Test、OperationalCriticalAlertMetricsTest | critical emitted counter；无全局 active-alert gauge | F007 accepted | 用合法 PaperRun API/service fixture 验证监控/报告；不要自动假定 ordinary workload 已关联 PaperRun |
| runtime health | OperationalObservationConfiguration + Actuator | OperationalObservationConfigurationTest | health/info HTTP；operational 是 process-local 摘要，业务失败仍 UP，unknown=-1 | F007 accepted | UP 不作为业务合格依据；采原摘要与独立 qualification 状态，不能把已接受语义重新定 P1 |
| daily report | PaperRunMonitorService.generateDailyReport、PaperRunDailyReportRepository | PaperRunMonitorServiceTest 等，未见 L6 连续驱动 | 按 run/date upsert、实际alertCount；收益/交易等字段当前为0的minimal report，无长期门槛 | 当前 research 实现/测试 | 首批接入合法PaperRun fixture；同日显式生成核对唯一记录和alertCount，不把占位0当交易统计，不宣称跨日调度已证明 |
| resource metrics | Actuator/Micrometer + JVM/OS/PG | 现有 MeterRegistry 与 health 测试；B0 web=NONE | 默认 exposure=health,info，无 HTTP metrics；长期阈值未建立 | F007 / application.yml / app pom | 扩展 test-only 采样协议，优先 in-process registry/MXBean；不可声称现在已有可直接 scrape 的完整 soak metrics |

没有发现当前 scripts 中可直接覆盖上述完整业务链的 L5/L6 soak/stress runner。既有 CI、runtime/恢复脚本只按原合同复用；历史生产启动/soak 不执行。

## 4. L5 规模合同

表中 baseline 是当前夹具/代码规模或“未建立”，不是实测容量。数值全部是本计划提出的有限资格范围，须在 L5 首批校准可行性并冻结 run manifest；若主机不足，记录 BLOCKED/修订提案，不能静默降低级别后宣称原级别通过。不做各维度最大值的笛卡尔积。

| Dimension | Baseline | Proposed qualification level | Hard upper bound（单 run） | 理由 / 可揭示失败 |
| --- | --- | --- | --- | --- |
| concurrent commands | B0 每子进程一个 pending；已有双 JVM 竞争 | 1→2→4，同 identity 竞争与独立 identity 各一组 | 8 pending（含驱动等待队列），正式级别4 | 超过双 actor，触发 DB 竞争/排队；producer 必须背压 |
| orders per run | B2 单 order；B0 多订单容器但非规模证明 | 先10 smoke，再120；规模尾批240 | 300 distinct orders，重复请求另计 | 跨 OKX 100 / V51 50 的批次边界，检查 prefix starvation |
| fills per order | accepted 单 fill / partial 2 fills | 1与2常规，少量4 partial | 4，累计数量精确等于冻结 effective quantity | 重复报告、部分成交累计与账务 fan-out；非1M fills |
| active strategies | B5 固定 strategy/schedule fixture | 2→4 | 8 | 同 window 竞争与不同策略进度隔离；风险限额保持生效 |
| strategy windows | 单 window admission + future dueAt progress | 每策略连续3个真实 dueAt | 6/strategy | 防上一 window 阻塞后续；不改全局时钟伪造运行天数 |
| reconciliation candidates | OKX每轮100，V51每轮50 | backlog=120，尾部验证240 | 300 | 至少两次/多次游标绕行；包含 CANCELLED/fill recovery eligible facts |
| multi-JVM count | 2个 NQ + 独立 Venue 的接受证明 | 2个常规，4个 NQ 有界竞争 | 4 NQ + 1 Venue + 至多1 PG proxy + 1 controller；PG容器1 | 单数据库、多 Spring actor；不承诺100 JVM；主机内存不足停止 |
| restart count | 单场景 kill/restart proof | 每代表组合3次（不同 workload chunk） | L5单run 3，L6单run 6 | 重复 recovery 避免只证明第一次启动 |
| fault repetition | L4确定性边界各场景 | 每个代表组合3次 | 每组合3；L5-C总计12次 | 检查故障状态清理与重入；不枚举全组合 |
| DB transactions | 未建立总计量；业务多事务 | 按实际 admitted/completed + pg_stat_database delta 记录 | 100000 committed+rolledback delta，超出安全停止并调查 | 观察 retry/query storm；包括采样事务，单列采样开销，不设吞吐 gate |
| scheduler iterations | B5手调；V51真实 tick proof | L5 validation在隔离30s周期下20次可观测完成/合法skip；V51按自身周期采样 | 每个scheduler每run 200轮，超出结束该有界子run | 固定 key锁、前后任务进度，skip 不充成功；L5加速不声称默认周期soak |

L5 每 run watchdog 上限30min（不含首次构建）；不是 latency SLA。内存预检以所有 NQ/Venue/PG tmpfs/controller 合计预算不超过开始可用内存的60%为计划安全上界；逐进程固定 heap/总预算并记录，值在首批冻结。磁盘保留至少2GiB且不低于开始可用量20%；raw artifacts 每 run 上限1GiB；超过资源安全线立即停 producer、保存证据、drain/cleanup，不能 OOM 宿主来制造压力。安全停止为未完成资格，根因若是预算过小不直接报 P1。

### L5 fault 组合与 metrics

只选以下四个代表组合，先正常 workload 对照再注入，各重复3次：

1. 多 JVM + duplicate command / same-window scanner + recovery：4个 actor、同身份竞争夹在独立合法订单中；每身份 PLACE<=1，未来 window 可继续。
2. backlog + NQ process death/restart：120 candidates 中保留尾部标记，沿用 V51 durable recovery；Venue/PG 不重启，检查游标公平与无 orphan。
3. load + delayed/lost venue response：并发上限4，沿用 B0/B2 屏障；有限挂起与释放，观察 in-flight/queue/pool、query-first，不能 blind PLACE retry。
4. load + DB connection/commit ambiguity：复用 B4 PG proxy 的已知边界，before/after 各至少一次、共3次；对照事务真相，不能用日志替代提交事实。

Kill transition 仅在第2组最后一个 chunk 附带一次：新发送被拒绝、既有 in-flight/账务可收敛；不是第五个批次。若组合掩盖故障根因，隔离重现该失败，不扩成全矩阵。

记录 arrival/completion/rejection、active commands、queue、DB active/idle/pending/timeouts、线程、heap/GC、per-identity PLACE/fills/Trade/Event/Ledger、backlog/oldest age、scan/tick progress、fault boundary/PID/exit。p50/p95/max latency、throughput、GC pause 是 performance observation，不设未经 authority 批准的固定 ms gate。

### L5 batches、entry、exit

| Batch / 任务后缀 | Entry | Work / 必要交付 | Exit / cost |
| --- | --- | --- | --- |
| L5-A `BOUNDED-WORKLOAD-AND-MEASUREMENT` | 本计划完成；后续任务明确授权 test/tooling；current candidate/cleanliness重新绑定，L4接受不变；owned PG/loopback/no credentials 可验证 | 沿用 B0/B2/B5 参数化身份/有限驱动、独立 Venue 多订单事实与分区计数、资源采样、规范化导出；10订单正对照；冻结全部预算、采样/阈值、run command、candidate/fixture/config hashes；验证采样缺失/上限触发不会伪报PASS | 有界 producer、业务oracle、资源可观测与失败导出均可用；没有 production delta；不是 L5接受；MEDIUM |
| L5-B `CONCURRENCY-AND-BACKLOG` | A能力与manifest冻结 | 1/2/4并发，120及240 candidates、4策略×3window；每次drain和尾部进度；校准健康吞吐与噪声 | 规模表mandatory levels达到；§7 correctness/operational无阻断，资源基线冻结；MEDIUM |
| L5-C `REPEATED-FAULT-AND-AGGREGATE` | B通过且同候选或完成证据失效评估 | 四组×3次；Kill附带；冻结结果汇总，复用B6不重跑L4 | 所有mandatory组合与清理通过，P0/P1=0、证据完整、P2/P3有disposition；资格结论待授权精确交付及exact-head CI；MEDIUM |

L5 ACCEPTED 仅在 A/B/C、候选证据一致、必要整改审查及授权交付完成后成立。本轮 `READY_TO_START_L5` 仅表示首批范围可执行，不表示 L5 已通过或已取得后续实施/发布授权。

## 5. L6 资源 inventory

“MEASURABLE_NOW”表示现有 JVM/OS/DB 接口能提供测量，不表示本轮已采样；所有指标必须由首批正对照确认存在、单位和重启语义。test-only exporter/采样器属于轻量 instrumentation。

| Resource | Classification | 来源 / 缺口与处理 |
| --- | --- | --- |
| JVM heap / GC | MEASURABLE_NOW | MemoryMXBean、GarbageCollectorMXBean /现有registry；记录used/committed/max、GC count/time；低谷需带自然GC事件，不强制GC来制造平稳 |
| threads | MEASURABLE_NOW | ThreadMXBean、PID级OS线程；记录live/peak/daemon，必要时线程类别快照 |
| executor queues | NEEDS_LIGHTWEIGHT_INSTRUMENTATION | driver/venue executor 实例显式采样；V51单线程fixed-delay不累积tick批次，但不能推导所有队列都有限 |
| DB pool | NEEDS_LIGHTWEIGHT_INSTRUMENTATION | 在实际 DataSource 确认为Hikari后读 pool MXBean/registry active/idle/pending/max/timeouts；源码未给统一显式pool容量，禁止把框架默认值冒充仓库门槛 |
| DB connections / transactions | MEASURABLE_NOW | owned PG的pg_stat_activity/pg_stat_database，区分应用、采样连接与累计stats reset；idle in transaction与年龄单列 |
| HTTP client connections | NOT_CURRENTLY_AVAILABLE | JDK HttpClient 内部连接池无已核实统一指标；用owned进程TCP连接、in-flight与FD作代理；不足以声称完整pool leak proof。若代理异常或资源耗尽，升级轻量诊断后再接受受影响项 |
| open handles / file descriptors | MEASURABLE_NOW（平台条件） | Windows Get-Process.HandleCount；Linux /proc/PID/fd。平台不支持则NOT_CURRENTLY_AVAILABLE，保留覆盖限制，不以0代替缺失 |
| scheduler active tasks / progress | NEEDS_LIGHTWEIGHT_INSTRUMENTATION | F007 validation进度 + test-only V51 tick begin/end/active计数；只读scheduler与recovery分开 |
| reconciliation backlog / age | NEEDS_LIGHTWEIGHT_INSTRUMENTATION | 有界只读SQL统计本run的eligible未收敛facts及最老年龄；F007 unresolved counter不能替代 |
| audit/event growth | MEASURABLE_NOW | owned DB按run统计rows/bytes，与completed订单/Trade/实际scan次数关联；不可要求业务增长时表大小不变 |
| log growth | MEASURABLE_NOW | B0Processes.Child.log 文件字节/速率；长跑禁止无界内存读取全日志；采样与导出也计预算 |
| temporary files | MEASURABLE_NOW | 仅枚举owned target/run目录的count/bytes；argfiles/PID日志计入；不扫描用户隐私目录 |
| container/process count | MEASURABLE_NOW | owned labels、PID+start time+parent；结束时owned survivors=0，不清理其他任务容器 |
| 新 Prometheus/Grafana 平台 | NOT_REQUIRED | 现有Actuator/Micrometer+轻量文件时间序列足够，不扩暴露端口或部署平台 |

## 6. L6 active workload 与持续不变量

单 controller、1 owned PG、1始终存活 Synthetic Venue，2 NQ actor；并发目标2、上限不超过 L5接受的4。到达率取 L5-B 无故障持续可完成速率的25%，每秒最多1个新订单；若背压出现即暂停 producer，不能把饱和测试伪装成 soak。每run最多3000 distinct orders、每订单最多4 fills、fault/restart<=6、DB transaction delta<=1000000、raw<=1GiB；其余主机安全线沿用§4。触及count/安全线提前终止不能算时长tier完成；下一次调整冻结速率，不在同run悄悄重置计数。

循环真实链路：合法定义/账户/余额/风险fixture初始化 → 手工 strategy scan/manual trigger → ordinary Order → Synthetic fill（1或2，少量4 partial）→ reconciliation → durable Trade/TradeExecuted → Ledger posting/replay → StrategyRun projection → monitor与report校验。初始余额/risk预算必须能支持整个有限run；不能绕过风控为凑数量强行下单。预期拒绝单列，不充作成交样本。所有最终业务事实来自真实服务/交易所/数据库，不SQL伪造Order、Trade、Event、Ledger成功结果。

每个active阶段10分钟采样窗口必须有至少一个完整成功业务链、策略scan、reconciliation与真实scheduler进度，否则该窗口不能支持active soak结论；drain静默窗口单列，不要求新订单。只读validation scheduler正常5min，V51 recovery tick正常5s；driver按5s调用可达reconcile单轮并防重叠。真实计时启动需解除B0 test launcher的24h initial-delay，仅在隔离test profile配置；不能全局@EnableScheduling激活历史方法。L6不继承L5每scheduler 200轮的短run上限：short/extended分别以1000/3000次实际回调为安全上限，过夜按冻结周期计算有界上限。

Paper monitoring/report 使用独立合法PaperRun fixture/service，并记录其与ordinary workload的关系（若无自动关联，明确两条样本链）。至少产生一次持久化CRITICAL正例并核对F007 emitted、一次正常monitor对照、同一日期报告重生成的唯一记录和实际alertCount。createAlert本身是insert，不假设重复调用自动dedup；minimal report的交易/收益0字段不是业务oracle。这里不发送外部通知、不等待真实午夜、不将report当作ordinary ledger oracle。若入口在隔离组合中不可用，先完成轻量合法fixture接入，不能以report=NOT_RUN接受mandatory观测链。

持续检查：每身份Order/effective qty/环境一致；Trade按venue fill唯一；TradeExecuted按对应Trade唯一；Ledger借贷平衡、fee/金额精确、重放无增量；StrategyRun无永久orphan且后续window有进度；V49一次性发送、V50 admission unique、V51 run-order/work/finality一致；query-first/no blind retry；PLACE<=1 **按(account,canonical venue,client identity/authority)分组**；已知终态最终对齐；Kill阻止新发送但允许既有恢复。短暂CREATED/DISPATCHING/RUNNING或未知venue状态不能直接叫orphan，更不能猜成功。

每个窗口以独立venue事实和只读数据库oracle对账；最终停止新输入、释放注入故障、执行drain，再对全run完整比对。持续采样可以增量，但最终总账/唯一性必须全量覆盖本run，不以抽样代替。

## 7. 阈值、失败与严重性

所有 operational 参数在故障/soak前随manifest冻结，失败后不可放宽为PASS。测量缺失、fixture串扰、候选漂移、controller超时属于 INVALID/INCOMPLETE qualification，保留失败证据；与产品P级分开判定。

| 类别 | Blocking threshold / 判定方式 |
| --- | --- |
| Correctness | duplicate external mutation=0、duplicate Trade/Event/accounting=0、借贷或精度差异=0、跨环境污染=0、unauthorized send=0；任一新增违反立即停止producer并保留证据 |
| Recovery/backlog | 故障释放后冻结候选集合必须完成至少两次公平游标绕行；设K为manifest内eligible总数、b为实际batch，回合下限=2×ceil(K/b)。drain deadline D=max(2×无故障同K drain耗时, 回合下限×(配置周期+健康单轮最大耗时))；L5-B校准后冻结，D上限10min。超过上限表示目标不可接受/需调查，不自动无限等待 |
| Unknown external facts | 注入未解除前query-first未决是预期；故障解除且venue可查询后仍越D未决则FAIL。不得为满足drain清空durable facts或重发PLACE |
| Pool/connections | 无注入区间非预期pool acquisition timeout/exhaustion=0；注入区间预期连接异常按计划计数，释放后D内pending=0、应用active归静默基线，idle in transaction遗留=0；idle pooled connections可保留，不要求总连接=0 |
| Queues/backpressure | producer/venue/执行队列均要有预算与可观测上界；in-flight+waiting不得越manifest cap；不能接入无界producer。超限拒绝且核心进度保持为有界降级；停止输入后越D仍不drain则FAIL |
| Scheduler | due且enabled、无合法锁占有/注入时，超过max(3×配置周期, execution timeout+2×周期)无开始/完成进度则FAIL并调查；主动disabled/预期dedup skip不算成功，也不凭空制造stall |
| Resources | warmup后至少3个等长10min窗口及drain静默窗口；heap比较带GC低谷，threads/connections/handles/temp比较同负载窗口低谷。连续3窗口增长且drain后不回基线噪声带即LEAK_SUSPECT，阻止该tier接受；以对象/线程/连接持有证据或第二次同机制复现确认leak。仅线性拟合正斜率不足以宣称无界 |
| Noise band | L5-B相同负载正对照按同采样方式建立max-min变化带，随manifest固定；时间序列太少/无可比GC低谷则INCONCLUSIVE，不伪报heap稳定。测量带不是SLO，也不能掩盖达到预算的单调增长 |
| Growth/disk | 合法audit/event按业务量增长；按每订单/每scan标准化，静默期只能有已列明定时审计增长。越资源线或出现无业务解释的持续增长先FAIL/诊断，不能删除审计以通过 |
| Restart/cleanup | 同DB/存活Venue，NQ在冻结启动deadline内ready且D内恢复；最终owned进程/容器=0，owned句柄/临时资源按保存清单收敛；只alive不合格 |
| Health / alert | 原始Actuator UP不是业务资格。qualification composite=DB事实+进度+diagnostic freshness+原始health；任何核心invariant破坏或观测不可用时不得输出绿色qualified。F007已接受的UP摘要本身不重新定P1；若新scale/duration使核心永久失败却被整体验收信号掩盖，则FAIL/P1 |

Correctness loss、permanent recovery loss、resource exhaustion停止核心runtime、核心破坏而资格健康误报 → **P1**。真实资金/安全隔离被突破等灾难性影响 → **P0**，立即停止相关动作；不能因在synthetic环境而忽略边界违规。

无正确性损失的bounded degradation、可避免低效、告警运营薄弱 → **WARNING/P2**；可定位的test/tooling/观测展示完善 → **P3**。一次latency spike、一次GC、pool短暂达到max但无timeout且有进度不自动成为P1。疑似leak可阻断资格等待诊断，但严重性以影响证据确定。

Performance observation：吞吐、p95、GC pause、每订单DB事务/bytes。Future optimization target：只有重复观测存在实际瓶颈才提出，不承诺生产HFT latency；本次未查得适用于L5/L6的固定latency SLA authority。

## 8. L6 时长、加速与批次

时长是本计划提案，不恢复历史数字。采样间隔10s；warmup固定10min，期间业务也计总量但不进资源趋势基线。首批可因启动条件修订warmup，但必须在正式run前冻结。

| Tier | Duration / workload | 信息价值与限制 | Disposition / cost |
| --- | --- | --- | --- |
| Short active soak | 60min连续：10min warmup+40min active+10min drain/静默；按§6速率和硬预算 | active覆盖8个5min validation周期、约480个5s recovery机会与4个资源窗口；按实际完成计数，不以理论tick数充证据；识别快速累积/未关闭资源 | L6 mandatory blocking；MEDIUM |
| Extended local soak | 180min：10min warmup+160min active+10min drain；同一PG/Venue；约40/80/120min依次执行下述3个restart事件 | 保留每个PID的连续窗口，对比多代NQ的稳定平台、PG/日志的累积；较60min扩大3倍时间窗口，仍不证明日/周泄漏 | L6 mandatory blocking；LONG |
| Optional overnight | 最长8h，保留warmup/drain，速率按3000订单与1GiB上限预计算，不扩大并发 | 仅当长周期到期/跨日需求真实接入，或60/180min无法区分噪声与慢增长时有价值；跨日尚未被本scope证明 | DEFER_UNTIL_TRIGGER / OVERNIGHT_OPTIONAL；无触发不运行；若为解决blocking疑点而启动则必须解决疑点才能接受 |

允许的加速只在test profile：validation fixedDelay=30s（仍在允许范围内），recovery delay=1s（代码允许下界），driver提高有限重复次数。加速可作为L5/短诊断前置，不能替代mandatory默认周期连续run；不改变CRON window身份、业务Clock、TTL/lease、状态机、risk、retry或production配置。高频次数证明不等于日历时间经过。没有必要引入24h/72h/7day默认门槛。

L6 restart/fault：extended中先一次graceful NQ restart，再一次forced NQ death，再一次短暂venue delay与NQ recovery restart；保留同一PG和Venue事实。每次恢复后继续active workload并保留至少3个10min资源窗口（事件尽量置于约40/80/120min，最终active至170min）；不循环重启Venue来清空内存。Venue自身持久化重启继续FUTURE_OBLIGATION；只有真实使用需要该故障模型时再提出最小fixture持久化方案。

| Batch / 任务后缀 | Entry | Work | Exit / cost |
| --- | --- | --- | --- |
| L6-A `ACTIVE-STABILITY` | L5 ACCEPTED；同候选/失效评估通过；§5 mandatory指标可采；真实timer、Paper monitoring/report fixture已接入；阈值冻结 | 复用L5 runner，60min active soak，无新框架建设批次 | active链、默认scheduler、资源窗口、drain及alert/report均满足；所有missing项显式处置；MEDIUM |
| L6-B `RESTART-CONTINUITY-AND-AGGREGATE` | A通过 | 180min extended与3次restart/fault；必要时按触发诊断；聚合所有run和未覆盖窗口 | §7通过、P0/P1=0、无未解LEAK_SUSPECT/核心measurement gap、清理完成、证据可复核；之后按授权交付接受；LONG |

L6接受范围限已测JDK/OS、单PG、synthetic workload、时长与规模包络；Windows handles结果不冒充Linux FD证明，180min不宣称不存在慢性日/周泄漏。可选项缺失不是PASS；mandatory测量缺失不能用“future”标签绕过。

## 9. 证据、CI、review 与交付合同

未来沿用audit/evidence风格，在 `docs/audit/evidence/phase6-l5/` 与 `phase6-l6/` 各维护 `qualification.md`、`summary.json`；有必要时每run一个紧凑 `runs/<canonical-run>.json`，L6时间序列聚合成单个 `metrics/summary.json`。这些路径本轮不创建。raw stdout/PG trace/venue原始事实/高频采样放ignored `backend/nq-app/target/qualification/<run>/` 或CI artifact，不默认进Git。

每个summary必须绑定：source HEAD/tree、production/test/fixture/config内容hash、JDK/OS、locked PG image digest/schema、run身份与PID代际、seed、完整命令、参数/预算/时间线、mandatory rows、实际sample count及缺口、pre/post清洁检查、exit code、fault before/after事实、每不变量结果、未决项/P级、raw artifact位置/retention及必要完整性校验。正常/失败attempt分别保留，不覆盖；只保存最小失败重现与必要machine facts，不复制数百散文件。

复用[synthetic identity policy与交付规则](../../../.agents/skills/nq-trading-correctness-proof/references/regression-delivery.md)：运行身份随机，tracked export使用batch/run/type内稳定双射canonical references；raw及逆映射留ephemeral artifacts，保持跨事实join/唯一性/数量可逆验证，不用通用redacted或随机identity的hash替身掩盖来源。UNKNOWN/credential sentinel不得清洗，必须拒绝导出；不扩大secret allowlist。制品/候选内容hash与业务身份替身不是同一概念。

| 场景 | Review / CI / delivery policy |
| --- | --- |
| 本规划 | docs-only self-review与只读/轻量检查；不独立审查、不Git发布、不authority sync |
| qualification only，production delta=0 | self-review → 有明确Git授权才precise delivery；不每run另开Independent Review。测试fixture也须证明oracle独立、真实PG/业务链、无SQL伪造结果 |
| production correctness remediation /关键并发 | 一次真正Independent Correctness Review，先核对冻结candidate/证据身份再复现必要关键失败；实现者换角色/Skill不算独立 |
| schema change | forward-only，单独一次Independent Review+隔离PG证明；不修改V49/V50/V51历史migration |
| security/deployment/release trust change | specialized review及明确范围授权；本计划不授权修改 |
| ordinary PR/exact-head CI | [ci.yml](../../../.github/workflows/ci.yml) 当前PR/push只匹配dev，另有workflow_dispatch；9个job覆盖治理、no-outbound、backend、PG/Flyway、frontend、research、secret、Java、provenance。远程audit分支push不应假定自动执行；授权交付时核实dispatch与实际headSha |
| 长soak/重负载/多进程fault | 本地显式qualification命令为默认；普通CI只放小型driver/exporter/边界回归。Docker/process故障需要独立资格运行环境，不塞每次PR。未来manual workflow只有确有需求才提案；nightly当前NOT_REQUIRED；本轮.github delta=0 |

原B0等测试存在opt-in/进程环境要求；普通CI绿色不能替代动态资格证据。后续runner真实命令由L5-A实现后在summary冻结，本文不编造现存可运行的L5/L6测试类。发布验收必须精确manifest列文件、核对staged inventory/blob/diff、授权commit/push、等待delivery commit exact-head CI；旧L4 CI不能标成新L5/L6 delivery。无需机械重跑所有suite，生产/fixture/config变化按影响撤销相应证据。

同一leak/retry storm/backlog/evidence机制重复两次，启动 `RECURRING_PROBLEM_ROOT_CAUSE_REMEDIATION`：保留失败 → reproduce → trace history → identify owner/root cause → fix → 原始/邻接/正常回归 → capture lesson。遵循[根因治理](../../../.agents/skills/nq-trading-correctness-proof/references/engineering-lessons.md)，按共同机制而非报错文本归类；不能每个soak run追加allowlist或workaround。本轮未发生需要该整改的产品运行失败。

## 10. 边界、路线与最终判定

real credentials=0、real exchange mutation=0、LIVE enable=0、real money=0；不执行真实PLACE/CANCEL/transfer/withdraw、不解除生产Kill、不读生产业务数据。synthetic SIM fixture里的Kill/fill/Order只按隔离合同执行；fixture安全控制仍必须真实生效。

P2 ordinary concurrent INSERT loser、P3 wildcard-import residual=`OPEN / NON_BLOCKING`；其他历史残余按原记录保留，不将观测缺口自动列为新P1。14 inactive rows继续NOT_CURRENTLY_ELIGIBLE / FUTURE_OBLIGATION，P1-1/PB1退休、PB2 dormant，不为覆盖率恢复；Venue restart persistence、未注册交易scheduler/不存在的lease/leader按真实入口触发再证明。

路线：**L5 ACCEPTED → L6 ACCEPTED → 剩余独立工作包（Frontend localization、Error UX consolidation、Error Catalog、NQ-TRD-1001 + ORDER_VERSION_CONFLICT）→ Phase7 final baseline / archive / freeze**。前端/错误目录不进入L5/L6实现；Phase7仍需自身当前合同与冻结检查，不由本文直接接受。上述相对位置来自本次用户要求，未发现相反current authority，不伪造未找到的Phase7细分定义。

时间成本只作执行类别：本planning=SHORT；L5首批/规模/重复fault=MEDIUM；L6 short=MEDIUM、extended=LONG；有触发才OVERNIGHT_OPTIONAL。不承诺项目完成日期。

本轮验证范围：git baseline/status/远端只读引用；authority与本文相对链接；current harness/metrics/CI source inventory；新文件空白检查及最终scope/index检查。Full Maven、L4 matrices、load、stress、soak、chaos、real provider均NOT_RUN。最终检查结果在下节记录。

规划裁决（仅规划，不表示阶段接受）：

```text
PASS
PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN_ACCEPTED
L5_SCOPE_DEFINED
L6_SCOPE_DEFINED
SCALE_LIMITS_DEFINED
SOAK_DURATION_POLICY_DEFINED
FAILURE_THRESHOLDS_DEFINED
EXISTING_L4_PROOFS_REUSED
NO_REAL_TRADING_AUTHORIZED
READY_TO_START_L5
```

唯一下一动作：`NQ-GATEAUDIT-PHASE6-L5-BOUNDED-WORKLOAD-AND-MEASUREMENT`。本轮不启动它；STATUS machine next-action保持原值，本文的推荐下一动作待后续授权工作承接。

## 11. 本轮轻量验证结果

| 检查 | 结果 |
| --- | --- |
| baseline / remote | 分支、HEAD、本地origin与只读ls-remote一致；开始worktree clean |
| `scripts/docs/check-current-authority.ps1` | PASS，errors=0；authority未修改 |
| `scripts/docs/check-doc-links.ps1 -Roots docs/audit/evidence/GATEAUDIT_PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN.md` | PASS，checked=18、warnings=0、errors=0 |
| `git diff --check` + 新增文件空白检查 | tracked diff无错误；新文件额外检查，因为普通diff不覆盖untracked。no-index显示exit=1是新增内容有差异，输出无whitespace error；LF→CRLF提示为现有Git转换配置 |
| scope / index | 只新增本文；staged=0；production/migration/harness/.github/STATUS/ROADMAP delta=0；commit=NONE、push=NONE |
| runtime tests | 全部NOT_RUN，符合本次planning限制 |

本规划自查新增P0=0、P1=0；没有运行负载来证明产品缺陷清零。既有P2/P3继续OPEN/NON_BLOCKING；本文列出的未实现qualification能力与测量限制保留为后续entry条件，不冒充测试通过。
