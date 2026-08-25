package com.roucoux.cairn.application.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.QuoteRestMapper;
import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.GetQuoteUseCase;
import com.roucoux.cairn.infrastructure.config.SecurityConfig;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(QuoteController.class)
@Import({SecurityConfig.class, QuoteRestMapper.class})
class QuoteControllerTest {

    private static final String ISIN = "US0378331005";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetQuoteUseCase getQuote;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/quote/{isin}", ISIN)).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheCurrentQuote() throws Exception {
        when(getQuote.byIsin(ISIN)).thenReturn(new Quote(ISIN, new BigDecimal("123.45")));

        mockMvc.perform(get("/quote/{isin}", ISIN).with(jwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isin").value(ISIN))
                .andExpect(jsonPath("$.price").value(123.45));
    }

    @Test
    void mapsABusinessErrorToAnUnprocessableEntityProblem() throws Exception {
        when(getQuote.byIsin(any())).thenThrow(new UnknownInstrumentException(ISIN));

        mockMvc.perform(get("/quote/{isin}", ISIN).with(jwt()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Unknown instrument")));
    }

    @Test
    void mapsATechnicalFailureToABadGatewayProblem() throws Exception {
        when(getQuote.byIsin(any())).thenThrow(new MarketDataUnavailableException(ISIN, new RuntimeException("boom")));

        mockMvc.perform(get("/quote/{isin}", ISIN).with(jwt())).andExpect(status().isBadGateway());
    }
}
