package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Quote;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Outbound port: read access to stored quotes. */
public interface LoadQuotesPort {

    Optional<Quote> findLatest(UUID instrumentId);

    Optional<Quote> findPrevious(UUID instrumentId, LocalDate before);

    List<Quote> findBetween(UUID instrumentId, LocalDate from, LocalDate to);

    /** One grouped read for the whole interval, rather than one query per instrument per day. */
    Map<UUID, List<Quote>> findBetweenForAll(Set<UUID> instrumentIds, LocalDate from, LocalDate to);
}
