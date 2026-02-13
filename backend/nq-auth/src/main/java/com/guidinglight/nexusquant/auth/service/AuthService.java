package com.guidinglight.nexusquant.auth.service;

import com.guidinglight.nexusquant.auth.dto.LoginRequest;
import com.guidinglight.nexusquant.auth.dto.LoginResponse;

/**
 * AuthService 定义认证服务占位接口。
 */
public interface AuthService {

    /**
     * 执行登录流程。
     *
     * @param request 登录请求
     * @return 访问令牌响应
     */
    LoginResponse login(LoginRequest request);
}
