# B5 durable mutation authority / execution fencing contract review

Task classification: HIGH_RISK / ARCHITECTURE_AND_SCHEMA_REVIEW / CONCURRENCY_PROTOCOL_DESIGN / REVIEW_ONLY / NQ-only。

Starting HEAD: `86c8ad84542636364f6c21e78bc292a323cbdff7`；branch 与本地 origin 跟踪引用为 `audit/post-gatey-agent-baseline`，HEAD 一致，未 fetch。日期：2026-09-10。本轮只产生设计结论，不是 production candidate 的独立正确性验收。

**PASS / B5_MUTATION_AUTHORITY_CONTRACT_ACCEPTED / FAIL_CLOSED_UNCERTAINTY_PROTOCOL_SELECTED / V49_SCHEMA_CONTRACT_READY / READY_FOR_IMPLEMENTATION / P1_OPEN_PENDING_IMPLEMENTATION**。

这里的 PASS 仅表示本任务的协议/schema 设计结论；没有实现、migration、业务测试修改或运行时 PASS。P0=0；既有 P1=1/OPEN；B5=NOT_QUALIFIED。F3–F6 沿用用户基线 OPEN/P2/NON_BLOCKING，本轮未重新审查；未另登记新 P2。

## 1. 决策与前提变化

选择 **Contract B：不可逆的一次性发送决定 + fail-closed uncertainty**。不承诺撤回已发请求，也不通过 sender 死亡、超时、DB 断连或多次 absent 猜测“永远不会发生 mutation”。已经可能发出的同一 logical PLACE 永不重新发放发送资格。

当前任务第7节明确允许无法自动消除的不确定性进入 `MANUAL_RESOLUTION_REQUIRED`。因此，上一轮“必须自动消除 orphan/unresolved 且旧 sender 物理不可再发”的组合不再是本轮 Contract B 的前提。[attempt01](GATEAUDIT_PHASE6_L4_B5_ORDINARY_SENDER_RECOVERY_FINALITY_REMEDIATION_ATTEMPT01.md) 的原始 STOP 记录不改写；其对 strict physical fencing 的限制仍成立，但不能据此排除本轮允许的 fail-closed 方案。

核心结果是：**合法 no-order finality 与唯一发送资格不可能同时取得**。若唯一发送资格先取得，原 A 迟到 PLACE 可以发生，但 B 不能提前制造 DB=no-order-terminal/venue=active 的冲突；B 也不能创建第二次 PLACE。未承诺没有外部事务支持时可自动 exactly-once 完成所有订单。

## 2. 当前真实链与边界

| 审计项 | 当前 production 源码事实 |
| --- | --- |
| Ordinary mutation chain | `TradingVerificationController.placeOrder` → `OrderCommandService.placeOrder` → `OrderCommandWriteService.preparePlaceOrder` → `AdapterBackedTradingVenueGateway.placeOrder` → `TradingAdapter`/`OkxExchangeAdapter.placeOrder` → `OkxHttpClient.post/send` → JDK `HttpClient.send` → OS/network → venue 接受/执行 |
| Last durable operation before PLACE | prepare 事务包含 Order INSERT、RiskGate、状态/事件/审计，提交 SENT/v2 后返回 sentOrder；当前常规链在这之后没有发送资格 CAS。必须区分最后业务状态更新和该事务最终 COMMIT |
| Last authority check | prepare 内 RiskGate 是 admission；service 用 completedResult 决定是否继续。gateway/adapter 有参数、readiness、instrument 检查，没有查询 DB authority generation 的 last-mile fence |
| Actual HTTP boundary | adapter 先获取 instruments、trim price/qty、构造 body，再 `authenticatedHttpClient.post`；`OkxHttpClient.send` 中 `httpClient.send(request, BodyHandlers.ofString())` 才是交给 HTTP transport 的边界。真正 side effect 在 venue 接受/执行，Service 返回值和 socket write 都不能证明它已发生或被撤回 |
| Timeout owner | `OkxRuntimeConfig` 的统一 timeout（默认5000ms，可由既有环境配置覆盖）绑定 JDK connect timeout 与 HttpRequest timeout；adapter 捕获 HTTP_TIMEOUT 后 query-confirm。单次 timeout 不是整条业务链的总截止，也不是 venue 不再执行的保证。B0 test injection 的25秒只属于旧 harness，不是 production 默认值 |
| Recovery path | `OkxRecoveryService.rebuild` 的 bounded candidate scan → open-order hydration → getOrder query-confirm → 51603/NOT_FOUND → `transitionToCancelled` → lifecycle requestCancel/cancel。另一条 `OkxRestReconcileService.reconcileOnce` 经 V48 cursor 扫描，NOT_FOUND 已记录 UNRESOLVED 并返回，不直接 negative terminalize |
| Current sender/recovery ownership | sender 是成功 prepare 后仍存活的同步调用栈；没有 durable ordinary process owner。另一线程/JVM 可调用 recovery。scheduler advisory lock 只保护 validation evidence 只读聚合，不包 ordinary mutation/recovery |
| ExecutionIntent | 当前 ordinary 链不经过 `ExecutionIntentService`；该 fake/local service 的 production `claimAndExecute` 引用仅自身重载。不能为此方案恢复 dormant worker 或伪造 live_session |

