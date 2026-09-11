# Reconciliation Same-State Convergence Correctness Review

## Attempt 01 — 2026-09-11 至 2026-09-12（Asia/Shanghai）

**PASS / RECONCILIATION_SAME_STATE_CONVERGENCE_CORRECTNESS_REVIEW_ACCEPTED / TARGET_QUALIFICATION_BLOCKER_CLOSED / READY_FOR_PRECISE_DELIVERY**。

这是对当前未提交候选的独立正确性审查，不是 L6 qualification、发布授权或全仓审计。范围内确认 P0=0、P1=0；本次目标 P2 qualification blocker 已有独立关闭证据。`L6=NOT_ACCEPTED / L6_A_RESUMED=NO / SOAK=NOT_RUN`。

### Review independence 与候选身份

本审查会话未参与候选生产实现、候选测试或实现交接证据的编写；本轮仅编写隔离审查探针及本文。实现方结论作为待核查数据，未用其 PASS 自证接受。执行依据是本次用户审查合同；历史 instruction-system/governance review 的 `.agents.review-subject` bootstrap 不适用于此次业务代码审查。使用当前交易正确性证明参考检查并发、事务与恢复边界。

入口及出口实际身份：

| 项 | 独立核验 |
| --- | --- |
| Repository | `E:\Project\nexus-quant-gateaudit` |
| Branch | `audit/post-gatey-agent-baseline` |
| HEAD | `23548b75093a62d7614e16f8abcaf9ff2ea32ed7` |
| Upstream | `origin/audit/post-gatey-agent-baseline`，实际已配置，未改配置/未 fetch |
| Candidate | [convergence-candidate.json](convergence-candidate.json)，19 个 backend 修改/未跟踪输入全部匹配，无遗漏 backend delta |
| Manifest SHA-256 | `39dd19b9ed7573137ec309165a4a577713291617d3fcea719f4c7256b656f338` |
| Candidate fingerprint | `d05ae696cfe8e2dadef5ba25b4039907fef5b4fe1a2dd9933ab1c646c75a0e20`，入口/出口相同 |
| Fingerprint 算法 | 按 manifest 顺序，对每个实际原始文件计算 SHA-256，以 `path + 空格 + sha256` 组成 LF 分隔、无尾换行的 UTF-8 文本，再 SHA-256；独立重算匹配 |
| 工作区 | 入口 10 modified、17 untracked；允许 dirty candidate；暂存区 0 |

[convergence-evidence-manifest.json](convergence-evidence-manifest.json) 的 7 个条目全部匹配；原 readiness summary 引用的 9 个 artifact 及 validation 引用的 10 个日志全部匹配。保存 3930 个 Git 可见文件的原始字节哈希，逐文件复制到 owned 隔离副本并核验；覆盖生产、测试、untracked harness、资源、配置、POM、构建脚本。副本使用独立 Git 目录并只读复用本地对象，以支持测试内固定历史 scheduler 的 `git show`，没有在原工作区构建。

| 变更归属 | 核对结果 |
| --- | --- |
| 继承 readiness | B0Processes 的增量完整行读取、L5BoundedWorkloadTest helper 可见性、L6ActiveStabilityTest / L6NqProcessMain / L6ProcessOutputTest / L6QualificationControls / l6_oracle.py；B0NqProcessMain 中 L6 qualification 接线 |
| 本次整改 | 4 个 production 类；L6ConvergenceBaseline / BoundaryProcessTest / Controls / NqMain / ProcessTest；scheduler 目标测试；B0NqProcessMain 短时 convergence 接线；B4 V51 断言/标签 |
| 非运行证据/指导 | readiness 与 remediation 文档、JSON；engineering-lessons 的经验条目。保留原值，不将其纳入生产修改或由其扩权 |
| 历史目标候选差异 | `330ed30e…` 到最终 `d05ae696…` 唯一 backend 文件差异为 B4TradeEventRemediationTest 的 V50→V51 断言/标签；本轮 B4 直接运行最终候选，无旧失败冒充通过 |

### Files and call paths inspected

完整追踪本轮修改及必要持久化 owner：

