# L6-B restart-continuity runner

本批只修改 qualification test harness。生产源码无变更，L6-A accepted evidence 与原合同保留。

## 固定协议

正式入口 `L6BQualificationTest#formal180Minutes`，仅 `nq.l6b.formal=true`；600s warmup / 9600s active / 600s drain。probe 使用独立的 60/180/60s，不能形成正式接受。

按 canonical plan 在约 T+2400/4800/7200s 对 actor 0/1/0 执行 graceful / forced / bounded-delay recovery restart。事件只能在正常 pacer 发出新订单、有真实 backlog 时启动。第三次只延迟原 PLACE 响应 700ms，不改订单、fill、费用或TradeExecuted身份。所有事件保持同一PG container、database identity、postmaster启动时间及Venue PID/start identity。

新代必须 ready，恢复backlog，完成两次公平cursor绕行及真实tick，完整业务oracle通过后才标记RECOVERED；之后继续新业务全链。所有PID只能由owned registry控制，旧代结束后才启动新代，不重建fixture。

## 容量与证据

`L6_B_RUNNER_CONTRACT.json`绑定accepted模型、manifest、A报告副本和evidence-volume-analysis。1434为最大订单库存，1080为总时长/10s派生采样量，190为60s检查点+final+3次restart边界额度。每代callback上限3000，command上限6380，reconciliation上限4415。

事务按常态reconciliation、业务链、scheduler、sampler、controller、checkpoint、连接健康、restart startup/fair-cursor recovery、一次delay分别计费：投影3642928，reserve39382，向上整千冻结3683000。保留独立rolling rate guard。

PG容量使用本次无workload准备baseline + accepted订单存续时间积分 + allocation burst + 三次startup/backlog存储额度 + 10min最大库存reserve；参考baseline48910336时为10227MiB。参考值不替代正式入口测量，定容后不可改。总owned预算必须不超过启动前可用主机内存60%，正式T=0前再测一次；运行时每10秒报告可用内存并保护冻结入口40%的可用余量，不把已分配的本轮内存从完整预算中重复扣除。

完整DB快照独立gzip，完整Venue事件只存一份append-only journal。每个快照绑定event prefix长度、顺序与SHA256，可随时重建原完整业务oracle。冻结压缩比例8/16来自accepted快照最大比率的2倍上取整；每事件512 bytes硬门、905576 event cap，raw上限1744830464 bytes。磁盘预留max(2GiB, entryFree/5)，文件上限10000。历史原始证据不删除。

## 运行与判定

源码、构建配置、编译输出、HEAD与输入在T=0前、检查点及退出核验指纹。正式期间stage=0、代码/fixture/contract/budget不变；任何漂移使本次资格失效。

资源按logical actor/generation/PID分组。DOWN/STARTING/STOPPING只报告生命周期，不伪填JVM零值。自然GC按PID/collector/id去重，以uptime映射事件时间；后代前600秒初始化单列。每代至少3个完整600秒可比窗口。同代连续3窗口增长超过冻结噪声且同代末段未恢复，输出LEAK_SUSPECT；新PID低值不能消除旧代疑点。缺少自然GC或窗口明确INCONCLUSIVE。

正式分析器 `l6_b_analyzer.py`回放所有完整oracle，检查16个active窗口的新业务全链、scan、validation、recovery tick和cursor进度；聚合各代资源、PG/WAL/audit/event/log/raw及drain。持久化audit/event是合法留存量，结合业务库存和存储模型解释，不用正斜率直接推断leak。正式完成但有未解疑点只能COMPLETED_NOT_ACCEPTED。

Maven成功表示runner测量完成。正式资格接受必须额外满足分析器、完整证据复核及独立审查；probe、CI和清理成功均不能替代180min接受。
