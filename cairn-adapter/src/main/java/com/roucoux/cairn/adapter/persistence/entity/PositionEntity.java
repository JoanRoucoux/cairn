package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.Position;
import com.roucoux.cairn.domain.model.PositionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "positions")
public class PositionEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 12)
    private String isin;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PositionStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PositionEntity() {}

    public static PositionEntity fromDomain(Position position) {
        PositionEntity entity = new PositionEntity();
        entity.id = position.id();
        entity.isin = position.isin();
        entity.quantity = position.quantity();
        entity.price = position.price();
        entity.status = position.status();
        entity.createdAt = position.createdAt();
        return entity;
    }

    public Position toDomain() {
        return new Position(id, isin, quantity, price, status, createdAt);
    }
}
