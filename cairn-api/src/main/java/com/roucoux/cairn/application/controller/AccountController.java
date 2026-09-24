package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.AccountRestMapper;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.port.in.SetCashBalanceUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import com.roucoux.cairn.generated.api.AccountApi;
import com.roucoux.cairn.generated.model.AccountResponse;
import com.roucoux.cairn.generated.model.CreateAccountRequest;
import com.roucoux.cairn.generated.model.SetCashBalanceRequest;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Inbound adapter: implements the generated contract and delegates to the outbound ports. */
@RestController
class AccountController implements AccountApi {

    private final LoadAccountsPort loadAccounts;
    private final SaveAccountPort saveAccount;
    private final SetCashBalanceUseCase setCashBalance;
    private final AccountRestMapper mapper;

    AccountController(
            LoadAccountsPort loadAccounts,
            SaveAccountPort saveAccount,
            SetCashBalanceUseCase setCashBalance,
            AccountRestMapper mapper) {
        this.loadAccounts = loadAccounts;
        this.saveAccount = saveAccount;
        this.setCashBalance = setCashBalance;
        this.mapper = mapper;
    }

    @Override
    public ResponseEntity<List<AccountResponse>> listAccounts() {
        List<AccountResponse> accounts =
                loadAccounts.findAll().stream().map(mapper::toResponse).toList();
        return ResponseEntity.ok(accounts);
    }

    @Override
    public ResponseEntity<AccountResponse> createAccount(CreateAccountRequest createAccountRequest) {
        Account account = new Account(
                UUID.randomUUID(),
                createAccountRequest.getName(),
                AccountType.valueOf(createAccountRequest.getType().name()),
                createAccountRequest.getInstitution());
        Account saved = saveAccount.save(account);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(saved));
    }

    @Override
    public ResponseEntity<Void> setCashBalance(UUID id, SetCashBalanceRequest setCashBalanceRequest) {
        setCashBalance.setCashBalance(id, setCashBalanceRequest.getAmount());
        return ResponseEntity.noContent().build();
    }
}
