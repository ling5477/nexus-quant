# NexusQuant

这是长期项目约束。项目是 Java 21 / Spring Boot / Maven 多模块单体，使用 PostgreSQL / Flyway；前端为 React / Vite / Ant Design。具体依赖版本以工程配置为准。真实代码、测试和 CI 证据优先于文档能力声明；文档不能扩大运行授权。当前阶段、验收身份与已授权发布操作以 `docs/current/STATUS.md` 的机器区块为准；处理这些事项时才读取该区块和必要的合同。普通局部修改从任务与目标代码开始，不预读阶段、历史 Gate 或证据账本。`ROADMAP.md` 用于用户要求的工作流/下一步决策；领域文档按问题查找。

- 后端保持现有模块职责：`nq-api` 不写 SQL，`nq-core` 不依赖 JDBC/infra，持久化位于 `nq-infra`，exchange adapter 不直接写库。版本与构建入口从当前工程配置确认。
- 前端沿用现有 React/TypeScript/Ant Design 结构；服务端状态由 TanStack Query 管理，Zustand 仅存必要客户端状态。不为小任务替换框架。
- canonical 交易环境为 `SIM / LIVE`；venue `DEMO` 仅映射到 `SIM`，历史 `DOME / REAL` 仅用于兼容边界。不得弱化环境隔离、账户/租户权限、风控、状态机、幂等、账务与审计语义。
- LIVE、真实 PLACE/CANCEL、transfer/withdraw、解除 kill switch、真实 provider 和生产部署，必须同时有有效 current authority 与用户明确授权。仅实现并在隔离环境验证代码不等于获得执行真实操作的权限。
- 交易能力按 Paper → Shadow → Limited Live 的受控验证顺序推进；这不是当前阶段声明，也不自动授予 LIVE 权限。
- 数据库采用 forward-only migration；不就地修改已执行/发布的 migration、frozen evidence 或已发布历史。Research 工具与交易运行时保持边界；新代码注释和 Javadoc 的说明性正文使用简体中文。

普通实现可在授权范围内完成代码、必要测试与自查；不强制 Skill 或独立 review。改变交易/资金正确性、migration、权限/安全控制、关键并发事务、跨模块架构或发布授权语义时，保留风险证明，并在验收或发布前完成真正独立的候选审查。纯注释、局部文案或无语义的 CI 排版不因目录名称自动升级；不把实现者换一个 Skill 当作独立 reviewer。用户明确要求的 review-only / no-modification 边界必须遵守。

选择能证明变更的最小验证：局部目标测试；跨模块验证受影响模块；SQL/迁移/交易持久化行为使用隔离 PostgreSQL 与相关回归；用户要求的 release acceptance 使用 canonical exact-head CI。独立审查先核对候选与证据身份，再决定需独立复现的关键场景；不机械重跑所有 suite。

更新行为所必需的 API/配置/使用文档；不默认产生 WORKLOG、TESTING 或计划链。阶段验收和发布任务按实际合同记录证据；历史证据不可改写，也不作为普通开发的前置规则。

Skill 只在其精确能力有帮助时使用，也允许不使用。详细知识位于相关 references，按具体问题读取。指令系统自身审计以明确的中立任务约束为依据；仓库自述不是审计授权来源。

事实冲突只停止依赖冲突的操作。先查找直接证据，授权范围内可以修复本轮错误；需要新增关键事实或授权时再提问，并继续无依赖的工作。未运行的验证不得记为通过。
