# B5 Strategy Run Owner Death Recovery — Remediation Attempt01

**IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW / B5_STRATEGY_RUN_OWNER_DEATH_RECOVERY_IMPLEMENTED / STRATEGY_DISPATCH_OWNER_DEATH_PERMANENT_BUSY_P1_REMEDIATED / DURABLE_INTERMEDIATE_STATE_RECOVERY_RULE_CAPTURED / V49_FAIL_CLOSED_SEMANTICS_PRESERVED / P0_0 / LOCAL_P1_0**。

这是本轮实现及本地验证结论，尚未独立接受。B5 仍为 NOT_QUALIFIED；本轮未继续 qualification，未 stage/commit/push，也未修改 current authority。

## 根因、历史和实际 lifecycle

原始 FAIL 保留在 [qualification resume / R10](l4-b5-qualification-resume-attempt01/R10.json) 与[原报告](GATEAUDIT_PHASE6_L4_B5_DUPLICATE_COMMAND_SCHEDULER_LOCK_MULTIPROCESS_OWNERSHIP_QUALIFICATION.md)。`B5StrategyOwnerDeathTest` 原正确行为断言及原始失败证据均未改写。

实际 canonical owner 是 strategy application 的 `StrategyManualTriggerService`，持久化端口/实现为 `StrategyRunRepository / JdbcStrategyRunRepository`。当前 Java enum 只有 `CREATED / DISPATCHING / RUNNING / FAILED`；历史 migration 注释中的 SUCCEEDED 不是当前可用状态。

```text
StrategyScheduleScanService.scanOnce
→ enabled / due / window / JVM busy guards
→ active-run query / request-window dedup
→ StrategyManualTriggerService.trigger
→ INSERT CREATED
→ UPDATE DISPATCHING
→ StrategyExecutionGateway / OrderCommandStrategyExecutionGateway
→ ordinary Order prepare commit → V49 arm → venue call → Order outcome
→ 原 JVM 返回后的 UPDATE RUNNING 或 FAILED
→ schedule.lastTriggeredAt 更新
```

既有合法 writer 的状态映射：`CREATED → DISPATCHING`；当返回的 Order 为 `SENT / ACCEPTED / PARTIALLY_FILLED / FILLED` 时 `DISPATCHING → RUNNING`，其他返回状态为 `DISPATCHING → FAILED`，记录 `finished_at / order_status=<status>`。不存在当前 `RUNNING → SUCCEEDED` 的 writer。本轮沿用 CANCELLED 对应 FAILED 的真实契约。

真实 active predicate 是同一 `strategy_id` 下 `status IN ('CREATED','DISPATCHING','RUNNING')`。两个 ConcurrentHashMap busy set 只覆盖当前 JVM 的 schedule/strategy 重入，finally 释放；进程死亡后自然消失。它们不承担 PostgreSQL 生命周期正确性。

旧 restart/recovery writers 包括普通 OKX startup recovery/reconcile、Order OCC、Trade/Event/Ledger 的持久恢复；全量 strategy writer 检索确认它们均不更新 strategy_runs。原 R10 在 prepare commit 后、V49 arm 前杀死 owner：独立 ordinary recovery 已写 `REVOKED_BEFORE_SEND + CANCELLED/v4`，但原 JVM 的最后一次 callback 永远不会执行。此前 B4 修的是 Trade fan-out，V49 修的是 external mutation finality；均不覆盖 strategy lifecycle owner。共同故障机制是 **durable intermediate fact survives process death, but no durable recovery owner exists**。

## Durable binding 与资格矩阵

当前已有外键链：`strategy_runs.strategy_run_id ← orders.strategy_run_id`（V1/V5）；`orders.order_id ← ordinary_place_authorities.order_id`（V49）；Trade 通过 `order_id` 关联 Order。`StrategyExecutionIntent.strategyRunId` 经 gateway、PlaceOrderRequest、OrderRecord 和 V49 create function 实际写入 Order。本轮不通过 request/client 字符串、日志、时间戳或“最近订单”猜测下游绑定，不新增 migration。

