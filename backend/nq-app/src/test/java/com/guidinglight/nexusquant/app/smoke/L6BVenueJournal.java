package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Venue自己写一次完整事件日志；快照绑定不可变前缀，避免重复物化整个历史。 */
final class L6BVenueJournal {
    static final String FILE = "venue-events.ndjson";
    private final ObjectMapper json = new ObjectMapper();
    private final Path file = Path.of(FILE);
    private final MessageDigest digest;
    private final long eventCap, byteCap;
    private long count, bytes;
    private ObjectNode pending;
    L6BVenueJournal(long eventCap, long byteCap) throws Exception {
        L6BContract.require(eventCap > 0 && eventCap <= 1_000_000 && byteCap == eventCap*512);
        this.eventCap=eventCap; this.byteCap=byteCap; digest=MessageDigest.getInstance("SHA-256");
        Files.createFile(file);
    }
    ObjectNode event(String type, String client) {
        flush(); L6BContract.require(count < eventCap);
        pending=json.createObjectNode().put("sequence",++count).put("type",type)
                .put("nanoTime",System.nanoTime()).put("client",client);
        return pending;
    }
    void flush() {
        if (pending==null) return;
        try {
            byte[] line=(json.writeValueAsString(pending)+"\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            L6BContract.require(line.length<=512 && bytes+line.length<=byteCap);
            Files.write(file,line,StandardOpenOption.APPEND); digest.update(line); bytes+=line.length; pending=null;
        } catch (Exception failure) { throw new IllegalStateException("L6_B_JOURNAL_WRITE_FAILED",failure); }
    }
    ObjectNode snapshot() {
        flush();
        try { return json.createObjectNode().put("path",FILE).put("eventCount",count).put("bytes",bytes)
                .put("sha256",HexFormat.of().formatHex(((MessageDigest)digest.clone()).digest())); }
        catch (CloneNotSupportedException failure) { throw new IllegalStateException(failure); }
    }
}
