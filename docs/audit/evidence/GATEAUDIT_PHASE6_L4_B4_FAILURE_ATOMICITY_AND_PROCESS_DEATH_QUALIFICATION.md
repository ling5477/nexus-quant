# Phase6 L4 B4：事务失败与进程死亡资格验证

日期：2026-09-09。Task classification：NQ-only / HIGH_RISK / L4_QUALIFICATION / TRANSACTION_FAILURE_PROOF / REAL_PROCESS_FAULT_PROOF。

**FAIL / L4_B4_CORRECTNESS_FINDING / STOP / PRODUCTION_REMEDIATION_REQUIRED**。

B4 未 QUALIFIED、未 READY_FOR_DELIVERY、未 ACCEPTED。发现 P1 后停止其余资格矩阵；没有修改 production 后继续宣称 PASS。

## 基线与范围

- branch=`audit/post-gatey-agent-baseline`；Starting HEAD=`e9509e351e6fbc6179e5e081cb03e66c8cb6ad99`，等于本地 origin 同名引用。开始时 worktree clean、`git diff --check` exit0。
- 用户给定 B0/B1/B2/B3 已接受；B3 当前 head 的 GitHub CI 本轮只读核验为 `34334552967 / completed / success`。B3 证据记录 B1 pair=`d1cedb6debfaa3dbeacb0e95ef8599c69e5f9da3 / 34246407667`、B2 pair=`e927fe107ce5cac4eb828f86b95561d977785b53 / 34327322619`；本轮未重新验收这些历史能力。
- STATUS/ROADMAP 仍写 pre-B0，这是 B3 已记录的文档冲突。沿用本轮用户明确给定的隔离资格验证授权，不修改 current authority，也不据此获得生产运行许可。
- real credential=0、real exchange=0、LIVE enable=0。仅新建 owned loopback PostgreSQL16 tmpfs、V48，真实 Spring/RiskGate/adapter/JDBC，独立 Synthetic Venue JVM；SIM Order。DISENGAGED 仅为 B0 启动前封存的 TEST_PRECONDITION，运行中不修改 Kill。
- stage=0、commit=NONE、push=NONE；production、migration、.github、AGENTS/Skills 均无修改。

## 实际 transaction boundary map

以下先由真实代码追踪得到，再选择 fault。外层 ordinary command 和 reconciliation **没有一个覆盖全部步骤的大事务**。

| 边界 | 当前实际 owner / 顺序 | 原子性合同 |
|---|---|---|
| command event | `OrderCommandService.placeOrder` 先 append PlaceOrderCommand | 位于 prepare 事务之外；不能要求 prepare rollback 同时撤销该命令事实 |
| local prepare begin → commit | `OrderCommandWriteService.preparePlaceOrder`，Spring `@Transactional`，创建 Order、真实 RiskGate、状态与相应审计/事件 | 完成并提交 SENT 后，外层才得到 preparation |
| venue side effect | `OrderCommandService` → `AdapterBackedTradingVenueGateway` → OKX adapter → Synthetic Venue HTTP acceptance | 在 prepare commit 之后、ACK 本地写事务之前；PG rollback 不撤销 venue truth |
| receipt / Order finalize begin → commit | `finalizeAcceptedPlaceOrder`，`@Transactional`：external identity、OCC transition、OrderAck event、ORDER_ACKED audit | 同一 Spring 事务；代理提交成功后才返回 PlaceOrderResult。ordinary 路径不使用 execution_receipts 表伪造 receipt |
| caller receives result | finalize 事务代理返回 → placeOrder 返回 → harness 输出 B0_RESULT | 与实际 PG commit 分离；本轮未注入 commit-response loss |
| reconciliation reservation | `JdbcOrderRepository.reserveReconciliationCandidates`，REQUIRES_NEW / READ_COMMITTED | cursor 独立提交，之后才执行 venue I/O，不把 cursor 和订单业务事实混为一体 |
| query / state alignment | `OkxRestReconcileService` 查询 order/fills、验证唯一 fill 合集，调用 lifecycle/write service | 状态转换与 ORDER_STATUS_TRANSITION audit 同事务；普通 transition 不凭空要求另一个不存在的状态事件 |
| Trade begin → commit | `JdbcTradeRepository.insert`，`@Transactional`，锁定 Order、核对累计数量、INSERT trades | 真实 Trade 事务提交；不包含下一行 TradeExecuted 或后续 Ledger |
| **B4 fault cut** | **TradeRepository 事务代理已返回，caller 尚未执行 publishTradeEvent** | **无活跃事务；独立 reader 可见 Trade=1。父进程此时强杀 NQ** |
| Trade event | `OkxRestReconcileService.publishTradeEvent` → `EventStoreAppender.append` | 单独 JDBC 写 event_store；在 Trade commit 后，当前没有同事务绑定 |
| Ledger begin → commit | `TradeLedgerPostingService.postTrade`，`@Transactional` | 每笔 fill 的全部本金/费用分录、ledger_events、position、account snapshots、LedgerPosted/PositionUpdated、LEDGER_POSTED audit 同事务 |
| recovery | existingTrade 分支 → ensureLedgerConvergence → canonical postTrade → OKX_FILL_DEDUP_HIT | 补 Ledger，随后 continue；当前分支不补 TradeExecuted |

