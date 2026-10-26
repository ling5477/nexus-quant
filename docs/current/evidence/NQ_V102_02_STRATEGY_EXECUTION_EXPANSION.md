# NQ-V102-02 策略执行扩展

## 结论与来源

`IMPLEMENTED / TARGETED_VALIDATION_PASS / INDEPENDENT_REVIEW_COMPLETE`；本任务尚未 Commit、Push 或创建 PR。基线为 `89eed70162fdebf87cccd15c54130847d527ca93`，分支 `codex/nq-v102-strategy-execution`。

前项研究数据集产品化已由 [PR #83](https://github.com/ling5477/nexus-quant/pull/83) 正常合并：候选 `0b386759da43edf67cc45f507f7a8a612e3ebfb6`，PR CI `40294468193` 为9/9成功；合并提交为本轮基线，dev push CI `40296744230` 为9/9成功。两者 tree 均为 `9376061f65812e7b9e927b301ab09e796fa81dc3`。原38文件提交 `6159400d4a148b1294f8b1752ad28a3c2d0835db` 保留，初始 tree=`cda72a49efd17f2733efaea4f5699ba767e32191`；必要交付修正使 PR 最终涉及40文件。PG16关键数据库测试7项零跳过；旧 Demo 401 已在原dev与候选同条件复现，归因为旧夹具缺少邮箱验证/READY登记，没有放宽权限。

## 策略与执行合同

新增 `SPOT_CHANNEL_BREAKOUT_V1`，准确含义是 **仅做多、1h、收盘确认型 Donchian 通道突破**，不是完整海龟系统。默认 `entryLookbackBars=20 / exitLookbackBars=10`，版本参数满足 `2 ≤ exit < entry ≤ 200`。

- 入场阈值为此前 entry 根的最高价，退出阈值为此前 exit 根的最低价，排除当前信号 bar。
- 初始方向 FLAT；FLAT 严格收盘突破上轨时 ENTER_LONG，LONG 严格收盘跌破下轨时 EXIT_LONG；等于阈值或通道内波动为 HOLD。预热至少 entry+1 根完整连续 bar。
- `Decision` 分开表达目标方向、目标敞口和交易动作。HOLD 不进入 sizing，不创建执行意图、再平衡或加仓。原 SMA 保留每根 bar 的 REBALANCE 语义与原 DECIMAL128 计算结果。
- `SpotIndicators` 只共享已收盘输入校验、均值和此前窗口极值；`SpotTargetStrategy` 是两种正式策略共用的版本解析与决策入口，回测和 SIM 使用同一合同。
- SIM 以相邻不可变 `strategy_sim_decisions.target_exposure` 恢复目标方向，另在已有输入 JSON 保存 previous/desired state 与 action。方向不从账户实际持仓推断；拒单、未成交与部分成交仍由 canonical 执行/对账路径处理。目标敞口最多8位小数，避免已有数值列舍入改变状态。没有新增表或 migration。
- Continuous 种子窗口只预热指标，开始时仍为 FLAT；最近500根滚动截断不会重置已持久化方向。前驱决策缺失、时间不连续、版本或校验值不符时拒绝恢复。旧 DECIDING、稳定请求身份和游标 CAS 恢复路径保留。
- 信号严格消费已收盘且实际可见的输入，执行继续使用 `NEXT_OPEN_STRICTLY_AFTER_AVAILABLE_AT` 或既有合法连续报价事件。缺口/迟到/无合法后续事件不制造理想化成交。

正式页面增加通道策略类型与参数表单，保留 SMA 参数；中英文解释 HOLD、当前 bar 排除、初始方向与收益未证明。Public Demo 的后端策略限制保留，创建入口不提供新策略。Continuous SIM 的 OKX/BTC-USDT/1h 与 provider 准入没有扩展。

真实 API 验证发现 SIM 冻结输入读取仍限500根，初次720根回测发布后的 SIM 创建被拒绝。只将该读取上限对齐已有输入身份合同的720根，继续核验完整数量、内容摘要、来源和版本；补充720接受、721在资金写入前拒绝的证明。决策列表和 Continuous 最近窗口仍保持原500行边界。

## 验证

| 验证面 | 实际结果 |
| --- | --- |
| Core 与回测 | `SpotTargetStrategyTest` 5项、原 SMA 2项、`BacktestExecutionServiceTest` 10项通过 |
| PostgreSQL 16.15 | 显式启用 `nq.strategy-sim.pg.required=true`；正式回测→评价→发布→canonical SIM、HOLD无订单、持久化拒单后不盲重试、500根滚动窗口和两个新 JVM 恢复，以及原 SMA/拒单恢复，共5项通过、零跳过；后补720/721边界1项通过、零跳过 |
| 前端 | 类型/构建通过，四个相关测试文件12项通过 |
| 独立审查 | 初始19项技术文件 identity=`ac171b25e03fd095f15ddb6f06740e47c695095c442c54853e3c68b2cd60b3a4`；独立 Java oracle 64,009断言，包括15组窗口、720次目标状态恢复和基线 SMA 1,000前缀对比。未发现 actionable finding |
| 输入边界补审 | identity=`81e81e7af4cfc5147141c2a97c852eb7f730ec0fd309620de52454bf2e255ce3`；仅两文件变化，其余17项不变。独立编译实际候选读取方法，1/500/501/719/720接受、0/721拒绝，摘要/计数/版本/范围/provider负例，12断言通过 |
| 正式浏览器 | 创建新策略及20/10可用版本；M=N被拒且数据库版本数仍0，更正后恰好1个版本；刷新业务写请求0。1280×720桌面、390×844手机及1440×900英文表单已查看；BTC/ETH结果页面均显示实际720点权益曲线 |

PG16 定向命令为 `mvn -f backend/pom.xml -pl nq-app -am test`，使用 `-Dsurefire.failIfNoSpecifiedTests=false`，测试选择：

```text
-Dtest=SpotTargetStrategyTest,SpotSmaTargetStrategyTest,BacktestExecutionServiceTest,StrategySimPostgresIntegrationTest#channel*+formalServicesBindDataset*+continuousCursorWindowProjectsDurableRiskRejectionBeforeNextRun
-Dnq.strategy-sim.pg.required=true
-Dnq.strategy-sim.pg.url=jdbc:postgresql://127.0.0.1:15444/postgres
```

720/721新增后只定向执行 `StrategySimPostgresIntegrationTest#channelReplayAccepts720BarsAndRejectsOverflowBeforeFunding`。数据库为任务独立 PG16.15 容器；凭据仅为任务合成夹具，不纳入本报告。其余未变化路径复用已通过的同候选技术证据，没有重跑发行资格。

拒单测试只构造隔离数据库中的 durable rejection，以证明目标状态与成交分离及 HOLD 不重试，**不表述为新做了实际 Risk Engine 拒单证明**。独立审查自行执行 oracle，数据库与新 JVM 证据由实现者执行并由审查者核对，没有独立重跑全库或逐提交点崩溃注入。

## 正式 runtime 使用

在任务 PG17.7 中新建 `nq_v102_strategy_product` 数据库副本，保留上轮真实公开数据及原证据，创建新的策略、版本、研究、配置和运行身份。最终任务 runtime JAR SHA256=`94396fbee651f5cc97e8535f0cd11f8c3516a9ed23544d5421bad0c3ecd36927`，后端18890、前端51890。所有业务写入通过正式 HTTP API，没有直接构造成功回测记录。

| 真实冻结输入 | 完整 bar / 权益点 | 模拟成交 | 同输入、版本、成本配置重复结果 |
| --- | --- | --- | --- |
| BTC-USDT | 720 / 720 | 22 | 两次持久化成交和权益投影完全一致 |
| ETH-USDT | 720 / 720 | 16 | 两次持久化成交和权益投影完全一致 |

BTC 正式发布后创建 `ptr-4dbff0f3-6ea1-407c-a4af-87c5f16c6167`，canonical account=2；继承原输入摘要 `cb333d1a0f742fe149e2e030818858e50b49c5143b3c964d77c8fc5b6d6f5bcb`，初始预算/现金10000、订单与成交0。此真实使用 run **仅创建，未启动**；实际 SIM 成交、退出与 Continuous 恢复由上述隔离 PG16 测试证明。该使用环境 GLOBAL_TRADING kill switch 仍 ENGAGED、LIVE关闭、Continuous轮询关闭。

运行ID、HTTP回执、两次有序业务投影、初次500边界失败和后续结果位于 `artifacts/nq-v102-strategy-execution/`，包括 `product-results.json`、`http-receipts.json`、`product-run*.log`、`pg16-targeted-final.xml`、`pg16-720-boundary.xml`、独立审查 JSON 与 `browser/` 截图。原始日志和运行数据不入Git。

20/10是研究基准，参数最优性及盈利性未证明；30天样本仅证明软件链路。未增加 ATR/追踪止损、金字塔、批量实验、Walk-Forward 或完整 Factor Library。没有 Release、Tag、生产部署或 LIVE 授权；本阶段远端 CI 尚未运行，不能借用前项9/9作本阶段交付证明。
