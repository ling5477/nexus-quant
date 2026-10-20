package com.guidinglight.nexusquant.account.application.service;

import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialRotateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCredentialUpsertCommand;
import com.guidinglight.nexusquant.account.application.exception.CredentialOperationIdentityException;
import com.guidinglight.nexusquant.account.application.exception.ExchangeAccountCredentialNotFoundException;
import com.guidinglight.nexusquant.account.application.exception.ExchangeAccountNotFoundException;
import com.guidinglight.nexusquant.account.domain.CredentialOperation;
import com.guidinglight.nexusquant.account.domain.CredentialOperationReceipt;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.port.CredentialOperationRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;

import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 委托既有生命周期服务；成功回执与版本链、校验和审计同事务提交。 */
public class CredentialOperationService {
    private final CredentialOperationRepository operations;
    private final ExchangeAccountRepository accounts;
    private final ExchangeAccountCredentialRepository credentials;
    private final ExchangeAccountCredentialCommandService commands;
    private final ExchangeAccountCredentialVerificationService verification;
    private final int keyVersion;

    public CredentialOperationService(
            CredentialOperationRepository operations,
            ExchangeAccountRepository accounts,
            ExchangeAccountCredentialRepository credentials,
            ExchangeAccountCredentialCommandService commands,
            ExchangeAccountCredentialVerificationService verification,
            int keyVersion) {
        this.operations = operations;
        this.accounts = accounts;
        this.credentials = credentials;
        this.commands = commands;
        this.verification = verification;
        this.keyVersion = keyVersion;
        if (keyVersion <= 0)
            throw new IllegalArgumentException("credential key version must be positive");
    }

    /** 串行执行指定账户和版本的生命周期操作；敏感输入只参与HMAC，同输入重放不重复轮换、校验或审计。 */
    @Transactional(timeout = 10)
    public CredentialOperationReceipt execute(
            Long owner, Long account, UUID operationId, CredentialOperation request, String actor) {
        owned(owner, account);
        validate(request);
        if (operationId == null)
            throw new IllegalArgumentException("credential operation identity required");
        operations.lock(owner, account, operationId);
        byte[] input = encode(owner, account, request);
        String fingerprint;
        try {
            fingerprint = operations.fingerprint(input);
        } finally {
            Arrays.fill(input, (byte) 0);
        }
        String fingerprintKey = operations.fingerprintKeyId();
        var existing = operations.find(owner, account, operationId);
        if (existing.isPresent()) {
            var receipt = existing.get();
            if (!receipt.fingerprintKeyId().equals(fingerprintKey)
                    || receipt.keyVersion() != keyVersion)
                throw new CredentialOperationIdentityException();
            if (!receipt.inputHmac().equals(fingerprint))
                throw new CredentialOperationIdentityException();
            return receipt;
        }
        if (request.type() != CredentialOperation.Type.CREATE) {
            var current =
                    credentials
                            .findByCredentialIdForOwner(owner, account, request.credentialId())
                            .orElseThrow(
                                    () -> new ExchangeAccountCredentialNotFoundException(account));
            if (!current.credentialType().equals(request.credentialType()))
                throw new IllegalStateException("credential type does not match selected version");
        }
        var result =
                switch (request.type()) {
                    case CREATE -> {
                        if (credentials
                                .findActiveSummary(owner, account, request.credentialType())
                                .isPresent())
                            throw new IllegalStateException(
                                    "active credential already exists; select its version for rotation");
                        var created =
                                commands.upsert(
                                        owner,
                                        account,
                                        new ExchangeAccountCredentialUpsertCommand(
                                                request.credentialType(),
                                                request.apiKey(),
                                                request.secretKey(),
                                                request.passphrase(),
                                                request.privateKeyPem()),
                                        keyVersion);
                        credentials.appendCredentialAuditLog(
                                created.credentialId(),
                                account,
                                "CREATED",
                                actor,
                                null,
                                "{\"credentialType\":\"" + created.credentialType() + "\"}",
                                created.updatedAt());
                        yield created;
                    }
                    case ROTATE ->
                            commands.rotate(
                                    owner,
                                    account,
                                    request.credentialId(),
                                    new ExchangeAccountCredentialRotateCommand(
                                            request.apiKey(),
                                            request.secretKey(),
                                            request.passphrase(),
                                            request.privateKeyPem(),
                                            request.reason()),
                                    actor,
                                    keyVersion);
                    case VERIFY ->
                            verification.verifyVersion(owner, account, request.credentialId());
                    case DISABLE ->
                            commands.disable(
                                    owner,
                                    account,
                                    request.credentialId(),
                                    actor,
                                    request.reason());
                    case ENABLE ->
                            commands.enable(
                                    owner,
                                    account,
                                    request.credentialId(),
                                    actor,
                                    request.reason());
                    case EXPIRE ->
                            commands.expire(
                                    owner,
                                    account,
                                    request.credentialId(),
                                    actor,
                                    request.reason());
                    case REVOKE ->
                            commands.revoke(
                                    owner,
                                    account,
                                    request.credentialId(),
                                    actor,
                                    request.reason());
                };
        var receipt =
                new CredentialOperationReceipt(
                        owner,
                        account,
                        operationId,
                        request.type(),
                        fingerprint,
                        fingerprintKey,
                        keyVersion,
                        result,
                        Instant.now().truncatedTo(ChronoUnit.MICROS));
        operations.save(receipt);
        return receipt;
    }

