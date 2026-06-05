# GateJ-FREEZE Acceptance Template

> 用于 GateJ-FREEZE 阶段 1h / 24h / 7d 连续运行验收记录。
> 本模板只是结构，不允许把失败写成通过。每次验收必须独立填写一份。

## 1. 验收元数据

- 验收窗口：1h / 24h / 7d（圈选一项）
- 验收编号：GateJ-FREEZE-ACCEPTANCE-<N>
- 验收人：<填写>
- 验收日期（开始 / 结束）：<YYYY-MM-DD HH:mm UTC> / <YYYY-MM-DD HH:mm UTC>

## 2. 环境信息

- 后端版本（git commit SHA）：<填写>
- 前端版本（git commit SHA）：<填写>
- Flyway 当前版本：V25
- 数据库：本地 PostgreSQL 5432（或填写）
- 是否使用 fixture `accounts.account_id=3001`：是 / 否
- 操作系统 / Node / Java 版本：<填写>

## 3. Paper run 信息

- Paper Run ID：<填写>
- Trade env：SIM
- Exchange / Market / Symbol / Interval：<填写>
- 创建时间（UTC）：<填写>
- 启动时间（UTC）：<填写>
- 停止时间（UTC）：<填写>

## 4. 调度信息

- 调度计划 ID：<填写>
- cron：<填写>
- 调度时区：<填写>
- 验收期间状态变更（ENABLED / DISABLED / PAUSED 切换次数）：<填写>

## 5. 指标计数（窗口内）

| 指标 | 计数 | 备注 |
| --- | --- | --- |
| Heartbeat 总数 | | |
| Heartbeat OK 数 | | |
| Heartbeat LAGGING 数 | | |
| Heartbeat STOPPED 数 | | |
| Heartbeat UNKNOWN 数 | | |
| Schedule fire 总数 | | |
| Schedule fire SUCCEEDED 数 | | |
| Schedule fire FAILED 数 | | |
| Schedule fire SKIPPED 数 | | |
| Daily report 总数 | | |
| Alert 总数 | | |
| Alert OPEN 数（未处理） | | 必须包含 CRITICAL 未处理数 |
| Alert CRITICAL 未处理数 | | |
| HEARTBEAT_LAG 自动告警数 | | |
| SCHEDULE_FIRE_FAILED 自动告警数 | | |
| Recovery 事件总数 | | |
| Recovery SUCCEEDED 数 | | |
| Recovery FAILED 数 | | |
| Retry failed step 总数 | | |
| Emergency stop 触发数 | | |
| Stability check 总数 | | |
| Stability check PASSED 数 | | |
| Stability check PARTIAL 数 | | |
| Stability check FAILED 数 | | |

## 6. Stability check 详情

引用 `paper_run_stability_checks` 表的 `stability_check_id`：

- ID：<填写>
- check_window_start / check_window_end：<填写> / <填写>
- 状态：PASSED / PARTIAL / FAILED
- uptime_ratio：<填写>
- summary_json 关键摘要：<填写>

## 7. E2E 验证结果

- `npm run test:e2e`：passed / skipped / failed 数：<填写>
- skipped 用例列表：<填写>
- failed 用例列表：<填写>

如果有 failed 必须停下记录原因，不允许把 failed 写成通过。

## 8. 验证基线结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | | |
| `mvn -f backend/pom.xml test` | | |
| `npm run build` | | |
| `npm run test:e2e` | | |
| `python -m pytest -q` | | |
| `python -m mypy src` | | |
| `python -m ruff check .` | | |

## 9. 失败 / 异常事件记录

- 任何 CRITICAL 告警未在窗口内 ack：<填写>
- 任何 stability check FAILED：<填写>
- 任何后端进程异常退出 / OOM / 数据库连接断开：<填写>
- 任何 LIVE / AI / 真实交易所调用痕迹：必须为「无」，否则验收失败

## 10. 验收判定

按 `GATEJ_WORK_ORDER.md` GateJ-FREEZE 验收标准：

- 1 小时短验收：在线率 100%、无 CRITICAL 告警、无 FAILED 调度触发。结果：PASSED / FAILED
- 24 小时中验收：在线率 ≥ 99%、失败触发 ≤ 2 次。结果：PASSED / FAILED
- 7 天稳定性验收：在线率 ≥ 99%、失败触发 ≤ 5 次、恢复成功率 ≥ 90%。结果：PASSED / FAILED

## 11. 结论

- 本次验收结论：PASSED / FAILED
- 失败时下一步动作：<填写，必须修复后重新验收，不允许把 FAILED 写成 PASSED>
- 通过时下一步动作：进入下一窗口验收 / GateJ freeze snapshot 归档

## 12. 签收

- 验收人签字：<填写>
- 审阅人签字：<填写>
- 提交日期：<YYYY-MM-DD>

## 13. 边界确认

本次验收不允许夹带：
- AI、AI 信号、AI 自动交易、AI Paper Trading
- 真实 LIVE 下单、真实交易所下单接口调用
- 新业务功能、新 API、新 migration、新前端页面
- 把 FAILED 写成 PASSED
- 在 GateJ 整体未通过验收前创建 `docs/gates/gate-j/`
