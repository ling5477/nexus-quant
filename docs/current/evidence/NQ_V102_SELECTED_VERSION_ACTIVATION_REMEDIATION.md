# NQ-V102-SELECTED-VERSION-ACTIVATION-REMEDIATION

Task: NQ-V102-SELECTED-VERSION-ACTIVATION-REMEDIATION

Decision: **IMPLEMENTED / TARGETED_VALIDATION_PASS**

日期：2026-10-28（Asia/Shanghai）

本结论针对本轮源码整改和限定验证。旧 v1.0.2 安装包的结论仍是 **WINDOWS_QUALIFICATION_BLOCKED**；没有以源码测试替代安装包验收。

## Baseline 与候选身份

| 字段 | 结果 |
| --- | --- |
| Starting HEAD / origin/dev | `1390affb4b155d6fad0e286b1680352919a5bb2c` |
| Baseline Tree | `9bdf4bef80672304aa08ab1d462da3d7a988a2e4` |
| Branch | `codex/nq-v102-selected-version-activation` |
| Version / Schema | 1.0.2 / V49；未增加迁移 |
| Final Staged Tree | 见仓库外 `E:/nq-v102-selected-activation-20261028/evidence/stage-identity.json` 的 `stagedTree`；本报告参与该 tree，避免在自身写入自引用摘要 |
| 最终逐文件 SHA256 | 同目录 `final-candidate-files.json`；Git blob/tree 身份见 `stage-identity.json` |
| Evidence Root | `E:/nq-v102-selected-activation-20261028/` |

任务开始工作区和 index 干净，从最新 origin/dev 建立指定分支。最终仅精确暂存下列18个文件，不提交。

## Root Cause 与 Activation Contract

原实验创建 DRAFT 版本，Walk-Forward 最终冻结该原版本；正式发布正确要求 ACTIVE，但缺少激活原选中版本的正式入口。复制新的 ACTIVE 版本会改变 VersionId，不能满足原 Trial/Run/Comparison 血缘合同。策略定义启用与版本激活是不同动作。

本轮新增服务端解析冻结选择的显式 `DRAFT → ACTIVE`。`ACTIVE → ACTIVE` 返回同一版本，不再更新时间或产生成功审计；ARCHIVED 拒绝激活。客户端不能指定 VersionId、参数、Checksum或目标状态，携带 JSON body 返回400，也不能通过本入口把 ACTIVE降回DRAFT。

Identity Preservation: 原 VersionId、StrategyCode、Version、VersionName、Parameter/Config/Source Snapshot、Checksum、CreatedBy及CreatedAt不变。只写 status和updated_at；原 Backtest Run冻结的DRAFT快照保持原样。生命周期状态作为元数据变化，不改变经济策略身份。

Persistence: `StrategyVersionService`通过正式Repository端口执行受租户限定的CAS，WHERE同时核对DRAFT及完整不可变身份。核心方法要求已有事务；外层激活服务在同一事务中完成状态变更和成功审计。并发请求仅一次CAS成功，另一个读取原ACTIVE结果；异常使整笔事务回滚。

Audit: 复用 `AuditLogRepository`，domain=`STRATEGY_VERSION`，action=`ACTIVATE_SELECTED_VERSION`。记录StrategyVersionId、ValidationId、SelectedTrialId、PreviousStatus、NewStatus、ActorId、OccurredAt和TraceId。仅changed=true时追加；不记录凭据或完整研究输入。

Eligibility: 当前合法owner及RLS可见的成功Validation与源实验，完整最终训练及冻结step12选择、源Trial归属和成功状态、冻结candidate参数、选中最终训练Result/Run/Evaluation关联、源Run与训练Run的成功及三份快照/Checksum一致性。未成功、未冻结或身份漂移均拒绝；其他用户、跨租户与不存在对象均保持404。允许未选中的训练候选失败，只要求最终冻结选择本身有效。

## API 与 Frontend

