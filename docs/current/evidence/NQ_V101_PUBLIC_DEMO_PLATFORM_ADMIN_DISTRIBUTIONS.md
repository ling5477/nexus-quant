# Public Demo 平台管理与双发行实施报告

日期：2026-10-22（Asia/Shanghai）。本报告只记录本轮本地实现和验证，不授予公网部署、真实交易或发布权限。

```text
Task: NQ-V101-PUBLIC-DEMO-PLATFORM-ADMIN-DISTRIBUTIONS
Baseline: 9842028f86b394695e3b503e29bb4cc7b4acdb15
Branch: codex/nq-v101-public-demo-platform-admin
Starting HEAD: 9842028f86b394695e3b503e29bb4cc7b4acdb15
Current HEAD: 9842028f86b394695e3b503e29bb4cc7b4acdb15
```

最初检出为 `codex/nq-v11-public-demo-registration / 7393425b0774f70a8425b5117a792559f7754d1e`，工作区干净。直连 fetch 失败后，通过已有本机代理完成 fetch、dev fast-forward pull，并从上述指定基线新建本轮分支。没有覆盖既有改动。

## Implementation

| 项目 | 本轮结果 |
| --- | --- |
| Platform Admin | 独立纯 PLATFORM_ADMIN 角色，无 Tenant Membership、模拟账户或普通业务权限 |
| Authentication | 复用 JWT、auth_sessions、auth_version、首次改密、注销和续期；Demo 准入接受独立平台身份或既有验证完成注册者 |
| Tenant List | 分页 1–100，用户名搜索、enabled 筛选、注册时间排序；批量最小投影与全局容量概览，没有逐租户 N+1 |
| Tenant Summary | 专用只读身份、初始化、SIM 与执行控制汇总；不设置目标 TenantContext |
| SIM Performance | 单账户 canonical 注资身份和已有 VERIFIED 冻结估值；复用原账户计算器，注资不计利润；缺估值 null/UNAVAILABLE，历史估值 STALE，最多 999 样本 |
| User Enable | 仅恢复 enabled；旧 Session 不恢复，suspended/manual-resume 保留，不注资、不建账户、不启动策略 |
| User Disable | 用户行锁、控制行冲突锁、auth_version、会话撤销、暂停/manual-resume、execution_epoch 与审计同事务 |
| Audit | 不可变 platform_user_operations；稳定 operationId 绑定 actor、目标、动作、原因及请求摘要，冲突 409，重放返回原结果 |
| Execution Control | 原经济提交 SHARE 许可与禁用 FOR UPDATE 线性化；在途行情、后台恢复及旧代际不能继续提交；原显式 Resume 才可解除来源一致的平台暂停 |
| Frontend | 平台首页、搜索筛选分页列表、只读详情/历史绩效、中英文、操作确认/反馈、未知结果稳定重试、角色路由及身份缓存隔离 |
| Distribution Isolation | 构建资源固定发行身份，启动验证模块/配置匹配；默认 Self-Hosted，无 Demo JAR/页面模块；Public Demo 显式方法路径白名单，末尾 denyAll |

平台 API 为任务指定的五条 GET/POST 路径。服务层再次验证当前平台用户、纯平台角色、Session 和无 TenantContext。HTTP 拒绝 tenantId/header override、未登记方法路径，以及平台访问普通租户业务、Demo 用户访问平台。用户启停不接受余额、收益、订单或账务字段，也不能启停平台管理员。

平台初始化是可选 Demo 模块中的独立交互 CLI：仅首个账号、隐藏口令输入、无固定默认口令、强制首次改密、不可变安装审计，不提供公共 HTTP/自动 Runner。安装方法见 [平台身份安装说明](../../../scripts/demo/README-platform-admin.md)。

## Database

```text
Migration: V46__platform_user_operations.sql
           V47__platform_read_projections.sql
Historical Flyway V1–V45: UNCHANGED
```

V46 增加启停回执与平台初始化审计，正向修复显式恢复和重叠暂停的代际规则。V47 增加受控只读投影。专门 LOGIN reader 只获三个 EXECUTE；独立 NOLOGIN 投影所有者只有必要 SELECT，无经济 DML、schema CREATE、角色继承或任意租户切换权限。未安装角色时 fail closed，不借用业务连接。读事务为 REPEATABLE READ/READ ONLY，连接池最多 3、连接等待 5 秒、查询 5 秒、事务 8 秒。

