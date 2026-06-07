# Credential Active Credential Uniqueness Review

任务：NQ-DB-SCHEMA-GOVERNANCE-BATCH-5E-C-ACTIVE-CREDENTIAL-UNIQUENESS-REVIEW
日期：2026-06-07
状态：Batch 5-E-C review completed；Batch 5-E-D migration not required for current boundary；Batch 5-F enable read-only audit required before implementation。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

## 1. Scope

本轮只读评估 `exchange_account_credentials` 是否需要从“account + credential_type active 唯一”升级为“account 全局 active 唯一”，或继续保留多 credential type active 模型。

本轮只写文档，不新增 migration，不修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本；不新增 enable endpoint；不接 AI、DH、LIVE 或真实交易所；不读取或输出真实密钥、secret、token、私钥、助记词、cookie、passphrase、encrypted payload 或 decrypted payload。

## 2. Current Schema And Code Facts

- V12 精确定义：`CREATE UNIQUE INDEX IF NOT EXISTS uq_exchange_account_credentials_active_type ON exchange_account_credentials (exchange_account_id, credential_type) WHERE is_active = TRUE;`
- V12 该 partial unique index 只保证同一 `exchange_account_id + credential_type` 最多一条 `is_active=true`，不保证同一 account 全局只有一条 active credential。
- V12 `credential_type` 当前 CHECK 允许 `OKX_API_V5 / BINANCE_HMAC / BINANCE_ED25519`；这些更接近交易所认证类型，不等同于 READ_ONLY / TRADE / FUNDING 权限范围。
- V29 新增 `credential_status`，允许 `ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED`。
- V29 新增 `permission_scope`，CHECK 允许 `READ_ONLY / TRADE / NULL`；当前没有 `FUNDING`，也没有应用层写入、读取或过滤。
- V29 没有新增 `(exchange_account_id) WHERE is_active = TRUE AND credential_status = 'ACTIVE'` 的 account 全局 active 唯一约束。
- Batch 5-E-B 后，active summary / active material 均要求 `is_active=true` 且 `credential_status='ACTIVE'`；无 `credentialType` 时 0 条返回 empty，1 条返回，多条返回状态冲突；显式 `credentialType` 时只读取对应 ACTIVE credential。
- `GET /api/exchange-accounts/{accountId}/credentials/active` 与 `POST /api/exchange-accounts/{accountId}/credentials/verify` 已支持可选 `credentialType`；多 ACTIVE type 且未指定时返回 `409 STATE_CONFLICT`。
- active material 仍只应服务端内部使用；API response 只返回非敏感摘要，不返回 encrypted payload、decrypted payload、secret、token、private key、passphrase 或 credential material。

## 3. Risk Judgment

不建议当前新增 account 全局 active unique constraint。当前更合适的模型是保留多 credential type active 的 schema 能力，并通过 5-E-B 的代码层显式选择 / conflict 规则避免非确定性读取。

原因：

- 未来真实交易所权限治理可能需要将 READ_ONLY、TRADE、以及后续可能出现的 FUNDING/资金类权限拆成不同 credential 或不同权限范围。
- 如果过早强制 account 全局 active 唯一，会把“一个 exchange account 只能有一个 ACTIVE credential”固化到 schema，可能阻碍权限拆分、最小权限凭证、只读探活凭证与交易凭证分离。
- 当前 V29 `permission_scope` 还没有应用层事实，不能作为约束或交易权限判断依据；`NULL` 仍表示权限尚未由代码确认。
- 5-E-B 已经消除了最主要的 `LIMIT 1` 静默选错风险；在当前 LIVE disabled、真实权限探活未实现、AI/DH 未接入的边界下，代码层 conflict 足够。

## 4. Findings

### P0

无。

本轮未发现 active material 被 API 直接暴露、被 AI / DH / Agent 读取、被 LIVE trading 路径使用，或当前 schema 已经导致真实交易所权限误用的证据。

### P1

- 如果未来新增 enable endpoint 而不先做只读审计，DISABLED credential 重新进入 ACTIVE 可能绕过 5-E-B 的调用方选择纪律，重新放大多 active 候选风险。
- 如果未来真实交易所权限探活、交易下单前校验或 Paper/LIVE credential 路由继续调用无 `credentialType` 的 active material 路径，多 ACTIVE type 会返回 conflict；这比选错更安全，但会成为功能阻塞，必须在接入前改为显式传 type。
- 如果产品实际要求“一个 exchange account 永远只能有一个 active credential”，当前 schema 没有 account 全局唯一约束，只靠代码层无法防止外部 SQL 或未来错误写入形成多 type active。

### P2

- `permission_scope` 仍未接入应用层语义，不能表达 READ_ONLY / TRADE 选择，也不能代表真实交易所权限探活结果。
- 当前 schema 不支持 `permission_scope='FUNDING'`；如未来需要资金划转或资金账户只读权限，需要单独 schema/API/Service 设计，不应混入本轮。
- 现有 `credential_type` 是认证类型，不是权限类型；仅用 `credentialType` 可以消除当前多 active type 歧义，但不能替代未来权限范围选择。
- API 层目前只有 active summary 和 verify 支持 `credentialType`；未来任何读取 active material 或触发权限校验的新 API 都必须显式传 `credentialType`，并在权限接入后同时传或推导 `permission_scope`。

