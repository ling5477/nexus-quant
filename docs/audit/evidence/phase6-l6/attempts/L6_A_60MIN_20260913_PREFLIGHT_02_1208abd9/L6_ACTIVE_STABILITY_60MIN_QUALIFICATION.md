# L6 Active Stability 60min Qualification

2026-09-13（Asia/Shanghai）；attempt=`L6_A_60MIN_20260913_PREFLIGHT_02_1208abd9`。

**L6_A_60MIN = BLOCKED / QUALIFICATION_INFRASTRUCTURE_FAILURE / L6_FORMAL_CALIBRATION_MANIFEST_MISSING**。

正式运行在入口核对阶段阻断，`FORMAL_RUN=NOT_STARTED / COMPLETION=NOT_COMPLETED / L6_NOT_ACCEPTED`。没有启动 qualification Maven、PostgreSQL、Venue 或 NQ actor，没有重跑 readiness、freshness review、sampler review，也没有进入180min。此记录与旧 `20260913-preflight-01` 分开，旧报告、失败证据及其机器汇总均保持原字节。

## 已核实基线

- Branch=`audit/post-gatey-agent-baseline`；HEAD/upstream=`1208abd904c318b832af152c9c52f8a5f4b46df3`；tree=`cf989b24331f38b9a3618b1fd58de68d3a26c302`。
- 本轮只读查询 [CI 34739867074](https://github.com/ling5477/nexus-quant/actions/runs/34739867074)：completed/success，headSha精确匹配，最终有效jobs=9/9 SUCCESS，failed/cancelled/skipped=0。既有交付记录保留 attempt 1 失败及 attempt 2 成功，不改写历史。
- 规划 `docs/audit/evidence/GATEAUDIT_PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN.md` SHA-256=`80e339594be7223f07783d30e7f18913a53187ecde59b360ecdbf9223c9e45ac`。
- 起始只有6个未提交L6 qualification/delivery evidence；tracked diff=0、stage=0。3965个既有Git可见文件全部逐字节保持，完整起止inventory见raw。未发现candidate drift。
- 当前10秒sampler整改及交付接受证据绑定当前head，本轮复用这些接受事实，不重开采样审查。其报告明确未进行noise-band标定或修改负载率。
- STATUS机器区块仍记载旧L5/L6阶段；本轮按用户明确授权执行隔离L6入口核对，保留该文档原样，不据此宣称更广的当前阶段接受。

## 入口阻断

| 所需冻结合同 | 当前证据 | 结论 |
| --- | --- | --- |
| L5 accepted healthy rate ×25% | `L6ActiveStabilityTest.java:223,225`固定两条每分钟策略；`:139`只调用scanner，没有读取L5标定manifest。稳态名义触发量为2/min，约0.0333/s；这只是代码推导，不是本轮实测。现有L5 S1/S2/S3与C1/C2/C3摘要记录批次耗时、producer/drain和资源观察，但没有找到将该负载绑定到25%目标的冻结记录。 | `L6_A_ARRIVAL_RATE_BINDING_UNVERIFIED` |
| 冻结noise band | 规划第159行要求L5-B相同负载、同采样方式的max-min变化带随manifest固定，第186行将阈值冻结列为L6-A入口；当前用户第12节继续要求drain恢复到冻结noise band。对L5/L6 evidence以及docs/target中manifest/summary/metrics文件的定向检索未定位可绑定的数值manifest。采样整改JSON明确`NOT_CHANGED_NOT_CALIBRATED_IN_SAMPLING_TASK`。 | `L6_A_FROZEN_NOISE_BAND_UNBOUND` |

这些是正式qualification的标定/证据缺口，不是新的production correctness failure，也不证明资源泄漏。未将“未定位”扩大为全磁盘不存在的结论。用户随后确认：远端同一exact head也未找到正式冻结manifest，没有遗漏路径可提供，并明确要求保存本次入口BLOCKED证据后停止。

没有把含故障批次的throughput、含setup/cleanup的总耗时，或短时资源峰值自行替换成持续健康速率和正式noise band。当前harness的240订单上限比规划3000更紧，此事实本身不判错；同样不能为了凑满60min临时调整固定负载、预算或验收阈值。用户第1、17节明确禁止本轮代码/harness/config修改，因此停止于入口并保留证据。

## 环境与未执行范围

实测环境为Windows 11 build 26200、JDK `21.0.9+7-LTS-338`、Docker Server 29.7.2。canonical lock镜像为 `postgres:16@sha256:f1c3376c26f2609ab9f29f71f824103fe2fcd8ee0346485cb6122a4f93df6f94`。本地按image ID及`postgres@digest`元数据可见相同digest；按harness使用的`postgres:16@digest`执行image inspect返回No such image。该只读探测失败原样保存，没有启动容器验证run引用解析，不将它宣称为已证明的运行故障，也未tag/pull/修改配置。PG运行version、V51实测均NOT_STARTED。

正式命令仅记录、未执行：

```powershell
mvn -f backend/pom.xml -pl nq-app -am test '-Dtest=L6ActiveStabilityTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dnq.l6=true' '-Dnq.l6.diagnostic=false' '-DargLine=-Xmx512m'
```

600/2400/600秒合同保持；monotonic计时器未启动，phase start/end/actualDuration=null，各phase sampleCount=0；没有把之前115秒计入。本轮业务计数、重复副作用、账务、逐源Validation、scheduler、backlog、Hikari、资源趋势与控制组均未测，不报告实测零或PASS。只有“本轮未创建owned runtime”可确认，所以owned runtime survivors=0、cleanup=NONE_REQUIRED。既有`nq-c1-occ-20260907`容器未触碰。

## 证据与收尾

机器记录位于 `runs/L6_A_60MIN_20260913_PREFLIGHT_02_1208abd9.json`；metrics位于 `metrics/L6_A_60MIN_20260913_PREFLIGHT_02_1208abd9/summary.json`（均相对phase6-l6目录）。按attempt隔离，避免覆盖已有根目录metrics/summary和旧60min报告。

Raw目录为 `backend/nq-app/target/l6-a-preflight-20260913-02-1208abd9/`：含baseline、CI原始JSON、环境、source带行号副本、检索结果、L5引用摘要、起止input inventory及最终Git结果。逐文件路径、大小与SHA-256绑定在本attempt机器记录中。

本轮新增confirmed P0=0、P1=0、P2=0、P3=1（合并为一个冻结标定入口缺口，QUALIFICATION_BLOCKING）；既有P2/P3及历史修复义务未重评。P0/P1=0不等于运行正确性已验证。

只新增本attempt三份qualification evidence；production/test/harness/config delta=0，stage=0、commit=NONE、push=NONE。结束git diff --check/stat/name-only/cached name-only为空，既有文件起止hash相同。

后续任务为 `NQ-GATEAUDIT-PHASE6-L6-FORMAL-CALIBRATION-MANIFEST-CLOSURE`：先判断已接受L5 raw能否在无新动态运行下精确重建；不足时明确最小缺失calibration，并保持BLOCKED。本轮按用户要求停止，不启动该closure或新动态运行；不现场计算速率、不选择noise band。未来正式attempt必须从T=0开始，且先核实canonical镜像引用可用；60min未PASS，禁止启动180min。
