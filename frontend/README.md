# Frontend（Console Baseline）

本目录承载正式前端控制台基线，当前已包含：

- React 19 + TypeScript + Vite 8 工程初始化
- 登录页、token 持久化、`/api/auth/me` 恢复登录态
- 统一 Axios client、401/403/500 基础处理
- 控制台布局、左侧菜单、正式 IA 分组导航
- `dashboard / accounts / trading / instruments / marketdata / strategies / schedules / runs / research / backtests / evaluations / publishes` 页面域
- Playwright 登录与关键工作区 smoke

## 1. 环境变量

复制 `frontend/.env.example` 为 `frontend/.env`，按需调整：

```powershell
Copy-Item frontend/.env.example frontend/.env
```

关键变量：

- `VITE_API_BASE_URL`：前端请求 base URL，默认 `/api`
- `VITE_API_PROXY_TARGET`：Vite 本地代理目标，默认 `http://127.0.0.1:18888`
- `E2E_USERNAME / E2E_PASSWORD`：Playwright 登录用账号，默认本地 profile 的 `admin / ChangeMe123!`

## 2. 启动后端

本地联调默认走 `backend/nq-app` 的 `local` profile，端口默认 `18888`：

```powershell
mvn -f backend/pom.xml -pl nq-app spring-boot:run
```

默认登录账号来自 `backend/nq-app/src/main/resources/application-local.yml`：

- `admin / ChangeMe123!`
- `operator / ChangeMe123!`
- `viewer / ChangeMe123!`

## 3. 启动前端

```powershell
Set-Location frontend
npm install
npm run dev
```

默认访问地址：`http://127.0.0.1:4173/login`

## 4. 构建

```powershell
Set-Location frontend
npm run build
```

## 5. Playwright 冒烟

前提：

- 后端已启动并可访问 `POST /api/auth/login`
- 前端依赖已安装

运行命令：

```powershell
Set-Location frontend
npm run test:e2e
```

当前 smoke 覆盖：

- 打开 `/login`
- 使用真实账号登录
- 进入 `/dashboard`
- 跳转 `/accounts`
- 跳转 `/trading`
