# NQ-GATEW-FREEZE-BLOCKER-2-OKX-REAL-READONLY-PERMISSION-PROBE-CONFORMANCE-REVIEW — Attempt 01

## Review target and evidence

- 审查本任务全部 production/test/docs diff、官方 OKX contract、V31 schema、Spring composition、Maven 结果与 forbidden-scope。
- 独立检查唯一 method/path、NoReal default、credential boundary、IP/permission fail-closed、两段事务、CAS writeback、敏感信息禁入、无 API/scheduler/migration/交易能力。

## Findings

- P0：0。
- P1-1（已关闭）：原 owner/account/type selection 在 credential rotation 竞态中可能探测错误 active credential；已改为真实 port 必须使用 exact credential ID，JDBC count/decrypt SQL 同时绑定该 ID，并补测试。
- P1-2（已关闭）：未取得 permission observation 的认证/网络失败会清空既有 `permission_scope`/`withdraw_enabled`；已改为保留最后已知风险事实，并补回归测试。
- P2：既有 root `README.md` 的短摘要仍写 GateW-1 尚未初始化，与 `STATUS.md` 当前 authority 有历史漂移；该文件起始即如此且不在本任务 allowlist，本轮不改，不影响机器 authority checker。
- P3：既有 SLF4J/Mockito warning；不影响测试结果。V31 storage 使用 `SUCCEEDED + READ_ONLY + PASSED` 表达逻辑 `READ_ONLY_VERIFIED`，这是既有 schema canonical，不新增 migration。

## Conformance result

- 唯一新远端调用：`GET /api/v5/account/config`；真实 port 无 balance/order/cancel/transfer/withdraw/private WebSocket。
- method/path 由 enum + typed request 固定，redirect/host drift 被拒绝；timeout/concurrency/response cap 有界，无无限 retry。
- unknown/missing permission 或 IP facts 全部失败；Trade/Withdraw 任一开启都不会返回成功。
- credential material 只在 JDBC executor callback 生命周期内使用；request record 不含 payload，exception/log/audit/metadata 不含 raw material。
- HTTP 位于两段短事务之间；finalize 使用单事务与 `IN_PROGRESS` CAS，affected rows=0 直接抛错，不返回 success。
- 默认/CI/no-outbound composition 保持 NoReal；缺 flag、executor、expected IP 或任一 safety fact 冲突均不会启用真实 port；无 startup/background probe。
- forbidden scope 无 `nq-api`、scheduler、migration、frontend、research、deploy、CI、POM、Gate archive 或 authority 变更。

## Validation

- IntelliJ errors-only：23 files，0 errors。
- required targeted Maven：23/23 modules `SUCCESS / BUILD SUCCESS`。
- full Maven：23/23 modules `SUCCESS / BUILD SUCCESS`。
- `git diff --check`：PASS。
- `check-current-authority.ps1`：`PASS / CURRENT_AUTHORITY_CONSISTENT`。
- `check-gate-archive.ps1 -Gate gate-v -ExpectedTag nq-gatev-freeze`：`PASS / ARCHIVE_MANIFEST_COMPLETE`；GateW archive 未启动且不在本任务范围。
- `check-doc-links.ps1 -Roots docs/current`：111 links，0 errors，1 个既有 GateJ historical ledger warning，非阻断。
- credential/forbidden scan：29 changed paths，forbidden paths=0，direct credential env additions=0，raw credential/provider leak additions=0；真实 port forbidden private path occurrences=0。

## Boundary confirmation

本轮未读取/配置真实 Key，未调用 OKX，未操作服务器，未启动 soak，未启用 LIVE 或交易能力。GateW authority 保持 `IN_PROGRESS|NOT_FROZEN`，GateW-FREEZE 保持 `NOT_STARTED`。

## Decision

`PASS / OKX_REAL_READONLY_PERMISSION_PROBE_ACCEPTED / READY_TO_COMMIT`（通过 / 真实只读 permission probe 已接受 / 可进入提交前复核）。
