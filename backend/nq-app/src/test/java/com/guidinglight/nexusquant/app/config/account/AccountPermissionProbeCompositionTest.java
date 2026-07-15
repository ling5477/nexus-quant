package com.guidinglight.nexusquant.app.config.account;

import com.guidinglight.nexusquant.account.domain.port.ExchangeCredentialPermissionProbePort;
import com.guidinglight.nexusquant.account.infra.gatew.JdbcOkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.gatew.OkxPrivateCredentialExecutor;
import com.guidinglight.nexusquant.account.infra.probe.NoRealExchangeCredentialPermissionProbePort;
import com.guidinglight.nexusquant.account.infra.probe.OkxRealReadonlyPermissionProbePort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccountPermissionProbeCompositionTest {

    private final AccountModuleConfiguration configuration = new AccountModuleConfiguration();
    private final OkxPrivateCredentialExecutor executor = mock(JdbcOkxPrivateCredentialExecutor.class);

    @Test
    void defaultsToNoRealWhenPermissionFlagOrExecutorIsMissing() {
        GateWOkxPermissionProbeProperties disabled = properties(false, "203.0.113.8");
        assertInstanceOf(NoRealExchangeCredentialPermissionProbePort.class,
                select(provider(executor), disabled, safeEnvironment()));
        assertInstanceOf(NoRealExchangeCredentialPermissionProbePort.class,
                select(provider(null), properties(true, "203.0.113.8"), safeEnvironment()));
    }

    @Test
    void selectsRealReadonlyPortOnlyWhenEverySafetyFactIsExplicit() {
        ExchangeCredentialPermissionProbePort selected = select(
                provider(executor),
                properties(true, "203.0.113.8"),
                safeEnvironment()
        );

        assertInstanceOf(OkxRealReadonlyPermissionProbePort.class, selected);
    }

    @Test
    void fallsBackToNoRealForInvalidIpOrAnyConflictingSafetyFlag() {
        assertInstanceOf(NoRealExchangeCredentialPermissionProbePort.class,
                select(provider(executor), properties(true, null), safeEnvironment()));
        assertInstanceOf(NoRealExchangeCredentialPermissionProbePort.class,
                select(provider(executor), properties(true, "example.com"), safeEnvironment()));

        for (String unsafe : new String[]{
                "nq.env-safety.live-enabled",
                "nq.env-safety.real-client-enabled",
                "nq.env-safety.real-provider-enabled",
                "nq.env-safety.no-outbound"
        }) {
            MockEnvironment environment = safeEnvironment().withProperty(unsafe, "true");
            assertInstanceOf(NoRealExchangeCredentialPermissionProbePort.class,
                    select(provider(executor), properties(true, "203.0.113.8"), environment));
        }
        MockEnvironment realExchangeDisabled = safeEnvironment()
                .withProperty("nq.env-safety.real-exchange-enabled", "false");
        assertInstanceOf(NoRealExchangeCredentialPermissionProbePort.class,
                select(provider(executor), properties(true, "203.0.113.8"), realExchangeDisabled));
    }

    private ExchangeCredentialPermissionProbePort select(
            ObjectProvider<OkxPrivateCredentialExecutor> provider,
            GateWOkxPermissionProbeProperties properties,
            MockEnvironment environment
    ) {
        return configuration.exchangeCredentialPermissionProbePort(provider, properties, environment);
    }

    private static GateWOkxPermissionProbeProperties properties(boolean enabled, String expectedIp) {
        GateWOkxPermissionProbeProperties properties = new GateWOkxPermissionProbeProperties();
        properties.setEnabled(enabled);
        properties.setExpectedIp(expectedIp);
        return properties;
    }

    private static MockEnvironment safeEnvironment() {
        return new MockEnvironment()
                .withProperty("nq.env-safety.real-exchange-enabled", "true")
                .withProperty("nq.env-safety.live-enabled", "false")
                .withProperty("nq.env-safety.real-client-enabled", "false")
                .withProperty("nq.env-safety.real-provider-enabled", "false")
                .withProperty("nq.env-safety.no-outbound", "false");
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<OkxPrivateCredentialExecutor> provider(OkxPrivateCredentialExecutor executor) {
        ObjectProvider<OkxPrivateCredentialExecutor> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(executor);
        return provider;
    }
}
