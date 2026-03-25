package com.guidinglight.nexusquant.research.port;

import com.guidinglight.nexusquant.research.model.ExecutionStrategyDefinitionDraft;

public interface ExecutionStrategyDefinitionWriter {

    String publish(ExecutionStrategyDefinitionDraft executionStrategyDefinitionDraft);
}
