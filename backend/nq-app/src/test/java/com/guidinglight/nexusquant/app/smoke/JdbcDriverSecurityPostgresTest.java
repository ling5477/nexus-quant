package com.guidinglight.nexusquant.app.smoke;

import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.postgresql.util.ByteStreamWriter;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 仅在独立 PostgreSQL 验证驱动的短写隔离和拒绝全部认证方法的边界。 */
@EnabledIfSystemProperty(named = "nq.product.pg.required", matches = "true")
class JdbcDriverSecurityPostgresTest {
    @Test
    void shortWritesCannotPersistBytesFromEarlierStatements() throws Exception {
        try (var database = ReleaseBaselineTestDatabase.create("nq.product.pg");
                var connection = database.source().getConnection();
                var statement = connection.createStatement()) {
            statement.setQueryTimeout(10);
            statement.execute("CREATE TABLE driver_bytes (payload bytea)");
            byte[] marker = new byte[32768];
            Arrays.fill(marker, (byte) 0x5a);
            try (var seed = connection.prepareStatement("SELECT ?::bytea")) {
                seed.setQueryTimeout(10);
                seed.setBytes(1, marker);
                try (var result = seed.executeQuery()) {
                    result.next();
                    assertArrayEquals(marker, result.getBytes(1));
                }
            }
            byte[] actual = new byte[32768];
            Arrays.fill(actual, (byte) 0x41);
            try (var insert = connection.prepareStatement("INSERT INTO driver_bytes VALUES (?)")) {
                insert.setQueryTimeout(10);
                // 显式声明长度大于实际输出，验证驱动必须补零而不能复用连接内旧语句。
                insert.setObject(1, new ByteStreamWriter() {
                    @Override public int getLength() { return 65536; }
                    @Override public void writeTo(ByteStreamTarget target) throws IOException {
                        target.getOutputStream().write(actual);
                    }
                });
                assertEquals(1, insert.executeUpdate());
            }
            byte[] expected = Arrays.copyOf(actual, 65536);
            try (var result = statement.executeQuery("SELECT payload FROM driver_bytes")) {
                result.next();
                assertArrayEquals(expected, result.getBytes(1));
            }
        }
    }

    @Test
    void excludingEveryAuthenticationMethodFailsClosed() throws Exception {
        try (var database = ReleaseBaselineTestDatabase.create("nq.product.pg")) {
            Properties properties = new Properties();
            properties.setProperty("user", System.getProperty("nq.product.pg.user"));
            properties.setProperty("password", System.getProperty("nq.product.pg.password"));
            properties.setProperty("connectTimeout", "5");
            properties.setProperty("socketTimeout", "5");
            properties.setProperty("requireAuth", "!password,!md5,!gss,!sspi,!scram-sha-256,!none");
            SQLException failure = assertThrows(SQLException.class,
                    () -> { try (var ignored = DriverManager.getConnection(database.url(), properties)) { } });
            assertEquals("08004", failure.getSQLState());
        }
    }
}
