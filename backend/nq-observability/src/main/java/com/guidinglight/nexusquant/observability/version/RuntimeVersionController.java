package com.guidinglight.nexusquant.observability.version;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 沿用正式/api GET认证守卫；检查与更新仍由安装器拥有，本接口没有交易或系统写入入口。 */
@RestController
public class RuntimeVersionController {
    private final RuntimeVersionService versions;

    public RuntimeVersionController(RuntimeVersionService versions) {
        this.versions = versions;
    }

    @GetMapping("/api/system/version")
    public RuntimeVersionSnapshot read() {
        return versions.read();
    }
}
