package com.guidinglight.nexusquant.account.infra.binance.readonly;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.account.application.exception.AccountFeeSyncException;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountCredentialSummary;
import com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary;
import com.guidinglight.nexusquant.account.domain.fee.AccountFeeQuote;
import com.guidinglight.nexusquant.account.domain.fee.SpotCommissionTerms;
import com.guidinglight.nexusquant.account.domain.port.AccountFeeReadPort;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountCredentialRepository;
import com.guidinglight.nexusquant.account.domain.port.ExchangeAccountRepository;
import com.guidinglight.nexusquant.adapter.binance.fee.BinanceCommissionHttpReader;
import com.guidinglight.nexusquant.adapter.binance.fee.BinanceCommissionRates;
import com.guidinglight.nexusquant.adapter.binance.fee.BinanceFeeReadException;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceApiCredentials;
import com.guidinglight.nexusquant.adapter.binance.model.BinanceKeyType;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchSnapshot;
import com.guidinglight.nexusquant.risk.domain.model.KillSwitchStatus;
import com.guidinglight.nexusquant.risk.service.KillSwitchService;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.Objects;
import java.util.Set;

/** 精确owner/账户/版本的即时解密只读桥；只返回费用事实，不导出材料、session或任何交易能力。 */
public final class JdbcBinanceAccountFeeReadPort implements AccountFeeReadPort {
    private final JdbcTemplate jdbc;
    private final String masterKey;
    private final BinanceCommissionHttpReader reader;
    private final ExchangeAccountRepository accounts;
    private final ExchangeAccountCredentialRepository credentials;
    private final KillSwitchService kill;
    private final long ownerId, accountId, credentialId;
    private final String credentialType;
    private final Set<String> symbols;
    private final BinanceCommissionHttpReader.PermissionScope permission;
    private final ObjectMapper mapper =
            new ObjectMapper(
                            JsonFactory.builder()
                                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                                    .streamReadConstraints(
                                            StreamReadConstraints.builder()
                                                    .maxNestingDepth(4)
                                                    .maxStringLength(16384)
                                                    .maxNumberLength(32)
                                                    .build())
                                    .build())
                    .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public JdbcBinanceAccountFeeReadPort(
            JdbcTemplate source,
            String masterKey,
            BinanceCommissionHttpReader reader,
            ExchangeAccountRepository accounts,
            ExchangeAccountCredentialRepository credentials,
            KillSwitchService kill,
            long ownerId,
            long accountId,
            long credentialId,
            String credentialType,
            Set<String> symbols,
            BinanceCommissionHttpReader.PermissionScope permission) {
        this.jdbc = new JdbcTemplate(Objects.requireNonNull(source.getDataSource()));
        this.jdbc.setQueryTimeout(5);
        this.reader = Objects.requireNonNull(reader);
        this.accounts = Objects.requireNonNull(accounts);
        this.credentials = Objects.requireNonNull(credentials);
        this.kill = Objects.requireNonNull(kill);
        this.masterKey = Objects.requireNonNull(masterKey);
        this.permission = Objects.requireNonNull(permission);
        this.ownerId = ownerId;
        this.accountId = accountId;
        this.credentialId = credentialId;
        this.credentialType = credentialType;
        this.symbols = Set.copyOf(symbols);
        if (masterKey.isBlank()
                || ownerId <= 0
                || accountId <= 0
                || credentialId <= 0
                || !Set.of("BINANCE_HMAC", "BINANCE_ED25519").contains(credentialType)
                || symbols.isEmpty()
                || symbols.size() > 20
                || symbols.stream()
                        .anyMatch(symbol -> !symbol.matches("[A-Z0-9]{1,20}-[A-Z0-9]{1,20}")))
            throw denied("FEE_PRIVATE_SCOPE_INVALID");
    }

    /** Binance测试网没有必需的/sapi权限预检；SIM不会借用主网凭据或被映射成LIVE读取。 */
    @Override
    public boolean supports(ExchangeAccountSummary account, String symbol) {
        return account != null
                && Objects.equals(account.ownerUserId(), ownerId)
                && Objects.equals(account.exchangeAccountId(), accountId)
                && "BINANCE".equals(account.exchangeCode())
                && "LIVE".equals(account.tradeEnv())
                && "ACTIVE".equals(account.status())
                && symbols.contains(symbol);
    }

