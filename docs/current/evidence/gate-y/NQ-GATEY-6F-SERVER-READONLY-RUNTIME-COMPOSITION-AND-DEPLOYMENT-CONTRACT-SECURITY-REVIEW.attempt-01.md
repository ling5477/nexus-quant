# NQ-GATEY-6F server read-only runtime composition and deployment contract Security Review attempt-01

## 1. Review decision

`FAIL / GATEY_6F_SERVER_READONLY_RUNTIME_COMPOSITION_AND_DEPLOYMENT_CONTRACT_SECURITY_REVIEW_REJECTED / P0_2 / P1_2 / RELEASE_LINK_INTEGRITY_BYPASS / ROLLBACK_EVIDENCE_UNTRUSTED / QUALIFICATION_PRODUCTION_CONTEXT_NOT_BOOTABLE / POSIX_OWNER_MODE_INSTALLATION_CONTRACT_UNENFORCED / NOT_READY_TO_COMMIT`（失败 / GateY-6F server只读runtime composition与部署合同安全审查拒绝 / 不可提交）。

实现的默认关闭、kill-engaged guarded authority、单profile轻量Bean图、canonical JSON、migration byte binding及GateW regression均有正向证据；但release verifier接受指向release root外可变inode的hard link，rollback/health只信任调用者JSON布尔值，完整production component scan的qualification context无法启动，且实际owner/mode/installer/atomic current contract未被执行或验证。P0/P1未关闭，禁止commit、push、exact-head CI、release build与deployment。

## 2. Task classification

- `SECURITY_AUDIT / INDEPENDENT_SECURITY_REVIEW / REVIEW_ONLY`。
- 辅助审查面：Spring runtime graph、credential boundary、network ingress、mutation reachability、release supply chain、migration/rollback、startup side effect、task evidence。
- NQ-only、L级；被审实现不得修改。

## 3. Baseline 与 authority before

- `git fetch origin`成功；branch=`dev`。
- `HEAD == origin/dev == 2605a20e9de3a6ef2cacc3118a353942fa74b2b1`。
- staged=`0`。
- baseline CI：`NQ CI Baseline` run=`32041844923`、attempt=`2`、`completed / success`、headSha=`2605a20e...`、10 jobs全绿。
- authority checker：errors=`0`；`GateY-6F=NOT_STARTED`、`LIVE=DISABLED`、kill switch=`ENGAGED`、first real order/micro-live未授权、soak未启动。

## 4. Scope 与 files inspected

逐项读取并复核implementation canonical changed set全部22路径：11 modified + 11 untracked，missing/extra=`0/0`。覆盖：

- runtime/composition/profile：`ExchangeAdapterConfiguration`、两个GateY qualification classes、guarded authority、qualification YAML。
- isolation：5个scheduler/recovery classes与validation scheduler configuration。
- tests：两个qualification tests与exchange adapter regression。
- release/deployment：GateY builder、contract module、deployment evaluator、PowerShell regression。
- evidence：implementation evidence、GateY index、`TESTING.md`、`WORKLOG.md`的本轮diff。

额外只读检查：`NexusQuantApplication` component scan、PilotScope API/Service、default/OKX authorities、credential executor、typed transport/signer、Spot provider/adapter、worker launcher、所有相关startup/scheduler/WS/catalog/recovery components、GateW builder/contract/installer/verifier/regression及V1～V41 migrations。未读取`.env`、key、pem、secrets、credentials、logs、dumps或backup。

## 5. Implementation diff provenance review

- implementation evidence声明的4类inventory可展开为22条exact paths；实际changed set=`22`、missing=`0`、extra=`0`、staged=`0`。
- `backend/frontend/research/deploy/.github/migration/GateW frozen`无额外diff；GateW文件未修改。
- review写入前未发现mixed worktree。

## 6. Threat model

