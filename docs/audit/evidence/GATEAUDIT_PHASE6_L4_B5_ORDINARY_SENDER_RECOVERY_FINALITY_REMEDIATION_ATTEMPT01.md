# B5 ordinary sender / recovery finality remediation — attempt01

Task classification: HIGH_RISK / P1_CORRECTNESS_REMEDIATION / EXTERNAL_MUTATION_FENCING / CONCURRENCY / NQ-only。

**STOP / EXTERNAL_MUTATION_FENCING_NOT_PROVEN**。本次完成根因与现有 primitive 评估、最小后续设计需求和工程经验；没有交付 production 修复候选。P0=0，原 confirmed P1=OPEN，LOCAL_P1 不为 0，B5=FAIL / NOT_QUALIFIED。不得标记 IMPLEMENTED 或 PENDING_INDEPENDENT_CORRECTNESS_REVIEW。

若采用下述 durable ownership 方案，下一步为 **BLOCKED / SCHEMA_REVIEW_REQUIRED / EXTERNAL_MUTATION_FENCING_SCHEMA_GAP**：先审查最小 V49 及执行端 fencing 契约。本证据不声称“所有可能的无迁移设计均已被排除”，也不声称仅增加表或 epoch 就足以解决网络发送 race。当前可复用机制中没有找到满足全部硬性条件的完整协议。

## 1. 起始身份、范围与已有证据

- branch=`audit/post-gatey-agent-baseline`；HEAD 与本地 origin 同名跟踪引用均为 `86c8ad84542636364f6c21e78bc292a323cbdff7`，未 fetch。开始 `git diff --check` PASS。
- 已有未提交改动：B0NqProcessMain.java、B2SyntheticVenueMain.java；新增 B5PreSendBarrier.java、B5RealProcessProofTest.java、原 B5 FAIL 主文档与 proof.json，共 6 个文件。均来自上一轮；本轮保留，不冒充本轮新实现。起始记录保存于 ephemeral `backend/nq-app/target/b5-remediation-start.json`，收尾按文件字节哈希核对。
- [原 FAIL evidence](GATEAUDIT_PHASE6_L4_B5_DUPLICATE_COMMAND_SCHEDULER_LOCK_MULTIPROCESS_OWNERSHIP_QUALIFICATION.md) 和 [canonical oracle](l4-b5-qualification-attempt01/proof.json) 不修改。原测试为两个真实 Spring JVM、同 PG16/V48、同 Synthetic Venue，1 failure / 0 error / 0 skip；本轮未改生产或该 harness，复用原红色证据，不声称重新运行。
- 本轮新增/修改只有本 evidence 与现有 `engineering-lessons.md`。生产、Flyway、CI、AGENTS、Skill topology 均无变化。无真实 provider、credential、LIVE、生产部署访问；stage=0 / commit=NONE / push=NONE。

## 2. Old concurrency map 与六个根因问题

```text
A / ordinary command                         B / ordinary recovery
validate + find identity
preparePlaceOrder transaction:
  INSERT Order → risk → SENT / v2
COMMIT
hold sentOrder in Java
                                              read SENT
                                              venue getOrder → NOT_FOUND
                                              lifecycle requestCancel → v3
                                              lifecycle cancel → CANCELLED / v4
gateway.placeOrder(sentOrder)
  adapter → OkxHttpClient → HTTP PLACE
venue accepts
finalize ACK with expected SENT/v2
  CAS rejected; durable CANCELLED/v4 survives
```

1. **何时获得 PLACE authority？** 当前没有显式 authority 实体。事实上的资格来自 `preparePlaceOrder` 成功提交后返回 `PlaceOrderPreparation.readyForAdapter(sentOrder)`；service 无额外仲裁即调用 gateway。
2. **何时失效？** 没有 durable revoke/consume/quiescence 契约。方法结束或进程死亡可以结束该调用，但另一事务更新 orders.status/version 不撤销仍存活调用栈的外发能力。进程死亡也不自动撤销已经发往 venue 的请求。
3. **recovery 凭什么终态化？** `OkxRecoveryService.resolveOrderNotFoundDuringQueryConfirm` 对 QUERY_CONFIRM_STATUSES 的快照查询，依据 adapter NOT_FOUND/51603 调用 `transitionToCancelled`；没有确认旧 sender 或在途请求已不可继续 mutation。
4. **共享什么并发边界？** 当前共享订单行的状态/version CAS，但它只约束各自本地更新。没有覆盖 HTTP side effect 与 negative finality 的同一 owner 边界。
5. **为何 version 只挡旧 ACK？** `OrderCommandService` 第126行先外发，随后才调用 finalize；repository 的 WHERE status/version 位于后续 SQL，不由 venue 执行。原复现实际证明 v4 未被覆盖，但活动订单已存在。
6. **可复用 primitive？** 已核查 orders unique key、V47 version、V48 scan cursor、scheduler advisory transaction lock、V39 execution claim、V42 pilot lease；其适用性如下。

