# B5 Contract B / V49 implementation attempt01

任务：`NQ-GATEAUDIT-PHASE6-L4-B5-FAIL-CLOSED-MUTATION-AUTHORITY-V49-IMPLEMENTATION`。2026-09-10，NQ-only。

**IMPLEMENTED / PENDING_INDEPENDENT_CORRECTNESS_REVIEW / B5_FAIL_CLOSED_MUTATION_AUTHORITY_IMPLEMENTED / V49_APPLIED / STALE_SENDER_MUTATION_P1_REMEDIATED / NO_FALSE_NO_ORDER_FINALITY / NO_SECOND_PLACE / P0_0 / LOCAL_P1_0**。最终冻结候选的第二次 Full Maven 已通过；P0/LOCAL_P1 为本轮实现验证结论，独立接受仍待审查。实现审查由作者完成，不构成独立正确性验收；B5 始终 `NOT_QUALIFIED`，没有继续 duplicate scheduler/ownership 等剩余资格矩阵。

## Authority 与候选身份

- 严格采用[已接受 Contract B](GATEAUDIT_PHASE6_L4_B5_DURABLE_MUTATION_AUTHORITY_AND_EXECUTION_FENCING_CONTRACT_REVIEW.md)：fail-closed uncertainty + durable one-shot mutation authority。不是物理 HTTP fencing。
- 起始/结束基线 HEAD 与本地 origin tracking ref：`86c8ad84542636364f6c21e78bc292a323cbdff7`，branch=`audit/post-gatey-agent-baseline`；未 fetch、stage、commit、push。
- [候选 manifest](l4-b5-v49-implementation-attempt01/candidate-manifest.json)覆盖 30 个 production/test 工作树文件，含本轮依赖的既有未提交 harness。8 个 production 文件；manifest 原始字节 SHA-256=`d1be8d6022fe52ed6eafc24274eda43e0a270e0f357a916c59ba2b9a0e39d9be`。文档不参与此 runtime/test 指纹，避免自引用。
- 原[B5 FAIL](GATEAUDIT_PHASE6_L4_B5_DUPLICATE_COMMAND_SCHEDULER_LOCK_MULTIPROCESS_OWNERSHIP_QUALIFICATION.md)、[旧 remediation STOP](GATEAUDIT_PHASE6_L4_B5_ORDINARY_SENDER_RECOVERY_FINALITY_REMEDIATION_ATTEMPT01.md)及 contract review 保留，不以本次绿色覆盖 V48 的真实红色事实。

## V49 与持久化边界

`ordinary_place_authorities` 只有 `order_id VARCHAR(64)`、`state VARCHAR(24)`、`decided_at TIMESTAMPTZ` 三列。没有 lease、PID、epoch、generation、heartbeat 或 scheduler ownership。

| 起点 | 终点 | 条件与事务所有者 |
| --- | --- | --- |
| 不存在 | NOT_ARMED / 时间为空 | security-definer create helper 原子 INSERT **新** Order 与 authority；已有 Order 唯一冲突失败，不能补 NOT_ARMED |
| NOT_ARMED | MAY_HAVE_ESCAPED / DB 时间 | 原 sender 的 arm helper 成功 CAS；Kill→Order→authority；核对 SENT/version、同 prepare 的 durable Risk ALLOW |
| NOT_ARMED | REVOKED_BEFORE_SEND / DB 时间 | recovery 的 revoke helper；Order→authority；同事务完成 canonical CANCEL_REQUESTED→CANCELLED/version/events |
| MAY 或 REVOKED | 无后续决定 | 吸收态；逆转、交叉转换、时间重写、删除、TRUNCATE 拒绝 |
| legacy/missing | 保守 MAY | owner-only bounded backfill，每次 1..500 行；中断/回滚可重试，冲突不覆盖既有决定 |

PK/FK 保证一单一行及 parent 删除限制。CHECK 限定 state/time 配对；trigger 保证不可逆与绑定 Order 的 account/client/venue/environment/payload 不可变。runtime authority DML 只通过 helper：即使错误授予表 INSERT/UPDATE，非 owner 的直接写入仍被 trigger 拒绝。应用角色没有 owner/superuser/DDL 权限；DDL owner/超级用户不属于本协议对抗模型。

