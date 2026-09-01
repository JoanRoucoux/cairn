package com.roucoux.cairn.domain.model;

import java.math.BigDecimal;

/**
 * One position submitted for import, already structurally parsed. {@code isinOrTicker} is whatever
 * identifies the instrument to a price source: an ISIN, a ticker or a provider id.
 */
public record ImportRow(
        String accountName,
        AccountType accountType,
        String institution,
        String instrumentName,
        String isinOrTicker,
        BigDecimal quantity,
        BigDecimal averageCost) {}
