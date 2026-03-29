package com.guidinglight.nexusquant.marketdata.infra.fixture;

import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalBar;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalMarketDataPort;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

/**
 * FixtureHistoricalMarketDataPort 提供 GateF-2 的样本历史行情读取实现。
 * <p>
 * Why:
 * 本批目标是跑通最小回测执行主链，不接真实行情系统，因此使用 classpath fixture 保证数据稳定、可测试、可复现。
 */
@Repository
public class FixtureHistoricalMarketDataPort implements HistoricalMarketDataPort {

    @Override
    public List<HistoricalBar> loadBars(HistoricalMarketDataQuery query) {
        ClassPathResource resource = new ClassPathResource(query.datasetSpec().resourcePath());
        if (!resource.exists()) {
            throw new IllegalStateException("historical fixture not found: " + query.datasetSpec().resourcePath());
        }
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(),
                StandardCharsets.UTF_8
        ))) {
            return reader.lines()
                    .skip(1)
                    .filter(line -> !line.isBlank())
                    .map(line -> parseLine(line, query.interval()))
                    .filter(bar -> bar.symbol().equalsIgnoreCase(query.symbol()))
                    .filter(bar -> bar.interval() == query.interval())
                    .filter(bar -> !bar.openTime().isBefore(query.startTime()))
                    .filter(bar -> !bar.closeTime().isAfter(query.endTime()))
                    .sorted(Comparator.comparing(HistoricalBar::openTime))
                    .toList();
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read historical fixture: " + query.datasetSpec().resourcePath(), ex);
        }
    }

    private HistoricalBar parseLine(String line, BarInterval requestedInterval) {
        String[] columns = line.split(",");
        if (columns.length != 9) {
            throw new IllegalStateException("invalid historical fixture row: " + line);
        }
        return new HistoricalBar(
                columns[0].trim(),
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
}


