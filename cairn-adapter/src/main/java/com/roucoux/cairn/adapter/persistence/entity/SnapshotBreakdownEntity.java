package com.roucoux.cairn.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "snapshot_breakdowns")
public class SnapshotBreakdownEntity {

    public static final String ACCOUNT_TYPE = "ACCOUNT_TYPE";
    public static final String ASSET_CLASS = "ASSET_CLASS";

    @EmbeddedId
    private SnapshotBreakdownId id;

    @Column(name = "value_eur", nullable = false, precision = 19, scale = 4)
    private BigDecimal valueEur;

    protected SnapshotBreakdownEntity() {}

    public static SnapshotBreakdownEntity of(LocalDate asOf, String dimension, String key, BigDecimal valueEur) {
        SnapshotBreakdownEntity entity = new SnapshotBreakdownEntity();
        entity.id = new SnapshotBreakdownId(asOf, dimension, key);
        entity.valueEur = valueEur;
        return entity;
    }
}
