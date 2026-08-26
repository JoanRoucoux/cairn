package com.roucoux.cairn.adapter.client.adapter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.roucoux.cairn.domain.exception.technical.MarketDataUnavailableException;
import com.roucoux.cairn.domain.model.Instrument;
import com.roucoux.cairn.domain.model.Money;
import com.roucoux.cairn.domain.model.PriceSource;
import com.roucoux.cairn.domain.model.Quote;
import com.roucoux.cairn.domain.port.out.FetchQuotePort;
import com.roucoux.cairn.domain.port.out.LoadInstrumentsPort;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.context.WebApplicationContext;

/**
 * Outbound adapter: fetches crypto prices from CoinGecko. The API groups every coin into a single
 * call, whereas {@link FetchQuotePort} is called instrument by instrument, so the adapter caches
 * the response for the duration of its own lifecycle. That lifecycle is what keeps the cache
 * correct: request-scoped for the API (one call per refresh, fresh again on the next request),
 * step-scoped for the batch (one call per step execution) — a singleton would serve a stale price
 * forever.
 */
@Component
@Scope(value = WebApplicationContext.SCOPE_REQUEST, proxyMode = ScopedProxyMode.TARGET_CLASS)
public class CoinGeckoQuoteAdapter implements FetchQuotePort {

    private final RestClient client;
    private final Clock clock;
    private final LoadInstrumentsPort loadInstrumentsPort;
    private Map<String, BigDecimal> cachedPrices;

    public CoinGeckoQuoteAdapter(
            @Qualifier("coinGeckoRestClient") RestClient client, Clock clock, LoadInstrumentsPort loadInstrumentsPort) {
        this.client = client;
        this.clock = clock;
        this.loadInstrumentsPort = loadInstrumentsPort;
    }

    @Override
    public boolean supports(PriceSource source) {
        return source == PriceSource.COINGECKO;
    }

    @Override
    public Quote fetch(Instrument instrument) {
        BigDecimal price = prices().get(instrument.sourceRef());
        if (price == null) {
            throw new MarketDataUnavailableException("CoinGecko has no price for " + instrument.sourceRef());
        }
        return new Quote(instrument.id(), LocalDate.now(clock), price, Money.EUR, PriceSource.COINGECKO, Instant.now());
    }

    @Override
    public List<Quote> fetchHistory(Instrument instrument, LocalDate from) {
        MarketChartResponse chart = marketChart(instrument.sourceRef());
        List<Quote> quotes = new ArrayList<>();
        Set<LocalDate> seenDays = new HashSet<>();
        for (List<BigDecimal> point : chart.prices()) {
            LocalDate day = toLocalDate(point.get(0).longValue());
            if (day.isBefore(from) || !seenDays.add(day)) {
                continue;
            }
            quotes.add(new Quote(instrument.id(), day, point.get(1), Money.EUR, PriceSource.COINGECKO, Instant.now()));
        }
        return quotes;
    }

    private Map<String, BigDecimal> prices() {
        if (cachedPrices == null) {
            cachedPrices = loadAllPrices();
        }
        return cachedPrices;
    }

    private Map<String, BigDecimal> loadAllPrices() {
        List<String> ids = loadInstrumentsPort.findAll().stream()
                .filter(instrument -> instrument.priceSource() == PriceSource.COINGECKO)
                .map(Instrument::sourceRef)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, Map<String, BigDecimal>> response = client.get()
                    .uri("/api/v3/simple/price?ids={ids}&vs_currencies=eur", String.join(",", ids))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null) {
                return Map.of();
            }
            Map<String, BigDecimal> result = new HashMap<>();
            response.forEach((coinId, currencies) -> {
                if (currencies != null && currencies.get("eur") != null) {
                    result.put(coinId, currencies.get("eur"));
                }
            });
            return result;
        } catch (RestClientException failure) {
            throw new MarketDataUnavailableException("CoinGecko call failed: " + failure.getMessage());
        }
    }

    private MarketChartResponse marketChart(String coinId) {
        try {
            MarketChartResponse response = client.get()
                    .uri("/api/v3/coins/{id}/market_chart?vs_currency=eur&days=90&interval=daily", coinId)
                    .retrieve()
                    .body(MarketChartResponse.class);
            if (response == null || response.prices() == null) {
                throw new MarketDataUnavailableException("CoinGecko returned no history for " + coinId);
            }
            return response;
        } catch (RestClientException failure) {
            throw new MarketDataUnavailableException(
                    "CoinGecko call failed for " + coinId + ": " + failure.getMessage());
        }
    }

    private static LocalDate toLocalDate(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.of("Europe/Paris"))
                .toLocalDate();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record MarketChartResponse(List<List<BigDecimal>> prices) {}
}
