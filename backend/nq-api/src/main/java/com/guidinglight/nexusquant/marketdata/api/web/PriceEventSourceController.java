package com.guidinglight.nexusquant.marketdata.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.marketdata.application.service.PriceEventSourceService;
import com.guidinglight.nexusquant.marketdata.domain.PriceEventSourceRecord;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/** 既有全局行情读/写权限控制来源接入；操作者来自正式认证，接口只记录给出的时点而非认证供应方真实性。 */
@RestController
@RequestMapping("/api/marketdata/price-event-sources")
public class PriceEventSourceController {
    private final PriceEventSourceService sources;
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;

    public PriceEventSourceController(
            PriceEventSourceService sources,
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles) {
        this.sources = sources;
        this.auth = auth;
        this.profiles = profiles;
    }

    /** 保存单根已入库K线的来源记录；网络未知时使用同一sourceId读取，禁止生成新身份盲重试。 */
    @PostMapping
    public PriceEventSourceRecord submit(@Valid @RequestBody SubmitSource request) {
        return sources.submit(
                actor(),
                request.sourceId(),
                request.barId(),
                request.expectedBarSha256(),
                request.sourceName(),
                request.documentJson());
    }

    /** 用保存时捕获的完整请求核对结果，核验入口没有插入/更新副作用。 */
    @PostMapping("/verification")
    public PriceEventSourceService.Verification verify(@Valid @RequestBody SubmitSource request) {
        return sources.verify(
                actor(),
                request.sourceId(),
                request.barId(),
                request.expectedBarSha256(),
                request.sourceName(),
                request.documentJson());
    }

    private long actor() {
        var user =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        long actor =
                profiles.findByUsername(user.username())
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"))
                        .userId();
        return actor;
    }

    /** 来源记录与已有行情一样属于已认证的全局只读数据；历史记录不自动贴到更新后的bar。 */
    @GetMapping("/{sourceId}")
    public PriceEventSourceRecord read(@PathVariable UUID sourceId) {
        return sources.read(sourceId)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "PRICE_EVENT_SOURCE_NOT_FOUND"));
    }

    public record SubmitSource(
            @NotNull UUID sourceId,
            @Positive long barId,
            @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String expectedBarSha256,
            @NotBlank @Size(max = 128) String sourceName,
            @NotBlank @Size(max = 32768) String documentJson) {}
}
