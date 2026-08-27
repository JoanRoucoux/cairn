package com.roucoux.cairn.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.AccountRestMapper;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
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
@WebMvcTest(AccountController.class)
@Import({WebAuthnConfig.class, AccountRestMapper.class})
class AccountControllerTest {

    private static final UUID ACCOUNT_ID = UUID.randomUUID();
    private static final Account BOURSORAMA_PEA =
            new Account(ACCOUNT_ID, "PEA Boursorama", AccountType.PEA, "Boursorama");
    private static final String VALID_BODY = """
            {"name":"PEA Boursorama","type":"PEA","institution":"Boursorama"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoadAccountsPort loadAccounts;

    @MockitoBean
    private SaveAccountPort saveAccount;

    @MockitoBean
    private JdbcOperations jdbcOperations;

    @Test
    void listsEveryAccount() throws Exception {
        when(loadAccounts.findAll()).thenReturn(List.of(BOURSORAMA_PEA));

        mockMvc.perform(get("/accounts").with(user("joan")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("PEA Boursorama"))
                .andExpect(jsonPath("$[0].type").value("PEA"));
    }

    @Test
    void createsAnAccount() throws Exception {
        when(saveAccount.save(any())).thenReturn(BOURSORAMA_PEA);

        mockMvc.perform(post("/accounts")
                        .with(user("joan"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institution").value("Boursorama"));
    }

    @Test
    void refusesAWriteWithoutACsrfToken() throws Exception {
        mockMvc.perform(post("/accounts")
                        .with(user("joan"))
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }
}
