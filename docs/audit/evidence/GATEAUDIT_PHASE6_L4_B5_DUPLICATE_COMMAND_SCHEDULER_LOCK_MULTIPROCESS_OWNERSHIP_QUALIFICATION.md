# B5 duplicate command / scheduler / multiprocess ownership qualification

Task classification: HIGH_RISK / L4_QUALIFICATION / CONCURRENCY / MULTI_PROCESS_OWNERSHIP / NQ-only。

Starting HEAD: `86c8ad84542636364f6c21e78bc292a323cbdff7`；branch=`audit/post-gatey-agent-baseline`，本地 `origin/audit/post-gatey-agent-baseline` 引用一致（未 fetch）。起始工作区与 index 干净，`git diff --check` exit 0。本轮 stage=0 / commit=NONE / push=NONE。

Final decision: **FAIL / L4_B5_CORRECTNESS_FINDING / STOP / PRODUCTION_REMEDIATION_REQUIRED**。

P0=0；新增 confirmed P1=1：`ORDINARY_STALE_SENDER_MUTATION_AFTER_RECOVERY_FINALITY`。这是当前 ordinary sender 的真实可达问题，不把 dormant worker 的历史 finding 重新登记为 current。不声称 duplicate PLACE 或两个 mutation owner 已被复现。

B5=NOT_QUALIFIED / NOT_ACCEPTED / NOT_READY_FOR_PRECISE_DELIVERY。首次红色复现后停止剩余矩阵，没有修 production 再继续 qualification。

## 基线与范围

本轮输入声明 B0–B4=ACCEPTED、schema=V48、既有 P0/P1=0、F3–F6=OPEN/P2/NON_BLOCKING。当前 `docs/current/STATUS.md` 机器区块仍是 pre-B0，不能把用户给定的技术基线描述成已同步的 machine authority。本轮仅依据用户明确授权进行 disposable PostgreSQL / Synthetic Venue 验证，不写 current authority，不执行真实 provider、LIVE、真实账户 PLACE/CANCEL 或生产部署。

生产 Java、resources、Flyway、CI、AGENTS、Skills 均无修改。test-only 范围：B0 stdin 控制、原调用线程暂停屏障、现有 B2 Venue 查询响应、B5 红色测试与证据。启动前 fixture 的 DISENGAGED 仅为原 B0 sealed TEST_PRECONDITION，不是运行时解除生产 kill switch。

## Actual contracts

| Contract | 当前源码事实与证明边界 |
| --- | --- |
| Command idempotency contract | `TradingVerificationController.placeOrder` → `OrderCommandService.placeOrder` → `OrderCommandWriteService.preparePlaceOrder` → `AdapterBackedTradingVenueGateway.placeOrder`。identity 是 `(account_id, client_order_id)`；controller 构造 `accountId:clientOrderId`，V1 的 `uq_orders_account_client_order` 唯一约束与 V5 dedup_key 固化身份。service 已存在订单直接返回；首次插入/风控/推进到 SENT 在本地事务内，外发在事务外。唯一键只约束订单身份，不能视为跨外发的 lease。 |
| Scheduler contract | 唯一当前 scheduling processor 由 `ValidationEvidenceSchedulerConfiguration.EnabledConfiguration` 显式开关注册，只处理 `ValidationEvidenceScheduler`。`scheduledRefresh` → `runOnce` → lock → `ValidationEvidenceRefreshService.refresh` →只读 aggregate overview。不会创建 command 或 PLACE/CANCEL。相同开关可在两个 JVM 注册，未见单 JVM 检查。历史 recovery/reconcile/paper 的 `@Scheduled` 不因此激活。 |
| Ownership contract | ordinary 首次调用携带提交后的 `sentOrder` 继续外发，没有 durable worker owner/token/expiry。scheduler owner 是 `PostgresAdvisorySchedulerExecutionLock` 的 REQUIRES_NEW、read-only PostgreSQL 事务，`pg_try_advisory_xact_lock` 成功时运行 callback。两种 owner 不相同。 |
| Fencing contract | `JdbcOrderRepository.compareAndSetStatus` 用 status+version CAS 拒绝旧结果写入；不是网络发送 fencing。`OrderCommandService` 从 prepare 返回后直接进入 gateway；gateway 使用旧对象调用 adapter，无当前数据库状态/owner 检验。scheduler 没有 lease expiry/fencing token；timeout 在 callback 返回后检查，不能证明丢失连接会停止 Java callback。只读 callback 不得被当作交易 fencing。 |
| Takeover contract | scheduler 事务/连接终止时 PG 释放 advisory lock，后续 trigger 可尝试取得；本轮未执行其强杀证明。ordinary 没有可转移的 mutation lease；另一 JVM 可以独立进入 `OkxRecoveryService.rebuild`/`OkxRestReconcileService.reconcileOnce` 查询并恢复，不能谎称其接管了原 sender 的 mutation token。`ExecutionIntentService.claimAndExecute` production 引用只有自身重载，无当前 worker caller，保持 dormant。 |
| Recovery contract | ordinary recovery 不经过 validation scheduler lock。`OkxRecoveryService` 对 SENT 等状态 query-confirm，NOT_FOUND 经 canonical lifecycle 推进 CANCEL_REQUESTED/CANCELLED。本轮实际在 A 仍存活时由 B 完成；无 test SQL 推进订单状态。 |
| Production process assumption | 本轮没有访问部署环境，不能确认 production 实際 PID/副本数，也不能断言部署事实上单实例。源码不存在 ordinary mutation 的全局单进程约束；即使部署单 JVM，也不能据此排除同 JVM 并发 recovery。 |

直接源码索引（均为当前 HEAD）：

- [command owner](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java)、[transaction owner](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java)、[gateway](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/AdapterBackedTradingVenueGateway.java)、[OCC repository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepository.java)。
- [recovery](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRecoveryService.java)、[scheduler wiring](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/validationevidence/ValidationEvidenceSchedulerConfiguration.java)、[scheduler entrypoint](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/validationevidence/ValidationEvidenceScheduler.java)、[read-only refresh](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/validationevidence/ValidationEvidenceRefreshService.java)、[advisory lock](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/scheduler/infra/lock/PostgresAdvisorySchedulerExecutionLock.java)。

## 最小红色复现

[完整 canonical PostgreSQL 与 Venue 快照](l4-b5-qualification-attempt01/proof.json)。运行身份由既有 field-aware exporter 转换为 `SYNTH-L4:B5:R01:*`；原始文件仅保存在 `backend/nq-app/target/b5-proof/`。PID、数量、时间戳和版本未清洗。

| Fact | Observed |
| --- | --- |
| Controller PID | 69932 |
| NQ PIDs | A=13120，B=71284；真实独立 PID、同时存活、真实 Spring context |
| Venue PID | 76192；既有 B2SyntheticVenueMain 独立进程，loopback HTTP |
| PostgreSQL | 16.15 (Debian 16.15-1.pgdg13+2)，本轮新建 pinned-image 容器；A/B 同一 disposable DB：`SYNTH-L4:B5:R01:DATABASE:001`，Flyway V48 |
| Safety composition | 真实 RiskGate、代理化 OrderCommandWriteService、AdapterBackedTradingVenueGateway、隔离 app DB role；独立 reader 只读 oracle |
| Ownership/lease fact | ordinary 无 mutation lease；本次是 sender 与 recovery 的竞争，不是 lease transfer。proof 的 `restartPid` 沿用旧 harness 字段表示 B，`topology` 明确 NO_RESTART，不作为重启证据。 |

实际先后由有界屏障、独立 reader 及 venue event sequence 一起确定，不能比较不同 JVM 的 nanoTime：