源码锚点（行号按 starting HEAD）：

- [command](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java) 86–135；[write owner](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java) 的 prepare/finalize；[gateway](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/AdapterBackedTradingVenueGateway.java) 70–102。
- [adapter](../../../backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxExchangeAdapter.java) 130–163、229–260、436–520、buildPlaceOrderBody/buildOrderIdentityQuery；[HTTP transport](../../../backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxHttpClient.java) send/buildRequest；[timeout config](../../../backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxRuntimeConfig.java)。
- [negative recovery](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRecoveryService.java) 211–260；[query/reconcile](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/OkxRestReconcileService.java) 128–275；[OCC repository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepository.java) compareAndSetStatus。

## 3. 两类契约与不可能性边界

当前 direct HTTP 不携带或验证 DB authority generation；未发现由 NQ 控制且能阻止绕过的 network egress enforcement point。因此 **DB fencing token alone != external side-effect fencing**。

```text
validate token → pause → B revoke/terminalize → resume → HTTP PLACE
PG backend lost → lock released → old JVM remains alive
HTTP handed to OS/network → JVM dies → request later accepted by venue
```

上述是协议反例，不是本轮新运行的故障实验。一次 status SELECT、短事务 CAS 后的再检查、长期 PG 锁、看门狗或 JVM interrupt 都不能单独排除它们。

**Contract A — strict execution-side fencing：当前不可选。** validator 必须位于不可绕过的实际执行/接受边界；撤销与 dispatch/accept 必须有明确线性化关系。单纯本机代理多一次 SELECT 仍有相同间隙；已从代理发出的请求仍可能在撤销后到达。即使增加单 executor，也须解决其重启、旧副本、排队/在途请求和 validator 自身 fencing；validator 死亡默认拒绝新发，不能自动授权另一个无约束 sender。若使用 venue 原生 generation/截止保证，还须证明 receiver 强制、查询可见性和 in-flight 排空；当前 adapter 契约未实现或验证该保证。本轮不接触真实 venue，也不由 Synthetic Venue 发明这一能力。为当前 P1 新建受控 egress/HA executor 不如 Contract B 小，且它本身不能免除下游在途证明。

| Candidate | Prevent stale physical send | Prevent false no-order finality | Process pause | DB loss | Complexity | Current-stage fit |
| --- | --- | --- | --- | --- | --- | --- |
| DB lock only | NO，连接丢失后旧 JVM 可继续 | 仅连接存续且所有参与者合作的区间成立 | 持锁阻塞可影响恢复 | 不足 | 中：跨网连接/锁预算 | 不选 |
| DB epoch/token only | NO，receiver 不验证 | token 检查到网络间仍有窗口 | 不足 | 不足 | 低但未满足目标 | 不选 |
| Strict execution-side fencing | 当前无已证明 enforcement point；完整接收端协议才可能 | 条件成立时可以 | 需真实 last-mile fence | validator/在途协议均须证明 | 高且超当前必要范围 | 不选 |
| Fail-closed may-have-escaped | 不承诺，允许原唯一调用迟到 | YES，按第5节互斥证明 | durable MAY 不撤销/重授 | commit unknown 无新增发送 | 低：短事务、一次决定 | **选择** |

