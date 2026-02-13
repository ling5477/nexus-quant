# Gate A 验收清单（个人开发版）

> 项目：nexus-quant  
> Gate：A（核心内核 + auth/gateway 骨架 + 启动载体/横切模块）  
> 目标：冻结核心模型与契约，保证闭环“正确性底座”可被实现与验证

---

## 0. 基础项（仓库与构建）

- [ ] mono-repo 目录结构存在（backend/research/frontend/infra/docs）
- [ ] `README.md` 写清楚：范围、结构、如何启动 Postgres、文档入口
- [ ] `docker-compose.yml` 至少能启动 PostgreSQL（并带 healthcheck）
- [ ] 统一包名基线：`com.guidinglight.nexusquant`
- [ ] 统一模块命名：`nq-*`

---

## 1. 文档冻结项（必须齐全）

- [ ] `docs/ARCHITECTURE.md`：架构基线（域模型、状态机、幂等、账本、恢复）
- [ ] `docs/MODULES.md`：模块边界与依赖方向（含 `nq-app` 启动载体）
- [ ] `docs/CONTRACTS.md`：事件契约（Envelope / Topic / 去重字段）
- [ ] `docs/EVOLUTION_RULES.md`：事件版本演进规则（兼容/破坏性/迁移流程）
- [ ] `docs/NUMERIC_POLICY.md`：数值精度/舍入/交易所 tickSize 对齐策略
- [ ] `docs/DB_SCHEMA.md`：表清单、关键字段、索引与一致性口径
- [ ] `docs/RECOVERY_RUNBOOK.md`：恢复/回放流程与校验口径
- [ ] `docs/DECISIONS.md`：ADR 记录（至少包含本 Gate 的关键决策）
- [ ] `docs/ROADMAP.md`：v1→v3 里程碑边界

---

## 2. 模块清单（Gate A 只要求骨架与依赖方向）

> 说明：此处仅验收“模块清单与依赖方向是否明确”，不要求实现交易所连接与真实策略。

- [ ] 启动载体：`nq-app`
- [ ] 核心内核：`nq-core`、`nq-ledger`、`nq-risk`
- [ ] 接入骨架：`nq-gateway`、`nq-auth`、`nq-security`
- [ ] 横切能力：`nq-observability`、`nq-config`、`nq-scheduler`、`nq-infra`
- [ ] 契约模块：`nq-contracts`、`nq-common`
- [ ] 适配占位：`nq-adapter-api`、`nq-adapter-okx`、`nq-adapter-binance`（Gate A 只冻结接口）

---

## 3. 核心正确性要求（文档必须写清楚并可被测试）

- [ ] 严格订单状态机（非法跃迁必须拒绝）
- [ ] 幂等键 `client_order_id` 贯穿：订单创建、事件、账本引用
- [ ] 账本可重算：ledger_entries 能重算余额并做平衡校验
- [ ] traceId 贯穿：HTTP → EventEnvelope → 日志
- [ ] 恢复/回放：从事件/账本重建投影表的流程与校验口径明确

---

## 4. 明确“不做”的范围（Gate A 禁止项）

- [ ] 不要求接入 OKX/Binance 网络连接（仅冻结接口）
- [ ] 不要求实现策略逻辑/因子/回测
- [ ] 不要求实现前端页面
- [ ] 不要求上线 Kafka/Redis（可占位）

