package com.roucoux.cairn.adapter.persistence.repository;

import com.roucoux.cairn.adapter.persistence.entity.QuoteFailureEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuoteFailureJpaRepository extends JpaRepository<QuoteFailureEntity, UUID> {

    default long countAll() {
        return count();
    }
}
