package com.roucoux.cairn.domain.model;

import java.util.List;
import java.util.Optional;

public record Portfolio(
        Money total,
        Money dayChange,
        Optional<Money> unrealizedGain,
        List<Allocation> byAssetClass,
        List<Allocation> byAccount,
        List<ValuedHolding> holdings,
        int staleCount) {}
