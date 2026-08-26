# NQ-GATEY-6F server read-only runtime composition and deployment contract P0/P1 remediation attempt-01

## 1. 最终结论

IMPLEMENTED / GATEY_6F_P0_P1_REMEDIATION_COMPLETE / RELEASE_HARDLINK_BYPASS_CLOSED / ROLLBACK_EVIDENCE_PROVENANCE_ENFORCED / QUALIFICATION_PRODUCTION_CONTEXT_BOOTABLE / LINUX_INSTALLATION_CONTRACT_ENFORCED / P0_0 / P1_0 / PENDING_INDEPENDENT_SECURITY_REVIEW（已实现 / GateY-6F P0/P1 修复完成 / 等待独立安全审查）。

本结论仅覆盖本地未提交实现与 disposable Linux 验证。未执行 server SSH、部署、production migration、production backup/restore、systemd、production symlink、credential、OKX/Binance、PLACE/CANCEL、资金操作、LIVE enable 或 kill disengage。GateY-6F 继续为 NOT_STARTED（未开始）。

## 2. Baseline 与范围

- branch=dev；HEAD == origin/dev == 2605a20e9de3a6ef2cacc3118a353942fa74b2b1。
- baseline CI=32041844923 / attempt 2 / completed / success。
- 原 implementation evidence 与 Security Review attempt-01 的 FAIL / P0_2 / P1_2 / NOT_READY_TO_COMMIT 历史保持原样。
- 修复范围：GateY release verifier/builder/deployment evaluator/Linux installer/PowerShell regression，qualification production composition 与完整 Spring context regression，最小 current evidence。
- 排除：GateW frozen files、migration、frontend、Python、CI workflow、governance contract、真实 server/runtime/provider/credential/trading。

## 3. P0-01 HardLink remediation

实现：

- Test-GateYReadonlyRelease 对 release root、manifest、每个 ancestor 与 artifact 执行 link/reparse 检查。
- Windows 使用 fsutil hardlink list 证明 link count；Linux 使用 stat --format=%F|%h|%U|%a|%d|%i 证明 regular file、nlink == 1、device/inode identity。能力缺失时统一 fail-closed。
- path contract 拒绝 dot segment、parent segment、空 segment、double slash、trailing slash 与 case-normalized collision。
- Linux installer 在 source pre-verification 后逐文件独立 copy，copy 前复核 source identity/hash，copy 后对 stage 与 final release 重新验证 hash、size、nlink、owner、mode；不使用 hardlink。

永久负向测试：

- release root 外文件到 release 内 HardLink：BLOCKED / RELEASE_LINK_INTEGRITY_VIOLATION。
- candidate verification 后替换为外部 HardLink，再安装：拒绝，current 不移动。
- 安装后修改 source：installed SHA-256 不变、inode independent、nlink == 1。
- parent junction/symlink traversal、installed hardlink：拒绝。
- 普通独立 regular file：通过。

结果：RELEASE_HARDLINK_BYPASS_CLOSED。

## 4. P0-02 Rollback evidence remediation

Receipt model：

- canonical gatey-deployment-receipt.v1，类型为 FLYWAY_HISTORY、BACKUP_VERIFICATION、RESTORE_VERIFICATION、COMPATIBILITY、HEALTH。
- 每份 receipt 绑定 fixed producer identity/version、release manifest、schema/artifact/proof identity，并对去除 receiptSha256 后的 canonical bytes 计算 SHA-256。
- backup 绑定 sanitized database identity、Flyway source、artifact hash/size/time/owner/mode/tool/version。
- restore 绑定同一 backup digest、disposable target、start/end/result、restored Flyway 与 integrity checks。
- compatibility 绑定 previous release/manifest、target release/manifest/schema、decision、proof type/digest/time。

Provenance validation：

- pre-deployment 只接受 flyway-history + compatibility + conditional backup/restore receipt chain；裸 backupVerified、restoreProcedureVerified、previousReleaseCompatibleWithTargetSchema 已移除。
- compatibility 无法证明 previous release 与 migrated schema 兼容时，backup + restore receipt 自动成为 hard gate。
- health 从 pre-deployment 移除；仅 POST_ACTIVATION 阶段接受独立 HEALTH receipt。
- 状态拆分为 BUILT_VERIFIED → PRE_DEPLOYMENT_READY → INSTALLED_VERIFIED → POST_ACTIVATION_ACCEPTED。

负向测试覆盖：receipt 缺失、canonical/digest 篡改、backup database identity 不匹配、restore 使用不同 backup、compatibility 属于其他 release、health mutation counter 非零。

结果：ROLLBACK_EVIDENCE_PROVENANCE_ENFORCED。

## 5. P1-01 Production qualification context remediation

根因：

- 被排除的 concrete exchange adapter 仍被 catalog sync、recovery、maintenance 与 controller consumers 强依赖。
- 选择性 ApplicationContextRunner 未覆盖 NexusQuantApplication 的全包 component scan。

Composition changes：

- qualification profile 关闭 AdapterInstrumentCatalogSyncService、BinanceRecoveryService、SchedulerTradingMaintenanceService 以及 trading/instrument controllers。
- 复用既有 profile/property 关闭 OKX/Binance adapter、REST reconcile、recovery、ledger/paper/validation scheduler 与 private WS；未提供 fake production adapter。

Full application context：

