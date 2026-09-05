# F008 YAML Semantic CI Enforcement Remediation

日期：2026-09-05。任务：`NQ-GATEAUDIT-PHASE5-F008-YAML-SEMANTIC-CI-ENFORCEMENT-REMEDIATION`，NQ-only。

`IMPLEMENTED / F008_YAML_SEMANTIC_CI_ENFORCEMENT_REMEDIATED / P0_0 / P1_1_REMEDIATED / PENDING_FINAL_CLOSURE_REVIEW`

## 1. Scope、authority 与基线

- Classification：`CI_VALIDATOR_SECURITY_FIX / HIGH_RISK / YAML_SEMANTIC_VALIDATION / TARGETED_MUTATION_REGRESSION / NO_PRODUCTION_CODE_CHANGE / NO_COMMIT`。
- 唯一目标：`CI_PROD_CONFIG_BYPASS_VARIANTS_NOT_ENFORCED`。前一轮 Final Closure Review 在 plain/quoted raw-key 检查中证实 escaped-key bypass；本轮不重审 production profile、JWT/master-key、datasource、PG16、JAR、Flyway、release、F007/F009。
- Repository：`E:\Project\nexus-quant-gateaudit`；branch=`audit/post-gatey-agent-baseline`；HEAD=origin=`aa73a7a58b7d5ecbb8e5beba2106cbbe982803dc`，staged=0。沿用紧邻前轮已 fetch 的 remote-tracking 基线，本轮未再次联网。
- Current authority 实读：accepted batch 仍为 `GateAUDIT-PHASE5B-CANONICAL-DEPLOYMENT-AND-RESTORE / ACCEPTED|CI_GREEN`；work batch=`GateAUDIT-PHASE5-F008-PROD-CONFIG-FAIL-CLOSED / IMPLEMENTED|PENDING_REVIEW / NONE / NOT_RUN`。本轮不修改 authority。
- Primary Skill=`nq-dh-workflow-router`；supporting Skills=NONE。本次为实施会话，不能充当自身整改的独立 final reviewer。

## 2. P1 root cause

原 validator 按 raw YAML 行匹配 `if:` / `continue-on-error:`。YAML 的双引号 key 可以使用 Unicode escape，因此 `"continue\u002don-error"` 与 `"\u0069f"` 分别与普通 key 语义相同，却未命中 raw-text 检查。

本轮移除 workflow job/step 的 raw-text 定位与安全 key 检查。没有添加任何用于识别特定 escape 拼写的 regex/substring production rule。

## 3. Parser inventory 与选择

| Candidate | 当前事实 | Decision |
| --- | --- | --- |
| 仓库现成 YAML parser 调用 | scripts/.github/backend/research 未发现现成 semantic parser helper 调用 | 无可直接复用入口 |
| frontend yaml | package-lock 中是 Vite 的 optional peer，未形成已安装 parser 的保证；diff-check 未安装 frontend dependencies | 不选，不临时 npx/install |
| research Python | dev dependencies 为 pytest/mypy/ruff，未声明 PyYAML；diff-check 未准备 Python 包 | 不选，不临时 pip/module install |
| 既有 Java/Maven backend dependency | backend/pom.xml pin Spring Boot BOM 3.5.10；其 BOM 管理 SnakeYAML 2.4，spring-boot-starter 已依赖该 parser；required diff-check 已配置 Java 21 | 选择，复用已有依赖 |

新增 [NqWorkflowYaml.java](../../../scripts/ci/NqWorkflowYaml.java) 是本轮正式编写的 CI adapter，使用 SnakeYAML 的 `compose` node API；没有复制 artifacts 中的 review parser，也没有复制或实现 YAML parser。新增 [Read-NqWorkflowYaml.ps1](../../../scripts/ci/Read-NqWorkflowYaml.ps1) 从 root backend POM 得到 Boot version，再从本地对应 BOM 得到 SnakeYAML version，读取精确版本的既有 JAR。未新增/升级 Maven/Node/Python 依赖，未修改任何 POM 或 lock。

Reader 不联网、不安装 parser、不执行 Maven。Java 21 source launcher 在内存中编译受仓库管理的 helper，不信任或保留预编译 class cache。Canonical CI 使用标准 Maven local repository；reader 支持显式已有 cache path 供离线工具/test 使用，缺失 dependency 时明确拒绝。

## 4. Semantic model 与 enforcement

SnakeYAML 负责 YAML 语法、escape 解码与 mapping/sequence structure。Adapter 将 node graph 投影为 JSON，保留 mapping/sequence 与 decoded scalar text；不构造输入指定的 Java 对象、不执行 GitHub expression。Workflow 的 `on` key 保持字符串 `on`，不会因 YAML 1.1 boolean resolution 被改名。

