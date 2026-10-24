package com.roucoux.cairn.application.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.TelegramLinkRestMapper;
import com.roucoux.cairn.domain.exception.business.TelegramNotConfiguredException;
import com.roucoux.cairn.domain.exception.technical.NotificationDeliveryException;
import com.roucoux.cairn.domain.model.LinkInvitation;
import com.roucoux.cairn.domain.model.LinkedChat;
import com.roucoux.cairn.domain.model.TelegramLinkStatus;
import com.roucoux.cairn.domain.model.TelegramLinkView;
import com.roucoux.cairn.domain.port.in.LinkTelegramUseCase;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@TestPropertySource(properties = {"app.security.password=test-password", "app.security.username=alex"})
@WebMvcTest(TelegramLinkController.class)
@Import({WebAuthnConfig.class, TelegramLinkRestMapper.class, TestClockConfig.class})
class TelegramLinkControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    LinkTelegramUseCase telegram;

    @MockitoBean
    PublicKeyCredentialUserEntityRepository userEntities;

    @MockitoBean
    UserCredentialRepository credentials;

    @MockitoBean
    WebAuthnRelyingPartyOperations relyingParty;

    @Test
    void statusAnswersLinkedWithLabelAndDate() throws Exception {
        when(telegram.status())
                .thenReturn(new TelegramLinkView(
                        TelegramLinkStatus.LINKED, new LinkedChat(42L, "Alex", Instant.parse("2026-10-24T08:00:00Z"))));

        mockMvc.perform(get("/session/telegram").with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("linked"))
                .andExpect(jsonPath("$.chatLabel").value("Alex"))
                .andExpect(jsonPath("$.linkedAt").value("2026-10-24T08:00:00Z"));
    }

    @Test
    void startAnswers201WithTheTmeUrl() throws Exception {
        when(telegram.start())
                .thenReturn(new LinkInvitation("cairn_test_bot", "tok123", Instant.parse("2026-10-24T08:10:00Z")));

        mockMvc.perform(post("/session/telegram/link").with(user("alex")).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").value("https://t.me/cairn_test_bot?start=tok123"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-24T08:10:00Z"));
    }

    @Test
    void startWhenUnconfiguredIs422() throws Exception {
        when(telegram.start()).thenThrow(new TelegramNotConfiguredException());

        mockMvc.perform(post("/session/telegram/link").with(user("alex")).with(csrf()))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    void checkWhenTelegramFailsIs502WithoutUpstreamDetail() throws Exception {
        when(telegram.check()).thenThrow(new NotificationDeliveryException("Telegram: 409 secret upstream text"));

        mockMvc.perform(post("/session/telegram/link/check").with(user("alex")).with(csrf()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("Telegram"));
    }

    @Test
    void unlinkAnswers204() throws Exception {
        mockMvc.perform(delete("/session/telegram/link").with(user("alex")).with(csrf()))
                .andExpect(status().isNoContent());

        verify(telegram).unlink();
    }

    @Test
    void writesWithoutCsrfAre403() throws Exception {
        mockMvc.perform(post("/session/telegram/link").with(user("alex"))).andExpect(status().isForbidden());
        mockMvc.perform(post("/session/telegram/link/check").with(user("alex"))).andExpect(status().isForbidden());
        mockMvc.perform(delete("/session/telegram/link").with(user("alex"))).andExpect(status().isForbidden());
    }

    @Test
    void readWithoutSessionIs401() throws Exception {
        mockMvc.perform(get("/session/telegram")).andExpect(status().isUnauthorized());
    }
}
