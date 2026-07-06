# Credential Revocation Governance Review

任务：NQ-DB-SCHEMA-GOVERNANCE-BATCH-5A-CREDENTIAL-REVOCATION-REVIEW
日期：2026-06-07
类型：CODE_ANALYSIS + DOCUMENTATION
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

## 1. 结论

本轮只读审计确认：NQ 当前已经存在正式 `exchange_accounts` 与 `exchange_account_credentials` 模型，凭证使用数据库密文字段保存，API response 只返回摘要和 masked access key，不直接返回 secret / token / private key。凭证轮换已有最小版本链语义：新增版本、旧 active 版本标记 `REVOKED`、写入 `revoked_at`，并通过 `rotated_from_credential_id` 建立血缘。

当前缺口是撤销治理仍不完整：`REVOKED` 目前主要由轮换触发，没有独立 revoke command；缺少 `revoked_by`、`revoke_reason`、`rotated_at`、`last_used_at`、`failed_auth_count`、权限范围、withdraw 禁用证明、IP allowlist 记录和独立 credential audit log。建议后续拆为 Batch 5-B schema-only 与 Batch 5-C code/API/test，不在本轮实现。

## 2. 当前 credential 相关表清单

| 表 | 来源 migration / 文档 | 当前角色 | 是否保存真实密钥 | 当前生命周期语义 |
| --- | --- | --- | --- | --- |
| `exchange_accounts` | `backend/nq-infra/src/main/resources/db/migration/V12__rc1_account_and_credentials.sql` | 正式交易账户配置表，保存 owner、exchange、trade env、alias、external ref、default 与 status。 | 否。只保存账户元数据，不保存 secret。 | `status IN ('ACTIVE','DISABLED')`，`trade_env IN ('SIM','LIVE')`。 |
| `exchange_account_credentials` | `backend/nq-infra/src/main/resources/db/migration/V12__rc1_account_and_credentials.sql` | 凭证版本表，保存账户下不同 credential type 的 active 版本和轮换血缘。 | 是，但仅以 `encrypted_payload BYTEA` 密文形式保存；API 摘要只暴露 `masked_access_key`。 | `verification_status IN ('PENDING','VERIFIED','FAILED','REVOKED')`，`is_active` 控制当前版本，`revoked_at` 记录旧版本失效时间。 |
| `accounts` | legacy `V1` / `V12` 迁移映射 | legacy 账户表，经 `legacy_account_id` 迁移到 `exchange_accounts`。 | 否，按本轮检索未作为正式 credential 主数据源。 | Batch 3-A 已将 legacy 状态约束到 `ACTIVE/DISABLED`。 |

## 3. 当前字段和状态语义

### `exchange_accounts`

- `exchange_account_id`：正式账户上下文主键。
- `owner_user_id`：账户归属用户。
- `exchange_code`：交易所编码，例如 `OKX / BINANCE`。
- `trade_env`：交易环境，当前约束为 `SIM / LIVE`；历史 `DOME / REAL` 只允许在导入映射层存在。
- `account_alias` / `external_account_ref`：账户展示和外部引用元数据。
- `is_default`：同一 owner / exchange / env 下最多一个默认账户。
- `status`：`ACTIVE / DISABLED`。当前没有 `REVOKED / EXPIRED / ROTATED`，这些不应放到账户状态里，应归属于 credential 生命周期。

### `exchange_account_credentials`

- `credential_type`：`OKX_API_V5 / BINANCE_HMAC / BINANCE_ED25519`。
- `encrypted_payload`：密文字节。当前采用 `pgp_sym_encrypt` / `pgp_sym_decrypt`，应用层通过 master key 解密后仅用于服务端结构性校验。
- `key_version`：加密主密钥版本号。
- `cipher_suite`：当前默认 `PGP_SYM_AES256`。
- `masked_access_key`：前端展示和审计定位用的脱敏主标识。
- `verification_status`：`PENDING / VERIFIED / FAILED / REVOKED`。当前没有 `EXPIRED / ROTATED` 状态。
- `is_active`：当前生效版本标记。唯一索引保证同账户同类型最多一个 active。
- `revoked_at`：旧 active 版本在轮换时被写入，当前缺少操作者与原因。
- `rotated_from_credential_id`：新版本指向旧版本，用于轮换链。
- `last_verified_at` / `last_verification_error`：结构性校验结果，不等价于真实交易所在线探活或权限证明。
- `created_at` / `updated_at`：版本记录创建和更新时间。