1. A 调用 `ARM_B5_PRE_SEND`、`BEGIN_PLACE_B2`；A 的事务外 interceptor 在真实 prepare 返回后暂停，marker=`transactionActive=false`。A marker epochMillis=1788966275248；controller 观察 marker=1788966275306。
2. 独立 PG reader 看到 Order=`SENT / version 2`，venue placeRequests=0。B 直接执行相同 `PLACE_B2`，返回同一 SENT；数据库仍只有一笔 Order，version 仍为 2。控制器没有根据数据库结果主动跳过这次调用。
3. B 调用真正 `OkxRecoveryService.rebuild`。Venue event sequence 2=`QUERY_OPEN_ORDERS count=0`；3=`QUERY_ORDER_NOT_FOUND`。返回的 51603 仅依据 venue 自身不存在订单的事实，不是客户端伪造异常。独立 reader 确认 Order=`CANCELLED / version 4`；controller 观察提交=1788966275606；再次读取 venue placeRequests=0。
4. 控制器释放 A。A 持有的旧 sentOrder 继续调用真实 adapter；venue sequence 4=`PLACE_REQUEST_RECEIVED`、5=`PLACE_ACCEPTED`，epochMillis=1788966275651/1788966275658。A 的 release 命令响应被 controller 观察到的 `releasedAt=1788966275728` 是响应观察时间，**不是释放动作发生时间**。
5. A finalize 返回 CANCELLED；PG 仍为 `CANCELLED / version 4`，但 venue order=`live`，PLACE requests=1、accepted places=1。数据库 OCC 成功防止旧 ACK 覆盖，并未阻止外部 mutation。

| Oracle | atCut | afterDuplicate | afterRecovery | afterResume |
| --- | --- | --- | --- | --- |
| Order count/status/version | 1 / SENT / 2 | 1 / SENT / 2 | 1 / CANCELLED / 4 | 1 / CANCELLED / 4 |
| Venue PLACE requests / accepted | 0 / 0 | 0 / 0 | 0 / 0 | 1 / 1 |
| Trade | 0 | 0 | 0 | 0 |
| TradeExecuted | 0 | 0 | 0 | 0 |
| Ledger entries | 0 | 0 | 0 | 0 |

ExecutionIntent/Receipt：ordinary path 不创建，未将它们作为该场景 oracle；本轮没有独立查询这两张表，不声称其 row count。Venue CANCEL=0；blind retries=0（唯一 PLACE 是原调用的迟到发送，不是 B 重试）；duplicate PLACE=0；duplicate Trade/TradeExecuted/Ledger=0，未产生成交，故这些零值不证明成交后完整账务资格。

最后测试断言期望 `latePlaceRequests=0`，实际为 1：`Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`，Maven exit=1。三个 child 结束并验证已退出，临时数据库删除成功，owned PG 容器删除并验证 remaining=0。这些清理强杀不构成 owner-death/takeover 场景。

## Matrix disposition

Canonical plan B5 只包含当前 ordinary duplicate-command 和 scheduler-lock rows；INTENT_WORKER rows 保持 dormant。新增 probe 是本轮明示的 ordinary stale-sender/resume 边界，没有复活 dormant MIL sender。

| Requested row | 本轮结果 |
| --- | --- |
| Duplicate command | 已执行 first unresolved 时重复提交；同一 Order / version，重复调用未外发。sequential complete duplicate 未运行 |
| Concurrent duplicate | 已运行 A paused / B duplicate 的 overlapping arrival；插入前 near-concurrent race 未运行 |
| Scheduler duplicate trigger | 源码确认只读 entrypoint；运行验证 NOT_RUN / STOP_AFTER_P1 |
| Two-JVM competition | A/B same PG+venue 的 ordinary sender/recovery 竞争实际运行；整体 FAIL，不声明 multiprocess ownership proven |
| Stale-owner resume | FAIL：旧 ordinary sender 在 recovery finality 后仍 PLACE；没有虚构 lease stolen/expired |
| Owner death/takeover | NOT_RUN / STOP_AFTER_P1 |
| Restart/recovery | NOT_RUN / STOP_AFTER_P1；本轮 B 与 A 同时存活 |
| Recovery under ownership contention | B 可在 A 暂停时 query-confirm 并提交；scheduler lock-held recovery 未运行 |
| Repetition | 1 次最小红色复现，按首次 P1 STOP，不以再跑重复数继续 qualification |

## 根因与历史交互

Canonical owner 是 ordinary command/recovery 的外发与最终性边界；scheduler lock、Controller、Spring composition root 都不是此业务缺陷的修复位置。

[L4 plan](GATEAUDIT_PHASE6_L4_FAILURE_MATRIX_PLAN.md) 第 10/17/18 节记录：2026-09-06 前后已有 dormant PB2 sender 在 query-not-found 终态后 late dispatch 的观察；其生产 caller 不成立，因而不属于当时 current finding。C1 修复的是 stale ACK 数据库覆盖，用 status+version CAS 保留较新终态；未声称解决网络发送 fencing。本次实际看到 CANCELLED/version4 保持，说明不能称 C1 回归失败。共同机制是“最终性决策与仍存活 sender 的外发能力未形成互斥/撤销契约”，本次新增的是 **ordinary production reachable + 两独立 JVM + 真实 adapter/PG/venue** 的证据。

Recurring-problem rule 已执行 REPRODUCE → TRACE HISTORY → IDENTIFY COMMON MECHANISM。没有追加 workaround；ROOT-CAUSE FIX、相邻/正向永久回归和统一 PROJECT LESSON 更新留给单独 production remediation，本轮不声明系统性关闭、不修改 Skills。保留的红色测试要求正确行为，修复后自然应变绿；不能反转成“允许迟到 PLACE”的 characterization。整改需处理 sender 存活/死亡与 recovery finality 的真实边界，不能仅增加一次有 TOCTOU 窗口的 SELECT，或新增第二套 controller dedupe。

B1/B4 query-first 与 no blind retry 原则保留；本次 B 只查询/恢复。B2/C1 OCC 拒绝旧写入仍有效；环境为 SIM。B3 kill 与 B4 fan-out 已接受证明不重审。本轮没有 derived fact 缺失或新 synthetic identity allowlist；不从这些零变更推断 B5 已通过。

## Validation 与交付边界

永久红色入口（仓库根运行）：

```text
mvn -o -f backend/pom.xml -pl nq-app -am -Dnq.b5=true -Dtest=B5RealProcessProofTest -Dsurefire.failIfNoSpecifiedTests=false test
```

- B5 targeted / relevant PostgreSQL / real-process probe：同一次真实运行，1 test / 1 failure / 0 error / 0 skip；不能三次重复计数。完整日志留在 `backend/nq-app/target/b5-targeted.log`。
- 其余 B1/B2/B3/B4 regressions、scheduler PG lock tests、剩余矩阵：NOT_RUN / STOP_AFTER_P1。
- Full Maven rerun：NOT_RUN，production byte-for-byte unchanged；以上 `-am` 是目标测试依赖构建，不是 Full Maven PASS。
- Synthetic exporter regressions：5 tests PASS；实际 B5 proof 全树逆映射、身份关系与 raw identity leakage 检查见本次收尾检查结果。
- pinned Gitleaks / stage-assets / whitespace：在保存 canonical evidence 后执行；结果见收尾检查记录，不以检查通过覆盖红色 correctness test。
- CI：NOT_RUN；exact-head CI 留给后续获授权的 delivery，当前无 delivery recommendation。

Files changed：B0NqProcessMain.java、B2SyntheticVenueMain.java、新增 B5PreSendBarrier.java、B5RealProcessProofTest.java、本 evidence 和 `l4-b5-qualification-attempt01/proof.json`。Production files changed=0；migration=0；CI=0。

