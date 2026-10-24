# NQ-V101-SIGNED-TRANSITION-AUTHORIZATION

日期：2026-10-24（Asia/Shanghai）。**Decision: PENDING_WORKFLOW_GIT_DELIVERY**。Independent Trust仍为BLOCKED，尚未产生真实Attestation、签名Bundle或签发Run。

本轮已经实现可审查的手动签名工作流、配置真实GitHub人工审批环境、完成12项定向测试与一次独立定向候选审查，并实际调用签发及验签CLI。远端尚无新工作流，签发尝试返回HTTP404；有效参数的自定义Predicate验签也返回HTTP404。没有把本地实现、合成测试、已有源码Commit签名或隔离测试豁免当成真实签发成功。

## 固定身份及候选保护

- Release Source Commit：`1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71`。
- Release Source Tree：`c323207865b8ef6a978746dd2ae63f15fdcc3ca4`。
- Version1.0.1 / Schema47 / NQ_SELF_HOSTED / Windows11 amd64 Docker。
- 原始Transition SHA256：`c084eda5916007d90d1e3799a906ab4f7b95da0e87b8b0f3a7cb08f88d95fd56`，原文件及工作流公开输入副本的原始字节均准确匹配。
- 主工作区 `E:/Project/nexus-quant` 的两份暂存报告、index blobs和Staged Tree `a86ec81cc9914ac1e45504acd233d44bed7025b5` 保持不变；两份历史未跟踪报告原样保留。
- 新增候选仅位于独立受管Worktree `C:/Users/Lingyu/.codex/worktrees/nq-transition-authorization/nexus-quant`，分支 `codex/nq-v101-transition-authorization`，HEAD为固定1dc5ea7e…；未Commit、Push、PR或Merge。
- 148份资格证据的实际文件SHA全部复核一致，没有重跑研究/回测/SIM/迁移/完整恢复，也没有修改正式发行源、Legacy Bridge、业务代码、Flyway、现有包或v1.0.0 Release。

## 实现范围和信任语义

工作流：`.github/workflows/nq-release-transition-attest.yml`。只响应workflow_dispatch，不支持PR/push自动签发。事件/仓库/仓库数值ID/owner数值ID/actor/triggering_actor/refs/heads/dev均检查，不能由任意调用者提供一个摘要就获得签名。

调用者必须输入完整 `AUTHORIZE c084eda5916007d90d1e3799a906ab4f7b95da0e87b8b0f3a7cb08f88d95fd56`，并显式确认已在本地核验真实候选及安装配置。随后还须在专用GitHub Environment实际完成人工审批。只有这些条件成立，签发job才取得id-token:write和attestations:write；低权限前置job只读取审批配置，防止不存在的Environment自动变成无保护环境。

| Action | 确认的完整Commit |
| --- | --- |
| actions/checkout | `11d5960a326750d5838078e36cf38b85af677262` |
| actions/attest@v4 | `1e69f48acb82d1966a394da916b4c1698aa569d6` |
| actions/upload-artifact | `ea165f8d65b6e75b540449e92b4886f43607fa02` |

上述Commit通过实际GitHub API确认，GitHub Commit验证状态为valid；attest输入合同及basename主体行为另经固定版本官方源码核实。

除contents:read外，policy job的actions:read用于读取环境；签发job的actions:read用于读取环境及run approvals，id-token:write/attestations:write只用于GitHub OIDC/Sigstore签发。没有deployments:write、审批API写入、产品发布、registry推送或Release权限。

自定义Predicate URI：`https://github.com/ling5477/nexus-quant/attestations/legacy-transition-authorization/v1`；format为 `NQ_LEGACY_TRANSITION_AUTHORIZATION_V1`。

签发前Runner实际核对完整原始清单摘要、全部12字段合同、两种源码工具的固定Git字节、固定源码Tree、公开目标Manifest字节及其发行身份，并读取官方v1.0.0 Release资产digest元数据。署名内容包含完整transition、EXPLICIT_OWNER_APPROVAL、ISOLATED_V100_TO_V101_UPGRADE、准确发行源码Commit/Tree、目标Manifest/TAR、安装配置SHA、架构和项目身份，以及真实人工审批的环境/用户/run身份。

