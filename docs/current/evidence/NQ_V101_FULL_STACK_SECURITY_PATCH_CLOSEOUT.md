# NQ-V101-FULL-STACK-SECURITY-PATCH-CLOSEOUT

Task: `NQ-V101-FULL-STACK-SECURITY-PATCH-CLOSEOUT`

执行日期：2026-10-23（Asia/Shanghai）。Decision: **BLOCKED**。

本轮只补 Temurin CVE-2026-91403 资格及[原报告](NQ_V101_FULL_STACK_SECURITY_PATCH.md)已经列明的构建链缺失证明。没有重新扫描依赖、没有重新调用OSV/npm audit/pip-audit/Trivy、没有重跑业务回归。原9个暂存文件和报告保持不变；本报告单独新增、未暂存。没有修改基础镜像、证书库、业务代码或Flyway，因此不触发镜像重建/启动复测。

## 1. 候选保护

- Branch: `codex/v101-full-stack-security-patch`
- HEAD: `1b467fdd20ba799eab99675343aa6eaeb58c956d`
- Staged Tree: `7c7e17fd8eacf4e1d05b25e26d8d4c4fa9fc7169`
- Staged Files: **9，原清单不变**。
- 原始index blob、index SHA256、工作文件SHA256及binary patch：[candidate-before.json](../../../artifacts/v101-security-closeout/candidate-before.json)、[candidate-before.patch](../../../artifacts/v101-security-closeout/candidate-before.patch)。收尾核对：[candidate-after.json](../../../artifacts/v101-security-closeout/candidate-after.json)。

Changed File Hashes（SHA256；工作文件与index内容一致）：

