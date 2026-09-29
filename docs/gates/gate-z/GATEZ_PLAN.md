# GateZ 当前计划

> 历史 current 计划的原文归档。下文的“当前”、STATUS/next action 均指该计划编写及增补时点；冻结前当前 authority 仍以 `docs/current/STATUS.md` 为准。此注仅解释历史身份，不追改当时结论。

本文件定义 GateZ 的当前业务范围；阶段、安全状态与唯一机器 `next_action` 以 [STATUS.md](../../current/STATUS.md) 为准。GateAUDIT 已冻结，历史资格与残余保留在 [canonical archive](../gate-audit/README.md)，不由本计划重评。

## 第一里程碑：单策略现货回测到 SIM 闭环

里程碑 ID：`NQ-GATEZ-1-SPOT-ONE-STRATEGY-BACKTEST-TO-SIM-CLOSED-LOOP`。GateZ-1 已在 PR #24 合并，`dev` merge/exact-head CI 为 `09b7e1cd9c68033b54b75cf361cdda70b584f3a4 / 36111701008 / 9 of 9 SUCCESS`；当前机器状态与下一动作以 [STATUS.md](../../current/STATUS.md) 为准。

代表样例限定为 **OKX SPOT / BTC-USDT**，在隔离环境中走完：

```text
Dataset → frozen StrategyVersion → same-version Backtest → Evaluation → Publish
→ isolated SIM run → canonical Risk / Execution → Order → Trade
→ Position / Cash → Ledger → PnL / replay / trace
```

GateZ-1 必须用实际冻结的策略定义和参数执行回测与 SIM；固定可重放的 bar 内容、来源、可见时点和版本身份。信号只能在可见后形成，成交使用后续可交易时点，避免同一 bar 收盘信号按该收盘价成交。预算经目标敞口转为增量订单，按现金、持仓、在途资金、精度、最小量、最小名义额、费用和滑点计算。10U / 100U / 1000U 各自应给出可执行数量或稳定拒绝原因；收益为正不是验收条件。

隔离 SIM 验收须覆盖同版身份、费用一次性入账、重复触发幂等、资金竞争并发、stop、过期数据与恢复，并用 canonical Order / Trade / Position / Cash / Ledger 事实追溯 PnL。研究侧 Paper 页面可读取或关联这些事实，但不得另造第二套 Paper Order、Trade、Position 或 Ledger authority。既有历史记录须保持可读。可复用现有页面与接口，只补闭环所需入口及可解释结果。

GateZ-1 以隔离 PostgreSQL 和合成 bars 验证算法及 canonical SIM 事实；这些结果不称为交易所历史实测。后续公开历史 bars 的非生产业务 smoke 已单独完成；该历史 SIM 证明不提供真实账户余额、费率或历史时点交易所规则，未知输入不默认为零成本或已合规；后续账户只读资格见下节。设计依据为 [NQ V1 能力盘点](../../archive/gate-z/evidence/NQ-V1-CURRENT-CAPABILITY-ASSESSMENT-AND-NEXT-TASK.md)，该报告是 `76caf387` 的分析快照，不是 machine authority。

## 已接受切片：公开市场数据可重放 smoke

任务 ID：`NQ-GATEZ-PUBLIC-MARKET-REPLAY-SMOKE`，状态=`ACCEPTED / CI_GREEN`；技术 PR #27 merge/exact-head CI=`b253dc19124d26cd8b2aab5685d773adc03e3687 / 36141910575 / 9 of 9 SUCCESS`。在**非生产、隔离 SIM** 中，真实 OKX SPOT / BTC-USDT / 1h 公开历史响应的 72 根连续已收盘 bar 已保存为不可变输入，并驱动同一冻结 `StrategyVersion` 的回测、评价、发布和 SIM。公开行情窗口上限仍为 500 根；请求窗口、原始响应、观察时间、回放可见时间假设、摘要、规则和成本身份可追溯。后续 OKX 账户事实只读观察的当前状态与机器下一动作以 [STATUS.md](../../current/STATUS.md) 为准。

