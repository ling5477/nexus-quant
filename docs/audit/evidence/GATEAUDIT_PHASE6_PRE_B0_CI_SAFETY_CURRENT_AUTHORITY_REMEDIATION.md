# Pre-B0 CI Safety and Current Authority Remediation

Task classification: HIGH_RISK / TARGETED_REMEDIATION / CI_GUARD + ARTIFACT_SECURITY + CURRENT_AUTHORITY

Task: `NQ-GATEAUDIT-PHASE6-PRE-B0-CI-SAFETY-AND-CURRENT-AUTHORITY-REMEDIATION`

Starting HEAD: `f3cc63b95d305fb227f6a91adf5f9ccb9350289e`

Branch: `audit/post-gatey-agent-baseline`。开始时本地 HEAD 与 origin 同名引用一致，tracked/untracked working changes=0，staged=0，diff-check PASS。本轮未 fetch、stage、commit 或 push；origin 校验是本地已保存引用的读取，不是新的远端 CI 验收。

Reproduction baseline: 用户认可的中立 directory-review/REPORT.md、inventory.csv、probes.json 和失败日志。原 F1 返回 `CONFIG_INVALID: missing project Skill`；原 F2 对 JSON 假凭据拒绝、对 properties 和 JAR properties 漏检。本轮复用该定位，没有重新执行全目录 audit。

## F1 — Java engineering validator

Root cause: 工程 validator 在已完成的 standards/platform/configuration 校验之外，额外要求退休 Skill 文件存在并含固定 Role type、responsibility、trigger 和英文语句，使真实 canonical repo 无法通过。

Remediation: 仅移除该 Skill 文件读取/措辞检查及其专属遍历路径。标准目录、平台事实与 POM/CI 比较、Huangshan mapping、架构 scope/source、baseline schema/configuration hash/projection hash、排序、reparse 拒绝、CI shadow consumer 和 V40 Git blob 检查全部保留。没有修改 scanner、工程标准、platform profile 或 baseline 文件，也没有复制 Skill inventory validator。

Positive proof: canonical 仓库直接执行 `verify-java-engineering-standard.ps1` 返回 PASS；无退休 Skill 的隔离 fixture 与变异回滚后的 fixture 均 PASS。保持 Java release=21、Boot=3.5.10、Framework=6.2.15；current configuration hash=`47f37f2dbdca21df5c5cfc7b6ba14bdf845d20109863906a188b4cc5dd4d34bc`。V40 blob=`63052fcd7473e1b6e8a8975c1be45679010b01bb`，raw SHA-256=`1c0e486db0f3db4cdf250cb99ab0ed1e289f42d1ed522981272ee8b4c4da25e3`，未改变。

Negative proof: 新增 `scripts/java-standard/tests/Test-JavaEngineeringValidation.ps1`，2 positive / 10 negative PASS。逐一验证平台 CI 版本不一致、preview、失效 architecture mode、缺失 architecture source、未知工程 rule、非法 rule disposition、错误 baseline schema、错误 configuration hash、标准内容漂移、错误 baseline projection hash。拒绝不仅检查 exit=2，还核对具体错误类别与原因。隔离 no-checkout worktree 使用真实 Git object，不改调用仓库，不运行 Maven。

Runtime scope: F1 的完整 consumer 与上述测试在现有 CI 使用的 PowerShell 7 下验证；未发现 F1 必须兼容 PS5.1 的当前合同要求。既有 V40 子校验使用 .NET `ProcessStartInfo.ArgumentList`；本轮没有扩展这项运行时兼容性，也不声称完整 F1 已在 PS5.1 通过。通用 canonical hash 与 CI consumer 既有回归均在 PS7 通过。

## F2 — Delivery artifact safety

Root cause: 文本后缀不含 properties，JAR/ZIP 分支只看 entry name 后直接跳过内容。

Remediation: 保留既有全部内容规则与 forbidden path/name 规则；加入 properties/conf/config/ini/cfg/csv/map/mf 文本类型，JAR/ZIP/WAR/EAR 中的受控文本同样扫描。识别大小写扩展名和文本 BOM；无 BOM 的文本严格按 UTF-8 解码，解码失败拒绝。归档通过内存流读取，不解压到文件系统；受限递归检查依赖 JAR。新增父目录 reparse 拒绝与归档逃逸路径拒绝，异常和匹配输出只含规则/位置，不输出匹配内容。

