# Credential Active Material Selection Review

任务：NQ-DB-SCHEMA-GOVERNANCE-BATCH-5E-A-CREDENTIAL-ACTIVE-MATERIAL-SELECTION-REVIEW
日期：2026-06-07
状态：Batch 5-E-A review completed；Batch 5-E-B code fix not started；Batch 5-E-C schema fix not started。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

## 1. Scope

本轮只读审计 credential active summary / active material 的选择语义，判断是否需要显式 `credentialType` 或 `permission_scope` 过滤，避免未来一个 exchange account 同时存在多个 ACTIVE credential type 时出现非确定性选择。

本轮只写文档，不修改 Java、API、Repository、migration、前端、Python 或部署脚本；不新增 enable endpoint；不调用真实交易所；不读取或输出真实密钥、secret、token、私钥、助记词、cookie、passphrase、encrypted payload 或 decrypted payload。

## 2. Current Active Material Behavior

- `findActiveSummary(ownerUserId, exchangeAccountId)` 当前按 `exchange_account_id`、owner、`is_active = TRUE`、`credential_status = 'ACTIVE'` 过滤，并按 `updated_at DESC LIMIT 1` 返回单条摘要。
- `findActiveMaterial(ownerUserId, exchangeAccountId)` 当前按同样条件过滤，并解密返回服务端内部 material；该查询同样使用 `ORDER BY updated_at DESC LIMIT 1`。
- `findActiveByAccountAndType(exchangeAccountId, credentialType)` 已存在并按 `credential_type` 过滤，但主要用于 upsert 查找同类型旧 active，不是 active material / active summary 的默认读取路径。
- `verifyActive(ownerUserId, exchangeAccountId)` 使用 `findActiveMaterial` 读取 material，再回写 verification 结果，之后使用 `findActiveSummary` 返回摘要。
- `GET /api/exchange-accounts/{accountId}/credentials/active` 通过 `findActiveSummaryOrNull` 返回单个 `activeCredential` 摘要，不返回 material。
- API response 只包含 `credentialId`、`credentialType`、`maskedAccessKey`、生命周期、校验状态和时间字段；不暴露 encrypted payload、secret、token、private key、passphrase 或 decrypted payload。

## 3. Schema Facts

- V12 建立 `exchange_account_credentials`，`credential_type` 允许 `OKX_API_V5 / BINANCE_HMAC / BINANCE_ED25519`。
- V12 partial unique index 为 `uq_exchange_account_credentials_active_type ON (exchange_account_id, credential_type) WHERE is_active = TRUE`。
- 该索引只保证同一个 account + credential type 最多一条 `is_active=true` 记录，不保证同一个 account 全局只有一条 active credential。
- V29 新增 `credential_status`，允许 `ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED`。
- V29 新增 `permission_scope`，CHECK 允许 `READ_ONLY / TRADE / NULL`；当前代码没有写入、读取或过滤该字段。
- V29 没有新增 `(exchange_account_id) WHERE is_active = TRUE AND credential_status = 'ACTIVE'` 的 account 全局 active 唯一约束。

## 4. Risk Judgment

当前状态在单 active credential type 的数据形态下可工作；Batch 5-C / 5-D-B 已能保证 revoke / disable / expire / rotate 后 inactive 或非 ACTIVE 记录不会进入 active material。

主要风险来自 schema 与读取语义不一致：schema 允许同一 account 同时存在多个 active credential type，但 active summary / active material 查询没有 `credential_type` 或 `permission_scope` 输入，只按更新时间 `LIMIT 1`。一旦同一 account 同时存在 `READ_ONLY` 与 `TRADE`、或 OKX/Binance 多种 credential type，`GET active`、`verifyActive` 和未来真实交易所权限探活可能选中错误 credential。

这不是当前 GateJ 的 P0，因为 LIVE trading disabled、真实交易所权限探活未实现、AI/DH 未接入，active material 仍只在服务端内部结构性校验路径使用。但这是后续接真实权限探活或更细权限治理前必须修正的 P1。

## 5. Findings

### P0

无。

本轮未发现 active material 被 API 直接暴露、被 DH / AI / Agent 读取、或被 LIVE trading 路径使用的证据。

### P1

- active material / active summary 查询未显式绑定 `credential_type`，但 V12 schema 允许同一 account 多个 credential type 同时 active；未来多 type active 时会产生非确定性选择。
- `verifyActive` 当前没有 credential type 入参，可能校验最新更新的 active credential，而不是调用方意图校验的交易所 credential。
- `GET /credentials/active` 的单数响应会掩盖多 active credential 的事实；前端或调用方可能误以为该 account 只有一个 active credential。

### P2

- `permission_scope` 已有 schema 字段和 CHECK，但应用层当前不写、不读、不过滤；未来 READ_ONLY / TRADE 权限探活前不能依赖该字段。
- 当前没有代码层冲突检测：当一个 account 出现多条 `credential_status='ACTIVE' AND is_active=true` 记录时，Repository 不会返回 conflict，只会 `LIMIT 1`。
- 如果业务决策是 account 全局最多一个 active credential，则当前 schema 缺少相应 partial unique 约束；如果业务允许 READ_ONLY 与 TRADE 并存，则当前单数 active API 语义需要调整。
- `updated_at DESC` 只能稳定选择最近更新记录，不能表达 credential type、权限范围或调用意图。

