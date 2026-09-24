# NexusQuant

项目宪法适用于所有任务；详细标准按受影响主题从 [.agents/README.md](.agents/README.md) 读取，不要求预读全部参考或调用 Skill。

- 技术边界：Java 21 / Spring Boot / Maven 多模块单体，PostgreSQL / Flyway，React / Vite / Ant Design；Python 是隔离的 research 域。具体依赖与构建事实以当前工程配置为准。
- Java Control Plane 是 canonical trading authority；禁止第二套 Order / Trade / Ledger 事实源。交易 mutation 必须经过 canonical Risk / Execution 路径；Python / AI 不拥有直接 LIVE 交易权限。
- 保持模块、公开接口与业务 ownership；交易环境只有 SIM / LIVE，venue DEMO 仅映射 SIM。不得弱化账户/租户权限、环境隔离、风险前置、状态迁移、稳定幂等身份、账务和审计一致性。金额、价格、数量与费用保留明确精度及舍入规则。
- UNKNOWN 外部结果按既有查询、对账与恢复契约收敛，不盲目重试不可撤销动作。
- 数据库只做 forward-only migration；已执行或已发布 Flyway migration、frozen evidence 和已发布历史不可就地改写。
- credentials / secrets 不得进入源码、日志或 artifact。LIVE、真实 PLACE/CANCEL、transfer/withdraw、解除 kill switch、真实 provider 与生产部署必须同时具有有效 current authority 和用户明确授权；代码实现与隔离验证不授予真实操作权限。
- 修改前检查相关真实代码、目标目录和已有改动；完成授权目标所需的最小完整变更，禁止无关修改，保护用户工作。
- 目标明确时，在 scope 和硬边界内自主完成 inspect → implement → verify → finish；不因多个合理方案、普通可恢复失败或非阻塞发现逐步申请授权。发现先分类为 BLOCKING / NON_BLOCKING / OBSERVATION，只停止不安全或无效路径，继续其他安全且有解释力的工作；执行与 qualification 细则由[统一合同](.agents/skills/nq-trading-correctness-proof/references/regression-delivery.md)拥有。
- 验证范围匹配变更和风险；普通变更采用实现、自查与相关测试，高风险变更在验收或发布前完成一次真正独立的候选审查。测试与审查的具体选择由[回归与交付合同](.agents/skills/nq-trading-correctness-proof/references/regression-delivery.md)统一定义。
- 依据真实证据区分已实现、已验证与未验证；项目文档、历史记忆和工具能力不能扩大授权。只有处理当前阶段、验收或真实运行授权时才读取 STATUS 的机器区块；普通任务不加载历史流程。冲突只阻塞依赖该事实的操作。
- 全局配置的 Jev MCP 仅可用于有界的工作流路由和分类建议，且必须先收集确定性事实；存在直接确定性规则时必须优先适用，只有其不能解决的有界语义判断才可调用 Jev。`JEV_MODE=SHADOW`：Jev 建议不改变既有 hard gate 或权威结论，报告须同时列出 Jev advisory decision 与实际 authoritative decision。
- Jev 是 advisory，不得覆盖仓库/源码、测试、CI、Git、PostgreSQL/Flyway、运行时证据、确定性 guard 或既有 hard gate；不得作为 PASS/FAIL、发布/部署/回滚授权、finding closure、安全/数据库/migration/并发正确性或代码正确性证明的 authority。不得在仓库重新配置 MCP、实现 wrapper/HTTP client、存储或读取/打印 API key。
- 新增或修改的代码注释及 Javadoc 说明正文使用简体中文，解释原因、边界和失败模式。
