# L6 PG Storage Calibration Boundary Timing Remediation

实现、最终35项定向测试及同一次独立定向review的修复确认已完成；新exact-head CI待交付后验证。本记录不接受正式storage calibration，不冻结tmpfs capacity，也不接受L6-A/L6。

## 历史失败与2000ms authority审计

正式失败run `0674a301-3329-498f-9b50-e2a6560635f4` 保持 `BLOCKED / PG_STORAGE_CALIBRATION_INFRASTRUCTURE_FAILURE`：151 valid periodic samples、128 full chains、measurement/drain boundary迟到3178ms，600秒drain未完成。全部原始证据保留原字节，不标PARTIAL_PASS、不拼接未来run、不用于容量冻结；[失败证据身份](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/failed-attempt-preservation.json)。

基线branch=`audit/post-gatey-agent-baseline`，HEAD/upstream=`0f34b6b1d7aeb0ad3404c04731ff0fed8f978873`，[基线CI 34805659823](https://github.com/ling5477/nexus-quant/actions/runs/34805659823)=9/9 SUCCESS。这不是本次待交付candidate的新CI。

2000ms最早来自commit `1208abd9` 的 `L6ResourceSampler.MAX_START_LAG_MILLIS`，[采样合同记录](L6_RESOURCE_SAMPLING_10S_HARNESS_REMEDIATION.md)明确其为10秒周期样本的启动延迟预算，另有8000ms采集预算。commit `0f34b6b1` 将同一常量用于 `boundaryAt`，并在[storage harness closure](L6_PG_STORAGE_CALIBRATION_HARNESS_CLOSURE.md)记录沿用2秒容差。

[冻结L5/L6 plan](../GATEAUDIT_PHASE6_L5_L6_SCOPE_AND_QUALIFICATION_PLAN.md)没有数字化的2000ms phase transition容忍值；正式 `L6StorageCalibrationContract`只定义300/1200/600秒时长；manifest冻结pacing、rate和采样间隔，没有另行规定逻辑phase可以延后2秒。故原数字是已交付harness的观测预算，不能同时表达逻辑阶段、producer cutoff和采样及时性。保留2000ms而不放宽；无需修改冻结规划。详见[authority audit](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/authority-audit.json)。

## 新旧时序

原driver在循环开头判断phase，随后执行observer/reconciliation/checkpoint，下一轮才处理phase transition；boundary与periodic共用同步sampler实例。失败时最后MEASUREMENT checkpoint直到1503.178351599s才完成，随后边界被拒绝。

新[phase controller](../../../../backend/nq-app/src/test/java/com/guidinglight/nexusquant/app/smoke/L6StoragePhaseController.java)直接以monotonic T0+duration计算phase和admission；deadline执行线程仅登记transition并提交有界边界任务。独立boundary执行线程使用单独reader和同一 `L6RuntimeResources` collector实现，不等待periodic sampler的锁或业务checkpoint连接。periodic仍只负责原10s样本。两个执行通道拥有不同token namespace、各自sample count和文件，避免重复计数。

| 合同 | Owner / 语义 | 失败边界 |
| --- | --- | --- |
| Logical phase | T0+300s/+1500s/+2100s，phase()直接读monotonic clock | 没有采样或cron的附加grace |
| Producer cutoff | pacer emit前再次读clock与phase admission；命令发送前controller gate；子JVM保留canonical trigger前deadline检查 | deadline到达即拒绝新admission，missed slot不补发 |
| Controller响应 | 独立deadline通道，记录phaseTransitionLatenessMillis；记录延迟不移动逻辑deadline | >2000ms → PHASE_TRANSITION_DEADLINE_VIOLATION |
| Boundary observation | 独立collector通道，逐条记录scheduled/observed nanos、lateness、phaseBefore/After | start>2000ms或collection>8000ms → BOUNDARY_OBSERVATION_DEADLINE_VIOLATION；其他采集失败单独分类 |
| Periodic cadence | 原sampler的绝对10s节拍，periodicSamplerLatenessMillis | 原2000ms start/8000ms collection不变 |

measurement deadline的处理顺序为：close admission → freeze已发出的measurement slot集合 → MEASUREMENT_END采集 → DRAIN_ENTERED记录 → DRAIN_START采集 → 允许driver继续drain工作。即使deadline线程尚未获调度，producer自身的monotonic检查也不授予迟到admission。measurement workload集合记录已提交命令的slot身份，完整业务完成仍由既有full-chain counter/oracle证明。

五个boundary各自一条独立观测：WARMUP_END、MEASUREMENT_START、MEASUREMENT_END、DRAIN_START、DRAIN_END。无“相近periodic替代boundary”。每条包含boundaryType、scheduledElapsedNanos、observedElapsedNanos、latenessMillis、sampleType、phaseBefore、phaseAfter；controller实际响应、boundary观测和periodic迟到分别记录。

capacity-risk公式与常量未修改，提前停止仍锁存producer并进入原有bounded drain；新controller停止后续正式boundary派发，保留风险停止和提前DRAIN_END的独立身份。formal 5/20/10、256MiB、7.11765s、10s cadence、full-chain定义、scheduler频率、manifest/noise bands均不修改。

## 定向测试与唯一一次runtime smoke

[最终测试结果](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/review-fixes-final-targeted-results.json)：35 tests、0 failures/errors/skips，绑定最终8文件候选。其中10个phase tests覆盖：可控300/1500/2100秒deadline及5个唯一boundary、periodic启动迟到4000ms但phase/cutoff正常、真实periodic collector锁被阻塞4s但boundary独立执行、checkpoint/snapshot pending时deadline前允许/at-and-after拒绝、missed slots不补发、真正controller迟到3178ms拒绝、及时transition但boundary迟到的独立失败原因、9秒collector拒绝、capacity-risk提前drain、循环中途跨deadline的业务派发gate以及顶层机器失败分类。另一个真实文件并发测试验证iterator的hasNext/next持有共享锁，且临时删除等待metadata读取完成。复用原pacer、sampler、storage contract、process output回归，不执行35分钟sleep测试。

[最终日志/XML归档](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/review-fixes-final-validation.zip)绑定最终candidate。首次31项与随后32项验证保留于[初始validation archive](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/targeted-validation.zip)，修复后第一次35项验证保留于[中间validation archive](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/review-fixes-validation.zip)，各自身份独立。

唯一一次storage smoke运行于初始5文件候选 `84dc2bb046d00fd3e753ee92d2065c4f196effe0e7c8320fdce3cbb6adea1c45`，不是最终8文件候选的重新运行；其未改变的phase/pacer/boundary及jitter时序观察仅作有限范围复用。审查后修复由最终定向测试和原审查者修复确认覆盖。smoke=`5c5d31a4-ceae-4348-9507-0284bf4ab213`，20/50/30s，result=`STORAGE_CALIBRATION_SMOKE_PASS`。通过显式且仅限short-smoke的test开关，在checkpoint业务通道注入一次65.0109948s→74.0297289s阻塞，跨越70s deadline。periodic没有故意违约：>3178ms periodic lateness的拒绝/隔离在可控时钟测试中证明；运行smoke验证的是checkpoint抖动下periodic仍有效。不得把该区别省略。

| 实测 | 结果 |
| --- | --- |
| Phase response lateness | 20s/70s/100s处分别10/7/12ms |
| Boundary start lateness | 12.574 / 515.218199 / 8.3403 / 495.166699 / 13.0057ms，五条均有效 |
| Periodic / unique boundary | 10 / 5；共15个唯一token |
| Max periodic start lag / collection | 15ms / 575ms |
| mandatory missing / cadence violation / stale reuse | 0 / 0 / 0 |
| Full chains | 7；Trade=7，TradeExecuted=7，Ledger entries=28，Position=0.7 |
| Deadline后admission / drain新order | 0 / 0 |
| Final backlog / oldest age | 0 / NONE |
| command queue / Venue queue | 0 / 0 |
| duplicate mutation/accounting / orphan | 0 / 0 |
| capacity risk | false，256MiB未改变 |
| cleanup | owned controller/Venue/2 NQ/Maven及精确PG容器均退出，survivors=0 |
| Manifest entry/exit | 922253c8934e32b7626ced2f9e42407a2a4313c665256b89c483e847ce6abaa6，原样一致 |

[Smoke summary](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/smoke-summary.json)、[完整raw ZIP](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/jitter-smoke-raw.zip)、[逐文件SHA-256](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/smoke-raw-manifest.json)。所有结果只证明本次harness整改和短时smoke，不生成PG_STORAGE_CALIBRATION_ACCEPTED。

## Review与交付状态

同一次定向独立review及其发现项的有界修复确认已通过，scope仅时间authority、cutoff、boundary ordering、采样解耦、no-catch-up和fail-closed guard。未新开第二次全面review。初始P0/P1=0/0，P2=3；最终open P0/P1/P2/P3=0/0/0/0。

| Finding | 修复及确认 |
| --- | --- |
| TIMING-REVIEW-01：循环中途跨deadline绕过循环顶部gate | 每个实际command/FILL/checkpoint入口重查monotonic deadline与drainReady；测试覆盖边界pending时拒绝 |
| TIMING-REVIEW-02：timing失败坍缩为FAILED | 顶层cause-chain分类明确保留两种BLOCKED timing violation；正负例确认 |
| TIMING-REVIEW-03：并发collector共享临时文件的枚举/属性/删除竞态 | 单次iterator advance及type/size与临时删除使用同一短临界区；外部采集、进程等待、全目录扫描不持该锁；真实文件并发测试确认 |

[独立审查历史与最终确认](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/independent-review.json)记录初始发现和两次有界确认。最终review start=end fingerprint=`631f7aa46e96d1c47e53044d4f6f39b454d17fdcc82f50e45d1ff3ac90d162f6`，8个source SHA无变化，stage start/end=0/0，全程REVIEW_ONLY、无写入。

[最终candidate identity](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/candidate-final.json)绑定8个源文件；production/Flyway/production config/manifest/pacing value/tmpfs value delta=0。[文件保全与ZIP读回](runs/L6_STORAGE_BOUNDARY_TIMING_REMEDIATION_20260914/final-preservation-and-archives.json)：entry 4186文件中仅4个获授权既有test源文件改变，其余4182文件原字节不变；包括历史失败raw、manifest及只读analyzer。原有未提交L6 evidence及历史analyzer不随本次暂存。交付使用明确allowlist；最终接受只绑定提交后新HEAD的9/9 CI，CI结果另存本地delivery acceptance，避免用基线CI替代新候选CI。

下一任务仍为从T=0重新执行正式5/20/10 storage calibration；本轮不运行。正式35分钟接受后才具备capacity freeze/preflight的前提；此前继续禁止L6-A 60min和L6-B 180min。
