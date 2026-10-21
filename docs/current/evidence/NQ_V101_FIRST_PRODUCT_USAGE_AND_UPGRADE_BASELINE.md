# NQ-V101-01 产品使用与升级前业务基线执行证据

执行日期：2026-10-21（Asia/Shanghai）。结果：`IMPLEMENTED / SELF_REVIEWED`；限本轮隔离产品使用、恢复基线和目标修复，不是 V1.0.1 升级、远端 CI、发布或 LIVE 接受。

## Baseline

| 身份 | 本轮核验结果 |
| --- | --- |
| 仓库与分支 | `E:/Project/nexus-quant`，由干净最新 `dev` 建立 `codex/nq-v101-first-usage` |
| Starting / Current HEAD | `30b6856d2e73bc0517298a1e7f48365e086ffc2f`；本轮没有提交 |
| origin/dev | `git fetch origin`、`git pull --ff-only origin dev` 后仍为同一 SHA |
| 正式 Release | GitHub `v1.0.0`，published、非 draft、非 prerelease；本轮只读核验 |
| Snapshot / annotated tag object | `be816342581c8ab09a2c9b70dd8e9d1c88dcec8c` / `955023dc1325093de21dca85b918fc911f4a1e6a` |
| 正式包 SOURCE_SHA | `5231266d366680ed5c6c1e38ab84adb33ffcd644`，与开发 HEAD 分列 |
| Manifest SHA256 | `c0d350875c822c48edd489992fe27a0cf5e5c9dae44d1dd772938dd33b482ff1` |
| 正式包目录 | `C:/nq-v1-freeze-20261020-565764cc/package-amd64-frozen`；逐文件与正式 provenance 核对全部一致 |
| 宿主与隔离运行 | Windows 11 amd64；Docker Desktop Linux engine；独立项目 `nq-v101-usage-20261021`，UI 18780 / API 18788 |
| 数据目录 | `C:/nq-v101-usage-20261021/installation`；检查容器 Compose label 和 PG mount 后才恢复 |
| 产品 schema | PG16 / Flyway V1–V34，备份 manifest 与本轮 DB history 一致 |

核对了 AGENTS、`.agents/README.md`、相关 Skills/统一回归合同、Work Order 模板、README、INSTALL、CHANGELOG、STATUS、ROADMAP 和 TESTING 当前摘要。README/STATUS/CHANGELOG 的未发布表述已经落后于 GitHub 正式 Release；本轮仅作为历史声明记录，没有据其否定用户提供并已在线确认的发布事实，也没有重开 GateAUDIT 或修改历史 authority。

Docker 初始 daemon 未运行，执行 `docker desktop start` 后可用。既有容器由其原 restart policy 启动；没有对其数据库执行业务写入、重置或恢复。本轮所有 SIM 写入均在新安装或另一个新建测试 PG16 容器内。

## 本轮实际业务执行

全部产品对象通过冻结正式包的实际 HTTP API 建立；不是把集成 fixture 当成已安装产品结果。临时执行入口为 `C:/nq-v101-usage-20261021/usage.py`，详细请求状态与响应在该目录 `evidence/product-usage.json`，认证秘密只在受限安装配置中。

