package com.guidinglight.nexusquant.trading.infra.reconciliation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.guidinglight.nexusquant.account.infra.gatew.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateOrderSnapshot;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadRequest;
import com.guidinglight.nexusquant.adapter.okx.service.OkxPrivateReadResult;
import com.guidinglight.nexusquant.trading.application.reconciliation.ReconciliationRequest;
import com.guidinglight.nexusquant.trading.application.reconciliation.RemoteSnapshotBatch;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class ReadOnlyReconciliationAdaptersTest {
    private static final Instant NOW = Instant.parse("2026-07-14T08:00:00Z");

    @Test
    @SuppressWarnings("unchecked")
    void localAdapterExecutesOnlyBoundedSelectWithAccountEnvironmentSymbolsAndWindow() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        AtomicReference<String> sql = new AtomicReference<>();
        AtomicReference<MapSqlParameterSource> parameters = new AtomicReference<>();
        when(jdbc.query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class)))
                .thenAnswer(invocation -> {
                    sql.set(invocation.getArgument(0));
                    parameters.set(invocation.getArgument(1));
                    return List.of();
                });

        List<?> result = new JdbcLocalOrderSnapshotReadAdapter(jdbc).read(request());

        assertTrue(result.isEmpty());
        String normalizedSql = sql.get().toLowerCase();
        assertTrue(normalizedSql.stripLeading().startsWith("select"));
        assertFalse(normalizedSql.contains("insert into"));
        assertFalse(normalizedSql.contains(" update "));
        assertFalse(normalizedSql.contains("delete from"));
        assertTrue(normalizedSql.contains("limit :boundedlimit"));
        assertEquals(1L, parameters.get().getValue("accountId"));
        assertEquals("OKX", parameters.get().getValue("exchangeCode"));
        assertEquals("SIM", parameters.get().getValue("tradeEnvironment"));
        assertEquals(100, parameters.get().getValue("boundedLimit"));
        verify(jdbc).query(anyString(), any(MapSqlParameterSource.class), any(RowMapper.class));
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void remoteAdapterUsesConfigFirstAndExactlyThreeTypedReadsWithoutRealCredentialOrNetwork() {
        FakeCredentialExecutor executor = new FakeCredentialExecutor(Set.of("READ_ONLY"));
        OkxReadOnlyReconciliationRemoteAdapter adapter = new OkxReadOnlyReconciliationRemoteAdapter(
                executor, "OKX_API_V5", OkxPrivateEnvironment.DEMO
        );

        RemoteSnapshotBatch result = adapter.read(request());

        assertTrue(result.readOnlyPermissionConfirmed());
        assertTrue(result.complete());
        assertEquals(1, result.orders().size());
        assertEquals(1, result.pageCount());
        assertEquals(List.of(
                OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                OkxPrivateReadOperation.OKX_SPOT_OPEN_ORDERS_READ,
                OkxPrivateReadOperation.OKX_SPOT_ORDER_HISTORY_READ,
                OkxPrivateReadOperation.OKX_SPOT_RECENT_FILLS_READ
        ), executor.operations);
        assertEquals(4, executor.fakeCalls.get());
    }

    @Test
    void remoteAdapterRejectsNonReadPermissionBeforeAnyOrderOrFillRead() {
        FakeCredentialExecutor executor = new FakeCredentialExecutor(Set.of("READ_ONLY", "TRADE"));
        RemoteSnapshotBatch result = new OkxReadOnlyReconciliationRemoteAdapter(
                executor, "OKX_API_V5", OkxPrivateEnvironment.DEMO
        ).read(request());

        assertFalse(result.readOnlyPermissionConfirmed());
        assertFalse(result.complete());
        assertTrue(result.orders().isEmpty());
        assertEquals(List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ), executor.operations);
    }

    private static ReconciliationRequest request() {
        return new ReconciliationRequest(
                1, 7, 9, "OKX", "SPOT", "SIM", List.of("BTC-USDT"),
                NOW.minusSeconds(3600), NOW, 1, 100
        );
    }

    private static final class FakeCredentialExecutor implements OkxPrivateCredentialExecutor {
        private final Set<String> permissions;
        private final List<OkxPrivateReadOperation> operations = new ArrayList<>();
        private final AtomicInteger fakeCalls = new AtomicInteger();

        private FakeCredentialExecutor(Set<String> permissions) {
            this.permissions = permissions;
        }

        @Override
        public <T> T withActiveCredential(
                Long ownerId,
                Long exchangeAccountId,
                String credentialType,
                CredentialCallback<T> callback
        ) {
            assertEquals(7L, ownerId);
            assertEquals(9L, exchangeAccountId);
            assertEquals("OKX_API_V5", credentialType);
            return callback.execute(this::executeFake);
        }

        private OkxPrivateReadResult executeFake(
                OkxPrivateReadRequest request,
                OkxPrivateEnvironment environment
        ) {
            fakeCalls.incrementAndGet();
            operations.add(request.operation());
            if (request.operation() == OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ) {
                return new OkxPrivateReadResult(request.operation(), permissions, 0, true,
                        List.of(), List.of(), NOW);
            }
            if (request.operation() == OkxPrivateReadOperation.OKX_SPOT_OPEN_ORDERS_READ) {
                OkxPrivateOrderSnapshot order = new OkxPrivateOrderSnapshot(
                        "exchange-1", "client-1", "BTC-USDT", "buy", "limit",
                        new BigDecimal("1.00"), new BigDecimal("2"), BigDecimal.ZERO,
                        "live", NOW, request.operation()
                );
                return new OkxPrivateReadResult(request.operation(), Set.of(), 0, true,
                        List.of(order), List.of(), NOW);
            }
            return new OkxPrivateReadResult(request.operation(), Set.of(), 0, true,
                    List.of(), List.of(), NOW);
        }
    }
}
