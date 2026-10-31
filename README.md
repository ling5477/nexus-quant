# NexusQuant

NexusQuant 是面向 OKX Spot 的研究与模拟交易工作台。Java Control Plane 是唯一 canonical Risk / Order / Trade / Ledger 权威。

## 当前版本与发行范围

[NexusQuant v1.0.3](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.3)已于2026-10-31T06:00:01Z正式发布，Release ID429746750、Schema V52；v1.0.0、v1.0.1、v1.0.2历史发行保持不变。

产品由 **NexusQuant Self-Hosted + NexusQuant Python Research Companion** 两个独立组件交付。正式验证范围为Windows 11 amd64 / 已运行Docker Desktop Linux Containers / PostgreSQL16（本包16.15）/ V52；Companion为Windows amd64 / CPython3.11 x64 / NumPy2.3.5独立离线安装。Linux/macOS/ARM64、Public Demo及其他Python版本未取得本版正式资格。

正式Qualified Source Commit为5f770f60e2b6f11b376535b865a60ef5976db3c1，Source CI41078509409为9/9成功；独立无父Snapshot为29677025572536bcab0ce2bf77ac46fe766cf844，Tree为294085f526528afb780d4ee152f1abc0333d732a，Annotated Tag Object为03da66b14beeda90152758d06e9d215431407696。[完整发行身份及六资产](docs/current/STATUS.md)与[未签名provenance](https://github.com/ling5477/nexus-quant/releases/download/v1.0.3/NexusQuant-1.0.3-provenance.json)分别记录来源与制品；发行后文档commit/CI不替代正式Source，Snapshot CI=NOT_RUN。

## v1.0.3 功能

- Historical Research：OKX SPOT BTC/ETH 1h，1～180完整UTC日、最多4320根；每次显式接入最多3000根，PARTIAL后手工续拉。旧V1最多720根保持兼容；长输入为不可变Dataset V2分块及引用。原正式回测、实验及90/180日Walk-Forward预设消费同一冻结版本；长实验最多4候选，不扩大SIM准入。
- Forward SIM Evaluation：原canonical分析入口上的UTC/asOf观察窗口、覆盖/样本状态、期间绩效和风险/执行解释，追加人工复盘历史。停止观察只结束研究记录，不暂停SIM、撤单或平仓。原Order/Trade/Ledger、Legacy409和Continuous SIM执行保持。
- Python Factor Research：V2只读导出、离线Momentum/Realized Volatility/Volume Anomaly、purged时间切分及有界块Bootstrap、独立Artifact与原研究页只读预览。Java验证身份/摘要/格式，不重新计算统计、不持久化本地文件、不授予交易权限。

180日不代表多年历史或长期稳健性；回放availableAt是假设，源实验已见最终留出。Forward合成时间测试不是90日真实观察。因子相关与Bootstrap不证明长期盈利或可交易优势。

## 安装与升级

从[v1.0.3 Release](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.3)取得NexusQuant-1.0.3-windows-amd64.zip并核验可信摘要；完整解压后内部目录仍为NexusQuant-1.0.3-rc2-windows-amd64。在该目录运行：

```powershell
.\installers\install.ps1
```

普通Java安装/运维无需宿主Java、Maven、Node、PostgreSQL或Python。默认UI为 <http://127.0.0.1:18080>；首次admin/123456登录必须改密，服务默认仅绑定本机。

已验证普通更新仅为 **v1.0.2/V49 → v1.0.3/V52**，要求可信目标Manifest、版本递增、Schema单调及同一可信PostgreSQL config digest。原正式安装器负责停写、备份、正向迁移、回执及失败恢复；不能仅换旧镜像连接新库，不使用Flyway repair。不声明v1.0.0或其他未经验证的直接升级路径。

Python Companion必须另行下载NexusQuant-Python-Research-Companion-1.0.3-windows-amd64-py311.zip，用户自行准备CPython3.11 x64并创建venv，按其中INSTALL与hash锁使用 `--no-index` 从附带wheelhouse离线安装。Wheel为 `nexus-quant-research==1.0.3`，NumPy固定2.3.5。用户显式运行CLI，然后在原research页选择本地Artifact。Java安装器、Compose和服务不安装、启动或执行Python。

安装、运维和可信摘要要求见 [INSTALL.md](INSTALL.md)，产品历史见 [CHANGELOG.md](CHANGELOG.md)。版本读取 [VERSION](VERSION)。已发布Tag、Snapshot、包和证据保持原字节。

## 安全与限制

LIVE=DISABLED，Kill Switch=ENGAGED；真实资金/真实交易所写入、private diagnostics、transfer/withdraw、真实provider/client、自动LIVE、AI交易、期货/杠杆和HFT关闭。Python不持有交易或数据库凭据。缺可信收益、费用、事件时点或未知币种权益时不补造经济事实。

原whole-run FAILED、update exit1、两表raw DIFFERENT保留；后续恢复仅历史数据与稳定控制QUALIFIED，不代表全部raw或物理库一致。历史长时运行原审查NOT_ACCEPTED/EVIDENCE_GAPS、所有者例外与72小时重跑豁免分开保留。Linux既有低内存短时SCOPED_PASS不是正式运行资格或长期稳定性。低于4GiB继续为警告/LIGHT，不新增硬最低门槛或自动Swap。

## License and financial risk

NexusQuant is licensed under Apache-2.0. See [LICENSE](LICENSE), [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) and [DISCLAIMER.md](DISCLAIMER.md). 研究结果不是投资建议或盈利承诺。
