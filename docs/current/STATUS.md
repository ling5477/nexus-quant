# Current Status

## 项目定位

NexusQuant 是通用量化交易平台，第一阶段聚焦虚拟币量化交易，后续复用账户、行情、策略、回测、评估、发布、风控、交易、复盘、AI 信号协议等底座扩展到美股和 A 股。

## 当前完成状态

- legacy console gate completed。
- RC1 completed and frozen。
- GateH-PRE completed。
- DOC-CLEAN completed。
- BASELINE-FIX completed。
- GateH-PLAN completed。
- GateH-1-WO completed。
- GateH-2-WO completed。
- GateH-3-WO completed。
- GateH completed。
- GateI-PLAN completed。
- GateI-1-WO completed。
- GateI-2-WO completed.
- GateI-3-WO completed。
- GateI-3-FIX completed。
- GateI-4-WO completed。
- GateI-4-FIX completed。
- GateI completed。
- GateJ-PLAN completed。
- GateJ-1-WO completed。
- GateJ-2-WO completed。
- GateJ-3-WO completed。
- DOC-CLEAN-2 completed。
- PRE-FREEZE-CODE-AUDIT completed。
- PRE-FREEZE-CODE-AUDIT second pass completed。
- AUDIT-FIX completed。
- GateJ-FREEZE-FIX completed。
- GateJ-FREEZE-FIX-SECOND-PASS completed。
- GateJ-FREEZE-FIX-3 completed。
- GateJ-FREEZE-FIX-4 completed。
- GateJ-FREEZE-FIX-5 local release reproducibility fix completed（ECS 复验待执行）。
- GateJ-FREEZE-FIX-6 local freeze sync guard and console text cleanup completed（ECS 复验待执行）。

## 当前执行状态

- Current stage: GateJ-FREEZE-FIX-6 local fix completed; next upload new release to ECS and verify Instrument Catalog sync no longer returns internal server error before GateJ-FREEZE first-start acceptance。
- GateJ-3-WO 已完成。
- PRE-FREEZE-CODE-AUDIT second pass 已完成：无 P0；Claude 第一轮 P1-1 / P1-2 验证缺口已由 Codex 实际重跑关闭；P1-3 不阻塞；P1-4 已闭环 GATEJ_FREEZE_ACCEPTANCE_TEMPLATE。详见 `PRE_FREEZE_AUDIT_REPORT.md` 与 `PRE_FREEZE_AUDIT_FIX_PLAN.md`。
- FULL_SECURITY_AUDIT 报告中的 P1 已由 AUDIT-FIX 关闭：旧 OKX dome 验收脚本已移出 `scripts/` 可执行区并归档到 `docs/archive/scripts/`，原路径只保留阻断 stub；`/__gated/**` 仍仅为历史路径。
- GateJ-FREEZE-FIX 已修复 ECS freeze 登录页敏感信息暴露与登录 401 根因：生产构建登录页不再展示 legacy console gate、本地端口、默认账号密码、认证 API 与 Authorization header 示例；freeze profile 不再执行 local 默认用户 seed；新增 `scripts/seed-freeze-user.sh` 通过服务器环境变量生成 BCrypt hash 并写入验收用户。
- GateJ-FREEZE-FIX-SECOND-PASS 已完成：`frontend/dist` 与新 release 解压内容未命中敏感登录页泄露串；freeze compose/template 使用 `NQ_PROFILE=freeze`；Git 未追踪 release/dist/env/jar/zip/dump/log/evidence；详见 `GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md`。
- GateJ-FREEZE-FIX-3 已完成：`scripts/seed-freeze-user.sh` 不再使用跨 statement 生命周期不稳定的临时表，改为单个 `psql` session/transaction 内 upsert freeze 用户、启用用户、重绑角色并校验 BCrypt；部署文档明确禁止手工 `source .env.freeze`，特殊字符密码建议通过 seed 脚本交互式隐藏输入。
- GateJ-FREEZE-FIX-4 已完成：修复 `seed-freeze-user.sh` 交互式隐藏输入路径，避免 `read` 后视觉换行进入命令替换返回值并被单行校验误判；ECS 仍需用真实 Bash/TTY 复验后才能继续 GateJ-FREEZE 首次启动验收。
- GateJ-FREEZE-FIX-5 本地修复已完成：新增 `.gitattributes` 强制 shell/yaml/PowerShell 换行策略，仓库 `scripts/*.sh` 已归一为 LF，`scripts/build-freeze-release.ps1` 在 zip 前对 staging `scripts/*.sh` 做 LF 兜底转换；新 release 本地解压检查确认 zip 内 `.sh` 不含 CRLF。ECS 仍需重新上传新 release 后直接执行 `bash -n`、`backup-db.sh`、`freeze-health-loop.sh` 与 `health-check-7d.log` 写入 `UP` 验证，未通过前不得进入 GateJ-FREEZE 首次启动验收。
- GateJ-FREEZE-FIX-6 本地修复已完成：freeze profile 默认禁用 Instrument Catalog 外部同步，`/api/instruments/sync` 在禁用或 Binance exchangeInfo 失败时返回 409 受控错误，不再进入 `api_unhandled_exception`；前端 Instrument Catalog 与 Header 已清理 `GateH-PRE` / `LOCAL` 可见残留，dist/release 扫描未命中禁止串。ECS 仍需重新上传新 release 后验证浏览器同步 Catalog 不再显示 internal server error，日志不再出现 `api_unhandled_exception path=/api/instruments/sync`。
- E2E/Vite 本地端口已从 `4173` 调整为 `5179`，避开 Windows TCP excluded range `4141-4240`；AUDIT-FIX 完整 E2E 已通过。
- 后端 `mvn -f backend/pom.xml test` BUILD SUCCESS（23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors）。
- 前端 `npm run build` 通过（仍有 Vite chunk > 500 kB P2 警告）。
- E2E `npm run test:e2e` 本轮实际执行通过：24 passed / 1 skipped / 0 failed；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路，GateJ 主链未 skip。
- Python `pytest / mypy / ruff` 本轮实际执行通过：pytest 2 passed，mypy 8 source files no issues，ruff all checks passed。
- Flyway 当前版本 V25（gate j3 paper run recovery stability）。
- Next: 重新部署 GateJ-FREEZE release 后执行首次启动验收，再进入 1h/24h/7d 连续运行验收 + 冻结。
- AI 尚未开始。AI 最早 GateK 才允许进入信号层。
- GateJ 不是 AI 阶段。GateJ 只做 Paper Trading 稳定运行。

