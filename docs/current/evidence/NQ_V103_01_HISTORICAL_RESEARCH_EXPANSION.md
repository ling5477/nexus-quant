# NQ-V103-01 有界长历史研究

日期：2026-10-30。此报告记录功能候选与已执行验证；最终 PR 文档 head、合并及清理在交付完成后另行绑定，不以功能候选 CI 替代后续准确提交的 CI。

## Baseline Commit

- 仓库：ling5477/nexus-quant；起始 `dev`、fetch 后 `origin/dev` 均为 `09de4b045b9befca82cfd2ad1234698eabdb96c0`，没有前进差异；初始工作区干净。
- 任务分支：`codex/nq-v103-historical-research`。需求依据：仓库外 `E:/Project/NQ_V103_PRODUCT_ROADMAP.md` 的 NQ-V103-01。
- 正式发行仍为 v1.0.2 / V49；本开发候选的 forward migration 到 V51，不修改已发布迁移、Tag、Snapshot 或 Release。
- Java canonical authority、SIM 边界、LIVE=DISABLED、Kill Switch=ENGAGED 保持。未连接私有交易账户、未部署、未操作服务器。

## Implementation Summary 与 Actual Data Limits

复用原接入任务、OKX Adapter、行情表、Dataset、Java Backtest Engine、实验调度器及原前端页面。新增研究冻结版本仓储，没有第二套行情、回测、Order/Trade/Ledger 或执行引擎。

| 环节 | 实际合同 |
| --- | --- |
| 研究入口 | OKX SPOT，BTC-USDT / ETH-USDT，1h，1～180 个完整 UTC 日；90/180 日预设 |
| 时间边界 | 页面开始日包含、结束日不包含；原服务请求以结束时点减 1 ms 表达包含式末端 |
| 公开历史 Adapter | 原每页最多 300 根、125 ms 节流及有界超时/重试保留 |
| 每次显式接入 run | 原最多 10 页 / 3000 根；180 日先 PARTIAL，再显式续传 1320 根；没有自动无限续拉 |
| 通用接入服务 | 原通用任务并非统一限定 180 日；本次限制的是正式研究入口与冻结/研究消费，不把数据库容量宣称为无限历史容量 |
| 研究冻结 | 旧 V1：1～720；新 V2：721～4320；超过 4320 拒绝，不静默裁切 |
| V2 仓储 | 每块最多 500 根 / 512 KiB，最多 9 块，总最多 8 MiB；manifest 64 KiB、配置引用 8 KiB 内 |
| 研究读取 | JDBC 以有限页读取；冻结处于同一 REPEATABLE READ 事务，有额外一根探测上限；读取版本逐块验证 |
| 回测 | 原执行器；完整可见前缀先校验一次，策略每步最多读取 201 根指标窗口；经济事件仍逐根产生 |
| 实验 | 旧短输入最多 16 候选；长输入最多 4；沿用原串行调度、每 trial 最多 3 attempts |
| Walk-Forward | V1 仅 720；V2 仅 2160 / 4320；其他长度可单次/参数实验，WF 显式不可用 |
| Continuous SIM | 默认身份/冻结上限 720、最近 500 根滚动窗口、撮合、恢复与安全限制没有扩大 |

## Dataset / Freeze Contract

`HISTORICAL_RESEARCH_INPUT_V1` 的字段顺序、规范字节与摘要语义保持；旧快照仍内嵌 bars，旧读取器继续拒绝 721。

V2 在原 Dataset 上首次冻结不可变版本。配置、Run、实验和验证复用紧凑引用：`version / datasetVersionId / sha256 / sourceSha256 / manifestSha256 / frozenAt / barCount / chunkCount / totalBytes / replayVisibilityVersion / replayVisibilitySource`，不逐候选复制完整行情。

manifest 绑定 Dataset/来源身份、UTC 范围、数量、原观察时间、回放可用时间假设及有序分块摘要。消费摘要、来源摘要、manifest 摘要各有职责。读取复核父 Dataset、分块数量/顺序/字节/摘要、完整连续覆盖和冻结时间；缺块、坏摘要、未来观察均拒绝，不能回退到 mutable 行情表。

同 tenant/owner/Dataset 首次版本稳定；后续采集修订不覆盖历史版本。原 `snapshotAt` 固定为首次 frozenAt。新表 FORCE RLS、HTTP owner 检查和正式 Worker TenantExecution 授权链均保留；其他用户不能读取版本。