迁移所有者不能因新策略得到无租户查询能力。所有新安全函数及受影响既有函数使用显式 `pg_catalog, public, pg_temp` 解析顺序；历史 SQL 不变。真实 V45→当前升级、校验和、重复 migrate=0、旧经济行保持及历史备份恢复均有 PostgreSQL 证据。动态开发迁移入口自动识别 47 个迁移；没有手工新增最大版本号，也没有改变已发布 Manifest。

## Verification

测试只使用本轮新建 PostgreSQL 16 容器 `nq-platform-admin-pg-20261022`（loopback 55461）和本机 SMTP 捕获容器（11026/18026）。每个 PG 用例创建并清理随机数据库和受限角色，没有使用共享业务库、生产数据、真实交易账户或公共邮件。

本机证据目录：`C:\Users\Lingyu\AppData\Local\Temp\nq-platform-admin-20261022`。连接配置不纳入报告、仓库或制品。

| 验证 | 结果与边界 |
| --- | --- |
| Backend Unit / PostgreSQL / HTTP | 23 个受影响套件去重 88 项，0 failure/error/skip；最终受影响修复另执行 15 项 PG/HTTP，BUILD SUCCESS |
| Auth / Security | 本地兼容 WebMVC、Tenant 身份、JWT、独立平台登录/会话、三主体互斥权限、未知 API 拒绝 |
| Admin Concurrency | PostgreSQL 实际锁等待、不同 operationId 并发、幂等冲突、认证代际与 Session 撤销、在途行情竞争 |
| Restart | 新 JVM 完整 Servlet 重新装配；禁用登录失败、后台恢复拒绝、撮合没有新推进，父进程持久化经济快照一致 |
| Resume | 启用不恢复；重新登录后显式恢复；多次启停未 Resume 仍可恢复；成员/角色额外暂停则 409，仍 suspended/manual |
| Canonical Regression | 原研究→回测→评价→发布→Continuous SIM→Risk/Order/Matching/Ledger→冻结估值；利润按资金基准精确核验 |
| Economic Fact Mutation | 启停及拒绝前后完整 orders/trades/ledger/positions/account snapshots、funding receipts、历史绩效快照行比较：0 |
| Flyway | 动态源码身份 PASS；10 个开发 Schema 合同测试 PASS；升级/validate/replay/保留历史数据与校验和 PASS |
| Frontend Unit | 87 文件 / 677 项 PASS |
| Production Frontend Builds | Self-Hosted 与 Public Demo 均 PASS；Self 所有 chunk 的模块图不含 public-demo/platform 功能模块 |
| Self-Hosted Build / HTTP | 最终 Boot JAR 构建及内容扫描 PASS；独立 java -jar、本地 ADMIN 登录和普通业务 GET PASS；注册与平台接口不可用 |
| Public Demo Build / HTTP | 最终 Boot JAR 构建及内容扫描 PASS；独立 java -jar 无真实凭据启动、无 Membership 平台登录/列表、双用户实际注册验证与 canonical 注资 PASS |
| Playwright | 9 项 UI mock 回归 PASS；另 1 项生产前端→真实 PG 后端完整三身份链 PASS，无接口 mock |
| Distribution Startup Rejection | 6 项真实 Java 子进程拒绝：Self 升 Demo/注册、Demo 身份错配/LIVE/legacy bootstrap/危险 profile |
| Actual Frontend Pairing | 专有 Docker 网络代理到真实 Demo Servlet：Demo 前端运行，Self 混搭退出 1/FRONTEND_BACKEND_DISTRIBUTION_MISMATCH |
| Independent Correctness Review | 一次限定独立检查，实际独立新库/HTTP/角色负例；发现的 1 个 P1 已定向修复并独立复核 CLOSED |
| Remote CI | NOT_RUN；本地证据不等于 exact-head 远端 CI 或正式发布资格 |
| Full Docker Image Builds | NOT_RUN；已实现构建入口及真实 nginx 启动配对验证，不将其等同完整镜像构建 |

最终后端日志为 `independent-finding-final.log`；其余相同源和未受修改影响的有效套件结果来自 `platform-and-regression-final.log`、`affected-regression-first.log` 及 `independent-finding-remediation.log`，逐套去重清单为 `backend-verification-summary.json`。没有将失败运行整体追认为通过。

真实页面联调使用生产前端、两个正式注册者和一个 PLATFORM_ADMIN，经过原 canonical 成交与冻结估值。验证禁用后旧 Session/Renew/Login 401，启用保持暂停，经济投影不变，退出并切换两名用户后私有缓存不串身份。该页面证据取得后，V46 还进行了重叠暂停定向修复；前端与页面调用合同没有变化，最终 SQL 的多周期及非平台暂停边界由后续真实 PG/HTTP 测试和独立负例补验，没有声称原 UI 进程运行过最终 V46。

