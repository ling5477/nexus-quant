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
