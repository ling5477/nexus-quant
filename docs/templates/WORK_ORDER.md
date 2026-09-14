# Work Order

普通任务的 canonical prompt contract。优先描述 outcome、scope、hard boundaries 和 done state，不预先规定所有执行步骤；简单任务不必机械填空。执行、发现分类、验证与交付规则引用[统一合同](../../.agents/skills/nq-trading-correctness-proof/references/regression-delivery.md)，不复制另一套 authority。

若 prompt 堆积“必须先 A 再 B”“只能一次”“失败必须停”“第二次重试重新授权”，逐项检查是否来自 safety / correctness / irreversible boundary；没有这类依据的过程控制应删除或交给模型自主判断。目标是减少冲突和无必要停机诱因，不是单纯压缩篇幅。

## 1. Goal
描述最终可验收目标，不写完整操作手册。

## 2. Baseline
目标目录、候选或相关现状；只填写影响本次工作的事实。

## 3. Scope
可修改的模块、文件或行为，以及明确不可修改的范围；保护已有工作。

## 4. Hard Invariants
只列本任务真正不可破坏的条件，引用已有 owner，不复制 AGENTS/Skill 全文，不把偏好升级成硬约束。安全、正确性、不可逆授权与正式 run 不可变边界保持严格。

## 5. Execution Policy
在 scope 和硬边界内自主执行到完成；目标清楚时自行选择合理方案。发现先按统一合同分类 BLOCKING / NON_BLOCKING / OBSERVATION，记录非阻塞问题并继续安全有效工作，收尾统一归因。普通可恢复失败在范围内自行修复和有界重试，无需重复申请授权；正式 qualification 非阻塞异常保留证据并继续有效观察，不在 run 中改代码、合同或标准。

## 6. Stop Conditions
只列任务特有的真正 blocker，沿用统一合同的安全、业务正确性、证据/实验有效性、scope、不可逆授权及扩大损害边界。停止受影响路径，继续其他安全且有解释力的工作；普通 warning 或首次可恢复失败不自动 STOP。

## 7. Verification
先选最相关测试、必要边界证明及已有 hard gate，给出预期结果。通过且无新失败、实质变更或未解决风险即结束验证，不默认 Full Maven 或重复 smoke/qualification。普通变更自查加相关测试；高风险按统一合同完成一次定向独立审查，通过后不默认 review-of-review。

## 8. Done
定义最终必须成立的状态、交付物及必须披露的限制；统一评估发现，区分完成观察、通过验证与获得验收。失败证据保留，未满足验收条件不能声称 PASS。

## 9. Git
分别注明 commit、push、PR、merge 授权与目标；已有授权不重复索取，未授权的动作不执行。按统一合同核对 exact diff、只暂存明确文件 allowlist，禁止 git add . / git add -A，不混入其他任务 evidence；需要交付 CI 时注明新 exact-head 的预期检查与成功条件。
