# GateZ 当前计划

本文件定义 GateZ 的当前业务范围；阶段、安全状态与唯一机器 `next_action` 以 [STATUS.md](STATUS.md) 为准。GateAUDIT 已冻结，历史资格与残余保留在 [canonical archive](../gates/gate-audit/README.md)，不由本计划重评。

## 第一里程碑：单策略现货回测到 SIM 闭环

里程碑 ID：`NQ-GATEZ-1-SPOT-ONE-STRATEGY-BACKTEST-TO-SIM-CLOSED-LOOP`。GateZ-1 已在 PR #24 合并，`dev` merge/exact-head CI 为 `09b7e1cd9c68033b54b75cf361cdda70b584f3a4 / 36111701008 / 9 of 9 SUCCESS`；当前机器状态与下一动作以 [STATUS.md](STATUS.md) 为准。

代表样例限定为 **OKX SPOT / BTC-USDT**，在隔离环境中走完：

```text
Dataset → frozen StrategyVersion → same-version Backtest → Evaluation → Publish
→ isolated SIM run → canonical Risk / Execution → Order → Trade
→ Position / Cash → Ledger → PnL / replay / trace
```

GateZ-1 必须用实际冻结的策略定义和参数执行回测与 SIM；固定可重放的 bar 内容、来源、可见时点和版本身份。信号只能在可见后形成，成交使用后续可交易时点，避免同一 bar 收盘信号按该收盘价成交。预算经目标敞口转为增量订单，按现金、持仓、在途资金、精度、最小量、最小名义额、费用和滑点计算。10U / 100U / 1000U 各自应给出可执行数量或稳定拒绝原因；收益为正不是验收条件。

隔离 SIM 验收须覆盖同版身份、费用一次性入账、重复触发幂等、资金竞争并发、stop、过期数据与恢复，并用 canonical Order / Trade / Position / Cash / Ledger 事实追溯 PnL。研究侧 Paper 页面可读取或关联这些事实，但不得另造第二套 Paper Order、Trade、Position 或 Ledger authority。既有历史记录须保持可读。可复用现有页面与接口，只补闭环所需入口及可解释结果。

GateZ-1 以隔离 PostgreSQL 和合成 bars 验证算法及 canonical SIM 事实；这些结果不称为交易所历史实测。后续公开历史 bars 的非生产业务 smoke 已单独完成；真实余额、真实账户费率与历史时点交易所规则仍为 UNKNOWN，未知输入不默认为零成本或已合规。设计依据为 [NQ V1 能力盘点](evidence/NQ-V1-CURRENT-CAPABILITY-ASSESSMENT-AND-NEXT-TASK.md)，该报告是 `76caf387` 的分析快照，不是 machine authority。

## 已接受切片：公开市场数据可重放 smoke

任务 ID：`NQ-GATEZ-PUBLIC-MARKET-REPLAY-SMOKE`，状态=`ACCEPTED / CI_GREEN`；技术 PR #27 merge/exact-head CI=`b253dc19124d26cd8b2aab5685d773adc03e3687 / 36141910575 / 9 of 9 SUCCESS`。在**非生产、隔离 SIM** 中，真实 OKX SPOT / BTC-USDT / 1h 公开历史响应的 72 根连续已收盘 bar 已保存为不可变输入，并驱动同一冻结 `StrategyVersion` 的回测、评价、发布和 SIM。公开行情窗口上限仍为 500 根；请求窗口、原始响应、观察时间、回放可见时间假设、摘要、规则和成本身份可追溯。下一切片尚未选定，以 [STATUS.md](STATUS.md) 的 `next_action=NONE` 为准。

当前代码入口：`OkxHistoricalKlineAdapter` 已在显式 `public-marketdata-manual` profile 与 outbound 开关下读取公开 `history-candles`；`MarketdataIngestionService`、`MarketdataDatasetService`、`MarketdataController` 和 Marketdata/Backtests 页面提供摄取、数据集与回测绑定；`SpotBarIdentity` 冻结策略实际消费的 bar 字节与 digest，`StrategySimRunService` 核对版本、dataset 与 digest。`OkxVenueRuleFactsReader` 可读取公开 SPOT instrument facts，并有观察时间、checksum 与 freshness 校验。现有 `JdbcMarketdataBarRepository` 对公开历史摄取将 `available_at` 写成摄取时间，读取端使用 `COALESCE(available_at, ingested_at)`；故现有记录不能直接证明历史收盘时可见，也不能未经处理就作为逐 bar 交易回放时钟。

