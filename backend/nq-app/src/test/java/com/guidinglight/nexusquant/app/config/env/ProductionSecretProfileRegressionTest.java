package com.guidinglight.nexusquant.app.config.env;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import com.guidinglight.nexusquant.security.token.JwtTokenService;
import com.guidinglight.nexusquant.security.token.JwtTokenSettings;
import com.guidinglight.nexusquant.security.token.TokenClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/** Real ConfigData/profile expansion and automatic initializer discovery; no ambient DB/config sources. */
@ExtendWith(OutputCaptureExtension.class)
class ProductionSecretProfileRegressionTest {
    private static final String JWT_KEY = "F008_JWT_TEST_" + UUID.randomUUID() + UUID.randomUUID();
    private static final String MASTER_KEY = "F008_MASTER_TEST_" + UUID.randomUUID() + UUID.randomUUID();
    private static final AtomicInteger BEANS_CREATED = new AtomicInteger();
    private static final String JWT_PROPERTY = "nq.security.secret";
    private static final String MASTER_PROPERTY = "nq.account.credentials.master-key";

    @BeforeEach
    void reset() {
        BEANS_CREATED.set(0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "ci", "paper", "freeze", "gated-verify",
            "gatew-okx-readonly-soak", "gatey-readonly-qualification", "public-marketdata-manual", "unknown"})
    void rejectsEveryNonProductionAdjunct(String profile, CapturedOutput output) {
        Map<String, Object> properties = valid();
        properties.put("spring.profiles.active", "prod," + profile);
        reject(properties, Map.of(), "exactly {prod}", output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"include", "group", "alias", "json", "cli", "system"})
    void rejectsExpandedProfilesFromEverySupportedSource(String source, CapturedOutput output) {
        Map<String, Object> properties = valid();
        Map<String, Object> external = new LinkedHashMap<>();
        switch (source) {
            case "include" -> external.put("SPRING_PROFILES_INCLUDE", "local");
            case "group" -> properties.put("spring.profiles.group.prod", "local");
            case "alias" -> {
                properties.put("spring.profiles.active", "production-alias");
                properties.put("spring.profiles.group.production-alias", "prod,local");
            }
            case "json" -> external.put("SPRING_APPLICATION_JSON", "{\"spring\":{\"profiles\":{\"include\":\"local\"}}}");
            case "cli" -> { /* Supplied to SpringApplication.run below. */ }
            case "system" -> properties.put("spring.profiles.include", "test");
            default -> throw new AssertionError("Unknown fixture");
        }
        Exception error = assertThrows(Exception.class, () -> start(properties, external,
                source.equals("cli") ? new String[]{"--spring.profiles.include=local"} : new String[0]));
        assertFailure(error, "exactly {prod}", output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "ci", ""})
    void markerRequiresProductionEvenWithValidSecrets(String profile, CapturedOutput output) {
        Map<String, Object> properties = valid();
        if (profile.isEmpty()) {
            properties.remove("spring.profiles.active");
        } else {
            properties.put("spring.profiles.active", profile);
        }
        reject(properties, Map.of(), "effective profiles exactly {prod}", output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "false", "true"})
    void prodAlwaysValidatesSecretsRegardlessOfMarker(String marker, CapturedOutput output) {
        Map<String, Object> properties = valid();
        if (marker.equals("absent")) properties.remove("nq.production-configuration");
        else properties.put("nq.production-configuration", marker);
        properties.remove(JWT_PROPERTY);
        reject(properties, Map.of(), JWT_PROPERTY, output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "false", "true"})
    void defaultProdAlwaysValidatesSecretsRegardlessOfMarker(String marker, CapturedOutput output) {
        Map<String, Object> properties = valid();
        properties.remove("spring.profiles.active");
        if (marker.equals("absent")) {
            properties.remove("nq.production-configuration");
        } else {
            properties.put("nq.production-configuration", marker);
        }
        properties.put(JWT_PROPERTY, publicDefaults().getFirst());

        reject(properties, Map.of(), JWT_PROPERTY, output,
                "--spring.profiles.active=", "--spring.profiles.default=prod");
    }

