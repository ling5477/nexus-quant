# GateAUDIT Phase5 F001 remote required-check enforcement

Task：`NQ-GATEAUDIT-PHASE5-F001-REMOTE-REQUIRED-CHECK-ENFORCEMENT-UNBLOCK`。
结果：`PASS / PHASE5_F001_REMOTE_REQUIRED_CHECK_ENFORCEMENT_APPLIED_AND_VERIFIED / P0_0 / P1_0 / PENDING_AUTHORITY_ACCEPTANCE`。

## 1. Scope与授权

仅处理F001远端required-check enforcement。当前repository=`ling5477/nexus-quant`，本地branch=`audit/post-gatey-agent-baseline`；starting HEAD/origin=`dcb541c06ffe2477619f56d62ddae276ed0112a3`，指定fetch成功，starting worktree CLEAN、staged=0。未reset/rebase；不改application、workflow、F009 implementation或其他治理规则，不merge/push/tag/release、不执行生产/LIVE操作。

`REMOTE_RULESET_MUTATION_AUTHORIZED=YES`：授权来自本轮用户任务书第2节明确要求将canonical checks真正设置到远端，以及第7/10节限定的最小mutation范围；不是由GitHub token或admin权限推断授权。当前operator=`ling5477`（User ID `128130348`），repository permissions与collaborator permission两次只读核验均为admin。未读取或输出token/credential；只通过既有GitHub CLI认证发出限定API请求。

Primary evidence Skill=`nq-docs-writer`；`nq-dh-workflow-router`只路由。当前STATUS登记本F001 UNBLOCK任务；旧STATUS中的NOT_APPLIED/NOT_VERIFIED为待下一次authority acceptance更新的快照，本任务不提前关闭finding。

## 2. Target branch与单一enforcement authority

- Target branch=`dev`，完整ref=`refs/heads/dev`。
- 唯一目标由当前`governance-workflow-contract.json`的`release.expectedBranch`、当前canonical workflow的push/PR branches交叉得到；GitHub default branch独立读回同一值。目标不是从当前audit工作分支推断。
- Before repository/inherited rulesets=`[]`，effective branch rules=`[]`；legacy protection GET返回`404 / Branch not protected`，branch protected=false。没有既有canonical mechanism或冲突路径。
- 采用单一repository ruleset，便于通过GitHub effective-rule API验证实际branch匹配；没有额外建立legacy branch protection。ruleset ID=`22381941`，name=`Canonical required checks (dev)`，enforcement=`active`。

Before default branch head=`4c19cb775ebb18b4288400a5a1a402145c2fe30a`；after完全一致。GitHub repository默认分支、merge选项等相关元数据也逐项相等。

## 3. Canonical required-check identities

源头是当前`Test-CanonicalDeliveryWorkflow.ps1`中注明single source的requiredJobs registry。使用仓库正式`Read-NqWorkflowYaml.ps1`进行离线语义解析，仅调用reader，不运行Maven admission；workflow的全部job IDs/names与registry双向一致。数量9由解析得到，不作为硬编码名称来源。

Accepted CI=`34024427455`，head=`dbb8b9c6a2319338f5ca90b566ad494142a55e20`，workflow=`.github/workflows/ci.yml`，check suite=`92188618828`。GitHub run=completed/success，9个真实check runs全部completed/success，head一致；app ID `15368`与slug `github-actions`从实际check runs读取。当前workflow/validator/machine contract与accepted technical head间无diff。

| Canonical job ID | Required context | Integration ID |
| --- | --- | --- |
| `diff-check` | Repository hygiene and governance | `15368` |
| `no-outbound-guard` | Runtime safety and no-outbound | `15368` |
| `backend` | Backend regression | `15368` |
| `postgres-flyway` | PostgreSQL and Flyway | `15368` |
| `frontend-critical` | Frontend build and critical E2E | `15368` |
| `research` | Research quality | `15368` |
| `secret-scan` | Secret scanning | `15368` |
| `java-engineering-shadow` | Java architecture guard | `15368` |
| `delivery-provenance` | Delivery SBOM and provenance | `15368` |

stale identities=0、duplicate contexts=0。F009 stage checker与guard tests仍在`diff-check` job内，属于Repository hygiene and governance这个已存在required context；没有另造F009 context。

## 4. Before-state、mutation与保留边界

