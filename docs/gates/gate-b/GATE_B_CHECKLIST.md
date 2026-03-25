# Gate B CHECKLIST（验收门禁）

> Gate B 完成标准：跑通一次“模拟盘最小交易闭环”，并满足幂等、状态机、记账平衡、可恢复、可观测。

---

## 1. 构建与测试（必须 PASS）

- [x] `mvn -q -f backend/pom.xml test`
- [x] 核心单测存在且通过：
    - [x] 状态机合法迁移
    - [x] 状态机非法迁移
    - [x] client_order_id 幂等（重复下单不产生新订单）
    - [x] 撮合重复执行不产生重复 trade（或能被幂等拦截）
    - [x] 记账平衡校验（PASS/FAIL 两类）

---

## 2. 本地运行（必须 PASS）

- [x] `docker compose up -d postgres`
- [x] 数据库健康：`docker inspect -f "{{.State.Health.Status}}" nexusquant-postgres` => `healthy`
- [x] 启动：`mvn -q -f backend/pom.xml -pl nq-app -am spring-boot:run`
- [x] 探活：`/actuator/health` => `UP`

---

## 3. 闭环验证（必须 PASS）

### 3.1 触发一次策略运行（任选一种方式）

- 历史可选路径：HTTP 触发（如有），未作为 GateB 冻结前置条件
- [x] scheduler 定时触发（推荐：本地自动跑）

### 3.2 数据库检查点（必须出现）

- [x] `strategy_runs`：新增 1 条（run_id 可追踪）
- [x] `orders`：新增 1 条（含 client_order_id，状态机状态正确）
- [x] `risk_events`：至少 1 条（PASS 或 REJECT）
- [x] `trades`：>= 1 条（paper 撮合生成）
- [x] `ledger_events`：>= 1 条（关联 trade/order）
- [x] `ledger_entries`：>= 2 条（借贷分录），且平衡校验通过
- [x] `positions` / `account_snapshots`：发生变化（最小可用投影）
- [x] `audit_logs`：至少 3 条（下单、风控、成交/记账）

---

## 4. 重启恢复（必须 PASS）

- [x] 在订单未终态时重启应用
- [x] 重启后：
    - [x] 不重复创建订单（幂等）
    - [x] 状态机继续推进到终态（或明确可恢复为待撮合）
    - [x] 不产生重复 trade/ledger（或幂等拦截）

---

## 5. Stretch（可选加分项）

- [x] 支持撤单（CancelOrder）并进入 CANCELLED 终态
- [x] 支持 LIMIT 基础行为（满足价格才成交）
- [x] 提供最小对账任务（ledger reconcile）并可输出差异
