package com.guidinglight.nexusquant.currency.api;

import com.guidinglight.nexusquant.common.currency.CurrencyRegistry;
import com.guidinglight.nexusquant.common.currency.CurrencyValuationService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 已认证用户只读币种定义和实际能力；目录不是交易品种、结算权限或汇率承诺。 */
@RestController
@RequestMapping("/api/reference/currencies")
public class CurrencyController {
    private final CurrencyRegistry registry;
    private final CurrencyValuationService valuations;

    public CurrencyController(CurrencyRegistry registry, CurrencyValuationService valuations) {
        this.registry = registry;
        this.valuations = valuations;
    }

    @GetMapping
    public View read() {
        return new View(
                "USDT",
                "USDT",
                registry.list().stream()
                        .map(
                                c ->
                                        new Entry(
                                                c.code(),
                                                c.kind(),
                                                c.name(),
                                                valuations.supportsTarget(c.code()),
                                                false,
                                                "CNY".equals(c.code()),
                                                valuations.supportsTarget(c.code())
                                                        ? "SOURCE_REQUIRED"
                                                        : "NOT_ENABLED"))
                        .toList());
    }

    public record View(
            String defaultValuationCurrency,
            String defaultDisplayCurrency,
            List<Entry> currencies) {}

    public record Entry(
            String code,
            String kind,
            String name,
            boolean valuationSupported,
            boolean displayConversionSupported,
            boolean displayOnly,
            String status) {}
}
