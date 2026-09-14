# L6 Windows temp-log cleanup resilience

本地实现、目标验证和定向独立审查通过；交付接受以本候选提交后的新 exact-head CI 为准。L6-A / L6 仍 NOT_ACCEPTED，本任务未运行正式60min。

## 根因与证据边界

历史 run 9e7c7b06-9cd6-45e5-8388-bfcaae07f77d 在约45m40s由 B0Processes.commandUntil 的 finally 删除临时日志抛 FileSystemException 中断。该 finally 会覆盖正常返回或原始异常。原始 helper stdout 有236笔完整链结果，但没有独立退出码/读取成功遥测；不追认历史 exit=0，具体占用进程未知。历史失败及其清理证据保持原样。见同目录 root-cause.json。

## 改变的行为

- 已读取输出且确认子进程死亡后，日志删除 IOException 记录为 NON_BLOCKING；重试最多3次，退避10/20ms。后续命令和 JVM shutdown 只清理本实例创建且已消费的日志。
- 登记上限32个，残留预算16MiB；到达数量或字节门时停止普通命令准入。未读取日志保留，不交给延后删除；无外部路径扫描、无无限重试、无 deleteOnExit 无界注册。
- exit!=0、超时、不可读输出、不可用观测及预算故障仍失败。进程无法终止优先于命令结果，原异常作为 suppressed 保留；StoragePending 身份保留，中断在清理后恢复。
- Pg.close 的 owned docker rm 改用不依赖临时日志的有界子进程：45s deadline，必要时强制终止并等待5s；非零退出仍失败。随后原有 helper 核验容器缺失，证据不可用仍失败。只删除既有 Pg 持有的容器ID，不改变L5业务语义。

## 验证与审查

最终目标测试12/12 PASS：正常stdout/stderr、暂时/持续删除异常、后续命令继续、数量/字节边界、非零退出、超时和PID清理、StoragePending、不可读输出、不可终止进程、观测失败、旧日志故障后owned cleanup仍执行。

最终真实Windows probe 1/1 PASS：24个真实子命令，输出读取后持有NOSHARE_DELETE句柄；72次真实删除失败可观测，所有命令成功。每轮释放句柄再sweep，最终残留0、survivors0。未使用交易runtime或正式qualification。

一次定向独立审查发现P1：旧日志异常仍可能阻止Pg.close启动清理；已改为temp-free removal。对实质修复做受影响范围复核，P0=0/P1=0、PASS，候选起止hash相同、stage=0。没有review-of-review。

初次目标测试的中文夹具stdout受Windows编码影响，已改为显式UTF-8字节输出；原失败及后续成功均在validation.zip保存。生产、manifest、容量模型、projection math、pacing、duration、CI workflow、历史qualification证据均无本轮改动。

候选身份、JUnit XML、原始日志、真实probe、审查及保护检查见candidate-final.json、validation.zip、review-final.json、verification.json。交付结果另记delivery-result.json，避免对尚未完成的CI提前宣称成功。
