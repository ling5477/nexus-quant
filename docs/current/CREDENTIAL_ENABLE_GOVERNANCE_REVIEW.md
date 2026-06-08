# Credential Enable Governance Review

任务：NQ-DB-SCHEMA-GOVERNANCE-BATCH-5F-A-CREDENTIAL-ENABLE-REVIEW
日期：2026-06-07
状态：Batch 5-F-A review completed；enable endpoint not implemented；Batch 5-F-B schema migration required before enable implementation。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

> 历史快照说明：本文是 Batch 5-F-A 只读审计快照，记录 2026-06-07 当时 enable endpoint 尚未实现、且需要先完成 Batch 5-F-B schema-only `ENABLED` audit event 准备的结论。当前事实以 Batch 5-F-C enable command 实现和 Batch 5-G freeze review 为准；最新入口见 `docs/current/CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md` 与 `docs/current/CREDENTIAL_GOVERNANCE_FREEZE_REVIEW.md`。

## 1. Scope

本轮只读审计 credential enable / re-enable 生命周期设计，判断是否允许从 `DISABLED` 恢复为 `ACTIVE`，以及后续 enable 需要哪些前置校验、冲突检测、audit log 和测试。

本轮只写文档，不新增 migration，不修改历史 migration，不修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本；不新增 enable endpoint；不调用真实交易所；不接 AI、DH、LIVE 或真实交易路径；不读取或输出真实密钥、API key、secret、token、私钥、助记词、cookie、passphrase、encrypted payload 或 decrypted payload。

## 2. Current Lifecycle Behavior

- V12 建立 `exchange_account_credentials`，`credential_type` 允许 `OKX_API_V5 / BINANCE_HMAC / BINANCE_ED25519`，并通过 partial unique index 保证同一 `exchange_account_id + credential_type` 最多一条 `is_active=true`。
- V29 新增 `credential_status`，允许 `ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED`。
- V29 新增 `credential_audit_logs`，当前 `event_type` 允许 `CREATED / VERIFIED / FAILED_VERIFICATION / DISABLED / REVOKED / ROTATED / EXPIRED / USED / ACCESS_DENIED`，不包含 `ENABLED`。
- Batch 5-C 已实现 `revoke / disable / expire`：三者都会让 credential 退出 active material；`REVOKED` 写 `revoked_at / revoked_by / revoke_reason`，`DISABLED / EXPIRED` 不写 revoke 字段。
- Batch 5-C 当前阻止非 `REVOKED` 目标从 `REVOKED / ROTATED` 状态转出，因此 `REVOKED / ROTATED` 已具备不可恢复语义。
- Batch 5-D-B rotate 只允许从 `credential_status='ACTIVE' AND is_active=true` 的旧 credential 派生；旧 credential 标记 `ROTATED`，新 credential 创建为 `ACTIVE`，并追加旧 `ROTATED` / 新 `CREATED` audit log。
- Batch 5-E-B active summary / active material 查询要求 `credential_status='ACTIVE' AND is_active=true`；无 `credentialType` 多候选返回 conflict，显式 `credentialType` 只读取对应 ACTIVE credential。
- 当前没有 enable endpoint，没有 enable Service 方法，没有 `ENABLED` audit event，也没有针对 `DISABLED` credential 的 by-credential material verification 读取路径。

## 3. Enable Recommendation

不建议在当前批次实现 enable。后续如要实现，应拆成至少两个批次：

- Batch 5-F-B：schema-only migration，给 `credential_audit_logs.event_type` 增加 `ENABLED`，并更新注释；不改 Java/API。
- Batch 5-F-C：最小 code/API/test 接入 enable；只允许 `DISABLED` 在严格校验后恢复为 `ACTIVE`。

推荐原则：

- `DISABLED` 可考虑恢复，但必须通过 owner 校验、同 type active 冲突检测、结构性校验、actor/reason 审计和 `ENABLED` audit log。
- `REVOKED` 永久不可恢复。
- `ROTATED` 永久不可恢复。
- `EXPIRED` 默认不可恢复，优先通过 rotate 创建新版本；只有未来业务明确需要时再单独评估。
- enable 不做真实交易所权限探活；真实权限探活应作为后续单独任务。
- enable 不读取或输出 secret；即使内部结构性校验需要读取 material，也不得进入 API response、日志或 audit metadata。

## 4. Recoverable States

- `DISABLED`：唯一推荐可恢复状态。语义是临时停用，理论上可在重新校验后恢复。

恢复前置条件：

- request 必须绑定 `accountId + credentialId`，不建议做 account-level enable。
- credential 必须属于当前 owner 的 exchange account。
- credential 当前状态必须为 `DISABLED` 且 `is_active=false`。
- credential 不应带有不可恢复语义字段；如存在 `revoked_at` 或 `rotated_at` 的异常组合，应返回状态冲突而不是清空字段。
- service 必须从 credential 记录派生 `credentialType`，并检查同一 `exchange_account_id + credential_type` 当前没有其他 ACTIVE credential。
- 必须执行结构性校验；结构性校验失败时保持 `DISABLED`，不得激活。
- 必须写 append-only audit log。

