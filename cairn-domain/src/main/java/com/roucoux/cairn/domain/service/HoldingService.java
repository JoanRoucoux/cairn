package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.CashHoldingTradeException;
import com.roucoux.cairn.domain.exception.business.DuplicateHoldingException;
import com.roucoux.cairn.domain.exception.business.InvalidInstrumentException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.exception.business.SavingsAccountLineException;
import com.roucoux.cairn.domain.exception.business.ZeroQuantityException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Isin;
import com.roucoux.cairn.domain.model.NewInstrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.ManageHoldingUseCase;
import com.roucoux.cairn.domain.port.out.DeleteHoldingPort;
import com.roucoux.cairn.domain.port.out.DeleteInstrumentPort;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class HoldingService implements ManageHoldingUseCase {

    private static final String EUR = "EUR";
    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");

    private final LoadHoldingsPort loadHoldings;
    private final SaveHoldingPort saveHolding;
    private final DeleteHoldingPort deleteHolding;
    private final LoadAccountsPort loadAccounts;
    private final LoadInstrumentsPort loadInstruments;
    private final SaveInstrumentPort saveInstrument;
    private final SaveQuotePort saveQuote;
    private final InstrumentCleanup instrumentCleanup;
    private final Clock clock;

    public HoldingService(
            LoadHoldingsPort loadHoldings,
            SaveHoldingPort saveHolding,
            DeleteHoldingPort deleteHolding,
            LoadAccountsPort loadAccounts,
            LoadInstrumentsPort loadInstruments,
            SaveInstrumentPort saveInstrument,
            SaveQuotePort saveQuote,
            DeleteInstrumentPort deleteInstrument,
            Clock clock) {
        this.loadHoldings = loadHoldings;
        this.saveHolding = saveHolding;
        this.deleteHolding = deleteHolding;
        this.loadAccounts = loadAccounts;
        this.loadInstruments = loadInstruments;
        this.saveInstrument = saveInstrument;
        this.saveQuote = saveQuote;
        this.instrumentCleanup = new InstrumentCleanup(loadHoldings, loadInstruments, deleteInstrument);
        this.clock = clock;
    }

    @Override
    public Holding createWithNewInstrument(
            UUID accountId, NewInstrument newInstrument, BigDecimal quantity, BigDecimal averageCost) {
        requireNonZero(quantity);
        Account account =
                loadAccounts.findById(accountId).orElseThrow(() -> new NotFoundException("account", accountId));
        if (account.type() == AccountType.SAVINGS) {
            throw new SavingsAccountLineException();
        }
        NewInstrument normalised = normalised(newInstrument);
        Optional<Instrument> tracked = tracked(normalised);
        tracked.ifPresent(instrument -> loadHoldings
                .findByAccountAndInstrument(accountId, instrument.id())
                .ifPresent(existing -> {
                    throw new DuplicateHoldingException(accountId, instrument.id());
                }));
        Instrument instrument = tracked.orElseGet(() -> createInstrument(normalised));
        return saveHolding.save(
                new Holding(UUID.randomUUID(), accountId, instrument.id(), quantity, averageCost, clock.instant()));
    }

    private Optional<Instrument> tracked(NewInstrument newInstrument) {
        if (newInstrument.priceSource() == PriceSource.MANUAL) {
            return Optional.empty();
        }
        return loadInstruments.findAll().stream()
                .filter(instrument -> instrument.priceSource() == newInstrument.priceSource()
                        && newInstrument.sourceRef().equals(instrument.sourceRef()))
                .findFirst();
    }

    private Instrument createInstrument(NewInstrument newInstrument) {
        Instrument instrument = saveInstrument.save(new Instrument(
                UUID.randomUUID(),
                newInstrument.name(),
                newInstrument.isin(),
                newInstrument.symbol(),
                newInstrument.currency(),
                newInstrument.assetClass(),
                newInstrument.priceSource(),
                newInstrument.sourceRef(),
                null));
        if (newInstrument.priceSource() == PriceSource.MANUAL) {
            saveQuote.upsert(new Quote(
                    instrument.id(),
                    LocalDate.now(clock),
                    newInstrument.price(),
                    EUR,
                    PriceSource.MANUAL,
                    clock.instant()));
        }
        return instrument;
    }

    private static NewInstrument normalised(NewInstrument request) {
        if (request.priceSource() != PriceSource.SG_SIRIUS
                && (request.name() == null || request.name().isBlank())) {
            throw new InvalidInstrumentException("name is required");
        }
        if (request.assetClass() == AssetClass.CASH) {
            throw new InvalidInstrumentException("a cash instrument cannot be created here");
        }
        if (request.priceSource() != PriceSource.MANUAL
                && (request.assetClass() == AssetClass.BOND || request.assetClass() == AssetClass.OTHER)
                && request.priceSource() != PriceSource.SG_SIRIUS) {
            throw new InvalidInstrumentException("BOND and OTHER are only accepted for a MANUAL instrument");
        }
        PriceSource source = request.priceSource();
        if (source != PriceSource.MANUAL && request.price() != null) {
            throw new InvalidInstrumentException("price is only accepted for a MANUAL instrument");
        }
        return switch (source) {
            case MANUAL -> {
                if (request.price() == null || request.price().signum() <= 0) {
                    throw new InvalidInstrumentException("price must be positive for a MANUAL instrument");
                }
                yield new NewInstrument(
                        request.name(),
                        request.assetClass(),
                        source,
                        null,
                        request.isin(),
                        request.symbol(),
                        request.price(),
                        EUR);
            }
            case SG_SIRIUS -> {
                String isin = validIsin(request);
                yield new NewInstrument(isin, AssetClass.FUND, source, isin, isin, request.symbol(), null, EUR);
            }
            case AMUNDI -> {
                String isin = validIsin(request);
                yield new NewInstrument(
                        request.name(), request.assetClass(), source, isin, isin, request.symbol(), null, EUR);
            }
            case YAHOO, COINGECKO -> {
                if (request.sourceRef() == null || request.sourceRef().isBlank()) {
                    throw new InvalidInstrumentException("sourceRef is required unless priceSource is MANUAL");
                }
                yield new NewInstrument(
                        request.name(),
                        request.assetClass(),
                        source,
                        request.sourceRef().strip(),
                        request.isin(),
                        request.symbol(),
                        null,
                        currency(request));
            }
        };
    }

    private static String currency(NewInstrument request) {
        if (request.currency() == null || request.currency().isBlank()) {
            return EUR;
        }
        String currency = request.currency().strip().toUpperCase(Locale.ROOT);
        if (!CURRENCY.matcher(currency).matches()) {
            throw new InvalidInstrumentException("currency must be an ISO 4217 code");
        }
        return currency;
    }

    private static String validIsin(NewInstrument request) {
        String candidate = request.isin() != null && !request.isin().isBlank() ? request.isin() : request.sourceRef();
        String isin = candidate == null ? "" : candidate.strip().toUpperCase(Locale.ROOT);
        if (!Isin.isValid(isin)) {
            throw new InvalidInstrumentException("a valid ISIN is required for " + request.priceSource());
        }
        return isin;
    }

    @Override
    public Holding create(UUID accountId, UUID instrumentId, BigDecimal quantity, BigDecimal averageCost) {
        requireNonZero(quantity);
        Account account =
                loadAccounts.findById(accountId).orElseThrow(() -> new NotFoundException("account", accountId));
        Instrument instrument = loadInstruments
                .findById(instrumentId)
                .orElseThrow(() -> new NotFoundException("instrument", instrumentId));
        if (account.type() == AccountType.SAVINGS && !instrument.isEurCash()) {
            throw new SavingsAccountLineException();
        }
        loadHoldings.findByAccountAndInstrument(accountId, instrumentId).ifPresent(existing -> {
            throw new DuplicateHoldingException(accountId, instrumentId);
        });
        return saveHolding.save(
                new Holding(UUID.randomUUID(), accountId, instrumentId, quantity, averageCost, clock.instant()));
    }

    @Override
    public Holding update(UUID id, BigDecimal quantity, BigDecimal averageCost) {
        requireNonZero(quantity);
        Holding existing = loadHoldings.findById(id).orElseThrow(() -> new NotFoundException("holding", id));
        return saveHolding.save(new Holding(
                existing.id(), existing.accountId(), existing.instrumentId(), quantity, averageCost, clock.instant()));
    }

    @Override
    public void delete(UUID id) {
        Holding existing = loadHoldings.findById(id).orElseThrow(() -> new NotFoundException("holding", id));
        deleteHolding.delete(id);
        instrumentCleanup.releaseIfUnheld(existing.instrumentId());
    }

    @Override
    public Holding buy(UUID id, BigDecimal quantity, BigDecimal unitPrice) {
        Holding existing = tradable(id);
        return saveHolding.save(existing.buy(quantity, unitPrice).withUpdatedAt(clock.instant()));
    }

    @Override
    public Optional<Holding> sell(UUID id, BigDecimal quantity) {
        Holding existing = tradable(id);
        Optional<Holding> remaining = existing.sell(quantity);
        if (remaining.isEmpty()) {
            deleteHolding.delete(id);
            instrumentCleanup.releaseIfUnheld(existing.instrumentId());
            return Optional.empty();
        }
        return Optional.of(saveHolding.save(remaining.get().withUpdatedAt(clock.instant())));
    }

    private Holding tradable(UUID id) {
        Holding existing = loadHoldings.findById(id).orElseThrow(() -> new NotFoundException("holding", id));
        loadInstruments
                .findById(existing.instrumentId())
                .filter(instrument -> instrument.assetClass() == AssetClass.CASH)
                .ifPresent(cash -> {
                    throw new CashHoldingTradeException(id);
                });
        return existing;
    }

    private static void requireNonZero(BigDecimal quantity) {
        if (quantity == null || quantity.signum() == 0) {
            throw new ZeroQuantityException();
        }
    }
}
