package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.IntradayValuationEntity;
import com.roucoux.cairn.adapter.persistence.repository.IntradayValuationJpaRepository;
import com.roucoux.cairn.domain.model.IntradayValuation;
import com.roucoux.cairn.domain.port.out.LoadValuationsPort;
import com.roucoux.cairn.domain.port.out.SaveValuationPort;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Outbound adapter: implements the domain's read and write ports for intraday valuations with Spring Data JPA. */
@Component
public class ValuationPersistenceAdapter implements LoadValuationsPort, SaveValuationPort {

    private final IntradayValuationJpaRepository repository;

    public ValuationPersistenceAdapter(IntradayValuationJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<IntradayValuation> findBetween(Instant from, Instant to) {
        return repository.findByAtBetweenOrderByAtAsc(from, to).stream()
                .map(IntradayValuationEntity::toDomain)
                .toList();
    }

    @Override
    public void upsert(IntradayValuation valuation) {
        repository.save(IntradayValuationEntity.fromDomain(valuation));
    }

    @Override
    @Transactional
    public void deleteBefore(Instant cutoff) {
        repository.deleteByAtBefore(cutoff);
    }
}
