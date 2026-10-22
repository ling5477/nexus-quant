# 开发数据库 CI 合同

开发 CI 自动从 `backend/nq-infra/src/main/resources/db/migration` 发现当前迁移，生成本次运行专用的身份文件。新增连续版本 SQL 后，无需修改版本号、迁移条数、表数量或 Schema fingerprint。

入口是 `development_schema.py`。Maven 集成测试通过 `CurrentMigrationTestInventory` 从同一 SQL 源码目录获得预期，研究、账户快照、活动查询和跨 JVM 恢复测试不再读取旧开发路径快照。Backend CI 和 PostgreSQL CI 消费同一个生成规则；`MigrationInventory.java` 还会把生成清单中的 SHA256 与实际 classpath SQL 比较，避免旧构建资源或遗漏资源通过校验。

## 保留的检查

- 版本从 V1 连续递增，拒绝重复版本、缺口、异常文件和符号链接。
- `development-schema-manifest.json` 保留为 V44 历史锚点。其已有 SQL/checksum 及初始安全事实继续校验，不作为当前版本或当前 Schema 数量的固定预期。旧 `development-source-manifest.json` 仅供历史合同测试，不再作为开发 CI 的当前迁移列表。
- PR 使用真正的 base SHA；push 使用事件的 before SHA，完整获取该历史。不能只检查最后一个提交的父节点，否则一次 push 中较早提交的历史改写可能漏检。手动 workflow_dispatch 默认检查第一父节点；本地可显式传 `--base-ref`。
- PostgreSQL CI 创建随机参考数据库，使用同一候选 SQL 实际执行 Flyway migrate、validate 和重复 migrate=0，生成当前参考 Schema/history。被测数据库仅只读比较，不能直接用被测库现状生成自己的预期。
- 比较完整 Schema（含约束、函数、索引、trigger、RLS/ACL）、Flyway history 和初始事实；既有控制事实必须保持，新业务表默认零行。修改默认控制、初始角色或新增非空静态事实需要显式调整相应业务合同，不能自动接受。
- 参考库在成功和失败路径均清理；不对被测库执行 clean、repair 或 reset。连接参数和凭证不进入日志。

## 使用

仅生成开发迁移身份，不访问数据库：

```powershell
python scripts/ci/development_schema.py --base-ref origin/dev --source-output artifacts/current-migrations.json
```

数据库比较沿用 CI 中的 `--psql-url`、`--reference-classpath` 与 `NQ_FLYWAY_DB_URL/USER/PASSWORD`。参考连接账号只应属于可丢弃 CI 环境并可创建临时数据库；该脚本不用于生产资格或真实部署。

`verify-release-schema.py`、`release-schema-manifest.json`、`release-schema-baseline-manifest.json` 和 `release/source-manifest.json` 的正式发行语义继续保持冻结，不隐式发现或吸收新增迁移。
