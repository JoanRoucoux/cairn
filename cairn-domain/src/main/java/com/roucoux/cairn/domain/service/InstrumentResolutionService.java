package com.roucoux.cairn.domain.service;

import com.roucoux.cairn.domain.exception.business.UnknownInstrumentException;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.InstrumentCandidate;
import com.roucoux.cairn.domain.port.in.ResolveInstrumentUseCase;
import com.roucoux.cairn.domain.port.out.ResolveInstrumentPort;
import java.util.List;

public class InstrumentResolutionService implements ResolveInstrumentUseCase {

    private final List<ResolveInstrumentPort> resolvers;

    public InstrumentResolutionService(List<ResolveInstrumentPort> resolvers) {
        this.resolvers = List.copyOf(resolvers);
    }

    @Override
    public List<InstrumentCandidate> resolve(String query) {
        List<InstrumentCandidate> candidates = resolvers.stream()
                .flatMap(resolver -> safeResolve(resolver, query).stream())
                .toList();
        if (candidates.isEmpty()) {
            throw new UnknownInstrumentException(query);
        }
        return candidates;
    }

    private static List<InstrumentCandidate> safeResolve(ResolveInstrumentPort resolver, String query) {
        try {
            return resolver.resolve(query);
        } catch (MarketDataUnavailableException unavailable) {
            return List.of();
        }
    }
}