## 5. Non-Recoverable States

- `REVOKED`：不可恢复。该状态表示安全撤销，必须保留 `revoked_at / revoked_by / revoke_reason` 证据，不允许 enable。
- `ROTATED`：不可恢复。该状态表示已被新版本替代，恢复会破坏版本链和 rotate audit 语义。
- `EXPIRED`：默认不可恢复。过期通常说明时间或外部策略不可用，推荐 rotate 到新 credential；如未来需要恢复，必须另做审计和测试。
- `ACTIVE`：不需要 enable；重复 enable 应幂等返回当前摘要还是返回冲突需后续产品决定，推荐返回状态冲突，避免掩盖重复操作。

## 6. Schema Impact

如果后续实现 enable，需要新增 migration。原因不是 lifecycle 状态缺值，而是 audit log event type 缺值：

- `exchange_account_credentials.credential_status` 已包含 `ACTIVE`，无需新增 lifecycle 状态。
- `credential_audit_logs.event_type` CHECK 当前不包含 `ENABLED`。
- enable 是独立生命周期事件，不应复用 `VERIFIED`；`VERIFIED` 表示校验结果，不表示恢复可用。
- enable 不应复用 `USED`；`USED` 表示使用事件，不表示生命周期状态变化。
- enable 不应复用 `CREATED`；`CREATED` 只表示新版本创建。

推荐 Batch 5-F-B：schema-only migration 修改 `credential_audit_logs.event_type` CHECK，增加 `ENABLED`，并同步 `COMMENT ON COLUMN credential_audit_logs.event_type`。本轮不新增该 migration。

## 7. Recommended API Design

后续如开工，推荐 API 形态：

```text
POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/enable
```

请求体建议复用 lifecycle reason 形态，但 reason 应必填：

```json
{
  "reason": "operator approved re-enable after local verification"
}
```

设计边界：

- 不建议请求体接收 `credentialType`；`credentialId` 已唯一定位 credential，Service 必须从旧记录派生 `credentialType`，避免调用方伪造或切换 type。
- 如果未来设计 account-level enable，则必须要求 `credentialType`，但不推荐该形态。
- response 只返回 `ExchangeAccountCredentialSummaryResponse` 非敏感摘要。
- response 不返回 encrypted payload、decrypted payload、secret、token、private key、passphrase、cookie 或任何 credential material。

## 8. Recommended Transaction Semantics

后续 Service 事务建议：

1. 解析当前认证主体，确认 owner / account 归属；如未来引入角色权限，应要求 owner 或具备 credential lifecycle 管理权限的 ADMIN / OPERATOR。
2. 按 `accountId + credentialId` 读取 credential 摘要并锁定目标行。
3. 只允许当前状态为 `DISABLED` 且 `is_active=false`。
4. 拒绝 `REVOKED / ROTATED / EXPIRED / ACTIVE`。
5. 从目标 credential 派生 `credentialType`。
6. 检查同一 `exchange_account_id + credential_type` 是否已有其他 `credential_status='ACTIVE' AND is_active=true`；有则返回状态冲突，避免触发 DB partial unique violation。
7. 对目标 credential 执行结构性校验；结构性校验失败时保持 `DISABLED`，更新 `verification_status='FAILED' / last_verified_at / last_verification_error`，并可写 `FAILED_VERIFICATION` audit。
8. 结构性校验成功后写 `verification_status='VERIFIED' / last_verified_at`，再写 `credential_status='ACTIVE' / is_active=true / updated_at`。
9. 写 `ENABLED` audit log。
10. 返回非敏感摘要。

字段处理建议：

- `last_verified_at`：只有实际执行结构性校验时更新；不得为了 enable 假写。
- `failed_auth_count`：不建议在结构性校验成功后清零。该字段代表认证或权限失败历史，只有未来真实权限探活成功且有明确策略时再重置。
- `revoked_at / revoked_by / revoke_reason`：不得清空；目标为 `DISABLED` 时理论上应为空，非空视为异常组合并返回冲突。
- `rotated_at / rotated_by`：不得清空；目标为 `DISABLED` 时理论上应为空，非空视为异常组合并返回冲突。
- `withdraw_enabled=false`：不阻止 enable；该字段默认 false 是更安全状态，不代表可提现或 LIVE 可用。
- `permission_scope=NULL`：可作为“权限未知”的 active credential 恢复，但不得被解释为 `TRADE`。未来交易或真实权限路径必须显式要求 `TRADE` 或权限探活结果。

