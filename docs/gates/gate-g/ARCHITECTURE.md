# GateG 已落地前端架构

> 历史卷宗说明：本文件属于 GateG completed/frozen 历史档案，只读参考，不代表当前实现入口。当前事实以 docs/current/* 与最新源码为准.


## 1. 文档定位

本文件描述 **当前已经落地的 GateG 前端架构**，不是仅用于规划的目录建议。

当前事实：

- 正式前端工程已建立
- 登录恢复、鉴权守卫、控制台布局已落地
- 九个业务页首屏壳子已落地
- 统一 API client 与基础错误处理已落地
- Playwright smoke baseline 已落地

---

## 2. 已落地技术栈

GateG 前端当前实际使用：

- React 19
- TypeScript
- Vite 8
- React Router
- TanStack Query
- Axios
- Zustand
- Ant Design
- Playwright

这套栈已经进入仓库，不再是候选方案。

---

## 3. 已落地目录结构

当前 `frontend/` 已落地的核心结构如下：

```text
frontend/
  src/
    app/
      providers/
    api/
    components/
      app/
      layout/
      page/
    layouts/
    pages/
      login/
      dashboard/
      strategies/
      schedules/
      runs/
      research/
      backtests/
      evaluations/
      publishes/
      trade-validation/
      not-found/
    router/
    store/
    styles/
    types/
    utils/
  tests/
    e2e/
```

目录职责已经收口为：

- `app/providers`：全局 Provider、QueryClient、Ant Design theme、鉴权恢复
- `api`：Axios client、认证接口、错误归一化、query key
- `components`：通用 loading、布局组件、页面壳子组件
- `layouts`：控制台主布局
- `pages`：登录页、dashboard 与九个业务页首屏壳子
- `router`：路由配置、菜单配置、受保护路由守卫
- `store`：鉴权态与 token 状态
- `types / utils`：环境变量、错误事件、token 存储等基础工具
- `tests/e2e`：Playwright smoke

---

## 4. 已落地路由架构

当前真实路由如下：

- `/login`
- `/dashboard`
- `/strategies`
- `/schedules`
- `/runs`
- `/research`
- `/backtests`
- `/evaluations`
- `/publishes`
- `/trade-validation`
- `* -> 404`

路由组织规则：

- `/login` 为公开路由
- 其余页面统一由 `RequireAuth` 保护
- 根路由自动重定向到 `/dashboard`
- 左侧菜单与路由配置共用同一组导航元数据

---

## 5. 已落地鉴权架构

当前鉴权链已经落地为：

1. 登录页调用 `POST /api/auth/login`
2. 登录成功后将 token 与当前用户最小信息写入本地持久化存储
3. 应用启动后在 `AppProviders` 中调用 `GET /api/auth/me`
4. `RequireAuth` 根据恢复结果决定是否放行受保护路由
5. Axios request interceptor 自动追加 `Authorization: Bearer <token>`
6. Axios response interceptor 统一处理 `401 / 403 / 500`

关键约束：

- 服务端主数据不进入 Zustand
- Zustand 只保存鉴权态和最小用户上下文
- 刷新恢复必须依赖 `/api/auth/me`，不允许页面自己重复判定登录态

---

## 6. 已落地请求与错误处理架构

当前请求层已经形成统一出口：

- 所有请求通过 Axios instance 发起
- `baseURL` 统一指向 `/api`
- 本地 Vite dev server 默认代理到 `http://127.0.0.1:18888`
- TanStack Query 负责查询缓存、重试与初始化恢复请求

当前错误处理口径：

- `401`：清理本地登录态并回到 `/login`
- `403`：统一提示权限不足
- `500`：统一提示服务异常并保留 `traceId`

这部分已经落地，不属于 GateG-3 的待设计项。

---

## 7. 已落地布局与页面结构

控制台布局当前已经落地为：

- 顶部区：应用标题、环境标识、当前用户、角色标签、退出登录
- 左侧菜单：dashboard、策略、调度、运行、研究、回测、评估、发布、交易验证
- 内容区：面包屑 + 页面主体

页面结构当前统一复用页面壳子组件，包含：

- 页面标题
- 查询区占位
- 表格区占位
- 空状态

这意味着 GateG-3 以后新增真实列表逻辑时，重点是替换数据和动作，而不是推翻页面骨架。

---

## 8. 已落地测试架构

当前已经落地 Playwright smoke baseline：

- 打开登录页
- 使用真实认证接口登录
- 进入 dashboard
- 跳转至少一个菜单页

当前未完成的是完整 E2E：

- 策略查询 / 触发
- 调度 scan-once
- 运行详情
- 研究 / 回测 / evaluation / publish
- 交易验证操作

---

## 9. 当前未完成与下一步扩展点

GateG-3 ~ GateG-6 仍需完成：

- 真实字段与表格列
- 列表查询、分页、筛选
- 详情页与详情 tab
- 创建 / 触发 / 表单动作
- 完整 Playwright 关键链路回归

扩展原则不变：

- 不新增无关基础设施
- 不改第二套认证协议
- 不把前端联调扩成数据库或后端大重构

---

## 10. 环境说明

当前文档记录里的 build / Playwright 未在当时执行环境实跑完成，根因是 npm registry 网络受限。

这属于执行环境问题，不代表当前前端架构未完成。
