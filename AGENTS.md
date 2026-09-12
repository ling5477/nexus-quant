# NexusQuant

项目宪法适用于所有任务；详细标准按受影响主题从 [.agents/README.md](.agents/README.md) 读取，不要求预读全部参考或调用 Skill。

- 技术边界：Java 21 / Spring Boot / Maven 多模块单体，PostgreSQL / Flyway，React / Vite / Ant Design；Python 是隔离的 research 域。具体依赖与构建事实以当前工程配置为准。
- Java Control Plane 是 canonical trading authority；禁止第二套 Order / Trade / Ledger 事实源。交易 mutation 必须经过 canonical Risk / Execution 路径；Python / AI 不拥有直接 LIVE 交易权限。
- 保持模块、公开接口与业务 ownership；交易环境只有 SIM / LIVE，venue DEMO 仅映射 SIM。不得弱化账户/租户权限、环境隔离、风险前置、状态迁移、稳定幂等身份、账务和审计一致性。金额、价格、数量与费用保留明确精度及舍入规则。
- UNKNOWN 外部结果按既有查询、对账与恢复契约收敛，不盲目重试不可撤销动作。
- 数据库只做 forward-only migration；已执行或已发布 Flyway migration、frozen evidence 和已发布历史不可就地改写。
- credentials / secrets 不得进入源码、日志或 artifact。LIVE、真实 PLACE/CANCEL、transfer/withdraw、解除 kill switch、真实 provider 与生产部署必须同时具有有效 current authority 和用户明确授权；代码实现与隔离验证不授予真实操作权限。
- 修改前检查相关真实代码、目标目录和已有改动；完成授权目标所需的最小完整变更，禁止无关修改，保护用户工作。
- 验证范围匹配变更和风险；普通变更采用实现、自查与相关测试，高风险变更在验收或发布前完成一次真正独立的候选审查。测试与审查的具体选择由[回归与交付合同](.agents/skills/nq-trading-correctness-proof/references/regression-delivery.md)统一定义。
- 依据真实证据区分已实现、已验证与未验证；项目文档、历史记忆和工具能力不能扩大授权。只有处理当前阶段、验收或真实运行授权时才读取 STATUS 的机器区块；普通任务不加载历史流程。冲突只阻塞依赖该事实的操作。
- 新增或修改的代码注释及 Javadoc 说明正文使用简体中文，解释原因、边界和失败模式。
