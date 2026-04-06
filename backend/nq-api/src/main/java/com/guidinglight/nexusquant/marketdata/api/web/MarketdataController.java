package com.guidinglight.nexusquant.marketdata.api.web;

import com.guidinglight.nexusquant.api.web.ApiErrorResponse;
import com.guidinglight.nexusquant.marketdata.api.dto.FixtureMarketdataIngestionRequestBody;
import com.guidinglight.nexusquant.marketdata.api.dto.FixtureMarketdataIngestionResponse;
import com.guidinglight.nexusquant.marketdata.api.dto.MarketdataBarResponse;
import com.guidinglight.nexusquant.marketdata.application.MarketdataBarIngestService;
import com.guidinglight.nexusquant.marketdata.application.command.FixtureMarketdataIngestionCommand;
import com.guidinglight.nexusquant.marketdata.domain.BarInterval;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalDatasetSpec;
import com.guidinglight.nexusquant.marketdata.domain.HistoricalMarketDataQuery;
import com.guidinglight.nexusquant.marketdata.domain.port.HistoricalMarketDataPort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * MarketdataController 提供 RC1 最小 historical bars 查询与 fixture ingest 接口。
 * <p>
 * Why:
 * RC1-5-A 要把 marketdata 从“只有包结构和读骨架”推进到“真实能写库、能查库”的最小闭环，
 * 因此 controller 需要同时暴露显式 ingest 动作和带 `exchangeCode` 的真实查询入口。
 */
@Validated
@RestController
@RequestMapping("/api/marketdata")
@Tag(name = "Marketdata API", description = "RC1 historical bars ingest/query API.")
public class MarketdataController {

    private final MarketdataBarIngestService marketdataBarIngestService;
    private final HistoricalMarketDataPort historicalMarketDataPort;

    public MarketdataController(
            MarketdataBarIngestService marketdataBarIngestService,
            HistoricalMarketDataPort historicalMarketDataPort
    ) {
        this.marketdataBarIngestService = Objects.requireNonNull(
                marketdataBarIngestService,
                "marketdataBarIngestService must not be null"
        );
        this.historicalMarketDataPort = Objects.requireNonNull(
                historicalMarketDataPort,
                "historicalMarketDataPort must not be null"
        );
    }

    @GetMapping("/bars")
    @Operation(
            summary = "查询历史 K 线",
            description = "按 exchangeCode / symbol / interval / start / end 返回 marketdata bars。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponse(responseCode = "200", description = "查询成功")
    public List<MarketdataBarResponse> listBars(
            @RequestParam String exchangeCode,
            @RequestParam String symbol,
            @RequestParam String interval,
            @RequestParam Instant startTime,
            @RequestParam Instant endTime
    ) {
        BarInterval normalizedInterval = BarInterval.fromWireValue(interval);
        return historicalMarketDataPort.loadBars(new HistoricalMarketDataQuery(
                new HistoricalDatasetSpec("db", "marketdata_bars", exchangeCode, symbol, normalizedInterval, "marketdata_bars"),
                exchangeCode,
                symbol,
                normalizedInterval,
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

    @PostMapping("/bars/ingestions/fixture")
    @Operation(
            summary = "导入注册 fixture bars",
            description = "仅支持首批注册 fixture 数据集的显式导入，并返回写入统计摘要。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "导入成功"),
            @ApiResponse(responseCode = "400", description = "fixture 请求非法", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "导入状态冲突", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public FixtureMarketdataIngestionResponse ingestFixture(
            @Valid @RequestBody FixtureMarketdataIngestionRequestBody requestBody
    ) {
        return FixtureMarketdataIngestionResponse.from(marketdataBarIngestService.ingestFixture(
                new FixtureMarketdataIngestionCommand(
                        requestBody.fixtureId(),
                        requestBody.exchangeCode(),
                        requestBody.symbol(),
                        requestBody.interval(),
                        requestBody.startTime(),
                        requestBody.endTime()
                )
        ));
    }
}