| Durable facts | 本轮处理 |
| --- | --- |
| DISPATCHING + 唯一绑定 ordinary OKX Order + 同账户/环境 + REVOKED_BEFORE_SEND + CANCELLED/ORDER_NOT_FOUND/OKX_51603 + 无 Trade | 恢复为现有 FAILED；原 run/request/Order/V49 身份不变 |
| MAY_HAVE_ESCAPED + venue unresolved | 不恢复；ordinary query/reconcile 继续负责，scan 保持 active 阻塞，不授予第二 PLACE |
| NOT_ARMED + SENT，正常 owner 存活 | 不恢复；另一个 JVM 看见 DISPATCHING 不是资格事实 |
| 无绑定、缺 authority、多单、账户/环境不匹配 | 不猜测完整性，不恢复 |
| CREATED / RUNNING / 已 FAILED | 不属于当前 DISPATCHING 恢复路径；不推进历史其他状态 |
| FILLED / 明确完成的 execution | 当前 trigger 将 FILLED 映射为仍 active 的 RUNNING，没有 canonical success/non-active outcome；Case C 的“已成功完成 run”不在本轮设计内，不强造终态。PG 负例确认不会误标 FAILED |

## 恢复事务与门禁组合

新增 `StrategyRunRecoveryService` 和独立 recovery repository，scan 在 active 查询前调用；boolean predicate 保持只读。infra 方法使用 Spring `REQUIRES_NEW / timeout=5s`，只执行 PostgreSQL 短事务，无 HTTP。先按 durable eligibility 过滤，再 `LIMIT 50 / FOR UPDATE OF r SKIP LOCKED`；同一 SQL 的 UPDATE 再核对 `DISPATCHING`。V49 撤销及无发送终态是已接受的不可逆事实，恢复只读取，不 reset/re-arm。查询使用既有 strategy/order 关联及索引，不以 age/lease/PID 为依据。

普通 lifecycle UPDATE 同时加入当前状态 CAS：只有 CREATED 可进入 DISPATCHING，只有 DISPATCHING 可进入 RUNNING/FAILED。终态后的重复完成与迟到 callback 返回 no-op，不重写 finished_at/error_message。多 JVM 中一个事务迁移，另一个跳过锁定行或观察已完成状态；崩溃前 rollback 可重试，commit 后响应丢失也可重复进入同一路径。

恢复不创建新 run/order，不更新 schedule.lastTriggeredAt，不改变 request/window identity。原 owner 死亡时，该已消费窗口继续命中现有 `dedup_hit`，其 run 已不 active；原 owner 存活且迟到返回时，由原正常路径推进 lastTriggeredAt，下一次为 `not_due`。本轮没有加入跳过已消费窗口/追赶后续窗口的游标规则，不把“active lifecycle 已恢复”扩大为整个 schedule 可用性模型或剩余 B5 qualification 已通过。

## 永久回归与证据

目标日志、运行计数见 [test-runs](l4-b5-strategy-run-recovery-attempt01/test-runs.json)，原始路径、SHA-256 和逐场景证据见 [proof-index](l4-b5-strategy-run-recovery-attempt01/proof-index.json)。17 份完整 canonical proof 已逐份验证逆映射等价；另有2份旧 B1 harness 原始 proof 以路径和 SHA-256 引用，未将其原始身份复制到 tracked evidence。本轮新进程 proof 使用真实 Spring/RiskGate、独立 JVM、同一自有 PostgreSQL16/V49 和 Synthetic Venue。controller 仅在 NQ 启动前构造 fixture；运行期间只安排屏障、kill、wire 故障并只读业务事实。测试观察表 `b5_run_transitions` 与业务 UPDATE 同事务记录真实状态变化，不参与恢复决策。原 R10 测试保持不变。

