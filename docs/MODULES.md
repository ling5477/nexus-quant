# 模块边界与依赖（MODULES）

> 本文档是 `ARCHITECTURE.md` 的补充：给出**模块职责、依赖方向、以及“启动载体”约定**。  
> Gate A：只冻结边界与依赖，不实现业务细节。

---

## 1. 模块分层

### 1.1 启动载体（Runtime）
- **nq-app（新增）**：Spring Boot 入口模块（推荐单体起步）
  - 负责：装配各模块 Bean、加载配置、启动调度、暴露健康检查/控制面骨架
  - 禁止：在 nq-app 内写领域逻辑；领域逻辑必须落在 core/ledger/risk 等模块

> Gate A 仅要求“启动骨架 + 可观测字段贯穿”，不要求实际连交易所。

### 1.2 平台/横切（Cross-cutting）
- **nq-observability（新增）**
  - 负责：日志规范（JSON/MDC）、traceId 生成与透传、metrics 命名规范、OpenTelemetry 占位
- **nq-config（新增）**
  - 负责：策略参数与风控阈值的版本化/快照（configSnapshot）设计与接口约定
- **nq-scheduler（新增）**
  - 负责：策略实例生命周期编排（start/stop/restart）、定时任务、巡检任务（仅骨架）
- **nq-infra**
  - 负责：PG/Flyway、（占位）Kafka/Redis 客户端封装、Outbox（如采用）

### 1.3 核心交易内核（Trading Kernel）
- **nq-core**
  - 负责：Order/Trade/Position/Account 等域模型、状态机、幂等键、回执处理协议
- **nq-ledger**
  - 负责：资金账本（可重算）、平衡校验、对账口径（事件驱动）
- **nq-risk**
  - 负责：pre/in/post-trade 风控规则与 Kill Switch（规则框架 + 事件记录）

### 1.4 接入层与契约
- **nq-gateway / nq-auth / nq-security**
  - 负责：控制面 API 骨架、JWT、权限、审计字段约定
- **nq-contracts**
  - 负责：事件 Envelope、Topic 常量、DTO、版本号与兼容策略
- **nq-adapter-api / nq-adapter-okx / nq-adapter-binance（占位）**
  - 负责：交易所适配接口与实现（Gate A 不要求实现网络连接，只冻结接口）

---

## 2. 依赖方向（强制）

依赖只允许“向下”：
- `nq-app` → 平台/内核/接入模块
- `nq-gateway` → `nq-auth/nq-security`、（读取）`nq-core` 查询接口、`nq-contracts`
- `nq-core` → `nq-contracts`、`nq-common`
- `nq-ledger` → `nq-contracts`、`nq-common`
- `nq-risk` → `nq-contracts`、`nq-common`

禁止：
- core 依赖 gateway
- ledger 依赖 gateway
- risk 依赖 adapter 具体实现
- 任意模块直接依赖“具体交易所实现”（应通过 adapter-api 抽象）

---

## 3. Gate A 必需输出（文档层）

- 模块清单（本文件）
- 关键领域对象与状态机（见 `ARCHITECTURE.md`）
- 事件契约与演进规则（见 `CONTRACTS.md`、`EVOLUTION_RULES.md`）
- 数值精度策略（见 `NUMERIC_POLICY.md`）
- 恢复/回放口径（见 `RECOVERY_RUNBOOK.md`）
