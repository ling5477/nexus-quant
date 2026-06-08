# Credential Governance Freeze Review

任务：NQ-DB-SCHEMA-GOVERNANCE-BATCH-5G-CREDENTIAL-GOVERNANCE-FREEZE-REVIEW
日期：2026-06-08
状态：Batch 5-G freeze review completed；Batch 5 credential governance conditionally freezable；follow-up doc/comment cleanup recommended。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

## 1. Scope

本轮只读复核 Batch 5-A 到 Batch 5-F-C 的 credential lifecycle governance，实现边界覆盖：

- `V29__schema_credential_revocation_governance.sql`
- `V30__schema_credential_enable_audit_event.sql`
- credential Repository / JDBC
- credential CommandService / VerificationService
- credential Controller / DTO
- credential tests
- `docs/current/API.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/CREDENTIAL_*` 文档
- `README.md` / `docs/current/README.md` / `WORKLOG.md` / `TESTING.md`

本轮未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本；未接真实交易所、AI、DH、LIVE；未实现 permission probe；未读取或输出真实密钥。

## 2. Freeze Conclusion

结论：允许条件冻结 Batch 5 credential governance。

冻结依据：

- schema：V29 拆分 `credential_status` 与 `verification_status`，V30 为 audit event 增加 `ENABLED`，均有 CHECK / COMMENT / 敏感信息禁入边界。
- API：credential response 只返回非敏感 summary；create/rotate request 可接收 credential material，但 response 不返回 material；lifecycle command request 只接收 reason；enable request reason 必填。
- Service：revoke / disable / expire / rotate / enable 都通过 `credential_status` 驱动生命周期，不再让 `verification_status` 承载生命周期语义。
- Repository：active summary / active material 查询均要求 `is_active=true AND credential_status='ACTIVE'`；多 active type 无 `credentialType` 时 conflict，指定 `credentialType` 时显式选择。
- audit：lifecycle / rotate / enable metadata 只保存脱敏状态、来源、credentialType、reasonPresent、verificationStatus 等上下文，不保存 credential material。
- tests：已覆盖 revoke / disable / expire / rotate / enable、active material selection、response 脱敏、audit metadata 脱敏和 permission_scope 不参与交易权限判断。
- docs：`API.md` 与 `DB_SCHEMA.md` 已准确记录当前只做本地结构性校验，不代表真实交易所权限可用。

条件：存在 P3 级文档/注释过期项，应在后续 cleanup 批次修复，但不阻塞冻结。

## 3. Required Review Checklist

| # | 检查项 | 结论 | 证据 |
| --- | --- | --- | --- |
| 1 | API response 不含 encrypted/decrypted payload、secret、token、private key、passphrase | PASS | `ExchangeAccountCredentialSummaryResponse` 只包含 id、type、masked key、lifecycle、verification、时间字段；Controller tests 断言敏感字段不存在。 |
| 2 | audit metadata 不含敏感 material | PASS | `lifecycleMetadata`、`rotateMetadata`、`enableMetadata` 均只写脱敏状态和上下文；tests 覆盖 metadata 不含 secret/token/private/passphrase。 |
| 3 | revoke / disable / expire / rotate / enable 都有测试 | PASS | `ExchangeAccountCredentialCommandServiceTest` 覆盖五类 lifecycle command；Controller / JDBC tests 覆盖 API response 与 SQL 语义。 |
| 4 | active material 只读 ACTIVE + is_active=true | PASS | JDBC `findActiveMaterial`、active summary、rotate lock 路径均包含 `is_active = TRUE` 与 `credential_status = 'ACTIVE'`。 |
| 5 | 多 active type conflict 或显式 credentialType | PASS | Repository default path 多候选抛 `multiple active credential types require credentialType`；API 支持 `credentialType` query param。 |
| 6 | rotate 是否 old=ROTATED、new=ACTIVE | PASS | `markRotated` 写旧 `ROTATED / is_active=false`；`insertNewVersion` 写新 `ACTIVE / is_active=true`；tests 断言 old/new 与 audit。 |
| 7 | enable 是否只允许 DISABLED | PASS | Service 要求 `credential_status='DISABLED'` 且 `is_active=false`；JDBC `markEnabled` 同样带该 WHERE 条件。 |
| 8 | REVOKED / ROTATED / EXPIRED 是否不可 enable | PASS | Service 拒绝非 DISABLED；tests 覆盖 `ACTIVE / REVOKED / ROTATED / EXPIRED` enable rejected。 |
| 9 | verification_status 是否不再承载生命周期语义 | PASS | V29 新增 `credential_status`；Service lifecycle command 写 `credential_status`，不再把轮换旧版本写成 `verification_status='REVOKED'`。 |
| 10 | permission_scope=NULL 是否未被当作 TRADE | PASS | 应用代码不读取或写入 `permission_scope`；tests 断言 SQL 不含 `permission_scope` 且 enable metadata 不含 `TRADE`。 |
| 11 | failed_auth_count 是否未被错误清零 | PASS | enable / verify / lifecycle SQL 未更新 `failed_auth_count`；DB_SCHEMA 明确 enable 不清零该字段。 |
| 12 | V29/V30 与 DB_SCHEMA.md 是否一致 | PASS | DB_SCHEMA 记录 V29 字段、CHECK、metadata 禁入；记录 V30 只重建 event_type CHECK 增加 `ENABLED`，不改 credential 字段、不 backfill。 |
| 13 | API.md 是否准确标注当前只是本地结构性校验，不代表真实交易所权限可用 | PASS | API.md 说明 verify / enable 只做结构性校验，且当前未新增真实交易所权限探活、LIVE 或真实交易路径。 |
| 14 | 是否仍存在旧代码路径绕过 credential_status | PASS with P3 note | active material 读取未发现绕过；但 Controller 中 disable 的 OpenAPI description 仍写“本轮不提供 enable 接口”，属于过期说明，不是运行时绕过。 |

