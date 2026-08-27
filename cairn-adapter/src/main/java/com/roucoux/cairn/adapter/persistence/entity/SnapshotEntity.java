package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.Snapshot;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;

/**
 * Persists only the measured total, {@code snapshot_breakdowns} is not read here: no consumer
 * needs the per-instrument quantities and prices yet, so {@link Snapshot#quantities()} and
 * {@link Snapshot#prices()} come back empty.
 */
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

    public Snapshot toDomain() {
        return new Snapshot(asOf, totalEur, Map.of(), Map.of());
    }
}
