# Credential Revocation Governance Plan

任务：NQ-DB-SCHEMA-GOVERNANCE-BATCH-5A-CREDENTIAL-REVOCATION-REVIEW
日期：2026-06-07
状态：Batch 5-A review completed；Batch 5-B schema completed；Batch 5-C code/API/test completed；Batch 5-D-A rotate review completed；Batch 5-D-B explicit rotate command implemented；Batch 5-E-A active material selection review completed；Batch 5-E-B deterministic active material selection implemented；Batch 5-E-C active credential uniqueness review completed；Batch 5-F-A enable governance review completed；Batch 5-F-B credential enable audit event schema completed；Batch 5-F-C credential enable command implemented；Batch 5-G credential governance freeze review completed；permission probe design review completed；V31 permission probe schema-only completed；permission probe code/API/test design review completed。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

## 1. 目标

本计划把 credential revocation 从泛化 DB schema governance 中拆为独立治理链路，避免把凭证撤销、账户禁用、轮换、过期、权限校验和审计日志混成一个状态字段。

本计划记录 credential revocation governance 的分批落地事实。当前已完成 Batch 5-A 只读审计、Batch 5-B schema-only 治理、Batch 5-C 最小 code/API/test 接入、Batch 5-D-A rotate 只读审计、Batch 5-D-B 显式 rotate command、Batch 5-E-A active material selection 只读审计、Batch 5-E-B deterministic active material selection code/API/test 接入、Batch 5-E-C active credential uniqueness 只读审计、Batch 5-F-A enable governance 只读审计、Batch 5-F-B credential enable audit event schema-only migration、Batch 5-F-C credential enable command、Batch 5-G credential governance freeze review、permission probe design review、V31 permission probe schema-only migration 和 permission probe code/API/test design review；未完成真实交易所权限探活实现、前端接入、AI/DH/Agent 调用或 LIVE 交易能力。

## 2. 固定边界

- 不接 AI。
- 不接 DH。
- 不开启 LIVE。
- 不新增真实下单、真实撤单或真实交易所私有链路。
- 不读取、输出、提交任何真实密钥、API key、secret、token、私钥、助记词、cookie。
- 不把 GateK-PLAN 写成 GateK 实现已启动。
- credential 表不得 hard delete。
- audit log 必须 append-only。

## 3. 状态语义目标

建议后续将 credential 生命周期与校验状态拆开：

| 维度 | 建议字段 | 允许值 | 说明 |
| --- | --- | --- | --- |
| 生命周期 | `credential_status` | `ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED` | 表示凭证是否可用和为何不可用。 |
| 校验状态 | `verification_status` | `PENDING / VERIFIED / FAILED` | 表示最近一次结构性或权限校验结果，不承担撤销语义。 |
| active 标记 | `is_active` | `TRUE / FALSE` | 保留兼容唯一 active 版本，但不得单独代表安全状态。 |

最小兼容方案也可以继续使用 `verification_status=REVOKED`，但必须在文档和代码中明确：轮换旧版本、不可恢复撤销和过期不是同一种业务事件。推荐新增 `credential_status`，避免状态过载。

## 4. Batch 5-B：schema-only 最小变更

状态：completed。已新增 `backend/nq-infra/src/main/resources/db/migration/V29__schema_credential_revocation_governance.sql`，本批只做 schema-only migration 和文档同步。

允许范围：

- 新增一个 Flyway migration。
- 只修改 credential 相关表和新增 credential audit log。
- 同步 `docs/current/DB_SCHEMA.md`、`docs/current/TESTING.md`、`docs/current/WORKLOG.md`。

禁止范围：

- 禁止修改 Java、Repository、API、前端、Python、部署脚本。
- 禁止修改历史 migration。
- 禁止实现 revoke endpoint。
- 禁止接入 KMS / Secret Manager 真实外部服务。

已落地 schema：

