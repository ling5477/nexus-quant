package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.marketdata.application.command.CreateMarketdataDatasetCommand;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetService;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.context.ConfigurableApplicationContext;

/** 隔离受控 OHLCV 经原 Dataset/V2 冻结；不创建策略、回测、SIM 或经济事实。 */
public final class FactorResearchBrowserFixtureMain {
    static UUID seed(ConfigurableApplicationContext app, String symbol, int count) {
        var observedAt = Instant.parse("2026-10-29T00:00:00Z");
        var bars = LongHistoricalResearchBrowserFixtureMain.bars(symbol, count).stream().map(bar ->
                new HistoricalBar(bar.exchangeCode(), bar.marketType(), bar.symbol(), bar.interval(),
                        bar.openTime(), bar.closeTime(), bar.openPrice(), bar.highPrice(), bar.lowPrice(), bar.closePrice(),
                        bar.volume(), bar.quoteVolume(), bar.tradeCount(), bar.qualityStatus(), bar.rawPayloadJson(), observedAt)).toList();
        app.getBean(MarketdataBarRepository.class).upsertBars(bars, "SYNTHETIC_FACTOR_RESEARCH", observedAt);
        var datasets = app.getBean(MarketdataDatasetService.class);
        var start = bars.getFirst().openTime();
        var dataset = datasets.createDataset(new CreateMarketdataDatasetCommand("Synthetic factor " + symbol + " " + count,
                "OKX", "SPOT", symbol, "1h", start, start.plusSeconds(count * 3600L).minusMillis(1), "experiment-owner"));
        datasets.buildDatasetSnapshot(dataset.datasetId());
        return dataset.datasetId();
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("ready and stop paths required");
        Path ready = Path.of(args[0]).toAbsolutePath(), stop = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(ready) || Files.exists(stop) || !ready.getParent().equals(stop.getParent()))
            throw new IllegalArgumentException("fresh fixture identity required");
        try (var db = TenantIsolationTestDatabase.create(null, System.getenv("TASK_EXPERIMENT_PG_URL"),
                "postgres", System.getenv("TASK_EXPERIMENT_PG_PASSWORD"))) {
            var owner = db.local("experiment-owner"); db.local("factor-other");
            db.demo("FACTOR_OTHER_TENANT", "factor-other-tenant");
            try (var app = LongHistoricalResearchBrowserFixtureMain.start(db)) {
                db.admin.update("UPDATE scheduled_job_controls SET enabled=false,next_run_at=NULL");
                UUID btc = db.as(owner, () -> seed(app, "BTC-USDT", 4320));
                UUID eth = db.as(owner, () -> seed(app, "ETH-USDT", 2160));
                var identity = new LinkedHashMap<String, Object>();
                identity.put("port", app.getWebServer().getPort()); identity.put("pid", ProcessHandle.current().pid());
                identity.put("username", "experiment-owner"); identity.put("password", TenantIsolationTestDatabase.PASSWORD);
                identity.put("btcDatasetId", btc); identity.put("ethDatasetId", eth);
                identity.put("dataSource", "SYNTHETIC_CONTROLLED_JAVA_V2");
                identity.put("database", db.url());
                Files.writeString(ready.resolveSibling("business-fingerprint-initial.json"), FactorResearchPostgresIntegrationTest.fingerprint(db));
                Files.writeString(ready, LongHistoricalResearchBrowserFixtureMain.JSON.writeValueAsString(identity), StandardOpenOption.CREATE_NEW);
                long deadline = System.nanoTime() + java.time.Duration.ofMinutes(50).toNanos();
                while (!Files.exists(stop) && System.nanoTime() < deadline) Thread.sleep(200);
                Files.writeString(ready.resolveSibling("business-fingerprint-final.json"), FactorResearchPostgresIntegrationTest.fingerprint(db));
            }
        }
    }
}
