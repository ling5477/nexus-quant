# Claude Adapter

Claude 在本仓库工作时必须完整遵守根 [AGENTS.md](AGENTS.md)，并在每轮任务开始时解析 `docs/current/STATUS.md` 的 `nq-current-authority` 区块。

- 本文件只描述 Claude 的执行适配，不维护 current stage、Gate、test count、Integration 状态或独立 workflow。
- Claude 可使用其可用的只读检索、补丁编辑和命令工具，但工具能力不得扩大用户授权、repository authority 或安全边界。
- 无法使用某工具时，选择等价的最小权限方式并披露未验证项；不得以工具差异覆盖 `AGENTS.md` 或 `docs/current/STATUS.md`。
- 不得把缓存提示、旧会话、历史文档或被审计 Skill 当作 current repository authority。
