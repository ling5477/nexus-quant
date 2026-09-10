# B5 strategy same-window dispatch admission remediation

本轮实现同窗口持久admission并完成本地证明，状态为 **IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW**。目标`STRATEGY_SAME_WINDOW_DUPLICATE_DISPATCH`已本地整改；B5仍`NOT_QUALIFIED`，未继续剩余qualification或precise delivery。

范围内的“admission提交后、尚无Order”死亡窗口只证明不再重复认领，仍保留CREATED并fail-closed；**没有实现或宣称该窗口的自动恢复**。已审recovery只覆盖存在可靠Order/V49无发送关联的原run，本轮不擅自扩大资格。该可用性限制明确交给reviewer判断，不能把它混写为原R10自动恢复能力。

## 起点与历史

- Task：`NQ-GATEAUDIT-PHASE6-L4-B5-STRATEGY-SAME-WINDOW-DISPATCH-ADMISSION-REMEDIATION`；`HIGH_RISK / P1_CORRECTNESS_REMEDIATION / POSTGRESQL_CONCURRENCY / STRATEGY_ADMISSION / NQ-only`。
- HEAD=`86c8ad84542636364f6c21e78bc292a323cbdff7`，branch=`audit/post-gatey-agent-baseline`。进入本轮3537个tracked/untracked非忽略文件已冻结原始hash；保护用户已有改动，不把它们计成本轮实现。
- 原reviewed fingerprint=`9be6df6668a2bcbbd3eb453be9a051c046d78a77d12a2d1e08070d70cd17cf00`，后续[qualification attempt02](GATEAUDIT_PHASE6_L4_B5_DUPLICATE_COMMAND_SCHEDULER_LOCK_MULTIPROCESS_OWNERSHIP_QUALIFICATION.md#qualification-resume-attempt02--2026-09-10)真实失败为两个run、一个Order/PLACE，完整[原R01](l4-b5-qualification-resume-attempt02/R01.json)保持原字节。
- 本轮production变化后不再复用原reviewer Full作为最终candidate PASS；未重开V49、TradingVenue、stale sender、authority bypass或已接受的strategy recovery设计审查。生产候选必须接受下一次真正独立review。

## 根因与canonical identity

实际链仍是`scanOnce → active/window/dedup观察 → ManualStrategyTriggerGateway → StrategyManualTriggerService → StrategyRun → OrderCommandStrategyExecutionGateway → ordinary Order → V49 → Venue`。旧两个SELECT与INSERT分离，两个JVM都可读到无active/无window，然后各生成随机run。Order幂等及V49只能挡住下游，不拥有StrategyRun admission。

canonical key固定为：**strategy_id + account_id + schedule_job_id + CRON dueAt**。

| 字段 | 定义和边界 |
| --- | --- |
| strategy/account | 当前策略定义绑定的身份；repository同时校验schedule的strategy/account，不允许跨账户伪绑定 |
| schedule/window | 现有持久schedule_job_id与`resolveDueAt`计算的UTC瞬时；相同计划/窗口在两个JVM下完全相同 |
| manual scan invocation | 手动调用的是scan入口，不把每次scan的trace、随机UUID或调用时间当作窗口 |
| request/client | 保留原三种schedule request展示格式和`coid-`下游形式；唯一性独立于requestId，因此不同调用request也不能绕过同key |
| definition version | 当前version审计配置快照，不定义一次新的同窗口消费；配置更新不能自动重复已消费窗口。新窗口仍使用当时的正常定义快照 |
| 独立manual trigger | 没有schedule/window输入，保持既有独立手动操作契约；HTTP请求不开放自选结构化schedule身份 |

新`StrategyDispatchIdentity`在core中持有typed key，校验非空scope和秒级CRON时间。scan将它传递到原trigger入口；全链无PID、lease、heartbeat、epoch或新scheduler框架。

## V50与原子协议

[V50](../../../backend/nq-infra/src/main/resources/db/migration/V50__strategy_window_admission.sql)只在原`strategy_runs`增加`admission_schedule_id`与`admission_due_at`，使用schedule FK、列对CHECK及部分唯一索引。新键不替代run主键、Order SoR或V49；终结/恢复不释放窗口，trigger阻止修改已认领的四项身份。

`JdbcStrategyRunRepository.admit`使用Spring `REQUIRES_NEW / timeout=5`短事务：

1. 验证run为CREATED/SCHEDULER且scope与typed key一致。
2. 对同scope精确匹配三种历史schedule request格式的旧NULL身份行保守拒绝。旧数据不推断、不合并、不回填；不根据“最近订单”猜归属。
3. `INSERT … SELECT`验证真实schedule绑定，随后`ON CONFLICT … DO NOTHING`。唯一索引在并发事务中强制同key只有一条run。
4. 只有本次插入成功且事务成功返回才是winner，继续原DISPATCHING CAS和下游；读回已存在run始终是loser。提交异常/未知不继续发送。
5. loser在新的READ COMMITTED语句快照读取canonical run，返回`duplicateAdmission=true`；scan映射为`SKIPPED_DEDUP / duplicate_admission`，不调用下游、不更新lastTriggeredAt。没有catch unique violation后在污染事务中查询。

active predicate仍纯SELECT；原recovery仍是独立writer，在scan中先于active观察执行。JVM busy只做本地重入优化。正常winner的CREATED→DISPATCHING现在检查CAS结果，失去状态资格不能继续。

### Legacy、部署及回退边界

- V1–V49 hash不变；V50 additive，无默认窗口和历史UPDATE。真实V49库带两条重复legacy request升级成功，两条旧行均保留NULL admission字段。
- 部分索引精确排除NULL旧行；对可识别的历史同窗口请求保守拒绝。无法从旧行恢复结构化窗口的记录不被猜测backfill。
- 新保证要求全部scan writer使用新协议；升级前停止旧scan进程，禁止旧/新扫描writer混跑。旧writer会写NULL，不能把旧二进制当作安全回退到可扫描状态。
- DDL锁等待5秒、语句上限30秒；索引需要扫描表，生产容量和迁移窗口留给授权部署评估。迁移失败不静默跳过；无down migration，停扫描后前向修复，不能删除/清空identity重授窗口。
- 本轮只操作自有隔离PG，不执行生产migration、LIVE或真实provider。

## 已执行证明

所有完整raw proof留在target；canonical副本及hash见[proof-index](l4-b5-admission-remediation-attempt01/proof-index.json)。原R01红色断言保留为正确行为要求，新版增加重复3轮及A/B实际key输出比较，未将其改成“允许两条run”。Barrier只暂停原gateway/事务边界，不替换返回值，不在运行中改业务表。

| 场景 | 实际结果 | run / Order / V49 / PLACE / Trade-Event-Ledger |
| --- | --- | --- |
| RACE-1/2/3 | 每轮A/B均通过观察并到达屏障，实际key字符串相同；同时释放后1个TRIGGERED、1个duplicate_admission，无FAILED | 1 RUNNING / 1 ACCEPTED v3 / 1 MAY / 1 / 0-0-0 |
| STALE | A派生窗口后暂停，B先认领并完成PLACE；A恢复为loser。随后fill及两次恢复，最后两个新JVM重放不改快照 | 1 RUNNING / 1 FILLED v4 / 1 MAY / 1 / 1-1-4 |
| BEFORE_ADMISSION_DEATH | A停在提交前且PG无run；杀A后B合法winner；两个新JVM不重复 | 1 RUNNING / 1 FILLED v4 / 1 MAY / 1 / 1-1-4 |
| AFTER_ADMISSION_DEATH | A的CREATED已提交、尚未执行下游时死亡；B与后续两个新JVM均不新建run | 1 CREATED / 0 / 0 / 0 / 0-0-0；自动恢复NOT_IMPLEMENTED |
| RECOVERY_SCAN | A在Order prepare后arm前死亡；普通恢复先得到无发送终态，然后B recovery与C scan并发，最终只一次FAILED迁移，重放稳定 | 1 FAILED / 1 CANCELLED v4 / 1 REVOKED / 0 / 0-0-0 |
| MAY | A在MAY后发送前死亡；B及新JVM维持active block，无替代admission/PLACE | 1 DISPATCHING / 1 SENT v2 / 1 MAY / 0 / 0-0-0 |
| ORIGINAL-R10 | 原已审安全orphan场景在V50下保持通过，原run恢复且窗口仍消费 | 1 FAILED / 1 CANCELLED v4 / 1 REVOKED / 0 / 0-0-0 |

MAY=`MAY_HAVE_ESCAPED`，REVOKED=`REVOKED_BEFORE_SEND`。所有场景CANCEL=0，blind retry=0。RACE的零成交不代替账务证明；STALE与BEFORE_ADMISSION_DEATH实际产生唯一Trade、TradeExecuted及4条Ledger，重复恢复和新JVM快照一致。原生命周期仍将FILLED映射为RUNNING，本轮不新定义成功终态。

PG独立事务测试另证明：不同per-invocation requestId但同typed key仍只一个winner且返回同run；sequential duplicate、FAILED后的duplicate均拒绝；不同窗口、不同策略及当前合同下不同账户的独立策略定义均能成功admit；回滚不提交run，后继可成为winner；旧窗口拒绝；identity不可更换；直接SQL违反unique/列对/FK均失败。正例证明admission key不会形成全局strategy锁，不撤销原active gate对仍在运行策略的正常限制。

原recovery PG regression继续证明51条安全候选分50+1处理、67条未决/负例受保护、rollback零提交、迟到CAS no-op。未重跑B1–B4完整矩阵。

## 运行记录与最终候选

[test-runs](l4-b5-admission-remediation-attempt01/test-runs.json)保留全部运行、计数和日志hash：

| 运行 | tests / failures / errors / skips | 说明 |
| --- | --- | --- |
| target-01 | 36 / 0 / 0 / 0 | 基础strategy、repository及架构检查 |
| race-02 | 3 / 3 / 0 / 0 | 首次夹具仍固定49，实际迁移到50后在创建阶段失败；未进入竞争，不是duplicate admission回归 |
| race-03 | 3 / 0 / 0 / 0 | 更新fixture版本断言后，三轮真实竞争通过 |
| interaction-04 | 3 / 0 / 0 / 0 | 2个PG测试＋1个JUnit内5个独立进程场景；不能计成7个JUnit |
| final-target-05 | 41 / 0 / 0 / 0 | 实际A/B key比较的最终三轮竞争、原R10、PG恢复资格及基础/架构回归 |
| Full Maven | **1881 / 0 / 0 / 106 conditional skips** | 最终冻结candidate；exit0 |

Full命令=`mvn -f backend/pom.xml test`。Full01于2026-09-10 07:26:51Z—07:28:43Z执行，自有loopback/tmpfs PG16.15、canonical锁定镜像和CI legacy PAPER account fixture，正常迁移并validate至V50；exchange_accounts/credentials=0。没有继承Spring profile或Java/Maven options，显式datasource仅指向本轮实例。容器按ID/label/image/port核验后已删除。

[Full结果](l4-b5-admission-remediation-attempt01/full-maven.json)、[106项skip原因](l4-b5-admission-remediation-attempt01/full-skips.json)、[1794文件最终manifest](l4-b5-admission-remediation-attempt01/tested-backend-manifest.json)。Full01日志SHA-256=`8f40557ec980b52901ce647dbed84385a0052f12465d4f3f227cde51815ee93d`，它对应导出修正前的manifest。后续新增admission观察字段触发scanner后，在已有exporter补齐typed身份映射；Java/V50不变，Python/exporter候选变化，因此Full01保留为历史PASS，不冒充最终完整candidate。Full02绑定最终稳定候选，其时间、日志hash和manifest身份见Full结果。归档使用既有path/sha256结构并验证逆映射相同。Full起止及收尾逐文件mismatch=0。必需opt-in场景已在目标运行实际执行，不用Full conditional skip代替PASS。

为保持迁移后的fixture可用，只更新使用latest schema的B0–B5测试固定版本断言及B2/B3/B4 proof schema标签。未重写历史proof，未改变历史正确性断言。新代码与本轮新增import均显式导入；未批量处理P3历史通配符。

## 变更、遗留与交接

[source manifest](l4-b5-admission-remediation-attempt01/source-manifest.json)列出本轮所有backend变化：8个production Java文件、1个V50；test包括新admission PG/进程场景、原R01增强、测试控制命令和key日志与必要schema断言。API/DB_SCHEMA补充新候选契约；现有engineering-lessons追加`Durable Admission Uniqueness Rule`，未新增Skill。完整起点delta见[candidate-integrity](l4-b5-admission-remediation-attempt01/candidate-integrity.json)。V49、TradingVenue、既有strategy recovery production、.github、AGENTS及全部历史evidence保持原字节。

- P0=0；目标same-window重复admission的LOCAL_P1=0，待独立审查确认关闭，不能自授接受。
- 普通Order并发INSERT输家P2仍`OPEN / NON_BLOCKING`。新strategy admission在其之前正常拒绝同窗口输家，避免进入该错误路径；未修无关ordinary INSERT逻辑或宣称全局P2关闭。
- P3历史import残留保持非阻断；本轮不扩大清理。
- stage-assets仍为**144项且错误集合与进入本轮完全一致**，scanned1855 / reviewed_exceptions138；`DELIVERY_COMPATIBILITY_BLOCKER_PENDING_ROOT_CAUSE`。未同步hash、修改registry、扩大例外或关闭validator。
- 已认领且无Order死亡窗口不重复，但自动恢复未实现；CREATED保持fail-closed。新admission不授权猜测恢复资格。它不等于原R10修复失效，也不等于已证明该窗口可自动恢复。
- 所有新proof、manifest、secret negatives及辅助检查结果见[checks](l4-b5-admission-remediation-attempt01/checks.json)。真实venue/credentials/LIVE操作=0。
- Git：stage=0 / commit=NONE / push=NONE。无precise delivery授权、未运行CI、未继续qualification。

下一动作：`NQ-GATEAUDIT-PHASE6-L4-B5-STRATEGY-SAME-WINDOW-ADMISSION-INDEPENDENT-CORRECTNESS-REVIEW`。一次真正独立review只审本候选key/DB admission、双JVM竞争与直接恢复交互，不重审V49。**B5继续NOT_QUALIFIED**。


## 导出收尾的重复问题处理

首次最终artifact扫描exit2/findings6，全部是三份RACE proof中的A/B观察字段中的Java record文本；不存在随机身份原值或凭证泄漏。没有改低运行证据标准，也不新增B5专用exporter或scanner例外。修复现有`synthetic_evidence.py`，仅对两个已知观察字段且完整符合typed identity格式的值登记ADMISSION双射；未知形状和credential子树保持原文。

Python回归7 tests PASS，新增同key相同引用、不同window不同引用及完整逆映射；实际scanner补充两个未知密钥形状负例，均原值保留且REJECT。原六类secret negatives仍有效；最终全部源文件和artifact扫描exit0/findings0。原raw proof/hash不变，9份canonical proof重新导出并逐份全树逆映射相等。Full01在此修正前通过，最终Full02结果独立保留；不将第一次PASS记录删除或称作失败。

最终Full02：2026-09-10 07:36:07Z—07:37:57Z，1881/0/0/106 conditional skips，exit0；日志SHA-256=`434db6d95e512a4a885c78b6adfaec134ff9d12b4663ee266ab54585f48cf5a5`，原始backend manifest SHA-256=`df56a740159af790c91fe4bf475c795d667580ad5f7494eced3ef3b20ee35200`。1794个backend/CI/fixture文件无漂移，自有PG已核验清理。导出修正前的[Full01 PASS](l4-b5-admission-remediation-attempt01/full-01-before-exporter-correction.json)独立保留。
