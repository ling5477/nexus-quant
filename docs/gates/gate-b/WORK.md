# Gate B WORK 记录

## 2026-02-25 - Gate B 最小闭环实现（PR-1 ~ PR-4）

### PR-1（contracts + event_store 事实源）

- 做了什么：
    - 在 `nq-contracts` 新增 Gate B 事件 payload：`OrderCreated`、`RiskPassed`、`RiskRejected`、`OrderSubmitted`、
      `TradeExecuted`、`LedgerPosted`、`LedgerPostFailed`、`PositionUpdated`、`AuditRecorded`、`RiskEventRaised`。
    - 细化 `PlaceOrderCommand`、`CancelOrderCommand` 的 `@JsonProperty` 下划线字段映射。
    - 在 `nq-infra` 新增 `EventStoreAppender`（JDBC 写入 `event_store`，写入 envelope 全量 JSON）。
    - 新增测试：
        - `EventEnvelopeSerializationTest`
        - `EventStoreAppenderTest`
- 验证命令：
    - `mvn -q -f backend/pom.xml test`
- 结果：
    - 通过。
- 坑与修复：
    - `h2` 依赖拉取失败（aliyun 源网络重置），改为 `RecordingJdbcTemplate` 替身测试，不再依赖外部 DB 驱动。

### PR-2（nq-core：幂等下单 + 状态机 + 审计）

- 做了什么：
    - 扩展 `OrderStatus`：新增 `RISK_PASSED`、`RISK_REJECTED`、`SENT`、`ACCEPTED`。
    - 更新 `InMemoryOrderStateMachine`：覆盖 Gate B 最小链路 `NEW -> RISK_PASSED -> SENT -> FILLED` 与
      `NEW -> RISK_REJECTED`，并保留旧骨架路径兼容。
    - 新增 `OrderCommandService`，实现：
        - `account_id + client_order_id` 幂等
        - 显式状态迁移 API（非法迁移抛错并写 `audit_logs`）
        - 下单命令和订单事件写 `event_store`（`ORDER_COMMAND_V1`、`ORDER_EVENT_V1`）
        - 风控结果写 `risk_events`，并同步写 `RISK_EVENT_V1`
    - 新增 JDBC 仓储：`JdbcOrderRepository`、`JdbcAuditLogRepository`、`JdbcRiskEventRepository`。
    - 新增测试：
        - `InMemoryOrderStateMachineTest`（非法迁移 >= 5）
        - `OrderCommandServiceTest`（幂等 + 非法迁移审计）
- 验证命令：
    - `mvn -q -f backend/pom.xml test`
- 结果：
    - 通过。

### PR-3（paper 撮合：orders -> trades，不出网）

- 做了什么：
    - 新增 `PaperMatchingService`（定时扫描 `SENT/ACCEPTED`，生成 `PAPER` 成交，不出网）。
    - 新增 `OrderExecutionGateway` + `CoreOrderExecutionGateway`，保证撮合层只能通过 `nq-core` 迁移状态。
    - 新增 `TradeRepository` + `JdbcTradeRepository`，按 `order_id` 去重，重复 tick 不生成重复 trade。
    - 成交后写 `TRADE_EVENT_V1`，并推进订单到 `FILLED`。
    - `SchedulerConfiguration` 启用 `@EnableScheduling`。
    - 新增测试：`PaperMatchingServiceTest`（重复 tick 幂等）。
- 验证命令：
    - `mvn -q -f backend/pom.xml test`
- 结果：
    - 通过。

### PR-4（nq-ledger：trade -> ledger + positions）

