package com.roucoux.cairn.adapter.client.adapter;

import static java.util.Comparator.comparing;

import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Outbound adapter: fetches FCPE net asset values from SG Sirius. The API returns the whole
 * history regardless of the request, so {@link #fetch(Instrument)} and {@link
 * #fetchHistory(Instrument, LocalDate)} share the same call, and {@code fetch} keeps only the
 * latest point.
 */
@Component
public class SgSiriusQuoteAdapter implements FetchQuotePort {

    private static final String PATH =
            "/fr/nos-fonds/autres-fonds/details/type/1239/importfundsdata/SiriusFund/{isin}/liquidative/";

    private final RestClient client;

    public SgSiriusQuoteAdapter(@Qualifier("sgSiriusRestClient") RestClient client) {
        this.client = client;
    }

    @Override
    public boolean supports(PriceSource source) {
        return source == PriceSource.SG_SIRIUS;
    }

    @Override
    public Quote fetch(Instrument instrument) {
        List<Quote> history = fetchHistory(instrument, LocalDate.EPOCH);
        if (history.isEmpty()) {
            throw new MarketDataUnavailableException("SG Sirius returned no NAV for " + instrument.sourceRef());
        }
        return history.getLast();
    }

    @Override
    public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
        BigDecimal[][] points = call(instrument.sourceRef());
        return Arrays.stream(points)
                .map(point -> new Quote(
                        instrument.id(),
                        Instant.ofEpochMilli(point[0].longValue())
                                .atZone(ZoneId.of("Europe/Paris"))
                                .toLocalDate(),
                        point[1],
                        Money.EUR,
                        PriceSource.SG_SIRIUS,
                        Instant.now()))
                .filter(quote -> !quote.asOf().isBefore(from))
                .sorted(comparing(Quote::asOf))
                .toList();
    }

    private BigDecimal[][] call(String isin) {
        try {
            BigDecimal[][] points = client.get().uri(PATH, isin).retrieve().body(new ParameterizedTypeReference<>() {});
            return points == null ? new BigDecimal[0][] : points;
        } catch (RestClientException failure) {
            throw new MarketDataUnavailableException("SG Sirius call failed for " + isin + ": " + failure.getMessage());
        }
    }
}
