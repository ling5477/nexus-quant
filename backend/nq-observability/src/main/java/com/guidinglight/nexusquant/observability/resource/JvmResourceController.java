package com.guidinglight.nexusquant.observability.resource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 沿用/api只读认证守卫；无账户参数、命令入口或敏感进程明细。 */
@RestController
public class JvmResourceController {
    private final JvmResourceService service;

    public JvmResourceController(JvmResourceService service) {
        this.service = service;
    }

    @GetMapping("/api/runtime/resources")
    public JvmResourceSampler.Snapshot read() {
        return service.read();
    }
}
