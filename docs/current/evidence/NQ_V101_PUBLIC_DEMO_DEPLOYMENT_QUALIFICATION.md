# NQ-V101-PUBLIC-DEMO-DEPLOYMENT-QUALIFICATION

本记录只证明本地 Windows / Docker Desktop / linux-amd64 的双发行物资格。目标为 v1.0.1 开发候选；未生成正式 v1.0.1 包、Tag 或 Release，也不授予公网部署权限。

## 候选及前置条件

| 项目 | 实际身份 |
| --- | --- |
| Baseline / HEAD | `3e705b15361f01009f8cac7dc475f76cd89cf922` |
| Branch | `codex/nq-v101-public-demo-deployment-qualification` |
| Platform Admin | PR #72 已合并 |
| Baseline post-merge CI | [39760661954](https://github.com/ling5477/nexus-quant/actions/runs/39760661954)，9 jobs SUCCESS |
| 最终本地候选 | 上述 HEAD + 四文件未提交补丁，`sourceDirty=true` |
| 四文件 raw diff SHA256 | `5abe358f72ce274762c31fb02388052d43f8221b27f01ce783792e0b514c4630` |
| Docker source SHA256 | `5c9366162492c41f864327ddebc0577c31ff0be671fd88dc879d1d808154d83a` |
| Build ID | `793081ff00624cd8a443325f5bd57894` |
| 新候选远端 CI | `NOT_RUN`，本任务禁止 push |
| LIVE | `DISABLED` |

候选使用同一份源码、当前官方 `release/build-distributions.ps1` 与 Dockerfiles 实际构建四镜像，构建前后官方来源摘要相同。读取并保留 `release/build-package.ps1` 与 `release/source-manifest.json` 的正式发布合同，没有绕过或修改其 frozen-export、文件 allowlist、来源哈希及制品完整性规则。当前正式来源 manifest 仍指向 v1.0.0；本次 UUID 镜像及本地归档不冒充正式 release-source 或新版本发布包。

## 实际镜像

| 发行身份 | 组件 | Docker immutable image ID |
| --- | --- | --- |
| NQ_SELF_HOSTED | backend | `sha256:c34d87d6d530be54361362de6fcdc639c5150387c70e41662d6c9b0db540f837` |
| NQ_SELF_HOSTED | frontend | `sha256:3487315f6c3f6266ead38ab0c5f3b39c9ee26eeefddc08d2b3dcec660b4bf7d6` |
| NQ_PUBLIC_DEMO | backend | `sha256:34232e30043329f0e91df126e2f8d5a8c8093591afa26480c4330e106aaf1772` |
| NQ_PUBLIC_DEMO | frontend | `sha256:1e89ac884841d2259e1e7892c98e5a3add754287ea434ab13adb0b733d53aab0` |

四镜像均实际 Docker Build → Inspect → Startup → Health → HTTP；最后实际 Shutdown 结果见收尾证据。后端 UID 10001、前端 UID 101，配对身份由真实 nginx 与后端校验。Self-Hosted JAR 不含 `nq-public-demo` 模块，前端无 Demo 专用 chunk。四项后端错误配置和两项前后端交叉混搭均实际启动失败并退出：不能通过 runtime 配置把 Self-Hosted 提升成 Public Demo。

本地 `docker save` 归档 `qualified-local-images.tar` 为 200844800 bytes，SHA256 `8b08cd4676574b405b3e3a49bed52b518676097f75762ace5ba010d527636cbf`。实际遍历 OCI index、amd64 manifest、config 与全部 layers 并校验 digest，详细身份见 `local-image-archive-integrity.json`。未 push 公共镜像仓库。正式 `nexusquant/backend:1.0.0` 与 frontend 标签的 image ID 与开工前相同。

## 隔离环境与验证边界

- Self-Hosted / Public Demo 各自独立数据库、volume、内部 network 与 loopback ingress；所有发布端口绑定 `127.0.0.1`。没有连接或修改正式安装数据库。
- 两库均为 PostgreSQL 16.15，使用镜像内 Flyway 全新迁移 V1–V47 并 validate。两后端实际 JAR 的 47 份 migration 与当前源码逐字节相同，未修改历史 migration。
- migration 登录身份、NOLOGIN owner、应用 runtime 分离。runtime 非表 owner、无 owner 角色成员关系、NOSUPERUSER、NOBYPASSRLS、NOINHERIT；平台 reader 单独授权，仅调用受限投影函数，原始 ledger SELECT 被拒绝。
- SMTP 仅为任务本地 Mailpit。行情全部来自明确标记的本地合成设施；真实系统时间、bar close 与 freshness guard 均保留。未使用真实交易账户或真实下单。
- 后端每套 1.5 CPU / 1536 MiB、数据库 1 CPU / 768 MiB、前端 0.5 CPU / 192 MiB；应用池最大 4。只证明本地两个活跃租户及对应请求边界，不证明公网容量。

## 验证结果

| 项目 | 结果与证据 |
| --- | --- |
| Self-Hosted Compatibility | 管理员实际登录；研究、回测、发布、既有 SIM 正式链成功；canonical 1 order / 1 trade / 6 ledger entries；真实 nginx UI 渲染；注册及平台接口未开放，不依赖 SMTP |
| Demo Registration / Funding | A/B 正式注册、本地邮件验证、独立 Tenant；各自三个并发验证请求与重放不重复发放初始资金；每用户唯一 10,000 USDT，且不把验证邮件失败账户启用 |
| Demo SIM / Performance | PASS：最终候选 A/B 各自真实调度产生 1 order / 1 trade；每账户唯一 funding pair + 4 TRADE postings，共 6 ledger entries；额外 UI 注册用户只有 funding pair。UTC 12:00 收盘后自然执行，没有修改 freshness。A/B 正式冻结估值均 VERIFIED；equity=9979.55523574、netPnl=-20.44476426，初始资金不计入收益 |
| Platform Admin | PASS：正式 CLI 安装的 PLATFORM_ADMIN 没有普通 Tenant Membership；真实 nginx 平台登录、搜索/过滤、含成交只读绩效、禁用/启用及身份缓存隔离 E2E 1/1 PASS。身份切换的未绑定用户来自本轮真实注册 UI，A/B 两个已绑定交易租户另由完整 HTTP/RLS 验证 |
| Tenant Isolation | 双向跨租户策略、研究、回测、发布、canonical facts/performance 拒绝；tenant header override 403；40 次、4 并发真实 Hikari 切换无泄漏；runtime RLS 跨租户 ledger 为 0，缺失 context 的 orders 为 0；非空 own trade 正例见最终证据 |
| Economic Fact Integrity | PASS：平台经济写入/快照动作 403；跨至少 34 秒及一次调度周期，禁用租户经济指纹不变；旧 JWT/续期/登录/恢复被拒绝；后台轮询排除 A 且包含 B；用户上下文 TENANT_ACCESS_DENIED，后台上下文 TENANT_SUSPENDED。启用仍保持 suspended/manual resume，旧 Session 不复活 |
| Restart / Recovery | PASS：两后端实际 restart、StartedAt 变化，经济事实及 manual resume 状态 MATCH；两套全新 PostgreSQL 隔离恢复各 122 张表，以及 sequences、RLS/owners、函数定义/owners 全部严格 MATCH，Flyway V47 validate。原镜像连接恢复库健康启动，用户正式显式 RESUME 后清除暂停，经济事实不变 |
| Rate / Quota | 注册过大 body 413、15 分钟持久限流 429 且持续拒绝；每租户第二个 continuous SIM 绑定被拒绝；本地 CPU、内存及连接池有界 |
| Monitoring / Failure Recovery | 实际 health 与资源采样、OOM/重启计数、容器日志保存；本地 SMTP 暂停后 outbox PENDING/attempts=2，恢复后 SENT/attempts=3；非验证账户保持禁用；实际进程重启与恢复库启动另见恢复记录 |

重启与恢复严格分开：重启比较既有 canonical 经济事实及手动恢复状态；备份在停写后对全部 public 表、序列、RLS/owner、函数定义与 owner 建立指纹，使用 custom pg_dump 与角色备份在两个全新隔离 PostgreSQL 容器恢复，再逐项比较，并用实际镜像和 runtime 身份启动。不会把 HTTP 200 或日志存活替代业务事实一致。

## 真实缺陷、修复及审查

基线 Docker 中，Demo 用户已有成交和 VERIFIED 决策事件，但正式 `POST /api/paper-trading/runs/{id}/equity-snapshots` 被 Demo 白名单拒绝 403，前端动作也被旧角色条件禁用；平台因此无法读取派生绩效。历史测试直接调用服务，掩盖了 HTTP 集成缺陷。这是一项已关闭 P1；没有修改 canonical Order/Trade/Ledger 或平台绩效计算逻辑。

最小修复：精确开放原有 owner-scoped 快照 POST 与单快照 GET，前端复用现有发行角色 capability；测试改走正式 Servlet HTTP，验证 owner、幂等、跨租户 404、平台 403、经济事实不变，并在真实注册 E2E 中点击保存可信快照。

- PostgreSQL / Servlet HTTP 定向回归：3/3，0 fail/error/skip，BUILD SUCCESS。
- 发行角色前端单测：3/3 PASS；两发行物正式 Vite/tsc Docker build 实际成功。
- 独立审查者未参与实现，冻结同一四文件 diff；P0/P1/P2=0/0/0，独立实际 Policy/Filter JVM probe 24 assertions PASS。没有将复用的实现者 PG/HTTP 结果冒充审查者重跑，也未启动全量审计。
- 最终 Docker / 浏览器 / 备份恢复结果由本任务执行，审查不替代这些证据。

## 保留的失败及重新运行

1. 初次 Docker Hub auth 直连超时，使用已有本机代理后成功。失败与成功构建日志都保留；未更改共享 Docker 配置或更换依赖。
2. 首次行情夹具的 quote_volume 与后续 feed 不同，真实 guard 正确触发 `DATA_REVISION_DETECTED` 并停止。仅修正任务合成数据夹具，在新数据库和身份中重跑，没有改产品 guard。
3. 第二次基线实际产生两名 Demo 用户各一笔成交，暴露上述快照授权集成 P1。旧候选停止并冻结，旧成交不计入最终修复候选验收。
4. 最终修复候选重新构建四镜像、新库、新用户、新研究/发布/SIM；自然 `STALE_DATA` / `WAITING_FOR_BAR_CLOSE` 期间没有改时钟、造未来数据或绕过 freshness。最终成交证据只取自 attempt3。
5. 额外采集器曾错误假设单账户应至少 7 条分录、backtest_runs 直接持有 tenant_id、禁用用户上下文直接触发 TENANT_SUSPENDED。按真实 schema 和 guard 顺序修正为唯一 funding pair + 每成交 4 条 TRADE 分录、通过 backtest_configs 关联租户，并同时验证用户权限拒绝与后台暂停拒绝；没有修改生产代码或削弱断言，详情见 `collector-corrections.json`。
6. 早期浏览器采集曾遇到 loading / 本地化 selector 与历史无平台估值，失败记录保留；最终真实 nginx 执行记录与截图单独标识。

## 关键证据索引

以下路径相对于本次脱敏证据目录；`attempt3` 才是最终修复候选运行，历史 attempt1/2 不代替最终证据。

| 证明 | 文件 |
| --- | --- |
| 同源四镜像、实际 image ID | `images-fixed/distribution-images.json`、`attempt3/candidate-provenance.json` |
| 构建步骤与依赖解析 | `build-uuv2u8jgvoged0r2hes3yekbh.log`、`build-nc4k2wghn3l1zp3sz6zaku9m0.log`、`build-fg8vsg6y4xcz6m8ygkegimw39.log`、`build-sm4wikjm3gdvn29gu6xfhtoal.log` |
| 镜像归档完整性 | `attempt3/local-image-archive-integrity.json`、根目录 `qualified-local-images.tar` |
| 发行边界与拒绝错误配置 | `attempt3/image-distribution-boundaries.json` |
| 实际启动/HTTP | `attempt3/startup-health-http.json` |
| 注册、验证并发与资金幂等 | `attempt3/registration-funding.json` |
| A/B 正式研究→回测→发布→SIM | `attempt3/dq-demo-user-a-product-chain.json`、`attempt3/dq-demo-user-b-product-chain.json` |
| 真实调度与非空经济事实 | `attempt3/canonical-scheduler-observation.json`、`attempt3/prebackup-nonempty-canonical-role-proof.json` |
| 正式快照与平台绩效 | `attempt3/canonical-frozen-valuation-and-platform-performance.json` |
| 浏览器 | `attempt3/browser-registration.log`、`attempt3/browser-platform.log`、`attempt3/self-real-nginx-browser.json` 及相应截图 |
| 租户 HTTP/RLS/连接池 | `attempt3/security-isolation-runtime.json` |
| 禁用、Session 撤销、执行阻断 | `attempt3/disable-enable-execution-integrity.json` |
| 重启 | `attempt3/actual-container-restart-integrity.json` |
| 备份/恢复全表身份 | `attempt3/backup-manifest.json`、`attempt3/self-backup-fingerprint.json`、`attempt3/demo-backup-fingerprint.json`、对应 `*-restored-fingerprint.json`、`attempt3/isolated-restore-verification.json` |
| 恢复后正式 HTTP 与显式 RESUME | `attempt3/restored-container-http-explicit-resume.json`、`attempt3/nonempty-canonical-facts-and-runtime-role-proof.json` |
| 日志、资源及清理 | `attempt3/runtime-logs/`、`attempt3/final-container-runtime-inspection.json`、`attempt3/final-resource-statistics.jsonl`、`attempt3/actual-shutdown-verification.json`、`attempt3/task-cleanup-verification.json` |
| 独立审查 | `independent-integration-fix-review.md`、`independent-review/policy-filter-probe.log` |
| 全部脱敏证据文件哈希 | 根目录 `evidence-manifest.json` |

## 公网剩余阻断

| 项目 | 状态 | 限制 |
| --- | --- | --- |
| Public Email | NOT_QUALIFIED | 只有本地 Mailpit；没有真实服务身份、投递/退信等证据，未向外部地址发信 |
| Public Marketdata License | UNVERIFIED | 合成数据测试许可不等于交易所行情公网再展示许可，需取得明确许可依据 |
| HTTPS / TLS | NOT_VERIFIED | 仅 loopback HTTP，没有实际域名、证书或公网 TLS 部署证明 |
| Public Capacity | NOT_QUALIFIED | 当前仅两个活跃 SIM 租户、40 次池切换及限流/资源配置边界；没有公网负载与容量证明 |
| Public Deployment | BLOCKED / NOT_READY | 上述外部条件未就绪，不在本轮申请、购买或临时部署 |
| LIVE | DISABLED | 没有 LIVE 或真实账户授权 |

## 收尾与交付

```text
Task: NQ-V101-PUBLIC-DEMO-DEPLOYMENT-QUALIFICATION
SELF_HOSTED_IMAGE: BUILD_PASS
PUBLIC_DEMO_IMAGE: BUILD_PASS
DISTRIBUTION_IDENTITY: PASS
SELF_HOSTED_COMPATIBILITY: PASS
DEMO_REGISTRATION: PASS
DEMO_SIM_FUNDING: PASS
PLATFORM_ADMIN: PASS
TENANT_ISOLATION: PASS
ECONOMIC_FACT_INTEGRITY: PASS
POSTGRESQL_RLS: PASS
LOCAL_CONTAINER_DEPLOYMENT: QUALIFIED
PUBLIC_EMAIL: NOT_QUALIFIED
PUBLIC_MARKETDATA_LICENSE: UNVERIFIED
PUBLIC_DEPLOYMENT: BLOCKED / NOT_READY
LIVE: DISABLED
Findings: P0=0 / P1=0 / P2=0 unclosed implementation findings
Historical P1: canonical snapshot HTTP integration, fixed and requalified
Decision: LOCAL_CONTAINER_QUALIFIED (linux-amd64 / isolated local environment only)
Commit: NOT_COMMITTED
Push: NOT_PUSHED
Release: NOT_CREATED
```

任务容器 15 个、任务网络 4 个、任务专属 volume 8 个已精确清理（另随任务测试容器移除其匿名 volume），剩余任务资源为 0。已有用户容器身份全部保留，本地候选镜像和正式 v1.0.0 镜像保留。数据库 dump、角色文件及运行配置另保存在 `C:\Users\Lingyu\.codex\qualification-private\nq-v101-demo-docker-20261022-793081ff`，ACL 移除继承，仅当前用户及 SYSTEM 获权。

非敏感原始结果、脱敏构建/运行日志、独立审查、镜像归档与证据文件哈希：`E:\Project\nexus-quant\artifacts\qualification\nq-v101-demo-docker-20261022-793081ff`（Git ignored）。原始 task harness 和私有配置位于任务私有临时目录；数据库 dump / 角色备份保留在独立私有目录，不进入 Git 或上述公开证据集合。

Files Changed：

- `backend/nq-app/src/main/java/com/guidinglight/nexusquant/app/security/web/DemoTenantApiPolicy.java`
- `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/PlatformReadPostgresIntegrationTest.java`
- `frontend/src/features/paper-trading/components/PaperEquitySnapshotAction.tsx`
- `frontend/tests/e2e/public-demo-registration-real.spec.ts`
- `docs/current/evidence/NQ_V101_PUBLIC_DEMO_DEPLOYMENT_QUALIFICATION.md`

Commit: `NOT_COMMITTED`；Push: `NOT_PUSHED`；Release: `NOT_CREATED`。无 Tag、无公共镜像 push、无公网部署。临时容器、任务网络及任务 volume 精确清理结果见 `task-cleanup-verification.json`；未执行 prune，用户原有容器与正式镜像保持。