`sourceObservedAt` 表示实际采集观察；回放 `availableAt` 仍为 `CLOSED_HOURLY_BOUNDARY_V1 / EXPERIMENT_ASSUMPTION`。180 日样本不代表拥有供应商历史 PIT 发布证明，也不抹去原留出已暴露说明。

## Backtest Compatibility

正式链路仍为 Frozen Dataset → Backtest Configuration → 原 Strategy Version → Backtest Execution → Evaluation。不改变 SMA / Channel 公式、next-event 严格时间因果、闭合行情、费用、滑点、精度或账户事实。

长 WF：设 N 为 2160 或 4320、u=N/6，四个训练窗为 `[0,2u)`、`[0,3u)`、`[0,4u)`、`[0,5u)`；后三个逐 u OOS 与最终 u holdout。维持 16 步、训练内选参、各测试独立 FLAT、真实成本压力重跑。最终版本仍需显式人工激活；未激活、未发布或启动 SIM。

4320 根、4 候选预算：28 验证 Run、含源实验 32 Run、经济窗口处理 66240 根（预热/重试另计）；最多 3 attempts 的理论总 Run 上限 96。页面展示这些边界。

## Database Changes

- V50：`research_dataset_versions`、`research_dataset_chunks`；父引用、唯一首次冻结、FORCE RLS、不可变触发器、deferred 完整块约束。
- V51：精确替换原验证窗口 CHECK，增加按政策版本/父输入/固定步骤校验的触发器；缺失、JSON null、未知政策及错误窗口均拒绝。V1～V49 未修改，不使用 Flyway repair。
- 隔离 PG 从有实际旧数据的 V49 升级到 V51并 validate；旧短输入和结果保留。回滚保留两张新增表及冻结历史，不做逆向删表。

## Frontend Workflow

原行情准备页选择 UTC 范围 → 创建稳定接入任务 → 显式分批获取 → 原 Dataset 创建/质量检查 → 原研究/回测配置 → 绑定原策略版本与冻结输入 → 启动正式回测/评价 → 原可视化及实验/WF 页面。

展示 Requested Range、Actual Coverage、Total Bars、Data Quality、V1/V2 frozen identity、研究状态与预算；刷新只读，不创建新 run、冻结或经济事实；保留 operation URL 恢复。

## Unit Tests / PostgreSQL Tests / Review

本地原始证据根目录：`E:/Project/nexus-quant/artifacts/nq-v103-history/`。这些文件不纳入发行制品，也没有以历史测试冒充本次测试。

| 验证 | 实际结果与证据 |
| --- | --- |
| 后端目标单元 | 34 个不同测试通过；`unit-second.log` 32 项与 `postgres-final.log` 中新增 LongResearchSnapshotTest 2 项；重复执行的 codec 测试不重复计数 |
| 前端目标单元 / Build | `npm run test:unit -- tests/unit/research-dataset.test.ts tests/unit/walk-forward-policy.test.ts tests/unit/research-sim-comparison.test.ts tests/unit/backtest-binding-identity.test.ts`：4 文件 34 项 PASS；`npm run build` 的 TypeScript/Vite PASS。原工具输出 chunk 为 e07fbd / f88b20，没有另存文件日志 |
| 新长历史真实 PG | `LongHistoricalResearchPostgresIntegrationTest` 3/3、0 skip，`postgres-final.log`；真实 Adapter 对本地受控 HTTP、3000+1320、稳定 run 身份、已确认前缀/失败恢复 |
| 冻结/质量/权限 | 4320 冻结与正式回测评价/重复一致性、720 兼容、721/2160/4320及超限；缺失、重复、乱序、未闭合、非法 OHLC、Observation After Freeze、Checksum Mismatch、缺块、不可变与 RLS 负例 |
| 长窗口与因果 | 4320 SMA、2160 Channel，各 4 源候选 +28 验证 Run；修改未来 OHLCV 不改变首训练排名、收益/回撤、消费前缀摘要及逐条经济事实 |
| 跨 JVM 恢复 | 512 MiB 子 JVM 在真实 PG 切点强杀，原 Run=CREATED / attempt=RUNNING，回滚后成交/权益事实均 0；另一 512 MiB JVM 恢复原 attempt，成功结束 |
| 旧正式链 | Comparison 2、SelectedVersionActivation 2、Experiment 1、WF 1，共 6 通过，`compatibility-regression.log` |
| SIM 隔离回归 | 原 720 接受/721 拒绝、500 滚动窗口后 fresh JVM 恢复、断点与丢失确认无重复事实，3/3，`sim-boundaries-final.log` |
| 独立候选审查 | 独立审查未参与实现；SQL NULL 三值绕过 P2 已修复并以真实 PG 负例验证；没有未关闭 actionable finding |
| 独立 oracle | Java 39098 断言，lookback 3/20/200、两策略逐完整前缀与有界指标窗一致、旧摘要/长 codec/未来观察反例；`independent-oracle.log` |

