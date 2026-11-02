# NexusQuant

NexusQuant是面向OKX Spot的研究与模拟交易工作台。Java Control Plane是唯一canonical Risk / Order / Trade / Ledger权威。

## v1.0.4正式发行

[NexusQuant v1.0.4](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.4)已于2026-11-02T12:37:58Z正式公开（Release ID431438991，非draft、非prerelease），Schema V52。v1.0.0～v1.0.4均RELEASED。固定Qualified Source=f5a24b5d9ac5c89dc7fbf6a53361f5d2b1a31bd0；本次或未来文档提交不替换它，不从最新dev重新构包冒充已发布资产。

产品由 **NexusQuant Self-Hosted 1.0.4 + NexusQuant Python Research Companion 1.0.4** 两个独立组件组成。正式范围为Windows11 amd64 / 已运行Docker Desktop Linux Containers / PostgreSQL16.15 / V52；Companion为Windows amd64 / CPython3.11 x64（实际3.11.9）/ NumPy2.3.5。其他平台、Python及Public Demo未取得本版资格。准确Source/Tree/CI、无父Snapshot/Annotated Tag、六项资产大小/SHA256及保留失败见[正式发行说明](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.4)。来源映射UNSIGNED_RELEASE_SOURCE_MAPPING不是数字签名。

## 本版功能

- 真实公开行情自然时间首用：既有SMA/研究版本→独立公开种子→Continuous SIM→FORWARD人工复盘；原[2026-10-31T09:00:00Z,2026-11-01T09:00:00Z)完成24/24自然小时，游标、一次真实进程重启后推进及Canonical事实一致。仅限实际窗口与记录owner，不代表长期稳定或盈利。
- Forward SIM事件诊断：原目标仍NOT_COMPARABLE / OBSERVATION_BOUNDARY_MARK_UNAVAILABLE；显式查看已保存事件子区间，沿原canonical GET保留精确asOf。子区间结果不替代原目标收益，不覆盖历史复盘，不新增执行权。
- 可复现离线研究交接：原research页提供身份明确下载名、独立目录、完整参数和固定frozenAt命令；未知时间不伪造，已有输出拒绝覆盖。用户手工运行正式Companion并带回Artifact只读预览，身份/会话切换清除本地报告。

原长历史研究继续支持OKX SPOT BTC/ETH 1h、最多4320根、不可变V2、原回测/有界实验与Walk-Forward；Continuous SIM720/500及Legacy409边界不变。因子仍为Momentum、Realized Volatility、Volume Anomaly，purged时序验证与moving-block Bootstrap；数学及Artifact版本不随产品名改变。

## 安装与研究

从上述正式Release取得完整Self-Hosted ZIP、独立Companion ZIP及可信摘要；六资产已完成发布前认证与发布后匿名完整下载校验。GitHub自动源码包不能替代镜像安装包。Self-Hosted在解压包根执行：

```powershell
.\installers\install.ps1
```

普通安装和运维无需宿主Java、Maven、Node、PostgreSQL或Python。默认仅loopback，首次管理员登录须改密。已验证普通升级仅v1.0.3/V52→v1.0.4/V52，使用原check-update/update、可信Manifest和相同PG immutable image/config；本版无新migration。

Companion独立ZIP包含nexus-quant-research==1.0.4及NumPy2.3.5 wheelhouse/hash-lock。用户自行准备CPython3.11 x64并创建干净venv，按包内INSTALL以--no-index离线安装；不需要源码checkout。固定输入/参数/seed/生成时间/数值运行时才能比较完整Artifact字节。Java只核验owner、冻结身份、格式、摘要，不认证科学结论，不保存Artifact，不从Java启动Python。

详见[INSTALL.md](INSTALL.md)、[CHANGELOG.md](CHANGELOG.md)、[VERSION](VERSION)。

## 安全与保留限制

LIVE=DISABLED，Kill Switch=ENGAGED。无真实资金/私有交易provider、真实PLACE/CANCEL、transfer/withdraw、AI交易或Python交易权限。STOP_OBSERVATION只结束研究观察，PAUSE Continuous SIM也不自动平仓。

原24h目标缺锚和null指标保持；短样本、未平仓估值、回放availableAt假设、已暴露留出及Bootstrap假设不支持长期盈利推断。PR101首次CI估值负例状态断言失败根因仍UNRESOLVED；同候选重跑成功不代表修复，固定Source/merge CI及包级证据仅支持已验证发行范围非阻断。原两轮取证driver FAILED、故障update exit1和两运行表raw DIFFERENT保留，恢复仅历史数据与稳定控制QUALIFIED。历史长时运行原NOT_ACCEPTED/EVIDENCE_GAPS、所有者例外、72小时豁免及v1.0.3恢复原FAILED/exit1/raw DIFFERENT分别保留。Linux/macOS/ARM64、其他Python及Public Demo未取得本版正式资格。

## License and financial risk

Apache-2.0。见[LICENSE](LICENSE)、[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)和[DISCLAIMER.md](DISCLAIMER.md)。研究结果不是投资建议或盈利承诺。
