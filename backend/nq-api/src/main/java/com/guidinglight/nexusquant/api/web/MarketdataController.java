package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.backtest.model.BarInterval;
import com.guidinglight.nexusquant.backtest.model.HistoricalDatasetSpec;
import com.guidinglight.nexusquant.backtest.model.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.backtest.port.HistoricalMarketDataPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MarketdataController 提供 RC1 最小 historical bars 查询接口。
 */
@Validated
@RestController
@RequestMapping("/api/marketdata")
@Tag(name = "Marketdata API", description = "RC1 historical bars query API.")
public class MarketdataController {

    private final HistoricalMarketDataPort historicalMarketDataPort;

    public MarketdataController(HistoricalMarketDataPort historicalMarketDataPort) {
        this.historicalMarketDataPort = Objects.requireNonNull(
                historicalMarketDataPort,
                "historicalMarketDataPort must not be null"
        );
    }

    @GetMapping("/bars")
    @Operation(
            summary = "查询历史 K 线",
            description = "按 symbol / interval / start / end 返回 marketdata bars。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<MarketdataBarResponse> listBars(
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam Instant startTime,
            @RequestParam Instant endTime
    ) {
        return historicalMarketDataPort.loadBars(new HistoricalMarketDataQuery(
                new HistoricalDatasetSpec("db", "marketdata_bars", symbol, BarInterval.fromWireValue(interval), "marketdata_bars"),
                symbol,
                BarInterval.fromWireValue(interval),
                startTime,
                endTime
        )).stream().map(bar -> new MarketdataBarResponse(
                bar.symbol(),
                bar.interval().wireValue(),
                bar.openTime(),
                bar.closeTime(),
                bar.openPrice(),
                bar.highPrice(),
                bar.lowPrice(),
                bar.closePrice(),
                bar.volume()
        )).toList();
    }
}
