# L6 Shadow Runs Freshness and Readiness Closure — Attempt 01

**IMPLEMENTED / SELF_REVIEWED / READINESS_PASS / PENDING_INDEPENDENT_REVIEW**。2026-09-13（Asia/Shanghai）。L6=`NOT_ACCEPTED`，SOAK=`NOT_RUN`。

## 分类、基线与已有 WIP

本轮为 production freshness contract + test-only measurement closure + isolated readiness。branch=`audit/post-gatey-agent-baseline`，HEAD=`70d9d183046635aff1f81bf79bba439713b2cf9a`；入口 staged=0。已有 8 个 L6 code drafts 和 7 个关联 evidence，全部修改前保存原字节及 SHA-256；未发现无关代码漂移，未 reset/clean/stash/restore。

备份目录：`C:\Users\Lingyu\AppData\Local\Temp\nq-l6-shadow-freshness-20260913-013029`；initial inventory SHA-256=`5d7116be4967c9c1cae09f62e07807a16b6a7d289b0fb957bde5bb9f1e891aab`。结束时七份已有证据逐字节一致。历史 `FAIL / VALIDATION_UNEXPECTED_DEGRADATION / STALE_THRESHOLD_NOT_DEFINED` 仍保留在 [前轮记录](L6_VALIDATION_CONTRACT_AND_MEASUREMENT_CLOSURE.md)，本文件是追加的新 attempt，未追认旧失败为成功。STATUS 的机器区块仍是较早 L4/L5 planning authority；本次范围来自当前用户明确指令，不修改 STATUS、不推导 LIVE 或 soak 授权。

## SHADOW_RUNS ownership 与决策

真实链路：`ValidationOperationsRuntimeEvidenceOverviewQueryService.overview` 固定聚合五来源 → `SHADOW_RUNS` → `ShadowRunOverviewQueryService.overview` → SELECT-only `JdbcShadowRunOverviewQueryRepository` → `ReadModelEvidenceMetadataCalculator`。该来源有真实 API 和 scheduler consumer，属于本轮 L6 必须完整且新鲜的四个来源之一；它表达本地诊断证据，不能把 freshness 当作 Shadow 执行成功或交易许可。

查询四表：`shadow_runs / shadow_run_events / shadow_run_snapshots / shadow_consistency_reports`。canonical `lastCalculatedAt` 沿用四个 latest fact 的最大值：run `updated_at`、report `generated_at`、event `created_at`、snapshot `captured_at`。查询分别按这些字段排序；不使用查询时间、JVM 启动时间或测试开始时间替代事实时间。`staleRuns` 仍表示缺 snapshot/report 的完整性计数，没有改成时间年龄计数。

此前完整事实虽为 AVAILABLE，owner 仍固定传 `staleAfter=null`，calculator 因缺策略返回 UNKNOWN。选择 **IMPLEMENT_NOW**：阈值按用户允许的第 3 优先级，复用当前同类本地 Shadow 证据的 **7 天 / 604800 秒**策略：

- `ShadowValidationWorkflowOverviewQueryService.STALE_AFTER` 已是 `Duration.ofDays(7)`；其 JDBC 查询消费相同 Shadow `updated_at / generated_at / created_at`。
- `ConsistencyEvidenceOverviewQueryService.STALE_AFTER` 同样为 7 天，消费本地 Shadow consistency reports。
- `docs/gates/gate-t/source/GATET_2_CONSISTENCY_EVIDENCE_REFINEMENT_WO.md:265-271` 明确 report/snapshot/event 的窗口，并允许沿用 GateT-1 的 7 天策略。这是同类 policy 的现存依据，未用更短任意阈值制造 PASS。

不存在 Shadow freshness configuration/property owner；现行同类 owner 使用 typed Duration 常量。Shadow runner 是调用方驱动的本地 skeleton，无自动生成事实的 cadence；Validation `fixed-delay=PT5M` 仅重新读取聚合，不更新 Shadow 时间，因此没有用该调度间隔决定阈值。

## Production 变更与语义

仅修改 `ShadowRunOverviewQueryService`：Duration import、7 天常量、将 null 实参替换为常量。保留事实来源、时间选择、availability、severity、安全边界和共享 calculator。schema/Flyway、交易/Risk/reconciliation 生产逻辑、前端/research/deployment/.github、frozen planning、AGENTS/Skills 均未修改。

无事实 → UNAVAILABLE/UNKNOWN；不完整事实 → PARTIAL/UNKNOWN；完整事实 → AVAILABLE。现有 calculator 按整数 `ageSeconds` 与 `staleAfterSeconds` 比较：604800 秒边界为 FRESH，604801 秒为 STALE；缺时间/未来时间仍 UNKNOWN。未改变共享计算器的整秒粒度。没有新增可配置或字符串解析入口；negative Duration 拒绝、null threshold UNKNOWN 由共享组件定向测试验证，不能将无配置入口虚构为配置解析已测试。