代码定位：`OrderCommandService.java:114-136`；`OrderCommandWriteService.java:269`、`:582`、`:667`；`JdbcTradeRepository.java:105`；`OkxRestReconcileService.java:423-467`、`:656`；`TradeLedgerPostingService.java:77`；`EventStoreAppender.java:57`。文件均在 backend 相应 main 模块中；本轮没有修改。

Trade durable / Ledger 尚无记录在这里是允许的恢复窗口，不能把它误报为同事务半提交。**实际缺陷是恢复完成后，TradeExecuted 仍永久缺失**，违反 canonical 关键事件事实链完整性；不是要求把所有阶段强行合并成一个事务。

## P1：Trade commit 后 process death 丢失 canonical TradeExecuted

最小复现：普通 PLACE → venue 单笔 fill10 / fee0.01 → ordinary RECOVER → 真实 Trade INSERT 事务提交 → 在 publishTradeEvent 前阻塞 → 父进程 destroyForcibly NQ A 并确认退出 → NQ B 新 PID、同 PG、同 Venue truth → 两次 ordinary RECOVER。

注入机制：test-only `B4ProcessFaults` 在已存在的真实 `Advised` TradeRepository 代理链最外侧添加一次性 interceptor。它先 `invocation.proceed()` 执行全部实际仓储与事务，再确认 `transactionActive=false` 并输出屏障标记。没有 mock repository、假异常、替换事务管理器或 SQL 写业务表。Controller 的独立 `nq_b0_reader` 在强杀前直接查到 committed Trade。屏障最多60秒，父进程不到位则 HARNESS_CONTROL_GAP；实际均由父进程强杀。

当前恢复对 existing Trade 调用 Ledger recovery 后直接 continue；缺失的 TradeExecuted 没有补写路径。对照场景使用相同生产路径与同一查询 oracle，但不启用屏障，在完整第一次 RECOVER 后再重启，事件存在且不重复。

| 最小确认 attempt02 | NQ A | Venue | NQ B | Order/version | Trade | Ledger entries/events | TradeExecuted |
|---|---:|---:|---:|---|---:|---|---:|
| 无故障对照 | 60780 | 34500 | 14940 | FILLED/4 | 1 | 4/4 | 1 |
| 提交后/事件前强杀 | 53164 | 3028 | 41596 | FILLED/4 | 1 | 4/4 | **0** |

DB identities 分别为 `SYNTH-L4:B4-CTRL:R01:DATABASE:001`、`SYNTH-L4:B4:R01:DATABASE:001`。PG=`16.15 (Debian 16.15-1.pgdg13+2)`，schema V48；canonical pinned postgres16 image、pull=never。原始 DB 名称与进程日志仅保留 target。

