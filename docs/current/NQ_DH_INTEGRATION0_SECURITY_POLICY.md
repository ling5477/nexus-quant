# NQ-DH Integration-0 Security Policy

> 任务：NQ-DH-INTEGRATION-0-CONTRACT-FREEZE
> 类型：DOCUMENTATION + CONTRACT DESIGN
> 日期：2026-06-11
> 仓库视角：NexusQuant（NQ）
> 配套：`NQ_DH_INTEGRATION0_CONTRACT_FREEZE.md`、`NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`

本文件冻结 NQ-DH Integration-0 的安全策略：header / 签名 / 防重放 / tenant / trace / payload / 脱敏 / 审计。**只做契约设计，不实现代码。**

---

## 1. 安全总原则

- DH 是不可信来源；所有 DH → NQ 输入按 untrusted input 处理。
- 默认 fail-closed：缺失认证、签名、tenant、source 等任一要素一律拒绝。
- 默认所有真实通道关闭（gate disabled）；Integration-0 只允许 mock / stub / contract test。
- 最小权限：DH 默认只有候选提交与只读摘要能力，无任何执行能力。
- 凭证零暴露：任何密钥、签名原材料、raw payload、prompt 不得落日志、不得落库、不得回显。

## 2. Required Headers（冻结）

```text
X-NQ-DH-Source         来源系统标识，必须命中 allowlist
X-NQ-DH-Tenant-Id      租户标识，必须与认证主体绑定
X-NQ-DH-Request-Id     幂等键
X-NQ-DH-Trace-Id       端到端追踪键
X-NQ-DH-Timestamp      请求时间戳（epoch 毫秒或 RFC3339，二选一冻结为 epoch 毫秒）
X-NQ-DH-Nonce          一次性随机值
X-NQ-DH-Signature      HMAC-SHA256 签名（候选方案）
Content-Type: application/json
```

任一 header 缺失 → 拒绝（缺认证类返回 401，缺来源/租户类返回 403，缺幂等/追踪类返回 400）。

## 3. Authentication & Signature（冻结）

- 服务间认证：`Authorization: Bearer <service-token>` 或 mTLS；生产建议 mTLS + HMAC 双层。
- 签名算法：HMAC-SHA256（Integration-0 候选方案）。
- 签名原材料（canonical string）至少包含：

```text
X-NQ-DH-Source
X-NQ-DH-Tenant-Id
X-NQ-DH-Request-Id
X-NQ-DH-Trace-Id
X-NQ-DH-Timestamp
X-NQ-DH-Nonce
sha256(body)
```

- token 哈希存储（如 SHA-256），禁止明文 token 入库或日志。
- 签名不匹配 → 拒绝（401 或 403），落审计但不记录签名原材料。

## 4. Timestamp & Replay Protection（冻结）

- Timestamp 允许窗口：默认 ±300 秒（5 分钟），超窗拒绝（401/403）。
- Nonce 防重放：`Source + Nonce + Request-Id` 组合在 TTL 内必须唯一；重放拒绝（409 或等价）。
- Nonce TTL：建议 ≥ 2 × maxClockSkew。
- **Integration-1 前置**：nonce 必须持久化或集中缓存，不能只依赖单实例内存（DH P1-4 残留，本轮不修复，阻塞 Integration-1）。

## 5. Tenant / Trace / Request 绑定（冻结）

- `tenantId` 必须与认证主体绑定，禁止仅信任请求体；不一致返回 403。
- `tenantId` 同时约束数据作用域与审计作用域：DH 只能在被授权 tenant 范围内读写候选。
- `traceId` 是端到端追踪主键，必须可在审计/追踪记录中命中。
- `requestId` 是幂等键；同 requestId 换 payload 返回 409。
- `correlationId`（可选）用于关联一组事件；不得与 traceId / requestId 混用。

## 6. Payload 策略（冻结）

- payload 最大 64 KiB（65536 bytes）；超限拒绝（413），不得截断后接受。
- 大对象（完整回测结果、完整订单/成交、原始市场数据）禁止外发；只传摘要、指标与引用 ID。
- Content-Type 必须 `application/json`；其它类型拒绝。

## 7. 脱敏与禁止外发数据（冻结）

禁止发送给 DH / 任何第三方 provider：

```text
交易所 API Key / Secret / Passphrase / token / cookie / 私钥 / 连接串
NQ / DH 服务 token、HMAC secret、JWT、数据库 DSN
账户余额全量、可识别真实账户身份字段、原始 credential payload
LIVE 真实订单全量、成交全量、交易所原始响应全量
未脱敏错误堆栈、内部路径、SQL、服务拓扑
```

必须脱敏后才可用于摘要：

```text
tenantId / accountId / strategyCode / paperRunId / backtestId  -> 按需 hash 或内部别名
订单 / 成交 / 持仓摘要  -> 只保留统计指标与解释所需最小字段
错误信息  -> 去除内部路径、SQL、连接串、拓扑
市场数据  -> 只必要窗口、必要粒度，禁止批量外发原始数据资产
```

## 8. Provider / 中转站边界（冻结）

- Integration-0 不接任何真实 provider。
- 真实 provider 接入前必须先完成 provider trust policy、数据分级、脱敏、出站审计、baseURL allowlist 与人工审批。
- trust level 口径（沿用既有 `ProviderTrustLevel`）：`OFFICIAL_API` / `SELF_HOSTED_GATEWAY` / `CONTROLLED_RELAY` 允许分级使用；`UNTRUSTED_RELAY` / `UNKNOWN` 拒绝。
- OpenAI-compatible relay、new-api、one-api、openrouter、siliconflow、未知 relay/proxy 默认拒绝。

## 9. 审计要求（冻结）

必须落审计的事件：

```text
接收 / 拒绝 / 限流 / 重放命中 / 签名失败 / tenant 不一致 /
payload 超限 / forbidden field 命中 / 风控结果 / 幂等冲突
```

审计字段（按存在情况选择）：

```text
traceId / requestId / tenantId / source / eventType / errorCode / 耗时 / 结果
```

审计禁止记录：

```text
token / API key / secret / passphrase / cookie / 私钥 / 助记词 /
签名原材料 / raw request / raw response / full prompt / full context / 未脱敏堆栈
```

## 10. Gate 与开关（冻结）

- 跨系统集成默认 gate disabled；命中 disabled 返回 423。
- LIVE 永远独立开关、独立审查、独立 Gate；Integration-0..N 不得开启 LIVE。
- 真实通道开关只能在 Integration-1 及以后、且 P1-4 残留修复并通过安全审查后，由人工显式启用。

## 11. 与现有实现的关系（诚实声明）

- DH 已实现 NQ feedback 认证使用 `X-DH-NQ-*` header 族与 HMAC/timestamp/nonce/source allowlist/payload gate（见 DH `DH_AUDIT_FIX_REPORT.md`，P1-1/P1-2/P1-3 已关闭）。
- 本策略冻结的 canonical 跨系统 header 族为 `X-NQ-DH-*`。
- 两者对齐（统一命名或映射层转换）是 **Integration-1 前置项**，不在本轮实现。

## 12. Integration-1 安全前置（冻结）

进入 Integration-1 前必须修复（DH P1-4 残留，本轮不修复）：

- rate limit（租户/能力级限流）。
- memory cap（InMemory 仓储上限或外部存储）。
- replay nonce 持久化（持久化或集中缓存）。