## 4. Venue idempotency 与身份

`clientOrderId` 由调用方提供，Trading API DTO/PlaceOrderRequest 要求非空；当前交易工作台要求用户填写，不是服务端为每次重试生成新 ID。controller 构造 `accountId:clientOrderId`，V1 唯一约束 `(account_id, client_order_id)`；Order ID 是本地另一身份，不能替换 client ID。adapter body 的 clOrdId 来自该 request；recovery 复用 durable Order 的 clientOrderId，已知 externalOrderId 时优先按它查询，不新建订单身份。

当前 repository 无修改 account/client 的正常方法，但数据库唯一性不等于字段不可变；V49 方案须补上已绑定 authority 的 identity/payload 不可变约束。唯一范围是本地账户+client，不自动证明多个本地账户映射同一 venue credential 时的远端唯一范围。

**真实 venue 对重复 clOrdId 的时间范围、终态后复用、retention 和 exactly-once 语义未在本轮获得可依赖的保证。** B0/B2 Synthetic Venue 的重复处理不能代替真实 venue 文档/契约。方案不依赖 venue 去重来允许重发；每个 logical command 最多授予一次应用层 PLACE 调用，不能通过改 client ID 或 request ID重试同一意图。后续必须继续计数 HTTP PLACE requests 与 venue accepted effects，确认 transport 不自行重放可能已产生副作用的 POST；JDK/adapter 调用次数不是远端恰好一次的替代证明。

## 5. 最小 authority 状态机与证明

scope：现有 ordinary OKX PLACE 路径，SIM/LIVE domain 隔离保持，测试仅依隔离授权。CANCEL intent、历史 pilot、其他 venue 的独立状态机不在此方案扩展范围；它们不能消费此 PLACE authority。

最小持久化状态只有三个：

```text
NOT_ARMED ── atomic one-shot arm ──> MAY_HAVE_ESCAPED   [absorbing]
    └────── atomic revoke ────────> REVOKED_BEFORE_SEND [absorbing]
```

- NOT_ARMED：创建 authority 前不存在任何该 logical PLACE 的发送资格；Order 可以已是 SENT，表示本地 prepare 完成，不表示已 arm。
- MAY_HAVE_ESCAPED：即 `MUTATION_MAY_HAVE_ESCAPED`。该状态的成功 COMMIT 是不可逆边界，位于调用 adapter 之前；包括“已经发出”和“唯一原调用今后仍可能发出”。不拆 SEND_AUTHORIZED/MAY 两阶段，因为前者只要能赋予外发能力就必须按 MAY 处理。
- REVOKED_BEFORE_SEND：先于 arm 赢得撤销，之后永远不能再 arm。撤销与无订单 terminal commit 在同一事务；生命周期所需的 CANCEL_REQUESTED→CANCELLED 仍使用原 Order 状态机和逐次 version CAS。
- VENUE_CONFIRMED/RESOLVED 为 **由 Order/external identity、venue 查询、Trade/event/Ledger facts 导出的业务结果**，不另存一套 authority 终态。MAY 在 Order FILLED 或真实 venue CANCELLED 后仍保留为不可再发墓碑。
- 缺 authority 行不是 NOT_ARMED；视为 LEGACY_OR_MISSING_UNCERTAIN，禁止发送和 absent 终态化，可继续正向 query/reconcile。

**归纳证明**（针对遵守新协议的 ordinary writers，数据库约束见第6节）：

