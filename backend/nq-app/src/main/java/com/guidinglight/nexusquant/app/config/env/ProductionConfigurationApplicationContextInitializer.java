package com.guidinglight.nexusquant.app.config.env;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Validates the final effective production datasource contract before the application context is refreshed.
 *
 * <p>Why: validating {@code NQ_PROD_DB_*} alone would leave higher-precedence Spring property sources
 * able to bypass the guard while the real DataSource consumes {@code spring.datasource.*}. This guard
 * runs after config data and other ordered context initializers, but before Hikari, Flyway, repositories,
 * or any other application bean can be created.</p>
 *
 * <p>The canonical systemd unit supplies {@code nq.production-configuration=true} and the {@code prod}
 * profile as command-line properties. The marker makes a missing or downgraded production profile a
 * deterministic startup error while preserving the existing default-local developer experience.</p>
 */
public final class ProductionConfigurationApplicationContextInitializer
        implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {

    static final String ERROR_CODE = "PROD_CONFIGURATION_INVALID";
    static final String PRODUCTION_MARKER = "nq.production-configuration";
    private static final String PROD_PROFILE = "prod";
    private static final Set<String> APPROVED_PRODUCTION_PROFILES = Set.of(PROD_PROFILE);
    private static final List<String> PRODUCTION_SECRETS = List.of(
            "nq.security.secret", "nq.account.credentials.master-key"
    );
    // These are public development defaults, not key material. Reject all repository profile defaults
    // even when they arrive through a higher-priority external source or the other secret property.
    private static final Set<String> KNOWN_DEFAULT_SECRETS = Set.of(
            "change-me-change-me-change-me-change-me",
            "local-change-me-local-change-me-123456",
            "test-change-me-test-change-me-123456",
            "change-me-account-credentials-master-key-123456",
            "local-account-credentials-master-key-123456",
            "test-account-credentials-master-key-123456",
            "gated-verify-account-credentials-master-key-123456"
    );
    private static final String DATASOURCE_URL = "spring.datasource.url";
    private static final String DATASOURCE_USERNAME = "spring.datasource.username";
    private static final String DATASOURCE_PASSWORD = "spring.datasource.password";
    private static final String DATASOURCE_DRIVER = "spring.datasource.driver-class-name";
    private static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";
    private static final Set<String> URL_IDENTITY_PARAMETERS = Set.of(
            "user",
            "password",
            "passfile",
            "sslpassword",
            "sslpasswordcallback",
            "sslkey",
            "sslcert",
            "host",
            "pghost",
            "port",
            "pgport",
            "dbname",
            "pgdbname",
            "service",
            "pgservice"
    );
    private static final List<String> ALTERNATE_DATASOURCE_IDENTITY_PROPERTIES = List.of(
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
    private static final List<String> ALTERNATE_DATASOURCE_IDENTITY_MAPS = List.of(
            "spring.datasource.hikari.data-source-properties",
            "spring.datasource.xa.properties"
    );
    private static final List<String> INDEPENDENT_FLYWAY_PROPERTIES = List.of(
            "spring.flyway.url",
            "spring.flyway.user",
            "spring.flyway.password"
    );

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        validate(applicationContext.getEnvironment());
    }

    @Override
    public int getOrder() {
        // Config-driven initializers run before this lowest-precedence guard. The canonical launcher and
        // classpath are release-admitted artifacts; validation still occurs before refresh can create a DataSource.
        return Ordered.LOWEST_PRECEDENCE;
    }

    static void validate(Environment environment) {
        List<String> violations = new ArrayList<>();
        String marker = readProperty(environment, PRODUCTION_MARKER, violations);
        boolean productionMarker = false;
        boolean markerPresent = marker != null;
        if (markerPresent) {
            if ("true".equalsIgnoreCase(marker.strip())) {
                productionMarker = true;
            } else if (!"false".equalsIgnoreCase(marker.strip())) {
                violations.add(PRODUCTION_MARKER + " must be true or false");
            }
        }

        Set<String> effectiveProfiles = effectiveProfiles(environment);
        boolean productionProfile = effectiveProfiles.contains(PROD_PROFILE);
        if (productionMarker && !APPROVED_PRODUCTION_PROFILES.equals(effectiveProfiles)) {
            violations.add("production configuration requires effective profiles exactly {prod}");
        }

        boolean productionConfiguration = productionMarker || productionProfile;
        if (productionConfiguration) {
            // Spring selects active profiles when present and otherwise uses default profiles. ConfigData
            // has already expanded include/group profiles in that selected set, so never inspect only the
            // raw spring.profiles.active property here.
            if (!APPROVED_PRODUCTION_PROFILES.equals(effectiveProfiles)) {
                violations.add("spring.profiles.active must resolve to exactly {prod}");
            }
            validateDatasource(environment, violations);
            for (String property : PRODUCTION_SECRETS) {
                String value = requiredProperty(environment, property, violations);
                if (value != null && KNOWN_DEFAULT_SECRETS.contains(value)) {
                    violations.add(property + " must not use a repository-known default in prod");
                }
            }
            validateNoAlternateDatasourceIdentity(environment, violations);
            validateNoIndependentFlywayIdentity(environment, violations);
        }

        if (!violations.isEmpty()) {
            throw new IllegalStateException(ERROR_CODE + ": " + String.join("; ", violations));
        }
    }

    private static Set<String> effectiveProfiles(Environment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        String[] selectedProfiles = activeProfiles.length == 0
                ? environment.getDefaultProfiles()
                : activeProfiles;
        return Set.copyOf(Arrays.asList(selectedProfiles));
    }

    private static void validateDatasource(Environment environment, List<String> violations) {
        String url = requiredProperty(environment, DATASOURCE_URL, violations);
        requiredProperty(environment, DATASOURCE_USERNAME, violations);
        String password = requiredProperty(environment, DATASOURCE_PASSWORD, violations);
        String driver = requiredProperty(environment, DATASOURCE_DRIVER, violations);

        if (url != null && !isCanonicalPostgresqlUrl(url)) {
            violations.add(DATASOURCE_URL + " must be a well-formed jdbc:postgresql:// URL with a database name");
        }
        if (password != null && "change_me".equalsIgnoreCase(password)) {
            violations.add(DATASOURCE_PASSWORD + " must not use a placeholder value");
        }
        if (driver != null && !POSTGRESQL_DRIVER.equals(driver)) {
            violations.add(DATASOURCE_DRIVER + " must select the canonical PostgreSQL driver in prod");
        }
    }

    private static void validateNoAlternateDatasourceIdentity(
            Environment environment,
            List<String> violations
    ) {
        for (String property : ALTERNATE_DATASOURCE_IDENTITY_PROPERTIES) {
            if (readProperty(environment, property, violations) != null) {
                violations.add(property + " is forbidden in prod; use the validated spring.datasource contract");
            }
        }
        for (String prefix : ALTERNATE_DATASOURCE_IDENTITY_MAPS) {
            try {
                Map<String, Object> values = Binder.get(environment)
                        .bind(prefix, Bindable.mapOf(String.class, Object.class))
                        .orElse(Map.of());
                if (!values.isEmpty()) {
                    violations.add(prefix + " is forbidden in prod; use the validated spring.datasource contract");
                }
            } catch (RuntimeException exception) {
                violations.add(prefix + " could not be safely resolved in prod");
            }
        }
    }

    private static void validateNoIndependentFlywayIdentity(
            Environment environment,
            List<String> violations
    ) {
        for (String property : INDEPENDENT_FLYWAY_PROPERTIES) {
            if (readProperty(environment, property, violations) != null) {
                violations.add(property + " is forbidden in prod; Flyway must inherit the validated DataSource");
            }
        }
    }

    private static String requiredProperty(
            Environment environment,
            String property,
            List<String> violations
    ) {
        String value = readProperty(environment, property, violations);
        if (value == null) {
            violations.add(property + " is required in prod");
            return null;
        }
        if (value.isBlank()) {
            violations.add(property + " must not be blank in prod");
            return null;
        }
        // Binder can retain an unresolved placeholder as text rather than throwing. It must never
        // become a usable password/key merely because that literal string is nonblank.
        if (value.contains("${")) {
            violations.add(property + " must not contain an unresolved placeholder in prod");
            return null;
        }
        if (!value.equals(value.strip())) {
            violations.add(property + " must not contain leading or trailing whitespace in prod");
            return null;
        }
        return value;
    }

    private static String readProperty(
            Environment environment,
            String property,
            List<String> violations
    ) {
        try {
            return Binder.get(environment).bind(property, String.class).orElse(null);
        } catch (RuntimeException exception) {
            // Unresolved placeholders are classified without propagating a value or nested secret-bearing cause.
            violations.add(property + " could not be resolved in prod");
            return null;
        }
    }

    private static boolean isCanonicalPostgresqlUrl(String value) {
        if (!value.startsWith("jdbc:postgresql://")) {
            return false;
        }
        try {
            URI uri = new URI(value.substring("jdbc:".length()));
            if (!"postgresql".equals(uri.getScheme())
                    || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null
                    || !hasValidHosts(uri.getRawAuthority())
                    || !hasValidDatabasePath(uri.getPath())
                    || containsCredentialQueryParameter(uri.getRawQuery())) {
                return false;
            }
            Class<?> driverClass = Class.forName(POSTGRESQL_DRIVER);
            Object parsed = driverClass
                    .getMethod("parseURL", String.class, Properties.class)
                    .invoke(null, value, new Properties());
            return parsed instanceof Properties;
        } catch (ReflectiveOperationException | LinkageError | URISyntaxException | IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean hasValidHosts(String rawAuthority) {
        if (rawAuthority == null || rawAuthority.isBlank()) {
            return false;
        }
        for (String endpoint : rawAuthority.split(",", -1)) {
            try {
                URI endpointUri = new URI("postgresql://" + endpoint + "/database");
                if (endpointUri.getHost() == null
                        || endpointUri.getHost().isBlank()
                        || endpointUri.getPort() > 65535) {
                    return false;
                }
            } catch (URISyntaxException exception) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasValidDatabasePath(String decodedPath) {
        if (decodedPath == null || !decodedPath.startsWith("/")) {
            return false;
        }
        String databaseName = decodedPath.substring(1);
        return !databaseName.isBlank() && !databaseName.contains("/");
    }

    private static boolean containsCredentialQueryParameter(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return false;
        }
        for (String pair : rawQuery.split("&")) {
            String rawName = pair.split("=", 2)[0];
            String name = URLDecoder.decode(rawName, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            if (URL_IDENTITY_PARAMETERS.contains(name)) {
                return true;
            }
        }
        return false;
    }
}
