package com.guidinglight.nexusquant.app.config.env;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(OutputCaptureExtension.class)
class ProductionConfigurationApplicationContextInitializerTest {

    private static final String VALID_URL = "jdbc:postgresql://synthetic.invalid:5432/nexus_quant";
    private static final String VALID_USERNAME = "synthetic_user";
    private static final String SYNTHETIC_SECRET = "synthetic-secret-never-emit";

    @BeforeEach
    void resetOutboundProbe() {
        CountingDataSource.constructionAttempts.set(0);
        CountingDataSource.connectionAttempts.set(0);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidDatasourceCases")
    void shouldRejectInvalidEffectiveProductionDatasource(
            String description,
            Map<String, String> properties,
            String expectedClassification
    ) {
        MockEnvironment environment = productionEnvironment(properties);

        IllegalStateException failure;
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.setEnvironment(environment);
            context.registerBean(DataSource.class, CountingDataSource::new);
            failure = assertThrows(
                    IllegalStateException.class,
                    () -> new ProductionConfigurationApplicationContextInitializer().initialize(context),
                    description
            );
        }

        assertTrue(failure.getMessage().startsWith("PROD_CONFIGURATION_INVALID:"));
        assertTrue(failure.getMessage().contains(expectedClassification));
        assertFalse(failure.getMessage().contains(SYNTHETIC_SECRET));
        assertEquals(0, CountingDataSource.constructionAttempts.get());
        assertEquals(0, CountingDataSource.connectionAttempts.get());
    }

    @Test
    void shouldValidateFinalEffectiveSpringPropertiesAfterPrecedenceResolution() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        environment.getPropertySources().addLast(new MapPropertySource("application-prod", Map.of(
                "spring.datasource.url", "jdbc:postgresql://db-prod:5432/nexus_quant",
                "spring.datasource.username", "postgres",
                "spring.datasource.password", "change_me"
        )));
        environment.getPropertySources().addFirst(new MapPropertySource("commandLineArgs", validProperties()));

        assertDoesNotThrow(() -> ProductionConfigurationApplicationContextInitializer.validate(environment));

