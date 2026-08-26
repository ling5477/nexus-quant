# NQ-GATEY-6 Explicit Micro-Live Authorization Preflight and Work Order — attempt-01

## Result

`PASS / GATEY_6_FIRST_REAL_ORDER_PREFLIGHT_DEFINED / HARD_GATE_MATRIX_RECONCILED / CAPABILITY_AND_PILOT_BINDING_SEPARATED / REAL_PROVIDER_GAPS_IDENTIFIED / CREDENTIAL_AND_IP_GAPS_IDENTIFIED / EXACT_PILOT_SCOPE_CONTRACT_FROZEN / 120H_SOAK_CONTRACT_FROZEN / EXPLICIT_AUTHORIZATION_NOT_GRANTED / FIRST_REAL_ORDER_NOT_AUTHORIZED / MICRO_LIVE_NOT_AUTHORIZED / LIVE_DISABLED / IMPLEMENTED|PENDING_REVIEW`

本 evidence 记录 documentation/governance-only preflight。它不接受 work order，不授权 credential 访问、OKX 调用、真实订单、kill disengage、LIVE 或 120h soak；必须经过独立 Security/Operations Review 与后续 exact-head CI。

## Baseline

| Fact | Exact value |
| --- | --- |
| Repository / branch | `F:\project\nexus-quant` / `dev` |
| Worktree / staged before | clean / empty |
| Local HEAD | `8a3b2981668b53b492a9a46a6b4b381f7f656782` |
| `origin/dev` after fetch | `8a3b2981668b53b492a9a46a6b4b381f7f656782` |
| Exact-head CI | `NQ CI Baseline` run `31764829976`，`completed / success / bad jobs=0` |
| Current authority check before | `PASS / CURRENT_AUTHORITY_CONSISTENT / errors=0` |
| Accepted batch | GateY-5 `ACCEPTED|CI_GREEN`；implementation `8d594f1a0000678e4817f3ec80de19ac975da992`；acceptance head `88f6f7f25a81f55fe17984df335546ad2033c61f`；CI `31761584826` |
| Work batch before | GateY-6 `NOT_STARTED / NONE / NOT_RUN` |
| Safety facts before | real provider/private trading=`NOT_IMPLEMENTED`；FIRST_REAL_ORDER/micro-live=`NOT_AUTHORIZED`；LIVE=`DISABLED`；kill=`ENGAGED` |

`git fetch origin` 与 `gh run view 31764829976` 仅用于精确基线核对；未访问交易所或生产系统。

## GateY-5 accepted binding and qualification

- GateY-5 fake-only worker、durable `SEND_STARTED`、NO BLIND RETRY、query-first recovery、rollback/restore/incident/reconciliation 与 operator visibility 的 accepted evidence绑定 implementation `8d594f1a...`、acceptance head `88f6f7f...`、CI `31761584826`。
- Lock-window准确口径是 `CLOSED_FOR_REVIEWED_SYNTHETIC_DISPOSABLE_GATEY_SCALE`，只支持engineering deployment risk evidence，不是production migration authorization或production SLA。
- Fake provider recovery/partial-fill/cancel-race PASS不等于real OKX semantics PASS。
- GateY-4 private credential能力仅覆盖read-only diagnostic；real smoke未运行，remote permission/IP未验证。

## Hard-gate reconstruction

从 `docs/current/GATEY_PLAN.md` 独立重建20项plan gates，并增加10项 `HARD_GATE_GAP_CANDIDATE`：

1. real provider mutation path implemented/reviewed；
2. private trading path implemented/reviewed；
3. instrument metadata freshness；
4. tick size / lot size / minimum order value；
5. available balance；
6. fee assumptions；
7. clock/time synchronization；
8. venue order-state translation；
9. real query-by-clientOrderId；
10. creator/approver separation。

Manifest totals：

| Metric | Count |
| --- | ---: |
| total hard gates | 30 |
| final `PASS` | 0 |
| final `NOT_MET` | 25 |
| final `NOT_VERIFIABLE` | 5 |
| gap candidates | 10 |

三层规则已逐项执行：只有 `capabilityStatus=PASS` 且 `pilotBindingStatus=PASS` 才允许 final PASS。本轮没有pilot scope、credential、account、release、risk、symbol、cap、window、identities或authorization物化，因此没有任何final PASS。