Commit recommendation：不提交为 B5 qualification complete；本轮 stage=0 / commit=NONE / push=NONE。

Next action：单独授权 ordinary sender/recovery finality correctness remediation，完成根因修复、正向/相邻/原红色回归和一次 Independent Correctness Review 后，恢复剩余 B5 qualification。当前不进入 B5 precise delivery 或 B6。

## 收尾检查记录

- 本次 proof 与既有 exporter 输出逐树相等；17 个 canonical identity 双射与全树逆映射 PASS，随机 runtime DB/client/order/event identity 泄漏=0。首次辅助检查以全部原始字符串作 substring 搜索，误将固定 trace 文本及字符串 `null` 算作泄漏；没有修改 exporter 或增加例外，改用随机身份检验并保留全树逆映射验证。不是 evidence reference loss。
- pinned Gitleaks 8.18.4：归档 SHA256 对当前 supply-chain lock 验证成功；6/6 secret negatives REJECT；当前 CI 原配置与 safe-file 范围下 working-tree exit=0/findings=0；新增主文档和 proof.json 另作完整扫描 exit=0/findings=0。首次 WSL 命令的 Windows git 路径不存在，在负例通过后文件枚举前失败；改为 `Get-Command git` 实测的 `D:\Tool\Git\cmd\git.exe` 后成功，没有修改工具或 policy。
- stage-assets：`scanned=1831 / reviewed_exceptions=173 / errors=0`。没有修改任何 exception hash。
- `git diff --check` PASS；新增文件另检 UTF-8、行尾空白和 evidence 相对链接。最终改动仅 4 个 test Java 文件与 2 个 evidence 文件；index 无变化。
- 红色 correctness 结果保持 FAIL，没有用卫生检查的 PASS 替代。未运行 Full Maven、其余 real-process 矩阵或 exact-head CI。


## Qualification resume attempt01 — 2026-09-10

本节是追加事实；上方首次 V48 stale-sender FAIL、后续合同与整改历史均保留。当前结论：**FAIL / L4_B5_CORRECTNESS_FINDING / STOP / PRODUCTION_REMEDIATION_REQUIRED**。新增 P1=`STRATEGY_DISPATCH_OWNER_DEATH_PERMANENT_BUSY`。B5 继续 `NOT_QUALIFIED / NOT_ACCEPTED / NOT_READY_FOR_PRECISE_DELIVERY`。

### 已接受候选与历史身份

- 本轮用户明确接受 B0–B4 与 Attempt-02 的 V49 one-shot、fail-closed uncertainty、TradingVenue，以及两个旧 P1 closure；这些不重开。当前 STATUS 机器区块未同步，本轮不修改 current authority。
- branch=`audit/post-gatey-agent-baseline`；起止 HEAD 与本地 origin 引用均为 `86c8ad84542636364f6c21e78bc292a323cbdff7`，未 fetch。进入本轮的 3481 文件整体指纹精确等于 Attempt-02：`51dae8759116de3e1647acf08dbf28fdbb5d85f3c152ad785077d9d967002aa0`。
- 后续仅 test/harness/evidence 改动，整体指纹自然不再表示旧全工作树；生产/migration/CI/AGENTS/Skills 共1333个受保护文件逐字节一致，见 [candidate-integrity](l4-b5-qualification-resume-attempt01/candidate-integrity.json)。没有以新测试文件冒充旧 reviewed manifest。
- 历史顺序：上方首次 stale-sender P1 → [初次整改评估](GATEAUDIT_PHASE6_L4_B5_ORDINARY_SENDER_RECOVERY_FINALITY_REMEDIATION_ATTEMPT01.md) → [Contract review](GATEAUDIT_PHASE6_L4_B5_DURABLE_MUTATION_AUTHORITY_AND_EXECUTION_FENCING_CONTRACT_REVIEW.md) → [V49 implementation](GATEAUDIT_PHASE6_L4_B5_FAIL_CLOSED_MUTATION_AUTHORITY_V49_IMPLEMENTATION_ATTEMPT01.md) → [Attempt-01 review FAIL](l4-b5-venue-canonicalization-attempt01/review-attempt01-original.md) → [venue canonicalization](GATEAUDIT_PHASE6_L4_B5_VENUE_IDENTITY_CANONICALIZATION_REMEDIATION_ATTEMPT01.md) → Attempt-02 PASS（本会话上一轮独立审查，用户本轮明确接受）→ 本节 qualification resume。
- Attempt-02 原始日志继续保存在 `backend/nq-app/target/b5-review-attempt02-targeted.log` 与 `b5-review-attempt02-supplement.log`；62个目标 tests 全通过，补充竞争/断连/Kill/unknown通过。它关闭旧两个P1，不接受本轮发现的新 strategy-run 恢复缺口。

### 当前入口与 ownership 合同

1. ordinary command：`OrderCommandService` 的 `(account_id, client_order_id)` 查询与数据库唯一键负责 logical Order 身份；V49负责一次性 send/revoke。不存在 ordinary lease/epoch；owner死亡后接管的是查询/恢复，不能把 MAY 重新变成发送资格。
2. 自动 scheduler：`ValidationEvidenceSchedulerConfiguration` 的 processor只注册一个 `ValidationEvidenceScheduler.scheduledRefresh`。两个真实 Spring JVM均核对 scheduled task count=1；测试将首次时钟触发设为24小时后，并直接调用真实 `scheduledRefresh`安排确定性交叠。callback仍运行真实只读 aggregate，不替换结果。锁为 `PostgresAdvisorySchedulerExecutionLock` 的 REQUIRES_NEW/read-only 事务与 `pg_try_advisory_xact_lock`；work key=`validation-operations/runtime-evidence-refresh`。它仅排除重叠，不持久去重相同时间窗口：A释放后B可以再次执行只读aggregate。无交易callback，不能用它宣称交易scheduler恰好一次。
3. **实际可达的手动计划扫描**：`POST /api/strategy-schedules/scan-once → StrategyScheduleScanService.scanOnce → ManualStrategyTriggerGateway → StrategyManualTriggerService → OrderCommandStrategyExecutionGateway → OrderCommandService`。这些当前bean和调用边均存在，不是retired路径；本轮真实Spring调用证实可达。此前仅根据自动调度processor排除交易scheduler的范围描述不足，本节补全该入口。
4. strategy scan work identity为 `scheduleJobId + dueAt`生成的 requestId，ordinary clientOrderId=`coid-`加requestId；busyScheduleIds/busyStrategyIds仅JVM内。持久busy检查来自 `strategy_runs.status IN (CREATED,DISPATCHING,RUNNING)`，发生在dedup检查之前；没有expiry/lease或owner身份。
5. 历史INTENT_WORKER、lease expiry/leader election及未注册的交易`@Scheduled`仍为 `NOT_CURRENTLY_ELIGIBLE / FUTURE_OBLIGATION`；未激活它们。

### 实际矩阵及 oracle

每行独立创建PG16.15/V49数据库、Synthetic Venue JVM与至少两个Spring JVM；真实PreTradeRiskService、ordinary service、adapter均保留。封存前DISENGAGED仅隔离TEST_PRECONDITION。原始随机身份留在target，下面的proof保存canonical身份、JVM/Venue PID、DB identity、Order/client、authority与账务快照。[全部索引](l4-b5-qualification-resume-attempt01/proof-index.json)。