1. 每个 Order 只有一个 authority，唯一允许的更新是从 NOT_ARMED 二选一；没有 reset、rearm、expiry takeover 或删除重建。
2. S=NOT_ARMED→MAY 与 R=NOT_ARMED→REVOKED 使用同一行、相同锁顺序的条件更新。PostgreSQL 串行化同一行竞争，至多一个事务提交其目标。
3. 只有 S 的 affectedRows=1 且 COMMIT 成功已确认的**同一次存活调用**获得一次 adapter 调用许可。`SELECT MAY`、相同 token、重复 request、重启或重新调用 API 均不授予许可。
4. no-order terminalization 只随 R 同一事务提交，或幂等返回其已提交结果；MAY/missing+absent 没有此迁移。
5. 若 R 先成功，S 不成立，A 的发送数=0。若 S 先成功，R 不成立；原 A 无论暂停、断连还是在途，都不能与合法 no-order 终态并存。另一 B 没有第二次 S，故没有第二次 PLACE 授权。
6. S COMMIT unknown 时该调用放弃发送，即便后来 SELECT 确认 MAY；损失可用性但不损失安全性。读回仅用于恢复分类，不能将 readback 当作 grant。

协议假设不包含恶意进程任意绕过所有业务接口直接调用 venue；生产旧 writer 混跑、transport POST 重发和不受约束的替代 ordinary gateway 都是 implementation admission 必须排除的旁路。

## 6. 具体 V49 schema proposal（仅设计，不创建 migration）

新增 **一张表 `ordinary_place_authorities`**，不建立新的 Order SoR，不存金额/成交/账户余额。选择一次性决定，因此不需要可重获的 lease、epoch、leader 或独立 authority UUID。

| Column | Type / null | Invariant / writer / reader / transition / constraint |
| --- | --- | --- |
| order_id | VARCHAR(64), NOT NULL | 同一 logical PLACE 的唯一持久身份；prepare 创建，arm/recovery 查询；PK，FK orders(order_id)，ON UPDATE/DELETE RESTRICT，永不更新 |
| state | VARCHAR(24), NOT NULL，无默认值 | NOT_ARMED/MAY_HAVE_ESCAPED/REVOKED_BEFORE_SEND；prepare 初始化；sender/recovery 原子二选一；CHECK 枚举 + transition trigger 拒绝逆转、交叉转换及删除重建 |
| decided_at | TIMESTAMPTZ, nullable | NOT_ARMED 必须 NULL；MAY/REVOKED 必须非 NULL，由 DB 在决定更新时赋值并冻结；恢复用于 unresolved 年龄/人工升级与证据，不用作 lease/自动终态截止。保守历史 MAY 的值表示迁移分类时点，不是假造实际发送时间 |

不增字段的明确理由：

- authority_id/logical identity：Order PK 足够；FK+既有 `(account_id,client_order_id)` unique 绑定同一 logical PLACE。
- generation/epoch/version：不支持第二代或 rearm，三态有向无环 CAS 无 ABA；orders.version 继续只管 Order 状态。不能为了展示 fencing 增加永远为1的 epoch。
- owner identity/acquired_at：不做 mutation ownership 转移；数据库行的 MAY 不能恢复调用许可，因此 PID/token 不参与安全判定。原调用 audit/event 可记录运行身份用于诊断，不另建 lease owner 字段。
- may_have_escaped_at/revoked_at：共享 decided_at，根据互斥 state 解释。
- resolved_at：沿用既有业务事实/事件，不复制第二套交易终态。

约束与写入合同：

