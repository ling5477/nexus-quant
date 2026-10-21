# NQ V1.1 Public Demo 多租户影响分析与实施方案

Task: `NQ-V11-PUBLIC-DEMO-MULTITENANT-IMPACT-ASSESSMENT`

日期：2026-10-21（Asia/Shanghai）

Decision: **READY_FOR_TENANT_IMPLEMENTATION**

Next Task: `NQ-V11-PUBLIC-DEMO-TENANT-FOUNDATION`

## 1. 范围、基线与结论

本文件是一次性静态影响分析及后续实现输入，不是全量审计、运行验收或公共上线批准。只修改此文档；未执行 Migration、数据库连接、资金写入、交易、运行配置变更或功能实现。后续实现直接进入第 9 节四项任务，不增加规划或架构复审任务。遵照本轮“完成后停止”的范围，本轮不提前实施 Tenant。

| 项目 | 本轮证据 |
| --- | --- |
| 仓库 | `E:\Project\nexus-quant`；fetch 输出远端 `https://github.com/ling5477/nexus-quant` |
| 起始分支 / 工作区 | `dev`；`git status --short` 无输出 |
| fetch 后 HEAD / origin/dev | 均为 `82ebf663dea9e877bd7b82450b1cbb593ad84210` |
| 更新 | `git switch dev`、`git pull --ff-only origin dev`，Already up to date |
| 评估分支 | `codex/nq-v11-public-demo-tenancy-assessment`，从上述 HEAD 新建 |
| Schema 来源 | `nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql` 至 `V34__renewable_auth_sessions.sql`；这是源码 Schema，未声明任何运行数据库已与之同步 |
| PR #67 / 9 项 CI | 用户给定历史背景；本轮未查询 GitHub，不将其写为本轮验证通过 |
| 验证方式 | Git、源码搜索、DDL/约束/触发器阅读、调用链和既有测试阅读；未运行 Maven、PostgreSQL、Playwright |

统一标记：`VERIFIED_EXISTING` 表示当前源码/DDL/测试定义可确认；`REQUIRES_CHANGE` 表示目标尚缺；`PROPOSED` 表示本文件选定的实现设计；`UNKNOWN` 表示本轮不能确认的外部或运行事实。**测试存在不等于本轮测试通过。** 后文“当前”列是 VERIFIED_EXISTING 静态事实，“目标/修改”列均为 PROPOSED / REQUIRES_CHANGE。

结论：复用 Java 核心、认证和 canonical 经济事实，新增真正的 Tenant 身份及可信上下文。采用 Shared Database / Shared Schema；私有数据由显式 Tenant 根表、强制父对象归属查询、数据库一致性约束隔离。大体量共享行情不按注册用户复制。现有系统不能直接对公网开放注册；完成四项实现及相应证据后才可判断公共上线。

### 1.1 最小产品闭环的确定选择

- Self-Hosted：所有已有数据及用户映射至固定 `NQ_LOCAL`；保留本地 ADMIN / OPERATOR / VIEWER 及其权限兼容；没有注册或平台管理模块/页面。此处 `SELF_HOSTED_ADMIN` 是权限矩阵中的产品身份，不要求重命名现有持久化 ADMIN 角色。
- Public Demo：用户与 Tenant 分开建模；首版每用户唯一 Membership、每 Tenant 一个用户。注册用户仅获 `DEMO_USER`，平台操作员仅获 `PLATFORM_ADMIN`，不附赠 ADMIN / OPERATOR。
- 首个可运行闭环限定为：多策略研究/版本/回测/评价/发布，**每 Tenant 一个固定绑定发布版本的 canonical SIM 实验**，可以暂停和显式继续；一个独立 PAPER/SIM 账户，一次性 10,000 USDT。首版不提供删除重建领资、账户重置、重复注资、替换已冻结 SIM 的发布记录或并行创建第二个 SIM 账户。该限制保留现有“一运行一账户”的经济合同，服务端明确返回容量/已绑定冲突，不能悄悄重建。
- 这是最小闭环的容量与产品边界，不是宣称现有引擎支持共享资金池、多运行账户复用或资金划转。后续如需 SIM 切换/多个组合，必须另行明确资金分配合同；本四项实现不引入该功能，也不制造第二套 Ledger。
- 公共展示数据采用审核准入的共享目录；用户私有 dataset 是对已准入共享行情的私有范围/参数描述，首版不开放任意数据上传、任意 URL 抓取或私有行情写入共享 bars。

## 2. A：当前事实与证据索引

以下路径均相对仓库根目录；末尾附录提供表对应持久化类的可点击路径。

| 编号 | 真实来源 | 已存在什么 / 缺少什么 |
| --- | --- | --- |
| S01 | [SecurityConfiguration](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/auth/SecurityConfiguration.java) | BCrypt、JWT、每请求 DB enabled/auth_version/会话校验、无 Servlet Session；GET `/api/**` 总体 authenticated，写侧总体 ADMIN/OPERATOR，存在更严格 scheduler/maintenance 例外；末尾 anyRequest permitAll。没有 Demo 显式白名单。 |
| S02 | [DbAuthService](../../../backend/nq-auth/src/main/java/com/guidinglight/nexusquant/auth/application/service/DbAuthService.java)、[AuthController](../../../backend/nq-api/src/main/java/com/guidinglight/nexusquant/auth/api/web/AuthController.java) | login / renew / logout / me / change-password 链可复用；登录先要求 `hasAdminUser()`。平台只有 PLATFORM_ADMIN 时需要发行物相关的初始化判断，不能为了通过此判断附赠 ADMIN。没有公共注册。 |
| S03 | [JdbcAuthUserRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/auth/infra/jdbc/JdbcAuthUserRepository.java)、[AuthUserRepository](../../../backend/nq-auth/src/main/java/com/guidinglight/nexusquant/auth/domain/port/AuthUserRepository.java)、[JWT filter](../../../backend/nq-security/src/main/java/com/guidinglight/nexusquant/security/web/JwtAuthenticationFilter.java) | 用户角色从 DB 读取；用户行锁、会话创建/续期/撤销、密码代际可复用；JWT principal 是 TokenClaims，当前 subject 为 username，不含 Tenant；用户启停管理命令与 Membership 不存在。旧无 sid 令牌仍有兼容分支。 |
| S04 | [AuthBootstrapAdminConfiguration](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/auth/AuthBootstrapAdminConfiguration.java) | 显式属性开启时使用固定开发凭据，初次创建强制改密；现有空库锁保护不是公网安全初始化。文档不复制凭据值。Demo 必须排除此 Runner 并在误启时拒绝启动。 |
| S05 | [V1 baseline](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql)、[V3](../../../backend/nq-infra/src/main/resources/db/migration/V3__user_default_account.sql)、[V34](../../../backend/nq-infra/src/main/resources/db/migration/V34__renewable_auth_sessions.sql) | users、roles、user_roles、auth_sessions 已有；users 含 enabled/auth_version，无邮箱和验证字段；账户有 owner_user_id、legacy_account_id；V3 是每用户一个默认账户。没有 tenants / tenant_memberships 或通用 tenant_id。 |
| S06 | [JdbcResearchConfigRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcResearchConfigRepository.java)、[JdbcStrategyVersionRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyVersionRepository.java) | 研究按 config ID、版本按 ID/strategy_code 查询；created_by 只是审计字符串，不能证明租户归属。部分列表/计数没有 owner 边界。 |
| S07 | [ResearchFeeReadScope](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/ResearchFeeReadScope.java) | 私有费用来源按 owner 过滤；普通研究假设可见，owner 缺失返回 TRUE。不是通用租户隔离；Tenant 必须与费用隔离叠加，不能取代既有私有费用约束。 |
| S08 | [BacktestRunService](../../../backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/BacktestRunService.java)、[BacktestPublishService](../../../backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/BacktestPublishService.java)、[V32](../../../backend/nq-infra/src/main/resources/db/migration/V32__backtest_evaluation_report_history.sql) | 正式创建冻结配置/版本/数据快照；发布可绑定 typed artifact locator，普通调用可为 LEGACY_ARTIFACT_UNBOUND；报告已改为多次评估追加，不得把每 run 唯一报告恢复回来。 |
| S09 | [ResearchCreationOperationService](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/research/ResearchCreationOperationService.java)、[V28](../../../backend/nq-infra/src/main/resources/db/migration/V28__research_creation_operations.sql)、V30/V31 | 操作 UUID 和 owner、原请求、结果身份同事务；SIM_RUN 使用 READ_COMMITTED，不能盲目套研究 REPEATABLE_READ。owner 回执不意味着被创建对象已获 Tenant 保护。 |
| S10 | [StrategySimRunController](../../../backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/StrategySimRunController.java)、[StrategySimRunService](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/StrategySimRunService.java) | 旧 create 控制器传 actor=`system` 和客户端 budget；服务创建 paper run、全新 accounts、一次性 fundOnce、策略定义，再经 StrategyExecutionGateway。不能原样授权 Demo。 |
| S11 | [StrategySimAnalysisQuery](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/StrategySimAnalysisQuery.java)、[StrategySimPerformanceQuery](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/StrategySimPerformanceQuery.java) | 读侧按创建用户名或源策略账户 owner 推导可见性，并叠加费用边界；绩效从 canonical 成交、Ledger、冻结 mark 推导，有窗口上限/UNAVAILABLE；要求一次性 funding 与 run 的 ref_id 一致。不是任意账户复用的组合绩效。 |
| S12 | [PaperTradingRunService](../../../backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/service/PaperTradingRunService.java)、[PaperRunReadAccess](../../../backend/nq-api/src/main/java/com/guidinglight/nexusquant/paper/api/web/PaperRunReadAccess.java) | run service 委托 ID 仓储；read interceptor 只处理 GET/HEAD，canonical 分支做 owner 校验，legacy Paper 被保留兼容。不能把该拦截器当作写侧或全租户隔离。 |
| S13 | [ContinuousSimRunService](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/ContinuousSimRunService.java)、[ContinuousSimRepository](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/ContinuousSimRepository.java) | public-marketdata-manual profile + 属性启用；activeIds LIMIT 8，全局 advisory admission 锁、总容量 8；按 run 轮询，不读取 users.enabled/Membership。stop/resume 不等于用户禁用控制。 |
| S14 | [SimCashFundingService](../../../backend/nq-ledger/src/main/java/com/guidinglight/nexusquant/ledger/application/service/SimCashFundingService.java)、[JdbcLedgerPostingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcLedgerPostingRepository.java) | 新空账户一次性 SIM_FUNDING_CASH/CONTRA、快照、幂等和货币锁；balance 查询识别特定分录，不能随便加“余额”字段或新分录类型冒充支持资金划拨。 |
| S15 | [CanonicalLegacyAccountBridgeService](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/CanonicalLegacyAccountBridgeService.java)、[JdbcAccountTradeEnvironmentAuthority](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountTradeEnvironmentAuthority.java) | 现代 exchange_account 与 accounts 是两种身份；bridge 校验 owner/environment/venue，SIM_VENUES 目前仅 OKX/BINANCE，尚不接受 PAPER。环境 authority 用 UNION ALL 汇总现代账户和运行且要求恰一条，直接新增 PAPER 映射会在运行后双命中。Demo 需要第 7 节的受限兼容改造，不能声称仅调用旧 bridge 即可完成。 |
| S16 | [ValidationReviewCase](../../../backend/nq-core/src/main/java/com/guidinglight/nexusquant/validationreview/domain/ValidationReviewCase.java)、V1 validation_review_* | 局部 tenant_key 固定 NQ_LOCAL，含 tenant/owner 查询及复合 FK；不是可注册的全局 Tenant。与 DH 出站签名中的 tenantId 也不能互认成登录 Tenant。 |
| S17 | [backend/pom.xml](../../../backend/pom.xml)、[nq-app/pom.xml](../../../backend/nq-app/pom.xml)、[应用入口](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/NexusQuantApplication.java) | Java 21、Boot 3.5.10 多模块，nq-app composition root 全包扫描并依赖核心/adapter；当前未区分 Demo/Self-Hosted 装配。 |
| S18 | [routes.tsx](../../../frontend/src/router/routes.tsx)、[vite.config.ts](../../../frontend/vite.config.ts)、[package.json](../../../frontend/package.json) | React/Vite/Ant Design，一个前端构建入口；已有登录/强制改密、业务路由，无注册/平台入口的发行变体。SIM 编译 flag 是入口开关，不是权限机制。 |
| S19 | [backend Dockerfile](../../../release/docker/backend.Dockerfile)、[frontend Dockerfile](../../../release/docker/frontend.Dockerfile)、[compose](../../../release/runtime/compose.yml)、[build-package](../../../release/build-package.ps1)、[source manifest](../../../release/source-manifest.json) | Docker 构建固定 nq-app 与单个前端 dist，镜像/包身份验证与 source allowlist 已有；公共部署形态不存在。compose 给 DB 初始化用户和后端同名数据库身份，不能假定已有最小权限/RLS 隔离。 |
| S20 | V1 的 `nq_*` SECURITY DEFINER 函数及 `nq_guard_*` 触发器 | canonical admission/work/订单状态依赖受限函数和 owner 判断；仅加 WHERE 或换 DB role 不会自动保护函数内的跨租户对象引用。 |

`UNKNOWN`：公网域名、实际数据库角色属性/版本、数据规模与脏关联、邮箱发送基础设施、真实行情公共展示许可及可公开的字段/频率范围。本轮不连接这些系统，也不推断许可。用户资料/策略内容不需要从生产环境提取。

## 3. B：表级数据隔离矩阵

### 3.1 读表说明与选定的数据模型

四类：`TENANT_PRIVATE`（下表 P）、`SHARED_READONLY`（S）、`PLATFORM_INTERNAL`（I）、`DEMO_UNAVAILABLE`（U）。S 是对 Demo 用户只读，不禁止受控平台采集；I 不是 PLATFORM_ADMIN 任意运维写权限；U 在 Self-Hosted 保留原能力/授权，Public Demo 服务器拒绝。

每行独立列出真实表。CurrentOwner/CurrentIsolation 不表示已做动态越权测试。RepositoryChanges 中 **T** = 必须在数据库查询/写入内绑定可信 Tenant，包含 list/count/search/detail/export/更新/归档和回执重放；**D** = 每次通过列出的父链校验，不允许只有裸 ID；**RO** = Demo 只读准入投影；**X** = Demo 不装配/不授权；**A** = 审计/事件同事务附加归属。所有 T/D 写入同时检查对象原归属，禁止移动到另一 Tenant。

TenantIdRequired / MigrationRequired 编码：

