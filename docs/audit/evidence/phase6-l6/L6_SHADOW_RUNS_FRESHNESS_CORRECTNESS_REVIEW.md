# L6 Shadow Runs Freshness Correctness Review

2026-09-13（Asia/Shanghai）。**PASS / L6_SHADOW_RUNS_FRESHNESS_CORRECTNESS_REVIEW_ACCEPTED / READY_FOR_PRECISE_DELIVERY**。

本次独立审查范围内 P0=0、P1=0。SHADOW_RUNS freshness、Validation fail-closed、mandatory measurements、formal 10/40/10 contract 均通过；readiness 已独立实际执行。`L6=NOT_ACCEPTED / SOAK=NOT_RUN`。本报告不授予 Git 发布、正式长时运行或真实交易权限。

## 独立性与候选身份

本审查会话没有参与该候选的 production、tests、oracle 或实现方证据编写。实现方结论只作为待核验输入；独立读取真实调用路径、生成清单、执行测试及重算业务事实。本轮执行依据为用户提供的 review charter 和当前项目交易证明合同。历史 governance review 的 `.agents.review-subject` 目录隔离适用于治理候选；本轮 AGENTS/Skills 未变，不是该场景。

| 项目 | 入口与结束核验 |
| --- | --- |
| 工作目录 | `E:\Project\nexus-quant-gateaudit` |
| branch | `audit/post-gatey-agent-baseline` |
| HEAD | `70d9d183046635aff1f81bf79bba439713b2cf9a` |
| staged | 0 |
| `git diff --check` | exit=0；仅现有 Git LF→CRLF 提示，无空白错误 |
| 入口未提交文件 | 20：11 code、9 evidence |
| 全部 Git 可见文件 | 3951；各文件原始字节 SHA-256；原始文件在本轮结束全部不变 |
| 实现方 candidateFiles 对比 | 11/11 匹配，mismatch=0 |
| 本轮唯一新增 Git 可见文件 | 本 Markdown |

独立 raw 目录：`C:\Users\Lingyu\AppData\Local\Temp\nq-l6-freshness-review-20260913-014951`。`initial-inventory.json` SHA-256=`32d09a135e6e7223f410a2e79cddb18c91bc616fcd41c623e3da6110a138adaf`；`candidate-inventory.json` SHA-256=`b239918134e4f80b6b01570c0d5f412744a223e1e27398c45245cb86df4741fe`。两者是工作区原始字节清单，不是 Git tree 或 CI identity。以下完整入口 inventory 包含原有证据，不暗示其中每份旧证据已获 precise staging 授权。