| Row | 实际结果 | 最终 Order / authority | PLACE/CANCEL | Trade / TradeExecuted / Ledger |
| --- | --- | --- | --- | --- |
| [R01](l4-b5-qualification-resume-attempt01/R01.json)–[R03](l4-b5-qualification-resume-attempt01/R03.json) | 三轮双JVM插入前真正竞争，之后第三JVM连续重复、成交、反复恢复；PASS | FILLED/v4 / MAY | 1/0 | 1/1/4 |
| [R04](l4-b5-qualification-resume-attempt01/R04.json) | A prepare后、arm前暂停；B重复；kill A；B和新JVM查询恢复；PASS | CANCELLED/v4 / REVOKED | 0/0 | 0/0/0 |
| [R05](l4-b5-qualification-resume-attempt01/R05.json) | A已MAY但未HTTP时死亡；B和新JVM只查/不重发；PASS | SENT/v2 / MAY | 0/0 | 0/0/0 |
| [R06](l4-b5-qualification-resume-attempt01/R06.json) | 原PLACE与Venue fill后kill A，B恢复，新JVM再恢复；PASS | FILLED/v4 / MAY | 1/0 | 1/1/4 |
| [R07](l4-b5-qualification-resume-attempt01/R07.json) | scheduler A持锁，B真实重复触发callback=0；释放后B再次执行；PASS | FILLED/v4 / MAY | 1/0 | 1/1/4 |
| [R08](l4-b5-qualification-resume-attempt01/R08.json) | scheduler A持锁死亡；PG锁消失，B和新JVM真实callback执行；PASS | FILLED/v4 / MAY | 1/0 | 1/1/4 |
| [R09](l4-b5-qualification-resume-attempt01/R09.json) | 切断自有lock backend，A仍活；B执行后A恢复失败退出原callback，随后A普通重复也未重发；PASS | FILLED/v4 / MAY | 1/0 | 1/1/4 |
| [R10](l4-b5-qualification-resume-attempt01/R10.json) | 实际strategy scan在arm前owner死亡；B与新JVM被孤立DISPATCHING永久busy阻断；**FAIL/P1** | CANCELLED/v4 / REVOKED | 0/0 | 0/0/0 |

MAY=`MAY_HAVE_ESCAPED`，REVOKED=`REVOKED_BEFORE_SEND`。所有行只有1个Order/authority；ExecutionIntent/Receipt均实际查询为0，不将ordinary work冒充intent worker。R07–R09的唯一PLACE来自B独立ordinary命令：A持scheduler锁期间B完成真实Trade/Event/Ledger恢复，证明scheduler ownership不阻塞它们。scheduler本身PLACE/CANCEL=0/0。各行释放后PG advisory锁=0。

R01–R03同时INSERT输家均发生 `DUPLICATE_INSERT_TRANSACTION_FAILURE`，赢家成功；controller没有查库跳过请求，错误完整保留在child日志，之后新JVM请求实际返回同一订单并收敛。它不是“双请求都成功”的证明；记录为P2可用性/错误响应限制，不能用external exactly-once抹去该失败。

原ordinary stale sender恢复证明复用Attempt-02，仍为恢复提交后PLACE=0。手动strategy scan同窗口竞争、stale scan与其它后续rows在首次新增P1后 **NOT_RUN / STOP_AFTER_P1**；不能用R01–R03 ordinary竞争替代它们。

### 新 P1 最小因果链

R10 fixture仅在NQ启动前创建一条enabled策略及两分钟前已到期的CRON计划，venue=`oKx`，trade_env=`SIM`。运行阶段controller不写strategy_run、Order、authority或lastTriggeredAt。

1. A进入真实 `scanOnce`，生产代码获取本地busy、确认无active run并生成窗口requestId。`StrategyManualTriggerService`先持久化run，再将其设为 `DISPATCHING`。
2. A经过真实Risk/Order prepare，事务外屏障暂停。独立reader看到run=`DISPATCHING / finished_at=NULL`、Order=`OKX / SENT / v2`、authority=`NOT_ARMED`、lastTriggeredAt=NULL；Venue PLACE=0。
3. 强杀A并确认死亡。B调用真实OkxRecoveryService与reconcile，Order原子收敛为 `CANCELLED / v4`，authority=`REVOKED_BEFORE_SEND`；无未知外部事实，PLACE/CANCEL=0/0。
4. B直接再调用 `scanOnce`，结果 `SKIPPED_BUSY / strategy_run_active`。再次退出B并启动新JVM C，真实recovery/reconcile及scan重做，仍同样busy。最终run仍DISPATCHING、finished_at=NULL，lastTriggeredAt仍NULL。
5. 有限writer搜索确认当前production仅 `StrategyManualTriggerService` 的原同步调用更新该run状态；`JdbcStrategyRunRepository.existsActiveRunByStrategyId`无期限检查，Order/V49/recovery不更新strategy run，未找到重启收敛writer。因此永久性判断由当前代码合同加真实死亡/重启事实共同支持，不以短暂等待推断“永久”。

根因owner为 **strategy run生命周期 / schedule durable busy admission / owner死亡恢复**。不是advisory锁泄漏，不是V49重发，不是TradingVenue分裂。已接受Contract B允许MAY保留未知；R10为明确REVOKED+CANCELLED，故不能把死DISPATCHING的永久busy归为该已接受可用性代价。不同机制，旧P1不重开。没有修production或继续矩阵。

最小正确行为回归：

```text
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=B5StrategyOwnerDeathTest -Dsurefire.failIfNoSpecifiedTests=false -Dnq.b5.resume=true
```

当前预期红色：`1 test / 1 failure / 0 errors / 0 skips`，断言要求死亡run不能永久阻断已证明无发送的schedule。后续根因整改须明确策略run终态与合法successor，不允许通过重置MAY、换clientOrderId或绕过V49修复。

### 验证、证据卫生与处置

- `run-01.log`：`B5QualificationResumeTest`，Maven exit=0，1个JUnit test内部9个独立进程场景全部通过；不能计为9个JUnit tests。
- `run-02-strategy-death.log`：`B5StrategyOwnerDeathTest`，Maven exit=1，1 test/1 failure/0 errors/0 skips；这是production correctness失败，不是harness blocked。完整日志均在 `backend/nq-app/target/b5-qualification-resume/`，原始proof路径及hash见索引。全部自有child、DB/container已清理。
- 两次运行之间只追加strategy scan测试控制与最小死亡probe。R01–R09已执行路径与production未变化，未因追加未使用控制重复整套矩阵。此前冻结Full Maven的1873/0/0/98按用户授权复用；不声称它验证了本轮新增tests。未重跑Full、B1–B4矩阵、V49 review或CI。
- Recurring-problem检查：新strategy proof首次归档前，零随机身份检查发现 `strategy_run_id` 及command payload `strategy_id`中的RUN引用未覆盖，标记 `RECURRING_PROBLEM_ROOT_CAUSE_NOT_CLOSED`（export coverage，非production身份分裂）。按现有lesson修现有通用export owner：RUN主键映射与仅匹配已登记RUN的strategy_id引用；不全局清洗字符串、不新增B5专用exporter/allowlist。补充Order/run/command引用、稳定definition字段及credential不变回归，Python 6 tests PASS；10份proof全树逆映射相等、UUID泄漏=0。原始文件未改。最终scanner/negative/stage-assets结果见收尾补记，不把其PASS覆盖R10红色。
- Files changed范围：B0NqProcessMain、B5QualificationControls、B5SchedulerNqProcessMain、B5QualificationResumeTest、B5StrategyOwnerDeathTest、synthetic_evidence.py、test_synthetic_evidence.py及本节/新证据目录。生产=0、migration=0、.github=0、AGENTS/Skills=0。本轮stage=0、commit=NONE、push=NONE。
- P0=0；新增P1=1；P2=1（并发duplicate调用的失败响应限制）。Commit recommendation：不能作为B5 qualification complete交付。Next action：单独strategy run owner-death/recovery整改，修复后一次真正独立正确性审查，再恢复被停止的eligible rows。当前不进入precise delivery/B6。


### Resume 收尾检查

