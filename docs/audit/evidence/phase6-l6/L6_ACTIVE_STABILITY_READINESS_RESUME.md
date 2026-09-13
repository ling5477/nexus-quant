# L6 Active Stability Readiness Resume

结论：**BLOCKED / L6_MANDATORY_MEASUREMENT_NOT_READY / L6_READINESS_INFRASTRUCTURE_INCOMPLETE**。

本轮短时业务 workload 实测通过，原 FILLED → FILLED 异常未复现；现有 measurement/controller 尚不能支持完整正式资格。不能输出 READY_TO_START_L6_A_60MIN。L6=NOT_ACCEPTED、L6_A=NOT_ACCEPTED、SOAK=NOT_RUN。

## 基线和执行

- Branch：`audit/post-gatey-agent-baseline`；HEAD/upstream/remote SHA 均为 `b733b75b7d5b018dc2f72f508ba11d1450a3bddb`；tree=`b4aeb5a3bee8c6195c36fdebc35486b3769ac012`。
- [Exact-head CI 34622315529](https://github.com/ling5477/nexus-quant/actions/runs/34622315529)：completed/success，9/9 success，failed/cancelled/skipped=0，本轮重新查询。
- [冻结 planning](../GATEAUDIT_PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN.md) SHA256=`80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`。
- 已读取并绑定原 remediation、独立 correctness review、delivery acceptance、原 readiness 失败及其9个 raw artifacts，现有 L6 主入口、launcher、controls、oracle。全部 Git 可见输入逐文件绑定，结束重算无漂移。
- 唯一入口未提交文件 [delivery acceptance](RECONCILIATION_SAME_STATE_CONVERGENCE_DELIVERY_ACCEPTANCE.md) SHA256=`605ff2fbeb122d5def94951840c2bf112804207f81e4fda79bd2f5b99a2ba233`；原内容保持不变。
- 环境：Windows 11 Pro 10.0.26200；JDK 21（完整 build 见机器汇总）；owned PostgreSQL 16.15，V51；镜像 `postgres:16@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`。仅 loopback，固定缓存镜像，PG 768MiB、tmpfs 256MiB，NQ 每个 Xmx512m、Venue Xmx256m，Maven/controller 各512MiB；入口可用内存约26.5GiB、磁盘约215.8GiB，预算低于60%内存安全线。
- Runtime PID：controller=31276；NQ=27960/41264；Venue=26588。实际2 actor、2策略，正常 CRON window；未降低并发或关闭 reconciliation。

```powershell
mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=L6ActiveStabilityTest,L6ProcessOutputTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6=true' '-Dnq.l6.diagnostic=true' '-DargLine=-Xmx512m'
```

父进程使用 OS/JDK 环境 allowlist，不继承 NQ/Spring/provider/Java options；子进程再次清空环境并由 B0 绑定 owned datasource。未读取 provider credential、未连接真实 provider、未启用 LIVE。命令2026-09-12 00:42:53至00:45:21（Asia/Shanghai），含构建共147.329秒，exit=0。2 tests / 0 failures / 0 errors / 0 skips，非Full Maven。

实际 warm-up=15.004919s，diagnostic active=90.0144941s，drain=10.5004897s；5个完整checkpoint、23个业务采样/轮次，两个NQ资源文件分别14/13条。正式60分钟计时未开始。

## 实际业务证据

5个checkpoint完整oracle全部通过，Order总量2→2→4→6→6；6个策略run均SUCCEEDED，6 Order/6 synthetic PLACE/6 fill/6 Trade/6 TradeExecuted/24 Ledger/24 Ledger events，Position BTC0.6，12个account snapshots，latest BTC0.6/USDT0。Paper链独立于ordinary账务链。

按身份核对 duplicate external mutation、Trade、TradeExecuted、accounting均0。所有checkpoint和完整最终快照未见durable orphan。原同态异常在两份NQ完整日志中出现次数0，两个actor各24次reconcile SUCCESS、FAILURE=0，真实工作经历多个策略窗口；这是本次runtime结果，不仅引用独立review，也不将有限无复现升级为所有交错证明。

额外两次 owned PG `nq_b0_reader` READ ONLY快照检查状态事件/审计：6订单各4条ORDER_STATUS_TRANSITION，expected_version/version为0→1→2→3→4，from/to连续且无同态记录，重复迁移审计0；OrderStatusChanged=0符合既有publisher职责，未把不存在的事件虚构为6条。先完成的订单重放仍v4；最终checkpoint全部v4。迁移审计诊断最后采样为00:45:05，早于最终00:45:17 checkpoint约12秒；最终版本/账务已核验，不能将该诊断时间点冒充teardown前完整audit快照。

游标revision=4→18→32→46→48，final actionable/backlog=0；后续window持续完成。两次额外只读诊断的oldest unresolved均NULL，含义是查询时无未决候选，不把NULL记为age=0；现有采样器尚无oldest-age时间序列或非空正对照。

现有oracle对本次5个checkpoint离线复算全部通过；Position、Snapshot、PLACE、Event、Ledger、orphan六个内存副本变异全部拒绝，未修改数据库、oracle或测试。首个额外只读诊断误用单数cursor表名而失败，事务为READ ONLY并中止；仅修正owned临时诊断SQL后读取成功，原错误及SQL保留，不是production失败。

## Timer、Paper与测量可用性

V51真实默认5s tick：NQ分别started/completed=24/24、23/23，failed=0。Validation真实timer分别触发一次，均ATTEMPT=1、DEGRADED=1、SUCCESS=0；日志availability=PARTIAL、freshnessStatus=UNKNOWN、blockerCount=3。证明timer实际执行，不代表validation健康；未观察异常或永久stall。未把UNKNOWN或DEGRADED改为成功。

独立合法PaperRun：normal monitor无alert；CRITICAL正例1条、F007 emitted=1；同日期report生成两次仍1条，alertCount=1。minimal report占位字段不作ordinary账务oracle。

| Measurement | 实际状态 |
| --- | --- |
| Heap / GC、threads | 可采；heap used/committed/max、GC count/time、线程数/名称；短时不判leak/plateau |
| Venue executor queue | 可采active/queue/completed/rejected/capacity；正常queue=0来自真实executor |
| Driver command queue | **缺口：资源导出写死commandQueue=0**；同步命令设计不能把该常量当实测queue/in-flight |
| Hikari | active/idle/pending/max可采，pending观测最大0；**未导出acquisition timeout count** |
| PG connections | appConnections=20，各checkpoint idleInTransaction=0；不要求idle pooled连接在运行期归零 |
| Handles | Windows 3个owned进程HandleCount/WorkingSet64可采；不声称Linux FD已证明 |
| Scheduler / scan | V51、validation观测及reconciliation游标可采，validation DEGRADED单列 |
| Backlog / age | backlog count可采；**oldest candidate age/identity周期序列缺失** |
| Audit/event、log、temp growth | 可采表行数/数据库大小、日志字节、owned目录文件数/字节；本run很短，不作长期增长接受 |
| Owned process/container | 生命周期PID及容器identity可核验，最终survivors=0 |

## 阻断处置

1. mandatory measurements尚不完整：driver queue/in-flight不可用、oldest-age序列缺失、pool acquisition timeout缺失。按用户§4E输出L6_MANDATORY_MEASUREMENT_NOT_READY；这些是P3 qualification tooling gaps，资格阻断，未证明production correctness defect。
2. 正式controller参数与本次冻结合同不符：[L6ActiveStabilityTest](../../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6ActiveStabilityTest.java)非diagnostic固定warmup=120s、active=3600s、drain=30s，并无对应时长property；当前任务要求600/2400/600s。禁止直接用现有非diagnostic入口声称10/40/10通过；本轮不改controller或planning。
3. validation的DEGRADED/PARTIAL/UNKNOWN保留为fixture待解释项，不能充SUCCESS；本轮只确认timer进度，没有将其升级为production P1。

P0 confirmed=0、P1 confirmed=0；目标same-state P2维持CLOSED，本次runtime无复现。既存ordinary concurrent INSERT loser P2、wildcard-import P3继续OPEN/NON_BLOCKING，HISTORICAL_PROJECTION_REPAIR_REQUIRED/pre-freeze义务及inactive/future obligations不变。没有新增已证实业务correctness failure；readyToStart60Min=false源于measurement/controller完整性，不能写L6_ACCEPTED。

## 清理、证据与下一步

主入口检查owned NQ/Venue退出、PG和Venue端口释放、owned PG容器不存在；独立复核controller/NQ/Venue/输出sanity子进程共5个PID均不存在，owned容器0。只保留本轮raw artifacts，未删除历史失败或其他任务资源。

[机器汇总](L6_ACTIVE_STABILITY_READINESS_RESUME.json)记录源身份、参数、数值、PID、完整JDK、measurement availability和raw hashes。原始证据目录 `C:\Users\Lingyu\AppData\Local\Temp\nq-l6-readiness-resume-20260912`；artifact-index.json同时指向原始run目录，保存全部checkpoint、日志、资源序列、只读诊断、Surefire及命令。历史9份raw artifacts重新核对hash不变；交付记录和所有入口Git可见文件hash不变。

本轮stage=0、commit=NONE、push=NONE，仅新增本说明和机器汇总为未提交evidence。Full Maven、完整L4/L5、新independent review、60/180min/overnight均NOT_RUN。阻断依据是本次用户§4E/§6/§9/§10，不是Skill要求新增审批；依照“不得在同任务顺手整改”，本轮未修harness。

下一步先在单独授权范围处理measurement/controller缺口并重新readiness；本轮未获READINESS_PASS，因此不得进入 `NQ-GATEAUDIT-PHASE6-L6-ACTIVE-STABILITY-60MIN-QUALIFICATION`。
