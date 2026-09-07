# Phase6 L4 C1 post-CI authority transition to C2

日期：2026-09-07。分类：`ACCEPTED_EXECUTION_EVIDENCE / NON_RUNTIME_AUTHORITY`。

本文件记录C1既有implementation、独立review与exact-head CI的正式接受。唯一machine authority仍为[STATUS](../../current/STATUS.md)，finding disposition与下一动作由[ROADMAP](../../current/ROADMAP.md)解释。本轮为`DOCS_GOVERNANCE_ONLY / NO_C2_IMPLEMENTATION / NO_ADDITIONAL_C1_REVIEW / NO_NEW_TECHNICAL_QUALIFICATION`。

## 1. 固定基线与不可变lineage

- branch=`audit/post-gatey-agent-baseline`；starting HEAD与`git fetch origin --prune`后的origin均为`eb9740b7519f48ffc1e32968cbb0950261b871ef`；初始worktree=CLEAN、staged=0。
- accepted head的parent就是C1 implementation；commit message=`fix(ci): close C1 delivery schema and diagnostic gaps`。

| 身份 | Commit / CI / 结果 | 含义 |
| --- | --- | --- |
| L4 planning pair | `d79408228ce31c97802afbb674eb2e3d0a2e7bfd / 34071672665` | ACCEPTED / CI_GREEN，仅plan/reproduction delivery |
| C1 implementation | `41c3bbcb210a65bf2b7b5aad9885d6f9e7bdccdd` | V47、versioned OCC与正确性回归 |
| C1 independent correctness review | `PASS / PHASE6_L4_C1_VERSIONED_OCC_INDEPENDENT_REVIEW_ACCEPTED / P0_0 / P1_0 / READY_FOR_C1_DELIVERY` | 既有review接受，不追加review |
| Failed delivery | `41c3bbcb210a65bf2b7b5aad9885d6f9e7bdccdd / 34086018265` | FAILED DELIVERY，保留失败事实 |
| Accepted delivery/remediation | `eb9740b7519f48ffc1e32968cbb0950261b871ef / 34098902705` | ACCEPTED / CI_GREEN，9/9 required jobs SUCCESS |
| Authority synchronization | 引入本文的独立docs-only commit | 由本轮最终交付报告记录SHA，不替代implementation或acceptance head |

来源：[C1 implementation evidence](GATEAUDIT_PHASE6_L4_RUNTIME_CORRECTNESS_C1_IMPLEMENTATION.md)的Attempt-02、[delivery remediation evidence](GATEAUDIT_PHASE6_L4_C1_DELIVERY_CI_REMEDIATION.md)第8节、[machine inventory](GATEAUDIT_PHASE6_L4_C1_DELIVERY_CI_REMEDIATION_REFERENCES.json)的closureAttempt02，以及[plan acceptance](GATEAUDIT_PHASE6_L4_PLAN_POST_CI_AUTHORITY_TRANSITION_TO_C1.md)。原文件的preflight、blocked与pending文字都是当时快照，原样保留。

独立review来源是既有任务“审查 Phase6 L4 C1 OCC 修复”，task id=`01a07a1f-09c7-7431-ba6e-36711052ef9b`，本轮只读重取其final结果。review raw fingerprint=`1ff1143f5d3ad9cf5982d1ed79fc54a09980f729e99f43c78703d0bb1094606e`；交付时记录的Git-normalized reviewed fingerprint=`fc7d96505ee0b9ce018706579c54c1bcb1fbb98c6ca8497c7a7f69c354dfdccd`。这两个值是历史review/delivery身份，不冒充本轮文档候选指纹。

本轮从implementation parent→implementation派生12个reviewed paths，逐个比较implementation与accepted head的Git blob，全部相同：`C1 reviewed bytes changed=0`。因此保留既有review有效性，不启动新C1 review。

## 2. Exact-head CI readback