主要攻击者/失败源：能够准备或替换release directory内容的本机用户/installer缺陷；能够伪造deployment evidence JSON的调用者；profile/component scan遗漏；具备同一文件系统hard-link能力的低权限主体；配置组合错误。关键资产：exact application JAR、migration bytes、manifest identity、credential material、kill/LIVE boundary、immutable release/current pointer与production recovery能力。

信任边界：Git exact source → local builder → release directory/manifest → server installer/verifier → Flyway/backup/restore → atomic activation → qualification runtime → JIT credential/OKX。P0攻击发生在release directory verifier边界；P1发生在Spring full-context与deployment evidence provenance边界。

## 7. Findings

### P0-1 — GateY release verifier接受外部可变inode的hard link

- 证据：`scripts/gatey/gatey-readonly-release-contract.psm1:312-364`；artifact验证在338～345行只做lexical containment、`PathType Leaf`、size与SHA-256，不检查`LinkType`、reparse、hard-link count、逐级NOFOLLOW、real-path containment或stable-open identity。
- 动态PoC：在独立临时目录把`app/nq-app.jar`建立为指向release root外文件的`HardLink`；`Get-Item.LinkType=HardLink`，`Test-GateYReadonlyRelease`仍返回`PASS / GATEY_READONLY_RELEASE_VERIFIED`。
- 对照：GateW verifier在`verify-gatew-release.ps1:74`拒绝link/reparse，并在890、955行验证实际POSIX contract；GateY deployment evaluator没有调用GateW verifier。
- 影响：外部路径可在verification后修改同一inode，改变已“验证”的application artifact；manifest hash保持不变但运行内容漂移，破坏immutable/content-addressed release与migration/application identity。
- 分类：`P0 / RELEASE_INTEGRITY_BYPASS`。

### P0-2 — Rollback/health hard gate信任自报布尔值，没有proof provenance

- 证据：`scripts/gatey/invoke-gatey-readonly-deployment-contract.ps1:18-29`直接读取任意JSON，将`previousReleaseCompatibleWithTargetSchema`、`backupVerified`、`restoreProcedureVerified`及health字段强制转换后交给assertion；31行即可输出`PASS / GATEY_READONLY_DEPLOYMENT_CONTRACT_READY`。
- regression fixture在`run-gatey-readonly-release-contract-regression.ps1:158-160`仅设置三个布尔值，没有backup path/hash/size/owner/mode、restore drill receipt、Flyway query evidence digest、previous-release compatibility proof或签名/attestation。
- 影响：不存在backup或restore procedure时，调用者仍可提交`true`使rollback gate通过；同理可自报startup/health counters为0。合同无法区分真实evidence与伪造JSON。
- 分类：`P0 / ROLLBACK_CONTRACT_UNPROVEN`。

### P1-1 — 完整production qualification Spring context无法启动

- 证据：`ExchangeAdapterConfiguration.java:56-98`在qualification profile排除OKX/Binance adapter；但`AdapterInstrumentCatalogSyncService.java:33-49`仍是无profile的`@Component`并强制注入两个adapter。`BinanceRecoveryService.java:39-65`同样无profile且依赖被排除的Binance adapter/reconcile。
- 现有测试`GateYReadonlyQualificationConfigurationTest.java:57-120`只注册选择性configuration/component集合，未使用`NexusQuantApplication`完整component scan，因而看不到这些production consumers。
- 动态验证：IDEA run configuration以唯一active profile=`gatey-readonly-qualification`、loopback/dummy非秘密配置、web type=none启动完整application；在credential/OKX调用前以`UnsatisfiedDependencyException`失败，首个缺失Bean为`AdapterInstrumentCatalogSyncService`构造参数`OkxExchangeAdapter`。
- 影响：实现目标runtime不可部署/不可健康启动；轻量Bean图绿不能证明production graph。
- 分类：`P1 / QUALIFICATION_PRODUCTION_CONTEXT_NOT_BOOTABLE`。

### P1-2 — POSIX owner/mode、installer、atomic current只被声明，没有被GateY contract执行验证

