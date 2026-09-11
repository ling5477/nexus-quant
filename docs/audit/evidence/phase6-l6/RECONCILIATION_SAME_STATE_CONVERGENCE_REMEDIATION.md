# L6 Reconciliation Same-State Convergence Remediation

任务：`NQ-GATEAUDIT-PHASE6-L6-RECONCILIATION-SAME-STATE-CONVERGENCE-REMEDIATION`。

状态：`IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW / L6_RECONCILIATION_SAME_STATE_CONVERGENCE_REMEDIATED / CONCURRENT_CONVERGENCE_IDEMPOTENCY_PROVEN / NO_VERSION_OR_EVENT_CHURN / ACCOUNTING_RECOVERY_PRESERVED / P0_0 / LOCAL_P1_0`。Severity=`P2_RECONCILIATION_SAME_STATE_CONCURRENCY_REMEDIATED`。本证据不是独立审查或 L6 acceptance。L6=`NOT_ACCEPTED`；L6-A 仍为 `BLOCKED / READINESS_FAILED`，60-minute qualification=`NOT_STARTED`。[原 readiness 失败](ACTIVE_STABILITY.md)、[原汇总](summary.json)和 metrics 均保留，不改写历史。

## 候选与授权

基线 HEAD=`23548b75093a62d7614e16f8abcaf9ff2ea32ed7`。本轮 `stage=0 / commit=NONE / push=NONE`。无 migration 文件、`.github/**` 或运行授权变更；未运行真实交易所、LIVE 或 L6 soak。首次 Full Maven 的默认 local datasource 事故与回滚证据单独记录于下，不将其表述为隔离验证。

入口已有 B0NqProcessMain、B0Processes、L5BoundedWorkloadTest 的未提交改动，以及 L6ActiveStabilityTest、L6NqProcessMain、L6ProcessOutputTest、L6QualificationControls、l6_oracle.py 和 phase6-l6 evidence。本轮保留上述内容，仅在 B0NqProcessMain 添加显式启用的短时 convergence 控制器接线。实现和测试候选绑定于随附 manifest；继承的 readiness harness 不因此取得资格。

## 真实调用链与 owner

1. `OkxRestReconcileService.reconcileOnce` → `reconcileObservedFacts` → `OrderCommandService.reserveReconciliationCandidates`：V48 有界游标选取 OKX 候选（含 FILLED/CANCELLED），扫描得到的 Order 用于路由、身份和外部查询。
2. 非终态走 `reconcileSingleOrder` → real `OkxExchangeAdapter.getOrder`，由 externalStatus 选择目标；必要时沿用原子 externalOrderId 绑定。
3. `reconcileFills` 读取真实 venue reports 与 durable Trade，校验身份、重复报告一致性、正数量及并集 overfill，之后执行 `alignValidatedStatus`。**当前接受的顺序是校验 → Order 状态对齐 → Trade/Event → Ledger**，并非全部记账后才改订单；本轮不改变此事务契约。
4. `alignOrderStatus` 再读最新 Order，做同态/终态快速检查，必要时先推进 CANCEL_REJECTED、ACCEPTED 或 CANCEL_REQUESTED 中间态。这次读取保护路由，但不能锁住后续写入。
5. lifecycle → command facade → `OrderCommandWriteService` 的 Spring `@Transactional` 入口再次读取 Order。原 `transitionOrderAttempt` 先调用状态机，再检查版本耗尽，再执行 expected-status/version CAS。竞态的关键是第 4 步与第 5 步之间的提交窗口。
6. version owner 是 repository CAS（成功才加一）与对应的 `OrderRecord.withStatus` 返回映射；状态迁移审计 owner 是 `transitionOrderAttempt`。CAS 失利返回最新 durable Order，不刷新版本重试旧意图。
7. 普通 `transitionOrder` 本身不额外发布 OrderStatusChanged；已有 command finalization/B2 correction 的发布职责保持原状。本轮不新增事件。Trade/TradeExecuted 的原子 owner 是 `insertWithRequiredEvent`，`ensureRequiredEvent` 负责既存缺口；Ledger/Position/Snapshot 沿用真实幂等记账事务。

源码 owner：[OkxRestReconcileService](../../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java)、[OrderLifecycleService](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderLifecycleService.java)、[OrderCommandService](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java)、[OrderCommandWriteService](../../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java)，以及未修改的 OrderRepository/JdbcOrderRepository、JdbcTradeRepository 与 ledger 实现。

