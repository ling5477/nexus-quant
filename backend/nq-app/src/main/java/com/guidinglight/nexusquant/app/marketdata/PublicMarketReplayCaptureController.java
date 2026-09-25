package com.guidinglight.nexusquant.app.marketdata;

import java.security.Principal;
import java.time.Instant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅手动公开行情 profile 可用的有界捕获入口。 */
@RestController
@Profile("public-marketdata-manual")
@ConditionalOnProperty(prefix = "nq.public-marketdata.outbound", name = "enabled", havingValue = "true")
@RequestMapping("/api/marketdata/public-captures")
public final class PublicMarketReplayCaptureController {
    private final PublicMarketReplayCaptureService service;

    public PublicMarketReplayCaptureController(PublicMarketReplayCaptureService service) {
        this.service = service;
    }

    @PostMapping
    public PublicMarketReplayCaptureService.CaptureView capture(@RequestBody CaptureRequest request,
            Principal principal) {
        return service.capture(request.start(), request.end(),
                principal == null ? "LOCAL_OPERATOR" : principal.getName());
    }

    public record CaptureRequest(Instant start, Instant end) { }
}
