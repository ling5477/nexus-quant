# Python 测试与交付

根据仓库已配置能力选择最小充分 gate，不假设固定工具链。

## 测试设计

- 先识别业务不变量和 bug 的失败证据，再选择 unit、integration、regression 或 contract test。
- 覆盖与变更有关的 happy path、boundary、error path、state transition、serialization、time、randomness 和 concurrency。
- 用真实 adapter contract 或受控 fake 验证边界；不要用过度 mock 掩盖 SQL、serialization、transaction 或 network contract 问题。
- 测试保持独立、可重复、无执行顺序依赖；禁止访问生产服务或真实账户。

## 确定性

- 注入或冻结 clock、random seed、ID generator、timezone 和外部响应。
- 对 NumPy/随机算法设置相应 seed；固定 input ordering 和 dataset/version identifier。
- 对 float 使用领域合理 tolerance，不用脆弱的逐位相等掩盖或制造误差。
- 禁止用当前时间、共享临时目录、未界定线程调度或不稳定网络制造 flaky test。

## 基于事实选择命令

优先顺序：

1. 仓库 README/CONTRIBUTING 指定入口；
2. Makefile、tox、nox、task、just 或 CI script；
3. dependency manager 的执行入口，如 `uv run`、`poetry run`、PDM/Hatch；
4. 仅当配置存在时直接运行 pytest、type checker、lint、format check。

典型命令仅作已配置项目的候选，不得照抄为事实：

```text
pytest <focused-path>
pytest
mypy .
pyright
ruff check .
ruff format --check .
python -m build
```

先跑 focused test，再扩大到受影响 package 和仓库 gate。依赖/packaging 变更还要验证 lockfile 由官方工具生成、wheel/sdist 可构建、entry point 可启动且安装边界正确。

## 新项目基线

仅对 `NEW_PROJECT` 默认考虑：

- `pyproject.toml` 作为现代 metadata/tool 配置入口；
- `src/` package layout、`tests/`、typing；
- pytest、Ruff 和一个符合需求的 type checker；
- 由部署目标、依赖支持和 CI matrix 决定 Python 版本。

先确认用户需求和运行环境。不得固定 Python 版本、强制 uv/Poetry/PDM、强制 Pydantic/async/Web framework，或为小项目堆叠工具。

## Research to production

- 区分 `research/`、`experiments/`、notebooks、backtest 与 production 的可靠性等级。
- 进入 shared library、production pipeline、service 或 live-trading boundary 前，移除 hidden state，显式化输入与 assumption，补 validation、typing、tests、logging 和 error handling。
- Notebook 保留 orchestration、visualization、analysis 与 experimentation；需要复用、测试、生产或回测调用的逻辑提取到正式 module。
- 对数据版本、时区、随机性、look-ahead bias、输入顺序和数值容差建立可复现证据。

## 验证失败与交付

- 失败时记录 command、exit code、关键错误和 root cause；修复后重跑相同 gate。
- 不删除失败测试、不降低 assertion、不新增无依据 ignore、不关闭 type/lint rule、不随意 skip。
- 若失败为 pre-existing baseline，提供与本次 diff 无关的证据并明确未通过范围。
- 完成前审查 `git diff`、`git status --short`、新增/删除文件、敏感信息、生成物和 lockfile 来源。
- 报告测试新增/更新、所有执行命令、通过/失败数量、未验证项、剩余风险与回滚方式。