Before-state captured at `2026-09-06T10:28:18.064271+00:00`；包含repository/principal权限摘要、目标来源、原始branch响应、ruleset列表、protection 404原始响应、effective rules及授权来源。Hash=`343c6223ea04f308d94b8c15c69e714e32e3c651ecedfdb25af823787cdafa23`。

执行API：`POST /repos/ling5477/nexus-quant/rulesets`，唯一提交payload见[mutation-request.json](GATEAUDIT_PHASE5_F001_REMOTE_REQUIRED_CHECK_ENFORCEMENT/mutation-request.json)。该POST成功响应与之后独立GET分别保存，不以创建响应代替验证。

只创建一条`required_status_checks`规则，绑定9个context及实际GitHub Actions integration ID；condition精确include=`refs/heads/dev`、exclude=[]，enforcement=active，bypass_actors=[]。

`strict_required_status_checks_policy=false`：before没有strict/update-branch要求，本任务不额外引入分支更新策略。`do_not_enforce_on_create=false`：没有创建时豁免。不添加PR review count、force-push/deletion规则、signed commits、linear history、merge queue、bypass actors、branch creation restriction或其他规则；unrelated remote policies changed=0。

## 5. Independent read-after-write与effective-rule proof

Readback at `2026-09-06T10:29:37.566179+00:00`，全部真实执行：

1. GET ruleset `22381941`，逐项比较name/target/enforcement/bypass/conditions与request，确认仅1条required-status-check规则。
2. GET `/repos/ling5477/nexus-quant/rules/branches/dev`：返回唯一required-status-check rule，来源ruleset ID与新建ID一致；expected/actual contexts及integration IDs完全相同，strict/create语义保持。
3. GET所有repository/inherited rulesets：仅本次1个active ruleset；GET legacy protection仍为404；branch protected=true。
4. GET branch和repository metadata：default branch commit SHA及其他policy元数据与before相等。

Configuration=PASS；effective target branch matched=YES；required-check rule effective=YES；enforcement=ACTIVE；expected=9、actual=9、missing=0、unexpected=0、duplicate=0。

Non-destructive negatives仅在本地比较：remove-one-check synthetic集合与effective remote集合不同，精确缺1项；synthetic unknown/stale context不在actual集合中，添加后比较不相等。未修改远端来制造失败，没有临时branch/PR、broken push、merge或disable操作。当前F001任务只要求effective-rule proof，没有额外行为probe，因此不执行破坏性证明。

## 6. Rollback材料与状态

before-state是空ruleset/effective rules和不存在的legacy protection。精确回滚仅删除本轮创建的ID `22381941`，保留任何其他独立变更：

```powershell
gh api --method DELETE repos/ling5477/nexus-quant/rulesets/22381941
gh api 'repos/ling5477/nexus-quant/rulesets?includes_parents=true&per_page=100'
gh api repos/ling5477/nexus-quant/rules/branches/dev
gh api repos/ling5477/nexus-quant/branches/dev/protection
```

这些是回滚命令记录，未执行。预期恢复rulesets=[]、effective rules=[]、legacy protection仍404；与captured before-state比较。如出现并发外部变更，不能删除其他ruleset，应保留并报告差异。

Rollback material=COMPLETE；required=NO；executed=NO；verified=NOT_REQUIRED（没有声称实际执行rollback测试）。

## 7. Evidence artifacts

- [before-state.json](GATEAUDIT_PHASE5_F001_REMOTE_REQUIRED_CHECK_ENFORCEMENT/before-state.json)：mutation前完整相关状态。
- [expected-checks.json](GATEAUDIT_PHASE5_F001_REMOTE_REQUIRED_CHECK_ENFORCEMENT/expected-checks.json)：registry/accepted check-run identities与来源。
- [mutation-request.json](GATEAUDIT_PHASE5_F001_REMOTE_REQUIRED_CHECK_ENFORCEMENT/mutation-request.json)：精确创建payload。
- [mutation-response.json](GATEAUDIT_PHASE5_F001_REMOTE_REQUIRED_CHECK_ENFORCEMENT/mutation-response.json)：真实创建响应及ID。
- [after-state.json](GATEAUDIT_PHASE5_F001_REMOTE_REQUIRED_CHECK_ENFORCEMENT/after-state.json)：独立configured/effective/branch readback。
- [verification.json](GATEAUDIT_PHASE5_F001_REMOTE_REQUIRED_CHECK_ENFORCEMENT/verification.json)：精确比较、负向比较与结果。
- [rollback-plan.json](GATEAUDIT_PHASE5_F001_REMOTE_REQUIRED_CHECK_ENFORCEMENT/rollback-plan.json)：已绑定新建ID的精确恢复步骤与验收。
- [manifest.json](GATEAUDIT_PHASE5_F001_REMOTE_REQUIRED_CHECK_ENFORCEMENT/manifest.json)：上述7个JSON的SHA-256；不包含credential。

