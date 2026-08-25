package com.roucoux.cairn.domain.exception.business;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotFoundExceptionTest {

    @Test
    void buildsAMessageFromTheKindAndTheId() {
        UUID id = UUID.randomUUID();

        NotFoundException exception = new NotFoundException("Account", id);

        assertThat(exception.getMessage()).isEqualTo("Account " + id + " not found");
    }
}