Fail-closed contract：

- 非法 YAML、多个 documents、重复 decoded key、非 scalar key、merge key、alias reuse、非 core tag 均拒绝。
- 文件限制 1 MB，parser code-point limit=1,000,000、nesting limit=64、collection alias limit=0；没有 parse-error fallback。
- root/jobs/job/step 必须是 mapping；steps 必须是 sequence；name/run/shell 必须满足 scalar contract。required backend/F008 缺失、wrong type、重复 step identity 均拒绝。
- 对 required jobs 与 security-critical steps，使用解析后 mapping 中的 key **存在性**检查 `if`、`continue-on-error`。值为 false/true/expression 一律不影响拒绝，不评估 expression。
- Job/step identity、owner、ordering、run/shell/env、action/image 的 key 读取都使用同一 model。保留的 regex 用于 command/value identity 合同，或独立 production YAML 的原有限定合同；不再充当 workflow key parser。
- action version comment 仍必须匹配 lock；由 SnakeYAML 的 `uses` scalar source mark 关联实际 annotation，再校验其 value identity，未放宽历史 pinning contract。
- F008 owner 仍是 required `backend / Backend regression`，当前唯一；两个真实 selector、完整 Maven invocation、bash shell、无 failure-ignore wrapper 均保持。

## 5. ContractOnly / default 与 dependency bootstrap

[Validator](../../../scripts/ci/Test-CanonicalDeliveryWorkflow.ps1) 将原有同源 targeted Maven/fresh-report 代码移动到 `Invoke-ProductionConfigAdmission`。默认入口先运行该已存在的 mandatory capability，再调用共同的 semantic parser 与 contract checks。这样 clean CI 的原有 Maven reactor 会先准备自身已声明的 SnakeYAML dependency，parser reader 随后只读本地缓存，不引入独立 parser installation。

两种模式的唯一区别是是否执行该 Maven admission；都调用同一个 reader、job/step model 与安全检查。Default 必须同时满足 native exit/fresh reports 和 semantic contract，才输出 `CANONICAL_ADMISSION=ACCEPTED`；Maven PASS 本身不能授予 admission。ContractOnly 不执行 Maven，输出 `CANONICAL_ADMISSION=NOT_EVALUATED_CONTRACT_ONLY`，缺少预先准备的 Java/parser dependency 时 fail-closed。

当前调用方兼容性：

| Caller | Mode / dependency preparation | Result |
| --- | --- | --- |
| `.github/workflows/ci.yml` canonical validator step | default；已有 Java 21 setup，原有 targeted Maven 先准备应用依赖 | 保留 full admission，workflow 未修改 |
| validator suite baseline | default；真实 Maven + parser + contract | 两种 shell 通过 |
| workflow/configuration fixtures | ContractOnly；suite baseline 已准备依赖 | 两种 shell 通过；用抛错 mvn stub 证明没有执行 Maven |
| R06/R09/R10 source copies | default + BackendRoot | actual source mutation → Maven assertions fail → same admission rejects |
| semantic parser tests | 直接调用 offline reader | equality/negative/missing dependency proof 全通过 |

原有 source binding、删除目标两份 Surefire XML、native exit 检查、tests>0 与 failure/error/skip=0 的要求保留；没有新增业务 mutation 或 Java production marker 判断。

## 6. Permanent semantic equality 与 matrix

新增 [Test-NqWorkflowYaml.Tests.ps1](../../../scripts/ci/tests/Test-NqWorkflowYaml.Tests.ps1)，由 canonical validator suite 实际调用。

两种 PowerShell 的日志均包含：

```text
SEMANTIC_KEY_EQUALITY=continue-on-error plain=quoted=escaped
SEMANTIC_KEY_EQUALITY=if plain=quoted=escaped
YAML_SEMANTIC_TEST equalities=2 structure=PASS invalid-rejected=8 missing-dependency=REJECTED
```

Equality proof 比较实际 parsed mapping 的唯一 key，确认 plain、quoted、Unicode-escaped 三种表示完全相同；expression 保留原字符串。另验证真实 mapping/sequence structure、decoded run key/value 与 `on` key。