- pinned Gitleaks 8.18.4 / supply-chain archive digest 校验PASS；21个本轮文件扫描 exit=0、findings=0，六类导出后secret negatives全部REJECT。原报告：`backend/nq-app/target/b5-qualification-resume/gitleaks-result.json`，未改scanner配置或allowlist。
- stage-assets：scanned=1845 / reviewed_exceptions=173 / errors=0；未改exception hash。`git diff --check`与21个本轮文件的UTF-8/行尾空白、文档相对链接检查PASS。
- 本轮共21个文件：7个test/harness/exporter源文件，14个文档/证据文件。其余输入已有变更全部保留；生产/migration/CI/AGENTS/Skills相对本轮起点delta=0。Git index仍无staged文件，HEAD未变。
- 最终仍为 **P0=0 / P1=1 / FAIL / STOP / PRODUCTION_REMEDIATION_REQUIRED**；卫生检查和九行绿色不覆盖R10失败。

## Qualification resume attempt02 — 2026-09-10

本节仅追加本轮事实，保留全部此前 FAIL、remediation 与独立 review 历史。**FAIL / L4_B5_CORRECTNESS_FINDING / STOP / PRODUCTION_REMEDIATION_REQUIRED**。首次剩余同窗口 strategy scan 并发场景产生两条同逻辑窗口的 StrategyRun，新增 **P1 / STRATEGY_SAME_WINDOW_DUPLICATE_DISPATCH**；B5 仍为 `NOT_QUALIFIED / NOT_ACCEPTED / NOT_READY_FOR_DELIVERY`。

### 候选与复用身份

- Task classification：`HIGH_RISK / L4_QUALIFICATION / STRATEGY_CONCURRENCY / MULTI_PROCESS_OWNERSHIP / NQ-only`。
- Starting HEAD：`86c8ad84542636364f6c21e78bc292a323cbdff7`；branch=`audit/post-gatey-agent-baseline`。
- 起始 3529 个 tracked/untracked 非忽略文件、HEAD、branch、index entries 与 porcelain status 按 reviewer 原算法重新计算，fingerprint=`9be6df6668a2bcbbd3eb453be9a051c046d78a77d12a2d1e08070d70cd17cf00`；与 reviewer start/end 精确一致，missing/mismatch/unexpected=0。
- 旧实现 manifest 的234项 Java 差异是此前用户授权的全限定名整改，已经纳入该 fingerprint 和 reviewer 新 Full，并非本轮漂移。归档的 [reviewed backend manifest](l4-b5-qualification-resume-attempt02/reviewed-backend-manifest.json) 是 reviewer 原始1788文件映射，本轮逐字节核验全部一致；不能误用旧实现 manifest 阻断当前已审候选。
- [原独立审查报告](l4-b5-qualification-resume-attempt02/reused-review-original.txt)按原字节复制；其中相对路径仍属于原 Temp 报告上下文。它是历史接受事实，不能作为本轮新审查。原始来源及 hash、reviewer Full identity 和复用索引见 [reused-proof-index](l4-b5-qualification-resume-attempt02/reused-proof-index.json)。
- Reviewer Full=`1877 tests / 0 failures / 0 errors / 102 conditional skips`，原日志 SHA-256 本轮重新核验；`Full Maven rerun=NONE`。不以该 Full 覆盖本轮新增测试失败。`Independent Review=NONE`，未重审 V49、TradingVenue 或已接受 strategy recovery。
- 此前 duplicate command sequential/concurrent/cross-JVM、ordinary stale sender、ordinary owner death、V49 recovery-wins/sender-wins、venue canonicalization、strategy orphan recovery、两 JVM 恢复、重复重启及 MAY negative 保持 `REUSED_ACCEPTED_PROOF`。复用不代表被停止的 strategy scan 剩余行已完成。

### Strategy dispatch identity 与并发边界

当前可达生产链仍为 `POST /api/strategy-schedules/scan-once → StrategyScheduleScanService.scanOnce → ManualStrategyTriggerGateway → StrategyManualTriggerService → OrderCommandStrategyExecutionGateway → OrderCommandService`。本轮通过真实 Spring service 入口调用该链，未声称执行 HTTP/security 测试。

Fixture 在 NQ 启动前创建 strategy=`b5-strategy`、account=1、schedule=`b5`、SIM、venue=`oKx`、`SCHEDULE_WINDOW`、UTC 年度 CRON。已到期的逻辑窗口为2026-01-01T00:00:00Z；每个 JVM 都按生产 `resolveDueAt` 计算。request identity=`req-schedule-b5-window-<dueAt epochMillis>`，ordinary client identity=`coid-`加该 request；ordinary Order 由 `(account_id, client_order_id)` 幂等。Order 自身另有 request_id，不能将其和 strategy request_id 当成同一个字段身份。运行随机 run/order 身份通过既有 exporter 映射，原始值仅在 target。

`existsActiveRunByStrategyId` 只读查询同策略 `CREATED/DISPATCHING/RUNNING`；scan 在它之前调用独立 recovery writer。`busyScheduleIds/busyStrategyIds` 仅属于每个 JVM。窗口 dedup 也是独立 SELECT；通过后 trigger 生成新的随机 run ID，再 INSERT/UPDATE DISPATCHING。当前数据库 request_id 索引不是唯一约束，也没有将窗口检查与 run 创建合成原子 admission。

复用现有 `ARM/BEGIN/RELEASE_B5_STRATEGY` 测试控制，不修改它：代理暂停在真实 gateway 调用前，此时 active/window/dedup 检查已经返回，local busy 仍持有。两个 JVM 都暂停到位、独立 PG 确认 run/order 均0、Venue PLACE=0后，控制器同时释放两个调用，原 gateway 各自继续。没有注入门禁返回值、SQL运行时构造业务结果、替代 RiskGate，或按数据库结果省略请求。

### R01 实际结果与因果链

[R01完整PG及Venue快照](l4-b5-qualification-resume-attempt02/R01.json)；[运行、raw来源及hash](l4-b5-qualification-resume-attempt02/run-index.json)。Controller PID=47248；NQ A=27628、B=53472；Venue=7320。两个独立 Spring JVM 同时存活，共用自有 PostgreSQL16.15/V49 和同一 Synthetic Venue。DB=`SYNTH-L4:B5-Q2:R01:DATABASE:001`；fixture 的 DISENGAGED 仅为封存前隔离 TEST_PRECONDITION。

1. A/B 均在生产 active/window/dedup 之后、trigger 之前暂停。独立 reader 看到 StrategyRun=0、Order=0；控制器于1789021334107 epochMillis观察两者均到位。
2. 两个调用释放后，各自创建 StrategyRun。两条 run 的 strategy/account/request 完全相同，`strategy_run_id`不同；观察表记录两次实际 `CREATED → DISPATCHING`。观察触发器仅记录业务 UPDATE，不参与 admission/recovery。
3. B 得到唯一 Order，并完成真实 adapter PLACE；run 进入 RUNNING。A 在普通 Order 并发 INSERT 输家路径遇到既有 SQLSTATE 25P02，scan 返回 FAILED，但已提交的另一条 run 仍为 DISPATCHING，且没有任何 Order 通过外键关联它。
4. Venue sequence2=PLACE_REQUEST_RECEIVED、3=PLACE_ACCEPTED、4=PLACE_ACK_GENERATED、5=PLACE_RESPONSE_DELIVERED；只有一次 PLACE。最终完整 snapshot 保存于1789021334330 epochMillis，随后断言 `logicalDispatchCount <= 1` 得到实际2而失败。