- `exchange_account_credentials.credential_status`：允许值 `ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED`，用于凭证生命周期，不再混用 `verification_status`。
- `exchange_account_credentials.revoked_by`、`exchange_account_credentials.revoke_reason`：用于不可恢复撤销元数据；`revoke_reason` 注释明确禁止保存密钥、token、API secret、私钥、助记词、cookie、passphrase 或交易所凭证。
- `exchange_account_credentials.rotated_at`、`exchange_account_credentials.rotated_by`：用于轮换元数据，区分 `ROTATED` 和不可恢复 `REVOKED`。
- `exchange_account_credentials.last_used_at`、`exchange_account_credentials.failed_auth_count`：用于使用和失败计数元数据；`failed_auth_count` 有非负 CHECK 约束。
- `exchange_account_credentials.permission_scope`：V31 后允许 `READ_ONLY / TRADE / FUNDING` 或 `NULL`；`NULL` 表示尚未由代码确认权限，不等于 `TRADE`。
- `exchange_account_credentials.withdraw_enabled`：默认 `FALSE`，只记录治理元数据，不代表系统实现提现能力或开启 LIVE trading。
- `exchange_account_credentials.ip_allowlist_required`：默认 `TRUE`，只记录治理要求，不保存 IP 凭证、token、cookie 或网络访问密钥。
- `exchange_account_credentials.permission_probe_status`：V31 新增，允许 `NOT_PROBED / IN_PROGRESS / SUCCEEDED / FAILED / SKIPPED`；默认 `NOT_PROBED` 只表示未探活，不代表权限可用。
- `exchange_account_credentials.last_permission_probe_at` / `last_permission_probe_error`：V31 新增，用于记录真实权限探活完成时间与脱敏错误摘要；错误摘要不得保存 secret、token、API key、签名、headers、request body、raw response、明文 payload 或交易所凭证。
- `exchange_account_credentials.ip_allowlist_probe_status`：V31 新增，允许 `NOT_CHECKED / PASSED / FAILED / UNKNOWN / SKIPPED`；默认 `NOT_CHECKED` 不代表 IP allowlist 已通过。
- `exchange_account_credentials.external_secret_ref`、`exchange_account_credentials.key_alias`：仅保存外部密钥引用或别名，不得保存 secret 明文。
- 新增 `credential_audit_logs` append-only 表，事件类型允许 `CREATED / VERIFIED / FAILED_VERIFICATION / DISABLED / ENABLED / REVOKED / ROTATED / EXPIRED / USED / ACCESS_DENIED / PERMISSION_PROBE_STARTED / PERMISSION_PROBE_SUCCEEDED / PERMISSION_PROBE_FAILED / PERMISSION_PROBE_SKIPPED`；`ENABLED` 由 Batch 5-F-B schema-only migration 增加，表示 `DISABLED` credential 经校验后重新启用；permission probe events 由 V31 schema-only migration 增加，仅表示后续权限探活审计语义已准备。

兼容回填：

- 历史 `verification_status='REVOKED'` 或 `is_active=false` 记录按当前轮换旧版本语义回填为 `credential_status='ROTATED'`。
- 历史 `ROTATED` 记录的 `rotated_at` 使用 `revoked_at` 或 `updated_at` 补齐，避免把旧轮换记录误写成不可恢复撤销。
- 本批没有读取、输出或复制任何真实 credential material。

验收标准：

- 新增字段均有 `COMMENT ON COLUMN`。
- 新增表有 `COMMENT ON TABLE`。
- 状态字段有 CHECK 约束。
- 敏感文本字段注释明确禁止保存 secret、token、API key、exchange secret、private key、passphrase、cookie、助记词。
- migration 可从空库回放。
- 如只改 schema 和文档，必须明确未执行 Java/API 行为验证，不能写成 revoke 已实现。

本批未执行项：

- 未修改 Java、Repository、Service、Controller、DTO、前端、Python 或部署脚本。
- 未实现 revoke endpoint、rotate endpoint、active material 读取改造、Repository 默认过滤或 Service 状态流转。
- 未接 AI、DH、LIVE 或真实交易所私有链路。

## 5. Batch 5-C：code / API / test 接入

