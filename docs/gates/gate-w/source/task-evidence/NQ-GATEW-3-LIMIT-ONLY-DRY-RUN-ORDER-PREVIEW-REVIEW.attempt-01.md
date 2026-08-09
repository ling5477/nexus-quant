# NQ GateW-3 LIMIT-only Dry-run Order Preview Review Attempt-01

> 日期：2026-07-14
> 审查对象：真实 working-tree production/test/docs diff
> 审查模式：独立 conformance review

## 1. Scope

已审查：`nq-core` 新增/修改 production 与 tests、attempt-02 合同、implementation evidence、current authority 变更。未审查 runtime DB row 的实际存在/新鲜度；该项按合同保持 `UNKNOWN`。明确不涉及 Controller/API、frontend、migration、network/OKX HTTP、credential/account/balance、order submission、LIVE、DH、AI。

## 2. Actual diff review

本审查重新读取真实 diff、untracked production/test files、构造依赖和测试实现，没有仅引用 implementation evidence。

| 审查项 | 结果 | 证据 |
|---|---|---|
| no `TradingAdapter` dependency | PASS | production prohibited-reference scan=0；service fields 仅 read port、freshness evaluator 与 static logger |
| no order write dependency | PASS | 无 order command/state machine/repository writer/venue gateway 引用 |
| no credential/account/balance dependency | PASS | production scan=0；dependency-reflection test PASS |
| no network | PASS | 无 provider/HTTP/client/sync dependency；structural blocker 为零 local read |
| no DB write / persistence | PASS | preview 依赖 `InstrumentCatalogReadPort`，只暴露 bounded read；无 transaction/write method |
| no API/frontend/migration | PASS | forbidden changed count=0 |
| no hidden rounding | PASS | exact BigDecimal remainder；原始 price/quantity 不被修改；mismatch 阻断 |
| no misleading readiness | PASS | result invariant 强制 `executionReadiness=BLOCKED`，无 valid/approved/ready/canTrade 字段 |
| unknown != pass | PASS | min notional、fee、permission 为 `unknowns`；balance/risk 为 `notEvaluated` |
| zero-call tests meaningful | PASS | dependency-reflection、CountingReadPort、result record components 与 safety invariant 分别验证对象图、调用次数、无 order ID 和固定安全状态 |

## 3. Test and inspection evidence

- `nq-core -am test`：5/5 reactor modules SUCCESS；nq-core 288 tests，0 failures/errors/skipped；BUILD SUCCESS。
- 定向复测：45 tests（preview 37 + freshness 8），0 failures/errors/skipped；BUILD SUCCESS。
- 指定 targeted reactor：因 `nq-app -am` 依赖闭包覆盖 23 modules，23/23 SUCCESS；BUILD SUCCESS。
- 精确 full regression：`mvn -f backend/pom.xml test`，23/23 reactor modules SUCCESS；BUILD SUCCESS。
- IDEA `get_file_problems`：修改后的 preview production/test 均 0 problems；其余新增/修改 production 文件 0 errors。
- `git diff --check`：PASS（仅 Git 的 LF/CRLF checkout warning，无 whitespace error）。
- forbidden changed scope：0。
- production prohibited reference scan：0。

## 4. Findings

### P0

- 无。

### P1

- 无。

### P2

- 无实现 conformance finding。Runtime fact presence/freshness 仍未验证，属于冻结合同中的显式 `UNKNOWN`，不是 readiness PASS。

### P3

- 单元测试 classpath 无 SLF4J provider，故 local-read failure case 输出标准 NOP logger warning；该警告来自 test runtime logging provider，production 代码只记录稳定错误码且不输出异常/参数，不影响边界或结果。
- 既有 Spring application tests 输出自动生成的 test-only development password warning；本轮没有读取、持久化或复制真实 credential，值未进入 evidence。该既有日志卫生问题不由 preview diff 引入。

## 5. Required self-check

- 循环调用 API：无；API/network 调用总数为 0。
- 循环查询 DB：无；每次 preview 最多一个 symbol 的一次 bounded read。
- 无分页大数据：无；port 强制 1..3，preview 固定 1。
- 敏感信息：无 credential 输入/依赖/日志；local-read exception 不记录原始异常和参数。
- timeout/retry/rate limit：无外部调用，均不适用；不消费现有 stateful rate-limit。
- transaction/idempotency/resource release：无写、无事务、无资源句柄；相同 input/facts deterministic。
- 失败路径：覆盖 MARKET、exchange/type/state、missing/stale/schema/checksum/future time、数值/对齐/上下限、local-read failure 与 safety invariant。
- 架构/生产/真实交易：不改变交易核心状态机，不触达真实交易或 LIVE。

## 6. Authority transition

Governance library 验证：

```text
IMPLEMENTED|PENDING_REVIEW
-> REVIEW_ACCEPTED|READY_TO_COMMIT
result=PASS

work_batch_commit=UNCOMMITTED
work_batch_ci_run=NOT_RUN
accepted_batch=GateW-2
next_action=NQ-GATEW-3-LIMIT-ONLY-DRY-RUN-ORDER-PREVIEW-COMMIT-AND-PUSH
```

## 7. Final decision

```text
P0=0
P1=0

PASS /
LIMIT_ONLY_INTERNAL_ORDER_PREVIEW_ACCEPTED /
READY_TO_COMMIT
```

该结论只允许精确暂存、提交和 exact-head CI；不接受 GateW-3，不冻结 GateW，不开启 LIVE/private trading。