实现 owner：Java Marketdata/Dataset 负责公开响应、实际观察时间、来源与可重放输入；Backtest/Strategy 负责显式历史回放可见时间假设、同版身份与因果执行；StrategySimRun 复用既有 canonical Risk / Execution / Order / Trade / Position / Cash / Ledger / PnL。公开 instrument 规则仅作为**本次观察到的规则事实**，不可宣称其在历史窗口内有效；费用与滑点使用冻结且非零的显式假设，不把它们标作交易所实际账户费率。规则过期、缺失、校验失败或与所需最小量/精度冲突时拒绝，不能默认为零或可交易。窗口缺口、未收盘 bar、重复/乱序、迟到修订、版本或 digest 不一致也须 fail closed，并暴露可解释原因。

实现复用既有 dataset 绑定和冻结 consumed bars，并新增 forward-only V53–V54，以不同字段保存**公开响应实际观察时间**与**历史回放可见时间假设及其来源**；未覆盖原始观察时间、重解释旧记录或改写已执行 migration。公开 instrument rules 是本次观察事实，并非历史窗口当时的规则；费用与滑点是显式非零冻结假设，并非真实账户费率。Python research 无运行时职责。

隔离 PostgreSQL 17.7 已迁移并 validate 至 V54；真实公开响应窗口为 2026-09-20T00:00:00Z–2026-09-23T00:00:00Z。两次 Backtest 与两次至首笔 canonical fill 的 SIM smoke 使用相同冻结 dataset、`StrategyVersion` 和成本身份；每次 1 Order、1 Trade、6 Ledger entries，现金与 PnL 的差异由 canonical 账本舍入及估值时点解释。数据质量、身份、规则、预算、stop、重启恢复和 SIM/LIVE 隔离负例通过，独立只读审查 P0/P1=`0/0`。剩余 P2：尚未证明全部 72 根的 SIM 决策稳定；真实账户费率、历史时点真实规则和真实余额仍为 UNKNOWN。公开响应与回放证据见 [TESTING.md](TESTING.md) 的 2026-09-25 候选记录；该历史记录原位保留。

本切片不读取私有 API、真实余额或账户费率，不触发真实 PLACE/CANCEL、LIVE、生产数据库或部署，不建立第二套 Paper 事实源、第二策略、Factor Library、portfolio/scheduling、worker 或通用研究平台。回滚边界为本切片新增的公开数据/回放能力及其独立非生产证据；已执行 migration 不回退，GateZ-1 已接受技术身份保持不变。

候选比较与延期触发：A（公开市场数据可重放 smoke）具有现成消费者，直接关闭合成输入后的真实性缺口，**本轮选中**。B（共享因子）目前只有 `SPOT_SMA_TARGET_V1` 一条已核实可执行策略，Python 样例独立；出现两个真实复用消费者再选。C（组合资金分配）需至少两条真实策略及同账户资本竞争需求；GateZ-1 并发测试本身不满足。D（隔离执行 worker）待主 JVM 执行耦合产生可测可靠性或隔离问题、且有明确部署消费者。E（多策略调度）待第二可用策略、统一调度需求及资本 owner 明确；既有 schedule scan 消费显式订单 trigger。F（OOS/benchmark/trial 增量）待公开可重放输入和明确研究决策问题出现，再选择最小验证；不提前建 Trial Ledger 或统计平台。B–F 均有未来价值，但当前触发条件未满足。

## 不变边界

Java Control Plane 仍是唯一 canonical trading authority；所有交易 mutation 经过既有 Risk / Execution 路径，Order / Trade / Ledger 各只有一个事实源。Python 仅做离线研究与可追溯 artifact，不直接交易。AI / DH 不获得 LIVE 权限。GateZ-1 只用隔离 SIM；`LIVE=DISABLED`、kill switch=`ENGAGED`，不授权真实 PLACE / CANCEL、私有 API、生产部署或资金移动。

## 后续 GateZ 扩展

旧 GateZ 研究中的 **Isolated Execution Scale / Multi-Strategy Scale** 保留为后续候选方向：Java Control Plane 批准 intent，isolated execution worker 只能执行该 intent；Python Offline Research Artifact 不成为交易 authority。未来可在真实消费者出现后考虑多策略调度、portfolio construction、capital / factor allocation、market regime、capacity、correlation、execution-cost feedback 与 strategy decay。

以下均不属于 GateZ-0 或 GateZ-1：完整 Factor Library、Trial Ledger 平台、Random Baseline Engine、DSR/PBO/Holm/FDR 全套统计平台、复杂 Portfolio Optimizer、多策略调度、isolated execution worker、第二交易所 private LIVE、永续/期货/杠杆/期权、链上执行、AI / DH runtime、Agent Wallet、LLM 自动交易、Polymarket execution、MLflow / DVC 与全面微服务拆分。仅当单策略闭环已有可复核的 SIM 消费者，且新增需求有明确输入、owner、边界和验收标准时，才按具体能力重新开启相应扩展；涉及 LIVE 或真实资金仍须另行 current authority 与用户授权。
