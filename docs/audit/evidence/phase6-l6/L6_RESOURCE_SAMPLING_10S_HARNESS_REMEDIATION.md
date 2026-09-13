# L6 Resource Sampling 10s Harness Remediation

2026-09-13（Asia/Shanghai）。**PASS / L6_RESOURCE_SAMPLING_10S_HARNESS_READY / MANDATORY_RESOURCE_CADENCE_10S_VERIFIED / CHECKPOINT_AND_SAMPLER_SEPARATED / MANDATORY_MEASUREMENT_MISSING_0 / PRODUCTION_DELTA_0 / P0_0 / P1_0 / READY_FOR_EXACT_HEAD_CI**。

本结论为harness/test-only整改及短时运行通过；提交前记录，后续交付必须绑定新commit的exact-head CI。未运行正式60min/180min；L6_A和L6均NOT_ACCEPTED，不将115秒短时运行视为正式qualification。

## 原阻断与候选

原 `L6_ACTIVE_STABILITY_60MIN_QUALIFICATION.md` 及对应JSON记录 `BLOCKED / QUALIFICATION_INFRASTRUCTURE_FAILURE / L6_A_MANDATORY_RESOURCE_CADENCE_MISMATCH`：JVM指标10秒，但Venue queue、OS handles、audit/event、log/file growth只在至少30秒的checkpoint中采集。原始四个未提交phase6-l6 evidence文件原字节保留，不覆盖历史、不随本次整改暂存。

