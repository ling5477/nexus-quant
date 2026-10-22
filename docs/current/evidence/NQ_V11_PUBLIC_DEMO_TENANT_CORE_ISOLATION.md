# Tenant Core Isolation 实现与验证

任务：`NQ-V11-PUBLIC-DEMO-TENANT-CORE-ISOLATION`。基线为 Foundation PR #69 合并提交 `82188a7e147c2c741fe354ce1d29e956f30f3e86`；分支为 `codex/nq-v11-public-demo-tenant-core-isolation`。这里只记录本地实现和隔离证明，不代表 Git 交付、公网准入或发行接受。

## 实现边界

- V36 为 accounts、exchange_accounts、strategy_definitions、strategy_versions、research_configs、backtest_configs、paper_trading_runs、marketdata_datasets 建立不可重绑定归属；历史归属 NQ_LOCAL。策略代码和版本序号改为 Tenant 内唯一，全局主键保持。
- V37 为跨父对象关联和审计、事件、风险事件旁表建立一致性检查。无法证明归属的历史事件保留内部分类，不根据 actor、trace 或 payload 猜测。
- V38 实现执行控制行、epoch、后台身份和容量投影。经济提交持有同一控制行 SHARE 锁，暂停更新与之冲突。重新启用必须显式继续；旧 epoch 不能获得新资格。
- V39 对私有根表、派生事实、费用回执和事件实施 FORCE RLS。七个原 canonical SECURITY DEFINER 保留原状态机正文，只增加租户执行检查、固定 search_path 和撤销 PUBLIC EXECUTE。
- V40、V41、V42、V43 分别补齐后台扫描、关联约束、发布内部修订及对账工作作用域。游标和工作表只保存可信身份，不保存第二份订单状态、资金或收益。扫描先过滤资格再 LIMIT，持久游标轮转，每次最多 64 项。
- V44 将只读对账与经济推进资格分开：暂停及停用账户仍须核验；租户自身停用导致不可读取时明确报错，不能报告账务一致。受限函数和 Tenant 辅助函数显式将 `pg_temp` 放在搜索路径末尾。
- Repository 的详情、列表、分页 COUNT、版本号和更新继续使用原业务算法，并叠加显式 Tenant 谓词。SIM 分析、成交、Ledger 和收益继续读取 canonical 经济事实。

## 数据库连接合同

数据库迁移须先由独立安装身份执行，业务应用使用另一运行身份，不能让业务运行账号拥有 schema、表或函数 owner 的继承／切换权限。RLS 表及受限函数 owner 必须为 NOLOGIN、NOSUPERUSER、NOBYPASSRLS、NOCREATEROLE、NOCREATEDB；运行身份还必须无 SUPERUSER、BYPASSRLS、CREATEROLE、CREATEDB。

应用启动检查八根 FORCE RLS 与角色拓扑，并拒绝运行账号在 public schema 创建对象。运行账号不能直接读写后台工作／容量／游标表，不能修改执行控制、事件旁表或共享许可；只按显式授权调用原 canonical 函数及工作 poll／restore、容量、继续函数。隔离测试 helper 展示了实际权限装配，不是生产数据库安装授权。

所有受管 JDBC 操作在 Spring 同连接事务中绑定 transaction-local `nq.tenant_id`、`nq.user_id`、`nq.execution_epoch`。无业务身份也明确清空 GUC，禁止默认回落 NQ_LOCAL。REQUIRES_NEW 独立绑定，不能在已开始的事务中切换租户。启动时拒绝不安全角色，避免裸 JDBC 消费者借 owner 绕过 RLS。

后台从持久业务身份恢复 Tenant 和 epoch，作用域在 finally 清理。Continuous SIM 捕获身份覆盖网络读取及后续执行；Matching、Strategy dispatch／recovery／schedule、Ledger reconciliation 复用原业务链逐项恢复身份。容量仍为全局八个、每 Tenant 一个活跃 Continuous SIM。

## Demo API 准入

`nq.demo.tenant-business-enabled` 默认 false；开启也只接受 `DemoTenantApiPolicy` 逐方法列出的研究、版本、回测、评价、发布、canonical SIM 只读及 Continuous stop／resume。没有目录通配准入。

