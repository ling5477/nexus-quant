# HISTORICAL / NON_AUTHORITATIVE

归档自 b733b75b7d5b018dc2f72f508ba11d1450a3bddb。下文是当时的经验及任务记录，不是当前执行要求、未决状态或运行授权；不纳入默认 instruction stack。

# 重复问题根因治理与工程经验

本文件是项目统一的 engineering lessons / troubleshooting reference，按共同机制追加短条目，相关 Skill 按需读取。具体任务的原始证据保留在其既有位置，以链接引用；不为每个小问题创建独立长文档，不把历史 Gate、commit、CI run 清单加入默认 Agent 上下文。

## 经验：Idempotency Before Business Validation Rule

- 症状与首因：L5 C3 中，同一 venue fill 的两个 reconciliation actor 都通过锁外不存在预检；赢家提交后，输家把同一数量再次加入 executed total，误报 overfill。根因是 `IDEMPOTENCY_CLASSIFICATION_AFTER_QUANTITY_VALIDATION`，不是实际超额成交；原 C3 失败证据保持不变。
- Rule：对可重放 durable event/fill，在同一数据库 accounting boundary 内先区分 `NEW / ALREADY_APPLIED`。只有 NEW 才参与 quantity accumulation、overfill、balance delta 和 projection delta：`duplicate detection → business validation → durable apply`。重复 delivery 不能被解释成新的业务事实；同 key 而内容冲突必须拒绝。
- Canonical owner：`JdbcTradeRepository.insertWithRequiredEvent` 锁定 Order 后复用 `(exchange, exchange_trade_id)` 唯一契约，核对订单/账户/品种/外部订单/金额数量费用/成交时间。正常重放使用赢家 durable trade_id，不能用本次随机内部 ID 再造 Event/Ledger；上限继续读取 durable effective Order.qty。
- 为什么既有修复未覆盖：先前 Order 行锁与数量重验保护不同 fill 的总量，锁外去重只优化通常路径；B4 保护 source/event 原子性，projection 修复保护同一 source 的增量唯一。它们不证明“同 fill 两次预检均不存在”后的正确分类。无需增加唯一键、吞 unique exception 或改变数量上限；既有数据库锁、唯一约束与真实错误拒绝均保留。
- 排查：真实 venue/Order/unique Trade 数量 → canonical fill key → 锁外预检 → 事务锁内去重与数量检查顺序 → 后续使用的 durable trade_id → Ledger/Position/Snapshot 重放。永久入口为 `L5FillIdempotencyTest`、`L5FillPostgresTest`，使用 `nq.l5.fill=true`；保留两/四 JVM 的实际 PostgreSQL 等待、相同 fill 重放、不同 fill 真 overfill 负例和真实 COMMIT 响应中断。原复现与结果见[整改证据](../../docs/audit/evidence/phase6-l5/CONCURRENT_FILL_IDEMPOTENCY_REMEDIATION.md)。
- 伴随 fixture 教训：STOP/关闭 stdin 的异常不能跳过其他 owned child；每个进程均在 finally 终止并检查退出，批量清理继续处理剩余资源。actor 关闭失败也必须停止 sampler、关闭 writer。`L5FixtureCleanupTest` 分开证明 PASS、assertion、exception/closed-stdin 和 setup failure，不将清理结果混入 production correctness。

## 经验：Concurrent Projection Update Rule

- 症状与根因：L5 C1 的 120 个唯一 Trade 合计 12.0 BTC，Position 仅 11.7；最新账户查询为 11.6。不同事务无锁读取同一旧 Position 后绝对覆盖会丢增量；快照又按成交时间选 latest，导致较晚应用的旧成交被排在旧投影之后。这是一个 projection-concurrency correctness cluster，包含两个独立机制。
- Rule：跨 JVM projection 不得 `read current → calculate absolute result → blind overwrite`，除非完整读写边界受数据库 serialization/version fencing 保护。原子增量也必须同时证明 exactly-once application / replay idempotency，不能只把 SQL 改为 `qty=qty+delta`。
- Canonical owner：当前 Position 按 Trade.qty、Order.side 和 base fee 更新；现有 Ledger 金额成对分录不能冒充 base 数量 oracle。沿用同一记账事务与 `tradeId:LEDGER:*` 唯一键，完整分录、Position 和 Snapshot 原子提交；不完整历史应用 fail closed。数据库锁在读取前取得，首次建行、提交失败、进程死亡及结果不确定均不能绕过身份判定。
- Snapshot publication：币种快照需在对应账户/币种数据库锁内读状态并发布，按持锁期间分配的单调 sequence 选当前值；成交时间只表示观察时间。历史 snapshot_id 若无此前的串行发布保证，不可追认成旧投影的正确性证明。共享币种需串行，不相交账户/币种应有可并行正例，不能用全局锁或降低 reconciliation concurrency 掩盖问题。
- 排查顺序：`authoritative facts → projection writer → read/write transaction boundary → concurrency primitive → replay identity → snapshot publication order → independent reconstruction oracle`。分别检查事实唯一、不同事实无丢失、重放不增量，以及旧 writer 能否晚于新 writer 发布。
- 永久回归：保留原 C1 红色证据；`L5ProjectionConcurrencyTest` 与 `L5ProjectionRecoveryTest` 使用真实 PostgreSQL/独立 JVM，检查 1/2/4 路竞争、首次初始化、暂停旧 writer、账户/币种隔离、replay、kill/restart 和真实 COMMIT 响应中断。原 `L5BoundedWorkloadTest` 仅以 `nq.l5.level=C1` 运行 120 Trade 回归，不扩展 C2/C3。候选与实际结果见[整改证据](../../docs/audit/evidence/phase6-l5/CONCURRENT_POSITION_AND_ACCOUNT_PROJECTION_REMEDIATION.md)；实现证明不替代独立审查和 L5 qualification。

