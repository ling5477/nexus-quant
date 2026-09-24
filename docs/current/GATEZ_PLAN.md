# GateZ 当前计划

本文件定义 GateZ 的当前业务范围；阶段、安全状态与唯一机器 `next_action` 以 [STATUS.md](STATUS.md) 为准。GateAUDIT 已冻结，历史资格与残余保留在 [canonical archive](../gates/gate-audit/README.md)，不由本计划重评。

## 第一里程碑：单策略现货回测到 SIM 闭环

里程碑 ID：`NQ-GATEZ-1-SPOT-ONE-STRATEGY-BACKTEST-TO-SIM-CLOSED-LOOP`。当前唯一实现入口的机器 action 为 `NQ-GATEZ-1-SPOT-ONE-STRATEGY-BACKTEST-TO-SIM-CLOSED-LOOP-IMPLEMENTATION`。GateZ-1 尚未实施或验收。

代表样例限定为 **OKX SPOT / BTC-USDT**，在隔离环境中走完：

```text
Dataset → frozen StrategyVersion → same-version Backtest → Evaluation → Publish
→ isolated SIM run → canonical Risk / Execution → Order → Trade
→ Position / Cash → Ledger → PnL / replay / trace
```

GateZ-1 必须用实际冻结的策略定义和参数执行回测与 SIM；固定可重放的 bar 内容、来源、可见时点和版本身份。信号只能在可见后形成，成交使用后续可交易时点，避免同一 bar 收盘信号按该收盘价成交。预算经目标敞口转为增量订单，按现金、持仓、在途资金、精度、最小量、最小名义额、费用和滑点计算。10U / 100U / 1000U 各自应给出可执行数量或稳定拒绝原因；收益为正不是验收条件。

隔离 SIM 验收须覆盖同版身份、费用一次性入账、重复触发幂等、资金竞争并发、stop、过期数据与恢复，并用 canonical Order / Trade / Position / Cash / Ledger 事实追溯 PnL。研究侧 Paper 页面可读取或关联这些事实，但不得另造第二套 Paper Order、Trade、Position 或 Ledger authority。既有历史记录须保持可读。可复用现有页面与接口，只补闭环所需入口及可解释结果。

GateZ-1 的实施和验证是下一轮独立任务。若没有合格公开历史 bars，先以明确标识的合成输入证明算法，再在非生产环境使用公开数据做业务 smoke；不能把合成结果称为交易所历史实测。真实余额、费率、规则或可用数据未知时，应明确输出未知或不可交易，不默认为零成本或已合规。当前设计依据为 [NQ V1 能力盘点](evidence/NQ-V1-CURRENT-CAPABILITY-ASSESSMENT-AND-NEXT-TASK.md)，该报告是 `76caf387` 的分析快照，不是 machine authority。

## 不变边界

Java Control Plane 仍是唯一 canonical trading authority；所有交易 mutation 经过既有 Risk / Execution 路径，Order / Trade / Ledger 各只有一个事实源。Python 仅做离线研究与可追溯 artifact，不直接交易。AI / DH 不获得 LIVE 权限。GateZ-1 只用隔离 SIM；`LIVE=DISABLED`、kill switch=`ENGAGED`，不授权真实 PLACE / CANCEL、私有 API、生产部署或资金移动。

## 后续 GateZ 扩展

旧 GateZ 研究中的 **Isolated Execution Scale / Multi-Strategy Scale** 保留为后续候选方向：Java Control Plane 批准 intent，isolated execution worker 只能执行该 intent；Python Offline Research Artifact 不成为交易 authority。未来可在真实消费者出现后考虑多策略调度、portfolio construction、capital / factor allocation、market regime、capacity、correlation、execution-cost feedback 与 strategy decay。

以下均不属于 GateZ-0 或 GateZ-1：完整 Factor Library、Trial Ledger 平台、Random Baseline Engine、DSR/PBO/Holm/FDR 全套统计平台、复杂 Portfolio Optimizer、多策略调度、isolated execution worker、第二交易所 private LIVE、永续/期货/杠杆/期权、链上执行、AI / DH runtime、Agent Wallet、LLM 自动交易、Polymarket execution、MLflow / DVC 与全面微服务拆分。仅当单策略闭环已有可复核的 SIM 消费者，且新增需求有明确输入、owner、边界和验收标准时，才按具体能力重新开启相应扩展；涉及 LIVE 或真实资金仍须另行 current authority 与用户授权。