    /** 按用户、账户和原操作身份读取已提交回执；空结果保持未知，不触发生命周期操作。 */
    @Transactional(readOnly = true, timeout = 5)
    public Optional<CredentialOperationReceipt> find(Long owner, Long account, UUID operationId) {
        owned(owner, account);
        if (operationId == null)
            throw new IllegalArgumentException("credential operation identity required");
        return operations.find(owner, account, operationId);
    }

    /** 读取最多50个版本并多取一条判断后页；只返回脱敏摘要，不取加密载荷或供应方材料。 */
    @Transactional(readOnly = true, timeout = 5)
    public List<ExchangeAccountCredentialSummary> history(
            Long owner, Long account, Long before, int limit) {
        owned(owner, account);
        if (limit < 1 || limit > 50 || (before != null && before <= 0))
            throw new IllegalArgumentException("invalid credential history range");
        return credentials.listHistory(owner, account, before, limit + 1);
    }

    private void owned(Long owner, Long account) {
        if (owner == null || owner <= 0 || account == null || account <= 0)
            throw new IllegalArgumentException("positive owner and account required");
        accounts.findByIdForOwner(owner, account)
                .orElseThrow(() -> new ExchangeAccountNotFoundException(account));
    }

    /** 区分材料写入与生命周期命令，并拒绝不适用字段；在指纹计算前验证有效Unicode，防止编码替换产生身份碰撞。 */
    private static void validate(CredentialOperation r) {
        if (r == null
                || r.type() == null
                || r.credentialType() == null
                || !List.of("OKX_API_V5", "BINANCE_HMAC", "BINANCE_ED25519")
                        .contains(r.credentialType()))
            throw new IllegalArgumentException("valid credential operation and type required");
        if (r.type() == CredentialOperation.Type.CREATE
                ? r.credentialId() != null
                : r.credentialId() == null || r.credentialId() <= 0)
            throw new IllegalArgumentException("credential version does not match operation");
        boolean material =
                r.type() == CredentialOperation.Type.CREATE
                        || r.type() == CredentialOperation.Type.ROTATE;
        if (!material
                && (r.apiKey() != null
                        || r.secretKey() != null
                        || r.passphrase() != null
                        || r.privateKeyPem() != null))
            throw new IllegalArgumentException(
                    "lifecycle operation does not accept sensitive material");
        if (r.type() == CredentialOperation.Type.VERIFY && r.reason() != null
                || r.type() == CredentialOperation.Type.CREATE && r.reason() != null)
            throw new IllegalArgumentException("operation does not accept a reason");
        if (material) {
            bounded(r.apiKey(), 512, true);
            bounded(r.secretKey(), 4096, !r.credentialType().equals("BINANCE_ED25519"));
            bounded(r.passphrase(), 512, r.credentialType().equals("OKX_API_V5"));
            bounded(r.privateKeyPem(), 16384, r.credentialType().equals("BINANCE_ED25519"));
            if (r.credentialType().equals("BINANCE_ED25519") && r.secretKey() != null
                    || !r.credentialType().equals("BINANCE_ED25519") && r.privateKeyPem() != null
                    || !r.credentialType().equals("OKX_API_V5") && r.passphrase() != null)
                throw new IllegalArgumentException("sensitive fields do not match credential type");
        }
        bounded(
                r.reason(),
                1024,
                r.type() == CredentialOperation.Type.ROTATE
                        || r.type() == CredentialOperation.Type.ENABLE);
    }

    private static void bounded(String value, int max, boolean required) {
        if (required && (value == null || value.isBlank()) || value != null && value.length() > max)
            throw new IllegalArgumentException("credential operation field is invalid");
        if (value == null) return;
        // Java的默认UTF-8编码会将孤立代理字符替换成问号，必须在指纹计算前拒绝，避免不同材料同身份。
        for (int index = 0; index < value.length(); index++) {
            char unit = value.charAt(index);
            if (Character.isHighSurrogate(unit)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1)))
                    throw new IllegalArgumentException(
                            "credential operation text must contain valid Unicode");
                index++;
            } else if (Character.isLowSurrogate(unit)) {
                throw new IllegalArgumentException(
                        "credential operation text must contain valid Unicode");
            }
        }
    }

    private static byte[] encode(Long owner, Long account, CredentialOperation r) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var out = new DataOutputStream(bytes)) {
                // 编码保留null、空串和字段边界；不把敏感输入转换成可落盘JSON。
                for (String value :
                        new String[] {
                            "credential-operation-v1",
                            owner.toString(),
                            account.toString(),
                            r.type().name(),
                            r.credentialId() == null ? null : r.credentialId().toString(),
                            r.credentialType(),
                            r.apiKey(),
                            r.secretKey(),
                            r.passphrase(),
                            r.privateKeyPem(),
                            r.reason()
                        }) {
                    if (value == null) out.writeInt(-1);
                    else {
                        byte[] part = value.getBytes(StandardCharsets.UTF_8);
                        out.writeInt(part.length);
                        out.write(part);
                        Arrays.fill(part, (byte) 0);
                    }
                }
            }
            return bytes.toByteArray();
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "credential operation identity encoding failed", failure);
        }
    }
}
