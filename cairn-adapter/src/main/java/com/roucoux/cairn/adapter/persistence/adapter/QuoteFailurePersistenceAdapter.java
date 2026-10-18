package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.QuoteFailureEntity;
import com.roucoux.cairn.adapter.persistence.repository.QuoteFailureJpaRepository;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.out.RecordQuoteFailurePort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

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

    @Override
    public void deleteBefore(Instant cutoff) {
        repository.deleteByOccurredAtBefore(cutoff);
    }
}
