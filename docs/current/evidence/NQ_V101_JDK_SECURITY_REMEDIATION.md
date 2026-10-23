# NQ-V101-JDK-SECURITY-REMEDIATION

日期：2026-10-23。范围：CVE-2026-91403 定向缓解、实际 Java 构建/运行环境验证，以及上一轮明确列出的构建链证据缺口。未启动全量扫描或新的独立审查阶段。

**Decision: READY_TO_COMMIT（本地定向安全补丁候选）**。CVE-2026-91403 为 **VERIFIED_MITIGATION**，不是 Temurin 二进制升级或供应商永久修复声明。原安全补丁保留；Git 交付待执行，公网 Demo 仍 NOT_READY，LIVE 保持 DISABLED。

## 1. 候选与证据身份

| 项目 | 实际值 |
| --- | --- |
| Branch | `codex/v101-full-stack-security-patch` |
| Starting / current HEAD | `1b467fdd20ba799eab99675343aa6eaeb58c956d` |
| Starting staged tree | `7c7e17fd8eacf4e1d05b25e26d8d4c4fa9fc7169` |
| Starting staged files | 9；开始时无 tracked unstaged 修改 |
| Starting untracked | `docs/current/evidence/NQ_V101_FULL_STACK_SECURITY_PATCH_CLOSEOUT.md`，原文保留 |
| Final staged files | 15；原9路径全部保留，新增6路径 |
| Final Tree | 见下方独立 `final-candidate.json` 和本次最终回复；该树包含本报告，不能在报告正文写入其自身树哈希而形成自引用 |
| 本轮代码/配置变更 | `.gitattributes`、CI、backend Dockerfile、source manifest、security目录3文件；另新增本报告 |
| 未修改 | 业务 Java、Order/Ledger/Risk/Tenant/RLS、策略算法、Flyway；已验收的 npm/Python/JDBC 依赖更新 |

原9文件的起始 Git blob：

| 文件 | Starting blob | 本轮处理 |
| --- | --- | --- |
| `.github/workflows/ci.yml` | `547994d85a30e070e3a6d25edad8f7ba4064fe55` | 保留原补丁，仅增加6处 setup-java 后的证书策略安装 |
| `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/JdbcDriverSecurityPostgresTest.java` | `2e52555f6fbd136447ae5ab9693ba493c19abf91` | blob完全保留 |
| `backend/pom.xml` | `df1bbcdaf76e440a4d28a1e1dc634451f181fb70` | blob完全保留 |
| `docs/current/evidence/NQ_V101_FULL_STACK_SECURITY_PATCH.md` | `d25bc8558071319c7f482756e979d4a37ee6a60d` | blob完全保留；历史BLOCKED结论不就地覆盖 |
| `frontend/package-lock.json` | `190f23ee577bc898c6e294bca1dbd1ba874aa2a9` | blob完全保留 |
| `frontend/package.json` | `d90d3e22264746b57cc56444ac514af0c8fe4176` | blob完全保留 |
| `release/docker/backend.Dockerfile` | `5e1bc45e92fbdd11dc1b54b3e5f4eb911a82640e` | 保留原补丁，仅在实际构建JDK与最终JRE安装同一策略 |
| `release/docker/frontend.Dockerfile` | `ea4675a08b40adc98f1babd12798ee5a9fd8d132` | blob完全保留 |
| `research/py/pyproject.toml` | `50dc03b5b49d7f2e82aa5c6aaaea4a483ba15c0c` | blob完全保留 |

本轮证据持久化目录：

`C:/Users/Lingyu/.codex/state/plugins/codex-security/scans/nexus-quant/artifacts-0f89f173146f17a06a3927438d255cdfc3a28eece64c37647b0471ece2e4034d/artifacts/jdk-remediation/`