### P3

- 文档需要明确：保留多 credential type active 是当前推荐模型，不代表允许同一 type 多 active；V12 partial unique index 仍负责同 type active 唯一。
- `permission_scope=NULL` 需要持续解释为“尚未由代码确认权限”，不能被前端、后端或运维文档误写为 READ_ONLY 或 TRADE。
- Batch 5-E-A 的“5-E-C 可评估 schema 约束”已被本轮结论收口为“当前不需要 5-E-D migration，除非业务改判为 account 全局唯一 active”。

## 5. Recommendation

推荐方案：保留当前 V12 `account + credential_type active` partial unique index，不新增 account 全局 active unique constraint；继续以 5-E-B 的代码层 deterministic selection 作为当前治理边界。

具体口径：

- 保留多 credential type active 模型，允许未来按认证类型和权限边界演进。
- 当前所有 active material 读取必须继续要求 `credential_status='ACTIVE' AND is_active=true`。
- 无 `credentialType` 的读取只能兼容单候选；多候选必须返回 conflict，不允许恢复 `ORDER BY updated_at DESC LIMIT 1`。
- `GET /credentials/active`、`POST /credentials/verify` 已支持 `credentialType`；未来任何 active material、权限探活、交易前 credential 选择、enable 预检查 API 都必须显式绑定 `credentialType`。
- `permission_scope` 后续应单独接入：写入来源、默认值、READ_ONLY/TRADE 含义、`NULL` 处理、权限探活回写和测试矩阵必须同批定义；当前不把它作为 schema active uniqueness 条件。

## 6. Batch 5-E-D Migration Decision

当前不需要 Batch 5-E-D migration。

只有在产品和安全策略明确要求“同一 exchange account 全局最多一个 ACTIVE credential”时，才应单独设计 Batch 5-E-D，并至少包含：

- 上线前数据冲突扫描：查找同一 `exchange_account_id` 下多条 `credential_status='ACTIVE' AND is_active=true` 的记录。
- 冲突清理策略：人工选择保留 credential，或按安全策略先 disable 非目标 credential；不得自动删除历史记录。
- 新 partial unique constraint：候选形式为 `(exchange_account_id) WHERE is_active = TRUE AND credential_status = 'ACTIVE'`。
- 回滚策略：如果需要恢复多 type active 模型，必须通过后续 migration 删除新约束，不能修改历史 migration。

在当前更可能需要 READ_ONLY / TRADE / 未来 FUNDING 权限拆分的方向下，不建议启动该 migration。

## 7. Batch 5-F Enable Review Decision

应继续推迟 enable endpoint，并在 Batch 5-F 先做 enable 只读审计。

Batch 5-F 审计至少要回答：

- enable 是否允许把 `DISABLED` 恢复为 `ACTIVE`，是否禁止 `REVOKED / ROTATED / EXPIRED` 恢复。
- enable 恢复时是否必须指定 `credentialType`，并校验同一 type 是否已有 ACTIVE credential。
- enable 是否必须重新 verify 或权限探活后才能进入 ACTIVE。
- enable 是否会与多 credential type active 模型冲突，是否需要先展示候选列表而不是直接恢复。
- enable audit log 如何记录 actor、reason、previousStatus、targetStatus，且不得保存 secret 或 material。
- enable 是否需要在 READ_ONLY / TRADE / 未来 FUNDING 权限接入后才允许落地。

## 8. Impact Review

- Paper/LIVE 隔离：当前 active credential 仍通过 `exchange_account_id` 归属到具体 account；本轮不改变 Paper/LIVE 隔离，也不启用 LIVE。未来交易前 credential 选择必须继续由 account context 和 trade_env 控制。
- Exchange account summary：5-E-B 已避免单数 active summary 在多 ACTIVE type 下随机展示；无 type 多候选返回 conflict，调用方需要显式 type 或后续列表视图。
- 真实交易所权限探活：在实现前必须显式选择 `credentialType`，并设计 `permission_scope` 写入/校验语义；不能依赖 account 全局唯一假设。
- READ_ONLY / TRADE / FUNDING：当前 schema 只支持 READ_ONLY / TRADE / NULL，尚不支持 FUNDING；保留多 active type 不会阻碍未来扩展，account 全局唯一 active 会阻碍权限拆分。
- Security posture：保留代码层 conflict 比全局唯一 schema 更灵活，但要求所有未来入口遵守显式选择纪律；文档和测试需要持续覆盖。

## 9. Boundary Confirmation

- 本轮未新增 migration，未修改历史 migration。
- 本轮未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。
- 本轮未新增 enable endpoint。
- 本轮未调用真实交易所，未新增真实下单、撤单或 LIVE 交易路径。
- 本轮未接 AI，未接 DH，未把 GateK-PLAN 写成实现已启动。
- 本轮未读取或输出真实 credential material、secret、token、private key、passphrase、cookie 或助记词。
