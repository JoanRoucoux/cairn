package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.QuoteFailureEntity;
import com.roucoux.cairn.adapter.persistence.repository.QuoteFailureJpaRepository;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Outbound adapter: implements the domain's port to record a failed quote refresh with Spring Data JPA. */
@Component
public class QuoteFailurePersistenceAdapter implements RecordQuoteFailurePort {

    private final QuoteFailureJpaRepository repository;

    public QuoteFailurePersistenceAdapter(QuoteFailureJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(UUID instrumentId, PriceSource source, String message) {
        repository.save(QuoteFailureEntity.of(instrumentId, source, message));
    }
}
