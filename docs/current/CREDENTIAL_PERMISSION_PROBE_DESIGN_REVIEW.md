# Credential Permission Probe Design Review

任务：NQ-CREDENTIAL-PERMISSION-PROBE-DESIGN-REVIEW
日期：2026-06-08
状态：design review completed；permission probe not implemented；no real exchange call performed。
当前阶段：GateJ completed；Next: GateK-PLAN；AI not started；DH integration not started / not connected to NQ；LIVE trading disabled。

## 1. Scope

本轮只读设计审计真实交易所 credential permission probe，覆盖 READ_ONLY / TRADE / FUNDING 权限建模、withdraw 禁用、IP allowlist、失败重试、`failed_auth_count`、告警、前端风险提示和 Paper/LIVE 隔离。

本轮未新增 migration，未修改 Java、Repository、Service、Controller、DTO、API、前端、Python 或部署脚本；未调用 OKX、Binance、Bybit、Gate 或任何真实交易所；未读取或输出真实密钥；未接 AI、DH、LIVE；未实现 permission probe。

## 2. Current State

- `verification_status='VERIFIED'` 当前只代表本地结构性校验成功：`StructuralExchangeAccountCredentialVerifier` 解析 decrypted payload 并复用 signer 构造签名，不访问真实交易所，不证明账号权限、IP allowlist 或交易权限可用。
- `permission_scope` 当前允许 `READ_ONLY / TRADE / NULL`；`NULL` 表示权限尚未由代码确认，不得当作 `TRADE`。
- 当前 schema 没有 `permission_probe_status`、`last_permission_probe_at`、`last_permission_probe_error` 或可区分真实权限探活历史的专用字段。
- `last_verified_at` 只适合结构性校验时间；`last_used_at` 只适合服务端业务路径使用时间；真实权限探活需要独立 `last_permission_probe_at`。
- `withdraw_enabled` 默认 `FALSE`，但当前没有强制 CHECK 保证 probe 后仍保持 false。
- `ip_allowlist_required` 默认 `TRUE`，当前只表示治理要求，没有记录交易所侧是否验证通过。
- `failed_auth_count` 当前不会被 enable / verify / lifecycle 命令清零；真实权限探活需要单独定义何时增加、何时不增加、何时仍不清零。
- 现有 `paper_run_alerts` 绑定 `paper_run_id`，适合 Paper run 告警，不适合作为 credential 安全告警的通用表。

## 3. Required Review Checklist

| # | 审计项 | 结论 |
| --- | --- | --- |
| 1 | 当前 VERIFIED 是否仅代表本地结构性校验 | 是。不得把 `VERIFIED` 写成真实交易所权限可用。 |
| 2 | 是否需要新增 `verification_status` 或 `permission_probe_status` | 需要新增 `permission_probe_status`；不建议扩展 `verification_status` 承载真实权限语义。 |
| 3 | READ_ONLY / TRADE / FUNDING 权限如何建模 | 建议 `permission_scope` 表示最高确认权限，CHECK 增加 `FUNDING`；详细能力写入脱敏 probe result metadata。 |
| 4 | `withdraw_enabled` 必须如何保持 false | 默认与探活后均必须保持 `FALSE`；建议新增 CHECK 强制 `withdraw_enabled = FALSE`，直到未来单独审批提现能力。 |
| 5 | `ip_allowlist_required` 如何校验和记录 | 建议新增 `ip_allowlist_probe_status` 或在 probe result 中记录 `REQUIRED_VERIFIED / REQUIRED_NOT_VERIFIED / NOT_REQUIRED / UNKNOWN / UNSUPPORTED`，不得记录 IP secret 或网络凭证。 |
| 6 | `failed_auth_count` 何时增加、何时不清零 | 仅真实交易所返回认证/签名/IP allowlist 拒绝时增加；本地结构失败、网络超时、5xx、限流、Paper gate 拦截、用户取消不增加；probe 成功不清零。 |
| 7 | `last_used_at / last_verified_at / last_permission_probe_at` 是否需要区分 | 必须区分：业务使用、结构性校验、真实权限探活是三类事件。 |
| 8 | `permission_scope=NULL` 是否继续表示未确认权限 | 是。NULL 继续表示未确认，不得解释为 READ_ONLY、TRADE 或 FUNDING。 |
| 9 | probe 是否必须只在 Paper 安全上下文运行 | 是。初版必须绑定 Paper 安全上下文，且不得产生订单、撤单、转账或提现。 |
| 10 | 是否允许对 LIVE credential 做 probe | 当前默认禁止。未来如允许，必须单独 Gate、审批、feature flag、审计和测试。 |
| 11 | 是否需要告警表或复用现有 alert | 不建议复用 `paper_run_alerts` 做通用 credential 安全告警；初版可先写 `credential_audit_logs`，后续单独设计 credential security alert。 |
| 12 | API response 如何避免泄露 material | response 只返回 credentialId、accountId、credentialType、probe status、确认权限、脱敏错误摘要和时间；不得返回 encrypted/decrypted payload、apiKey、secret、token、private key、passphrase、签名或 raw response。 |
| 13 | audit metadata 如何避免泄露 secret | metadata 只保存状态、scope、sanitized exchange code、reasonPresent、retry count、policy decision、request id；不得保存 request body、headers、签名、payload、raw exchange response 或 credential material。 |
| 14 | 是否需要新增 migration | 需要。建议先做 schema-only migration，不在同批实现真实调用。 |
| 15 | 是否需要先做 schema-only，再做 code | 必须先 schema-only，再 code/API/test；避免代码先把权限写入无约束字段。 |
| 16 | 是否需要 adapter 抽象，还是 credential service 内独立 probe port | 建议独立 `ExchangeCredentialPermissionProbePort`；credential service 负责编排与审计，adapter 只实现脱敏探活能力，不由 credential service 直接写 HTTP。 |
| 17 | 是否会影响 GateK-PLAN 边界 | 不应影响。permission probe 是 credential governance 安全设计，不代表 GateK implementation started、AI started、DH integrated 或 LIVE enabled。 |

