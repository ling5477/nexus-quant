# 前端本地化与错误目录

本目录解释错误身份与展示契约。运行时唯一前端目录为 [catalog.ts](../../frontend/src/errors/catalog.ts)，后端稳定编号唯一 owner 为 [ApiErrorIdentity](../../backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/ApiErrorIdentity.java)。本文不是第二份运行时映射表。

## Inventory 与迁移边界

基线为 `4122cba1c6697fd1e5322510af028e184c7588ef`，分支 `audit/post-gatey-agent-baseline`；远端同分支 HEAD 一致，基线 CI `35058863828` 为 9/9 SUCCESS，初始 stage=0。既存 Phase6 evidence 和 storage analyzer 工作区改动不属于本批，不得暂存。远端默认分支 HEAD 与工作分支不同，不作为本批 baseline。

| 分类 | 本批处理 |
| --- | --- |
| LOCALIZE_NOW | 登录/鉴权/异常页、App Shell、导航、主要业务页面的自然语言、共享加载/错误/确认/风险/图表文案、表单校验、动作与提示。 |
| MACHINE_IDENTIFIER_KEEP | symbol、交易所代码、SIM/LIVE、业务枚举、API 字段与 payload、query key、路由、traceId、errorId、case/order/run 等标识及原始审计证据。 |
| ERROR_CATALOG | 统一 API 错误、字段校验、认证/权限、冲突、系统/网络故障，以及当前可见 admission/review 错误。 |
| DEFER | 未路由的旧页面壳、开发专用 design-system 演示文案；后端返回的审计/诊断原文仍为证据，不以翻译改变其内容。历史业务码的全面 NQ 编号不在本批。 |

Inventory 发现原有 `formatApiError` 直接展示 backend message，AppProviders 另写 HTTP 文案，instrument 同步页通过 message 子串判断错误。迁移后由 catalog 解释机器身份，页面保留业务操作逻辑，错误消息不再参与语义判断。

## i18n

[i18n/index.ts](../../frontend/src/i18n/index.ts) 使用 i18next/react-i18next，`zh-CN` 为默认和 fallback，`en-US` 为第二语言。namespace 为 `common`、`errors`、`pages`。语言选择写入本地 `nq.locale`；存储不可用时仍可在当前会话切换，不新建后端偏好字段。

组件订阅语言变化，静态导航/列文案按读取时翻译，memo 依赖语言；不通过重挂载页面改变表单或业务状态。已显示的表单校验错误仅重新本地校验，不调用 onFinish，不发送写请求。ConfigProvider 和 dayjs 同步语言，数字精度、金额换算、排序、API machine values 和 query identity 保持不变。

## 错误契约与展示

只使用原有 [ApiErrorResponse](../../backend/nq-api/src/main/java/com/guidinglight/nexusquant/api/web/ApiErrorResponse.java)。新增可省略的 `errorKey/errorId`，保留原八参数构造器，未编号旧错误的 JSON 字段形状不变。旧 `code` 继续兼容现有客户端；精确身份只由对应异常 handler 显式附加，不从通用 code 或 message 推导。

`normalizeApiError` 保留 code/errorKey/errorId/status/path/traceId/fieldErrors/raw 与诊断 message。字段原有 `reason/rejectedValue` 仍为诊断事实。因当前字段校验没有稳定 constraint code，正常 UX 显示字段名加本地化通用校验提示，不解析英文 reason，也不展示 rejectedValue。

解析优先级固定为：已登记 errorId → 已登记 errorKey → 已登记 code → HTTP category → UNKNOWN_ERROR。未知身份仍保留供定位；单独的旧 STATE_CONFLICT 不冒认订单冲突。目录 entry 包含 HTTP 预期、severity、presentation、title/message/action key、retry policy 与 traceId 可见性。

| Presentation | 使用方式 |
| --- | --- |
| GLOBAL_NOTIFICATION | HTTP 权限/系统错误通过全局 bridge；同一错误对象的页面短提示去重。 |
| INLINE_FORM_ERROR | 本地表单校验或统一错误说明；字段诊断和 traceId 可同时保留。 |
| PAGE_STATE_ERROR | 查询错误通过页面错误态和统一 formatter 展示。 |
| CONFLICT_ACTION_REQUIRED | 提示读取最新状态后重新确认，禁止自动重放写请求。 |
| AUTH_REDIRECT_OR_PROMPT | 登录页提示或既有 401 清理/跳转；只跨跳转保存安全错误身份和 traceId。 |

所有正常错误 UX 使用本地化目录文案；后端 message 仅留作诊断。未知响应/代理 HTML/非标准字段形状也不能进入主文案。系统异常页不生成伪造的服务端追踪编号，缺少编号时明确显示未提供。

## NQ-TRD-1001 / ORDER_VERSION_CONFLICT

真实路径为：

`POST /api/trading/orders/cancel` → `TradingVerificationController` → `OrderCommandService` → `OrderCommandWriteService.transitionOrderInternal` 的既有 CAS 失配分支 → `OrderVersionConflictException` → `ApiExceptionHandler` → HTTP 409 的统一 envelope → frontend normalization → catalog → 双语提示。

Controller 保留既有 trading-components 开关。异常仍继承 IllegalStateException，保留原内部诊断和捕获/回滚语义；唯一业务路径变化是将原异常换为该子类型。未新增交易 API、状态迁移或数据库改动。其它 generation invariant 失败继续 STATE_CONFLICT，不扩大映射。

该具体分支以前由通用 IllegalStateException handler 返回 STATE_CONFLICT，本批保持其 HTTP 409 和 `code=STATE_CONFLICT`，附加 `errorKey=ORDER_VERSION_CONFLICT`、`errorId=NQ-TRD-1001`。旧客户端继续读取旧 code，新客户端优先解析精确身份。不能把所有 STATE_CONFLICT 全局 alias 为订单版本冲突；未登记的旧错误继续保留原 code 且不附加身份。前期直接替换旧 code 的方案已由独立审查指出并整改，未提交或发布。

中文：订单已被其他操作更新，请刷新最新状态后重试。

English: The order was updated by another operation. Refresh the latest state before trying again.

当前撤单 API 没有客户端 expectedVersion 字段，不能为示例虚构字段；并发所有权仍由服务端既有订单快照/CAS 控制。全局 mutation retry=false，冲突 query 也不自动重试；用户主动刷新后才能决定下一步。语言切换不改变 clientOrderId、现有 expectedVersion 或幂等身份。

## 旧错误编号处置

CATALOG_NOW：通用 validation/auth/HTTP 类别、ORDER_VERSION_CONFLICT、ADMIN_NOT_INITIALIZED、ADMISSION_*、SHADOW_MATERIALIZATION_FORBIDDEN，以及已登记 review/idempotency 错误。

LEGACY_STABLE_KEEP：其余 REVIEW_*、LIVE_*、PILOT_*、Shadow idempotency、RISK_LIMIT_SET_OPERATOR_ROLE_REQUIRED、PREREQUISITE_OBSERVATION_IDENTITY_CONFLICT、APPROVAL_ID_REUSED、TRUSTED_PREREQUISITE_OBSERVATION_UNAVAILABLE 等 code 保持原样。未登记具体文案时使用 HTTP 安全回退，不宣称已为其分配 NQ 编号。

后续新增错误先注册其真实 owner 和 HTTP 语义，再登记双语文案与失败路径测试，不能根据 message 推导身份。
