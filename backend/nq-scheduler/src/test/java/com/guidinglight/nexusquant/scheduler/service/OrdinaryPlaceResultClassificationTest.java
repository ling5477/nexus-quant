package com.guidinglight.nexusquant.scheduler.service;

import com.guidinglight.nexusquant.adapter.api.model.*;
import com.guidinglight.nexusquant.adapter.api.service.TradingAdapter;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;
import com.guidinglight.nexusquant.trading.application.port.TradingGatewayResultCategory;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import java.time.Instant;
import java.util.List;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

/** 不确定响应不能充当拒单证据；真实进程的损坏 ACK 场景另证无错误终态及成交恢复。 */
class OrdinaryPlaceResultClassificationTest {
    @Test void separatesUncertainResponsesFromDefinitiveBusinessRejection() {
        TradingAdapter adapter=mock(TradingAdapter.class); when(adapter.venue()).thenReturn("OKX");
        var gateway=new AdapterBackedTradingVenueGateway(List.of(adapter));
        var order = new OrderRecord("order", 1L, null, "okx", "BTC-USDT", "client", "BUY", "LIMIT",
                BigDecimal.ONE, BigDecimal.ONE, null,
                OrderStatus.SENT, null, "trace");
        var request=mock(PlaceOrderRequest.class);
        when(request.traceId()).thenReturn("trace");
        for (String code : new String[]{"INVALID_JSON","OKX_EMPTY_DATA","OKX_API_ERROR","51603","51008"}) {
            var category=code.equals("51603") ? AdapterResultCategory.NOT_FOUND : AdapterResultCategory.FATAL_FAILURE;
            when(adapter.placeOrder(any())).thenReturn(new AdapterOrderAck(false,"OKX",1L,"BTC-USDT","client",null,
                    "REJECTED",category,new AdapterError(code,"test",category,false),Instant.now(),null,"trace","SIM"));
            assertEquals(code.equals("51008") ? TradingGatewayResultCategory.FATAL_FAILURE : TradingGatewayResultCategory.DEFERRED,
                    gateway.placeOrder(order,request).resultCategory(),code);
        }
        when(adapter.placeOrder(any())).thenReturn(new AdapterOrderAck(false,"OKX",1L,"BTC-USDT","client",null,
                "UNKNOWN",AdapterResultCategory.FATAL_FAILURE,null,Instant.now(),null,"trace","SIM"));
        assertEquals(TradingGatewayResultCategory.DEFERRED,gateway.placeOrder(order,request).resultCategory());
    }
}