## 4. Risk Register

### P0

- 如果直接实现真实交易所调用，并把 `verification_status='VERIFIED'` 当作真实权限可用，会造成权限语义混淆，可能让后续交易链路误判 credential 可交易。
- 如果 probe 默认允许 LIVE credential 或绕过 Paper 安全上下文，即使只调用只读接口，也会突破 GateJ completed / GateK-PLAN 边界。
- 如果日志、API response、audit metadata、异常或告警保存 credential material、签名、raw payload、raw response、header、API key、secret、private key 或 passphrase，会形成凭证泄露风险。
- 如果 probe 触发下单、撤单、转账、提现或任何资金变动接口，属于禁止范围。

### P1

- 当前 schema 不足以区分结构性校验与真实权限探活，需要新增 `permission_probe_status` 和 `last_permission_probe_at`。
- 当前 `permission_scope` 缺少 `FUNDING`，且单字段无法表达复杂交易所权限细节；需要先定义最高权限与细粒度 metadata 的关系。
- 当前 `withdraw_enabled` 只有默认 false，缺少“探活也不得置 true”的强约束。
- 当前 `ip_allowlist_required` 只记录治理要求，缺少真实探活结果状态。
- 现有 `paper_run_alerts` 绑定 Paper run，不适合直接承载跨账户 credential 安全告警。

### P2

- 重试策略需要区分认证失败、IP 拒绝、超时、限流和 5xx；否则会错误增加 `failed_auth_count` 或制造告警噪音。
- 前端必须在未来实现时明确显示“本地结构性校验”和“真实权限探活”两种状态，否则用户可能误以为 `VERIFIED` 代表交易权限可用。
- probe port 必须有交易所级 timeout、rate limit、幂等 request id 和脱敏错误映射，否则审计难以复盘。
- `permission_scope` 写入策略需要覆盖从 `NULL` 到 `READ_ONLY / TRADE / FUNDING` 的迁移与失败回退。

### P3

- 文档索引需要持续标注 permission probe 仍未实现，避免把设计审计误读为功能已上线。
- 命名建议统一使用 `permission_probe_status` 与 `last_permission_probe_at`，避免与 `verification_status` 混写。

## 5. Recommended Schema Changes

建议下一批进入 schema-only migration，不修改历史 migration，不做数据 backfill，不实现 Java：

1. `exchange_account_credentials.permission_probe_status VARCHAR(32) NOT NULL DEFAULT 'NOT_PROBED'`
   - CHECK：`NOT_PROBED / PENDING / SUCCEEDED / FAILED / PARTIAL / SKIPPED`。
   - 语义：真实交易所权限探活状态；独立于 `verification_status`。

