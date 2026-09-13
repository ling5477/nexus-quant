package com.guidinglight.nexusquant.app.smoke;

import java.sql.Connection;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** 初始化独立诊断来源的合法输入；不制造交易成功、Shadow执行或比较成功事实。 */
final class L6ValidationFixture {
    static void seed(Connection owner) throws Exception {
        String dataset = UUID.randomUUID().toString();
        String run = UUID.randomUUID().toString();
        String payload = "{\"fixture\":true,\"bars\":[]}";
        String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8)));
        try (var s = owner.createStatement()) {
            s.execute("INSERT INTO strategy_versions(strategy_version_id,strategy_code,version,version_name,checksum) "
                    + "VALUES('l6-validation-version','l6-strategy-1',1,'L6 diagnostic input','" + checksum + "')");
            s.execute("INSERT INTO marketdata_datasets(dataset_id,dataset_name,exchange_code,market_type,symbol,\"interval\","
                    + "start_time,end_time,status,quality_status,source,created_by) VALUES('" + dataset
                    + "','L6 empty diagnostic dataset','OKX','SPOT','BTC-USDT','1m',CURRENT_TIMESTAMP-INTERVAL '1 minute',"
                    + "CURRENT_TIMESTAMP,'CREATED','INCOMPLETE','L6_FIXTURE','L6_FIXTURE')");
            s.execute("INSERT INTO shadow_runs(id,strategy_version_id,dataset_id,status,idempotency_key,trace_id) VALUES('"
                    + run + "','l6-validation-version','" + dataset + "','CREATED','l6-diagnostic-input','l6-fixture')");
            s.execute("INSERT INTO shadow_run_events(id,shadow_run_id,event_type,to_status,trace_id) VALUES('"
                    + UUID.randomUUID() + "','" + run + "','CREATED','CREATED','l6-fixture')");
            s.execute("INSERT INTO shadow_run_snapshots(id,shadow_run_id,snapshot_type,sequence_no,source,schema_version,"
                    + "checksum,payload,captured_at,trace_id) VALUES('" + UUID.randomUUID() + "','" + run
                    + "','INPUT_MARKETDATA',0,'L6_FIXTURE','v1','" + checksum + "','" + payload + "',CURRENT_TIMESTAMP,'l6-fixture')");
            s.execute("INSERT INTO shadow_consistency_reports(id,shadow_run_id,comparison_status,limitations,trace_id) VALUES('"
                    + UUID.randomUUID() + "','" + run + "','NOT_COMPARABLE','[\"Fixture has no Paper comparison\"]','l6-fixture')");
        }
    }
}
