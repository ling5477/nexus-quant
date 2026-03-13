# Modules（GateD 对齐版）

> Top-Level Navigation Notice
> - 本文件是根级导航摘要，用于帮助快速定位 GateD 模块分工，不是当前阶段的 Source of Truth。
> - 当前阶段的模块职责事实以 `docs/current/*`、`docs/gates/gate-d/MODULES.md`、README/checklist 为准。
> - 若本文件与当前 Gate 文档不一致，以后者为准。
> - 只有当模块职责、模块边界、主战场模块优先级发生明显变化时，才需要同步更新本摘要。
> - 本文件不承担详细阶段状态、细粒度冻结条件或详细实施计划职责。

## 1. backend 模块职责

### nq-core
GateD 的执行域中心。负责：
- 统一执行入口
- place / cancel / query-confirm 编排
- 统一状态推进入口
- 与 risk / adapter / ledger / scheduler 协调

### nq-risk
GateD 的 pre-trade 硬风控模块。负责：
- 风控规则链
- 风控拒绝标准模型
- 风控日志与审计协同

### nq-adapter-api
统一执行端口与归一模型。负责：
- place / cancel / query / list-open-orders / fills / snapshots 统一接口
- venue 方言与 core 隔离

### nq-adapter-okx / nq-adapter-binance
负责：
- 外部请求签名、调用、解析
- venue 状态 / 错误 / 回报 映射到统一模型

### nq-scheduler
负责：
- reconcile / recovery / degrade job 触发与协调
- 非终态窗口扫描
- 不负责核心业务状态机实现

### nq-ledger
负责：
- trade posting
- ledger entries
- position projection
- account snapshot projection
- 幂等记账与失败可见

### nq-app
负责：
- Spring Boot 启动承载
- 本地验收入口
- profile / feature gate 控制

### nq-infra
负责：
- Flyway
- repository 持久化基础设施
- 数据源配置

### nq-observability
负责：
- 日志字段规范
- 指标、trace、故障观测

### nq-api
负责：
- 对外查询视图
- order / trade / position / account read facade

## 2. GateD 不作为主改对象
- nq-auth
- nq-security
- nq-gateway
- frontend
- research
