package com.guidinglight.nexusquant.app.smoke;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;

/** 从已注册并注资的 V45 原库正向升级，平台初始化与迁移都不能改写既有经济事实。 */
@EnabledIfSystemProperty(named="nq.demo.pg.required",matches="true")
class PlatformUserMigrationIntegrationTest {
    @Test void forwardUpgradeAndOneTimePlatformInitializationRetainExistingFacts() throws Exception {
        try(var db=TenantIsolationTestDatabase.create("45")) {
            db.runtime();
            try(var app=PublicDemoTestHttp.start(db);var http=new PublicDemoTestHttp(app.getWebServer().getPort())) {
                PublicDemoTwoUserFlowIntegrationTest.register(db,http,"platform-before-upgrade");
            }
            var before=TenantIsolationHttpIntegrationTest.economicSnapshot(db);
            var checksums=db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success ORDER BY installed_rank");
            db.migrate(null);db.grantRuntimePrivileges(db.runtime);
            assertEquals(0,db.migration(null).migrate().migrationsExecuted);
            assertEquals(checksums,db.admin.queryForList("SELECT version,checksum FROM flyway_schema_history WHERE success AND version::integer<=45 ORDER BY installed_rank"));
            assertEquals(before,TenantIsolationHttpIntegrationTest.economicSnapshot(db));
            var initialize=Class.forName("com.guidinglight.nexusquant.demo.platform.PlatformAdminInitializer")
                    .getDeclaredMethod("initialize",java.sql.Connection.class,String.class,String.class);
            initialize.setAccessible(true);
            String hash=new BCryptPasswordEncoder().encode(PublicDemoTestHttp.PASSWORD);
            try(var connection=db.admin.getDataSource().getConnection()) {initialize.invoke(null,connection,"platform-initialized",hash);}
            assertEquals(1,db.admin.queryForObject("SELECT count(*) FROM platform_identity_initializations",Integer.class));
            assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM tenant_memberships m JOIN users u ON u.id=m.user_id WHERE u.username='platform-initialized'",Integer.class));
            assertEquals(List.of("PLATFORM_ADMIN"),db.admin.queryForList("SELECT r.role_code FROM user_roles ur JOIN users u ON u.id=ur.user_id JOIN roles r ON r.id=ur.role_id WHERE u.username='platform-initialized'",String.class));
            try(var connection=db.admin.getDataSource().getConnection()) {
                var error=assertThrows(java.lang.reflect.InvocationTargetException.class,()->initialize.invoke(null,connection,"platform-second",hash));
                assertTrue(error.getCause().getMessage().contains("already initialized"));
            }
            assertEquals(0,db.admin.queryForObject("SELECT count(*) FROM users WHERE username='platform-second'",Integer.class));
            assertEquals(before,TenantIsolationHttpIntegrationTest.economicSnapshot(db));
            try(var app=PublicDemoTestHttp.start(db);var http=new PublicDemoTestHttp(app.getWebServer().getPort())) {
                String token=http.login("platform-initialized");
                assertTrue(http.ok("GET","/api/auth/me",token,null).path("mustChangePassword").asBoolean());
                assertEquals(403,http.send("GET","/api/platform/tenants",token,null).statusCode());
                String replacement="ChangedPlatformSecret123456789!";
                assertEquals(204,http.send("POST","/api/auth/change-password",token,Map.of("currentPassword",PublicDemoTestHttp.PASSWORD,"newPassword",replacement)).statusCode());
                assertEquals(401,http.send("GET","/api/auth/me",token,null).statusCode());
                var session=http.ok("POST","/api/auth/login",null,Map.of("username","platform-initialized","password",replacement));
                assertFalse(session.path("mustChangePassword").asBoolean());
                // 未装配独立只读数据库时明确503，不回退到业务连接读取跨租户事实。
                assertEquals(503,http.send("GET","/api/platform/tenants",session.path("accessToken").asText(),null).statusCode());
            }
        }
    }
}
