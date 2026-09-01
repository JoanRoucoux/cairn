package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException;
import com.roucoux.cairn.domain.model.Account;
import com.roucoux.cairn.domain.model.Holding;
import com.roucoux.cairn.domain.model.ImportError;
import com.roucoux.cairn.domain.model.ImportErrorCode;
import com.roucoux.cairn.domain.model.ImportReport;
import com.roucoux.cairn.domain.model.ImportRow;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.port.in.ImportPortfolioUseCase;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.out.LoadAccountsPort;
import com.roucoux.cairn.domain.port.out.LoadHoldingsPort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveAccountPort;
import com.roucoux.cairn.domain.port.out.SaveHoldingPort;
import com.roucoux.cairn.domain.port.out.SaveInstrumentPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

public class PortfolioImportService implements ImportPortfolioUseCase {

    /** Two letters then ten alphanumerics: enough to tell an ISIN from a ticker or a provider id. */
    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{10}");

    private static final String EUR = "EUR";

    private final LoadAccountsPort loadAccounts;
    private final SaveAccountPort saveAccount;
    private final LoadInstrumentsPort loadInstruments;
    private final SaveInstrumentPort saveInstrument;
    private final ResolveInstrumentUseCase resolveInstrument;
    private final LoadHoldingsPort loadHoldings;
    private final SaveHoldingPort saveHolding;

    public PortfolioImportService(
            LoadAccountsPort loadAccounts,
            SaveAccountPort saveAccount,
            LoadInstrumentsPort loadInstruments,
            SaveInstrumentPort saveInstrument,
            ResolveInstrumentUseCase resolveInstrument,
            LoadHoldingsPort loadHoldings,
            SaveHoldingPort saveHolding) {
        this.loadAccounts = loadAccounts;
        this.saveAccount = saveAccount;
        this.loadInstruments = loadInstruments;
        this.saveInstrument = saveInstrument;
        this.resolveInstrument = resolveInstrument;
        this.loadHoldings = loadHoldings;
        this.saveHolding = saveHolding;
    }

    @Override
    public ImportReport importPortfolio(List<ImportRow> rows) {
        Map<String, Account> accountsByName = new HashMap<>();
        loadAccounts.findAll().forEach(account -> accountsByName.put(account.name(), account));
        Map<String, Instrument> instrumentsByRef = new HashMap<>();
        loadInstruments.findAll().forEach(instrument -> index(instrumentsByRef, instrument));

        Map<String, InstrumentCandidate> candidates = validate(rows, instrumentsByRef);

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

            Instrument instrument = instrumentsByRef.get(row.isinOrTicker());
            if (instrument == null) {
                instrument = saveInstrument.save(from(row, candidates.get(row.isinOrTicker())));
                index(instrumentsByRef, instrument);
                instrumentsCreated++;
            }

            Optional<Holding> existing = loadHoldings.findByAccountAndInstrument(account.id(), instrument.id());
            UUID holdingId = existing.map(Holding::id).orElseGet(UUID::randomUUID);
            saveHolding.save(new Holding(holdingId, account.id(), instrument.id(), row.quantity(), row.averageCost()));
            if (existing.isPresent()) {
                holdingsUpdated++;
            } else {
                holdingsCreated++;
            }
        }

        return new ImportReport(accountsCreated, instrumentsCreated, holdingsCreated, holdingsUpdated);
    }

    /**
     * Every row is checked before any is written, and every reason is collected rather than the
     * first: a caller fixing a file wants the whole list in one pass. Resolution happens here too,
     * so an instrument is looked up once and the external call is not repeated while writing.
     */
    private Map<String, InstrumentCandidate> validate(List<ImportRow> rows, Map<String, Instrument> known) {
        List<ImportError> errors = new ArrayList<>();
        Map<String, InstrumentCandidate> candidates = new HashMap<>();

        for (int index = 0; index < rows.size(); index++) {
            ImportRow row = rows.get(index);
            if (row.quantity() == null || row.quantity().signum() == 0) {
                errors.add(new ImportError(index, ImportErrorCode.ZERO_QUANTITY, null));
            }
            String ref = row.isinOrTicker();
            if (known.containsKey(ref) || candidates.containsKey(ref)) {
                continue;
            }
            List<InstrumentCandidate> found = resolveInstrument.resolve(ref);
            if (found.isEmpty()) {
                errors.add(new ImportError(index, ImportErrorCode.UNRESOLVED_INSTRUMENT, ref));
            } else {
                candidates.put(ref, found.getFirst());
            }
        }

        if (!errors.isEmpty()) {
            throw new PortfolioImportRejectedException(errors);
        }
        return candidates;
    }

    private static Instrument from(ImportRow row, InstrumentCandidate candidate) {
        String name = row.instrumentName() == null || row.instrumentName().isBlank()
                ? candidate.name()
                : row.instrumentName();
        return new Instrument(
                UUID.randomUUID(),
                name,
                isin(row.isinOrTicker()),
                EUR,
                candidate.assetClass(),
                candidate.source(),
                candidate.sourceRef(),
                null);
    }

    private static String isin(String isinOrTicker) {
        return ISIN.matcher(isinOrTicker).matches() ? isinOrTicker : null;
    }

    /** Reachable by whichever identifier the file used, so a second row naming it differently still matches. */
    private static void index(Map<String, Instrument> instrumentsByRef, Instrument instrument) {
        if (instrument.isin() != null) {
            instrumentsByRef.put(instrument.isin(), instrument);
        }
        if (instrument.sourceRef() != null) {
            instrumentsByRef.put(instrument.sourceRef(), instrument);
        }
    }
}
