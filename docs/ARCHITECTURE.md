# nexus-quant V1 架构基线（ARCHITECTURE）

> 版本：v1.0（Gate A 基线）  
> 范围：仅 V1 数字货币量化系统 Gate A（核心内核），不接交易所、不实现策略、不做前端  
> 技术栈冻结：Java 21 / Spring Boot 3.x / PostgreSQL / Kafka / Redis / Docker Compose

---

## 1. 目标与非目标

### 1.1 目标（V1 必须闭环）
- 闭环链路：行情 → 信号 → 风控 → 下单 → 成交 → 仓位 → 资金账本 → 对账 → 回放 → 可观测 → 审计 → 可恢复
- 初期实盘资金：≤ 100,000 USDT（上线前置风控与熔断）
- 频率演进：日频 → 分钟级 → 秒级 → 高频（架构预留，V1 不实现高频）

### 1.2 非目标（Gate A 明确不做）
- 不接入 OKX/Binance（Gate B 才做）
- 不实现任何策略逻辑（只定义接口占位）
- 不实现 Web 控制台（只保留最小启动骨架/可选 Swagger）
- 不实现分布式与 K8s（本地 docker-compose）

---

## 2. 总体架构概览

### 2.1 分层架构（Mermaid）
```mermaid
flowchart TB
  subgraph Adapter[Adapter 层（Gate B+）]
    A1[OKX Adapter]
    A2[Binance Adapter]
  end

  subgraph Core[Core 内核（Gate A）]
    O[Order Engine / 状态机]
    R[Risk Gate]
    P[Position Engine]
    L[Ledger Engine]
    X[Reconciliation Engine（占位）]
    S[Strategy Runtime（接口占位）]
  end

  subgraph Infra[Infra]
    K[(Kafka)]
    PG[(PostgreSQL)]
    RD[(Redis)]
    OBS[Observability]
  end

  Adapter --> K
  Adapter --> Core
  Core --> PG
  Core --> K
  Core --> OBS
