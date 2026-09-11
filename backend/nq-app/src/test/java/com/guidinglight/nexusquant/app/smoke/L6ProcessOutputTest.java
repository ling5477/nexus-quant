package com.guidinglight.nexusquant.app.smoke;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证长跑增量读取不会把正在写入的半行或旧结果当作新响应。 */
class L6ProcessOutputTest {
    @Test void waitsForCompleteUtf8LineAndPreservesResultSequence() throws Exception {
        var dir = Files.createTempDirectory(B0Processes.root().resolve("backend/nq-app/target"), "l6-output-");
        var child = new B0Processes.Child(FragmentedOutput.class, dir, "echo", B0Processes.cleanEnvironment());
        try {
            assertEquals("echo", child.ready());
            assertEquals("echo", child.ready());
            assertEquals("第一条", child.send("one"));
            assertEquals("第二条", child.send("two"));
        } finally { B0Processes.closeChildren(List.of(child)); }
    }

    public static final class FragmentedOutput {
        public static void main(String[] args) throws Exception {
            System.out.println("B0_READY echo");
            try (var input = new BufferedReader(new InputStreamReader(System.in))) {
                String command;
                while ((command = input.readLine()) != null) {
                    if (command.equals("STOP")) return;
                    System.out.print("B0_RESULT "); System.out.flush();
                    Thread.sleep(250);
                    System.out.println(command.equals("one") ? "第一条" : "第二条");
                    System.out.flush();
                }
            }
        }
    }
}
