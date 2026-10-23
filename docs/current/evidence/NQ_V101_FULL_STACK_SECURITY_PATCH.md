# NQ-V101-FULL-STACK-SECURITY-PATCH

Task: `NQ-V101-FULL-STACK-SECURITY-PATCH`

执行日期：2026-10-23（Asia/Shanghai）

Decision: **BLOCKED / Java 供应商补丁证据与构建链覆盖缺口尚未关闭**

Commit: `NOT_COMMITTED`

Push: `NOT_PUSHED`

Release: `NOT_CREATED`

本轮已完成全栈依赖清点、定向补丁、相关回归和本地双发行镜像验证。BLOCKED 是安全发布候选结论：不能把 npm/pip 零发现或应用镜像 OS 层没有 High/Critical，提升为全栈安全通过。没有修改业务实现、历史 Flyway、交易状态机、Ledger、Tenant、研究算法或真实资金事实，没有连接真实交易账户或启用 LIVE。

## 1. 基线与候选身份

| 项目 | 实际结果 |
| --- | --- |
| Baseline branch | 干净的 `dev` |
| Baseline HEAD | `1b467fdd20ba799eab99675343aa6eaeb58c956d` |
| cached origin/dev | 同一 SHA |
| 远端确认 | 两次 `git fetch origin` 因 GitHub TCP 连接失败；随后认证 GitHub branch API 两次返回同一 dev SHA。没有把 fetch 失败写成成功 |
| Task branch | `codex/v101-full-stack-security-patch` |
| 当前 HEAD | 仍为基线；补丁仅在工作区及精确暂存区 |
| 最终镜像构建 ID | `4484ff4b58554ba3ad8b9b193fd7dfb3` |
| 最终工作区 sourceSha256 | `2d9da644900e7ee98138dc74da9329ff03c6edf46d28f37bcd2943608694e6c2` |
| 来源属性 | `sourceDirty=true`；只证明本地工作区构建，未形成不可变提交、正式源码包、远程 CI 或发布资格 |

第一次镜像构建 `09fcf9b63d8c4ed08a63cffb97a4dbbc` 使用更新前的 Maven builder。后续固定 builder 后重新完整构建；最终证据只使用上表的第二次构建身份。

## 2. 证据位置、扫描口径与计数

原始证据保留在仓库忽略目录 `artifacts/v101-security/`，不加入产品源码或发布包。当前文档是版本控制内的修复记录；以下原始日志、SBOM 和 ledger 是 **LOCAL_ONLY**，尚未上传、发布或纳入不可变证据存储。

- [逐项 findings ledger](../../../artifacts/v101-security/findings-ledger.csv)：2,317 条扫描/供应商快照记录，包含用户要求的 Finding ID、Component、Package、Installed Version、CVE/GHSA、Severity、Affected Range、Fixed Version、Source、Runtime Scope、Reachability、Exploit Preconditions、Recommended Fix、Regression Scope、Status。范围中未知字段保留 `UNVERIFIED`。
- [扫描汇总](../../../artifacts/v101-security/findings-summary.json)、[原始证据 SHA-256 清单](../../../artifacts/v101-security/evidence-sha256.json)。ledger 包含同一 CVE 的不同组件、镜像、前后快照，**不能将行数当作独立漏洞数**。
- [最终发行镜像身份](../../../artifacts/v101-security/distributions-final-amd64/distribution-images.json)、[镜像运行结果](../../../artifacts/v101-security/runtime-images.json)、[镜像内实际 JAR 清单](../../../artifacts/v101-security/final-image-java-libraries.json)。
- [后端最终 CycloneDX SBOM](../../../artifacts/v101-security/backend-after.cdx.json)、[前端运行依赖 SBOM](../../../artifacts/v101-security/frontend-after.cdx.json)。Maven 实际树和插件解析清单还保留于各模块 `target/security-dependencies.json`、`target/security-build-plugins.txt`，另复制到证据目录。

