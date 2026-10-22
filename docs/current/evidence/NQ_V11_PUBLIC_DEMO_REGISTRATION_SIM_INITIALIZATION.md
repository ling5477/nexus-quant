# Public Demo 注册与 SIM 初始化实现记录

日期：2026-10-22（Asia/Shanghai）。这是本轮本地实现与定向验证记录，不修改全局阶段、发布或真实运行 authority。

```text
Task: NQ-V11-PUBLIC-DEMO-REGISTRATION-SIM-INITIALIZATION
Baseline: dev / ae51c935c534155b42b26aa7c388333f0d370669
Branch: codex/nq-v11-public-demo-registration
HEAD: ae51c935c534155b42b26aa7c388333f0d370669
Candidate: 本轮未提交工作区；不是新的 commit / Release

Implementation:
Registration: IMPLEMENTED
Email Verification: IMPLEMENTED / PUBLIC_EMAIL_DELIVERY_NOT_QUALIFIED
Tenant: PASS
Membership: PASS
SIM Account: PASS
Funding: 10000.00000000 USDT / canonical Ledger
Duplicate Funding: 0（本轮重复、并发、重启场景）
First SIM Binding: PASS
Tenant Isolation: PASS（本轮两用户闭环及受影响路径）
Frontend: IMPLEMENTED / TARGETED_VERIFICATION_PASS
Distribution Isolation: PASS / Self-Hosted Registration UNAVAILABLE

Database:
Migration: V45__demo_registration_and_reserved_sim_identity.sql
Existing Data Preservation: PASS（V44→V45；历史经济事实与迁移checksum保持）

Findings:
P0: 0 未解决新增项
P1: 0 未解决新增项；独立检查发现的 provider 合同错误已修复
P2: 0 未解决新增项；独立检查发现的 REAL 配置空白规范化遗漏已修复

Decision: IMPLEMENTED / SELF_REVIEWED
Independent Check: 一次限定检查及受影响修复补核完成
LIVE: DISABLED
Public Demo: NOT_READY
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Release: NOT_CREATED
Next: NQ-V11-PUBLIC-DEMO-PLATFORM-ADMIN-DISTRIBUTIONS（仅记录，不启动）
```

## 关键变化与不变量

- `nq-public-demo` 只通过显式 Maven `public-demo` 构建进入制品；Spring Profile 或运行变量不能给 Self-Hosted 增加注册 Controller。前端使用显式构建身份，Self-Hosted 产物没有公共注册 API 或 Demo 页面 chunks。
- 注册固定 DEMO_USER / DEMO / SIM，统一规范化用户名和邮箱，数据库保证唯一性。Pending 用户不能登录业务；Demo 登录、续期和 JWT 每请求同时校验已提交的 READY 注册、邮箱验证和有效成员关系。
- 一次性验证令牌仅保存摘要；Outbox 保存随机邮件身份并由独立配置秘密重建令牌，支持进程重启恢复。错误验证、有效期、邮件重试、重发冷却、来源及邮箱请求限流均有边界，匿名 JSON 请求体上限 8 KiB。回执不返回验证令牌或账号是否存在。
- 同一 PostgreSQL 事务完成用户启用、Tenant/Membership/control、唯一 PAPER/SIM 账户、canonical 桥接、预留运行身份、原 `fundOnce` 的 CASH/CONTRA 分录及注册回执。任何初始化失败连同用户启用一起回滚，不删除成功事实伪造回滚。
- `tenant_sim_accounts` 仅保存身份，不存余额或收益。首次 Continuous SIM 使用原账户、预留 `ptr-UUID` 和原 funding ref_id，不新建第二个账户、不再注资。原 operationId 重放返回原结果，不同发布竞争只能一个成功；其他创建入口拒绝 Demo，避免消耗唯一绑定后留下孤立运行。
- PAPER 仅专用 Demo/SIM 桥接准入，未绑定或未运行没有交易 authority，运行后不产生双匹配。数据库生成列和复合外键保证 PAPER 不能持有凭据，避免把 RLS 隐藏的凭据误当成不存在；原 OKX/Binance/LIVE 范围与歧义拒绝保留。
- V45 正向修复共用暂停触发器的行类型解析问题，不改 V35–V44。Demo 启动同时核对 Spring 和 adapter 实际进程配置，拒绝 REAL、默认 bootstrap、真实 provider 与其他禁止能力；不生成 Boot 默认开发用户口令。
- 前端提供中英文注册、邮箱验证/重发、初始化与未绑定状态、唯一账户、首次 Continuous SIM 及既有 canonical 分析入口。退出和切换用户清理业务缓存；未知/失败响应不继续显示成功状态。Demo 只选择已准入数据目录。

