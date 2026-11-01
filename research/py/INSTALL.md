# NexusQuant Python Research Companion 1.0.3

此独立资产对应 NexusQuant Self-Hosted 1.0.3；Wheel 的 distribution 为 `nexus-quant-research==1.0.3`。候选资格以绑定此资产 SHA256 的发行资格报告为准。最小验证目标为 Windows 11 amd64 / CPython 3.11 x64；其他版本、Linux/macOS/ARM64 不据 `requires-python>=3.11` 声明正式资格。

先从可信发行渠道取得整个 Companion ZIP 及其可信 SHA256，核验后解压。`SHA256SUMS.txt` 和同包摘要只能检测损坏，不能独立证明发行者身份。`companion-manifest.json` 与 `source.json` 分别绑定 Wheel/依赖和准确开发 Commit 的独立 Git Blob 投影；映射为未签名来源说明。

自行从可信渠道预先安装 CPython 3.11 x64。此资产不安装 Python，不修改系统 PATH，不启动服务。创建不继承全局包的独立环境，再从本包离线安装：

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\python.exe -m pip --isolated install --no-index --no-cache-dir --only-binary=:all: --find-links .\wheelhouse --require-hashes -r .\requirements.lock
.\.venv\Scripts\python.exe -m pip check
```

运行依赖固定 NumPy 2.3.5，完整依赖 Wheel 与 hash 随包提供；无需全局 NumPy、包缓存、editable 源码安装或外部包索引。NumPy Wheel 自带其及所包含数值库的许可证，详见该 Wheel 的 dist-info/LICENSE.txt；项目法律说明为本目录 LICENSE、DISCLAIMER.md 与 THIRD_PARTY_NOTICES.md。源码和构建工具版本保存在 source/ 与 dependencies.json，不是运行时需联网安装的内容。

在 NQ 原 research 页选择自己的已冻结 V2 Dataset。支持 OKX SPOT BTC/ETH 1h、721～4320根，导出最多8MiB。目录可选不代表已冻结 V2；未冻结或 V1 输入先沿原研究/回测配置流程显式准备，不由下载自动冻结或补采。

成功下载后，页面按原 Dataset/Version 显示专属文件名、研究子目录和命令。在 Companion 根目录新建该研究子目录，将下载输入移入，再从 Companion 根目录的 PowerShell 复制执行页面命令。不要从源码 checkout 安装正式运行环境；复用上面已安装的独立 `.venv`。

以下仅为手工路径示例，实际文件名和固定 UTC 时间使用页面所示原导出身份：

```powershell
.\.venv\Scripts\python.exe -m nq_research --frozen-input .\my-research\frozen-input.json --factor-output .\my-research\factor-repeat-a.json --factor-window 24 --horizon-bars 6 --seed 42 --bootstrap-repetitions 200 --created-at '<替换为原导出frozenAt或不早于它的固定UTC时间>'
```

也可执行 `.\.venv\Scripts\nq-research.exe` 加相同参数。页面从真实 frozenAt 建议固定时间，不在未知时伪造；需保留该时间的完整精度。省略 `--created-at` 时统计仍确定，但生成时间、Artifact ID/checksum 会变化。固定输入/参数/seed/时间和 NumPy 运行时后重复到另一个输出名，并比较完整文件 SHA256。不同 Dataset/Version 分目录；CLI 仍可覆盖已有输出，页面示例先用 Test-Path 拒绝覆盖，不能把相同输出名当成另一份独立研究。

保留输入、参数、固定时间、数值运行时、manifest 和 Artifact。随后在同一 NQ 页面选择本地 Artifact，Java 验证身份/摘要/格式并只读预览，不持久化该文件。请人工在研究材料中记录原观察/版本、Artifact ID/checksum、本地路径和局限；不会建立自动策略关联。

合同为 frozen-research-export.v1 → factor-research-1.0.0 / 三因子版本1.0.0 / PURGED_CHRONOLOGICAL_PEARSON_MOVING_BLOCK_V1 → python-factor-research-artifact.v1；产品/Wheel 版本更新不改变这些数学合同。source/research/py/fixtures 的共享721根向量是合成受控兼容样本，不是用户真实市场研究。

CLI 仅处理本地文件，无网络、数据库、凭据、Java subprocess 或交易权限。浏览器认证下载/预览仍需要访问自己的 Self-Hosted 服务；Java 安装和原研究/SIM能力不需要安装 Companion。统计仅为探索诊断，不证明长期盈利或可交易优势；回放可用时间是假设，Java摘要校验不认证科学结论。LIVE=DISABLED，Kill Switch=ENGAGED。