- 做了什么：
    - 新增 `TradeLedgerPostingService`，实现：
        - `trade_id` 维度幂等分录键（`trade_id:LEDGER:*`）
        - `ledger_entries` 写入（>=2 条）与 `ledger_events` 同步写入
        - 平衡校验（净额归零）
        - 失败分支：写 `risk_events(scope=LEDGER)` + `audit_logs` + `LedgerPostFailed`（`LEDGER_EVENT_V1`）+
          `RiskEventRaised`（`RISK_EVENT_V1`）
        - 成功分支：写 `LedgerPosted`（`LEDGER_EVENT_V1`）+ `PositionUpdated`（`POSITION_EVENT_V1`）+ `audit_logs`
    - 新增 JDBC 仓储：
        - `JdbcLedgerPostingRepository`
        - `JdbcLedgerRiskAuditRepository`
    - 撮合链路接入 ledger：`PaperMatchingService` 在 trade 后调用记账网关。
    - 新增测试：`TradeLedgerPostingServiceTest`（记账幂等 + 平衡校验 PASS/FAIL）。
- 验证命令：
    - `mvn -q -f backend/pom.xml test`
- 结果：
    - 通过。

### 闭环触发补齐（策略入口）

- 做了什么：
    - 新增 `GateBDemoStrategyRunner`：
        - 定时确保 demo `accounts` 与 `strategy_runs` 存在
        - 触发固定 `client_order_id` 的 `PlaceOrder`（可重启防重）
        - 订单终态后不再重复触发
- 目的：
    - 保证本地启动后无需手工操作即可跑最小闭环。

### 本轮统一验证

- 命令：
    - `mvn -q -f backend/pom.xml test`
- 结果：
    - 全部通过。

### 运行态验证补充说明

- 命令：
    - `docker compose up -d postgres`
- 结果：
    - 当前执行环境未启动 Docker Desktop 引擎（`//./pipe/dockerDesktopLinuxEngine` 不存在），因此无法在本次会话完成
      `postgres` 与 `nq-app` 的运行态联调验证。

### 运行态验证更新（Docker Desktop 已启动）

- 执行时间：
    - `2026-02-25 16:38`（本地验证）
- 命令：
    - `docker compose up -d postgres`
    - `docker inspect -f "{{.State.Health.Status}}" nexusquant-postgres`
    - 后台启动应用：`mvn -q -f backend/pom.xml -pl nq-app -am spring-boot:run`
    - 探活：`Invoke-RestMethod http://localhost:8080/actuator/health`
- 结果：
    - `nexusquant-postgres` 状态为 `healthy`
    - `/actuator/health` 返回 `status=UP`，且 `components.db.status=UP`
    - 启动日志显示 `profile=local`、`Flyway validate/migrate` 正常、`Tomcat 8080` 启动成功
    - 验证后已停止后台任务，未长期占用终端进程

### 运行态复核更新（发现闭环阻塞）

- 执行时间：
    - `2026-02-25 17:15`（本地复核）
- 命令：
    - `mvn -q -f backend/pom.xml test`
    - `docker compose up -d postgres`
    - `docker inspect -f "{{.State.Health.Status}}" nexusquant-postgres`
    - 后台启动应用：`mvn -q -f backend/pom.xml -pl nq-app -am spring-boot:run`
    - 探活：`Invoke-RestMethod http://localhost:8080/actuator/health`
    - DB 计数核验（JDBC 连接 `jdbc:postgresql://localhost:5432/nexus_quant`）
- 结果：
    - 单测通过，`nexusquant-postgres` 为 `healthy`，`/actuator/health` 为 `UP`。
    - 闭环未跑通：`strategy_runs/orders/trades/ledger_entries/ledger_events/risk_events/audit_logs/positions/event_store`
      当前均为 `0`。
    - 应用日志持续报错：`GateBDemoStrategyRunner.ensureDemoStrategyRun` 插入 `strategy_runs` 时，`Instant` 参数触发
      PostgreSQL 错误：`Can't infer the SQL type to use for an instance of java.time.Instant`。
    - 环境核验发现本机 `5432` 同时存在 Docker 与本机 `postgres` 监听；容器内 `nexus_quant` 当前无业务表，且
      `pg_stat_activity` 未观察到应用连接。
- 坑与修复（待完成）：
    - 修复 `GateBDemoStrategyRunner` 的 `started_at` 入参类型（例如 `Timestamp.from(Instant.now())` 或显式 SQL type）。
    - 统一应用与验收脚本连接到同一 PostgreSQL 实例（例如容器改端口 `15432`，或停用本机 `postgres`）。