## 实际验证与证据

证据目录：`C:\Users\Lingyu\AppData\Local\Temp\nq-demo-registration-test`。所有数据库、运行角色、SMTP 和行情均为本任务隔离测试资源；没有使用真实用户数据库、真实邮件地址或交易 provider。

| 范围 | 实际结果 | 证据 |
| --- | --- | --- |
| 前置 Git/CI | PR #70 MERGED；merge SHA 等于基线；post-merge 9/9 SUCCESS | Git 与 GitHub run `39702480549`；这是基线 CI，不是本轮新候选 CI |
| 注册 HTTP / PostgreSQL | 5 tests PASS：规范化、重复/并发 username/email/registrationId、Token 消费/过期/错误次数、限流与拒绝权限字段、邮件失败恢复、原子回滚、Pending 无业务权限、实际重启 | `demo-final-tests.log`；最新启动与两用户链另见下一行 |
| 两用户完整闭环 | PASS：正式注册→验证→策略/回测→幂等评价→发布→并发首次绑定→canonical 各一 Order/Trade/Position、独立 Ledger、PnL 排除初始资金、跨租户拒绝、绑定后实际新 JVM 重读与经济快照完全相同 | `demo-two-user-final-attempt2.log`（BUILD SUCCESS）；`backend/nq-app/target/demo-registration-evidence/READ_BOUND.log` |
| PostgreSQL/Flyway 升级 | 2 tests PASS：V44→V45 与 validate、旧事实/checksum 保持、规范化冲突使整个迁移回滚 | `demo-final-tests.log`；最终 LF/EOF 格式整理后的复核 `migration-final-format.log`（BUILD SUCCESS） |
| PAPER / 原环境身份 | 2 tests PASS：受限桥接、未绑定无交易资格、运行后不双匹配、PAPER 凭据硬拒绝及原 venue 边界 | `demo-final-tests.log` |
| Demo 启动守卫 | 3 tests PASS，含两 venue 带空白 REAL 配置拒绝；最新实际启动使用 dome / disabled endpoint，未生成开发口令 | `demo-two-user-final-attempt2.log`、`ui-fixture-final.log` |
| 后端定向单位测试 | 33 tests PASS：PaperRun 4、TenantIdentity 7、Continuous operation 8、费用 6、绩效 5、Security Context 3 | `self-hosted-regression.log` |
| Self-Hosted Auth / canonical SIM | AuthSession 2 与 StrategySim PG 4 tests PASS；原资金/风控/成交账务与正式研究链 | `self-hosted-regression.log`；该命令另有下文的 3 个旧夹具 ERROR，不能称整个命令成功 |
| 默认 Self-Hosted Continuous | 2 tests PASS，含资格暂停/显式恢复竞态与八槽位容量；最终同 V45 候选 BUILD SUCCESS | `self-hosted-continuous-final.log` |
| 两发行 Boot 包 | 两个实际 `package spring-boot:repackage` BUILD SUCCESS；Self-Hosted Demo entries=0，Demo 正好一个可选模块且包含注册 Controller | `self-hosted-package.log`、`public-demo-package.log` 与两个 `*-artifact.json` |
| 两发行前端构建 | PUBLIC_DEMO / SELF_HOSTED 均 PASS；Self-Hosted 公共注册 API chunks=0、Demo 页面 chunks=0 | `frontend-public-demo-build.log`、`frontend-self-hosted-build.log`、`frontend-self-hosted-isolation.json` |
| 前端单位测试 | 6 files / 101 tests PASS | `frontend-targeted-unit.log` |
| Playwright | 4 项界面夹具 PASS；1 项真实 PG/HTTP/Mailpit 浏览器链 PASS，失败 API 响应数=0 | `frontend-verification-summary.txt`；系统 Chrome，trace off，避免验证令牌进入 trace |