## 修复前复现与影响分类

在修改 production 前，使用新建 PG **16.15 / V51**、独立 Synthetic Venue JVM 和两个真实 Spring NQ JVM：A 已决定 FILLED，在进入 writer 事务之前暂停；B 对同一个 Order/venue truth 完成 FILLED、Trade/Event/Ledger 提交；恢复 A，稳定取得 `invalid order transition: FILLED -> FILLED`。父控制器通过 reader role 只读数据库，不编造业务事实。

- **Invocation/batch**：原 `reconcileOnce` 记录 FAILURE 并重新抛出异常；for-loop 没有逐订单 catch，所以剩余候选不会在该 invocation 内处理。静态调用链证实 batch 边界；修复后的两候选动态证明见下。
- **Scheduler**：真实 `scheduledReconcile` 在 Spring `ThreadPoolTaskScheduler` 默认 recurring-task error handler 下运行，原异常确实逃出方法；记录 `TICKS 3 ERRORS 1`，后续轮次继续。测试复用默认错误处理，不声明正式 L6 timer qualification。
- **JVM**：父进程捕获 invocation 结果的 actor 保持存活，下一次 `RECOVER` 返回 0；原 readiness 的 stdin launcher 未捕获异常才退出。不能将该 test entry 退出等同于生产 JVM 永久退出。
- **Recovery**：未修复时下一次正常周期已按 FILLED 分支恢复/重放，状态、version 和业务事实与 B 提交后的快照一致。

分类：**P2 / QUALIFICATION_BLOCKING**。没有观察到该同态故障造成 ordinary runtime permanent liveness/correctness loss；不预设 P1，也不将 qualification 中断写为无影响。范围内 P0 confirmed=0、LOCAL_P1 confirmed=0，不代表其他历史残余清零。

原始复现命令是在 production 未修改时运行；永久 reproduction 开关现在编译固定基线 HEAD 的原 scheduler，在独立子 JVM 前置 classpath，仍调用未改变的严格普通 transitionOrder，不通过 mock 抛异常。

## 新收敛契约及目标清单

新增 `OrderLifecycleService.reconcileExternalStatus` → facade → transactional `OrderCommandWriteService.reconcileOrderStatus`。在写入所用的事务回读上，`current.status == desired` 即为 `ALREADY_CONVERGED`，返回原 Order，**不调用状态机、不 CAS、不修改 reason/version、不产生状态迁移 audit/event**。返回的是既有 OrderRecord 契约；ALREADY_CONVERGED 是语义，不新增外部 API 状态码。

| 目标 | 当前可达来源 | 同态语义 |
| --- | --- | --- |
| ACCEPTED | OKX live/effective；SENT→partial 的中间确认 | no-op |
| PARTIALLY_FILLED | OKX partially_filled | no-op |
| FILLED | OKX filled | no-op |
| CANCELLED | OKX canceled/cancelled，包括先补 CANCEL_REQUESTED 的分支 | no-op |
| REJECTED | OKX order_failed/rejected | no-op |
| CANCEL_REQUESTED | 对齐撤单终态时的中间步骤 | no-op |
| CANCEL_REJECTED | CANCEL_REQUESTED 对齐非 CANCELLED 外部事实时的中间步骤 | no-op |

OKX 未知状态 fallback 为 SENT；现有 external lifecycle 并不允许把其他状态迁移为 SENT，本轮不扩大该目标集合。原有同态 SENT 快速返回仍保留。NEW 等非对账目标在新入口明确拒绝。

状态机完全未改。ordinary lifecycle、manual command、PLACE/CANCEL admission、原 applyExternalStatus 均不使用新 no-op 契约。不同目标继续经过原状态机与 OCC。原外层终态快速返回、B2 full-fill correction、fresh-state read、overfill/identity guards 均保留。

## Accounting 与审计范围

同态只跳过 Order transition，`reconcileFills` 仍继续逐 fill 去重、TradeExecuted 恢复及 Ledger/Position/Snapshot 收敛。FILLED 分支仍可补缺失账务。

