package com.roucoux.cairn.adapter.messaging.adapter;

import com.roucoux.cairn.domain.model.PriceSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

record PriceUpdatedData(
        UUID instrumentId, LocalDate asOf, BigDecimal price, String currency, PriceSource priceSource) {}