研究数据快照从服务器可读 Dataset 重建。当前 Demo 只接受已验证的 DB 数据描述符；私有描述符按 Tenant，公共原始 bar 还须受有效共享许可窗口约束。任意 fixture／文件路径及未验证的 public-capture 输入保持拒绝。共享目录没有自动发布入口。

Demo 研究定义仅使用本租户 PAPER 账户和已支持的 SMA executable；研究账户检查不授予交易环境资格。实际执行继续要求原 canonical 运行／账户绑定，不改变旧账户桥接保护。

旧通用 SIM 创建、自动注资、research creation operation、全局 Scheduler、账户／凭据、原始 Paper 别名、LIVE、真实下撤单和运维接口均保持拒绝。本任务测试只能经隔离夹具和原受控资金服务建立独立实例，不能作为后续公共注册或初始化实现。

## 可复验命令与证据

任务专用 PostgreSQL 16.15 容器为 `nq-tenant-core-pg-20261021`，每例建立独立数据库及随机 NOLOGIN owner／受限运行角色。端口使用 `docker port nq-tenant-core-pg-20261021 5432/tcp` 获取，不能复用旧动态端口。密码及运行连接材料不写入本文。

```powershell
mvn -f backend/pom.xml -pl nq-app -am test `
  '-Dtest=TenantIsolation*IntegrationTest,TenantIdentityTest,StrategyDefinitionServiceTest,StrategyVersionServiceTest,StrategyScheduleScanServiceTest,OrderCommandStrategyExecutionGatewayTest,TradeLedgerPostingServiceTest,PaperMatchingServiceTest,SecurityConfigurationContextTest,PaperRunReadAccessTest,BacktestConfigSnapshotPrecisionTest,ResearchConfigListQueryTest,BacktestConfigListQueryTest,ContinuousSimOperationServiceTest,ContinuousSimReadControllerTest,JdbcStrategyDefinitionRepositoryTest,JdbcLedgerPostingRepositoryTest,JdbcLedgerReconcileRepositoryTest,JdbcBacktestEvaluationQueryPortTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' `
  '-Dnq.tenant.pg.required=true' `
  "-Dnq.tenant.pg.url=$taskPostgresUrl" `
  "-Dnq.tenant.pg.user=$taskInstallerUser" `
  "-Dnq.tenant.pg.password=$taskInstallerPassword"
