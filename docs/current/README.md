# Current Stage（当前阶段入口）

当前阶段：**RC1（项目收口重构批次）**

当前状态：**GateH 已暂停；当前主线为 RC1-0 ~ RC1-6；RC1 完成前不再推进 GateH 新功能。**

---

## 1. 当前阶段结论

- GateD 已冻结
- GateE 已冻结
- GateF 已完成并冻结
- GateG 已完成并冻结
- GateH 当前暂停
- current 目录当前承载 RC1 正式入口
- 当前主线只做结构收口、清理、重构与基础模型建设

---

## 2. RC1 目标

- 清理无用产物、敏感文件、弃用配置与历史残留实现
- 建立“用户 - 交易账户 - 凭证 - 环境（SIM/LIVE）”主模型
- 将交易所凭证从全局 env/yml 切换为数据库密文存储
- 收口 Java 模块边界与包结构
- 建立 `marketdata` 正式域与 Python 研究子工程骨架
- 建立前端账户上下文与账户/凭证管理入口
- 增加 ArchUnit 约束与全量验证护栏

---

## 3. RC1 非目标

- 不恢复 GateH 新功能开发
- 不做新交易所接入
- 不做复杂研究功能扩展
- 不做大规模 UI 美化
- compat drop 单独后续处理，RC1 只完成主读写切换与兼容层收口

---

## 4. 当前入口

- 当前阶段入口：`docs/current/README.md`
- RC1 主卷宗：`docs/current/REFACTOR_BATCH_RC1.md`
- RC1 checklist：`docs/current/RC1_CHECKLIST.md`
- 当前阶段模块边界：`docs/current/MODULES.md`
- 当前阶段模板：`docs/current/WORK_TEMPLATE.md`
- GateH 暂停卷宗：`docs/gates/gate-h/README.md`
- GateG 冻结卷宗：`docs/gates/gate-g/README.md`

---

## 5. 当前建议顺序

1. 先阅读 `docs/current/REFACTOR_BATCH_RC1.md`
2. 再阅读 `docs/current/RC1_CHECKLIST.md`
3. 再阅读 `docs/current/MODULES.md`
4. 如需核对暂停边界，再阅读 `docs/gates/gate-h/README.md`
