# NQ-V101-TRANSITION-TRUST-ANCHOR-CLOSEOUT

日期：2026-10-24（Asia/Shanghai）。结论：**BLOCKED / INDEPENDENT_TRUST_ANCHOR_MISSING**。原技术桥接资格仍为 **PASS**，本轮没有重跑研究、回测、SIM、迁移或恢复，没有创建签名、密钥、Tag或Release。

固定清单、冻结源码、真实制品、实例原始配置/当前已提交配置及148份原资格证据均匹配。实际GitHub Attestations查询和CLI验证未取得可验证的签名主体，亦未找到可用的预先受信维护者签名身份。完整实例授权签名仍缺失，不能将此前隔离测试豁免或源码Commit的有效签名升级为完整清单的发行信任。

## 候选保护与固定来源

| 项目 | 实际身份 |
| --- | --- |
| Repository | ling5477/nexus-quant，GitHub repository ID 1156012145，public |
| Source Commit | `1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71` |
| Source Tree | `c323207865b8ef6a978746dd2ae63f15fdcc3ca4` |
| Version / Schema | 1.0.1 / 47 |
| 原工作分支 / HEAD | `codex/nq-v101-legacy-upgrade-bridge` / `129fd8fb53326925e3d69db7a251fe4e24c95a79` |
| 开始时Staged Tree | `3620ad2dbb93cb89983ea229205b2b375de65a13` |
| 原暂存文件 | 仅NQ_V101_FORMAL_BRIDGE_RELEASE_QUALIFICATION.md；其index blob和工作区字节保持原样 |
| 原未跟踪报告 | SECURITY_PATCH_CLOSEOUT及FORMAL_UPGRADE_QUALIFICATION_RESUMED均保持原字节 |

按要求读取Bridge、source-manifest和build-package，并逐项与固定Git对象核对。发行清单包含Bridge及Baseline Tool；打包器实际复制Baseline Tool，已有最终包文件字节与冻结Git一致。没有通过修改源码、安装器或过渡清单取得通过。

外部材料根：`E:/nq-v101-transition-trust-closeout-20261024-1dc5ea7e/`；保护记录为 `protected-candidate.json`，完整绑定结果为 `candidate-bindings.json`。旧报告与原证据目录没有被覆盖。

## 原始清单与全部12字段

原文件：`E:/nq-v101-formal-bridge-qualification-20261024-1dc5ea7e/transition-v1.json`。

实际重新计算SHA256：`c084eda5916007d90d1e3799a906ab4f7b95da0e87b8b0f3a7cb08f88d95fd56`。无重复字段，字段集合与冻结Bridge的NQ_LEGACY_BRIDGE_V1合同完全一致，没有UNCOMMITTED或local-candidate路径。

| 字段 | 实际值 / 核验 |
| --- | --- |
| format | NQ_LEGACY_BRIDGE_V1 |
| bridgeSourceCommit | `1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71` |
| bridgeSha256 | `555d6471291757edd3314293e3ff82bffb6a8cc72093ce7be42ce763d04948b8`；实际包文件=冻结Git源码 |
| baselineToolSha256 | `523305aae28e4d8b3a6c500b592d84bdf7ff5669b7c45b3f9c17d03ae9def51c`；实际包文件=相同冻结来源 |
| sourceManifestSha256 | `c0d350875c822c48edd489992fe27a0cf5e5c9dae44d1dd772938dd33b482ff1`；官方v1.0.0 ZIP内容匹配 |
| targetManifestSha256 | `c3437d4bab960546cfba57d065cda375610ccd700f593ea95827a77020e1a67d`；实际目标Manifest匹配 |
| targetArchiveSha256 | `477e41ac8fc838063257142f944e0465a5d1fe1ee727853e30a8795f4bbbfef2`；实际images-amd64.tar重新哈希匹配 |
| targetSourceCommit | `1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71` |
| architecture | amd64 |
| installationConfigSha256 | `0c149976a9b2c2d60f8b2dcdb7938bb4ee66706f6bfbce97e51d83593eb01954`；原始配置的真实字节匹配 |
| projectName | nq-v101-formal-bridge-20261024 |