| 数据源 | 修复前 | 修复后 / 局限 |
| --- | --- | --- |
| Maven OSV 运行/测试实际树 | 104 个坐标，68 个组件-公告匹配 | 合并插件扫描后最终查询 298 个坐标、52 个公告、60 个匹配；其中 runtime 2、build 24、inactive 34。不能称为 Maven 零漏洞 |
| Maven 含插件基线 | 297 个坐标、107 个公告、115 个匹配 | 最终 OSV 8 个详情请求超时，均以 GitHub 公告 API 补齐；原始失败保留。另从 pgJDBC 供应商发现 OSV 未返回的新 Low 公告 |
| npm audit 全量/production | 全量 14 个受影响包：High 9、Moderate 4、Low 1；含传播计数 | 两种 audit 均零发现，原始退出码 0；原始基线退出码 1 保留 |
| pip-audit | pip 25.3 的 10 条别名记录，5 个独立 CVE | 隔离环境 pip 26.2.2 后无已知依赖漏洞；本地项目不在 PyPI，扫描器明确 skip，项目源码另作限定检查；不覆盖 CPython 本体 |
| npm 供应链 | 真实 lock | 206 个 registry signatures、56 个 attestations 验证成功 |
| Dependabot | API 403，alerts disabled / scope 不足 | `UNVERIFIED`；未更改权限。OSV/npm/pip/Trivy 是等价补充数据源，不代表读取过 Dependabot 全部告警 |

Findings 的原始计数：Critical 23、High 345、Medium/Moderate 1,203、Low 714、未知严重度 32。它们是上述多快照 ledger 的匹配行，包含已修复、不可达、重复镜像及历史基线；**不是当前 NQ 可利用漏洞数量**。当前没有证明满足 P0 定义的对外可利用链；这也不证明不存在 P0。`UNVERIFIED` 819 行、`NO_FIX_AVAILABLE` 1,257 行、`NOT_AFFECTED` 95 行、`FIXED` 146 行。OS 行的 `NO_FIX_AVAILABLE` 仅指当次发行版数据库未列 fixed version，不是跨供应商的永久无修复结论。

## 3. Scanned：实际组件与修复

| 栈 | 修复前 → 最终实际解析 | 作用域与处理 |
| --- | --- | --- |
| Java | 本机 Oracle JDK 21.0.9；原 Maven 镜像 Temurin 21.0.10+7 → 新 builder/runtime 均 Temurin 21.0.12.1+1 | 本机测试工具与发行 JRE 分开；未升级 Java 主版本，未改全局 JDK |
| Spring Boot | 3.5.10 → 3.5.16 | PRODUCTION_RUNTIME，保留 3.5.x |
| Spring Framework / Security | 6.2.15 / 6.5.7 → 6.2.19 / 6.5.11 | Boot 管理版本；剩余两个 WebMVC 公告见下节 |
| Tomcat embed | 10.1.50 → 10.1.61 | core/el/websocket 同版本显式管理 |
| Jackson | 2.19.4 → 2.21.7 | import Jackson BOM 在 Boot BOM 前，避免仅改同名属性但实际无效 |
| Log4j API / Logback | 2.24.3 / 1.5.25 → 2.25.5 / 1.5.34 | Log4j BOM 显式覆盖，Logback 随 Boot |
| JDBC | 42.7.9 → 42.7.14 | 显式 dependencyManagement；避免 Boot BOM 带入 42.7.11 |
| Flyway / JWT / HikariCP | 11.7.2 / JJWT 0.12.6 / HikariCP 6.3.3 | 实际树覆盖，未因扫描机械升级；未修改 migration |
| Maven | 本机 3.9.12；发行 builder 3.9.12 → 3.9.16 | BUILD_TIME；插件 realm 与应用 BOM 分开检查 |
| Node / npm | 本机 Node 24.16.0、npm 11.13.0；builder Node 24.22.1 | BUILD_TIME；正式前端运行阶段只有 Nginx 静态文件，不运行 Node/RSC |
| Axios / React Router | 1.13.6 / 7.13.2 → 1.20.0 / 7.18.4 | 真实 package-lock，定向更新 |
| Vite / Vitest | 8.0.3 / 5.0.1 → 8.3.5 / 5.0.5 | BUILD_TIME / DEVELOPMENT_ONLY，同主版本 |
| 未改变的前端组件 | React/DOM 19.2.4、AntD 5.29.3、Query 5.95.2、Zustand 5.0.12、ECharts 6.1.0、Lightweight Charts 5.2.0、i18next 26.4.2、Day.js 1.11.20、Playwright 1.58.2、TypeScript 5.9.3 | 实际 lock/audit 覆盖，没有盲目全量升级 |
| Python | 本机隔离 venv CPython 3.14.2；CI 配置 3.11 | 研究 runtime dependencies 为空；第三方均 dev/build；全局解释器未修改，CI 3.11 未在本机重跑 |
| Python 工具 | pip 25.3 → 26.2.2；setuptools 实际 84.2.1；pytest 9.1.1、mypy 2.4.0、ruff 0.15.22 | CI install 前增加 pip>=26.2；build-system setuptools 下限 68 → 78.1.1；后者是避免允许已知受影响版本的约束加固，不能声称基线实际安装了旧 setuptools |
| PostgreSQL Server | 16.15，Debian 16.15-1.pgdg13+2 | 独立容器、CI 既有固定 digest 均实测同版本；与 JDBC 公告分开 |
| Docker / Compose / buildx | Client/Engine 29.9.2；Compose 5.6.1；buildx 0.38.1 | 本机工具。Moby/CLI 官方公告核对；不等于 Docker Desktop VM/宿主 OS 全部软件审计 |
| PowerShell / Shell | PowerShell 7.6.7；Bash 5.2.21 | 脚本合同测试与来源检查；PowerShell GitHub 三条旧公告不匹配。完整 MSRC/.NET、Git Bash 打包补丁级证据仍 UNVERIFIED |

