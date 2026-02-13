
---

## 3) `docs/GATE_A_CHECKLIST.md`

```md
# Gate A 验收清单（个人开发版）

> 项目：nexus-quant  
> Gate：A（核心内核 + auth/gateway 骨架）  
> 目标：冻结核心模型与契约，保证闭环“正确性底座”

---

## 0. 基础项（仓库与构建）

- [ ] mono-repo 目录结构存在（backend/research/frontend/infra/docs）
- [ ] backend 为 Maven 多模块父工程（`backend/pom.xml`）
- [ ] 父 POM 冻结版本（JDK/Boot/Cloud/PG/Flyway/Kafka/Redis/JWT）
- [ ] docker-compose.yml 至少能启动 PostgreSQL
- [ ] README 写清楚：如何启动 PG、如何跑测试、如何运行 auth/gateway（最小）

验收命令：
- [ ] `cd backend && mvn -q test` 通过

---

## 1. Auth/Gateway（Gate A 最小骨架）

### 1.1 nq-auth
- [ ] 存在 `nq-auth` 模块（包名 `com.guidinglight.nexusquant.auth`）
- [ ] 提供最小登录接口 `POST /auth/login`
- [ ] 能签发 JWT（包含 sub/username/roles/iat/exp）
- [ ] Flyway DDL 包含 `users/roles/user_roles`
- [ ] 登录/鉴权关键操作写入 `audit_logs`（至少登录成功/失败）

### 1.2 nq-gateway
- [ ] 存在 `nq-gateway` 模块（包名 `com.guidinglight.nexusquant.gateway`）
- [ ] Gateway 能校验 JWT（Bearer）
- [ ] traceId 生成/透传（`X-Trace-Id`），下游服务日志能看到 traceId（MDC）
- [ ] 路由配置存在（指向 nq-auth / nq-api 占位）

---

## 2. 统一域模型（Domain）

- [ ] 存在实体：Order / Trade / Position / Account / LedgerEntry / RiskEvent / AuditLog
- [ ] 数值类型统一：BigDecimal（明确 scale/rounding 策略）
- [ ] 时间统一：Instant（UTC）
- [ ] Order 含 `clientOrderId`（幂等键）
- [ ] Trade 含 `tradeId`（去重键）
- [ ] LedgerEntry 含 `entryId`（去重键）

---

## 3. 订单状态机（State Machine）

- [ ] 状态枚举齐全：NEW/VALIDATED/SUBMITTING/ACKED/PARTIALLY_FILLED/FILLED/CANCEL_REQUESTED/CANCELLED/REJECTED/FAILED
- [ ] 状态机 transition 规则集中管理（禁止随意 setStatus）
- [ ] 明确竞态规则：撤单与迟到成交（Trade 为最终事实）

单测：
- [ ] 合法路径测试覆盖
- [ ] 非法路径测试覆盖（必须拒绝/抛错）
- [ ] 撤单后迟到成交用例（顺序模拟即可）
- [ ] 乱序重复成交用例（顺序模拟即可）

---

## 4. 幂等与去重（Idempotency）

- [ ] DDL：orders UNIQUE(account_id, client_order_id)
- [ ] 代码：重复 clientOrderId 行为定义清晰（返回既有或拒绝）
- [ ] Trade 去重：重复 tradeId 不产生重复副作用
- [ ] LedgerEntry 去重：重复 entryId 不产生重复副作用

单测：
- [ ] 重复 PlaceOrderCommand 测试
- [ ] 重复 Trade.Filled 测试
- [ ] 重复 Ledger.EntryCreated 测试（或 ledger 记账去重测试）

---

## 5. 资金账本（Ledger）

- [ ] 余额可由 ledger_entries 聚合重算（提供方法/服务）
- [ ] 成交记账规则明确（含手续费）
- [ ] 借贷平衡策略明确（double-entry 或等价校验）
- [ ] refType 预留：TRADE/FEE/TRANSFER/FREEZE/UNFREEZE

单测：
- [ ] 单笔成交账本平衡
- [ ] 多笔成交累计一致
- [ ] 重复事件不破坏余额

---

## 6. PostgreSQL DDL（Flyway）

- [ ] Flyway 迁移目录存在：`db/migration`
- [ ] `V1__init.sql` 存在且可执行
- [ ] auth 表：users/roles/user_roles
- [ ] core 表：accounts/orders/trades/positions/ledger_entries/risk_events/audit_logs
- [ ] 关键索引与约束齐全（至少 orders 唯一约束）

验证：
- [ ] 本地可启动 PG 并完成 migrate（说明写在 README）

---

## 7. Kafka 契约（Gate A：规范 + 常量）

- [ ] `docs/CONTRACTS.md` 完整
- [ ] Topic 列表与 key 规则明确
- [ ] Envelope 标准明确（含 trace_id）
- [ ] 幂等/去重/版本演进规则明确
- [ ] `nq-contracts` 中有 topic/event type 常量与 DTO

---

## 8. 恢复能力（Recovery）

- [ ] 提供 `RecoveryService.rebuild()`（最小实现）
- [ ] 能从 DB 恢复 orders
- [ ] 能从 trades+ledger_entries 重建 positions 与余额（或明确策略）
- [ ] 输出恢复报告/统计（最小）

验证：
- [ ] 有测试或可运行用例模拟恢复

---

## 9. 文档与 ADR

- [ ] `docs/ARCHITECTURE.md` 完整（含 Mermaid 图）
- [ ] `docs/CONTRACTS.md` 完整（含 auth + trace 规范）
- [ ] `docs/DECISIONS.md` 至少包含 8 条 ADR（范围冻结、PG+Flyway、Kafka契约、幂等键、Ledger、Trade最终事实、Auth/Gateway最小骨架等）
- [ ] 本清单已逐项勾选

---

## Gate A 结论

- [ ] Gate A 通过（全部项完成）
- [ ] Gate A 不通过（阻塞项如下）：
  - [ ] 状态机/幂等/账本/DDL/恢复/文档 任一缺失