helper 的 search_path 固定为 `pg_catalog,<migration schema>,pg_temp`，调用方临时表不能覆盖真实 Kill/Order/authority。调用者必须具有 Order INSERT/UPDATE 权限，reader 不可消费 helper。函数 lock_timeout=5s；migration DDL lock_timeout=5s、statement_timeout=60s。只有 PK index，risk fact 检查复用既有 `(scope,scope_id,created_at)` index；没有新 worker 或 authority 全表调度。

撤销使用 deferred constraint trigger 验证同提交的 Order 为 `CANCELLED / ORDER_NOT_FOUND/OKX_51603`；它只保护原子关联，不把自由文本 reason 当作真实 venue 证据。真实 absent 的来源仍是生产 recovery query-confirm。

本轮在自有 disposable PostgreSQL 16 上应用 V49；不是生产迁移。部署必须先停止旧 ordinary sender/recovery writer，整体切换兼容版本，禁止旧版本混跑；不能回滚到忽略 authority 的旧二进制。历史缺行在回填前后都保持 uncertain。真实存量规模、部署停机预算、生产数据纠错均未验证或执行。

## Production ownership 与行为

`nq-core` 的 domain port 定义创建/arm/revoke；`nq-infra` JDBC 仅在调用者事务内执行 helper。`OrderCommandWriteService.preparePlaceOrder` 对 ordinary OKX 原子创建 Order+NOT_ARMED，并在原事务运行真实 RiskGate、risk/event/audit 与 SENT/v2；PAPER 等既有入口保持原持久化路径。

`OrderCommandService.placeOrder` 在 prepare 返回后调用 `REQUIRES_NEW armOrdinaryPlace`。只有 affectedRows=1 且 Spring 事务提交正常返回的同一次调用会调用 gateway 一次。任何异常、rollback、definitive rejection、COMMIT response loss 都不会返回发送许可。读回 MAY 只用于事实恢复，不恢复调用栈的许可；duplicate account/client 直接复用 durable Order，不再 arm、不换 client ID。

`OkxRecoveryService` 的 absent finalizer 改为 `OrderCommandService.finalizeOrdinaryNoOrder` → WriteService 的短事务。revoke 与两个合法 Order CAS、OrderStatusChanged 在同一提交；MAY/missing 或 CAS 失败时保留实际非终态并记录 `MANUAL_RESOLUTION_REQUIRED`。正向 venue/Trade/账务恢复继续走原 reconciliation owner；scheduler 不再自己拥有 negative trading finality。

补充关闭同类分类入口：ordinary OKX gateway 将 NOT_FOUND、损坏 JSON、缺少 ACK data、缺少拒单事实保持 DEFERRED；明确 venue 业务拒绝继续保留原 fatal/reject 分类。`INVALID_JSON` 的旧 FATAL 映射不能再在已经 arm 后制造确定拒单。真实 HTTP 损坏 ACK 场景验证其后可按 venue fill 收敛。

所有数据库事务在进入网络前结束。confirmed MAY 后原调用可以迟到 PLACE；Kill ENGAGE 不撤回已确认的 in-flight 权限，不自动 CANCEL。Kill 在 arm 前生效时不允许新的 arm，query/reconcile 继续可用。MAY 后死亡可能长期 unresolved，这是 Contract B 接受的可用性代价，不是可重授 lease 或死 owner 锁。

## 真实证明与 oracle

测试只使用 pinned PostgreSQL 16.15 自有容器、真实 Spring/RiskGate/transaction proxy、独立 NQ A/B JVM 与独立 Synthetic Venue HTTP JVM。故障 proxy 只控制 PostgreSQL COMMIT 包/响应；不授权 PLACE、不修复业务行。场景 controller 对业务数据使用 read-only checker。数据库对抗测试的 SQL fixtures 单独标明，不冒充生产链。