| Candidate path | 独立 SHA-256 |
| --- | --- |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6ActiveStabilityTest.java` | `03ed9817e8b9ad7d9087929f90debe41f76aea8b956328c37aa8d8277fae6064` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6DurationContract.java` | `3a771bd643bfc9df7efd9ad2a852a06a1f4683902c3c550790bb444e6c5e4613` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6MeasurementPostgresTest.java` | `83ea4e48f7adafb2c27232044e1a853ed9a1d3e2d09a181737c3b14fbeb8b8a4` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6Measurements.java` | `d93d37522757b0da93da916da3413b4939b322d27c0fbd096c1b374a931133b5` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6QualificationControls.java` | `18b56a795c82c65e24dd97d513491aa1b30d39f1ef8cd8d0229879b612961ddc` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6ReadinessContractTest.java` | `f4c61f6c2d5cdd0222ebad07456b97a3263847a18170c69f5859e277ca15e260` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6ValidationContract.java` | `32c38cebd8c3ec084a62009d1a3cfe4e7b3b8391a294f39648a1141561c027c1` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6ValidationFixture.java` | `ccf81e43941d85830341b7bed0bc4bbbf7df29a9337132a985ef9bb4c7a842b0` |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunOverviewQueryService.java` | `2984c7a63a3f59ae939a0e8f8a1f4f16311071779cb398dc4ae0a54e35b9e435` |
| `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/readmodel/ReadModelEvidenceMetadataCalculatorTest.java` | `b989914cd8579fb6015df33c01ad9dfa76c521cd180ed4bd159e510e9e132336` |
| `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunOverviewQueryServiceTest.java` | `5138087126f0ae8e76058cb2ae3b0e22ae25c94bc8e4e3d7609cef31bade03f3` |
| `docs/audit/evidence/phase6-l6/L6_ACTIVE_STABILITY_READINESS_RESUME.json` | `684662469637f8ee2624764988a80245bda3f1f9913cc8cb10cb9a1e7f40dbbd` |
| `docs/audit/evidence/phase6-l6/L6_ACTIVE_STABILITY_READINESS_RESUME.md` | `370ffd1751651aee1d4618770c4b3182d869e3fa111ddb5701a1792bbc0b58e0` |
| `docs/audit/evidence/phase6-l6/L6_MANDATORY_MEASUREMENT_AND_TIMER_CLOSURE.json` | `3c29fb38ab20ab045176124e290fcd3aa5b6ae425b84996c39be5cca0f2fef23` |
| `docs/audit/evidence/phase6-l6/L6_MANDATORY_MEASUREMENT_AND_TIMER_CLOSURE.md` | `ef30ffc433804bf42bbaea0ffdd80e4abdac0f3aa9dfba181ca9a75710ef1cd8` |
| `docs/audit/evidence/phase6-l6/L6_SHADOW_RUNS_FRESHNESS_AND_READINESS_CLOSURE.attempt-01.json` | `cc6b2934517404bee9ed78a5f78db94acf192f69a9506c262a3f03d1ddb8c8e3` |
| `docs/audit/evidence/phase6-l6/L6_SHADOW_RUNS_FRESHNESS_AND_READINESS_CLOSURE.attempt-01.md` | `5e9fd2f3d370b3f7b0a4ac83039484eb010b6d88949337fbd45477534c0dbd27` |
| `docs/audit/evidence/phase6-l6/L6_VALIDATION_CONTRACT_AND_MEASUREMENT_CLOSURE.json` | `540142624949be0a2ee822138a5a51c8a5ae297195e0292794cc70fe13d5b894` |
| `docs/audit/evidence/phase6-l6/L6_VALIDATION_CONTRACT_AND_MEASUREMENT_CLOSURE.md` | `43b2dfed91856179b8e364ec474f7bb6aa987ac93c3ca0f990ac24e4850d7abf` |
| `docs/audit/evidence/phase6-l6/RECONCILIATION_SAME_STATE_CONVERGENCE_DELIVERY_ACCEPTANCE.md` | `605ff2fbeb122d5def94951840c2bf112804207f81e4fda79bd2f5b99a2ba233` |

已读取 [实现方 attempt-01](L6_SHADOW_RUNS_FRESHNESS_AND_READINESS_CLOSURE.attempt-01.md) 与 [JSON](L6_SHADOW_RUNS_FRESHNESS_AND_READINESS_CLOSURE.attempt-01.json)。原失败、原 readiness/measurement 证据及 same-state delivery acceptance 原字节保留。STATUS 的较早 L4/L5 planning 机器区块没有被当作本轮授权来源，也没有修改或据此扩展运行权限。

## 检查文件与调用路径

- Production delta：`ShadowRunOverviewQueryService` 仅增加 Duration import、七天 typed constant 及 calculator 实参；没有修改查询、availability、severity、安全 flags 或交易写侧。
- Freshness：`ShadowRunOverviewQueryService.overview → ShadowRunOverviewQueryPort.loadOverviewFacts → JdbcShadowRunOverviewQueryRepository → ReadModelEvidenceMetadataCalculator`；检查 `ShadowRunRunnerService`、`ShadowConsistencyReportService`、`JdbcShadowRunFactRepository` 与 `JdbcShadowRunIllegalTransitionAuditWriter` 的写入时间来源。
- Policy：`ShadowValidationWorkflowOverviewQueryService`、`JdbcShadowValidationWorkflowOverviewQueryRepository`、`ConsistencyEvidenceOverviewQueryService`；GateT-2 WO 的 evidenceFreshness 及七天 policy 段落。
- Aggregate：`ValidationOperationsRuntimeEvidenceOverviewQueryService → 五来源 public overview`，`ValidationEvidenceRefreshService.refresh`、`PythonEvaluationArtifactPreviewOverviewQueryService`、`L6ValidationContract` 与对应 negative tests。
- Measurement/readiness：所有 8 个 app candidate files，`L6NqProcessMain → B0NqProcessMain → L6QualificationControls`，`OkxRestReconcileService.reserveReconciliationCandidates` 调用实参、`L5BoundedWorkloadTest.BACKLOG`、`B0Fixture/B0Processes`、`l6_oracle.py` 及 Maven 配置。

## A. Freshness authority 与 timestamp semantics

`SHADOW_RUNS` 是 production aggregate 固定五来源之一，owner 为 `ShadowRunOverviewQueryService`，source id=`LOCAL_DB_SHADOW_FACTS`。查询只读，不启动 runner、刷新 snapshot/report 或写业务状态。

| durable fact | canonical 时间及实际查询 |
| --- | --- |
| run | `shadow_runs.updated_at`；按 updated_at 降序选 latest run |
| consistency report | `shadow_consistency_reports.generated_at`；不以较新的 created_at 替代生成时间 |
| event | `shadow_run_events.created_at`；事件实际持久化的来源时间 |
| snapshot | `shadow_run_snapshots.captured_at`；不以较新的 created_at 替代捕获时间 |

owner 沿用四类 latest fact 时间的最大值。Report/snapshot 的 created_at 仅用于同一 canonical 时间的排序 tie-break，不参与 freshness 时间选择。顶层 overview `generatedAt=clock.instant()` 仅表达本次响应生成；calculator 使用 clock 计算 age，不将其当作 lastCalculatedAt。

run 的 updated_at 来自合法状态 CAS 的实际持久化更新；report generated_at 来自真实本地比较结果写入；snapshot/event 来自实际记录生成。JVM clock 在写侧记录事实发生时刻与在 query 中伪造 freshness 是不同操作，本候选没有后者。非法 Shadow transition 也可能产生新的 durable diagnostic event；新的诊断事件可以推进“最近诊断事实”时间，不代表运行成功或使旧 report 的逐项 freshness 变新。这与现有 source 的 latest-fact 定义一致。

本 source 的 FRESH 表示最新本地诊断证据仍在窗口内，不表示全部历史 run/report 均新鲜、dataset 内容新鲜、Shadow 已完成、比较成功或获准交易。新的 report/event 与较旧 run 并存不是错误地选择 query time；NOT_COMPARABLE/FAILED 等业务诊断继续由原 severity/limitations 字段表达。本次未将 FRESH 解释成这些额外业务承诺。

阈值=`Duration.ofDays(7)=604800 seconds`，现有 `ShadowValidationWorkflowOverviewQueryService` 与 `ConsistencyEvidenceOverviewQueryService` 已使用相同窗口。[GateT-2 WO](../../../gates/gate-t/source/GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md) 第265–271行规定 latest report/snapshot/event freshness 并允许复用 GateT-1 七天窗口。不是本轮凭空取值，未从 scheduler 读取周期或 fixture 寿命推导。

共享 calculator 未改：`Duration.between(sourceTime, now).getSeconds() > staleAfter.getSeconds()` 才 STALE，保持整秒语义；604800 秒为 FRESH，604801 秒为 STALE。缺事实=UNAVAILABLE/UNKNOWN；缺 report/snapshot 等完整性不足=PARTIAL/UNKNOWN；缺时间、未来时间、无阈值均 UNKNOWN，负阈值拒绝。`staleRuns` 仍是缺 snapshot/report 的完整性计数，不能当作时间 stale 计数。

除了 owner 单测中的重复查询/未来事实/混合事实时间检查，本轮直接从正在执行的 owned PostgreSQL 只读获取四表 max 时间，保存 `durable-shadow-times.json`。最大值为 `2026-09-12T17:52:29.759210+00:00`。两个 NQ JVM 经真实 scheduler/aggregate 输出的 SHADOW_RUNS lastCalculatedAt 均精确等于该值；响应 generatedAt 分别较晚，age=33/37秒，threshold=604800，AVAILABLE/FRESH。持久化时间没有被 readiness 开始时间或响应时间覆盖。

## B. Validation aggregate fail-closed

生产 aggregate 仍为 PARTIAL/UNKNOWN，scheduler 仍报告 DEGRADED 并保留 no-file blocker；本轮没有改为 SUCCESS。L6 eligibility 独立核对全部五个来源、唯一键、完整集合、每个来源 availability/freshness 与 aggregate 状态。

唯一允许缺口精确匹配 `EVALUATION_ARTIFACT_PREVIEW / LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW / UNAVAILABLE / UNKNOWN / lastCalculatedAt=null / ageSeconds=null`，disposition=`EXPECTED_UNAVAILABLE / DEFER_UNTIL_TRIGGER / NOT_REQUIRED_FOR_L6`。其余四源必须 AVAILABLE/FRESH 且有事实时间。

独立执行的负例覆盖每个必需来源的 UNKNOWN、UNAVAILABLE、PARTIAL、AVAILABLE+UNKNOWN、STALE，以及额外/重复/缺失来源、错误 no-file 来源键、scheduler failure 与未完成。均拒绝；无 `aggregate==PARTIAL → pass` 或等价宽泛 allowlist。实际 readiness 两份逐来源输出验证只有登记的 no-file 来源不可用，其余四源均 AVAILABLE/FRESH。

## C. Mandatory measurements

- `commandQueue`：`L6QualificationControls.handle` 的真实命令通过单 worker、容量1、AbortPolicy 的 bounded executor。queueSize/active/completed/capacity 直接读取该 executor。独立测试实际阻塞 worker、排队第二命令、拒绝第三命令、释放并完成；不是常量0。readiness 每 actor 13个资源样本，最终 queueSize=0/MEASURED_ZERO、completed=73/74。
- 状态区分：观测到零=MEASURED_ZERO；不适用的 Windows FD 项按既有 NOT_APPLICABLE_WINDOWS 表达；executor/metric/mandatory 字段缺失=UNAVAILABLE 并阻断，不将 NOT_APPLICABLE 用来豁免这三类必测项。
- candidate age：拦截真实 `reserveReconciliationCandidates` 的 status 实参并继续原调用；使用相同 OKX venue 和状态集合，再应用既有 L5/L6 actionable backlog 谓词。年龄来自候选 `orders.created_at` 与数据库 statement_timestamp 差，不把所有扫描到的终态历史行算作待处理。已读取生产状态集合与 backlog owner；这表达本轮 actionable 候选，不声称覆盖所有未来工作类型。
- PostgreSQL 独立负/正例：空集合 NONE/null；真实 eligible 行年龄≥2秒；更老但不在扫描状态集合的行排除；eligible 消失恢复 NONE/null；未捕获 eligibility 拒绝。真实 readiness 共46个 actor age样本，正值范围91–264ms，最终 scanEligible=6、actionable=0、age=null/NONE（两 actor相同）。没有用0ms替代无候选。
- Hikari：精确查询实际 pool tag 上唯一 `hikaricp.connections.timeout` Counter；缺失或不唯一抛错。独立 PG 连接池容量1耗尽测试观测真实 Counter 0→1；未注册 Counter 负例拒绝。readiness 两池 timeout delta 均0。
- `requireMandatory` 对序列化 JSON 的必需字段、数值、queue状态、age状态做检查。独立执行逐字段删除、缺 age值及 UNAVAILABLE queue 负例，全部拒绝。对本轮全部26个 raw资源样本又独立检查字段存在及语义，mandatory missing=0。

## D. Formal duration contract

`L6DurationContract.forMode(false)` 固定600/2400/600秒；正式模式累计边界10/50/60分钟，不读取 ordinary duration property。`forMode(true)` 才使用15/90/10秒；proof 显式记录 READINESS、diagnostic=true、115秒，成功结果为 READINESS_MEASURED，不能写成正式 L6-A。

`startedNanos`、phase end、checkpoint interval 及 elapsed 校验均使用 System.nanoTime。currentTimeMillis 只记录观测时间，不控制阶段推进。phase 单测实际覆盖10/50/60分钟前1ns和精确边界，并验证 duration=1 property 不改变正式总长。正式执行期使用绝对 elapsed 边界；它定义阶段调度，真实采样和命令完成的微小耗时仍记录在 actual 字段，不能把固定常量本身当作60分钟资格已执行。

本次短时实测：

| Phase | 累计计划边界 | 实际 phase duration |
| --- | ---: | ---: |
| warmup | 15.0 s | 15.0004875 s |
| active | 105.0 s | 90.0004152 s |
| drain | 115.0 s | 10.0000787 s |

## 独立执行、业务事实与 cleanup

| Suite | tests | failures / errors / skips |
| --- | ---: | --- |
| `L6MeasurementPostgresTest` | 1 | 0 / 0 / 0 |
| `L6ReadinessContractTest` | 5 | 0 / 0 / 0 |
| `ReadModelEvidenceMetadataCalculatorTest` | 8 | 0 / 0 / 0 |
| `ShadowRunOverviewQueryServiceTest` | 4 | 0 / 0 / 0 |
| `ValidationEvidenceRefreshServiceTest` | 3 | 0 / 0 / 0 |
| `L6ActiveStabilityTest` | 1 | 0 / 0 / 0 |

定向合计21 PASS，readiness另1 PASS，均skip=0；不是重跑实现方全部41条。未运行 Full Maven、完整L4/L5、60min、180min或overnight。

实际命令（仓库根目录、Java21、Maven3.9.12）：

```text
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=ShadowRunOverviewQueryServiceTest,ReadModelEvidenceMetadataCalculatorTest,L6ReadinessContractTest,L6MeasurementPostgresTest,ValidationEvidenceRefreshServiceTest -Dsurefire.failIfNoSpecifiedTests=false -Dnq.l6.measurement=true -Dnq.l6=true -Dnq.l6.diagnostic=true -DargLine=-Xmx512m
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=L6ActiveStabilityTest -Dsurefire.failIfNoSpecifiedTests=false -Dnq.l6.measurement=true -Dnq.l6=true -Dnq.l6.diagnostic=true -DargLine=-Xmx512m
python -X utf8 <owned-raw>/verify_results.py
```

Maven 由 owned wrapper 使用 OS/JDK 环境 allowlist 启动，不继承 NQ/Spring/provider/JAVA_OPTIONS/MAVEN_OPTS；子进程复用 B0 cleanEnvironment。隔离 loopback Synthetic Venue、2 NQ JVM、2 strategies、锁定 PostgreSQL16镜像及真实schema V51。PG镜像=`postgres:16@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`；只使用缓存镜像，没有真实 provider 或生产数据库访问。

readiness run=`996a2020-99aa-4460-9e85-5fd680a8bb5d`。测试内使用只读 checker 完整快照和既有关系 oracle；结束后审查者重新执行五个 checkpoint 的 oracle，并独立计算按 client/order/trade/idempotency key 分组的重复数、检查Order终态/version、资源字段和durable source timestamp。

| 最终事实 | 独立核对结果 |
| --- | --- |
| Order / Trade / TradeExecuted / Ledger | 6 / 6 / 6 / 24 |
| Position BTC / latest BTC snapshot | 0.6 / 0.6 |
| duplicate external mutation / Trade / TradeExecuted / accounting | 0 / 0 / 0 / 0 |
| FILLED→FILLED exception / same-state transition audit | 0 / 0；SQL拒绝同态审计，非只检日志 |
| transition audits / duplicate transition audit | 24 / 0；Order均FILLED/version4 |
| orphan / backlog final | 0 / 0 |
| oldestCandidateAge final | NONE/null，两actor |
| mandatory missing / Hikari timeout delta | 0 / 0 |
| owned survivors | 0 |

账务 oracle 同时核对 Trade金额/手续费、TradeExecuted payload关联、每Trade四分录及balance链、LedgerEvent一对一、durable strategy work/effective quantity、Position以及按snapshot_id选取的最终余额。没有用日志、row count或退出码代替这些关联不变量。

已核查 controller PID3992、NQ PID19472/25048、Venue PID36000全部退出；Maven launcher18552/33800已完成。measurement容器`a5d2fb6ac559805248a4c3b2c325f944aa571e8644a33b3456444eb53bef52cd`与readiness容器`d3e6de87c2633e15707871b49575db97396c7ad5d4ecff6cc1d5b2a512469f6d`均不存在；测试内另确认owned端口释放。owned workloads全部清理；raw输出保留在Temp及ignored target，不删除失败或历史证据。

## Proof impact 与 findings

| 既有证明 | 本轮影响与复用结论 |
| --- | --- |
| Same-state convergence independent review | REUSABLE；`b733b75b7d5b018dc2f72f508ba11d1450a3bddb`到当前HEAD无backend差异，本轮生产仅freshness owner；原交易no-op及后续fill/accounting恢复契约没有变更。引用[原独立审查](RECONCILIATION_SAME_STATE_CONVERGENCE_CORRECTNESS_REVIEW.md)及[delivery acceptance](RECONCILIATION_SAME_STATE_CONVERGENCE_DELIVERY_ACCEPTANCE.md)。 |
| L4 accepted proof | REUSABLE within original scope；technical pair `3d103cea2072b3c2d9d1009cc5841c18a958ee80 / 34501806297`保持历史接受身份。后续已接受整改沿原证据链承接，不宣称当前候选逐字节等于L4旧候选，也不重开L4。 |
| L5 accepted proof | REUSABLE within original scope；读取[L5最终Aggregate Acceptance Resume](../phase6-l5/L5_AGGREGATE_QUALIFICATION_ACCEPTANCE.md)的ACCEPTED结论，保留原失败历史；当前read-only freshness与harness measurement不改变原Order/Trade/Ledger/Risk/execution业务契约，不重跑S/C/F/Kill矩阵。 |

本轮 production delta 未逃逸至 Order / Trade / Ledger / Risk / execution semantics；`REVIEW_SCOPE_ESCAPED=NO`。上述复用不提升历史证明的规模、时长、环境或future/inactive覆盖，也不把本轮改过的harness当作旧run当时使用的版本。

历史问题 `SHADOW_RUNS / UNKNOWN / STALE_THRESHOLD_NOT_DEFINED`：**INDEPENDENTLY_VERIFIED / CLOSED**，仅关闭“完整durable事实却缺owner stale policy”这一根因。missing/partial/future/stale拒绝仍有效，原历史FAIL并未改成PASS。

本轮确认新 findings=0，P0=0、P1=0；未发现阻断本候选的freshness、aggregate、measurement或timing缺陷。既有P2 ordinary concurrent INSERT loser、P3 wildcard-import、historical projection repair及inactive/future obligations维持原处置，本轮没有重新定级或清零。

## Raw evidence 与最终决定

- `verified-results.json` SHA-256=`e74197cf6713f2b03ef61bec560351c43914250c1eaa51593e6dd82c695d703c`：XML计数、五个checkpoint及hash、资源/age/validation独立核对、清理和candidate drift。
- `readiness-raw.zip` SHA-256=`6d9235981f1b1dd903455ec1de992583a1cf08901e425d6940c61c6ab62f52ae`：本轮完整proof、checkpoint、progress、candidate-age、resource、validation、process日志。
- `durable-shadow-times.json` SHA-256=`15c2aa0340cc8de269e0962742416a9d44e3a150fa3dc9fdf1dc2a642ca7b6ab`：owned PG只读时间事实。
- `targeted.log` SHA-256=`60a618295922f344c05e2ce30781164194f27ac5983758c64808bbd773d26156`；`readiness.log` SHA-256=`cf8e5acf79e592a54802c128625b8c01b1065dc7768a320f5e235a74bd28809d`。命令/环境键/开始结束/exit code见两份`*-command.json`，XML原件在`targeted-reports`和`readiness-reports`。复核脚本`verify_results.py`只读业务快照与候选，未修改oracle/tests。
- 初始3951个文件逐字节不变；本轮只新增本报告。stage=0、commit=NONE、push=NONE、new exact-head CI=NOT_RUN。

```text
PASS /
L6_SHADOW_RUNS_FRESHNESS_CORRECTNESS_REVIEW_ACCEPTED /
SHADOW_RUNS_FRESHNESS_CONTRACT_ACCEPTED /
VALIDATION_FAIL_CLOSED_ACCEPTED /
L6_MANDATORY_MEASUREMENTS_ACCEPTED /
FORMAL_10_40_10_CONTRACT_ACCEPTED /
L6_READINESS_INDEPENDENTLY_VERIFIED /
P0_0 /
P1_0 /
READY_FOR_PRECISE_DELIVERY /
L6_NOT_ACCEPTED /
SOAK_NOT_RUN
```

唯一下一动作：按precise delivery授权执行精确文件选择 → commit → push → exact-head CI。GREEN后直接进入`NQ-GATEAUDIT-PHASE6-L6-ACTIVE-STABILITY-60MIN-QUALIFICATION`；不再增加readiness review或重复独立freshness review。本轮停在NO_COMMIT / NO_PUSH / NO_60MIN_SOAK边界。