| 对象 | 实际身份或结果 |
| --- | --- |
| OKX Spot / Dataset | BTC-USDT / 1h；`0ea148cc-b8bc-488e-b911-7290df11ae23` |
| 行情区间 | `2026-10-18T01:00:00Z` → `2026-10-21T01:00:00Z`，72 根已关闭 bar；本轮真实公开网络捕获 |
| Raw / consumed SHA256 | `d92b8b1d8fcbacd6df1db71a8f9a616f1b5282978869bce747ac92cf51db677b` / `caed83441b51fe12e9a468a59422b877fab48341a079550a99c5a45881217855` |
| 公共规则 SHA256 | `31ccba9b105a8bd25879d600e4e3f45b28a61b7ab2e71d3463f337fcc0a15e29`；当前观测规则，非历史逐时规则 |
| StrategyVersion | `sv-a1c1fe37-2de6-4546-8a0c-f26cfc79b390`，`SPOT_SMA_TARGET_V1`，window=3、investedExposure=1，冻结后未为成交调整 |
| Strategy checksum | `9b4782b0ee7790d58735168247d35601a021fbcf81722584e95c5c7dcc3f32e7` |
| BacktestRun | `brn-cac46236-059d-436b-8e5b-eaf2f0680982`，SUCCEEDED；同 Dataset、同版本、初始资金100 USDT |
| Evaluation | `eval-0246b9ec-19f6-454a-ae7f-746006265ae7`，SUCCEEDED / ResearchValidity AVAILABLE |
| IS / OOS / benchmark | 50 / 22 根；Buy & Hold AVAILABLE；72 根全段27笔回测成交 |
| 研究数值 | 最终权益95.658125383794，策略收益-4.341874616206%；Buy & Hold最终权益101.265421666705、收益1.265421666705%；它们是研究结果，不是盈利保证 |
| Publication | `pub-d4ed7ac1-c240-4fe9-856d-5126c511d599`，SUCCEEDED，沿用现有资格校验，没有直接写 publication 表 |
| Continuous SIM | `ptr-c779508d-04c5-4806-9fed-a6af35bd39ab`，独立 canonical account 2 |
| 历史 SIM 回放 | `ptr-5dfbd6c8-38ba-4437-a8a4-329be7cb0b2b`，独立 canonical account 3，同发布、同参数、同冻结公开历史数据 |

冻结成本假设为费率0.001、滑点10bps、最小名义额5，明确标记 EXPERIMENT_ASSUMPTION；quantityStep=0.00000001、priceTick=0.1、minimumQuantity=0.00001，并绑定公共规则摘要。没有读取私有交易 API、真实手续费或账户凭据，没有制造实时事件时点。

Continuous SIM 初始调度 job 默认 disabled。通过 `/api/scheduler/jobs` 读取后，以 expectedVersion 经正式 PATCH 启用 CONTINUOUS_SIM_POLL（30秒）及 PAPER_MATCHING（2秒）；先有一次明确 run-once，随后存在真实自动 SUCCESS 轮询。本轮游标从00:00Z推进至02:00Z，两个新窗口均为 NOT_TRADABLE / STALE_DATA；随后 RUNNING / WAITING_FOR_BAR_CLOSE，连续失败0。两个补追窗口不使用过期行情强行成交，因此自然运行0订单、0成交，资金100 USDT，持仓0。未为改变这个结果调整策略或伪造订单。

历史回放只用于确定性成交与升级数据覆盖，不冒充 Continuous SIM 自然成交：前两个决策 NO_SIGNAL / INSUFFICIENT_HISTORY，随后 ACCEPTED / EXECUTABLE，经 canonical Risk ALLOW / RISK_RULES_PASSED、Order、Paper matching、Trade、Ledger/Position 完成一笔 BUY。1笔FILLED订单、1笔成交、6条该账户账务分录；cash=0.00057419、position=0.00121998 BTC、mark=81804.3、equity=99.800184104、pnl=-0.199815896 USDT。全库另含 Continuous SIM 的2条拨款分录，故全库 Ledger entries=8，不把两种范围混算。

相同 CREATE operation UUID 重发返回原 paperRunId，未重复创建/拨款；自然运行与历史回放账户隔离。执行完通过原 PAUSE operation 停止连续推进并禁用其调度。PAUSE 令 continuous cursor=STOPPED，父 Paper run 可保留 RUNNING；正式页面明确显示“连续处理已暂停”，这是两种既有状态语义。最后停止本任务三个产品容器及自有 proof PG，全部数据和备份保留。全局产品 kill=ENGAGED、LIVE=false、real provider/client/exchange=false；既有隔离测试内部自己的 kill fixture 不代表操作外部实例。

## 实际缺陷与最小修复

`PaperRunMonitorService.generateDailyReport` 固定写权益、订单、成交等零值。正式包真实返回的 `replayDailyReport` 对同一已有成交 run 仍为 GENERATED / 0订单 / 0成交 / 0权益，原响应完整保留。现有 canonical `/summary` 与 `/strategy-sim/analysis/runs/{id}` 能读到正确事实，正式 UI 也用 canonical 视图。

