# NQ-DH Integration-0 Contract Freeze

> 任务：NQ-DH-INTEGRATION-0-CONTRACT-FREEZE
> 类型：DOCUMENTATION + CONTRACT DESIGN
> 日期：2026-06-11
> 仓库视角：NexusQuant（NQ，交易事实源 / 主权执行方）
> 对端：Decision Hub（DH，AI Agent 决策能力层 / 候选建议方）

本文件是 NQ 侧 Integration-0 契约冻结主文档。配套文档：

- `NQ_DH_INTEGRATION0_SECURITY_POLICY.md`：安全策略（header / 签名 / 防重放 / 脱敏 / 审计）。
- `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`：mock / contract test 设计。

DH 仓库的对应镜像文档为 `DH_NQ_INTEGRATION0_CONTRACT_FREEZE.md` 等，内容口径一致，框架视角不同。

---

## 1. 总体结论

- 结论：**有条件通过冻结**。本轮只冻结 Integration-0 契约与边界，不实现任何集成代码。
- Integration-0 是 **contract / mock / documentation work line, not runtime integration**。
- 本轮产出可作为后续 mock / stub / contract test 的稳定依据。
- DH 当前 **not integrated**：NQ 侧无 DH 入站端点、无 DH client、无 feedback outbox。
- 真实联调、真实 HTTP、RealClient、真实 Provider、真实交易所调用、LIVE 全部禁止，必须等 Integration-1 及以后单独开工并通过安全审查。
- DH P1-4 残留（rate limit / memory cap / replay nonce 持久化缺失）**不阻塞 Integration-0 契约冻结**，但**阻塞 Integration-1**（真实只读接入或真实通道）。本轮不修复 P1-4。

## 2. Integration-0 定义

Integration-0 = NQ 与 DH 真实接入前的**只读边界、契约冻结、权限模型、审计模型与风险清单**的文档与契约工作线。

允许：

- 只读边界设计。
- 契约冻结（header / auth / replay / payload / 数据契约 / 错误码）。
- mock / stub / contract test 设计。
- 安全策略文档。
- signature / tenant / trace / replay / payload 契约设计。
- 禁止能力清单冻结。
- 可开放能力清单冻结。

禁止：

- 真实联调、真实 HTTP 调用、真实交易所调用。
- NQ RealClient、DH RealClient、真实 Provider。
- 下单、撤单、启动/停止 Paper Run、修改策略状态、修改风控状态。
- 读取 NQ 凭证、读取交易所 API key / secret / passphrase。
- 读写 NQ DB。
- 开启 LIVE。
- 自然语言或 Agent output 直接驱动交易。
- 把本轮写成 implemented；把 Integration-0 写成真实集成。

## 3. 明确禁止事项

本轮严格遵守，且作为契约冻结结论长期生效：

1. 不实现任何 Java / frontend / Python 代码，不新增 API / Controller / Service / Repository / DTO / migration。
2. 不新增 NQ RealClient、DH RealClient、真实 Provider、真实 NQ 调用、真实交易所调用。
3. 不读取、打印、复制、输出任何真实密钥、token、cookie、私钥、助记词、API secret、passphrase。
4. 不把 DH not integrated 写成 integrated；不把 AI not started 写成 started；不把 LIVE disabled 写成 enabled。
5. 契约冻结只描述未来接入时的稳定约束，不代表已经接入。

## 4. DH → NQ 禁止能力（冻结）

以下能力 DH **永久禁止默认拥有**。每项口径固定为：