| 场景 | 必需且实际检查的 oracle |
| --- | --- |
| 原 P1 / recovery wins / stale resume | A prepare SENT/v2+NOT_ARMED；B atomic REVOKED+CANCELLED/v4；A 恢复，PLACE requests/effects=0 |
| Sender wins | A confirmed MAY 后暂停，B absent 不得 CANCELLED；原 A 仅1次 PLACE；重复命令0次新增；最终 fill→FILLED、Trade1、TradeExecuted1、Ledger4 |
| 三轮近同时竞争 | controller CyclicBarrier 同时释放 A 与调用 B recovery；无固定 sleep 决定胜负；仅 R胜/0PLACE 或 S胜/1PLACE，不能双方成功 |
| MAY 后死亡 | A 未 HTTP 即死亡；B 和死亡后新启动 JVM 都只查询，duplicate仍 SENT/MAY、0PLACE，无死 owner 锁 |
| Kill before arm | prepare 后 ENGAGE；A恢复不 arm、0PLACE；recovery 在 Kill 下可撤销并原子终态化 |
| Authority ROLLBACK / REJECT | 真事务回滚 / deferred PG commit拒绝；独立reader为 NOT_ARMED；原调用0PLACE；重复不发送；B可撤销 |
| Authority BEFORE_DROP | COMMIT未转发即断连；原调用失败且0PLACE；独立DB确认未提交后B可撤销 |
| Authority AFTER_DROP | server COMMIT成功响应被截断；原调用失败且0PLACE；独立DB为MAY；B不能终态化或重新PLACE |
| Malformed ACK | venue真实接受1次，返回损坏JSON；DB保持SENT/MAY；Kill下查询成交恢复，不制造REJECTED |
| DB constraints | six concurrent sender/sender or sender/revoke races exactly one winner；权限、temp hijack、缺Risk ALLOW、reverse/delete/truncate/payload修改、missing与bounded backfill rollback/replay拒绝/收敛 |
| B1 affected | accepted-timeout、lost ACK各一轮；同client query-first，无blind PLACE retry |
| B2 affected | STALE_PLACE 与 MULTI_PARTIAL，各在SIM/LIVE **合成domain数据**运行；OCC、每fill唯一Trade/事件/账务及环境保持；无真实LIVE执行 |
| B3 affected | PRE_ACCEPT、RESTART_PRE_ACK各一轮；Kill不撤回in-flight、不自动撤单、允许恢复 |
| B4 affected | 原 Trade commit/process death回归与PG原子事件/并发/环境证明；不是完整B4重新qualification |

导出后的进程 proof 位于本文同级 `l4-b5-v49-implementation-attempt01` 目录；原始日志和 raw-proof 保留在 `backend/nq-app/target`。场景/PID/version/authority/venue count 以各 proof 为准，日期与随机身份的规范化经过既有 exporter 的逆变换等价校验，不映射秘密字段。

## 验证尝试账本

| Run / target log | 观察结果 |
| --- | --- |
| b5-v49-targeted-01 | test编译失败：旧构造调用多余参数；已修复 |
| b5-v49-targeted-02 | test编译失败：缺少Mockito静态import；已修复 |
| b5-v49-targeted-03 | 相关模块目标测试通过；真实PG测试在Docker daemon未启动前环境错误，未进入V49 |
| b5-v49-compile-04 | reactor test-compile PASS，未运行业务测试 |
| b5-v49-pg-05 | Docker启动故障期间CLI timeout/临时日志占用错误；环境阻塞，不冒充schema失败或通过 |
| b5-v49-targeted-06 | V49实际应用成功，fixture旧latest=48断言失败；更新运行harness的latest断言，不改历史proof或固定V48迁移测试 |
| b5-v49-targeted-07 | 3个JUnit tests PASS，内含原P1、9个进程场景与6轮DB竞争；早期候选证明，不替代最终候选 |
| b5-v49-affected-08 | 相关单测及B1–B4受影响回归PASS；早期候选证明 |
| b5-v49-final-targeted-09 | 新分类单测fixture漏填required adapter字段，提前REMOTE_UNAVAILABLE；1 failure；补齐fixture，未削弱期望 |
| b5-v49-final-targeted-10 | 全部受影响单测、B1-B4、DB约束及原P1 PASS；B5扩展场景在同时首次启动时遇到既有用户bootstrap唯一键竞争，overall FAIL（1 failure）；未进入该场景业务竞争 |
| b5-v49-final-targeted-11 | PASS：3个JUnit tests，含原P1、11个进程场景、六轮PG竞争及权限/约束/回填对抗；3m23s |
| b5-v49-full-maven-01 | 第一次Full：1852 tests / 0 failures / 12 errors / 97 conditional skips；FAIL，保留原始结果 |
| b5-v49-full-failure-remediation-12 | 13 tests / 0 failures / 1 error；交易链8、跨JVM重启2、local context2通过；research因空测试库缺少legacy account前置条件失败 |
| b5-v49-full-failure-remediation-13 | PASS：research目标1 test / 0 failures/errors；19.664s。配合第12轮，首次Full所有失败测试均已逐项获得目标PASS，但首次Full仍为FAIL |
| b5-v49-full-maven-02 | PASS：最终冻结候选，1852 tests / 0 failures / 0 errors / 96 conditional skips；exit0，1m44s；canonical CI fixture + 隔离PG16/V49 |

