# RC1_CHECKLIST

当前状态：**进行中**

## RC1-0 文档切换

- [x] `README.md` 已切换到 RC1 口径
- [x] `AGENTS.md` 已切换到 RC1 口径
- [x] `docs/current/*` 已切换到 RC1 入口
- [x] `docs/gates/gate-h/*` 已明确为暂停卷宗

## RC1-1 仓库清理

- [x] 构建产物已删除
- [x] 根目录敏感文件已移出仓库工作区
- [x] `.gitignore` 已收口为精确忽略规则
- [x] 弃用配置与无用脚本已完成首轮盘点

## RC1-2 表结构重构

- [x] `exchange_accounts` 已落地
- [x] `exchange_account_credentials` 已落地
- [x] 唯一约束与 active 凭证规则已落地
- [x] migration 专用用户回填已落地
- [ ] legacy 凭证导入工具已落地

## RC1-3 Java 模块与包结构收口

- [ ] `nq-core` 不再包含 JDBC 实现
- [x] `nq-api` 不再直接写 SQL
- [x] controller 不再直接依赖 scheduler 具体实现
- [~] `nq-app` 总装配已完成首轮拆分
- [~] 六个业务域包结构已启动建立

## RC1-4 前端基础重构

- [x] 账户上下文 store 已建立
- [x] header 上下文入口已建立
- [x] `/accounts` 页面骨架已建立
- [x] `trade-validation` 已切为上下文优先
- [~] 巨型页面拆分已启动

## RC1-5 marketdata 域与 Python 研究骨架

- [x] `marketdata` 正式域已建立
- [x] `marketdata_bars` 与最小 ingest/query 骨架已建立
- [x] `research/py` 已升级为正式子工程
- [x] `pytest / ruff / mypy` 骨架已建立

## RC1-6 清理残留与全量验证

- [ ] 历史残留实现已分类处理
- [ ] ArchUnit 约束已建立
- [ ] `mvn test` 通过
- [ ] 前端 build 通过
- [ ] E2E smoke 通过
- [ ] migration 验证通过
