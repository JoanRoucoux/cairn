package com.roucoux.cairn.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.QuoteRestMapper;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.RecordManualQuoteUseCase;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import java.math.BigDecimal;
import java.time.Instant;
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
@WebMvcTest(QuoteController.class)
@Import({WebAuthnConfig.class, QuoteRestMapper.class})
class QuoteControllerTest {

    private static final UUID INSTRUMENT_ID = UUID.randomUUID();
    private static final Quote A_QUOTE = new Quote(
            INSTRUMENT_ID,
            LocalDate.of(2026, 8, 21),
            new BigDecimal("686.31"),
            "EUR",
            PriceSource.YAHOO,
            Instant.now());

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoadQuotesPort loadQuotes;

    @MockitoBean
    private RecordManualQuoteUseCase recordManualQuote;

    @MockitoBean
    private JdbcOperations jdbcOperations;

    @Test
    void recordsAManualNetAssetValue() throws Exception {
        when(recordManualQuote.record(any(), any(), any())).thenReturn(A_QUOTE);

        mockMvc.perform(post("/instruments/{id}/quotes", INSTRUMENT_ID)
                        .with(user("alex"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"asOf\":\"2026-08-20\",\"price\":57.48}"))
                .andExpect(status().isCreated());

        verify(recordManualQuote).record(eq(INSTRUMENT_ID), eq(LocalDate.of(2026, 8, 20)), any());
    }

    @Test
    void readsTheQuoteHistoryOfAnInstrument() throws Exception {
        when(loadQuotes.findBetween(eq(INSTRUMENT_ID), any(), any())).thenReturn(List.of(A_QUOTE));

        mockMvc.perform(get("/instruments/{id}/quotes", INSTRUMENT_ID)
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-21")
                        .with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].price").value(686.31));
    }

    @Test
    void refusesAWriteWithoutACsrfToken() throws Exception {
        mockMvc.perform(post("/instruments/{id}/quotes", INSTRUMENT_ID)
                        .with(user("alex"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"asOf\":\"2026-08-20\",\"price\":57.48}"))
                .andExpect(status().isForbidden());
    }
}