当前源码锚点：

- [OrderCommandService](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java)：86–135，prepare 返回后直接外发。
- [OrderCommandWriteService](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java)：prepare 提交 SENT；finalizeAcceptedPlaceOrder 只处理后续本地回执。
- [OkxRecoveryService](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRecoveryService.java)：211–260，query NOT_FOUND → lifecycle terminalization。
- [JdbcOrderRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepository.java)：compareAndSetStatus；[OkxHttpClient](../../../backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxHttpClient.java)：send/buildRequest。

## 3. 现有机制与拒绝的伪修复

| 机制 | 实际能力 | 未满足的关键性质 |
| --- | --- | --- |
| 唯一键 + orders.version | identity 去重、状态 CAS、拒绝 stale ACK | 不约束下游 PLACE；再次 SELECT/CAS 后仍可暂停 |
| row/advisory lock 跨 HTTP | 数据库连接存续期间，使合作的两个数据库参与者互斥 | PG 连接终止释放锁，Java sender 不因此必然死亡；未处理在途请求延迟生效 |
| V39 execution_intents | token/lease、短事务 markSendStarted、receipt CAS | session_id 非空且 FK live_sessions，非 ordinary owner；现有 fake/local `ExecutionIntentService` 无 production worker caller，markSendStarted 后外发前仍有窗口 |
| V42 pilot_execution_leases | 历史单 pilot/session/binding 生命周期 | 单 pilot unique index、LIVE/pilot 授权语义，不能为 ordinary SIM 伪造 session/binding；无 venue fencing |
| V48 cursor | bounded reconciliation 扫描进度 | 不是 mutation ownership，不可将 cursor 锁当订单 sender lease |
| HTTP timeout / Thread interrupt / heartbeat expiry | 有界等待、故障检测信号 | 不能证明远端副作用不再发生；不得由超时或心跳缺失推断已停止 |
| SENT + reason/traceId 或通用 audit/event payload 记录 claim | 现有列能存字符/事件 | 当前不拥有类型化发送生命周期、epoch/claim 约束和安全回填语义；复用文本存储不等于复用已证明的协议，不能暗中建立第二套 ownership |
| 永远不对 SENT 做 negative terminalization | 避免部分误终态 | sender 真死且 venue absent 时可能 SENT forever，违反 recovery/takeover 条件 |

[scheduler lock](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/scheduler/infra/lock/PostgresAdvisorySchedulerExecutionLock.java) 在 REQUIRES_NEW/read-only 事务内取得 `pg_try_advisory_xact_lock`，直接执行 `action.get()`，之后检查 elapsed timeout。当前 callback 是只读聚合，其契约不承诺停止网络 mutation；本轮未把此正常只读契约登记成新的 scheduler P1。

[V39 schema](../../../backend/nq-infra/src/main/resources/db/migration/V39__gate_y2_live_session_fact_model.sql) 与 [claim implementation](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java) 明确 SEND_STARTED 的 durable CAS，但 token 并未被 ordinary HTTP receiver 识别。[V42 schema](../../../backend/nq-infra/src/main/resources/db/migration/V42__gate_y_minimal_live_pilot_execution_lease.sql) 明确 pilot/session 约束。本轮没有复活这些历史入口。

下面是 **候选设计的逻辑反例，不是声称本轮执行过的新实验**：

```text
A acquires PG lock, validates current status/token, pauses before HTTP
A's PG backend ends (connection loss); A's JVM is still alive
B acquires released lock, queries absent, commits CANCELLED
A resumes → HTTP PLACE
```

