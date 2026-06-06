# DB Schema Governance Plan

任务：NQ-DB-SCHEMA-GOVERNANCE-REVIEW-BATCH-1 后续计划
日期：2026-06-06
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ。
原则：本计划只定义后续 schema governance 工作单，不代表本轮已经修改表结构，也不代表 GateK 进入实现阶段。

## 1. 总体原则

- Batch 2-5 必须单独开工，不能合并到本轮审查任务。
- 所有 schema 变更都必须新增 Flyway migration，禁止修改历史 migration。
- 任何 migration 都必须包含 `COMMENT ON TABLE` 或 `COMMENT ON COLUMN`，并同步更新 `docs/current/DB_SCHEMA.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`。
- 不引入 AI、DH integration、LIVE trading、真实下单、真实撤单、真实交易所私有链路。
- 不把 retention purge、archive、disable、revoke 写成普通删除接口。

## 2. Batch 2：comment-only migration

### 允许范围

- 新增一个 comment-only Flyway migration。
- 只修改 PostgreSQL `COMMENT ON TABLE` 与 `COMMENT ON COLUMN`。
- 清理长期业务表中的 `GateE/GateF/GateH/GateI/RC1/第一版/最小口径` 等阶段语义。
- 保留必要业务边界，例如 Paper only、SIM/LIVE 隔离、JSONB 不保存密钥/token/cookie、稳定性检查不是最终 freeze 验收。

### 禁止事项

- 禁止新增、删除、重命名字段。
- 禁止新增或修改 CHECK、INDEX、FK、UNIQUE。
- 禁止改业务代码、Repository、API、DTO、前端、Python、部署。
- 禁止把 GateK-PLAN 写成 GateK 进入实现阶段。

### 验收标准

- `COMMENT ON` 语句可重复回放于全新数据库。
- `rg -n "GateE|GateF|GateH|GateI|RC1|第一版|最小口径" backend/nq-infra/src/main/resources/db/migration` 仅命中历史 migration 和必要边界说明，新增 migration 不引入新的阶段化长期注释。
- `mvn -f backend/pom.xml test` 通过，或说明 comment-only 变更为何只需 Flyway 启动验证。
- `docs/current/DB_SCHEMA.md` 更新实际治理结果，不写未执行验证为通过。

### 回滚方式

- 新增一个反向 comment-only migration，把注释恢复到 Batch 2 前版本。
- 不回滚历史 migration，不使用 `git reset --hard`。

### 必须单独开工

Batch 2 必须单独提交，不能与字段新增、Repository 过滤、retention job 合并。

## 3. Batch 3：配置/主数据表字段和约束补齐

### 允许范围

- 新增 Flyway migration 补齐低风险字段或约束。
- 候选字段：
  - `roles.updated_at`
  - `accounts.updated_at`
  - `positions.created_at`
  - `exchange_account_credentials.revoked_by`
  - `exchange_account_credentials.revoke_reason`
  - `research_configs.status`
  - `backtest_configs.status`
- 候选约束：
  - `accounts.status IN ('ACTIVE','DISABLED')`
  - `instrument_catalog.instrument_type IN ('SPOT')`
  - `instrument_catalog.status IN ('ACTIVE','DISABLED','DELISTED')`
  - `risk_events.decision` 与 `risk_events.severity` 的业务枚举
- 字段新增必须给出默认值、回填策略、锁表影响和兼容性说明。

### 禁止事项

- 禁止给订单、成交、账本、审计、风控事件、回测/Paper 事实表批量加 `deleted_at`。
- 禁止让 `marketdata_bars`、心跳、曲线、日报等时序/快照表使用 soft delete。
- 禁止修改历史 migration。
- 禁止改 Repository 行为，除非字段已经落库且本批范围明确包含兼容读取。

### 验收标准

- Flyway 从空库可完整迁移到最新版本。
- 后端 `mvn -f backend/pom.xml test` 通过。
- 新增字段和约束均有 `COMMENT ON COLUMN`。
- JSONB 或敏感相关字段注释明确不保存密钥、token、cookie、exchange secret。
- 对现有数据的默认值和回填结果有验证 SQL。

### 回滚方式

- 字段新增回滚通过后续 migration 标记废弃或删除字段前先停写；约束回滚通过后续 migration `DROP CONSTRAINT`。
- 对大表字段不做同步大事务回滚；如涉及大表，先单独设计 expand/contract。

### 必须单独开工

Batch 3 不能与 Batch 4 Repository 默认过滤合并。先落 schema，再改代码。

## 4. Batch 4：Repository 查询过滤、逻辑删除/归档接口、测试

