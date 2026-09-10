# B5 effective execution quantity contract remediation — attempt01

本轮结论为 `IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW`。实现自查 P0=0、LOCAL_P1=0；独立正确性审查尚未执行。B5=`NOT_QUALIFIED`，V51=`NOT_ACCEPTED`；不继续 qualification，不修改 current authority。最终执行结果与候选身份在本文件及新证据目录中记录；旧证据保持原字节。

## 候选与范围

- HEAD：`86c8ad84542636364f6c21e78bc292a323cbdff7`，branch：`audit/post-gatey-agent-baseline`。
- 起始候选含 3683 个非忽略 tracked/untracked 文件，aggregate：`2b64706aec2d9276d2bba41c3fdfbd0494a6f554f1b058639b934f1c3000c002`。算法为按 path 排序的 `path + NUL + SHA256(bytes) + LF`，UTF-8。
- V51 previous candidate=`REJECTED`，原文件 SHA256：`7a333f1e0cca154564a0b0b13a677a241f6a8880e4b4ca099a7de8c186d342f8`；原字节另存于本轮 target 的 `rejected-V51.sql` 并与起始 hash 核对。
- [既有 V51 architecture contract](GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_DURABLE_EXECUTION_CONTRACT_REVIEW.md) 与[旧 V51 implementation evidence](GATEAUDIT_PHASE6_L4_B5_STRATEGY_RUN_DURABLE_EXECUTION_V51_IMPLEMENTATION_ATTEMPT01.md) 保持原字节；旧通过的测试不替代新候选验证。
- V51 revised candidate=`NEW_REVIEW_CANDIDATE`。V1–V50 不变、不新增 V52；不重构 V49/V50/scheduling、不改 unrelated adapter、`.github/**`、AGENTS 或 SKILL.md。按用户显式要求，仅向现有 engineering-lessons reference 追加规则。
- 变更以本轮起始快照为基线，不能把已有 dirty 工作树与 HEAD 的全部差异归于本轮。stage=0、commit=NONE、push=NONE。

## 真实 data-flow 与根因

| 事实 | 旧候选的 writer / reader | 新契约与原复现值 |
| --- | --- | --- |
| Strategy requested | definition/manual intent → admission | requested quantity=10.00050000，意图不覆盖 |
| Immutable dispatch work | StrategyDispatchWork / nq_admit_strategy_work，事务 A | quantity/price 继续保存原始意图；此时 effective 未决定 |
| Normalization owner | OkxExchangeAdapter.trim：NumericPolicy.normalize 后按 lot/tick 以 DOWN 取整 | 抽为同 adapter 的只读 normalizeOrder；lot=0.001 得到 quantity=10；price=100.005、tick=0.01 得到100；没有第二套舍入算法 |
| Durable effective | 旧候选缺失此独立事实，Order.qty 直接使用 requested | gateway 在 B 前读取 instruments；B 锁原 run，一次绑定 effective_quantity/effective_price，Order.qty/price 使用同值 |
| V49 / wire | 旧 adapter 在已取得发送决定后才静默 trim 为不同值 | 有效值与初始 NOT_ARMED 同 B 提交，先于 MAY；发送时校验并直接从 Order 参数构造 sz=10.00000000、px=100.00000000 |
| Venue original / executed | Synthetic Venue / OKX snapshot / actual fills | venue sz=10，FILLED，actual fills 累计10 |
| Terminal / accounting | V51 nq_project_strategy_run 比较 unique durable fills 与 Order.qty；B2 使用实际 Trade | 仍严格 executed=Order.qty；Order 已等于 effective，满成可 SUCCEEDED，Trade/Event/Ledger=1/1/4，无0.0005虚假残量 |

根因是 `REQUESTED_QUANTITY != EFFECTIVE_EXECUTABLE_QUANTITY`，旧 recovery 将两者当成同一事实。此前 lifecycle writer 修复未约束 adapter 的实际外发值。price 具有相同静默转换机制，本轮在同一 owner/原子决定中一并修复。没有 epsilon、lot 补偿、无条件 FILLED 终态或 replacement Order。

