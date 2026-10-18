package com.roucoux.cairn.application.json;

import java.math.BigDecimal;
import org.springframework.boot.jackson.JacksonComponent;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.jdk.NumberDeserializers;

@JacksonComponent
public class BoundedDecimalDeserializer extends ValueDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) {
        BigDecimal value = NumberDeserializers.BigDecimalDeserializer.instance.deserialize(parser, context);
        if (value != null && !DecimalBounds.isWithinBounds(value)) {
            return context.reportInputMismatch(BigDecimal.class, "number out of range");
        }
        return value;
    }

    @Override
    public Class<?> handledType() {
        return BigDecimal.class;
    }
}
