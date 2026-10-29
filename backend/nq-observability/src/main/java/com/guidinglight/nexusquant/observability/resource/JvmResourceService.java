package com.guidinglight.nexusquant.observability.resource;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 采样不访问数据库、供应方、账户或交易命令，读取也不触发额外采样。 */
@Service
public class JvmResourceService {
    private final JvmResourceSampler sampler = new JvmResourceSampler();

    /** 仅定期采集当前JVM资源；进程内窗口不持久化，重启后以新身份重新积累。 */
    @Scheduled(fixedDelay = 15_000)
    public void sample() {
        sampler.sample();
    }

    public JvmResourceSampler.Snapshot read() {
        return sampler.snapshot();
    }
}
