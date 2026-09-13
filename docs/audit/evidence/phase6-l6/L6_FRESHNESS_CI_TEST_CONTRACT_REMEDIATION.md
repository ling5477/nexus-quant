# L6 Freshness CI Test Contract Remediation

2026-09-13（Asia/Shanghai）。分类：TEST_ONLY / CI_CONTRACT_REMEDIATION。基线及 parent 为 `35f6bbdadacbfb2b6097a9fcb5a0e84bcbd924a8`，branch=`audit/post-gatey-agent-baseline`，入口工作区干净。

## 失败与根因

[失败 CI 34736516078](https://github.com/ling5477/nexus-quant/actions/runs/34736516078) 永久保留为 FAILURE：7 success、1 failure、1 skipped、0 cancelled。Backend regression 在 nq-core 的 574 tests 中出现 2 failures、0 errors；Delivery SBOM and provenance 被跳过。

`ShadowRunReadOnlyQueryServiceTest` 的两个旧断言未同步已接受的 freshness contract：

- `shouldMapOverviewCountsLatestFactsAnchorsAndHighDivergenceSeverity` 仍期待 staleAfterSeconds=null，真实值为七天。
- `shouldKeepOverviewServiceDependencyAwayFromRunnerAdapterAccountLedgerAndOrderPorts` 枚举全部 declared fields，将新增静态 Duration 策略常量误算成实例依赖。

本轮成功不能改写该历史 run 的失败结论。

## 最小测试修复

仅修改上述测试类，未改生产实现或其他 helper。通过 `ShadowValidationWorkflowOverviewQueryService` 的公开 overview 响应取得现有 Shadow policy，同时断言等于 `Duration.ofDays(7).getSeconds()`，再比较 SHADOW_RUNS。新增行为测试验证边界前一秒、精确边界为 FRESH，后一秒为 STALE，事实时间不因查询推进而变化；原 PARTIAL / UNKNOWN / SOURCE_PARTIAL 断言保留。

架构 guard 仅豁免由 `ShadowRunOverviewQueryService` 声明、名为 STALE_AFTER、类型 Duration 且 static final 的常量。其余字段继续参与原精确依赖列表比较和 runner/adapter/account/ledger/order/client 禁入断言。新增两个负例分别注入意外实例 Duration 状态与可变静态 Duration 状态，均实际触发拒绝。没有忽略所有未知字段或删除约束。

## 候选身份与审查复用

production delta=0。入口所有 tracked files 保存原始 SHA-256；测试后仅本测试类发生变化，其余逐字节匹配。生产 owner `ShadowRunOverviewQueryService.java` 原始 SHA-256 仍为 `2984c7a63a3f59ae939a0e8f8a1f4f16311071779cb398dc4ae0a54e35b9e435`，与[独立 correctness review](L6_SHADOW_RUNS_FRESHNESS_CORRECTNESS_REVIEW.md)一致。

独立审查报告原始 SHA-256=`d18c89689634631ee56e56d1f979477902059a776cb7fa97a6a640f6f69921f0`，正文与 PASS 结论不变。Validation aggregate、measurement harness、formal 10/40/10、其他 code/evidence 及禁改路径均不变。按本轮用户明确政策复用原独立审查，不新增 review。

## 验证

Windows / Java 21 / Maven 3.9.12；进程环境仅继承 OS/JDK allowlist，不继承 NQ/Spring/provider/Java options。依赖 reactor 限于 nq-core 及其上游，不执行 Full Maven。

```text
mvn -B -f backend/pom.xml -pl nq-core -am test -DargLine=-Xmx512m -Dtest=ShadowRunReadOnlyQueryServiceTest -Dsurefire.failIfNoSpecifiedTests=false
mvn -B -f backend/pom.xml -pl nq-core -am test -DargLine=-Xmx512m
```

- 原失败测试类：10 tests / 0 failures / 0 errors / 0 skips，exit=0。
- nq-core：576 tests / 0 failures / 0 errors / 13 skips，exit=0。新增 2 个测试；13 skips 来自 Windows symlink / SecureDirectoryStream 等既有平台前提，不是本轮目标测试跳过，Linux exact-head CI 负责相应平台覆盖。
- 自查：测试只读生产公开语义；精确常量豁免与负例有效；生产哈希、范围及 diff 检查通过。

原始命令、日志、每次独立保存的 XML 与 inventory：`C:\Users\Lingyu\AppData\Local\Temp\nq-l6-ci-remediation-kv5hi25v`。修复后测试原始 SHA-256=`b9822f188b2dca0f866a5171b5cf7ed5fa2255eae874e16a85eb682e01cad816`。未运行 L4、L5、L6 readiness、60min 或 180min。

## 提交前结论

TEST_CONTRACT_REMEDIATED / PRODUCTION_CANDIDATE_UNCHANGED / INDEPENDENT_CORRECTNESS_REVIEW_REUSED / PENDING_EXACT_HEAD_CI。既有审查范围 P0=0、P1=0；本轮没有新增已确认生产正确性 finding。L6=NOT_ACCEPTED / SOAK=NOT_RUN。只有 remediation commit 的 canonical CI completed/success 且 9/9 jobs success 后，才可接受交付并进入 `NQ-GATEAUDIT-PHASE6-L6-ACTIVE-STABILITY-60MIN-QUALIFICATION`；此记录不预先宣称 CI GREEN。
