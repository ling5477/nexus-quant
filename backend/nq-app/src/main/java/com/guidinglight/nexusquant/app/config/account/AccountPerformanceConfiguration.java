package com.guidinglight.nexusquant.app.config.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.service.AccountPerformanceService;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.account.infra.jdbc.JdbcAccountPerformanceRepository;
import com.guidinglight.nexusquant.common.currency.CurrencyValuationService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Configuration(proxyBeanMethods = false)
public class AccountPerformanceConfiguration {
    @Bean
    public JdbcAccountPerformanceRepository accountPerformanceRepository(JdbcTemplate jdbc) {
        return new JdbcAccountPerformanceRepository(jdbc);
    }

    @Bean
    public AccountPerformanceService accountPerformanceService(
            ExchangeAccountRepository accounts,
            JdbcAccountPerformanceRepository repository,
            ObjectMapper mapper,
            CurrencyValuationService valuations) {
        return new AccountPerformanceService(accounts, repository, mapper, valuations);
    }

    @Bean
    @ConditionalOnProperty(
            name = "nq.account.performance.sampling-enabled",
            havingValue = "true",
            matchIfMissing = true)
    public Collector accountPerformanceCollector(
            AccountPerformanceService service, JdbcAccountPerformanceRepository repository) {
        return new Collector(service, repository);
    }

    /** 每分钟轮转最多50个账户，每账户最多十五分钟采样一次；失败只影响自身测量。 */
    public static final class Collector implements SmartLifecycle {
        private static final Logger LOG = LoggerFactory.getLogger(Collector.class);
        private final AccountPerformanceService service;
        private final JdbcAccountPerformanceRepository repository;
        private long cursor;
        private volatile boolean running;
        private ScheduledExecutorService executor;
        private ScheduledFuture<?> task;

        Collector(AccountPerformanceService service, JdbcAccountPerformanceRepository repository) {
            this.service = service;
            this.repository = repository;
        }

        @Override
        public synchronized void start() {
            if (running) return;
            // 统计扫描不能占用全局调度线程，否则慢查询可能延误正式SIM撮合与恢复。
            executor =
                    Executors.newSingleThreadScheduledExecutor(
                            command -> {
                                var thread = new Thread(command, "account-equity-sampling");
                                thread.setDaemon(true);
                                return thread;
                            });
            running = true;
            task =
                    executor.scheduleWithFixedDelay(
                            () -> {
                                try {
                                    collect();
                                } catch (RuntimeException ex) {
                                    LOG.warn(
                                            "Account equity sampling failed: {}",
                                            ex.getClass().getSimpleName());
                                }
                            },
                            10,
                            60,
                            TimeUnit.SECONDS);
        }

        @Override
        public synchronized void stop() {
            running = false;
            if (task != null) task.cancel(true);
            if (executor != null) {
                executor.shutdownNow();
                try {
                    if (!executor.awaitTermination(10, TimeUnit.SECONDS))
                        LOG.warn("Account equity sampling did not stop before timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public int getPhase() {
            return Integer.MAX_VALUE - 100;
        }

        public void collect() {
            var candidates = repository.samplingCandidates(cursor, 50);
            if (candidates.isEmpty()) {
                cursor = 0;
                candidates = repository.samplingCandidates(0, 50);
            }
            int failures = 0;
            for (var account : candidates) {
                cursor = account.exchangeAccountId();
                try {
                    service.sample(account.exchangeAccountId(), "USDT", Instant.now());
                } catch (RuntimeException ex) {
                    failures++;
                }
            }
            repository.pruneDerivedHistory(Instant.now());
            if (failures > 0)
                LOG.warn(
                        "Account equity measurements failed for {} accounts; no economic facts were"
                                + " changed",
                        failures);
        }
    }
}
