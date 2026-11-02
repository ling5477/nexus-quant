package com.guidinglight.nexusquant.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NexusQuantApplication 是后端运行时的统一启动入口。
 *
 * Why:
 * `nq-app` 在收口后只负责 Spring Boot 启动、模块扫描与运行时装配，
 * HTTP controller 和 API 适配逻辑全部下沉到 `nq-api`，避免启动模块继续承载业务边界。
 */
@SpringBootApplication(scanBasePackages = "com.guidinglight.nexusquant")
public class NexusQuantApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusQuantApplication.class, args);
    }
}