| 能力 | 允许 Integration-0 | 允许 Integration-1 | 允许 LIVE | 需代码硬闸 | 需审计记录 |
| --- | --- | --- | --- | --- | --- |
| 下单（place order） | 否 | 默认否，需单独审计批准 | 否 | 是 | 是 |
| 撤单（cancel order） | 否 | 默认否，需单独审计批准 | 否 | 是 | 是 |
| 修改订单状态 | 否 | 默认否，需单独审计批准 | 否 | 是 | 是 |
| 修改策略状态（启停/发布） | 否 | 默认否，需单独审计批准 | 否 | 是 | 是 |
| 启动 Paper Run | 否 | 默认否，需单独审计批准 | 否 | 是 | 是 |
| 停止 Paper Run | 否 | 默认否，需单独审计批准 | 否 | 是 | 是 |
| 修改风控状态 | 否 | 默认否，需单独审计批准 | 否 | 是 | 是 |
| 读取交易所凭证 | 否 | 否 | 否 | 是 | 是 |
| 读取 NQ credential | 否 | 否 | 否 | 是 | 是 |
| 直接读写 NQ DB | 否 | 否 | 否 | 是 | 是 |
| 绕过 NQ API | 否 | 否 | 否 | 是 | 是 |
| 绕过 NQ 风控 | 否 | 否 | 否 | 是 | 是 |
| 绕过 NQ 状态机 | 否 | 否 | 否 | 是 | 是 |
| 绕过 NQ 审计 | 否 | 否 | 否 | 是 | 是 |
| 直接触发 LIVE | 否 | 否 | 否 | 是 | 是 |
| 自然语言直接驱动交易 | 否 | 否 | 否 | 是 | 是 |
| Agent output 直接驱动交易 | 否 | 否 | 否 | 是 | 是 |
| feedback 直接影响交易执行 | 否 | 否 | 否 | 是 | 是 |

冻结原则：

- DH 输出永远只是**候选输入**（candidate input），不是 NQ 执行命令。
- 任何最终交易动作必须由 NQ 独立风控、独立状态机、本地审计决定。
- “需代码硬闸=是”表示：未来实现接入前，必须先在 NQ 侧补齐拒绝/隔离硬闸，硬闸缺失则不允许接入。

## 5. DH → NQ 可开放能力（冻结）

Integration-0 只允许**设计**以下低风险能力，本轮不实现。统一约束：真实 HTTP=否；需认证=是；需签名=是；需 tenant binding=是；需 requestId/traceId=是；需 timestamp=是；需 nonce=是；需 replay protection=是；需 payload size limit=是（默认 64 KiB）；需 audit log=是；允许进入交易执行路径=否。

| 能力 | 只读 | 允许 mock | 方向 | 说明 |
| --- | --- | --- | --- | --- |
| 1. 读取公开/脱敏系统状态 | 是 | 是 | NQ→DH | 仅运行态摘要，不含凭证/账户敏感字段 |
| 2. 读取策略元数据 | 是 | 是 | NQ→DH | strategyCode/版本/状态枚举，不含源代码 |
| 3. 读取回测摘要 | 是 | 是 | NQ→DH | 指标摘要、verdict，不含原始大对象 |
| 4. 读取 Paper 结果摘要 | 是 | 是 | NQ→DH | paperRunId/状态/指标摘要/拒绝码 |
| 5. 提交候选信号 | 否（写候选） | 是 | DH→NQ | 只落候选/待审查，不执行交易 |
| 6. 提交研究报告 | 否（写候选） | 是 | DH→NQ | 报告引用，不进入执行路径 |
| 7. 提交风险解释 | 否（写候选） | 是 | DH→NQ | 风险说明，不改风控状态 |
| 8. 提交非执行型建议 | 否（写候选） | 是 | DH→NQ | recommendation，仅候选 |
| 9. 接收 NQ 脱敏反馈 | 是 | 是 | NQ→DH | feedback 事件，DH 只消费不反写 NQ |
| 10. mock test | 是 | 是 | 双向 | 契约 mock |
| 11. stub test | 是 | 是 | 双向 | stub 隔离 |
| 12. contract test | 是 | 是 | 双向 | schema/header/replay 校验 |

说明：

- “提交”类能力（5-8）即使是写入，也只允许落到 NQ 的**候选/待审查队列**，不得进入下单、撤单、Paper/LIVE 启动或策略状态修改路径。
- 所有可开放能力在 Integration-0 阶段只允许 mock / stub / contract test 验证，**禁止真实 HTTP**。

## 6. Header / Auth / Replay Contract（冻结）

详见 `NQ_DH_INTEGRATION0_SECURITY_POLICY.md`，此处冻结字段与规则摘要。

Required headers（跨系统请求）：

```text
X-NQ-DH-Source         请求来源系统标识，必须命中 allowlist
X-NQ-DH-Tenant-Id      租户标识，必须与认证主体绑定
X-NQ-DH-Request-Id     幂等键，用于幂等与审计
X-NQ-DH-Trace-Id       端到端追踪键，用于跨系统排查
X-NQ-DH-Timestamp      请求时间戳，canonical wire format 固定为 RFC3339 UTC Z，例如 2026-06-15T12:34:56Z，且必须落在允许窗口
X-NQ-DH-Nonce          一次性随机值，用于防重放
X-NQ-DH-Signature      HMAC-SHA256 签名（候选方案）
Content-Type: application/json
```