状态：completed。本批在不新增 migration 的前提下接入 V29 lifecycle 字段、最小 command API、append-only audit log 写入和回归测试。

前置条件：

- Batch 5-B schema 已完成并验证。
- 当前 Gate 边界仍允许 credential governance 接入。

已落地范围：

- Repository 接入新字段。
- Service 新增 revoke / disable / expire 状态流转；rotate 仍只由 upsert 旧 active 版本产生，不新增独立 rotate endpoint。
- API 新增最小 revoke / disable / expire command。
- 单元测试、Repository 测试、Controller 测试补齐。
- 文档同步 API 与安全边界。

已固定行为：

- active summary / active material 查询同时要求 `is_active=true` 和 `credential_status='ACTIVE'`。
- upsert 新版本时旧 active 版本写为 `credential_status='ROTATED'` 且 `is_active=false`，不再把 `verification_status` 改写为 `REVOKED`。
- revoke 是不可恢复撤销，重复 revoke 幂等返回当前摘要，不重复写 audit。
- disable / expire 会让 credential 退出 active material；本轮不实现 enable。
- 对已经 `REVOKED` 或 `ROTATED` 的 credential 执行 disable / expire 返回状态冲突，避免破坏不可恢复撤销和历史轮换语义。
- audit log append 只保存脱敏 metadata，不保存 secret、token、private key、passphrase、decrypted payload 或 request body 明文。

禁止范围：

- 禁止真实交易所权限探活。
- 禁止新增真实下单或撤单。
- 禁止 DH / Agent / AI 调用 credential API。
- 禁止 API response 返回 secret、token、private key、passphrase 或 decrypted payload。
- 禁止日志输出 request body 中的敏感字段。

已覆盖测试：

- revoke 幂等。
- revoke 后 active material 不可读取。
- revoke 后 verify 无 active material。
- DISABLED / EXPIRED 不可 active；REVOKED 不可通过本轮接口恢复。
- ROTATED 只能由 upsert 产生。
- EXPIRED 不可 active。
- owner 越权访问失败。
- API response 不包含敏感字段。
- audit log 写入不包含敏感字段。

## 6. Batch 5-D-A：rotate governance review

状态：completed。本批只读审计 credential rotate 生命周期设计，新增 `docs/current/CREDENTIAL_ROTATE_GOVERNANCE_REVIEW.md`，未修改 Java、Repository、Service、Controller、DTO、migration、API、前端、Python 或部署脚本。

只读结论：

- 当前 upsert 已具备最小轮换版本链：新增版本、旧 active 版本写为 `credential_status='ROTATED'` 且 `is_active=false`，新版本写为 `credential_status='ACTIVE'`、`verification_status='PENDING'`。
- active summary / active material 查询已同时要求 `is_active=true` 和 `credential_status='ACTIVE'`。
- rotate 目前仍不是显式 command，没有 rotate endpoint，也没有 `ROTATED` / `CREATED` audit log。
- V12 partial unique index 只保证同一 `exchange_account_id + credential_type` 一个 `is_active=true`，active material 查询不带 credential type 时仍需后续代码层冲突检测或单独 schema 约束决策。

Batch 5-D-B 落地事实：

- 已新增显式 `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/rotate` endpoint。
- rotate 请求体不接收 `credentialType`；`credentialType` 从旧 ACTIVE credential 派生，避免通过 rotate 任意切换凭证类型。
- rotate 必须要求 reason，actor 从认证主体解析；缺失认证主体时仍由 API 鉴权返回 unauthorized，Service 内部保留 `system` fallback 仅用于非 Web 调用保护。
- rotate 只允许从 `credential_status='ACTIVE' AND is_active=true` 派生；`REVOKED / DISABLED / EXPIRED / ROTATED` 派生 rotate 返回状态冲突。
- 单事务内完成旧 ACTIVE credential 锁定、旧 credential 标记 `ROTATED`、新 credential 创建为 `ACTIVE`、旧 credential `ROTATED` audit log、新 credential `CREATED` audit log。由于 V12 partial unique index 已保证同 account + credential type 仅一个 `is_active=true`，物理 SQL 顺序先标记旧 credential inactive 再插入新 active；任一步失败由事务回滚，避免成功响应留下无 active。
- rotate 后 active material 查询只返回新 `ACTIVE` credential；旧 credential 不可恢复。
- audit metadata 只保存 old/new credentialId、credentialType、credentialStatus、source 和 reasonPresent；不保存 secret、token、private key、passphrase、明文 payload 或交易所凭证。
- 本批未新增 migration，未修改历史 migration，未新增 enable endpoint，未做真实交易所权限探活，未接 AI、DH、LIVE 或真实交易路径，未修改前端、Python 或部署脚本。

