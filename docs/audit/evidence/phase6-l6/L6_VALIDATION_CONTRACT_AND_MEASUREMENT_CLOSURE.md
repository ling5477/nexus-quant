# L6 Validation Contract and Measurement Closure — Attempt 01

日期：2026-09-12（Asia/Shanghai）。结果：**FAIL / VALIDATION_UNEXPECTED_DEGRADATION / STOP / INDEPENDENT_REVIEW_REQUIRED**；生产边界为 **BLOCKED / PRODUCTION_CHANGE_REQUIRED**。

本轮实现test-only草稿并完成定向测试；真实短时readiness被额外来源UNKNOWN阻断。未扩大no-file允许集合，未修改production，未取得READINESS_ACCEPTED，未启动正式60分钟。

## 基线、授权及精确输入

branch=`audit/post-gatey-agent-baseline`；HEAD/upstream/remote均为`b733b75b7d5b018dc2f72f508ba11d1450a3bddb`；tree=`b4aeb5a3bee8c6195c36fdebc35486b3769ac012`。
[CI 34622315529](https://github.com/ling5477/nexus-quant/actions/runs/34622315529)本轮复核completed/success、9/9 success。没有新交付提交，不将该旧基线CI用作本轮草稿的exact-head CI。

入口只有5份已知phase6-l6未提交evidence，所有3937个Git可见文件保存raw SHA256。原readiness、measurement closure阻断记录及delivery acceptance保持原字节。本轮production/migration/config/frontend/research/CI/deploy/current authority/frozen plan修改均0。

## 已实施但尚未接受的test-only草稿

本轮实际代码清单如下；精确hash见[机器汇总](L6_VALIDATION_CONTRACT_AND_MEASUREMENT_CLOSURE.json)。路径均位于`backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/`。

| 文件 | 变化与证明范围 |
| --- | --- |
| L6ValidationContract.java | execution与availability分开；固定5个source key；仅允许EVALUATION_ARTIFACT_PREVIEW的LOCAL_NO_FILE_EVALUATION_ARTIFACT_PREVIEW为UNAVAILABLE/UNKNOWN/null权威时间；其他必须AVAILABLE/FRESH；重复、缺失、未知来源或额外降级拒绝 |
| L6Measurements.java | 实际单worker、有界queue容量1；采queueSize/active/completed/capacity；缺失executor拒绝。Hikari从runtime registry按真实pool标签解析timeout Counter；不存在拒绝。age取PG statement_timestamp和持久化created_at |
| L6DurationContract.java | READINESS=15/90/10秒；FORMAL=600/2400/600秒，无普通时长property覆盖；monotonic 10/50/60分钟边界 |
| L6ValidationFixture.java | 初始化独立诊断输入：DRAFT version、CREATED/INCOMPLETE dataset、CREATED Shadow记录/事件、空输入snapshot及NOT_COMPARABLE报告；没有运行Shadow、没有制造比较成功或ordinary交易成功事实 |
| L6QualificationControls.java | 命令经过上述真实executor；捕获真实reconcile传入的候选状态及真实Validation aggregate结果，原方法继续执行；采真实pool counter、age与严格qualification结果 |
| L6ActiveStabilityTest.java | 单一起点绝对phase deadline，记录各phase实际起止；先于fill采age正对照；保留完整业务oracle，增加真实transition audit/version检查 |
| L6ReadinessContractTest.java | 时间边界、expected-degraded正例、逐来源额外降级/未知/缺失/重复负例、scheduler失败/未完成负例、实际queue占用/拒绝/完成证明 |
| L6MeasurementPostgresTest.java | owned PG上真实pool耗尽使timeout counter递增；不存在meter拒绝；隔离临时关系验证age查询、NONE和JSON序列化，不作为业务资格事实 |

扫描状态由真实`reserveReconciliationCandidates`参数捕获，未复制production状态列表。扫描全集包含已收敛FILLED；`scanEligibleCandidateCount`与其中符合既有L5/L6 BACKLOG谓词的`eligibleCandidateCount`分别输出。待处理count=0时age=null/NONE，不能把扫描全集数量冒充积压，也不能把NONE写为0ms。

no-file disposition按本次合同设为DEFER_UNTIL_TRIGGER/NOT_REQUIRED_FOR_L6，仅未来正式引入file/artifact source触发；没有为SHADOW_RUNS增加第二个例外。

## 测试与失败保留

```powershell
mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=L6ReadinessContractTest,L6MeasurementPostgresTest,L6ProcessOutputTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6.measurement=true' '-Dnq.l6=true' '-Dnq.l6.diagnostic=true' '-DargLine=-Xmx512m'

mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=L6ActiveStabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6.measurement=true' '-Dnq.l6=true' '-Dnq.l6.diagnostic=true' '-DargLine=-Xmx512m'
```

- targeted-01：编译失败，SimpleMeterRegistry没有实现AutoCloseable；改为finally显式close，未削弱断言。
- targeted-02：6 tests/2 failures/0 errors/0 skips；JSON序列化后LongNode/IntNode类型不同导致结构equals拒绝相同数值。改为核对序列化往返的完整JSON文本，保留各字段与不变量断言。
- targeted-03：6 tests/0 failures/0 errors/0 skips，exit0。真实`hikaricp.connections.timeout`在独立pool耗尽时start=0、end=1；正常空/满queue、完成计数、meter缺失、eligibility缺失和严格Validation负例均通过。10/50/60分钟边界通过确定性测试，未真实等待60分钟。
- readiness-01：1 test/1 failure/0 errors/0 skips，exit1；命令含构建56.593秒。真实Validation运行后，guard抛`VALIDATION_UNEXPECTED_DEGRADATION`，故本attempt不通过。
- 编辑脚本第一次未指定UTF-8发生GBK解码错误，修改前失败；显式`python -X utf8`后执行。临时脚本和原测试失败日志保留。

所有命令使用OS/JDK环境allowlist，不继承NQ/Spring/provider/Java options；B0 child再次清空环境并绑定owned PG、loopback Synthetic Venue，无真实provider或credentials。

## Readiness实际取得的局部证据

2 NQ actor、2策略未降低；controller PID=25064、NQ=28020/27176、Venue=25024。PG沿用锁定postgres16镜像、schema V51。warm-up实际15.0144582s；active未完成，drain未执行。

1个完整checkpoint通过：2 Order、2 Trade、2 TradeExecuted、8 Ledger，BTC Position0.2、duplicates0、orphans0、backlog0；该checkpoint的transition audit=8且无重复，所有Order版本4。它只覆盖该时间点，不能推出失败时最终业务状态。原FILLED→FILLED错误未观察到；本次停止由Validation qualification规则触发，不是新账务/状态机错误。

6个progress样本、6轮age采样（每轮2 actor），两个资源文件各3条。实际非空age样本4条，age=97–262ms；首个完整checkpoint待处理候选归0、oldest age=null，扫描全集仍2。实际runtime Hikari metric解析成功，两actor观测start=0、last=0、delta=0；这是失败前局部序列，不声明完整readiness资源稳定。

Paper normal/CRITICAL/report链继续取得原合法fixture结果；不是ordinary账务替代物。发生失败后没有完整最终DB/Venue/来源metadata导出；raw子进程日志、此前checkpoint与资源序列保留。未把缺失数据填0或UNKNOWN冒充健康。

## 新阻断根因及精确处置

两个真实Validation scheduler回调均完成并记录：DEGRADED、availability=PARTIAL、freshnessStatus=UNKNOWN、blockerCount=1、warningCount=1、failureCategory=null。执行本身没有证实uncaught scheduler error；是额外来源不满足本次qualification合同。

[ShadowRunOverviewQueryService](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/shadowrun/service/ShadowRunOverviewQueryService.java)的`evidenceMetadata`即使completeEvidence=true、availability=AVAILABLE，也固定向calculator传入`staleAfter=null`。[ReadModelEvidenceMetadataCalculator](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/readmodel/ReadModelEvidenceMetadataCalculator.java)明确规定：有权威时间且AVAILABLE，但阈值缺失时仍为UNKNOWN/STALE_THRESHOLD_NOT_DEFINED。

因此当前`SHADOW_RUNS`不能通过更多seed变成FRESH。no-file例外之外至少还有这个不可由数据fixture闭合的UNKNOWN；当前严格规则在真实运行中拒绝它。逐来源overview未在抛错前序列化，故这里对warning来源的归因是当前真实source/calculator代码推导，与runtime一个warning吻合；不把它冒充逐来源动态JSON。

本轮没有把该设计边界定性为已证实生产缺陷。若继续，需要更改生产freshness policy，或扩大qualification允许的UNKNOWN集合。本次用户§6要求production change needed即BLOCKED，§9要求需要扩大多个未知source即STOP/INDEPENDENT_REVIEW_REQUIRED；因此停止，未添加例外、未改production、未另开review。

## 清理和交付状态

本轮两个定向PG容器及readiness PG容器均不存在；runtime和输出协议sanity记录的owned PID均已退出，独立清理检查survivors=0。未操作其他任务资源。raw目录`C:\Users\Lingyu\AppData\Local\Temp\nq-l6-contract-closure-20260912`的artifact-index.json绑定42个raw文件，含原始run目录、各失败/成功日志、命令、proof、采样、Surefire和cleanup；hash见机器汇总。

P0 confirmed=0、P1 confirmed=0；既存P2/P3、same-state整改历史接受、projection repair及inactive/future义务不变。当前8个test-only代码文件及新增本说明/机器汇总保持未暂存草稿；原5份evidence未变。stage=0、commit=NONE、push=NONE，新exact-head CI=NOT_RUN。用户的提交授权以PASS为前提，当前未满足。

L6=NOT_ACCEPTED、L6_A=NOT_ACCEPTED、SOAK=NOT_RUN、READY_TO_START_L6_A_60MIN=false。不得启动正式60分钟qualification。下一步须先处理SHADOW_RUNS freshness合同的范围与审查前提，不能直接扩大DEGRADED允许集合。
