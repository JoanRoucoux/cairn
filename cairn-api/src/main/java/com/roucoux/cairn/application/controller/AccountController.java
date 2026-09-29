package com.roucoux.cairn.application.controller;

import com.roucoux.cairn.application.mapper.AccountRestMapper;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.port.in.ManageAccountUseCase;
import com.roucoux.cairn.domain.port.in.SetCashBalanceUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.generated.api.AccountApi;
import com.roucoux.cairn.generated.model.AccountResponse;
import com.roucoux.cairn.generated.model.CreateAccountRequest;
import com.roucoux.cairn.generated.model.SetCashBalanceRequest;
import com.roucoux.cairn.generated.model.UpdateAccountRequest;
import com.roucoux.cairn.infrastructure.transaction.AccountDeletionTransaction;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AccountController implements AccountApi {

    private final LoadAccountsPort loadAccounts;
    private final ManageAccountUseCase manageAccount;
    private final SetCashBalanceUseCase setCashBalance;
    private final AccountDeletionTransaction accountDeletion;
    private final AccountRestMapper mapper;

    AccountController(
            LoadAccountsPort loadAccounts,
            ManageAccountUseCase manageAccount,
            SetCashBalanceUseCase setCashBalance,
            AccountDeletionTransaction accountDeletion,
            AccountRestMapper mapper) {
        this.loadAccounts = loadAccounts;
        this.manageAccount = manageAccount;
        this.setCashBalance = setCashBalance;
        this.accountDeletion = accountDeletion;
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
        Account saved = manageAccount.create(
                createAccountRequest.getName(),
                AccountType.valueOf(createAccountRequest.getType().name()),
                createAccountRequest.getInstitution());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toResponse(saved));
    }

    @Override
    public ResponseEntity<AccountResponse> updateAccount(UUID id, UpdateAccountRequest updateAccountRequest) {
        Account updated = manageAccount.update(
                id,
                updateAccountRequest.getName(),
                AccountType.valueOf(updateAccountRequest.getType().name()),
                updateAccountRequest.getInstitution());
        return ResponseEntity.ok(mapper.toResponse(updated));
    }

    @Override
    public ResponseEntity<Void> deleteAccount(UUID id) {
        accountDeletion.run(id);
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> setCashBalance(UUID id, SetCashBalanceRequest setCashBalanceRequest) {
        setCashBalance.setCashBalance(id, setCashBalanceRequest.getAmount());
        return ResponseEntity.noContent().build();
    }
}