证明比较 orders、trades、ledger_entries/events、positions、account_snapshots、execution facts 的完整快照，并检查 event_store 数量与 `ORDER_STATUS_TRANSITION` 数量。**零增长指业务状态迁移事实**。既有 `OKX_FILL_DEDUP_HIT`、`OKX_*_COMPLETED` 等诊断审计可能继续追加；不宣称 audit_logs 总行数不增长，也不把检查记录伪装成状态变化。

## 验证与失败保留

原始运行日志、PID、真实 fixture identity 和 DB 快照保存在 ignored `backend/nq-app/target/l6-convergence/` 及 B2/B4/B5 对应 target，未将原始随机身份导出为 Git evidence。

- `baseline-maven.log`：首次 fixture 断言错误，零手续费实际为 2 条分录，错误预期 4；原同态异常已被捕获，失败保留。
- `baseline-maven-02.log`：未修改 production 的 invocation/scheduler 原故障证明通过；PASS 指证实预期故障和自然恢复，**不是旧 production 健康**。
- `boundary-01.log`：新测试同时启动两个 Spring child，发生 admin bootstrap 唯一键冲突，业务场景尚未开始。改为逐个 awaitReady 后再制造业务并发；未修改生产 bootstrap。
- `targeted-01.log`、`targeted-02.log`：阶段性目标验证通过；不替代最终候选证据。

### 最终目标证明

| 证明 | 实际结果 |
| --- | --- |
| 两 JVM FILLED / 四 JVM FILLED | A（四路时三个 loser）均返回 OK；赢家 v3→v4，其他 actor 与三次重放保持 v4；Trade=1、TradeExecuted=1、零手续费 Ledger=2、Position/BTC Snapshot=10 |
| Spring scheduler 相同竞争 | 3 次执行、0 error，actor 存活 |
| 两 JVM PARTIALLY_FILLED | 赢家推进一次；重放无 version/event churn，Position/BTC Snapshot=4 |
| 已 FILLED、Trade+Event 已提交而 Ledger 缺失 | 在真实提交后屏障强杀，Ledger=0；新 JVM 正常 reconcile 补为 2，Order version 不变；再次重放完整业务快照一致 |
| 七个 finite target 同态 | 每个在真实 Spring/PG writer 上连续 3 次 no-op；普通 transition 同态仍拒绝；NEW 仍拒绝 |
| CANCELLED 实际对账竞争 | 合成 venue 取消生效，本地由 ACCEPTED 先经 CANCEL_REQUESTED；最终 CANCELLED 由 B 提交一次，A 恢复无异常/无重复变更 |
| 旧 PARTIALLY_FILLED 观察的 CAS 失利 | A 在真实 repository CAS 前暂停；B 增加剩余 fill 并提交 FILLED；A 返回 durable truth，不降级、不重新提升 version |
| FILLED desired / CANCELLED durable 冲突 | 继续拒绝非法普通迁移，不吞异常；下一次正常对账按完整 durable fills 走 B2 correction 到 FILLED，纠正审计一次 |
| 两候选 batch | B 仅处理第一个候选；A 恢复后继续第二个，最终 2 FILLED / 2 Trade / 8 Ledger，重放快照一致 |
| B2 直接回归 | SIM/LIVE 的 stale PLACE ACK 和多笔 partial fill，共 4 场景通过 |
| B4 直接回归 | SIM/LIVE 的 LEGACY_TRADE_GAP：旧真实 producer 提交后死亡形成 TradeExecuted 缺口，当前恢复与重复重启通过；无 SQL 删除事实 |
| B5 直接回归 | RUNNING owner death 后 durable fill 释放未来窗口通过 |

`targeted-final.log` 中 scheduler unit=21/21、新增 L6 两个 JUnit 入口（共 12 个场景）、B2 与 B5 均通过；**该组合命令整体失败**，因为 B4 旧测试仍硬编码 V50（实际 V51）。本轮只将 B4 的版本断言及证据 schema 标签改为 V51，随后 `b4-baseline-final.log` 中 B4 与永久旧竞态 reproduction 均通过，2 tests / 0 failures / 0 errors / 0 skips。

目标组合命令：

