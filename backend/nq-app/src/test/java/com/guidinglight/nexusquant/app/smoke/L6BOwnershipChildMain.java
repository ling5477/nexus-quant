package com.guidinglight.nexusquant.app.smoke;

/** 负例只创建无DB/网络的测试子进程，用存活事实证明错误PID请求没有执行kill。 */
public final class L6BOwnershipChildMain {
    public static void main(String[] args) throws Exception {
        System.out.println("B0_READY ownership-test");System.out.flush();
        try(var input=new java.io.BufferedReader(new java.io.InputStreamReader(System.in))) {
            while(true){String line=input.readLine();if(line==null || "STOP".equals(line))return;}
        }
    }
}