## Real-provider and architecture audit

已只读检查：

- `backend/nq-adapter-okx/.../OkxExchangeAdapter.java`
- `backend/nq-adapter-okx/.../OkxSpotEndpointGuard.java`
- `backend/nq-adapter-okx/.../OkxHttpClient.java`
- `backend/nq-adapter-api/.../DefaultAdapterReadinessService.java`
- `backend/nq-core/.../ScopedCredentialCapabilityPolicy.java`
- `backend/nq-core/.../LiveSessionControlService.java`
- `backend/nq-infra/src/main/resources/db/migration/V39__gate_y2_live_session_fact_model.sql`

Findings：

- Repository存在legacy OKX adapter的PLACE/QUERY/CANCEL/fill代码，但current `DefaultAdapterReadinessService`对OKX mutation fail-close，`OkxSpotEndpointGuard`仅允许typed private read-only operations并拒绝`PRIVATE_MUTATING`。因此current默认wiring未发现可绕过GateY-6显式授权的真实mutation路径，P0=0、P1=0。
- Legacy adapter不是GateY isolated worker可安全消费并经独立review接受的real-provider contract；current authority继续 `real_provider=NOT_IMPLEMENTED`、`private_trading=NOT_IMPLEMENTED`。
- Reusable primitives仅包括部分request/parse、signing/timeout、GateY-3 stable idempotency与GateY-4 JIT reference；endpoint allowlist、response cap/rate limit、clock skew、real error taxonomy、typed single-order query、state translation与真实convergence仍需EXTEND或NOT_IMPLEMENTED。
- V39/domain能表达本工作单mandatory durable facts；当前未发现必须立即创建V40的hard gap。
- `LiveSessionControlService`能拒绝creator self-approval，但具体independent identities未绑定，final gate仍为NOT_MET。

## Credential and IP state

本轮credential read=`0`，remote permission probe=`0`，OKX calls=`0`。状态保持：

```text
SCOPED_PILOT_CREDENTIAL=NOT_MET
TRADE_PERMISSION=NOT_VERIFIABLE
WITHDRAW_DISABLED=NOT_VERIFIABLE
TRANSFER_DISABLED=NOT_VERIFIABLE
IP_ALLOWLIST=NOT_VERIFIABLE
```

Future procedure只允许NQ credential management/JIT；dedicated GateY pilot key、minimum TRADE、withdraw false、transfer/funding unavailable、IP allowlist、expiry/rotation/revoke均需独立审查和非mutation验证。不得通过chat、Markdown、Git、shell history、CLI argument、fixture或evidence传递credential material。

## Pilot scope and authorization

`pilotSessionId`、account/credential/owner/creator/approver references、release/risk IDs与digests、artifact digest、symbols、capital/order/position/daily-loss caps、order counts、window、expiry与scope hash均为 `UNRESOLVED`；只有venue固定为 `OKX_SPOT`。没有自行选择symbol或金额。

Current request只要求work order，不构成交易授权：

```text
EXPLICIT_MICRO_LIVE_AUTHORIZATION=NOT_GRANTED
FIRST_REAL_ORDER=NOT_AUTHORIZED
MICRO_LIVE=NOT_AUTHORIZED
LIVE=DISABLED
kill_switch=ENGAGED
```

## Proposed GateY-6 batches

- GateY-6A：本hard-gate/work-order/manifest，documentation-only。
- GateY-6B：real-provider mutation contract + endpoint guard + fake/stub/no-egress contract tests。
- GateY-6C：credential/IP/private permission lifecycle与真实read-only verification，NO MUTATION。
- GateY-6D：exact pilot scope materialization与independent approval，kill remains ENGAGED。
- GateY-6E：未来单独显式授权与exactly-one LIMIT order；不得自动第二单。
- GateY-6F：120h controlled soak与terminal reconciliation；不得从第一单自动进入。

## Security findings

| Priority | Count | Finding / disposition |
| --- | ---: | --- |
| P0 | 0 | 未发现current默认路径可绕过authorization/kill发送真实mutation |
| P1 | 0 | 未发现reachable real fallback、withdraw/transfer path、worker self-authorization或credential control-plane escape |
| P2 | 2 | legacy adapter尚非GateY worker-safe contract；mutation rate/clock/response/error/convergence controls未形成accepted contract。两者阻塞future implementation，但current mutation仍fail-close |
| P3 | 1 | `CLAUDE.md`仍含历史GateJ/GateK阶段文字；`STATUS.md` checker与项目规则已明确其不得覆盖current authority，本任务不扩大范围修改 |

