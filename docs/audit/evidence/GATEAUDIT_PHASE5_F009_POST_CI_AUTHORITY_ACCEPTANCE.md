# GateAUDIT Phase5 F009 post-CI authority acceptance

Task：`NQ-GATEAUDIT-PHASE5-F009-POST-CI-AUTHORITY-ACCEPTANCE`。
类型：NQ_ONLY / POST_CI_AUTHORITY_ACCEPTANCE / GOVERNANCE_ONLY_COMMIT / NO_IMPLEMENTATION_CHANGE / NO_ADDITIONAL_REVIEW。

## 1. 事实来源与事件边界

本记录于2026-09-06重新读取STATUS、ROADMAP、TESTING、WORKLOG、FACT_SOURCE_INDEX及Git/GitHub evidence；不从历史记忆推断finding清单。历史技术执行是source event，本次GitHub只读核验与authority同步是新的materialization/acceptance event，不声称重新执行技术测试。Primary Skill为nq-docs-writer，nq-dh-workflow-router仅负责路由；lifecycle与matcher由既有machine contract提供。

Preflight：repository=`E:/Project/nexus-quant-gateaudit`，branch=`audit/post-gatey-agent-baseline`，starting HEAD=origin=`dbb8b9c6a2319338f5ca90b566ad494142a55e20`；指定fetch成功，worktree CLEAN、staged=0。Git ancestry证明remediation的直接parent是首次delivery，未reset/rebase/force push。

## 2. F009 local review与失败链路

