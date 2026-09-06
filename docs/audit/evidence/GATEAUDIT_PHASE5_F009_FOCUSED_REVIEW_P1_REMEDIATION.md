# F009 focused review 三项 P1 remediation

状态：`IMPLEMENTED / P1_REMEDIATED / PENDING_CLOSURE_REVIEW`（已整改，待独立 delta closure review）。本记录是 implementation delta 证据，不是独立审查、exact-head CI 或 acceptance。

## 1. 授权、基线与范围

- 任务：`NQ-GATEAUDIT-PHASE5-F009-FOCUSED-REVIEW-P1-REMEDIATION`；`NQ_ONLY / HIGH_RISK / NO_COMMIT / NO_PUSH`。
- branch：`audit/post-gatey-agent-baseline`。
- HEAD 与 `git fetch origin --prune` 后的 origin：`21d14d9112364f3befcc77a1a370828ef22e72ed`。
- Reviewed candidate fingerprint：`53f93002653c0110403d2cc2e7451aae1c8e952b6aeaff49850aea03433a58d4`；按原 reviewer 算法现场复算一致。Preflight staged=0，diff check PASS。
- 原 focused review：`FAIL / P0_0 / P1_3 / NOT_READY_TO_COMMIT`。本轮仅修复 P1-1、P1-2、P1-3，不重写原 review 或 implementation evidence。
- 65 DELETE、24 migration、29 caller adjustment、canonical release/install/activation、rollback/recovery、systemd unit、Phase5A/B、F007/F008 均保持进入本轮时的 candidate 内容。

## 2. P1-1：活动 Spring selector 的业务语义替换

Root cause：`OkxHistoricalKlineAdapter` 上的 `@Profile("!gatew")` 是 Bean 注册条件，原 `HISTORICAL_METADATA` exception 错把它当作诊断文字。

真实 composition：`AdapterHistoricalKlineProvider` 消费 `List<HistoricalKlineAdapter>` 并按 exchange code 路由。OKX adapter 只负责 SPOT historical candles；`PublicMarketDataOutboundConfiguration` 已使用 `public-marketdata-manual` 与 `nq.public-marketdata.outbound.enabled=true` 共同表达手动公开行情外联能力。

最小变更：adapter 复用同一 existing profile/property 双重条件，flag 缺失不匹配。默认、local、test、ci、paper、prod、scoped private-readonly 均不会仅因组件扫描注册该 adapter；manual profile 仍需显式 true。没有新增 Gate mode、alias 或 feature flag；公开协议适配、请求方法、数据库与交易语义未修改。

验证：`OkxSpotEndpointGuardTest` 中用 8 个稳定 profile × missing/false/invalid/true 共 32 次真实 Spring context refresh 断言 Bean 数量，只有 manual+true 注册一个 adapter。没有注入 `gatew`。正向测试保留真实 component/condition discovery，在 bean factory postprocessor 中只替换构造 supplier，显式注入 loopback transport fixture；不调用环境解析器，不发送请求，finally 关闭 fixture HttpClient。受影响 app composition tests 同时通过。

通用 checker 变更：活动 Java `@Profile` 中的阶段 selector 独立分类为 `ACTIVE_SPRING_STAGE_SELECTOR`，包括 fully-qualified annotation 与 Java Unicode escape；任何 exception/hash 都不能放行。Adapter 剩余旧诊断消息仍可作为历史 provenance，更新后的 metadata 理由只指这些消息。原 wiring test 已无 stage 语义，删除其 stale exception。

结果：active Spring stage selector=0；该 adapter 的 metadata 误分类=0。Inventory 中该 adapter 的 disposition 仍为既有 `KEEP_CANONICAL`，reason 记录真实 capability，未创造 disposition taxonomy。

## 3. P1-2：兼容成员与 approved caller 授权

Root cause：旧 checker 在 caller 没有 stage literal 时返回；合同文件 hash 仅验证 integrity，没有授权 source-reference edge。

新增通用控制：

