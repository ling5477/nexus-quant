# Instruction consolidation · attempt-01

Task classification: INSTRUCTION_CONSOLIDATION / LOCAL_CODEX_CONFIG + NQ_REPO / BEHAVIORAL_VALIDATION

Final decision: IMPLEMENTATION_COMPLETE / SKILLS_12_TO_4 / NEW_ROUTING_SAMPLES_PASS / ACCEPTANCE_INCOMPLETE

完整 old/new 行为对照尚未完成，且现有 stage-assets 内容摘要存在不兼容，不能宣布 BEHAVIORAL_VALIDATION_PASS、最终接受或进入 B0。没有新建 instruction 治理任务。

## Scope exception

AUTHORIZED / INSTRUCTION_VALIDATOR_COMPATIBILITY_ONLY

Reason: 12→4 canonical Skill consolidation requires the existing CI-invoked instruction validator to stop encoding the retired 12-Skill/A-H topology.

.github workflow changes: NONE

security/release/trading enforcement changes: NONE

允许的三个配套资产：`scripts/docs/agent-workflow-policy.json`、`scripts/docs/agent-workflow-fixtures.json`、`scripts/docs/test-agent-workflow-fixtures.ps1`。

审计来源：`E:/Project/nexus-quant-instruction-audit-neutral/instruction-audit/REPORT.md`、`inventory.csv`、`proposed/`。未重新开展64项审计。`.agents/README.md` 按已接受 REPORT 的 SIMPLIFY disposition 改为 policy 指针，避免残留12项伪 active 清单。

## Baseline and inventory

C2 技术身份保持为 `612c2f5887a2e6b3a8b3138d9ae9b193c20e298f` / CI `34183851797` / C2 ACCEPTED。此次 GET 查询确认该 workflow_dispatch run success；没有重审或重跑 C2。

正式工作区 `E:/Project/nexus-quant-gateaudit`，分支 `audit/post-gatey-agent-baseline`，开始时本地、origin引用及远端分支均为上述 SHA，工作区干净。

旧 Global 在 `$HOME/.codex/AGENTS.md.20260908`；旧 NQ 根与12 Skills仍位于正式工作区的标准路径。`.agents_bak`、AGENTS.bak.md 和 CLAUDE.bak.md 实际位于中立工作区，不在正式工作区。

Global：0个启用入口→新 AGENTS.md；旧全局备份保留。NQ root：1→1；nested AGENTS：0→0；canonical Skills：12→4。未安装 alias/router/legacy wrapper。

最终 Skills：

- `nq-postgres-migration-review`
- `nq-trading-correctness-proof`
- `nq-frontend-state-design`
- `nq-research-reproducibility`

每个最终 Skill带1个按需 reference。普通任务允许零 Skill。新根文本基于已接受 proposed，只补本轮明确要求的技术栈、代码/测试/CI事实优先以及 Paper→Shadow→Limited Live验证顺序；未复制当前阶段状态。

移除16个旧 tracked Skill/支持文件，旧12个入口为：`db-schema-migration-review`、`frontend-antd-page-builder`、`frontend-product-ui-design`、`frontend-quality-regression`、`java-backend-maintenance`、`java-backend-regression-tests`、`nq-dh-workflow-router`、`nq-docs-writer`、`nq-java-engineering-standard`、`python-ops-tooling`、`python-project-development`、`ui-visual-system-polish`。

KEEP：模块、交易、迁移、凭证不变量及所有原有交易/发布/供应链 enforcement；历史指针继续保留。SIMPLIFY：root、active README、三个指令配套资产。SPLIT：Global稳定协作与项目约束。MERGE：Java证明、前端与research相关专业能力。DELETE：旧执行router和通用recipe入口。MOVE_TO_REFERENCE：数据库证明、交易证明、页面状态和research工程细节；未改动business docs。

## Size

