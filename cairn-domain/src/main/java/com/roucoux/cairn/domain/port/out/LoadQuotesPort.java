package com.roucoux.cairn.domain.port.out;

import com.roucoux.cairn.domain.model.Quote;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Outbound port: read access to stored quotes. */
public interface LoadQuotesPort {

    Optional<Quote> findLatest(UUID instrumentId);

    Optional<Quote> findPrevious(UUID instrumentId, LocalDate before);

    List<Quote> findBetween(UUID instrumentId, LocalDate from, LocalDate to);
}
