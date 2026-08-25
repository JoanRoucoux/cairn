package com.roucoux.cairn.application.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.PositionRestMapper;
import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.Position;
import com.roucoux.cairn.domain.port.in.CreatePositionUseCase;
import com.roucoux.cairn.domain.port.in.GetPositionUseCase;
import com.roucoux.cairn.infrastructure.config.SecurityConfig;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PositionController.class)
@Import({SecurityConfig.class, PositionRestMapper.class})
class PositionControllerTest {

    private static final String CREATE_BODY = "{\"isin\":\"US0378331005\",\"quantity\":10}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreatePositionUseCase createPosition;

    @MockitoBean
    private GetPositionUseCase getPosition;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsUnauthenticatedRequests() throws Exception {
        mockMvc.perform(get("/position/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void createsAPosition() throws Exception {
        Position position = Position.open("US0378331005", new BigDecimal("10"), new BigDecimal("123.45"));
        when(createPosition.create("US0378331005", new BigDecimal("10"))).thenReturn(position);

        mockMvc.perform(post("/position")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(position.id().toString()))
                .andExpect(jsonPath("$.price").value(123.45))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void mapsABusinessErrorToAnUnprocessableEntityProblem() throws Exception {
        when(createPosition.create(any(), any())).thenThrow(new UnknownInstrumentException("US0378331005"));

        mockMvc.perform(post("/position")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(containsString("Unknown instrument")));
    }

    @Test
    void mapsATechnicalFailureToABadGatewayProblem() throws Exception {
        when(createPosition.create(any(), any()))
                .thenThrow(new MarketDataUnavailableException("US0378331005", new RuntimeException("boom")));

        mockMvc.perform(post("/position")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BODY))
                .andExpect(status().isBadGateway());
    }

    @Test
    void rejectsAnInvalidBody() throws Exception {
        mockMvc.perform(post("/position")
                        .with(jwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"quantity\":10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns404ForAnUnknownPosition() throws Exception {
        when(getPosition.byId(any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/position/{id}", UUID.randomUUID()).with(jwt())).andExpect(status().isNotFound());
    }
}