- [OkxRestReconcileService](../../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java)：`reconcileOnce → reconcileObservedFacts → reconcileSingleOrder/reconcileFilledOrder/reconcileCancelledOrder → reconcileFills → alignOrderStatus → ensureLedgerConvergence`。
- [OrderLifecycleService](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderLifecycleService.java) 与 [OrderCommandService](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java)：对账专用 facade；普通 lifecycle/admission/ACK 未转入同态 no-op。
- [OrderCommandWriteService](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java)：`reconcileOrderStatus`、`transitionOrderAttempt`、`loadOrder`、普通 provider finalization 与 B2 correction 边界。
- `JdbcOrderRepository.compareAndSetStatus`：`order_id + expected status + expected version` 条件，成功才 `version+1`；未改变 SQL/状态机。
- `JdbcTradeRepository.insertWithRequiredEvent / ensureRequiredEvent`、`RequiredTradeEventStore.ensure`：Order/source 行锁、稳定 fill identity、TradeExecuted 原子写入/补齐；`TradeLedgerPostingService.postTrade` 与 `JdbcLedgerPostingRepository`：币种/品种锁、完整幂等分录判定、Ledger/Position/Snapshot 同事务。
- 所有选中测试入口、B0Fixture/B0Processes、L6ConvergenceControls、B4TransactionFaults/B4PgWireProxyMain、B2SyntheticVenueMain，以及当前 Java 21/POM 配置。Spring 真实 RiskGate、write proxy、adapter gateway 的 child composition 检查实际通过。

### 原失败机制与同态语义

原 `alignOrderStatus` 的状态查询与 writer 的事务回读之间没有互斥；A 已选择 FILLED 并停在 writer 事务之前，B 提交 FILLED 后，A 进入严格状态机先于 CAS 抛出 `invalid order transition: FILLED -> FILLED`。固定 HEAD 的原 scheduler 在独立 child classpath 中编译加载，同一 fixture 输入与同一屏障稳定复现；没有通过 mock 抛错、sleep 制造时序或概率性重跑。

旧 invocation 返回该错误；Spring 默认 recurring scheduler 实测 `TICKS 3 ERRORS 1`，下一周期仍运行。新候选相同交错返回 OK，scheduler=`TICKS 3 ERRORS 0`。原 readiness 异常发生于测试 stdin launcher，不能外推为生产 JVM 永久退出、永久停滞或账务损坏。原失败和最终状态未捕获的历史限制均保留。

新入口在真实事务中按 orderId 加载 durable Order，仅允许 ACCEPTED / PARTIALLY_FILLED / FILLED / CANCEL_REQUESTED / CANCELLED / CANCEL_REJECTED / REJECTED。相等只意味着这一次 **Order 状态对齐无需迁移**，不表示普通命令成功或整条账务链完成；NEW 拒绝、普通同态 transition 仍拒绝。该入口不接收 provider ACK，也不替代其冻结 generation 校验。

外层继续校验 venue/durable fill 的 order、account、symbol、side、venue、external identity、重复内容和数量上界；同态入口不是额外的通用鉴权/identity API。当前可达恢复路径通过这些检查后才对齐，随后继续 Trade/Event/Ledger。不同状态仍走原状态机和 CAS；CAS 输家回读 durable truth，不提升 version 重试旧意图。未新增广域 `catch(Exception) → continue`。

### 独立数据库证明

所有运行使用 owned digest-pinned PostgreSQL 16 镜像，实际 `16.15 (Debian 16.15-1.pgdg13+2)`；Flyway 51 migrations applied/validated，V51。NQ 与 Synthetic Venue 为独立 JVM/PID，均为 loopback，无真实交易所凭证。只读 checker 读取真实数据库快照；故障注入仅在 owned DB 撤销/恢复权限或丢弃 TCP 提交响应，没有 SQL 写入成功业务事实。