可比口径为 Global受审备份 + NQ root + NQ SKILL根正文，不包含未修改的用户级 Playwright、平台/插件/记忆或按需references。

旧：14文件 / 51,328字符 / 85,449 bytes。

新：6文件 / 4,114字符 / 9,448 bytes。

Static reduction: 91.98%。TOKEN_REDUCTION = NOT_MEASURED。没有把静态字符减幅写成运行时 token减幅。

## Instruction validation

现有 test-agent-workflow-fixtures 在 PowerShell 5.1、7 均通过：18正向、17负向；filesystem=policy；legacy-active=0；duplicate-identities=0；unknown-targets=0。

数量从 policy读取，不内置4或12；无A–H章节要求，无必须先调用旧router的规则。fixture提供语义标签，验证器不冒充自然语言路由器，也不授权外部操作。迁移/交易能力自身携带风险标签，即使样本漏填额外riskTags，仍保持高风险证明要求。

负向验证覆盖未知/重复/legacy身份、目录不一致、description缺失、未知触发/风险、普通任务加载所有Skill、migration/credential/trading风险降级及必要PostgreSQL证明缺失。保留安全语义下限，不把全部负例删成空壳。

可丢弃真实目录实验：仅新增第5个Skill目录、policy数据与fixture，原校验器通过；删除声明目录→INVENTORY_MISMATCH；恢复legacy目录→LEGACY_ACTIVE_SKILL。没有为了5个能力再改PowerShell数量常量。

初次PS5.1暴露UTF-8无BOM注释解析及JSON数组枚举差异，已在允许的校验器内修复，最终两个host输出一致。没有新增instruction checker文件。

## Behavioral benchmark

方法：六个合成样本，各自全新ephemeral Codex exec；模型及reasoning沿用本机 `gpt-6-astra / medium`。使用 `E:/Project/nq-instruction-bench-new` disposable worktree，真实加载新Global、NQ root和4个Skills。提示相同的A–F任务，不指定Skill；统一只读实施前判定，观察实际读取与后续验证选择，不执行实现、测试/构建或commit。

因此本结果仅证明路由/验证选择，不证明模型完成了修复或运行了PostgreSQL测试。共同只读限制使“未创建额外任务”的观测较弱；未把它当作不受约束自主执行的完整证明。

| 样本 | 选择Skill | 实读Skill reference | 必读治理docs | 处理 | 独立review | Full Maven选择 | 无必要停止 |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A | NONE | 0 | 0 | PROCEED | NO | NO | 0 |
| B | NONE | 0 | 0 | PROCEED | NO | NO | 0 |
| C | NONE | 0 | 0 | PROCEED | NO | NO | 0 |
| D | nq-postgres-migration-review | 1 | 0 | PROCEED | YES | NO | 0 |
| E | nq-trading-correctness-proof | 1 | 0 | PROCEED | YES | NO | 0 |
| F | NONE | 0 | 0 | PROCEED | NO | NO | 0 |

A小Java bug、B前端文案、C docs链接、F普通service：最小局部修改/目标回归或直接检查。D migration：forward-only、既存数据兼容、DDL锁及隔离PostgreSQL。E交易P1：风险前置、稳定幂等身份、持久状态、并发与真实跨进程恢复，并保留独立review。

D/E 的 additional_governance_tasks 输出栏包含验证前提/验收review说明，正文明确不创建任务、不生成WORKLOG/TESTING；按语义判定不是新治理任务，未将数组长度直接等同task数。两例还轻量检索了现有MEMORY索引，未打开历史Gate文档；记忆来自环境层，并未在本轮修改。

旧侧：NOT_RUN_ISOLATED_AUTH_UNAVAILABLE。已准备 `E:/Project/nq-instruction-bench-old` 及旧根/12 Skills，独立CODEX_HOME含旧Global；但隔离CLI login status返回Not logged in。现有正式home可使用已配置登录。未提取/迁移keyring凭证，未为benchmark临时覆盖共享Global为旧规则；所有临时配置硬链接已移除，未改变原配置。

