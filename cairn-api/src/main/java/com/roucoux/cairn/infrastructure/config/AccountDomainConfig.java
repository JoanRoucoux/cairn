package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.in.ManageAccountUseCase;
import com.roucoux.cairn.domain.port.out.DeleteAccountPort;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.DeleteInstrumentPort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import com.roucoux.cairn.domain.service.AccountService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AccountDomainConfig {

    @Bean
    ManageAccountUseCase manageAccountUseCase(
            LoadAccountsPort loadAccounts,
            SaveAccountPort saveAccount,
            DeleteAccountPort deleteAccount,
            LoadHoldingsPort loadHoldings,
            DeleteHoldingPort deleteHolding,
            LoadInstrumentsPort loadInstruments,
            DeleteInstrumentPort deleteInstrument) {
        return new AccountService(
                loadAccounts,
                saveAccount,
                deleteAccount,
                loadHoldings,
                deleteHolding,
                loadInstruments,
                deleteInstrument);
    }
}