| 场景 | 实际断言/结果 |
| --- | --- |
| 两 JVM FILLED | NQ PID 23608 / 38112；赢家 v3→v4；输家与 3 次重放均 v4，Trade=1、Ledger=2、event_store=8、迁移 audit=4，完整业务快照不变 |
| 四 JVM FILLED | NQ PID 36928 / 25408 / 5540 / 17468；3 个输家均 OK；同一 v4、Trade=1、Ledger=2，事件和迁移 audit 无增长 |
| 两 JVM PARTIALLY_FILLED / scheduler | 部成 qty=4、一次推进；scheduler 3 ticks/0 errors；重放不增加版本或迁移事实 |
| 七个有限目标 | 每个目标连续 3 次 no-op，状态/reason/version/业务快照不变；普通同态 transition 与 NEW 拒绝 |
| CANCELLED 真实竞争 | 本地先到 CANCEL_REQUESTED，再由赢家到 CANCELLED；输家不重复最终迁移 |
| STALE_CAS | A 停在真实 CAS 前；B 持久化剩余成交并到 FILLED；A 的旧 PARTIALLY_FILLED 意图失利，返回 durable truth，不降级或刷新版本重试 |
| 不同目标冲突 | durable CANCELLED / desired FILLED 仍抛非法迁移；失败时业务快照不变；下次按完整 durable fills 走 B2 correction，一次 correction audit，重放稳定 |
| 已 FILLED 缺账 | 真实 Trade+TradeExecuted 提交后强杀，Ledger=0；新 JVM 补到2，Order v4不变，Position/latest BTC Snapshot=10；再次重放全部业务快照相同 |
| 同态输家直接补账（独立探针） | A停在writer前；B已FILLED、Trade=1、TradeExecuted=1、Ledger=0时死亡；A进入新同态分支后继续补Ledger=2，Order行及迁移audit不变；重放稳定 |
| identity 负例（独立探针） | FILLED订单重放时，loopback转发器只把真实venue fill响应的ordId改成错误值；抛 `REPORT_EXTERNAL_ORDER_ID_MISMATCH`，业务快照不变；恢复正确响应后重放成功 |
| DB read / CAS write 负例（独立探针） | 在事务入口/CAS屏障处撤销 owned app role 的 SELECT/UPDATE；PostgreSQL日志确认 `permission denied for table orders`；调用返回 ERROR、无新增业务事实。恢复权限后正常收敛且重放稳定 |
| 新事务提交响应未知（独立探针） | 同态 no-op 与真实 FILLED transition 两种场景，wire收到服务器COMMIT确认后扣留响应，再断连。均返回 `ERROR JDBC rollback failed`，日志保留原 `DataAccessResourceFailureException: JDBC commit` 及回滚异常链；没有将数据库结果未知转为成功。reader检查已提交事实；独立actor继续恢复，重放无变化 |
| batch forward progress | B只完成第一候选时还有1 ACCEPTED；A竞争后继续第二候选，最终2 FILLED / 2 Trade / 8 Ledger，各Order v4；重放不变 |

状态事件/审计归因：普通 `transitionOrderAttempt` 的真实 CAS 成功追加 `ORDER_STATUS_TRANSITION`，未新增 OrderStatusChanged publisher；同态不调用 CAS 或 audit。缺账恢复 event_store 从6到8属于 LedgerPosted/PositionUpdated，不是重复 Order transition。B2 correction 独立保留原事件和审计合同。诊断 audit（如 dedup/completed）仍可能追加，不声称 audit_logs 总数零增长。

故障分类：`reconcileOnce` 的 RuntimeException 分支记录 FAILURE 并重新抛出；真实失败没有被逐订单 continue 吞掉。NOT_FOUND / ledger拒绝仍沿用 UNRESOLVED 区分；正常 invocation 的 SUCCESS 不能解释为所有候选业务均完成。新增入口没有 catch；提交发生在 Spring proxy 返回之前，直接提交未知探针已证明外层不会收到伪成功。

### Tests independently executed / reused / skipped / not run

以下命令均在 owned 副本根目录执行，原命令 argv/cwd、环境变量名称 allowlist 与退出码保存在 raw evidence。Maven父进程去除继承 NQ/Spring datasource/provider/Java options；child再次使用B0 cleanEnvironment，并显式绑定owned DB。SIM/LIVE测试标签仅指隔离合成fixture。

```powershell
mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=L6ConvergenceProcessTest,L6ConvergenceBoundaryProcessTest,OkxRestReconcileServiceTest,B2RealProcessProofTest#v49AffectedOccAndPerFillRegression,B4TradeEventRemediationTest,B5DurableLifecycleCrashRecoveryTest#runningOwnerDeathAndDurableFillMustReleaseFutureWork' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6.convergence=true' '-Dnq.b2=true' '-Dnq.b4.remediation=true' '-Dnq.b4.case=LEGACY_TRADE_GAP' '-Dnq.b5.lifecycle=true'

mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=L6ConvergenceProcessTest,L6IndependentReviewProbeTest,L5FillIdempotencyTest#committedTradeWithLostResponseIsInspectedAndReplayedBySuccessor' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6.convergence=true' '-Dnq.l6.convergence.baseline=true' '-Dnq.l5.fill=true'

mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=L6ReviewCommitUnknownTest' '-Dsurefire.failIfNoSpecifiedTests=false'
```