生产聚合仍为 **PARTIAL / UNKNOWN**，scheduler result 仍为 **DEGRADED**；执行完成、来源可用性、aggregate health 独立记录。唯一精确允许的缺口是 `EVALUATION_ARTIFACT_PREVIEW / LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW / UNAVAILABLE / UNKNOWN / no timestamp`，disposition=`EXPECTED_UNAVAILABLE / DEFER_UNTIL_TRIGGER / NOT_REQUIRED_FOR_L6`。SHADOW_RUNS 没有 UNKNOWN 例外；任何其他 UNKNOWN/PARTIAL/STALE/UNAVAILABLE、额外/缺失/重复来源继续拒绝。

## 测量与 timer 闭合

- commandQueue：真实命令经容量 1 的 bounded executor；观测 queue/active/completed/capacity，排队、拒绝和完成均有正负例。0 只标为 MEASURED_ZERO，缺 executor 拒绝。
- oldestCandidateAge：捕获实际 reconciliation 的状态实参，在现有 L5/L6 backlog 谓词上计算持久化 `orders.created_at` 的年龄；无 actionable 为 null/NONE，保留 scan candidates 与 actionable 的区别。
- Hikari：按实际 pool tag 查找精确 `hikaricp.connections.timeout` Counter，缺失拒绝；真实 pool 耗尽正例 0→1，readiness 两 actor 增量均 0。
- 新增 mandatory serialized-field guard；缺字段/UNAVAILABLE 不允许默认变成 0。Linux FD 仍 NOT_APPLICABLE_WINDOWS，不豁免当前 mandatory 三类测量。
- formal 固定单调时钟边界：10m warmup / 40m active / 10m drain，累计 10/50/60m；边界单测通过。checkpoint 间隔也改为 `System.nanoTime`。READINESS 为 15/90/10 秒，不构成 formal qualification。
- 在资格判定前保存完整逐来源 Validation JSON；失败也保留源时间、阈值和原因。

## 定向验证

| Suite | Tests | Failures / Errors / Skips |
| --- | ---: | --- |
| `L6MeasurementPostgresTest` | 1 | 0 / 0 / 0 |
| `L6ProcessOutputTest` | 1 | 0 / 0 / 0 |
| `L6ReadinessContractTest` | 5 | 0 / 0 / 0 |
| `PythonEvaluationArtifactPreviewOverviewQueryServiceTest` | 5 | 0 / 0 / 0 |
| `ReadModelEvidenceMetadataCalculatorTest` | 8 | 0 / 0 / 0 |
| `ShadowRunOverviewQueryServiceTest` | 4 | 0 / 0 / 0 |
| `ShadowValidationWorkflowOverviewQueryServiceTest` | 8 | 0 / 0 / 0 |
| `ValidationOperationsRuntimeEvidenceOverviewQueryServiceTest` | 6 | 0 / 0 / 0 |
| `ValidationEvidenceRefreshServiceTest` | 3 | 0 / 0 / 0 |

最终有效定向证据 **41 tests / 0 failures / 0 errors / 0 skips**。其中 34 条 source/aggregate 测试来自 targeted-02 的已通过模块，后续变更仅限 L6 测量工具，7 条最终 tooling 测试来自 targeted-03。失败历史分别保留：targeted-01 因本轮引入的 Mockito import 不存在而编译失败，改用真实 domain records；targeted-02 的 PostgreSQL 测试因 Docker 未启动产生 1 error，启动本地引擎后 targeted-03 通过。没有把 targeted-02 整个命令称为 PASS。

共享 threshold 测试覆盖缺阈值 UNKNOWN、负阈值拒绝、未来时间拒绝；owner 覆盖 fresh/边界/stale/empty/partial、重复查询不更新 canonical 时间。Aggregate 与 L6 负向测试覆盖预期 no-file gap、SHADOW_RUNS fresh eligible、stale/AVAILABLE+UNKNOWN 拒绝、未知来源拒绝。measurement 测试覆盖 queue、PG age、实际 Hikari timeout、duration 边界和 serialization。命令及环境边界见 machine summary 与 raw `commands.json`；Maven 和子进程使用 OS/JDK 环境 allowlist，不继承 NQ/Spring/provider/Java options。

## 短时真实 readiness

`L6_ACTIVE_STABILITY_READINESS`：**1 test / 0 failures / 0 errors / 0 skips**。隔离锁定 PostgreSQL 16 镜像、既有 schema V51、loopback Synthetic Venue、2 NQ JVM、2 strategies；未运行 Full Maven、完整 L4/L5、60/180 分钟或 overnight。owned database 中执行现有 migration 仅用于隔离初始化，没有新增或修改 migration。

| Phase | Scheduled end | Actual phase duration |
| --- | ---: | ---: |
| warmup | 15s | 15.010449s |
| active | 105s | 89.989715s |
| drain | 115s | 10.015088s |

最终 6 orders / 6 trades / 6 TradeExecuted / 24 ledger entries，Position=0.6 BTC；完整源业务关系 oracle 通过，duplicate external mutation / Trade / TradeExecuted / Ledger-accounting / orphan 均 0。每个 checkpoint 验证终态/version、transition audit、账务和 venue identity；FILLED→FILLED 异常及同态 transition audit=0。

