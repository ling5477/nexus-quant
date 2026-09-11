# Phase6 L5 bounded workload 与测量基线

Task：`NQ-GATEAUDIT-PHASE6-L5-BOUNDED-WORKLOAD-AND-MEASUREMENT`。
Classification：`HIGH_RISK / SCALE_QUALIFICATION / TEST_HARNESS_AND_MEASUREMENT / NQ-only`。

Decision：`PASS / PHASE6_L5_BOUNDED_WORKLOAD_AND_MEASUREMENT_ACCEPTED / SELF_REVIEWED`。S1/S2/S3均通过；`L5 != ACCEPTED`。唯一下一动作：`NQ-GATEAUDIT-PHASE6-L5-CONCURRENT-WORKLOAD-AND-BACKLOG`。

## 身份与边界

- Starting/ending HEAD：`991187fe772ad8b03746a4a9ddfc3ea9010e5896`；branch=`audit/post-gatey-agent-baseline`。
- L4保持ACCEPTED：technical SHA=`3d103cea2072b3c2d9d1009cc5841c18a958ee80`，既有exact-head CI=`34501806297 / 9 of 9 SUCCESS`；schema=V51。未重跑该CI。
- [工作规划](../GATEAUDIT_PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN.md)起止SHA-256均为 `80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`，byte-for-byte不变；它是起始唯一pre-existing untracked文件。
- production/migration/current authority/.github delta=0；credentials/real exchange mutation/LIVE/real money=0；stage=0、commit=NONE、push=NONE。没有Full Maven、独立review、故障注入或precise delivery。
- [汇总及指纹](summary.json)、[S1](runs/S1.json)、[S2](runs/S2.json)、[S3](runs/S3.json)是本批五份canonical evidence。每个identity只保留一行关系事实，完整raw/log/sampling留在ignored target。
- 指纹在完成时采集，覆盖10个代码文件、实际配置、三份原始证明及运行日志。最终S1至S3之间Java driver/fixture未变；之后只增加Python schema/secret拒绝校验及离线测试，已重新验证、导出三份未改动raw。未声称存在运行前未采集的指纹，也未把working-tree结果当作exact-head CI验收。

## 先审计复用，再最小扩展

| Capability | Disposition | 使用方式 |
| --- | --- | --- |
| B0–B5 driver / multi-JVM / isolated PG | REUSE | B0Processes/B0Fixture/RestartDatabase；owned label、loopback、reader权限、真实Spring |
| Synthetic Venue | MINIMAL_EXTENSION_REQUIRED | 既有多订单协议增加L5_OPEN正常ACK及受控成交；独立JVM，不访问NQ DB |
| Scanner / reconciliation / recovery | REUSE | 真实scanOnce、reconcileOnce(100)、recoverAll及RiskGate/V49/V50/V51 |
| Order/Trade/Event/Ledger oracle | REUSE + MINIMAL_EXTENSION_REQUIRED | 沿用SQL事实及synthetic identity exporter，扩展多身份关系、金额与余额校验 |
| F007 / Hikari | REUSE | 读取既有F007 snapshot、HikariPoolMXBean，未加生产instrumentation |
| Continuous sampling / aggregate export | MINIMAL_EXTENSION_REQUIRED | 只读MVCC进度采样、ThreadMXBean、test-only executor计数；250ms目标间隔；canonical汇总与负例 |
| 新load/chaos/monitoring平台、Prometheus/Grafana | NOT_NEEDED | 未创建或部署 |

实现位于[既有smoke目录](../../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke)。L5入口委托B0装配，L5命令分支不执行旧故障命令。B0原调用的默认资源行为保持不变。

## 真实负载与correctness oracle

| Dimension / result | S1 | S2 | S3 |
| --- | --- | --- | --- |
| Result | PASS | PASS | PASS |
| 并发在途上限 / NQ JVM | 1 / 1 | 2 / 2 | 4 / 4 |
| 普通logical commands / initial candidates | 120 | 120 | 240 |
| StrategyRun / dispatch work | 0 / 0 | 0 / 0 | 12 / 12，4策略×3窗口 |
| Order / V49 / Venue PLACE / fill / Trade / TradeExecuted | 各120 | 各120 | 各252 |
| Ledger entry / Ledger event | 480 / 480 | 480 / 480 | 1008 / 1008 |
| Position qty / account snapshots | 12 / 240 | 12 / 240 | 25.2 / 504 |
| duplicate mutation / accounting / lost work / orphan | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 |
| Oracle mutation negatives | 17/17 REJECTED | 17/17 REJECTED | 19/19 REJECTED |

