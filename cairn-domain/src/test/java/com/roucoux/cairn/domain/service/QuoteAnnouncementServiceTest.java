package com.roucoux.cairn.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.DomainEvent;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.model.event.RefreshCompleted;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuoteAnnouncementServiceTest {

    @Test
    void announcesOnePriceUpdatePerSavedQuote() {
        List<DomainEvent> published = new ArrayList<>();
        AnnounceQuotesUseCase announce = new QuoteAnnouncementService(published::add);

        announce.quotesSaved(List.of(quote("AAA"), quote("BBB")));

        assertThat(published).hasSize(2).allMatch(PriceUpdated.class::isInstance);
    }

    @Test
    void announcesTheEndOfARefreshWithItsCounts() {
        List<DomainEvent> published = new ArrayList<>();
        new QuoteAnnouncementService(published::add)
                .refreshCompleted(Set.of(AssetClass.ETF), 7, 1, RefreshTrigger.BATCH);

        assertThat(published)
                .singleElement()
                .isEqualTo(new RefreshCompleted(Set.of(AssetClass.ETF), 7, 1, RefreshTrigger.BATCH));
    }

    private static Quote quote(String ref) {
        return new Quote(
                UUID.nameUUIDFromBytes(ref.getBytes()),
                LocalDate.of(2026, 9, 23),
                BigDecimal.TEN,
                "EUR",
                PriceSource.YAHOO,
                Instant.parse("2026-09-23T00:00:00Z"));
    }
}