Archive bounds（调用者可收紧、不可提高硬上限）：

| 预算 | 默认硬上限 | 范围 |
| --- | --- | --- |
| MaxArchiveEntries | 50,000 | 一次 EvidenceRoot 扫描内所有归档与嵌套 entry 合计 |
| MaxEntryBytes | 64 MiB | 单 entry 声明大小；文本/嵌套内容读取另以实际字节计数限流 |
| MaxExpandedTextBytes | 128 MiB | 顶层文本与全部归档展开文本合计 |
| MaxNestedArchiveBytes | 512 MiB | 读取的嵌套归档字节合计 |
| MaxArchiveBytes | 256 MiB | 单个顶层归档文件 |
| MaxArchiveDepth | 3 | 顶层计 1；允许两级嵌套，超过即拒绝 |

单次正则匹配设 2 秒 timeout；损坏归档、资源超限、文本解码错误均 fail-closed。非受控文本/归档类型不作通用二进制内容识别；这仍是有限规则的制品检查，不声称能识别所有秘密编码或所有文件格式。

Positive/negative proof: 新增 `scripts/ci/tests/Test-DeliveryArtifactSafety.ps1` 并从 CI 已调用的 `Test-DeliveryEvidence.ps1` 接入。PowerShell 5.1 与 7 各 7 positive / 20 negative PASS：

- JSON、properties、JAR properties、大小写 ZIP、UTF-16 properties、嵌套 JAR 的相同假凭据均拒绝；断言异常与输出不包含哨兵。
- clean JSON/properties/JAR、嵌套 JAR、恰好三层归档、精确文本预算边界和空文本均通过。
- 文件/entry 禁止路径、entry traversal、无效文本/归档、entry count/size、展开文本合计、顶层归档大小、嵌套字节/深度/全局计数均拒绝；跨两个顶层 JAR 的文本预算不会重置，四层归档拒绝。
- 仅使用程序生成的假凭据形状，不读取真实 credential，不运行生产或真实 provider。

PS5.1 验证修正了 UTF-8 无 BOM 中文注释在本机系统代码页下误解析的问题，扫描脚本使用 UTF-8 BOM；同时显式加载 `System.IO.Compression`。测试 harness 已覆盖单字节与空数组写入，异常时释放流。Python 派生 WinPS 的模块搜索环境曾使既有 Get-FileHash 无法加载，最终 PS5.1 结果来自 PowerShell host 直接启动的独立进程；没有通过修改产品脚本绕过该环境失败。

## F7/F8/F9 — Current docs

F7 status/schema sync: STATUS 的 accepted batch 绑定 C2=`ACCEPTED / CI_GREEN / CLOSED`，implementation/acceptance head=`612c2f5887a2e6b3a8b3138d9ae9b193c20e298f`、CI=`34183851797`；本地该 commit 存在，CI 接受事实使用用户明确给定的身份，没有发起新的 C2 验收。当前 work batch 切换为 pre-B0 remediation，`IMPLEMENTED|PENDING_REVIEW / NONE / NOT_RUN`。README/ROADMAP/RUNBOOK 同步，DB_SCHEMA 增加 V43–V48 的真实 migration 入口与结构摘要；原有历史 V42/V47 和 Phase5B V46 接受事实不改写，不推断生产 schema。

Next-action mapping: `NQ-GATEAUDIT-PHASE6-PRE-B0-SAFETY-INDEPENDENT-REVIEW` 是唯一命中 REVIEW matcher 的机器动作，对应用户指定完整任务名 `NQ-GATEAUDIT-PHASE6-PRE-B0-CI-SAFETY-REMEDIATION-INDEPENDENT-REVIEW`。后者若直接置入机器字段会同时命中 CI/FIX/REVIEW；因此只在文档中明确别名映射，未改 matcher 或治理合同。

F8 retired instruction cleanup: 四个旧 current 文档简化为退役说明与当前 AGENTS/STATUS/policy 入口，不保留 router 重建规格、固定插件 recipe 或第二份 Skill inventory。GOVERNANCE_WORKFLOW/DOC_RULES 删除不存在的 bootstrap 字段要求，审计边界来自用户明确任务；未恢复旧 primary/supporting 强制流程。

