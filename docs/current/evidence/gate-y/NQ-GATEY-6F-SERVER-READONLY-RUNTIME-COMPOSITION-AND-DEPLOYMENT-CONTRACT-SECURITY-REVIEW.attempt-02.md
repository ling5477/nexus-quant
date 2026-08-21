# NQ-GATEY-6F server read-only runtime composition and deployment contract Security Review attempt-02

## 1. Review decision

FAIL / GATEY_6F_SECURITY_REVIEW_ATTEMPT_02_REJECTED / P0_P1_BLOCKERS_REMAIN / NOT_READY_TO_COMMIT / NO_DEPLOYMENT（失败 / Attempt-02 安全审查拒绝 / P0/P1 阻断仍存在 / 不可提交 / 不部署）。

Attempt-01 的 HardLink、full context bootability 与 Linux installation 主要测试路径已重新通过；但新的 receipt/provenance 体系可由调用者自行铸造可信 producer 与 digest，并可在没有 activation/current pointer/实际 health probe 的情况下获得 POST_ACTIVATION_ACCEPTED。另发现 GateY 阶段语义被写入 production class、error code、profile 与跨模块负向排除，安全闭包依赖临时阶段字符串。P0=1、P1=1，不能进入 commit。

## 2. Baseline、authority 与 worktree provenance

- branch=dev；HEAD == origin/dev == 2605a20e9de3a6ef2cacc3118a353942fa74b2b1；baseline CI=32041844923 / attempt 2 / completed / success。
- 三份 evidence 独立展开 expected changed set；actual=32、expected=32、missing/extra=0/0。
- 初始发现 staged=5，与任务 hard baseline 不符；5 条均在 expected set 内。仅执行精确 git restore --staged 使 staged=0，未改工作区文件内容。
- STATUS authority 检查 errors=0；GateY-6F=NOT_STARTED、first real order/micro-live=NOT_AUTHORIZED、soak=NOT_STARTED、LIVE=DISABLED、kill=ENGAGED，均未改变。
- Attempt-01 FAIL evidence 与 remediation evidence 未改写。

## 3. P0-01 Release link/inode review

PoCs：

- Windows PowerShell 5.1 与 7 canonical regression 均为 22/22 PASS。
- disposable Linux canonical verifier 为 22/22 PASS；external HardLink、parent symlink traversal、artifact tamper、cross-release receipt negatives 均拒绝。
- disposable Linux installer 为 13/13 PASS；post-verification hardlink swap、source mutation independence、wrong owner/mode/world-writable、existing release、previous release preservation 与 atomic current均覆盖。
- reviewer-created FIFO 返回 BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION。
- reviewer-created service user move current 返回 exit=1。

Verifier/installer analysis：

- Linux verifier 实际读取 type、nlink、owner/mode；Windows 使用 fsutil hardlink list，未知平台 fail-closed。
- installer 使用独立 copy 并对 stage/final 重新验证；service user 无 release/current parent 写权限。

Result：原 RELEASE_INTEGRITY_BYPASS 未复现；该 original finding 关闭。

## 4. P0-02 Rollback provenance review

Receipt graph实际实现为：

release manifest → caller-created FLYWAY_HISTORY/COMPATIBILITY/BACKUP_VERIFICATION/RESTORE_VERIFICATION/HEALTH object → canonical SHA-256 → evaluator。

这不是可信 provenance chain。New-GateYDeploymentReceipt 是公开 export，调用者可任意传入 Fields；函数本身固定写入 nq-gatey-*-verifier producer identity/version 并计算合法 digest。Read/Assert 只验证该调用者刚生成的自洽内容，没有验证 backup artifact、Flyway query、compatibility proof、installation receipt 或真实 health producer。

独立 forgery PoCs：

1. caller 选择 COMPATIBLE + CALLER_ASSERTION proof，得到 codeRollback=ALLOWED。
2. caller 虚构 backup SHA/size/owner/mode/tool 与 restore checks，得到 VERIFIED_BACKUP_AND_RESTORE_REQUIRED。
3. disposable Linux 中构造 root-owned release，但不安装、不创建 current pointer、不执行 health probe；caller 生成 HEALTH receipt 后，evaluator 返回：

    PASS / GATEY_READONLY_POST_ACTIVATION_ACCEPTED
    contractState=POST_ACTIVATION_ACCEPTED
    currentPointerExists=false
    realHealthProbeExecuted=false

Result：P0 / ROLLBACK_CONTRACT_UNPROVEN。receipt tamper/mismatch 负例虽会拒绝，但不能弥补 caller 具有 minting capability 的根因。

## 5. P1-01 Production Spring context review

- Maven 中 NexusQuantApplication + gatey-readonly-qualification 真实 component scan 成功启动。
- guarded trusted authority=1；SpotExecutionProviderPort=0；TradingAdapter=0；worker/recovery/business scheduler/private WS=0；startup datasource/credential/decrypt/OKX counters=0。
- 默认 lightweight regression 保持 unavailable authority=1、trusted authority=0、mutation runtime=0。

