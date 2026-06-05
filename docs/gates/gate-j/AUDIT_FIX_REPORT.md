# AUDIT FIX REPORT

## 1. 结论

- P1：已关闭。旧 OKX dome 验收脚本已移出 `scripts/` 可执行区，原路径只保留阻断 stub。
- E2E 端口问题：已关闭。E2E/Vite 端口已从 `4173` 调整为 `5179`，用于避开 Windows TCP excluded range `4141-4240`；完整 E2E 复跑通过。
- GateJ-FREEZE 判断：建议允许重新进入 GateJ-FREEZE 判断；GateJ-FREEZE 仍必须单独执行 1h / 24h / 7d 连续运行验收，不得夹带新业务功能。

## 2. 修改清单

- `scripts/gated_okx_dome_verify.ps1`：替换为安全阻断 stub，防止误执行旧真实 OKX 验收链。
- `docs/archive/scripts/gated_okx_dome_verify.ps1`：保存旧脚本历史证据，移出可执行 `scripts/` 区域。
- `frontend/playwright.config.ts`：默认 Playwright `baseURL` 和 Vite webServer 端口改为 `5179`。
- `frontend/playwright.config.js`：同步 TypeScript 配置生成物中的端口，避免后续 build 产生额外漂移。
- `frontend/tests/e2e/run-e2e.mjs`：Vite dev server 启动端口改为 `5179`，确保等待地址与实际监听地址一致。
- `frontend/vite.config.ts`：Vite dev / preview 默认端口改为 `5179`，避免配置层仍保留 `4173`。
- `frontend/vite.config.js`：同步 Vite 配置生成物中的端口，避免后续 build 产生额外漂移。
- `frontend/.env.example`：`E2E_BASE_URL` 示例端口改为 `5179`。
- `docs/current/API.md`：确认 `/__gated/**` 只属于历史说明/归档证据，不属于当前可执行 API。
- `docs/current/STATUS.md`：记录 AUDIT-FIX 关闭 P1 与端口问题。
- `docs/current/TESTING.md`：记录 AUDIT-FIX 验证项与端口修复原因。
- `docs/current/WORKLOG.md`：记录 AUDIT-FIX 执行过程、边界和验证结果。

## 3. P1 关闭证据

- 旧脚本已移动到 `docs/archive/scripts/gated_okx_dome_verify.ps1`，不再位于可执行 `scripts/` 区域。
- `scripts/gated_okx_dome_verify.ps1` 当前只保留阻断 stub，并明确：
  - 该脚本已废弃。
  - `/__gated/**` 是历史路径。
  - 当前 GateJ 不允许执行该脚本。
  - 不得用于真实交易验收。
- `docs/current/API.md` 已写明正式 HTTP API 统一使用 `/api/**`，`/__gated/**` 不属于当前可执行 API。

## 4. E2E 端口修复证据

- `frontend/playwright.config.ts`：默认 `baseURL` 与 webServer 端口从 `4173` 改为 `5179`。
- `frontend/tests/e2e/run-e2e.mjs`：Vite 启动端口从 `4173` 改为 `5179`。
- `frontend/vite.config.ts`：Vite dev / preview 默认端口从 `4173` 改为 `5179`。
- `frontend/.env.example`：`E2E_BASE_URL` 从 `http://127.0.0.1:4173` 改为 `http://127.0.0.1:5179`。
- 修复原因：当前 Windows TCP excluded range 覆盖 `4141-4240`，`4173` 无法监听并触发 `EACCES`；`5179` 避开该范围。

## 5. 验证结果

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `git status --short` | 已执行 | 变更范围已确认 |
| `git diff --stat` | 已执行 | 变更规模已确认 |
| `git diff -- scripts/gated_okx_dome_verify.ps1 docs/archive/scripts/gated_okx_dome_verify.ps1 frontend/playwright.config.ts frontend/tests/e2e/run-e2e.mjs docs/current/API.md docs/current/STATUS.md docs/current/TESTING.md docs/current/WORKLOG.md` | 已执行 | 关键 diff 已确认 |
| `mvn -f backend/pom.xml test` | 通过 | Reactor `BUILD SUCCESS`；23 个 module SUCCESS；`nq-app` 35 tests / 0 failures / 0 errors |
| `cd frontend && npm run build` | 通过 | `tsc -b && vite build` 通过；保留既有 chunk > 500 kB 警告 |
| `cd frontend && npm run test:e2e` | 通过 | 首次在后端未启动时失败于 `127.0.0.1:18888 ECONNREFUSED`；启动后端 local profile 后复跑通过，Vite 监听 `127.0.0.1:5179`，结果 24 passed / 1 skipped |

## 6. 剩余风险

- P2：验收 profile 与本地 Docker 配置仍存在开发/验收用途的敏感默认字段，需在后续安全加固中处理。
- P2：前端 token 仍存储在 `localStorage`，XSS 后可读风险未在本轮处理。
- P2：`frontend/package-lock.json` 仍使用 `registry.npmmirror.com`，供应链镜像源信任边界未在本轮处理。
- P3：Vite chunk > 500 kB 体积警告未在本轮处理。
- P3：Mockito 动态 agent 与 SLF4J provider 警告未在本轮处理。