1. PK(order_id) 永久保证一单一 authority，不需要 partial unique active index。CHECK 保证 state/decided_at 配对；trigger 要求仅 NOT_ARMED→MAY 或 NOT_ARMED→REVOKED，冻结其他字段。已吸收状态只允许完全不变的幂等读取，不伪造更新为重新 grant。
2. 禁止普通运行角色 DELETE/TRUNCATE authority 或删除重建绑定 Order；DELETE 触发器和 FK 限制作为补充。DDL owner/超级用户不属于应用并发证明的威胁模型；实现必须检查真实运行角色权限，不能使用拥有禁用 trigger 权限的角色声称约束有效。
3. 已有 authority 的 orders.account_id/client_order_id/venue/trade_env/symbol/side/type/price/qty 不允许改变；用 orders 更新触发器保护绑定及发送 payload，externalOrderId 单调 enrichment 与合法状态/version 更新仍允许。authority 不复制这些字段。
4. runtime 新 NOT_ARMED 只能由 canonical 新订单 prepare 在同一事务创建，与 Order INSERT、risk facts、SENT 提交绑定；不能对已有订单用 `ON CONFLICT DO NOTHING` 后“补 NOT_ARMED”。将创建/arm/revoke 的 authority DML 收口到有界 PostgreSQL helper/明确数据库权限接口，运行角色不能任意 INSERT/UPDATE authority；新订单创建 helper 必须原子 INSERT 新 Order 与 NOT_ARMED，已有 Order 唯一冲突则整个创建失败。prepare 后续风险失败同事务回滚/拒绝，不能外发。权限授予按实际 deployment role 配置，不在设计中臆造角色名。
5. arm helper：调用方必须是新 prepare 的原执行链，锁定既有 Kill 行→Order→authority；核对实际 Order status/version、环境/账户 binding 与有效风险前提，NOT_ARMED CAS 为 MAY，记录 canonical audit/event。事务成功返回的一次性决策只能由原调用消费，不能序列化成可重放消息。helper 不执行 HTTP。
6. revoke/no-order helper：Order→authority 同序锁定，当前行必须 NOT_ARMED；撤销与合法 Order terminal/version/events 原子提交。不得先将 authority 撤销提交，再另事务写 no-order 终态；回滚一起回滚。所有基于 absent 的 negative finalizer 必须调用该 application/repository owner；数据库仅凭自由文本 reason 不能判定真实 venue 证据，不能把 CHECK 描述成万能终态真实性验证。
7. positive venue 确认/实际拒单/取消/成交沿用 canonical 状态机；不能把 transport exception、absent 或 unknown outcome 伪装成确定性 REJECTED/CANCELLED 来绕过 no-order helper。实现必须覆盖这类分类入口。已有真实业务拒绝仍须有对应的确定证据。

**Index**：仅 PK(order_id)。候选发现继续从既有 Order + V48 cursor 按 limit 扫描，在原 bounded 查询中 LEFT JOIN authority 一次取出元数据，避免每单额外读表。无独立 authority worker、无全表 heartbeat 扫描、无新 owner 扫描索引。人工升级年龄可从 bounded 候选的 decided_at 计算，不为未来任务预建索引。

**锁和事务**：均为短 DB 事务，不跨网络。Kill admission 复用 `kill_switch_states` 的既有锁定读取/CAS primitive；arm 在持锁时检查 ENGAGED，直到 arm commit，禁止与 ENGAGE 发生“先读后无保护 arm”的间隙。全局锁顺序 Kill→Order→authority；negative recovery 不需要 Kill 锁，Order→authority 后不可反向请求 Kill。需限时等待并通过 PG 并发测试检查与已有风险查询锁的组合。

**数据库永久保证与应用义务分开**：PK/FK/transition trigger/写权限保证唯一性、不可重授、绑定不变；同事务 helper 保证两种决定互斥及 no-order 更新原子性。真实 venue 证据分类、一次成功 grant 只调用一次 transport、没有可绕过旧 writer，是应用/执行契约的义务，必须用后续真实进程与架构检查证明，不能谎称全由 CHECK 完成。

## 7. 无迁移替代、回填与兼容窗口

**无迁移替代确实存在安全方向**：保守地把当前所有 durable SENT 及后续可能发送状态解释为 MAY，所有 absent 只 unresolved，不再负面终态化，维持既有唯一 INSERT 的一次调用。它可以避免原 P1 的错误终态，并接受更多 MANUAL_RESOLUTION_REQUIRED；不能再沿用 attempt01 的可用性要求把它直接判“不可能”。

本设计选 V49 是为了具备用户要求的**durable prepare 后仍可证明从未获发送资格、允许 recovery 抢先撤销**的可回收窗口，并把一次决定从过载的 SENT 语义分离出来。V49 不是在逻辑上关闭初始 P1 的唯一办法；若明确放弃该可撤销窗口，可重新选择更保守的无迁移方案。当前所选三列表是该窗口的最小显式协议，不扩展为 HA 框架。

