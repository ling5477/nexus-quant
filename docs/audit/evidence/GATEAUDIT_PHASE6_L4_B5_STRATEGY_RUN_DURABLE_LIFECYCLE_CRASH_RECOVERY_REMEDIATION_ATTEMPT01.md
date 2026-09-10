# B5 StrategyRun durable lifecycle crash recovery — attempt01

本轮结果：**STOP / STRATEGY_RUN_TO_ORDER_IDEMPOTENCY_GAP / SCHEMA_REVIEW_REQUIRED**。生产修复未实施，`STRATEGY_RUN_DURABLE_LIFECYCLE_RECOVERY_NOT_PROVEN`；原 CREATED P1 仍 OPEN，并实际复现另一个同根因的 RUNNING P1。B5 保持 **NOT_QUALIFIED**。

用户任务第6节要求在不能证明同 run 最多一个 Order 时停止直接实现 CREATED recovery；第19/21节要求涉及新 schema 时先提交最小 contract。本轮先交付红色回归、有限生命周期清单和设计候选，不新建 V51，不修改 V50/V49，不将 STOP 伪装为 IMPLEMENTED。

## 身份、范围与历史

- Task：`NQ-GATEAUDIT-PHASE6-L4-B5-STRATEGY-RUN-DURABLE-LIFECYCLE-CRASH-RECOVERY-REMEDIATION`；HIGH_RISK / P1_CORRECTNESS_REMEDIATION / CRASH_RECOVERY / POSTGRESQL_CONCURRENCY / RECURRING_ROOT_CAUSE / NQ-only。
- HEAD=`86c8ad84542636364f6c21e78bc292a323cbdff7`；branch=`audit/post-gatey-agent-baseline`；worktree=`E:/Project/nexus-quant-gateaudit`。
- 起点3561个非忽略 tracked/untracked 文件，按排序的 `path + NUL + SHA256 + LF` 聚合指纹为 `e4b85d09fecb20a9795b0366a518fb0d7a48f81520f4e738d7c79a63ca5319ae`；全量起点快照保存在 `backend/nq-app/target/b5-lifecycle-remediation-attempt01/starting-files.json`。
- 既有 DISPATCHING 无发送恢复见[原整改](GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_OWNER_DEATH_RECOVERY_REMEDIATION_ATTEMPT01.md)，V50候选见[admission整改](GATEAUDIT_PHASE6_L4_B5_STRATEGY_SAME_WINDOW_DISPATCH_ADMISSION_REMEDIATION.md)。本轮不重审或撤销此前 V49/TradingVenue 的接受事实。
- 上轮 reviewer 的 CREATED 红色事实以 canonical 副本永久保留，原始 JSON、驱动和日志不覆盖；本轮新增相同生产断点的永久测试。证据索引见 [proof-index](l4-b5-lifecycle-remediation-attempt01/proof-index.json)。

## 当前实际可达的有限状态清单

生产 enum 仅有 `CREATED / DISPATCHING / RUNNING / FAILED`。scheduler 和独立 manual trigger 都经 `StrategyManualTriggerService`；不扩展到 PaperRun、ShadowRun 或其他项目状态机。