- 用户已接受的local review=`CLOSED / PASS / P0_0 / P1_0`；P1-1/P1-2/P1-3均CLOSED。提交前STATUS的review记录与本轮请求一致，不追加review。原focused review结论（由用户任务书与起始STATUS承载）、[P1 remediation](GATEAUDIT_PHASE5_F009_FOCUSED_REVIEW_P1_REMEDIATION.md)、[P1-3 remediation](GATEAUDIT_PHASE5_F009_P1_3_JS_MODULE_DEPENDENCY_REMEDIATION.md)保持历史事实，不能将remediation文档中的pending snapshot当成当前review状态。
- 首次implementation/delivery commit=`85d11984d0c65b464ffe4858fe7fd1da51885f12`。
- [Failed CI 34024011663](https://github.com/ling5477/nexus-quant/actions/runs/34024011663)：head为首次delivery，completed/failure，8 success、1 failure、0 skipped、0 cancelled。唯一失败job为Repository hygiene and governance；failed step为Reject retired stage runtime assets，日志同时报告`STAGE_SEMANTICS: docs/current/ROADMAP.md`和`STALE_EXCEPTION: docs/current/ROADMAP.md`。
- 根因：precommit ROADMAP authority delta改变正式inspect() digest，但对应exception仍绑定旧值。该失败commit/run永久保留为`FAILED DELIVERY HISTORY`。
- 最小remediation commit=`dbb8b9c6a2319338f5ca90b566ad494142a55e20`；直接parent=`85d11984d0c65b464ffe4858fe7fd1da51885f12`。Git diff显示仅ROADMAP exception sha256由`f584ee079174d210ccf27681d9f15deafe235f1dd1416637d82bc9a68f6c6af9`更新为`aa600c9fec8392053b07647df8ed7c337eb41783390d55d1b3517faef2819f44`。

## 3. Immutable technical acceptance binding

```text
TECHNICAL_ACCEPTED_HEAD=dbb8b9c6a2319338f5ca90b566ad494142a55e20
TECHNICAL_ACCEPTED_CI=34024427455
status=completed
conclusion=success
required_jobs=9/9 success
failed=0
skipped=0
cancelled=0
```

[Accepted CI 34024427455](https://github.com/ling5477/nexus-quant/actions/runs/34024427455)的headSha与上述head完全一致。只读读取canonical validator的requiredJobs registry并与GitHub job names逐项核对；未运行validator的Maven admission。

| Required job | Result |
| --- | --- |
| Repository hygiene and governance | completed / success |
| Runtime safety and no-outbound | completed / success |
| Backend regression | completed / success |
| PostgreSQL and Flyway | completed / success |
| Frontend build and critical E2E | completed / success |
| Research quality | completed / success |
| Secret scanning | completed / success |
| Java architecture guard | completed / success |
| Delivery SBOM and provenance | completed / success |

本轮F009 authority由`REVIEW_ACCEPTED / READY_TO_COMMIT`同步为`ACCEPTED / CLOSED`；P0=0、P1=0是F009已接受review的范围，不表示Phase5全部P1清零。后续authority commit不能替换本technical pair。

## 4. Phase5 finding reconciliation

来源是当前[ROADMAP finding registry](../../current/ROADMAP.md)、[STATUS](../../current/STATUS.md)与append-only [TESTING](../../current/TESTING.md)/[WORKLOG](../../current/WORKLOG.md)。下表accepted technical evidence是已接受的来源，不表示本轮重新qualification。

| Finding ID | Current authority status | Accepted technical evidence | Blocking / non-blocking | Remaining action |
| --- | --- | --- | --- | --- |
| P5-F001 | LOCAL_REQUIRED_CHECK_BASELINE_ACCEPTED / REMOTE_ENFORCEMENT_NOT_APPLIED；remote NOT_VERIFIED | Phase5A `d1d20f4087cd337e0b21037b38b377bcbe25499f / 33505000903`，仅local required-check baseline | Blocking：P1；无remote closure证据或non-blocking waiver | 先取得remote enforcement显式授权与适用authority；再应用并读回验证9个required checks，保留可接受证据 |
| P5-F002 | ACCEPTED / CLOSED | Phase5B `a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848` | Non-blocking：closed | 无；pair失效才重开 |
| P5-F003 | ACCEPTED / CLOSED | Phase5B `a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848` | Non-blocking：closed | 无；pair失效才重开 |
| P5-F004 | ACCEPTED / CLOSED | Phase5A `d1d20f4087cd337e0b21037b38b377bcbe25499f / 33505000903` | Non-blocking：closed | 无；pair失效才重开 |
| P5-F005 | INTERNAL_SBOM_PROVENANCE_ACCEPTED；platform attestation DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION / id-token NOT_GRANTED | Phase5A `d1d20f4087cd337e0b21037b38b377bcbe25499f / 33505000903`，仅internal SBOM/provenance | Non-blocking：沿用已有显式延期范围，未完整closed | 保留platform attestation延期；须另有显式授权，不在本任务实施 |
| P5-F006 | ACCEPTED / CLOSED | Phase5A `d1d20f4087cd337e0b21037b38b377bcbe25499f / 33505000903`；Phase5B最新E2E baseline由`a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848`接受 | Non-blocking：closed | 无；pair或critical scope失效才重开 |
| P5-F007 | ACCEPTED / CLOSED | `0e2efdeb236c185dbace67bb22f94c6af64a563a / 34009290836` | Non-blocking：closed | 无 |
| P5-F008 | ACCEPTED / CLOSED | `614359fc7f25227f736fbb1c11c7d584da1f0627 / 33978394774` | Non-blocking：closed | 无 |
| P5-F009 | ACCEPTED / CLOSED | `dbb8b9c6a2319338f5ca90b566ad494142a55e20 / 34024427455`；local review CLOSED / P0_0 / P1_0 | Non-blocking：closed | 本次authority commit发布后无剩余F009动作 |

计数口径：total=9；完整accepted/closed=7；open/unclosed=2，其中blocked=1（F001）、deferred=1（F005）。blocked/deferred是open的子集，不能再相加；互斥分组是7+1+1=9。F001/F005部分能力已接受，不等于其所有scope已关闭。remaining Phase5 blocking findings=1（P5-F001，既有severity=P1）。

Phase5 decision=`REMAINS_OPEN`：current authority仍未证明open blocking findings=0。没有隐藏、降级、重新命名或将F001/F005并入F009。

## 5. Phase6与下一动作

Phase6 status=`DEFERRED`，implementation started=`NO`。ROADMAP要求Phase5 accepted baseline；deployment、F007/F008/F009 acceptance前置条件已满足，但Phase5 closure尚未成立，故不进一步推进Phase6 readiness。L4 failure matrix、L5/L6 qualification均未执行或接受。

```text
accepted_batch=GateAUDIT-PHASE5-F009-LEGACY-GATE-SPECIFIC-ACTIVE-ASSET-CONSOLIDATION
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=85d11984d0c65b464ffe4858fe7fd1da51885f12
accepted_batch_acceptance_head=dbb8b9c6a2319338f5ca90b566ad494142a55e20
accepted_batch_ci_run=34024427455
work_batch=GateAUDIT-PHASE5-F001-REMOTE-REQUIRED-CHECK-ENFORCEMENT
work_batch_status=BLOCKED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-GATEAUDIT-PHASE5-F001-REMOTE-REQUIRED-CHECK-ENFORCEMENT-UNBLOCK
```

既有machine contract支持`BLOCKED`及唯一`UNBLOCK` action类型；不添加字段、matcher或unsupported token。该task名称只是现有F001 remaining action的登记，不赋予remote mutation权限。F005维持单独延期；LIVE DISABLED、kill ENGAGED、其余安全字段保持。

## 6. ROADMAP stage-guard原子绑定

本轮ROADMAP正文与被inspect()检查的语义均改变。直接加载当前`check-stage-assets.py`并调用`load_policy()` / `inspect(root, path, retired)`，没有复制hash算法。

- Path：`docs/current/ROADMAP.md`。
- Old digest：`aa600c9fec8392053b07647df8ed7c337eb41783390d55d1b3517faef2819f44`。
- New digest：`169f8b7c6f907d9b67764de22ce56c6244a5b7f491dc9f3d6e12c4822efead5a`。
- Exception仅改对应sha256；path、kind=GOVERNANCE_CONTRACT、reason、owner、removalTrigger及全部其他registry内容必须语义完全相等。未删除exception、扩大wildcard、排除docs/current或关闭STALE_EXCEPTION。
- 临时mutation要求：先对当前candidate执行完整stage checker并PASS，再对ROADMAP增加可被inspect消费的无副作用fixture文本而不更新digest，完整checker必须返回`STALE_EXCEPTION: docs/current/ROADMAP.md`；finally恢复原始bytes并复验PASS。临时fixture不提交。

## 7. Governance validation

| Validation | 实际结果 |
| --- | --- |
| `python scripts/docs/check-stage-assets.py` | PASS，exit=0；1798 files、178 reviewed exceptions、errors=0；mutation恢复后再次通过 |
| `python -m unittest discover -s scripts/docs/tests -p test_stage_assets.py` | PASS，exit=0；49 tests、47 passed、2 Windows symlink privilege skips、failures=0；未执行Linux qualification |
| ROADMAP stale mutation | REJECT，exit=1；STAGE_SEMANTICS与STALE_EXCEPTION均精确指向ROADMAP；finally原始bytes恢复，重跑PASS |
| Exception semantic diff | PASS；仅ROADMAP对应sha256变化，其他全部JSON语义相等 |
| `powershell -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1` | PASS，exit=0；PS5.1 authority errors=0 |
| `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-current-authority.ps1` | PASS，exit=0；PS7 authority errors=0 |
| `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-current-authority-next-action.ps1` | PASS，exit=0；positive-actions=7、ambiguous-actions=4、safety-negative=9、schema-negative=4、whitespace-negative=5、failed=0 |
| `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-agent-workflow-fixtures.ps1` | PASS，exit=0；12/12 fixtures，malicious mutations=6/6 rejected，machine skill drift=0 |
| `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/docs/test-governance-workflow-lifecycle.ps1` | PASS，exit=0；20 passed、0 failed |
| `pwsh -NoProfile -ExecutionPolicy Bypass -File scripts/docs/check-doc-links.ps1` | PASS，exit=0；302 checked、123既有历史warnings、errors=0 |
| Append-only | PASS；TESTING/WORKLOG按Git baseline前缀逐字节核对（仅统一CRLF/LF），原记录全部保留；写入时同时确认本地原bytes是新文件前缀 |
| Active current stale claims | PASS；FACT_SOURCE_INDEX声明的active owners中，本轮F009旧pending review/commit动作已清除；historical ledger snapshots不作为current claims |
| Safety / frozen authority | PASS；schema、frozen gate/tag/commit、active Gate及全部安全字段与starting HEAD逐项完全相同 |
| Technical binding | PASS；GitHub head/status/conclusion及9个job names与canonical validator requiredJobs registry双向完全一致，bad jobs=0 |
| Scope / historical integrity | PASS；candidate仅9个allowlisted paths，runtime/build/deployment实现变化=0、既有historical evidence修改=0 |
| `git diff --check` | PASS，exit=0；范围diff已读取，暂存前staged=0；提交前另执行cached checks |

验证过程中的失败未隐藏：PS5.1首次不带进程execution-policy参数的-File调用exit=1，报`running scripts is disabled on this system / UnauthorizedAccess`，脚本未执行；用仅本验证进程的Bypass重跑通过，未修改系统execution policy。一次临时Python安全字段核对使用系统默认GBK解码Git UTF-8正文，出现UnicodeDecodeError（exit=1）；改为显式UTF-8后安全字段、stale claims和technical binding全部通过，未修改repository实现。路径探测中曾查找不存在的.githooks及工作树.git/hooks；通过`git rev-parse --git-path hooks/pre-commit`确认实际hook不存在，未安装或跳过hook。

本地原始验证日志位于ignored `artifacts/20260906-f009-authority/`，不暂存；以上结果在本evidence中物化。Mutation使用同一正式checker，复验方式如下（临时追加内容无执行副作用，结束后恢复ROADMAP原始bytes）：

```python
from pathlib import Path
import subprocess, sys
p = Path('docs/current/ROADMAP.md')
original = p.read_bytes()
try:
    p.write_bytes(original + b'\nTemporary Phase5 mutation: pwsh scripts/docs/check-current-authority.ps1\n')
    result = subprocess.run([sys.executable, 'scripts/docs/check-stage-assets.py'],
                            capture_output=True, text=True, encoding='utf-8', timeout=120)
    assert result.returncode == 1
    assert 'STALE_EXCEPTION: docs/current/ROADMAP.md' in result.stdout
finally:
    p.write_bytes(original)
assert p.read_bytes() == original
subprocess.run([sys.executable, 'scripts/docs/check-stage-assets.py'], check=True, timeout=120)
```


## 8. 范围、历史完整性与回滚

Candidate仅包括STATUS、ROADMAP、TESTING/WORKLOG追加、FACT_SOURCE_INDEX evidence mapping、本evidence、ROADMAP exception sha256。因active current stale claims检查另最小同步current README与RUNBOOK的F009旧review摘要；root README已转为owner引用，无需修改。ROADMAP残留F007 IMPLEMENT_LATER disposition同步为已接受，不重新实现F007。

不改历史F009 review/remediation/failure evidence、docs/gates、docs/archive、旧WORKLOG/TESTING记录、Java/runtime、JS parser、compatibility contracts、CI/workflow/build/deployment/release/systemd或数据库迁移。风险是把partial acceptance当complete closure，或将authority commit替换technical head；通过逐项registry、两层pair与精确allowlist防止。回滚仅反向应用本次精确文件diff，ledger用后续纠正条目保留历史，并按恢复后的ROADMAP重新计算hash、重跑治理检查；不reset/rebase/amend，不改写历史提交。

Additional technical CI=`NOT_REQUIRED / NOT_EXECUTED`：candidate仅docs/governance binding/evidence，治理检查通过后无新技术qualification要求。既有workflow自动push/PR触发仅匹配dev，本分支发布不要求额外CI；不手动dispatch。Full/targeted Maven、PG16、canonical release qualification、frontend、Playwright、Linux JS qualification、production/LIVE操作全部未执行。

## 9. 发布记录边界

授权commit message：`docs(gateaudit): accept F009 legacy asset consolidation`。精确暂存candidate，提交后在最终报告记录`F009_AUTHORITY_ACCEPTANCE_COMMIT`，parent必须为`dbb8b9c6a2319338f5ca90b566ad494142a55e20`；本文件不预填尚未产生的commit SHA或push结果。

本次authority synchronization commit由Git中新增本evidence的提交唯一定位：`git log --diff-filter=A --format=%H -- docs/audit/evidence/GATEAUDIT_PHASE5_F009_POST_CI_AUTHORITY_ACCEPTANCE.md`。这层commit绝不替换`dbb8b9c6a2319338f5ca90b566ad494142a55e20 / 34024427455`。

Final decision在本地治理验证与授权发布成功后为：`PASS / PHASE5_F009_ACCEPTED_AND_AUTHORITY_SYNCHRONIZED / P0_0 / P1_0 / PHASE5_REMAINS_OPEN`。这里P0/P1仅指F009 acceptance范围；Phase5既有P1（P5-F001）仍OPEN/BLOCKED。
