package com.guidinglight.nexusquant.search.api;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import com.guidinglight.nexusquant.search.application.ObjectSearchService;

import io.swagger.v3.oas.annotations.Operation;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 从认证上下文取得所有者，再查询其可见对象；结果仅提供定位摘要，不接受客户端用户身份。 */
@RestController
@RequestMapping("/api/search")
public class ObjectSearchController {
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService profiles;
    private final ObjectSearchService search;

    public ObjectSearchController(
            GatewayAuthFacade auth,
            CurrentUserProfileService profiles,
            ObjectSearchService search) {
        this.auth = auth;
        this.profiles = profiles;
        this.search = search;
    }

    @GetMapping
    @Operation(
            summary = "搜索自身可见业务对象",
            description = "2到80字普通文本、每页20条、最多1000条；仅返回定位摘要，不变更账户默认值或授予执行权限。")
    public ObjectSearchService.Page search(
            @RequestParam String q,
            @RequestParam(defaultValue = "ALL") String kind,
            @RequestParam(defaultValue = "0") int offset) {
        var user =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        long owner =
                profiles.findByUsername(user.username())
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"))
                        .userId();
        return search.search(owner, q, kind, offset);
    }
}