| 状态/断点 | 进入状态的 durable boundary | 下一项 durable work/外部动作 | owner 死亡后现有 writer | 合法恢复事实与当前分类 |
| --- | --- | --- | --- | --- |
| CREATED，admission/insert 已提交 | scheduler 的 V50 `admit(REQUIRES_NEW)`；manual 的普通 insert | 独立的 CREATED→DISPATCHING CAS，然后 Order prepare | 无；`recoverNoSendDispatches` 排除 CREATED；active 查询永久计入 | 原 P1，A/B/C 均未满足。scheduler 的已有字段可提供大部分 work，但不能据此恢复任意 manual 请求；不能仅按状态/年龄回收。 |
| DISPATCHING，CAS 后、Order 前 | `updateStatus(DISPATCHING)` 独立提交 | OrderCommandService 先写 command event，再进入 prepare 事务 | 无 Order 时 JOIN 不匹配 | 同类缺口；仅修 CREATED 的 CAS 会将死亡窗口向后移动。须将状态推进和必要本地 work 原子建立。 |
| DISPATCHING，Order prepare 已提交、V49 arm 前 | 同事务 Order、RiskGate结果/风险事件、Order事件、V49 NOT_ARMED；允许路径为 SENT | 独立 arm 事务，之后 HTTP PLACE | 原 Order recovery 可竞争撤销 NOT_ARMED；原 StrategyRun writer 在 CANCELLED + REVOKED_BEFORE_SEND 后设 FAILED | 已接受 B 类安全终结分支；stale sender 由原 V49 CAS 拒绝。本轮只保留契约，未重新验收。 |
| DISPATCHING，prepare 得到 RISK_REJECTED，或拒绝/finalize 已提交但 run callback 未执行 | Order 已有明确结果，run 仍 DISPATCHING | 原 JVM 的 RUNNING/FAILED callback | 原 writer 仅匹配 CANCELLED + 特定 no-order reason + REVOKED，不覆盖一般风险拒绝/明确结果 | 同类 lifecycle gap，静态确认覆盖缺失；未在本轮单独运行全部变体，不能记 PASS。 |
| DISPATCHING，MAY_HAVE_ESCAPED 已提交，HTTP 前/中/回包丢失 | 原 V49 一次性 arm 已决定 | 唯一原发送者可能发送；successor 只能查询/对账 | 原 Order query/reconcile；不能重授发送许可 | C：真实不确定性必须保持 unresolved，遵守既有 query/manual-resolution contract；不能根据 absent、PID 或年龄终结/重发。 |
| DISPATCHING，Order ACCEPTED/PARTIALLY_FILLED/FILLED durable，run callback 丢失 | Order finalize/reconcile 独立提交 | 将原 run 与其 Order 事实收敛 | Order 有恢复，原 run writer 不覆盖这些结果 | 活动 Order 可以继续查询；Order 明确终结后没有 run writer，属于同一 lifecycle owner 缺口。 |
| RUNNING，Order 尚未终结 | 原 callback 将 SENT/ACCEPTED/PARTIALLY_FILLED/FILLED 均映射 RUNNING | Order query/reconciliation、Trade/Event/Ledger恢复；最终还须释放 run active | Order恢复存在；无 RUNNING lifecycle writer | 在确实未决时可按 C 保持；不能把已经明确 FILLED 的事实继续归为未知。 |
| RUNNING，Order FILLED，Trade/Event/Ledger完成 | 下游恢复已提交，但原 run 不再改变 | 原 run 的终结/active 释放及未来调度进展 | 无；原 callback 本身也把 FILLED 映射 RUNNING | **NEW_P1 / STRATEGY_DURABLE_LIFECYCLE_RECOVERY_GAP**，真实进程重启与未来窗口证明失败。不是仅缺最后一次 callback，正常映射也没有成功终结语义。 |
| FAILED（终态对照） | 原结果 callback 或已接受 no-send recovery 提交 | 同窗口不可重新 admission；后续独立窗口按正常契约评估 | 无需 owner 回调；V50身份保留 | 不能将未知成交或缺失 manual work 猜为 FAILED；终态之外仍需保证计划游标可进展。 |

`existsActiveRunByStrategyId` 仅观察 `CREATED/DISPATCHING/RUNNING`，没有时间范围或到期释放；本轮保持它为只读查询。JVM busy set 只覆盖局部重入。当前没有为 CREATED 或明确 FILLED 的 RUNNING 定义可接受的永久手工悬挂契约。

## Order绑定和完整工作事实：为何不能直接恢复

1. `orders.strategy_run_id` 有 FK 和普通索引；唯一约束仅是 `(account_id, client_order_id)`，没有将该 client 与特定 run 的唯一 canonical work 绑定。
2. 隔离 PG16/V50 中，真实 `JdbcStrategyRunRepository.admit` 创建一个 run；两个 Spring 事务通过真实 `JdbcOrdinaryPlaceAuthorityRepository` / V49受限函数，以同 run、不同 client 创建 Order，**两个事务均提交，Order=2/V49=2**。这是数据库约束诊断，未执行 RiskGate/PLACE，不能宣称生产 scan 已发生双 PLACE。
3. 相同 client 的重复 INSERT 被既有 UNIQUE 拒绝，SQLSTATE=`23505`；继续在该事务查询为 `25P02`。与当前 `preparePlaceOrder` 的 catch-duplicate-then-query 不兼容。恢复不能只复用该分支并称 loser 为正常 no-op。
4. 当前 client 为 `coid- + requestId`。只要参与者采用同一 durable requestId，已有 UNIQUE 对这组 client 有效；但它不等于数据库强制同 run 只有一个 command。只读检查、原 JVM局部身份或一次 CREATED CAS 均不提供完整协议。
5. scheduler run 持久化了 strategy/account/env、schedule/dueAt、requestId、config snapshot，但没有独立冻结的 effective dispatch work。manual API 允许传入 symbol/side/orderType/quantity/price；run 保存的是 definition.configSnapshot，实际执行用 request 字段。这两者可以不同，故不能从已有 manual CREATED 推导原始指令。不能覆盖历史 snapshot、读最新配置或猜价格/数量来伪造恢复事实。
6. 新 Order 一旦由原 prepare 正确建立，可以继续使用现有 V49；问题在 run→Order 工作身份和原子性，不需要重新设计 V49 的三态发送权限。

