---
name: python-project-development
description: 在真实代码仓库中开发和维护正式 Python package、library、service、CLI、多模块工程、研究/回测/数据工程。用于存在 pyproject.toml 或 package architecture、同时修改 implementation 与 tests、涉及依赖管理、typing、application architecture、async service、persistence/network adapter、packaging/release，或把 research code 提升为可复用生产模块的任务；单一独立脚本、一次性分析、简单数据转换、migration/helper script 和不形成长期维护 package 的小工具改用 python-ops-tooling。
---

# Python Project Development

以仓库事实为准完成最小、完整、可测试的 Python 工程变更。先理解工程和边界，再写代码；不把本 Skill 当作迁移工具或 Python 教程。

## 路由边界

先判定任务是否属于正式工程：

- 存在 `pyproject.toml`、package architecture 或稳定的多模块结构。
- 修改 package/library/service、CLI package、async service、持久化或网络 adapter。
- 同时涉及实现与 tests、dependency management、typing 或 application architecture。
- 修改正式 research/backtest/data pipeline framework，或把 notebook/experiment 提升为复用模块。

命中任一项时继续使用本 Skill。若只是单一独立脚本、一次性任务、临时分析、简单转换、开发辅助或 migration/helper script，停止本 Skill 并路由到 `python-ops-tooling`。不要合并两个职责。

## 执行工作流

### 1. 分类任务

选择最贴近的一类，并用分类决定审查深度、测试范围与风险：

`NEW_PROJECT`、`FEATURE`、`BUG_FIX`、`REFACTOR`、`DEPENDENCY_CHANGE`、`TEST_CHANGE`、`CONFIG_CHANGE`、`PERFORMANCE`、`ASYNC_CONCURRENCY`、`PACKAGING`、`CLI`、`DATA_PIPELINE`、`RESEARCH_TO_PRODUCTION`。

### 2. 检查仓库与工作区

写操作前确认 repository、branch、Git 状态、目标模块、排除范围和用户已有改动。读取仓库级 instructions、目标模块说明与实际存在的工程文件，按需检查：

- `pyproject.toml`、`requirements*.txt`、`uv.lock`、`poetry.lock`、`Pipfile`、`setup.py`、`setup.cfg`；
- package roots、`src/`、`tests/`、README、CONTRIBUTING；
- Makefile、task runner、tox/nox、CI workflow、Dockerfile；
- Python 版本事实：`requires-python`、`.python-version`、`.tool-versions`、Docker image、CI matrix。

只读取存在且与任务有关的文件。识别项目使用 uv、Poetry、pip/pip-tools、PDM、Hatch 或其他方案，并沿用它；不得按个人偏好迁移工具、框架或 layout。

### 3. 建立工程边界

识别受影响 package、公开 API、调用方、测试和依赖方向。按实际架构区分 domain、application/service、infrastructure、API/CLI、configuration、persistence 和 external integration boundary，但不要形式主义地强制 DDD。

对已有工程保持现有合法结构。只有 `NEW_PROJECT` 可默认建议 `pyproject.toml`、`src/` layout、`tests/`、typing 和项目选择的质量工具；Python 版本由需求与运行环境决定。

### 4. 设计最小完整变更

列出目标文件、契约变化、失败模式、测试和验证命令。遵循 `smallest coherent change`：完成当前任务所需的最小完整范围，不做无关 cleanup、全仓格式化、框架替换或顺手重构。邻近问题除非阻塞验收，否则只报告。

涉及复杂工程约束时，先读取 [engineering-guardrails.md](references/engineering-guardrails.md)。涉及测试、构建、发布、新工程或 research-to-production 时，读取 [testing-and-delivery.md](references/testing-and-delivery.md)。

### 5. 实现与测试

- 先修改实现，再添加或更新能证明行为的测试；bug 修复必须有回归场景。
- 遵守既有格式、命名、import、typing、logging、配置、异常和测试习惯。
- 保持 domain logic 与 HTTP/DB/queue/filesystem/subprocess adapter 分离。
- 仅在有明确业务价值时新增依赖、抽象或 exception hierarchy。
- 处理所有资源生命周期；显式检查 timeout、retry、cancellation、backpressure、transaction 和幂等是否适用。
- 不读取、打印或提交 secret；不默认执行生产写入、真实交易或其他外部副作用。

### 6. 验证

优先使用项目官方入口，例如 Makefile、tox、nox、task/just、`uv run`、`poetry run` 或 CI script。不要假设所有项目都有 pytest、mypy 或 Ruff。

按由窄到宽的顺序运行：

1. 目标测试或最小复现；
2. 受影响 package 的 unit/integration/contract/regression tests；
3. 项目已配置的 type checker；
4. 项目已配置的 lint 与 format check；
5. 任务相关的 build/package/CLI smoke gate；
6. 成本合理且仓库要求时运行更广泛 gate。

记录真实命令、退出码和关键输出。验证失败时定位 root cause、做最小修复并重跑；不得删除测试、降低断言、添加无依据 ignore、关闭规则或 skip 来制造通过。若为 baseline failure，记录命令、失败、与本任务无关的证据，不得宣称完整通过。

### 7. 审查 diff 并报告

检查最终 diff 是否仅包含授权范围，确认无敏感信息、生成物、意外 lockfile 手改、无关格式化、危险 fallback 或 import-time side effect。

最终报告使用：

```text
Task classification:
Result: DONE | BLOCKED / <root cause>
Changed files:
What changed:
Tests added/updated:
Validation commands:
Validation result:
Known remaining issues:
Rollback:
```

只有请求已完成且相关验证真实通过时写 `DONE`。存在阻断时写 `BLOCKED / <具体原因>`，不要使用模糊措辞。
