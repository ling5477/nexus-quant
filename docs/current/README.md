# Current Stage（当前阶段入口）

当前阶段：**RC1 冻结完成基线**

当前状态：**RC1 已整体完成并冻结；`GateH = paused`；下一步 = `GateH-PLAN`。**

---

## 1. 当前结论

- `RC1-0 / 1 / 2 / 3 / 4 / 5 / 6 / 7` 已全部完成。
- RC1 不再是进行中批次。
- 当前主线不再是 RC1 执行，而是基于 RC1 冻结基线进入后续规划。
- GateH 继续暂停，尚未启动开发。

---

## 2. 当前系统已具备的正式能力

- 表结构主模型收口。
- DB-backed auth。
- 用户 / 账户 / 凭证 / 默认账户上下文主链。
- 账户与凭证写侧闭环。
- 凭证 active 版本切换与结构性校验。
- JDBC 实现与模块边界收口。
- 后端业务域包结构收口。
- `marketdata` 最小 ingest/query 真闭环。
- `research -> backtest -> eval` 最小 DB-backed happy path。
- `RC1-6` 验证闭环。

---

## 3. RC1 未纳入范围

- GateH 功能开发未启动。
- publish 深化不在 RC1 必达范围。
- 多交易所历史行情平台化不在 RC1 必达范围。
- research/front-end 深化不在 RC1 必达范围。
- 管理员跨用户账户 / 凭证运营能力未在 RC1 内展开。
- 凭证真实外网探活未纳入 RC1，当前为结构性校验。
- 更复杂的前端运营台与批量操作未纳入 RC1。

---

## 4. 当前入口

- RC1 冻结说明：`docs/current/REFACTOR_BATCH_RC1.md`
- RC1 最终 checklist：`docs/current/RC1_CHECKLIST.md`
- 当前模块基线：`docs/current/MODULES.md`
- 当前冻结检查表：`docs/current/GATE_CHECKLIST.md`
- GateH 暂停卷宗：`docs/gates/gate-h/README.md`

---

## 5. 下一步

下一步不是 GateH 开发，而是：**GateH-PLAN**。
