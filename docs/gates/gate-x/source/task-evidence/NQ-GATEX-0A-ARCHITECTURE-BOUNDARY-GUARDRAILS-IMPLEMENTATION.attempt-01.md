# NQ-GATEX-0A architecture boundary guardrails implementation — attempt-01

## 基线与范围

- Starting HEAD：`c93e728936fd05d0f8d5b63c33b31c1786d926f7`；`origin/dev` 同一 commit。
- Exact-head CI baseline：`NQ CI Baseline` run `31316720858`，`completed / success`（完成 / 成功）。
- Authority before：GateX=`IN_PROGRESS|NOT_FROZEN`；work batch=`GateX-0A / NOT_STARTED`；LIVE=`DISABLED`。
- 仅修改 backend 中与 Strategy/Trading execution contract、audit port ownership、ArchUnit guardrail 直接相关的代码和测试；未修改 API、migration、schema、订单状态机、风险规则、ledger、frontend、research、scripts、CI、deploy、LIVE、AI 或 DH runtime。

## 已确认的错误依赖与修复

### Strategy / Trading

- 修改前：`strategy.domain.port.StrategyExecutionGateway` 直接引用 `trading.application.PlaceOrderRequest` 与 `PlaceOrderResult`，Strategy contract 反向依赖 Trading application DTO。
- 修改后：Strategy 自有 `StrategyExecutionIntent`、`StrategyExecutionResult` 与 `StrategyExecutionGateway.execute(...)`；`OrderCommandStrategyExecutionGateway` 是唯一 Trading application bridge，逐字段映射到既有 `PlaceOrderRequest`，再把 `PlaceOrderResult` 映射回 Strategy result。
- Execution contract decision：保持同一 `OrderCommandService.placeOrder(...)` 调用链；不移动 `PlaceOrderCommand`，不新增订单模型、Maven module、API 或第二条下单路径。

### Audit ownership

- 修改前：`AuditLogRepository` 位于 `trading.domain.port`，但 Trading、Validation Review、Scheduler 与 Infra 均消费该跨域能力。
- 修改后：端口迁至 `audit.domain.port`；JDBC implementation、SQL、事务传播、detail schema 与审计写入时机保持不变。
- 静态结果：Strategy → Trading application/infra 禁止 import=`0`；旧 Trading-owned audit port import=`0`；新 audit port reference files=`32`。

## 涉及类与 package

- 新增：`strategy.domain.port.StrategyExecutionIntent`、`StrategyExecutionResult`、`audit.domain.port.AuditLogRepository`。
- 修改：`StrategyExecutionGateway`、`StrategyManualTriggerService`、`OrderCommandStrategyExecutionGateway` 及其相关单元测试。
- package move consumers：`nq-core`、`nq-infra`、`nq-scheduler` 中 32 个 production/test consumers 仅更新 import。
- 删除旧 owner：`trading.domain.port.AuditLogRepository`。

## ArchUnit guardrails

在既有 `PackageBoundaryArchTest` 中增加或增强：

1. Strategy 不得依赖 Trading application/infra/infrastructure。
2. Validation/Validation Review 不得依赖旧 Trading-owned audit port。
3. Domain 不得依赖 Spring JDBC、infra、controller 或 API web。
4. 除 `OrderCommandStrategyExecutionGateway` 外，Trading application 不得依赖 Strategy port。
5. `OrderCommandStrategyExecutionGateway` 必须实现 `StrategyExecutionGateway`。

Negative proof：隔离 fixture `InvalidStrategyTradingDependency` 故意引用 `PlaceOrderRequest`；JUnit 断言规则抛出 `AssertionError`，证明规则可阻断错误依赖而非空跑。

## 验证结果

| 验证 | 结果 | 说明 |
| --- | --- | --- |
| `mvn -f backend/pom.xml -pl nq-core -am test` | PASS（通过） | 419 tests，0 failures，0 errors，3 skipped |
| focused `PackageBoundaryArchTest` | PASS | 8 tests，0 failures，0 errors；包含 negative fixture |
| CI-equivalent PostgreSQL precondition | PASS | 临时 PostgreSQL 17.7；Flyway 35；legacy fixture=`1`、exchange account=`0`、credentials=`0` |
| 三个 local Spring context tests | PASS | 3 tests，0 failures，0 errors |
| `mvn -f backend/pom.xml test` | PASS | 23 modules 全部成功；1277 tests，0 failures，0 errors，18 existing skipped |
| static boundary checks | PASS | 两类 forbidden import 均为 0 |
| `check-current-authority.ps1` | PASS | 现有 authority 保持不变；errors=`0` |
| `git diff --check` | PASS | whitespace errors=`0` |

首轮本地全量 Maven 因 `localhost:5432` 未运行出现 3 个 context errors；启动既有本地容器后确认其中两个通过，第三个因缺 CI legacy account fixture 失败。随后使用临时、可删除 PostgreSQL 容器严格复刻 CI Flyway/fixture 前置，目标测试与全量回归均通过；未将环境失败写成代码通过。

## 行为兼容性与自审

- API：无变更。
- Database：无 migration/schema/SQL 变更；临时测试库已隔离。
- Spring assembly：完整 context 与全量 backend test 通过，无 Bean 冲突。
- Trading：订单状态机、幂等、risk、ledger、audit 顺序与 `OrderCommandService.placeOrder(...)` 调用保持不变。
- P0：0。
- P1：架构 ownership P1 已关闭；治理 authority sync 仍有 1 个阻断项。
- P2：0。
- P3：0。

## Authority 结论

Canonical governance contract 允许普通实现从 `NOT_STARTED` 进入 `IMPLEMENTED|SELF_REVIEWED`，但该状态强制后续动作类型为 `COMMIT_AND_PUSH`。候选 `NQ-GATEX-0B-STAGE-SEMANTIC-NAMING-CLEANUP-IMPLEMENTATION` 被分类为 `IMPLEMENTATION`，映射验证为 `False`；`NQ-GATEX-0A-COMMIT-AND-PUSH` 映射为 `True`。

本任务 allowlist 未授权同步仍声明 GateX-0A implementation 的 `ROADMAP.md` 与 current README。仅修改 `STATUS.md` 会形成 current summary conflict。因此本 attempt 不修改 current authority，不声称 GateX-0B 已获授权，最终治理结论为 `BLOCKED / AUTHORITY_MAPPING_MISMATCH`。代码与测试实现保持为可审查工作区 diff，禁止范围触达为 0。
