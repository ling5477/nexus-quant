# Public Demo Registration Closeout

日期：2026-10-22（Asia/Shanghai）。本记录补充原注册实现证据，不覆盖其中保留的历史失败，不启动新的审计阶段。

```text
Task: NQ-V11-PUBLIC-DEMO-REGISTRATION-CLOSEOUT
Baseline: ae51c935c534155b42b26aa7c388333f0d370669
Branch: codex/nq-v11-public-demo-registration
Starting Tree: 7879bbf29642bd3271775aab8bbc36c970a68f51
Production Changes: NONE in closeout
Decision: READY_TO_COMMIT (local targeted checks only)
Public Email Delivery: NOT_QUALIFIED
Public Registration Deployment: NOT_READY
Self-Hosted Registration: UNAVAILABLE
LIVE: DISABLED
Commit: NOT_COMMITTED
Push: NOT_PUSHED
PR / Merge: NOT_CREATED / NOT_MERGED
Release: NOT_CREATED
```

最终暂存树及完整 Git 检查输出保存在本任务本地证据目录的 `final-git-checks.json`，并列于交付回复；不把文件自身所属树的散列递归写入本文件。原 78 个暂存 blob 逐项保持不变，本轮仅追加三个测试文件和本记录，共 82 个暂存路径。原实现记录及其生产候选均未改写。

## 失败定位

完整原日志：`C:\Users\Lingyu\AppData\Local\Temp\nq-demo-registration-test\self-hosted-regression.log`。已检查三个完整堆栈：

| 原失败方法 | 原调用位置 | 失败阶段 |
| --- | --- | --- |
| splitBudgetPostsBalancedCanonicalFactsAndRepeatsWithoutMutation | 原测试第 66 行 | 首次 fundOnce 的 assertAccountEnvironment，尚未插入经济分录 |
| replayAfterActualCanonicalTradeNeverResetsCashOrAsset | 原测试第 83 行 | 首次 fundOnce 的同一环境查询，尚未进入成交 |
| partialPostingFailureRollsBackBothCurrenciesReceiptEventsAndSnapshots | 原测试第 270 行 | 移除故障 trigger 后的重试；此前宽泛 assertThrows 也可提前捕获 Schema 错误 |

三者均为 `JdbcLedgerPostingRepository.assertAccountEnvironment` 第 64 行的实际 SQL 引用不存在的 `accounts.tenant_id`（别名 `ta`，位置 357）。旧 fixture 只迁移至 V14；Tenant 身份、归属、关系、执行控制、RLS 及后续一致性守卫来自 V35–V45。`SimAssetFundingService.fundOnce` 第 55 行在账务写入前调用该查询。`SimCashFundingService` 也依赖同一 Repository，不能直接拿现代读取在 V13 构造历史事实。

最终历史测试在真实 V14 数据库执行相同现代环境查询，明确断言 PostgreSQL 原始 SQLSTATE=`42703`、缺失 `ta.tenant_id`、前后经济快照一致。这是历史 Schema 与现代查询不兼容的诊断反例，不是 V14 运行现代服务成功的证据。

## 最小测试修复与证明对象

- V13 阶段继续由正式 `SimCashFundingService.fundOnce` 生成 100 USDT、对手分录、事件及快照，事务、账户/币种 advisory lock 和写入实现均沿用生产逻辑。没有手写余额替代注资。`HistoricalSimCashFundingFixture` 只适配三个历史读取方法，逐字冻结自租户化前提交 `82188a7e147c2c741fe354ce1d29e956f30f3e86`，构造时硬校验实际版本为 V13，不能用于现代运行证明。
- 历史阶段真实执行 V13→V14、Flyway validate、重复 migrate=0、版本=14、receipt=0，并逐行比较完整经济 JSON 快照（分录、快照、仓位、订单、成交、Ledger 事件）。原历史断言全部保留。V14 CHECK oracle 仍单独固定 V14，没有改成 V45。
- 历史证明完成后，同一数据库继续正向迁移至 V45 并 validate；现代 Repository/Service 仅在这之后运行。受限 runtime 与 NOLOGIN 安装 owner 分离，采用正式 `TenantTransactionManager`、`TenantJdbcTemplateFactory` 和从已持久化用户取得的 TenantContext。旧现金余额仍为 100，现金 fundOnce 重放及资产初始化拒绝均不改变原完整经济快照。
- 普通现代资金 fixture 使用当前 V45，补齐 backtest 的 strategy_version_id 与 publication/run 的真实父链一致性。未放宽账户/租户或资金归属限制。
- 故障 trigger 的安装/移除使用临时库安装连接；实际资金操作全部使用受限 runtime。回滚测试新增根因断言，确认分别命中 BNB 插入和 deferred commit 注入点，避免提前异常造成假阳性。
- 并发线程显式打开 TenantContext，连接池容量允许两个资金事务同时进入 PostgreSQL 锁等待。两个新 JVM 使用受限 runtime，从数据库重新解析用户成员身份，再读取和重放原 funding identity。
- 自审确认原断言没有删除，额外保留 SQLSTATE、真实注入点、旧现金重放和当前版本断言。没有修改任何生产代码、Migration、RLS、安全约束或 canonical Ledger 经济语义；仅测试夹具修复，不触发资金生产代码独立复核。