- **Y / ADD**：显式新增 tenant_id、FK/索引、禁止后续迁移归属；八个根表见下文。`marketdata_datasets` 另有共享判别。
- **D / FK**：无需冗余 tenant_id；Tenant 必须从强制父链派生。迁移补 FK 或归属一致性约束/数据库触发器及索引；已有 FK 不重复建立。多父链必须相等，不是“随便一个父对象属于自己即可”。
- **D / POLICY**：无需回填经济/历史行，增加权限/查询策略、必要的关联索引；只有确实缺约束才加 FK。
- **M / SIDE**：原行无可靠通用父链，新增旁表登记 tenant_id 和分类，不改冻结 payload / 历史金额；所有后续事件同事务落旁表。
- **N / NONE**：不加 tenant_id、不改现有业务 Schema；只做所列服务/接口隔离；新功能所需变更另列。

#### Auth / Account

| Table | CurrentOwner / CurrentIsolation | TargetIsolation | TenantIdRequired / MigrationRequired | RepositoryChanges / 读写路径 | CrossTenantRisk |
| --- | --- | --- | --- | --- | --- |
| users | id/username；enabled/auth_version，平台用户事实 | I（用户自读自己的安全投影） | N / 新增规范化 email、email_verified_at 可空字段及局部唯一索引 | JdbcAuthUserRepository；按服务端 userId 读取/改密；仅专用平台服务启停 | 把全局角色或 username 当 Tenant |
| roles | 全局 role_code | I | N / 新增 DEMO_USER、PLATFORM_ADMIN 数据 | JdbcAuthUserRepository；注册不能选择角色 | 把 ADMIN/OPERATOR 给注册者 |
| user_roles | user_id+role_id | I | N / NONE | 同上；平台角色仅可信初始化，Tenant 角色来自 membership | 客户端改角色/伪造平台身份 |
| auth_sessions | user_id/auth_version/session_id；V34 | I / 本人 | N / NONE | JdbcAuthUserRepository；禁用事务撤销，Demo 不接收 legacy 无 sid token | 续期复活/跨用户 sid |
| user_preferences | user_id PK | P（用户维度） | D / POLICY | JdbcUserPreferencesRepository；userId + 当前 membership，不能客户端传 owner | 界面偏好覆盖他人 |
| exchange_accounts | owner_user_id；legacy_account_id；已有 owner 复合键 | P | Y / ADD | JdbcExchangeAccountRepository；T，owner 与 membership 一致、桥接 T | 用户 owner 不等于 Tenant；两个账户 ID 混用 |
| accounts | account_id；全局 account_code，未含用户 | P | Y / ADD | CanonicalLegacyAccountBridgeService、StrategySimRunService；T；全部新旧入口校验环境 | canonical 账户无现代账户父对象时越权 |
| account_snapshots | account_id，部分 trade_env/估值来源 | P | D / FK | JdbcLedgerPostingRepository、JdbcAccountPerformanceRepository、JdbcTradingQueryFacade；D(accounts) | 未过滤账户聚合/环境混算 |
| account_equity_measurements | exchange_account_id | P | D / FK | JdbcAccountPerformanceRepository；D(exchange_accounts)，有界采样/清理 | 时间序列跨账户 |
| account_command_receipts | owner_user_id+exchange_account_id，V5 不可变 | P | D / POLICY | JdbcAccountCommandReceiptRepository；D(account)，回放同 owner+Tenant | 按 operation_id 裸重放 |
| exchange_account_credentials | exchange_account_id；加密材料 | U | N / NONE | JdbcExchangeAccountCredentialRepository；X；Self-Hosted 仍按 account Tenant + owner | 即使 GET 也会泄漏凭据元数据 |
| credential_operation_receipts | owner+account+credential，V6 不可变 | U | N / NONE | JdbcCredentialOperationRepository；X | 跨账号原请求/结果泄漏 |
| credential_audit_logs | credential/account，actor 字符串 | U | N / NONE | JdbcExchangeAccountCredentialRepository；X | 原因/metadata 泄漏 |
| account_fee_observations | owner+account+credential，V7/V10 不可变 | U | D / POLICY（Self-Hosted 私有派生读仍限 Tenant） | JdbcAccountFeeRepository；Demo 禁止采集和读取私有费率；不绕过 ResearchFeeReadScope | 费用冻结 JSON 的隐式跨租户读取 |

#### Strategy / Research / Backtest / Evaluation / Publication

| Table | CurrentOwner / CurrentIsolation | TargetIsolation | TenantIdRequired / MigrationRequired | RepositoryChanges / 读写路径 | CrossTenantRisk |
| --- | --- | --- | --- | --- | --- |
| strategy_definitions | account_id/exchange_code/trade_env；strategy_code 全局唯一 | P | Y / ADD | JdbcStrategyDefinitionRepository、JdbcSourceStrategySnapshotRepository；T，策略账户同 Tenant | 代码撞名/trigger 他人策略 |
| strategy_versions | strategy_code，created_by 字符串 | P | Y / ADD | JdbcStrategyVersionRepository、JdbcStrategyVersionSnapshotQueryPort；T；maxVersion 同租户 | 同 code 版本号/冻结快照泄漏 |
| research_configs | source_strategy_id，无 owner；裸 config ID/列表 | P | Y / ADD | JdbcResearchConfigRepository；T；source 必须同 Tenant | 已知 ID 可读/归档他人配置 |
| backtest_configs | research_config_id，dataset_id、strategy_version_id | P | Y / ADD | JdbcBacktestConfigRepository；T；研究/版本同 Tenant，dataset 为自己或获准共享 | 绑定其他租户 dataset/version |
| backtest_runs | config/research/source/version；部分费用 owner 过滤 | P | D / FK | JdbcBacktestRunRepository；D(backtest_configs)，同时验证研究/版本；创建、start、执行回写都限范围 | 普通研究费用条件为 TRUE，不等于隔离 |
| sim_orders | backtest_run_id | P | D / FK | JdbcSimOrderRepository；D(backtest_runs) | 回测 sim_* 与 canonical 实盘表混淆 |
| sim_trades | backtest_run_id / sim_order_id；V18 execution_sequence | P | D / FK | JdbcSimTradeRepository；D(run)，成交与订单同 run | 关联错 run / 序列泄漏 |
| sim_positions | backtest_run_id | P | D / FK | JdbcSimPositionRepository；D(run)，run+symbol 作用域 | 持仓按 symbol 全局查 |
| sim_pnl_snapshots | backtest_run_id；V18 valuation_sequence | P | D / FK | JdbcSimPnlSnapshotRepository；D(run) | 报告/资金快照交叉 |
| backtest_eval_reports | backtest_run_id，多报告历史 | P | D / POLICY | JdbcBacktestEvaluationReportRepository、JdbcBacktestEvaluationQueryPort；D(run)，精确报告与最新报告均过滤 | 按 evaluationId 越权、误改历史 |
| backtest_evaluation_requests | owner_user_id + run；V29 原请求 | P | D / FK | BacktestEvaluationRequestService；D(run)+owner；PENDING 不视为可重做 | 回执 owner 与目标 run 不一致 |
| backtest_publish_records | run/config/research/source/version/eval_report，多父链 | P | D / FK | JdbcBacktestPublishRecordRepository、BacktestPublishService；D(run)并核对所有父对象；发布目标策略 T | 发布他人结果/绑定他人 artifact |
| research_creation_operations | owner_user_id，kind/parent_key/object_id 原请求 | P | D / POLICY | ResearchCreationOperationService；owner 的 Membership + 目标同 Tenant；不更改稳定 UUID | 私有回执掩盖全局对象创建 |
| strategy_release_admission_state | strategy_release_id / admission revision | U（首版不开放 Shadow/LIVE admission） | D / POLICY | JdbcStrategyReleaseAdmissionStateRepository、相关 facts queries；X，内部按 publish Tenant | release ID 提升成运行授权 |
| shadow_runs | version/dataset/evaluation/publish/paper；全局 idempotency | U | D / POLICY | JdbcShadowRunFactRepository；X；既有本地链按父对象同 Tenant | 非首版面被 GET authenticated 暴露 |
| shadow_run_events | shadow_run_id | U | D / POLICY | JdbcShadowRunFactRepository；X / D(shadow) | 全局事件时间线 |
| shadow_run_snapshots | shadow_run_id | U | D / POLICY | 同上；X / D(shadow) | 含策略和输入快照 |
| shadow_consistency_reports | shadow_run_id + paper_run_id | U | D / FK | JdbcShadowRunFactRepository、JdbcPaperShadowComparisonFactRepository；X，双父同 Tenant | cross-run 比较泄漏 |
| validation_review_cases | tenant_key 固定 NQ_LOCAL + owner_id | U（首版非 Demo API） | D / POLICY | JdbcValidationReviewRepository；保留本地固定键和原 owner/ADMIN 范围，不冒充全局 Tenant | 将现有 ADMIN tenant scope 误授平台管理员 |
| validation_review_events | review_case_id+tenant_key FK | U | D / POLICY | 同上；X，现有事件不回写 tenant_key | 改写 frozen review 或事件 |

首版不开放 U 域的用户入口，并不删除这些表/模块或改变 Self-Hosted 当前语义。Evaluation 的基准/有效性配置在 config/report JSON 及派生查询中承载，未发现需凭空新增的独立“评价基准业务表”；必须沿同一 run/config 范围读取。

#### MarketData

| Table | CurrentOwner / CurrentIsolation | TargetIsolation | TenantIdRequired / MigrationRequired | RepositoryChanges / 读写路径 | CrossTenantRisk |
| --- | --- | --- | --- | --- | --- |
| marketdata_bars | 交易所/market/symbol/interval/time 共享范围 | S | N / NONE | JdbcMarketdataBarRepository；RO；只能经许可目录取得行情窗口，平台采集写 | 任意范围下载/私有上传污染共享事实 |
| instrument_catalog | exchange/instrument，平台同步 | S | N / NONE | JdbcInstrumentCatalogRepository；RO 审核字段；同步 API 不给 Demo | 租户触发全局同步/资源消耗 |
| currency_definitions | 全局币种定义 V27 | S | N / NONE | JdbcCurrencyRegistry；RO | 私人修改精度影响全平台 |
| marketdata_datasets | created_by 审计字符串、dataset_id | P 或 S（逐行明确） | Y / ADD：私有必须 tenant_id，共享必须为空且有许可目录准入 | JdbcMarketdataDatasetRepository；T/RO，visibility 与 Tenant CHECK，禁止用户创建共享项 | created_by 不能认证归属；共享描述符混入私人参数 |
| marketdata_dataset_coverage | dataset_id | P 或 S，继承父项 | D / FK | JdbcMarketdataDatasetRepository；D(dataset) | coverage 旁路泄漏私有 dataset |
| marketdata_ingestion_jobs | created_by 字符串，公共采集范围 | I | N / NONE | JdbcMarketdataIngestionJobRepository；X；首版只允平台受控采集 | 用户制造任意出站任务 |
| marketdata_ingestion_runs | job_id | I | N / NONE | 同上；X | raw summary / error 泄漏 |
| public_market_captures | dataset_id、冻结公开 feed/rule/raw JSON | S 的受控输入，原始响应 I | N / NONE | PublicMarketReplayCaptureService；只公开准入派生 DTO，不直接返回 raw_response | “public API”不等于公共展示授权 |
| public_market_capture_operations | owner_user_id、result_dataset_id；V25 | I | N / NONE | PublicMarketCaptureOperationService；X | 全局采集结果/原请求泄漏 |
| marketdata_dataset_operations | owner_user_id + dataset/coverage；V26 | P 或 I（操作用途决定） | D / FK | MarketdataDatasetOperationService；用户仅建私有描述符时 D(dataset)+owner；平台操作 X | 复用平台回执/修改共享描述符 |
| instrument_catalog_sync_requests | owner_user_id；V29 | I | N / NONE | InstrumentCatalogSyncRequestService；X | Demo 触发共享写任务 |
| marketdata_price_event_sources | bar_id、created_by_user_id；V9 不可变 | I / 准入后可共享投影 | N / NONE | JdbcPriceEventSourceRepository；X 写入，只读公开规范字段 | 文档来源未授权/作者身份泄漏 |
| discount_asset_price_snapshots | owner_user_id、dataset_id；V15 不可变 | P（首版不启折扣币支付） | D / POLICY | JdbcDiscountAssetPriceSnapshotRepository；D(dataset)+owner；不 UPDATE 历史快照 | 公共价格包装中的私人预算/来源 |

不能把所有 created_by 非空行自动标成共享。历史 dataset 默认 NQ_LOCAL 私有；平台发布共享目录时仅新增/准入描述符，复用 bars，不改冻结历史快照。共享行情的商业授权状态不能靠 tenant_id 表达，Demo 专属目录需保存来源、允许范围、许可依据引用与有效期；未准入默认拒绝。

#### SIM / Trading / Ledger

