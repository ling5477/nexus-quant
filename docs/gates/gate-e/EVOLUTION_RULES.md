# GateE EVOLUTION_RULES

> GateE 演化规则。目标：在不破坏 GateD 冻结成果的前提下，把策略层真正接进来，而不是顺手把系统又改成一锅乱炖。

---

## 1. 基本原则

1. 先对齐当前 Gate 边界，再动代码
2. 先补文档，再改实现，再回填 `WORK.md`
3. 以最小可验证修改集推进，不做顺手大改
4. 契约 / schema / 状态机改动必须同步更新 GateE 文档
5. GateD 的订单状态机、恢复规则、执行闭环继续视为冻结基线

---

## 2. 允许主改的区域

GateE 允许主改以下模块：
- `nq-core`
- `nq-scheduler`
- `nq-contracts`
- `nq-infra`
- `nq-api`
- `nq-app`
- `nq-adapter-api`
- `nq-adapter-binance`
- `nq-adapter-okx`
- `nq-ledger`

允许同步修改以下文档：
- `AGENTS.md`
- `README.md`
- `docs/current/*`
- `docs/gates/gate-e/*`
- `docs/ARCHITECTURE.md`
- `docs/MODULES.md`
- `docs/ROADMAP.md`
- `docs/README.md`

---

## 3. 不允许的演化方向

- 不允许把 `strategyId` 和 `strategyRunId` 继续混用
- 不允许 scheduler 直接写订单状态、账本或持仓
- 不允许把 `GateBDemoStrategyRunner` 偷偷演进成正式 GateE 主链
- 不允许为凑文档完整度创造空 migration
- 不允许在 `nq-core / nq-scheduler` 写交易所方言分支
- 不允许把 GateE 扩写成回测 / 因子 / 研究平台

---

## 4. Breaking Change 规则

### 4.1 公共契约
以下内容视为高风险 breaking change：
- `strategyId / strategyRunId` 字段含义
- 策略状态枚举
- 运行状态枚举
- 调度触发命令语义
- 执行血缘字段命名

处理规则：
- 必须先更新 `CONTRACTS.md` / `STATE_MACHINE.md`
- 必须在 PR 描述中说明兼容策略
- 必须补回归测试

### 4.2 数据库迁移
- 禁止修改已发布 Flyway 脚本内容
- 新变更一律新增迁移文件
- 涉及策略定义 / 调度计划 / 运行状态字段时，必须同步更新 `DB_SCHEMA.md`

---

## 5. 文档同步规则

当改动以下内容时，必须同步更新对应文档：

- 策略接入契约：`CONTRACTS.md`
- 策略 / 运行状态：`STATE_MACHINE.md`
- 数据模型：`DB_SCHEMA.md`
- 调度流程：`ARCHITECTURE.md`、`MODULES.md`
- 验收范围：`TEST_CASES.md`
- 阶段边界：`docs/current/README.md`、`docs/current/GATE_CHECKLIST.md`

---

## 6. 退出条件

当出现以下情况时，必须停止当前修改并先回到文档 / 设计收口：

- 一个 PR 同时改 5 个以上主模块且没有 split plan
- `strategyId / strategyRunId` 的语义在不同模块里继续对不上
- scheduler 开始直接碰订单状态或账本
- 为了赶进度临时把历史 demo runner 当正式调度器
- 新增表结构但没有更新 GateE 文档与验收口径