B5启动夹具先等待A完成一次性bootstrap，再启动B；两者在业务屏障前均已ready并同时存活，业务CAS仍真实竞争。未修改production用户初始化；此startup limitation不扩大为剩余B5 qualification整改。

本机 Docker 的残留 AF_UNIX socket 启动错误已通过保留临时目录备份并重建空运行目录解决；没有 factory reset、镜像/volume清理或凭证读取。Docker 29.7.2，容器镜像仍使用仓库锁定的PG16 digest。工具自身自动更新提示不构成本轮更新授权，未执行更新。

## 最终验证与 disposition

### Full Maven 失败分类与修复边界

[Full结果](l4-b5-v49-implementation-attempt01/full-maven-01.json)：`1852 / failures0 / errors12 / skipped97`；此首次失败记录保留。它不代表后续测试夹具修复后的候选；最终回归结论由下面独立的第二次 Full 给出。

- 7 errors：`TradingChainPostgresIntegrationTest` 的外层测试事务持有账户/Kill fixture和prepare锁，新的REQUIRES_NEW arm不能取得锁，SQLSTATE55P03。属于本candidate引入的测试事务边界兼容失败，不能统称环境错误。测试改为在ordinary PLACE前提交fixture；独立schema仍负责清理，没有为迁就测试改变production事务或锁保护。
- 2 errors：两个旧restart测试要求显式`SPRING_DATASOURCE_URL`，首次Full未提供。
- 3 errors：local context使用默认local datasource，该既有数据库在V45的legacy account FK上失败；本轮没有改写旧migration或修复该既有数据库。后续目标证明仅用新建、loopback绑定、自有PG16容器，并显式传入datasource。
- 在干净测试库上，research happy-path另外显式要求至少一个legacy account。按其前置合同只在本轮新库seed一行测试账户；不扩大成research/历史schema整改。

Full实际使用的[旧测试manifest](l4-b5-v49-implementation-attempt01/full-maven-tested-manifest.json)保留，SHA-256=`d83cb4e6d103fa88d5fad7e915224a93f490e35ffc40b87be15237569e0f88bf`。Full后只改一个test fixture；8个production文件哈希完全相同。当前manifest与Full测试manifest不能混用。B5第11轮证明与B1-B4第10轮的有效子集覆盖的是相同production candidate；它们不替代失败的Full。

97 skips：49项显式system-property opt-in、48项其他条件跳过；本任务mandatory B5/相关B1-B4证明已在目标运行中实际执行，不以默认Full中的skip冒充通过。

### 证据卫生与未完成项

12份B5 canonical proof逐份逆映射等价PASS；[proof index](l4-b5-v49-implementation-attempt01/proof-index.json)保存raw/proof各自SHA-256。所有raw数据留在target。三个近同时进程race均实际为sender wins；recovery ordering另由确定性原P1和PG互斥竞争覆盖。补充[B1-B4 proof引用](l4-b5-v49-implementation-attempt01/affected-regressions.json)保存10个实际进程proof路径/哈希及venue计数。

既有exporter单测5/5 PASS；pinned Gitleaks8.18.4工作树扫描3405文件、0 findings，6种credential负例全部REJECT。新增源码/完整本文/canonical artifacts另用相同pin和原CI配置扫描。没有修改scanner policy、allowlist或.github。

