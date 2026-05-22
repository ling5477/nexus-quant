# PRE-FREEZE-CODE-AUDIT Fix Plan

> 配套文档：`PRE_FREEZE_AUDIT_REPORT.md`
> 计划日期：2026-05-22
> 范围：根据本轮 PRE-FREEZE-CODE-AUDIT 的发现给出修复计划。本轮只产生计划，不直接修代码。

## 1. Fix plan status

- Codex second-pass audit 已完成。
- 无 P0。
- No P0 blockers after Codex second-pass audit。
- P1-1（E2E 未实际重跑）已关闭：Codex 本轮实际执行完整 `npm run test:e2e`，结果 24 passed / 1 skipped / 0 failed。
- P1-2（Python pytest/mypy/ruff 未实际重跑）已关闭：Codex 本轮实际执行 Python 三件套并全部通过。
- P1-3（`PaperTradingPage.tsx` 膨胀）仍为 GateK 前重构建议，不阻塞 GateJ-FREEZE。
- P1-4（GateJ-FREEZE 验收记录模板）已闭环。
- P2 / P3 不阻塞，集中放入后续 FULL-AUDIT 或专项工单。

## 2. P0 fixes

**No P0 blockers found.**

## 3. P1 fixes

### P1-1：E2E 基线重跑（Codex second-pass 已关闭）

- Codex second-pass status：已关闭。
- 本轮实际结果：后端 local profile 启动成功，`/actuator/health` 返回 `UP`；完整 E2E 25 tests total，24 passed / 1 skipped / 0 failed。
- 唯一 skipped：`E2E_TRADE_ORDER_ID` 未配置的既有订单详情链路，与 GateJ 主链无关。

- 背景：本轮 PRE-FREEZE-CODE-AUDIT 在审查窗口未启动后端 local profile，未实际执行 E2E；沿用 GateJ-3-WO 24 passed / 1 skipped 通过基线。
- 入场前要做：
  1. 启动本地 PostgreSQL（端口 5432）。
  2. 启动后端 local profile：`mvn -f backend/pom.xml -pl nq-app -am spring-boot:run '-Dspring-boot.run.profiles=local'`。
  3. 确认 Flyway 版本到 V25，确认本地数据库存在种子 `accounts.account_id=3001`。
  4. 执行 `cd frontend && npm run test:e2e`。
  5. 期望结果：≥ 24 passed / 1 skipped（skipped 仅为 `E2E_TRADE_ORDER_ID` 未配置的既有订单详情链路）。
- 不通过的处理：必须将失败计入 GateJ-FREEZE 验收记录，不允许把失败写成通过。
- 处理范围：仅启动 + 执行 + 记录，不修业务代码。

### P1-2：Python `pytest / mypy / ruff` 基线重跑（Codex second-pass 已关闭）

- Codex second-pass status：已关闭。
- 本轮实际结果：
  - `python -m pytest -q`：2 passed。
  - `python -m mypy src`：Success，8 source files 无问题。
  - `python -m ruff check .`：All checks passed。
- 环境备注：默认 shell `python` 仍指向 WindowsApps alias；Codex 本轮使用 workspace bundled Python 临时置于 PATH 首位后执行同样的 `python -m ...` 命令。后续人工复跑需使用真实 Python 解释器或修正 PATH。

- 背景：本轮 PRE-FREEZE-CODE-AUDIT 当前 shell 仅 WindowsApps stub，无真实 Python 解释器，无法执行；沿用 BASELINE-FIX-2 / GateJ-3 通过基线。
- 入场前要做：
  1. 在具备真实 Python 3.11+ 的本地或 CI 窗口执行：
     ```powershell
     Set-Location research/py
     python -m pip install -e ".[dev]"  # 若 editable install 卡顿，可降级为 python -m pip install pytest mypy ruff
     python -m pytest -q
     python -m mypy src
     python -m ruff check .
     ```
  2. 期望结果：`pytest` 2 passed、`mypy` Success、`ruff` All checks passed。
- 不通过的处理：必须修复后才允许 GateJ-FREEZE。
- 处理范围：仅执行 + 记录，不修业务代码。

### P1-3：`PaperTradingPage.tsx` 重构（不阻塞 GateJ-FREEZE）

- 背景：当前 page 已承载 15 个 Tab、Drawer width 1280、30+ TanStack Query hooks，维护负担可见。
- 处理建议：
  - 在 GateK 前单独开 P1 工单，按 Tab 拆 4–5 个子组件，将 hooks 与 Tab JSX 一一对应。
  - 不在 GateJ-FREEZE 范围内执行。
- 处理范围：GateK 前的前端重构工单（FULL-AUDIT 或 GateK-PRE）。

### P1-4：GateJ-FREEZE 验收记录模板（已闭环）

- 背景：缺少 1h/24h/7d 验收记录的统一模板。
- 处理：本轮已新增 `docs/current/GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md`，含验收窗口、Paper run ID、环境、启动/结束时间、心跳数、alert 数、recovery 数、failed fire 数、report 数、stability check 结果、E2E 结果、结论、签收记录字段。
- 处理范围：仅文档，已闭环。

## 4. P2 follow-ups

下列 P2 不阻塞 GateJ-FREEZE，建议在 GateJ-FREEZE 完成后、GateK-PLAN 启动前的 FULL-AUDIT 或专项工单中处理：

