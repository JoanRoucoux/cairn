package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.QuoteEntity;
import com.roucoux.cairn.adapter.persistence.entity.QuoteId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuoteJpaRepository extends JpaRepository<QuoteEntity, QuoteId> {

    Optional<QuoteEntity> findFirstByIdInstrumentIdOrderByIdAsOfDesc(UUID instrumentId);

    Optional<QuoteEntity> findFirstByIdInstrumentIdAndIdAsOfLessThanOrderByIdAsOfDesc(
            UUID instrumentId, LocalDate before);

    List<QuoteEntity> findByIdInstrumentIdAndIdAsOfBetweenOrderByIdAsOf(
            UUID instrumentId, LocalDate from, LocalDate to);

    @Query("select q from QuoteEntity q where q.id.instrumentId in :ids "
            + "and q.id.asOf between :from and :to order by q.id.asOf")
    List<QuoteEntity> findAllBetween(
            @Param("ids") Set<UUID> ids, @Param("from") LocalDate from, @Param("to") LocalDate to);

    default long countAll() {
        return count();
    }
}
