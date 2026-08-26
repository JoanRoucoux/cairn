package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.Holding;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "holdings")
public class HoldingEntity {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Column(nullable = false, precision = 28, scale = 12)
    private BigDecimal quantity;

    @Column(name = "average_cost", precision = 19, scale = 6)
    private BigDecimal averageCost;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected HoldingEntity() {}

    public static HoldingEntity fromDomain(Holding holding) {
        HoldingEntity entity = new HoldingEntity();
        entity.id = holding.id();
        entity.accountId = holding.accountId();
        entity.instrumentId = holding.instrumentId();
        entity.quantity = holding.quantity();
        entity.averageCost = holding.averageCost();
        entity.updatedAt = Instant.now();
        return entity;
    }

    public Holding toDomain() {
        return new Holding(id, accountId, instrumentId, quantity, averageCost);
    }
}