规则：

- Timestamp 必须是 RFC3339 / ISO-8601 UTC `Z`，示例 `2026-06-15T12:34:56Z`；epoch seconds / epoch milliseconds 不是 canonical wire format，必须拒绝；数字时区偏移（如 `+08:00`）也不是 canonical wire format，必须拒绝。
- Timestamp 必须有允许窗口，默认 ±300 秒；超窗拒绝。
- Nonce 必须防重放；`Source + Nonce + RequestId` 组合在 TTL 内唯一，重放拒绝（409 或等价）。
- Signature 使用 HMAC-SHA256 作为候选方案；签名原材料至少包含 source、tenantId、requestId、traceId、timestamp、nonce、body。
- Payload 最大 64 KiB，超限拒绝，不得截断后接受。
- Source 必须在 allowlist 中。
- Tenant 必须绑定请求、审计和数据作用域，禁止仅信任请求体。
- RequestId 用于幂等与审计；同 requestId 不允许换 payload。
- TraceId 用于跨系统排查。
- Signature 原材料、raw request / raw response、prompt / full context **不得落日志、不得落库**。

与现有实现的关系（诚实声明，不在本轮修复）：

- DH 已实现的 NQ feedback authenticator 当前使用 `X-DH-NQ-*` 命名族（见 DH `DH_AUDIT_FIX_REPORT.md`）。
- Integration-0 冻结的 canonical 跨系统 header 族为 `X-NQ-DH-*`。
- Integration-1 实现时必须把两者对齐：要么统一到 `X-NQ-DH-*`，要么在显式映射层转换。该对齐是 **Integration-1 前置项**，不在本轮修复。
- NQ production runtime timestamp handling 仍为 **NOT PRESENT / NOT STARTED**；本轮仅做 NQ docs 与 INT0 test/support companion alignment，不能把 timestamp alignment overall 写成 CLOSED。

## 7. Data Contracts（冻结草案，contract-only / mock-only）

本轮只做文档契约设计，**不生成 Java DTO**。每个契约统一字段：`contractName / version / direction / purpose / allowedFields / forbiddenFields / validationRules / auditRequirements / payloadLimit / idempotencyRules / Integration-0 status`。

通用约束：`version=0.1.0-int0-frozen`；`payloadLimit=64 KiB`；`Integration-0 status=contract-only / mock-only`；`forbiddenFields` 见第 8 节统一禁止字段清单（每个契约都继承）。

### 7.1 DHSignalCandidate

- direction：DH_TO_NQ
- purpose：DH 提交候选交易信号，仅作为候选输入，不驱动执行。
- allowedFields：`contractName, version, tenantId, requestId, traceId, correlationId, occurredAt, schemaVersion, strategyCode, symbol, side(enum BUY/SELL), signalType(enum), confidence(0..1), horizon, rationaleRef, candidateOnly(const true)`
- validationRules：symbol 白名单；side/signalType 枚举；confidence∈[0,1]；candidateOnly 必须为 true；schemaVersion semver。
- auditRequirements：落 NQ 本地审计（接收/拒绝/限流/风控结果）。
- idempotencyRules：同 requestId 幂等；换 payload 视为冲突（409）。

### 7.2 DHResearchReport

- direction：DH_TO_NQ
- purpose：DH 提交研究报告引用与摘要。
- allowedFields：`contractName, version, tenantId, requestId, traceId, occurredAt, schemaVersion, reportId, title, summary, metricsSummary, referenceIds[], candidateOnly(const true)`
- validationRules：summary/字段长度受 payload 上限约束；只允许引用 ID 与摘要，不允许原始大对象。
- auditRequirements：落审计。
- idempotencyRules：同 requestId 幂等。

### 7.3 DHRiskReview

- direction：DH_TO_NQ
- purpose：DH 提交风险解释/复核意见，不改 NQ 风控状态。
- allowedFields：`contractName, version, tenantId, requestId, traceId, occurredAt, schemaVersion, subjectRef, riskLevel(enum), findings[], explanation, candidateOnly(const true)`
- validationRules：riskLevel 枚举；不得包含执行指令字段。
- auditRequirements：落审计。
- idempotencyRules：同 requestId 幂等。

### 7.4 DHDecisionSummary