## 4. Findings

### P0

无。

未发现 API response 返回 credential material、audit metadata 保存 credential material、AI/DH/LIVE 访问 credential、真实交易所权限探活或真实交易路径接入。

### P1

无。

未发现阻塞冻结的 schema、Service 状态流转、active material selection、audit log 或测试缺口。

### P2

无。

当前 `permission_scope` 与 `failed_auth_count` 仍是治理元数据，未被误用为交易权限或 enable 成功后自动重置。

### P3

- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialController.java` 中 disable endpoint 的 OpenAPI description 仍写着“本轮不提供 enable 接口”。该文字已与 Batch 5-F-C 后事实不一致，但不影响运行时行为；建议单独 cleanup 批次修复，避免 API 文档生成误导。
- `docs/current/CREDENTIAL_ENABLE_GOVERNANCE_REVIEW.md` 是 Batch 5-F-A 历史审计快照，文件头和若干段落仍描述 enable 尚未实现。当前 `docs/current/README.md`、`API.md`、`DB_SCHEMA.md`、`CREDENTIAL_REVOCATION_GOVERNANCE_PLAN.md` 已记录 5-F-C 实现事实；建议后续只补充历史快照说明，不重写 5-F-A 原始结论。

## 5. Freeze Decision

是否允许冻结 Batch 5 credential governance：允许，条件冻结。

是否需要修复批次：不需要 P0/P1/P2 修复批次；建议开一个 P3 cleanup 批次，只修正文档/注释过期描述，不改业务逻辑。

是否允许进入真实交易所权限探活设计审计：允许进入只读设计审计。下一批仍不得直接实现 permission probe；应先完成设计审计，明确外部权限探活不会泄露 material、不会触发真实交易、不会绕过 Paper/LIVE 隔离。

## 6. Follow-up Tasks

建议后续任务：

1. `NQ-DB-SCHEMA-GOVERNANCE-BATCH-5G-A-CREDENTIAL-GOVERNANCE-DOC-CLEANUP`：只修正过期 OpenAPI description 与 5-F-A 历史快照说明，不改 Service / Repository / migration。
2. `NQ-CREDENTIAL-PERMISSION-PROBE-DESIGN-REVIEW`：只读设计真实交易所权限探活，输出 READ_ONLY / TRADE / withdraw / IP allowlist / failed_auth_count 策略，不实现代码。
3. `NQ-CREDENTIAL-PERMISSION-SCOPE-WRITE-POLICY-REVIEW`：评估 `permission_scope=NULL` 到显式权限状态的迁移策略、测试矩阵和是否需要新 migration。

## 7. Validation

本轮执行：

- `git diff --check`

本轮未执行：

- `mvn -f backend/pom.xml test`

未执行 Maven 原因：本轮只做 `CODE_ANALYSIS + DOCUMENTATION`，未修改 Java、migration、API、前端、Python 或部署脚本；测试覆盖结论来自只读检查测试文件与上一轮 `TESTING.md` 已记录的实际验证结果，不把本轮未执行测试写成通过。

## 8. Boundary Confirmation

- 未新增 migration。
- 未修改历史 migration。
- 未修改 Java、Repository、Service、Controller、DTO 或 API。
- 未修改前端、Python 或部署脚本。
- 未接真实交易所。
- 未接 AI、DH、LIVE。
- 未读取或输出真实密钥、API key、secret、token、私钥、助记词、cookie、passphrase、encrypted payload 或 decrypted payload。
- 未实现 permission probe。
