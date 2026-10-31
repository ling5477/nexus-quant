# Current Status

<!-- nq-current-authority:start
authority_schema=3
last_frozen_gate=GateZ
last_frozen_gate_status=FROZEN|ACCEPTED|TAGGED
last_frozen_gate_tag=nq-gatez-freeze
last_frozen_gate_commit=d106775b6c8a9e17cddb6b99e96c9420fa46df63
active_gate=GateZ
active_gate_status=FROZEN|ACCEPTED|TAGGED
accepted_batch=NQ-V103-03-FACTOR-RESEARCH-FOUNDATION
accepted_batch_status=ACCEPTED|CI_GREEN
accepted_batch_implementation_commit=fe206edffd937c871323278891532b694a47f3ab
accepted_batch_acceptance_head=fe206edffd937c871323278891532b694a47f3ab
accepted_batch_ci_run=41048377808
work_batch=NQ-V104-PLANNING
work_batch_status=NOT_STARTED
work_batch_commit=NONE
work_batch_ci_run=NOT_RUN
next_action=NQ-V104-PLAN
production_soak=COMPLETED
kill_switch=ENGAGED
live=DISABLED
shadow_trading=NOT_ENABLED
ai=NOT_STARTED
dh_runtime=NOT_INTEGRATED
integration_runtime=NOT_STARTED
real_provider=NOT_IMPLEMENTED
private_trading=NOT_IMPLEMENTED
nq-current-authority:end -->

`docs/current/STATUS.md` 是 NexusQuant 当前阶段状态的唯一 authority。其他 current 文档只能引用或解释本文件。

## 当前正式发行与下一阶段

NQ v1.0.0、v1.0.1、v1.0.2、v1.0.3均为 **RELEASED**。v1.0.3已正式发布，三个业务阶段、功能收口、限定范围发行准备与最终准入均完成；本次只是发行事实和当前文档同步，不重新资格化、不增加功能或审查阶段。