F9 navigation cleanup: docs/gates/README.md 的 3 个 GateN 指针改到实际 archive；CI_BASELINE_INDEX.md 的 2 个 CI 指针改到实际 archive 并使用 Markdown 链接。冻结历史正文没有修改。

Stage-asset compatibility: 只删除已退役 router 文档对应的既有 exception，并按正式 `inspect()` 算法更新 Test-DeliveryEvidence.ps1 的既有 digest：`92486e59ddfae1afe3f29dc81afe9085a5f1d54b79934bf69d9789e1dddac7a0` → `b614b40f368e05bfd6017d03b9ab379611712bd148979b552d0a873b65ede19e`。ROADMAP 的正式归一化摘要未变，无需更新。未增加新 exception、历史兼容锁或 caller。

## Tests

| 验证 | 结果 |
| --- | --- |
| F1 canonical validator / 新增变异 suite | PS7 PASS；2 positive / 10 negative |
| F1 既有 JavaShadowCiContract / CanonicalConfigurationHash / V40MigrationGitBlobContract | PS7 PASS |
| F2 既有 Test-DeliveryEvidence（包含新边界 suite） | PS5.1 与 PS7 PASS；各 7 positive / 20 negative；原 provenance/readback/tamper/secret 回归保留 |
| Test-CanonicalDeliveryWorkflow.Tests | PS7 PASS；135/135 变异拒绝，既有 ContractOnly/假进程路径，不运行 Maven 或真实部署 |
| check-current-authority / test-current-authority-next-action | PS5.1 与 PS7 PASS；现有歧义/安全/schema/空白负向回归保留 |
| test-agent-workflow-fixtures | PS5.1 与 PS7 PASS；18 positive / 17 negative；filesystem=policy=4，legacy-active/duplicate-identities/unknown-targets=0；动态第 5 个 Skill fixture PASS |
| check-stage-assets | PASS；scanned=1797，reviewed_exceptions=173，errors=0；同步前确实拒绝失效 exception，未放松 checker |
| check-doc-links | 默认 roots：466 checked / 123 历史 warnings / 0 errors；本轮索引与退役入口额外 roots：20 checked / 0 warnings / 0 errors。未修改冻结历史正文来消除 warnings。 |

Full Maven=NOT_RUN；Playwright=NOT_RUN；C2 PostgreSQL=NOT_RUN；真实 restore/deployment/trading=NOT_RUN；remote exact-head CI=NOT_RUN。

原始命令输出、初次失败/修正摘要与候选清单保存在中立工作区 `pre-b0-remediation/`；本文件是本轮唯一 repository evidence。测试成功不是独立审查通过。

## Residual P2

| Finding | Status | 保留触发条件 |
| --- | --- | --- |
| F3 restore proof identity | OPEN / P2 / NON_BLOCKING_FOR_B0 | POST_RESTORE_VALIDATION 的错误 commit/proof schema/schema target 或相等空 canary 仍可能被接受。 |
| F4 SBOM array shape | OPEN / P2 / NON_BLOCKING_FOR_B0 | 单元素或空嵌套数组经规范化可能变成对象/null。 |
| F5 Java shadow committed-change classification | OPEN / P2 / NON_BLOCKING_FOR_B0 | 新增违规提交后，git status 不再提供新代码分类。 |
| F6 manual seed SQL scope | OPEN / P2 / NON_BLOCKING_FOR_B0 | 有非 fixture admin 账号的数据库手工执行 seed 时，默认账号 UPDATE 选择范围过宽；未执行 SQL。 |

四项实现文件均保持不变，不宣称 GateAUDIT 全部结束。

## Decision and delivery boundary

P0: 本轮本地自查未发现未解决 P0。

P1: F1/F2 已实现并通过本地回归；本轮范围内未发现未解决 P1。独立审查尚未进行，不能写成 REVIEW_ACCEPTED 或最终 CLOSED。

`.github` changes=0；backend production/frontend/Flyway/deployment production/C2 implementation changes=0；AGENTS 与 4 canonical Skills、canonical agent policy changes=0。交易/发布授权与 enforcement 未修改；仅实施明确授权的 artifact-content guard 加强及 Java consumer 兼容修复。

