package com.guidinglight.nexusquant.observability.version;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

/** 只读取明确白名单的制品/安装元数据，不枚举环境变量、读取配置文件或执行宿主命令。 */
@Service
public class RuntimeVersionService {
    private final Environment environment;
    private final ObjectProvider<BuildProperties> builds;

    public RuntimeVersionService(Environment environment, ObjectProvider<BuildProperties> builds) {
        this.environment = environment;
        this.builds = builds;
    }

    /** 包元数据只表示安装器传入的身份，不将其冒充签名验证、最新版本查询或本次发行验收。 */
    public RuntimeVersionSnapshot read() {
        var reasons = new ArrayList<String>();
        var build = builds.getIfAvailable();
        String serviceVersion = build == null ? null : safeBuildValue(build.getVersion());
        String serviceArtifact = build == null ? null : safeBuildValue(build.getArtifact());
        if (serviceVersion == null || serviceArtifact == null)
            reasons.add("BUILD_INFO_UNAVAILABLE");
        String version = environment.getProperty("NQ_VERSION");
        String commit = environment.getProperty("SOURCE_SHA");
        String source = environment.getProperty("RELEASE_SOURCE_HASH");
        String manifest = environment.getProperty("PACKAGE_MANIFEST_SHA256");
        String schema = environment.getProperty("SCHEMA_VERSION");
        boolean missing =
                version == null
                        && commit == null
                        && source == null
                        && manifest == null
                        && schema == null;
        boolean valid =
                matches(version, "(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})")
                        && matches(commit, "[a-f0-9]{40}")
                        && matches(source, "[a-f0-9]{64}")
                        && matches(manifest, "[a-f0-9]{64}")
                        && matches(schema, "[1-9][0-9]{0,5}");
        if (!valid)
            reasons.add(
                    missing ? "INSTALLED_PACKAGE_INFO_UNAVAILABLE" : "PACKAGE_METADATA_INVALID");
        // 不能拼接不完整包字段形成看似有效的发行身份，也不能从服务artifact版本推导产品包版本。
        return new RuntimeVersionSnapshot(
                serviceVersion,
                serviceArtifact,
                valid ? version : null,
                valid ? commit : null,
                valid ? source : null,
                valid ? manifest : null,
                valid ? schema : null,
                valid ? "INSTALLER_DECLARED" : missing ? "UNAVAILABLE" : "INVALID",
                false,
                "HOST_INSTALLER",
                reasons);
    }

    private static boolean matches(String value, String pattern) {
        return value != null && value.length() <= 64 && value.matches(pattern);
    }

    private static String safeBuildValue(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,79}") ? value : null;
    }
}
