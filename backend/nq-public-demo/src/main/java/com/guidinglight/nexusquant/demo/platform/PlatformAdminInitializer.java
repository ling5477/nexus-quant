package com.guidinglight.nexusquant.demo.platform;

import java.io.Console;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** 受控安装命令只创建首个平台身份；独立进程、隐藏输入、无公共 HTTP 初始化入口。 */
public final class PlatformAdminInitializer {
    private PlatformAdminInitializer() {}

    public static void main(String[] args) throws Exception {
        try (var marker=PlatformAdminInitializer.class.getClassLoader().getResourceAsStream("META-INF/nq-distribution.properties")) {
            var identity=new java.util.Properties();
            if(marker==null)throw new IllegalStateException("PUBLIC_DEMO_ARTIFACT_REQUIRED");
            identity.load(marker);
            if(!"NQ_PUBLIC_DEMO".equals(identity.getProperty("distribution")))
                throw new IllegalStateException("PUBLIC_DEMO_ARTIFACT_REQUIRED");
        }
        Console console = System.console();
        if (console == null || args.length != 1 || !args[0].matches("[a-z0-9][a-z0-9_.-]{2,63}"))
            throw new IllegalArgumentException("interactive console and normalized username required");
        String url = System.getenv("NQ_PLATFORM_INSTALL_JDBC_URL");
        String installer = System.getenv("NQ_PLATFORM_INSTALL_DB_USER");
        if (url == null || !url.startsWith("jdbc:postgresql:") || installer == null || installer.isBlank())
            throw new IllegalArgumentException("explicit platform installation database required");
        char[] databasePassword = console.readPassword("Installation database password: ");
        char[] password = console.readPassword("Initial platform password (20 to 72 UTF-8 bytes): ");
        char[] confirmation = console.readPassword("Confirm platform password: ");
        try {
            if (!Arrays.equals(password, confirmation)) throw new IllegalArgumentException("password confirmation mismatch");
            String value = new String(password);
            int length = value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (length < 20 || length > 72 || value.toLowerCase(Locale.ROOT).contains(args[0]))
                throw new IllegalArgumentException("strong independent initial password required");
            try (Connection connection = DriverManager.getConnection(url, installer, new String(databasePassword))) {
                initialize(connection, args[0], new BCryptPasswordEncoder(12).encode(value));
            }
            console.printf("Platform identity initialized; first login requires password change.%n");
        } finally {
            Arrays.fill(databasePassword,'\0'); Arrays.fill(password,'\0'); Arrays.fill(confirmation,'\0');
        }
    }

    /** 安装账号必须有明确数据库权限；普通运行身份不得调用，也不通过应用 Runner 自动创建。 */
    static void initialize(Connection connection, String username, String hash) throws Exception {
        if (username == null || !username.matches("[a-z0-9][a-z0-9_.-]{2,63}")
                || hash == null || !hash.startsWith("$2")) throw new IllegalArgumentException("invalid platform initialization");
        connection.setAutoCommit(false);
        try (var statement = connection.createStatement()) {
            statement.execute("SET LOCAL lock_timeout='10s'");
            statement.execute("SET LOCAL statement_timeout='15s'");
            statement.execute("LOCK TABLE users IN SHARE ROW EXCLUSIVE MODE");
            try (var found = statement.executeQuery("SELECT EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE r.role_code='PLATFORM_ADMIN') OR EXISTS(SELECT 1 FROM platform_identity_initializations)")) {
                found.next();
                if (found.getBoolean(1)) throw new IllegalStateException("platform identity already initialized");
            }
            long id;
            try (var insert = connection.prepareStatement("INSERT INTO users(username,password_hash,enabled,must_change_password) VALUES(?,?,true,true) RETURNING id")) {
                insert.setString(1, username); insert.setString(2, hash);
                try (var result = insert.executeQuery()) { result.next(); id = result.getLong(1); }
            }
            try (var role = connection.prepareStatement("INSERT INTO user_roles(user_id,role_id) SELECT ?,id FROM roles WHERE role_code='PLATFORM_ADMIN'")) {
                role.setLong(1,id);
                if (role.executeUpdate()!=1) throw new IllegalStateException("platform role is not installed");
            }
            try (var audit = connection.prepareStatement("INSERT INTO platform_identity_initializations(user_id) VALUES(?)")) {
                audit.setLong(1,id); audit.executeUpdate();
            }
            connection.commit();
        } catch (Exception failure) { connection.rollback(); throw failure; }
    }
}
