package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class InstrumentResolutionService implements ResolveInstrumentUseCase {

    private static final Duration DEFAULT_PROBE_TIMEOUT = Duration.ofSeconds(4);

    private final List<ResolveInstrumentPort> resolvers;
    private final List<FetchQuotePort> fetchers;
    private final Duration probeTimeout;

    public InstrumentResolutionService(List<ResolveInstrumentPort> resolvers, List<FetchQuotePort> fetchers) {
        this(resolvers, fetchers, DEFAULT_PROBE_TIMEOUT);
    }

    InstrumentResolutionService(
            List<ResolveInstrumentPort> resolvers, List<FetchQuotePort> fetchers, Duration probeTimeout) {
        this.resolvers = List.copyOf(resolvers);
        this.fetchers = List.copyOf(fetchers);
        this.probeTimeout = probeTimeout;
    }

    @Override
    public List<InstrumentCandidate> resolve(String query) {
        List<InstrumentCandidate> candidates = resolvers.stream()
                .flatMap(resolver -> safeResolve(resolver, query).stream())
                .toList();
        if (candidates.isEmpty()) {
            throw new UnknownInstrumentException(query);
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<InstrumentCandidate>> probes = candidates.stream()
                    .map(candidate -> executor.submit(() -> probe(candidate)))
                    .toList();
            List<InstrumentCandidate> probed = new ArrayList<>();
            for (int i = 0; i < probes.size(); i++) {
                probed.add(await(probes.get(i), candidates.get(i)));
            }
            return probed;
        }
    }

    private static List<InstrumentCandidate> safeResolve(ResolveInstrumentPort resolver, String query) {
        try {
            return resolver.resolve(query);
        } catch (MarketDataUnavailableException unavailable) {
            return List.of();
        }
    }

    private InstrumentCandidate probe(InstrumentCandidate candidate) {
        if (candidate.probePrice() != null) {
            return candidate;
        }
        Instrument transientInstrument = new Instrument(
                UUID.randomUUID(),
                candidate.name(),
                null,
                "EUR",
                candidate.assetClass(),
                candidate.source(),
                candidate.sourceRef(),
                null);
        return fetchers.stream()
                .filter(fetcher -> fetcher.supports(candidate.source()))
                .findFirst()
                .map(fetcher ->
                        withPrice(candidate, fetcher.fetch(transientInstrument).price()))
                .orElse(candidate);
    }

    private InstrumentCandidate await(Future<InstrumentCandidate> probe, InstrumentCandidate fallback) {
        try {
            return probe.get(probeTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return fallback;
        } catch (ExecutionException | TimeoutException failed) {
            probe.cancel(true);
            return fallback;
        }
    }

    private static InstrumentCandidate withPrice(InstrumentCandidate candidate, BigDecimal price) {
        return new InstrumentCandidate(
                candidate.name(),
                candidate.source(),
                candidate.sourceRef(),
                candidate.assetClass(),
                candidate.exchange(),
                candidate.isin(),
                candidate.symbol(),
                price);
    }
}