额外核对2182份源码导出文件的实际SHA与冻结导出身份一致；Source Manifest SHA `6e4268ffd9479992f225bd8a2c2470fbceefaef3fe7afedba1938af35e2157e1`、Release Source Hash `58da58497d748e913ccfd5a28f2dc62fd1a5afdf6ef346ee94da4e0f4ac779f8`。这是字节/来源完整性复核，不是新依赖扫描或业务测试。

Bridge Git blob `8523dcd61cebee7b212beb4326b7fa157113f848`；Baseline Tool Git blob `3f9ec653989d0a53f9325ec4614d3f43bed3d393`。当前工作区对应源码的Git内容也与固定对象一致，未把本地HEAD或暂存报告作为发行输入。

## 官方原包及目标包身份

GitHub API重新确认已发布v1.0.0 Release 418603194 / ZIP asset659954162；独立发布记录给出的ZIP摘要 `sha256:37d79f1b752cd1a4967cc4a5acec4a51f3074c636f3b3be1be5ecc335e6b4931` 与本地原始官方ZIP重新哈希一致。ZIP内原Manifest与清单sourceManifest一致，19份原包文件逐项相同。没有重新构建或修改v1.0.0。证据为 `github-official-v100-release.json` 与 `candidate-bindings.json`。

目标实际Manifest的SOURCE_SHA为固定Commit、VERSION1.0.1、SCHEMA47、RELEASE_SOURCE_HASH与冻结导出一致；实际TAR的SHA为477e41ac…，实际compose/runtime/PS/SH资产SHA均与Manifest一致。当前目标配置的三种镜像身份与Manifest一致。此前更完整的OCI/config/layer和包内文件验证在148份已重新核对的资格证据中，未重建或重跑。

这些事实证明当前字节与已经验证的候选相同；**目标包尚无可验证签名，不能将本地hash与Manifest自声明当成独立签名背书**。

## 独立来源与实际签名验证

| 探查 | 实际结果 | 可得结论 |
| --- | --- | --- |
| GitHub源码Commit签名元数据 | verified=true / reason=valid / tree匹配 | 只证明源码Commit的GitHub验证状态，不覆盖本地清单或目标包 |
| 固定源码CI run40085665454 | completed / success / head_sha固定Commit / path=.github/workflows/ci.yml | 复用准确来源CI；不等同Artifact Attestation |
| 当前远端workflow列表 | NQ CI Baseline及动态Dependency Graph | 没有现成发行/实例清单签名工作流 |
| 固定源码ci.yml | contents:read；无id-token:write、attestations:write或attest签名action | 不能用原CI产生本次OIDC签名 |
| 源码CI现存Artifacts | backend/frontend delivery evidence、internal-delivery-provenance、schema及shadow报告 | 不是本次目标images TAR或完整transition签名；普通Artifact digest不等于签名证明 |
| 五个实际subject查询 | transition、Bridge、Baseline、目标Manifest、目标TAR均HTTP404 | 未取得可访问签名；404不被当成全局绝对不存在的证明 |
| 实际gh attestation verify | exit1 / HTTP404 | 无签名可供证书、主体、时间戳和实例授权验证 |
| GitHub公共SSH signing keys / GPG keys | 维护者ling5477两项API均返回空列表 | 当前未取得可用公开签名身份；不推断没有任何离线私有密钥 |
| 本机Git signingkey / format | 未配置；ssh-agent连接不可用 | 没有现成可用且已建立信任的本地签名路径 |

实际CLI命令（只读，无上传、无workflow dispatch）：

```text
gh attestation verify <original-transition-v1.json>
  --repo ling5477/nexus-quant
  --source-digest 1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71
  --signer-workflow ling5477/nexus-quant/.github/workflows/ci.yml
  --signer-digest 1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71
  --format json
```

这是对唯一现有仓库CI身份的有界验证探查；没有将任意未来workflow自动列为可信。默认SLSA provenance探查失败；另外不限定predicate的subject API查询同样404，故没有遗漏一个已返回的自定义实例predicate再误称不存在。完整原始响应、命令、exitCode和输出hash保存在各 `.result.json/.stdout/.stderr`，汇总为 `remote-trust-summary.json`。