| Table | CurrentOwner / CurrentIsolation | TargetIsolation | TenantIdRequired / MigrationRequired | RepositoryChanges / 读写路径 | CrossTenantRisk |
| --- | --- | --- | --- | --- | --- |
| paper_trading_runs | publish/version、created_by；canonical_account_id 可空 | P | Y / ADD | JdbcPaperTradingRunRepository、StrategySimAnalysisQuery、StrategySimRunService；T；publish、version、account 同 Tenant | actor=`system`、legacy 无 owner、写侧裸 ID |
| continuous_sim_runs | paper_run_id | P | D / FK | ContinuousSimRepository、ContinuousSimQueryService；D(paper)，停用 gate | 账号禁用仍自动推进 |
| continuous_sim_bars | paper_run_id+open_time，运行冻结输入 | P | D / FK | ContinuousSimRepository；D(run)，保留已有输入摘要；公共轮询单次缓存后分发 | 复制共享原始历史/跨 run 输入替换 |
| continuous_sim_operations | owner_user_id + paper_run_id，V21 不可变 | P | D / POLICY | ContinuousSimOperationRepository/Service；D(run)+owner，原 UUID 重放 | 越权 stop/resume 或重复领资 |
| strategy_sim_decisions | paper_run_id，version/run/order 多关联 | P | D / FK | StrategySimDecisionRepository、JdbcStrategySimReplayClockRepository；D(paper)，其余关联同账户/租户 | 可见 decision 指向其他账户经济事实 |
| strategy_runs | strategy_id+account_id，冻结 request/config | P | D / FK | JdbcStrategyRunRepository/QueryRepository/ExecutionRepository/RecoveryRepository；D(account)+strategy 同 Tenant | admission/worker 不经 HTTP |
| strategy_run_dispatch_work | strategy_run_id+account_id，不可变工作 | P | D / FK | JdbcStrategyRunExecutionRepository、canonical SQL 函数；D(run)+account 同 Tenant | 重启或 claim 恢复错 Tenant |
| orders | account_id、strategy_run_id、env；稳定客户端身份 | P | D / FK | JdbcOrderRepository、JdbcTradingQueryFacade、StrategySimOrderQuery；D(account)，run同 Tenant；所有新增交易仍走 Risk/Execution | 按 orderId 查/取消/刷新他人订单 |
| trades | account_id+order_id+strategy_run_id，venue/environment | P | D / FK | JdbcTradeRepository、RequiredTradeEventStore；D(account)，order/account/run一致 | 成交 ID 全局索引与裸查询 |
| positions | account_id+symbol | P | D / FK | JdbcLedgerPostingRepository、JdbcTradingQueryFacade；D(account)，数量/费用精度不变 | 全局 symbol 汇总/环境错配 |
| ledger_entries | account_id，ref_type/ref_id/idempotency_key | P | D / POLICY | JdbcLedgerPostingRepository、JdbcLedgerReconcileRepository、StrategySimPerformanceQuery；D(account)，交易引用同账户 | 账务重放碰撞、跨账户 ref |
| ledger_events | entry_id，冻结 payload | P | D / FK | JdbcLedgerPostingRepository；D(entry→account) | event 读取绕过账户过滤 |
| sim_asset_funding_receipts | paper_run/account/分录，V14 不可变 | P（首版折扣注资不开放） | D / POLICY | JdbcSimAssetFundingRepository；D(paper/account)，所有分录同账户 | 机械回填触发 immutable guard |
| ordinary_place_authorities | order_id，受限 SQL 状态机 | P / 内部不可直接 API | D / POLICY | JdbcStrategyRunRecoveryRepository 与 nq_* functions；D(order) | SECURITY DEFINER 绕过表级 RLS |
| ordinary_order_cancel_finality | order_id，受限 SQL 不可变结果 | P / 内部不可直接 API | D / POLICY | nq_finish_strategy_cancel 等函数；D(order) | 伪造归属/终态 |
| paper_trading_orders | legacy paper_run_id | P / legacy只读；Demo 不使用 legacy 执行 | D / FK | JdbcPaperTradingOrderRepository；D(paper) | legacy 兼容分支原样公开 |
| paper_trading_trades | legacy paper_order_id+paper_run_id | P / legacy只读 | D / FK | JdbcPaperTradingTradeRepository；D(paper)，订单同 run | 第二套经济事实被误当 canonical |
| paper_trading_positions | legacy paper_run_id | P / legacy只读 | D / FK | JdbcPaperTradingPositionRepository；D(paper) | 同上 |
| paper_risk_check_results | paper_run_id | P | D / FK | JdbcPaperRiskCheckResultRepository；D(paper) | runOnce 访问他人运行 |
| equity_curve_snapshots | paper_run_id；V27/V30 来源/估值及不可变投影 | P | D / POLICY | JdbcEquityCurveSnapshotRepository；D(paper)；仍由事实推导，不手工改净值 | 历史曲线修改、混合旧投影 |
| position_curve_snapshots | paper_run_id+symbol | P | D / FK | JdbcPositionCurveSnapshotRepository；D(paper) | 快照关联 ID 越权 |
| trade_replay_records | paper_run_id | P | D / FK | JdbcTradeReplayRecordRepository；D(paper) | 回放包含策略/成交详情 |

#### Scheduler / Audit / 平台内部与 Demo 禁用域

| Table | CurrentOwner / CurrentIsolation | TargetIsolation | TenantIdRequired / MigrationRequired | RepositoryChanges / 读写路径 | CrossTenantRisk |
| --- | --- | --- | --- | --- | --- |
| strategy_schedules | strategy_id+account_id，env；全局 due 扫描 | P | D / FK | JdbcStrategyScheduleRepository、ListQueryAdapter；D(strategy/account)，worker恢复 Tenant | 禁用后 due 仍触发 |
| strategy_schedule_creations | owner_user_id + schedule_job_id，V23 不可变 | P | D / POLICY | JdbcStrategyScheduleCreationRepository；D(schedule)+owner | 回执跨 owner/tenant |
| paper_run_schedules | paper_run_id，created_by 字符串 | P | D / FK | JdbcPaperRunScheduleRepository；D(paper)，首版不给任意 cron 写 | 操作 scheduleId 绕过 run |
| paper_run_schedule_fires | schedule_id+paper_run_id | P | D / FK | JdbcPaperRunScheduleFireRepository；双父一致，worker Tenant | 任务恢复到错误租户 |
| paper_run_heartbeats | paper_run_id | P | D / FK | JdbcPaperRunHeartbeatRepository；D(paper) | 定时写不含请求上下文 |
| paper_run_daily_reports | paper_run_id | P | D / FK | JdbcPaperRunDailyReportRepository；D(paper)，按 run/date 有界查询 | 日报包含其他账户绩效 |
| paper_run_stability_checks | paper_run_id | P | D / FK | JdbcPaperRunStabilityCheckRepository；D(paper) | 跨 run 统计窗口 |
| paper_run_alerts | paper_run_id | P | D / FK | JdbcPaperRunAlertRepository；D(paper) | 目录/ack裸 alertId |
| paper_alert_operations | owner、paper_run/alert，V22 不可变 | P | D / POLICY | JdbcPaperAlertOperationRepository；D(run/alert)+owner | 操作回执泄漏 |
| paper_run_recovery_events | paper_run_id | P | D / FK | JdbcPaperRunRecoveryEventRepository；D(paper)；恢复不得跳过停用 gate | 恢复继续交易 |
| emergency_stop_events | paper_run_id，triggered_by | P | D / FK | JdbcEmergencyStopEventRepository；D(paper) | 平台管理员借 stop 修改经济状态 |
| scheduled_job_controls | job_key，全局运维调度开关 | I | N / NONE | ScheduledJobControlRepository；Demo 两角色均无通用运维 API | 一个用户停全平台/突破全局硬限制 |
| scheduled_job_operations | owner_user_id+job_key，V19 | I | N / NONE | ScheduledJobOperationRepository/Service；X | 平台角色被误当现有 ADMIN |
| strategy_run_recovery_scan_cursor | cursor_id=1，全局扫描游标 | I | N / NONE | JdbcStrategyRunExecutionRepository；内部枚举后逐实体恢复 scope，不能作为读取私有事实的 bypass | 无上下文 worker 大范围写 |
| reconciliation_scan_cursors | venue，全局扫描游标 | I | N / NONE | JdbcOrderRepository；同上，Demo 无外部私有对账入口 | 全局游标不等于租户授权 |
| audit_logs | domain/action/actor/detail，无必有父 FK | P 或 I（逐事件分类） | M / SIDE | JdbcAuditLogRepository、JdbcLedgerRiskAuditRepository、bridge 等直接 writer；A | actor 字符串不能派生 Tenant |
| event_store | topic/key/payload/trace，无统一 owner FK | P 或 I（逐事件分类） | M / SIDE | EventStoreAppender、RequiredTradeEventStore、JdbcOrderRepository；A，重放由登记范围恢复 | payload/traceId 不能用作授权 |
| risk_events | scope/scope_id，多态对象引用 | P 或 I（逐事件分类） | M / SIDE | JdbcRiskEventRepository、JdbcLedgerRiskAuditRepository；A；ORDER/ACCOUNT映射校验，其余平台范围 | 多态 scope 不能总按订单 JOIN |
| trading_maintenance_operations | owner、稳定维护请求；V33 | U | N / NONE | TradingVerificationController、JdbcTradingMaintenanceOperations；X | ADMIN/OPERATOR 维护可改变经济事实 |
| kill_switch_states | 全局/命名 scope，状态版本 | I；管理写 U | N / NONE | JdbcKillSwitchStateRepository；Demo 固定安全装配，PLATFORM_ADMIN 无解除权限 | tenant admin 被授 kill switch 控制 |
| kill_switch_events | scope/actor，全局事件 | I | N / NONE | 同上；只给运维内部，不给 Demo 通用审计 | 全局安全/操作信息泄漏 |
| risk_limit_sets | effective_scope、created_by、digest | U | N / NONE | JdbcLiveControlRepository；Demo 仅服务器固定 SIM 限制，不开放 LIVE 风险集操作 | 将用户风险配置变成平台交易权限 |
| live_sessions | owner/exchange_account/release/risk/credential | U | N / NONE | JdbcLiveControlRepository；X | LIVE 授权入口 |
| live_session_events | session_id | U | N / NONE | 同上；X | 历史/审批信息 |
| operator_approvals | session/approver/scope hash | U | N / NONE | JdbcLiveControlRepository、JdbcPilotScopeRepository；X | Demo 角色参与交易审批 |
| operator_execution_authorities | owner/account/credential，全局 single active | U | N / NONE | JdbcOperatorPilotAuthorityRepository；X | 意外扩大全局 LIVE 并发限制 |
| controlled_execution_leases | session/binding/authority、created_by | U | N / NONE | JdbcPilotExecutionLeaseRepository；X | 不能为多租户放松 single-open 硬门 |
| controlled_execution_lease_events | lease_id | U | N / NONE | 同上；X | 执行恢复元数据 |
| controlled_execution_lease_intents | lease_id+intent_id | U | N / NONE | JdbcExecutionIntentRepository / lease repository；X | 原全局 PLACE/CANCEL 限制被移除 |
| execution_intents | session_id/local_order/client_order | U | N / NONE | JdbcExecutionIntentRepository；X | 真正外部 PLACE/CANCEL 路径 |
| execution_receipts | intent_id | U | N / NONE | 同上；X | 外部结果当重试许可 |
| execution_scope_bindings | session/scope/digest/created_by | U | N / NONE | JdbcPilotScopeRepository；X | 将平台配置误当租户 authority |
| execution_prerequisite_observations | execution_scope_id/observation_set | U | N / NONE | 同上；X | 私有余额/凭据引用 |
| execution_instrument_observation_items | observation_id | U | N / NONE | 同上；X | 受控交易观测旁路 |
| execution_pre_place_recovery_decisions | predecessor lease/session/decided_by | U | N / NONE | JdbcPilotPrePlaceRecoveryRepository；X | UNKNOWN 外部结果盲重放 |

### 3.2 聚合、搜索和文件访问同属范围

表有 Tenant 不能自动保护 read model。`JdbcObjectSearchRepository`、`JdbcTradingQueryFacade`、`JdbcStrategyRun*QueryAdapter`、`JdbcStrategyValidationOverviewQueryRepository`、`JdbcPaperShadowComparisonFactRepository`、incident/replay/consistency overview 等多表查询必须按矩阵改或在 Demo 默认拒绝。不能先查询所有对象再 Java 内存筛选，也不能 COUNT 全局、列表局部，不能通过错误内容泄漏对象存在。

Backtest/Publication 中的 artifact_storage_key / manifest_storage_key 是存储定位，不是权限。先按 Tenant 取得 publish/run，再由服务器解析关联 locator；文件下载/预览也做同一授权，禁止客户端路径、目录遍历和静态 Web 根目录直出私人 artifact。首版不开放 Python artifact 绑定、远程执行/任意代码和 DH/AI gateway；Python 是隔离 research 域，未在上述 Flyway 中发现可当全局研究运行权威的 Python 表。本方案不复制 Java 回测引擎或凭空假设 Python 服务已支持 Tenant；相关非最小路径拒绝访问，现有 Self-Hosted research 保持。

## 4. 可信身份、认证与权限

### 4.1 Tenant / User / Role / Account 的明确分工（PROPOSED）

`tenants(tenant_id UUID PK, tenant_code UNIQUE, tenant_name, tenant_kind, status, created_at, updated_at)`；tenant_kind 限 LOCAL/DEMO，NQ_LOCAL 使用一条稳定 UUID 映射，不以 UserId 充当 TenantId。tenant_code 作为公开稳定标识，不参与客户端任意切换。

`tenant_memberships(tenant_id FK, user_id FK, role_code, membership_kind, status, created_at, updated_at)`；PK `(tenant_id,user_id)`，全局 `UNIQUE(user_id)` 保证一个用户仅属于一个 Tenant；仅在 `membership_kind='DEMO'` 上建立 tenant_id 部分唯一索引，保证每个 Demo Tenant 一个用户。NQ_LOCAL 允许多个旧本地用户；membership_kind 与 tenants 的 LOCAL/DEMO 类型必须由复合 FK/约束一致校验，不允许向 Demo Tenant 插入 LOCAL Membership 绕过限制。role_code 的 CHECK/引用只允许本发行物的 Tenant 角色；全局 PLATFORM_ADMIN 在 user_roles 中，不获得普通 Tenant Membership，也不选一个目标 Tenant 进入业务服务。

本地 Membership 的 role_code 固定 LOCAL_MEMBER，仅表达本地成员关联，有效 ADMIN/OPERATOR/VIEWER 仍从原 user_roles 得到；Demo Membership 的 role_code 为 DEMO_USER。LOCAL_MEMBER 不新增业务权限，不把旧多角色压缩成一个角色，也不参与公网授权。

users / auth_sessions / roles 是全局认证事实。沿用 numeric userId、BCrypt、auth_version 和 session，JWT 如增加 userId/tenantId claim，仅是签名提示；Tenant 用户每次请求仍从 DB 校验 user、Membership、Tenant status，使用服务器得到的 immutable `TenantPrincipal(tenantId,userId,roles,sessionId,authVersion)`。有效 DEMO_USER authority 由 active Membership 得到，PLATFORM_ADMIN 从受控 user_roles 得到，不接受客户端或旧 JWT 中角色自授。平台管理员使用独立 PlatformPrincipal，不要求虚构 Membership，也不建立业务 TenantContext；调用普通私有服务因缺 Tenant 而拒绝。首版无租户切换 API，无从 Header/URL/body 取 TenantContext 的分支。

Context 生命周期：JWT 校验 → DB active Membership → 建 TenantContext → 权限路由 → 服务/仓储 → finally 清理。缺上下文对私有域 fail closed；Self-Hosted 也必须由已认证身份映射 NQ_LOCAL，不能在 Demo 上用“上下文缺失自动本地”兜底。平台注册/初始化和后台全局枚举使用显式专用系统身份及有界入口，不伪造普通用户。

注册与邮箱：新增规范化 email（长度/格式/大小写规则一致、部分唯一索引；旧本地用户可空）、email_verified_at；注册 Pending 用户不获得业务权限。验证 token 仅持久化摘要、有限 TTL、一次性消费与尝试上限，不在日志/回执返回原 token；发送队列与账号创建事务分开，邮件失败可按稳定请求补发、不得重建 Tenant/资金。邮箱只做验证，不自动暗含密码找回功能；登录/注册/验证/重发做按 IP+身份的有界限速、统一不可枚举响应，注册总量与任务配额有服务器上限。发送基础设施 UNKNOWN 是公共上线依赖，不阻止写模型和隔离测试。

### 4.2 C：方法 + 路径 / 操作权限矩阵（目标）

“本地原权限”始终受现有配置、SIM/LIVE 当前授权和风控约束；本文件不授予真实交易操作。DEMO_USER 私有资源均必须满足 Tenant 和 owner/费用等原边界；PLATFORM_ADMIN 的“无”不因它能查看绩效而改变。