## Findings

- P0：最终未关闭 0。
- P1：独立检查 `PAUSE-OVERLAP-001` 曾真实复现平台 Resume 错误解除重叠暂停。已将成员停用、成员删除、混合角色与角色变更/删除的额外暂停推进执行代际；独立新库验证成员和角色反例均被 `TENANT_SUSPENDED` 拒绝，经济行不变，CLOSED。最终 V46 SHA256：`afce670e977d94326b1bec22876d2a647dcf2aabd3d9d3d3b0cb5d7fb0c97682`。
- P2：最终未关闭 0。独立检查没有覆盖全仓、压力容量、公网、真实邮件或正式发行。

其他本轮定位并复测的问题包括：未安装投影角色时原 owner 获得跨租户读取；新函数临时表解析路径；在途轮询留下 STALLED 导致原 Resume 回执约束失败；重复启停未 Resume 的来源衔接；body 413 被默认拒绝的错误分派改成 401；测试固定迁移版本/旧容器名/新元数据字段及 Self 测试编译依赖可选模块。均保留初次失败日志，没有删除正确性断言或新增 Skip 取得通过。

独立报告位于证据目录 `independent-correctness-review.md`，报告 SHA256 `993b204b49d9d0b729458c54c3bddfb079efc1ef58a75dab21dc9ec5da0479cd`。它区分审查者实际执行的反例/补验与复用的实现者并发、重启证据，没有形成反复审查链。

## Decision

```text
Decision: IMPLEMENTED / SELF_REVIEWED
PLATFORM_ADMIN_LOGIN: PASS
PLATFORM_ADMIN_TENANT_MEMBERSHIP: NOT_REQUIRED
TENANT_LIST: PASS
TENANT_PERFORMANCE: READ_ONLY
USER_DISABLE: PASS
USER_ENABLE: PASS
SESSION_REVOCATION: PASS
EXECUTION_SUSPENSION: PASS
AUTOMATIC_SIM_RESUME: DISABLED
ECONOMIC_FACT_MUTATION: 0
SELF_HOSTED_DEMO_ENDPOINTS: UNAVAILABLE
PUBLIC_DEMO_LIVE: DISABLED
DISTRIBUTION_ISOLATION: PASS
PUBLIC_EMAIL_DELIVERY: NOT_QUALIFIED
PUBLIC_DEPLOYMENT: NOT_READY
Commit: NOT_COMMITTED
Push: NOT_PUSHED
PR / Merge / Tag: NOT_CREATED / NOT_MERGED / NOT_CREATED
Release: NOT_CREATED
Staging: 83 explicit allowlisted paths
Next: NQ-V101-PUBLIC-DEMO-DEPLOYMENT-QUALIFICATION
```

默认正式发行仍为 Self-Hosted。`release/source-manifest.json` 的 v1.0.0 历史清单、已发布 tag/Manifest 与正式安装器没有被改写。完整镜像、正式 v1.0.1 包、真实邮件和公网部署资格留给下一任务；本轮完成后停止。

## Artifacts

前端产物：`frontend/dist/self-hosted` 与 `frontend/dist/public-demo`，各含明确 `distribution.json`。截图和 UI 证据：`frontend/dist/verification`。后端最终制品位于证据目录的 `distributions` 子目录：

| 制品 | SHA256 |
| --- | --- |
| nq-self-hosted-final.jar | 7663a9003c20db3dcae76586002e78791d965ae9751572957ffad7532a7dd279 |
| nq-public-demo-final.jar | 35efaefbc233bb5732298200b9336a1b42e3cdd47fc038cb3f5ccf470c04adbb |

`distributions/distribution-verification-summary.json` 绑定两个最终 JAR、前端身份、制品内 V46/V47 与源码的相同 SHA、两个真实 Boot 进程正向 HTTP 及最终制品的六项启动拒绝。较早的两种 JAR 保留为修复前候选，不冒充最终制品。全套实际组合测试的临时子进程、数据库、角色和 Docker 网络均已清理。

精确暂存首次检查发现本轮新前端文件及本报告附录的 CRLF 被 raw `git diff --cached --check` 认定为尾部空白，原失败保留为 `staged-whitespace-initial.log`。只将这 19 个本轮新增文件的行尾改为 LF，没有改变内容字符或测试断言；随后重新构建两个前端，所有输出文件的路径和 SHA256 与原生产构建逐项完全相同，raw staged check 通过。没有设置 whitespace 例外。