- `stage-asset-exceptions.json` schema 2 中，18 个 `compatibilityContracts` 与 18 个 `WIRE_COMPATIBILITY` exception 双向绑定。每个合同包含 source/FQ type identity、canonical owner、声明成员、approved caller path/member、migration trigger、removal condition。
- 当前 105 个声明成员覆盖保留类型、嵌套类型、公开 static final 字段和 enum constants。Checker 从源声明核对 member coverage，不能删掉 member 清单以绕过授权。
- `JavaCompatibilityReferences.java` 用 JDK 21 compiler tree API 解析真实 Java source，去除 comment/string 非引用，绑定 imports、类型/成员选择与有合同类型的 field/parameter/local/getter 使用到 enclosing declaration signature。检查所有 active Java source，不以 stage literal 预筛 caller。
- 授权是精确 `(contract identity, source path, caller member, referenced member)` 集合。新文件、已获准文件中的新方法、static import、嵌套类型 import、继承与 typed-variable 使用均进入关系检查。未获准关系 REJECT；stale approved edge 也 REJECT。
- 同一方法已有 type grant 不能放行新的 fully-qualified member use。对无法唯一消歧的 simple name/typed use 保守取并集，可能要求审查额外边，不会因此自动授予权限。它是 source-reference authorization，不声称执行完整运行时反射或动态代码分析。
- Parser error、缺失 JDK、非法 schema/identity/member、wildcard caller、缺少 owner、缺少合同均 fail-closed。没有 regex/hash fallback，也没有运行时自动学习或 accept/update 开关。

重新计算：18/18 contracts；105 declared members；1,559 approved member edges；外部 type/file source relationships=110；合同内部 type/file relationships=13。原 111 条中，`PublicMarketDataOutboundClient` 对 policy 的一条引用仅为 Javadoc，AST 正确排除；其余 110 条真实外部关系均存在。类型文件关系与细化成员边采用不同口径，不混报。

永久真实源码 exploit：中性 `neutral.Consumer` 引用 `ExactPilotBinding.DeploymentIdentity.RUNTIME_PROFILE` 和 `AdmissionGuard.SIDE_EFFECT_POLICY_VERSION`。Fixture 使用当前真实 core 源码和 JDK 编译至临时目录：javac=0、runtime=0；checker 对两个成员均报告 `UNAUTHORIZED_COMPATIBILITY_CALLER`。全量现有 approved edge 检查 PASS，unauthorized=0。

## 4. P1-3：活动可执行输入 completeness

Root cause：旧 `inspect()` 对不在扩展名白名单中的文件静默返回；`.js` 可通过中性 workflow 获得执行权而不被检查。

新增通用控制：活动 `scripts/`、`deploy/`、`.github/` 下的每个输入都必须可检查，或属于精确路径、原因和内容 SHA-256 固定的 safe data。这个范围比已解析的调用图更保守，未知类型即使没有 stage 字面量或尚未发现 caller 也拒绝。Static local interpreter/direct-launcher 引用还把这些目录之外的输入纳入检查。Workflow 中缺失的本地执行输入同样拒绝。

支持的可执行类型：`.ps1 / .psm1 / .sh / .py / .js / .mjs / .cjs / .java`；其余现有 YAML、JSON、XML、service、configuration 等控制输入也检查。当前实际控制面可执行文件为 ps1=48、psm1=2、py=2、java=4；仓库当前没有该范围内的 sh/js/mjs/cjs 文件，这些类型由永久 fixture 覆盖。唯一额外 safe input 是 `.github/CODEOWNERS`，固定为 ownership metadata；safe data 被当作 executable 使用时 REJECT。

负向与边界：

- 中性 workflow → `node scripts/runtime.js` → hidden stage branch/profile：REJECT。
- 同样语义的 ps1/sh/py/mjs/cjs：全部 REJECT。
- workflow → unknown `.rb/.lua/.cmd/.opaque` 或 extensionless active input：REJECT。
- workflow → 目录之外的 interpreter input/direct launcher、missing input：REJECT；相对 JS import/require 输入递归检查，未知类型或不可检查 external module dependency fail-closed。
- historical JS、历史 stage 文本及 generated fixtures：ALLOW；不扫描 node_modules、target 或历史 evidence。
- safe data 内容变化或作为执行输入：REJECT。

不存在针对 `GateYMode`、`runtime.js` 或 reviewer 类名的 checker 特判。

## 5. Review exploit 前后复现

用进入本轮时保留的原 checker/policy，在隔离 fixture 中复现：中性 Java caller、JS hidden semantics、unknown executable 均返回 errors=0。切换整改后的 checker/contract，同一 fixture 分别出现 unauthorized member edges、`STAGE_SEMANTICS`、`UNSUPPORTED_ACTIVE_EXECUTABLE_INPUT`。

日志：`artifacts/f009-p1-remediation/exploits-before-after.log`。永久 tests 同时验证真实 core 源码编译/运行与 checker 拒绝，不以编译失败代替 authorization proof。

## 6. 验证与失败记录

