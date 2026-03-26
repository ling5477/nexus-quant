---
name: wire-api-module
description: 将后端接口稳定接入当前 React 19 + TypeScript 项目，统一使用 Axios 实例 + TanStack Query 管理服务端数据，并按需要配合 Zustand 处理页面级轻量状态。适用于新增接口接入、字段调整、分页查询接线、提交动作接线等场景。
---

# 目标

把后端接口按当前项目规范接入到前端模块中，形成可复用、可维护、可联调的服务端数据访问层，而不是把请求散落在页面里。

# 固定技术栈约束

1. 所有 HTTP 请求统一走 Axios 实例。
2. 所有服务端数据请求、缓存、失效刷新统一走 TanStack Query。
3. 页面内禁止直接裸写 `axios.get/post/put/delete`。
4. query key 必须稳定、可组合、可失效。
5. 提交动作统一使用 mutation。
6. Zustand 不用于替代服务端数据缓存。
7. Zustand 仅用于：
    - 页面 UI 状态
    - 轻量跨组件状态
    - 当前筛选器临时状态
    - 当前选中项等短期状态
8. 错误提示、请求拦截、鉴权处理优先复用现有 Axios 体系。

# 适用范围

适用于：

- 新增列表查询接口接入
- 新增详情接口接入
- 新增创建/编辑/删除接口接入
- 新增分页查询接入
- 新增 query hooks
- 修正接口字段映射
- query key 整理
- mutation 成功刷新链路整理

不适用于：

- 完整页面 UI 搭建
- 单纯样式问题
- 路由结构设计
- 大规模架构改造

# 必须遵守

1. 先找出现有 Axios 实例、query hooks、query key 组织方式。
2. 请求函数必须放在统一 api/service 模块，不允许散落在页面 JSX 中。
3. query 与 mutation 必须语义清晰，命名清晰。
4. 分页、筛选、排序参数必须与接口契约一致。
5. 成功提交后必须处理：
    - success message
    - query invalidation
    - 局部刷新或回跳
6. 必须处理 loading、error、empty、submitting。
7. 必须处理空值、缺失值、默认值。
8. 对枚举、状态、时间、金额、布尔显示，优先复用现有转换工具。
9. 不允许把 query 结果镜像复制到 Zustand 再二次维护。
10. 输出必须说明请求模块放在哪里、query key 如何组织、页面如何调用。

# 执行步骤

## 第一步：识别当前请求层模式

确认以下内容：

- Axios 实例位置
- 拦截器位置
- 公共 error handler 位置
- api/service 文件结构
- TanStack Query hooks 组织方式
- query key 常量或工厂函数组织方式
- mutation 成功后的刷新模式
- 是否已有页面级 Zustand store

## 第二步：理解接口契约

明确：

- 路径
- 请求方法
- path/query/body 参数
- 统一响应体
- 分页字段
- 列表字段
- 详情字段
- 提交字段
- 可能错误码
- 是否需要鉴权
- 是否需要文件上传下载

## 第三步：确定落点

决定以下内容：

- api/service 文件
- type 文件
- query hook 文件
- query key 文件
- 页面调用位置
- Zustand store（仅在确有必要时）

## 第四步：生成接线代码

实现时必须覆盖：

- Axios 请求函数
- 参数类型
- 返回类型
- query hooks
- mutation hooks
- query key
- error 处理
- submitting 处理
- success 后的 invalidation
- 页面侧最小调用方式

## 第五步：验证页面接入方式

明确页面应如何调用：

- 初始化加载
- 条件查询
- 重置
- 翻页
- 提交
- 删除确认
- 成功刷新
- 失败提示

# 输出格式

每次使用本 skill，输出必须按下面结构组织：

1. 接口定位
2. 参考实现
3. 新增文件
4. 修改文件
5. Axios / Query 设计
6. query key 设计
7. 页面接入方式
8. Zustand 使用边界
9. 风险与注意事项
10. 验证步骤

# 质量标准

合格结果必须满足：

- Axios 实例统一
- TanStack Query 使用正确
- query key 清晰
- mutation 成功链路完整
- Zustand 未被滥用
- 页面可直接联调

# 禁止事项

- 禁止在页面组件里直接裸写 axios 请求
- 禁止把服务端数据放进 Zustand
- 禁止 query key 随意拼字符串且不可复用
- 禁止成功提交后不刷新缓存
- 禁止大量使用 any