## 2026-02-26 - Gate B 运行态闭环阻塞修复（Instant/JDBC 绑定 + 连库指纹）

### 修复范围（最小变更）

- `GateBDemoStrategyRunner.ensureDemoStrategyRun`：
    - 将 `strategy_runs.started_at` 入参从 `Instant` 改为 `Timestamp.from(Instant.now())`，消除 PG 驱动类型推断失败。
    - 增加启动期一次性“连接指纹”日志：`datasource_url`、`inet_server_addr()`、`inet_server_port()`、`current_database()`。
    - 增加关键表存在性防呆：启动前检查 `orders` 表；若不存在则记录 ERROR 并阻断 demo runner。
- 端口与本地配置统一到 docker postgres：
    - `docker-compose.yml` 默认端口映射改为 `${NQ_DB_PORT:-15432}:5432`。
    - `application-local.yml` 默认 JDBC 改为
      `jdbc:postgresql://localhost:${NQ_DB_PORT:15432}/${NQ_DB_NAME:nexus_quant}`。
    - `.env` 与 `.env.example` 的 `NQ_DB_PORT` 改为 `15432`。
- 运行态追加修复（闭环继续阻塞时发现）：
    - `EventStoreAppender`：`payload_json` 改为 `CAST(? AS jsonb)`，修复 `event_store.payload_json(JSONB)` 写入失败。
    - `JdbcOrderRepository` / `JdbcTradeRepository` / `JdbcLedgerPostingRepository`：
        - 所有 `TIMESTAMPTZ` 入参统一改为 `Timestamp.from(...)`。
    - `JdbcAuditLogRepository` / `JdbcLedgerRiskAuditRepository`：
        - `detail_json` 统一改为 `CAST(? AS jsonb)`。
    - `JdbcLedgerPostingRepository`：
        - `ledger_events.payload_json` 改为 `CAST(? AS jsonb)`。

### 验证命令

- 构建与测试：
    - `mvn -q -f backend/pom.xml test`
- 数据库与健康检查：
    - `docker compose up -d postgres`
    - `docker inspect -f "{{.State.Health.Status}}" nexusquant-postgres`
    -
  `mvn --% -q -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=28080`
    - `Invoke-RestMethod http://localhost:28080/actuator/health`
- 表计数与 topic 核验：
  -
  `docker exec nexusquant-postgres psql -U postgres -d nexus_quant -c "SELECT 'strategy_runs' AS table_name, COUNT(*) AS cnt FROM strategy_runs UNION ALL SELECT 'orders', COUNT(*) FROM orders UNION ALL SELECT 'event_store', COUNT(*) FROM event_store UNION ALL SELECT 'trades', COUNT(*) FROM trades UNION ALL SELECT 'ledger_entries', COUNT(*) FROM ledger_entries UNION ALL SELECT 'ledger_events', COUNT(*) FROM ledger_events UNION ALL SELECT 'audit_logs', COUNT(*) FROM audit_logs UNION ALL SELECT 'risk_events', COUNT(*) FROM risk_events UNION ALL SELECT 'positions', COUNT(*) FROM positions;"`
  -
  `docker exec nexusquant-postgres psql -U postgres -d nexus_quant -c "SELECT topic, COUNT(*) AS cnt FROM event_store GROUP BY topic ORDER BY topic;"`

### 验证结果摘要

- `mvn test`：通过（0 失败）。
- `docker postgres`：`healthy`。
- `/actuator/health`：`UP`，`db=UP`。
- 启动日志连接指纹：
    - `datasource_url=jdbc:postgresql://localhost:15432/nexus_quant`
    - `server_addr=172.18.0.2`
    - `server_port=5432`
    - `current_database=nexus_quant`
- 表计数（启动后 20 秒）：
    - `strategy_runs=1`
    - `orders=1`
    - `event_store=107`
    - `trades=1`
    - `ledger_entries=2`
    - `ledger_events=2`
    - `audit_logs=8`
    - `risk_events=1`
    - `positions=1`
