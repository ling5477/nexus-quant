package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.ledger.service.port.SimAssetFundingPort;
import com.guidinglight.nexusquant.auth.application.service.TenantIdentityResolver;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import org.springframework.jdbc.core.JdbcTemplate;

import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** 独立 JVM 重读冻结资金身份并原样重放；不建立内存余额，也不连接共享验收库。 */
public final class SimAssetFundingRestartProbeMain {
    private SimAssetFundingRestartProbeMain() {}

    public static void main(String[] args) {
        var source =
                new DriverManagerDataSource(
                        System.getenv("NQ_SIM_ASSET_FUNDING_DB_URL"),
                        System.getenv("NQ_SIM_ASSET_FUNDING_DB_USER"),
                        System.getenv("NQ_SIM_ASSET_FUNDING_DB_PASSWORD"));
        // 每个新进程从持久化用户和成员关系重新解析身份，不能依赖父进程 ThreadLocal。
        var identity = new TenantIdentityResolver(new JdbcAuthUserRepository(new JdbcTemplate(source)))
                .resolve(System.getenv("NQ_SIM_ASSET_FUNDING_USERNAME")).tenant();
        try (var scope = TenantContext.open(identity);
                var app = SimAssetFundingPostgresIntegrationTest.context(source)) {
            SimAssetFundingPort funding = app.getBean(SimAssetFundingPort.class);
            long account = Long.parseLong(args[0]);
            String run = args[1];
            var plan = funding.findPlan(account, run).orElseThrow();
            var cash = funding.assetBalance(account, "USDT");
            var asset = funding.assetBalance(account, plan.assetCurrency());
            funding.fundOnce(account, run, plan, "restart-same-funding-identity");
            if (cash.compareTo(funding.assetBalance(account, "USDT")) != 0
                    || asset.compareTo(funding.assetBalance(account, plan.assetCurrency())) != 0) {
                throw new IllegalStateException("SIM funding replay changed canonical balance");
            }
            System.out.println(
                    "SIM_ASSET_FUNDING_REPLAY=UNCHANGED PID=" + ProcessHandle.current().pid());
        }
    }
}