## Files Changed


```text
.gitignore
backend/nq-app/pom.xml
backend/nq-app/src/main/distribution/META-INF/nq-distribution.properties
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/auth/SecurityConfiguration.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/distribution/DistributionConfiguration.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/distribution/DistributionController.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/distribution/DistributionIdentity.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/security/web/PlatformApiPolicy.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/security/web/TenantFoundationAccessFilter.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/distribution/DistributionHealthPrivacyTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/distribution/DistributionIdentityTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PlatformAdminHttpIntegrationTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PlatformAdminRestartProbeMain.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PlatformAdminUiFixtureMain.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PlatformReadPostgresIntegrationTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PlatformReadTestRoles.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PlatformUserMigrationIntegrationTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PublicDemoRegistrationMigrationIntegrationTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PublicDemoTwoUserFlowIntegrationTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TenantIsolationMigrationIntegrationTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TenantIsolationTestDatabase.java
backend/nq-auth/src/main/java/com/guidinglight/nexusquant/auth/domain/port/PublicDemoDistribution.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/service/AccountPerformanceCalculator.java
backend/nq-infra/src/main/resources/db/migration/V46__platform_user_operations.sql
backend/nq-infra/src/main/resources/db/migration/V47__platform_read_projections.sql
backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/platform/PlatformAccess.java
backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/platform/PlatformAdminInitializer.java
backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/platform/PlatformController.java
backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/platform/PlatformReadService.java
backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/platform/PlatformReadSource.java
backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/platform/PlatformUserService.java
backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/registration/DemoRegistrationBodyFilter.java
backend/nq-public-demo/src/main/java/com/guidinglight/nexusquant/demo/registration/PublicDemoConfiguration.java
backend/nq-public-demo/src/test/java/com/guidinglight/nexusquant/demo/platform/PlatformReadServiceTest.java
backend/nq-public-demo/src/test/java/com/guidinglight/nexusquant/demo/registration/PublicDemoConfigurationTest.java
backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/ContinuousSimRepository.java
backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/paper/ContinuousSimRunService.java
backend/pom.xml
docs/current/evidence/NQ_V101_PUBLIC_DEMO_PLATFORM_ADMIN_DISTRIBUTIONS.md
frontend/package.json
frontend/src/app/providers/AppProviders.tsx
frontend/src/app/providers/DistributionBoundary.tsx
frontend/src/app/render.tsx
frontend/src/components/layout/AppHeader.tsx
frontend/src/components/layout/AppSiderMenu.tsx
frontend/src/distributions/public-demo-features.tsx
frontend/src/distributions/public-demo.tsx
frontend/src/distributions/self-hosted-features.tsx
frontend/src/distributions/self-hosted.tsx
frontend/src/features/platform/api.ts
frontend/src/features/platform/i18n.ts
frontend/src/features/platform/pending-operation.ts
frontend/src/features/platform/platform.css
frontend/src/features/platform/PlatformPage.tsx
frontend/src/features/platform/TenantDetailPage.tsx
frontend/src/features/platform/TenantsPage.tsx
frontend/src/features/platform/TenantTable.tsx
frontend/src/features/platform/UserStateAction.tsx
frontend/src/i18n/locales/en-US/common.json
frontend/src/i18n/locales/zh-CN/common.json
frontend/src/main.tsx
frontend/src/pages/login/LoginPage.tsx
frontend/src/router/RequireAuth.tsx
frontend/src/router/routes.tsx
frontend/src/utils/distribution.ts
frontend/tests/e2e/platform-admin-real.spec.ts
frontend/tests/e2e/platform-admin.spec.ts
frontend/tests/e2e/public-demo-registration.spec.ts
frontend/tests/unit/i18n-source-contract.test.ts
frontend/tests/unit/platform-admin.test.ts
frontend/tsconfig.app.json
frontend/vite.config.js
frontend/vite.config.ts
release/build-distributions.ps1
release/docker/backend.Dockerfile
release/docker/frontend.Dockerfile
release/docker/verify-distribution.sh
scripts/ci/Test-DistributionArtifact.ps1
scripts/ci/Test-DistributionStartup.ps1
scripts/demo/Install-PlatformAdmin.sql
scripts/demo/Install-PlatformReadRole.sql
scripts/demo/README-platform-admin.md
scripts/demo/README-platform-read.md
```
