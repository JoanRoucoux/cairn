package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException;
import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.AccountType;
import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.ImportError;
import com.roucoux.cairn.domain.model.ImportErrorCode;
import com.roucoux.cairn.domain.model.ImportReport;
import com.roucoux.cairn.domain.model.ImportRow;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.port.in.ImportPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import java.lang.System.Logger.Level;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public class PortfolioImportService implements ImportPortfolioUseCase {

    private static final System.Logger LOG = System.getLogger(PortfolioImportService.class.getName());

    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{10}");

    private static final String EUR = "EUR";

    private static final Comparator<Instrument> DETERMINISTIC = Comparator.comparing(
                    (Instrument instrument) -> instrument.priceSource().name())
            .thenComparing(Instrument::sourceRef, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Instrument::id);

    private final LoadAccountsPort loadAccounts;
    private final SaveAccountPort saveAccount;
    private final LoadInstrumentsPort loadInstruments;
    private final SaveInstrumentPort saveInstrument;
    private final ResolveInstrumentUseCase resolveInstrument;
    private final LoadHoldingsPort loadHoldings;
    private final SaveHoldingPort saveHolding;
    private final Clock clock;

    public PortfolioImportService(
            LoadAccountsPort loadAccounts,
            SaveAccountPort saveAccount,
            LoadInstrumentsPort loadInstruments,
            SaveInstrumentPort saveInstrument,
            ResolveInstrumentUseCase resolveInstrument,
            LoadHoldingsPort loadHoldings,
            SaveHoldingPort saveHolding,
            Clock clock) {
        this.loadAccounts = loadAccounts;
        this.saveAccount = saveAccount;
        this.loadInstruments = loadInstruments;
        this.saveInstrument = saveInstrument;
        this.resolveInstrument = resolveInstrument;
        this.loadHoldings = loadHoldings;
        this.saveHolding = saveHolding;
        this.clock = clock;
    }

    @Override
    public ImportReport importPortfolio(List<ImportRow> rows) {
        Map<String, Account> accountsByName = new HashMap<>();
        loadAccounts.findAll().forEach(account -> accountsByName.put(account.name(), account));
        Map<String, List<Instrument>> instrumentsByRef = new HashMap<>();
        loadInstruments.findAll().stream()
                .sorted(DETERMINISTIC)
                .forEach(instrument -> index(instrumentsByRef, instrument));

        Map<String, InstrumentCandidate> candidates;
        try {
            candidates = validate(rows, accountsByName, instrumentsByRef);
        } catch (PortfolioImportRejectedException rejected) {
            LOG.log(
                    Level.INFO,
                    "portfolio import rejected: %d error(s) over %d row(s)"
                            .formatted(rejected.errors().size(), rows.size()));
            throw rejected;
        }

        int accountsCreated = 0;
        int instrumentsCreated = 0;
        int holdingsCreated = 0;
        int holdingsUpdated = 0;

        for (ImportRow row : rows) {
            Account account = accountsByName.get(row.accountName());
            if (account == null) {
                account = saveAccount.save(
                        new Account(UUID.randomUUID(), row.accountName(), row.accountType(), row.institution()));
                accountsByName.put(account.name(), account);
                accountsCreated++;
            }

            Instrument instrument = pick(instrumentsByRef.get(row.isinOrTicker()), account);
            if (instrument == null) {
                instrument = saveInstrument.save(
                        account.type() == AccountType.SAVINGS
                                ? eurCash()
                                : from(row, candidates.get(row.isinOrTicker())));
                index(instrumentsByRef, instrument);
                instrumentsCreated++;
            }

            Optional<Holding> existing = loadHoldings.findByAccountAndInstrument(account.id(), instrument.id());
            UUID holdingId = existing.map(Holding::id).orElseGet(UUID::randomUUID);
            saveHolding.save(new Holding(
                    holdingId, account.id(), instrument.id(), row.quantity(), row.averageCost(), clock.instant()));
            if (existing.isPresent()) {
                holdingsUpdated++;
            } else {
                holdingsCreated++;
            }
        }

        ImportReport report = new ImportReport(accountsCreated, instrumentsCreated, holdingsCreated, holdingsUpdated);
        LOG.log(Level.INFO, "portfolio import accepted: " + report);
        return report;
    }

    private Map<String, InstrumentCandidate> validate(
            List<ImportRow> rows, Map<String, Account> accountsByName, Map<String, List<Instrument>> known) {
        List<ImportError> errors = new ArrayList<>();
        Map<String, InstrumentCandidate> candidates = new HashMap<>();

        for (int index = 0; index < rows.size(); index++) {
            ImportRow row = rows.get(index);
            if (row.quantity() == null || row.quantity().signum() == 0) {
                errors.add(new ImportError(index, ImportErrorCode.ZERO_QUANTITY, null));
            }
            String ref = row.isinOrTicker();
            if (isSavings(row, accountsByName)) {
                if (!EUR.equals(ref)) {
                    errors.add(new ImportError(index, ImportErrorCode.SAVINGS_ACCOUNT_LINE, ref));
                }
                continue;
            }
            if (known.containsKey(ref) || candidates.containsKey(ref)) {
                continue;
            }
            try {
                Optional<InstrumentCandidate> chosen = choose(resolveInstrument.resolve(ref));
                if (chosen.isEmpty()) {
                    errors.add(new ImportError(index, ImportErrorCode.UNRESOLVED_INSTRUMENT, ref));
                } else {
                    candidates.put(ref, chosen.get());
                }
            } catch (UnknownInstrumentException unknown) {
                errors.add(new ImportError(index, ImportErrorCode.UNRESOLVED_INSTRUMENT, ref));
            }
        }

        if (!errors.isEmpty()) {
            throw new PortfolioImportRejectedException(errors);
        }
        return candidates;
    }

    private static Optional<InstrumentCandidate> choose(List<InstrumentCandidate> candidates) {
        return candidates.stream()
                .filter(candidate -> EUR.equals(candidate.currency()))
                .findFirst()
                .or(() -> candidates.stream()
                        .filter(candidate -> candidate.currency() == null)
                        .findFirst());
    }

    private static boolean isSavings(ImportRow row, Map<String, Account> accountsByName) {
        Account existing = accountsByName.get(row.accountName());
        return (existing == null ? row.accountType() : existing.type()) == AccountType.SAVINGS;
    }

    private static Instrument eurCash() {
        return new Instrument(UUID.randomUUID(), "Euros", null, EUR, AssetClass.CASH, PriceSource.MANUAL, EUR, null);
    }

    private static Instrument from(ImportRow row, InstrumentCandidate candidate) {
        String name = row.instrumentName() == null || row.instrumentName().isBlank()
                ? candidate.name()
                : row.instrumentName();
        return new Instrument(
                UUID.randomUUID(),
                name,
                isin(row.isinOrTicker()),
                candidate.symbol(),
                candidate.currency() == null ? EUR : candidate.currency(),
                candidate.assetClass(),
                candidate.source(),
                candidate.sourceRef(),
                null);
    }

    private static String isin(String isinOrTicker) {
        return ISIN.matcher(isinOrTicker).matches() ? isinOrTicker : null;
    }

    private Instrument pick(List<Instrument> titles, Account account) {
        if (titles == null) {
            return null;
        }
        if (titles.size() > 1 && account.id() != null) {
            Set<UUID> held = new HashSet<>();
            loadHoldings.findByAccount(account.id()).forEach(holding -> held.add(holding.instrumentId()));
            Optional<Instrument> alreadyHeld =
                    titles.stream().filter(title -> held.contains(title.id())).findFirst();
            if (alreadyHeld.isPresent()) {
                return alreadyHeld.get();
            }
        }
        return titles.stream()
                .filter(title -> title.priceSource() == PriceSource.YAHOO && EUR.equals(title.currency()))
                .findFirst()
                .orElse(titles.get(0));
    }

    private static void index(Map<String, List<Instrument>> instrumentsByRef, Instrument instrument) {
        if (instrument.isin() != null) {
            add(instrumentsByRef, instrument.isin(), instrument);
        }
        if (instrument.sourceRef() != null && !instrument.sourceRef().equals(instrument.isin())) {
            add(instrumentsByRef, instrument.sourceRef(), instrument);
        }
    }

    private static void add(Map<String, List<Instrument>> instrumentsByRef, String key, Instrument instrument) {
        instrumentsByRef.computeIfAbsent(key, ignored -> new ArrayList<>()).add(instrument);
    }
}