- 证据：manifest在`gatey-readonly-release-contract.psm1:176-201`声明owner/current/mode policy，但`Test-GateYReadonlyRelease`不比较实际mode/owner；builder在`build-gatey-readonly-release.ps1:312-323`完成普通directory move后即返回`deployable=true`。
- GateY没有installer；`invoke-gatey-readonly-deployment-contract.ps1`不调用GateW installer/verifier、不检查existing release overwrite、root ownership、service-user write denial或atomic symlink operation。
- 影响：Windows/local目录可被标为deployable，却没有可复核的Linux root/POSIX/immutable installation evidence；future deployment可能绕过GateW已接受语义。
- 分类：`P1 / RELEASE_INSTALLATION_CONTRACT_UNENFORCED`。

### P2 residuals

- committed exact-head deployable release尚未构建。
- server实际`flyway_schema_history`未核验。
- production backup未创建、integrity未验证。
- restore drill未执行。
- previous release + target schema compatibility未在服务器证明。
- systemd activation与真实health acceptance未执行。

### P3

- implementation evidence changed-files段仍写“13-case regression”，实际脚本/测试结果为14/14，属于非阻断evidence文字漂移。
- `BinanceRestReconcileService`既有Javadoc引用不存在的`eventStoreAppender`参数；不在本轮逻辑diff，Maven编译通过。

## 8. Spring bean graph review

- default轻量context：trusted/guarded/Spot provider=`0/0/0`，unavailable authority为唯一bean；default adapter行为保持既有guarded adapters。
- qualification轻量context：guarded authority=1，Spot provider/TradingAdapter/worker/scheduler=0；计数DataSource与kill repository startup reads=0。
- production full context：FAIL；无法达到qualification context healthy，故不能接受`SPRING_BEAN_GRAPH_VERIFIED`。
- guarded call chain源码成立：PilotScope control plane → guarded authority → kill=`ENGAGED` → OKX authority → exact credential JIT → observePrerequisites。未发现`SpotExecutionProviderPort` production bean或ExecutionIntent→real provider binding。

## 9. Credential lifecycle and exposure review

- guarded authority在delegate前读取durable kill snapshot；非ENGAGED/UNKNOWN fail-closed。
- executor构造不查询DB；exchange credential仅在explicit callback内SELECT/decrypt，session线程/生命周期受限，char arrays finally清理。
- scoped safe-path regex未发现硬编码API key/secret/passphrase/private key/token/password值。
- full-context启动在adapter dependency resolution阶段失败；本轮credential metadata/material read/decrypt=`0/0/0`，OKX calls=0。
- 未发现credential进入manifest、evidence、DTO或新增日志。

## 10. Startup side-effect、network与management ingress review

- runtime identity强制releaseId/sourceCommit exact 40-hex相等、profile identity、Java 21、`server.address=127.0.0.1`；非loopback context fail-fast。
- profile YAML关闭LIVE、real provider/client/exchange、catalog sync、WS、recovery与validation scheduler；changed scheduler/recovery classes增加profile exclusion。
- 未新增generic proxy/raw provider/debug endpoint、transfer/withdraw/borrow/margin/batch/algo path。
- 完整application未健康启动，故不能宣称完整startup side-effect acceptance；实际动态启动在credential/network前失败，side effects仍为0。

## 11. Release supply-chain、determinism与GateW review

- exact clean HEAD gate、Java 21、source-commit timestamp、local Maven clean/repackage、fat-JAR migration byte binding、canonical JSON、artifact hash/tamper与dirty-worktree rejection均有实现和测试。
- PS5.1/PS7 manifest hash一致；V1～V41 synthetic JAR migration tamper被拒绝。
- 但GateY regression没有link/hardlink/junction/reparse、actual owner/mode、installer overwrite/atomic current负例；P0/P1成立。
- GateW files diff=`0`；GateW 34/34 regression通过。GateW语义本身未退化，但GateY contract没有安全复用该语义。

## 12. Migration、rollback、activation review

