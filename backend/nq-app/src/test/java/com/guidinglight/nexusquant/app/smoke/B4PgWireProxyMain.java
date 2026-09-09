package com.guidinglight.nexusquant.app.smoke;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** 单个隔离PG的有界明文协议转发器：在选中事务的COMMIT边界断连或暂停。 */
public final class B4PgWireProxyMain {
    private final AtomicReference<String> armed = new AtomicReference<>("NONE");
    private final Set<Link> links = ConcurrentHashMap.newKeySet();
    private final ExecutorService workers = Executors.newFixedThreadPool(24);
    private final int upstream;

    private B4PgWireProxyMain(int upstream) { this.upstream = upstream; }

    public static void main(String[] args) throws Exception {
        B0Fixture.require(args.length == 0);
        String url = System.getenv("NQ_B4_DB");
        URI uri = URI.create(url.substring(5));
        B0Fixture.requireLoopbackDatabase(url, uri.getPath().substring(1));
        var proxy = new B4PgWireProxyMain(uri.getPort());
        try (var server = new ServerSocket(0, 12, InetAddress.getByName("127.0.0.1"));
             var input = new BufferedReader(new InputStreamReader(System.in))) {
            proxy.workers.submit(() -> {
                while (!server.isClosed()) {
                    try {
                        Socket client = server.accept();
                        if (proxy.links.size() >= 10) { client.close(); continue; }
                        var link = proxy.new Link(client);
                        proxy.links.add(link);
                        proxy.workers.submit(link::clientToServer);
                    } catch (IOException failure) {
                        if (!server.isClosed()) throw new UncheckedIOException(failure);
                    }
                }
            });
            System.out.println("B0_READY " + server.getLocalPort()); System.out.flush();
            String command;
            while ((command = input.readLine()) != null && !command.equals("STOP")) {
                if (command.startsWith("ARM ")) {
                    String mode = command.substring(4);
                    B0Fixture.require(Set.of("BEFORE_DROP", "AFTER_DROP", "BEFORE_PAUSE", "AFTER_PAUSE").contains(mode));
                    B0Fixture.require(proxy.armed.compareAndSet("NONE", mode));
                    System.out.println("B0_RESULT ARMED " + mode);
                } else if (command.equals("DROP_PENDING")) {
                    for (Link link : proxy.links) if (!link.mode.equals("NONE")) link.close();
                    System.out.println("B0_RESULT DROPPED");
                } else throw new IllegalArgumentException("unsupported wire command");
                System.out.flush();
            }
        } finally {
            proxy.links.forEach(Link::close);
            proxy.workers.shutdownNow();
        }
    }

    private final class Link {
        private final Socket client;
        private final Socket database;
        private volatile String mode = "NONE";
        private volatile boolean commit;
        private boolean marker;
        private final java.util.Map<String, String> statements = new java.util.HashMap<>();
        private final java.util.Map<String, String> portals = new java.util.HashMap<>();
        private final CountDownLatch released = new CountDownLatch(1);

        Link(Socket client) throws IOException {
            this.client = client;
            database = new Socket(); database.connect(new InetSocketAddress("127.0.0.1", upstream), 3000);
            client.setTcpNoDelay(true); database.setTcpNoDelay(true);
        }

        void clientToServer() {
            try {
                var in = new DataInputStream(client.getInputStream());
                var out = new DataOutputStream(database.getOutputStream());
                byte[] startup = body(in);
                // PG默认SSL探测在本地fixture返回N；从不接收真实认证密钥或转发TLS流量。
                if (startup.length == 4 && new DataInputStream(new ByteArrayInputStream(startup)).readInt() == 80877103) {
                    client.getOutputStream().write('N'); client.getOutputStream().flush(); startup = body(in);
                }
                out.writeInt(startup.length + 4); out.write(startup); out.flush();
                workers.submit(this::serverToClient);
                while (!client.isClosed()) {
                    int type = in.readUnsignedByte(); byte[] payload = body(in);
                    String query = executionQuery(type, payload);
                    if (query.contains("b4-target-commit")) marker = true;
                    if (marker && query.strip().equalsIgnoreCase("COMMIT")) {
                        mode = armed.getAndSet("NONE");
                        B0Fixture.require(!mode.equals("NONE"));
                        if (mode.startsWith("BEFORE")) {
                            emit("COMMIT_NOT_FORWARDED");
                            if (mode.endsWith("PAUSE")) pause();
                            return;
                        }
                        commit = true;
                    }
                    out.writeByte(type); out.writeInt(payload.length + 4); out.write(payload); out.flush();
                }
            } catch (EOFException | SocketException expected) {
                // 对端死亡/断连是选定故障的实际网络结果；独立reader判定提交事实。
            } catch (Exception failure) {
                System.out.println("B4_WIRE_ERROR " + failure.getClass().getSimpleName()); System.out.flush();
            } finally { close(); }
        }