| Oracle | bothAtCut | final |
| --- | --- | --- |
| 同一逻辑窗口 StrategyRun | 0 | **2**：RUNNING 1、DISPATCHING 1；同一 REQUEST:001 |
| 下游绑定 | 无 | ORDER:001只绑定RUN:001；RUN:002无绑定Order |
| Order/status/version | 0 | 1 / ACCEPTED / v3 / OKX / SIM |
| V49 | 0 | 1 / MAY_HAVE_ESCAPED，唯一Order关联 |
| Venue PLACE requests / accepted | 0 / 0 | 1 / 1 |
| CANCEL / blind retry | 0 / 0 | 0 / 0 |
| Trade / TradeExecuted / Ledger entries | 0 / 0 / 0 | 0 / 0 / 0 |
| ExecutionIntent / Receipt | 0 / 0 | 0 / 0 |

本轮停止于首次新P1，不继续制造fill、recovery或restart；这些零成交计数仅证明该失败快照没有重复账务，不证明成交后完整账务资格。Order只采集了空状态及最终ACCEPTED/v3，未单独采样全部中间version迁移。V49发送互斥与Order幂等在本次挡住了第二Order/PLACE，但不能覆盖策略层两次实际dispatch与孤立run。

### 新 P1、P2 与 recurring-problem 边界

新的失败是同一逻辑窗口被两个 scanner 都消费，形成两个持久run和两次DISPATCHING；不是因为既有P2错误响应本身而停止。P2 `concurrent INSERT loser transaction failure`仍 `OPEN / NON_BLOCKING`，其SQLSTATE保留；P3通配符import残留仍 `OPEN / NON_BLOCKING`。本轮唯一新Java文件全部使用显式import。

Canonical owner为 strategy/window admission 与 StrategyRun创建的跨JVM原子性。当前先SELECT再INSERT没有持久互斥，两个本地busy set互不知晓。普通Order幂等及V49拥有下游执行身份和外发资格，不拥有上游run唯一性。不能借PLACE=1把两条run判为正常去重。

按 recurring-problem rule 已读现有 Durable Intermediate State Recovery Rule 并追查其覆盖边界：已接受 recovery 只恢复有唯一绑定Order、REVOKED_BEFORE_SEND与无发送终态的DISPATCHING。此次输家run没有Order，正确地不满足该资格，原回调又因异常中止。因此旧R10关闭事实保持；新发现是未被该修复覆盖的重复admission与无绑定中间态路径。本轮未另跑恢复验证其永久性，也未将短暂快照单独命名为新的永久busy P1；已证明的重复dispatch本身足以触发STOP。没有增加局部workaround、放宽recovery条件、复活scheduler或修改lesson。

### 剩余矩阵 disposition

| Row | 本轮结论 |
| --- | --- |
| Same-window strategy concurrency | **FAIL / 新P1**；真实两个JVM都通过检查并创建run |
| Cross-JVM active-run gate | 独立review的ACTIVE negative复用；本轮额外矩阵 `NOT_RUN / STOP_AFTER_P1`，不能由已存在active时阻塞推断并发创建原子性 |
| Stale scanner resume | `NOT_RUN / STOP_AFTER_P1`；R01是同时到达后释放，不冒充B完成后A恢复 |
| Owner death before arm | `REUSED_ACCEPTED_PROOF`：独立review原R10 |
| Owner death after MAY_HAVE_ESCAPED | `REUSED_ACCEPTED_PROOF`：独立review MAY negative；未新增第二PLACE |
| Safe recovery followed by next scan | 独立review dedup_hit/not_due负例复用；本轮新行 `NOT_RUN / STOP_AFTER_P1` |
| Concurrent recovery + scanner | `NOT_RUN / STOP_AFTER_P1` |
| JVM busy-set | 本地重入优化；本次两个JVM各自持有busy仍重复创建run，不能视为durable ownership证明 |
| Automatic scheduler | 源码wiring核验仍只注册ValidationEvidenceScheduler，callback只读aggregate；`REUSED_ACCEPTED_PROOF`，未重跑锁/重启suite |
| INTENT_WORKER、未注册交易scheduler、不存在lease/leader | `NOT_CURRENTLY_ELIGIBLE / FUTURE_OBLIGATION`；未激活 |

### 验证与交付边界

唯一新运行：`mvn -o -f backend/pom.xml -pl nq-app -am test -Dtest=B5StrategyScanConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false -Dnq.b5.scan=true`。

Targeted / PostgreSQL / real-process是同一次运行：**1 test / 1 failure / 0 errors / 0 skips / Maven exit1**；不是3次验证。完整输出在`backend/nq-app/target/b5-qualification-resume-attempt02/run-01.log`，原始proof与child日志见run-index。自有A/B/Venue均已确认退出，容器删除后remaining=0；这些收尾kill不属于owner-death proof。未重跑Full、Independent Review、B1–B4 matrix或CI。

Files changed：新增 `B5StrategyScanConcurrencyTest.java`、本节与`l4-b5-qualification-resume-attempt02/`；已有test/harness均未修改。Production changed=0；Migration=0；CI/.github=0；AGENTS/Skills=0。起止候选完整性见 [candidate-integrity](l4-b5-qualification-resume-attempt02/candidate-integrity.json)，本轮只在已审生产候选外追加测试和证据。卫生检查见 [checks](l4-b5-qualification-resume-attempt02/checks.json)，其通过不覆盖红色correctness结果。

P0=0；新增P1=1。Final decision=`FAIL / L4_B5_CORRECTNESS_FINDING / STOP / PRODUCTION_REMEDIATION_REQUIRED`。Commit recommendation：不能按B5 qualification complete提交；本轮stage=0 / commit=NONE / push=NONE。Next action：单独授权 strategy同窗口admission/重复dispatch根因整改，再按新候选风险完成必要独立审查后恢复剩余qualification；本轮不执行production整改，不进入precise delivery或B6。


### Attempt02 收尾检查

- 新proof沿用既有synthetic exporter，9个身份映射全树逆映射相等、随机UUID泄漏=0；exporter回归6 tests PASS。原始proof未修改。
- 新artifact第一次pinned Gitleaks扫描 exit2/findings82，全部命中原reviewer的path-keyed manifest中的真实文件SHA-256；不是运行随机identity或凭证泄漏。原始manifest仍保留reviewer Temp及原hash。本轮归档改用既有`sourceFileCount / files / path / sha256`结构，反向还原映射与1788个原条目完全一致；第二次scan exit0/findings0。没有修改scanner、allowlist或已有exporter。失败输出与两次记录均保留在target及checks索引。
- Gitleaks8.18.4归档hash对canonical lock核验PASS；6类导出后secret negatives全部REJECT，既有working-tree scan exit0/findings0。新test、完整主文档及本次全部归档artifact另行全覆盖扫描通过。
- **stage-assets FAIL**：scanned1851 / reviewed_exceptions138 / errors144；分别为UNAUTHORIZED_COMPATIBILITY_CALLER38、STALE_COMPATIBILITY_CALLER36、STAGE_SEMANTICS35、STALE_EXCEPTION35。全部37个报告路径对进入本轮已审candidate哈希不变，新test未在错误中出现；属于既有候选的guard/registry漂移。按现有stage-assets经验核对实际输入和映射，未盲目刷新hash、caller registry或修改production；这不是新的runtime P1，也不以卫生修复替代本轮STOP。后续delivery前仍须单独处理此阻碍。
- 主文档链接checked34 / warnings0 / errors0；git diff --check PASS。本轮新增文件UTF-8、JSON及行尾空白检查通过。主文档追加前的完整字节前缀保持不变，全部原FAIL/history保留；review原报告按原字节保留于txt。
- 收尾生产/受保护文件及reviewer1788个backend条目无漂移，index entries哈希未变、staged=0、HEAD未变。最终依然**P0=0 / 新P1=1 / B5 NOT_QUALIFIED**；不进入precise delivery。

## Final qualification resume — 2026-09-10

