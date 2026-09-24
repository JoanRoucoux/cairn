package com.roucoux.cairn.application.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.PerformanceRestMapper;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.EnvelopePerformance;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.Performance;
import com.roucoux.cairn.domain.model.PerformanceRange;
import com.roucoux.cairn.domain.port.in.GetPerformanceUseCase;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = "app.security.password=test-password")
@WebMvcTest(PerformanceController.class)
@Import({WebAuthnConfig.class, PerformanceRestMapper.class})
class PerformanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetPerformanceUseCase getPerformance;

    @MockitoBean
    private JdbcOperations jdbcOperations;

    @Test
    void returnsThePerformanceForTheRequestedRange() throws Exception {
        when(getPerformance.performance(eq(PerformanceRange.D1))).thenReturn(aPerformance());

        mockMvc.perform(get("/portfolio/performance").with(user("joan")).param("range", "1d"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.range").value("1d"))
                .andExpect(jsonPath("$.total.valueEur").value(650.00))
                .andExpect(jsonPath("$.byEnvelope[0].accountType").value("CTO"));
    }

    @Test
    void rejectsAnUnknownRange() throws Exception {
        mockMvc.perform(get("/portfolio/performance").with(user("joan")).param("range", "3d"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAnUnauthenticatedCall() throws Exception {
        mockMvc.perform(get("/portfolio/performance").param("range", "1d")).andExpect(status().isUnauthorized());
    }

    private static Performance aPerformance() {
        return new Performance(
                PerformanceRange.D1,
                LocalDate.of(2026, 9, 23),
                LocalDate.of(2026, 9, 24),
                false,
                Optional.empty(),
                Money.eur(new BigDecimal("650")),
                Money.eur(new BigDecimal("50")),
                Optional.of(new BigDecimal("0.083333")),
                List.of(new EnvelopePerformance(
                        AccountType.CTO,
                        Money.eur(new BigDecimal("650")),
                        BigDecimal.ONE,
                        Money.eur(new BigDecimal("50")),
                        Optional.of(new BigDecimal("0.083333")))));
    }
}
