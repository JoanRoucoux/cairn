package com.roucoux.cairn.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.roucoux.cairn.domain.model.AssetClass;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.model.event.DomainEvent;
import com.roucoux.cairn.domain.model.event.PriceUpdated;
import com.roucoux.cairn.domain.model.event.RefreshCompleted;
import com.roucoux.cairn.domain.model.event.RefreshTrigger;
import com.roucoux.cairn.domain.port.in.AnnounceQuotesUseCase;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import com.roucoux.cairn.domain.port.out.SaveQuotePort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuoteDomainConfigTest {

    private static final Instrument LIVRET_A = new Instrument(
            UUID.randomUUID(), "Livret A", null, "EUR", AssetClass.CASH, PriceSource.MANUAL, null, "Regulated savings");

    private final QuoteDomainConfig config = new QuoteDomainConfig();

    @Test
    void wiresTheAnnouncementThroughSoAManualQuotePublishesItsPriceAndAManualRefresh() {
        List<DomainEvent> published = new ArrayList<>();
        List<Quote> saved = new ArrayList<>();
        LoadInstrumentsPort loadInstruments = new LoadInstrumentsPort() {
            @Override
            public List<Instrument> findAll() {
                return List.of(LIVRET_A);
            }

            @Override
            public Optional<Instrument> findById(UUID id) {
                return LIVRET_A.id().equals(id) ? Optional.of(LIVRET_A) : Optional.empty();
            }

            @Override
            public List<Instrument> findRefreshable(Set<AssetClass> assetClasses) {
                return List.of();
            }
        };
        SaveQuotePort saveQuote = new SaveQuotePort() {
            @Override
            public void upsert(Quote quote) {
                saved.add(quote);
            }

            @Override
            public void upsertAll(List<Quote> quotes) {
                saved.addAll(quotes);
            }
        };
        AnnounceQuotesUseCase announceQuotes = config.announceQuotes(published::add);

        Quote quote = config.quoteRecordingService(loadInstruments, saveQuote, announceQuotes)
                .record(LIVRET_A.id(), LocalDate.of(2026, 8, 20), new BigDecimal("57.48"));

        assertThat(saved).containsExactly(quote);
        assertThat(published)
                .containsExactly(
                        new PriceUpdated(quote),
                        new RefreshCompleted(Set.of(AssetClass.CASH), 1, 0, RefreshTrigger.MANUAL));
    }
}