- direction：DH_TO_NQ
- purpose：DH JudgeDecision 后的最终建议摘要（非执行命令）。
- allowedFields：`contractName, version, tenantId, requestId, traceId, occurredAt, schemaVersion, decisionId, recommendation(enum), rationaleRef, linkedCandidateIds[], candidateOnly(const true)`
- validationRules：recommendation 枚举；linkedCandidateIds 必须可追溯；candidateOnly 必须 true。
- auditRequirements：落审计。
- idempotencyRules：同 requestId 幂等。

### 7.5 NQFeedbackEvent

- direction：NQ_TO_DH
- purpose：NQ 把脱敏后的事实事件回流给 DH，DH 只消费。
- allowedFields：`contractName, version, tenantId, traceId, requestId, correlationId, eventId, eventType(enum), sourceSystem(const NQ), sourceJobId, occurredAt, schemaVersion, payloadSummary`
- validationRules：eventType 枚举；sourceSystem 常量 NQ；payloadSummary 脱敏。
- auditRequirements：DH 落 ingestion 审计；NQ 落 outbox 审计（未来）。
- idempotencyRules：eventId 全局唯一幂等；nonce 重放认证层先拒绝。

### 7.6 NQPaperResultSummary

- direction：NQ_TO_DH
- purpose：NQ 返回 Paper 运行结果摘要。
- allowedFields：`contractName, version, tenantId, traceId, paperRunId, status(enum), metricsSummary, rejectReasonCode, stabilitySummary, occurredAt, schemaVersion`
- validationRules：status 枚举；只摘要不含全量订单/成交。
- auditRequirements：只读查询审计。
- idempotencyRules：读侧无写幂等；按 paperRunId 查询。

### 7.7 NQStrategyMetadata

- direction：NQ_TO_DH
- purpose：NQ 返回策略元数据（只读）。
- allowedFields：`contractName, version, tenantId, strategyCode, strategyVersion, status(enum), publishState(enum), updatedAt, schemaVersion`
- validationRules：枚举校验；不含策略源代码。
- auditRequirements：只读查询审计。
- idempotencyRules：读侧无写。

### 7.8 NQBacktestSummary

- direction：NQ_TO_DH
- purpose：NQ 返回回测摘要（只读）。
- allowedFields：`contractName, version, tenantId, traceId, backtestId, verdict(enum), metricsSummary, winRate(0..1), occurredAt, schemaVersion`
- validationRules：verdict 枚举；winRate∈[0,1]；只摘要不含原始大对象。
- auditRequirements：只读查询审计。
- idempotencyRules：读侧无写。

### 7.9 NQErrorResponse

- direction：NQ_TO_DH
- purpose：NQ 对外标准错误响应。
- allowedFields：`contractName, version, errorCode(enum), message(safe), traceId, requestId, timestamp`
- validationRules：message 必须脱敏，不含内部路径/SQL/凭证/栈。
- auditRequirements：错误落审计。
- idempotencyRules：不适用。

### 7.10 NQDhContractError

- direction：NQ_TO_DH
- purpose：契约/校验级错误（schema、字段、签名、replay、tenant、payload）。
- allowedFields：`contractName, version, errorCategory(enum: SCHEMA/SIGNATURE/REPLAY/TENANT/PAYLOAD/RATE_LIMIT/FORBIDDEN_FIELD), errorCode, message(safe), traceId, requestId, timestamp`
- validationRules：errorCategory 枚举；message 脱敏。
- auditRequirements：拒绝必须落审计，可追踪、可复盘。
- idempotencyRules：不适用。

## 8. Forbidden Fields（统一禁止字段，所有契约继承）

所有契约**禁止包含**以下任一字段或其等价物：

```text
API key
API secret
token
cookie
passphrase
private key
mnemonic
wallet private key
exchange credential
account credential
raw request
raw response
full prompt
full context
signature raw material
authorization header
database connection string
production URL with secret
password
2FA secret
recovery code
```

约束：

- 出现任一禁止字段，契约校验必须直接拒绝（`NQDhContractError` errorCategory=FORBIDDEN_FIELD）。
- 禁止字段不得落日志、不得落库、不得回显。

## 9. NQ 对 DH 输入的不可信处理原则（冻结）

NQ 必须把所有 DH 输入视为**不可信输入**（untrusted input）。

进入 NQ 前必须校验：

