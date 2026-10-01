package com.roucoux.cairn.application.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.roucoux.cairn.application.csv.HoldingCsvWriter;
import com.roucoux.cairn.application.csv.PortfolioCsvReader;
import com.roucoux.cairn.application.mapper.AccountRestMapper;
import com.roucoux.cairn.application.mapper.AllocationRestMapper;
import com.roucoux.cairn.application.mapper.HoldingRestMapper;
import com.roucoux.cairn.application.mapper.PortfolioRestMapper;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.GetPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.service.AllocationService;
import com.roucoux.cairn.domain.service.PortfolioService;
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
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@TestPropertySource(properties = "app.security.password=test-password")
@WebMvcTest(PortfolioController.class)
@Import({
    WebAuthnConfig.class,
    PortfolioRestMapper.class,
    AllocationRestMapper.class,
    AccountRestMapper.class,
    HoldingRestMapper.class,
    HoldingCsvWriter.class,
    PortfolioCsvReader.class,
    PortfolioAllocationAgreementTest.RealDomain.class
})
class PortfolioAllocationAgreementTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-26T20:00:00Z"), ZoneOffset.UTC);
    private static final Account PEA = new Account(UUID.randomUUID(), "Saxo", AccountType.PEA, "Saxo Bank");
    private static final Account SAVINGS = new Account(UUID.randomUUID(), "Fortuneo", AccountType.SAVINGS, "Fortuneo");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper json;

    @MockitoBean
    private LoadHoldingsPort loadHoldings;

    @MockitoBean
    private ValueHoldingUseCase valueHolding;

    @MockitoBean
    private PortfolioImportTransaction importPortfolio;

    @MockitoBean
    private JdbcOperations jdbcOperations;

    @TestConfiguration
    static class RealDomain {
        @Bean
        Clock clock() {
            return CLOCK;
        }

        @Bean
        PortfolioService portfolioService(LoadHoldingsPort loadHoldings, ValueHoldingUseCase valueHolding) {
            return new PortfolioService(loadHoldings, valueHolding, CLOCK);
        }

        @Bean
        AllocationService allocationService(GetPortfolioUseCase getPortfolio) {
            return new AllocationService(getPortfolio);
        }
    }

    @Test
    void bothAllocationEndpointsGiveTheNumbersOfThePortfolioBreakdowns() throws Exception {
        List<Holding> holdings = List.of(
                holding(PEA, AssetClass.ETF, "7", "33.333", true),
                holding(SAVINGS, AssetClass.ETF, "3", "12.5", true),
                holding(SAVINGS, AssetClass.CASH, "1500.10", "1", true),
                holding(PEA, AssetClass.EQUITY, "4", "0", false));
        when(loadHoldings.findAll()).thenReturn(holdings);

        JsonNode portfolio = read("/portfolio");
        JsonNode classes = read("/portfolio/allocation/classes");
        JsonNode accounts = read("/portfolio/allocation/accounts");

        assertThat(classes.get("totalEur")).isEqualTo(portfolio.get("totalEur"));
        assertThat(accounts.get("totalEur")).isEqualTo(portfolio.get("totalEur"));
        assertThat(classes.get("items")).hasSameSizeAs(portfolio.get("byAssetClass"));
        for (int i = 0; i < classes.get("items").size(); i++) {
            JsonNode item = classes.get("items").get(i);
            JsonNode expected = portfolio.get("byAssetClass").get(i);
            assertThat(item.get("assetClass")).isEqualTo(expected.get("label"));
            assertThat(item.get("valueEur")).isEqualTo(expected.get("valueEur"));
            assertThat(item.get("share")).isEqualTo(expected.get("share"));
        }
        assertThat(accounts.get("items")).hasSameSizeAs(portfolio.get("byAccount"));
        for (int i = 0; i < accounts.get("items").size(); i++) {
            JsonNode item = accounts.get("items").get(i);
            JsonNode expected = portfolio.get("byAccount").get(i);
            assertThat(item.get("account").get("name")).isEqualTo(expected.get("label"));
            assertThat(item.get("valueEur")).isEqualTo(expected.get("valueEur"));
            assertThat(item.get("share")).isEqualTo(expected.get("share"));
        }
    }

    private JsonNode read(String path) throws Exception {
        return json.readTree(mockMvc.perform(get(path).with(user("joan")))
                .andReturn()
                .getResponse()
                .getContentAsString());
    }

    private Holding holding(Account account, AssetClass assetClass, String quantity, String price, boolean quoted) {
        PriceSource source = assetClass == AssetClass.CASH ? PriceSource.MANUAL : PriceSource.YAHOO;
        Instrument instrument = new Instrument(
                UUID.randomUUID(),
                "Test",
                null,
                "EUR",
                assetClass,
                source,
                source == PriceSource.MANUAL ? null : "TEST.PA",
                null);
        Holding holding = new Holding(UUID.randomUUID(), account.id(), instrument.id(), new BigDecimal(quantity), null);
        Optional<Quote> quote = quoted
                ? Optional.of(new Quote(
                        instrument.id(),
                        LocalDate.of(2026, 8, 26),
                        new BigDecimal(price),
                        "EUR",
                        source,
                        CLOCK.instant()))
                : Optional.empty();
        when(valueHolding.value(holding))
                .thenReturn(Optional.of(new ValuedHolding(holding, instrument, account, quote, Optional.empty())));
        return holding;
    }
}