| API / 业务操作 | SELF_HOSTED_ADMIN | DEMO_USER | PLATFORM_ADMIN |
| --- | --- | --- | --- |
| POST `/api/auth/login`；本人 renew/logout/change-password；GET `/api/auth/me` | 允许，本地原合同 | 登录可匿名；其余本人且 enabled，Demo 要求 sid/验证状态 | 同本人认证；无 Tenant 业务能力 |
| POST `/api/auth/register`、`/verify-email`、`/resend-verification`（新增） | Controller 不存在 | 匿名限速入口；验证前无业务权限 | 无角色管理特权，不替用户注册 |
| GET/PUT `/api/user/preferences` | 本人 | 本人 | 本人 |
| GET `/api/exchange-accounts`、`/{id}`、本人默认账户相关只读投影 | 本地原权限 | 仅自己的固定 PAPER/SIM 账户，DTO 无 credential 元数据 | 无；用平台只读 summary |
| POST/PATCH exchange-account 建立/修改/enable/disable/set-default/resolve-sim-identity | 本地原权限 | 禁止；账号由注册服务初始化，不开放通用账户管理 | 禁止 |
| GET `/api/strategies`、`/{code}`、`/{code}/versions`、`/{code}/versions/{id}`、版本 page | 本地原权限 | 仅本 Tenant | 无 |
| POST `/api/strategies`、`/{code}/versions`；PATCH `/{code}/status` | 本地原权限 | 允许受限 SIM 策略 DTO；服务器固定 account/env/代码运行模板 | 无 |
| POST `/api/strategies/{code}/trigger` | 本地原权限 | 首版拒绝通用 trigger；仅 Strategy SIM 准入 | 无 |
| GET/POST `/api/research-configs`、GET `/{id}`、POST `/{id}/archive`、page | 本地原权限 | 本 Tenant，父策略同 Tenant | 无 |
| GET/POST `/api/backtest-configs`、GET `/{id}`、PATCH dataset/strategy-version、POST archive、page | 本地原权限 | 本 Tenant，私有或获准共享 dataset；固定配额 | 无 |
| POST/GET `/api/backtest-runs`、GET `/{id}`、POST `/{id}/start` | 本地原权限 | 本 Tenant、受限数据窗口/并发/策略类型 | 无 |
| POST `/api/backtest-runs/{id}/evaluate`；GET `/{id}/evaluation`、`/api/evaluations`、`/api/evaluations/{id}` | 本地原权限 | 本 Tenant；保留 report history | 无 |
| POST/GET `/api/backtest-runs/{id}/publish` | 本地原权限 | 仅本 Tenant 向 SIM 发布；拒绝 LIVE/Shadow materialization | 无 |
| GET `/{run}/sim-orders`、sim-trades、sim-positions、pnl-snapshots | 本地原权限 | 父 backtest run 同 Tenant | 无 |
| POST/GET `/api/research-creation-operations/{operationId}`、`/api/backtest-evaluation-requests/{requestId}` | 本地原权限 | 只允许上述白名单 kind/目标，owner+Tenant 原请求重放 | 无 |
| GET `/api/paper-trading/strategy-sim/analysis/runs[/{id}[/performance]]` | 本地原权限 | 自己的 canonical SIM | 无；走专用平台查询 |
| POST strategy-sim runs 的创建/start；continuous 创建/operation 提交与回执 | 本地原权限 | 仅首次绑定预留账户/run；budget 服务端固定 10,000；禁止通用旧 create 再领资 | 无 |
| POST `.../{id}/continuous/stop`、`/resume` | 本地原权限 | 自己；resume 重新检查 enabled、membership、execution control、容量、冻结绑定 | 无 |
| GET `.../{id}/facts`、decisions、continuous、orders/{orderId}；continuous-capabilities | 本地原权限 | 本 Tenant canonical run/account/order | 无 |
| POST `.../{id}/advance`、orders cancel / refresh-execution | 本地原权限 | 首版拒绝手工推进/取消/刷新；后台 canonical pipeline 拥有推进，避免新增权限面 | 无 |
| legacy `/api/paper-trading/runs/**`、schedules、portfolio/diagnostics 等别名 | 本地原权限 | 首版默认拒绝；如 UI 必需只读别名，必须逐方法显式登记并同 run scope 后才开 | 无 |
| 行情/instrument/currency GET | 本地原权限 | **仅**公共许可目录对应 DTO/窗口，不能通配全部 marketdata GET | 仅同一共享只读面 |
| 行情采集/同步/上传、price sources 写、私有费率、credentials、API Key、LIVE、DH/AI/Python 绑定、充值/提现/transfer、kill-switch、maintenance、全局 scheduler 管理、原始 event/audit/search/monitoring GET | 原有逐接口权限/当前 authority；本任务不扩权 | 全部拒绝 | 全部拒绝 |
| GET `/api/platform/tenants`、`/{tenantId}/summary`、`/{tenantId}/sim-performance`（新增） | 不存在 | 拒绝 | 专用只读服务，分页，最少必要字段，无密码/私有 artifact |
| POST `/api/platform/users/{userId}/disable`、`/enable`（新增） | 不存在 | 拒绝 | 仅启停与审计；不改交易/账务/收益/策略/回测 |
| 未登记的新 API，包括 HEAD、OPTIONS、非 `/api` 管理面 | 本地兼容现有安全合同 | 默认拒绝；HEAD 仅映射已允许 GET，CORS preflight 严格来源 | 同默认拒绝 |

表中组合方括号只是文档缩写，实际 matcher 必须分别列出 HTTP 方法和真实 controller mapping。现有路径在 S01/S02/S10/S12 及相应 Controller，新增 platform/auth 路径为 PROPOSED；实现时由这些 controller 注解生成断言用路径集合，拒绝“所有认证 GET”兜底。不能把整个 `/api/paper-trading/**` 或 `/api/strategies/**` 授予 DEMO_USER。

Self-Hosted 和 Public Demo 使用互斥 SecurityFilterChain 装配；Demo 链末尾 denyAll，健康检查仅最小非敏感 health，Swagger/actuator/read-only provider observation 无匿名放行继承。服务层仍须强制 scope，不能仅换路由 matcher 或菜单。

## 5. D：数据迁移与数据库边界

### 5.1 显式根表与派生关系（PROPOSED）

**首版明确新增 tenant_id 的八张既有表**：`accounts`、`exchange_accounts`、`strategy_definitions`、`strategy_versions`、`research_configs`、`backtest_configs`、`paper_trading_runs`、`marketdata_datasets`。前七个最终 NOT NULL；dataset 增加 visibility，TENANT_PRIVATE 必须非空，SHARED_READONLY 必须为空且仅平台可准入。没有“NULL 等于任意租户”的查询约定。

其他私有业务表的 Tenant 同样是必需身份，但通过矩阵中的父对象取得，而不是给每个事实表冗余回填。订单/成交/账务/位置归 `accounts`；backtest_run 归 backtest_config；评价和发布归 run；Paper 子表归 paper_run。账户没有现代 exchange_accounts 父对象也必须有 accounts.tenant_id。旧数据全部归 NQ_LOCAL，不尝试按 created_by 拆出公网租户。

多父一致性：新增 `(tenant_id, 主键)` 唯一键，根对象间以复合 FK 约束；对未加 tenant_id 的多父表用约束触发器在 INSERT/相关引用更新时比较双方祖先 Tenant/账户/运行。包括 backtest_run 的 config/research/version、publish 的 run/eval/version/target strategy、paper 的 publish/version/canonical account、decision 的 paper/strategy run/order、trade 的 order/account/run、schedule 的 strategy/account、schedule_fire 的 schedule/run。已有 ID FK 仍保留。共享 dataset 是显式例外：同租户私有或已准入共享，不能放宽成任意 NULL。

这些检查必须在 DB 中保护写入及并发，不能只先 SELECT 再无约束 INSERT。祖先 tenant_id 创建后不可更改，使父归属检查不受并发迁移影响；新对象的跨父关系在同一事务验证。资金/ref_type 多态关联同时由原 LedgerPosting/Trade 合同检查；不添加会把 CONTRA 当独立账户余额的通用约束。

对没有统一父 FK 的 audit_logs/event_store/risk_events，分别新增 `audit_log_tenant_scopes`、`event_tenant_scopes`、`risk_event_tenant_scopes`，主键为原表 ID、FK 到原记录，字段含分类、tenant_id、受控业务引用。P 必须有 Tenant，I 不得混入用户 DTO。新事件与旁表同事务提交，缺旁表默认不可对用户读取。历史旁表在迁移时基于明确订单/账户/run 引用归 NQ_LOCAL；无法可靠分类的记录标记 I、不得开放给 Demo，不能推断 actor/trace 为 Tenant。

不可变表（V5/V6/V7/V14/V15/V21/V22/V23/V30 等）不批量 UPDATE；冻结 JSON、checksum、费用、资金、订单 ID、时序、报告、事件 payload 保持原值。旁表与父链优先解决归属。根表元数据回填若会触发现有 admission revision 失效触发器，需以**新版本**迁移调整其条件为真实业务字段变化才触发，保留原经济状态机和原字段保护；绝不能关闭触发器、session_replication_role 或改历史 SQL 来通过回填。新列本身禁止业务迁移归属。

### 5.2 唯一索引、幂等与稳定标识

| 现有身份/索引 | 选定处理 |
| --- | --- |
| users.username、roles.role_code | 保持全局唯一；注册规范化 username/email，并发由唯一索引决胜 |
| strategy_definitions.strategy_code、strategy_versions(code,version) | 改为 `(tenant_id,strategy_code)`、`(tenant_id,strategy_code,version)`；版本到 definition 的 code FK 同步复合化；maxVersion 同 Tenant。全局主键 ID 仍保持全局唯一，不改旧 ID |
| accounts.account_code | 保留全局唯一；新账号服务端生成稳定含 Tenant 身份的 account_code，保留 bridge 的确定名称规则，不能把用户名拼进外部凭据 |
| exchange_accounts 默认和别名 | 首版保留 owner 唯一及 V3 user-default；加 Tenant 归属一致性索引，Demo 仅一个默认账户。不放松历史 external_ref 唯一约束 |
| orders(account_id,client_order_id)、orders(account_id,dedup_key)、strategy work(account_id,client_order_id) | account_id 本身全局唯一且不可跨 Tenant，保留即可；所有查重先限 Tenant/account。保留策略单 run、窗口准入和 decision 请求身份 |
| ledger_entries 全局 idempotency_key | 保留全局唯一和原键；新 key 必须含稳定 server-generated account/run 身份。Tenant 注册注资重放不得生成新键；不因多租户把历史键重新计算 |
| research/continuous/alert/account 等 operation UUID 全局 PK | 保留全局 UUID PK，读取+重放比对 owner、Tenant、kind、原请求；同 UUID 被其他 Tenant 占用返回不泄漏目标的冲突，不给第二次副作用；新增可有 `(tenant_id,operation_id)` 查询索引但不需要删除全局 PK |
| backtest_eval_reports.run | 维持 V32 允许多报告；按 run + evaluated_at/id 最新索引，不恢复旧 unique |
| paper.canonical_account_id UNIQUE | 保留，一账户一固定 SIM 实验；不得直接删掉以“复用账户”而混算绩效 |
| trades(exchange,exchange_trade_id)、(exchange_code,exchange_trade_id) | 保留已有外部事实去重范围；PAPER 新 trade ID 应全局 server-generated。没有重复事实证据时不扩大/削弱外部 ID 合同 |
| 市场 bars 范围唯一键 | 保留共享自然键，不添加 Tenant 前缀复制行情 |
| LIVE single-open/single-active/global-place/global-cancel | Demo 完全不可达；不能为了多用户改成 tenant 作用域 |

Tenant 根表的 list/count/时间范围索引以 tenant_id 为前缀；派生表使用父 ID + 排序时间/稳定 ID，符合现有有界窗口；不对全部列机械加索引。全局 worker 枚举与用户查询分开，按 Tenant 轮转，限制返回/任务/并发/队列/结果大小。

### 5.3 PostgreSQL RLS 决策

**选定：Public Demo 对主要私有业务表采用 RLS 作为第二道防线，在任务 2 内完成；应用层 T/D 与数据库一致性约束始终必需。Tenant 基础任务先完成模型和请求上下文，不以未验证 RLS 宣称隔离完成。** Self-Hosted 仍绑定 NQ_LOCAL，不需要第二套 Schema。

1. 根表 policy 使用 transaction-local `current_setting('nq.tenant_id', true)` 与 tenant_id 比较，USING/WITH CHECK 都覆盖；未设置、空值或非法身份拒绝。派生表 policy 用已索引的父链 EXISTS，不能读取全局后应用过滤。共享目录明确 shared_read policy，普通用户没有共享写 policy。
2. 外层事务开始后，在**同一受 Spring 管理的连接** `set_config('nq.tenant_id', serverTenantId, true)`，最外层 transaction template 统一设置；read-only 查询也要事务。提交/回滚自动清除；不能连接借出后 SET 会话变量、不能只在 HTTP filter 取得另一条连接设置。REQUIRES_NEW 必须重新绑定；异步线程从持久化工作恢复并重新设置，不继承池上次 Tenant。事务/连接获取失败 fail closed。
3. Demo 运行角色必须 NOSUPERUSER、NOBYPASSRLS、非表 owner、无 SET ROLE 到 owner 权限、无 DDL/TRUNCATE；迁移 owner 为独立离线/安装身份。私有表 ENABLE/FORCE RLS；序列/函数仅授最小权限。release compose 当前使用 DB 初始化身份作为 app 身份，必须分离，不是换一个 Spring profile 就完成。
4. V1 的 `SECURITY DEFINER` admission/prepare/finalize/guard 与 owner 判断必须兼容：受限函数及其依赖 owner 判断的状态表由同一个受控 NOLOGIN owner 持有，运行角色不能切换到它；不能随意换函数 owner 使 `current_user=relowner` 保护失效。固定 search_path，撤销 PUBLIC EXECUTE；每个可执行函数先核对 server scope、account、strategy/run/order 的 Tenant，内部读取不能因 definer 绕过 owner/RLS 而绕过归属。需要 FORCE RLS 的表对 owner 路径仍执行策略并保持 transaction-local context，必要的全局扫描入口单独只返回有界 work identity，不开放任意数据写或任意 tenant override 参数。以新迁移替换函数定义，不能删原状态机保护。
5. 平台统计用独立 SELECT-only 数据源/DB role 和固定只读聚合接口；无 BYPASSRLS、无任意 SET Tenant、无经济 DML。以专用授权视图/受限只读函数访问汇总；不对普通 runtime role 开“is_admin=true”旁路。平台启停走另一个仅用户/控制表写授权的 port，不注入 Ledger/Order writer。
6. GUC 是应用事务上下文，不是防任意 SQL 执行或凭证被窃的独立认证机制；RLS 不替代参数化 SQL/DB 最小权限。迁移/备份工具权限与服务账号分开。当前实际运行角色属性 UNKNOWN，只能以实现阶段真实非 owner PostgreSQL 测试证明。