最终卫生检查：stage-assets=`scanned1837 / reviewed_exceptions173 / errors0`；新增30个源码/测试文件、完整本文与全部canonical JSON、lesson的pinned Gitleaks扫描=`exit0 / findings0`；UTF-8/JSON解析、candidate/proof指纹及`git diff --check`通过；[测试日志索引](l4-b5-v49-implementation-attempt01/test-runs.json)保存各次原始日志SHA-256。所有本轮测试子JVM已退出，B5场景容器与后续回归容器已按精确owned identity移除。stage=0 / commit=NONE / push=NONE；真实exchange/资金操作=0。

### 第二次 Full Maven：最终稳定候选的有效回归

用户于本轮明确澄清：“只运行一次”指最终稳定 candidate 的一次有效 Full Maven；首次失败后夹具/前置条件变化，需要代表最终候选的绿色全量运行。本轮按该授权执行第二次 Full；不是将定向重测替代第一次失败。

[Full02 结果](l4-b5-v49-implementation-attempt01/full-maven-02.json)：**1852 tests / failures0 / errors0 / skipped96 / exit0 / BUILD SUCCESS**，用时1m44s。命令仍为 `mvn -f backend/pom.xml test`。起止 UTC 时间及日志SHA-256见JSON；原始日志保留在 `backend/nq-app/target/b5-v49-full-maven-02.log`。

运行前执行 status、diff-check、diff-stat 并冻结[Full02 tested manifest](l4-b5-v49-implementation-attempt01/full-maven-02-tested-manifest.json)，SHA-256与当前30文件manifest相同：`d1be8d6022fe52ed6eafc24274eda43e0a270e0f357a916c59ba2b9a0e39d9be`。额外冻结1772个后端/CI配置文件；运行后逐文件校验无变化。本轮没有修改production或test，后续仅收尾证据和既有lesson表述。

全新、自有、loopback随机端口、tmpfs PostgreSQL16.15实例，镜像使用canonical CI锁定digest。复用 `scripts/ci/BackendCiLegacyAccountFixture.java` 及CI相同classpath构建命令，正常迁移并validate到V49、pending0，确认canonical PAPER ACTIVE账户1、exchange_accounts0、credential0。只从白名单继承必要OS变量；NQ fail-closed/placeholder值按backend CI设置，datasource aliases仅作用于Full子进程；未继承SPRING_PROFILES_ACTIVE、reviewer profile、Java/Maven options或机器NQ配置。测试结束按容器ID/name/label/image/port核对所有权后移除实例。

[96个skips明细](l4-b5-v49-implementation-attempt01/full-maven-02-skips.json)：49项显式system-property opt-in，47项其他assumption条件。B5三项在默认Full中属于 `nq.b5` opt-in，**不将该skip记为mandatory PASS**；mandatory真实执行证据来自最终目标第11轮：3 tests / 0 failures / 0 errors / 0 skips，包含原P1、11个进程场景、六轮PG竞争及权限/约束证明。该运行后B5 production/test/harness/依赖配置均未变，后续修改仅为另一个测试类TradingChain的独立schema fixture；本轮重新核对该日志SHA及12份raw/canonical proof指纹，复用依据与Full02共同记录。B1–B4受影响证明保留第10轮有效子集，未扩展剩余qualification。

Full02模块汇总与原始日志计数一致。两个architecture类分别由JUnit/ArchUnit执行，后一个引擎覆盖同名XML；XML合计1847，日志保留先前引擎额外1+4项，合计1852。`backend/nq-app/target/b5-full02-surefire-index.json`保留归档XML指纹、同名引擎明细及skip分类，不从旧XML虚增测试数。首次1852/0/12/97 FAIL保持不变。

最终候选已具备独立正确性审查交接所需的manifest、原故障及修复证明、绿色Full和失败历史；本任务状态为PENDING_INDEPENDENT_CORRECTNESS_REVIEW，B5仍NOT_QUALIFIED。

全量缺口现已关闭，下一独立正确性审查动作是：`NQ-GATEAUDIT-PHASE6-L4-B5-FAIL-CLOSED-MUTATION-AUTHORITY-INDEPENDENT-CORRECTNESS-REVIEW`。本轮没有独立 reviewer、exact-head CI、提交或发布，不将 implementation proof 写成 qualification/acceptance。
