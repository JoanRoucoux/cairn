package com.roucoux.cairn.application.csv;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.generated.model.HoldingResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class HoldingCsvWriterTest {

    private static final String HEADER =
            "account;instrument;isin;quantity;averageCost;price;marketValueEur;unrealizedGainEur;priceAsOf";

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
    void quotesAFieldThatContainsTheSeparator() {
        assertThat(writer.write(List.of(aPassbook()))).contains("\"Woodgrove; Livret A\"");
    }

    @Test
    void leavesACommaAloneNowThatItSeparatesNothing() {
        assertThat(writer.write(List.of(aHoldingNamed("Amundi, MSCI World")))).contains("Amundi, MSCI World");
    }

    @Test
    void doublesAQuoteInsideAQuotedField() {
        assertThat(writer.write(List.of(aHoldingNamed("L\"Oreal")))).contains("\"L\"\"Oreal\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1", "-1", "@SUM(A1)", "\tcmd"})
    void defusesATextCellThatASpreadsheetWouldRunAsAFormula(String name) {
        String row = writer.write(List.of(aHoldingNamed(name))).split("\r\n", 2)[1];

        assertThat(row).contains(";'" + name + ";").doesNotContain(";" + name + ";");
    }

    @Test
    void defusesATextCellThatStartsWithACarriageReturn() {
        String csv = writer.write(List.of(aHoldingNamed("\rcmd")));

        assertThat(csv).contains("\"'\rcmd\"");
    }

    @Test
    void leavesNegativeNumbersAndPlainTextUntouched() {
        HoldingResponse holding = aHoldingNamed("Livret A");
        holding.setUnrealizedGainEur(new BigDecimal("-12.50"));

        String row = writer.write(List.of(holding)).split("\r\n", 2)[1];

        assertThat(row).contains(";Livret A;").contains(";-12.50;").doesNotContain("'");
    }

    @Test
    void leavesAnUnknownValueEmptyRatherThanWritingAZero() {
        String row = writer.write(List.of(aPassbook())).split("\r\n")[1];

        assertThat(row).contains(";;").doesNotContain(";0;");
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
        holding.setAccountName("Contoso Trading");
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
        holding.setAccountName("Woodgrove; Livret A");
        holding.setInstrumentName("Livret A");
        holding.setQuantity(new BigDecimal("5000"));
        holding.setMarketValueEur(new BigDecimal("5000"));
        holding.setPriceAsOf(LocalDate.of(2026, 8, 26));
        return holding;
    }

    private static HoldingResponse aHoldingNamed(String instrumentName) {
        HoldingResponse holding = new HoldingResponse();
        holding.setAccountName("Contoso Trading");
        holding.setInstrumentName(instrumentName);
        holding.setQuantity(new BigDecimal("3"));
        holding.setMarketValueEur(new BigDecimal("100"));
        holding.setPriceAsOf(LocalDate.of(2026, 8, 26));
        return holding;
    }

    private static HoldingResponse aHoldingOfQuantity(BigDecimal quantity) {
        HoldingResponse holding = new HoldingResponse();
        holding.setAccountName("Tailspin Wallet");
        holding.setInstrumentName("Bitcoin");
        holding.setQuantity(quantity);
        holding.setMarketValueEur(new BigDecimal("100"));
        holding.setPriceAsOf(LocalDate.of(2026, 8, 26));
        return holding;
    }
}