迁移/启用要求：

- forward-only V49；旧迁移和已发布证据不改写。没有执行任何 DDL。
- 现存相关 ordinary OKX Order 一律保守归类 MAY，不从 status、absence、updated_at 推断 NOT_ARMED；历史 terminal row 也不能获得新的发送机会。现存 false CANCELLED 不自动重写，应单独识别并按实际 venue truth 处理，历史数据纠错授权不由本 review 授予。
- 迁移可以分阶段建表/约束与按稳定 order_id keyset 分批回填，每批有限、冲突仅保留已有值，不覆盖 NOT_ARMED/REVOKED；中断可续，不用一个无限大迁移事务锁全表。V49 DDL 与大规模 backfill 分开，实际行数/批大小/lock timeout/部署预算须实施时测量；不得编造当前生产规模。
- 回填前后 missing authority 永远保守 uncertain；只有新 canonical 创建事务可产生 NOT_ARMED。收紧 helper 权限和触发器前检查存量 writer 的兼容性。
- 启用必须停止旧 ordinary sender/recovery writer 后以新版本整体切换，禁止旧版本绕过 helper/MAY 判断。旧进程与在途请求不能靠迁移“撤回”，将旧订单保守 MAY 正是为其保留安全性。排空/暂停是部署前提，不是本轮执行动作或发布授权。
- 不允许回滚到忽略新 authority 的旧二进制继续发送/negative terminalization；恢复走兼容的前向修复或暂停 mutation。PG16 下需实测 DDL锁、角色权限、FK/trigger、回填中断和 rolling compatibility rejection。

## 8. Sender 与 recovery 协议

| Sender condition | 必须行为 |
| --- | --- |
| Before send | 原 RiskGate/admission 不绕过；prepare 新订单+NOT_ARMED；短事务 arm 成功提交并确认后才有一次 adapter 调用机会 |
| Duplicate logical command | 按 durable account/client 返回已有 Order，不重新创建 authority、不 arm、不 PLACE，即使它仍 NOT_ARMED |
| Arm lost / REVOKED / missing | fail closed；读取当前业务结果、query/reconcile，无发送、无新 token |
| Arm commit rejection | 无发送；若确定回滚则仍按原命令结果处理，不把重试变成新的 mutation attempt |
| Arm COMMIT unknown | 无发送；读回分类，看到 MAY 也只能恢复/人工处理，不能根据相同 request ID 再发 |
| During send / success | 一次 transport 调用；ACK/identity/状态按现有 OCC 提交，MAY 永久保留；无长 DB 事务 |
| Timeout / exception | 保持 MAY；query-first；没有可靠拒绝事实不得 no-order terminalize；禁止自动新 PLACE |
| DB loss after confirmed arm | 不获得新资格；原调用未被明确中止时可能执行其唯一一次 PLACE，B 必须按 MAY 保留 uncertainty。若调用已中止/重启则不能恢复“剩余发送机会” |
| Pause / resume | pause 在 arm 前：若 B 撤销，CAS失败，PLACE=0。pause 在 confirmed MAY 后：无可撤销 lease，仍最多原来一次 PLACE；不得宣称 stale physical send=0 |
| Process death | mutation grant 不继承、不重发。B 接管 query/reconciliation，不接管 PLACE |
| Authority loss | NOT_ARMED 竞争失败=永不发送；MAY 没有 runtime revoke/expiry。所谓“DB session丢失”不是 durable MAY 撤销，不能把新 epoch 发给 B；已丢失原调用许可的重启进程只 query |

