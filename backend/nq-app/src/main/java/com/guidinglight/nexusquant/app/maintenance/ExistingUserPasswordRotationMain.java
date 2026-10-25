package com.guidinglight.nexusquant.app.maintenance;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.auth.application.service.ExistingUserPasswordRotationService;
import com.guidinglight.nexusquant.auth.domain.PasswordRotationException;
import com.guidinglight.nexusquant.auth.infra.jdbc.JdbcExistingUserPasswordRotationRepository;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.Properties;
import java.util.logging.LogManager;

/** 独立的一次性维护入口；不创建 Spring 上下文、HTTP listener、seed 或交易组件。 */
public final class ExistingUserPasswordRotationMain {
    private static final String ACTION = "--execute-existing-user-password-rotation";

    private ExistingUserPasswordRotationMain() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out));
    }

    static int run(String[] args, PrintStream output) {
        if (args.length != 2 || !ACTION.equals(args[0])) {
            output.println("{\"status\":\"EXPLICIT_MAINTENANCE_ACTION_REQUIRED\"}");
            return 2;
        }
        byte[] configBytes = null;
        char[] password = null;
        char[] databasePassword = null;
        DriverManagerDataSource dataSource = null;
        try {
            // 此入口独立运行；清除外部 JUL 配置，避免 JDBC trace 输出绑定参数。
            LogManager.getLogManager().reset();
            Path requestPath = Path.of(args[1]);
            configBytes = ProtectedMaintenanceFiles.read(requestPath, 16384);
            RotationRequest request = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .readValue(configBytes, RotationRequest.class);
            request.validate(requestPath);
            password = readSecret(Path.of(request.newPasswordFile()), 288);
            databasePassword = readSecret(Path.of(request.databasePasswordFile()), 1024);
            dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl(request.databaseUrl());
            dataSource.setUsername(request.databaseUser());
            dataSource.setPassword(new String(databasePassword));
            Properties connectionProperties = new Properties();
            connectionProperties.setProperty("connectTimeout", "5");
            connectionProperties.setProperty("socketTimeout", "20");
            connectionProperties.setProperty("ApplicationName", "existing-user-password-rotation");
            dataSource.setConnectionProperties(connectionProperties);
            ExistingUserPasswordRotationService service = new ExistingUserPasswordRotationService(
                    new JdbcExistingUserPasswordRotationRepository(dataSource), new BCryptPasswordEncoder(),
                    Clock.systemUTC());
            service.rotate(request.exactUserId(), request.expectedUsername(), request.expectedHashSha256(), password);
            output.println("{\"status\":\"PASSWORD_ROTATED\",\"userId\":" + request.exactUserId() + "}");
            return 0;
        } catch (PasswordRotationException exception) {
            output.println("{\"status\":\"" + exception.reason().name() + "\"}");
            return 3;
        } catch (Exception exception) {
            // 文件、JSON、SQL 异常可能携带输入或凭据；边界仅输出固定失败状态。
            output.println("{\"status\":\"MAINTENANCE_INPUT_OR_RUNTIME_FAILURE\"}");
            return 4;
        } finally {
            if (configBytes != null) {
                Arrays.fill(configBytes, (byte) 0);
            }
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (databasePassword != null) {
                Arrays.fill(databasePassword, '\0');
            }
            if (dataSource != null) {
                dataSource.setPassword(null);
            }
        }
    }

    private static char[] readSecret(Path path, int limit) throws Exception {
        byte[] bytes = ProtectedMaintenanceFiles.read(path, limit);
        CharBuffer decoded = null;
        try {
            decoded = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            char[] value = new char[decoded.remaining()];
            decoded.get(value);
            return value;
        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (decoded != null && decoded.hasArray()) {
                Arrays.fill(decoded.array(), '\0');
            }
        }
    }

    record RotationRequest(String databaseUrl, String databaseUser, String databasePasswordFile,
                           String newPasswordFile, long exactUserId, String expectedUsername,
                           String expectedHashSha256) {
        void validate(Path requestPath) {
            if (databaseUrl == null || !databaseUrl.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]{1,5}/[A-Za-z0-9_]+")
                    || databaseUser == null || !databaseUser.matches("[A-Za-z_][A-Za-z0-9_]{0,62}")
                    || databasePasswordFile == null || newPasswordFile == null
                    || Path.of(databasePasswordFile).equals(Path.of(newPasswordFile))
                    || requestPath.equals(Path.of(databasePasswordFile)) || requestPath.equals(Path.of(newPasswordFile))) {
                throw new IllegalArgumentException("INVALID_MAINTENANCE_REQUEST");
            }
        }
    }
}