模式明确为 **OWNER_AUTHORIZED_DIGEST_ATTESTATION**：目标TAR和安装配置的本地字节校验由owner显式确认。Runner没有读取本地TAR或runtime.env，不宣称它独立构建、哈希或重新验证了整个发行包。目标包来源/本地业务资格沿用已核验且未改变的证据；此次签名的性质是对固定内容及固定实例范围的明确授权，不是新构建provenance。

签名工作流未来的Commit来自正常交付后的GITHUB_SHA；发行源码Commit始终是原1dc5ea7e…，两者分别记录。验证CLI的signer-digest/source-digest使用签名工作流自身Commit，发行源码身份则在签名Predicate中严格核对，不能用原发行Commit冒充尚未存在的工作流Commit。

## 实际GitHub人工审批配置

已实际创建 `nq-release-transition-signing`，Environment ID `25219760021`。required reviewer为ling5477（ID128130348），can_admins_bypass=false，部署分支只允许dev。prevent_self_review=false，允许唯一owner在发起后另行人工批准其明确授权请求；没有让自动化代审批，未声称独立第二维护者审批。

原aliyun环境没有修改。API实际接受并返回required_reviewers规则，因此当前公共仓库具备本任务要求的审批能力；不是只引用计划文档推断。尚无签发Run，故**没有实际审批记录可宣称通过**。环境配置证据为 `signing-environment-created.json`、`signing-environment-final.json`、`signing-branch-final.json`。

## 本地实例与公开内容

原配置真实SHA为 `0c149976a9b2c2d60f8b2dcdb7938bb4ee66706f6bfbce97e51d83593eb01954`，从正常事务保存的原始runtime.env核对，匹配清单。当前已提交V101配置SHA为 `36f3513a4db660dbae491de05e0cadb700200d343725c1fefa97e13ffc04cf14`，匹配COMMITTED事务targetConfigSha256；两者差异为既有升级变化，不使技术证据失效。

唯一实例项目为nq-v101-formal-bridge-20261024，installationId为2a94e292-6b9b-4fd8-8499-9cef3163766e。本次签名scope只授权该清单绑定的隔离V100→V101流程，不授予LIVE、Public Demo或产品发布权限。

仓库只增加两份允许公开的输入：完整固定transition及不含秘密的目标包Manifest。没有复制/上传原始runtime.env、数据库口令、JWT Secret、私钥、数据库或业务数据。审批评论也不会写入公开Predicate。工作流只上传本次授权JSON、准确清单、实际签名Bundle、验证结果及签名记录，不上传整个仓库或安装目录。

## 实际签发与验签尝试

1. 实际调用 `gh workflow run nq-release-transition-attest.yml --repo ling5477/nexus-quant --ref dev`，带固定授权短语及local_identity_verified=true。结果exit1：`HTTP404 workflow ... not found on the default branch`。远端尚未有本候选工作流；没有绕过正常PR/CI上传GitHub源码，也没有触发原全量CI。
2. 实际调用gh attestation verify原始清单，限定仓库、自定义Predicate URI、实际计划使用的工作流身份、refs/heads/dev及GitHub OIDC issuer。结果exit1 / HTTP404，未取得该subject/自定义Predicate的签名。
3. 首次验签命令误同时提供互斥cert-identity和signer-workflow，被CLI参数校验拒绝；已保留原失败并修正。有效参数的第二次尝试确实到达GitHub API，返回404，不把参数错误伪装为远端证明不存在。

证据根：`E:/nq-v101-signed-transition-authorization-20261024-1dc5ea7e/`。实际生成尝试见 `actual-signing-dispatch.stdout/.stderr/.exit`，验签见 `actual-signature-verification-r2.stdout/.stderr/.exit`。这些是当前真实失败证据，不是只生成设计报告。

将来签发后，helper直接验证本次Action产生的Bundle，强制GitHub仓库、OIDC issuer、工作流、精确工作流Commit及dev ref，并在CLI验签后继续核对证书精确SAN、完整subject、全部Predicate、owner审批、scope、实例字段和当前run。会保存实际Bundle、gh验证JSON、Attestation ID/URL、Run ID/attempt和UTC记录时间；密码学签发时间以验证JSON的verifiedTimestamps为准，不以本地记录时间冒充。

