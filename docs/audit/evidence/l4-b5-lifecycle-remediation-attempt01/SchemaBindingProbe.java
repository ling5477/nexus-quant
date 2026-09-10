package com.guidinglight.nexusquant.app.smoke;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.guidinglight.nexusquant.strategy.domain.StrategyDispatchIdentity;
import com.guidinglight.nexusquant.strategy.domain.StrategyRun;
import com.guidinglight.nexusquant.strategy.domain.StrategyRunStatus;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyRunRepository;
import com.guidinglight.nexusquant.strategy.infra.jdbc.JdbcStrategyRunRepository;
import com.guidinglight.nexusquant.trading.domain.OrderRecord;
import com.guidinglight.nexusquant.contracts.model.OrderStatus;
import com.guidinglight.nexusquant.trading.domain.port.OrdinaryPlaceAuthorityRepository;
import com.guidinglight.nexusquant.trading.infra.jdbc.JdbcOrdinaryPlaceAuthorityRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
public class SchemaBindingProbe {
 public static void main(String[] args) throws Exception {
  var m=new ObjectMapper();var proof=m.createObjectNode().put("scenario","SCHEMA_BINDING_DIAGNOSTIC").put("productionDispatchProof",false);
  try(var pg=B0Processes.Pg.start();var f=B0Fixture.create(pg)) {
   f.initialize(true,"http://127.0.0.1:1",B0Processes.cleanEnvironment());B5StrategyRunRecoveryProcessTest.seed(f);
   try(var c=new AnnotationConfigApplicationContext();var reader=f.checker()) {
    c.register(B5AdmissionPostgresTest.Transactions.class);
    c.registerBean(DataSource.class,()->new DriverManagerDataSource(f.url(),B0Fixture.APP,""));
    c.registerBean(JdbcTemplate.class,()->new JdbcTemplate(c.getBean(DataSource.class)));
    c.registerBean(PlatformTransactionManager.class,()->new DataSourceTransactionManager(c.getBean(DataSource.class)));
    c.registerBean(JdbcStrategyRunRepository.class);c.registerBean(JdbcOrdinaryPlaceAuthorityRepository.class);c.refresh();
    String run="run-"+UUID.randomUUID();var due=Instant.parse("2026-01-01T00:00:00Z");
    c.getBean(StrategyRunRepository.class).admit(new StrategyRun(run,"b5-strategy",1L,"OKX","SIM","SCHEDULER",StrategyRunStatus.CREATED,"{}","binding-probe",Instant.now(),null,null,"binding-probe"),new StrategyDispatchIdentity("b5","b5-strategy",1L,due));
    var tx=new TransactionTemplate(c.getBean(PlatformTransactionManager.class));tx.setTimeout(5);
    var repo=c.getBean(OrdinaryPlaceAuthorityRepository.class);
    try(var pool=Executors.newFixedThreadPool(2)) {
     var barrier=new CyclicBarrier(2);
     var a=pool.submit(()->{barrier.await(5,TimeUnit.SECONDS);tx.executeWithoutResult(s->repo.insertOrder(order(run,"binding-client-a"),Instant.now()));return true;});
     var b=pool.submit(()->{barrier.await(5,TimeUnit.SECONDS);tx.executeWithoutResult(s->repo.insertOrder(order(run,"binding-client-b"),Instant.now()));return true;});
     proof.put("bothTransactionsCommitted",a.get(15,TimeUnit.SECONDS)&&b.get(15,TimeUnit.SECONDS));
    }
    proof.put("database",f.name()).put("postgres",B5StrategyRunRecoveryProcessTest.value(reader,"SHOW server_version"));
    proof.put("flyway",B5StrategyRunRecoveryProcessTest.value(reader,"SELECT version FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 1"));
    for(String t:new String[]{"strategy_runs","orders","ordinary_place_authorities"})
     proof.set(t,m.readTree(B5StrategyRunRecoveryProcessTest.value(reader,"SELECT jsonb_agg(to_jsonb(t))::text FROM "+t+" t")));
    try(var app=DriverManager.getConnection(f.url(),B0Fixture.APP,"");var s=app.createStatement()) {
     app.setAutoCommit(false);
     try {s.execute("SELECT nq_create_ordinary_place_order('binding-duplicate',1,'"+run+"','OKX','BTC-USDT','binding-client-a','BUY','LIMIT',100,1,'NEW','diagnostic','binding-probe','SIM',0,now())");}
     catch(SQLException e) {proof.put("sameClientConflictSqlState",e.getSQLState());}
     try {s.executeQuery("SELECT count(*) FROM orders WHERE client_order_id='binding-client-a'");}
     catch(SQLException e) {proof.put("sameTransactionReadSqlState",e.getSQLState());}
     app.rollback();
    }
    if(proof.path("orders").size()!=2||proof.path("ordinary_place_authorities").size()!=2)throw new AssertionError(proof);
    Path p=Path.of(args[0]).resolve("raw-binding-proof.json");Files.writeString(p,m.writerWithDefaultPrettyPrinter().writeValueAsString(proof));
    SyntheticEvidenceExport.write(p,"B5-LC",3);
    System.out.println("SCHEMA_GAP run=1 orders=2 authorities=2 differentClients=true conflict="+proof.path("sameClientConflictSqlState")+" poisonedRead="+proof.path("sameTransactionReadSqlState"));
   }
  }
 }
 static OrderRecord order(String run,String client) {return new OrderRecord("order-"+UUID.randomUUID(),1L,run,"OKX","BTC-USDT",client,"BUY","LIMIT",new BigDecimal("100"),BigDecimal.ONE,null,OrderStatus.NEW,"ORDER_CREATED","binding-probe","SIM");}
}