入口依次为 `OrderCommandStrategyExecutionGateway.executeAndProject` → `AdapterBackedTradingVenueGateway.normalizePlaceOrder` → `OkxExchangeAdapter.normalizeOrder` → `StrategyOrderPreparationService.prepare` → `JdbcStrategyRunExecutionRepository.bindEffective` / `nq_bind_strategy_effective` → `OrderCommandService.executePreparedPlaceOrder` / V49 → adapter `placeOrder` → HTTP。terminal reader 是 `nq_project_strategy_run` / `nq_strategy_execution_proof`，账务保持已有实际 Trade 派生链。

## V51 最小修订与兼容性

旧 work.quantity 表示原请求，Order 在 B 前尚不存在；将二者原地覆盖会丢失意图/差额，因此选择允许的 Case B。仅在尚未交付的 V51 work 表增加三列 `effective_quantity / effective_price / normalization_rejection`，保留既有 A/B/C 结构和 same-run→one-Order 唯一约束。

`nq_bind_strategy_effective` 只能从 CREATED 未决定状态写入一次，原 run 行锁串行化不同计算结果；并发输家必须读取赢家的 durable 决定。requested 不变，effective/rejection 不可改。deferred constraint 拒绝“effective 已提交但 Order 不存在”；有效 B 原子包含 effective、DISPATCHING、Order、初始 V49 和 RiskGate。明确无效的规范化结果则原子保存 rejection、FAILED/finishedAt/审计，不产生 Order 或发送许可。SQL 验证合法结构、正数及 NUMERIC(38,8) 精度，未实现舍入；受限函数固定 search_path 和5秒 lock_timeout。

adapter 发送时防御性校验当前 instruments，只发送原 durable Order 值。规则变化可能明确拒绝冻结值，不能改写或重新授予 mutation。配置变化不会改变已有 requested/effective；在 A 之后、B 之前尚无 durable effective 的 CREATED 可以首次计算有效决定，这是首次决定而非改写。网络/metadata 暂时不可用时仍保留 CREATED 供恢复，不能捏造确定业务拒绝。

非 OKX 路径保持现有 identity mapping，未为其他 provider 引入 normalization 实现。直接向 OKX adapter 提交未规范化数量/价格的调用现在明确拒绝 `OKX_EFFECTIVE_PARAMETERS_CHANGED`（或具体无效值原因），不再静默改变 wire。该兼容性变化已同步 [API](../../current/API.md) 与 [DB_SCHEMA](../../current/DB_SCHEMA.md)。

相邻精度缺口：lot=0.000000003、requested=10.00050001 产生9位有效值10.000500009，无法精确保存为平台8位。之前由 EffectiveOrderParameters 抛异常；本轮保留红测后，在同一 adapter owner 校验 normalized 值能否无损表示于 NumericPolicy，不能无损表示时返回 `OKX_EFFECTIVE_PRECISION_UNSUPPORTED`，原子 FAILED/Order0/authority0/PLACE0。price tick 的同类9位结果也使用相同拒绝；未再舍入出一个不同订单。

受影响回归另发现 V51 run/Order 锁环：projector 持 run FOR UPDATE 等 Order；真实 ACK 在同事务先补 external identity 再 UPDATE status，外键检查申请 run KEY SHARE，双方互等。`affected-01` 与带PG日志的 `precision-lock-01` 均复现，`lock-red-01` 的确定性双连接诊断再次复现，原日志全部保留。V51 和 repository 中所有显式 run 锁改为 FOR NO KEY UPDATE；run 身份不可改，所以该锁仍保证 lifecycle/prepare writer 互斥，并与 Order/Trade FK KEY SHARE 兼容。Order 锁、终态谓词、唯一约束及 V49 不变。永久回归 `projectionMustNotDeadlockOrderIdentityThenStatusWrite` 先观察 projector 被 Order writer 阻塞，再推进第二次 Order 写入和 commit；不能靠随机重跑或吞异常掩盖死锁。

## 当前 nonterminal crash inventory

