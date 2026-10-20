package com.guidinglight.nexusquant.adapter.api.publicmarketdata;

import static org.junit.jupiter.api.Assertions.*;

import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataEndpointCategory;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.model.PublicMarketDataOutboundRequest;
import com.guidinglight.nexusquant.adapter.api.publicmarketdata.policy.PublicMarketDataOutboundPolicy;

import org.junit.jupiter.api.Test;

import java.time.Clock;

/** 证明盘口默认禁止及显式开放后的固定现货路径边界；包含私有、超深度、衍生品和携带材料的拒绝例。 */
class SpotOrderBookPolicyTest {
    @Test
    void defaultRemainsDeniedAndOptInOnlyAllowsTheBoundedSpotResource() {
        String path = "/api/v5/market/books?instId=BTC-USDT&sz=20";
        var request =
                PublicMarketDataOutboundRequest.publicGet(
                        "OKX", PublicMarketDataEndpointCategory.ORDER_BOOK, path);
        assertFalse(new PublicMarketDataOutboundPolicy().evaluate(request).allowed());
        var enabled = new PublicMarketDataOutboundPolicy(Clock.systemUTC(), true);
        assertTrue(enabled.evaluate(request).allowed());
        for (String unsafe :
                new String[] {
                    "//other/market/books",
                    "/api/v5/market/books?instId=BTC-USDT&sz=400",
                    "/api/v5/market/books?instId=BTC-USDT-SWAP&sz=20",
                    "/api/v5/account/balance",
                    "/api/v5/market/books?instId=BTC-USDT&sz=20&apiKey=secret"
                })
            assertFalse(
                    enabled.evaluate(
                                    PublicMarketDataOutboundRequest.publicGet(
                                            "OKX",
                                            PublicMarketDataEndpointCategory.ORDER_BOOK,
                                            unsafe))
                            .allowed());
        assertFalse(
                enabled.evaluate(
                                new PublicMarketDataOutboundRequest(
                                        "OKX",
                                        PublicMarketDataEndpointCategory.ORDER_BOOK,
                                        path,
                                        true,
                                        false,
                                        null,
                                        null,
                                        null))
                        .allowed());
        assertFalse(
                enabled.evaluate(
                                new PublicMarketDataOutboundRequest(
                                        "OKX",
                                        PublicMarketDataEndpointCategory.ORDER_BOOK,
                                        path,
                                        false,
                                        true,
                                        null,
                                        null,
                                        null))
                        .allowed());
    }
}
