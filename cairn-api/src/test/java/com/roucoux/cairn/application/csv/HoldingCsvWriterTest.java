package com.roucoux.cairn.application.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.HoldingResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HoldingCsvWriterTest {

    private static final String HEADER =
            "account,instrument,isin,quantity,averageCost,price,marketValueEur,unrealizedGainEur,priceAsOf";

    private final HoldingCsvWriter writer = new HoldingCsvWriter();

    @Test
    void startsWithAByteOrderMarkSoThatExcelReadsTheAccents() {
        assertThat(writer.write(List.of())).startsWith("﻿");
    }

    @Test
    void startsWithAHeaderRow() {
        assertThat(writer.write(List.of()).substring(1).split("\r\n")[0]).isEqualTo(HEADER);
    }

    @Test
    void writesOneRowPerHolding() {
        assertThat(writer.write(List.of(anEtf(), aPassbook())).split("\r\n")).hasSize(3);
    }

    @Test
    void quotesAFieldThatContainsAComma() {
        assertThat(writer.write(List.of(aPassbook()))).contains("\"Fortuneo, Livret A\"");
    }

    @Test
    void doublesAQuoteInsideAQuotedField() {
        assertThat(writer.write(List.of(aHoldingNamed("L\"Oreal")))).contains("\"L\"\"Oreal\"");
    }

    @Test
    void leavesAnUnknownValueEmptyRatherThanWritingAZero() {
        String row = writer.write(List.of(aPassbook())).split("\r\n")[1];

        assertThat(row).contains(",,").doesNotContain(",0,");
    }

    @Test
    void writesAmountsInPlainDecimalNotation() {
        assertThat(writer.write(List.of(aHoldingOfQuantity(new BigDecimal("0.00000012")))))
                .contains("0.00000012")
                .doesNotContain("E-");
    }

    @Test
    void producesOnlyAHeaderForAnEmptyPortfolio() {
        assertThat(writer.write(List.of()).substring(1).split("\r\n")).hasSize(1);
    }

    private static HoldingResponse anEtf() {
        HoldingResponse holding = new HoldingResponse();
        holding.setAccountName("CTO Boursorama");
        holding.setInstrumentName("Amundi PEA MSCI Emerging");
        holding.setIsin("FR0011869353");
        holding.setQuantity(new BigDecimal("12"));
        holding.setAverageCost(new BigDecimal("45.50"));
        holding.setPrice(new BigDecimal("52.30"));
        holding.setMarketValueEur(new BigDecimal("627.60"));
        holding.setUnrealizedGainEur(new BigDecimal("81.60"));
        holding.setPriceAsOf(LocalDate.of(2026, 8, 26));
        return holding;
    }

    private static HoldingResponse aPassbook() {
        HoldingResponse holding = new HoldingResponse();
        holding.setAccountName("Fortuneo, Livret A");
        holding.setInstrumentName("Livret A");
        holding.setQuantity(new BigDecimal("5000"));
        holding.setMarketValueEur(new BigDecimal("5000"));
        holding.setPriceAsOf(LocalDate.of(2026, 8, 26));
        return holding;
    }

    private static HoldingResponse aHoldingNamed(String instrumentName) {
        HoldingResponse holding = new HoldingResponse();
        holding.setAccountName("CTO Boursorama");
        holding.setInstrumentName(instrumentName);
        holding.setQuantity(new BigDecimal("3"));
        holding.setMarketValueEur(new BigDecimal("100"));
        holding.setPriceAsOf(LocalDate.of(2026, 8, 26));
        return holding;
    }

    private static HoldingResponse aHoldingOfQuantity(BigDecimal quantity) {
        HoldingResponse holding = new HoldingResponse();
        holding.setAccountName("Binance");
        holding.setInstrumentName("Bitcoin");
        holding.setQuantity(quantity);
        holding.setMarketValueEur(new BigDecimal("100"));
        holding.setPriceAsOf(LocalDate.of(2026, 8, 26));
        return holding;
    }
}
