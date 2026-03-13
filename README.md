# README.md
# NexusQuant（nexus-quant）

NexusQuant 是面向数字资产交易场景的量化系统工程骨架，核心原则是：
**幂等（client_order_id） + 严格状态机 + 事实链（event_store） + 账本（ledger_entries） + 可审计 + 可恢复 + 可观测**。

> 当前处于哪个 Gate、当前 Gate 要做什么，以 `docs/current/` 为准。  
> 历史 Gate 冻结快照位于 `docs/gates/gate-*/`，只读参考，不作为当前实现边界。

---

## 1. 当前阶段

当前阶段：**GateD（统一执行闭环与执行域硬化）**。

GateD 的目标不是继续“接更多接口”，也不是提前跑去做研究平台，而是把已有的 GateC 能力收敛成一条稳定、可审计、可补偿、可验收的执行闭环。

GateD 的阶段目标：
- 统一执行入口
- pre-trade 硬风控
- Paper / OKX / Binance 的统一执行抽象
- 订单状态机硬化
- 交易回执、成交、账本、持仓、账户快照联动
- WS 加速 + REST 兜底
- recovery / reconcile / degrade / query-confirm
- GateD 文档、测试、验收冻结

---

## 2. 文档结构（按 Gate 冻结）

### 当前阶段入口（Source of Truth）
- `docs/current/README.md`：当前 Gate 总览入口
- `docs/current/GATE_CHECKLIST.md`：当前 Gate 唯一验收入口

### 当前 Gate 权威文档
- `docs/gates/gate-d/README.md`
- `docs/gates/gate-d/ARCHITECTURE.md`
- `docs/gates/gate-d/CONTRACTS.md`
- `docs/gates/gate-d/MODULES.md`
- `docs/gates/gate-d/DB_SCHEMA.md`
- `docs/gates/gate-d/STATE_MACHINE.md`
- `docs/gates/gate-d/RISK_RULES.md`
- `docs/gates/gate-d/COMPENSATION_SYNC.md`
- `docs/gates/gate-d/TEST_CASES.md`
- `docs/gates/gate-d/DECISIONS.md`
- `docs/gates/gate-d/EVOLUTION_RULES.md`
- `docs/gates/gate-d/NUMERIC_POLICY.md`
- `docs/gates/gate-d/PR_SPLIT_PLAN.md`
- `docs/gates/gate-d/RECOVERY_RUNBOOK.md`
- `docs/gates/gate-d/SOURCES.md`
- `docs/gates/gate-d/WORK.md`

### 历史 Gate 冻结快照
- `docs/gates/gate-a/`
- `docs/gates/gate-b/`
- `docs/gates/gate-c/`

根级 `docs/*.md` 中仍标记 Gate A 的旧文档只作 archive 参考，不作为当前实现事实来源；当前阶段仍以 `docs/current/*` 与 `docs/gates/gate-d/*` 为准。

---

## 3. 当前实现范围（Scope）

当前 Gate 只在 `backend/` 内推进“统一执行闭环”与“执行域硬化”，重点包括：

- `nq-core`：统一执行应用服务、状态推进、事件协调
- `nq-risk`：pre-trade 硬风控规则链
- `nq-adapter-api`：统一执行端口与归一模型
- `nq-adapter-okx` / `nq-adapter-binance`：交易所对接与归一映射
- `nq-scheduler`：reconcile / recovery / degrade 调度
- `nq-ledger`：成交、账本、持仓、账户投影联动
- `nq-app`：本地验收入口与 profile 约束
- `nq-infra`：Flyway 与基础持久化支撑
- `nq-observability`：日志、指标、trace 规范

以下目录与能力**不属于 GateD 实现范围**：

- `infra/`：Kafka、Debezium、Prometheus、Grafana、Tempo、Loki、K8s、Terraform 等生产基建
- `frontend/`：控制台 UI、运营台、可视化大屏
- `research/`：回测、因子分析、组合优化、自动化研究平台
- 合约 / 杠杆 / 期货 / 期权执行域

---

## 4. 模块总览（backend）

- 启动载体：`nq-app`
- 公共与契约：`nq-common`、`nq-contracts`
- 核心内核：`nq-core`、`nq-ledger`、`nq-risk`
- 横切模块：`nq-infra`、`nq-observability`、`nq-config`、`nq-scheduler`
- 接入控制面：`nq-security`、`nq-auth`、`nq-gateway`、`nq-api`
- 适配层：`nq-adapter-api`、`nq-adapter-okx`、`nq-adapter-binance`

---

## 5. API Key 安全约束（强制）

