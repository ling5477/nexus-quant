# Credential Rotate Governance Review

任务：NQ-DB-SCHEMA-GOVERNANCE-BATCH-5D-A-CREDENTIAL-ROTATE-REVIEW
日期：2026-06-07
状态：Batch 5-D-A review completed；Batch 5-D-B rotate endpoint not implemented。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

## 1. 结论

本轮只读审计确认：现有 credential upsert 已具备最小轮换版本链能力，做法是新增 credential 版本，并把同一 `exchange_account_id + credential_type` 的旧 active 版本标记为 `credential_status='ROTATED'`、`is_active=false`。Batch 5-C 也已经让 active summary / active material 查询同时要求 `is_active=true` 和 `credential_status='ACTIVE'`，避免 `DISABLED / REVOKED / EXPIRED / ROTATED` 进入 active material。

Batch 5-D-B 可以开工，但应作为明确 rotate command 实现，不应继续把 rotate 混在普通 upsert 语义里。最小实现必须保证同一事务内完成旧 active 锁定、新版本创建、旧版本 `ROTATED` 标记、`ROTATED` audit log、新版本 `CREATED` audit log，并确保 API response 和 audit metadata 不包含 `encrypted_payload`、secret、token、private key、passphrase、cookie、decrypted payload 或 request body 明文。

本轮未新增 migration，未修改 Java、Repository、Service、Controller、DTO、前端、Python 或部署脚本，未新增 rotate endpoint 或 enable endpoint。

## 2. 审计范围

只读检查文件：

