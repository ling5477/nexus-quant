package com.guidinglight.nexusquant.app.marketdata;

import java.time.Instant;

import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.security.token.model.TokenClaims;

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
    private final GatewayAuthFacade auth;

    public PublicMarketReplayCaptureController(PublicMarketReplayCaptureService service, GatewayAuthFacade auth) {
        this.service = service;
        this.auth = auth;
    }

    @PostMapping
    public PublicMarketReplayCaptureService.CaptureView capture(@RequestBody CaptureRequest request) {
        // 捕获来源必须是认证链中的规范用户名，不能把含 token 声明的 principal 字符串写入 Dataset。
        String actor = auth.currentUser().map(TokenClaims::username)
                .filter(name -> !name.isBlank())
                .orElseThrow(() -> new IllegalStateException("PUBLIC_CAPTURE_ACTOR_UNAVAILABLE"));
        return service.capture(request.start(), request.end(), actor);
    }

    public record CaptureRequest(Instant start, Instant end) { }
}
