package com.roucoux.cairn.infrastructure.transaction;

import com.roucoux.cairn.domain.port.in.ManageAccountUseCase;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AccountDeletionTransaction {

    private final ManageAccountUseCase manageAccount;

    AccountDeletionTransaction(ManageAccountUseCase manageAccount) {
        this.manageAccount = manageAccount;
    }

    @Transactional
    public void run(UUID id) {
        manageAccount.delete(id);
    }
}
