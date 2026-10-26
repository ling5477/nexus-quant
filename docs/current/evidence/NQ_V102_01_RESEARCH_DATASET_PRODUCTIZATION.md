# NQ-V102-01-RESEARCH-DATASET-PRODUCTIZATION

2026-10-26（Asia/Shanghai）。开发基线 `origin/dev=58af3c8badae2eb2b535cd512680743538d31a61`，分支 `codex/nq-v102-research-dataset`，起始工作区干净。首次直连GitHub失败后，用已存在的本机代理完成fetch，远端基线未前进。VERSION仍为已发布的1.0.1；本轮是v1.0.2功能开发，不产生新版发行身份。

## 实现与复用

1. 历史分页和恢复原在 `MarketdataIngestionService → AdapterHistoricalKlineProvider → OkxHistoricalKlineAdapter`。旧实现一页最多300条，provider请求500不代表能获取500，且run事务覆盖外部下载。现在按最多300根的连续小窗口读取，每次显式run最多10批；网络在事务外，行情与连续前缀在同一短事务提交。失败保留已确认前缀，用新显式run续拉；同run ID只读原结果。任务更新时间形成5分钟失联租约，旧工作者写入前复核归属，不能覆盖接替运行。
2. 旧普通Dataset只保存目录描述，回测再读共享行情；公开捕获Dataset则通过 `public_market_captures` 保存不可变响应、规则与序列。新研究冻结复用原 `datasetSnapshotJson`，`frozenInput`保存完整bar、消费SHA、原观察时间数组、来源SHA与冻结时间。回放可见性为 `CLOSED_HOURLY_BOUNDARY_V1 / EXPERIMENT_ASSUMPTION`，不把采集时刻伪称历史发布时间。冻结与读取均拒绝收盘前观察、原数组confirm=0、缺失、重复、乱序、混用及异常OHLC。
3. 旧冻结db回测固定加载500，随后读第二页只用于拒绝，`SpotBarIdentity`也限制500。新冻结输入直接读取并校验快照内完整序列，真实消费720；保留正式策略版本绑定和原 `SPOT_SMA_TARGET_V1` 公式、撮合及账务。旧db路径有界加载两页并明确拒绝超过720；旧BTC公开捕获相关回归通过。
4. 正式页面新增Self-Hosted专用UTC日期入口（OKX、BTC/ETH、1h、1至30完整UTC日），结束日期排他并准确转换成原API包含末毫秒的区间。原任务→Dataset预填、Dataset→研究预填均只读导航；刷新/返回不自动写。空instrument目录的新安装也能完成此固定研究范围，旧通用入口仍保留原目录校验。配置/运行结果页显示冻结数量、摘要、来源和假设。
5. 创建正式run时复用原公开捕获外联开关读取对应BTC或ETH公开instrument规则，冻结原响应、标的、当前观察时间和摘要；执行只复核被冻结来源。ETH实际lot/tick/minimum分别为0.000001/0.01/0.0001，BTC为0.00000001/0.1/0.00001，未复制BTC规则。费用0.001、滑点10bps及最小名义额5均为显式实验假设，当前规则不是历史规则。规则外调最多2次×3秒及500ms退避，保留10秒run事务边界；原捕获预算不变。
6. 仅完整、READY/OK、零缺口、1至720根的完整小时描述符进入新冻结；旧通用非整点描述符和Demo保持原合同。进入冻结后SQL实际覆盖不符必须失败，不能静默回退。租户仍由服务端解析，沿用RLS及既有授权。无migration、无新事实表，无Demo白名单、注册、容量、初始化、运行权限或Continuous SIM标的扩展。