当前代码入口：`OkxHistoricalKlineAdapter` 已在显式 `public-marketdata-manual` profile 与 outbound 开关下读取公开 `history-candles`；`MarketdataIngestionService`、`MarketdataDatasetService`、`MarketdataController` 和 Marketdata/Backtests 页面提供摄取、数据集与回测绑定；`SpotBarIdentity` 冻结策略实际消费的 bar 字节与 digest，`StrategySimRunService` 核对版本、dataset 与 digest。`OkxVenueRuleFactsReader` 可读取公开 SPOT instrument facts，并有观察时间、checksum 与 freshness 校验。现有 `JdbcMarketdataBarRepository` 对公开历史摄取将 `available_at` 写成摄取时间，读取端使用 `COALESCE(available_at, ingested_at)`；故现有记录不能直接证明历史收盘时可见，也不能未经处理就作为逐 bar 交易回放时钟。

实现 owner：Java Marketdata/Dataset 负责公开响应、实际观察时间、来源与可重放输入；Backtest/Strategy 负责显式历史回放可见时间假设、同版身份与因果执行；StrategySimRun 复用既有 canonical Risk / Execution / Order / Trade / Position / Cash / Ledger / PnL。公开 instrument 规则仅作为**本次观察到的规则事实**，不可宣称其在历史窗口内有效；费用与滑点使用冻结且非零的显式假设，不把它们标作交易所实际账户费率。规则过期、缺失、校验失败或与所需最小量/精度冲突时拒绝，不能默认为零或可交易。窗口缺口、未收盘 bar、重复/乱序、迟到修订、版本或 digest 不一致也须 fail closed，并暴露可解释原因。

实现复用既有 dataset 绑定和冻结 consumed bars，并新增 forward-only V53–V54，以不同字段保存**公开响应实际观察时间**与**历史回放可见时间假设及其来源**；未覆盖原始观察时间、重解释旧记录或改写已执行 migration。公开 instrument rules 是本次观察事实，并非历史窗口当时的规则；费用与滑点是显式非零冻结假设，并非真实账户费率。Python research 无运行时职责。

隔离 PostgreSQL 17.7 已迁移并 validate 至 V54；真实公开响应窗口为 2026-09-20T00:00:00Z–2026-09-23T00:00:00Z。两次 Backtest 与两次至首笔 canonical fill 的 SIM smoke 使用相同冻结 dataset、`StrategyVersion` 和成本身份；每次 1 Order、1 Trade、6 Ledger entries，现金与 PnL 的差异由 canonical 账本舍入及估值时点解释。数据质量、身份、规则、预算、stop、重启恢复和 SIM/LIVE 隔离负例通过，独立只读审查 P0/P1=`0/0`。该历史 smoke 当时剩余 P2：尚未证明全部 72 根的 SIM 决策稳定；此 P2 已由下述全窗口回放后续关闭。该历史 smoke 不提供真实账户费率、历史时点真实规则或真实余额；后续当前账户只读观察不追溯改变历史输入。公开响应与回放证据见 [TESTING.md](../../current/TESTING.md) 的 2026-09-25 候选记录；该历史记录原位保留。

本切片不读取私有 API、真实余额或账户费率，不触发真实 PLACE/CANCEL、LIVE、生产数据库或部署，不建立第二套 Paper 事实源、第二策略、Factor Library、portfolio/scheduling、worker 或通用研究平台。回滚边界为本切片新增的公开数据/回放能力及其独立非生产证据；已执行 migration 不回退，GateZ-1 已接受技术身份保持不变。

候选比较与延期触发：A（公开市场数据可重放 smoke）具有现成消费者，直接关闭合成输入后的真实性缺口，**本轮选中**。B（共享因子）目前只有 `SPOT_SMA_TARGET_V1` 一条已核实可执行策略，Python 样例独立；出现两个真实复用消费者再选。C（组合资金分配）需至少两条真实策略及同账户资本竞争需求；GateZ-1 并发测试本身不满足。D（隔离执行 worker）待主 JVM 执行耦合产生可测可靠性或隔离问题、且有明确部署消费者。E（多策略调度）待第二可用策略、统一调度需求及资本 owner 明确；既有 schedule scan 消费显式订单 trigger。F（OOS/benchmark/trial 增量）待公开可重放输入和明确研究决策问题出现，再选择最小验证；不提前建 Trial Ledger 或统计平台。B–F 均有未来价值，但当前触发条件未满足。

