package com.roucoux.cairn.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.csv.HoldingCsvWriter;
import com.roucoux.cairn.application.mapper.HoldingRestMapper;
import com.roucoux.cairn.domain.exception.business.DuplicateHoldingException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.ManageHoldingUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
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
@WebMvcTest(HoldingController.class)
@Import({WebAuthnConfig.class, HoldingRestMapper.class, HoldingCsvWriter.class, HoldingControllerTest.ClockConfig.class
})
class HoldingControllerTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final UUID HOLDING_ID = UUID.randomUUID();
    private static final String VALID_BODY = """
            {"accountId":"%s","instrumentId":"%s","quantity":4,"averageCost":43.64}
            """.formatted(ACCOUNT_ID, INSTRUMENT_ID);
    private static final Holding A_HOLDING =
            new Holding(HOLDING_ID, ACCOUNT_ID, INSTRUMENT_ID, new BigDecimal("4"), new BigDecimal("43.64"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManageHoldingUseCase manageHolding;

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
    void createsAHolding() throws Exception {
        when(manageHolding.create(any(), any(), any(), any())).thenReturn(A_HOLDING);
        when(valueHolding.value(A_HOLDING)).thenReturn(Optional.of(aValuedHolding(A_HOLDING)));

        mockMvc.perform(post("/holdings")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountName").value("CTO Boursorama"))
                .andExpect(jsonPath("$.accountType").value("CTO"))
                .andExpect(jsonPath("$.instrumentName").value("Apple Inc."))
                .andExpect(jsonPath("$.assetClass").value("EQUITY"))
                .andExpect(jsonPath("$.price").value(123.45))
                .andExpect(jsonPath("$.priceCurrency").value("USD"))
                .andExpect(jsonPath("$.priceSource").value("YAHOO"))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.marketValueEur").exists());
    }

    @Test
    void fallsBackToTheBareHoldingWhenItCannotBeValuedYet() throws Exception {
        when(manageHolding.create(any(), any(), any(), any())).thenReturn(A_HOLDING);
        when(valueHolding.value(A_HOLDING)).thenReturn(Optional.empty());

        mockMvc.perform(post("/holdings")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(HOLDING_ID.toString()))
                .andExpect(jsonPath("$.price").doesNotExist());
    }

    @Test
    void listsHoldingsWithTheirFullValuation() throws Exception {
        when(loadHoldings.findAll()).thenReturn(List.of(A_HOLDING));
        when(valueHolding.value(A_HOLDING)).thenReturn(Optional.of(aValuedHolding(A_HOLDING)));

        mockMvc.perform(get("/holdings").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accountName").value("CTO Boursorama"))
                .andExpect(jsonPath("$[0].price").value(123.45))
                .andExpect(jsonPath("$[0].marketValueEur").exists())
                .andExpect(jsonPath("$[0].stale").value(false));
    }

    @Test
    void omitsAHoldingThatCannotBeValuedFromTheList() throws Exception {
        when(loadHoldings.findAll()).thenReturn(List.of(A_HOLDING));
        when(valueHolding.value(A_HOLDING)).thenReturn(Optional.empty());

        mockMvc.perform(get("/holdings").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void reportsAnAbsentAverageCostAsNullNotZero() throws Exception {
        Holding holdingWithoutCostBasis = new Holding(HOLDING_ID, ACCOUNT_ID, INSTRUMENT_ID, new BigDecimal("4"), null);
        when(manageHolding.create(any(), any(), any(), any())).thenReturn(holdingWithoutCostBasis);
        when(valueHolding.value(holdingWithoutCostBasis))
                .thenReturn(Optional.of(aValuedHolding(holdingWithoutCostBasis)));

        mockMvc.perform(post("/holdings")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.averageCost").doesNotExist());
    }

    @Test
    void reportsADuplicateAs422() throws Exception {
        when(manageHolding.create(any(), any(), any(), any()))
                .thenThrow(new DuplicateHoldingException(ACCOUNT_ID, INSTRUMENT_ID));

        mockMvc.perform(post("/holdings")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void reportsAnUnknownHoldingAs404() throws Exception {
        doThrow(new NotFoundException("holding", HOLDING_ID))
                .when(manageHolding)
                .delete(HOLDING_ID);

        mockMvc.perform(delete("/holdings/{id}", HOLDING_ID).with(user("joan")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void deletesAHolding() throws Exception {
        mockMvc.perform(delete("/holdings/{id}", HOLDING_ID).with(user("joan")).with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void servesTheHoldingsAsACsvAttachment() throws Exception {
        when(loadHoldings.findAll()).thenReturn(List.of(A_HOLDING));
        when(valueHolding.value(A_HOLDING)).thenReturn(Optional.of(aValuedHolding(A_HOLDING)));

        mockMvc.perform(get("/holdings/export").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8"))
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cairn-2026-08-26.csv\""));
    }

    @Test
    void refusesAnUnauthenticatedExport() throws Exception {
        mockMvc.perform(get("/holdings/export")).andExpect(status().isUnauthorized());
    }

    @Test
    void refusesAWriteWithoutACsrfToken() throws Exception {
        mockMvc.perform(post("/holdings")
                        .with(user("joan"))
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    private static ValuedHolding aValuedHolding(Holding holding) {
        Instrument instrument = new Instrument(
                INSTRUMENT_ID, "Apple Inc.", "US0378331005", "USD", AssetClass.EQUITY, PriceSource.YAHOO, "AAPL", null);
        Account account = new Account(ACCOUNT_ID, "CTO Boursorama", AccountType.CTO, "Boursorama");
        Quote quote = new Quote(
                INSTRUMENT_ID,
                LocalDate.of(2026, 8, 26),
                new BigDecimal("123.45"),
                "USD",
                PriceSource.YAHOO,
                Instant.parse("2026-08-26T20:00:00Z"));
        return new ValuedHolding(holding, instrument, account, quote, null);
    }
}
