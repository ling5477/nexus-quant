package com.guidinglight.nexusquant.account.application.service;

import com.guidinglight.nexusquant.account.application.command.ExchangeAccountCreateCommand;
import com.guidinglight.nexusquant.account.application.command.ExchangeAccountUpdateCommand;
import com.guidinglight.nexusquant.account.application.exception.AccountCommandIdentityConflictException;
import com.guidinglight.nexusquant.account.domain.AccountCommand;
import com.guidinglight.nexusquant.account.domain.AccountCommandReceipt;
import com.guidinglight.nexusquant.account.domain.port.AccountCommandReceiptRepository;

import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/** 串行化同一用户的同一命令，业务变更与成功回执共用事务，失败整体回滚。 */
public class AccountCommandService {
    private final AccountCommandReceiptRepository receipts;
    private final ExchangeAccountCommandService commands;
    private final Clock clock;

    public AccountCommandService(
            AccountCommandReceiptRepository receipts, ExchangeAccountCommandService commands) {
        this(receipts, commands, Clock.systemUTC());
    }

    AccountCommandService(
            AccountCommandReceiptRepository receipts,
            ExchangeAccountCommandService commands,
            Clock clock) {
        this.receipts = receipts;
        this.commands = commands;
        this.clock = clock;
    }

    /** 在用户/命令锁内委托正式账户生命周期；同输入重放原结果，身份改绑冲突，业务和成功回执同时提交。 */
    @Transactional(timeout = 10)
    public AccountCommandReceipt execute(Long owner, UUID commandId, AccountCommand request) {
        if (owner == null || owner <= 0 || commandId == null)
            throw new IllegalArgumentException("valid owner and command identity required");
        validate(request);
        String fingerprint = fingerprint(request);
        receipts.lock(owner, commandId);
        var recorded = receipts.find(owner, commandId);
        if (recorded.isPresent()) {
            if (!recorded.get().requestSha256().equals(fingerprint))
                throw new AccountCommandIdentityConflictException();
            return recorded.get();
        }
        var result =
                switch (request.type()) {
                    case CREATE ->
                            commands.create(
                                    owner,
                                    new ExchangeAccountCreateCommand(
                                            request.exchangeCode(),
                                            request.tradeEnv(),
                                            request.accountAlias(),
                                            request.externalAccountRef()));
                    case UPDATE ->
                            commands.updateProfile(
                                    owner,
                                    request.accountId(),
                                    new ExchangeAccountUpdateCommand(
                                            request.accountAlias(), request.externalAccountRef()));
                    case ENABLE -> commands.enable(owner, request.accountId());
                    case DISABLE -> commands.disable(owner, request.accountId());
                    case SET_DEFAULT -> commands.setDefault(owner, request.accountId());
                    case RESOLVE_SIM -> commands.resolveSimIdentity(owner, request.accountId());
                };
        var receipt =
                new AccountCommandReceipt(
                        owner,
                        commandId,
                        request.type(),
                        fingerprint,
                        result,
                        clock.instant().truncatedTo(ChronoUnit.MICROS));
        receipts.save(receipt);
        return receipt;
    }

    /** 只读原命令成功回执；未查到可能尚未提交，不能据此创建新命令或推断原请求失败。 */
    @Transactional(readOnly = true, timeout = 5)
    public Optional<AccountCommandReceipt> find(Long owner, UUID commandId) {
        if (owner == null || owner <= 0 || commandId == null)
            throw new IllegalArgumentException("valid owner and command identity required");
        return receipts.find(owner, commandId);
    }

    private static void validate(AccountCommand request) {
        if (request == null || request.type() == null)
            throw new IllegalArgumentException("account command type required");
        if (request.type() == AccountCommand.Type.CREATE) {
            if (request.accountId() != null)
                throw new IllegalArgumentException(
                        "create does not accept an existing account identity");
            required(request.exchangeCode(), 32);
            required(request.tradeEnv(), 8);
            required(request.accountAlias(), 64);
        } else {
            if (request.accountId() == null || request.accountId() <= 0)
                throw new IllegalArgumentException("positive account identity required");
            if (request.exchangeCode() != null || request.tradeEnv() != null)
                throw new IllegalArgumentException(
                        "existing account market identity cannot be changed");
            if (request.type() == AccountCommand.Type.UPDATE) required(request.accountAlias(), 64);
            else if (request.accountAlias() != null || request.externalAccountRef() != null)
                throw new IllegalArgumentException("state command does not accept profile fields");
        }
        if (request.externalAccountRef() != null && request.externalAccountRef().length() > 128)
            throw new IllegalArgumentException("external account reference is too long");
    }

    private static void required(String value, int max) {
        if (value == null || value.isBlank() || value.length() > max)
            throw new IllegalArgumentException("required account field is invalid");
    }

    static String fingerprint(AccountCommand request) {
        try {
            var bytes = new ByteArrayOutputStream();
            try (var output = new DataOutputStream(bytes)) {
                // 长度前缀和显式null避免分隔符、空串、字段顺序造成命令身份碰撞。
                for (String value :
                        new String[] {
                            request.type().name(),
                            request.accountId() == null ? null : request.accountId().toString(),
                            request.exchangeCode(),
                            request.tradeEnv(),
                            request.accountAlias(),
                            request.externalAccountRef()
                        }) {
                    if (value == null) output.writeInt(-1);
                    else {
                        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
                        output.writeInt(encoded.length);
                        output.write(encoded);
                    }
                }
            }
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray()));
        } catch (IOException | NoSuchAlgorithmException failure) {
            throw new IllegalStateException(
                    "account command identity could not be calculated", failure);
        }
    }
}
