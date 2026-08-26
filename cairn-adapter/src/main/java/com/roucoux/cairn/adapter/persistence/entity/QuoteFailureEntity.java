package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.PriceSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quote_failures")
public class QuoteFailureEntity {

    @Id
    private UUID id;

    @Column(name = "instrument_id", nullable = false)
    private UUID instrumentId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PriceSource source;

    @Column(nullable = false, length = 500)
    private String message;

    protected QuoteFailureEntity() {}

    public static QuoteFailureEntity of(UUID instrumentId, PriceSource source, String message) {
        QuoteFailureEntity entity = new QuoteFailureEntity();
        entity.id = UUID.randomUUID();
        entity.instrumentId = instrumentId;
        entity.occurredAt = Instant.now();
        entity.source = source;
        entity.message = message;
        return entity;
    }
}
