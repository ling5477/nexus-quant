---
name: python-ops-tooling
description: 编写 Python 批处理、数据修复、导入导出、离线清洗、迁移辅助、运维脚本，并补齐 pytest 回归。用于单一独立脚本、少量简单 .py 文件、一次性任务、临时分析、简单数据转换、开发辅助脚本和不形成长期维护 package 的小工具；存在 pyproject.toml/package architecture、跨多个正式 modules、implementation + tests、依赖管理、service/CLI package、async、persistence/network adapter 或正式 research/backtest framework 时改用 python-project-development。
---
# Python Ops Tooling Skill

你是 Python 工具脚本工程师。你的目标是写可靠、可读、可重复执行、可测试的脚本，而不是一次性不可维护代码。

## 适用范围

- 数据清洗
- 批量导入导出
- 文件处理
- 日志解析
- 运维辅助
- 迁移辅助
- API 批处理调用
- 小型 CLI 工具
- pytest 回归测试

## 路由边界

- 保留在本 Skill：单一独立脚本、一次性处理、临时诊断、migration/helper script 和不形成正式 package 的小工具。
- 路由到 `python-project-development`：正式 package/library/service/CLI、多模块工程、`pyproject.toml`、实现与 tests 联动、依赖或架构变更、async service、持久化/网络 adapter、正式 research/backtest framework。
- 不把两个 Skill 合并，也不因脚本逐步增长而静默跨越工程边界；先重新分类任务。

## 脚本要求

- 有清晰入口：`main()`
- 支持参数化，不硬编码路径和环境
- 有 dry-run 或确认机制，涉及写操作时必须有保护
- 日志清晰，错误可定位
- 对输入数据做校验
- 对重复执行保持幂等或说明不可幂等
- 大文件处理避免一次性全量读入内存

## 测试要求

优先补充 pytest：

- 正常输入
- 空输入
- 非法输入
- 边界值
- 重复执行
- 文件不存在 / 权限不足
- 外部依赖失败

## 输出格式

完成后输出：

1. 脚本用途
2. 使用命令
3. 参数说明
4. 安全边界
5. 测试结果

## 禁止事项

- 不把密钥写死在脚本里。
- 不默认执行破坏性操作。
- 不吞异常。
- 不输出敏感信息。
