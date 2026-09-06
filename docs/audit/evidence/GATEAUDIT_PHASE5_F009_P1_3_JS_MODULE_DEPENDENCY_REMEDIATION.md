# F009 P1-3 JS module dependency remediation

任务：`NQ-GATEAUDIT-PHASE5-F009-P1-3-JS-MODULE-DEPENDENCY-REMEDIATION`。

结果：`IMPLEMENTED / PHASE5_F009_P1_3_JS_MODULE_DEPENDENCY_BYPASS_REMEDIATED / P0_0 / P1_1_REMEDIATED / PENDING_P1_3_CLOSURE_REVIEW`。这里的 `P1_1_REMEDIATED` 表示本轮一项 P1 已整改；不改写已经 CLOSED 的 Finding P1-1/P1-2，也不是独立 review 或提交许可。

## 1. 基线与范围

- Repository：`E:\Project\nexus-quant-gateaudit`。
- Branch：`audit/post-gatey-agent-baseline`；HEAD=`21d14d9112364f3befcc77a1a370828ef22e72ed`。
- Origin：`https://github.com/ling5477/nexus-quant.git`；本地 upstream ref 与 HEAD 相同。本轮没有 fetch，未核验远端是否变化。
- Reviewed candidate=`c421eddb9b6b0d3fc9e707f2a3e766ceb87e20ff9fd88b9003c64fffedfa814f`，现场复算一致。
- 原 unaffected fingerprint=`ad338e7ac9a9a599a14dc4cedecd98ab6bda4ed18373e4fb2a7256e66817d34b`，写前按原 12 路径 exclusion manifest 复算一致。
- 用户本轮授权优先于 STATUS 中同一 F009 batch 的通用 REVIEW token。STATUS/ROADMAP 和全部安全字段均不修改；人工下一任务采用本轮指定的 P1-3 closure review。
- Primary Skill=`python-ops-tooling`；router 仅做 authority/范围路由，`nq-docs-writer` 仅支持本轮指定的 evidence 与 ledgers。风险=`HIGH_RISK`；无子代理或独立 review。

唯一允许的 candidate delta：

1. `scripts/docs/check-stage-assets.py`
2. `scripts/docs/tests/test_stage_assets.py`
3. `docs/current/TESTING.md`（仅追加）
4. `docs/current/WORKLOG.md`（仅追加）
5. `docs/audit/evidence/GATEAUDIT_PHASE5_F009_P1_3_JS_MODULE_DEPENDENCY_REMEDIATION.md`（新增本文件）

未修改 inventory、P1-1 Java/config/test、P1-2 compatibility authorization、18 contracts、1559 edges、65 DELETE、24 migrations、29 caller adjustments、canonical release/deploy/rollback、systemd、Phase5A/B、F007/F008、历史证据或冻结文件。

## 2. Root cause 与新合同

旧模型直接对整个 JS source 做 import/require/from 正则匹配，既没有 comment/string token 边界，也没有完整消费依赖声明的要求。合法注释会拆开关键字与 module specifier，导致漏掉位于 `tools/` 的二级模块。原因是 dependency parser coverage defect，不能靠增加某个注释模式修复。

新模型为无第三方依赖的 tokenizer 加保守 statement grammar。它不执行被分析 JS，不调用 Node/parser 服务，不读取 node_modules，不触发网络。Node 只在永久测试中执行测试自己创建的纯字面量模块，用于证明 exploit 语法有效；测试移除 NODE_OPTIONS/NODE_PATH 环境 preload。

当前 `scripts/`、`deploy/`、`.github/` 的实际活动 JS 文件为 0；当前完整活动扫描也没有拉入外部 JS caller。因此不存在必须保留的 require/dynamic import caller。本轮没有安装新 parser 或修改 dependency governance。

### 支持的 grammar boundary

