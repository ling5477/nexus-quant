# GateX-0C Strategy Validation 前端拆分实施证据（attempt-01）

## 任务与边界

- 任务：`NQ-GATEX-0C-VALIDATION-FRONTEND-DECOMPOSITION-IMPLEMENTATION`。
- 类型：NQ-only、L 级 `FRONTEND_REFACTOR / FEATURE_DECOMPOSITION / BEHAVIOR_PRESERVATION`。
- 起始分支与提交：`dev`，`HEAD == origin/dev == 108a14d14906d6fa354349c66d35a2ae6967cebf`；起始工作区与暂存区均为空。
- GateX-0B acceptance：exact-head `NQ CI Baseline` run `31321821962` 为 `completed / success`，因此同步为 `ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- 禁止边界：未修改 backend、API contract、query key、migration、依赖、router contract、交易状态机、LIVE、credential、真实 provider、AI 或 DH runtime；未执行 commit/push。

## 拆分前审计

- `StrategyValidationPage.tsx`：6,412 LOC。
- 页面内有 64 个以大写命名的 component/panel helper、12 个既有只读 query hook 调用、1 组 URL/search-param 协调、1 组本地 submitted query 状态；无页面内 mutation。
- 主要职责覆盖 review workbench、runtime evidence、validation overview、shadow workflow、consistency、artifact preview、incident replay/review、Paper/Shadow workbench、traceability、evidence matrix 与三个 query result panel。
- review drawer、RBAC、mutation 与 cache invalidation 已由既有 `ValidationReviewWorkbench` 及其 hooks/components 封装，本轮不重复创建近义实现。

## 实施结果

- `StrategyValidationPage.tsx`：39 LOC，减少 6,373 LOC，降幅约 99.4%；现在只保留 URL/search-param coordination、submitted query 页面状态与 feature composition。
- 新增 `StrategyValidationWorkspace`：承接原有 validation sections 与展示 helpers，保留原 JSX、状态映射、错误语义和 Ant Design 结构。
- 新增 `useStrategyValidationWorkspaceQueries`：集中承接原页面 12 个只读 query 调用、shadowRunId 选择与 loading 聚合；调用顺序、参数、query key、enabled 条件和 cache 语义未改。
- 新增 `ValidationReviewSection`：组合原 PageHero 与既有 `ValidationReviewWorkbench`；reviewCaseId URL、drawer、RBAC、mutation 和 invalidation 仍由既有实现处理。
- API endpoint、request/response DTO、route、query-key contract、cache invalidation、RBAC、review lifecycle、Paper/Shadow、分页、筛选、按钮与用户可见功能均未改。

## 验证

| Command / Check | Result | Scope / Warning |
| --- | --- | --- |
| `npm run build` | PASS（通过） | TypeScript build 与 Vite production build 通过；仅有既存 chunk size warning |
| `npm run test:e2e -- tests/e2e/strategy-validation-paper-shadow-smoke.spec.ts tests/e2e/validation-review-workbench-smoke.spec.ts --project=chromium` | PASS | runner support 10/10；Chromium 6/6，覆盖页面 sections、reviewCaseId 深链/清除、drawer、review mutation/invalidation、权限与错误态 |
| 0C 初始化 authority checker 首轮 | FAIL（失败） | `NOT_STARTED` sentinel 与三个入口 next-action 摘要不一致；未改合同，最小修正后重跑 |
| `scripts/docs/check-current-authority.ps1`（0C 初始化态） | PASS | `AUTHORITY_CHECK errors=0` |

E2E known warning：Ant Design v5 对 React 19 compatibility 的既有 console warning；未导致 test failure。本任务未运行 Maven、Python 或全量 frontend E2E，因为未修改对应模块，且附件要求只跑关键 validation smoke。

## 剩余结构债与延后项

- `StrategyValidationWorkspace.tsx` 仍约 6.3k LOC；本轮以行为保持和解除 page monolith 为优先，只提取低耦合 review section 与 query orchestration。后续若获单独授权，可继续按 overview/evidence/lifecycle/shadow/consistency 拆分，但不得在本次 commit/push 动作中扩大范围。
- GateX-0D 的 `NqStatusTag / StatusTag` 统一、红涨绿跌、用户标签去阶段化与 Design Token 调整全部原样保留，未顺手修复。

## 自审结论

- P0：0。
- P1：0。
- P2：1；feature workspace 仍大，属于明确保留的渐进式结构债，不影响本轮行为保持验收。
- P3：0。
- 结论：`IMPLEMENTED / SELF_REVIEWED / VALIDATION_FRONTEND_DECOMPOSED / FRONTEND_REGRESSION_GREEN / READY_TO_COMMIT`。
- 唯一下一动作：`NQ-GATEX-0C-COMMIT-AND-PUSH`。
