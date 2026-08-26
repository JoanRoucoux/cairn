package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.HoldingEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HoldingJpaRepository extends JpaRepository<HoldingEntity, UUID> {

    Optional<HoldingEntity> findByAccountIdAndInstrumentId(UUID accountId, UUID instrumentId);
}