- 静态 `import './a.js'`，default import，named import/alias，namespace import，以及 default 与 named/namespace 的组合。
- 静态 `export { value } from './a.js'`、named alias、`export * from`、`export * as ns from`。
- token 间任意合法 JS whitespace、line break、`//` 与 `/* ... */` comment；单/双引号 module string；语句以分号、EOF 或符合此子集的换行边界结束。
- 允许空语句、单个 identifier 的 const/let/var 字面量初始化（string、十进制 number、boolean、null，number 可带负号），及对应 exported declaration。
- 每个 token 都必须属于上述 grammar；不存在“跳过函数体/表达式但返回依赖”的通道。记录 module binding/export names，拒绝重复绑定/重复 export 与非法 import clause。
- `.js/.mjs` 使用上述 module 子集；`.cjs` 可接受简单字面量声明，静态 import/export 明确拒绝。

该子集刻意保守，**不是完整 JavaScript 语法支持**。函数、任意表达式、require、dynamic import、import.meta、local export、import attributes、template literal、regex/division、escaped literal/identifier、Unicode identifier 等均拒绝。日后真实 caller 需要这些语法时，须显式扩展 grammar 和测试；不能静默放行。当前没有因此被拒绝的实际 JS caller。

### Fail-closed behavior

- Unterminated comment/string、unsupported token/statement、非法 clause 等产生 `UNSUPPORTED_ACTIVE_JS_SYNTAX` 阻断错误；CLI exit=1，并保留其他 stage diagnostics。输出只有类别与文件路径，不回显 source。
- 识别到的依赖必须以 `./` 或 `../` 开头；只按精确路径解析本地 `.js/.mjs/.cjs` 文件，不做扩展名补全或 package resolution。
- 缺失文件、根目录逃逸、非相对 specifier、URL/query/fragment/percent 编码路径、未知 executable suffix、symlink（含 parent directory alias）均拒绝。
- 可解析的 dependency 加入原有 pending/inspected 集合递归扫描；cycle 只检查一次，结果确定，不无限递归。
- 历史 evidence/archive 不参与活动递归；若活动模块反向 import 历史文件，则拒绝该活动引用。

## 3. Exploit 与永久回归

固定 graph 为 neutral workflow → `node scripts/runtime.mjs` → `tools/secondary.mjs`，secondary 含 `export const something = 1; const GateYMode = true;`。

| 输入 | 旧 checker | Node fixture | 新 checker |
|---|---|---|---|
| `import /* dependency */ '../tools/secondary.mjs';` | ALLOW | PASS | REJECT，定位 secondary |
| `import/*x*/'../tools/secondary.mjs';` | ALLOW | PASS | REJECT，定位 secondary |
| multiline named import，`from /* x */ '../tools/secondary.mjs'` | ALLOW | PASS | REJECT，定位 secondary |
| named/star/namespace re-export | 不作为旧模型结论 | PASS | REJECT，定位 secondary |
| string 中的 `import './fake.mjs'` | 不作为旧模型结论 | PASS | ALLOW，无 fake dependency |
| block comment 中的相同文本 | 不作为旧模型结论 | PASS | ALLOW |
| line comment 中的相同文本 | 不作为旧模型结论 | PASS | ALLOW |
| 历史 evidence 中相同文本及 stage literal | OUT_OF_ACTIVE_SCOPE | 不执行 | ALLOW |
| missing dependency、unsupported active syntax/type | — | 不执行 | REJECT |
| 三层相对依赖循环 | — | PASS | ALLOW 且重复结果相同；末端加入 stage 语义后 REJECT |

新增 `JavaScriptDependencyTest` 18 项；原 31 项全部保留。AST 对比证明原两个测试类及其 assertions 完全不变；checker 除 `executable_inputs` 外的全部原函数也完全不变。比对证据在 `artifacts/f009-js-dependency-remediation/before-after-and-preservation.json`。

## 4. Windows/Linux 与环境记录

