# GateD WORK
# GateD 工作记录

## 1. 开工背景

当前仓库已经完成 GateC：多交易所接入、REST / WS 基座、reconcile / recovery、paper matching、ledger posting 基础存在。

GateD 开工的原因不是“继续接功能”，而是：
- 旧文档把 GateD 错定义成研究 / 回测，已与现状冲突
- `docs/current/*` 仍停留在 GateC
- `nq-core / nq-risk / nq-scheduler` 的执行域边界仍待收敛
- `docs/gates/gate-d/` 目录已存在但尚未正式立卷

## 2. 本轮文档目标
- 正式建立 GateD 文档目录
- 统一 README / AGENTS / current docs / roadmap 的阶段定义
- 给出模块改造说明与 checklist

## 3. 后续代码目标
- 收敛 nq-core 执行入口
- 建立 nq-risk 规则链
- 瘦身 nq-scheduler
- 冻结 nq-adapter-api 契约
- 补齐 GateD Flyway 迁移与验收入口

## 4. 遗留项
- 需要结合实际代码进一步补 ADR
- 需要在代码提交后回填验证证据
- 需要在具体 PR 中补外部官方接口依据

## 5. 下一步输入
- `docs/current/GATE_CHECKLIST.md`
- `docs/gates/gate-d/MODULES.md`
- `docs/gates/gate-d/CONTRACTS.md`
- 目标代码模块现状

