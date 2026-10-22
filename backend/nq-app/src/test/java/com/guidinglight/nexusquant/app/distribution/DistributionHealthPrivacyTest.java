package com.guidinglight.nexusquant.app.distribution;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointProperties;
import org.springframework.boot.actuate.endpoint.Show;
import static org.junit.jupiter.api.Assertions.*;

class DistributionHealthPrivacyTest {
    @Test void demoOverridesDetailsAndGroupConfigurationWhileSelfHostedKeepsItsContract() {
        var properties = new HealthEndpointProperties();
        var group = new HealthEndpointProperties.Group();
        properties.setShowDetails(Show.ALWAYS); properties.setShowComponents(Show.ALWAYS);
        group.setShowDetails(Show.ALWAYS); group.setShowComponents(Show.ALWAYS);
        properties.getGroup().put("readiness", group);
        DistributionConfiguration.restrictHealth(DistributionIdentity.NQ_SELF_HOSTED, properties);
        assertEquals(Show.ALWAYS, properties.getShowDetails());
        assertEquals(Show.ALWAYS, group.getShowComponents());
        DistributionConfiguration.restrictHealth(DistributionIdentity.NQ_PUBLIC_DEMO, properties);
        assertEquals(Show.NEVER, properties.getShowDetails());
        assertEquals(Show.NEVER, properties.getShowComponents());
        assertEquals(Show.NEVER, group.getShowDetails());
        assertEquals(Show.NEVER, group.getShowComponents());
    }
}