        Map<String, Object> blankHigherPrecedence = new LinkedHashMap<>(validProperties());
        blankHigherPrecedence.put("spring.datasource.password", " ");
        environment.getPropertySources().replace(
                "commandLineArgs",
                new MapPropertySource("commandLineArgs", blankHigherPrecedence)
        );
        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ProductionConfigurationApplicationContextInitializer.validate(environment)
        );
        assertTrue(failure.getMessage().contains("spring.datasource.password must not be blank in prod"));
    }

    @ParameterizedTest
    @MethodSource("independentFlywayProperties")
    void shouldRejectIndependentFlywayConnectionIdentity(String property) {
        Map<String, String> properties = new LinkedHashMap<>(validStringProperties());
        properties.put(property, "synthetic-independent-value");
        MockEnvironment environment = productionEnvironment(properties);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ProductionConfigurationApplicationContextInitializer.validate(environment)
        );

        assertTrue(failure.getMessage().contains(
                property + " is forbidden in prod; Flyway must inherit the validated DataSource"
        ));
    }

    @ParameterizedTest
    @MethodSource("alternateDatasourceIdentityProperties")
    void shouldRejectAlternateDatasourceConnectionIdentity(String property) {
        Map<String, String> properties = new LinkedHashMap<>(validStringProperties());
        properties.put(property, SYNTHETIC_SECRET);
        MockEnvironment environment = productionEnvironment(properties);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ProductionConfigurationApplicationContextInitializer.validate(environment)
        );

        assertTrue(failure.getMessage().contains(
                property + " is forbidden in prod; use the validated spring.datasource contract"
        ));
        assertFalse(failure.getMessage().contains(SYNTHETIC_SECRET));
    }

    @ParameterizedTest
    @MethodSource("alternateDatasourceIdentityMaps")
    void shouldRejectAlternateDatasourceConnectionIdentityMap(String prefix) {
        Map<String, String> properties = new LinkedHashMap<>(validStringProperties());
        properties.put(prefix + ".password", SYNTHETIC_SECRET);
        MockEnvironment environment = productionEnvironment(properties);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ProductionConfigurationApplicationContextInitializer.validate(environment)
        );

        assertTrue(failure.getMessage().contains(
                prefix + " is forbidden in prod; use the validated spring.datasource contract"
        ));
        assertFalse(failure.getMessage().contains(SYNTHETIC_SECRET));
    }

    @Test
    void shouldRejectRelaxedCamelCaseAlternateIdentityFromJsonStylePropertySource() {
        MockEnvironment environment = productionEnvironment(validStringProperties());
        environment.getPropertySources().addFirst(new MapPropertySource("spring.application.json", Map.of(
                "spring.datasource.hikari.jdbcUrl", "jdbc:mysql://synthetic.invalid/nexus_quant",
                "spring.datasource.jndiName", "java:comp/env/jdbc/nexusQuant"
        )));

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ProductionConfigurationApplicationContextInitializer.validate(environment)
        );

        assertTrue(failure.getMessage().contains("spring.datasource.hikari.jdbc-url is forbidden in prod"));
        assertTrue(failure.getMessage().contains("spring.datasource.jndi-name is forbidden in prod"));
    }

    @Test
    void shouldRequireEffectiveProdProfileSetForCanonicalProductionMarker() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("nq.production-configuration", "true");
        environment.setActiveProfiles("local");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ProductionConfigurationApplicationContextInitializer.validate(environment)
        );

        assertTrue(failure.getMessage().contains("production configuration requires effective profiles exactly {prod}"));
    }

    @Test
    void shouldPreserveLocalTestAndCiWithoutProductionCredentials() {
        for (String profile : new String[]{"local", "test", "ci"}) {
            MockEnvironment environment = new MockEnvironment();
            environment.setActiveProfiles(profile);
            assertDoesNotThrow(() -> ProductionConfigurationApplicationContextInitializer.validate(environment), profile);
        }
    }

    @Test
    void shouldAcceptSyntheticProductionConfigurationWithoutCreatingDatasource() {
        MockEnvironment environment = productionEnvironment(validStringProperties());
        environment.setProperty("nq.production-configuration", "true");

        assertDoesNotThrow(() -> ProductionConfigurationApplicationContextInitializer.validate(environment));
        assertEquals(0, CountingDataSource.constructionAttempts.get());
        assertEquals(0, CountingDataSource.connectionAttempts.get());
    }

    @Test
    void shouldAllowNonCredentialPostgresqlUrlOptions() {
        Map<String, String> properties = new LinkedHashMap<>(validStringProperties());
        properties.put("spring.datasource.url", VALID_URL + "?sslmode=require");
        MockEnvironment environment = productionEnvironment(properties);

        assertDoesNotThrow(() -> ProductionConfigurationApplicationContextInitializer.validate(environment));
    }

    @ParameterizedTest
    @MethodSource("urlIdentityOverrideQueries")
    void shouldRejectPostgresqlUrlIdentityOverrideQuery(String query) {
        Map<String, String> properties = new LinkedHashMap<>(validStringProperties());
        properties.put("spring.datasource.url", VALID_URL + "?" + query);
        MockEnvironment environment = productionEnvironment(properties);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> ProductionConfigurationApplicationContextInitializer.validate(environment)
        );

        assertTrue(failure.getMessage().contains("spring.datasource.url must be a well-formed"));
        assertFalse(failure.getMessage().contains("override.invalid"));
    }

    @Test
    void shouldResolveCanonicalProductionEnvironmentKeysWithoutDatabaseAccess() {
        SpringApplication application = new SpringApplication(PositiveConfiguration.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);

        try (ConfigurableApplicationContext context = application.run(
                "--spring.main.web-application-type=none",
                "--spring.profiles.active=prod",
                "--nq.production-configuration=true",
                "--NQ_PROD_DB_URL=" + VALID_URL,
                "--NQ_PROD_DB_USER=" + VALID_USERNAME,
                "--NQ_PROD_DB_PASSWORD=" + SYNTHETIC_SECRET,
                "--nq.security.secret=" + java.util.UUID.randomUUID() + java.util.UUID.randomUUID(),
                "--nq.account.credentials.master-key=" + java.util.UUID.randomUUID()
        )) {
            assertTrue(context.getEnvironment().matchesProfiles("prod"));
        }

        assertEquals(0, CountingDataSource.constructionAttempts.get());
        assertEquals(0, CountingDataSource.connectionAttempts.get());
    }

    @Test
    void shouldFailBeforeDatasourceCreationOrConnectionAndNeverEmitSecret(CapturedOutput output) {
        SpringApplication application = new SpringApplication(OutboundProbeConfiguration.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);

        Exception failure = assertThrows(Exception.class, () -> application.run(
                "--spring.main.web-application-type=none",
                "--spring.profiles.active=prod",
                "--nq.production-configuration=true",
                "--spring.datasource.url=jdbc:postgresql://",
                "--spring.datasource.username=" + VALID_USERNAME,
                "--spring.datasource.password=" + SYNTHETIC_SECRET
        ));

        String messages = exceptionMessages(failure);
        assertTrue(messages.contains("PROD_CONFIGURATION_INVALID"));
        assertFalse(messages.contains(SYNTHETIC_SECRET));
        assertFalse(output.getAll().contains(SYNTHETIC_SECRET));
        assertEquals(0, CountingDataSource.constructionAttempts.get());
        assertEquals(0, CountingDataSource.connectionAttempts.get());
    }

    @Test
    void shouldFailBeforeLocalDatasourceWhenCanonicalProductionProfileIsMissing() {
        SpringApplication application = new SpringApplication(OutboundProbeConfiguration.class);
        application.setBannerMode(Banner.Mode.OFF);
        application.setLogStartupInfo(false);
        application.setRegisterShutdownHook(false);

        Exception failure = assertThrows(Exception.class, () -> application.run(
                "--spring.main.web-application-type=none",
                "--nq.production-configuration=true"
        ));

        assertTrue(exceptionMessages(failure).contains("production configuration requires effective profiles exactly {prod}"));
        assertEquals(0, CountingDataSource.constructionAttempts.get());
        assertEquals(0, CountingDataSource.connectionAttempts.get());
    }

    private static Stream<Arguments> invalidDatasourceCases() {
        return Stream.of(
                Arguments.of("missing URL", propertiesWithout("spring.datasource.url"),
                        "spring.datasource.url is required in prod"),
                Arguments.of("blank URL", propertiesWith("spring.datasource.url", ""),
                        "spring.datasource.url must not be blank in prod"),
                Arguments.of("whitespace URL", propertiesWith("spring.datasource.url", "   "),
                        "spring.datasource.url must not be blank in prod"),
                Arguments.of("malformed URL", propertiesWith("spring.datasource.url", "jdbc:postgresql://"),
                        "spring.datasource.url must be a well-formed"),
                Arguments.of("non-PostgreSQL URL", propertiesWith(
                                "spring.datasource.url", "jdbc:mysql://synthetic.invalid:3306/nexus_quant"),
                        "spring.datasource.url must be a well-formed"),
                Arguments.of("out-of-range port", propertiesWith(
                                "spring.datasource.url", "jdbc:postgresql://synthetic.invalid:99999/nexus_quant"),
                        "spring.datasource.url must be a well-formed"),
                Arguments.of("empty host", propertiesWith(
                                "spring.datasource.url", "jdbc:postgresql://:5432/nexus_quant"),
                        "spring.datasource.url must be a well-formed"),
                Arguments.of("multi-segment database path", propertiesWith(
                                "spring.datasource.url", "jdbc:postgresql://synthetic.invalid/nexus/quant"),
                        "spring.datasource.url must be a well-formed"),
                Arguments.of("whitespace database name", propertiesWith(
                                "spring.datasource.url", "jdbc:postgresql://synthetic.invalid/%20"),
                        "spring.datasource.url must be a well-formed"),
                Arguments.of("URL user-info", propertiesWith(
                                "spring.datasource.url",
                                "jdbc:postgresql://synthetic_user:" + SYNTHETIC_SECRET
                                        + "@synthetic.invalid/nexus_quant"),
                        "spring.datasource.url must be a well-formed"),
                Arguments.of("URL password query", propertiesWith(
                                "spring.datasource.url", VALID_URL + "?password=" + SYNTHETIC_SECRET),
                        "spring.datasource.url must be a well-formed"),
                Arguments.of("missing username", propertiesWithout("spring.datasource.username"),
                        "spring.datasource.username is required in prod"),
                Arguments.of("blank username", propertiesWith("spring.datasource.username", ""),
                        "spring.datasource.username must not be blank in prod"),
                Arguments.of("whitespace username", propertiesWith("spring.datasource.username", "   "),
                        "spring.datasource.username must not be blank in prod"),
                Arguments.of("missing password", propertiesWithout("spring.datasource.password"),
                        "spring.datasource.password is required in prod"),
                Arguments.of("blank password", propertiesWith("spring.datasource.password", ""),
                        "spring.datasource.password must not be blank in prod"),
                Arguments.of("whitespace password", propertiesWith("spring.datasource.password", "   "),
                        "spring.datasource.password must not be blank in prod"),
                Arguments.of("placeholder password", propertiesWith("spring.datasource.password", "change_me"),
                        "spring.datasource.password must not use a placeholder value")
        );
    }

    private static Stream<String> independentFlywayProperties() {
        return Stream.of("spring.flyway.url", "spring.flyway.user", "spring.flyway.password");
    }

    private static Stream<String> alternateDatasourceIdentityProperties() {
        return Stream.of(
                "spring.datasource.jndi-name",
                "spring.datasource.type",
                "spring.datasource.hikari.jdbc-url",
                "spring.datasource.hikari.username",
                "spring.datasource.hikari.password",
                "spring.datasource.hikari.driver-class-name",
                "spring.datasource.hikari.data-source-class-name",
                "spring.datasource.hikari.data-source-j-n-d-i",
                "spring.datasource.hikari.data-source-jndi",
                "spring.datasource.xa.data-source-class-name"
        );
    }

    private static Stream<String> alternateDatasourceIdentityMaps() {
        return Stream.of(
                "spring.datasource.hikari.data-source-properties",
                "spring.datasource.xa.properties"
        );
    }

    private static Stream<String> urlIdentityOverrideQueries() {
        return Stream.of(
                "host=override.invalid",
                "PGHOST=override.invalid",
                "port=6543",
                "PGPORT=6543",
                "dbname=other",
                "PGDBNAME=other",
                "service=external-service",
                "PGSERVICE=external-service"
        );
    }

    private static MockEnvironment productionEnvironment(Map<String, String> properties) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        properties.forEach(environment::setProperty);
        return environment;
    }

    private static Map<String, Object> validProperties() {
        return new LinkedHashMap<>(Map.of(
                "spring.datasource.url", VALID_URL,
                "spring.datasource.username", VALID_USERNAME,
                "spring.datasource.password", SYNTHETIC_SECRET,
                "spring.datasource.driver-class-name", "org.postgresql.Driver",
                "nq.security.secret", java.util.UUID.randomUUID().toString(),
                "nq.account.credentials.master-key", java.util.UUID.randomUUID().toString()
        ));
    }

    private static Map<String, String> validStringProperties() {
        return new LinkedHashMap<>(Map.of(
                "spring.datasource.url", VALID_URL,
                "spring.datasource.username", VALID_USERNAME,
                "spring.datasource.password", SYNTHETIC_SECRET,
                "spring.datasource.driver-class-name", "org.postgresql.Driver",
                "nq.security.secret", java.util.UUID.randomUUID().toString(),
                "nq.account.credentials.master-key", java.util.UUID.randomUUID().toString()
        ));
    }

    private static Map<String, String> propertiesWithout(String property) {
        Map<String, String> properties = validStringProperties();
        properties.remove(property);
        return properties;
    }

    private static Map<String, String> propertiesWith(String property, String value) {
        Map<String, String> properties = validStringProperties();
        properties.put(property, value);
        return properties;
    }

    private static String exceptionMessages(Throwable throwable) {
        StringBuilder messages = new StringBuilder();
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current.getMessage() != null) {
                messages.append(current.getMessage()).append('\n');
            }
        }
        return messages.toString();
    }

    @Configuration(proxyBeanMethods = false)
    static class OutboundProbeConfiguration {

        @Bean
        DataSource dataSource() {
            return new CountingDataSource();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PositiveConfiguration {
    }

    static final class CountingDataSource extends AbstractDataSource {

        private static final AtomicInteger constructionAttempts = new AtomicInteger();
        private static final AtomicInteger connectionAttempts = new AtomicInteger();

        CountingDataSource() {
            constructionAttempts.incrementAndGet();
        }

        @Override
        public Connection getConnection() throws SQLException {
            connectionAttempts.incrementAndGet();
            throw new SQLException("OUTBOUND_CONNECTION_ATTEMPTED");
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return getConnection();
        }
    }
}
