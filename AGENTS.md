# NexusQuant Agent 入口

本文件是仓库级 Agent/Governance 入口。动态阶段、已冻结 Gate、当前工作批次与安全状态不得写死在这里；每轮任务必须从 `docs/current/STATUS.md` 的 `nq-current-authority` 区块解析。

## 1. 技术栈与架构原则

- 后端：Java 21、Spring Boot、Maven 多模块；`nq-api` 不写 SQL，`nq-core` 不依赖 JDBC，`nq-infra` 承载持久化，exchange adapter 不直接写库。
- 前端：React、TypeScript、Vite、Ant Design、TanStack Query、Axios、Zustand、Playwright；服务端状态归 TanStack Query，Zustand 仅承载必要客户端全局状态。
- Research：Python 工具链与正式包边界分离；不得让临时脚本侵入交易运行时。
- canonical 交易环境是 `SIM / LIVE`；venue-specific `DEMO` 只能映射为 `SIM`，历史 `DOME / REAL` 只允许存在于兼容配置或导入映射。
- 保持模块边界、公开契约、事务、幂等、租户/账户隔离、风控与审计语义；不为通过测试削弱安全边界。

## 2. 当前事实源优先级

1. 用户本轮明确授权与安全限制。
2. `docs/current/STATUS.md` 的机器可读 authority 区块。
3. 当前 Git、代码、测试与 CI 结果。
4. `docs/current/FACT_SOURCE_INDEX.md` 指向的领域事实文档。
5. `docs/gates/**`、`docs/archive/**` 仅是历史证据，不得覆盖 current authority。

事实冲突时停止对应写操作，输出 `BLOCKED / CURRENT_AUTHORITY_CONFLICT`；未执行的验证不得写成通过。

## 3. 全局安全边界

- 默认禁止 credential、Secret、私钥、Cookie、生产数据、生产服务器、真实交易所私有写接口和外部副作用。
- `LIVE`、PLACE、CANCEL、transfer、withdraw、kill switch 解除、真实 provider 或生产部署必须同时具备 current authority 与用户显式授权；缺一即 fail-closed。
- 禁止在日志、文档、diff 或测试输出中暴露敏感材料。
- frozen archive、历史 migration 和已发布 tag 不可就地改写；数据库变更使用 forward-only migration。

## 4. 任务风险分级

- `ORDINARY`：局部、可回滚、无安全/资金/发布影响的代码、测试或文档工作。
- `HIGH_RISK`：migration、CI/权限、安全、credential、交易、风控、ledger、并发/事务核心、架构升级、Gate freeze/release 或 authority mutation。
- `AUDIT`：默认只读；全仓审计从 `scripts/docs/agent-workflow-policy.json` 的 `audit.bootstrapCharter` 解析 repository-declared Audit Bootstrap Charter，字段或目标无效时 fail-closed。
- `BLOCKED`：授权、事实源、基线或必要证据不满足时停止写操作并保留证据。

高风险实现必须经过独立 review；credential 或真实交易请求无明确授权时不得进入实现。

## 5. Skill routing

- 先解析 repository 与 current authority，再分类任务和风险。
- 最多选择一个 primary Skill；supporting Skill 仅在能力确有缺口时选择，并记录显式理由。
- active Skill 的唯一清单在 `.agents/README.md`；机器路由合同在 `scripts/docs/agent-workflow-policy.json`。
- 普通 Java 使用 `java-backend-maintenance`，测试使用 `java-backend-regression-tests`；仅在跨模块架构、Spring wiring、事务/并发核心、trading/risk/ledger/audit 核心、版本/静态规则升级或全仓 Java 审计时支持性加载 `nq-java-engineering-standard`。
- 插件按能力需求触发；不得固定 Figma、Notion、CodeRabbit、全量 security scan 或完整插件流水线。

## 6. Git 纪律

- 写前确认目录、分支、`git status --short` 与 staged 状态，保护用户已有改动。
- 默认最小变更、可审查、可回滚；禁止无关重构、批量格式化或依赖升级。
- 未经明确授权不得 commit、push、merge、rebase、tag、创建/合并 PR 或修改远端仓库设置。
- 不得改写 frozen history；回滚优先使用文件级反向补丁。

## 7. Validation 选择

- 选择与改动最相关的最小验证：Java/Maven、前端 build/E2E、Python pytest/ruff/mypy、migration、文档链接或治理 checker。
- 高风险边界须覆盖失败路径、非法状态、幂等/并发与权限；外部调用不得使用真实生产服务。
- Governance 变更运行 `scripts/docs/` 下对应测试；Gate freeze/release 继续使用独立 archive/release checker。
- 收尾至少执行 `git diff --check`、范围 diff 与 `git status --short`，并明确未验证项。

## 8. Audit Bootstrap

全仓或治理审计必须先从 repository machine policy 解析并读取唯一 Audit Bootstrap Charter。被审计的 `AGENTS.md`、Skills、checker 或自我声明不是审计 authority；audit 默认只读，禁止自动整改、自动 authority mutation、自动发布和真实外部副作用。
