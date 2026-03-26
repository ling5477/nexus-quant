package com.guidinglight.nexusquant.api.web;

import com.guidinglight.nexusquant.auth.dto.LoginRequest;
import com.guidinglight.nexusquant.auth.service.AuthService;
import com.guidinglight.nexusquant.gateway.service.GatewayAuthFacade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

import java.util.Objects;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AuthController 提供正式登录与当前用户查询接口。
 */
@Validated
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Auth API", description = "正式认证与当前用户接口。")
public class AuthController {

    private final AuthService authService;
    private final GatewayAuthFacade gatewayAuthFacade;

    public AuthController(AuthService authService, GatewayAuthFacade gatewayAuthFacade) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.gatewayAuthFacade = Objects.requireNonNull(gatewayAuthFacade, "gatewayAuthFacade must not be null");
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
        return new AuthLoginResponse(
                loginResponse.accessToken(),
                loginResponse.tokenType(),
                loginResponse.expiresIn(),
                loginResponse.expiresAt(),
                loginResponse.username(),
                loginResponse.roles()
        );
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
        return new CurrentUserResponse(currentUser.username(), currentUser.roles(), true);
    }
}
