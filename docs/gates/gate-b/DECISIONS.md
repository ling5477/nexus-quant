# Gate B DECISIONS（ADR）

> 目的：记录 Gate B 关键设计决策，便于复盘、Code Review、以及 GateC 扩展时不走回头路。
>
> ADR 编号：ADR-Bxx（递增）
> 每条包含：Context / Decision / Consequences / Alternatives

---

## ADR-B01：引入“Paper Adapter”实现 Gate B 闭环

### Context
Gate B 需要跑通下单→成交→记账闭环，但不允许接真实交易所网络。

### Decision
实现一个 Paper 模式适配器（推荐独立模块 `nq-adapter-paper`），提供：
- place/cancel
- 定时撮合生成 trades
- 统一回报事件

### Consequences
- ✅ 闭环可在本地/CI 稳定复现
- ✅ 不污染真实 adapter 的网络逻辑
- ❌ 与真实交易所差异较大（GateC 需替换适配器）

### Alternatives
- 在 okx/binance adapter 中加 paper 开关（不选：边界污染、测试复杂）

---

## ADR-B02：订单状态机强制 + 非法迁移即失败

### Context
订单生命周期复杂，若允许随意改状态，会导致恢复/对账/审计不可控。

### Decision
在 `nq-core` 实现订单状态机：
- 所有状态变更必须通过状态机 API
- 非法迁移抛错并记录 audit/risk

### Consequences
- ✅ 一致性强，恢复简单
- ❌ 需要写单测覆盖迁移矩阵

### Alternatives
- 只靠 DB 状态字段自由写（不选：后期必炸）

---

## ADR-B03：client_order_id 幂等：DB 唯一约束 + 服务层去重

### Context
scheduler/事件重试会带来 at-least-once 调用。

### Decision
- orders 表加 UNIQUE（推荐 tenant_id + client_order_id）
- 服务层：重复请求读取并返回已存在订单结果

### Consequences
- ✅ 有效副作用 exactly-once
- ❌ 需要处理 unique violation 的并发窗口

### Alternatives
- 仅服务层缓存去重（不选：重启丢失）

---

## ADR-B04：记账以 Trade 为唯一驱动源 + 平衡校验

### Context
账本必须可复盘，且要防止“钱凭空出现/消失”。

### Decision
- trade 产生 ledger_event
- ledger_event 产生 ledger_entries
- 同一 ledger_event 的 entries 必须平衡（按币种）

### Consequences
- ✅ 可审计，可重放
- ❌ 初期实现略繁琐（但越早越值）

### Alternatives
- 直接改 positions/account_snapshots（不选：不可审计）

---

## ADR-B05：风控规则框架：最小规则集先落地

### Context
Gate B 需要风控闭环，但不追求策略复杂。

### Decision
实现规则引擎接口（Rule/RuleResult），默认规则：
- Kill switch
- 最大下单金额
- 最大持仓
- 下单频率限制

### Consequences
- ✅ 有扩展点，GateC 可加更多规则
- ❌ 规则参数配置需要后续完善

### Alternatives
- 写死 if-else（不选：无法扩展/审计弱）

---

## ADR-B06：恢复策略：从 orders 状态 + 调度重建

### Context
应用重启后必须可恢复闭环继续推进。

### Decision
- 启动时扫描非终态 orders
- 重建 scheduler 任务：撮合/对账/超时取消（最小集）

### Consequences
- ✅ 重启不丢单
- ❌ 启动时会有扫描成本（可接受）

### Alternatives
- 依赖内存队列（不选：重启丢失）