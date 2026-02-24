# NexusQuant（nexus-quant）

NexusQuant 是面向数字资产交易场景的量化系统工程。当前仓库处于 **Gate A（可开工骨架，已冻结）** 阶段：
只落地模块结构、接口契约、DDL、启动与配置骨架，不实现真实交易逻辑。

## 文档结构（按 Gate 冻结）

- 当前阶段入口：`docs/current/`
- Gate A 冻结快照：`docs/gates/gate-a/`
- 后续 Gate（B/C/...）：在完成后新增 `docs/gates/gate-b/`、`docs/gates/gate-c/` 并冻结。


## Gate A 范围

- 已做：`backend/` Maven 多模块父工程、模块边界、占位实现、Flyway 初始 DDL、`nq-app` 启动入口、PostgreSQL compose。
- 不做：真实交易所网络连接、策略算法、生产级风控/账本执行逻辑。
- 约束来源：
  - `docs/gates/gate-a/ARCHITECTURE.md`
  - `docs/gates/gate-a/GATE_A_CHECKLIST.md`
  - `docs/gates/gate-a/MODULES.md`
  - `docs/gates/gate-a/CONTRACTS.md`

## 模块总览（backend）

- 启动载体：`nq-app`
- 公共与契约：`nq-common`、`nq-contracts`
- 核心内核：`nq-core`、`nq-ledger`、`nq-risk`
- 横切模块：`nq-infra`、`nq-observability`、`nq-config`、`nq-scheduler`
- 接入控制面：`nq-security`、`nq-auth`、`nq-gateway`、`nq-api`
- 适配层占位：`nq-adapter-api`、`nq-adapter-okx`、`nq-adapter-binance`

## 本地启动（PowerShell）

1. 准备环境变量文件：

```powershell
Copy-Item .env.example .env
```

2. 启动 PostgreSQL：

```powershell
docker compose up -d postgres
```

3. 构建并执行测试（Gate A 质量闸之一）：

```powershell
mvn -q -f backend/pom.xml test
```

4. 启动应用（空业务骨架）：

```powershell
mvn -q -f backend/pom.xml -pl nq-app spring-boot:run
```

5. 健康检查：

```powershell
Invoke-WebRequest http://localhost:8080/actuator/health | Select-Object -ExpandProperty Content
```

## 环境配置分层（Profile）

`nq-app` 已按环境拆分配置：

- `backend/nq-app/src/main/resources/application.yml`：公共配置 + 默认 `NQ_PROFILE=local`
- `backend/nq-app/src/main/resources/application-local.yml`：本地开发可运行配置
- `backend/nq-app/src/main/resources/application-test.yml`：测试环境占位配置（待后续接入）
- `backend/nq-app/src/main/resources/application-prod.yml`：生产环境占位配置（待后续接入）

切换环境示例：

```powershell
$env:NQ_PROFILE = "test"
mvn -q -f backend/pom.xml -pl nq-app spring-boot:run
```

## Flyway 与数据库

- Flyway 脚本位置：`backend/nq-infra/src/main/resources/db/migration/V1__init.sql`
- 启动 `nq-app` 时会自动执行迁移。
- 当前 DDL 覆盖：`orders`、`trades`、`positions`、`account_snapshots`、`ledger_entries`、`ledger_events`、`strategy_runs`、`risk_events`、`audit_logs`、`event_store`、`users/roles/user_roles`。

## Gate A 验收入口

- 主清单：`docs/gates/gate-a/GATE_A_CHECKLIST.md`
- 架构基线：`docs/gates/gate-a/ARCHITECTURE.md`
- 模块依赖方向：`docs/gates/gate-a/MODULES.md`
- 契约规则：`docs/gates/gate-a/CONTRACTS.md`

## 停止与清理

```powershell
docker compose down
```

如需清理数据库卷（会删除本地数据）：

```powershell
docker compose down -v
```
