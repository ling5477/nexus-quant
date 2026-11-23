package com.guidinglight.nexusquant.marketdata.api.web;

import com.guidinglight.nexusquant.common.trace.TraceIdContext;
import com.guidinglight.nexusquant.marketdata.api.dto.InstrumentCatalogItemResponse;
import com.guidinglight.nexusquant.marketdata.api.dto.InstrumentCatalogSyncResponse;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogService;
import com.guidinglight.nexusquant.marketdata.application.instrument.InstrumentCatalogSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * InstrumentCatalogController 提供 PRE-2 的正式 instrument/symbol 查询与同步入口。
 * <p>
 * Why:
 * 前端不应该继续手工输入 symbol，也不应该直接读取 adapter cache；
 * 这里先提供最小的 catalog 列表与显式 sync 动作，作为多币种主链的正式入口。
 */
@RestController
@ConditionalOnProperty(
        prefix = "nq.runtime.trading-components",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
@RequestMapping("/api/instruments")
@Tag(name = "Instrument Catalog API", description = "正式 instrument/symbol catalog API")
public class InstrumentCatalogController {

    private final InstrumentCatalogService instrumentCatalogService;
    private final InstrumentCatalogSyncService instrumentCatalogSyncService;

    public InstrumentCatalogController(
            InstrumentCatalogService instrumentCatalogService,
            InstrumentCatalogSyncService instrumentCatalogSyncService
    ) {
        this.instrumentCatalogService = Objects.requireNonNull(
                instrumentCatalogService,
                "instrumentCatalogService must not be null"
        );
        this.instrumentCatalogSyncService = Objects.requireNonNull(
                instrumentCatalogSyncService,
                "instrumentCatalogSyncService must not be null"
        );
    }

    @GetMapping
    @Operation(
            summary = "列出 instrument catalog",
            description = "按 exchangeCode 返回正式 instrument/symbol 条目；exchangeCode 为空时返回全部。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public List<InstrumentCatalogItemResponse> list(@RequestParam(required = false) String exchangeCode) {
        return instrumentCatalogService.list(exchangeCode).stream()
                .map(InstrumentCatalogItemResponse::from)
                .toList();
    }

    @PostMapping("/sync")
    @Operation(
            summary = "同步 instrument catalog",
            description = "从当前已接入交易所 adapter 的 instrument cache 同步正式 catalog。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public InstrumentCatalogSyncResponse sync(@RequestParam(required = false) String exchangeCode) {
        return InstrumentCatalogSyncResponse.from(
                instrumentCatalogSyncService.sync(exchangeCode, TraceIdContext.getOrCreate())
        );
    }
}