这些文件是本次remote operation与readback的source evidence，报告是同一次执行的materialization。后续authority acceptance应重新核验其来源与当前远端状态，不能将F009 technical pair替换为本任务的治理证据。

## 8. Local governance validation

| Validation | 实际结果 |
| --- | --- |
| Remote before-state captured | PASS，mutation前保存；7个JSON source artifacts由manifest校验7/7一致 |
| Authorization / permission | PASS；用户任务指令与admin执行能力分开记录 |
| Remote mutation response | PASS，一次POST；ruleset ID=22381941 |
| Configured read-after-write | PASS；唯一rule与payload逐字段一致 |
| Effective target-branch rules | PASS；唯一rule来自22381941，target refs/heads/dev实际匹配，ACTIVE |
| Expected / actual | PASS；9/9；missing/unexpected/duplicate=0；app绑定15368 |
| Local negative comparison | PASS；remove-one差1项，unknown context不在actual中且加入后比较失败 |
| Rollback material | COMPLETE；未需要或执行rollback，不声称rollback实测通过 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1` | PASS，exit=0；PS5.1 errors=0 |
| `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1` | PASS，exit=0；PS7 errors=0 |
| `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-current-authority-next-action.ps1` | PASS，exit=0；positive-actions=7、ambiguous-actions=4、safety-negative=9、schema-negative=4、whitespace-negative=5、failed=0 |
| `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-doc-links.ps1` | PASS，exit=0；checked=313、warnings=123既有历史引用、errors=0 |
| Append-only | PASS；TESTING/WORKLOG保留原始本地bytes前缀，Git baseline比较仅归一CRLF/LF；历史记录无变化 |
| Scope / authority preservation | PASS；11个evidence/ledger文件；STATUS/ROADMAP、application/workflow/scripts、历史evidence均未修改；staged=0 |
| Credential leakage | 0；仅API JSON body，未捕获认证headers；新artifact与追加文本的GitHub credential/private-key等值模式匹配=0，不读取本机凭证文件 |
| `git diff --check` | PASS，exit=0 |

上述验证均为本次实际执行，未运行Maven/PG16/frontend/Playwright/F009 guard qualification/new application CI。两个PowerShell进程的Bypass只作用于当前验证进程，没有系统策略写入。只读discovery中legacy protection的404为预期“未保护”状态，不是成功mutation；最初reader路径探测查找的Read-WorkflowYaml.ps1不存在，随后使用正式Read-NqWorkflowYaml.ps1成功解析，未新增parser。

STATUS/ROADMAP/current machine authority保持原样，TESTING/WORKLOG仅追加；本任务没有commit/push授权，不暂存或提交本地证据。工作区保留本次11个文件的可审查变更；本地日志在ignored artifacts/20260906-f001-remote，未暂存。主要风险为后续远端规则漂移；下一authority acceptance应重新读取有效规则。新technical CI未要求/未执行，未执行merge或故障push行为probe。

工具：Git/GitHub CLI、PowerShell、Python用于只读解析、限定API调用与evidence验证；MCP未使用；Skills为nq-dh-workflow-router/nq-docs-writer；网络仅GitHub，外部mutation只有上述ruleset创建。

## 9. Acceptance boundary与下一任务

本任务仅为REMOTE ENFORCEMENT APPLIED / VERIFIED / PENDING_AUTHORITY_ACCEPTANCE。没有写P5-F001 ACCEPTED/CLOSED，没有写Phase5 CLOSED或Phase6 READY；F005仍DEFERRED/NON_BLOCKING，F009固定technical pair仍为`dbb8b9c6a2319338f5ca90b566ad494142a55e20 / 34024427455`。

唯一下一任务：`NQ-GATEAUDIT-PHASE5-F001-POST-REMOTE-AUTHORITY-ACCEPTANCE`。该任务负责F001正式关闭、再次重算Phase5 findings并判断Phase5 closure/Phase6 readiness；本任务不实施Phase6。