必须覆盖同池 A→B→无 context、事务回滚、异常、线程复用、REQUIRES_NEW、直接 SQL 越界、复合父 ID、受限函数越界、平台 role 写拒绝。若某表/函数尚不适配，依赖路径保持禁用，不将 RLS 缺口忽略成 PASS；任务 2 内定向修复，不启动新一轮全量架构审查。

### 5.4 Forward-only 迁移顺序与恢复

版本号按实施时当前最大版本递增；本基线最大 V34，下面 V35+ 仅顺序示意，不预占未来号。

1. **Foundation migration**：tenants、tenant_memberships、NQ_LOCAL，旧用户本地 Membership，新增全局角色；不把旧 ADMIN 变平台管理员、不更改密码/资金。新 Demo 表和入口保持未装配。
2. **Ownership migration**：八根表 additive columns / dataset visibility；先补 metadata-only 回填条件及不可变保护，再回填 NQ_LOCAL，检查 NULL/孤儿/多父不一致。出现无法归属的关联停止对应约束收紧并报告行的非敏感 ID/计数，不任意修复资金或指向别的账户。私有 dataset 历史默认本地。
3. **Consistency migration**：建立租户复合 unique/FK、关联一致性触发器、必需索引；替换策略 code 的全局唯一/FK；NOT VALID FK/CHECK 在回填后 VALIDATE，最终 NOT NULL。parent 绑定缺失等问题必须先解决，不以 nullable 长期放行。数据规模 UNKNOWN，索引锁预算需实现时测量；大表并发索引单独 non-transactional Flyway migration，不能混在事务脚本。
4. **Scope / RLS migration**：事件/审计/risk 旁表历史归属，更新所有直接 writer；非 owner 角色授权及 canonical definer 校验，启用 policy，验证 missing-scope 拒绝。DDL 和应用发布顺序保证旧单租户 app 只在维护窗口运行，不能让旧版继续写未绑定对象。
5. **Demo lifecycle migration**：邮箱验证摘要/发送 outbox、稳定注册回执、tenant_execution_controls、tenant_sim_accounts、平台启停回执与专用审计。业务入口只在全部 schema/capability contract 匹配时开放。

新增表均拥有稳定业务语义，不含 Gate/Phase/批次命名。tenants 的 inactive 与 execution controls 是授权/运行控制事实，不是用户交易经济事实。tenant_sim_accounts 只登记账户与预留 run 身份，不存一份余额/收益。

为避免后续另起模型规划，Demo 生命周期新增结构的最小合同如下（均为 PROPOSED，尚未创建）：

| 新表 | 必需身份、约束及状态 | 数据类别 |
| --- | --- | --- |
| email_verification_tokens | token_id PK、user_id FK、token_hash UNIQUE、expires_at、consumed_at、attempt_count；原 token 不落库，过期/消费更新按条件原子执行 | I / 本人验证入口 |
| verification_email_outbox | message_id PK、token_id FK、受控模板/收件身份、发送状态/次数/next_attempt_at；有界退避、超时和过期清理，敏感材料不进业务回执 | I |
| user_registration_operations | registration_id PK、规范化身份指纹、user_id UNIQUE FK、初始化 tenant/account 结果引用、状态/时间；不存密码原文/可离线验证密码的请求摘要；Pending→Initialized 同账号锁与稳定请求串行 | I / 本人安全回执 |
| tenant_execution_controls | tenant_id PK/FK、suspended、requires_manual_resume、execution_epoch 单调、updated_at；启停不删历史 | P / 平台仅启停编排可写 |
| tenant_sim_accounts | tenant/account/exchange_account 唯一及复合 FK、预留 run、可空实际 run FK，见第 7 节；无余额字段 | P |
| platform_user_operations | operation_id PK、platform actor FK、target user/tenant FK、enable/disable、原请求指纹、结果/前后版本/原因/时间；同 ID 同输入重放，完成结果不可变 | I |
| public_marketdata_catalog | catalog_id PK、dataset_id FK、来源/许可依据引用/展示字段及窗口上限/有效期/准入状态；仅受控发行管理写，不赋予 PLATFORM_ADMIN 经济或采集管理权限 | S / 未准入内容 I |

上述表连同 tenants/tenant_memberships 及三个 scope 旁表是新增范围。共享 catalog 不保存复制的 K 线；任何需要投递的验证材料采取专用短期受保护存储，发送完销毁可恢复材料，持久业务日志与回执仅引用 token_id。发送基础设施与密钥管理仍需实现验证。

迁移前记录 Schema/Flyway 身份并备份整库、角色/授权及相关 artifact；在隔离环境验证恢复后旧订单/成交/分录/余额/报告/冻结 JSON/摘要逐项相同，新增 Tenant metadata 可见。保留行数、ID/关键经济列的排序摘要以及引用一致性检查，不只数行。

回退优先关闭 Demo 注册/入口和租户推进，保留新增 Schema 和全部事实，部署兼容前版。已有两个公网 Tenant 后**不能**回滚到全局可读旧代码，也不能合并到 NQ_LOCAL；需要保持隔离的兼容修复或从完整维护窗口备份恢复。恢复会损失备份后的注册与运行事实，须明确恢复点并获操作授权；禁止 down migration 删除 tenant_id/成员/分录。基础实现的恢复验证用任务私有数据库，不连接生产。

## 6. 核心业务调用链与异步隔离

下表是实施后的边界；当前差距以第 2/3 节对应事实为准。

| 节点 | 创建绑定 | 查询/更新/删除边界 | 异步/关联 ID 要求 |
| --- | --- | --- | --- |
| Tenant/User | 注册事务服务器生成 TenantId/UserId，Membership 绑定 | 认证 DB 实时 active 校验；用户禁用不删行 | 发信用稳定 registration identity，不持久化密码/token 明文 |
| Strategy→Version | definition/version 显式 tenant_id，account 同 Tenant | code 与版本查找、maxVersion、状态/归档全含 Tenant | 不以 created_by、字符串 code 或 JSON 中 owner 授权 |
| Research→Backtest | 研究/配置根表绑定 Tenant，run 冻结同 Tenant 父对象 | 配置绑定、run启动/执行回写、列表/计数一致 | 同步服务保持当前事务；未来工作队列必须持久化父 run，恢复时重新查 Tenant |
| Evaluation→Publication | 从可信 run 派生 Tenant，不接受客户端指定 owner | 精确 reportId、最新报告、publish与全部引用一致 | artifact 定位经授权 publish，快照 JSON 不当认证事实 |
| Paper/Continuous SIM | 从已授权 publish + 固定 tenant account 创建 | 旧 create、start/stop/advance、回执、aliases 都同 Tenant | activeIds 仅枚举；每 run 进入可信 scope 后再读写，不把全局枚举当特权 |
| Risk→Order | StrategyExecutionGateway 接受同 Tenant strategy/account；仍走 canonical Risk/Execution | order 与 run/account/environment 匹配；客户端不直指定资金账户 | worker claim/恢复根据持久化 work 关联 account Tenant，再次检查执行资格 |
| Trade→Ledger→Position | 同 canonical order/account，原精度/费用/幂等合同 | 一致性约束 + account scope；没有旁路写余额 | matching、reconcile、retry/recovery 在业务事务中重建 scope；UNKNOWN 按既有恢复不盲重做 |
| Performance | 只读原成交/分录/已冻结可见 mark | Tenant→run→account→经济事实；保留 UNAVAILABLE/窗口受限 | 平台 read model 批量聚合不改写收益，不把多个 run 资金相加冒充 Tenant 收益 |
| Audit/Event | 服务器 actor/user/Tenant + scope 旁表同事务 | 用户只读允许范围，平台管理操作独立审计 | event replay 从可信旁表和父对象校验恢复，不信任 payload tenant 值 |

ThreadLocal 只负责当前执行段传递，不是持久化身份。工作记录保留稳定 run/account（必要时加 tenant 身份在新工作 envelope 元数据），重启重新读取并验证父对象；不把 HTTP 的 SecurityContext、JWT、请求 header、事务或数据库连接复制到异步线程。每项任务 finally 清 context，重试保持原业务幂等身份并重新查 enabled/epoch。

### 6.1 禁用与调度并发语义

当前 `ContinuousSimRunService.pollOnce/pollActive`、`PaperMatchingService.matchOnce`、strategy schedules/dispatch/recovery 没有统一用户 enabled gate。只撤销 JWT 无法阻止已落库后台任务。

PROPOSED：`tenant_execution_controls(tenant_id PK, suspended, requires_manual_resume, execution_epoch, updated_at)`。每个 SIM admission、advance、matching 的经济提交前都通过共享执行许可：在同一数据库事务按统一顺序锁 user/membership/control，再进入 account/run/order 锁；批量跨租户按单租户拆事务，不能拿全局交易锁。禁用命令与这些许可互斥，等待已经持有许可的有限事务完成。

- 禁用成功事务：user enabled=false、auth_version 单调增加、全部 session revoked；control suspended=true、requires_manual_resume=true、epoch++；管理操作/审计同事务。Tenant/Membership 可标 inactive，但不删除；平台自身身份不能被该接口当普通 Demo 用户处理。
- **线性化承诺**：已取得许可并先提交的经济事实保留；disable 返回成功后，无新的信号/准入/撮合/账务推进可提交。处于 public feed 网络读取阶段的 worker，返回后必须重新取得许可/校验 epoch，不能拿旧凭证继续提交。网络调用不持有上述锁；连接、语句、事务和网络都有有限超时。
- 已有未成交订单/预留/游标保持原事实，不为停用伪造成交、手工解冻、补收益或自动取消。后台不再 matching；恢复时由原 canonical reconciliation/state machine 收敛，不能回滚先前成交。
- 只跳过扫描不足以证明停用：正在执行、显式 API、重启恢复和所有匹配入口均需 gate。顺序不可因新增许可与当前 account/run/advisory 锁反转，相关真实 PostgreSQL 并发用例为验收项。
- 重新启用：enabled=true、必要的 Membership active，保持 session 已撤销；不清 suspended/manual_resume。用户重新登录后显式 resume，复核冻结绑定、历史执行状态、原预算、市场可用性和容量再放行，不注资。
- 现有 global 8 上限保留为安全容量；增加 per Tenant 1、按 Tenant 轮转的有界枚举，禁用项不占轮询批次，resume 竞争仍用原 admission 锁。首版总并发 8，应向第 9 个启动用户明确返回容量限制，不能把注册人数承诺成无限持续运行能力。
- 共享市场拉取按 exchange/market/symbol/interval/window 有界缓存/合并请求，多 Tenant 复用同一次行情；continuous_sim_bars 仍保存必要 run 输入证据，不能因节省副本丢失运行的不可变输入。

## 7. Public Demo 注册与资金初始化

本段是 PROPOSED，不宣称当前支持注册注资。选定最小方案与一次运行一账户保持一致：

1. 注册邮箱验证前保存 Pending 用户与可恢复注册身份，不创建可交易权限。验证成功后，同一受限 READ_COMMITTED 数据库事务创建 Tenant、DEMO Membership、execution control、一个 PAPER/SIM exchange_account，并扩展正式 `SimAccountIdentityBridge`/`CanonicalLegacyAccountBridgeService` 的 SIM 专用分支得到 canonical accounts ID：PAPER 仅允许受控 Demo 初始化、同 Tenant/owner、零 credentials、稳定 account_code；LIVE 分支仍只接受原范围，不能简单扩大其通用 venue allowlist。Tenant 新建后由注册编排在同连接绑定该服务器生成身份，再写受 RLS 保护的账户；不得借匿名请求的 header 设置 GUC。服务器固定环境 SIM。
2. 新增 `tenant_sim_accounts(tenant_id PK, exchange_account_id UNIQUE, account_id UNIQUE, reserved_paper_run_id UNIQUE, bound_paper_run_id nullable UNIQUE, created_at)`；预留 `ptr-UUID` 是服务器生成身份，**不是已经存在的运行**。绑定列 FK 到正式 paper run，预留列在尚无策略发布时不设虚假 run FK；account/exchange_account 与 Tenant 复合 FK。该表只存身份、不存余额或收益。
3. 在同事务、同账户货币锁下复用 `SimCashFundingPort.fundOnce(accountId, reservedPaperRunId, 10000.00000000, trace)`，保留 CASH/CONTRA 及原快照语义。注册回执只引用 user/tenant/account/预留身份/原注资分录；全局唯一注册身份与 tenant/account 唯一键使并发重复验证只返回原结果。余额由 canonical Ledger 读取。禁止调接口先建账户、事务外再盲试注资。
4. 首次创建策略 SIM 时，内部 typed 命令从 Tenant 绑定表取得预留 run/account；增强 `PaperTradingRunService.create` 的服务端专用身份参数，旧 Self-Hosted 入口仍随机建 run。复用 `StrategySimRunService` 的原冻结输入、定义、执行路径，Demo 分支只改身份/账户分配：使用已注资账户和预留 run，不再 INSERT accounts 或新 fundOnce。原 funding ref_id 与新 run ID 一致，原 performance 的一次 funding 检查仍成立。published/version/account 必须同 Tenant。`JdbcAccountTradeEnvironmentAuthority` 对 PAPER 走明确的 canonical run + Tenant 绑定分支，不把现代 PAPER 映射再 UNION ALL 成第二条；未绑定/未运行时没有交易 authority。不能用 DISTINCT 隐藏其他真正的歧义，OKX/BINANCE 原环境解析保持原拒绝歧义合同。
5. 先验证正式 publish/回测/费用/数据条件，再在同一事务插入 paper run、绑定 canonical_account_id 与 tenant_sim_accounts.bound_paper_run_id、冻结策略定义。失败不改变原注资或占用绑定；响应未知重放同 operationId，不生成新账户/run/资金。绑定后拒绝另一个发布记录，新策略仍可研究/回测。
6. 初次注资至首次 SIM 的空闲期可能较长；UI 显示“模拟账户已初始化、尚未绑定运行”，不假装 Continuous SIM 已运行。资金注入发生时点保留原时间，不能把它改成首根历史 K 线时间或把 10,000 当收益。已有 Self-Hosted 账户/资金完全不走此初始化，不补发 10,000。

需要修改的不是第二套交易/资金系统，而是原服务的受限身份分配与准入分支。原 SimCashFundingService 的输入校验、空账户要求、环境、两条分录完整性及精度不放松；首次绑定必须真实证明账户没有任何非预期经济事实。折扣币注资、私有实际费率、用户自定 budget 不进入首版 Demo，采用显式标注的公共研究费用假设。

邮件 outbox 仅发送验证邮件，内容由服务器模板构造；本评估没有发送任何消息。初始化平台管理员采用部署时独立一次性受控 CLI/安装步骤：从安全输入读取随机高强度初始口令、只建 PLATFORM_ADMIN、强制首次改密并生成审计；不写入源码/镜像/日志，不用默认开发密码，初始化结束不再有 HTTP bootstrap。Demo 误启旧 bootstrap 或 LIVE/private provider 选项必须启动失败。这里的实施方案不是对本轮实际初始化的授权。

