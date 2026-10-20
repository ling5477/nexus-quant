package com.guidinglight.nexusquant.app.config.account;

import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.binance.readonly.JdbcBinanceAccountFeeReadPort;
import com.guidinglight.nexusquant.adapter.binance.fee.BinanceCommissionHttpReader;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.stream.Collectors;

/** 仅显式只读profile和全部安全开关匹配时装配；构造不解密/联网，不创建交易adapter或后台同步。 */
@Configuration
@Profile("binance-private-readonly")
@ConditionalOnProperty(
        prefix = "nq.account-fees.binance",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false)
@ConditionalOnProperty(
        prefix = "nq.env-safety",
        name = {
            "ci",
            "live-enabled",
            "real-exchange-enabled",
            "real-client-enabled",
            "real-provider-enabled",
            "no-outbound"
        },
        havingValue = "false",
        matchIfMissing = false)
public class BinanceAccountFeeConfiguration {
    /** HTTP客户端随上下文关闭，避免常驻连接/线程泄漏；不使用默认代理或重定向。 */
    @Bean(destroyMethod = "close")
    public HttpClient binanceAccountFeeHttpClient() {
        return BinanceCommissionHttpReader.directHttpClient();
    }

    /** 只支持主网LIVE来源只读，不代表LIVE执行授权；测试网缺必要权限接口，不能挪用主网费率。 */
    @Bean
    public AccountFeeReadPort binanceAccountFeeReadPort(
            HttpClient binanceAccountFeeHttpClient,
            JdbcTemplate jdbc,
            AccountCredentialRuntimeProperties secrets,
            ExchangeAccountRepository accounts,
            ExchangeAccountCredentialRepository credentials,
            KillSwitchService kill,
            Environment environment) {
        String prefix = "nq.account-fees.binance.";
        var reader =
                new BinanceCommissionHttpReader(
                        binanceAccountFeeHttpClient,
                        URI.create("https://api.binance.com"),
                        BinanceCommissionHttpReader.Environment.PRODUCTION,
                        Duration.ofSeconds(4),
                        Clock.systemUTC());
        return new JdbcBinanceAccountFeeReadPort(
                jdbc,
                secrets.getMasterKey(),
                reader,
                accounts,
                credentials,
                kill,
                environment.getRequiredProperty(prefix + "owner-user-id", Long.class),
                environment.getRequiredProperty(prefix + "exchange-account-id", Long.class),
                environment.getRequiredProperty(prefix + "credential-id", Long.class),
                environment.getRequiredProperty(prefix + "credential-type"),
                Arrays.stream(
                                environment
                                        .getRequiredProperty(prefix + "allowed-instruments")
                                        .split(","))
                        .map(String::trim)
                        .collect(Collectors.toUnmodifiableSet()),
                BinanceCommissionHttpReader.PermissionScope.valueOf(
                        environment.getRequiredProperty(prefix + "permission-scope")));
    }
}