## 7. Batch 5-E-A：active material selection review

状态：completed。本批只读审计 credential active summary / active material 查询是否需要显式 `credentialType` 或 `permission_scope` 过滤，新增 `docs/current/CREDENTIAL_ACTIVE_MATERIAL_SELECTION_REVIEW.md`，未修改 Java、Repository、Service、Controller、DTO、migration、API、前端、Python 或部署脚本。

只读结论：

- 当前 `findActiveSummary` 与 `findActiveMaterial` 已同时要求 `is_active=true` 和 `credential_status='ACTIVE'`，能排除 `DISABLED / REVOKED / EXPIRED / ROTATED`。
- 当前两个查询都不带 `credential_type` 或 `permission_scope`，并通过 `ORDER BY updated_at DESC LIMIT 1` 返回单条记录。
- V12 partial unique index 只保证同一 `exchange_account_id + credential_type` 最多一个 `is_active=true`，不是 account 全局 active 唯一。
- V29 已新增 `permission_scope`，但当前代码不写、不读、不过滤该字段；`NULL` 仍表示尚未由代码确认权限。
- 如果一个 account 未来同时存在多个 ACTIVE credential type，当前 active summary / active material 可能选中非调用方预期的 credential。

Batch 5-E-B 建议：

- 先做代码层最小修复：在多 active type 时显式 conflict，或让 active summary / active material 查询接收 `credentialType`。
- `verifyActive` 和 `GET /credentials/active` 不应继续在多 active type 下静默 `LIMIT 1`。
- 暂不把 `permission_scope` 作为强过滤条件，除非同批完成写入、校验、默认值和测试。
- 继续推迟 enable endpoint；DISABLED 恢复为 ACTIVE 会放大多 active 选择风险。

Batch 5-E-C schema 约束决策：

- 如果业务确认同一 account 全局只能有一个 active credential，可单独评估新增 `(exchange_account_id) WHERE is_active = TRUE AND credential_status = 'ACTIVE'` partial unique constraint。
- 如果业务允许 READ_ONLY 与 TRADE 或多个 credential type 并存，则不应加 account 全局唯一约束，应在代码/API 层显式选择 credential type 和权限范围。

## 8. Batch 5-E-B：deterministic active material selection

状态：completed。本批在不新增 migration 的前提下接入 deterministic active selection：多 ACTIVE credential type 不再通过 `ORDER BY updated_at DESC LIMIT 1` 静默选择，调用方可显式传入 `credentialType`，无 type 多候选返回 `409 STATE_CONFLICT`。

已落地范围：

- Repository port 新增 `listActiveSummaries(ownerUserId, exchangeAccountId)`、`findActiveSummary(ownerUserId, exchangeAccountId, credentialType)` 和 `findActiveMaterial(ownerUserId, exchangeAccountId, credentialType)`。
- 无 `credentialType` 的 `findActiveSummary` / `findActiveMaterial` 兼容单 active 候选；0 条返回空，多条抛出状态冲突，避免解密多份 material 或按更新时间选错。
- JDBC active summary 查询继续要求 `is_active=true` 和 `credential_status='ACTIVE'`；无 type 路径列出候选，不再使用 `ORDER BY updated_at DESC LIMIT 1`。
- `GET /api/exchange-accounts/{accountId}/credentials/active` 与 `POST /api/exchange-accounts/{accountId}/credentials/verify` 新增可选 `credentialType` 查询参数；多 ACTIVE type 且未指定时返回 409。
- `rotate / revoke / disable / expire` 核心语义保持不变；rotate 后按同 credential type 只读取新 ACTIVE credential，inactive lifecycle 状态不会进入 active material。

