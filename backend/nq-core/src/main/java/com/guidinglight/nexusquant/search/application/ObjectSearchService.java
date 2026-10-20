package com.guidinglight.nexusquant.search.application;

import com.guidinglight.nexusquant.search.domain.ObjectSearchHit;
import com.guidinglight.nexusquant.search.domain.ObjectSearchRepository;

import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/** 最多20条一页和1000条定位窗口，通配符按普通文本匹配。 */
public class ObjectSearchService {
    private final ObjectSearchRepository repository;

    public ObjectSearchService(ObjectSearchRepository repository) {
        this.repository = repository;
    }

    /** 校验2至80字输入、20条页步长及1000条窗口；搜索不改变默认账户或授予运行权限。 */
    @Transactional(readOnly = true, timeout = 5)
    public Page search(long ownerUserId, String query, String kind, int offset) {
        String text = query == null ? "" : query.trim();
        if (ownerUserId <= 0
                || text.length() < 2
                || text.length() > 80
                || offset < 0
                || offset > 980
                || offset % 20 != 0
                || !Set.of("ALL", "ACCOUNT", "STRATEGY", "VERSION", "RUN", "SIM", "ORDER")
                        .contains(kind)) {
            throw new IllegalArgumentException("invalid bounded object search");
        }
        var found = repository.findOwned(ownerUserId, text, kind, offset, 21);
        return new Page(
                found.stream().limit(20).toList(), offset < 980 && found.size() > 20, offset);
    }

    public record Page(List<ObjectSearchHit> items, boolean hasMore, int offset) {}
}
