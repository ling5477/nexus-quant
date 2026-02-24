# 文档目录说明

本仓库文档按 Gate 分层管理：

- `docs/current/`：当前阶段（活文档入口）。Codex 启动时默认读取这里的清单与模板。
- `docs/gates/gate-a/`：Gate A 已完成后的冻结快照（只读）。
- 未来 Gate：`docs/gates/gate-b/`、`docs/gates/gate-c/` … 以此类推。

## 使用约定

1. 开发期间仅维护 `docs/current/` 入口文件。
2. 每个 Gate 完成后：将当期关键文档与 WORK 记录复制到 `docs/gates/gate-<x>/`，并在 GitHub 上通过 CODEOWNERS/分支保护冻结。
