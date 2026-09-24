package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.IntradayValuationEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntradayValuationJpaRepository extends JpaRepository<IntradayValuationEntity, Instant> {

    List<IntradayValuationEntity> findByAtBetweenOrderByAtAsc(Instant from, Instant to);

    void deleteByAtBefore(Instant cutoff);
}
