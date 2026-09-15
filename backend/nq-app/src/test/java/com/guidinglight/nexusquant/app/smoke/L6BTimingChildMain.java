package com.guidinglight.nexusquant.app.smoke;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** 协议负例的独立进程：受控延迟不代表生产reconcile结论。 */
public final class L6BTimingChildMain {
    public static void main(String[] args) throws Exception {
        System.out.println("B0_READY timing"); System.out.flush();
        try (var input = new BufferedReader(new InputStreamReader(System.in))) {
            String command;
            while ((command = input.readLine()) != null) {
                if (command.equals("DIE")) return;
                if (command.equals("HANG")) {
                    var executor = L6Measurements.commands();
                    try { L6CommandExecution.await(executor, () -> { Thread.sleep(60000); return "LATE"; }); }
                    finally { executor.shutdownNow(); }
                }
                if (command.equals("SLOW")) Thread.sleep(6000);
                System.out.println("B0_RESULT " + command); System.out.flush();
            }
        }
    }
}
