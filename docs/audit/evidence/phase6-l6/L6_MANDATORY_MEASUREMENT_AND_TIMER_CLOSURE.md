# L6 Mandatory Measurement and Timer Closure — Attempt 01

**BLOCKED / L6_PRODUCTION_OBSERVABILITY_CHANGE_REQUIRED**。日期：2026-09-12（Asia/Shanghai）。原 `L6_MANDATORY_MEASUREMENT_NOT_READY` 尚未关闭。本次在修改任何 harness 前查明不可由数据 fixture 解决的 Validation SUCCESS 前提；按用户§3停止，不修改生产、不替换真实来源、不伪造SUCCESS。

## 基线与原始状态

branch=`audit/post-gatey-agent-baseline`；HEAD/upstream/remote SHA均为`b733b75b7d5b018dc2f72f508ba11d1450a3bddb`；tree=`b4aeb5a3bee8c6195c36fdebc35486b3769ac012`。
[CI 34622315529](https://github.com/ling5477/nexus-quant/actions/runs/34622315529)本轮重新查询：completed/success，9/9 success。

入口只有三个已知未跟踪evidence：delivery acceptance、[上一轮readiness说明](L6_ACTIVE_STABILITY_READINESS_RESUME.md)及其机器汇总。git diff --check通过，stage=0。入口所有Git可见文件保存raw SHA256，定向测试后重算无变化；原BLOCKED attempt和原交付记录均未覆盖。

## 实际根因

1. [PythonEvaluationArtifactPreviewOverviewQueryService](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/pyartifactpreview/PythonEvaluationArtifactPreviewOverviewQueryService.java)是现行真实Spring来源。构造器只有Clock依赖；`overview()`调用private `evidenceMetadata()`，固定`Availability.UNAVAILABLE`、`lastCalculatedAt=null`、`staleAfter=null`，由calculator生成UNKNOWN。该no-file入口既不接收artifact路径，也不读取数据库/文件/manifest，没有可以通过合法输入seed变成AVAILABLE的条件。
2. [ValidationOperationsRuntimeEvidenceOverviewQueryService](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/validationoperations/runtimeevidence/ValidationOperationsRuntimeEvidenceOverviewQueryService.java)真实构造器固定聚合五个QueryService，其中包括该preview来源。只有所有来源AVAILABLE，aggregate才AVAILABLE；只有全部AVAILABLE/FRESH才FRESH。故即使另外四个来源均补齐，preview仍使该合取条件失败。
3. [ValidationEvidenceRefreshService](../../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/validationevidence/ValidationEvidenceRefreshService.java)要求aggregate AVAILABLE且FRESH且blockerCount=0才SUCCESS；UNAVAILABLE来源按blocker计数。因此当前真实组合无法靠数据fixture取得任务要求的SUCCESS。
4. 上轮runtime已记录每actor一次真实Validation callback，DEGRADED/PARTIAL/UNKNOWN、blockerCount=3；本次没有重新启动该runtime。本文证明至少preview这个不可seed的阻断足以否定SUCCESS，不臆测另外两个blocker的具体归属，也不把历史摘要未导出的逐来源事实写成当前动态观察。

这是有意保留的no-file、fail-closed产品边界，不足以定性为`VALIDATION_RUNTIME_PRODUCT_DEFECT_DISCOVERED`，不新增生产P1。若要保持“当前真实五来源全部成功”的验收要求，需要新增生产来源能力或改变生产聚合语义；均越过本次src/test-only授权。用test-only override返回AVAILABLE/FRESH、mock整个aggregate、或把DEGRADED算SUCCESS只能证明替身，不能满足本次真实runtime条件。

停止依据直接来自本次用户§3：**“如果任何缺口只能通过修改 src/main 才能正确解决：BLOCKED / L6_PRODUCTION_OBSERVABILITY_CHANGE_REQUIRED；停止，不顺手修改 production。”** 此处不是Skill引入的额外审批流程。

## 定向核实

```powershell
mvn -f backend/pom.xml -pl nq-scheduler -am test '-Dtest=PythonEvaluationArtifactPreviewOverviewQueryServiceTest,ValidationEvidenceRefreshServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xmx512m'
```

2026-09-12 00:56:33启动，进程总耗时10.362s，exit=0；8 tests / 0 failures / 0 errors / 0 skips。No-file source实际测试5条，包含`shouldReturnSafeNoFileBaselineOverview`对UNAVAILABLE、UNKNOWN、缺失权威时间的明确断言。Refresh service测试3条，保留SUCCESS、DEGRADED和异常不伪成功语义；这3条使用mock aggregate，仅验证决策函数，不充当真实Validation SUCCESS或readiness证据。

[机器汇总](L6_MANDATORY_MEASUREMENT_AND_TIMER_CLOSURE.json)绑定命令、source hashes、CI、日志和Surefire hashes。raw目录：`C:\Users\Lingyu\AppData\Local\Temp\nq-l6-measurement-closure-20260912`。本轮未创建PG/Venue/NQ工作负载，Maven进程已结束。未运行Full Maven、L4/L5、readiness重跑、60/180分钟或新独立review。

## 未完成项与边界

本轮代码/harness/oracle修改=0。commandQueue真实测量、oldest age序列、Hikari timeout counter、formal 10/40/10合同、Validation真实成功及短时readiness重跑均未闭合。未为部分实现创建可误认为可交付的候选；保留本说明及机器汇总，等待前提决策。

P0 confirmed=0、P1 confirmed=0；既存P2/P3、原same-state整改接受、历史projection repair和inactive/future obligations不变。L6=NOT_ACCEPTED、L6_A=NOT_ACCEPTED、SOAK=NOT_RUN；READY_TO_START_L6_A_60MIN=false。

本任务的Git发布授权以闭合PASS为前提，本轮未满足，故stage=0、commit=NONE、push=NONE。仅新增这两份闭合阻断证据；原三份未提交evidence原字节保持不变。正式60分钟qualification不得启动。先明确Validation真实来源能力/验收合同的处理范围，不能在当前任务越界修改生产或降低成功条件。
