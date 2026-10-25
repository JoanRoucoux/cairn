package com.roucoux.cairn.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.apache.avro.util.ClassSecurityValidator;
import org.junit.jupiter.api.Test;

class TrustedEventClassesTest {

    @Test
    void registerTrustsTheGeneratedEventClasses() {
        TrustedEventClasses.register();

        assertThat(ClassSecurityValidator.getGlobal().isTrusted(PriceUpdatedEvent.class))
                .isTrue();
        assertThat(ClassSecurityValidator.getGlobal().isTrusted(EventMetadata.class))
                .isTrue();
        assertThat(ClassSecurityValidator.getGlobal().isTrusted(AssetClass.class))
                .isTrue();
    }

    @Test
    void registerKeepsTheDefaultTrust() {
        TrustedEventClasses.register();

        assertThat(ClassSecurityValidator.getGlobal().isTrusted(BigDecimal.class))
                .isTrue();
    }

    @Test
    void registeringTwiceIsHarmless() {
        TrustedEventClasses.register();
        TrustedEventClasses.register();

        assertThat(ClassSecurityValidator.getGlobal().isTrusted(PriceUpdatedEvent.class))
                .isTrue();
        assertThat(ClassSecurityValidator.getGlobal().isTrusted(BigDecimal.class))
                .isTrue();
    }
}
