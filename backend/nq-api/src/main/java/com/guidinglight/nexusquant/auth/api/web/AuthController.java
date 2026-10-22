package com.guidinglight.nexusquant.auth.api.web;

import com.guidinglight.nexusquant.account.application.service.ExchangeAccountQueryService;
import com.guidinglight.nexusquant.api.web.dto.ApiErrorResponse;
import com.guidinglight.nexusquant.auth.api.dto.AuthLoginRequestBody;
import com.guidinglight.nexusquant.auth.api.dto.PasswordChangeRequest;
import com.guidinglight.nexusquant.auth.application.service.PasswordChangeService;
import com.guidinglight.nexusquant.auth.application.service.TenantIdentityResolver;
import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import org.springframework.http.HttpStatus;
import com.guidinglight.nexusquant.auth.api.dto.AuthLoginResponse;
import com.guidinglight.nexusquant.auth.api.dto.CurrentUserResponse;
import com.guidinglight.nexusquant.auth.application.port.AuthService;
import com.guidinglight.nexusquant.auth.application.service.CurrentUserProfileService;
import com.guidinglight.nexusquant.auth.application.command.LoginRequest;
import com.guidinglight.nexusquant.gateway.application.GatewayAuthFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.List;

/**
 * AuthController 提供正式登录与当前用户查询接口。
 */
@Validated
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth API", description = "正式认证与当前用户接口。")
public class AuthController {

    private final AuthService authService;
    private final PasswordChangeService passwordChangeService;
    private final GatewayAuthFacade gatewayAuthFacade;
    private final CurrentUserProfileService currentUserProfileService;
    private final ExchangeAccountQueryService exchangeAccountQueryService;

    public AuthController(
            AuthService authService,
            GatewayAuthFacade gatewayAuthFacade,
            CurrentUserProfileService currentUserProfileService,
            ExchangeAccountQueryService exchangeAccountQueryService,
            PasswordChangeService passwordChangeService
    ) {
        this.passwordChangeService = Objects.requireNonNull(passwordChangeService, "passwordChangeService");
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.gatewayAuthFacade = Objects.requireNonNull(gatewayAuthFacade, "gatewayAuthFacade must not be null");
        this.currentUserProfileService = Objects.requireNonNull(
                currentUserProfileService,
                "currentUserProfileService must not be null"
        );
        this.exchangeAccountQueryService = Objects.requireNonNull(
                exchangeAccountQueryService,
                "exchangeAccountQueryService must not be null"
        );
    }

    @PostMapping("/login")
    @Operation(summary = "登录", description = "使用 username/password 登录并签发 Bearer access token。")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "登录成功"),
            @ApiResponse(responseCode = "400", description = "参数错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "用户名或密码错误", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "账号被禁用", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthLoginResponse login(@Valid @RequestBody AuthLoginRequestBody request) {
        var loginResponse = authService.login(new LoginRequest(request.username(), request.password()));
        return response(loginResponse);
    }

    @PostMapping("/renew")
    @Operation(summary = "续期当前活跃会话", description = "需要尚未过期的Bearer令牌；空闲、注销、改密或达到最长时限后不可续期。")
    public AuthLoginResponse renew() {
        var claims = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        return response(authService.renew(claims));
    }

    private AuthLoginResponse response(com.guidinglight.nexusquant.auth.application.result.LoginResponse loginResponse) {
        return new AuthLoginResponse(
                loginResponse.accessToken(),
                loginResponse.tokenType(),
                loginResponse.expiresIn(),
                loginResponse.expiresAt(),
                loginResponse.username(),
                loginResponse.roles(),
                loginResponse.mustChangePassword(),
                loginResponse.sessionId(), loginResponse.idleExpiresAt(), loginResponse.sessionExpiresAt(), loginResponse.serverTime()
        );
    }

    @PostMapping("/change-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        var user = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        passwordChangeService.changePassword(user.username(), user.authVersion(), request.currentPassword(), request.newPassword());
    }
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        var claims = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        authService.logout(claims);
    }
    @GetMapping("/me")
    @Operation(
            summary = "当前用户",
            description = "返回当前已认证用户的最小信息。",
            security = @SecurityRequirement(name = "bearerAuth")
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "查询成功"),
            @ApiResponse(responseCode = "401", description = "未登录或 token 无效", content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public CurrentUserResponse me() {
        var currentUser = gatewayAuthFacade.currentUser()
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        var userProfile = currentUserProfileService.findByUsername(currentUser.username())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("authentication required"));
        var tenant = TenantContext.current().orElse(null);
        boolean demo = tenant != null && tenant.kind() == TenantKind.DEMO;
        boolean restricted = demo || userProfile.roles().contains(TenantIdentityResolver.DEMO_USER)
                || userProfile.roles().contains(TenantIdentityResolver.PLATFORM_ADMIN);
        // 未完成业务隔离前，新身份的基础资料不触达账户读侧。
        var defaultAccount = restricted && !demo ? null
                : exchangeAccountQueryService.findDefaultByOwnerUserId(userProfile.userId()).orElse(null);
        return new CurrentUserResponse(
                userProfile.userId(),
                userProfile.username(),
                demo ? List.of(TenantIdentityResolver.DEMO_USER) : userProfile.roles(),
                true,
                defaultAccount == null ? null : defaultAccount.exchangeAccountId(),
                defaultAccount == null ? null : defaultAccount.exchangeCode(),
                defaultAccount == null ? null : defaultAccount.tradeEnv(),
                defaultAccount == null ? null : defaultAccount.accountAlias(),
                userProfile.mustChangePassword()
        );
    }
}





