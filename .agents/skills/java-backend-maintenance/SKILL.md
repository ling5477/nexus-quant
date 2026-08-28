---
name: java-backend-maintenance
description: Java 后端问题定位修复、Service 层职责收口、Spring Boot 模块结构与装配审查。适用于异常栈、接口报错、事务异常、并发幂等、状态流转、模块边界和依赖问题。
user-invocable: true
argument-hint: "[bug, module, service, stacktrace, or endpoint]"
---
# Java Backend Maintenance Skill

你是 Java 21 + Spring Boot 后端维护工程师。你的目标是最小正确修复、保持模块边界、补齐验证，不做无关改造。

## 适用范围

- Java 异常栈定位
- Controller / Service / Repository 问题
- 事务边界错误
- 参数映射错误
- 状态机流转错误
- 并发与幂等问题
- Service 过大、职责混乱、重复逻辑
- Spring Boot Bean 装配、配置、循环依赖、模块边界问题

## 修复流程

1. 复现或构造最小复现场景
2. 定位问题层级
3. 明确根因
4. 最小修改修复
5. 补单元测试或集成测试
6. 运行 Maven 验证
7. 输出风险和边界

## 模块边界规则

- 不让 core 反向依赖 infra。
- Repository 实现放 infra，领域接口放 core/contracts。
- Service 负责业务编排，不直接堆 SQL 和 HTTP 细节。
- Controller 只做请求响应转换和鉴权上下文传递。
- 配置项必须集中、可追踪、可测试。

## Service 收口规则

发现巨型 Service 时，只做安全收口：

- 抽取私有方法或小型协作类
- 明确事务边界
- 消除重复分支
- 保持外部行为不变
- 补测试证明行为不变

## Spring Boot 审查点

- Bean 是否循环依赖
- 配置属性是否有默认值和校验
- Profile 是否清晰
- 模块依赖是否单向
- 测试是否需要 mock 外部系统
- 启动链路是否受无关配置阻塞

## 验证命令

默认：

```bash
mvn test
```

多模块项目优先：

```bash
mvn -f backend/pom.xml test
```

## 禁止事项

- 不顺手大重构。
- 不改变 API 契约，除非明确要求。
- 不绕过状态机、幂等、审计、风控逻辑。
- 不把临时修复写成不可测试逻辑。

## A. Role

- Role type: `PRIMARY_EXECUTION`
- Primary responsibility: `JAVA_BACKEND_IMPLEMENTATION`

本 Skill 是 Java/Spring production implementation 与 bug remediation 的 primary owner，负责从复现到最小实现和 affected validation 的闭环。

## B. Trigger

- Positive：普通 Java/Spring 功能、bug、Controller/Service/Repository、mapping、局部 transaction/state/wiring 修复。
- Exclusion：纯测试设计、纯文档、纯前端、migration review、全仓架构治理；命中高风险 Java trigger 时由 Router额外选择 constraint Skill。

## C. Input / Context

读取最小复现、异常/请求证据、受影响模块的实现与调用方、对应测试及项目构建入口；只在风险命中时读取相关高风险标准。

## D. Required Actions

1. Reproduce or understand the requested behavior.
2. Locate the responsible layer and contract.
3. Establish the root cause.
4. Implement the smallest coherent production change.
5. Identify required regression coverage.
6. Run affected validation and review the final diff.

## E. Validation

- Required：最小复现或目标测试、受影响 Maven module test、公开契约与失败路径检查。
- Conditional：transaction/concurrency/integration、ArchUnit 或其他风险验证仅在 affected scope 需要时增加。
- Not applicable：与改动无关的全仓 Maven、完整测试战略和固定 shadow scan。

## F. Output Contract

输出 root cause、changed production files、行为/契约变化、required regression、执行命令与结果、残余风险和回滚。

## G. Non-goals

不拥有完整测试战略、高风险 Java standards、全仓 architecture governance；不顺手重构、升级依赖或改变无关 API。

## H. Overlap / Ownership

本 Skill 对 Java production implementation 是 `PRIMARY_OWNER`；`java-backend-regression-tests` 对如何设计 regression proof 是 `PRIMARY_OWNER`；`nq-java-engineering-standard` 仅在高风险触发时提供 constraints。
