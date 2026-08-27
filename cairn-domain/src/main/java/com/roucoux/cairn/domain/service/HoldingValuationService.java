package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import java.util.Optional;

public class HoldingValuationService implements ValueHoldingUseCase {

    private final LoadInstrumentsPort loadInstruments;
    private final LoadAccountsPort loadAccounts;
    private final LoadQuotesPort loadQuotes;

    public HoldingValuationService(
            LoadInstrumentsPort loadInstruments, LoadAccountsPort loadAccounts, LoadQuotesPort loadQuotes) {
        this.loadInstruments = loadInstruments;
        this.loadAccounts = loadAccounts;
        this.loadQuotes = loadQuotes;
    }

    @Override
    public Optional<ValuedHolding> value(Holding holding) {
        Optional<Instrument> instrument = loadInstruments.findById(holding.instrumentId());
        Optional<Account> account = loadAccounts.findById(holding.accountId());
        if (instrument.isEmpty() || account.isEmpty()) {
            return Optional.empty();
        }
        return loadQuotes
                .findLatest(holding.instrumentId())
                .map(quote -> new ValuedHolding(
                        holding,
                        instrument.get(),
                        account.get(),
                        quote,
                        loadQuotes
                                .findPrevious(holding.instrumentId(), quote.asOf())
                                .orElse(null)));
    }
}
