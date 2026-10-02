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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.HoldingRestMapper;
import com.roucoux.cairn.domain.exception.business.DuplicateHoldingException;
import com.roucoux.cairn.domain.exception.business.InsufficientQuantityException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.exception.business.SavingsAccountLineException;
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
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "app.security.password=test-password")
@WebMvcTest(HoldingController.class)
@Import({WebAuthnConfig.class, HoldingRestMapper.class, HoldingControllerTest.ClockConfig.class})
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
                .andExpect(jsonPath("$.priceFetchedAt").value("2026-08-26T20:00:00Z"))
                .andExpect(jsonPath("$.priceSource").value("YAHOO"))
                .andExpect(jsonPath("$.stale").value(false))
                .andExpect(jsonPath("$.marketValueEur").doesNotExist())
                .andExpect(jsonPath("$.dayChangeEur").doesNotExist())
                .andExpect(jsonPath("$.dayChangeRatio").doesNotExist())
                .andExpect(jsonPath("$.unrealizedGainEur").doesNotExist());
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
                .andExpect(jsonPath("$[0].priceCurrency").value("USD"))
                .andExpect(jsonPath("$[0].marketValueEur").doesNotExist())
                .andExpect(jsonPath("$[0].dayChangeEur").doesNotExist())
                .andExpect(jsonPath("$[0].dayChangeRatio").doesNotExist())
                .andExpect(jsonPath("$[0].unrealizedGainEur").doesNotExist())
                .andExpect(jsonPath("$[0].stale").value(false));
    }

    @Test
    void listsAnEuroHoldingWithItsValueInEuro() throws Exception {
        Holding held = new Holding(HOLDING_ID, ACCOUNT_ID, INSTRUMENT_ID, BigDecimal.TEN, new BigDecimal("100"));
        when(loadHoldings.findAll()).thenReturn(List.of(held));
        when(valueHolding.value(held)).thenReturn(Optional.of(aQuotedHolding(held, "EUR")));

        mockMvc.perform(get("/holdings").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].priceCurrency").value("EUR"))
                .andExpect(jsonPath("$[0].marketValueEur").value(1234.5))
                .andExpect(jsonPath("$[0].dayChangeEur").value(10.0))
                .andExpect(jsonPath("$[0].dayChangeRatio").exists())
                .andExpect(jsonPath("$[0].unrealizedGainEur").value(234.5));
    }

    @Test
    void listsAHoldingWithNoQuoteYetWithANullPrice() throws Exception {
        when(loadHoldings.findAll()).thenReturn(List.of(A_HOLDING));
        when(valueHolding.value(A_HOLDING)).thenReturn(Optional.of(aValuedHoldingWithoutQuote(A_HOLDING)));

        mockMvc.perform(get("/holdings").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].instrumentName").value("Apple Inc."))
                .andExpect(jsonPath("$[0].price").doesNotExist())
                .andExpect(jsonPath("$[0].marketValueEur").doesNotExist());
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
    void reportsALineOnASavingsAccountAs422() throws Exception {
        when(manageHolding.create(any(), any(), any(), any())).thenThrow(new SavingsAccountLineException());

        mockMvc.perform(post("/holdings")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A savings account holds one balance, not lines"));
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
    void buyingAnswersTheUpdatedHolding() throws Exception {
        UUID id = UUID.randomUUID();
        Holding bought = new Holding(
                id, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("540"), new BigDecimal("24.488889"));
        when(manageHolding.buy(id, new BigDecimal("40"), new BigDecimal("29.10")))
                .thenReturn(bought);

        mockMvc.perform(post("/holdings/{id}/buy", id)
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"quantity\":40,\"unitPrice\":29.10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(540))
                .andExpect(jsonPath("$.averageCost").value(24.49));
    }

    @Test
    void sellingPartAnswersTheRemainder() throws Exception {
        UUID id = UUID.randomUUID();
        Holding remaining =
                new Holding(id, UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("400"), new BigDecimal("24.12"));
        when(manageHolding.sell(id, new BigDecimal("100"))).thenReturn(Optional.of(remaining));

        mockMvc.perform(post("/holdings/{id}/sell", id)
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"quantity\":100}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(400));
    }

    @Test
    void sellingEverythingAnswersNoContent() throws Exception {
        UUID id = UUID.randomUUID();
        when(manageHolding.sell(id, new BigDecimal("500"))).thenReturn(Optional.empty());

        mockMvc.perform(post("/holdings/{id}/sell", id)
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"quantity\":500}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void sellingTooMuchIsABusinessRefusal() throws Exception {
        UUID id = UUID.randomUUID();
        when(manageHolding.sell(id, new BigDecimal("501")))
                .thenThrow(new InsufficientQuantityException(new BigDecimal("500"), new BigDecimal("501")));

        mockMvc.perform(post("/holdings/{id}/sell", id)
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"quantity\":501}"))
                .andExpect(status().isUnprocessableEntity());
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
        return aQuotedHolding(holding, "USD");
    }

    private static ValuedHolding aQuotedHolding(Holding holding, String currency) {
        Instrument instrument = new Instrument(
                INSTRUMENT_ID, "Apple Inc.", "US0378331005", "USD", AssetClass.EQUITY, PriceSource.YAHOO, "AAPL", null);
        Account account = new Account(ACCOUNT_ID, "CTO Boursorama", AccountType.CTO, "Boursorama");
        Quote quote = new Quote(
                INSTRUMENT_ID,
                LocalDate.of(2026, 8, 26),
                new BigDecimal("123.45"),
                currency,
                PriceSource.YAHOO,
                Instant.parse("2026-08-26T20:00:00Z"));
        Quote previous = new Quote(
                INSTRUMENT_ID,
                LocalDate.of(2026, 8, 25),
                new BigDecimal("122.45"),
                currency,
                PriceSource.YAHOO,
                Instant.parse("2026-08-25T20:00:00Z"));
        return new ValuedHolding(holding, instrument, account, Optional.of(quote), Optional.of(previous));
    }

    private static ValuedHolding aValuedHoldingWithoutQuote(Holding holding) {
        Instrument instrument = new Instrument(
                INSTRUMENT_ID, "Apple Inc.", "US0378331005", "USD", AssetClass.EQUITY, PriceSource.YAHOO, "AAPL", null);
        Account account = new Account(ACCOUNT_ID, "CTO Boursorama", AccountType.CTO, "Boursorama");
        return new ValuedHolding(holding, instrument, account, Optional.empty(), Optional.empty());
    }
}