```

覆盖以下真实路径：

1. A、B、NQ_LOCAL 认证和同策略代码，版本→研究→回测→两次独立 Evaluation→发布；跨父对象、私有 Dataset、分页／COUNT、ID 枚举拒绝，旧费用来源范围保留。
2. 原资金→Risk／Order→Matching→Trade／Ledger／Position／Performance；A、B 资金独立；重复撮合和重启无重复资金事实。七个受限函数分别尝试跨租户直接调用并拒绝。
3. 单连接 A→B→无身份、非法 GUC、owner 强制 RLS、异常回滚、同事务切租户拒绝、REQUIRES_NEW 与不安全运行角色拒绝。
4. 真实 Trade 写入屏障下的暂停竞争，网络读取期间暂停，重新启用仍需手动继续，旧 epoch 拒绝；九租户并发仅八个启动成功，全局空出名额仍不能绕过每租户一个。
5. 两次新 JVM：暂停资格持久保留；另一个租户恢复既有订单撮合；再次重启经济摘要不变。后台对账继续按可信账户身份执行。
6. V35 丰富历史夹具及 1,006 个账户分批升级，既有业务列和 V1–V35 checksum 保持；V36 故障注入完整回滚，免 repair 重试；真实 pg_dump／pg_restore 后摘要一致。

最近完整定向回归为 23 个测试类、80 例，失败／错误／跳过均为零，其中真实 PostgreSQL 集成用例 12 例，含分页、COUNT、私有数据集及七个 SECURITY DEFINER 的跨租户断言。日志 `repaired-candidate-regression.log` 和独立 JVM 输出保留于 `backend/nq-app/target/tenant-isolation-evidence/`，历史失败与后续成功分别保留。独立正确性检查单独报告，不能从测试通过推导其接受结论。

独立开发 schema 清单对应 V44：114 表、1,454 列、842 约束、392 索引、171 函数、153 触发器。全新安装 catalog、Flyway history、seed 与独立 verifier 一致，schema SHA256 为 `79860c6ceb3b6a205d5c09cae8a6eb6b5529490466af9caa5f9162ddf7532f15`，seed SHA256 为 `449252f799e76fb46110a9ef900aa6a74dfe9fc82d5ebe455fdc5c3bb39ce230`。清单仅描述开发候选；V1.0.0 历史清单、Tag、Release 及 V1–V35 源文件不变。

## 独立发现与兼容性状态

初次独立检查绑定 staged tree `36803ea6e18257fa5d908ed88e37e970cf80ea2d`。原始候选补丁及复现日志保留在上述证据目录，不能由修复后的 PASS 覆盖。

- 历史 NQ_LOCAL 可合法存在多个活跃 Continuous SIM，初始 V38 的唯一索引会阻断升级。仅修正本任务尚未提交的 V38 索引谓词，保留既有本地运行；新准入仍由串行容量函数限制为每 Tenant 一个及全局八个。后置迁移无法修复此前失败的 V38，因此另建全新数据库重做升级、备份恢复和历史摘要验证，没有 repair 已存在数据库的 checksum。
- 暂停租户的账户曾被后台对账过滤。V44 只读账户核验不再使用经济推进过滤，并验证暂停、账户停用时仍记录既有差异，租户不可读时明确失败。经济写入仍受暂停和 epoch 控制。
- 临时关系曾能影响受限函数的控制表解析。V44 固定搜索路径，真实同池临时表反例现被拒绝；public CREATE 权限也由运行角色检查拒绝。
- JDBC 对账仓储新增账户绑定参数后，旧 mock 验证签名失配；已更新参数匹配，保留原资金语义断言并补充 Tenant 谓词检查。

现有 `StrategySimPostgresIntegrationTest#continuousClosedBarPersistsCursorAndRecoversLostAcknowledgmentWithoutDuplicateFacts` 仍将完整新应用连接到 V27 schema 和 owner／安装身份，实测 1 例、0 failure、1 error、0 skipped，被运行角色检查拒绝，日志为 `legacy-strategy-sim-compatibility.log`。这套旧 CI 夹具尚未适配当前 schema 与分离运行身份；不能报告现有 CI 全绿。本文新 HTTP、Continuous SIM 和独立 JVM 证明均使用最新 schema 与受限运行身份。

独立检查代理已返回上述三项实质发现，但报告生成被平台中断。修复后候选 `b4e02fd4489f142d18e2cd6e487cbd9b6eb65f38` 的有界补查也被平台以“可能涉及网络安全风险”为由中断，当前不存在完整独立接受结论，不能将实现者复测冒充独立审查。

## Core 实现交接时结论（Closeout 前）

- Baseline／Starting HEAD／Current HEAD：`82188a7e147c2c741fe354ce1d29e956f30f3e86`；Foundation PR #69 已合并，前置合并后 CI `39603339999` 为 9/9 SUCCESS。
- Implementation：八根归属、关联约束、Research／Backtest／Evaluation／Publication、canonical SIM／Trading／Ledger、RLS、后台身份恢复及执行控制均有本地实现和上述定向证据。尚不能将该结论提升为独立接受或现有 CI 兼容。
- Migration：V36–V44；V1–V35 源码和历史 checksum 保持，旧业务列／经济事实／冻结快照摘要保持，隔离备份恢复通过。
- Verification：单元与仓储用例 68 例、真实 PostgreSQL 集成用例 12 例，共 80 例通过；完整 HTTP、暂停竞争、容量并发、两次独立 JVM 恢复及 canonical 经济回归已执行。另有旧 CI 夹具单例 1 error，单独保留，不混入 PASS 计数。
- Independent correctness review：三项真实发现已由实现者修复并复测；最终独立结论与修复关闭确认 UNAVAILABLE。P0／P2 没有已返回的报告项，但不代表完整审查排除；三项实质发现不能仅凭实现者测试标记为独立 CLOSED。
- Decision：`BLOCKED / 独立检查未形成最终结论；旧 CI 夹具兼容性尚未闭合`。
- Files Changed：62 个文件，均精确暂存；最终 tree 由 Git 收尾输出记录。原始候选及修复后路径清单存于本地 ignored 证据目录。
- Commit：NOT_COMMITTED；Push：NOT_PUSHED；PR：NOT_CREATED；Release：NOT_CREATED。
- Next Task：`NQ-V11-PUBLIC-DEMO-REGISTRATION-SIM-INITIALIZATION`，NOT_STARTED。先闭合当前任务的剩余验收条件。