已覆盖测试：

- 单一 ACTIVE type 兼容旧 active summary / material 查询。
- 多 ACTIVE type 无 `credentialType` 时返回 conflict，不再静默 `LIMIT 1`。
- 指定 `credentialType` 时只返回或校验对应 ACTIVE credential；不存在的 type 返回空 / not found。
- `DISABLED / REVOKED / EXPIRED / ROTATED` 不可作为 active material。
- API response 不包含 encrypted payload、decrypted payload、secret、token、private key、passphrase 或 permission scope。
- 不新增 enable endpoint，不调用真实交易所。

本批未做：

- 未新增 migration，未修改历史 migration。
- 未把 `permission_scope` 作为交易权限判断；`permission_scope=NULL` 仍表示权限尚未由代码确认。
- 未修改前端、Python 或部署脚本。
- 未接 AI、DH、LIVE 或真实交易路径。

## 9. Batch 5-E-C：active credential uniqueness review

状态：completed。本批只读评估 `exchange_account_credentials` 是否需要从“account + credential_type active 唯一”升级为“account 全局 active 唯一”，新增 `docs/current/CREDENTIAL_ACTIVE_CREDENTIAL_UNIQUENESS_REVIEW.md`，未修改 Java、Repository、Service、Controller、DTO、API、migration、前端、Python 或部署脚本。

只读结论：

- V12 partial unique index 精确定义为 `uq_exchange_account_credentials_active_type ON exchange_account_credentials (exchange_account_id, credential_type) WHERE is_active = TRUE`。
- 该索引只保证同一 account + credential type active 唯一，不保证 account 全局 active 唯一。
- Batch 5-E-B 的代码层 conflict 已足够覆盖当前 GateJ/GateK-PLAN 边界下的非确定性 active material selection 风险。
- 当前不建议新增 account 全局 active unique constraint；保留多 credential type active 模型更利于未来 READ_ONLY / TRADE / 可能的 FUNDING 权限拆分。
- `permission_scope` 仍只作为治理元数据，当前不用于交易权限判断；后续接入需要单独定义写入、校验、默认值、`NULL` 语义和测试矩阵。
- 当前不需要 Batch 5-E-D migration；只有产品明确要求“一个 account 全局最多一个 ACTIVE credential”时，才单独设计数据冲突扫描、清理策略和 partial unique constraint。
- Batch 5-F enable endpoint 应继续推迟，先做 enable 只读审计，避免 DISABLED 恢复 ACTIVE 放大多 active 候选或 stale permission 风险。

## 10. Batch 5-F-A：enable governance review

状态：completed。本批只读审计 credential enable / re-enable 生命周期设计，新增 `docs/current/CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md`，未修改 Java、Repository、Service、Controller、DTO、API、migration、前端、Python 或部署脚本。

只读结论：

- 当前没有 enable endpoint，没有 enable Service 方法，也没有 `ENABLED` audit event。
- `DISABLED` 是唯一可考虑恢复的状态，但必须先做 owner 校验、同 account + credentialType active 冲突检测、结构性校验、actor/reason 审计和 append-only audit log。
- `REVOKED` 不可恢复；`ROTATED` 不可恢复；`EXPIRED` 默认不恢复，优先 rotate。
- enable 不应复用 `VERIFIED / USED / CREATED` audit event；Batch 5-F-B 已通过 schema-only migration 将 `ENABLED` 加入 `credential_audit_logs.event_type` CHECK，但这只代表审计事件 schema 已准备好，不代表 enable endpoint 已实现。
- enable API 推荐形态为 `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/enable`；`credentialType` 从 credentialId 对应记录派生，不建议由请求体传入。
- enable 不做真实交易所权限探活；真实权限探活应作为后续单独任务。
- `permission_scope=NULL` 不得当作 `TRADE`；`withdraw_enabled=false` 不阻止恢复，但不代表提现能力或 LIVE 交易。
- Batch 5-F-C 才允许单独实现 enable code/API/test；实现前仍需按 Batch 5-F-A 结论保留 owner 校验、同 account + credentialType active 冲突检测、结构性校验、actor/reason 审计和 append-only audit log。

