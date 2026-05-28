# CLAUDE（Claude 开发指引 - NexusQuant）

> 目的：让 Claude / 开发者在本仓库内严格遵循当前阶段、模块边界、文档事实源、验证纪律和禁止范围。
> 当前事实源：`docs/current/`。

## 1. 当前阶段

Current stage: GateJ-FREEZE-FIX-SECOND-PASS completed, next redeploy GateJ-FREEZE-FIX release and run first-start acceptance

Previous completed stages:

- DOC-CLEAN
- BASELINE-FIX
- GateH
- GateI-PLAN
- GateI-1-WO
- GateI-2-WO
- GateI-3-WO
- GateI-3-FIX
- GateI-4-WO
- GateI-4-FIX
- GateI
- GateJ-PLAN
- GateJ-1-WO
- GateJ-2-WO
- GateJ-3-WO
- DOC-CLEAN-2
- PRE-FREEZE-CODE-AUDIT
- PRE-FREEZE-CODE-AUDIT-SECOND-PASS
- AUDIT-FIX
- GateJ-FREEZE-FIX
- GateJ-FREEZE-FIX-SECOND-PASS

Next allowed: redeploy GateJ-FREEZE-FIX release and run first-start acceptance。GateJ-FREEZE-FIX-SECOND-PASS 已完成并允许重新部署，详见 `docs/current/GATEJ_FREEZE_FIX_SECOND_PASS_REPORT.md`。GateJ 仍未 completed，必须在服务器首次启动验收后再进入 1h / 24h / 7d 连续运行验收。

GateJ-FREEZE 允许范围：
- 1h / 24h / 7d 连续运行验收。
- 验收记录、文档冻结、FREEZE_SUMMARY 编写。
- 同步 STATUS / WORKLOG / TESTING / ROADMAP / CLAUDE / AGENTS / README。
- GateJ 整体通过验收后才允许创建 `docs/gates/gate-j/` 并冻结。

GateJ-FREEZE 禁止范围：
- 不接 AI、不做 AI 信号 / AI 自动交易 / AI Paper Trading。
- 不做 GateK 任何实现。
- 不新增业务功能、API、migration。
- 不改前端页面功能。
- 不做真实 LIVE 下单、不调用真实交易所下单接口。
- 不把 GateJ 写成 completed，除非 GateJ-FREEZE 通过。
- 不创建 `docs/gates/gate-j/`，除非 GateJ completed。

GateJ 是 Paper Trading 稳定运行阶段，不是 AI 阶段。AI 最早 GateK 才允许进入信号层。

## 2. GateI 完成范围

GateI 已整体完成，覆盖以下内容：

- 策略版本与发布绑定（GateI-1）。
- 回测追溯与评估指标增强（GateI-2）。
- SIM/Paper Trading 运行闭环（GateI-3）。
- Paper Trading 风控回写、资金曲线、持仓曲线、交易复盘、异常停机（GateI-4）。
- 后端 35 tests / 0 failures。
- 前端 build 通过。
- E2E 19 passed / 1 skipped。

## 3. 严格禁止范围

- 不接 AI。
- 不新增 AI 模块。
- 不做 AI 信号。
- 不做 AI 自动交易。
- 不做 AI Paper Trading。
- 不做真实 LIVE 下单。
- 不调用真实交易所下单接口。
- 不做美股/A 股。
- 不做合约全量。
- 不做高频。
- 不做复杂因子平台。
- 不改交易核心状态机。
- 不改策略核心算法。
- 不改回测核心算法。
- 不绕过账户上下文。
- 不允许新增无注释表或无注释字段。
- 不修改历史 migration。
- 不把失败验证写成通过。

## 4. 文档规则

- `docs/current` 是当前事实源。
- `docs/gates` 只放已完成 Gate 的冻结卷宗。
- `docs/archive` 只归档，不作为当前开发依据。
- GateI 已完成并冻结，归档在 `docs/gates/gate-i`。
- GateH 已完成并冻结，归档在 `docs/gates/gate-h`。
- GateJ 尚未完成，不要创建 `docs/gates/gate-j`。
- 新 Gate 或新 WO 开始前必须先阅读 `docs/current` 对应计划文档。
- 每轮完成后必须按实际改动更新 `docs/current/STATUS.md`、`docs/current/WORKLOG.md`、`docs/current/TESTING.md`。
- 文档描述必须与代码和测试状态一致；未执行验证不能写成通过。

## 5. 数据库规则

- 本地 PostgreSQL 默认端口为 `5432`。
- 新增 Flyway migration 不允许修改历史 migration。
- 所有新增表必须有 `COMMENT ON TABLE`。
- 所有新增字段必须有 `COMMENT ON COLUMN`。
- JSONB 字段必须说明用途和边界，且不得保存密钥、token、cookie。
- 状态字段必须说明允许值。

## 6. 模块边界

- `nq-api` 不写 SQL。
- `nq-core` 不依赖 JDBC。
- `nq-infra` 承载 JDBC 实现。
- adapter 只做交易所适配，不直接写库。
- frontend 服务端数据使用 Axios + TanStack Query。
- Zustand 只放 auth/account-context 等全局状态。
- Python research 工具链不能被破坏。
- 正式 HTTP API 统一使用 `/api/**`。
- 交易所环境 canonical 口径固定为 `SIM / LIVE`；legacy `DOME / REAL` 只允许存在于导入映射层。

## 7. 每轮验证要求

后端：

```powershell
mvn -f backend/pom.xml test
```

前端：

```powershell
Set-Location frontend
npm run build
npm run test:e2e
```

Python：

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

如果只改文档，可以不跑全量测试，但必须在 `WORKLOG.md` / `TESTING.md` 中写清未跑原因。

## 8. 提交前检查

提交前必须至少执行：

```powershell
git status --short
```

检查项：

- 不提交 `tsbuildinfo`、生成产物、临时日志。
- 不提交本地密钥、`.env`、凭证。
- 不把 skipped / failed 写成 passed。
- 不把尚未完成阶段写成 completed。
- 不创建 `docs/gates/gate-j`，直到 GateJ 整体完成并冻结。

## 9. Claude 执行纪律

- 默认使用简体中文说明计划、过程和结论。
- 先读 `CLAUDE.md`、`README.md`、`docs/current/*`，再读目标代码或文档。
- 默认最小变更，避免无关重构。
- 不回退用户已有改动。
- 能用工具验证的结论必须用工具验证。
- 每次交付必须说明修改文件、验证结果、剩余风险和是否触达禁止范围。
