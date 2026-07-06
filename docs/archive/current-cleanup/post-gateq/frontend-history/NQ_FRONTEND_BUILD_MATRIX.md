# NQ Console 前端施工排期矩阵

> 任务分类: `SPREADSHEET_MATRIX` (primary) + `PRODUCT_DESIGN`
> 用途: 把研究报告的"目标态蓝图"切成按后端就绪度施工的批次。每个页面给 v1 最小切口。
> Gate 基线: GateJ completed → GateK-PLAN;**AI 未开始;DH integration 未开始、未连 NQ**。
> 原则: 不为不存在的数据做成熟前端。AI/Agent/DH 页面本阶段只做设计规范 + 菜单占位,**不做 mock 实现**。

---

## 1. 四类页面原型(施工骨架,先于页面锁死)

| 原型 | 结构 | 适用 |
|---|---|---|
| Collection | 过滤器 + 表格 + split detail | Orders / Runs / Alerts / Publishes |
| Detail | 页头摘要 + tabs + 事实区 | Strategy / Backtest / Account Detail |
| Monitor | 顶栏指标 + 中区图表 + 侧边事件 + 底部日志 | Dashboard / Paper Console / Risk / Market Data |
| Workbench | 稳定多分栏(预定义、可保存,非自由拖拽) | Trading Workbench / AI Research |

---

## 2. 后端就绪度图例

```text
READY    后端已有结构化数据/接口,可直接接真数据
PARTIAL  部分接口存在或需小幅补齐,可先接已有、留位未就绪部分
NOT-YET  后端未实现(skeleton/mock 级),只能做规范+占位,不接数据
```

> 说明:NQ 后端就绪度按"GateJ 完成"基线推断,**请与 NQ 当前 Gate 文档逐项核对**;DH 侧依据 decision-hub 仓库现状(MockProvider + Run create 骨架 + golden runner),Agent Runtime 未建。

施工状态(`施工状态` 列):

```text
READY_NOW   当前可施工(本阶段就能落代码)
PLANNED     已排期,待前置完成(主要前置是 B0 与批次顺序)
PARTIAL     部分可施工(接已有数据,未就绪部分留位)
BLOCKED     后端/数据未就绪,禁止 mock 成熟页(只做规范+占位)
```

> 注意:`施工状态` 表示"是否可以开始施工",**不表示"已完成"**。当前阶段 GateJ completed → GateK-PLAN,除 B0 外都不是"已开始"。

---

## 3. 施工矩阵