    @Test
    void rejectsDefaultProductionProfileMixedWithLocal(CapturedOutput output) {
        Map<String, Object> properties = valid();
        properties.remove("spring.profiles.active");
        properties.remove("nq.production-configuration");

        reject(properties, Map.of(), "exactly {prod}", output,
                "--spring.profiles.active=", "--spring.profiles.default=prod,local");
    }

    @Test
    void acceptsValidDefaultProductionProfileWithoutMarker(CapturedOutput output) {
        Map<String, Object> properties = valid();
        properties.remove("spring.profiles.active");
        properties.remove("nq.production-configuration");

        try (var context = start(properties, Map.of(),
                "--spring.profiles.active=", "--spring.profiles.default=prod")) {
            assertEquals(List.of(), List.of(context.getEnvironment().getActiveProfiles()));
            assertEquals(List.of("prod"), List.of(context.getEnvironment().getDefaultProfiles()));
        }
        assertRedacted(output.getAll());
    }

    @ParameterizedTest
    @MethodSource("secretCases")
    void rejectsMissingBlankWhitespaceAndAllPublicDefaults(String caseId, CapturedOutput output) {
        String[] parts = caseId.split(":", 2);
        String property = parts[0].equals("jwt") ? JWT_PROPERTY : MASTER_PROPERTY;
        Map<String, Object> properties = valid();
        switch (parts[1]) {
            case "missing" -> properties.remove(property);
            case "blank" -> properties.put(property, "");
            case "whitespace" -> properties.put(property, " \t ");
            default -> properties.put(property, publicDefaults().get(Integer.parseInt(parts[1])));
        }
        reject(properties, Map.of(), property, output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"canonical-env", "spring-env", "json", "system", "cli", "import"})
    void acceptsUniqueSecretsThroughEffectiveSpringSources(String source, CapturedOutput output) {
        Map<String, Object> properties = valid();
        properties.remove(JWT_PROPERTY);
        properties.remove(MASTER_PROPERTY);
        Map<String, Object> external = new LinkedHashMap<>();
        List<String> arguments = new ArrayList<>();
        switch (source) {
            case "canonical-env" -> {
                external.put("NQ_SECURITY_SECRET", JWT_KEY);
                external.put("NQ_ACCOUNT_CREDENTIALS_MASTER_KEY", MASTER_KEY);
            }
            case "spring-env" -> {
                external.put("NQ_SECURITY_SECRET", JWT_KEY);
                external.put("NQ_ACCOUNT_CREDENTIALS_MASTERKEY", MASTER_KEY);
            }
            case "json" -> external.put("SPRING_APPLICATION_JSON", "{\"nq\":{\"security\":{\"secret\":\""
                    + JWT_KEY + "\"},\"account\":{\"credentials\":{\"masterKey\":\"" + MASTER_KEY + "\"}}}}");
            case "cli" -> {
                arguments.add("--nq.security.secret=" + JWT_KEY);
                arguments.add("--nq.account.credentials.master-key=" + MASTER_KEY);
            }
            case "system" -> {
                properties.put(JWT_PROPERTY, JWT_KEY);
                properties.put(MASTER_PROPERTY, MASTER_KEY);
            }
            case "import" -> {
                properties.put("spring.config.import", "classpath:production-secret-import.properties");
                external.put("F008_JWT_INPUT", JWT_KEY);
                external.put("F008_MASTER_INPUT", MASTER_KEY);
            }
            default -> throw new AssertionError("Unknown fixture");
        }
        try (var context = start(properties, external, arguments.toArray(String[]::new))) {
            assertTrue(JWT_KEY.equals(context.getEnvironment().getProperty(JWT_PROPERTY)), "JWT effective identity");
            // Compare using Binder, which handles the camelCase JSON alias identically to the guard.
            assertTrue(MASTER_KEY.equals(org.springframework.boot.context.properties.bind.Binder.get(context.getEnvironment())
                    .bind(MASTER_PROPERTY, String.class).orElseThrow(IllegalStateException::new)), "Master effective identity");
            assertEquals(List.of("prod"), List.of(context.getEnvironment().getActiveProfiles()));
        }
        assertRedacted(output.getAll());
    }