- GET `/api/strategy-experiments/validations/{id}/selected-version`：查询当前准确版本身份、状态和资格。
- POST `/api/strategy-experiments/validations/{id}/selected-version/activate`：必须认证及已有ADMIN/OPERATOR研究写权限，由服务端选择版本，无客户端替代字段。
- 现有Walk-Forward区域显示最终版本、状态、参数和Checksum，人工确认后才激活。中英文均明确激活不等于Publish、SIM或LIVE；短样本和已观察Holdout限制继续保留。
- mutation禁止自动重试；响应丢失后只通过GET恢复。查询按owner及Validation隔离，成功后失效相应版本及Comparison缓存。
- ACTIVE后链接既有 `/backtests?backtestConfigId=...&backtestRunId=...` 操作页，保留原Config/Run身份，正式Publish仍需另外人工点击。
- 原 `BacktestPublishService` ACTIVE门禁及生产 `ResearchSimComparisonService`均未修改；Comparison夹具从直接SQL激活改走本次正式服务。

## Validation

环境：Windows 11专业版10.0.26200 amd64；PowerShell7.6.9；Docker Engine29.9.2 Linux Containers；Java21 / Maven现有工程；Node24.16.0 / npm11.13.0。真实浏览器为本机Microsoft Edge156.0.4314.50，Playwright1.58.2。

隔离PostgreSQL16.15：任务容器 `nq-v102-selected-activation-pg16-20261028`，仅127.0.0.1:55461，2CPU/2GiB，数据位于证据目录下postgres-data。镜像ID为 `sha256:9e2cf6287f25c3151f4c9421ccce2b17f3e60061c111a29165e0d2cdd9ff0a97`。每次执行新随机数据库及受限角色；原worker消费720根冻结研究数据，真实运行SMA与通道实验、Walk-Forward、回测与评价。测试没有调用真实交易provider。

| 验证项 | 实际结果与证据（路径相对Evidence Root） |
| --- | --- |
| Backend / Regression | `evidence/targeted-regression-1.json`：19项，0失败/错误/跳过；覆盖版本服务、PG激活、Walk-Forward、Research/SIM Comparison、Publish、参数和比较算法 |
| 修复后受影响目标复验 | `evidence/final-activation-tests.json`：7项，0失败/错误/跳过；core5项+PG2项，包含永久部分训练失败回归 |
| PostgreSQL16 / Flyway | V1–V49实际迁移及validate；受限运行角色、FORCE RLS与无租户scope不可见均断言通过；未改变角色或RLS授权 |
| Strategy Version State | DRAFT激活PASS；ACTIVE重复幂等；ARCHIVED拒绝；客户端状态替换400，仍为ACTIVE |
| Identity Preservation | 完整版本行除status/updated_at外一致；参数/config/source/checksum漂移分别409；历史Run DRAFT快照未回写 |
| Formal Publish | 两类策略均DRAFT发布409→显式激活→同VersionId/原Run正式Publish SUCCEEDED；未mock发布结果 |
| Tenant Isolation | 未认证401、无写权限403、其他用户/跨租户/不存在404；未选中客户端VersionId400，未完成或失败Validation409 |
| Concurrency / Audit | 每类两并发请求均收敛ACTIVE，仅一条成功审计；重复请求返回原结果。撤销隔离库审计INSERT权限时500，版本状态保持DRAFT且审计0；随后恢复测试授权 |
| Frontend | `npm.cmd run build`通过TypeScript及Vite；最终日志 `evidence/frontend-build-after-route.log`；既有chunk size提示为NON_BLOCKING |
| Comparison Unit | `npm.cmd run test:unit -- tests/unit/research-sim-comparison.test.ts`：3/3 PASS；日志 `evidence/frontend-comparison-unit.log` |
| Playwright | `evidence/browser-7/playwright-result.json`：真实登录及双策略激活→正式发布，1场景PASS，0跳过/重试；fixture退出0且database-evidence PASS |
| 恢复与自动动作边界 | SMA实际POST已成功后丢弃响应，GET恢复ACTIVE；刷新、重登和页面切换不新增激活POST；每版本只有一次激活请求和审计 |
| 中英文与窄屏 | 桌面中文及英文390px交互通过；两张卡片截图已实际查看，长ID/Checksum换行可读，卡片边界未越出视口 |
| No Trading Mutation | 激活前后Canonical Order/Trade/Ledger、Paper及Continuous SIM事实摘要一致；浏览器隔离库这五类表最终均0，没有自动创建/启动SIM；LIVE保持禁用 |
| Independent Review | `independent-review/REVIEW.md`：TARGETED_INDEPENDENT_REVIEW_PASS；唯一独立reviewer自主运行6项测试并独立复现/关闭R1；路由变更另作影响范围补核对 |

