package com.roucoux.cairn.adapter.persistence.entity;

import com.roucoux.cairn.domain.model.IntradayValuation;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "intraday_valuations")
public class IntradayValuationEntity {

    @Id
    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(name = "total_eur", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalEur;

    protected IntradayValuationEntity() {}

    public IntradayValuation toDomain() {
        return new IntradayValuation(at, totalEur);
    }
}
