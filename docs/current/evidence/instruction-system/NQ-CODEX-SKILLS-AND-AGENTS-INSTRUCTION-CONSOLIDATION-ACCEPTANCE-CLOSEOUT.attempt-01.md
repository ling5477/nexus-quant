# Instruction consolidation acceptance closeout · attempt-01

Task classification: TARGETED_ACCEPTANCE_REMEDIATION / INSTRUCTION_ASSET_COMPATIBILITY / NO_NEW_GOVERNANCE

Decision: PASS / INSTRUCTION_CONSOLIDATION_ACCEPTED / SKILLS_12_TO_4 / CANONICAL_POLICY_ALIGNED / STAGE_ASSETS_ALIGNED / HIGH_RISK_INVARIANTS_PRESERVED / P0_0 / P1_0

本记录关闭 [consolidation attempt-01](NQ-CODEX-SKILLS-AND-AGENTS-INSTRUCTION-CONSOLIDATION.attempt-01.md) 的 acceptance residual，保留原始未完成验收记录，不重写历史。按本轮用户授权，旧 loaded-session 对照缺失为非阻断 P2；不再要求补齐旧登录环境。

## Baseline and exact scope

开始时工作区干净；分支 `audit/post-gatey-agent-baseline`；HEAD、origin 引用和远端分支均为 `9e19f8545befe078b2b9aae3418795ddce32cc78`。本轮不重开 C2；其已接受技术身份 `612c2f5887a2e6b3a8b3138d9ae9b193c20e298f` / CI `34183851797` 不变。

实际修改只有 `scripts/docs/stage-asset-exceptions.json` 和本 evidence。用户所指四个 stage-assets 旧锁，经实际失败输出定位为同一 exceptions 数组内的四条记录，不是四个需修改的独立文件：

| 锁引用的资产（本轮均未改动） | 旧 sha256 |
| --- | --- |
| `AGENTS.md` | `1c5f390975d627ec5cd42abd261d87e2f7dd0dab0b0424ff0cf6ed36f8c2bf94` |
| `scripts/docs/agent-workflow-policy.json` | `db2f3678377005a234e0ae3d4040cad65e805376265cc0248c53e50dd7ca3d48` |
| `scripts/docs/agent-workflow-fixtures.json` | `d8d0fd75d374525cca6f26b48067ad93e6cf07a2ed8c1a12a23c7e05a5f5e01e` |
| `scripts/docs/test-agent-workflow-fixtures.ps1` | `2b8804972f6af13a19b22a0118d67b2de8164882888c8e5946e2a4ba4929a9d0` |

Scope exception: AUTHORIZED / INSTRUCTION_ASSET_COMPATIBILITY_ONLY。仅移除上述四项过期 instruction 例外，未更改 stage checker、安全策略执行逻辑、其他174项例外、retiredPaths、compatibilityContracts 或 safeControlPlaneInputs。没有更改 `.github`、workflow topology、required jobs 或交易/发布/安全 enforcement。

## Actual failure and derived semantics

整改前执行现有 `python scripts/docs/check-stage-assets.py`，exit=1：

```text
STALE_EXCEPTION: AGENTS.md
STALE_EXCEPTION: scripts/docs/agent-workflow-fixtures.json
STALE_EXCEPTION: scripts/docs/agent-workflow-policy.json
STALE_EXCEPTION: scripts/docs/test-agent-workflow-fixtures.ps1
STAGE_ASSET_CHECK scanned=1795 reviewed_exceptions=174 errors=4
```

现有 checker 的 `inspect()` 对四个当前文件均返回 None：收敛已移除该检查器识别的 stage 语义，旧的全内容摘要例外因此不再被消费。先前 evidence 所述“摘要不兼容”在本轮被实际输出精确归因为四项 STALE_EXCEPTION，没有 STAGE_SEMANTICS。单纯刷新四个 hash 仍会留下 stale 例外。

整改删除这四条已经无适用对象的例外，不设置新摘要，不新增按数量或 Skill 名称的豁免，不更改校验逻辑。canonical policy 继续由现有 instruction validator 读取，派生 expected inventory 并校验 filesystem、trigger/routing fixtures；现有 stage checker 则独立扫描这些相同的 active 文件。两者在原 CI 中已有各自的调用入口，没有新增生成器、summary 镜像或路由器，也不声称 stage checker 自身新增了 policy 解析功能。

未来合法能力扩展只需同步 policy、Skill 目录和对应 fixture 数据。它不再因旧 instruction 内容锁或固定数量而失败；若引入真正的历史 stage 命令，仍由原 stage 检查拒绝。

## Validation

