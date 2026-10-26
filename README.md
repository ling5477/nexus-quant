# NexusQuant

NexusQuant 是面向 OKX Spot 的研究与模拟交易工作台。Java Control Plane 统一管理风险、订单、成交与账务事实，正式 UI 贯通策略验证、发布、Continuous SIM 与运行监控。

## 发行状态

[v1.0.0](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.0) 与 [v1.0.1](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.1) 已发布；当前开发进入 v1.0.2 的研究数据集产品化。在 Self-Hosted 正式行情页面，可选择 OKX Spot BTC/ETH、1h、最多30个完整UTC日，接入并检查质量后绑定现有策略版本回测。研究输入保存完整冻结序列与摘要，公开规则使用当前观察值，历史可见性、费用及滑点均明确标注假设。当前任务不授予新版发布或真实交易权限。

已发布 Tag、Snapshot、包与签名证据保持冻结。历史 C4 审查仍为 `NOT_ACCEPTED / EVIDENCE_GAPS`，owner 例外决定与72小时重跑豁免保留；`UPGRADE_NOT_VERIFIED` 不因本轮功能实现被改判。

普通账户缺可信权益来源时不显示虚构收益；真实手续费必须有可信来源；OHLC 不能提供真实事件发生时点；旧 Paper 未知币种权益不改写。

## V1.0 范围

- OKX Spot 公开行情、数据集与策略研究/回测。
- Continuous SIM、Paper matching、风险校验及 Order / Trade / Ledger / Position。
- 策略发布、调度、运行观察与监控。
- Runtime 资源配置、首次登录强制改密、持久化令牌失效、本地备份恢复与用户确认后的更新/回滚。

不包含自动 LIVE 执行、期货/杠杆、Binance 生产支持、AI 交易或 HFT。Python research 是隔离的开发研究工具，不加入产品 runtime Compose。

## 架构与技术栈

浏览器 → React / Vite / Ant Design → Java 21 / Spring Boot / Maven 多模块单体 → PostgreSQL 16 / Flyway。Python 研究域不拥有交易写入权限。

## Quick Start

版本统一读取根目录 [VERSION](VERSION)。取得对应架构的官方预构建 release package，解压后运行：

V1.0.0 唯一正式支持合同为 **Windows 11 amd64，Docker Desktop 已安装且 daemon 正在运行（Linux containers）**。本合同只声明该宿主范围；预发布技术验收仍须完成对应候选的真实经济链、重启和备份恢复证明。

```powershell
# Windows 11 amd64 / 已运行的 Docker Desktop
.\installers\install.ps1
```

其他安装入口保留为 `AVAILABLE_BUT_UNQUALIFIED`，本版本不声明正式资格：

```sh
# macOS / Docker Desktop
sh installers/install-macos.sh
# Linux / Docker Engine + Compose v2
sh installers/install-linux.sh
```

打开 <http://127.0.0.1:18080>，使用 **admin / 123456** 登录。首次登录只能查看自己的认证资料、修改密码或退出；新密码至少 8 个字符，不能使用当前密码或 `123456`。改密成功后重新登录，所有旧令牌立即失效，重启后仍失效。

正式支持合同要求先安装并运行 Docker Desktop；安装器复用 Docker 并加载经 manifest 校验的 immutable 镜像，用户无需 Java、Maven、Node、PostgreSQL 或 Python，也不执行源码编译。Docker 缺失时的自动安装、daemon recovery 和 Linux/macOS 安装器保留为 `AVAILABLE_BUT_UNQUALIFIED`，不属于 V1.0.0 正式支持合同。更新默认关闭，`check-update` 只提示，实际更新需用户选择。冻结矩阵详见 [INSTALL.md](INSTALL.md)；公开发行状态以最终 Release qualification / current release authority 为准。

## 安全默认值

SIM 可用；public OKX marketdata 允许访问。LIVE、真实交易所写入、真实 provider/client、私有 OKX diagnostics、transfer/withdraw 均关闭；kill switch 初始为 ENGAGED。内部数据库密码、JWT 密钥和凭据加密密钥随机生成，服务只绑定本机地址。

安装、运维、数据目录、版本兼容和排障详见 [INSTALL.md](INSTALL.md)。产品变化见 [CHANGELOG.md](CHANGELOG.md)。

## License

NexusQuant is licensed under the Apache License 2.0 (SPDX: `Apache-2.0`).
See [LICENSE](LICENSE).
Third-party attribution: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Financial Risk

NexusQuant is quantitative trading software and does not provide investment advice.
Before using any real-money functionality, read [DISCLAIMER.md](DISCLAIMER.md).