所有 npm 操作使用显式官方 registry；没有 `npm audit fix --force`。没有主版本框架迁移。生产业务代码变化：**无**。新增 Java 文件仅为驱动回归测试。

## 4. 重点漏洞适用性与剩余项

以下表格为决策摘要；完整逐项原始范围、修复版本与来源在 ledger。FIXED 指已移除受影响依赖版本或通过定向反例验证，并不声称每个基线公告在 NQ 曾可利用。

| Finding ID | Component / CVE-GHSA | Severity / Scope | 适用性、前提与状态 | 修复及回归 |
| --- | --- | --- | --- | --- |
| NQ-SEC-J01 | JSSE / CVE-2026-91403 | Critical；runtime/build | **UNVERIFIED，发布阻塞**。Oracle CNA/官方告警明确列 Oracle 21.0.13 等为受影响，涉及 eMudhra 中间 CA 信任。不能把它直接映射到 Temurin 21.0.12.1，也不能把 Temurin 21.0.13 标签当作修复证明 | Adoptium 最新 release 标签21.0.13+9存在；固定版本JRE Docker tag拉取不存在，官方容器Dockerfile仍21.0.12.1。缺 Temurin 对应供应商声明、可用修复镜像及拒绝受影响链/正常TLS控制。没有自制 truststore 补丁 |
| NQ-SEC-J02 | Boot Mail / CVE-2026-40992、GHSA-9wxp-w4px-32vh | Moderate；runtime | 3.5.0–3.5.14受影响，3.5.15修复；依赖版本 **FIXED**。NQ 手动创建 JavaMailSenderImpl，mail默认关闭，guard只允许loopback SMTP，未证明外部TLS利用链 | Boot3.5.16；Demo配置、HTTP注册/邮件、双用户链回归。未进行外部SMTP hostname TLS实验。Spring直连复核403，使用已取得OSV/GHSA详情与供应商链接，不冒充直连成功 |
| NQ-SEC-J03 | pgJDBC / CVE-2026-107314、GHSA-rhp9-mr79-r74h | Medium；runtime | 仅42.7.11–13受影响；原42.7.9 **NOT_AFFECTED**。全部排除/空方法的requireAuth配置会被当作未设置，且需攻击者控制认证对端 | 显式42.7.14避免随Boot引入回归；拒绝全部方法返回08004。该测试是当前fail-closed smoke，不是42.7.9旧漏洞复现 |
| NQ-SEC-J04 | pgJDBC / CVE-2026-107315、GHSA-f64h-wr5q-3qf3 | Medium（供应商）；runtime | 42.7.4–13短写补齐泄漏旧缓冲；42.7.14 **FIXED**。应用未找到ByteStreamWriter/Copy/LargeObject短写consumer；隔离真实PG证明依赖缺陷 | 实际写32768、声明65536：旧42.7.9在index32768预期0实际80；新42.7.14持久化结果为原值+零。初始8192/32768小输入未复现，日志保留；不能将它写成失败 |
| NQ-SEC-J05 | pgJDBC / GHSA-hcjv-f5jh-r55v | Low；runtime，P2 | 新供应商公告：<=42.7.14读取sslrootcert/sslcert无限设备可能CPU/线程DoS，**NO_FIX_AVAILABLE**。需控制本机证书路径；当前NQ只有受信任部署配置，未发现租户可控入口 | 保留regular trusted cert约束建议；未执行/dev/zero PoC，未将loginTimeout宣称为普遍充分缓解。OSV零/少匹配不能覆盖供应商新公告 |
| NQ-SEC-J06 | Spring WebMVC / CVE-2026-47890、GHSA-j9f9-w8pj-32f8 | scanner Critical；runtime | 当前NQ **NOT_AFFECTED**：公告需SSE fragments rendering；未发现FragmentsRendering/SseEmitter消费路径 | 保留Spring6.2.19，不跳Spring7；后续启用此API须重新判断 |
| NQ-SEC-J07 | Spring WebMVC / CVE-2026-47884、GHSA-pc63-qcmh-9cmg | scanner Critical；runtime | 当前NQ **NOT_AFFECTED**：需XsltView加通配映射/隐式view名；无XsltView、ViewResolver、该view映射consumer | 源码/配置检查；未声称供应商版本本身已修复 |
| NQ-SEC-J08 | Maven active plugin realm：Plexus、Commons、Snappy、Jackson2.21.4、HttpClient5/HttpCore5 | 多级；BUILD_TIME，P2/UNVERIFIED | 应用BOM不会改插件自身classpath。最终24个build匹配；触发通常需要恶意归档、元数据、特定JSON/HTTP路径，未证明NQ执行命中具体函数 | 不仅按CVSS机械升级；可信构建链的逐调用方可达性/安全版本闭包仍不足，作为候选阻塞的覆盖缺口。34个site/deploy-only匹配 **INACTIVE / NOT_AFFECTED**，不当成生产Web服务 |
| NQ-SEC-F01 | Axios / React Router / Vite / Vitest及传递依赖 | 多级；runtime/build/dev | 真实lock命中，定向升级后 **FIXED**；React/Vite没有RSC服务器，不套用不存在的RSC利用路径 | 详见逐项npm ledger、682单测、双生产构建、Edge登录smoke |
| NQ-SEC-N01 | Node24 / CVE-2026-105482、105483、106069、111497等10月安全公告 | High等；BUILD_TIME | 正式builder **FIXED** 为24.22.1；DNS/TLS/Brotli类前提取决于consumer。宿主24.16.0仍旧，未自动更新全局工具 | 正式双发行Docker构建使用新Node；本机工具仍为P2/环境维护项，不声称全机修复 |
| NQ-SEC-P01 | pip / CVE-2026-1703、3219、6357、8643、13346 | 扫描未给统一severity；BUILD_TIME | 隔离环境 **FIXED**。恶意包/索引/解包路径属于安装供应链，不属于Java交易运行时 | pip26.2.2 + CI>=26.2；pip-audit/pytest/mypy/ruff |
| NQ-SEC-P02 | CPython3.14.2 / 3.14.8公告中的SSL、zipfile、tarfile、urllib/StringPrep问题 | UNVERIFIED；research/dev | 不能以pip-audit代替解释器审计。3.14.8已被3.14.9取代；公告包括19445、19553、15310、19672、15806、17084，逐条引入版本不由release-note反推。CVE-2026-82049官方明确3.14不受影响 | 研究src实际为本地CSV/JSON，没有pickle/eval/exec/subprocess入口；未证明当前研究路径可利用。宿主解释器修复与CI3.11实际patch身份仍未验证 |
| NQ-SEC-O01 | Nginx1.28 →1.30.6 | Medium/High等；runtime | **FIXED** 版本覆盖官方最新alias/rewrite、script、HTTP3、TLS tickets、AIO等公告。当前nginx.conf无HTTP3/TLS/alias/rewrite/AIO配置，不能说所有公告均曾可达 | 明确tag+digest；前端实际入口和发行身份启动成功 |
| NQ-SEC-O02 | PostgreSQL16.15及OS libxml2 / CVE-2026-6653 | scanner Critical；runtime | Server16.15与官方修复表核对；libxml2是OS依赖，不能当作PG Server自身CVE。数据库输入/XML利用前提未证明，扫描未列发行版修复，**NO_FIX_AVAILABLE / P2条件风险** | 实际数据库角色/RLS/migration/应用回归；非生产数据库渗透测试，不凭版本宣布所有OS依赖安全 |
| NQ-SEC-O03 | Node builder zlib1g / CVE-2023-45853 | scanner Critical；BUILD_TIME | 涉及contrib minizip，不是看到zlib包名就证明可利用；实际链接/发行版适用性尚未完整证明，**UNVERIFIED** | builder无对外产品服务；保留供应商适用性待核对，不算已验证缓解 |
| NQ-SEC-O04 | Maven builder OpenSSL / CVE-2026-84782 | High；BUILD_TIME | 两个包命中；需DTLS handshake retransmission。构建路径使用Maven HTTPS，不提供DTLS服务，未证明此触发路径 | 固定新builder后仍有2 High包命中，不能写成零告警；发行镜像不包含builder |
| NQ-SEC-O05 | 最终Nginx musl/musl-utils CVE-2026-107891、zlib CVE-2026-85091 | Unknown/Medium；runtime | **UNVERIFIED / P2**：固定版本分别1.2.6-r3、1.3.2-r1，实际base尚有告警；未证明NQ当前模块触发路径，也没有虚构缓解 | 不为消除计数进行全OS无差别升级；后续按实际函数/模块与供应商包修复验证 |
| NQ-SEC-T01 | Docker Engine/CLI最新公告 | High/Medium；build/runtime工具 | Engine29.9.2不在92543/92542（29.8.2修复）及42306/41568/41567（29.5.1修复）范围。CLI29.9.2、Compose5.6.1不在15558范围，**NOT_AFFECTED** | 官方仓库公告原文与本机Client/Server/Compose身份分别保存；不推导其他host组件安全 |