每个identity经过真实OrderCommandService或strategy scanner→RiskGate→Order/V49→Synthetic Venue PLACE→fill→reconciliation→Trade/TradeExecuted/Ledger。每单qty=0.1、price=100、fee=0.01。逐身份核对稳定client集合、account/SIM、Order FILLED/version、唯一V49、Venue/fill绑定、唯一Trade及event payload、四条Ledger金额/余额链、ledger事件、Position及最后账户快照。PLACE=1、CANCEL=0。S3另核对SUCCEEDED run、durable dispatch绑定及每策略三个唯一schedule/window；正常重放不改变持久化事实。

每波先向全部JVM发命令，再收齐响应；至少250ms/波，保留真实默认每JVM同账户5/s风控。S3先完成240个普通candidate，再由同一真实scanner执行4策略的三个已到期年度CRON窗口。fixture只预置定义/schedule起始时间，由真实Clock/CRON计算dueAt，没有修改业务结果或Clock，没有制造240×4×3笛卡尔积。

本批reconciliation是分配到不同JVM的**串行有界单轮调用**；策略阶段也是单scanner调用。没有把它记为并发scanner/recovery压力证明，没有激活既有自动scheduler。多scanner竞争、重复故障、长期heap/GC与L5 aggregate仍未执行。

## Backlog与forward progress

| Measurement | S1 | S2 | S3 |
| --- | --- | --- | --- |
| Initial / peak backlog | 120 / 120 | 120 / 120 | 240 / 240 |
| Processed orders | 120 | 120 | 252（含后续12个策略订单） |
| Remaining / final actionable / final unresolved | 0 / 0 / 0 | 0 / 0 / 0 | 0 / 0 / 0 |
| Serial drain iterations | 2 | 2 | 10（普通与三次策略drain之和） |
| Drain seconds | 2.836 | 3.277 | 10.027 |
| Progress samples / JVM resource samples | 186 / 192 | 105 / 232 | 147 / 701 |
| Transaction delta（含观测开销） | 2693 | 3233 | 7770 |
| Whole run seconds（含setup/cleanup） | 57.721 | 40.338 | 59.356 |

backlog读取非终态订单及缺Trade/Ledger的FILLED订单；correctnessRequiredUnresolved单列MAY_HAVE_ESCAPED、未获external identity的非终态订单；actionable=backlog-unresolved。这是本批clean-load候选统计，不是全量生产eligibility扫描器。终态/non-actionable行排除，完整账务由关系oracle核对。

PLACE在途期间unresolved峰值为1/2/4，最终均0；合法MAY_HAVE_ESCAPED本身没有被判错。FILLED对应的V49仍保持该状态，其后果由Venue/Trade/Ledger事实证明。

持续只读采样能看到orders增长、terminal/Trade/Ledger增加及backlog归零。S3从240个普通订单完成继续到252及12个SUCCEEDED run。单轮数量与30分钟watchdog防止测试无限等待，不是毫秒SLA；耗时不是benchmark或性能验收门槛。

## 资源、测量隔离与清理

每JVM Hikari max/total peak=10、active peak=1、waiting peak=0、最终active/waiting=0；raw含idle/total连续采样。S1/S2线程15→18、peak19；S3最终17或18、peak19。没有要求线程回到启动瞬间15，最终所有进程退出。本批未观察到泄漏或永久耗尽。

NQ命令同步执行，最多1条active，无内部积压队列；这不是所有生产executor队列的覆盖声明。Venue固定4 workers/queue capacity16；active采样峰值1/1/2、queue峰值0、rejected=0、completed持续增长。metrics请求自身占用worker，响应时active=1不代表泄漏，最终进程退出和端口释放另行验证。

NQ heap512MiB/JVM、Venue256MiB、PG768MiB/tmpfs256MiB、Maven512MiB/Surefire1024MiB。采样最多7200项，有磁盘预检及artifact上界。reader无orders UPDATE权限；每次采样使用REPEATABLE_READ只读快照，HTTP前commit，不锁业务行、不UPDATE业务表。exporter只读文件，负例只改副本。

每轮owned数据库删除验证、owned容器清理、NQ/Venue退出及PG/Venue loopback端口释放均PASS。结束时正常Windows Docker CLI返回29.7.2，nq.b0.identity容器及L5 qualification JVM查询为空。ignored target中的证据保留不属于运行资源泄漏。

## Docker恢复与失败保留

