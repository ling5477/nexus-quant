package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.auth.application.port.AuthService;
import com.guidinglight.nexusquant.auth.application.command.LoginRequest;
import com.guidinglight.nexusquant.common.tenant.*;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

/** MockMvc 沿真实 JWT 解析恢复身份；测试自身的 SQL 断言另有显式本地作用域。 */
@TestConfiguration(proxyBeanMethods=false)
public class LocalTenantMockMvcFixture {
    @Bean MockMvcBuilderCustomizer localOperatorBearer(AuthService auth) {
        var login=auth.login(new LoginRequest("local-operator",RestrictedPostgresTestInitializer.OPERATOR_PASSWORD));
        return builder->builder.defaultRequest(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/")
                .header("Authorization","Bearer "+login.accessToken()));
    }

    public static JdbcTemplate fixtureQueries(JdbcTemplate runtime,TenantIdentity identity) {
        var proxy=new org.springframework.aop.framework.ProxyFactory(runtime);proxy.setProxyTargetClass(true);
        proxy.addAdvice((org.aopalliance.intercept.MethodInterceptor)invocation->{
            try(var ignored=TenantContext.open(identity)){return invocation.proceed();}
        });
        return (JdbcTemplate)proxy.getProxy();
    }
}
