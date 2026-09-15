# L6-B 内容身份修正

V1候选提交c2e42b69的CI 34936341503未通过，正式180min未启动。原合同、原probe及全部历史结果保留。

新入口使用L6_B_RUNNER_CONTRACT_V2.json。模型、A报告副本和volume-analysis的Git文本换行会在Windows/Linux检出间转换；三项输入明确采用UTF8_LF_EXACT_CONTENT SHA256，仅将CRLF转LF，不trim、不JSON重序列化，不修改原输入。每项与已提交Git blob的SHA完全一致。原manifest及canonical plan仍按原raw SHA验证。

运行指纹继续绑定现场原始字节、全部源码/编译输出、HEAD与当前V2合同。正式期间即使只有换行变化也会使现场指纹失效。committed输入核验仍存在；修改JSON值、空白或尾部换行仍被内容hash拒绝。

本次不改重启时序、业务、资源采样、容量或预算。复用已通过的最终300s probe验证这些未变机制；新增hash入口由定向9项测试及新exact-head CI验证，probe不升格为正式资格。
