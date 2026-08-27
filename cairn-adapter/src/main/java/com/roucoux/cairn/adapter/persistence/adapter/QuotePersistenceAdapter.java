package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.QuoteEntity;
import com.roucoux.cairn.adapter.persistence.repository.QuoteJpaRepository;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** Outbound adapter: implements the domain's read and write ports for quotes with Spring Data JPA. */
@Component
public class QuotePersistenceAdapter implements LoadQuotesPort, SaveQuotePort {

    private final QuoteJpaRepository repository;

    public QuotePersistenceAdapter(QuoteJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Quote> findLatest(UUID instrumentId) {
        return repository
                .findFirstByIdInstrumentIdOrderByIdAsOfDesc(instrumentId)
                .map(QuoteEntity::toDomain);
    }

    @Override
    public Optional<Quote> findPrevious(UUID instrumentId, LocalDate before) {
        return repository
                .findFirstByIdInstrumentIdAndIdAsOfLessThanOrderByIdAsOfDesc(instrumentId, before)
                .map(QuoteEntity::toDomain);
    }

    @Override
    public List<Quote> findBetween(UUID instrumentId, LocalDate from, LocalDate to) {
        return repository.findByIdInstrumentIdAndIdAsOfBetweenOrderByIdAsOf(instrumentId, from, to).stream()
                .map(QuoteEntity::toDomain)
                .toList();
    }

    @Override
    public Map<UUID, List<Quote>> findBetweenForAll(Set<UUID> instrumentIds, LocalDate from, LocalDate to) {
        return repository.findAllBetween(instrumentIds, from, to).stream()
                .map(QuoteEntity::toDomain)
                .collect(Collectors.groupingBy(Quote::instrumentId));
    }

    @Override
    public void upsert(Quote quote) {
        repository.save(QuoteEntity.fromDomain(quote));
    }

    @Override
    public void upsertAll(List<Quote> quotes) {
        repository.saveAll(quotes.stream().map(QuoteEntity::fromDomain).toList());
    }
}