## 8. 平台管理员与发行物装配

### 8.1 Platform Admin 仅有五类能力

Tenant list / summary / SIM performance 为专用只读服务，按 Tenant/run/account 读 canonical 投影；不把目标 TenantId 塞入普通 DEMO_USER Context 调用任意业务 service。用户列表最少字段，分页/时间窗口/单次 Tenant 数量上限固定，避免逐 Tenant N+1 和全库无界分析。绩效查询失败或样本不足保留原状态，不手工制造 0 收益/PASS，不把模拟资金注入计入利润。

Enable/disable 仅改认证、Membership/执行控制和管理回执/审计；DTO 不接受余额/收益/订单/成交/持仓/历史报告字段，service 不依赖它们的写 port。新增管理操作稳定 UUID、actor、target user/tenant、reason、before/after enabled/auth_version/epoch、时间/trace，重放返回同结果；失败不能只有用户 disabled 而 gate 仍开。用户重新启用不自动恢复 SIM。

### 8.2 发行隔离的选定最小方案

不建立第二仓库/引擎。新增一个 **`nq-public-demo` 可选 Maven 模块**，仅包含注册/邮箱验证/平台 API 及其编排；Tenant 模型与 ports 在 nq-common/nq-auth 或所属 core domain，JDBC 在 nq-infra，经济写入仍 nq-ledger/nq-core/nq-scheduler。nq-app 的显式 Maven `public-demo` 构建 profile 才依赖该模块；默认 self-hosted 不把此 jar 放入 BOOT-INF/lib，因此全包扫描也找不到注册/平台 Controller。核心 security 根据不可变发行 identity 选择互斥配置；Demo 编排不能在 nq-app 到处散落。

构建绑定不可变发行 identity（SELF_HOSTED/PUBLIC_DEMO），服务端发布 capability 供 UI 交叉校验；运行参数不能把 self-hosted 变成含注册类的 Demo，也不能把 Demo 提升到 LIVE。Demo 除配置固定 false 外还需装配/启动 guard 拒绝 LIVE、真实 PLACE/CANCEL/private provider/credential 管理/transfer/withdraw/解除 kill switch 及相关 profile；允许受限公共行情出站不代表允许私有 API。两发行物共享相同 Risk/Execution/回测/Ledger 类，不能新增替代引擎。

前端增加编译期发行入口文件：self-hosted 与 public-demo 分别导入自己的 route tree；注册/验证/platform admin 页面只从 Demo entry 可达，打包检查 self-hosted 的所有 chunk 不含这些模块，不能只是运行时 if 隐藏菜单。共用其余业务组件，Demo 配额/缺权限/未验证邮箱/禁用/需手动继续等状态明确展示；后端每方法白名单仍是权威。清理登出/禁用后的 React Query 缓存，query key 不跨不同登录身份复用。

release/docker 两个 Dockerfile 传入一致的 Maven/Vite distribution 参数；release/build-package、source-manifest/policy 和安装器校验发行 identity、镜像、Schema、source hash。Self-Hosted 默认包不含 Demo module/pages/入口；Demo 包不得出现开发 bootstrap 配置，不能从环境变量打开真实交易。只新增发行变体，不改写已发布 v1.0.0 tag/Manifest/历史 allowlist。实际包构建、联网、部署与发行需要另有授权，本轮均未执行。

## 9. E：最多四项可直接开发的实施任务

执行次序 1→2→3→4；3 中可先验证本地闭环，4 完成前不开放公网。最小闭环在 2 的隔离夹具和 3 的两用户注册链逐步落地，不以再写规划代替开发。以下是当前代码范围，不是允许随意扩展全仓。

### 任务 1：NQ-V11-PUBLIC-DEMO-TENANT-FOUNDATION

| 项目 | 要求 |
| --- | --- |
| 目标 | tenants/memberships、NQ_LOCAL、可信请求 TenantPrincipal/Context，Self-Hosted 兼容，DEMO_USER/PLATFORM_ADMIN 权限身份，私有服务缺上下文拒绝；先实现可用于两个隔离测试主体的基础闭环 |
| 实际代码范围 | AuthUserRepository/AuthUserProfile、JdbcAuthUserRepository、DbAuthService、CurrentUserProfileService、TokenUserValidator/JwtAuthenticationFilter、SecurityConfiguration；新增 Tenant 模型/查询 port/JDBC 与 forward Migration；Demo 登录初始化判断不再依赖 ADMIN 角色存在 |
| 允许修改模块 | nq-common（轻量 ID/context）、nq-auth、nq-security、nq-infra、nq-app；必要的 auth DTO 在 nq-api；所属测试及与契约直接相关文档 |
| 禁止事项 | 无注册/新资金/交易事实变更、无公网启用、无 USER=TENANT 替代、无客户端可选 Tenant、无改旧 Migration/默认空上下文 NQ_LOCAL 兜底；不提交或发布未授权 Git 操作 |
| 测试要求 | 真实 PostgreSQL forward 迁移已有多用户到 NQ_LOCAL、空库、重复执行结果；HTTP 篡改 header/body/path Tenant、旧/新 JWT、失效 Membership、缺 context、异常清理；Self-Hosted ADMIN/OPERATOR/VIEWER 旧权限目标测试；Demo 未初始化与 PLATFORM_ADMIN 登录正确区分 |
| 验收标准 | 服务器唯一解析 Tenant；一用户一 Demo Tenant 与本地多用户兼容；缺/错身份 fail closed，跨请求/线程不串；无历史金额/订单/报告/密码重写；尚未保护的业务 API 对 Demo 全拒绝，不声称端到端隔离已完成 |
| 回滚要求 | 新增 schema 可保留；关闭未开放的 Demo 装配，兼容原 Self-Hosted；隔离库恢复证明旧用户/角色/会话事实不被污染；已有多 Tenant 时禁止旧全局可见应用接管 |

### 任务 2：NQ-V11-PUBLIC-DEMO-TENANT-CORE-ISOLATION

| 项目 | 要求 |
| --- | --- |
| 目标 | 在原核心链完成第 3 节 T/D、八根表归属、关联一致性、scope 旁表、RLS 与异步 execution gate；两租户真实研究→回测→评价→发布→SIM→经济事实闭环，无注册 UI 依赖 |
| 实际代码范围 | 矩阵列出的 strategy/research/backtest/eval/paper/account/trading/ledger JDBC；StrategySimRunService/AnalysisQuery/PerformanceQuery、PaperTradingRunService/PaperRunReadAccess、ContinuousSim*、PaperMatchingService、StrategyExecutionGateway 与 dispatch/recovery/schedule 边界；nq_* definer 函数及 RLS 新版本迁移；聚合/search/alias 按白名单保护或 Demo 拒绝 |
| 允许修改模块 | nq-core、nq-research、nq-backtest、nq-eval、nq-ledger-contracts/nq-ledger、nq-scheduler-contracts/nq-scheduler、nq-infra、nq-auth/nq-security、nq-api、nq-app；只涉及 Tenant 传递/校验/准入的必要端口与调用方 |
| 禁止事项 | 不复制撮合/回测/Ledger，不直接写经济结果、不删幂等/状态机/费用隔离/全局 LIVE 硬门，不重写 frozen JSON/历史迁移，不开放尚未隔离的 legacy/Shadow/运维面，不把平台角色当 ADMIN |
| 测试要求 | 两 Tenant 同 code/version/客户端请求 key；逐根对象 A读写B、嵌套 ID 混搭、列表/count/search/export/aliases；真实 PG 非 owner/RLS/受限函数/同池复用；真实 HTTP/AppContext；matching 与 disable 竞态、重启 dispatch/recovery、pending/UNKNOWN 稳定身份；canonical Order/Trade/Ledger/Position 及费用/并发/恢复原回归 |
| 验收标准 | 全部对 Demo 开放路径查询和写入均限 Tenant；未开放接口默认拒绝；账户/策略/run/订单引用不能跨 Tenant；disable 线性化后无新经济提交；原金额/精度/幂等及亏损事实保留；RLS/definer 不存在明知的越界路径。高风险候选在验收前做一次真正独立候选审查，定向修复后验证，不做 review-of-review 或新规划循环 |
| 回滚要求 | task-owned PostgreSQL 备份/恢复及关键事实摘要一致；新 Schema 保留，关闭 Demo 推进，隔离兼容版本回退；不跨租户合并，不删除事实或修改已发布迁移 |

### 任务 3：NQ-V11-PUBLIC-DEMO-REGISTRATION-SIM-INITIALIZATION

| 项目 | 要求 |
| --- | --- |
| 目标 | 按第 7 节注册、验证邮箱、一个 Tenant/独立账户/一次 10,000 USDT，并用原 SIM 首次绑定完成两用户闭环 |
| 实际代码范围 | 新 nq-public-demo 模块注册/验证编排与 DTO；auth users/email、验证摘要/outbox/注册回执 JDBC；tenant_sim_accounts；正式 account bridge 的 PAPER/SIM 专用分支、JdbcAccountTradeEnvironmentAuthority 双命中处理、SimCashFundingPort/Service 原合同调用；PaperTradingRunService 内部预留身份参数、StrategySimRunService 身份分配分支、ResearchCreationOperationService/ContinuousSimOperationService 稳定操作；Demo 前端注册/验证与账户未绑定状态 |
| 允许修改模块 | 新 nq-public-demo；nq-auth、nq-infra、nq-api（共用 auth DTO 如需）、nq-app（模块装配）；nq-core 的账户 port、nq-ledger-contracts/nq-ledger、nq-research/nq-scheduler 仅必要身份分配；frontend 对应页面/会话状态；必要迁移与测试 |
| 禁止事项 | 不允许自选角色/Tenant/env/budget/凭据；不向旧账户补钱、不重放新 UUID 重复注资、不开放 API Key/真实资金、不使用默认管理员密码、不建立虚假已运行 paper record，不扩大到多账户资金划转 |
| 测试要求 | 并发同 username/email/注册 UUID/验证 token，邮件失败及过期/一次消费；注册事务各失败点、响应丢失/重启后重读；首次 run 绑定失败/并发、不同 publish 竞争；每 Tenant 恰一账户、恰一 funding CASH/CONTRA，10,000.00000000 精度；PAPER 桥接/未绑定不可交易/运行后现代映射不双命中、OKX/BINANCE 原歧义拒绝及 LIVE 原边界；既有 PG 资金与 canonical 绩效/费用原回归；HTTP + 前端注册至本人 SIM 路径，另一用户不可读取 |
| 验收标准 | 未验证无业务权限；验证后完整初始化或原子失败；重放不增资；首次 SIM 使用原账户/预留 run，无第二次 mint；Self-Hosted 不因新增模块依赖获得注册能力；默认开发 bootstrap 在 Demo 拒绝启动；未接真实邮件发送器时仅隔离夹具验证，标记公共上线未就绪 |
| 回滚要求 | 关闭注册/验证消费和 SIM 新准入；保留用户/Tenant/账户及分录；outbox/回执可恢复；不删注册用户去“撤销”资金；隔离库证明恢复无重复注资 |

### 任务 4：NQ-V11-PUBLIC-DEMO-PLATFORM-ADMIN-DISTRIBUTIONS

| 项目 | 要求 |
| --- | --- |
| 目标 | 五类专用平台能力、启停审计/手动恢复语义；Self-Hosted / Public Demo 的模块、前端、包身份真正隔离，Demo 永不启用 LIVE |
| 实际代码范围 | nq-public-demo platform controller/service/read query/管理回执；用户启停与 execution controls；SecurityConfiguration 的两发行链/启动 guard；nq-app 与新模块 Maven 装配；frontend entry/router/navigation/login/account/SIM/platform 状态；release Dockerfiles/build-package/source policy/manifest/安装参数 |
| 允许修改模块 | nq-public-demo、nq-auth、nq-security、nq-infra、nq-app、必要 nq-api DTO；frontend；release 与对应构建/测试脚本；Scheduler 只做前面 gate 接入所需收尾 |
| 禁止事项 | 管理员无余额/收益/订单/成交/持仓/Ledger/回测修改 port；不以隐藏菜单代替后端拒绝；不改已发布 tag/Manifest/历史 allowlist；无本轮授权外部署/发信/推送/PR/合并/Release |
| 测试要求 | DEMO_USER 调五类平台接口拒绝；平台管理员调全部经济 mutation 拒绝；只读 DB role 直接 DML 拒绝；disable 与 JWT renew/worker/matching 竞态，重启保持 suspended、enable 不自动 resume；两种 Boot AppContext 与实际打包 jar/chunk 检查；恶意运行参数开启 LIVE/bootstrap/private profile 启动失败；UI 对应 role/禁用/未知结果状态检查 |
| 验收标准 | self-hosted jar 不含 Demo module、无注册 Controller/页面/platform chunks；Demo jar 明确 identity，只有白名单权限；专用平台只读统计来源 canonical 且无 N+1；仅五类管理能力；需要的市场展示授权和发信/域名安全配置具实证后才能另行请求公共上线资格，不能以打包成功冒充上线批准 |
| 回滚要求 | 停注册/停租户推进，回退隔离兼容包，不回退全局可读旧包；保留审计、会话撤销和资金历史；按完整备份恢复策略操作，实际生产恢复须另有授权 |

## 10. 风险、阻碍与验收解释

| 级别 / 分类 | 已确认缺口或未知 | 实施处置 / 是否阻塞本轮结论 |
| --- | --- | --- |
| P0 / 公网开放 BLOCKING | 当前 GET authenticated、写侧 ADMIN/OPERATOR，无通用 Tenant；legacy/read aliases 和 worker 存旁路 | 任务 1/2/4 必改；阻止直接公网注册，**不阻止 Tenant Foundation 开发** |
| P0 / 公网开放 BLOCKING | 开发固定 bootstrap、Demo 真交易/私有 provider 尚无不可覆盖发行 guard | 任务 3/4 替代初始化/拒绝装配；本轮不暴露任何服务 |
| P0 / 经济正确性 BLOCKING | 每次 SIM 创建新账户新预算，不能直接当租户初始余额；禁用 JWT 不阻断 worker | 第 6.1/7 节选定一次注资/预留身份/执行许可；保留原 canonical 合同，不手工修经济事实 |
| P1 / 公网开放 BLOCKING / UNKNOWN | 真实市场数据是否具有公共展示许可、许可范围/频率/转发限制 | 由发行责任方取得依据并配置审核目录；未具证据时没有公开展示资格。能在任务私有环境用合法夹具实现/验证，不新开规划循环 |
| P1 / 实现验收依赖 | DB owner/definer/RLS、触发器副作用、真实数据孤儿/规模未知 | 任务 2 真实非 owner PG/迁移/恢复验证；发现仅阻塞对应不可用路径，不能改历史事实凑通过 |
| P1 / 公网容量限制 | global Continuous SIM 8、注册/研究任务缺少面向不可信用户配额 | 保留总容量，加每 Tenant 1、限速/并发/窗口，清楚返回容量；不承诺无限用户持续运行 |
| P1 / 公网上线依赖 / UNKNOWN | 验证邮件通道、公开 TLS/代理/出站策略/实际密钥管理 | 任务 3/4 实现与隔离测试后由部署资格验证；不从当前源码推断配置已安全 |
| P2 / NON_BLOCKING | 各服务存在直接 JDBC、legacy Paper、已冻结 code/JSON、未绑定 artifact 等历史形态 | 仅针对矩阵必要入口修隔离或 Demo deny；不做无关架构重构，不改写冻结 artifact/证据 |

