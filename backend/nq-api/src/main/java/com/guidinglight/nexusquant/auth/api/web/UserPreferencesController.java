package com.guidinglight.nexusquant.auth.api.web;

import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.application.service.UserPreferencesService;
import com.guidinglight.nexusquant.auth.domain.UserPreferences;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 偏好身份只来自当前认证主体，不接受客户端传入另一用户的标识。 */
@RestController
@RequestMapping("/api/user/preferences")
public final class UserPreferencesController {
    private final GatewayAuthFacade auth;
    private final CurrentUserProfileService users;
    private final UserPreferencesService preferences;

    public UserPreferencesController(
            GatewayAuthFacade auth,
            CurrentUserProfileService users,
            UserPreferencesService preferences) {
        this.auth = auth;
        this.users = users;
        this.preferences = preferences;
    }

    @GetMapping
    public UserPreferences read() {
        return preferences.read(userId());
    }

    /** 保存个人偏好须匹配旧版本；冲突返回409，调用方重新读取后确认，不能自动覆盖另一会话。 */
    @PutMapping
    public UserPreferences save(@RequestBody UserPreferences request) {
        try {
            return preferences
                    .save(userId(), request)
                    .orElseThrow(
                            () ->
                                    new ResponseStatusException(
                                            HttpStatus.CONFLICT,
                                            "preferences changed; reload before saving"));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
        }
    }

    private long userId() {
        var principal =
                auth.currentUser()
                        .orElseThrow(
                                () ->
                                        new AuthenticationCredentialsNotFoundException(
                                                "authentication required"));
        return users.findByUsername(principal.username())
                .orElseThrow(
                        () ->
                                new AuthenticationCredentialsNotFoundException(
                                        "authentication required"))
                .userId();
    }
}
