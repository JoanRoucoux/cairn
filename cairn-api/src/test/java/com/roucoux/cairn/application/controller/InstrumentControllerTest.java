package com.roucoux.cairn.application.controller;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.InstrumentRestMapper;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.SearchInstrumentsUseCase;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "app.security.password=test-password")
@WebMvcTest(InstrumentController.class)
@Import({WebAuthnConfig.class, InstrumentRestMapper.class})
class InstrumentControllerTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchInstrumentsUseCase searchInstruments;

    @MockitoBean
    private JdbcOperations jdbcOperations;

    @Test
    void searchesOneSourceAndReturnsItsCandidates() throws Exception {
        when(searchInstruments.search(PriceSource.YAHOO, "FR0000000010"))
                .thenReturn(List.of(new InstrumentCandidate(
                        "Fonds Exemple Diversifie",
                        PriceSource.YAHOO,
                        "0P0000000A.F",
                        AssetClass.FUND,
                        "Frankfurt",
                        "FR0000000010",
                        "0P0000000A.F",
                        new BigDecimal("131.57"),
                        "USD",
                        LocalDate.of(2026, 9, 24),
                        INSTRUMENT_ID)));

        mockMvc.perform(get("/instruments/search")
                        .param("source", "YAHOO")
                        .param("query", "FR0000000010")
                        .with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceRef").value("0P0000000A.F"))
                .andExpect(jsonPath("$[0].exchange").value("Frankfurt"))
                .andExpect(jsonPath("$[0].isin").value("FR0000000010"))
                .andExpect(jsonPath("$[0].symbol").value("0P0000000A.F"))
                .andExpect(jsonPath("$[0].probePrice").value(131.57))
                .andExpect(jsonPath("$[0].currency").value("USD"))
                .andExpect(jsonPath("$[0].probeAsOf").value("2026-09-24"))
                .andExpect(jsonPath("$[0].trackedInstrumentId").value(INSTRUMENT_ID.toString()));
    }

    @Test
    void answersAnEmptyListWhenTheSourceKnowsNothing() throws Exception {
        when(searchInstruments.search(PriceSource.COINGECKO, "zzzz")).thenReturn(List.of());

        mockMvc.perform(get("/instruments/search")
                        .param("source", "COINGECKO")
                        .param("query", "zzzz")
                        .with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void reportsASourceThatIsDownAs502() throws Exception {
        when(searchInstruments.search(PriceSource.AMUNDI, "FR0000000010"))
                .thenThrow(new MarketDataUnavailableException("Amundi call failed"));

        mockMvc.perform(get("/instruments/search")
                        .param("source", "AMUNDI")
                        .param("query", "FR0000000010")
                        .with(user("alex")))
                .andExpect(status().isBadGateway());
    }

    @Test
    void refusesAQueryOutsideTwoToSixtyFourCharacters() throws Exception {
        mockMvc.perform(get("/instruments/search")
                        .param("source", "YAHOO")
                        .param("query", "a")
                        .with(user("alex")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/instruments/search")
                        .param("source", "YAHOO")
                        .param("query", "a".repeat(65))
                        .with(user("alex")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesASourceThatCannotBeSearched() throws Exception {
        mockMvc.perform(get("/instruments/search")
                        .param("source", "SG_SIRIUS")
                        .param("query", "QS0000000010")
                        .with(user("alex")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/instruments/search").param("query", "apple").with(user("alex")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresASessionToSearch() throws Exception {
        mockMvc.perform(get("/instruments/search").param("source", "YAHOO").param("query", "apple"))
                .andExpect(status().isUnauthorized());
    }
}