- NexusQuantApplication + gatey-readonly-qualification 使用真实 component scan 启动成功。
- trusted guarded authority=1；SpotExecutionProviderPort=0；TradingAdapter=0；execution worker/recovery/business scheduler/private WS beans=0。
- CountingDataSource startup connection=0，因此 startup credential metadata/material/decrypt=0/0/0。
- OKX no-outbound selector count=0，因此 startup OKX GET/POST=0/0。

结果：QUALIFICATION_PRODUCTION_CONTEXT_BOOTABLE。

## 6. P1-02 Linux installer/POSIX remediation

- 新增 root-only Linux GateY installer；production root 固定 /opt/nexus-quant，仅显式 disposable-test switch 允许 /tmp/**。
- release root、directory、manifest 与 artifacts 实际 owner/mode 均由 post-install verifier 读取；directory=0755，artifact 使用 manifest exact mode，owner=root。
- service user 通过 runuser ... test -w 证明不能写 release。
- existing release fail-closed，不覆盖；stage 与 final 都完整复验。
- current 使用同文件系统 temporary symlink + mv -T -f 原子替换，不执行 rm current。
- failed install/verification 不移动 current；成功切换后 previous release 保留。

Disposable Linux 结果：13/13 PASS，覆盖 owner/mode/world-write、service-user denial、hardlink、source mutation independence、no-overwrite、atomic current 与 previous release preservation。

## 7. Architecture hygiene

- composition/profile 条件仍在 Spring composition/API boundary；domain/service 业务语义、adapter protocol 与 execution port ownership 未改变。
- 未新增第二套业务接口、fake adapter、Controller 到 OKX transport、反向 module dependency 或 migration。
- ModuleBoundaryArchTest 与 PackageBoundaryArchTest 包含在 full Maven green baseline。

## 8. Validation

| Command / check | Result |
| --- | --- |
| mvn -f backend/pom.xml test | PASS；23 modules、320 reports、1547 tests、failures/errors/skipped=0/0/48 |
| focused full context + affected web regressions | PASS；qualification full context、light context、Auth WebMvc、Trading controller |
| GateY PowerShell 5.1 | 22/22 PASS；manifest SHA-256=eae0c8ca638739007adc50ec5b720a103d1b2d2deaafb70f9be9f4abfeece6f1 |
| GateY PowerShell 7 | 22/22 PASS；与 PS5.1 hash一致 |
| disposable Linux canonical verifier | 22/22 PASS；network=none |
| disposable Linux installer | 13/13 PASS；root/POSIX/service-user/current；network=none |
| GateW frozen regression | 34/34 PASS |
| migration inventory | V1～V41、41 files、target V41、inventory SHA-256=2b6847457a91423f0cbbaed49c3e018f28846a5b94615a169fc5bee67802488b、migration diff=0 |
| git diff --check | PASS；仅既有 LF→CRLF warnings |
| current authority | PASS；errors=0；GateY-6F=NOT_STARTED |
| doc links | PASS；361 checked / 14 historical warnings / 0 errors；首次漏传 mandatory Roots 参数未执行扫描，修正后通过 |

验证历史保留：首次 focused Maven 命令因 PowerShell -D 参数未引用而未进入编译；完整 context 先后暴露 maintenance/catalog consumer closure；首次 full Maven 因 controller ConditionalOnBean 影响 WebMvc slice 而 14 failures，改为 qualification-only profile exclusion 后受影响测试与 full Maven 均复跑通过。

## 9. Findings 与剩余边界

- P0 remaining：0（待独立 Security Review 复核）。
- P1 remaining：0（待独立 Security Review 复核）。
- P2：committed exact-head release、真实 server Flyway/backup/restore/activation/health 均未执行；这是本任务禁止范围，不得写成通过。
- P3：既有 BinanceRecoveryService Javadoc 参数名漂移未扩大处理；IDE format/problems 调用连续超时，已降级为 Java 21 compile、full Maven 与 diff check。

## 10. Side-effect counters

    Server SSH read/write = 0/0
    Deployment = 0
    Migration = 0
    Production Backup = 0
    Production Restore = 0
    Systemd change = 0
    Symlink change on server = 0

    Credential metadata/material read = 0/0
    Decrypt = 0

    OKX GET/POST = 0/0
    PLACE/CANCEL = 0/0
    Transfer/Withdraw = 0/0

    ExecutionIntent/ExecutionReceipt delta = 0/0
    Order/Ledger delta = 0/0

    LIVE enable = 0
    Kill disengage = 0

Disposable Linux side effects仅发生在 network=none 容器的 /tmp/nq-gatey-linux-*、临时 system user 与临时 symlink；容器退出后删除，不计为 production/server mutation。

## 11. Authority 与交接

- STATUS.md / ROADMAP.md 未修改；GateY-6F=NOT_STARTED、first real order/micro-live=NOT_AUTHORIZED、soak=NOT_STARTED、LIVE=DISABLED、kill switch=ENGAGED。
- 未 stage、commit、push、deploy 或 tag。
- Final decision：IMPLEMENTED / PENDING_INDEPENDENT_SECURITY_REVIEW。
- Commit recommendation：当前不得 commit。
- Next concrete action：NQ-GATEY-6F-SERVER-READONLY-RUNTIME-COMPOSITION-AND-DEPLOYMENT-CONTRACT-SECURITY-REVIEW-ATTEMPT-02。