## 本轮执行

所有 Maven 命令从 `E:\Project\nexus-quant\backend` 执行，使用默认 Self-Hosted 构建。数据库为既有任务独占 PostgreSQL 16 容器 `nq-demo-registration-pg-20261022`，仅 loopback 55453；每个集成用例随机创建并清理自己的数据库和角色，不使用共享业务库。连接秘密从任务本地配置读取，未写入本记录或源码。

统一命令前缀：`mvn -pl nq-app -am test -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false`。资金 PG 场景附加 `-Dnq.postgres.smoke.required=true` 及该前缀的 url/user/password；Tenant 场景附加 `-Dnq.tenant.pg.required=true` 及对应 url/user/password。条件均实际启用，无 skipped。

| 执行范围 / -Dtest 选择 | 结果 | 日志 |
| --- | --- | --- |
| SimAssetFundingPostgresIntegrationTest#splitBudgetPostsBalancedCanonicalFactsAndRepeatsWithoutMutation+replayAfterActualCanonicalTradeNeverResetsCashOrAsset+partialPostingFailureRollsBackBothCurrenciesReceiptEventsAndSnapshots | 3/3 PASS，0 skipped | original-three.log |
| SimAssetFundingPostgresIntegrationTest（完整所在套件） | 11/11 PASS，0 skipped | funding-suite.log |
| SimAssetFundingPlanTest；TradeLedgerPostingServiceTest#simFundingPublishesExplicitCashProjection | 7/7 PASS，0 skipped | targeted-regression.log |
| TenantIsolationPostgresIntegrationTest#isolatesRootsAndReusesOneConnectionAcrossTenantsAndEmptyContext+bindsRequiresNewAndRollbacksWithoutLeakingOrSwitchingExistingTransactions+scopesImmutableEventsAndRefusesUnsafeRuntimeOrDirectMetadataMutation；TenantIsolationReviewRegressionIntegrationTest#firstJdbcOperationAlsoRejectsAnIncompleteRlsInstallation | 4/4 PASS，0 skipped | targeted-regression.log |
| SimAssetFundingPostgresIntegrationTest（自审统一同一事务管理器并保留失败清理后复验） | 最终 11/11 PASS，0 skipped，BUILD SUCCESS | funding-suite-final.log |

最终套件包含全部三个原失败方法，以及漂移拒绝、缺失事件/快照拒绝、非空/错误绑定拒绝、部分写入回滚、提交失败回滚、真实锁竞争、历史升级、CHECK 约束和进程重放。最后执行完成时间：2026-10-22 12:26:49 +08:00。

实际并发证据：`waiters=2`，随机数据库 `nq_baseline_test_43acac147827430984d7185f089e7a33`。最终独立 JVM 证据：parent PID=29796，child PID=50268 / 12548，各自 `unchanged=true`；父进程再次比较完整持久化经济快照。

```text
V13→V14 historical migration = PASS
Current Tenant Schema = PASS
Canonical Funding = PASS
Duplicate Funding = 0
Historical economic facts = UNCHANGED
P0 / P1 / P2 = 0 unresolved in this bounded closeout
```

本轮证据目录：`C:\Users\Lingyu\AppData\Local\Temp\nq-demo-registration-closeout`。没有重新执行全量 GateAUDIT、无关注册/前端回归或远程 CI；原注册候选 78 个 blob 保持原样，其此前通过证据仍以原实现记录的覆盖与限制为准。没有连接真实邮件、启动公网 Demo、创建 Release 或启动下一审计阶段。

## 证据身份

以下 SHA256 绑定本轮最终测试源码及实际日志。Git 检查包括 status、diff --check、cached --check/stat/name-only、write-tree；最终 receipt 同时验证原 78 个 index 条目（mode/blob/path）不变。

| 文件 | SHA256 |
| --- | --- |
| backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/HistoricalSimCashFundingFixture.java | `daa457f4f5be328b94aea87cd39ba27846a449f0ecb9f15a86d221c929c0fc16` |
| backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/SimAssetFundingPostgresIntegrationTest.java | `4b26f540e19caf72480d55ff35d1c4e3d0749d9670168b08bc0bcea9b9c945aa` |
| backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/SimAssetFundingRestartProbeMain.java | `e269c95028da7ef7cc5f284341444a42176daccf9c388198cf733ec925a6a01f` |
| original-three.log | `806829bdc08fcea4e8e42d862368f05c859203bd841beeca522e45b2d831fcb9` |
| funding-suite.log | `914337c8b5d9d60556f015bfb59a217c875cf677d081cb4721fdb84ddddf1278` |
| targeted-regression.log | `2b0e24eef405fa0a9a80fb2adbd92f060b3000473964028c977fffe6fd1903c3` |
| funding-suite-final.log | `ec23120c7ac2126bb5fca8c53d3b893f7cf667e725b46653489e14d3dfc20b17` |
| 原 self-hosted-regression.log（保留失败） | `add73b984722bc581dab96f23f282ccc932297cd3f4047d7d3f001faf929760b` |