真实浏览器链覆盖新注册→本地邮件→验证→登录→唯一 PAPER/SIM 账户→实际创建策略→退出切换已绑定用户→已有 canonical portfolio。完整研究、评价、发布、首次绑定与成交通过上表的两用户真实 HTTP 测试执行；界面夹具不替代这些业务事实证明。浏览器截图早于最后两处 Demo 说明文案修正；之后两发行 build 与 101 单测重新通过，经济逻辑未改。

实际包 SHA256：

- Self-Hosted：`ada512db1e72b705d27bd69349a780544cc45adb05147354c1400c91883f37f1`。
- Public Demo：`4ece484ee42635f2204113b61e5974e11aa741b7e060b68ddc37f8048b8950cc`。

## 保留的失败及限制

- 实现期间真实测试发现过初始化 RLS 时序、共用暂停触发器字段解析、provider 合同错误；已修复并保留前后日志。测试 fixture 的行情可用时间、末尾毫秒与公共缺失响应合同也已按真实实现修正，没有放松账务、隔离或交易断言。
- 旧 `TenantIsolationContinuousIntegrationTest` 的角色种子用户没有注册 READY 回执，在 Demo 制品中登录被正确拒绝。因此误带它的 `demo-final-tests.log` 整体命令 FAIL；其本来所属的默认 Self-Hosted 构建最终 2 tests PASS，不能把两个命令混称为一次全绿运行。
- 额外尝试的 `SimAssetFundingPostgresIntegrationTest` 三个旧用例 ERROR：fixture 固定 V14，现有 `JdbcLedgerPostingRepository` 已查询 Tenant 字段，报 `column ta.tenant_id does not exist`。这两个文件本轮均未修改；本轮未为旧夹具扩大修复范围，也未把这次 ERROR 写为 PASS。最新 schema 下本轮实际注资、回滚、幂等、成交账务及 Self-Hosted canonical 路径已有上表证明。
- 独立限定检查发现的 P1 provider 错值与 P2 REAL 规范化遗漏均已修复并补核，最终没有未解决的新增 P0/P1/P2；没有启动全量安全审计或历史 GateAUDIT。
- `PUBLIC_EMAIL_DELIVERY_NOT_QUALIFIED`；仅本地 SMTP 与合成批准目录验收。没有公网注册、公共部署、LIVE、真实 PLACE/CANCEL、凭据管理、transfer/withdraw 或 Release。没有本轮新 SHA 的远程 CI。
- 浏览器应用进程已正常停止，隔离 UI 数据库随 fixture 生命周期清理；这属于一次性测试资源清理，不是删除成功业务事实来模拟回滚。任务独占 PostgreSQL/Mailpit 容器和日志保留供复核。

## 运行与关闭

具体构建、受限数据库权限和本地邮件配置见 [模块说明](../../../backend/nq-public-demo/README.md)。关闭注册后保存 Pending、Outbox、成功回执和资金；关闭新业务准入不等于停止既有后台任务，暂停仍使用原租户执行控制及 Continuous stop。不得回改历史迁移或删除已成功的资金事实。

## Files Changed