## 4. 当前 credential 相关代码路径清单

| 层级 | 路径 | 本轮只读确认 |
| --- | --- | --- |
| Domain | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/ExchangeAccountSummary.java` | 账户摘要返回 exchange / env / status / default，不包含 secret。 |
| Domain | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/ExchangeAccountCredentialSummary.java` | API 可见凭证摘要，不包含解密 payload。 |
| Domain | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/ExchangeAccountCredentialMaterial.java` | 服务端校验材料，包含 `decryptedPayloadJson`，必须只停留在服务端内存。 |
| Port | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/domain/port/ExchangeAccountCredentialRepository.java` | 定义 active 摘要、active 材料、停用旧版本、新增版本和校验状态回写。 |
| Service | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandService.java` | 新增/轮换凭证；轮换时旧 active 版本写成 `REVOKED`。 |
| Service | `backend/nq-core/src/main/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialVerificationService.java` | 解密 active material 后结构性校验，回写 `VERIFIED / FAILED`。 |
| Repository | `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepository.java` | 使用 `pgp_sym_encrypt` / `pgp_sym_decrypt`；active 查询按 owner 校验账户归属。 |
| API | `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialController.java` | `GET active`、`POST upsert`、`POST verify`；当前没有独立 revoke endpoint。 |
| DTO | `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/ExchangeAccountCredentialUpsertRequestBody.java` | 请求体接收敏感字段，必须禁止日志输出请求体。 |
| DTO | `backend/nq-api/src/main/java/com/guidinglight/nexusquant/account/api/dto/ExchangeAccountCredentialSummaryResponse.java` | 响应只包含 masked key、status、rotation source 和校验摘要。 |
| Config | `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/config/account/AccountCredentialRuntimeProperties.java` | 运行时承接 master key、key version、verification mode；本轮未读取配置值。 |
| Verifier | `backend/nq-infra/src/main/java/com/guidinglight/nexusquant/account/infra/verification/StructuralExchangeAccountCredentialVerifier.java` | 只做结构性签名能力校验，不真实调用交易所私有接口。 |
| Tests | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialCommandServiceTest.java` | 覆盖创建 active、mask、轮换旧版本 REVOKED。 |
| Tests | `backend/nq-core/src/test/java/com/guidinglight/nexusquant/account/application/ExchangeAccountCredentialVerificationServiceTest.java` | 覆盖结构性校验成功/失败和 active 缺失。 |
| Tests | `backend/nq-infra/src/test/java/com/guidinglight/nexusquant/account/infra/jdbc/JdbcExchangeAccountCredentialRepositoryTest.java` | 覆盖加密/解密 SQL、REVOKED 更新和校验回写 SQL。 |
| Tests | `backend/nq-api/src/test/java/com/guidinglight/nexusquant/account/api/web/ExchangeAccountCredentialControllerWebMvcTest.java` | 覆盖 active/upsert/verify API 摘要响应。 |

## 5. 当前安全边界

- API response 不返回 `apiKey` 原文、`secretKey`、`passphrase`、`privateKeyPem` 或 `decryptedPayloadJson`。
- `ExchangeAccountCredentialSummary` 与 `ExchangeAccountCredentialSummaryResponse` 只暴露 `maskedAccessKey`。
- 解密路径集中在 `JdbcExchangeAccountCredentialRepository.findActiveMaterial(...)` 和 `StructuralExchangeAccountCredentialVerifier`，只用于服务端校验。
- owner 访问通过 `exchange_accounts.owner_user_id` 校验，credential active material 查询不会只按 credential id 裸查。
- DH integration 当前未启动，文档边界明确 DH 不允许访问凭证、下单、撤单、启动 Paper Run 或修改 NQ 交易状态。
- GateJ completed 仍不是 AI 阶段；本轮未发现 AI / Agent 访问 credential 的业务路径。