`gh attestation trusted-root`实际成功，下载根材料SHA256 `65ca537f6ed8a47fd0e560c421baa1f6c1efb8b25fc200d8c5c02c0e92eb2b9c`，保存于 `github-sigstore-trusted-root.stdout`。该根材料可供GitHub/Sigstore验证器使用，但**取得根材料不等于有主体签名**；本轮没有可验证SAN、OIDC签发身份、签名工作流digest或该subject的透明日志证明。

GitHub Actions/Attestations具备签名内容摘要及自定义声明的能力；缺的是当前候选的可信签名执行、真实资产输入和实例授权证明，并非宣称平台天生无法签完整清单。只签通用包或把操作者输入的摘要原样代签，不能证明本次12字段实例授权。维护者替代路径也需要事先可信公钥/身份根；本轮没有新建自签密钥并自行宣布其可信。

## 原始配置、当前配置与实例范围

安装身份 `2a94e292-6b9b-4fd8-8499-9cef3163766e`；安装根 `E:/nq-v101-formal-bridge-qualification-20261024-1dc5ea7e/installation`；项目 `nq-v101-formal-bridge-20261024`，amd64，授权仅限这一任务自有隔离安装。

- 清单绑定的是升级前V100/V34配置。读取正常Bridge事务 `20261024T125624000Z-6775b954/runtime.env` 的原始字节，SHA准确为 `0c149976a9b2c2d60f8b2dcdb7938bb4ee66706f6bfbce97e51d83593eb01954`，与清单和持久回执sourceConfigSha256一致。
- 当前已升级且完成恢复验证的配置SHA为 `36f3513a4db660dbae491de05e0cadb700200d343725c1fefa97e13ffc04cf14`，与COMMITTED事务targetConfigSha256准确一致；版本/Schema/Source/Manifest/三个镜像也匹配。
- 两份配置均绑定相同NQ_HOME、project、amd64；原maintenance/JWT/credentials身份保持，当前运行角色格式2且口令与maintenance不同。只在内存比较秘密，未打印、复制、上传runtime.env、口令、JWT或私钥。
- 原始配置与当前配置摘要不同是有记录的预期升级变化，不是输入漂移。完整实例的**本地绑定已核对**，但实例级的独立签名授权仍MISSING，不能仅凭未来通用包签名将其升级为VERIFIED。

`unsigned-signing-inputs.json` 汇总精确subject、全部字段、源码/包身份、原/目标配置摘要和实例限制，只是未签名交接输入。旁置清单为原字节复制；没有上传。本文件明确标记UNSIGNED_REVIEW_INPUT_NOT_TRUST_EVIDENCE，不能充当签名或授权根。

## 技术证据复用与影响范围

原 `qualification-result.json` 中148份证据的实际SHA全部匹配；原技术报告、Bridge、Baseline、完整源码导出及目标包字节未变，原/目标配置与相应事务分别匹配。Bridge Upgrade、PG Transition、V34→V47、RLS、Canonical SIM、Installer Handoff、Failure Recovery和旧经济事实保持的既有PASS可在原Windows11 amd64/Docker及隔离授权边界内复用。

没有影响技术资格的字节或意外配置变化，没有需要作废的原技术结果；没有重跑业务、数据库或恢复。此前TRANSITION_TRUST_EXCEPTION仍仅是隔离测试豁免，未被本轮改写。

本轮仓库只增加本收尾报告，没有修改任何正式源码或签名工作流。现有基础设施无法产生所需签名，且本任务禁止自动Git发布；只新增本地未执行的签名YAML也不会产生本轮有效签名。未将新源码Commit人为设为固定候选的来源前提。原两个未跟踪报告、原暂存资格报告和全部原始材料保持。

## 未解除阻断与最小后续事项

当前缺少：**覆盖固定过渡清单及本次实例授权的可验证签名、可信签名主体和对应真实制品绑定**。

后续任选一条真正可信路径：

