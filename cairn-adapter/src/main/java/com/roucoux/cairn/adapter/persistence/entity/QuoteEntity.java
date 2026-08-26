package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "quotes")
public class QuoteEntity {

    @EmbeddedId
    private QuoteId id;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal price;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PriceSource source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    protected QuoteEntity() {}

    public static QuoteEntity fromDomain(Quote quote) {
        QuoteEntity entity = new QuoteEntity();
        entity.id = new QuoteId(quote.instrumentId(), quote.asOf());
        entity.price = quote.price();
        entity.currency = quote.currency();
        entity.source = quote.source();
        entity.fetchedAt = quote.fetchedAt();
        return entity;
    }

    public Quote toDomain() {
        return new Quote(id.instrumentId(), id.asOf(), price, currency, source, fetchedAt);
    }
}