- Demo / Testnet Key：允许不绑 IP，用于本地开发与验收。
- Real Key：必须绑定 IP 白名单；权限只开 Read + Trade，**永不启用 Withdraw**。
- 所有密钥只允许写入本地 `.env`，禁止提交到仓库；仓库只保留 `.env.example` 占位符。
- 私有 WS、recovery、reconcile 的真实验证必须显式区分 demo / real profile，禁止混用。

---

## 6. 本地启动（PowerShell）

### 6.1 准备环境变量
```powershell
Copy-Item .env.example .env
```

### 6.2 启动 Postgres
```powershell
docker compose up -d postgres
```

### 6.3 构建并执行测试
```powershell
mvn -q -f backend/pom.xml test
```

### 6.4 启动应用
```powershell
mvn -q -f backend/pom.xml -pl nq-app spring-boot:run
```

### 6.5 健康检查
端口以 `application-local.yml` 为准，例如：
```powershell
Invoke-WebRequest http://localhost:18888/actuator/health | Select-Object -ExpandProperty Content
```

---

## 7. GateD 本地验证最小顺序

1. 应用启动且 health `UP`
2. 执行 paper LIMIT -> cancel
3. 执行 paper MARKET -> fill
4. 核查 `orders / trades / ledger_entries / positions / event_store / audit_logs`
5. 手工触发一次 reconcile
6. 手工触发一次 recovery
7. 核查无重复成交、重复记账、状态回退
8. 再执行一个 OKX 或 Binance 最小 LIMIT -> cancel 验证

---

## 8. 环境变量约定（NQ_ 前缀）

### 8.1 OKX
- `NQ_OKX_ENV=dome|real`
- Demo：`NQ_OKX_DOME_BASE_URL / NQ_OKX_DOME_API_KEY / NQ_OKX_DOME_API_SECRET / NQ_OKX_DOME_API_PASSPHRASE`
- Real：`NQ_OKX_REAL_BASE_URL / NQ_OKX_REAL_API_KEY / NQ_OKX_REAL_API_SECRET / NQ_OKX_REAL_API_PASSPHRASE`
- 通用：`NQ_OKX_TIMEOUT_MS`

### 8.2 Binance
- `NQ_BINANCE_ENV=dome|real`
- `NQ_BINANCE_KEY_TYPE=hmac|ed25519`
- Demo：`NQ_BINANCE_DOME_BASE_URL / NQ_BINANCE_DOME_WS_URL / NQ_BINANCE_DOME_API_KEY / NQ_BINANCE_DOME_API_SECRET`
- Real：`NQ_BINANCE_REAL_BASE_URL / NQ_BINANCE_REAL_WS_URL / NQ_BINANCE_REAL_API_KEY / NQ_BINANCE_REAL_API_SECRET`
- 通用：`NQ_BINANCE_TIMEOUT_MS`

---

## 9. 环境配置分层（Profile）

- `application.yml`：公共配置与默认 profile
- `application-local.yml`：本地可运行配置
- `application-test.yml`：测试环境占位
- `application-prod.yml`：生产环境占位

切换环境示例：
```powershell
$env:NQ_PROFILE = "test"
mvn -q -f backend/pom.xml -pl nq-app spring-boot:run
```

---

## 10. Flyway 与数据库

- Flyway 脚本位置：`backend/nq-infra/src/main/resources/db/migration/`
- 启动 `nq-app` 时自动执行迁移。
- GateD 建议新增迁移：`V5__gate_d_execution_closure.sql`
- GateD 重点关注表：`orders`、`trades`、`positions`、`account_snapshots`、`ledger_entries`、`audit_logs`、`event_store`

---

## 11. 当前 Gate 验收入口（唯一）

- `docs/current/GATE_CHECKLIST.md`
- `docs/current/README.md`

历史 Gate 的 checklist 仅供参考。当前阶段以 `docs/current/` 为准。

---

## 12. 停止与清理

```powershell
docker compose down
```

如需删除本地卷：
```powershell
docker compose down -v
```



## 10. GateD 施工约束补充

GateD 已进入“可持续施工”阶段，除主文档外，还必须遵守以下约束文档：

- `docs/gates/gate-d/DECISIONS.md`：记录阶段性工程决策
- `docs/gates/gate-d/EVOLUTION_RULES.md`：规定哪些能改、怎么改、哪些不能乱动
- `docs/gates/gate-d/NUMERIC_POLICY.md`：统一数值、精度、舍入、比较与持久化策略
- `docs/gates/gate-d/PR_SPLIT_PLAN.md`：规定 GateD 提交拆分边界
- `docs/gates/gate-d/RECOVERY_RUNBOOK.md`：规定恢复、补偿与排障操作路径