| 当前状态/断点 | durable facts | recovery 行为与验证 |
| --- | --- | --- |
| CREATED，A提交后/B前死亡 | 原 requested work、admission、cursor；effective 未决定 | successor 用原 work 首次 normalization，B 原子建立唯一 effective/Order；原生命周期及 rounded CREATED/future-window 回归 |
| B执行中未提交 | 原 run仍 CREATED；未提交 effective/Order不可见 | PG rollback/commit-loss 回归；不允许 partial effective commit |
| DISPATCHING，B后/arm前死亡 | frozen effective、唯一 Order、NOT_ARMED、RiskGate | successor 重读同值，复用原 V49；双连接不同 effective 提案也只有一个赢家 |
| MAY后/HTTP前死亡 | frozen effective、原 Order、不可逆 MAY | 无第二 PLACE；absent 不终结，保留 RUNNING/unresolved，既有 Contract B 限制 |
| HTTP后/ACK前死亡 | 同一 effective / MAY，venue已接单 | query/reconciliation 原 Order；完整实际 fills 后终态，PLACE=1 |
| RUNNING，FILLED后/C前死亡 | effective Order、唯一 Trade/Event/Ledger | C严格比较 effective满成，原 run SUCCEEDED；重放不改 finishedAt、不重复账务，后续 dueAt 可 admission |
| RUNNING，partial/cancel/in-flight | 原 Order及实际累计fills、取消最终性或未决事实 | 保持 B2/V51 partial/overfill/cancel finality 谓词；cancel ACK不终态，未决不重授 mutation |
| 无效 normalization | rejection与原 run FAILED 原子提交 | Order=0、authority=0、PLACE=0；重放不改拒绝或finishedAt |

当前 enum 的非终态只有 CREATED/DISPATCHING/RUNNING。legacy 缺 work 不猜参数，非 OKX 自动发送不扩权；既有 recovery cursor 与独立 tick 保持原责任。没有发现需要新增跨 provider 架构的同机制缺口。

## 运行证据

环境为 Java21、Maven3.9.12、pinned PostgreSQL16.15/V51、真实 Spring/RiskGate、production gateway/OKX adapter、独立 Synthetic Venue 与 NQ JVM。数据库容器只绑定 loopback、tmpfs、fixture隔离；测试中 SIM/LIVE 为合成环境标签，未连接真实 venue、真实账户或凭证。SQL 诊断与真实发送证明分别记录。

所有日志位于 `backend/nq-app/target/b5-effective-quantity-attempt01/`，不会覆盖旧 attempts。

| attempt | 实际结果 | 说明 |
| --- | --- | --- |
| red-01 | 1 test / 1 failure / 0 errors / 0 skips | 未修候选真实复现 EFFECTIVE_EXECUTION_QUANTITY_TERMINALIZATION_GAP；原Order/FILLED与run/RUNNING冲突 |
| green-01 | 1 / 1 / 0 / 0，FAIL | 修订后业务终结与账务已通过，最后因文本10与10.00000000比较失败；改为BigDecimal精确数值比较，保留失败日志 |
| quantity-01 | 27 / 0 / 0 / 0，PASS | architecture、原rounded满成、十行矩阵、V51 schema；随后增加并发、HTTP契约和rounded CREATED未来窗口断言 |
| quantity-final-01 | 6 / 0 / 0 / 0，PASS | 1 adapter HTTP + 2 PG + 2 process + 1 schema；十行矩阵全部PASS |
| affected-01 | 13 / 0 / 1 / 0，FAIL | CREATED_PAUSE 双 successor deadlock；B2/V49、原CREATED/RUNNING、提交断连、tick/legacy、scan通过；后续矩阵行未执行 |
| precision-red-01 | 1 / 1 / 0 / 0，FAIL | 测试输入10.0005恰为3e-9整数倍，无法触发目标边界；修正fixture为10.00050001，保留该次错误测试 |
| precision-red-02 | 1 / 1 / 0 / 0，FAIL | 真实调用因超8位有效值抛异常，未达预期TRIGGERED；原JVM日志保留 `detail=Rounding necessary` |
| precision-lock-01 | 2 / 0 / 1 / 0，FAIL | 精度拒绝已PASS；CREATED_PAUSE deadlock再次复现，额外保留PostgreSQL双方语句 |
| lock-red-01 | 1 / 0 / 1 / 0，FAIL | 确定性双PG连接复现同一 ACK/projector deadlock |
| final-targeted-01 | 11 / 0 / 0 / 0，PASS | 新候选完整数量/精度/锁诊断、V51完整13行矩阵与B1受影响回归 |
| final-regression-01 | 9 / 0 / 0 / 0，PASS | 锁修订后重跑CREATED/RUNNING、A/B/C六个commit断连、legacy/tick、三轮scan并发 |
| full-01 / final Full Maven | 1898 / 0 / 0 / 121，PASS，exit0 | 2026-09-10 13:37:58–13:39:45 UTC；新建PG16.15/V51，1818个源码/配置绑定文件期间变化0，容器已清理 |

