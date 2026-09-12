---
name: nq-postgres-migration-review
description: 用于 NQ PostgreSQL schema、Flyway migration 或回填的实现与审查；普通查询修复和文档提及 database 不触发。
---
# PostgreSQL migration review

目标是证明拟议迁移在已有数据和应用兼容范围内安全成立。评审模式不修改候选，除非用户明确要求同轮修复且不要求独立性。

从迁移差异、受影响查询及兼容调用方取证；保留 forward-only、历史不可变、约束与数据完整性。只检查受影响表的锁、数据规模和恢复风险。回填存在时证明分批、重跑和中断恢复。

涉及数据库实际行为的验收使用隔离 PostgreSQL，执行升级与 Flyway validate，验证新增/既存数据和相关失败场景；静态审查可交付明确的未验证项，但不能声称运行时已通过。完成时给出有定位的 findings、执行证据和剩余风险。

按需参考 [数据库证明](references/database-proof.md)。不连接生产库，不运行无关应用全量测试。
