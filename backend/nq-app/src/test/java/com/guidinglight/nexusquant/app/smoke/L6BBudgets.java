package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayDeque;

/** 完整180min及三次恢复在T=0前计费；沿用实际单位事务成本和独立短窗口runaway门。 */
final class L6BBudgets {
    private final L6BContract contract;
    private final long baseline, cap, diskFloor;
    private final ObjectNode frozen;
    private final ArrayDeque<Point> recent=new ArrayDeque<>();
    private long lastAt=-1,lastTransactions;
    private int checkpoints;
    private long maximumRaw,maximumFiles;
    private record Point(long millis,long transactions) { }
    L6BBudgets(L6BContract contract,long baseline,long freeDisk,JsonNode entryActors) {
        this.contract=contract;this.baseline=baseline;
        long seconds=contract.timing.total()/L6BContract.SECOND;
        long cycles=Math.ceilDiv(seconds,5), invocations=2*cycles;
        long batch=Math.min(contract.orders,100), scanCost=2+batch*7;
        var groups=new ObjectMapper().createObjectNode()
                .put("qualificationReconciliation",invocations*scanCost).put("businessChain",contract.orders*50L)
                .put("scheduler",2*cycles).put("schedulerObservation",4*cycles)
                .put("validation",2*Math.ceilDiv(seconds,300)*4).put("sampler",contract.samples*4L)
                .put("controller",(cycles+Math.ceilDiv(contract.timing.total(),contract.manifest.intervalNanos())+Math.ceilDiv(seconds,60)+3)*3)
                .put("checkpoint",contract.checkpoints*3L).put("connectionHealthAndSetup",seconds*40+80);
        // 349是accepted完整空载fixture的绝对事务基线；重启不重新建库，用全fixture成本作保守bootstrap额度。
        long startup=3*(349+scanCost+75*40);
        long recovery=3*2*Math.ceilDiv(contract.orders,100)*scanCost;
        long delay=2*scanCost+50;
        groups.put("restartStartup",startup).put("restartFairCursorRecovery",recovery).put("boundedDelay",delay);
        long projected=0;for(var value:groups)projected+=value.asLong();
        long reserve=2*Math.ceilDiv(contract.orders,100)*scanCost+24*scanCost+50*9+1024;
        cap=Math.ceilDiv(projected+reserve,1000)*1000;
        diskFloor=Math.max(2L*1024*1024*1024,freeDisk/5);
        require(freeDisk>contract.rawCap+diskFloor && baseline>=0,"DISK_OR_BASELINE");
        require(entryActors.isArray() && entryActors.size()==2,"ACTORS");
        for(var a:entryActors) {
            require(a.path("poolMax").asInt()==10 && a.path("commands").isIntegralNumber() && a.path("tickStarted").isIntegralNumber(),"ENTRY_MEASUREMENT");
            require(a.path("tickStarted").asLong()+cycles+60<=L6BContract.TICK_CAP,"CALLBACK_BUDGET");
            require(a.path("commands").asLong()+2*cycles+contract.orders+128<=L6BContract.COMMAND_CAP,"COMMAND_BUDGET");
        }
        frozen=new ObjectMapper().createObjectNode().put("status","SUFFICIENT").put("frozenBeforeT0",true)
                .put("startTransactions",baseline).put("transactionSemantics","RUN_DELTA_AND_ROLLING_RATE")
                .put("projectedTransactions",projected).put("reserveTransactions",reserve).put("transactionHardCap",cap)
                .put("orders",contract.orders).put("fillsPerOrder",1).put("globalOrders",3000).put("rawHardCap",contract.rawCap)
                .put("entryFreeDiskBytes",freeDisk).put("diskFreeFloorBytes",diskFloor)
                .put("callbacksPerGeneration",L6BContract.TICK_CAP).put("commandsPerGeneration",L6BContract.COMMAND_CAP)
                .put("reconciliationCallsCap",invocations+3*(1+2*Math.ceilDiv(contract.orders,100))+2)
                .put("checkpointCap",contract.checkpoints).put("periodicSamples",contract.samples).put("boundaryAllowance",12)
                .put("fileCap",10000).put("derivedFileCount",contract.checkpoints+128)
                .put("restartCap",3).put("delayCap",1).put("appConnectionsCap",20);
        frozen.set("transactionGroups",groups);
        var dispositions=frozen.putObject("budgetDispositions");
        for(String name:new String[]{"transactions","orders","fills","schedulerCallbacks","commands","reconciliationCalls",
                "checkpoints","samples","files","rawEvidence","restartCount","disk"})dispositions.put(name,"SUFFICIENT");
    }
    synchronized void observe(JsonNode row) {
        var pg=row.path("sources").path("postgres").path("values");
        require(pg.path("transactions").isIntegralNumber(),"MISSING_TRANSACTIONS");
        check(row.path("elapsedMillis").asLong(),pg.path("transactions").asLong());
        require(pg.path("orders").asLong()<=contract.orders && pg.path("maximumFillsPerOrder").asLong()<=1
                && pg.path("appConnections").asLong()<=20,"BUSINESS_OR_POOL_BOUND");
        var files=row.path("sources").path("files").path("values");
        maximumRaw=Math.max(maximumRaw,files.path("ownedTempBytes").asLong());
        maximumFiles=Math.max(maximumFiles,files.path("ownedTempFileCount").asLong());
        require(maximumRaw<contract.rawCap && maximumFiles<=10000 && files.path("freeDiskBytes").asLong()>=diskFloor,"EVIDENCE_DISK_BOUND");
        for(String name:new String[]{"nq0","nq1"}) {
            var a=row.path("sources").path(name).path("values");
            if("RUNNING".equals(a.path("lifecycle").asText())) {
                require(a.path("transactionsByOriginAndOwner").isObject() && a.path("poolMax").asInt()==10,"ACTOR_ACCOUNTING");
                require(a.path("tickStarted").asLong()<=L6BContract.TICK_CAP && a.path("commands").asLong()<=L6BContract.COMMAND_CAP,"ACTOR_BOUND");
            }
        }
    }
    synchronized void check(long at,long total) {
        require(at>=0 && at>lastAt && total>=baseline && (lastAt<0 || total>=lastTransactions) && total-baseline<=cap,"TRANSACTION_CAP_OR_REGRESSION");
        for(var p:recent) {
            long span=at-p.millis;
            if(span>=50_000) {
                long cycles=Math.ceilDiv(span+10_000,5000);
                long rate=2*cycles*702+Math.ceilDiv(span+10_000,7117)*50+Math.ceilDiv(span+10_000,1000)*40
                        +1024+349+702;
                require(total-p.transactions<=rate,"TRANSACTION_RATE_AMPLIFICATION");break;
            }
        }
        recent.addLast(new Point(at,total));while(!recent.isEmpty() && at-recent.getFirst().millis>60_000)recent.removeFirst();
        lastAt=at;lastTransactions=total;
    }
    synchronized void checkpoint() { require(++checkpoints<=contract.checkpoints,"CHECKPOINT_CAP"); }
    synchronized void reconciliation(long count) { require(count<=frozen.path("reconciliationCallsCap").asLong(),"RECONCILIATION_CAP"); }
    synchronized void checkFinal(long total) { require(total>=baseline && total-baseline<=cap,"FINAL_TRANSACTIONS");frozen.put("finalTransactionDelta",total-baseline); }
    synchronized ObjectNode evidence() { return frozen.deepCopy().put("observedTransactionDelta",lastTransactions-baseline)
            .put("checkpointAttempts",checkpoints).put("maximumRawBytes",maximumRaw).put("maximumFiles",maximumFiles); }
    ObjectNode finalFiles(java.nio.file.Path directory) throws Exception {
        long bytes=0,count=0;
        try(var paths=java.nio.file.Files.walk(directory)) {
            for(var path:paths.filter(java.nio.file.Files::isRegularFile).toList()){bytes+=java.nio.file.Files.size(path);count++;}
        }
        require(bytes+65536<contract.rawCap && count+1<=10000
                && java.nio.file.Files.getFileStore(directory).getUsableSpace()>=diskFloor,"FINAL_EVIDENCE_BOUND");
        return new ObjectMapper().createObjectNode().put("status","PASS").put("rawBytesBeforeReceipt",bytes)
                .put("receiptReserveBytes",65536).put("rawHardCap",contract.rawCap).put("fileCountBeforeReceipt",count);
    }
    private static void require(boolean ok,String why) { if(!ok)throw new IllegalStateException("L6_B_BUDGET_"+why); }
}
