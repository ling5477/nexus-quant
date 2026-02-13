# nexus-quant 架构决策记录（DECISIONS / ADR）

> 目的：记录关键决策的“为什么”，避免后续推翻重来  
> 规则：凡是影响 域模型/状态机/账本/DDL/事件契约/Auth/Gateway 的变更，必须先写 ADR，再改代码  
> 决策人：帅哥

---

## ADR 索引

| ADR ID | 标题 | 状态 | 日期 | 影响范围 |
|---|---|---|---|---|
| ADR-001 | mono-repo + backend/research/frontend 结构 | Accepted | 2026-02-12 | repo |
| ADR-002 | 包名使用 com.guidinglight.nexusquant，模块使用 nq-* | Accepted | 2026-02-12 | repo |
| ADR-003 | 交易内核优先 Java（Gate A），Python 后置 | Accepted | 2026-02-12 | backend/research |
| ADR-004 | PostgreSQL + Flyway 作为交易域主库与迁移 | Accepted | 2026-02-12 | db |
| ADR-005 | 事件驱动：契约先行，nq-contracts 统一归口 | Accepted | 2026-02-12 | contracts |
| ADR-006 | 命令幂等键：UNIQUE(account_id, client_order_id) | Accepted | 2026-02-12 | order |
| ADR-007 | 资金账本 Ledger：流水可重算余额 + 平衡校验 | Accepted | 2026-02-12 | ledger |
| ADR-008 | Trade 为最终事实：可纠偏订单状态（处理竞态/乱序） | Accepted | 2026-02-12 | order/trade |
| ADR-009 | 增加 nq-auth + nq-gateway：Gate A 只做最小骨架 | Accepted | 2026-02-12 | auth/gateway |
| ADR-010 | traceId 规范：网关透传 + MDC + 事件 Envelope trace_id | Accepted | 2026-02-12 | obs/contracts |

> 状态枚举：Proposed / Accepted / Rejected / Superseded

---

## ADR 模板

### ADR-XXX：<标题>
- **状态**：Proposed / Accepted / Rejected / Superseded
- **日期**：YYYY-MM-DD
- **决策人**：帅哥
- **上下文**：
  - 问题是什么？为什么现在必须决定？
- **决策**：
  - 选择了什么方案（一句话）
- **备选方案**：
  1. 方案 A（优缺点）
  2. 方案 B（优缺点）
- **关键权衡**：
  - 性能 / 复杂度 / 可维护 / 风险
- **影响范围**：
  - 模块/数据/契约/测试
- **落地动作**：
  - [ ] 代码修改
  - [ ] 文档修改
  - [ ] 测试新增
- **回滚策略**：
  - 如何回退
- **复审条件**：
  - 触发复审的条件/时间点

---

## 已冻结决策（Gate A 基线）

### ADR-001：mono-repo + backend/research/frontend 结构
- 状态：Accepted
- 日期：2026-02-12
- 上下文：个人开发需要统一版本与低协作成本；减少多仓库摩擦
- 决策：一个仓库，三端隔离（构建系统隔离）
- 影响范围：目录结构、CI、发布
- 落地动作：
  - [ ] 创建目录结构与基础构建文件
  - [ ] README 写清启动与开发流程

### ADR-002：包名与模块命名规范
- 状态：Accepted
- 日期：2026-02-12
- 上下文：统一命名避免后期重构、便于代码生成与检索
- 决策：
  - 包名统一：`com.guidinglight.nexusquant`
  - 模块统一：`nq-*`
- 影响范围：所有 Java 模块
- 落地动作：
  - [ ] 父 POM 强制 groupId
  - [ ] Checkstyle（可选）约束包名

### ADR-003：交易内核优先 Java，Python 后置
- 状态：Accepted
- 日期：2026-02-12
- 上下文：闭环正确性依赖状态机/幂等/账本/恢复，Java 工程治理更强
- 决策：Gate A 先实现 Java 内核；Python 仅建 research 骨架，后续做研究/回测
- 影响范围：迭代顺序、接口边界
- 落地动作：
  - [ ] Strategy 仅接口占位
  - [ ] 事件契约先冻结

### ADR-004：PostgreSQL + Flyway
- 状态：Accepted
- 日期：2026-02-12
- 上下文：交易域需要强一致 + 可恢复；个人开发需要迁移可控
- 决策：PG 作为主库；Flyway 管理迁移
- 影响范围：DDL、索引、迁移命名
- 落地动作：
  - [ ] V1__init.sql
  - [ ] docker-compose 启动 PG

### ADR-005：契约先行 + nq-contracts 归口
- 状态：Accepted
- 日期：2026-02-12
- 上下文：恢复/回放/复盘/幂等需要稳定事件模型；避免 DTO 分散漂移
- 决策：所有 topic/Envelope/payload DTO 统一放 `nq-contracts`
- 影响范围：事件模型、后续 adapter
- 落地动作：
  - [ ] `nq-contracts` 定义 topic 常量与 DTO
  - [ ] `docs/CONTRACTS.md` 固化规范

### ADR-006：命令幂等键与唯一约束
- 状态：Accepted
- 日期：2026-02-12
- 上下文：网络重试与断线恢复会产生重复命令
- 决策：以 `(account_id, client_order_id)` 做硬幂等（DB UNIQUE + 代码行为定义）
- 影响范围：orders 表、下单服务、测试
- 落地动作：
  - [ ] UNIQUE(account_id, client_order_id)
  - [ ] 重复下单行为测试

### ADR-007：Ledger 账本（流水可重算余额 + 平衡校验）
- 状态：Accepted
- 日期：2026-02-12
- 上下文：仅余额快照无法解释 PnL 漂移；对账与审计必须依赖流水
- 决策：每笔成交与手续费必须记账；余额可聚合重算；提供平衡校验
- 影响范围：ledger_entries、记账逻辑、恢复与对账
- 落地动作：
  - [ ] 成交生成 ledger entry（含 fee）
  - [ ] 平衡校验单测

### ADR-008：Trade 为最终事实（纠偏订单状态）
- 状态：Accepted
- 日期：2026-02-12
- 上下文：撤单与迟到成交、乱序回报普遍存在
- 决策：Trade 事件优先；订单状态允许被成交纠偏
- 影响范围：状态机设计、事件消费、账本与持仓更新
- 落地动作：
  - [ ] 撤单后迟到成交用例测试
  - [ ] 乱序/重复成交去重测试

### ADR-009：增加 nq-auth + nq-gateway（Gate A 最小骨架）
- 状态：Accepted
- 日期：2026-02-12
- 上下文：未来 API/管理能力需要统一入口与鉴权；避免后期“拆网关”重构
- 决策：
  - Gate A 即创建 auth/gateway 模块并提供最小可用登录与鉴权骨架
  - 不做复杂 RBAC 管理与页面
- 影响范围：模块、DDL、契约（login、traceId）
- 落地动作：
  - [ ] /auth/login
  - [ ] gateway JWT 校验 + traceId 透传
  - [ ] users/roles/user_roles 表

### ADR-010：traceId 全链路规范
- 状态：Accepted
- 日期：2026-02-12
- 上下文：复盘/定位问题必须可追溯；事件与日志要关联
- 决策：
  - 网关生成/透传 `X-Trace-Id`
  - 后端写入 MDC
  - 事件 Envelope 必带 trace_id
- 影响范围：网关过滤器、common 工具、contracts
- 落地动作：
  - [ ] traceId filter
  - [ ] 日志格式包含 traceId

---

## 变更记录（占位）
> 当某条 ADR 被替代：写明 “ADR-XXX 被 ADR-YYY Superseded”，原因是什么。
