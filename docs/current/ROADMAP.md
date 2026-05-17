# Roadmap

## 总路线

```text
DOC-CLEAN / BASELINE-FIX
  ↓
GateH-PLAN
  ↓
GateH：交易工作台 + 历史行情数据接入
  ↓
GateI：虚拟币量化 V1 完整闭环
  ↓
GateJ：Paper Trading 稳定运行
  ↓
GateK：AI 信号接入
  ↓
GateL：AI Paper Trading
  ↓
GateM：AI 小资金 LIVE
  ↓
GateN：美股适配
  ↓
GateO：A 股适配
```

## 当前阶段

- DOC-CLEAN completed。
- BASELINE-FIX completed。
- 当前阶段是 `GateH-PLAN`。
- GateH 尚未开工，不能写成 completed。

## 路线原则

- 先把虚拟币做成完整 V1。
- GateH 先完成交易工作台与历史行情数据接入。
- GateI 再完成虚拟币量化 V1 完整闭环。
- GateJ 稳定 Paper Trading。
- GateK 才允许 AI 进入信号层。
- GateL 进入 AI Paper Trading。
- GateM 才允许 AI 小资金 LIVE。
- 美股/A 股复用虚拟币 V1 沉淀的通用底座。

## 当前边界

- 本轮只做 GateH 规划文档。
- 不开发 GateH 功能代码。
- 不新增 DB migration。
- 不新增 API 实现。
- 不新增前端页面实现。
- 不接入 AI 自动交易。