- 故障 cut：Trade=1、Ledger=0、TradeExecuted=0；reader 直接证明 COMMITTED，不依赖 exception 猜测。
- 两行最终 venue accepted PLACE=1、placeRequests=1、blind retry=0、CANCEL=0；venue truth filled10，fee0.01。
- 两行均由 B2 oracle 逐 fill 检查 price/qty/fee、本金与费用 DEBIT/CREDIT、幂等键、USDT净分录=0、position10、ledger_entries/events 配对。该成对模型不等同真实钱包结算。
- 两次恢复后完整 B2 business snapshot 相等，Order/version=FILLED/4、Trade=1、Ledger=4/4；无重复 accounting 或状态回退。
- Order transition audit、ORDER_ACKED、LEDGER_POSTED、LedgerPosted、PositionUpdated 存在。故障行另有 OKX_LEDGER_RECOVERY_COMPLETED，但没有任何 trade.event.v1 / TradeExecuted；完整 event_store 快照同样证明零条，而非只看单个计数查询。
- 最终移除每个 disposable DB；全部 child 已退出，两个 owned PG 容器各自清理 remaining0。

完整可复核快照：[无故障对照](l4-b4-failure-atomicity-attempt02/control.json)、[故障与恢复](l4-b4-failure-atomicity-attempt02/failure.json)、[身份导出验证](l4-b4-failure-atomicity-attempt02/export-verification.json)。快照包含 atCut / beforeRestart、afterRecovery、afterReplay、完整 Order/Trade/Ledger/Audit/Event、venue events、PID 与 DB canonical references。

## 执行与停止边界

