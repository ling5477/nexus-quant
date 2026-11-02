package com.guidinglight.nexusquant.demo.registration;

import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 有界 Outbox 重试；SMTP 回执丢失最多重复发送同一令牌，不重复注册或注资。 */
@Component
public class DemoEmailOutboxDispatcher {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Environment environment;
    private final DemoTokenCodec tokens;
    private final JavaMailSenderImpl mail=new JavaMailSenderImpl();
    public DemoEmailOutboxDispatcher(DataSource source,PlatformTransactionManager manager,Environment environment) {
        jdbc=new JdbcTemplate(source);transactions=new TransactionTemplate(manager);transactions.setTimeout(15);
        this.environment=environment;tokens=new DemoTokenCodec(environment.getProperty("nq.demo.token-secret","disabled"));
        mail.setHost(environment.getProperty("spring.mail.host","127.0.0.1"));
        mail.setPort(environment.getProperty("spring.mail.port",Integer.class,1025));
        var properties=mail.getJavaMailProperties();
        properties.setProperty("mail.smtp.connectiontimeout","2000");
        properties.setProperty("mail.smtp.timeout","2000");properties.setProperty("mail.smtp.writetimeout","2000");
    }

    @Scheduled(fixedDelayString="${nq.demo.mail-poll-ms:1000}")
    public void dispatch() {
        if(!environment.getProperty("nq.demo.mail-dispatch-enabled",Boolean.class,false))return;
        for(int count=0;count<10;count++) {
            Boolean found=transactions.execute(status -> {
                var rows=jdbc.queryForList("""
                    SELECT o.token_id,v.expires_at>now() AS unexpired,v.consumed_at,v.invalidated_at,u.email,o.attempts
                    FROM demo_email_outbox o JOIN demo_email_verifications v USING(token_id)
                    JOIN demo_registrations r USING(registration_id) JOIN users u ON u.id=r.user_id
                    WHERE o.status='PENDING' AND o.next_attempt_at<=now() ORDER BY o.next_attempt_at,o.token_id
                    LIMIT 1 FOR UPDATE OF o SKIP LOCKED
                    """);
                if(rows.isEmpty())return false;
                var row=rows.getFirst();UUID id=(UUID)row.get("token_id");
                if(!Boolean.TRUE.equals(row.get("unexpired")) || row.get("consumed_at")!=null || row.get("invalidated_at")!=null) {
                    jdbc.update("UPDATE demo_email_outbox SET status='CANCELLED' WHERE token_id=?",id);return true;
                }
                int attempts=((Number)row.get("attempts")).intValue()+1;
                try {
                    var message=new SimpleMailMessage();message.setFrom("verification@nexusquant.local");
                    message.setTo((String)row.get("email"));message.setSubject("NexusQuant email verification / 邮箱验证");
                    message.setText("Verify your email / 验证邮箱（30分钟内有效）:\n"
                            +environment.getRequiredProperty("nq.demo.verification-base-url")+"#token="+tokens.token(id));
                    mail.send(message);
                    jdbc.update("UPDATE demo_email_outbox SET status='SENT',attempts=?,sent_at=now() WHERE token_id=?",attempts,id);
                } catch(org.springframework.mail.MailException failure) {
                    // 只保存受控状态，不持久化可能包含地址、凭据或令牌的发送异常。
                    jdbc.update("UPDATE demo_email_outbox SET attempts=?,status=?,next_attempt_at=now()+(? * interval '1 second') WHERE token_id=?",
                            attempts,attempts>=6?"FAILED":"PENDING",Math.min(300,1<<attempts),id);
                }
                return true;
            });
            if(!Boolean.TRUE.equals(found))break;
        }
    }
}