复现入口保留在仓库外：

```powershell
python -X utf8 -B E:/nq-v102-selected-activation-20261028/run_tests.py 'StrategyVersionServiceTest,SelectedVersionActivationPostgresIntegrationTest,WalkForwardPostgresIntegrationTest,ResearchSimComparisonPostgresIntegrationTest,BacktestPublishServiceTest,WalkForwardPolicyTest,ExperimentParametersTest,ResearchSimPerformanceComparisonTest' <新的证据标签>
python -X utf8 -B E:/nq-v102-selected-activation-20261028/run_browser.py <新的证据标签>
```

需先在原任务专属容器身份核对后恢复该隔离PG；runner从private配置读取口令并脱敏日志，口令不写入本报告或Git。浏览器runner新建fixture、启动仅本机Vite及真实Servlet，结束后关闭自身进程并清理自己的随机数据库/角色。最终证据不依赖旧资格实例或旧包运行。

浏览器7真实持久化身份：

| 策略 | StrategyVersionId | 原Run | 发布及不可变事实 |
| --- | --- | --- | --- |
| SMA | `sv-4e4197c5-a489-4135-9fcd-50ac32726bec` | `brn-45f4a6e1-8cfd-4083-b4ef-17be8091b8e6` | publishSucceeded=true；audit=1；版本身份/历史Run快照MATCH |
| Channel | `sv-bd388830-bc6b-4d71-b647-fd0f87455c37` | `brn-493f9527-192c-4f85-ac44-d5c47053bf13` | publishSucceeded=true；audit=1；版本身份/历史Run快照MATCH |

完整Experiment/Validation/Trial/Config关联见 `evidence/browser-7/database-evidence.json`。

## 历史失败、修复与Remaining Findings

- **R1，已关闭**：独立审查发现合法最终选择在另一训练候选失败时被过度拒绝。原实现要求所有最终训练Result成功，但原producer允许部分失败后冻结有效成功选择。删除该额外allMatch，保留所选Trial/Result/Run全部成功和经济身份核验；增加永久PG回归。独立原probe修复前409、修复后200 ACTIVE且同VersionId，分别保留 `independent-review/partial-training-before-fix.json`及`partial-training-result.json`。
- browser-1：classpath误包含public-demo，发行边界正确拒绝；修正测试runner模块推导。browser-2/3：缓存Chromium可执行文件缺失，改用本机预检可启动的Edge。browser-4：首次登录声明弹窗拦截，测试按真实确认及不再提示流程完成。
- browser-5：新增发布链接误指向无发布按钮的可视化页。修正本组件链接到原双ID操作页，独立补核对通过；未改原发布页面或门禁。
- browser-6：已点击正式发布，但采集器监听了错误路径而超时；按现有客户端 `POST /api/publishes?backtestRunId=...`修正观察。browser-7新fixture完整通过，并以数据库确认两类各一次发布。历轮目录分别保留，失败未覆盖、未追认为PASS。
- 最终审查SHA见 `independent-review/reviewed-files.json`；最终候选逐文件SHA与差异说明见 `evidence/final-candidate-files.json`。审查后如仅采集spec变化，明确单列；无未经补核对的生产实现变更。
- Remaining Findings：本轮范围无未关闭的确认阻断。既有Vite chunk size提示不影响本次功能验证。exact-head CI、新源码导出、新安装包、Windows资格重验均 **NOT_RUN**，属于后续任务，不能由本地通过推定。

