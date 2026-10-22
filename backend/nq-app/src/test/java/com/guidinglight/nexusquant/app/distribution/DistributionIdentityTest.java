package com.guidinglight.nexusquant.app.distribution;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class DistributionIdentityTest {
    @Test void rejectsMixedArtifactsAndRuntimePromotion() {
        assertThrows(IllegalStateException.class, () -> DistributionIdentity.validate(DistributionIdentity.NQ_SELF_HOSTED, true, null));
        assertThrows(IllegalStateException.class, () -> DistributionIdentity.validate(DistributionIdentity.NQ_PUBLIC_DEMO, false, null));
        assertThrows(IllegalStateException.class, () -> DistributionIdentity.validate(DistributionIdentity.NQ_SELF_HOSTED, false, "NQ_PUBLIC_DEMO"));
        assertThrows(IllegalStateException.class, () -> DistributionIdentity.validate(DistributionIdentity.NQ_PUBLIC_DEMO, true, "NQ_SELF_HOSTED"));
        for (String key : java.util.List.of("nq.distribution", "NQ_DISTRIBUTION", "nq.frontend.distribution")) {
            assertThrows(IllegalStateException.class, () -> DistributionIdentity.NQ_SELF_HOSTED.validateRuntime(
                    new MockEnvironment().withProperty(key,"NQ_PUBLIC_DEMO")));
        }
    }

    @Test void selfHostedRejectsDemoConfigurationInsteadOfPretendingRegistrationExists() {
        assertDoesNotThrow(() -> DistributionIdentity.NQ_SELF_HOSTED.validateRuntime(new MockEnvironment()));
        var environment = new MockEnvironment();
        environment.setActiveProfiles("public-demo");
        assertThrows(IllegalStateException.class, () -> DistributionIdentity.NQ_SELF_HOSTED.validateRuntime(environment));
        for (String key : java.util.List.of("nq.demo.registration-enabled", "nq.demo.mail-dispatch-enabled",
                "nq.demo.tenant-business-enabled"))
            assertThrows(IllegalStateException.class, () -> DistributionIdentity.NQ_SELF_HOSTED.validateRuntime(
                    new MockEnvironment().withProperty(key,"true")));
    }
}
