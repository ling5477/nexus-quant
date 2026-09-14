# L6 PG tmpfs capacity freeze and preflight

本轮只冻结 L6-A 10/40/10 容量、实现 test-only preflight / projection guard，并运行定向测试与短 PG fixture。没有启动正式 L6-A 或 L6-B。基线 HEAD `2f2ecdff063d5657e606cf27ada6ba7db0d659c3`，branch `audit/post-gatey-agent-baseline`。实现候选、独立审查与交付 receipt 位于 `runs/L6_PG_TMPFS_CAPACITY_FREEZE_20260914/`。

## 数据身份与容量

唯一数值源为已接受 storage run `40c5ba3a-ea8b-4040-82de-f4ac49cff476` 的完整 300/1200/600 秒序列：210 PERIODIC、5 PHASE_BOUNDARY。对应 raw archive SHA-256 `8f70aa4693f5bfa7da9e931f0821e142187e80618fba38f7a49fdd8c62c80986`。本轮入口重新核对原 ZIP SHA；交付包含紧凑 replay ZIP，其四个 raw 文件逐项匹配原 raw manifest，另包含原 manifest 与 accepted summary。紧凑 ZIP SHA `6b0fdc7eeb71174248320aa75a4287da1a8dd466b0a94e0b338575ed64318fb6`。

冻结 pacing manifest SHA `922253c8934e32b7626ced2f9e42407a2a4313c665256b89c483e847ce6abaa6`。不采用失败 `0674a301-3329-498f-9b50-e2a6560635f4` 的测量补数，不改写既存成功或失败证据。

| 项目 | bytes |
| --- | ---: |
| observed peak | 181473280 |
| formal start baseline | 48910336 |
| measurement 20min growth | 83001344 |
| max rolling 10min absolute growth | 61538304 |
| observed 10min drain growth | 28712960 |
| projected warmup 10min growth | 126995260 |
| projected active 40min growth | 731429368 |
| projected drain 10min growth | 92890800 |
| projected formal end peak | 1000225764 |
| uncertainty reserve | 304052400 |
| ceilMiB(peak + reserve) | **1304428544 = 1244 MiB** |

`ORDER_TIME_ENVELOPE_V1` 使用 exposure = ∫(1 + orders)dt。原因是 drain 无新增订单仍产生重复 reconciliation audit；仅用 bytes/new-chain 会遗漏更大库存和更长持有时间。源序列用每 10 秒左端订单库存积分；单调库存使该分母偏小、系数偏保守。正式窗口以每个合法 slot 即时形成订单积分；最大 422 订单，其中 warmup 85、active 337，不假设跳过任何 slot。

源 warmup / measurement / drain exposure 分别为 4290 / 95220 / 78600 order-seconds。正式 exposure 为 26189.9895 / 610542.04335 / 253800。warmup、active、drain 整数上取整系数分别为 4849 / 1198 / 366 bytes per order-second。

active 采用 61 个完整可比 600 秒窗口中最大的归一化系数 `9547776/7975`（约1197.2133），高于 p95 `5877760/5179`（约1134.9218）与全段均值 `20750336/23805`（约871.6797）。归一化最大窗口为 300→900 秒，增长38191104 bytes、exposure31900；绝对最大窗口与归一化最大窗口不是同一窗口。

交叉验证：measurement完成链增量103。普通 per-chain × active最大337链为271567505 bytes；该算法遗漏已存订单的时间成本。按 formal/source 每链 exposure 比校正后，per-chain 与 observed-phase 模型均为532197125 bytes（两者分数值精确相等）。rolling upper模型得到731429368 bytes，约为相同 exposure 均值模型的1.373倍。WAL分配脉冲解释 rolling 与均值差异；没有把均值或单个10秒尖峰当上界。这个交叉验证是同源归一化一致性检查，不冒充三个独立测量实验。

投影覆盖全部3600秒：baseline + warmup + active + drain。reserve另加最终423单位库存的一次最坏归一化600秒窗口：`1198 × 423 × 600 = 304052400`。这600秒是计划时间之外的不确定性 allowance，未再次加入任何已计划阶段。采用字节精确有理数计算，再将总和向上取整到MiB；不手填近似MiB，不直接选择512MiB/1GiB。

该模型是已观察健康语义下的条件性包络，不是未知故障下的绝对物理界限；runtime guard负责拒绝新的增长风险。`L6B_CAPACITY_REQUIRES_SEPARATE_PROJECTION`：1244MiB不授予180分钟运行容量。

## Host memory 与 fixture

冻结预算：NQ heaps 2×512MiB，Venue256MiB，controller512MiB，Maven512MiB，native/tools planning allowance2048MiB。PG非tmpfs allowance768MiB，加tmpfs1244MiB形成PG cgroup最大2012MiB；总预算只加这一个PG cgroup，不再次加tmpfs。Docker memory-swap等于memory。总计6364MiB / 6673137664 bytes。这是预算会计，不是observed RSS或native内存硬上限。

