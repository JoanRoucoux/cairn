package com.roucoux.cairn.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.HoldingRestMapper;
import com.roucoux.cairn.domain.exception.business.DuplicateHoldingException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.port.in.ManageHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(HoldingController.class)
@Import({WebAuthnConfig.class, HoldingRestMapper.class})
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
    private JdbcOperations jdbcOperations;

    @Test
    void createsAHolding() throws Exception {
        when(manageHolding.create(any(), any(), any(), any())).thenReturn(A_HOLDING);

        mockMvc.perform(post("/holdings")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    void reportsAnAbsentAverageCostAsNullNotZero() throws Exception {
        Holding holdingWithoutCostBasis = new Holding(HOLDING_ID, ACCOUNT_ID, INSTRUMENT_ID, new BigDecimal("4"), null);
        when(manageHolding.create(any(), any(), any(), any())).thenReturn(holdingWithoutCostBasis);

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
    void refusesAWriteWithoutACsrfToken() throws Exception {
        mockMvc.perform(post("/holdings")
                        .with(user("joan"))
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }
}