以下清单由最终 Git 修改路径生成；只暂存这些本轮文件，未 Commit/Push/PR/Merge/Tag/Release。
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/ApiExceptionHandler.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/auth/api/web/AuthController.java`
- `backend/nq-app/pom.xml`
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/auth/SecurityConfiguration.java`
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/research/BacktestEvaluationRequestController.java`
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/research/ResearchCreationOperationController.java`
- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/security/web/DemoTenantApiPolicy.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PublicDemoAccountIdentityPostgresIntegrationTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PublicDemoAccountTestFixture.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PublicDemoRegistrationHttpIntegrationTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PublicDemoRegistrationMigrationIntegrationTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PublicDemoRegistrationRestartProbeMain.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PublicDemoTestHttp.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PublicDemoTwoUserFlowIntegrationTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PublicDemoUiFixtureMain.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TenantIsolationContinuousIntegrationTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TenantIsolationHttpIntegrationTest.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TenantIsolationTestDatabase.java`
- `backend/nq-auth/src/main/java/com/guidinglight/nexusquant/auth/application/service/DbAuthService.java`
- `backend/nq-auth/src/main/java/com/guidinglight/nexusquant/auth/domain/port/PublicDemoDistribution.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/port/SimAccountIdentityBridge.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/CanonicalLegacyAccountBridgeService.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcAccountTradeEnvironmentAuthority.java`
- `backend/nq-infra/src/main/resources/db/migration/V45__demo_registration_and_reserved_sim_identity.sql`
- `backend/nq-public-demo/pom.xml`
- `backend/nq-public-demo/README.md`
- `backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/registration/DemoEmailOutboxDispatcher.java`
- `backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/registration/DemoRegistrationBodyFilter.java`
- `backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/registration/DemoRegistrationController.java`
- `backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/registration/DemoRegistrationService.java`
- `backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/registration/DemoTokenCodec.java`
- `backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/registration/PublicDemoConfiguration.java`
- `backend/nq-public-demo/src/test/java/com/guidinglight/nexusquant/demo/registration/PublicDemoConfigurationTest.java`
- `backend/nq-research/src/main/java/com/guidinglight/nexusquant/research/application/paper/service/PaperTradingRunService.java`
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/StrategySimRunService.java`
- `backend/pom.xml`
- `docs/current/evidence/NQ_V11_PUBLIC_DEMO_REGISTRATION_SIM_INITIALIZATION.md`
- `frontend/src/components/layout/AppHeader.tsx`
- `frontend/src/components/layout/AppSiderMenu.tsx`
- `frontend/src/components/legal/LoginDisclosures.tsx`
- `frontend/src/features/backtests/BacktestRunActions.tsx`
- `frontend/src/features/paper-trading/components/StrategySimPanel.tsx`
- `frontend/src/features/paper-trading/continuous-operations.ts`
- `frontend/src/features/preferences/hooks/useUserPreferences.ts`
- `frontend/src/features/public-demo/api.ts`
- `frontend/src/features/public-demo/DemoAccountPage.tsx`
- `frontend/src/features/public-demo/DemoAuthSurface.tsx`
- `frontend/src/features/public-demo/DemoContinuousControls.tsx`
- `frontend/src/features/public-demo/DemoSimSetupPage.tsx`
- `frontend/src/features/public-demo/i18n.ts`
- `frontend/src/features/public-demo/public-demo.css`
- `frontend/src/features/public-demo/RegisterPage.tsx`
- `frontend/src/features/public-demo/registration-state.ts`
- `frontend/src/features/public-demo/VerifyEmailPage.tsx`
- `frontend/src/features/research-result-recovery.ts`
- `frontend/src/features/research/components/ConfigurationArchiveAction.tsx`
- `frontend/src/features/research/components/CreationRecoveryPanel.tsx`
- `frontend/src/i18n/LanguageSelect.tsx`
- `frontend/src/i18n/locales/en-US/common.json`
- `frontend/src/i18n/locales/en-US/pages.json`
- `frontend/src/i18n/locales/zh-CN/common.json`
- `frontend/src/i18n/locales/zh-CN/pages.json`
- `frontend/src/pages/backtests/BacktestsPage.tsx`
- `frontend/src/pages/evaluations/EvaluationsPage.tsx`
- `frontend/src/pages/login/LoginPage.tsx`
- `frontend/src/pages/paper-trading/components/PaperAnalysisWorkspace.tsx`
- `frontend/src/pages/paper-trading/components/SimAnalysisWorkspace.tsx`
- `frontend/src/pages/publishes/PublishesPage.tsx`
- `frontend/src/pages/research/ResearchPage.tsx`
- `frontend/src/pages/strategies/StrategiesPage.tsx`
- `frontend/src/router/navigation.tsx`
- `frontend/src/router/routes.tsx`
- `frontend/src/utils/distribution.ts`
- `frontend/tests/e2e/public-demo-registration-real.spec.ts`
- `frontend/tests/e2e/public-demo-registration.spec.ts`
- `frontend/tests/unit/public-demo-registration.test.ts`
- `frontend/vite.config.js`
- `frontend/vite.config.ts`