## 11. Batch 5-F-B：credential enable audit event schema

状态：completed。本批只新增 `backend/nq-infra/src/main/resources/db/migration/V30__schema_credential_enable_audit_event.sql`，把 `credential_audit_logs.event_type` CHECK 增加 `ENABLED`，并同步 `credential_audit_logs` 表、`event_type` 和 `metadata` 注释。

已落地范围：

- 重建 `chk_credential_audit_logs_event_type`，允许值为 `CREATED / VERIFIED / FAILED_VERIFICATION / DISABLED / ENABLED / REVOKED / ROTATED / EXPIRED / USED / ACCESS_DENIED`。
- `credential_audit_logs.event_type` 注释明确：`ENABLED` 表示 `DISABLED` credential 经校验后重新启用。
- `credential_audit_logs.metadata` 注释继续声明不得保存 secret、token、API key、API secret、私钥、助记词、cookie、passphrase、签名、明文 payload 或交易所凭证。

本批未做：

- 未新增字段，未修改 `exchange_account_credentials` 字段，未做数据 backfill。
- 未修改 Java、Repository、Service、Controller、DTO 或 API。
- 未新增 enable endpoint，未新增 rotate / revoke / disable / expire 行为。
- 未修改前端、Python 或部署脚本。
- 未接 AI、DH、LIVE 或真实交易所私有链路。

## 12. Batch 5-F-C：credential enable command

状态：completed。本批在不新增 migration 的前提下实现最小 enable command：`POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/enable`。

已落地范围：

- Repository 新增按 owner + accountId + credentialId `SELECT ... FOR UPDATE` 读取 credential material 的内部方法；该 material 只在 Service 内用于结构性校验，不进入 API response、普通日志或 audit metadata。
- Repository 新增同 account + credentialType 其他 ACTIVE credential 检查，避免 enable 制造双 ACTIVE。
- Repository 新增 `markEnabled`，只在 `credential_status='DISABLED' AND is_active=false` 时写入 `credential_status='ACTIVE'`、`is_active=true`、`verification_status='VERIFIED'`、`last_verified_at` 和 `updated_at`。
- Service 单事务完成 owner/account 校验、目标 credential 锁定、状态检查、active 冲突检查、本地结构性校验、状态写回和 `ENABLED` audit log。
- API request body 只接收必填 `reason`；`credentialType` 从 credentialId 对应记录派生，不允许请求体传入。
- API response 只返回非敏感 `ExchangeAccountCredentialSummaryResponse`，不包含 encrypted payload、decrypted payload、secret、token、private key、passphrase 或 credential material。
- audit metadata 只保存 `credentialStatus`、`previousCredentialStatus`、`source`、`credentialType`、`reasonPresent`、`verificationStatus`，不保存 secret、token、API key、private key、passphrase、明文 payload 或交易所凭证。

已固定行为：

- 只允许 `DISABLED` 且 `is_active=false` credential enable。
- `ACTIVE / REVOKED / ROTATED / EXPIRED` enable 返回状态冲突；`REVOKED / ROTATED / EXPIRED` 仍不可恢复。
- 结构性校验失败时保持 `DISABLED`，不写 `ENABLED` audit log。
- `failed_auth_count` 不清零；该字段仍保留历史认证/权限失败计数，除非未来真实权限探活单独定义重置策略。
- 不清空 `revoked_at / rotated_at` 历史字段；如 DISABLED 记录带有 revoked/rotated 历史标记，enable 返回状态冲突。
- `permission_scope=NULL` 不得被当作 `TRADE`；enable 本身不判断交易权限，也不代表 LIVE 或提现能力。