    @Test
    void higherPrecedenceInvalidSecretsCannotHideBehindValidLowerValues(CapturedOutput output) {
        Map<String, Object> properties = valid();
        Map<String, Object> external = Map.of("SPRING_APPLICATION_JSON",
                "{\"nq\":{\"security\":{\"secret\":\" \"},\"account\":{\"credentials\":{\"masterKey\":\" \"}}}}");
        reject(properties, external, JWT_PROPERTY, output);
    }

    @ParameterizedTest
    @ValueSource(strings = {"spring.datasource.url", "spring.datasource.username", "spring.datasource.password",
            "nq.security.secret", "nq.account.credentials.master-key"})
    void actualProdYamlRequiresEachExternalValue(String property, CapturedOutput output) {
        Map<String, Object> properties = valid();
        properties.remove(property);
        reject(properties, Map.of(), property, output);
    }

    @Test
    void rejectsOldDefaultSignedRolesUnderValidProductionKey(CapturedOutput output) {
        try (var context = start(valid(), Map.of())) {
            String effectiveKey = context.getEnvironment().getProperty(JWT_PROPERTY);
            var runtime = new JwtTokenService(new JwtTokenSettings("nexus-quant", effectiveKey, Duration.ofMinutes(5)));
            var oldKeySigner = new JwtTokenService(new JwtTokenSettings("nexus-quant", publicDefaults().getFirst(), Duration.ofMinutes(5)));
            Instant now = Instant.now();
            var claims = new TokenClaims("synthetic", "synthetic", List.of("ADMIN"), now,
                    now.plusSeconds(120), "nexus-quant", UUID.randomUUID().toString());
            assertTrue(runtime.parse(runtime.issue(claims)).isPresent(), "Current key positive control");
            assertTrue(runtime.parse(oldKeySigner.issue(claims)).isEmpty(), "Old default key must fail signature validation");
        }
        assertRedacted(output.getAll());
    }

    @ParameterizedTest
    @ValueSource(strings = {"local", "test", "ci", ""})
    void preservesDeveloperAndTestProfilesWithoutProductionKeys(String profile) {
        Map<String, Object> properties = new LinkedHashMap<>();
        if (!profile.isEmpty()) properties.put("spring.profiles.active", profile);
        try (var context = start(properties, Map.of())) {
            assertEquals(List.of(profile.isEmpty() ? "local" : profile), List.of(context.getEnvironment().getActiveProfiles()));
        }
    }

    @Test
    void rejectsBeforeRealDataSourceAndFlywayAutoConfiguration(CapturedOutput output) {
        Map<String, Object> properties = valid();
        properties.remove(MASTER_PROPERTY);
        properties.put("spring.flyway.enabled", "true");
        Exception error = assertThrows(Exception.class, () -> application(properties, Map.of(), DatabaseAutoConfiguration.class).run());
        assertFailure(error, MASTER_PROPERTY, output);
        assertFalse(output.getAll().contains("HikariPool"), "Pool must not initialize");
        assertFalse(output.getAll().contains("FlywayExecutor"), "Flyway must not initialize");
    }