| 检查 | 结果 |
| --- | --- |
| Affected Maven targeted | 29 tests，failures/errors/skips=0，BUILD SUCCESS |
| Adapter endpoint/wiring | 9 tests；其中 wiring test 内含 32 个 context 组合 |
| Public outbound composition | 4 tests PASS |
| Private-readonly permission context | 10 tests PASS |
| Readonly observation composition/production context | 4+2 tests PASS |
| Windows stage guard full suite | 31 tests：30 PASS，1 existing symlink environment skip |
| Linux stage guard full suite | 31/31 PASS，0 skips；包含 symlink refusal |
| Compatibility relationship suite | 已包含在上述 full suite，真实成员 exploit compile/runtime PASS、checker REJECT |
| Windows/Linux current active scan | scanned=1798、reviewed exceptions=178、errors=0 |
| Authority/governance/document checks | 结果与可重复命令见下方最终验证记录 |
| Full Maven / PG16 / canonical release 66 / Phase5B admission / frontend | NOT_REQUIRED，未执行 |
| Production / LIVE / private provider / deployment | 未执行 |

开发阶段失败与隔离记录：初次正向 context test 使用既有默认构造器，检查代码后发现其环境解析器可能读取 `.env`，不将该次运行作为隔离证明；未输出其内容，已替换为显式 loopback supplier 并重新运行 targeted Maven。新增继承 probe 首次只能拒绝继承关系，未定位 inherited constant 的使用方法；补齐继承成员可见性后通过。更新 ROADMAP 后，旧内容 pin 正确拒绝文档；仅刷新这一本轮授权文件的 pin 后通过。最初 evidence helper 对已无 stage 语义的 test 未处理 None，退出 1；改为移除 stale exception 后完成。Linux 初始无 JDK，未新增 test skip；从官方 Adoptium 下载 isolated JDK 21，SHA-256=`ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94` 验证通过。首次 PowerShell 下载过慢，停止该任务进程，改用 curl 完成并验证；未修改系统环境。收尾新增 fully-qualified member 与相对 JS module probe 后，细化发现额外 94 条既有成员边；外部 caller 集合保持 110，Windows/Linux 使用扩展后的 31 项 suite 复验。直接 launcher 识别初次把多行文档清单误当命令，收窄到 workflow/systemd 命令字段与显式相对执行行后恢复通过。

## 7. Closure metrics 与 scope binding

| Metric | 整改后 |
| --- | --- |
| Unclassified active assets | 0 |
| Unresolved retired-path callers | 0 |
| Legacy retired paths after | 0（89 个 registry paths 均不存在） |
| Stage-specific active control-plane entrypoints after | 0 |
| Hidden active stage semantics | 0 |
| Unauthorized compatibility caller edges | 0 |
| New compatibility caller bypass | 0（永久负向用例拒绝） |
| Unsupported active executable inputs accepted | 0 |
| Historical evidence modified | 0（不含本轮获准更新的 current machine inventory） |
| Canonical path duplication | 0；原 review 通过区域的内容绑定保持一致 |

Scope fingerprint 排除下一节列出的 12 个允许路径，其余 tracked/nonignored-untracked 路径、原始文件内容与 missing marker 均参与原 reviewer 算法：按路径排序，依次 SHA256.update(path + NUL)，再 update(file SHA256 raw digest)，缺失文件使用字节 `MISSING`。

```text
unaffected before = ad338e7ac9a9a599a14dc4cedecd98ab6bda4ed18373e4fb2a7256e66817d34b
unaffected after  = ad338e7ac9a9a599a14dc4cedecd98ab6bda4ed18373e4fb2a7256e66817d34b
mismatch = 0
unchanged candidate paths = 3201
unaffected inventory candidate records = 1138 / 1138
```

此外对 inventory 的 deletedAssets、movedAssets、dispositionCounts、retiredPathCallerGraph、controlPlaneAssetsBefore、allApplicationResourceInventory 作结构化 equality 校验。未重新 qualification 已通过区域，没有 canonical implementation dependency change。

## 8. 本轮文件与回滚

仅修改下列已有文件或新增明确标注的两个文件：