### 允许范围

- 仅在 Batch 3 已新增 `status` 或归档字段后，修改对应 Repository 默认查询。
- 候选行为：
  - `research_configs.status=ARCHIVED` 后默认列表过滤或显式展示。
  - `backtest_configs.status=ARCHIVED` 后默认列表过滤或显式展示。
  - `instrument_catalog.status=DISABLED/DELISTED` 后前端和 API 不默认参与可交易筛选。
  - `exchange_accounts.status=DISABLED` 和 `exchange_account_credentials.verification_status=REVOKED` 保持已有状态语义。
- 补充 JUnit / repository integration / service regression tests。

### 禁止事项

- 禁止新增真实下单、撤单路径。
- 禁止把归档接口用于删除事实、审计、订单、成交、账本、风控、Paper run。
- 禁止改前端展示，除非本批工作单明确包含页面验收。
- 禁止没有 migration 就先写 `deleted_at IS NULL`。

### 验收标准

- 后端 `mvn -f backend/pom.xml test` 通过。
- 新增或修改的 Repository 查询有测试覆盖。
- 归档/停用/撤销状态流转有幂等测试和非法状态测试。
- API 文档明确默认过滤和显式查询语义。

### 回滚方式

- 通过后续代码提交恢复查询条件，或通过状态字段保留但不默认过滤。
- 不删除已新增字段；字段弃用需单独 migration 和兼容窗口。

### 必须单独开工

Batch 4 必须在 Batch 3 后执行，不能与 comment-only 或 retention purge 合并。

## 5. Batch 5：大表 retention policy

### 允许范围

- 为高增长表制定 retention policy 文档和可审计清理脚本/任务。
- 候选表：
  - `marketdata_bars`
  - `marketdata_ingestion_runs`
  - `event_store`
  - `account_snapshots`
  - `sim_pnl_snapshots`
  - `equity_curve_snapshots`
  - `position_curve_snapshots`
  - `paper_run_schedule_fires`
  - `paper_run_heartbeats`
  - `paper_run_daily_reports`
  - `paper_run_stability_checks`
- 清理条件必须按业务时间、run id、dataset id 或 scope 分批，并输出 dry-run 统计。

### 禁止事项

- 禁止对 `orders`、`trades`、`ledger_entries`、`audit_logs`、`risk_events`、`paper_trading_runs`、`paper_trading_orders`、`paper_trading_trades` 做 retention purge。
- 禁止无 dry-run 的批量删除。
- 禁止直接清理生产数据。
- 禁止把 retention purge 写成用户可随意触发的普通删除接口。

### 验收标准

- 每张 retention 表有留存周期、清理条件、索引依赖、批大小、dry-run SQL、执行 SQL、审计日志策略。
- 清理脚本必须支持 dry-run 和实际执行分离。
- 对 `marketdata_bars` 等大表必须确认使用时间索引或 scope+time 索引，避免全表扫描和长事务。
- 后端测试或脚本测试通过；如只是文档计划，必须明确未执行代码验证。

### 回滚方式

- 清理前必须先生成备份或导出清单。
- 对已物理清理的数据，只能通过备份恢复或重新 ingest/rebuild，不能依赖 soft delete 回滚。
- retention job 首次运行必须小窗口、可观测、可停止。

### 必须单独开工

Batch 5 必须单独开工。它涉及物理清理风险，不能与 schema 字段补齐、Repository 过滤或业务功能合并。

## 6. 批次依赖关系

```text
Batch 2 comment-only migration
  -> Batch 3 fields/check constraints
  -> Batch 4 Repository filtering and status APIs

Batch 5 retention policy can be planned in parallel,
but execution must be separate and must not run before dry-run evidence exists.
```

## 7. 默认验证命令

后端 schema / Repository 批次：

```powershell
mvn -f backend/pom.xml test
```

文档或 comment-only 批次补充：

```powershell
git diff --check
rg -n "<禁写状态短语>" docs/current
```

如只改文档，不运行 Maven，必须在 `docs/current/TESTING.md` 或当轮报告中写清未运行原因，不能写成通过。

## 8. 风险说明

- 当前最主要风险不是缺少 `deleted_at`，而是误把所有表统一套 soft delete，破坏事实、审计、交易、账本、回测和 Paper run 追溯。
- `deleted_at` 只适合少数用户可编辑目录型数据；NQ 当前更适合 `DISABLED / REVOKED / ARCHIVED / RETENTION_PURGE` 的组合策略。
- retention purge 是物理删除，必须比普通 schema 变更更谨慎，必须带 dry-run、备份或可重建路径。
