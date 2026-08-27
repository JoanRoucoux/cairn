package com.roucoux.cairn.application.mapper;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.generated.model.AccountResponse;
import org.springframework.stereotype.Component;

/** Maps a plain {@link Account} to the generated DTO. */
@Component
public class AccountRestMapper {

    public AccountResponse toResponse(Account account) {
        AccountResponse response = new AccountResponse();
        response.setId(account.id());
        response.setName(account.name());
        response.setType(AccountResponse.TypeEnum.valueOf(account.type().name()));
        response.setInstitution(account.institution());
        return response;
    }
}