    /**
     * 在同步事务内先锁定精确有效版本，再解密；三个固定GET之间及返回前重核上下文与同一ENGAGED状态。
     * VERIFIED仅证明已有结构验证，远端权限和IP限制启用标志由本次reader另行验证，不宣称获知具体IP名单。
     */
    @Override
    public AccountFeeQuote read(
            ExchangeAccountSummary account,
            ExchangeAccountCredentialSummary credential,
            String symbol) {
        char[] key = null, secret = null;
        try {
            if (!TransactionSynchronizationManager.isActualTransactionActive()
                    || !supports(account, symbol)
                    || credential == null
                    || !Objects.equals(credential.credentialId(), credentialId)
                    || !Objects.equals(credential.exchangeAccountId(), accountId)
                    || !credentialType.equals(credential.credentialType())
                    || !credential.isActive()
                    || !"ACTIVE".equals(credential.credentialStatus())
                    || !"VERIFIED".equals(credential.verificationStatus())
                    || credential.revokedAt() != null
                    || credential.rotatedAt() != null) throw denied("FEE_PRIVATE_SCOPE_MISMATCH");
            KillSwitchSnapshot initial = kill.snapshot();
            Thread caller = Thread.currentThread();
            Runnable boundary =
                    () -> {
                        if (Thread.currentThread() != caller
                                || !account.equals(
                                        accounts.findByIdForOwner(ownerId, accountId).orElse(null))
                                || !credential.equals(
                                        credentials
                                                .findByCredentialIdForOwner(
                                                        ownerId, accountId, credentialId)
                                                .orElse(null)))
                            throw denied("FEE_SOURCE_CONTEXT_CHANGED");
                        KillSwitchSnapshot current = kill.snapshot();
                        // observedAt/traceId属于本次读取；只比较持久化安全身份，不能把正常重读误判为状态变化。
                        if (initial == null
                                || current == null
                                || initial.status() != KillSwitchStatus.ENGAGED
                                || current.status() != KillSwitchStatus.ENGAGED
                                || initial.scope() != current.scope()
                                || initial.version() != current.version()
                                || !Objects.equals(initial.updatedAt(), current.updatedAt())
                                || !Objects.equals(initial.source(), current.source())
                                || !Objects.equals(initial.reasonCode(), current.reasonCode()))
                            throw denied("FEE_KILL_STATE_CHANGED");
                    };
            boundary.run();
            jdbc.execute("SET LOCAL lock_timeout='1500ms'");
            // 锁和解密使用相同谓词；0/多条不解密，凭据/账户生命周期写入在整个来源事务内不能穿透锁。
            String selection =
                    """
                    FROM exchange_account_credentials c JOIN exchange_accounts a ON a.exchange_account_id=c.exchange_account_id
                    WHERE a.owner_user_id=? AND a.exchange_account_id=? AND a.exchange_code='BINANCE' AND a.trade_env='LIVE' AND a.status='ACTIVE'
                      AND c.credential_id=? AND c.credential_type=? AND c.is_active AND c.credential_status='ACTIVE'
                      AND c.verification_status='VERIFIED' AND c.revoked_at IS NULL AND c.rotated_at IS NULL
                    """;
            if (jdbc.query(
                                    "SELECT c.credential_id "
                                            + selection
                                            + " LIMIT 2 FOR SHARE OF a,c",
                                    (rs, index) -> rs.getLong(1),
                                    ownerId,
                                    accountId,
                                    credentialId,
                                    credentialType)
                            .size()
                    != 1) throw denied("FEE_CREDENTIAL_NOT_ACTIVE");
            String payload =
                    jdbc.queryForObject(
                            "SELECT pgp_sym_decrypt(c.encrypted_payload,?) " + selection,
                            String.class,
                            masterKey,
                            ownerId,
                            accountId,
                            credentialId,
                            credentialType);
            if (payload == null || payload.length() > 32768) throw denied("FEE_CREDENTIAL_INVALID");
            JsonNode json = mapper.readTree(payload);
            if (!json.isObject()
                    || json.has("privateKeyPath")
                    || !credentialType.equals(text(json, "credentialType", 32)))
                throw denied("FEE_CREDENTIAL_INVALID");
            key = text(json, "apiKey", 256).toCharArray();
            secret =
                    text(
                                    json,
                                    credentialType.equals("BINANCE_HMAC")
                                            ? "secretKey"
                                            : "privateKeyPem",
                                    credentialType.equals("BINANCE_HMAC") ? 1024 : 16384)
                            .toCharArray();
            // JDBC/Jackson及现有签名器有不可原地清零String边界；仅在本同步调用内创建，不存字段、缓存或返回。
            var material =
                    credentialType.equals("BINANCE_HMAC")
                            ? new BinanceApiCredentials(new String(key), new String(secret))
                            : new BinanceApiCredentials(
                                    new String(key),
                                    null,
                                    BinanceKeyType.ED25519,
                                    new String(secret),
                                    null);
            var nativeFee = reader.read(symbol.replace("-", ""), material, permission, boundary);
            boundary.run();
            var offer = nativeFee.discount();
            var terms =
                    new SpotCommissionTerms(
                            nativeFee.symbol(),
                            rates(nativeFee.standard()),
                            rates(nativeFee.tax()),
                            rates(nativeFee.special()),
                            new SpotCommissionTerms.Discount(
                                    offer.enabledForAccount(),
                                    offer.enabledForSymbol(),
                                    offer.asset(),
                                    offer.multiplier()),
                            nativeFee.responseSha256());
            return AccountFeeQuote.binance(
                    terms, nativeFee.providerResponseAt(), nativeFee.observedAt());
        } catch (AccountFeeSyncException failure) {
            throw failure;
        } catch (BinanceFeeReadException failure) {
            throw denied("FEE_PRIVATE_READ_" + failure.reason().name());
        } catch (Exception failure) {
            throw denied("FEE_PRIVATE_READ_FAILED");
        } finally {
            if (key != null) Arrays.fill(key, '\0');
            if (secret != null) Arrays.fill(secret, '\0');
        }
    }

    private static SpotCommissionTerms.Rates rates(BinanceCommissionRates value) {
        return new SpotCommissionTerms.Rates(
                value.maker(), value.taker(), value.buyer(), value.seller());
    }

    private static String text(JsonNode json, String field, int maximum) {
        if (!json.path(field).isTextual()
                || json.path(field).textValue().isBlank()
                || json.path(field).textValue().length() > maximum)
            throw denied("FEE_CREDENTIAL_INVALID");
        return json.path(field).textValue();
    }

    private static AccountFeeSyncException denied(String code) {
        return new AccountFeeSyncException(code);
    }

    @Override
    public String toString() {
        return "JdbcBinanceAccountFeeReadPort[REDACTED]";
    }
}