## Product and trading boundary

```text
backend diff=0
frontend diff=0
research diff=0
migration diff=0
scripts diff=0
deploy diff=0
.github diff=0
credential access=0
exchange calls=0
worker starts=0
trading side effects=0
production operations=0
```

## Authority transition

Before：GateY-5 accepted；GateY-6 `NOT_STARTED / NONE / NOT_RUN`；next action为本implementation task。

After candidate：accepted batch保持GateY-5；GateY-6 `IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；real provider/private trading保持NOT_IMPLEMENTED；下一动作只能是：

```text
NQ-GATEY-6-EXPLICIT-MICRO-LIVE-AUTHORIZATION-PREFLIGHT-AND-WORK-ORDER-SECURITY-OPERATIONS-REVIEW
```

## Validation evidence

| Command/check | Result | Exact summary |
| --- | --- | --- |
| manifest parse/contract | PASS（通过） | JSON parse；gates=`30`；duplicate IDs=`0`；unknown status=`0`；final-status logic errors=`0`；final `PASS/NOT_MET/NOT_VERIFIABLE=0/25/5`；gap candidates=`10` |
| exact evidence cross-check | PASS AFTER RCA（RCA后通过） | draft GateX-5 SHA与STATUS/strict archive不一致；final修正为implementation `3336bd8153845d5368a0d65a9c72d3566dc9bd35`、acceptance `a383be750f51d063d429bc25fad80e60dffb7014`、CI `31512467501`；错误值未保留 |
| manifest evidence/safety | PASS（通过） | missing evidence refs/paths=`0/0`；secret-like value fields=`0`；FIRST_REAL_ORDER=`NOT_AUTHORIZED`；authorization=`NOT_GRANTED`；micro-live=`NOT_AUTHORIZED`；LIVE=`DISABLED`；kill=`ENGAGED`；real provider/private trading=`NOT_IMPLEMENTED` |
| current authority | PASS（通过） | `PASS / CURRENT_AUTHORITY_CONSISTENT / errors=0`；GateY-6=`IMPLEMENTED|PENDING_REVIEW / UNCOMMITTED / NOT_RUN`；next action为独立review |
| docs links final | PASS WITH HISTORICAL WARNINGS（通过并有历史warning） | `287 checked / 14 warnings / 0 errors`；14项均为append-only TESTING中的既有GateJ/GateX历史路径 |
| first link invocation | FAILED BEFORE SCAN（扫描前失败） | nested child-PowerShell将array展开为positional argument，`PositionalParameterNotFound`、exit=`1`；RCA后使用当前PowerShell direct-array调用并通过；无写副作用 |
| wildcard diagnostic | FAILED / NO WRITE（失败 / 无写操作） | Windows `rg` literal wildcard返回`os error 123`；后续以explicit paths完成placeholder/hygiene验证，不影响final authoritative checks |
| git/diff boundary | PASS（通过） | changed paths=`11`、staged=`0`、unexpected/missing=`0/0`；backend/frontend/research/migration/scripts/deploy/.github diff=`0`；`git diff --check` errors=`0`，仅既有LF→CRLF warning |
| product tests | NOT RUN（未运行） | documentation/governance-only；产品/migration/workflow diff为0；采用exact-head baseline CI `31764829976`，不重复Maven/frontend/Python tests |

Current-summary checker要求根README与`docs/current/README.md`的next action均与STATUS一致，因此这两个摘要作最小同步；未扩大重写。

## Rollback

未提交时只恢复本轮current docs并删除本轮3个新增文件；不得使用破坏性Git命令覆盖其他变更。提交后应以独立revert commit回滚。任何独立review P0/P1都会阻止后续commit/acceptance，且不改变FIRST_REAL_ORDER/LIVE/kill安全状态。

## Next action

`NQ-GATEY-6-EXPLICIT-MICRO-LIVE-AUTHORIZATION-PREFLIGHT-AND-WORK-ORDER-SECURITY-OPERATIONS-REVIEW`
