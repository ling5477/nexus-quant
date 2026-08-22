package com.guidinglight.nexusquant.app.config.livecontrol;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.livecontrol.application.AuthenticatedLiveControlActor;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeControlPlane;
import com.guidinglight.nexusquant.livecontrol.application.ExactPilotScopeControlResult;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Non-web, single-purpose exact pilot CLI adapter；不提供 HTTP、reflection 或 generic bean execution。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(ExactPilotScopeControlPlane.class)
@ConditionalOnProperty(
        prefix = "nq.runtime.exact-pilot-cli",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = false
)
public class ExactPilotScopeCliConfiguration {

    private static final long MAXIMUM_INPUT_BYTES = 65_536L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ExactPilotScopeCliConfiguration.class);

    @Bean
    public ApplicationRunner exactPilotScopeCliRunner(
            ExactPilotScopeControlPlane controlPlane,
            ObjectMapper objectMapper,
            ConfigurableApplicationContext context,
            @Value("${nq.runtime.exact-pilot-cli.input-path}") String inputPath
    ) {
        Objects.requireNonNull(controlPlane, "controlPlane must not be null");
        Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        Objects.requireNonNull(context, "context must not be null");
        Path input = validateInputPath(inputPath);
        return arguments -> {
            try {
                ObjectMapper strictMapper = objectMapper.copy()
                        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                ExactPilotScopeCliInput value = strictMapper.readValue(
                        Files.readAllBytes(input), ExactPilotScopeCliInput.class);
                ExactPilotScopeControlResult result = controlPlane.materializeAndBind(
                        new AuthenticatedLiveControlActor(value.creatorPrincipal()),
                        new AuthenticatedLiveControlActor(value.approverPrincipal()),
                        value.toCommand());
                LOGGER.info("EXACT_PILOT_CONTROL_RESULT={}", objectMapper.writeValueAsString(result));
            } finally {
                context.close();
            }
        };
    }

    private static Path validateInputPath(String inputPath) {
        if (inputPath == null || inputPath.isBlank()) {
            throw new IllegalArgumentException("exact pilot input path is required");
        }
        Path value = Path.of(inputPath).toAbsolutePath().normalize();
        try {
            if (!Files.isRegularFile(value, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(value)
                    || Files.size(value) < 2
                    || Files.size(value) > MAXIMUM_INPUT_BYTES) {
                throw new IllegalArgumentException("exact pilot input file is invalid");
            }
            return value.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (java.io.IOException exception) {
            throw new IllegalArgumentException("exact pilot input file is unavailable", exception);
        }
    }
}
