# L6 Chunk-Safe Secret Scan Remediation

**LOCAL_VALIDATION_PASS / INDEPENDENT_SECURITY_REVIEW_ACCEPTED / EXACT_HEAD_CI_PENDING**。

Baseline HEAD/upstream=`98a68d653efb3310e967e400d1737d4ca28f26d9`，branch=`audit/post-gatey-agent-baseline`，stage=0。旧CI 34759057064的7成功/2失败保留。入口未提交证据与tracked文件的path/size/SHA inventory已保存；未改历史report、100s smoke/calibration raw及manifest。

## 精确finding核对

固定gitleaks8.18.4、default rules、原precise allowlist、tracked safe-file/no-history/redaction模型不变。scanner rc=0照常继续；rc=2调用新verifier；其他rc直接失败。verifier完成后仍执行custom regex backstop及CI log redaction proof。

Hash-only registry仅含4个已独立确认的synthetic sampleToken，保存exact path、whole-file SHA、rule、起止行、fieldName、完整值SHA及reason/owner/removalTrigger，不保存token明文。登记指向的原始文件必须受Git跟踪，且本次staging文件字节必须与repository文件完全一致。路径越界、dot segments、符号链接/重解析点拒绝；输入大小和数量有界。

Verifier从原始完整行严格解析唯一ASCII sampleToken成员，不使用report Secret/Match/前缀。完整文件和完整值hash、路径/rule/line/field同时核对；escape、Unicode、多个字段、跨行与解析歧义全部拒绝。重复JSON键、畸形registry/report、重复/未知/缺失映射及扫描模型漂移失败。actual finding集合必须与registry双向完全一致，仅在全部成功后输出REVIEWED_FALSE_POSITIVE元数据；任何异常均只输出固定失败类别，不输出原文或credential-like值。

## 实证与负例

当前候选按同一safe-file过滤模型扫描4057个文件（tracked集合加即将交付的3个新控制文件）：raw findings=4，reviewed=4，unknown=0，secret gate PASS。

独立临时Git fixture仅改变完整token末尾序号，保持同字节长度及相同gitleaks truncated Secret；scanner仍返回4条，但verifier以FILE_SHA_MISMATCH拒绝。单元测试另将file hash显式同步到变更fixture，完整值hash仍以FULL_VALUE_SHA_MISMATCH拒绝，证明不是仅靠文件hash偶然挡住。

Linux verifier 9 tests全部PASS；Windows同9 tests，1个因符号链接权限skip。覆盖4/4正例、prefix/suffix、错字段、escape/Unicode/多字段歧义、staging差异、文件/行漂移、未知rule/path/extra finding、重复registry/report、missing entry、unsafe/untracked/symlink source、坏JSON/duplicate keys及日志不泄露。既有6个secret负例仍由generic-api-key拒绝；scanner missing/runtime error仍fail closed。custom regex backstop=0 findings。

## Stage登记与候选保护

新增loader的FIXTURE_IDENTITY精确登记，以及hash-only reviewed registry的GOVERNANCE_CONTRACT精确登记；后者出现的阶段路径仅定位历史evidence。使用现有schema/kind，未修改checker/detector/active roots。ci.yml既有entry的hash更新通过canonical lifecycle proposal审查和应用。Stage tests 59项，Windows2个平台skip；实际loader constant单字节漂移负例被拒绝。

Formal runtime候选10文件、sampler/capacity、canonical manifest与全部既有raw字节不变。100s smoke PASS直接复用，不执行smoke/calibration/60min/180min/Full Maven。Production delta=0，L6仍NOT_ACCEPTED。Integration最终接受依赖本轮新exact-head CI 9/9，不能复用旧失败CI。

## 审查与交付

定向独立review及精确交付状态将记录于本报告机器摘要。实现/测试与raw来源记录在[机器摘要](L6_CHUNK_SAFE_SECRET_SCAN_REMEDIATION.json)，最终HEAD/CI在CI结束后另行追加本地delivery结果，避免将交付前状态伪装为已完成。

独立审查结论INDEPENDENT_SECURITY_CONTROL_REVIEW_ACCEPTED，P0=0、P1=0，起止6文件fingerprint一致、stage=0。批准proposal SHA=`95f591b05b48b88008d0fb578326ae528ec42d3dca417607ecda922b0e14a901`已由canonical lifecycle应用，最终checker scanned=1953、reviewed=175、errors=0；applied policy与已审proposal完全相同。[完整证明包](L6_CHUNK_SAFE_SECRET_SCAN_REMEDIATION_PROOF.zip)保存原始报告、关键正反例与审查身份。
