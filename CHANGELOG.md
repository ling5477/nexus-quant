# 1.0.3 — 已发布 / Released

Release Date: 2026-10-31 (UTC)

Release URL: https://github.com/ling5477/nexus-quant/releases/tag/v1.0.3

Release ID429746750，published_at=2026-10-31T06:00:01Z，draft=false/prerelease=false。三个功能阶段及限定范围最终准入均完成；Windows11 amd64/Docker Linux Containers/PG16.15/V52 Self-Hosted与独立Windows CPython3.11/NumPy2.3.5 Companion已正式交付。

正式Source=5f770f60e2b6f11b376535b865a60ef5976db3c1 / Source CI41078509409（9/9）；无父Snapshot=29677025572536bcab0ce2bf77ac46fe766cf844 / Tree294085f526528afb780d4ee152f1abc0333d732a；Annotated Tag Object=03da66b14beeda90152758d06e9d215431407696。六资产与来源映射见[正式发行说明](https://github.com/ling5477/nexus-quant/releases/tag/v1.0.3)，发行后文档CI单独记录，来源映射未签名。

## Features

- Historical Research：OKX SPOT BTC/ETH 1h、1～180完整UTC日/4320根，V2不可变分块与引用，旧V1冻结兼容；长回测/最多4候选实验及90/180日WF预设，不扩大Continuous SIM720/500边界。
- Forward SIM Evaluation：原canonical UTC/asOf观察、覆盖与逐项样本/期间指标、风险执行解释和追加人工复盘；STOP仅关闭研究记录。
- Python Factor Research：原V2只读导出、Momentum/Realized Volatility/Volume Anomaly、purged时序验证/块Bootstrap、离线Artifact与原research页只读预览，不持久化本地产物。
- 独立Python Research Companion1.0.3：已验证Windows amd64/CPython3.11 x64，Wheel和NumPy2.3.5依赖随包，以hash锁和--no-index离线安装；不进入Java/Compose运行时。

## Upgrade and limitations

- 已验证正式v1.0.2/V49→V50→V51→V52普通升级、新安装、备份恢复及主动回滚；历史migration和PG immutable image/config合同不变。原整轮FAILED、原update exit1、两表raw DIFFERENT保留，后续自动恢复仅QUALIFIED_FOR_HISTORICAL_DATA_AND_STABLE_CONTROLS，不宣称全部raw MATCH；旧数据库与绑定资产一起恢复。
- LIVE=DISABLED、Kill Switch=ENGAGED；无真实交易或Python交易权限。180日不证明多年历史能力；回放时间、已暴露留出、短样本与Bootstrap假设披露；合成时间不代表长期真实观察。
- Linux/macOS/ARM64、其他Python版本、Public Demo不因构包获得资格。历史长时运行原审查及owner例外/72小时豁免保留，v1.0.2发行历史不改写。

# 1.0.2 — 已发布 / Released

Release Date: 2026-10-29 (UTC)

Release URL: https://github.com/ling5477/nexus-quant/releases/tag/v1.0.2

以下候选准备说明保留其构包时点；最终Windows资格与限制以该版本Release Notes为准。

## Features

- Dataset：OKX Spot BTC/ETH 1h 历史接入、最多30个完整UTC日的有界续传、质量检查与内容冻结，正式回测消费同一输入身份。
- Strategy Version：保留单均线目标敞口，增加收盘确认的现货通道突破；默认20/10根，排除当前bar，HOLD不再平衡。
- Experiment：沿用正式版本、回测、评价和调度器，持久保存有界参数候选、尝试历史、失败、显式重试、取消及只读比较。
- Walk-Forward：时间顺序训练选优、三折独立FLAT测试、最终留出、正式执行器成本压力重算和参数稳定性说明；已观察样本及短窗口限制明确披露。
- Comparison：只读关联训练/OOS/留出、正式发布和真实Continuous SIM；沿用原绩效与Canonical事实，权限、冻结身份和不可比原因由服务端核验。

## Breaking Changes

- Schema V47→V48→V49 仅正向迁移。恢复旧应用必须恢复更新前完整数据库及绑定资产，不能让旧应用直接接管V49。

## Known Issues

- 仅准备 NQ_SELF_HOSTED / Windows 11 amd64 / Docker Desktop Linux Containers / PostgreSQL 16；新候选安装、正式包升级及包级恢复尚未执行。
- 普通v1.0.1更新要求可信目标Manifest、版本递增、Schema单调及同一PG16 config digest；实际包仍待准确提交与不可变导出后验证。
- 不支持声明v1.0.0直接升级；Public Demo公网、Linux/macOS、ARM64、真实交易和自动策略发布均不在本候选范围。
- LIVE关闭；研究收益不代表盈利或长期稳健性。缺失的经济事实保持UNAVAILABLE，不补造零值。

# 1.0.1 — 已发布 / Released

Release Date: 2026-10-25 (UTC)

Release URL: https://github.com/ling5477/nexus-quant/releases/tag/v1.0.1

正式发行物为 Self-Hosted，Windows 11 amd64 / Docker Desktop Linux Containers / PostgreSQL 16.15 / V47。原始资格、不可变来源与 Bridge 限制以该 Release Notes 为准；Public Demo 未包含在发行中。v1.0.0→v1.0.1 需要独立可信实例授权的 Legacy Upgrade Bridge，不能直接普通更新。历史资格不自动赋予 v1.0.2。

## Features

- 多租户隔离、受限数据库运行角色、Canonical SIM 事实保护与版本/更新说明。
- Demo 注册、邮箱验证、租户初始化及平台管理属于独立开发能力，尚未取得公网部署资格。

## Known Issues

- LIVE 禁用；真实权益、手续费、OHLC 事件时点及旧 Paper 未知币种权益限制保留。
- 历史长时运行观察原审查为 NOT_ACCEPTED / EVIDENCE_GAPS，所有者例外和72小时重跑豁免独立保留。

# 1.0.0 — 已发布 / Released

Release Date: 2026-10-20 (UTC)

Release URL: https://github.com/ling5477/nexus-quant/releases/tag/v1.0.0

首次产品版本，已正式发布（非 draft、非 prerelease）。

## Features

- OKX Spot 公开行情、数据集、策略研究、回测与发布。
- Continuous SIM、Paper matching、风险、订单/成交/账务及运行监控。
- Docker / PostgreSQL 16 产品运行时与 Windows、macOS、Linux 安装入口。
- 首次管理员创建、强制改密、持久化认证代际及旧令牌失效。
- Docker 官方发行 bootstrap、daemon 启动等待、host preflight 与确定性 Runtime 资源档位。
- 本地启动、停止、重启、status/doctor、备份、完整数据库恢复与保留数据卸载；purge 需要二次确认。
- 可信 manifest/digest 检查、只提示的 check-update、用户确认更新、失败恢复、已知 receipt 回滚；不执行自动更新。
- LIVE 自动执行、期货/杠杆、Binance 生产、AI 交易、HFT 不在本版本范围内。

许可证已确定为 Apache-2.0，参见 LICENSE；第三方声明和双语风险声明保留。

Windows 11 amd64 / 已运行 Docker Desktop 的隔离全新安装、PG16.15/V34、备份恢复及恢复后重启已验证。升级仍为 `UPGRADE_NOT_VERIFIED`；长时运行观察 已完成72小时观察，原审查因定点 UI 证据缺失未接受；用户已作 `ACCEPT_WITH_EVIDENCE_EXCEPTION` 决定并豁免72小时重跑，原结论保留。历史UI缺口按 `ADMITTED_WITH_DOCUMENTED_EXCEPTION` 单列准入，当前运行差异另经定向验证覆盖；新包使用新源码身份和摘要。公开正式发布已完成，其他平台仍未取得正式资格。

保留普通账户收益来源、真实手续费来源、OHLC 实际事件时点和旧 Paper 未知币种权益四项限制。LIVE 仍禁用。

## Improvements

- Docker daemon 启动等待、host preflight 和明确的 Runtime 资源档位。

## Bug Fixes

- 首次改密、持久化认证代际与旧令牌失效保护。

## Breaking Changes

- 首次产品版本，无产品版本间升级声明。

## Known Issues

- 其他平台尚未取得正式资格；LIVE 禁用。上述外部数据与历史观察限制保留。