十行矩阵：EXACT=10.000；ADJACENT=10.0015→10.001；ZERO=0.0005→拒绝；BELOW_MIN→拒绝；PRICE_ZERO→拒绝；CREATED；PRE_ARM；MAY；HTTP；RULE_CHANGE（冻结10.001后lot变0.01，明确拒绝/PLACE0/不改值）。所有有效发送的 wire、Order/effective、实际fills相等；原 rounded RUNNING 与 rounded CREATED 均验证后续不同 dueAt 可进入且原 PLACE=1。

最终目标组另通过 PRECISION 与 PRICE_PRECISION，均为 `OKX_EFFECTIVE_PRECISION_UNSUPPORTED / FAILED / Order0 / authority0 / PLACE0`。V51完整矩阵的13行包括 CREATED_PAUSE/CREATED_TWO、B_PAUSE/B_DEATH、REVOKED_PAUSE、MAY_PAUSE/MAY_DEATH、TERMINAL_TWO、CANCEL_ZERO/CANCEL_PARTIAL、KILL_CREATED、RECOVERY_SCAN、B_ROLLBACK，均实际PASS；stale scanner/restart另有独立测试。

固定目标命令：

```text
mvn -f backend/pom.xml -pl nq-app -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=OkxEffectiveExecutionContractTest,B5EffectiveQuantityProcessTest,B5EffectiveQuantityPostgresTest,B5V51SchemaPostgresTest -Dnq.b5.quantity=true -Dnq.b5.v51=true
mvn -f backend/pom.xml -pl nq-app -am test -Dsurefire.failIfNoSpecifiedTests=false -Dtest=B5DurableLifecycleCrashRecoveryTest,B5V51RecoveryProcessTest,B5V51CommitProcessTest,B5V51RecoveryTickProcessTest,B5V51LegacyPostgresTest,B5StrategyScanConcurrencyTest,B5AuthorityProcessTest,B2RealProcessProofTest#v49AffectedOccAndPerFillRegression -Dnq.b5.lifecycle=true -Dnq.b5.v51=true -Dnq.b5.scan=true -Dnq.b5=true -Dnq.b2=true
mvn -f backend/pom.xml test
```

Full 由本轮 `run-full.py` 独立准备PG16/V51与canonical legacy-account fixture，清除继承的profile/Java/Maven/datasource参数后执行上述精确命令；记录源码manifest、命令、exit和清理结果。显式opt-in的进程测试结果来自目标组，不将Full中的条件skip记为执行PASS。旧1891/0/0/115属于被拒绝候选，未用作新候选最终Full。

最终目标命令在第一条命令基础上增加 `B5V51RecoveryProcessTest,B1RealProcessProofTest#v49AffectedTimeoutAndLostAckRegression`，启用 `nq.b1=true`；锁修订后再运行 `B5DurableLifecycleCrashRecoveryTest,B5V51CommitProcessTest,B5V51RecoveryTickProcessTest,B5V51LegacyPostgresTest,B5StrategyScanConcurrencyTest`，启用 `nq.b5.lifecycle/nq.b5.v51/nq.b5.scan=true`。B2/V49在affected-01中的已通过部分仍按原调用记录；新的精度拒绝只改变不可表示结果，锁修订只改变V51 run锁，未改V49文件或B2账务路径。

## 证据与完整性

