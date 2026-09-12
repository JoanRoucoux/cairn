package com.roucoux.cairn.infrastructure.transaction;

import com.roucoux.cairn.domain.port.in.ManageInstrumentUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting an instrument requires deleting every one of its holdings first, which the domain
 * expresses as several distinct port calls; this wrapper is what makes them commit or fail
 * together, since {@code cairn-domain} is plain Java and cannot open a transaction itself.
 *
 * <p>Deliberately not an implementation of {@link ManageInstrumentUseCase}: an inbound port is
 * implemented by domain services only, and ArchUnit's {@code useCasesAreImplementedByDomainServicesOnly}
 * rejects a wrapper that did. The controller depends on this class, not on the port, so the
 * transaction cannot be bypassed by accident.
 */
@Component
public class InstrumentDeletionTransaction {

    private final ManageInstrumentUseCase manageInstrument;

    InstrumentDeletionTransaction(ManageInstrumentUseCase manageInstrument) {
        this.manageInstrument = manageInstrument;
    }

    @Transactional
    public void run(UUID id) {
        manageInstrument.delete(id);
    }
}