## 定向验证与独立审查

- 新增12项定向测试PASS，含PR/fork/错误actor/ref/重新运行者、未确认摘要/本地实例、缺失或错误Environment审批、管理员绕过、原始字节篡改、精确证书SAN、完整声明错配、错scope/owner/run及密码学CLI失败的有界拒绝。合成JSON只测试语义层，不证明真实签名。
- 一次真正独立的签名控制候选审查PASS，未解决P0/P1/P2为0/0/0；独立审查者重跑原10项定向测试PASS，随后仅增加2项负例，实施逻辑没有再改。
- 同次审查发现CLI signer-workflow过滤为SAN前缀；已增加certificate.subjectAlternativeName严格等值验证并测试相似文件名/其他分支拒绝。官方Action与CLI/Sigstore源码的主体及结果字段兼容性已核实。
- actionlint官方工具下载尝试因网络超时/无进展未完成，没有执行未校验的二进制，也不声称actionlint PASS。工作流正常远端PR/CI仍待交付。
- 首轮新测试清空环境导致Windows PATH丢失，仅测试夹具修正为保留执行所需系统路径；不涉及发行源码或验证规则弱化。

测试日志 `targeted-tests-r5.log`，审查记录 `independent-candidate-review.json`，Action身份 `confirmed-action-identities.json`。没有重跑148份证据所对应业务行为。

## Git交付与恢复同一签发任务

精确候选共7文件：.gitignore的两个源码白名单例外、手动工作流、签名/验签helper、其定向测试及两份公开固定输入、本报告。.gitignore例外用于将新工具正常纳入Git，不扩大正式发行source-manifest；固定发行候选来源仍为原Commit。

按照本任务第7节，下一步是该工作流候选正常Git交付及PR/CI；本轮没有Commit/Push/PR/Merge授权，不自动执行。交付至dev后，恢复**同一个签发任务**：owner核对本地输入仍一致→手动dispatch完整固定授权→在专用Environment实际人工审批→签发→下载并验证实际Bundle及全部声明。仅交付或签名Action成功均不直接授予正式产品发布资格。

## 最终输出

```text
Task: NQ-V101-SIGNED-TRANSITION-AUTHORIZATION
Release Source Commit: 1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71
Transition SHA256: c084eda5916007d90d1e3799a906ab4f7b95da0e87b8b0f3a7cb08f88d95fd56

Trust Provider: GITHUB_ACTIONS_OIDC_SIGSTORE / WORKFLOW_IMPLEMENTED_NOT_DELIVERED
Signer Identity: EXPECTED_GITHUB_WORKFLOW_CERTIFICATE / NOT_YET_VERIFIED
Signer Workflow: ling5477/nexus-quant/.github/workflows/nq-release-transition-attest.yml@refs/heads/dev
Workflow Run: NOT_CREATED / ACTUAL_DISPATCH_404
Attestation ID: NOT_CREATED
Attestation Bundle: NOT_CREATED

Signature: NOT_CREATED / NOT_VERIFIED
Authorization: OWNER_ENVIRONMENT_POLICY_CONFIGURED / ACTUAL_RUN_REVIEW_PENDING
Source Binding: EXACT_FROZEN_SOURCE_VERIFIED / FUTURE_SIGNER_COMMIT_SEPARATE
Package Binding: FIXED_MANIFEST_AND_AUTHORIZED_ARCHIVE_DIGEST / NO_RUNNER_BUILD_CLAIM
Instance Binding: LOCAL_ORIGINAL_CONFIG_SHA_MATCH / SIGNED_AUTHORIZATION_PENDING

Technical Qualification: PASS / 148_EVIDENCE_HASHES_MATCH / NOT_RERUN
Independent Trust: BLOCKED
Remaining Release Blockers: WORKFLOW_GIT_DELIVERY_AND_CI; ACTUAL_HUMAN_ENVIRONMENT_APPROVAL; REAL_ATTESTATION_AND_COMPLETE_VERIFICATION
Decision: PENDING_WORKFLOW_GIT_DELIVERY

Public Demo: NOT_READY
LIVE: DISABLED
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Tag: NOT_CREATED
Release: NOT_CREATED
```
