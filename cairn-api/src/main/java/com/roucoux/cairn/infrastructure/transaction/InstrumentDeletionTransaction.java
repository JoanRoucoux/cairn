package com.roucoux.cairn.infrastructure.transaction;

import com.roucoux.cairn.domain.port.in.ManageInstrumentUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
