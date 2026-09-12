---
name: nq-frontend-state-design
description: 用于 NQ 前端页面、组件、CSS 和交互状态的实现或设计；仅讨论前端的文档不触发。
---
# 前端产品与状态

从受影响页面、现有 API 和用户动作出发，沿用 React/Vite/Ant Design 与既有 tokens。局部 CSS 只处理对应视觉问题；复杂状态再分析权限、动作前提和失败反馈，不虚构后端能力。

按需读取[前端工程](references/frontend-engineering.md)命中小节。测试、审查和 Git 统一使用[回归与交付](../nq-trading-correctness-proof/references/regression-delivery.md)；不要求固定插件或完整 E2E。

API 缺失或未知权限只阻塞依赖部分；不得通过隐藏错误、mock 成功或前端绕过服务端权限完成任务。
