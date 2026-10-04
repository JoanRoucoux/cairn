package com.roucoux.cairn.adapter.client.adapter;

import static java.util.Comparator.comparing;

import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AmundiQuoteAdapter implements FetchQuotePort {

    private final AmundiShares shares;

    public AmundiQuoteAdapter(@Qualifier("amundiRestClient") RestClient client) {
        this.shares = new AmundiShares(client);
    }

    @Override
    public boolean supports(PriceSource source) {
        return source == PriceSource.AMUNDI;
    }

    @Override
    public Quote fetch(Instrument instrument) {
        return shares.find(instrument.sourceRef(), "lastNav")
                .map(AmundiShares.Share::lastNav)
                .filter(nav -> nav.value() != null && nav.date() != null)
                .map(nav -> toQuote(instrument, nav))
                .orElseThrow(() ->
                        new MarketDataUnavailableException("Amundi returned no NAV for " + instrument.sourceRef()));
    }

    @Override
    public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
        List<AmundiShares.Nav> history = shares.find(instrument.sourceRef(), "navHistory")
                .map(AmundiShares.Share::navHistory)
                .orElse(List.of());
        return history.stream()
                .filter(nav -> nav.value() != null && nav.date() != null)
                .map(nav -> toQuote(instrument, nav))
                .filter(quote -> !quote.asOf().isBefore(from))
                .sorted(comparing(Quote::asOf))
                .toList();
    }

    private static Quote toQuote(Instrument instrument, AmundiShares.Nav nav) {
        String currency =
                nav.currency() == null ? instrument.currency() : nav.currency().iso3Code();
        return new Quote(instrument.id(), nav.date(), nav.value(), currency, PriceSource.AMUNDI, Instant.now());
    }
}