当前开发分支修复：canonicalAccountId 非空的运行在生成、列表及按ID读取旧日报时抛出 `STRATEGY_SIM_DAILY_REPORT_UNAVAILABLE_USE_CANONICAL_ANALYSIS`，既有异常处理映射HTTP409。生成在 upsert 前拒绝，旧报告不重写、不清理；legacy run 保持原合同。该限制防止假零值，不新增一套日结估值或报表事实源；canonical 日结报告的完整语义留给后续版本。

生产变更仅一个 research service；没有修改 Order/Trade/Ledger/Risk 的写入、计算、幂等或事务算法，没有 schema/migration delta。按统一回归合同采用自查与目标测试，没有启动全量审计。修复仅在开发源码及隔离测试执行，冻结正式包、其已存错误日报和历史证据均未修改；本轮未构建或部署修复包。

## 可复用升级前基线与恢复

原始恢复入口：`C:/nq-v101-usage-20261021/installation/runtime/nexusquant.ps1`。备份路径：`C:/nq-v101-usage-20261021/installation/backups/20261021T033822453Z-787c414a`，创建于2026-10-21 11:38（Asia/Shanghai），schema34、PG16。

- Backup manifest SHA256：`647475bda0120321ba75ac41e715bf258d475bc40e94bd6d8268841a0f82b6dc`。
- database.dump SHA256：`d2e95cb40d3b64017cbd6eddadfb3ba0fdf3326e63aac29e61bb0554ff5b35c2`。
- 基线文件：`C:/nq-v101-usage-20261021/evidence/upgrade-before.json`。其 sourceVersion=1.0.0、sourceCommit 为正式包 SOURCE_SHA。
- 安装配置、备份和 private 内含内部秘密，保留本机受限目录，不纳入 Git 或作为普通证据发送。

32表基线计数如下；每张表还保存完整原列名、真实主键及每个主键行的SHA256，不保存行内容。所有业务关联、策略快照、dataset和cost摘要、状态、金额精度、幂等键等原字段都包含在对应行摘要中。

| 表 | 基线记录数 |
| --- | ---: |
| `accounts` | 3 |
| `exchange_accounts` | 1 |
| `strategy_definitions` | 4 |
| `strategy_versions` | 1 |
| `research_configs` | 1 |
| `marketdata_datasets` | 1 |
| `marketdata_dataset_coverage` | 0 |
| `public_market_captures` | 1 |
| `backtest_configs` | 1 |
| `backtest_runs` | 1 |
| `backtest_eval_reports` | 1 |
| `backtest_publish_records` | 1 |
| `sim_orders` | 27 |
| `sim_trades` | 27 |
| `sim_positions` | 1 |
| `sim_pnl_snapshots` | 72 |
| `paper_trading_runs` | 2 |
| `continuous_sim_runs` | 1 |
| `continuous_sim_bars` | 74 |
| `continuous_sim_operations` | 2 |
| `strategy_sim_decisions` | 5 |
| `strategy_runs` | 1 |
| `orders` | 1 |
| `trades` | 1 |
| `ledger_events` | 8 |
| `ledger_entries` | 8 |
| `positions` | 1 |
| `sim_asset_funding_receipts` | 0 |
| `risk_events` | 1 |
| `paper_run_daily_reports` | 1 |
| `account_snapshots` | 4 |
| `account_equity_measurements` | 1 |

补齐的唯一比较入口为 `scripts/verify/upgrade_business_baseline.py`，复用 Docker exec/psql 读取现有事实，不承担部署或恢复。一次 repeatable-read/read-only 事务读取固定32表；每表上限10000、statement timeout15秒、lock timeout3秒、进程timeout30秒，超界失败而非截断通过。固定UTC，使用 PostgreSQL 原始 JSON 文本摘要，不把金融数值转换为浮点。已有输出拒绝覆盖。

判定规则：旧主键缺失=MISSING，新主键=ADDED，同主键原字段摘要变化=CHANGED；任何差异均返回 DIFFERENT/exit1。字段及主键缺失失败；未来新增列可通过 `capture --reference` 仅投影旧列比较，新列本身不在本基线资格范围。原 Flyway version/checksum/success 前缀必须完全相等，只允许追加成功 migration。结果 MATCH 只代表这些字段的保存一致性，不证明未来候选新增行为正确。

