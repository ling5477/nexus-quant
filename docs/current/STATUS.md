# Current Status

## 项目定位

NexusQuant 是通用量化交易平台，第一阶段聚焦虚拟币量化交易，后续复用账户、行情、策略、回测、评估、发布、风控、交易、复盘、AI 信号协议等底座扩展到美股和 A 股。

## 当前完成状态

- GateG completed。
- RC1 completed and frozen。
- GateH-PRE completed。
- DOC-CLEAN completed。
- BASELINE-FIX completed。
- GateH-PLAN completed。
- GateH-1-WO completed。
- GateH-2-WO completed。
- GateH-3-WO completed。
- GateH completed。
- GateI-PLAN completed。
- GateI-1-WO completed。
- GateI-2-WO completed.
- GateI-3-WO completed。
- GateI-3-FIX completed。
- GateI-4-WO completed。
- GateI-4-FIX completed。
- GateI completed。
- GateJ-PLAN completed。
- GateJ-1-WO completed。
- GateJ-2-WO completed。
- GateJ-3-WO completed。

## 当前执行状态

- GateJ-3-WO 已完成。
- 后端 `mvn -f backend/pom.xml test` BUILD SUCCESS（含 PaperRunRecoveryServiceTest 9 用例 + PaperRunStabilityCheckServiceTest 10 用例 + PaperRunMonitorRunServiceTest 8 用例）。
- 前端 `npm run build` 通过。
- E2E `npm run test:e2e` 24 passed / 1 skipped（新增 paper-trading-recovery-smoke、paper-trading-stability-check-smoke 通过）。
- Flyway 当前版本 V25（gate j3 paper run recovery stability）。
- Next: GateJ-FREEZE（1h/24h/7d 连续运行验收 + 冻结）。
- AI 尚未开始。AI 最早 GateK 才允许进入信号层。
- GateJ 不是 AI 阶段。GateJ 只做 Paper Trading 稳定运行。

## 当前未完成状态

- 尚未完成虚拟币量化 V1。
- 尚未完成 Paper Trading 稳定运行。
- 尚未进入 AI 自动交易。
- 尚未进入美股/A 股适配。

## 后续路线

```text
DOC-CLEAN / BASELINE-FIX
  ↓
GateH：交易工作台 + 历史行情 + dataset 绑定 completed
  ↓
GateI-PLAN
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

## 本地环境约定

- PostgreSQL 默认端口：`5432`。
- `local` profile 默认连接 `localhost:5432`。
- `docker-compose` 默认映射 `5432:5432`。

## 当前验证基线

- 后端 `mvn -f backend/pom.xml test` 已通过。
- 前端 `npm run build` 已通过。
- E2E `npm run test:e2e` 已通过，结果为 5 passed / 3 skipped。
- GateH-2 后 E2E `npm run test:e2e` 已通过，结果为 9 passed / 3 skipped。
- GateH-3 后 E2E `npm run test:e2e` 已通过，结果为 10 passed / 4 skipped。
- GateH-3 的 backtest dataset binding UI smoke 因当前本地库没有可绑定 backtest config 种子而 skip；后端 controller 测试覆盖绑定 API。
- GateI-1 后端 `mvn -f backend/pom.xml test` 已通过。
- GateI-1 前端 `npm run build` 已通过。
- GateI-1 E2E `npm run test:e2e` 已通过，结果为 13 passed / 3 skipped。
- GateI-2 后端 `mvn -f backend/pom.xml test` 已通过。
- GateI-2 前端 `npm run build` 已通过。
- GateI-2 后端 local profile 启动已通过，Flyway 当前版本为 `20`。
- GateI-2 E2E `npm run test:e2e` 已通过，结果为 17 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路，不影响 GateI-2 主链。
- GateI-3 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests，0 failures）。
- GateI-3 前端 `npm run build` 已通过。
- GateI-3 E2E `npm run test:e2e` 已通过，结果为 18 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateI-3 主链。
- GateI-3 Flyway 当前版本为 `21`。
- GateI-4 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests，0 failures，含 PaperTradingMonitorServiceTest 5 用例）。
- GateI-4 前端 `npm run build` 已通过。
- GateI-4 Flyway 当前版本为 `22`。
- GateI-4-FIX E2E `npm run test:e2e` 已通过，结果为 19 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路。
- Python `pytest`、`mypy`、`ruff` 已通过。
- GateJ-1 后端 `mvn -f backend/pom.xml test` 已通过（35 tests / 0 failures）。
- GateJ-1 前端 `npm run build` 已通过。
- GateJ-1 E2E `npm run test:e2e` 已通过，结果为 20 passed / 1 skipped。
- GateJ-1 Flyway 当前版本为 `23`。
- GateJ-2 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests / 0 failures，含 PaperRunMonitorServiceTest 12 用例）。
- GateJ-2 前端 `npm run build` 已通过。
- GateJ-2 E2E `npm run test:e2e` 已通过，结果为 22 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateJ-2 主链。
- GateJ-2 Flyway 当前版本为 `24`。
- GateJ-3 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，含 PaperRunRecoveryServiceTest 9 用例 + PaperRunStabilityCheckServiceTest 10 用例 + PaperRunMonitorRunServiceTest 8 用例）。
- GateJ-3 前端 `npm run build` 已通过。
- GateJ-3 E2E `npm run test:e2e` 已通过，结果为 24 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateJ-3 主链。
- GateJ-3 Flyway 当前版本为 `25`。

## GateI 当前边界

- GateI 已整体完成。
- GateI-1 实现策略版本与发布记录绑定。
- GateI-2 实现回测配置、评估指标、结果追溯增强。
- GateI-3 实现 SIM/Paper Trading 运行闭环最小版本。
- GateI-4 实现风控回写、资金曲线、持仓曲线、交易复盘、异常停机最小结构。
- AI、AI 信号、AI 自动交易、AI Paper Trading 仍未开始。
- GateJ-1-WO 已完成（Paper run 调度与连续运行）。
- GateJ-2-WO 已完成（运行监控、日报、告警）。
- GateJ-3-WO 已完成（异常恢复、失败重试、稳定性验收结构、HEARTBEAT_LAG/SCHEDULE_FIRE_FAILED 自动告警最小落库）。
- Next: GateJ-FREEZE（1h/24h/7d 连续运行验收 + 冻结）。AI 最早 GateK 才允许进入信号层。
