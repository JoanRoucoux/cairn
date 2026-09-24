package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.IntradayValuationEntity;
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
    @Modifying
    @Query("delete from IntradayValuationEntity v where v.at < :cutoff")
    void deleteByAtBefore(@Param("cutoff") Instant cutoff);
}
