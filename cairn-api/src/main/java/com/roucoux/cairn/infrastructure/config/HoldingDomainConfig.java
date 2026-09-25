package com.roucoux.cairn.infrastructure.config;

import com.roucoux.cairn.domain.port.in.SetCashBalanceUseCase;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import com.roucoux.cairn.domain.service.CashBalanceService;
import com.roucoux.cairn.domain.service.HoldingService;
import com.roucoux.cairn.domain.service.HoldingValuationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class HoldingDomainConfig {

    @Bean
    HoldingService holdingService(
            LoadHoldingsPort loadHoldings,
            SaveHoldingPort saveHolding,
            DeleteHoldingPort deleteHolding,
            LoadAccountsPort loadAccounts,
            LoadInstrumentsPort loadInstruments) {
        return new HoldingService(loadHoldings, saveHolding, deleteHolding, loadAccounts, loadInstruments);
    }

    @Bean
    SetCashBalanceUseCase setCashBalanceUseCase(
            LoadAccountsPort loadAccounts,
            LoadInstrumentsPort loadInstruments,
            SaveInstrumentPort saveInstrument,
            LoadHoldingsPort loadHoldings,
            SaveHoldingPort saveHolding,
            DeleteHoldingPort deleteHolding) {
        return new CashBalanceService(
                loadAccounts, loadInstruments, saveInstrument, loadHoldings, saveHolding, deleteHolding);
    }

    @Bean
    ValueHoldingUseCase valueHoldingUseCase(
            LoadInstrumentsPort loadInstruments,
            LoadAccountsPort loadAccounts,
            LoadQuotesPort loadQuotes,
            Clock clock) {
        return new HoldingValuationService(loadInstruments, loadAccounts, loadQuotes, clock);
    }
}
