package com.guidinglight.nexusquant.app.config.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.http.HttpClient;

/** 只验证composition root的默认关闭和精确开关；不取得材料、不连接数据库或交易所。 */
class BinanceAccountFeeConfigurationTest {
    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(BinanceAccountFeeConfiguration.class)
                .withBean(
                        JdbcTemplate.class,
                        () ->
                                new JdbcTemplate(
                                        new DriverManagerDataSource(
                                                "jdbc:postgresql://127.0.0.1:1/unconnected_fixture")))
                .withBean(
                        ExchangeAccountRepository.class,
                        () -> mock(ExchangeAccountRepository.class))
                .withBean(
                        ExchangeAccountCredentialRepository.class,
                        () -> mock(ExchangeAccountCredentialRepository.class))
                .withBean(KillSwitchService.class, () -> mock(KillSwitchService.class))
                .withBean(
                        AccountCredentialRuntimeProperties.class,
                        () -> {
                            var value = new AccountCredentialRuntimeProperties();
                            value.setMasterKey("synthetic-config-test-master-key");
                            return value;
                        });
    }

    private ApplicationContextRunner configured() {
        return runner().withInitializer(
                        context ->
                                context.getEnvironment()
                                        .setActiveProfiles("binance-private-readonly"))
                .withPropertyValues(
                        "nq.account-fees.binance.enabled=true",
                        "nq.account-fees.binance.owner-user-id=1",
                        "nq.account-fees.binance.exchange-account-id=2",
                        "nq.account-fees.binance.credential-id=3",
                        "nq.account-fees.binance.credential-type=BINANCE_HMAC",
                        "nq.account-fees.binance.allowed-instruments=BTC-USDT",
                        "nq.account-fees.binance.permission-scope=READ_ONLY",
                        "nq.env-safety.ci=false",
                        "nq.env-safety.live-enabled=false",
                        "nq.env-safety.real-exchange-enabled=false",
                        "nq.env-safety.real-client-enabled=false",
                        "nq.env-safety.real-provider-enabled=false",
                        "nq.env-safety.no-outbound=false");
    }

    @Test
    void normalApplicationNeverConstructsThePrivateReader() {
        runner().run(
                        context -> {
                            assertThat(context).doesNotHaveBean(AccountFeeReadPort.class);
                            assertThat(context).doesNotHaveBean(HttpClient.class);
                        });
    }

    @Test
    void anyUnsafeOrDisabledFlagPreventsReaderConstruction() {
        for (String override :
                new String[] {
                    "nq.env-safety.no-outbound=true",
                    "nq.env-safety.live-enabled=true",
                    "nq.env-safety.real-provider-enabled=true",
                    "nq.account-fees.binance.enabled=false"
                })
            configured()
                    .withPropertyValues(override)
                    .run(
                            context -> {
                                assertThat(context).doesNotHaveBean(AccountFeeReadPort.class);
                                assertThat(context).doesNotHaveBean(HttpClient.class);
                            });
    }

    @Test
    void explicitReadOnlyProfileBuildsThePortWithoutResolvingMaterialOrConnecting() {
        configured()
                .run(
                        context -> {
                            assertThat(context).hasSingleBean(AccountFeeReadPort.class);
                            assertThat(context).hasSingleBean(HttpClient.class);
                        });
    }
}