## 停止与恢复约束

已出现多租户事实后，禁止让不理解租户隔离的旧应用接管新 schema。发生问题时先关闭 Demo 业务准入和后台推进，保留全部事实，使用新的正向迁移修复；备份恢复验证只在任务自有数据库执行。

公共注册：DISABLED。Public Demo：NOT_READY。LIVE：DISABLED。本任务不 commit、push、创建 PR、merge、Tag 或 Release，不进入 Registration／SIM Initialization。


## Closeout：CI 夹具兼容性与最终检查（2026-10-22）

任务：`NQ-V11-PUBLIC-DEMO-TENANT-CORE-ISOLATION-CLOSEOUT`。起始 HEAD 为 `82188a7e147c2c741fe354ce1d29e956f30f3e86`，起始 62 文件暂存树为 `bc674271266e1749695bc1ae123d4ab817bdef30`。原暂存候选完整保留，最终树由本轮 Git 输出记录；本节更新交接时的兼容性状态，前文失败和审查中断记录继续保留。

### 修复与边界

- Currency 夹具先由 NOLOGIN owner 执行 V26→V27，保留 fingerprint、UNKNOWN 与非法写入 SQLSTATE `23514` 断言；历史检查结束后才升级最新 schema，再以受限 non-owner 启动当前应用。没有把历史升级目标替换成 V44。
- Strategy SIM、估值、快照、研究、AppContext、仓储、TradingChain 与跨 JVM 恢复夹具使用独立数据库和受限运行角色，安装身份只准备 schema、身份元数据及故障注入。显式 Local fixture scope 只用于本地测试，不给应用添加默认租户。
- 保留旧错误 Trade 的恢复防御：V35 独立库持久化原反例，V37 拒绝升级且历史行不变；历史只读仓储作为恢复输入，当前 canonical Order／Recovery／Audit／Ledger 仍在最新 schema 执行。当前 schema 自身拒绝不一致 INSERT（23514），没有关闭约束或改写经济事实。
- 原重复注资、金额和费用精度、HTTP 跨 owner 404、容量八个／每 Demo Tenant 一个、恢复两次无重复账务等断言保留。注资引用建立真实同账户父链，资金仍通过原资金服务。
- 生产兼容修复一：只读诊断 profile 保留启动零数据库访问；取得连接时执行完整角色、八根 FORCE RLS 及内部 metadata 权限检查。普通应用仍在启动检查；受管事务也逐次检查完整安装状态。
- 生产兼容修复二：local/test 配置身份由安装身份预置，运行账号仅校验密码 hash、enabled 和 roles；缺失或不一致拒绝启动，不再写只读角色元数据。兼容影响：旧本地库必须先由安装身份准备匹配的用户，不能仅以运行身份首次启动并自动 seed。
- frontend-critical 的一次性 CI 入口 `LocalTenantRuntimeFixture` 复用同一隔离夹具、读取实际 local 配置并预置用户；后续应用使用随机受限角色及已迁移数据库，Flyway 在业务进程禁用。该入口不是生产安装器，也不启用公共注册。
- 测试应用先停止上下文后台任务再销毁其数据库，避免连接池向已删除角色重连。历史告警和首次失败日志均保留。
- V1–V44 migration 与 V1.0.0 Release 文件相对起始树字节一致；开发 schema/source 清单相对起始树也没有修改。没有修改 canonical 经济算法或创建第二份事实源。

### 验证记录

所有日志位于本地 ignored 目录 `backend/nq-app/target/tenant-closeout-evidence/`；此前 Core 的 80/80 证据继续保留于 `tenant-isolation-evidence/`，不把重跑重复计为新增覆盖。