| 验证 | 结果 |
|---|---|
| Windows full stage guard | 49 tests，47 PASS，2 symlink permission skips，0 failures/errors |
| Linux full stage guard | 49/49 PASS，0 skips，包含两项真实 symlink 拒绝 |
| Windows/Linux active scan | scanned=1798，reviewed_exceptions=178，errors=0 |
| Authority PS5.1 / PS7 | PASS，errors=0 |
| Next-action | positive=7、ambiguous=4、safety-negative=9、schema-negative=4、whitespace-negative=5，failed=0 |
| Agent workflow | fixtures=12/12，malicious=6/6 rejected；contract drift=0 |
| Lifecycle | 20/20 PASS |
| Docs links | PASS，errors=0；123 existing historical warnings |
| Append-only / scope / diff | PASS；protected mismatch=0，staged=0 |

Windows 的一项 symlink skip 是原有环境限制，另一项是新增 parent-alias case 的同一权限限制；两项均在 Linux 实际执行通过，不声称 Windows symlink 能力已验证。

Linux JDK source：Ubuntu 24.04 官方 `archive.ubuntu.com` / `security.ubuntu.com` 软件源。Java version=`21.0.12+8-1-24.04-Ubuntu`，javac=`21.0.12`，`JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`，`bin/java=ELF 64-bit x86-64`。安装方式为 apt `openjdk-21-jdk-headless`，仅 TEST_ENVIRONMENT_MUTATION；repository files changed by installation=0。通过实际 `readlink -f` 路径设置进程级 JAVA_HOME/PATH；没有拿 java.exe 作 Linux qualification。

环境处理记录：最初按离线要求搜索；`/usr/lib/jvm` 不存在，`/opt`、`/usr/local`、WSL HOME 无候选，`/tmp` 两个目录无读取权限；挂载盘扫描尚未结束时用户追加 JDK 网络授权，停止该轮搜索，不声称已完成全部离线排除。sudo 非交互需要密码，依用户安装授权通过 WSL root 执行官方源 apt。首次安装因 libpcsclite1 下载 HTTP 502 返回 exit=100；同一官方源有限重试后 exit=0，未新增其他网络范围。

开发失败记录：首轮 Windows 49 tests 中 3 个既有 `.js/.mjs/.cjs` stage-semantic case 因新 ValueError 提前中断而报 errors。修复为收集阻断诊断，保留后续 stage 检查；未修改旧 tests/assertions。其余最终运行均以 final 日志为准。

日志完整性：发现 WSL stdout/stderr 同时重定向到 Windows 文件时覆盖了部分日志行，未据此忽略验证。最终在 Linux shell 内合并输出并通过 capture_output 一次写入，重采集完整 suite、scanner 和 JDK qualification 输出。Windows Node=`24.16.0`，Linux Node=`22.22.1`，均仅执行固定测试 fixture。

## 5. Scope binding 与 closure metrics

算法不变：枚举 Git tracked 与 nonignored untracked 路径，按路径排序；对每个路径依次更新 `SHA256(path UTF-8 + NUL)` 与文件 SHA-256 raw digest，缺失文件使用 `MISSING` 字节。只改变显式 exclusion manifest：原轮排除 12 个路径，本轮仅排除第 1 节的 5 个路径，纳入更多保护内容。

```text
old unaffected = ad338e7ac9a9a599a14dc4cedecd98ab6bda4ed18373e4fb2a7256e66817d34b
new scoped before = 0c4cb19699c5bf914d8e675f60fc37a4869b15ba4ee18ea3bac65e82c9740099
new scoped after  = 0c4cb19699c5bf914d8e675f60fc37a4869b15ba4ee18ea3bac65e82c9740099
protected paths = 3209
mismatch = 0
```

`artifacts/f009-js-dependency-remediation/allowed.json` 是本轮 exclusion manifest；`baseline.json` 与 `scope-binding.json` 提供完整 path/hash manifest 及 before/after 结果。新 evidence 不纳入其自身 fingerprint；最终完整 candidate fingerprint 位于 scope-binding artifact，避免文档自引用 hash。

