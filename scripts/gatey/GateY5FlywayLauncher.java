package gatey;

import org.flywaydb.core.Flyway;

/**
 * Disposable-only Flyway entry point; it starts no Spring, adapter, scheduler, or HTTP runtime.
 */
public final class GateY5FlywayLauncher {
    private GateY5FlywayLauncher() {
    }

    public static void main(String[] args) {
        if (args.length != 5 || !args[0].startsWith("jdbc:postgresql://127.0.0.1:")) {
            throw new IllegalArgumentException("loopback JDBC URL, user, password, location and target are required");
        }
        Flyway flyway = Flyway.configure()
                .dataSource(args[0], args[1], args[2])
                .locations(args[3])
                .target(args[4])
                .cleanDisabled(true)
                .load();
        var result = flyway.migrate();
        flyway.validate();
        var current = flyway.info().current();
        if (!result.success || current == null || !args[4].equals(current.getVersion().getVersion())) {
            throw new IllegalStateException("Flyway did not reach the requested target");
        }
        System.out.printf("PASS / FLYWAY_TARGET_%s migrations=%d%n", args[4], result.migrationsExecuted);
    }
}
