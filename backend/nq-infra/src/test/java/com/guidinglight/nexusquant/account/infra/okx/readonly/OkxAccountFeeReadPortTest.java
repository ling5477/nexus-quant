package com.guidinglight.nexusquant.account.infra.okx.readonly;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.guidinglight.nexusquant.account.application.exception.AccountFeeSyncException;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxIpAllowlistStatus;
import com.guidinglight.nexusquant.adapter.okx.auth.OkxPrivateEnvironment;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateFeeFact;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadOperation;
import com.guidinglight.nexusquant.adapter.okx.privateread.model.OkxPrivateReadResult;
import com.guidinglight.nexusquant.livecontrol.deployment.policy.ScopedCredentialCapabilityPolicy;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchScope;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** typed session替身只收集GET，不解密、不读取凭据、不连接真实供应方。 */
class OkxAccountFeeReadPortTest {
    private static final Instant NOW = Instant.parse("2026-10-13T04:00:00Z");
    private final KillSwitchService kill = mock(KillSwitchService.class);
    private final CapturingExecutor executor = new CapturingExecutor();

    private OkxAccountFeeReadPort source() {
        return new OkxAccountFeeReadPort(
                executor,
                kill,
                new ScopedCredentialCapabilityPolicy(Duration.ofHours(1)),
                ScopedCredentialCapabilityPolicy.PermissionScope.READ_ONLY,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "127.0.0.1",
                7,
                8,
                9,
                Set.of("BTC-USDT"));
    }

    private static ExchangeAccountSummary account(String environment) {
        return new ExchangeAccountSummary(
                8L, 42L, 7L, "OKX", environment, "synthetic fee source", null, false, "ACTIVE");
    }

    private static ExchangeAccountCredentialSummary credential(String permission) {
        return new ExchangeAccountCredentialSummary(
                9L,
                8L,
                "OKX_API_V5",
                "masked",
                "ACTIVE",
                "VERIFIED",
                true,
                null,
                null,
                null,
                NOW,
                null,
                NOW,
                "SUCCEEDED",
                permission,
                false,
                "PASSED",
                0,
                NOW.minusSeconds(30),
                null);
    }

    private static KillSwitchSnapshot engaged(long version) {
        return new KillSwitchSnapshot(
                KillSwitchScope.GLOBAL_TRADING,
                KillSwitchStatus.ENGAGED,
                version,
                "TEST",
                "TEST",
                NOW,
                NOW,
                "trace");
    }

    @Test
    void constructionAndAvailabilityHaveNoPrivateSideEffects() {
        assertTrue(source().supports(account("LIVE"), "BTC-USDT"));
        assertFalse(source().supports(account("LIVE"), "ETH-USDT"));
        assertEquals(0, executor.calls);
        verifyNoInteractions(kill);
    }

    @Test
    void readsOnlyConfigurationAndExactInstrumentFeeWithOriginalSigns() {
        when(kill.snapshot()).thenReturn(engaged(1));
        var quote = source().read(account("LIVE"), credential("READ_ONLY"), "BTC-USDT");
        assertEquals(
                List.of(
                        OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ,
                        OkxPrivateReadOperation.OKX_SPOT_ACCOUNT_FEE_READ),
                executor.operations);
        assertEquals(OkxPrivateEnvironment.PRODUCTION, executor.environment);
        assertEquals(0, new BigDecimal("-0.0002").compareTo(quote.makerCostRate()));
        assertEquals(0, new BigDecimal("0.001").compareTo(quote.takerCostRate()));
    }

    @Test
    void venueDemoUsesSimWithoutChangingTradeAuthority() {
        when(kill.snapshot()).thenReturn(engaged(1));
        source().read(account("SIM"), credential("READ_ONLY"), "BTC-USDT");
        assertEquals(OkxPrivateEnvironment.DEMO, executor.environment);
    }

    @Test
    void wrongAccountCredentialOrInstrumentRejectBeforeExecutor() {
        var other =
                new ExchangeAccountSummary(
                        18L, 42L, 7L, "OKX", "LIVE", "other", null, false, "ACTIVE");
        assertThrows(
                AccountFeeSyncException.class,
                () -> source().read(other, credential("READ_ONLY"), "BTC-USDT"));
        assertThrows(
                AccountFeeSyncException.class,
                () -> source().read(account("LIVE"), credential("READ_ONLY"), "ETH-USDT"));
        assertEquals(0, executor.calls);
        verifyNoInteractions(kill);
    }

    @Test
    void disallowedMetadataPermissionAndMissingKillRejectBeforeDecrypt() {
        when(kill.snapshot()).thenReturn(engaged(1));
        assertThrows(
                AccountFeeSyncException.class,
                () -> source().read(account("LIVE"), credential("TRADE"), "BTC-USDT"));
        when(kill.snapshot()).thenReturn(null);
        assertThrows(
                AccountFeeSyncException.class,
                () -> source().read(account("LIVE"), credential("READ_ONLY"), "BTC-USDT"));
        assertEquals(0, executor.calls);
    }

