package com.guidinglight.nexusquant.scheduler.control;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;

/** 间隔与版本只能是完整JSON整数，拒绝小数和数字字符串，避免静默截断改变用户意图。 */
public final class ScheduledOperationWholeNumberDeserializer extends JsonDeserializer<Long> {
    @Override
    public Long deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            return context.reportInputMismatch(
                    Long.class, "scheduler values require a whole JSON integer");
        }
        return parser.getLongValue();
    }
}
