package com.guidinglight.nexusquant.app.smoke;

import com.guidinglight.nexusquant.app.config.auth.SecurityRuntimeProperties;
import com.guidinglight.nexusquant.auth.application.command.SeedUserCommand;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcAuthUserRepository;
import com.guidinglight.nexusquant.common.tenant.*;
import java.util.List;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;

/** 历史本地应用夹具先由安装身份准备最新库；应用仍使用真实受限身份，不注入默认生产租户。 */
public final class RestrictedPostgresTestInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    public static final String FIXTURE="restrictedPostgresFixture";
    public static final String IDENTITY="restrictedPostgresLocalIdentity";
    public static final String OPERATOR_PASSWORD="SyntheticLocalOperator123";

    @Override public void initialize(ConfigurableApplicationContext context) {
        try {
            var env=context.getEnvironment();
            var db=TenantIsolationTestDatabase.create(null,env.getRequiredProperty("spring.datasource.url"),
                    env.getRequiredProperty("spring.datasource.username"),env.getRequiredProperty("spring.datasource.password"));
            var users=new JdbcAuthUserRepository(db.admin);
            var properties=Binder.get(env).bind("nq.security",SecurityRuntimeProperties.class).orElseGet(SecurityRuntimeProperties::new);
            for(var user:properties.getUsers())users.upsertSeedUser(new SeedUserCommand(user.getUsername(),user.getPasswordHash(),List.copyOf(user.getRoles()),user.isEnabled()));
            var admin=users.findByUsername("admin");
            long userId=admin.isPresent()?admin.get().userId():db.user("ci-local-admin","ADMIN","OPERATOR","VIEWER");
            users.upsertSeedUser(new SeedUserCommand("local-operator",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(OPERATOR_PASSWORD),List.of("OPERATOR","VIEWER"),true));
            db.admin.update("INSERT INTO accounts(account_code,venue,status,tenant_id) VALUES('ci-backend-test-account','PAPER','ACTIVE',?)",TenantId.LOCAL.value());
            TestPropertyValues.of("spring.datasource.url="+db.url(),"spring.datasource.username="+db.runtime,
                    "spring.datasource.password="+db.runtimePassword,"spring.flyway.enabled=false","spring.sql.init.mode=never",
                    "nq.auth.bootstrap-admin.enabled=false").applyTo(context);
            context.getBeanFactory().registerSingleton(FIXTURE,db);
            context.getBeanFactory().registerSingleton(IDENTITY,new TenantIdentity(TenantId.LOCAL,"NQ_LOCAL",TenantKind.LOCAL,userId));
            context.addApplicationListener(event->{if(event instanceof ContextClosedEvent closed && closed.getApplicationContext()==context) {
                try {db.close();}catch(Exception failure){throw new IllegalStateException("isolated tenant fixture cleanup failed",failure);}
            }});
        } catch(Exception failure) {throw new IllegalStateException("restricted PostgreSQL fixture preparation failed",failure);}
    }

    /** 作用域在测试事务之前建立；只适用于显式标注的本地业务测试。 */
    public static final class LocalScope extends org.springframework.test.context.support.AbstractTestExecutionListener {
        private static final String ATTRIBUTE=LocalScope.class.getName();
        @Override public int getOrder(){return 3500;}
        @Override public void beforeTestMethod(org.springframework.test.context.TestContext test) {
            var identity=test.getApplicationContext().getBean(IDENTITY,TenantIdentity.class);
            test.setAttribute(ATTRIBUTE,TenantContext.open(identity));
        }
        @Override public void afterTestMethod(org.springframework.test.context.TestContext test) {
            var scope=(TenantContext.Scope)test.removeAttribute(ATTRIBUTE);if(scope!=null)scope.close();
        }
    }
}
