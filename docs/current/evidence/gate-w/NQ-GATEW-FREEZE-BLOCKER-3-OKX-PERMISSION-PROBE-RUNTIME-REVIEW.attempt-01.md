# NQ-GATEW-FREEZE-BLOCKER-3-OKX-PERMISSION-PROBE-RUNTIME-REVIEW — Attempt 01

## Review target

- Task：`NQ-GATEW-FREEZE-BLOCKER-3-OKX-PERMISSION-PROBE-RUNTIME-REMEDIATION`。
- 类型：`SECURITY_REMEDIATION / SERVER_SECRET_HYGIENE / RUNTIME_COMPOSITION / SAFE_ERROR_CLASSIFICATION / SERVER_AUTHENTICATION`。
- 起始基线：`dev`，`HEAD == origin/dev == 9c2ec47c3b6bb3920e65197edded1cabb435aa79`；exact-head `NQ CI Baseline` run `29433233718` 为 `completed / success / 10 jobs / bad=0`。
- Authority：GateW `IN_PROGRESS|NOT_FROZEN`；GateW-FREEZE `NOT_STARTED`；next action `NQ-GATEW-FREEZE-CLOSEOUT-IMPLEMENTATION`；LIVE `DISABLED`。

## Current fact verification

- Attempt-05 实际明文输入源为本机 ignored `E:\Project\nexus-quant\.env`，不是服务器 `/root/.env`；服务器 `/root/.env` 为 `ABSENT`。
- 服务器 deployment config 仅发现 `/opt/nexus-quant/gatew-soak/config/management.env`，owner/group=`nqgatew/nqgatew`、mode=`600`，三个 `NQ_OKX_REAL_*` 字段均 `ABSENT`。
- 服务器 credential 聚合事实为 rows/ACTIVE/encrypted non-empty=`1/1/1`；management health=`UP`。未查询、解密或输出 credential material。
- Attempt-05 最终 metadata 为 `FAILED / permission_scope=NULL / withdraw=false / ip=UNKNOWN / HTTP_ERROR`；FAILED audit=`1`、STARTED audit=`1`。
- 既有 management/GateW logs 与 evidence 都没有可恢复的 HTTP status 或 allowlisted OKX top-level code；safe status category count=`0`。

## Review decision

- 明文源必须原子删除三个字段，保留 owner-only ACL，且不得重建 secret pattern 做二次 exact-value scan。
- 既有 `HTTP_ERROR` 证据不足时只能结论 `SAFE_DIAGNOSTIC_INSUFFICIENT`，不得猜测 401/403/429/5xx 或 OKX code，也不得重跑真实 probe。
- transport/probe 只允许保存 canonical category；HTTP status 分组与 OKX allowlist mapping 必须脱敏，未知业务 code 固定为 `OKX_BUSINESS_REJECTED`，raw `msg/body/header/signature` 不得进入 metadata、audit、日志或 exception。
- permission finalize 必须保留最后已知 permission/withdraw/IP 风险事实；CAS conflict 与 metadata/audit 原子写回失败必须分别暴露 `VERSION_CONFLICT`、`ATOMIC_WRITEBACK_FAILED` 并整体 `BLOCKED`。
- 默认、CI、no-outbound、LIVE 或任一交易写侧开关冲突时必须选择 NoReal；仅 `gatew-okx-readonly-soak` + explicit permission flag + 全部显式安全布尔匹配时才允许装配 real read-only port，且 Bean 创建不得联网。
- Attempt-05 一次性 operational launcher 已删除，不能虚构源文件修复；本轮必须修复受支持的 Spring runtime/launcher serialization path，并证明 Java time 类型可序列化且输出无 credential 字段。
- supervisor commit identity 使用 Git blob object ID；上传字节另用 artifact SHA-256。不得改动既有 evidence hash-chain canonicalization。
- `nqgatew` GitHub authentication 必须既能读取 Actions run，又满足 least privilege；目标仓库实际为 `PUBLIC`，现有 `repo/workflow` broad scopes 不能作为通过证据。

## Findings

- P0：0。
- P1：服务器 GitHub least-privilege authentication 与新 exact-head artifact 的服务器部署尚未关闭；服务器 `gh 2.45.0` device flow 最低 scopes 固定含 `repo/read:org/gist`，无法直接满足 no-write-scope 要求，需用户提供不经聊天传递的 read-only credential。完成前 conformance 不得写 `READY_TO_COMMIT` 或最终 PASS。
- P2：现有脱敏 evidence 无 HTTP status/code，根因只能停在 `SAFE_DIAGNOSTIC_INSUFFICIENT`；这是 evidence limitation，不允许通过重跑 probe规避。
- P3：Attempt-05 一次性 launcher 无可追踪源码；受支持替代路径必须由 Spring-managed mapper 与仓库内 test-only launcher 回归证明。

## Boundary confirmation

- 不重跑真实 OKX permission probe，不启动 soak，不录入/修改 credential，不访问 raw response。
- 不新增 API、Controller、scheduler、migration、dependency、真实交易能力或公网 listener。
- 不修改 `nq-api`、`nq-scheduler`、frontend、research、deploy、`.github`、POM/lock、migration、Gate archive、STATUS 或 ROADMAP。

## Decision

`PASS / REMEDIATION_DESIGN_ACCEPTED / IMPLEMENTATION_AUTHORIZED`（通过 / remediation 设计已接受 / 允许在精确范围内实现）。

Post-review resolution：服务器最终使用 `gh 2.45.0` canonical top-level token layout；`gh auth status`、authenticated `/user` 与指定 Actions run 全部成功，OAuth scopes为空、rate limit=`5000`，least-privilege hard gate已关闭。