| Staged file | SHA256 |
| --- | --- |
| `.github/workflows/ci.yml` | `920866ddbc4ca39aa550cdee87dbd76a4392b0021674bf42794c4307979926dd` |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/JdbcDriverSecurityPostgresTest.java` | `f21412ea0c680a85ff0c9131aec6ec8427089ae8befe44dc8c36de444a84423e` |
| `backend/pom.xml` | `8359edc076a70109e5ce034b494ebb790f30ffe1e6002aebcee73ce75ee75449` |
| `docs/current/evidence/NQ_V101_FULL_STACK_SECURITY_PATCH.md` | `6c26455975d081ee2cb497a410d2eb381d03eb9aa43fe7f034253572af07c73a` |
| `frontend/package-lock.json` | `f0138c05bde9f66fcbfa9713308d26ccfaab937f154e26c424964ac714e5fe29` |
| `frontend/package.json` | `1bd6a79a863651e64aa94260578983181631198583742ebdb64d7130265b9bbc` |
| `release/docker/backend.Dockerfile` | `922a760745c3d7abe72b5be84d0bef8c05095ba818bf0ace5d3912e1da00c11d` |
| `release/docker/frontend.Dockerfile` | `f9a7f94df34a6750e2a6df73643b46d31d74b7ac1e6c00da5b22a650cae7f68a` |
| `research/py/pyproject.toml` | `e4ce473cf695ce6cd528e0aa2a945501ff7fdb4a69804451ba4961acb8ae03d2` |

## 2. Temurin 供应商与不可变身份

以下均为本轮实际Docker镜像读取/执行；不是仅从Docker tag推断版本。

| 项目 | Runtime JRE | Maven Build JDK |
| --- | --- | --- |
| Vendor | Eclipse Adoptium | Eclipse Adoptium |
| 完整版本 | Temurin-21.0.12.1+1；21.0.12.1+1-LTS；2026-08-18 | 同左 |
| Java home | /opt/java/openjdk | /opt/java/openjdk |
| 默认TrustStore实际路径 | /opt/java/openjdk/lib/security/cacerts | 同左 |
| cacerts SHA256 | 98d34a90fca2688ef5674862d2a484e09e5ca25d2f07c5d654aa28f1581f3419 | 同左 |
| blocked.certs SHA256 | 96572f243f31c2ef81a6e627542e596f6a9295cff3c7ae095c1b595cb1457ded | 同左 |
| blocked.certs条目 | 38个SHA256 | 同左 |
| 默认信任锚 | 118 | 118 |
| jssecacerts | 不存在 | 不存在 |
| 实际镜像digest | Self-Hosted: sha256:fe38dbecbaa6a7246b4a5d6a588f6738770a7e6545eff008e9e59ffb1c845ebe | sha256:55ba61f691e4f75db16e33cc3864f2c4aea19b4e776b80194505f00f1fd636e6 |
| Public Demo复核 | sha256:06dc551c79bb0ae5ee896967715cde6dafc12a396b04f060ca5e7b9df7533efc；相同证书材料hash与测试结果 | 不适用 |
| 基础镜像digest | eclipse-temurin:21-jre-jammy@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0 | maven:3.9.16-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320 |
| Maven | 不在产品JRE中运行 | 3.9.16，commit 2bdd9fddda4b155ebf8000e807eb73fd829a51d5 |

证据：[image-config.json](../../../artifacts/v101-security-closeout/image-config.json)、runtime/build-java-trust.txt、runtime/demo/build-trust-material.txt。实际镜像RepoDigests和本地Id分别保存在JSON；这不是远端发布/上传证明。

覆盖检查：默认TrustManagerFactory实际加载118个锚；javax.net.ssl.trustStore、trustStoreType及java.security.properties均未设置。镜像Env未配置覆盖；backend入口直接java -jar，Compose只设置内存JAVA_TOOL_OPTIONS。对backend Java/YAML/properties的定向搜索未发现SSLContext/TrustManager/trustStore消费者。Temurin附带的/__cacert_entrypoint.sh包含USE_SYSTEM_CA_CERTS可选导入逻辑，但本候选未启用；运行入口及Maven入口均另行记录。该结论只适用于当前镜像和仓库运行配置，不能排除未来部署者传入自定义TrustStore。

### 官方修复关联

1. 复用[Oracle正式告警](https://www.oracle.com/security-alerts/alert-cve-2026-91403.html)：eMudhra中间CA失信，21.0.13在Oracle受影响表内；不能直接套成Temurin修复判定。
2. 本轮取得[OpenJDK JDK-8395142提交](https://github.com/openjdk/jdk/commit/22bc7357ce6bc5f1ed2f1e83e0544b2c25f781d4)及[JDK21回移提交](https://github.com/openjdk/jdk21u/commit/85974eed594aa5a9e13a6a20713156ed8d9cd0ed)。固定源码中新增16张eMudhra中间证书，并修正BlockedCertsConverter按签名算法/签名长度生成ECDSA另一编码的规则。
3. 使用该固定源码及实际build JDK生成**独立证据文件**upstream-fixed-blocked.certs：86条；当前镜像38条，缺48条，16个新增证书原始指纹全部缺失。没有安装或挂载该文件替换JRE的blocked.certs。源码及比较见[jdk21u-fix.json](../../../artifacts/v101-security-closeout/jdk21u-fix.json)、[blocked-source-comparison.json](../../../artifacts/v101-security-closeout/blocked-source-comparison.json)。
4. Temurin官方latest仍为jdk-21.0.13+9；本轮取得Linux x64供应商build metadata，其scmRef=jdk-21.0.13+9_adopt，解析到2ab10ca20c191701c3a9f3153bfc6e93d63714c1。**该精确供应商源码中16个新增证书一个也没有**。OpenJDK修复相对于jdk-21.0.13+9为ahead 2，behind 0。不能把这个正式版本当成已修复版本，也没有据此更换镜像。
5. JDK-8395555的JBS REST读取返回401；JDK-8395142的JBS读取超时，源码提交可读。8395555精确issue关系仍UNVERIFIED，不能把搜索无结果当作不存在。ops.java/emudhra返回403。所有失败保留，不声称已读取受限公告。

本轮未确认包含该修复的正式Temurin供应商包/镜像；没有制作临时证书库补丁。供应商源码对比证明当前候选缺少上述阻断材料，但不单独证明当前NQ对外存在可利用TLS链。

### 实际Java信任链实验

使用[CertificateProof.java](../../../artifacts/v101-security-closeout/CertificateProof.java)，由实际Maven build JDK编译，分别在Self-Hosted JRE、Public Demo JRE和build JDK执行。容器--network none、--read-only，唯一额外挂载是只读测试类和公开证书；没有替换任何信任锚。

负向输入来自固定OpenJDK修复提交新增的16张公开证书，逐张记录Subject、Issuer、SHA256、有效期、匹配默认根数量和PKIX结果。正常控制是从repo.maven.apache.org以默认验证TLS、启用hostname校验抓取的真实4证书链；随后离线由Java PKIX及默认JSSE TrustManager实际验证。测试日期取实际时钟，离线关闭撤销查询以隔离本地信任/阻断行为，不将此实验解释为在线OCSP/CRL资格。

| 测试 | 最终SH JRE | 最终Demo JRE | 实际Build JDK |
| --- | --- | --- | --- |
| 16个受影响中间证书目标的PKIX路径建立 | 16 REJECTED | 16 REJECTED | 16 REJECTED |
| 负向拒绝原因 | 无法构造到默认锚的路径；16个直接issuer根均不存在 | 同左 | 同左 |
| 正常真实链PKIX | ACCEPTED，pathLength=4，ISRG Root X1 | 同左 | 同左 |
| 正常真实链JSSE checkServerTrusted | ACCEPTED | ACCEPTED | ACCEPTED |
| 完整受影响链/交叉签名路径与编码变体阻断资格 | **NOT_VERIFIED** | **NOT_VERIFIED** | **NOT_VERIFIED** |

**16次REJECTED不能升级为CVE FIXED或VERIFIED_MITIGATION**：缺少根的输入不能证明主动distrust，也没有完成可到达默认锚的受影响完整链、交叉签名路径及编码变体的拒绝证明。未对受影响链执行端到端TLS握手。正常链通过只是有效正控制。脚本退出0仅表示记录完整、已测正负输入结果符合记录，**不表示安全资格PASS**。

可复现入口：[run_certificate_proof.py](../../../artifacts/v101-security-closeout/run_certificate_proof.py)；精确命令、类/源码hash和结果：[certificate-results.json](../../../artifacts/v101-security-closeout/certificate-results.json)。原始首次结果及后续拒绝原因诊断均保留。

## 3. 从原报告逐项提取的构建链证据缺口

本表的Missing Evidence栏区分本轮已补与尚缺。详细CSV：[build-chain-gaps.csv](../../../artifacts/v101-security-closeout/build-chain-gaps.csv)。没有针对下列组件新增全量漏洞扫描或升级。

| Evidence ID | Affected Component | Required Proof | Existing Evidence | Missing Evidence | Blocking Reason |
| --- | --- | --- | --- | --- | --- |
| BC-01 / NQ-SEC-J08 | Maven active plugin realm | 实际构建插件身份、现有24条匹配的逐调用方可达性或安全版本闭包 | 原报告§4 J08；osv-final-matches.json；maven-resolved/*；原24条明细已引用 | 已补7个实际构建插件descriptor及SHA256、14个原受影响坐标的映射；仍缺各漏洞函数/输入到执行路径的完整证明 | BLOCKING：不能把缓存中存在、descriptor缺席、应用BOM覆盖或本机Maven3.9.12树当作容器3.9.16安全闭包 |
| BC-02 / NQ-SEC-N01 | Node/npm build tool | 区分实际发行构建器与宿主旧工具，锁定实际版本及输入 | 原报告§3/§4 N01；固定Node基础镜像和成功构建记录 | 已补实际Node24.22.1/npm11.19.1、node二进制hash、内嵌组件版本及容器lock hash；宿主24.16.0仍未更新 | 发行构建器身份缺口已关闭；宿主维护项NON_BLOCKING，不能称全机修复 |
| BC-03 / NQ-SEC-P02 | CPython / research CI | CI3.11实际patch身份及解释器公告适用性 | 原报告§3/§4 P02；pip-audit/研究源码边界；CI只声明3.11 | 当前候选没有远端CI执行，无法从浮动3.11推导实际patch；本机3.14.2仍未完成补丁资格 | UNVERIFIED：保留原构建链覆盖缺口；不以pip零发现替代解释器证明，不另造本机CI执行结果 |
| BC-04 / NQ-SEC-O03 | Node builder zlib1g / CVE-2023-45853 | 精确发行版包、contrib minizip适用性和实际链接 | 原报告§4 O03；原Trivy OS JSON | 已补zlib1g=1:1.2.13.dfsg-1、包文件清单、无minizip命名文件、Node ldd不链接系统libz、Node内嵌zlib1.3.2.1-motley-285e94b；发行版官方适用性页面两次网络超时 | PARTIAL：缩小到已检查Node消费路径；不以文件名/ldd单独证明所有构建工具静态链接闭包安全 |
| BC-05 / NQ-SEC-O04 | Maven builder OpenSSL / CVE-2026-84782 | 精确包版本及DTLS重传触发路径排除/补丁 | 原报告§4 O04；原OS匹配；构建使用Maven HTTPS | 已补openssl和libssl3t64均3.0.13-0ubuntu3.15、OpenSSL构建日期/配置；仍缺整个builder消费者的DTLS调用闭包 | UNVERIFIED：HTTPS事实仅支持已知Maven路径，不删除原2个High包匹配 |
| BC-06 | PowerShell / Bash / .NET host tools | 宿主发行打包patch、MSRC/.NET覆盖证明 | 原报告§3和§8；PowerShell7.6.7/Bash5.2.21及旧公告结果 | 完整宿主补丁/公告闭包不存在；本轮不扩展成宿主全量安全审计 | UNVERIFIED：保持原宿主构建链证据限制；没有新增已确认可利用缺陷 |
| BC-07 | SBOM实际覆盖范围 | 应用、构建、OS、最终制品组件之间可核对边界 | 原报告§2/§5/§7；后端93组件、前端production SBOM、77/82个实际JAR库 | 已补逐制品差异：后端各缺spring-boot-jarmode-tools3.5.16，补实际嵌入JAR hash；前端5个lock坐标未在production SBOM中；插件和工具闭包仍非应用SBOM覆盖 | 覆盖边界已核实并补清单；完整安全覆盖仍UNVERIFIED，不把SBOM文件存在当全栈证明 |
| BC-08 | 下载工具/依赖来源与校验和 | 实际工具字节与可信下载源/固定校验和关联 | 原报告§6；supply-chain-lock；6个Actions SHA上游核对；Trivy固定digest及DB日志；npm206签名/56attestation | 已补CycloneDX2.9.3 Maven Central实际下载与本机执行JAR SHA256一致；gitleaks归档和官方checksum本轮下载超时；npm lock含126个npmmirror与106个npmjs URL，232项均有integrity | PARTIAL：不能把官方registry命令参数等同全部归档来自官方；旧签名证据可复用，但不证明全工具传递依赖安全或此次CI实际执行 |
| BC-09 | 构建输入→制品身份 | 当前输入hash、镜像ID、实际JAR、SBOM身份关联 | 原报告§1/§5；distribution-images.json、runtime-images.json、final-image-java-libraries.json | 已重算3185个构建输入，sourceSha256完全匹配；原370份证据hash全部匹配；仍无提交身份/远端exact-head CI/签名正式包 | LOCAL_ONLY身份缺口已关闭；正式交付NOT_RUN，非本轮另行commit理由，不能把HEAD基线SHA当已提交补丁 |
| BC-10 | 固定镜像与动态下载边界 | 精确基础digest、实际最终镜像与可重现性边界 | 原报告§5四个FROM固定digest；PostgreSQL两个digest分别验证版本 | 已补实际镜像配置/层身份；基础digest与最终镜像分别列示；apt curl和研究pip范围约束仍不是全部下载字节固定 | 镜像身份已核实；完全可重现构建仍UNVERIFIED，不从FROM锁定推导所有后续层可重现 |

### 补充证明的解释边界

- Maven：已提取实际7个plugin.xml及插件JAR SHA256，见[actual-build-plugin-descriptors.json](../../../artifacts/v101-security-closeout/actual-build-plugin-descriptors.json)。容器resources3.4.0、jar3.5.0、install3.1.4与原本机解析部分版本不同。因此原24条BUILD_TIME匹配不是重新扫描后的实际容器计数；按原Finding ID逐条保留在[active-plugin-identity-mapping.json](../../../artifacts/v101-security-closeout/active-plugin-identity-mapping.json)。descriptor声明依赖不证明goal执行，更不证明漏洞函数被调用；未出现于所收集descriptor的坐标也不能自动关闭宿主/CI工具路径。构建镜像中Jackson2.21.4、HttpClient5.5.2等实际JAR仍有字节hash记录。
- SBOM：[sbom-coverage-reconciliation.json](../../../artifacts/v101-security-closeout/sbom-coverage-reconciliation.json)将后端93组件SBOM与最终77/82个库匹配到76/81个；差项均为打包器注入的spring-boot-jarmode-tools3.5.16。补录嵌入JAR SHA256=`d05beb46a7eac0f1a06733c75828b190a1cb250076ce03be2213aca4a5c0f03f`，两发行一致。前端SBOM116组件；不能以lock的dev=false简单推导运行时，@types/react/typescript标为devOptional，其余csstype/debug/ms也不在该production SBOM中。构建工具由完整lock而非production SBOM表达。补清单不等于新增安全扫描或证明最终bundle所有模块的实际执行可达性。
- 下载来源：[source-and-sbom-supplement.json](../../../artifacts/v101-security-closeout/source-and-sbom-supplement.json)记录232个lock条目全部有integrity，其中126个resolved指向registry.npmmirror.com、106个指向registry.npmjs.org。Dockerfile运行npm ci，不能用原报告“显式官方registry”措辞推导所有归档下载都来自官方。原206签名/56attestation验证结果及其hash仍可复用，未重跑。未更改lock来源或宣称镜像站包已被利用。
- CycloneDX：本机实际执行的2.9.3插件JAR与本轮从Maven Central取得的JAR，SHA256均为`c452d5eebe28bc86bef2e7c72d129f04f60877bef843eac8120f01fb655be293`；另核对Central SHA1。SHA1仅作上游传输比对，不充当签名/可重现源码证明。lock中的源码commit继续引用，不能据此推导整个工具闭包安全。gitleaks的固定URL/SHA256合同存在，但本轮官方归档和checksum下载均超时，没有运行扫描器或宣称新下载验证成功。
- 原始370份证据逐文件hash全部匹配，见[previous-evidence-integrity.json](../../../artifacts/v101-security-closeout/previous-evidence-integrity.json)。构建输入采用原build-distributions.ps1同样的路径清单/排序/hash算法，3185文件重新计算为`2d9da644900e7ee98138dc74da9329ff03c6edf46d28f37bcd2943608694e6c2`，与buildId=`4484ff4b58554ba3ad8b9b193fd7dfb3`完全相同，见[build-input-recheck.json](../../../artifacts/v101-security-closeout/build-input-recheck.json)。SH/Demo应用JAR SHA256分别为`30348bc425b3bdc3fe281c2fe870d5900a593533e2d0f381436451e7cd92d838`、`a2a5fc016dfcde12fb423dbe3535b1e0d8e209d7d64780bae73c6be604634db0`。这些是本地工作区→镜像→JAR证据，sourceDirty=true，仍非不可变提交的远端构建证明。
- 读取builder工具时--read-only令Jansi临时锁文件创建失败；该记录同时有完整mvn -version、包版本与hash。查找builder内CycloneDX目录返回不存在（它在本机生成SBOM），没有把这次组合命令的非零退出当测试PASS。之后插件descriptor通过独立只读归档读取完成。

Dependabot403、Windows POSIX mode、arm64、正式源码包、远端exact-head CI、升级/恢复、外部SMTP、历史GateAUDIT、原安装器独立阻断继续沿用原限制；没有把它们变成本轮新增工作或解除其门禁。CVE-2026-91403及构建链完整安全闭包之外的原P2条件风险未重新评估。

## 4. 验证与最终结论

复用原报告已通过的682前端单测、JDBC回归、定向后端/Python回归、双发行启动/身份/数据库证据；本轮没有重复执行。基础镜像和证书配置未改，无需重建/启动复测。新增执行仅为证书链实验、精确身份/来源比对和文档/候选保护检查。未达到用户要求的完整受影响链阻断资格，Regression整体保留BLOCKED，不能用历史功能回归PASS覆盖安全验收缺口。

```text
Task: NQ-V101-FULL-STACK-SECURITY-PATCH-CLOSEOUT
CVE-2026-91403: BLOCKED

