package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.generated.model.AccountResponse;
import com.roucoux.cairn.generated.model.AccountType;
import org.springframework.stereotype.Component;

@Component
public class AccountRestMapper {

    public AccountResponse toResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.id());
        response.setName(account.name());
        response.setType(AccountType.valueOf(account.type().name()));
        response.setInstitution(account.institution());
        return response;
    }
}