- 认证主体、tenantId、traceId、requestId、nonce、签名。
- schemaVersion 与 JSON Schema。
- 字段白名单、枚举、日期窗口、symbol 白名单、资金/数量上限。
- 幂等键：同 requestId 不允许换 payload。
- 来源权限：DH 只能提交 candidate signal / recommendation / research / risk review，不具备交易执行权限。

NQ 必须独立执行：

- 独立风控：任何 DH 输入不得绕过 `RiskGate` / 风控前置。
- 独立订单状态机：订单状态变化只能由 NQ core/application service 完成。
- 本地审计：接收、拒绝、限流、重放、风控结果全部落 NQ 本地审计。
- 本地事实源：NQ 仍是唯一交易事实源。

NQ 拒绝矩阵（冻结）：

```text
400  schema / 字段 / symbol / 日期 / 参数错误
401  认证缺失或失败
403  权限/来源不允许，或 tenant 不一致
409  幂等冲突（同 requestId 换 payload）或 nonce 重放
413  payload 超 64 KiB
423  AI / integration gate disabled（默认关闭）
429  租户或能力限流
```

DH 不可用降级：

- NQ 主链路继续运行，不阻塞订单、风控、账本、回测、Paper。
- DH 不可用不得触发 NQ 内部策略自动执行。

## 10. Mock / Contract Test Plan（冻结摘要）

完整设计见 `NQ_DH_INTEGRATION0_CONTRACT_TEST_PLAN.md`。本轮**只写测试计划，不写测试代码**。必须设计 15 项（禁止能力、可开放能力、header 缺失、HMAC 失败、timestamp 过期、nonce replay、tenant mismatch、payload 超限、forbidden field、raw prompt/context 拒绝、candidate schema、feedback schema、audit required、no trading side-effect、no credential access）。每项包含 `testName / targetSystem / purpose / input / expectedResult / forbiddenSideEffect / whetherBlocksIntegration0`。

## 11. Integration-0 验收标准（冻结）

Integration-0 视为通过当且仅当：

1. 本主文档 + 安全策略 + contract test plan 三份文档落盘，口径一致。
2. DH → NQ 禁止能力清单冻结（第 4 节）。
3. DH → NQ 可开放只读能力清单冻结（第 5 节）。
4. header / auth / replay / payload 契约冻结（第 6 节 + 安全策略）。
5. 数据契约草案冻结（第 7 节）。
6. 统一禁止字段清单冻结（第 8 节）。
7. NQ 不可信输入处理原则冻结（第 9 节）。
8. mock / contract test 设计冻结（第 10 节 + test plan）。
9. 明确 DH P1-4 残留为 Integration-1 前置修复，不在本轮修复。
10. 明确 Integration-0 不是实现任务；未修改代码、API、migration、测试或部署。
11. `git diff --check` 通过；本轮 docs-only。

## 12. Integration-1 Blockers（冻结）

进入 Integration-1（真实只读接入或真实通道设计）前必须先修复以下 DH P1-4 残留，本轮不修复：

- **rate limit 缺失**：跨系统入口缺少租户/能力级限流，必须补齐后才允许真实通道。
- **memory cap 缺失**：DH InMemory 仓储无上限，真实流量下存在内存膨胀风险，必须补上限/外部存储。
- **replay nonce 持久化缺失**：nonce 仅依赖单实例内存，必须持久化或集中缓存（TTL ≥ 2 × maxClockSkew），否则多实例重放防护失效。

其它前置：

- NQ 侧 DH 入站端点、DH client、feedback outbox 均未实现，Integration-1 才允许设计/实现，且必须先过安全审查。
- header 命名 `X-DH-NQ-*` 与 `X-NQ-DH-*` 对齐（见第 6 节）。

## 13. Out-of-Scope（本轮不做）

- 任何 Java / frontend / Python 代码。
- 任何 API / Controller / Service / Repository / DTO / migration。
- NQ RealClient / DH RealClient / 真实 Provider / 真实 HTTP / 真实交易所调用。
- 真实联调、下单、撤单、Paper Run 启停、策略状态修改、LIVE。
- 读取凭证、读写 NQ DB。
- 修复 DH P1-4 残留。

## 14. Next Action

- 推进 Integration-0 mock / contract test 设计与安全文档固化（仍是 contract / mock / docs 工作线）。
- 不得直接做真实联调；任何真实通道必须等 Integration-1 单独开工并先修复 P1-4 残留与通过安全审查。