Result：原 QUALIFICATION_PRODUCTION_CONTEXT_NOT_BOOTABLE 关闭。

## 6. P1-02 Linux installation contract review

- root ownership、directory/file mode、regular-file/nlink、service-user denial、no-overwrite、atomic current、previous release preservation均有 disposable Linux 验证。
- installer 不使用 Git checkout、server build 或 chmod 777。

Result：原 RELEASE_INSTALLATION_CONTRACT_UNENFORCED 关闭。

## 7. New P1 — stage semantic security-boundary coupling

GateYReadonlyQualificationConfiguration、GateYReadonlyQualificationRuntimeIdentity、GateYReadonlyQualificationObservationAuthority 以及 GATEY_READONLY_QUALIFICATION_KILL_SWITCH_REQUIRED 被写入 production app/infra。gatey-readonly-qualification 同时出现在 application YAML、ExchangeAdapterConfiguration、两个 API controller、八个 scheduler/recovery/maintenance components 与 validation configuration。

安全隔离依赖分散的 negative @Profile denylist，而不是可复用的 capability boundary。最初 full context 已先后遗漏 catalog、Binance recovery、maintenance 和 controller consumers，说明该模型需要不断手工补齐。未来新增 component 默认不会被 capability contract 阻止，重命名/结束 GateY 也可能误使 mutation consumer 回归。

Result：P1 / STAGE_SEMANTIC_SECURITY_BOUNDARY_COUPLING。该 finding 直接响应“创建的类加入阶段语义”的架构/安全问题。

## 8. Architecture hygiene

- 未发现第二 credential source、第二 execution port、Controller 到 OKX transport 或反向 module dependency。
- 上述 GateY-specific production runtime model 与分散 profile exclusion 是阻断性例外。

## 9. Validation

| Check | Result |
| --- | --- |
| mvn -f backend/pom.xml test | PASS；1547 tests，failures/errors/skipped=0/0/48 |
| GateY PS5.1 / PS7 | PASS；22/22 / 22/22，manifest hash一致 |
| disposable Linux verifier / installer | PASS；22/22 / 13/13，network=none |
| reviewer FIFO / current move | PASS；FIFO rejected；service user current move denied |
| GateW | PASS；34/34 |
| migration inventory | PASS；V1-V41、41 files、target V41、migration/GateW diff=0 |
| authority / doc links / diff | PASS；authority errors=0；links=361/14 warnings/0 errors；git diff --check=0 |

首次 annotation scan 因 glob 不匹配返回全零，未作为证据；修正后审计到 Component=33、Configuration=28、Bean=80、Profile=27、ConditionalOnProperty=20、ApplicationRunner=6、SmartLifecycle=4、Scheduled=7、EventListener=1，并逐项复核 qualification relevant paths。

## 10. Findings

- P0：1 — ROLLBACK_CONTRACT_UNPROVEN / caller-minted receipt producer and post-activation acceptance forge。
- P1：1 — STAGE_SEMANTIC_SECURITY_BOUNDARY_COUPLING / GateY stage semantics与分散 negative profile denylist进入生产 runtime。
- P2：default context 仅有 lightweight regression，不是完整 NexusQuantApplication default-profile proof；receipt verifier仍无 stable-open identity，installer post-copy check降低但不消除 standalone verifier race surface。
- P3：full context test Javadoc 仍称替换 typed transport probe，但当前只替换 DataSource；测试说明漂移。

## 11. Side-effect counters

    Server SSH read/write = 0/0
    Deployment = 0
    Production Migration = 0
    Production Backup/Restore = 0/0
    Systemd/server symlink change = 0/0

    Credential metadata/material read = 0/0
    Decrypt = 0

    OKX GET/POST = 0/0
    PLACE/CANCEL = 0/0
    Transfer/Withdraw = 0/0

    ExecutionIntent/ExecutionReceipt delta = 0/0
    Order/Ledger delta = 0/0

    LIVE enable = 0
    Kill disengage = 0

Disposable Linux side effects只发生在 cached image、network=none、/tmp 临时文件系统与容器内临时用户；非 server/production mutation。

## 12. Required remediation direction

1. 删除公开通用 receipt minting path；由实际 Flyway/backup/restore/health verifier 在受控权限边界生成 receipt，并验证实际 artifact/query/process/current identity。
2. POST_ACTIVATION 必须绑定 installed release/current pointer/installation receipt 和真实 loopback health evidence，不能接受 caller Fields。
3. 把 GateY stage naming 从 production classes、profile、error code 和跨模块排除移除；建立 capability-neutral、集中 fail-closed runtime condition，并由 full default/qualification context tests覆盖。

Commit recommendation：DO NOT COMMIT。

Next concrete action：NQ-GATEY-6F-SERVER-READONLY-RUNTIME-COMPOSITION-AND-DEPLOYMENT-CONTRACT-P0-P1-REMEDIATION-ATTEMPT-02。