OKX官方合同核对于本轮执行：[历史K线文档](https://www.okx.com/docs-v5/en/#order-book-trading-market-data-get-candlesticks-history)，每页最多300，20次/2秒/IP，after取更早、before取更新，confirm=1表示收盘。已下载原文到任务本地临时目录；适配器按125ms节流并有界重试429/50011、5xx及适当网络失败，正文有上限。

## 真实使用结果

任务自有 `127.0.0.1:15442/nq_v102_product`，原生PostgreSQL17.7；Docker启动因Secrets Engine本机socket错误失败，未重置Docker。复用现有安装维护入口准备NOLOGIN owner与受限runtime角色，47份历史迁移migrate/validate通过，正式HTTP为18889，前端51889。未连接用户正式安装。这里不是PG16或发行/升级资格声明。

真实公开窗口为 `[2026-09-26T00:00:00Z, 2026-10-26T00:00:00Z)`；BTC/ETH各720根，质量OK，缺口0。唯一正式版本 `sv-08fdf16b-58de-47c6-9352-4dae04d58a86`（window=20，investedExposure=0.5），通过正式策略/版本API创建，执行源为SPOT_SMA_TARGET_V1，未走fixture策略。

| 标的 | Dataset | 最终候选BacktestConfig | 成交数 / 权益点 |
| --- | --- | --- | --- |
| BTC-USDT | 46b03072-8fad-4de6-b93a-07923b91077f | bcf-3e4ea69a-68e8-46d3-836d-b48b1fd269e6 | 201 / 720 |
| ETH-USDT | 0819b8fe-fc7f-4c69-8b74-3286447f00b0 | bcf-7b8bc26d-6901-437c-91e1-f7784356a141 | 212 / 720 |

每组两次正式运行逐条比较持久化模拟成交（剔除运行/成交/订单ID和自然执行时间）、720个权益快照、决策顺序和绩效，语义一致。任务库内临时修改共享行情volume后，配置冻结JSON不变，第三次正式运行仍消费原摘要且业务结果相同；随后原volume恢复。BTC消费SHA=`cb333d1a0f742fe149e2e030818858e50b49c5143b3c964d77c8fc5b6d6f5bcb`，ETH=`04eb166588eedb3edff3eb22ed6cd813d505fc5b366fe99271d3266c5f62a646`。两组收益均为负，不构成策略有效性或收益承诺。canonical orders/trades均为0，真实资金与LIVE未启用。

另完成ETH全页面实际操作闭环：研究 `rcf-040dcac1-e0ea-4f36-83ef-48715a93d388` → 配置 `bcf-2aaae5c1-d9ce-4458-9ed8-16ac0c705520` → Dataset/正式版本绑定 → 运行 `brn-9bb4b96b-be1e-4282-9ece-e0739eb20a46` → 页面启动SUCCEEDED → 页面评价 `eval-5a04ff13-f316-444c-8f9d-e35914841726` SUCCEEDED → 结果720冻结bar、720权益点、212成交。7次业务写均由真实页面按钮触发，没有API代写或mock；刷新结果页无额外写。回执为 `eth-full-ui.json`，截图为 `ETH-full-ui-*.png`。

## 定向验证

- 后端：`mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=MarketdataResearchSnapshotTest,FrozenHistoricalInputTest,BacktestExecutionServiceTest,MarketdataIngestionOperationTest,MarketdataIngestionVisibilityTest,OkxHistoricalKlineRangeTest,AdapterHistoricalKlineProviderTest,MarketdataIngestionOperationControllerTest,MarketdataDatasetOperationControllerTest,BacktestConfigControllerTest,PublicMarketReplayCaptureServiceTest,ResearchPublicRulesTest' '-Dsurefire.failIfNoSpecifiedTests=false'`：50项通过。
- PostgreSQL：同一reactor执行 `-Dtest=MarketdataIngestionOperationPostgresIntegrationTest,MarketdataDatasetOperationPostgresIntegrationTest,TenantIsolationPostgresIntegrationTest`，启用 `nq.ingestion.operation.pg.required=true`、`nq.dataset.operation.pg.required=true`、`nq.tenant.pg.required=true`，维护URL均为任务15442/postgres、用户nq_task、任务trust认证。两项前置测试通过 `TASK_INGESTION_* / TASK_DATASET_*`环境变量，租户通过 `-Dnq.tenant.pg.url=jdbc:postgresql://127.0.0.1:15442/postgres -Dnq.tenant.pg.user=nq_task -Dnq.tenant.pg.password=`；evidence目录均在本任务artifact下。7项通过，含720/中断300+重建上下文续420、无下载长事务、Dataset稳定回执、跨租户Dataset/研究拒读与拒写、Demo不可读取LOCAL接入任务及Demo不获新冻结入口。
- 前端：`npm run build`通过（tsc及Vite）；`npm run test:unit -- tests/unit/research-dataset.test.ts tests/unit/marketdata-ingestion-recovery.test.ts tests/unit/dataset-operation-recovery.test.ts tests/unit/backtest-binding-identity.test.ts`：4文件72项通过。Vite给出chunk大小提示，未修改构建阈值。
- 真实Playwright：前端目录设置 `E2E_BASE_URL=http://127.0.0.1:51889`、`E2E_EXTERNAL_DEV_SERVER=true`、`E2E_BROWSER_CHANNEL=chrome`，执行 `node node_modules/playwright/cli.js test tests/e2e/research-dataset-productization.spec.ts --project=chromium --output=../artifacts/nq-v102-research-dataset/playwright-final`：2项通过（30.5秒），真实BTC/ETH接入、Dataset720/零缺口、研究预填、刷新无意外写及中英文。
- 使用 `python artifacts/nq-v102-research-dataset/live_backtest.py`、`semantic_check.py`重验最终候选正式API链和持久化语义；`node artifacts/nq-v102-research-dataset/frontend-result-observation.mjs`确认最终两组正式结果页、输入720/权益720、版本与摘要；桌面1440×1000、手机390×844截图已目视检查。
- 后端自查及一次限定独立检查：接入实现者未参与冻结/规则实现，独立构造收盘前观察反例，发现P2；父级修复后独立probe确认freeze/read均拒绝原反例。另独立测得规则延迟失败2次/6750ms，关闭原重试预算与事务不匹配问题。其后仅补旧通用描述符兼容guard及两项定向回归，不重审无关范围。

原始证据根目录 `E:/Project/nexus-quant/artifacts/nq-v102-research-dataset/`（不入Git）：`final-targeted-02.log`、`final-postgres.log`、`validation-inventory.json`、`frontend-build.log`、`frontend-unit-final-02.log`、`playwright-final.log`、`live-ingestion.json`、`live-backtests.json`、`live-backtest-final.log`、`semantic-verification.json`、`semantic-check-final.log`、`frontend-result-observation-final.log`及截图。最终隔离runtime.jar SHA256=`7b28132708ebfa8b2c899ba6389f1ff749a3be7efb7eea47f0bb243457e1e658`。

## 失败与限制

保留首次并行reactor重编译导致的NoClassDefFound、Dataset测试上下文缺新增真实port、单独repackage缺package、首次隔离启动角色/本地种子不满足既有安装guard、Windows3189端口保留及页面测试定位失败日志；已修正任务构建/夹具/定向实现并重验。没有删除断言、放宽生产guard或清库伪造通过。

另尝试既有 `TenantIsolationHttpIntegrationTest`：Self-Hosted构建先被既有Demo配置guard拒绝，使用public-demo测试profile后在Demo用户登录前置返回401，未完成该项旧全链测试。此项记为NON_BLOCKING的既有夹具验证缺口，未修改Demo认证或登记合同；本轮跨租户验证由受限角色真实PG及实际服务拒读测试覆盖，不能据此把旧HTTP测试追认为通过。

30日不是策略有效性证明。真实外部规则与OHLC历史发布时间不可追溯，显式保留回放及成本假设；PG16与生产部署/新版升级不在本轮验证结果中。已发布Tag、Snapshot、正式包及签名证据均未修改。

## 文件与交接

无migration。只登记4个新增运行源码到开发清单，未修改正式发行source-manifest，未做发行冻结。README及当前STATUS/ROADMAP仅更新v1.0.1已发布和本轮v1.0.2范围，历史Gate/C4结果保留。

实际本轮文件清单（精确暂存allowlist）：

```text
README.md
backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/marketdata/OkxHistoricalKlineAdapter.java
backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/marketdata/OkxHistoricalKlineRangeTest.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/ResearchAccountFeeSource.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/marketdata/PublicMarketReplayCaptureService.java
backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/marketdata/ResearchPublicRules.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/marketdata/MarketdataDatasetOperationPostgresIntegrationTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/marketdata/MarketdataIngestionOperationPostgresIntegrationTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/marketdata/ResearchPublicRulesTest.java
backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/TenantIsolationPostgresIntegrationTest.java
backend/nq-backtest/src/main/java/com/guidinglight/nexusquant/research/application/backtest/BacktestExecutionService.java
backend/nq-backtest/src/test/java/com/guidinglight/nexusquant/research/application/backtest/BacktestExecutionServiceTest.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/service/MarketdataDatasetService.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/application/service/MarketdataIngestionService.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/marketdata/domain/FrozenHistoricalInput.java
backend/nq-core/src/main/java/com/guidinglight/nexusquant/strategy/domain/SpotBarIdentity.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/marketdata/application/service/MarketdataIngestionOperationTest.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/marketdata/application/service/MarketdataIngestionVisibilityTest.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/marketdata/application/service/MarketdataResearchSnapshotTest.java
backend/nq-core/src/test/java/com/guidinglight/nexusquant/marketdata/domain/FrozenHistoricalInputTest.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcHistoricalMarketDataPort.java
backend/nq-infra/src/main/java/com/guidinglight/nexusquant/marketdata/infra/jdbc/JdbcMarketdataIngestionJobRepository.java
docs/current/ROADMAP.md
docs/current/STATUS.md
docs/current/evidence/NQ_V102_01_RESEARCH_DATASET_PRODUCTIZATION.md
frontend/src/features/backtests/FrozenResearchInputSummary.tsx
frontend/src/features/marketdata/DatasetOperationRecovery.tsx
frontend/src/features/marketdata/MarketdataIngestionWorkspace.tsx
frontend/src/features/marketdata/research-dataset.ts
frontend/src/i18n/locales/en-US/pages.json
frontend/src/i18n/locales/zh-CN/pages.json
frontend/src/pages/backtests/BacktestDetailPage.tsx
frontend/src/pages/backtests/BacktestsPage.tsx
frontend/src/pages/marketdata/MarketdataPage.tsx
frontend/src/pages/research/ResearchPage.tsx
frontend/tests/e2e/research-dataset-productization.spec.ts
frontend/tests/unit/research-dataset.test.ts
scripts/ci/development-source-manifest.json
```

精确暂存后，`git diff --cached --check`、逐路径清单及`git write-tree`由最终交接记录确认；报告本身属于该tree，不在正文嵌入自引用tree哈希。后续提交命令（本轮不执行）：

```powershell
git -C E:/Project/nexus-quant diff --cached --check
git -C E:/Project/nexus-quant commit -m "feat(research): productize bounded OKX historical datasets"
```

Decision: IMPLEMENTED / TARGETED_VALIDATION_PASS

Commit: NOT_COMMITTED
Push: NOT_PUSHED
Release: NOT_CREATED
LIVE: DISABLED
