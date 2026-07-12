# NQ-GOVERNANCE-WORKFLOW-CONSOLIDATION-REVIEW attempt-01

## Review baseline

- Worktree：`E:\Project\nexus-quant-governance`。
- Branch：`refactor/governance-workflow-consolidation`。
- Base：`HEAD == origin/dev == de14c7d29975151bb30ff49b10eee9625394ef02`。
- Base CI：`NQ CI Baseline` run `29191985718`，`completed / success`，`headSha` 精确匹配。
- Review 前 staged 为空；正确计入 untracked 后 implementation scope 为 15 paths、extra 0、missing 0。
- GateV tag baseline：annotated object `06d5fea2af1765f143f277b111358b3abd8171ce`，peeled commit `530ce4e2bde416aa61944262cbfbadca556656cb`。

## Inspected files

审查了唯一 contract、shared helper、Archive/Authority/Release checker、三个 regression entry、canonical governance 文档、current evidence policy、implementation evidence、current authority、GateV archive manifest/roles，以及 README/FACT_SOURCE_INDEX/TESTING/WORKLOG 的本轮 diff。未扫描 credential、generated、业务模块或无关 archive。

## Canonical contract review

- `governance-workflow-contract.json` 是普通任务、高风险任务、Freeze、合法 status、status/action mapping、commit/CI field policy、evidence path/extension/body policy、release identity 与 hard blocker 的唯一完整机器源。
- Production checker 不再暴露 `ContractPath`；只加载自身目录下的 canonical contract。
- Helper 精确接受 `schemaVersion=1.0.0`、`authoritySchema=3` 与固定 `origin/dev`/`NQ CI Baseline` release identity；未知版本或不兼容 release identity fail-closed。
- Checker 外未发现第二套完整生命周期状态表；局部 required fields、安全事实、错误码与输出 token 属于职责内常量。
- Contract 不包含 GateV/GateW 专属状态分支。

## Checker responsibility review

- Archive checker只读取 manifest/contract、枚举 archive role/path/link/evidence，并在显式 post-tag compatibility 参数下单向委托 Release checker；不读取 authority/work batch/next_action，不实现 tag、remote 或 CI 判断。
- Authority checker只读取 STATUS schema v3、active/accepted/work batch、字段格式、action mapping 与固定安全边界；不枚举 archive，不执行 Git/tag/remote/`gh`。
- Release checker只读取 canonical release identity与 STATUS release commit声明，并检查 commit object、fresh remote `origin/dev` alignment/ancestry、exact-HEAD CI、annotated local tag、local/remote object及 peeled target；不读取 archive role或 work batch。
- Dependency graph 只有 Archive → Release 的可选单向委托；不存在 Release/Authority → Archive 或循环调用。

## Lifecycle review

- Ordinary：`NOT_STARTED → IMPLEMENTED|SELF_REVIEWED → COMMITTED|CI_PENDING → ACCEPTED|CI_GREEN`；无需独立 review，跳过实现和倒退均失败。
- High risk：`NOT_STARTED → IMPLEMENTED|PENDING_REVIEW → REVIEW_ACCEPTED|READY_TO_COMMIT → COMMITTED|CI_PENDING → ACCEPTED|CI_GREEN`；未 review 直接 accepted 失败。
- Freeze：candidate 可由 `IMPLEMENTED|PENDING_REVIEW` 进入，不要求独立 `REVIEW_ACCEPTED` authority commit；CI/tag/remote 由 Release checker fail-closed。
- Active Gate固定为 `IN_PROGRESS|NOT_FROZEN`；GateW planning 未开始继续由 `work_batch_status=NOT_STARTED` 表达。

## Evidence security review

- Current 与 archive task-evidence path均由 contract anchored regex约束；Windows separator会先 canonicalize，absolute path、`..`、单/双 URL encoding traversal、非法名称与可执行扩展名均失败。
- Archive evidence在 role matching前分类；nested README 是 non-role，顶层 README仍是唯一 archive-entry，approved evidence不触发 unknown，approved root外文件继续失败。
- `.md` 是唯一安全扩展名；empty、whitespace-only、`TODO/TBD/PLACEHOLDER` 与过短 attempt fail-closed。
- ReparsePoint/symlink fail-closed；不同 attempt编号可共存。
- Implementation evidence与本 review evidence文件名均符合 policy；implementation evidence hash在 review 前后保持 `0e9c8d416ee7d5e0980329efe05b3402523d2a9e`。

