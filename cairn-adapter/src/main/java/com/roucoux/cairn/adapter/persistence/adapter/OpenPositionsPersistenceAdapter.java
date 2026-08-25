package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.PositionEntity;
import com.roucoux.cairn.adapter.persistence.repository.OpenPositionJpaRepository;
import com.roucoux.cairn.domain.model.Position;
import com.roucoux.cairn.domain.model.PositionStatus;
import com.roucoux.cairn.domain.port.out.LoadOpenPositionsPort;
import java.util.List;
import org.springframework.stereotype.Component;

/** Outbound adapter: bulk read of the still-open positions, used by the batch module. */
@Component
class OpenPositionsPersistenceAdapter implements LoadOpenPositionsPort {

    private final OpenPositionJpaRepository jpaRepository;

    OpenPositionsPersistenceAdapter(OpenPositionJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<Position> findOpen() {
        return jpaRepository.findAllByStatus(PositionStatus.OPEN).stream()
                .map(PositionEntity::toDomain)
                .toList();
    }
}
