package com.guidinglight.nexusquant.account.domain.port;

import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;

/** 显式账户费率同步端口；构造和能力查询不解密凭据、不访问供应方。 */
public interface AccountFeeReadPort {
    /** 仅判断显式装配的账户及标的范围，不解密或联网；返回可支持不替代读取时的权限核验。 */
    boolean supports(ExchangeAccountSummary account, String symbol);

    /** 只在用户显式同步后读取所选凭据版本的费率；来源、权限或完整性不满足时必须失败。 */
    AccountFeeQuote read(
            ExchangeAccountSummary account,
            ExchangeAccountCredentialSummary credential,
            String symbol);

    /** 缺少受控来源时拒绝同步；保留历史证据读取，不用默认费率冒充实际查询。 */
    static AccountFeeReadPort disabled() {
        return new AccountFeeReadPort() {
            @Override
            public boolean supports(ExchangeAccountSummary account, String symbol) {
                return false;
            }

            @Override
            public AccountFeeQuote read(
                    ExchangeAccountSummary account,
                    ExchangeAccountCredentialSummary credential,
                    String symbol) {
                throw new IllegalStateException("PRIVATE_FEE_READ_DISABLED");
            }
        };
    }
}
