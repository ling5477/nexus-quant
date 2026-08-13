package com.guidinglight.nexusquant.app.livecontrol.executionworker;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;

/**
 * 独立、loopback-only、disposable fake venue process。
 */
public final class DisposableFakeVenueLauncher {

    private static final int MAX_BODY = 8 * 1024;

    private DisposableFakeVenueLauncher() {
    }

    public static void main(String[] arguments) throws Exception {
        Arguments args = Arguments.parse(arguments);
        Path store = requireDisposableStore(args.store());
        HttpServer server = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), args.port()), 16);
        FakeStore fakeStore = new FakeStore(store);
        server.createContext("/v1/fake/place", exchange -> fakeStore.mutate(exchange, false));
        server.createContext("/v1/fake/cancel", exchange -> fakeStore.mutate(exchange, true));
        server.createContext("/v1/fake/query", fakeStore::query);
        server.createContext("/v1/fake/metrics", fakeStore::metrics);
        server.createContext("/health", exchange -> respond(exchange, 200, "status=UP\nmode=FAKE_ONLY\n"));
        server.setExecutor(Executors.newFixedThreadPool(4, Thread.ofPlatform()
                .name("nq-disposable-fake-venue-", 0).factory()));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        server.start();
        System.out.printf("DISPOSABLE_FAKE_VENUE_READY port=%d store=%s%n", args.port(), store.getFileName());
    }

    private static Path requireDisposableStore(Path supplied) throws IOException {
        Path repo = Path.of(System.getProperty("nq.fake-worker.repo-root", "."))
                .toAbsolutePath().normalize().toRealPath();
        Path artifacts = repo.resolve("artifacts").normalize();
        Path value = supplied.toAbsolutePath().normalize();
        if (!value.startsWith(artifacts)
                || !value.getFileName().toString().matches("fake-venue-store-tmp-[a-z0-9-]+\\.properties")) {
            throw new IllegalArgumentException("fake store must be an exact disposable project artifact path");
        }
        Files.createDirectories(artifacts);
        if (Files.isSymbolicLink(artifacts) || Files.exists(value, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(value) || Files.isDirectory(value, LinkOption.NOFOLLOW_LINKS))) {
            throw new IllegalArgumentException("fake store must not be a link or directory");
        }
        return value;
    }

    private static final class FakeStore {
        private final Path path;

        private FakeStore(Path path) {
            this.path = path;
        }

        synchronized void mutate(HttpExchange exchange, boolean cancel) throws IOException {
            Map<String, String> request = request(exchange);
            String key = required(request, "clientOrderId");
            Properties state = load();
            long calls = number(state, key, "mutationCallCount") + 1;
            state.setProperty(field(key, "mutationCallCount"), Long.toString(calls));
            String remoteId = state.getProperty(field(key, "remoteOrderId"));
            if (remoteId == null) {
                remoteId = "fake-order-" + sha256(key).substring(0, 20);
                state.setProperty(field(key, "remoteOrderId"), remoteId);
                state.setProperty(field(key, "remoteOrderCount"), "1");
            }
            String scenario = scenario(key);
            state.setProperty(field(key, "scenario"), scenario);
            state.setProperty(field(key, "remoteStatus"), cancel ? "CANCELLED" : "CONFIRMED");
            save(state);
            if ("TIMEOUT_AFTER_STORE".equals(scenario)) {
                sleep(Duration.ofSeconds(5));
            }
            if ("CONTROLLED_ERROR".equals(scenario)) {
                respond(exchange, 200, result("TRANSPORT_ERROR", key, remoteId,
                        "FAKE_CONTROLLED", "CONTROLLED_ERROR"));
                return;
            }
            respond(exchange, 200, result("ACKNOWLEDGED", key, remoteId, null, null));
        }

        synchronized void query(HttpExchange exchange) throws IOException {
            String key = required(request(exchange), "clientOrderId");
            Properties state = load();
            state.setProperty(field(key, "recoveryQueryCount"),
                    Long.toString(number(state, key, "recoveryQueryCount") + 1));
            String remoteId = state.getProperty(field(key, "remoteOrderId"));
            String scenario = state.getProperty(field(key, "scenario"), scenario(key));
            String status;
            String errorCode = null;
            if (remoteId == null) {
                status = "NOT_FOUND";
            } else if ("UNKNOWN".equals(scenario)) {
                status = "UNKNOWN";
            } else if ("PARTIAL_FILL".equals(scenario)) {
                status = "PARTIAL_FILL_SIMULATION";
            } else if ("CANCEL_RACE".equals(scenario)) {
                status = "CANCEL_RACE_SIMULATION";
            } else if ("LATE_FILL".equals(scenario)) {
                status = "CONFIRMED";
                errorCode = "LATE_FILL";
            } else {
                status = "CONFIRMED";
            }
            save(state);
            respond(exchange, 200, queryResult(status, key, remoteId, errorCode));
        }

        synchronized void metrics(HttpExchange exchange) throws IOException {
            String key = required(request(exchange), "clientOrderId");
            Properties state = load();
            respond(exchange, 200, "clientOrderId=" + key + "\n"
                    + "mutationCallCount=" + number(state, key, "mutationCallCount") + "\n"
                    + "remoteOrderCount=" + number(state, key, "remoteOrderCount") + "\n"
                    + "recoveryQueryCount=" + number(state, key, "recoveryQueryCount") + "\n"
                    + "remoteStatus=" + state.getProperty(field(key, "remoteStatus"), "NOT_FOUND") + "\n");
        }

        private Properties load() throws IOException {
            Properties state = new Properties();
            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 2 * 1024 * 1024) {
                    throw new IOException("fake store failed bounded regular-file check");
                }
                try (var input = Files.newInputStream(path, StandardOpenOption.READ)) {
                    state.load(input);
                }
            }
            return state;
        }

        private void save(Properties state) throws IOException {
            Path temporary = path.resolveSibling(path.getFileName() + ".new");
            if (Files.exists(temporary, LinkOption.NOFOLLOW_LINKS)) Files.delete(temporary);
            try (var output = Files.newOutputStream(temporary, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                state.store(output, "Disposable fake venue state");
            }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Map<String, String> request(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, "error=METHOD_NOT_ALLOWED\n");
            throw new IOException("POST required");
        }
        byte[] body = exchange.getRequestBody().readNBytes(MAX_BODY + 1);
        if (body.length > MAX_BODY) throw new IOException("bounded request exceeded");
        Map<String, String> values = new LinkedHashMap<>();
        for (String pair : new String(body, StandardCharsets.UTF_8).split("&")) {
            int separator = pair.indexOf('=');
            if (separator < 1) throw new IOException("malformed form body");
            String key = decode(pair.substring(0, separator));
            String value = decode(pair.substring(separator + 1));
            if (values.putIfAbsent(key, value) != null) throw new IOException("duplicate form field");
        }
        return values;
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String result(String outcome, String key, String orderId, String category, String code) {
        return "outcome=" + outcome + "\nexchangeRequestId=fake-request-" + sha256(key).substring(0, 16)
                + "\nexchangeOrderId=" + orderId + "\nerrorCategory=" + nullText(category)
                + "\nerrorCode=" + nullText(code) + "\n";
    }

    private static String queryResult(String status, String key, String orderId, String code) {
        return "status=" + status + "\nexchangeRequestId=fake-query-" + sha256(key).substring(0, 16)
                + "\nexchangeOrderId=" + nullText(orderId) + "\nerrorCategory=FAKE_RECONCILIATION"
                + "\nerrorCode=" + nullText(code) + "\n";
    }

    private static String scenario(String key) {
        if (key.contains("partial-fill")) return "PARTIAL_FILL";
        if (key.contains("late-fill")) return "LATE_FILL";
        if (key.contains("cancel-race")) return "CANCEL_RACE";
        if (key.contains("unknown")) return "UNKNOWN";
        if (key.contains("timeout")) return "TIMEOUT_AFTER_STORE";
        if (key.contains("error")) return "CONTROLLED_ERROR";
        return "ACKNOWLEDGED";
    }

    private static String required(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new IllegalArgumentException("invalid bounded fake venue field: " + key);
        }
        return value;
    }

    private static long number(Properties state, String key, String name) {
        return Long.parseLong(state.getProperty(field(key, name), "0"));
    }

    private static String field(String key, String name) {
        return key + "." + name;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static String nullText(String value) {
        return value == null ? "-" : value;
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void sleep(Duration value) {
        try {
            Thread.sleep(value);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private record Arguments(int port, Path store) {
        static Arguments parse(String[] args) {
            if (!Boolean.getBoolean("nq.fake-worker.confirm-disposable") || args.length != 2) {
                throw new IllegalArgumentException("explicit disposable confirmation and port/store are required");
            }
            int port = Integer.parseInt(args[0]);
            if (port < 1024 || port > 65535) throw new IllegalArgumentException("invalid port");
            return new Arguments(port, Path.of(args[1]));
        }
    }
}
