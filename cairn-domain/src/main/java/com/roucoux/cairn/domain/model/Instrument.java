package com.roucoux.cairn.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record Instrument(
        UUID id,
        String name,
        String isin,
        String currency,
        AssetClass assetClass,
        PriceSource priceSource,
        String sourceRef,
        String description) {

    public static final int MAX_DESCRIPTION_LENGTH = 280;

    public Instrument {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(assetClass, "assetClass");
        Objects.requireNonNull(priceSource, "priceSource");
        if (priceSource != PriceSource.MANUAL && (sourceRef == null || sourceRef.isBlank())) {
            throw new IllegalArgumentException("sourceRef is required unless priceSource is MANUAL");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("description must not exceed " + MAX_DESCRIPTION_LENGTH);
        }
    }

    public boolean isRefreshable() {
        return priceSource != PriceSource.MANUAL;
    }

    public Optional<String> externalUrl() {
        return switch (priceSource) {
            case YAHOO -> Optional.of("https://finance.yahoo.com/quote/" + sourceRef);
            case COINGECKO -> Optional.of("https://www.coingecko.com/en/coins/" + sourceRef);
            case SG_SIRIUS ->
                Optional.of("https://investmentsolutions.societegenerale.fr/fr/nos-fonds/autres-fonds/details/isin/"
                        + sourceRef + "/");
            case MANUAL -> Optional.empty();
        };
    }
}
