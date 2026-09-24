package com.roucoux.cairn.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.HistoryRestMapper;
import com.roucoux.cairn.domain.model.HistoryMode;
import com.roucoux.cairn.domain.model.HistoryPoint;
import com.roucoux.cairn.domain.model.IntradayPoint;
import com.roucoux.cairn.domain.port.in.GetHistoryUseCase;
import com.roucoux.cairn.domain.port.in.GetIntradayHistoryUseCase;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = {"app.security.password=test-password", "app.zone=Europe/Paris"})
@WebMvcTest(HistoryController.class)
@Import({WebAuthnConfig.class, HistoryRestMapper.class})
class HistoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetHistoryUseCase getHistory;

    @MockitoBean
    private GetIntradayHistoryUseCase getIntradayHistory;

    @MockitoBean
    private JdbcOperations jdbcOperations;

    @Test
    void returnsTheSeriesForTheRequestedRange() throws Exception {
        when(getHistory.history(eq(HistoryMode.CONSTANT_MIX), any(), any()))
                .thenReturn(List.of(new HistoryPoint(LocalDate.of(2026, 8, 21), new BigDecimal("278146.45"))));

        mockMvc.perform(get("/history")
                        .with(user("joan"))
                        .param("mode", "constant-mix")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].totalEur").value(278146.45))
                .andExpect(jsonPath("$.reconstructed").value(true));
    }

    @Test
    void marksASnapshotSeriesAsMeasuredNotReconstructed() throws Exception {
        when(getHistory.history(eq(HistoryMode.SNAPSHOT), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/history")
                        .with(user("joan"))
                        .param("mode", "snapshot")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-21"))
                .andExpect(jsonPath("$.reconstructed").value(false));
    }

    @Test
    void rejectsARangeWhoseEndPrecedesItsStart() throws Exception {
        mockMvc.perform(get("/history")
                        .with(user("joan"))
                        .param("mode", "constant-mix")
                        .param("from", "2026-08-21")
                        .param("to", "2026-08-01"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesAnUnauthenticatedCall() throws Exception {
        mockMvc.perform(get("/history")
                        .param("mode", "constant-mix")
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-21"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheDaysIntradayPoints() throws Exception {
        when(getIntradayHistory.intraday(eq(LocalDate.of(2026, 9, 24)), any()))
                .thenReturn(
                        List.of(new IntradayPoint(Instant.parse("2026-09-24T07:00:00Z"), new BigDecimal("108000.00"))));

        mockMvc.perform(get("/history/intraday").with(user("joan")).param("date", "2026-09-24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points[0].totalEur").value(108000.00))
                .andExpect(jsonPath("$.points[0].at").value("2026-09-24T07:00:00Z"));
    }

    @Test
    void rejectsAMissingDate() throws Exception {
        mockMvc.perform(get("/history/intraday").with(user("joan"))).andExpect(status().isBadRequest());
    }
}
