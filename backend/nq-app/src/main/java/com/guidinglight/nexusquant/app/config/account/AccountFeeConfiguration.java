package com.guidinglight.nexusquant.app.config.account;

import com.guidinglight.nexusquant.account.application.service.AccountFeeModelService;
import com.guidinglight.nexusquant.account.application.service.AccountFeeService;
import com.guidinglight.nexusquant.account.application.service.RoutingAccountFeeReadPort;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcAccountFeeRepository;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;

/**
 * 普通产品环境可读取已有证据；缺少显式受控私有读取装配时同步保持关闭。
 */
@Configuration
public class AccountFeeConfiguration {
    @Bean
    public AccountFeeRepository accountFeeRepository(JdbcTemplate jdbc) {
        return new JdbcAccountFeeRepository(jdbc);
    }

    /**
     * 冻结只读取已提交来源；这里不装配私有联网端口或交易执行器。
     */
    @Bean
    public AccountFeeModelService accountFeeModelService(
            AccountFeeRepository observations,
            ExchangeAccountRepository accounts,
            ExchangeAccountCredentialRepository credentials) {
        return new AccountFeeModelService(observations, accounts, credentials);
    }

    @Bean
    public AccountFeeService accountFeeService(
            ExchangeAccountRepository accounts,
            ExchangeAccountCredentialRepository credentials,
            AccountFeeRepository repository,
            ObjectProvider<AccountFeeReadPort> source) {
        return new AccountFeeService(
                accounts,
                credentials,
                repository,
                new RoutingAccountFeeReadPort(source.orderedStream().toList()),
                Clock.systemUTC());
    }
}
