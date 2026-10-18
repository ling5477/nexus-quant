package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.ledger.service.port.TradeLedgerPort;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.net.URI;

/** 仅供临时PG重启证明：从持久化成交重放原身份，不读取真实凭据、不联网或创建新订单。 */
public final class SignedFeeLedgerRestartProbeMain {
    private SignedFeeLedgerRestartProbeMain() {}

    public static void main(String[] args) throws Exception {
        String url = System.getenv("NQ_SIGNED_FEE_DB_URL");
        URI uri = URI.create(url.substring("jdbc:".length()));
        if (!url.startsWith("jdbc:postgresql://127.0.0.1:")
                || !uri.getPath().matches("/nq_baseline_test_[a-f0-9]{32}")
                || uri.getRawQuery() != null
                || uri.getUserInfo() != null
                || args.length != 1
                || !args[0].matches("trd-[a-f0-9-]{36}")) {
            throw new IllegalArgumentException(
                    "owned temporary database and trade identity required");
        }
        var source =
                new DriverManagerDataSource(
                        url,
                        System.getenv("NQ_SIGNED_FEE_DB_USER"),
                        System.getenv("NQ_SIGNED_FEE_DB_PASSWORD"));
        try (var context = SignedFeeLedgerPostgresIntegrationTest.context(source)) {
            var request =
                    SignedFeeLedgerPostgresIntegrationTest.request(
                            context.getBean(JdbcTemplate.class), args[0]);
            var result = context.getBean(TradeLedgerPort.class).postTrade(request);
            if (!result.posted() || !result.idempotentHit())
                throw new IllegalStateException("original committed result must be idempotent");
            System.out.println("SIGNED_FEE_REPLAY=IDEMPOTENT_HIT");
        }
    }
}
