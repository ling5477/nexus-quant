# Testing

本文记录统一验证命令和当前基线验证结果。未执行的验证不能写成通过。

## 统一验证命令

### 后端验证

```powershell
mvn -f backend/pom.xml test
```

### 前端验证

```powershell
Set-Location frontend
npm ci
npm run build
npm run test:e2e
```

### Python 验证

首次本地验证前安装 dev 依赖：

```powershell
Set-Location research/py
python -m pip install -e ".[dev]"
```

```powershell
Set-Location research/py
python -m pytest -q
python -m mypy src
python -m ruff check .
```

### 本地启动验证

```powershell
docker compose up -d postgres
```

启动 `nq-app` local profile 后检查：

```powershell
Invoke-RestMethod http://localhost:18888/actuator/health
```

并检查：

- `POST /api/auth/login`
- `GET /api/auth/me`

## 本地 PostgreSQL 规则

- 本地 PostgreSQL 默认端口是 `5432`。
- 使用本机 PostgreSQL 时，不重复启动 `docker-compose postgres`。
- 使用 `docker-compose postgres` 时，确认本机 `5432` 未被占用。

## 本次实际验证记录

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 工作区包含本次 docs/config 修改与 `git mv` 归档，详见 `WORKLOG.md` |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`，23 个 backend module 均为 `SUCCESS`；local integration 日志确认连接 `jdbc:postgresql://localhost:5432/nexus_quant` |
| `npm ci` | 通过 | 首次因 `D:\Tool\NodeJs\node_cache` 写入权限/占用失败；提权重跑后成功安装 177 packages；`npm audit` 提示 4 个漏洞（2 moderate、2 high），本任务未执行 `npm audit fix` |
| `npm run build` | 通过 | `tsc -b && vite build` 成功；Vite 提示 bundle chunk 超过 500 kB，属于既有构建体积风险 |
| `npm run test:e2e` | 通过 | BASELINE-FIX-2 后通过；8 个 Playwright 用例中 5 passed、3 skipped。E2E runner 会启动 Vite、设置外部 dev server 模式、运行 Playwright、最后停止 Vite |
| `python -m pip install -e ".[dev]"` | 未在当前环境完成 | 已在 `pyproject.toml` 补充 dev extras；当前本机 editable install 两次卡在 build/editable 阶段超时。为完成当前验证，使用等价工具安装命令补齐当前用户环境 |
| `python -m pip install pytest mypy ruff` | 通过 | 提权执行成功；下载较慢并发生断点续传，最终安装 `pytest-9.0.3`、`mypy-2.1.0`、`ruff-0.15.13` |
| `python -m pytest -q` | 通过 | `2 passed in 0.01s` |
| `python -m mypy src` | 通过 | `Success: no issues found in 8 source files` |
| `python -m ruff check .` | 通过 | `All checks passed!` |
| 本地启动验证 | 通过 | `mvn -f backend/pom.xml -pl nq-app -am spring-boot:run -Dspring-boot.run.profiles=local` 启动成功；`/actuator/health` 返回 `UP`；`POST /api/auth/login` 和 `GET /api/auth/me` 成功，当前默认账户恢复为 `rc1-admin-default / 900001` |

## 当前剩余风险

- 未执行 `docker compose up -d postgres`：当前本机已有 PostgreSQL `5432` 可用，后端测试和 local profile 均已连接该实例。
- `npm audit` 仍提示 4 个漏洞（2 moderate、2 high），后续单独处理。
- Vite build 仍提示 chunk 超过 500 kB，后续单独处理。
- E2E 中 3 个详情/交易链路用例按当前环境数据条件 skip，不代表对应业务链路已完整验证。