本批未做：

- 未新增 migration，未修改历史 migration。
- 未做真实交易所权限探活，未调用真实交易所。
- 未新增真实交易、真实下单、真实撤单、reveal/decrypt/includeSecret 接口。
- 未修改前端、Python 或部署脚本。
- 未接 AI、DH、LIVE 或真实交易路径。

## 13. 后续安全审计重点

## 13. Batch 5-G：credential governance freeze review

状态：completed。本批只读复核 Batch 5-A ~ 5-F-C 的 schema、API、Service、Repository、audit log、测试和文档边界，新增 `docs/current/CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md`。

冻结结论：

- 允许条件冻结 Batch 5 credential governance。
- 无 P0 / P1 / P2 阻塞问题。
- 存在 P3 过期描述：disable endpoint OpenAPI description 仍写“本轮不提供 enable 接口”；`CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md` 作为 5-F-A 历史快照仍保留 enable 未实现语境。该问题不影响运行语义，建议后续 cleanup 批次修复。
- 不需要 P0/P1/P2 修复批次。
- 允许进入真实交易所权限探活设计审计，但下一批仍只允许设计审计，不得直接实现 permission probe。

本批未做：

- 未新增 migration，未修改历史 migration。
- 未修改 Java、Repository、Service、Controller、DTO 或 API。
- 未修改前端、Python 或部署脚本。
- 未接真实交易所、AI、DH、LIVE 或真实交易路径。
- 未实现 permission probe。

## 14. Permission probe schema-only

状态：completed。本批只新增 `backend/nq-infra/src/main/resources/db/migration/V31__schema_credential_permission_probe.sql`，为后续真实交易所权限探活准备 schema 和 audit event；未实现 Java/API/前端/Python/部署，未调用真实交易所。

已落地范围：

- `exchange_account_credentials` 新增 `permission_probe_status`，允许 `NOT_PROBED / IN_PROGRESS / SUCCEEDED / FAILED / SKIPPED`，默认 `NOT_PROBED`。
- `exchange_account_credentials` 新增 `last_permission_probe_at`，用于区分真实权限探活时间与 `last_verified_at` 结构性校验时间、`last_used_at` 业务使用时间。
- `exchange_account_credentials` 新增 `last_permission_probe_error`，只允许脱敏错误摘要或错误分类。
- `exchange_account_credentials` 新增 `ip_allowlist_probe_status`，允许 `NOT_CHECKED / PASSED / FAILED / UNKNOWN / SKIPPED`，默认 `NOT_CHECKED`。
- 扩展 `permission_scope` CHECK 支持 `FUNDING`，`NULL` 继续表示未确认权限，不等于 `TRADE`。
- 扩展 `credential_audit_logs.event_type` CHECK 支持 `PERMISSION_PROBE_STARTED / PERMISSION_PROBE_SUCCEEDED / PERMISSION_PROBE_FAILED / PERMISSION_PROBE_SKIPPED`。
- 更新新增字段、`withdraw_enabled`、`credential_audit_logs` 表、`event_type` 和 `metadata` COMMENT，继续声明不得保存 secret、token、API key、API secret、私钥、助记词、cookie、passphrase、签名、headers、request body、raw response、明文 payload 或交易所凭证。

withdraw constraint decision：

- 本轮未新增 `CHECK (withdraw_enabled = FALSE)`。
- 原因：V29 已有 `withdraw_enabled BOOLEAN NOT NULL DEFAULT FALSE`，但本轮未查询现有数据证明所有既有行均为 false；为避免破坏已有数据，本轮只更新注释和设计文档。
- `withdraw_enabled=true` 不得视为可接受生产状态；如未来要加硬约束，必须先单独执行数据确认和修复批次。

本批未做：

- 未修改历史 migration。
- 未修改 Java、Repository、Service、Controller、DTO 或 API。
- 未新增 permission probe endpoint。
- 未修改前端、Python 或部署脚本。
- 未调用 OKX、Binance、Bybit、Gate 或任何真实交易所。
- 未实现 permission probe。
- 未接 AI、DH、LIVE 或真实交易路径。

