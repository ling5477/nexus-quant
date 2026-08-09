# NQ-GATEW-2 OKX Spot Private Read-only Probe Implementation Attempt 02

## Final Decision

```text
IMPLEMENTED / PENDING_REVIEW
```

P0=0，P1=0。下一轮只对本次实际 diff 执行精简 security conformance review，不重新进行完整方案审查。

## Preflight and Authority Before

- branch：`dev`；起始 worktree clean、staged empty。
- starting HEAD：`2c7def771b8779c16b98810f09e5758161242ed6`。
- origin/dev：`2c7def771b8779c16b98810f09e5758161242ed6`。
- current exact-HEAD CI：`NQ CI Baseline` run `29222532638 / completed / success`。
- security review：当前 HEAD 即 `NQ-GATEW-2-SECURITY-REVIEW` commit，且 exact-HEAD CI green。
- authority before：`accepted_batch=GateW-1 / ACCEPTED|CI_GREEN`；`work_batch=GateW-2 / NOT_STARTED / NONE / NOT_RUN`；`next_action=NQ-GATEW-2-IMPLEMENTATION`。
- 当日只读复核 OKX 官方 API guide/changelog，允许的两个 Read operation、global host、签名构造、UTC timestamp、query 参与签名与 demo 显式 header 未发现实质漂移。

## Actual Structure and Module Dependencies

- `nq-adapter-api`：扩展 GateW-1 decision reason，仅增加 typed private read-only allow 语义；decision 仍固定不构成交易授权。
- `nq-adapter-okx`：operation/request、canonical query、environment、signer、sanitized error taxonomy、typed transport 与解析实现；不暴露 raw method/path/host/body/query map。
- `nq-infra`：owner/account/type 唯一 credential executor、非持久化 observation 与 config-before-balance probe service；core 未依赖 infra，未形成 Maven 循环。
- `nq-app`：唯一 composition root；只注册 read-only transport/executor/service，并在同一 profile 排除既有 mutating/private WebSocket Bean。
- `nq-core`：未修改；复用既有 owner-bound account repository port，不把 credential 或 signer 放进 domain/DTO。
- ArchUnit/module boundary tests 已通过；未新增 Maven dependency。

## Operation, Allowlist and Query Schema

仅存在以下两个 production operation：

| Operation | Method | Exact path | Query |
| --- | --- | --- | --- |
| `OKX_ACCOUNT_CONFIGURATION_READ` | `GET` | `/api/v5/account/config` | 无 |
| `OKX_ACCOUNT_BALANCE_READ` | `GET` | `/api/v5/account/balance` | 仅 canonical `ccy`：uppercase、去重、排序、最多 3 个、合法标识 |

host 精确固定为 OKX global REST host；redirect policy 为 NEVER。raw private path/method/host/body/query map 没有 production 入口。unknown、mutating、order、cancel、transfer、withdraw 与 funds movement 继续 default-deny。

## Signer and Transport

- signer 使用 injected `Clock`，生成 UTC ISO-8601 毫秒 timestamp；GET body 固定为空，canonical query 进入签名输入；固定测试向量与 query 差异测试通过。
- transport connect timeout 默认 2 秒/最大 5 秒，请求与读取 timeout 默认 5 秒/最大 10 秒；无自动 retry，单次并发 1。
- response 在接收阶段执行 256 KiB 上限，超限取消；payload 解析后 byte buffer 覆盖。
- production 只解析顶层 provider status、config permission 与 balance asset count/completeness；不返回 raw body、remote identifier 或数值余额。
- error taxonomy 覆盖 network、timeout、redirect、oversize、malformed、HTTP、provider、authentication、signature、permission、rate limit、clock skew、environment、partial、credential 与 account scope，全部 fail-closed。

## Credential Deterministic Selection and Scoped Executor

- key 固定为 `(ownerId, exchangeAccountId, credentialType=OKX_API_V5)`；owner-bound account、OKX、ACTIVE 与显式 environment 在解密前校验。
- SQL 精确过滤 active lifecycle；0 个返回 unavailable，1 个才解密，多个返回 conflict；无其他 type fallback，无 `ORDER BY ... LIMIT 1`。
- 解密只在 `JdbcOkxPrivateCredentialExecutor` 同步 callback 内发生；callback 只调用一次，直接返回 scoped context 会被拒绝。
- context 不进入字段、cache、DTO、event、future task 或持久化；可清理 `char[]`/`byte[]` 在 `finally` 覆盖，`toString()` 与异常仅输出脱敏类别。
- P2：JDBC driver 解密结果与 JDK HTTP header API 短暂经过 immutable `String`，无法可靠覆盖；生命周期限制在 executor/transport 内，未跨到 application/domain/DTO/log/evidence。可清理 credential、signing 与 response buffer 均显式覆盖。