首次S1在业务启动前遭遇Windows docker inspect 45s超时，cleanup也挂起，日志文件占用使finally的FileSystemException覆盖原超时显示。两个named-pipe入口均无响应，而docker-desktop内部Unix socket正常：确认故障位于Windows Desktop backend/API桥接路径，未建立更深内部死锁根因。

用户授权修复后，docker desktop restart --timeout 45仍无法退出；核实无其他运行容器后，只停止卡住的Desktop/backend进程并隐藏窗口重启Desktop。Windows CLI恢复，后三轮均通过正常入口完成run/inspect/rm及readback。未修改Docker配置、镜像、volume或生产。早期内部socket仅用于核对身份后的owned清理，不是最终qualification路径。

| Attempt（全部在backend/nq-app/target保留） | Result / disposition |
| --- | --- |
| l5-compile-attempt01.log | test-compile PASS |
| l5-s1-attempt01.log及同名raw-proof | 11 JUnit中10 PASS、1 SETUP_ERROR；无业务启动，不是生产P1 |
| l5-s1-attempt02.log | 第6个快速请求被真实RateLimitRule拒绝；补测试driver pacing，未放宽风控 |
| l5-s1-attempt03.log | PASS，被最终有界fixture及增强账务/资源oracle的S1替代 |
| l5-s1-attempt04.log | 最终S1及driver tests PASS，0 skipped |
| l5-s2-attempt01.log / l5-s3-attempt01.log | 最终S2/S3 PASS，0 skipped |
| secret-check前两次inventory尝试 | Linux Git无法解析Windows绝对worktree gitdir，保留工具失败 |

secret inventory重复问题按RECURRING_PROBLEM_ROOT_CAUSE_REMEDIATION定位平台Git选择；safe.directory不解决路径格式，用已有verifier的--git /mnt/d/Tool/Git/cmd/git.exe恢复。未修改gitdir、scanner、allowlist。首次BLOCKED报告/summary另留target/l5-initial-blocked-report.md及l5-initial-blocked-summary.json；本文保留失败并记录最终恢复。

## 检查与复现

| Check | Result |
| --- | --- |
| B0FixtureSafetyTest | 8/8 PASS |
| SyntheticEvidenceExportTest / existing Python identity | 2/2及8/8 PASS |
| L5DriverContractTest | 2/2 PASS，拒绝故障/越界/格式错误命令 |
| L5 measurement离线测试 | 4/4 PASS，真实raw正例、17/17/19 mutations、secret/schema拒绝、双射/joins及原始文件不变 |
| S1 / S2 / S3 | 最终三次目标Maven BUILD SUCCESS，0 failures/errors/skipped |
| stage-assets | PASS，1887 scanned / 173 exceptions / errors=0 |
| Gitleaks / secret negatives | pinned8.18.4、实际CI配置，3869 safe files、0 findings；6/6 negatives REJECTED |
| 新证据扫描 / links / diff | 见summary.json最终验证记录 |
| Full Maven / independent review | NOT_RUN；按本批约定SELF_REVIEWED |

从仓库根目录运行（level依次S1/S2/S3；S2/S3最终命令仅选L5BoundedWorkloadTest）：

```powershell
$env:MAVEN_OPTS='-Xmx512m'
mvn -o -f backend/pom.xml -pl nq-app -am test '-Dtest=L5BoundedWorkloadTest,L5DriverContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-DargLine=-Xmx1024m' '-Dnq.l5=true' '-Dnq.l5.level=S1'
```

log的L5_ROOT定位raw-proof.json；原始摘要见summary。离线测试：`python backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/test_l5_measurement.py <S1 raw-proof.json> <S2 raw-proof.json> <S3 raw-proof.json>`。单份重导出使用同目录l5_measurement.py。

自查覆盖无故障入口、B0默认行为、读写隔离、真实risk pacing、有界循环/资源、关系oracle及原始失败保留。P0=0、P1=0；既有P2/P3继续OPEN/NON_BLOCKING。改动是summary.candidate所列10个test/tooling文件及本目录5份evidence；规划未改。

```text
PASS / PHASE6_L5_BOUNDED_WORKLOAD_AND_MEASUREMENT_ACCEPTED /
S1_PASS / S2_PASS / S3_PASS / BOUNDED_SCALE_CORRECTNESS_PRESERVED /
MEASUREMENT_BASELINE_ESTABLISHED / ACTIONABLE_BACKLOG_CONVERGED /
RESOURCE_USAGE_BOUNDED / NO_DUPLICATE_MUTATION / NO_DUPLICATE_ACCOUNTING /
P0_0 / P1_0 / READY_FOR_L5_CONCURRENT_BACKLOG
L5 != ACCEPTED
```
