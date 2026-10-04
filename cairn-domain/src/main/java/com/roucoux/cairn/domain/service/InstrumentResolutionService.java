package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.in.SearchInstrumentsUseCase;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

public class InstrumentResolutionService implements ResolveInstrumentUseCase, SearchInstrumentsUseCase {

    private static final System.Logger LOG = System.getLogger(InstrumentResolutionService.class.getName());

    private static final Pattern ISIN = Pattern.compile("[A-Z]{2}[A-Z0-9]{10}");
    private static final String UNKNOWN_CURRENCY = "XXX";
    private static final Set<PriceSource> IMPORT_SOURCES = Set.of(PriceSource.YAHOO, PriceSource.AMUNDI);
    private static final Duration DEFAULT_PROBE_TIMEOUT = Duration.ofSeconds(4);

    private final List<ResolveInstrumentPort> resolvers;
    private final List<FetchQuotePort> fetchers;
    private final LoadInstrumentsPort loadInstruments;
    private final Duration probeTimeout;

    public InstrumentResolutionService(
            List<ResolveInstrumentPort> resolvers, List<FetchQuotePort> fetchers, LoadInstrumentsPort loadInstruments) {
        this(resolvers, fetchers, loadInstruments, DEFAULT_PROBE_TIMEOUT);
    }

    InstrumentResolutionService(
            List<ResolveInstrumentPort> resolvers,
            List<FetchQuotePort> fetchers,
            LoadInstrumentsPort loadInstruments,
            Duration probeTimeout) {
        this.resolvers = List.copyOf(resolvers);
        this.fetchers = List.copyOf(fetchers);
        this.loadInstruments = loadInstruments;
        this.probeTimeout = probeTimeout;
    }

    @Override
    public List<InstrumentCandidate> resolve(String query) {
        List<InstrumentCandidate> candidates = resolvers.stream()
                .filter(resolver -> IMPORT_SOURCES.stream().anyMatch(resolver::supports))
                .flatMap(resolver -> safeResolve(resolver, query).stream())
                .toList();
        if (candidates.isEmpty()) {
            candidates = exactCoinGeckoMatch(query);
        }
        if (candidates.isEmpty()) {
            throw new UnknownInstrumentException(query);
        }
        return probedAndSorted(candidates);
    }

    private List<InstrumentCandidate> exactCoinGeckoMatch(String query) {
        if (ISIN.matcher(query).matches()) {
            return List.of();
        }
        return resolvers.stream()
                .filter(resolver -> resolver.supports(PriceSource.COINGECKO))
                .flatMap(resolver -> safeResolve(resolver, query).stream())
                .filter(candidate -> query.equalsIgnoreCase(candidate.sourceRef()))
                .toList();
    }

    @Override
    public List<InstrumentCandidate> search(PriceSource source, String query) {
        List<InstrumentCandidate> candidates = resolvers.stream()
                .filter(resolver -> resolver.supports(source))
                .findFirst()
                .map(resolver -> resolver.resolve(query))
                .orElse(List.of());
        if (candidates.isEmpty()) {
            return List.of();
        }
        Map<String, UUID> tracked = trackedBy(source);
        return probedAndSorted(candidates).stream()
                .map(candidate -> candidate.withTrackedInstrumentId(tracked.get(candidate.sourceRef())))
                .toList();
    }

    private Map<String, UUID> trackedBy(PriceSource source) {
        Map<String, UUID> tracked = new HashMap<>();
        for (Instrument instrument : loadInstruments.findAll()) {
            if (instrument.priceSource() == source && instrument.sourceRef() != null) {
                tracked.putIfAbsent(instrument.sourceRef(), instrument.id());
            }
        }
        return tracked;
    }

    private List<InstrumentCandidate> probedAndSorted(List<InstrumentCandidate> candidates) {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<InstrumentCandidate>> probes = candidates.stream()
                    .map(candidate -> {
                        Optional<FetchQuotePort> fetcher = fetcherFor(candidate);
                        return executor.submit(() -> probe(candidate, fetcher));
                    })
                    .toList();
            List<InstrumentCandidate> probed = new ArrayList<>();
            for (int i = 0; i < probes.size(); i++) {
                probed.add(await(probes.get(i), candidates.get(i)));
            }
            return probed.stream()
                    .sorted(Comparator.comparingInt(InstrumentResolutionService::currencyRank))
                    .toList();
        }
    }

    private static List<InstrumentCandidate> safeResolve(ResolveInstrumentPort resolver, String query) {
        try {
            return resolver.resolve(query);
        } catch (MarketDataUnavailableException unavailable) {
            LOG.log(
                    Level.WARNING,
                    "instrument resolution source unavailable for " + query + ": " + unavailable.getMessage());
            return List.of();
        }
    }

    private Optional<FetchQuotePort> fetcherFor(InstrumentCandidate candidate) {
        if (candidate.probePrice() != null || candidate.source() == PriceSource.COINGECKO) {
            return Optional.empty();
        }
        return fetchers.stream()
                .filter(fetcher -> fetcher.supports(candidate.source()))
                .findFirst();
    }

    private InstrumentCandidate probe(InstrumentCandidate candidate, Optional<FetchQuotePort> fetcher) {
        Instrument transientInstrument = new Instrument(
                UUID.randomUUID(),
                candidate.name(),
                null,
                UNKNOWN_CURRENCY,
                candidate.assetClass(),
                candidate.source(),
                candidate.sourceRef(),
                null);
        return fetcher.map(port -> withQuote(candidate, port.fetch(transientInstrument)))
                .orElse(candidate);
    }

    private InstrumentCandidate await(Future<InstrumentCandidate> probe, InstrumentCandidate fallback) {
        try {
            return probe.get(probeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            LOG.log(Level.WARNING, "price probe interrupted for " + describe(fallback));
            return fallback;
        } catch (ExecutionException | TimeoutException failed) {
            probe.cancel(true);
            LOG.log(Level.WARNING, "price probe failed for " + describe(fallback) + ": " + reasonOf(failed));
            return fallback;
        }
    }

    private static String describe(InstrumentCandidate candidate) {
        return candidate.sourceRef() + " (" + candidate.source() + ")";
    }

    private static String reasonOf(Exception failed) {
        Throwable cause =
                failed instanceof ExecutionException && failed.getCause() != null ? failed.getCause() : failed;
        return cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }

    private static int currencyRank(InstrumentCandidate candidate) {
        if (candidate.currency() == null) {
            return 1;
        }
        return "EUR".equals(candidate.currency()) ? 0 : 2;
    }

    private static InstrumentCandidate withQuote(InstrumentCandidate candidate, Quote quote) {
        return new InstrumentCandidate(
                candidate.name(),
                candidate.source(),
                candidate.sourceRef(),
                candidate.assetClass(),
                candidate.exchange(),
                candidate.isin(),
                candidate.symbol(),
                quote.price(),
                quote.currency(),
                candidate.probeAsOf() != null ? candidate.probeAsOf() : quote.asOf(),
                candidate.trackedInstrumentId());
    }
}
