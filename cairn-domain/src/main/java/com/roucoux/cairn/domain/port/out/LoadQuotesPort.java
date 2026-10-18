package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Quote;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface LoadQuotesPort {

    Map<UUID, List<Quote>> findLatestTwo(Set<UUID> instrumentIds);

    List<Quote> findBetween(UUID instrumentId, LocalDate from, LocalDate to);

    Map<UUID, List<Quote>> findBetweenForAll(Set<UUID> instrumentIds, LocalDate from, LocalDate to);

    Map<UUID, Quote> findLatestOnOrBefore(Set<UUID> instrumentIds, LocalDate day);

    Map<UUID, LocalDate> findFirstQuoteDates(Set<UUID> instrumentIds);
}