1. `backend/nq-adapter-okx/src/main/java/com/guidinglight/nexusquant/adapter/okx/service/OkxHistoricalKlineAdapter.java`
2. `backend/nq-adapter-okx/src/test/java/com/guidinglight/nexusquant/adapter/okx/service/OkxSpotEndpointGuardTest.java`
3. `scripts/docs/check-stage-assets.py`
4. `scripts/docs/JavaCompatibilityReferences.java`（新增）
5. `scripts/docs/stage-asset-exceptions.json`
6. `scripts/docs/tests/test_stage_assets.py`
7. `docs/audit/evidence/GATEAUDIT_PHASE5_F009_ACTIVE_ASSET_INVENTORY.json`
8. `docs/audit/evidence/GATEAUDIT_PHASE5_F009_FOCUSED_REVIEW_P1_REMEDIATION.md`（新增，本文件）
9. `docs/current/STATUS.md`
10. `docs/current/ROADMAP.md`
11. `docs/current/TESTING.md`（仅追加）
12. `docs/current/WORKLOG.md`（仅追加）

精确 before bytes、baseline hashes 与 scope report 保存在 `artifacts/f009-p1-remediation/before/`、`baseline.json`、`scope-binding.json`。回滚只将上面 10 个已有文件恢复为本轮 before bytes，并移除两个本轮新增文件；执行前确认这些文件未出现后续用户改动。不得用 HEAD 覆盖整个 F009 candidate，也不得 reset/clean。当前未执行回滚。

建议后续合并 delta 的 commit message：`fix(governance): 修复阶段装配与兼容调用和执行输入守卫`。本轮不执行 add、commit、push。

## 9. 复验命令与下一动作

```powershell
python -B scripts/docs/check-stage-assets.py
python -B -m unittest discover -s scripts/docs/tests -p test_stage_assets.py -v
mvn -o -f backend/pom.xml -pl nq-app -am "-Dtest=OkxSpotEndpointGuardTest,PublicMarketDataOutboundConfigurationTest,ReadOnlyProviderObservationConfigurationTest,ReadOnlyProviderObservationProductionContextTest,OkxPrivateReadOnlyPermissionProbeSpringContextTest" -Dsurefire.failIfNoSpecifiedTests=false test
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1
pwsh -NoProfile -File scripts/docs/check-current-authority.ps1
pwsh -NoProfile -File scripts/docs/test-current-authority-next-action.ps1
pwsh -NoProfile -File scripts/docs/test-agent-workflow-fixtures.ps1
pwsh -NoProfile -File scripts/docs/test-governance-workflow-lifecycle.ps1
pwsh -NoProfile -File scripts/docs/check-doc-links.ps1
git diff --check
git diff --cached --name-only
```

Linux 用同一套 Python tests 与 JDK 21 执行；JDK/parser 缺失即失败，不能改为 skip。

Machine authority 使用既有合法 `IMPLEMENTED|PENDING_REVIEW` 和同一 batch 的 `REVIEW` action，不修改 matcher。人工唯一下一任务：`NQ-GATEAUDIT-PHASE5-F009-FOCUSED-P1-CLOSURE-REVIEW`，范围仅三项 P1 与 unaffected candidate binding。尚未 independent closure review、commit、push、exact-head CI；不得声称 ACCEPTED/CLOSED 或 READY_TO_COMMIT。

工具声明：PowerShell、Git、Python、Maven、JDK、WSL、curl；MCP 仅用于读取上一轮任务的 fingerprint 算法与 findings。Skills 使用 router、python-ops-tooling、java-backend-maintenance 和 nq-docs-writer；Java standard 仅核对适用条件/platform profile，未触发全仓 Java 验证。网络为授权 origin fetch 和官方 Adoptium/GitHub JDK 下载。写操作限上述 delta、验证 artifacts 和构建生成物；未直接读取或输出 credential，最终正向 fixture 隔离环境解析；无生产、交易或远端发布操作。


## 10. 最终验证记录

- Authority：PowerShell 5.1 与 7 均 errors=0 / CURRENT_AUTHORITY_VALID。
- Next-action：positive=7、ambiguous=4、safety-negative=9、schema-negative=4、whitespace-negative=5，failed=0。
- Agent workflow：fixtures=12/12，malicious mutations=6/6 rejected，其他 capability/runtime/audit-policy negative 全部拒绝，无 contract drift。
- Governance lifecycle：20/20 PASS，无 Task-ID-specific runtime rule。
- Doc links：追加本轮 ledgers 后复验，errors=0；123 项已有历史 warning 保留。
- Closure metrics、scope-binding 与完整 Git 输出保存在本轮 artifacts；临时 exploit fixture 与下载的 Linux JDK/压缩包已清理，证据/回滚 before bytes 与 Maven target 生成物保留。
- Staged=0、commit=NONE、push=NONE；P0=0，P1 remaining=0（implementation remediation 口径，独立 closure review 尚未执行）；P2/P3 新增=0。