| 批次 | 页面 | 原型 | 后端就绪度 | v1 最小切口 | 施工状态 |
|---|---|---|---|---|---|
| **B0** | Design Tokens v2 / App Shell / Status System | — | READY | token + 固定壳 + StatusTag/EnvBadge/RiskBanner/DataFreshness 四件 | READY_NOW |
| **B0** | 登录页 + 异常页(403/无权限/错误/空初始化) | — | READY | 居中双区登录;移除 Gate/DEV/PAPER 主视觉降为 footer;异常页给原因+request id+入口 | READY_NOW |
| **B1** | Dashboard / 首页 | Monitor | PARTIAL | 总控态:资产/环境/订单数/策略运行数/风险/告警/数据同步/系统健康 + 权益趋势 + "需立即处理"单列;缺的指标留 DataFreshness 占位 | PLANNED |
| **B2** | Paper Trading Console | Monitor | PARTIAL | 顶栏运行指标 + 权益/PnL 图 + 事件流 + 底部 Orders/Positions tabs;Live 动作样式区分 | PLANNED |
| **B3** | Backtest Detail | Detail | PARTIAL→READY | detail-first:策略版本/数据集快照/区间/资金/费率 + 权益/回撤/滚动收益图 + 成交明细 + 参数快照 | PLANNED |
| **B3** | Backtests 列表 | Collection | READY | 过滤 + 表格 + split detail | PLANNED |
| **B4** | Strategy Detail | Detail | PARTIAL | 页头标识 + tabs(基础/版本/参数/回测/Paper/日志/调度/风险/发布);版本历史一级,支持 diff | PLANNED |
| **B4** | Strategies 列表 | Collection | READY | 主键=策略+版本;列含版本/运行态/参数摘要/最近回测/评分/发布态 | PLANNED |
| **B4** | Schedules / 调度 | Collection | PARTIAL | 运维视角:cron/下次运行/上次结果/重试/heartbeat/启停/阻断原因 | PLANNED |
| **B4** | Runs / 运行记录 | Collection | PARTIAL | 列表+详情追踪(business trace viewer):输入/输出/日志流/异常/关联订单 | PLANNED |
| **B5** | Orders / 订单 | Collection | READY | table-first;固定列 状态标签+失败原因+最后更新+关联实体;区分 open/terminal 状态 | PLANNED |
| **B5** | Positions / 持仓 | Detail/Monitor | READY | 全局敞口摘要 + 分账户/策略/标的持仓表 + 风险热区;实现/未实现盈亏用涨跌色 | PLANNED |
| **B5** | Accounts / 账户 | Detail | READY | detail-first:类型/环境/状态/对账 + 余额/冻结/可用/保证金区 | PLANNED |
| **B6** | Risk Center / 风控 | Monitor | PARTIAL | 全局风险状态 + 阻断横幅 + 按账户/策略/持仓/订单分区 + 生效规则 + 命中/恢复时间轴 | PLANNED |
| **B6** | Alerts / 告警 | Collection | PARTIAL | 分级/聚合/确认/指派/处理记录;按严重度+新鲜度排序 | PLANNED |
| **B6** | Market Data / 市场数据 | Monitor | PARTIAL | 监控页:覆盖率/缺口/异常 candle/同步时间 + ingestion job + 完整性表 | PLANNED |
| **B6** | Reports / 日报复盘 | Detail/Workbench | PARTIAL | report center:列表 + notebook 阅读区(图表+事实+人工批注) | PLANNED |
| **B6** | Evaluations / 评估 | Detail | PARTIAL | 门禁页:评分矩阵(策略/风险/稳定性/数据质量/回测可信度)可下钻到规则+证据 | PLANNED |
| **B6** | Publishes / 发布 | Collection | PARTIAL | deployment center:版本/目标环境/审批/风控检查/门禁/回滚;详情显示"为何不能进 LIVE" | PLANNED |
| **B7** | Trading Workbench | Workbench | PARTIAL | watchlist + K 线主图 + 下单区(只读区/操作区视觉分层)+ Orders/Positions/Audit tabs | PARTIAL |
| **B7** | Settings | Collection | READY | 按 section 拆;API key 只显前后缀/作用域/最后使用,绝不显明文;**唯一允许有限用 ProComponents** | PLANNED |
| **B7** | Trade Validation | Detail/侧栏 | PARTIAL | 交易前/策略/风险/账户/交易所参数校验;失败原因提交前前置 | PLANNED |
| **B8** | AI Overview | Monitor | **NOT-YET** | **仅规范 + 菜单占位**,不接数据 | BLOCKED |
| **B8** | AI Research Assistant | Workbench | **NOT-YET** | 仅规范 + 占位 | BLOCKED |
| **B8** | AI Strategy / Risk / Backtest Review | Detail | **NOT-YET** | 仅规范 + 占位 | BLOCKED |
| **B8** | AI Decision Log | Collection | **NOT-YET** | 仅规范(table-first 列定义)+ 占位 | BLOCKED |
| **B8** | Agent Runs / Agent Workflow | Collection/Workbench | **NOT-YET** | 仅规范(trace viewer / DAG 形态)+ 占位 | BLOCKED |
| **B8** | Prompt Management / Model Provider / AI Cost & Usage | Collection/Monitor | **NOT-YET** | 仅规范 + 占位 | BLOCKED |
| **B8** | DH Integration 页 | Monitor | **NOT-YET** | 仅"边界说明 + 连接状态"占位;页头长期声明 DH 只读、不可下单/绕风控/读凭证/操作 LIVE | BLOCKED |

---

## 4. 批次推进顺序与门槛

```text
B0  基础系统      ← 立即,先于一切页面;登录页随壳重做
B1  Dashboard
B2  Paper Console
B3  Backtest      ← 回测可视化是 NQ 专业度的第一张名片
B4  Strategy 域(Detail/列表/调度/Runs)
B5  Orders/Positions/Accounts
B6  Risk/Alerts/Market Data/Reports/Evaluations/Publishes
B7  Workbench 强化 / Settings / Trade Validation
B8  AI/Agent/DH    ← 门槛:DH Agent Runtime(DH-REFIT-2)跑通、有结构化数据可渲染后才开;此前只做规范+占位,严禁 mock 实现
```

**B8 解锁条件(硬门槛):**

```text
[ ] DH 侧 ResearchRun/AgentTask/StrategyCandidate/JudgeDecision 等结构化产物已落库
[ ] AI Decision Log / Agent Run 有真实 trace 与成本/延迟字段
[ ] DH↔NQ 集成边界与契约冻结(读写边界、HMAC/nonce/replay/审计)
在以上未满足前,B8 页面只允许:设计规范 + 菜单占位 + 边界声明文案。
```

---

## 5. 边界声明(贯穿全程)

```text
DH 不建独立完整前端;AI/DH 页面 = NQ Console 消费 DH API。
AI 是结构化研究/解释/风控/审计辅助,不做聊天玩具,不直接驱动交易。
所有最终建议经 JudgeDecision;所有关键对象带 traceId。
LIVE 操作:红色 + 二次确认(敏感动作可 typed confirm),样式与 Paper 明显不同。
失败/阻断/降级有可读原因区,不靠 toast 一闪。
```
