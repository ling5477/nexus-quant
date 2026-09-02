package nqcanonical;

import org.flywaydb.core.Flyway;

/** Disposable-only migration and validation entry point; it starts no application runtime. */
public final class NqCanonicalFlywayLauncher {
    private NqCanonicalFlywayLauncher() {
    }

    public static void main(String[] args) {
        if (args.length != 6 || !args[1].startsWith("jdbc:postgresql://127.0.0.1:")) {
            throw new IllegalArgumentException("action, loopback URL, user, password, location and target are required");
        }
        String action = args[0];
        String expectedTarget = args[5];
        if (!action.equals("migrate") && !action.equals("validate")) {
            throw new IllegalArgumentException("unsupported action");
        }
        Flyway flyway = Flyway.configure()
                .dataSource(args[1], args[2], args[3])
                .locations(args[4])
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .outOfOrder(false)
                .load();
        if (action.equals("migrate")) {
            flyway.migrate();
        }
        flyway.validate();
        var current = flyway.info().current();
        var pending = flyway.info().pending();
        if (current == null || current.getVersion() == null
                || !expectedTarget.equals("V" + current.getVersion().getVersion())
                || pending.length != 0) {
            throw new IllegalStateException("Flyway current/pending state does not match repository target");
        }
        System.out.printf("PASS / NQ_FLYWAY_%s current=%s pending=%d%n",
                action.toUpperCase(), expectedTarget, pending.length);
    }
}