| Case | Mutation | PS5.1 | PS7 |
| --- | --- | --- | --- |
| S01 | plain continue-on-error=false | REJECTED | REJECTED |
| S02 | quoted continue-on-error expression | REJECTED | REJECTED |
| S03 | Unicode-escaped hyphen key | REJECTED | REJECTED |
| S04 | plain if=success() | REJECTED | REJECTED |
| S05 | quoted if expression | REJECTED | REJECTED |
| S06 | Unicode-escaped if key | REJECTED | REJECTED |
| S07 | backend job escaped soft-fail key | REJECTED | REJECTED |
| S08 | F008 step escaped if key | REJECTED | REJECTED |
| S09 | backend full regression + F008 dual escaped soft-fail | REJECTED | REJECTED |
| S10 | mixed plain/escaped soft-fail | REJECTED | REJECTED |

S01–S10 要求命中实际 required-capability condition/soft-fail rejection message，不把任意异常算作其 semantic proof。

新增 8 个完整 workflow 的 parser/critical-subtree negatives（malformed YAML、duplicate decoded key、jobs missing/sequence、backend scalar、steps mapping、step scalar、run sequence）均命中 `YAML_SEMANTIC_*` 并拒绝。另一个永久 default-mode case 重放前一轮实际 ACCEPTED 的 escaped continue-on-error exploit，真实 targeted Maven 后 semantic contract 拒绝。原有 116 项全部保留。

## 7. 本轮实际 Validation

| Validation | PS5.1 | PS7 |
| --- | --- | --- |
| canonical validator suite | exit=0；186.91s；135/135 REJECTED | exit=0；183.00s；135/135 REJECTED |
| security mutations accepted | 0 | 0 |
| semantic equality | 2/2 PASS | 2/2 PASS |
| parser/unsupported input negatives | 8/8 REJECTED | 8/8 REJECTED |
| missing parser dependency | REJECTED，无安装或 fallback | REJECTED，无安装或 fallback |
| targeted F008 baseline | 118 tests / failures=0 / errors=0 / skipped=0 | 同左 |
| R06 | 118 tests / assertion failures=17 / errors=0 / skipped=0；REJECTED | 同左 |
| R09 | 118 tests / assertion failures=50 / errors=0 / skipped=0；REJECTED | 同左 |
| R10 | 118 tests / assertion failures=12 / errors=0 / skipped=0；REJECTED | 同左 |
| authority checker | errors=0 / CURRENT_AUTHORITY_VALID | errors=0 / CURRENT_AUTHORITY_VALID |

每个 R mutation 都使用独立 source copy，通过真正的 Maven assertions failure 进入 `CANONICAL_ADMISSION_REJECTED / PRODUCTION_CONFIG_REGRESSION_FAILED`；未靠 static-string throw 替代拒绝链。

两种 shell 合计 270 个 canonical mutation executions 全拒绝。Parser test 的 8 个拒绝用例及 missing dependency proof 单列，不混入 135 的计数。

额外合法 semantic-positive fixture 将 `jobs/backend/name/run` key 转义后，PS7 ContractOnly 仍通过，输出 9 required jobs、25 capabilities、missing=0、unknown=0 与 NOT_EVALUATED_CONTRACT_ONLY，证明合法 key 表示不依赖旧 raw YAML 格式。临时 fixture 已清理。

执行环境：Java 21.0.9、Maven 3.9.12、PowerShell 5.1/7。Runner 只清除子进程继承的 NQ/SPRING/PG/JVM/Maven 配置，设置 CI/no-outbound、synthetic loopback DB aliases 与 `MAVEN_ARGS=-o`；没有运行联网 parser install、Full Maven、真实 DB、生产启动或交易调用。

