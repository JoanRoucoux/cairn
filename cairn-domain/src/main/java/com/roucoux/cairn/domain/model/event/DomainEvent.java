package com.roucoux.cairn.domain.model.event;

/** Marker for everything the domain announces to the outside world through {@code PublishEventPort}. */
public sealed interface DomainEvent permits PriceUpdated, RefreshCompleted, ValuationRecorded {}