## Release checker security review

- `Gate` 有 `ValidatePattern`；tag必须与 Gate canonical tag精确一致；commit必须是 40 位 hex，并与 canonical STATUS声明一致。
- Remote/branch不能由调用者覆盖，固定为 contract中的 `origin/dev`；fresh `ls-remote` branch head必须与本地 tracking ref一致后才做 ancestry。
- Git/`gh` 参数通过 argument array传递，不使用 `Invoke-Expression`、动态 script text、force、tag create/delete/move。
- `gh` 缺失、认证/网络失败、JSON无效、无 exact `headSha`、非 `completed/success` 均失败；provider错误正文不回显，避免认证信息泄漏。
- Annotated type、local object/peeled target、remote object/peeled target全部独立验证；lightweight、cross-Gate tag、wrong target、remote missing、STATUS commit conflict和 stale tracking ref均有负向 fixture。

## Minimal fixes during review

1. Contract helper增加 exact version/schema/release identity兼容检查；三个 checker移除可替换 canonical contract的 `ContractPath`。
2. Release checker固定 `origin/dev`与 canonical STATUS/tag binding，增加 fresh remote branch alignment，拒绝 caller override与 stale tracking ref，并收口错误输出。
3. `next_action` regex锚定末尾，拒绝 action suffix/宽泛 `WAIT.*CI`/`UNBLOCKED`。
4. Evidence body policy拒绝 whitespace、placeholder与过短 attempt；补齐 absolute/double-encoded/executable/review-attempt fixtures。

以上均为原 15-path范围内的 P1 最小关闭；未新增第二份 contract、第二个 release checker或额外 governance文档。

## Validation

- PowerShell parser：7/7 changed scripts PASS。
- `GOVERNANCE_LIFECYCLE_REGRESSION`：PASS。
- `TASK_EVIDENCE_POLICY_VALID`：PASS。
- `GATE_ARCHIVE_MANIFEST_REGRESSION`：PASS。
- `CURRENT_AUTHORITY_NEXT_ACTION_REGRESSION`：PASS。
- GateV Authority：`CURRENT_AUTHORITY_CONSISTENT`。
- GateV post-tag Archive：`ARCHIVE_MANIFEST_COMPLETE`，12 roles、0 warnings、0 errors。
- GateV Release：`GATE_RELEASE_VALID`；CI run `29191677441` exact-HEAD green。
- Doc links：current 52 checked / 1 existing historical warning / 0 errors；GateV 12 checked / 0 warnings / 0 errors。
- GateV local/remote tag object与 peeled target保持不变。

## Findings

- P0：0。
- P1：0（review中发现的4组 fail-open已在原范围最小关闭并完成全量复测）。
- P2：`docs/current/TESTING.md` 保留1个既有 GateJ historical ledger link warning；不是本 diff引入。Release checker依赖可用的 Git/remote/`gh`，不可用时按设计 fail-closed。
- P3：0。

## Rollback

提交前只恢复本 review修改的 contract/helper/checker/test路径，并删除本 review evidence、恢复 evidence index；不得使用覆盖其他未提交实现的 `git reset --hard`。提交后使用独立 revert commit；不得移动、删除或覆盖任何 release tag。

## Final decision

`PASS / REVIEW_ACCEPTED / READY_TO_COMMIT`（通过 / 复核已接受 / 可进入提交）。P0=0、P1=0。

## Next action

`NQ-GOVERNANCE-WORKFLOW-CONSOLIDATION-COMMIT-AND-PUSH`。治理提交取得 exact-HEAD CI green 后，进入 `NQ-GATEW-PLAN-IMPLEMENTATION`；不再新增 governance freeze、final review或 addendum。