验证命令：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ci/tests/Test-NqWorkflowYaml.Tests.ps1
pwsh -NoProfile -File scripts/ci/tests/Test-NqWorkflowYaml.Tests.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1
pwsh -NoProfile -File scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1
pwsh -NoProfile -File scripts/docs/check-current-authority.ps1
git diff --check
```

Logs：`artifacts/f008-yaml-semantic-remediation/suite-ps51.log`、`suite-ps7.log`、`validation-results.json`、`authority-ps51.log`、`authority-ps7.log`、`semantic-positive.log`。本轮 targeted native log 为 `backend/nq-app/target/production-config-admission.log`。

Full Maven、PG16、packaged JAR、canonical release、frontend=`NOT_REQUIRED / NOT_RUN`；remote exact-head CI=`NOT_RUN`。没有扩大 qualification。

开发期 RCA：最初 helper 的 Get-Command 返回本机 Java 21/8 两条 application path，调用失败；改为选择命令解析顺序的第一条路径后，两种 shell 的最终 parser tests/suites 均通过。若干 inventory 查询误用了 Windows shell 不展开的 wildcard 路径，已改用具体目录/文件。收尾自检读取中文 evidence 时曾使用系统默认 GBK 导致解码失败，改为显式 UTF-8 后复验。上述开发/自检错误不计入最终通过数据，没有通过临时安装工具解决。

## 8. Fingerprint、变更范围、回滚

- inherited candidate=24 files，before=`14a26713c00cb850d66dc865eb03a128d4d67ed53ec922d51bc34f2e31697fdf`。
- 同一固定 24-file set 的 after=`b623af35247b8cb1bb4ac7cc45da3b38ab9914743491e6e119e056ad9c815490`。
- 算法：按路径排序的 `path|byte-length|lowercase-sha256`，UTF-8/LF、无尾 LF，再计算 SHA-256。新增文件不混入固定 inherited set；本 evidence 不自参与指纹。
- inherited 中仅两个 CI scripts 与 TESTING/WORKLOG 改变；其余 20 个 candidate 文件逐文件 SHA-256 不变，包括 production Java/tests/config、workflow、lock、deployment/systemd、STATUS/ROADMAP/RUNBOOK 与全部此前 evidence。TESTING/WORKLOG 验证为 byte-prefix 保留，仅追加。
- 快照与本轮反向补丁来源：`artifacts/f008-yaml-semantic-remediation/before.json`、`after-inherited.json`、`before/`、`task-delta.patch`。

本轮正式文件清单：

1. `scripts/ci/Test-CanonicalDeliveryWorkflow.ps1`
2. `scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1`
3. `scripts/ci/NqWorkflowYaml.java`（新增）
4. `scripts/ci/Read-NqWorkflowYaml.ps1`（新增）
5. `scripts/ci/tests/Test-NqWorkflowYaml.Tests.ps1`（新增）
6. `docs/current/TESTING.md`（仅追加）
7. `docs/current/WORKLOG.md`（仅追加）
8. 本 evidence（新增）

没有修改 production Java/config、pom、frontend、migration、frozen archive、既有历史 evidence、current STATUS 或 task-ID matcher。P5-F007/P5-F009 继续 `OPEN / NOT_IMPLEMENTED`。

风险/影响面：ContractOnly 现在也需要 Java 与已准备的 SnakeYAML 依赖；当前真实 CI/suite 调用次序已处理这一前提。Default 调整为先执行原有 targeted Maven，再做共同的 semantic contract，任一阶段失败均不能输出 ACCEPTED。尚未进行独立 final closure review 或远端 exact-head CI，不能据此宣布 F008 CLOSED。

回滚方式：仅对上述四个已存在文件使用本轮 `task-delta.patch` 的反向补丁，并移除本轮新增三个 helper/test 与本 evidence；before snapshots 可核对。不得 reset/restore 整个 inherited candidate。未执行回滚。

## 9. Final decision 与下一步

- P0=0；唯一 P1=`REMEDIATED`，本轮验证 residual=0，待独立 final closure review。
- `IMPLEMENTED / F008_YAML_SEMANTIC_CI_ENFORCEMENT_REMEDIATED / P0_0 / P1_1_REMEDIATED / PENDING_FINAL_CLOSURE_REVIEW`。
- staged=0；commit=NONE；push=NONE。临时 mutation/parser directories 与 fixture 已清理；保留 ignored 验证 logs、snapshots、patch，未生成 Java helper class cache。
- Authority 原样保持 `IMPLEMENTED|PENDING_REVIEW / work_batch_commit=NONE / work_batch_ci_run=NOT_RUN`，machine next_action 仍为既有 F008 REVIEW；用户指定的新 task 不由本轮新增 matcher 或 authority mutation。
- 唯一下一次独立 review：`NQ-GATEAUDIT-PHASE5-F008-YAML-SEMANTIC-FINAL-CLOSURE-REVIEW`，只验证 semantic parser、escaped-key mutations、ContractOnly/default model 一致性、R06/R09/R10。禁止重跑 Full Maven/PG16/JAR、JWT/master-key/datasource 重审或广泛 fuzzing。本轮未创建该 task，也未创建新的 Attempt。
- 该独立 review 若 PASS/P0_0/P1_0，按用户指定顺序进入 commit → push → exact-head CI → post-CI authority acceptance，不再增加 F008 local Review/Attempt。本轮仍严格 NO_COMMIT/NO_PUSH。
- 建议 commit message（未执行）：`fix(ci): 使用 YAML 语义模型阻断 F008 软失败绕过`。

工具声明：使用 Git、PowerShell 5.1/7、Python、Java/Maven、已有 SnakeYAML；MCP 未使用；Skill 使用 nq-dh-workflow-router。网络访问未使用，Maven 离线；写操作仅上述八个正式文件与 ignored 验证生成物，无 stage/commit/push、系统配置、生产服务、凭证读取或真实外部副作用。
