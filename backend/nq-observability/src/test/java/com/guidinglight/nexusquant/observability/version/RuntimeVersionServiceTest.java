package com.guidinglight.nexusquant.observability.version;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Properties;

/** 缺失/错误包身份不得由服务版本补齐，且环境中的其他敏感键不能进入只读响应。 */
class RuntimeVersionServiceTest {
    @Test
    void absentMetadataRemainsUnavailable() {
        var snapshot = service(new MockEnvironment(), null).read();
        assertThat(snapshot.serviceVersion()).isNull();
        assertThat(snapshot.installedPackageVersion()).isNull();
        assertThat(snapshot.packageIdentityStatus()).isEqualTo("UNAVAILABLE");
        assertThat(snapshot.reasons())
                .containsExactly("BUILD_INFO_UNAVAILABLE", "INSTALLED_PACKAGE_INFO_UNAVAILABLE");
        assertThat(snapshot.automaticUpdate()).isFalse();
    }

    @Test
    void serviceArtifactDoesNotBecomeProductPackageVersion() {
        var snapshot = service(new MockEnvironment(), build()).read();
        assertThat(snapshot.serviceVersion()).isEqualTo("0.1.0-SNAPSHOT");
        assertThat(snapshot.serviceArtifact()).isEqualTo("nq-app");
        assertThat(snapshot.installedPackageVersion()).isNull();
        assertThat(snapshot.sourceCommit()).isNull();
    }

    @Test
    void completeInstalledIdentityIsExplicitlyDeclaredNotVerified() throws Exception {
        var environment = installed().withProperty("NQ_SECURITY_SECRET", "must-never-be-returned");
        var versions = service(environment, build());
        var snapshot = versions.read();
        assertThat(snapshot.installedPackageVersion()).isEqualTo("1.0.0");
        assertThat(snapshot.sourceCommit()).isEqualTo("a".repeat(40));
        assertThat(snapshot.packageIdentityStatus()).isEqualTo("INSTALLER_DECLARED");
        assertThat(snapshot.packagedSchemaVersion()).isEqualTo("9");
        assertThat(snapshot.updateExecutor()).isEqualTo("HOST_INSTALLER");
        assertThat(snapshot.reasons()).isEmpty();
        MockMvcBuilders.standaloneSetup(new RuntimeVersionController(versions))
                .build()
                .perform(get("/api/system/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceVersion").value("0.1.0-SNAPSHOT"))
                .andExpect(jsonPath("$.automaticUpdate").value(false))
                .andExpect(jsonPath("$.NQ_SECURITY_SECRET").doesNotExist());
        assertThat(snapshot.toString()).doesNotContain("must-never-be-returned");
    }

    @Test
    void partialMalformedAndOversizedMetadataCannotFormAReleaseIdentity() {
        for (var environment :
                new MockEnvironment[] {
                    new MockEnvironment().withProperty("NQ_VERSION", "1.0.0"),
                    installed().withProperty("SOURCE_SHA", "not-a-git-commit"),
                    installed().withProperty("NQ_VERSION", "1.0.0\nPRIVATE=value"),
                    installed().withProperty("SCHEMA_VERSION", "0"),
                    installed().withProperty("PACKAGE_MANIFEST_SHA256", "b".repeat(1000))
                }) {
            var snapshot = service(environment, build()).read();
            assertThat(snapshot.packageIdentityStatus()).isEqualTo("INVALID");
            assertThat(snapshot.installedPackageVersion()).isNull();
            assertThat(snapshot.sourceCommit()).isNull();
            assertThat(snapshot.packageManifestSha256()).isNull();
            assertThat(snapshot.reasons()).containsExactly("PACKAGE_METADATA_INVALID");
        }
    }

    private static RuntimeVersionService service(
            MockEnvironment environment, BuildProperties build) {
        var factory = new StaticListableBeanFactory();
        if (build != null) factory.addBean("buildProperties", build);
        return new RuntimeVersionService(
                environment, factory.getBeanProvider(BuildProperties.class));
    }

    private static BuildProperties build() {
        var properties = new Properties();
        properties.setProperty("version", "0.1.0-SNAPSHOT");
        properties.setProperty("artifact", "nq-app");
        return new BuildProperties(properties);
    }

    private static MockEnvironment installed() {
        return new MockEnvironment()
                .withProperty("NQ_VERSION", "1.0.0")
                .withProperty("SOURCE_SHA", "a".repeat(40))
                .withProperty("RELEASE_SOURCE_HASH", "b".repeat(64))
                .withProperty("PACKAGE_MANIFEST_SHA256", "c".repeat(64))
                .withProperty("SCHEMA_VERSION", "9");
    }
}