- [最终候选身份](l4-b5-effective-quantity-remediation-attempt01/final-identity.json)、[本轮精确scope](l4-b5-effective-quantity-remediation-attempt01/scope-manifest.json) 与[受保护文件](l4-b5-effective-quantity-remediation-attempt01/protected-manifest.json) 分开保存；identity的aggregate排除它自身以避免自引用，覆盖其余非忽略工作树。
- [98份proof索引](l4-b5-effective-quantity-remediation-attempt01/proof-index.json)：92 PASS、6 FAIL，失败不删除；每份记录原始target路径、raw/canonical hash及逆映射等价结果。[日志索引](l4-b5-effective-quantity-remediation-attempt01/validation-log-index.json)保留每次实际Maven结果及PG锁日志，包括没有独立proof JSON的SQL红测。
- [Full结果](l4-b5-effective-quantity-remediation-attempt01/full-01-result.json)与[Full源码manifest](l4-b5-effective-quantity-remediation-attempt01/full-01-tested-manifest.json)：原manifest SHA256=`435fe9c9576dadf31647ee9a7e922b7d6b4e188591869ffba228de601caa7983`，Full日志 SHA256=`c273a1f0410b49a59ad99ec521c2814cc84889f20b968be26541aacce3228dc1`。所有最终代码/测试/迁移字节仍匹配Full。
- [manifest导出证明](l4-b5-effective-quantity-remediation-attempt01/manifest-export-validation.json)：路径→hash字典转换为显式path/sha256行，记录原始CRLF/末尾换行等序列化信息，证明语义及原字节均可逆。[执行helper身份](l4-b5-effective-quantity-remediation-attempt01/execution-helpers.json)绑定本轮本地编排脚本；永久测试入口仍是仓库JUnit类。
- canonical proof只转换字段已知的synthetic身份。旧B1的顶层 `placeResult` 和 `venueAtFault/venueFinal.events[ACK_GENERATED].body` 是额外的明确结构入口；body作为JSON解析后复用既有mapper，逆映射恢复原body字节。其bytes/sha256继续表示原wire事实，不是替换后文本。固定fixture诊断文字如 `trace_id=b5-schedule` 原样保留；随机运行身份残留为0。导出器7项回归通过，原始日志/JSON不改写。Windows Maven日志按实际UTF-8/CP936严格解码，原字节及hash不变；proof/manifest严格UTF-8。
- stage-assets：scanned=1878、reviewed_exceptions=138、errors=144，exit1；与既存144项错误集合逐项相等，new delta=0、removed delta=0，继续 `OPEN / DELIVERY_COMPATIBILITY_BLOCKER`。未改exceptions或同步hash。Git diff check通过，21个代码/契约delta文件UTF-8无BOM、无尾随空白。
- Gitleaks8.18.4使用pinned archive和当前CI配置，候选正例零findings，受保护secret负例由实际scanner拒绝；最终封存后再扫同一候选，末次结果保留本轮target的 `secret-final-01/result.json`，避免把扫描报告回写被扫描候选形成自引用。未扩充scanner例外或清洗秘密字段。

## 接受边界与下一动作

三个原P1在实现自查范围内已整改：CREATED owner death、RUNNING owner death（含rounded fully-filled）和EFFECTIVE_EXECUTION_QUANTITY_TERMINALIZATION_GAP均有实际绿色证明；新增超精度和FK锁环亦已覆盖。保留原失败、当前实现、自查及未来独立审查为不同事实，不宣称独立接受或正式B5资格完成。

```text
IMPLEMENTED /
PENDING_INDEPENDENT_CORRECTNESS_REVIEW /
B5_EFFECTIVE_EXECUTION_QUANTITY_CONTRACT_IMPLEMENTED /
ROUNDED_FULL_FILL_TERMINALIZATION_P1_REMEDIATED /
WIRE_AND_DURABLE_QUANTITY_ALIGNED /
V51_DURABLE_EXECUTION_CONTRACT_REMEDIATED /
P0_0 /
LOCAL_P1_0
```

下一动作固定为 `NQ-GATEAUDIT-PHASE6-L4-B5-STRATEGY-RUN-DURABLE-EXECUTION-V51-INDEPENDENT-CORRECTNESS-REVIEW-ATTEMPT02`。须由真正独立的新审查会话核对本轮候选和证据；当前实现会话不执行安全接受，不继续 B5 qualification，不创建该任务或发布 Git。
