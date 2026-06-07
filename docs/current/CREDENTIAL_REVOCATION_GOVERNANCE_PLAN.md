# Credential Revocation Governance Plan

任务：NQ-DB-SCHEMA-GOVERNANCE-BATCH-5A-CREDENTIAL-REVOCATION-REVIEW
日期：2026-06-07
状态：Batch 5-A review completed；Batch 5-B schema completed；Batch 5-C code/API/test completed；Batch 5-D-A rotate review completed；Batch 5-D-B explicit rotate command implemented。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

## 1. 目标

本计划把 credential revocation 从泛化 DB schema governance 中拆为独立治理链路，避免把凭证撤销、账户禁用、轮换、过期、权限校验和审计日志混成一个状态字段。

本计划记录 credential revocation governance 的分批落地事实。当前已完成 Batch 5-A 只读审计、Batch 5-B schema-only 治理、Batch 5-C 最小 code/API/test 接入、Batch 5-D-A rotate 只读审计和 Batch 5-D-B 显式 rotate command；未完成 enable endpoint、真实交易所权限探活、前端接入、AI/DH/Agent 调用或 LIVE 交易能力。

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
- `exchange_account_credentials.permission_scope`：允许 `READ_ONLY / TRADE` 或 `NULL`；`NULL` 表示当前 schema-only 阶段尚未由代码确认权限。
- `exchange_account_credentials.withdraw_enabled`：默认 `FALSE`，只记录治理元数据，不代表系统实现提现能力或开启 LIVE trading。
- `exchange_account_credentials.ip_allowlist_required`：默认 `TRUE`，只记录治理要求，不保存 IP 凭证、token、cookie 或网络访问密钥。
- `exchange_account_credentials.external_secret_ref`、`exchange_account_credentials.key_alias`：仅保存外部密钥引用或别名，不得保存 secret 明文。
- 新增 `credential_audit_logs` append-only 表，事件类型允许 `CREATED / VERIFIED / FAILED_VERIFICATION / DISABLED / REVOKED / ROTATED / EXPIRED / USED / ACCESS_DENIED`。

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

## 7. 后续安全审计重点

- P0：真实密钥泄露、LIVE credential 被 Paper 路径误用、DH / Agent / AI 访问 credential。
- P1：撤销语义缺失、不可恢复撤销和临时禁用混淆、API 返回敏感字段、Paper / LIVE 隔离不清。
- P2：审计字段不足、轮换链上下文不足、权限范围记录不足、IP allowlist / withdraw disabled 证明缺失。
- P3：注释、命名、测试 fixture 和文档措辞不清。

## 8. 回滚与兼容原则

- Batch 5-B 新增字段通过后续 migration 回滚或废弃，不修改历史 migration。
- 不删除已有 credential 版本记录。
- 不删除 audit log。
- `credential_status` 已新增，初始回填兼容现有 `is_active` 和 `verification_status`：
  - `is_active=true` 且 `verification_status<>REVOKED`：`ACTIVE`。
  - `verification_status=REVOKED` 或 `is_active=false`：按现有轮换旧版本语义回填为 `ROTATED`。
  - 其他历史异常组合保守落到 `DISABLED`，避免误判为可用凭证。

## 9. 与 GateK-PLAN 的关系

Credential revocation governance 是安全和数据治理工作，不代表 GateK 实现已启动。即使 GateK-PLAN 后续规划 AI 信号接入，AI / Agent / DH 也不得访问 credential、master key、decrypted payload 或 revoke/audit API，除非未来单独安全设计、审批和验证。
