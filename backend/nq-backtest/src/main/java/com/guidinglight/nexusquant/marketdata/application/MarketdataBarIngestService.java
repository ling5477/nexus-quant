package com.guidinglight.nexusquant.marketdata.application;

import com.guidinglight.nexusquant.marketdata.application.command.FixtureMarketdataIngestionCommand;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalDatasetSpec;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.marketdata.domain.MarketdataBarUpsertStats;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataBarRepository;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MarketdataBarIngestService 提供 RC1-5-A 的 fixture -> DB 正式 ingest 入口。
 * <p>
 * Why:
 * 当前项目已经具备 `marketdata_bars` 的正式查询表，但没有任何真实写入路径。
 * 本服务把“读取注册 fixture + 按唯一键幂等 upsert + 返回统计摘要”收口到 application 层，
 * 使 controller 只负责协议映射，不直接参与文件解析或 SQL 写入。
 */
@Service
public class MarketdataBarIngestService {

    private static final String INGEST_SOURCE = "FIXTURE_SYNC";

    private final FixtureMarketdataRegistry fixtureMarketdataRegistry;
    private final MarketdataBarRepository marketdataBarRepository;
    private final Clock clock;

    /**
     * 显式指定运行时构造器，避免测试专用 Clock 构造器干扰 Spring 自动装配。
     * Why:
     * 本服务在单测里需要固定 Clock 保证统计时间戳稳定，运行时则必须固定走完整依赖注入构造器，
     * 否则容器会退回默认实例化路径并在启动期失败。
     */
    @Autowired
    public MarketdataBarIngestService(
            FixtureMarketdataRegistry fixtureMarketdataRegistry,
            MarketdataBarRepository marketdataBarRepository
    ) {
        this(fixtureMarketdataRegistry, marketdataBarRepository, Clock.systemUTC());
    }

    MarketdataBarIngestService(
            FixtureMarketdataRegistry fixtureMarketdataRegistry,
            MarketdataBarRepository marketdataBarRepository,
            Clock clock
    ) {
        this.fixtureMarketdataRegistry = Objects.requireNonNull(
                fixtureMarketdataRegistry,
                "fixtureMarketdataRegistry must not be null"
        );
        this.marketdataBarRepository = Objects.requireNonNull(
                marketdataBarRepository,
                "marketdataBarRepository must not be null"
        );
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 从注册 fixture 导入一段 bars 到 `marketdata_bars`。
     */
    @Transactional
    public MarketdataFixtureIngestionResult ingestFixture(FixtureMarketdataIngestionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        FixtureMarketdataDataset dataset = fixtureMarketdataRegistry.require(requireText(command.fixtureId(), "fixtureId"));
        String exchangeCode = normalizeExchangeCode(command.exchangeCode());
        String symbol = normalizeSymbol(command.symbol());
        BarInterval interval = normalizeInterval(command.interval());
        if (!exchangeCode.equals(dataset.exchangeCode())
                || !symbol.equals(dataset.symbol())
                || interval != dataset.interval()) {
            throw new IllegalArgumentException("requested fixture scope does not match registered dataset");
        }
        Instant startTime = Objects.requireNonNull(command.startTime(), "startTime must not be null");
        Instant endTime = Objects.requireNonNull(command.endTime(), "endTime must not be null");
        if (endTime.isBefore(startTime)) {
            throw new IllegalArgumentException("endTime must not be before startTime");
        }

        Instant startedAt = Instant.now(clock);
        List<HistoricalBar> bars = loadFixtureBars(new HistoricalMarketDataQuery(
                new HistoricalDatasetSpec(
                        "fixture",
                        dataset.fixtureId(),
                        dataset.exchangeCode(),
                        dataset.symbol(),
                        dataset.interval(),
                        dataset.resourcePath()
                ),
                exchangeCode,
                symbol,
                interval,
                startTime,
                endTime
        ));
        if (bars.isEmpty()) {
            throw new IllegalArgumentException("no fixture bars found for requested range");
        }
        MarketdataBarUpsertStats upsertStats = marketdataBarRepository.upsertBars(bars, INGEST_SOURCE, startedAt);
        Instant finishedAt = Instant.now(clock);
        return new MarketdataFixtureIngestionResult(
                dataset.fixtureId(),
                exchangeCode,
                symbol,
                interval.wireValue(),
                startTime,
                endTime,
                bars.size(),
                upsertStats.insertedCount(),
                upsertStats.updatedCount(),
                startedAt,
                finishedAt
        );
    }

    private List<HistoricalBar> loadFixtureBars(HistoricalMarketDataQuery query) {
        ClassPathResource resource = new ClassPathResource(query.datasetSpec().resourcePath());
        if (!resource.exists()) {
            throw new IllegalStateException("marketdata fixture not found: " + query.datasetSpec().resourcePath());
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            return reader.lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(line -> parseLine(query.exchangeCode(), line))
                    .filter(bar -> bar.symbol().equalsIgnoreCase(query.symbol()))
                    .filter(bar -> bar.interval() == query.interval())
                    .filter(bar -> !bar.openTime().isBefore(query.startTime()))
                    .filter(bar -> !bar.closeTime().isAfter(query.endTime()))
                    .sorted(Comparator.comparing(HistoricalBar::openTime))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read marketdata fixture: " + query.datasetSpec().resourcePath(), ex);
        }
    }

    private HistoricalBar parseLine(String exchangeCode, String line) {
        String[] columns = line.split(",");
        if (columns.length != 9) {
            throw new IllegalStateException("invalid marketdata fixture row: " + line);
        }
        return new HistoricalBar(
                exchangeCode,
                columns[0].trim().toUpperCase(Locale.ROOT),
                BarInterval.fromWireValue(columns[1].trim()),
                Instant.parse(columns[2].trim()),
                Instant.parse(columns[3].trim()),
                new BigDecimal(columns[4].trim()),
                new BigDecimal(columns[5].trim()),
                new BigDecimal(columns[6].trim()),
                new BigDecimal(columns[7].trim()),
                new BigDecimal(columns[8].trim())
        );
    }

    private String normalizeExchangeCode(String exchangeCode) {
        return requireText(exchangeCode, "exchangeCode").toUpperCase(Locale.ROOT);
    }

    private String normalizeSymbol(String symbol) {
        return requireText(symbol, "symbol").toUpperCase(Locale.ROOT);
    }

    private BarInterval normalizeInterval(String interval) {
        return BarInterval.fromWireValue(requireText(interval, "interval"));
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