起始branch=`audit/post-gatey-agent-baseline`，HEAD=`23a7135ad8e0574373c1b818dfed010dc7479a71`；只读重验[CI 34738227591](https://github.com/ling5477/nexus-quant/actions/runs/34738227591)精确head、completed/success、9/9 jobs成功。入口stage=0、tracked diff=0。生产、Flyway、production config、frontend、research、GitHub workflows、AGENTS/Skills均无变更。

本轮修改2个现有测试入口、新增4个test-only类及本记录/机器汇总。[机器汇总](L6_RESOURCE_SAMPLING_10S_HARNESS_REMEDIATION.json)绑定全部6个harness/test文件SHA-256、baseline inventory、精确命令、XML、原始run归档及哈希。除2个授权修改的既有测试文件外，入口所有既有文件原始字节一致。短时运行后只收紧测试的显式static imports，并重跑相同12项定向测试；运行入口与采样实现保持实测字节。

## 统一采样合同

[L6ResourceSampler](../../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6ResourceSampler.java)是唯一周期资源采样器：controller独立线程按monotonic绝对0/10/20/30秒固定节拍运行，所有来源共享 `sampleIndex / sampledAt / elapsedMillis / phase / sampleToken`。实际采集在同一采样窗口内完成，不宣称不同进程的读操作原子同时发生。

每个resource保留MEASURED或精确平台NOT_APPLICABLE状态；缺值/UNAVAILABLE、上一拍身份、漏拍、采集超时均拒绝资格并保存失败行，不缓存、补写或将缺失填0。Windows FD为NOT_APPLICABLE_WINDOWS，同时handles必须实测；Linux反向处理；其他未支持平台阻断。0只来自真实计数，zero actionable的oldest age仍为NONE/null。

固定采样预算在实测前定义：interval=10000ms、最大启动延迟2000ms、最大整拍采集8000ms。固定频率不因checkpoint耗时漂移；超过预算拒绝，不进行追赶补拍。此预算用于可检测的操作系统调度/采集延迟，不是leak/noise-band判据。较长资源趋势窗口及formal 600/2400/600秒常量均未修改。

| Collector | 每拍真实来源与mandatory内容 |
| --- | --- |
| nq0 / nq1 | 独立loopback只读HTTP请求；MXBean heap/GC/threads、真实command及metrics executor队列、Hikari active/idle/pending/max/timeout counter、V51 tick和F007 scheduler/reconciliation progress、真实candidate age |
| venue | 现有 `/l5-metrics` 每拍新GET；真实bounded executor active/queue/completed/rejected/capacity=16 |
| postgres | 独立owned read-only connection；connections、idle-in-transaction、backlog/actionable、audit/event rows、transactions和cursor |
| os | 只查询2 NQ、Venue、controller的明确PID；Windows Handles/WorkingSet或Linux `/proc/<owned pid>/fd` |
| files | 仅owned run目录metadata；logBytes/logDeltaBytes、ownedTempFileCount/ownedTempBytes；保持1GiB raw和2GiB磁盘底线 |
| ownership | 逐owned PID核对startInstant generation；docker inspect只指定owned container ID核对存活；稳定组成4进程/1容器 |

[L6MetricsEndpoint](../../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6MetricsEndpoint.java)仅在L6测试进程注册，loopback随机端口、GET-only、无凭证，容量1的独立executor；每次调用实际resources并返回新observationSequence及请求sampleToken。旧actor后台10秒sampler删除，避免双时钟。端点在actor关闭时回收。

[L6RuntimeResources](../../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6RuntimeResources.java)不使用业务stdin命令通道或checkpoint连接，因而不等待重型业务工作。HTTP请求/OS命令有明确超时，无重试；命令临时输出归owned目录并在finally回收。仅owned fixture给reader授予pg_read_all_stats，避免PG将其他角色的state隐藏后把idle-in-transaction误报0；没有production角色/配置修改。

30秒checkpoint继续导出完整关系快照并执行原账务oracle，不再包含mandatory资源序列。原BEFORE_FILL候选age正对照属于额外业务诊断，不替代任何10秒资源样本。每拍采集失败由controller在下次业务轮次前发现并停止producer；全程结束还校验预期样本总数。

## 测试及短时实测

定向测试 `L6ResourceSamplerTest,L6ReadinessContractTest,L6ProcessOutputTest`：**12 tests / failures=0 / errors=0 / skips=0**。覆盖可控时钟0/10/20/30…110秒所有来源逐拍新值和共用身份；缺测、旧observation、漏拍、9秒慢collector均拒绝；重型command被闩锁阻塞时，真实loopback测量端点仍返回每拍新值；序列化保留availability、样本数、间隔、phase和timestamp。未用真实60分钟sleep测试节拍。

一次 `L6ActiveStabilityTest / nq.l6=true / nq.l6.diagnostic=true`：**1 test / failures=0 / errors=0 / skips=0**。Windows 11/JDK21，owned PG16/V51、1 Synthetic Venue、2真实NQ JVM、controller；OS/JDK环境allowlist，不读取provider credentials。实际monotonic终点115.0009546秒；计划15/90/10秒，formal常量未动。

| 实测项 | 结果 |
| --- | --- |
| Run ID | `0634d254-c0d2-45c3-9c7c-bad6a166dc0c` |
| Unified samples | 12，每拍7个collector；warmup/active/drain=2/9/1 |
| 实际相邻样本间隔 | 9988–10010ms；最大启动延迟15ms |
| 完整采集最大耗时 | 389ms |
| mandatory missing / cadence violations / cached-stale substitutions | 0 / 0 / 0 |
| 业务checkpoint / 原oracle离线重算 | 5 / 全部通过 |
| 最终业务 | 4 Order / 4 Trade / 4 TradeExecuted / 16 Ledger / Position BTC0.4；原Snapshot oracle通过 |
| duplicate mutation / accounting / durable orphan | 0 / 0 / 0 |
| backlog final / oldest age | 0 / NONE(null)，两个actor实测一致 |
| unexpected Hikari timeout / FILLED→FILLED错误 | 0 / 0 |
| 最后资源样本idle-in-transaction | 0 |
| cleanup | controller12088、Venue5108、NQ22020/8316均退出；owned容器不存在；survivors=0 |

12行统一资源记录和所有嵌套availability已逐项离线复核；两个actor每拍observationSequence均依次1…12，sampleToken与当拍一致。未产生旧 `l6-resources-<pid>.ndjson` 周期文件。每来源min/max/first/last及原始phase时间见机器汇总，不以短时资源统计宣称60分钟稳定或no-leak。

原始命令、日志、XML、原始run ZIP、校验脚本与结果保存在 `backend/nq-app/target/l6-sampling-remediation-20260913/`。完整run保留在 `backend/nq-app/target/l6-active/0634d254-c0d2-45c3-9c7c-bad6a166dc0c/`，原始文件未删除。

## 处置与交付边界

production delta=0；本轮P0=0、P1=0，新增P2/P3=0；原sampling P3在当前harness关闭，其历史BLOCKED保持。其他既有P2/P3不重新定级或清零。按用户第13节，test/harness-only整改无需independent review；未增加sampler/readiness review。

下一步仅精确stage本轮6个harness/test文件与2个新evidence文件，commit/push后运行exact-head CI。GREEN后进入 `READY_TO_RESTART_L6_A_60MIN`，正式运行需另行从T=0执行完整10/40/10。本任务不启动正式运行，也不开展noise-band标定或修改负载率；先前记录的这些正式运行入口核对事项不由采样整改替代。