| Recovery observation | Allowed actions | Forbidden actions |
| --- | --- | --- |
| NOT_ARMED | bounded QUERY；当前状态和范围允许时 atomic REVOKE+TERMINALIZE；若发现已有 venue positive truth 则视为契约异常，保留证据并恢复真实事实 | 根据旧 snapshot 不经 CAS 终态化；重新 PLACE |
| Sender与 finalizer近同时 | 由同一行 CAS 决定；失败者重新读取并按现状 query/wait | 两边各自据缓存决定 |
| MAY + venue absent/ambiguous | QUERY / WAIT / RECONCILE / escalation | REVOKE、absent→CANCELLED、PLACE |
| MAY + venue present/fills | 现有 identity hydration、OCC 状态收敛、Trade/required event/Ledger恢复；MAY 不重置 | 重发 PLACE、换 logical identity |
| Owner dead/stale/DB disconnected | 行状态决定行为；NOT_ARMED可撤销，MAY只接管 recovery | 将 PID死亡、lease expiry或连接断开当作从未发出的证明 |
| REVOKED_BEFORE_SEND | 返回已提交无订单终态；异常 positive truth 触发 correctness调查，不悄悄重新 arm | 再授予发送、删除 tombstone |
| Missing/legacy | 保守 query/recover，补历史 MAY 只能按受控 backfill；必要时人工 | 插入 NOT_ARMED 恢复发送资格 |

Unresolved 使用**现有非终态 Order（原 SENT，或已进入 ACCEPTED/CANCEL_REQUESTED 等的实际状态）+ authority metadata**，不把当前状态倒退到 SENT，不新增 UNKNOWN/RECONCILING 等 Order 状态。正向证据可随时推进；单次或任意有限次 absent 不产生不可再发证明。

恢复调度沿用实际可达的普通触发入口和 V48 bounded cursor；不激活 retired `@Scheduled`。每次查询有 timeout，批量 limit/backoff 和观察次数/时间预算有界，超过操作预算进入 `MANUAL_RESOLUTION_REQUIRED` 告警/审计，状态仍可恢复。预算只决定升级，不证明 no-order。人工也不能仅凭“等够久”改 CANCELLED或重新 PLACE；必须取得实际 venue/成交事实，或独立授权且可证明的停止所有旧发送/在途失效条件。无法取得则可以长期 unresolved，这是本轮明确接受的可用性代价，**不是永久锁或需要死 owner 解锁的 orphan**。

## 9. 后续双 JVM 证明模型（本轮 NOT_RUN）

全部使用真实 Spring A/B、同 PG16/V49、同 Synthetic Venue，保留原 V48 红色历史；不得用新 test owner 代替 production grant。关键 CAS 竞争多轮独立执行，使用事务/transport 屏障而非固定 sleep 赢得顺序。

| Scenario | 后续 required oracle |
| --- | --- |
| Original P1 / recovery wins | A SENT/v2+NOT_ARMED，B原子REVOKED+CANCELLED，A恢复arm失败；venue PLACE=0；无假发送/冲突事件 |
| Sender wins before adapter | A确认MAY后暂停，B query absent但不得no-order terminalize；A恢复唯一PLACE，venue truth最终收敛；其他调用PLACE=0 |
| Near-simultaneous race | 只允许R胜/零PLACE或S胜/至多一PLACE；不允许R提交后有效S；SQL state、Order版本、事件、venue请求与效果同时验证 |
| Stale A resumes after DB loss | arm前被撤销→0；confirmed MAY后→至多原1次，B保留uncertain；不把此分支谎报物理fencing |
| A killed before arm / after MAY before wire | 前者B可撤销；后者B查询并进入unresolved/manual，PLACE=0、无重授、无锁泄漏，不强求自动CANCELLED |
| A killed after wire / accepted ACK lost | B query-first，late request/positive venue/fill后恢复；未发现前不得负面终态，PLACE requests无重发 |
| Arm commit rejection/response loss | 确认拒绝和确认丢失分开记录；COMMIT unknown原调用即使读回MAY也不发送 |
| Duplicate identity | sequential、overlap、unresolved时重复command均不再arm/PLACE；同一Order/authority，不重复Trade/TradeExecuted/Ledger |
| DB adversarial | 并发arm/revoke、重复PK、非法phase/null、逆转/删除/重建、payload替换、runtime直接authority DML、漏authority、回填中断/角色旁路拒绝 |