stage=NONE / commit=NONE / push=NONE。

Final decision: `IMPLEMENTED / PENDING_INDEPENDENT_REVIEW / F1_REMEDIATED / F2_REMEDIATED / CURRENT_AUTHORITY_ALIGNED`。

Next action: 用户指定的唯一一次 `NQ-GATEAUDIT-PHASE6-PRE-B0-CI-SAFETY-REMEDIATION-INDEPENDENT-REVIEW`。由未参与本候选实现的 reviewer 核对固定候选，确认 F1/F2 实质 guard 未削弱及 F7/F8/F9 未恢复旧 instruction authority；本实现对话不冒充独立 reviewer。审查 P0/P1=0 后按用户指令进行精确 delivery 与 exact-head CI，本轮停在未提交候选。

## Candidate files and integrity

Files changed: 20；除本轮 evidence 外的 19 个候选文件 raw-byte fingerprint=`4b8cd57cc60144e0811c8ade0569d01688ad22d690019cb2af08148db99da36f`。算法：按相对路径排序，将每项 `path + NUL + lowercase SHA256(file bytes) + LF` 拼接为 UTF-8 后作 SHA-256；排除本 evidence 避免自引用。完整清单及 evidence 独立哈希在中立工作区 candidate-manifest.json。

- `docs/DOC_RULES.md`
- `docs/audit/evidence/GATEAUDIT_PHASE6_PRE_B0_CI_SAFETY_CURRENT_AUTHORITY_REMEDIATION.md`
- `docs/baselines/CI_BASELINE_INDEX.md`
- `docs/current/CODEX_PROJECT_INSTRUCTIONS.md`
- `docs/current/DB_SCHEMA.md`
- `docs/current/GOVERNANCE_WORKFLOW.md`
- `docs/current/NQ_DH_CODEX_PLUGIN_WORKFLOW.md`
- `docs/current/NQ_DH_CODEX_TASK_TEMPLATES.md`
- `docs/current/NQ_DH_WORKFLOW_ROUTER_SKILL.md`
- `docs/current/README.md`
- `docs/current/ROADMAP.md`
- `docs/current/RUNBOOK.md`
- `docs/current/STATUS.md`
- `docs/gates/README.md`
- `scripts/ci/Test-DeliveryArtifactSafety.ps1`
- `scripts/ci/tests/Test-DeliveryArtifactSafety.ps1`
- `scripts/ci/tests/Test-DeliveryEvidence.ps1`
- `scripts/docs/stage-asset-exceptions.json`
- `scripts/java-standard/tests/Test-JavaEngineeringValidation.ps1`
- `scripts/java-standard/verify-java-engineering-standard.ps1`

结束检查：HEAD保持起始身份；staged=0；git diff --check PASS；全 tracked 文件与开始快照比较，所有 byte delta 均在上述 allowlist 内，禁止范围 byte delta=0。新增文件仅两个对应测试与本 evidence。所有测试 worktree 已注销；未创建新治理任务或执行独立审查。

## 用户追加：engineering discipline completeness（2026-09-08）

前述20文件清单与 fingerprint 是追加整改前的候选快照，保留原 F1/F2 验证事实；最终提交/审查不能继续只使用旧 fingerprint。新增 scope 是 references/按需入口、instruction consolidation evidence 与当前审查范围说明；详见 [discipline ownership matrix](../../current/evidence/instruction-system/NQ-CODEX-ENGINEERING-DISCIPLINE-COMPLETENESS.attempt-01.md)。

同一次 Independent Review 追加核对原12 Skills工程规则的九领域归属、关键交易/数据库/安全细则、四Skill topology/默认轻量上下文及 DELETE/KEEP/MOVE_TO_REFERENCE/CHECKER_ENFORCED 映射。F1/F2 实现、标准与 checkers 不修改；原 P2 residual 与未验证范围保留。当前状态：`IMPLEMENTED / PENDING_INDEPENDENT_REVIEW / F1_REMEDIATED / F2_REMEDIATED / ENGINEERING_DISCIPLINE_COMPLETENESS_REMEDIATED / CURRENT_AUTHORITY_ALIGNED`。
