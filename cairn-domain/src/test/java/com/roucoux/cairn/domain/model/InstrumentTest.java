package com.roucoux.cairn.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class InstrumentTest {

    @Test
    void rejectsAQuotedInstrumentWithoutASourceReference() {
        assertThatThrownBy(() -> new Instrument(
                        UUID.randomUUID(),
                        "Accor",
                        "FR0000120404",
                        "EUR",
                        AssetClass.EQUITY,
                        PriceSource.YAHOO,
                        null,
                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sourceRef");
    }

    @Test
    void allowsAManuallyPricedInstrumentWithoutASourceReference() {
        Instrument cash = new Instrument(
                UUID.randomUUID(),
                "Livret A",
                null,
                "EUR",
                AssetClass.CASH,
                PriceSource.MANUAL,
                null,
                "Livret d'epargne reglementee");

        assertThat(cash.sourceRef()).isNull();
    }

    @Test
    void allowsAnInstrumentWithoutAnIsin() {
        Instrument ether = new Instrument(
                UUID.randomUUID(), "Ethereum", null, "EUR", AssetClass.CRYPTO, PriceSource.COINGECKO, "ethereum", null);

        assertThat(ether.isin()).isNull();
    }

    @Test
    void rejectsADescriptionLongerThanATweet() {
        assertThatThrownBy(() -> new Instrument(
                        UUID.randomUUID(),
                        "Accor",
                        "FR0000120404",
                        "EUR",
                        AssetClass.EQUITY,
                        PriceSource.YAHOO,
                        "AC.PA",
                        "x".repeat(281)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void derivesTheExternalUrlFromItsSource() {
        assertThat(instrument(PriceSource.YAHOO, "CW8.PA").externalUrl())
                .contains("https://finance.yahoo.com/quote/CW8.PA");
        assertThat(instrument(PriceSource.COINGECKO, "ethereum").externalUrl())
                .contains("https://www.coingecko.com/en/coins/ethereum");
        assertThat(instrument(PriceSource.MANUAL, null).externalUrl()).isEmpty();
    }

    private static Instrument instrument(PriceSource source, String sourceRef) {
        return new Instrument(UUID.randomUUID(), "Test", null, "EUR", AssetClass.EQUITY, source, sourceRef, null);
    }
}
