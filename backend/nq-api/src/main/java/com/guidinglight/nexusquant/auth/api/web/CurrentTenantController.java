package com.guidinglight.nexusquant.auth.api.web;

import com.guidinglight.nexusquant.common.tenant.TenantContext;
import com.guidinglight.nexusquant.common.tenant.TenantKind;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 只返回认证链已经验证的当前归属；没有按请求参数查询其他租户的入口。 */
@RestController
public final class CurrentTenantController {
    @GetMapping("/api/auth/tenant")
    public Identity current() {
        var identity = TenantContext.current().orElseThrow(
                () -> new ResponseStatusException(HttpStatus.FORBIDDEN, "TENANT_CONTEXT_REQUIRED"));
        return new Identity(identity.tenantId().value(), identity.tenantCode(), identity.kind(), identity.userId());
    }

    public record Identity(UUID tenantId, String tenantCode, TenantKind tenantKind, long userId) {}
}