## 6. P0 / P1 / P2 / P3 风险分级

### P0

无确认型 P0。

- 未发现 API response 直接返回 secret / token / private key。
- 未发现 DH / Agent / AI 当前可访问 credential 的业务路径。
- 未发现本轮触达 LIVE enable、真实下单、真实撤单或真实交易所私有链路。

### P1

- 撤销语义不完整：当前 `REVOKED` 主要由轮换旧 active 版本触发，缺少独立不可恢复撤销命令和对应 API / Service / Repository 入口。
- Paper / LIVE 隔离字段存在但 credential 权限未落库：`exchange_accounts.trade_env` 有 `SIM / LIVE`，但 credential 表未记录 credential 自身允许环境、交易权限、read-only/trade 范围或 withdraw disabled 证明。
- API 写侧请求体接收敏感材料，当前需要继续保证 Controller、异常处理、审计日志和测试报告不记录请求体明文；后续 Batch 5-C 应补日志脱敏回归。

### P2

- 审计字段不足：缺少 `revoked_by`、`revoke_reason`、`rotated_at`、`rotated_by`、`last_used_at`、`failed_auth_count`、`last_auth_failed_at`。
- 状态约束不足：`verification_status` 没有区分 `EXPIRED`、`ROTATED`；当前用 `REVOKED` 表示旧 active 版本因轮换失效，容易混淆不可恢复撤销与版本轮换。
- 轮换记录不足：已有 `rotated_from_credential_id`，但缺少清晰 `rotated_at / rotated_by`，新旧版本之间缺少明确操作上下文。
- 权限元数据不足：缺少 read-only / trade / withdraw disabled、交易所 IP allowlist、账户权限校验结果、外部 secret id / key alias / secret ref 设计。
- 缺少独立 credential audit log：当前可从凭证表字段部分追溯，但无法 append-only 记录 upsert、verify、revoke、rotate、failed auth、权限检查等事件。

### P3

- 文档命名仍有历史 RC1 口径，V26 已清理主注释，但 credential revocation 后续计划需要单独形成事实源。
- 测试 fixture 使用假 secret 字面量，当前是测试内假值，但后续应统一使用明显不可用的占位值并避免出现在文档、日志或测试报告中。
- `last_verification_error` 是错误摘要字段，需保持脱敏约束，避免未来真实外部校验时把 exchange 返回的敏感错误上下文写入库或 API。

## 7. 后续 Batch 5-B 最小 schema 方案

Batch 5-B 应只做 schema migration，不改 Java / API / 前端 / Python / 部署。建议最小字段：

### `exchange_account_credentials`

- 新增 `credential_status VARCHAR(16)`，建议允许 `ACTIVE / DISABLED / REVOKED / EXPIRED / ROTATED`。如果不新增独立字段，则必须重新定义 `verification_status` 与 `is_active` 的组合语义，但该方案容易混淆校验状态和生命周期状态。
- 新增 `revoked_by VARCHAR(128)`。
- 新增 `revoke_reason TEXT`，COMMENT 明确不得保存密钥、token、API secret、私钥、助记词、cookie 或交易所凭证。
- 新增 `rotated_at TIMESTAMPTZ`。
- 新增 `rotated_by VARCHAR(128)`。
- 新增 `last_used_at TIMESTAMPTZ`。
- 新增 `failed_auth_count INTEGER NOT NULL DEFAULT 0`，CHECK `failed_auth_count >= 0`。
- 新增 `last_auth_failed_at TIMESTAMPTZ`。
- 可选新增 `permission_scope VARCHAR(16)`，允许 `READ_ONLY / TRADE`；禁止表达 withdraw enabled。
- 可选新增 `withdraw_disabled BOOLEAN NOT NULL DEFAULT TRUE`。
- 可选新增 `ip_allowlist_note TEXT`，只存状态摘要或外部校验结论，不存敏感网络凭证。
- 可选新增 `external_secret_ref VARCHAR(256)` 或 `key_alias VARCHAR(128)`，用于未来外部 KMS / Secret Manager；不得与 `encrypted_payload` 明文并存失控。

