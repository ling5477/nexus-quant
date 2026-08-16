package com.guidinglight.nexusquant.livecontrol.infra.okx;

import com.guidinglight.nexusquant.account.infra.okx.readonly.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderOperation;
import com.guidinglight.nexusquant.adapter.okx.service.OkxSpotProviderTransport;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OkxCredentialScopedSpotProviderTransportTest {

    @Test
    void bindsExactIdentityAndProductionEnvironmentWithoutSpringRuntimeWiring() {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<OkxPrivateEnvironment> environment = new AtomicReference<>();
        OkxPrivateCredentialExecutor executor = new OkxPrivateCredentialExecutor() {
            @Override
            public <T> T withActiveCredential(
                    Long ownerId,
                    Long exchangeAccountId,
                    String credentialType,
                    CredentialCallback<T> callback
            ) {
                throw new AssertionError("must use exact credential reference");
            }

            @Override
            public <T> T withActiveCredential(
                    Long ownerId,
                    Long exchangeAccountId,
                    Long credentialId,
                    String credentialType,
                    CredentialCallback<T> callback
            ) {
                assertEquals(7L, ownerId);
                assertEquals(9L, exchangeAccountId);
                assertEquals(42L, credentialId);
                assertEquals("OKX_API_V5", credentialType);
                calls.incrementAndGet();
                return callback.execute(new CredentialSession() {
                    @Override
                    public OkxPrivateReadResult execute(
                            OkxPrivateReadRequest request,
                            OkxPrivateEnvironment environment
                    ) {
                        throw new AssertionError("legacy read path is not expected");
                    }

                    @Override
                    public OkxSpotProviderTransport.OrderResponse queryOrder(
                            OkxSpotProviderTransport.OrderCommand command,
                            OkxPrivateEnvironment selectedEnvironment
                    ) {
                        environment.set(selectedEnvironment);
                        return new OkxSpotProviderTransport.OrderResponse(
                                new OkxSpotProviderTransport.ResponseMetadata(
                                        OkxSpotProviderOperation.QUERY_ORDER, 10, null,
                                        Instant.parse("2026-08-16T12:00:00Z")),
                                null, null);
                    }
                });
            }
        };
        UUID sessionId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        OkxCredentialScopedSpotProviderTransport transport = new OkxCredentialScopedSpotProviderTransport(
                executor, 7L, 9L, 42L, sessionId, List.of("BTC-USDT"));
        assertThrows(IllegalArgumentException.class,
                () -> new OkxCredentialScopedSpotProviderTransport(
                        executor, 7L, 9L, 42L, sessionId, List.of("BTC-USDT", "BTC-USDT")));

        transport.queryOrder(orderCommand(sessionId, "BTC-USDT"));

        assertEquals(1, calls.get());
        assertEquals(OkxPrivateEnvironment.PRODUCTION, environment.get());
        assertFalse(OkxCredentialScopedSpotProviderTransport.class.isAnnotationPresent(Component.class));

        assertThrows(IllegalArgumentException.class,
                () -> transport.queryOrder(orderCommand(sessionId, "ETH-USDT")));
        assertThrows(IllegalArgumentException.class,
                () -> transport.queryOrder(orderCommand(UUID.randomUUID(), "BTC-USDT")));
        assertEquals(1, calls.get(), "out-of-scope requests must fail before credential callback");
    }

    private static OkxSpotProviderTransport.OrderCommand orderCommand(UUID sessionId, String instrument) {
        return new OkxSpotProviderTransport.OrderCommand(
                "nq10000000000000000000000000000",
                instrument,
                new OkxSpotProviderTransport.TransportContext(
                        sessionId, "reference", "trace", "correlation",
                        Instant.parse("2026-08-16T12:00:00Z")),
                new OkxSpotProviderTransport.ResponseReadLimit(4096, 10));
    }
}