`READY_FOR_TENANT_IMPLEMENTATION` 只表示已有可执行最小方案与明确代码范围。Auth=可复用/需变更；Tenant=缺失；Database=八根表+派生约束/策略+新身份/控制表；Research/Backtest/Evaluation/Publication=需通用 Tenant；SIM/Trading/Ledger=复用经济核心、补入口/工作归属；Scheduler=需执行 gate；Platform Admin/Distribution=新增。它不等于 READY_FOR_PUBLIC_DEMO、端到端隔离已证实、生产就绪、迁移通过或交易授权。

### 10.1 既有测试阅读与实现验证锚点

- [AuthSessionPostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/AuthSessionPostgresIntegrationTest.java)：upgrade preserves user、renew 最大生命周期/idle、logout竞态、密码代际撤销、会话上限/owner/重启。可扩展 Tenant/disable，不以重新发 token 替代原会话合同。
- [PaperRunReadAccessTest](../../../backend/nq-api/src/test/java/com/guidinglight/nexusquant/paper/api/web/PaperRunReadAccessTest.java)：明确 legacy 与 canonical 读取及命令的兼容边界；现有预期不能被误读成全部接口已隔离。
- [StrategySimPostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/StrategySimPostgresIntegrationTest.java)：包含并发同账户预算、decision恢复不重复 canonical order、跨 JVM 恢复、冻结输入变化等用例；后续修改须保留，并加两 Tenant 交叉身份及一次注资闭环。
- [SimAccountIdentityBridgePostgresIntegrationTest](../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/account/SimAccountIdentityBridgePostgresIntegrationTest.java)：新账户和并发 bridge 解析；Tenant 不能绕过该稳定账户身份。
- [ContinuousSimRunServiceContextTest](../../../backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/paper/ContinuousSimRunServiceContextTest.java)、相关 ContinuousSim/HTTP restart、费用与 Funding PG 用例：后续按改动范围复用并补充真实 disable/epoch 竞态。

以上均为 `VERIFIED_EXISTING` 测试源码已检查，**本轮未执行**。实现阶段高风险数据库/交易隔离按仓库统一回归合同做一次独立候选审查和必要真实验证；这是实现验收，不是再生成第二份架构规划/无限审查。

## 11. 本轮交付与停止点

唯一交付：`docs/current/plans/NQ_V11_PUBLIC_DEMO_MULTITENANT_IMPACT_ASSESSMENT.md`。原目录不存在，新增 plans 子目录承载这一份当前实施输入，没有修改 STATUS/ROADMAP/API/DB_SCHEMA 或复制动态 authority。

完成时核对工作树仅本文件、文档内路径/真实表名、`git diff --check`（含暂存）、diff stat；只精确暂存本文件。`Commit=NOT_COMMITTED`，`Push=NOT_PUSHED`；无 PR/Merge/Release。本轮停止，下一项直接为任务 1，不另启规划。

## 附录：真实 Schema 与持久化引用定位

本附录由本轮源码静态匹配生成，给出每张真实业务表的 CREATE TABLE 位置和包含直接 SQL 的类；它是定位索引，不证明 SQL 已安全。函数内 SQL、动态拼装和接口委托不一定出现在直接引用列，矩阵及 S20 的函数检查仍需适用。

