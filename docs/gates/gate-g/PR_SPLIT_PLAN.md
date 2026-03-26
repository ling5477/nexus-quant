# GateG PR_SPLIT_PLAN

原则：

- 一个 PR 只解决一类问题
- 先文档、再骨架、再页面、再回归
- 不把 GateG 做成一次性巨型 PR
- 不借 GateG 发散成新的后端重构批

---

## 1. 执行顺序总览

- [x] GateG-DOC-1：前端控制台与联调开工基线
- [ ] GateG-1：前端工程骨架
- [ ] GateG-2：登录、鉴权守卫、布局、菜单
- [ ] GateG-3：策略定义 / 调度 / 运行页面
- [ ] GateG-4：研究配置 / 回测配置 / 回测运行 / evaluation / publish 页面
- [ ] GateG-5：交易验证操作页
- [ ] GateG-6：Playwright 关键链路回归

---

## 2. GateG-1：前端工程骨架

目标：

- 建立 React + TypeScript + Vite 工程
- 建立路由、状态管理、请求层、页面目录与基础样式体系
- 收口环境配置与 API base URL

不做：

- 业务页面细节实现
- 真实复杂主题系统

---

## 3. GateG-2：登录、鉴权守卫、布局、菜单

目标：

- 登录页
- token 持久化
- `/api/auth/me` 初始化当前用户
- 受保护路由守卫
- 基础布局、菜单、头部与退出登录

不做：

- SSO
- 多租户复杂权限模型

---

## 4. GateG-3：策略定义 / 调度 / 运行页面

目标：

- 策略定义列表、详情、创建、触发
- 调度列表、详情、创建、scan-once
- 运行列表、详情

不做：

- 复杂图形化编排器
- 实时推送大屏

---

## 5. GateG-4：研究配置 / 回测配置 / 回测运行 / evaluation / publish 页面

目标：

- 研究配置列表、详情、创建
- 回测配置列表、详情、创建
- 回测运行列表、详情、创建、start、evaluate、publish
- run 详情 tab：`sim-orders / sim-trades / sim-positions / pnl-snapshots / evaluation / publish`

不做：

- BI 报表平台
- 自由拖拽分析器

---

## 6. GateG-5：交易验证操作页

目标：

- 下单、撤单
- 查询订单、成交、持仓、账户
- 执行 reconciliation / recovery run-once
- 展示结果与 trace / id 关键信息

不做：

- 复杂运维控制台
- 多交易所统一大盘

---

## 7. GateG-6：Playwright 关键链路回归

目标：

- 登录成功
- 进入受保护页面
- 策略页基本查询 / 触发
- 回测页基本查询 / start / evaluate / publish
- 交易验证页基本下单 / 撤单 / 查询入口

不做：

- 全量视觉回归
- 大规模跨浏览器矩阵