2. `exchange_account_credentials.last_permission_probe_at TIMESTAMPTZ`
   - 语义：最近一次真实权限探活完成时间；不代表业务使用时间。

3. `exchange_account_credentials.last_permission_probe_error TEXT`
   - 语义：脱敏错误摘要；禁止保存 secret、token、API key、private key、passphrase、签名、raw payload 或 raw response。

4. 扩展 `exchange_account_credentials.permission_scope` CHECK
   - 建议允许 `READ_ONLY / TRADE / FUNDING` 或 `NULL`。
   - `NULL` 继续表示未确认权限。
   - `FUNDING` 不得隐含提现能力；`withdraw_enabled` 仍必须 false。

5. 新增 `exchange_account_credentials.ip_allowlist_probe_status VARCHAR(32)`
   - 建议允许 `UNKNOWN / REQUIRED_VERIFIED / REQUIRED_NOT_VERIFIED / NOT_REQUIRED / UNSUPPORTED`。
   - 用于区分治理要求和真实探活结论。

6. 新增或强化 `withdraw_enabled` CHECK
   - 建议在当前阶段增加 `CHECK (withdraw_enabled = FALSE)`，确保任何 probe 或后续代码都不能开启提现。

7. 扩展 `credential_audit_logs.event_type`
   - 建议增加 `PERMISSION_PROBE_STARTED / PERMISSION_PROBE_SUCCEEDED / PERMISSION_PROBE_FAILED / PERMISSION_PROBE_SKIPPED`。
   - `metadata` 注释继续明确敏感信息禁入。

8. 告警表
   - 初版不建议复用 `paper_run_alerts` 承载 credential 安全告警。
   - 如需要可观测告警，建议后续单独设计 `credential_security_alerts` 或通用 security alerts；schema-only 批次可先只准备 audit event。

## 6. Recommended API Design

推荐先完成 schema-only，再单独审计 API/code/test。未来 API 设计建议：

- `POST /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe`
  - 默认仅允许 Paper 安全上下文。
  - 默认拒绝 LIVE credential。
  - 请求体只允许 `reason`、`requestedScopes`、`paperRunId` 或安全上下文标识，不接收 credential material。
  - `requestedScopes` 初版默认 `READ_ONLY`；`TRADE / FUNDING` 需要显式确认和额外风险提示。
  - response 只返回 probe summary：credentialId、exchangeAccountId、credentialType、permissionProbeStatus、permissionScope、ipAllowlistProbeStatus、withdrawEnabled=false、failedAuthCount、lastPermissionProbeAt、sanitizedError。
  - 不返回 raw exchange response、headers、signature payload、request body、encrypted payload、decrypted payload、API key、secret、token、private key、passphrase。

- `GET /api/exchange-accounts/{accountId}/credentials/{credentialId}/permission-probe/latest`
  - 只读最新 probe summary。
  - 不读取 credential material。

API 文案必须明确：permission probe 仅验证有限只读/交易权限端点可访问性，不代表 LIVE 下单已启用，不代表提现可用，不代表 AI/DH 可访问 credential。

## 7. Recommended Service / Port Design

推荐新增独立 port，不把真实交易所 HTTP 调用写入 credential Service：

- `ExchangeCredentialPermissionProbePort`
  - 输入：脱敏上下文、credential material、requested scopes、exchange/credentialType、Paper safety context。
  - 输出：脱敏 `PermissionProbeResult`，包含 status、confirmed scope、ip allowlist status、withdraw forbidden check、sanitized error、retry count、exchange error category。
  - 禁止输出 raw credential material、raw request、raw response、headers 或 signature。

- `CredentialPermissionProbeService`
  - 负责 owner/account/credential 校验、Paper safety gate、LIVE 禁止、状态写回、`failed_auth_count` 策略、audit log、告警调度。
  - 只允许读取 `credential_status='ACTIVE' AND is_active=true` 的 credential。
  - 结构性校验失败不进入真实 probe。
  - 交易所 401/403/签名错误/IP allowlist 拒绝增加 `failed_auth_count`；超时、限流、5xx、unsupported exchange、Paper gate blocked 不增加。
  - 成功不清零 `failed_auth_count`；如需重置必须未来单独审批并审计。

- Adapter 实现
  - 每个交易所实现最小只读权限端点探活。
  - 禁止调用 order placement、cancel、transfer、withdraw、sub-account transfer 或任何资金变动接口。
  - timeout、retry、rate limit、request id、脱敏错误映射必须在 adapter 或 port 层可测试。