**PASS / L4_B5_QUALIFICATION_COMPLETE；B5 = CORRECTNESS_QUALIFIED / DELIVERY_BLOCKED。** 本节追加当前候选的最终资格结论，保留上文所有原始 FAIL、STOP、remediation 与 resume attempt。不是 Git 发布或 exact-head acceptance；B5 尚不是 ACCEPTED。

### 冻结候选、授权与复用依据

- Task classification：`HIGH_RISK / L4_QUALIFICATION / MULTI_PROCESS_CONCURRENCY / STRATEGY_EXECUTION / NQ-only`。Starting HEAD=`86c8ad84542636364f6c21e78bc292a323cbdff7`，branch=`audit/post-gatey-agent-baseline`。
- 最新独立审查为任务“独立审查 V51 B5 正确性”，接受 token=`B5_STRATEGY_RUN_DURABLE_EXECUTION_V51_REVIEW_ATTEMPT02_ACCEPTED`。[审查原答复](l4-b5-final-qualification-resume/reused-review-original.txt)是历史接受事实的文本副本，不作为本轮重新审查。reviewed manifest owner 为 `l4-b5-effective-quantity-remediation-attempt01` 的 scope/protected/full manifests 与 final-identity。
- 起始完整候选 fingerprint=`0502bc83ec532c6f8ec26fb7a21ffda03e986cd6d86018d4b0184eeb55032411`；按原算法 sorted path + NUL + SHA256 + LF，排除原 final-identity 自身重新计算一致。scope22、protected3667、Full绑定1818文件 missing/mismatch=0；完整3797文件 inventory无额外文件。V1–V50的50个文件不变，V51=`afbc3211b824b8f717912707b584d2382f47fe9f8df86802e7c4a1c8a6cd9942`，与 reviewed candidate 一致。
- 最终 production byte-for-byte unchanged。新增测试和证据、exporter修复会改变完整工作树 aggregate，不能把结束工作树冒充起始完整指纹；[候选完整性](l4-b5-final-qualification-resume/candidate-integrity.json)逐文件区分起始既有文件与本轮delta。
- 原 Full Maven=`1898 / 0 / 0 / 121 conditional skips`，1818文件清单及原日志 SHA256=`c273a1f0410b49a59ad99ec521c2814cc84889f20b968be26541aacce3228dc1`均核验。`Full Maven rerun=NONE`；该 Full 不冒充新增测试的执行证据。
- STATUS machine block仍是历史pre-B0；本轮沿用本附件明确的隔离资格授权，不更改 machine authority，不以其旧字段覆盖已核验的技术审查。无真实provider、生产/LIVE交易、部署或Git发布。

### 剩余 eligible rows 的逐行处置

不复活最初固定矩阵数；历史red row先标记SUPERSEDED，再指向当前接受proof。新运行见[新行索引](l4-b5-final-qualification-resume/new-run-index.json)，当前review原始proof的可逆副本见[复用索引](l4-b5-final-qualification-resume/reused-proof-index.json)。复用34份不同故障/边界proof不是34次本轮执行，也不是完整B5矩阵行数。

| 故障/入口 | 进入本轮分类 → 最终处置 | 当前证明 |
| --- | --- | --- |
| ordinary sequential / cross-JVM / concurrent duplicate | ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF | resume01 R01–R03保留；本轮R07补最终restart/filled interaction |
| ordinary stale sender / recovery-wins / confirmed sender-wins | ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF | V49与venue接受proof保留；当前review B_PAUSE、REVOKED_PAUSE、MAY_PAUSE为直接组合回归 |
| ordinary owner death before arm / after MAY | ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF | resume01 R04–R06及当前review MAY_DEATH、PRE_ARM；不凭PID推断no-send |
| strategy same-window duplicate | 历史resume02 FAIL为SUPERSEDED_BY_REMEDIATION；当前proof为ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF | reused12–14：同PG16/V51、同Venue、A/B同dueAt竞争，1 TRIGGERED + 1 duplicate_admission，run/order/V49/PLACE各1 |
| stale scanner before admission | ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF | reused21：A在gateway前暂停，B推进后A被duplicate_admission吸收；同窗口restart不变 |
| owner death before admission commit | 历史V50proof需最终组合确认 → PASS | R08–R10：A gateway前死亡，PG无run/order，successor合法admit；三套独立DB/Venue/JVM |
| CREATED recovery + scanner | SUPERSEDED_BY_REMEDIATION → PASS | R01–R03：A在admission已提交且Order=0处死亡，B recovery/C scanner同时调用，恢复同run |
| CREATED paused owner + recovery + scanner | NOT_YET_RUN → PASS | R04–R06：A持续暂停到B/C推进后恢复，复用同run/binding/V49，无第二PLACE |
| recoverable terminal RUNNING + scanner | NOT_YET_RUN → PASS | R11–R13：Order真实FILLED、run仍RUNNING处杀A；B recovery/C scanner同时调用，同run SUCCEEDED |
| Order binding之后/V49之前死亡 | ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF | reused08、22：同Order、effective与NOT_ARMED；successor不会重建binding |
| DISPATCHING双successor、暂停、回滚 | ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF | reused22–24、27–28、33；B原子提交/回滚及旧owner resume |
| MAY_HAVE_ESCAPED / HTTP pending / lost ACK | ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF + 直接交互PASS | reused06–07、30–31；无新发送许可，未知保持unresolved；本轮B1两行验证query-first |
| RUNNING terminalization / replay | ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF + 组合PASS | reused01、25–26、34及本轮R11–R13；不把cancel ACK当最终取消 |
| requested/effective/wire与账务 | ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF + 组合PASS | 本轮12个strategy执行均10.0005→10、100.005→100；wire与durable effective相同 |
| future-window progress | ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF | reused01、04：原run收敛后不同dueAt admission；未来run停在CREATED，原Order/PLACE仍1，未声称未来第二单成交 |
| ordinary final duplicate interaction | NOT_YET_RUN → PASS | R07：A发送后死亡，B重放与恢复；B/C退出后新D再次重放；Order/V49/PLACE=1、Trade/Event/Ledger=1/1/4 |
| automatic scheduler overlap/death/connection loss | ACCEPTED_PROOF → REUSED_ACCEPTED_PROOF | resume01 R07–R09；4个production wiring/refresh/lock文件与resume02 reviewed manifest逐字节相同，仍为只读aggregate |
| dormant INTENT_WORKER | NOT_CURRENTLY_ELIGIBLE / FUTURE_OBLIGATION | 不激活，不计PASS |
| unregistered trading scheduler | NOT_CURRENTLY_ELIGIBLE / FUTURE_OBLIGATION | 不人为赋予只读scheduler交易mutation职责 |
| nonexistent lease/leader、其他retired typed rows | NOT_CURRENTLY_ELIGIBLE / FUTURE_OBLIGATION | 保留重新可达时的证明义务，不以mock构造current reachability |

### 新增真实进程证明与 oracle

测试入口为 `B5FinalQualificationInteractionTest#remainingInteractionsStopAtFirstFailure`；单一JUnit test顺序执行13个独立运行，首个异常/断言失败重抛并停止循环。Java21 / Maven3.9.12 / pinned PostgreSQL16.15 / V51，真实Spring/RiskGate/production gateway/OKX adapter，独立Synthetic Venue。各场景A/B/C与Venue同时存活，D为最后新启动的重放进程；逐行controller/A/B/C/D/Venue PID、DB identity与hash均在新行索引。所有业务进程启动前只设置策略输入；运行后checker只读，没有SQL构造run、Order、authority、成交、账务或cursor。

