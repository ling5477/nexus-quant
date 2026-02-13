# NexusQuant（nexus-quant）

NexusQuant 是一套面向 **数字货币 → 美股 → A股** 的可扩展量化交易系统工程骨架。  
当前仓库处于 **v1.0 Gate A（架构/契约/模型冻结）** 阶段：以“闭环正确性底座”为第一目标，**不实现交易所接入与真实策略**。

## 1. 当前阶段（Gate A）范围

Gate A 交付物聚焦：
- 统一域模型与严格状态机（Order / Trade / Position / Account / Ledger）
- 事件契约冻结（Envelope、Topic、幂等、版本演进规则）
- 账本可重算与最小恢复流程（Recovery/Rebuild 的设计与测试口径）
- auth/gateway 骨架（JWT + traceId 透传骨架）
- 可观测性规范（日志/指标/trace 字段与约定）
- 数据库 DDL 设计说明（PostgreSQL + Flyway 迁移规划）

> 说明：代码实现由你使用 Codex 生成；本次更新仅补充文档与仓库“可开工”的规范材料。

## 2. 目录结构（约定）

```
.
├─ backend/                # Java 多模块（Gate A 可先空骨架）
├─ research/               # Python 研究/回测/因子（可选）
├─ frontend/               # 前端（可选）
├─ infra/                  # 部署/环境（compose/k8s 等，可选）
├─ docs/                   # 架构/契约/决策/验收清单（当前重点）
├─ docker-compose.yml      # 本地依赖（PG/Kafka/Redis，v1 先 PG）
└─ AGENTS.md               # Codex 开发指引（本仓库约束）
```

## 3. “启动模块”约定（仅文档层）

本仓库采用“模块 + 启动载体”的结构：
- **nq-app（启动模块）**：唯一 Spring Boot 入口（v1 推荐先单体），装配 core/ledger/risk/gateway 等模块。
- Gate A 只要求：启动骨架、配置分层、健康检查与最小可观测规范；**不要求连接交易所或跑真实策略**。

模块清单与边界请见：`docs/ARCHITECTURE.md` 与 `docs/MODULES.md`。

## 4. 本地环境（仅基础依赖）

启动 PostgreSQL（Gate A 最低要求）：
```bash
docker compose up -d postgres
```

停止：
```bash
docker compose down
```

> Kafka/Redis 在 Gate A 可保持占位（compose 中预留但可不启）。

## 5. 文档入口

- 架构基线：`docs/ARCHITECTURE.md`
- 模块边界与依赖：`docs/MODULES.md`
- 事件契约：`docs/CONTRACTS.md`
- 事件演进规则：`docs/EVOLUTION_RULES.md`
- 数值精度策略：`docs/NUMERIC_POLICY.md`
- DB 结构说明：`docs/DB_SCHEMA.md`
- 恢复/回放 Runbook：`docs/RECOVERY_RUNBOOK.md`
- 决策记录（ADR）：`docs/DECISIONS.md`
- Gate A 验收清单：`docs/GATE_A_CHECKLIST.md`
- Roadmap：`docs/ROADMAP.md`

## 6. 重要约束（强制）

- **碰钱/订单/风控/恢复的链路必须可审计、可回放、可重算。**
- **严格状态机**：不得任意 setStatus。
- **幂等**：`client_order_id` 贯穿全链路。
- **traceId**：HTTP → 事件 Envelope → 日志必须贯穿。
- **数值精度**：统一 scale/rounding（见 `docs/NUMERIC_POLICY.md`）。