真实 PG 共 12 个不同集成测试通过。首次单元编译错误、两次 V51 SQL/约束名失败、旧 SIM 测试拒绝空口令均保留原日志；修复后准确候选通过不追认原失败。独立审查及 22 个生产文件摘要保存在 `independent-review.md` / `independent-reviewed-candidate.json`。

## Browser E2E 与公开样本

正式 Playwright `playwright.long-history.config.ts`，Chrome / 真前端 / 真 Spring 应用 / 真隔离 PG，`browser-e2e-complete.log`：**2/2 PASS，50.2 秒**。BTC180：4320 接入→质量→研究→正式配置/版本→V2 冻结→Run/Evaluation→4320 权益点；390px、中英文和两次只读刷新。ETH90：2160 分页→零缺口 Dataset。没有用 Mock API 代替业务响应。

行情来自本次实际读取的 OKX 官方公开历史响应，归档 `real-public-history.json` SHA256=`3546e043a1a8dad90a84f67d1bf19927ddc84253e329abcf47b74e4ceb478b8c`；逐页原响应、URL、摘要与 observedAt 保存在 `public-capture/`。BTC `[2026-04-01,2026-09-28)` 4320 根；ETH `[2026-04-01,2026-06-30)` 2160 根。

浏览器使用 **REAL_OKX_PUBLIC_CAPTURE_REPLAY / ARCHIVED_PUBLIC_CAPTURE_REPLAY_NOT_BROWSER_TIME_EXTERNAL_FETCH**：正式 Adapter 从隔离 HTTP 重放上述真实归档，不声称浏览器当时直接外联；故障注入与资源测试另标 SYNTHETIC_LONG_HISTORY。

另一次可检查的真实页面操作：BTC Dataset `ab04ad34-b2b5-4a43-a1e9-fda4be2925da`，V2 `f200a050-b0ff-4b69-9a17-bbad55e40eda`，Run `brn-aa6bce50-add9-4f41-8418-041534a26c64`，1277 成交、netPnl=`-3651.148264771938000000`；不是盈利或统计优势证明。原结果截图 `browser-02/btc180-result-desktop.png`。

同页真实 4 候选实验 `b67d8727-5ce2-47b6-83e6-10beccd2d407` 与 WF `37543cdd-3078-4d42-859a-bd9f9a294093`：SUCCEEDED、16 步、28 验证 Run；页面预算/UTC 区间、DRAFT 选中版本及刷新无写已观察，见 `browser-02/final-wf-complete.log`、`btc180-walk-forward.png`。未点击激活/发布。

浏览器首轮断言把显式语言偏好保存误计为刷新写入，以及 AntD 选择器不匹配；后续测试隔离读取断言、确认弹窗/语言及外层选择器后重新完整通过。失败/中断批次各保留独立输出，不覆盖。AntD/React19 与 deprecated、Vite chunk / __dirname 的既有警告保留，没有借机改架构。

## Resource Measurements

候选真实 PG + Java `-Xmx512m`、Windows 本机，4320 根隔离合成样本；详见 `final-evidence/frozen-replay-resources.json` / `walk-forward-resources.json`。