本轮实际用正式 `backup` 建快照，随后通过 API 新增一个任务内 marker 账户，再用正式 `restore -BackupPath` 回到备份时点。恢复仅影响本任务库，marker消失，32表严格 MATCH（`restore-compare.log`，exit0）。随后正式 restart，旧行丢失/修改=0，canonical facts API逐字段相等。

重启后的严格比较保留 DIFFERENT/exit1：仅 `account_equity_measurements` 多一条新观测，sample_id=`24f02b8b-b958-460d-8f31-47fa193c100f`，sampling_bucket=1991726；原样本bucket=1991725。新值为SIM账户1 / NO_BALANCE / equity=null；源代码按900秒时间桶采样，并以(exchange_account_id, quote_currency, sampling_bucket)去重。这是解释清楚的新测量，不是重复资金分录。`recovery-reviewed.json` 单独记录 PRESERVED_WITH_EXPLAINED_NEW_MEASUREMENT，原DIFFERENT及失败断言不改判。实际后续升级应先约定观测写入窗口，不能静默忽略所有新增行。

后续升级应把此备份恢复到另一个明确自有安装，保留旧基线；只有真实V1.0.1候选存在、用户授权更新且满足原可信包合同后，才使用原更新器升级。之后以候选真实SOURCE_SHA捕获并比较：

```powershell
python scripts/verify/upgrade_business_baseline.py capture --container <任务自有PG容器> --source-version 1.0.1 --source-commit <候选实际SOURCE_SHA> --reference C:/nq-v101-usage-20261021/evidence/upgrade-before.json --output <新的升级后基线路径>
python scripts/verify/upgrade_business_baseline.py compare --before C:/nq-v101-usage-20261021/evidence/upgrade-before.json --after <升级后基线路径>
```

这两条是后续可执行比较方式，本轮没有执行V1.0.1升级。`UPGRADE_EXECUTION=NOT_RUN`，`UPGRADE_RESULT=NOT_VERIFIED`。

## 验证与证据

| 验证 | 本轮结果与边界 |
| --- | --- |
| 正式包安装/HTTP | PASS；新安装、首次改密、真实public capture、研究、评价、发布、SIM、原操作编号重发 |
| Backend目标单测 | 15/15；PaperRunMonitorServiceTest 13，PaperRunSummaryServiceTest 2；fail/error/skip=0 |
| PG定向集成 | StrategySimPostgresIntegrationTest 11/11；fail/error/skip=0；修复后重跑，新自有PG16/25443；该现有夹具迁移到V27，不能冒称全部测试在产品V34执行 |
| PG覆盖 | capturedPublicInput、formalServices、continuous*、canonical fill/账务、跨JVM；覆盖无信号、风险拒绝、未终结订单、注入失败STALLED与恢复、并发和重复经济副作用 |
| 新比较工具单测 | 7/7，含丢失、新增、错关联、8位金额变化、schema checksum/失败migration、缺表/错误计数、重复主键、容量及标识符反例；全部合成数据，未冒充正式包运行 |
| 实际备份恢复 | 32表MATCH；产品V34全库恢复、marker移除 |
| 实际重启 | 31表无差异；1表新增可解释测量；旧行全部保存、canonical facts相等；严格DIFFERENT保留 |
| Frontend单测/构建 | NOT_RUN，无前端代码/配置变更 |
| Playwright | 实际冻结UI，Microsoft Edge：登录、评价详情同身份/IS/OOS/benchmark、交易成交/资金分录、连续处理暂停；未mock API；无前端重构 |
| 最终test-compile | PASS，全部受影响Java测试源码可编译；最后仅整理测试import，生产逻辑未变化 |
| diff检查 | 本轮检查普通diff与精确暂存diff；最终结果见交付时Git读回 |
| 新exact-head CI | NOT_RUN，未commit/push，无新的可交付SHA；历史9/9不替代本轮 |
| V1.0.1升级/72h/LIVE | NOT_RUN；无候选包 / 已豁免且不重开 / 禁止范围 |

目标命令（PowerShell中的Maven -D参数按单个字符串传递）：

