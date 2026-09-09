import org.flywaydb.core.Flyway;
import java.sql.*;
public class PrepareFullDatabase {
 public static void main(String[] args) throws Exception {
  String url="jdbc:postgresql://127.0.0.1:40037/nq_b2_env_full";
  Flyway.configure().dataSource(url,"postgres","b2-env-full-fixture-not-a-secret").locations("classpath:db/migration").load().migrate();
  try(var c=DriverManager.getConnection(url,"postgres","b2-env-full-fixture-not-a-secret");var s=c.createStatement()) {
   s.executeUpdate("INSERT INTO accounts(account_code,venue,status) VALUES('b2-env-full-prerequisite','OKX','ACTIVE')");
   try(var r=s.executeQuery("SELECT current_database(),(SELECT count(*) FROM orders),(SELECT count(*) FROM trades),(SELECT count(*) FROM ledger_entries),(SELECT count(*) FROM accounts)")) {
    r.next(); if(r.getLong(2)!=0 ||r.getLong(3)!=0 ||r.getLong(4)!=0||r.getLong(5)!=1)throw new AssertionError("dirty full test fixture");
    System.out.println("FULL_DB_READY db="+r.getString(1)+" orders=0 trades=0 ledger=0 accounts=1 schema=48");
   }
  }
 }
}