## 15. Permission probe code/API/test design review

状态：completed。本批只读审计后续 permission probe code/API/test 实现方案，新增 `docs/current/CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md`；未修改 Java、Repository、Service、Controller、DTO、API、migration、前端、Python 或部署脚本，未调用真实交易所，未实现 permission probe。

只读结论：

- V31 schema 足够支撑下一步最小 code/API/test implementation，不需要本轮 schema 补丁。
- 推荐新增独立 `ExchangeCredentialPermissionProbePort`，Service 只负责编排 owner/account/credential 校验、ACTIVE 校验、Paper safety gate、LIVE 禁止、状态写回、`failed_auth_count` 和 append-only audit log。
- 真实 HTTP 调用必须隔离在 adapter 层；adapter 只允许安全 read-only endpoint，禁止 order / cancel / transfer / withdraw endpoint。
- API response 与 audit metadata 只允许脱敏 summary，不得返回 raw exchange response、headers、signature、encrypted/decrypted payload、API key、secret、private key 或 passphrase。
- 测试必须使用 fake server / mock port / socket guard，证明 full Maven test 与 local Spring context 不访问 `www.okx.com` 或 `api.binance.com`。
- 允许进入单独 code implementation 批次，但该批次仍不得新增 migration、不得接 AI/DH/LIVE、不得调用真实交易所、不得下单/撤单/转账/提现。

## 16. 后续安全审计重点

- P0：真实密钥泄露、LIVE credential 被 Paper 路径误用、DH / Agent / AI 访问 credential。
- P1：撤销语义缺失、不可恢复撤销和临时禁用混淆、API 返回敏感字段、Paper / LIVE 隔离不清。
- P2：审计字段不足、轮换链上下文不足、权限范围记录不足、IP allowlist / withdraw disabled 证明缺失。
- P3：注释、命名、测试 fixture 和文档措辞不清。

## 17. 回滚与兼容原则

- Batch 5-B 新增字段通过后续 migration 回滚或废弃，不修改历史 migration。
- Batch 5-F-B 只改变 `credential_audit_logs.event_type` CHECK 和注释；如需回滚，应新增后续 migration 移除 `ENABLED` 并恢复注释，不修改历史 V30。
- Batch 5-F-C 为应用代码和文档变更；如需回滚，移除 enable endpoint、Service enable 方法、Repository enable 方法和相关测试/文档，不修改历史 migration。
- Batch 5-G 为只读复核和文档变更；如需回滚，移除 freeze review 文档和索引/日志记录，不修改历史 migration 或 Java。
- V31 permission probe schema 如需回滚，应新增后续 migration 移除 probe 字段、恢复 `permission_scope` CHECK 和 `credential_audit_logs.event_type` CHECK，不修改历史 V31。
- Permission probe code/API/test design review 为文档批次；如需回滚，删除 `CREDENTIAL_PERMISSION_PROBE_CODE_API_TEST_DESIGN_REVIEW.md` 并回退 README / WORKLOG / TESTING / plan 中对应索引和状态，不修改 Java 或 migration。
- 不删除已有 credential 版本记录。
- 不删除 audit log。
- `credential_status` 已新增，初始回填兼容现有 `is_active` 和 `verification_status`：
  - `is_active=true` 且 `verification_status<>REVOKED`：`ACTIVE`。
  - `verification_status=REVOKED` 或 `is_active=false`：按现有轮换旧版本语义回填为 `ROTATED`。
  - 其他历史异常组合保守落到 `DISABLED`，避免误判为可用凭证。

## 18. 与 GateK-PLAN 的关系

Credential revocation governance 是安全和数据治理工作，不代表 GateK 实现已启动。即使 GateK-PLAN 后续规划 AI 信号接入，AI / Agent / DH 也不得访问 credential、master key、decrypted payload 或 revoke/audit API，除非未来单独安全设计、审批和验证。