```powershell
mvn -B -ntp -f backend/pom.xml -pl nq-research -am test '-Dtest=PaperRunMonitorServiceTest,PaperRunSummaryServiceTest' '-Dsurefire.failIfNoSpecifiedTests=false'
mvn -B -ntp -f backend/pom.xml -pl nq-app -am test '-Dtest=StrategySimPostgresIntegrationTest#continuous*+syntheticBudgetCreatesOneCanonicalFillAndLedgerWithoutPrivateProvider+capturedPublicInputDrivesFormalBacktestAndCanonicalSim+formalServicesBindDatasetVersionBacktestEvaluationPublishAndCanonicalSim+distinctJvmProcessesRecoverSubmittedOrderWithoutDuplicateFacts' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.strategy-sim.pg.required=true' '-Dnq.strategy-sim.pg.url=jdbc:postgresql://127.0.0.1:25443/postgres' '-Dnq.strategy-sim.pg.user=<自有测试用户>' '-Dnq.strategy-sim.pg.password=<受限配置读取>'
python -m unittest discover -s scripts/verify -p test_upgrade_business_baseline.py -v
mvn -B -ntp -f backend/pom.xml -pl nq-app -am test-compile -DskipTests
```

本轮实际 Maven 参数及凭据由本机 `C:/nq-v101-usage-20261021/proof.py` 读取受限私有配置传给测试进程，未输出到共享证据。全部产品原始证据在本机同根 `evidence/`；SHA索引 `C:/nq-v101-usage-20261021/evidence-sha256.json`。以下固定关键身份：

| 本机证据名 | SHA256 |
| --- | --- |
| `product-usage.json` | `88df383f73e100b6d8552af707b2f78aeceb010da792339ea829caa65507e223` |
| `backend-proof.json` | `556c25df74ee1c0020a5ea6cb619b48178075f9c83be955a191b615f58b0e0a5` |
| `upgrade-before.json` | `7ba5f6aae9ef3644b6b2a05bb4e86382129613ad102e3032d6e6092bc438bb2f` |
| `upgrade-after-restore.json` | `9f130be1a29fdd4a6cc0313c152dd103942c17228998a5aa595d2c75c84eeca1` |
| `upgrade-after-restart.json` | `7d2b57f00340d60323846b607676f08fa2be06f9cefbccfc06d79105484a3c88` |
| `recovery-reviewed.json` | `4a6ddbdc96117f570446fb8910a4be6f2044e778c939545e70115815d9f0733e` |

保留的工具失败：首次改密后收集器误带已失效Bearer登录，401，清除token后成功；Chrome headless无法启动、旧bundled Chromium目录缺exe，改用已安装Edge；临时登录JS首次未显式UTF8造成中文定位符乱码，改为UTF8后完成；连续暂停在专用SIM页展示，初次误用交易页RUNNING字段作暂停断言的selector timeout不判产品失败。上述均为本轮临时收集器问题，原记录/错误原因保留，没有降低业务断言。严格restart比较的DIFFERENT是有效发现而不是工具误报。

## 发现、范围与交付

- P0：未发现。
- P1：旧日报对canonical run生成假零值，已在开发分支修复并通过目标测试；正式1.0.0包仍保留原行为，待后续候选交付。
- P2/后续：canonical日结报告需要专门的日界/估值合同，本轮使用既有canonical分析复盘，不重新开发；发布前产品文档仍含历史未发布表述，单列后续文档同步。
- OBSERVATION：自然补追窗口STALE_DATA、等待新bar无成交；默认调度disabled；900秒时间桶新增NO_BALANCE观测。均有解释且不伪装成交或零收益。
- 已存在并复用：公开行情捕获、策略/回测/评价/发布、canonical SIM/Risk/Execution/账务、分析与UI、原安装器backup/restore/restart、现有11项集成测试。
- 本轮新增/修复：只读32表升级指纹比较及7项反例；旧日报canonical保护及unit/PG HTTP回归；本执行证据。
- 数据库变更：仅任务隔离业务数据；schema/migration变更0。既有用户数据、冻结发行包、Tag/Snapshot/manifest均未改写。
- 最终Git动作：按六文件精确allowlist暂存，`NOT_COMMITTED / NOT_PUSHED`，未创建PR或合并。
- Next Action：用户审阅暂存变更；对应Git动作须另行明确授权。真实V1.0.1候选形成后，复用此备份/身份和比较入口执行定向升级；本轮到此停止。