| 新行 | PID/DB身份出处 | 最终事实 |
| --- | --- | --- |
| R01–R03 CREATED_DEATH_SCAN | new-run-index对应R01–R03 | 每行run1/SUCCEEDED、Order1/FILLED/v4、V49 1/MAY、PLACE1/CANCEL0、Trade1/Event1/Ledger4 |
| R04–R06 CREATED_PAUSED_SCAN | new-run-index对应R04–R06 | 同上；afterSuccessors→旧A恢复→afterReplay身份一致 |
| R07 ORDINARY_RESTART | new-run-index R07 | strategy run0；Order1/FILLED/v4、V49 1/MAY、PLACE1/CANCEL0、Trade1/Event1/Ledger4 |
| R08–R10 PRE_ADMISSION_DEATH | new-run-index对应R08–R10 | atCut run/order0，successor后同窗口run1；最终成交账务同上 |
| R11–R13 TERMINAL_SCAN | new-run-index对应R11–R13 | atCut run RUNNING、Order FILLED，successor只终结原run；最终成交账务同上 |

13个新运行累计PLACE=13、CANCEL=0、StrategyRun=12、Order=13、V49=13、Trade=13、TradeExecuted=13、Ledger=52。这些计数跨不同数据库相加，**每个逻辑窗口/command**上限仍是1；不将其他复用或B1/B3运行混入该总数。每行Venue fill=10、price=100、fee=.01 USDT；四笔账务为-1000/+1000/-.01/+.01 USDT，方向、trade引用、账户及幂等键一致。

Java原始快照含strategy_runs、dispatch_work、schedule/cursor、Order/status/version、V49、Trade、TradeExecuted payload、Ledger以及Venue PLACE/CANCEL/fill/events。Java计数断言之外，[可复现离线oracle](l4-b5-final-qualification-resume/verify-oracle.py)用Decimal校验全部身份链、requested/effective/wire、金额/fee/ledger借贷和重放，raw13/13、canonical13/13通过；5类篡改负例全部拒绝。full-tree逆映射还原原JSON，raw字节保持不变。

并发声明限于B/C在barrier后向两个真实入口同时发起调用，不宣称每次都在同一数据库临界区重叠；stale owner的A明确暂停跨越B/C完成。普通重复行B是预先启动进程，D才是全新启动进程。入口通过真实Spring service触达，不冒充HTTP/security端到端测试。

### Crash-window completeness 与 B1–B4 interaction

| reachable状态 | owner dies | paused owner resumes | two successors | replay | future work |
| --- | --- | --- | --- | --- | --- |
| CREATED | R01–R03；同run RESTART_SAFE_RECOVERY | R04–R06；同run/effective/order | reused28及R01–R06 | 每个新行D快照不变 | reused04不同dueAt可admit |
| DISPATCHING | reused22/08；B ATOMIC_NEXT_STEP，复用Order | reused23/33；V49吸收旧调用 | reused23/27/28 | B/MAY/revoke后重复recovery不变 | 有确定终态后同一V51 projection释放active；已接受CREATED/rounded future proof组合覆盖 |
| RUNNING | R11–R13、reused01 | reused31；MAY保留原唯一sender契约 | reused34及R11–R13 | 原run SUCCEEDED/finishedAt与账务不变 | reused01不同dueAt可admit |

MAY无venue事实、legacy缺work等保留 `CORRECTNESS_REQUIRED_UNRESOLVED`；这类active阻塞是正确性要求，不标为permanent orphan，不允许false reclaim、re-arm或blind retry。已关闭P1没有重新审查，未发现同机制新orphan、重复执行或未来窗口被确定终态永久阻断。

- B1：本轮仅 `v49AffectedTimeoutAndLostAckRegression`，accepted-timeout与ACK-generated/lost各1行，query-first/no blind retry；见interaction01–02。
- B2：复用最新V51 reviewer的V49/B2四场景日志与原接受；新增13行直接验证Order v4、exactly-once Trade/Event/Ledger，不重跑48-run矩阵。
- B3：本轮仅 `v49AffectedKillAndRestartRegression` 的PRE_ACCEPT与RESTART_PRE_ACK；新mutation被Kill阻断、in-flight与恢复按原契约；见interaction03–04。V51 KILL_CREATED采用reused29。
- B4：复用当前review的A/B/C before/after commit-response-loss六行（reused15–20）及accepted durable fan-out；本轮owner-death/restart新增组合逐行检查event/ledger不重复，不重新执行完整B4 qualification。

### 验证、独立核对、证据卫生与最终处置

- Targeted Maven：[targeted01](l4-b5-final-qualification-resume/targeted-01-result.json)为1/0/0/0，内部13个独立DB/Venue多JVM场景；[interaction02](l4-b5-final-qualification-resume/interaction-02-result.json)为2/0/0/0，内部B1两行+B3两行。合计**3 JUnit tests / 0 failures / 0 errors / 0 skips，17个新real-process场景**，16个自有PG容器清理均remaining=0。PG证明来自这些真实进程，不另虚增一套PG测试数量。
- 本轮使用独立只读reviewer核对新增test与13份raw；reviewer未参与实现/证据编写，未重审production、未启动交易测试。测试源码SHA256=`4986dd36809d2e8ddec5dcda1eab0bd25c6ee8b5225e5dfe84fb7a9aa8912310`。其独立离线复核与后续exporter/oracle复核均通过，限制已写在上文。
- 初次导出零UUID检查拒绝返回值字符串里的run/order随机身份，属于test evidence representation缺口，非production correctness FAIL；归档未接受该输出。原始记录保持不变。沿用统一exporter，增加严格完整格式匹配、仅已登记引用转换，未知字符串/credential子树保留；8个exporter单测PASS，原proof全树可逆、随机UUID泄漏0。没有添加scanner例外。
- Stage-assets existing=144/current=144/new=0/removed=0，完整错误集合与原最终候选一致。**DO NOT FIX / DO NOT ADD EXCEPTIONS / DO NOT BULK SYNC HASHES**边界保持。其他卫生结果见[checks](l4-b5-final-qualification-resume/checks.json)。
- P0=0、P1=0；既有ordinary concurrent INSERT loser P2、wildcard-import P3均 `OPEN / NON_BLOCKING`，本轮不扩大或清理。production/migration/CI/AGENTS/Skills变化0；stage=0、commit=NONE、push=NONE。

```text
PASS / L4_B5_QUALIFICATION_COMPLETE /
DUPLICATE_COMMAND_IDEMPOTENCY_PROVEN /
STRATEGY_SAME_WINDOW_SINGLE_ADMISSION_PROVEN /
STRATEGY_DURABLE_LIFECYCLE_RECOVERY_PROVEN /
SCHEDULER_DUPLICATE_EXECUTION_CONTROL_PROVEN /
MULTIPROCESS_EXECUTION_PROVEN /
STALE_ACTOR_DUPLICATE_MUTATION_PREVENTED /
OWNER_DEATH_TAKEOVER_PROVEN /
EFFECTIVE_EXECUTION_CONTRACT_PRESERVED /
V49_V50_V51_INVARIANTS_PRESERVED /
NO_BLIND_RETRY / NO_DUPLICATE_MUTATION / NO_DUPLICATE_ACCOUNTING /
P0_0 / P1_0
B5=CORRECTNESS_QUALIFIED / DELIVERY_BLOCKED
DELIVERY_BLOCKED_BY_STAGE_ASSETS_ROOT_CAUSE
```

下一任务固定为 `NQ-GATEAUDIT-PHASE6-L4-B5-STAGE-ASSETS-ROOT-CAUSE-REMEDIATION`：分析144项失配的canonical owner与根因，增加permanent regression并恢复errors0；本轮不执行该整改。不能逐项豁免、扩大exception或禁用validator。关闭blocker后才进入B5 precise delivery；B5 exact-head CI绿色后才标ACCEPTED，再进入B6 aggregate acceptance。