- `backend/nq-infra/src/main/resources/db/migration/V12__rc1_account_and_credentials.sql`
- `backend/nq-infra/src/main/resources/db/migration/V29__schema_credential_revocation_governance.sql`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialVerificationService.java`
- `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/port/ExchangeAccountCredentialRepository.java`
- `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepository.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialController.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/ExchangeAccountCredentialSummaryResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/ExchangeAccountActiveCredentialResponse.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/ExchangeAccountCredentialUpsertRequestBody.java`
- `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/ExchangeAccountCredentialLifecycleRequestBody.java`
- `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandServiceTest.java`
- `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepositoryTest.java`
- `backend/nq-api/src/test/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialControllerWebMvcTest.java`

未检查也不得检查：`.env`、secrets、credentials、logs、dump、backup、`target`、node_modules、dist、build、`.git`。

## 3. 当前 rotate 相关已有能力

- V12 已有 `exchange_account_credentials` 版本表、`encrypted_payload`、`verification_status`、`is_active`、`revoked_at`、`rotated_from_credential_id`，并通过 partial unique index 限制同一 `exchange_account_id + credential_type` 只有一条 `is_active=true` 记录。
- V29 已新增 `credential_status`，允许 `ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED`，并新增 `rotated_at / rotated_by` 与 `credential_audit_logs`。
- `upsert` 当前在 `@Transactional` 中执行：读取同类型 active、旧 active 版本退 active、插入新 active 版本。
- 旧 active 退 active 时，Repository 写入 `credential_status='ROTATED'`、`is_active=false`、`rotated_at=now`，并保留 `verification_status` 原值，不再把校验状态写成 `REVOKED`。
- 新 credential 插入时写入 `credential_status='ACTIVE'`、`verification_status='PENDING'`、`is_active=true`、`rotated_from_credential_id=oldCredentialId`。
- active summary / active material 查询均要求 `is_active=true` 且 `credential_status='ACTIVE'`。
- revoke / disable / expire 已有显式 command 和 append-only audit log；rotate 目前没有显式 endpoint，也没有 rotate audit log。
- API 摘要响应只返回 masked access key 与生命周期/校验元数据，不返回 encrypted payload 或 decrypted payload。

## 4. 当前风险点

### P0

无确认型 P0。本轮未发现 API response 返回 encrypted payload、secret、token、private key、passphrase 或 decrypted payload 的证据。

### P1

- rotate 目前仍由普通 upsert 隐式触发，没有显式 rotate command，无法强制 reason、actor、旧 credential 状态校验和 rotate 审计语义。
- upsert 轮换旧 active 版本时不写 `credential_audit_logs event_type='ROTATED'`，新 credential 也不写 `CREATED` audit log，导致轮换链缺少 append-only 证据。
- V12 partial unique index 只限制同一 `exchange_account_id + credential_type` 的 `is_active=true`，而 active material 查询不带 `credential_type` 且 `LIMIT 1`，如果同一 account 出现多种 active credential type，会产生非确定性 active material。
- rotate 失败原子性目前依赖 Service `@Transactional` 和既有 partial unique index；缺少面向 `credential_status='ACTIVE'` 的唯一约束或显式并发锁设计说明。

### P2

- `rotated_by` 字段已存在，但当前 upsert 轮换不会写入操作者。
- `deactivateActiveByAccountAndType` 当前仍同时写 `revoked_at` 和 `rotated_at`；文档语义已把 `REVOKED` 与 `ROTATED` 拆开，后续实现必须避免把 `revoked_at` 当成不可恢复撤销证据。
- `verification_status` 当前仅用于 `PENDING / VERIFIED / FAILED` 回写，生命周期语义已经拆到 `credential_status`，但历史 V12 check 仍允许 `verification_status='REVOKED'`，后续测试需持续防止新代码再写入该旧生命周期含义。
- 当前 sensitive reason 拒绝覆盖常见英文和中文关键词，但 rotate 新请求体也必须复用该校验，并新增 audit metadata 脱敏断言。
- 真实交易所权限探活、read-only/trade 权限确认、withdraw disabled 证明和 IP allowlist 证明仍未实现；若需要，应拆为后续单独任务。

### P3

- 文档和 API 命名需要把 upsert 与 rotate 区分清楚，避免调用方把普通新增理解为已经完整安全轮换。
- 测试中还需要补充 rotate 幂等、并发、失败回滚、旧状态拒绝、response 脱敏和 audit metadata 脱敏矩阵。
- enable endpoint 如未来需要，应单独设计，不应和 rotate endpoint 混做。

## 5. 建议的 Batch 5-D-B 最小实现范围

建议 Batch 5-D-B 只做 rotate endpoint、Service 事务语义、Repository 最小方法和测试，不新增真实交易所探活、不新增 enable、不改前端、不接 AI/DH/LIVE。

最小范围：

- 新增显式 rotate command，不再把安全轮换只表达为普通 upsert。
- rotate 只允许从当前 `credential_status='ACTIVE'` 且 `is_active=true` 的旧 credential 派生。
- rotate 不允许从 `REVOKED / DISABLED / EXPIRED / ROTATED` credential 派生。
- rotate 后旧 credential 不可恢复；若未来需要恢复，只能通过新 rotate/upsert 创建新版本，不得把旧版本重新置为 active。
- rotate 必须要求 reason；actor 必须从认证主体解析，不允许 request body 伪造。
- 新 credential 初始 `verification_status='PENDING'`；只有实际执行结构性校验且成功时才能写 `VERIFIED`。
- 继续禁止真实交易所权限探活；如需探活，应另开任务并先完成权限、超时、审计和脱敏设计。

## 6. 建议的 API 形态

推荐：

```text
POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/rotate
```

请求体建议：

```json
{
  "reason": "operator scheduled rotation",
  "apiKey": "<redacted>",
  "secretKey": "<redacted or null>",
  "passphrase": "<redacted or null>",
  "privateKeyPem": "<redacted or null>"
}
```

设计约束：

- `credentialId` 必须是当前 account 下的 active credential。
- `credentialType` 建议从旧 credential 派生，避免请求体伪造类型导致跨类型轮换。
- 响应只返回新 credential 的 `ExchangeAccountCredentialSummaryResponse`。
- 响应不得包含 `encryptedPayload`、`secretKey`、`token`、`privateKeyPem`、`passphrase`、`decryptedPayload` 或 request body 明文。
- 不新增 enable endpoint；enable 如需做，必须独立任务评估 `DISABLED` 的恢复边界。

## 7. 建议的 Service 事务语义

rotate 必须在一个 `@Transactional` 边界内完成：

1. 校验 owner 与 account。
2. 按 `credentialId + accountId` 读取旧 credential，并确认 `credential_status='ACTIVE'`、`is_active=true`。
3. 对旧 active 记录加行级锁，或使用带状态条件的 update 作为乐观锁闸门。
4. 校验新 credential payload 与旧 credential type 匹配。
5. 插入新 credential：`credential_status='ACTIVE'`、`verification_status='PENDING'`、`is_active=true`、`rotated_from_credential_id=oldCredentialId`。
6. 更新旧 credential：`credential_status='ROTATED'`、`is_active=false`、`rotated_at=now`、`rotated_by=actor`、`updated_at=now`。
7. 追加旧 credential 的 `ROTATED` audit log。
8. 追加新 credential 的 `CREATED` audit log。
9. 返回新 credential 摘要。

失败回滚要求：

- 插入新 credential 失败时，旧 credential 仍保持原 active。
- 旧 credential 更新失败时，新 credential 插入必须回滚，不能留下两个 active。
- audit log 写入失败时，状态更新和新版本插入必须回滚，不能留下无审计的 rotate。
- 并发 rotate 时，第二个请求必须得到明确冲突或基于当前 active 重新执行，不能静默产生两个 active 或无 active。

## 8. 建议的 Repository 方法

最小方法建议：

- `findByCredentialIdForRotateForOwner(ownerUserId, exchangeAccountId, credentialId)`：读取旧 credential，并带 owner 校验；实现可使用 `FOR UPDATE` 或等价锁定策略。
- `insertRotatedVersion(exchangeAccountId, credentialType, encryptedPayloadJson, keyVersion, cipherSuite, maskedAccessKey, rotatedFromCredentialId, now)`：插入新版本。
- `markRotated(credentialId, exchangeAccountId, actor, reason, now)`：只在旧 credential 当前仍 `is_active=true` 且 `credential_status='ACTIVE'` 时更新为 `ROTATED`。
- `appendCredentialAuditLog(...)`：复用现有 append-only 写入，但 metadata 必须只保存脱敏状态、old/new credential id、request id 和 source。
- `findActiveMaterial` 后续应避免 `LIMIT 1` 静默吞掉多 active；若业务要求同一 account 只有一个 active credential，应增加代码层冲突检测或后续 schema 唯一约束。

唯一约束建议：

- 现有 DB 已保证同一 account + credential type 只有一个 `is_active=true`。
- 如果业务确认同一 account 任意 credential type 也只能有一个 active material，则需要后续单独 migration 增加 partial unique constraint：`(exchange_account_id) WHERE is_active = TRUE AND credential_status = 'ACTIVE'`。
- 若 Batch 5-D-B 不新增 migration，则必须在 Service/Repository 层拒绝同一 account 出现多条 active material 的异常状态，并写入测试覆盖。

## 9. 建议的 audit log 行为

rotate 必须写两类 audit：

- 旧 credential：`event_type='ROTATED'`，actor 为认证主体，reason 为 rotate reason，metadata 记录脱敏 `oldCredentialId`、`newCredentialId`、`credentialStatus='ROTATED'`、`source='credential_rotate_command'`。
- 新 credential：`event_type='CREATED'`，actor 为认证主体，reason 同 rotate reason 或 `created by rotate`，metadata 记录脱敏 `oldCredentialId`、`newCredentialId`、`credentialStatus='ACTIVE'`、`verificationStatus='PENDING'`、`source='credential_rotate_command'`。

不建议在 rotate 创建时直接写 `VERIFIED` audit，除非同一事务内确实执行了结构性校验并回写 `verification_status='VERIFIED'`。真实交易所权限探活不属于 Batch 5-D-B。

## 10. 建议的测试矩阵

Service tests：

- 从 ACTIVE credential rotate 成功，新 credential ACTIVE/PENDING，旧 credential ROTATED/inactive。
- rotate 写入 `rotated_at / rotated_by`。
- rotate reason 必填并拒绝敏感字段。
- actor 从认证主体传入；空 actor 落 `system` 的策略如保留必须有测试。
- 从 REVOKED / DISABLED / EXPIRED / ROTATED 派生 rotate 均返回状态冲突。
- rotate 后旧 credential 不可通过 disable / expire 改写。
- 新 credential 创建失败时旧 active 仍存在。
- 旧 credential 标记 ROTATED 失败时新 credential 回滚。
- audit log 写入失败时整笔 rotate 回滚。
- 并发 rotate 只允许一个成功，另一个返回冲突。

Repository tests：

- `findActiveSummary / findActiveMaterial` 只读取 `is_active=true AND credential_status='ACTIVE'`。
- `markRotated` 必须带当前 active 条件。
- `appendCredentialAuditLog` 使用 `CAST(? AS jsonb)`，metadata 不含敏感字段。
- 多 active 异常状态不能被 `LIMIT 1` 静默吞掉；至少应在后续代码层检测并冲突。

Controller tests：

- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/rotate` 成功返回新 credential 摘要。
- 响应不包含 `encryptedPayload`、`secretKey`、`token`、`privateKeyPem`、`passphrase`、`decryptedPayload`。
- reason 缺失、过长、包含敏感词返回 400。
- owner 越权返回 404/403 的既有策略，不泄露 credential 是否存在。
- 不新增 enable endpoint。

