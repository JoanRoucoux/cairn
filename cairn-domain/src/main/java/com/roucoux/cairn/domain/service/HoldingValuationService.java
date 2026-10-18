package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.ValuedHolding;
import com.roucoux.cairn.domain.port.in.ValueHoldingUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.LoadQuotesPort;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class HoldingValuationService implements ValueHoldingUseCase {

    private final LoadInstrumentsPort loadInstruments;
    private final LoadAccountsPort loadAccounts;
    private final LoadQuotesPort loadQuotes;
    private final Clock clock;

    public HoldingValuationService(
            LoadInstrumentsPort loadInstruments,
            LoadAccountsPort loadAccounts,
            LoadQuotesPort loadQuotes,
            Clock clock) {
        this.loadInstruments = loadInstruments;
        this.loadAccounts = loadAccounts;
        this.loadQuotes = loadQuotes;
        this.clock = clock;
    }

    @Override
    public Optional<ValuedHolding> value(Holding holding) {
        return valueAll(List.of(holding)).stream().findFirst();
    }

    @Override
    public List<ValuedHolding> valueAll(List<Holding> holdings) {
        if (holdings.isEmpty()) {
            return List.of();
        }
        Map<UUID, Instrument> instruments =
                loadInstruments.findAll().stream().collect(Collectors.toMap(Instrument::id, Function.identity()));
        Map<UUID, Account> accounts =
                loadAccounts.findAll().stream().collect(Collectors.toMap(Account::id, Function.identity()));
        Set<UUID> quotedIds = holdings.stream()
                .map(Holding::instrumentId)
                .filter(id ->
                        instruments.containsKey(id) && !instruments.get(id).isPricedAtPar())
                .collect(Collectors.toSet());
        Map<UUID, List<Quote>> quotes = quotedIds.isEmpty() ? Map.of() : loadQuotes.findLatestTwo(quotedIds);
        return holdings.stream()
                .flatMap(holding -> valued(holding, instruments, accounts, quotes).stream())
                .toList();
    }

    private Optional<ValuedHolding> valued(
            Holding holding,
            Map<UUID, Instrument> instruments,
            Map<UUID, Account> accounts,
            Map<UUID, List<Quote>> quotes) {
        Instrument instrument = instruments.get(holding.instrumentId());
        Account account = accounts.get(holding.accountId());
        if (instrument == null || account == null) {
            return Optional.empty();
        }
        if (instrument.isPricedAtPar()) {
            LocalDate today = LocalDate.now(clock);
            Quote par = Quote.atPar(instrument.id(), instrument.currency(), today, clock.instant());
            Quote previousPar =
                    Quote.atPar(instrument.id(), instrument.currency(), today.minusDays(1), clock.instant());
            return Optional.of(
                    new ValuedHolding(holding, instrument, account, Optional.of(par), Optional.of(previousPar)));
        }
        List<Quote> latestTwo = quotes.getOrDefault(instrument.id(), List.of());
        Optional<Quote> latest = latestTwo.stream().findFirst();
        Optional<Quote> previous = latestTwo.stream().skip(1).findFirst();
        return Optional.of(new ValuedHolding(holding, instrument, account, latest, previous));
    }
}
