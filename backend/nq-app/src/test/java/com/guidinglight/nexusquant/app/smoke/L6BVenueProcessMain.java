package com.guidinglight.nexusquant.app.smoke;

/** B专属入口从只读参数取得有界日志和订单容量，协议仍使用同一Synthetic Venue。 */
public final class L6BVenueProcessMain {
    public static void main(String[] args) throws Exception {
        L6BContract.runManifest();
        var p=new com.fasterxml.jackson.databind.ObjectMapper().readTree(java.nio.file.Files.readAllBytes(java.nio.file.Path.of("parameters.json")));
        int orders=p.path("runOrderBudget").asInt();
        L6BContract.require(orders>0 && orders<=1434);
        B0SyntheticVenueMain.boundedWorkload=true;
        B0SyntheticVenueMain.boundedOrderCapacity=orders;
        B0SyntheticVenueMain.l6BJournal=new L6BVenueJournal(p.path("journalEventCap").asLong(),p.path("journalByteCap").asLong());
        B0SyntheticVenueMain.main(args);
    }
}