- `event_store` topic 证据：
    - `order.command.v1`、`order.event.v1`、`trade.event.v1`、`ledger.event.v1`、`risk.event.v1`、`position.event.v1` 均已出现。

## 2026-02-26 - Gate B 收尾验证与未完成项补齐（应用端口 18888）

### 本次目标

- 将应用默认端口切换为 `18888`。
- 完成 Gate B 清单中剩余项，尤其是“非终态订单重启恢复”验证。
- 以 `docs/current/GATE_CHECKLIST.md` 作为最终验收锚点补全勾选。

### 本次代码/配置改动

- `backend/nq-app/src/main/resources/application-local.yml`
    - `server.port` 默认值改为 `${NQ_APP_PORT:18888}`。
- 运行态阻塞补齐（本次复核确认）：
    - `JdbcOrderRepository`：`created_at/updated_at` 改为 `Timestamp.from(now)`。
    - `JdbcTradeRepository`：`trades.ts` 改为 `Timestamp.from(trade.ts())`。
    - `JdbcLedgerPostingRepository`：
        - `ledger_entries.ts` 与 `positions.updated_at` 改为 `Timestamp.from(...)`；
        - `ledger_events.payload_json` 改为 `CAST(? AS jsonb)`。
    - `JdbcAuditLogRepository` 与 `JdbcLedgerRiskAuditRepository`：
        - `audit_logs.detail_json` 改为 `CAST(? AS jsonb)`。

### 验证命令（本轮实跑）

- 构建测试：
    - `mvn -q -f backend/pom.xml test`
- 环境准备：
    - `docker compose up -d postgres`
    - `docker inspect -f "{{.State.Health.Status}}" nexusquant-postgres`
    -
  `docker exec nexusquant-postgres psql -U postgres -d nexus_quant -c "TRUNCATE TABLE event_store, ledger_events, ledger_entries, trades, risk_events, audit_logs, positions, orders, strategy_runs, accounts RESTART IDENTITY CASCADE;"`
- 非终态重启（关键未完成项）：
    - Phase1 启动（延后撮合，制造非终态）：
        - `set NQ_PAPER_MATCHING_INITIAL_DELAY_MS=30000`
        -
      `mvn -q -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=18888`
    - Phase1 观测并停机：
        - 命中 `orders=1 且 trades=0` 后停进程（订单状态 `SENT`）。
    - Phase2 重启（正常配置）：
      -
      `mvn -q -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local -Dspring-boot.run.arguments=--server.port=18888`
    - 探活：
        - `Invoke-RestMethod http://localhost:18888/actuator/health`
    - 计数核验：
      -
      `strategy_runs=1, orders=1, trades=1, ledger_entries=2, ledger_events=2, risk_events=1, audit_logs=10, positions=1, event_store=9`

### 关键结果与结论

- `18888` 端口启动成功，`/actuator/health` 返回 `UP` 且 `db=UP`。
- 连接指纹确认：`datasource_url=jdbc:postgresql://localhost:15432/nexus_quant`，服务端口为容器内 `5432`。
- 非终态重启验证通过：
    - 重启前：`orders=1(SENT), trades=0, ledger_entries=0`；
    - 重启后：`orders` 仍为 `1`（未重复创建），并推进到 `FILLED`；`trades=1`、`ledger_entries=2`（无重复写入）。
- `trace_id=trc-gateb-demo-001` 在 `orders/trades/ledger_entries/risk_events/audit_logs/event_store` 全链路可追踪。
- Gate B 清单未完成项已补齐，并已在 `docs/current/GATE_CHECKLIST.md` 与 `docs/gates/gate-b/GATE_B_CHECKLIST.md` 完成勾选。

## 2026-02-26 - Gate B Stretch（可选加分项）补齐

### 本次目标

- 补齐撤单能力：支持 `CancelOrder` 并进入 `CANCELLED` 终态。
- 补齐 LIMIT 基础撮合：仅在价格条件满足时成交。
- 补齐最小对账任务：周期输出 `ledger reconcile` 差异。

### 本次代码改动

- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/service/OrderCommandService.java`
    - 新增 `cancelOrder(CancelOrderRequest)` 编排入口。
    - 撤单路径固定为 `CANCEL_REQUESTED -> CANCELLED`，全程通过状态机推进。
    - 写入 `ORDER_COMMAND_V1` 与 `ORDER_EVENT_V1`，并补充撤单审计动作。
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/state/InMemoryOrderStateMachine.java`
    - 放开 `SENT -> CANCEL_REQUESTED` 合法迁移。
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/service/CancelOrderRequest.java`
    - 新增撤单请求模型（支持 `orderId` 或 `accountId + clientOrderId` 定位）。
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/core/service/CancelOrderResult.java`
    - 新增撤单结果模型（返回终态与幂等命中标识）。
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/PaperMatchingService.java`
    - 新增 LIMIT 价格判断：
        - `BUY`: `marketPrice <= limitPrice` 才成交。
        - `SELL`: `marketPrice >= limitPrice` 才成交。
    - 不满足时保持挂单并写 `LIMIT_NOT_REACHED` 审计。
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/LedgerReconcileScheduler.java`
    - 新增最小对账任务（`@Scheduled` + `reconcileOnce`）。
    - 差异写日志并写 `audit_logs(domain=LEDGER_RECONCILE)`。
-

`backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/repository/JdbcLedgerReconcileRepository.java`
- 新增账本聚合 vs 最新快照差异查询。

- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/port/LedgerReconcileRepository.java`
    - 新增对账仓储端口。
- `backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/model/LedgerReconcileDiff.java`
    - 新增对账差异模型。

### 本次新增/更新测试

- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/core/service/OrderCommandServiceTest.java`
    - 新增：撤单成功进入 `CANCELLED`。
    - 新增：已撤单订单再次撤单命中幂等。
- `backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/service/PaperMatchingServiceTest.java`
    - 新增：LIMIT 不满足价格不成交。
    - 新增：LIMIT 满足价格才成交。
- `backend/nq-scheduler/src/test/java/com/guidinglight/nexusquant/scheduler/service/LedgerReconcileSchedulerTest.java`
    - 新增：有差异写 `RECONCILE_DIFF_FOUND`。
    - 新增：无差异写 `RECONCILE_MATCH`。

### 验证命令与结果

- 编译验证（IDE）
    - 命令：`Build Project`
    - 结果：通过（`isSuccess=true`，无编译问题）。
- 测试验证（命令行）
    - 命令：`mvn -q -f backend/pom.xml test`
    - 结果：当前环境失败（`mvn` 不在 PATH，`CreateProcess error=2`）。
    - 处理：已用 IDE 编译 + 测试代码静态检查兜底，待本机补齐 Maven 后执行全量单测。

### 清单更新

- `docs/gates/gate-b/GATE_B_CHECKLIST.md`
    - Stretch 三项已勾选：
        - 支持撤单（CancelOrder）并进入 CANCELED 终态
        - 支持 LIMIT 基础行为（满足价格才成交）
        - 提供最小对账任务（ledger reconcile）并可输出差异

## 2026-02-26 - Maven 命令路径复验记录

### 本次目标

- 复验 `mvn -q -f backend/pom.xml test` 在当前环境是否通过。
- 将复验证据追加到 Gate B WORK，避免后续误判“命令本身失败”。

### 验证命令

- 仓库根目录执行（推荐）：
    - `D:\Tool\Maven\apache-maven-3.9.12\bin\mvn.cmd -q -f backend/pom.xml test`
- `backend` 项目上下文执行（本次复验）：
    - `D:\Tool\Maven\apache-maven-3.9.12\bin\mvn.cmd -q -f backend/pom.xml test`

### 验证结果

- 两次执行均返回 `exit code = 0`。
- 控制台仅出现 `SLF4J` provider 警告（`No SLF4J providers were found`），不影响测试结论。

### 结论

- 当前环境下，该验收命令可通过。
- 若终端对 `mvn` 解析不稳定（PATHEXT/PATH 差异），建议在验收脚本中固定使用 `mvn.cmd` 绝对路径。
