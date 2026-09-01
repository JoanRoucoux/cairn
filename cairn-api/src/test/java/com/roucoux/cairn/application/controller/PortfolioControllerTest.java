package com.roucoux.cairn.application.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.csv.HoldingCsvWriter;
import com.roucoux.cairn.application.csv.PortfolioCsvReader;
import com.roucoux.cairn.application.mapper.HoldingRestMapper;
import com.roucoux.cairn.application.mapper.PortfolioRestMapper;
import com.roucoux.cairn.domain.exception.business.NonEurHoldingException;
import com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.ImportError;
import com.roucoux.cairn.domain.model.ImportErrorCode;
import com.roucoux.cairn.domain.model.ImportReport;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Portfolio;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import com.roucoux.cairn.infrastructure.transaction.PortfolioImportTransaction;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "app.security.password=test-password")
@WebMvcTest(PortfolioController.class)
@Import({
    WebAuthnConfig.class,
    PortfolioRestMapper.class,
    HoldingRestMapper.class,
    HoldingCsvWriter.class,
    PortfolioCsvReader.class,
    PortfolioControllerTest.ClockConfig.class
})
class PortfolioControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetPortfolioUseCase getPortfolio;

    @MockitoBean
    private PortfolioImportTransaction importPortfolio;

    @MockitoBean
    private LoadHoldingsPort loadHoldings;

    @MockitoBean
    private ValueHoldingUseCase valueHolding;

    @MockitoBean
    private JdbcOperations jdbcOperations;

    @TestConfiguration
    static class ClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-08-26T20:00:00Z"), ZoneOffset.UTC);
        }
    }

    @Test
    void returnsTheValuedPortfolio() throws Exception {
        when(getPortfolio.get()).thenReturn(aPortfolioOf(new BigDecimal("278146.45")));

        mockMvc.perform(get("/portfolio").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEur").value(278146.45))
                .andExpect(jsonPath("$.holdings").isArray());
    }

    @Test
    void roundsMoneyToTwoDecimalsOnTheWireOnly() throws Exception {
        when(getPortfolio.get()).thenReturn(aPortfolioOf(new BigDecimal("278146.4512345")));

        mockMvc.perform(get("/portfolio").with(user("joan")))
                .andExpect(jsonPath("$.totalEur").value(278146.45));
    }

    @Test
    void reportsAnAbsentUnrealizedGainAsNullNotZero() throws Exception {
        when(getPortfolio.get()).thenReturn(aPortfolioWithoutCostBasis());

        mockMvc.perform(get("/portfolio").with(user("joan")))
                .andExpect(jsonPath("$.unrealizedGainEur").doesNotExist());
    }

    @Test
    void mapsANonEurHoldingTo422InsteadOfCrashing() throws Exception {
        when(getPortfolio.get()).thenThrow(new NonEurHoldingException("US0378331005", "USD"));

        mockMvc.perform(get("/portfolio").with(user("joan"))).andExpect(status().isUnprocessableEntity());
    }

    @Test
    void refusesAnUnauthenticatedCall() throws Exception {
        mockMvc.perform(get("/portfolio")).andExpect(status().isUnauthorized());
    }

    @Test
    void servesThePortfolioAsACsvAttachmentDatedToday() throws Exception {
        Holding holding =
                new Holding(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("10"), null);
        when(loadHoldings.findAll()).thenReturn(List.of(holding));
        when(valueHolding.value(holding)).thenReturn(Optional.of(aValuedHolding(holding)));

        mockMvc.perform(get("/portfolio/export").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cairn-2026-08-26.csv\""));
    }

    @Test
    void refusesAnUnauthenticatedExport() throws Exception {
        mockMvc.perform(get("/portfolio/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void importsAPortfolioAndReportsWhatItChanged() throws Exception {
        when(importPortfolio.run(anyList())).thenReturn(new ImportReport(1, 2, 3, 4));

        mockMvc.perform(post("/portfolio/import")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType("text/csv")
                        .content(PortfolioCsvReader.HEADER + "\r\n"
                                + "Sample Broker,PEA,Sample Bank,Tracker,LU0000000001,100,20.00\r\n"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountsCreated").value(1))
                .andExpect(jsonPath("$.instrumentsCreated").value(2))
                .andExpect(jsonPath("$.holdingsCreated").value(3))
                .andExpect(jsonPath("$.holdingsUpdated").value(4));
    }

    @Test
    void listsEveryRefusedRowAsALineNumberedProblemDetail() throws Exception {
        when(importPortfolio.run(anyList()))
                .thenThrow(new PortfolioImportRejectedException(
                        List.of(new ImportError(0, ImportErrorCode.UNRESOLVED_INSTRUMENT, "GGT.PA"))));

        mockMvc.perform(post("/portfolio/import")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType("text/csv")
                        .content(PortfolioCsvReader.HEADER + "\r\n"
                                + "Sample Broker,PEA,Sample Bank,Tracker,LU0000000001,100,20.00\r\n"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.errors[0].line").value(2))
                .andExpect(jsonPath("$.errors[0].code").value("UNRESOLVED_INSTRUMENT"))
                .andExpect(jsonPath("$.errors[0].value").value("GGT.PA"));
    }

    @Test
    void servesTheImportTemplateAsItsOwnHeaderRow() throws Exception {
        mockMvc.perform(get("/portfolio/import/template").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(PortfolioCsvReader.HEADER)));
    }

    private static ValuedHolding aValuedHolding(Holding holding) {
        Instrument instrument = new Instrument(
                holding.instrumentId(),
                "Apple Inc.",
                "US0378331005",
                "USD",
                AssetClass.EQUITY,
                PriceSource.YAHOO,
                "AAPL",
                null);
        Account account = new Account(holding.accountId(), "CTO Boursorama", AccountType.CTO, "Boursorama");
        Quote quote = new Quote(
                holding.instrumentId(),
                LocalDate.of(2026, 8, 26),
                new BigDecimal("123.45"),
                "USD",
                PriceSource.YAHOO,
                Instant.parse("2026-08-26T20:00:00Z"));
        return new ValuedHolding(holding, instrument, account, quote, null);
    }

    private static Portfolio aPortfolioOf(BigDecimal total) {
        Money totalMoney = Money.eur(total);
        return new Portfolio(
                totalMoney,
                Money.eur(new BigDecimal("100")),
                Optional.of(Money.eur(new BigDecimal("50"))),
                List.of(),
                List.of(),
                List.of(aHolding()),
                0);
    }

    private static Portfolio aPortfolioWithoutCostBasis() {
        return new Portfolio(
                Money.eur(new BigDecimal("1000")),
                Money.eur(new BigDecimal("10")),
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                0);
    }

    private static ValuedHolding aHolding() {
        UUID accountId = UUID.randomUUID();
        UUID instrumentId = UUID.randomUUID();
        Holding holding = new Holding(UUID.randomUUID(), accountId, instrumentId, new BigDecimal("10"), null);
        Instrument instrument = new Instrument(
                instrumentId, "Apple Inc.", "US0378331005", "USD", AssetClass.EQUITY, PriceSource.YAHOO, "AAPL", null);
        Account account = new Account(accountId, "CTO Boursorama", AccountType.CTO, "Boursorama");
        Quote quote = new Quote(
                instrumentId,
                LocalDate.of(2026, 8, 26),
                new BigDecimal("123.45"),
                "USD",
                PriceSource.YAHOO,
                Instant.parse("2026-08-26T20:00:00Z"));
        return new ValuedHolding(holding, instrument, account, quote, null);
    }
}