| Run | 结果及覆盖 |
| --- | --- |
| 原候选定向组合 | exit0；26 tests / 0 failures / 0 errors / 0 skips：scheduler21、L6两个JUnit入口共12场景、B2 SIM/LIVE stale PLACE ACK+multi partial共4场景、B4 SIM/LIVE LEGACY_TRADE_GAP共2场景、B5 RUNNING owner death |
| Baseline + 第一组审查探针 + L5单方法 | exit0；3 / 0 / 0 / 0：旧scheduler两个原失败交错、4个新探针、真实Trade提交响应丢失后successor恢复 |
| 新Order事务提交未知探针最终run | exit0；1 / 0 / 0 / 0，SAME / CHANGE两场景 |
| 审查探针失败记录 | 两次额外Maven各1 / 1 / 0 / 0，均保留：第一次AOP标记追加晚于已启动invocation，未到wire屏障；第二次已到真实commit边界，但预期异常文案与实际JdbcTransactionManager不符。修正仅涉及新探针安装顺序与精确异常链oracle，原状态/恢复断言全部保留；不计作候选通过或新生产缺陷 |
| 审查准备工具失败 | UTF-8源码被Python默认GBK解码，发生于Maven前；改为显式UTF-8，记录保留 |
| Full Maven 复用 | 独立验证日志/ZIP/hash/镜像/报告后复用；不重跑 |
| 未运行 | L6-A readiness主流程、60/180分钟soak、整套L4/L5 qualification、远端CI、生产操作 |

Full Maven：日志 `63fc0b8af02d3aa578ceed4d575a7d0367a4f89bf243abdc742202fa862da83b`、surefire.zip `742ac1303b24b362be66995a1d65ddcd0f4de98b0a3b7f566d784c66814364d0` 匹配；ZIP完整性通过，828份报告；日志逐suite合计1934/0/0/148，XML合计1929/0/0/148。两组architecture suite重复报告多出5，未虚增XML总数。3928个Full镜像输入逐字节匹配当时清单，和当前技术输入无差异；当前已有清单条目的差异仅最终remediation说明文档，不是代码/夹具/配置变化。

148条conditional skips单列保存在 `full-reuse-check.json`，包括L6Convergence两个入口、B2、B4及B5等opt-in证明；本轮选中的必需场景都已显式启用且0 skips，其余历史未选场景不由Full绿色冒充执行。最初Full失败1934/1/5/149、默认datasource回滚事故和后续长路径收集失败仍是历史失败，未改写，也未连接该默认datasource复查。

### L4/L5 proof impact and reuse

| 已接受证明部分 | 本候选处置 |
| --- | --- |
| L4 风控前置、V49一次性mutation authority、V50窗口身份、V51 immutable execution、migration/环境隔离 | 实现未改；保留原接受身份及证据，不重新打开整批。B5 owner-death→durable fill释放未来窗口作为相邻执行验证 |
| L4 B2 外部结果/取消终态/多笔成交、B4 Trade/Event缺口、B5 reconciliation依赖 | 不无条件继承新scheduler行为；由本轮B2/B4/B5定向回归、不同目标/CAS冲突、缺账与提交未知探针承接 |
| L5 fill幂等、Position/Snapshot事务投影 | owner实现未改；同态输家补账、两/四JVM重放、真实Trade响应丢失与replay直接验证受影响调用链，历史并发source锁/投影证明保留 |
| L5 S/C规模、F反复故障、Kill-under-load及有界资源观测 | 历史结果仍绑定原接受候选；本次不重写其20行矩阵，不把短时定向测试宣布为新候选完整scale/Kill/长时资格。恢复语义部分由上述定向证据承接，其余未修改机制按原覆盖范围复用 |
| 仍需未来证明 | L6 readiness/长期运行及timer/resource稳定性；>4 JVM、>240候选、真实provider/LIVE、多日运行、14个inactive/future义务均不计PASS。本次目标整改无剩余必需证据缺口 |