## 经验：Concurrent Convergence No-op Rule

- 症状：L6 readiness 的对账 actor 在外层读取非终态后，另一 JVM 已提交 FILLED；事务层回读 FILLED 后仍验证 FILLED → FILLED，于 CAS 前失败。外层同态检查只缩小窗口，无法保护事务回读与应用边界。
- Rule：并发 actor 发现同一目标事实已被正确提交时，在 canonical application boundary 识别 `ALREADY_APPLIED / ALREADY_CONVERGED`。相同目标不是新的业务变更；不得增加 version、重复状态迁移审计或业务事件。只优化相同目标，不能把不同目标、损坏身份或非法数量也当作成功。
- Owner：`OrderCommandWriteService.reconcileOrderStatus` 在事务内读当前事实，先识别同态，再由既有状态机和 expected-state/version CAS 处理真实变化。普通命令与 `applyExternalStatus` 保持严格迁移语义；CAS 失败仍只返回 durable truth，不刷新 version 重试旧观察。无需 migration 或全局放开同态状态机。
- Accounting：只跳过 Order transition，不跳过 fill identity/quantity 校验和 Trade/Event/Ledger/Position/Snapshot 恢复。既有 accepted 顺序为完整成交集合校验 → 状态对齐 → Trade/Event → Ledger；FILLED 仍可对应尚未完成的 downstream facts。诊断审计与状态迁移审计应分别统计，不以业务 no-op 声称所有观测写入为零。
- 与既往修复的关系：duplicate fill、duplicate command 与 same-window admission 都要求在 durable 边界辨识重复事实。它们的唯一键/锁保护不同 owner，不自动覆盖 Order 状态机校验前的竞态。保留外层快速检查与 B2 冲突/终态纠正逻辑；新增事务层同态判定补齐正确性，不能靠额外 catch 或降低并发遮蔽缺口。
- 排查顺序：candidate/venue observation → desired state → 外层读 → 事务回读 → 状态机 → CAS → version/audit/event owner → downstream 恢复。严重度按 invocation、batch、scheduler 后续轮次与 JVM 实际存活判定，不能从 qualification launcher 退出直接推断永久运行故障。
- 永久回归：`L6ConvergenceProcessTest`、`L6ConvergenceBoundaryProcessTest` 使用隔离 PG16/V51、真实 Spring 和独立 JVM，覆盖同目标竞争、重放、相邻状态、旧 CAS、冲突、batch 与真实 crash 缺账恢复；开关为 `nq.l6.convergence=true`。`nq.l6.convergence.baseline=true` 选用固定旧 scheduler 复现原失败。结果与限制见[整改证据](../../docs/audit/evidence/phase6-l6/RECONCILIATION_SAME_STATE_CONVERGENCE_REMEDIATION.md)。

## 触发条件与排除项

满足下列任一条件，必须启动根因排查，不得继续只修表面症状：

- 同类问题跨任务、Gate、CI run 或模块出现两次及以上。
- 修复后复发，或相同 workaround、allowlist、compatibility patch 连续增加。
- 不同报错指向同一底层机制，或修一个问题会稳定触发下一个机械性问题。
- 同类人工操作已经形成重复排错流程。

“同类”按根因类别判断，不要求报错文本相同。必须有共同机制的证据或强迹象；一次性 typo、单次环境故障、已知外部服务偶发失败，以及 owner / mechanism 明确不同的相似报错，不自动升级为系统性根因任务。

## 必须执行的路径

```text
STOP PATCHING SYMPTOMS
→ REPRODUCE
→ TRACE HISTORY
→ IDENTIFY ROOT CAUSE
→ FIX ROOT CAUSE
→ ADD REGRESSION
→ CAPTURE PROJECT LESSON
```

停止连续追加表面补丁，先保留原失败并追踪历史，再在授权范围内修复源头。不得沿用“出现一次加一个例外，再出现扩大 allowlist”的循环。只有生产恢复确需临时止血时才允许例外，并且必须登记根因整改 owner、后续动作和退出条件；该例外不授予生产操作权限。

每次排查至少回答以下八项；未知项标明缺少什么证据，不补造历史：

1. 第一次何时出现？区分最早已确认记录与推断。
2. 之前如何修复？定位实际补丁和验证证据。
3. 为什么上次修复未阻止复发？指出其覆盖边界。
4. 多次故障共享哪个机制？给出复现、数据流或调用链证据。
5. canonical owner 在哪里：数据生成、domain model、状态机、transaction/concurrency、persistence、configuration、CI/checker、test harness、evidence、deployment、instruction/workflow，或明确的其他 owner？
6. 现有 workaround 应 DELETE、CONSOLIDATE 还是 KEEP TEMPORARILY？保留项须有原因及退出条件。
7. 哪项修改能使未来同类问题自然消失，而非仅消除当前报错？
8. 哪些自动化 regression 能阻止复发，如何运行及判定？

