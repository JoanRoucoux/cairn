package com.roucoux.cairn.adapter.client.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class YahooQuoteAdapter implements FetchQuotePort {

    private final RestClient client;
    private final Duration pauseBetweenCalls;

    public YahooQuoteAdapter(
            @Qualifier("yahooRestClient") RestClient client,
            @Value("${app.client.yahoo.pause-between-calls:300ms}") Duration pauseBetweenCalls) {
        this.client = client;
        this.pauseBetweenCalls = pauseBetweenCalls;
    }

    @Override
    public boolean supports(PriceSource source) {
        return source == PriceSource.YAHOO;
    }

    @Override
    public Quote fetch(Instrument instrument) {
        Chart chart = chart(instrument.sourceRef(), "1d");
        return new Quote(
                instrument.id(),
                sessionDate(chart),
                chart.meta().regularMarketPrice(),
                chart.meta().currency(),
                PriceSource.YAHOO,
                Instant.now());
    }

    @Override
    public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
        Chart chart = chart(instrument.sourceRef(), "max");
        List<Quote> quotes = new ArrayList<>();
        List<Long> timestamps = chart.timestamp();
        List<BigDecimal> closes = chart.indicators().quote().getFirst().close();
        for (int i = 0; i < timestamps.size(); i++) {
            BigDecimal close = closes.get(i);
            if (close == null) {
                continue;
            }
            LocalDate day = toLocalDate(timestamps.get(i));
            if (!day.isBefore(from)) {
                quotes.add(new Quote(
                        instrument.id(), day, close, chart.meta().currency(), PriceSource.YAHOO, Instant.now()));
            }
        }
        return quotes;
    }

    private Chart chart(String symbol, String range) {
        pause();
        try {
            ChartResponse response = client.get()
                    .uri("/v8/finance/chart/{symbol}?range={range}&interval=1d", symbol, range)
                    .retrieve()
                    .body(ChartResponse.class);
            if (response == null
                    || response.chart() == null
                    || response.chart().result() == null
                    || response.chart().result().isEmpty()) {
                throw new MarketDataUnavailableException("Yahoo returned no result for " + symbol);
            }
            return response.chart().result().getFirst();
        } catch (RestClientException failure) {
            throw new MarketDataUnavailableException("Yahoo call failed for " + symbol + ": " + failure.getMessage());
        }
    }

    private static LocalDate sessionDate(Chart chart) {
        List<Long> timestamps = chart.timestamp();
        List<BigDecimal> closes = chart.indicators().quote().getFirst().close();
        for (int i = timestamps.size() - 1; i >= 0; i--) {
            if (closes.get(i) != null) {
                return toLocalDate(timestamps.get(i));
            }
        }
        return toLocalDate(chart.meta().regularMarketTime());
    }

    private static LocalDate toLocalDate(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.of("Europe/Paris"))
                .toLocalDate();
    }

    private void pause() {
        if (pauseBetweenCalls.isZero()) {
            return;
        }
        try {
            Thread.sleep(pauseBetweenCalls);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChartResponse(ChartWrapper chart) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChartWrapper(List<Chart> result) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Chart(Meta meta, List<Long> timestamp, Indicators indicators) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Meta(String currency, BigDecimal regularMarketPrice, long regularMarketTime) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Indicators(List<Quotes> quote) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Quotes(List<BigDecimal> close) {}
}
