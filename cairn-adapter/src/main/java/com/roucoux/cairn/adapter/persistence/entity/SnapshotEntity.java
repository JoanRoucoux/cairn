package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.Snapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

@Entity
@Table(name = "snapshots")
public class SnapshotEntity {

    @Id
    @Column(name = "as_of", nullable = false)
    private LocalDate asOf;

    @Column(name = "total_eur", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalEur;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SnapshotEntity() {}

    public static SnapshotEntity fromDomain(Snapshot snapshot, Clock clock) {
        SnapshotEntity entity = new SnapshotEntity();
        entity.asOf = snapshot.date();
        entity.totalEur = snapshot.totalEur();
        entity.createdAt = clock.instant();
        return entity;
    }

    public Snapshot toDomainWithoutBreakdowns() {
        return new Snapshot(asOf, totalEur, Map.of(), Map.of());
    }

    public LocalDate asOf() {
        return asOf;
    }
}
