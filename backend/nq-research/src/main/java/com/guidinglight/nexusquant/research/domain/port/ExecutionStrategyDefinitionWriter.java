package com.guidinglight.nexusquant.research.domain.port;

import com.guidinglight.nexusquant.research.domain.ExecutionStrategyDefinitionDraft;

public interface ExecutionStrategyDefinitionWriter {

    String publish(ExecutionStrategyDefinitionDraft executionStrategyDefinitionDraft);
}


