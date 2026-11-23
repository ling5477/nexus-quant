package com.guidinglight.nexusquant.marketdata.api.web;

import com.guidinglight.nexusquant.marketdata.api.dto.MarketdataDatasetPageResponse;
import com.guidinglight.nexusquant.marketdata.application.service.MarketdataDatasetListService;
import com.guidinglight.nexusquant.marketdata.domain.port.MarketdataDatasetListQuery;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** 只扩展原共享行情目录的有界读取；旧列表和所有数据集写入入口保持原合同。 */
@RestController
public class MarketdataDatasetQueryController {
    private final MarketdataDatasetListService service;

    public MarketdataDatasetQueryController(MarketdataDatasetListService service) {
        this.service = service;
    }

    @GetMapping("/api/marketdata/datasets/page")
    @Operation(summary = "分页查询行情数据集", security = @SecurityRequirement(name = "bearerAuth"))
    public MarketdataDatasetPageResponse list(
            @RequestParam(required = false) UUID datasetId,
            @RequestParam(required = false) String datasetName,
            @RequestParam(required = false) String exchangeCode,
            @RequestParam(required = false) String marketType,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String interval,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String qualityStatus,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Long maximumGapCount,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        return MarketdataDatasetPageResponse.from(
                service.list(
                        new MarketdataDatasetListQuery(
                                datasetId,
                                datasetName,
                                exchangeCode,
                                marketType,
                                symbol,
                                interval,
                                status,
                                qualityStatus,
                                source,
                                maximumGapCount,
                                limit,
                                offset)));
    }
}