| 检查 | 命令与结果 | 证据 |
| --- | --- | --- |
| V27、owner/non-owner、Tenant PostgreSQL | `mvn -f backend/pom.xml -pl nq-app -am test -Dtest=TenantIsolation*IntegrationTest,CurrencyValuationPostgresIntegrationTest,StrategySimPostgresIntegrationTest,AccountSnapshotSemanticsPostgresIntegrationTest,OkxAccountFactsPostgresIntegrationTest,SchemaSemanticConsolidationPostgresIntegrationTest,RecoveryScanCursorPostgresIntegrationTest,NqAppContextPostgresSmokeTest,JdbcRepositoryPostgresSmokeTest,ExistingUserPasswordRotationPostgresTest -Dsurefire.failIfNoSpecifiedTests=false`；各 PostgreSQL required/url/user/password 参数指向任务库。EXIT 0；73 例、0 failure、0 error、1 skip（需额外手工 UX 条件的原用例）。 | `final-postgres-regression.log` |
| 真跨 JVM 与历史 Trade 拒绝 | `mvn ... -Dtest=TradingRestartRecoveryPostgresIntegrationTest,TradingChainPostgresIntegrationTest#rejectsRecoveryWhenDurableTradeDoesNotBelongToOwningOrder`（准确 selector 见日志）；EXIT 0；3/3。 | `restart-and-legacy-final-target.log` |
| required backend，保留失败 | `CI=true NQ_NO_OUTBOUND=true mvn -f backend/pom.xml test`；EXIT 1；2,956 例、0 failure、1 error、375 skip。唯一错误为 Research Validity 的 owner/random-schema 旧夹具在 accounts 写入 tenant_id NULL（23502），不是金额断言失败；后续修复单独复测，不将此运行追认为 PASS。新增 CI main 的首次编译失败也另存。 | `backend-required-final-corrected.log`；`backend-required-final.log` |
| required backend 最终候选 | `CI=true NQ_NO_OUTBOUND=true mvn -f backend/pom.xml test`，datasource aliases 指向任务库；EXIT 0 / BUILD SUCCESS；2,956 例、0 failure、0 error、375 skip，实际执行通过 2,581 例。旧失败运行不变。 | `backend-required-accepted-candidate.log` |
| 最终 owner 夹具 | `mvn ... -Dtest=ResearchValidityPostgresIntegrationTest,StrategySimPostgresIntegrationTest,NqAppContextPostgresSmokeTest`，显式 required 参数；EXIT 0；26 例、0 failure、0 error、1 skip。 | `final-owner-fixtures.log` |
| local 实际启动 | 编译后运行 `LocalTenantRuntimeFixture`；EXIT 0；相同受限角色启动真实 local Application，HTTP `/actuator/health` 为 UP。 | `local-runtime-provision.log`；`local-runtime-application.log` |
| 真后端 E2E | `npm run test:e2e -- runtime-operational-readiness-final-smoke.spec.ts adapter-readiness-panel-backend-smoke.spec.ts --project=chromium`，proxy 指向任务后端，`E2E_BROWSER_CHANNEL=chrome`；EXIT 0；2/2、0 skip。首次缺少 bundled Chromium 的环境失败保留；本次使用已安装 Chrome，不能称已验证 Ubuntu bundled 浏览器。 | `frontend-real-backend-chrome.log`；`frontend-real-backend-final.log` |
| 治理和交付脚本 | 11 项 PowerShell checks EXIT 0：gate-release-ci-runs、agent-workflow-fixtures、current-authority-next-action、governance-workflow-lifecycle、gate-archive-manifest、check-current-authority、check-doc-links、Test-CanonicalDeliveryWorkflow、其 Tests、Test-GitleaksExecution.Tests、Test-NqCanonicalRelease.Tests。 | 各同名 `.log` |
| Python contracts | whitespace 5/5；stage assets 66、skip 2；stage semantics 4/4；schema source verifier PASS；release exporter/installer 82、skip 1；全部 EXIT 0。Windows POSIX modes 未验证，未将 skip 当 PASS。 | `python-*.log`；`release-exporter-tests.log` |