## 已接受切片：公开市场 72 根全窗口 SIM 回放

任务 ID：`NQ-GATEZ-PUBLIC-MARKET-FULL-WINDOW-SIM-REPLAY`，状态=`ACCEPTED / CI_GREEN`；技术 PR #48 的 merge/exact-head CI=`1ace052903b6bf70af41f1cf77c72b5157e3c6c8 / 36370231759 / 9 of 9 SUCCESS`。复用先前冻结的 OKX SPOT / BTC-USDT / 1h、2026-09-20T00:00:00Z–2026-09-23T00:00:00Z 公开输入；未重新获取行情。PG16/V55 的两套独立 schema 从头运行 72/72 根，逐根决策及 canonical Order/Trade/Ledger、现金/仓位/PnL 轨迹在业务语义归一化后完全相等；实际每次 22 Order、22 Trade、90 Ledger entries。持久化执行 bar 提供 SIM 订单准入与成交业务时钟；信号只读取已收盘前缀，成交使用后续可交易 bar 的开盘事件。第三套隔离 schema 从头回放 bar 60 变异，验证先前信号与已发生经济事件不变；重复推进不产生第二套经济事实。缺失、重复、乱序、未收盘 bar 和输入身份漂移均 fail closed；`FULL_WINDOW_SIM_DECISION_STABILITY_P2=CLOSED`。

公开 instrument rule 仍为采集时观察的冻结回放假设，不能证明该历史窗口的实际规则；费用与滑点是显式非零实验假设，不是真实账户费率。此验收没有 OKX API、凭证、生产数据库/部署或真实交易动作；无 migration，LIVE=`DISABLED`、kill=`ENGAGED`。唯一机器下一动作以 [STATUS.md](../../current/STATUS.md) 为准。

## 已接受切片：OKX 账户事实只读观察

基础实现任务 ID：`NQ-GATEZ-OKX-ACCOUNT-FACTS-READONLY-QUALIFICATION-IMPLEMENTATION`，已通过 PR #30 合并，technical merge/exact-head CI=`5a8cfb7e10b192efea1b804d9e6b6c0e1a637bd3 / 36158986349 / 9 of 9 SUCCESS`；独立 SECURITY + CORRECTNESS 审查 P0/P1/P2=`0/0/0`。后续 coverage unblock 已通过 PR #40 合并，technical merge/exact-head CI=`e9df23f079f0aaec1eff5b38269f4b18c7e9dae3 / 36295009039 / 9 of 9 SUCCESS`。实现复用 Java Control Plane 的 OKX private GET transport、JIT credential executor、权限/IP 观察，提供显式人工调用的非持久化 `AccountFactsSnapshot`。固定 GET 覆盖账户配置、全币种余额、BTC-USDT SPOT 私有账户费率、全 SPOT 未完成订单，以及模式 2 的完整 typed positions；公开 GET 读取服务器时间及当前 BTC-USDT SPOT instrument rule，不写 catalog。模式 1 不读取 positions，模式 3/4 保持未资格化；模式 2 的非零非现货仓位保留为外部上下文，不能与 BTC 现货数量相加，也不直接构成托管 DIVERGED。当前托管对账仅比较同账户、LIVE、OKX 且已证明身份的活跃订单；全账户余额与 NQ managed ledger projection 不同义，不能据此判 MATCH 或 DIVERGED。返回前复查 freshness 与 kill identity。默认启动不读取 credential 或发出 private/public rule 请求；不新增 Order、Trade、Ledger 写入或 migration。字段兼容性及 freshness 详见 [API.md](../../current/API.md)。

