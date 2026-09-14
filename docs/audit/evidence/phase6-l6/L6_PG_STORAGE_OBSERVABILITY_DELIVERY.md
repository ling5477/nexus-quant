# L6 PG storage observability 精确交付

本记录只交付存储观测与测试工具。提交前状态为 `LOCAL_VERIFIED / EXACT_HEAD_CI_PENDING`；交付接受必须绑定本候选的新 commit 与新 CI，不能复用 baseline CI。最终 commit/CI 对由后续独立 acceptance 记录保存，避免在候选内自引用尚不存在的提交身份。

起点：`audit/post-gatey-agent-baseline`，HEAD/upstream=`5a9d1470e7d59a3970dff768bd9fcf920b752115`，baseline CI=`34763727993 / 9 of 9 SUCCESS`，entry staged=0。

## 候选边界

A 类 allowlist：`L6FormalRuntime.java`、`L6RuntimeResources.java`、`L6PgStorageObservation.java`、`L6PgStorageObservationTest.java`、`L6PgStoragePostgresTest.java`，均在 `backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/`；以及本记录、下述 summary 和 validation archive。

B 类交付量为零：没有猜测/固定新 L6 tmpfs 值，没有正式 60% host-memory preflight，没有 runtime projection guard 或实验容量逻辑。短 probe 的既有预算断言仅约束它自己的 JVM/PG，不覆盖 formal topology，不构成正式 preflight。历史只读容量诊断器、之前 remediation 报告及 60min 失败记录继续保留原工作区，不纳入本次提交；本记录独立保存本次测量补齐与未完成边界。

## 失败事实与测量合同

前次正式 10/40/10 run `d7803598-db2d-45f5-8a54-7ed6f1c38108` 约 57m36s 遇到 PG ENOSPC：`INSERT INTO audit_logs` 无法扩展 `base/16384/16589`。只取得 346/360 resource samples，drain 未完成。这个失败不能证明 audit 是最大的物理占用源；旧数据缺少整个 tmpfs/WAL 时间序列，不能推导容量。原失败证据保留，不追认 PASS。

measurement owner 为 `L6PgStorageObservation`，仅 `L6FormalRuntime` 通过 `formalStorage=true` 将字段加入 `L6RuntimeResources` 的 postgres mandatory set。默认构造入口保持原合同。复用单一 10 秒 sampler，formal command/SQL 2 秒超时及既有采集上限。

- `stat -f` 读取 owned PG 的整个 tmpfs，导出 `pgTmpfsCapacityBytes`、`pgTmpfsUsedBytes`、`pgTmpfsFreeBytes`、`pgTmpfsUsageRatio`；核验 filesystem 类型、单位、计数一致性与 overflow。
- `du` 导出 `pgDataAllocatedBytes`、`pgWalAllocatedBytes`、`pgBaseAllocatedBytes`、`pgGlobalAllocatedBytes`、`pgOtherAllocatedBytes`。SQL 另存 `pgDatabaseSizeBytes` 和 `pgRelationStorage`（schema/relation/tableBytes/indexBytes/totalBytes，最多 512 个 relation，table 包含 TOAST）。
- component bytes 不等于整个 tmpfs used；statfs、du、SQL 为先后读取，不宣称原子快照。other 不是独立 temporary-file metric；没有新增独立 temp 增长指标。既有 auditRows/eventRows 保留，关系明细提供物理大小事实，短 probe 未覆盖业务增长曲线。
- 所有新增 mandatory 字段继续区分 MEASURED 与 UNAVAILABLE；失败阻断 qualification，禁止 UNKNOWN/UNAVAILABLE 回填零。

## 最终候选验证

[Machine summary](runs/L6_PG_STORAGE_OBSERVABILITY_DELIVERY_20260914/summary.json) 保存 5 个源文件的 LF-canonical SHA-256、结果及 archive entry 原始字节 SHA。[完整验证包](runs/L6_PG_STORAGE_OBSERVABILITY_DELIVERY_20260914/validation.zip) 保存原始 probe proof/resources、4 份测试 XML、全部本轮 Maven 日志。ZIP 保持原字节，不受 Git 换行转换影响。

在 `backend` 执行：

```powershell
mvn.cmd -B -l nq-app/target/l6-storage-delivery/targeted-tests-retry.log -pl nq-app -am '-Dtest=L6PgStorageObservationTest,L6ResourceSamplerTest,L6FormalContractTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn.cmd -B -l nq-app/target/l6-storage-delivery/final-pg-probe.log -pl nq-app -am '-Dtest=L6PgStoragePostgresTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6.storage=true' '-DargLine=-Xmx512m' test
```

18 targeted + 1 real PG test PASS，failure/error/skip=0。首次调用因 PowerShell 参数解析在测试前失败，日志另存，不覆盖。parser 正负例覆盖 FS/units/ratio/components/overflow/缺失；UNAVAILABLE 路径证明拒绝且不伪造零。

最终 probe=`af5887bc-3095-46f9-8329-14656e8e449c`，30 秒、3 个真实 10 秒样本，非 superuser collector，SELECT=true/INSERT=false。真实读写 16 行、每行 8192 字节；capacity=268435456 bytes，used=40284160→40488960，最终 free=227946496。所有样本 used+free=capacity、ratio 正确；WAL=16777216，probe relation table/index/total=188416/16384/204800 bytes。最大采集 351ms、最大起始延迟 7ms。cleanup PASS，外部再次核验 owned container 不存在。未启动 NQ/Venue、业务 Order 或正式 timer。

自查与 stage-assets 检查通过。B0Processes/default L5 tmpfs 256MiB 未变；production `backend/**/src/main/**` delta=0。manifest、7.11765s pacing、arrival rate、10/40/10 timing、noise bands、business oracle、capacity value delta 均为 0。未运行本地 Full Maven、任何 calibration、60min 或 180min。

## 未完成边界

`capacity decision=NOT_MADE / formal 60% preflight=NOT_IMPLEMENTED / projection guard=NOT_IMPLEMENTED`。

继续保留 `BLOCKED / L6_PG_TMPFS_CAPACITY_EVIDENCE_INSUFFICIENT`。产品新增 P0=0/P1=0；旧 infrastructure capacity P1 与 audit/log 超冻结 band 信号仍未解决。`L6_A=NOT_ACCEPTED / L6=NOT_ACCEPTED / 180MIN=NOT_STARTED`。

本轮成功交付后唯一下一任务为 `NQ-GATEAUDIT-PHASE6-L6-PG-STORAGE-CALIBRATION`；该运行尚未执行。观测能力交付不授予正式 qualification 重跑资格。