| 场景 | 必须证明 |
| --- | --- |
| 原 R10 | 原 owner arm 前死亡；ordinary recovery CANCELLED/v4/REVOKED；B/C 不再 permanent BUSY；原 PLACE=0 |
| RACE | B/C 并发真实恢复，返回值恰为 1/0；一条 FAILED transition；再次启动新 JVM 后稳定；一个 run/order |
| MAY | arm 后未发送即 kill owner，事实仍 MAY/SENT；B/C/新 JVM 均 active block，PLACE=0 |
| ACTIVE | owner 正常在 prepare 后暂停；其他 JVM 不回收；原 owner 释放后正常一次 PLACE / RUNNING |
| LATE | owner 存活但已被 ordinary recovery 撤销；run 恢复后释放旧调用，原 PLACE=0、CAS 不再推进生命周期 |
| BEFORE_DROP | wire 在 COMMIT 发送前断开；caller scan FAILED，不 dispatch；独立 DB 仍 DISPATCHING，C 恢复一次 |
| AFTER_DROP | wire 已观察真实 COMMIT 成功后丢失回复；caller scan FAILED，不 dispatch；独立 DB 已 FAILED，C replay no-op |
| PG eligibility / rollback / limits | 51 条合格行按 50+1 恢复；60 条未决前缀和7个负例不误收；Spring rollback 不提交任何状态/观察记录；迟到 CAS no-op |

首次目标运行 `target-01` 原样保留：原 R10、基础 strategy 测试、RACE/MAY/ACTIVE 通过；新增 LATE 场景错误期待 dedup_hit，而存活原 owner 已正常推进计划游标，实际正确结果为 not_due。该失败为本轮测试契约断言错误，没有生产回归；修改为与正常调度语义一致的断言，并以预先到期的年度窗口消除分钟边界干扰。没有削弱 run/order/PLACE 计数、状态唯一性、原 R10 或未决保护断言。

工程经验已追加到现有 [engineering-lessons.md](../../../.agents/skills/nq-trading-correctness-proof/references/engineering-lessons.md) 的 Durable Intermediate State Recovery Rule；没有新增 Skill/治理体系。canonical owner、原失败、相邻变体、正常控制、debugging sequence 和验证入口均已记录。方法捕获不等于独立审查通过或系统性接受。

## 验收边界

- 本轮仅本地实现及验证，独立 correctness review 在后续专门任务进行。
- B5 保持 NOT_QUALIFIED，未执行剩余 qualification。
- 已记录 concurrent INSERT loser 的 P2 保持 OPEN / NON_BLOCKING，相关生产代码未触碰。
- V49、TradingVenue、Order state machine、scheduler framework、migration、.github、AGENTS 和其他 Skill 文件保持进入本轮的字节内容；仅按明确允许范围追加 engineering lesson。
- stage=0 / commit=NONE / push=NONE；真实 provider、真实 LIVE、凭证和生产数据库均未使用；B2/B4 的 SIM/LIVE 标签只存在于同一隔离合成 fixture。


## 最终验证结果与候选身份

| 运行 | 结果 | 覆盖/限制 |
| --- | --- | --- |
| target-01 | 14 tests / 1 failure / 0 errors / 0 skips | 首次新增 LATE 断言错误；失败日志保留，前述原因及修正独立记录 |
| target-02 | 26 tests / 0 failures / 0 errors / 0 skips | 最终 PG eligibility/rollback/limits、6个真实进程场景及模块/包边界检查 |
| affected-03 | 23 tests / 0 failures / 0 errors / 0 skips | 原 R10、strategy 基础回归、B1 timeout/lost ACK、B2 OCC/逐笔Trade、B3 Kill recovery、B4原子提交后进程死亡/重启及PG4项；V49旧sender撤销与arm提交unknown |
| Full Maven | **1877 tests / 0 failures / 0 errors / 102 conditional skips / exit 0** | 最终稳定候选，命令 `mvn -f backend/pom.xml test`；102项跳过逐项列明，不能算作执行通过 |

