package com.guidinglight.nexusquant.account.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.account.application.exception.AccountFeeSyncException;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 多bean装配须失败关闭：相同范围多源不能按注册顺序选择，能力查询也不能执行读取。 */
class RoutingAccountFeeReadPortTest {
    @Test
    void zeroAndAmbiguousMatchesAreNotAvailable() {
        var first = new Source();
        var second = new Source();
        var empty = new RoutingAccountFeeReadPort(List.of(first, second));
        assertFalse(empty.supports(null, "BTC-USDT"));
        assertThrows(AccountFeeSyncException.class, () -> empty.read(null, null, "BTC-USDT"));
        first.enabled = true;
        second.enabled = true;
        assertFalse(empty.supports(null, "BTC-USDT"));
        assertThrows(AccountFeeSyncException.class, () -> empty.read(null, null, "BTC-USDT"));
        assertEquals(0, first.reads);
        assertEquals(0, second.reads);
    }

    @Test
    void exactlyOneMatchUsesOnlyThatSource() {
        var first = new Source();
        var second = new Source();
        second.enabled = true;
        var router = new RoutingAccountFeeReadPort(List.of(first, second));
        assertTrue(router.supports(null, "BTC-USDT"));
        assertEquals(0, second.reads);
        assertEquals(second.quote, router.read(null, null, "BTC-USDT"));
        assertEquals(0, first.reads);
        assertEquals(1, second.reads);
    }

    /** 用计数的有界来源验证路由副作用，不引入核心模块原本没有的mock依赖。 */
    private static final class Source implements AccountFeeReadPort {
        boolean enabled;
        int reads;
        final AccountFeeQuote quote =
                new AccountFeeQuote(
                        BigDecimal.ZERO, BigDecimal.ZERO, "Lv0", Instant.EPOCH, Instant.EPOCH);

        @Override
        public boolean supports(ExchangeAccountSummary account, String symbol) {
            return enabled;
        }

        @Override
        public AccountFeeQuote read(
                ExchangeAccountSummary account,
                ExchangeAccountCredentialSummary credential,
                String symbol) {
            reads++;
            return quote;
        }
    }
}
