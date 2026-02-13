package com.guidinglight.nexusquant.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * NexusQuantApplication 是 Gate A 阶段的统一启动载体。
 *
 * Why:
 * docs/MODULES.md 明确 nq-app 负责装配与运行入口，
 * 并要求领域逻辑不落在启动模块。
 */
@SpringBootApplication(scanBasePackages = "com.guidinglight.nexusquant")
public class NexusQuantApplication {

    public static void main(String[] args) {
        SpringApplication.run(NexusQuantApplication.class, args);
    }
}