## 修复顺序与永久回归

优先定位和修复：源头数据 / 事实模型 → canonical owner → 核心实现 → 生成器 / adapter → validator / checker → consumer → 最后才是 allowlist / exception。不能为了让错误输入通过而削弱正确的检查器。

根因修复必须包含原失败场景的 permanent regression、至少一个相邻变体，以及有效正常场景的 positive control。断言必须证明共同生成机制受到约束；“本次不再报错”不足以证明未来同类问题不会再次产生。记录这些回归的固定入口、运行命令及成功/拒绝标准。

## 经验条目与可复用排查路径

每种共同机制在本文件维护一个短条目，至少包含：Problem pattern、Symptoms、Root cause、Why previous fix was insufficient、Canonical solution、Detection method、Debugging sequence、Regression tests、Do / Don't、Applicable modules。后续重复事件扩充该条目，以链接保留任务细节，不复制长日志。

稳定的排查路径按以下顺序记录；后续同类故障优先复用，再针对差异补证：

```text
现象 → 第一检查项 → 判断标准 → 第二检查项
→ 最小复现 → 根因分类 → 修复路径 → 验证命令
```

## Review、提交与完成定义

根因修复涉及 trading correctness、accounting、concurrency、migration/schema、credential/security、CI/release trust 或 production deployment 时，在验收或发布前保留一次真正独立的候选审查。普通工程根因修复采用 root-cause fix + regression + self-review + relevant tests，再按已有 Git 授权提交；不因“根因分析”自动增加多轮治理 review，也不扩大 commit/push/PR/merge 授权。

重复问题只有下列条件全部满足才可标记为系统性关闭：

```text
ROOT_CAUSE_IDENTIFIED
ROOT_CAUSE_FIXED
WORKAROUND_REDUCED_OR_REMOVED
ORIGINAL_REPRO_PASS
ADJACENT_REGRESSION_PASS
PROJECT_LESSON_CAPTURED
DEBUG_METHOD_REUSABLE
```

仅 CURRENT_SYMPTOM_FIXED 不得标记系统性关闭。KEEP TEMPORARILY 是处置状态，不能代替 WORKAROUND_REDUCED_OR_REMOVED 的实际证据。单批交付验收与系统性关闭分开记录，不改写既有验收或失败历史。

## 经验：随机 synthetic identity 进入 tracked evidence

