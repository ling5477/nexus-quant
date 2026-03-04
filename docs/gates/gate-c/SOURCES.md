# docs/gates/gate-c/SOURCES.md
# Gate C 参考依据（权威来源清单）

> 规则：本文档只收录“官方文档 / 官方仓库 / 权威参考文档”。
> GateC 文档中的接口、WS 通道、约束（如 ping/pong、限连、幂等策略、outbox）均应能在下列依据中找到出处。

---

## 1) OKX（官方 API 文档）

### 1.1 OKX API v5 总入口（REST + WS）
```text
OKX API v5 Docs (English)
https://www.okx.com/docs-v5/en
```
---

### 1.2 REST：下单/撤单/查单/挂单列表（orders-pending）
```text
OKX REST - Place order: POST /api/v5/trade/order
OKX REST - Cancel order: POST /api/v5/trade/cancel-order
OKX REST - Order details: GET /api/v5/trade/order
OKX REST - Order List (incomplete orders): GET /api/v5/trade/orders-pending
来源（同一页包含以上 endpoints 示例与说明）：
https://www.okx.com/docs-v5/en/#order-book-trading-trade
```

--- 

### 1.3 REST：请求超时控制（expTime）
```text
OKX REST - Transaction Timeouts / expTime header
https://www.okx.com/docs-v5/en/#order-book-trading-trade-transaction-timeouts
```
---

### 1.3.1 REST：模拟盘请求头（x-simulated-trading）
```text
OKX REST - Demo trading via x-simulated-trading: 1
https://www.okx.com/docs-v5/en
（在 REST 请求示例/说明中可检索 simulated trading header）
```
---

### 1.4 WS（私有）：account / positions / balance_and_position
```text
OKX WebSocket Private channels:
- account
- positions
- balance_and_position
来源（同一 docs 域下 WebSocket 私有通道说明）：
https://www.okx.com/docs-v5/en
（在 WebSocket -> Private 下可检索 channel: account/positions/balance_and_position）
```
---

### 1.5 WS：ping/pong 与连接限制（orders/account/positions/balance_and_position）
```text
OKX WebSocket - ping/pong keepalive + connection count limit (orders/account/positions/balance_and_position)
https://www.okx.com/docs-v5/en
（在 WebSocket 连接说明中可找到 keepalive 与限连条款）
```
---

## 2) Binance（官方文档仓库：Spot APIs & Streams）

### 2.1 官方仓库入口（声明“本仓库内容为官方支持文档”）
```text
binance/binance-spot-api-docs (Official Documentation)
https://github.com/binance/binance-spot-api-docs
```
---

### 2.2 Spot REST（下单/撤单/查单）
```text
Spot REST API - rest-api.md
- New order (TRADE): POST /api/v3/order
- Cancel order (TRADE): DELETE /api/v3/order
- Query order (USER_DATA): GET /api/v3/order
来源（原始文件）：
https://raw.githubusercontent.com/binance/binance-spot-api-docs/master/rest-api.md
```
---

### 2.3 Spot 用户数据流（订单/账户推送事件语义）
```text
Spot User Data Streams - user-data-stream.md
- Account Update: outboundAccountPosition
- Order Update: executionReport
来源（原始文件）：
https://raw.githubusercontent.com/binance/binance-spot-api-docs/master/user-data-stream.md
```
---

## 3) Transactional Outbox / CDC（Debezium 官方）

### 3.1 Outbox Event Router（outbox pattern 的官方解释 + 表结构假设）
```text
Debezium Outbox Event Router (SMT)
https://debezium.io/documentation/reference/2.6/transformations/outbox-event-router.html
```
---

## 4) 可观测性（Spring Boot / OpenTelemetry 官方）

### 4.1 Spring Boot Actuator Tracing（Micrometer Tracing 自动配置）
```text
Spring Boot Reference - Actuator Tracing
https://docs.spring.io/spring-boot/reference/actuator/tracing.html
```
---

### 4.2 OpenTelemetry（Spring Boot Starter 零侵入采集）
```text
OpenTelemetry Java Instrumentation (incl. Spring Boot starter)
https://opentelemetry.io/docs/languages/java/instrumentation/

OpenTelemetry Spring Boot Starter - Getting started
https://opentelemetry.io/docs/zero-code/java/spring-boot-starter/getting-started/
```
---