### 新增 `credential_audit_logs`

建议新增 append-only 审计表，而不是把全部审计压到凭证表：

- `audit_id BIGSERIAL PRIMARY KEY`
- `credential_id BIGINT`
- `exchange_account_id BIGINT NOT NULL`
- `event_type VARCHAR(32) NOT NULL`，允许 `UPSERT / ROTATE / REVOKE / DISABLE / VERIFY / AUTH_FAILED / PERMISSION_CHECK`
- `actor_user_id BIGINT`
- `actor_name VARCHAR(128)`
- `event_reason TEXT`
- `event_time TIMESTAMPTZ NOT NULL DEFAULT NOW()`
- `request_id VARCHAR(128)`
- `metadata_json JSONB`

`metadata_json` 必须 COMMENT：只允许保存脱敏状态、结果码和审计上下文，禁止保存 secret、token、API key、private key、passphrase、cookie、助记词或 exchange credential。

## 8. 后续 Batch 5-C 代码 / API / 测试方案

Batch 5-C 必须在 Batch 5-B schema 落库后执行，且不新增真实下单、真实撤单、AI、DH 或 LIVE enable。

- Repository：新增 revoke / disable / record audit / mark auth failed / mark used 等方法；active material 查询必须排除 `REVOKED / EXPIRED / ROTATED`。
- Service：新增不可恢复 revoke command；区分临时禁用、轮换旧版本、过期、撤销。
- API：新增受控 `POST /api/exchange-accounts/{accountId}/credentials/revoke` 或按 credential id 的 revoke endpoint；请求体只允许 `revokeReason`，操作者由服务端认证主体解析。
- DTO：response 继续只返回 masked key 和状态元数据，不返回 secret、token、private key、passphrase 或 decrypted payload。
- 测试：覆盖 revoke 幂等、revoke 后不可 verify/use、DISABLED 可恢复但 REVOKED 不可恢复、ROTATED 只由 upsert 产生、EXPIRED 不可 active、owner 越权失败、API response 不含敏感字段。
- 日志：补回归保证 request body、decrypted payload、last verification error、audit metadata 不输出敏感材料。
- 安全边界：明确 DH / Agent / AI 不得调用 credential API，不得读取 active material，不得访问 master key。

## 9. 不得 hard delete 的表与字段边界

- `exchange_account_credentials` 不得 hard delete。原因：凭证版本和撤销链是安全审计证据，必须保留 REVOKED / ROTATED / EXPIRED 等状态和 audit log。
- `credential_audit_logs` 如后续新增，必须 append-only，不得 hard delete。保留策略只能按合规留存设计单独审批。
- `exchange_accounts` 不建议 hard delete。应使用 `DISABLED`，避免破坏订单、Paper run、审计和 credential 归属追溯。
- `encrypted_payload` 不得存储明文；后续如引入 `secret_ref / key_alias / external_secret_ref`，也不得把明文 secret 写入这些字段。
- 任何文档、日志、API response、测试报告、audit metadata、archive/revoke reason 都不得输出 secret、token、API key、exchange secret、private key、passphrase、cookie 或助记词。

## 10. 本轮未执行项

- 未新增 migration。
- 未修改历史 migration。
- 未修改 Java / API / Repository / DTO / 前端 / Python / 部署脚本。
- 未运行 Maven / npm / pytest；本轮只做文档审计，验证以 `git diff --check` 和范围检查为准。
- 未读取 `.env`、secrets、credentials、logs、dump、backup、target、node_modules、dist、build、`.git` 内容。
- 未把 GateK-PLAN 写成 GateK implementation started；未写 AI started；未写 DH integrated；未写 LIVE enabled。