- [完整新增证据包](C:/Users/Lingyu/.codex/state/plugins/codex-security/scans/nexus-quant/artifacts-0f89f173146f17a06a3927438d255cdfc3a28eece64c37647b0471ece2e4034d/artifacts/jdk-remediation/jdk-remediation-evidence.zip)：130份本轮输入/源码/日志，ZIP SHA256 `29831f26d6355b84948dbada851fca0b1e930494d4e7c2d7223f7ff22c9a04bc`。
- `new-evidence-manifest.json`：只记录本轮新增证据的hash；未重复检查旧370份证据或3185构建输入。
- `starting-candidate.json`、`implementation-inputs.json`：原暂存blob、新实现文件hash。
- [最终候选身份](C:/Users/Lingyu/.codex/state/plugins/codex-security/scans/nexus-quant/artifacts-0f89f173146f17a06a3927438d255cdfc3a28eece64c37647b0471ece2e4034d/artifacts/jdk-remediation/final-candidate.json)：暂存后独立记录最终树、全部blob/工作文件SHA256、原7个不变blob核对、剩余untracked及diff检查。
- `qualification-results.json`、`runtime-images.json`、`build-artifact-linkage.json`：实际镜像、证书、TLS、启动与JAR身份。
- `build-chain-classification.json`、`build-chain-scope.json`、`os-applicability-sources.json`：逐项分类及有界证据。

证据目录为本机持久化存储，尚非仓库附件或远端CI artifact；后续交付应随候选归档，不声称已上传。下文未给绝对路径的新增日志均位于上述ZIP内，旧证据继续引用仓库 `artifacts/v101-security/` 和 `artifacts/v101-security-closeout/`。

## 2. 修复选择与供应商边界

### A：正式 Temurin 修复版本

本轮刷新 Adoptium release，latest仍为 `jdk-21.0.13+9`（2026-10-22发布）。沿用已取得的精确源码关联：`jdk-21.0.13+9_adopt` → `2ab10ca20c191701c3a9f3153bfc6e93d63714c1`，buildRef `1cf43d7a3e77886bfea7d550a902e151a87d724d`。该源码没有固定OpenJDK修复新增的16张证书；这不能单独排除供应商通过根库更新实施其他修复。

