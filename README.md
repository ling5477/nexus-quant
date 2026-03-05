# README.md
# NexusQuant（nexus-quant）

NexusQuant 是面向数字资产交易场景的量化系统工程骨架，核心原则是：
**幂等（client_order_id） + 严格状态机 + 事实链（event_store） + 账本（ledger_entries） + 可审计 + 可恢复 + 可观测**。

> 当前处于哪个 Gate、当前 Gate 要做什么，以 `docs/current/` 为准（这是唯一入口/唯一事实来源）。  
> 历史 Gate 冻结快照位于 `docs/gates/gate-*/`（只读参考）。

---

## 文档结构（按 Gate 冻结）

- **当前阶段入口（Source of Truth）**：`docs/current/`
  - `docs/current/README.md`：当前 Gate 总览入口
  - `docs/current/GATE_CHECKLIST.md`：当前 Gate 验收门禁（唯一验收入口）
  - `docs/current/WORK_TEMPLATE.md`：工作记录模板
- **历史 Gate 冻结快照**：`docs/gates/gate-a/`、`docs/gates/gate-b/`、`docs/gates/gate-c/` ……（完成后新增并冻结）
- **权威依据（必须可追溯）**
  - 当前 Gate 的对外接口、WS 通道、关键约束必须能在对应 Gate 的 `SOURCES.md` 中找到出处（例如 GateC：`docs/gates/gate-c/SOURCES.md`）。

---

## 当前实现范围（Scope）

- **当前 Gate（以 `docs/current/` 为准）**：
  - 阶段只在 `backend/` 内推进“可交易、可对账、可恢复”的执行闭环（adapter/OMS/ledger/reconcile/recovery/audit/event_store）。
- **以下目录/能力在当前 Gate **不实现**，进入后续 Gate（GateD/GateE）再推进**：
  - `infra/`：Kafka/Debezium（outbox/CDC）、Prometheus/Grafana/Tempo/Loki、K8s/Helm/Terraform 等生产化基础设施
  - `frontend/`：订单/成交/持仓/事件链的控制台 UI
  - `research/`：回测/因子评估（Alphalens/Pyfolio）、RD-Agent/Qlib 自动化研究等

---

## 模块总览（backend）

- 启动载体：`nq-app`
- 公共与契约：`nq-common`、`nq-contracts`
- 核心内核：`nq-core`、`nq-ledger`、`nq-risk`
- 横切模块：`nq-infra`、`nq-observability`、`nq-config`、`nq-scheduler`
- 接入控制面：`nq-security`、`nq-auth`、`nq-gateway`、`nq-api`
- 适配层：`nq-adapter-api`、`nq-adapter-okx`、`nq-adapter-binance`

---

## API Key 安全约束（强制）

- Demo（模拟盘）Key：允许不绑 IP，用于本地开发与验收。
- Real（真实盘）Key：必须绑定 IP 白名单；权限只开 Read + Trade，**永不启用 Withdraw**。
- 所有密钥仅写入本地 `.env`，禁止提交到仓库；仓库只维护 `.env.example` 占位符。

---

## 本地启动（PowerShell）

1. 准备环境变量文件：

```powershell
Copy-Item .env.example .env
```
2. 启动 Postgres：
```powershell
docker compose up -d postgres
```
3. 构建并执行测试（质量闸之一）：
```powershell
mvn -q -f backend/pom.xml test
```
4. 启动应用：
```powershell
mvn -q -f backend/pom.xml -pl nq-app spring-boot:run
```
5. 访问健康检查：
- 端口以 `application-local.yml` 为准（下例为 18888）。
```powershell
Invoke-WebRequest http://localhost:18888/actuator/health | Select-Object -ExpandProperty Content
```
---

## 环境变量约定（NQ_ 前缀）

OKX 配置按环境隔离，避免混用凭证：
- `NQ_OKX_ENV=dome|real`
- Demo：`NQ_OKX_DOME_BASE_URL / NQ_OKX_DOME_API_KEY / NQ_OKX_DOME_API_SECRET / NQ_OKX_DOME_API_PASSPHRASE`
- Real：`NQ_OKX_REAL_BASE_URL / NQ_OKX_REAL_API_KEY / NQ_OKX_REAL_API_SECRET / NQ_OKX_REAL_API_PASSPHRASE`
- 通用：`NQ_OKX_TIMEOUT_MS`

---

## 环境配置分层（Profile）

1. nq-app 已按环境拆分配置：
- `backend/nq-app/src/main/resources/application.yml`：公共配置 + 默认 NQ_PROFILE=local
- `backend/nq-app/src/main/resources/application-local.yml`：本地开发可运行配置
- `backend/nq-app/src/main/resources/application-test.yml`：测试环境占位配置（待后续接入）
- `backend/nq-app/src/main/resources/application-prod.yml`：生产环境占位配置（待后续接入）

2. 切换环境示例：
```powershell
$env:NQ_PROFILE = "test"
mvn -q -f backend/pom.xml -pl nq-app spring-boot:run
```
--- 

## Flyway 与数据库
- Flyway 脚本位置：`backend/nq-infra/src/main/resources/db/migration/V1__init.sql`
- 启动 nq-app 时会自动执行迁移。
- 当前 DDL 覆盖：orders、trades、positions、account_snapshots、ledger_entries、ledger_events、strategy_runs、risk_events、audit_logs、event_store、users/roles/user_roles。

--- 

## 当前 Gate 验收入口（唯一）
- `docs/current/GATE_CHECKLIST.md`
- 其它入口：`docs/current/README.md`
- 历史 Gate 的验收清单在 `docs/gates/gate-*/GATE_*_CHECKLIST.md`，仅供参考；当前 Gate 以 `docs/current/` 为准。
- GateC 验收触发器（`/__gatec/*`）仅用于本地验收：需要 `local` profile 且 `nq.gatec.verify.enabled=true` 才会暴露；生产环境永不暴露。
--- 

## 停止与清理
```powershell
docker compose down
```
--- 

## 如需清理数据库卷（会删除本地数据）：
```powershell
docker compose down -v
```
---