## Probe and Observation

固定流程为显式 owner/account/type → account scope 校验 → credential 唯一选择 → scoped decrypt → account config → permission fail-closed → 条件性 balance → in-memory observation。

- config 必须先于 balance；只有 normalized permission 精确为 read-only 才继续。
- Trade、Withdraw、unknown、missing、empty、parse failure 或 config failure 均阻断 balance。
- balance partial 返回 `PARTIAL`，不把缺失字段补零。
- observation 仅包含 status/time/source/normalized permissions/asset count/completeness/blockers/warnings，并固定 diagnostic-only、no-side-effect、not-trading-authorization、LIVE-disabled、order-not-submitted。
- 不写 credential metadata、probe metadata、account、balance、position、order、ledger、audit 或 snapshot；未调用 generic mutating adapter。

## Spring/Profile Boundary

真实 private read-only transport 仅在 `gatew-okx-readonly` profile、feature flag true 且 LIVE false 时装配。default/local/test/CI、flag false 或 LIVE true 时无该 transport、无 decrypt、无 probe；context startup 不访问网络。

未新增 scheduler、runner、startup hook、后台轮询、Controller 或外部 API。production/demo 必须由调用方显式选择；demo 添加模拟交易 header，禁止自动 fallback 或同一 probe 切换环境。

## Tests and Results

- 定向 GateW tests：operation/query、signer、transport、credential、probe、Spring profile 全部通过；仅使用 fake exchange/mock，不访问 OKX。
- required target reactor：`mvn -f backend/pom.xml -pl nq-adapter-api,nq-adapter-okx,nq-core,nq-infra,nq-app -am test`，23/23 modules `SUCCESS`，0 failures/errors。
- full backend：`mvn -f backend/pom.xml test`，最终源码 23/23 modules `SUCCESS`，0 failures/errors；既有 configured skips 保持。
- 一次定向命令因 PowerShell 参数引号错误，在 Maven 参数解析阶段失败；修正后通过。最终内存收紧首轮复验在 adapter 编译阶段捕获局部 `ByteBuffer` 遮蔽字段导致的 `Arrays.fill` 类型错误；改为显式字段引用后重新执行 required target reactor 与 full backend，最终结果只采信修正后重跑。
- 既有 local Spring tests 只连接本机 PostgreSQL，schema V33 已是最新，未执行 migration；未连接生产数据库。

## Manual Smoke and External Effects

- `REAL_SMOKE=NOT_RUN`。
- `API_KEY=NOT_REQUIRED_FOR_IMPLEMENTATION`。
- 未调用 OKX API，未读取或使用真实 credential，未执行下单、撤单、转账、提现或任何资金移动。

## Findings and Safety Boundary

- P0：0。
- P1：0。
- P2：1；JDBC/JDK API 边界不可可靠清零的 immutable plaintext/authenticated-header `String` 采用最小局部生命周期，不跨层、不持久化、不记录。
- P3：0。
- LIVE、Shadow trading、AI、DH runtime、Integration runtime 与 private trading 均未开启；read-only probe 结果不构成 provider readiness、trading authorization 或 LIVE permission。
- 无 API/frontend/migration/dependency/scheduler/runner/persistence diff；禁止范围 diff 为空。

## Rollback

未提交状态下按逐文件 reverse patch 删除本 Attempt 02 新增类/测试，并还原 GateW-1 decision/matrix/guard、Spring profile 排除与 current docs；不得使用 hard reset、clean 或覆盖用户改动。回滚不涉及数据库、外部服务或远端 Git，因为本轮无 migration、real call、stage、commit 或 push。

## Authority After

```text
accepted_batch=GateW-1
accepted_batch_status=ACCEPTED|CI_GREEN
active_gate=GateW
active_gate_status=IN_PROGRESS|NOT_FROZEN
work_batch=GateW-2
work_batch_status=IMPLEMENTED|PENDING_REVIEW
work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEW-2-SECURITY-CONFORMANCE-REVIEW
```

Machine contract 将 `IMPLEMENTED|PENDING_REVIEW` 映射到 `REVIEW`；上述 action 以 `-REVIEW` 结尾，由 authority checker 支持，未修改治理 contract。

## Next Action

```text
NQ-GATEW-2-SECURITY-CONFORMANCE-REVIEW
```

下一轮仅针对本次实际 diff 做精简 security conformance review，不重新完整审查方案，不初始化 GateW-3。