### P3

- 文档已经多处提示 active material 只取 ACTIVE，但对“account 是否允许多个 active credential type”还没有最终业务决策。
- 测试覆盖了 revoke / disable / expire / rotate 后不可读和单 active 正常路径，但缺少“同一 account 多 active type 时必须冲突或显式选择”的回归矩阵。
- `permission_scope=NULL` 的当前含义是尚未由代码确认权限；后续文档需要避免把 NULL 误读为允许 READ_ONLY 或 TRADE。

## 6. Recommended Batch 5-E-B Code Changes

建议 5-E-B 先做最小代码修复，不新增 migration，不改历史 migration。

1. 为 Repository 增加显式选择或冲突检测路径：
   - 最小方案：新增 `listActiveSummariesForOwner(ownerUserId, exchangeAccountId)` 或等价内部查询，调用方发现多条 active 时返回明确 conflict，而不是 `LIMIT 1`。
   - 更完整方案：新增 `findActiveSummary(ownerUserId, exchangeAccountId, credentialType)` 与 `findActiveMaterial(ownerUserId, exchangeAccountId, credentialType)`，让调用方显式选择 type。
2. `verifyActive` 不应在多 active type 时静默选择；在 API 未增加 type 参数前，应返回明确状态冲突，提示需要指定 credential type 或清理多 active。
3. `GET /credentials/active` 应至少在多 active type 时返回冲突或后续改成列表视图；不要继续把多 active 压缩成一个“最新 active”。
4. `rotate` 当前按 credentialId 锁定旧 ACTIVE credential，并从旧记录派生 credentialType，选择语义稳定；5-E-B 只需补充多 active type 下 rotate 后 active material 不被错误读取的回归。
5. `permission_scope` 暂不建议作为 5-E-B 的强过滤条件，除非同批同时完成写入、校验、默认值、API/Service 语义和测试；否则会因历史 NULL 导致误拒绝或误通过。

## 7. Recommended Tests

- Repository：同一 account 下 `OKX_API_V5` 与 `BINANCE_HMAC` 均为 `credential_status='ACTIVE' AND is_active=true` 时，active material 不得 `LIMIT 1` 静默返回。
- Repository：同一 account 同一 credential type 轮换后只返回新 credential，旧 credential `ROTATED/inactive` 不可读。
- Service：`verifyActive` 在单 active 时保持现有行为；在多 active type 时返回明确 conflict。
- Service：revoke / disable / expire 任一 active 后，该 credential 不再进入 active material；如果仍有另一 active type，应按新规则冲突或显式 type 选择。
- API：`GET /credentials/active` 在多 active type 时不返回任意一条摘要；响应不包含 material、secret、token、private key、passphrase 或 decrypted payload。
- API：`POST /credentials/{credentialId}/rotate` 后 active material 选择稳定指向新 credential；重复 rotate 旧 credential 仍拒绝。
- Future permission scope：`permission_scope=NULL` 不得被当成 `TRADE`；READ_ONLY 路径和 TRADE 路径必须分别覆盖。

## 8. Schema Constraint Decision

本轮不建议立即新增 schema 约束。原因是业务语义尚未最终确定：

- 如果未来要求一个 account 全局只能有一个 active credential，则需要 Batch 5-E-C 新增 partial unique constraint：`(exchange_account_id) WHERE is_active = TRUE AND credential_status = 'ACTIVE'`，并先做数据清理/冲突检测。
- 如果未来允许同一 account 同时保留 READ_ONLY 与 TRADE credential，或允许多个交易所 credential type 并存，则不应加 account 全局唯一约束，而应在代码层和 API 层显式传入 `credentialType` / `permission_scope`。

推荐顺序：5-E-B 先做代码层冲突检测或显式 type 选择；5-E-C 仅在业务明确“account 全局唯一 active credential”后再做 schema 约束。

## 9. Impact Review

- Paper/LIVE 隔离：当前 active material 查询通过 `exchange_account_id` 归属到具体 account；没有发现跨 account 或跨 trade_env 读取。后续真实探活或交易路径必须继续由 account context 控制，且 LIVE 仍保持 disabled。
- Exchange account summary：当前 `GET active` 单数摘要可能在多 active type 下展示错误 credential；这是 UI/调用方认知风险，不涉及 material 泄露。
- Future real exchange permission probe：必须在探活前明确 `credentialType` 与 `permission_scope` 选择；不能用当前 `LIMIT 1` 作为真实权限判断入口。
- Enable endpoint：应继续推迟。DISABLED 恢复为 ACTIVE 会直接放大多 active 选择风险，必须等 active material selection 规则、冲突处理和权限范围语义完成后再设计。

## 10. Boundary Confirmation

- 本轮未新增 migration。
- 本轮未修改 Java、API、Repository、前端、Python 或部署脚本。
- 本轮未新增 enable endpoint。
- 本轮未调用真实交易所，未新增真实下单、撤单或 LIVE 交易路径。
- 本轮未接 AI，未接 DH，未把 GateK-PLAN 写成 GateK implementation started。
- 本轮未读取或输出真实 credential material、secret、token、private key、passphrase、cookie 或助记词。