Temurin Runtime:
Version: Eclipse Adoptium Temurin-21.0.12.1+1 / 21.0.12.1+1-LTS
Image Digest: sha256:fe38dbecbaa6a7246b4a5d6a588f6738770a7e6545eff008e9e59ffb1c845ebe
Public Demo Digest: sha256:06dc551c79bb0ae5ee896967715cde6dafc12a396b04f060ca5e7b9df7533efc
Certificate Validation: 正常链ACCEPTED；16个受影响中间证书目标REJECTED；完整受影响链及替代路径阻断NOT_VERIFIED

Temurin Build JDK:
Version: Eclipse Adoptium Temurin-21.0.12.1+1 / 21.0.12.1+1-LTS
Image Digest: sha256:55ba61f691e4f75db16e33cc3864f2c4aea19b4e776b80194505f00f1fd636e6
Certificate Validation: 正常链ACCEPTED；16个受影响中间证书目标REJECTED；完整受影响链及替代路径阻断NOT_VERIFIED

Build Chain:
Verified: 候选保护、370份证据完整性、实际工具/镜像身份、3185构建输入到本地制品关联、SBOM覆盖边界、CycloneDX下载字节匹配
Unverified: active plugin漏洞调用闭包、宿主/CPython-CI补丁资格、OS条件路径完整适用性、gitleaks本轮下载验证、全工具传递闭包及正式远端构建来源
Regression: BLOCKED
Unresolved P0: 未证明当前NQ对外可达P0；不等于不存在
Unresolved P1: Temurin CVE修复/完整缓解资格；原构建链安全覆盖缺口未全部关闭
Decision: BLOCKED
Public Deployment: NOT_READY
LIVE: DISABLED
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Release: NOT_CREATED
```

证据均为LOCAL_ONLY。本轮到此停止，不启动第二轮安全审计。