仅增加源行锁可以串行化遵循协议的 writer，也可能成为后续无 DDL 方案的一部分；本轮**不宣称数学上不存在任何无 DDL 方案**。但它本身既不能重建未保存的 manual payload，也不能为其他可达 Order writer 建立不可绕过的同 run 绑定。本轮没有可证明覆盖全部当前入口的 Contract A，因此遵守任务停止条件，不以假设开始 CREATED recovery。

## 最小设计候选（待独立 schema contract review，未实施）

优先保留 Contract A 的同 run 可恢复工作模型，不引入 owner、lease、expiry、generation 或 leader election。下述新增表/guard 需要授权后的 forward-only migration；本轮没有创建 migration 或运行这些 DDL。

### 一个同 run 的 immutable dispatch work

拟新增 `strategy_run_dispatch_work`：

- `strategy_run_id` 为 PK/FK，直接复用 admitted run ID；不是 replacement run。
- 冻结实际 effective 指令：account、canonical venue、SIM/LIVE、symbol、side、type、quantity、price、time-in-force及实际需要的 request/source/trace。保留原 strategy config snapshot 的审计语义，不把它暗改为另一种 payload。
- 冻结 canonical client identity，采用 run 身份派生的明确编码规则；必须证明长度、legacy兼容、跨进程一致性与 `(account_id,client_order_id)` 唯一性。旧已绑定Order始终使用原 client，不改写已发出的请求。
- `order_id` 初始 NULL，绑定后不可清空/替换，UNIQUE并以可延迟 FK 引用原 orders；它只是 work 的单一下一步引用，不是第二 Order事实。
- work 的作用域/指令不可改写，禁止 DELETE/TRUNCATE 释放窗口。新 admission 与其 work 同事务提交；manual insert 也必须同事务持久化实际请求。

### 数据库强制同 run 一个 Order

拟在 orders 的 INSERT/绑定 UPDATE 边界增加受限 guard：对于存在该 work 的 run，锁定对应 work 行，核对 run/account/env/venue/client与全部经济字段，原子填入唯一 `order_id`；已有不同Order绑定时拒绝。禁止从 work run 解绑、改绑或通过普通 INSERT 绕过。所有这些写入与原 Order/V49 prepare 同事务，FK在提交时验证，不改 V49三态或现有迁移字节。

canonical application writer 在同一短事务中先锁 run/work、查询已绑定Order；已完成者正常返回 no-op，不依靠触发器异常处理正常竞争。事务内执行原 RiskGate/prepare，然后提交 `CREATED→DISPATCHING + Order + V49 + work.order_id`；连接失效/回滚时整个本地步骤不成立，可由另一个进程从原 work 重试。DB guard 兜底拒绝违规 writer，不把 catch后查询当正常协议。

必须在设计审查中证明所有 writer 都受 guard 约束、锁顺序一致、并发 admission/Order与隔离级别无可见性漏洞、权限/搜索路径安全。不要直接对全部历史 orders 增加 `UNIQUE(strategy_run_id)`：历史多单或无法重建work的run须先分类，不能自动合并/清洗。

### 不依赖 owner 是否死亡的 progression

| Cut point | 设计中的合法 successor 行为 |
| --- | --- |
| CREATED+work durable，原 A 暂停或死亡 | B/C竞争同一行锁和本地事务；只有一个建立Order。A恢复后读取原绑定，正常no-op；不需要证明A已死亡。 |
| 本地 prepare 事务中断 | 状态、Order、V49、绑定一起回滚；原work仍可重试；不得先提交一个新的无法恢复claim。 |
| Order/V49已提交但未arm | 复用已接受V49的发送/撤销竞争；不创建新Order，不给已决定authority重授许可。 |
| MAY/commit unknown | 不重发；查询/对账及既有人工处理语义。禁止以读回MAY恢复旧发送许可。 |
| Order明确终结 | 独立、有界的 lifecycle writer 推进同run，处理 DISPATCHING/RUNNING 的所有可达终结分支；未知仍阻塞。 |

### 终结语义和计划游标

- 当前没有成功终态；设计需明确新增成功完成语义及API/消费者兼容，而不能把 FILLED 改名为失败。建议 `SUCCEEDED` 只由唯一、作用域匹配的原Order及明确的完整成交事实驱动，记录 finishedAt；取消、风险拒绝、部分成交和迟到成交须分别定义，不能统一猜测。
- writer 对 run/Order/work采取一致锁序、状态 CAS、每批上限与公平扫描；late callback 不能覆盖已收敛终态。当前无Order或多Order的历史run保持可见的未决分类，不自动迁移成可恢复work。
- 原窗口消费与 schedule cursor 的推进必须持久、幂等且单调；不能在失败后永久重复选择已消费dueAt，也不能借删除V50让同窗口重开。以已消费canonical dueAt推进，并明确现有跳过积压窗口的策略及与原 `lastTriggeredAt=now` writer 的并发关系。
- 这是待审设计，不是已证明 A/B/C 的实现，更不是已批准的 V51。

