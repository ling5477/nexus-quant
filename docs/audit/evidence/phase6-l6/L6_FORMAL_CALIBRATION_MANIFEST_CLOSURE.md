# L6 Formal Calibration Manifest Closure

**BLOCKED / L6_FORMAL_CALIBRATION_EVIDENCE_INSUFFICIENT / MINIMAL_CALIBRATION_RUN_REQUIRED**。

Task classification：`EVIDENCE_RECONCILIATION / OFFLINE_CALIBRATION_ASSESSMENT / NO_DYNAMIC_RUN`。附带阻断：`HEALTHY_RATE_CALIBRATION_EVIDENCE_INSUFFICIENT`、`CALIBRATION_HARNESS_CHANGE_REQUIRED`。本轮没有生成可供运行读取的 `L6_FORMAL_CALIBRATION_MANIFEST.json`，没有将不足的数据填成已冻结值；L6仍NOT_ACCEPTED，60min/180min均NOT_STARTED。

## Baseline与证据来源

Branch=`audit/post-gatey-agent-baseline`；HEAD/upstream=`1208abd904c318b832af152c9c52f8a5f4b46df3`；tree=`cf989b24331f38b9a3618b1fd58de68d3a26c302`。本轮查询 [CI 34739867074](https://github.com/ling5477/nexus-quant/actions/runs/34739867074)：completed/success，exact head，9/9 SUCCESS，failed/cancelled/skipped=0。起始9份未提交L6 evidence，tracked diff=0、stage=0。

已读取并交叉核对冻结规划、L5 bounded/concurrent/repeated-fault/aggregate四份报告、summary、runs及L6全部现有证据；总计53份文件，逐文件SHA-256与相关段落保存于raw索引。规划SHA-256=`80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`。规划第106行要求L5-B校准健康吞吐与噪声，第134行要求25%速率，第159行要求同负载同方法max-min噪声带。旧L5聚合最终只接受 `BOUNDED_FOR_L5_SCOPE`，未冻结本轮需要的校准定义。

| 来源 | 接受身份 | 本轮用途 |
| --- | --- | --- |
| S1/S2/S3最终run | REUSED_ACCEPTED_PROOF | 检查原始bounded观测；不是L5-B连续完成率 |
| C1-resume、C2-resume、C3-resume-accepted | REUSED_ACCEPTED_PROOF | 主要无故障证据候选；精确检查producer时间线、资源与事实 |
| F1三次、F2 exact三次、F3三次 | REUSED_ACCEPTED_PROOF | 交叉核对原始接受链；有故障，排除healthy baseline |
| F4 combined | REUSED_ACCEPTED_PROOF | 引用F2等既有run，不新增计数 |
| Kill-under-load | ACCEPTED_PROOF | 原接受有效；Kill transition不作为持续健康对照 |
| 早期S attempts、失败C1/C3、targeting/export/input失败 | SUPERSEDED/FAIL | 保留原状态，不纳入校准 |
| L6 readiness/freshness/sampling短跑 | 原范围接受或失败 | 不是L5-B calibration；`plannedOrderRatePerMinute=2`不是25%来源 |

最终聚合20行仍为ACCEPTED=1、REUSED=19、missing/invalid=0。共定位16个不同已接受运行，**16 raw + 16 log SHA-256全部匹配**。S1–S3从已登记日志的L5_ROOT唯一定位；C/F/Kill沿用rawLocator。完整runId、源路径及预期/实测哈希见 [机器分析](L6_FORMAL_CALIBRATION_MANIFEST_CLOSURE.json)。本轮不重开已有正确性接受，也不把接受范围扩大成calibration接受。

## Healthy-rate derivation

| Run | producer起止，epoch ms | active间隔 | 该间隔新Order行 | 停产后fairness快照Trade数 | 原始资源/进度样本 |
| --- | --- | --- | --- | --- | --- |
| C1 | 1789108581275 → 1789108594559 | 13.284s | 60 | 58 | 285 / 138 |
| C2 | 1789108734698 → 1789108757858 | 23.160s | 180 | 170 | 694 / 149 |
| C3 | 1789115221231 → 1789115246793 | 25.562s | 180（含策略） | 176 | 748 / 163 |

这些数值直接读取哈希匹配raw，不使用Markdown表格推算。三个run先预置60个持续未成交候选，再开始300ms波次producer和并发reconcile；末尾才FILL全部并drain。C1是2 actors，C2/C3是4 actors；C3还包含策略扫描及等待。producer有显式wall-clock边界，但没有预先冻结的稳态完成窗口/选择规则及成套monotonic边界。正常slot背压可从源码及部分queue样本理解，没有完整pause/resume分类时间线。

停产后fairness snapshot在producerStop之后获取，不能冒充stop瞬间的全链完成计数。DB created_at不是独立提交完成时刻；Venue nanoTime来自另一个JVM，没有与controller epoch时钟配对。原始events没有created_at字段，其按created_at时间窗计数在机器分析中为null；实际58/170/176个event由fairness事实保留，不能把缺少时间戳记为0。旧S1–S3则先集中PLACE、之后统一FILL/drain，未记录producer起止，不能提供连续到达与完整链完成的稳态对照。

因此无法从已接受定义唯一选择 `N_completed / steady_seconds`；producer到达率、Trade行时间窗速率、stop后fairness快照速率与全链持续完成率不能互换。**healthySustainedRate=null；l6ArrivalRate=null**。唯一保留公式为 `r_L6 = r_H × 0.25`，单位orders/s；同时要求r_L6≤1、并发target=2/hard max≤4。没有计算whole-run速率、没有采用2/min、没有裁剪25%结果到1/s。置信结论是“证据不足”，不是“健康速率为0”。

## Noise-band derivation与采样兼容性

旧L5实际actor采样为250ms fixed-delay、per-PID、wall clock；进度/PG/Venue由另一个250ms fixed-delay采样器读取。当前L6为10s fixed-rate统一sampleIndex/sampledAt/elapsedMillis/phase，并校验当拍token，包含新增metrics HTTP线程、scheduler和采集连接。不能用抽取每40条旧样本模拟当前10秒采集，也不能用后来readiness的资源样本回填L5。

| Resource | 原raw可用内容 | 正式处理 |
| --- | --- | --- |
| heap/GC low-water | 6个无故障run的resources均无heap/GC字段 | CALIBRATION_REQUIRED |
| threads | ThreadMXBean标量存在，旧actor/timer/采样线程拓扑不同 | NOT_COMPARABLE_WITH_CURRENT_SAMPLING |
| Hikari active/idle/pending/total | 旧MXBean标量存在，缺timeout counter；同负载同采样条件不成立 | NOT_COMPARABLE_WITH_CURRENT_SAMPLING |
| PostgreSQL connections | 缺当前全databaseConnections与observer ownership口径 | CALIBRATION_REQUIRED |
| handles / FD | 无逐PID/generation同方法时间序列 | CALIBRATION_REQUIRED |
| command queue | 旧ReentrantLock等待队列；当前commandExecutor队列，owner不同 | NOT_COMPARABLE_WITH_CURRENT_SAMPLING |
| Venue queue | executor标量存在，旧observer流量/周期及负载不同 | NOT_COMPARABLE_WITH_CURRENT_SAMPLING |
| temp count/bytes | final rawBytesBeforeProof不能还原逐拍变化 | CALIBRATION_REQUIRED |
| log growth | 日志存在且hash匹配，但无逐拍文件大小记录 | CALIBRATION_REQUIRED |

已按原producer窗口、原PID分别重算历史min/max/max-min/sampleCount，并把全阶段统计单列，详见机器分析historicalObservations。这些字段均标记formalNoiseBand=false。例如C1两原actor各51个producer样本：threads=20..20、active=0..1、pending=0..0、total=10..10、commandQueue=0..1；其producer进度样本Venue queue=0..0。**这些只是历史观测，不将threads=20或零波动冻结为L6阈值**。替代JVM和setup/drain样本没有混入producer窗口。全部正式noiseBands数值保持null。

## minimalCalibrationRunRequired

Dynamic calibration required：**YES**。以下是下一任务的最小有界建议，`PROPOSED / NOT_EXECUTED / NOT_FROZEN`；不是本轮现场定义并使用新速率或噪声阈值，也不保证一次就获得足够自然GC样本。

- Composition：相同已接受production、1 owned PG16/V51、1 Synthetic Venue、2 NQ actors、1 controller；loopback、无credentials/provider、fault=0、restart=0。沿用当前10s sampler原字节与资源owner拓扑。
- Duration：建议硬上限900s：120s warmup → 300s健康测量 → 60s停止并收敛 → 300s匹配L6负载noise control → 120s quiet drain。前后阶段分开，不能冒充L6 60min；自然GC/同方法样本不足时仍BLOCKED，不在同run延期。
- Arrival pattern：健康段建议全局250ms最小间隔、两actor轮换、offered ceiling≤4/s，每actor最多1条pending command；背压即暂停且不补发赶拍，保持真实RiskGate。4/s只是未来输入上限建议（满足25%后≤1/s的代数边界），不是已测healthy rate。持续正常FILL/reconcile，不保留人为60订单前缀。
- Rate proposal：在健康段预先固定3个100s窗口，逐identity记录入场及全链完成；`r_i=N_i/actual_monotonic_seconds_i`，建议`r_H=min(r_i)`。边界carry单列、不得重复；拒绝/背压完整分类。这是一条需要在未来run前固定的新前瞻规则，不能追认成旧L5已接受定义。只有数据充分时将r_H×0.25用于下一匹配负载段；不能clip或默认2/min。
- Sampling/noise：当前10s统一当拍采样，900s名义90拍，noise段30拍、quiet段12拍；沿用2s启动延迟、8s采集预算。按owner/PID generation、相同负载区间计算min/max/max-min/count；heap关联重复自然GC低谷，禁止强制GC。合法log/temp/audit累积要保留原值并按业务/时间归一，不把证据文件必然增长当泄漏或要求其回到0。
- Safety：建议orders≤2000（上述输入上界最多1980，仍在L6规划3000内）、fills/order≤4、raw≤1GiB；沿用进程/PG内存限制、总预算≤入口可用内存60%、磁盘保留≥2GiB且≥入口20%。命中边界停产、drain、保留证据；不扩并发、放宽风控或重复故障证明。
- Exact outputs：预先冻结parameters、candidate/fixture/sampler hashes；phase timing；逐identity arrival/completion、backpressure timeline；原10s resources.ndjson；自然GC与noise逐样本索引；rate-calculation；原业务oracle事实与cleanup；artifact SHA-256。只有所有字段充分，才生成唯一canonical manifest；否则继续输出null和缺口。

**CALIBRATION_HARNESS_CHANGE_REQUIRED**：当前L5入口只有S/C固定订单数及旧250ms采样；当前L6DurationContract只有15/90/10和600/2400/600，L6ActiveStabilityTest固定每分钟2策略与240订单预算，未提供上述校准阶段/速率/全链时间线入口。没有一个现有命令能够满足该建议，本报告不虚构可执行命令。下一步最小no-fault calibration前需要限定test-only orchestration缺口并绑定候选；生产和当前sampler可复用，新增harness必须有独立hash，不能仍宣称整个候选为未变的1208abd9。本轮不修改harness、不运行该calibration。

## Validation与收尾

机器文件记录所有source hashes、run IDs、原始窗口、单位、历史观测重算结果、采样兼容性、missing fields及完整minimalCalibrationRunRequired。32个raw/log哈希均匹配；原始JSON中的时间差、逐PID min/max/max-min与样本数已独立复算，未插值或使用失败来源。一次离线诊断曾将events缺失created_at错误计为0，最终已改为null，并保存analysis-correction；原始数据不变，也没有因此产生任何accepted rate。

新增文件仅 [本报告](L6_FORMAL_CALIBRATION_MANIFEST_CLOSURE.md) 与 [机器分析](L6_FORMAL_CALIBRATION_MANIFEST_CLOSURE.json)；分析工具和原始核对结果位于 `backend/nq-app/target/l6-calibration-closure-20260913-01/`，逐文件SHA-256见机器分析。未创建canonical运行manifest。

所有既有Git可见文件与被分析raw/log均保持原字节，production/test/harness/config delta=0，stage=0、commit=NONE、push=NONE；git diff --check/stat/name-only/cached name-only为空。没有新运行资源需清理，没有重跑L4/L5/readiness/60min/180min。未报告新生产P0/P1，既有残余不重评。

Final decision：**BLOCKED / L6_FORMAL_CALIBRATION_EVIDENCE_INSUFFICIENT / MINIMAL_CALIBRATION_RUN_REQUIRED**。下一动作仅为补齐上述最小no-fault calibration及必要harness入口；本轮停止，禁止直接进入60min。
