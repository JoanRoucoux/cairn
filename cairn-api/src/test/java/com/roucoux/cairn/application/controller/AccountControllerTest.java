package com.roucoux.cairn.application.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.roucoux.cairn.application.mapper.AccountRestMapper;
import com.roucoux.cairn.domain.exception.business.AccountNotEmptyException;
import com.roucoux.cairn.domain.exception.business.NegativeCashBalanceException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.exception.business.SavingsAccountLineException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.port.in.ManageAccountUseCase;
import com.roucoux.cairn.domain.port.in.SetCashBalanceUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.infrastructure.auth.WebAuthnConfig;
import com.roucoux.cairn.infrastructure.transaction.AccountDeletionTransaction;
import java.math.BigDecimal;
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
    private static final Account NORTHWIND_PEA =
            new Account(ACCOUNT_ID, "Northwind PEA", AccountType.PEA, "Northwind Bank");
    private static final String VALID_BODY = """
            {"name":"Northwind PEA","type":"PEA","institution":"Northwind Bank"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LoadAccountsPort loadAccounts;

    @MockitoBean
    private ManageAccountUseCase manageAccount;

    @MockitoBean
    private SetCashBalanceUseCase setCashBalance;

    @MockitoBean
    private AccountDeletionTransaction deletion;

    @MockitoBean
    private JdbcOperations jdbcOperations;

    @Test
    void listsEveryAccount() throws Exception {
        when(loadAccounts.findAll()).thenReturn(List.of(NORTHWIND_PEA));

        mockMvc.perform(get("/accounts").with(user("alex")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Northwind PEA"))
                .andExpect(jsonPath("$[0].type").value("PEA"));
    }

    @Test
    void createsAnAccount() throws Exception {
        when(manageAccount.create("Northwind PEA", AccountType.PEA, "Northwind Bank"))
                .thenReturn(NORTHWIND_PEA);

        mockMvc.perform(post("/accounts")
                        .with(user("alex"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.institution").value("Northwind Bank"));
    }

    @Test
    void updatingAnswersTheAccount() throws Exception {
        UUID id = UUID.randomUUID();
        when(manageAccount.update(id, "Northwind PEA", AccountType.PEA, "Northwind Bank"))
                .thenReturn(new Account(id, "Northwind PEA", AccountType.PEA, "Northwind Bank"));

        mockMvc.perform(put("/accounts/{id}", id)
                        .with(user("alex"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Northwind PEA\",\"type\":\"PEA\",\"institution\":\"Northwind Bank\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Northwind PEA"));
    }

    @Test
    void turningAnAccountHoldingSecuritiesIntoSavingsIsRefused() throws Exception {
        UUID id = UUID.randomUUID();
        when(manageAccount.update(id, "Livret A", AccountType.SAVINGS, "Woodgrove Bank"))
                .thenThrow(new SavingsAccountLineException());

        mockMvc.perform(put("/accounts/{id}", id)
                        .with(user("alex"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"Livret A\",\"type\":\"SAVINGS\",\"institution\":\"Woodgrove Bank\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("A savings account holds one balance, not lines"));
    }

    @Test
    void deletingAnswersNoContent() throws Exception {
        UUID id = UUID.randomUUID();

        mockMvc.perform(delete("/accounts/{id}", id).with(user("alex")).with(csrf()))
                .andExpect(status().isNoContent());

        verify(deletion).run(id);
    }

    @Test
    void deletingANonEmptyAccountIsRefused() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new AccountNotEmptyException(id, 8)).when(deletion).run(id);

        mockMvc.perform(delete("/accounts/{id}", id).with(user("alex")).with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("8 holding")));
    }

    @Test
    void refusesAWriteWithoutACsrfToken() throws Exception {
        mockMvc.perform(post("/accounts")
                        .with(user("alex"))
                        .contentType(APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void setsAnAccountsCashBalance() throws Exception {
        mockMvc.perform(put("/accounts/{id}/cash", ACCOUNT_ID)
                        .with(user("alex"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":732.40}"))
                .andExpect(status().isNoContent());

        verify(setCashBalance).setCashBalance(ACCOUNT_ID, new BigDecimal("732.40"));
    }

    @Test
    void answersNotFoundForAnUnknownAccount() throws Exception {
        org.mockito.Mockito.doThrow(new NotFoundException("account", ACCOUNT_ID))
                .when(setCashBalance)
                .setCashBalance(any(), any());

        mockMvc.perform(put("/accounts/{id}/cash", ACCOUNT_ID)
                        .with(user("alex"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":732.40}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersUnprocessableForANegativeAmountRefusedByTheDomain() throws Exception {
        org.mockito.Mockito.doThrow(new NegativeCashBalanceException())
                .when(setCashBalance)
                .setCashBalance(ACCOUNT_ID, new BigDecimal("-1"));

        mockMvc.perform(put("/accounts/{id}/cash", ACCOUNT_ID)
                        .with(user("alex"))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"amount\":-1}"))
                .andExpect(status().isUnprocessableContent());
    }
}
