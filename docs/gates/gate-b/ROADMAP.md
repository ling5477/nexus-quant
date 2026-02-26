# Gate B ROADMAP（从 GateB 到后续 Gates）

---

## 1. Gate B 完成后，下一步（Gate C）
### Gate C 目标：接入真实交易所（OKX/Binance）
- 用真实 adapter 替换 paper adapter（或并行支持）
- 处理真实回报：订单回执、成交回报、撤单回报
- 处理网络异常与重试（仍保持幂等与状态机）

Gate C 输入：
- Gate B 的闭环与契约稳定
- 状态机/账本/风控可复用

Gate C 输出：
- 实盘/仿真盘可切换
- 多交易所接入的统一行为

---

## 2. Gate D（Research / Backtest / Factor）
- 引入回测框架与研究管线（包括 AlphaCFG 类因子发现）
- 研究侧与线上执行侧隔离（产物发布制）

---

## 3. Gate E（合约/杠杆/期货）
- 扩展订单/持仓/账本模型
- 风控规则升级（保证金、强平、资金费率等）

---

## 4. 版本推进建议（NexusQuant v1.x）
- v1.0：GateA（骨架）✅
- v1.1：GateB（模拟盘闭环）
- v1.2：GateC（真实交易所接入）
- v1.3：GateD（回测/研究/因子）
- v1.4：GateE（合约/杠杆）