## 当前未完成状态

- 尚未完成虚拟币量化 V1。
- 尚未完成 Paper Trading 稳定运行。
- 尚未进入 AI 自动交易。
- 尚未进入美股/A 股适配。

## 后续路线

```text
DOC-CLEAN / BASELINE-FIX
  ↓
GateH：交易工作台 + 历史行情 + dataset 绑定 completed
  ↓
GateI-PLAN
  ↓
GateI：虚拟币量化 V1 完整闭环
  ↓
GateJ：Paper Trading 稳定运行
  ↓
GateK：AI 信号接入
  ↓
GateL：AI Paper Trading
  ↓
GateM：AI 小资金 LIVE
  ↓
GateN：美股适配
  ↓
GateO：A 股适配
```

## 本地环境约定

- PostgreSQL 默认端口：`5432`。
- `local` profile 默认连接 `localhost:5432`。
- `docker-compose` 默认映射 `5432:5432`。

## 当前验证基线

- 后端 `mvn -f backend/pom.xml test` 已通过。
- 前端 `npm run build` 已通过。
- E2E `npm run test:e2e` 已通过，结果为 5 passed / 3 skipped。
- GateH-2 后 E2E `npm run test:e2e` 已通过，结果为 9 passed / 3 skipped。
- GateH-3 后 E2E `npm run test:e2e` 已通过，结果为 10 passed / 4 skipped。
- GateH-3 的 backtest dataset binding UI smoke 因当前本地库没有可绑定 backtest config 种子而 skip；后端 controller 测试覆盖绑定 API。
- GateI-1 后端 `mvn -f backend/pom.xml test` 已通过。
- GateI-1 前端 `npm run build` 已通过。
- GateI-1 E2E `npm run test:e2e` 已通过，结果为 13 passed / 3 skipped。
- GateI-2 后端 `mvn -f backend/pom.xml test` 已通过。
- GateI-2 前端 `npm run build` 已通过。
- GateI-2 后端 local profile 启动已通过，Flyway 当前版本为 `20`。
- GateI-2 E2E `npm run test:e2e` 已通过，结果为 17 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有订单详情链路，不影响 GateI-2 主链。
- GateI-3 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests，0 failures）。
- GateI-3 前端 `npm run build` 已通过。
- GateI-3 E2E `npm run test:e2e` 已通过，结果为 18 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateI-3 主链。
- GateI-3 Flyway 当前版本为 `21`。
- GateI-4 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests，0 failures，含 PaperTradingMonitorServiceTest 5 用例）。
- GateI-4 前端 `npm run build` 已通过。
- GateI-4 Flyway 当前版本为 `22`。
- GateI-4-FIX E2E `npm run test:e2e` 已通过，结果为 19 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路。
- Python `pytest`、`mypy`、`ruff` 已通过。
- GateJ-1 后端 `mvn -f backend/pom.xml test` 已通过（35 tests / 0 failures）。
- GateJ-1 前端 `npm run build` 已通过。
- GateJ-1 E2E `npm run test:e2e` 已通过，结果为 20 passed / 1 skipped。
- GateJ-1 Flyway 当前版本为 `23`。
- GateJ-2 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，35 tests / 0 failures，含 PaperRunMonitorServiceTest 12 用例）。
- GateJ-2 前端 `npm run build` 已通过。
- GateJ-2 E2E `npm run test:e2e` 已通过，结果为 22 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateJ-2 主链。
- GateJ-2 Flyway 当前版本为 `24`。
- GateJ-3 后端 `mvn -f backend/pom.xml test` 已通过（BUILD SUCCESS，含 PaperRunRecoveryServiceTest 9 用例 + PaperRunStabilityCheckServiceTest 10 用例 + PaperRunMonitorRunServiceTest 8 用例）。
- GateJ-3 前端 `npm run build` 已通过。
- GateJ-3 E2E `npm run test:e2e` 已通过，结果为 24 passed / 1 skipped；唯一 skipped 为未配置 `E2E_TRADE_ORDER_ID` 的既有交易订单详情链路，不影响 GateJ-3 主链。
- GateJ-3 Flyway 当前版本为 `25`。

