# GateX-0D 前端语义统一实施证据（attempt-01）

## 任务与边界

- 任务：`NQ-GATEX-0D-FRONTEND-SEMANTIC-UNIFICATION-IMPLEMENTATION`。
- 类型：NQ-only、L 级 `FRONTEND_REFACTOR / DESIGN_SEMANTICS / REGRESSION_TEST / SELF_REVIEW`。
- 起始分支与提交：`dev`，`HEAD == origin/dev == 46392213495652f6a09005148cc160fd2882adb9`；起始工作区与暂存区均为空。
- GateX-0C acceptance：exact-head `NQ CI Baseline` run `31325824949` 为 `completed / success`，因此同步为 `ACCEPTED / CI GREEN`（已接受 / CI 已通过）。
- 禁止边界：未修改 route、API、DTO、query/cache、RBAC、backend、research、scripts、`.github`、migration、依赖、交易流程、LIVE、credential、真实 provider、AI 或 DH runtime；未继续拆分约 6.3k LOC 的 `StrategyValidationWorkspace.tsx`；未执行 commit/push。

## StatusTag inventory 与收敛

Before：

- `frontend/src/nq-design-system/status/StatusTag.tsx` 提供 design-system 视觉组件，但没有统一业务状态映射。
- `frontend/src/components/nq/NqStatusTag.tsx` 独立维护 `STATUS_TONE` 与 Ant Design `Tag` 渲染。
- `frontend/src/nq-design-system/format/cells.tsx` 再维护一份 `STATUS_TONE`。
- Strategy Validation 还有两个领域 adapter，各自处理稳定领域文案与 tooltip。

After：

- `frontend/src/nq-design-system/status/StatusTag.tsx` 是唯一 canonical 状态展示合同，集中常见状态映射；unknown/空状态安全回退 `neutral`/`-`，不会 crash 或自动解释为 success。
- `NqStatusTag` 保留为 `@deprecated` 薄兼容 wrapper，只转发旧 props，不维护独立映射或颜色逻辑。
- `StatusCell` 删除重复映射并直接委托 canonical 组件。
- Strategy Validation 两个本地 adapter 只保留领域文案、tooltip 与显式 tone，实际渲染委托 canonical；没有继续拆分 workspace。
- canonical implementation=`1`，compatibility wrapper mapping=`0`，duplicate production mapping=`0`。

## 金融颜色语义

Before：Design System v1 已采用红涨绿跌；v2 默认 `INTL_CRYPTO`，即 `up=#33d6a6`、`down=#ff5c6c`，与 NQ 业务 UI 口径不一致。

After：

- 默认 market convention 固定为 `CN_STOCK`：`up=#ff5c6c`（红色上涨/正收益）、`down=#33d6a6`（绿色下跌/负收益）、`flat=#93a1ba`。
- system semantics 保持独立：`success=#3ad29f`、`danger=#ff6166`、`warning=#fbbf3f`、`info=#56c7f5`。
- `up != danger`、`down != success`、`profit/loss != system health`；CSS variables、TypeScript token 默认值与 design-system 说明一致。
- `font-variant-numeric: tabular-nums` 保持，未改变价格、百分比、精度或格式化合同。

## 用户可见阶段标签

共关闭 11 处普通用户语义污染：

1. production 环境 fallback：`GateJ-FREEZE` → `PAPER`。
2. Dashboard 风险 banner：工程阶段 → `Paper Trading（模拟交易）`。
3. Paper stability：`GateJ-FREEZE 7 天最终验收` → `正式 7 天稳定性验收`。
4. Validation Review Workbench：`GateV-2` → `持久化人工复核`。
5. Validation PageHero badge：`GateV-4` → `验证运营 · 本地人工复核`。
6. Validation PageHero tip：`GateT-5` → `只读运营复核`。
7. Artifact preview：`GateT-4` → `当前评估产物预览`。
8. Partial data：`GateT / GateS overview` → `验证运营或影子运行 overview`。
9. Detail sections：`GateS / GateT` → `影子运行与验证运营`。
10. Shadow Run list badge：`GateR-8` → `影子运行 · 只读列表`。
11. Shadow Run detail badge：`GateR-7 / GateS-2` → `影子运行详情 · 只读诊断`。

最终 `rg` 命中 48 行，全部分类为合法残留：

- `StrategyEvaluationGateResponse` / `getEvaluationGate` 等业务类型或标识符误命中，不是研发阶段标签。
- API、DTO、hook、代码注释中的历史 work-order/contract identity。
- Design System dev/debug、历史 metadata 与 evidence 注释。
- 普通用户主要 UI 残留=`0`；`REVIEW_REQUIRED=0`。未机械替换历史/API/type/test fixture 事实。

## 验证与 RCA

| Command / Check | Result | Scope / Warning |
| --- | --- | --- |
| `npm run build` | PASS（通过） | TypeScript + Vite production build；仅有既存 chunk size warning |
| 首轮 expanded 7-spec Chromium smoke | FAIL（失败） | 10 passed / 2 failed；review 的默认 `title=OPEN` 干扰既有 locator；Paper stability 因本地 backend `127.0.0.1:18888` 未启动而在登录前失败 |
| `NqStatusTag` wrapper 最小修复 | PASS | 显式 `title=""`，恢复旧 wrapper 无 title 的兼容行为；未改 canonical mapping |
| 6-spec targeted Chromium smoke | PASS | runner support 10/10；Playwright 11/11，覆盖 Design System status、Dashboard/Runtime、Paper 边界、Shadow、Strategy Validation 与 review |
| static semantic contract | PASS | canonical=1、wrapper mapping=0、红涨绿跌、system colors 独立、用户 UI pollution=0、48 行合法 residual、review required=0、tabular nums 保持 |

最终 targeted command：

```powershell
npm run test:e2e -- tests/e2e/design-system-table-smoke.spec.ts tests/e2e/dashboard-runtime-readiness-summary-smoke.spec.ts tests/e2e/runtime-paper-boundary-banners-smoke.spec.ts tests/e2e/shadow-run-detail-smoke.spec.ts tests/e2e/strategy-validation-paper-shadow-smoke.spec.ts tests/e2e/validation-review-workbench-smoke.spec.ts --project=chromium
```

Known warnings：既存 Ant Design deprecated/React 19 compatibility/one disconnected form console warning；均未导致 test failure。未运行全量 frontend E2E、Maven、Python 或远端 CI，因为本轮只修改前端语义与明确授权的 current docs/evidence；work batch 尚未提交。

## 自审结论

- Route、API/DTO、query/cache、RBAC、业务流程影响：`0`。
- Visual behavior：状态映射收敛、金融颜色统一、工程标签替换为稳定业务语义；功能和数据合同不变。
- GateX-0C decomposition debt：明确延后；workspace 未继续拆分。
- GateX-0E query/config hygiene：明确延后；未提前初始化。
- P0：0。
- P1：0。
- P2：0。
- P3：0。
- 结论：`IMPLEMENTED / SELF_REVIEWED / FRONTEND_SEMANTICS_UNIFIED / FRONTEND_REGRESSION_GREEN / READY_TO_COMMIT`。
- 唯一下一动作：`NQ-GATEX-0D-COMMIT-AND-PUSH`。