| 指标 | 实测 |
| --- | --- |
| Dataset size / Bar Count | 1,564,525 规范输入 bytes / 4320；紧凑配置引用 1006 bytes |
| Seed + Freeze | 21797.8732 ms，含整批造数，不冒充纯冻结延迟 |
| Query Duration | 43.5198 ms，查询并复核冻结内容 |
| Backtest Duration | 9185.8268 ms，含正式评价 |
| Peak Memory | heap 各池独立峰值合计上界 260,332,976 bytes；非精确同一时刻峰值；单点 heap 141,741,232 bytes，max heap 536,870,912 |
| Database Size | 整个该测试数据库 31,546,391 bytes，不冒充单 Dataset 增量 |
| 4320 SMA / 2160 Channel 源实验+WF | 186393.5247 / 36545.5185 ms，各 32 Run；各池峰值合计上界 330,872,096 / 277,349,752 bytes |
| Query Count | NOT_INSTRUMENTED；不写成 0，不承诺容量线性外推 |

## Changed Files

- core：`MarketdataDatasetService`、`FrozenHistoricalInput`、新 `ResearchHistoricalInput` / `ResearchDatasetVersionPort`、`SpotBarIdentity` 及相应单元测试。
- infra：新 `JdbcResearchDatasetVersionPort`、V50/V51。
- backtest/app：`BacktestExecutionService`、`StrategyExperimentService`、`ExperimentValidationService`、`WalkForwardPolicy`、`SelectedStrategyVersionService`；WF 单元、新长历史 PG 及浏览器 fixture。
- frontend：原 `MarketdataIngestionWorkspace` / `research-dataset`、`FrozenResearchInputSummary`、`WalkForwardPanel` / API 类型、`ExperimentsPage`、中英资源；Dataset/WF 单元与独立长历史 Playwright 配置/测试。
- CI/登记：原 PG job 增加新目标测试、开发源码 manifest 加 3 个 Java runtime 路径、业务 freeze 词与准确摘要登记；新迁移由现有动态 inventory 发现，旧 V44 固定清单的迁移段保持原样。没有弱化 checker、增加 job 或新增治理阶段。
- 本报告与当前 STATUS/ROADMAP 的最小能力及下一任务同步。最终准确路径清单以该 PR diff 为准。

## Known Limitations / Rollback

180 日不是任意多年容量或长期统计优势；时序仍有明确的回放可用时间假设，源实验已观察最终区间，不是 pristine holdout。多币对/分钟级、大并发、Forward SIM Evaluation、Factor Research 不在此任务。

V2 缺失/损坏存储必须失败；不自动重抓旧版本。发现回归时停用长输入入口并保留旧 Dataset、冻结、结果及错误；已合并代码采用受控 Revert PR，保留新增表和历史，不删数据、不改旧 migration、不使用 Flyway repair。回退代码对新 V2 数据不承诺继续消费。

## PR / CI / Merge Commit / Cleanup Result

实现提交 `ae939aabd96b4245497650e9dda0e1a04690475d`，PR [#94](https://github.com/ling5477/nexus-quant/pull/94)。首轮 CI [41003852194](https://github.com/ling5477/nexus-quant/actions/runs/41003852194) 的 V51 空库迁移、动态完整 inventory、独立参考库指纹和空库业务事实均通过；随后旧 V44 快照的 CRLF 单元测试因源码清单误加入两条新迁移而失败。修复仅移除固定清单中的 V50/V51 登记，保留动态 V1～V51 发现和原冻结合同，未改 checker、历史清单或 migration。原始日志 `ci-first-postgres-failed.log` 保留，修复后 schema 单元另记。

修复后的功能接受 head=`68d757748f2958fef501996a0bf0d3dac24df869`，tree=`484791d15067064d343d8d20778db9c3f7e892d9`；[CI 41004482332](https://github.com/ling5477/nexus-quant/actions/runs/41004482332) 为 pull_request / completed / success，9/9 必要 job 全部 SUCCESS。另有27项 schema 合同单元通过，日志 `schema-contract-tests-final.log`；固定清单的迁移段与基线完全一致，22个已独立审查生产文件无漂移。

当前机器接受 pair 绑定上述已验证功能候选。同步状态、路线和本报告的文档提交只改3份文档；最终 PR head 的必要 CI、Merge、Post-merge CI 与 Cleanup 尚待后续真实结果，不提前宣称 NQ_V103_01_ACCEPTED。完整交付后在仓库外 `E:/Project/NQ_V103_01_HISTORICAL_RESEARCH_EXPANSION.md` 同名收尾报告绑定最终身份，避免自引用提交/CI循环。未进行发行、部署或02/03实施。
