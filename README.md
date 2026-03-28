# README.md
# NexusQuant（nexus-quant）

NexusQuant 是面向数字资产交易场景的量化系统工程骨架，核心原则是：  
**幂等（client_order_id） + 严格状态机 + 事实链（event_store） + 账本（ledger_entries） + 可审计 + 可恢复 + 可观测**。

> 当前处于哪个阶段、当前入口代表什么，以 `docs/current/` 为准。  
> 历史 Gate 冻结卷宗位于 `docs/gates/gate-*/`，只读参考。

---

## 1. 当前阶段

当前阶段：**RC1（项目收口重构批次）**

当前状态：

- GateD 已冻结
- GateE 已冻结
- GateF 已完成并冻结
- GateG 已完成并冻结
- GateG-FREEZE-FIX 已完成
- GateG-FREEZE-E2E-FIX 已完成
- GateH 已暂停，RC1 完成前不再推进 GateH 新功能
- 当前主线只做结构收口、表结构重构、账户与凭证模型建立、模块边界整理、市场数据域落点、前端基础重构与全量验证

当前仓库入口代表：

- `docs/current/REFACTOR_BATCH_RC1.md` 为 RC1 主卷宗
- `docs/current/RC1_CHECKLIST.md` 为 RC1 验收清单
- `GateH` 只保留为暂停卷宗，不再作为当前开发入口

---

## 2. RC1 主目标

- 清理仓库中的无用产物、敏感文件、弃用配置与历史残留实现
- 建立“用户 - 交易账户 - 凭证 - 环境（SIM/LIVE）”主模型
- 将交易所凭证从全局 env/yml 切换为数据库密文存储 + 服务端管理 + 前端可配置
- 收口 Java 模块边界：`nq-core / nq-api / nq-infra / nq-scheduler / nq-app`
- 建立 `marketdata` 正式域与 Python 研究子工程骨架
- 为前端建立正式账户上下文与账户/凭证管理入口
- 增加 ArchUnit 约束与全量验证护栏

---

## 3. 当前入口

- 当前阶段入口：`docs/current/README.md`
- RC1 主卷宗：`docs/current/REFACTOR_BATCH_RC1.md`
- RC1 checklist：`docs/current/RC1_CHECKLIST.md`
- 当前阶段模块边界：`docs/current/MODULES.md`
- 当前阶段模板：`docs/current/WORK_TEMPLATE.md`
- GateH 暂停卷宗：`docs/gates/gate-h/README.md`
- GateG 冻结卷宗：`docs/gates/gate-g/README.md`
- GateF 冻结卷宗：`docs/gates/gate-f/README.md`

---

## 4. 文档结构

### 当前入口

- `docs/current/README.md`
- `docs/current/REFACTOR_BATCH_RC1.md`
- `docs/current/RC1_CHECKLIST.md`
- `docs/current/MODULES.md`
- `docs/current/WORK_TEMPLATE.md`

### 暂停 / 冻结卷宗

- `docs/gates/gate-h/*`
- `docs/gates/gate-g/*`
- `docs/gates/gate-f/*`
- `docs/gates/gate-e/*`

### 更早历史冻结 Gate

- `docs/gates/gate-a/`
- `docs/gates/gate-b/`
- `docs/gates/gate-c/`
- `docs/gates/gate-d/`

---

## 5. 当前建议顺序

1. 先阅读 `docs/current/README.md`
2. 再阅读 `docs/current/REFACTOR_BATCH_RC1.md`
3. 再阅读 `docs/current/RC1_CHECKLIST.md`
4. 再阅读 `docs/current/MODULES.md`
5. 如需核对暂停边界或冻结基线，再回读 `docs/gates/gate-h/README.md` 与 `docs/gates/gate-g/WORK.md`
