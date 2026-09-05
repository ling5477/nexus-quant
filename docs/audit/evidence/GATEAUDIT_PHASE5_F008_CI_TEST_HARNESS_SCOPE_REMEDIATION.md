# GateAUDIT Phase5 F008 CI test harness scope remediation

- Task：`NQ-GATEAUDIT-PHASE5-F008-CI-TEST-HARNESS-SCOPE-REMEDIATION`。
- Classification：`NQ-only / CI_TEST_HARNESS_FIX / POWERSHELL_SCOPE_COMPATIBILITY / TARGETED_REGRESSION`。
- 本地结果：`SELF_REVIEWED / READY_TO_COMMIT`；不预先声明后续commit或exact-head CI成功。
- Starting HEAD/origin：`716199a7cb836a5eaf43a88b0de6db0f47a75e91`；branch=`audit/post-gatey-agent-baseline`，worktree clean、staged=0；parent=`aa73a7a58b7d5ecbb8e5beba2106cbbe982803dc`。
- Failed exact-head CI：[33976140445](https://github.com/ling5477/nexus-quant/actions/runs/33976140445)，headSha=`716199a7cb836a5eaf43a88b0de6db0f47a75e91`，completed/failure，8/9 SUCCESS。
- 唯一目标：`CI_TEST_HARNESS_SCOPE_RESOLUTION_FAILURE`；F008 production/config/security Final Closure Review保持PASS，不重新打开。

## 1. RCA 与复现

失败job=`Repository hygiene and governance`，step=`Validate canonical delivery workflow contract`。原CI先成功执行default validator、25项capability inventory和19项negative rejection，随后production fallback fixture中的`Assert-Condition`报`CommandNotFoundException`，exit=1，完整mutation suite没有完成。

原helper定义在[测试脚本](../../../scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1)的script作用域。`GetNewClosure()`将变量捕获到动态module session state，不会将该script的命名函数一起导入。直接以`powershell/pwsh -File suite.ps1`启动时，helper在entry scope可见；GitHub以`-File`执行生成的临时wrapper，再由wrapper调用suite，多了一层script边界，closure无法按函数名找到helper。

PS5.1与PS7隔离fixture均实测：直接`-File`命名helper通过；wrapper边界命名helper及`script:Assert-Condition`限定均因CommandNotFoundException失败；捕获原helper ScriptBlock后用调用运算符执行，两种shell均exit=0。Probe同时验证first/second循环值捕获，以及false assertion仍抛出预期错误。首次临时probe漏传预期参数导致CAPTURE_LOST，补齐显式参数后完成验证；不涉及candidate修改，失败日志保留。

## 2. 最小修复

- 保留原`Assert-Condition([bool] $Condition, [string] $Message)`及完整函数体。
- 同一script scope增加`$assertCondition = ${function:Assert-Condition}`，让closure显式捕获原helper的ScriptBlock。
- 仅将两处closure内调用改为`& $assertCondition`：production fallback target存在性检查，以及F008 relocation/duplicate target存在性检查。参数、错误消息和throw行为不变。
- AST inventory：17个closure中仅这两处引用本地命名helper。不引入global函数或helper module，不修改workflow调用方式。
- 自审：22个assertion调用点、17个closure前后相同。逆向移除新增binding/comment并恢复两个调用名后，脚本与starting HEAD版本逐字符相等；mutation bodies、case集合和安全断言均未删除或降级。
- Validator、workflow、backend/config、Flyway、frontend及machine authority保持原字节；P5-F007/P5-F009、Phase6不变。

## 3. 实际验证

所有命令从repository root启动。Windows PowerShell=`5.1.26100.9168`，PowerShell=`7.6.5`；仅子进程清除继承的NQ/SPRING/PG/JVM/Maven选项，设置CI/no-outbound、synthetic loopback aliases和`MAVEN_ARGS=-o`。没有修改系统环境或联网安装工具。

真实入口：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1
pwsh -NoProfile -File scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1
```

额外完整执行`pwsh -NoProfile -File wrapper.ps1`；wrapper将ErrorActionPreference设为Stop，并调用`./scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1`。该项验证真实script调用边界，不替代两个直接入口。

| 本轮执行 | Rejected | Accepted | Exit |
| --- | --- | --- | --- |
| PS5.1 direct -File完整suite | 135/135 | 0 | 0 |
| PS7 direct -File完整suite | 135/135 | 0 | 0 |
| PS7 GitHub wrapper完整suite | 135/135 | 0 | 0 |

三次均执行到M13/M14、ContractOnly downgrade和quoted-key cases，并输出`MUTATIONS_REJECTED=135`与最终SUPPLY_CHAIN_TEST PASS。plain/quoted/escaped if与continue-on-error、parser negatives、ContractOnly、selector removal和failure-ignore cases全部保留。

| Rejection chain（三次结果一致） | Tests | Assertion failures | Errors | Skips | Validator |
| --- | ---: | ---: | ---: | ---: | --- |
| R06 | 118 | 17 | 0 | 0 | REJECTED |
| R09 | 118 | 50 | 0 | 0 | REJECTED |
| R10 | 118 | 12 | 0 | 0 | REJECTED |

同一default admission入口由隔离source mutation触发真实Maven assertions failure，再以PRODUCTION_CONFIG_REGRESSION_FAILED拒绝。Suite baseline/default执行原mandatory F008 Maven，两个fresh reports合计`50+68=118 tests / 0 failures / 0 errors / 0 skips`，未另外重复运行。

PS5.1/PS7 authority checker均exit=0，git diff --check通过。Full Maven、PG16、packaged JAR、canonical release 66/66、frontend本地资格验证均NOT_REQUIRED / NOT_RUN。

## 4. 提交与CI边界

本轮仅提交测试脚本、本evidence及TESTING/WORKLOG追加。自审=`SELF_REVIEWED / READY_TO_COMMIT`，assertions removed=0、mutations removed=0、safety weakened=0；不新开独立Review。

用户已授权commit、push与新exact-head CI。新run须绑定新HEAD，9个required jobs全部success，并由日志确认完整mutation suite、R06/R09/R10和mandatory F008 Maven实际完成后，才能声明CI_GREEN。若再次失败，停止报告，不自动amend或连续修第二个问题。

CI green后唯一下一动作：`NQ-GATEAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED-POST-CI-AUTHORITY-ACCEPTANCE`。本任务不写P5-F008 CLOSED。

本地详细日志保留于ignored artifacts/f008-harness-scope-remediation，不暂存probe、runner、Maven target或临时生成物。回滚使用单独授权的forward revert，不改写已发布历史。

工具声明：Git、GitHub CLI、PowerShell 5.1/7、Java/Maven；MCP未使用；primary Skill为nq-dh-workflow-router，nq-docs-writer仅支持证据追加；网络用于Git/GitHub基线和授权CI，不联网安装parser，不访问生产服务、credential或真实交易接口。
