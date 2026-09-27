# Account snapshot provenance：生产 V55 与 canonical release（2026-09-27）

## 结论与边界

`NQ-GATEZ-ACCOUNT-SNAPSHOT-SEMANTICS-UNBLOCK` 的代码、精确 HEAD CI、PG16/V55 迁移、历史行保护和普通 canonical activation 已验证。此结论仅覆盖账户快照的 NQ provenance、余额口径与时间语义的部署；没有新的 OKX private account observation，也不解释既有 `DIVERGED` 的金额差。正式 divergence collector 的源码仍未找回，状态为 `COLLECTOR_SOURCE_UNAVAILABLE / DEFERRED_NON_BLOCKING_FOR_SNAPSHOT_SEMANTICS`；不得用新写的等价脚本追认旧 qualification。

技术 PR [#44](https://github.com/ling5477/nexus-quant/pull/44) 的实现提交为 `da623c50ce05a6fa72b7e6aa82e347daed8f52a8`，合并后的 `dev` 精确 HEAD 为 `5197b128131aa21be7705ee6cf3619db3cec00bb`，tree 为 `6086ef6f17232e68591b9c9a7d17268798d1546e`。[CI 36314244328](https://github.com/ling5477/nexus-quant/actions/runs/36314244328) 是该 HEAD 的 `push` run，`completed / success / 9 of 9`。目标 release 为 `nq-5197b128131a-39f54b083d7e0571`，manifest SHA-256 为 `6654faa157d187dc0fee188e5ee69e53844e1afadc2dcfa0bbe53f011f2bd00c`，外部 `EXACT_HEAD_CI` admission SHA-256 为 `fd6f53ca9011c92f8cd55308c6fbba0f7e40c5050f63685b6dab5dd522f514f5`；服务器上的独立 release/admission verifier 均 PASS。

## 生产数据库与备份

迁移前 `/current` 是 `nq-f7a5faa782e5-20670623964ad074`。目标是 `nq-gatew-postgres` 容器中的 `127.0.0.1:55432/nexus_quant`，PostgreSQL `16.14`。只读预检为 Flyway V54、失败记录 0、V55 历史记录 0、其他客户端 0、非 recovery、public 表 owner mismatch 0；Java 进程 0，kill switch `ENGAGED`。该主机没有已安装的 `nq-canonical.service`；不存在的 unit 的 `inactive` 返回不作为单独的停机证明。生产 `runtime.env` 不存在、`NQ_LIVE_ENABLED` 未设置且已验证 release 的默认值为 `false`，无应用进程；LIVE 保持 `DISABLED`。

在受限目录 `/var/lib/nexus-quant/qualification/snapshot-semantics-v55-20260927-5197b128` 保存了 V54 的 PG16 custom dump、canonical metadata、源表指纹及恢复结果。dump SHA-256 为 `075c3263548ebdb9c4bd2135e8eb91905866842bce1b38ee793e03f7a09730df`，922781 bytes；metadata 经 `Test-NqCanonicalBackup` 验证为 `PASS / NQ_CANONICAL_BACKUP_VERIFIED`。dump 前后生产源的 82 张表指纹一致。首次隔离恢复因一次性容器缺少源库已有的 `nq_gatey_readonly` ACL 角色失败，原始失败输出保留；未修改生产角色。仅在 `--network none`、无发布端口的 PG16 一次性容器创建同名 `NOLOGIN` 角色后，`pg_restore --exit-on-error --single-transaction` 通过。恢复后 82 张表的全部旧列行数与指纹、owner、ACL 均匹配；源表指纹文件 SHA-256 为 `44e17d650a296710993dedf303451e7c5147ba207f1a63c3e1633872641bcbe2`，恢复报告 SHA-256 为 `0bca9baa2e03767071c7257d4b4b0263e35e2c03c35b5088035786ae914e225c`。dump 和含数据指纹的原件仅留在服务器受限目录，没有提交仓库。

## 唯一 forward migration 与激活

执行器从已核对的 V1–V55 SQL 清单复制到 root 独占的 pin 目录，核对复制后的 V55 SHA-256 `70fe952b3dad34e6cf03209e58a998340b837f1e0a82fd6ca0d7dd109c9ef710`、目录/文件 owner 与权限、PG16/V54、零失败历史、kill engaged 和上述备份身份，再从 pin 目录运行 Flyway。受审 helper 源码 SHA-256 为 `8d478fbe683f7ffa8ddea444e27026596428c9a02a1a13413edda7dc6b2f39bd`，服务器重编译 class SHA-256 为 `53f2bf63c297d25334d885e354e354defdc8ab4a4f878a9712f9ccb4e60ad54e`，独立重编译结果相同；定向 `REVIEW_ONLY` 为 PASS、P0/P1=0。首次 helper 因未绑定执行时 V55 内容被审查拒绝，未执行迁移；修订后只读 `validate-pre` 为 `V54 / only V55 pending`。

生产 `migrate-v55` 唯一一次执行恰好 1 个 migration。随后严格 Flyway validate 两次均通过：`V55 / pending=0 / failed=0`。V55 的四个约束、一个索引和成功历史记录各自存在。两条历史 `account_snapshots` 的 `trade_env`、`balance_basis`、`balance_scope`、`recorded_at` 全为 NULL，旧字段指纹与 V54 备份一致；没有将历史行推断或回填为 LIVE/SIM。

受信 admission 文件以 root/0644 安装，canonical `preflight`、普通 `install`、安装目标 `verify` 均 PASS。`observe-database` 生成签名的 PG16/V55/failed=0 数据库观察，随后普通 `activate` 返回 `COMPLETED`，transaction `59947832e01b486ca952a7790361a2f5`，generation 5。激活后 `/current` 精确指向 `/opt/nexus-quant/releases/nq-5197b128131a-39f54b083d7e0571`；activation head/journal 的 release、前任 release、transaction、generation、digest 与 V55 一致，manifest/admission/source commit 精确绑定。再次回读 PG16/V55、失败历史 0、两条历史 UNKNOWN、旧字段不变、kill `ENGAGED`、Java 进程 0、无 LIVE 配置或活动 runtime。没有执行 legacy bootstrap、OKX 私有请求或交易 mutation。
