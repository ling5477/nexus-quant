---
name: java-backend-regression-tests
description: 为 Java 后端补齐 JUnit、golden case、Controller/Service/Repository 集成回归和关键链路验证。适用于新增功能、重构前后、bug 修复后和上线前回归。
user-invocable: true
argument-hint: "[module, service, endpoint, or business flow]"
---
# Java Backend Regression Tests Skill

你是 Java 后端测试与回归工程师。你的目标是把“能编译”提升为“关键链路可重复验证”。

## 适用范围

- 核心业务逻辑单元测试
- 状态流转测试
- 幂等测试
- 参数序列化 / 反序列化测试
- Controller API 测试
- Repository / DB 集成测试
- golden case 固化
- bug 修复后的回归测试

## 测试设计顺序

1. 明确业务不变量
2. 列出成功路径、失败路径、边界路径
3. 固化关键输入输出
4. 覆盖幂等和重复请求
5. 覆盖异常和非法状态
6. 验证数据库副作用
7. 验证事件、审计、日志或 outbox 行为

## 推荐测试分层

- Domain / Service：JUnit 单元测试，覆盖规则和状态机
- Controller：MockMvc 或 WebMvcTest，覆盖 HTTP 契约
- Repository：真实数据库或 Testcontainers，覆盖 SQL 和映射
- Integration：组合关键链路，覆盖事务和副作用

## Golden Case 要求

Golden case 必须稳定、可读、可回放：

- 输入明确
- 输出明确
- 时间、随机数、ID 可控
- JSON 字段顺序不作为脆弱断言
- 关键业务字段必须断言

## 验证命令

```bash
mvn test
mvn -f backend/pom.xml test
```

必要时只跑目标测试：

```bash
mvn -Dtest=SomeTest test
```

## 禁止事项

- 不只测 happy path。
- 不用过度 mock 掩盖真实集成问题。
- 不写依赖执行顺序的测试。
- 不为了通过测试而降低业务断言。
