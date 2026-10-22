package com.guidinglight.nexusquant.demo.registration;

import com.guidinglight.nexusquant.auth.domain.port.PublicDemoDistribution;
import java.net.URI;
import java.util.Set;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 构建依赖确定 Demo 身份；在 Runner 和外部适配器创建前拒绝危险配置。 */
@Configuration
@EnableScheduling
public class PublicDemoConfiguration {
    /** 禁止 Boot 生成并记录开发登录口令；正式认证仍只由数据库服务和 JWT 链拥有。 */
    @Bean public org.springframework.security.core.userdetails.UserDetailsService publicDemoUserDetailsService(){
        return username -> {throw new org.springframework.security.core.userdetails.UsernameNotFoundException("database authentication required");};
    }

    @Bean public PublicDemoDistribution publicDemoDistribution(javax.sql.DataSource source){
        var jdbc=new org.springframework.jdbc.core.JdbcTemplate(source);
        return userId -> Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM demo_registrations r JOIN users u ON u.id=r.user_id
                JOIN tenant_memberships m ON m.user_id=u.id AND m.tenant_id=r.tenant_id
                JOIN tenants t ON t.tenant_id=m.tenant_id
                WHERE u.id=? AND u.enabled AND u.email_verified_at IS NOT NULL AND r.status='READY'
                    AND m.membership_kind='DEMO' AND m.status='ACTIVE' AND t.tenant_kind='DEMO' AND t.status='ACTIVE')
            """,Boolean.class,userId));
    }

    @Bean
    public static BeanFactoryPostProcessor demoDistributionGuard(Environment environment) {
        return demoDistributionGuard(environment,
                com.guidinglight.nexusquant.common.runtime.ProcessEnvironmentResolver.resolveForCurrentProcess());
    }

    static BeanFactoryPostProcessor demoDistributionGuard(Environment environment,java.util.Map<String,String> process) {
        return factory -> {
            // 适配器读取 .env/进程配置，不能让 Spring profile 的 false 掩盖实际 REAL 配置。
            if(real(process.get("NQ_OKX_ENV")) || real(process.get("NQ_BINANCE_ENV")))
                throw new IllegalStateException("PUBLIC_DEMO_REAL_EXCHANGE_FORBIDDEN");
            for(String key:Set.of("NQ_LIVE_ENABLED","NQ_REAL_PROVIDER_ENABLED","NQ_REAL_CLIENT_ENABLED",
                    "NQ_REAL_EXCHANGE_ENABLED","NQ_OKX_WS_ENABLED","NQ_BINANCE_WS_ENABLED","NQ_OKX_RECOVERY_ENABLED")) {
                if(Boolean.parseBoolean(normalize(process.get(key)))) throw new IllegalStateException("PUBLIC_DEMO_UNSAFE_PROCESS_CONFIGURATION: "+key);
            }
            for(String profile:environment.getActiveProfiles()) {
                if(Set.of("local","test","prod","production","live").contains(profile.toLowerCase(java.util.Locale.ROOT)))
                    throw new IllegalStateException("PUBLIC_DEMO_UNSAFE_PROFILE");
            }
            for(String key:Set.of("nq.auth.bootstrap-admin.enabled","nq.env-safety.live-enabled","nq.env-safety.real-provider-enabled",
                    "nq.env-safety.real-client-enabled","nq.env-safety.real-exchange-enabled","nq.env-safety.ai-enabled",
                    "nq.env-safety.dh-runtime-enabled","nq.okx.ws.enabled","nq.binance.ws.enabled","nq.okx.recovery.enabled",
                    "nq.instrument.catalog-sync.enabled","nq.runtime.minimal-live-pilot.enabled",
                    "nq.runtime.minimal-live-pilot.order-submission-enabled","nq.runtime.minimal-live-pilot.cancel-enabled",
                    "nq.runtime.minimal-live-pilot.transfer-enabled","nq.runtime.minimal-live-pilot.withdraw-enabled",
                    "nq.live-control.exact-pilot-binding.enabled","nq.runtime.provider-observation.enabled",
                    "nq.dh.integration1.runtime.enabled","nq.dh.integration1.runtime.client.enabled",
                    "nq.dh.integration1.runtime.production-enabled")) {
                if(environment.getProperty(key,Boolean.class,false)) throw new IllegalStateException("PUBLIC_DEMO_UNSAFE_CONFIGURATION: "+key);
            }
            if(real(environment.getProperty("nq.okx.env")) || real(environment.getProperty("nq.binance.env")))
                throw new IllegalStateException("PUBLIC_DEMO_REAL_EXCHANGE_FORBIDDEN");
            for(String key:Set.of("nq.okx.api-key","nq.okx.api-secret","nq.okx.api-passphrase")) {
                if(!environment.getProperty(key,"").isBlank()) throw new IllegalStateException("PUBLIC_DEMO_CREDENTIALS_FORBIDDEN");
            }
            if(environment.getProperty("nq.demo.registration-enabled",Boolean.class,false)
                    || environment.getProperty("nq.demo.mail-dispatch-enabled",Boolean.class,false)) {
                if(environment.getProperty("nq.demo.token-secret","").length()<32)
                    throw new IllegalStateException("PUBLIC_DEMO_TOKEN_SECRET_REQUIRED");
                URI base=URI.create(environment.getRequiredProperty("nq.demo.verification-base-url"));
                if(base.getHost()==null || base.getUserInfo()!=null || base.getFragment()!=null || base.getQuery()!=null
                        || !("https".equals(base.getScheme()) || ("http".equals(base.getScheme()) && loopback(base.getHost()))))
                    throw new IllegalStateException("PUBLIC_DEMO_VERIFICATION_URL_INVALID");
            }
            if(environment.getProperty("nq.demo.mail-dispatch-enabled",Boolean.class,false)
                    && !loopback(environment.getProperty("spring.mail.host","127.0.0.1")))
                throw new IllegalStateException("PUBLIC_EMAIL_DELIVERY_NOT_QUALIFIED: local SMTP required");
        };
    }
    private static boolean loopback(String host){return Set.of("127.0.0.1","localhost","::1","[::1]").contains(host);}
    private static String normalize(String value){return value==null?"":value.trim();}
    private static boolean real(String value){return "real".equalsIgnoreCase(normalize(value));}
}
