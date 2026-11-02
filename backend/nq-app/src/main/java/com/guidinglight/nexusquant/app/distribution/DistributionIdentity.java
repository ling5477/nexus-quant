package com.guidinglight.nexusquant.app.distribution;

import java.io.IOException;
import java.util.Properties;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

/** 从制品资源读取发行身份；运行时属性只能确认身份，不能改变已打包的能力。 */
public enum DistributionIdentity {
    NQ_SELF_HOSTED, NQ_PUBLIC_DEMO;

    public static DistributionIdentity fromArtifact(ClassLoader loader) {
        try (var input = loader.getResourceAsStream("META-INF/nq-distribution.properties")) {
            if (input == null) throw new IllegalStateException("DISTRIBUTION_IDENTITY_MISSING");
            var properties = new Properties();
            properties.load(input);
            var identity = valueOf(properties.getProperty("distribution", ""));
            boolean demo = ClassUtils.isPresent(
                    "com.guidinglight.nexusquant.demo.registration.PublicDemoConfiguration", loader);
            validate(identity, demo, null);
            return identity;
        } catch (IOException | IllegalArgumentException invalid) {
            throw new IllegalStateException("DISTRIBUTION_IDENTITY_INVALID", invalid);
        }
    }

    static void validate(DistributionIdentity identity, boolean demoModule, String expected) {
        if ((identity == NQ_PUBLIC_DEMO) != demoModule)
            throw new IllegalStateException("DISTRIBUTION_MODULE_MISMATCH");
        if (expected != null && !expected.isBlank() && !identity.name().equals(expected))
            throw new IllegalStateException("DISTRIBUTION_RUNTIME_MISMATCH");
    }

    public void validateRuntime(Environment environment) {
        for (String key : java.util.List.of("nq.distribution", "NQ_DISTRIBUTION", "nq.frontend.distribution"))
            validate(this, this == NQ_PUBLIC_DEMO, environment.getProperty(key));
        if (this == NQ_SELF_HOSTED) {
            for (String profile : environment.getActiveProfiles())
                if (java.util.Set.of("public-demo", "public_demo").contains(profile))
                    throw new IllegalStateException("SELF_HOSTED_DEMO_PROFILE_FORBIDDEN");
            for (String key : java.util.List.of("nq.demo.registration-enabled", "nq.demo.mail-dispatch-enabled",
                    "nq.demo.tenant-business-enabled"))
                if (environment.getProperty(key, Boolean.class, false))
                    throw new IllegalStateException("SELF_HOSTED_DEMO_CONFIGURATION_FORBIDDEN");
        }
    }
}