```powershell
mvn -o -f backend/pom.xml -pl nq-app -am '-Dnq.b4=true' '-Dtest=B4RealProcessProofTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

最终 attempt02：JUnit1，failure1、error0、skip0，Maven exit1，53.342秒。单一测试先执行正常对照（通过），再执行故障（预期业务不变量断言失败，expected1 / actual0）。这是保留正确预期的红色最小复现，不是绿色 expected-failure qualification。

attempt01：B0FixtureSafetyTest 8/8 PASS；B4 1 FAILURE，Maven总计9、failure1、error0、skip0，exit1、33.305秒。初版事件计数误用了 JSON `eventType` 字段；完整 event_store 快照另行确认缺失。修正为真实 `event_type` 列后，仅补正常对照和一次同截点最小确认，得到上表结果。初版错误不隐瞒，不将其计数断言作为最终结论依据；其 NQ A/Venue/NQ B 为40996/20704/62916，资源亦已清理。raw logs 为 target/b4-targeted-attempt01.log、target/b4-minimal-confirmation-attempt02.log。

Runs：3次独立 DB/Venue/NQ 执行（attempt01故障1；attempt02正常对照1、故障1）。**没有满足每个 canonical B4 row 的3次资格运行**；P1立即停止规则优先，后续不补跑矩阵来凑数。最小确认用于排除 oracle 错误，不构成继续 qualification。

| 要求 | 本轮状态 |
|---|---|
| Rollback | NOT_RUN |
| Commit rejection / definitive failure | NOT_RUN；不宣称已证明 |
| Commit-response lost / ambiguous outcome | NOT_RUN；本例提交结果已被 driver/事务代理正常接收，不冒充 response loss |
| Connection loss pre-commit / ambiguous | NOT_RUN |
| Process death A：venue之后、完整本地提交前 | NOT_RUN 为独立资格行 |
| Process death B/C：local Trade commit后、reconciliation完成前 | 最小复现发现 P1，FAIL |
| C1/C2/B1/B2/B3 regressions | 停止后不重新运行历史资格矩阵。仅复用B2 oracle证明本次唯一账务与稳定 replay；本例为FILLED候选fill backfill，不冒充B1 SENT query-first、C1 stale ACK、C2 starvation或B3 Kill回归 |

Full Maven=NOT_RUN（production unchanged）；本轮 delivery CI=NOT_RUN。测试代码修改后未发布，不以起始HEAD的成功CI覆盖本轮。

## Recurring-problem check

完整 event/audit 快照首次导出被 raw identity 零泄漏检查拒绝，尚未写入 tracked evidence。已按统一 engineering lesson：REPRODUCE → TRACE HISTORY → ROOT CAUSE → CANONICAL FIX → REGRESSION。

- 历史：`e0d4a027` 精确 synthetic allowlist 后再发生 B2 CI `34318927962`；`e927fe10` 将 common exporter 改为 typed references。最早已确认记录与原因沿用 engineering-lessons，不断言更早历史。
- 此次原因：之前完整 B2 oracle 没有导出全部 command/audit；共用映射仅解析 Ledger 的 trade:LEDGER key，actor_id 仅认 Order。新增完整快照揭示既有 account:client 和 Ledger audit Trade 引用遗漏。
- canonical owner：`synthetic_evidence.py`。补齐有已登记 client 的 numeric-account:client 解析，以及 actor_id 的 Trade 引用。不做自由文本替换、不改 runtime identity、不加B4分支/allowlist、不动秘密子树或未知字段。
- 新永久回归覆盖真实 command key、Trade与Order audit、相邻Ledger复合key、unknown与credential保留；原48行B2完整随机身份回归保持。Python共5/5 PASS。
- 两个B4完整树导出：typed identities=22/21，双射与完整逆映射相等、raw UUID/client identity泄漏=0。原始JSON不变。
- 历史allowlist KEEP TEMPORARILY，退出条件仍沿用原工程经验；本轮不移除历史例外、不宣称系统所有未来格式均已覆盖。工程经验引用现有 canonical lesson，AGENTS/Skills 修改=0。
- stage-asset hash drift 未触发，未改 bound asset；evidence formatting 无历史改写。

实际检查：pinned Gitleaks8.18.4 archive SHA 与当前 lock 一致、当前CI原样配置；6/6 secret negatives REJECT，tracked safe-file扫描0 findings，新增B4主文档与三个JSON另行完整扫描exit0 / findings0。首次WSL调用使用Linux git不能解析Windows worktree路径，在负例通过后、文件枚举前退出；改用脚本已有`--git`参数指定Windows git，最终完整执行成功。未修改git/worktree元数据、扫描脚本或配置。`check-stage-assets.py`：scanned1822、reviewed_exceptions173、errors0；evidence三条附件链接有效，tracked diff与全部新文件空白检查通过。最后仅整理新Java测试缩进，未改变已验证执行逻辑。

## 结论与下一步

Files changed：B0NqProcessMain（显式test-only屏障命令）；B4ProcessFaults、B4RealProcessProofTest（最小故障复现）；共用synthetic_evidence.py与test_synthetic_evidence.py（导出根因修复）；本 evidence 与 canonical JSON附件。

P0=0（本轮未发现）；**P1=1，OPEN：TRADE_COMMIT_PROCESS_DEATH_LOSES_TRADE_EXECUTED**。未测试的矩阵不据此声明无缺陷。

Review：SELF_REVIEWED test/harness/evidence；未新增 independent review，未修 production。Production files changed=0；Migration=0；CI=NOT_RUN。

Final decision：**FAIL / L4_B4_CORRECTNESS_FINDING / STOP / PRODUCTION_REMEDIATION_REQUIRED**。

Commit recommendation：不进入 B4 PRECISE-DELIVERY。保留红色最小复现和失败证据；本轮不stage/commit/push。

Next action：单独授权 production correctness remediation，确定 Trade/TradeExecuted 的原子提交或可恢复幂等事件合同，保留 crash window 证明并按该任务要求进行一次真正独立正确性审查；修复接受后再恢复 B4 剩余矩阵。当前任务明确禁止生产修改，因此本轮到此停止。


---

## 2026-09-09 qualification resume：QUALIFIED / READY_FOR_DELIVERY

本节追加当前恢复结果；前文首次P1 FAIL、旧transaction map与停止结论完整保留为历史，未用本次结果改写。Task=`NQ-GATEAUDIT-PHASE6-L4-B4-QUALIFICATION-RESUME`；HIGH_RISK / L4_QUALIFICATION / REAL_PROCESS_FAILURE_ATOMICITY / NQ-only。

**PASS / L4_B4_QUALIFICATION_COMPLETE / READY_FOR_PRECISE_DELIVERY**。B4=`QUALIFIED / READY_FOR_DELIVERY`，**尚非 ACCEPTED**。Production unchanged；stage=0、commit=NONE、push=NONE；本轮没有Full Maven、第二轮Independent Review或CI。

### Reviewed baseline 与 evidence reuse

- branch=`audit/post-gatey-agent-baseline`；Starting HEAD与本地origin同名引用均=`e9509e351e6fbc6179e5e081cb03e66c8cb6ad99`。未fetch/push，不将本地origin引用核对表述为新一次remote CI检查。
- 独立review完整candidate指纹=`1af151b4a4e33bb05d96cf7065c9270f692ea52120959223efa8cceb07f1307c`。review start/end相等，119 targeted / failure0 / error0 / skip0；Full Maven实际1839 / failure0 / error0 / conditional skips90，均已接受。
- Reviewed production fingerprint=`a681af6653dd387491012e5eea164f64f89ee2dba74fe7bcef5a1e2992993e6d`：五个production路径→SHA-256清单按键排序的紧凑JSON再取SHA-256。该清单与review冻结的逐文件字节完全相同；完整src/main、.github、.agents与AGENTS共1328受保护文件亦原样。此production指纹与完整candidate指纹为不同口径，不互相替代。
- [review身份、production清单与原测试结果](l4-b4-qualification-resume-attempt01/review-provenance.json)绑定接受事实；不重新运行TradeExecuted review。Trade+required TradeExecuted原子提交、历史缺口恢复、Ledger独立恢复、Durable Source Fan-out整改接受状态不重开。
- 原review实际执行的PG deferred event commit rejection（Trade=0/Event=0）、真实source锁竞争、历史随机ID兼容、两向Ledger恢复直接复用。由于它们不都覆盖venue side effect，本轮另补ACK/Trade真实commit rejection行，而非只靠PG fixture宣称外部成功后收敛。
- 原review的[SIM Trade/Event提交后死亡](l4-b4-qualification-resume-attempt01/review-SIM-ATOMIC_DEATH.json)及[LIVE同截点](l4-b4-qualification-resume-attempt01/review-LIVE-ATOMIC_DEATH.json)直接复用：Trade1/Event1/Ledger0→新PID恢复Ledger4→再次新PID/replay稳定。两份均来自本会话前一轮独立运行，重新导出时验证完整逆映射相等，没有重跑凑次数。

### 当前eligible rows与新增故障覆盖

原plan JSON的`currentEligibility`只有ESDB-00/05/06/07属于CURRENT_CANONICAL_PROFILE。本轮不激活ESDB-01..04的RETIRED_COMPATIBILITY_PROFILE，不制造ExecutionIntent/Receipt或复活旧sender。按本次用户要求，在当前ordinary路径额外证明相应ACK commit rejection/response loss/death机制；这不宣称已退休typed行通过。

| Canonical row | 本轮对应证明 | 结果 |
|---|---|---|
| ESDB-00 | PREPARE_CONNECTION_LOSS：真实prepare全部SQL后、COMMIT Execute转发前断连；Order0；新PID canonical ENGAGE；同一client重放被RISK_REJECTED，distinct command client=1、venue PLACE0。ROLLBACK/REJECT两组另作同一owner支持证明 | SIM/LIVE PASS |
| ESDB-05 | CANCELLED_TRADE_DEATH：venue部分fill4/fee0.01并撤单，CANCELLED/version5已durable；Trade/Event未提交时强杀→同PG/Venue新PID恢复Trade1/Event1/Ledger4，终态/version不变 | SIM/LIVE PASS |
| ESDB-06 | LEDGER_CONNECTION_LOSS：Trade/Event先提交，Ledger事务真实执行全部SQL，COMMIT前物理断连→Ledger/ledger_events/position/snapshot均无部分提交→新PID恢复 | SIM/LIVE PASS |
| ESDB-07 | LEDGER_DEATH_BEFORE_COMMIT：Ledger SQL已执行，COMMIT尚未转发，强杀仍运行的NQ→数据库abort→新PID收敛，另一事务Trade/Event保留 | SIM/LIVE PASS |

以下每行有SIM/LIVE两个独立DB/Venue/NQ执行。完整逐行cut、实际提交结果、PID、DB identity、venue events、Order/version、Trade/Event/Ledger、afterRecovery/afterReplay见[28行索引](l4-b4-qualification-resume-attempt01/summary.json)及其同目录JSON。

| Row | 目标事务/故障 | 独立reader结果与恢复 | SIM / LIVE |
|---|---|---|---|
| HEALTHY | 无故障、真实wire正常转发 | FILLED4 / Trade1 / Event1 / Ledger4；重启稳定 | PASS / PASS |
| PREPARE_ROLLBACK | prepare业务SQL后抛测试故障，由真实Spring/PG执行ROLLBACK | 该事务Order/Risk/OrderEvent等均0；事务外PlaceOrderCommand可保留 | PASS / PASS |
| PREPARE_REJECT | prepare的deferred trigger在COMMIT实际拒绝 | COMMIT_DEFINITIVELY_FAILED；Order0/Trade0/Event0/Ledger0；Venue0 | PASS / PASS |
| PREPARE_CONNECTION_LOSS | prepare的COMMIT Execute尚未转发时关闭实际socket | 后端连接退出，事务abort；同client在Kill下重放RISK_REJECTED；Venue0 | PASS / PASS |
| ACK_REJECT | venue accepted后，ACK/finalize的COMMIT真实拒绝 | SENT2、ACK0→query-first→FILLED3，Trade/Event唯一、Ledger4 | PASS / PASS |
| TRADE_REJECT | venue已成交、Order FILLED4后，Trade/Event COMMIT真实拒绝 | Trade0/Event0/Ledger0→ordinary recovery三者1/1/4；无再PLACE | PASS / PASS |
| ACK_RESPONSE_LOSS | PG已发COMMIT成功帧，代理不交给应用并断连 | DB ACCEPTED3/ACK1，app ambiguous→新PID查询→FILLED4；无重复ACK/version | PASS / PASS |
| TRADE_RESPONSE_LOSS | Trade/Event的COMMIT成功帧被截留并断连 | DB Trade1/Event1、Ledger0；恢复后原Trade/Event完整快照不变，Ledger4 | PASS / PASS |
| LEDGER_RESPONSE_LOSS | Ledger的COMMIT成功帧被截留并断连 | DB Ledger4、事件/position/snapshots已提交；恢复幂等，原完整账务快照不变 | PASS / PASS |
| LEDGER_CONNECTION_LOSS | COMMIT前断开Ledger连接 | Trade/Event1/1保持，Ledger0/投影0→恢复Ledger4 | PASS / PASS |
| ACK_DEATH_BEFORE_COMMIT | venue已接受，ACK COMMIT Execute暂停，强杀NQ A | SENT2/ACK0→新PID→FILLED3，PLACE1 | PASS / PASS |
| LEDGER_DEATH_BEFORE_COMMIT | Ledger写入后COMMIT Execute暂停，强杀NQ A | 原Ledger事务整体abort，Trade/Event保持；新PID完成Ledger | PASS / PASS |
| ACK_DEATH_AFTER_COMMIT | PG COMMIT已成功，响应帧暂停交付；强杀仍运行的NQ A | DB ACCEPTED3，应用尚无完成结果；新PID→FILLED4 | PASS / PASS |
| CANCELLED_TRADE_DEATH | 已取消订单的Trade/Event提交前强杀 | CANCELLED5 / fill4 / Trade1 / Event1 / Ledger4，replay不重复 | PASS / PASS |

### 故障控制、真实结果与安全边界

`B4TransactionFaults`追加在真实Spring TransactionInterceptor内侧，先执行原业务方法；确认transactionActive后只设置一次session-local标记或触发显式测试rollback。不替换repository/adapter/RiskGate/transaction manager，不改变生产算法。`B4PgWireProxyMain`是独立PID、仅面向已验证owned loopback PG的有界协议代理；限定连接数、线程数、frame大小、statement/portal映射和pause期限，无通用网络chaos或生产入口。

- REJECT：仅在新建fixture启动前安装DEFERRABLE INITIALLY DEFERRED constraint trigger；session-local标记只属于本次目标事务，PG COMMIT触发真实SQLSTATE40001，独立reader核对无目标durable事实。不会把任意SQLException都判定为明确commit失败。
- BEFORE_DROP/PAUSE：识别Parse/Bind/Execute关联的准确COMMIT，**Execute未转发**，然后物理断连或等待父进程强杀。连接EOF后PG abort，reader另行确认数据库实际结果。
- AFTER_DROP/PAUSE：COMMIT已转发；上游实际`CommandComplete(COMMIT)`到代理后，**该成功帧及后续ReadyForQuery没有交付应用**，再断连或等待父进程强杀。应用侧=`COMMIT_OUTCOME_AMBIGUOUS`；独立数据库事实=`COMMITTED`。不是调用真实commit正常返回后再伪造SQLException。
- 每个新成功行有独立Venue/NQ A/NQ B/Proxy/controller PID；NQ B连接同一PG、同一Venue事实，真实ordinary recovery。pause行先确认A仍活着再强杀并wait。proxy不会控制reader连接；无SQL删除/补写event或业务表修库。
- 真实Ledger仍独立于Trade/Event事务；明确允许Trade/Event已durable、Ledger未完成。恢复后的Trade/Event与已提交Ledger IDs、金额和快照不重写。Ledger按现有成对本金/费用模型验证，不冒充新的钱包账务模型。
- 新PID先canonical ENGAGE，Kill=ENGAGED下恢复与3次replay通过；mutation探针被RISK_REJECTED且Venue计数不增长。ESDB-00补验重放原client；其他有已接受订单的行使用新client探针，避免以原订单的幂等读取冒充拒绝新mutation。
- afterCut后的Venue事件按真实顺序验证：ACK不确定行先QUERY_ORDER再QUERY_FILLS；终态回填先QUERY_FILLS；无新的PLACE_REQUEST_RECEIVED。PLACE最多1；仅取消场景CANCEL1，其余CANCEL0。每个成交行Trade1/Event1/Ledger4，逐fill金额/fee/方向/幂等键与Venue truth一致。
- SIM/LIVE均取durable Order/Trade，不靠proxy或profile推断。本轮LIVE仅synthetic fixture事实语义；real credential/provider/exchange=0，LIVE enable=0。Kill DISENGAGED仍只来自启动前sealed TEST_PRECONDITION，没有运行期解除。

### 执行记录与harness失败历史

| Attempt | 实际结果 | 如何处理 |
|---|---|---|
| 01 wire preflight | B0FixtureSafety8 PASS；B4 1 FAILURE，尚未成功注入故障 | 初版只在SQL文本出现时识别COMMIT，漏掉PG JDBC复用prepared statement的Execute；NQ正常收到ACCEPTED，按HARNESS_CONTROL_GAP停止。只修test proxy的Parse→Bind→Execute跟踪，未报production缺陷 |
| 02 wire preflight | 1 JUnit PASS承载SIM/LIVE ACK_RESPONSE_LOSS两行，Maven exit0 | 真实服务器COMMIT成功帧被截留；这两行后续直接复用 |
| 03 remaining matrix | B0FixtureSafety8 PASS；SIM HEALTHY/PREPARE_ROLLBACK/PREPARE_REJECT三行PASS，ACK_REJECT的最终version断言失败，Maven exit1 | test错误强制FILLED4；实际ACK已回滚，SENT2直接query收敛FILLED3是canonical正确行为。按实际cut version + 合法状态迁移计数修正oracle；无production改动。该失败不作为qualification PASS |
| 04 remaining matrix | 1 JUnit PASS承载21独立行，Maven exit0 | 复用02的两行及03中不依赖错误断言的三行，不机械重跑；所有新行通过 |
| 05 ESDB-00 equivalence | 1 JUnit PASS承载SIM/LIVE prepare connection-loss同client重放两行，Maven exit0 | 补足原canonical ESDB-00具体合同，不用新client拒绝探针替代同client恢复语义 |

合计**28个新的成功场景 + 2个复用的已审查process-death场景**。本轮另有2个明确harness/oracle失败执行，全部保留；不是production失败或expected-failure绿色测试。场景承载JUnit数与场景数分别记录，不冒充28个JUnit。各次精确命令、退出码、耗时、原始proof/log在ignored `backend/nq-app/target/b4-qualification-resume/`；[test source SHA清单](l4-b4-qualification-resume-attempt01/test-source.json)固定最终源码。测试selector只为复用已完成的独立行，默认完整入口运行全部14行×SIM/LIVE，零匹配会失败。

永久入口：`mvn -o -f backend/pom.xml -pl nq-app -am -Dnq.b4.resume=true -Dtest=B4QualificationResumeTest,B0FixtureSafetyTest -Dsurefire.failIfNoSpecifiedTests=false test`。本轮使用精确row/exclude selector分段执行上述矩阵，没有随后重复整套矩阵。B1/B2/B3只复用已接受119 targeted中的相关correctness证明，并在本次fault后实际验证query-first、unique fill/per-fill accounting、Order/version、Kill恢复/新mutation拒绝；没有重跑历史full matrices。

### Recurring-problem check、验证与收尾

Durable fan-out missing derived fact未再次出现，没有新production P0/P1，也没有重新声称前次整改失败。上述两个新harness问题分别为协议执行定位和合法version路径oracle，不属于再次补事件/账务缺口。未新增batch-specific exporter/Gitleaks例外或绕过。

common `synthetic_evidence.py`原样复用，Python5/5 PASS。28行完整payload/envelope、query-first、version/replay核对通过；typed identity映射双射、逆映射等于完整raw树、raw runtime identity泄漏0。复用review两行同样独立导出核验。raw UUID/client/DB identity只留target，tracked文件使用既有canonical references。新harness SQL只用于PG故障控制，不更新业务事实；AGENTS/Skills、Flyway、.github、production均未修改。

[验证汇总](l4-b4-qualification-resume-attempt01/validation.json)：1328 protected files byte-for-byte unchanged；5个本轮owned PG容器均已移除；119个child PID均有强杀wait或异常退出wait记录，未遗留NQ/Venue/Proxy进程。原始FAIL文档13818字节作为本节前缀完整保留。V48 byte hash继续为`5147c5b6dddc8b6bd05ce9f10620b2a2f9c9bd1b338d120d1df8da57c983b742`。

P0=0、P1=0（本轮生产正确性范围）。Limitations：仅当前ordinary eligible B4及用户指定新增机制；不扩张retired typed/intent能力、不执行真实LIVE、不宣称exact-head CI或B4 ACCEPTED。当前STATUS/ROADMAP旧阶段声明仍不由本轮修改。

Final decision：**PASS / L4_B4_QUALIFICATION_COMPLETE / ROLLBACK_PROVEN / COMMIT_REJECTION_PROVEN / COMMIT_RESPONSE_LOSS_PROVEN / CONNECTION_LOSS_PROVEN / PROCESS_DEATH_RECOVERY_PROVEN / DURABLE_FANOUT_RECOVERY_PROVEN / NO_BLIND_RETRY / NO_DUPLICATE_ACCOUNTING / P0_0 / P1_0 / READY_FOR_PRECISE_DELIVERY**。

Commit recommendation：可进入下一项单独授权的精确交付；本轮stage=0、commit=NONE、push=NONE。Production unchanged且P0/P1=0，按本轮合同不再开启第二轮Independent Review。

Next action：`NQ-GATEAUDIT-PHASE6-L4-B4-PRECISE-DELIVERY`。
