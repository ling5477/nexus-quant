package com.guidinglight.nexusquant.demo.registration;

import com.guidinglight.nexusquant.account.domain.port.SimAccountIdentityBridge;
import com.guidinglight.nexusquant.common.tenant.*;
import com.guidinglight.nexusquant.ledger.service.port.SimCashFundingPort;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 验证、可信身份绑定、账户和注资由同一 PostgreSQL 事务提交，失败不留下可用的部分状态。 */
@Service
public class DemoRegistrationService {
    private static final BigDecimal CAPITAL=new BigDecimal("10000.00000000");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PasswordEncoder passwords;
    private final SimAccountIdentityBridge bridge;
    private final SimCashFundingPort funding;
    private final Environment environment;
    private final DemoTokenCodec tokens;

    public DemoRegistrationService(DataSource source,PlatformTransactionManager manager,PasswordEncoder passwords,
            SimAccountIdentityBridge bridge,SimCashFundingPort funding,Environment environment) {
        // 注册最初没有租户；仅在服务端创建并验证身份后，显式设置当前事务的 GUC。
        this.jdbc=new JdbcTemplate(source);this.transactions=new TransactionTemplate(manager);
        transactions.setTimeout(20);transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.passwords=passwords;this.bridge=bridge;this.funding=funding;this.environment=environment;
        this.tokens=new DemoTokenCodec(environment.getProperty("nq.demo.token-secret","disabled"));
    }

    public void rateLimit(String action,String identity,int maximum) {
        Boolean allowed=transactions.execute(status -> {
            jdbc.update("""
                DELETE FROM demo_request_limits WHERE bucket_hash IN
                    (SELECT bucket_hash FROM demo_request_limits WHERE window_start<now()-interval '1 day' LIMIT 100)
                """);
            Integer count=jdbc.queryForObject("""
                INSERT INTO demo_request_limits(bucket_hash,window_start,attempts) VALUES(?,now(),1)
                ON CONFLICT(bucket_hash) DO UPDATE SET
                    attempts=CASE WHEN demo_request_limits.window_start<now()-interval '15 minutes' THEN 1 ELSE demo_request_limits.attempts+1 END,
                    window_start=CASE WHEN demo_request_limits.window_start<now()-interval '15 minutes' THEN now() ELSE demo_request_limits.window_start END
                RETURNING attempts
                """,Integer.class,DemoTokenCodec.digest(action+":"+identity));
            return count!=null && count<=maximum;
        });
        if(!Boolean.TRUE.equals(allowed)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"REQUEST_LIMITED");
    }

