package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.IntradayValuationEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface IntradayValuationJpaRepository extends JpaRepository<IntradayValuationEntity, Instant> {

    List<IntradayValuationEntity> findByAtGreaterThanEqualAndAtLessThanOrderByAtAsc(Instant from, Instant to);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
            value = "insert into intraday_valuations (at, total_eur) values (:at, :totalEur) "
                    + "on conflict (at) do update set total_eur = excluded.total_eur",
            nativeQuery = true)
    void upsert(@Param("at") Instant at, @Param("totalEur") BigDecimal totalEur);

    @Transactional
    @Modifying
    @Query("delete from IntradayValuationEntity v where v.at < :cutoff")
    void deleteByAtBefore(@Param("cutoff") Instant cutoff);
}
