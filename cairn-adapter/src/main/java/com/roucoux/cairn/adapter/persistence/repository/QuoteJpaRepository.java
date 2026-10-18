package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.QuoteEntity;
import com.roucoux.cairn.adapter.persistence.entity.QuoteId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface QuoteJpaRepository extends JpaRepository<QuoteEntity, QuoteId> {

    List<QuoteEntity> findByIdInstrumentIdAndIdAsOfBetweenOrderByIdAsOf(
            UUID instrumentId, LocalDate from, LocalDate to);

    @Query("select q from QuoteEntity q where q.id.instrumentId in :ids "
            + "and q.id.asOf between :from and :to order by q.id.asOf")
    List<QuoteEntity> findAllBetween(
            @Param("ids") Set<UUID> ids, @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query(
            value = "select distinct on (instrument_id) * from quotes "
                    + "where instrument_id in (:ids) and as_of <= :day "
                    + "order by instrument_id, as_of desc",
            nativeQuery = true)
    List<QuoteEntity> findLatestOnOrBefore(@Param("ids") Set<UUID> ids, @Param("day") LocalDate day);

    @Query(
            value = "select instrument_id, min(as_of) as as_of from quotes "
                    + "where instrument_id in (:ids) group by instrument_id",
            nativeQuery = true)
    List<FirstQuoteDate> findFirstQuoteDates(@Param("ids") Set<UUID> ids);

    interface FirstQuoteDate {
        UUID getInstrumentId();

        LocalDate getAsOf();
    }

    @Query(
            value = "select q.* from instruments i cross join lateral "
                    + "(select * from quotes where instrument_id = i.id order by as_of desc limit 2) q "
                    + "where i.id in (:ids) order by q.instrument_id, q.as_of desc",
            nativeQuery = true)
    List<QuoteEntity> findLatestTwo(@Param("ids") Set<UUID> ids);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "insert into quotes (instrument_id, as_of, price, currency, source, fetched_at) "
                    + "values (:instrumentId, :asOf, :price, :currency, :source, :fetchedAt) "
                    + "on conflict (instrument_id, as_of) do update set price = excluded.price, "
                    + "currency = excluded.currency, source = excluded.source, fetched_at = excluded.fetched_at",
            nativeQuery = true)
    void upsert(
            @Param("instrumentId") UUID instrumentId,
            @Param("asOf") LocalDate asOf,
            @Param("price") BigDecimal price,
            @Param("currency") String currency,
            @Param("source") String source,
            @Param("fetchedAt") Instant fetchedAt);

    default long countAll() {
        return count();
    }
}