    @ParameterizedTest
    @ValueSource(strings = {"absent", "false"})
    void defaultProdKnownSecretFailsBeforeDataSourceAndFlyway(String marker, CapturedOutput output) {
        Map<String, Object> properties = valid();
        properties.remove("spring.profiles.active");
        if (marker.equals("absent")) {
            properties.remove("nq.production-configuration");
        } else {
            properties.put("nq.production-configuration", marker);
        }
        properties.put(JWT_PROPERTY, publicDefaults().getFirst());
        properties.put("spring.flyway.enabled", "true");

        Exception error = assertThrows(Exception.class, () -> application(
                properties, Map.of(), DatabaseAutoConfiguration.class).run(
                "--spring.profiles.active=", "--spring.profiles.default=prod"));

        assertFailure(error, JWT_PROPERTY, output);
        assertFalse(output.getAll().contains("HikariPool"), "Pool must not initialize");
        assertFalse(output.getAll().contains("FlywayExecutor"), "Flyway must not initialize");
    }

    private static Stream<String> secretCases() {
        return Stream.of("jwt", "master").flatMap(property -> Stream.concat(
                Stream.of(property + ":missing", property + ":blank", property + ":whitespace"),
                java.util.stream.IntStream.range(0, publicDefaults().size()).mapToObj(i -> property + ":" + i)));
    }

    private static List<String> publicDefaults() {
        return List.of("change-me-change-me-change-me-change-me", "local-change-me-local-change-me-123456",
                "test-change-me-test-change-me-123456", "change-me-account-credentials-master-key-123456",
                "local-account-credentials-master-key-123456", "test-account-credentials-master-key-123456",
                "gated-verify-account-credentials-master-key-123456");
    }

    private static Map<String, Object> valid() {
        return new LinkedHashMap<>(Map.of("spring.profiles.active", "prod", "nq.production-configuration", "true",
                "spring.datasource.url", "jdbc:postgresql://synthetic.invalid:5432/fixture",
                "spring.datasource.username", "synthetic", "spring.datasource.password", "synthetic-db",
                JWT_PROPERTY, JWT_KEY, MASTER_PROPERTY, MASTER_KEY));
    }

    private static ConfigurableApplicationContext start(Map<String, Object> properties,
            Map<String, Object> external, String... arguments) {
        return application(properties, external, ProbeConfiguration.class).run(arguments);
    }

    private static SpringApplication application(Map<String, Object> properties,
            Map<String, Object> external, Class<?> source) {
        StandardEnvironment environment = new StandardEnvironment();
        // Hermetic: never inherit a developer's actual environment, system datasource, or config file.
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().addLast(new MapPropertySource("systemProperties", properties));
        environment.getPropertySources().addLast(new SystemEnvironmentPropertySource("systemEnvironment", external));
        SpringApplication application = new SpringApplication(source);
        application.setEnvironment(environment);
        application.setDefaultProperties(Map.of("spring.config.location", "classpath:/application.yml"));
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);
        return application;
    }

    private static void reject(Map<String, Object> properties, Map<String, Object> external,
            String classification, CapturedOutput output, String... arguments) {
        Exception error = assertThrows(Exception.class, () -> start(properties, external, arguments));
        assertFailure(error, classification, output);
    }

    private static void assertFailure(Throwable error, String classification, CapturedOutput output) {
        assertTrue(error.getMessage().contains("PROD_CONFIGURATION_INVALID"), "Stable error classification");
        assertTrue(error.getMessage().contains(classification), "Expected property classification");
        for (Throwable current = error; current != null; current = current.getCause()) assertRedacted(current.toString());
        assertRedacted(output.getAll());
        assertEquals(0, BEANS_CREATED.get(), "Guard must run before any application bean or database consumer");
    }

    private static void assertRedacted(String text) {
        assertFalse(text.contains(JWT_KEY), "JWT value disclosed");
        assertFalse(text.contains(MASTER_KEY), "Master key value disclosed");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        Object creationProbe() {
            BEANS_CREATED.incrementAndGet();
            return new Object();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @Import({DataSourceAutoConfiguration.class, FlywayAutoConfiguration.class})
    static class DatabaseAutoConfiguration {
    }
}