        void serverToClient() {
            try {
                var in = new DataInputStream(database.getInputStream());
                var out = new DataOutputStream(client.getOutputStream());
                while (!database.isClosed()) {
                    int type = in.readUnsignedByte(); byte[] payload = body(in);
                    if (commit && type == 'C' && cstring(payload, 0).equals("COMMIT")) {
                        // 服务器CommandComplete已到达代理，尚无任何COMMIT完成帧交付应用连接。
                        emit("SERVER_COMMIT_CONFIRMED_RESPONSE_WITHHELD");
                        if (mode.endsWith("PAUSE")) pause();
                        return;
                    }
                    out.writeByte(type); out.writeInt(payload.length + 4); out.write(payload); out.flush();
                }
            } catch (EOFException | SocketException expected) {
                // 关闭另一方向时同时解除阻塞，避免孤立数据库会话。
            } catch (Exception failure) {
                System.out.println("B4_WIRE_ERROR " + failure.getClass().getSimpleName()); System.out.flush();
            } finally { close(); }
        }

        void emit(String outcome) { System.out.println("B4_WIRE_CUT " + mode + " " + outcome); System.out.flush(); }
        String executionQuery(int type, byte[] payload) {
            if (type == 'Q') return cstring(payload, 0);
            if (type == 'P') {
                String statement = cstring(payload, 0);
                statements.put(statement, cstring(payload, zeroEnd(payload, 0) + 1));
                B0Fixture.require(statements.size() <= 4096);
            } else if (type == 'B') {
                String portal = cstring(payload, 0);
                String statement = cstring(payload, zeroEnd(payload, 0) + 1);
                portals.put(portal, statements.getOrDefault(statement, ""));
                B0Fixture.require(portals.size() <= 4096);
            } else if (type == 'E') {
                // 驱动会复用已Parse的COMMIT；必须在Execute而非仅在SQL文本出现时截断。
                return portals.getOrDefault(cstring(payload, 0), "");
            } else if (type == 'C' && payload.length > 1) {
                if (payload[0] == 'S') statements.remove(cstring(payload, 1));
                if (payload[0] == 'P') portals.remove(cstring(payload, 1));
            }
            return "";
        }
        void pause() throws InterruptedException {
            if (!released.await(45, TimeUnit.SECONDS)) throw new IllegalStateException("B4_HARNESS_CONTROL_GAP");
        }
        void close() {
            released.countDown();
            try { client.close(); } catch (IOException ignored) { }
            try { database.close(); } catch (IOException ignored) { }
            links.remove(this);
        }
    }

    private static byte[] body(DataInputStream input) throws IOException {
        int size = input.readInt();
        if (size < 4 || size > 8 * 1024 * 1024) throw new IOException("invalid PG frame size");
        byte[] bytes = input.readNBytes(size - 4);
        if (bytes.length != size - 4) throw new EOFException();
        return bytes;
    }
    private static String cstring(byte[] payload, int start) {
        int end = start; while (end < payload.length && payload[end] != 0) end++;
        return new String(payload, start, end - start, StandardCharsets.UTF_8);
    }
    private static int zeroEnd(byte[] payload, int start) {
        int end = start; while (end < payload.length && payload[end] != 0) end++;
        B0Fixture.require(end < payload.length);
        return end;
    }
}
