package com.roucoux.cairn.application.controller;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.InstrumentRestMapper;
import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
    private static final UUID LIVRET_A_ID = UUID.randomUUID();
    private static final Instrument SP500 = new Instrument(
            INSTRUMENT_ID,
            "Amundi ETF PEA S&P 500",
            "FR0011550185",
            "EUR",
            AssetClass.ETF,
            PriceSource.YAHOO,
            "ESE.PA",
            "ETF sur le S&P 500, les 500 plus grandes capitalisations americaines");
    private static final Instrument LIVRET_A = new Instrument(
            LIVRET_A_ID, "Livret A", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, "Livret d'epargne");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoadInstrumentsPort loadInstruments;

    @MockitoBean
    private SaveInstrumentPort saveInstrument;

    @MockitoBean
    private ResolveInstrumentUseCase resolveInstrument;

    @BeforeEach
    void stubDefaults() {
        when(loadInstruments.findById(INSTRUMENT_ID)).thenReturn(Optional.of(SP500));
        when(saveInstrument.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @MockitoBean
    private JdbcOperations jdbcOperations;

    @Test
    void listsEveryInstrument() throws Exception {
        when(loadInstruments.findAll()).thenReturn(List.of(SP500));

        mockMvc.perform(get("/instruments").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isin").value("FR0011550185"));
    }

    @Test
    void createsAnInstrument() throws Exception {
        mockMvc.perform(post("/instruments")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"name":"Amundi ETF PEA S&P 500","isin":"FR0011550185","currency":"EUR",
                                 "assetClass":"ETF","priceSource":"YAHOO","sourceRef":"ESE.PA"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceRef").value("ESE.PA"));
    }

    @Test
    void proposesCandidateSourcesForAnIsin() throws Exception {
        when(resolveInstrument.resolve("FR0013296084"))
                .thenReturn(List.of(new InstrumentCandidate(
                        "Valmy Gestion Diversifiee",
                        PriceSource.YAHOO,
                        "0P0001D8GQ.F",
                        AssetClass.FUND,
                        new BigDecimal("131.57"))));

        mockMvc.perform(post("/instruments/resolve")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"query\":\"FR0013296084\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sourceRef").value("0P0001D8GQ.F"))
                .andExpect(jsonPath("$[0].probePrice").value(131.57));
    }

    @Test
    void reportsAnUnresolvableIsinAs422() throws Exception {
        when(resolveInstrument.resolve("XX0000000000")).thenThrow(new UnknownInstrumentException("XX0000000000"));

        mockMvc.perform(post("/instruments/resolve")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"query\":\"XX0000000000\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void returnsTheDescriptionAndTheLinkToTheProviderSheet() throws Exception {
        when(loadInstruments.findById(INSTRUMENT_ID)).thenReturn(Optional.of(SP500));

        mockMvc.perform(get("/instruments/{id}", INSTRUMENT_ID).with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(startsWith("ETF sur le S&P 500")))
                .andExpect(jsonPath("$.externalUrl").value("https://finance.yahoo.com/quote/ESE.PA"));
    }

    @Test
    void reportsNoExternalUrlForAManuallyPricedInstrument() throws Exception {
        when(loadInstruments.findById(LIVRET_A_ID)).thenReturn(Optional.of(LIVRET_A));

        mockMvc.perform(get("/instruments/{id}", LIVRET_A_ID).with(user("joan")))
                .andExpect(jsonPath("$.externalUrl").doesNotExist());
    }

    @Test
    void updatesOnlyTheDescription() throws Exception {
        mockMvc.perform(patch("/instruments/{id}", INSTRUMENT_ID)
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\":\"Les 500 plus grandes capitalisations americaines\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void reportsAnUnknownInstrumentAs404() throws Exception {
        when(loadInstruments.findById(LIVRET_A_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/instruments/{id}", LIVRET_A_ID).with(user("joan")))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesAWriteWithoutACsrfToken() throws Exception {
        mockMvc.perform(patch("/instruments/{id}", INSTRUMENT_ID)
                        .with(user("joan"))
                        .contentType(APPLICATION_JSON)
                        .content("{\"description\":\"whatever\"}"))
                .andExpect(status().isForbidden());
    }
}