| Table | Schema 定义 | 直接 SQL 定位（最多三处，其余受第 3 节约束） |
| --- | --- | --- |
| account_command_receipts | [V5__account_command_receipts.sql:6](../../../backend/nq-infra/src/main/resources/db/migration/V5__account_command_receipts.sql) | [JdbcAccountCommandReceiptRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountCommandReceiptRepository.java) |
| account_equity_measurements | [V4__account_equity_measurements.sql:1](../../../backend/nq-infra/src/main/resources/db/migration/V4__account_equity_measurements.sql) | [JdbcAccountPerformanceRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountPerformanceRepository.java) |
| account_fee_observations | [V7__account_fee_observations.sql:4](../../../backend/nq-infra/src/main/resources/db/migration/V7__account_fee_observations.sql) | [JdbcAccountFeeRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountFeeRepository.java) |
| account_snapshots | [V1__nexus_quant_v1_baseline.sql:117](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcAccountPerformanceRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountPerformanceRepository.java)；[JdbcLedgerPostingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcLedgerPostingRepository.java)；[JdbcSimAssetFundingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcSimAssetFundingRepository.java) |
| accounts | [V1__nexus_quant_v1_baseline.sql:137](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [CanonicalLegacyAccountBridgeService](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/CanonicalLegacyAccountBridgeService.java)；[JdbcSimAssetFundingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcSimAssetFundingRepository.java)；[JdbcTradingQueryFacade](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/query/JdbcTradingQueryFacade.java) |
| audit_logs | [V1__nexus_quant_v1_baseline.sql:147](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [CanonicalLegacyAccountBridgeService](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/CanonicalLegacyAccountBridgeService.java)；[JdbcAuditLogRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/audit/infra/jdbc/JdbcAuditLogRepository.java)；[JdbcLedgerRiskAuditRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcLedgerRiskAuditRepository.java) |
| auth_sessions | [V34__renewable_auth_sessions.sql:1](../../../backend/nq-infra/src/main/resources/db/migration/V34__renewable_auth_sessions.sql) | [JdbcAuthUserRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/auth/infra/jdbc/JdbcAuthUserRepository.java) |
| backtest_configs | [V1__nexus_quant_v1_baseline.sql:157](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcBacktestConfigRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/JdbcBacktestConfigRepository.java) |
| backtest_eval_reports | [V1__nexus_quant_v1_baseline.sql:180](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcBacktestEvaluationQueryPort](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/eval/jdbc/JdbcBacktestEvaluationQueryPort.java)；[JdbcBacktestEvaluationReportRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/eval/jdbc/JdbcBacktestEvaluationReportRepository.java)；[JdbcPaperShadowComparisonFactRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcPaperShadowComparisonFactRepository.java) |
| backtest_evaluation_requests | [V29__catalog_sync_and_evaluation_requests.sql:43](../../../backend/nq-infra/src/main/resources/db/migration/V29__catalog_sync_and_evaluation_requests.sql) | [BacktestEvaluationRequestService](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/research/BacktestEvaluationRequestService.java) |
| backtest_publish_records | [V1__nexus_quant_v1_baseline.sql:216](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcMarketdataDatasetRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataDatasetRepository.java)；[JdbcBacktestRunRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/JdbcBacktestRunRepository.java)；[JdbcBacktestEvaluationReportRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/eval/jdbc/JdbcBacktestEvaluationReportRepository.java) |
| backtest_runs | [V1__nexus_quant_v1_baseline.sql:243](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcMarketdataDatasetRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataDatasetRepository.java)；[JdbcBacktestRunRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/JdbcBacktestRunRepository.java)；[JdbcBacktestEvaluationReportRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/eval/jdbc/JdbcBacktestEvaluationReportRepository.java) |
| continuous_sim_bars | [V1__nexus_quant_v1_baseline.sql:267](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [ContinuousSimRepository](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/ContinuousSimRepository.java) |
| continuous_sim_operations | [V21__continuous_sim_operations.sql:5](../../../backend/nq-infra/src/main/resources/db/migration/V21__continuous_sim_operations.sql) | [ContinuousSimOperationRepository](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/ContinuousSimOperationRepository.java) |
| continuous_sim_runs | [V1__nexus_quant_v1_baseline.sql:279](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [ContinuousSimRepository](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/ContinuousSimRepository.java)；[StrategySimPerformanceQuery](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/StrategySimPerformanceQuery.java) |
| controlled_execution_lease_events | [V1__nexus_quant_v1_baseline.sql:305](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPilotExecutionLeaseRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcPilotExecutionLeaseRepository.java) |
| controlled_execution_lease_intents | [V1__nexus_quant_v1_baseline.sql:320](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcLiveControlRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcLiveControlRepository.java)；[JdbcPilotExecutionLeaseRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcPilotExecutionLeaseRepository.java) |
| controlled_execution_leases | [V1__nexus_quant_v1_baseline.sql:328](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcLiveControlRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcLiveControlRepository.java)；[JdbcPilotExecutionLeaseRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcPilotExecutionLeaseRepository.java) |
| credential_audit_logs | [V1__nexus_quant_v1_baseline.sql:357](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExchangeAccountCredentialRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepository.java) |
| credential_operation_receipts | [V6__credential_operation_receipts.sql:7](../../../backend/nq-infra/src/main/resources/db/migration/V6__credential_operation_receipts.sql) | [JdbcCredentialOperationRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcCredentialOperationRepository.java) |
| currency_definitions | [V27__currency_registry_and_equity_valuation.sql:2](../../../backend/nq-infra/src/main/resources/db/migration/V27__currency_registry_and_equity_valuation.sql) | [JdbcCurrencyRegistry](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/currency/infra/JdbcCurrencyRegistry.java) |
| discount_asset_price_snapshots | [V15__discount_asset_price_snapshots.sql:85](../../../backend/nq-infra/src/main/resources/db/migration/V15__discount_asset_price_snapshots.sql) | [JdbcDiscountAssetPriceSnapshotRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcDiscountAssetPriceSnapshotRepository.java) |
| emergency_stop_events | [V1__nexus_quant_v1_baseline.sql:369](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcEmergencyStopEventRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcEmergencyStopEventRepository.java) |
| equity_curve_snapshots | [V1__nexus_quant_v1_baseline.sql:385](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcEquityCurveSnapshotRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcEquityCurveSnapshotRepository.java) |
| event_store | [V1__nexus_quant_v1_baseline.sql:399](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [EventStoreAppender](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/eventstore/infra/EventStoreAppender.java)；[RequiredTradeEventStore](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/scheduler/infra/jdbc/RequiredTradeEventStore.java)；[JdbcOrderRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepository.java) |
| exchange_account_credentials | [V1__nexus_quant_v1_baseline.sql:410](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExchangeAccountCredentialRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepository.java)；[JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcLiveControlRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcLiveControlRepository.java) |
| exchange_accounts | [V1__nexus_quant_v1_baseline.sql:452](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [CanonicalLegacyAccountBridgeService](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/CanonicalLegacyAccountBridgeService.java)；[JdbcAccountFeeRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountFeeRepository.java)；[JdbcAccountPerformanceRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountPerformanceRepository.java) |
| execution_instrument_observation_items | [V1__nexus_quant_v1_baseline.sql:468](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcPilotScopeRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcPilotScopeRepository.java) |
| execution_intents | [V1__nexus_quant_v1_baseline.sql:486](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcExecutionOperationsSnapshotQuery](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionOperationsSnapshotQuery.java)；[JdbcLiveControlRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcLiveControlRepository.java) |
| execution_pre_place_recovery_decisions | [V1__nexus_quant_v1_baseline.sql:519](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPilotPrePlaceRecoveryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcPilotPrePlaceRecoveryRepository.java) |
| execution_prerequisite_observations | [V1__nexus_quant_v1_baseline.sql:540](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcPilotScopeRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcPilotScopeRepository.java) |
| execution_receipts | [V1__nexus_quant_v1_baseline.sql:575](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcExecutionOperationsSnapshotQuery](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionOperationsSnapshotQuery.java)；[JdbcLiveControlRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcLiveControlRepository.java) |
| execution_scope_bindings | [V1__nexus_quant_v1_baseline.sql:592](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcPilotScopeRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcPilotScopeRepository.java) |
| instrument_catalog | [V1__nexus_quant_v1_baseline.sql:631](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcInstrumentCatalogRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcInstrumentCatalogRepository.java) |
| instrument_catalog_sync_requests | [V29__catalog_sync_and_evaluation_requests.sql:5](../../../backend/nq-infra/src/main/resources/db/migration/V29__catalog_sync_and_evaluation_requests.sql) | [InstrumentCatalogSyncRequestService](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/marketdata/InstrumentCatalogSyncRequestService.java) |
| kill_switch_events | [V1__nexus_quant_v1_baseline.sql:671](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcKillSwitchStateRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/risk/infra/jdbc/JdbcKillSwitchStateRepository.java) |
| kill_switch_states | [V1__nexus_quant_v1_baseline.sql:691](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcExecutionOperationsSnapshotQuery](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionOperationsSnapshotQuery.java)；[JdbcPilotPrePlaceRecoveryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcPilotPrePlaceRecoveryRepository.java) |
| ledger_entries | [V1__nexus_quant_v1_baseline.sql:709](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcAccountPerformanceRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountPerformanceRepository.java)；[JdbcLedgerPostingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcLedgerPostingRepository.java)；[JdbcSimAssetFundingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcSimAssetFundingRepository.java) |
| ledger_events | [V1__nexus_quant_v1_baseline.sql:725](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcLedgerPostingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcLedgerPostingRepository.java)；[JdbcSimAssetFundingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcSimAssetFundingRepository.java) |
| live_session_events | [V1__nexus_quant_v1_baseline.sql:734](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcExactPilotBindingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcExactPilotBindingRepository.java)；[JdbcExactPilotScopeAuthorizationRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcExactPilotScopeAuthorizationRepository.java) |
| live_sessions | [V1__nexus_quant_v1_baseline.sql:756](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcExecutionOperationsSnapshotQuery](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionOperationsSnapshotQuery.java)；[JdbcLiveControlRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcLiveControlRepository.java) |
| marketdata_bars | [V1__nexus_quant_v1_baseline.sql:794](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcAccountPerformanceRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountPerformanceRepository.java)；[JdbcMarketdataBarRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataBarRepository.java)；[JdbcMarketdataDatasetRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataDatasetRepository.java) |
| marketdata_dataset_coverage | [V1__nexus_quant_v1_baseline.sql:817](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcMarketdataDatasetRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataDatasetRepository.java)；[JdbcMarketdataQualityOverviewRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataQualityOverviewRepository.java)；[JdbcPaperShadowComparisonFactRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcPaperShadowComparisonFactRepository.java) |
| marketdata_dataset_operations | [V26__marketdata_dataset_operations.sql:5](../../../backend/nq-infra/src/main/resources/db/migration/V26__marketdata_dataset_operations.sql) | [MarketdataDatasetOperationService](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/marketdata/MarketdataDatasetOperationService.java) |
| marketdata_datasets | [V1__nexus_quant_v1_baseline.sql:834](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [MarketdataDatasetOperationService](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/marketdata/MarketdataDatasetOperationService.java)；[PublicMarketReplayCaptureService](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/marketdata/PublicMarketReplayCaptureService.java)；[JdbcMarketdataDatasetRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataDatasetRepository.java) |
| marketdata_ingestion_jobs | [V1__nexus_quant_v1_baseline.sql:858](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcMarketdataIngestionJobRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataIngestionJobRepository.java)；[JdbcMarketdataQualityOverviewRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataQualityOverviewRepository.java)；[JdbcMarketdataReadinessRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataReadinessRepository.java) |
| marketdata_ingestion_runs | [V1__nexus_quant_v1_baseline.sql:880](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcMarketdataIngestionJobRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataIngestionJobRepository.java)；[JdbcMarketdataQualityOverviewRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataQualityOverviewRepository.java)；[JdbcMarketdataReadinessRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataReadinessRepository.java) |
| marketdata_price_event_sources | [V9__marketdata_price_event_sources.sql:2](../../../backend/nq-infra/src/main/resources/db/migration/V9__marketdata_price_event_sources.sql) | [JdbcPriceEventSourceRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcPriceEventSourceRepository.java) |
| operator_approvals | [V1__nexus_quant_v1_baseline.sql:902](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionOperationsSnapshotQuery](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionOperationsSnapshotQuery.java)；[JdbcLiveControlRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcLiveControlRepository.java)；[JdbcPilotScopeRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcPilotScopeRepository.java) |
| operator_execution_authorities | [V1__nexus_quant_v1_baseline.sql:924](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java)；[JdbcLiveControlRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcLiveControlRepository.java)；[JdbcOperatorPilotAuthorityRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcOperatorPilotAuthorityRepository.java) |
| orders | [V1__nexus_quant_v1_baseline.sql:960](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcAccountPerformanceRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountPerformanceRepository.java)；[JdbcSimAssetFundingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcSimAssetFundingRepository.java)；[JdbcExecutionIntentRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/execution/infra/jdbc/JdbcExecutionIntentRepository.java) |
| ordinary_order_cancel_finality | [V1__nexus_quant_v1_baseline.sql:990](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | 见 DDL 的受限函数/归属父链 |
| ordinary_place_authorities | [V1__nexus_quant_v1_baseline.sql:998](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcStrategyRunRecoveryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunRecoveryRepository.java) |
| paper_alert_operations | [V22__paper_alert_operations.sql:7](../../../backend/nq-infra/src/main/resources/db/migration/V22__paper_alert_operations.sql) | [JdbcPaperAlertOperationRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperAlertOperationRepository.java) |
| paper_risk_check_results | [V1__nexus_quant_v1_baseline.sql:1006](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPaperRiskCheckResultRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRiskCheckResultRepository.java) |
| paper_run_alerts | [V1__nexus_quant_v1_baseline.sql:1020](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcIncidentReplayOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayOverviewQueryRepository.java)；[JdbcIncidentReplayReviewOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayReviewOverviewQueryRepository.java)；[JdbcPaperRunAlertRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunAlertRepository.java) |
| paper_run_daily_reports | [V1__nexus_quant_v1_baseline.sql:1039](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPaperRunDailyReportRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunDailyReportRepository.java) |
| paper_run_heartbeats | [V1__nexus_quant_v1_baseline.sql:1058](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPaperRunHeartbeatRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunHeartbeatRepository.java) |
| paper_run_recovery_events | [V1__nexus_quant_v1_baseline.sql:1072](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcIncidentReplayOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayOverviewQueryRepository.java)；[JdbcIncidentReplayReviewOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayReviewOverviewQueryRepository.java)；[JdbcPaperRunRecoveryEventRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunRecoveryEventRepository.java) |
| paper_run_schedule_fires | [V1__nexus_quant_v1_baseline.sql:1087](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPaperRunScheduleFireRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunScheduleFireRepository.java) |
| paper_run_schedules | [V1__nexus_quant_v1_baseline.sql:1101](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPaperRunScheduleRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunScheduleRepository.java) |
| paper_run_stability_checks | [V1__nexus_quant_v1_baseline.sql:1117](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPaperRunStabilityCheckRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunStabilityCheckRepository.java) |
| paper_trading_orders | [V1__nexus_quant_v1_baseline.sql:1136](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPaperTradingOrderRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingOrderRepository.java) |
| paper_trading_positions | [V1__nexus_quant_v1_baseline.sql:1153](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPaperTradingPositionRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingPositionRepository.java) |
| paper_trading_runs | [V1__nexus_quant_v1_baseline.sql:1165](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcSimAssetFundingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcSimAssetFundingRepository.java)；[JdbcPaperRunCanonicalFactsRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperRunCanonicalFactsRepository.java)；[JdbcPaperTradingRunRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingRunRepository.java) |
| paper_trading_trades | [V1__nexus_quant_v1_baseline.sql:1190](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPaperTradingTradeRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPaperTradingTradeRepository.java) |
| position_curve_snapshots | [V1__nexus_quant_v1_baseline.sql:1204](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcPositionCurveSnapshotRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcPositionCurveSnapshotRepository.java) |
| positions | [V1__nexus_quant_v1_baseline.sql:1219](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcAccountPerformanceRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountPerformanceRepository.java)；[JdbcLedgerPostingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcLedgerPostingRepository.java)；[JdbcSimAssetFundingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcSimAssetFundingRepository.java) |
| public_market_capture_operations | [V25__public_market_capture_operations.sql:5](../../../backend/nq-infra/src/main/resources/db/migration/V25__public_market_capture_operations.sql) | [PublicMarketCaptureOperationService](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/marketdata/PublicMarketCaptureOperationService.java) |
| public_market_captures | [V1__nexus_quant_v1_baseline.sql:1231](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [PublicMarketReplayCaptureService](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/marketdata/PublicMarketReplayCaptureService.java) |
| reconciliation_scan_cursors | [V1__nexus_quant_v1_baseline.sql:1261](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcOrderRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcOrderRepository.java) |
| research_configs | [V1__nexus_quant_v1_baseline.sql:1272](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcResearchConfigRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcResearchConfigRepository.java) |
| research_creation_operations | [V28__research_creation_operations.sql:1](../../../backend/nq-infra/src/main/resources/db/migration/V28__research_creation_operations.sql) | [ResearchCreationOperationService](../../../backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/research/ResearchCreationOperationService.java) |
| risk_events | [V1__nexus_quant_v1_baseline.sql:1289](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcLedgerRiskAuditRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcLedgerRiskAuditRepository.java)；[JdbcRiskEventRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/risk/infra/jdbc/JdbcRiskEventRepository.java)；[JdbcStrategyRunRelationQueryAdapter](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunRelationQueryAdapter.java) |
| risk_limit_sets | [V1__nexus_quant_v1_baseline.sql:1301](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcLiveControlRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcLiveControlRepository.java) |
| roles | [V1__nexus_quant_v1_baseline.sql:1339](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcAuthUserRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/auth/infra/jdbc/JdbcAuthUserRepository.java) |
| scheduled_job_controls | [V1__nexus_quant_v1_baseline.sql:1347](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [ScheduledJobControlRepository](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/control/ScheduledJobControlRepository.java) |
| scheduled_job_operations | [V19__scheduled_job_operations.sql:5](../../../backend/nq-infra/src/main/resources/db/migration/V19__scheduled_job_operations.sql) | [ScheduledJobControlRepository](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/control/ScheduledJobControlRepository.java)；[ScheduledJobOperationRepository](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/control/ScheduledJobOperationRepository.java) |
| shadow_consistency_reports | [V1__nexus_quant_v1_baseline.sql:1369](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcIncidentReplayOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayOverviewQueryRepository.java)；[JdbcIncidentReplayReviewOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayReviewOverviewQueryRepository.java)；[JdbcConsistencyEvidenceOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcConsistencyEvidenceOverviewQueryRepository.java) |
| shadow_run_events | [V1__nexus_quant_v1_baseline.sql:1384](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcIncidentReplayOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayOverviewQueryRepository.java)；[JdbcIncidentReplayReviewOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayReviewOverviewQueryRepository.java)；[JdbcConsistencyEvidenceOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcConsistencyEvidenceOverviewQueryRepository.java) |
| shadow_run_snapshots | [V1__nexus_quant_v1_baseline.sql:1402](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcConsistencyEvidenceOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcConsistencyEvidenceOverviewQueryRepository.java)；[JdbcPaperShadowConsistencyDrilldownQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcPaperShadowConsistencyDrilldownQueryRepository.java)；[JdbcShadowRunFactRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcShadowRunFactRepository.java) |
| shadow_runs | [V1__nexus_quant_v1_baseline.sql:1420](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcIncidentReplayReviewOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayReviewOverviewQueryRepository.java)；[JdbcConsistencyEvidenceOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcConsistencyEvidenceOverviewQueryRepository.java)；[JdbcPaperShadowConsistencyDrilldownQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcPaperShadowConsistencyDrilldownQueryRepository.java) |
| sim_asset_funding_receipts | [V14__sim_asset_funding.sql:6](../../../backend/nq-infra/src/main/resources/db/migration/V14__sim_asset_funding.sql) | [JdbcSimAssetFundingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcSimAssetFundingRepository.java) |
| sim_orders | [V1__nexus_quant_v1_baseline.sql:1462](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcSimOrderRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/JdbcSimOrderRepository.java) |
| sim_pnl_snapshots | [V1__nexus_quant_v1_baseline.sql:1478](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcSimPnlSnapshotRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/JdbcSimPnlSnapshotRepository.java) |
| sim_positions | [V1__nexus_quant_v1_baseline.sql:1493](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcSimPositionRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/JdbcSimPositionRepository.java) |
| sim_trades | [V1__nexus_quant_v1_baseline.sql:1504](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcSimTradeRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/backtest/jdbc/JdbcSimTradeRepository.java) |
| strategy_definitions | [V1__nexus_quant_v1_baseline.sql:1519](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcSourceStrategySnapshotRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcSourceStrategySnapshotRepository.java)；[JdbcObjectSearchRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/search/infra/JdbcObjectSearchRepository.java)；[JdbcPaperShadowComparisonFactRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcPaperShadowComparisonFactRepository.java) |
| strategy_release_admission_state | [V1__nexus_quant_v1_baseline.sql:1536](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcLiveControlRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/livecontrol/infra/jdbc/JdbcLiveControlRepository.java)；[JdbcStrategyReleaseAdmissionStateRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyReleaseAdmissionStateRepository.java) |
| strategy_run_dispatch_work | [V1__nexus_quant_v1_baseline.sql:1554](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcStrategyRunExecutionRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunExecutionRepository.java)；[StrategySimDecisionRepository](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/StrategySimDecisionRepository.java) |
| strategy_run_recovery_scan_cursor | [V1__nexus_quant_v1_baseline.sql:1582](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcStrategyRunExecutionRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunExecutionRepository.java) |
| strategy_runs | [V1__nexus_quant_v1_baseline.sql:1590](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcObjectSearchRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/search/infra/JdbcObjectSearchRepository.java)；[JdbcStrategyRunDetailQueryAdapter](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunDetailQueryAdapter.java)；[JdbcStrategyRunExecutionRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunExecutionRepository.java) |
| strategy_schedule_creations | [V23__strategy_schedule_creations.sql:5](../../../backend/nq-infra/src/main/resources/db/migration/V23__strategy_schedule_creations.sql) | [JdbcStrategyScheduleCreationRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyScheduleCreationRepository.java) |
| strategy_schedules | [V1__nexus_quant_v1_baseline.sql:1612](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcStrategyRunRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyRunRepository.java)；[JdbcStrategyScheduleListQueryAdapter](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyScheduleListQueryAdapter.java)；[JdbcStrategyScheduleRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyScheduleRepository.java) |
| strategy_sim_decisions | [V1__nexus_quant_v1_baseline.sql:1632](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcStrategySimReplayClockRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/trading/infra/jdbc/JdbcStrategySimReplayClockRepository.java)；[StrategySimAnalysisQuery](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/StrategySimAnalysisQuery.java)；[StrategySimDecisionRepository](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/StrategySimDecisionRepository.java) |
| strategy_versions | [V1__nexus_quant_v1_baseline.sql:1664](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcStrategyVersionSnapshotQueryPort](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/jdbc/JdbcStrategyVersionSnapshotQueryPort.java)；[JdbcObjectSearchRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/search/infra/JdbcObjectSearchRepository.java)；[JdbcPaperShadowComparisonFactRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcPaperShadowComparisonFactRepository.java) |
| trade_replay_records | [V1__nexus_quant_v1_baseline.sql:1681](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcIncidentReplayOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayOverviewQueryRepository.java)；[JdbcIncidentReplayReviewOverviewQueryRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/monitoring/infra/jdbc/JdbcIncidentReplayReviewOverviewQueryRepository.java)；[JdbcTradeReplayRecordRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/research/infra/paper/jdbc/JdbcTradeReplayRecordRepository.java) |
| trades | [V1__nexus_quant_v1_baseline.sql:1699](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcAccountPerformanceRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountPerformanceRepository.java)；[JdbcLedgerPostingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcLedgerPostingRepository.java)；[JdbcSimAssetFundingRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/ledger/infra/jdbc/JdbcSimAssetFundingRepository.java) |
| trading_maintenance_operations | [V33__trading_maintenance_operation_receipts.sql:2](../../../backend/nq-infra/src/main/resources/db/migration/V33__trading_maintenance_operation_receipts.sql) | [JdbcTradingMaintenanceOperations](../../../backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/infra/JdbcTradingMaintenanceOperations.java) |
| user_preferences | [V2__user_preferences.sql:1](../../../backend/nq-infra/src/main/resources/db/migration/V2__user_preferences.sql) | [JdbcUserPreferencesRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/auth/infra/jdbc/JdbcUserPreferencesRepository.java) |
| user_roles | [V1__nexus_quant_v1_baseline.sql:1723](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcAuthUserRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/auth/infra/jdbc/JdbcAuthUserRepository.java) |
| users | [V1__nexus_quant_v1_baseline.sql:1729](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcExchangeAccountRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountRepository.java)；[JdbcAuthUserRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/auth/infra/jdbc/JdbcAuthUserRepository.java)；[JdbcExistingUserPasswordRotationRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/auth/infra/jdbc/JdbcExistingUserPasswordRotationRepository.java) |
| validation_review_cases | [V1__nexus_quant_v1_baseline.sql:1741](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcValidationReviewRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/validationreview/infra/jdbc/JdbcValidationReviewRepository.java) |
| validation_review_events | [V1__nexus_quant_v1_baseline.sql:1778](../../../backend/nq-infra/src/main/resources/db/migration/V1__nexus_quant_v1_baseline.sql) | [JdbcValidationReviewRepository](../../../backend/nq-infra/src/main/java/com/guidinglight/nexusquant/validationreview/infra/jdbc/JdbcValidationReviewRepository.java) |