原 B5 最小测试的红色 history 不改写；后续在同一 ordinary 入口加入 production authority 后，保留其期望行为并扩展 sender-wins 分支。业务 orphan要求需按 Contract B解释为“恢复可继续且没有死锁/owner占用”，不能再把无证据的自动终态列作PASS条件。

## 10. B1/B3/B4、Kill 与 architecture ownership

| Interaction | 合同 |
| --- | --- |
| B1 accepted-timeout/lost ACK | MAY不回退；继续同clientOrderId query，绝不重新PLACE。当前timeout query-confirm的HTTP/result分类必须在实现时检查，UNKNOWN/NOT_FOUND不得被误认为已证实无副作用 |
| B2/C1 | orders.version仍保护本地状态/旧ACK；authority不替代OCC。正向fill/cancel/reconcile和Trade唯一事实沿用原owner，不以新表建立账务 |
| B3 / Kill | 新arm必须经过原风险前置并与canonical Kill ENGAGE串行化；ENGAGED后不能新arm，允许query/reconcile。已confirmed MAY是in-flight边界，Kill不被夸大成能撤回既有HTTP/原调用；在可检查处拒绝继续可减少发送，但本协议安全性不依赖该JVM检查。没有DISENGAGE/真实LIVE授权 |
| B4 prepare commit rejection | Order+NOT_ARMED同事务，拒绝则无外发；prepare结果不明时不进入arm |
| B4 authority commit-response loss/connection loss | 新增的短事务unknown→无mutation；durable MAY也不重授；negative finality事务unknown→读回，不自行假设已CANCELLED |
| B4 ACK/process death | 外部成功、本地ACK失败可恢复，MAY仍在；不将网络置于本地事务，不把rollback当远端撤销 |
| B4 durable fan-out | Trade+required TradeExecuted原子source写入、Ledger重放保持原owner；MAY或人工待处理不能阻塞已找到的正向事实恢复 |

Canonical owners：domain/`nq-core` 定义三态语义与一次性决定；application 的 OrderCommand/Write owner 编排prepare/arm/finalize；`nq-infra` 实现PG CAS/helper/约束/角色权限；execution/provider 只消费原成功调用的一次机会并进入现有adapter/HTTP，不声称验证epoch；recovery归原查询/对账owner，负面终态通过同一application事务协议。Controller无SQL/并发逻辑，scheduler不拥有交易正确性，adapter不成为Order SoR，composition root只装配。

## 11. 交付边界与最终决定

Implementation task name：`NQ-GATEAUDIT-PHASE6-L4-B5-FAIL-CLOSED-MUTATION-AUTHORITY-V49-IMPLEMENTATION`。这是下一动作名称，不是本轮授权执行migration/创建新任务。后续实施须明确授权V49；实现后按选定Contract B更新proof预期、运行相关PG/双JVM/B1–B4，再按实施合同运行最终Full Maven与一次真正Independent Correctness Review。

Review limitations：本轮为当前代码支持下的静态协议/schema审查与状态迁移证明；未测新DDL/权限/实际迁移锁、未证明新代码运行时PASS，未核实production部署或真实venue去重保证。PASS不覆盖这些尚未实现的验收义务，也不宣称本设计是对作者独立的候选审查。

production code change=0 / migration=0 / business test modification=0 / stage=0 / commit=NONE / push=NONE / real exchange=0 / LIVE=0。本轮仅新增本设计evidence与现有工程经验的必要Contract B澄清；此前FAIL及attempt01 evidence原样保留。

收尾检查：起始8个未提交文件中，允许追加的engineering lesson之外7个文件逐字节哈希一致。stage-assets=`scanned1831 / reviewed_exceptions173 / errors0`；两个完整文档的pinned Gitleaks8.18.4扫描exit0/findings0（归档SHA与当前lock、版本和CI原配置均核对）；UTF-8、空白、相对链接及`git diff --check`通过，index为空。未运行业务测试、Full Maven、Flyway或CI，文档检查不冒充协议运行证明。