| Metric | 本轮证明 |
|---|---|
| P1-1 residual / P1-2 residual | 0 / 0；沿用用户 CLOSED 结论，并以内容绑定和原函数/测试不变证明保持；不重新 review |
| hidden JS dependency bypass | 0，已支持 grammar 的 exploit/变体全部拒绝 |
| unsupported active executable accepted | 0，原 negatives 保留，新 unsupported grammar cases 拒绝 |
| relative dependency unresolved-but-accepted | 0，missing/escape/未知类型 negatives 拒绝 |
| hidden active stage semantics | 0，完整 active scan errors=0 |
| unclassified active assets / unresolved callers | 0 / 0，既有 inventory 原字节保持、完整 guard 通过 |
| historical evidence modified | 0；仅新增本 evidence、追加指定 ledgers |
| canonical duplication | 0 新增；原通过区域内容绑定保持，不重做 canonical qualification |

这些是本轮修复、回归及候选保持证据，不是对所有 JavaScript 语法或任意运行时行为的完备安全证明。

## 6. 复验、回滚与交接

```powershell
python -B -m unittest discover -s scripts/docs/tests -p test_stage_assets.py -v
python -B scripts/docs/check-stage-assets.py
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1
pwsh -NoProfile -File scripts/docs/check-current-authority.ps1
pwsh -NoProfile -File scripts/docs/test-current-authority-next-action.ps1
pwsh -NoProfile -File scripts/docs/test-agent-workflow-fixtures.ps1
pwsh -NoProfile -File scripts/docs/test-governance-workflow-lifecycle.ps1
pwsh -NoProfile -File scripts/docs/check-doc-links.ps1
git diff --check
git diff --cached --name-only
```

Linux 在同一 `/mnt/e/Project/nexus-quant-gateaudit` checkout，用实际 Linux JDK JAVA_HOME/PATH 运行相同 Python suite 和 checker。原始输出、安装日志、scope report、before bytes 与本轮 delta diff 均保存在 `artifacts/f009-js-dependency-remediation/`。

Git 收尾保留全部路径状态/stat/name-only/staged 输出及完整本轮 delta（包括原本 untracked 的 checker/tests）；全候选 diff 的敏感配置路径正文不渲染，仍参与完整 path/hash 绑定。未借此重审受保护区域。

回滚：确认没有后续修改后，将 `before/` 下四个文件的原始字节分别恢复到同名路径，仅删除本轮新增 evidence。不得从 HEAD 整体 checkout/reset/clean，因为那会覆盖原 F009 未提交候选。JDK 为已授权测试环境安装，保留供后续 review 使用；如需卸载可按安装日志中的新增包清单单独处理，不执行 autoremove 或清理既有环境。本轮未回滚。

Maven targeted / Full Maven / PG16 / canonical release 66 / frontend=`NOT_REQUIRED`，未执行；independent review / exact-head CI=`NOT_RUN`。P0=0，整改实现口径 P1 remaining=0，P2/P3 新增=0；独立 review 未完成，不使用 READY_TO_COMMIT。

Git：NO add、staged=0、commit=NONE、push=NONE；本轮 fixture/search 进程已结束，无临时 fixture/JDK archive 残留；验证日志和 before bytes 属于保留交付物，已安装 JDK 属于测试环境。

唯一下一任务：`NQ-GATEAUDIT-PHASE5-F009-P1-3-JS-MODULE-DEPENDENCY-CLOSURE-REVIEW`，只检查 JS extraction、合法 comment/whitespace、recursive checking、fail-closed、false positives、原 exploit 和 scope binding；不得扩大到 P1-1/P1-2 或原已通过区域。本轮不执行该独立 review。

建议后续 commit message：`fix(governance): 修复活动 JS 静态相对依赖漏检`。

工具声明：使用 PowerShell、Git、Python unittest/AST/hash、Node（纯测试 fixture）、WSL/Linux JDK/apt/file；MCP 未使用；Skills 为 router、python-ops-tooling、支持性的 nq-docs-writer。网络仅 Ubuntu JDK 软件源及必要安装依赖；未使用 npm/pip/Maven 下载、第三方 parser、生产服务、交易所、credential 或 LIVE。仓库写操作仅上述 5 个路径和 ignored 验证 artifacts；测试环境另安装 JDK。
