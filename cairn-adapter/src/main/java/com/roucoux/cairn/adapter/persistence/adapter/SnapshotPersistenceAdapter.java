package com.roucoux.cairn.adapter.persistence.adapter;

import com.roucoux.cairn.adapter.persistence.entity.SnapshotBreakdownEntity;
import com.roucoux.cairn.adapter.persistence.entity.SnapshotEntity;
import com.roucoux.cairn.adapter.persistence.repository.SnapshotBreakdownJpaRepository;
import com.roucoux.cairn.adapter.persistence.repository.SnapshotJpaRepository;
import com.roucoux.cairn.domain.model.Snapshot;
import com.roucoux.cairn.domain.port.out.LoadSnapshotsPort;
import com.roucoux.cairn.domain.port.out.SaveSnapshotPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class SnapshotPersistenceAdapter implements LoadSnapshotsPort, SaveSnapshotPort {

    private final SnapshotJpaRepository repository;
    private final SnapshotBreakdownJpaRepository breakdownRepository;
    private final Clock clock;

    public SnapshotPersistenceAdapter(
            SnapshotJpaRepository repository, SnapshotBreakdownJpaRepository breakdownRepository, Clock clock) {
        this.repository = repository;
        this.breakdownRepository = breakdownRepository;
        this.clock = clock;
    }

    @Override
    public List<Snapshot> findBetween(LocalDate from, LocalDate to) {
        return repository.findByAsOfBetweenOrderByAsOf(from, to).stream()
                .map(SnapshotEntity::toDomainWithoutBreakdowns)
                .toList();
    }

    @Override
    public void save(Snapshot snapshot) {
        repository.save(SnapshotEntity.fromDomain(snapshot, clock));
        breakdownRepository.deleteByAsOf(snapshot.date());
        breakdownRepository.saveAll(breakdowns(snapshot));
    }

    private static List<SnapshotBreakdownEntity> breakdowns(Snapshot snapshot) {
        List<SnapshotBreakdownEntity> entities = new ArrayList<>();
        addBreakdowns(entities, snapshot.date(), SnapshotBreakdownEntity.ACCOUNT_TYPE, snapshot.byAccountType());
        addBreakdowns(entities, snapshot.date(), SnapshotBreakdownEntity.ASSET_CLASS, snapshot.byAssetClass());
        return entities;
    }

    private static void addBreakdowns(
            List<SnapshotBreakdownEntity> entities, LocalDate asOf, String dimension, Map<String, BigDecimal> values) {
        values.forEach((key, value) -> entities.add(SnapshotBreakdownEntity.of(asOf, dimension, key, value)));
    }
}