只读查询[accepted run 34098902705](https://github.com/ling5477/nexus-quant/actions/runs/34098902705)：workflow=`NQ CI Baseline`，headSha=`eb9740b7519f48ffc1e32968cbb0950261b871ef`，status=`completed`，conclusion=`success`。

| Required job | Conclusion |
| --- | --- |
| Repository hygiene and governance | SUCCESS |
| Runtime safety and no-outbound | SUCCESS |
| Backend regression | SUCCESS |
| PostgreSQL and Flyway | SUCCESS |
| Frontend build and critical E2E | SUCCESS |
| Research quality | SUCCESS |
| Secret scanning | SUCCESS |
| Java architecture guard | SUCCESS |
| Delivery SBOM and provenance | SUCCESS |

required=9、success=9、failed=0、skipped=0、cancelled=0；required job名称集合无missing、unexpected或duplicate。PostgreSQL/Flyway job中的`Run current-schema backup and restore drill`、`Verify canonical backup creation and integrity`、`Verify canonical post-restore validation`均SUCCESS。

[Failed run 34086018265](https://github.com/ling5477/nexus-quant/actions/runs/34086018265)只读核验仍属于implementation且conclusion=failure；不与成功交付折叠为一条历史。

## 3. 正式正确性disposition

P1-2原缺陷为stale PLACE ACK把较新FILLED覆盖成ACCEPTED，以及stale CANCEL ACK把较新FILLED覆盖成CANCELLED。接受的修复是durable version generation加atomic status/version CAS，关闭绑定implementation、独立review与exact-head CI三者，不能表述为仅由测试修复。

既有独立review已验证以下结果；本轮只核验其身份及reviewed bytes，未重跑技术测试：

| 不变量 | 已接受结果 |
| --- | --- |
| stale PLACE | SENT/2→FILLED/3后旧ACK CAS=0，返回FILLED，Trade=1/Ledger=2保留 |
| stale CANCEL | 旧CANCEL_REQUESTED/4对应较新FILLED/6；旧ACK CAS=0，较新terminal及Trade/Ledger保留 |
| ABA | 旧代际4、新代际7，同status但version不同；旧CancelReject CAS=0，新代际保留 |
| pre-cancel race | 旧ACCEPTED/3对较新FILLED/4的CAS=0；外部CANCEL调用数=0 |
| externalOrderId | NULL→A允许，A→A幂等，A→B拒绝；identity写不递增状态version |
| 事件/审计 | stale结果不发布虚假成功ACK或成功状态迁移事实；不重试旧ACK |

| Finding | Before | After |
| --- | --- | --- |
| P1-2 | OPEN / C1 | REMEDIATED / REVIEWED / CI_GREEN / CLOSED |
| P1-3 | OPEN / C2 | OPEN / C2，唯一当前正确性目标 |
| Canonical blocking P1 | 2 | 1 |
| Reachability unknown | 0 | 0 |
| P1-1 | RETIRED_COMPATIBILITY_ONLY | RETIRED_COMPATIBILITY_ONLY |
| PB1 | RETIRED_COMPATIBILITY_ONLY | RETIRED_COMPATIBILITY_ONLY |
| PB2 | DORMANT_NO_CURRENT_ENTRYPOINT | DORMANT_NO_CURRENT_ENTRYPOINT |

P1-2测试现为`permanent correctness regressions`；P1-3仍为`KNOWN_DEFECT_REPRODUCTION`。historical findings未改写为FIXED。plan pair的68/68历史分类保持原样，不再作为当前P1-2分类。

P1-3当前事实：local Order=CANCELLED而venue truth存在fill，当前reconciliation排除/未修复该订单，Trade/Ledger未收敛；eligible-state positive control已证明普通current reconciliation可以收敛。C2目标仅为此可达冲突的bounded、idempotent、canonical fill→Trade→Ledger convergence，本轮实现=0。

## 4. Machine authority与DAG

| Field | Before | After |
| --- | --- | --- |
| accepted_batch | GateAUDIT-PHASE6-L4-FAILURE-MATRIX-PLAN | GateAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C1 |
| accepted_batch_status | ACCEPTED\|CI_GREEN | ACCEPTED\|CI_GREEN |
| accepted_batch_implementation_commit | d79408228ce31c97802afbb674eb2e3d0a2e7bfd | 41c3bbcb210a65bf2b7b5aad9885d6f9e7bdccdd |
| accepted_batch_acceptance_head | d79408228ce31c97802afbb674eb2e3d0a2e7bfd | eb9740b7519f48ffc1e32968cbb0950261b871ef |
| accepted_batch_ci_run | 34071672665 | 34098902705 |
| work_batch | GateAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C1 | GateAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C2 |
| work_batch_status / commit / ci | NOT_STARTED / NONE / NOT_RUN | NOT_STARTED / NONE / NOT_RUN |
| next_action | NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C1-IMPLEMENTATION | NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C2-IMPLEMENTATION |

使用schema 3既有字段与matcher，C2 action唯一类型为IMPLEMENTATION。写前PS5.1/PS7 authority与next-action regression通过。既有[lifecycle contract](../../../scripts/docs/governance-workflow-contract.json)没有要求在已review且CI green的C1 authority同步后再追加C1 review，也没有为此docs-only同步增加技术qualification；不修改matcher或lifecycle。

```text
L4 plan ACCEPTED / CI_GREEN
  → C1 ACCEPTED / CI_GREEN / CLOSED
  → C2 NOT_STARTED
  → Independent Correctness Review NOT_STARTED
  → B0 Harness Foundation NOT_STARTED
  → L4 Qualification NOT_STARTED
```

Phase6保持`IN_PROGRESS / NOT_FROZEN`；L4未接受、未qualified。L5/L6=`NOT_STARTED / blocked by L4`；C3仅historical/future-trigger。C2之后的current correctness cluster独立review保留；authority同步→C2 implementation之间无新C1 review。

## 5. Schema、Docker与F009保护

- 当前repository schema=V47，来自既有orders.version migration；DB schema owner继续以Flyway migrations为准。历史Phase5B技术pair=`a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848`时的V46不改写；不全局替换V46。
- 历史run 34086018265 Docker root cause=`UNCLASSIFIABLE_WITH_RETAINED_EVIDENCE`，不标记为TRANSIENT_CONFIRMED。
- new diagnostic surface=`PRESENT`；accepted exact-head PostgreSQL/Flyway job=`SUCCESS`；current-schema backup/restore drill=`SUCCESS`。含义是历史失败未在accepted head的新CI运行中复现，当前实现由该exact-head CI证明；未推断历史根因。
- F009=`ACCEPTED / CLOSED`；本轮逐字段核验implementation→accepted head的registry只有三项机械授权hash变化，完整closureAttempt02清单保留。contracts=18、members=105、approved caller edges=1559、protected paths=178；contract count、member topology、approved caller topology不变；new compatibility caller=0、enforcement semantic change=0，不重新打开F009。
- 本轮ROADMAP用未修改的`check-stage-assets.py.inspect()`计算正式摘要，before=after=`169f8b7c6f907d9b67764de22ce56c6244a5b7f491dc9f3d6e12c4822efead5a`、matches=2。path、classification、reason、scope、caller topology均不变，本轮registry字节变化=0，无需digest同步或语义例外。

## 6. 本轮治理准入

以下治理检查已实际执行，exit code均为0。原始readback、逐文件baseline备份、lineage/digest比对及检查日志保存在`artifacts/20260907-c1-authority-transition/`。TESTING/WORKLOG只追加，已有evidence、frozen history、migration、production/test Java、workflow、deployment脚本、checker、F009 registry和Gitleaks配置均不修改。

| 检查 / 命令 | 结果 |
| --- | --- |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1` | PS5.1 errors=0 |
| `pwsh -NoProfile -File scripts/docs/check-current-authority.ps1` | PS7 errors=0 |
| `pwsh -NoProfile -File scripts/docs/test-current-authority-next-action.ps1` | failed=0；C2 token使用现有唯一IMPLEMENTATION matcher |
| `pwsh -NoProfile -File scripts/docs/test-governance-workflow-lifecycle.ps1` | 20/20 PASS |
| `python scripts/docs/check-stage-assets.py` | scanned=1802、reviewed_exceptions=178、errors=0 |
| `python -m unittest discover -s scripts/docs/tests -p test_stage_assets.py` | Windows 49：47 PASS、2个既有symlink权限条件跳过；WSL Ubuntu同suite 49/49 PASS、0 skip |
| `pwsh -NoProfile -File scripts/docs/test-agent-workflow-fixtures.ps1` | 12/12 PASS；malicious mutations 6/6拒绝，capability/runtime/audit negatives均拒绝 |
| `pwsh -NoProfile -File scripts/docs/check-doc-links.ps1` | checked=436、errors=0、warnings=123（既有历史链接） |
| task-scoped `check-consistency.py` | exact paths=8、append-only=true、history变化=0、stale current claims=0、safety delta=0、reviewed bytes=0、technical changes=0 |
| pinned Gitleaks 8.18.4 | 3147个tracked safe files及新增evidence纳入扫描；exit=0、findings=0；复验缓存archive的canonical/official SHA256并提取binary，CI配置未放宽 |
| `git diff --check`及范围diff | PASS，production/test/migration/workflow paths=0 |

本表为已执行source events的materialization。更新本段及ledger后再运行最终candidate的stage、doc links、consistency、Gitleaks与diff检查；如有错误则停止提交。append-only/hash和stale检查不把历史snapshot的旧C1/P1/V46文字误判为current声明。

本轮最小文件集为STATUS、ROADMAP、FACT_SOURCE_INDEX、TESTING、WORKLOG及本文；current README/RUNBOOK因仍有C1 NOT_STARTED、canonical P1=2和旧next-action而纳入一致性修正。DB_SCHEMA未包含stale V46 current-head声明且已以migrations为事实源，无需无关修改。

## 7. 交付、安全与回滚边界

建议并授权的精确提交：`docs(gateaudit): accept Phase6 L4 C1 and open C2`。最终报告记录`C1_AUTHORITY_COMMIT=<SHA>`、parent、push、HEAD/origin和clean/staged状态；SHA不能自引用写入本commit，使用引入本文的Git commit与最终报告追溯。不amend、不force、不跳hook。

新technical qualification=`NOT_REQUIRED`；本轮不重跑Maven/PostgreSQL/frontend。push若自动触发CI则不取消，其run不替换C1 accepted technical pair；docs同步只接受已有不可变技术交付。

LIVE=`DISABLED`、kill_switch=`ENGAGED`、shadow_trading=`NOT_ENABLED`、real_provider/private_trading=`NOT_IMPLEMENTED`；其余machine safety字段逐项保持。未读credential，真实exchange调用、production DB写入、新pilot、PLACE/CANCEL/transfer/withdraw均=0。

回滚仅针对本轮八个文档文件使用文件级反向补丁并重新运行authority/链接/一致性检查；不回滚C1技术提交或V47，不改变生产事实，不重写历史。后续唯一动作是`NQ-GATEAUDIT-PHASE6-L4-RUNTIME-CORRECTNESS-C2-IMPLEMENTATION`，仅P1-3。

工具声明：外部工具为Git/gh、PowerShell 5.1/7、Python、rg、WSL与Gitleaks；MCP仅Codex task只读工具，用于读取既有独立review结论；Skill为`nq-docs-writer`，无子代理。网络限GitHub/origin readback及本轮已授权的正常push；没有真实交易或生产外部副作用。写入为八个文档与任务专用ignored artifacts；本轮未重跑Maven/PG/frontend，未执行C2、B0或L4 qualification。
