package com.guidinglight.nexusquant.app.distribution;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开最小制品能力供配套前端核对，不返回配置、凭据或租户信息。 */
@RestController
public class DistributionController {
    private final DistributionIdentity identity;
    public DistributionController(DistributionIdentity identity) { this.identity = identity; }
    @GetMapping("/api/distribution") public View distribution() { return new View(identity.name()); }
    public record View(String distribution) {}
}