PostgreSQL 为任务专用 16.15 容器，实际 Flyway core 为 11.7.2；每例独立随机数据库、NOLOGIN owner、受限 runtime；本轮使用 `nq-tenant-closeout-pg-20261022` 与原 `nq-tenant-core-pg-20261021`，动态端口由 `docker port` 读取，不连接共享/生产库。Currency 的 `historical-upgrade.json` 记录 `from=26,to=27,historyPreserved=true,invalidWriteSqlstate=23514`。完整命令的连接材料仅用于本地隔离运行，不写入源码或本报告。

### 最终独立检查与决定

独立冷上下文代理 `/root/closeout_independent_correctness` 已完成用户指定 A–D 全部范围，结论 **PASS，P0=0，P1=0，P2=0**；`INDEPENDENT_REVIEW_BLOCKED=NO`。该代理未参与实现，未改候选或测试；读取冻结代码、原失败及最终证据，并在任务数据库执行只读 catalog / SET LOCAL ROLE 核验：V44、八根 FORCE RLS、22 个 SECURITY DEFINER 的 NOLOGIN/受限 owner 与固定 search_path、运行角色四项危险权限为 false、无上下文根表不可见。A/B、并发及跨 JVM 语义结合已核对的真实专项证据形成结论，没有将只读空计数当完整隔离证明。

独立审查开始时 tree 为 `62dc66d08eb015d60adf8d7412c27b7e4385fe55`（87 文件）。治理具体提案 SHA256 `3930067830042b31c3983a3ae939405e8a6b9a69ee3757efbb3895741f2e722a` 经该代理独立批准，未修改的 lifecycle 工具随后应用；只刷新现有 workflow 注册 hash，不增加例外。审查者再次确认应用后 tree `3f477a5bb7aeaf2109b0348065d209877d098fa7`（88 文件）相对冻结树仅有该批准的 policy 变化。此后只补充本报告的真实结果，代码、migration、fixture 与权限不变。

完整独立报告保留于 `backend/nq-app/target/tenant-closeout-evidence/independent-correctness-review.md`，SHA256 `57b39d85099e1af9ce1b693806a0cae3933b71208dc1726b0a3fc3815d640a47`。它清楚区分实现者执行的测试、审查者独立只读 SQL、治理提案重建及未覆盖范围；不把此前平台中断追认为 PASS。

最终 workflow 合同及其负例 suite EXIT 0，135 项变异均被拒绝；`check-stage-assets.py` EXIT 0（2,625 inputs，87 既有注册，errors 0）；stage semantic errors 0；文档链接 errors 0、保留既有 warnings 123。源码冻结后的测试和本节元数据更新没有实质变更，不重新执行已通过的同候选回归。

```text
Task: NQ-V11-PUBLIC-DEMO-TENANT-CORE-ISOLATION-CLOSEOUT
Branch: codex/nq-v11-public-demo-tenant-core-isolation
Starting Tree: bc674271266e1749695bc1ae123d4ab817bdef30
Changed Files: 88 staged relative to HEAD; 31 touched by Closeout relative to Starting Tree
Legacy CI Fixtures: PASS
Targeted Regression: PASS
V36–V44: PASS / byte-identical to Starting Tree
Tenant Isolation: PASS
Canonical Economic Integrity: PASS
Independent Correctness Review: PASS
P0: 0
P1: 0
P2: 0
Public Registration: DISABLED
LIVE: DISABLED
Decision: READY_TO_COMMIT
Commit: NOT_COMMITTED
Push: NOT_PUSHED
PR: NOT_CREATED
Release: NOT_CREATED
Next Feature: NOT_STARTED
```

最终候选 Tree 由全部精确暂存完成后的 `git write-tree` 输出记录，不再使用旧 62 文件树指代本候选。普通 diff 与 cached diff 的 whitespace 检查均须 EXIT 0。远程 exact-head CI 为 NOT_RUN；这是可提交候选，不是远程 CI、发布或生产接受。375 个全量 skip、专项条件 skip、Windows POSIX file modes 与 Ubuntu bundled 浏览器未验证范围保持原样；两项 E2E 已用本机 Chrome 实际通过。Bundled 浏览器安装尝试未完成并已结束，未将其记为 PASS。临时 E2E 后端进程已结束。