1. 使用已事先建立公钥/身份信任的维护者签名方案，对完整清单摘要及明确实例范围签名，提供可独立验证材料；核验者仍需核对原包/目标包及原/目标配置绑定。
2. 若采用Actions，交付具有明确受信身份、最小OIDC/attestation权限和实例授权检查的签名流程，让可信runner取得并核对本次真实目标Manifest/TAR及固定源码，然后签完整清单subject与实例范围；取得实际bundle/run/identity后再限定复核。现有泛化CI、普通Artifact digest或只签通用包不足以替代。

本轮不执行上述Git交付、重新构建、正式发布或升级重测，也不以新签名或新workflow的存在直接授予全产品/全平台发布资格。

## 最终输出

```text
Task: NQ-V101-TRANSITION-TRUST-ANCHOR-CLOSEOUT

Source Commit: 1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71
Source Tree: c323207865b8ef6a978746dd2ae63f15fdcc3ca4

Transition Manifest: E:/nq-v101-formal-bridge-qualification-20261024-1dc5ea7e/transition-v1.json
SHA256: c084eda5916007d90d1e3799a906ab4f7b95da0e87b8b0f3a7cb08f88d95fd56

Trust Provider: GitHub Actions / Artifact Attestations INVESTIGATED; NO_USABLE_SUBJECT_PROOF
Signing Identity: NOT_ESTABLISHED
Trusted Root: GITHUB_SIGSTORE_ROOT_FETCHED / NO_SUBJECT_SIGNATURE_VERIFIED
Attestation Reference: NOT_OBTAINED / SUBJECT_API_404
Attestation Subject: transition-v1.json / sha256:c084eda5916007d90d1e3799a906ab4f7b95da0e87b8b0f3a7cb08f88d95fd56
Verified Source Commit: 1dc5ea7ea5971a7621c3bbfc66a0e9fe8cd14d71 / GITHUB_COMMIT_VERIFICATION_VALID
Verification Result: BLOCKED / SIGNATURE_AND_SIGNED_INSTANCE_AUTHORIZATION_MISSING

Bridge Identity: 555d6471291757edd3314293e3ff82bffb6a8cc72093ce7be42ce763d04948b8 / FROZEN_GIT_AND_PACKAGE_MATCH
Baseline Tool Identity: 523305aae28e4d8b3a6c500b592d84bdf7ff5669b7c45b3f9c17d03ae9def51c / SAME_FROZEN_SOURCE_MATCH
Source Package Identity: c0d350875c822c48edd489992fe27a0cf5e5c9dae44d1dd772938dd33b482ff1 / OFFICIAL_V100_RELEASE_ZIP_MATCH
Target Package Identity: MANIFEST_c3437d4bab960546cfba57d065cda375610ccd700f593ea95827a77020e1a67d / TAR_477e41ac8fc838063257142f944e0465a5d1fe1ee727853e30a8795f4bbbfef2 / LOCAL_BYTES_MATCH_NOT_SIGNED
Installation Identity: 2a94e292-6b9b-4fd8-8499-9cef3163766e / nq-v101-formal-bridge-20261024 / ORIGINAL_AND_TARGET_CONFIG_LOCAL_BINDING_MATCH / SIGNED_AUTHORIZATION_MISSING

TRANSITION_HASH: LOCAL_BYTE_VERIFIED
INDEPENDENT_TRUST_SOURCE: NOT_VERIFIED_FOR_COMPLETE_TRANSITION
SIGNATURE: NOT_VERIFIED
RELEASE_ARTIFACT_BINDING: LOCAL_AND_GIT_VERIFIED / INDEPENDENT_SIGNATURE_NOT_VERIFIED
INSTALLATION_BINDING: LOCAL_VERIFIED / SIGNED_INSTANCE_AUTHORIZATION_MISSING
TECHNICAL_BRIDGE_VALIDATION: PASS / INPUTS_UNCHANGED / NOT_RERUN
Technical Qualification: PASS
Formal Release Trust: BLOCKED
Remaining Release Blockers: TRUSTED_SIGNATURE_OVER_COMPLETE_TRANSITION_AND_INSTANCE_AUTHORIZATION
Decision: BLOCKED / INDEPENDENT_TRUST_ANCHOR_MISSING

Public Demo: NOT_READY
LIVE: DISABLED
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Tag: NOT_CREATED
Release: NOT_CREATED
```
