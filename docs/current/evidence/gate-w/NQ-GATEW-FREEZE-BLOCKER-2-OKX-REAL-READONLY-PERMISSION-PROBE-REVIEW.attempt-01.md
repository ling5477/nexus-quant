# NQ-GATEW-FREEZE-BLOCKER-2-OKX-REAL-READONLY-PERMISSION-PROBE-REVIEW — Attempt 01

## Review target

- Task：`NQ-GATEW-FREEZE-BLOCKER-2-OKX-REAL-READONLY-PERMISSION-PROBE-IMPLEMENTATION`。
- 类型：`SECURITY_REVIEW / EXCHANGE_INTEGRATION / CREDENTIAL_BOUNDARY`。
- 起始基线：`dev`，`HEAD == origin/dev == 8a1554eee58d6931a7ff192cbd6ddcdeab2b22ac`；exact-head `NQ CI Baseline` run `29424519273` 为 `completed / success / 10 jobs / bad=0`。
- Authority：GateW `IN_PROGRESS|NOT_FROZEN`；GateW-FREEZE `NOT_STARTED`；LIVE `DISABLED`。

## Evidence checked

- 既有 `CredentialPermissionProbeService`、`ExchangeCredentialPermissionProbePort`、`NoRealExchangeCredentialPermissionProbePort`、V31 permission-probe schema、JDBC metadata repository。
- 既有 `JdbcOkxPrivateCredentialExecutor`、typed OKX private request signer/transport 与 Spring composition。
- OKX 官方 REST API 文档：`GET /api/v5/account/config`；`perm` token 为 `read_only / trade / withdraw`；`ip` 为空表示未绑定 IP；认证/时间/IP/权限错误码包括 `50102 / 50105 / 50110 / 50111 / 50113 / 50120`。

## Review decision

- 唯一允许新增的远端能力固定为 typed `GET /api/v5/account/config`；不允许 raw path、redirect、host fallback、balance/order/cancel/transfer/withdraw/private WebSocket。
- V31 足以保存脱敏 current facts：storage canonical success 为 `permission_probe_status=SUCCEEDED`、`permission_scope=READ_ONLY`、`withdraw_enabled=false`、`ip_allowlist_probe_status=PASSED`、`ip_allowlist_required=true`。该组合在本任务中对应逻辑状态 `READ_ONLY_VERIFIED`，不新增 `READ_ONLY_VERIFIED` DB enum，也不修改 migration。
- 远端 HTTP 必须位于事务外；claim 与 finalize 分别使用短事务，finalize 必须以 `permission_probe_status='IN_PROGRESS'` CAS 且 affected rows 为 1。
- credential 必须从既有 DB 密文经 `JdbcOkxPrivateCredentialExecutor` 按 owner/account/exact credential ID 解密；request/DTO/audit/日志/exception 不得携带 payload 或 raw provider data。
- 真实 port 默认不装配；仅在 explicit permission flag、合法 expected IP、JDBC executor 与全部 safety flags 精确匹配时启用。缺失或冲突一律回落 NoReal。

## Findings

- P0：0。
- P1：实现复核必须重点验证 exact credential ID、失败写回不清除既有高风险权限事实、CAS 冲突不返回成功。
- P2：V31 没有独立 read/trade 三布尔字段；使用既有 `permission_scope + withdraw_enabled + probe/ip status` 表达，满足本任务且不得为此新增 migration。
- P3：0。

## Boundary confirmation

- 不读取/录入真实 Key，不调用 OKX，不操作服务器，不启动 soak。
- 不修改 `nq-api`、scheduler、migration、POM、frontend、research、deploy、CI、Gate archive 或 authority。
- 不启用 LIVE、real client/provider、下单、撤单、转账或提现。

## Decision

`PASS / SECURITY_REVIEW_ACCEPTED / IMPLEMENTATION_AUTHORIZED`（通过 / 安全审查已接受 / 允许在精确范围内实现）。