backlog final=0；两 actor scan candidates final=6、actionable=0、oldestCandidateAge final=null/NONE。候选年龄采样 46 个 actor 点，正值 6 个，范围 [95, 264] ms；两 actor 各 13 个资源采样，mandatory missing=0。两 actor 实际 scheduler 各完成一次，逐来源 SHADOW_RUNS 均 AVAILABLE/FRESH、threshold=604800；其余三个必需来源 AVAILABLE/FRESH，唯一 no-file UNKNOWN 保留。

最终业务 oracle、41 条定向 XML、readiness XML、完整 progress/checkpoint/age/resource/validation JSON、进程日志与 tested candidate hashes 均收集到 raw 目录。另做 post-run 只读校验；owned PIDs=[3624, 13044, 32068, 35700, 37588, 38604]，survivors=0，owned container 不存在。Windows 长路径曾导致收集脚本失败，修正扩展路径后成功；未重跑或改变已通过技术结果。Docker Desktop 本地引擎保持运行，不属于本轮创建的 NQ/PG/Venue workload survivor。

## 候选、证据和交付边界

| Candidate code file | Raw SHA-256 |
| --- | --- |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6DurationContract.java` | `3a771bd643bfc9df7efd9ad2a852a06a1f4683902c3c550790bb444e6c5e4613` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6MeasurementPostgresTest.java` | `83ea4e48f7adafb2c27232044e1a853ed9a1d3e2d09a181737c3b14fbeb8b8a4` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6Measurements.java` | `d93d37522757b0da93da916da3413b4939b322d27c0fbd096c1b374a931133b5` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6ReadinessContractTest.java` | `f4c61f6c2d5cdd0222ebad07456b97a3263847a18170c69f5859e277ca15e260` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6ValidationContract.java` | `32c38cebd8c3ec084a62009d1a3cfe4e7b3b8391a294f39648a1141561c027c1` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6ValidationFixture.java` | `ccf81e43941d85830341b7bed0bc4bbbf7df29a9337132a985ef9bb4c7a842b0` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6ActiveStabilityTest.java` | `03ed9817e8b9ad7d9087929f90debe41f76aea8b956328c37aa8d8277fae6064` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6QualificationControls.java` | `18b56a795c82c65e24dd97d513491aa1b30d39f1ef8cd8d0229879b612961ddc` |
| `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunOverviewQueryService.java` | `2984c7a63a3f59ae939a0e8f8a1f4f16311071779cb398dc4ae0a54e35b9e435` |
| `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/readmodel/ReadModelEvidenceMetadataCalculatorTest.java` | `b989914cd8579fb6015df33c01ad9dfa76c521cd180ed4bd159e510e9e132336` |
| `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/shadowrun/ShadowRunOverviewQueryServiceTest.java` | `5138087126f0ae8e76058cb2ae3b0e22ae25c94bc8e4e3d7609cef31bade03f3` |

tested candidate inventory SHA-256=`2bebdbb514f60609a55ff38f569b73ec9bfa8cf85b122659d19670150cf7d47d`；raw artifact index SHA-256=`91f1758d094c646e9ab17b5b924f871951c34d7aee12e8c27967bddbb614252f`，共 75 个 raw files。最终候选与测试时逐文件一致；入口 7 份 evidence 不变。本轮新增本 Markdown 和[机器汇总](L6_SHADOW_RUNS_FRESHNESS_AND_READINESS_CLOSURE.attempt-01.json)，未修改旧 FAIL/BLOCKED。表中 11 个 code files 包含原有 8 个 drafts；本轮 production 仅 1 个 owner、对应 unit tests 2 个。

P0 confirmed=0、P1 confirmed=0；既存 P2 ordinary concurrent INSERT loser、P3 wildcard-import residual 继续 OPEN/NON_BLOCKING；其他 inactive/future obligations 不重评。自查已完成，真正独立 review 尚未执行，不能自签 ACCEPTED。

staged=0；commit=NONE；push=NONE；exact-head CI=NOT_RUN；L6/L6-A=NOT_ACCEPTED；60min soak=NOT_RUN。

**Final decision:** `IMPLEMENTED / SHADOW_RUNS_FRESHNESS_CONTRACT_READY / VALIDATION_UNEXPECTED_DEGRADATION_CLOSED / L6_MANDATORY_MEASUREMENTS_READY / FORMAL_10_40_10_READY / L6_ACTIVE_STABILITY_READINESS_PASS / P0_0 / P1_0 / PENDING_INDEPENDENT_REVIEW / L6_NOT_ACCEPTED / SOAK_NOT_RUN`

唯一下一动作：**NQ-GATEAUDIT-PHASE6-L6-SHADOW-RUNS-FRESHNESS-CORRECTNESS-REVIEW**。仅定向审查 freshness semantics、threshold authority、Validation fail-closed 以及 L6 measurement/readiness interaction；不重审 L4/L5 或 same-state convergence。Review PASS 后按后续精确交付授权进入 delivery + exact-head CI，再进入正式 L6-A 60min，不追加 readiness review。
