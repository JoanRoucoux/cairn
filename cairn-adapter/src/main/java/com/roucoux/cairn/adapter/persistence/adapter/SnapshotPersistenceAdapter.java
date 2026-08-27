package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.SnapshotEntity;
import com.roucoux.cairn.adapter.persistence.repository.SnapshotJpaRepository;
import com.roucoux.cairn.domain.model.Snapshot;
import com.roucoux.cairn.domain.port.out.LoadSnapshotsPort;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** Outbound adapter: implements the domain's read port for measured snapshots with Spring Data JPA. */
@Component
public class SnapshotPersistenceAdapter implements LoadSnapshotsPort {

    private final SnapshotJpaRepository repository;

    public SnapshotPersistenceAdapter(SnapshotJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<Snapshot> findBetween(LocalDate from, LocalDate to) {
        return repository.findByAsOfBetweenOrderByAsOf(from, to).stream()
                .map(SnapshotEntity::toDomain)
                .toList();
    }
}
