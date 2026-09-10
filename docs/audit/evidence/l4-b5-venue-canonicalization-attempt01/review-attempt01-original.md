# 前次 Independent Review 原文

以下保留本任务开始前、同一会话已给出的审查结论正文。它属于旧候选，不因整改改变。原始 bypass 进程日志、探针源码与起止工作树清单逐字节归档于 `backend/nq-app/target/b5-venue-remediation/review-attempt01/`；规范化事实与原始文件 SHA-256 见 `original-bypass.json`。

````text
任务分类：HIGH_RISK / INDEPENDENT_CORRECTNESS_REVIEW / REVIEW_ONLY
Review mode：严格 REVIEW_ONLY

Starting HEAD：`86c8ad84542636364f6c21e78bc292a323cbdff7`
Candidate fingerprint start/end：`b916029577df1bf1f46a27037edcb0eec79946705fdb8fd91c589e0a2d5ee6b0`
Candidate unchanged：是；30-file implementation manifest 无 hash mismatch，stage=0，commit/push=NONE。

发现 P1：`venue="okx"` 可绕过 V49 authority。

- API 边界仅要求 venue 非空：[OrderSubmitRequest.java](/E:/Project/nexus-quant-gateaudit/backend/nq-api/src/main/java/com/guidinglight/nexusquant/trading/api/web/OrderSubmitRequest.java:29)。
- 下单创建与 arm 都用大小写敏感的 `"OKX".equals(...)`：[OrderCommandWriteService.java](/E:/Project/nexus-quant-gateaudit/backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandWriteService.java:124)、[OrderCommandService.java](/E:/Project/nexus-quant-gateaudit/backend/nq-core/src/main/java/com/guidinglight/nexusquant/trading/application/OrderCommandService.java:126)。
- Gateway 却将 venue 规范化为大写并路由到 OKX adapter：[AdapterBackedTradingVenueGateway.java](/E:/Project/nexus-quant-gateaudit/backend/nq-scheduler/src/main/java/com/guidinglight/nexusquant/scheduler/service/AdapterBackedTradingVenueGateway.java:268)。

独立实测：真实 Spring、PreTradeRiskService、PG16.15/V49、Synthetic Venue，以普通 `PlaceOrderRequest(venue="okx")` 调用真实 `OrderCommandService`，结果为：

```text
venue=okx
status=ACCEPTED
ordinary_place_authorities=0
Synthetic Venue POST /api/v5/trade/order = 1
```

`placeRequests` 仅在该 POST 路径递增：[B2SyntheticVenueMain.java](/E:/Project/nexus-quant-gateaudit/backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/B2SyntheticVenueMain.java:111)。

V49 schema/约束：表结构与 PostgreSQL one-shot/逆转/并发约束测试通过，但实际 sender boundary 为 FAIL。已有 canonical-uppercase B5 定向回归独立运行通过：3 tests、0 failures/errors/skips；不能覆盖此大小写路径。

Original P1、recovery-wins、sender-wins、race、owner death、commit ambiguity、duplicate sender：canonical 路径证据存在，但不予接受，因为存在 authority bypass path。
B1/B2/B3/B4 interaction：不予接受。
Implementation Full Maven：记录为 `1852 / 0 failures / 0 errors / 96 skips`；不能弥补本次发现。
Full Maven reuse decision：NOT_ACCEPTABLE_FOR_REMEDIATION_ACCEPTANCE。

P0：0
P1：1 — `AUTHORITY_BYPASS_PATH`
P2：未继续评估

Final decision：

```text
FAIL /
B5_MUTATION_AUTHORITY_REMEDIATION_NOT_ACCEPTED /
P1_OPEN /
AUTHORITY_BYPASS_PATH /
STALE_SENDER_MUTATION_P1_NOT_CLOSED
```

Next action：在 review 外修复 venue canonicalization／拒绝策略，使 authority admission 与实际 adapter routing 使用同一 canonical venue，再冻结新 candidate 并重新独立审查。
````

前次行内审查意见原文：

> [P1] Lowercase venue bypasses authority
>
> 该判断仅接受精确大写 OKX；请求可携带 okx，而 gateway 随后会规范化为 OKX 并发送真实 PLACE。因此订单不会创建或 arm V49 authority，却仍进入 adapter。应在持久化和 authority admission 前使用与 gateway 相同的 canonical venue 规则，或拒绝非 canonical 输入。
