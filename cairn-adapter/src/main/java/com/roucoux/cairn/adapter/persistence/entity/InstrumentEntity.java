package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "instruments")
public class InstrumentEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 12)
    private String isin;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_class", nullable = false, length = 20)
    private AssetClass assetClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "price_source", nullable = false, length = 20)
    private PriceSource priceSource;

    @Column(name = "source_ref", length = 64)
    private String sourceRef;

    @Column(length = 280)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected InstrumentEntity() {}

    public static InstrumentEntity fromDomain(Instrument instrument) {
        InstrumentEntity entity = new InstrumentEntity();
        entity.id = instrument.id();
        entity.name = instrument.name();
        entity.isin = instrument.isin();
        entity.currency = instrument.currency();
        entity.assetClass = instrument.assetClass();
        entity.priceSource = instrument.priceSource();
        entity.sourceRef = instrument.sourceRef();
        entity.description = instrument.description();
        entity.createdAt = Instant.now();
        return entity;
    }

    public Instrument toDomain() {
        return new Instrument(id, name, isin, currency, assetClass, priceSource, sourceRef, description);
    }
}