| 发行身份层 | 固定事实 |
| --- | --- |
| Qualified Source Commit / Source Tree | 5f770f60e2b6f11b376535b865a60ef5976db3c1 / bf7ad89766deb01ccd468d6c39dc7c0b672a6f64 |
| Qualified Source CI | [41078509409](https://github.com/ling5477/nexus-quant/actions/runs/41078509409)，准确Source head、pull_request、completed/success、9/9 SUCCESS |
| Development Merge / Merge CI | 1badf22e976efef4e2582885046eb64fa678e40c / [41082190021](https://github.com/ling5477/nexus-quant/actions/runs/41082190021)，准确merge head、push、9/9 SUCCESS；tree与Source相同 |
| 无父Release Snapshot / Tree | 29677025572536bcab0ce2bf77ac46fe766cf844 / 294085f526528afb780d4ee152f1abc0333d732a，2228文件、parents=[] |
| Annotated Tag / Object / Peeled | v1.0.3 / 03da66b14beeda90152758d06e9d215431407696 / 29677025572536bcab0ce2bf77ac46fe766cf844 |
| Release / Published | [v1.0.3 / 429746750](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.3) / 2026-10-31T06:00:01Z（北京时间14:00:01），draft=false、prerelease=false |
| Snapshot CI / Post-release Documentation CI | Snapshot CI=NOT_RUN；发行后文档CI单独绑定本次文档commit/PR/merge，不替代正式Source或业务接受pair |

Self-Hosted Source Manifest SHA256=9400efd78ebb0d0b38b4a5593a68b199636f226344e96275e7c65a2fe86d7484；Release Source Hash=82cfa1be500dad60fd24bf34a76d4efd8639366def0234866c08b3d9a41cec2e；Package Manifest=d985d38099f11beca3f3ef98bf4f320e93f4c54d54b5b400473d7e89e8f47734；images-amd64.tar=b6bcb3318900ac9ab807cd6320b68f205f70be0504247eae45b37cc3a5aacfc1。

Companion Source Manifest SHA256=0235f0455f73785e1033020454a4a313ca8673c1a4c0fb8a4b9f9ad651f5e1cc；Source Hash=676ac7a89edb6ecb30cfef9f181855e38e6bc280ba3a42b99730112d213e9bad；Companion Manifest=286493b957a20316e1b4c57480762cf09c89a2fbdb0fe07ed48e212a867aaaae；Wheel=32f0af7fcea903349dd4f3b7ccbd1a6bb5fe8882d0c60b5641afd2d371edf499；Dependency Manifest=990f0ad9141d3e9c1889c0f893ab749ee25b37b62c11e6c7c0a3253a7a422b39；NumPy Wheel=a414504bef8945eae5f2d7cb7be2d4af77c5d1cb5e20b296c2c25b61dff2900c。完整公开映射见[provenance](https://github.com/ling5477/nexus-quant/releases/download/v1.0.3/NexusQuant-1.0.3-provenance.json)，类型UNSIGNED_RELEASE_SOURCE_MAPPING，不是数字签名。

### 六项已发布资产

| 正式资产 | GitHub ID | Bytes | SHA256 |
| --- | --- | ---: | --- |
| NexusQuant-1.0.3-windows-amd64.zip | 694496626 | 320966803 | fa743eaeb202abfbfef820ff17900a42692318242b6309b4b712a5b493679e61 |
| NexusQuant-1.0.3-windows-amd64.zip.sha256 | 694494770 | 101 | f05b7f8b5c09166e7e181549343e7f6cc9a04d46426ee3dc3ba670ffc0388cbe |
| NexusQuant-Python-Research-Companion-1.0.3-windows-amd64-py311.zip | 694496245 | 13107099 | bfd099adbe85c82781e8f00fd07ec51feb9b8b71cc8dbcf495b2386952edcb0b |
| NexusQuant-Python-Research-Companion-1.0.3-windows-amd64-py311.zip.sha256 | 694495179 | 133 | d321839f21724a5abc3f96cf883bceb3c7bc276305320fcfed3bec33ae6a74f0 |
| NexusQuant-1.0.3-provenance.json | 694495620 | 17062 | 76fd5f9f6180c173fc140120bb1a19dbd8959a69b4108425b9864eb0bdaffada |
| NexusQuant-1.0.3-provenance.json.sha256 | 694494510 | 99 | 1cee3662b39d4bf413f2d7356a68e77340e167277a647098d0edf260803c192b |

六项均为uploaded；正式发布阶段已逐项做匿名完整下载/大小/SHA256核验，未用Range替代完整文件。发行后本轮只读复核GitHub固定身份与本地原件摘要，不重新下载全部大包或运行安装/升级测试。两ZIP原字节、正式Snapshot/Tag/资产和历史资格证据保持不变。

正式范围仅Self-Hosted Windows 11 amd64 / 已安装且运行的Docker Desktop Linux Containers / PostgreSQL 16（本包16.15）/ Flyway V52，以及独立Python Companion Windows amd64 / CPython 3.11 x64（实际3.11.9）/ NumPy 2.3.5离线安装。两个组件同源、各有独立ZIP/来源hash；Companion不进入Java运行时、Compose或Self-Hosted Snapshot，不拥有交易权限。普通直接升级仅正式v1.0.2/V49→v1.0.3/V52。

原Whole-run=FAILED / RETAINED，原Update Exit=1 / RETAINED，Raw Comparison=DIFFERENT / RETAINED。后续RECOVERY_RECONCILIATION_02只使Recovery=QUALIFIED_FOR_HISTORICAL_DATA_AND_STABLE_CONTROLS：同一实际故障的历史业务/经济事实与稳定控制恢复正确；scheduled_job_controls及tenant_work_scan_cursors两行后续核对只有四个运行时间字段推进。首次/后续采样不同，不宣称全部raw、全部时间戳或物理库相同，不把原FAILED改为PASS。

保留历史C4 NOT_ACCEPTED / EVIDENCE_GAPS、owner ACCEPT_WITH_EVIDENCE_EXCEPTION与72小时WAIVED；180天不是多年研究或长期策略优势，回放availableAt为实验假设且留出已暴露，Forward合成时钟不等于自然长期观察，Python探索统计不证明可持续盈利。Linux/macOS/ARM64、其他Python及Public Demo未取得本版正式资格，旧Linux低内存600秒仅SCOPED_PASS、长期NOT_VERIFIED；旧research/py安装提示和本地128项工具failure1/skip1保留。LIVE=DISABLED、Kill Switch=ENGAGED，不开放真实资金自动交易。

最近业务接受pair继续为NQ-V103-03 / fe206edffd937c871323278891532b694a47f3ab / CI41048377808；GateZ冻结身份不变。唯一下一阶段仅登记NQ-V104-PLANNING / NOT_STARTED，work commit=NONE、CI=NOT_RUN，next_action=NQ-V104-PLAN。本轮不选题、不制定v1.0.4方案或启动实现。

[TESTING](TESTING.md)与[WORKLOG](WORKLOG.md)仅追加本次事实。实际文档commit、PR、merge、各自准确CI与清理记录在仓库外 E:/Project/NQ_V103_POST_RELEASE_CLOSEOUT.md；新文档提交永远不替代上述正式Source Commit。

## 历史记录：v1.0.2发行与v1.0.3候选准备

以下保留既有原文的形成时点，包括旧“当前”、NOT_RUN及下一任务描述；它们不覆盖本页顶部机器authority和上述已发布1.0.3事实。

NQ-V103-FUNCTIONAL-CLOSEOUT 已于2026-10-30接受，三个业务阶段功能范围COMPLETE，开发Schema V52。当前执行 NQ-V103-RELEASE-READINESS：版本/正式白名单与独立Python Companion交付准备；顶部accepted pair继续记录最近已接受业务候选，不提前绑定新的源码CI或包资格。v1.0.3未创建Tag/Release，安装/升级/恢复与离线Wheel资格以仓库外准确候选报告为准。用户明确授权本轮Git交付与隔离发行资格，不授予生产或真实交易权限。

NQ v1.0.0、v1.0.1、v1.0.2 均为 RELEASED。v1.0.2 已于 2026-10-29T10:15:34Z 正式发布：[NexusQuant v1.0.2](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.2)，Release ID=`427705563`，draft=false、prerelease=false。本次发行后文档同步不改变任何已发布身份。

| 身份 | 固定值 |
| --- | --- |
| v1.0.2 Source Commit / 包 SOURCE_SHA | `e4eca0b23c769602999043b3b95b8777a33bd285` |
| Source Tree | `cfab9f904dbefdca4dbe6eefd1bc88d568646451` |
| Source CI | [40720379547](https://github.com/ling5477/nexus-quant/actions/runs/40720379547) / exact source head / 9 of 9 SUCCESS |
| 无父 Release Snapshot / Tree | `6b5ddcfac9f9ac02e343975ffe2d061df9785318` / `67c74aed5613c3769ed6621a404fe186c488e873`（2211 文件） |
| Annotated Tag / Tag Object | `v1.0.2` / `f078dad1e4122eba55179c5bb426a1134df60924`，peeled target 为上述 Snapshot |
| 发行分支 / schema | `release/1.0.2` / V49 |
| RC3 ZIP SHA256 | `158b7ccc42fb333f4031ceae12fc756ea1f340603ca9aef2f92a63946c67c048` |
| Package Manifest SHA256 | `74406593acccdc452004067a5877a080d7dff596d9417f0af60e54aceb200d5f` |

V102-01～05 已完成；顶部 accepted_batch 记录最新开发功能的实施与接受 CI，不替换上述固定发行身份。固定 RC3 已取得正式准入：Windows 新安装、v1.0.1/V47→v1.0.2/V49 普通升级、历史数据保留、包级备份恢复、主动回滚、真实失败更新自动恢复均 PASS。原 update 失败返回 exit1，原安装器自动恢复 V47 及原资产，122 张旧表/32 张业务表恢复检查点一致，pending 清除、成功回执、健康和登录通过；不是用主动 rollback 替代自动恢复。证据索引及适用范围见 [TESTING](TESTING.md) 的 2026-10-29 发行收尾追加记录。

正式范围仅 Self-Hosted / Windows 11 amd64 / Docker Desktop Linux Containers / PostgreSQL 16（本包 16.15）/ V49；支持 v1.0.1 普通更新，不支持 v1.0.0 直接普通更新到 v1.0.2。四项公开资产大小及完整 SHA256 已复核；provenance 为 UNSIGNED_RELEASE_SOURCE_MAPPING，不是独立数字签名。Snapshot CI=NOT_RUN；上述 9/9 是 Source CI，本次开发文档 CI 另行绑定实际提交。

保留限制：Linux 低内存实际安装、一次重启和 600 秒运行仅 SCOPED_PASS，正式 Linux 支持 NOT_QUALIFIED、长期稳定性 NOT_VERIFIED。低于 4 GiB 为警告→LIGHT→继续（768m/512m/128m），不保证长期稳定性；既存 3.99 GiB 硬拒绝断言为有证据的非阻断旧预期，原失败保留，后续可定向修正测试，本轮不改测试。历史 C4 原审查 NOT_ACCEPTED / EVIDENCE_GAPS、所有者例外及 72 小时重跑豁免均保留。Public Demo 不包含在本次正式发行中，LIVE=DISABLED、Kill Switch=ENGAGED，不开放真实资金自动交易。

v1.0.2 开发与产品发布主线已完成。NQ-V103-01 有界长历史研究的功能候选 `68d757748f2958fef501996a0bf0d3dac24df869` 已通过 [CI 41004482332](https://github.com/ling5477/nexus-quant/actions/runs/41004482332) 的 9/9 必要检查：OKX SPOT BTC/ETH 1h 的 1～180 UTC 日、最多4320根、V2不可变冻结引用、原正式回测/实验和90/180日预设WF。开发 schema 为 V51，正式发行仍为 V49。旧720输入与Continuous SIM边界保持，未自动激活、发布或下单；详见[实施证据](evidence/NQ_V103_01_HISTORICAL_RESEARCH_EXPANSION.md)。

NQ-V103-01 已由 PR #94 交付，当前02的开发基线为 `96e011cb3b7edbba3e62dd85ad0b7fc2469a74b7`。NQ-V103-02 前向 SIM 评价与人工复盘已实现：功能接受 head=`f8fd3a45385708e72436c4ac57c3565b8f7e627d`，准确 [CI 41029376114](https://github.com/ling5477/nexus-quant/actions/runs/41029376114) 为9/9 SUCCESS。原 canonical 分析增加有界1～90日UTC/asOf观察、覆盖与逐项指标状态；V52只持久化研究目标及追加人工复盘，STOP不影响SIM执行。真实隔离PG、Chrome闭环、历史研究/SIM边界回归及一次独立定向审查均通过；合成时间推进不代表真实长期观察。开发schema为V52，正式v1.0.2/V49不变，详见[02实施证据](evidence/NQ_V103_02_FORWARD_SIM_EVALUATION.md)。

02已由PR #95合并，03起始dev基线为 `df2d8057afc0e6ce7f742ccbacfe30ac2a99db46`。NQ-V103-03离线Python因子研究已完成：功能接受head=`fe206edffd937c871323278891532b694a47f3ab`，[准确CI 41048377808](https://github.com/ling5477/nexus-quant/actions/runs/41048377808)为9/9 SUCCESS，Python3.11质量门禁通过。原V2冻结只读导出、三个因子、purged时间切分/块Bootstrap、独立诊断Artifact及原研究页只读预览形成闭环；真实隔离PG/Chrome BTC4320和ETH2160往返、54表零差异及一次独立审查PASS。没有新表或交易事实，开发schema仍V52，正式v1.0.2/V49不变，见[实施证据](evidence/NQ_V103_03_PYTHON_FACTOR_RESEARCH.md)。同一[PR #96](https://github.com/ling5477/nexus-quant/pull/96)的最终文档head、merge与post-merge CI另行绑定，不能用功能接受pair替代。下一任务仅 `NQ-V103-FUNCTIONAL-CLOSEOUT`，机器next_action的PLAN后缀表达待后续指令，不自动执行closeout、构包或发布v1.0.3。以下旧阶段文字只表示对应历史时点，不覆盖当前正式发行及开发状态。

以下发布准备文字为2026-10-20历史记录，不重新触发发布审计，也不覆盖当前迭代。历史 Gate、C4 原审查与例外决定不变，LIVE保持关闭。

## 历史 V1 Release 准备

截至 2026-10-20，dev 集成源码为 `565764cce5862ecc02ca7d1ce18d56fc382d8841`，tree=`39d472ade2d678e0e053875fcd425611f991614f`；PR #64 已合并，同一 head 的 CI `39373837941` 为 completed/success、9/9 SUCCESS。该CI只绑定集成基线，新文档与Snapshot提交的远端CI必须单独获得。

C1/C2/C3 已完成；下一阶段是解决 Release 发布门槛，再进入版本迭代。C4 的实际原始终态为 **72小时观察完成 / NOT_ACCEPTED / EVIDENCE_GAPS / SAFE_STOP_CONFIRMED**：候选 `064b6801f08486943c6e4691537de641dcd25c4d`、release=`nq-064b6801f084-04b6ffbe6403e926`，缺 T+12/18/36/60h 定点 UI 证据。当前候选的撮合、记账、调度与 schema 差异已按正式白名单逐项绑定已有集成/CI证据及39项定向PG/HTTP/跨JVM证明；原C4不承担新增代码的正确性证明。旧 V58 不作为新 V34 的运行升级来源，正式安装仍要求空库。用户随后明确决定：`C4_OWNER_DECISION=ACCEPT_WITH_EVIDENCE_EXCEPTION`、`C4_72H_RERUN=WAIVED`，接受历史定点 UI 证据缺口并豁免72小时重跑；原审查仍为 `NOT_ACCEPTED / EVIDENCE_GAPS`，没有后续独立改判报告。这是单独的 owner 决定，不追认缺失证据、不改写历史审查；新增运行路径另由本次定向证据证明。

本次用户已授权Release Admission裁定；现有合同不禁止在保留原审查的前提下单列历史证据例外。历史定点UI缺口按 `ADMITTED_WITH_DOCUMENTED_EXCEPTION` 准入，新增行为由独立定向审查与上述证据覆盖，未降低或修改validator。`C4_REVIEW=NOT_ACCEPTED/EVIDENCE_GAPS`、`C4_72H_RERUN=WAIVED` 均保留。新文档提交及独立Release Snapshot仍须完成其准确SHA的远端CI，不能用565764cc旧CI代替；本地包冻结与公开发布分开记录。

保留技术接受身份：C1=`02536d050bdca953ddda54388735744bc15050c7 / CI 36705151215`（PR #61）；C2=`354c03415e2eb1fc40c289361d352301d2b555a5 / CI 37414767478`（PR #62）；C3=`07958d143bf05d7e37fd9aacf270244bc0ae5451 / CI 37418672434`（[原始证据](evidence/NQ_V1_C3_PRODUCT_UX_CLOSURE.md)）。以上均为各自9/9接受 pair，不由 dev 集成或本轮文档身份替代。

本地包 `C:/nq-v1-final-20261020-565764cc/package-amd64-final` 的 Release ID 为 `53dc4bd18e2797cfb1327a1c04c3ce374d7565d7db7c5b03d90b517b608f1949`，Windows 11 amd64 / 已运行 Docker Desktop 的隔离全新安装、PG16.15/V34、备份恢复和恢复后重启已通过，范围为 `QUALIFIED_LOCAL_SCOPED`，不等于公开发布或 C4 接受。运行入口 http://127.0.0.1:18580；保持 Web 请求/回执人工交接，不授予 Web 宿主执行权限。严格更新版本条件不满足，保留 `UPGRADE_NOT_VERIFIED`。

保留四项外部数据限制：`ACCOUNT-PERFORMANCE` 普通账户缺可信权益来源时不编造收益；`ACCOUNT-FEE-SOURCE` 不编造真实手续费；`PRICE-EVENTS` 不从 OHLC 编造实际发生时点；`LEGACY-VALUATION` 不更改旧 Paper 未知币种权益。`LIVE=DISABLED`、kill=`ENGAGED`，真实 provider/client/交易所写入关闭。

清理结果为 `NQ_V1_LOCAL_CLEANUP_PARTIAL / EXACT_RETAINED_RESOURCES`，不是全部完成。以 `E:/nq-v1-cleanup-20261020-565764cc/NQ_V1_FINAL_CLEANUP_REPORT.md` 为准；18580、18099 Demo、正式包、必要备份、C4与恢复证据保留。本轮未清理资产。

本轮正式清单内产品文档随新的本地开发提交、独立白名单Snapshot和新目录发行包共同冻结；来源及最终摘要见 `C:/nq-v1-freeze-20261020-565764cc/NQ_V1_FINAL_RELEASE_ADMISSION_AND_PACKAGE_FREEZE_REPORT.md`。旧包不修改、不复用旧摘要代表新内容。开发文档不进入Release，精确白名单不扩大。当前机器work batch指向尚未公开发布的新候选；远端exact-head CI未经执行，不声明已通过。

- C4 原始终态（本地证据，非Git文件）：`E:/Project/nexus-quant/artifacts/runtime/v1-c4-064b6801-20261006/attempt-02/evidence/final-rc-report.md`；JSON、final-evidence-index、controlled-restart 与 final-facts-review-confirmed 在同目录，均为只读历史。
- 本地发行：`C:/nq-v1-final-20261020-565764cc/NQ_V1_FINAL_LOCAL_RELEASE_REPORT.md`。
- 清理：`E:/nq-v1-cleanup-20261020-565764cc/NQ_V1_FINAL_CLEANUP_REPORT.md`。
- 本轮闭包/文档报告：`E:/nq-v1-release-closure-20261020-565764cc/` 下两份 NQ_V1_*_REPORT.md；机器证据在其 evidence 目录，独立 Git-blob 快照为 release-source-565764cc。

## 冻结 Gate 与历史接受索引

下文保留历史身份；其中生产 runtime stopped、旧 schema 和旧 next action 仅属于对应历史时点，不覆盖以上 V1 本地运行事实。`production_soak=COMPLETED` 是原 Gate 机器字段，不等于本次 C4 接受。

## 1. 当前阶段

- GateY：`FROZEN / ACCEPTED / TAGGED`（已冻结 / 已接受 / 已打 tag）；strict archive 为 [../gates/gate-y/README.md](../gates/gate-y/README.md)，freeze commit=`72fbf5e78f217a02b572a54fadb17dea204b594f`，annotated tag=`nq-gatey-freeze`，tag object=`c84f412e1da652e85158c5478997945d3065e575`，peeled commit 与 freeze commit 一致。
- GateAUDIT：`FROZEN / ACCEPTED / TAGGED`；freeze commit=`a4cf8516382b47112852fc3f8dd9e5a65a80f993`，tree=`557bb3fa6549732cc5bdf2d40251c31a963f83a0`，annotated tag=`nq-gateaudit-freeze`，tag object=`4ee65d68471c5e25874ab6a08c3e9fc420f667b7`，peeled commit=`a4cf8516382b47112852fc3f8dd9e5a65a80f993`。`dev` exact-head CI=`35840126216 / push / completed / success / 9 of 9`；release checker 与 release-mode archive checker 均 `PASS / errors=0`。13-role archive 保持 pre-tag snapshot 语义；post-tag freeze 由 Git tag 与本 current authority 表达。Phase7-E 与 Phase7-F authority synchronization 已完成；post-GateAUDIT Git cleanup 按用户接受的 disposition 为 `COMPLETE`，保留的历史本地分支及 worktree 属于非阻断残余。
- GateZ：`FROZEN / ACCEPTED / TAGGED`。13-role strict archive 见 [GateZ archive](../gates/gate-z/README.md)；freeze commit=`d106775b6c8a9e17cddb6b99e96c9420fa46df63`、tree=`1bf0f4734b02cbc7f2b4a48ce308c9f492f26e98`、annotated tag=`nq-gatez-freeze`、tag object=`05f3f31975394aa787745adf5414ad8467b8c17b`，本地与远端 peeled commit 均为 freeze commit。`dev` exact-head push CI=`36540779142 / completed / success / 9 of 9`；release 与 strict archive 检查 errors=0。归档中的 `PRETAG / TAG_PENDING` 保持历史快照原义；post-tag current authority 由本机器区块表达。V1 planning 交付动作以顶部机器区块为准。
- GateZ 账户差异根因检查：技术 PR #42 合并于 `f7a5faa782e55a87f263f7fefba4b717599396d9`，其 `dev` exact-head CI `36302559099` 为 9/9 SUCCESS；release `nq-f7a5faa782e5-20670623964ad074` 已普通 install/activate，PG16/V54 只读 validate 通过，migration 执行数 0。本轮唯一新 OKX 观察的 typed 结果为 `UNKNOWN / BALANCE_SEMANTIC_MISMATCH`：本地账户快照无 SIM/LIVE 环境语义，不能与 OKX 余额同口径比较。确认 7 种外部正余额资产、其中 5 种无本地快照行；BTC 为正且低于当次公开最小数量；本地 BTC/USDT 快照为 2026-08-26，时间因果未证明。正式采集器因空 `expiresAt` 触发 `AttributeError`，原始验收为 `QUALIFICATION_BLOCKED_COLLECTOR_ATTRIBUTEERROR`，不追认 PASS，也不标记 `ACCOUNT_DIVERGENCE=EXPLAINED`。10 次固定 GET、交易 mutation 0、五类 canonical 交易事实内容未变；终态 Java/MainPID=0、LIVE=DISABLED、kill=ENGAGED。先解决快照余额的 LIVE/SIM 语义与新鲜度来源，再决定是否有精确金额差；详见[本轮只读根因检查证据](../archive/gate-z/evidence/gate-z/OKX_ACCOUNT_DIVERGENCE_READONLY_ROOT_CAUSE.md)。
- GateZ 账户快照 provenance：技术 PR #44 的 `dev=5197b128131aa21be7705ee6cf3619db3cec00bb / exact-head CI 36314244328 / 9 of 9 SUCCESS` 已接受。PG16/V54 的 canonical backup 与隔离恢复通过后，生产仅执行 V55，Flyway validate 为 V55/pending=0/failed=0；两条历史快照的新 provenance 字段仍全为 NULL，旧字段指纹不变。`nq-5197b128131a-39f54b083d7e0571` 已普通 install/activate，`/current` 与 activation head/journal 一致，终态无 Java 进程、LIVE=DISABLED、kill=ENGAGED。没有新的 OKX private observation；collector 原正式源码未找回，状态为 `COLLECTOR_SOURCE_UNAVAILABLE / DEFERRED_NON_BLOCKING_FOR_SNAPSHOT_SEMANTICS`。既有 divergence 仍为 UNKNOWN，不追认 `ACCOUNT_DIVERGENCE=EXPLAINED`。详见[生产 V55 与 release 证据](../archive/gate-z/evidence/gate-z/ACCOUNT_SNAPSHOT_PROVENANCE_PRODUCTION_V55.md)。
- GateZ 账户只读对账模型修复：技术 PR #46 已合并，`dev=dc0b01e5653d6718a00b0d667bc2440c47b49b55 / exact-head CI 36326961228 / 9 of 9 SUCCESS`。`WHOLE_VENUE_BALANCE_RETIRED_FROM_MANAGED_DIVERGENCE`、`EXTERNAL_ACCOUNT_CONTEXT_SEPARATED`、`NQ_MANAGED_RECONCILIATION_SEMANTICS_ACCEPTED`：全账户余额、未归属资产/订单、BTC dust、非现货仓位仅作上下文；托管聚合限已证明归属且同义、新鲜的 NQ LIVE 活跃 OKX 订单，结果为 `MATCH / DIVERGED / UNKNOWN / NOT_APPLICABLE`。隔离 PG16/V55 的 26 项集成测试与 27 项单元测试通过，完整 backend Maven 通过；独立 correctness 审查及受影响复审最终 P0/P1/P2/P3=`0/0/0/0`。本次无 migration、OKX 请求、凭证读取、交易 mutation 或生产部署。历史 `REAL_ACCOUNT_READONLY_FACTS_QUALIFIED`、当时的 DIVERGED 与 `UNKNOWN / BALANCE_SEMANTIC_MISMATCH`、采集器失败均保持原结果；本次代码/CI 不构成新的真实账户观察。`LIVE=DISABLED`、kill=`ENGAGED`。
- GateZ 公开市场全窗口 SIM 回放：`NQ-GATEZ-PUBLIC-MARKET-FULL-WINDOW-SIM-REPLAY` 已由技术 PR #48 合并，`dev=1ace052903b6bf70af41f1cf77c72b5157e3c6c8 / exact-head CI 36370231759 / 9 of 9 SUCCESS`。既有冻结 OKX SPOT / BTC-USDT / 1h 公开响应的 72 根已收盘 bar 在隔离 PostgreSQL 16 / V55 中逐根消费，A/B 两次独立重算的决策、canonical Order/Trade/Ledger 和资金、仓位、PnL 语义轨迹相等；第三次独立反事实回放验证未来 bar 变异不改变先前信号及已发生经济事件。同一 logical run 重复推进不增添经济事实，缺失/重复/乱序/未收盘 bar 与 dataset、策略、成本、规则身份漂移均拒绝。实际每次 22 Order、22 Trade、90 Ledger entries；最终 cash=`100.96886500`、position=`0E-8`、PnL=`0.968865000`。本地 PG 定向测试 12/12、完整 backend Maven 与独立只读审查 P0/P1/P2/P3=`0/0/0/0`；`FULL_WINDOW_SIM_DECISION_STABILITY_P2=CLOSED`。公开规则是采集时观察的冻结回放假设，费用和滑点是非零实验假设；无 migration、OKX 调用、凭证读取、生产部署或真实交易 mutation，LIVE=`DISABLED`、kill=`ENGAGED`。
- GateY-6F：`ACCEPTED / CI GREEN / MINIMAL LIVE PILOT VERIFIED`（已接受 / CI 已通过 / 最小实盘 pilot 已验证）；production pilot release=`8e3dd0cf6104eb85f36a0e434ca51ea9d903705a`，CI run=`32978280738 / completed / success / 10 jobs`。
- GateY-FREEZE：`ACCEPTED / CI GREEN / TAGGED`（已接受 / CI 已通过 / 已打 tag）；exact-head CI run=`33037514013 / completed / success / 11 jobs / bad=0`，archive/release post-tag checker errors=0。
- GateAUDIT-0C-R3-DOC-LINK-LINUX-REMEDIATION：`ACCEPTED / CI GREEN`（已接受 / CI 已通过）；immutable acceptance pair=`40e1077e1fe735a3d250f094caaa24e437e8ea3f / 33306024232`，blocking jobs=`11/11 SUCCESS`。Linux CI 已关闭 P1-01 authority fixture、P1-02 Java verifier 与 doc-link hidden-root portability finding；P0=0、P1=0。
- GateAUDIT Phase 3：`COMPLETE / READY_FOR_PHASE4`；正式 findings=`P0 0 / P1 4 / P2 8 / P3 1`。Tier A F-001～F-004 均已接受；剩余 capability gaps 已进入 Phase4 closeout disposition，不把 Phase5/Phase6 后置能力写成当前已实现。
- GateAUDIT-PHASE4-L3-PROOF-FOUNDATION：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`95b859ee61a8e7f0a725e29877e7303ea4453b1a / 33347091147`，exact-head CI=`11/11 SUCCESS`。F-001 不在本任务重新 Review。
- GateAUDIT-PHASE4-F004-TRADE-LEDGER-CONVERGENCE：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`18efc06c380d2b411ba7d5f651e7e441247a1b96 / 33358364678`，exact-head CI=`11/11 SUCCESS`。
- GateAUDIT-PHASE4-F002-RESTART-PROOF-FOUNDATION：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`0651a7365d1a6afe453d75c8abd3975d458e0b7a / 33387882472`。R1/R2 forked-JVM proof 与 CI datasource binding remediation 已由 exact-head CI 接受，且不等于 Phase6 full L4 qualification。
- GateAUDIT-PHASE4-F003-ORDER-EXECUTION-IDENTITY-CONVERGENCE：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`327c2229e89c076eace60046b79ec02c622a7fe4 / 33399190770`，exact-head CI=`11/11 SUCCESS`。ordinary Order 是唯一 execution fact，ExecutionIntent 只编排已存在 Order 的外部动作。
- GateAUDIT-PHASE4-REMAINING-DISPOSITION-AND-CONSOLIDATION：`COMPLETE / ACCEPTED / CI_GREEN`；immutable acceptance pair=`7ca1fc92f8900e3e9d19184fccd40569f233823f / 33405549149`，exact-head CI=`11/11 SUCCESS`，blocking P0/P1=`0/0`。该 pair 是 Phase4 capability acceptance authority，不由后续 current-fact synchronization commit/CI替代。
- GateAUDIT Phase5A：`ACCEPTED / CI_GREEN`；immutable acceptance pair=`d1d20f4087cd337e0b21037b38b377bcbe25499f / 33505000903`，exact-head CI=`completed / success / 9 of 9`，blocking P0/P1=`0/0`。P5-F001=`ACCEPTED / CLOSED`（local baseline与后续remote enforcement均已接受）；P5-F004与P5-F006=`ACCEPTED / CLOSED`；P5-F005=`INTERNAL_SBOM_PROVENANCE_ACCEPTED`，platform attestation仍为`DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION / id-token NOT_GRANTED`。`IMAGE_DIGEST_RUNTIME_PULL_PENDING_EXACT_HEAD_CI`与`CRITICAL_E2E_ADMISSION_PENDING_FIXTURE_REPAIR`已由该exact-head CI关闭；remote required checks现已由ruleset `22381941`在`refs/heads/dev`生效并接受，effective checks=9/9。
- GateAUDIT Phase5B：`ACCEPTED / CI_GREEN`；immutable technical pair=`a12ec821fee9dcadaa11428f1db0a065614fb58b / 33615809848`，tree=`40421839abdb44ebd5e934add03fba85d78feab6`，exact-head CI=`9/9 SUCCESS / failed 0 / skipped 0`。Canonical release=`nq-a12ec821fee9-a9a98236663bba0b / COMMITTED_CLEAN / deployable=true / authorizationEligible=true`，build→external admission→verify→install→activate→active verification全部成功；该历史Phase5B pair的PostgreSQL 16.15 schema restore至V46、pending=0、backup integrity、Flyway validate、repository/app-context smoke均通过，PG17 wrong-major提前拒绝。Critical E2E current baseline=`5 specs / 27 cases`（loopback=`25/25`、real-backend=`2/2`），Idempotency-Key fail-closed实际执行并通过。P5-F002与P5-F003=`ACCEPTED / CLOSED`；P5-F008=`ACCEPTED / CLOSED`；P5-F007为`ACCEPTED / CLOSED`，P5-F009为`ACCEPTED / CLOSED`。remote enforcement现为`APPLIED / VERIFIED / ACCEPTED`（ruleset `22381941`，`refs/heads/dev`），platform attestation仍`DEFERRED`。

- GateAUDIT Phase5 F008：`ACCEPTED / CLOSED`（已接受 / 已关闭）；Final Closure Review=`PASS / P0_0 / P1_0`，implementation commit=`716199a7cb836a5eaf43a88b0de6db0f47a75e91`，immutable technical acceptance pair=`614359fc7f25227f736fbb1c11c7d584da1f0627 / 33978394774`，exact-head CI=`completed / success / 9 of 9 / failed 0 / skipped 0`。`614359fc7f25227f736fbb1c11c7d584da1f0627`属于`accepted CI test-harness compatibility remediation`，不构成新的F008 implementation finding。Mandatory Maven与YAML semantic validator通过，mutations=`135 REJECTED / 0 ACCEPTED`，R06/R09/R10均拒绝；本轮只接受已有review/CI证据，不重跑技术测试。详见[post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F008_POST_CI_AUTHORITY_ACCEPTANCE.md)。历史失败与remediation attempts保留在原evidence和append-only ledgers。

- GateAUDIT Phase5 F007：`ACCEPTED / CLOSED`（已接受 / 已关闭）；implementation与accepted technical head均为`0e2efdeb236c185dbace67bb22f94c6af64a563a`，immutable technical acceptance pair=`0e2efdeb236c185dbace67bb22f94c6af64a563a / 34009290836`，exact-head CI=`completed / success / 9 of 9 / failed 0 / skipped 0`，P0=0、P1=0。Scheduler/worker、reconciliation、ledger recovery与critical alert最小观测均已具备；既有observability测试10/10通过，Full Maven=1783 tests、0 failures/errors、53 conditional test skips；53是测试条件跳过，不是CI job skip。高基数metric tags=0、业务副作用语义变化=0；本轮仅接受既有技术与CI证据，不重跑qualification。详见[post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F007_POST_CI_AUTHORITY_ACCEPTANCE.md)。本轮docs-only authority commit不替代该technical pair。

- GateAUDIT Phase5 F009：`ACCEPTED / CLOSED`（已接受 / 已关闭），local review=`CLOSED / PASS / P0_0 / P1_0`，P1-1/P1-2/P1-3均CLOSED。首次delivery=`85d11984d0c65b464ffe4858fe7fd1da51885f12`、failed CI=`34024011663`保留为失败历史；remediation/accepted technical head=`dbb8b9c6a2319338f5ca90b566ad494142a55e20`、CI=`34024427455 / completed / success / 9 of 9 / failed 0 / skipped 0 / cancelled 0`。本次authority同步不替换technical pair；详见[F009 post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F009_POST_CI_AUTHORITY_ACCEPTANCE.md)。
- GateAUDIT Phase5 F001：`ACCEPTED / CLOSED`（已接受 / 已关闭）；remote enforcement事件为ruleset `22381941`创建，目标=`refs/heads/dev`，required checks=9；本轮只读重新验证ACTIVE、effective=YES、missing/unexpected/duplicate=0/0/0、GitHub Actions app=15368，P0=0、P1=0。正式authority acceptance与此前remote mutation分层，详见[F001 post-remote acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F001_POST_REMOTE_AUTHORITY_ACCEPTANCE.md)。
- Phase5=`ACCEPTED / CLOSED`：total=9，accepted/closed=8（F001/F002/F003/F004/F006/F007/F008/F009），open/unclosed=1（仅F005 deferred/non-blocking），blocked=0、remaining blocking=0；非延期open=0。F005 internal SBOM/provenance已接受，platform attestation继续`DEFERRED / NON_BLOCKING / DEFERRED_UNTIL_EXPLICIT_AUTHORIZATION`，不伪装成CLOSED。既有ROADMAP闭合条件已满足，machine lifecycle无必须关闭全部deferred项的额外条件。
- GateAUDIT Phase6=`ACCEPTED / COMPLETE`；Phase6 L4=`ACCEPTED`，B0–B5=`ACCEPTED`，B6=`AGGREGATE_ACCEPTED / AGGREGATE_QUALIFICATION_ACCEPTED`。最终 technical SHA=`3d103cea2072b3c2d9d1009cc5841c18a958ee80`，exact-head CI=`34501806297 / 9 of 9 SUCCESS`；current eligible matrix=`28/28 ACCEPTED / missing=0 / invalid=0`，L4范围P0=0、P1=0。完整接受事实见[B6 aggregate evidence](../audit/evidence/GATEAUDIT_PHASE6_L4_B6_AGGREGATE_QUALIFICATION_ACCEPTANCE.md)。本次docs-only authority同步不替代该technical pair，Phase6 L5、L6-A、L6-B及L6均已接受，mandatory remaining=0；在该 Phase6 final acceptance 时，GateAUDIT 尚未冻结；后续 Phase7-A 至 Phase7-F 状态见当前 authority 与路线图。完整矩阵与各技术身份见[Phase6 final acceptance](../audit/evidence/GATEAUDIT_PHASE6_L5_L6_FINAL_ACCEPTANCE.md)。

## 2. Accepted pilot facts

- Scope：单账户、单 credential、OKX Spot、BTC-USDT、BUY LIMIT、pilot cap `<= 10 USDT`、人工受控。
- Execution：PLACE=1、PLACE retry=0、CANCEL=0、Attempt-02=`NOT_CREATED`、second PLACE=`NOT_EXECUTED`。
- Reconciliation：Order=`FILLED/LIVE`、Intent=`RECONCILED`、Receipt=`QUERY_CONFIRMED`、Trade=1、Ledger entries=4。
- Terminal：Lease=`CLOSED`、activeLease=0、Session=`LIVE_RECONCILED`、Authority=`CLOSED`、kill=`ENGAGED`、LIVE=false、runtime stopped、Transfer=0、Withdraw=0。
- Residual：`P2 / ORDER_VENUE_IDENTITY_MODEL_CONSISTENCY_RESIDUAL`；`Order.externalOrderId=NULL`，不阻断 freeze，本轮不得修改生产事实或代码清零。

## 3. Archive Compatibility Verification

以下字段只供最近已冻结 Gate 的 archive checker 校验，不属于 `nq-current-authority` schema：

```text
current_gate_status=FROZEN|ACCEPTED|TAGGED
current_gate_tag=nq-gatez-freeze
updated_commit=d106775b6c8a9e17cddb6b99e96c9420fa46df63
```

## 4. 安全与运行边界

- LIVE=`DISABLED`，kill switch=`ENGAGED`，activeLease=0，runtime stopped。
- 禁止再次 pilot、PLACE、CANCEL、第二订单、transfer、withdraw 或重新 DISENGAGE kill。
- 禁止修改生产订单、Trade/Ledger、lease/session/authority、生产数据库业务事实、credential、OKX 权限或重新部署 pilot runtime。
- Shadow trading 未启用；AI、DH runtime 与 Integration runtime 未开始。NQ-only 任务不声明 DH current authority。

## 5. 下一允许动作

- Phase6 L4/L5/L6=`ACCEPTED`，Phase6=`ACCEPTED / COMPLETE`。Frontend localization / Error UX / Error Catalog / stable error identity 前置工作包保持 `COMPLETED / ACCEPTED / CI_GREEN`，technical pair=`1b4c87129f2a79e13e379aa56501042ddd5bd42f / 35684433673`。NQ Console Visual System V3=`ACCEPTED / CI_GREEN`，固定 technical pair=`07453f8b16e798bd580070a3727aa9eb7e88a193 / 35720426791`。Phase7-A final baseline inventory=`ACCEPTED / CI_GREEN`，immutable pair=`baa01f0f0034bb46a24f9fe8f62acf60bb56e3f6 / 35729125034`，9/9 SUCCESS；21 行 acceptance owner、17 组 technical/CI binding、ancestry、17 行 residual、projection handoff 与 release boundary 均已收敛，P0/P1=`0/0`。完整 inventory 见[Phase7-A evidence](../audit/evidence/GATEAUDIT_PHASE7_A_FINAL_BASELINE_INVENTORY.md)。本次 authority sync commit 不替代该 pair。
- Phase7 B～F taxonomy normalization=`ACCEPTED / CI_GREEN`，pair=`8868edb248b614e360377317c9c17e8f1d7d8404 / 35734048380 / 9 of 9 SUCCESS`。B～F 分别唯一分类为 `IMPLEMENTATION / AUDIT / IMPLEMENTATION / RELEASE / IMPLEMENTATION`，且 predecessor-compatible；governance contract/lib/checker delta=`0/0/0`，UNKNOWN/AMBIGUOUS 继续 fail-closed。
- Phase7-B historical projection baseline verification=`ACCEPTED / CI_GREEN`，immutable pair=`fea0f1ce228ac7079a7873393daba2d1294fec58 / 35748394188 / 9 of 9 SUCCESS`；PG16 offline restore、source-only Decimal oracle、Position 与 latest Snapshot exact comparison、独立 `REVIEW_ONLY` 均通过。`HISTORICAL_PROJECTION_REPAIR_REQUIRED` 已精确 disposition 为 `CLOSED_BY_BASELINE_VERIFICATION / REPAIR_NOT_REQUIRED`，mandatory before/after=`1/0`，17 行 residual 保留、其余 16 行重分类=`0`，P0/P1=`0/0`。完整结果见[Phase7-B evidence](../audit/evidence/GATEAUDIT_PHASE7_B_HISTORICAL_PROJECTION_BASELINE_VERIFICATION.md)；该 pair 是 Phase7-C 的 immutable input，不覆盖当前 next action。
- Phase7-C readiness repository audit=`ACCEPTED / CI_GREEN`，immutable pair=`82f1afc43664a327eea2fcfd7046bcef099e621b / 35761394744 / 9 of 9 SUCCESS`；结论=`PASS / GATEAUDIT_FREEZE_READY / PRETAG_ARCHIVE_AUTHORIZED`，P0/P1=`0/0`。完整结果见[Phase7-C readiness evidence](../audit/evidence/GATEAUDIT_FREEZE_READINESS_REVIEW.md)。
- Phase7-D canonical archive and closeout=`ACCEPTED / CI_GREEN`，immutable pair=`4800ab1d9407eeb527182328263c0bae9e6c3087 / 35803472376 / 9 of 9 SUCCESS`；[strict canonical archive](../gates/gate-audit/README.md) 包含 8 mandatory + 5 conditional roles，pre-tag validation errors=`0`。archive 中的 `PRETAG / TAG_PENDING` 是冻结前生成时的历史语义，原位保留；Phase7-E 随后完成同一 SHA 的 `dev` fast-forward、push CI 与 annotated tag，Phase7-F 将结果同步到 current authority。
- Logging sensitive-data protection pair=`48c1b1cd4c84e1429be82093e460984b6d1c805c / 35817828506`，修复前 13/13 synthetic 泄漏和修复后 0/13 证明原位保留；SQL ownership audit pair=`0e200e807a1347e5ec6acd24975fa3531a31c90d / 35828020513`，初审 FAIL、修订后 delta PASS 均见[独立复核](../audit/evidence/GATEAUDIT_SQL_OWNERSHIP_REPOSITORY_INDEPENDENT_REVIEW.md)。[Original Scope reconciliation](../audit/evidence/GATEAUDIT_ORIGINAL_SCOPE_TRACEABILITY_RECONCILIATION.md) 最新为 TOTAL=39 / UNCLASSIFIED=0 / REMAINING_GAPS=0；17-row canonical residual 未重分类。
- Pre-tag delta rebind/archive refresh=`ACCEPTED / CI_GREEN`，进入点=`6bc3fa75def9ffe31710d006359e0991d1a60bc1`，最终 implementation pair=`c91f26487caf92f963fab36486efa2aebe666ccb / 35832860696 / 9 of 9 SUCCESS`。[13-role archive](../gates/gate-audit/README.md) 纳入 Phase7-D 后 7 个提交，唯一 runtime delta 是已接受的 logging 修复；21-row capability matrix 和 17-row residual 原位保留。首次 refresh 提交 `5bf244b8f4dbf4d857cc6d2f45eeb23ead21f3fc / 35831845804` 的 secret scan 失败（8/9）保留在 [closeout](../gates/gate-audit/GATEAUDIT_FREEZE_CLOSEOUT.md)，后续仅修正文档排版，未修改规则。`POST_PHASE7D_DELTA_RECONCILED=true / ARCHIVE_REFRESHED=true / PRETAG_CANDIDATE_REBOUND=true / P0=0 / P1=0`。Phase7-E 已完成；freeze commit 与 tag identity 见本 STATUS 当前 GateAUDIT 条目。
- L4历史technical pair保持`3d103cea2072b3c2d9d1009cc5841c18a958ee80 / 34501806297`；Phase6 aggregate pair=`dbf9662add09388cd77ca7552de276bb019f0f74 / 35043157675`及全部历史 owner identity 不变。GateZ-1 实现提交=`9ada1f76151e3f9f7ec6fe732004a4ce7af04b7d`，CI 修复提交=`a3f9f58de872a55ca0bda1a989ec1e69794c6dad`，PR #24 merge/exact-head pair=`09b7e1cd9c68033b54b75cf361cdda70b584f3a4 / 36111701008 / 9 of 9 SUCCESS` 保持历史接受身份。当前公开行情回放切片实现=`1dafcbdf36774c3febfc7ac0499149ec8566afd9`，测试修复=`91ba50d95a8bc68dd4d4e0b43974e89b13fd1391`，PR #27 merge/exact-head pair=`b253dc19124d26cd8b2aab5685d773adc03e3687 / 36141910575 / attempt 2 / 9 of 9 SUCCESS`；首轮 PostgreSQL restore 的一次性容器启动时序失败保留在同一 run 历史，不替换已接受的最终结果。独立只读审查 P0/P1=`0/0`；在该历史 smoke 验收时，全部 72 根的 SIM 决策稳定性仍是 P2。本轮 full-window 验收已关闭该 P2，见当前 GateZ 全窗口条目。GateAUDIT pre-tag refresh technical pair=`c91f26487caf92f963fab36486efa2aebe666ccb / 35832860696`仍保留为历史接受身份。
- 当前 repository schema=`V58`，仅表示已合并代码及隔离 PostgreSQL/CI 的迁移事实，不推断生产 schema；历史 migration 与接受身份保持不变。
- P2 ordinary concurrent INSERT loser、P3 wildcard-import residual均为`OPEN / NON_BLOCKING`；不以L4 P0/P1=0宣称全部问题清零。其他历史非阻断残余不在本次重评，原记录保留于[pre-B0 evidence](../audit/evidence/GATEAUDIT_PHASE6_PRE_B0_CI_SAFETY_CURRENT_AUTHORITY_REMEDIATION.md)。
- P1-1/PB1=`RETIRED_COMPATIBILITY_ONLY`，PB2=`DORMANT_NO_CURRENT_ENTRYPOINT`；14个历史inactive scenario与其他future obligations不计PASS。仅在路径重新canonical时重新评估reachability，不为覆盖率复活入口，详见[B6 aggregate evidence](../audit/evidence/GATEAUDIT_PHASE6_L4_B6_AGGREGATE_QUALIFICATION_ACCEPTANCE.md)。
- Phase7-F post-tag authority sync 已将 GateAUDIT 冻结；其最后一个 immutable technical accepted pair 为 pre-tag refresh `c91f26487caf92f963fab36486efa2aebe666ccb / 35832860696`，freeze commit `a4cf8516382b47112852fc3f8dd9e5a65a80f993` 不伪装成新 technical pair。GateZ-1 与公开行情回放切片均已合并并通过各自合并后 `dev` exact-head CI；LIVE=`DISABLED`、kill switch=`ENGAGED`，未发生真实交易或生产部署。

## 6. F009 acceptance provenance

- 已接受本地review与三项P1 closure，不追加review或技术测试。Reviewed fingerprint=`4f5ff0565f44f6aed7fe1b3288f1273a0284b163185cc6eea78a86fc68fef826`；protected fingerprint=`0c4cb19699c5bf914d8e675f60fc37a4869b15ba4ee18ea3bac65e82c9740099`，均仅为历史precommit证据标识，不冒充当前authority candidate fingerprint。
- Failed delivery pair=`85d11984d0c65b464ffe4858fe7fd1da51885f12 / 34024011663`；根因是precommit ROADMAP authority delta导致stage-asset exception digest stale。最小remediation为`dbb8b9c6a2319338f5ca90b566ad494142a55e20`，只同步当时ROADMAP exception hash。
- 固定technical acceptance pair=`dbb8b9c6a2319338f5ca90b566ad494142a55e20 / 34024427455`；后续governance-only authority commit与此pair分层记录，不能成为新的technical acceptance head。完整记录见[F009 post-CI acceptance evidence](../audit/evidence/GATEAUDIT_PHASE5_F009_POST_CI_AUTHORITY_ACCEPTANCE.md)。

- C1 delivery remediation仅机械同步三项授权protected hashes：contract count=18、member topology=105、approved caller topology=1559 edges均不变；new compatibility caller=0、enforcement semantic change=0。F009仍`ACCEPTED / CLOSED`，不是新F009实现。

- Engineering discipline历史整改与来源保留于[原证据](evidence/instruction-system/NQ-CODEX-ENGINEERING-DISCIPLINE-COMPLETENESS.attempt-01.md)；其历史待审阶段不再作为当前work batch或next action。
