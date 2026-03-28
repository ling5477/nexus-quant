# GateH PR_SPLIT_PLAN

原则：

- 一个 PR 只解决一类工作流问题
- 先消费已有正式接口，再评估是否需要新增后端能力
- 先收口用户可执行路径，再扩细节体验
- 不回改 GateG 已冻结主链
- 文档、E2E 与实现同步推进，不把回归债务滞后到最后

---

## 1. 执行顺序总览

- [x] GateH-PLAN：规划、范围冻结、优先级排序
- [ ] GateH-1：策略 / 调度 / 运行工作流增强
- [ ] GateH-2：回测运行工作流与深详情
- [ ] GateH-3：trade-validation 多结果工作区
- [ ] GateH-4：E2E 与测试数据治理

---

## 2. GateH-1：策略 / 调度 / 运行工作流增强

目标：

- 补 `strategies` 的 create / trigger
- 补 `schedules` 的 create / scan-once
- 补 `runs` 与前述动作的联动、刷新与跳转

不做：

- 不虚构 `runs` 独立写动作 API
- 不扩成图形化编排器
- 不要求新增全局调度列表接口

成功标准：

- 用户能从控制台完成 create / trigger / scan-once
- `runs` 能展示对应动作后生成的最新结果
- 文档、类型、hooks 与至少一条 E2E 同步补齐

---

## 3. GateH-2：回测运行工作流与深详情

目标：

- 建立 `backtest-runs` 的 create / start / detail
- 接入 `sim-orders / sim-trades / sim-positions / pnl-snapshots`
- 收口与 `evaluations / publishes` 的动作衔接

不做：

- 不做 BI 报表平台
- 不做 research/backtest config 的大而全编辑器
- 不新增数据库结构

成功标准：

- 用户能完成回测运行 create -> start -> detail -> evaluate / publish
- `sim_*` 与结果详情能在前端完整查看
- 至少一条覆盖 run 主链的 E2E 可稳定执行

---

## 4. GateH-3：trade-validation 多结果工作区

目标：

- 在当前 `/api/trading/**` 口径下整理查询与动作结果
- 保留最近查询上下文、Trace 关联与多块结果并行查看
- 明确哪些能力是前端重组即可完成，哪些能力需要后端新增查询面

不做：

- 不做新的交易运维大盘
- 不做全局订单检索中心
- 不把 `OperationTriggerResponse` 私自扩成未定义协议

成功标准：

- 页面支持更系统的多结果工作区
- 查询与动作结果不再互相覆盖造成上下文丢失
- 若发现后端缺口，形成单独清单并停止越界实现

---

## 5. GateH-4：E2E 与测试数据治理

目标：

- 为 GateH-1 / GateH-2 / GateH-3 补 Playwright 用例
- 明确 seed / 预置数据与环境变量方案
- 降低关键链路对 skip 的依赖

不做：

- 不做全量视觉回归
- 不做大规模跨浏览器矩阵
- 不把测试数据治理演变为数据库结构重构

成功标准：

- GateH 新链路均有对应 E2E
- 形成稳定的测试数据准备步骤
- skip 数量减少，且保留项均有清晰原因
