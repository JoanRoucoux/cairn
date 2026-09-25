package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;

public record ImportRow(
        String accountName,
        AccountType accountType,
        String institution,
        String instrumentName,
        String isinOrTicker,
        BigDecimal quantity,
        BigDecimal averageCost) {}
