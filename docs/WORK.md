# WORK（会话衔接上下文）

> 最后更新：2026-02-13  
> 范围：Gate A 可开工工程骨架落地（不实现业务逻辑）

## 1. 今日目标与边界

- 目标：仅依据 `docs/` 文档，在 `backend/` 落地可编译、可启动、可继续分模块开发的骨架工程。
- 明确不做：
  - 不接真实交易所网络
  - 不实现真实策略/撮合/交易业务逻辑
  - 不引入与 Gate A 无关依赖

## 2. 今日已完成（核心结果）

### 2.1 多模块工程与模块骨架

- 已创建 `backend/pom.xml` 父工程（Java 21 + Spring Boot 3.5.10）。
- 已创建并可编译的模块：
  - `nq-app`
  - `nq-common`
  - `nq-contracts`
  - `nq-infra`
  - `nq-ledger`
  - `nq-risk`
  - `nq-core`
  - `nq-config`
  - `nq-scheduler`
  - `nq-observability`
  - `nq-adapter-api`
  - `nq-adapter-okx`
  - `nq-adapter-binance`
  - `nq-auth`
  - `nq-security`
  - `nq-gateway`
  - `nq-api`

### 2.2 契约、核心与横切占位

- `nq-contracts`：Envelope/Topic/命令与事件 payload 占位 DTO 已冻结。
- `nq-common`：`traceId`、`ErrorCode`、数值归一化策略占位实现已落地。
- `nq-core/nq-ledger/nq-risk`：状态机、恢复、账本、风控/kill switch 仅骨架实现。
- `nq-observability`：`TraceIdFilter` 与自动装配骨架。

### 2.3 数据库与迁移

- `nq-infra` 下已落地 Flyway 目录与 `V1__init.sql`。
- 已覆盖最小核心表与约束（orders/trades/positions/account_snapshots/ledger_entries/ledger_events/strategy_runs/risk_events/audit_logs/event_store/users/roles/user_roles）。

### 2.4 启动与配置

- `nq-app` 启动入口、模块装配、Actuator 健康检查骨架已完成。
- 应用配置已按环境拆分：
  - `application.yml`（公共 + 默认 profile）
  - `application-local.yml`（本地可运行）
  - `application-test.yml`（占位）
  - `application-prod.yml`（占位）
- 默认数据库参数已同步：
  - DB：`nexus_quant`
  - User：`postgres`
  - Password：`123456`
- 根配置已同步：`.env.example`、`docker-compose.yml`。

### 2.5 文档对齐

- `README.md` 已更新本地启动、Profile 使用说明与 Gate A 验收入口。
- `docs/DECISIONS.md` 已新增 ADR-011（Gate A 骨架落地决策）。

## 3. 验证记录（关键命令）

- 构建/测试：
  - `mvn -q -f backend/pom.xml test` ✅
- 容器与数据库：
  - `docker compose up -d postgres` ✅
  - `docker inspect -f "{{.State.Health.Status}}" nexusquant-postgres` => `healthy` ✅
- 应用启动：
  - `mvn -q -f backend/pom.xml -pl nq-app -am spring-boot:run`（通过探活验证）✅
  - `/actuator/health` => `UP` ✅

## 4. 过程中修复过的问题

1. Docker 早期不可用  
   - 现象：`dockerDesktopLinuxEngine` 管道不存在。  
   - 结论：Docker Engine 未就绪；后续已恢复并验证通过。

2. Flyway 对 PostgreSQL 17.7 识别失败  
   - 现象：`Unsupported Database: PostgreSQL 17.7`。  
   - 修复：在 `nq-app` 增加 `org.flywaydb:flyway-database-postgresql` 依赖。

## 5. 当前可直接继续的下一步（给新会话）

1. 按模块进入 Gate B 实现（建议顺序）：
   - `nq-contracts/nq-common` 细化
   - `nq-core` 状态机与幂等行为
   - `nq-ledger` 平衡校验与回放
   - `nq-risk` 规则框架
2. 补单元测试（状态机非法迁移、幂等、账本重算、恢复流程）。
3. 保持约束：
   - 严格状态机
   - `client_order_id` 幂等
   - `trace_id` 贯穿 HTTP/事件/日志
   - 仅做最小可审查增量变更