| 验证 | 结果 |
| --- | --- |
| PowerShell 5.1 instruction validator | PASS：18 positive + 17 negative |
| PowerShell 7 instruction validator | PASS：18 positive + 17 negative，summary 与 PS5.1 一致 |
| 正式工作区 stage-assets | PASS：scanned=1795 / reviewed_exceptions=174 / errors=0 |
| filesystem == canonical policy | PASS，当前派生数量为4 |
| legacy active / unknown target / duplicate identity / filesystem mismatch | 0 / 0 / 0 / 0 |
| fixed Skill-count assumption / retired A-H topology dependency | 0 / 0；现有 validator 无固定 inventory 数量或旧分类要求，且真实扩展实验证明 |
| Global / NQ AGENTS / Skills / triggers / policy / fixtures / instruction validator | SHA-256 与本轮基线一致 |
| 其他所有既有 tracked 文件 | SHA-256 不变；唯一例外为上述 stage-asset-exceptions.json |

保留17个负向 fixture，包括未知/legacy/重复 identity、目录不一致、普通任务加载无关能力、migration/credential/trading 降级及 PostgreSQL proof 缺失。高风险语义未因收敛放松。

真实第5个 Skill 实验在可丢弃 detached worktree 完成：仅加入 `extension-proof` 目录、policy 项及 fixture 数据，使用正式候选的两个原样校验器与已整改例外数据。

- PS5.1 / PS7 均 PASS：canonical=5 / filesystem=5 / fixtures=19 / negative=17；校验器自身通用扩展 probe 进一步得到6。
- 完整 stage checker PASS：scanned=1796 / reviewed_exceptions=174 / errors=0；没有修改任何校验器数量或路由逻辑。
- 在新增 Skill 中注入 `powershell scripts/gatex-runtime.ps1`，原 checker exit=1，拒绝 `STAGE_SEMANTICS: .agents/skills/extension-proof/SKILL.md`。
- 删除 policy 声明的实际 Skill 目录，instruction validator exit=1，拒绝 `INVENTORY_MISMATCH`。
- 临时 Skill 已删除，临时 worktree 已移除；正式 topology 未改变。

环境排障记录：初次临时目录过长导致 Windows checkout 失败，改用短路径；checkout 还转换了 CODEOWNERS 的换行，触发既有 raw-byte safe-input 锁。核对后仅在临时环境复制 canonical 已锁定原始字节，并对两个 checker 同样保持精确字节，最终实验通过；未刷新该锁，未改正式 `.github`，失败输出保留。不是 canonical policy inconsistency。

Full Maven / Playwright / C2 correctness tests：NOT_RUN。未新建检查器或 benchmark framework。

## Behavioral residual and smoke

P0=0；P1=0；P2=1（非阻断）；P3=0。本轮 classification：P2 / OLD_LOADED_BEHAVIOR_BASELINE_NOT_RUN / ENVIRONMENT_BLOCKED。

旧 loaded-session benchmark 仍为 NOT_RUN / LOGIN_UNAVAILABLE；不修改 authentication、Codex config，不恢复旧体系，不以其阻塞本轮 structural acceptance。Behavioral comparison: PARTIAL / OLD_BASELINE_NOT_RUN。

NEW_BEHAVIOR_SMOKE: REUSED / UNCHANGED_INSTRUCTION_EVIDENCE；本轮没有启动新 loaded sessions。复用前次六个全新 session 的只读实施前选择样本，核对 current Global hash 与当时记录一致，并确认当前 NQ root、四个 Skills 与四个 references 和原实验环境逐字节一致。它们证明读取、路由与拟选验证，不冒充实际实现或 PostgreSQL 测试。

普通 Java / frontend / docs / service 样本均选择零 Skill，无无原因 BLOCKED、Full Maven 或 independent review；migration / trading 样本各读取一个专业 Skill 和一个 reference，保留 PostgreSQL / migration 或交易恢复证明及独立 review。样本原始结果在前次 evidence 中保留。

Comparable static instruction text reduction = 91.98%（51,328→4,114字符）；runtime token reduction 与执行时间收益均 NOT_MEASURED。

## Delivery and completion

backend/frontend/research/Flyway/.github/deployment/交易发布安全 enforcement changes=0；C2 untouched；没有备份、实验或其他意外文件纳入提交。精确提交集合为 stage-asset-exceptions.json 与本 evidence。

原始 before/after 输出、四项旧记录、dynamic proof、全文件 hash 核对位于中立工作区 `E:/Project/nexus-quant-instruction-audit-neutral/closeout-work/`。本 evidence 随交付提交固定；commit/push/remote readback 与 exact-head 自动 CI 观测由交付回执报告，不为写入自身 SHA 再提交同步文档。

本分支不匹配现有 CI 的 dev push/PR trigger，不手动触发全量 CI；没有新 exact-head run 时不声明 CI green，也不借用 C2 run。此次接受依据为用户定义的 targeted structural acceptance。

Rollback：需要时对本 closeout commit 执行 git revert；这会恢复四条旧例外及其已证明的 stale failure，不回退已接受 instruction topology。

Final decision: INSTRUCTION_CONSOLIDATION_ACCEPTED。本治理主线关闭，不新增 instruction-system 治理任务。

Next action: RESUME_GATEAUDIT_PHASE6_L4_B0。本轮只确认下一动作，不执行 B0 或修改 current business authority 文档。
