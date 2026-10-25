package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.QuoteFailureEntity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface QuoteFailureJpaRepository extends JpaRepository<QuoteFailureEntity, UUID> {

    @Transactional
    @Modifying
    @Query("delete from QuoteFailureEntity f where f.occurredAt < :cutoff")
    void deleteByOccurredAtBefore(@Param("cutoff") Instant cutoff);
}