| 项目 | 可复用结论 |
| --- | --- |
| Problem pattern / Symptoms | 不同 qualification 批次将随机 synthetic identity 原样写入 Git，secret scanner 在 client/event key 等字段重复报告 generic-api-key。 |
| 最早已确认记录 / 历史修复 | 已确认最早的本轮追踪记录为[2026-09-08：前一批交付及精确例外补丁](https://github.com/ling5477/nexus-quant/commit/e0d4a0276b2ee9c9a302556277ad605e57b109b5)。随后[另一批交付的失败 CI](https://github.com/ling5477/nexus-quant/actions/runs/34318927962)再次命中；不据此断言更早历史没有同类问题。 |
| Root cause / owner | test harness 的 evidence-export owner 缺少 runtime raw identity 与 tracked representation 的边界。随机值生成适合隔离运行，但直接发布到 evidence 会持续产生新的高熵字符串。 |
| Why previous fix was insufficient | 精确 allowlist 只覆盖已见到的固定值，未约束下一次运行的导出行为。改 workflow 后若忽略已有内容 hash 绑定，还会触发第二个机械性失败；应核查当前 validator 与实际绑定，而非盲目更新所有 hash。 |
| Canonical solution | 运行身份保持随机；在导出层按已知字段建立 batch/run/type 内的稳定双射，默认 proof 文件输出 canonical references，raw-* 留在 ephemeral artifacts。引用采用短字段分隔，避免规范化后的拼接字符串再次触发 scanner。规范见[身份策略](../skills/nq-trading-correctness-proof/references/regression-delivery.md#synthetic-test-identity-policy)。 |
| Workaround disposition | KEEP TEMPORARILY：历史精确兼容例外保持原范围，不扩充。退出条件是相关历史 evidence 已获授权迁移、身份关系及必要原始字节证明仍可验证，并在去除例外的配置上通过扫描及负例；未满足前不自动删除。 |
| Detection method | 对每条命中核对 path、line、field/context、身份类型及 fixture provenance。UNKNOWN、真实秘密候选或非 synthetic 高熵值必须停止相关 canonicalization，不能借导出掩盖。 |
| Debugging sequence | 命中现象 → 查当前 scanner report → 判断是否同一生成机制 → 查旧补丁与导出入口 → 用新随机身份复现最小字段样本 → 定位 harness/export owner → 修字段映射和默认文件选择 → 执行下列验证。 |
| Regression tests | 原始完整证据结构注入新随机身份后必须零泄漏；相邻变体覆盖不同身份、类型/run 隔离、前向引用与复合键；正常导出保持状态/数量且 raw 文件不变。apiKey/token/Authorization 哨兵必须保持原值并被实际 pinned scanner 拒绝。入口与具体证据见[整改记录](../../docs/audit/evidence/l4-b2-synthetic-identity-remediation/README.md)。 |
| Do / Don't | Do：核验来源、双射和完整逆映射；运行当前 scanner、相关负例与 stage-assets。Don't：按长字符串通用清洗、统一 redacted、发布 raw identity 的 hash/base64/hex 替身、按批次或目录扩充 allowlist、修改运行 ID 为固定值。 |
| Applicable modules | L4 test harness、evidence exporter、打包/交付流程；不改变 production trading、账务或 scanner policy。 |
| 关闭边界 | 本条记录已交付的源头整改和可复用方法；历史 workaround 仍保留，尚不能据此声称满足本规则的全部系统性关闭条件。未来批次还须执行自身回归，不能借旧 CI 自动覆盖。 |

验证命令从仓库根目录运行：

```text
python backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/test_synthetic_evidence.py
mvn -f backend/pom.xml -pl nq-app -am test -Dtest=SyntheticEvidenceExportTest -Dsurefire.failIfNoSpecifiedTests=false
python backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/verify_synthetic_secrets.py <pinned-linux-archive> <ephemeral-output>
python scripts/docs/check-stage-assets.py
git diff --check
```

scanner 验证程序使用 Linux pinned archive，先校验当前 supply-chain lock，再原样读取当前 CI 配置。Windows worktree 从 WSL 调用时可通过程序的 `--git` 参数指定实际 Windows Git。判定为映射/正例通过、原身份残留为零、凭证负例全部 REJECT、tracked scan 零 findings、stage-assets errors=0；只有实际 changed bound assets 才机械同步对应 hash。发布验收另须 canonical exact-head CI。

## 经验：Durable Source Fan-out Rule

- 症状：Trade 已提交，但进程在后续事件或账务调用前死亡，restart 只能恢复部分必需事实。此前 F004 为 Ledger 增加从 durable Trade 重放；B4 证明同一 source 的 TradeExecuted 仍依赖首次插入后的单次内存调用，因此 F004 的单分支修复未覆盖完整 fan-out。
- 共同根因：将数据库 commit 后的函数调用误当成可靠持久化传播。进程退出会丢掉未执行调用，重试只检查 source 存在又可能永久跳过派生事实。
- 规则：每个 REQUIRED durable derived fact 必须与 source 同事务原子提交，或能在 restart/replay 中从 source 幂等重建。同一数据库/事务管理器、ownership 合理时优先原子提交；独立事务应保留可重建路径及持久唯一性。不要因此合并原本独立的 Ledger 事务。
- 排查：先列 source、全部 required fan-out 与各 commit；逐个核对真实 writer、恢复候选、durable identity/约束、旧事件兼容与冲突处理，再在真实提交边界 kill/rollback。不能只给当前报错位置补 append，也不能用无锁 SELECT-then-INSERT 声称并发唯一。
- 修复 owner：Trade persistence / required event recovery。当前普通 OKX 将新 Trade+TradeExecuted 原子写入；旧缺口以 source 行锁串行化恢复，稳定 event_id 主键兜底，保留且验证已有随机 ID 事件，冲突拒绝。该写入协议要求参与者遵守同一 source 锁；不据此授权旧新 writer 混跑、历史生产数据清洗或扩大到其他事件。
- Permanent regression：正常正例；原缺口旧进程→新PID恢复；event durable/ledger未提交；ledger durable/event缺失；反复重启；两个真实PG连接同时竞争source锁；event与ledger各自commit失败；SIM/LIVE与Kill下恢复。检查每个派生事实恰好一次、业务payload来自durable source、源事实及另一分支不被重写。入口：`B4TradeEventRemediationTest`（`nq.b4.remediation=true`）、`B4TradeEventPostgresTest`（`nq.b4.pg=true`）；结果见[B4整改证据](../../docs/audit/evidence/GATEAUDIT_PHASE6_L4_B4_TRADE_EVENT_DURABILITY_REMEDIATION_ATTEMPT01.md)。实现证明、独立审查、交付与B4资格验收分别记录，不相互替代。

## 经验：Durable Intermediate State Recovery Rule

- Problem pattern / Symptoms：durable 中间态跨进程存活，原 JVM 的最后一次回调消失，restart 没有 lifecycle writer。B5 R10 中 Order/V49 已收敛为 `CANCELLED / REVOKED_BEFORE_SEND`，strategy run 仍为 `DISPATCHING`，新 JVM 持续命中 `strategy_run_active`；与 B4 已提交 Trade 的必需派生事实缺失属于同一故障族。
- Root cause / Why previous fix was insufficient：B4 修复了 Trade fan-out，V49 修复了发送资格与 Order 最终性，两者均不拥有 strategy run。数据库中的 active 状态不能由原 JVM 内存调用独占完成职责。
- 规则：任何能跨事务或跨进程长期保持 active/in-progress 的 durable state，必须至少满足其一：A，在同一原子边界进入最终状态；B，restart 后仅根据 durable facts 幂等恢复；C，明确定义为 unresolved/manual-resolution 状态，且阻塞是业务正确性要求。不能依赖原 JVM 最后一次内存回调完成生命周期。
- Canonical owner / solution：strategy application 拥有恢复入口，infra 在短事务内依赖 `strategy_run_id → orders → ordinary_place_authorities` 的外键关联判断资格，行锁/CAS 保证一次合法迁移；active 查询和 scheduling dedup 保持独立。`REVOKED_BEFORE_SEND` 加无发送终态允许沿用 `FAILED`；`MAY_HAVE_ESCAPED` 未决不能清空 active gate。不得猜测成功终态，也不得扩修所有历史 RUNNING。
- Debugging sequence / Detection：列全部实际状态和 writer → 找跨 commit/process 的中间态 → 核对 durable 下游绑定与完整性 → 分类已知未发送、未决、明确结果 → 在真实提交边界杀 JVM → 核对原 run、原 Order、发送计数和下一逻辑窗口 → 添加恢复 writer 与并发回归。时间戳、日志、最近订单、PID、lease 到期均不能代替业务最终性。
- Permanent regression：`B5StrategyOwnerDeathTest` 保留原 R10；`B5StrategyRunRecoveryProcessTest` 覆盖双 JVM 竞争、再重启、存活 owner、未决 MAY、迟到回调和 commit 响应丢失；`B5StrategyRunRecoveryPostgresTest` 覆盖缺关联/多单/环境冲突拒绝、过滤后限量、回滚和终态 CAS。运行属性分别为 `nq.b5.resume=true` 与 `nq.b5.strategy=true`，配合 `-Dtest=<对应类> -Dsurefire.failIfNoSpecifiedTests=false -pl nq-app -am`。成功标准是原 run 最多一次迁移、恢复重放不产生新 run/order/PLACE、未决事实继续阻塞。
- Do / Don't / workaround disposition：新增 durable recovery owner，保留 JVM busy set 作为局部重入优化；不加超时清状态、V49 reset/re-arm、额外 leader/heartbeat、字段启发式或 scanner 例外。若缺少持久关联则先报告 schema contract，不能绕过审查补 migration。
- Applicable modules / closure：strategy lifecycle、交易编排与 restart recovery。验证结果和未覆盖边界见[B5 strategy-run 整改证据](../../docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_OWNER_DEATH_RECOVERY_REMEDIATION_ATTEMPT01.md)；经验记录不替代独立审查、P1 接受或 B5 qualification。

## External Mutation Finality Rule：本地 OCC 不等于外部副作用 fencing

- Problem pattern / Symptoms：recovery 已提交“外部订单不存在”的终态，较旧 sender 恢复后仍发出 PLACE；本地 status/version 完全保持正确，venue 却出现活动订单。最早已核对的相关历史是 L4 plan 的 dormant PB2 late-sender 观察；当前 ordinary 路径由 [B5 双 JVM 红色复现](../../docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_DUPLICATE_COMMAND_SCHEDULER_LOCK_MULTIPROCESS_OWNERSHIP_QUALIFICATION.md)证明，不能混同两条入口的验收身份。
- Root cause / Why previous fix was insufficient：local OCC protects local writes, not external side effects。C1 的 CAS 约束 ACK 写入，不撤销已经拿到旧快照的调用者的外发能力。任何宣告 external mutation 不可能发生或已最终结束的状态迁移，都必须与仍能发出该 mutation 的 actor 共享可证明的 ownership/fencing 边界。
- Canonical owner / solution：ordinary command 的 mutation authority 与 recovery 的 negative finality；Controller、scheduler、composition root 不能各自补一套去重。应先原子仲裁发送资格和最终性，再证明丢失资格的 actor 与在途请求不能在最终性之后产生新副作用。DB token、lease expiry、连接断开或 HTTP timeout 本身均不证明外部执行已停止；不得把 sender 的再次 SELECT 当作 fence。
- Debugging sequence：①找 external side-effect point；②找 mutation authority acquisition；③找 authority revocation；④找 competing finalizer；⑤检查双方是否共享 durable concurrency boundary，以及 fence 在何处实际生效；⑥验证 stale actor 暂停/恢复，特别是 DB 连接丢失而 JVM 仍活；⑦验证真实 owner death/takeover 与已经在途的请求。检查 venue truth、DB finality、身份与先后关系，不能只看 ACK 被拒绝。
- Regression / detection：保留 `B5RealProcessProofTest` 正确行为断言；整改后须覆盖 recovery-wins、sender-wins、两个 JVM 无固定 sleep 的近同时竞争、失去 ownership 后恢复、强杀前后边界、重复 command、query-first/no blind retry。原始未修候选应红，修复候选必须在相同真实边界变绿；只读锁 callback 测试不能冒充网络 fencing。
- Do / Don't / workaround disposition：未新增 workaround。不能通过永久禁止 negative recovery 保住 safety 却让 SENT 永久悬挂；不能只把 PG 锁跨 HTTP 就宣称解决连接丢失后的旧发送者。需要 durable schema 或执行端契约时先按授权边界停下并报告，不能把 identity/audit 字段暗改成第二套 ownership 存储。
- Applicable modules / closure：ordinary trading command、recovery、repository concurrency、external adapter。方法已记录，生产 P1 尚未修复，ROOT_CAUSE_FIXED/ORIGINAL_REPRO_PASS/系统性关闭均未达到；当前方案评估和缺口见 [B5 remediation attempt01](../../docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_ORDINARY_SENDER_RECOVERY_FINALITY_REMEDIATION_ATTEMPT01.md)。
- Contract B 澄清（2026-09-10）：当任务明确接受 unresolved/manual-resolution 的可用性代价时，可选择不可逆的一次性 MAY_HAVE_ESCAPED 决定；该决定后不再授予 PLACE，也不凭 absent 宣告无订单终态。它通过禁止错误最终性关闭冲突，不声称物理撤回 stale HTTP。NOT_ARMED 的撤销与 sender 的首次 arm 必须同一 durable CAS 边界互斥；COMMIT unknown 即使读回 MAY 也不恢复发送许可。epoch、PID、lease 不是这个一次性协议的必要字段。
- Proof / availability 边界：区分 arm 前撤销后的 stale send=0，与 confirmed MAY 后原唯一调用可能迟到但 no-order finality 被禁止。owner death 后可以接管 query/reconciliation，不可重授 mutation；长期 unresolved 是诚实的未知事实，不是死 owner 永久占锁。预算耗尽只触发 MANUAL_RESOLUTION_REQUIRED，不授权人工猜测终态或重下单。所选三态和最小V49契约见 [B5 contract review](../../docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_DURABLE_MUTATION_AUTHORITY_AND_EXECUTION_FENCING_CONTRACT_REVIEW.md)；设计通过不代表实现、运行证明或P1关闭。
- 独立事务与最终回归：引入 REQUIRES_NEW 后，先检查测试外层事务是否仍持有账户/Kill/Order fixture 锁；在自有 schema 内提前提交前置事实，靠 schema 清理隔离，不削弱 production 锁或事务。Full Maven 前显式准备隔离 PostgreSQL、datasource 和测试声明的 legacy account 前置数据；不能依赖默认 local 数据库。失败 Full 原样保留，后续目标 PASS 不能替代它；候选或测试前置实质变化后，以最终稳定候选的有效绿色 Full 为准。本次 V49 运行证明、首次失败与第二次绿色 Full 分别保留，见[实现证据](../../docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_FAIL_CLOSED_MUTATION_AUTHORITY_V49_IMPLEMENTATION_ATTEMPT01.md)。
## 经验：Durable Admission Uniqueness Rule

- 故障：B5两个真实scan都经过active/window的SELECT后，各自创建同一逻辑窗口的run；JVM-local busy互不知晓。下游Order/V49虽保住PLACE=1，上游仍有RUNNING和无Order的DISPATCHING两条事实。
- 边界：跨进程必须至多一次的业务admission，不能由SELECT-then-INSERT或JVM-local guard证明。canonical logical identity加数据库强制的原子admission才是正确性边界；读取已有记录不是取得winner资格。
- Canonical owner：strategy application派生`strategy/account/schedule_job_id/dueAt`，infra以唯一索引和短事务`INSERT ON CONFLICT DO NOTHING`创建StrategyRun，冲突输家不执行下游。配置版本、随机request、client identity与PID都不能重新开放同一窗口；生命周期恢复不删除或更换admission身份。
- 排查：复现双进程过检查边界 → 对比两者真实key → 查唯一约束/INSERT事务及冲突语义 → 证明一赢家 → 验证stale/restart/recovery与不同窗口正例。复用B5既有屏障、真实PG和Synthetic Venue，不增加scheduler/lease/leader框架。
- 回归入口：`B5StrategyScanConcurrencyTest`三轮真实竞争；`B5AdmissionProcessTest`的stale、提交前/后死亡、再重启、MAY与recovery+scan；`B5AdmissionPostgresTest`的并发、不同请求同key、不同窗口/策略/账户、回滚、legacy拒绝及V49→V50升级。参数分别为`nq.b5.scan=true`和`nq.b5.admission=true`。
- 旧修复边界：Order幂等与V49只拥有下游身份/发送权限；原strategy恢复要求绑定Order及可证明无发送，不能猜测回收新出现的无Order中间态。无Order死亡若只保留fail-closed，必须明示可用性限制，不能宣称自动恢复已完成。
- 不新增workaround；不更改未知V49状态、不把唯一冲突变成污染事务后的500、不回填猜测窗口、不让旧/新scan writer混跑。V50只在原StrategyRun中增加必要列与约束；旧红色证据、原接受事实和新实现/独立审查状态分层保存。

## 经验：Durable State Crash-Window Completeness Rule

- Problem pattern / recurrence：B5先出现DISPATCHING owner死亡后的永久BUSY，随后V50的CREATED重现同类问题；本轮又实测Order已FILLED且Trade/Event/Ledger=1/1/4，RUNNING仍永久阻塞未来窗口。上一次writer只覆盖已见的no-send终态，没有覆盖状态机全部当前可达断点。
- Rule：新增或修改durable状态机时，枚举每个当前reachable non-terminal state及其entry commit、下一durable work、实际recovery writer、安全事实和unresolved契约。逐项回答：进入后立即死亡；下一持久事实前死亡；paused old owner与successor竞争；restart/replay幂等；未来工作是否永久被阻塞。每个状态必须有atomic-next-step、restart-safe recovery或correctness-required explicit unresolved contract之一，不能只修当前报错状态。
- Canonical owner / method：lifecycle application拥有完整状态收敛，infra提供不可绕过的同一work身份、绑定与原子/CAS边界。先验证恢复payload可从durable facts完整重建，再验证同run下一项work的数据库唯一性；admission唯一、client唯一和mutation authority各有范围，不能互相替代。读最新配置、按年龄回收或单次CAS后再独立创建Order不能证明恢复安全。
- Debugging sequence / detection：列实际enum与全部writer → 展开每个commit/外发断点 → 核对有效请求是否持久化、run→Order是否强制唯一 → 对每类状态运行原owner死亡、存活暂停及successor竞争 → 用DB/venue/账务oracle检查原身份、终结与下一窗口。已有安全事实和真正未知事实分别处理，不以不存在的callback或人工流程解释永久busy。
- Regression：`B5DurableLifecycleCrashRecoveryTest`，参数`nq.b5.lifecycle=true`，配合`-pl nq-app -am -Dtest=B5DurableLifecycleCrashRecoveryTest -Dsurefire.failIfNoSpecifiedTests=false`。当前两个红色场景永久保留：CREATED死亡与RUNNING死亡后真实成交收敛；未来每个新增状态都增加同一检查矩阵。paused-owner、双recovery、commit未知和cursor进展须在修复协议稳定后补齐，不把未执行项当PASS。
- Do / Don't / disposition：KEEP既有V49与安全no-send writer，后续在完整lifecycle内整合；不创建replacement run、不删除admission、不把成功成交改成失败、不将手动请求的缺失payload猜成配置快照。若当前不能证明同run→Order最多一个，或需要schema，按任务授权停止并提交最小contract，不用JVM ownership规避设计审查。
- Applicable modules / closure：仅当前StrategyRun lifecycle、same-run work/Order binding和scheduler交互。详见[本轮清单、红色证据与待审设计](../../docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_DURABLE_LIFECYCLE_CRASH_RECOVERY_REMEDIATION_ATTEMPT01.md)。本轮STOP，P1仍OPEN，未达到ROOT_CAUSE_FIXED或系统性关闭；该规则不替代真正独立候选审查。

- V51 implementation regression 补充（2026-09-10，待独立正确性审查）：保留原生命周期红测历史；新增 `B5V51RecoveryProcessTest`（CREATED/prepare/MAY 的暂停与死亡、双 successor、terminal/cancel/Kill/scan），`B5V51CommitProcessTest`（A/B/C COMMIT 前后断连），`B5V51RecoveryTickProcessTest`（disabled manual 原 work 启动/tick 恢复），`B5V51LegacyPostgresTest`（70 行 legacy 循环公平性与拒绝历史多 Order 升级）。参数 `nq.b5.v51=true`；具体实际结果与尚未完成项见 [V51 实施证据](../../docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_DURABLE_EXECUTION_V51_IMPLEMENTATION_ATTEMPT01.md)。实现与 proof 不代替后续独立 review，也不将 B5 改为 QUALIFIED。

## 经验：Effective External Mutation Contract Rule

- Problem pattern / Symptoms / 历史边界：V51 首轮独立审查发现 requested/work/Order qty=10.0005，而既有 OKX lot=0.001 的 DOWN 规范化实际发送 10；venue FILLED、durable fills=10，run 仍 RUNNING 并永久阻塞未来窗口。此前 V51 补齐 lifecycle writer，却继续把 pre-normalization intent 当作 external execution fact；与 canonical identity 的 owner 错配是同一机制的执行域表现。最早已确认本场景为该首轮审查，不推断更早历史。
- Rule / Canonical solution：任何 adapter/provider 规范化后才发送的 mutation 参数，其实际可发送值必须在 external side effect 前形成 canonical durable fact。保留 requested intent；由现有唯一 normalization owner 计算 effective decision，先与对应 Order 及发送前事实原子冻结，再允许 V49 send decision。adapter 只验证并发送相同 durable 值；recovery/accounting/finality 使用实际 mutation contract。price 与 quantity 同属该边界，一并核对，不能只补当前数量症状。
- Detection / Debugging sequence：requested fact → normalization owner → durable effective fact → actual wire payload → venue truth → recovery predicate。逐项定位 writer、数值/精度/单位及 commit，检测原始值与实际值差异；再在 effective durable、MAY、HTTP/ACK、完整成交后的窗口杀原 JVM，检查 successor 是否读取同一决定、同 run/Order、后续窗口及真实账务。
- Owner / Workaround disposition / Do / Don't：OKX adapter 保留唯一 lot/tick/min 算法；strategy preparation 与 V51 拥有 requested/effective 的原子不可变绑定；Order 是成交比较 owner。不新增 workaround；不复制 rounding 到 SQL/strategy/recovery，不用 epsilon、lot 补偿或 FILLED 无条件终态，不按新配置/metadata 重算已决定参数、不创建 replacement Order。新规则不再接受冻结值时明确拒绝；取整为零/低于 min 的确定拒绝须持久终结且 PLACE=0。
- Permanent regression：`OkxEffectiveExecutionContractTest` 校验真实 HTTP 值及拒绝 silent trim；`B5EffectiveQuantityPostgresTest` 证明并发唯一决定、不可变性、精度拒绝与 B 原子性；`B5EffectiveQuantityProcessTest` 保留原红测、相邻数量、精确正例、无效数量/价格、metadata 变化和真实 Spring/PG/venue 进程死亡矩阵。运行 `mvn -f backend/pom.xml -pl nq-app -am test -Dtest=OkxEffectiveExecutionContractTest,B5EffectiveQuantityPostgresTest,B5EffectiveQuantityProcessTest -Dsurefire.failIfNoSpecifiedTests=false -Dnq.b5.quantity=true`；判定 wire=durable effective、有效满成可恢复终态、无效 PLACE=0、原 Order≤1、Trade/Event/Ledger 无虚假残量/重复。
- Applicable modules / closure：ordinary OKX strategy execution、V51 work/Order、adapter 与恢复/账务读取边界；不扩为跨 provider precision framework。原失败与本轮实现证明见[有效执行参数整改证据](../../docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_EFFECTIVE_EXECUTION_QUANTITY_CONTRACT_REMEDIATION_ATTEMPT01.md)。实现自查不能代替独立审查；V51 未接受、B5 NOT_QUALIFIED，系统性接受仍待独立候选 review。
- 相邻边界补充：venue 步长细于平台精度时，规范化结果可能无法精确 durable；应在同一 owner 返回确定拒绝并原子终结，不再次舍入或将确定错误永久留给重试。恢复事务还须核对真实 FK 锁：ACK 同事务补 identity 后再改状态可能申请 run KEY SHARE；不变主键的 lifecycle writer 使用 NO KEY UPDATE 保持互斥，避免 run→Order 与 Order→FK 的锁环。新增 quantity/price 超精度真实进程拒绝与确定性双连接锁回归；原失败及 PG 锁日志按 attempt 保留。


## 经验：Stage Asset Lifecycle Rule

- 症状与根因：合法技术演进后，stage-assets 在交付尾部反复报告旧摘要与旧 caller identity；只改 hash 无法治理下一次复发。注册必须绑定 canonical asset lifecycle，不能用长期静态快照追赶持续演进的代码。历史首次可确认是 2026-09-06 ROADMAP 变化后的单摘要修补，随后 CI 变化再现；详细追溯见[本次根因证据](../../docs/audit/evidence/GATEAUDIT_PHASE6_L4_B5_STAGE_ASSETS_ROOT_CAUSE_REMEDIATION.md)。
- Canonical owner：`scripts/docs/stage-asset-exceptions.json` 继续唯一拥有分类、例外理由、owner、退出条件和已接受身份；`check-stage-assets.py` 拥有动态 inclusion/exclusion 与统一摘要/调用图计算；`stage-asset-lifecycle.py` 只复用该计算生成临时提案，不创建另一份运行时 registry。技术变更作者负责在候选稳定后、请求验收前生成提案，相关注册 owner 的独立审查负责判定变化是否合法。
- 合法演进：冻结候选 → `python scripts/docs/stage-asset-lifecycle.py propose --output <ephemeral-proposal.json>` → 独立审查旧/新身份、源变化与接受来源，并记录提案摘要 → `python scripts/docs/stage-asset-lifecycle.py apply --proposal <ephemeral-proposal.json> --reviewed-proposal-sha256 <reviewed-sha256>` → canonical validator PASS。提案生成不代表授权；应用参数必须来自审查结论，不能把生成器输出直接管道回 apply。
- 未授权漂移：canonical registration 未更新则 validator FAIL；提案后新增、删除、修改任何 active 输入、旧 policy 或工具都会使应用拒绝。应用要求工作区静止；原子替换保护写入完整性，不宣称文件系统提供跨所有输入的事务隔离。历史 evidence、已发布 migration 和 frozen history 按各自不可变契约处理，不因本工具而获准重写。
- 例外生命周期：工具只能更新既有例外的摘要与现有兼容合同的精确 caller 集合；不新增例外、不改 owner/reason/退出条件、不扩目录豁免。消失或不再命中阶段语义的普通注册可确定性移除；兼容合同退出、成员变化、新语义例外与安全数据分类必须另做显式 policy 变更和审查。重复注册仍拒绝。版本化 fixture 与兼容调用是可演进受保护资产，不是永久冻结的历史证据。
- 排查：reproduce mismatch → group by mechanism → find canonical owner → distinguish stale registration vs genuine drift → repair lifecycle → regenerate deterministically → negative regression。不能将诊断条数当文件数，也不能把 UNAUTHORIZED_COMPATIBILITY_CALLER 标签直接当已证实的非法业务调用。
- 永久回归：现有 `python -m unittest discover -s scripts/docs/tests -p test_stage_assets.py` 覆盖合法演进、未登记漂移、新语义、失效/删除注册、重复注册、提案摘要/输出篡改和动态新增输入；保留既有 Java/Javascript reachability 拒绝。CI 继续同一 validator 与测试入口，不自动刷新 registry。每次候选稳定后在本地先运行 guard，把 identity 更新纳入同一候选审查；交付 CI 仍提供独立 integrity 检查。
