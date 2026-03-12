# GateD EVOLUTION_RULES

> GateD 演化规则。  
> 目标：在不破坏既有 GateA/B/C 冻结成果的前提下，允许 GateD 做必要收敛；禁止“顺手大改”把边界改成一锅粥。

---

## 1. 基本原则

1. 先对齐当前 Gate 边界，再动代码
2. 先补文档，再改实现，再回填 `WORK.md`
3. 以最小可验证修改集推进，不做大面积顺手重构
4. 公共契约、状态机、Flyway、恢复逻辑一旦改动，必须同步更新文档
5. 历史 Gate 文档只读参考；当前实现以 `docs/current/*` 为准

---

## 2. 允许修改的区域

GateD 允许主改以下模块：

- `nq-core`
- `nq-risk`
- `nq-adapter-api`
- `nq-adapter-okx`
- `nq-adapter-binance`
- `nq-scheduler`
- `nq-ledger`
- `nq-app`
- `nq-infra`
- `nq-observability`
- `nq-api`

允许增量修改以下文档：

- `AGENTS.md`
- `README.md`
- `docs/current/*`
- `docs/gates/gate-d/*`
- `docs/ROADMAP.md`
- `docs/MODULES.md`
- `docs/ARCHITECTURE.md`
- `docs/gates/gate-b/ROADMAP.md`
- `docs/gates/gate-c/ROADMAP.md`

---

## 3. 非主改区域

以下目录与模块不作为 GateD 主改造对象；若确需修改，只允许最小兼容性修正：

- `nq-auth`
- `nq-security`
- `nq-gateway`
- `frontend/`
- `research/`
- `infra/` 中与 GateD 无关的生产基建目录

---

## 4. Breaking Change 规则

### 4.1 公共契约
以下内容视为高风险 breaking change：

- adapter 接口签名
- 订单状态枚举语义
- 事件类型语义
- 核心 DTO 字段语义
- Flyway 已发布字段含义

处理规则：

- 必须先更新 `CONTRACTS.md` / `STATE_MACHINE.md` / `DB_SCHEMA.md`
- 必须在 PR 描述中说明变更面与兼容策略
- 必须补回归测试

### 4.2 数据库迁移
- 禁止修改已发布 Flyway 脚本内容
- 新变更一律新增迁移文件
- 涉及唯一键、索引、精度变更时，必须同步更新 `DB_SCHEMA.md`
- 涉及数据语义调整时，必须写明回滚与兼容策略

---

## 5. 重构规则

### 5.1 允许的重构
- 类重命名，但必须保持语义更清晰
- 包迁移，但必须避免跨模块职责混淆
- 过渡实现收敛到统一入口
- 去除重复状态推进逻辑
- 将交易所方言从 core / scheduler 收回 adapter

### 5.2 不允许的重构
- 为了“看着更优雅”大面积重排目录
- 未补测试就替换核心执行路径
- 未更新 runbook 就重写 recovery / reconcile 路径
- 未更新 numeric policy 就改金额/数量计算方式
- 顺手把 GateD 扩成研究 / 回测 / 前端工程

---

## 6. 文档同步规则

当改动以下内容时，必须同步更新对应文档：

- 执行入口、状态推进：`README.md`、`ARCHITECTURE.md`、`MODULES.md`、`STATE_MACHINE.md`
- 风控：`RISK_RULES.md`
- 契约：`CONTRACTS.md`
- 数据库：`DB_SCHEMA.md`
- 恢复 / 补偿：`COMPENSATION_SYNC.md`、`RECOVERY_RUNBOOK.md`
- 数值：`NUMERIC_POLICY.md`
- 阶段边界：`docs/current/README.md`、`docs/current/GATE_CHECKLIST.md`、`WORK.md`

---

## 7. current 与 gate 冻结文档关系

- `docs/current/*`：当前施工事实入口
- `docs/gates/gate-d/*`：GateD 权威卷宗与冻结归档

规则：

- 当前 Gate 施工时，优先更新 `docs/current/*`
- 同步更新 `docs/gates/gate-d/*`
- GateD 冻结后，`docs/current/*` 切换到下一 Gate，`docs/gates/gate-d/*` 保持只读

---

## 8. 测试优先规则

以下变更必须先补或同步补测试：

- 状态机迁移规则
- 风控规则链
- adapter 映射
- fills 去重
- ledger posting 幂等
- reconcile / recovery / query-confirm
- account / position snapshot 同步

---

## 9. 退出条件

当出现以下情况时，必须停止当前修改并先回到文档 / 设计收口：

- 一个 PR 同时改动 5 个以上主模块且没有 split plan
- scheduler 开始直接写订单状态或账本
- core 出现交易所私有分支
- 风控规则被写进 controller 或 adapter
- 数值处理出现 double / float
- 恢复逻辑依赖人工口头说明而无 runbook
