# 当前账户现货费用来源

费率观察属于账户来源证据，不建立订单、成交或账务事实源。同步必须由有权限用户显式提交稳定 UUID；回执重放与历史读取不再次解密或访问供应方。未知结果继续查询同一身份，不能通过换 UUID 掩盖在途请求。

OKX 保留原生负值收费／正值返佣，NQ 标量成本取相反数。旧 V1 观察摘要及旧冻结模型的字段、精度和编码保持不变。

Binance 保留 standard、tax、special 各自的 maker、taker、buyer、seller 十二个分量及 discount 条件。按指定买卖方向和流动性角色相加；优惠资产足额时仅 standard 乘优惠乘数，tax 与 special 不变。优惠开关不能证明余额足额或某笔成交实际优惠。观察没有全局 maker/taker 成本、VIP 档位或费率变更时间，这些旧标量字段为 null。HTTP Date 只能表示供应方响应时间；缺少时 providerAt 为 null、依据为 NOT_PROVIDED。

Binance 来源摘要使用 V2 编码，绑定 owner、账户、精确凭据版本、环境、正式内部标的、供应时点依据、完整规范化分量及响应摘要。原生响应仅即时解析并计算摘要，不保存认证 query、材料或原文。

V10 只前向扩展现有不可变观察表。旧来源行不回填费率或重算摘要；owner/账户与凭据外键、稳定同步身份、观察有效期以及不可变触发器保留。新应用仓储要求 V10；原 V9 运行候选不会热读新字段，更新安装须先完成相应迁移及候选验证。旧应用仍能读取旧 OKX 行，但不能理解新的 Binance 条件行，不支持把旧应用回滚到已有新来源的 V10 数据上运行。

## 只读装配与实际限制

默认关闭。仅显式 `binance-private-readonly` profile、`nq.account-fees.binance.enabled=true` 和全部环境安全条件匹配时装配。owner-user-id、exchange-account-id、credential-id、credential-type、allowed-instruments、permission-scope 必须精确配置；本配置不携带材料。

即时解密从正式密文库精确选择 BINANCE_HMAC 或 BINANCE_ED25519 的活动、结构校验通过版本。事务内先加账户／凭据共享锁，再解密；只执行固定 time、apiRestrictions、commission GET，不重试、重定向或使用系统默认代理。每步重核同一账户／版本及 ENGAGED 安全身份。普通 VERIFIED 是结构验证，远端权限由本次 GET 独立验证；ipRestrict 只能证明远端启用了 IP 限制，不能宣称获得具体 IP 名单或字面匹配证明，更不授予交易能力。

主网只读来源对应 LIVE 账户上下文，环境执行开关仍保持关闭。Spot Test Network 不提供必需的 `/sapi` 权限接口，SIM 账户不可借用主网来源；原生 reader 在联网前明确拒绝 TESTNET。支持原生 Unicode 协议不扩大 NQ 当前 ASCII 内部标的目录。

JDBC、Jackson 和现有签名器存在不可原地清零 String 边界。解密材料仅留在同步调用内，临时字符数组在 finally 清零，不跨对象字段、缓存、会话、回执或 API 返回；不宣称 JVM 内全部字符串可即时擦除。

## 尚未闭合的消费与验证

现有运行 V1 只消费单一 quote 币种费用率。Binance 条件观察不能压缩为该标量，正式选择目前以 FEE_MODEL_DIRECTIONAL_CONSUMPTION_REQUIRED 拒绝，页面如实提示。后续仍须实现方向与支付条件的冻结及经济消费，证明 BUY 收入 base／SELL 收入 quote 与优惠资产支付时的数量、估值、预留、精度和账务语义；不能仅改费率字段名称。

当前验证使用随机隔离 PostgreSQL 和 127.0.0.1 合成供应方；不等于真实 Binance 联调或历史费率证明。真实凭据、私有公网、LIVE、真实交易和发布仍需要当前用户授权。源代码与隔离测试不会自行赋予这些权限。