无论在 A 哪次 SELECT 后暂停，都仍有 SELECT 到 HTTP 的间隙。heartbeat watchdog、finally release、commit failure 均不能撤销已经发生的副作用。另一个边界是 A 已发 HTTP 后进程死亡，B 查询先返回 absent，而旧请求随后才到达/生效；仅证明 PID 已死也不足以处理这种在途请求。

因此不提交仅用锁/检查的候选来让原始屏障场景变绿，也不声称通用分布式系统中的所有方案不可能。

## 4. 跨网络持锁评估

| 评估项 | 结果 |
| --- | --- |
| Connection occupation | 每个网络等待占用连接；若 prepare/finalize 为独立事务还需额外连接预算。当前 ordinary 外发不在 DB 事务内，没有已接受的跨网持锁容量证明 |
| Timeout | JDBC/HTTP timeout 是不同边界，callback 暂停与远端请求完成都不由 transaction timeout 可靠撤销 |
| Deadlock | 同时涉及订单行、风险/审计、recovery/Trade/Ledger，必须统一锁顺序并限制等待；不能随意把整个 command 包进已有 read-only lock |
| Recovery starvation | 长暂停占锁或池耗尽可阻塞恢复；try-lock 跳过必须有可证明接管而不是永久跳过 |
| Process death | PG 会释放连接锁，但需额外区分数据库断连与 JVM 死亡，并处理先前在途 HTTP |
| Rollback | 外部已接受不随数据库事务 rollback；不得因此恢复为可重发状态 |
| B4 semantics | 已接受的 prepare/ACK commit ambiguity、断连与强杀场景都依赖清晰本地事务/外发分离。新跨网事务需重新证明这些交互，不能用本轮未跑的 suite 当通过 |

结论：不能把跨网络 PG 持锁认定为当前合理且足以满足 hard invariant 的 contract。

## 5. 拟议最小 V49 需求与仍待证明的执行端契约

以下是供 schema/contract review 的最小逻辑需求，**未创建 DDL、未迁移、未实现**。不是设计已接受声明。

1. ordinary 每个 Order 最多一个 PLACE authority 记录，以 order_id FK/unique 绑定原订单；不新增 Order/Trade/Ledger fact source，不依赖 live_session。列可附于 orders 或独立受约束表，需比较 migration/回填风险后选择，不在 reason/traceId 中编码。
2. 持久化 authority generation/version、attempt/owner identity、发送生命周期（至少区分可撤销的 pre-dispatch、可能在途、已不可再发、negative-finalized）、相关时间与明确的 quiescence/fence 证据身份。owner identity 不能只有可复用 PID；expiry 不能单独证明停止。
3. 同一短事务 CAS 仲裁 `pre-dispatch → dispatch-authorized` 与 `pre-dispatch → revoked/finalized`。finalizer 获胜后旧 sender 无法取得授权；sender 获胜后，仅凭当前 absent 不可终态化。Order terminal 更新与 authority 最终状态必须原子提交。
4. 对 dispatch-authorized/in-flight，negative finality 必须等待**真实执行端可强制的撤销/截止及排空证明**，或等价且经证明的不可再发契约；并在该边界之后 query-confirm。仅数据库 epoch/token 或本地最后一次检查不够。若用接收端 epoch/绝对截止，须确认 production adapter/venue 真正支持、时钟/过期/在途语义和每次请求都强制执行，不能只给 Synthetic Venue 增加功能。当前 ordinary request/HTTP path 未发现这一实现。
5. crash/ambiguity：无法确认 claim/dispatch/quiescence 提交结果时先查询 durable authority；外部结果不明时 query-first，绝不重新 PLACE。正向 venue/fill recovery 应继续，不因等待 negative finality 阻断已有 Trade/TradeExecuted/Ledger 收敛。
6. 既存 SENT/非终态行不得统一回填为 never-sent：它们可能存在活 sender 或外部 mutation。部署时必须评估旧 sender 排空/停止、在途请求屏障、保守 UNKNOWN 回填与 forward-only 约束。混跑旧 writer 不能绕过新协议。恢复索引与等待/backoff 有界。

**重要限制：V49 不是单独充分条件。** 目前缺少执行端 fence/quiescence 契约的证据；只授权加表仍不能让我给出 stale-resume 与 owner-death 的 PASS。最小下一步是 schema 与该契约的联合设计审查，而不是先迁移再试着补轮询。

拟议 concurrency map（未实现）：