## 实际运行与红色证据

新增永久回归：[B5DurableLifecycleCrashRecoveryTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B5DurableLifecycleCrashRecoveryTest.java)。所有业务事实经真实Spring、RiskGate、普通Order/V49、Synthetic Venue与恢复入口生成；controller只在首个业务JVM启动前提交配置，运行中读取独立reader。新窗口允许自己的合法Order，不把跨窗口总计数当作重复判据。

| 实测场景 | 原run / Order / V49 | PLACE/CANCEL | Trade/TradeExecuted/Ledger | 恢复及未来窗口 |
| --- | --- | --- | --- | --- |
| CREATED admission后杀A，B恢复，再启C | 1 CREATED / 0 / 0 | 0/0 | 0/0/0 | B/C均恢复0；原计划后续秒级窗口及预配置未来计划均BUSY；FAIL |
| RUNNING+ACCEPTED后杀A，Venue fill，B恢复，再启C | 1 RUNNING / 1 FILLED v4 / 1 MAY | 1/0 | 1/1/4 | 下游真实收敛，B/C run恢复仍0；未来窗口BUSY；FAIL |
| schema binding诊断，两事务同run不同client | 1 CREATED / 2 NEW / 2 NOT_ARMED | 未调用venue | 0/0/0 | 两事务均提交，证明缺少强制同run绑定；不是两次生产dispatch的证明 |

环境均为自有loopback/tmpfs的canonical锁定PostgreSQL **16.15 / V50**；真实provider、credentials、LIVE=0。相关JVM、数据库及容器均已清理。原reviewer的日志/JSON仍保留原字节；canonical副本通过既有field-aware exporter生成。

执行命令：

```text
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=B5DurableLifecycleCrashRecoveryTest -Dnq.b5.lifecycle=true -Dsurefire.failIfNoSpecifiedTests=false
```

结果 **2 tests / 2 failures / 0 errors / 0 skips，exit=1**。两个失败都来自实际永久BUSY断言，不是fixture异常；没有将失败断言改为接受orphan。schema诊断driver正常结束只表示缺口复现完成，不表示correctness PASS。

Full Maven=`NOT_RUN / STOP_BEFORE_PRODUCTION_REMEDIATION`。此前1881/0/0/106的Full结果原样保留，但不覆盖本轮新增测试，也不作为本任务完成证明。paused-live-owner、双successor恢复winner、恢复+scan等修复后验证尚未执行；B1–B4未重跑，因为未修改production，不能把未运行项记PASS。

## 根因升级、遗留和下一步

- 第一项相关已确认事件是B5 R10的DISPATCHING无发送终态缺少run writer；随后V50 admission增加了可独立提交的CREATED，旧recovery明确不拥有它。本轮又证明RUNNING即使下游完全收敛也无法释放。
- 共同机制：按已见状态补writer，没有对每个durable nonterminal检查下一项持久工作、暂停旧owner、完整身份、terminal映射和未来工作进展。V49与V50各自拥有authority/admission，不拥有整个StrategyRun lifecycle。
- canonical owner为strategy lifecycle/orchestration和其infra事务/持久work边界；旧no-send recovery应保留并最终整合到完整分类，V49保持原协议，不扩成全项目审计。
- 已在现有engineering-lessons中追加 `Durable State Crash-Window Completeness Rule`；无新增Skill，无AGENTS更改。
- 原CREATED P1=`OPEN`；新RUNNING P1=`OPEN / STRATEGY_DURABLE_LIFECYCLE_RECOVERY_GAP`。P0=0；LOCAL_P1不为0，不授予整改完成身份。
- stage-assets原144项继续 OPEN / DELIVERY_COMPATIBILITY_BLOCKER；本轮不改registry/hash/exception/validator。收尾核对结果见 [checks](l4-b5-lifecycle-remediation-attempt01/checks.json)。
- production/V1–V50/V49/TradingVenue/.github/AGENTS/既有evidence均不修改；本轮仅新增红色回归及诊断证据、更新指定lesson。stage=0，commit=NONE，push=NONE。
- 下一步：对上述最小dispatch-work/Order binding及终结/游标contract做独立schema设计审查；只有设计及migration授权明确后才能继续实施。该停止来自本轮用户附件第6/19/21节，不是Skill新增审批流程。B5继续NOT_QUALIFIED。