2026-09-27 的新只读资格验证已通过：`REAL_ACCOUNT_READONLY_FACTS_QUALIFIED`。coverage unblock 技术 PR #40 的合并提交/精确 HEAD CI 为 `e9df23f079f0aaec1eff5b38269f4b18c7e9dae3 / 36295009039 / 9 of 9 SUCCESS`；canonical release=`nq-e9df23f079f0-5a401cd983263e6d` 已普通 install/activate，PG16/V54 validate 通过，migration 执行数为 0。复用现有 owner 登录来源，密码、角色和 owner 链未变；permission probe 为 `SUCCEEDED / TRADE / WITHDRAW=false / IP PASSED`。新 snapshot 的模式 2、BTC/USDT 与全部返回余额、positions、挂单、费率、交易所时间、当前公开规则和偏差均为 OBSERVED；positions=`NO_ACTIVE_POSITION / 0`，open orders=`0`，divergence=`DIVERGED`，完整分类不要求 MATCH。观察时间为 `2026-09-27T05:38:23.820031102Z`；私有事实 60s、公开规则 24h，验收时全部新鲜，历史快照不能作为后续实时输入。成功轮 10 次固定 GET、其他 endpoint 0、交易 mutation 0，五类 canonical 交易事实数量及内容不变。runtime 已停止，Java/MainPID=0，LIVE=DISABLED，kill=ENGAGED。首轮采集器阶段计数错误导致的失败原位保留，修正后以独立 run 身份重新验证；不追认旧失败为成功。详情见[模式 2 与公开规则只读资格证据](../../archive/gate-z/evidence/gate-z/OKX_ACCOUNT_FACTS_READONLY_COVERAGE.md)。

账户只读对账模型修复已由技术 PR #46 合并，`dev=dc0b01e5653d6718a00b0d667bc2440c47b49b55 / exact-head CI 36326961228 / 9 of 9 SUCCESS`。当前 `AccountFactsSnapshot` 将访问状态、外部账户上下文与 NQ 托管状态对账分开；余额、额外资产、BTC dust、未归属外部订单、非现货仓位、费用和公开规则不污染托管聚合。托管订单须证明账户、LIVE、OKX、活跃状态与 client/exchange 订单身份；数量单位或状态不明时为 UNKNOWN，无合格维度时为 NOT_APPLICABLE。原 `REAL_ACCOUNT_READONLY_FACTS_QUALIFIED` 与历史只读观察结果保持有效且不被追认，当前无新 OKX 调用或生产部署。下一唯一动作以 [STATUS.md](../../current/STATUS.md) 的机器 `next_action` 为准。

## 不变边界

Java Control Plane 仍是唯一 canonical trading authority；所有交易 mutation 经过既有 Risk / Execution 路径，Order / Trade / Ledger 各只有一个事实源。Python 仅做离线研究与可追溯 artifact，不直接交易。AI / DH 不获得 LIVE 权限。GateZ-1 与公开行情 smoke 只用隔离 SIM，不授权私有 API。此前账户事实资格仅覆盖当次服务器现有 credential 的只读 private GET；本轮模型修复没有新的 private/public 请求或生产部署，后续真实观察与 canonical 生产部署均需分别获得当前授权。`LIVE=DISABLED`、kill switch=`ENGAGED`，真实 PLACE / CANCEL、账户 mutation 与资金移动仍被禁止。

## 后续 GateZ 扩展

旧 GateZ 研究中的 **Isolated Execution Scale / Multi-Strategy Scale** 保留为后续候选方向：Java Control Plane 批准 intent，isolated execution worker 只能执行该 intent；Python Offline Research Artifact 不成为交易 authority。未来可在真实消费者出现后考虑多策略调度、portfolio construction、capital / factor allocation、market regime、capacity、correlation、execution-cost feedback 与 strategy decay。

以下均不属于 GateZ-0 或 GateZ-1：完整 Factor Library、Trial Ledger 平台、Random Baseline Engine、DSR/PBO/Holm/FDR 全套统计平台、复杂 Portfolio Optimizer、多策略调度、isolated execution worker、第二交易所 private LIVE、永续/期货/杠杆/期权、链上执行、AI / DH runtime、Agent Wallet、LLM 自动交易、Polymarket execution、MLflow / DVC 与全面微服务拆分。仅当单策略闭环已有可复核的 SIM 消费者，且新增需求有明确输入、owner、边界和验收标准时，才按具体能力重新开启相应扩展；涉及 LIVE 或真实资金仍须另行 current authority 与用户授权。