主要供应商依据：[Oracle 91403](https://www.oracle.com/security-alerts/alert-cve-2026-91403.html)、[pgJDBC 42.7.14](https://jdbc.postgresql.org/changelogs/2026-10-07-42.7.14-release/)、[Spring 40992](https://spring.io/security/cve-2026-40992/)、[Node 2026-10](https://nodejs.org/en/blog/vulnerability/october-2026-security-releases)、[PostgreSQL安全表](https://www.postgresql.org/support/security/)、[Nginx公告](https://nginx.org/en/security_advisories.html)、[Python3.14.8](https://www.python.org/downloads/release/python-3148/)。原始网页、CNA JSON、GitHub API结果已保存；获取失败与替代来源分开记录。

## 5. 镜像、系统包与实际运行边界

基础镜像的四个 Dockerfile FROM 均已固定 digest：

| 镜像 | Digest |
| --- | --- |
| maven:3.9.16-eclipse-temurin-21 | `sha256:99e61abcff91a9b1333463bd8451fb18495d6eba9250ac66a338b518f8278320` |
| eclipse-temurin:21-jre-jammy | `sha256:affcab5356e1270ac35d2361ee83135af2f03e8d580eca78a6d5d6fdbf048db0` |
| node:24.22.1-bookworm-slim | `sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205` |
| nginxinc/nginx-unprivileged:1.30.6-alpine | `sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03` |

PostgreSQL实际扫描/隔离运行 digest 为 `sha256:ca0bd484cb98bf4b24eb1010e73fb3fcbd6714d240fbc1a10eea5b7dbecb641d`。CI已有digest `sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94` 单独运行 `postgres --version` 证实同为16.15；没有因digest不同而虚构同一镜像身份。

| 最终镜像 | 实际 image ID |
| --- | --- |
| Self-Hosted backend | `sha256:fe38dbecbaa6a7246b4a5d6a588f6738770a7e6545eff008e9e59ffb1c845ebe` |
| Self-Hosted frontend | `sha256:732ad7cea7885b171906cf96723269a2866a786510c90b6063f5ff0c8605edda` |
| Public Demo backend | `sha256:06dc551c79bb0ae5ee896967715cde6dafc12a396b04f060ca5e7b9df7533efc` |
| Public Demo frontend | `sha256:abaf6ff4dd22ce3bec12c25a9b687b46b3cb93b812f66cf399874c39c79c4e5e` |

Trivy0.76.1固定digest `sha256:790fd66d1697395d8693d1acaa1ea702406a20f109b6fc7bac8db623eca6c06c`，已更新漏洞DB。镜像扫描使用 `--scanners vuln --pkg-types os`，**只覆盖OS包**；镜像内应用依赖由真实Maven/npm树、OSV、SBOM与最终JAR提取共同覆盖，没有声称Trivy扫描过Java数据库。Self-Hosted/Public Demo最终JAR分别列出77/82个BOOT-INF/lib条目，包含内部模块。

额外通过同一Dockerfile的`--target build`导出并扫描实际构建阶段：backend `sha256:55ba61f691e4f75db16e33cc3864f2c4aea19b4e776b80194505f00f1fd636e6`，frontend `sha256:1d8c9816d11d4bcac652ac3445fef54da82338f4db9680fc168a60eb1a3db878`。两者OS计数分别与Maven3.9.16/Node24.22.1基础镜像一致；实际构建阶段与最终运行镜像身份没有混用。

| OS扫描对象 | Critical | High | Medium | Low | Unknown |
| --- | ---: | ---: | ---: | ---: | ---: |
| 历史backend1.0.0 | 0 | 0 | 27 | 16 | 0 |
| 历史frontend1.0.0 | 2 | 46 | 84 | 39 | 6 |
| 最终每个backend | 0 | 0 | 27 | 16 | 0 |
| 最终每个frontend | 0 | 0 | 1 | 0 | 2 |
| Maven3.9.12 builder | 0 | 4 | 408 | 163 | 0 |
| Maven3.9.16 builder | 0 | 2 | 97 | 57 | 0 |
| Node24.22.1 builder | 1 | 56 | 87 | 83 | 1 |
| PostgreSQL16.15 | 1 | 61 | 109 | 138 | 8 |

这些是包匹配数，非独立CVE数。Trivy正常发现时退出码1；缓存锁/工具失败也可能返回1，只有存在完整JSON且有Results才计为有效扫描。首次缓存冲突已保留，最终四镜像串行扫描有效。最终外层PowerShell汇总重定向写法出错，但四份JSON、inspect和各自exit均已生成；没有将外层错误作为漏扫或PASS隐藏。

配置核查：backend运行UID10001；frontend使用unprivileged基础镜像；Compose应用端口限定127.0.0.1，数据库无宿主发布端口，runtime.yml只读挂载，日志轮转和healthcheck存在。Compose未设置全面cap_drop/read_only/no-new-privileges，是后续加固观察，不等于已证明漏洞。正式installer生成的凭据与开发Compose测试默认值分开，未把开发密码直接映射到正式部署。后端apt安装curl仍依赖构建时仓库状态，因此必须保留最终镜像ID，不能仅用FROM digest声称整个层可重现。

## 6. PostgreSQL、前端入口与供应链限定检查

实际使用任务独立PostgreSQL16.15。最终两种发行镜像均通过已有maintenance入口在空隔离库执行并验证47个migration，再使用`nq_runtime`启动；不接触用户数据库。查询仅元数据，得到：

- `nq_runtime` 与 `nq_schema_owner` 均 `NOSUPERUSER/NOBYPASSRLS/NOCREATEDB/NOCREATEROLE/NOINHERIT`，owner为NOLOGIN，runtime为LOGIN，runtime成员资格数量0。
- 实际 `SET ROLE nq_schema_owner` 被拒绝。RLS/FORCE RLS及对象owner逐表记录；TenantIsolation真实PG测试还覆盖跨租户读取/写入、连接复用、事务切换、TRUNCATE拒绝与unsafe runtime拒绝。
- 扩展 `pgcrypto1.3`、`plpgsql1.0`；29个SECURITY DEFINER函数的owner、search_path与runtime EXECUTE权限已列出；平台管理函数仍不授予runtime执行权。
- 这只是当前补丁的隔离回归，**不关闭用户指定的官方安装器角色分离独立阻断**，不替代恢复/升级/真实部署资格。

前端检查范围包括实际src的用户输入渲染、URL、token-storage、AppProviders、登录/注册/邮箱验证/平台页面。未发现dangerouslySetInnerHTML消费者；新闻URL经trustedNewsUrl判断，外链使用noopener/noreferrer。session token存localStorage仍属于XSS一旦成立后的影响面；本轮未证明可利用XSS，未擅自重做认证协议。身份变化执行cancelQueries/clear并清账户上下文；操作缓存使用owner身份。平台授权仍由后端执行。682单测提供已有隔离/页面回归；浏览器smoke只覆盖登录入口，不冒称全平台E2E。

研究域实际读取本地CSV/JSON；没有发现pickle/eval/exec/subprocess路径。整个文件载入内存属于操作者输入规模边界观察，未证明远程DoS，不修改算法/数据集/冻结结果。

CI核查：6种Actions固定SHA均通过可信上游tag API逐一匹配，证据为`actions-upstream-verification.json`。workflow使用contents:read，未发现pull_request_target高权限不可信代码执行路径；现有artifact safety和来源hash检查保留。缓存是构建输入缓存，不能取代源码/制品身份；现有npm ci、Git blob来源allowlist、hash/tamper合同没有被弱化。固定gitleaks下载checksum与CycloneDX2.9.3配置仍由原supply-chain lock拥有，未改发布权限。构建插件/扫描工具的全部传递依赖闭包并未因此自动成为已验证安全，保留UNVERIFIED。

## 7. Verification

| 验证 | 真实结果与边界 |
| --- | --- |
| Self-Hosted backend定向回归 | PASS：33 tests，0 failure/error/skip；JWT、Spring Security、HTTP auth、发行身份/health隐私、2个AuthSession PG持久化/并发用例、4个TenantIsolation PG用例 |
| Public Demo backend | PASS：7 tests，0 failure/error/skip；配置guard、发行身份、真实HTTP双用户注册/本地邮件→research/publish→canonical SIM/order/fill/ledger→持久化/重启 |
| JDBC反例 | 旧42.7.9大输入FAIL为预期漏洞证据；42.7.14两项PASS。没有把基线反例清理掉 |
| Frontend unit | PASS：89 files / 682 tests |
| Frontend生产构建 | PASS：Self-Hosted与Public Demo；正式Docker重建也完成两种构建 |
| Playwright | 最初缺bundled Chromium失败保留；使用现有Edge channel后登录smoke1项PASS；runner支持测试10项PASS |
| Python | pytest26 PASS（1个pytest cache权限warning）；mypy19 source files PASS；ruff PASS；pip-audit退出0，local项目skip如上 |
| Docker build/startup | PASS：最终四镜像构建；两组内部网络独立启动，backend health UP，前后端distribution JSON相等。使用ci profile、受限角色、关闭邮件/注册/连续交易的启动smoke；完整注册/交易链由上列真实HTTP集成测试证明。运行时未开放外联，结束清理临时容器/网络 |
| DB角色/元数据 | PASS：两组maintenance退出0、47 migration validate、runtime无特权/无成员资格/owner切换被拒绝；非生产资格结论 |
| SBOM | 后端CycloneDX2.9.3成功，93个组件；前端production SBOM成功；最终JAR库清单另存 |
| CI合同 | 首次因本机Maven cache在D盘而非默认.m2失败；只补齐已有Boot BOM/SnakeYAML解析依赖后ContractOnly通过；不会授予canonical admission。YAML结构/8项拒绝用例通过；首次同进程记录遗留LASTEXITCODE=1，保留原始值，独立pwsh进程重跑实际退出0。workflow135个负向mutation全部拒绝 |
| Release工具合同 | 85 tests，OK(skipped=1)：真实POSIX mode在Windows不可证明。首次有GBK reader-thread解码异常；受影响的单个PosixHandoff用例使用PYTHONUTF8=1定向重跑PASS，不将模拟合同测试当真实安装/恢复资格 |
| 文档/空白 | docs link检查原有123 warnings、0 errors；新增报告后复核。whitespace5 tests PASS；最终raw git diff --cached --check另行保留 |
| 独立审查 | 按fix-finding技能执行一次全新只读候选审查，未提供父任务通过结论；未发现有源码支持的绕过或新增兼容性缺陷。审查不替代扫描与runtime结果 |
| Remote CI / release | NOT_RUN / NOT_CREATED；未commit/push/PR/merge/tag，未创建正式v1.0.1包 |

独立审查候选：7个tracked源文件的`git diff --binary` SHA256为`13613c1cef1ef44ee0104474347d8b37f65d4ad4853f6f70fa2cfff718fa6678`；新增JDBC测试文件SHA256为`f21412ea0c680a85ff0c9131aec6ec8427089ae8befe44dc8c36de444a84423e`。此后未改变这8个实现/配置/测试文件，只补报告和本地证据。

主要可复查命令：

```powershell
git fetch origin
gh api repos/ling5477/nexus-quant/branches/dev --jq .commit.sha
mvn -B -f backend/pom.xml -Ppublic-demo org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree '-DoutputType=json' '-DoutputFile=target/security-dependencies.json'
mvn -B -f backend/pom.xml -Ppublic-demo org.apache.maven.plugins:maven-dependency-plugin:3.8.1:resolve-plugins '-DoutputFile=target/security-build-plugins.txt'
mvn -B -f backend/pom.xml -Ppublic-demo org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom '-DincludeTestScope=false' '-DoutputFormat=json' '-DoutputName=security-after'
# 在frontend目录；before/after原始输出与exit分别保存
npm ci --registry=https://registry.npmjs.org
npm audit --json --registry=https://registry.npmjs.org
npm audit --omit=dev --json --registry=https://registry.npmjs.org
npm audit signatures --registry=https://registry.npmjs.org
npm run test:unit
./release/build-distributions.ps1 -OutputDirectory E:/Project/nexus-quant/artifacts/v101-security/distributions-final-amd64 -Architecture amd64
./scripts/ci/Test-CanonicalDeliveryWorkflow.ps1 -ContractOnly
./scripts/ci/tests/Test-NqWorkflowYaml.Tests.ps1
./scripts/ci/tests/Test-CanonicalDeliveryWorkflow.Tests.ps1
python artifacts/v101-security/scan_osv.py final
python artifacts/v101-security/runtime_images.py
git diff --cached --check
```

扫描器/测试的详细参数、原始非零退出码和重试保留在同名`.log/.exit/.json`文件；测试/运行辅助脚本只在忽略目录。首次Maven未引用`-D...json`被PowerShell拆分、npm文件占用重试、Trivy缓存锁、浏览器缺包、网页403/404、公告详情超时、汇总重定向与文本编码错误均有记录，未用零发现代替工具可用性。

## 8. Remaining Risks 与交付结论

- **P0**：未证明当前NQ对外可达P0；这不是全局不存在漏洞的声明。
- **P1 / release阻塞**：CVE-2026-91403存在Critical供应商告警，但Temurin的影响范围/可用修复及TLS证明缺失；不得宣称风险已排除。另有active Maven plugin与宿主构建工具完整安全闭包、CPython补丁身份等证据缺口，未满足“可信构建链没有已知高危风险”的完整证明。
- **P2**：pgJDBC证书无限流Low无补丁、已列OS残留包/条件路径、宿主Node/Python旧版本、localStorage暴露面、Compose加固观察；逐项保留前提和状态，不虚构利用链、不把不可达代码变为生产漏洞。
- **其他未验证**：Dependabot访问不可用；Windows不能证明真实POSIX权限；arm64、正式源码包、远程exact-head CI、升级/恢复/外部邮件/公开部署均未执行。本轮不重做已知安装器独立阻断，不改变其结论。

**Decision: BLOCKED**。依赖补丁及已列本地回归可供审阅；须补齐上述供应商/构建链证据并按实际风险定向处理后，才能重新判断`READY_FOR_RELEASE_CANDIDATE`。本轮按明确文件清单精确暂存；`NOT_COMMITTED / NOT_PUSHED / NOT_CREATED`。