## 11. 明确禁止项

- 不做真实交易所权限探活。
- 不输出 secret、token、API key、exchange secret、private key、passphrase、cookie、助记词、encrypted payload 或 decrypted payload。
- 不接 LIVE。
- 不接 DH / AI。
- 不新增真实下单、真实撤单或真实交易所私有链路。
- 不把 Batch 5-D-A 写成 rotate 已实现。
- 不把 GateK-PLAN 写成 GateK 实现已启动。

## 12. 对 Paper / LIVE 与 exchange account summary 的影响

- rotate endpoint 本身不应改变 `exchange_accounts.trade_env`，也不应开启 LIVE trading。
- Paper/LIVE 隔离仍以 account 的 `trade_env` 和调用路径控制；credential rotate 只管理 credential 版本，不授权交易。
- exchange account summary 可保持不变；credential summary 只暴露 masked key 和 lifecycle 元数据。
- 若未来需要在 account summary 展示 credential 状态，应只展示 `credentialStatus / verificationStatus / lastVerifiedAt / rotatedAt` 等非敏感字段。

## 13. Batch 5-D-B 是否可开工

结论：可以开工，但必须满足以下入口条件：

- 只做 rotate endpoint、Service、Repository、DTO 和测试。
- 不新增 migration，除非业务明确要求从 account 维度强制唯一 ACTIVE credential；如需要该约束，应单独列入 schema 子任务。
- 不混做 enable endpoint。
- 不做真实交易所权限探活。
- 不接 AI、DH、LIVE 或真实交易路径。
- 不输出或记录任何真实 credential material。