    @Test
    void remotePermissionOrIpMismatchPreventsFeeCall() {
        when(kill.snapshot()).thenReturn(engaged(1));
        executor.permissions = Set.of("READ_ONLY", "WITHDRAW");
        assertThrows(
                AccountFeeSyncException.class,
                () -> source().read(account("LIVE"), credential("READ_ONLY"), "BTC-USDT"));
        assertEquals(
                List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ),
                executor.operations);
        executor.operations.clear();
        executor.permissions = Set.of("READ_ONLY");
        executor.ip = OkxIpAllowlistStatus.MISMATCHED;
        assertThrows(
                AccountFeeSyncException.class,
                () -> source().read(account("LIVE"), credential("READ_ONLY"), "BTC-USDT"));
        assertEquals(
                List.of(OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ),
                executor.operations);
    }

    @Test
    void killVersionChangeAfterFeeReadRejectsObservation() {
        when(kill.snapshot()).thenReturn(engaged(1));
        executor.changeKill = true;
        assertThrows(
                AccountFeeSyncException.class,
                () -> source().read(account("LIVE"), credential("READ_ONLY"), "BTC-USDT"));
    }

    @Test
    void partialOrWrongInstrumentFeeCannotBecomeActualSnapshot() {
        when(kill.snapshot()).thenReturn(engaged(1));
        executor.instrument = "ETH-USDT";
        assertThrows(
                AccountFeeSyncException.class,
                () -> source().read(account("LIVE"), credential("READ_ONLY"), "BTC-USDT"));
        executor.instrument = "BTC-USDT";
        executor.complete = false;
        assertThrows(
                AccountFeeSyncException.class,
                () -> source().read(account("LIVE"), credential("READ_ONLY"), "BTC-USDT"));
    }

    @Test
    void expectedIpIsValidatedBeforeAnyCredentialScopeCanOpen() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new OkxAccountFeeReadPort(
                                executor,
                                kill,
                                new ScopedCredentialCapabilityPolicy(Duration.ofHours(1)),
                                ScopedCredentialCapabilityPolicy.PermissionScope.READ_ONLY,
                                Clock.fixed(NOW, ZoneOffset.UTC),
                                "untrusted.example",
                                7,
                                8,
                                9,
                                Set.of("BTC-USDT")));
        assertEquals(0, executor.calls);
    }

    private final class CapturingExecutor implements OkxPrivateCredentialExecutor {
        int calls;
        boolean changeKill;
        boolean complete = true;
        String instrument = "BTC-USDT";
        Set<String> permissions = Set.of("READ_ONLY");
        OkxIpAllowlistStatus ip = OkxIpAllowlistStatus.MATCHED;
        OkxPrivateEnvironment environment;
        List<OkxPrivateReadOperation> operations = new ArrayList<>();

        @Override
        public <T> T withActiveCredential(
                Long owner, Long account, String type, CredentialCallback<T> callback) {
            throw new AssertionError("inexact credential forbidden");
        }

        @Override
        public <T> T withActiveCredential(
                Long owner,
                Long account,
                Long credential,
                String type,
                CredentialCallback<T> callback) {
            assertEquals(7L, owner);
            assertEquals(8L, account);
            assertEquals(9L, credential);
            assertEquals("OKX_API_V5", type);
            calls++;
            return callback.execute(
                    (request, env) -> {
                        environment = env;
                        operations.add(request.operation());
                        if (request.operation()
                                == OkxPrivateReadOperation.OKX_ACCOUNT_CONFIGURATION_READ)
                            return new OkxPrivateReadResult(
                                    request.operation(),
                                    permissions,
                                    0,
                                    true,
                                    List.of(),
                                    List.of(),
                                    true,
                                    ip,
                                    NOW,
                                    null,
                                    List.of(),
                                    null);
                        assertEquals(
                                OkxPrivateReadOperation.OKX_SPOT_ACCOUNT_FEE_READ,
                                request.operation());
                        assertEquals("BTC-USDT", request.instrumentId());
                        if (changeKill) when(kill.snapshot()).thenReturn(engaged(2));
                        return new OkxPrivateReadResult(
                                request.operation(),
                                Set.of(),
                                0,
                                complete,
                                List.of(),
                                List.of(),
                                false,
                                OkxIpAllowlistStatus.NOT_CHECKED,
                                NOW,
                                null,
                                List.of(),
                                new OkxPrivateFeeFact(
                                        instrument,
                                        new BigDecimal("0.0002"),
                                        new BigDecimal("-0.001"),
                                        "Lv1",
                                        NOW));
                    });
        }
    }
}