- actual migration inventory：V1～V41连续、41 files、target=`V41`、inventory SHA-256=`2b6847457a91423f0cbbaed49c3e018f28846a5b94615a169fc5bee67802488b`。
- migration plan从输入的applied versions派生pending且拒绝非连续history；不硬编码server current version。
- 顺序模型包含history→pending→backup→verify→rollback→forward-only migrate→target→activate→health。
- 但输入不是对`flyway_schema_history`、backup或restore proof的可信采集，rollback/health P0未关闭；activation仅输出path/pointer字符串，不执行/验证atomic current。

## 13. Architecture ownership review

- app新增内容主要为composition root；guarded authority位于infra，OKX protocol仍在adapter，credential JIT仍在account infra，mutation port仍属execution；ArchUnit全绿。
- 未发现Controller直接调用OKX、第二credential/PilotScope SoR或cross-domain DTO shortcut。
- production component profile闭包缺失属于composition完整性P1，不是ownership反向依赖。

## 14. Tests executed and results

| Command / check | Result |
| --- | --- |
| `mvn -f backend/pom.xml test` | 23 modules、319 reports、1546 tests、failures/errors/skipped=`0/0/48`、50.469s、`BUILD SUCCESS` |
| GateY contract / PowerShell 5.1 | 14/14 PASS；manifest SHA-256=`c8b986ee55deadca4b13671871dd545e87052b70eb82e88c827d8b0c0aad8c01` |
| GateY contract / PowerShell 7 | 14/14 PASS；与PS5.1 hash一致 |
| GateW release regression | 34/34 PASS |
| full qualification application run | FAIL before credential/network；`AdapterInstrumentCatalogSyncService → OkxExchangeAdapter` missing |
| local hard-link release PoC | verifier错误返回`PASS / GATEY_READONLY_RELEASE_VERIFIED`；P0 reproduced |
| migration inventory | V1～V41、41 files、continuous、target=V41 |
| authority | errors=0、PASS |
| doc links | 378 checked / 14 historical warnings / 0 errors |
| implementation provenance | expected/actual=`22/22`、missing/extra=`0/0`、staged=0 |
| tracked/untracked diff check | PASS；仅既有LF→CRLF提示 |

绿色regression不关闭Finding：现有14-case suite没有hard-link/owner/mode/full component scan/proof provenance覆盖。

## 15. Authority after、side effects 与 remediation

- authority after：不修改`STATUS/ROADMAP`；`GateY-6F=NOT_STARTED`、first real order/micro-live未授权、soak未启动、LIVE disabled、kill engaged。
- Server SSH read/write、deployment、migration、backup、restore、symlink/systemd change均=0。
- credential metadata/material read/decrypt=`0/0/0`。
- OKX GET/POST、PLACE/CANCEL、transfer/withdraw=`0/0/0/0/0/0`。
- ExecutionIntent/Receipt、order、ledger delta=`0/0/0/0`。
- LIVE enable、kill disengage=`0/0`。
- stage/commit/push/PR/tag=`0/0/0/0/0`。

Remediation必须在独立任务中完成，审查者本轮不修改实现：

1. GateY release verifier/installer安全复用GateW primitives，或实现等价的逐级NOFOLLOW、real-path containment、regular-file + link-count=1、actual owner/mode、stable identity、root-only install、no-overwrite与atomic current；新增hard-link/symlink/junction/reparse/replace race回归。
2. deployment evidence改为typed、可追溯proof：绑定Flyway query digest、backup path/size/hash/owner/mode、restore drill receipt、compatibility decision source与health采集receipt；拒绝裸布尔自报。
3. 收口qualification production component profile closure；至少处理所有强依赖被排除adapter/scheduler的components，并新增`NexusQuantApplication`完整component-scan integration test，证明healthy context、Bean counts与startup side effects。
4. 重跑本审查全部命令；P0/P1=0后才允许commit。

Next action：`NQ-GATEY-6F-SERVER-READONLY-RUNTIME-COMPOSITION-AND-DEPLOYMENT-CONTRACT-P0-P1-REMEDIATION`。
