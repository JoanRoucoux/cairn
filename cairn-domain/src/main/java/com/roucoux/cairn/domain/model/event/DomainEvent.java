package com.roucoux.cairn.domain.model.event;

public sealed interface DomainEvent permits PriceUpdated, RefreshCompleted, ValuationRecorded {}
