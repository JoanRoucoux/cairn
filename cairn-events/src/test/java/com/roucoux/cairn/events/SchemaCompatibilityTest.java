package com.roucoux.cairn.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.junit.jupiter.api.Test;

class SchemaCompatibilityTest {

    @Test
    void everyCurrentSchemaReadsDataWrittenWithItsReleasedCopy() throws IOException {
        Schema.Parser parser = new Schema.Parser();
        for (String name : new String[] {"EventMetadata", "AssetClass", "PriceSource", "RefreshTrigger"}) {
            parse(parser, name);
        }
        Schema releasedPrice = parse(parser, "PriceUpdatedEvent");
        Schema releasedRefresh = parse(parser, "RefreshCompletedEvent");

        assertThat(SchemaCompatibility.checkReaderWriterCompatibility(PriceUpdatedEvent.getClassSchema(), releasedPrice)
                        .getType())
                .as("PriceUpdatedEvent")
                .isEqualTo(SchemaCompatibilityType.COMPATIBLE);
        assertThat(SchemaCompatibility.checkReaderWriterCompatibility(
                                RefreshCompletedEvent.getClassSchema(), releasedRefresh)
                        .getType())
                .as("RefreshCompletedEvent")
                .isEqualTo(SchemaCompatibilityType.COMPATIBLE);
    }

    private static Schema parse(Schema.Parser parser, String name) throws IOException {
        try (InputStream in = SchemaCompatibilityTest.class.getResourceAsStream("/avro/released/" + name + ".avsc")) {
            return parser.parse(in);
        }
    }
}