| P2 ID | 项 | 处理建议 |
| --- | --- | --- |
| P2-1 | npm audit 4 个告警 | 专项依赖升级工单（与 GateK 准备同步） |
| P2-2 | Vite chunk > 500 kB | 前端构建优化专项工单 |
| P2-3 | Ant Design React 19 / Card.bordered / Modal.destroyOnClose deprecation | 前端依赖与 API 升级专项工单 |
| P2-4 | 日报占位 0（total_equity / daily_pnl / max_drawdown） | 撮合回写完整后补联动 |
| P2-5 | cron 仅字段数校验 | 后台调度器接入或单独 cron 解析库引入时补 |
| P2-6 | fire 状态第一版固定 SUCCEEDED | 后台调度器接入时补失败路径 |
| P2-7 | 后台常驻调度器未实现 | 单独评估 Spring Scheduler 接入 |
| P2-8 | heartbeat lag 阈值固定 300s | 增加运行时配置项 |
| P2-9 | alert 去重只按 alert_type + 5 分钟 | 增加 fire_id / event 维度去重 |
| P2-10 | uptime_ratio 粗略口径 | GateK 前细化按时间加权 |
| P2-11 | Python editable install 不稳 | 替换为 wheel 或固定依赖，或在 README 写明 fallback |
| P2-12 | E2E 期间 Ant Design runtime warning 集合 | React 19 compatibility、`Card.bordered`、`Modal.destroyOnClose`、`useForm` 未连接、`Descriptions` span 合计不匹配；前端专项处理 |

## 5. P3 long-term improvements

- 前端组件拆分与体验优化。
- 表格→图表升级。
- 命名统一微调。
- README 表述精简。
- E2E_TRADE_ORDER_ID 既有交易订单链路 skip（与 GateJ 主链无关，可在后续配置 E2E_TRADE_ORDER_ID 后补跑）。

## 6. Traceability fixes

无文档/代码可追溯性 P0/P1 fix。本轮 cross-check 结果：
- 100% endpoint 在 `API.md` 中存在。
- 100% 新增表/字段在 `DB_SCHEMA.md` 中存在并匹配 Flyway migration。
- 100% 后端单元测试 / Service 在 WORKLOG 与 TESTING 中体现。
- 100% E2E spec 在 TESTING / WORKLOG 中体现。
- 100% 前端 Tab 与后端 endpoint 对应。

## 7. Validation commands after fixes

GateJ-FREEZE 入场前 / 验收期间 / 验收完成时必须执行：

```powershell
git status --short

mvn -f backend/pom.xml test

Set-Location frontend
npm run build
npm run test:e2e

Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

不通过不允许把验收写成通过。

## 8. GateJ-FREEZE entry conditions

允许进入 GateJ-FREEZE 的前提：
1. PRE-FREEZE-CODE-AUDIT 审查报告与本修复计划已提交并审阅。
2. P1-1 / P1-2 已在 Codex second-pass audit 中完成重跑：
   - `npm run test:e2e`：24 passed / 1 skipped / 0 failed。
   - Python `pytest`：2 passed；`mypy`：Success；`ruff`：All checks passed。
3. 后端 `mvn -f backend/pom.xml test` BUILD SUCCESS（Codex second-pass 已执行通过）。
4. 前端 `npm run build` 通过（Codex second-pass 已执行通过）。
5. GateJ-FREEZE 1h/24h/7d 验收按 `GATEJ_FREEZE_ACCEPTANCE_TEMPLATE.md` 记录，不夹带 AI 或新功能。
6. 不创建 `docs/gates/gate-j/`，直到 GateJ 整体通过验收。

## 9. Current decision

- 无 P0。
- Codex second-pass 已实际执行并通过 E2E 与 Python 基线，P1-1 / P1-2 已关闭。
- P1-3 不阻塞 GateJ-FREEZE；P1-4 已闭环。
- P2 / P3 不阻塞。

**GateJ-FREEZE allowed after review/commit.**

GateJ-FREEZE 只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新业务功能。

## 10. Codex second-pass validation closure

本轮 Codex 二次审查已关闭 Claude 第一轮遗留的验证缺口：

| 命令 | 本轮实际结果 | GateJ-FREEZE 影响 |
| --- | --- | --- |
| `mvn -f backend/pom.xml test` | 通过；23 个 module SUCCESS，`nq-app` 35 tests / 0 failures / 0 errors | 不阻塞 |
| `cd frontend && npm run build` | 通过；仍有 Vite chunk > 500 kB 警告 | P2，不阻塞 |
| `cd frontend && npm run test:e2e` | 通过；24 passed / 1 skipped / 0 failed | 不阻塞 |
| `cd research/py && python -m pytest -q` | 通过；2 passed | 不阻塞 |
| `cd research/py && python -m mypy src` | 通过；8 source files 无问题 | 不阻塞 |
| `cd research/py && python -m ruff check .` | 通过；All checks passed | 不阻塞 |

Final: GateJ-FREEZE allowed after review/commit。若后续在 GateJ-FREEZE 正式验收窗口重新执行上述命令并失败，必须记录失败并转入 AUDIT-FIX，不允许把失败写成通过。