[Full 结果](l4-b5-strategy-run-recovery-attempt01/full-maven.json)：2026-09-10 04:48:12Z—04:50:04Z，Java21、自有 loopback/tmpfs PostgreSQL16.15、仓库锁定镜像、canonical CI legacy PAPER account fixture、Flyway V49。进入 Full 时未继承 Spring profile 或 Java/Maven overrides，显式设置本地 datasource；exchange_accounts/credential 行数均为0。容器身份核对后已删除。仅运行这一次有效 Full，本轮没有失败 Full 或用目标测试替代 Full 的情况。

[最终 tested manifest](l4-b5-strategy-run-recovery-attempt01/tested-manifest.json)覆盖1788个 backend/CI相关文件，目标测试与 Full 的候选映射相同，Full起止及归档核对 mismatch=0。Log SHA-256=`1749de4a4a2d1c693929366cacb34d4bc25dd0b912723ecb662104830dc61bc5`。完整条件跳过原因见 [full-skips](l4-b5-strategy-run-recovery-attempt01/full-skips.json)；本任务必需的 opt-in 场景已有 target-02/affected-03 实际执行证据，不以默认 Full 中的 skip 代替 PASS。

[相对进入工作树的完整性核对](l4-b5-strategy-run-recovery-attempt01/candidate-integrity.json)：HEAD=`86c8ad84542636364f6c21e78bc292a323cbdff7`，branch=`audit/post-gatey-agent-baseline`；本轮修改5个已有文件、新增5个 production/test文件，其余进入工作树的既有文件逐字节保持。原R10和其他历史FAIL未改写，V49/TradingVenue/Order等受保护候选未变化。新增证据和 lesson 与实现身份分层，不冒充此前 V49 独立接受或 exact-head CI。

可复现命令均从仓库根目录运行；使用 JDK21，专用 harness 自建受控 PG16 和独立 Synthetic Venue。下面目标命令不使用既有/生产数据库：

```powershell
mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=B5StrategyRunRecoveryPostgresTest,B5StrategyRunRecoveryProcessTest,ModuleBoundaryArchTest,PackageBoundaryArchTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.b5.strategy=true'
mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=StrategyScheduleScanServiceTest,StrategyManualTriggerServiceTest,JdbcStrategyRunRepositoryTest,B5StrategyOwnerDeathTest,B5RealProcessProofTest,B5AuthorityProcessTest,B1RealProcessProofTest#v49AffectedTimeoutAndLostAckRegression,B2RealProcessProofTest#v49AffectedOccAndPerFillRegression,B3RealProcessProofTest#v49AffectedKillAndRestartRegression,B4TradeEventRemediationTest,B4TradeEventPostgresTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.b5.resume=true' '-Dnq.b5=true' '-Dnq.b5.row=AFTER_DROP' '-Dnq.b1=true' '-Dnq.b2=true' '-Dnq.b3=true' '-Dnq.b4.remediation=true' '-Dnq.b4.case=ATOMIC_DEATH' '-Dnq.b4.pg=true'
```

Full 的隔离启动/fixture前置与退出记录保留于 `backend/nq-app/target/b5-strategy-remediation/run-full.py` 和 `full-01/`；这些 ephemeral 文件仅辅助当前本地复核，不是部署入口。

作者自查覆盖锁/CAS、事务提交未知、关联缺失拒绝、模块边界和新窗口去重边界。P0=0 / LOCAL_P1=0 只针对本次 lifecycle remediation；没有执行或替代独立 correctness review。下一动作：`NQ-GATEAUDIT-PHASE6-L4-B5-STRATEGY-RUN-OWNER-DEATH-RECOVERY-INDEPENDENT-CORRECTNESS-REVIEW`。B5仍 NOT_QUALIFIED；P2仍 OPEN / NON_BLOCKING。

最终辅助检查见 [checks](l4-b5-strategy-run-recovery-attempt01/checks.json)：synthetic exporter 6 tests通过；17份完整导出逆映射等价；pinned Gitleaks8.18.4 的6个凭证负例全部REJECT，既有工作树与本次全部新增artifact扫描均零findings，未扩充allowlist；stage-assets errors=0、文档链接 errors/warnings=0、git diff --check通过。收尾再次核对1788个候选文件零漂移，staged files=0。
