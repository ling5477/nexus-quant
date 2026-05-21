package com.guidinglight.nexusquant.research.domain.paper.port;

import com.guidinglight.nexusquant.research.domain.paper.PaperRunScheduleFire;

import java.util.List;

public interface PaperRunScheduleFireRepository {

    void insert(PaperRunScheduleFire fire);

    List<PaperRunScheduleFire> listByScheduleId(String scheduleId);
}
