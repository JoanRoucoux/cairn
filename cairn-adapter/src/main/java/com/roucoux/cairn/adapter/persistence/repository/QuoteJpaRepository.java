package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.QuoteEntity;
import com.roucoux.cairn.adapter.persistence.entity.QuoteId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteJpaRepository extends JpaRepository<QuoteEntity, QuoteId> {

    Optional<QuoteEntity> findFirstByIdInstrumentIdOrderByIdAsOfDesc(UUID instrumentId);

    Optional<QuoteEntity> findFirstByIdInstrumentIdAndIdAsOfLessThanOrderByIdAsOfDesc(
            UUID instrumentId, LocalDate before);

    List<QuoteEntity> findByIdInstrumentIdAndIdAsOfBetweenOrderByIdAsOf(
            UUID instrumentId, LocalDate from, LocalDate to);

    default long countAll() {
        return count();
    }
}
