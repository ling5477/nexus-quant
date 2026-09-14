# L6 动态 PG tmpfs 启动前定容

本次只接受 qualification tooling 的动态定容能力，不执行正式 60 分钟 qualification。L6-A / L6 仍为 NOT_ACCEPTED；L6-B 180 分钟未开始。基线 HEAD 为 `7f2c9431b966cf102e75d6be42014e834450bcdc`，基线 CI `34829072127` 已核验 9/9 SUCCESS。

## 原失败重建

原 run `47ceae97-ab0e-4596-afa3-cd6782d69b6f` 的 raw manifest、archive SHA 和输入文件 SHA 均核验。实际入口 used=48,910,336、free=1,255,518,208 bytes，baseline 恰与 calibration 相等。T+10.4429285s 样本 scheduled=10s，used=49,451,008、free=1,254,977,536 bytes。

剩余 warmup=126,884,303，active=731,429,368，drain=92,890,800，reserve=304,052,400，backlog allowance=0；总需求=1,255,256,871，free−need=`−279,335 bytes`。初始取整余量150,380 bytes，被实际前10秒增长540,672、减去模型已消耗增长110,957后耗尽。

直接根因是库存时间模型在短分配周期内低估了增长突增；固定容量绑定 calibration baseline 是另一个需要关闭的设计限制。此样本不支持 baseline mismatch、测量语义不一致或 baseline 重复计入的解释。旧失败不改判为 PASS。

## 唯一模型与每次运行的派生值

canonical JSON schema 2 冻结原三阶段库存时间系数、600/2400/600秒、422订单、7,117,650,000ns pacing、原长期 reserve、backlog 单价、60%安全上限及来源 SHA。旧1244MiB移到 `calibrationReference.legacyCapacityBytes`，仅保留历史回放解释，不再用于启动。

新增可消耗 allocation burst 为 accepted calibration 全部相邻 periodic 10秒样本的最大正增长：17,473,536 bytes，来源 scheduled T=1270→1280秒。Java loader 从有界、SHA校验的原回放重新推导并验证这个值。它只分配一次，不要求 guard 永久保留；原304,052,400 bytes长期 reserve 继续完整保留。此条件模型不承诺覆盖未观测故障的无条件上界，异常持续增长仍触发 guard。

公式为：

```text
growth = 126995260 + 731429368 + 92890800
capacity(B) = max(256 MiB,
    ceil_to_MiB(B + growth + 304052400 + 17473536))
```

`B` 的生命周期是 PG / Flyway / fixture / seed 完成、Venue ready、两个 NQ actor ready、paper ready、Venue open，尚无订单、尚未开始正式计时。准备实例使用既有256MiB有界fixture和完整组件60%预算，完成该生命周期的真实测量后全部清理。随后依据测量推导容量、核验host预算，启动新的正式PG；容量从该PG启动起不再改变。

正式实例达到同一入口时再次实测 B、重新计算容量、重新观察host可用内存。仅当真实入口推导值、预定容量、实际tmpfs容量三者完全一致时才生成 `run-capacity.json` 并开始计时。两次 baseline 不被假定相等；若落在不同MiB分配区间，在workload前 fail-closed，以新准备/新运行重新开始，禁止原实例扩容或继续。256MiB准备容量本身也有边界，超出时停止该准备路径。

预算为两个NQ heap + Venue heap + controller heap + Maven heap + 原native/tools预算 + PG non-tmpfs预算 + 本次tmpfs，仅计tmpfs一次；`budget * 5 <= available * 3`，等号允许。heap与组件预算未降低。

运行中 guard 比较 current free 与未来三阶段剩余增长、当前backlog allowance和长期reserve；不加入baseline、current used或已消耗增长。ACTIVE采用冻结producer库存上界；DRAIN只使用已有库存。完整可比10分钟窗口可提高保护用增长率。真实不足仍停producer并执行既有有界drain；容量漂移、缺失/过期采样继续拒绝。

## 验证与限制

- 原raw fixture回归精确复现−279,335 bytes；新分配重放通过。不同baseline产生不同容量，同模型/输入结果确定，入口不一致拒绝。
- 相关测试覆盖模型完整回放、来源损坏、burst篡改、60%小于/等于/大于边界、overflow、L5隔离、剩余需求代数、backlog、DRAIN与真实不足。详情见[测试结果](runs/L6_DYNAMIC_PG_TMPFS_20260914/test-summary.json)。
- 真实PG fixture验证default=256MiB与L6容量、实际cgroup限额、读写及cleanup。
- 第二次60秒probe通过：baseline=48,910,336，推导与实际tmpfs均为1,322,254,336 bytes（1261MiB）；完整链oracle为3订单、3成交、12账务，duplicates=0、orphans=0、backlog=0，guard未触发，cleanup PASS / owned survivors=0。入口预算6,690,963,456 <= floor(30,204,514,304×0.60)=18,122,708,582 bytes。
- 首次probe因新准备目录缺少子进程必需parameters而失败，已在准备阶段清理；修复沿用manifest协议。失败和成功均保留于[probe回放](runs/L6_DYNAMIC_PG_TMPFS_20260914/runtime-probes.zip)。之后只增加source burst loader负例校验，相同合法输入的运行行为未变，针对性回归覆盖该补强。
- production delta=0；原manifest、35分钟raw、旧1244MiB freeze及原BLOCKED evidence保留；无正式60分钟运行、无L6-A/L6接受。

代码候选身份见[candidate identity](runs/L6_DYNAMIC_PG_TMPFS_20260914/candidate-identity.json)，独立审查与交付结论由本目录后续记录绑定，不以本地测试替代exact-head CI。

独立审查已通过（[REVIEW_ONLY记录](runs/L6_DYNAMIC_PG_TMPFS_20260914/independent-review.json)）：P0=0、P1=0、P2=0，最终14文件起止指纹相同、HEAD相同、stage=0。原失败数学和来源回放由审查者独立重算；交付CI待新提交后绑定。
