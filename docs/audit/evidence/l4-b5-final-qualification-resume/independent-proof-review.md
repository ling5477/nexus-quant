# 新增证明独立只读核对

Reviewer：独立子代理 `/root/final_proof_review`，未参与新增测试或证据实现；全程未改文件，未重审production或复跑交易测试。核对对象是新增Java harness、原始日志和13个新场景；不把该局部审查单独视为B5全矩阵资格。

第一轮结论：13行全部PASS、databaseAbsent=true，真实Spring/RiskGate/PG16/V51/独立Venue和A/B/C路径成立。控制器仅启动前修改输入，运行后checker只读。死亡强制终止并确认退出，CREATED暂停点跨越B/C推进，TERMINAL点真实Order FILLED/run RUNNING。

已测Java SHA256：`4986dd36809d2e8ddec5dcda1eab0bd25c6ee8b5225e5dfe84fb7a9aa8912310`。Reviewer从完整raw独立核对run身份延续、dispatch→Order→Venue client、Order→Trade→TradeExecuted、exchange order/fill、qty10/price100/fee.01、四笔USDT借贷±1000/±.01、账户/ref/幂等键、authority、SIM、单request/fill与零cancel，以及final→新JVM afterReplay一致。

Java金额与关联断言偏弱，要求可复现离线oracle补齐。已通过 `verify-oracle.py` 完成，不重跑有效场景。并发只证明两个真实入口同时调用，不证明每轮同一数据库临界区重叠；ordinary B提前启动，末尾D才是全新JVM。

第二轮独立轻量执行：exporter8/8 PASS；raw oracle13/13与canonical oracle13/13 PASS，分别5个突变负例拒绝；13份raw/canonical SHA256与重新内存导出全树等价全部通过。四类场景各3次+ordinary1次，未发现阻断问题。

Exporter只接受完整已知返回值格式、只替换已登记引用，credential子树、未知格式与未登记引用保持；未发现新增凭证掩盖。该工具不是通用凭证清洗器，离线oracle也不替代进程时序与拓扑证据。