## 8. Recommended Audit Log Behavior

- `PERMISSION_PROBE_STARTED`：可选，只有真正进入外部探活前写入；metadata 记录 requestedScopes、paperContextPresent、credentialType、source。
- `PERMISSION_PROBE_SUCCEEDED`：记录 confirmedPermissionScope、ipAllowlistProbeStatus、withdrawEnabled=false、retryCount、exchangeErrorCategory=null。
- `PERMISSION_PROBE_FAILED`：记录 sanitizedFailureCategory、failedAuthCountIncremented、retryCount、ipAllowlistProbeStatus。
- `PERMISSION_PROBE_SKIPPED`：记录 skippedReason，例如 `LIVE_CREDENTIAL_BLOCKED`、`PAPER_CONTEXT_MISSING`、`STRUCTURAL_VERIFICATION_FAILED`、`UNSUPPORTED_EXCHANGE`。
- 所有 metadata 禁止保存 secret、token、API key、API secret、private key、passphrase、cookie、助记词、签名、request body、raw response、明文 payload 或交易所凭证。

## 9. Recommended Frontend Risk Prompts

未来前端实现前必须先做 UI/UX 风险文案审计：

- 把“本地结构性校验”和“真实权限探活”分成两个可见状态。
- 当 `permission_scope=NULL` 或 `permission_probe_status=NOT_PROBED` 时显示“未确认真实交易所权限”，不得显示可交易。
- 对 `TRADE / FUNDING` 请求显示二次确认；说明不会开启 LIVE，不会提现，不会下单。
- 对 LIVE credential 显示禁用态和原因：当前阶段默认禁止 probe。
- 展示 `failed_auth_count` 时说明它不会因 probe 成功自动清零。

## 10. Recommended Test Matrix

schema-only 批次：

- CHECK 约束覆盖 `permission_probe_status` 所有允许值与非法值。
- `permission_scope` 新增 `FUNDING` 与非法值拒绝。
- `withdraw_enabled = TRUE` 被 CHECK 拒绝。
- `credential_audit_logs.event_type` 新 probe events 允许，非法值拒绝。
- COMMENT 检查敏感信息禁入边界。

code/API 批次：

- `VERIFIED` 仍只表示结构性校验，不写 `permission_probe_status=SUCCEEDED`。
- Paper context missing 返回 409/403 并写 `PERMISSION_PROBE_SKIPPED`，不调用 port。
- LIVE credential probe 默认拒绝，不调用 port。
- READ_ONLY success 写 `permission_scope=READ_ONLY`、`permission_probe_status=SUCCEEDED`。
- TRADE success 只在显式 requestedScopes 且安全 gate 通过时写 `TRADE`。
- FUNDING observed 不开启 `withdraw_enabled`，仍保持 false。
- IP allowlist 拒绝增加 `failed_auth_count` 并写脱敏 failure。
- 超时、限流、5xx 不增加 `failed_auth_count`。
- 成功不清零 `failed_auth_count`。
- API response 与 audit metadata 均不含 credential material。
- Adapter tests 使用 mock HTTP，不调用真实交易所。

## 11. Decision

是否允许进入 probe schema-only 批次：允许。

入场条件：

- 下一批只能新增 schema-only migration 和同步 `DB_SCHEMA.md` / governance docs。
- 不得在 schema-only 批次实现 Java、API、前端、Python、部署或真实交易所调用。
- schema-only 之后再单独开 code/API/test 批次，并再次审计真实交易所调用边界。

明确禁止：

- 禁止直接实现真实交易所调用。
- 禁止调用 OKX / Binance / Bybit / Gate 或任何真实交易所。
- 禁止对 LIVE credential 默认 probe。
- 禁止下单、撤单、转账、提现。
- 禁止把 `verification_status='VERIFIED'` 写成真实权限可用。
- 禁止把 permission probe 写成 GateK implementation started、AI started、DH integrated 或 LIVE enabled。

## 12. Validation

本轮执行：

- `git diff --check`

本轮未执行：

- `mvn -f backend/pom.xml test`

未执行 Maven 原因：本轮只做 `CODE_ANALYSIS + DOCUMENTATION`，只允许修改 `docs/current` 文档和 README 索引；未修改 Java、migration、API、前端、Python 或部署脚本，不把未执行测试写成通过。
