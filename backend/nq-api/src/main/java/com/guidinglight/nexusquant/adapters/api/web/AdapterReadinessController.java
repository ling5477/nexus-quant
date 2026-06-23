package com.guidinglight.nexusquant.adapters.api.web;

import com.guidinglight.nexusquant.adapters.api.AdapterReadinessStatusService;
import com.guidinglight.nexusquant.adapters.api.dto.AdapterReadinessResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Objects;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AdapterReadinessController 暴露只读 adapter readiness 状态查询。
 * <p>
 * Why:
 * GateM-5A 给后续前端一个入口，展示「OKX / Binance / Noop 当前能不能实盘、原因是什么」。该 controller 只读：
 * 委托 {@link AdapterReadinessStatusService} 聚合静态 readiness 决策，不触达真实交易所、不读取 credential、
 * 不触发 adapter delegate / 下单 / 撤单 / 行情订阅。当前 no-real / LIVE disabled baseline 下结果全部 fail-closed。
 */
@RestController
@RequestMapping("/api/adapters")
@Tag(name = "Adapter Readiness API", description = "只读 adapter readiness 状态查询（no-real / fail-closed）")
public class AdapterReadinessController {

    private final AdapterReadinessStatusService adapterReadinessStatusService;

    public AdapterReadinessController(AdapterReadinessStatusService adapterReadinessStatusService) {
        this.adapterReadinessStatusService = Objects.requireNonNull(
                adapterReadinessStatusService,
                "adapterReadinessStatusService must not be null"
        );
    }

    @GetMapping("/readiness")
    @Operation(
            summary = "查询 adapter readiness 状态",
            description = "只读返回各 venue（OKX / Binance / Noop 等）各能力当前 readiness。当前 no-real / LIVE disabled "
                    + "baseline 下全部 fail-closed（allowed=false / liveAuthorized=false，无 READY）；不触达真实交易所、不读取 credential。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    public AdapterReadinessResponse readiness() {
        return adapterReadinessStatusService.currentReadiness();
    }
}