    public void register(UUID registrationId,String username,String email,String password,String confirmation) {
        requireEnabled();
        String name=normalize(username),address=normalize(email);
        if(registrationId==null || !name.matches("[a-z0-9][a-z0-9_.-]{2,63}") || !validEmail(address)
                || password==null || !password.equals(confirmation) || password.length()<12 || password.length()>72
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72 || password.indexOf('\0')>=0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"REGISTRATION_INPUT_INVALID");
        }
        String requestHash=tokens.fingerprint(name+'\0'+address+'\0'+password);
        String hash=passwords.encode(password);
        transactions.executeWithoutResult(status -> {
            var old=jdbc.queryForList("SELECT request_hash FROM demo_registrations WHERE registration_id=?",registrationId);
            if(!old.isEmpty() && !DemoTokenCodec.same((String)old.getFirst().get("request_hash"),requestHash))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"REGISTRATION_ID_CONFLICT");
            Long user=jdbc.queryForObject("SELECT nq_create_demo_pending(?,?,?,?,?)",Long.class,registrationId,name,address,hash,requestHash);
            if(user!=null && jdbc.queryForObject("SELECT count(*) FROM demo_email_verifications WHERE registration_id=?",Integer.class,registrationId)==0)
                enqueue(registrationId);
        });
    }

    public void resend(String email) {
        requireEnabled();String address=normalize(email);
        if(!validEmail(address)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"REGISTRATION_INPUT_INVALID");
        rateLimit("resend-email",address,3);
        transactions.executeWithoutResult(status -> {
            var registrations=jdbc.queryForList("""
                SELECT r.registration_id FROM demo_registrations r JOIN users u ON u.id=r.user_id
                WHERE u.email=? AND r.status='PENDING' FOR UPDATE OF r
                """,address);
            if(registrations.isEmpty()) return;
            UUID id=(UUID)registrations.getFirst().get("registration_id");
            Boolean recent=jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM demo_email_verifications WHERE registration_id=? AND created_at>now()-interval '60 seconds')",Boolean.class,id);
            if(Boolean.TRUE.equals(recent)) return;
            jdbc.update("UPDATE demo_email_verifications SET invalidated_at=now() WHERE registration_id=? AND consumed_at IS NULL AND invalidated_at IS NULL",id);
            jdbc.update("UPDATE demo_email_outbox SET status='CANCELLED' WHERE status IN ('PENDING','FAILED') AND token_id IN (SELECT token_id FROM demo_email_verifications WHERE registration_id=?)",id);
            enqueue(id);
        });
    }

    public boolean verify(String token) {
        requireEnabled();
        if(token==null || token.length()>128 || !token.matches("[0-9a-f-]{36}\\.[A-Za-z0-9_-]{43}")) return false;
        UUID tokenId;
        try { tokenId=UUID.fromString(token.substring(0,36)); } catch(IllegalArgumentException failure){return false;}
        return Boolean.TRUE.equals(transactions.execute(status -> {
            var refs=jdbc.queryForList("SELECT registration_id FROM demo_email_verifications WHERE token_id=?",tokenId);
            if(refs.isEmpty()) return false;
            UUID registration=(UUID)refs.getFirst().get("registration_id");
            var reg=jdbc.queryForMap("SELECT * FROM demo_registrations WHERE registration_id=? FOR UPDATE",registration);
            var verification=jdbc.queryForMap("SELECT *,expires_at>now() AS unexpired FROM demo_email_verifications WHERE token_id=? FOR UPDATE",tokenId);
            if(verification.get("invalidated_at")!=null || ((Number)verification.get("attempts")).intValue()>=5) return false;
            String digest=DemoTokenCodec.digest(token);
            if(!DemoTokenCodec.same((String)verification.get("token_hash"),digest)) {
                jdbc.update("UPDATE demo_email_verifications SET attempts=attempts+1 WHERE token_id=?",tokenId);return false;
            }
            if(verification.get("consumed_at")!=null) return "READY".equals(reg.get("status"));
            if(!Boolean.TRUE.equals(verification.get("unexpired"))) return false;
            jdbc.update("UPDATE demo_email_verifications SET consumed_at=now() WHERE token_id=?",tokenId);
            UUID tenant=jdbc.queryForObject("SELECT nq_initialize_demo_identity(?,?)",UUID.class,registration,digest);
            long user=((Number)reg.get("user_id")).longValue();
            // RLS 必须读取同事务已验证的用户；账户或注资失败会连同启用状态一起回滚。
            jdbc.update("UPDATE users SET enabled=true,email_verified_at=now(),updated_at=now() WHERE id=? AND NOT enabled",user);
            String code="DEMO_"+tenant.toString().replace("-","").toUpperCase(Locale.ROOT);
            var identity=new TenantIdentity(new TenantId(tenant),code,TenantKind.DEMO,user);
            try(var ignored=TenantContext.open(identity)) {
                jdbc.queryForObject("SELECT set_config('nq.tenant_id',?,true)",String.class,tenant.toString());
                jdbc.queryForObject("SELECT set_config('nq.user_id',?,true)",String.class,Long.toString(user));
                jdbc.queryForObject("SELECT set_config('nq.execution_epoch','1',true)",String.class);
                Long exchange=jdbc.queryForObject("""
                    INSERT INTO exchange_accounts(owner_user_id,exchange_code,trade_env,account_alias,is_default,tenant_id)
                    VALUES(?,'PAPER','SIM','Demo SIM',true,?) RETURNING exchange_account_id
                    """,Long.class,user,tenant);
                var summary=new com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary(
                        exchange,null,user,"PAPER","SIM","Demo SIM",null,true,"ACTIVE");
                long account=bridge.resolveOrCreateDemoSim(summary,"registration-"+registration,Instant.now());
                String paper="ptr-"+UUID.randomUUID();
                jdbc.update("INSERT INTO tenant_sim_accounts(tenant_id,exchange_account_id,account_id,reserved_paper_run_id) VALUES(?,?,?,?)",tenant,exchange,account,paper);
                funding.fundOnce(account,paper,CAPITAL,"registration-"+registration);
                jdbc.update("""
                    UPDATE demo_registrations SET status='READY',ready_at=now(),
                        cash_entry_id=(SELECT entry_id FROM ledger_entries WHERE account_id=? AND ref_id=? AND ref_type='SIM_FUNDING_CASH'),
                        contra_entry_id=(SELECT entry_id FROM ledger_entries WHERE account_id=? AND ref_id=? AND ref_type='SIM_FUNDING_CONTRA')
                    WHERE registration_id=?
                    """,account,paper,account,paper,registration);
            }
            return true;
        }));
    }

    public Result result() {
        var identity=TenantContext.requireCurrent();
        if(identity.kind()!=TenantKind.DEMO) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"DEMO_IDENTITY_REQUIRED");
        return transactions.execute(status -> {
            var rows=jdbc.queryForList("""
                SELECT d.*,r.status AS registration_status,p.status AS paper_status,c.status AS continuous_status
                FROM demo_registrations r JOIN tenant_sim_accounts d ON d.tenant_id=r.tenant_id
                LEFT JOIN paper_trading_runs p ON p.paper_run_id=d.bound_paper_run_id AND p.tenant_id=d.tenant_id
                LEFT JOIN continuous_sim_runs c ON c.paper_run_id=p.paper_run_id
                WHERE r.user_id=? AND d.tenant_id=?
                """,identity.userId(),identity.tenantId().value());
            if(rows.size()!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"INITIALIZATION_STATE_UNAVAILABLE");
            var row=rows.getFirst();String state;
            if(!"READY".equals(row.get("registration_status"))) state="INITIALIZING";
            else if(row.get("bound_paper_run_id")==null) state="SIM_NOT_BOUND";
            else if("STOPPED".equals(row.get("paper_status")) || "STOPPED".equals(row.get("continuous_status"))) state="SIM_STOPPED";
            else if("RUNNING".equals(row.get("paper_status")) || "RUNNING".equals(row.get("continuous_status"))) state="SIM_RUNNING";
            else if("CREATED".equals(row.get("paper_status"))) state="INITIALIZED";
            else throw new ResponseStatusException(HttpStatus.CONFLICT,"SIM_STATE_UNAVAILABLE");
            return new Result(state,identity.tenantId().value(),((Number)row.get("exchange_account_id")).longValue(),
                    ((Number)row.get("account_id")).longValue(),(String)row.get("reserved_paper_run_id"),(String)row.get("bound_paper_run_id"),CAPITAL.toPlainString(),"USDT");
        });
    }

    private void enqueue(UUID registration) {
        UUID id=UUID.randomUUID();String digest=DemoTokenCodec.digest(tokens.token(id));
        jdbc.update("INSERT INTO demo_email_verifications(token_id,registration_id,token_hash,expires_at) VALUES(?,?,?,now()+interval '30 minutes')",id,registration,digest);
        jdbc.update("INSERT INTO demo_email_outbox(token_id) VALUES(?)",id);
    }
    public com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary account() {
        var identity=TenantContext.requireCurrent();
        if(identity.kind()!=TenantKind.DEMO) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"DEMO_IDENTITY_REQUIRED");
        return transactions.execute(status -> {
            var accounts=jdbc.query("""
                SELECT e.* FROM tenant_sim_accounts s JOIN exchange_accounts e
                    ON e.exchange_account_id=s.exchange_account_id AND e.tenant_id=s.tenant_id AND e.legacy_account_id=s.account_id
                WHERE s.tenant_id=? AND e.owner_user_id=? AND e.exchange_code='PAPER' AND e.trade_env='SIM' LIMIT 2
                """,(rs,row) -> new com.guidinglight.nexusquant.account.domain.ExchangeAccountSummary(
                        rs.getLong("exchange_account_id"),rs.getLong("legacy_account_id"),rs.getLong("owner_user_id"),
                        rs.getString("exchange_code"),rs.getString("trade_env"),rs.getString("account_alias"),
                        rs.getString("external_account_ref"),rs.getBoolean("is_default"),rs.getString("status")),
                    identity.tenantId().value(),identity.userId());
            if(accounts.size()!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"INITIALIZATION_STATE_UNAVAILABLE");
            return accounts.getFirst();
        });
    }
    private void requireEnabled() {
        if(!environment.getProperty("nq.demo.registration-enabled",Boolean.class,false))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"REGISTRATION_CLOSED");
    }
    private static String normalize(String value){return value==null?"":value.strip().toLowerCase(Locale.ROOT);}
    private static boolean validEmail(String value){return value.length()<=254 && value.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9](?:[a-z0-9.-]*[a-z0-9])?\\.[a-z]{2,63}");}
    public record Result(String status,UUID tenantId,long exchangeAccountId,long accountId,String reservedPaperRunId,
            String boundPaperRunId,String initialCapital,String currency){}
}
