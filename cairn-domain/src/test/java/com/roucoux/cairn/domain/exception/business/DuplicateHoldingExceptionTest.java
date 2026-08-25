package com.roucoux.cairn.domain.exception.business;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DuplicateHoldingExceptionTest {

    @Test
    void buildsAMessageFromTheAccountAndTheInstrument() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();

        DuplicateHoldingException exception = new DuplicateHoldingException(accountId, instrumentId);

        assertThat(exception.getMessage())
                .isEqualTo("account " + accountId + " already holds instrument " + instrumentId);
    }
}
