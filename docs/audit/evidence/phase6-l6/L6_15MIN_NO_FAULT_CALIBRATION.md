# L6 15min No-Fault Calibration — Attempt 02

**PASS / L6_15MIN_NO_FAULT_CALIBRATION_ACCEPTED / FORMAL_CALIBRATION_MANIFEST_ACCEPTED**。

本轮从 T=0 完整运行 300s warmup + 600s measurement，冻结 healthy sustained rate、25% arrival rate 和资源噪声带。RunId=`dcd6bf4c-3053-4fff-9ff5-ed3c526daf8a`。**L6 仍 NOT_ACCEPTED，60min/180min 均未启动**。

## 候选、容量与执行

Branch=`audit/post-gatey-agent-baseline`，HEAD/upstream=`d94f8512108fd7f8e9cbdeee14cfc8b064f2d5d8`，tree=`0c010b7d80327f5e8ec994cd6a7073e6853fe3ea`。实时只读核对 [exact-head CI 34753536151](https://github.com/ling5477/nexus-quant/actions/runs/34753536151)，completed/success，9/9 SUCCESS。4002 个起始 Git 可见文件在运行后、证据打包前逐字节一致；production/test/harness/sampler/analyzer/config/threshold/Flyway/AGENTS/Skills delta=0。

正式 qualificationMode=`L6_CALIBRATION`，原始 harness mode 字段为 `CALIBRATION`，smoke=false。`QualificationCapacity.start` 在 fixture/业务订单/运行资源创建前校验容量；business orders=0 时即成立：runOrderBudget=600 ≤ Venue logical capacity=600 ≤ L6 global safety cap=3000。L6 专用 Venue 入口明确绑定 600；L5 默认 300 未参与本次 ceiling 判定。原始 parameters 和源文件哈希绑定在 manifest。

拓扑：Windows / Java21.0.9；1 owned PostgreSQL16.15 / Flyway V51（51 migrations validate），1 Synthetic Venue，2 NQ actors，1 controller。仅 loopback SIM fixture，无 fault injection、运行期 Kill、restart、delay/drop、DB fault、LIVE、真实 provider 或生产 DB 操作。

实际只执行一次：

```powershell
$env:MAVEN_OPTS='-Xmx512m'
mvn -o -f backend/pom.xml -pl nq-app -am test '-Dtest=L6CalibrationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6.calibration=true' '-Dnq.l6.calibration.smoke=false' '-DargLine=-Xmx512m'
```

Maven BUILD SUCCESS，JUnit 1 test / 0 failures / 0 errors / 0 skipped。运行完成后原 `l6_calibration_analyzer.py` 自动执行成功，重验全部 181 个 checkpoint。没有修改校准公式、sampler、analyzer、阈值或容量合同；没有重跑 Full Maven、L4/L5 或正式 L6 soak。

## 时间、完整链与速率

T0=`2026-09-13T11:19:37.501960700Z`。单调时间：warmup 从 1ms 至 300007ms，actual=300006ms；measurement 从 300007ms 至 900012ms，actual=600005ms。算法固定窗口为 `[300000,900000)`ms，边界暂停 0.007s 已排除。UTC measurement 边界由 T0 加单调偏移得到，不伪称额外采集的 wall-clock 时刻。

| Measurement 指标 | 结果 |
| --- | ---: |
| ordersAdmitted / ordersTerminal / fills | 240 / 240 / 240 |
| Trade / TradeExecuted / ledgerCompleted chains | 240 / 240 / 240 |
| Ledger entries | 960 |
| fullChainCompleted | 240 |
| producerActiveSeconds / eligibleSteadySeconds | 427.059 / 427.059 |
| producerPausedSeconds | 172.941 |
| explicitBackpressureSeconds | 172.934 |
| 10 个一分钟区间完成链 | 每分钟均为 24 |
| 最大相邻完成观测间隔 | 7057ms |
| 最长单次暂停 | 2254ms |

Warmup 完成 120 条全链，measurement 完成 240 条，总计 360。计数按已记录 admission/completion identity 与完整关系 oracle，覆盖 Order→Venue→Fill→Reconciliation→Trade→TradeExecuted→Ledger→Position/Snapshot；没有将单独 Order/Trade 数冒充全链。原始算法排除 warmup、窗口外完成和暂停内完成，240 个正式身份全部符合。

健康率采用已交付 `L6CalibrationEvidence.export`：`240 / 427.059 = 80000/142353 = 0.5619832388499013 orders/s`。背压由短时有界收敛等待构成，未出现长期连续背压；暂停总量少于窗口一半，原算法 rawRateEvidenceValid=true。所有资源采样 backlog=0，所有已完成 checkpoint backlog=0。两个 actor 的 tickCompleted 分别从 60→178、59→177；reconciliation success 均从 121→325，持续推进。

25% raw rate=`20000/142353 = 0.14049580971247533 orders/s`，低于 1 order/s，故 finalL6ArrivalRate 相同。精确 pacing interval=`142353/20000 seconds = 7.11765s = 7117650000ns`。分数为 authoritative exact representation；JSON 小数保留 binary64 round-trip 表达，不额外取整。间隔可由整数纳秒精确表示。

## 统一采样与资源噪声

唯一已交付 `L6ResourceSampler/L6RuntimeResources`，interval=10000ms。总计 90/90 拍：warmup 30，measurement 60（index 30–89）。mandatoryMissing=0、staleReuse=0、cadenceViolation=0；最大启动延迟16ms、最大采集耗时392ms，满足原 2000ms/8000ms 合同。没有补拍、插值或历史 attempt 数据复用。

Heap 仅用同 PID、同 G1 Young collector 的自然 GC 后 heapUsedAfterGc；按 uptime 将 GC 实际事件定位到 measurement，按 PID/collector/id 去重。没有以普通 heap min/max 替代，也没有强制 GC。原 analyzer 的全部 GC 观察继续保留，Concurrent GC 不混入下表 Young band。

| Owner / 资源 | sampleCount | min / lowWaterMin | max / lowWaterMax | range |
| --- | ---: | ---: | ---: | ---: |
| nq0 post-Young-GC heap，bytes | 25 | 53248800 | 53798664 | 549864 |
| nq1 post-Young-GC heap，bytes | 24 | 46012184 | 51079224 | 5067040 |
| 两 actor threads（各自） | 各60 | 23 | 23 | 0 |
| 两 actor Hikari active/pending（各自） | 各60 | 0 / 0 | 0 / 0 | 0 / 0 |
| 两 actor Hikari idle/max（各自） | 各60 | 10 / 10 | 10 / 10 | 0 / 0 |
| PostgreSQL app/all connections | 60 | 20 / 22 | 20 / 22 | 0 / 0 |
| Windows owned handles，总计4进程 | 60 | 2470 | 2489 | 19 |
| 两 actor commandQueue（各自） | 各60 | 0 | 0 | 0 |
| Venue queue | 60 | 0 | 0 | 0 |
| owned temp file count | 60 | 74 | 192 | 118 |
| owned temp bytes | 60 | 59825168 | 700092346 | 640267178 |

全部 heap used/committed/max、GC count/time、queues、Hikari、PG、handles、backlog/age、scheduler/reconciliation、audit/event/log/temp 与 ownership 序列保存在 raw。Windows FD 为平台互斥 NOT_APPLICABLE；实际 handles 已测量。采样中 idle-in-transaction=0..1，结束为0；观察连接采用既有 repeatable-read 业务 checkpoint，不把瞬时观测事务写成结束遗留。

增长统计使用正式 10s 样本 index30→89 的同一端点（约300s→890s），对应新增236个 Order与已观测全链；不伪造900s资源样本或将缺少的尾10秒资源值插值。完整600s业务计数240另行保存。

| 增长资源 | absolute growth | per-order | per-full-chain |
| --- | ---: | ---: | ---: |
| log bytes | 224727 | 952.2330508474577 | 952.2330508474577 |
| audit rows | 84196 | 356.76271186440675 | 356.76271186440675 |
| event rows | 1888 | 8 | 8 |

增长型指标保留原值与逐拍 delta。日志、audit/replay 与 frozen checkpoint 证据的正常累积不直接判为 leak；temp 字节与文件数量包括主动保留的原始证据。噪声带描述本次负载与采样方式，不声称另做了25%负载噪声实验或长期 leak 验证。

## 正确性、drain 与清理

181 个完整关系 checkpoint 全部通过（60 warmup、120 measurement、1 cleanup）。总计360 Order/fill/Trade/TradeExecuted、1440 Ledger entries、720 AccountSnapshot；Position BTC=36，最新 Snapshot 可按 snapshot_id 重建。duplicate external mutation/Trade/TradeExecuted/accounting=0，FILLED→FILLED error=0，unauthorized send=0，durable orphan=0，Trade/fill identity exact，Ledger balanced。P0=0、P1=0 仅限本轮 qualification。

所有90拍 Hikari acquisition timeout delta=0，recovery tickFailed=0。首次 Validation refresh 前的前三拍 warmup 尚无 qualification；其后及全部60拍 measurement 和 cleanup 的 unexpectedDegradation=0。唯一预期降级为既有 EVALUATION_ARTIFACT_PREVIEW 缺项，没有扩大 allowlist。

producer 于900012ms退出循环，bounded drain=794ms；cleanup 与最后measurement均360订单。最终 actionableBacklog=0、correctnessRequiredUnresolved=0、oldestCandidateAge=NONE/null、commandQueue=0、VenueQueue=0、Hikari pending=0、idle-in-transaction=0。没有额外的长 quiet interval；quiet/cleanup 证据限定为短 drain、终态核对及进程关闭后的文件稳定性。

Owned controller29556、Venue29516、NQ25696/29236均退出；owned PG container=`6b665d5ce909b4d7bf640420645cfaf352f56b134b8652383fd863224e0095fb`不存在。harness 端口释放与容器清理断言通过，host只读复核无survivor。日志 `B0_KILLED` 是measurement/drain之后的Venue资源回收，不是本轮运行期Kill/fault注入。原始证据保留，不要求证据文件回到0。

## 证据与边界

- [Canonical manifest](L6_FORMAL_CALIBRATION_MANIFEST.json)：exact HEAD/tree/CI、rate与精确pacing、noiseBands、sampling、correctness、cleanup、raw hashes、limitations。
- [本轮机器汇总](metrics/dcd6bf4c-3053-4fff-9ff5-ed3c526daf8a/summary.json)。
- [原始运行结果](runs/dcd6bf4c-3053-4fff-9ff5-ed3c526daf8a/calibration-run.json)、[全链进度](runs/dcd6bf4c-3053-4fff-9ff5-ed3c526daf8a/progress.ndjson)、[checkpoint索引](runs/dcd6bf4c-3053-4fff-9ff5-ed3c526daf8a/checkpoints.ndjson)。
- [统一资源序列](metrics/dcd6bf4c-3053-4fff-9ff5-ed3c526daf8a/resource-samples.ndjson)、[原 analyzer 输出](metrics/dcd6bf4c-3053-4fff-9ff5-ed3c526daf8a/calibration-analysis.json)。
- [Attempt01原文保全](attempts/3408f2f2-b017-4239-b005-a721a278bb54/L6_15MIN_NO_FAULT_CALIBRATION.md)：历史 BLOCKED 状态及其 raw 不变，不追认 PASS。

本轮198个原始文件、730981899 bytes，全部逐文件SHA-256绑定，完整checkpoint JSON、actor/Venue日志/参数仍位于 ignored `backend/nq-app/target/l6-calibration/dcd6bf4c-3053-4fff-9ff5-ed3c526daf8a/`。Maven/XML、CI原响应、起止fingerprint及打包记录在 ignored `backend/nq-app/target/l6-formal-calibration-20260913-attempt02/`。原 run/analyzer 的 pending acceptance 标识原样保留；本轮 qualification 接受层单独记录，不修改 raw。

STATUS 较早 machine authority 不在本轮改写；本次隔离 calibration 授权来自当前用户请求，不扩张 production/LIVE 权限。历史 P2/P3 不重评。仅本轮 evidence/manifest 变化，stage=0、commit=NONE、push=NONE。

Final decision：**PASS / L6_15MIN_NO_FAULT_CALIBRATION_ACCEPTED / HEALTHY_SUSTAINED_RATE_FROZEN / L6_25_PERCENT_ARRIVAL_RATE_FROZEN / RESOURCE_NOISE_BANDS_FROZEN / FORMAL_CALIBRATION_MANIFEST_ACCEPTED / CORRECTNESS_PRESERVED / P0_0 / P1_0 / L6_NOT_ACCEPTED / READY_TO_RESTART_L6_A_60MIN**。

下一动作仅为 `NQ-GATEAUDIT-PHASE6-L6-ACTIVE-STABILITY-60MIN-QUALIFICATION`，必须另从T=0按10m warmup / 40m active / 10m drain并直接读取本次manifest执行；本轮没有启动。