## 9. Recommended Audit Log Behavior

enable 必须写 append-only audit log。

推荐 event：

- `event_type='ENABLED'`。

推荐 metadata：

```json
{
  "credentialStatus": "ACTIVE",
  "previousCredentialStatus": "DISABLED",
  "source": "credential_enable_command",
  "credentialType": "OKX_API_V5",
  "reasonPresent": true,
  "verificationStatus": "VERIFIED"
}
```

metadata 禁止包含：

- encrypted payload。
- decrypted payload。
- api key。
- secret key。
- token。
- private key。
- passphrase。
- cookie。
- request body material。
- 签名或真实交易所响应敏感上下文。

## 10. Findings

### P0

无。

本轮未发现 enable endpoint 已实现、API 暴露 material、audit metadata 保存 secret、AI/DH/Agent 访问 credential、LIVE 路径读取 credential 的证据。

### P1

- V29 `credential_audit_logs.event_type` 不包含 `ENABLED`；如果不先做 schema migration，enable 无法用独立 audit event 正确落库。
- enable 如不检查同一 account + credentialType ACTIVE 冲突，可能触发 partial unique violation 或制造双 active 竞态。
- enable 如不重新结构性校验，可能恢复 stale / malformed credential。
- enable 如允许 `REVOKED / ROTATED / EXPIRED` 恢复，会破坏安全撤销、版本链和过期语义。

### P2

- 当前 `permission_scope` 未接入应用层；`NULL` 不得当作 `TRADE`。
- `failed_auth_count` 是否清零需要真实权限探活语义支持；当前结构性校验不足以证明认证失败历史已消除。
- enable 如果不要求 reason / actor，会削弱安全审计。
- 当前没有针对 DISABLED credential 的 by-credential material verification 读取路径；后续实现需要新增内部 Repository/Service 方法，但不得暴露 API material。

### P3

- 后续文档需要持续区分 `VERIFIED` 与 `ENABLED`：前者是校验结果，后者是 lifecycle 恢复事件。
- 如果未来需要恢复 `EXPIRED`，应单独设计，不要顺手并入 DISABLED enable。
- 多 credential type active 模型保留后，无 `credentialType` 的 active summary / material 仍会在多候选下 conflict，这是预期安全行为。

## 11. Recommended Tests

后续实现 enable 时至少覆盖：

- `DISABLED` enable 成功：状态变为 `ACTIVE`，`is_active=true`，写 `ENABLED` audit。
- `DISABLED` enable 前同 account + credentialType 已有 ACTIVE：返回 409，不改变状态。
- `REVOKED` enable：拒绝，不清空 `revoked_at / revoked_by / revoke_reason`。
- `ROTATED` enable：拒绝，不破坏 rotated chain。
- `EXPIRED` enable：默认拒绝，提示 rotate。
- `ACTIVE` 重复 enable：返回冲突或明确幂等语义，不重复写 audit。
- 结构性校验失败：保持 `DISABLED`，不得 active，可写 `FAILED_VERIFICATION`。
- 结构性校验成功：更新 `verification_status / last_verified_at`。
- `failed_auth_count`：结构性校验 enable 不清零。
- `permission_scope=NULL`：enable 后不得被当作 `TRADE`。
- `withdraw_enabled=false`：允许恢复为 active，但不代表提现能力或 LIVE 交易。
- API response 不包含 encrypted/decrypted payload、secret、token、private key、passphrase。
- audit metadata 不包含任何 credential material。
- owner 越权访问失败；如引入角色权限，ADMIN/OPERATOR/owner 边界分别覆盖。
- 多 ACTIVE type 场景：enable 后无 `credentialType` 的 active summary / material 仍按 5-E-B 规则返回 conflict。

## 12. Batch 5-F-B Decision

Batch 5-F-B 可以开工，但范围应是 schema-only migration，不实现 enable API。

建议 5-F-B 范围：

- 新增 migration，修改 `credential_audit_logs.event_type` CHECK，增加 `ENABLED`。
- 更新 `credential_audit_logs.event_type` column comment。
- 更新 `DB_SCHEMA.md / CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md / WORKLOG.md / TESTING.md`。
- 不改 Java、API、Repository、Service、Controller、DTO、前端、Python 或部署脚本。

Batch 5-F-C 才允许在单独任务中实现 enable code/API/test，并必须以 5-F-A 本审计和 5-F-B schema 为前置。

## 13. Boundary Confirmation

- 本轮未新增 migration，未修改历史 migration。
- 本轮未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本。
- 本轮未新增 enable endpoint，未把本轮审计写成 enable 已实现。
- 本轮未调用真实交易所，未做真实交易所权限探活。
- 本轮未接 AI、DH、LIVE 或真实交易路径。
- 本轮未读取或输出真实 credential material、secret、token、private key、passphrase、cookie 或助记词。
