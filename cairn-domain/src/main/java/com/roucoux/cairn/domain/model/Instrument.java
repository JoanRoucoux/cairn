package com.roucoux.cairn.domain.model;

import com.roucoux.cairn.domain.exception.business.InvalidInstrumentException;
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
        // The unique indexes on isin and source_ref only let duplicates through when the column is null,
        // so a blank one read as a value would collide with the next instrument that has none either.
        isin = blankToNull(isin);
        sourceRef = blankToNull(sourceRef);
        description = blankToNull(description);
        if (priceSource != PriceSource.MANUAL && sourceRef == null) {
            throw new InvalidInstrumentException("sourceRef is required unless priceSource is MANUAL");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new InvalidInstrumentException("description must not exceed " + MAX_DESCRIPTION_LENGTH);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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
