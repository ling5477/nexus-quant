package com.guidinglight.nexusquant.trading.application.boundary;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.guidinglight.nexusquant.contracts.model.OrderSide;
import com.guidinglight.nexusquant.contracts.model.OrderType;
import com.guidinglight.nexusquant.trading.application.CancelOrderRequest;
import com.guidinglight.nexusquant.trading.application.PlaceOrderRequest;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/**
 * PaperToRealBoundaryGuardTest 固定 GateM-4 Paper-to-Real 边界。
 *
 * <p>Why:
 * Paper run / order / fill / position ID 可能被外部调用方误传入正式交易入口。本测试证明这些 artefact
 * 在进入真实授权语义前就会 fail-closed，且普通 SIM/stub 回归仍不因为 venue=PAPER 被误杀。</p>
 */
class PaperToRealBoundaryGuardTest {

    @Test
    void shouldRejectPaperOrderArtefactBeforeRealOrderPath() {
        PlaceOrderRequest request = new PlaceOrderRequest(
                "req-paper-submit",
                1001L,
                "ptr-1",
                "OKX",
                "BTC-USDT",
                "coid-1",
                "1001:coid-1",
                "paper_trading",
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                new BigDecimal("0.01000000"),
                "IOC",
                "trc-paper-submit"
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PaperToRealBoundaryGuard.requireNoPaperArtifactForRealOrderPath(request)
        );

        assertTrue(ex.getMessage().contains(PaperToRealBoundaryGuard.PAPER_ORDER_NOT_REAL_AUTHORIZATION));
    }

    @Test
    void shouldKeepStubPaperVenueAllowedWhenNoPaperArtefactMarkerExists() {
        PlaceOrderRequest request = new PlaceOrderRequest(
                "req-sim-stub",
                1001L,
                "run-1",
                "PAPER",
                "BTC-USDT",
                "coid-1",
                "1001:coid-1",
                "strategy",
                OrderSide.BUY,
                OrderType.MARKET,
                null,
                new BigDecimal("0.01000000"),
                "IOC",
                "trc-sim-stub"
        );

        assertDoesNotThrow(() -> PaperToRealBoundaryGuard.requireNoPaperArtifactForRealOrderPath(request));
    }

    @Test
    void shouldRejectPaperCancelLocatorBeforeRealCancelPath() {
        CancelOrderRequest request = new CancelOrderRequest(
                "cancel-po-1",
                null,
                1001L,
                "OKX",
                "BTC-USDT",
                "po-1",
                null,
                "USER_REQUESTED",
                "trc-cancel-po-1"
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> PaperToRealBoundaryGuard.requireNoPaperArtifactForRealCancelPath(request)
        );

        assertTrue(ex.getMessage().contains(PaperToRealBoundaryGuard.PAPER_ORDER_NOT_REAL_AUTHORIZATION));
    }

    @Test
    void shouldRejectPaperFillAndPositionAsRealFacts() {
        IllegalArgumentException fill = assertThrows(
                IllegalArgumentException.class,
                () -> PaperToRealBoundaryGuard.requireNotPaperFillForRealFill("pt-1")
        );
        IllegalArgumentException position = assertThrows(
                IllegalArgumentException.class,
                () -> PaperToRealBoundaryGuard.requireNotPaperPositionForRealAccount("pos-1")
        );

        assertTrue(fill.getMessage().contains(PaperToRealBoundaryGuard.PAPER_FILL_NOT_REAL_FILL));
        assertTrue(position.getMessage().contains(PaperToRealBoundaryGuard.PAPER_POSITION_NOT_REAL_ACCOUNT_POSITION));
    }
}