CLI启动排障曾遇到忽略用户配置导致401、带引号MCP override路径错误；调整为沿用现有配置并正确关闭不需要的MCP后，新侧六例均exit0。失败预检不算行为样本。

Old/New delta：Skill-routing、documentation-load、testing、blocking、review的前后差值均 NOT_MEASURED，不能用静态审计或新侧结果代替旧侧。

模型样本读取的Global/root/Skill/reference与最终文件一致；未读取policy。其后修复的仅是三个机器配套资产，由独立fixture检查覆盖，不把此前sample冒充新版policy执行测试。

## Integrity and delivery limits

按开始时所有tracked文件hash核对，除授权instruction及本evidence外其余源均未变；backend/frontend/research/Flyway/deployment/.github/交易发布安全检查diff=0。Global config hash保持不变。Full Maven、Playwright、GateAUDIT correctness suite：NOT_RUN。

现有 `.github/workflows/ci.yml` 的push/PR仅匹配dev，目标audit分支需workflow_dispatch；本轮不手动触发全量CI。push后是否有自动run在交付回执中按exact head核对；无run不写green。

独立的stage-assets内容锁仍保存以下已修改文件的旧摘要：`AGENTS.md`, `scripts/docs/agent-workflow-fixtures.json`, `scripts/docs/agent-workflow-policy.json`, `scripts/docs/test-agent-workflow-fixtures.ps1`。这是直接比较已锁定摘要与当前规范化内容得出的确定不兼容；没有运行或修改该安全检查，没有改其例外文件。未来canonical CI执行此检查时不能通过旧摘要。此项超出本轮仅三个配套资产的范围，因此未伪造green或绕过enforcement。

候选SHA-256：

- `C:\Users\Lingyu\.codex\AGENTS.md`: `b06be193e697e993a88c88cded6312faba94231e0813de9481de33a094d6e7b1`
- `AGENTS.md`: `e281423e14ae66a09877c7a12b930a8d01dd3473e0a4c9ceb295bcc26b9dd1d8`
- `.agents/skills/nq-frontend-state-design/SKILL.md`: `e2b24e61df711924e8ff801a57d32e094bd22a15bc8f4d6c328070ff734fc941`
- `.agents/skills/nq-postgres-migration-review/SKILL.md`: `e8cceec5c0c0194972cff5ce704eadd3037bba3faa1abe10b0b5acd340cf4f49`
- `.agents/skills/nq-research-reproducibility/SKILL.md`: `3b175b0811d8f016bfee016038f0a18a089a0f3f38f6ce8eb7a9a3c928aead9c`
- `.agents/skills/nq-trading-correctness-proof/SKILL.md`: `c8a2ae1abdbcedca229781ef3255d6e6fa6f875be6ed689136b0da139fef413f`

本地原始benchmark JSON/命令记录、fixture日志、hash核对和尺寸计算位于 `E:/Project/nexus-quant-instruction-audit-neutral/consolidation-work/`；不复制进canonical repo。证据随所在Git提交固定，commit/push回执由最终回复报告，避免为写入本文件自己的commit SHA再生成authority同步提交。

## Rollback and decision

Global回滚前确认新入口和旧备份存在，移走新AGENTS后复制旧备份为AGENTS.md；保留原备份，不机械覆盖。Project提交前可对精确instruction文件恢复HEAD并清理本轮新Skill；提交后由本证据所在consolidation commit执行git revert。正式仓库不保留bak或旧Skill复制备份。

实现与new-side路由样本完成；高风险不变量在文本、fixture及new-side选择中保留。完整旧新对照和stage-assets兼容性未闭合，最终接受尚未满足；不宣称治理主线已关闭，不推进RESUME_GATEAUDIT_PHASE6_L4_B0，也不新建后续instruction治理任务。