```text
durable pre-dispatch
  ├─ finalizer atomic revoke wins → terminal commit → stale sender admission rejected
  └─ sender atomic authority wins → external dispatch may be in flight
       ├─ venue positive truth → canonical recovery / accounting
       └─ negative truth alone → non-final / bounded recheck
            → enforced no-future-mutation + in-flight drain proof
            → query-confirm absent → atomic negative finality
```

## 6. 原 P1、B1–B4 与测试 disposition

| Proof | 结果 |
| --- | --- |
| Original red P1 | 复用上一轮有效证据：SENT/v2 → B CANCELLED/v4 → A PLACE=1；未修、未重跑，不写 PASS |
| Recovery-wins / stale-resume | 修复后的要求 PLACE=0；当前尚未达到 |
| Sender-wins / near-concurrent race | 修复候选 NOT_AVAILABLE，NOT_RUN |
| Owner-death/takeover / duplicate command | 本轮 NOT_RUN；原 unresolved duplicate 观察不替代完整整改验收 |
| PostgreSQL tests / real-process tests | 本轮 NOT_RUN；源码协议反例不冒充运行证据 |
| B1 | query-first、accepted timeout/lost ACK/no blind retry 保持既有代码；未重新验证 |
| B2/C1 | 原红色证据显示 stale ACK CAS 成功保留 CANCELLED/v4；不是 C1 本地写保护回归失败 |
| B3 | kill 实现无改动，未运行回归 |
| B4 | 断连/提交不确定/进程死亡是新方案必须覆盖的边界，未运行回归，不声称新 authority 已兼容 |
| Full Maven | NOT_RUN：用户要求在 production candidate 稳定且 targeted 全绿后执行一次；本轮未达到此前提，没有消耗这次最终回归 |
| Remaining B5 qualification / exact-head CI | NOT_RUN，保持暂停 |

原运行的 A/B/Venue PID 为 13120/71284/76192，PG16.15/V48；PLACE=1、CANCEL=0、blind retry=0，Trade/TradeExecuted/Ledger=0。本轮没有新进程拓扑、新数据库或新副作用计数。

## 7. Recurring-problem / lesson / next action

原始历史 PB2 是 dormant 路径，C1 修的是本地 stale ACK；共同机制是 external finality 与仍存活 sender 的外发能力脱节。本轮完成 TRACE HISTORY / ROOT_CAUSE_IDENTIFIED，并在现有 [engineering-lessons.md](../../../.agents/skills/nq-trading-correctness-proof/references/engineering-lessons.md) 追加 External Mutation Finality Rule 与七步排查法。没有新 Skill、独立治理文档或 workaround。

ROOT_CAUSE_FIXED=NO、ORIGINAL_REPRO_PASS=NO、SYSTEMIC_CLOSURE=NO。经验记录完成不代表 P1 关闭。没有新增 synthetic 运行身份、exporter 修改或 stage-assets 例外。

Next action：评审本证据第5节的最小 ordinary durable authority schema 与执行端 fencing/quiescence 契约；若获明确授权才进入 V49/生产实现。随后按原任务运行双 JVM 对抗回归、相关 B1–B4、一次 Full Maven，并进入一次 Independent Correctness Review。当前不能跳至 Independent Correctness Review、B5 precise delivery 或 B6。

Commit recommendation：NONE；stage=0 / commit=NONE / push=NONE。本轮遵守用户“不能证明则 FAIL、需要 migration 则 STOP/SCHEMA_REVIEW_REQUIRED”的边界，未以未回复当作迁移授权。

## 8. 收尾验证

- 起始 6 个文件的逐字节 SHA256 对比全部一致，原 FAIL evidence/proof 与红色 harness 未修改；本轮增量仅一个工程经验段落和本 evidence。
- stage-assets：`scanned=1831 / reviewed_exceptions=173 / errors=0`；`git diff --check` PASS，两个文档 UTF-8/逐行空白/相对链接检查 PASS，index 空。
- pinned Gitleaks 8.18.4：归档 SHA 对当前 lock 一致，版本验证成功；当前 CI 原配置扫描本轮两个完整文档，exit=0 / findings=0。未改 scanner、allowlist 或 exporter。
- 上述检查只证明文档卫生和原证据保护；不证明生产整改、双 JVM 新协议或 Full Maven 通过。