```powershell
mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=L6ConvergenceProcessTest,L6ConvergenceBoundaryProcessTest,OkxRestReconcileServiceTest,B2RealProcessProofTest#v49AffectedOccAndPerFillRegression,B4TradeEventRemediationTest,B5DurableLifecycleCrashRecoveryTest#runningOwnerDeathAndDurableFillMustReleaseFutureWork' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6.convergence=true' '-Dnq.b2=true' '-Dnq.b4.remediation=true' '-Dnq.b4.case=LEGACY_TRADE_GAP' '-Dnq.b5.lifecycle=true'

mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=L6ConvergenceProcessTest,B4TradeEventRemediationTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6.convergence=true' '-Dnq.l6.convergence.baseline=true' '-Dnq.b4.remediation=true' '-Dnq.b4.case=LEGACY_TRADE_GAP'
```

### Full Maven 与身份

首次 `mvn -f backend/pom.xml test`：1934 tests / 1 failure / 5 errors / 149 skips / exit 1，保留 `full-maven.log`、exit 及 `full-failed-surefire/`。架构测试扫描到历史 target 内的源码副本；restart tests 缺少 SPRING_DATASOURCE_URL；另有 Spring local context 落入默认数据源，public schema=44 的 V45 尝试因 FK 失败，日志明确 `Changes successfully rolled back`。未对该默认数据源执行 repair、数据改写或后续探查，不将失败 run 作为正确性证据。

修复验证环境不改候选：复用已有 clean-full runner 的隔离流程，将 3928 个 Git 可见文件（排除 target）逐文件 SHA-256 校验复制到仓库外的干净目录；使用 allowlist 环境、新建 digest-pinned PG16 容器、现有 `BackendCiLegacyAccountFixture`，先确认 V51、canonical test account=1、exchange accounts/credentials=0，再绑定两组 NQ_DB / SPRING_DATASOURCE 后运行同一个 `mvn -f backend/pom.xml test`。不跳过或修改失败的测试/状态机/migration，不清理历史证据以避开架构断言。

当前 technical manifest：[convergence-candidate.json](convergence-candidate.json)，base HEAD 加 19 个 backend 变更/未跟踪输入，raw-byte fingerprint=`d05ae696cfe8e2dadef5ba25b4039907fef5b4fe1a2dd9933ab1c646c75a0e20`。目标组合时 fingerprint=`330ed30e6fae0218061aafca837dffa31d74644bc25f5ebdb643dc3335a8c78e`；差异仅 B4 测试的两处 V50→V51 描述/断言，无 production、L6/B2/B5 fixture 差异。B4 重跑与最终 Full Maven 绑定最终 manifest；不将旧 B4 失败改为 PASS。

最终 Full Maven：**BUILD SUCCESS / exit 0 / failures=0 / errors=0**。Maven aggregate=`1934 tests / 148 conditional skips`，不是 CI job skips；其中两个 architecture suite 名称重复报告合计 5 个测试，最后落盘 XML 合计为 1929，逐 suite 最后一条报告与 XML 一致，明细留在 collector 结果中。L6 核心 PG/多 JVM 证明通过显式开关运行，不以 Full Maven 的 conditional skip 代替。

日志=`backend/nq-app/target/l6-convergence/full-isolated/full-maven.log`；SHA-256=`63fc0b8af02d3aa578ceed4d575a7d0367a4f89bf243abdc742202fa862da83b`。Full 后普通目录复制报告遇到 Windows 长路径异常，原 driver failure 保留；从完整镜像收集 828 份报告到 `surefire.zip` 并校验 ZIP、log/module totals、XML 与源文件 hash，`collected-result.json=PASS`。该收集修复没有重跑 Maven或改候选。code drift=0，镜像字节匹配，owned PG 容器已清理；干净源码镜像与原始报告保留以供审查。

最终执行摘要与日志绑定：[convergence-validation.json](convergence-validation.json)。技术实现只运行了一次完成的 canonical isolated Full Maven；此前失败 attempt 仍为 FAIL，没有用目标测试覆盖它，也没有宣称首次命令通过。

## 审查交接

本轮为 implementation，尚未执行独立审查，不自审为 ACCEPTED。下一动作保持：`NQ-GATEAUDIT-PHASE6-L6-RECONCILIATION-SAME-STATE-INDEPENDENT-CORRECTNESS-REVIEW`。重点核对候选身份、同态 no-op、version/event 无 churn、缺账恢复、真实冲突保护及 batch liveness；独立审查完成前不恢复 L6-A。

已在现有 engineering-lessons 合并 `Concurrent Convergence No-op Rule`，未新增 Skill。既存 P2/P3、历史 projection repair 与 inactive/future obligations 均不在本轮宣称关闭。
