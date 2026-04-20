# Current Stage（当前阶段入口）

当前阶段：**RC1 completed and frozen；GateH-PRE completed；下一步 = `GateH-PLAN`。**

`docs/current/` 是当前事实入口。`docs/gates/gate-*/` 与 `docs/archive/` 只保留历史卷宗，不代表当前实现入口。

---

## 1. 当前结论

- `RC1-0 / 1 / 2 / 3 / 4 / 5 / 6 / 7` 已全部完成，RC1 已整体冻结。
- `GateH-PRE` 已完成，前置治理批次不再是待办状态。
- `GateH = paused / not started`，尚未启动开发。
- 下一步不是直接开发 GateH，而是进入 `GateH-PLAN`。

---

## 2. 当前代码事实

- `trading` anti-corruption 已成立，core 不再直接把 adapter contract 当作 trading application 主语义。
- `marketdata / instrument / symbol` owner 已从 backtest 附属语义收口到正式主链。
- `nq-api` research 编排层已移出，API 模块只承担 controller / DTO / web adapter。
- `nq-app` 已收口为启动、profile 与 Bean wiring 为主的 composition root。
- `nq-infra` namespace 已统一到 domain-first infra 包规范。
- 前端正式交易入口是 `/trading`；`/trade-validation` 只保留历史 alias 重定向。
- Python 子工程是离线研究工具链，`pytest` / `mypy` / `ruff` / CLI smoke 已通过。

---

## 3. 当前系统正式能力

- 表结构主模型收口。
- DB-backed auth。
- 用户 / 账户 / 凭证 / 默认账户上下文主链。
- 账户与凭证写侧闭环。
- 凭证 active 版本切换与结构性校验。
- JDBC 实现与模块边界收口。
- 后端业务域包结构收口。
- `marketdata` 最小 ingest/query 真闭环。
- `research -> backtest -> eval` 最小 DB-backed happy path。
- 前端账户上下文与交易工作台正式入口。
- Python 离线研究 CLI 与基础质量门禁。

---

## 4. 当前入口文件

- RC1 冻结说明：`docs/current/REFACTOR_BATCH_RC1.md`
- RC1 最终 checklist：`docs/current/RC1_CHECKLIST.md`
- 当前模块基线：`docs/current/MODULES.md`
- 当前冻结检查表：`docs/current/GATE_CHECKLIST.md`
- GateH-PRE 文档：
  - `docs/current/GATEH_PRE_1_TRADING_ANTI_CORRUPTION.md`
  - `docs/current/GATEH_PRE_2_MARKETDATA_INSTRUMENTS.md`
  - `docs/current/GATEH_PRE_3_FRONTEND_IA.md`
- GateH 暂停卷宗：`docs/gates/gate-h/README.md`

---

## 5. 历史文档规则

- `docs/archive/rc1/RC1_7_PACKAGE_MAPPING.md` 是历史迁移映射，不代表当前包事实。
- `docs/gates/gate-g/*` 是 GateG 历史冻结卷宗，只读参考。
- `docs/gates/gate-h/*` 是 GateH paused / not started 的历史规划草稿，不可直接作为开工依据。

---

## 6. 下一步

下一步进入：**GateH-PLAN**。

GateH-PLAN 只能基于当前冻结事实规划，不得回退 RC1 / GateH-PRE 已完成的结构边界。