已有ordinary concurrent INSERT loser P2、wildcard-import P3、HISTORICAL_PROJECTION_REPAIR_REQUIRED及pre-freeze/release-baseline义务保持原处置，未由本轮P0/P1=0清零。本文不修改STATUS机器区块或L5/L6冻结规划。

### Raw evidence / 探针差异 / candidate drift / cleanup

Owned evidence根目录：`C:\Users\Lingyu\AppData\Local\Temp\nq-l6-review-20260911-attempt01`，源码镜像位于其 `candidate`。原始随机fixture identity仅留在此临时目录，不导出到本文。`artifact-index.json`记录284个原始日志、proof、命令、探针和来源文件的路径/哈希，SHA-256=`7d45b1f8f0e3ee38dfacb9a7b9f19325c1ffec7e58a97d3ca1a08687a995333a`。

| 核心 artifact | SHA-256 |
| --- | --- |
| targeted.log | `5b571eb025ae1f0961f1a5be23932d7618c023e435f11e4e0af21c4feff27690` |
| probe.log | `5ad0dc7ff4405c95c6a96b2565c6206b60e1fa672b28af61e5cfd2bb174b8429` |
| commit.log（最终） | `ccac8321cc7c5fd727f599df21b816bf85018196a7120cf0a85b1a4426c4b9c9` |
| full-reuse-check.json | `93f7b0e4b9f7bb492c0f35507b3512b8ae280c35a54a400636a5e772a74c6487` |
| cleanup.json | `71fd1ccac8bbe8167c796150ab43b505783037d34f4647ab3cef46ed12b58ee1` |

探针是副本中额外的三个测试源文件，未改原候选输入：`L6IndependentReviewProbeTest.java` SHA=`adabf2bed385bfcea5c8858347eabcad0ebb3281924e1e2c1b6ccf18991ad0ce`；`L6ReviewCommitUnknownTest.java` SHA=`067baade82095e5882c3432b9a2f51c5a216f8d47d47b6f964da35c8a3a5769c`；`L6ReviewNqMain.java` SHA=`3c6d9dd0a33e46c84046d7c9bb53c1305242822455e22b2e2dc3879dfe92349b`。最后一个由原B0NqProcessMain生成：仅改类名、启用短时convergence控制器、新增CONVERGE事务故障标记接线；业务实现及Spring事务管理器不替换。生成脚本、两次失败版本、异常诊断与最终版本均保留。上述结果明确属于review probe，不冒充原候选自带测试。

结束复核：3930个入口文件在原工作区及副本中均无字节变化；19项候选fingerprint不变；原证据日志哈希不变。原工作区仅新增本文，暂存区仍0，`git diff --check`通过。本轮创建11个PG容器，逐个按持有ID核对已不存在；96个记录的子进程PID核对无本轮命令仍存活，NQ/Venue/wire均已回收。保留owned临时目录供复查；未操作其他任务资源或删除历史输出。

### Findings / Final decision / Next action

原 `L6_RECONCILIATION_SAME_STATE_RACE` 保持 **P2 / QUALIFICATION_BLOCKING** 的历史严重性与影响分类；本轮状态从 `REMEDIATED_PENDING_INDEPENDENT_VERIFICATION` 转为 **INDEPENDENTLY_VERIFIED / TARGET_QUALIFICATION_BLOCKER_CLOSED**，不以降低严重性换取PASS。证据为固定旧失败、新同态两/四JVM、无version/迁移事实churn、真实缺账/冲突/数据库及提交未知负例、batch和邻接回归。范围内未发现新增生产P0/P1或未关闭的目标P2阻断。

```text
PASS
RECONCILIATION_SAME_STATE_CONVERGENCE_CORRECTNESS_REVIEW_ACCEPTED
TARGET_QUALIFICATION_BLOCKER_CLOSED
READY_FOR_PRECISE_DELIVERY
L6=NOT_ACCEPTED
L6_A_RESUMED=NO
SOAK=NOT_RUN
STAGE=0
COMMIT=NONE
PUSH=NONE
PR/MERGE/TAG=NONE
```

下一动作：以该精确候选及本独立审查证据进入单独授权的precise delivery。本文不执行commit/push、不自动恢复L6-A；发布后的exact-head CI与后续readiness/soak仍按其任务合同办理。
