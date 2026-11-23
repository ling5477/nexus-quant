package com.guidinglight.nexusquant.demo.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 管理事务只依赖受限数据库命令，不拥有订单、余额、账务或策略写端口。 */
@Service
public final class PlatformUserService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PlatformAccess access;
    private final ObjectMapper json = new ObjectMapper();

    public PlatformUserService(DataSource source, PlatformTransactionManager manager, PlatformAccess access) {
        jdbc = new JdbcTemplate(source);
        jdbc.setQueryTimeout(15);
        transactions = new TransactionTemplate(manager);
        transactions.setTimeout(20);
        this.access = access;
    }

    public JsonNode change(UUID operationId, long userId, String action, String reason) {
        var actor = access.require();
        if (operationId == null || userId < 1 || reason == null || reason.isBlank() || reason.length() > 512)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "PLATFORM_INPUT_INVALID");
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((userId + "\n" + action + "\n" + reason).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        try {
            String result = transactions.execute(status -> {
                jdbc.execute("SET LOCAL lock_timeout='10s'");
                return jdbc.queryForObject("SELECT nq_platform_user_operation(?,?,?,?,?,?,?,?)::text", String.class,
                        operationId, actor.userId(), actor.sessionId(), actor.authVersion(), userId, action, hash, reason);
            });
            var stored = json.readTree(result);
            var response = json.createObjectNode();
            for (String name : new String[]{"operation_id", "target_user_id", "target_tenant_id", "action",
                    "before_enabled", "after_enabled", "before_auth_version", "after_auth_version",
                    "before_execution_epoch", "after_execution_epoch", "created_at"}) {
                String[] parts = name.split("_");
                StringBuilder camel = new StringBuilder(parts[0]);
                for (int i = 1; i < parts.length; i++) camel.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
                response.set(camel.toString(), stored.get(name));
            }
            return response;
        } catch (DataAccessException failure) {
            String detail = String.valueOf(failure.getMostSpecificCause().getMessage());
            if (detail.contains("PLATFORM_OPERATION_CONFLICT") || detail.contains("PLATFORM_TARGET_NOT_READY"))
                throw new ResponseStatusException(HttpStatus.CONFLICT, detail.contains("CONFLICT")
                        ? "PLATFORM_OPERATION_CONFLICT" : "PLATFORM_TARGET_NOT_READY");
            if (detail.contains("PLATFORM_TARGET_NOT_ALLOWED") || detail.contains("PLATFORM_ACCESS_DENIED"))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PLATFORM_ACCESS_DENIED");
            throw failure;
        } catch (java.io.IOException failure) { throw new IllegalStateException("platform receipt invalid", failure); }
    }
}
