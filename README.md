# NexusQuant

NexusQuant是面向OKX Spot的研究与模拟交易工作台。Java Control Plane是唯一canonical Risk / Order / Trade / Ledger权威。

## v1.0.4候选与发行状态

当前开发目标为1.0.4 / Schema V52，功能收口已接受，源码及包级发行准备进行中。最新正式发行仍为[v1.0.3](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.3)，其Source、Snapshot、Tag及资产不变。1.0.4尚未发布，不能将候选构建或功能CI等同正式发行/部署授权。准确候选资格由绑定其来源和制品摘要的发行记录表达。

产品继续由 **NexusQuant Self-Hosted 1.0.4 + NexusQuant Python Research Companion 1.0.4** 两个独立组件组成。候选验证目标为Windows11 amd64 / 已运行Docker Desktop Linux Containers / PostgreSQL16 / V52；Companion为Windows amd64 / CPython3.11 x64 / NumPy2.3.5。其他平台、Python及Public Demo不据构建结果取得资格。

## 本版功能

- 真实公开行情自然时间首用：既有SMA/研究版本→独立公开种子→Continuous SIM→FORWARD人工复盘；原[2026-10-31T09:00:00Z,2026-11-01T09:00:00Z)完成24/24自然小时，游标、一次真实进程重启后推进及Canonical事实一致。仅限实际窗口与记录owner，不代表长期稳定或盈利。
- Forward SIM事件诊断：原目标仍NOT_COMPARABLE / OBSERVATION_BOUNDARY_MARK_UNAVAILABLE；显式查看已保存事件子区间，沿原canonical GET保留精确asOf。子区间结果不替代原目标收益，不覆盖历史复盘，不新增执行权。
- 可复现离线研究交接：原research页提供身份明确下载名、独立目录、完整参数和固定frozenAt命令；未知时间不伪造，已有输出拒绝覆盖。用户手工运行正式Companion并带回Artifact只读预览，身份/会话切换清除本地报告。

原长历史研究继续支持OKX SPOT BTC/ETH 1h、最多4320根、不可变V2、原回测/有界实验与Walk-Forward；Continuous SIM720/500及Legacy409边界不变。因子仍为Momentum、Realized Volatility、Volume Anomaly，purged时序验证与moving-block Bootstrap；数学及Artifact版本不随产品名改变。

## 安装与研究

正式安装必须取得可信发行渠道的对应完整ZIP及独立可信摘要，不能用GitHub自动源码包替代镜像安装包；1.0.4尚无正式下载资产。通过资格且获发布授权后，Self-Hosted在解压包根执行：

```powershell
.\installers\install.ps1
```

普通安装和运维无需宿主Java、Maven、Node、PostgreSQL或Python。默认仅loopback，首次管理员登录须改密。候选普通升级目标为v1.0.3/V52→v1.0.4/V52，经正式check-update/update、可信Manifest和相同PG immutable config；其实际资格不能从旧版本记录推定。

Companion独立ZIP包含nexus-quant-research==1.0.4及NumPy2.3.5 wheelhouse/hash-lock。用户自行准备CPython3.11 x64并创建干净venv，按包内INSTALL以--no-index离线安装；不需要源码checkout。固定输入/参数/seed/生成时间/数值运行时才能比较完整Artifact字节。Java只核验owner、冻结身份、格式、摘要，不认证科学结论，不保存Artifact，不从Java启动Python。

详见[INSTALL.md](INSTALL.md)、[CHANGELOG.md](CHANGELOG.md)、[VERSION](VERSION)。

## 安全与保留限制

LIVE=DISABLED，Kill Switch=ENGAGED。无真实资金/私有交易provider、真实PLACE/CANCEL、transfer/withdraw、AI交易或Python交易权限。STOP_OBSERVATION只结束研究观察，PAUSE Continuous SIM也不自动平仓。

原24h目标缺锚和null指标保持；短样本、未平仓估值、回放availableAt假设、已暴露留出及Bootstrap假设不支持长期盈利推断。PR101首次CI估值负例状态断言失败根因仍UNRESOLVED；同候选重跑成功不代表修复，发行影响由新候选证据限定。历史长时运行原NOT_ACCEPTED/EVIDENCE_GAPS、所有者例外、72小时豁免及v1.0.3恢复原FAILED/exit1/raw DIFFERENT分别保留。Linux/macOS/ARM64、其他Python及Public Demo未取得本版正式资格。

## License and financial risk

Apache-2.0。见[LICENSE](LICENSE)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)和[DISCLAIMER.md](DISCLAIMER.md)。研究结果不是投资建议或盈利承诺。