正式入口先读真实available memory及controller/Maven MaxHeapSize，再验证 `budget × 5 <= available × 3`；等号允许，超过则 `BLOCKED / L6_HOST_MEMORY_SAFETY_BUDGET_EXCEEDED`。heap缺失或超预算拒绝。拒绝发生在 PG/Venue/NQ=false、formal timer=false、orders=0；不得提升60%。启动命令需同时设置 `MAVEN_OPTS=-Xmx512m` 和 `-DargLine=-Xmx512m`。

短fixture真实入口available为28977422336 bytes，controller及Maven各536870912 bytes，60% PASS。真实 `stat -f` 确认L5/default bounded路径268435456 bytes、L6契约路径1304428544 bytes。Docker memory分别805306368 / 2109734912 bytes；正常CREATE/INSERT/SELECT、所有PG存储字段、projection observer可用。两个owned容器均删除，survivors=0，未发业务订单。历史unbounded `Pg.start()`不在本任务扩容范围。详见 `short-fixture-proof.json`。

## Runtime contract

后续正式入口同时要求两个canonical inputs及紧凑源bundle与HEAD中的Git对象一致，记录HEAD与两个SHA；结束再次核对输入未变化。仅允许600/2400/600，拒绝旧formal short-smoke借用该authority；短fixture有独立显式opt-in入口。缺失、损坏、重复JSON字段、尾随内容、错误scope、公式/rounding/pacing/hash漂移均拒绝。

每10秒采样计算剩余warmup/active/drain，使用冻结phase系数；只有完整同phase600秒窗口可以上调归一化系数。使用scheduled时点保守预算采样期间增长，拒绝缺样、过期、容量漂移与非单调订单。backlog另预留`ceil(83001344/103)=805839` bytes/chain。

`free < need`锁存 `BLOCKED / PG_TMPFS_CAPACITY_BUDGET_AT_RISK`。pacer admission及实际EMIT写入都关闭，转DRAIN。风险停止后只计算现存库存的最多20秒drain、backlog与reserve；若连drain实际余量都没有则禁止进一步写入并立即清理。正常计划DRAIN计算剩余scheduler/audit增长，不计算未来producer。重型checkpoint不等待120秒；oracle子进程在风险锁存后可取消。风险停止不得运行最终完整覆盖/PASS断言，保存guard历史后清理。

## 验证与审查

定向命令（backend reactor，`-pl nq-app -am`）：`-Dtest=L6PgCapacityContractTest,L6PgProjectionGuardTest,L6PgStorageObservationTest,L6FormalContractTest,L6ResourceSamplerTest,L6StorageContractTest,L6StoragePhaseControllerTest -Dsurefire.failIfNoSpecifiedTests=false -DargLine=-Xmx512m test`。短fixture：`-Dtest=L6PgCapacityFixtureTest -Dnq.l6.capacity.fixture=true`，其余heap/reactor参数相同。

保留targeted-01成功、targeted-02临时日志占用失败、后续成功日志及XML。Python离线负例覆盖SHA损坏、缺样、非accepted源、manifest漂移；golden重复计算完整JSON相等。Java覆盖60%正例/等号/一字节负例、heap无界拒绝、pacing gate与EMIT gate、free等号/少一字节、drain既存库存、backlog、完整窗口上调、单个delta不外推、紧急余量不足。

production / Flyway / config / .github / AGENTS / Skills / pacing manifest delta=0。正式pacing、arrival rate、采样节拍及10/40/10不变，audit与交易业务语义不变。

最终本地定向集加短fixture共49 tests，failures/errors/skipped=0，`targeted-04-with-fixture.log`及`test-xml-04/`保存最终代码证据；其中calculator还运行4个Python负例。`short-fixture-proof-02.json`为最终fixture重跑，初次fixture证据不覆盖。真实fixture覆盖正常projection观察；风险锁存/紧急drain由定向单测证明，不声称已执行正式runtime风险故障长跑。

独立审查者 `/root/capacity_review` 使用另一种逐订单寿命积分重算，所有数学/预算结果一致，初次P0=0/P1=0。发现的P2为退出身份异常跳过proof写盘；已修复并新增保存guard/cleanup同时继续抛错的回归。同一review的有界确认记录在 `independent-review-02.json`，初次记录及稳定candidate指纹保留。历史路径治理检查通过复用既有canonical父路径、calculator显式输入参数解决，`stage-assets-02.log` errors=0，未修改规则或例外。

完整交付结果以本轮目录中的独立审查确认及提交后 `exact-head-ci.json` receipt共同判定，不以本地测试代替9/9 exact-head CI。即使容量交付通过，仍为 `L6_A_NOT_ACCEPTED / L6_NOT_ACCEPTED`。通过本任务后唯一下一动作是另行授权的 `NQ-GATEAUDIT-PHASE6-L6-ACTIVE-STABILITY-60MIN-QUALIFICATION`，从T=0读取两个committed contracts，不现场重算rate/capacity；60分钟PASS前禁止180分钟。