## PRE-FREEZE-CODE-AUDIT 验证记录（2026-05-22）

- 后端 `mvn -f backend/pom.xml test`：通过（BUILD SUCCESS，0 failures、0 errors；archunit 模块边界与包边界全部通过）。
- 前端 `npm run build`：通过（Vite 通过，dist/index.js ≈ 1.48 MB，仍有 chunk > 500 kB 警告）。
- `npm run test:e2e`：本轮未实际重跑（沿用 GateJ-3-WO 24 passed / 1 skipped 基线）；P1-1 要求 GateJ-FREEZE 入场前补跑。
- Python `pytest / mypy / ruff`：本轮未实际重跑（当前 shell 仅 WindowsApps stub，无真实 Python 解释器；沿用 BASELINE-FIX-2 / GateJ-3 通过基线）；P1-2 要求 GateJ-FREEZE 入场前补跑。
- 详见 `PRE_FREEZE_AUDIT_REPORT.md` 与 `PRE_FREEZE_AUDIT_FIX_PLAN.md`。

## PRE-FREEZE-CODE-AUDIT second pass 验证记录（2026-05-22）

- 后端 `mvn -f backend/pom.xml test`：通过（Reactor BUILD SUCCESS；23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors）。
- 前端 `npm run build`：通过（Vite build 成功；仍有 chunk > 500 kB 警告）。
- E2E `npm run test:e2e`：通过（后端 local profile 启动成功，Flyway 当前版本 25；完整 Playwright 24 passed / 1 skipped / 0 failed；唯一 skipped 为 `E2E_TRADE_ORDER_ID` 未配置的既有订单详情链路）。
- Python `python -m pytest -q`：通过（2 passed）。
- Python `python -m mypy src`：通过（Success: no issues found in 8 source files）。
- Python `python -m ruff check .`：通过（All checks passed）。
- API / DB / Paper-LIVE 隔离 / AI 边界二次抽查未发现 P0/P1。
- 结论：允许进入 GateJ-FREEZE，但 GateJ-FREEZE 必须单独开工，只能做 1h / 24h / 7d 连续运行验收与冻结，不能夹带 AI 或新功能。

## AUDIT-FIX 验证记录（2026-05-26）

- P1 关闭：`scripts/gated_okx_dome_verify.ps1` 已变为安全阻断 stub，旧脚本归档到 `docs/archive/scripts/gated_okx_dome_verify.ps1`；当前可执行 API 不包含 `/__gated/**`。
- E2E 端口修复：`frontend/playwright.config.ts`、`frontend/tests/e2e/run-e2e.mjs`、`frontend/vite.config.ts`、`frontend/.env.example` 已统一从 `4173` 调整为 `5179`。
- 验证结果：`mvn -f backend/pom.xml test` 通过；`cd frontend && npm run build` 通过；启动后端 local profile 后 `cd frontend && npm run test:e2e` 通过，结果 24 passed / 1 skipped / 0 failed。
- 本轮不新增 API、不新增 migration、不修改交易下单/风控/撮合/恢复/调度核心逻辑、不接 AI。
- 验证结果详见 `AUDIT_FIX_REPORT.md`。

## GateI 当前边界

- GateI 已整体完成。
- GateI-1 实现策略版本与发布记录绑定。
- GateI-2 实现回测配置、评估指标、结果追溯增强。
- GateI-3 实现 SIM/Paper Trading 运行闭环最小版本。
- GateI-4 实现风控回写、资金曲线、持仓曲线、交易复盘、异常停机最小结构。
- AI、AI 信号、AI 自动交易、AI Paper Trading 仍未开始。
- GateJ-3-WO 已完成（异常恢复、失败重试、稳定性验收结构、HEARTBEAT_LAG/SCHEDULE_FIRE_FAILED 自动告警最小落库）。
- DOC-CLEAN-2 已完成（删除 docs/current/ 中 GateH/GateI 计划副本）。
- PRE-FREEZE-CODE-AUDIT second pass 已完成（无 P0；E2E 与 Python 基线均已实际重跑通过，详见 PRE_FREEZE_AUDIT_REPORT.md）。
- Next: GateJ-FREEZE（1h/24h/7d 连续运行验收 + 冻结）；AI 最早 GateK 才允许进入信号层。