## Changes / Files

1. `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/port/StrategyVersionRepository.java`
2. `backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/application/StrategyVersionService.java`
3. `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/strategy/infra/jdbc/JdbcStrategyVersionRepository.java`
4. `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/research/SelectedStrategyVersionService.java`（新增）
5. `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/research/StrategyExperimentController.java`
6. `backend/nq-core/src/test/java/com/guidinglight/nexusquant/strategy/application/StrategyVersionServiceTest.java`
7. `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/SelectedVersionActivationPostgresIntegrationTest.java`（新增）
8. `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/SelectedVersionActivationBrowserFixtureMain.java`（新增）
9. `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/ResearchSimComparisonFixture.java`
10. `frontend/src/features/experiments/SelectedVersionActivation.tsx`（新增）
11. `frontend/src/features/experiments/WalkForwardPanel.tsx`
12. `frontend/src/features/experiments/validationApi.ts`
13. `frontend/src/api/query-keys.ts`
14. `frontend/src/i18n/locales/zh-CN/pages.json`
15. `frontend/src/i18n/locales/en-US/pages.json`
16. `frontend/tests/experiments/selected-version-activation.spec.ts`（新增）
17. `release/source-manifest.json`（仅新增上述两个生产文件路径，静态检查各出现一次；未导出或构包）
18. 本报告。

Migration: NONE。V1–V49、Canonical Order/Trade/Ledger生产代码、原发布ACTIVE门禁及Comparison严格经济身份判断均未修改。没有执行Flyway Repair。

## 原制品、资格与收尾

`evidence/protected-artifacts.json`重新计算并确认以下SHA256未变：

| 保护对象 | SHA256 |
| --- | --- |
| 原v1.0.2候选ZIP | `62ba33a778cb84b209d9a2e2d1167a6f6b12103be896cc199c08c1a8c33b3823` |
| 官方v1.0.1 ZIP | `5171222635a8fafca02045562eb7a9c52b3aa1a1a913886b8995468471612dfb` |
| 原Windows资格报告 | `8b6f52a149ff115afe0cb850ab0907d2ed1de3c81fcf5af083b89fcf1ddeb017` |

原报告 `E:/nq-v102-windows-runtime-20261028/NQ_V102_WINDOWS_RUNTIME_QUALIFICATION.md`保持：Fresh Install/Auth/V49已执行部分PASS、完整Core E2E因原发布链阻断；Upgrade、Backup/Restore、Rollback各自PASS；Failure Recovery NOT_RUN；整体WINDOWS_QUALIFICATION_BLOCKED。旧Manifest摘要仍为 `170d225bae7d9578b276abe96e41e0a569a994eed12522cc744123d9c5e84457`，本轮不赋予其新的资格。

67个既存容器ID、镜像、运行/退出状态及StartedAt前后MATCH，见 `evidence/resources-before.json`和`resources-after.json`。未操作正式安装、旧资格实例、共享Docker Engine、网络或代理；未Prune或删除既有Volume。任务专属PG容器已停止，保留绑定数据目录、原失败及成功证据；Java/Vite测试进程已结束。

精确暂存后的原始 `git diff --cached --check`及最终 `git write-tree`结果见 `evidence/stage-identity.json`，与最终答复共同提供实际tree身份。

Commit: **NOT_COMMITTED**

Push: **NOT_PUSHED**

PR / Merge: **NOT_CREATED / NOT_MERGED**

Release / Tag: **NOT_CREATED**

New Package / Windows Requalification: **NOT_RUN**

Ubuntu Deployment: **NOT_RUN**（本轮无授权）

LIVE: **DISABLED**