实际查到 [Adoptium #4586](https://github.com/adoptium/temurin-build/issues/4586)、[PR #4587](https://github.com/adoptium/temurin-build/pull/4587) 和 [release状态 #317](https://github.com/adoptium/temurin/issues/317)：供应商以 `NSS_3_131_1_RTM` 更新certdata处理该事件，PR合并SHA `43e8101d02f8df48fc210968a06fea435de319f9`。上述buildRef与该提交分叉，不能将PR合并直接当作特定二进制包含证明。本轮固定JRE tag manifest读取为registry超时，不能写成tag不存在。没有得到足够的“正式包→实际镜像→有效信任策略”资格，因此未用 `21.0.13+9` 字符串替换当前镜像，也不宣称Temurin没有任何其他有效修复。

### B：官方阻断数据与实际 Temurin 验证

采用 [Azul官方缓解文档](https://docs.azul.com/core/emudhra-workaround) 发布的 [Linux/macOS/container原始文件](https://docs.azul.com/assets/2026-10/emudhra-block-unix.certs)，SHA256：

`2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10`

该文件86条指纹，与固定 [OpenJDK21回移提交85974eed](https://github.com/openjdk/jdk21u/commit/85974eed594aa5a9e13a6a20713156ed8d9cd0ed) 生成的阻断文件字节完全一致。关联上游 [JDK-8395142提交22bc7357](https://github.com/openjdk/jdk/commit/22bc7357ce6bc5f1ed2f1e83e0544b2c25f781d4)：16张受影响证书及其编码变体处理。复用上一轮 `blocked-source-comparison.json`，本轮下载文件及实际有效文件hash相同；不是人工追加若干主指纹。

**支持边界必须区分**：Azul的正式缓解说明针对Zulu；本轮未证明Adoptium为这份文件提供Temurin产品支持或维护承诺。NQ接受的是“可信供应商原始数据与固定OpenJDK21修复一致，并在实际Temurin路径完成正负验证”的本地缓解资格，不冒充Temurin供应商发布了修复二进制，也不产生Azul支持权益。后续Temurin正式修复仍应按实际二进制重新资格验证并移除过渡覆盖。

[Oracle21.0.13.0.2说明](https://www.oracle.com/java/technologies/javase/21-0-13-0-2-relnotes.html) 明确关联非公开JDK-8395555和CVE-2026-91403；本轮只读评估，未更换发行商。当前 [ops.java指引](https://ops.java/emudhra) 已改为移除指定根或启用撤销检查；没有将较早缓存中“替换blocked.certs”的文案冒充当前Oracle建议。

实施：

1. 保留现有固定Temurin21/Maven基础镜像；Docker构建阶段在任何Maven下载之前运行 `install-certificate-policy.sh`，最终JRE执行相同安装。
2. 安装器核对官方完整SHA256、Java21、旧策略算法，并要求所有旧阻断项仍在新文件中。未来JDK新增阻断项会使安装失败，避免固定文件回退新保护。
3. 不修改cacerts、默认根集合、java.security、撤销参数或算法限制。以只读可读权限安装；实际运行用户10001能够读取。
4. 六处CI setup-java后安装同一策略；source manifest以现有Dockerfile所在 `INCLUDE_RELEASE_OPERATIONS` 类别纳入三个文件；`.gitattributes` 对官方文件设置 `-text` 保留精确下载字节。

## 3. 实际证书验证与镜像身份

### 证书材料及反例

完整链来自官方事件引用的公开材料：`https://emudhra.we-have-a-problem.com/chains.zip`、`roots.json`、`impacted-intermediates.json`。chains.zip SHA256为 `7b949956955fc501a30f4d98d2f513d34c0bbbe92e29a968194c96e097ce2345`。材料只含公开证书；未取得或使用私钥。正常对照复用已验证的Maven Central公开链，另在实际新镜像发起正常HTTPS握手。

`artifacts/TrustPolicyProof.java` 使用固定OpenJDK转换器生成16张中间证书的48种合法签名/AlgorithmIdentifier编码，先检查issuer签名与有效期，再执行实际Java PKIX验证。覆盖ECDSA的s/n−s和RSA外层AlgorithmIdentifier NULL编码组合。每项日志记录证书SHA256、issuer SHA256及拒绝原因。

直接路径实验只在进程内test KeyStore补入四个真实公开issuer根，使48种输入具有有效信任锚；产品cacerts从未改变。完整链实验分别使用默认JSSE及含这些fixture根的JSSE。受影响14条完整叶证书链含正常签发、AAA交叉签名和两个无CT控制链。

| 测试 | 旧实际JDK/JRE | 新实际Build SH / Build Demo / Runtime SH / Runtime Demo |
| --- | --- | --- |
| 48种合法编码，签名有效且存在issuer锚 | 48 ACCEPTED | 各48 REJECTED_BY_EFFECTIVE_SECURITY_POLICY；原因 `Untrusted certificate` |
| 14条完整链，补足真实fixture根 | 14 ACCEPTED | 各14因安全策略拒绝 |
| 14条完整链，未改默认根 | 7 ACCEPTED；7缺信任路径 | 各7因安全策略拒绝；另7仍缺信任路径，**不把后者计作策略证明** |
| 正常链，默认JSSE | ACCEPTED | 全部ACCEPTED |
| 正常Maven Central HTTPS，默认hostname校验 | 本轮不重复旧握手 | 四镜像HTTP200、TLS_AES_256_GCM_SHA384、peer CN=repo.maven.apache.org |

旧收尾报告的“16目标因MISSING_TRUST_PATH拒绝、修复NOT_VERIFIED”完整保留。新证据增加可达到信任锚的真实反例，未改写历史失败原因。旧/挂载缓解对照和新镜像内生策略验证分别留存。新镜像证书测试为 `--network none --read-only`，只挂载测试类/公开证书，**没有挂载替换blocked.certs或cacerts**。

此结论针对标准Java PKIX/JSSE及已列证书/编码集合；不宣称证明所有可能的编码、任意自定义TrustManager或在线OCSP/CRL行为。恶意链验证不依赖DNS、站点可用性或网络失败。

### 精确镜像

| 角色 | 更新前实际镜像 | 更新后实际镜像Digest/Id（linux/amd64） |
| --- | --- | --- |
| Self-Hosted JRE | `sha256:fe38dbecbaa6a7246b4a5d6a588f6738770a7e6545eff008e9e59ffb1c845ebe` | `sha256:ae0f40d80fda5222e3b7c169003cb979382b2451f72900c4f3840948b46c4616` |
| Public Demo JRE | `sha256:06dc551c79bb0ae5ee896967715cde6dafc12a396b04f060ca5e7b9df7533efc` | `sha256:d31d76f8b01cedc703ff9fe3d5995911873710407fac229b8cd5e84ac732dae0` |
| Self-Hosted Maven JDK | `sha256:55ba61f691e4f75db16e33cc3864f2c4aea19b4e776b80194505f00f1fd636e6` | `sha256:5ee8e38c99371a2e430de48f0a580792602f4ef37053977ca310eacde1de3ea4` |
| Demo Maven JDK | 原收尾只固定上述共同构建JDK | `sha256:d2c1b6acb5910761c684a43f7961dfbd9905d63921a565d91be1bb15461dec92` |

RepoDigests、config和rootfs身份保存于qualification-results.json；这些是本机buildx加载的不可变身份，**不是远端push证明**。

保持的基础镜像：

- `maven:3.9.16-eclipse-temurin-21@sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320`
- `eclipse-temurin:21-jre-jammy@sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0`

四镜像实际Java均为Eclipse Adoptium **Temurin-21.0.12.1+1 / 21.0.12.1+1-LTS**；Maven为3.9.16，commit `2bdd9fddda4b155ebf8000e807eb73fd829a51d5`。未升级Java主版本、Spring Boot或任何额外依赖。

| 有效材料 | 四镜像结果 |
| --- | --- |
| JAVA_HOME / java.home | `/opt/java/openjdk` |
| TrustStore | `/opt/java/openjdk/lib/security/cacerts`，118默认锚 |
| cacerts SHA256 | `98d34a90fca2688ef5674862d2a484e09e5ca25d2f07c5d654aa28f1581f3419`，保持原值 |
| blocked.certs SHA256 | `2596bb6dda83c66d96c5a252d7b2ae1f24cc1ae3f1890223b4f3591a8d52bf10` |
| java.security SHA256 | `36f2d2e06140970e17ec7f4599d9e34b21113ec0de03fc92f64cc0dc28d8bf41` |
| jssecacerts | 不存在 |
| javax.net.ssl.trustStore / java.security.properties | 未设置；默认信任实现 |
| build java二进制SHA256 | `2a207f5e7d075afa01d97f8048389a64432a44c4a5af0f5e77d6e286ec5f401d` |
| runtime java二进制SHA256 | `999c141a6e620c71aa4230ccb62341a89162f37818753ff006d766bf935dcbe1` |

复用原收尾对仓库TrustStore/SSLContext消费者的定向核对。本轮只增加默认策略数据，没有新增覆盖配置。实际NQ仍以 `java -jar` 使用镜像内同一Java home；部署者未来自行覆盖TrustStore/安全Provider需要重新验证。

构建JAR与最终镜像 `/opt/nexusquant/app.jar` 字节对应：SH `bd48017b888b976e712944e5fe0c2a787053d303697779524a936962c1d4cd23`；Demo `1336c2b7c1b9b7baaeb0508463c83d0607a1b07e1f8306d2e92e6a94ed6916d6`。前端镜像保持原身份，未重新构建或运行682项单测。

## 4. 仅处理原构建链缺口

完整逐项 `Required Proof / Existing Evidence / Missing Evidence / Blocking Reason` 保存在build-chain-classification.json。以下分类限定实际本地发行构建，不清除旧漏洞ledger，也不将未知解释为全局安全。

| Evidence ID | 组件与本轮证据 | 分类及仍缺内容 | 本候选是否阻塞 |
| --- | --- | --- | --- |
| BC-01 / J08 | 复用实际7插件descriptor和原24匹配，补实际goal日志及直接class引用检查 | UNVERIFIED_NON_BLOCKING；active compiler/install的完整反射/传递调用闭包未证明，具体边界见下文 | 无已证实可利用的当前发行完整性攻击路径；不以完整递归闭包缺失自动定P1 |
| BC-02 / N01 | 复用Node24.22.1/npm11.19.1实际Docker builder与链接证明 | 对宿主旧Node影响发行编译的命题为CONFIRMED_NOT_AFFECTED；host24.16.0维护保留 | 正式frontend构建不调用host Node |
| BC-03 / P02 | formal source工具读取Git blob/本地JSON；research CI不生成Java运行镜像 | BUILD_ENVIRONMENT_DEPENDENCY；host3.14.2及未来CI3.11 patch资格仍UNVERIFIED | 未发现所列SSL/远端下载/归档解包触发consumer；不声称解释器已修补 |
| BC-04 / O03 | [Debian官方tracker](https://security-tracker.debian.org/tracker/CVE-2023-45853)明确该版本binary未提供contrib/minizip；复用包文件/Node链接证据 | CONFIRMED_NOT_AFFECTED，仅限zlib1g `1:1.2.13.dfsg-1`该问题 | 关闭适用性缺口；source包告警仍可存在 |
| BC-05 / O04 | [Ubuntu官方说明](https://ubuntu.com/security/CVE-2026-84782)：DTLS retransmission/WANT_WRITE前提；实际Maven为JSSE，apt/curl为TCP | UNVERIFIED_NON_BLOCKING；openssl/libssl3t64 `.15`仍是受影响包，`.16`为修复；全builder DTLS闭包未证明 | 保留2个High包匹配；所检查发行路径没有DTLS消费者，不升级无关OS消除计数 |
| BC-06 | PowerShell/Bash/.NET只读命令和实际Docker编排路径 | BUILD_ENVIRONMENT_DEPENDENCY；全host补丁资格未验证 | 无本任务已确认的可利用宿主缺陷；受控构建环境仍是交付前提 |
| BC-07 | SBOM覆盖、jarmode-tools补录及来源证明直接复用 | 旧覆盖缺口已关闭；完整工具闭包归BC-08 | 不重生成SBOM、不重复370/3185证明 |
| BC-08a | 取得Gitleaks8.18.4官方release及checksum文件，linux_x64摘要与固定lock完全一致 | BUILD_ENVIRONMENT_DEPENDENCY；实际归档下载65秒超时，归档字节NOT_VERIFIED | Gitleaks是CI检查器，不生成运行制品；既有CI在下载/hash失败时停止，未绕过其后续交付门禁 |
| BC-08b | 固定Actions/tool身份、CycloneDX字节、npm完整性及签名证明直接复用 | UNVERIFIED_NON_BLOCKING；全工具递归安全闭包未证明 | 缺少无限递归证明不自动等于P1；未新增已知可利用路径 |
| BC-09 | 本轮实现hash→实际build日志/镜像→相同最终JAR字节；原3185输入证据复用 | MITIGATED，LOCAL_ONLY；commit/exact-head CI/签名发行包未执行 | 本地身份已关联；远端交付是下一步，不能冒称已完成 |
| BC-10 | 固定FROM、实际四镜像digest、官方文件固定SHA256 | UNVERIFIED_NON_BLOCKING；apt层全部字节可复现性、arm64资格仍未证明 | 已测试amd64候选；不声称整个构建可逐字节复现 |

BC-01的有界判定：

- 实际release命令为 `install -DskipTests`、`package spring-boot:repackage`，执行resources/compile/testCompile/jar/install/build-info/repackage；不执行clean/site/deploy/shade/build-image。跳过测试的surefire goal不是实际业务测试证明。
- 原Commons IO2.6（NQ-DEP-0118/0119）、Snappy0.4（0122）、Commons Lang3.12（0159）不在所收集实际descriptor中，原owner为clean/site等；该事实只关闭对应已知未执行goal路径，不声称host/CI全局不受影响。
- Plexus4.0.1安装插件（0120）、Plexus4.0.0与Commons IO2.11编译插件（0160/0158）仍保留UNVERIFIED。所检查插件自身class常量没有Expand引用；XmlStreamReader只出现于未执行的shade transformer。直接引用检查不是完整传递/反射证明，但未发现上述恶意解包/XML入口在本次构建被使用。
- Lang3.14/Plexus3.5.1映射到缓存shade3.6.0（0121/0157），不是本次运行goal。Boot realm的Jackson2.21.4、Lang3.16、HttpClient5/HttpCore5原匹配0161–0175继续保留；缓存JAR存在不证明build-image/HTTP/JSON易受攻击输入路径执行。实际目标是受控源码的build-info/repackage，无运行时租户输入。没有把应用BOM当作插件补丁。
- 接受UNVERIFIED_NON_BLOCKING的依据是实际goal、输入信任边界和已检查的调用前提，而非CVSS降级或测试通过就代表漏洞不存在。若后续启用相应goal、接收不可信构建元数据/归档或发现具体可达sink，应重新设为阻塞并修复。

**BUILD_CHAIN_RELEASE_BLOCKERS: 0（限定已列缺口与本地候选）**。Gitleaks下载、未来CI实际解释器、host维护和全部递归闭包仍明确未验证；后续exact-head CI、源码包、发布/升级/恢复资格不因本结论免除。

## 5. 定向验证

| 项目 | 结果与证据 |
| --- | --- |
| 实际JDK/JRE版本、策略来源及hash | PASS；4组 `*-final-materials.log`；官方数据与OpenJDK源码关联；非Temurin永久补丁声明 |
| 证书行为 | PASS；4组 `*-final-certificates.log`；每组48编码+14完整fixture链、默认JSSE正控制；旧/新对照日志同时保留 |
| 正常HTTPS | PASS；`build-tls-retry.log`、`self-tls-retry.log`、`demo-final-tls.log`、`build-demo-final-tls.log` |
| 安装器失败边界 | PASS；篡改文件、未来未知旧阻断项、Java25均拒绝，目标内容保持不变；`installer-negative.log` |
| SH后端定向测试 | PASS，156 tests，0 failure/error/skip；JWT、Security Context、Auth WebMVC、51生产配置与67secret-profile用例、2个AuthSession PG、Okx/Binance/loopback HTTP客户端、发行身份/health隐私；`targeted-self.log` |
| Demo定向测试 | PASS，7个不同用例；3配置+3发行身份/health+1正式Canonical SIM流程。初次遗漏Mailpit导致1项失败，补齐既有本地邮件夹具后只重跑该项；`targeted-demo.log`、`targeted-demo-retry.log` |
| 实际Docker双发行 | PASS；两份后端构建、维护入口、受限nq_runtime启动、Health UP、backend/frontend身份一致；`build-self.log`、`build-demo.log`、`runtime-images.json` |
| LIVE与真实交易配置 | PASS；隔离internal网络且无published ports；NO_OUTBOUND=true，LIVE/REAL_EXCHANGE/REAL_PROVIDER/REAL_CLIENT=false；生产fail-closed定向测试通过 |
| CI兼容性 | PASS（本地合同/YAML）；Test-CanonicalDeliveryWorkflow -ContractOnly、Test-NqWorkflowYaml，8类无效YAML拒绝；`ci-contract.log`、`ci-yaml.log`。远端CI NOT_RUN，REMOTE_ENFORCEMENT NOT_APPLIED |
| Source manifest | PASS（只检查新增3文件的唯一归类、真实存在及既有path/forbidden约束）；`source-manifest-targeted.json`。没有伪造未commit的正式源码包 |
| Git范围/空白检查 | 见独立final-candidate.json；原9路径保留、其中7个blob完全不变、两处必要增量；本报告与旧untracked报告分别列示 |

所有新测试数据库、SMTP、容器和网络为任务独占；只清理本任务创建的资源，未停止/替换现有Demo或其他工作区服务。PG使用已有固定16.15 digest；本地Mailpit使用 `sha256:b1f1be18af530d939a11ee8820b379e0c88eeec204d904bfad68862adced3a5a`，只验证合成邮件。

保留执行失误：首次证据挂载路径错误产生ClassNotFoundException；正常TLS脚本在关闭stream后读取cipher产生异常；Demo首次漏Mailpit。均修正本轮测试执行代码/夹具并留存失败及通过证据，未修改业务实现、弱化assertion或将失败记作PASS。

未重跑682前端单测、JDBC42.7.14短写缺陷证明、全部Java/Python测试、全部多租户回归、历史GateAUDIT、旧SBOM/CycloneDX/370/3185核对。原已通过证明继续只在相同范围内复用。

## 6. 风险、回滚与停止点

- P0：本轮未确认当前NQ对外可达P0；不是全局无漏洞声明。
- P1：本轮指定剩余安全资格阻断为0；CVE缓解有实际反例/策略证明，构建链按实际consumer分类。供应商产品支持承诺、远端交付和正式发布资格不由本地验证代替。
- P2：保留原J05 pgJDBC证书无限流、O02数据库OS条件风险、O05 Nginx musl/zlib、host Node/Python维护、localStorage及Compose观察；本轮不重扫、不虚构修复或利用链。BC-01/05/08b/10的UNVERIFIED边界继续保留。
- 回滚基线为起始暂存树 `7c7e17fd8eacf4e1d05b25e26d8d4c4fa9fc7169`；没有执行Git重置。若后续证书资格失效，应仅撤回本轮明确的证书策略/CI/Docker/manifest/属性增量并保留原9文件和全部证据；旧JRE会恢复已证明可接受受影响链的行为，不能以回滚当作安全发布方案。
- 未来更换Java、默认信任实现或覆盖文件须重验证。安装器对未来新增阻断项fail-closed，不能为了通过构建删除新保护。正式Temurin修复取得精确资格后，应撤去过渡覆盖。

```text
Task: NQ-V101-JDK-SECURITY-REMEDIATION
Baseline:
Branch: codex/v101-full-stack-security-patch
HEAD: 1b467fdd20ba799eab99675343aa6eaeb58c956d
Starting Tree: 7c7e17fd8eacf4e1d05b25e26d8d4c4fa9fc7169
Final Tree: recorded in final-candidate.json after staging this report

Original Security Patch:
Files Preserved: 9 / 9
Blob Identity: 7 unchanged; CI and backend Dockerfile retain original patch plus necessary policy installation

CVE-2026-91403: VERIFIED_MITIGATION
Vendor: Eclipse Adoptium runtime; official Azul policy data matching fixed OpenJDK21 source
Version: Temurin-21.0.12.1+1 / 21.0.12.1+1-LTS
Patch Reference: JDK-8395142 / 85974eed594aa5a9e13a6a20713156ed8d9cd0ed; Oracle JDK-8395555 public release-note association
Remediation: pinned upstream certificate block data in actual build JDK and final JRE
Validation: effective PKIX/JSSE rejection with valid trust anchors; normal chain and HTTPS accepted

Java Runtime: QUALIFIED (local mitigation scope)
Digest SH: sha256:ae0f40d80fda5222e3b7c169003cb979382b2451f72900c4f3840948b46c4616
Digest Demo: sha256:d31d76f8b01cedc703ff9fe3d5995911873710407fac229b8cd5e84ac732dae0
Certificate Policy: VERIFIED
Java Build: QUALIFIED (local mitigation scope)
Digest SH: sha256:5ee8e38c99371a2e430de48f0a580792602f4ef37053977ca310eacde1de3ea4
Digest Demo: sha256:d2c1b6acb5910761c684a43f7961dfbd9905d63921a565d91be1bb15461dec92
Certificate Policy: VERIFIED

Build Chain:
Blocking Findings: 0 within named local closeout scope
Non-blocking Findings: existing conditional package risks and scoped unknowns retained
Unverified: recursive plugin/tool closure; host/CPython patch qualification; Gitleaks archive bytes; exact-head CI; signed release package; full reproducibility; arm64

Changes:
Files Changed: 8 this round; 15 staged total including preserved original patch
Dependencies: no additional version changes
Docker Images: both backend distributions and actual build stages rebuilt

Verification:
Certificate Tests: PASS
Docker: SELF_HOSTED PASS / PUBLIC_DEMO PASS
Backend: TARGETED_REGRESSION PASS (156 SH + 7 Demo distinct tests)
CI Compatibility: LOCAL_CONTRACT PASS; REMOTE NOT_RUN

Findings:
P0: none demonstrated in this scope
P1: 0 unresolved within targeted security qualification
P2: retained with explicit conditions; not globally closed

PATCH_IMPLEMENTATION: COMPLETED
GIT_DELIVERY: PENDING
SECURITY_RELEASE: LOCAL_TARGETED_QUALIFIED; FORMAL_RELEASE_NOT_PERFORMED
Decision: READY_TO_COMMIT
Public Demo: NOT_READY
LIVE: DISABLED
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Release: NOT_CREATED
Next: SECURITY_PATCH_GIT_DELIVERY
```

完成本轮限定修复后停止，不启动新一轮全量审计或独立审查。
